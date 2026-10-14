"""Decode local HKX clips with Soulstruct; keep all outputs outside mod resources."""
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2] / 'build' / 'elden-source'
sys.path.insert(0, str(ROOT / 'tools' / 'python-havok'))
os.environ['USERPROFILE'] = str(ROOT / 'userdata')
os.environ['AppData'] = str(ROOT / 'userdata')

import numpy as np
from soulstruct.havok import HKX
from soulstruct.havok.spline_compression import SplineCompressedAnimationData


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest().upper()


def main():
    index = json.loads((ROOT / 'skill-animation-index.json').read_text(encoding='utf-8-sig'))
    destination = ROOT / 'decoded-skills'
    destination.mkdir(exist_ok=True)
    skeleton_path = ROOT / 'unpacked-player' / 'c0000-anibnd' / 'skeleton.hkx'
    skeleton = HKX.from_path(skeleton_path).root.namedVariants[0].variant.skeletons[0]
    parents = np.asarray(skeleton.parentIndices, dtype=np.int32)
    reference = [pose.to_trs_transform() for pose in skeleton.referencePose]
    skeleton_info = {
        'Source': str(skeleton_path), 'Sha256': sha256(skeleton_path),
        'Names': [bone.name for bone in skeleton.bones], 'Parents': parents.tolist(),
        'ReferenceLocalMatrices': [pose.to_matrix4().data.tolist() for pose in reference],
    }
    (destination / 'skeleton.json').write_text(json.dumps(skeleton_info, indent=2), encoding='utf-8')
    decoded = []
    compendiums = {}
    for skill in index['Skills']:
        clips = {clip['Local']: clip for animation in skill['Animations']
                 if animation['EventAnimationId'] >= 40000 for clip in animation['Clips']}
        for local, expected in clips.items():
            path = Path(local).resolve()
            if not path.is_relative_to(ROOT / 'unpacked-player'):
                raise ValueError(f'Clip outside extraction directory: {path}')
            if sha256(path) != expected['Sha256']:
                raise ValueError(f'Clip hash mismatch: {path}')
            companions = list(path.parent.glob('*.compendium'))
            if len(companions) != 1:
                raise ValueError(f'Expected exactly one compendium: {path.parent}')
            companion = companions[0]
            if companion not in compendiums:
                compendiums[companion] = HKX.from_path(companion)
            hkx = HKX.from_path(path, compendium=compendiums[companion])
            container = hkx.root.namedVariants[0].variant
            animation = container.animations[0]
            binding = container.bindings[0]
            if type(animation).__name__ != 'hkaSplineCompressedAnimation':
                raise ValueError(f'Unsupported animation format: {type(animation).__name__}')
            frames = SplineCompressedAnimationData(
                animation.data, animation.numberOfTransformTracks, animation.numBlocks
            ).to_interleaved_transforms(animation.numFrames, animation.maxFramesPerBlock)
            mapping = np.asarray(binding.transformTrackToBoneIndices, dtype=np.int32)
            if len(mapping) != animation.numberOfTransformTracks or len(set(mapping)) != len(mapping):
                raise ValueError('Invalid or duplicate track-to-bone binding.')
            if np.any(mapping < 0) or np.any(mapping >= len(parents)):
                raise ValueError('Track binding outside skeleton.')
            if len(frames) != animation.numFrames or any(len(f) != len(mapping) for f in frames):
                raise ValueError('Decoded frame or track count mismatch.')
            translation = np.array([[t.translation.data for t in f] for f in frames], dtype=np.float32)
            quaternion_raw = np.array([[t.rotation.data for t in f] for f in frames], dtype=np.float32)
            # Use the decoder's normalized rotation, retaining the raw spline samples for auditing.
            quaternion = np.array([[t.rotation.rotation.as_quat() for t in f] for f in frames], dtype=np.float32)
            scale = np.array([[t.scale.data for t in f] for f in frames], dtype=np.float32)
            world = np.empty((len(frames), len(parents), 4, 4), dtype=np.float32)
            for frame_index, frame in enumerate(frames):
                local_poses = list(reference)
                for bone_index, pose in zip(mapping, frame):
                    local_poses[bone_index] = pose
                unresolved = set(range(len(parents)))
                while unresolved:
                    ready = [b for b in unresolved if parents[b] == -1 or parents[b] not in unresolved]
                    if not ready:
                        raise ValueError('Cyclic skeleton hierarchy.')
                    for bone in ready:
                        matrix = local_poses[bone].to_matrix4().data
                        world[frame_index, bone] = matrix if parents[bone] == -1 else world[frame_index, parents[bone]] @ matrix
                        unresolved.remove(bone)
            root_motion = (np.asarray(animation.extractedMotion.referenceFrameSamples, dtype=np.float32)
                           if animation.extractedMotion is not None else np.empty((0, 4), dtype=np.float32))
            if not all(np.isfinite(a).all() for a in (translation, quaternion_raw, quaternion, scale, world, root_motion)):
                raise ValueError('Non-finite decoded data.')
            raw_error = float(np.max(np.abs(np.linalg.norm(quaternion_raw, axis=-1) - 1)))
            if np.any(np.linalg.norm(quaternion_raw, axis=-1) < 0.1):
                raise ValueError('Degenerate raw spline quaternion.')
            quaternion_error = float(np.max(np.abs(np.linalg.norm(quaternion, axis=-1) - 1)))
            if quaternion_error > 0.002:
                raise ValueError(f'Non-unit quaternion: {quaternion_error}')
            times = np.linspace(0, animation.duration, len(frames), dtype=np.float64)
            output = destination / (path.stem + '.npz')
            np.savez_compressed(output, times=times, track_bones=mapping, translation=translation,
                                quaternion_xyzw=quaternion, quaternion_raw_xyzw=quaternion_raw, scale=scale, world_matrices=world,
                                root_motion=root_motion)
            record = {
                'Skill': skill['Name'], 'TaeGroup': skill['TaeGroup'], 'Clip': path.name,
                'Source': str(path), 'SourceSha256': expected['Sha256'],
                'Compendium': str(companion), 'CompendiumSha256': sha256(companion),
                'Frames': len(frames), 'Tracks': len(mapping), 'SkeletonBones': len(parents),
                'DurationSeconds': animation.duration, 'FrameDuration': animation.frameDuration,
                'QuaternionMaxNormError': quaternion_error, 'Output': str(output), 'OutputSha256': sha256(output),
                'RawSplineQuaternionMaxNormError': raw_error,
                'HasExtractedRootMotion': animation.extractedMotion is not None,
                'Note': 'Original local tracks and bind-relative world matrices. Root motion separate. Not retargeted.',
            }
            decoded.append(record)
            print(f"{skill['Name']}: {path.name}, {len(frames)} frames, {len(mapping)} tracks, {animation.duration:.3f}s", flush=True)
    report = {
        'DecoderVersions': {name: importlib.metadata.version(name) for name in ('soulstruct', 'soulstruct-havok', 'numpy', 'scipy')},
        'Skeleton': skeleton_info['Source'], 'SkeletonSha256': skeleton_info['Sha256'],
        'DecodedClipCount': len(decoded), 'Clips': decoded,
        'Status': 'Source tracks decoded and structurally checked. Visual/retargeting approval still required. No mod resources changed.',
    }
    (destination / 'decode-report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
