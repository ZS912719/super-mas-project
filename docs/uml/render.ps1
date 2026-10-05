param(
    [string]$PlantUmlJar = (Join-Path $PSScriptRoot '..\..\tmp\tools\plantuml.jar')
)

$ErrorActionPreference = 'Stop'
$jarPath = (Resolve-Path -LiteralPath $PlantUmlJar).Path
$diagramPaths = @(
    (Join-Path $PSScriptRoot 'class-diagram.puml'),
    (Join-Path $PSScriptRoot 'sequence-diagram.puml'),
    (Join-Path $PSScriptRoot 'agent-organization.puml')
)

& java '-Djava.awt.headless=true' -jar $jarPath -charset UTF-8 -checkonly @diagramPaths
if ($LASTEXITCODE -ne 0) { throw 'PlantUML syntax validation failed.' }

foreach ($format in @('-tsvg', '-tpng')) {
    & java '-Djava.awt.headless=true' -jar $jarPath -charset UTF-8 $format @diagramPaths
    if ($LASTEXITCODE -ne 0) { throw "PlantUML rendering failed for $format." }
}

Write-Output "Rendered three diagrams as SVG and PNG in $PSScriptRoot"
