param([Parameter(Mandatory = $true)][string]$Database)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    if (-not $env:SUPERMAS_OBSERVATION_TOKEN -or $env:SUPERMAS_OBSERVATION_TOKEN.Length -lt 32) {
        throw 'Set SUPERMAS_OBSERVATION_TOKEN to a random value of at least 32 characters.'
    }
    if (-not $env:SUPERMAS_OBSERVATION_BOARDS) { throw 'Set SUPERMAS_OBSERVATION_BOARDS to authorized board keys.' }
    $databasePath = (Resolve-Path -LiteralPath $Database).Path
    & mvn -q -DskipTests package 'org.apache.maven.plugins:maven-dependency-plugin:3.1.1:build-classpath' '-Dmdep.outputFile=target/runtime-classpath.txt' '-Dmdep.includeScope=runtime'
    if ($LASTEXITCODE -ne 0) { throw 'Observation build failed.' }
    $dependencies = (Get-Content -Raw target/runtime-classpath.txt).Trim()
    $classpath = (Join-Path $root 'target/classes') + [IO.Path]::PathSeparator + $dependencies
    & java -cp $classpath org.supermas.observation.ObservationServer $databasePath
    if ($LASTEXITCODE -ne 0) { throw 'Observation server failed.' }
} finally { Pop-Location }
