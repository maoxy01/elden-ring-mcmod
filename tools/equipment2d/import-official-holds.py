"""Convert verified Mojang client resources into isolated Blockbench references.

No runtime asset, admitted art, installed JAR or user Blockbench project is edited.
Client archives/mappings stay in build/. Only selected reference assets are emitted.
"""
import base64
import copy
import hashlib
import importlib.util
import itertools
import json
import uuid
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "art/equipment-2d/official-holds-20261020"
CACHE = ROOT / "build/official-holds-20261020"
SLOTS = ["firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand", "gui", "ground", "fixed", "head"]
CLIENTS = {"1.21.1": "30c73b1c5da787909b2f73340419fdf13b9def88",
           "1.21.11": "ba2df812c2d12e0219c489c4cd9a5e1f0760f5bd"}
VERSIONS = {"1.21.1": "499680d659b7a0ccb2fa9a809c345c5baf2ace7d",
            "1.21.11": "718cd84b4912691fe5de829aab6876a4b7f6c5c4"}
MODELS = [("sword", "1.21.1", "diamond_sword", "sprite"),
          ("shield", "1.21.1", "shield", "shield"),
          ("bow", "1.21.1", "bow", "sprite"),
          ("axe", "1.21.1", "iron_axe", "sprite"),
          ("trident", "1.21.1", "trident_in_hand", "trident"),
          ("mace", "1.21.1", "mace", "sprite"),
          ("spear", "1.21.11", "iron_spear_in_hand", "sprite"),
          ("shield_blocking", "1.21.1", "shield_blocking", "shield"),
          ("trident_throwing", "1.21.1", "trident_throwing", "trident"),
          *[("bow_pulling_" + str(i), "1.21.1", "bow_pulling_" + str(i), "sprite") for i in range(3)],
          ("spear_gui", "1.21.11", "iron_spear", "sprite")]
IDENTITY = dict(rotation=[0, 0, 0], translation=[0, 0, 0], scale=[1, 1, 1])
spec = importlib.util.spec_from_file_location("display_math", Path(__file__).with_name("build-display-candidates.py"))
maths = importlib.util.module_from_spec(spec)
spec.loader.exec_module(maths)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def stable_id(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "mojang-hold-reference/" + name))


def extract(archive, version, path, receipts):
    data = archive.read(path)
    destination = OUT / "source" / version / path
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    receipts[version + "/" + path] = dict(sha256=sha(data), bytes=len(data))
    return data


def resolve(archive, version, model, receipts):
    path = "assets/minecraft/models/" + model.replace("minecraft:", "") + ".json"
    data = json.loads(extract(archive, version, path, receipts))
    parent = data.get("parent", "")
    inherited, chain = ({}, []) if parent.startswith("builtin/") or not parent else resolve(archive, version, parent, receipts)
    result = copy.deepcopy(inherited)
    for key, value in data.items():
        if key in ("display", "textures"):
            result.setdefault(key, {}).update(value)
        else:
            result[key] = value
    return result, [path] + chain


def display_of(data):
    raw = data.get("display", {})
    result = {}
    for slot in SLOTS:
        value = raw.get(slot, raw.get(slot.replace("lefthand", "righthand"), {}))
        result[slot] = {key: value.get(key, default).copy() for key, default in IDENTITY.items()}
    return result


def sprite_elements(texture):
    """One thin cube per opaque texel, same 16-unit item plane and 1-unit depth.

    Interior faces are redundant but preserve silhouette and texel colours. This
    is an editable preview mesh, not a claim to reuse vanilla's baked topology.
    """
    image = np.asarray(Image.open(texture).convert("RGBA"))
    h, w = image.shape[:2]
    elements = []
    for y, x in zip(*np.nonzero(image[:, :, 3])):
        x, y = int(x), int(y)
        faces = {face: dict(uv=[x, y, x + 1, y + 1], texture=0) for face in FACE_VERTICES}
        faces["north"]["uv"] = [x + 1, y, x, y + 1]
        elements.append(dict(name=f"texel_{x}_{y}", uuid=stable_id(texture.name + f"/{x}/{y}"), type="cube",
                             box_uv=False, autouv=0, origin=[0, 0, 0],
                             **{"from": [16 * x / w, 16 * (h - y - 1) / h, 7.5],
                                "to": [16 * (x + 1) / w, 16 * (h - y) / h, 8.5]}, faces=faces))
    return elements, w, h


