# dsh → Codex 交接：纯原版战斗（去 Epic Fight / 战技动作 / 护符 / 药物）

> 日期：2026-10-12 ｜ 分支状态：`D:\codexout\mcmod` 工作区（**无 git**，改动前请自行备份）
> 上一份交接：[`DSH-COMBAT-HANDOFF-20261012.md`](DSH-COMBAT-HANDOFF-20261012.md)（EF 时代，已作废，仅作历史参考）
> 计划与决策记录：[`DSH-VANILLA-COMBAT-PLAN.md`](DSH-VANILLA-COMBAT-PLAN.md)
> **⚠️ 2026-10-12 追加修复（务必先读）**：Codex 实机审查发现"按键/服务端循环没接事件总线"，
> 已修并补运行时证据，逐条回执见 [`DSH-COMBAT-REVIEW-FIXES-RESPONSE-20261012.md`](DSH-COMBAT-REVIEW-FIXES-RESPONSE-20261012.md)。
> 本文 §4.4/§5 里"需要实机验收"的结论**仍然有效**，但判断标准请以回执文档里的 `SERVER_BUS_PASS` /
> `CLIENT_NETWORK_PASS` 与动作栏拒绝提示为准。

---

## 0. 结论先行

1. **Epic Fight 已彻底移除**：依赖、`mods.toml` 依赖段、EF 相关构建任务、9 个引用 EF 的 Java 文件全部改完；
   `src` 内已**没有**任何 `yesman.epicfight` 代码引用（唯一命中是 `MargitDamageSource` 里一句解释性注释）。
2. **战技动作改成"只驱动原版六个关节"**：`head / body / rightArm / leftArm / rightLeg / leftLeg`，
   通过 mixin 注入 `PlayerModel.setupAnim` 的 TAIL 覆盖姿态；**没有给玩家模型加任何关节**，
   **普攻完全是原版**（原版挥剑动画 + 原版冷却），走路/潜行/游泳也不受影响。
3. **护符系统、药物加成已实现**，数值全部走数据包（`/reload` 生效，不用重编译）。
4. **本机可验证的部分已经验证**：`.\gradlew.bat build --offline` 绿，**67 个单测 0 失败**，
   产物 `build/libs/eldencombat-0.2.0.jar` 内含全部新资源。
5. **交给你的部分**：实机画面验收（动作好不好看、mixin 是否真的注入、护符/药物图标）与美术调参。
   下面 §5 是逐条清单，§4 是"改哪个文件、改什么字段"的数据契约。

---

## 1. 我改了什么（按"谁负责什么"排列）

### 1.1 去依赖（构建层）

| 文件 | 改动 |
| --- | --- |
| `build.gradle` | 删 `curse.maven:epic-fight-mod-405076`；删 `weaponSmoke` / `epicSmoke` 运行块；jar 排除只剩 3 个 smoke harness；描述改为"纯原版战斗扩展" |
| `src/main/resources/META-INF/neoforge.mods.toml` | 删 `[[dependencies.eldencombat]] modId="epicfight"`；描述改写 |

> 现在启动游戏**不需要**装 Epic Fight，装反而不保证兼容（EF 会接管玩家渲染，和我们的 mixin 抢姿态）。

### 1.2 服务端（原版化改写）

