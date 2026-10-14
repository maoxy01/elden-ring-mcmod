# dsh 交付：八件追忆武器专属战技实施报告

> 2026-10-11 更正：本报告保留 dsh 的历史交付记录。后续七个战技及实际绑定修复见 [Codex 当前报告](CODEX-REMAINING-SKILLS-IMPLEMENTATION-REPORT.md)。本报告所述「物品 JSON 添加 innate_skills」不适用于当前 EF：物品解析器忽略该字段，现已改成专属 preset + moveset 绑定。伤害与手感按用户要求由用户手动验收。

对应计划：[DSH-REMEMBRANCE-SKILLS-PLAN.md](DSH-REMEMBRANCE-SKILLS-PLAN.md)
基线（实测）：Minecraft 1.21.1 · NeoForge 21.1.219 · Epic Fight 21.17.3.1（`curse.maven:epic-fight-mod-405076:8175609`）· Java 21（`D:/JDK21`）
当前产物：`build/libs/eldencombat-0.2.0.jar`（2,391,128 字节，2026-10-11 19:13:36）· 离线构建 `./gradlew.bat build --offline` 通过 · 22 项单测全绿

> 本文件是**进度真源**：状态取值 `未开始` / `进行中` / `已实现未实机` / `已实机验证` / `阻塞`。
> 未实机验证过的内容一律不写成"已完成"。
>
> **验证分工**：按用户 2026-10-11 指示，游戏内验证由 Codex 执行。本报告第 3、8 节给出
> 已接线的验收入口、命令与预期日志行；dsh 不宣称这些场景已通过。

## 0. 基础设施状态

| 项 | 状态 | 位置 / 证据 |
| --- | --- | --- |
| 独立 FP 附件（服务端权威、可序列化、死亡补满、换维度不补、重登不刷） | 已实现未实机 | `server/skills/PlayerFpState.java`、`FpStateCodec.java`、`ModAttachments.PLAYER_FP`（`serialize(...).copyOnDeath()`）、`SkillServerEvents` |
| 施放状态与施放 ID（阶段、开始 tick、追击窗、去重、超时） | 已实现未实机 | `server/skills/SkillCastState.java`（`PLAYER_SKILL_CAST`）、`SkillDriver.java` |
| 原子扣费（FP 不足、非法武器、重复请求、过期追击都不扣费） | 已实现未实机 | `PlayerFpState.trySpend` + `WaveOfGoldSkill.executeOnServer` 的校验顺序 |
| 统一 cancel/finish 清理 | 已实现未实机 | `SkillDriver.cancel`；接线见下表 |
| 技能参数数据文件（FP/时长/上限/伤害，独立配置） | 已实现未实机 | `data/eldencombat/dsh_skills/remembrance_parameters.json` + `RemembranceSkillSpec` |
| 客户端 FP 镜像 | 已实现未实机 | `network/FpSyncPayload.java`、`client/ClientSkills.java` |
| HUD 画 FP 条 | 未开始 | 数据已就绪，`CombatHud` 尚未画 |
| 权限隔离的技能测试入口（命令） | 已实现未实机 | `/eldencombat fp get\|set\|max`、`/eldencombat skill status\|cast\|anim\|specs`，`command/SkillTestCommand.java` |
| 游戏内专属断言场景 | **已接线，未执行** | `testing/RemembranceSmokeHarness.java`（`-PskillSmoke`，不进发布 JAR） |

### 0.1 中断/清理的真实接线（修正上一版的过度声明）

上一版本报告把"切武器、受击中断"写进了"已实现"，实际当时 `SkillDriver.cancel` **没有任何调用点**。
现已逐条落实，下表是**当前真实状态**：

| 终止路径 | 是否接线 | 实现位置 |
| --- | --- | --- |
| 阶段超时 | 是 | `SkillDriver.tick` → `cancel("阶段超时")` |
| 死亡（克隆时 `isWasDeath`） | 是 | `SkillServerEvents.onPlayerClone` → `refillOnRespawn` + `finish()` |
| 换维度（非死亡克隆） | 是 | 同上，只 `finish()` + 同步，不补 FP |
| 离线 | 是 | `SkillServerEvents.onPlayerLoggedOut` → `finish()` |
| 切武器 / 武器专属技能变化 | 是（本轮新接） | `SkillInterrupts` 监听 EF `Player.CHANGE_INNATE_SKILL` |
| 受击中断 | 是（本轮新接） | `SkillInterrupts` 监听 EF `Entity.TAKE_DAMAGE_POST`（只处理施放者本人、有效伤害 > 0） |

