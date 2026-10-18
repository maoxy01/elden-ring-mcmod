# 八追忆战技VFX：V1–V3最小补正框架

2026-10-17。战斗部门只交付框架/接口/验收合同，DSH负责后续具体实现。本轮仅修改本文和必要执行索引，不修改Java、脚本、资源/素材，不构建、不运行游戏/Harness，不启动DSH或美术。人类明确搁置的新魔法/祷告不在范围内。

依据[最新统筹复核](D:/codexout/mcmod/docs/CODEX-VFX-CLOSURE-REVIEW-20261017.md)，续用[原运行框架](D:/codexout/mcmod/docs/DSH-VFX-RUNTIME-FRAMEWORK-20261016.md)，只补V1–V3，不重写已实现的160帧/10路由、独立碎块、客户端裁剪、可视包围或真实提交日志。

## 0. 复用核对与本轮边界

先查了现有工程及本地依赖，已有足够可复用能力：

| 来源 | 实际可复用内容 | 本单决定 |
|---|---|---|
| 项目[VfxIntegrationHarness](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/testing/VfxIntegrationHarness.java) | 场景输入、加载overlay等待、RenderFrameEvent.Post截图、异步保存、ImageIO差分、完成门禁 | 原位补元数据/配对条件及少量场景，不新造测试平台 |
| 已依赖的开源NeoForge事件API | 本地`neoforge-21.1.219-sources.jar`中RenderLevelStageEvent已提供`getModelViewMatrix/getProjectionMatrix/getCamera/getPartialTick`；RenderFrameEvent.Post已有帧末入口 | 在同一渲染帧复制实际矩阵/相机状态，复用事件，不加入相机运行库 |
| MC现有Camera、Screenshot、EntityRenderDispatcher API | 实际相机、现有`Screenshot.grab(File,String,RenderTarget,Consumer)`、观察实体及阴影开关 | 直接调用依赖API；不把MC反编译源码称为开源或复制入项目 |
| 项目VfxCameraMath、WaveVisualBounds、WaveArtworkVisibility、预算及renderer | 相机几何、保守边界、已发布lane快照、主/副绘制路径 | 继续使用；只修判据和调度，不复制另一套renderer |

暂无必须向外另找项目才能解决的缺口，因而不新增Replay/相机模组、图像分析库或运行依赖，不改正式mods.lock。例：MC的`ParticleEngine.clearParticles`在本地源码是private，不能在设计中假定它是可调用公开接口；本单不为清背景粒子引入访问改写或大平台。

当前有效成果按复核引用：JAR `07a14ceb3a061405299dcdef8f6e9f986ff3ac1a5354ecd0445033db19df8e6f`；102个目标PNG逐路径一致、466保护零变化、包中无Harness；33套JUnit XML为221/0失败/0错误/1跳过，最终日志test实际执行。18段、7重载、2单段有正常终态，证明原夹具判据完成。美术R1–R3已收口，不重复要求补旧加载屏/审计问题，也不把提供方10件GUI扩为全部手持通过。

VFX总体仍未通过：13/18段最大差分配对机位不同；部分同取整姿态配对为0或不存在；第三人称未覆盖、当前火墙锁存未触发；两个renderer存在零提交转legacy的分支。前述证据不足不等于“所有特效没画”。

## 1. V1：先证明可比较，再归因主体

### 1.1 当前最小修改点

`VfxIntegrationHarness.runSubjectCompare`当前遍历live图与后来的clean图，按较小图像宽高比较并取最大changed，没有验证配对状态。不能继续用这个最大值作为主体可见判据；尺寸不同时也不能裁成较小公共区域后算通过。

只改既有夹具的截图记录、对照流程和门禁；必要时在现有renderer提交点增加很小的显式诊断层过滤/计数入口。正常客户端未启用夹具时入口无效，不能让生产类无条件引用已从JAR排除的Harness类。不得为取证冻结服务器、改施放/实体寿命或延长追击窗口。

