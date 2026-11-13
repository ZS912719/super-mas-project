$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
[xml]$pom = Get-Content -Raw (Join-Path $root 'pom.xml')
$version = $pom.project.version
$stage = Join-Path $root ('target/distributions/agent-service-' + [guid]::NewGuid().ToString('N'))
$output = Join-Path $root 'target/release'
New-Item -ItemType Directory -Force $stage, $output | Out-Null

Push-Location $root
try {
    & mvn -B -ntp package '-DskipTests' 'org.apache.maven.plugins:maven-dependency-plugin:3.1.1:copy-dependencies' '-DincludeScope=runtime' "-DoutputDirectory=$stage/lib"
    if ($LASTEXITCODE -ne 0) { throw 'Agent service distribution build failed.' }

    Copy-Item -LiteralPath "target/supermas-$version.jar" -Destination (Join-Path $stage 'supermas.jar')
    Copy-Item -LiteralPath 'scripts/start-agent-service.ps1', 'LICENSE' -Destination $stage
    Copy-Item -LiteralPath 'docs/agent-service-distribution.md' -Destination (Join-Path $stage 'README.md')

    # Copy only versioned sample knowledge, including its source manifest.
    $sampleRoot = 'src/test/resources/university/knowledge'
    $samples = git ls-files -- "$sampleRoot/sources.json" "$sampleRoot/**/*.md"
    if ($LASTEXITCODE -ne 0 -or 'src/test/resources/university/knowledge/sources.json' -notin $samples) {
        throw 'Versioned sample knowledge is unavailable.'
    }
    foreach ($sample in $samples) {
        $relativePath = $sample.Substring($sampleRoot.Length + 1)
        $destination = Join-Path (Join-Path $stage 'knowledge') $relativePath
        New-Item -ItemType Directory -Force (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath $sample -Destination $destination
    }

    $archive = Join-Path $output "supermas-agent-service-$version.zip"
    Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $archive -Force
    Write-Output "Agent service distribution: $archive"
} finally {
    Pop-Location
}
