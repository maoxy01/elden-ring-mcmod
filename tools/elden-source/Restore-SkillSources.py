"""Recover only the previously indexed player skills into the local research cache.

Reuses the existing SoulsFormats-derived, read-only archive readers and Nuxe
file dictionary. Never writes to the game installation or mod resources.
"""
import hashlib
import json
from pathlib import Path, PureWindowsPath
import re
import struct
import sys

WORKSPACE = Path(__file__).resolve().parents[2]
ROOT = WORKSPACE / "build/elden-source"
sys.path.insert(0, str(WORKSPACE / "dist/elden-ring-icons-extract"))
from extract_selected import GAME, KEYS, header_bytes, path_hash
from formats import binder
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

# Previously verified TAE -> HKX mapping, recorded in the extraction report.
SKILLS = {
    814: ("Taker's Flames", ["a668_040000"]),
    820: ("Wave of Gold", ["a820_040000"]),
    827: ("Cursed-Blood Slice", ["a827_040000", "a827_040010"]),
    828: ("Waterfowl Dance", ["a828_040000", "a828_040010", "a828_040020"]),
    829: ("Gold Breaker", ["a829_040000", "a829_040004"]),
    832: ("Starcaller Cry", ["a832_040000", "a832_040010"]),
    834: ("Bloodboon Ritual", ["a834_040000", "a834_040010", "a834_040020"]),
    838: ("Destined Death", ["a838_040000"]),
}


def digest(data):
    return hashlib.sha256(data).hexdigest().upper()


def persist(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.read_bytes() != data:
            raise ValueError(f"Refusing conflicting existing file: {path}")
    else:
        path.write_bytes(data)


def main():
    # Headers and executable are checked before/after; the archive is opened rb.
    guards = [GAME / "eldenring.exe", GAME / "Data3.bhd"]
    before = {str(p): digest(p.read_bytes()) for p in guards}
    header = header_bytes("Data3")
    names = {path_hash(p): p for p in (KEYS / "Hash/Data3.txt").read_text(encoding="utf-8-sig").splitlines()
             if re.fullmatch(r"/chr/c0000(?:_[A-Za-z0-9_]+)?\.anibnd\.dcx", p)}
    count, offset = struct.unpack_from("<ii", header, 16)
    entries = []
    for bucket in range(count):
        num, start = struct.unpack_from("<ii", header, offset + bucket * 8)
        for i in range(num):
            ident, padded, size, data_offset, _, aes = struct.unpack_from("<Qiiqqq", header, start + i * 40)
            if ident in names:
                entries.append((names[ident], padded, size, data_offset, aes))
    total = sum(e[1] for e in entries)
    if not entries or total > 256 * 1024**2:
        raise ValueError(f"Unexpected selection: {len(entries)} archives, {total} bytes")
    wanted = {clip + ".hkx" for _, clips in SKILLS.values() for clip in clips}
    taes = {f"a{group}.tae" for group in SKILLS}
    found, manifests = {}, []
    with (GAME / "Data3.bdt").open("rb") as archive:
        for name, padded, size, start, aes in entries:
            archive.seek(start)
            data = bytearray(archive.read(padded))
            if len(data) != padded:
                raise ValueError("Archive range truncated")
            if aes:
                key = header[aes:aes + 16]
                ranges, = struct.unpack_from("<i", header, aes + 16)
                for i in range(ranges):
                    begin, end = struct.unpack_from("<qq", header, aes + 20 + i * 16)
                    if begin < 0 or end < 0 or begin == end:
                        continue
                    if not 0 <= begin < end <= len(data) or (end - begin) % 16:
                        raise ValueError("Invalid AES archive range")
                    decryptor = Cipher(algorithms.AES(key), modes.ECB()).decryptor()
                    data[begin:end] = decryptor.update(bytes(data[begin:end])) + decryptor.finalize()
            data = bytes(data[:size] if size > 0 else data)
            source = ROOT / "extracted-player" / name.lstrip("/")
            persist(source, data)
            destination = ROOT / "unpacked-player" / source.name.replace(".dcx", "").replace(".", "-")
            selected = []
            for meta, content in binder(data):
                filename = PureWindowsPath(meta["name"]).name.lower()
                if filename not in wanted | taes | {"skeleton.hkx"} and not filename.endswith(".compendium"):
                    continue
                target = destination / filename
                persist(target, content)
                record = {"Local": str(target), "Sha256": digest(content), "Bytes": len(content)}
                selected.append(record)
                if filename in wanted:
                    if filename in found:
                        raise ValueError(f"Duplicate clip across containers: {filename}")
                    found[filename] = record
            manifests.append({"Source": str(source), "Sha256": digest(data), "Entries": selected})
            print(f"{source.name}: {len(data):,} bytes, {len(selected)} retained entries", flush=True)
    if set(found) != wanted:
        raise ValueError(f"Missing clips: {sorted(wanted - set(found))}")
    index = {"MappingReference": "docs/ELDEN-RING-ANIMATION-EXTRACTION-REPORT.md",
             "Status": "Restored prior verified clip selection; TAE event semantics not re-decoded.",
             "Skills": [{"Name": name, "TaeGroup": group,
                         "Animations": [{"EventAnimationId": 40000 + i,
                                         "Clips": [found[clip + '.hkx']]} for i, clip in enumerate(clips)]}
                        for group, (name, clips) in SKILLS.items()]}
    after = {str(p): digest(p.read_bytes()) for p in guards}
    if before != after:
        raise ValueError("Game executable or header changed")
    ROOT.mkdir(parents=True, exist_ok=True)
    (ROOT / "skill-animation-index.json").write_text(json.dumps(index, indent=2), encoding="utf-8")
    receipt = {"SourceUnchanged": True, "SourceHashes": after, "SelectedBytes": total,
               "ClipCount": len(found), "Binders": manifests,
               "Tools": ["Nuxe 1.2.1 file dictionary", "existing SoulsFormats-derived archive readers"],
               "Distribution": "Local research only, excluded from mod resources and packages"}
    (ROOT / "restoration-receipt.json").write_text(json.dumps(receipt, indent=2), encoding="utf-8")
    print(f"Restored {len(found)} clips, source unchanged.", flush=True)


if __name__ == "__main__":
    main()
