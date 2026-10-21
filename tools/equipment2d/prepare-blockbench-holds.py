"""Stage the reviewed native artwork and independent Java-item display workbenches."""
import base64
import copy
import hashlib
import importlib.util
import json
import shutil
import uuid
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
FULL = ROOT / "art/equipment-2d/full"
NATIVE = ROOT / "art/equipment-2d/blockbench-all-items-20261018"
OUT = ROOT / "art/equipment-2d/blockbench-holds-20261019"
spec = importlib.util.spec_from_file_location("display_math", Path(__file__).with_name("build-display-candidates.py"))
maths = importlib.util.module_from_spec(spec)
spec.loader.exec_module(maths)


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def stage():
    if (OUT / "selection.json").exists():
        raise RuntimeError("Working selection already exists; do not replace its backup")
    manifest, full = read(NATIVE / "manifest.json"), read(FULL / "equipment-42.json")
    selected = {a["id"]: a for a in full["assets"]}
    for asset in manifest["assets"]:
        for state in asset["variants"] + asset["gui"]:
            if sha((NATIVE / state["file"]).read_bytes()) != state["sha256"]:
                raise RuntimeError("Native export changed: " + state["file"])
    controls = [FULL / name for name in ("equipment-42.json", "publishable-assets.json", "selected-art.json",
                                        "display-candidates.json", "display-overrides.json")]
    controls += [ROOT / "src/main/resources/assets/eldencombat/routes/2d.json"]
    for path in controls + list((FULL / "sprites").glob("*.png")) + list((ROOT / "art/item-thickness").rglob("*.json")):
        destination = OUT / "backup" / path.relative_to(ROOT)
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, destination)
    changes = []
    for asset in manifest["assets"]:
        current = selected[asset["id"]]
        existing = {s["state"]: s for s in current["variants"] + current["gui"]}
        for state in asset["variants"] + asset["gui"]:
            target = existing[state["state"]]
            path = FULL / target["file"]
            changes.append({"id": asset["id"], "state": state["state"],
                            "file": str(path.relative_to(ROOT)).replace("\\", "/"),
                            "previousSha256": sha(path.read_bytes()), "sha256": state["sha256"]})
            shutil.copy2(NATIVE / state["file"], path)
            for key in ("sha256", "size", "boundsInclusive", "opaquePixels", "colors", "components8",
                        "transparentMargin", "semiTransparentPixels", "anchorPixels"):
                if key in state:
                    target[key] = copy.deepcopy(state[key])
        for key in ("worldLengthBlocks", "ratioToPlayer", "anchorPixels", "sourceSpanPixels", "projection", "suggestedDisplay"):
            current[key] = copy.deepcopy(asset[key])
        current["artRevision"] = {"date": "2026-10-19", "source": str(NATIVE.relative_to(ROOT)).replace("\\", "/"),
                                  "scope": "Existing native master exports for hold calibration; no artwork redrawn",
                                  "humanAccepted": False, "inGameAccepted": False}
    whitelist = read(FULL / "publishable-assets.json")
    for entry in whitelist["include"]:
        entry["sha256"] = sha((FULL / entry["file"]).read_bytes())
    protection = read(FULL / "selected-art.json")
    by_path = {e["file"]: e for e in changes}
    for asset in protection["assets"]:
        for entry in asset["files"]:
            change = by_path.get(entry["file"])
            if change:
                entry["previousSha256"] = entry["sha256"]
                entry["sha256"] = change["sha256"]
    receipt = {"date": "2026-10-19", "authorization": {"quote": "那就这样搞",
               "scope": "Latest models plus native Blockbench display calibration; game acceptance still pending"},
               "basis": "28-item native master and its 94 hash-verified exports; divergent independent Morgott excluded",
               "nativeMasterSha256": sha((NATIVE / "all-items-optimized.bbmodel").read_bytes()),
               "humanAccepted": False, "inGameAccepted": False, "files": changes}
    protection.setdefault("selectionHistory", []).append({"authorization": receipt["authorization"],
        "receipt": str((OUT / "selection.json").relative_to(ROOT)).replace("\\", "/"),
        "backup": str((OUT / "backup").relative_to(ROOT)).replace("\\", "/")})
    write(FULL / "equipment-42.json", full)
    write(FULL / "publishable-assets.json", whitelist)
    write(FULL / "selected-art.json", protection)
    write(OUT / "selection.json", receipt)
    print("STAGED_NATIVE_ART: 28 items / 94 unchanged native PNG exports; previous full and controls backed up")


