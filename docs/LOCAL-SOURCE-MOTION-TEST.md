# 黄金波动：本地原始动作适配测试

2026-10-11，当前状态：**R2 已构建并通过离线验证，等待新的游戏验收。黄金波动缩短后摇并固定下肢；所有战技均缩短后摇。其余七个战技仍使用原创原型动作，尚未接入原始动作。**

## R2：后摇、下肢与特效

- 本地黄金波动保留 40 tick 起手和 8 tick 生效，仅将原收招段压缩到 6 tick（0.3 秒），总时长 2.7 秒。第 2 秒出波事件不变，第 2.4 秒后加速收招。
- 黄金波动骨盆固定为 EF 绑定姿态，取消源动作骨盆上下摆动与髋部扭转；左右大腿仅外展 4 度，小腿与辅助膝关节跟随稳定站姿。不修改玩家皮肤、腿部网格宽度或关节长度。上半身继续跟随源动作。
- 分发资源中的八个战技：七项收招 10 -> 5 tick；授血仪式 4 -> 2 tick。默认参数与 JSON 同步。两个专属追击动画也更新；不缩短追击输入窗口、不改变耗蓝、伤害或命中时间。
- 更新十个原创动画资源的收招段。备份对照确认前摇/攻击段 1,024 个关键帧的时间及姿态不变。
- 黄金波动亮芯贴合波面；水鸟刀光增加宽带与亮芯，斩击增加细亮芯。保持方块化风格、授血仪式正方形光圈，以及黄金波动成形后固定相对形状的规则。
- 所有网格特效使用短渐入、主体亮度保持、末段渐隐；移动波面使用同步进度与逐帧插值，不再硬编码恒定进度。特效不承担判伤。
- 特效绘制取消每个面的临时 varargs 数组，跳过不足一档透明度的面；仍限制每个特效最多 96 个方块。不宣称已测得帧率提升。
- `build` 成功，53 项 Java 测试、5 项原始解码检查、6 项重定向检查通过。新站姿全帧稳定，手臂方向验证及关节长度检查通过。真实方块人遮挡、持剑和手感仍由游戏验收。
- 新本地动作 SHA256：`30D9547B17269E0BA561FB5F05F97FC00C22A9E1B96396623A1667C40BE0FFB8`。
- 22:59:06 R2 手动测试世界出现 `MANUAL_SKILLS_READY`，八件武器绑定检查通过，FP=100。使用 `-PmanualSkills -PsourceMotion`，无限蓝、无自动施放/退出；新本地动作的首次实际加载仍需用户施放黄金波动后确认日志。启动有 EF 缺失字幕/可选披风层及联网失败信息，尚未发现本次修改关联的异常。
- 源码/原创资源备份：`docs/code-backups/20261011-recovery-fx-r2/`；R1 本地派生动作备份：`build/elden-source/retargeted-backups/20261011-recovery-fx-r1/`。原始及派生动作继续仅留在 build，不进分发资源。

以下为 R1 的历史接入与验收日志；4.5 秒/42 tick 是旧版本数据，不代表当前 R2。

## 本次改动

- 以已验证的 `a820_040000.hkx` 解码结果生成 EF attributes 动作，269 帧、20 个目标关节。
- 使用源/目标参考姿势及解剖方向校准左右、肩部、上臂、前臂。独立 FK 验证四条手臂方向与源动作最大差异小于 1 度。保留 EF 各关节的绑定长度、单位缩放及手腕挂点的局部平移。
- 武器挂点单独使用 `R_Weapon` / `L_Weapon` 的动画。当前将源武器 +X 对应 EF Tool +Y；这是基于骨架姿势的候选标定，尚未通过真实武器网格/游戏画面验收。离线线段只代表方向，不是神躯化剑模型。
- 不引入水平方向根位移、Coord 轨道、额外伤害、自动施放或处决。骨盆高度作为小幅模型姿势保留，实际实体位移仍由既有服务端逻辑控制。
- 原始约 4.467 秒时长取整到 90 tick（4.5 秒），约第 2 秒的 BulletBehavior 事件作为候选出波时机：40 tick 起手、8 tick 生效、42 tick 收招。既有黄金波动实体和方块化特效仍负责表现与判伤。
- 第 2 秒事件类型由 WitchyBND 的 ER TAE XML 模板核对；尚未追到原始 BulletParam / FXR 播放验证，不能据此宣称完全还原原游戏特效。

