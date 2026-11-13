# SuperMAS v1.0.0

This release includes the Windows desktop mailbox demo, the standalone Java Agent Service, and the SQLite blackboard demo with Markdown observation tools.

| Download | Use |
| --- | --- |
| `SuperMAS-Mail-1.0.0-win-x64.exe` | Unsigned Windows x64 portable app. Select **Explore the demo** for sample mail and simulated sending. |
| `supermas-agent-service-1.0.0.zip` | Local Java service for multi-agent drafting. Requires Java 17 and PowerShell. |
| `supermas-blackboard-1.0.0.zip` | Blackboard demo, recovery and observation tools. Requires Java 17 and PowerShell. |
| `SHA256SUMS.txt` | Checksums for the three downloads. |

The Java library is published in GitHub Packages as `org.supermas:supermas:1.0.0`.

The desktop download ships without project OAuth client IDs. Live Gmail and Outlook login and sending await dedicated-account testing. Local drafting can use Ollama, or the Agent Service can demonstrate the workflow with its template provider. See the README included with each Java archive for setup.