| 文件 | 改动 |
| --- | --- |
| `server/MargitDamageSource.java`（新，原 `compat/` 下） | 不再是 `EpicFightDamageSource` 子类，只提供 `DamageSource of(boss, attack)` → 原版 `mobAttack` |
| `server/skills/SkillHitEffects.java` | 战技伤害改原版 `playerAttack`/`indirectMagic`；**每次命中前清 `invulnerableTime`**（否则多段战技的后续段会被原版 20 tick 无敌帧吃掉） |
| `server/skills/SkillInterrupts.java` | EF 的 `CHANGE_INNATE_SKILL` / `TAKE_DAMAGE_POST` → NeoForge `LivingEquipmentChangeEvent` / `LivingDamageEvent.Post`，改为 `@EventBusSubscriber` |
| `server/skills/SkillDriver.java` | `cancel()` 里 EF 的"复位到 BIPED_IDLE"删掉（客户端靠状态归零回到原版姿态）；新增 `registerAll(Runtime)`；进入收招时补一次 `sync` |
| `server/skills/RemembranceRuntime.java` | `melee()` 不再借 EF `AttackAnimation.getPhaseByTime().getCollidingEntities()`，改为**自研扫掠**：从胸口沿视线伸出 `effectRange` 格、半径 `max(0.55, effectRadius*0.35)` 的胶囊，用 `Collision.capsuleHitsBox` 判交 |
| `server/skills/SkillCasting.java`（新） | 战技的**唯一服务端入口**：`request()`（按主手武器决定技能）与 `requestById()`（命令用），都走同一套 `SkillTransactions`（FP/冷却/段数）校验 |
| `server/Talismans.java`（新） | 护符栏与数值应用（见 §3） |
| `server/Consumables.java`（新） | 药物效果施加（见 §3） |
| `server/CombatDataEvents.java`（新） | 在 `AddReloadListenerEvent` 上挂护符/药物数据包监听 |
| `server/ServerCombat.java` | 新增 `SKILL` 意图处理；**施放期间冻结其他动作输入**（只留"再按一次战技=追击"与"锁定"）；`CANCEL` 同时取消施放；精力恢复带护符倍率；药瓶治疗吃护符加成 |
| `command/EldenCombatCommand.java`、`command/SkillTestCommand.java` | 去掉 EF patch/技能槽查询，改成本地状态查询；`/eldencombat fp skill cast <id>` 走 `SkillCasting` |

### 1.3 客户端（新的动作系统）

| 文件 | 作用 |
| --- | --- |
| `core/SkillPoseData.java`（新） | 姿态数据的**纯逻辑**：解析、线性插值、阶段回退；不依赖任何 MC 类型 |
| `client/SkillPoseAnimator.java`（新） | 资源加载（`skill_poses/*.json`）、网络状态镜像、把当前姿势写进 `ModelPart` |
| `mixin/PlayerModelPoseMixin.java`（新） | 注入 `PlayerModel.setupAnim(LivingEntity;FFFFF)V` 的 TAIL；改完关节后重新同步袖子/裤腿/帽子/外套 |
| `resources/eldencombat.mixins.json` | `client` 列表换成 `PlayerModelPoseMixin` |
| `network/SkillCastPayload.java`（新） | 服务端 → 客户端（含旁观者）的施放快照：`entityId, skillId, castId, stage, stageIndex, stageTicks` |
| `client/EldenCombatClient.java` | 删 EF 手持渲染注册；**注册全部 7 个 KeyMapping**（旧代码只注册了 `DRINK`，导致重击/翻滚/弹反/锁定/切换战斗全都收不到按键）；新增姿态与武器校正两个重载监听 |
| `client/EquipmentRenderer.java` | 统一 GUI/地面/展示框/第一人称/第三人称的归一化（原先只有 GUI/FIXED 归一化，手持会按 1/16 格原尺寸画成 16 格长），并接入 `item_skins` 的手持校正 |
| `client/WeaponCorrections.java`（新） | 读 `item_skins/<id>.json` 的 `eldencombat` 字段（平移/旋转/缩放），**调手持角度不用重编译** |

### 1.4 删除的文件 / 资源（确认无引用后删）

- Java：`compat/EpicFightIntegration`、`compat/RemembranceWeaponPresets`、`compat/BossParryingSkill`、`compat/skills/*`、`compat/animations/*`、
  `client/WeaponItemRenderer`、`client/EpicFightControls`、`mixin/StarcallerHandLayerMixin`、
  `testing/{EpicFightSmokeHarness,WeaponSmokeHarness,WeaponServerSmokeHarness,RemembranceSmokeHarness}`
  （上一轮已删，本轮确认）
