# Harness 收口验证记录（2026-10-13 18:09）

范围：`SkillDriver` 参数缺失统一清理 / `SkillCasting` 事务异常边界 / 客户端断线重连残留。
本文件只记录**本轮真实跑过的**检查，以及**没跑**的部分。

## 命令

```powershell
$env:JAVA_HOME='D:\JDK21'
.\gradlew.bat test build --offline --no-daemon --console=plain "-Dorg.gradle.java.home=D:/JDK21"
```

退出码复核（同一条命令，绕过 PowerShell 把 stderr 当错误记录的干扰）：

```powershell
cmd /c ".\gradlew.bat test build --offline --no-daemon --console=plain -Dorg.gradle.java.home=D:/JDK21 > nul 2>&1"
gradlew exit code = 0
```

## Gradle 控制台输出（摘录，本次运行）

```
> Task :createMinecraftArtifacts UP-TO-DATE
> Task :compileJava
> Task :generateEquipment2d UP-TO-DATE
> Task :syncArt UP-TO-DATE
> Task :processResources UP-TO-DATE
> Task :classes
> Task :jarJar NO-SOURCE
> Task :jar
> Task :compileTestJava
> Task :processTestResources NO-SOURCE
> Task :testClasses
> Task :test
> Task :assemble
> Task :compileSkillFxExporter
> Task :exportSkillFx
Exported active native effects into D:\codexout\mcmod\art\skill-fx\meshes.js
> Task :check
> Task :build

BUILD SUCCESSFUL in 14s
10 actionable tasks: 6 executed, 4 up-to-date
```

唯一告警是既有的弃用提示（本轮之前就存在，与本次改动无关）：

```
EldenCombatClient.java:30: 警告: [removal] EventBusSubscriber 中的 bus() 已过时, 且标记为待删除
2 个警告
```

## 单测聚合（`build/test-results/test/*.xml`）

```
测试类 20 / 用例 138 / 失败 0 / 错误 0 / 跳过 1
```

跳过的那 1 个是 `LocalSourceMotionTest` 里既有的 skip，不是本轮引入。

```
com.dsh.eldencombat.SkillCommitBoundaryTest          7   0   0   0
com.dsh.eldencombat.RemainingSkillsTest              9   0   0   0
com.dsh.eldencombat.RemembranceSkillsTest           17   0   0   0
com.dsh.eldencombat.SkillLandingTest                 9   0   0   0
com.dsh.eldencombat.WeaponProgressionTest           17   0   0   0
...（其余 15 个类全绿，逐类数字见 build/reports/tests/test/index.html）
```

## 本轮新增用例（`SkillCommitBoundaryTest`，7 条全绿）

```
  refundingTheReportedChargeRestoresExactlyOnce()            0.001s
  reportedChargeAlwaysEqualsTheActualDeduction()             0.001s
  unlimitedFpReportsZeroSoRollbackNeverMintsFp()             0.0s
  abortClosesTheFollowupWindowAndRetiresTheOldCastId()       0.005s
  clearCooldownOnlyTouchesTheNamedSkill()                    0.0s
  followupChargeIsReportedExactly()                          0.001s
  rejectedCommitChargesNothing()                             0.0s
```

这七条锁的是**回滚路径的不变量**（报告金额 == 实际扣减、退款精确且只能退一次、
中止后追击窗口关闭且旧 castId 作废、清冷却不误伤其它技能）。它们不启动 Minecraft，
因此不能替代游戏内验收。

## 产物

```
build/libs/eldencombat-0.2.0.jar   1,075,992 字节   2026-10-13 18:09:37
```

## 客户端 smoke（`runClient -PclientSmoke`）—— 通过

日志：`docs/combat-evidence/client-smoke-harness-abort-20261013.log`（58 KB）。退出码 0，`BUILD SUCCESSFUL in 2m 59s`。

```
CLIENT_SMOKE: 428 shared checks passed
CLIENT_SMOKE_PASS: all 33 visual fixtures rendered
CLIENT_NETWORK_PASS: all network checks completed
```

与本轮改动直接相关的夹具（全部 PASS，逐条来自日志）：

- `cancel restored ordinary gravity`、`weapon switch forced cancellation`、`weapon cancellation removed emitted wave`
  —— 取消路径（`SkillDriver.cancel` 加了 try/catch 之后）仍然清干净；
- `respawn has no transient cast`、`death removed emitted wave`、`dimension transfer cleared cast and gravity`
  —— 死亡/换维度的 detach 路径无残留；
