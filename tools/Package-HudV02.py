"""Package the HUD-only update and audit it against the enabled PCL instance."""
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'dist/hud-v02-20261023'
ART = ROOT / 'art/ui-pixel/hud-v02-20261023'
BASE = Path('D:/PCL/.minecraft/versions/Elden Combat 0.2.0 - Vanilla Combat - 副本/mods/eldencombat-0.2.0.jar')
JAR = ROOT / 'build/libs/eldencombat-0.2.0.jar'
NAMES = ['fp_bar_background', 'fp_bar_fill', 'stamina_bar_background', 'stamina_bar_fill',
         'boss_bar_background', 'boss_bar_fill', 'low_resource']


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    test = ET.parse(ROOT / 'build/test-results/test/TEST-com.dsh.eldencombat.CombatHudLayoutTest.xml').getroot()
    assert test.get('tests') == '4' and test.get('failures') == test.get('errors') == '0'
    manifest = json.loads((ART / 'manifest.json').read_text(encoding='utf-8'))
    baseline_input = BASE
    previous_receipt = OUT / 'verification.json'
    if previous_receipt.is_file():
        previous = json.loads(previous_receipt.read_text(encoding='utf-8'))
        if sha(BASE.read_bytes()) != previous['baselineSha256']:
            backup = ROOT / 'dist/hud-v02-20261023-install-backup/eldencombat-0.2.0.before-hud-v02.jar'
            assert sha(BASE.read_bytes()) == sha(JAR.read_bytes())
            assert backup.is_file() and sha(backup.read_bytes()) == previous['baselineSha256']
            baseline_input = backup
    allowed = {'com/dsh/eldencombat/client/CombatHud.class', 'com/dsh/eldencombat/client/CombatHudLayout.class',
               'assets/eldencombat/lang/zh_cn.json', 'assets/eldencombat/lang/en_us.json'}
    textures = []
    with zipfile.ZipFile(JAR) as jar, zipfile.ZipFile(baseline_input) as base:
        assert not any('Harness' in name for name in jar.namelist())
        hashes = lambda z: {n: sha(z.read(n)) for n in z.namelist() if not n.endswith('/')}
        old, new = hashes(base), hashes(jar)
        for name in NAMES:
            row = next(a for a in manifest['assets'] if a['id'] == name)
            resource = 'assets/eldencombat/textures/gui/hud_v02/' + name + '.png'
            allowed.add(resource)
            assert sha(jar.read(resource)) == row['sha256'] == sha((ART / row['file']).read_bytes())
            textures.append({'resource': resource, 'sha256': row['sha256'], 'width': row['width'], 'height': row['height']})
        added, removed = sorted(new.keys() - old.keys()), sorted(old.keys() - new.keys())
        changed = [n for n in sorted(new.keys() & old.keys()) if new[n] != old[n]]
        assert not removed and set(added + changed) <= allowed, 'Change outside the HUD update'
        language_changes = {}
        for language in ['zh_cn', 'en_us']:
            name = 'assets/eldencombat/lang/' + language + '.json'
            a, b = json.loads(base.read(name)), json.loads(jar.read(name))
            keys = [k for k in sorted(a.keys() | b.keys()) if a.get(k) != b.get(k)]
            assert all(k.startswith('hud.eldencombat.') for k in keys)
            language_changes[language] = keys
    # Verify that authoring selections and the art handoff stayed untouched.
    protection = json.loads((ART / 'protection.json').read_text(encoding='utf-8'))
    protected = []
    expected_ui_sources = {'src/main/java/com/dsh/eldencombat/client/CombatHud.java',
                           'src/main/java/com/dsh/eldencombat/client/CombatHudLayout.java'}
    expected_source_changes = []
    for entry in protection:
        file = ROOT / entry['file']
        if not file.is_file() or sha(file.read_bytes()) != entry['sha256']:
            if entry['file'] in expected_ui_sources and file.is_file():
                expected_source_changes.append(entry['file'])
            else:
                protected.append(entry['file'])
    assert not protected, 'Protected art changed: ' + repr(protected)
    (OUT / 'mods').mkdir(exist_ok=True)
    shutil.copyfile(JAR, OUT / 'mods/eldencombat-0.2.0.jar')
    result = {'date': '2026-10-23', 'jarSha256': sha(JAR.read_bytes()), 'baseline': str(BASE),
              'baselineSha256': sha(baseline_input.read_bytes()), 'added': added, 'changed': changed, 'removed': removed,
              'languageChanges': language_changes, 'textures': textures,
              'protectedArtFilesUnchanged': len(protection) - len(expected_source_changes),
              'authorizedHudSourceChanges': expected_source_changes,
              'layoutTests': {'tests': 4, 'failures': 0, 'errors': 0}, 'harnessEntries': 0,
              'installationReceipt': 'installation.json (separate from the manual update archive)',
              'gameLaunched': False, 'inGameVisualAcceptance': False,
              'mapBackendAdded': False, 'statusEffectsBackendAdded': False}
    (OUT / 'verification.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (OUT / 'README-先读我.txt').write_text(
        'Elden Combat HUD V0.2 · 2026-10-23\nMinecraft 1.21.1 / NeoForge 21.1.219 / Java 21\n\n'
        '生存模式资源条常驻；创造模式按R开启战斗模式后显示。蓝条在左侧护甲上方，金色耐力在右侧。\n'
        '经验条和等级恢复；潜水/氧气恢复期间右侧只显示原版氧气。多排生命、坐骑生命继续沿用原版状态栈。\n'
        '物品名保留原倒计时与字体并上移避让；Boss用美术贴图与真实同步名称/进度，只画一套。\n'
        '左上继续是现有赐福地图按键入口；未安装JourneyMap时提示未启用，不使用演示地图、敌人点或虚构状态图标。\n\n'
        '手动更新：退出游戏，将旧 eldencombat JAR 备份到mods目录外，再用mods/中的同名JAR替换，不要同时保留两份。\n'
        '这是单模组更新包，不是PCL整包导入文件。源代码、检查回执和安装备份均保留在项目内。\n\n'
        '已通过4项布局检查、编译和包内无Harness检查；尚未启动游戏，实际画面与第三方HUD遮挡仍需游戏中确认。\n', encoding='utf-8')
    (OUT / 'SHA256SUMS.txt').write_text(result['jarSha256'] + '  mods/eldencombat-0.2.0.jar\n', encoding='utf-8')
    archive = OUT / 'EldenCombat-HUD-V0.2-20261023.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for name in ['mods/eldencombat-0.2.0.jar', 'README-先读我.txt', 'SHA256SUMS.txt', 'verification.json']:
            z.write(OUT / name, name)
    (OUT / 'PACKAGE-SHA256.txt').write_text(sha(archive.read_bytes()) + '  ' + archive.name + '\n', encoding='utf-8')
    print(json.dumps({k: result[k] for k in ['jarSha256', 'added', 'changed', 'protectedArtFilesUnchanged', 'layoutTests']}, ensure_ascii=False))


if __name__ == '__main__':
    main()
