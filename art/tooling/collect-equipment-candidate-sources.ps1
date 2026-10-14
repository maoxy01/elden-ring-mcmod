$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$out = Join-Path $root 'art/equipment-reuse'
$null = New-Item -ItemType Directory -Path $out -Force
function Read-Source([string]$repo,[string]$sha,[string]$path) {
    $uri = "https://raw.githubusercontent.com/$repo/$sha/$path"
    $content = (Invoke-WebRequest -Uri $uri).Content
    return [ordered]@{url=$uri;sha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($content))).ToLower();content=$content}
}
$shieldRepo='Mai-xiyu/SpartanShields-NeoForge'
$shieldSha='a2a0eb62a98a2fa22d23e624948b1fa541048ce1'
$clawRepo='DakotaPride/vanilla-claws-forge'
$clawSha='bcad24f8166c0106fdd294bdb833f29fdc5757a0'
$sources=@()
foreach($path in @('src/main/resources/assets/spartan_shields_unofficial/models/item/basic_shield_base.json','src/main/resources/assets/spartan_shields_unofficial/models/item/tower_shield_base.json','src/generated/resources/data/spartan_shields_unofficial/recipe/iron_basic_shield.json','src/generated/resources/data/spartan_shields_unofficial/recipe/iron_tower_shield.json')) {
    $s=Read-Source $shieldRepo $shieldSha $path
    $j=$s.content | ConvertFrom-Json
    $sources += [ordered]@{url=$s.url;sha256=$s.sha256;kind='source-model-or-recipe';definition=$j}
}
foreach($spec in @(
    @{repo=$shieldRepo;sha=$shieldSha;path='src/main/java/org/xiyu/spartanshieldsunofficial/init/ModItems.java';pattern='DeferredRegister|ModID|MOD_ID|IRON_BASIC_SHIELD|IRON_TOWER_SHIELD|new BasicShieldItem'},
    @{repo=$shieldRepo;sha=$shieldSha;path='src/main/java/org/xiyu/spartanshieldsunofficial/ModSpartanShields.java';pattern='MOD_ID|MODID|ModId|modId|ModID|spartan_shields_unofficial'},
    @{repo=$clawRepo;sha=$clawSha;path='src/main/java/net/dakotapride/vanilla_claws/registry/ItemsInit.java';pattern='DeferredRegister|IRON|iron_claws|register\('},
    @{repo=$clawRepo;sha=$clawSha;path='src/main/java/net/dakotapride/vanilla_claws/VanillaClawsMain.java';pattern='MOD_ID|MODID|vanilla_claws'},
    @{repo=$clawRepo;sha=$clawSha;path='gradle.properties';pattern='minecraft_version|neo_version|mod_version|mod_id'}
)) {
    $s=Read-Source $spec.repo $spec.sha $spec.path
    $sources += [ordered]@{url=$s.url;sha256=$s.sha256;kind='source-registration-excerpt';lines=@($s.content -split "`n" | Where-Object {$_ -match $spec.pattern})}
}
foreach($path in @('src/main/resources/assets/vanilla_claws/models/item/iron_claws.json','src/main/resources/data/vanilla_claws/recipe/iron_claws.json')) {
    $s=Read-Source $clawRepo $clawSha $path
    $sources += [ordered]@{url=$s.url;sha256=$s.sha256;kind='source-model-or-recipe';definition=($s.content | ConvertFrom-Json)}
}
$data=[ordered]@{date='2026-10-13';scope='Source-only candidate evidence; release JAR not checked or installed';shieldCommit=$shieldSha;clawCommit=$clawSha;sources=$sources}
[IO.File]::WriteAllText((Join-Path $out 'candidate-source-evidence.json'),($data | ConvertTo-Json -Depth 35),[Text.UTF8Encoding]::new($false))
Write-Output "PASS $($sources.Count) pinned official source records; no third-party PNG imports"
