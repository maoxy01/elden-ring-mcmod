# 赐福底座（依赖准入 / 启动路径 / 夹具注册）验证证据

日期：2026-10-13 20:05–20:10
工作区：`D:/codexout/mcmod`
对应任务：`docs/DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md` §1.1、§1.2（另含 §1.4、§1.5 实现）
备份：`docs/code-backups/20261013-grace-baseline/`（13 个文件的改动前副本）

## 执行命令与结果

| # | 命令 | 真实退出码 | 结论 |
|---|---|---|---|
| 1 | `.\gradlew.bat compileJava --offline --no-daemon` | **0** | `BUILD SUCCESSFUL in 10s`，4 条既有 API 弃用警告，无错误 |
| 2 | `.\gradlew.bat test --offline --no-daemon` | **0** | 20 个测试类 / **139 用例 / 0 失败 / 1 跳过** |
| 3 | `.\gradlew.bat runServer -PserverSmoke --offline --no-daemon`（**classpath 无 Waystones/Balm**） | **0** | 专用服务器正常启动并跑完 smoke |
| 4 | `.\gradlew.bat runServer -PgracePrototype --offline --no-daemon`（依赖齐备） | **0** | 适配器经**正式路径**装载 + 夹具显式注册 + 两个真实节点落盘 |

日志：`build/logs/compile-round1.txt`、`build/logs/test-round1.txt`、
`build/logs/server-smoke-round1.txt`、`build/logs/grace-server-round1.txt`
（服务端游戏日志另有 `build/serverSmoke/logs/latest.log`、`build/gracePrototypeServer/logs/latest.log`）

## 关键日志（逐字）

### 无依赖（`-PserverSmoke`，这就是此前 `NoClassDefFoundError: .../Waystone` 崩掉的那个场景）

```
[modloading-sync-worker/INFO] [EldenCombat/]: [grace] 配置已载入 ...\build\serverSmoke\config\eldencombat-grace.json
                              （休息恢复：FP=FULL 生命=KEEP 精力=KEEP 药瓶=KEEP）
[modloading-sync-worker/INFO] [EldenCombat/]: GRACE_UNAVAILABLE: 缺少依赖 [waystones, balm]；赐福接口显示不可用，普通战斗功能不受影响
[Server thread/INFO] [net.minecraft.server.dedicated.DedicatedServer/]: Done (0.366s)! For help, type "help"
[Server thread/INFO] [EldenCombat/]: SERVER_SMOKE_PASS: 428 shared checks, no client-class dependencies
[Server thread/INFO] [EldenCombat/]: SERVER_SMOKE_COMPLETE: test datapack had 300 ticks to validate Boss damage/death
```

**没有任何 `NoClassDefFoundError`，没有 `AutomaticEventSubscriber.inject` 栈。**

### 有依赖（`-PgracePrototype`）

```
[EldenCombat/]: GRACE_ADAPTER_READY: 赐福适配器已装载（Waystones+Balm）；荒野默认策略，原生 Pre 与 requirements 双重门禁
[EldenCombat/]: GRACE_PROTO_HARNESS_REGISTERED: dedicated-server fixture active
[EldenCombat/]: GRACE_DEPENDENCY_PASS: waystones 21.1.46
[EldenCombat/]: GRACE_DEPENDENCY_PASS: balm 21.0.66
[EldenCombat/]: GRACE_DEPENDENCY_PASS: journeymap 1.21.1-6.0.9
[EldenCombat/]: GRACE_DEPENDENCY_PASS: commonnetworking 1.0.21-1.21.1
[EldenCombat/]: GRACE_NODE_PASS: uid=0c615fe8-... name=Grace prototype 1 pos=BlockPos{x=0, y=81, z=0} valid=true
[EldenCombat/]: GRACE_NODE_PASS: uid=fafc4d99-... name=Grace prototype 2 pos=BlockPos{x=20, y=81, z=0} valid=true
[EldenCombat/]: GRACE_PROTO_SERVER_COMPLETE: two real nodes saved; no player/map interaction asserted
```

`GRACE_ADAPTER_READY` 是在**没有** `-D eldencombat.gracePrototype` 逻辑参与的情况下打印的：
该 property 现在只控制测试夹具，产品路径改由「依赖齐备」判定。

## 本轮实现（对应文档条目）

- **§1.1 依赖准入 + 正式启动路径**：新增 `server/grace/GraceDependencies.java`（**不含任何 Waystones/Balm 类型**，只做依赖判定）、
  `server/grace/GraceBootstrap.java`（`catch (Throwable)` 把链路错误降级成"不可用"，避免可选子系统拖死整模组）。
  `EldenCombat.onCommonSetup` 改为调用 `GraceBootstrap.install()`，不再读原型 property。
  客户端侧：`ClientPayloadTrampoline.handleGraceNodes` 改用 `GraceDependencies.mapPresent()`；
  `GraceMapPlugin` 去掉 property 门禁，且只在**收到过服务端快照**后才清理 Waystones 的原生插件点。
  `ModPayloads` 的旅行包不再看 property：后端没装载时由 `GraceOperations.travel` 明确回"赐福系统尚未启用"。
- **§1.2 夹具类加载修复**：`GracePrototypeHarness` / `GraceClientPrototypeHarness` 摘掉 `@EventBusSubscriber`，
  改为 `install()` 显式注册（DEDICATED_SERVER / CLIENT 分别由 `EldenCombat.onCommonSetup` 与 `EldenCombatClient` 调用），
  注册条件是「显式测试模式 **且** 依赖齐全」。顺带把 `orElseGet(lambda)` 改成显式分支
  （lambda 合成方法签名会把 Waystone 带进反射可见的方法表）。
- **§1.4 pending 清理**：`GraceService` 新增 `pendingSince` 登记时刻表 + `sweepPending`：
  超时（`pendingTimeoutTicks`，默认 1200 tick）或**目标节点被删除/失效**时作废 pending；
  登出/服务端关闭照旧清理；切维度只在"去的不是目标维度"时作废
  （否则会抢在 `WaystoneTeleportEvent.Complete` 之前吃掉票据，冷却与完成日志都会丢）。
  异步回调仍只做反馈，不改玩家状态。
- **§1.5 rest 恢复规则集中配置**：新增 `config/eldencombat-grace.json`（`GraceSettings`），
  默认值 = 既有行为（只回 FP）。`restHealth`/`restStamina`/`restFlasks` 显式打开后分别接
  **原版生命值**、`CombatState.stamina`、`CombatState.potionCharges`（现有权威状态，未新造第二份资源），
  恢复后立即 `SkillServerEvents.sync` / `ServerCombat.syncNow` 同步。
  **重生点故意没有开关**（未接权威状态，不能被静默启用）。

## 仍未验证（不得写成通过）

1. 客户端在**有依赖**下的完整玩法闭环（发现赐福 → 装配 → 休息 → 地图旅行）本轮未跑；
   上轮 client-smoke 通过的是无依赖的 33 个视觉夹具，不是赐福玩法。
2. §1.3 赐福菜单（休息/旅行/战灰装配入口 + 每次执行重新校验）**尚未实现**。
3. §1.4 的"私有化"路径只在 `GraceAccess` / `GraceRequirement.canAfford` 上被重新校验，
   没有跑过"旅行途中把节点改私有"的真实场景。
4. `restHealth` / `restStamina` / `restFlasks` 三条规则只在编译与配置载入层面验证，
   **没有真人右键赐福验证 HUD 数值变化**。
5. 专用服务器双玩家权限隔离、掉线、节点删除等多人场景未跑。
