// Prototype integration with the Java agents (prototype branch, step 11.6).
// The contract is a proposal: docs/plans/agent-service-api.md. Replace it when the team agrees on one.

export function agentServiceEndpoint(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:' || !['127.0.0.1', '[::1]'].includes(url.hostname) || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('The agent service must be an HTTP loopback IP origin, such as http://127.0.0.1:4318.');
  }
  return new URL('/v1/reply', url);
}

/** Asks the agents for a reply to one received message of the conversation. Email text stays data. */
export async function requestAgentReply(messages, messageId, endpoint, token, request = fetch) {
  if (typeof token !== 'string' || token.length < 32) throw new Error('Set AGENT_SERVICE_TOKEN (at least 32 characters) to use the agents.');
  const target = agentServiceEndpoint(endpoint);
  const conversation = messages.map(({ id, threadId, sender, recipient, subject, body, date, direction }) =>
    ({ id, threadId, sender, recipient, subject, body, date, direction }));
  const input = JSON.stringify({ messageId, messages: conversation });
  if (input.length > 256000) throw new Error('Conversation exceeds the 256 KB agent service limit.');
  let response;
  try { response = await request(target, {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(60000),
    headers: { 'Content-Type': 'application/json', 'X-SuperMAS-Token': token }, body: input
  }); } catch (error) {
    if (error.name === 'TimeoutError' || error.name === 'AbortError') throw new Error('The agents did not answer within 60 seconds.');
    throw new Error('Cannot reach the agent service. Start it and check AGENT_SERVICE_URL.');
  }
  let result;
  try { result = await response.json(); } catch { throw new Error(`Agent service returned HTTP ${response.status} without JSON.`); }
  if (!response.ok) throw new Error(`Agent service refused the request: ${typeof result?.error === 'string' ? result.error.slice(0, 300) : `HTTP ${response.status}`}`);
  const body = result?.draft?.body;
  if (typeof body !== 'string' || !body.trim() || body.length > 16000) {
    throw new Error(`The agents produced no usable draft${typeof result?.failure?.message === 'string' ? `: ${result.failure.message.slice(0, 300)}` : '.'}`);
  }
  const agent = typeof result.draft.agentId === 'string' ? result.draft.agentId : null;
  const classification = result.classification && typeof result.classification === 'object' ? result.classification : null;
  // Review warnings: sentences in which the draft decides or acts for the owner (docs/plans/agent-service-api.md).
  // Untrusted text: strings only, at most 10, each cut to 400 characters; shown as text, never as HTML.
  const warnings = Array.isArray(result.draft.warnings)
    ? result.draft.warnings.filter(item => typeof item === 'string' && item.trim()).slice(0, 10).map(item => item.trim().slice(0, 400))
    : [];
  return { body: body.trim(), agent, classification, warnings };
}
