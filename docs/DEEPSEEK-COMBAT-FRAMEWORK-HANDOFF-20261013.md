# DeepSeek Harness 战斗框架交接

日期：2026-10-13。工程：D:/codexout/mcmod；Java21、MC1.21.1、NeoForge21.1.219。

## 本次边界

只在现有战斗工程上补实现。八战技、FP、强化、质变、保存及 namespace 修复已经存在，不重建第二套系统。今天不扩展地图/赐福/遗迹、美术、打包或平衡，不启动 DeepSeek 或新建对话。后续具体工作由用户安排。

本次新增战斗骨架仅为 server/skills/package-info.java 的包级调用契约，以及本交接文件。现有 SkillDriver.Runtime 已提供完整扩展接口，因此没有增加空 Runtime、假成功处理器或重复状态机。前一轮 GraceSettings/GraceAccess/GraceRest 属于赐福原型，保留但不纳入本次战斗实现任务。

以下 Java 路径均相对 src/main/java/com/dsh/eldencombat。

## 已有实现与调用契约

| 环节 | 文件/入口 | 输入 → 输出及责任 |
| --- | --- | --- |
| 玩家输入 | client/ClientCombat、network/CombatIntentPayload.of | 序号、动作、目标提示、方向 → 意图包；没有可信伤害、余额或命中结果 |
| 网络入口 | registry/ModPayloads、server/ServerCombat.handleIntent | 包与连接玩家 → 服务端处理；校验与去重必须先于副作用 |
| 技能选择 | server/skills/SkillCasting.specForMainHand、request | 服务端主手与当前施放 → 技能参数与事务结果；requestById 仅为既有测试命令路径 |
| 资源事务 | server/skills/SkillTransactions.request | cast、FP、spec、tick、legal → REJECTED/STARTED/FOLLOWUP；扣费与阶段提交在此完成，提交失败退款 |
| 阶段驱动 | server/skills/SkillDriver.tick、accepted | 服务端 tick 与参数 → Runtime 生命周期；WINDUP/ACTIVE/FOLLOWUP_WINDOW/RECOVERY/IDLE |
| 战技扩展 | server/skills/SkillDriver.Runtime、RemembranceRuntime | onBegin/onTick/onActive/onRecovery/onFinish/onCancel/onSafetyTick/onDetach；沿用此接口 |
| 命中去重 | server/skills/SkillCastState.markHit、markResolvedChannel | 实体 ID、通道 → 本次阶段是否已处理；多段技能必须明确通道归属 |
| 状态同步 | server/skills/SkillServerEvents.sync | 权威 FP 与施放状态 → FpSyncPayload、SkillCastPayload；客户端负责展示 |
| 客户端消费 | network/ClientPayloadTrampoline、client/ClientSkills.handleFp、SkillPoseAnimator.handle | 服务端快照 → UI/动作；不反向结算伤害或 FP |
| 中断清理 | SkillDriver.cancel/detach、SkillServerEvents 生命周期事件 | 受击/换武器/死亡/离线/换维度 → 取消效果、清理瞬态、同步结束 |
| 持久化 | PlayerFpState、FpStateCodec、registry/WeaponStateCodec、item/WeaponStacks | FP及武器成长 → 现有存档；不能保存一半施放并在登录时重复结算 |

普通攻击、翻滚和精力仍由 ServerCombat/CombatState 管理，不应移进追忆技能 Runtime。服务器专用入口不得直接加载客户端渲染类。

## 按现有缺口安排的待填项

这些是后续实现任务，不表示已完成；先由用户选择，避免一次性扩大范围。

1. **参数缺失时统一清理**：SkillDriver.tick 当前 spec==null 分支直接 cast.finish；SkillDriver.cancel 也只有 spec/runtime 均存在才调用 onCancel。设计最小补丁，让参数重载丢失时仍释放已生成效果/位移追踪并同步结束。检查 RemembranceRuntime.onCancel/onDetach 的资源归属，不给缺失 spec 编造参数，不改变八技正常伤害。
2. **普通武器战灰绑定入口**：SkillCasting.specForMainHand 当前只匹配八件追忆武器；RemembranceWeaponRules.matches 与 SkillDriver.tick 也检查绑定。后续需要同时接入服务端绑定读取和持续合法性校验，输出可用 spec 或拒绝。绑定数据应复用/扩展已有武器组件与 codec，经用户确定绑定规则后再实现；本次未增加绑定、网络包、配方或假默认技能。
3. **异常事务边界**：SkillCasting.submit 在事务成功后调用 SkillDriver.accepted，再同步。明确 Runtime 初始化异常时怎样清理、报告和处理已经提交的费用；不得笼统退款造成重复收益。SkillTransactions 的普通拒绝/失败退款语义保持，新增异常行为需有对应失败场景。
4. **客户端结束与重连**：检查 ClientSkills.resetLocal、SkillPoseAnimator.handle 的旧 cast/实体残留清理；输入只提交意图，拒绝请求不启动确认特效。保留原版动作方向，2D表现的具体资源和制作不在本任务内。

## 必须保持的约束