- `jump skill ... landing safety net observed the landing after the cast ended (LANDED)`、
  `player is back on solid ground, not stuck in the air or in the void`
  —— `onFinish`/`onAbort` 共用的 `parkOrphan` 没破坏 §4 安全网；
- 八件追忆战技逐条 `real key accepted` / `caused actual target damage` / `returned to idle` / `charged initial input once`
  —— 每段只扣一次费，与改动前一致；
- 4 组环境夹具（低顶/台阶/液体/悬崖）与 lifecycle 全绿。

失败/异常扫描：`FAIL|ERROR|Exception` 在整份日志里 **0 命中**。

## 持久化重启（`runClient -PclientSmoke -PclientSmokeReload`）—— 通过

日志：`docs/combat-evidence/client-smoke-reload-harness-abort-20261013.log`。退出码 0，`BUILD SUCCESSFUL in 23s`。

```
CLIENT_PERSISTENCE_COMPLETE: actual save/shutdown/restart passed
```

（上一轮退出前写的存档：属性 7/3/0/0/0、+2 重质变直剑、FP37、无残留施放，重启后全部对上。）

## 专用服务器 smoke（`runServer -PserverSmoke`）—— 失败，原因与本轮改动无关

日志：`docs/combat-evidence/server-smoke-harness-abort-20261013.log`；FML 崩溃报告
`build/serverSmoke/crash-reports/crash-2026-10-13_18.16.44-fml.txt`。

```
net.neoforged.neoforge.logging.CrashReportExtender$ModLoadingCrashException: Mod loading has failed
Caused by 0: java.lang.NoClassDefFoundError: net/blay09/mods/waystones/api/Waystone
  at java.lang.Class.getDeclaredMethods0(Native Method)
  at net.neoforged.fml.javafmlmod.AutomaticEventSubscriber.lambda$inject$4(AutomaticEventSubscriber.java:60)
Mod file: /D:/codexout/mcmod/build/classes/java/main/
Exception message: java.lang.ClassNotFoundException: net.blay09.mods.waystones.api.Waystone
```

根因（已定位到具体类，不是推测）：`testing/GracePrototypeHarness.java:16` 是
`@EventBusSubscriber(value = Dist.DEDICATED_SERVER)`，其中 `WaystonesAPI.getWaystoneAt(...).orElseGet(() -> ...)`
的 lambda 合成方法**返回类型是 `Waystone`**；FML 的 `AutomaticEventSubscriber` 构造模组时会枚举该类的
所有声明方法，于是必须在类路径上解析 `Waystone`——没装 Waystones 的专用服务器直接整模组加载失败。
`build.gradle` 只在打发布 jar 时排除了 testing 包，`runServer` 用的是 `build/classes/java/main`，所以排除不生效。

这是**赐福原型**（`GracePrototypeHarness` 13:33 / `server/grace/*` 13:47 落地）引入的既有问题：
最后一次通过的专用服务器 smoke 是 `server-bow-final-20261013.log`（12:33），早于该原型。
本轮改动的 8 个文件里没有任何 Waystone 引用（可复核：`grep -i waystone` 只命中 `server/grace/*` 与两个 Grace 夹具）。
客户端不受影响的原因也清楚了：该夹具声明的是 `Dist.DEDICATED_SERVER`，客户端不扫描它——
这也解释了为什么客户端 smoke 能跑满 3 分钟而专用服务器一启动就崩。

**没有修**：赐福原型不在本轮工单内（交接文档明确"不要继续其工单"）。两条出路：
① 往 `build/modpack-dependencies/` 放 Waystones（`docs/MODPACK.md` 的完整命令是 `-PserverSmoke -PpackSmoke`，
但当前该目录里只有 cloth-config/entityculling/ferritecore/iris/jei/lithium/sodium，没有 Waystones）；
② 让 `runServer` 的类路径也排除 `com/dsh/eldencombat/testing/Grace*Harness*`。

## 因此：仍未验证的部分

下面这些只能算"代码已就位、行为未验证"（需要专门的注入场景，客户端 smoke 不含这些夹具）：

- 施放中参数丢失 → 效果实体与位移被释放、客户端收到结束快照；
- Runtime `onBegin` 抛异常 → 只退本次提交金额、清冷却、玩家看到"启动失败"提示；
- 断线/重连 → HUD 不再残留上一局的战技、姿势缓存不套到复用实体 id 的新实体上。

注入办法与预期日志见 `docs/DEEPSEEK-COMBAT-FRAMEWORK-HANDOFF-20261013.md`
末节"追加：Harness 收口三项缺口（2026-10-13 18:15）"→"本轮验证与局限"。
