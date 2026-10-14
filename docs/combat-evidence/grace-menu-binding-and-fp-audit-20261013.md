# 赐福内战灰闭环（§2.3）+ 每段 FP 核对（§2.6）验证证据

日期：2026-10-13 20:49–20:56
工作区：`D:/codexout/mcmod`
对应任务：`docs/DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md` §2.3、§2.6
备份：本轮改的既有文件副本见 `docs/code-backups/20261013-grace-menu/`（`GraceMenu*`/`GraceScreen*`/payload/`build.gradle` 等）

## 0. 先说结论（含一条**自我更正**）

1. **§2.6 那个待查项不成立**：首次追击走的是 `costForStage(1)` = `fpCostFollowup`，
   **没有多收首段价**；而且"给索引加 +1"在这张只有两档的价表上**是空操作**，
   `cursed_blood_slice` 无论如何都收 8。我此前在台账里写的"缺 +1 → 多收 4 FP"是**错的**，已改掉那一行。
   核对方式刻意是**驱动真实状态机**，不是给 `costForStage` 传参（传参等于把待查的前提当成已知）。
2. **核对时查出一条真问题（不是收费 bug）**：`destined_death` / `waterfowl_dance` 的 `maxStages=3` 是**死配置**——
   它们 `followupWindowTicks=0`，所以 `hasFollowup()` 为假，第二段**永远买不到**；
   三段体感来自运行时内部的 `burst` 通道（一个长 ACTIVE 窗口里连打三下）。本轮**不改**这 8 件参数（越界改平衡），只备案。
3. **§2.3 闭环完成**：成功的装配/替换/卸除、重复点击、换手防误改、离开范围、进入战斗，全部在真实客户端上跑通。

## 1. 执行命令与结果

| # | 命令 | 真实退出码 | 结论 |
|---|---|---|---|
| 1 | `.\gradlew.bat test --offline --no-daemon` | **0** | 22 类 / **160 用例 / 0 失败 / 1 跳过**（`SkillCommitBoundaryTest` 12 例，含本轮新增 3 例） |
| 2 | `.\gradlew.bat runClient -PgracePrototype -PgraceMenu --offline --no-daemon` | **0** | 赐福菜单夹具 **16 项断言全过**（逐字日志见下） |
| 3 | `.\gradlew.bat runClient -PashSmoke --offline --no-daemon` | **0** | 普通战灰夹具无回归（含单段扣费 100→89 与 16.728 伤害） |
| 4 | `.\gradlew.bat runServer -PserverSmoke --offline --no-daemon` | **0** | 无依赖专用服务器正常（目录 3 件照旧注册） |

日志：`build/logs/test-round5c.txt`、`grace-menu-round5.txt`、`ash-smoke-round5.txt`、`server-smoke-round5.txt`；
游戏内日志 `build/gracePrototypeClient/logs/latest.log`、`build/ashSmoke/logs/latest.log`。

## 2. 赐福菜单夹具逐字日志（16 项断言）

```
GRACE_MENU_OPEN_REQUEST: fp=5 authorized=2 hidden=50af0864-…
GRACE_MENU_SCREEN_PASS: nodes=2 ashes=3 fp=5/100 held=（空手） rejection=当前主手武器不支持战灰
GRACE_MENU_BUTTON: pressed 休息（恢复 FP）
GRACE_MENU_BUTTON: pressed [0] eldencombat… · 未获得
GRACE_MENU_OWNERSHIP_PASS: 未解锁战灰被拒，notice=尚未获得此战灰
GRACE_MENU_REST_PASS: fp=100/100
GRACE_MENU_FORGERY_REJECTED: stale containerId ignored, fp still 5
GRACE_MENU_TRAVEL_REJECTED: undiscovered node refused, pos=(0.5, 81.0, 1.5)
GRACE_MENU_RANGE_REJECTED: out-of-range rest refused, fp still 5
GRACE_MENU_BUTTON: pressed [1] Grace prototy…  overworld
GRACE_MENU_TRAVEL_DISPATCH_PASS: authorized node accepted, notice=已提交旅行请求
GRACE_MENU_GRANT_FIXTURE: 已解锁 3 件并换上曲剑（管理脚手架，非玩法来源）token=0:eldencombat:curved_sword
GRACE_MENU_BUTTON: pressed [0] eldencombat… · 可装配
GRACE_MENU_BIND_PASS: 已解锁+兼容的战灰经菜单装配成功，notice=已装配战灰：eldencombat:spinning_slash
GRACE_MENU_DUPLICATE_PASS: 重复装配被当作可读空操作，notice=无法装配：这件武器已经绑定了战灰，请用替换
GRACE_MENU_HELD_SWITCH_REJECTED: 换手后旧 token 被拒且未误改新武器，notice=主手武器已变化（换手或换槽），已刷新，请重试
GRACE_MENU_BUTTON: pressed 卸除战灰
GRACE_MENU_UNBIND_PASS: 菜单卸除成功，主手不再绑战灰
GRACE_MENU_COMBAT_REJECTED: 战斗期间休息被拒、fp 仍为 5
GRACE_MENU_COMPLETE: engine fixture (programmatic button press); real mouse clicks still unverified
```