- 服务端决定技能、阶段、命中、伤害、FP与冷却；客户端目标和方向只是待校验输入。
- 每个合法阶段只在 SkillTransactions 扣一次 FP，Runtime 不再扣；重复或过期请求不能重复开段。序号处理沿用 ServerCombat，不另建相互冲突的计数器。
- 状态机先确认阶段，再运行一次阶段副作用；命中按实体/通道去重。追击输入不能绕过窗口、武器或余额检查。
- 取消、脱离、死亡和维度变化清理所属效果/落地状态；正常完成与取消区分，不在取消后继续出伤害。
- 保留 Clone LOWEST 的 FP 恢复顺序；保留强化/质变组件及旧物品注册 ID，不通过删除旧装备解决兼容。
- 外部物品按完整 ResourceLocation 判断；同 path 不同 namespace 不能套用本模组定义，合法标签回退保留。
- 未实现入口必须拒绝或明确缺失；不得返回 STARTED、伪造命中或写 PASS 日志替代实现。

## 完成后集中验收

按改动选择必要场景，一次集中验证，不边写边启动游戏。

| 场景 | 判定 |
| --- | --- |
| 正常起手/合法追击 | 每段单次扣费，阶段和同步一致 |
| FP不足、错武器、窗口外、重复包 | 无新增效果、无重复伤害、无错误扣费 |
| 多目标/同目标持续接触 | 每段每通道命中符合参数，无重复结算 |
| 硬收招/可取消尾段 | 既有取消边界保持，切换动作仍走正常资源校验 |
| 切武器、受击、死亡、断线、换维度 | 无遗留实体/位移状态，客户端及时结束 |
| 重载参数缺失、Runtime启动失败 | 清理有记录，费用策略明确，无假成功 |
| 保存与重启 | FP/强化/质变保留，施放不复活、伤害不重放 |
| 外部同名物品与自有物品 | namespace隔离，自有成长与标签回退不回归 |
| 专用服务器及观察者 | 无客户端类泄漏；施放者与观察者状态正确 |

## 已有证据与未完成项

- 既有八技、FP/成长保存等历史证据见 docs/COMBAT-DEPARTMENT-REPORT-20261012.md、docs/COMBAT-EVENTS-20261012.md 和 docs/combat-evidence；不是本轮新验证结果。
- 低顶、台阶、液体、悬崖以及死亡时已发出波形的新增夹具曾写入但未运行，不标通过。旧八技无关通过场景不必每次全跑。
- 赐福原型曾有地图按键/首次信息页/服务注册错误及交互超时；真正全屏地图和两节点恢复有证据，旅行/休息/多人未完成验收。不要用该原型失败推断八战技失败，也不要继续其工单。
- 本轮只做源码结构阅读与交接，没有编译、启动或跑测试。进程查询时未发现 java 进程；没有在途游戏需要继续操作。

## 给 Harness 的执行提示

先阅读本文件和用户指定的待填项；备份拟修改文件，再做最小实现。不要改八技平衡参数，不动美术、赐福或打包，不另起代理/对话。实现完成后列出改动文件、真实未完成项及建议的集中验证命令；只有用户安排验证时再启动相应流程。以实际日志区分单测、引擎夹具和真实玩家操作，不将编译成功写成可玩验收通过。

## 追加：战灰绑定与 2D 接入框架（2026-10-13）

此节替代上文“普通战灰绑定入口未接入”的旧状态；具体普通战灰玩法和新素材仍未实现。

### 实际代码与接线

- `registry/ModDataComponents.ASH_BINDING`：新增 `eldencombat:ash_binding`，ResourceLocation CODEC + STREAM_CODEC，作为独立可保存/同步组件。`weapon_state` ID、字段、codec 未改；旧物品无组件时 `AshBindings.read` 返回 null。
- `server/skills/AshBindings`：统一 `read/resolve/bind/replace/unbind`；修改接口只接受服务器玩家，操作其主手，要求服务端线程。忙碌时拒绝；追忆/定义锁定/artLocked 拒绝；普通战灰 ID 必须含 namespace；无定义、无实际 Runtime、物品不在定义白名单或兼容谓词不符分别返回原因。无客户端任意 ID 请求包。
- `AshBindings.Definition/register`：后续在公共初始化中注册 ResourceLocation ID、spec、独立 Runtime、完整物品 ID 白名单、类别兼容谓词。spec.id 必须等于完整技能 ID；Runtime 不能是 RemembranceRuntime。未实现定义可以登记，但不会注册 Runtime，解析或绑定返回拒绝。当前没有注册任何可玩的普通战灰。
- `SkillCasting.specForMainHand/request` → 统一 resolve；`RemembranceWeaponRules.matches` → 同一解析结果及 Runtime 存在检查；`SkillDriver.spec` → 固定技能优先、普通注册表回退，accepted/tick/followup/cancel 都使用该入口。非法换物品/改绑后持续校验不再局限八件追忆。绑定修改入口直接拒绝施放中修改，避免中途替换绕过状态机。
- 原有八技仍固定在原物品 ID，忽略写在追忆栈上的战灰组件。普通技能复用现有 RemembranceSkillSpec 数据结构与阶段事务，这是兼容容器，不代表复用追忆 Runtime 或伤害逻辑。

后续待补：普通战灰定义/独立 Runtime/类别兼容名单；UI、配方和获取方式；真实 ItemStack 保存/网络往返、施放中换绑和拒绝路径集中验收。新增绑定接口未经实机调用，不把编译通过等同绑定玩法通过。

### 2D 清单和模型生成

