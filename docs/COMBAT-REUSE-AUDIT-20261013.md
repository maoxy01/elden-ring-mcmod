# 战斗部门复用审计 · 2026-10-13

## 结论与执行边界

R-C0 资料及二进制接口审计完成。建议 R-C1 先用 **Waystones 21.1.46 + Balm 21.0.66 + JourneyMap 6.0.9** 的 Minecraft 1.21.1 NeoForge 文件，在独立新实例验证两个节点。JMI 1.9.1 暂不作为必需依赖：Waystones 已有 JourneyMap 同步，JMI 的图标点击也没有实现赐福安全旅行。需要一个小地图请求适配器和统一的服务端规则钩子，复用节点数据库，不重建旅行底座。

这是静态审计结论，**没有安装候选、没有启动兼容实例、没有修改 modpack/mods.lock.json 或旧存档**。研究用 JAR 只在 docs/research-evidence/reuse-audit-20261013；七个 Modrinth 文件逐一校验官方 API 的 SHA512，所有观察 SHA256 见该目录 observed-jar-hashes.json。Spartan 为官方 CDN 文件、记录观察哈希；FTB 下载403、法术附加包SSL失败，未声称它们的二进制范围已核实。

最新设计总结确定 2D 原版像素表现、赐福→地图→遗迹奖励的顺序。总结中“工程未启动”是原讨论信息边界，不覆盖现有 0.2.0 工程、FP、八技和测试证据。8个赐福/3到8记忆槽/三级技能数值仍是草案。Epic Fight 不恢复。

## 精确候选与依赖范围

下列范围来自发行 JAR 的 META-INF/neoforge.mods.toml、Jar-in-Jar 元数据；官方页面仅用来确认版本/许可。范围允许 21.1.219 表示加载声明不排除它，不能替代启动与多人实测。

