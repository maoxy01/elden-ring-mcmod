# 赐福菜单（§1.3）+ 节点披露与可读反馈（§1.6）验证证据

日期：2026-10-13 20:11–20:30
工作区：`D:/codexout/mcmod`
对应任务：`docs/DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md` §1.3、§1.6（依赖上一轮的 §1.1/§1.2/§1.4/§1.5）
交付快照：`docs/code-backups/20261013-grace-menu/`（**本轮改动后的状态快照**，方便下一轮/Codex 对照；
改动前的副本见 `docs/code-backups/20261013-grace-baseline/`，本轮新增的 8 个文件此前不存在）

## 执行命令与结果

| # | 命令 | 真实退出码 | 结论 |
|---|---|---|---|
| 1 | `.\gradlew.bat compileJava --offline --no-daemon` | **0** | `BUILD SUCCESSFUL in 9s` |
| 2 | `.\gradlew.bat test --offline --no-daemon` | **0** | 20 类 / **139 用例 / 0 失败 / 1 跳过** |
| 3 | `.\gradlew.bat runClient -PgracePrototype -PgraceMenu --offline --no-daemon` | **0** | 真实客户端 + 集成服务器，赐福菜单夹具 **8 项断言全过**（下方逐字日志） |
| 4 | `.\gradlew.bat runServer -PserverSmoke --offline --no-daemon`（无 Waystones/Balm） | **0** | `GRACE_UNAVAILABLE` + `SERVER_SMOKE_PASS` / `SERVER_SMOKE_COMPLETE` |
| 5 | `.\gradlew.bat runServer -PgracePrototype --offline --no-daemon`（依赖齐备） | **0** | `GRACE_ADAPTER_READY` + 两节点落盘 + `GRACE_PROTO_SERVER_COMPLETE` |
| 6 | `.\gradlew.bat runClient -PclientSmoke --offline --no-daemon` | **0** | `CLIENT_SMOKE 428 shared checks` + `CLIENT_SMOKE_PASS: all 33 visual fixtures rendered` + `CLIENT_NETWORK_PASS: all network checks completed`，客户端日志中 `Exception/ERROR/FAIL` 命中数 **0** |

日志：`build/logs/compile-round2.txt`、`test-round2.txt`、`grace-menu-round2c.txt`、
`server-smoke-round2.txt`、`grace-server-round2.txt`、`client-smoke-round2.txt`；
游戏内日志 `build/gracePrototypeClient/logs/latest.log`、`build/clientSmoke/logs/latest.log`。

## 逐字日志（`build/gracePrototypeClient/logs/latest.log`）

```
GRACE_ADAPTER_READY: 赐福适配器已装载（Waystones+Balm）；荒野默认策略，原生 Pre 与 requirements 双重门禁
GRACE_CLIENT_NODE_PASS: mode=engine activation player=380df991-... op=false uid=39101552-... name=Grace prototype 1
GRACE_CLIENT_NODE_PASS: mode=engine activation player=380df991-... op=false uid=230f7a67-... name=Grace prototype 2
GRACE_MENU_OPEN_REQUEST: fp=5 authorized=2 hidden=1d1cbfff-42a4-42d4-8dc8-47b4f44aab02
GRACE_MENU_SCREEN_PASS: nodes=2 ashes=0 fp=5/100 held=（空手） rejection=当前主手武器不支持战灰
GRACE_MENU_BUTTON: pressed 休息（恢复 FP）
GRACE_MENU_REST_PASS: fp=100/100
GRACE_MENU_FORGERY_REJECTED: stale containerId ignored, fp still 5
GRACE_MENU_TRAVEL_REJECTED: undiscovered node refused, pos=(0.5, 81.0, 1.5)
GRACE_MENU_RANGE_REJECTED: out-of-range rest refused, fp still 5
GRACE_MENU_BUTTON: pressed [1] Grace prototy…  overworld
GRACE_MENU_TRAVEL_DISPATCH_PASS: authorized node accepted, notice=已提交旅行请求
GRACE_MENU_COMPLETE: engine fixture (programmatic button press); real mouse clicks still unverified
```

逐项含义（每一行都是一个真实断言，不是打印）：

| 标记 | 断言的是什么 |
|---|---|
| `GRACE_MENU_SCREEN_PASS` | 服务端 `openMenu` 之后，真实客户端上打开的**就是** `GraceScreen`；状态快照里**只披露 2 个已授权节点**（世界里还有第 3 个已注册但未激活的节点，没有出现）；战灰目录当前为空；FP 与服务端一致 |
| `GRACE_MENU_REST_PASS` | 通过真实界面的「休息」按钮（`onPress()` → 真实网络包 → 服务端）把 FP 从 5 恢复到 100/100 |
| `GRACE_MENU_FORGERY_REJECTED` | 伪造/过期 `containerId` 的请求被丢弃：FP 保持 5（`GraceMenuActions` 用服务端菜单实例的 `containerId` + 服务端持有的赐福 UUID） |
| `GRACE_MENU_TRAVEL_REJECTED` | 对**未发现**节点（已注册为赐福但玩家未激活）的旅行请求被拒，玩家坐标未变 |
| `GRACE_MENU_RANGE_REJECTED` | 把玩家瞬移出有效距离后立刻请求休息 → 被拒，FP 保持 5（同一 tick 内调用，没有被"自动关菜单"掩盖） |
| `GRACE_MENU_TRAVEL_DISPATCH_PASS` | 对已授权节点的旅行请求通过全部授权校验并提交（notice = `已提交旅行请求`） |

