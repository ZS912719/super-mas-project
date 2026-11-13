// Prototype integration with the Java agents (prototype branch, step 11.6).
// The contract is a proposal: docs/plans/agent-service-api.md. Replace it when the team agrees on one.

const VERDICTS = new Set(['NEEDED', 'NOT_NEEDED', 'UNSURE']);
/** The agents' judgement whether a reply is needed; untrusted: a known verdict and at most 5 short reasons, or null. */
export function normalizeReplyNeed(value) {
  if (!value || typeof value !== 'object' || !VERDICTS.has(value.verdict)) return null;
  const reasons = Array.isArray(value.reasons) ? value.reasons.filter(r => typeof r === 'string' && r.trim()).slice(0, 5).map(r => r.trim().slice(0, 200)) : [];
  return { verdict: value.verdict, reasons };
}

export function agentServiceEndpoint(value, path = '/v1/reply') {
  const url = new URL(value);
  if (url.protocol !== 'http:' || !['127.0.0.1', '[::1]'].includes(url.hostname) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('The agent service must be an HTTP loopback IP origin, such as http://127.0.0.1:4318.');
  }
  return new URL(path, url);
}

export async function checkAgentService(endpoint, token) {
  let target;
  try { target = agentServiceEndpoint(endpoint, '/v1/health'); }
  catch { return 'configuration'; }
  if (typeof token !== 'string' || token.length < 32) return 'configuration';
  try {
    const response = await fetch(target, {
      redirect: 'error', signal: AbortSignal.timeout(2500), headers: { 'X-SuperMAS-Token': token }
    });
    if (response.status === 401 || response.status === 403) return 'configuration';
    if (response.status === 404) return 'incompatible';
    if (!response.ok) return 'unavailable';
    const result = await response.json();
    return result?.schemaVersion === '1' && ['ready', 'busy'].includes(result.status) ? result.status : 'incompatible';
  } catch {
    return 'unavailable';
  }
}

/** Asks the agents, without a model, whether the selected received message needs a reply (plan reply-needed). */
export async function requestReplyNeed(messages, messageId, endpoint, token, request = fetch) {
  if (typeof token !== 'string' || token.length < 32) throw new Error('Set AGENT_SERVICE_TOKEN (at least 32 characters) to use the agents.');
  const target = agentServiceEndpoint(endpoint, '/v1/reply-need');
  const conversation = messages.map(({ id, threadId, sender, recipient, subject, body, date, direction }) =>
    ({ id, threadId, sender, recipient, subject, body, date, direction }));
  const input = JSON.stringify({ messageId, messages: conversation });
  if (Buffer.byteLength(input) > 256000) return null;
  let response;
  try { response = await request(target, {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(10000),
    headers: { 'Content-Type': 'application/json', 'X-SuperMAS-Token': token }, body: input
  }); } catch { return null; }
  if (!response.ok) return null;
  try {
    const result = await response.json();
    return result?.schemaVersion === '1' ? normalizeReplyNeed(result.replyNeed) : null;
  } catch { return null; }
}

/** Asks the agents for a reply to one received message of the conversation. Email text stays data. */
export async function requestAgentReply(messages, messageId, endpoint, token, request = fetch) {
  if (typeof token !== 'string' || token.length < 32) throw new Error('Set AGENT_SERVICE_TOKEN (at least 32 characters) to use the agents.');
  const target = agentServiceEndpoint(endpoint);
  const conversation = messages.map(({ id, threadId, sender, recipient, subject, body, date, direction }) =>
    ({ id, threadId, sender, recipient, subject, body, date, direction }));
  const input = JSON.stringify({ messageId, messages: conversation });
  if (Buffer.byteLength(input) > 256000) throw new Error('Conversation exceeds the 256 KB agent service limit.');
  if (conversation.length > 200 || conversation.some(message =>
    Object.values(message).some(value => typeof value === 'string' && value.length > 32000))) {
    throw new Error('Conversation exceeds the agent service limit of 200 messages or 32,000 characters per field.');
  }
  let response;
  try { response = await request(target, {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(60000),
    headers: { 'Content-Type': 'application/json', 'X-SuperMAS-Token': token }, body: input
  }); } catch (error) {
    if (error.name === 'TimeoutError' || error.name === 'AbortError') throw new Error('The agents did not answer within 60 seconds.');
    throw new Error('Cannot reach the agent service. Start it and check AGENT_SERVICE_URL.');
  }
  let result;
  try { result = await response.json(); } catch (error) {
    if (error.name === 'AbortError' || error.name === 'TimeoutError') throw new Error('The agents did not answer within 60 seconds.');
    throw new Error(`Agent service returned HTTP ${response.status} without JSON.`);
  }
  if (!response.ok) throw new Error(`Agent service refused the request: ${typeof result?.error === 'string' ? result.error.slice(0, 300) : `HTTP ${response.status}`}`);
  if (result?.schemaVersion !== '1') throw new Error('Unsupported agent service response version.');
  const body = result?.draft?.body;
  if (result.status !== 'AWAITING_REVIEW' || typeof body !== 'string' || !body.trim() || body.length > 16000) {
    throw new Error(`The agents produced no usable draft${typeof result?.failure?.message === 'string' ? `: ${result.failure.message.slice(0, 300)}` : '.'}`);
  }
  const { boardId } = result;
  const { version } = result.draft;
  if (typeof boardId !== 'string' || !boardId.trim() || boardId.length > 256 || !Number.isSafeInteger(version) || version < 1) {
    throw new Error('Agent service returned invalid draft provenance.');
  }
  const agent = typeof result.draft.agentId === 'string' ? result.draft.agentId : null;
  const classification = result.classification && typeof result.classification === 'object' ? result.classification : null;
  // Review warnings: sentences in which the draft decides or acts for the owner (docs/plans/agent-service-api.md).
  // Untrusted text: strings only, at most 10, each cut to 400 characters; shown as text, never as HTML.
  const warnings = Array.isArray(result.draft.warnings)
    ? result.draft.warnings.filter(item => typeof item === 'string' && item.trim()).slice(0, 10).map(item => item.trim().slice(0, 400))
    : [];
  return { body: body.trim(), boardId, version, agent, classification, warnings, replyNeed: normalizeReplyNeed(result.replyNeed) };
}
