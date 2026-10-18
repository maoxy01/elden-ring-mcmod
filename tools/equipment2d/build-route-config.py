#!/usr/bin/env python3
"""Build the equipment section of assets/eldencombat/routes/2d.json (schema v2).

Inputs (priority order fixed by the hand-off):
  1. art/equipment-2d/full/equipment-42.json        - current per-item choice, sizes, grips
  2. art/equipment-2d/full/publishable-assets.json  - own 94 PNG admission whitelist
  3. art/equipment-reuse/research-only-full/evidence.json - provider model/state/predicate evidence
  4. art/equipment-2d/full/display-candidates.json  - derived per-context display candidates
5. art/equipment-2d/full/display-overrides.json   - human calibration on top, read-only here
  5. src/main/java/.../ModItems.java                - the 41 registered equipment IDs + flask

Everything outside the equipment array (ui / fx / existing / auxiliaryModels) is re-emitted
byte-for-byte from the current file: the fx contract must not move.

Admission semantics (contract §2, §4, §11):

  * `blockers` holds **technical** failures only. Anything that is a receipt (visual acceptance), an
    unreachable provider state, an unverified-but-usable alias or a preserved legacy source goes to
    `notes`, because none of those may stop a structurally complete candidate from rendering.
  * `display` is written from the separate candidate file, so re-running this generator can never
    reset a candidate display back to null. `display: null` is legal for the provider routes, which
    inherit the provider's own baked display; for `own_sprite` it is a hard technical failure because
    that route is the only owner of its transform.
  * `status` is `ready` when the static input and the capability are complete. Visual acceptance is
    recorded separately in `acceptance` and never gates `ready` (§4/§11.3).
"""
import hashlib
import json
import os
import re
import sys
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ROUTES = os.path.join(ROOT, "src/main/resources/assets/eldencombat/routes/2d.json")
MANIFEST = os.path.join(ROOT, "art/equipment-2d/full/equipment-42.json")
PUBLISHABLE = os.path.join(ROOT, "art/equipment-2d/full/publishable-assets.json")
EVIDENCE = os.path.join(ROOT, "art/equipment-reuse/research-only-full/evidence.json")
CANDIDATES = os.path.join(ROOT, "art/equipment-2d/full/display-candidates.json")
# Human/in-game calibration on top of the derived candidates. Read-only for every generator: this is
# the one place a tuned value survives a regeneration (contract §11.2).
OVERRIDES = os.path.join(ROOT, "art/equipment-2d/full/display-overrides.json")
MODITEMS = os.path.join(ROOT, "src/main/java/com/dsh/eldencombat/registry/ModItems.java")

SPARTAN_JAR_SHA256 = "84d3ddafb519e2f5e03f2681ff52c30c92c9512181f61a7a7487399a0179888f"

# The display contexts the runtime needs spelled out; `inheritance` covers everything else.
REQUIRED_CONTEXTS = ["firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand",
                     "thirdperson_lefthand", "gui", "ground", "fixed"]


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle, object_pairs_hook=OrderedDict)


def registered_ids():
    source = open(MODITEMS, encoding="utf-8").read()
    found = re.findall(r'(?:weapon|bow|crossbow|shield)\("([a-z_]+)"', source)
    return ["eldencombat:" + name for name in found] + ["eldencombat:test_flask"]


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def anchor_model_units(canvas, anchor):
    width, height = canvas
    x, y = anchor
    return [round(16.0 * x / width - 8.0, 6), round(8.0 - 16.0 * y / height, 6), 0.0]


def uniform_scale(canvas, world_length, span):
    return round(world_length * canvas[0] / span, 12)


def route_of(asset):
    provider = asset.get("providerId")
    if asset.get("delivery") == "own_sprite":
        return "own_sprite"
    if provider and provider.split(":", 1)[0] == "minecraft":
        return "vanilla_model"
    return "provider_model"


