"""Verify the native Blockbench handoff and write its index and contact sheets."""
import hashlib
import json
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art/equipment-2d/V0.2/holds'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main():
    mapping = read(OUT / 'mapping.json')
    native = read(OUT / 'qa/native-validation.json')
    assert native['complete'] and native['checked'] == 38 and native['javaExports'] == 36
    assert native['preexistingTabsPreserved'] and native['screenshots'] == 144
    inputs = read(OUT / 'input-hashes.json')
    changed = [p for p, digest in inputs.items() if not (ROOT / p).is_file() or sha(ROOT / p) != digest]
    assert not changed, 'Original model or saved grip changed: ' + ', '.join(changed)
    assert {r['project'] for r in native['rows']} == {r['project'] for r in mapping['items']}
    assert len(list((OUT / 'projects').glob('*.bbmodel'))) == 38
    assert len(list((OUT / 'exports').glob('*.json'))) == 36
    images = defaultdict(list)
    bounds = []
    pivot_fields = []
    for row, evidence in zip(mapping['items'], native['rows']):
        assert row['project'] == evidence['project'] and evidence['geometryRoundtrip']
        project = read(OUT / row['project'])
        assert (OUT / evidence['nativeRoundtrip']).is_file()
        if row['kind'] == 'arm_mount':
            assert sha(OUT / row['project']) == row['modelSha256'] == row['gripSha256']
            assert evidence['armPivotsUnchanged']
            continue
        grip = read(ROOT / row['gripSource'])
        assert project['display'] == grip['display'], row['project'] + ': literal display changed'
        for slot, spec in project['display'].items():
            for field in ('rotation_pivot', 'scale_pivot', 'mirror'):
                if any(spec.get(field, [])):
                    pivot_fields.append([row['project'], slot, field, spec[field]])
        exported = read(OUT / evidence['javaExport'])
        assert 'hold_source' not in exported and 'hold_pairing' not in exported
        assert exported['textures']['layer0'] == row['textureResource']
        assert sha(OUT / row['texture']) == row['textureSha256']
        for cube in exported['elements']:
            bounds.extend(cube['from'] + cube['to'])
        assert len(evidence['screenshots']) == 4
        for shot in evidence['screenshots']:
            path = OUT / shot['file']
            with Image.open(path) as image:
                assert image.format == 'PNG' and image.size == (640, 480)
                image.verify()
            images[shot['context']].append((row, path))
    assert not pivot_fields, 'Additional display pivot/mirror semantics need explicit review: ' + repr(pivot_fields)
    assert min(bounds) >= -16 and max(bounds) <= 32, 'Java model coordinates outside supported bounds'
    font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 13)
    title_font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 22)
    for context, shots in images.items():
        assert len(shots) == 36
        page = Image.new('RGB', (1440, 1320), '#e5e7eb')
        draw = ImageDraw.Draw(page)
        draw.text((16, 10), 'V0.2 / ' + context + ' / saved grip values preserved', fill='#111827', font=title_font)
        for i, (row, path) in enumerate(shots):
            x, y = (i % 6) * 240 + 8, (i // 6) * 210 + 52
            with Image.open(path) as image:
                thumb = image.convert('RGBA')
                thumb.thumbnail((224, 168))
                page.paste(thumb, (x, y), thumb)
            draw.text((x, y + 171), row['id'], fill='#111827', font=font)
            draw.text((x, y + 188), row['state'], fill='#475569', font=font)
        page.save(OUT / ('qa/contact-' + context + '.png'))

    grouped = defaultdict(list)
    for row in mapping['items']:
        grouped[row['id']].append(row)
    selection = {'candidate': 'V0.2最新候选', 'original': 'V0.2对照页原版', 'dsh_worn': 'V0.2穿戴版'}
    lines = [
        '# V0.2 新模型与已保存握姿配对', '',
        '已完成28件装备、38份可编辑工程的配对，并通过Blockbench原生导入、贴图解码、几何/UV往返及导出检查。', '',
        '模型取自上一级V0.2选版包；握姿取自 `art/equipment-2d/blockbench-holds-20261022/` 磁盘文件。本次不读取或覆盖Blockbench里尚未保存的修改。', '',
        '## 打开和检查', '',
        '- `projects/`：实际配对后的38份Blockbench工程，直接打开这里继续编辑。',
        '- `textures/`：36张手持状态贴图，均与V0.2选定PNG哈希一致；工程内也已嵌入。',
        '- `exports/`：Blockbench原生导出的36份Java物品模型JSON，属于待接入资料，不是可直接替换整个游戏资源目录的安装包。',
        '- `mapping.json`：每个工程的新模型来源、握姿来源、状态、纹理资源和SHA256。',
        '- `input-hashes.json`：原始输入文件哈希快照；末次复查全部未变。',
        '- `validation.json` 与 `qa/native-validation.json`：静态与Blockbench原生检查结果。',
        '- `qa/native-roundtrip/`：38份原生编解码往返副本，仅供对照；继续编辑优先使用 `projects/`。',
        '- `qa/screenshots/`：36份手持工程的第一/第三人称左右手共144张原生预览。', '',
        '### 预览总览', '',
    ]
    for context in images:
        lines.append(f'- [{context}](qa/contact-{context}.png)')
    lines.extend([
        '', '## 保留规则', '',
        '手持工程逐项原样复制你保存的 `display` 旋转、位置、缩放和所有显示槽。新几何沿用原握姿的转换比例与握点坐标，不重新全局缩放、移动或翻转刀刃；旧握点在四个手持视角中的数学位置未变。各武器新版原生比例差异继续保留。', '',
        '大弓待机旧工程缺少转换比例字段，使用其明确记录的旧原生源工程恢复比例，没有按新版轮廓重新拟合；详情见 `mapping.json` 的 `factorSource`。', '',
        '弓的待机与三段拉弦、弩炮的待机/三段装填/装填完成、三种盾的正反面均分别配对。静态状态文件不代表已经完成连续动画或游戏条件路由接入。', '',
        '钩爪和拳套保留原生free工程、现有左右手几何及肩部枢轴（右[-5,22,0]、左[5,22,0]）；参考假人不导出，左手不重复镜像。两份配对工程与选定模型/已保存握姿逐字节相同。穿戴图集及16/32图标保留在上级 `textures/` 与 `icons/`。详见 [穿戴合同](../contracts/WEAR-CONTRACT.md)。', '',
        '## 对应表', '',
        '| 装备 | 模型选版 | 配对工程/状态 |', '|---|---|---|',
    ])
    for rows in grouped.values():
        links = '；'.join(f"[{r.get('state', '左右手穿戴')}]({r['project']})" for r in rows)
        lines.append(f"| {rows[0]['name']} (`{rows[0]['id']}`) | {selection[rows[0]['selection']]} | {links} |")
    lines.extend([
        '', '## 验证边界', '',
        f'复查{len(inputs)}个原始文件哈希一致；38份原生往返成功，36份Java导出成功，144张PNG有效；原有Blockbench标签页（包括未保存工程）均保留，检查后恢复原活动标签。', '',
        '本次交付是模型与握姿的配对文件及原生预览，未改正式资源、未构建或安装JAR、未启动游戏。Blockbench检查通过不代表握姿已获人类观感验收；游戏内视野遮挡、左右手、盾牌动作与弓弩路由仍需后续实际加载核对。', '',
    ])
    (OUT / 'README.md').write_text('\n'.join(lines), encoding='utf-8')
    validation = read(OUT / 'validation.json')
    validation.update(nativeCheckPending=False, nativeChecked=38, nativeJavaExports=36,
                      nativeScreenshots=144, originalFilesRechecked=len(inputs),
                      preexistingTabsPreserved=True, javaCoordinateMin=min(bounds),
                      javaCoordinateMax=max(bounds), humanVisualAccepted=False)
    write(OUT / 'validation.json', validation)
    outputs = {p.relative_to(OUT).as_posix(): sha(p) for p in OUT.rglob('*')
               if p.is_file() and p.name != 'output-hashes.json'}
    write(OUT / 'output-hashes.json', outputs)
    print(json.dumps(validation, ensure_ascii=False))


if __name__ == '__main__':
    main()
