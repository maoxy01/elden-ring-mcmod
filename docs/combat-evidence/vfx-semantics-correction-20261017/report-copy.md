# 八追忆战技·动作语义缺陷修正报告（2026-10-17）

对象：`D:\codexout\mcmod`（MC 1.21.1 / NeoForge 21.1.219 / Java 21）。
触发：人类实测反馈三条 ——
①「水鸟和命定之死原地蹦三下跟个僵尸一样」②「战技只有挥剑的第一下有特效」③「黄金碎裂没落地前就有地面碎裂的特效」。

**结论先行：**

| # | 人类反馈 | 判定 | 根因 | 处置 |
|---|---|---|---|---|
| ① | 原地蹦、像僵尸 | **确认是缺陷** | 起跳几乎全垂直（水鸟水平=0，命定=0.2）；每段连击又调一次 `launch()`，落地后再起跳；生产代码**从不播原版挥臂动画** | 已修：低空前冲 + 连段只给水平 `dash` + 每一挥播原版挥击（含施法者自己） |
| ② | 只有第一下有特效 | **一半是缺陷、一半是观感** | 作者美术**每一挥都真的提交**（实测 47–48/48 帧），渲染管线没有"只画第一下"；但旧参数下第 2、3 段整个人在空中 1.5–1.8 格，三段斩击只有第一段打得到前方目标；且水鸟作者美术在屏幕上只有**一条小白缝**（实测 103 像素 / 854×480） | 突进修正后三段**全部命中**（`channels=3/3`，负例见下）；"看不起眼"归美术尺度/亮度，需人类定调 |
| ③ | 没落地就有地面碎裂 | **确认是缺陷** | `SkillLanding` 在起跳冲量发出后的头几个 tick 里，直接采信服务端**过期的 `onGround=true`** 就判落地；旧截图恰好抓到"人在空中、地面已裂" | 已修：离地锁（必须先观察到真的离地）+ 冲量被顶住时 8 tick 兜底 |

三条里有两条半此前**不可能**被夹具发现，原因见 §4：旧受控夹具的相机实体是盔甲架，**那种机位下客户端玩家位移恒为 0**。

## 1. 现场证据（旧构建，vfx92 固定夹具截图）

- `vfxfixed-01-gold_breaker-primary1-a.png`：地面金色碎裂纹理**已经出现**，而施法者**悬在那块地面之上**（身体前倾、离地）→ 抱怨③的直接画面证据。
- `vfxfixed-04-waterfowl_dance-primary1-a.png`：水鸟的作者美术在屏幕上只有角色肩侧**一小条白缝**（与实测 ROI 差 103 像素一致）→ 抱怨②"看不见"的来源之一是美术尺度。
- `vfxfixed-02-destined_death-primary1-a.png`：命定的作者美术是一块明显的黑色弧刃（实测 ROI 差 477 像素）→ 同一套渲染路径，观感差异来自素材本身。

三张图与其 `-clean` 对照帧已归档到 `docs/combat-evidence/vfx-semantics-correction-20261017/`。

## 2. 修正内容（源码）

1. `server/skills/SkillLanding.java` —— **离地锁**。
   `DEPART_EPSILON=0.05`、`NO_TAKEOFF_TICKS=8`：只有观察到"`onGround()==false` 或 `y > 起跳Y+0.05`"之后才允许 `LANDED`；
   冲量始终没把人抬起来（低矮空间被顶住 / 冲量被吞）时，8 tick 后按"原地落地"结算一次，不拖到 60 tick 超时兜底。
   新增 `departed()` 供夹具与诊断使用。
2. `server/skills/SkillMotion.java` —— 新增 `dash(player, horizontal, turn)`：**只改写水平速度**，绝不再给垂直冲量。
3. `server/skills/RemembranceRuntime.java`
   - 突进：命定 `launch(.24,.34,true)`、水鸟 `launch(.42,.34,true)`（原为 `.2/.52`、`0/.52`，几乎垂直）；
   - 连段推进改为 `dash(.15 / .22, true)`（原为 `launch(.28,.32,true)`，落地后等于每段重新起跳一次）；
   - 每一挥（水鸟/命定每段、咒血斩击、黄金碎击落地一击）调用 `swing(player)`：`swing(hand, true)` 广播给其他玩家 **+** 只发给施法者自己的 `ClientboundAnimatePacket`（原版 `swing(hand,true)` 不含自己，所以第一人称手臂纹丝不动）；
   - 新增 `JumpTrace`（起跳点 / 最高点 / 落点 / 空中 tick / 是否真的离地 / 起跳时 onGround 与实际垂直速度）与 `VFX_JUMP_START`/`VFX_JUMP_SETTLE` 日志，仅供夹具与诊断；
   - 新增**负例开关** `eldencombat.vfxLegacyHop`（仅 `vfxSmoke` 下有效）：恢复旧的原地蹦参数。
