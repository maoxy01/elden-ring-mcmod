"""生成 §2 锻造材料/模板的占位贴图 + 物品模型 + 语言条目。

用途：在 Codex 出正式美术之前，让 41+ 件锻造相关物品在游戏里可辨认、可验收。
正式贴图到位后直接同名覆盖即可（不要改物品 id）。

运行：
    <bundled python> tools/gen_forging_placeholders.py
"""
from __future__ import annotations

import json
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "eldencombat")
MODELS = os.path.join(ASSETS, "models", "item")
TEXTURES = os.path.join(ASSETS, "textures", "item")
LANG = os.path.join(ASSETS, "lang")

# 物品 id -> (底色, 中文名)
ITEMS: dict[str, tuple[tuple[int, int, int], str]] = {}

STONE_SHADES = [(190, 190, 190), (170, 172, 176), (150, 152, 158), (132, 136, 142),
                (114, 118, 126), (96, 100, 110), (78, 82, 94), (60, 64, 78)]
for i, shade in enumerate(STONE_SHADES, start=1):
    ITEMS[f"smithing_stone_{i}"] = (shade, f"锻造石[{i}]")
SOMBER_SHADES = [(196, 176, 216), (176, 152, 206), (156, 128, 196), (136, 104, 186), (116, 80, 176)]
for i, shade in enumerate(SOMBER_SHADES, start=1):
    ITEMS[f"somber_smithing_stone_{i}"] = (shade, f"失色锻造石[{i}]")
ITEMS["ancient_dragon_smithing_stone"] = ((240, 200, 90), "古龙岩锻造石")
ITEMS["ancient_dragon_somber_stone"] = ((214, 150, 240), "古龙岩失色锻造石")

for i in range(1, 9):
    ITEMS[f"upgrade_template_{i}"] = ((150 + i * 6, 110 + i * 4, 70), f"强化模板[{i}]")
for i in range(1, 6):
    ITEMS[f"somber_upgrade_template_{i}"] = ((130 + i * 8, 96, 168 + i * 6), f"失色强化模板[{i}]")

AFFINITY_COLORS = {
    "heavy": ((170, 130, 90), "厚重"),
    "keen": ((150, 200, 220), "锋利"),
    "quality": ((200, 190, 150), "优质"),
    "magic": ((120, 150, 240), "魔力"),
    "fire": ((235, 120, 60), "火焰"),
    "sacred": ((240, 225, 160), "神圣"),
    "blood": ((190, 60, 70), "血"),
    "occult": ((150, 110, 190), "神秘"),
}
for key, (color, label) in AFFINITY_COLORS.items():
    ITEMS[f"affinity_template_{key}"] = (color, f"质变模板·{label}")
ITEMS["reforge_template"] = ((110, 190, 180), "重铸模板")

MATERIALS = {
    "heavy_scale": ((160, 120, 80), "厚重石鳞"),
    "keen_scale": ((140, 190, 215), "锋利石鳞"),
    "quality_scale": ((195, 185, 145), "优质石鳞"),
    "glintstone_dust": ((130, 160, 245), "辉石粉尘"),
    "fire_ember": ((240, 130, 60), "火焰余烬"),
    "sacred_crystal": ((245, 230, 165), "圣律结晶"),
    "blood_resin": ((185, 55, 65), "凝血树脂"),
    "occult_crystal": ((145, 105, 185), "晦暗结晶"),
}
ITEMS.update(MATERIALS)
ITEMS["remorse_tear"] = ((180, 235, 245), "悔恨之泪")
for i in range(1, 5):
    ITEMS[f"bell_bearing_{i}"] = ((225, 180, 70), f"铃珠[{i}]")


def draw_icon(color: tuple[int, int, int]) -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    dark = tuple(max(0, c - 60) for c in color)
    light = tuple(min(255, c + 45) for c in color)
    draw.rectangle([3, 3, 12, 12], fill=color, outline=dark)
    draw.line([(4, 4), (11, 4)], fill=light)
    draw.line([(4, 5), (4, 11)], fill=light)
    draw.point((11, 11), fill=dark)
    return img


def main() -> None:
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    for item_id, (color, _) in ITEMS.items():
        draw_icon(color).save(os.path.join(TEXTURES, f"{item_id}.png"))
        with open(os.path.join(MODELS, f"{item_id}.json"), "w", encoding="utf-8") as handle:
            json.dump({"parent": "item/generated",
                       "textures": {"layer0": f"eldencombat:item/{item_id}"}},
                      handle, ensure_ascii=False, indent=2)
            handle.write("\n")

    for lang, formatter in (("zh_cn", lambda name, _id: name), ("en_us", None)):
        path = os.path.join(LANG, f"{lang}.json")
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
        for item_id, (_, name) in ITEMS.items():
            key = f"item.eldencombat.{item_id}"
            if lang == "zh_cn":
                data[key] = name
            else:
                data.setdefault(key, item_id.replace("_", " ").title())
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(data, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
    print(f"生成 {len(ITEMS)} 件锻造物品的占位贴图 / 模型 / 语言条目")


if __name__ == "__main__":
    main()