清单：`src/main/resources/assets/eldencombat/routes/2d.json`。

- equipment：41 件注册装备 + test_flask，42 项全部 pending。没有独立贴图就继续旧 builtin/entity 回退；旧 UV 图集和薄网格不算 2D 图标。
- existing：58 个已注册物品的既有模型引用，沿用原有材料/护符/消耗品路径；这是既有模型清点，不是本轮美术质量验收。
- auxiliaryModels：2 个弓/弩辅助模型，和注册物品分开；weapon_placeholder 本身不列作物品。药瓶只列 equipment pending。
- externalProviderStatus：provider JAR/资源根准入与模型校验适配尚未实现。external_model 是保留的路线声明，当前若标 ready，生成器明确拒绝，不宣称可以直接接入已安装第三方模组。后续应从完整已准入模组验证模型链，保留作者 display/overrides；不得把 ARR 资源拷入本模组解决此缺口。

`gradle/equipment-2d.gradle` 注册 generateEquipment2d，检查重复、完整 41 ID+药瓶覆盖、ID合法性、ready 本地独立 PNG 与状态模型引用。仅对 ready sprite 生成 generated/handheld 模型；processResources 最后覆盖该物品旧模型。生成目录受 build 路径检查保护，避免上次 ready 产物在改回 pending 后残留。

Harness 切换一个本地 sprite：在 `src/main/resources/assets/<namespace>/textures/...png` 放已授权独立图标；填写 texture/parent/overrides；弓弩盾需 statesReady=true 且提供完整状态模型后才能 ready。先生成资源，再由游戏 F3+T 重载；只改清单不重新生成物品 JSON 不会自动变换模型。

模型选择由原版 ItemRenderer/BakedModel 负责：generated/handheld 不调用 BEWLR，因此第一/第三人称左右手、GUI、地面和展示框均走原版 display/附魔光效，不套旧 EquipmentRenderer 的握点与长度校正。旧 IClientItemExtensions 保留，只供 builtin/entity 回退。未改玩家皮肤与原版动作。

`EldenCombatClient.setupItemStates` 为现有 BowItem/CrossbowItem/ShieldItem 注册原版 pull/pulling/charged/firework/blocking 属性回调。具体拉弓/装填/格挡模型仍 pending；仅有属性入口不等于动画完成。

### UI、序列帧和粒子

`PresentationRoutes` 使用现有客户端重载事件，prepare 阶段校验并解析 UI/FX，apply 原子替换不可变 Data。JSON 类型错误、非法 ID、缺资源、错误帧数或 atlas 被拒绝并保留之前有效配置；绘制阶段不解析 JSON。

- UI：可选 FP 16×16 图标插入 CombatHud；未 ready 时继续现有文字/程序绘制条。HP、精力、药瓶文字和 Boss 条维持现有绘制，并未承诺新图标或新 HUD 设计。
- slash：固定 16 帧、索引 0..15，粒子 atlas；清单沿用原版 explosion_0..15。SkillSlashSpriteRenderer 接入 slashFrame；pending 或未入 atlas 的自定义帧回退原有帧。自定义帧 PNG 还需按原版粒子 atlas 资源机制接入，框架不自动拼图或新造渲染器。
- 既有 spark/dust/heal 继续 `particles/*.json` + `EldenCombatClient.particles` 注册和原版粒子图集重载；FxResources/FxPalette 保留颜色及启用开关入口。GoldenWave/其他 mesh 效果仍为既有路径，不标成全套 2D 完成。

### 验证与局限

- 首次集中 compileJava + generateEquipment2d 成功；收口审阅发现 FX 在绘制阶段解析 JSON 的问题，修复后做了一次集中编译及定向测试。未启动游戏、未执行 check/runClient/runServer/打包。
- 最终日志：`docs/combat-evidence/ash-2d-framework-final-20261013.log`，编译通过（2 个既有 API 弃用警告）；WeaponNamespaceTest 1、WeaponProgressionTest 17、RemembranceSkillsTest 17，共 35 项通过，无失败/跳过。这些验证已有边界/成长/技能事务，不包含新绑定的完整 MC 运行验收。
- 结构记录：`docs/combat-evidence/ash-2d-structure-20261013.txt`；旧 WeaponStateCodec 与备份哈希一致，42 项覆盖，0 ready / 42 pending。
- 新普通绑定真实保存/同步、固定锁定拒绝、换绑取消、2D 各视角/附魔/状态模型、坏资源重载的游戏内表现仍待集中验证。无新增普通技能，因此没有 STARTED/FOLLOWUP 可玩声明。
- 备份：`docs/code-backups/20261013-ash-2d-framework`，保留修改前 Java 树和 build.gradle。原始美术与旧资源不改、不删。

修改文件：ModDataComponents、AshBindings（新增）、SkillCasting、RemembranceWeaponRules、SkillDriver、EldenCombatClient、PresentationRoutes（新增）、CombatHud、SkillSlashSpriteRenderer、build.gradle、gradle/equipment-2d.gradle（新增）、routes/2d.json（新增）及本交接。此次范围到此停止，详细 Runtime、素材和 provider 接入交用户后续安排。

## 追加：Harness 收口三项缺口（2026-10-13 18:15）

