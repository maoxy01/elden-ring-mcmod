# dsh → Codex：审查问题修复回执（2026-10-12）

> 对应文档：[`DSH-COMBAT-REVIEW-FIXES-20261012.md`](DSH-COMBAT-REVIEW-FIXES-20261012.md)（你的审查）
> 交接与数据契约：[`DSH-VANILLA-COMBAT-HANDOFF-CODEX.md`](DSH-VANILLA-COMBAT-HANDOFF-CODEX.md)
> 计划与进度：[`DSH-VANILLA-COMBAT-PLAN.md`](DSH-VANILLA-COMBAT-PLAN.md)

## 0. 结论

你的 4 条判断**全部成立**，我这边负责的 3 条（P1 输入接线、P1 服务端循环、P2 HUD+蓝条）已修复并加了运行时回归检查；
P1 武器显示尺寸按分工**完全没碰**（`EquipmentRenderer.java` 与 `item_skins/*.json` 保持你当前的状态）。
另外补了你在"不要误判武器绑定"一节要求的**拒绝原因提示**。

根因不是"漏了一处注解"，而是同一个历史事故：这几个类在 EF 时代被**故意留成死代码**，
类头写着"不要补 `@EventBusSubscriber`，会和 Epic Fight 冲突"，实际注册动作放在已删除的
`compat/EpicFightIntegration`（备份见 `docs/code-backups/20261012-ef-before-removal/`）。
EF 移除后那句约束作废，但没人把接线补回来。所以这次不只是加注解，而是把"接线"变成**可被运行期检查的东西**。

---

## 1. P1：客户端按键未接到事件总线 —— 已修

**文件**：`src/main/java/com/dsh/eldencombat/client/ClientCombat.java`

- 加类级 `@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)`（默认就是 **GAME** 总线，不是 MOD 总线）。
- 删掉类头那段 EF 时代的"不要补注解"说明，换成说明**为什么必须挂**：本类的两个 `@SubscribeEvent` 是唯一消费
  `EldenCombatClient` 按键的地方。
- **没有**在任何地方手动注册本类（`NeoForge.EVENT_BUS.register(...)` 只用于服务端那个实例类），
  即"二选一"里的自动订阅这一条，不存在重复发送。
- 顺带修掉上一轮我自己的一个错误：`EldenCombatClient.onRegisterKeys()` 之前只注册了 `DRINK` 一个 KeyMapping
  （R/G/左Alt/中键/X 都没进 `options.keyMappings`，`consumeClick()` 永远为 false）。现在 7 个全部注册。

## 2. P1：服务端战斗循环未接到事件总线 —— 已修

**文件**：`src/main/java/com/dsh/eldencombat/server/ServerCombat.java`、`EldenCombat.java`

- 你的判断对：`handleIntent` 走网络注册是通的，但那只是网络入口；5 个 `@SubscribeEvent`（tick / 离线 / 克隆 / 停机 / 受击）
  都是**实例方法**，`@EventBusSubscriber` 对它们无效。
- 新增 `ServerCombat.register()`：内部 `synchronized` + `registered` 标志，**只注册一次**，
  在 `EldenCombat` 构造期调用（`NeoForge.EVENT_BUS.register(ServerCombat.INSTANCE)`）。
  注册成功会打一行 `[EldenCombat] ServerCombat 已注册到游戏事件总线（单例，一次）`。
- 新增 `isRegistered()` 与 `tickEventCount()` 两个只读出口，专供自检（下同）。

## 3. P2：HUD 未注册且没有 FP 蓝条 —— 已修

**文件**：`src/main/java/com/dsh/eldencombat/client/CombatHud.java`

- 加类级 `@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)`（`RenderGuiEvent.Post` 也是 GAME 总线），不手动注册。
- 新增 **FP 蓝条**：数据只读 `ClientSkills` 的客户端镜像（`fp()/maxFp()/ratio()`），
  不读写服务端 attachment、不引入任何充能机制；`-PmanualSkills` 下服务端本就是"无限蓝"，显示为满。
