param(
    [string]$ArchivePath
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$destination = Join-Path $root 'tmp/tools/nuxmv'
New-Item -ItemType Directory -Force $destination | Out-Null

# Pin the official Windows archive used by the project's formal validation.
$expectedHash = 'ad1f0f1df931eb274da21f1fbebee4ba1949ef5c1718650b84ba82cf2e579f1f'
if (-not $ArchivePath) {
    $ArchivePath = Join-Path $destination 'nuXmv-2.1.0-win64.7z'
    Invoke-WebRequest -Uri 'https://nuxmv.fbk.eu/theme/download.php?file=nuXmv-2.1.0-win64.7z' -OutFile $ArchivePath
}
if ((Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedHash) {
    throw 'nuXmv archive SHA-256 mismatch.'
}
$extractor = Get-Command 7z -ErrorAction SilentlyContinue
$extractorPath = if ($extractor) { $extractor.Source } else { 'C:/Program Files/7-Zip/7z.exe' }
& $extractorPath x $ArchivePath "-o$destination" -y | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'nuXmv archive extraction failed.' }
$executable = Join-Path $destination 'nuXmv-2.1.0-win64/bin/nuXmv.exe'
if (-not (Test-Path -LiteralPath $executable)) { throw 'nuXmv executable was not extracted.' }
Write-Output "nuXmv installed: $executable"