# Minecraft FaceInfo vertex order, used to preserve ModelPart UV correspondence.
FACE_VERTICES = {
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)]}
# name, xyz, size, texture offset, mirror. From mapped TridentModel/ShieldModel;
# their numeric constants were also inspected in SHA1-verified Mojang bytecode.
ENTITY_BOXES = {
    "shield": [("plate", [-6, -11, -2], [12, 22, 1], [0, 0], False),
               ("handle", [-1, -3, -1], [2, 6, 6], [26, 0], False)],
    "trident": [("pole", [-.5, 2, -.5], [1, 25, 1], [0, 6], False),
                ("base", [-1.5, 0, -.5], [3, 2, 1], [4, 0], False),
                ("left_spike", [-2.5, -3, -.5], [1, 4, 1], [4, 3], False),
                ("middle_spike", [-.5, -4, -.5], [1, 4, 1], [0, 0], False),
                ("right_spike", [1.5, -3, -.5], [1, 4, 1], [4, 3], True)]}


def fit_uv(uvs):
    us, vs = sorted(set(u for u, v in uvs)), sorted(set(v for u, v in uvs))
    for flip_u, flip_v, rotation in itertools.product((False, True), (False, True), range(4)):
        u0, u1 = us[::-1] if flip_u else us
        v0, v1 = vs[::-1] if flip_v else vs
        raw = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
        if all(raw[(i + rotation) % 4] == uvs[i] for i in range(4)):
            return dict(uv=[u0, v0, u1, v1], rotation=rotation * 90, texture=0)
    raise ValueError("Entity UV cannot be represented as a Java cube face")


def entity_elements(kind):
    result = []
    for name, origin, size, offset, mirror in ENTITY_BOXES[kind]:
        lo, hi = np.array(origin, dtype=float), np.array(origin, dtype=float) + size
        dx, dy, dz = size
        u, v = offset
        # Index order: 000,100,110,010,001,101,111,011.
        indices = [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0), (0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]
        original = []
        for index in indices:
            point = np.where(index, hi, lo)
            if mirror:
                point[0] = lo[0] + hi[0] - point[0]
            original.append(point * [1, -1, -1])
        bounds_lo, bounds_hi = np.min(original, axis=0), np.max(original, axis=0)
        polygons = [([5, 4, 0, 1], [u + dz, v, u + dz + dx, v + dz]),
                    ([2, 3, 7, 6], [u + dz + dx, v + dz, u + dz + 2 * dx, v]),
                    ([0, 4, 7, 3], [u, v + dz, u + dz, v + dz + dy]),
                    ([1, 0, 3, 2], [u + dz, v + dz, u + dz + dx, v + dz + dy]),
                    ([5, 1, 2, 6], [u + dz + dx, v + dz, u + 2 * dz + dx, v + dz + dy]),
                    ([4, 5, 6, 7], [u + 2 * dz + dx, v + dz, u + 2 * dz + 2 * dx, v + dz + dy])]
        faces = {}
        for vertex_ids, (u0, v0, u1, v1) in polygons:
            coordinates = {tuple(original[index]): uv for index, uv in zip(vertex_ids, [(u1, v0), (u0, v0), (u0, v1), (u1, v1)])}
            for face, vertices in FACE_VERTICES.items():
                corners = [tuple(np.where(index, bounds_hi, bounds_lo)) for index in vertices]
                if set(corners) == set(coordinates):
                    faces[face] = fit_uv([coordinates[p] for p in corners])
                    break
            else:
                raise ValueError("Unmapped entity face: " + name)
        assert len(faces) == 6
        result.append(dict(name=name, uuid=stable_id(kind + "/" + name), type="cube", box_uv=False, autouv=0,
                           origin=[0, 0, 0], **{"from": bounds_lo.tolist(), "to": bounds_hi.tolist()}, faces=faces))
    return result


def validate_entity(elements, display, kind):
    maximum, samples = 0., 0
    for left, slot in enumerate(SLOTS[:4]):
        left = slot.endswith("lefthand")
        matrix = maths.raw_pose(display[slot], left)
        for element, box in zip(elements, ENTITY_BOXES[kind]):
            lo, hi = np.array(box[1]), np.array(box[1]) + box[2]
            for corner in itertools.product((0, 1), repeat=3):
                point = np.where(corner, hi, lo)
                # Vanilla: D * translate(-.5) * scale(1,-1,-1) * ModelPart.
                expected = matrix @ np.r_[point * [1, -1, -1] / 16 - .5, 1]
                converted = np.where(np.array(corner) * [1, -1, -1] + [0, 1, 1], element["to"], element["from"])
                actual = matrix @ np.r_[converted / 16 - .5, 1]
                maximum = max(maximum, float(np.linalg.norm(expected - actual)))
                samples += 1
    assert maximum < 1e-10
    return dict(vertexSamples=samples, maxPositionErrorBlocks=maximum)


