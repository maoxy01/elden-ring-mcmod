import json
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2] / 'build' / 'elden-source'
sys.path.insert(0, str(ROOT / 'tools' / 'python-havok'))
import numpy as np

spec = importlib.util.spec_from_file_location('retarget', Path(__file__).with_name('Retarget-WaveOfGold.py'))
retarget = importlib.util.module_from_spec(spec)
spec.loader.exec_module(retarget)


class RetargetedMotionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.destination = ROOT / 'retargeted'
        cls.profile = json.loads((cls.destination / 'profile.json').read_text())
        cls.clip = json.loads((cls.destination / 'wave_of_gold.json').read_text())
        cls.fk = json.loads((cls.destination / 'wave_of_gold.fk.json').read_text())
        cls.order, cls.parents, cls.local, cls.bind = retarget.load_target(ROOT.parent / 'ef-ref' / 'animmodels_entity_biped.json')
        with np.load(cls.destination / 'wave_of_gold.preview.npz', allow_pickle=False) as data:
            cls.expected = data['world_matrices'].copy()
            cls.times = data['times'].copy()
        cls.world = np.asarray([[frame[name] for name in cls.order] for frame in cls.fk['frames']]).reshape(-1, len(cls.order), 4, 4)

    def test_independent_ef_json_fk_matches_exported_poses(self):
        self.assertEqual(self.order, self.fk['order'])
        np.testing.assert_allclose(self.times, self.fk['times'], atol=1e-8)
        np.testing.assert_allclose(self.expected, self.world, atol=0.003)

    def test_bind_lengths_and_hand_connections_preserved(self):
        for name in self.order:
            if self.parents[name] is None:
                continue
            with self.subTest(joint=name):
                child = self.world[:, self.order.index(name), :3, 3]
                parent = self.world[:, self.order.index(self.parents[name]), :3, 3]
                bind_length = np.linalg.norm(self.local[name][:3, 3])
                np.testing.assert_allclose(np.linalg.norm(child - parent, axis=-1), bind_length, atol=.001)

    def test_anatomical_limb_directions_follow_source(self):
        skeleton = json.loads((ROOT / 'decoded-skills/skeleton.json').read_text())
        names = skeleton['Names']
        basis = np.asarray(json.loads((self.destination / 'retarget-report.json').read_text())['Basis'])
        with np.load(ROOT / 'decoded-skills/a820_040000.npz', allow_pickle=False) as data:
            source = data['world_matrices'][:, :, :3, 3]
        for target in ('Arm_R', 'Arm_L', 'Hand_R', 'Hand_L'):
            source_tip, target_tip = retarget.AXES[target]
            src = (source[:, names.index(source_tip)] - source[:, names.index(retarget.MAPPING[target])]) @ basis.T
            tgt = (self.world[:, self.order.index(target_tip), :3, 3] - self.world[:, self.order.index(target), :3, 3])
            src /= np.linalg.norm(src, axis=-1, keepdims=True)
            tgt /= np.linalg.norm(tgt, axis=-1, keepdims=True)
            angle = np.degrees(np.arccos(np.clip(np.sum(src * tgt, axis=-1), -1, 1)))
            with self.subTest(joint=target):
                self.assertLess(float(angle.max()), 1.0)

    def test_phase_and_release_times_agree(self):
        p = self.profile
        self.assertEqual(2.7, p['durationSeconds'])
        self.assertEqual(6, p['recoveryTicks'])
        self.assertEqual(p['durationSeconds'], (p['windupTicks'] + p['activeTicks'] + p['recoveryTicks']) / 20)
        self.assertEqual(2, p['windupTicks'] / 20)
        self.assertIn(2., self.times.tolist())
        self.assertTrue(np.any(np.isclose(self.times, 2.4)))
        self.assertTrue(all(track['time'][-1] == p['durationSeconds'] for track in self.clip['animation']))

    def test_no_stretch_or_horizontal_root_motion(self):
        for track in self.clip['animation']:
            locations = np.asarray([key['loc'] for key in track['transform']])
            scales = np.asarray([key['sca'] for key in track['transform']])
            np.testing.assert_allclose(scales, 1, atol=1e-8)
            if track['name'] == 'Root':
                np.testing.assert_allclose(locations[:, [0, 2]], 0, atol=1e-6)
            else:
                np.testing.assert_allclose(locations, 0, atol=1e-6)

    def test_pelvis_and_lower_body_remain_planted(self):
        for name in ('Root', 'Thigh_R', 'Thigh_L', 'Leg_R', 'Leg_L', 'Knee_R', 'Knee_L'):
            poses = self.world[:, self.order.index(name)]
            np.testing.assert_allclose(poses, np.broadcast_to(poses[0], poses.shape), atol=1e-6)
        for name in ('Leg_R', 'Leg_L'):
            thigh = name.replace('Leg', 'Thigh')
            direction = self.world[0, self.order.index(name), :3, 3] - self.world[0, self.order.index(thigh), :3, 3]
            angle = np.degrees(np.arccos(-direction[1] / np.linalg.norm(direction)))
            self.assertLess(angle, 6, 'Blocky legs should not spread or twist excessively.')


if __name__ == '__main__':
    unittest.main(verbosity=2)
