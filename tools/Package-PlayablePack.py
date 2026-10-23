"""Build a complete personal-use pack from the reviewed lock and installed files.

Does not rebuild art, launch Java, copy accounts/worlds, or promote runtime acceptance.
"""
from pathlib import Path
from io import BytesIO
import hashlib
import json
import re
import shutil
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('D:/PCL/.minecraft/versions/Elden Combat 0.2.0 - Vanilla Combat - 副本')
WORK = ROOT / 'build/pack-release-20261023'
OUT = ROOT / 'dist/playable-pack-20261023'
STAGE = WORK / 'client'
NAME = 'Elden Combat 0.2.0 - 20261023'
LOCK = ROOT / 'modpack/candidates/grace-talismans-20261019.lock.json'
JAR = ROOT / 'dist/hud-v02-20261023/mods/eldencombat-0.2.0.jar'


def digest(data, algorithm='sha256'):
    return hashlib.new(algorithm, data).hexdigest()


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def audit_mod(raw, filename, providers, requirements, depth=0):
    assert depth < 6, filename
    with zipfile.ZipFile(BytesIO(raw)) as archive:
        assert archive.testzip() is None, filename
        metadata = 'META-INF/neoforge.mods.toml'
        if metadata in archive.namelist():
            doc = tomllib.loads(archive.read(metadata).decode('utf-8-sig'))
            for mod in doc.get('mods', []):
                modid = mod['modId']
                candidate = {'file': filename, 'version': mod.get('version')}
                if modid in providers:
                    assert depth > 0 and '!' in providers[modid]['file'], f'duplicate top-level modId {modid}'
                    providers[modid].setdefault('nestedCandidates', []).append(candidate)
                else:
                    providers[modid] = candidate
            for owner, dependencies in doc.get('dependencies', {}).items():
                for dep in dependencies:
                    if dep.get('type') == 'required' or dep.get('mandatory'):
                        requirements.append(dict(owner=owner, file=filename, **dep))
        if 'META-INF/jarjar/metadata.json' in archive.namelist():
            nested = json.loads(archive.read('META-INF/jarjar/metadata.json'))
            for entry in nested['jars']:
                audit_mod(archive.read(entry['path']), filename + '!' + entry['path'],
                          providers, requirements, depth + 1)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    lock = json.loads(LOCK.read_text(encoding='utf-8-sig'))
    assert (lock['minecraft'], lock['neoforge']) == ('1.21.1', '21.1.219')
    assert len(lock['mods']) == 38
    assert len({m['path'] for m in lock['mods']}) == 38
    payload = {}
    mod_rows = []
    for mod in lock['mods']:
        assert mod['installApproved'] and re.fullmatch(r'mods/[\w.+-]+\.jar', mod['path'])
        raw = (SOURCE / mod['path']).read_bytes()
        assert len(raw) == mod['fileSize'], mod['name']
        for algorithm, expected in mod['hashes'].items():
            assert digest(raw, algorithm) == expected, mod['name']
        payload[mod['path']] = raw
        mod_rows.append(dict(path=mod['path'], name=mod['name'], sha256=digest(raw),
                             sha512=digest(raw, 'sha512'), bytes=len(raw), env=mod['env']))
    raw = JAR.read_bytes()
    hud = json.loads((ROOT / 'dist/hud-v02-20261023/verification.json').read_text(encoding='utf-8'))
    assert digest(raw) == hud['jarSha256']
    with zipfile.ZipFile(BytesIO(raw)) as archive:
        assert archive.testzip() is None
        assert not any('Harness' in n for n in archive.namelist())
        assert len([n for n in archive.namelist() if '/textures/gui/hud_v02/' in n and n.endswith('.png')]) == 7
    payload['mods/eldencombat-0.2.0.jar'] = raw
    mod_rows.append(dict(path='mods/eldencombat-0.2.0.jar', name='Elden Combat',
                         sha256=digest(raw), sha512=digest(raw, 'sha512'), bytes=len(raw),
                         env={'client': 'required', 'server': 'required'}))

    dependency_audit = {}
    for side in ('client', 'server'):
        providers = {'minecraft': {'version': '1.21.1'}, 'neoforge': {'version': '21.1.219'}}
        requirements = []
        selected = [r for r in mod_rows if r['env'][side] == 'required']
        for row in selected:
            audit_mod(payload[row['path']], row['path'], providers, requirements)
        missing = [d for d in requirements if d.get('side', 'BOTH') in ('BOTH', side.upper())
                   and d['modId'] not in providers]
        for owner, target, reason in lock['extraDependencyEdges']:
            if owner in providers and target not in providers:
                missing.append(dict(owner=owner, modId=target, reason=reason))
        assert not missing, missing
        dependency_audit[side] = dict(jars=len(selected), providers=providers,
                                    requiredDependencies=requirements, missing=missing,
                                    versionRanges='Recorded from JARs; exact reviewed versions retained, not a new runtime resolution')

    # Capture configuration only, never machine fingerprints, maps, worlds or accounts.
    for path in sorted((SOURCE / 'config').rglob('*')):
        if path.is_file() and path.suffix.lower() in ('.toml', '.json', '.json5', '.properties'):
            if path.name == 'sodium-fingerprint.json':
                continue
            payload[path.relative_to(SOURCE).as_posix()] = path.read_bytes()
    for relative in ('config/eldencombat-combat.json', 'config/eldencombat-grace.json'):
        payload[relative] = (ROOT / 'modpack/overrides' / relative).read_bytes()
    for relative in ('config/entityculling.json', 'config/combat_roll/hud_config.json'):
        payload[relative] = (ROOT / 'modpack/client-overrides' / relative).read_bytes()
    memory = json.loads(payload['config/memorysweep.json'])
    memory['memorySweep'] = False  # Keep installed; no measured benefit yet.
    payload['config/memorysweep.json'] = encoded(memory)
    iris = payload['config/iris.properties'].decode('utf-8-sig')
    payload['config/iris.properties'] = re.sub(r'enableShaders=true', 'enableShaders=false', iris).encode('utf-8')
    # Portable defaults; preserve native movement/inventory bindings, remove global R/C/G conflicts.
    options = ['version:3955', 'lang:zh_cn', 'autoJump:false', 'fullscreen:false',
               'renderDistance:8', 'simulationDistance:5', 'maxFps:60', 'resourcePacks:[]']
    keys = dict(line.split(':', 1) for line in (SOURCE / 'options.txt').read_text(encoding='utf-8').splitlines()
                if line.startswith('key_'))
    for key in ('key_iris.keybind.reload', 'key_key.simplytooltips.cycle_tab',
                'key_keybind.sophisticatedbackpacks.inventory_interaction',
                'key_key.saveToolbarActivator', 'key_key.loadToolbarActivator'):
        keys[key] = 'key.keyboard.unknown'
    keys['key_keybinds.combat_roll.roll'] = 'key.keyboard.left.alt'
    keys['key_key.eldencombat.toggle_combat'] = 'key.keyboard.r'
    options.extend(f'{key}:{value}' for key, value in keys.items())
    payload['options.txt'] = ('\n'.join(options) + '\n').encode('utf-8')
    payload['exact-mods.lock.json'] = LOCK.read_bytes()
    sources = '# 本机自用完整包来源\n\n保留作者原始 JAR；未公开上传。游戏本体、启动器和 Java 不在包内。\n\n'
    for mod in lock['mods']:
        source = mod['source'] if 'source' in mod else f"https://modrinth.com/mod/{mod['project']}/version/{mod['versionId']}"
        sources += f"- {mod['name']} {mod['version']}: {source}\n"
    payload['THIRD-PARTY.md'] = sources.encode('utf-8')
    readme = f'''艾尔登战斗整合包 · 2026-10-23

Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21
38 个第三方模组 + Elden Combat，共 39 个 JAR，已全部内置，无手动补装项。

本机：PCL 中选择“{NAME}”。已复制“副本”实例的存档供继续游玩，原存档保持原样。
换电脑：将 .mrpack 拖入支持 Modrinth 的启动器，安装到新实例。模组无需再下载；
Minecraft、NeoForge、Java 和游戏资源仍由启动器从官方来源安装。请使用自己的游戏账号。
导入包不包含私人存档；本机存档复制与安装回执记录在交付目录。

R 战斗模式；左 Alt 翻滚；G 重击；C 战技；X 弹反；V 喝药；鼠标中键锁定；B 背包。
生存模式显示新版蓝条和耐力；创造模式 R 开启后显示。经验条保留真实经验。
赐福基于 Waystones，四件现有护符效果已包含在项目 JAR 中。

默认 8 区块视距、5 区块模拟、60 FPS、光影关闭。MemorySweep 保留安装，默认关闭定时清扫。
本机新实例最大堆内存 3 GiB。启动前可运行“启动前检查.cmd”，不足时先关闭不用的大程序。
该检查不负责启动游戏；PCL 按钮本身不会执行内存门槛。

边界：完整文件及依赖存在检查通过，本次未启动游戏，未声称完成全包游玩/多人/性能验收。
JourneyMap 尚未加入当前 38 项组合，因此赐福地图快捷入口仍不可用；可正常使用传送石菜单。
LazyDFU Forge 无 1.21.1 兼容文件。V0.2 新武器/握姿和动作编辑工程尚未作为运行资源接入，
本包保留已接入 JAR 的武器；不把 Blockbench 工程直接冒充游戏模型。
不要用这个自用归档覆盖旧实例。多人服务器须同步项目 JAR 与 common 模组版本。
'''
    payload['README-先读我.txt'] = readme.encode('utf-8')
    payload['Assert-TestBudget.ps1'] = (ROOT / 'modpack/Assert-TestBudget.ps1').read_bytes()
    payload['启动前检查.cmd'] = ('@echo off\r\npowershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Assert-TestBudget.ps1" -Mode client\r\npause\r\n').encode('utf-8')
    receipt = dict(name=NAME, version='0.2.0-20261023', minecraft='1.21.1', neoforge='21.1.219',
                   java=21, filesComplete=True, manualFiles=[], runtimeAccepted=False, gameStarted=False,
                   jarSha256=digest(raw), mods=mod_rows, lockSha256=digest(LOCK.read_bytes()),
                   dependencyAudit=dependency_audit,
                   sourceInstance=str(SOURCE), packageUse='local personal backup/install; not published',
                   authoringAssetsInstalled=False, mapModInstalled=False,
                   defaultChanges=['R Iris reload unbound', 'C backpack inventory action unbound',
                                   'G tooltip cycle unbound', 'creative toolbar activators unbound',
                                   'MemorySweep disabled', 'shaders disabled', '8/5 chunks; 60 FPS'])
    payload['eldencombat-pack.json'] = encoded(receipt)
    # Exact list is the install allowlist; the installer never recursively copies arbitrary input.
    inventory = {path: dict(sha256=digest(data), bytes=len(data)) for path, data in payload.items()}
    for relative, data in payload.items():
        path = STAGE / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    assert {p.relative_to(STAGE).as_posix() for p in STAGE.rglob('*') if p.is_file()} == set(payload)
    (OUT / 'INSTALL-FILES.json').write_bytes(encoded(inventory))
    (OUT / 'PACKAGE-AUDIT.json').write_bytes(encoded(receipt))
    (OUT / 'README-先读我.txt').write_text(readme, encoding='utf-8')
    index = dict(formatVersion=1, game='minecraft', versionId='0.2.0-20261023', name=NAME,
                 summary='Complete personal-use files; MC/loader installed by launcher; gameplay recheck pending',
                 files=[], dependencies={'minecraft': '1.21.1', 'neoforge': '21.1.219'})
    pack = OUT / 'EldenCombat-0.2.0-20261023-full.mrpack'
    with zipfile.ZipFile(pack.with_suffix('.partial'), 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        archive.writestr('modrinth.index.json', encoded(index))
        for relative, data in payload.items():
            if relative.startswith('mods/'):
                row = next(row for row in mod_rows if row['path'] == relative)
                folder = 'overrides' if row['env']['server'] == 'required' else 'client-overrides'
            else:
                folder = 'client-overrides'
            archive.writestr(f'{folder}/{relative}', data)
    pack.with_suffix('.partial').replace(pack)
    with zipfile.ZipFile(pack) as archive:
        assert archive.testzip() is None
        expected = {}
        for name in archive.namelist():
            if name.startswith(('overrides/', 'client-overrides/')):
                relative = name.split('/', 1)[1]
                assert relative not in expected
                expected[relative] = digest(archive.read(name))
        assert expected == {k: v['sha256'] for k, v in inventory.items()}
    shutil.copyfile(pack, OUT / 'EldenCombat-0.2.0-20261023-full-PCL.zip')
    (OUT / 'SHA256SUMS.txt').write_text(''.join(
        digest(p.read_bytes()) + '  ' + p.name + '\n' for p in sorted(OUT.iterdir())
        if p.suffix in ('.zip', '.mrpack')), encoding='utf-8')
    print(json.dumps(dict(jars=len(mod_rows), serverJars=dependency_audit['server']['jars'],
                          payloadFiles=len(payload), package=str(pack), jarSha256=digest(raw)), ensure_ascii=False))


if __name__ == '__main__':
    main()
