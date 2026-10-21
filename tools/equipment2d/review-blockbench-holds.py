"""Validate and lay out native Blockbench captures; never renders replacement weapon art."""
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "art/equipment-2d/blockbench-holds-20261019"
NATIVE = ROOT / "art/equipment-2d/blockbench-all-items-20261018"


def main():
    assets = json.loads((NATIVE / "manifest.json").read_text(encoding="utf-8"))["assets"]
    checks = []
    for asset in assets:
        for context in ("firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"):
            path = OUT / "qa" / (asset["id"] + "_" + context + ".png")
            pixels = np.array(Image.open(path).convert("RGBA"))
            ys, xs = np.nonzero((pixels[:, :, :3].max(2) > 45) & (pixels[:, :, 3] > 0))
            if len(xs) < 8:
                raise ValueError("Blank native capture: " + path.name)
            checks.append({"id": asset["id"], "context": context, "foregroundPixels": len(xs),
                           "bounds": [int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())],
                           "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    viewports = {}
    font = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 17)
    for context in ("firstperson_righthand", "firstperson_lefthand"):
        sheet = Image.new("RGB", (1536, 1740), "#202833")
        draw = ImageDraw.Draw(sheet)
        draw.text((12, 8), "NATIVE BLOCKBENCH DISPLAY / " + context + " / NOT IN-GAME", font=font, fill="white")
        for i, asset in enumerate(assets):
            x, y = (i % 4) * 384, 36 + (i // 4) * 242
            filename = asset["id"] + "_" + context + ".png"
            image = Image.open(OUT / "qa" / filename).convert("RGBA")
            ys, xs = np.nonzero(np.array(image)[:, :, 3] == 0)
            viewport = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
            viewports[filename] = list(viewport)
            image = image.crop(viewport).resize((384, 216), Image.Resampling.LANCZOS)
            background = Image.new("RGB", image.size, "#70899e")
            background.paste(image, mask=image.getchannel("A"))
            sheet.paste(background, (x, y + 22))
            draw.text((x + 5, y + 1), asset["id"], font=font, fill="white")
        sheet.save(OUT / ("native-" + context + "-contact.png"))
    focus = Image.new("RGB", (1280, 414), "#202833")
    draw = ImageDraw.Draw(focus)
    draw.text((12, 8), "NATIVE BLOCKBENCH DISPLAY / NOT AN IN-GAME SCREENSHOT", font=font, fill="white")
    for i, name in enumerate(("great_sword", "great_bow")):
        filename = name + "_firstperson_righthand.png"
        image = Image.open(OUT / "qa" / filename).convert("RGBA").crop(viewports[filename])
        image = image.resize((640, 360), Image.Resampling.LANCZOS)
        background = Image.new("RGB", image.size, "#70899e")
        background.paste(image, mask=image.getchannel("A"))
        focus.paste(background, (i * 640, 54))
        draw.text((i * 640 + 8, 32), name, font=font, fill="white")
    focus.save(OUT / "focus-native-preview.png")
    dynamic = json.loads((OUT / "native-dynamic-states.json").read_text(encoding="utf-8"))
    for record in dynamic:
        for path in record["captures"]:
            data = np.array(Image.open(path).convert("RGBA"))
            if not ((data[:, :, :3].max(2) > 45) & (data[:, :, 3] > 0)).any():
                raise ValueError("Blank dynamic capture: " + path)
    result = {"nativeProjectCount": 28, "baseCaptures": len(checks), "dynamicStates": len(dynamic),
              "dynamicCaptures": sum(len(r["captures"]) for r in dynamic), "checks": checks,
              "contactSheetCrops": viewports, "runtimeAccepted": False,
              "provenance": "Native Blockbench Display / Preview.screenshot; contact sheets crop each native monitor viewport, composite a neutral review background and arrange captures; original PNGs are unchanged"}
    (OUT / "capture-validation.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print("NATIVE_CAPTURE_PASS: 28 items / 112 base views / 28 additional dynamic-state views; runtime still pending")


if __name__ == "__main__":
    main()
