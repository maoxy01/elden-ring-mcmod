"""量作者美术帧的"墨量"：每个路由每帧的不透明像素数、包围盒、以及墨/框比。

目的：回答"作者美术在屏幕上到底有多少可看的像素"。观感问题（人类反馈"只有第一下有特效"）
不能只看渲染器提交计数，要看素材本身有多少不透明内容、以及这些内容在平面上占多大比例。
纯离线测量，不改任何资源。

用法：python measure_art_ink.py <art/vfx-2d/textures/particle>
"""
import os
import sys

from PIL import Image


def measure(root):
    rows = []
    for route in sorted(os.listdir(root)):
        folder = os.path.join(root, route)
        if not os.path.isdir(folder):
            continue
        frames = sorted(f for f in os.listdir(folder) if f.endswith(".png"))
        if not frames:
            continue
        inks, boxes, sizes, lums, alphas = [], [], set(), [], []
        for name in frames:
            with Image.open(os.path.join(folder, name)) as image:
                image = image.convert("RGBA")
                sizes.add(image.size)
                alpha = list(image.getchannel("A").getdata())
                red = list(image.getchannel("R").getdata())
                green = list(image.getchannel("G").getdata())
                blue = list(image.getchannel("B").getdata())
                box = image.getchannel("A").getbbox()
                inked = [i for i, value in enumerate(alpha) if value > 8]
                inks.append(len(inked))
                boxes.append(0 if box is None else (box[2] - box[0]) * (box[3] - box[1]))
                if inked:
                    # 感知亮度：夜间深色背景上"黑色弧刃"的对比度就靠这个数字说话
                    lums.append(sum(0.2126 * red[i] + 0.7152 * green[i] + 0.0722 * blue[i] for i in inked) / len(inked))
                    alphas.append(sum(alpha[i] for i in inked) / len(inked))
        width, height = sorted(sizes)[0]
        plane = width * height
        rows.append((route, len(frames), f"{width}x{height}", sum(inks) / len(inks), max(inks),
                     sum(boxes) / len(boxes), 100.0 * sum(inks) / len(inks) / plane,
                     sum(lums) / max(1, len(lums)), sum(alphas) / max(1, len(alphas))))
    print(f"{'route':14s} {'frames':>6s} {'size':>10s} {'avgInk':>8s} {'maxInk':>7s} {'avgBox':>8s} {'ink/plane':>9s} {'inkLum':>7s} {'inkAlpha':>8s}")
    for route, frames, size, avg_ink, max_ink, avg_box, ratio, lum, alpha in rows:
        print(f"{route:14s} {frames:6d} {size:>10s} {avg_ink:8.0f} {max_ink:7.0f} {avg_box:8.0f} {ratio:8.2f}% {lum:7.1f} {alpha:8.1f}")


if __name__ == "__main__":
    measure(sys.argv[1] if len(sys.argv) > 1 else "art/vfx-2d/textures/particle")