> `finish()` 与 `cancel()` 的区别：`cancel` 会先回调 `Runtime.onCancel` 再 `finish`，
> 用于撤销本技能施加的临时状态。目前 `wave_of_gold` 没有临时状态需要撤销（不改变重力、不锁移动），
> 因此死亡/离线/换维度直接用 `finish()` 是等价的；后续有位移类技能时应统一走 `cancel`。

## 1. 八个战技逐项状态

| 战技 | 物品 | FP | 时长（tick） | 跃起 | 状态 | 本轮实际做到 |
| --- | --- | --- | --- | --- | --- | --- |
| 黄金波动 wave_of_gold | sacred_relic_sword | 20 | 10 + 4 + 10 | 否 | **已实现未实机** | 技能注册 + 武器绑定 + FP 校验扣费 + 阶段驱动 + 地面波命中体（传播/扩展/不穿墙/每目标一次/寿命 30 tick）+ 粒子 + **专属动画已交付**；游戏内断言场景已接线待执行 |
| 黄金碎击 gold_breaker | marikas_hammer | 22 | 8 + 16 + 10 | 是 | 未开始 | — |
| 命定之死 destined_death | malikeths_black_blade | 25 | 8 + 24 + 10 | 是 | 未开始 | — |
| 掠夺火焰 takers_flames | blasphemous_blade | 20 | 9 + 4 + 10 | 否 | 未开始 | — |
| 水鸟乱舞 waterfowl_dance | hand_of_malenia | 28 | 10 + 36 + 10 | 是 | 未开始 | — |
| 唤星 starcaller_cry | starscourge_greatsword | 15 + 15 | 8 + 6；追击 24 | 是（追击段） | 未开始 | — |
| 授血仪式 bloodboon_ritual | mohgwyns_sacred_spear | 每段 15 | 每段 6 + 4；段间窗 10 | 否 | 未开始 | — |
| 咒血斩击 cursed_blood_slice | morgotts_cursed_sword | 12 + 8 | 8 + 6；追击 18 | 是（专用跃进） | 未开始 | — |

参数（含伤害/削韧原型值）全部在 `data/eldencombat/dsh_skills/remembrance_parameters.json`，改数据不需重编译。

## 2. 黄金波动的专属动画（本轮交付）

产物：`src/main/resources/assets/eldencombat/animmodels/animations/wave_of_gold.json`

- **格式**：EF 的 `attributes` 格式（`{"loc":[x,y,z],"rot":[w,x,y,z],"sca":[x,y,z]}`），
  4 个关键帧段共 7 个时点（0 / 0.25 / 0.5 / 0.6 / 0.7 / 0.9 / 1.2 s），16 个关节。
- **时长与技能时间线对齐**：24 tick @20 TPS = 1.2 s，正好等于 `windup 10 + active 4 + recovery 10`。
  这条对齐有单测守着（`deliveredWaveAnimationMatchesSkillTimeline`），改参数忘了改动画会直接红。
- **动作**：起手 → 右后高位蓄力（0.5s）→ 横扫过正前（0.6s）→ 收在左前方（0.7s）→ 回位（1.2s），刀身全程约水平。
- **Root 不做平移**：本动画用 `StaticAnimation` 播放，没有 `COORD`/`ActionAnimationProperty`，
  根位移会让模型与实际实体位置脱节，所以位移只由腿部姿态表达。
- **来源诚实声明**：这是 dsh 用自写工具从**自定姿态规格**编译出来的原创动作，
  **不是美术定制动作，也不是从 Epic Fight 拷来的动画数据**。EF 只被用作 API/骨架/格式的来源。
  美术级打磨仍应交给 Codex；需要校准的项见第 7 节。

### 2.1 支撑工具（可以复用给后面七个技能）

| 工具 | 作用 | 路径 |
| --- | --- | --- |
| 姿态生成器 | 读世界轴姿态规格 → 编译成 EF `attributes` 动画 JSON；含"共轭转换自检"，写错转置会直接抛错 | `tools/dsh-anim/posegen.mjs` |
| 离线 FK 预览 | 用 EF biped 骨架做正向运动学，渲染火柴人 PNG，**不开游戏就能看动作对不对** | `tools/dsh-anim/fkcheck.mjs` |
| 骨架信息 | 打印 biped 关节树与绑定变换 | `tools/dsh-anim/biped-info.mjs` |
| 姿态规格 | 每个技能一份，可读可改可 diff | `tools/dsh-anim/specs/wave_of_gold.json` |

