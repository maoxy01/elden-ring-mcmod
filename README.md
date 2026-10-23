# Elden Combat 0.2.0

**2026-10-23 更新**：[V0.2 选版与配对握姿](art/equipment-2d/V0.2/holds/README.md) · [新版 HUD 接入](docs/COMBAT-HUD-V02-INTEGRATION-20261023.md) · [本机整包安装回执](docs/PLAYABLE-PACK-20261023.md) · [清理及同步记录](docs/REPOSITORY-CLEANUP-20261023.md)。本机交付包与原作动作研究不包含在公开 Git 上传中；本次同步不等于游戏实机验收。

**2026-10-21 工作快照**：[项目文档入口](docs/README.md) · [全部文档分类](docs/FILE-CATALOG.md) · [美术工程](art/README.md) · [开发工具](tools/README.md) · [本次整理与上传范围](docs/REPOSITORY-ORGANIZATION-20261021.md)。GitHub 当前地址为 [maoxy01/elden-ring-mcmod](https://github.com/maoxy01/elden-ring-mcmod)。

| 目录 | 内容 |
| --- | --- |
| `src/` | 模组 Java、资源与测试 |
| `art/` | 可编辑模型、贴图、候选及视觉证据 |
| `tools/` | 制作、导出、核查及 Blockbench 插件源码 |
| `gradle/` | 构建辅助与 Wrapper |
| `modpack/`、`modpack-defaults/` | 精确依赖锁、配置、打包脚本与默认设置 |
| `docs/` | 当前入口、交接、审查及日期历史记录 |

当前源码包含赐福/护符、手甲与握姿、战斗 HUD 及制作工具的后续改动。亵渎火焰 rev5 仍为美术候选。用户握姿工作稿保留，完整测试曾有一项哈希门禁失败，后续 HUD 定向检查通过；见[当前 HUD 交接与验收边界](docs/COMBAT-HUD-DESIGN-20261021.md)。本次上传不代表全包已验收，也没有重发安装包。

当前版本为 **0.2.0 开发候选版**，Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21。最近工作见 [2026-10-18 进度快照](docs/GITHUB-PROGRESS-SNAPSHOT-20261018.md)与[整合候选交付说明](docs/MODPACK-CANDIDATE-DELIVERY-20261018.md)。下文0.1.0仅为历史记录。

[下载2026-10-18开发候选与校验清单](https://github.com/maoxy01/elden-ring-mcmod/releases/tag/v0.2.0-dev-20261018)。这是当时的 JAR、PCL ZIP 与 mrpack，未包含本次工作快照的所有后续改动；6项CurseForge依赖按包内说明补装。[当时同步范围](docs/GITHUB-FULL-SYNC-20261018.md)。

0.2.0 开发候选：Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21。纯原版战斗扩展，原版玩家关节与普攻，八个追忆战技、自制装备、强化/质变首版已接入。PCL 新实例安装仍未验收。


- [协调计划](docs/PROJECT-COORDINATION-PLAN-20261012.md)
- [战斗事件表](docs/COMBAT-EVENTS-20261012.md)
- [战斗回执与未验项](docs/COMBAT-DEPARTMENT-REPORT-20261012.md)
- [整合包重建](docs/MODPACK.md)
- [武器预览](art/weapons/preview.html)
- [追忆游戏版](art/remembrance-game/preview.html)

构建：`./gradlew.bat test build --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"`。保存测试证据后串行运行构建与 smoke，不运行 clean。默认 C 战技、R 战斗模式、G 蓄力、左 Alt 翻滚、X 弹反、V 喝药、鼠标中键锁定，均可改绑。

正式依赖锁仍保留七项基线；扩充候选使用独立锁，最近交付记录客户端含36个第三方模组与本项目。候选尚未提升为正式发行，不要求 Epic Fight。安装、人工游玩与兼容性结论以对应版本的实际验收记录为准。
## 历史：0.1.0 独立引擎交付

- 引擎：NeoForge 21.1.209 / ModDevGradle 2.0.148 / Gradle 8.12 / JDK 21（`D:\JDK21`，不改系统默认 Java）。
- 已有：方块骨架加载器与渲染器、玩家动作状态机（轻/重/蓄力重、翻滚无敌、盾防、弹反、喝药、失衡硬直）、
  服务端扫掠命中与阻挡判定、可改绑输入与锁定视角、HP/精力 HUD、玛尔基特两阶段 AI 与八个动作草稿、调试命令与自检。
- 产物：`build\libs\eldencombat-0.1.0.jar`；构建与安装见 [docs/BUILD.md](docs/BUILD.md)，
  实现与验收状态见 [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)。
- 原始自检：`gradlew test` 与游戏内 `/eldencombat selftest` 共用同一份 95 项检查；本次已扩充至 420 项。
  `runServer` + 自动数据包实测通过（刷 Boss、验伤、死亡清理）。
- 本次已接入：第二批玩家资产、自制装备物品、Boss 血条、战斗粒子与临时音效；详情见 [docs/CODEX-IMPLEMENTATION.md](docs/CODEX-IMPLEMENTATION.md)。
- 待办：真实游玩手感、多人延迟和第三方渲染模组兼容性验收、原创音频制作。
- 给 Codex 的交接报告：`docs/REPORT-FOR-CODEX.md`（同目录另有 docx / pdf 版本），里面写清了资产接口硬约束与下一批交付需求。


2026-10-13 选定美术基线：新直剑 + 旧大剑/旧特大剑；矛/中盾/长弓当前精修版本。打包自测目录为 build/packaging-smoke，仍保留用户尺寸/质量、动态弓弦、盾背及PCL安装未验状态。


现在做的还只是一坨，估计要等明年才能完成初版
