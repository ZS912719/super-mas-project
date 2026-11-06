import { randomUUID } from 'node:crypto';
import { mailAddress } from './providers.js';

export function saveDraft(session, input) {
  const previous = input.id ? session.drafts.get(input.id) : null;
  if (input.id && !previous) throw new Error('Draft not found.');
  if (previous?.sending || previous?.sent) throw new Error('This draft has already been submitted for sending.');
  if (previous && input.version !== previous.version) throw new Error('Draft changed. Reload the current version.');
  const to = mailAddress(input.to);
  if (typeof input.subject !== 'string' || !input.subject.trim() || input.subject.length > 500 || /[\r\n]/.test(input.subject)) throw new Error('Enter a subject of up to 500 characters.');
  if (typeof input.body !== 'string' || !input.body.trim() || input.body.length > 32000) throw new Error('Enter a message of up to 32,000 characters.');
  const messageId = previous?.messageId ?? input.messageId ?? null;
  if (messageId && !session.messages.has(messageId)) throw new Error('Original message not found.');
  const draft = { id: previous?.id ?? randomUUID(), version: (previous?.version ?? 0) + 1, to, subject: input.subject,
    body: input.body, messageId, approvedVersion: null, origin: input.origin === 'model' ? 'model' : 'user',
    history: [...(previous?.history ?? []), ...(previous ? [{ version: previous.version, body: previous.body, subject: previous.subject, to: previous.to, origin: previous.origin }] : [])] };
  session.drafts.set(draft.id, draft);
  return draft;
}
export function approveDraft(session, id, version) {
  const draft = session.drafts.get(id);
  if (!draft || draft.version !== version || draft.sending || draft.sent) throw new Error('Only the current unsent draft version can be approved.');
  draft.approvedVersion = version;
  return draft;
}
export function beginSend(session, id, version) {
  const draft = session.drafts.get(id);
  if (!draft || draft.version !== version || draft.approvedVersion !== version || draft.sending || draft.sent) throw new Error('Approve the current draft version before sending.');
  draft.sending = true;
  return draft;
}
