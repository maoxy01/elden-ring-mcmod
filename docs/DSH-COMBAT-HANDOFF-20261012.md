# dsh 战斗接手说明

日期：2026-10-12。用户最新分工：**Codex 负责美术优化，dsh 负责战斗。**
工作区：`D:/codexout/mcmod`。这份说明是本次分工和现状摘要；旧报告中的进度、参数和分工不能直接当作当前状态。

## 1. 分工边界

| 范围 | 负责人 | 边界 |
| --- | --- | --- |
| 战技输入、武器绑定、普攻连击、技能阶段、追击窗口、耗蓝、精力、判伤、削韧、锁敌、碰撞与位移、中断及联机同步 | dsh | 维护战斗代码和参数，优先沿用现有 Epic Fight 接入，不另起第二套战斗引擎 |
| 武器模型、贴图、握持外观、玩家动作的姿态美术、特效几何/材质/渲染与预览 | Codex | 不再独立修改战斗数值、服务端阶段或判伤规则 |
| 动画时长、出手帧、武器挂点、特效触发和位置 | 共同接口 | dsh 给出时间线和事件契约，Codex 制作相匹配的动作与特效；改动前先交接，不并行覆盖同一文件 |
| 游戏验收 | 用户 | 进入游戏供用户手动测试；日志与自动测试是辅助证据，不能替代用户视觉和手感验收 |

建议 dsh 主要维护：`src/main/java/com/dsh/eldencombat/server/skills/`、`compat/skills/`、`compat/RemembranceWeaponPresets.java`、战斗输入/网络接线，以及 `src/main/resources/data/eldencombat/` 的战斗能力与参数。

Codex 主要维护：`art/remembrance-game/`、`art/weapons/`、`art/skill-fx/`、`effects/SkillEffectMesh.java`、`client/effects/`、武器渲染资产和姿态生产工具。

共享文件：`compat/animations/`、`entity/skill/GoldenWaveEntity.java`、`entity/skill/SkillEffectEntity.java`、`effects/WaveFront.java`、动画 JSON、`tools/dsh-anim/specs/`、`LocalSourceMotion.java` 和构建配置。共享不代表双方可以同时随意改动：先记录要改的字段/方法，交付后由另一方同步对应资源。

## 2. 用户已确认的要求

- 以 Epic Fight 为基础修改；专属战技不能变成钢铁旋风等不相关的默认技能。
- 战技消耗共享蓝条 FP，不要求积攒怒气或打人充能；普通攻击精力继续与 EF 整合。
- 所有战技收招后摇应短，不能把整段动作统一倍速来掩盖长后摇。
- 普攻按武器模板区分，大剑、特大剑等应有各自连贯的连击动作。当前不能宣称所有类型都已有定制连段。
- 不做处决；仅指定战技允许跃起，不因此自动开放普通跳跃攻击。
- 保留玩家自己的皮肤。方块人下肢不照搬真人的大幅髋扭转、交叉腿和深屈膝；不要为了适配动作改瘦玩家腿部模型。
- 特效以自绘方块化网格为主，不用简单粒子代替主要效果。授血仪式保留正方形光圈。
- 黄金波动先中间快、两边慢；达到指定弧形后，各部分固定相对位置，整片等速推进。不能让两翼一直减速或继续扩张。
- 测试时无限蓝、手动施放、不自动退出；正式玩法仍正常扣 FP。
- Boss 暂不作为本轮重点，先完善武器和战斗。

## 3. 当前实际状态

运行基线：Minecraft 1.21.1、NeoForge 21.1.219、Epic Fight 21.17.3.1、Java 21（`D:/JDK21`）。构建依赖为 `curse.maven:epic-fight-mod-405076:8175609`。

已有八个战技运行逻辑、服务端 FP、施放阶段、同步、专属 preset/moveset 绑定和十段原创原型动画（八主段加两追击段）。这些是可测试基线，不是八项均已还原法环或均已验收。

