#!/usr/bin/env python3
"""Derive explicit per-context display candidates for the equipment 2D route.

Why this exists (contract DSH-EQUIPMENT-2D-IMPLEMENTATION-CONTRACT-20261015 §5, §10.3, §11.1):

  * `display` used to be `null` for all 42 entries, so every entry was rejected as
    "display_not_calibrated" and no candidate could ever reach the render path. That is a
    validation dead end: an item cannot be accepted before it can be drawn, and cannot be
    drawn before it is accepted.
  * The palm target p and the rotation R are *not* user input. They are derived here from
    (a) a vanilla reference pose that already exists in the resource pack,
    (b) the reference item's own grip pixel, and
    (c) the per-item art geometry (canvas, grip pixel, measured pixel span, world length).
  * The runtime transform uses one shared unit and one origin:
        emission space  b(x,y) = (x/W - 0.5, 0.5 - y/H, 0)      (centred block coords, [-0.5,0.5])
        grip            a = b(anchorX, anchorY)                  (same space, same origin)
        display         D = T(t) . R . S      with   t = p - R.S.a and S = s*I
    `t = p - R.S.a` is exactly the formula the contract names; it is a plain TRS and is
    therefore expressible as JSON display values, so no mixin-side calibration state is needed.
    `R` is the reference pose's *rotation only*: the reference pose's own uniform scale is divided
    out, because the contract prescribes the emitted scale itself as
    `s = worldLengthBlocks*W/sourceSpanPixels`. Leaving the reference scale in R would emit
    `refScale*s` and make every weapon shorter than the art manifest declares. `p` keeps the
    reference pose's scale, because p is where the reference item's grip actually lands.

Outputs (never packaged into the mod): art/equipment-2d/full/display-candidates.json
Every value is a *candidate*: `status = proposal_not_game_verified`, `humanAccepted = false`.

Two hard rules the bug report of 2026-10-15 forced into this file:

  * **One unit for the reference grip.** `REFERENCES[...]["anchorPixels"]` is a pixel in the
    reference item's 16x16 icon box, so a grip at the *model origin* is pixel (8,8), not (0,0).
    (0,0) is the box *corner* — half a block away from the origin in x and y — and it pushed every
    shield half a block up and half a block back: "shield floats next to the hand".
  * **The axis angle cannot see a half-turn.** Aligning principal axes only makes two lines
    parallel; a canvas drawn 180 degrees round keeps that axis exactly where it was and moves the
    drawing to the other side of it. `perpendicular_skew` measures that side, and a sign mismatch
    with the reference art adds the compensating 180 degrees. The bow is why: its art is the vanilla
    bow icon rotated by a half-turn, which is what "the bow is held reversed" means.

Inputs:
  art/equipment-2d/full/equipment-42.json             per-item art geometry
  art/equipment-2d/full/sprites/*.png                 own sprites (axis measurement only)
  art/equipment-reuse/research-only-full/*.png        reference sprites (axis measurement only)
  reference display blocks copied verbatim from the vanilla resource pack
  (assets/minecraft/models/item/{handheld,generated,bow,crossbow,shield,shield_blocking}.json)

Nothing from the research directory is copied, published or referenced by the mod.
"""
import json
import math
import os
import sys
from collections import OrderedDict

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MANIFEST = os.path.join(ROOT, "art/equipment-2d/full/equipment-42.json")
SPRITES = os.path.join(ROOT, "art/equipment-2d/full/sprites")
RESEARCH = os.path.join(ROOT, "art/equipment-reuse/research-only-full")
OUT = os.path.join(ROOT, "art/equipment-2d/full/display-candidates.json")

CONTEXTS = ["thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand",
            "ground", "fixed", "head"]
# The inventory slot is served by the independent gui_16/gui_32 asset and an identity pose.
GUI_CONTEXT = "gui"

# The selected black-blade art has its cutting edge facing the holder with the default roll.
# Roll around the blade, not the canvas normal: keep the tip direction and swap edge/back.
HELD_BLADE_ROLL = {"malikeths_black_blade": 180.0}