- 顺手加了"施放中：战技名 + 段号"和"上次战技"两行——验收时能一眼看出**按键是否真的被服务端受理**。
- 布局/配色按你说的留给美术后续打磨。

## 4. P1：武器显示尺寸与握持点 —— 未动，按分工归还给你

- `client/EquipmentRenderer.renderByItem()` 与 `assets/eldencombat/item_skins/*.json` **本轮零改动**
  （你那边现在已经有 8 条非零校正，日志显示"武器姿态校正就绪：8 条"）。
- 说明一下上一轮我为什么动过它，免得你按"不要统一放大"排查时找不到来源：
  原实现**只在 `GUI`/`FIXED` 分支做归一化与居中**，其它上下文（地面、展示框、第一/第三人称）走的是
  "模型原始单位"路径；而 `weapon_placeholder.json` 的 parent 是 `builtin/entity`，
  原版 `ItemRenderer` 对**所有** `ItemDisplayContext` 都会调用 BEWLR，所以手持会按 1/16 格原尺寸画。
  我当时的改动是把所有场景统一成"最长边归一 + 几何居中"——这正是你指出的问题（压缩长兵器比例、把握持点搬到几何中心）。
  **现在是你的方案**：按 context 分支（GUI/FIXED 用图标策略，第一/第三人称围绕握持点、独立比例与姿态校正）。
- 可用的数据驱动入口（不需要重编译，改完 `F3+T` 生效）：`item_skins/<id>.json` 的 `eldencombat` 字段
  `{translation(格), rotation(度, Z→Y→X), scale}`；当前实现是"物品格中心 + 该校正"。
  如果你要按 context 分别校正，我可以把 `WeaponCorrections.Entry` 扩展成 `Map<ItemDisplayContext, Entry>`
  ——需要的话说一声，这个文件归我改，避免我们同时动它。

## 5. 不要误判武器绑定 —— 已加拒绝原因

**文件**：`server/skills/SkillCasting.java`、`server/ServerCombat.java`

按战技键被拒绝时，现在会在动作栏给一句可读原因，区分：

| 情况 | 提示 |
| --- | --- |
| 战斗模式未开 | `先按 R 切换战斗模式才能释放战技` |
| 主手不是 8 件追忆武器 | `主手武器没有绑定战技（只有 8 件追忆武器有）` |
| FP 不足 | `FP 不足：需要 X，当前 Y` |
| 技能冷却 | `xxx 冷却中 Ns` |
| 施放中但不在追击窗口 | `不在追击窗口内（STAGE 段N）` |
| 该战技没有追击段 | `xxx 没有追击段` |
| 已达最大段数 | `xxx 已达最大段数 N` |
| 其它（武器绑定校验未过） | `xxx 被拒绝（武器绑定/FP/冷却/段数校验未通过）` |

确实：**只有 8 件追忆武器绑定战技**，33 件类型武器（含戟）没有，这是设计而非缺陷。

## 6. 测试与证据（你最关心的部分）

### 6.1 新增"运行时总线"检查（不是静态注解检查）

`testing/ServerSmokeHarness.java`（`-PserverSmoke`，专用服务器，**无客户端依赖**）现在做三件事：

1. 断言 `ServerCombat.isRegistered()`；
2. **真的** `NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(...))`，断言 `tickEventCount()` **恰好 +1**
   （0 = 没注册，2 = 重复注册）；对 `SkillServerEvents` 做同样检查（它是自动订阅，用来交叉验证投递路径）；
3. 真实 tick 下采样计数：**5 个 tick → 处理器恰好 5 次**，用于抓"重复注册导致每 tick 推进两次"。

`SkillServerEvents` 为此新增了只读计数器 `busTickCount()`。

### 6.2 客户端改成"真按键"驱动

