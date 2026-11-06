import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { randomBytes, randomUUID } from 'node:crypto';
import { authorization, exchangeCode, profile, providers, providerConfig, listMail, threadMail, sendMail } from './lib/providers.js';
import { normalizeClassification } from './lib/classification.js';
import { generateReply } from './lib/model.js';
import { requestAgentReply } from './lib/agents.js';
import { demoMessages } from './lib/demo.js';
import { saveDraft, approveDraft, beginSend } from './lib/drafts.js';

export function createMailboxServer({ port = Number(process.env.PORT || 4317), send = sendMail,
  externalOAuth = false, exchangeAuthorizationCode = exchangeCode, loadProfile = profile } = {}) {
  const origin = `http://127.0.0.1:${port}`;
  const sessions = new Map();
  const pendingAuthorizations = new Map();
  const cookieName = 'supermas_session';
  const redirectUri = `${origin}/auth/callback`;
  function clearAccount(session) {
    if (session.pending) pendingAuthorizations.delete(session.pending.state);
    session.account = null; session.demo = false; session.messages.clear(); session.drafts.clear(); session.cursor = ''; session.pending = null; session.authorizationAttempt = null;
  }
  function sessionView(session) {
    return { csrf: session.csrf, connected: Boolean(session.account || session.demo), demo: session.demo,
      address: session.demo ? 'demo@supermas.example' : session.account?.address,
      provider: session.demo ? 'demo' : session.account?.provider,
      model: process.env.LOCAL_MODEL_NAME || 'llama3.2',
      providers: Object.entries(providers).map(([id, config]) => ({ id, name: config.name, configured: Boolean(providerConfig(id).clientId && providerConfig(id).clientSecret) })) };
  }
  const server = http.createServer(async (request, response) => {
    response.setHeader('X-Content-Type-Options', 'nosniff');
    response.setHeader('Referrer-Policy', 'no-referrer');
    response.setHeader('Cache-Control', 'no-store');
    response.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    const json = (data, status = 200) => { response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(data)); };
    const redirect = path => { response.writeHead(302, { Location: path }); response.end(); };
    try {
      if (request.headers.host !== `127.0.0.1:${port}`) return json({ error: `Open ${origin} to use this mailbox.` }, 403);
      const url = new URL(request.url, origin);
      const id = request.headers.cookie?.split(';').map(item => item.trim()).find(item => item.startsWith(`${cookieName}=`))?.slice(cookieName.length + 1);
      let session = sessions.get(id);
      if (session && Date.now() - session.touched > 8 * 3600000) { sessions.delete(id); session = null; }
      if (!session) {
        const sessionId = randomBytes(32).toString('hex');
        session = { csrf: randomBytes(32).toString('hex'), messages: new Map(), drafts: new Map(), account: null, demo: false, cursor: '', touched: Date.now() };
        sessions.set(sessionId, session);
        response.setHeader('Set-Cookie', `${cookieName}=${sessionId}; HttpOnly; SameSite=Lax; Path=/; Max-Age=28800`);
      }
      session.touched = Date.now();
      if (request.method === 'POST') {
        if (request.headers.origin !== origin || request.headers['x-csrf-token'] !== session.csrf) return json({ error: 'Invalid request origin or session token. Reload the application.' }, 403);
        if (!request.headers['content-type']?.startsWith('application/json')) return json({ error: 'JSON content is required.' }, 415);
      } else if (request.method !== 'GET') return json({ error: 'Method not allowed.' }, 405);
      let body = {};
      if (request.method === 'POST') {
        const chunks = []; let length = 0;
        for await (const chunk of request) { length += chunk.length; if (length > 128000) throw new Error('Request exceeds the 128 KB limit.'); chunks.push(chunk); }
        body = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
      }
      const path = url.pathname;
      if (path === '/api/session' && request.method === 'GET') return json(sessionView(session));
      if (path === '/auth/callback' && request.method === 'GET') {
        // The system browser has a different cookie jar from the desktop window.
        // Recover only the initiating desktop session using its single-use state.
        if (externalOAuth) {
          const state = url.searchParams.get('state');
          const initiatingSession = pendingAuthorizations.get(state);
          pendingAuthorizations.delete(state);
          if (!initiatingSession) throw new Error('Invalid or expired authorization. Please reconnect.');
          session = initiatingSession;
        }
        const pending = session.pending;
        session.pending = null;
        if (!pending || Date.now() - pending.createdAt > 600000 || url.searchParams.get('state') !== pending.state) throw new Error('Invalid or expired authorization. Please reconnect.');
        if (url.searchParams.has('error')) throw new Error('Email authorization was declined. You can try again.');
        const code = url.searchParams.get('code');
        if (!code) throw new Error('Missing authorization code.');
        const token = await exchangeAuthorizationCode(pending, code, redirectUri);
        const account = { provider: pending.provider, ...token };
        account.address = await loadProfile(account);
        if (session.authorizationAttempt !== pending.state) throw new Error('Authorization was cancelled or replaced. Please reconnect.');
        clearAccount(session); session.account = account;
        if (externalOAuth) {
          response.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
          response.end('Account connected. You can close this browser tab and return to SuperMAS Mail.');
          server.emit('account-connected');
          return;
        }
        return redirect('/?connected=1');
      }
      if (path === '/api/connect' && request.method === 'POST') {
        const pending = authorization(body.provider, redirectUri);
        if (session.pending) pendingAuthorizations.delete(session.pending.state);
        session.pending = pending;
        session.authorizationAttempt = pending.state;
        if (externalOAuth) pendingAuthorizations.set(pending.state, session);
        return json({ url: pending.url });
      }
      if (path === '/api/demo' && request.method === 'POST') {
        clearAccount(session); session.demo = true;
        demoMessages().forEach(message => session.messages.set(message.id, message));
        return json(sessionView(session));
      }
      if (path === '/api/logout' && request.method === 'POST') { clearAccount(session); session.csrf = randomBytes(32).toString('hex'); return json(sessionView(session)); }
      if (path.startsWith('/api/')) {
        if (!session.account && !session.demo) return json({ error: 'Connect an account or open the demo first.' }, 401);
        if (path === '/api/messages' && request.method === 'GET') {
          if (session.account) {
            const result = await listMail(session.account, url.searchParams.get('cursor') || '');
            for (const message of result.messages) {
              const previous = session.messages.get(message.id);
              session.messages.set(message.id, { ...message, labels: previous?.labels ?? [], classification: previous?.classification ?? null });
            }
            session.cursor = result.cursor;
          }
          return json({ messages: [...session.messages.values()], cursor: session.cursor });
        }
        if (path === '/api/thread' && request.method === 'GET') {
          const message = session.messages.get(url.searchParams.get('id'));
          if (!message) throw new Error('Message not found.');
          const messages = session.demo ? [...session.messages.values()].filter(item => item.threadId === message.threadId) : await threadMail(session.account, message);
          for (const item of messages) session.messages.set(item.id, { ...item, labels: session.messages.get(item.id)?.labels ?? message.labels ?? [], classification: session.messages.get(item.id)?.classification ?? null });
          return json({ messages: messages.sort((a, b) => a.date.localeCompare(b.date)) });
        }
        if (path === '/api/classification' && request.method === 'POST') {
          const message = session.messages.get(body.messageId);
          if (!message) throw new Error('Message not found.');
          const result = normalizeClassification(body.result);
          message.classification = result; message.labels = result.labels;
          return json({ message });
        }
        if (path === '/api/generate' && request.method === 'POST') {
          const message = session.messages.get(body.messageId);
          if (!message) throw new Error('Select a received message first.');
          if (message.direction === 'sent') throw new Error('Select a received message to generate a reply.');
          const messages = session.demo ? [...session.messages.values()].filter(item => item.threadId === message.threadId) : await threadMail(session.account, message);
          if (process.env.AGENT_SERVICE_URL) {
            // Prototype: the Java agents write the draft; their routing becomes the message's classification.
            const result = await requestAgentReply(messages.sort((a, b) => a.date.localeCompare(b.date)), message.id,
              process.env.AGENT_SERVICE_URL, process.env.AGENT_SERVICE_TOKEN);
            if (result.classification) {
              const classification = normalizeClassification(result.classification);
              message.classification = classification; message.labels = classification.labels;
            }
            return json({ body: result.body, origin: 'model', agent: result.agent });
          }
          const reply = await generateReply(messages.sort((a, b) => a.date.localeCompare(b.date)), process.env.LOCAL_MODEL_NAME || 'llama3.2', process.env.LOCAL_MODEL_URL || 'http://127.0.0.1:11434');
          return json({ body: reply, origin: 'model' });
        }
        if (path === '/api/drafts' && request.method === 'POST') return json({ draft: saveDraft(session, body) });
        if (path === '/api/drafts' && request.method === 'GET') return json({ drafts: [...session.drafts.values()] });
        if (path === '/api/approve' && request.method === 'POST') return json({ draft: approveDraft(session, body.id, body.version) });
        if (path === '/api/send' && request.method === 'POST') {
          const draft = beginSend(session, body.id, body.version);
          try {
            const original = session.messages.get(draft.messageId);
            const result = session.demo ? { status: 'simulated' } : await send(session.account, draft, original);
            draft.sent = true; draft.sending = false;
            const sent = { id: randomUUID(), threadId: original?.threadId ?? randomUUID(), sender: session.account?.address ?? 'demo@supermas.example',
              recipient: draft.to, subject: draft.subject, body: draft.body, direction: 'sent', date: new Date().toISOString(),
              labels: original?.labels ?? [], unread: false, simulated: session.demo, status: result.status };
            // Provider acceptance is not confirmed Sent history. Real mail appears through sync.
            if (session.demo) session.messages.set(sent.id, sent);
            return json({ result, message: sent });
          } catch (error) {
            // A timed-out send may have reached the provider. Prevent blind duplicate retries.
            draft.sending = false; draft.sent = true; draft.sendUncertain = true;
            throw new Error('Send outcome is uncertain. Check the provider Sent folder before creating another draft.');
          }
        }
        return json({ error: 'API route not found.' }, 404);
      }
      const assets = { '/': ['index.html', 'text/html; charset=utf-8'], '/app.js': ['app.js', 'text/javascript; charset=utf-8'], '/style.css': ['style.css', 'text/css; charset=utf-8'] };
      const asset = assets[path];
      if (!asset) return json({ error: 'Not found.' }, 404);
      const data = await readFile(new URL(`./public/${asset[0]}`, import.meta.url));
      response.writeHead(200, { 'Content-Type': asset[1] }); response.end(data);
    } catch (error) {
      if (!response.headersSent) json({ error: error.name === 'TimeoutError' ? 'The request timed out. Please check the service and try again.' : error.message }, 400);
      else response.end();
    }
  });
  const cleanup = setInterval(() => {
    for (const [id, session] of sessions) if (Date.now() - session.touched > 8 * 3600000) { clearAccount(session); sessions.delete(id); }
    for (const [state, session] of pendingAuthorizations) if (!session.pending || Date.now() - session.pending.createdAt > 600000) pendingAuthorizations.delete(state);
  }, 60000);
  cleanup.unref(); server.on('close', () => clearInterval(cleanup));
  return server;
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const port = Number(process.env.PORT || 4317);
  createMailboxServer({ port }).listen(port, '127.0.0.1', () => console.log(`SuperMAS Mail is ready at http://127.0.0.1:${port}`));
}