def main():
    archives, provenance, receipts, records = {}, {}, {}, []
    for version, expected in CLIENTS.items():
        data = (CACHE / (version + "-client.jar")).read_bytes()
        assert hashlib.sha1(data).hexdigest() == expected
        manifest_bytes = (OUT / "source" / (version + ".json")).read_bytes()
        assert hashlib.sha1(manifest_bytes).hexdigest() == VERSIONS[version]
        manifest = json.loads(manifest_bytes)
        archives[version] = zipfile.ZipFile(CACHE / (version + "-client.jar"))
        provenance[version] = dict(client=manifest["downloads"]["client"],
            manifestUrl=f"https://piston-meta.mojang.com/v1/packages/{VERSIONS[version]}/{version}.json",
            manifestSha1=VERSIONS[version], clientHashVerified=True)
    for name, version, model_name, kind in MODELS:
        archive = archives[version]
        model, chain = resolve(archive, version, "item/" + model_name, receipts)
        display = display_of(model)
        texture = ("entity/shield_base_nopattern" if kind == "shield" else "entity/trident") if kind != "sprite" else model["textures"]["layer0"].replace("minecraft:", "")
        texture_path = "assets/minecraft/textures/" + texture + ".png"
        texture_bytes = extract(archive, version, texture_path, receipts)
        local_texture = OUT / "source" / version / texture_path
        if kind == "sprite":
            elements, w, h = sprite_elements(local_texture)
            validation = dict(geometry="editable alpha extrusion; baked topology is not copied")
        else:
            elements = entity_elements(kind)
            w, h = Image.open(local_texture).size
            validation = validate_entity(elements, display, kind)
        metadata = dict(version=version, sourceModel=chain[0], inheritance=chain,
                        kind=kind, officialDisplayUnchanged=True, runtimeAccepted=False,
                        geometryConversion="entity positions [x,-y,-z], without re-centering" if kind != "sprite" else "16-unit alpha extrusion", validation=validation)
        project = dict(meta=dict(format_version="5.0", model_format="java_block", box_uv=False),
                       name="Official_" + name + "_" + version, parent="", java_block_version="1.21.1",
                       resolution=dict(width=w, height=h), front_gui_light=True, elements=elements,
                       outliner=[e["uuid"] for e in elements], display=display,
                       textures=[dict(name=Path(texture_path).name, id="0", uuid=stable_id(version + texture), width=w, height=h,
                                      uv_width=w, uv_height=h, source="data:image/png;base64," + base64.b64encode(texture_bytes).decode())],
                       unhandled_root_fields=dict(official_hold_reference=metadata))
        write(OUT / "blockbench" / (name + ".bbmodel"), project)
        write(OUT / "display-json" / (name + ".json"), dict(display=display))
        records.append(dict(id=name, display=display, **metadata))
    # Keep the spear's modern context selection as source evidence, not as a
    # resource that could accidentally be put into the 1.21.1 runtime.
    extract(archives["1.21.11"], "1.21.11", "assets/minecraft/items/iron_spear.json", receipts)
    write(OUT / "official-holds.json", dict(targetVersion="1.21.1", spearReferenceVersion="1.21.11",
          units=dict(rotation="degrees, XYZ", translation="model pixels, 16 per block", scale="dimensionless"),
          provenance=provenance, items=records, files=receipts, installed=False, nativePreviewAccepted=False))
    # Re-read every project and compare every display component against source.
    for record in records:
        project = json.loads((OUT / "blockbench" / (record["id"] + ".bbmodel")).read_text())
        assert project["display"] == record["display"]
        assert project["elements"] and len(project["outliner"]) == len(project["elements"])
        for texture in project["textures"]:
            assert base64.b64decode(texture["source"].split(",", 1)[1]).startswith(b"\x89PNG")
    write(OUT / "validation.json", dict(officialClientsVerified=2, baseTypes=7, blockbenchProjects=len(records),
          displayContexts=len(records) * len(SLOTS), rawDisplayValuesMatch=True,
          builtinGeometryChecks={r["id"]: r["validation"] for r in records if r["kind"] != "sprite"},
          runtimeFilesChanged=False, gameStarted=False, nativeBlockbenchPreview=False))
    print(f"Official clients verified: 2; base types: 7; editable projects: {len(records)}; contexts: {len(records) * len(SLOTS)}")


if __name__ == "__main__":
    main()
