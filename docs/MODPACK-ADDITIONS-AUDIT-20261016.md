# 新增模组精确文件审计

2026-10-16，MC1.21.1 / NeoForge21.1.219 / Java21。战斗设计部门只做元数据、隔离下载和源码/API取证；未启动DSH、构建或游戏，未安装到游玩实例，未改正式mods.lock.json。开始时只读list_threads确认美术idle；具体命令均已收束后交接。

## 结论与产物

21项原请求：5项已有锁定、13项找到精确文件候选、3项原项目未找到兼容文件。另锁定Armor Toughness Bar, Again作为明确的替代提案，不自动替换原项目。包含原有Lithium/JEI及全部新增前置，共30个精确文件条目（含该替代提案）。这不是30项已安装，也不是整包运行通过。

- [独立候选锁](MODPACK-ADDITIONS-CANDIDATE-LOCK-20261016.json)：版本ID/文件ID、文件名、字节数、哈希、环境、来源、发布路径、依赖声明。
- [DSH实施合同](DSH-MODPACK-ADDITIONS-HANDOFF-20261016.md)：脚本、翻滚替换及分批验收。
- [取证目录](research-evidence/modpack-additions-20261016)：官方API原始JSON、隔离JAR、TOML摘录、Combat Roll源码与实际2.0.6字节码。目录不进入发布包。

候选锁kind为audit_candidate_not_installable_release。原7项逐项保留；新增23个条目均已读取JAR并计算SHA256/SHA512，Modrinth文件还核对作者平台公布SHA512。CF文件的哈希来自下载到的官方ForgeCDN字节，**未取得独立平台哈希**，锁内hashProvenance明确记录该差异；后续另行复核来源，不伪称双重验证。下载并未执行JAR。

## 21项逐项矩阵

表中C=客户端、S=专服；环境是候选分包方向，项目全局标签、TOML依赖side和实际加载结果分别核对。必需前置同侧随根带入；客户端库不因TOML里写side=BOTH就一律塞专服。

