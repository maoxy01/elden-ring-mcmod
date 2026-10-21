"""Adopt the explicitly selected 20261021 models and export arm-local worn data.

Source Blockbench projects remain byte-identical. Historical 20261019 backups
are never reused or overwritten; this run has its own recoverable baseline.
"""
import copy
import hashlib
import importlib.util
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "art/equipment-2d/blockbench-holds-20261021"
WORN = ROOT / "art/equipment-2d/worn-hand-gear-20261021"
RES = ROOT / "src/main/resources/assets/eldencombat"


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def worn_models():
    manifest = read(WORN / "manifest.json")
    exported = []
    for name in ("claw", "fist"):
        source = WORN / (name + "_worn.bbmodel")
        assert digest(source) == manifest["items"][name]["sha256"]
        model = read(source)
        groups = {g["uuid"]: g for g in model["groups"]}
        cubes = {c["uuid"]: c for c in model["elements"]}
        for arm, group_name in (("right", "arm_right"), ("left", "arm_right_l")):
            group = next(g for g in groups.values() if g["name"] == group_name)
            pivot = group["origin"]
            assert pivot == ([-5, 22, 0] if arm == "right" else [5, 22, 0])
            assert group.get("rotation", [0, 0, 0]) == [0, 0, 0]
            found = []
            def visit(node, selected=False, enabled=True):
                if isinstance(node, str):
                    cube = cubes[node]
                    if selected and enabled and cube.get("export", True):
                        found.append(cube)
                    return
                info = groups[node["uuid"]]
                assert info.get("rotation", [0, 0, 0]) == [0, 0, 0]
                for child in node.get("children", []):
                    visit(child, selected or info["uuid"] == group["uuid"], enabled and info.get("export", True))
            for node in model["outliner"]:
                visit(node)
            converted, face_uvs = [], {}
            for cube in found:
                assert cube["type"] == "cube"
                assert all(face.get("rotation", 0) == 0 for face in cube["faces"].values())
                def local(point):
                    return [point[i] - pivot[i] for i in range(3)]
                converted.append(dict(name=cube["uuid"], bone="arm", material="atlas",
                                      **{"from": local(cube["from"]), "to": local(cube["to"]),
                                         "pivot": local(cube.get("origin", [0, 0, 0])),
                                         "rotation": cube.get("rotation", [0, 0, 0])}))
                face_uvs[cube["uuid"]] = {face: value["uv"] for face, value in cube["faces"].items()}
            assert len(converted) == (47 if name == "claw" else 28)
            resource = dict(schemaVersion=1, id=f"eldencombat:{name}_{arm}", unitsPerBlock=16,
                            texture="eldencombat:textures/item/worn_hands.png", textureSize=[128, 128],
                            bones=[dict(name="arm", parent=None, pivot=[0, 0, 0], rotation=[0, 0, 0])],
                            cubes=converted, materials=dict(atlas=dict(uv=[0, 0, 128, 128], color=[255, 255, 255])),
                            faceUVs=face_uvs, sourceSha256=digest(source),
                            attachment=dict(arm=arm, authoredPivot=pivot, runtimeModelPartScale=[1, -1, 1],
                                            leftGeometryAlreadyMirrored=True))
            write(RES / "worn" / (name + "_" + arm + ".json"), resource)
            exported.append(dict(id=name, arm=arm, cubes=len(converted), sourceSha256=digest(source),
                                 referencePlayerExcluded=True))
    texture = RES / "textures/item/worn_hands.png"
    texture.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(WORN / "textures/worn_atlas.png", texture)
    write(OUT / "worn-export.json", dict(items=exported, textureSha256=digest(texture), runtimeAccepted=False))


def main():
    spec = importlib.util.spec_from_file_location("adoption", Path(__file__).with_name("prepare-blockbench-holds.py"))
    adoption = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(adoption)
    adoption.OUT = OUT
    if not (OUT / "selection.json").exists():
        adoption.stage()
        selection = read(OUT / "selection.json")
        selection.update(date="2026-10-21", authorization=dict(quote="这是最新的建模，你按照这个来做握持",
                         scope="Latest 28 model exports and two arm-mounted hand gear models"),
                         basis="ART-MODELS-LATEST-20261021.md; all 28 model hashes verified")
        write(OUT / "selection.json", selection)
        full_path = ROOT / "art/equipment-2d/full/equipment-42.json"
        full = read(full_path)
        for asset in full["assets"]:
            if "artRevision" in asset:
                asset["artRevision"]["date"] = "2026-10-21"
        write(full_path, full)
    worn_models()
    print("Latest 28-item art admitted; 4 arm-local meshes exported, reference player excluded")


if __name__ == "__main__":
    main()
