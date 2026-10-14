# DSH：战灰、护符、赐福系统完整实现交接

日期：2026-10-13
工作区：D:/codexout/mcmod
用户分工：Codex 写框架，DSH 完成剩余代码。用户要求不处理美术，不边写边测；按模块完成实现后集中验证。本文是交接任务，不代表功能已经完成。

## 当前已落地的框架

### 战灰
- `server/skills/AshBindings.java`：保留唯一绑定组件和普通武器白名单；新增不可修改的 definitions() 目录视图。追忆/锁定武器保持不可覆盖。
- 新增 `server/skills/AshWorkshop.java`：catalog、BIND/REPLACE/REMOVE 三种操作；服务端取实时主手，先检查赐福本地权限，再检查拥有权，最后调用 AshBindings。
- `Ownership` 是待接入的服务端持久化解锁查询；未安装时返回 OWNERSHIP_UNAVAILABLE，不能假装玩家已拥有所有战灰。它是“永久解锁”的框架假设；若最终采用消耗型战灰，必须将此查询升级成原子消耗/回滚事务，不能只删物品再绑定。
- 未新增普通战灰定义或假 Runtime。现有八追忆战技继续使用原来的施放/FP/冷却/命中状态机。

### 护符
- 新增 `server/TalismanEquipment.java`：服务端快照、从真实手持装备、独立卸下入口；施放或战斗动作忙碌时拒绝换装。
- `item/TalismanItem.java` 的右键已接新入口。`Talismans.unequip` 能退回旧护符，无需持有第二件才能由菜单卸下；修正再次右键同类护符时旧护符丢失的问题。
- 当前仍为旧版单槽字符串附件，这是兼容基线，绝不是完成后的 Curios 多槽系统。现有伤害/精力/FP 等数值出口继续是 Talismans。
- 当前旧版存储只有 ID，不能保存原 ItemStack 的附魔、自定义名等组件；DSH 必须在正式装备容器中解决，不能宣传为无损物品存储。

### 赐福
- 新增 `server/grace/GraceOperations.java`：common 侧无 Waystones 类型的 Backend 接口、localAccess/rest/travel 入口；未安装后端返回 UNAVAILABLE。
- `GraceService.register` 安装现有 Waystones 适配器；旅行网络入口改走 GraceOperations，保留原有 sequence、发现/私人权限、战斗限制、异步旅行和落点规则。
- `GraceRest.rejectLocalUse` 拆出无恢复副作用的本地权限校验，供休息和战灰菜单共用。远程地图可旅行，不因此有权远程休息或换战灰。
- 休息目前仅恢复 FP；恢复生命/精力/药瓶、重生点等均未借框架名义默默加入。

## DSH 续写顺序和范围

### 1. 先完成赐福底座
1. 将原型开关与依赖准入整理为正式启动路径。缺失 Waystones/Balm 时普通战斗模组仍能启动，赐福接口显示不可用；存在依赖才加载适配类。客户端地图同样隔离 JourneyMap 类型。
2. 修复 GracePrototypeHarness 的自动订阅类加载问题：未装 Waystones 时，FML 反射其含 Waystone 返回类型的合成方法可能导致专用服务器启动失败。测试夹具应只在显式测试模式且依赖齐全时注册，不能只在事件方法内部检查布尔值。
3. 完成赐福菜单及其服务器 Menu/有效距离检查；菜单提供休息、旅行、战灰装配入口。每次执行重新验证真实 UUID、节点有效性、发现/权限、距离、维度、战斗状态，不把“打开过菜单”当永久授权。
4. 沿用 Waystones 的节点/发现/原生旅行与费用管线，GraceRegistry 只维护本项目节点身份。节点删除/私有化、掉线、切维度、服务端关闭时清理 pending；不得让异步回调在玩家离线后施加效果。
5. 将 rest 的资源恢复规则集中配置，默认保持当前 FP 恢复行为，其他资源明确配置后接现有权威状态，不重写第二份资源系统。旅行与打开菜单都不能顺手恢复资源。
6. 补齐节点快照、菜单状态和可读失败反馈，地图只显示服务端允许披露的节点。

### 2. 完成战灰闭环
1. 实现服务器权威的玩家战灰解锁存储（完整 namespaced ID、落盘、死亡/重登语义、幂等奖励）；通过一次安装连接 AshWorkshop.Ownership。奖励来源留给现有探索/奖励能力，不在这里造另一套任务系统。
2. 注册实际普通战灰目录和 Runtime，使用现有武器类型/物品白名单。未实现项必须不可选不可绑定；不要拿八追忆 Runtime 冒充普通战灰。
3. 实现赐福内战灰选择/兼容性预览及绑定、替换、卸除。请求只携带菜单操作、ID 等选择信息，不相信客户端传来的拥有权、FP、段数或 ItemStack。
4. Menu 容器验证、按钮处理、结果同步必须有完整实现；处理重复点击和打开菜单后换手/离开/进入战斗。若 UI 展示某把武器，服务端提交前核对它仍为同一目标，不能误改后来拿起的武器。
5. 保留强化、质变、原有物品组件；追忆和锁定战技不允许覆盖。不要修改 WeaponState 旧 codec 来硬塞战灰，继续用 ASH_BINDING。
6. 统一走 SkillCasting/SkillTransactions/SkillDriver，核对每段 FP 收费。已有待查项：cursed_blood_slice 首次追击 stageIndex 与 costForStage 的对应关系，不要沿用错误索引又在 UI 单独修数字。