## 接入与隔离

`LocalSourceMotion` 仅在同时设置 `eldencombat.manualSkills=true` 和显式 `eldencombat.sourceMotionProfile` 文件路径时加载。本地 profile 把动画与时间线作为一个整体校验：SHA256、大小、路径边界、关节集合、严格递增时间、单位四元数、非根关节零位移、无水平根漂移、总时长一致；非法 profile 明确报错，不悄悄混用旧动作时间线。

`RemembranceAttackAnimation.loadAnimation()` 使用 EF 原生 `JsonAssetLoader` 加载本地快照。`RemembranceSkillSpec.load()` 仅修改黄金波动的时间线及超时预算，FP、伤害、射程和其余七个战技保持不变。缓存的 profile 在本次 JVM 生命周期中保持一致；修改后需重启测试。

原 `src/main/resources/.../wave_of_gold.json` 未替换。原始/派生动作保持在 `build/elden-source/`；新构建 JAR 包含通用的本地测试加载代码，但不包含这些动作或 profile。未发布新整合包；不要把本地提取数据或测试 profile 放进分发资源。

运行入口（仅本地隔离世界，不用于联机或正式存档）：

```powershell
pwsh -NoProfile -File tools/elden-source/Run-LocalSourceMotion.ps1
# 等价 Gradle 命令：
.\gradlew.bat runClient -PmanualSkills -PsourceMotion --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

游戏目录 `D:/codexout/mcmod/build/manualSkills`，日志 `D:/codexout/mcmod/build/manualSkills/logs/latest.log`。无限蓝沿用已有手动测试开关，未放宽正常单人或服务端 FP 规则。没有自动施放战技、自动退出或修改法环安装。

## 验证结果

- 离线 `build` 成功，51 项 Java 测试通过（新增 8 项本地动作文件/开关/参数测试，实际候选文件测试执行未跳过）。
- 5 项原始解码验证、5 项重定向验证通过。
- 独立 JS EF 动画读取/FK，与 Python 输出的全部 269 帧、20 个关节世界矩阵相符（容差 0.003，涵盖 EF 原始绑定矩阵的小幅非正交误差）。
- 22:44:20 测试世界进入并出现 `MANUAL_SKILLS_READY`，八件武器绑定日志均通过。
- 22:44:49 用户手动施放黄金波动，日志出现 `[source-motion] Loaded local wave_of_gold: 4.5s`；实际候选 SHA256 为 `C85A7F023FFEF4AEDDCC63B2D4E2E0413A41725EA87DC89DEBC03B3B606DED9B`。
- 22:44:49 / 22:44:56，两次 `accepted=STARTED skill=wave_of_gold` 的 FP 都为 100。证明实际输入进入服务端，并加载了新动作；不等于握剑、脚底、特效时机或命中已经得到视觉验收。
- 启动日志另有 EF 联网失败和 wooden_sword 数据反序列化警告；当前未发现关联新动作的加载异常。后续用户报告问题时继续检查此日志。

离线对照图：`build/elden-source/retargeted/wave_of_gold.ef-check.png`。数据及转换记录：`profile.json`、`retarget-report.json`、`wave_of_gold.preview.npz`、独立 FK 数据 `wave_of_gold.fk.json`。

相关脚本：`Retarget-WaveOfGold.py`、`Test-RetargetedMotion.py`。旧源码备份：`docs/code-backups/20261011-source-motion-local/`。

## 下一步

用户验收黄金波动的持剑方向、横扫是否穿身、脚底是否漂浮以及出波是否贴合挥剑。通过后，再按每个战技的实际分段、握持和根位移处理其他七个动作；不把本次单段黄金波动的配置直接套到所有战技。
