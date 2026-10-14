# Epic Fight 武器接入方案（审查修订版）

日期：2026-10-11。目标：MC 1.21.1、NeoForge 21.1.219、Epic Fight 21.17.3.1、Java 21。
用户已授权实际修改模组代码与构建配置。Boss 暂停；不做处决；允许指定战技跃起，不自动启用普通跳跃攻击。

## 审查结论

原稿保存在 `docs/review-backups/20261011-weapon-integration/`。以下问题不能沿用：

| 优先级 | 原稿问题 | 修正 |
| --- | --- | --- |
| P1 | 用 `PATCHED_ITEM.post(callback)` 注册 | `registerEvent(callback, owner)` 才是订阅；`post(event)` 是分发。客户端 setup 只订阅一次。 |
| P1 | 只复用方块渲染器，忽略游戏版 mesh | 8 件追忆优先读减面预烘焙网格；类型武器或坏网格从 cubes 重载时烘焙一次。 |
| P1 | 三角索引可直接交给原版物品缓冲 | 原版实体与附魔缓冲使用 QUADS；三角形需适配为 a,b,c,c，不可跨三角形错误拼面。 |
| P1 | 游戏资产文档要求所有渲染器都禁止翻 V | 文件 V 是下原点；Minecraft VertexConsumer 使用上原点，适配器加载时做一次 `1-v`。Three.js 路径保持原样。 |
| P1 | 把每个 SkillContainer 的能量当全局 FP | 容器随换技重置，不是共享 FP；新增玩家服务端 attachment，精力仍归 EF。 |
| P1 | 用另一套 C2S/判伤实现全部战技 | 沿用 EF 技能请求入口；附加消息仅用于必要追击参数，不能形成第二条判伤链。 |
| P1 | 所有战技必须站地面 | 每个战技独立移动许可；黄金碎击、命定之死、水鸟乱舞等允许技能跃起。 |
| P2 | 方法没有类级注解就认定死代码 | 手动 EVENT_BUS 注册及直接调用同样有效；逐条查调用链，不在本轮删除旧引擎。 |
| P2 | correction matrix 当绝对世界矩阵 | 它是 EF 局部手部变换；复制后乘自有校准，通过 MathUtils.mulStack 应用一次。 |
| P2 | item/handheld + BEWLR 注册就能触发 | baked model 必须 isCustomRenderer；当前使用 builtin/entity 父模型。 |
| P2 | 动画 JSON 缺失等同注册表不一致 | 资源加载错误与两端注册名不一致是不同故障；自定义动画需正确资源并保证两端注册一致。 |
| P2 | innateSkill 不能读物品实例 | WeaponCapability 的 factory 是 Function<ItemStack, Skill>；Moveset 版本还提供 player patch。 |
| P2 | 改 assetLayoutVersion 才能触发同步 | Gradle Sync 跟踪文件输入；布局版本只作额外输入，不能代替正确的来源与目标映射。 |

本地核实入口：`build/epicfight-sources/yesman/epicfight/` 的 EventHook、
RenderEngine、RenderItemBase、WeaponCapability、SkillContainer、AnimationManager，
以及 `build/moddev/artifacts/neoforge-21.1.219-sources.jar` 的 RenderType。

## 当前接入范围

### 已实现的基础接入

- 保留其他 agent 已注册的 8 件追忆 + 33 件类型物品，不重复注册 ID。
- 构建从 `art/remembrance-game/` 同步 8 份 model、8 份 mesh、2048 图集和 catalog；不带高细节编辑源、Blockbench、预览、qa、备份。
- 33 类型模型和 512 图集仍同步到独立目录，药瓶继续使用旧 equipment 图集。
- WeaponAssets 使用 Minecraft ResourceManager；资源准备线程解析与烘焙，apply 原子切换不可变缓存。
- WeaponMeshData 校验格式、数量、有限值、UV、单位法线、整数索引及绕序；单位不重复除以 16。
- WeaponMesh 使用 QUADS 兼容缓存，EF 手持、原版 BEWLR、附魔均使用同一份已烘焙数据。
- EF renderer 在客户端 setup 订阅，item_skins 按物品绑定；支持当前配置的数字、单元素及三元素 scale。
- 补齐全部 41 件武器能力数据；先复用 EF 成熟基础招式，类型近似明确标记，不冒充专属战技。
- 攻速加速钩子覆盖全部本模组武器；取消对 METEOR_SLAM / DEMOLITION_LEAP 的旧跳技禁令，保留处决伤害禁止。
- 增加独立武器客户端/服务端验收入口，发布 JAR 排除测试入口。

