# 交给 Codex 的缺陷反馈与实施交接（八追忆战技动作/VFX）

2026-10-17。提出人类：DSH 自述"已修"不成立，故出此交接，由 Codex 接管编码。
本文只写**能复现的事实、根因、以及需要改的位置**；不写"已完成"。

---

## 0. 结论先说

1. **人类报的三条实测缺陷，一条都不能算修好。** 以人类观测为准：
   - 「水鸟和命定之死原地蹦三下跟个僵尸一样」
   - 「战技只有挥剑的第一下有特效」
   - 「黄金碎裂没落地前就有地面碎裂的特效，这符合现实吗」
2. DSH 这一轮实际做到的是**把缺陷变成可观测**（新语义夹具、逐实例提交登记、落地离地锁、每挥广播挥臂、前冲参数、取样窗口），**不是**把观感修好。
3. **连"量得准"都还没做到**：同一段固定夹具三次运行的像素差最大差 16 倍（夜间命定 12 → 193 → 195）。在读数不稳的仪器上宣布修好，等于又犯一次"13 段全绿却漏掉人类一眼可见缺陷"的错。
4. 请 Codex 按 §4 的 P0 清单换实现，**不要沿用 DSH 的实现当作"已修基线"**；§5 的门禁不许放宽。

---

## 1. 三条缺陷的根因（带文件与行号）

### BUG-A｜水鸟乱舞 / 命定之死"原地蹦三下，像僵尸"

**现状实现**：位移是手搓速度，不是服务端权威位移。

- `server/skills/SkillMotion.java:62` `launch(player, horizontal, vertical, turn)`、`:81` `dash(player, horizontal, turn)`；
- 两者最终都走 `SkillMotion.java:99-104` `send()`：`player.setDeltaMovement(v)` + `hurtMarked = true` + `ClientboundSetEntityMotionPacket`；
- 也就是说：**服务端不拥有位置**，它只是把一个瞬时速度塞给客户端，之后位置由客户端物理决定，服务端再从客户端位置包读 `onGround`/`y`。

**为什么看起来像原地蹦**（三条叠加，缺一条都不会是"僵尸"）：

1. **垂直冲量仍占主导**：`RemembranceRuntime.java:162-163` 起跳 `destined_death (.24,.34)`、`waterfowl_dance (.42,.34)`；玩家起跳落地总时长约 10–15 tick，水平自滑行约 1.1–1.5 格 → 一跳几乎等于"跳起来再落回原处"。
2. **连段不再起跳，但也没有新的位移量级**：`RemembranceRuntime.java:335` 连段每挥 `dash(.15/.22)`，三个 burst 合计只前进约 0.5 格，且间隔 12 tick（见 BUG-B）→ 三挥发生在同一小片地面上。
3. **姿态与位移无耦合**：客户端 `client/SkillPoseAnimator` 播的是 `assets/eldencombat/skill_poses/*.json` 的关键帧（原版六个关节），里面**没有位移曲线**；服务端每挥只补一次原版 `player.swing(MAIN_HAND,true)`（`RemembranceRuntime.java:330` `swing(player)`）。第一人称看不到玩家模型（PlayerModel 不渲染），所以第一人称下"人动了"只能靠相机位移 + 手臂挥动 —— 手臂每 12 tick 挥一下、相机原地小幅前扑，人眼读作"僵尸挥手"。

**Codex 要改的（建议 B 方案）**：

- **A（最小）**：保留冲量，但把 ACTIVE 段从"一次性冲量"改成**逐 tick 水平推进**（每 tick 给 0.15–0.25 的水平速度且不清零垂直分量），垂直冲量降到 0.12–0.18；并把 `SkillMotion` 的水平预算与 `spec.maxHorizontal()` 对齐。
- **B（推荐，结构性）**：改为服务端权威位移：服务端每 tick 计算目标位置并用 `ServerPlayer.teleportTo(...)`（或 `ServerGamePacketListenerImpl.teleport`）下发，客户端只做插值；同时给 `SkillCastPayload` 增加 `motionPhase`（0..1 位移进度）让姿态动画能跟位移同步。
- **无论 A/B**：水鸟三段之间必须"位移连续"（同一方向持续前进），不能每段重新起跳；验收见 §5 的位移门槛（apex/forward/airborne 已存在）。