## 本轮实现

**新增 8 个文件**

| 文件 | 职责 |
|---|---|
| `registry/ModMenus.java` | `MenuType<GraceMenu>` 注册（`IMenuTypeExtension`，额外数据只有赐福 UUID + 名字） |
| `server/grace/GraceMenu.java` | 0 槽位服务菜单；`stillValid` 每次重新校验访问权限，失效即关菜单；**不做**唯一授权 |
| `server/grace/GraceMenuActions.java` | 服务端执行口：赐福 UUID 取服务端菜单实例、每次执行重新校验、`containerId` 只挡过期/伪造包；所有失败翻译成人话 |
| `network/GraceMenuStatePayload.java` | 服务端 → 客户端状态快照（只含已授权节点、战灰目录、手持、FP、notice） |
| `network/GraceMenuActionPayload.java` | 客户端 → 服务端按钮请求（操作、菜单 id、目标节点、战灰 id、序号） |
| `client/grace/GraceScreen.java` | 界面：左侧旅行列表、右侧战灰装配、底部休息/卸除，全部原版控件 |
| `client/grace/GraceScreenState.java` | 最近一次快照（包可能先于界面到达，`init()` 也要能取到） |
| `client/grace/GraceClientActions.java` | 客户端唯一请求出口 + **菜单与地图共享的序号**（服务端用它挡重放，两边各数一套会互相误判） |

**修改**

- `server/grace/GraceService.java`：方块右键从"直接休息"改为"打开菜单"。四个条件都满足才抢这次交互
  （是本项目赐福 / 玩家已发现 / 非潜行 / 访问权限校验通过），抢下时必须同时 DENY `useBlock`+`useItem`——
  否则 Waystones 的原生界面会在事件之后打开并覆盖菜单；未发现时**不抢**，保留原生激活入口。
  `syncNodes` 改走新的 `GraceAccess.authorizedNodes`。
- `server/grace/GraceAccess.java`：新增 `authorizedNodes(player, limit)`——地图下发与菜单列表**共用同一份披露判定**，
  避免"地图看不到但菜单能传"的缺口。
- `server/grace/GraceRest.java`：休息后记 `GRACE_REST_APPLIED`（含四条恢复规则的实际取值），日志落在唯一的休息实现里。
- `server/skills/AshBindings.java`：新增只读 `editability(player)` 供菜单预览（与 bind/replace 同一套检查，不改状态）。
- `registry/ModPayloads.java`：注册两个菜单包；通道版本 `4 → 5`；菜单包处理器先判 `GraceOperations.available()`，
  防止这一行把含 Waystones 类型的服务端类加载进无依赖的专用服务器。
- `network/ClientPayloadTrampoline.java`：`handleGraceMenuState`。
- `client/EldenCombatClient.java`：`RegisterMenuScreensEvent` 注册 `GraceScreen`。
- `client/grace/GraceMapPlugin.java`：改用共享序号。
- `EldenCombat.java`：注册 `ModMenus.MENUS`。
- `build.gradle`：新增 `-PgraceMenu`（把 `eldencombat.graceMenu` 透给客户端夹具）。
- `testing/GraceClientPrototypeHarness.java`：新增赐福菜单夹具（**明确标注为引擎夹具**）。
- `testing/GracePrototypeHarness.java`：同步更新类注释里的夹具登记说明（上一轮改的显式注册）。

## 仍未验证（不得写成通过）

1. **真人鼠标点击**：夹具用的是原版控件的 `onPress()` 公共回调（真实包、真实服务端），但没有真实鼠标/焦点事件；
   `GRACE_MENU_COMPLETE` 这行已自带该声明。
2. **战斗状态拒绝**：菜单开着时进入战斗的路径没有跑（`GraceMenuActions` 每次执行都会查 `rejectActivity`，但未实测）。
3. **旅行真正落地**：只断言到"授权通过并已提交"；Waystones 异步落地的完成/失败属于框架既有未验收项，本轮未跑。
4. **私人/团队赐福权限**：菜单披露用的是 `GraceAccess.rejection`（私有节点非 owner 被排除），没有跑双玩家实测。
5. **战灰装配的界面路径**：当前 `AshWorkshop.catalog` 为空（§2.2 未实现），所以菜单的战灰列只验证到
   "显示未注册提示 + 手持不可编辑原因"。§2.1/§2.2/§2.3 完成后需要重跑本夹具。
6. **多人场景**：双玩家权限隔离、重复请求、节点删除/权限变化、异步落点失败都未跑（属于集中验收第 3 项）。