### 3. 完成护符闭环
1. 优先落实项目复用方向中的 Curios 适配，先确认本工程精确依赖再编码。不要凭空引用未安装版本 API；也不要把新的自制多槽附件与 Curios 同时设为装备权威。
2. 确定装备权威后，将旧 TALISMAN 字符串迁移一次：保留玩家旧物品及效果，不重复返还、不重复迁移；改用能保留完整 ItemStack 的容器。兼容路径需处理未知物品/缺失数据。
3. 所有现有战斗效果调用点仍通过 Talismans 统一读取，重构这些出口去读取最终容器并聚合。只改菜单而不改数值出口不算完成。槽数、重复同类/同系列限制、倍率叠加与上下限集中定义；未有最终规则时保留单槽默认值，并将新增规则标为可配置。
4. 完成装备、卸下、替换、背包满、死亡、重登、切维度、/reload 后数值刷新与同步。最大 FP 降低按现有规则截断，不能通过反复换护符补满 FP。
5. 修复远程客户端 tooltip/装备界面依赖服务端静态 specs 的问题：客户端只能使用同步后的展示数据，战斗效果仍由服务器计算。
6. 全 ID 命名空间迁移需兼容旧 eldencombat 路径字符串，不能让不同 namespace 同名护符互相覆盖。确认旧 equip 替换时旧物品不存在的处理，不允许记录被悄悄清掉。

## 共同边界
- 不处理美术资源；UI 使用现有资源或原版控件，功能信息与错误原因清楚即可。
- 不重写现有八战技、资源状态、原版战斗或地图引擎。
- 菜单/网络入口只接服务器线程，不公开可以由客户端安装 Backend/Ownership 的请求。
- 不要把 TODO、未安装适配器或返回成功的空方法算作“全部完成”。如果内容数量或平衡值未确定，明确列出实际支持列表和配置默认值，不能编造全量内容完成报告。
- 保留这次及此前 bug 修复：FP 不足提示、初始化失败按实际扣费退款并即时同步、正确的非满额退款断言。
- 本工作区无 Git，改现有文件前备份；构建、运行、资源同步保持单写者，避免多个进程同时改 build。

## 集中验收（全部实现后执行）
1. 编译及相关纯逻辑测试：战灰权限/兼容与锁定、奖励去重/保存、FP 段费用、护符聚合/迁移/物品守恒、赐福拒绝规则。
2. 客户端：新存档完整走“发现赐福 → 解锁战灰 → 装配 → 施放 → 装卸护符 → 休息 → 地图旅行”；退出重进复核。不要用 /give 或 OP 绕过实际玩法后声称闭环通过。
3. 专用服务器：无可选依赖能启动；有依赖双玩家权限隔离、重复请求、移动离开赐福、战斗/死亡/掉线、节点删除/权限变化、异步落点失败。
4. 给出实际变更文件、明确实现/未实现列表、执行命令、测试数量与日志路径；未运行场景明确标记未验收。

## 本轮框架验证
已在全部框架修改完成后执行一次 `./gradlew.bat compileJava --offline --no-daemon`，BUILD SUCCESSFUL。日志：`docs/combat-evidence/three-systems-framework-compile-20261013.log`。仅有现有客户端事件订阅 API 的弃用警告，无编译错误。此轮未执行单元测试、未运行游戏，不代表 UI、物品交易或多人行为已验收。

备份：docs/code-backups/20261013-three-systems-framework/。

---

## 追加：Harness 2026-10-13 20:10 · §1.1 / §1.2 / §1.4 / §1.5 已实现并验证

**已实现：**

1. **§1.1 依赖准入与正式启动路径。** 新增 `server/grace/GraceDependencies.java`（**只做依赖判定，
   不含任何 Waystones/Balm 类型**，否则判断本身就会 NoClassDefFoundError）与 `server/grace/GraceBootstrap.java`
   （`catch (Throwable)` 把链路错误降级成"赐福不可用"，不让可选子系统拖死整模组）。
   `EldenCombat.onCommonSetup` 不再读 `-D eldencombat.gracePrototype`，改为**依赖齐备即装载**。
   客户端侧：`ClientPayloadTrampoline.handleGraceNodes` 改用 `GraceDependencies.mapPresent()`；
   `GraceMapPlugin` 去掉 property 门禁，并只在**收到过服务端快照**后才清理 Waystones 的原生插件点
   （避免把没启用赐福的存档顺手清掉）；旅行包交给 `GraceOperations.travel`，后端缺失时明确回
   "赐福系统尚未启用"，不再静默丢弃。
2. **§1.2 夹具类加载修复。** 两个夹具摘掉 `@EventBusSubscriber`，改为 `install()` 显式注册，
   注册条件是「显式测试模式 **且** 依赖齐全」（分别在 `EldenCombat.onCommonSetup`（DEDICATED_SERVER）
   与 `EldenCombatClient`（CLIENT）里调用）；顺手把 `GracePrototypeHarness` 的 `orElseGet(lambda)`
   改成显式分支——lambda 合成方法的签名会把 Waystone 类型带进反射可见的方法表。
