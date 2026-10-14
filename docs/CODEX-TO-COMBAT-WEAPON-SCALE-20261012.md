# 给战斗代码对话的交接：逐件武器比例与战斗接入

日期：2026-10-12。工作区：`D:/codexout/mcmod`。

最新用户外形选择：great_sword 与 colossal_sword 已恢复到 `swords-before-refinement-20261012` 中的旧版，straight_sword 保留新版。model/mesh/bbmodel、catalog、比例与握区已刷新。同步当前 `art/weapons/` 文件；不要把缓存的三剑 R4 候选整批重新接回游戏。复原哈希清单见 `art/weapon-proportions/large-sword-version-selection.json`，两件的尺寸与实机握点校正仍需继续。

后续并行更新：最新读取的源码已出现 `WeaponDisplayTransforms.matrix(..., heldScale)` 和 `EquipmentRenderer` 的 `entry.proportion().heldScale(...)`。下文第 2 节的 `0.85 / h` 是初始审查发现，已不代表最新源码仍保留该行。当前游戏实际加载版本、资源路径与最终缩放乘积由接入方取证；不能仅凭源码有入口宣称实机已修复。

新增反馈与工单见 `WEAPON-SIZE-AND-QUALITY-REVISION-20261012.md`：用户反馈全部武器在游戏手持画面偏小，33 件普通装备质量不足。六类“当前 / +15% / +30%”离线尺寸候选已保存于 `art/weapon-proportions/size-review/`，包含验证和截图。此处的候选基于原草案，不是最新运行实例的实际显示尺寸；游戏端需要自行导出当前实际倍率再拍同机位候选。正式 targets 尚未覆盖。

用户最新分工：本对话负责美术，另一个对话负责战斗。用户要求按法环武器相对人物的比例换算，保留每把武器的大小差异。本文件供用户转交，不代表已经向其他对话发送消息。

## 1. 接手范围与当前基线

当前代码是 Minecraft 1.21.1、NeoForge 21.1.219、Java 21 的原版战斗扩展。先读取当前代码及 `COMBAT-DEPARTMENT-WORK-ORDER-20261012.md`，按其 C0–C4 排序收口；历史 Epic Fight 方案与 catalog 中旧 `proposedEpicFightPreset` 不是当前实现依据。

本次美术交付位于 `art/weapon-proportions/`：

- `targets.json`：41 件武器/盾牌逐件比例的编辑源，按稳定物品 ID 配置。
- `profiles.json`：当前模型包围盒、目标尺寸、网格倍率、握点和原版 display 倍率。
- `SCALE-TABLE.md`：可读的逐件换算表。
- `preview.html`：固定人物基准的比例对照与握点夹具。
- `source-manifest.json`：读取的 82 个源资产哈希；原模型、网格、图集和玩家骨架未改写。
- `validation.json`：美术检查结果；`runtimeIntegrated` 和 `inGameAccepted` 当前均为 false。

比例表是待验草案：神躯化剑有已查看的原游戏玩家持握图；另外 40 件是按明确代表武器填写的美术初值，尚未完成持握截图测量。不要把这些数值写成官方尺寸、源模型实测或已验收比例。后续校准只改对应 ID，不能回退成统一长度。

## 2. 已确认的尺寸问题

初始审查时，`src/main/java/com/dsh/eldencombat/core/WeaponDisplayTransforms.java` 的手持分支使用：

```java
float fit = ICON_LENGTH / Math.max(.001f, h); // ICON_LENGTH = 0.85
```

这会抵消模型原来的长度差异。只改模型坐标、只提高整组倍率，或给“大中小”三档套固定长度，都无法满足逐件比例要求。

`EquipmentRenderer.renderByItem` 在原版 display 变换之后再应用 `WeaponCorrections` 和上述矩阵。代码端需要消费逐件美术比例；本次没有修改 Java、物品模型路由、Gradle 或服务端规则。

## 3. 显示接入契约

```text
r = 法环玩家持握版本的武器主要尺寸 / 同场人物可见身高
H = MC 实际可见人物身高
L = r * H
s = L / 当前模型静止姿态的主要尺寸
```

`referencePlayerHeightBlocks = 1.8` 来自本项目预览人偶的实测几何高度。它不是对原版 PlayerRenderer 可见身高的测量；不能直接拿玩家碰撞箱高度代替可见模型高度。实机使用当前渲染人物的实际高度，并以相同的整体换算系数校准全表。

主握点为模型坐标 `[0,0,0]`，长轴 +Y，薄片厚度沿 Z。`.model.json` 坐标按 `unitsPerBlock=16` 换算；`.mesh.json` 的 positions 已是方块单位，禁止再次除 16。

`visualScale` 用于已换算成方块单位、尚未做 display 缩放的几何。不要把这个倍率烘焙到网格后又应用一次。缩放以握点为中心，副握点和机械骨骼随整件等比变化；不单独拉长刀身，不改变 UV，不拆开护手与剑柄。

当前剑类第三人称 display 为 0.85，弓/弩为 0.9，第一人称为 0.68。生成表已逐项读取现有 parent 的左右手倍率，`suggestedLocalScaleForCurrentDisplay` 等于 `visualScale / 当前 display 倍率`。这些值以当前资源为条件，资源包改了 display、校正或人物高度后必须重算。

