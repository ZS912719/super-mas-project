# Implementation Prompts

These task-scoped prompts summarize implementation requirements for review and reproduction. They exclude private conversations, credentials and runtime data. Entries reconstructed on 2026-11-08 cover the current Grafana task; they are not contemporaneous execution logs or evidence of completed checks. See [observation documentation](blackboard-observation.md) for actual verification and limits.

## 2026-11-07: Implement blackboard observation

Implement Grafana observation for SuperMASProject. Read AGENTS.md and pinned committed team guidance first; inspect current source, branch, HEAD, remotes and working-tree changes. Preserve unrelated work. Keep SQLite authoritative and Grafana read-only: no scheduling, task claiming, recovery, approval or sending. Reuse immutable snapshots, attempts and control records through short bounded read transactions. Do not rewrite agents, coordinator or model calls. Exclude private content and secrets by default.

Consult current official Grafana documentation and verify any third-party plugin license, maintenance, compatibility, backend querying and deployment needs. Pin actual versions. Use a separate authenticated Java metadata HTTP API and provisioned Grafana dashboards where supported. Expose overview, expert suitability separately from execution, recorded timeline intervals, decision/execution tables and only source-backed graph relationships. Expose source revisions, collection versus event time, gaps, unknown states, limits and cross-query consistency boundaries. Do not infer lease expiry, model success, contribution adoption or mailbox synchronization.

Provide Windows-compatible startup, versioned provisioning and field semantics. Keep API loopback-only; use protected environment configuration for credentials and bounded refresh/query limits. Validate with synthetic mail, controlled agents and fixed responses: multi-expert contribution, fallback, failure/retry, rejected draft revision, read-only polling, board isolation, source-value agreement and stale/failure presentation. Do not use real mail or paid models. Report interface/configuration/UI verification separately. Update ignored local progress and affected shared knowledge with exact source revisions and limits.

## 2026-11-08: Deploy with Docker and inspect Grafana

Use the already installed, running Docker Desktop to deploy and validate Grafana without installing system services or changing security settings. Provide a repeatable synthetic stack with pinned images/plugin, protected secrets and a read-only fixture. Keep the API on container loopback and publish Grafana only on host loopback. Do not mount the live Windows application database. Exercise actual Infinity queries and compare frames with authoritative API values. Reopen the Grafana UI and inspect board/expert selection, overview, timeline, graph, empty ranges, stale data, API failures and recovery. Restore temporary fault controls after verification and record remaining integration limits.

## 2026-11-08: Publish focused commits and shared knowledge

Review only task-related changes, split large modifications into focused Conventional Commits, commit and push them to the existing code branch. Preserve unrelated work. Update affected team knowledge in its separate knowledge-only checkout with the exact published code revision, actual verification and limitations. Fetch/reconcile concurrent changes without force pushing, then publish a separate knowledge commit and verify both remote references and intended knowledge content.

## 2026-11-08: Maintain implementation instructions

Keep this English prompt record versioned with code and update it after each implementation-related user instruction. Keep identifiers, comments, project-authored documentation and commit messages in English. Restrict README content to dependencies, structure, deployment and usage, using relative paths; move project progress into a separate linked document. Record Chinese local progress in an ignored file. Read task-relevant committed team knowledge when needed and update affected notes after code tasks. Split large changes into focused commits. Keep the developer's conversation language preference out of shared AGENTS.md. Reuse the existing team vault; for future long projects without a remote knowledge vault, propose one before creating it.

## 2026-11-13: Connect desktop drafting and human review

Create a feature branch from the checked-out prototype. Reuse the loopback Java agent service for demo drafting. Validate the version, review status and provenance of generated drafts. Bind each proposal and its warnings to the originating message and preserve that source when the mailbox saves a version. Ignore late responses when the editor has changed. Keep approval scoped to the current saved version and keep provider submission and sent history outside this integration. Use a dedicated demo data directory and keep connected mailboxes on the direct local model adapter. Verify the Node and Java contracts and exercise generation, editing and approval across the local services with synthetic mail.

## Adopt the Ponytail implementation approach on the mailbox review branch

Adapt Ponytail's repository guidance in the root AGENTS.md. Trace affected behavior before editing, prefer existing code and platform facilities, and keep changes focused while preserving validation, readability, and relevant checks. Keep the shared knowledge entry point and completion gate in place.

## Prepare the mailbox draft and review integration for rollout

Keep the Java bridge available in the demo workspace. Expose authenticated, model-free readiness and report contention while allowing only one draft to update the prototype at a time. Show the service state in mailbox settings and distinguish request limits, timeouts and incompatible service responses. Verify Java HTTP behavior, the mailbox bridge, and a synthetic draft and review flow before enabling any provider account integration.

## Package the agent service independently of the source checkout

Build a ZIP from the Java runtime JARs, versioned sample knowledge, launcher, license and usage instructions. Resolve bundled knowledge relative to the launcher, require an external writable data directory, and pass both paths explicitly to AgentService. Inject a shared token through the process environment and use the desktop configuration directory for any local environment file. Inspect the ZIP and start it from an unrelated directory with synthetic demo inputs.

## Fit the desktop mailbox landing page to the window

Use a responsive landing grid with height-aware spacing and headline sizing so the sign-in and demo actions fit within supported Electron window sizes. Hide the landing scrollbar while retaining overflow access for extreme zoom and small browser viewports. Keep the inbox scroll regions independent. Build the frontend and check rendered login controls across short, narrow, default and large viewports.

## Prepare a Windows mailbox release with built-in provider sign-in

Package the project-owned public Google Desktop and Microsoft Entra client IDs with the Electron app so end users can connect through their system browser. Reuse the loopback callback, one-time state, and PKCE. Preserve Web client configuration for development, handle partial consent and token refresh, and use the Graph Mail.Send reply operation after version-specific approval. Keep a demo build available. Offer an opt-in live-mail agent endpoint with isolated request-scoped storage and local inference. Verify the build with fixture IDs and simulated provider requests. Once real registrations are configured, have testers sign in through the provider pages with dedicated Gmail and Outlook accounts and validate reading, draft review, sending, and reconnecting.

## Publish and merge the desktop mailbox branch

Publish the verified mailbox commits and record their behavior and release limits in a separate team knowledge commit. Integrate the complete mailbox branch history into main using a normal fast-forward when main is its ancestor. Verify the remote feature, main and knowledge refs and preserve existing unrelated local changes.

## Summarize project status and next milestones in the README

Keep the root README's dependency, structure, deployment, and usage information. Add an English overview of the implemented desktop mailbox, Java drafting, blackboard and observation, and Windows packaging. Distinguish the demo and fixture evidence from live-account validation. List production OAuth setup, dedicated-account verification, and wider distribution as upcoming milestones. Align the existing OAuth and agent usage notes with the implementation and use relative documentation links.

## Publish the first project release

Set the Maven and mailbox versions to 1.0.0. Use the project tag v1.0.0 to run CI, package the Windows demo and Java distributions, verify checksums, publish a GitHub Release, and deploy the Maven library to GitHub Packages. Clearly describe the desktop demo and pending live OAuth validation. Preserve component tag and manual workflows. Fix the observed Windows-only test assumptions before running release validation.

## Add the course project introduction to the README

Insert the supplied English introduction directly below the root README title. Preserve its wording, paragraph breaks and chapter reference. Treat the pasted backslashes as line continuations. Commit and push the documentation update to main.