> 反面做法：继续调 `launch/dash` 的两个小数假装修好 —— 这正是本轮被判无效的原因。

### BUG-B｜"战技只有挥剑的第一下有特效"

**实测（本轮机器证据）**：三次挥击**在服务端都真实 spawn 了效果实体**（语义夹具逐实例覆盖：`effect/19/0`、`effect/21/1`、`effect/23/2` 各 47/48、47/47、47/47），所以这不是"没画"，而是**画了但看不见/看不到第三次**。三个独立原因：

1. **`maxStages: 3` 是死配置，段 2/3 永远不可达。**
   `data/eldencombat/dsh_skills/remembrance_parameters.json`：水鸟/命定 `followupWindowTicks: 0`、`maxStages: 3`；
   `SkillTransactions.java:39` 只有 `spec.hasFollowup()` 才允许追击 → `followupWindowTicks == 0` ⇒ `hasFollowup() == false` ⇒ 段 2/3 不可达。
   现在的"三段"其实是**同一个 ACTIVE 段（`activeTicks: 36`）里的 3 个 burst**：`RemembranceRuntime.java:324` `burst = min(2, elapsed*3/duration)`，即 elapsed 0/12/24。

2. **burst 间隔 12 tick，而每挥的效果只活 8 tick。**
   `RemembranceRuntime.java:337-338` `visual(player, player.position(), <WATERFOWL|DEATH_SLASH>, effectRadius, 8, burst, true)`；
   `entity/skill/SkillEffectEntity.java:82` 到点 `discard()`；`:86` 只要 `castId` 变化或不在施法也立即 `discard()`。
   → 观感 = 闪一下、空 4 tick、再闪一下；且三次都发生在 `player.position()` 附近（dash 只推 0.15–0.22 格），**三个效果互相重叠**，看起来像"第一下的残影"。

3. **每一挥都从作者美术的第 0 帧开始播，而第 0 帧是最弱的一帧。**
   离线墨量（`docs/combat-evidence/vfx-semantics-correction-20261017/art-ink-measurement.txt` + 本轮逐帧复测）：
   | 路由 | frame_00 | 中段最强帧 | 倍率 |
   |---|---|---|---|
   | waterfowl | ink 72、bbox 6×16 | frame_08：ink 140、bbox 28×24 | 1.9× / 面积 7× |
   | death_slash | ink 172、bbox 12×24 | frame_06：ink 420、bbox 34×38 | 2.4× / 面积 4.2× |
   | cursed_slash | ink 180、bbox 20×34 | frame_04：ink 220、bbox 36×26 | 1.2× |
   `ArtworkRenderer` 用 `FxArtwork.frame(progress, 16)` 从 0 开始播 → 每次挥击的第一帧都几乎透明。

**Codex 要改的**：

- P0-1：**把 `maxStages` 与 `followupWindowTicks` 对齐**（二选一，需人类定手感）：
  - (a) 真三段：`followupWindowTicks ≥ 8`，每段独立 `onActive`，`swing` + `visual` 每段一次；
  - (b) 单段三斩：`maxStages = 1`，在 ACTIVE 内做 3 个"斩击窗口"，每个窗口 spawn 自己的效果并**存活到下一次斩击**（寿命 ≥ 10 tick），并且在 `SkillCastPayload` 里带上窗口序号，客户端据此播不同关键帧。
- P0-2：`visual(...)` 的 `duration` 参数不要写死 `8`；按 `activeTicks/3` 的对齐值下发（36/3=12 → 8 太短），或让效果在"下一次斩击开始时"再 discard（需要"被替代"而不是定时销毁）。
- P0-3：`visual` 的 `origin` 现在是一对本位置 `player.position()`（脚底）；三段应沿视线方向设置**递增的偏移**（0 / 0.6 / 1.2 格），否则三个效果叠在同一个点。
- P1：起播帧补偿：`FxArtwork.frame()` 之后对短寿命效果做 `frame = clamp(frame + 2, 0, count-1)` 之类的偏移（或美术侧把 frame_00 补足），避免"第一帧最弱"。

