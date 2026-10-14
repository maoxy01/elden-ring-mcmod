"""Render diagnostic source skeletons, without claiming Epic Fight retargeting."""
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2] / 'build' / 'elden-source'
sys.path.insert(0, str(ROOT / 'tools' / 'python-havok'))
import numpy as np
from PIL import Image, ImageDraw, ImageFont

DATA = ROOT / 'decoded-skills'
OUTPUT = ROOT / 'source-motion-preview'
OUTPUT.mkdir(exist_ok=True)
SKELETON = json.loads((DATA / 'skeleton.json').read_text())
REPORT = json.loads((DATA / 'decode-report.json').read_text())
NAMES = SKELETON['Names']
CHAINS = [
    ['Pelvis', 'Spine', 'Spine1', 'Spine2', 'Neck', 'Head'],
    ['Spine2', 'L_Clavicle', 'L_UpperArm', 'L_Forearm', 'L_Hand'],
    ['Spine2', 'R_Clavicle', 'R_UpperArm', 'R_Forearm', 'R_Hand'],
    ['Pelvis', 'L_Thigh', 'L_Calf', 'L_Foot', 'L_Toe0'],
    ['Pelvis', 'R_Thigh', 'R_Calf', 'R_Foot', 'R_Toe0'],
]
COLORS = ['#d9e2e6', '#50cfbc', '#efb555', '#50cfbc', '#efb555']
BODY = sorted({NAMES.index(name) for chain in CHAINS for name in chain})
FONT = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 16)
TITLE = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 22)


def project(points):
    return np.stack((0.84 * points[..., 0] + 0.54 * points[..., 2],
                     -points[..., 1] + 0.12 * points[..., 2]), axis=-1)


def render_clip(record):
    with np.load(record['Output'], allow_pickle=False) as clip:
        world = clip['world_matrices']
        times = clip['times']
        positions = world[:, :, :3, 3].copy()
        # Lock only horizontal pelvis translation to compare postures at a stable viewing scale.
        pelvis = positions[:, NAMES.index('Pelvis'), :].copy()
        positions[:, :, 0] -= pelvis[:, None, 0]
        positions[:, :, 2] -= pelvis[:, None, 2]
        projected = project(positions)
        bounds_min = projected[:, BODY].min(axis=(0, 1))
        bounds_max = projected[:, BODY].max(axis=(0, 1))
        scale = min(165 / max(bounds_max[0] - bounds_min[0], 0.1),
                    240 / max(bounds_max[1] - bounds_min[1], 0.1))
        center = (bounds_min + bounds_max) / 2
        sheet = Image.new('RGB', (1600, 400), '#11171b')
        draw = ImageDraw.Draw(sheet)
        draw.text((18, 12), f"{record['Skill']} / {record['Clip']}", font=TITLE, fill='#edf1f3')
        draw.text((18, 44), 'Original HKX poses | no weapon mesh, VFX, root-motion playback or Epic Fight retargeting', font=FONT, fill='#a8b8c0')
        samples = np.linspace(0, len(times) - 1, 8).round().astype(int)
        for panel, frame in enumerate(samples):
            offset = np.array([panel * 200 + 100, 205])
            screen = (projected[frame] - center) * scale + offset
            draw.line([(panel * 200 + 7, 355), (panel * 200 + 193, 355)], fill='#33434d', width=1)
            for chain, color in zip(CHAINS, COLORS):
                points = [tuple(screen[NAMES.index(name)]) for name in chain]
                draw.line(points, fill=color, width=5)
                for x, y in points:
                    draw.ellipse((x - 3, y - 3, x + 3, y + 3), fill='#f1f6f7')
            x, y = screen[NAMES.index('Head')]
            draw.ellipse((x - 7, y - 9, x + 7, y + 5), outline='#edf1f3', width=2)
            draw.text((panel * 200 + 12, 368), f'{times[frame]:.2f}s / frame {frame}', font=FONT, fill='#a8b8c0')
        sheet.save(OUTPUT / (Path(record['Clip']).stem + '.png'))
        return sheet


if __name__ == '__main__':
    for record in REPORT['Clips']:
        render_clip(record)
    summary = {
        'ClipCount': len(REPORT['Clips']), 'Projection': 'Fixed oblique orthographic skeleton view',
        'HorizontalPelvisTranslationLocked': True, 'ScaleFixedWithinClip': True,
        'RootMotionApplied': False, 'WeaponMeshApplied': False, 'Retargeted': False,
        'LeftLimbs': 'teal', 'RightLimbs': 'gold', 'Data': str(DATA / 'decode-report.json'),
    }
    (OUTPUT / 'preview-report.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(f'Rendered {len(REPORT["Clips"])} source-pose sheets in {OUTPUT}')
