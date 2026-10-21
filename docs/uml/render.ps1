param(
    [string]$PlantUmlJar = (Join-Path $PSScriptRoot '..\..\tmp\tools\plantuml.jar'),
    [string]$JavaExecutable
)

$ErrorActionPreference = 'Stop'
$jarPath = (Resolve-Path -LiteralPath $PlantUmlJar).Path
$javaCommand = if ($JavaExecutable) {
    $JavaExecutable
} elseif ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    'java'
}
$diagramPaths = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.puml' -File |
    Sort-Object Name | ForEach-Object FullName)
if ($diagramPaths.Count -eq 0) { throw 'No PlantUML source files were found.' }

& $javaCommand '-Djava.awt.headless=true' -jar $jarPath -charset UTF-8 -checkonly @diagramPaths
if ($LASTEXITCODE -ne 0) { throw 'PlantUML syntax validation failed.' }

foreach ($format in @('-tsvg', '-tpng')) {
    & $javaCommand '-Djava.awt.headless=true' -jar $jarPath -charset UTF-8 $format @diagramPaths
    if ($LASTEXITCODE -ne 0) { throw "PlantUML rendering failed for $format." }
}

Write-Output "Rendered $($diagramPaths.Count) diagrams as SVG and PNG in $PSScriptRoot"