### 1.2 同一截图帧的实际状态记录

以下为**设计签名/数据示意，未实现**；可放在既有夹具内部，不要求建立新公共框架：

```java
record ShotState(/* sceneId, renderFrameId, resourceEpoch, target entity/cast/phase,
    camera entity/type, actual eye + orientation, copied view/projection matrices,
    effective FOV, framebuffer/viewport dimensions, render target,
    world/dimension/time/weather/render settings, diagnostic layer selection */) {}
record PairVerdict(boolean comparable, String reason /* no diff score if false */) {}
PairVerdict comparable(ShotState live, ShotState clean);
```

- 在本次world render事件复制真实矩阵值，不保留下一帧会被改写的Matrix4f引用；与帧末截图用同一`renderFrameId`关联。若world事件和截图帧不能对应，拒绝该样本。截图请求的客户端tick、渲染帧、异步保存回执分开记。
- eye/朝向不得只存3位小数；保留完整数值/float位模式或其可复查序列化，并记录actual Camera而非设计aim。options.fov不是实际投影；实际投影矩阵为准，FOV/near/far/视口另记用于解释。
- 原始图像与framebuffer/viewport尺寸必须相同，记录GUI缩放、相机模式、视角抖动/手臂/阴影、光影设置、overlay、资源代数。矩阵、相机或视口失配即`NOT_COMPARABLE`，不能通过把阈值放宽到若干度修补。
- 固定机位默认要求实际矩阵稳定相等；确因数值误差采用容差时，把小于一个像素的投影误差上限固定写入测试，并记录原值/误差。容差须能拒绝本次段2、4、6的已知机位差异。已取整相同的旧日志不能补造丢失的完整状态。

### 1.3 选择有界固定旁观方案，保留普通游玩镜头

本轮最小方案为：**同场景内固定客户端观察眼位、朝向和投影的live/clean对照**，不重新绘制整个世界两遍。相机在施放前根据预期作用范围选定，确认真实Camera及眼高稳定后锁定到场景结束；之后不追随玩家跃起、突进、波移动或target消失重新aim。服务器技能/玩家运动照常推进。波在预定视域内自然经过，超出画面则该样本无效，不能通过延长效果补拍。

普通第一/第三人称游玩画面另留证据，镜头正常跟随动作；它们是可读性/锚点审查，不与后来clean帧强行逐像素配对。两种证据模式有不同sceneId/相机模式标记，不混成同一组。

固定对照使用隔离的静态地形/固定昼夜和天气、关闭云等背景运动；这些是已有测试世界的取证设置，不改正常玩法。对照两帧的world gameTime可以自然不同，必须据实记录，不能声称世界时间冻结或完全同一世界渲染状态；只有背景状态受控且验证稳定时才可比较。

### 1.4 主贴片、碎块、背景分别归因

只新增一个作用于指定effect/wave实例的**显式诊断选择**，复用原提交代码；不是新的材质/renderer：

| 取证层 | 允许绘制 | 用途 |
|---|---|---|
| NORMAL | 正常主视觉/碎块/玩家/环境 | 普通游玩画面，不套用跨机位差分结论 |
| PRIMARY_ONLY | 指定实例/指定路由的主贴片或合法轮廓；禁止其碎块及其它VFX | 主体归因；sprite和outline计数必须分别真实记录 |
| DEBRIS_ONLY | 指定实例的现有次级碎块，不画主贴片 | 金色/重力坠击代表项的碎块归因，不能替主贴片过门禁 |
| CLEAN | 该取证组的目标主/副VFX都不画 | 静态背景参照；必须保持相同相机、环境和非VFX显示设置 |

诊断禁止提交必须返回明确的“诊断抑制”结果，不能被V2的legacy分支重新画出来。记录目标UUID/实体ID/阶段/route、层选择和实际主quad/轮廓panel/碎块调用数，排除别的实例顶替目标。用于像素对照的主贴片样本至少有该目标实际主贴片提交，不能只凭总体顶点/有实体/有碎块。