- **本轮删掉"额外关节"整条客户端链路**（这正对应你说的"不要给布偶添加多余的关节"）：
  `CombatPlayerModel`、`PlayerMeshRenderer`、`PlayerVisual`、`CombatArmorModel`、`CombatHeldItemLayer`、`CombatHandRenderer`、`mixin/LivingRendererAccessor`。
  这条链路此前**没有 `@SubscribeEvent`、属于死代码**，但留在仓里容易被误启用，所以直接删除。
  服务端命中判定用的 `skeleton/*`（`PlayerAssets` / `AnimationClip` / `HitVolumes`）**保持不动**。
- 资源：`assets/eldencombat/animmodels/animations/*.json`（10 个 EF attributes 动画）、
  `data/eldencombat/capabilities/weapons/*.json`（41 个 EF 能力档）、`data/eldencombat/skill_parameters/parrying.json`。

---

## 2. 玩法与操作（实机按这个验）

### 2.1 按键（全部可在"按键设置 → 法环战斗"改绑）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| 切换战斗模式 | `R` | 战斗模式关闭时，左键仍是原版挖掘/攻击 |
| 轻攻击 | 鼠标左键 | 战斗模式内被接管为 `LIGHT` 意图（原版挥剑表现 + 自建命中窗口/精力） |
| 重击 | `G` | 按住蓄力、松手释放 |
| 翻滚 | `左Alt` | |
| 锁定 | `鼠标中键` | |
| 弹反 | `X` | |
| 喝药 | `V` | 药瓶，走战斗系统的喝药时间线 |
| **战技** | **`C`** | **按一次释放主手武器绑定的战技；在追击窗口内再按一次进入下一段** |

### 2.2 战技与武器绑定（8 个）

绑定关系来自 `remembrance_parameters.json` 的 `itemId`，**换武器即换战技**，没有技能栏/技能树。

| 战技 | 武器 | 蓄势/生效/收招（tick） | 追击 |
| --- | --- | --- | --- |
| `wave_of_gold` 黄金波动 | 神躯化剑 | 10 / 4 / 5 | — |
| `gold_breaker` 黄金碎击 | 玛莉卡的石槌 | 8 / 16 / 5 | — |
| `destined_death` 命定之死 | 玛利喀斯的黑剑 | 8 / 24 / 5 | — |
| `takers_flames` 掠夺火焰 | 亵渎圣剑 | 9 / 4 / 5 | — |
| `waterfowl_dance` 水鸟乱舞 | 玛莲妮亚的义手刀 | 10 / 36 / 5 | — |
| `starcaller_cry` 唤星 | 碎星大剑 | 8 / 6 / 5 | 6 / 8 / 5，窗口 12t，最多 2 段 |
| `bloodboon_ritual` 授血仪式 | 蒙格温圣矛 | 6 / 4 / 2 | 6 / 4 / 2，窗口 10t，最多 3 段 |
| `cursed_blood_slice` 咒血斩击 | 蒙葛特咒剑 | 8 / 6 / 5 | 4 / 4 / 5，窗口 10t，最多 2 段 |

### 2.3 护符（4 件）

右键装备到护符栏（同一时间只生效一件，**再右键同一件即卸下**，换护符时旧的退回背包）。
数值由数据包给出，`/reload` 生效。

| 护符 | 效果 |
| --- | --- |
| 战士壶碎片 | 精力恢复 +20%，伤害 +8% |
| 拉达冈的烙印 | FP 上限 +30，精力恢复 +10%，**受伤 +10%（有代价）** |
| 绿龟护符 | 精力恢复 +30% |
| 龙印护盾护符 | 受伤 −18%，治疗 +1 |

### 2.4 药物（3 种）

**原版右键饮用**（不是 `V` 键）：喝下后按数据包给药水效果。

| 药物 | 效果 |
| --- | --- |
| 火焰赐予我力量 | 力量 II 60s + 生命恢复 I 20s |
| 铁壶香药 | 抗性提升 III 20s + 缓慢 II 20s |
| 星光碎片 | 治疗 2 + 生命恢复 I 30s + 吸收 I 30s |

---

## 3. 数据契约（**你调参只需要改这些文件，不用重编译**）

