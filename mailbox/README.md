# SuperMAS Mail
> I dont do JS so I CANNOT know if this part is true. NEED REVIEW. --zhao

## Project progress

The mailbox now runs in an Electron desktop window with its own local server. Demo mail, Gmail/Outlook adapters, label views, conversation history, editable local-model replies and version-specific approval remain available. Automatic agent execution and the Java runtime remain unimplemented.

## Dependencies

- Development: Node.js 22.12 or later, npm, Electron, esbuild and electron-builder; versions are in [package.json](package.json) and [package-lock.json](package-lock.json).
- Packaged desktop builds include the runtime; end users do not need Node.js or npm.
- Reply generation: a local Ollama service and an installed model.
- Live accounts: user-supplied OAuth application credentials and an Internet connection.
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

Output: `dist/SuperMAS-Mail-0.1.0-win-x64.exe`. Launch this portable executable directly; a separate browser or Node.js installation is not required. The executable is unsigned unless a signing identity is configured for electron-builder.

For source runs, copy [.env.example](.env.example) to `.env` in this directory. For packaged runs, press **Alt**, select **Application → Open configuration folder**, and put `.env` in the opened directory using the same template. Restart after editing configuration. Existing environment variables take precedence. Configuration and credentials are excluded from the package.

Session mail, tokens, labels, draft versions and approval metadata remain in memory and are cleared on exit. Provider mail remains at the provider. Durable storage, attachments, rich HTML and background synchronization remain deferred.

## Connect a real mailbox

Register the exact web redirect URI `http://127.0.0.1:4317/auth/callback`. Update it if `PORT` changes. This prototype uses user-supplied web OAuth clients. Do not distribute a shared confidential client secret in a desktop package; public distribution needs a separately reviewed native-client or hosted authorization design.

### Gmail

Enable Gmail API, configure the consent screen, and create a Web application OAuth client with the redirect URI above. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`. Add your account as a test user if applicable, then select **Connect Gmail**. Requested scopes are `gmail.readonly` and `gmail.send`.

### Outlook

Register a Microsoft Entra application with the intended account audience, a Web platform redirect URI and an application secret. Set `MICROSOFT_CLIENT_ID` and `MICROSOFT_CLIENT_SECRET`. Enable delegated `User.Read`, `Mail.Read` and `Mail.Send`; tenant policy may require administrator consent. Select **Connect Outlook**. The application also requests `offline_access` for token refresh.

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

Optional: the Java agents write the draft instead of Ollama. Start the agent service from the repository root, then the mailbox with the same token:

```bash
export AGENT_SERVICE_TOKEN="$(head -c 32 /dev/urandom | base64)"
mvn -q compile exec:java -Dexec.mainClass=org.supermas.prototype.AgentService
```

```dotenv
AGENT_SERVICE_URL=http://127.0.0.1:4318
AGENT_SERVICE_TOKEN=<the same token>
```

**Generate reply** then sends the selected conversation to `/v1/reply` (loopback only, redirects disabled, 60 seconds, response validated in `lib/agents.js`). The routed specialist's draft fills the editor. On a clear routing match, its role becomes the message's classification, stored like an imported one and shown after **Refresh**. If the agents return review warnings (sentences in which the draft decides or acts for you, such as granting an extension or claiming an attachment), they are listed in an amber box above the editor; the draft itself is not changed. Review, approval and sending are unchanged. Without `AGENT_SERVICE_URL`, Ollama is used as before. The UI text still says "local model". Contract: `docs/plans/agent-service-api.md` (a proposal).

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

`POST /api/classification` accepts `{ "messageId": "...", "result": { ... } }` with the session cookie, exact local Origin and session CSRF token. Keep this endpoint local. Agent integration remains deferred.

## Validation commands

```powershell
npm test
npm run build
```

See [VALIDATION.md](VALIDATION.md) for completed checks and live-service prerequisites. Build the unchanged Java library from the repository root with `mvn package`.
