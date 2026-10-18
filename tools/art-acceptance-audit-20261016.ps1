param(
    [string]$Root,
    [string]$JarPath,
    [string]$ProtectedManifest,
    [switch]$NegativeSamples,
    [switch]$SkipProtected
)
# 已选美术补验审计（只读）。
# R2：不再只看"整个 JAR 的 PNG 哈希集合"，而是按**当前正式路由/清单**推出每个目标 JAR 路径，
# 再逐路径比对内容哈希；缺失、内容不符、路径放错或保护文件变化都算失败并以非零退出。
# 用 -NegativeSamples 可在 build/ 下生成隔离的坏样本（缺资源/挪路径/换内容/保护清单改哈希），
# 要求它们全部被判失败，从而证明门禁不会假绿。本脚本不改任何正式素材或产物。
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not $Root) { $Root = Split-Path -Parent $PSScriptRoot }
if (-not $JarPath) { $JarPath = Join-Path $Root 'build\libs\eldencombat-0.2.0.jar' }
if (-not $ProtectedManifest) { $ProtectedManifest = Join-Path $Root 'docs\combat-evidence\art-acceptance-closure-20261016\protected-before.json' }

function Get-Sha([string]$path) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $fs = [System.IO.File]::OpenRead($path)
    try { return ([BitConverter]::ToString($sha.ComputeHash([System.IO.Stream]$fs)) -replace '-', '').ToLowerInvariant() }
    finally { $fs.Dispose(); $sha.Dispose() }
}
function Get-EntrySha($entry) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $s = $entry.Open()
    try { return ([BitConverter]::ToString($sha.ComputeHash([System.IO.Stream]$s)) -replace '-', '').ToLowerInvariant() }
    finally { $s.Dispose(); $sha.Dispose() }
}

