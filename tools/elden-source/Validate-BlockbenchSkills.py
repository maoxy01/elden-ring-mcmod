"""Audit source hashes, 1.3x timing, anatomical directions and Euler interpolation."""
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2] / "build/elden-source"
sys.path.insert(0, str(ROOT / "tools/python-havok"))
import numpy as np
from scipy.spatial.transform import Rotation


def main():
    out = ROOT / "blockbench-skills-20261021-arm70"
    report = json.loads((out / "conversion-report.json").read_text(encoding="utf-8"))
    skeleton = json.loads((ROOT / "decoded-skills/skeleton.json").read_text(encoding="utf-8"))
    names = skeleton["Names"]
    basis = np.asarray(report["basis"])
    rows = []
    assert len(report["projects"]) == 8 and len(report["clips"]) == 15
    assert sum(c["frames"] for c in report["clips"]) == 2744
    for file in report["projects"]:
        path = Path(file["path"])
        assert hashlib.sha256(path.read_bytes()).hexdigest().upper() == file["sha256"]
        project = json.loads(path.read_text(encoding="utf-8"))
        for animation in project["animations"]:
            for animator in animation["animators"].values():
                if animator["name"].startswith(("MC/", "SourceLink/")):
                    assert animator["quaternion_interpolation"]
    for entry in report["clips"]:
        candidate = json.loads((out / (entry["clip"] + ".motion.json")).read_text(encoding="utf-8"))
        assert candidate["waistGain"] == .6 and candidate["limbAmplitudeControl"] == .85
        assert candidate["armAmplitudeControl"] == .70
        assert candidate["waistEulerLimitsDegrees"] == [15., 20., 15.]
        source_path = Path(candidate["sourceNPZ"])
        assert hashlib.sha256(source_path.read_bytes()).hexdigest().upper() == candidate["sourceNPZSha256"]
        with np.load(source_path, allow_pickle=False) as source:
            time = np.array(candidate["timeSeconds"])
            assert np.allclose(time * 1.3, source["times"], atol=1e-12, rtol=0)
            assert np.all(np.diff(time) > 0)
            assert candidate["runtimeStageMap"] is None and not candidate["rootMotionApplied"]
            assert len(candidate["sourceRootMotion"]) == len(source["root_motion"])
            worst_interpolation = 0.
            worst_direction = 0.
            actor = Rotation.from_quat(candidate["actorHeadingQuaternionXYZW"])
            assert len(actor) == len(time)
            previous = json.loads((ROOT / "blockbench-skills-20261021" / (entry["clip"] + ".motion.json")).read_text(encoding="utf-8"))
            assert candidate["timeSeconds"] == previous["timeSeconds"]
            assert candidate["actorHeadingQuaternionXYZW"] == previous["actorHeadingQuaternionXYZW"]
            assert candidate["sourceRootMotion"] == previous["sourceRootMotion"]
            for joint, values in candidate["blockbenchEulerZYXDegrees"].items():
                values = np.asarray(values)
                assert values.shape == (len(time), 3) and np.isfinite(values).all()
                assert np.allclose(candidate["runtimeEulerDegrees"][joint], values * [-1, -1, 1])
                rotation = Rotation.from_euler("ZYX", values[:, ::-1], degrees=True)
                quaternion_rotation = Rotation.from_quat(candidate["rotationQuaternionXYZW"][joint])
                assert np.max((rotation.inv() * quaternion_rotation).magnitude()) < 1e-7
                if joint == "body":
                    assert np.all(np.abs(values) <= np.array([15., 20., 15.]) + 1e-7)
                else:
                    original = actor.inv() * Rotation.from_quat(candidate["undampedWorldQuaternionXYZW"][joint])
                    assert np.all(rotation.magnitude() <= original.magnitude() + 1e-7)
                    if joint.endswith("Arm"):
                        original_angle = original.magnitude()
                        # The existing continuous quaternion-vector gain is not
                        # the same as multiplying every large angle by 0.7.
                        expected_angle = 2 * np.arctan2(.7 * np.sin(original_angle / 2), np.cos(original_angle / 2))
                        assert np.max(np.abs(rotation.magnitude() - expected_angle)) < 1e-7
                        before = Rotation.from_quat(previous["rotationQuaternionXYZW"][joint])
                        assert np.all(rotation.magnitude() <= before.magnitude() + 1e-7)
                if not joint.endswith("Arm"):
                    assert candidate["rotationQuaternionXYZW"][joint] == previous["rotationQuaternionXYZW"][joint]
                q = rotation.as_quat()
                other = q[1:].copy()
                other[np.sum(q[:-1] * other, axis=1) < 0] *= -1
                midpoint = Rotation.from_quat(q[:-1] + other)
                linear = Rotation.from_euler("ZYX", ((values[:-1] + values[1:]) / 2)[:, ::-1], degrees=True)
                error = np.rad2deg((linear.inv() * midpoint).magnitude()).max()
                worst_interpolation = max(worst_interpolation, float(error))
                if joint.endswith("Arm") or joint.endswith("Leg"):
                    side = "R" if joint.startswith("right") else "L"
                    a, b = ("UpperArm", "Hand") if joint.endswith("Arm") else ("Thigh", "Foot")
                    direction = np.einsum("ij,fj->fi", basis,
                        source["world_matrices"][:, names.index(side + "_" + b), :3, 3]
                        - source["world_matrices"][:, names.index(side + "_" + a), :3, 3])
                    direction /= np.linalg.norm(direction, axis=1)[:, None]
                    # Anatomical fidelity is checked before the explicitly requested
                    # amplitude reduction, whose samples are retained separately.
                    actual = Rotation.from_quat(candidate["undampedWorldQuaternionXYZW"][joint]).apply([0, -1, 0])
                    worst_direction = max(worst_direction, float(np.max(np.abs(actual - direction))))
            assert worst_direction < 2e-6, (entry["clip"], worst_direction)
            rows.append({"clip": entry["clip"], "frames": len(time),
                         "undampedAnatomicalDirectionComponentError": worst_direction,
                         "waistBounded": True,
                         "armAmplitudeControl": .7, "nonArmTracksUnchanged": True,
                         "oldLinearEulerMidpointErrorDegrees": worst_interpolation,
                         "usesNativeQuaternionInterpolation": True})
    result = {"speed": 1.3, "passed": True, "clips": rows,
              "note": "Old Euler-only error is diagnostic. The bbmodel uses native quaternion interpolation; verify native playback separately."}
    (out / "validation.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
