"""Verify the saved native transforms against the actual source routes and built JAR."""
import hashlib
import importlib.util
import json
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'art/equipment-2d/blockbench-holds-20261021'
OUT = WORK / 'grip-audit'


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def main():
    routes_path = ROOT / 'src/main/resources/assets/eldencombat/routes/2d.json'
    routes = {e['assetId']: e for e in read(routes_path)['equipment'] if e.get('route') == 'own_sprite'}
    plan = read(OUT / 'candidates/frame-changes.json')['changes']
    frames = []
    for row in plan:
        context = row['context']
        stem = row.get('project', row['id'])
        saved = read(OUT / 'candidates/exports' / (stem + '.json'))['display'][context]
        actual = routes[row['id']]['displayBlocking' if row.get('target') == 'blocking' else 'display'][context]
        for field, default in (('rotation', [0, 0, 0]), ('translation', [0, 0, 0]), ('scale', [1, 1, 1])):
            errors = [abs((a-b+180) % 360-180) if field == 'rotation' else abs(a-b)
                      for a, b in zip(saved.get(field, default), actual.get(field, default))]
            assert max(errors) < .0001, (stem, context, field)
        frames.append(dict(id=row['id'], context=context, target=row.get('target', 'display'), nativeMatchesSource=True))
    assert len(frames) == 23

    spec = importlib.util.spec_from_file_location('display_math', Path(__file__).with_name('build-display-candidates.py'))
    maths = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(maths)
    contacts = []
    for asset in read(ROOT / 'art/equipment-2d/blockbench-all-items-20261018/manifest.json')['assets']:
        name = asset['id']
        if name in ('claw', 'fist'):
            continue
        for left in (False, True):
            sign = -1 if left else 1
            context = 'thirdperson_' + ('lefthand' if left else 'righthand')
            base = maths.rotation_xyz(-90, 180, 0) @ maths.translation([sign/16, 2/16, -10/16])
            grip = maths.anchor_block(asset['anchorPixels'], asset['variants'][0]['size'])
            point = (base @ maths.raw_pose(routes[name]['display'][context], left) @ np.r_[grip, 1])[:3] * 16
            low, high = np.array([-1, 6, -2] if left else [-3, 6, -2]), np.array([3, 10, 2] if left else [1, 10, 2])
            distance = float(np.linalg.norm(np.maximum(low-point, 0) + np.maximum(point-high, 0)))
            contacts.append(dict(id=name, hand='left' if left else 'right', anchorArmLocalUnits=point.tolist(),
                                 outsideHandDistanceUnits=distance, insideHandBox=distance < .0001))
            if name in ('ballista', 'test_flask'):
                assert np.linalg.norm(point - [-sign, 8, 0]) < .001, (name, left, point)

    native = read(WORK / 'native-review.json')['items']
    use = read(OUT / 'use-poses/native-review.json')['items']
    assert len(native) == 36 and all(len(r['captures']) == 4 for r in native)
    assert len(use) == 72 and all(r['nonempty'] and r['savedDisplayUnchanged'] for r in use)
    jar = ROOT / 'build/libs/eldencombat-0.2.0.jar'
    with zipfile.ZipFile(jar) as package:
        assert not any('Harness' in name for name in package.namelist())
        assert package.read('assets/eldencombat/routes/2d.json') == routes_path.read_bytes()
        for item in ('claw', 'fist'):
            for arm in ('right', 'left'):
                name = f'assets/eldencombat/worn/{item}_{arm}.json'
                assert package.read(name) == (ROOT / 'src/main/resources' / name).read_bytes()

    tests = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in (ROOT / 'build/test-results/test').glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        for field in tests:
            tests[field] += int(suite.attrib.get(field, 0))
    assert tests['tests'] >= 261 and tests['failures'] == tests['errors'] == 0
    report = dict(changedContexts=frames, currentContacts=contacts, nativeHeldProjects=36, nativeHeldViews=144,
                  nativeUseViews=72, tests=tests, sourceAdopted=True, jarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                  nonemptyIsNotVisualAcceptance=True, contactPointIsNotFullGripAcceptance=True,
                  installed=False, gameStarted=False, runtimeAccepted=False, humanAccepted=False)
    (OUT / 'final-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(f'GRIP_VERIFIED: {len(frames)} frames, {len(contacts)} current hand contacts, 72 native use views; JAR {report["jarSha256"]}')
    print('Game and human acceptance remain pending.')


if __name__ == '__main__':
    main()