### BUG-C｜"黄金碎击没落地就有地面碎裂"

**现状**：落地结算已统一到 `RemembranceRuntime.landingTick`（`:195`），地裂效果 `GOLD_IMPACT` 只在 `landingImpact` 里 spawn（`:274`）——这条改对了，但仍有三个可复现的"假落地"：

1. **判据来源是客户端**：`SkillLanding.Probe.onGround()` 来自服务端实体上的 `onGround`，而它由客户端位置包驱动。
2. **8 tick 兜底会把"冲量被顶住"判成落地**：`SkillLanding.java:118-121`
   ```java
   if (ticks >= NO_TAKEOFF_TICKS) {          // NO_TAKEOFF_TICKS = 8
       return settle(Outcome.LANDED);         // ← 从未离地也报 LANDED，并照常打地裂
   }
   ```
   低天花板、被方块卡住、客户端抑制位移、网络抖动都会走到这里 → 玩家还悬着，地面已经碎了。
3. **地裂原点用的是脚底**：`RemembranceRuntime.java:274` `visual(player, player.position(), Kind.GOLD_IMPACT, ...)`；落地瞬间脚底与方块表面常有 0 ～ 0.5 格误差，视觉上是"碎在半空"。

**Codex 要改的**：

- P0-4：把"冲量被顶住"从 `LANDED` 里**拆出去**，新增 `Outcome.BLOCKED` / `TAKEOFF_FAILED`：取消技能（或转回 IDLE）、**不发地裂**、给玩家动作栏提示；`NO_TAKEOFF_TICKS` 不得再 `settle(LANDED)`。
- P0-5：地裂原点改为 `LandingSupport.groundBelow(...)` 得到的**接触点**（或用 `player.onGround()` 后的 `player.position().subtract(0,0.02,0)` 并对齐到方块面），不要直接用脚底坐标。
- P1：`GOLD_IMPACT` 效果起播帧同样做"落地后 ≥1 tick 再播"的处理，避免冲击与接触同帧导致的悬空观感。

---

## 2. 为什么 DSH 的机器门禁没抓住（夹具盲区，必须一起交代）

1. **相机实体盲区（致命）**：旧固定夹具的相机是盔甲架（ArmorStand）。实测在那种机位下**客户端玩家位移恒为零**（`apex=81.000`、`horizontal=0.000`；相机给回玩家后为 `+2.75 / 3.19` 格）。→ "原地蹦/捞空/落地特效早于落地"在旧体系里**既无门禁也无观测量**，13 段全绿是必然的。本轮已改为施法者自己第一人称机位。
2. **单帧取样冒充"最好一帧"**：本轮把取样间隔从 6 帧降到 2 帧，但效果寿命 8 tick、burst 间隔 12 tick，`primary2` 名义 4 次取样实际 **0 次**（第一次取样后 2 帧内效果实体已 discard，`repeatCount>=1` 即关闭步骤）。日志实测每段 `pairs=1`。
3. **两次运行读数不稳定**（同一代码路径、仅取样相位不同）：
   | 段 | 回归1 | 中途 | 本轮 |
   |---|---|---|---|
   | 命定（白天） | 930 | — | **3310** |
   | 咒血斩 | 250 / 262 | 266 | **1071** |
   | 命定（夜间） | 12 | 193 | **195** |
   | 水鸟（白天） | 103 | 168 / 184 | **188** |
   | 水鸟（夜间） | 18 | 55 | **31** |
   → 在"单帧随机相位"上做门禁，等于用噪声当结论。
4. **阈值 300 是代理指标，不是观感**：水鸟白天 188 像素对应屏幕上约 **15×30 像素的一条细线**（证据图 `vfxfixed-04-waterfowl_dance-primary1-a.png`）。它不是"没渲染"，是"看起来没有"——而人类判据是后者。**因此即便门禁调成 100，"只有第一下有特效"这条也依然算没修。**

---

## 3. DSH 本轮已落地的改动（保留/回退由 Codex 判断，不主张它们是修复）