3. **§1.4 pending 清理。** `GraceService` 增加 `pendingSince` 登记时刻表与 `sweepPending`：
   超时（`pendingTimeoutTicks`，默认 1200 tick）或**目标节点被删除/失效**时作废 pending；
   登出/服务端关闭照旧清理；切维度只在「去的不是目标维度」时作废
   （否则会抢在 `WaystoneTeleportEvent.Complete` 之前吃掉票据，冷却与完成日志都会丢）。
   异步回调仍只做反馈，不改玩家状态。
4. **§1.5 rest 恢复规则集中配置。** 新增 `config/eldencombat-grace.json`（`GraceSettings`，`/eldencombat reload` 可热读）。
   默认值 = 既有行为（只回 FP）。`restHealth` / `restStamina` / `restFlasks` 显式改成 `FULL` 后分别接
   **原版生命值**、`CombatState.stamina`、`CombatState.potionCharges`（现有权威状态，未新造第二份资源），
   恢复后立即 `SkillServerEvents.sync` / `ServerCombat.syncNow` 同步。旅行与打开菜单**永不**恢复资源。
   **重生点故意没有开关**：它没有接本项目权威状态，不能被静默启用，也不该给一个配了不生效的假开关。

**验证（真实日志，非推断）：** `compileJava` 退出码 0（4 条既有弃用警告）；
`test` 20 类 / 139 用例 / 0 失败 / 1 跳过；
`runServer -PserverSmoke`（classpath **无** Waystones/Balm）退出码 0，打印
`GRACE_UNAVAILABLE: 缺少依赖 [waystones, balm]；赐福接口显示不可用，普通战斗功能不受影响`，
随后 `SERVER_SMOKE_PASS: 428 shared checks` + `SERVER_SMOKE_COMPLETE`，**此前那条
`NoClassDefFoundError: .../Waystone`（`AutomaticEventSubscriber.inject`）已消失**；
`runServer -PgracePrototype` 退出码 0：`GRACE_ADAPTER_READY`（且该行不依赖原型 property）
+ `GRACE_PROTO_HARNESS_REGISTERED` + 4 条依赖版本 + 两个真实节点落盘 + `GRACE_PROTO_SERVER_COMPLETE`。
证据与命令：`docs/combat-evidence/grace-baseline-20261013.md`。

**仍未做（不得算作完成）：** §1.3 赐福菜单；客户端**有依赖**下的完整玩法闭环；
双玩家权限隔离 / 掉线 / 节点删除等多人场景；三条新恢复规则的真人右键验证。

**备份：** `docs/code-backups/20261013-grace-baseline/`（13 个改动前副本）。

---

## 追加：Harness 2026-10-13 20:30 · §1.3 赐福菜单 + §1.6 披露/反馈 已实现并验证

**§1.3 赐福菜单（新增 8 个文件）**

- `registry/ModMenus.java`（`MenuType<GraceMenu>`，`IMenuTypeExtension`）、`server/grace/GraceMenu.java`
  （**0 槽位服务菜单**；`stillValid` 每次重校验访问权限，失效自动关菜单，但**不作唯一授权**）、
  `server/grace/GraceMenuActions.java`（服务端执行口）、
  `network/GraceMenuStatePayload.java` + `network/GraceMenuActionPayload.java`、
  `client/grace/GraceScreen.java`（原版控件：左旅行列表 / 右战灰装配 / 底部休息与卸除）、
  `GraceScreenState.java`、`GraceClientActions.java`。
- **入口**：方块右键从"直接休息"改成"打开菜单"。四个条件全满足才抢这次交互（本项目赐福 / 玩家**已发现** /
  非潜行 / 访问权限校验通过）；抢下时**必须同时 DENY `useBlock`+`useItem`**，否则 Waystones 的原生界面会在事件之后
  打开并覆盖菜单；未发现时**不抢**，保留原生激活入口。
- **授权口径**：赐福 UUID 取服务端 `GraceMenu` 实例里的那份（`MenuProvider` 捕获），客户端只能传「操作 + 选择 ID + 序号」。
  `GraceMenuActions.apply` 先用 `containerId` 挡过期/伪造包，再过 `GraceOperations.localAccess`
  （节点有效性/发现/权限/距离/维度/战斗状态），最后才分派到 `rest` / `travel` / `AshWorkshop.apply`。
- **§1.6**：新增 `GraceAccess.authorizedNodes(player,limit)`，地图下发与菜单披露**共用同一份判定**
  （避免"地图看不到但菜单能传"的缺口）；所有失败码经 `bindingReason` 翻译成中文短句；快照限长可配。
- 菜单与地图**共享序号计数器**（`GraceClientActions.nextSequence()`）——服务端 `requestTravel` 用序号挡重放，
  两边各数一套会互相把对方判成重放。
- `ModPayloads` 通道版本 `4 → 5`；菜单包处理器先判 `GraceOperations.available()`，避免把含 Waystones 类型的服务端类
  加载进无依赖的专用服务器。

**验证（真实日志，非推断）**：`compileJava` 退出码 0；`test` 20 类 / 139 用例 / 0 失败 / 1 跳过；
`runClient -PgracePrototype -PgraceMenu` 退出码 0，赐福菜单夹具 **8 项断言全过**
（`GRACE_MENU_SCREEN_PASS` 只披露 2 个已授权节点 / `GRACE_MENU_REST_PASS: fp=100/100` /
`GRACE_MENU_FORGERY_REJECTED` / `GRACE_MENU_TRAVEL_REJECTED`（未发现节点）/ `GRACE_MENU_RANGE_REJECTED`（超距离）/
`GRACE_MENU_TRAVEL_DISPATCH_PASS` / `GRACE_MENU_COMPLETE`）；
无依赖 `runServer -PserverSmoke`、有依赖 `runServer -PgracePrototype`、`runClient -PclientSmoke`
（428 checks / 33 visual fixtures / all network checks / 客户端日志 0 个 Exception）全部退出码 0。
证据：`docs/combat-evidence/grace-menu-20261013.md`。

