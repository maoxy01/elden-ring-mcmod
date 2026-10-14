# 艾尔登法环整合包复用方案与新版分工

日期：2026-10-13｜统筹只维护计划、接口和验收；战斗、美术部门执行开发。

## 1. 最新用户决定与工程基线

用户要求先找网上现成方案，并明确回复：附件作为最新方向，调整美术与开发顺序。因此本计划覆盖 2026-10-12 工单中继续批量自制 3D 武器和暂缓地图的安排。

- 以原版生存、建造和自由探索为基础，主世界增加遗迹、技能奖励和赐福旅行网络。
- 保留原版人物动作，装备和 UI 转向原版风格 2D 像素资源；技能主要使用贴片、逐帧精灵和粒子。
- 先复用装备、饰品、地图、传送、奖励能力，必要时做适配；只有查明现成方案的缺口后才自研。
- 当前 MC 1.21.1 / NeoForge 21.1.219 / Java 21 / eldencombat 0.2.0 工程继续使用，不自动迁移加载器或恢复 Epic Fight。
- 已有八战技、FP、强化、质变、保存和验证成果继续复用。附件的“工程尚未启动”描述属于另一轮聊天记录，不能覆盖当前工作区证据。
- 旧大剑/特大剑、精修直剑、其余 3D 源、对照和握持证据保留为历史成果与可恢复备份；停止新 3D 批量精修。武器偏小、握空气仍是验收问题，2D/第三方装备同样需要实机检查。

附件原件：`C:/下载/%E8%89%BE%E5%B0%94%E7%99%BB%E6%B3%95%E7%8E%AFMinecraft%E6%A8%A1%E7%BB%84_%E9%98%B6%E6%AE%B5%E6%80%A7%E8%AE%BE%E8%AE%A1%E6%80%BB%E7%BB%93_2026-10-13 (2).docx`。

SHA256：`86fa1786fd0c6435429a0564bd5d59ba64d0c61f4ad9d040cc063d25907d624e`。提取正文和 11 个表格到 `docs/research-evidence/design-summary-20261013.txt`；原件未修改。文档内数值、快捷键和未决问题仍按草案处理，不因方向确认而当成已实现或最终平衡值。

## 2. 已查到的现成候选

以下为官方项目页、精确文件页或作者仓库的声明。它们证明存在候选，尚不证明在本工程中兼容；尤其不能把项目支持多个加载器误解为每个文件都可互换。

