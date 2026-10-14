# 三大系统闭环 · 进度台账（DSH 维护）

来源任务：`docs/DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md`「DSH 续写顺序和范围」（18 条）
最近更新：2026-10-13 22:00
规则：本文件是**唯一**进度权威；状态只有 `TODO` / `进行中` / `已实现（未验收）` / `已验证` 四档。
「已验证」= 有真实日志证据，且证据文件里写明命令与退出码。

## 一、赐福底座

| # | 条目 | 状态 | 证据 / 下一步 |
|---|---|---|---|
| 1.1 | 原型开关 → 正式启动路径，依赖准入，客户端隔离 JourneyMap | **已验证** | `GraceDependencies` + `GraceBootstrap`；无依赖跑通 `runServer -PserverSmoke`。证据 `combat-evidence/grace-baseline-20261013.md` |
| 1.2 | `GracePrototypeHarness` 自动订阅类加载问题 | **已验证** | 两夹具改 `install()` 显式注册；无依赖服务端不再崩。同上证据 |
| 1.3 | 赐福菜单 + 服务器 Menu/有效距离检查（休息 / 旅行 / 战灰装配） | **已验证** | `GraceMenu` + `GraceMenuActions` + `GraceScreen` + 两个包；引擎夹具 8 项断言全过（含伪造 containerId / 未发现节点 / 超距离三条拒绝路径）。证据 `combat-evidence/grace-menu-20261013.md`。未验：真人鼠标、菜单开着时进战斗、多人 |
| 1.4 | pending 清理（节点删除/私有化、掉线、切维度、关服） | **部分已验证** | `pendingSince` + `sweepPending` + 维度变更分支。2026-10-13 22:25 起**超时**（`GRACE_PENDING_TIMEOUT_PASS`，日志打出真实 age 与上限）、**目标节点被删**（`GRACE_PENDING_DELETED_PASS`）、**切维度两条分支**（作废 / 保留，事件级验证）四条已在赐福菜单夹具里逐条断言。⚠ 未验：**掉线**（单玩家夹具制造不出真实掉线）、**关服**（清理由 `ServerStoppedEvent` 完成，之后没有可断言的运行时）。证据 `combat-evidence/grace-pending-20261013.md` |
| 1.5 | rest 资源恢复规则集中配置 | **已验证** | `config/eldencombat-grace.json`（默认 FP=FULL，其余 KEEP）；夹具实测：休息后 FP 5→100，而生命 9/20、精力 31、药瓶 0 **按 KEEP 未被补满**（`GRACE_MENU_REST_KEEP_PASS`）。未验：真人右键 |
| 1.6 | 节点快照、菜单状态、可读失败反馈；地图只显示服务端允许披露的节点 | **已验证** | `GraceAccess.authorizedNodes(player,limit)` 是地图下发与菜单披露的**唯一**判定；夹具断言"世界里 3 个已注册节点、只披露 2 个"；所有失败码已翻译成人话（`bindingReason`）。同上证据 |

## 二、战灰闭环

