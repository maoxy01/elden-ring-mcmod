# dsh → 《成长路径与手感收口方案》回执与施工计划

日期：2026-10-12 · 对应文档：`docs/DSH-PROGRESSION-AND-FEEL-PLAN-20261012.md`（下称"方案"）
项目：`D:\codexout\mcmod` · MC 1.21.1 / NeoForge 21.1.219 / Java 21 / **纯原版战斗，Epic Fight 已删除**

---

## 0. 结论

方案整体采纳，四项拍板按推荐执行。但**三条前提要更正**，其中一条会显著改变实现方式（§1）。
施工顺序调整为 **§6（已交付）→ §4 → §5 → §2**：

| 顺序 | 章节 | 状态 | 为什么这个次序 |
| --- | --- | --- | --- |
| 1 | §6 削韧 payoff | **已交付**（见 §3） | 最小闭环，能立刻把"追一条看不见的条"变成"开一个能看见的输出窗口" |
| 2 | §4 跃起落地统一兜底 | **已交付**（见 §3B） | 稳定性优先于手感，且是纯服务端逻辑，可单测 |
| 3 | §5 武器差异三张表 | 待做 | 它是 §2 的**数据前置**（基础伤害/补正档位与 reach/削韧/重量是同一张武器定义表） |
| 4 | §2 锻造与质变 | 待做 | 最大缺口，但依赖 §5 的数据层 + 补正接口契约 |

方案自己的排序是 §2→§5→§4→§3→§6，把"内容"放前、"调优"放后——那条原则我同意，
但**本轮的切入点是 §6 而不是 §2**，原因只有一个：§2 是数天量级的内容系统，而 §6/§4 是当天能闭环、
且不依赖任何未决设计的稳定性/反馈修补。先清掉"会卡死"的，再铺内容。

---

## 1. 三条必须更正的前提

### 1.1 方案 §0 的假设表已经没有"若"了：EF 已删除

方案写"按 EF 仍在写"，并给出"若不成立（已切纯原版）"的降级列。**事实是已经切纯原版**，
EF 依赖、EF Skill/WEAPON_INNATE、EF 动画 Phase、EF 碰撞体都已从代码里移除。
所以 §0 那张表直接取右列，`EF-WEAPONS-IMPLEMENTATION-PLAN.md` 这条线作废。

但方案对工作量的估计偏悲观：**§3 的时序改动不需要翻倍**。它依赖的四样东西现在都是自研的、
且已经是服务端权威的：

| 方案里担心的 | 现状 |
| --- | --- |
| FP / 能量 | `server/skills/PlayerFpState`（含护符加成），服务端权威 + 同步给客户端只读镜像 |
| 冷却 | `RemembranceSkillSpec.cooldownTicks` |
| 命中集合（同段只结算一次） | `SkillCastState.hitChannels`（按"段通道 + 实体 id"去重） |
| 阶段时间线 | `SkillDriver` + `SkillCastState`：`IDLE→WINDUP→ACTIVE→FOLLOWUP_WINDOW/RECOVERY→IDLE`，全部服务端 tick |

结论：§3 是**改参数 + 拆收招 + 加取消白名单**，不是重写一遍战斗底座。

### 1.2 §5.1 的 reach 不要自建攻击判定——原版有现成属性

方案把 reach 当成"需要自建判定"的东西。MC 1.21.1 有原版属性：

- `Attributes.ENTITY_INTERACTION_RANGE`（默认 3.0，反编译源码 `Attributes.java:37`）
- `Attributes.BLOCK_INTERACTION_RANGE`（默认 4.5）

做法：把 reach 写成**武器物品的默认属性修饰符**（`Item.Properties.attributes(...)` / 数据包
`attribute_modifiers`）。收益：

1. 不用 mixin、不用自建 reach 系统，判定仍走原版 `Player.attack` 的实体搜索半径；
2. JEI / 物品 tooltip 会自动显示，玩家能自己看出来"这把矛更长"；
3. 与 §5.3 的重量→攻速**同一套机制**（`Attributes.ATTACK_SPEED`），一张表两处收益；
4. 我们自研的扫掠判定（`HitResolver`）只负责战技与特效，不需要为普攻引入第二套半径。

