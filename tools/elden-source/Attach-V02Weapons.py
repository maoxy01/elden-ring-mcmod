"""Add the selected V0.2 weapons to source-motion studies without changing motion."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'build/elden-source/tools/python-havok'))
import numpy as np
from scipy.spatial.transform import Rotation

SOURCE = ROOT / 'local-research/elden-source-motion-20261021-arm70'
OUT = ROOT / 'local-research/elden-source-motion-20261021-arm70-v02'
HOLDS = ROOT / 'art/equipment-2d/V0.2/holds'
spec = importlib.util.spec_from_file_location('display_math', ROOT / 'tools/equipment2d/build-display-candidates.py')
math = importlib.util.module_from_spec(spec)
spec.loader.exec_module(math)


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ident(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'elden-v02-motion-weapon:' + name))


def main():
    assert not OUT.exists(), 'Preserve an existing editable V0.2 study rather than overwrite it'
    pairs = read(HOLDS / 'mapping.json')['items']
    skills = read(ROOT / 'src/main/resources/data/eldencombat/dsh_skills/remembrance_parameters.json')
    plans, rows, hashes = [], [], {}
    for skill in skills:
        source_path = SOURCE / 'projects' / (skill['id'] + '.bbmodel')
        model = read(source_path)
        row = next(r for r in pairs if r['id'] == skill['itemId'] and r['kind'] == 'held')
        hold_path = HOLDS / row['project']
        hold = read(hold_path)
        hashes[source_path.relative_to(ROOT).as_posix()] = sha(source_path)
        hashes[hold_path.relative_to(ROOT).as_posix()] = sha(hold_path)
        original_animations = copy.deepcopy(model['animations'])
        original_elements = copy.deepcopy(model['elements'])
        arm = next(g for g in model['groups'] if g['name'] == 'MC/rightArm')
        d = hold['display']['thirdperson_righthand']
        frame = math.raw_pose(d, False)
        frame[:3, 3] *= 16
        m = np.diag([-1., -1., 1., 1.]) @ math.rotation_xyz(-90, 0, 0) @ math.rotation_xyz(0, 180, 0) @ math.translation([1, 2, -10]) @ frame @ math.translation([-8, -8, -8])
        scale = np.linalg.norm(m[:3, :3], axis=0)
        rotation = m[:3, :3] / scale
        assert np.linalg.det(rotation) > .99999
        angles = Rotation.from_matrix(rotation).as_euler('ZYX', degrees=True)[::-1]
        assert np.max(np.abs(Rotation.from_euler('ZYX', angles[::-1], degrees=True).as_matrix() - rotation)) < 1e-8
        origin = np.array(arm['origin']) + m[:3, 3]
        group_id = ident(skill['id'] + '/weapon')
        cube_ids = []
        texture = copy.deepcopy(hold['textures'][0])
        texture['uuid'] = ident(skill['id'] + '/texture')
        for key in ('path', 'relative_path'):
            texture.pop(key, None)
        texture_index = len(model['textures'])
        model['textures'].append(texture)
        for i, source in enumerate(hold['elements']):
            assert not any(source.get('rotation', [])) and source.get('export', True)
            cube = copy.deepcopy(source)
            cube['uuid'] = ident(skill['id'] + '/cube/' + str(i))
            cube['name'] = 'V0.2/' + skill['itemId'] + '/' + str(i)
            cube['from'] = (origin + np.array(source['from']) * scale).tolist()
            cube['to'] = (origin + np.array(source['to']) * scale).tolist()
            cube['origin'] = origin.tolist()
            cube['rotation'] = [0, 0, 0]
            cube['locked'] = True
            for face in cube['faces'].values():
                if face.get('texture') is not None and face.get('texture') is not False:
                    face['texture'] = texture_index
            model['elements'].append(cube)
            cube_ids.append(cube['uuid'])
        group = {'uuid': group_id, 'name': 'V0.2武器/' + skill['itemId'], 'origin': origin.tolist(),
                 'rotation': angles.tolist(), 'export': True, 'visibility': True, 'locked': True,
                 'isOpen': False}
        model['groups'].append(group)

        def attach(nodes):
            for node in nodes:
                if isinstance(node, dict):
                    if node['uuid'] == arm['uuid']:
                        node['children'].append({'uuid': group_id, 'isOpen': False, 'children': cube_ids})
                        return True
                    if attach(node.get('children', [])):
                        return True
            return False

        assert attach(model['outliner'])
        assert model['animations'] == original_animations
        assert model['elements'][:len(original_elements)] == original_elements
        binding = {'itemId': skill['itemId'], 'sourceProject': hold_path.relative_to(ROOT).as_posix(),
                   'sourceSha256': sha(hold_path), 'sourceMotionProject': source_path.relative_to(ROOT).as_posix(),
                   'motionSha256': sha(source_path), 'hand': 'right', 'display': copy.deepcopy(hold['display']),
                   'itemToArm': m.tolist(), 'bakedItemScale': scale.tolist(), 'weaponGroup': group_id,
                   'armGroup': arm['uuid'], 'geometry': len(cube_ids), 'animationsUnchanged': True,
                   'rootMotionApplied': False, 'runtimeInstalled': False}
        model.setdefault('unhandled_root_fields', {})['elden_weapon_v02'] = binding
        model['name'] += '-V0.2武器'
        model.pop('history', None)
        plans.append((OUT / 'projects' / (skill['id'] + '.bbmodel'), model))
        rows.append({'skill': skill['id'], **binding, 'clips': len(model['animations'])})
    (OUT / 'projects').mkdir(parents=True)
    for folder in ['qa', 'qa/native-roundtrip', 'previews']:
        (OUT / folder).mkdir(exist_ok=True)
    for path, model in plans:
        path.write_text(json.dumps(model, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    for filename, data in [('manifest.json', {'source': str(SOURCE.relative_to(ROOT)), 'skills': rows, 'nativeCheckPending': True, 'runtimeInstalled': False}),
                           ('input-hashes.json', hashes)]:
        (OUT / filename).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (OUT / 'README.md').write_text(
        '# 原作动作研究：V0.2武器与握姿\n\n'
        '8份工程、15段动作加入V0.2配对武器，挂到Minecraft候选的右臂。所有原动作关键帧、时长、1.3倍速、腰部收幅与手臂70%设置保持不变。\n\n'
        '直接在Blockbench打开projects/。武器与贴图已内嵌，随手臂及人物朝向父组运动；不需要动作编辑器插件。第三人称握姿应用一次，未叠加旧运行资源的厚度校正。\n\n'
        '原始HKX、完整解码与motions/仍在相邻的elden-source-motion-20261021-arm70/，本目录不重复复制。旧工程保留。\n\n'
        '这是本地研究预览，未接入游戏；无独立手腕/肘关节，不能保证原作双手接触、武器轨迹及命中完全一致。新武器仅挂在收幅后的人物上，没有修改原始细线骨架。\n', encoding='utf-8')
    print(json.dumps({'skills': len(rows), 'clips': sum(r['clips'] for r in rows), 'animationsUnchanged': True, 'output': str(OUT)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