| 候选文件 | 发布标识与依赖 | NeoForge 21.1.219 | 复用判断 |
| --- | --- | --- | --- |
| waystones-neoforge-1.21.1-21.1.46.jar | Modrinth 6Z6MQ6os；MC `[1.21,)`、NF `[21-beta,)`、Balm `[21.0.39,)`，双方 | 声明允许 | 节点放置、激活、命名、发现、旅行与持久化首选；[官方文件列表](https://www.curseforge.com/minecraft/mc-mods/waystones/files/all?page=1&version=1.21.1)、[版本API](https://api.modrinth.com/v2/version/6Z6MQ6os) |
| balm-neoforge-1.21.1-21.0.66.jar | CquiaiDj；MC `[1.21.1,)`、NF `[21.0.82-beta,)` | 声明允许 | Waystones 必需；[版本API](https://api.modrinth.com/v2/version/CquiaiDj) |
| journeymap-neoforge-1.21.1-6.0.9.jar | CtFE7hwT；NF `[1.0.0,)`；commonnetworking `[1.0.16,)` | 声明允许 | 内嵌 common-networking 1.0.21-1.21.1、JourneyMap API 2.0.0-1.21.1，不另装重复API；[官方项目](https://www.curseforge.com/minecraft/mc-mods/journeymap)、[版本API](https://api.modrinth.com/v2/version/CtFE7hwT) |
| jmi-neoforge-1.21.1-1.9.1.jar | 53Z2qzeT；MC `[1.21.1,1.22)`、NF `[21,)`；JM客户端 `[1.21.1-6.0.0,)`；Waystones客户端可选 `[21.1,)`、FTB Chunks可选 `[2101.1,)` | 声明允许 | 非最小必需；与原生节点显示功能重叠；[官方项目](https://www.curseforge.com/minecraft/mc-mods/journeymap-integration)、[版本API](https://api.modrinth.com/v2/version/53Z2qzeT) |
| lootr-neoforge-1.21.1-1.11.38.127.jar | SPOrIzX8；MC `1.21.1`、NF `[21.1.195,)`，元数据未列其他必需模组 | 声明允许 | 首个遗迹个人宝箱候选；[官方列表](https://www.curseforge.com/minecraft/mc-mods/lootr/files/all?page=1&version=1.21.1)、[版本API](https://api.modrinth.com/v2/version/SPOrIzX8) |
| ftb-quests-neoforge-2101.1.36.jar | CF 8885017；FTB Library、FTB Teams必需，FTB Ranks可选；本文件JAR下载403 | **精确NF/前置版本范围未核实** | 笔记/线索UI后续；团队进度不等于个人奖励；[官方文件](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/files/8885017)、[依赖](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/relations/dependencies) |
| kubejs-neoforge-2101.7.2-build.377.jar | THIGFPwf；NF `[21.1.199,)`、Rhino `[2101.2.7-build.81,)`；JEI可选 `[19.25.0.322,)`、Better Advanced Tooltips仅元数据可选 | 声明允许 | 当前JEI 19.27.0.340范围允许；API把BAT标必需而JAR标可选，最终锁以启动验证为准；[官方文件](https://www.curseforge.com/minecraft/mc-mods/kubejs/files/8843626)、[版本API](https://api.modrinth.com/v2/version/THIGFPwf) |
| spartan_weaponry_unofficial-1.2.3-1.21.1.jar | CF 8717810；NF `[21,)`、MC `[1.21.1,1.21.4)`；Spartan Shields Unofficial可选 `[1.0.0,)` | 声明允许，旧mandatory字段须实测 | 普通武器优先对照候选；[官方文件](https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry-unofficial/files/8717810) |
| simplyswords-neoforge-1.70.2-1.21.1.jar | nS0Yahr5；NF `[4,)`、MC `[1.21,1.22)`、Architectury `[13.0.11,)`、Fzzy Config `[0.6.6,)`、Simply Tooltips客户端 `[0.1,)` | 声明允许，NF范围很宽须实测 | 普通武器备选，含自身特殊效果，不与Spartan默认全装；[官方文件](https://www.curseforge.com/minecraft/mc-mods/simply-swords/files/8746001)、[版本API](https://api.modrinth.com/v2/version/nS0Yahr5) |
| iss_elden_ring-1.0.3.jar，1.21.1 NeoForge | CF 9103593；必需 Iron's Spells及其前置；二进制SSL失败 | **精确NF/基底/传递依赖范围未核实** | 27法术候选，尚不能接主线；[官方文件](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks-elden-ring/files/9103593)、[作者说明](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks-elden-ring) |

未为传递依赖随意选“最新”版本。Rhino、Architectury、Fzzy、Simply Tooltips、FTB依赖、Iron's依赖均需要正式试装工单固定文件与哈希；七个现有辅助模组锁保持不变。

## R-C0：真实节点、地图入口与统一旅行校验

### 已核实的发行接口

Waystones 21.1.46 的公开 `net.blay09.mods.waystones.api.WaystonesAPI` 提供：`placeWaystone`、`activateWaystone`、`getWaystone(server, UUID)`、`getActivatedWaystones(player)`、`isWaystoneActivated`、`createDefaultTeleportContext`、`tryTeleport/tryTeleportAsync`、`resolveDefaultDestination`。Waystone 暴露 UID、name、dimension、pos、owner、visibility、origin、validity；区分世界生成/玩家放置可消费 origin，不需要再保存一套节点坐标。

服务器钩子是 `api.event.WaystoneTeleportEvent.Pre`，可读 `getContext/getRequirements`；Balm 21.0.66 中 BalmEvent 实现 NeoForge ICancellableEvent，`Balm.getEvents().onEvent` 可以注册回调。订阅时必须用事件中的真实服务器实体/节点；不能只拦新地图包而让原生UI、卷轴或Warp Stone绕过限制。异步传送的目标块/玩家状态在最终执行前仍应复核。调用普通 try 系列，不使用 force、unchecked、unbound 系列绕过原规则。[官方事件源码](https://github.com/TwelveIterations/Waystones/blob/1.21.1/common/src/main/java/net/blay09/mods/waystones/api/event/WaystoneTeleportEvent.java)

这是**可用接口证据**，事件执行顺序、取消提示、所有入口是否经过同一事件、原生落点搜索边界仍需 R-C1 实测。不能承诺一次订阅已经覆盖所有路径。

### 原生 JourneyMap 与 JMI 的实际重复

21.1.46 内有 `compat.JourneyMapIntegration`，字节码表明它消费 Waystones 的列表/更新/移除事件并增删 JourneyMap Waypoint。来源确实是节点列表，但结果仍是地图路径点；该桥接类没有实现 EldenCombat 的地图旅行请求。

JMI 1.9.1 的 `WaystonesMarker` 消费相同的列表生成 MarkerOverlay。精确发行字节码 `WaystonesMarkerListener.onMouseClick` 调用 `WaypointFactory.createClientWaypoint` 和 `IClientAPI.addWaypoint`；`onOverlayMenuPopup` 只有 return。**图标点击建立客户端路径点，不是普通玩家经服务端已激活节点旅行。**因此不将OP地图传送或任意客户端路径点作为M1验收。

JourneyMap内嵌的API 2.0.0-1.21.1已核实 `IOverlayListener.onMouseClick/onOverlayMenuPopup`、`ModPopupMenu.addMenuItem(String, Action)`。可以用自有marker绑定节点UUID并加“旅行”入口，不改写地图模组。必须避免同时启用两套相同节点图标；最小原型先不装JMI，若最后需要它的标记外观再关闭重复显示。[API仓库](https://github.com/TeamJM/journeymap-api)

上述二进制接口及调用链留在审计目录的 waystones-public-api.txt、waystones-teleport-api.txt、balm-events-api.txt、waystones-native-map-bytecode.txt、jmi-waystones-bytecode.txt、journeymap-map-api.txt，均来自精确文件，不凭项目描述猜实现。

### 建议 R-C1 的最小接口边界（未实现）

1. 节点身份/名称/所有者/发现/持久化归Waystones；EldenCombat只记录必要赐福标识/规则，不复制节点坐标与发现数据库。FP归现有PlayerFpState。
2. 客户端地图只发 `{nodeUUID, requestSequence}`，普通路径点坐标不构成授权。服务端重查真实节点、有效性、玩家激活状态或公共权限、目标维度、战斗/施放状态。
3. 在所有普通旅行入口共有的Pre钩子补规则，兼顾原生菜单/物品；通过合法default context和try API加载必要目标区块，不常驻加载全部节点，不替换成裸 `/tp`。
4. 赐福恢复FP、管理技能仅在合法到达/近距离交互的服务器事件做；不让客户端图标点击直接加FP。床/原版死亡规则保留。
5. 原型只验证两个节点：普通非OP玩家激活、命名、保存重登、地图请求、未发现/删除/私人节点拒绝、施放中拒绝、危险落点/跨维度取消、原生旅行入口同样拒绝；记录服务器日志和FP。数量上限及记忆槽暂不固化。

从任意位置出发还是仅在节点旁出发尚未定稿。R-C1首轮可以先限制在合法节点旁；不为了地图入口调用unchecked API跳过原生旅行费用、物品或出发点要求。

## 许可与复用方式

- Waystones/Balm 的JAR声明ARR，但作者[明确允许整合包使用](https://mods.twelveiterations.com/permissions)。不把ARR标签直接当禁止安装；保留完整官方依赖，若将来修改发行文件遵循作者披露要求。少数卷轴图标另有CC-BY-SA说明，当前不抽取使用。
- JourneyMap主体ARR；[API许可](https://raw.githubusercontent.com/TeamJM/journeymap-api/1.21.1_2.0.0/docs/license.md)允许自己代码依赖API和分发未修改的所需API编译类，但不允许分发未批准修改的API。走公开兼容层，避免重打包/改写主体。JMI仓库MIT不改变JourneyMap许可。[JMI官方源](https://github.com/frank89722/JourneyMapIntegration)
- Lootr不是“全部MIT”：[官方许可](https://www.curseforge.com/minecraft/mc-mods/lootr/license)区分代码MIT、资产ARR。作为完整官方依赖候选，不复制其贴图当自有资源。它提供每玩家容器内容，领取刷新配置与一次性技能解锁账本仍须分别验证。
- Spartan Unofficial为[Apache-2.0](https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry-unofficial)，保留署名与许可；仍优先完整依赖，不拆资产。Simply Swords是[Timefall Development License 1.2](https://www.curseforge.com/minecraft/mc-mods/simply-swords/license)，许可允许从官方平台下载的整合包依赖，但限制纹理/音效挪用与未授权移植；不是Apache/MIT素材库。
- FTB Quests[声明ARR](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/license)，完整包分发方式/官方平台下载规则待进一步确认，不能复制UI源码。KubeJS LGPLv3，Rhino为MPL-2.0；优先脚本与合法数据包配置，不搬运引擎。
- 法术附加包当前统筹资料标ARR；具体发行许可页面读取失败，二进制未取得，不复制内容，不声称许可已完整核实。作者明确施法按键、mana、冷却、spellbook由Iron's基底负责，27法术只增加效果。[作者说明](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks-elden-ring)
- 新遗迹候选[Dungeons and Taverns v4.4.4](https://modrinth.com/datapack/dungeons-and-taverns/version/v4.4.4)是1.21–1.21.1数据包、ARR。先作为完整依赖评估，数据覆盖不等于获得抽取NBT/纹理许可；允许的覆盖/分发范围需依据作者条款确认，不抽取结构。

## 与当前工程的重复和最小缺口

| 领域 | 可复用与保留 | 必须先解决的缺口 |
| --- | --- | --- |
| 节点/地图 | 现代码没有赐福注册/持久化/地图适配；复用Waystones/JM，不从头写数据库 | 上述普通玩家地图请求、统一规则、合法FP休息事件 |
| 个人遗迹奖励 | Lootr个人容器；Minecraft结构/loot table；保留现强化材料与配方 | 刷新/多人/重要技能领取去重；首个战灰解锁绑定是新内容，不能把现追忆固定战技当已实现 |
| 普通武器 | Spartan与Simply二选一对照；原版普攻与现有八追忆不重建 | `WeaponStacks.definition`和`Weapons`当前按item path查表，缺完整namespace隔离；外部同名dagger等有误套本模组参数风险。正式适配前修边界并用完整ResourceLocation/tag映射，避免重复制武器及重复基础伤害/reach修饰 |
| 笔记/配方 | FTB任务界面、Kube脚本；现24配方/强化/质变继续用 | 个人与队伍领取语义、奖励重放、依赖固定；[Kube 1.21迁移说明](https://kubejs.com/wiki/other/major-updates/7.0)已移高级loot事件、停旧worldgen事件，不能照搬旧脚本 |
| 法术与FP | 保留PlayerFpState、SkillTransactions、SkillCasting、SkillDriver唯一服务端权威 | Iron's自身mana/恢复/法力装备/持续法术重复收费、按键与spellbook槽位会冲突。必须先取得精确基底API与依赖，验证拒绝/取消/重复包/持续tick的原子扣FP及退款；仅隐藏mana HUD或每tick复制数值不能证明单一权威。暂不纳主线 |

本轮没有补namespace、注册地图包、改变FP或安装依赖：这些属于统筹将派的可恢复原型工单。现有130项检查、八技伤害、死亡FP、保存重载和本地候选归档证据继续有效，但不是新增候选兼容证据。资料/API审计已形成可执行边界，余下明确未核实项作为试装门禁，不继续无限检索。

辅助候选API包名清单在审计目录各 `.api-classes.txt`，仅证明发行文件暴露相应类，不承诺稳定二进制兼容或全部事件语义。Lootr可先走官方容器/loot table数据而不编译耦合内部类；KubeJS优先文档化ServerEvents配方接口；普通武器先按完整注册ID和原版物品能力适配。FTB及Iron's未成功取得本次精确JAR，相关事件API和版本范围明确保留未核实，不能凭仓库其他分支保证发行版兼容。
