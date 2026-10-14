# 护符闭环（§3.1–§3.6）验证证据

日期：2026-10-13 21:11–22:00
工作区：`D:/codexout/mcmod`
对应任务：`docs/DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md`「DSH 续写顺序和范围」§3 六条
进度台账：`docs/DSH-THREE-SYSTEMS-PROGRESS-20261013.md`（本文件只给证据）

## 0. 结论（先说结果）

1. **§3.1 Curios 适配：当前不可行，已按"自制多槽容器"落地**。本工程任何地方都没有 Curios jar，
   `modpack/mods.lock.json` 没有 curios 条目，构建是 `--offline` → 不能凭空引用未安装版本的 API。
   装备权威**只有一条**：服务端附件 `eldencombat:talisman_slots`（`TalismanLoadout`，容器里放**完整 ItemStack**）。
2. **§3.2 一次性迁移成立**：旧 `eldencombat:talisman` 字符串（裸路径或完整 ID）迁成真实 ItemStack，
   **幂等、不重复返还、不静默清掉**（物品不存在/没有空位时旧记录原样保留并 WARN）。
3. **§3.3 数值出口全部改读最终容器并聚合**：`Talismans.damageMultiplier / damageTakenMultiplier /
   staminaRegenMultiplier / healBonus / maxFpBonus` 五个出口都走 `TalismanAggregate`；
   槽数、重复/同系列限制、倍率叠加与上下限集中在 `core/TalismanRules`（配置文件 `config/eldencombat-talisman.json`），
   默认值与旧行为**完全一致**（单槽、`allowDuplicates=false`）。
4. **§3.4 刷新与同步成立**：装备/卸下/替换/背包满/死亡/重登/切维度/`reload` 八条路径都有断言，
   最大 FP 降低按现有规则截断，**反复换护符不能补满 FP**（夹具实测 100→130→100，当前 FP 恒为 100）。
5. **§3.5客户端 tooltip 只吃同步数据**：`TalismanItem.appendHoverText` 读 `TalismanClientData`
   （服务端 payload 写入），不再读服务端静态 `specs`；反证夹具手发一份"改过名"的展示数据，
   客户端随即变成新名字，而**服务端权威表没被改写**。
6. **§3.6 命名空间兼容**：所有比较/落盘/展示都走完整 `namespace:path`（`core/TalismanIds`），
   裸路径按本模组补全；不同 namespace 同名护符不互相覆盖（夹具断言 `othermod:warrior_jar_shard` 查不到本模组的条目）。

### 必须一起说的缺口（不许当成已完成）

- **没有真人鼠标/键盘证据**：护符的所有界面/按键路径都由引擎夹具驱动（真实客户端 + 真实集成服务器，
  调用原版控件的公开回调），不是真实输入。
- **多人未验**：专用服务器夹具是单玩家无头跑（`-PserverSmoke`），没有双玩家权限隔离实测。
- **同系列规则目前约束不到任何东西**：代码支持 `series`，但现有护符数据里没有任何一件声明系列。
- **数值仍是草稿**：`maxDamageBonus=0.5 / maxDamageReduction=0.6 / maxStaminaBonus=1.0 / maxFpBonus=100 / maxHealBonus=20`
  都是待平衡的上限，不是玩法定稿。

## 1. 执行命令与结果（真实退出码）

| # | 命令 | 退出码 | 结论 |
|---|---|---|---|
| 1 | `.\gradlew.bat compileJava --offline --no-daemon` | **0** | §3 全部代码编译通过（只有既有 deprecation 警告） |
| 2 | `.\gradlew.bat test --offline --no-daemon` | **0** | **26 套 / 178 用例 / 0 失败 / 1 跳过**（新增 `TalismanIdsTest` 4、`TalismanAggregateTest` 5、`TalismanRulesTest` 5） |
| 3 | `.\gradlew.bat runClient -PclientSmoke --offline --no-daemon` | **0** | 阶段一：33 张视觉夹具 + 生命周期 + 护符全相位通过（见 §2.1） |
| 4 | `.\gradlew.bat runClient -PclientSmoke -PclientSmokeReload --offline --no-daemon` | **0** | 阶段二：真实关服/重启后护符与展示数据都保留（见 §2.2） |
| 5 | `.\gradlew.bat runClient -PashSmoke --offline --no-daemon` | **0** | 战灰回归无退化（见 §2.3） |
| 6 | `.\gradlew.bat runClient -PgracePrototype -PgraceMenu --offline --no-daemon` | **0** | 赐福菜单全部断言无退化，且**新增**"战斗期服务端强制关菜单"一条（判据 `GRACE_MENU_COMPLETE` 且无 `GRACE_MENU_FAIL`，见 §2.4） |
| 7 | `.\gradlew.bat runServer -PserverSmoke --offline --no-daemon` | **0** | 专用服务器（无 Waystones/Balm/JourneyMap）启动与 428 项共享检查通过 |