保持图标中心握点补偿的概念：

```text
M = T(iconGrip) * R(existingOrientation) * S(perItemVisualScale / upstreamDisplayScale)
```

这是现有图标坐标桥接的局部示意，不包含整个手臂变换栈。不要在中心平移之前额外全局 scale，导致握点一起偏移；不要删除原版 `translate(-0.5,-0.5,-0.5)` 对应的补偿。现有 `WeaponCorrections` 位于外层，含平移/旋转/缩放时需用实际矩阵验证主握点。

各显示场景分别验收：

| 场景 | 要求 |
| --- | --- |
| 第三人称手持 | 逐件人物比例是主要验收目标；匕首、直剑、特大剑、长矛长度明显不同 |
| 第一人称 | 可独立调整镜头构图和持握位置；不要反向压平第三人称尺寸差异 |
| GUI、快捷栏、JEI | 可以分别适配图标格；此处的 fit 不参与世界尺寸 |
| 展示框、掉落物 | 单列显示政策和截图，不用它们的适配倍率当手持倍率 |
| 盾牌 | 按盾面朝向和前臂握姿检查，不能直接套钻石剑握法 |
| 弓、弩、法器 | 保留各自显示模板，并检查拉弓/装填、主副手及机械骨骼 |

碎星大剑的比例指单把，双持由持握逻辑放置两把；不把两把合并包围盒再缩小。鞭子当前记录静止 Y 跨度，不是展开长度或命中距离。

## 4. 战斗任务

执行已有战斗工单并补下列接口；先核实已实现部分，避免重复重写。

1. 输出八追忆战技的当前事件表：物品 ID、阶段、FP 扣费、起手、生效、收招、命中窗口、出波/跃起/落地/追击、取消清理。交付 `docs/COMBAT-EVENTS-20261012.md`，美术据此校准姿态和效果。
2. 保持 FP 蓝条施放。正常游戏校验 FP 与冷却，测试实例支持用户已要求的无限 FP；它应是测试开关，不能让正式服务器默认无限施放。
3. 收招尽量短且能自然衔接，依据当前代码记录前后 tick。不能把自动多段、追击窗口和终段恢复统一成一个计时器。
4. 服务端拥有施放、伤害、碰撞、位移与中断判定。切武器、死亡、离线、换维度、超时都要清理施法状态与视觉实体。
5. 武器显示大小不自动等于 reach。若现有命中范围与视觉明显不符，记录两者差异，在战斗配置中独立调整并测试；不得直接用整件装饰包围盒当攻击体积。
6. 本轮按现有原版普攻收口。若恢复历史“按武器类型做连招”的需求，应在战斗计划中显式登记为后续动作批次，不由本次美术缩放顺带改写输入或普攻。

八技对应：神躯化剑 / `wave_of_gold`，石槌 / `gold_breaker`，黑剑 / `destined_death`，亵渎圣剑 / `takers_flames`，义手刀 / `waterfowl_dance`，碎星 / `starcaller_cry`，圣矛 / `bloodboon_ritual`，咒剑 / `cursed_blood_slice`。

保留用户已定方向：无处决；允许战技自身跃起。黄金波动成形后前沿各部分保持相对位置，没有额外横斩光效。水鸟与命定之死保留大范围视觉；地面碎块按现有视觉效果处理，永久破坏地形须另立战斗规则。

## 5. 对原美术任务包的修正

`DSH-ART-MODELING-TASK-PACKAGE-CODEX-20261012.md` 中“41 件共用 weapon_placeholder，因此外观完全相同、没有贴图”的结论不成立。当前 `EquipmentRenderer` 会通过 `WeaponAssets.entryFor(item)` 取逐件模型和图集，GUI 也有自定义渲染路径。缺少 parent 的 texture 字段不能证明实际渲染缺贴图。

不要批量把 parent 改成 `item/generated`。若另加 GUI 图标，需要按显示上下文选模型，保留手持 `builtin/entity` 的自定义 3D 路径。先实机截图定位可见性和明暗问题。

当前 33 件类型武器和 8 件追忆游戏版图集均为 128×128；旧文档的 512/2048 尺寸与扁平化未完成描述已过时。材质槽数也不等于实测 draw call 数。

## 6. 验收与回报

先用匕首、直剑、特大剑、长矛、中盾、神躯化剑六件做同场人物对照，再覆盖 41 件。测试断言应验证相对长度与握点稳定，移除“不同武器最后等长”的旧预期。照片要同人物、同机位、同 FOV，显示左右手与 classic/slim 两种皮肤。

检查附魔、资源重载、第三人称、第一人称、物品栏、JEI、掉落物和展示框。网页的握点夹具不是原版 ItemRenderer，网页通过不能当作游戏握姿通过。

构建和 smoke 与其他开发串行安排；不要同时 clean 或覆盖共享报告。按战斗工单运行适用测试，最后交付游戏截图、日志、JAR 哈希和具体已验/未验项。

本美术导出可独立重建：

```powershell
node art/tooling/build-weapon-proportions.mjs
node art/tooling/verify-weapon-proportions.mjs --browser
```

这两个命令只生成或检查 `art/weapon-proportions/`，不会将比例自动接入 JAR。接线完成后再更新 `runtimeIntegrated` 与实机验收报告，不能只改状态字段宣称接入成功。