先对上文的两处时点：§已有证据与未完成项第 4 条写的"本轮没有编译/启动/跑测试"指的是 13:57 那一手，
18:06 的追加节已补上集中编译与定向测试；本节再补 Harness 这一手。**待填项 ①参数缺失统一清理、
③异常事务边界、④客户端结束与重连 至此已实现；②普通武器战灰绑定入口 由上一节交付。**

### ① 参数缺失时统一清理

问题：`SkillDriver.tick` 的 spec==null 分支只 `cast.finish()` 就走人，本次施放生成的效果实体与位移追踪
（`RemembranceRuntime.states` 里的 `SkillMotion`/`SkillLanding`）留在世界上且不再同步；
`SkillDriver.cancel` 也要求 spec 与 runtime 同时在才调 `onCancel`，于是"重载丢项"这条路径两边都漏。

改法：

- `SkillDriver.Runtime` 新增 `onAbort(player, cast, tick)`：与 `onCancel` 的唯一区别是**拿不到 spec**，
  默认退化为 `onDetach`。接口注释里写死两条禁令：不许读 spec、不许据此结算伤害。
- `SkillDriver.abort(player, cast, tick, reason)`：调 `onAbort`（清理异常吞进日志，清理失败不允许拖垮服务端 tick）
  → `finish()` → `sync()`，客户端收到 IDLE 快照后自动回原版姿态。
- `tick` 的 spec==null 分支、`cancel` 的 spec==null 分支都改走 `abort`；`cancel` 调 `onCancel` 也加了 try/catch。
- `RemembranceRuntime.onAbort`：丢弃本次登记的效果实体、`motion.stop`（速度与重力交还原版）、
  **照常登记落地安全网**——安全网只救人、不结算伤害，登记它不产生收益。`onFinish` 里原来那段
  "悬空则登记安全网"抽成 `parkOrphan`，两条路径共用，行为不变。

### ② 事务已提交、Runtime 初始化却抛异常

问题：`SkillCasting.submit` 在事务成功后直接调 `SkillDriver.accepted`；`onBegin` 抛异常会抛穿服务端 tick，
而此时 FP 已扣、施放半开，冷却还留在一个根本没生效的技能头上。

改法：

- `SkillTransactions` 增加 `record Outcome(Result, float charged)` 与 `commit(...)`，`request(...)` 保留为薄封装
  （既有调用点与测试不受影响）。`charged` 取自 `fp.fp()` 的前后差，所以 `-PmanualSkills` 的无限 FP 如实报 0
  （回滚不会凭空加钱），多段技能也不需要调用方重算段号。
- `SkillDriver.acceptedSafely(...)`：包住 `accepted`，异常时记 ERROR（带 cause）→ `abort(...)` → 返回 false。
- `SkillCasting.submit`：提交成功但初始化失败 → `rollback(...)`：只退 `outcome.charged()` 那一笔、
  `PlayerFpState.clearCooldown(spec.id())`（新方法，只清一个技能）、动作栏提示"启动失败，已退回本次 FP N"，
  本次返回 `REJECTED`（客户端不起确认特效）。`SkillCasting.request` 的拒绝提示按原因分家：
  `!legal` 说"主手武器当前不匹配"，初始化失败由 `rollback` 自己说明。

费用策略（写在这里，避免下次重新讨论）：**不退已生效的前几段**（那些段真的打出了伤害/位移，
一起退就等于"反复触发启动失败刷 FP"）；**只退本次刚提交且确认未生效的那一笔**；金额由事务方报出，调用方不回算。

### ③ 客户端断线/重连残留

问题：`ClientSkills.resetLocal()` 此前是死代码（没有任何调用点），`SkillPoseAnimator.CASTING` 也从不清空。
重连后 HUD 会继续显示上一局的 `casting/activeSkillId`，而实体 id 会被复用——上一局的战技姿势会套到
新会话里 id 相同的新实体上，并且永远不会自己消失（服务端不会再发那一局的结束包）。

改法：`ClientCombat.checkLevel` 检测到 `minecraft.level` 变化（断线、重连、换维度都触发）时，
在原有 `resetLocal()` 旁边调用 `ClientSkills.resetLocal()` 与新增的 `SkillPoseAnimator.reset()`；
`SkillPoseAnimator.handle` 另外加了迟到包守卫（当前缓存 castId 比来包新时忽略），
避免乱序或上一段的包把正在播的新动作顶掉。

复核结论：**"输入只提交意图"已是事实**——按战技键只发 `IntentType.SKILL`，没有任何本地预测特效；
HUD 的战技显示读的是服务端同步的 `ClientSkills`，被拒绝时不会起确认表现。这一条无需改动。

### 已查实、未改：`cursed_blood_slice` 首次追击多收 4 点 FP

`SkillTransactions.commit` 的收费段号仍是 `spec.costForStage(followup ? cast.stageIndex() : 0)`；
追击那一刻 `stageIndex` 还是上一段（0），所以**第一次追击按 `fpCost=12` 收**，第二次才按 `fpCostFollowup=8`。
三次输入实收 12+12+8=32，按参数表应是 12+8+8=28。只有这件战技同时满足"多段 + 两个费用不等"
（`starcaller_cry`、`bloodboon_ritual` 两者都是 15，看不出来）。

