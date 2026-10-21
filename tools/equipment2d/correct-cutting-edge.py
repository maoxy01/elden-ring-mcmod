"""Correct only first-person single-edge displays around the authored grip/tip axis.

Reuses the vanilla ItemTransform math in build-display-candidates.py. This is a
parameter correction, not a native Blockbench capture or in-game acceptance.
"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "art/equipment-2d/edge-facing-20261020"
ROUTES = ROOT / "src/main/resources/assets/eldencombat/routes/2d.json"
OVERRIDES = ROOT / "art/equipment-2d/full/display-overrides.json"
spec = importlib.util.spec_from_file_location("display_math", Path(__file__).with_name("build-display-candidates.py"))
maths = importlib.util.module_from_spec(spec)
spec.loader.exec_module(maths)
# Authored tip pixels, checked against the admitted sprites. On these five
# blades the cutting edge is the lower/right side of the grip-to-tip line.
TIPS = {"curved_sword": [27.5, 7.5], "curved_greatsword": [28.5, 6.5],
        "hand_of_malenia": [60.5, 4.5], "morgotts_cursed_sword": [59.5, 9.5],
        "malikeths_black_blade": [56.5, 2.5]}


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def roll(axis, angle):
    x, y, z = axis
    skew = np.array([[0, -z, y], [z, 0, -x], [-y, x, 0]])
    return np.eye(3) * np.cos(angle) + (1 - np.cos(angle)) * np.outer(axis, axis) + np.sin(angle) * skew


def correct(entry, frame, left):
    geometry = entry["geometry"]
    grip = maths.anchor_block(geometry["anchorPixels"], geometry["canvas"])
    tip = maths.anchor_block(TIPS[entry["assetId"]], geometry["canvas"])
    axis = tip - grip
    axis /= np.linalg.norm(axis)
    edge = np.array([axis[1], -axis[0], 0.])
    before = maths.raw_pose(frame, left)
    rotation = maths.normalized_rotation(before)[:3, :3]
    palm = before[:3, :3] @ grip + before[:3, 3]
    sign = -1 if left else 1
    midpoint = (before @ np.r_[(grip + tip) / 2, 1])[:3]
    midpoint += np.array([sign * .56, -.52, -.72])
    view_ray = midpoint / np.linalg.norm(midpoint)
    # In camera space +Z points back at the player. Require the edge to face
    # away and laterally outside the wielding hand, while keeping a broad face.
    angles = sorted(range(-1800, 1801), key=abs)
    for step in angles:
        updated = rotation @ roll(axis, np.deg2rad(step / 10))
        direction = updated @ edge
        face_visibility = abs(np.dot(updated[:, 2], view_ray))
        # A world-space edge test alone can leave the left-hand sprite almost
        # edge-on to the camera. Keep at least half of its face projection.
        # An already outward baseline (black blade right hand) stays untouched.
        if (direction[2] <= -.03 and sign * direction[0] >= .75
                and (step == 0 or face_visibility >= .5)):
            break
    else:
        raise ValueError("No outward edge solution: " + entry["assetId"])
    if step == 0:
        result = copy.deepcopy(frame)
    else:
        matrix = np.eye(4)
        matrix[:3, :3] = updated @ np.diag(frame["scale"])
        matrix[:3, 3] = palm - matrix[:3, :3] @ grip
        # Store MC's actual left-handed Euler convention explicitly. The old
        # raw_from_applied helper conjugates local X too, which would not
        # preserve an asymmetric sprite's local coordinates for this operation.
        angles = maths.euler_xyz(np.block([[updated, np.zeros((3, 1))], [np.zeros((1, 3)), np.ones((1, 1))]]))
        result = dict(rotation=[angles[0], sign * angles[1], sign * angles[2]],
                      translation=[sign * matrix[0, 3] * 16, matrix[1, 3] * 16, matrix[2, 3] * 16],
                      scale=copy.deepcopy(frame["scale"]))
    after = maths.raw_pose(result, left)
    error = max(np.linalg.norm((after @ np.r_[point, 1]) - (before @ np.r_[point, 1])) for point in (grip, tip))
    assert error < 1e-8, (entry["assetId"], error)
    assert result["scale"] == frame["scale"] and np.linalg.det(updated) > .999999
    actual_edge = maths.normalized_rotation(after)[:3, :3] @ edge
    assert actual_edge[2] <= -.03 + 1e-9 and sign * actual_edge[0] >= .75 - 1e-9
    actual_visibility = abs(np.dot(maths.normalized_rotation(after)[:3, 2], view_ray))
    assert step == 0 or actual_visibility >= .5 - 1e-9
    return result, dict(rollDegrees=step / 10, gripTipErrorBlocks=float(error),
                       edgeBefore=(rotation @ edge).tolist(), edgeAfter=actual_edge.tolist(),
                       faceVisibilityCosine=float(actual_visibility))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    baseline = OUT / "routes-before.json"
    # Re-running uses the recorded baseline; never accumulate another rotation.
    if not baseline.exists():
        baseline.write_bytes(ROUTES.read_bytes())
        (OUT / "overrides-before.json").write_bytes(OVERRIDES.read_bytes())
    original = read(baseline)
    routes = read(ROUTES)
    overrides = read(OVERRIDES)
    entries = {e["assetId"]: e for e in routes["equipment"] if e.get("assetId") in TIPS}
    old = {e["assetId"]: e for e in original["equipment"] if e.get("assetId") in TIPS}
    checks = []
    for name, entry in entries.items():
        model = read(ROOT / "art/equipment-2d/blockbench-holds-20261019/projects" / (name + ".bbmodel"))
        for left, context in enumerate(("firstperson_righthand", "firstperson_lefthand")):
            before = old[name]["display"][context]
            after, check = correct(entry, before, bool(left))
            entry["display"][context] = after
            overrides["overrides"][name]["contexts"][context] = after
            model["display"][context] = after
            checks.append(dict(id=name, context=context, before=before, after=after, **check))
        source = {"project": str((OUT / (name + ".bbmodel")).relative_to(ROOT)).replace("\\", "/"),
                  "method": "Analytical axial roll; native Display and game review pending",
                  "replacesFirstPersonNativeCalibration": True, "tipPixels": TIPS[name]}
        override = overrides["overrides"][name]
        override.update(accepted=False, edgeFacingCalibration=source,
                        note="2026-10-20: outward cutting edge; grip, tip and scale preserved; game review pending")
        model["name"] = "edge_facing_" + name
        write(OUT / (name + ".bbmodel"), model)
    # Strict diff allowlist: runtime edit is exactly the ten first-person frames.
    comparison = copy.deepcopy(routes)
    for entry in comparison["equipment"]:
        if entry.get("assetId") in old:
            for context in ("firstperson_righthand", "firstperson_lefthand"):
                entry["display"][context] = old[entry["assetId"]]["display"][context]
    assert comparison == original, "Unexpected runtime change since baseline"
    write(OVERRIDES, overrides)
    write(ROUTES, routes)
    report = dict(sourceRoutesSha256=hashlib.sha256(baseline.read_bytes()).hexdigest(),
                  routesSha256=hashlib.sha256(ROUTES.read_bytes()).hexdigest(), checks=checks,
                  sourceArtworkChanged=False, nonFirstPersonChanged=False,
                  nativeDisplayAccepted=False, runtimeAccepted=False, humanAccepted=False)
    write(OUT / "verification.json", report)
    print(json.dumps(dict(contexts=len(checks), changed=sum(c["before"] != c["after"] for c in checks),
                          maxGripTipError=max(c["gripTipErrorBlocks"] for c in checks))))


if __name__ == "__main__":
    main()