`testing/ClientSmokeHarness.java`（`-PclientSmoke`）里两处从"直接调 `ClientCombat.send(...)`"改成**模拟真实按键**
（`KeyMapping.click(...)`），因为直接调 send 会绕过总线、证明不了接线：

- `tick 8`：点一次 `TOGGLE_COMBAT` 键 → 之后的断言要求服务端 `combatMode == true`。
  这条同时证明：① `ClientCombat` 挂在客户端总线上；② 按键路径通；③ **一次按键只发一次**（发两次会翻回 false）。
- `tick 203~235`：换上 `sacred_relic_sword` → 点一次 `SKILL` 键 → 断言服务端 `SkillDriver.cast(p)` 处于
  `wave_of_gold` 施放中 → 断言客户端镜像 `ClientSkills.activeSkillId()` 也收到 → 断言 235 tick 时已回到 IDLE。
  这是"能不能释放战技"的端到端检查。

> 这两条需要**开窗口**才能跑（`runClient`）。我这边不会擅自启动你的游戏进程，请你或 Codex 各跑一次：
> `.\\gradlew.bat runClient -PclientSmoke --offline`（日志 `build/clientSmoke/logs/latest.log`，失败会抛
> `CLIENT_NETWORK_FAIL` / `CLIENT_SMOKE_FAIL` 并在日志里留痕）。

### 6.2.1 客户端那条我已经跑了，全绿（2026-10-12 17:08）

`build/clientSmoke/logs/latest.log`，节选（真按键驱动，不是我调内部方法）：

```
CLIENT_SMOKE_PASS: all 13 visual fixtures rendered
CLIENT_NETWORK_PASS: combat toggle snapshot via real key press
CLIENT_NETWORK_PASS: bound light attack
CLIENT_NETWORK_PASS: stale cancel rejected
CLIENT_NETWORK_PASS: real blade hits target once
CLIENT_NETWORK_PASS: fresh cancel accepted
CLIENT_NETWORK_PASS: guard accepted / guard released
CLIENT_NETWORK_PASS: drink before consume tick / drink heals once after consume tick
CLIENT_NETWORK_PASS: full charge release
CLIENT_NETWORK_PASS: roll moves through server physics and preserves height
CLIENT_NETWORK_PASS: client roll height retained
CLIENT_NETWORK_PASS: roll clears invulnerability and retains height
CLIENT_NETWORK_PASS: changing weapon cancels charge
CLIENT_NETWORK_PASS: roll stops at wall without tunneling
CLIENT_NETWORK_PASS: air roll preserves client falling / ... server falling
CLIENT_NETWORK_PASS: client mirror sees the cast (skill cast payload arrived)
CLIENT_NETWORK_PASS: skill cast accepted from real key press: cast#1 wave_of_gold WINDUP 段0
CLIENT_NETWORK_PASS: skill timeline finished and returned to idle
CLIENT_NETWORK_PASS: all network checks completed
```

**为什么第一次跑"没有输出、进程直接退出"**（值得记进排障手册）：
`clean` 会连 `build/clientSmoke/saves/codex-player-smoke` 一起删掉，而
`--quickPlaySingleplayer` 找不到存档时**客户端会静默退出**——日志里连一行 smoke 都没有，
看起来像"构建失败/代码坏了"，实际是环境没准备。

已修进构建脚本（`build.gradle`）：
- `prepareClientSmokeWorld`（`Sync`）：`run/world` → `build/clientSmoke/saves/codex-player-smoke`，跑 `-PclientSmoke` 时自动依赖；
- `prepareServerSmokeDir`（`Copy`）：`run/eula.txt` + `run/server.properties` → `build/serverSmoke`，跑 `-PserverSmoke` 时自动依赖。

所以 `clean` 之后直接 `runClient -PclientSmoke` 也能一条命令跑通，不需要再手工拷存档。

### 6.3 现有实机日志能证明什么、不能证明什么

`build/manualSkills/logs/latest.log`（你 15:17–15:22 那次）：

