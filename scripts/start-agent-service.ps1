param(
    [Parameter(Mandatory = $true)][string]$DataDirectory,
    [string]$KnowledgeDirectory,
    [ValidateRange(1024, 65535)][int]$Port = 4318,
    [string]$Project = 'mailbox-review-demo'
)

$ErrorActionPreference = 'Stop'
if ($env:AGENT_SERVICE_TOKEN.Length -lt 32) {
    throw 'Set AGENT_SERVICE_TOKEN to the same value used by the desktop mailbox (at least 32 characters).'
}
if (-not $KnowledgeDirectory) { $KnowledgeDirectory = Join-Path $PSScriptRoot 'knowledge' }
$knowledge = (Resolve-Path -LiteralPath $KnowledgeDirectory).Path
if (-not (Test-Path -LiteralPath $knowledge -PathType Container)) {
    throw 'KnowledgeDirectory must be a directory.'
}
$data = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($DataDirectory)
$packagePath = $PSScriptRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if ($data -eq $PSScriptRoot -or $data.StartsWith($packagePath, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Choose a DataDirectory outside the extracted distribution.'
}
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java' } else { 'java' }
$classpath = (Join-Path $PSScriptRoot 'supermas.jar') + [IO.Path]::PathSeparator + (Join-Path $PSScriptRoot 'lib/*')
$arguments = @('-cp', $classpath, 'org.supermas.prototype.AgentService',
    '--knowledge', $knowledge, '--data', $data, '--port', "$Port", '--project', $Project)
& $java @arguments
if ($LASTEXITCODE -ne 0) { throw 'Agent service exited with an error.' }
