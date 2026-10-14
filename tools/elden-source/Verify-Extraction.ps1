$ErrorActionPreference = 'Stop'
$root = 'D:/codexout/mcmod/build/elden-source'
$receiptPath = Join-Path $root 'extracted-player/extraction-receipt.json'
$receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
$exe = Get-FileHash -LiteralPath (Join-Path $receipt.Game 'eldenring.exe') -Algorithm SHA256
if ($exe.Hash -ne $receipt.SourceExeSha256) { throw 'Source executable no longer matches extraction receipt.' }
foreach ($header in $receipt.SourceHeaders) {
    if ((Get-FileHash -LiteralPath $header.Path -Algorithm SHA256).Hash -ne $header.Hash) {
        throw "Source header no longer matches: $($header.Path)"
    }
}
$files = @(Get-ChildItem -LiteralPath (Join-Path $root 'extracted-player/chr') -Filter '*.dcx' -File)
$entries = @(Get-ChildItem -LiteralPath (Join-Path $root 'unpacked-player') -Recurse -Filter 'manifest.json' |
    ForEach-Object { (Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json).Entries })
$decode = Get-Content -LiteralPath (Join-Path $root 'decoded-skills/decode-report.json') -Raw | ConvertFrom-Json
$report = [pscustomobject]@{
    VerifiedUtc = [DateTime]::UtcNow.ToString('o'); Source = $receipt.Game
    SourceExeSha256 = $exe.Hash; ExecutableAndHeadersMatchExtraction = $true
    SourceBodyArchivesFullyHashed = $false; ExtractionReceipt = $receiptPath
    BinderCount = $files.Count; BinderBytes = ($files | Measure-Object Length -Sum).Sum
    Binders = @($files | Get-FileHash -Algorithm SHA256)
    EntryCount = $entries.Count; EntryBytes = ($entries | Measure-Object Bytes -Sum).Sum
    Extensions = @($entries | Group-Object { [IO.Path]::GetExtension($_.Local).ToLowerInvariant() } | Select-Object Name,Count)
    SkillGroupCount = @($decode.Clips.TaeGroup | Sort-Object -Unique).Count
    DecodedClipCount = $decode.DecodedClipCount; DecodedFrameCount = ($decode.Clips | Measure-Object Frames -Sum).Sum
    Status = 'Read-only extraction and raw-track decoding complete; Epic Fight retargeting and in-game acceptance pending.'
}
$report | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $root 'verified-inventory.json') -Encoding utf8
$report | Select-Object VerifiedUtc,ExecutableAndHeadersMatchExtraction,BinderCount,BinderBytes,EntryCount,EntryBytes,SkillGroupCount,DecodedClipCount,DecodedFrameCount | Format-List
