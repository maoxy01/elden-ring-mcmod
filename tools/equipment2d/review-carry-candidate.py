"""Stage a first-person carry candidate for review; never overwrite admitted resources.

Uses existing vanilla-derived hand/display math. Projection images are diagnostic
illustrations, not Minecraft screenshots or evidence of user acceptance.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "art/equipment-2d/carry-review-20261020"
spec = importlib.util.spec_from_file_location("carry_projection", Path(__file__).with_name("calibrate-first-person.py"))
maths = importlib.util.module_from_spec(spec)
spec.loader.exec_module(maths)
NAMES = ["great_sword", "great_bow", "curved_sword", "great_spear",
         "malikeths_black_blade", "starscourge_greatsword", "mohgwyns_sacred_spear", "twinblade"]


def candidate(entry, frame, left):
    result = copy.deepcopy(frame)
    result["scale"][:2] = [value * .90 for value in result["scale"][:2]]
    grip = maths.maths.anchor_block(entry["geometry"]["anchorPixels"], entry["geometry"]["canvas"])
    current = maths.camera_points(frame, np.array([grip]), left)[0]
    # Hand origin stays low and lateral. A bow's grip is in its middle, not its end.
    ndc_y = -.50 if entry["kind"] == "bow" else -.98
    ndc_x = .49 if entry["kind"] == "bow" else .64
    sign = -1 if left else 1
    target = np.array([sign * ndc_x * -current[2] * maths.TAN * 1.6,
                       ndc_y * -current[2] * maths.TAN, current[2]])
    linear = maths.maths.raw_pose(result, left)[:3, :3]
    hand = np.array([sign * .56, -.52, -.72])
    offset = target - hand - linear @ grip
    result["translation"] = [sign * offset[0] * 16, offset[1] * 16, offset[2] * 16]
    assert result["rotation"] == frame["rotation"]
    assert all(value > 0 for value in result["scale"])
    return result


def draw_weapon(panel, entry, frame):
    sprite = np.asarray(Image.open(ROOT / "art/equipment-2d/full" / next(iter(entry["held"].values()))).convert("RGBA"))
    w, h = entry["geometry"]["canvas"]
    draw = ImageDraw.Draw(panel)
    quads = []
    for py, px in zip(*np.nonzero(sprite[:, :, 3] > 0)):
        corners = np.array([[px/w-.5, .5-py/h, 0], [(px+1)/w-.5, .5-py/h, 0],
                            [(px+1)/w-.5, .5-(py+1)/h, 0], [px/w-.5, .5-(py+1)/h, 0]])
        camera = maths.camera_points(frame, corners, False)
        if camera[:, 2].max() >= -.05:
            continue
        ndc = maths.project(camera, panel.width/panel.height)
        xy = [((a+1)*panel.width/2, (1-b)*panel.height/2) for a, b in ndc]
        quads.append((camera[:, 2].mean(), xy, tuple(int(v) for v in sprite[py, px])))
    for _, xy, colour in sorted(quads, key=lambda item: item[0]):
        draw.polygon(xy, fill=colour)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source = ROOT / "src/main/resources/assets/eldencombat/routes/2d.json"
    routes = json.loads(source.read_text(encoding="utf-8-sig"))
    entries = {e["assetId"]: e for e in routes["equipment"] if e.get("route") == "own_sprite"}
    checks, frames = [], {}
    for name in NAMES:
        entry = entries[name]
        frames[name] = {}
        points = np.concatenate([maths.pixel_points(entry, ROOT / "art/equipment-2d/full" / value)
                                 for value in entry["held"].values()])
        for left, context in enumerate(maths.CONTEXTS):
            before = entry["display"][context]
            after = candidate(entry, before, bool(left))
            frames[name][context] = after
            checks.append(dict(id=name, context=context, before=before, after=after,
                boundsByAspect={str(a): maths.bounds(after, points, bool(left), a) for a in (4/3, 1.6, 16/9, 21/9)}))
        # Editable draft preserves geometry, embedded textures and all third-person slots.
        model_path = ROOT / "art/equipment-2d/blockbench-holds-20261019/projects" / (name + ".bbmodel")
        model = json.loads(model_path.read_text(encoding="utf-8-sig"))
        model["display"].update(frames[name])
        model["name"] = "carry_candidate_" + name
        (OUT / (name + ".bbmodel")).write_text(json.dumps(model, ensure_ascii=False, indent=2), encoding="utf-8")
    panel_w, panel_h, label = 480, 300, 26
    sheet = Image.new("RGB", (panel_w * 2, 52 + (panel_h+label)*len(NAMES)), "#1d2631")
    title = ImageDraw.Draw(sheet)
    title.text((10, 8), "MATHEMATICAL PROJECTION ONLY / NOT A MINECRAFT SCREENSHOT", fill="white")
    title.text((10, 30), "CURRENT INSTALLED PARAMETERS", fill="white")
    title.text((panel_w+10, 30), "CANDIDATE / NOT INSTALLED", fill="white")
    for row, name in enumerate(NAMES):
        for col, frame in enumerate([entries[name]["display"]["firstperson_righthand"], frames[name]["firstperson_righthand"]]):
            panel = Image.new("RGB", (panel_w, panel_h), "#6f8fa8")
            draw = ImageDraw.Draw(panel)
            draw.rectangle((0, panel_h*.6, panel_w, panel_h), fill="#526740")
            draw.line((panel_w/2-5, panel_h/2, panel_w/2+5, panel_h/2), fill="white")
            draw.line((panel_w/2, panel_h/2-5, panel_w/2, panel_h/2+5), fill="white")
            draw_weapon(panel, entries[name], frame)
            x, y = col * panel_w, 52 + row * (panel_h+label)
            title.text((x+8, y+5), name, fill="white")
            sheet.paste(panel, (x, y+label))
    sheet.save(OUT / "comparison-all.png")
    sheet.crop((0, 0, panel_w*2, 52+(panel_h+label)*2)).save(OUT / "comparison-focus.png")
    report = dict(sourceRoutesSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        scope="8 own weapons, first-person display draft only", contexts=16, checks=checks,
        runtimeAccepted=False, humanAccepted=False, installed=False, sourceArtworkChanged=False,
        limitations="Resting vanilla 70-degree hand projection; no equip/swing/bob, arm rendering or shader validation")
    (OUT / "candidate.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "checks"}))


if __name__ == "__main__":
    main()
