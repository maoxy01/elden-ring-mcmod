# DSH新增模组整合与Combat Roll替换合同

2026-10-16。来源：[用户工单](MODPACK-ADDITIONS-WORK-ORDER-20261016.md)、[精确审计](MODPACK-ADDITIONS-AUDIT-20261016.md)、[候选锁](MODPACK-ADDITIONS-CANDIDATE-LOCK-20261016.json)。Codex只完成框架和取证；具体Java/脚本修改、安装、运行由用户安排DSH。此文不启动DSH/Harness，不接续装备或VFX开发。

> **2026-10-16 附注（实施方变更）**：按用户要求，本文 §2/§3/§4/§5 的**实施方由 DSH 改为 Codex**，文件级任务、判据与离线用例见 [MODPACK-ADDITIONS-CODEX-IMPLEMENTATION-HANDOFF-20261016.md](MODPACK-ADDITIONS-CODEX-IMPLEMENTATION-HANDOFF-20261016.md)。本文其余条款（批次验收内容、Combat Roll 行为依据、回执必须包含项）**仍是验收依据**，未被修改。

## 1. 执行前条件与保留项

遵守根AGENTS.md：统筹派工前只读查两部门真实状态，确认上一部门完成或明确停止且相关命令结束。设计、研究、实现、构建、验收都算工作，不因目录不同允许并行。发现美术更晚任务运行时本单排队，不发会启动另一部门的消息。超时不算结束。

固定Java21、MC1.21.1、NeoForge21.1.219。保留正式锁原7项及其版本；Sophisticated最新包和EnchDesc最新包已因加载器最低版本过高被排除，采用候选锁中的较早精确文件，不重新“取最新”。保留原存档、武器注册ID、战灰/FP/成长/护符/赐福服务端权威。不要覆盖用户游玩实例，也不删除此前装备/VFX候选成果。

候选锁30条包含未批准的Armor Toughness Bar, Again替代项。原Armor Toughness Bar、Mod Menu、LazyDFU Forge分别保留阻断记录。部署时默认不启用替代提案，不能把“未选替代”说成整个用户请求完成。其余精确候选可继续隔离整合，不被三个问题一起阻断。

## 2. 获取与打包脚本适配

先备份拟修改文件。修复现有脚本，不再造第二套下载器：

| 入口 | 当前问题 | DSH实施要求 |
|---|---|---|
| modpack/Fetch-Mods.ps1:7起 | 7个pins写死；参数为空；最终直接重写正式锁 | 显式输入已批准锁/输出隔离目录；按锁获取，不从latest重选；候选与正式锁分离 |
| 同脚本来源校验 | 仅允许Modrinth CDN | 按source platform实施独立来源准入，保留HTTPS、主机/重定向、文件名与路径检查；CF清单/手动获取有明确状态，禁止任意URL通配放开 |
| Build-Modpack.ps1:34–35 | mixin数组必须恰为PlayerModelPoseMixin | 核对当前源码声明与构建JAR中实际类/配置完整性；列出允许/必需集合，不用删除检查代替适配 |
| Build-Modpack.ps1:65及116 | 要求lock.mods.Count与index.files.Count都等于7 | 由批准条目和侧闭包决定集合；生成前后路径、ID、哈希集合精确一致，拒绝多项/漏项/重复modid |
| Build-Modpack.ps1:80 | scope摘要过时 | 按真实本轮装入项/排除项/未验项生成回执，不硬写整包完成或仍只有旧战斗候选 |

锁驱动不是跳过验证：下载前验证目标MC/加载器文件级证据、版本固定、必需/可选/不兼容依赖范围；下载后比较大小、锁哈希及平台哈希、JAR实际modid与neoforge元数据、嵌套依赖去重。缓存按哈希复用，不能同名文件直接认通过。解压/复制目标必须在隔离根，拒绝路径逃逸与重复覆盖。原锁7项保留，API对Iris的硬依赖精确指向现有Sodium Pb3OXVqC。

默认输出审计计划可只读；真正获取/打包需明确选用批准清单。不要直接让现有Fetch-Mods消费审计锁并重写正式锁。Armor替代项、运行未验、CF哈希来源和分发待准入不能在转换时消失。锁中的extraDependencyEdges补足Alex's Delight漏写TOML的两条玩法前置。

客户端与专服分别计算依赖闭包。Item Borders只在C侧，不能把Prism随库标签塞进S侧；Fast Leaf Decay为S功能，在单机包中服务集成服务器。客户端根的库是否需专服按实际闭包判断；没有需要它的服务端根时不要仅凭“库支持BOTH”就打入。打包回执列出两侧实际集合与排除理由。

分发采用精确来源引用；CF文件优先CF manifest projectID/fileID。当前mrpack/PCL路线若不能合法表达/获取，交官方手动获取清单或等待准入，不能塞ARR字节到overrides。取证目录、研究JAR、旧不兼容候选、临时源码与测试存档全部排除。包内自有代码/资源与第三方依赖分开核对。

