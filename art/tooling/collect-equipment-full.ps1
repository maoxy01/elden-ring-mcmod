$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$out = Join-Path $root 'art/equipment-reuse/research-only-full'
$null = New-Item -ItemType Directory -Path $out -Force
$mapping = [ordered]@{
    straight_sword='minecraft:iron_sword'; axe='minecraft:iron_axe'; short_bow='minecraft:bow'; crossbow='minecraft:crossbow'
    dagger='iron_dagger'; thrusting_sword='iron_rapier'; katana='iron_katana'; greataxe='iron_battleaxe'
    hammer='iron_flanged_mace'; great_hammer='iron_battle_hammer'; spear='iron_spear'; halberd='iron_halberd'; scythe='iron_scythe'; long_bow='longbow_iron_strengthened'
}
$jars = @{
    minecraft='build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar'
    spartan_weaponry_unofficial='docs/research-evidence/reuse-audit-20261013/spartan_weaponry_unofficial-1.2.3-1.21.1.jar'
}
$archives=@{}; $hashes=@{}
function Qualified([string]$id) { if ($id.Contains(':')) { return $id }; return "minecraft:$id" }
function Definition([string]$id) {
    $id=Qualified $id
    $parts=$id -split ':',2
    $entry="assets/$($parts[0])/models/$($parts[1]).json"
    $archive=$archives[$parts[0]]
    if (-not $archive) { throw "No archive for $id" }
    $record=$archive.GetEntry($entry)
    if (-not $record) { throw "Missing $entry" }
    $reader=[IO.StreamReader]::new($record.Open())
    try { return @{id=$id;entry=$entry;definition=($reader.ReadToEnd() | ConvertFrom-Json -AsHashtable)} } finally { $reader.Dispose() }
}
try {
    foreach ($ns in $jars.Keys) {
        $p=Join-Path $root $jars[$ns]
        $archives[$ns]=[IO.Compression.ZipFile]::OpenRead($p)
        $hashes[$ns]=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLower()
    }
    $items=@()
    foreach ($id in $mapping.Keys) {
        $provider=$mapping[$id]
        if (-not $provider.Contains(':')) { $provider="spartan_weaponry_unofficial:$provider" }
        $ns,$item=$provider -split ':',2
        $queue=[Collections.Generic.Queue[object]]::new()
        $queue.Enqueue(@{model="${ns}:item/$item";state='standby';predicate=@{}})
        $seen=@{}; $variants=@()
        while ($queue.Count) {
            $state=$queue.Dequeue(); $modelId=Qualified $state.model
            if ($seen.ContainsKey($modelId)) { continue }; $seen[$modelId]=$true
            $chain=@();$textures=@{};$overrides=@();$display=$null;$elements=0;$current=$modelId
            while ($current -notmatch '(?:^|:)builtin/') {
                if ($chain.id -contains $current) { throw "Model cycle $current" }
                $record=Definition $current; $chain+=,$record; $d=$record.definition
                if ($d.ContainsKey('elements')) { $elements += $d.elements.Count }
                if (-not $display -and $d.display) { $display=$d.display }
                if ($d.textures) { foreach ($key in $d.textures.Keys) { if (-not $textures.ContainsKey($key)) { $textures[$key]=$d.textures[$key] } } }
                if ($d.overrides) { $overrides+= $d.overrides }
                if (-not $d.parent) { break }; $current=Qualified $d.parent
            }
            $images=@()
            foreach ($key in $textures.Keys) {
                $texture=$textures[$key];$aliases=@()
                while ($texture.StartsWith('#')) {
                    if ($aliases -contains $texture) { throw "Texture cycle $texture" }; $aliases+=$texture
                    $texture=$textures[$texture.Substring(1)]
                    if (-not $texture) { throw "Missing alias $key" }
                }
                $texture=Qualified $texture; $tns,$tpath=$texture -split ':',2
                $entryName="assets/$tns/textures/$tpath.png"; $entry=$archives[$tns].GetEntry($entryName)
                if (-not $entry) { throw "Missing $entryName" }
                $filename=($texture -replace '[:/]','_')+'.png';$target=Join-Path $out $filename
                $input=$entry.Open();$output=[IO.File]::Create($target)
                try {$input.CopyTo($output)} finally {$input.Dispose();$output.Dispose()}
                $images+=@{layer=$key;textureId=$texture;entry=$entryName;file="art/equipment-reuse/research-only-full/$filename";sha256=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLower()}
            }
            foreach ($o in $overrides) { $queue.Enqueue(@{model=$o.model;state=($o.model -split '/')[-1];predicate=$o.predicate}) }
            $variants+=@{state=$state.state;modelId=$modelId;predicate=$state.predicate;modelChain=$chain;display=$display;images=$images;explicitElements=$elements;terminalParent=$current}
        }
        $items+=@{id=$id;providerId=$provider;sourceJar=$jars[$ns];sourceJarSha256=$hashes[$ns];variants=$variants;strict2d=(@($variants | Where-Object {$_.explicitElements -gt 0 -or $_.terminalParent -match 'builtin/entity'}).Count -eq 0);installed=$null;runtimeRegistryVerified=$false;publishableAsOwnAsset=$false}
    }
    $report=@{date='2026-10-15';purpose='Local read-only provider evidence; copied textures excluded from redistribution';items=$items}
    [IO.File]::WriteAllText((Join-Path $out 'evidence.json'),($report | ConvertTo-Json -Depth 70),[Text.UTF8Encoding]::new($false))
    Write-Output "PASS $($items.Count) providers, $((($items | ForEach-Object {$_.variants}) | Measure-Object).Count) model states; no runtime writes"
} finally { foreach ($archive in $archives.Values) {$archive.Dispose()} }
