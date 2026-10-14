# dsh：纯原版战斗改造计划（去掉 Epic Fight）

日期：2026-10-12。工作区：`D:/codexout/mcmod`。
用户指令（本轮目标，优先于此前所有交接文档）：

> 完成战斗动作设计、护符系统、药物加成。我对目前的战斗动作设计不满意，**去掉史诗战斗模组**；
> 只需要设计**简单的战技释放动作**；**不要给布偶添加多余的关节**；**普攻动作就用原版那样**
> （例如原版挥动钻石剑的动作）。

## 0. 结论：架构决定

| 项 | 旧（Epic Fight 路线） | 新（本计划） |
| --- | --- | --- |
| 依赖 | `curse.maven:epic-fight-mod-405076` | **无任何战斗模组依赖**，只用 NeoForge + 原版 |
| 玩家模型 | EF biped 骨架 + 自定义关节 | **原版 `PlayerModel`（HumanoidModel），不加任何关节** |
| 普攻 | EF `Moveset.replaceComboAttacks` 连段 | **完全原版**：物品自带 `Attributes.ATTACK_DAMAGE/ATTACK_SPEED`，原版挥剑动画与冷却 |
| 战技动作 | EF `AttackAnimation` + 20 关节 attributes 动画 | **简单的原版关节姿态动画**：只驱动 `head/body/rightArm/leftArm/rightLeg/leftLeg`，由服务端状态驱动 |
| 战技输入 | EF `CPSkillRequest` / `SkillContainer` | 自建按键 → `CombatIntentPayload`（已有）→ 服务端状态机 |
| 判伤 | EF `EpicFightDamageSource`（impact/stun） | 原版 `playerAttack` + 自建削韧（`MargitEntity.addStagger` 已有） |
| 玩家皮肤 | 保留 | 保留（原版模型天然保留） |

**为什么可行**：仓库里本来就有一整套不依赖 EF 的战斗代码（`core/`、`server/ServerCombat`、
`server/HitResolver`、`network/*`、`client/CombatHud`、`effects/*`），只有 24 个文件引用 EF。
EF 只是把"玩家动作/输入/动作状态/伤害"接管了，把这些换成原版 + 自建即可。

## 1. 引用 Epic Fight 的 24 个文件与处置

删除（EF 专属，无替代价值）：

- `compat/EpicFightIntegration.java`（25 处）、`compat/RemembranceWeaponPresets.java`（11）
- `compat/skills/RemembranceSkills.java`、`RemembranceWeaponSkill.java`、`WaveOfGoldSkill.java`
- `compat/animations/RemembranceAnimations.java`、`RemembranceAttackAnimation.java`
- `compat/BossParryingSkill.java`
- `client/EpicFightControls.java`
- `mixin/StarcallerHandLayerMixin.java`
- `testing/EpicFightSmokeHarness.java`、`WeaponSmokeHarness.java`、`WeaponServerSmokeHarness.java`、`RemembranceSmokeHarness.java`

改写（保留功能，换掉 EF 依赖）：

| 文件 | EF 用途 | 改法 |
| --- | --- | --- |
| `compat/MargitDamageSource.java` | EF 伤害类型标签 | 移到 `server/`，原版 `DamageSource` + 自建标记 |
| `server/skills/SkillDriver.java` | EF `ServerPlayerPatch` 取消动画 | 去掉，改由自建动画驱动清理 |
| `server/skills/SkillInterrupts.java` | EF `CHANGE_INNATE_SKILL` / `TAKE_DAMAGE_POST` | 换 NeoForge `PlayerEvent` + 原版 `LivingHurtEvent` |
| `server/skills/RemembranceRuntime.java` | EF `AttackAnimation.getPhaseByTime` 取命中体 | 改用自建 `HitWindow`/`HitResolver`（已有） |
| `server/skills/SkillHitEffects.java` | `EpicFightDamageSource` | 原版 `playerAttack` + 自带无敌帧处理 + 削韧 |
| `client/WeaponItemRenderer.java` | EF `RenderItemBase` | 改成原版 `BlockEntityWithoutLevelRenderer`（BEWLR），用 `WeaponMesh` 自绘 |
| `client/EldenCombatClient.java` | 1 处 EF 注册 | 删掉该处 |
| `command/EldenCombatCommand.java`、`SkillTestCommand.java` | EF 技能/状态查询 | 改成本地状态查询 |
| `src/main/resources/META-INF/neoforge.mods.toml` | `epicfight` 依赖声明 | 删除该依赖段 |
| `build.gradle` | EF 依赖 + epicSmoke | 删除依赖与 epicSmoke，保留离线构建 |
| `data/eldencombat/capabilities/weapons/*.json` | EF capability | 提交 EF 前先保留文件但不再加载；后续清理 |

