param(
    [string]$NuXmv = (Join-Path $PSScriptRoot '../tmp/tools/nuxmv/nuXmv-2.1.0-win64/bin/nuXmv.exe')
)
$ErrorActionPreference = 'Stop'
$tool = (Resolve-Path -LiteralPath $NuXmv).Path
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$output = Join-Path $root 'target/formal'
New-Item -ItemType Directory -Force $output | Out-Null
$tasks = Get-Content -Raw (Join-Path $root 'formal/tasks.smv')
$projection = Get-Content -Raw (Join-Path $root 'formal/projection.smv')

function Invoke-Model([string]$name, [string]$source, [int]$expectedTrue, [int]$expectedFalse) {
    $path = Join-Path $output "$name.smv"
    [IO.File]::WriteAllText($path, $source, [Text.UTF8Encoding]::new($false))
    $lines = & $tool -dynamic $path 2>&1
    $exit = $LASTEXITCODE
    $text = $lines -join "`n"
    [IO.File]::WriteAllText((Join-Path $output "$name.txt"), $text)
    $passed = ([regex]::Matches($text, '(?m)^-- (?:specification|invariant).* is true')).Count
    $failed = ([regex]::Matches($text, '(?m)^-- (?:specification|invariant).* is false')).Count
    if ($exit -ne 0 -or $passed -ne $expectedTrue -or $failed -ne $expectedFalse) {
        throw "$name failed: exit=$exit true=$passed false=$failed. See target/formal/$name.txt"
    }
    Write-Output "$name`: true=$passed false=$failed"
}

Invoke-Model 'tasks' $tasks 5 0
Invoke-Model 'projection' $projection 6 0
# Re-run safety invariants without fairness or temporal specifications.
$taskSafety = [regex]::Replace($tasks, '(?m)^(FAIRNESS|CTLSPEC|LTLSPEC).*\r?\n?', '')
$projectionSafety = [regex]::Replace($projection, '(?m)^(FAIRNESS|CTLSPEC|LTLSPEC).*\r?\n?', '')
Invoke-Model 'unfair-tasks' $taskSafety 3 0
Invoke-Model 'unfair-projection' $projectionSafety 4 0
# Negative controls must produce counterexamples, proving the checks detect these faults.
$badFence = $taskSafety.Replace('candidate_a := valid_a;', 'candidate_a := task_status = running & token_a > 0;')
Invoke-Model 'broken-fence' $badFence 2 1
$badProjection = $projectionSafety.Replace('publishing : render_payload;', 'publishing : payload;')
Invoke-Model 'broken-projection' $badProjection 3 1
