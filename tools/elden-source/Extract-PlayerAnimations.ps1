param(
    [string]$GameDir = 'E:/stmgame/steamapps/common/ELDEN RING/Game',
    [string]$OutputDir = 'D:/codexout/mcmod/build/elden-source/extracted-player',
    [switch]$Extract
)
$ErrorActionPreference = 'Stop'
$game = [IO.Path]::GetFullPath($GameDir).TrimEnd('\')
$output = [IO.Path]::GetFullPath($OutputDir).TrimEnd('\')
$workspace = 'D:\codexout\mcmod\build\elden-source\'
if (-not $output.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase)) { throw 'Output must stay inside isolated workspace.' }
if ($output.StartsWith($game + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Never extract to the game installation.' }
$tool = 'D:/codexout/mcmod/build/elden-source/tools/nuxe-1.2.1'
$assemblies = Join-Path $tool 'assemblies'
$res = Join-Path $tool 'Nuxe 1.2.1/res'
foreach ($name in 'BouncyCastle.Cryptography','Bex','ZstdSharp','Coremats') {
    [Reflection.Assembly]::LoadFrom((Join-Path $assemblies ($name + '.dll'))) | Out-Null
}
$nuxe = [Reflection.Assembly]::LoadFrom((Join-Path $assemblies 'Nuxe.dll'))
$configType = $nuxe.GetType('Nuxe.GameConfig', $true)
$configs = $configType.GetMethod('LoadGameConfigs').Invoke($null, @($res.psobject.BaseObject))
$config = $configs | Where-Object Name -eq 'Elden Ring (PC)'
if ($null -eq $config) { throw 'Missing Elden Ring config.' }
Add-Type -TypeDefinition @'
using System;
public sealed class AssetInspectionProgress<T> : IProgress<T> {
    public void Report(T value) { Console.WriteLine(value); }
}
'@
$progressType = $nuxe.GetType('Nuxe.OperationProgress', $true)
$progress = [Activator]::CreateInstance([AssetInspectionProgress``1].MakeGenericType($progressType))
$operationType = $nuxe.GetType('Nuxe.UnpackOperation', $true)
$operation = [Activator]::CreateInstance($operationType, [object[]]@($res.psobject.BaseObject, $game, $config.psobject.BaseObject, $output,
        '^/chr/c0000(?:_[A-Za-z0-9_]+)?\.(anibnd|chrbnd)\.dcx$', $false, $true))
$flags = [Reflection.BindingFlags]'Instance,NonPublic'
$base = $operationType.BaseType
$base.GetProperty('Progress', $flags).SetValue($operation, $progress)
$binders = $operationType.GetMethod('ReadBinders', $flags).Invoke($operation, @('Read-only header inspection'))
$files = $operationType.GetMethod('AuditFiles', $flags).Invoke($operation, @('Player animation selection', $binders))
if ($files.Count -eq 0) { throw 'No new matching player binders; inspect existing output or file dictionary.' }
$total = 0L
$selection = foreach ($file in $files) {
    $target = [IO.Path]::GetFullPath($file.UnpackPath)
    if (-not $target.StartsWith($output + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe archive path.' }
    $total += $file.HeaderFile.DataLength
    [pscustomobject]@{ Archive = $file.BinderConfig.DataPath; Path = $file.GamePath; Bytes = $file.HeaderFile.DataLength }
}
$selection | ConvertTo-Json
Write-Output "Selected bytes: $total"
if (-not $Extract) { return }
if ($total -gt 1GB) { throw 'Selection exceeds the 1 GiB extraction budget.' }
$before = Get-FileHash -LiteralPath (Join-Path $game 'eldenring.exe') -Algorithm SHA256
$headerBefore = @(Get-ChildItem -LiteralPath $game -Filter '*.bhd' | Get-FileHash -Algorithm SHA256)
$operationType.GetMethod('UnpackFiles', $flags).Invoke($operation, @('Extracting to isolated workspace', $binders, $files)) | Out-Null
$after = Get-FileHash -LiteralPath (Join-Path $game 'eldenring.exe') -Algorithm SHA256
$headerAfter = @(Get-ChildItem -LiteralPath $game -Filter '*.bhd' | Get-FileHash -Algorithm SHA256)
if ($before.Hash -ne $after.Hash -or (Compare-Object $headerBefore $headerAfter -Property Path,Hash)) {
    throw 'Source executable or archive header changed during extraction.'
}
$receipt = [pscustomobject]@{
    Game = $game; Output = $output; Date = [DateTimeOffset]::UtcNow.ToString('o')
    Tool = 'Nuxe 1.2.1 unmodified extraction methods, no GUI or patch operation'
    ToolUrl = 'https://github.com/JKAnderson/Nuxe/releases/tag/v1.2.1'
    ToolArchiveHash = (Get-FileHash -LiteralPath (Join-Path $tool '../Nuxe.1.2.1.zip') -Algorithm SHA256).Hash
    SourceExeSha256 = $after.Hash; SourceHeaders = $headerAfter; SourceUnchanged = $true
    SelectedBytes = $total; Selection = @($selection)
    Extracted = @(Get-ChildItem -LiteralPath (Join-Path $output 'chr') -Filter '*.dcx' -File | Get-FileHash -Algorithm SHA256)
    Status = 'Extracted binders only; skill animation not yet identified or retargeted'
    Distribution = 'Local inspection only; excluded from mod resources'
}
$receipt | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output 'extraction-receipt.json') -Encoding utf8
Write-Output 'Extraction completed; original executable and archive headers unchanged.'
