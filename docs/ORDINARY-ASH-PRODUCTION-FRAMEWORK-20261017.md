# 普通战灰首批制作：美术合同与DSH实施框架

> **已停止派工（2026-10-17人类纠正）**：先从MC百科与作者项目选现成模组，不继续三招自研/新renderer/定制素材/自然箱奖励适配。后续以[现成模组选型](D:/codexout/mcmod/docs/ASH-REUSE-SELECTION-20261017.md)为入口。以下保留历史设计，不是当前执行授权，不派美术或DSH按本文实施。

2026-10-17。人类最新要求“继续推进战灰制作”。**当前优先为回旋斩、重刺、踏前上斩三项普通战灰；不扩成全部战灰或全地图。** 战斗部门只交框架/接口/验收合同，DSH后续具体实现；美术由统筹在本轮结束后单独派发，本文件不自动启动美术或DSH。

八追忆VFX V1–V3框架已交付，保留未完队列，不在本轮重开实现；新魔法/祷告继续搁置。本轮只写本文及必要索引，没有改Java、脚本、资源或素材，没有构建/启动游戏/Harness。

## 1. 先复用：已核对的方案与真实基线

| 已查来源 | 可直接复用 | 本批决定/限制 |
|---|---|---|
| 项目OrdinaryAshCatalog/Runtime、OrdinaryAshes、AshBindings、SkillCasting/Driver/Transactions | 三项真实运行时、白名单、同一FP/冷却/命中出口 | 不再写第二套绑定/施放/扣费系统，不扩大兼容表 |
| 项目AshUnlocks/AshUnlockData/AshRewards/AshRewardTable及真实GraceMenu | UUID永久解锁SavedData、击杀者本人奖励、赐福权限、真实网络/快照与菜单内武器身份token | 保留持久化、同槽同类武器换入token轮换、组件无损与服务器重新校验 |
| [既有三系统合同](D:/codexout/mcmod/docs/DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md)、[复用优先计划](D:/codexout/mcmod/docs/REUSE-FIRST-DEVELOPMENT-PLAN-20261013.md) | 永久解锁假设、自然探索闭环、个人奖励、旧存档/权限边界 | 旧“尚未接Ownership/未实现目录”等已完成部分不重做；历史夹具/数量不是本轮通过证据 |
| 已依赖的开源NeoForge事件API | 本地21.1.219源码中的PlayerInteractEvent.RightClickBlock、PlayerContainerEvent.Open及服务端事件总线，源码标注LGPL-2.1-only | 为原版自然容器加最小奖励适配，不下载另一个战斗框架 |
| 原版MC 1.21.1结构与容器API | 本地资源JAR确有desert_pyramid、pillager_outpost结构及对应chests loot table；RandomizableContainer、ChestMenu.getContainer、CompoundContainer.contains、StructureManager、BlockEntity持久数据 | 调用原版API与自然世界内容，不把MC反编译源码当开源重新分发；本批无需新结构/NBT或世界生成库 |
| [现有原创序列工具](D:/codexout/mcmod/art/tooling/build-vfx-2d.mjs)、[像素定义模块](D:/codexout/mcmod/art/tooling/vfx-eight-clips.mjs)、[校验器](D:/codexout/mcmod/art/tooling/verify-vfx-2d.mjs) | PNG/透明度量化、逐帧/atlas/preview/manifest与SHA、尺寸/锚点记录 | 复用工具方法，不把旧红/黑/金大招换色当三招；原校验器固定8技/10序列/16帧，不可直接拿来给新8帧序列报PASS |
| 已有Lootr/Dungeons and Taverns取证 | Lootr候选1.21.1 NeoForge JAR/接口索引；旧计划评估过遗迹包 | 正式7项锁没有Lootr/该遗迹包。Lootr本地JAR无独立LICENSE条目、metadata指向旧分支许可URL；D&T旧审计为ARR。此批不引入、不拆资源、不假称已安装/许可已覆盖。个人物品箱仍属后续评估 |

当前三招准确参数（保留，不是本轮平衡设计）：

