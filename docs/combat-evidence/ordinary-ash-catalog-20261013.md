# 普通战灰目录与运行时（§2.2）验证证据

日期：2026-10-13 20:39–20:48
工作区：`D:/codexout/mcmod`
对应任务：`docs/DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md` §2.2（并给出 §2.3 的拥有权路径、§2.6 的单段扣费证据）
备份：`docs/code-backups/20261013-grace-menu/`（改动前的 `EldenCombatClient.java`、`EldenCombat.java`、`build.gradle` 等）；
本轮新增文件不覆盖任何既有文件。

## 执行命令与结果

| # | 命令 | 真实退出码 | 结论 |
|---|---|---|---|
| 1 | `.\gradlew.bat test --offline --no-daemon` | **0** | 22 类 / **156 用例 / 0 失败 / 1 跳过**（新增 `OrdinaryAshCatalogTest` 8 用例） |
| 2 | `.\gradlew.bat runClient -PashSmoke --offline --no-daemon`（新夹具） | **0** | 目录/绑定校验/单段运行时/扣费/卸除 **5 项断言全过** |
| 3 | `.\gradlew.bat runClient -PgracePrototype -PgraceMenu --offline --no-daemon`（旧夹具复跑 + 加拥有权断言） | **0** | 菜单现在列出 3 件战灰；点未解锁的战灰被拒 `尚未获得此战灰` |
| 4 | `.\gradlew.bat runServer -PserverSmoke --offline --no-daemon`（无 Waystones/Balm） | **0** | 目录在**无依赖**专用服务器上同样注册；`SERVER_SMOKE_PASS` |

日志：`build/logs/test-round4b.txt`、`ash-smoke-round4c.txt`、`grace-menu-round4b.txt`、`server-smoke-round4.txt`；
游戏内日志 `build/ashSmoke/logs/latest.log`、`build/gracePrototypeClient/logs/latest.log`。

## 逐字日志

### 目录注册（无依赖专用服务器上也成立）

```
ASH_CATALOG_ENTRY: eldencombat:spinning_slash 「回旋斩」 武器分组=slashing 武器数=5 FP=8.0 段数=1
ASH_CATALOG_ENTRY: eldencombat:heavy_thrust 「重刺」 武器分组=thrusting 武器数=5 FP=9.0 段数=1
ASH_CATALOG_ENTRY: eldencombat:stamp_upward_cut 「踏前上斩」 武器分组=cleaving 武器数=5 FP=11.0 段数=1
ASH_CATALOG_REGISTERED: 实际支持普通战灰 3 件：eldencombat:spinning_slash, eldencombat:heavy_thrust, eldencombat:stamp_upward_cut
```

### `-PashSmoke` 夹具（真实客户端 + 集成服务器）

```
ASH_SMOKE_SETUP: catalog=3 ash=eldencombat:stamp_upward_cut fpCost=11.0 目标血量=20.0
ASH_SMOKE_CAST_PASS: eldencombat:stamp_upward_cut 起手成功，FP 100→89（扣 11）
ASH_SMOKE_HIT_PASS: eldencombat:stamp_upward_cut 单段判定造成 16.728 点伤害（20.0 -> 3.2719994）
ASH_SMOKE_UNBIND_PASS: 卸除后主手不再解析出战灰
ASH_SMOKE_COMPLETE: 目录/绑定校验/单段运行时/扣费/卸除 全部通过（引擎夹具）
```

同一夹具里还断言了（失败即 `ASH_SMOKE_FAIL`）：

| 断言 | 结果 |
|---|---|
| 目录非空且每一项 `implemented()==true`（未实现项不得进目录） | ✅ |
| 大剑绑刺击系战灰（`heavy_thrust`）→ `UNSUPPORTED_ITEM` | ✅ |
| 目录外战灰（`eldencombat:no_such_ash`）→ `UNKNOWN_SKILL` | ✅ |
| 裸路径 id（`bare_path`）→ `INVALID_ID` | ✅ |
| 同类型武器绑 `stamp_upward_cut` → `APPLIED`，且主手能解析回该战灰 | ✅ |
| 起手返回 `STARTED`，施放状态指向该战灰 | ✅ |
| **首段扣费正好等于 `fpCost`**（100→89，不多不少） | ✅ |
| 卸除后 `AshBindings.resolve` 与 `SkillCasting.specForMainHand` 都返回 null | ✅ |

### 赐福菜单夹具复跑（§2.3 的拥有权路径）