# --------------------------------------------------------------------------------------------
# Reference display blocks, copied verbatim from the vanilla resource pack in
# build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar.
# `None` means "the vanilla model does not declare it"; ItemTransforms.Deserializer then inherits
# the right-hand entry for the left hand (see net/minecraft/client/renderer/block/model/
# ItemTransforms.java lines 120-130), and we reproduce that inheritance explicitly.
# --------------------------------------------------------------------------------------------
HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
GENERATED = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}
BOW = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
CROSSBOW = {
    "thirdperson_righthand": {"rotation": [-90, 0, -60], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-90, 0, 30], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [-90, 0, -55], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [-90, 0, 35], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
SHIELD = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
    "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
}
SHIELD_BLOCKING = {
    "thirdperson_righthand": {"rotation": [45, 155, 0], "translation": [-3.49, 11, -2], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [45, 155, 0], "translation": [11.51, 7, 2.5], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
}
SHIELD_GUI = {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]}

REFERENCES = OrderedDict([
    ("melee", OrderedDict([
        ("image", "art/equipment-reuse/research-only-full/minecraft_item_iron_sword.png"),
        ("anchorPixels", [3.5, 12.5]),
        ("source", "minecraft:item/handheld with minecraft:item/generated for gui/ground/fixed/head"),
        ("display", {**GENERATED, **HANDHELD}),   # handheld wins where both declare a context
        ("blocking", None),
        # Box models (item/generated, item/handheld, item/bow, item/crossbow) are authored in the
        # [0,1] icon box and ItemRenderer's translate(-0.5,-0.5,-0.5) is *inside* the display frame
        # (PoseStack.translate multiplies on the right), so the world point of a box coordinate v is
        # D.v - D.h with h = (0.5,0.5,0.5). The centred grip `anchor_block` returns is exactly v - h,
        # so for these references D.grip is already the vanilla world point and no extra term is due.
        ("gripOffsetBlocks", [0.0, 0.0, 0.0]),
    ])),
    ("bow", OrderedDict([
        ("image", "art/equipment-reuse/research-only-full/minecraft_item_bow.png"),
        ("anchorPixels", [5.5, 7.5]),
        ("source", "minecraft:item/bow"),
        ("display", {**GENERATED, **BOW}),
        ("blocking", None),
        ("gripOffsetBlocks", [0.0, 0.0, 0.0]),
    ])),
    ("crossbow", OrderedDict([
        ("image", "art/equipment-reuse/research-only-full/minecraft_item_crossbow_standby.png"),
        ("anchorPixels", [9.5, 11.5]),
        ("source", "minecraft:item/crossbow"),
        ("display", {**GENERATED, **CROSSBOW}),
        ("blocking", None),
        ("gripOffsetBlocks", [0.0, 0.0, 0.0]),
    ])),
    ("shield", OrderedDict([
        ("image", None),
        # minecraft:item/shield parents builtin/entity: ShieldModel authors its plate and handle
        # *around the model origin* (plate x -6..6, y -11..11), so the origin — not the middle of a
        # [0,1] icon box — is the point that matters. ItemRenderer still shifts that geometry by
        # -0.5 on every axis *inside the display frame*, so the point the vanilla plate centre lands
        # on is D_ref . (-0.5,-0.5,-0.5). Omitting that half-box (the first version wrote the grip as
        # the icon-box corner) or adding it in the hand frame instead of the display frame (a plain
        # -0.5 after the display) each put the shield a visible half to one block away from the hand.
        ("anchorPixels", [8.0, 8.0]),
        ("gripOffsetBlocks", [-0.5, -0.5, -0.5]),
        ("source", "minecraft:item/shield (builtin/entity: handle at the model origin, geometry around "
                   "the origin); blocking pose from minecraft:item/shield_blocking"),
        ("display", {**GENERATED, **SHIELD, "gui": SHIELD_GUI}),
        ("blocking", {**SHIELD, **SHIELD_BLOCKING}),
    ])),
    ("flask", OrderedDict([
        ("image", None),
        # Same unit as the box families: item/generated, so the icon centre is the grip and it is
        # pixel (8,8) of the 16x16 reference box, not its corner.
        ("anchorPixels", [8.0, 8.0]),
        ("gripOffsetBlocks", [0.0, 0.0, 0.0]),
        ("source", "minecraft:item/generated (icon centre is the grip)"),
        ("display", GENERATED),
        ("blocking", None),
    ])),
])