### 3.1 战技动作姿态：`src/main/resources/assets/eldencombat/skill_poses/<skillId>.json`

时间单位是**该阶段内经过的 tick**，角度单位是**度**。**只允许出现 `head/body/rightArm/leftArm/rightLeg/leftLeg`**
（出现别的关节会被 `SkillPoseData.JOINTS` 过滤掉，并有单测兜底）。

```json
{
  "stages": {
    "WINDUP":  {"rightArm": [[0,[0,0,0]], [10,[-140,-20,25]]]},
    "ACTIVE":  {"rightArm": [[0,[-140,-20,25]], [4,[-35,15,-10]]]},
    "ACTIVE#2":{...},              // 第 2 段生效的覆盖写法；缺省则回退到 "ACTIVE"
    "RECOVERY":{"rightArm": [[0,[-35,15,-10]], [5,[-5,0,5]]]}
  }
}
```

角度约定（原版模型空间，**+Y 向下**）：

| 关节 | 数值含义 |
| --- | --- |
| `rightArm.xRot` | `-90` ≈ 手臂前平举；`-180` ≈ 举过头顶；`+90` ≈ 向后甩 |
| `rightArm.zRot` | 正值 = 右臂向外张开（左臂相反） |
| `body.xRot` | 正值 = 上身前倾 |
| `body.yRot` / `head.yRot` | 躯干/头部扭转（**手臂不是躯干的子节点**，扭腰要连手臂的 `yRot` 一起写） |

硬约束（单测会检查，别踩）：

1. 每个关节的关键帧 **tick 必须严格递增、首个必须是 0**；
2. 每个阶段每个关节的 **末帧 tick 必须等于该阶段时长**（`windupForStage` / `activeForStage` / `recoveryTicks`），
   改时长要同时改参数与姿态，否则动作和判定会对不上；
3. `RECOVERY` 与 `FOLLOWUP_WINDOW` 可以不写——那时会**冻结生效段最后一帧**（不会弹回站立）。

### 3.2 武器手持/展示姿态：`assets/eldencombat/item_skins/<weaponId>.json`

```json
{"renderer": "eldencombat:refined",
 "eldencombat": {"translation": [0,0,0], "rotation": [0,0,0], "scale": 1}}
```

- `translation` 单位**格**；`rotation` 单位**度**，按 **Z→Y→X** 依次施加；`scale` 可单值或三值；
- 作用位置在"物品格中心"（GUI / 掉落物 / 展示框 / 第一人称 / 第三人称**共用同一份**）；
- 目前 33+8 把武器全部是**零校正**（`[0,0,0]/[0,0,0]/1`），所以实机上武器是"按建模朝向摆在手心里"。
  **如果你看到某把武器握持方向不对，改这个文件即可**，改完 `F3+T` 重载资源就生效，不用重编译。

### 3.3 护符：`data/eldencombat/talismans/<id>.json`

```json
{"name": "战士壶碎片", "maxFp": 30, "staminaRegen": 0.20, "damage": 0.08,
 "damageReduction": 0.12, "heal": 2.0}
```
全部字段可省（省 = 0）。`damageReduction` 允许**负数**（= 更疼，见"烙印"）；
倍率会被夹在合理区间（伤害 ∈ [0.1,∞)，受伤 ∈ [0.1,2.0]）。**文件名必须等于物品注册名**。

### 3.4 药物：`data/eldencombat/consumables/<id>.json`

```json
{"name": "火焰赐予我力量", "heal": 0,
 "effects": [{"effect": "minecraft:strength", "amplifier": 1, "seconds": 60}]}
```
`amplifier` 是 0 起（1 = II 级）；`seconds` 可换 `ticks`；未知效果只记日志并跳过，不影响其它效果。

### 3.5 战技数值：`data/eldencombat/dsh_skills/remembrance_parameters.json`

8 个战技的 FP、蓄势/生效/收招 tick、追击窗口、段数、伤害、削韧、特效半径等（聚合在一个文件里）。
**刻意不放在 `data/<ns>/skill_parameters/`**：那是 EF 的目录约定，EF 已移除，但现在也不必搬回去。

