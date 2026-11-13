import { OceanEngine } from '../vendor/ascii-sharks/src/index.ts';

const $ = selector => document.querySelector(selector);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const state = { session: null, messages: [], selected: null, filter: 'All mail', labelFilter: false, query: '', unreadOnly: false, cursor: '', draft: null,
  dirty: false, generation: 0, loading: 0, thread: [], drafts: [], mode: 'mail' };
let landingOcean;
let loadingOcean;
let noticeTimer;
function confirmAction(message) {
  const dialog = $('#confirm-dialog');
  $('#confirm-text').textContent = message;
  dialog.returnValue = 'cancel';
  return new Promise(resolve => {
    dialog.addEventListener('close', () => resolve(dialog.returnValue === 'confirm'), { once: true });
    dialog.showModal();
  });
}
const icons = { 'All mail': '▤', Unread: '◉', Sent: '↗', Drafts: '▧', Research: '◇', Teaching: '□', Administration: '▱', Projects: '⌘', Unclassified: '○' };
function notice(message, error = false) {
  clearTimeout(noticeTimer);
  $('#notice').textContent = message; $('#notice').className = error ? 'error' : ''; $('#notice').hidden = false;
  noticeTimer = setTimeout(() => { $('#notice').hidden = true; }, error ? 12000 : 6000);
}
async function api(path, body) {
  const response = await fetch(`/api/${path}`, body === undefined ? {} : { method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': state.session.csrf }, body: JSON.stringify(body) });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || 'The request failed.');
  return result;
}
async function busy(text, action) {
  state.loading++;
  $('#loading-text').textContent = text; $('#loading').hidden = false;
  loadingOcean ??= new OceanEngine($('#loading-ocean'), [{ kind: 'hammerhead', size: 1 }, { kind: 'classic', size: .7 }]);
  try { return await action(); }
  finally { if (--state.loading === 0) { $('#loading').hidden = true; loadingOcean?.destroy(); loadingOcean = null; } }
}
function name(sender) { return sender.replace(/\s*<.*>/, '').replaceAll('"', ''); }
function initials(sender) { return name(sender).split(/[\s@.]+/).slice(0, 2).map(word => word[0] || '').join('').toUpperCase(); }
function shortDate(date) {
  return new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric' }).format(new Date(date));
}
function fullDate(date) { return new Date(date).toLocaleString('en', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }); }
function tags(labels = []) { return (labels.length ? labels : ['Unclassified']).map(label => `<span class="tag">${escapeHtml(label)}</span>`).join(''); }
function landing() {
  landingOcean?.destroy();
  $('#app').innerHTML = `<main class="landing"><canvas id="landing-ocean" aria-hidden="true"></canvas>
    <header class="landing-header"><div class="brand"><span class="brand-mark">≋</span>SuperMAS <span class="muted">/ MAIL</span></div><span class="muted">A little less noise. A little more focus.</span></header>
    <section class="landing-main"><span class="eyebrow">A CALMER SPACE FOR YOUR INBOX</span><h1>Good ideas.<br>Clear conversations.</h1>
    <p>Your mail, thoughtfully organized. Keep every conversation in view and find the words with a model running on your own machine.</p>
    <div class="landing-actions">${state.session.providers.map(provider => `<button data-connect="${provider.id}" class="${provider.id === 'outlook' ? 'secondary' : ''}" ${provider.configured ? '' : 'disabled'}>Connect ${provider.name} ↗</button>`).join('')}</div>
    ${state.session.providers.some(provider => !provider.configured) ? '<p class="muted">Unavailable sign-in options are not configured in this build.</p>' : ''}
    <button class="secondary demo-start" id="demo-start">Explore the demo →</button><div class="status-line">LOCAL INFERENCE &nbsp; / &nbsp; HUMAN REVIEW &nbsp; / &nbsp; YOUR MAIL</div></section>
    <footer class="landing-footer"><span>Built around your attention.</span><span>ASCII ocean by stackedlol / ascii-sharks</span></footer></main>`;
  landingOcean = new OceanEngine($('#landing-ocean'), [{ kind: 'hammerhead', size: 1 }, { kind: 'classic', size: .8 }, { kind: 'mako', size: .65 }]);
  document.querySelectorAll('[data-connect]').forEach(button => button.onclick = () => connect(button.dataset.connect));
  $('#demo-start').onclick = () => run(async () => {
    state.session = await api('demo', {});
    await openMailbox();
  });
}
async function connect(provider) {
  await run(async () => {
    const result = await busy('Opening a secure connection…', () => api('connect', { provider }));
    window.location.assign(result.url);
  });
}
async function run(action) { try { await action(); } catch (error) { notice(error.message, true); } }
function navButton(label, count, isLabel = false) {
  const active = state.filter === label && state.labelFilter === isLabel;
  return `<button class="nav-button${active ? ' active' : ''}" data-filter="${escapeHtml(label)}" data-label="${isLabel}" title="${escapeHtml(label)}" ${active ? 'aria-current="page"' : ''}><span><span class="icon">${icons[label] || '◇'}</span><span class="nav-label">${escapeHtml(label)}</span></span><span class="count">${count || ''}</span></button>`;
}
function renderShell() {
  landingOcean?.destroy(); landingOcean = null;
  $('#app').innerHTML = `<main class="shell"><aside class="sidebar"><div class="brand"><span class="brand-mark">≋</span><span class="brand-name">SuperMAS</span></div>
    <button class="compose-button" id="compose" title="New message"><span class="icon">＋</span><span class="nav-label">New message</span></button>
    <div class="nav-section">WORKSPACE</div><nav id="main-nav" aria-label="Mailbox"></nav><div class="nav-separator"></div><div class="nav-section">SMART LABELS</div><nav id="label-nav" aria-label="Classification labels"></nav>
    <div class="sidebar-bottom"><button class="nav-button" id="settings" title="Settings"><span><span class="icon">⚙</span><span class="nav-label">Settings & connections</span></span></button>
    <button class="nav-button" id="logout" title="Disconnect"><span><span class="icon">↪</span><span class="nav-label">Disconnect</span></span></button>
    <div class="account"><div class="avatar">${initials(state.session.address)}</div><div class="account-name"><strong>${state.session.demo ? 'Demo workspace' : escapeHtml(state.session.provider)}</strong><small>${escapeHtml(state.session.address)}</small></div></div></div></aside>
    <section class="message-column" aria-label="Message list"><header class="list-header"><div class="section-heading"><h1 id="list-title">All mail</h1><button class="quiet" id="refresh" aria-label="Refresh mailbox">↻</button></div><span class="muted" id="list-subtitle">Your conversations, in one place.</span><div class="search"><span>⌕</span><input id="search" aria-label="Search mail" placeholder="Search your mail"></div></header>
    <div class="list-tools"><span id="list-count"></span><button class="quiet" id="unread-toggle">Unread only</button></div><div class="message-list" id="message-list"></div><button id="load-more" class="load-more" hidden>Load older mail ↓</button></section>
    <section class="reading-column" aria-label="Conversation"><header class="reading-toolbar"><div><button class="quiet mobile-back" id="back">← Back</button><span class="eyebrow">SPACE TO THINK, ROOM TO REPLY</span></div><button class="quiet" id="classify">◇ Classification</button></header>
    ${state.session.demo ? '<div class="demo-banner">DEMO WORKSPACE · Sample messages. Sending is simulated.</div>' : ''}
    <div id="reading" class="reading-scroll"></div></section></main>`;
  $('#compose').onclick = () => run(async () => { if (!await preserveDraft()) return; state.selected = null; state.mode = 'compose'; state.draft = null; state.thread = []; renderReader(); $('.shell').classList.add('reading-open'); });
  $('#refresh').onclick = () => run(() => refresh());
  $('#search').oninput = event => { state.query = event.target.value; renderList(); };
  $('#unread-toggle').onclick = () => { state.unreadOnly = !state.unreadOnly; $('#unread-toggle').classList.toggle('primary', state.unreadOnly); renderList(); };
  $('#load-more').onclick = () => run(() => refresh(true));
  $('#settings').onclick = settings;
  $('#logout').onclick = () => run(async () => {
    if (!await preserveDraft()) return;
    if (!await confirmAction('Disconnect this workspace? Session mail, labels and drafts will be cleared.')) return;
    state.session = await api('logout', {}); state.messages = []; state.draft = null; state.dirty = false; state.selected = null; state.generation++; landing();
  });
  $('#back').onclick = () => $('.shell').classList.remove('reading-open');
  $('#classify').onclick = () => {
    const message = selectedMessage(); if (!message) return notice('Select a message first.');
    $('#classification-json').value = JSON.stringify(message.classification?.raw ?? { schemaVersion: '1', labels: ['Research'], confidence: 0.9, reason: 'Result from an external agent' }, null, 2);
    $('#classification-dialog').showModal();
  };
  renderNav(); renderList(); renderReader();
}
function renderNav() {
  const received = state.messages.filter(message => message.direction !== 'sent');
  $('#main-nav').innerHTML = [navButton('All mail', state.messages.length), navButton('Unread', received.filter(message => message.unread).length),
    navButton('Sent', state.messages.filter(message => message.direction === 'sent').length), navButton('Drafts', state.drafts.filter(draft => !draft.sent).length)].join('');
  const labels = [...new Set(['Research', 'Teaching', 'Administration', 'Projects', ...state.messages.flatMap(message => message.labels || []), 'Unclassified'])];
  $('#label-nav').innerHTML = labels.map(label => navButton(label, state.messages.filter(message => label === 'Unclassified' ? !message.labels?.length : message.labels?.includes(label)).length, true)).join('');
  document.querySelectorAll('[data-filter]').forEach(button => button.onclick = () => run(async () => {
    if (!await preserveDraft()) return;
    state.filter = button.dataset.filter; state.labelFilter = button.dataset.label === 'true'; state.mode = state.filter === 'Drafts' && !state.labelFilter ? 'drafts' : 'mail';
    if (state.mode === 'drafts') state.drafts = (await api('drafts')).drafts;
    renderNav(); renderList();
  }));
}
function filteredMessages() {
  return state.messages.filter(message => {
    const matches = state.labelFilter ? (state.filter === 'Unclassified' ? !message.labels?.length : message.labels?.includes(state.filter)) :
      (state.filter === 'All mail' || (state.filter === 'Unread' && message.unread && message.direction !== 'sent') || (state.filter === 'Sent' && message.direction === 'sent'));
    return matches && (!state.unreadOnly || message.unread) && `${message.sender} ${message.subject} ${message.body}`.toLowerCase().includes(state.query.toLowerCase());
  }).sort((a, b) => b.date.localeCompare(a.date));
}
function renderList() {
  $('#list-title').textContent = state.filter;
  if (state.mode === 'drafts') {
    const drafts = state.drafts.filter(draft => !draft.sent && `${draft.to} ${draft.subject} ${draft.body}`.toLowerCase().includes(state.query.toLowerCase()));
    $('#list-count').textContent = `${drafts.length} drafts`;
    $('#message-list').innerHTML = drafts.length ? drafts.map(draft => `<button class="message-item" data-draft="${draft.id}"><div class="message-subject">${escapeHtml(draft.subject)}</div><div class="message-preview">To ${escapeHtml(draft.to)}<br>${escapeHtml(draft.body)}</div><div class="message-tags"><span class="tag">Version ${draft.version} · ${draft.approvedVersion ? 'Approved' : 'Needs review'}</span></div></button>`).join('') : '<div class="empty">No saved drafts yet.</div>';
    document.querySelectorAll('[data-draft]').forEach(button => button.onclick = () => run(async () => {
      if (!await preserveDraft()) return;
      state.draft = state.drafts.find(draft => draft.id === button.dataset.draft); state.dirty = false;
      state.selected = state.draft.messageId; state.thread = []; state.generation++;
      if (state.selected) state.thread = (await api(`thread?id=${encodeURIComponent(state.selected)}`)).messages;
      renderReader(); $('.shell').classList.add('reading-open');
    }));
    $('#load-more').hidden = true;
    return;
  }
  const messages = filteredMessages();
  $('#list-count').textContent = `${messages.length} messages`;
  $('#message-list').innerHTML = messages.length ? messages.map(message => `<button class="message-item${state.selected === message.id ? ' active' : ''}" data-message="${escapeHtml(message.id)}" aria-label="${escapeHtml(message.subject)}">
    <div class="message-top"><span class="message-sender">${message.unread ? '<span class="unread-dot"></span>' : ''}${escapeHtml(message.direction === 'sent' ? 'You' : name(message.sender))}</span><time class="message-date">${shortDate(message.date)}</time></div>
    <div class="message-subject">${escapeHtml(message.subject)}</div><div class="message-preview">${escapeHtml(message.body)}</div><div class="message-tags">${tags(message.labels)}</div></button>`).join('') : '<div class="empty">No messages here.<br>Try another label or search.</div>';
  document.querySelectorAll('[data-message]').forEach(button => button.onclick = () => run(() => selectMessage(button.dataset.message)));
  $('#load-more').hidden = !state.cursor;
}
function selectedMessage() { return state.messages.find(message => message.id === state.selected); }
async function selectMessage(id) {
  if (!await preserveDraft()) return;
  const sequence = ++state.generation;
  state.selected = id; state.draft = null; state.dirty = false;
  const result = await busy('Gathering the conversation…', () => api(`thread?id=${encodeURIComponent(id)}`));
  if (sequence !== state.generation) return;
  state.thread = result.messages;
  const known = new Map(state.messages.map(message => [message.id, message]));
  result.messages.forEach(message => { if (!known.has(message.id)) known.set(message.id, message); });
  state.messages = [...known.values()];
  const drafts = (await api('drafts')).drafts; state.drafts = drafts;
  if (sequence !== state.generation || state.selected !== id) return;
  state.draft = drafts.filter(draft => draft.messageId === id && !draft.sent).at(-1) ?? null;
  renderNav(); renderList(); renderReader(); $('.shell').classList.add('reading-open');
  checkReplyNeed(id, sequence);
}
/** Asks once per message whether a reply is needed (agents' rules, no model) and shows the note next to Generate. */
async function checkReplyNeed(id, sequence) {
  const message = selectedMessage();
  if (!message || message.direction === 'sent') return;
  if (!message.replyNeed) {
    let result; try { result = await api('reply-need', { messageId: id }); } catch { return; }
    if (!result?.replyNeed) return;
    message.replyNeed = result.replyNeed;
    if (Array.isArray(result.message?.labels)) message.labels = result.message.labels;
  }
  if (sequence !== state.generation || state.selected !== id) return;
  renderReplyNeed(); renderNav(); renderList();
  // Only the tags under the subject; the editor is not re-rendered, so typing is never lost.
  const tagBox = document.querySelector('.thread-summary > div'); if (tagBox) tagBox.innerHTML = tags(selectedMessage()?.labels);
}
function renderReplyNeed() {
  const note = $('#reply-need-note'); const need = selectedMessage()?.replyNeed;
  if (!note) return;
  note.hidden = !need || need.verdict !== 'NOT_NEEDED';
  note.textContent = note.hidden ? '' : `Probably no reply needed${need.reasons.length ? `: ${need.reasons.join('; ')}` : ''}. You can still generate one.`;
}
function renderReader() {
  state.pendingProposal = null;
  state.modelOrigin = false;
  const selected = selectedMessage();
  $('#classify').disabled = !selected;
  if (!selected && state.mode !== 'compose' && !state.draft) {
    $('#reading').innerHTML = '<div class="empty"><span class="eyebrow">A QUIETER INBOX</span><h2>A conversation starts here.</h2><p>Select a message to see its history,<br>or write something new.</p></div>'; return;
  }
  const thread = state.thread;
  $('#reading').innerHTML = `<h1 class="subject-heading">${escapeHtml(selected?.subject ?? state.draft?.subject ?? 'A new conversation')}</h1>
    <div class="thread-summary"><div>${selected ? tags(selected.labels) : '<span class="tag">New message</span>'}</div><span class="muted">${thread.length ? `${thread.length} messages in this conversation` : 'A fresh start'}</span></div>
    ${thread.map((message, index) => `${index === 0 && thread.length > 1 ? '<div class="history-divider">CONVERSATION HISTORY</div>' : ''}<article class="thread-message"><div class="thread-person"><div class="avatar">${initials(message.sender)}</div><div><strong>${escapeHtml(name(message.sender))}${message.direction === 'sent' ? ' <span class="muted">· You</span>' : ''}</strong><small>To ${escapeHtml(message.recipient)}</small></div><time>${fullDate(message.date)}</time></div><p class="mail-body">${escapeHtml(message.body)}</p></article>`).join('')}
    <section class="reply-card" aria-label="Reply editor"><div class="reply-head"><div><strong>${selected ? 'Your reply' : 'New message'}</strong><small>Written by you. Assisted locally.</small><small class="reply-need" id="reply-need-note" hidden></small></div><button id="generate" ${!selected || selected.direction === 'sent' ? 'disabled' : ''}>✧ Generate reply · ${state.session.agentDrafting ? 'SuperMAS agents' : 'Ollama'}</button></div>
    <div class="review-warnings" id="review-warnings" role="alert" hidden></div>
    <div class="reply-fields"><label for="reply-to">To</label><input id="reply-to" type="email" placeholder="name@example.com" required><label for="reply-subject">Subject</label><input id="reply-subject" maxlength="500" required><label for="reply-body" class="sr-only">Message</label><textarea id="reply-body" placeholder="A thoughtful reply starts here…" maxlength="32000"></textarea></div>
    <div class="reply-footer"><span id="draft-status">Your approval is required before sending.</span><div class="reply-actions"><button id="save-draft">Save draft</button><button id="approve">Approve</button><button class="primary" id="send" disabled>${state.session.demo ? 'Simulate send ↗' : 'Send ↗'}</button></div></div></section>
    <p class="muted"><button class="quiet inline-button" id="classification-inline">Import a classification result ↗</button></p>`;
  $('#reply-to').value = state.draft?.to ?? (selected ? extractAddress(selected.direction === 'sent' ? selected.recipient : selected.replyTo || selected.sender) : '');
  $('#reply-subject').value = state.draft?.subject ?? (selected ? (/^re:/i.test(selected.subject) ? selected.subject : `Re: ${selected.subject}`) : '');
  $('#reply-body').value = state.draft?.body ?? '';
  const editor = $('#reply-body');
  let editRevision = 0;
  for (const field of ['#reply-to', '#reply-subject', '#reply-body']) $(field).oninput = () => {
    editRevision++; state.dirty = true; state.modelOrigin = false; updateDraftStatus();
  };
  $('#save-draft').onclick = () => run(async () => {
    editRevision++;
    if (await saveCurrent()) notice('Draft saved for this session.');
  });
  $('#approve').onclick = () => run(async () => {
    editRevision++;
    if (state.dirty || !state.draft) await saveCurrent();
    if (editor !== $('#reply-body') || state.dirty || !state.draft) return;
    const { id, version } = state.draft;
    const { draft } = await api('approve', { id, version });
    if (editor !== $('#reply-body') || state.draft?.id !== id || state.draft.version !== version) return;
    state.draft = draft;
    state.drafts = state.drafts.map(item => item.id === id ? draft : item);
    renderList();
    updateDraftStatus(); notice(`Version ${state.draft.version} approved. Review the recipient, then send when ready.`);
  });
  $('#send').onclick = () => run(async () => {
    const draft = state.draft;
    if (!draft || state.dirty) return notice('Approve the current draft before sending.', true);
    if (!await confirmAction(`${state.session.demo ? 'Simulate sending' : 'Send'} version ${draft.version} to ${draft.to}?\n\n${draft.subject}`)) return;
    try {
      const result = await busy('Submitting your message…', () => api('send', { id: draft.id, version: draft.version }));
      if (state.session.demo) { state.messages.unshift(result.message); state.thread.push(result.message); }
      state.draft = null; state.dirty = false;
      state.drafts = (await api('drafts')).drafts;
      if (!state.selected) state.mode = 'mail';
      if (!state.session.demo) await refresh();
      renderNav(); renderList(); renderReader();
      notice(state.session.demo ? 'Demo send simulated. No email left your machine.' : 'Provider accepted your message. Delivery is not yet confirmed.');
    } catch (error) {
      state.draft = (await api('drafts')).drafts.find(item => item.id === draft.id) ?? draft;
      updateDraftStatus(); throw error;
    }
  });
  const generate = $('#generate');
  generate.onclick = () => run(async () => {
    const messageId = state.selected;
    const sequence = state.generation;
    const revision = editRevision;
    const currentEditor = () => editor === $('#reply-body') && sequence === state.generation && state.selected === messageId && revision === editRevision;
    generate.disabled = true;
    let result;
    try {
      if (editor.value.trim() && !await confirmAction('Replace the current reply text with a newly generated draft?')) return;
      if (!currentEditor()) return;
      result = await busy('Generating a reply…', () => api('generate', { messageId }));
    } finally {
      generate.disabled = false;
    }
    if (!currentEditor()) return;
    editor.value = result.body; state.dirty = true; state.modelOrigin = true;
    state.pendingProposal = result.proposal ?? null;
    const message = selectedMessage();
    if (result.replyNeed && message) message.replyNeed = result.replyNeed;
    if (Array.isArray(result.labels) && message) {
      message.labels = result.labels;
      const tagBox = document.querySelector('.thread-summary > div');
      if (tagBox) tagBox.innerHTML = tags(message.labels);
      renderNav(); renderList();
    }
    renderWarnings(); renderReplyNeed();
    updateDraftStatus();
    notice('Draft generated. Review it before approving.');
  });
  $('#classification-inline').onclick = () => $('#classify').click();
  renderWarnings(); renderReplyNeed();
  updateDraftStatus();
}
function renderWarnings() {
  const box = $('#review-warnings'); if (!box) return;
  const items = (state.pendingProposal ?? state.draft?.proposal)?.warnings ?? [];
  box.hidden = !items.length;
  box.innerHTML = items.length ? `<strong>Review warnings from the original generated text</strong><ul>${items.map(item => `<li>${escapeHtml(item)}</li>`).join('')}</ul>` : '';
}
function extractAddress(value) { return value.match(/<([^<>]+)>/)?.[1] ?? value; }
function updateDraftStatus() {
  const draft = state.draft;
  const approved = draft && !state.dirty && draft.approvedVersion === draft.version && !draft.sent;
  $('#send').disabled = !approved || !state.session.canSend;
  $('#approve').disabled = Boolean(draft?.sent);
  $('#save-draft').disabled = Boolean(draft?.sent);
  $('#draft-status').textContent = draft?.sendUncertain ? 'Outcome uncertain. Check your provider Sent folder.' : draft?.sent ? 'Submitted to provider' : !state.session.canSend ? 'Read-only mailbox. Reconnect to grant sending permission.' : state.dirty ? 'Unsaved changes · approval required' : draft ? `Version ${draft.version} · ${approved ? 'approved' : 'awaiting review'}` : 'Your approval is required before sending.';
}
async function saveCurrent() {
  const editor = $('#reply-body');
  if (!editor) return;
  if (!$('#reply-to').reportValidity() || !$('#reply-subject').reportValidity()) throw new Error('Enter a recipient and subject first.');
  const proposal = state.pendingProposal;
  const input = { id: state.draft?.id, version: state.draft?.version, messageId: state.selected, proposalId: proposal?.id,
    to: $('#reply-to').value, subject: $('#reply-subject').value, body: editor.value, origin: state.modelOrigin ? 'model' : 'user' };
  const result = await api('drafts', input);
  if (editor !== $('#reply-body') || state.selected !== input.messageId) return;
  state.draft = result.draft;
  state.dirty = state.pendingProposal !== proposal || editor.value !== input.body || $('#reply-to').value !== input.to || $('#reply-subject').value !== input.subject;
  if (!state.dirty) { state.pendingProposal = null; state.modelOrigin = false; }
  state.drafts = [...state.drafts.filter(draft => draft.id !== result.draft.id), result.draft];
  renderNav(); renderWarnings(); updateDraftStatus();
  return result.draft;
}
async function preserveDraft() {
  if (!state.dirty) return true;
  try { await saveCurrent(); return !state.dirty; }
  catch (error) {
    if (await confirmAction(`${error.message}\n\nLeave this unfinished draft without saving?`)) { state.dirty = false; state.modelOrigin = false; return true; }
    return false;
  }
}
async function refresh(older = false) {
  const result = await busy(older ? 'Reaching a little further back…' : 'Bringing your mailbox up to date…', () => api(`messages${older && state.cursor ? `?cursor=${encodeURIComponent(state.cursor)}` : ''}`));
  state.messages = result.messages; state.cursor = result.cursor;
  renderNav(); renderList();
}
async function openMailbox() {
  await busy('Preparing your workspace…', async () => {
    const result = await api('messages'); state.messages = result.messages; state.cursor = result.cursor;
    state.drafts = (await api('drafts')).drafts; state.filter = 'All mail'; state.labelFilter = false; state.mode = 'mail'; state.draft = null; state.dirty = false;
    renderShell();
  });
  const first = filteredMessages().find(message => message.direction !== 'sent');
  if (first) await selectMessage(first.id);
}
function settings() {
  $('#settings-content').innerHTML = `<p>Your workspace uses ${state.session.demo ? 'sample data' : escapeHtml(state.session.provider)}. Connecting another account replaces the active workspace.</p>
    ${state.session.providers.map(provider => `<div class="connection-row"><strong>${escapeHtml(provider.name)}</strong><small>${provider.configured ? 'Ready to connect' : 'Unavailable in this build'}</small><button data-setting-connect="${provider.id}" ${provider.configured ? '' : 'disabled'}>Connect ${provider.name} ↗</button></div>`).join('')}
    <div class="connection-row"><strong>Reply drafting</strong><small>${state.session.agentDrafting ? 'SuperMAS agents · Demo workspace' : `Ollama · ${escapeHtml(state.session.model)}`}</small>
    ${state.session.agentDrafting ? '<p id="agent-status" role="status">Checking the agent service…</p><button class="quiet" id="check-agents">Check again</button>' : ''}</div>
    <div class="connection-row"><strong>Agent integration</strong><small>Not implemented</small><p>Import classification JSON from a message to preview labels. Unknown JSON fields are retained by the adapter.</p></div>
    <p>Session data is kept in server memory for up to 8 hours. Disconnecting or restarting the server clears tokens, drafts, and labels. Provider mail remains at your provider.</p>`;
  document.querySelectorAll('[data-setting-connect]').forEach(button => button.onclick = () => run(async () => {
    if (!await preserveDraft()) return;
    $('#settings-dialog').close();
    if (!await confirmAction('Connect another account? A successful connection clears the current workspace drafts and labels.')) return;
    await connect(button.dataset.settingConnect);
  }));
  $('#settings-dialog').showModal();
  if (state.session.agentDrafting) {
    const status = $('#agent-status');
    const check = $('#check-agents');
    check.onclick = async () => {
      const descriptions = {
        ready: 'Agent service ready for a draft.',
        busy: 'The agents are generating a draft. Try again shortly.',
        configuration: 'Check the agent service address and shared token.',
        incompatible: 'The agent service version is incompatible.',
        unavailable: 'The agent service did not respond. Check that it is running.'
      };
      check.disabled = true;
      status.textContent = 'Checking the agent service…';
      const result = await api('agent-status').catch(() => null);
      status.textContent = descriptions[result?.status] ?? descriptions.unavailable;
      check.disabled = false;
    };
    check.click();
  }
}
document.querySelectorAll('[data-close]').forEach(button => button.onclick = () => document.getElementById(button.dataset.close).close());
$('#classification-form').onsubmit = event => {
  event.preventDefault();
  run(async () => {
    const result = JSON.parse($('#classification-json').value);
    const response = await api('classification', { messageId: state.selected, result });
    state.messages = state.messages.map(message => message.id === state.selected ? response.message : message);
    $('#classification-dialog').close(); renderNav(); renderList();
    if (!state.dirty) renderReader();
    notice('Classification labels applied. Original JSON retained.');
  });
};
window.addEventListener('beforeunload', event => { if (state.dirty) { event.preventDefault(); event.returnValue = ''; } });
await run(async () => {
  state.session = await api('session');
  if (new URLSearchParams(location.search).has('connected')) history.replaceState({}, '', '/');
  if (state.session.connected) await openMailbox(); else landing();
});