代价只有一个：原版攻击冷却会跟着攻速变（重武器挥得慢）。**这正是方案想要的手感**，不是副作用。

> 耐力系数（§5.3 第二条）不在原版属性里，仍需在我们自己的 `CombatState` 计算里乘，
> 但它只是一个乘数，不构成架构成本。

### 1.3 §3.5 "顿帧必须客户端渲染冻结" 可做，但要注意叠加

方案这条是对的，且我们已经有现成通道：`SkillCastPayload`（服务端 → 追踪玩家的广播）里
加一个 `hitstopTicks` 字段，客户端在渲染侧冻结自己与目标的表现，服务端 tick 照常推进。
这样不会出现"看起来躲过了却被判中"。

要注意的是它和 §3.3（蓄势压缩到 4–6 tick）叠加：**4 tick 蓄势 + 3 tick 顿帧**会让 ACTIVE 段
实际观感比数字更长，所以这两项必须一起调，不能分开验收。

---

## 2. 对方案 §9 四项拍板的回答

| # | 决定 | dsh 结论 | 补充说明 |
| --- | --- | --- | --- |
| 1 | 强化档位 +10（类型）/ +5（追忆），不照搬 +25/+10 | **采纳** | 曲线按 `t = L / Lmax` 归一化后再套同一公式，两张表不会分叉；实现时直接算公式，不抄方案 §2.3 的趋势表（那张表自身有笔误，方案自己也注明了） |
| 2 | 不引入负重系统，重量只映射攻速与耐力 | **采纳** | 攻速直接用原版 `attack_speed`（顺带获得原版冷却手感）；耐力系数在我们自己的计算里乘 |
| 3 | 失衡 payoff 用"易伤 +50%"，不做处决 | **采纳并已落地** | 多人：崩解倍率不变（"敌人变脆"不是"玩家变强"），**失衡上限按 `×(1 + 0.5×(n-1))` 缩放**，已按此实现 |
| 4 | 补正系统（五项属性）是否与本方案一起做 | **建议做，分两步** | 砍掉补正 → 8 种质变里 6 种失去存在理由，§2.4 那个 0.85–0.95 的 P 值窄带也失去意义（质变变成纯惩罚）。**第一步**：把接口契约定死（档位→每点系数、软化函数、`WeaponDefinition` 的字段），属性值先用 `Attachment` + 命令式加点（`/eldencombat attribute`）跑通数值；**第二步**：加点 UI（归 Codex 的美术/交互） |

第 4 条的本轮落地形态：**属性不缺系统、缺 UI**。这样 §2 的质变与补正可以立刻做出完整的数值闭环，
又不用等 UI。

---

## 3. 已交付：§6 削韧 payoff

### 3.1 实现

| 文件 | 改动 |
| --- | --- |
| `core/PoiseMath.java`（新） | 纯逻辑：上限缩放 `maxForPlayers`、窗口 `breakTicks`、易伤 `damageTakenMultiplier` |
| `core/CombatConfig.java` | `Poise` 新增 `brokenTicks=40`（2.0s）、`brokenDamageMultiplier=1.5`、`multiplayerScalePerExtraPlayer=0.5`；新增 `poiseMaxFor` / `poiseBreakTicks` / `poiseDamageTakenMultiplier`；`normalize()` 补三项修补 |
| `entity/MargitEntity.java` | 新增同步字段 `DATA_BROKEN`；崩解窗口 = `max(硬直动作时长, 40)`；进入崩解开描边、退出/死亡关描边；`addStagger` 用**按人数缩放后**的上限；新增 `isBroken()` / `damageTakenMultiplier()` / `poiseMax()` |
| `server/HitResolver.java` | 普攻 → Boss 伤害乘崩解倍率 |
| `server/skills/SkillHitEffects.java` | 战技 / 金色波动体 → Boss 伤害乘崩解倍率（所有 Boss 伤害路径统一在这一处乘，避免"只有某种打法吃得到收益"） |
| `client/CombatHud.java` | 新增**失衡条**（金）+ 崩解期整条爆闪 + "崩解 +50%" 文本；只读同步字段，客户端不参与结算 |

### 3.2 证据（运行时，不是单测数字）

