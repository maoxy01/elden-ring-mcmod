"""Build the "effect frame vs same-pose clean frame" evidence sheet for the VFX runtime closure.

Reads the harness log (VFX_SHOT_DIFF lines) + the archived screenshots and produces:
  1. <out>/live-clean-<seg>-<skill>.png  : side-by-side effect frame / clean frame / changed-pixel mask
  2. <out>/live-clean-all.png            : one row per segment (all 18), with the changed-pixel count
Usage: python vfx-live-clean-sheet.py <log> <screenshot-dir> <out-dir>
"""
import os
import re
import sys

from PIL import Image, ImageChops, ImageDraw

DELTA = 24  # must match VfxIntegrationHarness.SUBJECT_CHANNEL_DELTA


def parse(log_path):
    """-> {segment: (skill, best_live, clean, changed)} keeping the largest changed count per segment."""
    pattern = re.compile(
        r"VFX_SHOT_DIFF segment=(\d+) id=(\S+) live=(\S+) clean=(\S+) changed=(\d+) total=(\d+) threshold=(\d+)")
    best = {}
    with open(log_path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = pattern.search(line)
            if not match:
                continue
            segment = int(match.group(1))
            entry = (match.group(2), match.group(3), match.group(4), int(match.group(5)))
            if segment not in best or entry[3] > best[segment][3]:
                best[segment] = entry
    return best


def mask(live, clean):
    diff = ImageChops.difference(live.convert("RGB"), clean.convert("RGB")).convert("L")
    return diff.point(lambda v: 255 if v >= DELTA else 0)


def main():
    log_path, shots, out = sys.argv[1], sys.argv[2], sys.argv[3]
    os.makedirs(out, exist_ok=True)
    best = parse(log_path)
    tiles = []
    for segment in sorted(best):
        skill, live_name, clean_name, changed = best[segment]
        live = Image.open(os.path.join(shots, live_name)).convert("RGB")
        clean = Image.open(os.path.join(shots, clean_name)).convert("RGB")
        masked = mask(live, clean)
        row = Image.new("RGB", (live.width * 3 + 8, live.height + 16), (16, 16, 16))
        for index, image in enumerate((live, clean, masked)):
            row.paste(image, (index * (live.width + 4), 16))
        ImageDraw.Draw(row).text((4, 3), "seg%d %s  changed=%d  (%s vs %s)"
                                 % (segment, skill, changed, live_name, clean_name), fill=(255, 235, 120))
        row.save(os.path.join(out, "live-clean-%02d-%s.png" % (segment, skill)))
        tiles.append(row)
    if tiles:
        width = max(tile.width for tile in tiles)
        sheet = Image.new("RGB", (width, sum(tile.height + 6 for tile in tiles)), (0, 0, 0))
        offset = 0
        for tile in tiles:
            sheet.paste(tile, (0, offset))
            offset += tile.height + 6
        sheet.save(os.path.join(out, "live-clean-all.png"))
        print("wrote %d rows, sheet %s" % (len(tiles), sheet.size))


if __name__ == "__main__":
    main()
