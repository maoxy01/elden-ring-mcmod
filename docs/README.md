# 项目文档入口

本项目为 Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21 的 0.2.0 开发候选。文档按入口分类，原文件路径保留，既有脚本与历史链接继续使用。

| 要做的事 | 从这里开始 |
| --- | --- |
| 了解本次整理与同步范围 | [2026-10-21 仓库整理记录](REPOSITORY-ORGANIZATION-20261021.md) |
| 查找全部交接、审查及历史文件 | [文件分类索引](FILE-CATALOG.md) |
| 修改或构建源码 | [构建说明](BUILD.md)、根目录 `build.gradle`、[整合包重建](MODPACK.md) |
| 找最新版模型与实际握姿状态 | [模型总览](ART-MODELS-LATEST-20261021.md)、[握点核查最终记录](CODEX-GRIP-AUDIT-20261021.md)、[美术目录](../art/README.md) |
| 看当前 HUD 改动及测试限制 | [战斗 HUD 布局](COMBAT-HUD-DESIGN-20261021.md) |
| 看赐福和四件护符实现 | [赐福与护符](CODEX-GRACE-TALISMAN-20261019.md) |
| 看亵渎火焰当前美术候选 | [粒子火焰 rev5](ART-TAKERS-FLAMES-PARTICLES-REV5-20261021.md) |
| 看八技 VFX 运行验收与缺口 | [最新收口审查](CODEX-VFX-CLOSURE-REVIEW-20261017.md)、[运行框架](DSH-VFX-RUNTIME-FRAMEWORK-20261016.md) |
| 查工作顺序与已有接口 | [执行索引](DSH-EXECUTION-INDEX-20261016.md)、[协调计划](PROJECT-COORDINATION-PLAN-20261012.md) |
| 查可复用模组与精确候选锁 | [复用短名单](MCMOD-REUSE-SHORTLIST-20261017.md)、[战灰选型](ASH-REUSE-SELECTION-20261017.md)、`../modpack/candidates/` |
| 查外部依赖与素材边界 | [第三方说明](THIRD-PARTY.md) |

`combat-evidence/` 存测试与运行回执；`qa-weapon-hold/` 存握姿核查；`research-evidence/` 存来源核对记录。`code-backups/`、`coordination-backups/`、`review-backups/` 按现有规则只留本机。

本次同步保存当前工作稿。HUD 交接明确记录完整测试曾有一项握姿哈希门禁失败，而后定向 HUD 检查成功；不能将二者合并为“当前全部测试通过”。静态预览、原生 Blockbench 视图、测试、实际安装、人工观感及完整整合包发布分别验收。
