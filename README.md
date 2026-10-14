# Elden Combat

当前开发状态见 [2026-10-14 进度快照](docs/PROGRESS-SNAPSHOT-20261014.md)。最新方向为原版像素风装备与2D战技特效，当前主攻美术；下文保留早期阶段记录，验收结论以最新交接为准。

0.2.0 开发候选：Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21。纯原版战斗扩展，原版玩家关节与普攻，八个追忆战技、自制装备、强化/质变首版已接入。PCL 新实例安装仍未验收。

战斗部门负责服务端规则、输入、网络、构建打包与 smoke；美术部门负责模型、贴图、逐件比例、姿态和显示。当前范围允许战技跃起；Boss 新开发、地图与处决另排。

- [协调计划](docs/PROJECT-COORDINATION-PLAN-20261012.md)
- [战斗事件表](docs/COMBAT-EVENTS-20261012.md)
- [战斗回执与未验项](docs/COMBAT-DEPARTMENT-REPORT-20261012.md)
- [整合包重建](docs/MODPACK.md)
- [武器预览](art/weapons/preview.html)
- [追忆游戏版](art/remembrance-game/preview.html)

构建：`./gradlew.bat test build --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"`。保存测试证据后串行运行构建与 smoke，不运行 clean。默认 C 战技、R 战斗模式、G 蓄力、左 Alt 翻滚、X 弹反、V 喝药、鼠标中键锁定，均可改绑。

整合包锁定七个辅助模组版本与哈希，不要求 Epic Fight。完整安装、人工游玩与兼容性结论只依据实际验收记录。
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


2026-10-13 选定美术基线：新直剑 + 旧大剑/旧特大剑；矛/中盾/长弓当前精修版本。打包自测目录为 build/packaging-smoke，仍保留用户尺寸/质量、动态弓弦、盾背及PCL安装未验状态。按用户最新指令暂停新增可被现成方案替代的系统，等待统筹复用工单。