| # | 条目 | 状态 | 说明 |
|---|---|---|---|
| 2.1 | 服务器权威的玩家战灰解锁存储（namespaced ID、落盘、死亡/重登语义、幂等奖励） | **已验证** | `core/AshUnlockSet`（纯逻辑）+ `server/skills/AshUnlockData`（SavedData）+ `AshUnlocks`（接线 `AshWorkshop.Ownership`）；真实死亡 + 真实关服重启都验过。**玩法内来源已接 1 条**：击杀玛尔基特 → `stamp_upward_cut`（`core/AshRewardTable` + `AshRewards`，幂等、只给击杀者）。证据 `combat-evidence/ash-unlock-store-20261013.md`、`combat-evidence/ash-reward-source-20261013.md`。⚠ **3 件战灰里只有 1 件能在玩法内获得**，另两件仅有目录条目、无来源（不许说成内容完整） |
| 2.2 | 注册真实普通战灰目录与 Runtime | **已验证** | 3 件（`spinning_slash` 回旋斩 / `heavy_thrust` 重刺 / `stamp_upward_cut` 踏前上斩），各自武器分组 5 件；独立 `OrdinaryAshRuntime`（**不是**追忆运行时）。夹具断言：目录非空且全部 implemented、类型不兼容/目录外/裸路径被拒、起手扣费正好等于 fpCost（100→89）、命中造成伤害、卸除后可解析为空。证据 `combat-evidence/ordinary-ash-catalog-20261013.md`。未验：另两件的判定形状没打过真实目标；13 件白名单武器未逐件实跑 |
| 2.3 | 赐福内战灰选择/兼容性预览与绑定、替换、卸除 | **已验证** | 菜单夹具 16 项断言全过：逐件兼容性预览、**成功装配/卸除**、重复点击→可读空操作、**换手用旧 token 被拒且未误改新武器**、超距离离开=菜单自动关、战斗期间休息被拒。证据 `combat-evidence/grace-menu-binding-and-fp-audit-20261013.md`。未验：真人鼠标、另两件战灰的菜单装配、多人 |
| 2.4 | Menu 容器验证、按钮处理、结果同步；重复点击 / 换手 / 离开 / 进战斗 | **已验证** | 与 2.3 同一批断言（`combat-evidence/grace-menu-binding-and-fp-audit-20261013.md` + 22:25 复跑）：伪造/过期 containerId 被丢弃、重复装配给可读空操作、换手旧 token 被拒、超距离离开=菜单自动关、战斗期间菜单自动关且休息被拒、按钮回执经 payload 同步到界面。未验：真人鼠标、多人 |
| 2.5 | 保留强化、质变、原有物品组件；追忆与锁定战技不允许覆盖 | **已验证** | 夹具实测：绑定后 强化+2 / 质变 HEAVY / 自定义名 全部保留，且组件写在同一个物品栈上（没偷偷换栈）；追忆武器（`sacred_relic_sword`）返回 `LOCKED`。证据 `combat-evidence/ash-reward-source-20261013.md` |
| 2.6 | 统一走 SkillCasting/SkillTransactions/SkillDriver，核对每段 FP | **已验证（核对完成）** | 用**真实状态机**驱动核对（`SkillCommitBoundaryTest` 12 例）：首次追击走 `costForStage(1)` = `fpCostFollowup`，**没有多收首段价**；"给索引 +1"在这张两档价表上是**空操作**。⚠ 旧记录"缺 `+1` → `cursed_blood_slice` 首次追击多收 4 FP"**是错的，已作废**。普通战灰单段扣费另实测（100→89）。**新发现（未改，只备案）**：`destined_death`/`waterfowl_dance` 的 `maxStages=3` 是死配置（`followupWindowTicks=0` → 第二段永远买不到；三段体感来自运行时内部 `burst` 通道），改它会动到八战技参数，故越界不做 |

## 三、护符闭环

| # | 条目 | 状态 | 说明 |
|---|---|---|---|
| 3.1 | Curios 适配（先确认本工程精确依赖，不凭空引用未安装版本 API） | **已验证（结论：不可行，改走自制容器）** | 本工程**没有 Curios jar**、`modpack/mods.lock.json` 无 curios 条目、构建 `--offline` → 不能凭空引用未安装版本 API。装备权威**只有一条**：服务端附件 `eldencombat:talisman_slots`（`TalismanLoadout`，容器里放**完整 ItemStack**）。证据 `combat-evidence/talisman-container-20261013.md` §0/§3 |
| 3.2 | 旧 `TALISMAN` 字符串一次性迁移到能保留完整 ItemStack 的容器 | **已验证** | 裸路径/完整 ID 都迁成真实 ItemStack；**幂等、不重复返还、不静默清掉**（非法 ID / 物品不存在 / 没有空位 → 旧记录原样保留 + WARN；容器非空 → 清旧记录 + WARN 避免重复返还）。夹具断言含"未知物品记录被保留"与"重复调用不重复发放"。同上证据 §2.1 |
| 3.3 | 所有数值出口改读最终容器并聚合；槽数/重复限制/叠加上限集中定义 | **已验证** | 五个出口（`damageMultiplier`/`damageTakenMultiplier`/`staminaRegenMultiplier`/`healBonus`/`maxFpBonus`）全部走 `core/TalismanAggregate`（**先求和再夹一次**）；槽数/重复/同系列/上下限集中在 `core/TalismanRules` + `config/eldencombat-talisman.json`。夹具实测三件聚合 受伤×0.82 治疗+1.0 精力×1.50，槽数 1→3→1 都生效。同上证据 §2.1 |
| 3.4 | 装备/卸下/替换/背包满/死亡/重登/切维度/`reload` 后数值刷新与同步 | **已验证** | 八条路径逐条有断言：装备/卸下/替换（含组件保留）、背包满拒绝卸下且不复制、**真实死亡+重生**保留护符与自定义名、**真实关服/重启**（`TALISMAN_RESTART_PASS`）、**跨维度来回**（容器与 FP 加成跟着走且不补 FP）、配置 `reload` 后 `refreshAll`。FP 上限 100→130→100 只向下夹，反复换装当前 FP 恒为 100。同上证据 §2.1/§2.2 |
| 3.5 | 远程客户端 tooltip/装备界面不依赖服务端静态 specs | **已验证** | `TalismanItem.appendHoverText` 只读 `TalismanClientData`（服务端 payload 写入），登录/`reload` 时下发。反证：夹具手发一份改过名字的展示数据 → 客户端立刻改名、**服务端权威表未被改写**；`othermod:warrior_jar_shard` 查不到本模组条目。同上证据 §2.1 |
| 3.6 | 全 ID 命名空间迁移；`eldencombat` 旧路径字符串兼容 | **已验证** | 比较/落盘/展示统一走完整 `namespace:path`（`core/TalismanIds`，裸路径补 `eldencombat:`，非法返回空串）；`specs` 以完整 ID 为键 → 不同 namespace 同名护符互不覆盖。同上证据 §0.6/§4.6 |