一行改法是 `costForStage(followup ? cast.stageIndex() + 1 : 0)`。**没有动**：这改的是 FP 经济而不是纯 bug，
按"不改八技平衡参数"的约束等用户确认。`SkillCommitBoundaryTest` 刻意不锁这条行为，避免把 bug 固化成期望。

### 本轮验证与局限

- `test build` **BUILD SUCCESSFUL**，gradle 退出码 0：20 测试类 / 138 用例 / 0 失败 / 0 错误 / 1 既有跳过；
  `build/libs/eldencombat-0.2.0.jar` 1,075,992 字节。产物与日志：`docs/combat-evidence/harness-abort-20261013.md`。
- 新增 `SkillCommitBoundaryTest`（7 条，全绿）只锁回滚路径的不变量：报告金额 == 实际扣减、退款精确且只能退一次、
  无限 FP 报 0、中止后追击窗关闭且旧 castId 作废、清冷却不误伤其它技能。它不启动 Minecraft，
  **不能替代游戏内验收**。
- `runClient -PclientSmoke` **通过**（退出码 0，2m59s）：`CLIENT_SMOKE: 428 shared checks passed`、
  `CLIENT_SMOKE_PASS: all 33 visual fixtures rendered`、`CLIENT_NETWORK_PASS: all network checks completed`，
  整份日志 `FAIL|ERROR|Exception` 零命中。与本轮直接相关的夹具全绿：`cancel restored ordinary gravity`、
  `weapon switch forced cancellation` + `weapon cancellation removed emitted wave`、
  `respawn has no transient cast`、`death removed emitted wave`、`dimension transfer cleared cast and gravity`、
  `landing safety net observed the landing after the cast ended (LANDED)`、八技逐条
  `real key accepted / caused actual target damage / returned to idle / charged initial input once`。
  即：取消、死亡、换维度、落地安全网这些清理路径**在真实游戏里被我改过之后仍然成立**。
- `runClient -PclientSmoke -PclientSmokeReload` **通过**（23s）：`CLIENT_PERSISTENCE_COMPLETE:
  actual save/shutdown/restart passed`（属性 7/3/0/0/0、+2 重质变直剑、FP37、无残留施放）。
- `runServer -PserverSmoke` **失败，与本轮改动无关且为既有阻断**：`NoClassDefFoundError:
  net/blay09/mods/waystones/api/Waystone`，栈在 `AutomaticEventSubscriber.inject`。
  根因已定位到类：`testing/GracePrototypeHarness.java:16` 是 `@EventBusSubscriber(Dist.DEDICATED_SERVER)`，
  其 lambda 的合成方法返回类型是 `Waystone`，FML 构造模组时枚举该方法即需解析该类；
  未装 Waystones 的专用服务器因此整模组加载失败。`build.gradle` 只在发布 jar 里排除 testing 包，
  `runServer` 用 `build/classes/java/main` 所以排除不生效。这是赐福原型（13:33/13:47 落地）引入的，
  最后一次通过的专用服务器 smoke 是 12:33（`server-bow-final-20261013.log`），早于该原型；
  客户端不受影响正是因为该夹具声明的是 `Dist.DEDICATED_SERVER`。
  **没有修**（赐福不在本轮工单内）：① 往 `build/modpack-dependencies/` 放 Waystones；
  ② 让 runServer 类路径也排除 `com/dsh/eldencombat/testing/Grace*Harness*`。
  因此在专用服务器修好之前，"专用服务器及观察者"这条验收仍然是**未验证**，不能算通过。
- 仍未验证（需要专门注入场景，现有 smoke 不含这些夹具）：施放中参数丢失后的效果/位移释放与结束同步；
  `onBegin` 抛异常时的退款、清冷却与玩家提示；断线重连后 HUD 与姿势缓存不再残留。
  注入办法：施放中把 `SkillDriver.specs()` 里对应 id 移除；或临时注册一个 `onBegin` 抛异常的 Runtime，
  预期日志分别是 `[skills] 中止施放 <id>：缺少参数定义` 与 `[skills] 运行时初始化失败，终止本次施放`。
- 完整命令（顺序有意义：先编译+单测，再客户端两段，专用服务器单列）：

```powershell
$env:JAVA_HOME='D:\JDK21'
.\gradlew.bat clean test build --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"
.\gradlew.bat runClient -PclientSmoke --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"
.\gradlew.bat runClient -PclientSmoke -PclientSmokeReload --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"
.\gradlew.bat runServer -PserverSmoke --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"   # 先解决上文 Waystones 阻断
```

`clean` 会连 `build/clientSmoke/saves/codex-player-smoke` 一起删掉（prep 任务会重建），
第二段的 `-PclientSmokeReload` 必须紧跟第一段、且中间不要 `clean`，否则持久化那条夹具没有存档可验。
本轮为了保留存档与产物**没有**执行 `clean`，用的是一次性 `ForkJoinPool`/单次 daemon；仓库无 git，
改动前源码已由 `docs/code-backups/20261013-harness-abort/` 备份。

### 本轮修改文件与备份

`server/skills/SkillDriver.java`、`server/skills/RemembranceRuntime.java`、`server/skills/SkillTransactions.java`、
`server/skills/PlayerFpState.java`、`server/skills/SkillCasting.java`、`client/SkillPoseAnimator.java`、
`client/ClientCombat.java`、`src/test/.../SkillCommitBoundaryTest.java`（新增）、本文件。