def own_state_support(asset_id):
    """Return (notes, blocked_states) for the own-sprite route. Blocked states are evidence."""
    notes, blocked = [], []
    if asset_id in ("shield", "small_shield", "great_shield"):
        notes.append("blocking re-poses both faces of the same geometry; no new texture and no new "
                     "defence resolution")
        blocked.append({"state": "blocking", "support": "unreachable_as_separate_model",
                        "reason": "blocking is a pose of the same two faces, not a third artwork"})
    if asset_id == "great_bow":
        notes.append("pull states are bound to the own bow's real use progress; artDrawFraction is a "
                     "visual fraction only")
    if asset_id == "ballista":
        notes.append("firework/extra ammo art not delivered; a charged firework is a reachable but "
                     "uncovered state and falls back to the legacy model")
        blocked.append({"state": "firework", "support": "uncovered",
                        "reason": "no firework artwork exists; the legacy model is drawn instead of "
                                  "disguising the state as a loaded bolt"})
    if asset_id == "test_flask":
        blocked.append({"state": "empty", "support": "unreachable",
                        "reason": "only the full state is delivered; no consumption or refill rule is "
                                  "invented"})
    return notes, blocked


def predicate_bindings(asset_id, evidence_item, own_item_kind):
    """Classify every provider predicate the evidence records for this item."""
    bindings = []
    seen = OrderedDict()
    for variant in evidence_item["variants"]:
        for key, value in (variant.get("predicate") or {}).items():
            seen.setdefault(key, []).append(variant["state"])
    for key, states in seen.items():
        namespace = key.split(":", 1)[0] if ":" in key else "minecraft"
        name = key.split(":", 1)[1] if ":" in key else key
        if namespace == "minecraft":
            if name in ("pull", "pulling"):
                support = "supported" if own_item_kind in ("bow", "crossbow") else "unreachable"
                reason = ("the vanilla property the own item already carries supplies the visual "
                          "fraction" if support == "supported"
                          else "own item has no bow/crossbow use state; the predicate never fires")
            elif name in ("charged", "firework"):
                support = "supported" if own_item_kind == "crossbow" else "unreachable"
                reason = ("read from the real charged-projectile component"
                          if support == "supported" else "own item is not a crossbow")
            elif name == "blocking":
                support = "supported" if own_item_kind == "shield" else "unreachable"
                reason = ("the real using stack decides it" if support == "supported"
                          else "own item is not a shield")
            else:
                support = "unresolved"
                reason = "unknown minecraft predicate; kept as evidence only"
        else:
            if name in ("pull", "pulling"):
                support = "supported_alias_required" if own_item_kind in ("bow", "crossbow") else "unreachable"
                reason = ("alias property registered on the own item in the provider namespace, "
                          "computed from the own stack's real usage and zeroed when it is not the "
                          "using stack" if support == "supported_alias_required" else
                          "own item has no such use state; alias deliberately not registered")
            elif name in ("blocking", "throwing"):
                support = "unreachable"
                reason = ("the provider model exposes the state but own play has no blocking/throwing "
                          "action; recorded as evidence and never triggered")
            else:
                support = "unresolved"
                reason = "unknown provider predicate; kept as evidence only"
        bindings.append(OrderedDict([
            ("predicate", key),
            ("support", support),
            ("states", states),
            ("reason", reason),
        ]))
    return bindings


def own_item_kind(asset_id):
    if asset_id in ("short_bow", "long_bow", "great_bow"):
        return "bow"
    if asset_id in ("crossbow", "ballista"):
        return "crossbow"
    if asset_id in ("shield", "small_shield", "great_shield"):
        return "shield"
    if asset_id == "test_flask":
        return "flask"
    return "melee"


