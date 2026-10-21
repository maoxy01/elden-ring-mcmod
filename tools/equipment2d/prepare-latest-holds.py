"""Prepare latest-model grip workbenches using vanilla palm references.

Preserves authored proportions, native source projects and per-item blade roll.
Arm-mounted fist/claw bypass these item displays when their resources load.
"""
import copy
import hashlib
import importlib.util
import json
import statistics
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "art/equipment-2d/blockbench-holds-20261021"
NATIVE = ROOT / "art/equipment-2d/blockbench-all-items-20261018"
FULL = ROOT / "art/equipment-2d/full"
ROUTES = ROOT / "src/main/resources/assets/eldencombat/routes/2d.json"
spec = importlib.util.spec_from_file_location("display_math", Path(__file__).with_name("build-display-candidates.py"))
maths = importlib.util.module_from_spec(spec)
spec.loader.exec_module(maths)


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def palm(frame, grip, left):
    return maths.raw_pose(frame, left) @ np.r_[grip, 1]


def prepare_displays():
    baseline = OUT / "backup/src/main/resources/assets/eldencombat/routes/2d.json"
    old = {e["assetId"]: e for e in read(baseline)["equipment"] if e.get("route") == "own_sprite"}
    assets = {a["id"]: a for a in read(NATIVE / "manifest.json")["assets"]}
    overrides = read(OUT / "backup/art/equipment-2d/full/display-overrides.json")
    report = []
    for name, asset in assets.items():
        entry = old[name]
        contexts = overrides["overrides"].setdefault(name, {}).setdefault("contexts", {})
        for left, context in enumerate(("firstperson_righthand", "firstperson_lefthand")):
            frame = copy.deepcopy(entry["display"][context])
            ratio = entry["geometry"]["sourceSpanPixels"] / asset["sourceSpanPixels"]
            frame["scale"][:2] = [s * ratio for s in frame["scale"][:2]]
            grip = maths.anchor_block(asset["anchorPixels"], entry["geometry"]["canvas"])
            old_grip = maths.anchor_block(entry["geometry"]["anchorPixels"], entry["geometry"]["canvas"])
            target = palm(entry["display"][context], old_grip, bool(left))[:3]
            family = maths.family_of(name)
            reference = "preserved authored shield/flask/fallback palm"
            if name not in ("claw", "fist", "test_flask") and family != "shield":
                ref = maths.REFERENCES[family]
                ref_frame = maths.context_spec(ref["display"], context, "lefthand" if left else "righthand")
                reference_grip = maths.anchor_block(ref["anchorPixels"], [16, 16]) + np.array(ref["gripOffsetBlocks"])
                target = palm(ref_frame, reference_grip, bool(left))[:3]
                reference = ref["source"]
            # Store the left-handed translation directly; avoid a second local X mirror.
            linear = maths.raw_pose(frame, bool(left))[:3, :3]
            translation = target - linear @ grip
            frame["translation"] = [( -1 if left else 1) * translation[0] * 16,
                                    translation[1] * 16, translation[2] * 16]
            contexts[context] = frame
            error = np.linalg.norm(palm(frame, grip, bool(left))[:3] - target)
            assert error < 1e-10
            report.append(dict(id=name, context=context, palmErrorBlocks=float(error),
                               reference=reference, sizeRatio=float(ratio), bladeRotationPreserved=True))
        overrides["overrides"][name].update(accepted=False,
            note="Latest 20261021 model; vanilla palm reference, authored blade roll; game review pending",
            latestModelCalibration=dict(sourceModel=asset["model"], sourceSha256=asset["modelSha256"],
                                        sourceRoot=str(NATIVE.relative_to(ROOT)).replace("\\", "/")))
    write(FULL / "display-overrides.json", overrides)
    # The existing generator resolves world length and all non-first-person contexts.
    maths.main()
    route_spec = importlib.util.spec_from_file_location("route_generation", Path(__file__).with_name("build-route-config.py"))
    generator = importlib.util.module_from_spec(route_spec)
    route_spec.loader.exec_module(generator)
    current = read(ROUTES)
    generator.main()
    generated = read(ROUTES)
    # Preserve extra route metadata/VFX fields that the historical generator does not retain.
    current["equipment"] = generated["equipment"]
    current["equipmentAdmission"] = generated["equipmentAdmission"]
    current["firstPersonPresentation"] = dict(date="2026-10-21", status="latest_models_native_review_pending",
        source=str(OUT.relative_to(ROOT)).replace("\\", "/"), humanAccepted=False, runtimeAccepted=False,
        policy="Vanilla palm references, per-weapon size and blade direction; fist/claw attach to arms")
    write(ROUTES, current)
    write(OUT / "palm-verification.json", dict(contexts=report, runtimeAccepted=False))