def item_elements(sprite):
    image = np.asarray(Image.open(sprite).convert("RGBA"))
    h, w = image.shape[:2]
    used, elements = set(), []
    for y in range(h):
        for x in range(w):
            if not image[y, x, 3] or (x, y) in used:
                continue
            width = 1
            while x + width < w and image[y, x + width, 3] and (x + width, y) not in used:
                width += 1
            height = 1
            while y + height < h and all(image[y + height, xx, 3] and (xx, y + height) not in used
                                         for xx in range(x, x + width)):
                height += 1
            used.update((xx, yy) for yy in range(y, y + height) for xx in range(x, x + width))
            faces = {name: {"uv": [x, y, x + width, y + height], "texture": 0}
                     for name in ("north", "south", "east", "west", "up", "down")}
            faces["north"]["uv"] = [x + width, y, x, y + height]
            for name in ("east", "west", "up", "down"):
                faces[name]["uv"] = [x, y, x + 1, y + 1]
            elements.append({"name": "sprite_rectangle_" + str(len(elements)), "uuid": str(uuid.uuid4()),
                             "type": "cube", "box_uv": False, "autouv": 0,
                             "from": [16*x/w, 16*(h-y-height)/h, 7.5],
                             "to": [16*(x+width)/w, 16*(h-y)/h, 8.5], "faces": faces})
    return elements, w, h


def workbenches():
    routes = read(ROOT / "src/main/resources/assets/eldencombat/routes/2d.json")
    entries = {e["assetId"]: e for e in routes["equipment"] if e["route"] == "own_sprite"}
    for asset in read(NATIVE / "manifest.json")["assets"]:
        name = asset["id"]
        primary = asset["variants"][0]
        sprite = NATIVE / primary["file"]
        elements, w, h = item_elements(sprite)
        display = copy.deepcopy(entries[name]["display"])
        if entries[name]["kind"] in ("melee", "bow", "crossbow"):
            for left, context in enumerate(("firstperson_righthand", "firstperson_lefthand")):
                old = read(OUT / "backup/src/main/resources/assets/eldencombat/routes/2d.json")
                old_entry = next(e for e in old["equipment"] if e["assetId"] == name)
                old_frame = old_entry["display"][context]
                rotation = maths.normalized_rotation(maths.raw_pose(old_frame, bool(left))[:3, :3])[:3, :3]
                scale = abs(old_frame["scale"][0]) * old_entry["geometry"]["sourceSpanPixels"] / asset["sourceSpanPixels"] * .90
                centered = entries[name]["kind"] in ("bow", "crossbow") or name == "twinblade"
                palm = np.array([-.26 if left else .26, -.02 if centered else -.22, -.45])
                grip = maths.anchor_block(asset["anchorPixels"], [w, h])
                matrix = np.eye(4)
                matrix[:3, :3] = rotation @ np.diag([scale, scale, .68])
                matrix[:3, 3] = palm - matrix[:3, :3] @ grip
                display[context] = maths.raw_from_applied(matrix, bool(left))[0]
        model = {"meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
                 "name": "EC_Hold_" + name, "resolution": {"width": w, "height": h},
                 "elements": elements, "outliner": [e["uuid"] for e in elements], "display": display,
                 "textures": [{"name": sprite.name, "id": "0", "uuid": str(uuid.uuid4()),
                               "width": w, "height": h, "uv_width": w, "uv_height": h,
                               "source": "data:image/png;base64," + base64.b64encode(sprite.read_bytes()).decode()}],
                 "unhandled_root_fields": {"hold_source": {"id": name, "textureSha256": sha(sprite.read_bytes()),
                     "anchorPixels": asset["anchorPixels"], "canvas": [w, h], "worldLengthBlocks": asset["worldLengthBlocks"],
                     "nativeSource": str(NATIVE.relative_to(ROOT)).replace("\\", "/"), "inGameAccepted": False}}}
        if entries[name]["kind"] == "shield":
            back = next(v for v in asset["variants"] if v["state"] == "back")
            back_path = NATIVE / back["file"]
            texture = copy.deepcopy(model["textures"][0])
            texture.update(name=back_path.name, id="1", uuid=str(uuid.uuid4()),
                           source="data:image/png;base64," + base64.b64encode(back_path.read_bytes()).decode())
            model["textures"].append(texture)
            for element in model["elements"]:
                element["faces"]["north"]["texture"] = 1
        write(OUT / "prepared" / (name + ".bbmodel"), model)
    print("PREPARED_WORKBENCHES: 28 Java-item display copies; geometry follows existing alpha, source projects untouched")


def states():
    for asset in read(NATIVE / "manifest.json")["assets"]:
        if asset["id"] not in ("great_bow", "ballista"):
            continue
        for state in asset["variants"][1:]:
            model = read(OUT / "prepared" / (asset["id"] + ".bbmodel"))
            model["display"] = read(OUT / "exports" / (asset["id"] + ".json"))["display"]
            sprite = NATIVE / state["file"]
            elements, w, h = item_elements(sprite)
            model["elements"], model["outliner"] = elements, [e["uuid"] for e in elements]
            model["textures"][0].update(name=sprite.name, width=w, height=h,
                source="data:image/png;base64," + base64.b64encode(sprite.read_bytes()).decode())
            model["unhandled_root_fields"]["hold_source"].update(state=state["state"], textureSha256=sha(sprite.read_bytes()))
            write(OUT / "prepared-states" / (asset["id"] + "__" + state["state"] + ".bbmodel"), model)
    print("PREPARED_DYNAMIC_STATES: bow pulling and ballista loading/loaded use saved native display settings")


if __name__ == "__main__":
    import sys
    {"stage": stage, "workbenches": workbenches, "states": states}[sys.argv[1]]()
