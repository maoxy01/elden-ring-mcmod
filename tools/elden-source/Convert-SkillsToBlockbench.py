"""Build local, editable 1.3x Blockbench motion studies from verified HKX data.

The line figure is an anatomical joint/segment reference; the pixel figure is
the Minecraft six-part candidate with a separate actor-heading parent.
No game resources are changed.
"""
import copy
import hashlib
import json
from pathlib import Path
import sys
import uuid
import warnings

WORKSPACE = Path(__file__).resolve().parents[2]
ROOT = WORKSPACE / "build/elden-source"
sys.path.insert(0, str(ROOT / "tools/python-havok"))
import numpy as np
from scipy.spatial.transform import Rotation

SPEED = 1.3
LIMB_AMPLITUDE = .85
ARM_AMPLITUDE = .70
WAIST_GAIN = .6
WAIST_LIMITS = np.array([15., 20., 15.])
OUT = ROOT / "blockbench-skills-20261021-arm70"
SKILLS = {
    814: ("takers_flames", "掠夺火焰"), 820: ("wave_of_gold", "黄金波动"),
    827: ("cursed_blood_slice", "咒血斩"), 828: ("waterfowl_dance", "水鸟乱舞"),
    829: ("gold_breaker", "黄金碎击"), 832: ("starcaller_cry", "唤星"),
    834: ("bloodboon_ritual", "授血仪式"), 838: ("destined_death", "命定之死"),
}
CHAINS = [
    ["Pelvis", "Spine", "Spine1", "Spine2", "Neck", "Head"],
    ["Spine2", "L_Clavicle", "L_UpperArm", "L_Forearm", "L_Hand"],
    ["Spine2", "R_Clavicle", "R_UpperArm", "R_Forearm", "R_Hand"],
    ["Pelvis", "L_Thigh", "L_Calf", "L_Foot", "L_Toe0"],
    ["Pelvis", "R_Thigh", "R_Calf", "R_Foot", "R_Toe0"],
]
JOINTS = ["head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def ident(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "elden-local-motion:" + name))


def unit(v):
    length = np.linalg.norm(v)
    if length < 1e-8:
        raise ValueError("Degenerate anatomical axis")
    return v / length


def axis_frame(y, preferred_z):
    y = unit(y)
    z = preferred_z - y * np.dot(y, preferred_z)
    if np.linalg.norm(z) < 1e-6:
        fallback = np.eye(3)[np.argmin(np.abs(y))]
        z = fallback - y * np.dot(y, fallback)
    z = unit(z)
    return np.column_stack((np.cross(y, z), y, z))


def angles(matrices):
    """Continuous equivalent ZYX Euler branches, stored in BB's x/y/z order."""
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        raw = Rotation.from_matrix(matrices).as_euler("ZYX")[:, ::-1]
    result = [raw[0]]
    for x, y, z in raw[1:]:
        candidates = np.array([[x, y, z], [x + np.pi, np.pi - y, z + np.pi]])
        previous = result[-1]
        candidates += 2 * np.pi * np.round((previous - candidates) / (2 * np.pi))
        result.append(min(candidates, key=lambda c: np.linalg.norm(c - previous)))
    result = np.asarray(result)
    rebuilt = Rotation.from_euler("ZYX", result[:, ::-1]).as_matrix()
    if np.max(np.abs(rebuilt - matrices)) > 1e-5:
        raise ValueError("Euler conversion changed source orientation")
    return np.rad2deg(result)


def cube(name, start, end, color=0):
    return {"name": name, "uuid": ident(name), "type": "cube", "from": start,
            "to": end, "origin": [0, 0, 0], "rotation": [0, 0, 0],
            "box_uv": False, "color": color,
            "faces": {face: {"uv": [0, 0, 1, 1], "texture": False}
                      for face in ["north", "south", "east", "west", "up", "down"]}}


def group(name, origin, children):
    return {"name": name, "uuid": ident(name), "origin": origin,
            "rotation": [0, 0, 0], "export": True, "isOpen": False,
            "visibility": True, "children": children}


def keyframes(channel, times, values):
    return [{"channel": channel, "time": float(t), "interpolation": "linear",
             # Keyframe values are Molang expressions in Blockbench. Scientific
             # notation (e.g. -4e-9) is not a safe numeric expression there.
             "data_points": [dict(zip("xyz", (f"{float(v):.9f}" for v in value)))]}
            for t, value in zip(times, values)]


def reduce_linear(times, values, tolerance):
    """Drop only points reconstructed within the stated component error bound."""
    selected = {0, len(times) - 1}
    pending = [(0, len(times) - 1)]
    while pending:
        begin, end = pending.pop()
        if end <= begin + 1:
            continue
        ratio = ((times[begin + 1:end] - times[begin]) / (times[end] - times[begin]))[:, None]
        expected = values[begin] + ratio * (values[end] - values[begin])
        error = np.max(np.abs(values[begin + 1:end] - expected), axis=1)
        worst = int(np.argmax(error)) + begin + 1
        if np.max(error) > tolerance:
            selected.add(worst)
            pending.extend([(begin, worst), (worst, end)])
    selected = sorted(selected)
    return keyframes("position", times[selected], values[selected])


def template():
    old = json.loads((WORKSPACE / "tools/blockbench-pose/evidence/projects/wave_of_gold.bbmodel").read_text(encoding="utf-8"))
    by_uuid = {e["uuid"]: e for e in old["elements"]}
    old_groups = {g["uuid"]: g for g in old.get("groups", [])}
    elements, groups = [], []
    for node in old["outliner"]:
        old_group = {**old_groups.get(node["uuid"], {}), **node}
        if old_group.get("name") not in JOINTS:
            continue
        joint = old_group["name"]
        pieces = [by_uuid[c] for c in old_group["children"]
                  if isinstance(c, str) and c in by_uuid and by_uuid[c]["name"] == joint + "_reference"]
        if len(pieces) != 1:
            raise ValueError("Unexpected reference player geometry")
        piece = copy.deepcopy(pieces[0])
        piece["uuid"] = ident("mc_cube/" + joint)
        for field in ["from", "to", "origin"]:
            piece[field][0] += 12
        elements.append(piece)
        origin = list(old_group["origin"])
        origin[0] += 12
        groups.append(group("MC/" + joint, origin, [piece["uuid"]]))
    if len(groups) != 6:
        raise ValueError("Reference player must have six independent parts")
    used_textures = {f["texture"] for e in elements for f in e["faces"].values()}
    textures = [t for i, t in enumerate(old["textures"]) if i in used_textures or t["uuid"] in used_textures]
    for t in textures:
        if not t.get("source", "").startswith("data:"):
            raise ValueError("Reference skin must be embedded")
        t.pop("path", None)
    return {"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
            "name": "", "resolution": {"width": 64, "height": 64},
            "elements": elements, "outliner": [group("Minecraft六关节候选", [12, 0, 0], groups)],
            "textures": textures, "animations": []}


def convert(world, refs, names, basis):
    point = lambda name: np.einsum("ij,fj->fi", basis, world[:, names.index(name), :3, 3])
    reference_rotation = lambda name: Rotation.from_matrix(refs[names.index(name), :3, :3]).as_matrix()
    motion_rotation = lambda name: Rotation.from_matrix(world[:, names.index(name), :3, :3]).as_matrix()
    delta = lambda name: basis @ motion_rotation(name) @ reference_rotation(name).T @ basis.T
    body = []
    for y, x in zip(point("Spine2") - point("Pelvis"), point("R_UpperArm") - point("L_UpperArm")):
        y = unit(y)
        x = unit(x - y * np.dot(x, y))
        body.append(np.column_stack((x, y, np.cross(x, y))))
    ref_y = basis @ (refs[names.index("Spine2"), :3, 3] - refs[names.index("Pelvis"), :3, 3])
    ref_x = basis @ (refs[names.index("R_UpperArm"), :3, 3] - refs[names.index("L_UpperArm"), :3, 3])
    ref_y = unit(ref_y)
    ref_x = unit(ref_x - ref_y * np.dot(ref_x, ref_y))
    ref_body = np.column_stack((ref_x, ref_y, np.cross(ref_x, ref_y)))
    rotations = {"head": delta("Head"), "body": np.asarray(body) @ ref_body.T}
    for side, target_side in [("R", "right"), ("L", "left")]:
        for start, end, twist_bone, target in [
            ("UpperArm", "Hand", "Forearm", "Arm"), ("Thigh", "Foot", "Calf", "Leg")]:
            direction = point(side + "_" + end) - point(side + "_" + start)
            twist = delta(side + "_" + twist_bone)
            rotations[target_side + target] = np.array([
                axis_frame(-v, r @ np.array([0., 0., 1.])) for v, r in zip(direction, twist)])
    return rotations


def restrain(rotations, world, refs, names, basis):
    """Keep actor heading separate from a gently moving, bounded rigid torso."""
    pelvis = names.index("Pelvis")
    reference = Rotation.from_matrix(refs[pelvis, :3, :3]).as_matrix()
    delta = basis @ Rotation.from_matrix(world[:, pelvis, :3, :3]).as_matrix() @ reference.T @ basis.T
    q = Rotation.from_matrix(delta).as_quat()
    heading = []
    for quat in q:
        # Y-twist extraction avoids treating forward bends as a heading reversal.
        if np.hypot(quat[1], quat[3]) < 1e-6:
            heading.append(heading[-1] if heading else 0.)
        else:
            heading.append(2 * np.arctan2(quat[1], quat[3]))
    heading = np.unwrap(heading)
    actor = Rotation.from_euler("y", heading).as_matrix()
    local = {n: np.swapaxes(actor, 1, 2) @ m for n, m in rotations.items()}
    result = {}
    for name, matrices in local.items():
        if name == "body":
            euler = angles(matrices)
            # Neutral-centred coordinates; actor heading, including complete turns,
            # is already carried by the parent and is never clipped here.
            euler = (euler + 180) % 360 - 180
            bounded = WAIST_LIMITS * np.tanh(WAIST_GAIN * euler / WAIST_LIMITS)
            result[name] = Rotation.from_euler("ZYX", bounded[:, ::-1], degrees=True).as_matrix()
        else:
            quat = Rotation.from_matrix(matrices).as_quat()
            # This antipodal-invariant mapping is continuous through +/-180°.
            # It reduces ordinary swings gently while preserving complete turns.
            quat[:, :3] *= ARM_AMPLITUDE if name.endswith("Arm") else LIMB_AMPLITUDE
            result[name] = Rotation.from_quat(quat).as_matrix()
    return result, actor


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    skeleton_path = ROOT / "decoded-skills/skeleton.json"
    skeleton = json.loads(skeleton_path.read_text())
    names, parents = skeleton["Names"], skeleton["Parents"]
    refs = []
    for parent, local in zip(parents, skeleton["ReferenceLocalMatrices"]):
        refs.append(np.asarray(local) if parent < 0 else refs[parent] @ np.asarray(local))
    refs = np.asarray(refs)
    right = refs[names.index("R_UpperArm"), :3, 3] - refs[names.index("L_UpperArm"), :3, 3]
    right[1] = 0
    right = unit(right)
    basis = np.vstack((right, [0, 1, 0], np.cross(right, [0, 1, 0])))
    scale = 32 / (refs[names.index("Head"), 1, 3] + .14)
    report_path = ROOT / "decoded-skills/decode-report.json"
    report = json.loads(report_path.read_text())
    records, projects, expected = [], {}, {}
    ref_names = sorted({n for chain in CHAINS for n in chain})
    for record in report["Clips"]:
        skill_id, label = SKILLS[record["TaeGroup"]]
        path = Path(record["Output"])
        for file, field in [(path, "OutputSha256"), (Path(record["Source"]), "SourceSha256"),
                            (Path(record["Compendium"]), "CompendiumSha256")]:
            if sha(file) != record[field]:
                raise ValueError(f"Input changed: {file}")
        arrays = np.load(path, allow_pickle=False)
        times = arrays["times"] / SPEED
        world = arrays["world_matrices"].astype(float)
        undamped = convert(world, refs, names, basis)
        rotations, actor = restrain(undamped, world, refs, names, basis)
        if skill_id not in projects:
            projects[skill_id] = template()
        project = projects[skill_id]
        project["name"] = label + "-法环动作-1.3倍速-腰部收紧-手臂70%"
        if len(project["outliner"]) == 1:
            reference = []
            for n in ref_names:
                e = cube("source_joint/" + n, [-.19, -.19, -.19], [.19, .19, .19], 1 if n.startswith("R_") else 3)
                project["elements"].append(e)
                reference.append(group("Source/" + n, [0, 0, 0], [e["uuid"]]))
            for chain in CHAINS:
                for a, b in zip(chain, chain[1:]):
                    name = f"SourceLink/{a}/{b}"
                    e = cube(name + "/segment", [-.09, 0, -.09], [.09, 1, .09], 1 if b.startswith("R_") else 3)
                    project["elements"].append(e)
                    reference.append(group(name, [0, 0, 0], [e["uuid"]]))
            project["outliner"].append(group("原骨架解码对照（不叠加根运动）", [0, 0, 0], reference))
        clip_id = path.stem
        animators = {}
        animators[ident("Minecraft六关节候选")] = {"name": "Minecraft六关节候选", "type": "bone",
            "quaternion_interpolation": True, "keyframes": keyframes("rotation", times, angles(actor))}
        angle_values = {}
        for joint, matrices in rotations.items():
            values = angles(matrices)
            angle_values[joint] = values.tolist()
            # Native per-bone quaternion interpolation avoids Euler chart singularities.
            animators[ident("MC/" + joint)] = {"name": "MC/" + joint, "type": "bone",
                "quaternion_interpolation": True, "keyframes": keyframes("rotation", times, values)}
        positions = np.einsum("ij,fbj->fbi", basis, world[:, :, :3, 3]) * scale + [-24, 0, 0]
        for n in ref_names:
            keys = reduce_linear(times, positions[:, names.index(n)], .015)
            animators[ident("Source/" + n)] = {"name": "Source/" + n, "type": "bone", "keyframes": keys}
        for chain in CHAINS:
            for a, b in zip(chain, chain[1:]):
                name = f"SourceLink/{a}/{b}"
                start = positions[:, names.index(a)]
                vector = positions[:, names.index(b)] - start
                length = np.linalg.norm(vector, axis=1)
                matrices = np.array([axis_frame(v, np.array([0., 0., 1.])) for v in vector])
                eulers = angles(matrices)
                keys = reduce_linear(times, start, .015)
                keys += keyframes("rotation", times, eulers)
                reduced = reduce_linear(times, np.column_stack((np.ones(len(times)), length, np.ones(len(times)))), .01)
                for k in reduced:
                    k["channel"] = "scale"
                keys += reduced
                animators[ident(name)] = {"name": name, "type": "bone", "quaternion_interpolation": True, "keyframes": keys}
        animation_name = f"{label} / {clip_id} / 1.3x / 腰部收紧 / 手臂70%"
        project["animations"].append({"uuid": ident(clip_id), "name": animation_name,
            "loop": "hold", "override": True, "length": float(times[-1]),
            "snapping": 60, "animators": animators})
        project.setdefault("unhandled_root_fields", {})["elden_source_motion"] = {
            "speed": SPEED, "skill": skill_id, "localResearchOnly": True,
            "sourceSkeletonBones": len(names), "displayedReferenceJoints": len(ref_names),
            "sourceSkeleton": str(skeleton_path), "sourceSkeletonSha256": sha(skeleton_path),
            "rootMotionApplied": False, "runtimeStageMap": None,
            "limbAmplitudeControl": LIMB_AMPLITUDE, "waistGain": WAIST_GAIN,
            "armAmplitudeControl": ARM_AMPLITUDE,
            "waistEulerLimitsDegrees": WAIST_LIMITS.tolist(), "actorHeadingSeparate": True,
            "interpolation": "Blockbench native per-bone quaternion slerp",
            "note": "Six Minecraft parts under a separate actor-heading preview root. Waist pitch/roll bounded to 15 degrees, yaw to 20. No elbows/knees, limb translations, weapons, VFX or hit timing. Source anatomical reference unchanged. Current game pose loader has neither actor-heading tracks nor quaternion interpolation; these files are not drop-in runtime replacements."}
        sample_indices = np.linspace(0, len(times) - 1, 9).round().astype(int)
        expected[clip_id] = {"skill": skill_id, "animation": animation_name,
            "frames": [{"time": float(times[i]), "rotations": {n: rotations[n][i].tolist() for n in JOINTS},
                        "actorRotation": actor[i].tolist(),
                        "referencePositions": {n: positions[i, names.index(n)].tolist() for n in ref_names}}
                       for i in sample_indices]}
        for i in np.linspace(0, len(times) - 2, 5).round().astype(int):
            midpoint_rotations = {}
            for n in JOINTS:
                a, b = Rotation.from_matrix(rotations[n][i:i + 2])
                midpoint_rotations[n] = (a * Rotation.from_rotvec((a.inv() * b).as_rotvec() / 2)).as_matrix().tolist()
            expected[clip_id]["frames"].append({"time": float((times[i] + times[i + 1]) / 2),
                "kind": "quaternion_midpoint", "rotations": midpoint_rotations,
                "actorRotation": (Rotation.from_matrix(actor[i]) * Rotation.from_rotvec(
                    (Rotation.from_matrix(actor[i]).inv() * Rotation.from_matrix(actor[i + 1])).as_rotvec() / 2)).as_matrix().tolist(),
                "referencePositions": {n: ((positions[i, names.index(n)] + positions[i + 1, names.index(n)]) / 2).tolist()
                                       for n in ref_names}})
        # Standalone full-rate candidate tracks; phase boundaries remain deliberately unset.
        candidate = {"clip": clip_id, "skill": skill_id, "speed": SPEED,
            "limbAmplitudeControl": LIMB_AMPLITUDE, "waistGain": WAIST_GAIN,
            "armAmplitudeControl": ARM_AMPLITUDE,
            "waistEulerLimitsDegrees": WAIST_LIMITS.tolist(), "actorHeadingSeparate": True,
            "sourceSeconds": float(arrays["times"][-1]), "seconds": float(times[-1]),
            "timeSeconds": times.tolist(), "blockbenchEulerZYXDegrees": angle_values,
            "runtimeEulerDegrees": {n: (np.asarray(v) * [-1, -1, 1]).tolist() for n, v in angle_values.items()},
            "nativeInterpolation": "quaternion_slerp", "runtimeInterpolationCompatible": False,
            "rotationQuaternionXYZW": {n: Rotation.from_matrix(m).as_quat().tolist() for n, m in rotations.items()},
            "actorHeadingQuaternionXYZW": Rotation.from_matrix(actor).as_quat().tolist(),
            "undampedWorldQuaternionXYZW": {n: Rotation.from_matrix(m).as_quat().tolist() for n, m in undamped.items()},
            "sourceRootMotion": arrays["root_motion"].tolist(),
            "rootMotionSampleSourceTimes": np.linspace(0, float(arrays["times"][-1]), len(arrays["root_motion"])).tolist(),
            "rootMotionSamplePlaybackTimes": (np.linspace(0, float(arrays["times"][-1]), len(arrays["root_motion"])) / SPEED).tolist(),
            "rootMotionApplied": False, "runtimeStageMap": None,
            "sourceNPZ": str(path), "sourceNPZSha256": sha(path)}
        (OUT / (clip_id + ".motion.json")).write_text(json.dumps(candidate, ensure_ascii=False), encoding="utf-8")
        records.append({"skill": skill_id, "clip": clip_id, "sourceSeconds": candidate["sourceSeconds"],
            "seconds": candidate["seconds"], "frames": len(times), "speed": SPEED,
            "rootMotionSamples": len(arrays["root_motion"]), "sourceNPZSha256": sha(path)})
    files = []
    for skill_id, project in projects.items():
        flat_groups = []
        def outline(node):
            if isinstance(node, str):
                return node
            flat_groups.append({k: v for k, v in node.items() if k != "children"})
            return {"uuid": node["uuid"], "isOpen": False,
                    "children": [outline(child) for child in node["children"]]}
        project["outliner"] = [outline(g) for g in project["outliner"]]
        project["groups"] = flat_groups
        path = OUT / (skill_id + ".bbmodel")
        path.write_text(json.dumps(project, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        files.append({"skill": skill_id, "path": str(path), "sha256": sha(path),
                      "animations": len(project["animations"]), "bytes": path.stat().st_size})
    (OUT / "native-expected.json").write_text(json.dumps(expected), encoding="utf-8")
    summary = {"speed": SPEED, "decodedSourceBones": len(names), "referenceJoints": len(ref_names),
        "limbAmplitudeControl": LIMB_AMPLITUDE, "waistGain": WAIST_GAIN,
        "armAmplitudeControl": ARM_AMPLITUDE,
        "waistEulerLimitsDegrees": WAIST_LIMITS.tolist(), "actorHeadingSeparate": True,
        "minecraftJoints": JOINTS, "basis": basis.tolist(), "referenceUnitsPerMeter": scale,
        "clips": records, "projects": files,
        "sourceReportSha256": sha(report_path), "converterSha256": sha(Path(__file__)),
        "status": "Local editable candidates; not admitted to runtime and not packaged for distribution"}
    (OUT / "conversion-report.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Converted {len(records)} clips / {len(projects)} projects at {SPEED}x to {OUT}")
    print(f"Total bbmodel bytes: {sum(f['bytes'] for f in files):,}")


if __name__ == "__main__":
    main()