不要回退到旧方案的物品级 `innate_skills` 写法；当前 EF 版本的这条绑定路径不生效，现有专属 preset/moveset 路径已有手动测试绑定日志。

### 当前时间线

下表为正常资源版参数，单位 tick，20 tick = 1 秒。前摇、生效和伤害尚未在本轮重新平衡；刚完成的是后摇缩短。

| 战技 ID | 首段前摇 / 生效 / 后摇 | 追击 |
| --- | --- | --- |
| wave_of_gold | 10 / 4 / 5 | 无 |
| gold_breaker | 8 / 16 / 5 | 无 |
| destined_death | 8 / 24 / 5 | 技能内部多段，不是额外输入追击 |
| takers_flames | 9 / 4 / 5 | 无 |
| waterfowl_dance | 10 / 36 / 5 | 技能内部多段，不是额外输入追击 |
| starcaller_cry | 8 / 6 / 5 | 6 / 8 / 5；输入窗 12 |
| bloodboon_ritual | 6 / 4 / 2 | 6 / 4 / 2；输入窗 10，最多三段 |
| cursed_blood_slice | 8 / 6 / 5 | 4 / 4 / 5；输入窗 10 |

参数来源：`src/main/resources/data/eldencombat/dsh_skills/remembrance_parameters.json`；回退值在 `RemembranceSkillSpec.defaults()`，二者必须同步。

**本地源动作测试是另一条时间线：**黄金波动 `40 / 8 / 6`，总长 2.7 秒，第 2 秒出波，第 2.4 秒开始收招。由 `build/elden-source/retargeted/profile.json` 覆盖，仅在 `-PmanualSkills -PsourceMotion` 双开关下启用。不要用正常资源版的 10 tick 前摇驱动这个测试动作。

只有黄金波动已进行本地源动作重定向；其余七项仍是原创原型姿态。黄金波动 R2 骨盆固定，左右大腿外展 4 度，下肢不导入源髋/膝扭转，上半身保留挥剑。武器轴向标定仍需用户实机验收。

## 4. 动作、判伤与特效契约

- `SkillDriver` 是服务端阶段入口，`RemembranceRuntime` 组织其余战技的运行行为；黄金波动入口还应检查 `compat/skills/WaveOfGoldSkill.java`。
- 现有 `RemembranceAttackAnimation` 用 EF 播姿态和动作状态；覆盖 `attackTick()` 防止 EF 再自动结算一遍伤害，覆盖 `move()` 防止与服务端 `SkillMotion` 重复移动。修改这些覆盖时必须证明不会双伤害/双位移。
- 动画和服务端统一用前摇、生效、后摇时间线。修改参数后，同时重排动画相应段、触发帧和测试，不能仅改 JSON 数字后仍播放旧长动作。
- 特效不做判伤。服务端给出类型、起点/方向、阶段、半径、寿命和施放归属，客户端只画表现；禁止客户端上传命中目标作为最终伤害依据。
- `SkillEffectEntity` 为同步的纯视觉实体。跟随施放者的效果应校验同次施放；固定落点的地面效果不能跟着玩家跑。
- `GoldenWaveEntity` 兼具波的服务端命中推进与同步数据，属于共享文件。当前画面使用半径、传播距离和寿命进度插值；不要在战斗修改中删除这部分表现数据。
- `WaveFront` 同时约束视觉与命中波面：16 lane、4 格成形距离、0.125 格阶梯。改变形状/遮挡/传播规则时，必须与美术同步，并保留成形后固定偏移的测试。
- 多段重复伤害和追击输入按同次施放 ID、阶段与命中预算去重；死亡、换武器、受击、离线、换维度、超时等退出路径应完整清理。

美术当前已实现亮芯、短渐入、亮度保持、末段淡出；网格上限每效果 96 方块，绘制跳过不足一档透明度的面，并取消面级临时数组。这不是帧率提升已测得的声明。新增大范围/多次特效先提供同时在场数量及寿命预算。

