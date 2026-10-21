"""Calibrate only own weapon first-person displays; never edits artwork or world proportions.

Historical 2026-10-18 fitting experiment. The current native Blockbench workflow supersedes it;
do not run this over the saved 2026-10-19 display-overrides. Use sync-blockbench-holds.py instead.

Uses Minecraft 1.21.1's resting hand translation / 70 degree hand projection.
Projection evidence is a mathematical preview, not an in-game acceptance receipt.
"""
import importlib.util
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/firstperson-framing-20261018"
ROUTES = ROOT / "src/main/resources/assets/eldencombat/routes/2d.json"
OVERRIDES = ROOT / "art/equipment-2d/full/display-overrides.json"
spec = importlib.util.spec_from_file_location("display_math", Path(__file__).with_name("build-display-candidates.py"))
maths = importlib.util.module_from_spec(spec)
spec.loader.exec_module(maths)
TAN = math.tan(math.radians(35))
CONTEXTS = ("firstperson_righthand", "firstperson_lefthand")
REFERENCE_DISPLAYS = json.loads((ROOT / "art/equipment-2d/full/display-candidates.json").read_text(encoding="utf-8"))["items"]


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def pixel_points(entry, sprite):
    """All opaque pixel corners, including half-texel extrusion depth on both sides."""
    image = np.asarray(Image.open(sprite).convert("RGBA"))
    ys, xs = np.nonzero(image[:, :, 3] > 0)
    w, h = entry["geometry"]["canvas"]
    points = np.concatenate([
        np.column_stack(((xs + dx) / w - .5, .5 - (ys + dy) / h, np.full(len(xs), z)))
        for dx in (0, 1) for dy in (0, 1) for z in (-1/32, 1/32)
    ])
    return points


def camera_points(frame, points, left):
    matrix = maths.raw_pose(frame, left)
    # Runtime ItemThickness normalizes model-local Z to the vanilla hand thickness, .68.
    matrix[:3, 2] *= .68 / abs(frame["scale"][2])
    result = (matrix @ np.column_stack((points, np.ones(len(points)))).T).T[:, :3]
    return result + np.array([-.56 if left else .56, -.52, -.72])


def project(points, aspect):
    return np.column_stack((points[:, 0] / (-points[:, 2] * TAN * aspect),
                            points[:, 1] / (-points[:, 2] * TAN)))


def bounds(frame, points, left, aspect):
    camera = camera_points(frame, points, left)
    ndc = project(camera, aspect)
    return [float(ndc[:, 0].min()), float(ndc[:, 1].min()),
            float(ndc[:, 0].max()), float(ndc[:, 1].max()), float(camera[:, 2].max())]


def fit(entry, points, left):
    # Recalibration must not rotate/shrink an already calibrated result a second time.
    old = REFERENCE_DISPLAYS[entry["assetId"]]["display"][CONTEXTS[int(left)]]
    old_matrix = maths.raw_pose(old, left)
    old_rotation = maths.normalized_rotation(old_matrix[:3, :3])[:3, :3]
    if entry["kind"] == "crossbow":
        rotation = maths.rot_x(math.radians(55))[:3, :3] @ old_rotation
    else:
        # Open the broad face; keep the existing longitudinal roll (including black-blade edge).
        yaw = old["rotation"][1] * (-1 if left else 1)
        rotation = maths.rot_y(math.radians(-math.copysign(65, yaw)))[:3, :3] @ old_rotation
        if entry["kind"] == "melee":
            grip = maths.anchor_block(entry["geometry"]["anchorPixels"], entry["geometry"]["canvas"])
            tip = points[np.argmax(np.linalg.norm(points[:, :2] - grip[:2], axis=1))] - grip
            direction = rotation @ tip
            angle = math.degrees(math.atan2(direction[1], direction[0]))
            rotation = maths.rot_z(math.radians((80 if left else 100) - angle))[:3, :3] @ rotation
    grip = maths.anchor_block(entry["geometry"]["anchorPixels"], entry["geometry"]["canvas"])
    # A forward, inward display position leaves room for the blade without crossing the reticle.
    centered = entry["kind"] in ("bow", "crossbow") or entry["assetId"] == "twinblade"
    palm = np.array([.12 if left else -.12, .48 if centered else .16, -.45])
    limit = min(old["scale"][0], 1.45 * entry["geometry"]["canvas"][0]
                / entry["geometry"]["sourceSpanPixels"])

    def frame(scale):
        matrix = np.eye(4)
        matrix[:3, :3] = rotation * scale
        matrix[:3, 3] = palm - rotation @ (scale * grip)
        return maths.raw_from_applied(matrix, left)[0]

    def fits(candidate):
        for aspect in (4/3, 16/10, 16/9, 21/9):
            x0, y0, x1, y1, z = bounds(candidate, points, left, aspect)
            if z >= -.10 or y0 < -.76 or y1 > .88:
                return False
            if (left and (x0 < -.92 or x1 > -.03)) or (not left and (x0 < .03 or x1 > .92)):
                return False
        return True

    lo, hi = .01, limit
    if not fits(frame(lo)):
        raise ValueError("Invalid framing position: " + entry["id"])
    for _ in range(32):
        mid = (lo + hi) / 2
        if fits(frame(mid)):
            lo = mid
        else:
            hi = mid
    result = frame(lo * .98)  # Small margin for float rounding and resting hand motion.
    if not fits(result):
        raise ValueError("Framing failed: " + entry["id"])
    landed = maths.raw_pose(result, left) @ np.append(grip, 1)
    if np.max(np.abs(landed[:3] - palm)) > 1e-6:
        raise ValueError("Grip moved within weapon: " + entry["id"])
    return result