def validate_display(asset_id, display, blockers, notes):
    """A candidate display must be complete and well formed, or the entry is not ready."""
    if display is None:
        blockers.append("display_missing: the own_sprite route owns its only transform, so palm "
                        "target p and rotation R must be resolved before it can be admitted")
        return
    missing = [context for context in REQUIRED_CONTEXTS if context not in display]
    if missing:
        blockers.append("display_incomplete: missing " + ", ".join(missing))
    if not display.get("inheritance"):
        blockers.append("display_incomplete: inheritance must name the context used for unspecified "
                        "contexts")
    for context, frame in display.items():
        if context == "inheritance":
            continue
        if not isinstance(frame, dict):
            blockers.append("display_invalid: " + context + " is not a transform object")
            continue
        if len(frame.get("translation", [])) != 3 or len(frame.get("rotation", [])) != 3 \
                or len(frame.get("scale", [])) != 3:
            blockers.append("display_invalid: " + context + " must hold three triples")
        if any(not isinstance(v, (int, float)) for v in frame.get("scale", [])):
            blockers.append("display_invalid: " + context + " scale is not numeric")
    notes.append("display source: derived candidate from the vanilla reference pose and the item's "
                 "own grip pixel; t = p - R.S.a, one origin, one unit")


def verify_asset(relative, root, whitelist, blockers):
    """Whitelist + root confinement + real hash, checked against the file on disk."""
    if not isinstance(relative, str) or ".." in relative:
        blockers.append("asset_path_invalid: " + str(relative))
        return
    if relative not in whitelist:
        blockers.append("asset_not_whitelisted: " + relative)
        return
    resolved = os.path.realpath(os.path.join(root, relative))
    if not resolved.startswith(os.path.realpath(root) + os.sep):
        blockers.append("asset_outside_admitted_root: " + relative)
        return
    if not os.path.isfile(resolved):
        blockers.append("asset_missing: " + relative)
        return
    actual = sha256(resolved)
    if actual != whitelist[relative]:
        # The admitted artefact and the file on disk disagree. That is exactly what the whitelist hash
        # exists to catch, so the entry stays pending instead of laundering the drift into the release.
        blockers.append("asset_hash_mismatch: " + relative + " (admitted " + whitelist[relative][:16]
                        + "..., on disk " + actual[:16] + "...)")


def apply_override(display, changes):
    """
    Replace whole contexts from the calibration file, leaving every other context as derived.

    A context is replaced as a unit (rotation + translation + scale together) so a half-applied
    calibration cannot exist. Malformed triples are still caught by `validate_display` downstream.
    """
    if not changes:
        return display
    merged = OrderedDict(display or {})
    for context, values in changes.items():
        if context == "inheritance":
            merged["inheritance"] = values
            continue
        frame = OrderedDict()
        for key in ("rotation", "translation", "scale"):
            if key in values:
                frame[key] = [float(v) for v in values[key]]
        merged[context] = frame
    return merged


