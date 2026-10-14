"""生成 §2 锻造配方（原版锻造台，自定义序列化器 eldencombat:weapon_forging）。

一个档位一条配方：产出由底座武器当前的成长状态现算，所以不需要 41 件武器 × 每档一条。

运行：<bundled python> tools/gen_forging_recipes.py
"""
from __future__ import annotations

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "eldencombat")
RECIPE_DIR = os.path.join(DATA, "recipe")
TAG_DIR = os.path.join(DATA, "tags", "item")

NORMAL_WEAPONS = [
    "dagger", "straight_sword", "great_sword", "colossal_sword", "thrusting_sword",
    "heavy_thrusting_sword", "curved_sword", "curved_greatsword", "katana", "twinblade",
    "axe", "greataxe", "hammer", "great_hammer", "colossal_weapon",
    "flail", "spear", "great_spear", "halberd", "scythe", "whip",
    "fist", "claw",
    "short_bow", "long_bow", "great_bow", "crossbow", "ballista",
    "glintstone_staff", "sacred_seal",
    "small_shield", "shield", "great_shield",
]
REMEMBRANCE_WEAPONS = [
    "sacred_relic_sword", "marikas_hammer", "malikeths_black_blade", "blasphemous_blade",
    "hand_of_malenia", "starscourge_greatsword", "mohgwyns_sacred_spear", "morgotts_cursed_sword",
]
AFFINITY_MATERIALS = {
    "HEAVY": "heavy_scale",
    "KEEN": "keen_scale",
    "QUALITY": "quality_scale",
    "MAGIC": "glintstone_dust",
    "FIRE": "fire_ember",
    "SACRED": "sacred_crystal",
    "BLOOD": "blood_resin",
    "OCCULT": "occult_crystal",
}


def recipe(template: str, addition: str, **extra) -> dict:
    body = {
        "type": "eldencombat:weapon_forging",
        "template": {"item": f"eldencombat:{template}"},
        "base": {"tag": "eldencombat:weapons"},
        "addition": {"item": f"eldencombat:{addition}"},
    }
    body.update(extra)
    return body


def main() -> None:
    os.makedirs(RECIPE_DIR, exist_ok=True)
    os.makedirs(TAG_DIR, exist_ok=True)

    tags = {
        "weapons": NORMAL_WEAPONS + REMEMBRANCE_WEAPONS,
        "normal_weapons": NORMAL_WEAPONS,
        "remembrance_weapons": REMEMBRANCE_WEAPONS,
    }
    for name, values in tags.items():
        path = os.path.join(TAG_DIR, f"{name}.json")
        with open(path, "w", encoding="utf-8") as handle:
            json.dump({"replace": False, "values": [f"eldencombat:{v}" for v in values]},
                      handle, ensure_ascii=False, indent=2)
            handle.write("\n")

    written = 0
    for tier in range(1, 9):
        body = recipe(f"upgrade_template_{tier}", f"smithing_stone_{tier}",
                      operation="UPGRADE", somber=False, tier=tier)
        _write(f"upgrade_normal_{tier}", body)
        written += 1
    # 古龙岩锻造石：+10 的另一种（也是唯一）来源
    _write("upgrade_normal_ancient", recipe("upgrade_template_8", "ancient_dragon_smithing_stone",
                                            operation="UPGRADE", somber=False, tier=8))
    written += 1

    for tier in range(1, 6):
        _write(f"upgrade_somber_{tier}", recipe(f"somber_upgrade_template_{tier}",
                                                f"somber_smithing_stone_{tier}",
                                                operation="UPGRADE", somber=True, tier=tier))
        written += 1
    _write("upgrade_somber_ancient", recipe("somber_upgrade_template_5", "ancient_dragon_somber_stone",
                                            operation="UPGRADE", somber=True, tier=5))
    written += 1

    for affinity, material in AFFINITY_MATERIALS.items():
        _write(f"affinity_{affinity.lower()}",
               recipe(f"affinity_template_{affinity.lower()}", material,
                      operation="AFFINITY", affinity=affinity))
        written += 1

    _write("reforge", recipe("reforge_template", "remorse_tear", operation="REFORGE"))
    written += 1

    print(f"生成 {written} 条锻造配方与 {len(tags)} 个物品标签")


def _write(name: str, body: dict) -> None:
    path = os.path.join(RECIPE_DIR, f"{name}.json")
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(body, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


if __name__ == "__main__":
    main()
