param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$paths = @(& git -C $projectRoot -c core.quotepath=false ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read repository file list.' }
$paths = @($paths | Sort-Object -Unique | Where-Object {
    Test-Path -LiteralPath (Join-Path $projectRoot $_) -PathType Leaf
})

$groups = [ordered]@{
    '项目、构建与整合包' = @()
    '美术、模型与装备' = @()
    '战斗、特效与交接' = @()
    '其他审查与历史记录' = @()
}
foreach ($file in $paths) {
    if ($file -notmatch '^docs/[^/]+\.md$' -or $file -eq 'docs/FILE-CATALOG.md') { continue }
    $name = [IO.Path]::GetFileName($file)
    if ($name -match '^(README|PROJECT|GITHUB|REPOSITORY|BUILD|MODPACK|MCMOD|THIRD-PARTY|REUSE-FIRST|PROGRESS)') {
        $group = '项目、构建与整合包'
    } elseif ($name -match '(ART|MODEL|GRIP|WEAPON|EQUIPMENT|BLOCKBENCH|PIXEL|THICKNESS|ASSET|SIZE)') {
        $group = '美术、模型与装备'
    } elseif ($name -match '(COMBAT|VFX|SKILL|ASH|GRACE|TALISMAN|DSH|SLASH|SPELL|IMPACT|ANIMATION)') {
        $group = '战斗、特效与交接'
    } else { $group = '其他审查与历史记录' }
    $groups[$group] += $name
}

$lines = @(
    '# 项目文件分类索引',
    '',
    '由 `tools/repository/Write-FileCatalog.ps1` 生成。只列出仓库文件，不扫描缓存、运行实例或本机提取包。',
    '',
    '先读[文档入口](README.md)、[美术导航](../art/README.md)及[工具导航](../tools/README.md)。历史日期文档记录当时状态，最新结论须结合后续回执与当前文件。',
    '',
    '| 区域 | 文件数 |',
    '| --- | ---: |'
)
foreach ($area in @('src', 'art', 'tools', 'docs', 'gradle', 'modpack', 'modpack-defaults')) {
    $count = @($paths | Where-Object { $_.StartsWith($area + '/') }).Count
    $lines += "| ``$area/`` | $count |"
}
foreach ($group in $groups.Keys) {
    $lines += @('', "## $group", '')
    foreach ($name in $groups[$group]) {
        $lines += '- [' + $name + '](' + [Uri]::EscapeDataString($name) + ')'
    }
}
$destination = Join-Path $projectRoot 'docs/FILE-CATALOG.md'
[IO.File]::WriteAllText($destination, ($lines -join "`n") + "`n", (New-Object Text.UTF8Encoding($false)))
Write-Output "Updated docs/FILE-CATALOG.md from $($paths.Count) repository files."
