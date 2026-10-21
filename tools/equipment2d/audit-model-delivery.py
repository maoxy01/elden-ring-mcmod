"""Read-only comparison of native candidates, selected art and the installed JAR."""
import base64
import hashlib
import io
import json
import subprocess
import zipfile
from collections import Counter
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
NATIVE = ROOT / "art/equipment-2d/blockbench-all-items-20261018"
FULL = ROOT / "art/equipment-2d/full"
JAR = Path("D:/PCL/.minecraft/versions/Elden Combat 0.2.0 - Vanilla Combat/mods/eldencombat-0.2.0.jar")
OUT = ROOT / "build/model-delivery-audit-20261019"


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def pixels(data):
    image = Image.open(io.BytesIO(data)).convert("RGBA")
    return image.size, image.tobytes()


def texture_map(model):
    return {t["name"]: pixels(base64.b64decode(t["source"].split(",", 1)[1]))
            for t in model["textures"] if t.get("source", "").startswith("data:")}


def geometry(element):
    return {"type": element.get("type", "cube"), "from": element.get("from"), "to": element.get("to"),
            "rotation": element.get("rotation", [0, 0, 0]), "origin": element.get("origin", [0, 0, 0]),
            "box_uv": element.get("box_uv", False), "inflate": element.get("inflate", 0),
            "visibility": element.get("visibility", True), "export": element.get("export", True),
            "faces": {k: {"uv": f.get("uv"), "texture": f.get("texture"), "rotation": f.get("rotation", 0)}
                      for k, f in element.get("faces", {}).items()}}


def texture_content(model):
    return Counter((size, sha(data)) for size, data in texture_map(model).values())