---

## 4. 我验证了什么 / 没验证什么

### 已验证（本机、可复现）

```
$env:JAVA_HOME='D:\JDK21'; cd D:\codexout\mcmod
.\gradlew.bat build --offline        # BUILD SUCCESSFUL
```
- **67 个单测 0 失败**（`build/test-results/test/*.xml`）：含本轮新增
  `SkillPoseDataTest`（5：度→弧度、线性插值、端点夹取、`ACTIVE#n` 覆盖与回退、非原版关节被丢弃）、
  `TalismanConsumableDataTest`（5：倍率换算与夹取、有代价护符、缺字段、效果 sanitize），
  以及改写后的 `RemainingSkillsTest` / `RemembranceSkillsTest`（姿态关键帧末帧 = 阶段时长、只用原版关节、8 个战技齐全）。
- 无 EF 代码引用；jar 内包含 8 个 `skill_poses/*.json`、4 个护符与 3 个药物的数据/模型/贴图。
- 校验过 mixin 目标描述符：`PlayerModel` 里 `setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V`
  与桥方法 `(Lnet/minecraft/world/entity/Entity;FFFFF)V` 同时存在，mixin 选的是前者（用 `javap` 核对过）。

### 没验证（**这是你的活**）

1. **实机画面**：动作好不好看、幅度够不够、有没有穿插/抽搐（我只能保证逻辑与数据契约）。
2. **mixin 是否真的注入**：只有启动客户端才会知道。**如果启动时日志出现 mixin 应用失败**，
   最省事的兜底是把 `resources/eldencombat.mixins.json` 里 `PlayerModelPoseMixin` 的
   `@Inject` 改成 `require = 0` 并同步告诉我（那样动作会静默不生效，但不会崩）。
3. **手持武器的抓握朝向**：目前零校正是"看起来可能不对"的默认状态（见 §3.2）。
4. 护符/药物的**图标是占位图**：我用脚本生成了 16×16 的几何色块
   （`textures/item/<id>.png` + `models/item/<id>.json`，`parent=item/generated`），
   目的是"不要出现紫黑缺模型方块"。**请用正式美术替换这 7 张 PNG**（文件名别改，改了要同步 `models/item/*.json`）。
   > 注意：美术资产的主流程是 `build.gradle` 的 `syncArt`（从 `art/margit`、`art/player`、`art/weapons`、`art/remembrance-game` 同步）。
   > 新增物品图标要么加一条 `syncArt` 映射，要么就继续放在 `src/main/resources`（当前做法），你定。

---

## 5. 交给 Codex 的具体清单

### P0（不做就无法验收）

1. **实机跑一遍**：`.\gradlew.bat runClient -PmanualSkills --offline`（无限 FP、不会自动退出战斗模式；
   它会用 `--quickPlaySingleplayer codex-skill-smoke` 直接进测试世界，游戏目录 `build/manualSkills/`），
   手里拿 8 把追忆武器，逐个按 `C` 看动作；确认：
   - 按 `C` 有动作（不是原地不动）；
   - 动作**只动原版关节**（不出现多余肢体/关节）；
   - 普攻仍然是**原版挥剑**（不是自定义动作）；
   - `F3+L`（或日志）能看到 `[skills] 战技姿态就绪：8 个`。
2. **确认 mixin 生效**：看 `build/manualSkills/logs/latest.log`，若出现 `PlayerModelPoseMixin` 相关错误/警告，
   按 §4.2 兜底并回传日志。

### P1（手感/美术）

3. **姿态角度调参**：只改 `skill_poses/*.json`（§3.1），改完 `F3+T` 生效；重点看水鸟乱舞（36 tick 三段）、
   唤星（追击段）是否"连贯而不抽搐"（相邻阶段的**首帧要接上一阶段的末帧**，我尽量对齐了，但值得复核）。
4. **武器握持校正**：只改 `item_skins/*.json`（§3.2）。
5. **7 张占位图标**替换为正式美术（§4.4）。

