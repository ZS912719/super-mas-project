export function demoMessages() {
  const rows = [
    ['research', 'Maya Chen <maya@university.example>', 'Research review · next steps', 'Thank you for sharing the revised architecture. Could we meet on Tuesday to review the evaluation plan? Please send a short outline before the meeting.', 'Research', 0, true],
    ['teaching', 'Alex Morgan <alex@university.example>', 'A question about the assignment', 'Hello, I am working through the blackboard exercise. Should each specialist keep its own state, or should all contributions live on the shared board?', 'Teaching', 1, true],
    ['admin', 'Graduate Office <office@university.example>', 'Your annual registration', 'The registration window opens next Monday. Please review your details and confirm your research supervisor before the end of the month.', 'Administration', 2, false],
    ['project', 'Sam Rivera <sam@lab.example>', 'Mailbox prototype feedback', 'The monochrome direction looks good. It would help to keep the entire conversation visible while drafting a response. Let us know when the next build is ready.', 'Projects', 3, false],
    ['reading', 'Library <library@university.example>', 'Your reading list is ready', 'The papers you requested are now available in your reading list. You can access them through the university library portal.', 'Unclassified', 5, false],
    ['research', 'You <demo@supermas.example>', 'Research review · next steps', 'I have attached the revised architecture notes. The agent runtime is still a set of contracts; this review focuses on the mailbox application.', 'Research', 6, false],
    ['research', 'Maya Chen <maya@university.example>', 'Research review · next steps', 'Could you share the current architecture notes before we discuss the evaluation plan?', 'Research', 8, false],
    ['event', 'Lab seminar <seminar@lab.example>', 'Friday: systems seminar', 'Join us on Friday for a discussion of cooperative problem solving. The session starts at 14:00 in room 204.', 'Unclassified', 10, false]
  ];
  return rows.map(([threadId, sender, subject, body, label, days, unread], index) => ({ id: `demo-${index}`, threadId, sender,
    replyTo: sender, recipient: 'demo@supermas.example', subject, body, date: new Date(Date.now() - days * 86400000 - 3600000).toISOString(),
    unread, direction: sender.startsWith('You') ? 'sent' : 'received', labels: label === 'Unclassified' ? [] : [label],
    classification: label === 'Unclassified' ? null : { labels: [label], reason: 'Sample classification supplied by the demo fixture.', confidence: null, raw: { source: 'demo-fixture', labels: [label] } } }));
}
