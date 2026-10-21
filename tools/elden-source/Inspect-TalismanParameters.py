"""Read selected talismans from a local Elden Ring installation; never modify it.

Reuses the project's existing SoulsFormats-derived binder/DCX/FMG readers.
PARAM field layout comes from soulsmods/Paramdex. Output is research evidence,
not a Minecraft datapack and not an assertion about observed engine behavior.
"""
import argparse
from fractions import Fraction
import hashlib
import json
from math import ceil
from pathlib import Path, PureWindowsPath
import re
import struct
import sys
import xml.etree.ElementTree as ET


def layout(definition):
    position = Fraction(0)
    fields = {}
    codes = {"u8": "B", "s8": "b", "u16": "H", "s16": "h",
             "u32": "I", "s32": "i", "u64": "Q", "s64": "q", "f32": "f"}
    for field in ET.parse(definition).findall("./Fields/Field"):
        kind, rest = field.attrib["Def"].split(" ", 1)
        name, _, default = rest.partition(" = ")
        if ":" in name:
            position += Fraction(int(name.rsplit(":", 1)[1]), 8)
        elif "[" in name:
            position = Fraction(ceil(position)) + int(re.search(r"\[(\d+)\]", name)[1])
        else:
            position = Fraction(ceil(position))
            fields[name] = (int(position), codes[kind], float(default or 0))
            position += Fraction(struct.calcsize(codes[kind]))
    return fields, ceil(position)


def rows(data, definition):
    fields, expected_stride = layout(definition)
    if data[44] != 0 or not data[45] & 4:
        raise ValueError("Unsupported PARAM header; refusing to guess offsets")
    count = struct.unpack_from("<H", data, 10)[0]
    headers = [struct.unpack_from("<iiqq", data, 64 + index * 24) for index in range(count)]
    offsets = sorted({row[2] for row in headers})
    strides = {right - left for left, right in zip(offsets, offsets[1:])}
    if strides != {expected_stride}:
        raise ValueError(f"{definition.name}: layout {expected_stride}, observed {strides}")
    decoded = {ident: {name: struct.unpack_from("<" + code, data, start + offset)[0]
                       for name, (offset, code, _) in fields.items()}
               for ident, _, start, _ in headers}
    return decoded, fields, expected_stride


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game", type=Path, required=True)
    parser.add_argument("--extract-root", type=Path, required=True)
    parser.add_argument("--accessory-def", type=Path, required=True)
    parser.add_argument("--effect-def", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    sys.path.insert(0, str(args.extract_root))
    import extract_selected
    if extract_selected.GAME.resolve() != args.game.resolve():
        raise ValueError("Existing DCX reader is configured for a different game directory")
    from formats import binder, dcx, fmg
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

    source = args.game / "regulation.bin"
    encrypted = source.read_bytes()
    # Existing read-only weapon_index.py uses the same regulation AES format.
    key = bytes.fromhex("99bffc366a6bc8c6f5827d093602d676c42892a01c207fb024d3af4e493fef99")
    decryptor = Cipher(algorithms.AES(key), modes.CBC(encrypted[:16])).decryptor()
    regulation = dcx(decryptor.update(encrypted[16:]) + decryptor.finalize())
    wanted = {"EquipParamAccessory": args.accessory_def, "SpEffectParam": args.effect_def}
    tables, metadata = {}, {}
    for meta, content in binder(regulation):
        name = PureWindowsPath(meta["name"]).stem
        if name in wanted:
            tables[name], fields, stride = rows(content, wanted[name])
            metadata[name] = {"rows": len(tables[name]), "stride": stride,
                              "definitionSha256": hashlib.sha256(wanted[name].read_bytes()).hexdigest(),
                              "paramSha256": hashlib.sha256(content).hexdigest(), "fields": fields}
    if set(tables) != set(wanted):
        raise ValueError("Regulation is missing one of the required tables")

    names, messages = {}, []
    for language in ("engus", "zhocn"):
        names[language] = {}
        for suffix in ("", "_dlc01", "_dlc02"):
            path = args.extract_root / f"raw/msg/{language}/item{suffix}.msgbnd.dcx"
            if not path.is_file():
                continue
            raw = path.read_bytes()
            messages.append({"path": str(path), "sha256": hashlib.sha256(raw).hexdigest()})
            for meta, content in binder(raw):
                if PureWindowsPath(meta["name"]).stem.startswith("AccessoryName"):
                    names[language].update(fmg(content))

    selected = {"Green Turtle Talisman", "Radagon's Soreseal", "Warrior Jar Shard",
                "Shard of Alexander", "Dragoncrest Shield Talisman",
                "Dragoncrest Shield Talisman +1", "Dragoncrest Shield Talisman +2",
                "Dragoncrest Greatshield Talisman"}
    results = []
    effect_fields = metadata["SpEffectParam"]["fields"]
    for ident, accessory in tables["EquipParamAccessory"].items():
        english = names["engus"].get(ident)
        if english not in selected:
            continue
        refs = {accessory[field] for field in ("refId", "residentSpEffectId1", "residentSpEffectId2",
                                              "residentSpEffectId3", "residentSpEffectId4")
                if accessory.get(field, -1) > 0}
        pending, visited, effects = list(sorted(refs)), set(), []
        while pending:
            effect_id = pending.pop(0)
            if effect_id in visited:
                continue
            visited.add(effect_id)
            if len(visited) > 32:
                raise ValueError("Unexpectedly large effect chain")
            row = tables["SpEffectParam"].get(effect_id)
            if row is None:
                raise ValueError(f"Missing referenced effect {effect_id}")
            different = {name: value for name, value in row.items()
                         if abs(value - effect_fields[name][2]) > 1e-6}
            effects.append({"id": effect_id, "nonDefaultFields": different})
            for field in ("replaceSpEffectId", "cycleOccurrenceSpEffectId", "atkOccurrenceSpEffectId"):
                if row.get(field, -1) > 0:
                    pending.append(row[field])
        results.append({"accessoryId": ident, "nameEn": english, "nameZh": names["zhocn"].get(ident),
                        "accessory": accessory, "effects": effects})
    if {item["nameEn"] for item in results} != selected:
        raise ValueError("Expected eight named accessories; localization/source selection needs review")
    for value in metadata.values():
        del value["fields"]
    report = {"status": "local_param_observation_not_runtime_acceptance",
              "game": str(args.game), "regulationSha256": hashlib.sha256(encrypted).hexdigest(),
              "regulationVersionRaw": regulation[24:32].decode("ascii").rstrip("\0"),
              "tables": metadata, "messageSources": messages, "talismans": results,
              "limitations": ["Bitfields are skipped, not interpreted as false.",
                              "Values and links are observed; event semantics require engine/source corroboration.",
                              "Localized names use previously extracted message archives, not live archives."]}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(args.output), "regulationSha256": report["regulationSha256"],
                      "tables": metadata, "talismans": [{"id": item["accessoryId"], "name": item["nameEn"],
                                                          "effectIds": [e["id"] for e in item["effects"]]}
                                                         for item in results]}, ensure_ascii=True))


if __name__ == "__main__":
    main()
