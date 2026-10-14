[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$directory = Join-Path $root 'build/modpack-dependencies'
$null = New-Item -ItemType Directory -Force -Path $directory
$pins = @(
    @{ name='Sodium'; id='Pb3OXVqC'; client='required'; server='unsupported' },
    @{ name='Iris Shaders'; id='t3ruzodq'; client='required'; server='unsupported' },
    @{ name='Entity Culling'; id='eieYbWYb'; client='required'; server='unsupported' },
    @{ name='Cloth Config API'; id='izKINKFg'; client='required'; server='required' },
    @{ name='FerriteCore'; id='x7kQWVju'; client='required'; server='required' },
    @{ name='Lithium'; id='DDUrRVCA'; client='required'; server='required' },
    @{ name='Just Enough Items'; id='YAcQ6elZ'; client='required'; server='optional' }
)
$mods = foreach ($pin in $pins) {
    $release = Invoke-RestMethod "https://api.modrinth.com/v2/version/$($pin.id)" -Headers @{'User-Agent'='EldenCombat/0.2.0 local-pack-builder'} -TimeoutSec 90
    if ('1.21.1' -notin $release.game_versions -or 'neoforge' -notin $release.loaders) { throw "Wrong loader: $($pin.name)" }
    $file = @($release.files | Where-Object primary)[0]
    if ($file.filename -notmatch '^[\w.+-]+\.jar$' -or $file.url -notlike 'https://cdn.modrinth.com/*') { throw 'Unsafe download path' }
    $destination = Join-Path $directory $file.filename
    if (!(Test-Path -LiteralPath $destination) -or (Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash.ToLowerInvariant() -ne $file.hashes.sha512) {
        Invoke-WebRequest $file.url -OutFile $destination -TimeoutSec 180
    }
    if ((Get-Item -LiteralPath $destination).Length -ne $file.size -or
        (Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash.ToLowerInvariant() -ne $file.hashes.sha512) { throw "Hash mismatch: $($pin.name)" }
    Write-Host "MOD_VERIFIED $($pin.name) $($release.version_number)"
    [ordered]@{ name=$pin.name; project=$release.project_id; versionId=$release.id; version=$release.version_number;
        path="mods/$($file.filename)"; hashes=$file.hashes; downloads=@($file.url); fileSize=$file.size;
        env=@{client=$pin.client;server=$pin.server} }
}
$json = [ordered]@{ minecraft='1.21.1'; neoforge='21.1.219'; mods=@($mods) } | ConvertTo-Json -Depth 10
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'mods.lock.json'), $json+"`n", [Text.UTF8Encoding]::new($false))

