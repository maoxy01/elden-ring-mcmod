$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskPackage = Join-Path $taskRoot 'dist\hud-v02-20261023'
$taskReceipt = Get-Content -LiteralPath (Join-Path $taskPackage 'verification.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$taskSource = Join-Path $taskPackage 'mods\eldencombat-0.2.0.jar'
$taskTarget = [IO.Path]::GetFullPath('D:\PCL\.minecraft\versions\Elden Combat 0.2.0 - Vanilla Combat - 副本\mods\eldencombat-0.2.0.jar')
if (-not $taskTarget.Equals([IO.Path]::GetFullPath($taskReceipt.baseline), [StringComparison]::OrdinalIgnoreCase)) { throw 'Package baseline and exact target differ' }
$taskHash = (Get-FileHash -LiteralPath $taskSource -Algorithm SHA256).Hash.ToLowerInvariant()
if ($taskHash -ne $taskReceipt.jarSha256) { throw 'Package hash mismatch' }
$taskCurrentHash = (Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash.ToLowerInvariant()
if ($taskCurrentHash -ne $taskReceipt.baselineSha256 -and $taskCurrentHash -ne $taskHash) { throw 'Installed mod changed after packaging; refuse to overwrite' }
$taskGame = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -eq 'javaw' -or ($_.ProcessName -eq 'java' -and $_.MainWindowTitle -match 'Minecraft|Elden Combat') }
if ($taskGame) { throw 'Close the game before replacing its mod' }
$taskBackupDir = Join-Path $taskRoot 'dist\hud-v02-20261023-install-backup'
[IO.Directory]::CreateDirectory($taskBackupDir) | Out-Null
$taskBackup = Join-Path $taskBackupDir 'eldencombat-0.2.0.before-hud-v02.jar'
if (Test-Path -LiteralPath $taskBackup) {
    if ((Get-FileHash -LiteralPath $taskBackup -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskReceipt.baselineSha256) { throw 'Existing backup differs; preserve it' }
} elseif ($taskCurrentHash -eq $taskReceipt.baselineSha256) {
    Copy-Item -LiteralPath $taskTarget -Destination $taskBackup
} else { throw 'Updated mod is present but its original backup is missing' }
if ((Get-FileHash -LiteralPath $taskBackup -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskReceipt.baselineSha256) { throw 'Backup verification failed' }
if ($taskCurrentHash -ne $taskHash) {
    $taskStaged = $taskTarget + '.hud-v02.tmp'
    Copy-Item -LiteralPath $taskSource -Destination $taskStaged -Force
    if ((Get-FileHash -LiteralPath $taskStaged -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskHash) { throw 'Staged file hash mismatch' }
    Move-Item -LiteralPath $taskStaged -Destination $taskTarget -Force
}
if ((Get-FileHash -LiteralPath $taskTarget -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskHash) { throw 'Installed file hash mismatch' }
$taskResult = [ordered]@{ installed = $true; target = $taskTarget; sha256 = $taskHash; backup = $taskBackup; backupSha256 = $taskReceipt.baselineSha256; gameLaunched = $false; installedAt = [DateTimeOffset]::Now.ToString('o') }
$taskResult | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskPackage 'installation.json') -Encoding UTF8
$taskResult | ConvertTo-Json