FAMILY_OF_KIND = {"melee": "melee", "bow": "bow", "crossbow": "crossbow", "shield": "shield",
                  "flask": "flask"}


def family_of(asset_id):
    """Which vanilla reference pose family an own-sprite item follows."""
    if asset_id in ("shield", "small_shield", "great_shield"):
        return "shield"
    if asset_id == "test_flask":
        return "flask"
    if asset_id == "great_bow":
        return "bow"
    if asset_id == "ballista":
        return "crossbow"
    return "melee"


# --------------------------------------------------------------------------------------------
# Matrix helpers. Column vectors; a pose is M = T . R . S, applied once, exactly like
# ItemTransform.apply (translate, then rotationXYZ, then scale).
# --------------------------------------------------------------------------------------------
def translation(t):
    m = np.eye(4)
    m[0, 3], m[1, 3], m[2, 3] = t
    return m


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], dtype=float)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], dtype=float)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], dtype=float)


def scale_mat(s):
    m = np.eye(4)
    m[0, 0], m[1, 1], m[2, 2] = s, s, s
    return m


def rotation_xyz(rx, ry, rz):
    """
    `Quaternionf().rotationXYZ(rx,ry,rz)` — the call vanilla's ItemTransform.apply makes.

    JOML composes it as qx*qy*qz, i.e. the matrix Rx.Ry.Rz (Z is applied to the vector first).
    Getting this backwards silently rotates every multi-axis pose: a stored (0,-90,55) would then be
    read as Rz.Ry.Rx and the item would face a different way than vanilla reads the same JSON.
    """
    return rot_x(math.radians(rx)) @ rot_y(math.radians(ry)) @ rot_z(math.radians(rz))


MIRROR_X = np.diag([-1.0, 1.0, 1.0, 1.0])


def wrap180(degrees):
    while degrees > 180.0:
        degrees -= 360.0
    while degrees <= -180.0:
        degrees += 360.0
    return degrees


def euler_xyz(m):
    """
    Inverse of rotation_xyz: R = Rx(a).Ry(b).Rz(c) -> (a, b, c) in degrees.

    The triple is then canonicalised to the equivalent `(a+180, 180-b, c+180)` whenever |a| > 90.

    That is not cosmetic. `Quaternionf.rotationXYZ` builds the quaternion from half-angle sines and
    cosines, and a triple with two angles near 180 degrees (which is exactly what a 180-degree pose
    plus a small in-plane correction decomposes into, e.g. the `fixed` context) comes back a few
    0.008 degrees off — measured, not assumed. The equivalent triple keeps |a| <= 90 and reproduces
    the same matrix to the last bit; every emitted triple is re-checked by the matrix round trip below.
    """
    b = math.asin(max(-1.0, min(1.0, m[0, 2])))
    if abs(m[0, 2]) > 1.0 - 1e-9:          # gimbal lock: a is free, fix a = 0
        a = 0.0
        c = math.atan2(m[1, 0], m[1, 1])
    else:
        a = math.atan2(-m[1, 2], m[2, 2])
        c = math.atan2(-m[0, 1], m[0, 0])
    a, b, c = math.degrees(a), math.degrees(b), math.degrees(c)
    if abs(a) > 90.0:
        a, b, c = a + 180.0, 180.0 - b, c + 180.0
    return [wrap180(a), wrap180(b), wrap180(c)]


def raw_pose(spec, left):
    """The pose a display entry produces, including the left-hand mirror MC applies."""
    rot = spec.get("rotation", [0, 0, 0])
    tra = spec.get("translation", [0, 0, 0])
    sca = spec.get("scale", [1, 1, 1])
    if left:
        tra = [-tra[0], tra[1], tra[2]]
        rot = [rot[0], -rot[1], -rot[2]]
    return translation([c / 16.0 for c in tra]) @ rotation_xyz(*rot) @ np.diag(
        [sca[0], sca[1], sca[2], 1.0])


