$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskBase=(Resolve-Path -LiteralPath $PSScriptRoot).Path
$taskZip=Join-Path $taskBase ('eldencombat-pose-1.0.0-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.zip')
$taskArchive=[IO.Compression.ZipFile]::Open($taskZip,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach($taskFile in Get-ChildItem -LiteralPath $taskBase -File -Recurse -Force) {
        $taskRelative=[IO.Path]::GetRelativePath($taskBase,$taskFile.FullName)
        if($taskRelative -match '^evidence[\\/](java|source-fixture|session-recovery)[\\/]' -or $taskRelative -match '\.(zip|log)$') {continue}
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,$taskFile.FullName,$taskRelative.Replace('\','/'),[IO.Compression.CompressionLevel]::Optimal)|Out-Null
    }
} finally {$taskArchive.Dispose()}
Get-FileHash -LiteralPath $taskZip | Select-Object Path,Hash
