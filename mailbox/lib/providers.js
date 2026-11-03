import { randomBytes, createHash } from 'node:crypto';

export const providers = {
  gmail: { name: 'Gmail', prefix: 'GOOGLE', authorize: 'https://accounts.google.com/o/oauth2/v2/auth', token: 'https://oauth2.googleapis.com/token',
    scope: 'https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/gmail.send' },
  outlook: { name: 'Outlook', prefix: 'MICROSOFT', authorize: 'https://login.microsoftonline.com/common/oauth2/v2.0/authorize',
    token: 'https://login.microsoftonline.com/common/oauth2/v2.0/token', scope: 'offline_access User.Read Mail.Read Mail.Send' }
};
const graph = 'https://graph.microsoft.com/v1.0';
const gmail = 'https://gmail.googleapis.com/gmail/v1/users/me';
const encode = encodeURIComponent;
export function providerConfig(provider, env = process.env) {
  const config = providers[provider];
  if (!config) throw new Error('Unknown email provider.');
  return { ...config, clientId: env[`${config.prefix}_CLIENT_ID`], clientSecret: env[`${config.prefix}_CLIENT_SECRET`] };
}
export function authorization(provider, redirectUri, env = process.env) {
  const config = providerConfig(provider, env);
  if (!config.clientId || !config.clientSecret) throw new Error(`Configure ${config.prefix}_CLIENT_ID and ${config.prefix}_CLIENT_SECRET before connecting.`);
  const verifier = randomBytes(32).toString('base64url');
  const state = randomBytes(32).toString('base64url');
  const parameters = new URLSearchParams({ client_id: config.clientId, redirect_uri: redirectUri, response_type: 'code', scope: config.scope,
    state, code_challenge: createHash('sha256').update(verifier).digest('base64url'), code_challenge_method: 'S256' });
  if (provider === 'gmail') { parameters.set('access_type', 'offline'); parameters.set('prompt', 'consent'); }
  return { url: `${config.authorize}?${parameters}`, state, verifier, provider, createdAt: Date.now() };
}
async function tokenRequest(provider, values) {
  const config = providerConfig(provider);
  const response = await fetch(config.token, { method: 'POST', redirect: 'error', signal: AbortSignal.timeout(20000),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: config.clientId, client_secret: config.clientSecret, ...values }) });
  if (!response.ok) throw new Error(`Email authorization failed (HTTP ${response.status}). Reconnect your account.`);
  const token = await response.json();
  if (!token.access_token) throw new Error('Provider did not return an access token.');
  return { accessToken: token.access_token, refreshToken: token.refresh_token, expiresAt: Date.now() + (token.expires_in ?? 3600) * 1000 };
}
export async function exchangeCode(pending, code, redirectUri) {
  return tokenRequest(pending.provider, { grant_type: 'authorization_code', code, redirect_uri: redirectUri, code_verifier: pending.verifier });
}
async function api(account, path, options = {}) {
  if (Date.now() > account.expiresAt - 60000) {
    if (!account.refreshToken) throw new Error('Email session expired. Reconnect your account.');
    const token = await tokenRequest(account.provider, { grant_type: 'refresh_token', refresh_token: account.refreshToken });
    Object.assign(account, { ...token, refreshToken: token.refreshToken ?? account.refreshToken });
  }
  const response = await fetch(path, { ...options, redirect: 'error', signal: AbortSignal.timeout(25000),
    headers: { Authorization: `Bearer ${account.accessToken}`, 'Content-Type': 'application/json', ...options.headers } });
  if (!response.ok) throw new Error(`Email provider returned HTTP ${response.status}. Refresh or reconnect your account.`);
  return response.status === 204 || response.status === 202 ? {} : response.json();
}
export async function profile(account) {
  if (account.provider === 'gmail') return (await api(account, `${gmail}/profile`)).emailAddress;
  const user = await api(account, `${graph}/me?$select=mail,userPrincipalName`);
  return user.mail || user.userPrincipalName;
}
function gmailBody(part) {
  if (part.mimeType === 'text/plain' && part.body?.data) return Buffer.from(part.body.data, 'base64url').toString('utf8');
  return (part.parts ?? []).map(gmailBody).filter(Boolean).join('\n');
}
export function normalizeGmail(message, address) {
  const headers = Object.fromEntries((message.payload?.headers ?? []).map(header => [header.name.toLowerCase(), header.value]));
  return { id: message.id, threadId: message.threadId, sender: headers.from ?? '', recipient: headers.to ?? '',
    replyTo: headers['reply-to'] ?? headers.from ?? '', subject: headers.subject ?? '(No subject)',
    body: gmailBody(message.payload ?? {}) || message.snippet || '[No plain-text body available]',
    date: new Date(Number(message.internalDate)).toISOString(), unread: (message.labelIds ?? []).includes('UNREAD'),
    direction: (message.labelIds ?? []).includes('SENT') ? 'sent' : 'received', messageId: headers['message-id'] ?? '', address };
}
export function normalizeOutlook(message, address) {
  return { id: message.id, threadId: message.conversationId, sender: message.from?.emailAddress?.address ?? '',
    recipient: (message.toRecipients ?? []).map(item => item.emailAddress.address).join(', '),
    replyTo: message.replyTo?.[0]?.emailAddress?.address ?? message.from?.emailAddress?.address ?? '',
    subject: message.subject || '(No subject)', body: message.body?.content ?? message.bodyPreview ?? '',
    date: message.receivedDateTime ?? message.sentDateTime, unread: !message.isRead,
    direction: message.from?.emailAddress?.address?.toLowerCase() === address.toLowerCase() ? 'sent' : 'received',
    messageId: message.internetMessageId ?? '', address };
}
export async function listMail(account, cursor = '') {
  if (account.provider === 'gmail') {
    const data = await api(account, `${gmail}/messages?maxResults=40${cursor ? `&pageToken=${encode(cursor)}` : ''}`);
    const messages = [];
    // Bound concurrency to avoid request bursts against provider quotas.
    for (let index = 0; index < (data.messages ?? []).length; index += 5) {
      messages.push(...await Promise.all(data.messages.slice(index, index + 5).map(async item => normalizeGmail(await api(account, `${gmail}/messages/${encode(item.id)}?format=full`), account.address))));
    }
    return { messages, cursor: data.nextPageToken ?? '' };
  }
  const skip = cursor ? Number(cursor) : 0;
  if (!Number.isSafeInteger(skip) || skip < 0) throw new Error('Invalid page cursor.');
  const data = await api(account, `${graph}/me/messages?$top=40&$skip=${skip}&$orderby=receivedDateTime%20desc&$select=id,conversationId,from,toRecipients,replyTo,subject,body,bodyPreview,receivedDateTime,sentDateTime,isRead,internetMessageId`, { headers: { Prefer: 'outlook.body-content-type="text"' } });
  return { messages: (data.value ?? []).map(message => normalizeOutlook(message, account.address)), cursor: data['@odata.nextLink'] ? String(skip + 40) : '' };
}
export async function threadMail(account, message) {
  if (account.provider === 'gmail') {
    const thread = await api(account, `${gmail}/threads/${encode(message.threadId)}?format=full`);
    return (thread.messages ?? []).map(item => normalizeGmail(item, account.address));
  }
  const filter = encode(`conversationId eq '${message.threadId.replaceAll("'", "''")}'`);
  let path = `${graph}/me/messages?$filter=${filter}&$top=100`;
  const messages = [];
  for (let page = 0; path && page < 10; page++) {
    const data = await api(account, path, { headers: { Prefer: 'outlook.body-content-type="text"' } });
    messages.push(...(data.value ?? []).map(item => normalizeOutlook(item, account.address)));
    const next = data['@odata.nextLink'];
    if (next && !next.startsWith(`${graph}/`)) throw new Error('Invalid provider pagination URL.');
    path = next;
  }
  if (path) throw new Error('Conversation exceeds the 1,000 message history limit.');
  return messages;
}
export function mailAddress(value) {
  const match = String(value).match(/<([^<>]+)>/);
  const address = (match ? match[1] : String(value)).trim();
  if (!/^[^\s@<>;,\r\n]+@[^\s@<>;,\r\n]+\.[^\s@<>;,\r\n]+$/.test(address)) throw new Error('Enter one valid recipient email address.');
  return address;
}
export function mimeMessage(account, draft, original) {
  const subject = Buffer.from(draft.subject).toString('base64');
  const headers = [`From: ${mailAddress(account.address)}`, `To: ${mailAddress(draft.to)}`, `Subject: =?UTF-8?B?${subject}?=`,
    'MIME-Version: 1.0', 'Content-Type: text/plain; charset=UTF-8', 'Content-Transfer-Encoding: base64'];
  if (original?.messageId && !/[\r\n]/.test(original.messageId)) headers.push(`In-Reply-To: ${original.messageId}`, `References: ${original.messageId}`);
  return Buffer.from(`${headers.join('\r\n')}\r\n\r\n${Buffer.from(draft.body).toString('base64').match(/.{1,76}/g).join('\r\n')}`).toString('base64url');
}
export async function sendMail(account, draft, original) {
  if (account.provider === 'gmail') {
    const result = await api(account, `${gmail}/messages/send`, { method: 'POST', body: JSON.stringify({ raw: mimeMessage(account, draft, original), ...(original ? { threadId: original.threadId } : {}) }) });
    return { providerId: result.id, status: 'accepted' };
  }
  // Graph reply preserves conversation membership when replying to an existing message.
  if (original) {
    const created = await api(account, `${graph}/me/messages/${encode(original.id)}/createReply`, { method: 'POST', body: '{}' });
    await api(account, `${graph}/me/messages/${encode(created.id)}`, { method: 'PATCH', body: JSON.stringify({ subject: draft.subject,
      body: { contentType: 'Text', content: draft.body }, toRecipients: [{ emailAddress: { address: mailAddress(draft.to) } }] }) });
    await api(account, `${graph}/me/messages/${encode(created.id)}/send`, { method: 'POST', body: '{}' });
    return { providerId: created.id, status: 'accepted' };
  }
  await api(account, `${graph}/me/sendMail`, { method: 'POST', body: JSON.stringify({ message: { subject: draft.subject,
    body: { contentType: 'Text', content: draft.body }, toRecipients: [{ emailAddress: { address: mailAddress(draft.to) } }] }, saveToSentItems: true }) });
  return { status: 'accepted' };
}