## 3. Combat Roll精确行为依据

目标文件combat_roll-neoforge-2.0.6+1.21.1.jar / FT7t1n1a，modid `combat_roll`；前置Cloth Config复用原锁、playerAnimator2.0.4另锁。文件/API原始证据位于research-evidence/modpack-additions-20261016，实际2.0.6 javap为最终类签名依据；GitHub1.21.1分支源码通过已保存tree.sha固定，不拿新版主分支API猜测。

已从目标JAR构造器核实：invulnerable_ticks_upon_roll=0、roll_duration=8 tick、roll_cooldown=4秒、exhaust_on_roll=0.1、food_level_required=6、allow_jump_while_rolling=false、allow_rolling_while_airborn=false、allow_rolling_while_weapon_cooldown=false、allow_auto_jump_while_rolling=true、additional_roll_distance=0。首批采用这些默认行为；不增加FP消耗，不沿用旧22精力或4–11tick无敌窗口，不擅自开启新无敌帧。若后续要调整平衡另列明确参数变更。

按键对象为 `net.combat_roll.client.Keybindings.roll`，翻译键 `keybinds.combat_roll.roll`，默认R；本项目R已是战斗模式。整合默认方案：**R保留切战斗模式，Combat Roll改为左Alt，移除本项目旧左Alt翻滚注册/消费**。用户已有自定义键位尊重并展示冲突，不每次登录重置；新实例配置中落实一键一动作。不能让R一次既切模式又翻滚。不要误改普通攻击/战技C/翻滚以外输入。

`RollManager.isEnabled/isRollAvailable/isRolling`提供客户端管理状态；它们不构成服务端权限闸门。实际API只有 `ServerSideRollEvents.PLAYER_START_ROLLING`，回调 `PlayerStartRolling.onPlayerStartedRolling(ServerPlayer,Vec3)` 返回void。ServerNetwork.handleRollPublish在广播动画、安排无敌与饥饿副作用后通知事件，**该事件不能取消起滚**。不可虚构pre-roll cancellable event或仅订阅该事件来禁止施法中翻滚。

目标handleRollPublish源码/字节码中未见完整的服务端冷却与战斗状态准入；动画/位移有客户端预测与上报，不能宣称该模组天然提供本工程所需的所有服务端校验。实现方需核实NeoForge网络封装到该方法的实际调用线程、客户端起滚与运动包顺序，并提交窄适配。不得将packet.velocity直接当可信位移重放。

## 4. 单一翻滚的接线合同

DSH在隔离分支/目录实施整体切换，成功后再提出正式发布；不要先改正常实例关旧翻滚，再等新依赖。依赖未就绪时保持原正常实例；候选一旦选择Combat Roll，则缺依赖明确阻断，不默默再开旧翻滚。

| 旧入口 | 切换后要求 |
|---|---|
| EldenCombatClient.ROLL注册、ClientCombat.tick旧consumeClick→IntentType.ROLL | 不再注册/发送旧翻滚输入；Combat Roll拥有唯一输入 |
| ServerCombat.handleIntent的ROLL与收招尾段处理 | 网络入口先拒绝旧ROLL，不能先cancel技能再拒绝；旧客户端/伪造包无动作、无扣费、无中断 |
| ServerCombat.startRoll、tick中ROLL位移、rollDirX/Z | 新路线不能调用，不能额外player.move/velocity；Combat Roll自身移动只一次 |
| CombatState.isRollInvulnerable、HitResolver伤害免除 | 新路线不产生旧ROLL spec或旧无敌窗口；只保留Combat Roll配置的0默认无敌语义 |
| CombatStatePayload.rollYaw、旧姿态/素材/动画状态消费者 | 不再由新翻滚驱动旧姿态；枚举/包字段若留兼容保持wire ID，不贸然改序号；清理旧瞬态状态 |
| CombatRules/ActionSpec旧rollCancel和配置、自测/夹具 | 区分停用遗留逻辑与新系统；更新真实新路径验收，不删除失败断言掩盖变化 |
| HUD | Combat Roll自己的冷却/次数显示唯一；旧精力/FP仍服务其他动作，不添加第二个翻滚冷却或扣蓝显示 |

静态搜索需覆盖ROLL、startRoll、isRollInvulnerable、rollYaw及数据驱动动作选择，不只上表几行。本项目普通战技/FP/攻击仍由原服务端负责，翻滚不借旧ActionId.ROLL激活第二状态机。

### 准入与动作互斥

最小适配分为客户端预测检查与服务端副作用前检查，复用现有CombatRules/SkillDriver边界，不新增翻滚移动/伤害结算：

