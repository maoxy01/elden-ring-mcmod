param(
    [switch]$Unpack,
    [string]$GameDir = 'E:/stmgame/steamapps/common/ELDEN RING/Game'
)
$ErrorActionPreference = 'Stop'
$root = 'D:/codexout/mcmod/build/elden-source'
$tool = [IO.Path]::Combine($root, 'tools/uxm-2.4.2/UXM Selective Unpack 2.4.2.0/UXM Selective Unpack.exe')
$libraryDir = [IO.Path]::Combine($root, 'tools/uxm-2.4.2/assemblies')
[IO.Directory]::CreateDirectory($libraryDir) | Out-Null
$uxm = [Reflection.Assembly]::LoadFrom($tool)
foreach ($name in 'soulsformats','bouncycastle.crypto') {
    $target = [IO.Path]::Combine($libraryDir, $name + '.dll')
    if (-not [IO.File]::Exists($target)) {
        $stream = $uxm.GetManifestResourceStream('costura.' + $name + '.dll.compressed')
        $deflate = [IO.Compression.DeflateStream]::new($stream, [IO.Compression.CompressionMode]::Decompress)
        $output = [IO.File]::Create($target)
        try { $deflate.CopyTo($output) } finally { $output.Dispose(); $deflate.Dispose(); $stream.Dispose() }
    }
    [Reflection.Assembly]::LoadFrom($target) | Out-Null
}
if (-not $Unpack) {
    foreach ($name in 'SoulsFormats.Oodle','SoulsFormats.BND4','SoulsFormats.TAE') {
        $type = [SoulsFormats.BND4].Assembly.GetType($name)
        if ($null -eq $type) { continue }
        $type.FullName
        $type.GetMethods([Reflection.BindingFlags]'Public,Static,Instance,FlattenHierarchy') | Where-Object Name -match 'Read|Oodle|Load|Init' | ForEach-Object ToString
        $type.GetFields([Reflection.BindingFlags]'Public,Static,NonPublic') | ForEach-Object ToString
    }
    return
}
$oodle = [SoulsFormats.BND4].Assembly.GetType('SoulsFormats.Oodle')
$handle = [Runtime.InteropServices.NativeLibrary]::Load([IO.Path]::Combine($GameDir, 'oo2core_6_win64.dll'))
$oodle.GetField('Oodle6Ptr', [Reflection.BindingFlags]'Static,NonPublic,Public').SetValue($null, $handle)
$results = @()
foreach ($source in Get-ChildItem -LiteralPath (Join-Path $root 'extracted-player/chr') -Filter '*.dcx') {
    $binder = [SoulsFormats.BND4]::Read($source.FullName)
    $destination = [IO.Path]::Combine($root, 'unpacked-player', $source.Name.Replace('.dcx','').Replace('.','-'))
    [IO.Directory]::CreateDirectory($destination) | Out-Null
    $entries = foreach ($file in $binder.Files) {
        $name = [IO.Path]::GetFileName($file.Name)
        if ([string]::IsNullOrWhiteSpace($name)) { throw 'Unnamed binder entry.' }
        $target = [IO.Path]::GetFullPath([IO.Path]::Combine($destination, $name))
        if (-not $target.StartsWith([IO.Path]::GetFullPath($destination) + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Unsafe binder entry path.'
        }
        if (-not [IO.File]::Exists($target)) { [IO.File]::WriteAllBytes($target, $file.Bytes) }
        elseif ((Get-FileHash -LiteralPath $target).Hash -ne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($file.Bytes))) {
            throw "Refusing conflicting entry: $target"
        }
        [pscustomobject]@{ Id = $file.ID; Name = $file.Name; Local = $target; Bytes = $file.Bytes.Length
            Sha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($file.Bytes)) }
    }
    $manifest = [pscustomobject]@{
        Source = $source.FullName; Sha256 = (Get-FileHash -LiteralPath $source.FullName).Hash
        Parser = 'Unmodified SoulsFormats library embedded in UXM 2.4.2'
        Count = $binder.Files.Count; Entries = @($entries)
        Status = 'Raw binder entries; HKX tracks not decoded, skill identity unverified, no MC retargeting'
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destination 'manifest.json') -Encoding utf8
    $results += [pscustomobject]@{ Binder = $source.Name; Count = $binder.Files.Count; Extensions = @($entries | Group-Object { [IO.Path]::GetExtension($_.Local) } | Select-Object Name,Count) }
}
$results | ConvertTo-Json -Depth 5
