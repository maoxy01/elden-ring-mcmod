# 战灰玩法内解锁来源（§2.1 后半）+ §2.5 组件保留 验证证据

日期：2026-10-13 20:57–21:04
工作区：`D:/codexout/mcmod`
对应任务：`docs/DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md` §2.1（奖励来源）、§2.5（保留强化/质变/原有组件；追忆与锁定战技不可覆盖）

## 0. 结论

1. **玩法内解锁来源接上了**（§2.1「奖励来源留给现有探索/奖励能力」）：击杀**玛尔基特** → 解锁 `eldencombat:stamp_upward_cut`，
   只发给击杀者本人、幂等、非玩家来源不解锁。表在 `core/AshRewardTable`（纯逻辑，可单测），受理在 `server/skills/AshRewards`。
2. **整条玩法链第一次用真玩法跑通**（无管理命令脚手架）：
   `击杀玛尔基特 → 解锁 → 赐福菜单里显示"可装配" → 装配成功 → 施放造成伤害 → 卸除`。
   上一轮那条"成功装配靠管理命令搭台"的保留意见，本轮**作废**（赐福菜单夹具已改为击杀解锁）。
3. **§2.5 已验证**：绑定不改变武器强化/质变/自定义名；追忆武器拒绝覆盖战技。
4. ⚠ **覆盖面必须如实说**：目录 3 件战灰里**只有 1 件**（`stamp_upward_cut`）在玩法内能拿到；
   另两件只有目录条目、**没有来源**。不许把"目录 3 件"说成"3 件都能获得"。

## 1. 执行命令与结果

| # | 命令 | 真实退出码 | 结论 |
|---|---|---|---|
| 1 | `.\gradlew.bat test --offline --no-daemon` | **0** | 23 类 / **164 用例 / 0 失败 / 1 跳过**（新增 `AshRewardTableTest` 4 例） |
| 2 | `.\gradlew.bat runClient -PashSmoke --offline --no-daemon` | **0** | 追忆拒绝覆盖 + 组件保留 + 玩法内解锁 + 幂等 全部通过 |
| 3 | `.\gradlew.bat runClient -PgracePrototype -PgraceMenu --offline --no-daemon` | **0** | 赐福菜单夹具 17 项断言全过，**解锁来自击杀而非管理命令** |
| 4 | `.\gradlew.bat runServer -PserverSmoke --offline --no-daemon` | **0** | 无依赖专用服务器正常；新的事件监听类不影响启动 |

## 2. 逐字日志

### `-PashSmoke`（§2.5 + 玩法内解锁 + 幂等）

```
ASH_SMOKE_LOCKED_PASS: 追忆武器拒绝覆盖战技
ASH_SMOKE_COMPONENT_PASS: 绑定后 强化+2 / 质变HEAVY / 自定义名 全部保留
ASH_SMOKE_SETUP: catalog=3 ash=eldencombat:stamp_upward_cut fpCost=11.0 目标血量=20.0
ASH_SMOKE_CAST_PASS: eldencombat:stamp_upward_cut 起手成功，FP 100→89（扣 11）
ASH_SMOKE_HIT_PASS: eldencombat:stamp_upward_cut 单段判定造成 18.467712 点伤害（20.0 -> 1.5322876）
ASH_SMOKE_UNBIND_PASS: 卸除后主手不再解析出战灰
ASH_UNLOCK_GRANTED: player=380df991-… ash=eldencombat:stamp_upward_cut
ASH_REWARD_GRANTED: entity=eldencombat:margit player=380df991-… ash=eldencombat:stamp_upward_cut
ASH_REWARD_PASS: 击杀玛尔基特解锁 eldencombat:stamp_upward_cut，当前共解锁 1 件
ASH_REWARD_IDEMPOTENT_PASS: 第二次击杀没有新增解锁（仍 1 件）
ASH_SMOKE_COMPLETE
```

