$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not (Test-Path (Join-Path $root 'src/main/java/org/supermas/blackboard/app/BlackboardDemo.java'))) {
    throw 'Blackboard runtime is not available on this branch.'
}
[xml]$pom = Get-Content -Raw (Join-Path $root 'pom.xml')
$version = $pom.project.version
$stage = Join-Path $root ('target/distributions/stage-' + [guid]::NewGuid().ToString('N'))
$output = Join-Path $root 'target/release'
New-Item -ItemType Directory -Force $stage, $output | Out-Null
Push-Location $root
try {
    & mvn -B -ntp verify 'org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy-dependencies' '-DincludeScope=runtime' "-DoutputDirectory=$stage/lib"
    if ($LASTEXITCODE -ne 0) { throw 'Blackboard distribution build failed.' }
    Copy-Item -LiteralPath (Join-Path $root "target/supermas-$version.jar") -Destination (Join-Path $stage 'supermas.jar')
    Copy-Item -LiteralPath (Join-Path $root 'scripts/start-blackboard.ps1') -Destination $stage
    Copy-Item -LiteralPath (Join-Path $root 'docs/blackboard-distribution.md') -Destination (Join-Path $stage 'README.md')
    Copy-Item -LiteralPath (Join-Path $root 'LICENSE') -Destination $stage
    $archive = Join-Path $output "supermas-blackboard-$version.zip"
    Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $archive -Force
    Write-Output "Blackboard distribution: $archive"
} finally { Pop-Location }