**仍未做（不得算作完成）**：真人鼠标点击（夹具走的是原版控件的 `onPress()` 公共回调，日志里已自带声明）；
菜单开着时进入战斗的拒绝路径；旅行**真正落地**（只断言到"授权通过并已提交"）；
私人/团队赐福的双玩家实测；战灰列的界面路径（`AshWorkshop.catalog` 目前为空，等 §2.1/§2.2）；
多人场景（双玩家隔离、重复请求、节点删除/权限变化、异步落点失败）。

**备注**：§1.3 用了 `AshWorkshop`/`AshBindings.editability` 的只读接口，没有改动战灰的绑定语义。

**备份：** `docs/code-backups/20261013-grace-menu/`（20 个**改动后**状态快照；改动前副本见 `20261013-grace-baseline/`）。

---

## 追加：Harness 2026-10-13 20:38 · §2.1 战灰解锁存储 已实现并验证

**新增 4 个文件**

- `core/AshUnlockSet.java`——**纯逻辑**（不依赖 Minecraft，可单测）：只收完整 `namespace:path`（裸路径一律拒绝）、
  授予**幂等**、落盘用稳定字典序、读入时脏条目丢弃**并计数上报**。
- `server/skills/AshUnlockData.java`——服务端 `SavedData`（`eldencombat_ash_unlocks`）：UUID → 解锁集合；
  `grant` 只在真新增时 `setDirty()`；`revoke` 清空后移除该玩家的条目。
- `server/skills/AshUnlocks.java`——服务端入口（唯一权威）：`install()` 把 `AshWorkshop.Ownership` **接线一次**，
  `owns/grant/revoke/list`，全部服务端线程校验。**不造任务系统、不自己发奖励**。
- `src/test/.../AshUnlockSetTest.java`——9 个纯逻辑用例。

**修改 3 个文件**：`EldenCombat.java`（common setup 调 `AshUnlocks.install()`，**不引用 Waystones**，无依赖服务器同样正确）、
`command/EldenCombatCommand.java`（`/eldencombat ash [grant|revoke <id>]`，注释里标明**管理/验收面、不是正式奖励来源**）、
`testing/ClientSmokeHarness.java`（在既有死亡/持久化流程里加 3 条断言 + 2 行 PASS 日志）。

**关键取舍**：存**服务端 SavedData**而不是玩家实体附件——解锁是账号级进度，不该随实体销毁/克隆丢失，
而且**不需要 `copyOnDeath`**，因此没有"靠死亡克隆刷解锁"的口子。

**验证（真实日志，非推断）**：`test` 退出码 0，21 类 / **148 用例 / 0 失败 / 1 跳过**；
`runClient -PclientSmoke` 退出码 0：`ASH_UNLOCK_GRANTED` → `ASH_UNLOCK_DEATH_PASS`
（真实 `hurt(genericKill,10000)` + `PERFORM_RESPAWN` 之后仍在）→ `CLIENT_PERSISTENCE_SAVED`，
同帧还断言了"重复授予返回 false"的幂等性；
`runClient -PclientSmoke -PclientSmokeReload` 退出码 0：`ASH_UNLOCK_RESTART_PASS` +
`CLIENT_PERSISTENCE_COMPLETE: actual save/shutdown/restart passed`（真实关服重启后仍在）；
无依赖 `runServer -PserverSmoke` 退出码 0：`ASH_OWNERSHIP_INSTALLED` + `SERVER_SMOKE_PASS`。
证据：`docs/combat-evidence/ash-unlock-store-20261013.md`。

**仍未做（不得算作完成）**：**奖励来源本身没有接上**——按文档"留给现有探索/奖励能力"，而本工程目前
**没有**可用的探索/奖励实现，所以现阶段**没有任何玩法内途径**解锁战灰；`/eldencombat ash grant` 只是管理/验收面，
**不得**据此声称玩法闭环通过。另：战灰目录仍为空（§2.2），"解锁后菜单里可装配"要等 §2.2/§2.3；
双玩家隔离与存档损坏恢复未实测。

**备份：** `docs/code-backups/20261013-ash-unlocks/`。

---

## 追加：Harness 2026-10-13 20:48 · §2.2 普通战灰目录与运行时 已实现并验证

**新增 6 个文件**

- `core/AshWeaponGroups.java`——武器白名单分组（**纯逻辑**）。直接复用现有的 `WeaponProfile` id
  （本工程 33 件武器都是 `eldencombat:<profile id>`），不另立一套分类表；展开即完整 namespaced 物品 id。
- `server/skills/OrdinaryAshCatalog.java`——**实际支持列表**（不是"全部战灰"）：3 件，各带 `RemembranceSkillSpec` 单段参数
  （同人草稿值）。不依赖 Minecraft，可单测。