| ID/中文名 | 兼容组及普通武器 | FP | WINDUP/ACTIVE/RECOVERY，tick | 当前判定 | 当前表现缺口 |
|---|---|---:|---|---|---|
| eldencombat:spinning_slash 回旋斩 | slashing：dagger、curved_sword、curved_greatsword、katana、twinblade | 8 | 6/6/8 | onActive一次绕身判定，effectRange=2.4、effectRadius=3.2 | 借CURSED_SLASH，猩红斩迹不合适；无正常获取来源 |
| eldencombat:heavy_thrust 重刺 | thrusting：thrusting_sword、heavy_thrusting_sword、spear、great_spear、halberd | 9 | 8/5/9 | onActive一次身前胶囊，range=4.4、radius=1.6 | 借DEATH_SLASH，不是窄幅前刺；无正常获取来源 |
| eldencombat:stamp_upward_cut 踏前上斩 | cleaving：straight_sword、great_sword、colossal_sword、axe、greataxe | 11 | 9/6/11 | onActive一次身前胶囊，range=3.2、radius=2.0 | 借GOLD_IMPACT，黄金地面大冲击不等于上挑；已有margit死亡奖励入口 |

全部maxStages=1、followupWindow=0、不由战技施加位移；冷却20/25/30 tick，damage12/15/17、poise14/20/26也保持。现visual helper在onActive生成10 tick、followOwner=true的SkillEffectEntity，phase来自stageIndex（当前首段为1）。视觉会进入部分收招，不把10 tick当ACTIVE时长，更不延长ACTIVE来配图。

本轮核对到的玩法来源仍只有`margit -> stamp_upward_cut`；这证明奖励事件存在，不证明玛尔基特已经能在新世界自然遗迹中遭遇。后者未核实，不能编造“首批三招都可自然获得”的现状。

## A. 给美术的首批素材合同（候选先行，不覆盖runtime）

### A1. 内容和识别标准

| 招式 | 必须可读的主形 | 排除项 |
|---|---|---|
| 回旋斩 | 围绕人物腰/武器高度的水平弧扫，主刃迹短亮、余迹沿旋转方向衰减，透明中心可见人物 | 猩红咒血、死亡黑焰、整屏光圈、全实心圆盘；不画成黄金波前 |
| 重刺 | 从手前向+Z伸出的窄直线/尖楔风压，尾短、头尖，前后方向明确 | 巨大横向月牙、黑红爆发、突进/传送尾迹；视觉向前不代表新增位移 |
| 踏前上斩 | 靠近脚前起势、沿武器前方上挑的短弧，低→高运动明确 | 黄金大爆炸、落地坠击、击飞敌人暗示；没有真实击飞/踏步位移机制 |

主色为普通钢铁灰、银白风压，必要时低饱和土灰；不使用猩红、死亡/重力或金光大招配色。三招在原版人物及实际普通武器旁仍能分辨，不只交孤立的大平面缩略图。不得修改已选武器PNG、比例、握点、黑剑方向或原版玩家骨架来迎合特效。

### A2. 建议尺寸、挂点与坐标（均为待审看候选）

实体原点为施放者脚部位置；局部+Z向前、+X为面朝方向的左侧、+Y向上。尺寸/offset是世界格，角度为度，UV/anchor为0..1。普通主贴片保持世界技能平面，不billboard、不按屏幕归一化铺满。

| route设计ID / 新Kind设计 | 画布/anchorPixels | 建议worldSize / offset | 平面与动作 |
|---|---|---|---|
| ash_spinning_slash / ASH_SPINNING_SLASH | 64×64 / [32,32] | [4.0,4.0] / [0,0.95,0] | 水平XZ，yaw=0、roll=0；PNG向上对应局部+Z，弧形在平面内推进，中心大部透明 |
| ash_heavy_thrust / ASH_HEAVY_THRUST | 64×64 / [8,36] | [3.6,0.9] / [0,1.0,0.35] | 窄竖直斜侧平面，建议作者yaw=-70°；画布U主要沿前方，避免严格YZ平面在第一人称完全侧薄不可见 |
| ash_stamp_upward_cut / ASH_STAMP_UPWARD_CUT | 64×64 / [32,56] | [2.2,2.8] / [0,0.35,0.55] | 竖直XY，yaw=0、roll=0；按anchor计算bottom约0、刀迹自低向上 |

