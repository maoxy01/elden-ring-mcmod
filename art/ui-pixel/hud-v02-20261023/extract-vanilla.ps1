$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$hudOutput = Join-Path $PSScriptRoot 'reference/vanilla'
New-Item -ItemType Directory -Path $hudOutput -Force | Out-Null
$hudZip = [IO.Compression.ZipFile]::OpenRead('C:/Users/30869/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
try {
    $hudEntries = @{
        'heart_full.png'='textures/gui/sprites/hud/heart/full.png'
        'heart_half.png'='textures/gui/sprites/hud/heart/half.png'
        'heart_container.png'='textures/gui/sprites/hud/heart/container.png'
        'armor_full.png'='textures/gui/sprites/hud/armor_full.png'
        'armor_empty.png'='textures/gui/sprites/hud/armor_empty.png'
        'food_full.png'='textures/gui/sprites/hud/food_full.png'
        'food_empty.png'='textures/gui/sprites/hud/food_empty.png'
        'air.png'='textures/gui/sprites/hud/air.png'
        'hotbar.png'='textures/gui/sprites/hud/hotbar.png'
        'hotbar_selection.png'='textures/gui/sprites/hud/hotbar_selection.png'
        'xp_background.png'='textures/gui/sprites/hud/experience_bar_background.png'
        'xp_fill.png'='textures/gui/sprites/hud/experience_bar_progress.png'
        'ascii.png'='textures/font/ascii.png'
        'font.json'='font/include/default.json'
    }
    foreach ($hudName in $hudEntries.Keys) {
        $hudEntry = $hudZip.GetEntry('assets/minecraft/'+$hudEntries[$hudName])
        if (!$hudEntry) { throw "Missing native HUD asset: $hudName" }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($hudEntry,(Join-Path $hudOutput $hudName),$true)
    }
} finally { $hudZip.Dispose() }
$hudAssetRoot = 'C:/Users/30869/.gradle/caches/neoformruntime/assets'
$hudIndex = Get-Content -LiteralPath (Join-Path $hudAssetRoot 'indexes/17.json') -Raw | ConvertFrom-Json
$hudFontHash = $hudIndex.objects.'minecraft/font/unifont.zip'.hash
$hudFontArchive = Join-Path $hudAssetRoot ('objects/'+$hudFontHash.Substring(0,2)+'/'+$hudFontHash)
$hudFontZip = [IO.Compression.ZipFile]::OpenRead($hudFontArchive)
try {
    $hudHexEntry = $hudFontZip.Entries | Where-Object FullName -Like '*.hex' | Select-Object -First 1
    [IO.Compression.ZipFileExtensions]::ExtractToFile($hudHexEntry,(Join-Path $hudOutput 'unifont.hex'),$true)
    [IO.Compression.ZipFileExtensions]::ExtractToFile($hudFontZip.GetEntry('LICENSE.txt'),(Join-Path $hudOutput 'unifont-LICENSE.txt'),$true)
} finally { $hudFontZip.Dispose() }
Write-Output 'Extracted 14 vanilla HUD preview references.'
