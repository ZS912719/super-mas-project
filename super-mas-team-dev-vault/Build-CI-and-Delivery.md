---
id: team-build
type: operations
scope: team-development
updated: 2026-11-08
status: active
---

# Build, CI and Delivery

Commands run from the repository root unless a different directory is specified. Select a branch containing the component first; see [Project-Status](Project-Status.md). Sources: `pom.xml`, `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `docs/ci-cd.md`; feature-branch runtime/mailbox documentation.

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

## Grafana observation deployment

See [Grafana-Blackboard-Observation](Grafana-Blackboard-Observation.md) for published prototype revision `f9151ac97f0f0654f5c215030a1627e11e55f115`. The code branch adds start-observation.ps1 and start-observation-grafana.ps1 under scripts/, with provisioning under observability/grafana/. Grafana 13.1.2 and Infinity 4.1.6 are pinned. Native launchers use same-host loopback and require an existing distribution/plugin. The 2026-11-08 Docker synthetic stack was actually started with digest-pinned images and only host-loopback Grafana publication; API loopback is shared inside the container namespace. Start it with scripts/start-observation-docker.ps1 and verify real queries with scripts/verify-observation-grafana.py --fault-tests. No native Windows startup or hosted CI result is claimed.

## Standalone Agent Service distribution

Commit `1d4a891c2634984ead4a77f1a2d352592fec7a6c` on `codex/mailbox-draft-review` adds `scripts/package-agent-service.ps1`, `scripts/start-agent-service.ps1` and `docs/agent-service-distribution.md`. The ZIP contains runtime JARs, versioned sample knowledge, the launcher, license and instructions. The launcher passes explicit knowledge and external data directories. The service and desktop use the same injected token. The desktop can load its local environment file from its existing packaged configuration folder.

A Windows PowerShell check on 2026-11-13 inspected the archive for its seven knowledge files and absence of environment files, databases and mail files. From a temporary working directory outside the checkout, the extracted launcher started Java, required the token, generated a synthetic template draft and wrote SQLite into the external data directory. This check used the HTTP API. It did not rebuild or exercise the Electron executable. The remote feature branch was verified at this revision.