这些是候选视觉尺寸，不是伤害盒或服务器射程。不能由画布宽度改FP/命中参数，也不能把重刺的可见前沿宣传为精确命中边界。美术可在清单中提出局部尺寸/挂点改进及理由，交统筹审看后再给DSH；不直接改正式路由。

重刺方向存在明确接入缺口：当前FxArtwork主路径只有horizontal与roll，不消费旧legacy的planeYaw。B部分规定为作者几何添加**可选yawDegrees，默认0**，避免启用旧字段后把原10路由一起转错。美术须交标出U/V/法线的预览，DSH验证第一/第三人称实际可读；-70°不是已经实机通过的值。

### A3. 帧序、触发与退场

- 首批每招**8张独立透明RGBA PNG**，共24张；64×64像素边缘，不插值放大出模糊细节。复用既有有限alpha等级/像素工具，逐帧可辨变化，不以8个同字节PNG充数。
- 触发是服务器本段onActive，一次，不另加预备粒子。frame0已有可辨钢铁/风压主形；1–3为主段，4–5为短余迹，6–7退场。WINDUP已存在，不把前几帧全画成空白导致命中后才出现主体。
- 用`FxArtwork.frame(progress,8)`直接采样10 tick自然视觉寿命；不能先量化16步。10 tick=0.5秒（正常20TPS），理论每帧约1.25 tick；客户端晚同步/低帧率可能错过采样窗口，不承诺每次自然施放8/8，不为凑帧延寿。
- 保持followOwner=true与当前cast关联；取消/死亡/换维度/新cast替换时不重放尾帧，实际完成时不残留。无需新增追击或输入段。
- 首批**3D地块碎片预算为0**，不因旧GOLD_IMPACT别名触发一整圈石块；主贴片≤1 quad。踏前上斩以低→高钢铁刀迹体现，不强依赖泥土爆炸。若美术认为必须补触地尘土，可另交“ground-only候选”并注明未接入；只有真实支撑时才可出现，不能画入必播主PNG后在空中也扬土。该可选副序列/接口由统筹另列，不偷偷加入本批runtime。

### A4. 交付路径、manifest与准入

候选写入新目录`D:/codexout/mcmod/art/ordinary-ash-2d/candidates/batch-01/`，不得改写旧`art/vfx-2d`的160帧、旧manifest或正式routes。美术由统筹后续单独启动；本轮不会创建这些素材文件。

每招含`frames/<skillPath>/frame_00.png`至`frame_07.png`，4×2无损QA atlas（256×128）、透明/深浅底原尺寸联系图，以及人物/武器比例与U/V/法线/anchor标记的离线preview。atlas/preview/QA不入游戏包，不能按atlas带边距的格子二次切图。离线预览不是实机验收。

候选manifest最少记录：schemaVersion、candidate状态、skillId、拟routeId/Kind与拟wireId、frameCount/size、每帧file/SHA256、atlasRect、anchorPixels/Normalized、worldSize/offset/horizontal/yawDegrees/roll/scales、onActive及10tick来源、followOwner/phase、secondaryDebris=0、source/provenance/license/作者/允许发布范围、source工具与输入版本、QA结果、`runtimeIntegrated=false/inGameAccepted=false`。像素锚点除以64就是normalized，不能把像素当模型单位或格。

优先原创工具生成/自制序列；复用项目自有绘图函数需保留来源记录，不冒称外部原作授权。外来素材若确有必要，先交准确项目/版本/具体文件/许可/署名要求，未准入只能research-only。不得把原作提取图或许可不明的高分辨率图混入候选发布清单。