固定对照中，演员/手持物/影子及原版粒子都可能造成变化：

- 对照组若需要隐藏演员，必须在PRIMARY/DEBRIS/CLEAN三种样本中一致，并仅限诊断观察模式；用现有渲染事件/设置处理演员与阴影，finally恢复。NORMAL保留玩家和手持物，验证动作相对锚点。
- 优先选无其它目标/实体/流动纹理的安静场景；不能以“粒子设置最少”假定没有原版落地尘。存在未隔离的粒子、移动演员、其它效果或光照/环境变化时，该对照只可作观察图，不给出自动主体通过。
- 拍摄前clean和效果结束后的clean均应保留；两张背景不稳定则该组不能自动过关。不要把粒子/玩家变化当作者贴图；不能降低差分阈值来掩盖干扰。
- 保存实际主平面/lane投影覆盖范围，差分分为主体ROI与ROI外背景；ROI外显著漂移判无效。ROI内变化仍可能是遮挡/背景，不能仅凭其数值就称作者主体可见，必须看PRIMARY_ONLY原图与对应sprite/outline形状。碎块只在DEBRIS_ONLY组评价。

若某技能实在取不到受控的固定机位对照，结论为“证据不足、待补”，保留正常画面供审查；不得宣称没画，也不得取所有配对的最大changed强行通过。不要求为此实现离屏世界重渲染、第二套GPU管线或大型测试平台。

### 1.5 配对与负对照门禁

先运行`comparable`，只有有效配对才计算差分；同组有效样本全部列出，不只列最大值。无有效配对直接不通过主体门禁。300像素不是普适主体标准，应给出ROI面积、有效像素、背景残差、作者形状可见审查及采用阈值理由。

至少保留两个负例：

1. 无目标主/副效果，只有相机移动：即使变化像素很多，也必须因相机/投影失配被拒。
2. 同机位、目标PRIMARY被诊断抑制，允许代表碎块/其它动态干扰：主贴片门禁仍须失败，证明没有用碎块/背景给主贴片“代签”。

旧段2/10、6/14取整同姿态差分为0和4/12/17无同姿态配对必须保留历史事实；它们只说明旧证据不足。新证据按精确状态重新取样，不篡改旧值、不笼统否定已有金波/火墙可见成果。

## 2. V2：接管、提交与合法降级分开

### 2.1 当前缺陷及最小文件

[GoldenWaveRenderer.render](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/GoldenWaveRenderer.java)和[SkillEffectRenderer.render](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/SkillEffectRenderer.java)目前均以`!artwork.submitted()`进入drawLegacy；[ArtworkRenderer](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/ArtworkRenderer.java)在越界progress、CORE、预算0或所有lane裁剪时都可能handled=true、submitted=false。旧wave分支仅消费服务端blockedLanes，不能保证遵守客户端火墙可见快照。

具体最小补正点：上述三个文件，必要时调整[WaveArtworkVisibility](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/WaveArtworkVisibility.java)的只读快照接口和旧wave geometry调用参数；保留独立碎块方法及现有预算。[WaveVisualBounds](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/WaveVisualBounds.java)已有实现，只补针对检查，不重写。

### 2.2 设计状态表

可以扩展现有DrawResult一个明确reason，或用等价的小枚举；不要用submitted反推接管权。以下为**设计伪代码/状态，未实现**：

```text
Disposition = NOT_HANDLED | DRAWN | SUPPRESSED
Reason = NO_DECLARATION | AUTHOR_SPRITE | RESOURCE_OUTLINE | CORE_OUTLINE
       | NO_BUDGET | NO_VISIBLE_LANE | INVALID_TIME | HIDDEN | DIAGNOSTIC_FILTER
DrawResult = disposition + reason + actualSpriteQuads + actualOutlinePanels
```