def build_entry(asset, evidence_by_id, candidates, whitelist, admitted_root, overrides):
    asset_id = asset["id"]
    entry_id = "eldencombat:" + asset_id
    route = route_of(asset)
    providers = asset.get("providerId")
    kind = own_item_kind(asset_id)
    primary = asset["variants"][0]
    canvas = primary["size"]
    entry = OrderedDict()
    entry["id"] = entry_id
    entry["status"] = "pending"
    entry["route"] = route
    entry["kind"] = kind
    entry["assetManifest"] = "art/equipment-2d/manifest-note"
    entry["assetManifest"] = "art/equipment-2d/full/equipment-42.json"
    entry["assetId"] = asset_id
    blockers = []
    notes = []
    candidate = candidates.get(asset_id)
    display = candidate["display"] if candidate else None
    display_blocking = candidate.get("displayBlocking") if candidate else None
    override = overrides.get(asset_id) or {}
    override_contexts = override.get("contexts") or {}
    override_blocking = override.get("blocking") or {}
    calibrated = bool(override_contexts or override_blocking)

    if route == "own_sprite":
        held = OrderedDict()
        for variant in asset["variants"]:
            held[variant["state"]] = variant["file"]
        gui = OrderedDict()
        for image in asset.get("gui", []):
            gui[image["state"].replace("gui_", "")] = image["file"]
        entry["held"] = held
        entry["gui"] = gui
        entry["geometry"] = OrderedDict([
            ("canvas", canvas),
            ("anchorPixels", asset["anchorPixels"]),
            ("worldLengthBlocks", round(asset["worldLengthBlocks"], 12)),
            ("sourceSpanPixels", round(asset["sourceSpanPixels"], 12)),
        ])
        entry["derived"] = OrderedDict([
            ("anchorLocalModelUnits", anchor_model_units(canvas, asset["anchorPixels"])),
            ("uniformXYScale", uniform_scale(canvas, asset["worldLengthBlocks"], asset["sourceSpanPixels"])),
            ("note", "a=(16*x/W-8, 8-16*y/H, 0) in centred model units; aBlock=a/16; "
                     "s=worldLengthBlocks*W/sourceSpanPixels"),
        ])
        state_notes, blocked = own_state_support(asset_id)
        notes.extend(state_notes)
        entry["states"] = OrderedDict([("supported", sorted(held)), ("blocked", blocked)])
        entry["displayPolicy"] = "own_calibrated_display_required"
        entry["leftHand"] = OrderedDict([
            ("anchorPixels", (asset.get("leftHand") or {}).get("anchorPixels")),
            ("policy", "the candidate display declares the left hand explicitly; it is not a "
                       "horizontal flip of the texture"),
        ])
        if asset.get("legacySelectionPreserved"):
            entry["legacySelectionPreserved"] = True
            notes.append("legacy_selection_preserved: the 2D route is a candidate alongside the "
                         "user-selected legacy source and does not overwrite it")
        if candidate:
            entry["displaySource"] = OrderedDict([
                ("kind", "derived_candidate"),
                ("reference", candidate["reference"]["source"]),
                ("referenceImage", candidate["reference"]["image"]),
                ("referenceAnchorPixels", candidate["reference"]["anchorPixels"]),
                ("measuredSpriteAxisDegrees", candidate["axis"]["measuredSpriteAxisDegrees"]),
                ("appliedAxisDegrees", candidate["axis"]["appliedAxisDegrees"]),
                ("notes", candidate["axis"]["notes"]),
            ])
            notes.extend(candidate["axis"]["notes"])
        if calibrated:
            # The candidate parameters and the human calibration live in separate files, and this
            # generator only ever reads the override file: re-running it can never silently wipe a
            # value the user tuned after looking at the game.
            display = apply_override(display, override_contexts)
            display_blocking = apply_override(display_blocking, override_blocking)
            replaced = sorted(list(override_contexts) + ["blocking:" + c for c in override_blocking])
            source = entry.setdefault("displaySource", OrderedDict())
            source["kind"] = ("derived_candidate+user_calibration" if candidate
                              else "user_calibration")
            source["calibratedContexts"] = replaced
            notes.append("user_calibration: " + ", ".join(replaced)
                         + " replaced from " + os.path.relpath(OVERRIDES, ROOT)
                         + "; the derivation is kept as the recoverable baseline")
        validate_display(asset_id, display, blockers, notes)
        for path in list(held.values()) + list(gui.values()):
            verify_asset(path, admitted_root, whitelist, blockers)
        entry["display"] = display
        entry["displayBlocking"] = display_blocking
        entry["displayStatus"] = ("calibrated_not_game_verified" if calibrated
                                  else "candidate_not_game_verified" if candidate
                                  else "missing")
    else:
        evidence = evidence_by_id[asset_id]
        namespace = providers.split(":", 1)[0]
        models = [v["modelId"] for v in evidence["variants"]]
        entry["providerMod"] = namespace
        entry["model"] = models[0]
        entry["providerItem"] = providers
        entry["stateModels"] = [OrderedDict([
            ("state", v["state"]),
            ("model", v["modelId"]),
            ("predicate", v.get("predicate") or {}),
            ("layers", [i["layer"] for i in v.get("images", [])]),
        ]) for v in evidence["variants"]]
        entry["predicateBindings"] = predicate_bindings(asset_id, evidence, kind)
        entry["gui"] = OrderedDict()
        entry["guiPolicy"] = ("use the provider model GUI path; no research thumbnail is generated or "
                              "packaged")
        entry["displayPolicy"] = "inherit_provider_display"
        entry["display"] = None
        entry["displayBlocking"] = None
        entry["displayStatus"] = "not_required_inherit_provider"
        entry["displaySource"] = OrderedDict([
            ("kind", "provider_baked_model_display"),
            ("model", models[0]),
            ("note", "the provider's own display block is the single base transform; no wrapper and no "
                     "second display is stacked on it"),
        ])
        entry["loader"] = OrderedDict([
            ("resolution", "provider_baked_model"),
            ("preserved", ["parent chain", "custom loader", "additional layers", "override resolution",
                           "context transforms"]),
            ("note", "the actual provider BakedModel is reused; the chain is never flattened to layer0"),
        ])
        coating = any("coating" in [i["layer"] for i in v.get("images", [])]
                      for v in evidence["variants"])
        if coating:
            entry["loader"]["coatingLayer"] = True
            entry["loader"]["loaderId"] = "spartan_weaponry_unofficial:oil_coated_item"
            notes.append("provider_loader_oil_coating: the stand-by chain carries the oil_coated_item "
                         "loader and a coating layer; the real baked model is reused so the loader and "
                         "its layer survive, and no oil-coating play is added. Whether the own stack "
                         "satisfies the loader's premise is checked at runtime before the chain is used")
        if namespace != "minecraft":
            entry["providerJarSha256"] = SPARTAN_JAR_SHA256
            notes.append("provider_not_installed_evidence: installed=null and "
                         "runtimeRegistryVerified=false was recorded when the evidence was collected; "
                         "it is not proof of absence and is re-probed on the running instance before "
                         "the chain is used")
        notes.append("provider_item_type: the own item is registered as " + kind + " and is not the "
                     "provider item class. That alone is not a reason to reject a reusable visual "
                     "model; behaviour tied to the concrete provider type is checked per item, and a "
                     "captured provider callback is never reused or cast")
        unsupported = [b for b in entry["predicateBindings"]
                       if b["support"] not in ("supported", "supported_alias_required")]
        if unsupported:
            notes.append("evidence_only_states: "
                         + ", ".join(sorted({b["predicate"] for b in unsupported}))
                         + " (recorded, never faked; the state it guards is not selectable and the "
                           "stand-by pose stays reachable)")
        required_alias = [b for b in entry["predicateBindings"]
                          if b["support"] == "supported_alias_required"]
        if required_alias:
            notes.append("predicate_alias: alias properties are registered on the own item in the "
                         "provider namespace and computed from the real stack; visual confirmation in "
                         "game is part of the acceptance receipt, not a technical precondition")

    entry["acceptance"] = OrderedDict([
        ("state", "accepted" if override.get("accepted") else
                  "calibrated_pending_visual" if calibrated else "pending_visual"),
        ("humanAccepted", bool(override.get("accepted"))),
        ("displayStatus", entry["displayStatus"]),
        ("note", "visual acceptance is a receipt; it never gates readiness and it never blocks a "
                 "structurally complete candidate from rendering"
                 + ("; humanAccepted comes from display-overrides.json, the user's own record" if
                    override.get("accepted") else "")),
    ])
    entry["fallback"] = "legacy_builtin_entity"
    entry["blockers"] = blockers
    entry["notes"] = notes
    entry["runtimeIntegrated"] = not blockers
    entry["inGameAccepted"] = False
    entry["status"] = "pending" if blockers else "ready"
    return entry


