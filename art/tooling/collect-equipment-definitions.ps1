$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$out = Join-Path $root 'art/equipment-reuse'
$null = New-Item -ItemType Directory -Path $out -Force
$providers = @(
    @{namespace='spartan_weaponry_unofficial';jar='spartan_weaponry_unofficial-1.2.3-1.21.1.jar';ids=@('iron_dagger','iron_longsword','iron_greatsword','iron_rapier','iron_saber','iron_katana','iron_battleaxe','iron_flanged_mace','iron_battle_hammer','iron_spear','iron_lance','iron_halberd','iron_scythe','longbow_iron_strengthened','heavy_crossbow_iron_strengthened','cestus')},
    @{namespace='simplyswords';jar='simplyswords-neoforge-1.70.2-1.21.1.jar';ids=@('iron_twinblade','iron_claymore')},
    @{namespace='minecraft';jar='neoforge-21.1.219-client-extra-aka-minecraft-resources.jar';ids=@('iron_sword','iron_axe','bow','crossbow','shield')}
)
$definitions = @()
foreach ($provider in $providers) {
    $jar = Join-Path $root ('docs/research-evidence/reuse-audit-20261013/' + $provider.jar)
    if ($provider.namespace -eq 'minecraft') { $jar = Join-Path $root ('build/moddev/artifacts/' + $provider.jar) }
    $zip = [IO.Compression.ZipFile]::OpenRead($jar)
    function Read-Json([string]$name) {
        $entry = $zip.GetEntry($name)
        if (-not $entry) { throw "Missing entry: $name" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    }
    try {
        foreach ($id in $provider.ids) {
            $modelPath = "assets/$($provider.namespace)/models/item/$id.json"
            $recipePath = "data/$($provider.namespace)/recipe/$id.json"
            $model = Read-Json $modelPath
            $recipe = if ($provider.namespace -eq 'minecraft') { @{result=@{id="minecraft:$id"}} } else { Read-Json $recipePath }
            if ($recipe.result.id -ne "$($provider.namespace):$id") { throw "Recipe ID mismatch: $id" }
            $chain = @([ordered]@{entry=$modelPath;parent=$model.parent;elements=if($null -ne $model.elements){$model.elements.Count}else{0};display=$model.display})
            $current = $model
            $visited = @{}
            while ($current.parent -like "$($provider.namespace):*") {
                if ($visited.ContainsKey($current.parent)) { throw 'Parent cycle' }
                $visited[$current.parent] = $true
                $parentPath = "assets/$($provider.namespace)/models/" + ($current.parent -split ':',2)[1] + '.json'
                $current = Read-Json $parentPath
                $chain += [ordered]@{entry=$parentPath;parent=$current.parent;elements=if($null -ne $current.elements){$current.elements.Count}else{0};display=$current.display}
            }
            $definitions += [ordered]@{itemId=$recipe.result.id;jar=$provider.jar;jarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLower();recipeEntry=if($provider.namespace -eq 'minecraft'){$null}else{$recipePath};idEvidence=if($provider.namespace -eq 'minecraft'){'Known Minecraft built-in ID; installed engine resource definition, not recipe/registry inspection'}else{'Exact release recipe result.id'};modelEntry=$modelPath;layer0=$model.textures.layer0;modelChain=$chain;rootParent=$current.parent;explicitElements=if($null -ne $current.elements){$current.elements.Count}else{0};runtimeRegistryVerified=$false}
        }
    } finally { $zip.Dispose() }
}
$data = [ordered]@{date='2026-10-13';purpose='Isolated mapping evidence, not models admitted to runtime';definitions=$definitions}
[IO.File]::WriteAllText((Join-Path $out 'definition-evidence.json'),($data | ConvertTo-Json -Depth 25),[Text.UTF8Encoding]::new($false))
Write-Output "PASS $($definitions.Count) release/engine definitions; no runtime writes"
