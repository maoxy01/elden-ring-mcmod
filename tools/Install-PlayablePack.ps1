$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskRelease = Join-Path $taskRoot 'dist/playable-pack-20261023'
$taskStage = Join-Path $taskRoot 'build/pack-release-20261023/client'
$taskAudit = Get-Content -LiteralPath (Join-Path $taskRelease 'RUNTIME-AUDIT.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$taskFiles = Get-Content -LiteralPath (Join-Path $taskRelease 'INSTALL-FILES.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$taskMeta = Get-Content -LiteralPath (Join-Path $taskRelease 'PACKAGE-AUDIT.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if (!$taskAudit.filesComplete -or !$taskMeta.filesComplete) { throw 'Package or game runtime is incomplete' }
if (Get-Process javaw -ErrorAction SilentlyContinue) { throw 'Close Minecraft before installing' }
$taskName = 'Elden Combat 0.2.0 - 20261023'
$taskVersions = [IO.Path]::GetFullPath('D:/PCL/.minecraft/versions')
$taskTarget = Join-Path $taskVersions $taskName
if (!(Split-Path -Parent $taskTarget).Equals($taskVersions, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid target' }
if (Test-Path -LiteralPath $taskTarget) { throw 'New instance already exists; do not overwrite it' }
$taskProfilePath = $taskAudit.profile
if ((Get-FileHash -LiteralPath $taskProfilePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskAudit.profileSha256) { throw 'Source launch profile changed' }
foreach ($taskFile in $taskFiles.PSObject.Properties) {
    if ($taskFile.Name -match '(^[/\\]|\.\.|:)') { throw 'Unsafe install path' }
    $taskInput = Join-Path $taskStage $taskFile.Name
    if ((Get-FileHash -LiteralPath $taskInput -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskFile.Value.sha256) { throw "Package changed: $($taskFile.Name)" }
}
# Only a new directory is created. Never move, delete or write to an existing world.
$taskPartial = $taskTarget + '.installing'
if (Test-Path -LiteralPath $taskPartial) { throw 'Previous incomplete installation exists; preserve for inspection' }
$null = New-Item -ItemType Directory -Path $taskPartial
foreach ($taskFile in $taskFiles.PSObject.Properties) {
    $taskDestination = Join-Path $taskPartial $taskFile.Name
    $null = New-Item -ItemType Directory -Force -Path (Split-Path -Parent $taskDestination)
    Copy-Item -LiteralPath (Join-Path $taskStage $taskFile.Name) -Destination $taskDestination
    if ((Get-FileHash -LiteralPath $taskDestination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskFile.Value.sha256) { throw 'Installed file mismatch' }
}
$taskUtf8 = [Text.UTF8Encoding]::new($false)
$taskProfile = Get-Content -LiteralPath $taskProfilePath -Raw -Encoding UTF8 | ConvertFrom-Json
$taskProfile.id = $taskName
[IO.File]::WriteAllText((Join-Path $taskPartial ($taskName + '.json')), ($taskProfile | ConvertTo-Json -Depth 100), $taskUtf8)
$taskBaseJar = Join-Path $taskAudit.instance (([IO.Path]::GetFileNameWithoutExtension($taskProfilePath)) + '.jar')
$taskClientJar = Join-Path $taskPartial ($taskName + '.jar')
Copy-Item -LiteralPath $taskBaseJar -Destination $taskClientJar
if ((Get-FileHash -LiteralPath $taskClientJar -Algorithm SHA1).Hash.ToLowerInvariant() -ne $taskProfile.downloads.client.sha1) { throw 'Vanilla game JAR mismatch' }
$null = New-Item -ItemType Directory -Path (Join-Path $taskPartial 'PCL')
$taskSetup = "VersionArgumentIndieV2:True`r`nVersionArgumentIndie:1`r`nVersionRamType:1`r`nVersionRamCustom:15`r`n"
[IO.File]::WriteAllText((Join-Path $taskPartial 'PCL/Setup.ini'), $taskSetup, $taskUtf8)
[IO.File]::WriteAllText((Join-Path $taskPartial 'PCL/config.json'), '{"InstanceMigratedJava":true}', $taskUtf8)
$taskWorlds = @()
$taskSourceSaves = Join-Path $taskAudit.instance 'saves'
if (Test-Path -LiteralPath $taskSourceSaves) {
    Copy-Item -LiteralPath $taskSourceSaves -Destination (Join-Path $taskPartial 'saves') -Recurse
    foreach ($taskWorldFile in Get-ChildItem -LiteralPath $taskSourceSaves -File -Recurse) {
        $taskRelative = $taskWorldFile.FullName.Substring($taskSourceSaves.Length).TrimStart('\','/')
        $taskWorldHash = (Get-FileHash -LiteralPath $taskWorldFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if ((Get-FileHash -LiteralPath (Join-Path (Join-Path $taskPartial 'saves') $taskRelative) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskWorldHash) { throw 'World copy mismatch' }
        $taskWorlds += @{path=$taskRelative; sha256=$taskWorldHash}
    }
}
Move-Item -LiteralPath $taskPartial -Destination $taskTarget
$taskReceipt = @{instance=$taskTarget; time=(Get-Date).ToString('o'); jars=39; filesVerified=$taskFiles.PSObject.Properties.Name.Count; maxHeapGiB=3; pclRamCustom=15; worldCopySource=$taskSourceSaves; copiedWorldFiles=$taskWorlds; originalInstanceModified=$false; gameStarted=$false; runtimeAccepted=$false}
[IO.File]::WriteAllText((Join-Path $taskRelease 'INSTALLED.json'), ($taskReceipt | ConvertTo-Json -Depth 10), $taskUtf8)
Write-Output "INSTALLED $taskTarget; 39 JARs verified; copied world files: $($taskWorlds.Count); game not launched"
