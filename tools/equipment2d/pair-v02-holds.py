"""Pair the selected V0.2 geometry with the user's saved 20261022 grips.

Create a separate authoring handoff; never redraw textures, retune display slots,
overwrite source projects, or install runtime assets.
"""
import base64
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import statistics
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'build/elden-source/tools/python-havok'))
import numpy as np
from PIL import Image

ART = ROOT / 'art/equipment-2d/V0.2'
GRIPS = ROOT / 'art/equipment-2d/blockbench-holds-20261022'
OUT = ART / 'holds'
CONTEXTS = ['firstperson_righthand', 'firstperson_lefthand',
            'thirdperson_righthand', 'thirdperson_lefthand']
spec = importlib.util.spec_from_file_location('display_math', Path(__file__).with_name('build-display-candidates.py'))
display_math = importlib.util.module_from_spec(spec)
spec.loader.exec_module(display_math)


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def state_cubes(model, state):
    groups = {g['uuid']: g for g in model.get('groups', [])}
    cubes = {e['uuid']: e for e in model['elements']}
    selected = []
    def visit(node, active=False, export=True):
        if isinstance(node, str):
            cube = cubes[node]
            if active and export and cube.get('export', True):
                selected.append(cube)
            return
        group = groups[node['uuid']]
        if any(abs(v) > 1e-8 for v in group.get('rotation', [0, 0, 0])):
            raise ValueError('Animated/rotated source group needs explicit baking: ' + group['name'])
        for child in node.get('children', []):
            visit(child, active or group['name'] == state, export and group.get('export', True))
    for node in model['outliner']:
        visit(node)
    if not selected:
        raise ValueError('No selected source state: ' + state)
    return selected


def original_factor(item, hold, state):
    source = hold['unhandled_root_fields']['hold_source']
    if 'modelToItemBoxScaleXY' in source:
        return source['modelToItemBoxScaleXY'], 'saved grip conversion basis'
    # The user's bow standby project predates this metadata field. Recover its
    # original basis from the declared native source, not from the new silhouette.
    native = ROOT / source['nativeSource'] / 'items' / (item['id'] + '.bbmodel')
    original = read(native)
    cubes = state_cubes(original, state)
    unit = statistics.median((c['to'][0] - c['from'][0]) /
                            abs(c['faces']['south']['uv'][2] - c['faces']['south']['uv'][0]) for c in cubes)
    return round(16 / (source['canvas'][0] * unit), 5), 'original native pixel/unit basis: ' + native.relative_to(ROOT).as_posix()