单测（`PoiseMathTest` + 全量）：`13 个类 / 80 个用例 / 0 失败`。

专用服务器 smoke（`runServer -PserverSmoke`，2026-10-12 17:20）：

```
SERVER_SMOKE_PASS: 428 shared checks, no client-class dependencies
SERVER_BUS_PASS: ServerCombat 单例与 SkillServerEvents 自动订阅都在游戏总线上（合成投递 1 次 → 各执行 1 次）
SERVER_BREAK_PASS: 崩解 payoff 接线正确（同名义伤害 10.0：崩解前掉血 10.0 / 崩解期掉血 15.0，倍率 1.5，描边=true）
SERVER_BUS_PASS: 每 tick 只推进一次（5 tick → 处理器 5 次）
```

`SERVER_BREAK_PASS` 真的刷了一个 Boss、真的把失衡打满、真的对比了崩解前后的掉血量（10 → 15）。
这条检查的意义在于：**"类写好了但没接线"是这轮踩过的坑**（事件总线那次），所以 payoff 也必须用运行时证明。

客户端镜像（`runClient -PclientSmoke`）：服务端刷 Boss → 打满失衡 → 客户端断言
`isBroken()` 与 `isCurrentlyGlowing()` 都同步到位（HUD 失衡条读的就是这两个字段）。
结果：**见 §3.3**。

### 3.3 客户端端到端结果（`runClient -PclientSmoke`，17:25，全绿）

```
[smoke] 已准备客户端测试存档 build/clientSmoke/saves/codex-player-smoke
CLIENT_SMOKE: 428 shared checks passed
CLIENT_SMOKE_PASS: all 13 visual fixtures rendered
CLIENT_NETWORK_PASS: combat toggle snapshot via real key press
CLIENT_NETWORK_PASS: skill cast accepted from real key press: cast#1 wave_of_gold WINDUP 段0
CLIENT_NETWORK_PASS: client mirror sees the cast (skill cast payload arrived)
CLIENT_NETWORK_PASS: skill timeline finished and returned to idle
CLIENT_NETWORK_PASS: fresh boss is not broken
CLIENT_NETWORK_PASS: server entered break state with outline
CLIENT_NETWORK_PASS: break boss is tracked by the client
CLIENT_NETWORK_PASS: client mirror sees break state (DATA_BROKEN synced)
CLIENT_NETWORK_PASS: client mirror sees break outline (shared flag 6 synced)
CLIENT_NETWORK_PASS: all network checks completed
```

最后五条就是 §6 的端到端证据：**服务端打满失衡 → 客户端看到"崩解"并看到描边标志**
（HUD 失衡条爆闪读的正是这两个同步值）。截图在 `build/clientSmoke/screenshots/`。

### 3.4 踩到并修掉的坑（值得你记进排障手册）

1. **`hasGlowingTag()` 不同步**。它读的是 `Entity.hasGlowingTag` 这个**非同步字段**，
   客户端恒为 `false`；客户端可见性必须用 `isCurrentlyGlowing()`（客户端读共享标志位 6，
   服务端 `setGlowingTag` 会同时置位）。我用错断言，第一次客户端 smoke 直接崩在
   `CLIENT_NETWORK_FAIL: client mirror sees break outline`——**代码是对的，断言是错的**，
   已改成 `isCurrentlyGlowing()`。
2. **`clean` 会删掉 smoke 存档**，而 `--quickPlaySingleplayer` 找不到存档时客户端**静默退出**
   （日志里连一行 smoke 都没有，看起来像构建失败）。已加构建任务
   `prepareClientSmokeWorld` / `prepareServerSmokeDir`，跑 `-PclientSmoke` / `-PserverSmoke` 时自动准备。

---

## 3B. 已交付：§4 跃起落地统一兜底

### 3B.1 实现