旧`build-vfx-2d.mjs`会写旧目录，旧verify固定16帧/8技/10clip。美术可复用其中PNG/绘图方法建立隔离候选入口与对应8帧校验，不能直接跑原入口覆盖旧成果，也不能改旧校验预期来掩盖新批未交付。人类选版后才由统筹提升为`art/ordinary-ash-2d/full`及独立发布白名单；DSH只消费准入单帧。

## B. 给DSH的最小具体实施合同

### B1. 独立普通表现，复用原驱动和renderer

以下均为**设计接口/待实现**，不代表当前已有：

1. 在`SkillEffectMesh.Kind`尾部追加`ASH_SPINNING_SLASH(11)`、`ASH_HEAVY_THRUST(12)`、`ASH_STAMP_UPWARD_CUT(13)`。现0..9与NONE(10)及其顺序不动；继续显式wireId/fromWire，不用ordinal，不插进旧枚举中间。效果实体不落盘，但仍须保证已有网络int语义稳定；玩家已存战灰namespaced ID不改。
2. `FxSlashRoute.routeId/defaultsForRoute`添加`ash_*`三路由；普通运行时visualFor只映射对应新Kind，未知ID明确无表现/报错，不再默认借GOLD_IMPACT。原十路由/颜色/形制不改。
3. 主实体仍由`SkillEffectEntity.spawn`在onActive生成一次，10tick、原phase/followOwner，现有服务器命中和FP照旧。不要把ASC效果复制成另一套实体/独立时间线，不创建视觉命中包。
4. `FxArtwork`可选`yawDegrees`默认0，解析旧记录缺字段仍为0；保留旧构造入口/默认，使现有十路由不变。同一作者平面的组合约定为`outerActorTransform * T(offset) * Ry(yawDegrees) * Rx(horizontal?90:0) * Rz(roll) * quad`；只在现有ArtworkRenderer做一次，不再叠legacy planeYaw，不由相机朝向计算。
5. ready三路由均使用既有particle atlas。建议frame ID为`eldencombat:ordinary_ash/<skillPath>/frame_00`等，对应`textures/particle/ordinary_ash/...png`；route key为`ash_spinning_slash/ash_heavy_thrust/ash_stamp_upward_cut`，与保存的skill ID及既有UI key明确分开。
6. 缺资源/CORE仍提供对应普通钢灰简轮廓：回旋水平短弧、重刺窄前楔、上挑低→高短弧；不借死亡/猩红/黄金轮廓。复用现有plane/panel原语和预算，必要时只给outline选择增加普通形状分支，不新造renderer。NOT_HANDLED、隐藏、时间结束、预算0不得绕回错误大招；本批不重新执行八技V1–V3整轮，若共同接口的已知零提交问题阻断普通路由，应向统筹列出最小共用修订及回归范围，不借此扩开八技补正队列。

新wire值意味着旧客户端不支持这些画面；按配套客户端/服务端版本发布，不能把旧fromWire→NONE当“完全兼容”。当前payload注册版本是`6`，DSH需在版本/能力声明中明确新支持范围，必要时提升握手版本并验证拒绝不匹配包；不凭空更改既有包字段、存档ID或兼容表。

现有主预算30000顶点及LOD/隐藏规则继续复用，首批普通主sprite为4顶点、无3D碎块；轮廓按实际panel计数。不要把“8帧已载入”当实际主体上屏；复用最新有效截图/提交/可比较状态门禁，普通游玩画面保留玩家与武器。

### B2. 原版简单释放姿势，不增加骨骼/位移

新增三份普通`skill_poses`建议仅覆盖body/rightArm/leftArm的小量WINDUP/ACTIVE/RECOVERY轨迹，沿现有SkillPoseData加载/采样；腿、行走、普攻和武器renderer保持原版，不加root motion/自定义关节。左右主臂应与持握武器一致，不能把左主臂武器的释放姿势硬套到右臂；具体角度由候选预览与实机验证确定。