# ---------------------------------------------------------------- 期望目标
# 目标 JAR 路径由 routes/2d.json（own_sprite 的 held/gui 状态）+ ui-pixel/assets.json 推出；
# 期望内容哈希取构建准入用的 publishable-assets.json（并对 equipment-42.json 做一致性交叉核对）。
$routes = Get-Content (Join-Path $Root 'src\main\resources\assets\eldencombat\routes\2d.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$whitelist = @{}
foreach ($w in @((Get-Content (Join-Path $Root 'art\equipment-2d\full\publishable-assets.json') -Raw -Encoding UTF8 | ConvertFrom-Json).include)) {
    $whitelist["$($w.file)"] = "$($w.sha256)".ToLowerInvariant()
}
$manifestHashes = @{}
$eq42 = Get-Content (Join-Path $Root 'art\equipment-2d\full\equipment-42.json') -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($a in @($eq42.assets)) {
    foreach ($g in @($a.variants, $a.gui)) {
        foreach ($v in @($g)) {
            if ($null -eq $v -or -not $v.file) { continue }
            if ("$($v.file)" -notlike 'sprites/*') { continue }
            $manifestHashes["$($v.file)"] = "$($v.sha256)".ToLowerInvariant()
        }
    }
}

$targets = New-Object System.Collections.Generic.List[object]
foreach ($e in @($routes.equipment)) {
    if ($e.route -ne 'own_sprite' -or $e.status -ne 'ready') { continue }
    $specs = New-Object System.Collections.Generic.List[object]
    if ($e.held) { foreach ($p in $e.held.PSObject.Properties) { $specs.Add([pscustomobject]@{ name = "$($e.assetId)_$($p.Name)"; rel = "$($p.Value)" }) } }
    if ($e.gui) { foreach ($p in $e.gui.PSObject.Properties) { $specs.Add([pscustomobject]@{ name = "$($e.assetId)_gui$($p.Name)"; rel = "$($p.Value)" }) } }
    foreach ($s in $specs) {
        $targets.Add([pscustomobject]@{
                kind   = 'equipment'
                owner  = "$($e.id)"
                jar    = "assets/eldencombat/textures/item/2d/$($s.name).png"
                source = Join-Path $Root ("art/equipment-2d/full/" + $s.rel)
                rel    = $s.rel
                sha    = if ($whitelist.ContainsKey($s.rel)) { $whitelist[$s.rel] } else { $null }
            })
    }
}
$uiAssets = @((Get-Content (Join-Path $Root 'art\ui-pixel\assets.json') -Raw -Encoding UTF8 | ConvertFrom-Json).assets)
foreach ($a in $uiAssets) {
    $leaf = Split-Path "$($a.file)" -Leaf
    $targets.Add([pscustomobject]@{
            kind   = 'ui'
            owner  = "$($a.id)"
            jar    = 'assets/eldencombat/textures/ui/' + $leaf.Replace('_16.png', '.png')
            source = Join-Path $Root ("art/ui-pixel/" + "$($a.file)")
            rel    = "art/ui-pixel/$($a.file)"
            sha    = "$($a.sha256)".ToLowerInvariant()
        })
}

# ---------------------------------------------------------------- 检查实现
function Invoke-Audit([string]$jarPath, [string]$protectedManifest, [bool]$includeProtected) {
    $problems = New-Object System.Collections.Generic.List[string]
    $stats = [ordered]@{
        diskMissing = 0; diskMismatch = 0; jarMissing = 0; jarMismatch = 0; jarUnexpected = 0
        protectedFiles = 0; protectedChanged = 0; protectedMissing = 0
    }
    # 期望哈希本身的完整性（缺清单/清单内互相矛盾，一样是失败）
    foreach ($t in $targets) {
        if (-not $t.sha) { $problems.Add("no admitted hash for $($t.rel) ($($t.owner))") }
        if ($t.kind -eq 'equipment' -and $manifestHashes.ContainsKey($t.rel) -and $manifestHashes[$t.rel] -ne $t.sha) {
            $problems.Add("manifest/whitelist hash disagree for $($t.rel)")
        }
    }
    # 1) 磁盘：源 PNG 必须存在且等于期望哈希
    foreach ($t in $targets) {
        if (-not (Test-Path -LiteralPath $t.source)) { $stats.diskMissing++; $problems.Add("missing source PNG: $($t.rel)"); continue }
        if ((Get-Sha $t.source) -ne $t.sha) { $stats.diskMismatch++; $problems.Add("source PNG differs from manifest: $($t.rel)") }
    }
    # 2) JAR：逐目标路径比对内容（不是哈希集合命中）
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $jarPath).ProviderPath)
    try {
        $byPath = @{}
        foreach ($entry in $zip.Entries) { $byPath[$entry.FullName] = $entry }
        $expected2d = @{}
        foreach ($t in $targets) {
            if ($t.kind -eq 'equipment') { $expected2d[$t.jar] = $t }
            if (-not $byPath.ContainsKey($t.jar)) { $stats.jarMissing++; $problems.Add("missing in jar: $($t.jar)"); continue }
            if ((Get-EntrySha $byPath[$t.jar]) -ne $t.sha) { $stats.jarMismatch++; $problems.Add("jar content differs: $($t.jar)") }
        }
        # 3) 2D 纹理命名空间里不允许出现目标以外的 PNG（挪路径/残留会在这里暴露）
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -notlike 'assets/eldencombat/textures/item/2d/*.png') { continue }
            if ($expected2d.ContainsKey($entry.FullName)) { continue }
            $stats.jarUnexpected++
            $problems.Add("unexpected texture in jar 2d namespace: $($entry.FullName)")
        }
    }
    finally { $zip.Dispose() }
    # 4) 保护文件
    if ($includeProtected) {
        $protected = Get-Content $protectedManifest -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($p in $protected.PSObject.Properties) {
            $stats.protectedFiles++
            $path = Join-Path $Root $p.Name
            if (-not (Test-Path -LiteralPath $path)) { $stats.protectedMissing++; $problems.Add("protected file missing: $($p.Name)"); continue }
            if ((Get-Sha $path) -ne ([string]$p.Value).ToLowerInvariant()) { $stats.protectedChanged++; $problems.Add("protected file changed: $($p.Name)") }
        }
    }
    return [pscustomobject]@{ problems = $problems; stats = $stats }
}

function Show-Result([string]$label, $result) {
    $s = $result.stats
    $equip = @($targets | Where-Object kind -eq 'equipment').Count
    $ui = @($targets | Where-Object kind -eq 'ui').Count
    "label                    = $label"
    "equipmentTargets         = $equip"
    "uiTargets                = $ui"
    "expectedTargets          = $($targets.Count)"
    "diskMissing/Mismatch     = $($s.diskMissing)/$($s.diskMismatch)"
    "jarMissing/Mismatch      = $($s.jarMissing)/$($s.jarMismatch)"
    "jarUnexpected2dPng       = $($s.jarUnexpected)"
    "protectedFiles           = $($s.protectedFiles)"
    "protectedChanged/Missing = $($s.protectedChanged)/$($s.protectedMissing)"
    "problems                 = $($result.problems.Count)"
    foreach ($p in $result.problems) { "  FAIL: $p" }
    if ($result.problems.Count -eq 0) { "RESULT                   = PASS" } else { "RESULT                   = FAIL" }
}