| 文件 | 改动 |
| --- | --- |
| `server/skills/SkillLanding.java`（新） | **纯逻辑**落地判定器：`AIRBORNE / LANDED / LOW_CEILING / LIQUID / VOID / TIMEOUT`，世界查询通过 `Probe` 注入；判定顺序固定为 落水 → 虚空 → 正常落地 → 低天花板 → 超时 |
| `server/skills/LandingSupport.java`（新） | 世界查询适配层：`groundBelow()`（向下 64 格找最近落点，扫不到返回 `NaN`）、`inLiquid()` |
| `server/skills/SkillMotion.java` | 记录起跳点（`launchX/Y/Z`）；新增 `ceilingBlocked` / `cutRise` / `place` |
| `server/skills/RemembranceRuntime.java` | 每次起跳重新武装落地判定（多段技能每段独立兜底）；`landingTick` 收口六个分支；`onFinish` 时若人还在空中则交给**安全网** |
| `server/skills/SkillDriver.java` + `SkillServerEvents.java` | 新增 `onSafetyTick`：**施放结束之后**仍悬空的跃起继续被轮询（只救人，不结算伤害） |
| `testing/ServerSmokeHarness.java` | 新增 `SERVER_LANDING_PASS`：真实世界里验证落点扫描（放一块测试石头 → 命中其顶面）与世界底部之下返回 `NaN` |

六个分支的落地方式：

| 分支 | 触发 | 处理 |
| --- | --- | --- |
| 正常落地 | 检测到地面 | 结算落地伤害（黄金碎击 / 唤星第二段），只结算一次 |
| 低天花板 | 上升被位移预算判定到顶 | 砍掉上升速度立即转下落（**非终止分支**） |
| 落水 / 落岩浆 | 落点是液体 | 取消落地伤害 + 溅水表现 |
| 悬崖 / 虚空 | `y < 世界底部 + 8` | 取消位移，送回起跳点，**退还 50% 该段 FP** |
| 超时未落地 | 起跳后 60 tick 未落地 | 吸附到下方最近实心方块后强制结算；下方 64 格无落点时按虚空处理 |
| 施放中断 | 死亡 / 换维度 / 断线 / 取消 | 走同一条 `onFinish` 清理路径，恢复重力 |

三处按方案要求特意做对的地方：
1. **落地伤害只结算一次**：判定器一旦结算就锁死结论（再次调用返回同一结论），`state.impacted` 再做一次保护；
2. **每个分支都会恢复重力**：`SkillMotion.stop/place` 统一接管速度，异常分支不例外；
3. **"不穿墙"与"落点安全"是两件事**：前者是既有的位移预算，后者由本处理器补上。

### 3B.2 证据

单测：`SkillLandingTest` 覆盖 9 个用例（六分支 + 优先级 + 只结算一次 + 超时钳制），
全量 `14 类 / 89 用例 / 0 失败`。

专用服务器 smoke：
```
SERVER_LANDING_PASS: 落点扫描可用（测试方块顶部 Y=121 命中，世界底部之下返回 NaN）
```

客户端 smoke（真实玩家、高空释放黄金碎击 → 施放在落地前结束 → 安全网接管）：
见 §3B.3。

### 3B.3 客户端端到端结果（17:38，全绿）

```
CLIENT_NETWORK_PASS: combat toggle snapshot via real key press
CLIENT_NETWORK_PASS: skill cast accepted from real key press (client mirror follows server cast)
CLIENT_NETWORK_PASS: skill timeline finished and returned to idle
CLIENT_NETWORK_PASS: jump skill cast accepted while airborne (client mirror follows server cast)
CLIENT_NETWORK_PASS: jump skill server-side cast is gold_breaker: cast#2 gold_breaker WINDUP 段0
CLIENT_NETWORK_PASS: landing safety net observed the landing after the cast ended (LANDED)
CLIENT_NETWORK_PASS: player is back on solid ground, not stuck in the air or in the void
CLIENT_NETWORK_PASS: all network checks completed
```

最后三条就是 §4 的端到端证据：**高空释放黄金碎击 → 施放在落地前结束 → 安全网接管并观察到落地
（LANDED）→ 玩家双脚踩在地面上**（没有卡在空中，也没有掉进虚空）。

### 3B.4 本轮踩到的第二个坑：smoke 的按键断言依赖窗口焦点

上一轮客户端 smoke 出现大面积失败（`combat toggle snapshot`、`bound light attack`、`guard accepted`…），
看起来像"上一轮修的东西又坏了"。实际原因链是：