| 能力 | 候选与已核对版本 | 可减少的开发 | 仍需核对 |
| --- | --- | --- | --- |
| 旅行节点 | [Waystones 21.1.46 的 1.21.1 NeoForge 文件列表](https://www.curseforge.com/minecraft/mc-mods/waystones/files/all?page=1&pageSize=20&version=1.21.1)；[作者功能说明](https://www.curseforge.com/minecraft/mc-mods/waystones) | 已有可制作、激活和使用的节点、旅行道具、公共节点；依赖 Balm | 精确依赖范围、节点持久化/撤销接口、私人权限、战斗限制和安全落点；ARR 项目的分发方式单独核对 |
| 地图显示 | [JourneyMap 1.21.1 NeoForge 6.0.9 文件列表](https://www.curseforge.com/minecraft/mc-mods/journeymap/files/all?page=1&version=1.21.1) | 现成小地图/全屏地图，不重写地图 | 地图本体 ARR，与 [公开 API](https://github.com/TeamJM/journeymap-api) 分开核对；点击扩展能力以目标版本 API 为准 |
| 地图接线 | [JourneyMap Integration](https://www.curseforge.com/minecraft/mc-mods/journeymap-integration)，初筛1.9，部门精确审计1.9.1；MIT | 已有 Waystones marker；Waystones 本体也自带路径点同步 | 精确1.9.1菜单回调为空，点击创建路径点，不提供本项目的安全旅行。本轮不必装，使用公开地图API补入口 |
| 多人宝箱 | [Lootr 的 1.21.1 NeoForge 文件列表](https://www.curseforge.com/minecraft/mc-mods/lootr/files/all?page=1&version=1.21.1)；[当前许可](https://www.curseforge.com/minecraft/mc-mods/lootr/license)区分代码 MIT、资产 ARR | 宝箱按玩家分别生成/领取战利品 | 一人多开、重进保存、重置策略、结构容器转换；个人宝箱不等于“技能全局一次解锁”。旧文件页的 MIT 标签不能用于放行新版美术资产复制 |
| 普通装备 | [Spartan Weaponry Unofficial 1.21.1-1.2.3](https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry-unofficial/files/8717810)；[作者仓库](https://github.com/Mai-xiyu/SpartanWeaponry-NeoForge)声明 NeoForge 21.1.x / Java 21 / Apache-2.0 | 匕首、剑、矛、戟、锤、长弓、弩等现成类别及扩展 API | 非官方移植；逐件像素风格、普通攻击语义、强化/战灰适配和握持需实际验证；资源的独立署名/例外条款要逐项确认 |
| 普通/特殊装备备选 | [Simply Swords 1.70.2 NeoForge 1.21.1](https://www.curseforge.com/minecraft/mc-mods/simply-swords/files/8746001) | 多武器类型与特殊效果；可做依赖/公开 API 扩展 | [许可](https://www.curseforge.com/minecraft/mc-mods/simply-swords/license)只允许通过官方平台清单下载来分发整包；不能抽取模型、音效、贴图重新装进自研 JAR。1.70 大更新有存档/配置/附属兼容警告，先用新测试存档 |
| 法环装备对照 | [Soulscraft Tarnished Legacy](https://www.curseforge.com/minecraft/mc-mods/soulscraft-tarnished-legacy)，1.2.1 NeoForge 1.21.1；ARR | 已有黑剑、长牙等武器及盾/护甲；页面列 GeckoLib、Architectury API 依赖 | 页面主打 3D 装备，与当前 2D 方向存在差异；先作为覆盖/外观对照，不作为抽取素材库或默认最终选择 |
| 饰品槽 | [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)；已核对 9.2.0+1.21.1 NeoForge 正式文件；LGPLv3 | 现成装备槽 API，避免另写饰品背包框架 | 当前自研护符效果接入与槽位规则；选定兼容的 9.x 精确版本后记录依赖和许可 |
| 线索/探险记录 | [FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge)，已查到 2101.1.35 NeoForge 1.21.1 | 现成任务/记录界面、条件和奖励入口 | 个人与团队进度差异、FTB 前置依赖、奖励事件重复发放；ARR 分发确认。不让任务等级阻塞原版生存 |
| 整合与配方 | [KubeJS](https://www.curseforge.com/minecraft/mc-mods/kubejs)，2101.7.2-build.377 NeoForge 1.21.1；LGPLv3 | 用现成脚本/数据能力处理配方和整合 | [1.21 官方迁移说明](https://kubejs.com/wiki/other/major-updates/7.0)指出高级掉落迁至 LootJS、世界生成事件停用；不拿旧版脚本直接套用，能用普通数据包时不强加脚本依赖 |
| 遗迹结构 | [Dungeons and Taverns v4.4.4 数据包](https://modrinth.com/datapack/dungeons-and-taverns/version/v4.4.4)，标记 1.21–1.21.1；ARR | 现成探索建筑，可先选小型结构评估奖励适配 | 全包内容/附魔是否超出首轮；允许的清单/数据覆盖方式、所选结构与 Lootr 兼容、自然生成和旧区块策略；不拆出未授权 NBT 重新分发 |
| 法环法术扩展 | [Iron's Spells 'n Spellbooks: Elden Ring 1.0.3](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks-elden-ring/files/9103593)，1.21.1 NeoForge；ARR | 作者页面已有 27 法术，利用 Iron's 的施法流程 | 依赖 Iron's 及其前置；[作者说明](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks-elden-ring)明确法力、按键、冷却仍来自 Iron's。必须解决与现有 FP/输入/记忆槽的权威和语义重复后才能进入主线 |

普通装备先比较 Spartan 与 Simply Swords，选覆盖与风格更合适的一套；不因“更多内容”默认同时全装。地图/旅行也先验证已有桥接是否足够，再决定新增适配器。

## 3. 已查但暂不作为当前接入底座

- [Marium's Soulslike Weaponry](https://modrinth.com/mod/mariums-soulslike-weaponry)有传奇武器与 Boss；核对的 1.21.1 文件为 Fabric，不能当 NeoForge 文件使用。Modrinth/作者仓库显示 CC0，而 CurseForge 显示 ARR，素材许可需要绑定具体来源、版本、目录再确认。本轮不为使用它迁移加载器或自建全量移植。
- [Elden Ring Weapons Pack](https://www.curseforge.com/minecraft/mc-mods/elden-ring-weapons-pack)有 25+ 法环武器，但文件为 Forge 1.19.2，ARR；[Moonlit Arsenal](https://www.curseforge.com/minecraft/mc-mods/moonlit-arsenal)为 Forge 1.20.1/1.19.2、ARR。当前不能直接使用，保留参考链接。
- [Celestisynth 官方 1.21.1 移植](https://www.curseforge.com/minecraft/mc-mods/celestisynth-1-21-1)有 NeoForge/MIT 版本及独立武器动作、GeckoLib/PlayerAnimator 依赖。动作与玩法扩张和现行原版人物方向不合，本轮不选为战斗底座。
- 检索到的 [Elden Adventure](https://www.curseforge.com/minecraft/modpacks/elden-adventure)为 Forge 1.16.5，[EldenCraft](https://www.curseforge.com/minecraft/modpacks/eldencraft-dark-fantasy-challenge)为 Forge 1.20.1。可以参考内容组合，不能据主题名把整包覆盖当前工程；没有证明存在一套满足所有现行约束的即插即用整包。

## 4. 战斗部门工单

### R-C0 当前验证收束与复用审计

先收束已经运行的验证/候选打包，保留失败日志，不重新重复已完成的八技测试。提交 `docs/COMBAT-REUSE-AUDIT-20261013.md`，逐候选登记官方链接、精确文件/哈希、加载器、依赖范围、许可、公开 API、被替代能力和本工程缺口。

首要检查 Waystones+Balm+JourneyMap；已有桥接与自身地图支持二选一，检查点击旅行的真实服务端路径，不把显示路径点当作安全传送完成。其次评估 Lootr、普通武器适配、Curios；FTB/KubeJS/法术列后续候选。

该阶段只做资料/API审计及已在执行的验证收口。暂不修改当前包锁、旧存档或批量安装候选。拿到证据后由统筹直接派发可恢复的本地试装工单，无需重复向用户请求新建部门或研究许可。

### R-C1 两个赐福节点原型

审计通过后在独立新测试实例安装最小依赖组合。优先沿用现成节点注册、发现、命名、保存和传送能力，由 EldenCombat 的薄适配层补真实赐福标识、恢复 FP、必要的战斗/权限限制和技能管理入口。若公开 API 无法满足某项，提交准确类/事件/方法证据与最小缺口，再自研该部分。

地图请求传节点标识；服务端核对真实节点存在、玩家已发现或公共权限、目标维度和落点。禁止将任意客户端坐标或普通路径点作为赐福授权。不以 OP 的地图 /tp 代替普通玩家旅行。节点或玩家发现数据各保留单一权威，避免另写两套不一致的存储。

验收至少覆盖：放置两个节点→激活/命名→地图识别→普通玩家合法旅行→保存退出重进；未发现、拆除失效、权限不符、战斗中、无有效落点的拒绝；跨维度与区块处理；原版床/生存可继续。记录精确模组组合、配置、日志及截图。数量8、传送成本/冷却为可配置草案，不标称已最终定稿。

### R-C2 第一个遗迹与战灰奖励

先选许可和版本通过的现成小型遗迹或结构能力，接自有奖励数据；不要直接造一套世界生成框架。只有现成结构不能承接设计时，才由美术做小型 NBT 内容并使用原版结构/Jigsaw/Loot Table。

完成一条自然发现遗迹→领取至少一份可用战灰/材料→绑定兼容普通武器→实际施放的链。普通宝箱保留 MC 掠夺体验；关键奖励先验证 Lootr 的个人宝箱，再补“玩家/节点一次解锁”语义。多人分别领取、重进不重发、旧已生成区不强行改造。

现有战技/强化保持可用。第三方武器优先标签/配置/附属 API 适配，先验证一把，不把外部物品全部转换成自有复制品或直接全量重写 Item 类。

## 5. 美术部门工单

### R-A0 现成装备审计与旧成果归档

提交 `docs/ART-REUSE-AUDIT-20261013.md`。先从官方图和允许读取的资源定义核对 Spartan、Simply Swords 的实际像素风格、武器类别覆盖、柄部位置、手持尺寸；Soulscraft 的现成法环装备作为风格与覆盖对照。

逐项标注“直接用依赖”“允许适配”“仅作外观参考”“必须补画”。公开仓库/项目页的许可不自动覆盖第三方音效和贴图；未知权限不能当可复制素材。既有自制3D文件及源定义保留备份，停止剩余批次制作；最终运行时替换由战斗串行同步。

### R-A1 最小赐福与奖励视觉

复用方案确定后先交付赐福/地图节点图标、一个战灰、锻造石/线索图标及需要的原版灰色 UI 元素。普通图标优先16×16，重点道具可32×32；赐福方块使用简单 MC 方块/贴片表现，不重建华丽3D资产框架。先确认是否能直接复用现有图标或第三方原资源。

图标需明确未激活、已发现、公共/私人和失效状态，实际状态由战斗接口提供。交付透明 PNG、可编辑源、帧尺寸/顺序/锚点/名称/许可与哈希；在物品栏、地图和真实游戏屏幕检查可读性。

### R-A2 技能与装备剩余缺口

现有八技能效果先评估可复用部分，再补像素贴片和水鸟乱舞首个可用精灵素材。概念图不是精灵图集，不直接作为运行资源。技能时序沿用真实事件表；新版三级技能规则另评估，不能直接把所有战技替换为文档参数。

装备只补现成方案未覆盖的必要图标/法环特征，维持各类别轮廓、像素阶梯刃缘和实际握柄。更换到2D路径时逐场景重验第一/第三人称、左右手、标准/细臂、攻击/防御；旧3D哈希对应的掌柄证明不能转用。

先验证一把装备是接入顺序，不是缩小最终范围。当前已有33类普通装备和8件追忆，最终候选要给出完整41项运行映射：各项提供方、完整注册ID、像素资源路径/哈希、战斗与成长策略、来源/许可、实际验收状态。已有PNG与第三方完整依赖优先；缺口才补画，不按41项重新从零制作。过渡实例保留旧3D基线用于比较和恢复，最终默认运行表现须按用户新方向切换2D；旧大剑/特大剑可作为像素剪影参考。原有注册ID/装备成长数据的兼容要明确处理，不删除物品导致旧存档丢装备。

R4不得仅凭两赐福、一个外部武器或一张精灵通过宣告完整整合包完成。还须逐项核对八技功能与2D表现、41项装备的最终显示/握持、承诺的成长获取链和探索内容、多人/保存、依赖许可与新实例导入。首个遗迹只是进入后续内容阶段的第一条闭环。

## 6. 分工、排程与新版里程碑

统筹负责来源审查、方案选择和工单；战斗负责 API、服务端规则、数据、网络、构建和试装；美术负责像素资源、外观对照、来源/许可台账。两部门不要互改报告和资产源。运行资源同步、Gradle、启动与打包仍由战斗单写，避免并发覆盖。

| 里程碑 | 可验证交付 | 当前状态 |
| --- | --- | --- |
| R0 复用选择 | 两部门审计、精确依赖/许可、能力与缺口表，确定最小候选组合 | 核心静态选型完成，两部门报告已复核；低优先候选未核实项保留 |
| R1 赐福原型 | 两真实节点、地图入口、普通玩家服务端旅行、保存/拒绝场景与像素显示 | 接入工单已派发；尚未实机验收 |
| R2 首个遗迹闭环 | 新世界自然遗迹、个人关键奖励、战灰绑定/施放、多人和旧存档策略 | 未完成 |
| R3 扩展探索与技能 | 墓地/塔、线索/记忆槽/饰品/法术、2D技能表现，逐项定义内容与验收 | 设计队列 |
| R4 整合包收口 | 新实例导入、依赖/许可证记录、升级保存、功能视觉兼容证据与交付说明 | 未完成 |

附件中的 V0.1 代表第一条玩法闭环，不自动将当前0.2.0 JAR降版或把测试候选包称作新版已完成。Boss/交界地全图/处决不进入首轮；主世界一个遗迹和赐福地图已进入首轮。

禁止以目录存在、网页描述、单项测试或“已派发”替代实际完成证据。查到可复用方案要进入明确的比较/试装/接线任务；查不到时记录已查来源和具体差距，避免无限研究或重新从头开发。

## 7. 已执行派发

- 两部门已收到复用优先收束，暂停新的自研批次并保存成果。
- 用户确认附件为最新方向后，已向原战斗与美术对话派发 R-C0 / R-A0，并交接完整正文；无需新建重复部门。
- 美术回复已保存六件刃器新旧对照和长弓三帧数据。这是旧方向阶段成果，不据此继续3D扩展。
- 战斗正在收束当前打包哈希门禁与验证；其报告已有原版 BowItem 实际出箭/命中日志，动态弓弦、最新细臂/工具握持尚未通过。新版2D方案选择前保留这些真实状态。

## 8. 统筹复核与第二轮派发

- 已读 `COMBAT-REUSE-AUDIT-20261013.md` 与 `ART-REUSE-AUDIT-20261013.md`。战斗对七个官方 Modrinth 文件完成SHA512核对；研究JAR不代表已安装。其FTB和法术包二进制获取失败，不据此重做系统或堵塞当前赐福主线。
- 统筹核对实际发行API：Waystones真实UUID/激活/节点/tryTeleport入口、Balm可取消Pre事件、Waystones原生地图同步、JourneyMap API v2 marker/menu入口、JMI1.9.1空菜单回调。采用 Waystones21.1.46 + Balm21.0.66 + JourneyMap1.21.1-6.0.9，先不加JMI。
- 已派发 [赐福地图接入工单](GRACE-REUSE-PROTOTYPE-WORK-ORDER-20261013.md)：战斗收束审计后直接进行隔离启动与薄适配，美术开始必要赐福像素资源，不等全部未来候选研究完才动工。两节点协议占位与最终赐福外观分别记录。
- Spartan为普通装备优先试装候选。美术仅证明固定源提交中抽样模型为原版像素物品模板，实际PNG/发布JAR对应关系与游戏尺寸握持尚未检查；33类覆盖表的basename也不冒称注册ID。先补取证，不把审计建议升级为视觉验收。
- 外部武器接入前修namespace边界：统筹已查看 `item/WeaponStacks.definition` 与 `server/Weapons.profileOf` 按注册ID的path查自有参数；`server/WeaponAttributes`也存在相同入口。先按完整ResourceLocation/明确白名单处理，外部同名项不得误套成长/伤害，保留原标签回退和自有武器保存语义。
- `build/packaging-smoke` 已有旧方向0.2.0测试候选、清单与PACK_PASS日志；仍是七辅助模组+自制武器基线，不是新版赐福/2D方案完成包，未证明PCL新实例导入。
- 已复核 `grace-server-start-20261013.log`：最小三模组及内嵌commonnetworking在真实专用服务端加载，两个有效Waystones节点已创建/命名/保存，Gradle成功退出。证据只覆盖基础依赖和节点写入；玩家激活、实际重启恢复、地图点击、权限/战斗限制、FP休息和最终赐福视觉未验。
- G-A1已产出15项原创像素资源和浏览器预览。统筹已查看浅背景总览并核对validation中的runtimeIntegrated=false；仅为资源草案，尚未证明游戏地图显示与状态接线。
- 用户正式确认：非战斗时可从野外通过地图前往已发现赐福。原型/最终验收都须包含非OP野外出发；仅节点旁旅行不能满足该要求。已同步战斗与美术，不用强制/unchecked传送绕过服务端校验。
- 客户端日志/回执已出现同一组节点UID恢复与真实Dev玩家的引擎激活。地图首次信息页、切换小地图预设、全屏地图须区分；当前地图测试发生失败，安全旅行仍未通过，不将“已打开某个JourneyMap界面”当完成。
- 已查看Spartan精确发布包六样本原PNG与最新赐福小锁16/32对照：五个刀剑/矛/弓弩样本使用原版像素轮廓，cestus为立体模型UV，不能泛称全包都是薄片。第三人称掌柄和实际比例仍待验。已派美术建立41项完整复用/2D迁移清单，并对缺口做有界现成候选查找。
- PCL路径缺失已解除：读取真实桌面快捷方式 `C:/Users/30869/OneDrive/Desktop/Plain Craft Launcher 2 - 快捷方式 (2).lnk`，目标为 `E:/PCL/Plain Craft Launcher 2.exe`，文件存在、版本 `2.13.1.1`、大小5750272字节。工作目录 `E:/PCL`。只读发现，没有启动、安装或修改外部实例；最终新实例导入仍未验。