| 位置 | 改动 | 建议 |
|---|---|---|
| `server/skills/SkillLanding.java` | 离地锁 `DEPART_EPSILON=0.05`、`NO_TAKEOFF_TICKS=8`、`departed()` | **保留锁**，但按 P0-4 拆掉 `NO_TAKEOFF_TICKS → LANDED` |
| `server/skills/SkillMotion.java` | 新增 `dash(player, horizontal, turn)`（只给水平） | 保留为 A 方案基础；B 方案下可删 |
| `server/skills/RemembranceRuntime.java` | 起跳参数、每挥 `swing`、每挥 `visual`、`JumpTrace`、`VFX_JUMP_*` 日志 | 保留（`swing` 与 Trace 是有效增量） |
| `client/SkillPoseAnimator` + `PlayerModelPoseMixin` | 姿态混合（上一轮） | 保留 |
| `client/effects/VfxDiagnosticLayers.java` | 逐实例提交登记、`art=` 帧号登记 | 保留（诊断用，不上线行为） |
| `testing/VfxSemanticsHarness.java` | 新语义夹具（8 技能 + 负例） | 保留 |
| `testing/VfxFixedHarness.java` | 取样间隔改为按步骤给（`SHORT_LIFE_SPACING=2`）、背景扣除演员区、`WAVE_FRONT` 机位、`art=` 写入 VFX_PAIR_DIFF | 保留，但**仍需按 §4-P0-6 改成"跨 burst 取样"** |

改动前备份：`docs/code-backups/20261017-semantics-dsh/`（7 个文件）。
DSH 自己的表述与证据：`docs/VFX-SEMANTICS-CORRECTION-REPORT-20261017.md`（其中"作者美术太薄需人类定调"的结论**部分作废**，见 §1-BUG-B 第 3 条：真相是"每帧都从最弱帧起播 + 三个效果重叠"）。

---

## 4. 交给 Codex 的实施清单（按优先级）

**P0（不修就是没修）**

1. **BUG-A 位移机制**：按 §1-A 的 A/B 方案落地；水鸟/命定三段位移连续、不再重新起跳。
2. **BUG-B 段数与效果寿命**：`maxStages` 与 `followupWindowTicks` 对齐；`visual` 寿命覆盖到下一次斩击；三段原点递增偏移。
3. **BUG-C 假落地**：`BLOCKED/TAKEOFF_FAILED` 独立结果，不打地裂；地裂原点贴接触面。
4. **P0-6 夹具取样**：`primary` 步骤**不要在 need 不满足时关闭**；改成"整个施法期最多取 N 次、每次落到一个新实例"（例如按 `VfxDiagnosticLayers.submittedIdentitiesThisFrame()` 的实例变化触发），这样才能真正证明"每一挥都有独立的主贴片"。

**P1（观感）**

5. 起播帧补偿（BUG-B 第 3 条）。
6. `SkillSlashSprites` 的 strip 几何（`effects/SkillSlashSprites.java:39-45`：`width = r*1.25..1.6`、`height = min(.3, width*.16)`、6 条带）目前**在生产路径上根本没被用**（路由 ready 时只画 `ArtworkRenderer` 的单张 6×4.5 平面）。若要让挥击像刀刃，得让 strip 路径真正参与，或把单平面按作者 `artwork.scales/rolls`（`routes/2d.json` 里 waterfowl 已声明 3 个 roll）画成 3 层。
7. 夜间可见性：`death_slash` 墨亮度仅 58（近黑），夜景对比塌（夜间命定 195 像素、夜间水鸟 31 像素）。若人类要夜景也看得见，需美术侧提亮或加程序化外发光（`FxSlashRoute.Outline.CRESCENT` 轮廓已有，但只在资源缺失/CORE LOD 时才画）。

**P2（工程）**

8. 把 §5 的"同段两次运行波动 ≤20%"做成门禁（当前没有任何门禁约束取样稳定性）。
9. 保持 `gradlew verifyJarHasNoHarness`（JAR 里不得出现夹具类）。

---

## 5. 验收门禁（沿用，不许放宽）