## 5. dsh 优先工作

1. 先读取当前代码和最新日志，不按历史报告重建已有功能。列出八战技的真实绑定、按键入口、动作选择、有效命中和中断状态，区分已实现与已验收。
2. 检查缩短后摇后，服务端施放锁、EF 动作状态和移动恢复是否一致。特别检查追击窗与收招不是同一概念，不能为了缩短收招删掉合法追击；也不能残留动作已结束但状态无故卡住的问题。
3. 完善专属战技行为、命中范围、连段去重、碰撞安全与锁敌。锁敌和方块破坏可以研究用户提供模组的行为，但不要直接依赖或复制未确认来源的实现，不默认打开大范围地形破坏。
4. 按武器模板制定普攻连击契约：每段动画 ID、时长、有效窗口、连击输入/缓存窗、取消窗、位移上限、collider 和精力消耗。提交给 Codex 做姿态打磨，先覆盖大剑、特大剑、刀、大曲剑、大矛及槌。
5. 每个战技提交事件表：蓄力、挥击/刺击、命中、出波、落地、追击和收招恢复所在的 tick，附位置空间与跟随规则。Codex 据此制作美术，不让两边猜触发时机。
6. 做单元测试和用户手动验收，再验证两人联机同步。不要把绑定通过、离线 FK 通过或日志出现 STARTED 写成完整战技验收通过。

## 6. 测试入口与证据

```powershell
# 正常构建和回归测试
.\gradlew.bat build --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'

# 无限蓝，原创资源动作，手动验收
.\gradlew.bat runClient -PmanualSkills --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'

# 无限蓝，本地黄金波动源动作候选，手动验收
.\gradlew.bat runClient -PmanualSkills -PsourceMotion --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

游戏与日志：`build/manualSkills/`、`build/manualSkills/logs/latest.log`。不要同时运行两个客户端打开同一个测试存档；需要重启时等用户退出，不强杀游戏。

最近构建：53 项 Java 测试通过；5 项原始动作检查、6 项重定向检查通过。十段原创动作的前摇/攻击段 1,024 个关键帧与改前一致，只压缩收招。

2026-10-12 12:37:50 测试日志出现 `MANUAL_SKILLS_READY` 和八条 `MANUAL_BINDING_PASS`，FP=100。这仅证明进入世界、绑定和测试准备，不代表八战技均完成验收。

原始/转换动作仅留在 `build/elden-source/` 供本地对照，不修改法环安装，不打进 JAR 或整合包。发布用原创资源。`LocalSourceMotion` 有路径、大小、SHA256、关节和时间线校验，不为方便调试移除这些校验或默认开启本地加载。

## 7. 交付格式

dsh 完成一轮后更新战斗报告，包含修改文件、八战技状态、实际耗蓝/阶段/命中规则、普通攻击模板覆盖、给 Codex 的动作和特效事件表、测试结果、用户待验收项及遗留问题。不要把旧版参数表当最新表复制。

相关资料：

- `docs/LOCAL-SOURCE-MOTION-TEST.md`：R2 当前动作/特效及 R1 历史记录。
- `docs/CODEX-REMAINING-SKILLS-IMPLEMENTATION-REPORT.md`：八战技接入和绑定修复。
- `docs/ELDEN-RING-ANIMATION-EXTRACTION-REPORT.md`：本地源动作检查与隔离要求。
- `docs/GAME-WEAPONS-ASSETS-HANDOFF.md`：游戏版武器资产契约。
- `docs/EF-WEAPONS-IMPLEMENTATION-PLAN.md`、`docs/DSH-SKILLS-IMPLEMENTATION-REPORT.md`：历史设计与交付，不是最新进度真源。

源码/原创动作改前备份：`docs/code-backups/20261011-recovery-fx-r2/`。本地源动作 R1 备份另在 `build/elden-source/retargeted-backups/20261011-recovery-fx-r1/`。不要覆盖其他 agent 已做的改动。
