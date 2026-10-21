"""Consume the saved native Display exports, without fitting or inventing new poses."""
import base64
import hashlib
import io
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
FULL = ROOT / "art/equipment-2d/full"
WORK = ROOT / "art/equipment-2d/blockbench-holds-20261019"


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def pixels(data):
    image = Image.open(io.BytesIO(data)).convert("RGBA")
    return image.size, image.tobytes()


def main():
    selection = read(WORK / "selection.json")
    capture = read(WORK / "capture-validation.json")
    if capture["baseCaptures"] != 112 or capture["dynamicCaptures"] != 28:
        raise ValueError("Complete native captures are required before synchronization")
    overrides = read(FULL / "display-overrides.json")
    manifest = read(FULL / "equipment-42.json")
    assets = {a["id"]: a for a in manifest["assets"]}
    protection = read(FULL / "selected-art.json")
    protected = {a["id"]: a for a in protection["assets"]}
    report = []
    for asset in read(ROOT / "art/equipment-2d/blockbench-all-items-20261018/manifest.json")["assets"]:
        name = asset["id"]
        path = WORK / "projects" / (name + ".bbmodel")
        model = read(path)
        expected = (FULL / assets[name]["variants"][0]["file"]).read_bytes()
        if pixels(base64.b64decode(model["textures"][0]["source"].split(",", 1)[1])) != pixels(expected):
            raise ValueError("Saved native project has stale artwork: " + name)
        exported = read(WORK / "exports" / (name + ".json"))
        for context in ("firstperson_righthand", "firstperson_lefthand"):
            for field, default in (("rotation", [0, 0, 0]), ("translation", [0, 0, 0]), ("scale", [1, 1, 1])):
                saved = model["display"][context].get(field, default)
                values = exported["display"][context].get(field, default)
                if any(abs(a - b) > 1e-4 for a, b in zip(saved, values)):
                    raise ValueError("Native display changed; save and re-export Java JSON before syncing: " + name)
        override = overrides["overrides"].setdefault(name, {})
        contexts = override.setdefault("contexts", {})
        for context in ("firstperson_righthand", "firstperson_lefthand"):
            values = exported["display"][context]
            contexts[context] = {"rotation": values.get("rotation", [0, 0, 0]),
                                 "translation": values.get("translation", [0, 0, 0]),
                                 "scale": values.get("scale", [1, 1, 1])}
        override.update(accepted=False, note="2026-10-19 native Blockbench Display; lower-corner carry; requires game review",
                        nativeCalibration={"project": str(path.relative_to(ROOT)).replace("\\", "/"),
                        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "source": "Native Display slots, no export-time recalibration"})
        if name not in protected:
            value = {"id": name, "worldLengthBlocks": asset["worldLengthBlocks"], "anchorPixels": asset["anchorPixels"],
                     "sourceSpanPixels": asset["sourceSpanPixels"], "files": [dict(f) for f in selection["files"] if f["id"] == name]}
            protection["assets"].append(value)
        report.append({"id": name, "project": override["nativeCalibration"], "contexts": 2,
                       "sourceArtworkPixelsMatch": True, "humanAccepted": False})
    write(FULL / "display-overrides.json", overrides)
    write(FULL / "selected-art.json", protection)
    routes_path = ROOT / "src/main/resources/assets/eldencombat/routes/2d.json"
    routes = read(routes_path)
    routes["firstPersonPresentation"] = {"date": "2026-10-19", "status": "native_blockbench_checked_game_pending",
        "tool": "tools/equipment2d/sync-blockbench-holds.py", "source": str(WORK.relative_to(ROOT)).replace("\\", "/"),
        "policy": "Lower-corner grip; weapon body visible; natural offscreen handle permitted",
        "humanAccepted": False, "runtimeAccepted": False}
    write(routes_path, routes)
    write(WORK / "sync-receipt.json", {"items": report, "contexts": 56, "runtimeAccepted": False,
                                      "gameStarted": False, "pngsRedrawn": 0})
    print("NATIVE_DISPLAY_SYNC: 28 items / 56 hand contexts, saved native exports consumed without recalibration")


if __name__ == "__main__":
    main()