改动前源码备份在 `docs/code-backups/20261013-harness-abort/`。注意：该目录里的交接文档副本与当前版本相同
（Codex 18:06 那节已在内），**不是旧版备份**；8 个 java 的备份是真实的改动前版本。

## Codex 审查修复（2026-10-13）

已修复本次审查指出的两处运行逻辑问题，并纠正退款测试：
- `SkillCasting.submit` 对事务提交拒绝补充 FP 不足或状态不满足提示；初始化失败仍保留专用退款提示，武器不匹配提示保持原入口处理。
- `rollback` 在退款及清除冷却后立即同步，避免客户端等待下一次周期同步才恢复 FP 显示；修正退款日志占位符。
- 退款测试从非满额 80 FP 起步，验证单次退款恢复原余额，移除“重复退款被上限夹住就等于幂等”的错误断言。新增 FP 不足拒绝追击时不扣费、不消耗窗口的用例。

集中验证：`compileJava test --tests '*SkillCommitBoundaryTest' --tests '*RemembranceSkillsTest' --offline --no-daemon` 成功，25 项测试全部通过（8 + 17，无跳过）。日志：`docs/combat-evidence/review-fixes-20261013.log`。
本次未启动游戏；纯逻辑测试没有覆盖客户端动作栏显示、网络同步实际到达，以及真实 Runtime 抛异常的完整链路，不能据此声称这些场景已实机验收。此前记录的专用服务器 Waystones 夹具阻断未在本次改动中处理。
修改前备份：`docs/code-backups/20261013-review-fixes/`。

## 最新表现交接：三个 2D 样板（2026-10-13）

用户认为当前特效难看。表现方向以 [VFX-REDESIGN-PLAN-20261013.md](VFX-REDESIGN-PLAN-20261013.md) 为准：原版像素风、2D 序列帧；12 日体素盒体规范不再作为正式主效果要求。本节是源码核对与待实现任务，当前效果没有重做。

### 素材来源与本轮边界

**最新分工覆盖此前安排：用户已明确将特效制作交给美术部门。** 共同工单为 [ART-VFX-WORK-ORDER-20261013.md](ART-VFX-WORK-ORDER-20261013.md)。仅恢复水鸟乱舞、命定之死、黄金波动三个 2D 样板，不自动恢复装备、地图或 UI 美术。

- 美术独占 `art/vfx-2d/` 新素材，负责来源/许可核对、PNG 序列、图集、manifest、色板和离线动态预览；新制作工具位于 `art/tooling/`。真实产物入口为 `docs/ART-VFX-HANDOFF-20261013.md` 及其中列出的 manifest/PNG 路径，以交付文件为准，不预设文件名或声称素材已到齐。
- 战斗负责 Java、`src/main/resources`、Gradle、build 与运行实例的接入写入。收到真实素材与 manifest 后，再核对帧序、锚点、朝向、显示尺寸及染色通道，安排 Kind 路由、世界空间平面、图集注册、资源同步和实机验证。不得自行绘制或覆盖美术目录。
- Harness 不再承担贴图制作；若用户后续安排其续写代码，仅承接渲染/资源接入代码。此刻不启动 Harness、不另开美术代理、不并行构建，不因三系统进度报告中的建议命令复跑测试。

按新方案优先评估 Foozle Lucifer Effects（https://foozlecc.itch.io/lucifer-effects）、Kenney Particle Pack（https://kenney.nl/assets/particle-pack）。方案记录其作者页为 CC0，但候选尚未等于已准入素材；美术部门下载具体包后须保存作者 URL、包版本/哈希、随包许可和修改记录，逐张判断是否适合像素弧线/火焰/波前。Kenney 的大尺寸粒子不能直接当柔光刀光。Henry Software 候选收费，本轮不购买。不拆取法环或 ARR 模组资源。
战斗部门此次只更新交接；素材制作由已恢复的美术部门依共同工单执行。下文原有代码、资源和阶段约束继续有效。

### 真实现有入口（路径均相对 src/main/java/com/dsh/eldencombat）

|样板|服务端阶段/生成入口|客户端替换入口|方向和挂点约束|
|---|---|---|---|
|水鸟 waterfowl_dance|server/skills/RemembranceRuntime.activeTick：ACTIVE 内分成三个 burst，visual(... WATERFOWL, radius, 8, burst, true)|client/effects/SkillEffectRenderer → FxGeometryCache → effects/SkillSlashSprites.build → SkillSlashSpriteRenderer.draw|实体跟随施法者位置；客户端外层旋转 -yaw。当前 draw 又加 +yaw 并套 cameraOrientation，故图片跟镜头转。主斩击必须改成技能空间的斩击平面，不能只换 PNG。|
|命定之死 destined_death|同一 activeTick 分支，Kind.DEATH_SLASH，三个 burst，各生成 8 tick 视觉实体|同一渲染链；按 Kind 分开形状与帧路由|黑红弯月是主轮廓，短红焰是余迹。当前位置挂点同样是施法者原点，并非武器骨骼挂点；Strip 的 x/y/z 是相对偏移，需对齐已有姿势。|
|黄金波动 wave_of_gold|RemembranceRuntime.onActive 生成 GoldenWaveEntity；使用 state.motion.direction()，速度 .9，范围/寿命/半径取 spec|client/effects/GoldenWaveRenderer.render → FxGeometryCache → SkillEffectMesh.build(GOLD_WAVE)|不走 SkillSlashSpriteRenderer。现有 atan2(dirX,dirZ) 定义波前方向，局部 +Z 对应前进方向；沿地面移动，不跟随施法者或镜头。|