| 请求 | 精确选择 / 状态 | 环境及必需前置 | 文件证据 |
|---|---|---|---|
| Sodium | 保留0.6.13，Pb3OXVqC | C；S排除 | 原锁 |
| Iris Shaders | 保留1.8.12，t3ruzodq | C；与已锁Sodium组合 | 原锁 |
| Entity Culling | 保留1.11.3，eieYbWYb | C；S排除 | 原锁 |
| Cloth Config API | 保留15.0.140，izKINKFg | 按原锁C/S；Combat Roll复用，不重复 | 原锁 |
| FerriteCore | 保留7.0.3，x7kQWVju | C/S | 原锁 |
| Mod Menu | 原项目无1.21.1 NeoForge文件 | 阻断；NeoForge内置Mods页面为功能方案，不加Fabric版/桥 | [项目](https://modrinth.com/mod/modmenu)，API空结果已保存 |
| Item Borders | 1.2.5，WjErgKWV | C；Iceberg1.3.2、Prism1.0.11 | [文件](https://modrinth.com/mod/item-borders/version/WjErgKWV) |
| Armor Toughness Bar | 原项目无精确匹配；Again v9 / 9473L0CF仅作不同项目提案 | C；待项目替代选择，未静默准入 | [原项目](https://modrinth.com/mod/armor-toughness-bar)、[Again文件](https://modrinth.com/mod/armor-toughness-bar-again/version/9473L0CF) |
| Sophisticated Backpacks | 3.25.21.1475，K27VegQ6 | C/S；Core≥1.3.98，选择1.3.100.1348 | [文件](https://modrinth.com/mod/sophisticated-backpacks/version/K27VegQ6) |
| Sophisticated Core | 1.3.100.1348，8R3bTV1X | C/S；只保留一份 | [文件](https://modrinth.com/mod/sophisticated-core/version/8R3bTV1X) |
| Overloaded Armor Bar | overloadedarmorbar-neoforge-1.21-2.jar，CF5537850 | C；S排除；只改显示 | [精确文件](https://www.curseforge.com/minecraft/mc-mods/overloaded-armor-bar/files/5537850) |
| MemorySweep | 2.0.3-fix，CF6686731 | C/S；Architectury≥13.0.8，选择13.0.11 NeoForge | [精确文件](https://www.curseforge.com/minecraft/mc-mods/memorysweep/files/6686731) |
| LazyDFU Forge | 原项目仅到1.19.3，无兼容文件 | 阻断；先测现有21.1.219启动基线，再评估同功能优化，不自动换包 | [原项目文件](https://www.curseforge.com/minecraft/mc-mods/lazy-dfu-forge/files/all) |
| Combat Roll | 2.0.6+1.21.1-neoforge，FT7t1n1a | C/S；Cloth Config + playerAnimator2.0.4 | [精确文件](https://modrinth.com/mod/combat-roll/version/FT7t1n1a) |
| MmmMmmMmmMmm / Target Dummy | 1.21-2.1.2，DldwQTMc | C/S；Moonlight3.7.2 | [精确文件](https://modrinth.com/mod/mmmmmmmmmmmm/version/DldwQTMc) |
| FPS Reducer | 1.21-2.10，QcqljX3m | C；S排除 | [精确文件](https://modrinth.com/mod/fps-reducer/version/QcqljX3m) |
| IMBlocker | 原作者项目5.6.2.1，BKOiydts | C；中文输入待Windows真人验证 | [精确文件](https://modrinth.com/mod/imblocker-original/version/BKOiydts) |
| Fast Leaf Decay | fastleafdecay-35.jar，CF5849090 | S；单机集成服务器也需随客户端包提供 | [精确文件](https://www.curseforge.com/minecraft/mc-mods/fast-leaf-decay/files/5849090) |
| Enchantment Descriptions | 21.1.9，zfAkJn0r | C；Bookshelf21.1.81、Prickle21.1.11；S可选依项目元数据处理 | [精确文件](https://modrinth.com/mod/enchantment-descriptions/version/zfAkJn0r) |
| Alex's Mobs | Raguto非官方移植1.22.17，CF7528193 | C/S；对应Citadel移植2.7.6 | [移植文件](https://www.curseforge.com/minecraft/mc-mods/alexs-mobs-1-21-1-port/files/7528193) |
| Alex's Delight | 原Baisylia项目1.6，CF7612806 | C/S；Alex's Mobs及Farmer's Delight1.3.4 | [原项目文件](https://www.curseforge.com/minecraft/mc-mods/alexs-delight/files/7612806) |

原锁额外两项Lithium0.15.4/DDUrRVCA、JEI19.27.0.340/YAcQ6elZ保留。候选文件和全部哈希以JSON为准，不在文档中手抄另一套散列。

## 选版依据及兼容陷阱

- 背包最新3.26.12.2208与Core1.5.13.2404的实际TOML均要求NeoForge≥21.1.229；Core还声明可选JEI≥19.32.0.359，而本包已有19.27.0.340。拒绝这两个最新包，选已下载的背包3.25.21.1475 / Core1.3.100.1348：两者NeoForge≥21.1.0，背包Core≥1.3.98，范围相容。不是声称已找到“所有历史版本中最新可用组合”，而是有精确证据的保守候选。
- EnchDesc21.1.11要求NeoForge≥21.1.233；改选21.1.9要求≥21.1.61。Bookshelf21.1.81要求≥21.1.209，Prickle21.1.11要求≥21.1.61，满足固定219。Farmer's Delight1.3.4要求≥21.1.219，恰好满足，不升级加载器。
- playerAnimator文件名含forge，但该具体版本的API loaders含neoforge且JAR含neoforge.mods.toml，MC范围[1.21,1.21.2)，因此未凭文件名排除；releaseType按锁记录，不能因为文件名相似引入旧Forge文件。
- IMBlocker最初查到的 `imblocker` 是不同的IMBlockerFabric项目，不能使用。原作者 `imblocker-original` 的精确文件为IMBlocker-5.6.2.1-neoforge+1.20.6-1.21.8.jar，API明确含1.21.1和neoforge，JAR为modid imblocker。其TOML未声明完整MC约束，仍需Windows实际启动/IME验证，不以项目全标签替代文件证据。
- Fast Leaf Decay35文件页明确包含1.21.1；JAR MC[1.21.1,1.22)、NeoForge≥21.1.0；不能看到展示的最高版本1.21.5就误判不兼容。
- MemorySweep JAR声明architectury≥13.0.8，必须带入；不能只看文件页没有前置摘要。优化效果不予预先背书。
- Alex's Mobs移植与Citadel移植由Raguto发布，不能称原作者官方更新。[Citadel精确文件](https://www.curseforge.com/minecraft/mc-mods/citadel-1-21-1-port/files/7982052) 与其模组ID/依赖范围已核对；不要换成另一个同modid的Modrinth Citadel项目文件。Alex's Mobs TOML仍含旧mandatory写法，Citadel的neoforge范围[4,)异常宽，平台文件标记虽匹配，实际NeoForge加载器解析仍须启动确认，不能自行修改第三方JAR。
- Alex's Delight1.6确实来自原项目，文件说明ported by woahian；TOML仅声明MC/NeoForge，**漏列两条玩法前置**。项目关系要求Alex's Mobs和Farmer's Delight，候选锁extraDependencyEdges补记，不能依赖NeoForge缺依赖检测兜底。1.6要求NeoForge≥21.1.218，当前219满足。非官方Mobs移植与此Delight的配方/注册兼容仅能实测，不据modid推断完成。

## 依赖闭包与环境

新增硬依赖边：Combat Roll→Cloth Config（已有）+playerAnimator；Item Borders→Iceberg+Prism；背包→Core（去重）；Dummy→Moonlight；EnchDesc→Bookshelf+Prickle；MemorySweep→Architectury；Alex's Mobs→Citadel移植；Alex's Delight→Alex's Mobs+Farmer's Delight。新增9个前置均精确锁定；详情含optional依赖保存在metadataDependencies/jarDependencyDeclarations，不自动安装可选集成库。JAR内已嵌入依赖的元数据另存*.jarjar.json，不重复下载同一modid。

环境约束以根功能向依赖传播：专服排除Sodium/Iris/EntityCulling/ItemBorders/Prism/FPSReducer/韧性条/Overloaded/IMBlocker等纯客户端根；Iceberg在只有ItemBorders需要时无需专服随装。保留原锁中的Cloth/Ferrite/Lithium服务端侧；CombatRoll/playerAnimator、背包/Core、Dummy/Moonlight、Alex链及服务器便利功能另验专服。JEI和EnchDesc服务器可选，避免可选根反向强制客户端功能上专服。锁内env保留平台声明，DSH需生成实际侧闭包而不是直接复制全部30项。

## 分发路线与证据限度

Modrinth来源使用精确版本URL/已公布哈希的引用下载；许可证声明随项目元数据保存，API可下载不等于任意重新托管授权。CF条目保留project/file ID，优先用CF清单由支持的客户端获取；若目标mrpack/PCL不支持，给明确官方手动获取步骤或在具备分发准入后选择路线，不能把ARR文件直接混进overrides绕过。不得从非官方镜像补包或悄悄引入兼容桥。

现有Fetch-Mods只支持Modrinth CDN，不能原样消费混合来源候选锁。30条锁中包括一个未批准替代项，故不是可直接投产的安装清单。缺安装器路线、运行兼容或替代决定的条目保留阻断；下载、API标签、静态范围、编译、进机和最终验收分开记。

本轮已完成：21项矩阵、精确候选与必要前置、23个新增/替代JAR静态取证、脚本缺口和翻滚API合同。未完成：正式准入、安装、客户端/专服、多人、存档、中文输入、性能与最终包验收。没有执行任何游戏或构建命令。