有一处已核对的接入差距：`SkillPoseAnimator.prepare`以文件basename存键，`applyPose`按完整active.skillId查找。三项普通ID带`eldencombat:`，仅增加`spinning_slash.json`并不能保证姿势命中。DSH应最小支持完整资源命名空间键，并仅为本mod保留旧八技bare-ID别名；不要全局剥离namespace造成其它包重名。新三pose文件及ID映射属于DSH接入，不让美术修改Java或正式pose资源。若首批不需要独立释放姿势，也必须明示沿用原版动作，不能在回执中写“已接上三招动作”。

### B3. 剩余两项正常来源：原版自然遗迹、本人开箱领取

本批建议来源（**方案，尚未实现/验证**）：

| 战灰 | 原版结构key | 原始容器loot table | 本批领取含义 |
|---|---|---|---|
| spinning_slash | minecraft:desert_pyramid | minecraft:chests/desert_pyramid | 本人实际打开合格沙漠神殿自然宝箱后永久解锁 |
| heavy_thrust | minecraft:pillager_outpost | minecraft:chests/pillager_outpost | 本人实际打开合格掠夺者前哨站自然宝箱后永久解锁 |
| stamp_upward_cut | 继续现margit死亡奖励 | 不新增箱子别名 | 仍只给合法击杀者；自然遭遇入口若未验单列，不能用夹具召唤冒充 |

两种原版结构/表已在本地1.21.1资源JAR核实，无需新结构、物品、美术建筑NBT、第三方mod或世界生成注册；旧地形不强制重建，新区块可生成它们。建议选择是首批制作方案，不冒充已获人类审看或现已可领；后续若统筹换来源，只改来源表/验收，不动技能数值。

**不能只用“第一次生成容器战利品”的成就当多人领取实现。** 本地RandomizableContainer源码先给当次ServerPlayer触发GENERATE_LOOT，随后`setLootTable(null)`；第二位玩家再开同箱不会再触发。该原版接口已查但不满足本批同遗迹两玩家个人解锁，故不用这个更短但语义不够的方案。

最小适配：一个纯来源定义表（可新增`AshExplorationSources`），一个服务端容器适配器（可新增`AshExplorationRewards`或扩现AshRewards）。**每玩家解锁账本仍只有AshUnlockData**；只在自然源箱的BlockEntity持久数据保留来源证据，不再建每玩家/每节点领取数据库。

**设计流程/字段，未实现：**

```text
RightClickBlock on logical server:
  validate alive non-creative/non-spectator player + real chest
  verify source loot-table key + actual registered StructureStart/piece at chest position
  preserve/validate immutable server source marker on that block entity
  keep one short-lived pending(player UUID, dimension, blockPos, sourceId, hand)

PlayerContainerEvent.Open on logical server:
  consume matching pending
  verify actual ChestMenu contains that same block entity; still valid/reachable
  recheck source marker, block identity, dimension, structure and supported ashId
  AshUnlocks.grant(player, source.ashId)   // only this UUID; notify only if newly added
```