- `server/skills/OrdinaryAshRuntime.java`——**独立**的普通战灰运行时。`AshBindings.Definition.implemented()` 明确排斥
  `RemembranceRuntime`，所以这里不可能借追忆战技冒充；共用的是**服务端判定助手**
  （`SkillHitEffects` + 胶囊判交 + `SkillCastState` 去重），不是时间线实现。
  两种判定形状：绕身横扫（回旋斩）、身前胶囊扫掠（刺/斩）。
- `server/skills/OrdinaryAshes.java`——注册入口。四条同时成立才进目录：id 合法且与 `spec.id()` 一致、
  武器白名单展开后非空、每一项在物品注册表与武器定义表里真实存在、运行时不是追忆。
  任何一条不成立就不注册并打错误日志（宁可没有它，也不要"能选但永远失败"的条目）。
- `testing/AshSmokeHarness.java`——新引擎夹具（`-PashSmoke`，真实客户端 + 集成服务器）。
- `src/test/.../OrdinaryAshCatalogTest.java`——8 个不变量用例。

**修改**：`EldenCombat.java`（调 `OrdinaryAshes.registerAll()`）、`client/EldenCombatClient.java`、
`build.gradle`（`-PashSmoke` + `prepareAshSmokeWorld` + jar 排除）、`testing/GraceClientPrototypeHarness.java`（加拥有权断言）。

**关键取舍**：①普通战灰一律**单段**（`followupWindowTicks=0, maxStages=1`）——用户要求"简单的战技释放动作"，
并且刻意不让新内容踩在 §2.6 那条还没修的多段索引上；②**不位移**（无跃起/突进），因此不需要落地安全网与残留清理，
`onAbort` 也没有需要收回的东西；③非白名单武器返回 `UNSUPPORTED_ITEM`，夹具已实测。

**验证（真实日志，非推断）**：`test` 退出码 0，22 类 / **156 用例 / 0 失败 / 1 跳过**；
`runClient -PashSmoke` 退出码 0：

```
ASH_CATALOG_REGISTERED: 实际支持普通战灰 3 件：eldencombat:spinning_slash, eldencombat:heavy_thrust, eldencombat:stamp_upward_cut
ASH_SMOKE_SETUP: catalog=3 ash=eldencombat:stamp_upward_cut fpCost=11.0 目标血量=20.0
ASH_SMOKE_CAST_PASS: eldencombat:stamp_upward_cut 起手成功，FP 100→89（扣 11）
ASH_SMOKE_HIT_PASS: … 单段判定造成 16.728 点伤害（20.0 -> 3.2719994）
ASH_SMOKE_UNBIND_PASS / ASH_SMOKE_COMPLETE
```

赐福菜单夹具复跑退出码 0：菜单现在列出 3 件战灰（`GRACE_MENU_SCREEN_PASS: ashes=3`），
点未解锁的那件被拒（`GRACE_MENU_OWNERSHIP_PASS: 未解锁战灰被拒，notice=尚未获得此战灰`），
上一轮的 8 项断言仍全过；无依赖 `runServer -PserverSmoke` 退出码 0（目录在**没有** Waystones 的服务器上同样注册成功）。
证据：`docs/combat-evidence/ordinary-ash-catalog-20261013.md`。

**仍未做（不得算作完成）**：①**玩法内解锁途径仍然没有**（§2.1 的奖励来源未接），所以
"解锁 → 菜单装配 → 施放"这条**完整玩法链**没有跑过；本轮的拥有权证据只是"未解锁 → 被拒"。
②§2.3 其余部分仍未验：成功绑定/替换/卸除（菜单路径）、重复点击、打开菜单后换手/离开范围/进入战斗、
"UI 展示的武器与服务端提交时目标一致"。③三件里只实测了 `stamp_upward_cut` 的判定形状，
`spinning_slash` 的绕身判定与 `heavy_thrust` 都没打过真实目标；13 件白名单武器未逐件实跑。
④平衡值是草稿，未做手感/数值评估，也没有真人操作。

**备份：** `docs/code-backups/20261013-grace-menu/`（本轮改动前的既有文件副本）。

---

## 追加：Harness 2026-10-13 20:56 · §2.3 赐福内战灰闭环 已验证 + §2.6 每段 FP 核对完成（含自我更正）

### §2.6 待查项核对结果：**不成立**，且已更正我此前的错误记录

文档 §2.6 留的待查项是 "`cursed_blood_slice` 首次追击 stageIndex 与 costForStage 的对应关系，
不要沿用错误索引又在 UI 单独修数字"。核对方式刻意是**驱动真实状态机**
（`commit → enterStage(ACTIVE) → openFollowupWindow → commit`），而不是给 `costForStage` 传参——
传参等于把待查的前提当成已知。

结论：**进入 ACTIVE 时 `stageIndex` 已经 +1**（`SkillCastState.enterStage` 只在 ACTIVE 分支自增），
所以首次追击走的是 `costForStage(1)` = `fpCostFollowup`，**没有多收首段价，不需要给索引 +1**。
进一步地，`costForStage` 对任何 `stageIndex > 0` 都返回 `fpCostFollowup`，
所以 **"+1" 在这张两档价表上是空操作**，不可能改变金额——这也是那条待查项容易被误判的原因。

**我此前在进度台账里写的"缺 `+1` → `cursed_blood_slice` 首次追击多收 4 FP"是错的，已改掉那一行**
（规则：改旧结论就改旧行，不写"更正"留矛盾）。