1. `ClientCombat` 在**窗口失焦**时会（正确地）忽略所有按键；
2. 自动化跑在后台时窗口常常不是前台 → 按键被忽略 → 战斗模式没打开；
3. 后面所有需要战斗模式的动作（普攻 / 盾防 / 喝药 / 蓄力 / 翻滚）全部被服务端拒绝，
   于是一整片断言连锁失败。

两条修正（都在测试侧，产品行为没改）：
- 真实按键改成 **轮询重试 + 相位冻结**：按一次不生效就再按（间隔 10 tick，上限 20 秒），
  在按键落实之前**不推进网络相位的 tick 计数**——后面的断言不会再被按键延迟带偏；
- 测试期间对 `Minecraft.setWindowActive(true)` 做一次覆盖（只覆盖"窗口焦点"这一个与测试目标无关的条件）。

另外顺手修掉的隐患：`case 0` 的"重置战斗状态"是排队到服务端线程执行的，
如果它晚于 `case 8` 的按键意图执行，会把刚打开的**战斗模式又清掉**（同一片连锁失败的另一种触发方式）。
现在相位冻结保证了顺序。

还有一个**自己写出来的** bug，值得单独记一笔：按键重试的间隔判断写成了
`ticks - lastClickTick < RETRY`，而 `lastClickTick` 初值是 `Integer.MIN_VALUE` ——
减法整型溢出成负数，条件永远为真，于是**一次都没按下去**，表现却是"等了 400 tick 超时"，
看着像窗口失焦。现在用 `boolean clicked` 记录"按过没有"，不靠哨兵值做算术。
（教训：超时类失败先怀疑"动作到底发出去了没有"，别先怀疑环境。）

---

## 3C. 已交付：§2 锻造与质变（首版）

### 3C.1 实现

| 层 | 文件 | 内容 |
| --- | --- | --- |
| 数值核心（纯逻辑） | `core/UpgradeMath.java` | 两条曲线 `B(t)=1+1.00t`、`S(t)=1+1.50t^1.6`；上限 +10（类型）/ +5（追忆） |
| | `core/Affinity.java` | 8 种质变：惩罚系数 0.85–0.95 + 主/副补正档位（低强化 A、高强化 S，即原作 "A~S"）；火焰是唯一的无补正质变 |
| | `core/CorrectionGrade.java` / `core/AttributeKind.java` | 档位→每点系数（S 0.90 … E 0.10）+ 三段软化函数（20/40/60 断点） |
| | `core/AttributeSet.java` / `core/WeaponState.java` | 五项属性、武器成长状态（等级/质变/战灰锁定）+ 合法值夹紧 |
| | `core/WeaponDefinition.java` / `core/WeaponDefinitions.java` | 武器定义表（基础伤害 + 补正强度 + 档位），支持数据包覆盖 |
| 平台层 | `registry/ModDataComponents.java` + `WeaponStateCodec.java` | 数据组件 `eldencombat:weapon_state`（persistent + networkSynchronized） |
| | `registry/ModRecipes.java` | 自定义配方序列化器 `eldencombat:weapon_forging`（类型仍是 `minecraft:smithing`，所以**原版锻造台直接用**） |
| | `crafting/WeaponForgingRecipe.java` | 三张模板三种操作；**产出由底座武器当前状态现算**（一个档位一条配方，而不是 41×10 条） |
| 物品 | `registry/ModItems.java` | 50 件：锻造石[1-8]、失色石[1-5]、古龙岩两种、强化模板[1-8]、失色模板[1-5]、8 种质变模板、重铸模板、8 种质变素材、悔恨之泪、铃珠[1-4] |
| 数据包 | `data/eldencombat/recipe/*.json`（24 条）+ `tags/item/{weapons,normal_weapons,remembrance_weapons}.json` | 强化 8+5（含古龙岩两种）、质变 8、重铸 1 |
| 战斗接线 | `HitResolver` / `SkillHitEffects` | 伤害乘"相对全新武器的倍率"（`damageAt(state, attrs)/baseDamage`），普攻与战技都吃 |
| 属性系统（最小） | `registry/ModAttachments.ATTRIBUTES` + `server/PlayerAttributes.java` | 落盘 + 随死亡继承；命令式加点（UI 后置） |
| 验收入口 | `/eldencombat attribute …`、`/eldencombat weapon [level N\|affinity X]` | 不改存档也能验收任意等级/质变组合 |
| 占位美术 | `tools/gen_forging_placeholders.py` → 50 张 16×16 占位贴图 + 模型 + 语言条目 | Codex 出正式图后同名覆盖，**不要改 id** |

