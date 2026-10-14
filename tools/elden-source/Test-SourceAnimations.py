import hashlib
import json
from pathlib import Path
import sys
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'build' / 'elden-source' / 'decoded-skills'
sys.path.insert(0, str(ROOT / 'build' / 'elden-source' / 'tools' / 'python-havok'))
import numpy as np
from PIL import Image


class SourceAnimationsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.report = json.loads((DATA / 'decode-report.json').read_text())
        cls.skeleton = json.loads((DATA / 'skeleton.json').read_text())

    def test_eight_skill_groups_fifteen_clips(self):
        self.assertEqual({814, 820, 827, 828, 829, 832, 834, 838}, {r['TaeGroup'] for r in self.report['Clips']})
        self.assertEqual(15, self.report['DecodedClipCount'])
        self.assertEqual(15, len({r['Clip'] for r in self.report['Clips']}))
        self.assertEqual(2744, sum(r['Frames'] for r in self.report['Clips']))

    def test_source_and_output_hashes(self):
        for record in self.report['Clips']:
            for path_key, hash_key in [('Source', 'SourceSha256'), ('Compendium', 'CompendiumSha256'), ('Output', 'OutputSha256')]:
                with self.subTest(clip=record['Clip'], path=path_key), Path(record[path_key]).open('rb') as stream:
                    self.assertEqual(record[hash_key], hashlib.file_digest(stream, 'sha256').hexdigest().upper())

    def test_all_tracks_and_frames(self):
        for record in self.report['Clips']:
            with self.subTest(clip=record['Clip']), np.load(record['Output'], allow_pickle=False) as data:
                f, t, b = record['Frames'], record['Tracks'], record['SkeletonBones']
                self.assertEqual((f, t, 3), data['translation'].shape)
                self.assertEqual((f, t, 4), data['quaternion_xyzw'].shape)
                self.assertEqual((f, t, 3), data['scale'].shape)
                self.assertEqual((f, b, 4, 4), data['world_matrices'].shape)
                self.assertTrue(all(np.isfinite(data[key]).all() for key in data.files))
                self.assertTrue(np.all(data['scale'] > 0))
                np.testing.assert_allclose(np.linalg.norm(data['quaternion_xyzw'], axis=-1), 1, atol=1e-6)
                self.assertEqual(f, len(data['times']))
                self.assertAlmostEqual(0, data['times'][0])
                self.assertAlmostEqual(record['DurationSeconds'], data['times'][-1])
                np.testing.assert_allclose(np.diff(data['times']), record['FrameDuration'], atol=2e-7)
                self.assertEqual(record['HasExtractedRootMotion'], len(data['root_motion']) > 0)
                hands = [self.skeleton['Names'].index(name) for name in ('L_Hand', 'R_Hand')]
                self.assertGreater(float(np.ptp(data['world_matrices'][:, hands, :3, 3], axis=0).max()), 0.1)

    def test_nonblank_pose_sheets(self):
        for record in self.report['Clips']:
            path = DATA.parent / 'source-motion-preview' / (Path(record['Clip']).stem + '.png')
            with self.subTest(clip=record['Clip']), Image.open(path) as image:
                self.assertEqual((1600, 400), image.size)
                for panel in range(8):
                    pixels = np.asarray(image.crop((200 * panel, 80, 200 * (panel + 1), 350)))
                    self.assertGreater(np.count_nonzero(np.max(pixels, axis=-1) > 100), 100)

    def test_raw_assets_not_in_existing_mod_jar(self):
        jar = ROOT / 'build' / 'libs' / 'eldencombat-0.2.0.jar'
        if not jar.exists():
            self.skipTest('No existing mod JAR to audit.')
        with zipfile.ZipFile(jar) as archive:
            rejected = [name for name in archive.namelist() if name.lower().endswith(('.hkx', '.tae', '.dcx', '.compendium', '.npz'))
                        or 'elden-source/' in name]
        self.assertEqual([], rejected)


if __name__ == '__main__':
    unittest.main(verbosity=2)
