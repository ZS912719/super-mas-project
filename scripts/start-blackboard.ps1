param(
    [ValidateSet('demo', 'watch', 'audit')][string]$Mode = 'demo',
    [string]$DataDirectory = (Join-Path $PSScriptRoot 'runtime')
)
$ErrorActionPreference = 'Stop'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java' } else { 'java' }
$classpath = (Join-Path $PSScriptRoot 'supermas.jar') + [IO.Path]::PathSeparator + (Join-Path $PSScriptRoot 'lib/*')
$entryClass = if ($Mode -eq 'demo') { 'org.supermas.blackboard.app.BlackboardDemo' } else { 'org.supermas.blackboard.app.BlackboardObserver' }
$arguments = @('-cp', $classpath, $entryClass, $DataDirectory)
if ($Mode -ne 'demo') { $arguments += $Mode }
& $java @arguments
if ($LASTEXITCODE -ne 0) { throw 'Blackboard command failed.' }