def raw_from_applied(applied, left):
    """Invert the left-hand mirror: MC applies Mx.applied.Mx, so store Mx.applied.Mx."""
    stored = (MIRROR_X @ applied @ MIRROR_X) if left else applied
    columns = [stored[:3, 0], stored[:3, 1], stored[:3, 2]]
    scale = [float(np.linalg.norm(column)) for column in columns]
    # The euler angles live in the rotation only; a scaled basis would break asin().
    rotation_matrix = np.eye(4)
    for index, column in enumerate(columns):
        rotation_matrix[:3, index] = column / scale[index]
    rot = euler_xyz(rotation_matrix)
    translation_blocks = [stored[0, 3], stored[1, 3], stored[2, 3]]
    return {
        "rotation": [round(v, 6) for v in rot],
        "translation": [round(v * 16.0, 6) for v in translation_blocks],
        "scale": [round(v, 6) for v in scale],
    }, stored


def normalized_rotation(m):
    """
    The pure rotation of a pose, with its own uniform scale divided out.

    The contract prescribes the emitted scale exactly (`s = worldLengthBlocks*W/sourceSpanPixels`,
    §5/§10.3) and the single final transform is `T(p).R.S.T(-a)` with S = s*I. Folding the reference
    pose's own scale (0.85 for handheld, 0.68 for first person, …) into R would silently emit
    `refScale*s` and make the weapon 15%/32% shorter than the length the art manifest declares, so R
    is taken as the rotation only and the reference scale is dropped.
    """
    out = np.eye(4)
    for index in range(3):
        column = m[:3, index]
        length = float(np.linalg.norm(column))
        if length <= 1e-12:
            raise ValueError("degenerate reference pose: zero basis column")
        out[:3, index] = column / length
    return out


def context_spec(display, context, hand):
    """Resolve one context, reproducing ItemTransforms.Deserializer's left-hand fallback."""
    if context in display:
        return display[context]
    if hand == "lefthand":
        mirrored = context.replace("lefthand", "righthand")
        if mirrored != context and mirrored in display:
            return display[mirrored]
    return None


def anchor_block(pixels, canvas):
    return np.array([pixels[0] / canvas[0] - 0.5, 0.5 - pixels[1] / canvas[1], 0.0])


def world_length_error(emitted, asset, canvas):
    """
    |emittedScale * span/W - worldLengthBlocks|: the contract's s must be the scale JSON carries.

    This is the check that would have caught the reference pose's own 0.85/0.68 scale leaking into
    the emitted value, which shrinks the weapon below the length the art manifest declares.
    """
    visible = emitted["scale"][0] * asset["sourceSpanPixels"] / canvas[0]
    return round(abs(visible - asset["worldLengthBlocks"]), 9)


def principal_axis(path):
    """Principal axis angle measured from +Y (up) in emission space, plus its anisotropy."""
    image = Image.open(path).convert("RGBA")
    alpha = np.asarray(image).astype(float)[..., 3]
    ys, xs = np.nonzero(alpha > 16)
    if len(xs) < 8:
        return None
    weights = alpha[ys, xs] / 255.0
    mx = float(np.average(xs, weights=weights))
    my = float(np.average(ys, weights=weights))
    dx = (xs - mx) * weights
    dy = (ys - my) * weights
    values, vectors = np.linalg.eigh(np.cov(np.vstack([dx, dy])))
    order = np.argsort(values)[::-1]
    ratio = float(values[order[0]] / max(values[order[1]], 1e-9))
    axis = vectors[:, order[0]]
    # pixel +x -> emission +x, pixel +y -> emission -y
    angle = math.degrees(math.atan2(axis[0], -axis[1])) % 180.0
    projection = (xs - mx) * axis[0] + (ys - my) * axis[1]
    return {
        "angleDegrees": round(angle, 3),
        "anisotropy": round(ratio, 3),
        "spanPixels": round(float(projection.max() - projection.min()), 4),
        "centerPixels": [round(mx, 3), round(my, 3)],
        "size": list(image.size),
    }


def wrap90(delta):
    while delta <= -90.0:
        delta += 180.0
    while delta > 90.0:
        delta -= 180.0
    return delta