日志路径：命令 1–7 的输出分别是 `build/logs/talisman-compile7.txt`、`talisman-test4.txt`、
`talisman-smoke4.txt`、`talisman-reload2.txt`、`talisman-ashsmoke3.txt`、`talisman-gracemenu5.txt`、
`talisman-serversmoke2.txt`（对应的客户端 `latest.log` 在各自 gameDir 的 `logs/` 下）。

## 2. 逐字日志

### 2.1 阶段一（`-PclientSmoke`）——护符相位

```
TALISMAN_MIGRATE_PASS: 旧裸路径 warrior_jar_shard 一次性迁移成真实 ItemStack，重复调用不重复发放
TALISMAN_MIGRATE_UNKNOWN_PASS: 未知物品的旧记录被保留（不静默清掉），也没有凭空发放
TALISMAN_EQUIP_PASS: 真实装备 → 槽 1 保留 customName/DAMAGE 组件；出口 伤害×1.08 精力×1.20
CLIENT_NETWORK_PASS: talisman container and FP bonus survived the return trip
TALISMAN_MULTISLOT_PASS: 槽数 1→3 经配置 reload 生效；三件聚合 受伤×0.82 治疗+1.0 精力×1.50；重复同款被拒；栏满右键=卸下
TALISMAN_SLOT_SHRINK_PASS: 槽数 3→1 时槽外两件护符都退回背包（物品守恒，未销毁）
TALISMAN_FP_PASS: 上限 100→130→100 随容器变化；反复换装当前 FP 恒为 100（无法刷 FP）
TALISMAN_DEATH_DIAG(before): changingDimension=false removed=false gameMode=CREATIVE abilitiesInvulnerable=true health=20.0 invulnerableTime=0 invulnerableTo=false
TALISMAN_DEATH_ATTEMPT: frame=68 hurt=true alive=false health=0.0 changingDimension=false invulnerableTo=false
TALISMAN_CLIENT_SYNC_PASS: 客户端展示数据来自服务端同步 payload（服务端权威表未被改写）
TALISMAN_DEATH_PASS: 护符与自定义名组件在真实死亡/重生后保留，数值出口重生后重新生效
TALISMAN_INVENTORY_FULL_PASS: 背包满时拒绝卸下（明确提示），护符留在栏里且未复制
CLIENT_PERSISTENCE_SAVED: attributes, +2 heavy weapon, FP37, ash unlock, talisman(含组件); awaiting restart
```

切维度那两条（§3.4）在 40/56/57 帧：

```
CLIENT_NETWORK_PASS: FP talisman equipped before the dimension transfer
CLIENT_NETWORK_PASS: talisman FP bonus applied before the dimension transfer
CLIENT_NETWORK_PASS: talisman container survived the cross-dimension transfer
CLIENT_NETWORK_PASS: talisman FP bonus still applied inside the other dimension
CLIENT_NETWORK_PASS: talisman container and FP bonus survived the return trip
```

### 2.2 阶段二（`-PclientSmokeReload`）——真实关服/重启

```
TALISMAN_RESTART_DISPLAY_PASS: 重启后客户端收到服务端护符展示数据 4 件
ASH_UNLOCK_RESTART_PASS: 战灰解锁在真实关服/重启后保留
TALISMAN_RESTART_PASS: 护符 ItemStack（含自定义名组件）在真实关服/重启后保留，登录时重新应用数值
CLIENT_PERSISTENCE_COMPLETE: actual save/shutdown/restart passed
```

### 2.3 战灰回归（`-PashSmoke`）

```
ASH_SMOKE_LOCKED_PASS / ASH_SMOKE_COMPONENT_PASS / ASH_SMOKE_CAST_PASS（FP 100→89，扣 11）
ASH_SMOKE_HIT_PASS: eldencombat:stamp_upward_cut 单段判定造成 19.467712 点伤害（20.0 -> ...）
ASH_SMOKE_UNBIND_PASS / ASH_REWARD_PASS / ASH_REWARD_IDEMPOTENT_PASS / ASH_SMOKE_COMPLETE
```

### 2.4 赐福菜单回归（`-PgracePrototype -PgraceMenu`）