覆盖的 §2.3 要求：

| 文档要求 | 断言 |
|---|---|
| 赐福内战灰选择 / **兼容性预览** / 绑定 / 替换 / 卸除 | 菜单现在带**逐件兼容性**（`compatible`，服务端算）；装配与卸除都经菜单按钮走通 |
| 请求只带选择信息，不信客户端的拥有权/FP/段数/ItemStack | 赐福 UUID 取服务端菜单实例；FP/拥有权/武器全是服务端现取（伪造 containerId 被丢弃） |
| Menu 容器验证、按钮处理、结果同步 | 每次执行后推新快照；控件只在"构成变化"时重建（否则每 10 tick 刷新会抖） |
| **处理重复点击** | 第二次点"装配"得到可读空操作"已经绑定了战灰，请用替换" |
| 打开菜单后**换手** | 用界面展示时的旧 `heldToken` 提交 → 被拒且**没有**装到后来拿起的武器上 |
| 打开菜单后**离开**（超距离） | 超距离休息被拒、菜单自动关闭 |
| 打开菜单后**进入战斗** | 战斗期间休息被拒、FP 不变 |
| UI 展示的武器与服务端提交目标一致 | 由 `heldToken`（槽位 + 物品 id）保证；见上两条 |

## 3. §2.6 核对（新增 3 个纯逻辑用例）

`SkillCommitBoundaryTest` 现在把整条链钉死：

| 用例 | 钉住的事实 |
|---|---|
| `firstFollowupChargesTheFollowupPriceNotTheFirstStagePrice` | 驱动真实状态机：首段收 12 → **进入 ACTIVE 后 stageIndex 已是 1** → 首次追击收 **8**（不是 12）；追击提交只回到 WINDUP，段号此刻仍是 1，进入下一段 ACTIVE 才变 2；整条链只扣 12+8 |
| `stagesWithAFollowupWindowChargeTheirConfiguredPrice` | 三件真有追击窗的技能（`bloodboon_ritual` 15/15、`starcaller_cry` 15/15、`cursed_blood_slice` 12/8）逐段按 spec 收 |
| `multiStageConfigIsInertWithoutAFollowupWindow` | `destined_death` / `waterfowl_dance`：声明 `maxStages>1` 但没有追击窗 → 第二段必须被拒、`stageIndex` 不推进（`maxStages` 无效的证据） |
| `priceTableHasExactlyTwoTiersSoLaterStagesCannotBePricedSeparately` | 价格表只有两档：`costForStage(1)==costForStage(2)==costForStage(9)`；因此"+1"**不可能**改变金额；并记下"三段以上无法给第三段单独定价"这条内容限制 |

`fpCostFollowup=0` 时的"回落到首段价"分支，在现有 8 件里**走不到**
（有窗的三件都配了非 0 追击价；没窗的两件根本买不到追击段）——已写进用例注释。

## 4. 本轮工具性改动（规格）

- `GraceMenuStatePayload`：新增 `heldToken`（武器身份）与 `Ash.compatible`（逐件兼容性预览）。
- `GraceMenuActionPayload`：新增 `heldToken`（客户端回传界面展示的那把武器）。
- `GraceMenu`：持有 `notice`，周期刷新时沿用，免得刷新把玩家刚看到的失败原因冲掉。
- `GraceMenuActions`：`heldToken(player)`（槽位 + 物品 id，**刻意不含**已绑战灰与强化等级，
  否则一次成功绑定就会让自己的标记过期）；只对"会写主手物品"的操作核对武器身份；
  新增 `refresh(player)` 与 `REFRESH_TICKS=10`。
- `GraceService`：每 10 tick 给"菜单正开着"的玩家推一次快照（节点快照仍按 `syncTicks`）。
- `AshBindings.checkHeld(player, ashId)`：只读的兼容性判定，与 bind/replace 同源
  （避免"显示可装、点了被拒"）。
- `GraceScreen`：只有"构成变化"（节点/战灰/手持/拒绝原因/已绑）才重建控件。

## 5. 仍未验证（不得算作完成）

1. **玩法内解锁途径仍然没有**：本轮的"成功装配"是用管理命令搭台的（日志里已标明"管理脚手架，非玩法来源"），
   **不能**据此声称玩法闭环通过。§2.1 的奖励来源仍待接。
2. **真人鼠标**：夹具走的是原版控件的 `onPress()` 回调（真实包、真实服务端），没有真实鼠标/焦点事件。
3. 只有曲剑（slashing）× `spinning_slash` 走通了完整装配链；另外两件战灰的菜单装配未逐件实跑。
4. 换手用"切槽"制造（槽 0 曲剑 → 槽 1 刀）；同一槽内换物品的路径未单独跑（由 `itemId` 进 token，逻辑同源）。
5. 多人场景（双玩家权限隔离、并发点击同一赐福）未跑，属集中验收第 3 项。