> 伤害从上一轮的 16.728 变成 **18.4677**，正是因为这一轮的探针武器带 **+2 / 质变 HEAVY**——
> 顺带证明武器成长倍率确实作用在普通战灰上（`SkillHitEffects.hurt` 里乘 `WeaponStacks.damageMultiplier`）。

### 赐福菜单夹具（玩法链，无管理命令）

```
GRACE_MENU_SCREEN_PASS: nodes=2 ashes=3 fp=5/100 held=（空手） rejection=当前主手武器不支持战灰
GRACE_MENU_OWNERSHIP_PASS: 未解锁战灰被拒，notice=尚未获得此战灰
GRACE_MENU_REST_PASS / FORGERY_REJECTED / TRAVEL_REJECTED / RANGE_REJECTED / TRAVEL_DISPATCH_PASS
ASH_REWARD_GRANTED: entity=eldencombat:margit player=380df991-… ash=eldencombat:stamp_upward_cut
GRACE_MENU_REWARD_UNLOCK: 击杀玛尔基特解锁 stamp_upward_cut 并换上大剑（玩法内来源，非管理命令）token=0:eldencombat:great_sword
GRACE_MENU_REOPEN_AFTER_COMBAT: 战斗窗口过后重开菜单
GRACE_MENU_BIND_PASS: 玩法内解锁的战灰经菜单装配成功，notice=已装配战灰：eldencombat:stamp_upward_cut
GRACE_MENU_DUPLICATE_PASS: 重复装配被当作可读空操作，notice=无法装配：这件武器已经绑定了战灰，请用替换
GRACE_MENU_HELD_SWITCH_REJECTED: 换手后旧 token 被拒且未误改新武器，notice=主手武器已变化（换手或换槽），已刷新，请重试
GRACE_MENU_UNBIND_PASS: 菜单卸除成功，主手不再绑战灰
GRACE_MENU_COMBAT_REJECTED: 战斗期间休息被拒、fp 仍为 5
GRACE_MENU_COMPLETE
```

**夹具自带前置**（写在日志与代码注释里，不当玩法来源）：清掉同一世界里上一次运行留下的解锁与背包，
让断言不依赖历史状态（服务端存档是持久的，这一点原本会让重复运行结果漂移）。

## 3. 附带查实的一条真实规则（夹具先撞上，再被写成断言）

**击杀 Boss 会让击杀者进入 200 tick 的"战斗期间"**（`GraceService.damage` 对
`event.getSource().getEntity() instanceof ServerPlayer` 也计时），因此**菜单会被 `stillValid` 自动关掉**。
夹具现在先断言"战斗期间菜单确实已被关闭"，等窗口过去再重开——
这条规则在 `GRACE_MENU_COMBAT_REJECTED`（战斗期间休息被拒）之外又多了一层证据。

## 4. 实现要点

- `core/AshRewardTable`（纯逻辑）：一个来源 → 若干战灰；`ashesFor` 规范化输入、未知来源返回空、不抛异常；
  `allRewardedAshes()` 用于覆盖率报告。
- `server/skills/AshRewards`（`@EventBusSubscriber`，无缺失依赖类型）：只认"玩家击杀"，
  幂等（`grant` 返回 false 就当没发生），并给玩家一条可读播报。
- 硬不变量（单测）：**奖励的战灰必须真的在目录里**——否则玩家拿到了也装不上，等于假奖励。

## 5. 仍未验证（不得算作完成）

1. **覆盖率 1/3**：`spinning_slash`、`heavy_thrust` **没有任何玩法内来源**，现在只能靠管理命令拿到。
   这不影响它们"能被装配"（菜单与技术路径都通），但**不能**说战灰系统已经"内容完整"。
2. 真人鼠标；夹具仍走原版控件的 `onPress()` 回调。
3. 普通战灰只实测了 `stamp_upward_cut` 的判定形状；另两件未打过真实目标。
4. 多人：击杀归属（两个玩家同时打）只按"伤害来源实体"判定，未做双人实测。
5. 平衡值仍是草稿；未做手感/数值评估。
