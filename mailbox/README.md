# SuperMAS Mail
> I dont do JS so I CANNOT know if this part is true. NEED REVIEW. --zhao

## Project progress

The mailbox runs in an Electron desktop window with its own local server. It supports demo mail, Gmail/Outlook adapters, label views, conversation history, editable local-model replies and version-specific approval. The demo can use the Java agent service for reply drafting.

## Dependencies

- Development: Node.js 22.12 or later, npm, Electron, esbuild and electron-builder; versions are in [package.json](package.json) and [package-lock.json](package-lock.json).
- Packaged desktop builds include the runtime; end users do not need Node.js or npm.
- Reply generation: a local Ollama service and an installed model.
- Live accounts: a system browser and an Internet connection. Release builds carry the project's desktop OAuth client IDs.
- Animation: vendored [ascii-sharks](https://github.com/stackedlol/ascii-sharks), with its [MIT license](vendor/ascii-sharks/LICENSE) and [revision record](vendor/ascii-sharks/UPSTREAM.md).

## Code structure and calls

```text
desktop/main.js          Starts the server, creates the window and handles shutdown
desktop/navigation.js    Allows local navigation and provider authorization URLs
server.js                Session-protected loopback HTTP application
web/                     Mailbox UI and grayscale animation views
lib/providers.js         Gmail, Microsoft Graph and OAuth calls
lib/model.js             Loopback-only Ollama requests
lib/classification.js    Replaceable classification JSON adapter
lib/drafts.js            Versioned drafts, approval and submission guards
lib/demo.js              Sample mailbox data
scripts/copy-assets.js   Copies browser assets after bundling
test/mailbox.test.js     Mailbox and desktop boundary tests
vendor/ascii-sharks/     Third-party animation source
public/                  Generated browser assets, ignored by Git
dist/                    Generated Windows application, ignored by Git
```

Call flow: Electron entry → local server → mail provider or local Ollama. The window loads the bundled UI without Node.js access. The desktop owns its server, closes it on exit and focuses the existing window on a second launch.

OAuth opens in the system browser. The callback uses expiring, single-use state and PKCE to update the initiating desktop session, then refreshes the desktop window. Provider sign-in is not embedded.

## Start from source

Run in this directory:

```powershell
npm ci
npm start
```

The start command builds the UI and opens an independent desktop window. Select **Explore the demo** for sample mail. Demo sending is simulated; **Generate reply** still requires a real local model.

For browser-only development:

```powershell
npm run build
npm run start:web
```

The development URL is `http://127.0.0.1:4317`. Do not run the browser server and desktop application on the same port. A port conflict produces an error instead of connecting to another process.

## Windows deployment

```powershell
npm run package:win
```

Output: `dist/SuperMAS-Mail-1.0.0-win-x64.exe`. Launch this portable executable directly; a separate browser or Node.js installation is not required. The executable is unsigned unless a signing identity is configured for electron-builder.

For source runs, copy [.env.example](.env.example) to `.env` in this directory. For packaged runs, press **Alt**, select **Application → Open configuration folder**, and put `.env` in the opened directory using the same template. Restart after editing configuration. Existing environment variables take precedence. Configuration and credentials are excluded from the package.

Session mail, tokens, labels, draft versions and approval metadata remain in memory and are cleared on exit. Provider mail remains at the provider. Durable storage, attachments, rich HTML and background synchronization remain deferred.

## Connect a real mailbox

Choose **Connect Gmail** or **Connect Outlook**, grant mailbox read access in the system browser, and return to SuperMAS Mail. Grant sending permission to enable submission. Read-only sessions can create drafts. **Disconnect** clears the active local session. Revoke authorization through the provider's account settings.

### Build a release with live sign-in

Maintainers set `GOOGLE_DESKTOP_CLIENT_ID` and `MICROSOFT_DESKTOP_CLIENT_ID` before running `npm run package:win:release`. The GitHub mailbox release workflow reads both public IDs from repository variables with those names. The release build requires both IDs and embeds them as application metadata. The normal `package:win` build supports demo and legacy development configuration.

### Gmail

Enable Gmail API, configure the consent screen, and register a **Desktop app** client. Copy its client ID to `GOOGLE_DESKTOP_CLIENT_ID`. Add the test address to the consent screen's test users when the app is in testing. The desktop uses `http://127.0.0.1:4317/auth/callback` and requests `gmail.readonly` and `gmail.send`. Complete [Google's restricted scope verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification) before broad distribution. See also [Google Desktop OAuth](https://developers.google.com/identity/protocols/oauth2/native-app).

### Outlook

Register a Microsoft Entra application for personal Microsoft accounts and work or school accounts, with the public-client redirect `http://127.0.0.1:4317/auth/callback`. Follow the [Entra loopback registration instructions](https://learn.microsoft.com/entra/identity-platform/reply-url) for the HTTP IP literal. Copy its application client ID to `MICROSOFT_DESKTOP_CLIENT_ID`. Enable delegated `User.Read`, `Mail.Read` and `Mail.Send`. The application also requests `offline_access` for token refresh. Tenant policy can require administrator consent.

For source development, set the desktop IDs before running `npm start`. Browser-only development continues to use Web clients and their `*_CLIENT_ID` and `*_CLIENT_SECRET` variables. Register the exact loopback Web redirect for that mode.

Messages are rendered as plain text. **Load older mail** paginates provider mail; opening a message fetches its conversation in the same window. Outlook thread history is capped at 1,000 messages.

Sending uses **Save draft → Approve → Send**. Editing invalidates approval; approval covers only the current version and does not send automatically. Provider acceptance is submission, not delivery confirmation. If sending fails or times out, check the provider Sent folder before creating another draft.

## Local reply generation

Start Ollama with an installed model and configure:

```dotenv
LOCAL_MODEL_URL=http://127.0.0.1:11434
LOCAL_MODEL_NAME=llama3.2
```

**Generate reply** calls `/api/chat` with the selected conversation. Only literal HTTP loopback origins are accepted; redirects are disabled. Input is capped at 24,000 characters, output at 16,000 characters and generation at 60 seconds. Generated text is an editable draft for human review. The adapter does not implement the Java agent interfaces.

## Agent reply generation (prototype branch)

For a desktop installation outside the checkout, use the [Agent Service distribution](../docs/agent-service-distribution.md).

To try agent-assisted drafting and review, start the Java agent service from the repository root with a separate demo data directory, then start the mailbox with the same token:

```bash
export AGENT_SERVICE_TOKEN="$(head -c 32 /dev/urandom | base64)"
mvn -q compile exec:java "-Dexec.mainClass=org.supermas.prototype.AgentService" "-Dexec.args=--data runtime/mailbox-review-demo --project mailbox-review-demo --llm-provider template"
```

```dotenv
AGENT_SERVICE_URL=http://127.0.0.1:4318
AGENT_SERVICE_TOKEN=<the same token>
```

Select **Explore the demo**, open a received message and click **Generate reply · SuperMAS agents**. The example uses the template provider, which needs no running model. The draft fills the editor and any review warnings refer to the original generated text. Use **Save draft** and **Approve** to review a specific version. The saved draft retains its agent source and warnings when reopened. Saving edits creates a new version that requires approval. The Java service receives the conversation for generation and only the selected message for the reply-need hint. See the [agent service contract](../docs/plans/agent-service-api.md).

Set `AGENT_SERVICE_LIVE_MAIL=yes` alongside the same URL and token to use Java for connected mailboxes. **Generate reply** sends the selected conversation to `/v1/private-reply`. Each request uses a fresh in-memory blackboard, Lucene index and topic catalog. Use a loopback model or the template provider. The resulting draft remains in the mailbox session for version-specific review and submission. Connected mailboxes use the direct Ollama adapter by default.

In **Settings & connections**, check the agent service under **Reply drafting**. It reports when the service is ready, drafting, misconfigured, incompatible, or unreachable. After starting or restarting Java, select **Check again**. A ready status confirms that Java can accept a draft request. A successful generated reply also verifies the configured generation provider.

## Classification API usage

Live mail starts unclassified. Select **Classification** to import an externally supplied result:

```json
{
  "schemaVersion": "1",
  "labels": ["Research", "Action required"],
  "confidence": 0.91,
  "reason": "A research plan review request",
  "futureAgentMetadata": { "role": "researcher" }
}
```

The adapter accepts `labels`, `categories`, or one `category`, and retains raw JSON and unknown fields. Labels apply to individual messages inside the application and are not written to provider folders/categories. Sample mail has explicitly marked fixture classifications.

`POST /api/classification` accepts `{ "messageId": "...", "result": { ... } }` with the session cookie, exact local Origin and session CSRF token. Keep this endpoint local.

## Validation commands

```powershell
npm test
npm run build
```

See [VALIDATION.md](VALIDATION.md) for completed checks and live-service prerequisites. Build the unchanged Java library from the repository root with `mvn package`.