```
GRACE_MENU_RESET(before): health=20.0/20.0 food=20 fire=false onGround=false pos=(2.5, -60.0, -9.5)
GRACE_MENU_SCREEN_PASS: nodes=2 ashes=3 fp=5/100 held=（空手）
GRACE_MENU_COMBAT_AUTOCLOSE_PASS: 战斗期间服务端把菜单关了（stillValid 每 tick 复核战斗状态）
GRACE_MENU_REOPEN_AFTER_COMBAT: 战斗窗口过后重开菜单（第 291 步才留住）
GRACE_MENU_WAIT: screen=GraceScreen notice= ashes=3 nodes=2 held=Great Sword
GRACE_MENU_BIND_PASS: 玩法内解锁的战灰经菜单装配成功，notice=已装配战灰：eldencombat:stamp_upward_cut
GRACE_MENU_DUPLICATE_PASS / GRACE_MENU_HELD_SWITCH_REJECTED / GRACE_MENU_UNBIND_PASS / GRACE_MENU_COMBAT_REJECTED
GRACE_MENU_COMPLETE: engine fixture (programmatic button press); real mouse clicks still unverified
```

```
SERVER_SMOKE_PASS: 428 shared checks, no client-class dependencies
SERVER_SMOKE_COMPLETE: test datapack had 300 ticks to validate Boss damage/death
```

## 3. 实现落点（复核用）

| 关注点 | 位置 |
|---|---|
| ID 规范化 / 命名空间 | `core/TalismanIds`（裸路径补 `eldencombat:`，非法返回空串） |
| 规则集中定义（可配置） | `core/TalismanRules` + `config/eldencombat-talisman.json`（`EldenCombat.reloadConfig` 读它并 `Talismans.refreshAll()`） |
| 聚合与上下限 | `core/TalismanAggregate`（**先求和再夹一次**；单槽时等价旧实现） |
| 装备权威（容器） | `server/TalismanLoadout`（`ItemStack.OPTIONAL_CODEC.listOf()`，`copyOnDeath()`）；附件 `eldencombat:talisman_slots` |
| 出口与装备流程 | `server/Talismans`（`equip/replace/unequip/refresh/migrateLegacy/reconcile` + 读聚合的五个出口） |
| 菜单/物品入口 | `server/TalismanEquipment`（`Status`→中文原因）、`item/TalismanItem`（`use` 全状态分支 + tooltip 读同步数据） |
| 展示数据同步 | `network/TalismanDataPayload` + `client/TalismanClientData` + `ClientPayloadTrampoline.handleTalismanData`（`ModPayloads` registrar `5`→`6`） |
| 登录/重生/切维度接线 | `server/skills/SkillServerEvents`（`onPlayerLoggedIn` 刷新+下发；`onPlayerClone` 在 LOWEST 优先级重算） |
| 纯逻辑单测 | `src/test/java/com/dsh/eldencombat/Talisman{Ids,Aggregate,Rules}Test.java` |

## 4. 被夹具钉死的行为语义（实现细节，避免下次改坏）

1. **栏满 + 手上是同一件** → 右键 = **卸下（toggle）**，不是"重复被拒"；有空位时重复装备才是 `REJECTED`。
2. **背包满时拒绝卸下**（`INVENTORY_FULL`），护符留在栏里、数量不翻倍——**不**偷偷丢在地上。
3. **替换/槽数下调返回物品**：优先回背包，背包满时丢在脚下并给聊天提示；**永不销毁**。
4. **旧记录迁移**：非法 ID / 物品不存在 / 没有空位 → 旧记录原样保留 + WARN；容器里已有护符 → 清旧记录 + WARN（避免重复返还）。
5. **FP 上限只向下夹**：`setBonusMaxFp` 只做 `fp = min(fp, maxFp)`，所以换护符永远不能补满 FP。
6. **`Talismans.spec(id)` 是服务端数据包权威**，客户端 tooltip 只能吃 `TalismanClientData`（同一个 payload 机制，别的 namespace 同名护符查不到）。

## 5. 本轮修掉的两个夹具缺陷（不是玩法改动，但会影响判定）

1. **夹具 step 与服务端 tick 解耦**：`AshSmokeHarness` / `GraceClientPrototypeHarness` 的 step 走**客户端 tick**，
   而被验证的动作在**服务端 tick** 上推进。第 1 步铺 315 块石头、以及 200 tick 的战斗窗口，
   都会让服务端落后 → 固定 step 的断言会误报（实测："施放应已结束"、"matched buttons=0"）。
   现在改成**轮询等待真实条件**（收招、菜单留住、按钮出现、回执到达），超时上限内仍然照原样失败。
2. **夹具世界会被自己弄脏**：超距离用例把玩家扔到 x=200 的半空，摔伤被写进存档，几次运行后玩家变成 0 血开局、
   客户端直接停在死亡界面。现在夹具开局复位玩家状态（必要时走**原版重生路径**让客户端退出死亡界面），
   超距离用例返回时清摔落距离并补满，收尾再补满存盘一次。

## 6. 备份

`docs/code-backups/20261013-talisman-container/`（改前副本 8 份：`Talismans`、`TalismanSpec`、`ModAttachments`、
`ModPayloads`、`TalismanEquipment`、`TalismanItem`、`ClientPayloadTrampoline`、`SkillServerEvents`）。
本轮新增文件不覆盖既有文件。