def perpendicular_skew(path, threshold=16):
    """
    Signed third moment of the sprite's mass perpendicular to its principal axis, in a frame whose
    perpendicular direction is fixed by the canvas +x axis rather than by the eigenvector sign.

    That convention is the whole point: the eigenvector of a principal axis is only a *line*, so any
    moment measured along an arbitrary one of its two directions reports nothing at all. Fixing the
    perpendicular by a canvas-fixed rule keeps the measure blind to nothing that a 180 degree
    in-plane rotation does: the axis line stays where it was, the mass moves to the other side, and
    the sign flips.

    A bow is the case this exists for. Its art's principal axis is parallel to the vanilla bow
    icon's, so the axis-angle term above reads 0 — and the drawn bow is then held with its limbs on
    the string's opposite side, which the bug report calls "the bow is held reversed".
    """
    image = Image.open(path).convert("RGBA")
    alpha = np.asarray(image).astype(float)[..., 3]
    ys, xs = np.nonzero(alpha > threshold)
    if len(xs) < 8:
        return None
    weight = alpha[ys, xs] / 255.0
    x = xs - np.average(xs, weights=weight)
    y = -(ys - np.average(ys, weights=weight))          # pixel +y is emission -y
    covariance = np.cov(np.vstack([x * weight, y * weight]))
    values, vectors = np.linalg.eigh(covariance)
    axis = vectors[:, int(np.argmax(values))]
    ux, uy = float(axis[0]), float(axis[1])
    px, py = (-uy, ux) if -uy > 0 else (uy, -ux)        # perpendicular with a positive x component
    if abs(px) < 1e-9:
        px, py = (0.0, 1.0 if py > 0 else -1.0)
    offset = x * px + y * py
    return float(np.average(offset ** 3, weights=weight))