### 3C.2 证据

单测：`WeaponProgressionTest`（曲线逐级、质变惩罚与档位重写、等级保留、战灰锁定、软化函数、
属性收益递减、定义表完整性）+ 全量 **17 类 / 117 用例 / 0 失败**。

专用服务器 smoke（**走真实配方管理器**，不是"JSON 文件存在"）：
```
SERVER_FORGING_PASS: 强化/质变/重铸全链可用，档位错配无产出，追忆武器锁定生效，+10 伤害倍率 ×2.00
```

这条断言覆盖了方案 §7.2 的可机检清单：

| §7.2 | 检查 | 结果 |
| --- | --- | --- |
| 1 | 每档伤害符合公式 | ✅ 单测逐级比对 B(t) |
| 2 | 质变后基础伤害 = 原值 × P、档位重写 | ✅ 单测 + smoke（HEAVY 产出） |
| 3 | 质变保留等级、重铸回无质变且保留等级 | ✅ smoke（+2 → HEAVY → NONE，等级都是 2） |
| 4 | 追忆武器 `replaceableArt=false`，不可重铸 | ✅ smoke（重铸无产出） |
| 5 | 材料不足/等级已满/物品不匹配时不产出且不消耗 | ✅ smoke（档位错配、满级强化均无产出；原版锻造台语义保证不消耗） |
| 6 | 并发不重复扣材料 | ✅ 依赖原版菜单的每玩家容器语义（无共享状态） |
| 7 | 存档重载后等级与质变保持 | ✅ smoke 做 NBT 编解码往返一致性 |
| 8 | JEI 显示每档配方与推荐质变 | ⚠️ 部分：配方是标准 smithing 类型，JEI 会列出模板+底座+素材；**动态产出**在 JEI 里显示为空，推荐质变提示需要 JEI 插件（归 Codex/后续） |

### 3C.3 明确没做的部分（不假装做完）

1. **铃珠经济**：铃珠物品已注册，但"交给商人解锁无限购买"需要商人/交易系统，本轮没做。
2. **质变素材的掉落/采集来源**：只有物品，没有世界生成与掉落表。
3. **加点 UI**：只有命令；UI 归 Codex 的交互层。
4. **出血/异常抗性表**：血质变目前只改档位，不带出血累积（与方案 §8 的边界一致）。
5. **占位美术**：50 张占位贴图需要正式版本。

---

## 3D. §5 收口时发现的真问题：31 件武器原本不能普攻

做 §5 时顺手查了武器识别链路，发现比"缺差异"严重得多的问题：

`Weapons.classify` 只认 `light_swords` / `great_swords` 两个物品标签，而这两个标签里只有
直剑/大剑与原版剑——**其余 31 件类型武器全部被判成 `WeaponClass.NONE`**，
而 `ServerCombat.startAttack` 对 `NONE` 是直接 `return`：拿在手里完全打不出普攻。

修法（`server/Weapons.java`）：按 §5.3 的重量表分档——`weight ≥ 5.5` 走重型时序、其余走轻型，
标签作为回退。这样 33 件立刻全部可用，而单件差异由 reach / 削韧 / 重量系数 / 成长倍率叠加。

对应断言：
```
SERVER_WEAPON_PASS: 41 件武器都有数值档，reach 真的落到属性上（矛 3.9 / 匕首 2.7 / 空手 3.0），
                    近战武器全部可普攻（轻档 12 件 / 重档 21 件）
```

---