def preview(entries, before):
    names = ["great_sword", "curved_sword", "great_spear", "great_bow",
             "malikeths_black_blade", "starscourge_greatsword", "morgotts_cursed_sword", "ballista"]
    panel_w, panel_h = 480, 300
    sheet = Image.new("RGB", (panel_w * 2, (panel_h + 28) * len(names) + 36), "#151b23")
    draw = ImageDraw.Draw(sheet)
    draw.text((10, 9), "MATHEMATICAL HAND PROJECTION - NOT AN IN-GAME SCREENSHOT", fill="white")
    for row, name in enumerate(names):
        entry = entries[name]
        path = next(iter(entry["held"].values()))
        sprite = np.asarray(Image.open(ROOT / "art/equipment-2d/full" / path).convert("RGBA"))
        w, h = entry["geometry"]["canvas"]
        for col, frame in enumerate((before[name][CONTEXTS[0]], entry["display"][CONTEXTS[0]])):
            x, y = col * panel_w, 36 + row * (panel_h + 28)
            draw.text((x + 8, y + 4), name + (" / BEFORE" if col == 0 else " / AFTER"), fill="white")
            y += 28
            draw.rectangle((x, y, x + panel_w - 1, y + panel_h - 1), fill="#293843")
            draw.line((x+panel_w/2-6, y+panel_h/2, x+panel_w/2+6, y+panel_h/2), fill="#a0acb5")
            draw.line((x+panel_w/2, y+panel_h/2-6, x+panel_w/2, y+panel_h/2+6), fill="#a0acb5")
            panel = Image.new("RGBA", (panel_w, panel_h))
            pd = ImageDraw.Draw(panel)
            for py, px in zip(*np.nonzero(sprite[:, :, 3] > 0)):
                corners = np.array([[px/w-.5, .5-py/h, 0], [(px+1)/w-.5, .5-py/h, 0],
                                    [(px+1)/w-.5, .5-(py+1)/h, 0], [px/w-.5, .5-(py+1)/h, 0]])
                camera = camera_points(frame, corners, False)
                if (camera[:, 2] >= -.05).any():
                    continue
                ndc = project(camera, panel_w / panel_h)
                xy = [(float((a+1)*panel_w/2), float((1-b)*panel_h/2)) for a, b in ndc]
                pd.polygon(xy, fill=tuple(int(v) for v in sprite[py, px]))
            sheet.paste(panel, (x, y), panel)
    sheet.save(OUT / "projection-before-after.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    routes, overrides = read(ROUTES), read(OVERRIDES)
    for path in (ROUTES, OVERRIDES):
        backup = OUT / (path.name + ".before")
        if not backup.exists():
            backup.write_bytes(path.read_bytes())
    baseline = read(OUT / (ROUTES.name + ".before"))
    baseline_displays = {e["assetId"]: e["display"] for e in baseline["equipment"]}
    before = {}
    checks = []
    entries = {}
    for entry in routes["equipment"]:
        if entry["route"] != "own_sprite" or entry["kind"] not in ("melee", "bow", "crossbow"):
            continue
        name = entry["assetId"]
        before[name] = baseline_displays[name]
        entries[name] = entry
        points = np.concatenate([pixel_points(entry, ROOT / "art/equipment-2d/full" / value)
                                 for value in entry["held"].values()])
        override = overrides["overrides"].setdefault(name, {})
        contexts = override.setdefault("contexts", {})
        for left, context in enumerate(CONTEXTS):
            candidate = fit(entry, points, bool(left))
            contexts[context] = candidate
            entry["display"][context] = candidate
            checks.append({"id": entry["id"], "context": context, "before": before[name][context],
                           "after": candidate, "boundsByAspect": {
                               str(a): bounds(candidate, points, bool(left), a)
                               for a in (4/3, 16/10, 16/9, 21/9)}})
        override["accepted"] = False
        override["note"] = "2026-10-18 first-person framing candidate; world/third-person unchanged; requires game visual acceptance"
        entry["acceptance"]["humanAccepted"] = False
        entry["inGameAccepted"] = False
    preview(entries, before)
    routes["firstPersonPresentation"] = {
        "date": "2026-10-18", "status": "projection_checked_runtime_pending",
        "tool": "tools/equipment2d/calibrate-first-person.py",
        "scope": "own weapons, first-person only; no world/third-person/GUI/artwork change",
        "humanAccepted": False,
    }
    write(OUT / "projection-checks.json", {"inGameAccepted": False, "restingPoseOnly": True,
                                           "handFov": 70, "checks": checks})
    write(OVERRIDES, overrides)
    write(ROUTES, routes)
    print("FRAMING_PROJECTION_PASS: %d weapons, %d hand contexts; no PNG/world/third-person edits" % (len(entries), len(checks)))


if __name__ == "__main__":
    main()
