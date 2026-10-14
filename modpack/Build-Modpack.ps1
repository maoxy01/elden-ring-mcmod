[CmdletBinding()]
param([string]$OutputDirectory = 'dist')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$version = '0.2.0'
$jarName = "eldencombat-$version.jar"
$jar = Join-Path $root "build/libs/$jarName"
$lock = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'mods.lock.json') -Raw | ConvertFrom-Json
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
    if(@($zip.Entries|Where-Object FullName -Match 'SmokeHarness').Count) { throw 'Test harness leaked into release' }
    $mixins=(Read-Entry $zip 'eldencombat.mixins.json')|ConvertFrom-Json
    if(($mixins.client -join ',') -ne 'PlayerModelPoseMixin' -or !$zip.GetEntry('com/dsh/eldencombat/mixin/PlayerModelPoseMixin.class')) { throw 'Unexpected pose mixins' }
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
if($lock.mods.Count -ne 7) { throw 'Expected seven requested mods' }
$files=foreach($mod in $lock.mods) {
    $file=Join-Path $root ('build/modpack-dependencies/'+$mod.path.Substring(5))
    if($mod.path -notmatch '^mods/[\w.+-]+\.jar$' -or $mod.downloads[0] -notlike 'https://cdn.modrinth.com/*') { throw 'Unsafe dependency path' }
    if((Get-Item -LiteralPath $file).Length -ne $mod.fileSize -or
        (Get-FileHash -LiteralPath $file -Algorithm SHA512).Hash.ToLowerInvariant() -ne $mod.hashes.sha512) { throw "Dependency not verified: $($mod.name)" }
    [ordered]@{path=$mod.path;hashes=$mod.hashes;env=$mod.env;downloads=@($mod.downloads);fileSize=$mod.fileSize}
}
$metadata=[ordered]@{
    formatVersion=1;game='minecraft';versionId=$version;name="Elden Combat $version - Vanilla Combat"
    summary='Remembrance skills and weapons; seven performance/configuration/item mods'
    files=@($files);dependencies=@{minecraft=$lock.minecraft;neoforge=$lock.neoforge}
}
$jarHash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()
$receipt=[ordered]@{name=$metadata.name;version=$version;minecraft=$lock.minecraft;neoforge=$lock.neoforge;java=21;
    scope='Combat candidate; skill jumps supported; PCL installation pending; no map or shader pack';
    ownMod=@{path="mods/$jarName";sha256=$jarHash};thirdPartyMods=$lock.mods}
$payload=[ordered]@{}
$payload['modrinth.index.json']=$utf8.GetBytes(($metadata|ConvertTo-Json -Depth 12)+"`n")
$payload["overrides/mods/$jarName"]=[IO.File]::ReadAllBytes($jar)
foreach($relative in @('config/eldencombat-combat.json','README-zh.md')) {
    $payload["overrides/$relative"]=[IO.File]::ReadAllBytes((Join-Path $PSScriptRoot "overrides/$relative"))
}
$payload['client-overrides/options.txt']=[IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'overrides/options.txt'))
$payload['client-overrides/config/entityculling.json']=[IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'client-overrides/config/entityculling.json'))
$payload['overrides/eldencombat-pack.json']=$utf8.GetBytes(($receipt|ConvertTo-Json -Depth 12)+"`n")
$payload['overrides/THIRD-PARTY.md']=[IO.File]::ReadAllBytes((Join-Path $root 'docs/THIRD-PARTY.md'))
$checksums=foreach($name in @("EldenCombat-$version-PCL.zip","EldenCombat-$version.mrpack")) {
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
            if($index.files.Count -ne 7 -or $index.dependencies.neoforge -ne '21.1.219') { throw 'Wrong dependency manifest' }
        } finally { $zip.Dispose() }
        Move-Item -LiteralPath $temp -Destination $path -Force
    } finally { if(Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp } }
    Write-Host "PACK_PASS $path ($((Get-Item -LiteralPath $path).Length) bytes; seven verified downloads)"
    "$((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant())  $name"
}
[IO.File]::WriteAllLines((Join-Path $output 'SHA256SUMS.txt'),[string[]]$checksums,$utf8)
Write-Host "MOD_SHA256 $jarHash"

