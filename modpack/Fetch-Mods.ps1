[CmdletBinding()]
param(
    [string]$LockPath = 'modpack/mods.lock.json',
    [string]$OutputDirectory = 'build/modpack-dependencies',
    [ValidateSet('all','client','server')][string]$Side = 'all',
    [string[]]$CacheDirectory = @(),
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$directory = [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
if (!$directory.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Output must stay inside workspace' }
$null = New-Item -ItemType Directory -Force -Path $directory
$lock = Get-Content -LiteralPath (Join-Path $root $LockPath) -Raw | ConvertFrom-Json
if ($lock.minecraft -ne '1.21.1' -or $lock.neoforge -ne '21.1.219') { throw 'Wrong platform lock' }
if (@($lock.mods.path | Sort-Object -Unique).Count -ne $lock.mods.Count -or
    @($lock.mods.project | Sort-Object -Unique).Count -ne $lock.mods.Count) { throw 'Duplicate dependency' }
function Verified($path,$mod) {
    (Test-Path -LiteralPath $path -PathType Leaf) -and
    (Get-Item -LiteralPath $path).Length -eq $mod.fileSize -and
    (Get-FileHash -LiteralPath $path -Algorithm SHA512).Hash.ToLowerInvariant() -eq $mod.hashes.sha512
}
function Read-ModIds($archive,[int]$depth=0) {
    if($depth -gt 5){throw 'Unsupported nested dependency depth'}
    $entry=$archive.GetEntry('META-INF/neoforge.mods.toml')
    if($entry) {
        $reader=[IO.StreamReader]::new($entry.Open())
        try {$body=$reader.ReadToEnd()}finally{$reader.Dispose()}
        # Restrict to the mods sections; dependency modIds must not count as providers.
        $sections=[regex]::Matches($body,'(?s)\[\[mods\]\](.*?)(?=\[\[|\z)')
        if(!$sections.Count -and $body -match '(?s)\bmods\s*=\s*\['){$sections=@([pscustomobject]@{Value=$body})}
        foreach($section in $sections) {
            foreach($match in [regex]::Matches($section.Value,'\bmodId\s*=\s*[''"]([^''"]+)[''"]')) {$match.Groups[1].Value}
        }
    }
    $nested=$archive.GetEntry('META-INF/jarjar/metadata.json')
    if($nested) {
        $reader=[IO.StreamReader]::new($nested.Open())
        try {$jars=($reader.ReadToEnd()|ConvertFrom-Json).jars}finally{$reader.Dispose()}
        foreach($jar in $jars) {
            $part=$archive.GetEntry($jar.path)
            if(!$part){throw 'Missing nested dependency'}
            $memory=[IO.MemoryStream]::new();$stream=$part.Open()
            try {$stream.CopyTo($memory)}finally{$stream.Dispose()}
            $memory.Position=0
            $zip=[IO.Compression.ZipArchive]::new($memory,[IO.Compression.ZipArchiveMode]::Read)
            try {Read-ModIds $zip ($depth+1)}finally{$zip.Dispose();$memory.Dispose()}
        }
    }
}
# Follow redirects ourselves: every hop must remain on the exact platform CDN allowlist.
function Download-Official($url,$destination) {
    $handler=[Net.Http.HttpClientHandler]::new(); $handler.AllowAutoRedirect=$false
    $client=[Net.Http.HttpClient]::new($handler); $client.Timeout=[TimeSpan]::FromSeconds(180)
    try {
        for($hop=0;$hop -lt 6;$hop++) {
            $uri=[Uri]$url
            if($uri.Scheme -ne 'https' -or $uri.Host -notin @('cdn.modrinth.com','edge.forgecdn.net','mediafilez.forgecdn.net','media.forgecdn.net')) { throw 'Unsafe download source or redirect' }
            $response=$client.GetAsync($uri,[Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
            try {
                if([int]$response.StatusCode -in @(301,302,303,307,308)) { $url=[Uri]::new($uri,$response.Headers.Location).AbsoluteUri; continue }
                $null=$response.EnsureSuccessStatusCode()
                $stream=[IO.File]::Create($destination)
                try { $response.Content.CopyToAsync($stream).GetAwaiter().GetResult() } finally { $stream.Dispose() }
                return
            } finally { $response.Dispose() }
        }
        throw 'Too many redirects'
    } finally { $client.Dispose(); $handler.Dispose() }
}
$missing=@(); $verified=@()
foreach($mod in $lock.mods) {
    if($Side -ne 'all' -and $mod.env.$Side -ne 'required') { continue }
    if($mod.PSObject.Properties['installApproved'] -and !$mod.installApproved) { throw "Unapproved candidate: $($mod.name)" }
    if($mod.path -notmatch '^mods/[\w.+-]+\.jar$' -or $mod.hashes.sha512 -notmatch '^[a-fA-F0-9]{128}$') { throw 'Unsafe path or hash' }
    $platform=if($mod.PSObject.Properties['platform']){$mod.platform}else{'modrinth'}
    $uri=[Uri]$mod.downloads[0]
    if($uri.Scheme -ne 'https' -or
       ($platform -eq 'modrinth' -and $uri.Host -ne 'cdn.modrinth.com') -or
       ($platform -eq 'curseforge' -and $uri.Host -notin @('edge.forgecdn.net','mediafilez.forgecdn.net')) -or
       $platform -notin @('modrinth','curseforge')) { throw 'Unapproved platform source' }
    $filename=$mod.path.Substring(5); $destination=Join-Path $directory $filename
    if(!(Verified $destination $mod)) {
        foreach($cache in $CacheDirectory) {
            $candidate=Join-Path (Join-Path $root $cache) $filename
            if(Verified $candidate $mod) { Copy-Item -LiteralPath $candidate -Destination $destination; break }
        }
    }
    if(!(Verified $destination $mod) -and !$Offline) {
        if($platform -eq 'modrinth') {
            $release=Invoke-RestMethod "https://api.modrinth.com/v2/version/$($mod.versionId)" -Headers @{'User-Agent'='EldenCombat/0.2.0 local-pack-builder'} -TimeoutSec 60
            $file=@($release.files | Where-Object {$_.filename -eq $filename})[0]
            if('1.21.1' -notin $release.game_versions -or 'neoforge' -notin $release.loaders -or
               $file.hashes.sha512 -ne $mod.hashes.sha512 -or $file.url -ne $mod.downloads[0]) { throw "Publisher lock mismatch: $($mod.name)" }
        }
        $temp=$destination+'.download'
        try { Download-Official $mod.downloads[0] $temp; if(!(Verified $temp $mod)){throw 'Downloaded hash mismatch'}; Move-Item -LiteralPath $temp -Destination $destination -Force }
        catch { if($platform -ne 'curseforge'){throw}; $missing+=@{name=$mod.name;projectId=$mod.project;fileId=$mod.fileId;source=$mod.source;path=$mod.path;error=$_.Exception.Message} }
        finally { if(Test-Path -LiteralPath $temp){Remove-Item -LiteralPath $temp} }
    }
    if(!(Verified $destination $mod)) {
        if($Offline){$missing+=@{name=$mod.name;path=$mod.path;error='not in verified cache'}}
        continue
    }
    $archive=[IO.Compression.ZipFile]::OpenRead($destination)
    try {
        $ids=@(Read-ModIds $archive)
        if(!$ids.Count -or ($mod.PSObject.Properties['modId'] -and $mod.modId -notin $ids)) {throw "JAR modId mismatch: $($mod.name)"}
    } finally {$archive.Dispose()}
    $verified+=$mod.path
    Write-Host "MOD_VERIFIED $($mod.name) $($mod.version)"
}
$receipt=@{lockSha256=(Get-FileHash (Join-Path $root $LockPath) -Algorithm SHA256).Hash.ToLowerInvariant();side=$Side;verified=$verified;missing=$missing;complete=($missing.Count -eq 0)}
$receipt | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 (Join-Path $directory 'fetch-receipt.json')
if($missing.Count){throw "Instance incomplete: $($missing.Count) missing files; see fetch-receipt.json"}