def main():
    if OUT.exists():
        raise ValueError('Pairing folder already exists; preserve edited outputs: ' + str(OUT))
    original_files = [p for p in ART.rglob('*') if p.is_file()] + list(GRIPS.glob('*.bbmodel'))
    inputs = {p: sha(p) for p in original_files}
    manifest = read(ART / 'manifest.json')
    plans, rows = [], []
    def plan(relative, data):
        content = data if isinstance(data, bytes) else (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
        plans.append((relative, content))

    for item in manifest['items']:
        model_path = ART / item['model']
        assert sha(model_path) == item['modelSha256'], 'Selected model hash changed: ' + item['id']
        model = read(model_path)
        if item['renderKind'] == 'worn_hand_gear' or item['selection'] == 'dsh_worn':
            grip_path = GRIPS / (item['id'] + '.bbmodel')
            # This batch's chosen DSH meshes and saved wear poses are byte-identical.
            # Do not flatten them into a Java item or mirror their left arm again.
            assert model_path.read_bytes() == grip_path.read_bytes(), 'Worn mesh/pose changed; review attachment differences'
            relative = 'projects/' + grip_path.name
            plan(relative, model_path.read_bytes())
            rows.append(dict(id=item['id'], name=item['name'], kind='arm_mount', selection=item['selection'],
                project=relative, model=item['model'], modelSha256=sha(model_path),
                gripSource=grip_path.relative_to(ROOT).as_posix(), gripSha256=sha(grip_path),
                armPivotRight=item['armPivotRight'], armPivotLeft=item['armPivotLeft'],
                originalBytesPreserved=True, contract='../contracts/WEAR-CONTRACT.md'))
            continue
        states = [t for t in item['textures'] if not t['state'].startswith('gui')]
        for index, texture_spec in enumerate(states):
            state = texture_spec['state']
            stem = item['id'] if index == 0 else item['id'] + '__' + state
            grip_path = GRIPS / (stem + '.bbmodel')
            hold = read(grip_path)
            source = hold['unhandled_root_fields']['hold_source']
            assert source['id'] == item['id']
            assert source.get('state', state) == state
            assert source['anchorPixels'] == item['anchorPixels'], 'Grip origin changed: ' + stem
            assert all(c in hold['display'] for c in CONTEXTS), 'Missing hand display slot: ' + stem
            width, height = texture_spec['width'], texture_spec['height']
            assert source['canvas'] == [width, height]
            factor, factor_source = original_factor(item, hold, state)
            assert 0 < factor < 20
            anchor = np.array([16 * source['anchorPixels'][0] / width,
                               16 * (1 - source['anchorPixels'][1] / height), 8.])
            old_geometry_error = 0.
            for cube in hold['elements']:
                assert not any(abs(v) > 1e-6 for v in cube.get('rotation', [0, 0, 0])), 'Geometry edits need review: ' + stem
                uv = cube['faces']['south']['uv']
                expected = {'from': [min(uv[0], uv[2]) * 16 / width, (1 - max(uv[1], uv[3]) / height) * 16],
                            'to': [max(uv[0], uv[2]) * 16 / width, (1 - min(uv[1], uv[3]) / height) * 16]}
                for field in expected:
                    old_geometry_error = max(old_geometry_error, max(abs(cube[field][a] - expected[field][a]) for a in [0, 1]))
            assert old_geometry_error < .001, ('Saved grip has non-display geometry edits', stem, old_geometry_error)
            selected = state_cubes(model, state)
            converted = copy.deepcopy(selected)
            inverse_error = 0.
            for before, after in zip(selected, converted):
                assert before['type'] == 'cube'
                assert not any(abs(v) > 1e-8 for v in before.get('rotation', [0, 0, 0]))
                for field in ['from', 'to', 'origin']:
                    if field in before:
                        value = np.asarray(before[field])
                        after[field] = (value * [factor, factor, 1] + anchor).tolist()
                        inverse_error = max(inverse_error, float(np.max(np.abs((np.asarray(after[field]) - anchor) / [factor, factor, 1] - value))))
                for face in after['faces'].values():
                    if face.get('texture') is not None:
                        face['texture'] = 0
            assert inverse_error < 1e-8
            texture_path = ART / texture_spec['file']
            assert sha(texture_path) == texture_spec['sha256']
            texture = copy.deepcopy(next(t for t in model['textures'] if t['name'] == item['id'] + '__' + state + '.png'))
            bitmap = base64.b64decode(texture['source'].split(',', 1)[1])
            with Image.open(io.BytesIO(bitmap)) as embedded, Image.open(texture_path) as external:
                assert embedded.convert('RGBA').tobytes() == external.convert('RGBA').tobytes()
            texture.update(name=item['id'] + '__' + state + '.png', id='layer0', namespace='eldencombat', folder='item/v02',
                           width=width, height=height, uv_width=width, uv_height=height)
            for field in ['path', 'relative_path']:
                texture.pop(field, None)
            paired = copy.deepcopy(hold)
            for field in ['history', 'undo_history', 'animations']:
                paired.pop(field, None)
            paired.update(name='V0.2_' + stem, elements=converted, groups=[], outliner=[c['uuid'] for c in converted],
                          textures=[texture], resolution=dict(width=width, height=height))
            paired['unhandled_root_fields'] = dict(hold_source=dict(id=item['id'], state=state,
                sourceRoot='art/equipment-2d/V0.2', sourceModel=item['model'], sourceModelSha256=sha(model_path),
                textureSha256=sha(texture_path), canvas=[width, height], anchorPixels=item['anchorPixels'],
                modelToItemBoxScaleXY=factor, runtimeAccepted=False),
                hold_pairing=dict(gripSource=grip_path.relative_to(ROOT).as_posix(), gripSha256=sha(grip_path),
                    selection=item['selection'], displayCopiedVerbatim=True, factorSource=factor_source))
            assert paired['display'] == hold['display']
            # Both projects use the same origin and literal display values. Check
            # vanilla's left-handed sign convention as well as the right hand.
            palm = (anchor - 8) / 16
            palm_errors = {}
            for context in CONTEXTS:
                left = context.endswith('lefthand')
                old = display_math.raw_pose(hold['display'][context], left) @ np.r_[palm, 1]
                new = display_math.raw_pose(paired['display'][context], left) @ np.r_[palm, 1]
                palm_errors[context] = float(np.max(np.abs(new - old)))
            relative = 'projects/' + stem + '.bbmodel'
            plan(relative, paired)
            plan('textures/' + texture['name'], texture_path.read_bytes())
            rows.append(dict(id=item['id'], name=item['name'], state=state, kind='held', selection=item['selection'],
                project=relative, model=item['model'], modelSha256=sha(model_path),
                gripSource=grip_path.relative_to(ROOT).as_posix(), gripSha256=sha(grip_path),
                sourceTexture=texture_spec['file'], texture='textures/' + texture['name'], textureSha256=sha(texture_path),
                textureResource='eldencombat:item/v02/' + texture['name'][:-4],
                sourceCubes=len(selected), projectCubes=len(converted), displayCopiedVerbatim=True,
                factor=factor, factorSource=factor_source, geometryInverseMaxError=inverse_error,
                palmErrorsBlocks=palm_errors, oldGeometryPixelMaxError=old_geometry_error))
    expected = {Path(row['gripSource']).name for row in rows}
    assert len(rows) == 38 and expected == {p.name for p in GRIPS.glob('*.bbmodel')}
    assert len({row['id'] for row in rows}) == 28
    result = dict(schema='equipment-model-grip-pairing/v1', date='2026-10-22',
        modelSource=ART.relative_to(ROOT).as_posix(), gripSource=GRIPS.relative_to(ROOT).as_posix(),
        totalItems=28, totalProjects=38, heldProjects=36, armMountProjects=2,
        policy='New selected geometry in the saved per-item grip coordinate basis; display values copied verbatim; no model-wide fit or retuning',
        runtimeInstalled=False, humanVisualAccepted=False, items=rows)
    plan('mapping.json', result)
    plan('input-hashes.json', {p.relative_to(ROOT).as_posix(): value for p, value in inputs.items()})
    assert all(sha(p) == value for p, value in inputs.items()), 'Source changed during preparation'
    for folder in ['projects', 'textures', 'exports', 'qa/native-roundtrip', 'qa/screenshots']:
        (OUT / folder).mkdir(parents=True, exist_ok=True)
    for relative, content in plans:
        (OUT / relative).write_bytes(content)
    assert all(sha(p) == value for p, value in inputs.items()), 'Source changed during write'
    validation = dict(items=28, projects=38, held=36, worn=2, inputsUnchanged=True,
        selectedModelHashesMatch=True, sourceTexturesMatch=True, displaysCopiedVerbatim=True,
        geometryRoundtrip=True, palmAnchorsUnchanged=True, nativeCheckPending=True, runtimeInstalled=False)
    (OUT / 'validation.json').write_text(json.dumps(validation, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(validation, ensure_ascii=False))


if __name__ == '__main__':
    main()
