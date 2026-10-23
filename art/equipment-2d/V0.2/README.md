# V0.2 装备美术选版

按2026-10-22用户选版整理，包含28件可编辑Blockbench工程及其贴图。所有工程按源文件原样复制，未重新绘制或转换。

- 抓钩（钩爪）与拳套：dsh的穿戴模型，含左右手几何、共用图集、16/32图标和挂点说明。
- 特大剑与重刺剑：本轮对照页“原版”，来自 blockbench-all-items-20261018/items；“原版”不是Minecraft自带物品，也不是更早的before历史快照。
- 其余24件：mc-polish-20261022最新候选，包括碎星收窄版、大矛重做版和大曲剑收窄版。

## 文件

- items/：28份工程；钩爪和拳套保留claw_worn、fist_worn文件名，运行物品ID仍分别为claw、fist。
- textures/：26件薄片物品的88张状态贴图，以及1张dsh共用图集。
- icons/：钩爪、拳套各16/32两张图标。
- preview/：28件的正面和斜侧面参考截图，共56张；dsh两件来自其原生验收截图。
- contracts/：dsh源挂点说明和源清单，作为原样参考。
- manifest.json：逐件选版、来源、相对路径、比例、握点和SHA256。
- validation.json：复制一致性、嵌入贴图和外置PNG一致性、源文件未变检查。

## 选版清单

| 名称 | 物品ID | 采用版本 | 工程 |
|---|---|---|---|
| 弩炮 | ballista | 最新候选 | [ballista.bbmodel](items/ballista.bbmodel) |
| 亵渎圣剑 | blasphemous_blade | 最新候选 | [blasphemous_blade.bbmodel](items/blasphemous_blade.bbmodel) |
| 钩爪 | claw | dsh 穿戴版 | [claw_worn.bbmodel](items/claw_worn.bbmodel) |
| 特大剑 | colossal_sword | 对照页原版 | [colossal_sword.bbmodel](items/colossal_sword.bbmodel) |
| 特大武器 | colossal_weapon | 最新候选 | [colossal_weapon.bbmodel](items/colossal_weapon.bbmodel) |
| 大曲剑 | curved_greatsword | 最新候选 | [curved_greatsword.bbmodel](items/curved_greatsword.bbmodel) |
| 曲剑 | curved_sword | 最新候选 | [curved_sword.bbmodel](items/curved_sword.bbmodel) |
| 拳套 | fist | dsh 穿戴版 | [fist_worn.bbmodel](items/fist_worn.bbmodel) |
| 连枷 | flail | 最新候选 | [flail.bbmodel](items/flail.bbmodel) |
| 辉石杖 | glintstone_staff | 最新候选 | [glintstone_staff.bbmodel](items/glintstone_staff.bbmodel) |
| 大弓 | great_bow | 最新候选 | [great_bow.bbmodel](items/great_bow.bbmodel) |
| 大盾 | great_shield | 最新候选 | [great_shield.bbmodel](items/great_shield.bbmodel) |
| 大矛 | great_spear | 最新候选 | [great_spear.bbmodel](items/great_spear.bbmodel) |
| 大剑 | great_sword | 最新候选 | [great_sword.bbmodel](items/great_sword.bbmodel) |
| 玛莲妮亚的义手刀 | hand_of_malenia | 最新候选 | [hand_of_malenia.bbmodel](items/hand_of_malenia.bbmodel) |
| 重刺剑 | heavy_thrusting_sword | 对照页原版 | [heavy_thrusting_sword.bbmodel](items/heavy_thrusting_sword.bbmodel) |
| 玛利喀斯的黑剑 | malikeths_black_blade | 最新候选 | [malikeths_black_blade.bbmodel](items/malikeths_black_blade.bbmodel) |
| 玛莉卡的石槌 | marikas_hammer | 最新候选 | [marikas_hammer.bbmodel](items/marikas_hammer.bbmodel) |
| 蒙格温圣矛 | mohgwyns_sacred_spear | 最新候选 | [mohgwyns_sacred_spear.bbmodel](items/mohgwyns_sacred_spear.bbmodel) |
| 蒙葛特咒剑 | morgotts_cursed_sword | 最新候选 | [morgotts_cursed_sword.bbmodel](items/morgotts_cursed_sword.bbmodel) |
| 神躯化剑 | sacred_relic_sword | 最新候选 | [sacred_relic_sword.bbmodel](items/sacred_relic_sword.bbmodel) |
| 圣印记 | sacred_seal | 最新候选 | [sacred_seal.bbmodel](items/sacred_seal.bbmodel) |
| 中盾 | shield | 最新候选 | [shield.bbmodel](items/shield.bbmodel) |
| 小盾 | small_shield | 最新候选 | [small_shield.bbmodel](items/small_shield.bbmodel) |
| 碎星大剑 | starscourge_greatsword | 最新候选 | [starscourge_greatsword.bbmodel](items/starscourge_greatsword.bbmodel) |
| 药瓶 | test_flask | 最新候选 | [test_flask.bbmodel](items/test_flask.bbmodel) |
| 双头剑 | twinblade | 最新候选 | [twinblade.bbmodel](items/twinblade.bbmodel) |
| 软鞭 | whip | 最新候选 | [whip.bbmodel](items/whip.bbmodel) |

## 使用边界

此目录是选定美术资产合集，未修改正式游戏资源、战斗代码或整合包安装内容。钩爪和拳套为立体穿戴件，保留dsh原有厚度、左右臂枢轴与export=false的参考假人；使用时只导出实际装备组，详见contracts/WEAR-CONTRACT.md。其余26件保留各自的长度、握点、状态贴图与原生撤销历史（源文件具有时）。

选版不等于运行接线或实机验收。工程内已有嵌入PNG，外置PNG同时附带，源目录和用户原稿均保留。