## 2. 新战斗系统设计

### 2.1 普攻（完全原版）

- 41 件武器都用原版 `SwordItem`/`AxeItem` 或自定义 `Item` + `Attributes` 修饰符，
  不注册任何自定义攻击动画或连段钩子。
- 保留已有的武器外观渲染（BEWLR 自绘网格）——这是"外观"，不是"动作"。
- 删除 `EpicFightIntegration.COMBO_ATTACK` 之类钩子。

### 2.2 战技（简单释放动作）

- 服务端状态机复用现有（**已经是 EF 无关的纯逻辑**）：
  `RemembranceSkillSpec`（参数）、`SkillCastState`（阶段）、`PlayerFpState`（FP）、
  `SkillTransactions`（原子扣费）、`SkillMovementBudget`（位移预算）、`SkillMotion`、
  `SkillDriver`（时间线）、`RemembranceRuntime`（八战技行为）、`GoldenWaveEntity`/`SkillEffectEntity`（命中体与特效）。
- 新增 `server/skills/SkillAnimState`（或复用 cast 阶段的同步字段）：把
  `{skillId, stage, stageTicks, castId}` 同步给客户端。
- 新增客户端 `client/SkillPoseAnimator`：在 `RenderPlayerEvent.Pre`（或
  `RenderLivingEvent`）里，按同步到的阶段/进度**只旋转原版关节**：
  - 起手：右臂抬起 + 躯干微转
  - 生效：右臂前挥
  - 收招：回到原版姿态
  每个战技一份**关键帧表**（度数，作用于 `rightArm.xRot/yRot/zRot`、`body.yRot`、`head.yRot` 等），
  数据放 `assets/eldencombat/skill_poses/<skill>.json`，方便后续调参不重编译。
- 不新增任何关节、不复用旧的 `skeleton/*` 自定义骨架。

### 2.3 护符系统（talisman）

- 物品：若干部位中立的护符（`TalismanItem`），佩戴在**专用护符槽**（用 attachment 存 ItemStack，不占背包格子）。
- 服务端每 tick/事件时施加效果：属性修饰符、抗性、削韧加成、FP 回复等。
- 用 `/eldencombat talisman` 命令与 GUI 之外的简单交互（潜行+右键装备/卸下）做第一版。
- 数据驱动：`data/eldencombat/talismans/<id>.json`（属性修饰符 + 触发条件）。

### 2.4 药物加成（consumable buffs）

- 扩展已有 `HealingFlaskItem` → `ConsumableItem`：使用后给 `MobEffectInstance` 列表 + 即时治疗/FP。
- 数据驱动：`data/eldencombat/consumables/<id>.json`（效果、时长、等级、冷却、治疗量）。
- 不在原版药水体系外另起一套；用原版 `MobEffect` 承载加成。

## 3. 执行顺序（每步都要能编译通过）

1. **去依赖**：`build.gradle`、`neoforge.mods.toml`、删除 EF 专属 14 个文件。→ 编译通过
2. **改写保留件**：`WeaponItemRenderer`（BEWLR）、`MargitDamageSource`、`SkillHitEffects`、
   `SkillDriver`、`SkillInterrupts`、`RemembranceRuntime`、命令、`EldenCombat` 装配。→ 编译 + 53 项测试
3. **战技输入**：自建按键（8 个战技可用 1 键切换 + 1 键释放，或 8 个直键）→ `CombatIntentPayload`。
4. **战技动作**：`SkillPoseAnimator` + 姿态 JSON（先做黄金波动，再铺七个）。
5. **护符系统**。
6. **药物加成**。
7. **验收**：`build`、用户实机手动验收（不自动退出）。

## 4. 迁移中的约束

- **不覆盖其他 agent 未提交的改动**；改动前先读当前文件。
- 备份在 `docs/code-backups/`（已有 `20261011-recovery-fx-r2`）。
- 原版普攻意味着**删除** EF 连段契约；此前写给 Codex 的"普攻连击契约"作废。
- 旧的 `skeleton/*`、`client/CombatPlayerModel`、`PlayerMeshRenderer`、`PlayerVisual`、
  `mixin/LivingRendererAccessor` 属于"多余的关节"路线，**本轮起不再用于玩家**；先保留文件避免影响其他 agent，
  确认无用后再删。
- `data/eldencombat/capabilities/weapons/*.json` 是 EF 的能力配置，EF 移除后不再被加载；
  保留文件但计划清理（不在本轮删，避免与其他 agent 冲突）。

## 5. 进度记录

