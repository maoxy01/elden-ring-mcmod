"""Build contact sheets for the VFX closure evidence so every captured frame gets eyeballed.

Usage: python vfx-contact-sheet.py <screenshot-dir> <out-dir> [cols]
Groups files by their vfx-<segment>-<skill>-<frame>.png naming, sorts by segment then frame.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

src = Path(sys.argv[1])
out = Path(sys.argv[2])
cols = int(sys.argv[3]) if len(sys.argv) > 3 else 4
out.mkdir(parents=True, exist_ok=True)

shots = sorted(src.glob("vfx-*.png"), key=lambda p: (p.name.split("-")[1], p.name.split("-")[3]))
if not shots:
    raise SystemExit(f"no screenshots in {src}")

thumb_w, thumb_h = 480, 270
rows_per_sheet = 6
per_sheet = cols * rows_per_sheet
try:
    font = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 16)
except Exception:
    font = ImageFont.load_default()

written = []
for index in range(0, len(shots), per_sheet):
    batch = shots[index:index + per_sheet]
    height = ((len(batch) + cols - 1) // cols) * (thumb_h + 26) + 8
    sheet = Image.new("RGB", (cols * (thumb_w + 8) + 8, height), (18, 18, 22))
    draw = ImageDraw.Draw(sheet)
    for position, path in enumerate(batch):
        row, column = divmod(position, cols)
        x = 8 + column * (thumb_w + 8)
        y = 8 + row * (thumb_h + 26)
        with Image.open(path) as image:
            image = image.convert("RGB").resize((thumb_w, thumb_h), Image.LANCZOS)
        sheet.paste(image, (x, y))
        draw.text((x + 2, y + thumb_h + 4), path.name, fill=(235, 235, 235), font=font)
    target = out / f"sheet-{index // per_sheet + 1:02d}.png"
    sheet.save(target)
    written.append(target)

print("|".join(str(p) for p in written))
