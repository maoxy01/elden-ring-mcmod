# 证据：赐福 pending 清理 + rest 规则（2026-10-13 22:25 复跑）

对应台账：`docs/DSH-THREE-SYSTEMS-PROGRESS-20261013.md` §1.4、§1.5、§2.4。
本轮**唯一**改动的生产文件是 `server/grace/GraceService.java`（新增 3 个诊断/脚手架方法 + 2 处日志），
其余改动都在夹具（`testing/`）里。

## 0. 结论

1. §1.4 的 5 条清理分支里 **4 条已验证**：超时、目标节点被删、切维度（作废）、切维度（保留）。
   **未验 2 条**：掉线、关服——单玩家夹具制造不出真实掉线；关服清理由 `ServerStoppedEvent` 完成，
   之后没有可断言的运行时。
2. §1.5 rest 规则**已验证**：默认规则 FP=FULL、生命/精力/药瓶=KEEP，实测"只补 FP、其余不动"。
3. §2.4 与 §2.3 是同一批菜单断言，随 22:25 复跑再次通过，状态由 `TODO` 改为**已验证**。
4. ⚠ **同时发现红项**：`-PashSmoke` 两次实跑不一致（一次"秒杀测试目标"、一次"没有造成伤害"）。
   原因是夹具把"造成伤害"钉在固定 step 45（客户端 tick），而判定走服务端 tick。
   改法已提交（目标血量 20→200、断言移进轮询相位）但**未复跑**，复跑命令见 §4。

## 1. 命令 / 退出码 / 日志

命令模板：`$env:JAVA_HOME='D:\JDK21'; cmd /c ".\gradlew.bat <任务> --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21 > build\logs\<日志>.txt 2>&1"`

| # | 任务 | 退出码 | 日志 | 关键标记 |
|---|---|---|---|---|
| 1 | `compileJava compileTestJava` | 0 | `build\logs\grace2-compile.txt` | BUILD SUCCESSFUL |
| 2 | `test` | 0 | `build\logs\grace2-test.txt` | 26 套 / 178 用例 / 0 失败 / 1 跳过（`build\test-results\test\*.xml`） |
| 3 | `runClient -PgracePrototype -PgraceMenu` | 0 | `build\logs\grace2-menu.txt` | `GRACE_MENU_COMPLETE` + 下列 6 条新断言 |
| 4 | `runClient -PclientSmoke` | 0 | `build\logs\grace2-smoke.txt` | `CLIENT_NETWORK_PASS: all network checks completed` |
| 5 | `runClient -PclientSmoke -PclientSmokeReload` | 0 | `build\logs\grace2-reload.txt` | `TALISMAN_RESTART_PASS`、`CLIENT_PERSISTENCE_COMPLETE` |
| 6 | `runClient -PashSmoke` | 0 | `build\logs\grace2-ashsmoke.txt`、`grace2-ashsmoke2.txt` | ❌ `ASH_SMOKE_FAIL`（红项，见 §0.4） |
| 7 | `runServer -PserverSmoke` | 0 | `build\logs\grace2-serversmoke.txt` | `SERVER_SMOKE_PASS: 428 shared checks`、`SERVER_SMOKE_COMPLETE` |

> 判据提醒：夹具失败只写 `*_FAIL`，gradle 仍返回 0。#6 就是反例——**必须先查日志标记**。

## 2. 原文摘录（`build\logs\grace2-menu.txt`）