| 条件 | 应有结果/动作 | 能否再进generic legacy |
|---|---|---|
| 无声明/未ready/没有作者几何，且时间/可见性合法 | NOT_HANDLED；既有兼容主路径有资格执行 | 可以，但仍先过共同生命周期、LOD及wave可见性约束 |
| 作者路由合法、资源齐全，FULL/REDUCED | 同一作者主路径按预算提交sprite，计数真实 | 不可以 |
| 合法路由缺PNG/atlas或关闭贴片资源开关 | 同一路由的合法轮廓，使用相同几何/可见lane和预算 | 不可以；不是NO_DECLARATION |
| CORE/pressure | 同一路由内部明确提交合法轮廓；不是直接返回0后等generic legacy补画 | 不可以 |
| 主预算0、所有lane被挡/UNKNOWN/无支撑、诊断禁止 | SUPPRESSED，0提交；部分lane时只画允许部分 | 不可以 |
| progress非有限、<0或>=1、实体已移除、HIDDEN | 所有主/轮廓及相应副效果0提交 | 不可以 |

FRAME.beginArtwork每个接管效果仅一次，轮廓/贴片各走既有准入；预算拒绝不换另一种路径重新申请。DrawResult里的spriteQuads不能把`plane(sprite=null)`提交的轮廓panel算成sprite；当前plane返回通用计数，应按实际提交分支准确归类。提交、采样和跳过原因日志不能混用。

普通效果在统一合法时间/LOD检查后选择一次主路径，再运行既有独立碎块；资源轮廓不应自动删除合法FULL/REDUCED碎块，CORE/HIDDEN仍为0碎块。时间越界不能因副方法把progress夹为0而画出早期碎块。主预算耗尽也不能借副效果替代主体通过。

### 2.3 所有wave主/轮廓/兼容路径共享同一可见约束

一次读取已发布lane快照，记录原始lane ID与支撑高度；主贴片、缺图轮廓、CORE轮廓及允许的旧兼容wave都消费它。黄金波的服务器blocked位为硬约束，火墙叠加客户端drawable/UNKNOWN/obstructed判断；无合法支撑不画。不要在不同分支各查一次得到矛盾结果。

不能只给作者路径过滤，随后旧整面/box绕回。legacy的buildWave/outline若不能表达某lane的同样约束，就复用现有逐lane轮廓提交原语或最小增加lane筛选参数；不另造火墙renderer，不复制完整帧到每段。全lane隐藏时所有主/outline/legacy提交均为0。

过滤后的list序号不能冒充原始0..15 lane ID；混合服务器mask和客户端mask时需保留原ID与UV。资源重载和新实体使用正确维度/资源代数快照，不能用旧“曾提交过”集合证明当前恢复。

### 2.4 必要针对断言

扩展现有VFX测试，不新增大平台：

- 作者路由、全lane遮挡或全UNKNOWN：sprite/outline/legacy均0；旧绘制入口调用计数0。
- 部分lane可见：只提交对应原ID/UV/支撑，包含混合mask负例；其它lane不被压缩索引误开。
- 预算0、progress=-ε/1/NaN、移除/HIDDEN：不回legacy；普通副碎块也不越过生命周期。
- 缺PNG/atlas、CORE分别在至少一个可见lane/普通效果上产生真正轮廓，证明没有机械替换handled后使CORE消失；全隐藏时同样0。
- 正常主sprite只提交一次，缺资源日志、outline计数与实际路径一致；独立碎块仍遵守FULL/REDUCED/CORE上限及全帧预算。
- 同一遮挡快照用于主贴片、轮廓与兼容分支；源码/测试证明足够时才删旧错误分支，不通过删场景解除门禁。

## 3. V3：补回外部动作画面和真正火墙锁存

### 3.1 有界第三人称/外部观察

现18段的8–15是夜间第一人称，不能再称第三人称/环绕。复用现有观察实体/Camera与VfxCameraMath，在独立诊断场景设置固定斜侧外部视角，确认actual eye/head yaw/投影与目标锚点；不要影响普通游玩镜头。