# ---------------------------------------------------------------- 正式包
$real = Invoke-Audit -jarPath $JarPath -protectedManifest $ProtectedManifest -includeProtected (-not $SkipProtected)
"jarPath                  = $JarPath"
"jarSha256                = $(Get-Sha $JarPath)"
Show-Result -label 'real-package' -result $real
$failed = $real.problems.Count -gt 0

# ---------------------------------------------------------------- 隔离坏样本
if ($NegativeSamples) {
    $negDir = Join-Path $Root 'build\art-acceptance-audit-negative'
    New-Item -ItemType Directory -Force -Path $negDir | Out-Null
    $victim = @($targets | Where-Object kind -eq 'equipment')[0]
    $other = @($targets | Where-Object kind -eq 'equipment')[1]
    $uiVictim = @($targets | Where-Object kind -eq 'ui')[0]
    $samples = @(
        @{ name = 'missing-resource'; build = {
                $z = [System.IO.Compression.ZipFile]::Open($dst, 'Update')
                $z.GetEntry($victim.jar).Delete(); $z.Dispose()
            } },
        @{ name = 'wrong-target-path'; build = {
                $z = [System.IO.Compression.ZipFile]::Open($dst, 'Update')
                $src = $z.GetEntry($victim.jar)
                $ms = New-Object System.IO.MemoryStream
                $in = $src.Open(); $in.CopyTo($ms); $in.Dispose()
                $new = $z.CreateEntry('assets/eldencombat/textures/item/2d/zz_moved_wrong_path.png')
                $out = $new.Open(); $ms.Position = 0; $ms.CopyTo($out); $out.Dispose(); $ms.Dispose()
                $z.GetEntry($victim.jar).Delete(); $z.Dispose()
            } },
        @{ name = 'swapped-content'; build = {
                $z = [System.IO.Compression.ZipFile]::Open($dst, 'Update')
                $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $other.source).ProviderPath)
                $entry = $z.GetEntry($victim.jar)
                $out = $entry.Open(); $out.SetLength(0); $out.Write($bytes, 0, $bytes.Length); $out.Dispose()
                $z.Dispose()
            } },
        @{ name = 'missing-ui-icon'; build = {
                # UI 目标同样逐路径校验：删掉一张 UI 图标（这里是 8 条期望中的第一条）也必须失败
                $z = [System.IO.Compression.ZipFile]::Open($dst, 'Update')
                $z.GetEntry($uiVictim.jar).Delete(); $z.Dispose()
            } }
    )
    foreach ($sample in $samples) {
        $dst = Join-Path $negDir ($sample.name + '.jar')
        Copy-Item -LiteralPath $JarPath -Destination $dst -Force
        $victim = @($targets | Where-Object kind -eq 'equipment')[0]
        & $sample.build
        $neg = Invoke-Audit -jarPath $dst -protectedManifest $ProtectedManifest -includeProtected $false
        Show-Result -label ("negative:" + $sample.name) -result $neg
        if ($neg.problems.Count -eq 0) {
            "  GATE FAILURE: negative sample '$($sample.name)' was reported PASS"
            $failed = $true
        }
    }
    # 保护清单被改一条哈希也必须失败
    $badProtected = Join-Path $negDir 'protected-tampered.json'
    $text = (Get-Content $ProtectedManifest -Raw -Encoding UTF8) -replace '"([0-9a-f]{64})"', '"ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"'
    [System.IO.File]::WriteAllText($badProtected, $text, (New-Object System.Text.UTF8Encoding($false)))
    $neg = Invoke-Audit -jarPath $JarPath -protectedManifest $badProtected -includeProtected $true
    Show-Result -label 'negative:protected-manifest-tampered' -result $neg
    if ($neg.problems.Count -eq 0) { "  GATE FAILURE: tampered protected manifest was reported PASS"; $failed = $true }
}

if ($failed) { "OVERALL                  = FAIL"; exit 1 }
"OVERALL                  = PASS"
exit 0