### 阶段与播放区间

- 阶段源是 SkillDriver 的 WINDUP → ACTIVE → RECOVERY → IDLE；起势可以读取已有同步阶段和 assets/eldencombat/skill_poses/{waterfowl_dance,destined_death,wave_of_gold}.json 对齐角色动作。不要为了视觉节奏改 FP、伤害、阶段时长或命中窗口。
- 当前 RemembranceSkillSpec.defaults 中水鸟 windup/active/recovery 为 10/36/5 tick，命定之死 8/24/5，黄金波动 10/4/5；运行中以实际 spec 为准，这些是源码默认值，不能把视觉帧数当 tick 数。
- 水鸟/命定之死 burst = min(2, elapsed*3/duration)，视觉 phase 是 burst 0/1/2，不是 cast.stageIndex。默认水鸟在 ACTIVE 的 0/12/24 tick 起三次短斩；命定之死在 0/8/16 tick 起三次，每个视觉实体持续 8 tick。原有各 burst 命中窗口仍由 elapsed-start<6 控制，勿让每帧贴图触发伤害。
- SkillEffectEntity 同步 KIND/RADIUS/DURATION/START/PHASE/OWNER；progress(partialTick) 由服务器起始游戏时间和持续时间计算。当前 SkillEffectRenderer 使用 progress(0)，所以几何按 tick 采样。设计样板时明确是否要插值，并同步调整缓存采样，不能误称当前 16 帧都会在 8 tick 内逐帧完整显示。
- 黄金波动用 visualRadius(partialTick)、visualProgress(partialTick)、visualTravel(partialTick)、blockedLanes()。形成/稳定前沿/渐散尾迹应绑定这些值，不以玩家施法结束就强行缩短波的原有寿命。注意 FxGeometryCache 对 GOLD_WAVE 将 profileTravel 截在 FORMATION_DISTANCE；如新增逐帧波前，缓存键需纳入帧/动画时间，否则形成后可能停帧。
- 命中反馈只能在真实伤害成功后发生：近战入口是 RemembranceRuntime.hit 中 SkillHitEffects.hurt(...)>0；黄金波动在 GoldenWaveEntity 自己的命中路径。现有主效果实体不提供逐目标命中通知；新增命中表现需接真实结果，不能由主刀光 progress 猜测命中。
- 消散在视觉实体有效生命周期内完成；SkillEffectEntity 超时或 owner/castId 失效会丢弃，黄金波动另有贴地、障碍与寿命退出逻辑。不能为残影保留已结束的伤害实体。

### 当前资源字段与必须补的接线

现有清单 src/main/resources/assets/eldencombat/routes/2d.json 只有一个共享的 fx.slash：status、fallback、frames、atlas。当前 frames 是 minecraft:explosion_0..15，atlas 是 minecraft:textures/atlas/particles.png。

1. PresentationRoutes.prepare 只接受该粒子图集且强制 16 帧，slashFrame(index,fallback) 不含 Kind 参数。只替换 fx.slash 会连 CURSED_SLASH 一起改变，不能当作三个独立样板已完成。DSH 需扩展为按效果 Kind/技能区分的路由，同时保留旧调用兼容与未入选技能原状。
2. 现有帧 ID 例如 eldencombat:waterfowl/frame_00 会对应 assets/eldencombat/textures/particle/waterfowl/frame_00.png。PNG 存在只通过第一层检查，资源还必须实际缝进粒子图集；新增 assets/eldencombat/atlases/particles.json 的 source 或采用明确的粒子资源注册，不能只把 PNG 放入目录。slashFrame 会检查 atlas sprite，缺帧会回退；正式样板不能悄悄回退爆炸帧却宣称替换完成。
3. 当前 `fallback` 是清单描述，PresentationRoutes 并未解析它；真实回退仍是 SkillSlashSpriteRenderer 的 FRAMES 常量。应为三个样板设计简单、可辨认的像素轮廓降级，区分已加载与缺失，不能仅改 fallback 字符串。
4. 现有 Strip 字段为 x/y/z、width/height、roll、rgb、alpha、frame，没有平面法线、pitch 或独立材质路由。样板至少要明确斩击平面和素材选择；旋转时检查外层 -yaw，避免删掉 billboard 后又重复叠 yaw。烟雾/小火星可保留 billboard，主斩击不行。
5. 黄金波动另补序列帧/像素波前渲染接线，保留 WaveFront 的横向分段及 blockedLanes，不能用一整张无裁剪平面穿过已阻挡的区域；不影响共享该渲染器的 takers_flames。
6. FxPalette 读取 assets/eldencombat/fx/palette.json，字段 colors.<色组>.highlight/mid/shadow 与 slashSprites。现有水鸟用 bone，死亡斩用 obsidian/death。使用有色素材时检查顶点 rgb 乘色，避免黑红素材再次乘暗色后变黑块。
7. 复用 FxGeometryCache、VfxBudget、FxRenderBudget。当前近处有贴片仍会绘制 0.18 alpha 盒体，CORE 降级只画盒体；需要替换样板对应的正常及降级轮廓，避免新贴图上继续叠交叉盒体。资源重载后按需使缓存失效，预算不足时保留一个可辨认主轮廓。

