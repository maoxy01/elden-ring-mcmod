param(
    [Parameter(Mandatory)][string]$Source,
    [Parameter(Mandatory)][string]$Destination,
    [string]$Include = '.'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($Destination)
if (-not $root.StartsWith('D:\codexout\mcmod\build\elden-source\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Tool extraction must stay in the isolated workspace.'
}
$bytes = [IO.File]::ReadAllBytes($Source)
$signature = '8B1202B96A612038727B930214D7A03213F5B9E6EFAE3318EE3B2DCE24B36AAE'
$index = [Convert]::ToHexString($bytes).IndexOf($signature, [StringComparison]::Ordinal) / 2
if ($index -lt 8) { throw 'No .NET single-file bundle header.' }
$offset = [BitConverter]::ToInt64($bytes, $index - 8)
$stream = [IO.MemoryStream]::new($bytes, $false)
$reader = [IO.BinaryReader]::new($stream)
try {
    $stream.Position = $offset
    $major = $reader.ReadUInt32()
    $minor = $reader.ReadUInt32()
    $count = $reader.ReadInt32()
    $id = $reader.ReadString()
    if ($major -ne 6 -or $count -lt 1 -or $count -gt 1024) { throw 'Unexpected bundle format.' }
    $stream.Position += 40
    $entries = for ($i = 0; $i -lt $count; $i++) {
        [pscustomobject]@{
            Offset = $reader.ReadInt64(); Size = $reader.ReadInt64()
            CompressedSize = $reader.ReadInt64(); Type = $reader.ReadByte(); Path = $reader.ReadString()
        }
    }
    [IO.Directory]::CreateDirectory($root) | Out-Null
    foreach ($entry in $entries) {
        if ($entry.Path -notmatch $Include) { continue }
        $target = [IO.Path]::GetFullPath([IO.Path]::Combine($root, $entry.Path))
        if (-not $target.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe bundle entry.' }
        if ([IO.File]::Exists($target)) { throw "Refusing to overwrite $target" }
        $length = if ($entry.CompressedSize -gt 0) { $entry.CompressedSize } else { $entry.Size }
        if ($entry.Size -gt 100MB -or $entry.Offset -lt 0 -or $entry.Offset + $length -gt $bytes.Length) {
            throw 'Invalid bundle entry range.'
        }
        $stream.Position = $entry.Offset
        $content = $reader.ReadBytes([int]$length)
        if ($entry.CompressedSize -gt 0) {
            $compressed = [IO.MemoryStream]::new($content, $false)
            $deflate = [IO.Compression.DeflateStream]::new($compressed, [IO.Compression.CompressionMode]::Decompress)
            $output = [IO.MemoryStream]::new()
            try { $deflate.CopyTo($output); $content = $output.ToArray() }
            finally { $deflate.Dispose(); $compressed.Dispose(); $output.Dispose() }
        }
        if ($content.Length -ne $entry.Size) { throw 'Bundle entry size mismatch.' }
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
        [IO.File]::WriteAllBytes($target, $content)
    }
    $entries | Select-Object Path,Size,CompressedSize | ConvertTo-Json
}
finally { $reader.Dispose(); $stream.Dispose() }