- 来源首次登记必须在原loot table被原版清空前核对；建议NBT子项`eldencombat:ash_source`保存schema、sourceId、原lootTable、结构key、原dimension/blockPos（必要时start chunk）。字段只由服务器适配器从原生数据产生，不读客户端自报战灰ID、箱子名字或物品lore，不保存玩家领取列表。写入后setChanged并验证保存重进可读。
- 同箱以后loot table为空，可使用此前验证的来源标记，但必须再次核对原位置/维度/实际结构/容器身份；玩家新放的无标记普通箱不能只因在遗迹内就授予。损坏/未知schema、搬移或不匹配标记拒绝，不把客户端NBT当凭据。
- RightClickBlock只是候选，不当作领取完成；被其它模组取消、打不开、权限/距离失败、未真的打开匹配菜单都不授予。PlayerContainerEvent.Open/ChestMenu.getContainer/CompoundContainer.contains可复用，支持原自然箱并成的双箱时需证明包含的是合格那一半，不接受赐福/任意菜单顶替。
- pending每玩家至多一条，有短TTL（建议≤2服务端tick，正常同线程打开应立即匹配），成功/失败即消费；登出、死亡、换维、服务端停止清理，不定时扫描全世界，也不持续加载区块。
- P1开箱登记后，即使箱内物品被拿空，P2仍需本人打开同源容器才为自己的UUID解锁；P1不替P2领取。普通箱内物品仍是原版共享战利品，**这里保证个人战灰解锁，不把箱内物品变成Lootr个人库存**。
- 已被预生成/漏斗提前拆表或老存档开过、又无可信标记的箱子，不靠猜测补发；去未开过的合格自然箱/新区块领取。没有强制旧区块改造。本批不处理与Lootr自定义容器的兼容，若后续批准安装再加可选适配。
- 结构Start和loot table是普通玩法来源证据，不是对管理员/世界编辑器的防伪体系；自然闭环验收仍需新种子实际生成与本人探索，`/setblock`带loot table、`/place`、`/give`、`advancement grant`都只能诊断，不得当自然来源通过。
- 保留margit原死亡奖励及命中归属，不把任意附近人或目标自然死亡都解锁。所有奖励仍进同一grant；重开、重领、重登只返回已拥有，不能重复播报/增加等级/消耗物品。三项都是永久账号进度（世界内UUID），不是消耗型战灰。

旧计划要求评估Lootr个人宝箱的**物品**语义仍保留后续，不能称本批已完成Lootr整合。若未来来源必须换新物品/结构/未审mod，应单列精确版本/加载器/许可、注册ID、正常获取、所需图标/建筑素材及迁移策略，统筹批准后串行安排；不能暗加进本批正式包。

### B4. 具体修改面与不变项

| 文件/数据 | DSH最小接续职责 |
|---|---|
| OrdinaryAshRuntime | visualFor三种独立Kind与10tick发射，保留whirl/sweep/land/sound/FP及无位移单段；旧注释“无新美术”更新为准确状态 |
| SkillEffectMesh.Kind、FxSlashRoute、FxArtwork、ArtworkRenderer及必要outline分支 | 追加11..13、3个route/default、可选yaw默认0与普通缺图轮廓；复用原渲染及预算，不改旧十路由形制 |
| SkillEffectEntity | 默认不需新wire字段/实体；继续cast关联清理；如纯表现确需接口调整，单列最小变更，不借此改命中或扩大寿命 |
| SkillPoseAnimator、三份普通skill_poses资源 | namespaced查找兼容、原版关节简单姿势/左右主臂；无新骨骼/root motion |
| build.gradle现syncArt、routes/2d.json | 候选批准后加独立来源/输入白名单，仅24张准入单帧；保留102装备/UI及160旧VFX原路径/内容，不打包atlas/preview/QA/工具 |
| AshExplorationSources（拟新增纯表）+服务端容器奖励适配 | 两项自然源表、实际容器Open匹配、服务端来源标记与pending清理，全部复用AshUnlocks |
| AshBindings/OrdinaryAshes/AshWeaponGroups/AshWorkshop/GraceMenu | 原则上不需改；严格保留白名单、追忆锁定、所有权、token、距离/发现/战斗校验及组件保留。现三组互斥，不能为了测替换扩兼容表 |
| AshSmokeHarness及必要现有测试 | 从只重点测stamp的诊断扩大到三项；正常输入与自然领取另记，不拿bind/requestById直调代替玩法 |

源码候选工具/目录、Kind/route新增与资源加载必须先按上述接口接妥，再把批准素材设ready；不能先把未知route写入正式manifest让整个既有配置被拒。旧FxArtworkTest有“恰好10路由”断言，新增后应保持旧十项逐项内容检查并单独检查新增三项，不能删掉旧哈希/几何保护或改旧美术manifest成13项伪装交付。

## C. 有限顺序与验收合同

执行顺序服从两部门串行：