### 美术参数交付与战斗接入验收

美术为每个样板提交一份真实参数表：素材包与许可、透明 PNG 尺寸、总帧数 N、每阶段帧区间、各 burst 的播放方式、挂点偏移、平面朝向、建议世界显示宽高（格）、alpha 淡入/淡出曲线。战斗接入时补充实际采用值及预算/缺资源降级。当前接口固定 16 帧只是旧实现限制；不同帧数由双方在真实素材交付后协调，不能为凑帧数重复素材或拉伸爆炸。具体尺寸/曲线由样板观感定，不在本交接硬定未经验证的新值。

先交付水鸟银白短弧、命定之死黑红弯月、黄金波动金色地面波前三个样板的实机短片：第一/第三人称，各含亮处/暗处，转动镜头检查斩击方向、连续施放检查残留，多人叠加检查预算。截图/浏览器预览只作初选。用户确认三个样板后再推广其他战技。

本节仅核对源码并更新文档；未修改 Java/资源，未编译、未启动游戏，未下载候选素材，当前效果仍为旧效果。

## DSH 接入回执：2D 路由框架（2026-10-13 晚）

用户选定「只做 DSH 侧框架」后，上面 7 条接线已全部实现并集中验证。改动文件、路由模型、清单 schema、每样板参数表与未验证项见
[`combat-evidence/vfx-2d-routing-framework-20261013.md`](combat-evidence/vfx-2d-routing-framework-20261013.md)。

- **纠正上节第 2 条**：新增 `assets/eldencombat/atlases/particles.json` **既不需要也不会被读取**。粒子图集定义文件路径由原版固定为
  `assets/minecraft/atlases/particles.json`（`ParticleEngine.PARTICLES_ATLAS_INFO = minecraft:particles`，`SpriteSourceList` 用
  `FileToIdConverter("atlases",".json")` 派生），其 `directory` 源经 `DirectoryLister` + `MultiPackResourceManager.listResources`
  **跨所有 namespace** 收集 `textures/particle/**.png`；因此 PNG 放进
  `assets/eldencombat/textures/particle/<id>/frame_NN.png` 即自动成为 sprite `eldencombat:<id>/frame_NN`。要新增图集源类型时才应写
  `assets/minecraft/atlases/particles.json`。证据行号见上述证据文档 §4。
- **零回归**：四条路由（waterfowl / death_slash / cursed_slash / golden_wave）在 `assets/eldencombat/routes/2d.json` 里全部
  `status: pending`，因此现有观感与改动前逐字节相同（相机 billboard + `minecraft:explosion_0..15` + 0.18 alpha 盒体）。素材到位后只改 manifest
  的 `status` 与帧数组即可切换，无需再动 Java。
- **接口已按「不固定 16 帧」设计**：`fx.routes.<id>.frames` 可声明 1..64 帧，`frameIndex` 把 16 个语义步重采样到该帧数；清单还可覆盖
  `orientation` / `plane.yaw` / `plane.pitch` / `tint` / `outline.shape` / `outline.color`。缺帧、核心 LOD、预算压力一律降级到程序化像素轮廓
  （ARC / CRESCENT / WAVE_FRONT），不再叠加交叉盒体；黄金波动逐车道出贴片并保留 `blockedLanes` 裁剪。
- **验证**：`compileJava test --offline --no-daemon` = `BUILD SUCCESSFUL`，27 个测试类 / 186 项 / 0 失败 / 0 错误 / 1 既有跳过；新增
  `FxSlashRouteTest` 8 项（含按客户端同样规则解析真实 `2d.json` 的用例）。日志 `docs/combat-evidence/vfx-2d-routing-20261013.log`。
  **未启动游戏**，实机观感、图集真实缝合、斩击方向随镜头转动均未验证；`-PashSmoke` 与专用服务器夹具仍挂账。
- **已知接口缺口**：贴图四边形宽高直接取 `Strip.width/height` 并把整张 PNG 的 UV 映射上去，PNG 比例会被拉伸到该宽高（水鸟 ≈25:1、
  黄金波动车道 ≈1:1）。美术素材比例若不同，需在清单增加「显示宽高（格）」覆盖字段或调整 `SkillSlashSprites` 的尺寸公式，此条应在美术
  manifest 交付时一并确认。
- 改动前备份：`docs/code-backups/20261013-slash-route-framework/`（9 个文件 CHANGED，`SkillEffectRenderTypes.java` 仅留档未改）。

## 最新分工与接续入口（2026-10-14）

用户最新要求：Codex继续框架设计，具体代码编写交给DSH。新的执行合同为 [DSH-VFX-IMPLEMENTATION-CONTRACT-20261014.md](DSH-VFX-IMPLEMENTATION-CONTRACT-20261014.md)，覆盖旧“三样板/pending/只改ready”的实施描述。美术已交付八战技十组素材；工作区已有部分真实接入实现和28项定向测试通过，但环绕相机夹具、辅助地块恢复、火墙障碍语义、缺图重载等仍待DSH收口。10组atlas加载日志不等于全部画面验收。Codex本轮只更新设计交接，不再续写实现或启动构建。
