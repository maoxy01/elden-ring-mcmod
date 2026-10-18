# 全套装备 2D 美术交付

2026-10-15。范围仅美术素材、离线预览、证据和交接；本次未修改 Java、正式资源、Gradle 或运行实例，未启动游戏。

## 结果与状态

- 覆盖 42 项：33 类普通装备、8 件追忆武器、1 个药瓶。28 项自有精灵，14 项原提供方完整模型映射。
- 自有资产共 94 张 PNG：38 张持用/状态图，56 张独立 16/32 像素 GUI 图。提供方核对了 43 个模型状态及全部引用纹理、父模型和 predicates。
- **素材文件/定义已交付；人类美术审查、依赖安装、运行接入、实机验收未完成。** 43 个模型状态不等于43种不同纹理；格挡、投掷可能共用纹理但变换不同。
- 本批没有替换用户选定的大剑、特大剑旧模型。二维剪影由其旧版源设计投影，原 model/mesh/bbmodel 哈希验证通过；不是另做一套新3D模型。

## 交付入口

| 文件 | 用途 |
|---|---|
| `art/equipment-2d/full/preview.html` | 42项浏览、逐件选择、状态切换、浅深底、左右手、标准/纤细手臂、同比例人偶 |
| `art/equipment-2d/full/equipment-42.json` | 当前实际选择，含 PNG/来源哈希、尺寸、握点、世界长度、状态、待接接口 |
| `art/equipment-2d/full/publishable-assets.json` | 仅本批自有94张PNG白名单，文件路径相对该清单目录 |
| `art/equipment-reuse/research-only-full/evidence.json` | 14项提供方完整定义取证；包含 coating 等附加层，不只 layer0 |
| `art/equipment-reuse/equipment-mapping-42.json` | 更新后的逐项总表；candidate 为历史候选，实际选择以 selectedProvider/fullAssetManifest 为准 |
| `art/equipment-2d/full/validation.json` | 静态及浏览器检查记录；不得替代游戏验收 |
| `art/equipment-2d/full/states-and-grips.png` | 状态与握点审查图，含第三方研究素材，不进入发布包 |

六类样板仍在 `art/equipment-2d/`，修改前备份位于 `art/equipment-2d/backups/six-before-full-20261014/`；原3D与VFX未覆盖。

## 复用选择与来源

- 原版4项：直剑 `minecraft:iron_sword`、斧 `minecraft:iron_axe`、短弓 `minecraft:bow`、弩 `minecraft:crossbow`。读自本地 Minecraft 1.21.1 / NeoForge 21.1.219 资源，正式接入引用原资源，不再分发提取的 Mojang PNG。
- Spartan10项：匕首 `iron_dagger`、刺剑 `iron_rapier`、刀 `iron_katana`、大斧 `iron_battleaxe`、槌 `iron_flanged_mace`、大槌 `iron_battle_hammer`、矛 `iron_spear`、戟 `iron_halberd`、镰刀 `iron_scythe`、长弓 `longbow_iron_strengthened`。统一命名空间 `spartan_weaponry_unofficial`。
- Spartan 固定发布包 `spartan_weaponry_unofficial-1.2.3-1.21.1.jar`，SHA256 `84d3ddafb519e2f5e03f2681ff52c30c92c9512181f61a7a7487399a0179888f`；[作者发布页](https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry-unofficial/files/8717810)。沿用既有 Apache-2.0 声明取证，不把此声明扩张为所有单张美术素材的拆分许可。
- 仅提供完整依赖映射；`research-only-full` 的未修改PNG只用于本地研究，全部排除于自有发布清单。作者署名沿用审计：ObliviousSpartan、xwerffx、Mai_xiyu。
- 以上 ID 的证据是发布包定义，不是实际运行注册表。`installed=null`、`runtimeRegistryVerified=false`。严格2D由模型结构判断，含自定义 loader 的实际显示仍需运行确认。
- 槌选用棱锤/权杖式 Mace 类别，不称其为方头锤。特大武器、弩炮、大矛使用自有设计，未用近似候选冒充；也未为双头剑、盾、钩爪额外引入其它武器系统。

## 美术变更

- 自有精灵保留阶梯像素边缘和原材质调色，不使用平滑插值。32像素为普通装备主图，追忆主图64像素；所有自有图独立提供16/32像素GUI版本，GUI尺寸不决定手持尺寸。
- 神躯化剑仅在GUI版加强刃根、纵向金色核心的连续性；手持图和1.710格长度未统一缩短。16像素槽仍只能表达轮廓，不能承载64像素的全部纹理。
- 大弓4状态：standby / pulling_0 / pulling_1 / pulling_2。共享画布、比例与握点，弓臂、弦线及箭矢位置改变，不用换色冒充拉弓。
- 弩炮5状态：standby / loading_0 / loading_1 / loading_2 / loaded。最后一帧含已装填弩矢；这些是美术状态，不规定装填时间、弹药或伤害。
- 三种盾各有正反面；格挡复用正面改变位置，**不是复制一张PNG作为新状态**。正反双面材质、格挡朝向由接入方实现。
- 碎星大剑交付单刃精灵；双持由运行侧摆放两个实例，不能把双剑挤进同一把物品的持用图。
- 软鞭保留源骨骼的静置弯曲，连枷/软鞭仅交付静置精灵，不承诺已包含攻击形变。
- 药瓶由原设计与调色补齐瓶肩、瓶颈；仅full满瓶一态。`test_flask` 对应旧源 `healing_flask.model.json`，饮用、空瓶、补充规则待战斗侧确定，不自创消耗机制。

## 接入要求与未决项

1. 使用逐件 `worldLengthBlocks/sourceSpanPixels` 和 `anchorPixels`，不要按整张PNG边长归一大小。大剑、特大剑等尺寸不同。既有比例多数仍是美术估值，不是已完成法环实测。
2. 握点落在有效像素且已检查柄部；网页标记不是游戏掌心验收。左右手、标准/纤细手臂、第一/第三人称应分别校准；不得多次叠加握持偏移。
3. 提供方保留完整父模型、custom loader、coating、display、overrides和原predicate命名空间。预览仅展示基础纹理；提供方格挡/投掷变换的游戏效果不在网页中仿真。
4. 本次读取的 `gradle/equipment-2d.gradle` 仍拒绝 external_model，sprite 分支也未消费本批握点/display与GUI独立图。**不要只把状态改为ready**；由代码侧实现接口后接入。
5. 绑定弓弩美术状态时明确字段、时机和中断回退；`artDrawFraction` 是视觉拉开比例，不是充能时长。弩炮烟花态/额外弹药、药瓶空瓶态尚未定义，不能挪用原版弩的玩法作隐含决定。
6. 自有94张PNG可按白名单准入；依赖原图不可混入自有素材包。安装与许可复核由整合方执行，本轮没有下载或安装新依赖。

## 验证与验收

- 静态通过：42个唯一ID、94张自有PNG哈希、源文件/选定旧版哈希、alpha仅0/255、透明边界、全部自有图8邻接单连通、所有状态握点为有效像素。
- 浏览器通过：1440×1080与390×844、原生16/32槽位尺寸、无横向溢出、左右手/纤细手臂、全部弓弩与盾状态切换、同比例画布非空。截图已落盘。
- 已目视查看整表、同尺度页、状态/握点图和手机预览；这是制作侧审查，用户验收仍待完成。
- 实机尚需：资源重载、物品栏/掉落物/展示框、第一/第三人称双手握柄与占屏、盾背及格挡、弓弩起始/中断/满蓄力状态，以及缺依赖时的明确提示。请将代码或游戏问题与美术问题分开记录。