1. 本轮框架完成并收束，由统筹核对状态后派美术做三招候选；美术不运行尚未接通的新Kind、不覆盖runtime。
2. 美术交候选/QA/来源与人类审看记录后收束；统筹再按人类安排给DSH。DSH复核素材准入，完成最小表现/来源/必要动作接口，不从头重写三系统。
3. 模块实现写完后集中编译/针对测试；游戏一次一个。先三招表现和拒绝边界，再自然新世界双来源→本人领→赐福绑定→正常战技键→保存重进；保留margit奖励对照。
4. 交实际源码/资源/JAR哈希、命令/终态、原图/普通玩法闭环与未验项；必要美术补图先收束DSH/战斗，再由统筹单独派美术。

| 验收组 | 必须证明 | 不能代替的证据 |
|---|---|---|
| 注册/兼容 | 旧0..10 wire与保存ID不变；新三route支持；15种兼容普通武器逐组检查、追忆/不兼容拒绝 | 只看目录3条、把effectRadius当伤害框、允许任意武器 |
| 正常施放 | 三招经普通战技输入和真实绑定，单次扣FP8/9/11、原冷却/阶段；目标至多一次结算、无新位移 | requestById/命令绕过绑定，或只测stamp再声称三招通过 |
| 表现 | 一三人称/左右主臂、人物/武器比例、三种形状可区分；8帧加载/采样/实际提交分开；主体图有效、无全屏大招；缺PNG/CORE合法普通轮廓、结束不复活 | preview、atlas齐全、背景像素多或加载屏截图 |
| 正常来源 | 两种原版自然结构真实箱，服务器来源标记/实际菜单匹配；未探索未开箱不授予，玩家自己领取 | 管理员grant、带loot table的人造测试箱、仅站在遗迹里 |
| 拒绝 | 未拥有、不兼容、追忆锁定、FP不足/冷却/忙碌、赐福权限/距离失效、旧/伪token；拒绝时武器完整组件及不相关数据不变 | 只看按钮灰色或沿用旧notice，必须等待本次真实服务器应答 |
| 重领/保存 | P1重开多箱幂等；退出服务器/重启后源标记和UUID解锁保留；死亡不丢、不重领收益 | 仅内存查询或给同一玩家复制组件 |
| 两玩家 | P1领不授予P2，P2同一已开源箱也能本人领取；两人绑定/FP各自独立，重登不串号 | 同进程两个模拟布尔、服务器遍历在场人全授予 |
| 取消/死亡/换维 | windup前取消不产生新命中/视觉；onActive后按原事务退款/清理规则，无额外命中、无残留/重播；pending与姿势清理 | 为拍图延寿，或者改原退款/命中规则获得测试通过 |

兼容组实际列出5+5+5=15项，旧报告“13件白名单”等历史计数不可照搬。纯检查复用OrdinaryAshCatalogTest、AshUnlockSetTest、AshRewardTableTest、SkillCommitBoundaryTest、FxArtwork/FxSlashRoute/SkillPoseData相关测试；只补来源验证/幂等和新增表现的针对断言，不为本批重新执行所有八技地形组合。

`AshSmokeHarness`目前是引擎诊断，部分直接调用bind/施放入口且使用测试平台/武器/FP；它不能证明正常获取或真实按键。旧文档还记录过不稳定/未复跑轮次；DSH以最新实际日志判定，不引用旧绿字或旧红字替本次结果。所有Harness仍不进发布包。

建议交付`D:/codexout/mcmod/docs/ORDINARY-ASH-PRODUCTION-REPORT-20261017.md`：分别记录素材候选、人类选版、代码准入、实际加载、机器场景、自然双人/保存闭环与人工观感。首批不含可消耗战灰物品、新建筑、个人物品库存或额外mod；最终专服/多人实测、光影、独立JAR/PCL导入及整包目标未验则保持未验。

## 本轮实际收束

本文是给后续美术与DSH的输入，不表示候选目录/新Kind/新来源已落地。旧三招逻辑与102装备/UI、旧160帧、用户选版/黑剑方向/弩炮校准均未修改。本轮命令仅只读源码、本地引擎/候选JAR/资源核对及文档保存，均已返回；未构建、未开游戏、未启动DSH或美术。统筹可在重新核对状态后串行派下一部门。
