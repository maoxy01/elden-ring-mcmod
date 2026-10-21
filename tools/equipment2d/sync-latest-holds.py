"""Admit saved native Blockbench Display exports, without recomputing their poses."""
import base64
import hashlib
import importlib.util
import io
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "art/equipment-2d/blockbench-holds-20261021"
FULL = ROOT / "art/equipment-2d/full"
ROUTES = ROOT / "src/main/resources/assets/eldencombat/routes/2d.json"
CONTEXTS = ("firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand")


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def pixels(data):
    im = Image.open(io.BytesIO(data)).convert("RGBA")
    return im.size, im.tobytes()


def main():
    captured = {item["id"]: item for item in read(WORK / "native-review.json")["items"]}
    expected = [Path(p["path"]).stem for p in read(WORK / "workbenches.json")["projects"] if p["kind"] == "held"]
    assert set(captured) == set(expected), "Complete native review required"
    overrides = read(FULL / "display-overrides.json")
    native_root = ROOT / "art/equipment-2d/blockbench-all-items-20261018"
    assets = {a["id"]: a for a in read(native_root / "manifest.json")["assets"]}
    receipt = []
    for stem in expected:
        project_path = WORK / "projects" / (stem + ".bbmodel")
        model = read(project_path)
        export = read(WORK / "exports" / (stem + ".json"))
        source = model["unhandled_root_fields"]["hold_source"]
        asset = assets[source["id"]]
        assert hashlib.sha256((native_root / asset["model"]).read_bytes()).hexdigest() == source["sourceModelSha256"]
        state = next(v for v in asset["variants"] if v["state"] == source["state"])
        assert pixels(base64.b64decode(model["textures"][0]["source"].split(",", 1)[1])) == pixels((native_root / state["file"]).read_bytes()), stem
        assert len(captured[stem]["captures"]) == 4
        for capture in captured[stem]["captures"]:
            assert Path(capture["path"]).is_file() and capture["visible"] > 12
        for context in CONTEXTS:
            for field, default in (("rotation", [0, 0, 0]), ("translation", [0, 0, 0]), ("scale", [1, 1, 1])):
                assert max(abs(a-b) for a, b in zip(model["display"][context].get(field, default),
                    export["display"][context].get(field, default))) < .0001, (stem, context, field)
        if "__" in stem:
            base_export = read(WORK / "exports" / (source["id"] + ".json"))
            for context in CONTEXTS:
                for field, default in (("rotation", [0, 0, 0]), ("translation", [0, 0, 0]), ("scale", [1, 1, 1])):
                    assert max(abs(a-b) for a, b in zip(export["display"][context].get(field, default),
                        base_export["display"][context].get(field, default))) < .0001, (stem, context, field)
        else:
            override = overrides["overrides"].setdefault(stem, {})
            override["contexts"] = {**override.get("contexts", {}), **{c: {
                field: export["display"][c].get(field, default) for field, default in
                (("rotation", [0, 0, 0]), ("translation", [0, 0, 0]), ("scale", [1, 1, 1]))} for c in CONTEXTS}}
            override.update(accepted=False, nativeCalibration={
                "project": str(project_path.relative_to(ROOT)).replace("\\", "/"),
                "sha256": hashlib.sha256(project_path.read_bytes()).hexdigest(),
                "sourceModelSha256": source["sourceModelSha256"],
                "source": "Blockbench native Display slots; no export-time recalibration"})
        receipt.append(dict(id=stem, contexts=4, sourcePixelsMatch=True, sourceModelMatches=True))
    write(FULL / "display-overrides.json", overrides)
    for name in ("build-display-candidates.py", "build-route-config.py"):
        spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name))
        generator = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(generator)
        if name.startswith("build-route"):
            previous = read(ROUTES)
        generator.main()
        if name.startswith("build-route"):
            generated = read(ROUTES)
            previous["equipment"] = generated["equipment"]
            previous["equipmentAdmission"] = generated["equipmentAdmission"]
            previous["firstPersonPresentation"] = dict(date="2026-10-21", status="native_blockbench_checked_game_pending",
                source=str(WORK.relative_to(ROOT)).replace("\\", "/"), humanAccepted=False, runtimeAccepted=False,
                policy="Native Display exports; per-item grip; arm-mounted claw/fist; no automatic game launch")
            write(ROUTES, previous)
    write(WORK / "sync-receipt.json", dict(items=receipt, contexts=len(receipt)*4, gameStarted=False,
        runtimeAccepted=False, humanAccepted=False, pngsRedrawn=0))
    print(f"NATIVE_SYNC: {len(receipt)} native projects / {len(receipt)*4} contexts; game review pending")


if __name__ == "__main__":
    main()
