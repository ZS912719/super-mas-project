param([string]$EnvironmentFile = 'runtime/observation-docker.env')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    $dockerDirectory = Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin'
    if (-not (Test-Path (Join-Path $dockerDirectory 'docker.exe'))) { throw 'Docker CLI is unavailable.' }
    $env:PATH = $dockerDirectory + [IO.Path]::PathSeparator + $env:PATH
    $docker = Get-Command docker
}
Push-Location $root
try {
    $composeArguments = @('compose')
    if (Test-Path -LiteralPath $EnvironmentFile) {
        $composeArguments += @('--env-file', (Resolve-Path -LiteralPath $EnvironmentFile).Path)
        foreach ($line in Get-Content -LiteralPath $EnvironmentFile) {
            $parts = $line -split '=', 2
            if ($parts.Length -eq 2 -and $parts[0] -in @('SUPERMAS_OBSERVATION_TOKEN', 'GF_SECURITY_ADMIN_PASSWORD')) {
                if (-not (Test-Path ('Env:' + $parts[0]))) { Set-Item ('Env:' + $parts[0]) $parts[1] }
            }
        }
    }
    if (-not $env:SUPERMAS_OBSERVATION_TOKEN -or $env:SUPERMAS_OBSERVATION_TOKEN.Length -lt 32 -or -not $env:GF_SECURITY_ADMIN_PASSWORD) {
        throw 'Inject SUPERMAS_OBSERVATION_TOKEN (at least 32 characters) and GF_SECURITY_ADMIN_PASSWORD or supply a protected environment file.'
    }
    & $docker.Source info --format '{{.OSType}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop with its Linux engine before continuing.' }
    New-Item -ItemType Directory -Path runtime/observation-controls -Force | Out-Null
    & mvn -q -DskipTests test-compile 'org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy-dependencies' '-DoutputDirectory=target/observation-container/lib' '-DincludeScope=test'
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic observation runtime build failed.' }
    & $docker.Source @composeArguments -f observability/grafana/compose.yaml up -d --build --force-recreate
    if ($LASTEXITCODE -ne 0) { throw 'Observation Compose startup failed.' }
    Write-Output 'Open http://127.0.0.1:3000 and sign in as admin with the injected password.'
    Write-Output 'This stack contains synthetic data only. Windows-hosted application databases are not mounted.'
} finally { Pop-Location }
