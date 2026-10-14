"""Retarget verified source motion into an opt-in local Epic Fight test clip."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

WORKSPACE = Path(__file__).resolve().parents[2]
ROOT = WORKSPACE / 'build' / 'elden-source'
sys.path.insert(0, str(ROOT / 'tools' / 'python-havok'))
import numpy as np
from scipy.spatial.transform import Rotation

B2M = np.array([[1, 0, 0, 0], [0, 0, 1, 0], [0, -1, 0, 0], [0, 0, 0, 1]], dtype=float)
MAPPING = {
    'Root': 'Pelvis', 'Torso': 'Spine', 'Chest': 'Spine2', 'Head': 'Head',
    'Thigh_R': 'R_Thigh', 'Leg_R': 'R_Calf', 'Knee_R': 'R_Calf',
    'Thigh_L': 'L_Thigh', 'Leg_L': 'L_Calf', 'Knee_L': 'L_Calf',
    'Shoulder_R': 'R_Clavicle', 'Arm_R': 'R_UpperArm', 'Hand_R': 'R_Forearm', 'Tool_R': 'R_Weapon',
    'Shoulder_L': 'L_Clavicle', 'Arm_L': 'L_UpperArm', 'Hand_L': 'L_Forearm', 'Tool_L': 'L_Weapon',
}
AXES = {
    'Torso': ('Spine2', 'Chest'), 'Chest': ('Head', 'Head'),
    'Thigh_R': ('R_Calf', 'Leg_R'), 'Thigh_L': ('L_Calf', 'Leg_L'),
    'Shoulder_R': ('R_UpperArm', 'Arm_R'), 'Shoulder_L': ('L_UpperArm', 'Arm_L'),
    'Arm_R': ('R_Forearm', 'Hand_R'), 'Arm_L': ('L_Forearm', 'Hand_L'),
    'Hand_R': ('R_Hand', 'Tool_R'), 'Hand_L': ('L_Hand', 'Tool_L'),
}


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest().upper()


def unit(vector):
    length = np.linalg.norm(vector)
    if length < 1e-7:
        raise ValueError('Degenerate anatomical axis.')
    return vector / length


def orientation(matrix):
    return Rotation.from_matrix(matrix[:3, :3]).as_matrix()


def frame_from_axis(axis):
    y = unit(axis)
    z = np.array([0., 0., 1.])
    if abs(np.dot(y, z)) > .95:
        z = np.array([1., 0., 0.])
    z = unit(z - y * np.dot(y, z))
    return np.column_stack((np.cross(y, z), y, z))


def load_target(path):
    raw = json.loads(path.read_text())
    order, parents, local, bind = [], {}, {}, {}

    def walk(node, parent):
        name = node['name']
        matrix = np.asarray(node['transform'], dtype=float).reshape(4, 4)
        if parent is None:
            matrix = B2M @ matrix
        order.append(name)
        parents[name] = parent
        local[name] = matrix
        bind[name] = matrix if parent is None else bind[parent] @ matrix
        for child in node.get('children', []):
            walk(child, name)

    for node in raw['armature']['hierarchy']:
        walk(node, None)
    return order, parents, local, bind


def main():
    data = ROOT / 'decoded-skills'
    source_info = json.loads((data / 'skeleton.json').read_text())
    names = source_info['Names']
    refs = []
    for parent, local in zip(source_info['Parents'], source_info['ReferenceLocalMatrices']):
        refs.append(np.asarray(local) if parent < 0 else refs[parent] @ local)
    refs = np.asarray(refs)
    report = json.loads((data / 'decode-report.json').read_text())
    source_record = next(r for r in report['Clips'] if r['Clip'] == 'a820_040000.hkx')
    source_file = Path(source_record['Output'])
    if digest(source_file) != source_record['OutputSha256']:
        raise ValueError('Decoded motion hash mismatch.')
    with np.load(source_file, allow_pickle=False) as arrays:
        source = arrays['world_matrices'].copy()
        source_times = arrays['times'].copy()
    ef_path = WORKSPACE / 'build' / 'ef-ref' / 'animmodels_entity_biped.json'
    order, parents, local, bind = load_target(ef_path)
    if not set(MAPPING).issubset(order):
        raise ValueError('Unexpected EF armature.')

    right = refs[names.index('R_UpperArm'), :3, 3] - refs[names.index('L_UpperArm'), :3, 3]
    right[1] = 0
    right = unit(right)
    up = np.array([0., 1., 0.])
    basis = np.vstack((right, up, np.cross(right, up)))
    calibrated = {name: orientation(matrix) for name, matrix in bind.items()}
    for target, (source_tip, target_tip) in AXES.items():
        source_vector = basis @ (refs[names.index(source_tip), :3, 3] - refs[names.index(MAPPING[target]), :3, 3])
        target_vector = bind[target_tip][:3, 3] - bind[target][:3, 3]
        correction = frame_from_axis(source_vector) @ frame_from_axis(target_vector).T
        calibrated[target] = correction @ calibrated[target]
    for target in ('Knee_R', 'Knee_L'):
        thigh = target.replace('Knee', 'Thigh')
        calibrated[target] = calibrated[thigh] @ orientation(local[target])
    for target in ('Elbow_R', 'Elbow_L'):
        arm = target.replace('Elbow', 'Arm')
        calibrated[target] = calibrated[arm] @ orientation(local[target])
    for target in ('Leg_R', 'Leg_L'):
        thigh = target.replace('Leg', 'Thigh')
        calibrated[target] = calibrated[thigh] @ orientation(local[target])

    # Attachment-axis calibration is kept explicit for visual review (+X source -> +Y EF tool).
    weapon_axes = np.array([[0, 1, 0], [-1, 0, 0], [0, 0, 1]], dtype=float)

    index = json.loads((ROOT / 'skill-animation-index.json').read_text(encoding='utf-8-sig'))
    skill = next(s for s in index['Skills'] if s['TaeGroup'] == 820)
    source_animation = next(a for a in skill['Animations'] if a['EventAnimationId'] == 40000)
    template_path = ROOT / 'tools/witchybnd-3.0.1.0/Assets/Templates/TAE.Template.ER.xml'
    bullet_template = ET.parse(template_path).getroot().find('.//event[@id="2"]')
    if bullet_template is None or bullet_template.attrib['name'] != 'BulletBehavior':
        raise ValueError('Bullet event template mismatch.')
    bullets = [e for e in source_animation['Events'] if e['Type'] == 2]
    if len(bullets) != 1:
        raise ValueError('Wave release event is ambiguous.')
    release_source = bullets[0]['StartSeconds']
    windup_ticks = round(release_source * 20)
    active_ticks = 8
    recovery_ticks = 6
    duration_ticks = windup_ticks + active_ticks + recovery_ticks
    duration = duration_ticks / 20
    active_end = release_source + active_ticks / 20
    target_times = np.interp(source_times, [0, release_source, active_end, source_times[-1]],
                             [0, windup_ticks / 20, (windup_ticks + active_ticks) / 20, duration])
    cue_frame = int(np.argmin(np.abs(source_times - release_source)))
    if abs(source_times[cue_frame] - release_source) < 1e-6:
        target_times[cue_frame] = windup_ticks / 20
    frames, bones = len(source_times), len(order)
    world = np.empty((frames, bones, 4, 4))
    transforms = {name: [] for name in order}
    rotations = {}
    # A planted, narrow stance avoids human hip/knee twists on the blocky EF legs.
    lower_body = {'Root', 'Thigh_R', 'Thigh_L', 'Leg_R', 'Leg_L', 'Knee_R', 'Knee_L'}
    for frame_index in range(frames):
        for bone_index, target in enumerate(order):
            parent = parents[target]
            parent_rotation = rotations[parent] if parent else np.eye(3)
            if target in lower_body:
                desired = parent_rotation @ orientation(local[target])
                if target.startswith('Thigh_'):
                    angle = 4 if target.endswith('_R') else -4
                    desired = Rotation.from_euler('z', angle, degrees=True).as_matrix() @ desired
            elif target.startswith('Tool_'):
                desired = basis @ orientation(source[frame_index, names.index(MAPPING[target])]) @ weapon_axes
            elif target in MAPPING:
                source_index = names.index(MAPPING[target])
                delta = basis @ orientation(source[frame_index, source_index]) @ orientation(refs[source_index]).T @ basis.T
                desired = delta @ calibrated[target]
            else:
                desired = parent_rotation @ orientation(local[target])
            rotations[target] = desired
            absolute = np.eye(4)
            absolute[:3, :3] = parent_rotation.T @ desired
            absolute[:3, 3] = local[target][:3, 3]
            parent_world = world[frame_index, order.index(parent)] if parent else np.eye(4)
            world[frame_index, bone_index] = parent_world @ absolute
            delta = np.linalg.solve(local[target], absolute)
            quat = Rotation.from_matrix(delta[:3, :3]).as_quat()
            # EF attributes invert the vector part when loading WXYZ.
            rotation = [quat[3], -quat[0], -quat[1], -quat[2]]
            if transforms[target] and np.dot(rotation, transforms[target][-1]['rot']) < 0:
                rotation = [-v for v in rotation]
            transforms[target].append({'loc': delta[:3, 3].tolist(), 'rot': rotation, 'sca': [1, 1, 1]})
    output = ROOT / 'retargeted'
    output.mkdir(exist_ok=True)
    clip_path = output / 'wave_of_gold.json'
    clip_path.write_text(json.dumps({'format': 'attributes', 'animation': [
        {'name': name, 'time': target_times.tolist(), 'transform': transforms[name]} for name in order
    ]}, separators=(',', ':'), allow_nan=False), encoding='utf-8')
    np.savez_compressed(output / 'wave_of_gold.preview.npz', times=target_times, world_matrices=world)
    mapping_report = {
        'SourceDecodedSha256': digest(source_file), 'SourceHkxSha256': source_record['SourceSha256'],
        'TargetArmatureSha256': digest(ef_path), 'Mapping': MAPPING,
        'Basis': basis.tolist(), 'Frames': frames, 'TargetJoints': bones,
        'TargetDurationSeconds': duration, 'SourceDurationSeconds': float(source_times[-1]),
        'SourceBulletSeconds': release_source, 'TargetBulletTick': windup_ticks,
        'BulletTemplateSha256': digest(template_path), 'BulletParameterHex': bullets[0]['ParameterHex'],
        'HorizontalRootMotionApplied': False, 'TargetLengthsPreserved': True,
        'VerticalRootMotionApplied': False,
        'LowerBodyPolicy': 'Fixed EF pelvis and planted legs, 4 degree thigh splay, no source hip/knee twist.',
        'RecoveryTicks': recovery_ticks, 'RecoveryOnlyRetimed': True,
        'HandSocketTranslationsPreserved': True,
        'WeaponAttachmentAxes': weapon_axes.tolist(),
        'WeaponAxisCalibrationStatus': 'Source +X mapped to EF +Y; pose-based candidate, pending weapon mesh/in-game acceptance.',
        'Status': 'Local test candidate. No in-game visual acceptance or distribution approval.',
    }
    (output / 'retarget-report.json').write_text(json.dumps(mapping_report, indent=2), encoding='utf-8')
    profile = {
        'schema': 1, 'skillId': 'wave_of_gold', 'animationFile': clip_path.name,
        'animationSha256': digest(clip_path), 'durationSeconds': duration,
        'windupTicks': windup_ticks, 'activeTicks': active_ticks, 'recoveryTicks': recovery_ticks,
    }
    (output / 'profile.json').write_text(json.dumps(profile, indent=2), encoding='utf-8')
    print(json.dumps(profile, indent=2))


if __name__ == '__main__':
    main()