### P2（可选增强，我没做，避免和你的美术工作抢文件）

6. 护符/药物的专用贴图图集化（现在是一物一图）。
7. 战技特效几何（`effects/SkillEffectMesh`、`entity/skill/*`）——一直是你的范围，我没动。
8. 连枷/软鞭的机械动画（`WeaponItemRenderer` 时代就未实现，现在由 `EquipmentRenderer` 静态绘制）。

---

## 6. 已知设计缺口（诚实记录，不是 bug 但你会撞上）

1. **每段的收招时长不可分别配置**：`SkillDriver` 的 RECOVERY 对所有段都用 `spec.recoveryTicks()`。
   如果想让"最后一段"收招更长，需要在 `RemembranceSkillSpec` 上加 `recoveryForStage(int)`。
2. **`bloodboon_ritual` 的第 2/3 段共用同一份姿态**（`ACTIVE#2` 被复用；`ACTIVE#3` 按回退链取 `ACTIVE`）。
   想要"三段各不相同"就往 `skill_poses/bloodboon_ritual.json` 里加 `"ACTIVE#3"`。
3. **`gold_breaker` 的落地判定要求 `player.onGround()`**：起跳后在生效窗口内没落地就不会产生冲击判定，没有兜底。
4. **护符的 FP 上限加成不落盘**：登录/重生时会按附件里的护符重新应用（这是刻意的，防止"摘了护符还留上限"），
   但副作用是"戴着 +30 上限的护符重登后，当前 FP 会先被夹到 100 再加到 130"，即 **FP 不会凭空补满**。
5. **战技期间输入冻结是"全有全无"**：施放中除了"追击"和"锁定"，其余动作输入一律忽略（包括翻滚取消）。
   这与"战技不可取消"的魂系手感一致，但如果你想要"后摇可翻滚取消"，需要单独设计。
6. `HitResolver` 里还留着 `eldencombat.epicSmoke` 这个系统属性开关与 `EPIC_SMOKE_*` 日志字样（EF 时代遗留），
   纯属命名陈旧、无功能影响；`build.gradle` 里已没有设置它的地方。要清理就一起清理。
7. `build.gradle` 的 `client`/`server` 运行块里还留着 `clientSmoke` / `skillSmoke` / `serverSmoke` / `weaponServerSmoke`
   四个属性开关，但它们对应的 harness 类（`EpicFightSmokeHarness` / `WeaponSmokeHarness` / `WeaponServerSmokeHarness` /
   `RemembranceSmokeHarness`）已在去 EF 时删除 → **这四个开关现在是空转的**，设了也不会跑任何检查。
   `ClientSmokeHarness`/`ServerSmokeHarness`/`SkillSmokeHarness` 仍在（`clientSmoke`/`skillSmoke` 里那两个仍然有效）。

---

## 7. 快速命令备忘

```powershell
# 离线构建 + 单测
$env:JAVA_HOME='D:\JDK21'; cd D:\codexout\mcmod; .\gradlew.bat build --offline

# 实机手动验收（无限 FP；游戏目录 build/manualSkills/，日志 build/manualSkills/logs/latest.log）
.\gradlew.bat runClient -PmanualSkills --offline

# 只跑单测
.\gradlew.bat test --offline

# 单测结果汇总
Get-ChildItem build\test-results\test\*.xml | ForEach-Object { [xml](Get-Content $_ -Encoding UTF8) } | ForEach-Object { $_.testsuite } | Select-Object name,tests,failures,errors

# 命令（需要权限等级 2）
/eldencombat fp get | fp set <值> | fp max
/eldencombat fp skill status            # 主手武器绑定的战技、施放阶段、FP
/eldencombat fp skill specs             # 8 个战技的全部参数
/eldencombat fp skill cast <id>         # 按 id 强制施放（跳过"主手必须是绑定武器"）
/eldencombat status                     # 引擎=纯原版、战斗模式、精力、当前战技
/eldencombat testkit                    # 发一套测试装备（剑/大剑/盾/药瓶）
/eldencombat summon margit | reset | reload | selftest
```
