# Blackboard distribution

## Requirements

- JDK 17 available through `JAVA_HOME` or `PATH`.
- PowerShell 7 (`pwsh`).

## Structure

- `supermas.jar`: Java blackboard implementation and entry classes.
- `lib/`: runtime dependency JARs, including SQLite JDBC and Jackson.
- `start-blackboard.ps1`: distribution launcher.
- `runtime/`: database and Markdown observation data created on first use.

The ZIP includes third-party runtime dependencies. Their licenses and notices are retained in their JARs.

## Usage

Extract the entire ZIP, then run from its directory:

```powershell
pwsh ./start-blackboard.ps1 -Mode demo
pwsh ./start-blackboard.ps1 -Mode audit
pwsh ./start-blackboard.ps1 -Mode watch
```

Use `-DataDirectory './my-data'` to select a different data directory. Stop `watch` with Ctrl+C. Open `runtime/vault` in Obsidian to view generated observations.

The demo stores fixed sample data. The observer recovers expired leases and exports Markdown; it does not execute agents or send email. The desktop mailbox is distributed separately.