- `gradlew test`：全绿（交接时实测 236 项、0 失败、1 跳过）。
- `gradlew verifyJarHasNoHarness`：`OK (no *Harness* entry)`；交接时重建的 JAR 为
  `build/libs/eldencombat-0.2.0.jar`，SHA256 `3F6E96C61DD98EE72C2FE156634C8165C65E32F953FAE91C39E1A0EEF5AC1DBD`。
- `runClient -PvfxSmoke -PvfxSemantics`：exit 0，`VFX_SEMANTICS_COMPLETE: 8 skill(s)`；负例 `-PvfxLegacyHop` 必须 exit 1。
  位移门槛：`departed=true`、`apex ≥ +0.25`、`airborneTicks ≥ 5`、`horizontal ≥ 1.2 × rise`。
- `runClient -PvfxSmoke -PvfxFixed`：**0 段失败**，且每段 `pairs ≥ 2`、`primaryInRoi ≥ 300`、`outside=0`、背景稳定性 `backgroundChanged ≤ 2049`。
- **新增**：同一段连续两次运行的 `primaryInRoi` 波动 ≤ 20%（否则视为仪器不稳定，不算通过）。
- 人类观感（最终判据，机器不可替代）：第一人称手打水鸟/命定看三连斩、黄金碎击看落地时序。

---

## 6. 复现命令（本机）

```powershell
$env:JAVA_HOME='D:\JDK21'; cd D:\codexout\mcmod
# 语义夹具（服务端语义，非观感）
cmd /c ".\gradlew.bat runClient -PvfxSmoke -PvfxSemantics --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21 > build\semantics.log 2>&1"
# 固定夹具（13 段像素对照）
cmd /c ".\gradlew.bat runClient -PvfxSmoke -PvfxFixed --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21 > build\fixed.log 2>&1"
```

结果看 `build\vfxSmoke\logs\latest.log`（隔离目录，仅成功编译并运行时刷新）：`VFX_SEMANTICS_COMPLETE` / `VFX_SMOKE_FAIL` / `VFX_FIXED_SEGMENT` / `VFX_PAIR_DIFF`（本轮起带 `art=<帧/总>`）。

本轮实测（`docs/combat-evidence/vfx-bug-feedback-20261017/fixed-run-0917-sampling2.log`，SHA256 `5EEAE1408E9AA31A5B6C2B94AEBFEC404B2213641E0ED82FD5294B1C38FD73E3`）：

| 段 | 技能 | primaryInRoi | 判定 |
|---|---|---|---|
| 0 | wave_of_gold | 1995 | pass |
| 1 | gold_breaker | 2498（debris 1082） | pass |
| 2 | destined_death（白天） | 3310 | pass |
| 3 | takers_flames（新正面机位） | 1304 | pass |
| 4 | waterfowl_dance（白天） | **188** | **FAIL** |
| 5 | starcaller_cry | 31354（debris 1983） | pass |
| 6 | bloodboon_ritual（白天） | 6675 | pass |
| 7 | cursed_blood_slice | 1071 | pass |
| 8 | destined_death（夜间） | **195** | **FAIL** |
| 9 | waterfowl_dance（夜间） | **31** | **FAIL** |
| 10 | bloodboon_ritual（夜间） | 5111 | pass |
| 11 | 负例：只移相机 | — | pass |
| 12 | 负例：主贴片被抑制 | — | pass |

---

## 7. DSH 明确没做 / 需要人类定调

- 没有改任何美术资源（160 帧 PNG、`2d.json` 的路由参数均未动）。
- 没有换服务端权威位移（成本高、会动输入手感，需人类同意）。
- 没有改 `followupWindowTicks`/`maxStages`（改了就改手感、FP 消耗与技能寿命，需人类定 (a)/(b)）。
- 没有做第一人称逐像素归因（镜头本身在位移，视差占主导，当前技术上做不到；已在报告 §5.2 记为证据不足）。
- 三个"待人类定调"的观感项：① 是否改成真三段；② 夜景是否提亮/加外发光；③ 挥击是否要刀刃条带（strip）而非单张平面。
