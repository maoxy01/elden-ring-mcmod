$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$out = Join-Path $root 'art/equipment-reuse/research-only-six'
$null = New-Item -ItemType Directory -Path $out -Force
$providers = @(
    @{name='minecraft';jar='build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar';ids=@('iron_sword')},
    @{name='spartan_weaponry_unofficial';jar='docs/research-evidence/reuse-audit-20261013/spartan_weaponry_unofficial-1.2.3-1.21.1.jar';ids=@('iron_spear','longbow_iron_strengthened')}
)
$entries = @()
foreach ($provider in $providers) {
    $jar = Join-Path $root $provider.jar
    $jarHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLower()
    $zip = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        function Read-Definition([string]$entryName) {
            $entry = $zip.GetEntry($entryName)
            if (-not $entry) { throw "Missing definition $entryName" }
            $reader = [IO.StreamReader]::new($entry.Open())
            try { return $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        }
        foreach ($id in $provider.ids) {
            $modelName = "assets/$($provider.name)/models/item/$id.json"
            $base = Read-Definition $modelName
            $variants = @(@{state='standby';model=$modelName;predicate=@{};definition=$base})
            foreach ($override in $base.overrides) {
                if ($override.model -notmatch '_pulling_[0-2]$') { continue }
                $parts = $override.model -split ':',2
                $name = "assets/$($parts[0])/models/$($parts[1]).json"
                $variants += @{state=($override.model -split '_strengthened_')[1];model=$name;predicate=$override.predicate;definition=(Read-Definition $name)}
            }
            foreach ($variant in $variants) {
                $texture = $variant.definition.textures.layer0
                $parts = $texture -split ':',2
                $entryName = "assets/$($parts[0])/textures/$($parts[1]).png"
                $entry = $zip.GetEntry($entryName)
                if (-not $entry) { throw "Missing texture $entryName" }
                $relative = "$($provider.name)-$id-$($variant.state).png"
                $target = Join-Path $out $relative
                $sourceStream=$entry.Open();$targetStream=[IO.File]::Create($target)
                try { $sourceStream.CopyTo($targetStream) } finally { $sourceStream.Dispose();$targetStream.Dispose() }
                $chain=@();$current=$variant.definition
                while ($current.parent -and $current.parent -notmatch '(?:^|:)builtin/' -and $current.parent -notmatch '^item/') {
                    $parentId=if($current.parent.Contains(':')){$current.parent}else{'minecraft:'+$current.parent}
                    $p=$parentId -split ':',2;$name="assets/$($p[0])/models/$($p[1]).json"
                    if (-not $zip.GetEntry($name)) { break }
                    $current=Read-Definition $name
                    $chain+=@{entry=$name;definition=$current}
                }
                $entries += [ordered]@{itemId="$($provider.name):$id";state=$variant.state;sourceJar=$provider.jar;sourceJarSha256=$jarHash;modelEntry=$variant.model;model=$variant.definition;parents=$chain;predicate=$variant.predicate;textureId=$texture;pngEntry=$entryName;file="art/equipment-reuse/research-only-six/$relative";sha256=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLower();modified=$false;publishableAsOwnAsset=$false;use='Local inspection only; runtime references complete original provider'}
            }
        }
    } finally { $zip.Dispose() }
}
$report=[ordered]@{date='2026-10-14';purpose='Unmodified local research snapshots, excluded from publishable assets';attribution='Minecraft: Mojang/Microsoft; Spartan: ObliviousSpartan, xwerffx, Mai_xiyu';entries=$entries}
[IO.File]::WriteAllText((Join-Path $out 'evidence.json'),($report | ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
Write-Output "PASS $($entries.Count) unmodified research PNGs; no runtime writes"