def main():
    manifest = read(NATIVE / "manifest.json")
    master = read(NATIVE / "all-items-optimized.bbmodel")
    master_textures = texture_map(master)
    full = {a["id"]: a for a in read(FULL / "equipment-42.json")["assets"]}
    checks, errors, model_checks = [], [], []
    with zipfile.ZipFile(JAR) as jar:
        installed_routes = json.loads(jar.read("assets/eldencombat/routes/2d.json"))
        source_routes = read(ROOT / "src/main/resources/assets/eldencombat/routes/2d.json")
        for asset in manifest["assets"]:
            model_path = NATIVE / asset["model"]
            model = read(model_path)
            embedded = list(texture_map(model).values())
            model_checks.append({"id": asset["id"], "modelHashMatchesManifest":
                                 sha(model_path.read_bytes()) == asset["modelSha256"],
                                 "elements": len(model["elements"])})
            selected = {v["state"]: v for v in full[asset["id"]]["variants"] + full[asset["id"]]["gui"]}
            for state in asset["variants"] + asset["gui"]:
                data = (NATIVE / state["file"]).read_bytes()
                native_pixels = pixels(data)
                key = asset["id"] + "__" + state["state"] + ".png"
                selected_path = FULL / selected[state["state"]]["file"]
                selected_data = selected_path.read_bytes()
                runtime_state = state["state"].replace("gui_", "gui")
                runtime_path = "assets/eldencombat/textures/item/2d/" + asset["id"] + "_" + runtime_state + ".png"
                installed_data = jar.read(runtime_path)
                external_path = ROOT / "art/weapons" / key
                check = {"id": asset["id"], "state": state["state"],
                         "candidateHashMatchesManifest": sha(data) == state["sha256"],
                         "masterTextureMatchesPixels": master_textures.get(key) == native_pixels,
                         "independentTextureMatchesPixels": native_pixels in embedded,
                         "candidateEqualsSelectedPixels": native_pixels == pixels(selected_data),
                         "selectedEqualsInstalledBytes": selected_data == installed_data,
                         "selectedEqualsInstalledPixels": pixels(selected_data) == pixels(installed_data),
                         "externalExportExists": external_path.exists(),
                         "externalExportMatchesCandidatePixels": external_path.exists()
                         and pixels(external_path.read_bytes()) == native_pixels,
                         "externalExportMatchesSelectedPixels": external_path.exists()
                         and pixels(external_path.read_bytes()) == pixels(selected_data),
                         "runtimePath": runtime_path}
                checks.append(check)
                for field in ("candidateHashMatchesManifest", "masterTextureMatchesPixels",
                              "independentTextureMatchesPixels", "selectedEqualsInstalledBytes"):
                    if not check[field]:
                        errors.append({"id": asset["id"], "state": state["state"], "failure": field})
        semantic_changes = []
        model_paths = ["art/equipment-2d/blockbench-all-items-20261018/all-items-optimized.bbmodel",
                       "art/equipment-2d/blockbench-all-items-20261018/before-current.bbmodel",
                       "art/equipment-2d/blockbench-all-items-20261018/items/morgotts_cursed_sword.bbmodel",
                       "art/equipment-2d/blockbench-polish-20261018/items-optimized.bbmodel",
                       "art/equipment-2d/blockbench-polish-20261018/before-current.bbmodel",
                       "art/weapons/crossbow.bbmodel"]
        for relative in model_paths:
            previous = subprocess.run(["git", "show", "HEAD:" + relative], cwd=ROOT,
                                      capture_output=True, check=True).stdout
            old, new = json.loads(previous), read(ROOT / relative)
            old_elements = {e["uuid"]: e for e in old["elements"]}
            new_elements = {e["uuid"]: e for e in new["elements"]}
            changes = sum(old_elements.get(k) != new_elements.get(k)
                          for k in old_elements.keys() | new_elements.keys())
            semantic_changes.append({"path": relative, "elementsBefore": len(old_elements),
                                     "elementsNow": len(new_elements), "changedElementRecords": changes,
                                     "geometryOrUvChangedElements": sum(geometry(old_elements.get(k, {}))
                                         != geometry(new_elements.get(k, {}))
                                         for k in old_elements.keys() | new_elements.keys()),
                                     "texturePixelsChanged": texture_content(old) != texture_content(new),
                                     "topLevelChangedKeys": sorted(k for k in old.keys() | new.keys()
                                                                   if old.get(k) != new.get(k))})
        selected_receipt = read(ROOT / "art/equipment-2d/taste-weapons-20261018/selection.json")
        adopted_checks = [{"id": e["id"], "state": e["state"], "matchesSelection":
                           sha((ROOT / e["file"]).read_bytes()) == e["sha256"]}
                          for e in selected_receipt["selectedArtFiles"]]
        independent_morgott = texture_map(read(NATIVE / "items/morgotts_cursed_sword.bbmodel"))
        morgott_matches = []
        for name, content in independent_morgott.items():
            backup = NATIVE / "revisions/stars-morgott-r2/textures" / name.replace("__", "_")
            morgott_matches.append({"name": name, "matchesPreR2BackupPixels":
                                    backup.exists() and pixels(backup.read_bytes()) == content})
        result = {"date": "2026-10-19", "scope": "Read-only; no model, art, source or installation modified",
                  "installedJar": str(JAR), "installedJarSha256": sha(JAR.read_bytes()),
                  "localBuildJarMatchesInstalled": (ROOT / "build/libs/eldencombat-0.2.0.jar").read_bytes()
                  == JAR.read_bytes(), "sourceRoutesEqualInstalled": source_routes == installed_routes,
                  "candidateManifest": {k: manifest[k] for k in ("humanAccepted", "runtimeIntegrated", "inGameAccepted")},
                  "candidateItems": len(manifest["assets"]), "candidateStates": len(checks),
                  "candidateStatesDifferentFromSelected": sum(not c["candidateEqualsSelectedPixels"] for c in checks),
                  "selectedStatesMatchingInstalled": sum(c["selectedEqualsInstalledBytes"] for c in checks),
                  "selectedPixelsMatchingInstalled": sum(c["selectedEqualsInstalledPixels"] for c in checks),
                  "externalExportsMatchingCandidate": sum(c["externalExportMatchesCandidatePixels"] for c in checks),
                  "externalExportsMatchingSelected": sum(c["externalExportMatchesSelectedPixels"] for c in checks),
                  "currentModelHashMismatches": [c for c in model_checks if not c["modelHashMatchesManifest"]],
                  "adoptedFiveWeaponStates": adopted_checks, "checks": checks,
                  "independentMorgottBackupMatches": morgott_matches,
                  "semanticChangesSinceGitHead": semantic_changes, "errors": errors,
                  "gameStarted": False, "runtimeRenderPathConfirmed": False}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "audit.json").write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps({k: result[k] for k in ("candidateItems", "candidateStates",
          "candidateStatesDifferentFromSelected", "selectedStatesMatchingInstalled",
          "selectedPixelsMatchingInstalled", "externalExportsMatchingCandidate",
          "externalExportsMatchingSelected", "currentModelHashMismatches", "errors",
          "sourceRoutesEqualInstalled", "localBuildJarMatchesInstalled", "semanticChangesSinceGitHead")}, indent=2))


if __name__ == "__main__":
    main()