## 集中验收（全部实现后）

1. 编译 + 纯逻辑测试（战灰权限/兼容与锁定、奖励去重/保存、FP 段费用、护符聚合/迁移/物品守恒、赐福拒绝规则）
2. 客户端新存档完整闭环：发现赐福 → 解锁战灰 → 装配 → 施放 → 装卸护符 → 休息 → 地图旅行，退出重进复核（不得用 `/give` 或 OP 绕过）
3. 专用服务器：无可选依赖能启动；有依赖时双玩家权限隔离、重复请求、移动离开赐福、战斗/死亡/掉线、节点删除/权限变化、异步落点失败
4. 输出实际变更文件、实现/未实现清单、命令、测试数量、日志路径；未跑场景明确标未验收

### 已完成的集中验收（阶段性）

| 日期 | 范围 | 结果 |
|---|---|---|
| 2026-10-13 20:10 | §1.1/§1.2 编译+单测+专用服务器（无依赖 / 有依赖两条） | 见 `combat-evidence/grace-baseline-20261013.md` |
| 2026-10-13 20:30 | §1.3/§1.6 赐福菜单（真实客户端+集成服务器夹具、8 项断言）+ 无回归复跑（无依赖服务端 / 有依赖服务端 / client-smoke） | 见 `combat-evidence/grace-menu-20261013.md` |
| 2026-10-13 20:38 | §2.1 战灰解锁存储（21 类/148 用例 + 真实死亡 + 真实关服重启 + 无依赖服务端） | 见 `combat-evidence/ash-unlock-store-20261013.md` |
| 2026-10-13 20:48 | §2.2 普通战灰目录与运行时（22 类/156 用例 + 新 `-PashSmoke` 夹具 5 项 + 赐福菜单复跑含拥有权 + 无依赖服务端） | 见 `combat-evidence/ordinary-ash-catalog-20261013.md` |
| 2026-10-13 20:56 | §2.3 赐福内战灰闭环（菜单夹具 16 项）+ §2.6 每段 FP 核对（22 类/160 用例；含自我更正） | 见 `combat-evidence/grace-menu-binding-and-fp-audit-20261013.md` |
| 2026-10-13 21:04 | §2.1 玩法内解锁来源（击杀玛尔基特）+ §2.5 组件保留（23 类/164 用例 + 两条夹具；整条玩法链首次无管理命令跑通） | 见 `combat-evidence/ash-reward-source-20261013.md` |
| 2026-10-13 22:00 | **§3 护符闭环六条全部到达「已验证」**：编译 0 + 纯逻辑 **26 类/178 用例/0 失败/1 跳过** + 客户端完整闭环（阶段一 33 视觉夹具 + 护符迁移/装备/多槽/收缩/FP/死亡/背包满/跨维度/客户端同步；阶段二真实关服重启）+ 战灰与赐福菜单回归 + 专用服务器 428 项检查，**7 条命令退出码全 0** | 见 `combat-evidence/talisman-container-20261013.md` |

| 2026-10-13 22:25 | §1.4 pending 清理（超时/节点被删/切维度两分支）+ §1.5 rest KEEP 语义复跑；全套复跑（编译+单测+赐福菜单+客户端闭环+真实重启+无依赖服务器，6 条命令退出码 0 且日志无 `_FAIL`） | ⚠ **同时发现**：`-PashSmoke` 战灰夹具本轮两次实跑结果不一致（一次秒杀目标、一次 0 伤害），已按"去掉固定步号断言"改好但**未复跑**，见 `combat-evidence/grace-pending-20261013.md` |

## 三大系统完成状态（2026-10-13 22:25）

18 条：§1 六条 + §2 六条 + §3 六条。**17 条「已验证」；1.4 为「部分已验证」**（超时 / 节点被删 /
切维度两分支共 4 条已断言，掉线、关服 2 条未验）。集中验收四问的当前答案：

1. **编译 + 纯逻辑测试**：✔ 26 套 / 178 用例 / 0 失败 / 1 跳过（护符聚合/迁移/守恒、战灰权限与锁定、
   FP 段费用、赐福拒绝规则都在里面）。
