"""Make review-only arm fixtures; never change admitted meshes or author projects."""
import copy
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art/equipment-2d/blockbench-holds-20261021/grip-audit/worn-firstperson'


def main():
    (OUT / 'projects').mkdir(parents=True, exist_ok=True)
    (OUT / 'qa').mkdir(exist_ok=True)
    jobs = []
    for item in ('claw', 'fist'):
        path = ROOT / f'art/equipment-2d/worn-hand-gear-20261021/{item}_worn.bbmodel'
        source = json.loads(path.read_text(encoding='utf-8'))
        groups = {g['uuid']: g for g in source['groups']}
        def members(name):
            found = []
            def visit(node, active=False):
                if isinstance(node, str):
                    if active:
                        found.append(node)
                    return
                for child in node.get('children', []):
                    visit(child, active or groups[node['uuid']]['name'] == name)
            for node in source['outliner']:
                visit(node)
            return found
        for arm in ('right', 'left'):
            selected = members('arm_right' if arm == 'right' else 'arm_right_l')
            ref_name = 'ref_arm_r' if arm == 'right' else 'ref_arm_l'
            for skin in ('wide', 'slim'):
                model = copy.deepcopy(source)
                model['elements'] = [e for e in model['elements'] if e['uuid'] in selected or e['name'] == ref_name]
                for cube in model['elements']:
                    cube['export'] = True
                    if skin == 'slim':
                        if cube['name'] == ref_name:
                            cube['from' if arm == 'right' else 'to'][0] += 1 if arm == 'right' else -1
                        else:
                            for field in ('from', 'to', 'origin'):
                                if field in cube:
                                    cube[field][0] += .5 if arm == 'right' else -.5
                model['groups'] = []
                model['outliner'] = [e['uuid'] for e in model['elements']]
                model['unhandled_root_fields'] = dict(reviewOnly=True, arm=arm, skin=skin,
                    sourceSha256=hashlib.sha256(path.read_bytes()).hexdigest())
                stem = f'{item}_{arm}_{skin}'
                fixture = OUT / 'projects' / (stem + '.bbmodel')
                fixture.write_text(json.dumps(model), encoding='utf-8')
                jobs.append(dict(id=stem, item=item, arm=arm, skin=skin, path=fixture.as_posix(),
                                 authoredPivot=[-5 if arm == 'right' else 5, 22, 0]))
    (OUT / 'jobs.json').write_text(json.dumps(dict(jobs=jobs, reviewOnly=True), indent=2), encoding='utf-8')
    receipt = OUT / 'native-review-1600x1000.json'
    if not receipt.exists():
        receipt.write_text('{"items":[]}', encoding='utf-8')
    print('WORN_FP_FIXTURES: 8 native working copies; no runtime/source asset changes')


if __name__ == '__main__':
    main()
