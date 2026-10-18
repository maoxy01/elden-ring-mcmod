"""Package the bounded single-file compile and display calibration over the verified baseline."""
import hashlib
import json
import zipfile
from copy import copy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "build/firstperson-framing-20261018"
OUT = ROOT / "dist/firstperson-framing-20261018"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    baseline = WORK / "baseline.jar"
    if sha(baseline.read_bytes()) != "4d8ec7dd3d6d7de79d285dc474c407a01831877aac76203c12bdf5f893625338":
        raise ValueError("Unexpected baseline; re-audit before packaging")
    routes_path = ROOT / "src/main/resources/assets/eldencombat/routes/2d.json"
    routes = json.loads(routes_path.read_text(encoding="utf-8"))
    before = json.loads((WORK / "2d.json.before").read_text(encoding="utf-8"))
    changes = {}
    before_entries = {e["id"]: e for e in before["equipment"]}
    for entry in routes["equipment"]:
        old = before_entries[entry["id"]]
        candidate, original = json.loads(json.dumps(entry)), json.loads(json.dumps(old))
        for context in ("firstperson_righthand", "firstperson_lefthand"):
            if candidate["display"] and original["display"]:
                candidate["display"].pop(context, None)
                original["display"].pop(context, None)
        if candidate != original:
            raise ValueError("Unexpected route change outside first-person: " + entry["id"])
    for file in (WORK / "classes/com/dsh/eldencombat/client").glob("EquipmentRenderer*.class"):
        changes["com/dsh/eldencombat/client/" + file.name] = file.read_bytes()
    if "com/dsh/eldencombat/client/EquipmentRenderer.class" not in changes:
        raise ValueError("Compile the renderer before packaging")
    changes["assets/eldencombat/routes/2d.json"] = routes_path.read_bytes()
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / "eldencombat-0.2.0.jar"
    staging = OUT / "eldencombat-0.2.0.jar.partial"
    with zipfile.ZipFile(baseline) as source:
        for entry in routes["equipment"]:
            if entry["route"] != "own_sprite" or entry["kind"] not in ("melee", "bow", "crossbow"):
                continue
            path = "assets/eldencombat/models/item/2d/" + entry["assetId"] + ".json"
            model = json.loads(source.read(path))
            for context in ("firstperson_righthand", "firstperson_lefthand"):
                model["display"][context] = entry["display"][context]
            changes[path] = (json.dumps(model, ensure_ascii=False, indent=2) + "\n").encode()
        if set(changes) - set(source.namelist()):
            raise ValueError("Patch unexpectedly adds archive paths")
        with zipfile.ZipFile(staging, "w") as output:
            for item in source.infolist():
                output.writestr(copy(item), changes.get(item.filename, source.read(item)))
        with zipfile.ZipFile(staging) as check:
            if check.testzip() is not None or any("Harness" in name for name in check.namelist()):
                raise ValueError("Invalid release archive")
            different = [name for name in source.namelist() if source.read(name) != check.read(name)]
            if set(different) - set(changes):
                raise ValueError("Unexpected archive mutation")
            protected = [name for name in source.namelist() if name.endswith(".png")]
            if any(source.read(name) != check.read(name) for name in protected):
                raise ValueError("Artwork changed")
    staging.replace(target)
    receipt = {
        "date": "2026-10-18", "kind": "single-file-compile-and-resource-patch",
        "jarSha256": sha(target.read_bytes()), "baselineSha256": sha(baseline.read_bytes()),
        "changedEntries": different, "protectedPngsUnchanged": len(protected),
        "ownWeaponHandContexts": 48, "legacyFallback": "first-person only; compiled, runtime pending",
        "thirdPersonGuiWorldUnchanged": True, "fullGradleBuildRun": False,
        "newGameRun": False, "newJUnitRun": False, "humanAccepted": False,
    }
    (OUT / "RECEIPT.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (OUT / "SHA256SUMS.txt").write_text(receipt["jarSha256"] + "  eldencombat-0.2.0.jar\n", encoding="utf-8")
    print("PRESENTATION_PACKAGE_PASS " + receipt["jarSha256"] + "; unchanged PNGs=" + str(len(protected)))


if __name__ == "__main__":
    main()
