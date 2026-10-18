[CmdletBinding()]
param(
    [string]$OutputDirectory = 'dist',
    [string]$LockPath = 'modpack/mods.lock.json',
    [string]$DependencyDirectory = 'build/modpack-dependencies',
    [switch]$Candidate,
    [string]$StageDirectory = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$version = '0.2.0'
$jarName = "eldencombat-$version.jar"
$jar = Join-Path $root "build/libs/$jarName"
$lock = Get-Content -LiteralPath (Join-Path $root $LockPath) -Raw | ConvertFrom-Json
$modIds=@($lock.mods | Where-Object {$_.PSObject.Properties['modId']} | ForEach-Object {$_.modId})
if($lock.minecraft -ne '1.21.1' -or $lock.neoforge -ne '21.1.219') { throw 'Wrong platform lock' }
if($lock.PSObject.Properties['releaseApproved'] -and !$lock.releaseApproved -and !$Candidate) { throw 'Unaccepted lock requires -Candidate' }
$utf8 = [Text.UTF8Encoding]::new($false)
$output = [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
if(!$output.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Output must stay inside the workspace' }
$null = New-Item -ItemType Directory -Force -Path $output
function Read-Entry($zip,$path) {
    $entry=$zip.GetEntry($path)
    if(!$entry) { throw "Missing entry $path" }
    $reader=[IO.StreamReader]::new($entry.Open())
    try { $reader.ReadToEnd() } finally { $reader.Dispose() }
}
function Hash-Stream($stream) {
    $hash=[Security.Cryptography.SHA256]::Create()
    try { [Convert]::ToHexString($hash.ComputeHash($stream)).ToLowerInvariant() } finally { $hash.Dispose() }
}
if(!(Test-Path -LiteralPath $jar)) { throw 'Build the release JAR first' }
$zip=[IO.Compression.ZipFile]::OpenRead($jar)
try {
    $toml=Read-Entry $zip 'META-INF/neoforge.mods.toml'
    if(!$toml.Contains('version = "0.2.0"') -or $toml.Contains('modId = "epicfight"') -or !$toml.Contains('versionRange = "[21.1.219,)"')) { throw 'Wrong JAR metadata' }
    $dependencies=[regex]::Matches($toml,'(?s)\[\[dependencies\.eldencombat\]\]\s*modId\s*=\s*"([^"]+)"')
    if((@($dependencies | ForEach-Object {$_.Groups[1].Value} | Sort-Object) -join ',') -ne 'minecraft,neoforge' -or
        !$toml.Contains('versionRange = "[1.21.1,1.21.2)"')) { throw 'Unexpected JAR dependencies' }
    if(@($zip.Entries|Where-Object FullName -Match 'Harness').Count) { throw 'Test harness leaked into release' }
    $mixins=(Read-Entry $zip 'eldencombat.mixins.json')|ConvertFrom-Json
    $expectedClient=@('PlayerModelPoseMixin','JourneyMapSizeMixin','ItemRendererEquipment2dMixin','CombatRollManagerMixin')
    if(($mixins.client -join ',') -ne ($expectedClient -join ',') -or ($mixins.mixins -join ',') -ne 'CombatRollServerMixin') { throw 'Unexpected mixin set' }
    foreach($mixin in @($mixins.client)+@($mixins.mixins)) {
        if(!$zip.GetEntry("com/dsh/eldencombat/mixin/$mixin.class")){throw "Missing mixin class $mixin"}
    }
    foreach($required in @('assets/eldencombat/skeletons/entity/margit.model.json',
        'assets/eldencombat/textures/entity/margit.png','data/eldencombat/dsh_skills/remembrance_parameters.json','assets/eldencombat/skill_poses/wave_of_gold.json','assets/eldencombat/skeletons/items/straight_sword.model.json','assets/eldencombat/skeletons/items/straight_sword.mesh.json','assets/eldencombat/textures/item/weapons.png','assets/eldencombat/skeletons/items/remembrance/sacred_relic_sword.model.json','assets/eldencombat/skeletons/items/remembrance/sacred_relic_sword.mesh.json','assets/eldencombat/textures/item/remembrance.png')) {
        if(!$zip.GetEntry($required)) { throw "Missing $required" }
    }
    $skills=(Read-Entry $zip 'data/eldencombat/dsh_skills/remembrance_parameters.json') | ConvertFrom-Json
    $proportions=(Read-Entry $zip 'assets/eldencombat/weapons/proportions.json') | ConvertFrom-Json
    if($proportions.entries.Count -ne 41 -or $proportions.geometryScaleBaked) { throw 'Wrong proportion profiles' }
    foreach($regionName in @('sword_grip_regions','utility_grip_regions')) {
    $grips=(Read-Entry $zip "assets/eldencombat/weapons/$regionName.json") | ConvertFrom-Json
    foreach($grip in $grips.entries) {
        $id=$grip.id.Substring('eldencombat:'.Length)
        if($id -notmatch '^[a-z0-9_]+$') { throw 'Unsafe grip ID' }
        $entry=$zip.GetEntry("assets/eldencombat/skeletons/items/$id.model.json")
        if(!$entry) { throw "Missing grip model $id" }
        $stream=$entry.Open()
        $expectedHash=if($regionName -eq 'sword_grip_regions'){$grip.modelSha256}else{$grip.sha256}
        try { if((Hash-Stream $stream) -ne $expectedHash) { throw "Stale grip region $id" } }
        finally { $stream.Dispose() }
    }
    }
    if($skills.Count -ne 8) { throw 'Expected eight remembrance skills' }
    foreach($skill in $skills) {
        foreach($required in @("assets/eldencombat/skill_poses/$($skill.id).json",
            "assets/eldencombat/skeletons/items/remembrance/$($skill.itemId).model.json",
            "assets/eldencombat/skeletons/items/remembrance/$($skill.itemId).mesh.json")) {
            if(!$zip.GetEntry($required)) { throw "Missing $required" }
        }
    }
} finally { $zip.Dispose() }
if(!$lock.mods.Count -or @($lock.mods.path | Sort-Object -Unique).Count -ne $lock.mods.Count -or
   @($lock.mods.project | Sort-Object -Unique).Count -ne $lock.mods.Count) { throw 'Empty or duplicate dependency manifest' }
$manual=@()
$files=foreach($mod in $lock.mods) {
    $file=Join-Path (Join-Path $root $DependencyDirectory) $mod.path.Substring(5)
    if($mod.PSObject.Properties['installApproved'] -and !$mod.installApproved){throw "Unapproved dependency $($mod.name)"}
    if($mod.path -notmatch '^mods/[\w.+-]+\.jar$') { throw 'Unsafe dependency path' }
    if((Get-Item -LiteralPath $file).Length -ne $mod.fileSize -or
        (Get-FileHash -LiteralPath $file -Algorithm SHA512).Hash.ToLowerInvariant() -ne $mod.hashes.sha512) { throw "Dependency not verified: $($mod.name)" }
    if($mod.PSObject.Properties['platform'] -and $mod.platform -eq 'curseforge') {
        if($mod.source -notmatch '^https://www\.curseforge\.com/minecraft/mc-mods/[a-z0-9-]+/files/\d+$' -or !$mod.project -or !$mod.fileId){throw 'Missing CurseForge identity'}
        $manual+=@{name=$mod.name;projectId=$mod.project;fileId=$mod.fileId;source=$mod.source;path=$mod.path;hashes=$mod.hashes;fileSize=$mod.fileSize}
        continue
    }
    if(([Uri]$mod.downloads[0]).Host -ne 'cdn.modrinth.com' -or ([Uri]$mod.downloads[0]).Scheme -ne 'https') { throw 'Unsafe manifest source' }
    # mrpack accepts sha1 and sha512 only; observed sha256 stays in the full receipt.
    [ordered]@{path=$mod.path;hashes=@{sha1=$mod.hashes.sha1;sha512=$mod.hashes.sha512};env=$mod.env;downloads=@($mod.downloads);fileSize=$mod.fileSize}
}
$files=@($files)
$metadata=[ordered]@{
    formatVersion=1;game='minecraft';versionId=$version;name="Elden Combat $version - Vanilla Combat"
    summary="Integration candidate: $($lock.mods.Count) third-party mods; $($manual.Count) exact CurseForge files require manual installation; runtime acceptance pending"
    files=@($files);dependencies=@{minecraft=$lock.minecraft;neoforge=$lock.neoforge}
}
$jarHash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
$receipt=[ordered]@{name=$metadata.name;version=$version;minecraft=$lock.minecraft;neoforge=$lock.neoforge;java=21;
    scope='Integration candidate; read README for manual files and pending runtime acceptance';
    candidate=[bool]$Candidate;requiredManualFiles=$manual;runtimeAccepted=$false;
    ownMod=@{path="mods/$jarName";sha256=$jarHash};thirdPartyMods=$lock.mods}
$payload=[ordered]@{}
$payload['modrinth.index.json']=$utf8.GetBytes(($metadata|ConvertTo-Json -Depth 12)+"`n")
$payload["overrides/mods/$jarName"]=[IO.File]::ReadAllBytes($jar)
foreach($relative in @('config/eldencombat-combat.json','config/combat_roll/server.json5')) {
    if($relative.Contains('combat_roll') -and 'combat_roll' -notin $modIds){continue}
    $payload["overrides/$relative"]=[IO.File]::ReadAllBytes((Join-Path $PSScriptRoot "overrides/$relative"))
}
$payload['client-overrides/options.txt']=[IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'overrides/options.txt'))
$payload['client-overrides/config/entityculling.json']=[IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'client-overrides/config/entityculling.json'))
if('combat_roll' -in $modIds) {
    $payload['client-overrides/config/combat_roll/hud_config.json']=[IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'client-overrides/config/combat_roll/hud_config.json'))
}
$payload['overrides/eldencombat-pack.json']=$utf8.GetBytes(($receipt|ConvertTo-Json -Depth 12)+"`n")
$readme="整合候选：MC1.21.1 / NeoForge21.1.219 / Java21。`n共$($lock.mods.Count)个第三方模组与本项目模组；未通过全包验收。仅导入新隔离实例。`n"
if($manual.Count){$readme+="必须按REQUIRED-CURSEFORGE-FILES.json补齐$($manual.Count)个官方精确文件到mods后才是完整实例；不能把导入成功当作全包安装成功。`n"}
$readme+="Combat Roll使用左Alt，R仅切换战斗模式；旧翻滚入口停用。Mod Menu使用NeoForge内置菜单，韧性条使用Again版；LazyDFU Forge无匹配文件暂缺。`n第三方武器保留原生能力，尚未统一项目FP或赐福换招。`n"
$payload['overrides/README-zh.md']=$utf8.GetBytes($readme)
$payload['overrides/REQUIRED-CURSEFORGE-FILES.json']=$utf8.GetBytes((ConvertTo-Json -InputObject @($manual) -Depth 10)+"`n")
$notice="# Third-party references`n`nOfficial unmodified releases; do not copy upstream assets into the project JAR. Preserve upstream licenses. CurseForge files are provided by exact official project/file references and are not embedded in overrides.`n`n"
foreach($mod in $lock.mods){$notice+="- $($mod.name) $($mod.version): $($mod.downloads[0])`n"}
$payload['overrides/THIRD-PARTY.md']=$utf8.GetBytes($notice)
$suffix=if($Candidate){'-integration-candidate'}else{''}
$checksums=foreach($name in @("EldenCombat-$version$suffix-PCL.zip","EldenCombat-$version$suffix.mrpack")) {
    $path=Join-Path $output $name
    $temp=Join-Path $output ('.'+[Guid]::NewGuid().ToString('N')+'.tmp')
    try {
        $zip=[IO.Compression.ZipFile]::Open($temp,[IO.Compression.ZipArchiveMode]::Create)
        try {
            foreach($key in $payload.Keys) {
                $entry=$zip.CreateEntry($key,[IO.Compression.CompressionLevel]::Optimal)
                $entry.LastWriteTime=[DateTimeOffset]::Parse('2026-01-01T00:00:00Z')
                $stream=$entry.Open()
                try { $stream.Write($payload[$key],0,$payload[$key].Length) } finally { $stream.Dispose() }
            }
        } finally { $zip.Dispose() }
        $zip=[IO.Compression.ZipFile]::OpenRead($temp)
        try {
            if($zip.Entries.Count -ne $payload.Count) { throw 'Unexpected archive content' }
            foreach($entry in $zip.Entries) {
                if($entry.FullName -notin $payload.Keys) { throw 'Unapproved archive content' }
                $stream=$entry.Open();$expected=[IO.MemoryStream]::new([byte[]]$payload[$entry.FullName])
                try { if((Hash-Stream $stream) -ne (Hash-Stream $expected)) { throw 'Payload mismatch' } }
                finally { $stream.Dispose();$expected.Dispose() }
                if($entry.FullName.EndsWith('.json')) { $null=(Read-Entry $zip $entry.FullName)|ConvertFrom-Json }
            }
            $index=(Read-Entry $zip 'modrinth.index.json')|ConvertFrom-Json
            if($index.files.Count -ne $files.Count -or $index.dependencies.neoforge -ne '21.1.219' -or
               (@($index.files.path | Sort-Object) -join ',') -ne (@($files.path | Sort-Object) -join ',')) { throw 'Wrong dependency manifest' }
        } finally { $zip.Dispose() }
        Move-Item -LiteralPath $temp -Destination $path -Force
    } finally { if(Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp } }
    Write-Host "PACK_PASS $path ($((Get-Item -LiteralPath $path).Length) bytes; $($files.Count) referenced downloads; $($manual.Count) manual files)"
    "$((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant())  $name"
}
[IO.File]::WriteAllLines((Join-Path $output 'SHA256SUMS.txt'),[string[]]$checksums,$utf8)
Write-Host "MOD_SHA256 $jarHash"
if($StageDirectory) {
    $stageFinal=[IO.Path]::GetFullPath((Join-Path $root $StageDirectory))
    $stage=$stageFinal+'.partial'
    if(!$stageFinal.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $stageFinal) -or (Test-Path -LiteralPath $stage)) {
        throw 'Staging requires a new directory inside the workspace'
    }
    foreach($side in @('client','server')) {
        $instance=Join-Path $stage $side
        $null=New-Item -ItemType Directory -Force -Path (Join-Path $instance 'mods')
        $installed=@()
        foreach($mod in $lock.mods | Where-Object {$_.env.$side -eq 'required'}) {
            $destination=Join-Path $instance $mod.path
            Copy-Item -LiteralPath (Join-Path (Join-Path $root $DependencyDirectory) $mod.path.Substring(5)) -Destination $destination
            if((Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash.ToLowerInvariant() -ne $mod.hashes.sha512){throw 'Staged dependency changed'}
            $source=if($mod.PSObject.Properties['source']){$mod.source}else{"https://modrinth.com/mod/$($mod.project)/version/$($mod.versionId)"}
            $installed+=@{path=$mod.path;sha512=$mod.hashes.sha512;source=$source}
        }
        Copy-Item -LiteralPath $jar -Destination (Join-Path $instance "mods/$jarName")
        if((Get-FileHash -LiteralPath (Join-Path $instance "mods/$jarName") -Algorithm SHA256).Hash.ToLowerInvariant() -ne $jarHash){throw 'Staged project JAR changed'}
        $installed+=@{path="mods/$jarName";sha256=$jarHash}
        foreach($key in $payload.Keys) {
            $relative=$null
            if($key.StartsWith('overrides/config/')) {$relative=$key.Substring(10)}
            if($side -eq 'client' -and $key.StartsWith('client-overrides/')) {$relative=$key.Substring(17)}
            if(!$relative){continue}
            $destination=Join-Path $instance $relative
            $null=New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($destination))
            [IO.File]::WriteAllBytes($destination,[byte[]]$payload[$key])
        }
        Copy-Item -LiteralPath (Join-Path $root $LockPath) -Destination (Join-Path $instance 'candidate.lock.json')
        $inventory=@{side=$side;minecraft=$lock.minecraft;neoforge=$lock.neoforge;java=21;filesComplete=$true;runtimeAccepted=$false;files=$installed}
        [IO.File]::WriteAllText((Join-Path $instance 'INSTANCE-CONTENTS.json'),($inventory|ConvertTo-Json -Depth 12),$utf8)
        [IO.File]::WriteAllText((Join-Path $instance 'README-zh.md'),"本地隔离候选，$($installed.Count)个JAR已补齐并校验。需要Minecraft1.21.1 / NeoForge21.1.219 / Java21启动环境；此目录不含启动器或游戏库。运行验收未完成，勿覆盖旧实例。`n",$utf8)
        Write-Host "STAGE_VERIFIED $side $($installed.Count) JARs"
    }
    Move-Item -LiteralPath $stage -Destination $stageFinal
    Write-Host "STAGE_PASS $stageFinal"
}

