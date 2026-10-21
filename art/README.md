# 美术文件导航

游戏实际采用的资源、可编辑工程、审查候选分别存放。目录日期用于追溯批次；不要仅按最新修改时间覆盖运行资源。

| 用途 | 入口 | 使用说明 |
| --- | --- | --- |
| 装备可编辑总工程、独立工程与贴图 | [blockbench-all-items-20261018](equipment-2d/blockbench-all-items-20261018/) | `all-items-optimized.bbmodel`、`items/`、`textures/`；用户仍在修改握姿，读取同批清单及最新核查 |
| 运行装备资产与准入清单 | [full](equipment-2d/full/) | `sprites/`、`native-held-models/`、display 与 publishable 清单；由 Gradle 消费 |
| 最新握姿工程、导出与原生视图 | [blockbench-holds-20261021](equipment-2d/blockbench-holds-20261021/) | `projects/`、`exports/`、`qa/`；最终接触检查见 `grip-audit/final-verification.json` |
| 钩爪与拳套手甲 | [worn-hand-gear-20261021](equipment-2d/worn-hand-gear-20261021/) | 独立工程、图集和 `WEAR-CONTRACT.md` |
| 亵渎战技当前美术候选 | [hybrid-rev5](vfx-blockbench/takers-flames-20261021/hybrid-rev5/) | 粒子火焰、地裂与岩浆柱；尚未 Java 接入及人工验收 |
| 已有八技 2D 序列帧 | [vfx-2d](vfx-2d/) | 制作与当前运行接线分别查 manifest 及对应交接 |
| 早期玩家与武器工程 | [player](player/)、[weapons](weapons/)、[remembrance-game](remembrance-game/) | 保留历史工程、预览及其相对贴图路径；不要当作新的权威总工程 |
| Margit 模型与概念参考 | [Margit_Fell_Omen.bbmodel](weapons/Margit_Fell_Omen.bbmodel)、[concepts](margit/concepts/) | 概念图与可编辑模型是不同交付物 |
| 制作脚本 | [tooling](tooling/) | 工作批次脚本保留原位置，避免破坏路径引用 |

最新状态先读[握点核查最终记录](../docs/CODEX-GRIP-AUDIT-20261021.md)和[模型总览](../docs/ART-MODELS-LATEST-20261021.md)。总览中“四件尚未采纳”的早期状态须结合后续握点核查与当前 `full/native-held-models.json` 判断，不将历史段落当作实时状态。

历史选版、`before-current`、`input-current`、`revisions/` 和日期批次继续保留作溯源。新增的 `backup/`、`_backup_*/`、临时冻结基线及 Mojang 原资源参考只留本机；公开仓库不新增这些副本。Mojang 参考可用 `tools/equipment2d/import-official-holds.py` 从合法本机资源重新生成。

暂停中的 `legendary-spells/` 维持原状。工程或预览存在不代表已接入、已验收或获得第三方素材再分发许可。
