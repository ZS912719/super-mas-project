# CI and delivery

## Current branch support

The initial `main` checkout contains Java contracts. The desktop mailbox and blackboard runtime remain on feature branches. CI always verifies Java; it detects committed mailbox and formal modules and runs their checks when present. It rejects incomplete modules. Delivery requests for an unavailable component fail with an explicit message. No feature implementation is copied into `main` by this pipeline setup.

## Workflow structure

| File | Triggers | Responsibilities |
| --- | --- | --- |
| `.github/workflows/ci.yml` | Branch pushes, pull requests, manual runs, reusable calls | Java verification on Linux and Windows, module detection, conditional mailbox/formal checks, aggregate CI gate |
| `.github/workflows/release.yml` | Component version tags, manual runs | Validate request, call CI for the same commit, prepare a package and checksum, create a draft release for tags |

CI uploads Java libraries and any available JUnit reports, Windows portable mailbox and formal-check logs. Artifacts expire after 14 days. Existing reports are uploaded even when their validation step fails. A successful `CI gate` requires module detection and Java verification to succeed, and every detected module to pass its checks. A module job may be skipped only when detection confirms that module is absent. Java verification on the interface-only branch checks compilation and packaging, not implemented runtime behavior.

The mailbox job uses Node.js 22 and `npm ci` with the committed lockfile. Mailbox tests and browser builds run for a detected mailbox. Desktop packaging runs when its `package:win` script exists. The packaging command passes `--publish never`, so branch and PR builds cannot publish releases. Tests simulate providers and model responses; they do not establish live OAuth, inference or desktop UI correctness.

The formal job downloads the [official nuXmv 2.1.0 Windows archive](https://nuxmv.fbk.eu/previous-version.html), verifies its pinned SHA-256 and extracts it with the runner's 7-Zip. The [nuXmv license](https://nuxmv.fbk.eu/license.html) applies to running the tool; its binaries are not included in delivery bundles. The existing formal script checks both positive models and expected negative controls.

## Enable on GitHub

1. Commit the pipeline files on `main` and push when ready to enable hosted runs. The local configuration does not change remote settings.
2. Inspect every job in the **Actions** tab. Java runs on both operating systems; unavailable feature modules should be skipped. A local Windows run does not verify Linux.
3. In the default branch's ruleset or branch protection, require pull requests and the `CI gate` status from GitHub Actions. Configure the requirement after its first successful run. Availability depends on the repository plan and visibility.
4. Integrate feature branches through pull requests. Their committed mailbox and formal modules activate the corresponding checks automatically. Delivery becomes available after the selected component implementation is present.

CI is intentionally not filtered by changed paths. Required workflows skipped by path filters can leave checks pending. Branch pushes and PR updates may both run CI; correctness is prioritized over deduplicating runs in this initial pipeline.

Ordinary jobs receive `contents: read`; only the final draft-release job receives `contents: write`. Checkout does not persist Git credentials. External actions are pinned to verified commit SHAs, with version comments for maintenance. PRs do not receive OAuth credentials, model configuration or release tokens. No personal access token is required for releases: the final job uses the repository's `GITHUB_TOKEN`. Actions and release creation must be allowed by repository or organization policies.

## Try delivery without publishing

Once the selected implementation is present, select **Delivery → Run workflow**, choose a branch and either `mailbox` or `blackboard`. The workflow runs all applicable CI checks and uploads a `delivery-bundle` artifact containing one package and `SHA256SUMS.txt`. Manual runs never create releases and allow development versions such as `1.0.0-SNAPSHOT`.

For local blackboard packaging after the runtime implementation is merged:

```powershell
./scripts/package-blackboard.ps1
```

The ZIP in `target/release/` includes the application JAR, runtime dependencies, a launcher, usage instructions and the project license. Extract it and follow its README. JDK 17 and PowerShell 7 are required on the destination machine. The JAR uploaded by ordinary CI is a library artifact and does not include runtime dependencies.

## Publish a project release or prepare a component draft

Release tags use exact stable versions:

- `vX.Y.Z` must match `pom.xml`, `mailbox/package.json` and `mailbox/package-lock.json`. After CI passes, the workflow publishes the Maven library to GitHub Packages and publishes the portable Windows demo and both Java distributions in a GitHub Release. Update [release notes](release-notes.md) for the tagged version.
- `mailbox-vX.Y.Z` must match `mailbox/package.json` and `mailbox/package-lock.json`.
- `blackboard-vX.Y.Z` must match `pom.xml`; a `-SNAPSHOT` version must first be changed to a release version.

Merge the version change into the default branch before tagging its commit. For example, once the mailbox version is `0.1.0` on the default branch:

```sh
git switch main
git pull --ff-only
git tag mailbox-v0.1.0
git push origin mailbox-v0.1.0
```

The delivery workflow rejects tags whose commit is not an ancestor of the default branch. It reruns applicable CI checks at the tagged commit, reuses the tested mailbox executable or packages the blackboard, and creates a **draft** release with checksums and generated notes. Existing releases are never overwritten; a rerun after draft creation fails at release creation. Inspect the draft and download its assets before publishing it through GitHub.

The mailbox executable is unsigned. Validate desktop startup and the demo on Windows before publication; live account authorization needs separate testing. This pipeline does not implement automatic desktop updates, public hosting or deployment of a multi-user server.

## References

- [GitHub Actions Maven verification](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven)
- [Required status checks](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks)
- [Workflow permissions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
- [electron-builder publishing](https://www.electron.build/v26/docs/publish/)