| 步骤 | 状态 |
| --- | --- |
| 0 架构决定 | 完成（本文件） |
| 1 去依赖 | **完成**：`build.gradle`/`mods.toml` 去掉 EF；9 个引用 EF 的文件全部改完；`src` 内已无任何 `yesman.epicfight` 代码引用 |
| 2 改写保留件 | **完成**：`MargitDamageSource` → `server/` 原版伤害源；`SkillHitEffects`/`SkillInterrupts`/`SkillDriver`/`RemembranceRuntime`/两个命令类全部原版化；`WeaponItemRenderer` 删除（BEWLR 已覆盖全部显示上下文） |
| 3 战技输入 | **完成**：`IntentType.SKILL` + 默认键 `C` + `SkillCasting`（服务端唯一入口）；同时修掉"6 个 KeyMapping 只注册了 1 个"的旧 bug |
| 4 战技动作 | **完成**：`core/SkillPoseData`（纯逻辑）+ `client/SkillPoseAnimator` + `mixin/PlayerModelPoseMixin` + 8 个 `skill_poses/*.json`；只驱动原版六关节 |
| 5 护符系统 | **完成**：`TalismanItem` + 玩家附件护符栏 + 4 件数据包护符 + 数值接入伤害/减伤/精力恢复/FP 上限/治疗 |
| 6 药物加成 | **完成**：`ConsumableItem` + 3 种数据包药物（原版右键使用）+ 数据包效果解析 |
| 7 验收 | **本机离线部分完成**（编译 + 67 个单测全绿 + jar 资源齐全）；**实机画面验收交给 Codex**，见 [`DSH-VANILLA-COMBAT-HANDOFF-CODEX.md`](DSH-VANILLA-COMBAT-HANDOFF-CODEX.md) |

### 5.1 本轮删除的旧资产（确认无引用后删除）

- `assets/eldencombat/animmodels/animations/*.json`（10 个，EF attributes 格式）→ 由 `skill_poses/*.json` 取代；
- `data/eldencombat/capabilities/weapons/*.json`（41 个，EF 武器能力档）；
- `data/eldencombat/skill_parameters/parrying.json`（EF 技能档）；
- 客户端"额外关节"整条链路：`CombatPlayerModel` / `PlayerMeshRenderer` / `PlayerVisual` / `CombatArmorModel` / `CombatHeldItemLayer` / `CombatHandRenderer` / `mixin/LivingRendererAccessor`
  （这条链路本来就没有 `@SubscribeEvent`、处于死代码状态，删掉即"不再给玩家模型加关节"）；服务端命中判定仍用 `skeleton/*` 采样，未动。

### 5.2 2026-10-12 追加修复（Codex 实机审查后）

Codex 审查指出"武器尺寸不对、战技放不出来"，其中三条归 dsh，已修：

| 问题 | 修复 |
| --- | --- |
| `ClientCombat` 未接客户端事件总线 → 按键全哑（R 切换战斗模式也失效） | 加类级 `@EventBusSubscriber(value = Dist.CLIENT)`（GAME 总线），**不**手动注册（二选一防重复）；删除 EF 时代"不要补注解"的过时说明 |
| `ServerCombat` 未接事件总线 → 普攻窗口/翻滚/精力/tick 全停 | 实例订阅者必须显式注册：新增 `ServerCombat.register()`（`synchronized` + 一次性标志），在 `EldenCombat` 构造期调用 |
| `CombatHud` 未注册且没有蓝条 | 加类级注解 + FP 蓝条（只读 `ClientSkills` 镜像）+ 施放中"战技名/段号"显示 |
| 战技被拒绝时无提示，易误判成"没实现" | `SkillCasting` 增加动作栏原因：非绑定武器 / 战斗模式未开 / FP 不足 / 冷却中 / 不在追击窗口 / 已达段数上限 |

**不再只报单测数量，本轮补了运行时证据**：

- `ServerSmokeHarness`（`-PserverSmoke`，专用服务器）新增总线自检：合成投递 1 次 `ServerTickEvent.Post`，
  断言 `ServerCombat` 恰好执行 1 次（0=没注册，2=重复注册）；再断言真实 tick 下"每 tick 只推进一次"（5 tick → 5 次）。
  实测日志：`SERVER_BUS_PASS`×2 + `SERVER_SMOKE_PASS: 420 shared checks`。
- `ClientSmokeHarness`（`-PclientSmoke`）把两处"直接调 `ClientCombat.send`"改成**真实按键**（`KeyMapping.click`），
  其 PASS 才等于"客户端总线接线通 + 一次按键只发一次 + 战技端到端可释放"。

> 武器显示尺寸/握持点按分工归 Codex，dsh 本轮**未改** `EquipmentRenderer` 与 `item_skins/*.json`。
> 逐条回执与残余问题见 [`DSH-COMBAT-REVIEW-FIXES-RESPONSE-20261012.md`](DSH-COMBAT-REVIEW-FIXES-RESPONSE-20261012.md)。


