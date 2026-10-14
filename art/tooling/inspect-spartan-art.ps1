$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$jar = Join-Path $root 'docs/research-evidence/reuse-audit-20261013/spartan_weaponry_unofficial-1.2.3-1.21.1.jar'
$out = Join-Path $root 'docs/research-evidence/spartan-art-20261013'
$null = New-Item -ItemType Directory -Path $out -Force
$zip = [IO.Compression.ZipFile]::OpenRead($jar)
function Read-Entry([string]$name) {
    $entry = $zip.GetEntry($name)
    if (-not $entry) { throw "Missing archive entry: $name" }
    $stream = $entry.Open()
    $memory = [IO.MemoryStream]::new()
    try { $stream.CopyTo($memory); return ,$memory.ToArray() }
    finally { $stream.Dispose(); $memory.Dispose() }
}
function Read-Json([string]$name) {
    return [Text.Encoding]::UTF8.GetString((Read-Entry $name)) | ConvertFrom-Json
}
try {
    $samples = @()
    foreach ($id in @('iron_dagger','iron_greatsword','iron_spear','longbow_iron_strengthened','heavy_crossbow_iron_strengthened','cestus')) {
        $name = "assets/spartan_weaponry_unofficial/models/item/$id.json"
        $model = Read-Json $name
        $models = @([ordered]@{entry=$name; definition=$model})
        $current = $model
        $visited = @{}
        while ($current.parent -like 'spartan_weaponry_unofficial:*') {
            $parent = $current.parent -replace '^spartan_weaponry_unofficial:', ''
            if ($visited.ContainsKey($parent)) { throw 'Model parent cycle' }
            $visited[$parent] = $true
            $name = "assets/spartan_weaponry_unofficial/models/$parent.json"
            $current = Read-Json $name
            $models += [ordered]@{entry=$name; definition=$current}
        }
        $texture = $model.textures.layer0
        if (-not $texture) { $texture = $current.textures.layer0 }
        if ($texture -notlike 'spartan_weaponry_unofficial:*') { throw "Unexpected layer0: $id $texture" }
        $texturePath = 'assets/spartan_weaponry_unofficial/textures/' + ($texture -replace '^spartan_weaponry_unofficial:', '') + '.png'
        $bytes = Read-Entry $texturePath
        $target = Join-Path $out "$id.png"
        if (Test-Path -LiteralPath $target) {
            $existing = [IO.File]::ReadAllBytes($target)
            if ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($existing)) -ne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))) { throw "Research sample collision: $id" }
        } else { [IO.File]::WriteAllBytes($target, $bytes) }
        $recipeEntry = "data/spartan_weaponry_unofficial/recipe/$id.json"
        $recipe = if ($zip.GetEntry($recipeEntry)) { Read-Json $recipeEntry } else { $null }
        $samples += [ordered]@{resourceBasename=$id; modelChain=$models; textureEntry=$texturePath; researchPng="$id.png"; textureSha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLower(); recipeResult=$recipe.result; recipeEntry=$recipeEntry; runtimeRegistryVerified=$false}
    }
    $metadata = [Text.Encoding]::UTF8.GetString((Read-Entry 'META-INF/neoforge.mods.toml'))
    $report = [ordered]@{date='2026-10-13'; jarFile=$jar; jarSha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLower(); purpose='Read-only art research; unmodified PNG samples outside runtime; not an admitted asset package'; officialFile='https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry-unofficial/files/8717810'; attribution='ObliviousSpartan (original author); xwerffx (weapon ideas and most art); Mai_xiyu (port), per bundled metadata'; licenseDeclaration='Apache License v2.0 in bundled metadata; per-asset exceptions not cleared for extraction/republication'; metadata=$metadata; samples=$samples}
    [IO.File]::WriteAllText((Join-Path $out 'evidence.json'), ($report | ConvertTo-Json -Depth 45), [Text.UTF8Encoding]::new($false))
    & 'D:/JDK21/bin/javap.exe' -classpath $jar -p -c org.xiyu.spartanweaponryunofficial.init.ModItems |
        Out-File -LiteralPath (Join-Path $out 'registry-bytecode.txt') -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw 'javap failed' }
    foreach ($kind in @('WeaponItemsMelee','WeaponItemsRanged')) {
        & 'D:/JDK21/bin/javap.exe' -classpath $jar -p -c -v ('org.xiyu.spartanweaponryunofficial.init.ModItems$' + $kind) |
            Out-File -LiteralPath (Join-Path $out "$kind-bytecode.txt") -Encoding utf8
        if ($LASTEXITCODE -ne 0) { throw 'javap group failed' }
    }
    Write-Output "PASS $($samples.Count) original research samples, parent references and recipe result IDs; no runtime writes"
} finally { $zip.Dispose() }