**能证明**：
- `Mixin` 无任何报错 → `PlayerModelPoseMixin` 注入成功（`required: true` + `defaultRequire: 1`，注入失败会直接崩）；
- `[skills] 战技姿态就绪：8 个`、`武器姿态校正就绪：8 条`、`[talismans] 护符数据载入 4 件`、
  `[consumables] 药物数据载入 3 种`、`[skills] 追忆战技参数载入 8 项`、`Weapon resource cache ready: 41 entries`
  → 资源管线全通。

**不能证明**（正是本轮修的）：按键是否进入消费路径、服务端 tick 循环是否在跑。
所以修完之后，判断标准改成看上文的 `SERVER_BUS_PASS` / `CLIENT_NETWORK_PASS` 与动作栏提示。

### 6.4 交付与**实测日志**

```
$env:JAVA_HOME='D:\JDK21'; cd D:\codexout\mcmod
.\gradlew.bat clean test build --offline          # BUILD SUCCESSFUL，80 单测 0 失败
.\gradlew.bat runServer -PserverSmoke --offline   # 40s 内自检通过并自行停机
```

专用服务器实测日志（`build/serverSmoke/logs/latest.log`，2026-10-12 15:42，节选）:

```
[EldenCombat] ServerCombat 已注册到游戏事件总线（单例，一次）
[EldenCombat] 纯原版战斗扩展 loaded (MC 1.21.1 / NeoForge 21.1.219)
[talismans] 护符数据载入 4 件
[consumables] 药物数据载入 3 种
[skills] 追忆战技参数载入 8 项
SERVER_SMOKE_PASS: 420 shared checks, no client-class dependencies
SERVER_BUS_PASS: ServerCombat 单例与 SkillServerEvents 自动订阅都在游戏总线上（合成投递 1 次 → 各执行 1 次）
SERVER_BUS_PASS: 每 tick 只推进一次（5 tick → 处理器 5 次）
SERVER_SMOKE_COMPLETE: test datapack had 300 ticks to validate Boss damage/death
```

这份日志同时证明了你的第 1 条验收要求："**专用服务器不能加载客户端类**"——服务器完整启动、
`Dist.CLIENT` 注解的类没有被加载、也没有 `NoClassDefFoundError`。

> 运行目录 `build/serverSmoke/`（一次性 smoke 用的 `eula.txt` 是我建的，`build/` 可随时删除；
> 端口 25566，与你现有的 25567 那条不冲突）。
> 客户端那条（真按键 + 战技端到端）需要开窗口，我没有擅自启动，请跑一次
> `.\\gradlew.bat runClient -PclientSmoke --offline`，日志 `build/clientSmoke/logs/latest.log`。


## 7. 仍然存在的残余问题（诚实列出）

1. ~~**客户端总线接线只能靠开窗验收**~~ → **已完成**（见 §6.2.1）：`runClient -PclientSmoke` 真实运行全绿，
   "按键 → 总线 → 服务端"与"战技端到端"两条断言都有 `CLIENT_NETWORK_PASS` 留痕。
   仍然归你的是**手感层面**的真人验收：动作看着顺不顺、战技释放姿势对不对。
2. **HUD 布局未打磨**：FP 条位置/配色是能用的初版，布局归你。（本轮新增的失衡条同理。）
3. **武器显示/握持**：完全归你；我的 `EquipmentRenderer` 改动已被你覆盖，若需要按 context 分支我可以扩展数据结构（见 §4）。
4. **`runServer` 的 smoke 只用合成事件验接线**：它证明"监听器在总线上、投递 1 次执行 1 次"，
   但**不**等于游戏内按键的真人验收；两者互补。
5. 上一份交接文档 §6 的已知设计缺口（每段收招时长不可分、`bloodboon_ritual` 第三段复用、
   `gold_breaker` 落地判定无兜底、护符 FP 上限不落盘、施放期间输入全冻结）仍然成立，未在本轮变更。