def main():
    current = load(ROUTES)
    manifest = load(MANIFEST)
    publishable = load(PUBLISHABLE)
    evidence = load(EVIDENCE)
    candidate_document = load(CANDIDATES)
    candidates = candidate_document["items"]
    override_document = load(OVERRIDES)
    overrides = override_document.get("overrides") or {}
    evidence_by_id = {item["id"]: item for item in evidence["items"]}

    expected = registered_ids()
    if len(set(expected)) != 42:
        sys.exit("expected 42 registered equipment IDs, found " + str(len(set(expected))))
    assets_by_id = {a["id"]: a for a in manifest["assets"]}
    missing = [i for i in expected if i.split(":", 1)[1] not in assets_by_id]
    extra = [a["id"] for a in manifest["assets"]
             if "eldencombat:" + a["id"] not in expected]
    if missing or extra:
        sys.exit("ID set mismatch; missing=%s extra=%s" % (missing, extra))

    whitelist = {item["file"]: item["sha256"] for item in publishable["include"]}
    if len(whitelist) != 94:
        sys.exit("publishable whitelist must hold 94 own PNGs, found " + str(len(whitelist)))

    equipment = [build_entry(assets_by_id[i.split(":", 1)[1]], evidence_by_id, candidates,
                             whitelist, os.path.join(ROOT, "art/equipment-2d/full"), overrides)
                 for i in sorted(expected)]

    admitted_own = [e["id"] for e in equipment
                    if e["route"] == "own_sprite" and e["status"] == "ready"]
    admitted_provider = [e["id"] for e in equipment
                         if e["route"] != "own_sprite" and e["status"] == "ready"]

    out = OrderedDict()
    out["version"] = current["version"]
    out["equipmentSchemaVersion"] = 2
    out["equipmentAdmission"] = OrderedDict([
        ("assetManifest", "art/equipment-2d/full/equipment-42.json"),
        ("publishableManifest", "art/equipment-2d/full/publishable-assets.json"),
        ("providerEvidence", "art/equipment-reuse/research-only-full/evidence.json"),
        ("displayCandidates", "art/equipment-2d/full/display-candidates.json"),
        ("displayOverrides", "art/equipment-2d/full/display-overrides.json"),
        ("calibratedItems", [e["id"] for e in equipment
                             if e["displayStatus"] == "calibrated_not_game_verified"]),
        ("ownSourceRoot", "art/equipment-2d/full"),
        ("deniedRoots", ["art/equipment-reuse/research-only-full", "art/equipment-reuse"]),
        ("providerJarSha256", OrderedDict([("spartan_weaponry_unofficial", SPARTAN_JAR_SHA256)])),
        ("admittedOwnPngs", len(whitelist)),
        ("readyRule", "ready = static input and capability complete; visual acceptance is recorded in "
                      "acceptance and never gates readiness"),
        ("productionNote", "none of the research PNGs or the evidence JAR is packaged; the only own "
                           "PNGs packaged are the whitelisted ones above"),
    ])
    out["ui"] = current["ui"]
    out["equipment"] = equipment
    out["fx"] = current["fx"]
    out["existing"] = current["existing"]
    out["auxiliaryModels"] = current["auxiliaryModels"]
    out["externalProviderStatus"] = ("runtime capability detection implemented: the provider is "
                                     "probed on the running instance and its real baked model is reused "
                                     "when present; installed=null is stale evidence, not a verdict")
    if "externalProviderStatus" in current and isinstance(current["externalProviderStatus"], str):
        out["externalProviderStatus"] = current["externalProviderStatus"]

    with open(ROUTES, "w", encoding="utf-8") as handle:
        json.dump(out, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    ready = sum(1 for e in equipment if e["status"] == "ready")
    print("2D equipment schema v2 written: %d entries, ready=%d, pending=%d"
          % (len(equipment), ready, len(equipment) - ready))
    print("  ready own_sprite: %d, ready provider/vanilla: %d" % (len(admitted_own), len(admitted_provider)))
    for entry in equipment:
        if entry["status"] != "ready":
            print("  PENDING %-26s blockers=%s" % (entry["id"], entry["blockers"]))
    print("  notes recorded for %d entries" % sum(1 for e in equipment if e["notes"]))
    integrity = [(e["id"], b) for e in equipment for b in e["blockers"]
                 if b.startswith("asset_hash_mismatch") or b.startswith("asset_missing")
                 or b.startswith("asset_not_whitelisted")]
    if integrity:
        print()
        print("  !! ASSET INTEGRITY: %d entry/entries stay pending until the admitted artefact and the "
              "file on disk agree:" % len({i for i, _ in integrity}))
        for entry_id, blocker in integrity:
            print("     " + entry_id + " -> " + blocker)
        print("  !! The whitelist and the art manifest still record the admitted hash; the sprite on "
              "disk changed afterwards. Re-issuing the whitelist is the art side's call.")


if __name__ == "__main__":
    main()