**核对时查出一条真问题（不是收费 bug，本轮不改只备案）**：`destined_death` / `waterfowl_dance`
配置 `maxStages=3` 但 `followupWindowTicks=0` → `hasFollowup()` 为假 → **第二段永远买不到**，
`maxStages` 是死配置；它们的"三段体感"来自运行时内部的 `burst` 通道
（`activeTick` 里 `burst = min(2, elapsed*3/duration)`，在长 ACTIVE 窗口里连打三下）。
另外记下两条内容限制：①价格表只有两档，三段以上**无法**给第三段单独定价；
②`fpCostFollowup=0` 的"回落首段价"分支在现有 8 件里**走不到**。

新增 3 个纯逻辑用例把它们钉死（`SkillCommitBoundaryTest` 共 12 例）。

### §2.3 闭环：新增/改动

- `GraceMenuStatePayload` 加 `heldToken`（武器身份）与 `Ash.compatible`（**逐件兼容性预览**，服务端算）；
  `GraceMenuActionPayload` 加 `heldToken`；`AshBindings.checkHeld` 提供只读兼容性判定（与 bind/replace 同源，
  避免"显示可装、点了被拒"）。
- **防误改后来拿起的武器**：`heldToken = 槽位 + 物品 id`（**刻意不含**已绑战灰与强化等级，
  否则一次成功绑定就会让自己的标记过期）。只对"会写主手物品"的操作核对；不一致就拒绝并刷新。
- `GraceMenu` 持有 `notice`（周期刷新沿用，免得冲掉玩家刚看到的失败原因）；
  `GraceService` 每 10 tick 给"菜单开着"的玩家推快照；`GraceScreen` 只在"构成变化"时重建控件。

### 验证（真实日志，非推断）

`runClient -PgracePrototype -PgraceMenu` 退出码 0，菜单夹具 **16 项断言全过**：

```
GRACE_MENU_SCREEN_PASS: nodes=2 ashes=3 fp=5/100 held=（空手） rejection=当前主手武器不支持战灰
GRACE_MENU_OWNERSHIP_PASS: 未解锁战灰被拒，notice=尚未获得此战灰
GRACE_MENU_REST_PASS / FORGERY_REJECTED / TRAVEL_REJECTED / RANGE_REJECTED / TRAVEL_DISPATCH_PASS
GRACE_MENU_BIND_PASS: 已解锁+兼容的战灰经菜单装配成功，notice=已装配战灰：eldencombat:spinning_slash
GRACE_MENU_DUPLICATE_PASS: 重复装配被当作可读空操作，notice=无法装配：这件武器已经绑定了战灰，请用替换
GRACE_MENU_HELD_SWITCH_REJECTED: 换手后旧 token 被拒且未误改新武器，notice=主手武器已变化（换手或换槽），已刷新，请重试
GRACE_MENU_UNBIND_PASS: 菜单卸除成功，主手不再绑战灰
GRACE_MENU_COMBAT_REJECTED: 战斗期间休息被拒、fp 仍为 5
GRACE_MENU_COMPLETE
```

`test` 退出码 0（22 类 / **160 用例 / 0 失败 / 1 跳过**）；`runClient -PashSmoke` 与
`runServer -PserverSmoke` 均退出码 0（无回归）。证据：`docs/combat-evidence/grace-menu-binding-and-fp-audit-20261013.md`。

### 仍未做（不得算作完成）

①**玩法内解锁途径仍然没有**——本轮的"成功装配"是用管理命令搭台的，日志里已标明
"管理脚手架，非玩法来源"，**不能**据此声称玩法闭环通过；
②真人鼠标（夹具走 `onPress()` 回调）；
③只有曲剑 × `spinning_slash` 走通完整装配链，另两件未逐件实跑；
④多人场景（并发点击、双玩家隔离）未跑。

---

## 追加：Harness 2026-10-13 21:04 · §2.1 玩法内奖励来源 + §2.5 组件保留 —— §2 六条全部到达「已验证」

### 接上玩法内解锁来源（§2.1 前半句"奖励来源留给现有探索/奖励能力"）

- `core/AshRewardTable`（**纯逻辑，可单测**）：一个来源 → 若干战灰；`ashesFor` 规范化输入、未知来源返回空、不抛异常。
- `server/skills/AshRewards`（`@EventBusSubscriber`，方法签名里没有缺失依赖的类型，安全）：
  只认**玩家击杀**、只发给击杀者本人、非玩家来源不解锁、**幂等**（`grant` 返回 false 就当没发生）。
- 当前表：击杀 `eldencombat:margit` → 解锁 `eldencombat:stamp_upward_cut`。加一个来源就是加一行。
- 单测硬不变量：**奖励的战灰必须真的在目录里**——否则玩家拿到了也装不上，等于假奖励。

### 整条玩法链第一次用真玩法跑通（无管理命令）

```
ASH_REWARD_GRANTED: entity=eldencombat:margit player=380df991-… ash=eldencombat:stamp_upward_cut
GRACE_MENU_REWARD_UNLOCK: 击杀玛尔基特解锁 stamp_upward_cut 并换上大剑（玩法内来源，非管理命令）
GRACE_MENU_REOPEN_AFTER_COMBAT: 战斗窗口过后重开菜单
GRACE_MENU_BIND_PASS: 玩法内解锁的战灰经菜单装配成功，notice=已装配战灰：eldencombat:stamp_upward_cut
```

