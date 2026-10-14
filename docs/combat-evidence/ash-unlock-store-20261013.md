# 战灰解锁存储（§2.1）验证证据

日期：2026-10-13 20:31–20:38
工作区：`D:/codexout/mcmod`
对应任务：`docs/DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md` §2.1
备份：`docs/code-backups/20261013-ash-unlocks/`（改动前的 `EldenCombat.java`、`EldenCombatCommand.java`）

## 执行命令与结果

| # | 命令 | 真实退出码 | 结论 |
|---|---|---|---|
| 1 | `.\gradlew.bat test --offline --no-daemon` | **0** | 21 类 / **148 用例 / 0 失败 / 1 跳过**（新增 `AshUnlockSetTest` 9 用例全绿） |
| 2 | `.\gradlew.bat runClient -PclientSmoke --offline --no-daemon`（第 1 阶段：授予 + 真实死亡 + 落盘） | **0** | `ASH_UNLOCK_GRANTED` → `ASH_UNLOCK_DEATH_PASS` → `CLIENT_PERSISTENCE_SAVED`；`CLIENT_SMOKE_PASS: all 33 visual fixtures rendered` |
| 3 | `.\gradlew.bat runClient -PclientSmoke -PclientSmokeReload --offline --no-daemon`（第 2 阶段：真实关服/重启后复核） | **0** | `ASH_UNLOCK_RESTART_PASS` + `CLIENT_PERSISTENCE_COMPLETE: actual save/shutdown/restart passed` |
| 4 | `.\gradlew.bat runServer -PserverSmoke --offline --no-daemon`（无 Waystones/Balm） | **0** | `ASH_OWNERSHIP_INSTALLED` + `GRACE_UNAVAILABLE` + `SERVER_SMOKE_PASS` / `SERVER_SMOKE_COMPLETE` |

日志：`build/logs/test-round3.txt`、`smoke-stage1-round3.txt`、`smoke-stage1-full.log`、
`smoke-stage2-round3.txt`、`server-smoke-round3.txt`；
游戏内日志 `build/clientSmoke/logs/latest.log`、`build/serverSmoke/logs/latest.log`。

## 逐字日志

### 第 1 阶段（授予 → 真实死亡 → 落盘）

```
[Server thread/INFO] [EldenCombat/]: ASH_UNLOCK_GRANTED: player=380df991-f603-344c-a090-369bad2a924a ash=eldencombat:smoke_probe_ash
[Server thread/INFO] [EldenCombat/]: ASH_UNLOCK_DEATH_PASS: 战灰解锁在真实死亡/重生后保留（服务端存档为权威）
[Server thread/INFO] [EldenCombat/]: CLIENT_PERSISTENCE_SAVED: attributes, +2 heavy weapon, FP37, ash unlock; awaiting restart
[Render thread/INFO] [EldenCombat/]: CLIENT_SMOKE_PASS: all 33 visual fixtures rendered
```

同一帧里还断言了**幂等**：`require(grant(...), "ash unlock granted before death")` 之后紧跟
`require(!grant(...), "ash unlock grant is idempotent (re-grant is a no-op)")`——第二次授予必须返回 false，
否则夹具直接失败。夹具里没有出现任何 `require ... FAIL`。

### 第 2 阶段（真实关服 → 重启 → 复核）

```
[Server thread/INFO] [EldenCombat/]: ASH_UNLOCK_RESTART_PASS: 战灰解锁在真实关服/重启后保留
[Server thread/INFO] [EldenCombat/]: CLIENT_PERSISTENCE_COMPLETE: actual save/shutdown/restart passed
```

> 这两个标记是在**已有的**持久化验收流程里加的断言（该流程原本就验证属性/强化/质变/FP 的真实重启恢复）：
> 死亡来自 `p.hurt(genericKill, 10000)` + `PERFORM_RESPAWN` 包，重启是 `saveEverything` 之后真的退出客户端再启动。

### 无依赖专用服务器

```
[modloading-sync-worker/INFO] [EldenCombat/]: ASH_OWNERSHIP_INSTALLED: 战灰解锁走服务端持久化存档（SavedData，UUID 为键，幂等授予）
[modloading-sync-worker/INFO] [EldenCombat/]: GRACE_UNAVAILABLE: 缺少依赖 [waystones, balm]；赐福接口显示不可用，普通战斗功能不受影响
[Server thread/INFO] [EldenCombat/]: SERVER_SMOKE_PASS: 428 shared checks, no client-class dependencies
```

## 本轮实现

**新增 4 个文件**

| 文件 | 职责 |
|---|---|
| `core/AshUnlockSet.java` | **纯逻辑**（不依赖 Minecraft，所以可单测）：只收完整 `namespace:path`、授予幂等、稳定字典序落盘、读入时丢弃脏条目并计数 |
| `server/skills/AshUnlockData.java` | 服务端 `SavedData`（`eldencombat_ash_unlocks`）：UUID → 解锁集合；`grant` 只在真新增时 `setDirty`，`revoke` 清空后移除条目 |
| `server/skills/AshUnlocks.java` | 服务端入口（唯一权威）：`install()` 把 `AshWorkshop.Ownership` 接线一次；`owns/grant/revoke/list`；服务端线程校验 |
| `src/test/.../AshUnlockSetTest.java` | 9 个纯逻辑用例：命名空间必需、大小写规范化、幂等、撤销、快照语义、落盘往返去重、脏数据不毁存档、null/空容忍 |

**修改 3 个文件**

- `EldenCombat.java`：common setup 里调用 `AshUnlocks.install()`（**不引用 Waystones**，所以无依赖服务器也有正确语义）。
- `command/EldenCombatCommand.java`：新增 `/eldencombat ash`（查看）、`/eldencombat ash grant <id>`、
  `/eldencombat ash revoke <id>`；类注释明确标注这是**管理/验收面、不是正式奖励来源**。
- `testing/ClientSmokeHarness.java`：在既有的死亡/持久化流程里加 3 条断言 + 2 行 PASS 日志。

## 关键取舍（写在代码注释里，便于复核）

1. **存服务端 SavedData，不存玩家实体附件**：解锁是**账号级**进度，不该随实体销毁/克隆丢失，
   也不该受"死亡掉落"类规则影响；而且**不需要** `copyOnDeath`，因此没有"靠死亡克隆刷解锁"的口子。
2. **拒绝裸路径 ID**：只认完整 `namespace:path`。裸路径会让"哪个命名空间的同名战灰"永远说不清，
   是兼容性事故的源头（护符那边的 §3.6 正是这种事故）。
3. **授予幂等 + 脏数据宽容**：奖励来源重试不会翻倍；读入时坏条目丢弃**并计数上报**（一条脏数据不能让整份存档读崩，也不静默吞掉问题）。

## 仍未做（不得算作完成）

1. **奖励来源本身没有接上**：按文档要求"留给现有探索/奖励能力，不在这里造另一套任务系统"，
   而本工程目前**没有**可用的探索/奖励实现。所以现阶段没有任何**玩法内**途径解锁战灰；
   `/eldencombat ash grant` 只是管理/验收面，**不得**据此声称玩法闭环通过。
2. 战灰目录为空（§2.2 未做），因此"解锁后菜单里可装配"这条链路只能等 §2.2/§2.3。
3. 多玩家隔离（两个 UUID 互不影响）只在实现上成立（UUID 为键），**没有**跑双玩家实测。
4. 存档损坏恢复（手工改坏 `eldencombat_ash_unlocks` 后启动）没有实测，只有单测覆盖 `load` 的宽容性。