### 仍未完成

以下不能写成已经接入的功能：8 个法环专属战技、22 个类型战技、共享 FP、
战灰组件与替换界面、自定义玩家动作、连枷/软鞭次级摆动和专用 collider、
法器施法、碎星单物品自动双剑、完整 LOD、两名玩家联机及锁定整合包全组合验收。

## 资源与渲染契约

| 来源 | JAR 路径 |
| --- | --- |
| remembrance-game/*.model.json / *.mesh.json | assets/eldencombat/skeletons/items/remembrance/ |
| remembrance-game/weapons.png | assets/eldencombat/textures/item/remembrance.png |
| remembrance-game/catalog.json | assets/eldencombat/weapons/remembrance.catalog.json |
| weapons/*.model.json | assets/eldencombat/skeletons/items/ |
| weapons/*.animations.json | assets/eldencombat/animations/items/ |
| weapons/weapons.png | assets/eldencombat/textures/item/weapons.png |

详细网格格式以 `GAME-WEAPONS-ASSETS-HANDOFF.md` 为准。
bakedMeshPath 只允许同目录合法文件名；加载失败记录诊断，用当前游戏版 cubes 烘焙兼容缓存。
不读取 art/remembrance 高细节源作为运行回退，不每帧重算方块角点、骨骼或 GUI 包围盒。
F3+T / 资源包切换会重新准备缓存；服务端 /reload 只重载数据包，不等同客户端资源重载。
当前是 CPU 顶点缓存，仍逐帧向 MultiBufferSource 提交，不声称已有持久 GPU VBO 或 LOD。

手持矩阵：已有 PoseStack 上应用 EF 的关节与校正，再乘武器局部校准。
保留 EF Tool_R / Tool_L 的局部坐标语义；追忆武器的刃面朝向在 item_skins 的独立局部校准中调整，不修改玩家骨架。
复制 getCorrectionMatrix 返回值，不能修改 EF 的共享 transformHolder。
模型 +Y 刃轴、握持原点和左右手轴向需要实机复核；secondaryGrip 不是动画 IK。
item_skins 的实例映射优先于 preset 渲染器，所以刀可使用 epicfight:uchigatana 基础能力。

## 基础能力映射

这只是可运行的基础攻击配置，不是完整法环类型复刻。

| 类型 | 基础 EF preset |
| --- | --- |
| 匕首 | dagger |
| 直剑 / 大剑 | 已有 eldencombat:straight_sword / great_sword |
| 特大剑、大曲剑、大斧、大槌、特大武器、镰刀 | greatsword |
| 刺剑、曲剑、双头剑、软鞭 | sword（暂用，后续专用招式） |
| 重刺剑、矛、大矛、戟、辉石杖 | spear（法杖仅近战占位，不含魔法） |
| 斧、槌、连枷 | axe（后两类暂用） |
| 刀 | uchigatana |
| 拳套、钩爪、圣印记 | fist（圣印记仅占位，不含祷告） |
| 三种弓 / 两种弩 | bow / crossbow，保留原版装填弹药 |
| 三种盾 | shield |

| 追忆武器 | 本轮基础能力 | 待做的固定专属技能 |
| --- | --- | --- |
| 神躯化剑 | eldencombat:great_sword | wave_of_gold / 黄金波动 |
| 玛莉卡的石槌 | epicfight:axe | gold_breaker / 黄金碎击 |
| 玛利喀斯的黑剑 | eldencombat:great_sword | destined_death / 命定之死 |
| 亵渎圣剑 | eldencombat:great_sword | takers_flames / 掠夺火焰 |
| 玛莲妮亚的义手刀 | epicfight:uchigatana | waterfowl_dance / 水鸟乱舞 |
| 碎星大剑 | eldencombat:great_sword | starcaller_cry / 唤星 |
| 蒙格温圣矛 | epicfight:spear | bloodboon_ritual / 授血仪式 |
| 蒙葛特咒剑 | eldencombat:great_sword | cursed_blood_slice / 咒血斩击 |

基础能力可能显示 EF 自带 innate skill，但不是上述专属技能。
碎星目前可分别装备两个物品展示，自动副手虚拟模型与其双剑动作留给专用 moveset，
不能通过自动赠送第二件物品实现。

## 战技实施约束

1. 用 EF 注册 Skill / WEAPON_INNATE，与 ItemStack 战灰组件选择技能；追忆 replaceableArt=false。
2. 全局 FP 服务端 attachment 管理，登录/重生/切换技能/维度同步，HUD 只显示服务端值。
   Skill.Resource 是封闭枚举，不扩充；不借用 WEAPON_CHARGE 冒充全局 FP。
3. 服务端验证技能、实际手持实例、FP、冷却、施放阶段与移动许可，然后原子扣费。
   防重复请求，同一施放 ID 不可反复扣费或获益；被拒绝不消耗。
4. 近战优先 EF AttackAnimation.Phase / collider。每段独立命中集合，不再让 ServerCombat 结算同一攻击。
5. 地面波与投射物才单独维护服务端命中体，包含方向、寿命、墙体遮挡、同目标去重。
   掠夺火焰仅确认伤害后吸血；授血仪式最多三段、逐段校验。
6. 黄金碎击、命定之死、水鸟乱舞、唤星、咒血斩击按专属设计允许跃起；
   服务端约束轨迹、落点、转向与重力恢复。普通空中攻击策略暂不改。
7. 切武器、死亡、维度切换、取消与合法受击中断清理施放和临时倍率；已确认消耗默认不退。
8. 不向共享 EF 动画 Phase 写各技能专用属性；SimpleWeaponInnateSkill.registerPropertiesToAnimation
   会修改共享对象。每个战技使用独立动画，或按施放实例严格限定属性。
9. 自定义动画使用 animmodels/animations 与 AnimationRegistryEvent；两端 ID 一致，资源缺失单独报错。
10. weapon-arts.design.json 只提供美术/同人原型值，不自动转换成服务器可执行配置。

## 后续顺序

1. 确认本次模型/基础攻击的实机验收，校正左右手、护甲、普通/细臂皮肤和碰撞尺度。
2. 先完成黄金波动作为地面波模板、黄金碎击作为跳技模板，再做其他六个固定专属技能。
3. 建立共享 FP、施放清理、追击时窗回归测试，再覆盖 22 个类型战技和战灰替换。
4. 为特殊类型提供独立动作/碰撞与法器逻辑，再提供低成本 GUI/掉落/远距 LOD。
5. 专用服务器、两名玩家、延迟/重复请求、资源包重载、Sodium/Iris 等锁定整合包组合全部验收后再发布。

## 验收与构建

```powershell
$env:JAVA_HOME='D:/JDK21'
./gradlew.bat build --offline --no-daemon
./gradlew.bat runClient -PweaponSmoke --offline --no-daemon
./gradlew.bat runServer -PweaponServerSmoke --offline --no-daemon
```

客户端测试用隔离目录 build/weaponSmoke，需有 saves/codex-weapons-smoke 测试世界。
独立服务端使用 build/weaponServerSmoke 和 25567 端口，不使用原存档与 Boss 测试数据包。
检查 WEAPON_SMOKE_COMPLETE、WEAPON_SERVER_PASS 及无 FAIL；Gradle 退出码不能替代游戏断言。
最终结果另见 `WEAPONS-INTEGRATION-REVIEW.md`。没有实测的性能与兼容性必须继续标记待验证。