def build_candidate(asset_id, asset, family):
    reference = REFERENCES[family]
    canvas = asset["variants"][0]["size"]
    grip = anchor_block(asset["anchorPixels"], canvas)
    uniform = asset["worldLengthBlocks"] * canvas[0] / asset["sourceSpanPixels"]

    sprite = None
    for variant in asset["variants"]:
        if variant["file"].startswith("sprites/"):
            sprite = os.path.join(ROOT, "art/equipment-2d/full", variant["file"])
            break
    own_axis = principal_axis(sprite) if sprite else None

    reference_axis = None
    if reference["image"]:
        reference_axis = principal_axis(os.path.join(ROOT, reference["image"]))

    notes = []
    axis = 0.0
    if family in ("shield", "flask"):
        notes.append("grip at the declared reference point; the sprite is near-isotropic so no "
                     "in-plane rotation is implied by the art")
    elif own_axis and reference_axis:
        axis = wrap90(reference_axis["angleDegrees"] - own_axis["angleDegrees"])
        if min(own_axis["anisotropy"], reference_axis["anisotropy"]) < 1.35:
            notes.append("principal axis is weak (anisotropy %.2f/%.2f); in-plane rotation pinned to 0"
                         % (own_axis["anisotropy"], reference_axis["anisotropy"]))
            axis = 0.0
    else:
        notes.append("no measurable sprite axis; in-plane rotation pinned to 0")

    # The axis angle above can only bring the two principal axes parallel; it cannot tell which of
    # the two ends is which. A 180 degree in-plane error of the art leaves the axis exactly where it
    # was and moves the drawing to the other side of it, so it is measured separately here.
    flip = 0.0
    sprite_skew = reference_skew = None
    if reference["image"]:
        sprite_skew = perpendicular_skew(sprite)
        reference_skew = perpendicular_skew(os.path.join(ROOT, reference["image"]))
        if sprite_skew is not None and reference_skew is not None \
                and min(abs(sprite_skew), abs(reference_skew)) > 1e-3 \
                and sprite_skew * reference_skew < 0.0:
            flip = 180.0
            notes.append("art_orientation_180: the sprite's mass lies on the opposite side of its "
                         "principal axis from the reference icon's (perpendicular third moment "
                         "%.4f vs %.4f), so the canvas is drawn 180 degrees round from the vanilla "
                         "art and the rotation carries the compensating half-turn" %
                         (sprite_skew, reference_skew))
    axis = wrap180(axis + flip)

    held_roll = HELD_BLADE_ROLL.get(asset_id, 0.0)
    # principal_axis measures from +Y; the rotation axis here is expressed from +X.
    blade_axis = 90.0 - own_axis["angleDegrees"] if held_roll else 0.0
    roll_rotation = (rot_z(math.radians(blade_axis)) @ rot_x(math.radians(held_roll))
                     @ rot_z(math.radians(-blade_axis)))[:3, :3]

    reference_grip = anchor_block(reference["anchorPixels"], [16.0, 16.0])
    # The vanilla chain's own translate(-0.5,-0.5,-0.5), expressed where it actually acts: *inside* the
    # display frame, i.e. as part of the reference point. Zero for box references (their `anchor_block`
    # already returns a centred coordinate, which is the box coordinate minus that half box).
    grip_offset = np.array(reference.get("gripOffsetBlocks") or [0.0, 0.0, 0.0])
    reference_grip = reference_grip + grip_offset

    contexts = OrderedDict()
    checks = []
    for context in CONTEXTS:
        hand = "lefthand" if context.endswith("lefthand") else "righthand"
        spec = context_spec(reference["display"], context, hand)
        if spec is None:
            continue
        left = hand == "lefthand"
        applied = raw_pose(spec, left)
        palm = applied @ np.append(reference_grip, 1.0)
        palm = palm[:3]

        own_rotation = normalized_rotation(applied[:3, :3])[:3, :3] @ rot_z(math.radians(axis))[:3, :3]
        if held_roll and context.startswith(("firstperson_", "thirdperson_")):
            own_rotation = own_rotation @ roll_rotation
        own_scale = scale_mat(uniform)[:3, :3]
        target = np.eye(4)
        target[:3, :3] = own_rotation @ own_scale
        target[:3, 3] = palm - own_rotation @ own_scale @ grip
        emitted, stored = raw_from_applied(target, left)

        # Independent re-application through the emitted JSON values, exactly as ItemTransform does.
        replay = raw_pose(emitted, left)
        drift = float(np.max(np.abs(replay - target)))
        landing = replay @ np.append(grip, 1.0)
        checks.append({
            "context": context,
            "roundTripMaxError": round(drift, 9),
            "gripLandsOnPalmErrorBlocks": round(float(np.max(np.abs(landing[:3] - palm))), 9),
            "worldLengthErrorBlocks": world_length_error(emitted, asset, canvas),
        })
        contexts[context] = emitted

    contexts[GUI_CONTEXT] = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
    # Any context this display does not declare copies `fixed`, so an unspecified context is a stated
    # decision instead of an accidental identity transform.
    contexts["inheritance"] = "fixed"

    blocking = None
    if reference["blocking"]:
        blocking = OrderedDict()
        for context in CONTEXTS:
            hand = "lefthand" if context.endswith("lefthand") else "righthand"
            spec = context_spec(reference["blocking"], context, hand)
            if spec is None:
                continue
            left = hand == "lefthand"
            applied = raw_pose(spec, left)
            palm = (applied @ np.append(reference_grip, 1.0))[:3]
            own_rotation = normalized_rotation(applied[:3, :3])[:3, :3]
            own_scale = scale_mat(uniform)[:3, :3]
            target = np.eye(4)
            target[:3, :3] = own_rotation @ own_scale
            target[:3, 3] = palm - own_rotation @ own_scale @ grip
            emitted, _ = raw_from_applied(target, left)
            replay = raw_pose(emitted, left)
            checks.append({
                "context": "blocking:" + context,
                "roundTripMaxError": round(float(np.max(np.abs(replay - target))), 9),
                "gripLandsOnPalmErrorBlocks": round(float(np.max(np.abs(
                    (replay @ np.append(grip, 1.0))[:3] - palm))), 9),
                "worldLengthErrorBlocks": world_length_error(emitted, asset, canvas),
            })
            blocking[context] = emitted
        # The blocking table is a second complete display list: the same identity inventory pose the
        # non-blocking table declares, so the renderer can swap tables without a missing context.
        blocking[GUI_CONTEXT] = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
        blocking["inheritance"] = "fixed"

    worst = max((check["roundTripMaxError"] for check in checks), default=0.0)
    worst_length = max((check["worldLengthErrorBlocks"] for check in checks), default=0.0)
    if worst > 1e-5 or worst_length > 1e-5:
        sys.exit("display round-trip failed for %s: matrix error %g, world-length error %g blocks"
                 % (asset_id, worst, worst_length))

    candidate = OrderedDict([
        ("route", "own_sprite"),
        ("family", family),
        ("reference", OrderedDict([
            ("key", family),
            ("source", reference["source"]),
            ("image", reference["image"]),
            ("anchorPixels", reference["anchorPixels"]),
            ("measuredAxisDegrees", reference_axis["angleDegrees"] if reference_axis else None),
            ("perpendicularSkew", None if reference_skew is None else round(reference_skew, 6)),
            ("gripOffsetBlocks", list(reference.get("gripOffsetBlocks") or [0.0, 0.0, 0.0])),
        ])),
        ("geometry", OrderedDict([
            ("canvas", canvas),
            ("anchorPixels", asset["anchorPixels"]),
            ("anchorBlock", [round(float(v), 9) for v in grip]),
            ("worldLengthBlocks", asset["worldLengthBlocks"]),
            ("sourceSpanPixels", asset["sourceSpanPixels"]),
            ("uniformXYScale", round(float(uniform), 12)),
        ])),
        ("axis", OrderedDict([
            ("measuredSpriteAxisDegrees", own_axis["angleDegrees"] if own_axis else None),
            ("spriteAnisotropy", own_axis["anisotropy"] if own_axis else None),
            ("appliedAxisDegrees", round(axis, 6)),
            ("flip180Degrees", flip),
            ("spritePerpendicularSkew", None if sprite_skew is None else round(sprite_skew, 6)),
            ("sprite", os.path.relpath(sprite, ROOT).replace("\\", "/") if sprite else None),
            ("notes", notes),
        ])),
        ("display", contexts),
        ("displayBlocking", blocking),
        ("verification", checks),
        ("status", "proposal_not_game_verified"),
        ("humanAccepted", False),
    ])
    if held_roll:
        candidate["axis"]["heldBladeRollDegrees"] = held_roll
        candidate["axis"]["heldBladeAxisFromXDegrees"] = blade_axis
        candidate["axis"]["notes"].append(
            "held edge correction: roll 180 degrees around the blade's longitudinal axis; "
            "translation is re-derived from the unchanged grip, with GUI/world display unchanged")
    return candidate


