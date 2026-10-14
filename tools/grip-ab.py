"""A/B 对比裁图工具（只依赖 Pillow）。

用法：
  python crop.py <out.png> <zoom> <x0,y0,x1,y1> <img...>

把若干张截图按同一区域裁剪、放大 zoom 倍，纵向拼接（第一张在最上面），
每张图上标注其文件名，便于"原版 vs 我们"的对照。
"""
import sys
from PIL import Image, ImageDraw

def main() -> int:
    out = sys.argv[1]
    zoom = float(sys.argv[2])
    x0, y0, x1, y1 = (int(v) for v in sys.argv[3].split(","))
    paths = sys.argv[4:]
    tiles = []
    for p in paths:
        im = Image.open(p).convert("RGB").crop((x0, y0, x1, y1))
        if zoom != 1.0:
            im = im.resize((int(im.width * zoom), int(im.height * zoom)), Image.NEAREST)
        label = p.replace("\\", "/").rsplit("/", 1)[-1]
        tile = Image.new("RGB", (im.width, im.height + 16), (16, 16, 16))
        tile.paste(im, (0, 16))
        ImageDraw.Draw(tile).text((4, 4), label, fill=(255, 220, 90))
        tiles.append(tile)
    w = max(t.width for t in tiles)
    h = sum(t.height for t in tiles)
    sheet = Image.new("RGB", (w, h), (16, 16, 16))
    y = 0
    for t in tiles:
        sheet.paste(t, (0, y))
        y += t.height
    sheet.save(out)
    print(f"{out} {sheet.width}x{sheet.height} <- {len(tiles)} tiles")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