def workbenches():
    entries = {e["assetId"]: e for e in read(ROUTES)["equipment"] if e.get("route") == "own_sprite"}
    projects, native_models = [], []
    for asset in read(NATIVE / "manifest.json")["assets"]:
        name = asset["id"]
        if name in ("claw", "fist"):
            source = ROOT / "art/equipment-2d/worn-hand-gear-20261021" / (name + "_worn.bbmodel")
            target = OUT / "projects" / (name + ".bbmodel")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read_bytes())
            projects.append(dict(id=name, kind="arm_mount", path=str(target.relative_to(ROOT))))
        original = read(NATIVE / asset["model"])
        groups = {g["uuid"]: g for g in original["groups"]}
        elements = {e["uuid"]: e for e in original["elements"]}
        for index, state in enumerate(asset["variants"]):
            selected = []
            def visit(node, active=False):
                if isinstance(node, str):
                    if active and elements[node].get("export", True): selected.append(elements[node])
                    return
                group = groups[node["uuid"]]
                assert group.get("rotation", [0, 0, 0]) == [0, 0, 0]
                for child in node.get("children", []): visit(child, active or group["name"] == state["state"])
            for node in original["outliner"]: visit(node)
            assert selected, (name, state["state"])
            w, h = state["size"]
            assert w == h
            # The handoff projection describes the pre-normalized source, not every
            # current native cube. Recover its actual pixel scale from the authored UVs.
            unit = statistics.median((c["to"][0] - c["from"][0]) /
                abs(c["faces"]["south"]["uv"][2] - c["faces"]["south"]["uv"][0]) for c in selected)
            for cube in selected:
                uv = cube["faces"]["south"]["uv"]
                expected_from = [(min(uv[0], uv[2]) - asset["anchorPixels"][0]) * unit,
                                 (asset["anchorPixels"][1] - max(uv[1], uv[3])) * unit]
                expected_to = [(max(uv[0], uv[2]) - asset["anchorPixels"][0]) * unit,
                               (asset["anchorPixels"][1] - min(uv[1], uv[3])) * unit]
                assert max(abs(cube[key][axis] - expected[axis]) for key, expected in
                    (("from", expected_from), ("to", expected_to)) for axis in (0, 1)) < .0001, (name, state["state"])
            factor = 16 / (unit * w)
            anchor = np.array([16 * asset["anchorPixels"][0] / w, 16 * (1 - asset["anchorPixels"][1] / h), 8])
            converted = copy.deepcopy(selected)
            for cube in converted:
                assert cube.get("rotation", [0, 0, 0]) == [0, 0, 0]
                for key in ("from", "to", "origin"):
                    if key in cube: cube[key] = (np.array(cube[key]) * [factor, factor, 1] + anchor).tolist()
                for face in cube["faces"].values(): face["texture"] = 0
            # Some delivered multi-state cubes retain face index 0. Bind the
            # explicitly named state texture rather than silently reusing standby.
            texture_index = next(i for i, t in enumerate(original["textures"])
                                 if t["name"] == name + "__" + state["state"] + ".png")
            texture = copy.deepcopy(original["textures"][texture_index])
            project = dict(meta=dict(format_version="5.0", model_format="java_block", box_uv=False),
                name="Latest_Hold_" + name + "_" + state["state"], java_block_version="1.21.1",
                resolution=dict(width=w, height=h), elements=converted,
                outliner=[e["uuid"] for e in converted], textures=[texture], display=entries[name]["display"],
                unhandled_root_fields=dict(hold_source=dict(id=name, state=state["state"],
                    sourceModel=asset["model"], sourceModelSha256=asset["modelSha256"],
                    textureSha256=state["sha256"], canvas=[w, h], anchorPixels=asset["anchorPixels"],
                    modelToItemBoxScaleXY=factor, runtimeAccepted=False)))
            stem = name if index == 0 else name + "__" + state["state"]
            # Preserve saved native Display edits when refreshing geometry conversion.
            saved_export = OUT / "exports" / (stem + ".json")
            if saved_export.exists():
                project["display"] = read(saved_export)["display"]
            path = OUT / "projects" / (stem + ".bbmodel")
            write(path, project)
            if name not in ("claw", "fist"):
                projects.append(dict(id=name, state=state["state"], kind="held", path=str(path.relative_to(ROOT))))
            else:
                write(OUT / "fallbacks" / (stem + ".bbmodel"), project)
                path.write_bytes((ROOT / "art/equipment-2d/worn-hand-gear-20261021" / (name + "_worn.bbmodel")).read_bytes())
            if entries[name]["kind"] != "shield":
                exported = []
                for cube in converted:
                    faces = {face: dict(uv=[v * 16 / w for v in values["uv"]], texture="#layer0")
                             for face, values in cube["faces"].items()}
                    exported.append({"from": cube["from"], "to": cube["to"], "faces": faces})
                model_path = FULL / "native-held-models" / (stem + ".json")
                write(model_path, dict(gui_light="front", ambientocclusion=False,
                    textures=dict(layer0="eldencombat:item/2d/" + name + "_" + state["state"]), elements=exported))
                native_models.append(dict(id="eldencombat:" + name, state=state["state"],
                    file=str(model_path.relative_to(FULL)).replace("\\", "/"), sha256=hashlib.sha256(model_path.read_bytes()).hexdigest(),
                    sourceModel=asset["model"], sourceModelSha256=asset["modelSha256"],
                    textureFile=entries[name]["held"][state["state"]], textureSha256=state["sha256"]))
    write(OUT / "workbenches.json", dict(projects=projects, sourceProjectsChanged=False, runtimeAccepted=False))
    write(FULL / "native-held-models.json", dict(source="ART-MODELS-LATEST-20261021.md", models=native_models,
          policy="Latest authored blade/handle geometry; shields keep existing double-material contour models"))
    print("Latest-model workbenches:", len(projects), "including two arm-mounted originals")


if __name__ == "__main__":
    prepare_displays()
    workbenches()