用法与已验证的关键约定（都从本机 EF 反编译源码 + 实机渲染对拍得出）：

```powershell
# 生成 + 预览（需要先准备参考文件，见下）
node tools/dsh-anim/posegen.mjs tools/dsh-anim/specs/wave_of_gold.json build/ef-ref/wave_of_gold.json
node tools/dsh-anim/fkcheck.mjs build/ef-ref/animmodels_entity_biped.json build/ef-ref/wave_of_gold.json build/ef-ref/wave_of_gold.png --cols 7
```

- EF 骨架/动画的 16 元素矩阵是"列主序装载 + `transpose()`"，等价于**直接按行主序读、平移在下标 3/7/11**；
- 根关节要额外左乘 `BLENDER_TO_MINECRAFT_COORD`（绕 X 轴 −90°，Blender Z-up → MC Y-up）；
- `attributes` 格式的 `rot` 是 `[w, x, y, z]`，装载时轴分量取负（`JsonAssetLoader.java:520-527`），
  所以恒等四元数是 `[1,0,0,0]` 而不是注释里的 `[0,0,0,1]`；
- 模型空间**前 = −Z、右 = +X、上 = +Y**（由 `MoveCoordFunctions.java:268` 的 Z 取反与 `:457` 的 `180-yRot` 反推；
  `Tool_R` 在 +X 与"右手"一致，交叉验证成立）；
- 欧拉顺序用 `R = Rx·Ry·Rz`：手臂绑定姿态沿 −Y 垂下，必须先绕 Z 外展抬离竖直轴，
  再绕 Y 横扫才产生水平位移（顺序写反会让横扫变成绕自身轴空转）。

## 3. 测试记录

| 类别 | 命令 | 结果 |
| --- | --- | --- |
| 离线构建 | `./gradlew.bat build --offline` | **通过**，jar 0.2.0（2,391,128 字节，19:13:36） |
| 纯逻辑测试 | `./gradlew.bat test --offline` | **22 项全绿**：`RemembranceSkillsTest` 16（新增 setFp 夹取、动画资源格式校验、动画时长与技能时间线一致性）、`BossHitGeometryTest` 1、`SelfTestTest` 1、`WeaponMeshDataTest` 4 |
| jar 内容检查 | 解包核对 | 含 `assets/eldencombat/animmodels/animations/wave_of_gold.json`、`data/eldencombat/dsh_skills/remembrance_parameters.json`、全部战技类；`RemembranceSmokeHarness` 已被排除 |
| 客户端 smoke（实机） | `./gradlew.bat runClient -PskillSmoke --offline` | **跑过两次，抓到 3 个真 bug（已修），专属断言场景本身尚未跑完**，详见 3.1 |

### 3.1 实机启动阶段抓到的三个真 bug（编译与单测都测不出来）

| # | 症状 | 根因 | 修复 |
| --- | --- | --- | --- |
| 1 | 模组**完全加载不了**：`class com.dsh.eldencombat.client.ClientSkills has no @SubscribeEvent methods, but register was called anyway` → `Mod loading has failed` | 我给 `ClientSkills` 加了 `@EventBusSubscriber`，但它只有 payload 处理器、没有 `@SubscribeEvent` 方法；NeoForge 的自动订阅者注册对这种类是**抛异常**而不是忽略 | 去掉该注解并写明原因；顺带审计了全部 15 个订阅者类，只有它有问题 |
| 2 | 登录即踢线：`Payload eldencombat:fp_sync may not be sent to the client!` → 客户端收到 `Invalid player data` 掉线 | 客户端方向的包被我用 `if (dist == CLIENT)` 守卫**只在客户端注册**。NeoForge 的 `NetworkRegistry.checkPacket` 查的是连接协商出的通道表，发送方（集成服务器）不认这个通道就会抛异常，而异常发生在 `PlayerLoggedInEvent` 里导致登录流程中断 | 客户端方向的三个包改为**两端都注册**；处理器移到公共跳板 `network/ClientPayloadTrampoline.java`，运行期再判 dist，靠 JVM 惰性符号解析保证专服不加载 `client.*` |
| 3 | 每次启动刷 `Skill eldencombat:remembrance_skills doesn't exist in the registry.` | EF 自己的 `SkillReloadListener` 以 `SimpleJsonResourceReloadListener(GSON, "skill_parameters")` 扫描**所有命名空间**的该目录，把每个文件名当技能 id。我的"一个聚合文件管八个战技"与 EF"一技能一文件"的约定冲突 | 参数文件移到私有目录 `data/eldencombat/dsh_skills/remembrance_parameters.json`；EF 的 `skill_parameters/parrying.json` 保持原样不动 |

