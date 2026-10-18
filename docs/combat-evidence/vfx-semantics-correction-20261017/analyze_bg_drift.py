"""定位两张 CLEAN 参照帧之间"变了哪些像素"：变化框 + 8x8 网格分布 + 平均颜色差。

用途：固定夹具报"pre/post CLEAN 参照差 3k~9k 像素（上限 2049）"时，判断漂移是
演员/影子（集中在施法者附近，可被"扫过区域"扣除）还是场景/光照（整屏，属于夹具不受控）。

用法：python analyze_bg_drift.py <pre.png> <post.png> [threshold]
"""
import sys

from PIL import Image


def analyze(pre_path, post_path, threshold=24):
    pre = Image.open(pre_path).convert("RGB")
    post = Image.open(post_path).convert("RGB")
    width, height = pre.size
    a = list(pre.getdata())
    b = list(post.getdata())
    cells = {}
    count = 0
    min_x, min_y, max_x, max_y = width, height, -1, -1
    sum_delta = [0, 0, 0]
    for index, (pa, pb) in enumerate(zip(a, b)):
        delta = max(abs(pa[0] - pb[0]), abs(pa[1] - pb[1]), abs(pa[2] - pb[2]))
        if delta <= threshold:
            continue
        count += 1
        x, y = index % width, index // width
        min_x, min_y = min(min_x, x), min(min_y, y)
        max_x, max_y = max(max_x, x), max(max_y, y)
        for channel in range(3):
            sum_delta[channel] += abs(pa[channel] - pb[channel])
        cell = (x // 64, y // 64)
        bucket = cells.setdefault(cell, [0, 0, 0, 0])
        bucket[0] += 1
    print(f"changed={count} of {width * height} ({100.0 * count / (width * height):.2f}%)")
    if count:
        print(f"bbox=({min_x},{min_y})-({max_x},{max_y})  size={max_x - min_x + 1}x{max_y - min_y + 1}")
        print(f"mean |delta| per channel = {[round(value / count, 1) for value in sum_delta]}")
    ranked = sorted(cells.items(), key=lambda item: -item[1][0])[:8]
    for (cell_x, cell_y), bucket in ranked:
        print(f"  cell({cell_x:2d},{cell_y:2d}) px=({cell_x * 64},{cell_y * 64})-({cell_x * 64 + 63},{cell_y * 64 + 63}) changed={bucket[0]}")


if __name__ == "__main__":
    analyze(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 24)