```
GRACE_MENU_SCREEN_PASS: nodes=2 ashes=3 fp=5/100 held=（空手） rejection=当前主手武器不支持战灰
GRACE_MENU_BUTTON: pressed [0] eldencombat… · 未获得
GRACE_MENU_OWNERSHIP_PASS: 未解锁战灰被拒，notice=尚未获得此战灰
GRACE_MENU_REST_PASS / FORGERY_REJECTED / TRAVEL_REJECTED / RANGE_REJECTED / TRAVEL_DISPATCH_PASS / COMPLETE   ← 上一轮的 8 项断言仍然全过
```

## 本轮实现

**新增 6 个文件**

| 文件 | 职责 |
|---|---|
| `core/AshWeaponGroups.java` | 武器白名单分组（**纯逻辑**）：复用现有 `WeaponProfile` id，展开成完整 namespaced 物品 id |
| `server/skills/OrdinaryAshCatalog.java` | **实际支持列表**（3 件）+ 单段参数表（同人草稿值）——不依赖 Minecraft，可单测 |
| `server/skills/OrdinaryAshRuntime.java` | **独立**的普通战灰运行时（不是 `RemembranceRuntime`）：两种判定形状（绕身横扫 / 身前胶囊扫掠），复用 `SkillHitEffects` 与已有特效 Kind，不新增美术 |
| `server/skills/OrdinaryAshes.java` | 把目录注册进 `AshBindings`（id 合法 + 白名单非空 + 真实存在 + 运行时非追忆，四条同时成立才注册） |
| `testing/AshSmokeHarness.java` | 新引擎夹具（真实客户端 + 集成服务器） |
| `src/test/.../OrdinaryAshCatalogTest.java` | 8 个不变量用例 |

**修改 3 个文件**：`EldenCombat.java`（common setup 调 `OrdinaryAshes.registerAll()`）、
`client/EldenCombatClient.java`（`-PashSmoke` 时显式注册夹具）、`build.gradle`（`-PashSmoke` 运行配置、
`prepareAshSmokeWorld` 存档准备、jar 排除夹具类）；另改 `testing/GraceClientPrototypeHarness.java`（加拥有权断言）。

## 关键设计取舍

1. **不借追忆运行时冒充**：`AshBindings.Definition.implemented()` 明确排斥 `RemembranceRuntime`，
   所以新写了 `OrdinaryAshRuntime`。两者共用的是**服务端判定助手**（`SkillHitEffects` + 胶囊判交 + `SkillCastState` 去重），
   不是时间线实现——共用助手不改变"实现是独立的"这一点。
2. **单段**：3 件都是 `followupWindowTicks=0, maxStages=1`。用户要求"简单的战技释放动作"，
   而且刻意不让新内容踩在 §2.6 那条还没修的多段索引上。
3. **不位移**：没有跃起/突进 → 不需要落地安全网、悬空追踪与残留清理；
   `onAbort` 也就没有需要收回的东西（这正是它不该结算伤害的原因）。
4. **白名单用现有武器类型**：直接用 `WeaponProfile` id 分组展开，不另立分类表；
   注册时逐个核对"物品真的注册过 + 真的有武器定义"，避免白名单里挂着死 id（那种"能选但永远失败"的坑）。
5. **非白名单武器不可绑**：`AshBindings.check` 返回 `UNSUPPORTED_ITEM`；夹具已实测。
6. **拥有权只在绑定入口校验**：目前唯一绑定入口是 `AshWorkshop.apply`（赐福菜单），它先查赐福权限再查拥有权。
   `AshBindings.bind` 是**不校验拥有权**的低层 API——将来若新增第二个绑定入口，必须同样过 `AshWorkshop`。

## 仍未验证（不得算作完成）

1. **玩法内解锁途径仍然没有**（§2.1 的奖励来源未接）：所以"解锁 → 菜单里装配 → 施放"这条**完整玩法链**没有跑过。
   本轮的拥有权证据是"未解锁 → 被拒"；"已解锁 → 装成功"要等奖励来源接上（或用管理命令搭台，届时必须标注）。
2. §2.3 的其余部分仍未验：**成功绑定/替换/卸除**（菜单路径）、重复点击、打开菜单后换手/离开范围/进入战斗、
   以及"UI 展示的武器与服务端提交时一致"。
3. 三件战灰里只实测了 `stamp_upward_cut`（身前胶囊形状）；`spinning_slash`（绕身形状）与 `heavy_thrust`
   只有绑定/不兼容的断言，**判定形状本身没有打过真实目标**。
4. 只有一件成对验证了"武器类型 ↔ 战灰"的兼容矩阵的正例；其余 12 件白名单武器未逐件实跑。
5. 平衡值全是草稿，未做任何手感/数值评估；也没有真人操作。