- 正常原版生存与战斗模式均可使用新翻滚，模式R不再是起滚必要条件；死亡、旁观、菜单/输入框、载具及原模组禁用场景按实际原版/模组能力拒绝。
- 技能WINDUP/ACTIVE/追击窗口/硬收招禁止翻滚；合法可取消收招尾段允许，服务器确认准入后才调用原SkillDriver.cancel一次；不得先cancel再因冷却/状态拒绝。普通攻击/喝药/格挡同样由既有允许取消区间判断，与Combat Roll物品使用限制共同生效。
- Combat Roll进行中拒绝新攻击/新战技，避免CustomCombat网络绕过其只拦原版操作的限制。允许期间应有的生命、受击、维度和断线清理；不修改技能伤害或FP段价。
- 适配候选挂在**实际起滚和handleRollPublish广播/副作用之前**，没有公开pre-event时只在已固定版本方法做窄hook。必须证明专服侧也有相同准入，不能仅把客户端RollManager.isEnabled设false。
- 冷却/次数/附魔距离继续由Combat Roll配置和真实属性驱动；若需服务端补充反重复/冷却校验，应镜像同一配置与属性规则，作为该输入准入校验，不另扣资源、另推位移、另画冷却。服务端拒绝时处理客户端预测的移动/动画恢复，不在拒绝后依然广播成功。若无法可靠接入，明示阻断，不用事后事件假装完成。

本轮设计没有预定未经证实的取消API。精确hook、线程、确认/拒绝回传和预测纠偏由DSH核对目标文件后实现并附证据。服务器不能信任客户端“已经通过检查”的布尔值。登录、死亡重生、换维度、离线均不复活旧ROLL/无敌，也不保留上一玩家/世界的冷却快照。

## 5. 串行试装与验收合同

每批结束关停实例并确认命令完成，再下一批；研究/运行期间美术不并行。记录批准锁哈希、原包基线、Java/加载器、实例路径、配置差异、实际命令与退出码、加载列表、问题与截图。普通玩家闭环、夹具与真人输入证据分别注明。

| 批次 | 成功路径 | 负向/恢复 |
|---|---|---|
| A 基础库/界面/背包/假人/便利 | 原7项保留；界面可读；背包完整组件保存、放置/携带、重登/真实重启/死亡规则；Dummy普通攻击与战技伤害，叶片自然衰减 | 满包/满背包无复制丢失；JEI配方/背包集成；客户端项目不上专服，无重复modid |
| B Alex链 | 新测试世界自然生成代表生物；Farmer's Delight烹饪与Alex's Delight真实掉落→食材→配方闭环；C/S加载 | 移除必要前置时报告明确；错Citadel版本拒绝；旧世界备份后升级/回退检查，不能用/give替代探索链 |
| C Combat Roll | 同轮证明旧入口关闭、新Alt可滚、R只切模式；第一/第三人称及观察者一次动作、HUD一次冷却；无新增FP/精力扣费 | 旧ROLL包拒绝无取消；技能硬阶段拒绝、合法尾段只cancel一次；死亡/重登/维度；双玩家并发/重复请求/延迟纠偏；默认0无敌下正常受伤 |
| D 性能对照 | 相同世界、JVM/Xmx、视距、路线、时长，MemorySweep开/关记录GC日志、帧时间p50/p95/p99及堆占用/停顿 | 不以一次内存下降称提速；停顿变差记录并保留用户请求的可选配置；FPS Reducer仅失焦/闲置节能，切回焦点恢复正常 |
| E 联调/新包 | 实际独立客户端+专服加载列表，普通玩家进入保存；新PCL实例导入成功，来源和哈希可复核 | 光影开/关、Entity Culling开/关不隐藏武器/战技/假人/新生物；HUD缩放不遮FP/精力/护甲/韧性/冷却；不能只验开发环境 |

IMBlocker需Windows真人中文输入覆盖：聊天、书/告示牌、物品搜索、赐福菜单、背包搜索和返回移动；既能正常输入中文，也不让输入框里的R/Alt触发战斗。FPS限帧影响测量时固定或关闭限帧；光影比较指定同一shader包/配置，Iris安装不等于已含shader包。

背包Core、Alex链与新生物物品涉及存档：复制旧世界做试验，卸载前记录后果，不能拿正式存档反复删模组。外部假人若受击API差异导致战技不计伤，定位伤害/目标适配，不能改八技参数凑数。

## 6. DSH回执必须包含

列出实际批准/安装/阻断项目、最终两侧锁及哈希、具体变更文件、Combat Roll精确hook和旧翻滚关闭证据、每批成功与拒绝日志、未跑项、恢复步骤。对无法安装的原项目保留名字与原因，不把Again等替代品计作原项目完成。正式发布前逐项消除运行缺口；设计交接和下载取证本身不构成整合完成。

装备/VFX及三系统已有待验项仍单列，不用新增模组启动成功销账。本轮无Java/生产脚本/正式锁/实例修改，无构建游戏结果。