**诚实的边界**：这三处修复目前**只经过编译 + 单测**，没有再跑一次实机确认；
按用户指示验证交给 Codex，命令见第 8 节。第 1 个 bug 属于"整个模组加载失败"级别，
建议 Codex 优先跑一次冒烟确认。

## 4. 已核实的关键接口（来自本机 EF 反编译源码，供后续七个技能复用）

| 用途 | 结论 | 来源 |
| --- | --- | --- |
| 注册技能 | `DeferredRegister.create(EpicFightRegistries.Keys.SKILL, MODID)` | `registry/EpicFightRegistries.java:34,84` |
| 构造技能 | `SkillBuilder.setRegistryName/setCategory/setResource/setActivateType` + `build(ResourceLocation)` | `skill/SkillBuilder.java:14-54` |
| 技能生命周期 | `isExecutableState`、`canExecute`、`checkExecuteCondition`、`executeOnServer`、`cancelOnServer`、`onInitiate` | `skill/Skill.java:147/154/161/182/203/240` |
| 资源枚举 | `Skill.Resource = {NONE, WEAPON_CHARGE, COOLDOWN, STAMINA, HEALTH}`，**不可扩展**，因此全局 FP 必须自建 | `skill/Skill.java:559-611` |
| 施放入口 | 客户端 `CPSkillRequest(slot, WorkType.CAST, args)` → 服务端 `SkillContainer.requestCasting` → `canUse`（`canExecute` + `isExecutableState` + `CAST_SKILL` 钩子 + `resourcePredicate`）→ `executeOnServer` | `EpicFightServerBoundPayloadHandler.java:97-107`、`skill/SkillContainer.java` |
| `Resource.NONE` 不拦施放 | `NONE` 的 predicate 恒为 true，因此 FP 校验必须自己写在 `executeOnServer` 里 | `skill/Skill.java:559+` |
| 武器绑定专属技能 | 每件武器的 capability JSON 写 `"innate_skills": {"<style>": "<skill id>"}`；EF 在换手时调 `CapabilityItem.changeWeaponInnateSkill` 写入 `WEAPON_INNATE` 槽 | `world/capabilities/item/WeaponTypeReloadListener.java:179-186`、`CapabilityItem.java:349-367` |
| 注册自定义玩家动画 | mod bus 的 `AnimationManager.AnimationRegistryEvent` → `event.newBuilder(ns, b -> b.nextAccessor(id, accessor -> new StaticAnimation(false, accessor, Armatures.BIPED)))`。**同一 namespace 只能 `newBuilder` 一次**（重复会抛异常） | `api/animation/AnimationManager.java:479-506` |
| 动画资源路径 / 格式 | `assets/<ns>/animmodels/animations/<id>.json`；`format` 缺省为 `matrix`，可写 `attributes` | `api/animation/types/StaticAnimation.java:100`、`api/asset/JsonAssetLoader.java:558,709-786` |
| 播放动画 | `LivingEntityPatch#playAnimationSynchronized(AssetAccessor<? extends StaticAnimation>, float)` | `world/capabilities/entitypatch/LivingEntityPatch.java:665` |
| 中断钩子 | `Player.CHANGE_INNATE_SKILL`、`Entity.TAKE_DAMAGE_POST`（`getDamage()` 是修正后伤害） | `api/event/EpicFightEventHooks.java:31-54`、`api/event/types/entity/TakeDamageEvent.java` |
| 网络协商 | 客户端方向的包必须**两端都注册**，否则发送时抛 `may not be sent to the client`；处理器若引用客户端类，需用公共跳板延迟解析 | `net/neoforged/neoforge/network/registration/NetworkRegistry.java:446`（`hasChannel`）、实机日志 |
| 本版**没有**的 API | `Motion`/`MotionBuilder`、`EntityPatch#setGravity`、`AABBCollider`、`PlayerPatch#setModelZRot` —— 位移用 `ActionAnimationProperty`（`NO_GRAVITY_TIME`/`MOVE_VERTICAL`/`COORD`）+ 原版 `setDeltaMovement` | `api/animation/property/AnimationProperty.java:179-280` |
| `skill_parameters/` 是 EF 的目录 | EF 会扫描**所有命名空间**的 `data/<ns>/skill_parameters/*.json` 并当作 `<技能id>.json` 解析 | `api/data/reloader/SkillReloadListener.java:121,128-135` |

