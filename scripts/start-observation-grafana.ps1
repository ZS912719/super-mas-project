param([Parameter(Mandatory = $true)][string]$GrafanaHome)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$grafanaRoot = (Resolve-Path -LiteralPath $GrafanaHome).Path
$configRoot = Join-Path $root 'observability/grafana'
$versions = Get-Content -Raw (Join-Path $configRoot 'versions.json') | ConvertFrom-Json
$executable = Join-Path $grafanaRoot 'bin/grafana.exe'
if (-not (Test-Path -LiteralPath $executable)) { throw 'Grafana executable is missing. Supply an extracted Windows distribution.' }
$versionText = & $executable server -v
if ($LASTEXITCODE -ne 0 -or ($versionText -join ' ') -notmatch [regex]::Escape($versions.grafana)) {
    throw "This configuration pins Grafana $($versions.grafana)."
}
if (-not $env:SUPERMAS_OBSERVATION_TOKEN -or -not $env:GF_SECURITY_ADMIN_PASSWORD) {
    throw 'Set SUPERMAS_OBSERVATION_TOKEN and GF_SECURITY_ADMIN_PASSWORD in this process environment.'
}
$env:GF_PATHS_DATA = Join-Path $root 'runtime/grafana/data'
$env:GF_PATHS_LOGS = Join-Path $root 'runtime/grafana/logs'
$env:GF_PATHS_PLUGINS = Join-Path $root 'runtime/grafana/plugins'
$env:GF_PATHS_PROVISIONING = Join-Path $configRoot 'provisioning'
$env:SUPERMAS_DASHBOARD_PATH = (Join-Path $configRoot 'dashboards').Replace('\', '/')
$pluginManifest = Join-Path $env:GF_PATHS_PLUGINS 'yesoreyeram-infinity-datasource/plugin.json'
if (-not (Test-Path -LiteralPath $pluginManifest)) {
    throw "Install Infinity $($versions.infinity) explicitly into runtime/grafana/plugins before starting. See docs/blackboard-observation.md."
}
$plugin = Get-Content -Raw $pluginManifest | ConvertFrom-Json
if ($plugin.info.version -ne $versions.infinity) { throw "This configuration pins Infinity $($versions.infinity)." }
& $executable server --homepath $grafanaRoot --config (Join-Path $configRoot 'grafana.ini')
if ($LASTEXITCODE -ne 0) { throw 'Grafana failed.' }