```
GRACE_MENU_REST_PRECOND: health=7.0/20.0 stamina=1.0 flasks=0（默认规则：FP=FULL，其余 KEEP）
GRACE_REST_APPLIED: … fp=100.0 fpRule=FULL healthRule=KEEP staminaRule=KEEP flaskRule=KEEP
GRACE_MENU_REST_KEEP_PASS: 休息只按配置补 FP；生命 9.0/20.0、精力 31.0、药瓶 0 都按 KEEP 保持未满
GRACE_PENDING_OBSERVED: 真实旅行请求走完后仍挂着的 pending 票据数=0
GRACE_PENDING_TIMEOUT_ARMED: 挂一张 age=1202tick 的票据（上限 1200），等 sweep 清理
GRACE_PENDING_EXPIRED: player=380df991-… age=1233ticks limit=1200ticks
GRACE_PENDING_TIMEOUT_PASS: 超时票据被 sweep 清掉（目标节点仍在，只有超时判定能清它）
GRACE_PENDING_DELETE_ARMED: 临时赐福 909e0e20-… 的方块已删除，等 sweep 作废票据
GRACE_PENDING_INVALIDATED: player=380df991-…
GRACE_PENDING_DELETED_PASS: 目标赐福在旅行途中被删 → 票据立刻作废（不等超时）
GRACE_PENDING_DIMENSION_INVALIDATED: … from=minecraft:overworld to=minecraft:the_nether targetDim=minecraft:overworld
GRACE_PENDING_DIMENSION_PASS: 切到非目标维度 → 票据作废（事件级验证，不是真切换维度）
GRACE_PENDING_DIMENSION_KEEP_PASS: 目的维度=目标维度 → 票据保留（不抢在 Complete 前吃掉它）
GRACE_MENU_COMPLETE: engine fixture (programmatic button press); real mouse clicks still unverified
```

红项原文（`build\logs\grace2-ashsmoke.txt` / `grace2-ashsmoke2.txt`）：

```
ASH_SMOKE_SETUP: catalog=3 ash=eldencombat:stamp_upward_cut fpCost=11.0 目标血量=20.0
ASH_SMOKE_FAIL: 测试目标应仍然存活（单段一击不该秒杀）
ASH_SMOKE_FAIL: 普通战灰运行时没有造成伤害（20.0 -> 20.0；玩家 (0.0, 81.0, 0.0) 朝向 0.0，目标 (0.5, 81.0, 2.5)）
```

## 3. 本轮改动

| 文件 | 变更 | 类型 |
|---|---|---|
| `server/grace/GraceService.java` | `pendingCount()` / `hasPending()`（诊断出口）；`debugHoldPending()`（**夹具脚手架**，挂票不传送）；切维度作废补日志；修 `GRACE_PENDING_EXPIRED` 把服务端 tick 当"存活时长"打印的错误 | 生产代码 |
| `testing/GraceClientPrototypeHarness.java` | §1.5 前置+断言；§1.4 四个相位（超时 / 删除 / 切维度作废 / 切维度保留） | 夹具 |
| `testing/AshSmokeHarness.java` | 目标血量 20→200；"造成伤害"断言移进轮询相位 | 夹具（**未复跑**） |
| `docs/code-backups/20261013-grace-pending/` | 上述三文件改前副本（无 Git，改前备份） | 备份 |

## 4. 复跑（自检）

```powershell
cd D:\codexout\mcmod
$env:JAVA_HOME='D:\JDK21'
cmd /c ".\gradlew.bat runClient -PashSmoke --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21 > build\logs\check-ash.txt 2>&1"
# 通过判据：日志里有 ASH_SMOKE_HIT_PASS + ASH_SMOKE_COMPLETE，且全文无 _FAIL
```

## 5. 已知夹具限制（写进结论，别当成玩法缺陷）

- `debugHoldPending` 是**脚手架**：它借真实节点/真实 `createDefaultTeleportContext` 造票，
  但不真正发起传送。真实旅行请求的票会在异步完成回调里立刻被回收（本轮 `pendingCount()=0` 就是
  异步完成后的正常态），所以清理分支必须靠脚手架制造"挂着的票"才能验。
- 切维度用的是**事件级**验证：只投递 `PlayerChangedDimensionEvent` 驱动处理器，
  不做真实维度切换（真实切换会把夹具拖进原版 `isChangingDimension` 无敌态）。
- 夹具自己的临时赐福放在 `(60,81,0)` 且不注册进 `GraceRegistry`，删掉它不影响菜单/地图断言。