def main():
    manifest = json.load(open(MANIFEST, encoding="utf-8"))
    candidates = OrderedDict()
    for asset in manifest["assets"]:
        if asset.get("delivery") != "own_sprite":
            continue
        candidates[asset["id"]] = build_candidate(asset["id"], asset, family_of(asset["id"]))

    out = OrderedDict([
        ("version", 1),
        ("date", "2026-10-15"),
        ("status", "proposal_not_game_verified"),
        ("humanAccepted", False),
        ("note", "Derived per-context display candidates for the own_sprite route. p comes from the "
                 "vanilla reference pose applied to the reference item's own grip pixel; the item's "
                 "own grip pixel is mapped onto that same point with one shared origin and unit, "
                 "t = p - R.S.a. Nothing here is user-supplied and nothing here is in-game verified."),
        ("unit", "translation in 1/16 block, rotation in degrees, scale unitless; "
                 "stored values are pre-mirror for the left hand, matching ItemTransform.apply"),
        ("references", REFERENCES),
        ("items", candidates),
    ])
    with open(OUT, "w", encoding="utf-8") as handle:
        json.dump(out, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print("display candidates written: %d own_sprite entries -> %s" % (len(candidates), OUT))
    for asset_id, candidate in candidates.items():
        geometry = candidate["geometry"]
        display = candidate["display"]["thirdperson_righthand"]
        print("  %-28s s=%-9.5f axis=%-7.2f a=(%.4f,%.4f) tp_rh=%s" % (
            asset_id, geometry["uniformXYScale"], candidate["axis"]["appliedAxisDegrees"],
            geometry["anchorBlock"][0], geometry["anchorBlock"][1],
            display["translation"]))


if __name__ == "__main__":
    main()