4. `testing/VfxSemanticsHarness.java`（新，随 `*Harness*` 排除出发布 JAR）—— 动作语义夹具，见 §3。
5. `build.gradle`：新增 `-PvfxSemantics` / `-PvfxSemanticsCases` / `-PvfxLegacyHop`，`vfxSmoke` 门禁要求对应完成标记 `VFX_SEMANTICS_COMPLETE:`。

## 3. 新门禁：动作语义夹具（补上此前完全缺失的一类证据）

`runClient -PvfxSmoke -PvfxSemantics`，8 个技能串行，**不拍差分图**，只做四条确定性断言（日志 `VFX_SEMANTICS_STAGE`）：

1. **每一次挥击都必须真的出图**：整段保持 `NORMAL` 层（= 玩家真实观感，不做任何诊断抑制），逐帧登记每个效果实例身份
   （`wave/<id>` / `effect/<id>/<phase>`）**是否真实提交过作者美术**；本段出现过的每个实例至少要有一帧真的提交，缺一个点名失败。
   （旧夹具只对"第一个提交的实例"取一次样，其余实例常常整段落在诊断层里 —— 既没证真也没证伪。）
2. **施法者每一次挥击都要有动作**：逐 tick 统计本地玩家 `swingTime` 起手次数，不少于该段实际挥出的段数。
3. **突进而不是原地蹦**：读服务端 `JumpTrace`，要求 `departed=true`、`apex-起跳Y ≥ 0.25`、
   `settleTick-launchTick ≥ 5`、**最小水平位移**（水鸟 ≥ 2.0 格、命定 ≥ 1.2 格、黄金碎击 ≥ 0.3 格等），
   以及**水平位移 ≥ 1.2 × 起跳高度**（"蹦"与"突进"的判别式：垂直冲量再小，只要人几乎不往前，观感就是原地蹦）。
4. **每一段都要打到正前方的受击靶**：正前方 3 格偏侧 0.9 格放一个不动、不烧、500 血的僵尸，
   统计 `cast.resolvedChannelCount()`（不同伤害段数）——水鸟/命定要求 **3/3**。

实测（最终通过运行，`-PvfxSmoke -PvfxSemantics`，exit 0，8/8 技能，含 1.2× 比例门禁）：

| 技能 | 实例覆盖 | 命中段 | 挥击次数 | 最高点 | 前冲 | 前冲/起跳 | 空中 tick |
|---|---|---|---|---|---|---|---|
| gold_breaker | 1/1 | 1/1 | 1 | 83.21 (+2.21) | 2.83 | 1.28 | 15 |
| destined_death | 3/3 | **3/3** | 3 | 81.87 (+0.87) | 1.35 | 1.55 | 10 |
| waterfowl_dance | 3/3 | **3/3** | 3 | 82.51 (+1.51) | 2.74 | 1.81 | 12 |
| cursed_blood_slice | 4/4 | 2/1 | 2 | 81.50 (+0.50) | 1.09 | 2.18 | 8 |
| starcaller_cry | 2/2 | 1/— | 1 | 82.59 (+1.59) | 0 | — | 12 |
| bloodboon_ritual | 3/3 | 1/1 | 0 | — | — | — | — |
| takers_flames / wave_of_gold | 1/1 | — | 0 | — | — | — | — |

> 夹具逐次波动（多次运行实测范围）：起跳高度 +0.50…+2.21 格、前冲 1.09…3.19 格——
> 波动来自"起跳冲量在服务端落地报值前后到达"的时序差，门禁阈值取的是这个范围的下端（水鸟前冲 ≥2.0 格、
> 命定 ≥1.2 格、比例 ≥1.2×），所以判定是保守的；出现临界值时以"参数再往前一点"为准，不放宽门禁。

## 4. 为什么以前那么多轮都没发现（夹具结构性盲区）

同一份代码、同一次施法，只改**相机实体**：

- 固定观察机位（相机实体 = 盔甲架，即此前 13 段受控夹具的机位）：`apex=81.000`、`horizontal=0.000`、`ticks=6` —— 玩家**完全没动**，
  服务端发出的技能位移没有作用到玩家身上（实测 `VFX_SEMANTICS_MOTION`：全程 `y=81.00`）。
- 相机给回施法者自己：`apex=82.750`、`horizontal=3.188`、`ticks=13` —— 位移、起跳、落地全部出现。

