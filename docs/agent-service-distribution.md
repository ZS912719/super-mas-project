# SuperMAS Agent Service distribution

The ZIP contains the Java service, its runtime libraries, the launcher and the sample university knowledge under `knowledge/`. Build it from a repository checkout with JDK 17, Maven, Git and PowerShell using `./scripts/package-agent-service.ps1`. Find the ZIP in `target/release/`.

On the target machine, extract the ZIP and use Java 17 with PowerShell. In a PowerShell session, generate a token and launch the service with a separate writable data directory:

```powershell
$env:AGENT_SERVICE_TOKEN = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
& './supermas-agent-service/start-agent-service.ps1' -DataDirectory './supermas-agent-data'
```

Adjust the launcher path to the extracted directory. The default knowledge path is relative to the launcher. Use `-KnowledgeDirectory` to supply another directory. The launcher passes both `--knowledge` and `--data` to Java. The service binds to loopback on port 4318 by default. Set `SUPERMAS_LLM_PROVIDER=template` in the launch environment for a demo that needs no model service.

In the packaged desktop mailbox, use **Application → Open configuration folder** and create a local `.env` with the following values, then restart the mailbox. Alternatively, start the desktop process with both variables set in its environment.

```dotenv
AGENT_SERVICE_URL=http://127.0.0.1:4318
AGENT_SERVICE_TOKEN=<same value used to start Java>
```

Select **Explore the demo**, then check the service under **Settings & connections → Reply drafting**. Keep the desktop `.env`, database and actual mail outside the extracted distribution. The archive is assembled from runtime JARs, the launcher, documentation and versioned sample knowledge.
