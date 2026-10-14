param()
$ErrorActionPreference = 'Stop'
if ($env:COMPlus_ReadyToRun -ne '0') {
    throw 'Start a fresh pwsh process with COMPlus_ReadyToRun=0 before loading the bundled library.'
}
$root = 'D:/codexout/mcmod/build/elden-source'
[Reflection.Assembly]::LoadFrom((Join-Path $root 'tools/witchybnd-3.0.1.0/assemblies/SoulsFormats.dll')) | Out-Null
$reference = Import-Csv -LiteralPath (Join-Path $root 'skill-tae-reference.csv')
$wanted = @{
    820 = 'Wave of Gold'; 829 = 'Gold Breaker'; 838 = 'Destined Death'; 814 = "Taker's Flames"
    828 = 'Waterfowl Dance'; 832 = 'Starcaller Cry'; 834 = 'Bloodboon Ritual'; 827 = 'Cursed-Blood Slice'
}
$manifestEntries = @(Get-ChildItem -LiteralPath (Join-Path $root 'unpacked-player') -Recurse -Filter 'manifest.json' |
    ForEach-Object { (Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json).Entries })
$byName = @{}
foreach ($entry in $manifestEntries) {
    $key = [IO.Path]::GetFileName($entry.Local).ToLowerInvariant()
    if (-not $byName.ContainsKey($key)) { $byName[$key] = @() }
    $byName[$key] += $entry
}
$skills = foreach ($id in $wanted.Keys | Sort-Object) {
    $row = @($reference | Where-Object ID -eq $id)
    if ($row.Count -ne 1 -or $row[0].'SKILL(S)' -ne $wanted[$id]) { throw "Unverified reference mapping for $id" }
    $path = Join-Path $root "unpacked-player/c0000-anibnd/a$id.tae"
    $tae = [SoulsFormats.TAE]::Read($path)
    $animations = foreach ($animation in $tae.Animations) {
        $events = foreach ($event in $animation.Events) {
            [pscustomobject]@{
                Type = $event.Type; StartSeconds = $event.StartTime; EndSeconds = $event.EndTime
                ParameterHex = [Convert]::ToHexString($event.GetParameterBytes($tae.BigEndian))
            }
        }
        $hkxName = [IO.Path]::ChangeExtension($animation.AnimFileName, '.hkx').ToLowerInvariant()
        $clips = @($byName[$hkxName] | Where-Object { $null -ne $_ } | Select-Object Local,Bytes,Sha256)
        [pscustomobject]@{
            EventAnimationId = $animation.ID; AuthoredFileName = $animation.AnimFileName
            HeaderType = $animation.MiniHeader.Type.ToString()
            ImportsHKX = $animation.MiniHeader.ImportsHKX
            ImportSourceId = $animation.MiniHeader.ImportHKXSourceAnimID
            Clips = $clips; Events = @($events)
            EventTimelineEndSeconds = ($animation.Events | Measure-Object EndTime -Maximum).Maximum
        }
    }
    [pscustomobject]@{
        Name = $wanted[$id]; TaeGroup = $id; TaeFile = $path; TaeSha256 = (Get-FileHash -LiteralPath $path).Hash
        Animations = @($animations)
    }
}
$index = [pscustomobject]@{
    GeneratedUtc = [DateTime]::UtcNow.ToString('o')
    MappingReference = 'https://docs.google.com/spreadsheets/d/1evpDLAfi1b3cYfGilDtMWXDvynwyN_lbX1chnSSR7Uk/edit?gid=1077055983'
    ReferenceSha256 = (Get-FileHash -LiteralPath (Join-Path $root 'skill-tae-reference.csv')).Hash
    Parser = 'SoulsFormats bundled with WitchyBND 3.0.1.0 (ReadyToRun disabled)'
    Status = 'TAE and HKX filename matching verified. Events are raw, not semantically decoded. No retargeting or MC integration.'
    Skills = @($skills)
}
$index | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $root 'skill-animation-index.json') -Encoding utf8
$skills | ForEach-Object { [pscustomobject]@{
    Name = $_.Name; Tae = $_.TaeGroup; EventAnimations = $_.Animations.Count
    SkillClips = @($_.Animations | Where-Object EventAnimationId -ge 40000 | ForEach-Object { $_.Clips.Local } | Sort-Object -Unique).Count
    UnresolvedNamedReferences = @($_.Animations | Where-Object { $_.AuthoredFileName -and $_.Clips.Count -eq 0 }).Count
} } | Format-Table -AutoSize