结论：**旧夹具只证明"作者美术有没有画上屏、像素归谁"，它的机位让"玩家位移"这一类现象恒为 0**，
所以"原地蹦 / 三段捞空 / 落地特效早于落地"在旧体系里既没有门禁、也没有可观测量。
另外，旧体系里 `MIN_SUBJECT_PIXELS=300` 曾把水鸟（103 像素）判失败，当时的处置方向是"换更近的机位" —— **那是错的方向**：
103 像素正是"作者美术在屏幕上几乎看不见"的真实信号，人类反馈与它一致。

## 5. 诚实的边界（未完成 / 需人类定调）

1. **第一人称像素级归因尚未做**：本轮证明的是"每个实例在自己存活的每一帧都真实提交了作者美术"，
   还没有在施法者自己的第一人称画面里做 `PRIMARY_ONLY ↔ CLEAN` 配对的像素差分。
2. **"看起来没特效"的定调需要人类**：水鸟作者美术在 854×480 下只占约 103 像素（对比黄金波 2012 像素）。
   两条可选路线（我给出取舍，不自行改美术）：
   - 美术侧：提高该路由的 `scales` / 加粗亮部（不改 PNG，属于路由参数，需统筹排美术）；
   - 代码侧：给水鸟/命定补一条程序化核心亮条（`SkillEffectMesh` 的既有能力），与作者美术叠加需先确认不与"作者美术接管"原则冲突。
3. 负例（`-PvfxLegacyHop` 恢复旧参数）用于证明新门禁真的抓得住缺陷，结论见 §6。
4. 第三人称/多人观感仍由人眼确认；`VfxDrawPlan`/`VfxShotComparison` 等 V1–V3 既有成果未改动。

## 6. 负例与终态

**负例**（`runClient -PvfxSmoke -PvfxSemantics -PvfxLegacyHop`：同一次运行里恢复旧起跳/连段参数）exit 1，恰好抓住人类反馈的两个症状：

| 负例失败项 | 实测 |
|---|---|
| `gold_breaker`：位移/高度比不足 | 前冲 1.64 格 vs 起跳 1.71 格（0.96×，要求 ≥1.2×）→ 垂直蹦 |
| `destined_death`：位移/高度比不足 | 前冲 1.83 格 vs 起跳 2.36 格（0.78×）→ 垂直蹦（旧 `apex` 比新参数高 0.85 格） |
| `waterfowl_dance`：三段斩击只中 2 段 | `channels=2/3` → 第 3 段在空中捞空 |

**终态**：

- `gradlew test`：223 项通过（新增 2 项 `SkillLandingTest` 离地锁回归：`staleOnGroundRightAfterTakeoffIsNotALanding`、
  `stalledTakeoffSettlesAsLandingNotTimeout`）。
- `runClient -PvfxSmoke -PvfxSemantics`：8/8 技能、exit 0，完成标记 `VFX_SEMANTICS_COMPLETE:`。
- 证据目录：`docs/combat-evidence/vfx-semantics-correction-20261017/`
  （通过运行的隔离日志与门禁行、负例运行日志、三张旧构建现场截图 + clean 对照帧、夹具盲区运行日志）。
- 备份：`docs/code-backups/20261017-semantics-dsh/`（改动前的 7 个文件）。

## 6.1 顺带查出的发布产物缺陷（已修 + 已加门禁）

重建 JAR 时发现：`jar` 任务只按**具名清单**排除夹具类，`VfxFixedHarness`（V1–V3 那轮新增）与本次的
`VfxSemanticsHarness` **都被静默打进发布包**（实测 `jar tf` 里 8 个 `*Harness*` 条目）。
索引此前"发布 JAR 不含任何 `*Harness*` 夹具类"的表述与实测不符。

- 修正：排除规则改为 `exclude 'com/dsh/eldencombat/testing/*Harness*'`（含 `$` 内部类），具名清单保留；
- 新增自检任务 `gradlew verifyJarHasNoHarness`：JAR 里出现任何 `*Harness*` 条目即非零失败（实测 OK，1179 条目 / 0 夹具）。

新的发布 JAR：`build/libs/eldencombat-0.2.0.jar`，SHA256
`5FDD235A3F6E85EC15F79FBFB0B30D997DC22809B1A9EE581A8200B88DC95A74`（旧值 `07A14CEB…` 作废）。

## 7. 复现命令

```powershell
$env:JAVA_HOME='D:\JDK21'; Set-Location D:\codexout\mcmod
# 单测
cmd /c ".\gradlew.bat test --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21"
# 动作语义门禁（8 技能，exit 0 才算过）
cmd /c ".\gradlew.bat runClient -PvfxSmoke -PvfxSemantics --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21"
# 负例：恢复旧的"原地蹦"参数，门禁必须失败
cmd /c ".\gradlew.bat runClient -PvfxSmoke -PvfxSemantics -PvfxLegacyHop --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21"
```
