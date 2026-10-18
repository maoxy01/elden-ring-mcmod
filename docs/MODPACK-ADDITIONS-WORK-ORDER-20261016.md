# 新增模组与翻滚替换工单

2026-10-16。用户要求将下列模组加入当前整合包；并已明确选择：改用Combat Roll，后续适配并停用原翻滚。保持Minecraft 1.21.1 / NeoForge 21.1.219 / Java21，不为兼容某个旧模组擅自迁移工程。战斗和美术必须串行；启动战斗前已核对美术上一轮completed/idle。

## 原始请求与已存在项

请求去重后21项，Sophisticated Core只计一次：Sodium、Iris Shaders、Entity Culling、Cloth Config API、FerriteCore、Mod Menu、Item Borders、Armor Toughness Bar、Sophisticated Backpacks、Sophisticated Core、Overloaded Armor Bar、MemorySweep、Lazy DataFixerUpper Forge、Combat Roll、MmmMmmMmmMmm/Target Dummy、FPS Reducer、IMBlocker、Fast Leaf Decay、Enchantment Descriptions、Alex's Mobs、Alex's Delight。

当前modpack/mods.lock.json已锁定前五项，共7项，另有Lithium和JEI。保留现有可用版本与非请求删除的内容，不重复安装、不借本轮随意全量升级。

## 已有来源证据与特别处理

- [Combat Roll发布文件](https://www.curseforge.com/minecraft/mc-mods/combat-roll/files/all?page=1&pageSize=20&version=1.21)：存在 combat_roll-neoforge-2.0.6+1.21.1.jar；最终选版与依赖以精确文件/API和JAR元数据为准。默认R键与项目战斗模式键重叠。
- [Alex's Mobs非官方移植](https://www.curseforge.com/minecraft/mc-mods/alexs-mobs-1-21-1-port)：有1.21.1 NeoForge，要求Citadel非官方移植。不得冒称原作者官方更新，具体来源和依赖明确写入报告。
- [Alex's Delight](https://www.curseforge.com/minecraft/mc-mods/alexs-delight)：官方项目列有1.21.1 NeoForge的1.6文件，并要求Alex's Mobs与Farmer's Delight；补齐匹配版本前置，不误装名称相近的其他Delight移植。
- [MemorySweep精确候选](https://www.curseforge.com/minecraft/mc-mods/memorysweep/files/6686731)：1.21.1 NeoForge 2.0.3-fix。用户请求保留在清单，GC效果另做对照，不预先声称改善性能。
- [Armor Toughness Bar, Again](https://www.curseforge.com/minecraft/mc-mods/armor-toughness-bar-again)：1.21.1 NeoForge同功能延续候选，必须标明不是用户原列表旧项目本体；核实原项目版本后决定兼容路线。
- [Overloaded Armor Bar](https://www.curseforge.com/minecraft/mc-mods/overloaded-armor-bar)有1.21.1 NeoForge。它是显示层，不能据名字擅自增加护甲数值上限或自动修改平衡；与韧性条和项目HUD核对布局。
- [Sophisticated Backpacks](https://www.curseforge.com/minecraft/mc-mods/sophisticated-backpacks)与[Sophisticated Core](https://www.curseforge.com/minecraft/mc-mods/sophisticated-core)须按前者实际依赖范围配对，不只分别挑最新JAR。
- Mod Menu、LazyDFU Forge、IMBlocker等逐项查精确1.21.1 NeoForge文件；项目页列有多个加载器不证明某个具体文件匹配。没有匹配版则保留明确阻断记录和同功能方案，不混装Fabric/旧Forge，不自动引入兼容桥。

## 交付与实施顺序

1. 战斗部门先完成21项矩阵：已存在/精确兼容候选/需适配/无匹配，记录官方项目与文件URL、版本、环境、完整依赖图、哈希、分发方式和已验证范围。目录只存在JAR不算实际加载。
2. 维护完整候选依赖锁（包含新增前置）和给DSH的实施合同；Java及脚本实现仍按最新分工由DSH落实。可用现有工具进行元数据检查、隔离下载取证；不写无关战斗实现，不接续装备/VFX修复，不覆盖用户游玩实例。
3. 当前Build-Modpack.ps1写死7模组数量、旧mixin集合、旧scope摘要，Fetch-Mods.ps1写死7个pins。DSH须同步调整为真实锁驱动的检查，继续校验文件大小/哈希、下载来源、客户端/服务端分包和实际所需产物，不能简单删除安全检查来让打包变绿。
4. 分批隔离试装：界面/背包/假人/便利模组 → Alex及Farmer's Delight依赖链 → Combat Roll切换 → MemorySweep等优化对照。全部使用官方可获取文件，不修改原始JAR。旧世界保留，新增生物与树叶行为先在新测试世界确认。

## Combat Roll最终状态

- 唯一翻滚来源改为Combat Roll；旧翻滚按键监听、意图提交、服务端位移/无敌触发、姿态/HUD状态等入口应一致停用，不能只换按键而保留两套结算。
- 先核对Combat Roll实际版本行为和可配置API，再设计与现有战斗/FP/精力/施放状态的边界；不假定其默认带无敌帧，不擅自增加耗蓝或沿用旧无敌结算。
- 明确新默认按键，避免与R战斗模式及其他动作冲突；主副手、施法期间、死亡/重登、多人冷却与移动限制有实际验收记录。
- 候选在隔离实例完成原翻滚关闭与新翻滚可用的同一轮验收。依赖尚未就绪时保持当前正常实例可用，不先让玩家失去所有翻滚能力；正式交付必须落实用户选择。

## 验收与回执

能启动不等于整合完成。记录客户端/专用服务端实际加载列表、缺依赖与重复modid检查、背包保存/死亡/重登、物品边框和护甲条/HUD兼容、中文输入、假人伤害、Alex生物与食物配方、翻滚单一权威、光影与剔除不隐藏武器/特效，以及新实例导入。GC/FPS限制效果以场景对照说明，不以模组数量推断更流畅。

交付 docs/MODPACK-ADDITIONS-AUDIT-20261016.md、精确候选锁、docs/DSH-MODPACK-ADDITIONS-HANDOFF-20261016.md。逐项保留未完成/无法安装原因。正式配置、安装验证或打包未做时必须说明，不将工单或下载完成冒称已加入并通过。