上一轮那条"成功装配靠管理命令搭台"的保留意见**本轮作废**：赐福菜单夹具已改为**击杀解锁**，
不再调用任何管理命令（只在夹具前置里清空上一次运行留下的解锁/背包，并在注释与日志里标明那是**测试前置**）。

### §2.5 已验证（绑定不动武器、也不许覆盖锁定战技）

```
ASH_SMOKE_LOCKED_PASS: 追忆武器拒绝覆盖战技
ASH_SMOKE_COMPONENT_PASS: 绑定后 强化+2 / 质变HEAVY / 自定义名 全部保留
```

顺带一条旁证：同一件探针武器带 +2/HEAVY 之后，同一发普通战灰伤害从 16.728 变成 **18.4677**，
说明武器成长倍率确实作用在普通战灰上。

### 夹具先撞上、再被写成断言的真实规则

**击杀 Boss 会让击杀者进入 200 tick 的"战斗期间"**（`GraceService.damage` 对
"伤害来源实体是玩家"也计时），因此刚打完 Boss 时**赐福菜单会被 `stillValid` 自动关掉**。
夹具现在先断言"战斗期间菜单确实已关闭"，等窗口过去再重开——这条规则因此多了一层证据。

### 验证

`test` 退出码 0（23 类 / **164 用例 / 0 失败 / 1 跳过**）；`runClient -PashSmoke`、
`runClient -PgracePrototype -PgraceMenu`（17 项断言）、`runServer -PserverSmoke` 全部退出码 0。
证据：`docs/combat-evidence/ash-reward-source-20261013.md`。

### 仍未做（不得算作完成）

①**内容覆盖不足**：目录 3 件战灰里只有 `stamp_upward_cut` 有玩法内来源，另两件**没有任何可获得途径**
（不影响"能被装配"，但不能说战灰系统内容完整）；②真人鼠标未验（夹具走 `onPress()` 回调）；
③另两件的判定形状没打过真实目标；④多人击杀归属（两玩家同时输出）未实测；⑤平衡值仍是草稿。

### 备份

`docs/code-backups/20261013-grace-menu/`（既有文件改动前副本）；本轮新增文件不覆盖既有文件。

---

## §3 护符闭环：实现落点与验证（2026-10-13 22:00）

### §3.1 装备权威：Curios 不可行 → 自制多槽容器（只此一条权威）

已查实：本工程**没有** Curios jar，`modpack/mods.lock.json` 无 curios 条目，构建是 `--offline`
→ 凭空引用未安装版本的 API 会直接破坏编译。因此按任务书允许的第二条路走：

- 权威 = 服务端附件 `eldencombat:talisman_slots`（`server/TalismanLoadout`），容器里放**完整 ItemStack**
  （`ItemStack.OPTIONAL_CODEC.listOf()`，`copyOnDeath()`）；旧字符串附件 `eldencombat:talisman` 降级为**只读迁移源**。
- 不新增第二条权威：没有 Curios、没有第二份"已装备护符"记录；所有数值出口都从这一份容器读。
- 槽数/重复/同系列/上下限集中在 `core/TalismanRules`（纯逻辑、可单测）+ `config/eldencombat-talisman.json`；
  **默认值与旧行为一致**（单槽、禁止重复），新增规则标为可配置。

### §3.2 一次性迁移：幂等、不重复返还、不静默清掉

`Talismans.migrateLegacy(player)`：裸路径补全为 `eldencombat:<path>`；成功迁移后清空旧记录并打 INFO
`TALISMAN_MIGRATED:`。三条保守规则（宁可留可疑记录也不吃玩家东西）：

1. 非法 ID / 物品不存在 → 旧记录**原样保留** + WARN；
2. 容器里已经有护符 → 只清旧记录 + WARN（避免重复返还）；
3. 重复调用是空操作（旧记录已清）。

### §3.3 出口全部改读容器 + 聚合

五个出口 `damageMultiplier / damageTakenMultiplier / staminaRegenMultiplier / healBonus / maxFpBonus`
全部读 `core/TalismanAggregate.of(specs, rules)`：**先求和、最后夹一次**（受伤减免夹到
`[-1, maxDamageReduction]`，精力/伤害夹到 `[-0.9, max]`）。单槽默认值下与旧实现逐位等价。

### §3.4 刷新与同步：八条路径

装备 / 卸下 / 替换 / 背包满 / 死亡 / 重登 / 切维度 / `reload`。要点：

- `refresh(player)` 顺序是「先按规则对齐槽位 → 再聚合 → 再写 FP 上限 → 再同步」，
  槽数下调时超出的物品**退回背包**（背包满才落地并在聊天里说明，**不销毁**）；
- `PlayerFpState.setBonusMaxFp` 只向下夹当前 FP → **反复换护符不能补满 FP**（夹具实测 100→130→100，当前恒 100）；
- 死亡/切维度走 `SkillServerEvents.onPlayerClone`（LOWEST 优先级，晚于 NeoForge 复制附件）重算；
  重登走 `onPlayerLoggedIn` 重算 + 下发展示数据；
- `reload` 走 `EldenCombat.reloadConfig()` → `TalismanRules.reload` + `Talismans.refreshAll()`。

### §3.5 客户端展示数据：只吃同步 payload

`network/TalismanDataPayload`（登录/`reload` 下发）→ `client/TalismanClientData`（唯一客户端缓存）
→ `TalismanItem.appendHoverText`。战斗效果仍然只在服务端算。反证夹具：服务端手发一份"改过名"的展示数据，
客户端随即改名，而**服务端权威表未被改写**；别的 namespace 同名护符查不到本模组条目。

