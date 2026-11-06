# Mailbox validation

## Desktop conversion - 2026-11-03

Branch: `feat/mailbox-desktop`.

- `npm run build`: passed.
- `npm test`: 11 tests passed, including desktop navigation restrictions, cross-cookie OAuth handoff, replay rejection and cancellation on logout. Provider authorization exchanges in the handoff test are simulated.
- `npm run package:win`: passed. Generated `dist/SuperMAS-Mail-0.1.0-win-x64.exe` (portable Windows x64 application).
- The packaged application was launched and a window titled `SuperMAS Mail` was detected. A first Computer Use capture timed out; a subsequent source-run Electron window was inspected successfully. Its start screen rendered the grayscale shark animation, and clicking Explore the demo opened the mailbox with three chronological conversation messages in the same window. In the desktop UI, a sample reply was edited, saved as version 1, and approved; the submission button became enabled. Sending was tested through HTTP simulation, not performed through the desktop UI.
- The packaged archive was checked against the desktop source files and contains no `.env` files. The runtime and portable executable were built successfully.
- `npm audit --omit=dev`: no production dependencies reported vulnerable. Installation reported 15 moderate development/build dependency findings; these dependencies are not shipped as application runtime modules.
- OAuth credentials and local model configuration remain user prerequisites. Live provider/model behavior has not been revalidated during the desktop conversion. No real email was sent.
- The executable is unsigned. Code signing and a public native-client authorization design remain deployment work before wider distribution.

The following section records the earlier browser prototype checks; those observations do not replace desktop interaction testing.

## Earlier browser prototype

Date: 2026-10-24. Branch: `feat/intelligent-mailbox`.

## Completed checks

- `npm run build`: passed. Browser bundle includes the vendored ASCII sharks TypeScript engine.
- `npm test`: 9 tests passed, including a real loopback HTTP workflow with simulated sending. Gmail and Outlook HTTP interactions are mocked in adapter tests; no live provider messages were sent.
- `mvn package`: passed. Existing Java contracts compile and package without changes.
- Browser: start screen and animated grayscale ocean rendered; demo workspace opened; the selected conversation displayed three received/sent history messages in chronological order.
- Browser: JSON import added a new Review label and retained an unknown nested field. Existing navigation counts updated.
- Browser: saving/approving a draft enabled Send; subsequent editing disabled Send. Reload restored the saved draft and its approval state.
- Demo submission, duplicate prevention, stale approval rejection and session isolation passed through the HTTP test. The in-app browser became unresponsive during final send-button testing, so a complete browser submission is not claimed as verified.

## Live prerequisites and limitations

- OAuth client credentials were not configured. Real Gmail/Outlook login, refresh, mailbox retrieval and sending remain unverified against live accounts.
- No Ollama service was available at `127.0.0.1:11434`. Request construction, loopback restriction, budgets and output validation were checked with test responses; live model inference remains unverified.
- Agent routing, classification execution and blackboard orchestration are intentionally unimplemented. Classification imports are application adapters for externally supplied results.
- Session storage is in memory. Restarting or disconnecting clears labels, drafts and tokens.
- Responsive breakpoints are implemented; a dedicated mobile-device browser pass remains outstanding.

These limits are also documented in the setup guide. No real email was sent during validation.