最小覆盖：八技各一次外部动作样本，追击仍在真实允许窗口，覆盖十路由的角色相对方向、脚下落点、竖直/水平平面和结束；与V1固定观察场景合并时保留NORMAL演员可见样本，不能拿隐藏演员的主体探针图当动作验收。环绕只在至少一个波前及一个跟随斩击加有限角度/时长样本，移动镜头样本不进入固定机位差分。

白天/夜间、第一/第三人称按实际拍摄标记；不能修改名称便宣布覆盖。普通视角若主体在近裁面外不可读，给出具体画面/锚点，不修改PNG或靠billboard掩盖。

### 3.2 稳定支撑→真实碰撞→锁存场景

继续使用WaveArtworkVisibility；新增或扩展**只读诊断**，按同一wave UUID/维度/资源代数、原lane ID、客户端tick记录：上一/当前有效支撑、held、groundY、前后lane位置、实际扫掠段与COLLIDER命中位置/形状、UNKNOWN原因、obstructed、最终drawable。聚合counts只作摘要，不能替代状态链。

当前solveLane先查当前ground；失败会设置UNKNOWN并清held，之后才可能做碰撞扫掠。因此“把3格高实墙放在当前落点”可能只得到无支撑/UNKNOWN，不能证明锁存。当前0.5步长的点/薄盒采样也不能先验保证命中任意薄Collider。本单不预判它已经造成实机错误；下述独立碰撞判据若命中而求解器漏掉，再由DSH最小修客户端连续扫掠/历史保留，不改服务器。

场景按以下有限步骤实现，不靠强制设置obstructed或仅调用debug标志：

1. 平整已加载支撑面，正常施放一条火墙；选择中心线之外的侧lane，先观察到该**同一lane**两次有效支撑及held成立、unknown=false、obstructed=false。
2. 侧lane路径上放置有实际COLLIDER形状的窄障碍，保留前后采样点有效地面，中央传播线留空，避免服务器中央撞墙令整波先消失。可用连接的玻璃板/栅栏等现有方块构成侧向短墙；具体位置从实际lane轨迹选取，不把硬编码x≤-2、z=6当碰撞证明。
3. 记录前后支撑之间的真实扫掠与独立形状相交结果，证明它确实跨过障碍。只有GROUND有效、held成立且真实碰撞才期望obstructed由false变true；支撑MISS或预算不足另算UNKNOWN，不准代替这一步。若薄形状被步进采样漏掉，可在该helper复用原版连续ClipContext/现有VoxelShape线段相交，保持已有查询预算和不加载区块边界。
4. 让同一波按原速度越过侧墙，在其仍存活且当前支撑重新有效时，obstructed仍true、该lane主sprite/轮廓/legacy仍0；邻近未碰撞lane正常提交。配套外部画面能看到墙及前沿，不能只拍墙把效果完全挡住的视角。
5. 同一波的锁存不因移除墙体即时取消；待正常结束，在隔离准备阶段移除障碍，再正常新施放，证明新wave不继承锁存并恢复可见。另做资源代数变化/恢复后新波正常求解，记录新UUID/epoch；不能要求短寿命旧波在加载结束后仍活着，更不能延寿。

未知负例单独做：缺支撑/未加载/预算不足触发UNKNOWN，obstructed必须仍false；恢复支撑或在合法新波中重新求解后恢复，不制造永久撞墙。资源恢复不是“把真实服务器mask清零”；黄金波DATA_BLOCKED仍保持权威。

如不能在自然寿命内得到上述状态链，场景按失败/缺证上报并修定位/客户端helper，不能删掉锁存断言或改服务器射程、速度、碰撞、伤害、FP和寿命。纯客户端遮挡与现有服务器命中的差异据实单列，不借视觉补正改变平衡。

### 3.3 保留已有地形和视锥边界

补正仅复用已有WaveVisualBounds与lane支撑：保留坡面、台阶、悬空MISS、只有3.5格火墙上部进入画面的代表样本/既有针对测试；缺哪项就补该项，不重跑全部组合。角点必须在保守可视框内，公共实体不引用客户端类，不通过全局noCulling/无限AABB绕过视锥。

