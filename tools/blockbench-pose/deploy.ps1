param([string]$ProjectRoot = 'D:\codexout\mcmod')
$ErrorActionPreference='Stop'
$taskSource=(Resolve-Path -LiteralPath $PSScriptRoot).Path
$taskProject=(Resolve-Path -LiteralPath $ProjectRoot).Path
if (-not (Test-Path -LiteralPath (Join-Path $taskProject 'src\main\resources\assets\eldencombat\skill_poses'))) { throw 'Not an Elden Combat project' }
$taskDestination=[IO.Path]::GetFullPath((Join-Path $taskProject 'tools\blockbench-pose'))
if (-not $taskDestination.StartsWith($taskProject+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Deployment escaped project' }
if ($taskSource -eq $taskDestination) { throw 'Source and destination are identical' }
foreach ($taskFile in Get-ChildItem -LiteralPath $taskSource -File -Recurse -Force) {
    $taskRelative=[IO.Path]::GetRelativePath($taskSource,$taskFile.FullName)
    if ($taskRelative -match '^evidence[\\/](java|source-fixture)[\\/]' -or $taskRelative -match '\.(zip|log)$') { continue }
    $taskTarget=[IO.Path]::GetFullPath((Join-Path $taskDestination $taskRelative))
    if (-not $taskTarget.StartsWith($taskDestination+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Copy escaped tool directory' }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($taskTarget)) | Out-Null
    Copy-Item -LiteralPath $taskFile.FullName -Destination $taskTarget -Force
}
Write-Output "Deployed isolated tool to $taskDestination"