## 3E. 收口验证（2026-10-12 18:11，全部一次通过）

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 构建 + 单测 | `.\gradlew.bat clean test build --offline` | BUILD SUCCESSFUL，**17 类 / 117 用例 / 0 失败** |
| 专用服务器 smoke | `runServer -PserverSmoke` | `SERVER_BUS_PASS` ×2、`SERVER_BREAK_PASS`、`SERVER_LANDING_PASS`、`SERVER_WEAPON_PASS`、`SERVER_FORGING_PASS` |
| 客户端 smoke | `runClient -PclientSmoke` | 13 个渲染场景 + 全部网络断言通过，含按键/战技/**高空落地安全网**/崩解同步 |

客户端 smoke 本轮新增通过的两条（§4 的端到端）：
```
CLIENT_NETWORK_PASS: jump skill cast accepted while airborne (client mirror follows server cast)
CLIENT_NETWORK_PASS: jump skill server-side cast is gold_breaker: cast#2 gold_breaker WINDUP 段0
CLIENT_NETWORK_PASS: landing safety net observed the landing after the cast ended (LANDED)
CLIENT_NETWORK_PASS: player is back on solid ground, not stuck in the air or in the void
CLIENT_NETWORK_PASS: all network checks completed
```

### 剩下的事（下一轮/交给 Codex）

| 事 | 归谁 | 说明 |
| --- | --- | --- |
| §3 战技时序收口 | dsh（下轮） | 收招拆"硬直 6 + 取消窗口 4"、蓄势压缩到 4–6 tick + 提前量、逐段收招、顿帧走客户端渲染冻结 |
| 铃珠经济（商人解锁无限购买） | dsh（下轮） | 物品与档位已在，缺商人/交易接线 |
| 质变素材的世界获取（矿脉/精英掉落/区域拾取） | dsh + Codex | 需要战利品表与地形决策 |
| 加点 UI | Codex | 数值契约与命令已就绪 |
| JEI 分类与推荐质变提示 | Codex | 配方是标准 smithing，JEI 已能列出材料 |
| 50 件锻造物品的正式贴图/模型 | Codex | 占位图在 `textures/item/*.png`，**同名覆盖，不要改 id** |
| 手感验收（§7.3 的 A–F 信号） | Codex | 数值与开关齐了，需要人跑 |

---

## 4. 施工计划（含完成条件）

| 项 | 内容 | 完成条件 |
| --- | --- | --- |
| ~~§4 落地兜底~~ | **已交付**，见 §3B | ✅ 9 条单测 + `SERVER_LANDING_PASS` + 客户端高空战技端到端 |
| ~~§5 三张表~~ | **已交付**，见 §3B/§3D | ✅ reach 落到属性（smoke 实测 3.9/2.7/3.0）、削韧逐件、重量→攻速/耐力、推荐质变；顺带修掉"31 件不能普攻" |
| ~~§2 锻造与质变~~ | **首版已交付**，见 §3C | ✅ 24 条配方 + 数据组件 + 曲线 + 补正契约 + `/eldencombat weapon` 验收入口；⚠️ 铃珠经济 / 素材掉落 / 加点 UI / 正式美术未做 |
| §3 战技时序收口 | 收招拆"硬直 + 取消窗口"、蓄势压缩 + 提前量、逐段收招、顿帧走客户端渲染冻结 | 时序参数改动 + 取消白名单；需要 Codex 手感验收 |
| §6 多人缩放复核 | 崩解倍率不变、失衡上限 ×(1+0.5×(n-1)) | ✅ 已按此实现（见 §3.1） |

---

## 5. 需要 Codex 配合的点（不阻塞我的部分）

1. **§2 的美术**：三张模板、8 种质变素材、失/普通锻造石 8 档、铃珠、悔恨之泪的图标与模型；
   数据侧 `DataComponent` 与配方由我落地。
2. **§5 的 UI 提示**：推荐质变、reach/削韧/重量在 tooltip 里的呈现（我的部分只到数据与属性）。
3. **HUD 布局**：新增的失衡条位置/配色可以调，但**不要**改成直接读服务端字段——
   它读的是同步值，客户端不参与结算，这条边界要保持。
4. **不要恢复 EF**；旧文档里"这些类不要注册到事件总线"的说明是 EF 时代的，已作废。

---

## 6. 不在本轮范围

属性加点 UI、战灰替换 GUI、22 个类型战技、BOSS、出血/异常状态抗性表。
（方案 §8 的边界我认可，加上"加点 UI 后置"这一条。）
