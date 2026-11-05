---
id: team-build
type: operations
scope: team-development
updated: 2026-11-05
status: active
---

# Build, CI and Delivery

Commands run from the repository root unless a different directory is specified. Select a branch containing the component first; see [[Project-Status]]. Sources: `pom.xml`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `docs/ci-cd.md`; feature-branch runtime/mailbox documentation.

## Main baseline

JDK 17 and Maven 3.9+:

```powershell
mvn -B -ntp clean verify
```

On the inspected main baseline this compiles/packages interfaces; there are no Java behavior tests or application dependencies. It does not run an email-processing workflow. The ordinary library JAR is not a complete executable distribution.

## Blackboard feature branch

The feature POM adds SQLite JDBC, Jackson and JUnit; consult its POM for exact pinned versions.

```powershell
mvn package
./scripts/run-blackboard.ps1 -Mode demo
./scripts/run-blackboard.ps1 -Mode audit
./scripts/run-blackboard.ps1 -Mode watch
./scripts/check-formal.ps1
```

The demo writes fixed sample data without model/provider calls. `watch` performs recovery/export and stops with Ctrl+C. It never runs agents. Formal checks require nuXmv; the main branch provides `scripts/install-nuxmv.ps1`, while feature-branch `formal/README.md` records model prerequisites and expected counterexamples.

## Mailbox feature branches

Node.js 22.12+, npm; run inside `mailbox/`:

```powershell
npm ci
npm test
npm run build
npm start
```

`npm start` opens the desktop app. `npm run start:web` is for browser development after building. Do not run competing servers on the same port. `npm run package:win` produces a portable Windows executable; packaged runtime is included. Live OAuth/model configuration belongs in ignored `.env`, not team notes.

## CI and delivery design

Java verification runs on Linux and Windows. CI detects tracked mailbox/formal modules, rejects incomplete modules and permits their jobs to skip only when absent. Aggregate `CI gate` checks applicable jobs. Workflow presence is not evidence that hosted runs or branch protection are enabled and passing.

PR packaging uses `--publish never`. Manual delivery uploads artifacts without releases. Stable component tags `mailbox-vX.Y.Z` or `blackboard-vX.Y.Z` must match package versions and target a commit reachable from the default branch. Tag delivery reruns checks and creates a draft release; human review precedes publication. Existing releases are not overwritten.

`scripts/package-blackboard.ps1` requires the runtime implementation before it can package JAR/dependencies/launcher. The current main distribution launcher still uses the old vault name: reconcile it with the feature branch before treating a combined package as validated. Do not infer hosted CI success from a local build or push.
