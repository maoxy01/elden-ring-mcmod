"""Diagnostic projection only; no Minecraft or Blockbench capture."""
import importlib.util
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("review", ROOT / "tools/equipment2d/review-carry-candidate.py")
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)
report = json.loads((OUT / "verification.json").read_text())
entries = {e.get("assetId"): e for e in json.loads((ROOT / "src/main/resources/assets/eldencombat/routes/2d.json").read_text())["equipment"]}
names = list(dict.fromkeys(c["id"] for c in report["checks"]))
w, h, label = 360, 225, 24
sheet = Image.new("RGB", (w * 4, 48 + (h + label) * len(names)), "#202733")
draw = ImageDraw.Draw(sheet)
draw.text((8, 4), "MATHEMATICAL PROJECTION / NOT A GAME CAPTURE", fill="white")
for col, title in enumerate(("RIGHT before", "RIGHT corrected", "LEFT before", "LEFT corrected")):
    draw.text((col * w + 8, 26), title, fill="white")
camera = review.maths.camera_points
for row, name in enumerate(names):
    for left in (False, True):
        context = "firstperson_lefthand" if left else "firstperson_righthand"
        check = next(c for c in report["checks"] if c["id"] == name and c["context"] == context)
        # The existing raster helper defaults to the right hand; override BOTH
        # the display handedness and camera hand translation for left panels.
        review.maths.camera_points = lambda frame, points, unused, left=left: camera(frame, points, left)
        for version, key in enumerate(("before", "after")):
            panel = Image.new("RGB", (w, h), "#6f8fa8")
            pd = ImageDraw.Draw(panel)
            pd.rectangle((0, h * .6, w, h), fill="#526740")
            pd.line((w / 2 - 4, h / 2, w / 2 + 4, h / 2), fill="white")
            review.draw_weapon(panel, entries[name], check[key])
            x, y = (2 * int(left) + version) * w, 48 + row * (h + label)
            sheet.paste(panel, (x, y + label))
            draw.text((x + 4, y + 4), name, fill="white")
sheet.save(OUT / "comparison-all.png")
sheet.crop((0, 0, w * 4, 48 + (h + label) * 2)).save(OUT / "comparison-focus.png")