### §3.6 命名空间：比较/落盘/展示统一走完整 ID

`core/TalismanIds`：裸路径 → `eldencombat:<path>`，完整 ID 原样保留，非法 → 空串（不抛异常）。
`specs` 以完整 ID 为键 → 不同 namespace 互不覆盖；旧 `eldencombat` 裸路径字符串在迁移里兼容。

### 验证（命令与退出码）

```
.\gradlew.bat compileJava                                  --offline --no-daemon   → 0
.\gradlew.bat test                                          --offline --no-daemon   → 0（26 类 / 178 用例 / 0 失败 / 1 跳过）
.\gradlew.bat runClient -PclientSmoke                       --offline --no-daemon   → 0（阶段一，含护符全相位）
.\gradlew.bat runClient -PclientSmoke -PclientSmokeReload   --offline --no-daemon   → 0（阶段二，真实关服重启）
.\gradlew.bat runClient -PashSmoke                          --offline --no-daemon   → 0
.\gradlew.bat runClient -PgracePrototype -PgraceMenu        --offline --no-daemon   → 0
.\gradlew.bat runServer -PserverSmoke                       --offline --no-daemon   → 0（428 项共享检查）
```

逐字日志与断言清单：`docs/combat-evidence/talisman-container-20261013.md`。
被判定的关键行：`TALISMAN_MIGRATE_PASS`、`TALISMAN_MIGRATE_UNKNOWN_PASS`、`TALISMAN_EQUIP_PASS`、
`TALISMAN_MULTISLOT_PASS`、`TALISMAN_SLOT_SHRINK_PASS`、`TALISMAN_FP_PASS`、`TALISMAN_DEATH_PASS`、
`TALISMAN_CLIENT_SYNC_PASS`、`TALISMAN_INVENTORY_FULL_PASS`、`TALISMAN_RESTART_PASS`、
`TALISMAN_RESTART_DISPLAY_PASS`、`CLIENT_PERSISTENCE_COMPLETE`。

### 仍未做（不得算作完成）

- 真人鼠标/键盘未验（夹具走原版控件 `onPress()` 回调与 `KeyMapping.click`）；
- 多人（双玩家权限隔离、同时请求、离线回调）未验，专用服务器夹具是单玩家无头跑；
- 现有护符数据里没有任何一件声明 `series` → 同系列规则目前约束不到东西；
- 护符上限与战灰数值仍是待平衡草稿；
- 玩法内容覆盖：3 件普通战灰里只有 `stamp_upward_cut` 能在玩法内获得。

### 本轮修掉的两个夹具缺陷（影响判定，必须记录）

1. **夹具 step 与服务端 tick 解耦**：`AshSmokeHarness` / `GraceClientPrototypeHarness` 的 step 是**客户端 tick**，
   被验证的动作在**服务端 tick** 上推进；铺 315 块石头、200 tick 战斗窗口都会让服务端落后，
   固定 step 断言因此误报（实测 "施放应已结束"、"matched buttons=0"）。现改为**轮询真实条件**（收招 / 菜单留住 /
   按钮出现 / 回执到达），超时上限内仍照原样失败。
2. **夹具世界被自己弄脏**：超距离用例把玩家扔到 x=200 半空，摔伤写进存档，几次运行后玩家 0 血开局、
   客户端停在死亡界面。现在开局复位玩家状态（必要时走**原版重生路径**让客户端退出死亡界面），
   超距离用例返回时清摔落距离并补满，收尾补满存盘一次。

### 备份

`docs/code-backups/20261013-talisman-container/`（改前副本 8 份：`Talismans`、`TalismanSpec`、`ModAttachments`、
`ModPayloads`、`TalismanEquipment`、`TalismanItem`、`ClientPayloadTrampoline`、`SkillServerEvents`）。
本轮新增文件不覆盖既有文件。
## 最新表现任务索引（2026-10-13）

最新分工：依 `docs/ART-VFX-WORK-ORDER-20261013.md`，美术部门制作三个样板，独占 `art/vfx-2d/`，交付入口为 `docs/ART-VFX-HANDOFF-20261013.md`。战斗收到真实素材后负责 Kind 路由、朝向、图集、资源同步及实机接入；Harness 不再承担贴图制作，也不得覆盖美术素材。本轮仅更新交接，未启动 Harness、构建或测试。

后续表现工作先读 docs/VFX-REDESIGN-PLAN-20261013.md 和 docs/DEEPSEEK-COMBAT-FRAMEWORK-HANDOFF-20261013.md 的“最新表现交接：三个 2D 样板”。已核对水鸟、命定之死、黄金波动的不同真实入口、资源字段、阶段和朝向限制。采用最新 2D 方向，旧体素规范不覆盖它；三个样板经用户实机短片确认后再扩展。当前只完成交接，效果未重做，未启动 Harness。

## 最新代码分工（2026-10-14）

用户再次明确：Codex负责框架设计，DSH负责具体实现。当前八战技特效接续入口是 docs/DSH-VFX-IMPLEMENTATION-CONTRACT-20261014.md，包含实际已有代码、接口合同、夹具问题和待验收项；不再沿用“战斗部门自行完成剩余实现”的旧安排。三系统内容不因这份特效工单自动扩展或重跑测试。