2. **客户端完整闭环**：✔ 但**是引擎夹具**（真实客户端 + 真实集成服务器，调原版控件的公开回调），
   不是真人鼠标键盘。玩法链条已无管理命令：击杀玛尔基特 → 解锁 → 赐福菜单装配 → 施放造成伤害 → 卸除 →
   装卸护符 → 休息恢复 FP → 旅行提交 → 退出重进复核。
   ⚠ 例外：`-PashSmoke`（战灰单段运行时）**当前是红项**——同一份代码两次实跑一次秒杀目标、
   一次 0 伤害，已定位为夹具固定步号与服务端 tick 耦合，改法已提交但**未复跑**；
   复跑通过前，§2.2 的"造成伤害"证据以 21:51 那次为准。
3. **专用服务器**：✔ 无可选依赖能启动（`runServer -PserverSmoke`，428 项共享检查）。
   ✖ **双玩家权限隔离/多人未验**（夹具是单玩家无头跑），异步落点失败也未实测。
4. **输出清单**：见本文件 + `combat-evidence/talisman-container-20261013.md` +
   `combat-evidence/grace-pending-20261013.md`；可读版汇报见 `DSH-三大系统-工作汇报-20261013.md`（含 .docx）。

### 必须随报告一起说的缺口（三条系统共同）

1. **内容是草稿**：普通战灰目录 3 件里只有 `stamp_upward_cut` 有玩法内来源（另两件无获取途径）；
   护符规则上限与战灰数值都是待平衡草稿。
2. **真人输入未验**：所有夹具走程序化回调（原版按钮 `onPress()`、`KeyMapping.click`），
   没有真实鼠标/键盘事件；普通战灰也没有真实按键路径覆盖。
3. **多人未验**：专用服务器夹具单玩家；双玩家同时请求/权限隔离/离线回调未测。
   另有备案未改项：`destined_death`/`waterfowl_dance` 的 `maxStages=3` 是死配置（见 §2.6 行）。

## §2 收口状态（2026-10-13 21:04）

§2 的 6 条全部到达「已验证」，但**两条真实缺口必须随报告一起说**：

1. **内容覆盖不足**：目录 3 件战灰里只有 `stamp_upward_cut` 有玩法内来源；另两件无可获得途径。
2. **真人操作仍未验**：所有夹具走的都是原版控件的 `onPress()` 回调（真实包、真实服务端），
   不是真实鼠标/键盘事件；八战技的真实按键路径由 `-PclientSmoke` 的 `pressKey` 覆盖，普通战灰没有等价覆盖。

§3 按这条结论落地。已查实的前置事实：**本工程任何地方都没有 Curios jar，`modpack/mods.lock.json` 里也没有
curios 条目，而构建是 `--offline`** → §3.1 要求的"先确认精确依赖再编码"结论是 **Curios 适配当前不可行**。
因此装备权威只有自制多槽容器（`eldencombat:talisman_slots` 附件，容器里放完整 ItemStack），
不引入 Curios，也不存在第二条权威。落地细节与证据见 `combat-evidence/talisman-container-20261013.md`。

## 1.3 实现落点（已完成，供复核）

- **入口**：赐福方块右键 → `GraceService.openGraceMenu` 打开菜单；四个条件全满足才抢交互
  （本项目赐福 / 玩家已发现 / 非潜行 / 访问权限校验通过），抢下时同时 DENY `useBlock`+`useItem`
  （否则 Waystones 原生界面会在事件之后覆盖菜单）；未发现时不抢，保留原生激活入口。
- **授权**：赐福 UUID 取服务端 `GraceMenu` 实例里的那份（`MenuProvider` 捕获），客户端只能传"操作 + 选择 ID + 序号"。
- **每次执行重新校验**：`GraceMenuActions.apply` 先查 `containerId` 挡过期/伪造包，再走
  `GraceOperations.localAccess`（节点有效性/发现/权限/距离/维度/战斗状态），然后才分派到
  `GraceOperations.rest` / `travel` / `AshWorkshop.apply`。
- **披露**：地图与菜单共用 `GraceAccess.authorizedNodes`，只发服务端允许披露的节点。
- **序号**：菜单与地图共享 `GraceClientActions.nextSequence()`（服务端 `requestTravel` 用它挡重放）。
- 界面全用原版控件（`GraceScreen`），失败原因一律中文短句。

### 夹具入口（下一轮改完战灰后要重跑）

```
.\gradlew.bat runClient -PgracePrototype -PgraceMenu --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21
# 通过判据：build/gracePrototypeClient/logs/latest.log 出现 GRACE_MENU_COMPLETE，且没有 GRACE_MENU_FAIL
```