## 5. 未实现 / 明确标注

- **另外七个战技**：未开始（按计划实施顺序：黄金碎击 → 掠夺火焰/咒血斩击 → 授血仪式/命定之死 → 水鸟乱舞/唤星）。
- **游戏内专属断言场景**：`RemembranceSmokeHarness` 已写好并接线，但**未执行通过**，不宣称已验收。
- **HUD FP 条**：未开始（`ClientSkills` 已提供 `fp()/maxFp()/ratio()/casting()`，`CombatHud` 直接可画）。
- **多人/专服**：未做。专服的客户端类隔离是靠 `ClientPayloadTrampoline` 的惰性解析保证的，**需要专服实跑确认**。
- **命定之死的有期限效果、出血/韧性**：本轮未实现，不用视觉冒充。
- **22 个类型战技、战灰替换界面、法术系统**：不在本轮范围。
- **Boss（玛尔基特）与普通空中攻击策略**：本轮未改动。
- **碎片命中/受击中断**：`SkillInterrupts` 已在 `EpicFightIntegration.registerHooks()` 里注册，但注入了未实机验证的 EF 事件名，需实机确认钩子真的会触发。

## 6. 修改文件清单（本轮）

新增（dsh 战技相关）：

- `src/main/java/com/dsh/eldencombat/server/skills/`：`PlayerFpState.java`、`FpStateCodec.java`、`SkillCastState.java`、`RemembranceSkillSpec.java`、`SkillDriver.java`、`SkillServerEvents.java`、`WaveStep.java`、`SkillInterrupts.java`
- `src/main/java/com/dsh/eldencombat/network/FpSyncPayload.java`、`ClientPayloadTrampoline.java`
- `src/main/java/com/dsh/eldencombat/client/ClientSkills.java`
- `src/main/java/com/dsh/eldencombat/entity/skill/GoldenWaveEntity.java`
- `src/main/java/com/dsh/eldencombat/compat/animations/RemembranceAnimations.java`
- `src/main/java/com/dsh/eldencombat/compat/skills/RemembranceSkills.java`、`WaveOfGoldSkill.java`
- `src/main/java/com/dsh/eldencombat/command/SkillTestCommand.java`
- `src/main/java/com/dsh/eldencombat/testing/RemembranceSmokeHarness.java`（不进发布 JAR）
- `src/main/resources/data/eldencombat/dsh_skills/remembrance_parameters.json`
- `src/main/resources/assets/eldencombat/animmodels/animations/wave_of_gold.json` ← 黄金波动专属动画
- `src/test/java/com/dsh/eldencombat/RemembranceSkillsTest.java`
- `tools/dsh-anim/`（`posegen.mjs`、`fkcheck.mjs`、`biped-info.mjs`、`specs/wave_of_gold.json`）

修改（加法改动，未覆盖他人未提交内容）：

- `EldenCombat.java`：注册 `RemembranceSkills.SKILLS`、`registerRuntimes()`、动画注册监听
- `registry/ModAttachments.java`：新增 `PLAYER_FP`（序列化 + `copyOnDeath`）与 `PLAYER_SKILL_CAST`
- `registry/ModEntities.java`：新增 `golden_wave`
- `registry/ModPayloads.java`：客户端方向三个包改为两端注册 + 走公共跳板（**修 bug 2**）
- `client/ClientSkills.java`：去掉错误的 `@EventBusSubscriber`（**修 bug 1**）
- `compat/EpicFightIntegration.java`：`registerHooks()` 里加一行 `SkillInterrupts.register()`
- `command/EldenCombatCommand.java`：把 `SkillTestCommand.node()` 挂到 `/eldencombat` 下
- `build.gradle`：新增 `-PskillSmoke` 客户端入口 + 把 `RemembranceSmokeHarness*` 排除出发布 JAR
- `data/eldencombat/capabilities/weapons/sacred_relic_sword.json`：加 `innate_skills.common = eldencombat:wave_of_gold`
- 参数文件从 `data/eldencombat/skill_parameters/remembrance_skills.json` 迁移到 `data/eldencombat/dsh_skills/remembrance_parameters.json`（**修 bug 3**）

