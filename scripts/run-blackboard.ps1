param(
    [ValidateSet('demo', 'watch', 'audit')][string]$Mode = 'demo',
    [string]$DataDirectory = 'runtime'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    & mvn -q -DskipTests package 'org.apache.maven.plugins:maven-dependency-plugin:3.1.1:build-classpath' '-Dmdep.outputFile=target/runtime-classpath.txt' '-Dmdep.includeScope=runtime'
    if ($LASTEXITCODE -ne 0) { throw 'Runtime build failed.' }
    $dependencies = (Get-Content -Raw target/runtime-classpath.txt).Trim()
    $classpath = (Join-Path $root 'target/classes') + [IO.Path]::PathSeparator + $dependencies
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    if ($Mode -eq 'demo') {
        & $java -cp $classpath org.supermas.blackboard.app.BlackboardDemo $DataDirectory
    } else {
        & $java -cp $classpath org.supermas.blackboard.app.BlackboardObserver $DataDirectory $Mode
    }
    if ($LASTEXITCODE -ne 0) { throw 'Blackboard command failed.' }
} finally { Pop-Location }