新框架不重复要求实现已经存在的碎块/可视框/正常缺图重载，也不把旧历史运行的obstructed样本代替本轮当前源码回归。

## 4. 有限实施顺序、运行矩阵与交付

| 顺序 | DSH最小工作 | 验收 |
|---|---|---|
| 1 | V2结果调度、CORE/缺图轮廓与共享可见性；生命周期/诊断抑制共用门禁 | 上述0提交不复活及合法轮廓针对断言；保留原预算/阶段测试 |
| 2 | V1 ShotState、实际矩阵/视口快照、固定观察/分层探针与严格配对 | 已知旧机位失配、移动相机无效果、只有碎块三种负例不得过主贴片门禁；无有效配对不能取最大值 |
| 3 | V3有限外部机位和逐lane真实状态链 | 真正碰撞/锁存/越墙隐藏/新波恢复，不以UNKNOWN代替；保留地形/顶部视锥样本 |
| 4 | 集中针对检查，再串行隔离运行、逐图审查 | 构图、层归因、实际提交、有效原图、落盘/完成标记/实际退出均对应同一场景 |

只扩展现有VfxCameraMathTest、VfxPipelineTest、WaveVisualBoundsTest及必要已有renderer/资源回归。纯调度和配对判据可以用小数据样本验证，不以模拟sprite表代替实机图集/画面。源签名、selector和日志字段若新增，由DSH在实际回执列出；本文不虚构它们已存在。

建议有界运行矩阵：

- 主体/外部观察：八技的固定旁观自然施放，覆盖十路由；对复核中问题突出的destined_death、水鸟、bloodboon补夜间样本。NORMAL与PRIMARY_ONLY分组，至少金色/重力坠击各一份DEBRIS_ONLY；不必再做八技乘所有地形。
- 调度：正常sprite、缺PNG/atlas轮廓、CORE、全lane隐藏/UNKNOWN、预算0/越界；复用已完成7重载证据，只重跑因V2触及的缺图/CORE/裁剪/恢复代表场景，不无条件重跑全部18+7+2。
- 锁存：一条侧lane自然碰撞链、一条UNKNOWN负例、新波及重载恢复；加已有坡/台阶/悬空/顶部视锥缺失项。
- 普通游玩：保留原第一人称与补充外部/第三人称动作图或短片，明确这些画面不等同受控差分；相机设置、诊断抑制、演员/阴影/资源包选择在结束/异常时恢复。

不得对动态普通游玩图继续使用“同机位”标签。受控对照仍需实际画面审查：每条route是否看得出作者主形、方向/尺度、阶段/收尾，不能用“像素多”代替，也不因一次0差分断言产品绝对不显示。

具体构建/启动复用当前build.gradle的隔离入口；DSH写完受影响实现后集中运行，一次一个构建/客户端，确认旧进程收束再下一项。场景/截图/重载均有上限，overlay未消失、截图未落盘、状态失配或缺完成标记必须非零失败，不能先退出0再在报告中加一句警告。

交付对[VFX运行回执](D:/codexout/mcmod/docs/VFX-RUNTIME-CLOSURE-REPORT-20261016.md)的补正或独立V1–V3回执：修改文件、真实命令/退出、原始截图与ShotState、全部配对判定、主/副/背景归因、逐lane状态链、实际提交和未验项；重记JAR/源码哈希及102目标/466保护、不含Harness的包检查。旧日志与无效对照标历史，不删除证据。

本轮只有静态接口核对及文档交付，没有新增机器/视觉PASS。独立JAR安装、专服/多人、迟加入/极端延迟、光影、PCL导入、探索奖励与人类最终观感仍按实际证据另验。保留用户选版、黑剑刃口朝外和弩炮校准；不重画160帧，不实施其它队列，不触碰搁置的新魔法/祷告。