## 7. 模型校准需求（提交给 Codex，不回改美术源文件）

待实机截图后填写：

1. 施放动画的手部/武器插槽对齐：现在握持姿态由 `Shoulder_R/Arm_R/Hand_R` 三个关节决定，
   `Tool_R` 只承接武器，未做武器自身的局部偏移；若武器刃面朝向不对，需要的是握持轴参数而不是重做动作。
2. 黄金波的粒子与地面贴合：粒子由服务端时点驱动，需确认在台阶/坡面上的视觉表现。
3. 黄金波动的动作属于"可用的工程动作"，若要美术级打磨请以它为基础替换
   `wave_of_gold.json`（time/transform 数量必须与 `time` 一一对应，且总时长保持 1.2 s 以免与命中时点脱节）。

## 8. 交给 Codex 的验证清单（命令与预期）

```powershell
$env:JAVA_HOME='D:/JDK21'
cd D:/codexout/mcmod

# 1) 构建 + 纯逻辑测试（应全绿，22 项）
./gradlew.bat build --offline

# 2) 游戏内专属战技断言（dsh 已接线、未执行）
./gradlew.bat runClient -PskillSmoke --offline
```

预期在日志里出现（每项都是 `SKILL_SMOKE_PASS:` 前缀，失败是 `SKILL_SMOKE_FAIL:`）：

| 断言 | 期望 |
| --- | --- |
| 技能注册 | `技能已注册: eldencombat:wave_of_gold` |
| 专属动画 | `黄金波动专属动画已加载（不是占位动作）` —— 若出现`占位动作`说明 JSON 没被加载 |
| 武器绑定 | `WEAPON_INNATE 槽绑定到 wave_of_gold` |
| 扣费 | `施放后 FP 恰好扣 20.0` |
| 真实伤害 | `黄金地面波真实造成伤害` |
| 收招 | `收招结束后施放状态回到 IDLE` |
| 拒发 | `FP 不足时拒发且不扣费` |
| 去重 | `重复请求只扣一次 FP` |
| 中断 | `切武器后施放被中断并清理`、`受击后施放被中断并清理` |

同时应**不再出现**下面三行（它们是本轮已修的 bug，回归即说明没修干净）：

- `has no @SubscribeEvent methods, but register was called anyway`
- `Payload eldencombat:fp_sync may not be sent to the client!`
- `Skill eldencombat:remembrance_skills doesn't exist in the registry.`

场景会截三张图到 `build/skillSmoke/`：`skill-wave-01-windup.png`、`skill-wave-02-hit.png`、`skill-wave-03-recovery.png`。

其他可用入口：

- `/eldencombat fp get|set <值>|max`（需要权限等级 2，权隔离在命令与状态两层都做了检查）
- `/eldencombat skill status|cast wave_of_gold|anim wave_of_gold|specs`
- 专服：`./gradlew.bat runServer -PweaponServerSmoke --offline`（验专服不加载客户端类；本轮的 payload 改动需要这个）

## 9. 下一步（按计划实施顺序）

1. **黄金碎击**：技能跃起（`NO_GRAVITY_TIME` + 服务端约束轨迹/落点）+ 冲击一次结算 + 中断恢复。
   模板已齐：`SkillDriver.Runtime` + `SkillInterrupts` + `posegen/fkcheck` 可直接复用。
2. **掠夺火焰、咒血斩击**：复用地面波 + 延迟命中 + 吸血上限（`GoldenWaveEntity` 已带 `healCap`）。
3. **授血仪式、命定之死**：多段扣费（`costForStage`）、伤害间隔、状态刷新与清理（`maxStages`/`stageBudget` 已就绪）。
4. **水鸟乱舞、唤星**：多段空中 + 虚拟双剑（`Moveset` 版绑定）。
5. **HUD FP 条**（`CombatHud` + `ClientSkills.ratio()`）。
