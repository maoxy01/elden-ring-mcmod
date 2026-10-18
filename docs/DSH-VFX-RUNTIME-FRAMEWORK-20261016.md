# 八战技VFX运行收口：DSH实施框架

2026-10-16。**本文件是设计与验收合同，不是实现回执。** 战斗部门只核对接口、编写框架；DSH在统筹安排后实现和验证。本轮仅修改本文与执行索引，没有修改Java、脚本、资源或素材，没有构建、运行游戏/Harness或启动DSH。

## 先决条件与最新基线

先按[美术补验统筹复核](D:/codexout/mcmod/docs/CODEX-ART-ACCEPTANCE-CLOSURE-REVIEW-20261016.md)完成R1–R3，再启动本单，不把补正并入VFX代码：

- A/B机器场景通过。C已有实际Spartan、10项GUI、长弓四状态及回退/恢复绘制日志，但两张关键回退/恢复截图是Mojang加载界面，C视觉未收口。
- 10项提供方只有GUI均有证据，只有长弓另有第一人称右手；本版长弓为SimpleBakedModel单pass，不宣称其余9件手持或多层/custom loader通过。
- 当前登记JAR为`231d9cb257a245d960c6d56cc2cceb8c15fec154ef66fda268d49f048a163eb2`；统筹独立核对102目标PNG路径/内容一致、466保护文件无变化。DSH执行前仍重新读磁盘，不把历史cb674/e4/7d23当新产物。
- `test-postexcl-20261016.log`的test是UP-TO-DATE，复用207项、0失败、1跳过的有效结果；不是排除后重新执行全套。
- R1补加载overlay消失后真实画面与异步截图落盘；R2只读审计逐目标路径、缺失/差异非零失败及负例；R3校准上述验证范围与措辞。VFX重载截图也必须遵守R1。

继续复用[当前VFX工单](D:/codexout/mcmod/docs/VFX-RUNTIME-CLOSURE-WORK-ORDER-20261016.md)及[既有十路由技术合同](D:/codexout/mcmod/docs/DSH-VFX-IMPLEMENTATION-CONTRACT-20261014.md)。160张帧、10个ready路由、ArtworkRenderer、既有预算和服务端阶段均已存在，不重造渲染器，不重画素材，不改变FP/命中/伤害/追击规则。下面只补当前真实缺口。

## 1. 已核对的当前接口与事实

| 文件/接口 | 当前实际行为 | 本单最小职责 |
|---|---|---|
| [VfxIntegrationHarness](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/testing/VfxIntegrationHarness.java) | 18段、每段120客户端tick；服务器输入使用level.gameTime；环绕位置sin/cos配`180+theta`，只设置实体yaw；最后一段仅强制missing开关 | 修观察相机和可见门禁；补真实资源包缺图/恢复场景；保留有界运行及真实输入窗口 |
| [SkillEffectRenderer](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/SkillEffectRenderer.java) | 跟随owner插值，外层应用一次`-yaw`；Artwork成功立即return，后面碎块不可达 | 主视觉选择与必要碎块分开，成功主贴片不再跳过碎块；不恢复旧主box |
| [ArtworkRenderer](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/ArtworkRenderer.java) | `draw(...)->boolean`；帧日志在预算/逐lane地形裁剪前；wave最多16段；ground短射线；没有LOD参数 | 返回明确的接管/提交结果；实际提交后记录；接收LOD；加入纯客户端火墙可见lane过滤 |
| [GoldenWaveRenderer](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/GoldenWaveRenderer.java) | 外层应用一次`atan2(dirX,dirZ)`旋转；使用同步进度/半径/距离/mask | 复用相同lane几何绘制，客户端可视包围覆盖实际贴片与地形范围 |
| [GoldenWaveEntity](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/entity/skill/GoldenWaveEntity.java) | 只有非flame调用`updateBlockedLanes`；flame仍是中央碰撞与每目标视线检查；culling Y扩张2 | 本单不改其命中/阻挡/推进规则；不得把客户端火墙遮挡回写DATA_BLOCKED |
| [FxArtwork](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/effects/FxArtwork.java) | 纯几何；`frame(p,N)`直接映射1..64帧；lane含center/width/forward/UV，flame无lag | 继续为几何来源，不回退16语义步重采样 |
| [SkillEffectEntity](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/entity/skill/SkillEffectEntity.java) | START/DURATION同步；`progress=(level.gameTime-START+partialTick)/duration`；寿命及owner/cast有效性由服务器维护 | 记录首次同步与自然可见窗口，不另起客户端战技时间线 |
| [SkillSlashSpriteRenderer](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/SkillSlashSpriteRenderer.java)、[PresentationRoutes](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/PresentationRoutes.java) | ready路由合法但缺PNG只警告；任一帧没入atlas则整条路由loaded=false；非法manifest返回上一份有效Data；apply仍更新revision | 保持合法缺资源与非法配置区别；按重载代数重新查图集，不持有旧sprite；不把revision增加当配置修复成功 |
| [FxRenderBudget](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/effects/FxRenderBudget.java)、[VfxFrameBudget](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/effects/VfxFrameBudget.java) | AFTER_SKY重置；主提交预算30000顶点；候选>40时pressure；次级碎块全局32调用 | 复用现有预算，不能重复beginArtwork或绕开准入；碎块预算与顶点口径分别说明 |

当前工单中的相机、提前return、flame mask及Y包围缺口均仍存在，未发现这些要求已被当前源码修复。已存在的gameTime、直接progress采样、同步groundState、10路由及缺PNG声明保留逻辑，不再作为“需要重新实现”的工作。

## 2. 单位、变换与生命周期不变量

- 坐标/尺寸/射线长度是世界格；时间为服务端level.gameTime的tick，渲染partialTick仅插值；yaw/pitch/roll设计值为度，只有调用`rotation(float)`等明确弧度API时转换。
- normalized anchor和UV在[0,1]。平面局部边界：`left=-anchorU*width`，`bottom=(anchorV-1)*height`，`right=left+width`，`top=bottom+height`。不把非居中锚点改成居中。
- 实体渲染器独占外层世界平移/朝向：普通效果跟随owner插值并应用`-ownerYaw`一次；波渲染器只应用方向旋转一次。Artwork仅做作者offset、水平面X旋转90°及phase roll/scale；不再次应用实体yaw，不改billboard来掩盖错误。PNG向下映射水平局部-Z，贴图仍白色顶点、作者alpha、既有深度测试。
- 追击只调用现有`SkillCasting.request`并要求真实`followupOpen(now)`。原runtime在stage切换前tick；咒血`elapsed==4`的延烧仍在切换前执行；GOLD_IMPACT/GRAVITY_IMPACT仍由实际landingImpact触发。不得提前到3、增加持续时间、客户端造追击或命中。
- 结束/取消/死亡/换维度时跟随实体仍按owner/cast生命周期消失。独立落地点/延烧不是新持续伤害源；资源失败与LOD不改变实体寿命、波传播、FP扣费、目标集合或伤害事件数。

## 3. 相机与画面门禁

修改范围只在既有[VfxIntegrationHarness](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/testing/VfxIntegrationHarness.java)，可抽一个夹具内纯数学方法；不增加正常玩家相机接管。

**设计伪代码，未写入src：**

```text
d = targetWorld - effectiveCameraEyeWorld
yawDegrees   = degrees(atan2(-d.x, d.z))
pitchDegrees = -degrees(atan2(d.y, hypot(d.x, d.z)))
```

目标使用本次实体/实际落点/波前可见范围中心，不永久盯施法原点。用实际相机眼位而非ArmorStand脚位；目标重合则夹具报错，竖直方向保持有限yaw、pitch夹到合法范围，禁止NaN。

本地引擎`neoforge-21.1.219-sources.jar`已核对：`Camera.setup`读取`getViewYRot`并使用插值eyeHeight；`LivingEntity.getViewYRot`读取`yHeadRot/yHeadRotO`。因此ArmorStand夹具除了实体`setYRot/setXRot`及旧值，还要同步头部yaw与旧值，初始化/等待eyeHeight稳定；仅改`180-theta`仍不足。最终以渲染帧中真实Camera position/rotation验证，不能只检查设置值。

构图门禁在相机稳定后、实际渲染帧完成时执行：目标在相机前方，实际特效可视包围投影与屏幕有非零交集，记录near裁剪、屏幕范围及提交数。整块特效需要可读的全景样本；第一人称允许近身裁剪，但另有可见主体样本。负例相机背向目标必须失败，不能把“实体存在/atlas已加载”当可见证明。必须查看真实截图/短片；投影相交不证明没被地形或角色遮住。

资源重载后的截图必须等overlay消失、目标实体/场景恢复并完成有效渲染帧；截图异步保存完成后再继续/退出，等待有上限。复用R1修订后的门禁，不再产出Mojang加载图作为回退证明。

## 4. 主贴片与必要碎块：一次变换、两条职责

建议最小设计签名（名称可沿用现有风格；**均未实现**）：

```java
record DrawResult(boolean handled, int spriteQuads, int outlinePanels) {}
static DrawResult ArtworkRenderer.draw(/* 原有参数 */, VfxBudget.Lod lod);
private void drawSecondaryDebris(SkillEffectEntity effect, float progress,
        VfxBudget.Lod lod, PoseStack pose, MultiBufferSource buffers);
```

`handled=false`才进入既有未配置/legacy主视觉分支；`handled=true`即使因预算/地形提交0，也不能再叠旧box。progress不在[0,1)或实体移除时主/副都不画。选好主路径后在同一个外层pose内调用一次必要碎块，再统一pop；不要把return挪后便同时画新主贴片和旧主盒体。

直接复用已有`SkillEffectMesh.debris(kind,radius,progress)`和effect.groundState、现有块渲染循环，不能为了碎块调用`FxGeometryCache.get`重建一整套boxes/strips。只支持当前已定义的GOLD_IMPACT、GRAVITY_IMPACT、GRAVITY_PULL，不向全部战技批量添加装饰。

| LOD | 主视觉 | 次级碎块 |
|---|---|---|
| FULL，距离≤16格 | 一张完整作者平面；波为最多16个不重叠UV片段 | 每效果≤12，且全帧共享32次块渲染准入 |
| REDUCED，16–48格 | 继续保持一张/必要lane，不重复完整波图 | 每效果≤6，共享同一32预算 |
| CORE，48–128格，或pressure | 沿既有轮廓逻辑保留可读主轮廓；波按开通lane轮廓；仍受panels预算 | 0，不因主贴片成功旁路CORE |
| HIDDEN，超过128格 | 0；不得再查地形或提交顶点 | 0 |

当前Artwork没有LOD参数，应明确传入，而不是依注释假设CORE已执行。主贴片/轮廓每quad记4顶点，`beginArtwork`每次接管只调用一次。沿用主提交30000与全帧32碎块的两个预算：当前`renderSingleBlock`可能因groundState模型不同提交超过24顶点，不能把一次块调用虚算为固定24，或宣称现有主预算已统计全部GPU顶点。若DSH要报告“主副合计≤30000”，必须补真实块顶点计数/可靠上界准入；否则分别报告主顶点和块调用上限，不扩大限额。

## 5. 火墙：仅客户端表现裁剪

**本单选择纯表现方案，不修改GoldenWaveEntity.serverTick/updateBlockedLanes/hitAround/WaveStep。** 黄金波仍以服务端blockedLanes为硬约束；火墙不能凭mask通常为0就称服务端逐lane阻挡已实现。视觉遮挡不回写服务器，不新加网络字段，不修改伤害过滤。

复用`FxArtwork.lanes`、16个lane和原UV，不重新分块美术。方向归一化`d=(dirX,dirZ)`，横向`r=(dirZ,-dirX)`；对每个lane：

```text
centerXZ = interpolatedBaseXZ + r * lane.center + d * lane.forward
lane.forward = 0 for flame; golden uses existing -WaveFront.laneLag
worldY = validGroundHitY + author offsets/transformed local corner Y
```

不可把world lateral当世界X，不能再旋转已经转换到世界的中心。外层pose仍由波渲染器旋转；用于查询的world center和用于绘制的local lateral/forward须从同一lane数据得出。

建议客户端小型`WaveArtworkVisibility`辅助，只做地形/可见lane求解，不提交顶点、不拥有战技时间或命中。设计输出为`SupportedLane(index, lane, groundY)`清单，供同一个ArtworkRenderer逐片绘制与边界检查。规则固定为：

1. 使用已加载客户端区块、COLLIDER、Fluid.NONE；保留现有向上1.5/向下2.5格地面射线窗口。检查查询经过区块，不能因端点已加载便隐式加载中途区块。MISS、未加载、从实心内部起射/非有效支撑面均跳过该lane，不造STONE落点，不跨虚空找远处地面。
2. 每客户端tick最多更新一次每lane支撑及前后扫掠结果，渲染帧只插值/消费；缓存按真实wave对象/维度和资源代数隔离。前一有效lane支撑到当前支撑的`+0.5格`COLLIDER扫掠用于火墙表现障碍，遵循实际VoxelShape，不用方块是否非空气代替。挂上地形不表示传播无墙，坡/台阶必须同时通过支撑和扫掠检查；不强制越过所有台阶。
3. 障碍命中的lane在**本波**客户端可见mask中锁存，后续隐藏，避免跨墙后闪回。它与服务端mask严格分离；本波中移除墙体不追补已隐去片段，下一波重新求解。缺地形只记UNKNOWN，不把未知当永久撞墙；再次加载后可重新求支撑，但先核对未观察路径，不能从墙后凭空恢复。
4. 第一次同步没有历史时，用`baseXZ-d*visualTravel`恢复发射XZ，按有限步长验证到当前lane的路径；每lane冷启动累计至多8段、每段≤1格，每客户端tick最多推进一段，并保持每lane最多一次支撑与一次扫掠查询（每波每tick≤32次clip）。尚未追上当前前沿时先不画；历史过长/无法重建则保守跳过并记录UNKNOWN_HISTORY，不用长距离无限扫描或制造服务端阻挡。该上限是拟议的客户端诊断/性能边界，不是改变波射程；DSH可用等价更小实现，但须保留有界性与未知不穿墙原则。
5. 缓存随实体移除、维度切换和资源代数变化清理；新波不继承旧mask。重载中旧波重新初始化失败可保守不画，不能复活已经移除实体。所有lane隐藏时主路径仍算接管，不能回退为未裁剪的旧整面/box。

纯表现遮挡不能证明服务器命中语义已改变：火墙侧lane的服务器规则仍可能与锁存视觉不同。发现“隐藏lane仍发生既有命中”的例子要单列真实差异交统筹，不在本单暗改服务器使测试好看。迟加入/严重延迟下的历史重建也单列边界，不用无障碍暖启动宣称全部网络条件通过。

## 6. 实际可视包围与专服边界

本地EntityRenderer源码已确认`shouldRender(entity,Frustum,camX,camY,camZ)`在距离检查后使用实体culling box（额外inflate 0.5）；Frustum接收世界AABB。**不能先调用super得到false，再指望扩大框补回来。** 不改用于hitAround等逻辑的实体真实boundingBox。

优先只在客户端`GoldenWaveRenderer.shouldRender`中使用保守可视AABB，保留实体距离/noCulling规则；设计助手`visualBounds(wave,art)`与lane几何共用纯角点计算，不引入另一套renderer。公共GoldenWaveEntity不得引用Minecraft、PresentationRoutes、Frustum或其他客户端类；专服加载无需任何美术状态。

包围至少联合：旧fallback culling box、前/当前base位置、半径与lag范围、每个lane平面的作者offset/anchor/roll与所有相关scale、地面支撑射线可能高度。采用支撑窗口上下界推算即可保守裁剪，无需在shouldRender里重复射线或强制加载区块。

当前flame artwork高3.5格、anchorV=0.90625、offsetY=0、roll=0，因此相对支撑面实际bottom=-0.328125、top=3.171875；并不是上下各1.75，也不是从脚底向上完整3.5。支撑窗口使当前base相对Y覆盖至少[-2.828125,+4.671875]，再联合前后baseY和小浮点余量。现有Y扩张2加引擎0.5仍不足覆盖上部。黄金水平面的height映射前后Z范围，也须纳入yaw后的世界角点，不能只用radius当完整平面宽度。

门禁包括只有火墙顶部进入画面、底部出画、坡面升降、侧向镜头、near plane与资源重载后的新尺寸。AABB有限且包含实际提交四角/所有lane；不得用无限AABB或全局noCulling掩盖问题。其余普通效果当前已有Y扩张6；仅在角点测试确实证明不足时再补，不顺手重写全部实体。

## 7. 资源、帧可达性与真实提交证据

| 情况 | 保留/降级合同 | 必须证明 |
|---|---|---|
| 合法ready路由、全部PNG/atlas有效 | 作者帧绘制；floor(progress*N)，不经16语义步 | 声明资源→atlas实际匹配ID→真实顶点提交→可见画面分层记录 |
| 合法路由、某PNG缺失/某sprite未缝入atlas | 当前最小策略保留整条ready声明、该路由整体轮廓；其它合法路由照常 | 不能将过滤后压缩的frames列表按原索引播放；当前loaded全量检查应保留 |
| 非法JSON/状态/尺寸/帧数等 | 当前loader保留上一份有效Data；首次无有效Data时为空声明并走原兼容路径 | 不把它当合法缺PNG；保留原错误日志，不强行ready |
| 合法资源恢复并重载 | 重新取本代atlas/sprite与几何，轮廓恢复为作者帧 | revision/cache失效、overlay消失后的实际顶点与有效截图 |
| 预算为0、全部lane未知/遮挡、HIDDEN或生命周期已结束 | 不提交；相应原因明确 | 不是missing-art，也不能记录atlas/outline“已绘制” |

真实缺资源用隔离资源包：合法JSON指向不存在PNG，或合法atlas filter排除目标sprite；同时保留一条对照路由正常。另做非法JSON→保留旧配置、恢复合法→重新加载的独立场景。坏包不进入正式资源/锁，退出前恢复选择。强制`vfxSmokeMissing`可留作快速诊断，不能替代上述路径；不要为单纯缺一帧引入新网络包或重绘素材。

现有`ArtworkRenderer.recordFrame`早于预算与地形，SEEN又跨实例/phase/重载累计，所以旧“实际帧”集合不能证明每次施放提交了这些帧。建议将plane返回实际sprite/outline quad数，在成功提交后记录，去重键至少含资源代数、实体实例、phase、route、frame、atlas/outline；记录LOD、提交数、开通/未知lane数及跳过原因。加载、时间采样、顶点提交分别使用不同标记，截图才说明可见/构图，顶点提交本身不是GPU可见证明。

先记录服务器spawn/start/duration/首个progress/移除时刻，以及客户端首次实体+metadata可用、render tick/partialTick/progress/frame/提交数。SkillEffectEntity有START/DURATION；GoldenWave同步的是progress等量，服务器第一步已推进并可能很快discard，不能从静态公式猜15帧缺的是哪一帧。

`FxArtwork.frame`的1..64帧逻辑可达已经有单测，保留；**逻辑可达不等于网络到达后每次自然施放都能显示全部16帧。** 首包晚到、低FPS和预算都可合法丢失采样窗口。若发现首次插值端点/metadata初始化错，修本地插值或必要视觉同步事实；不得把首次收到的中段进度硬置0、重播已过帧、延长服务器寿命、在discard后补尾帧，或为凑16/16改变FP/战技时间。报告应给出本次生命周期实际可达集合、真实提交集合及缺失原因；人为慢采样只证明映射，不伪装自然施放。

## 8. 有限实施顺序与针对检查

DSH先完成R1–R3，再按以下五步集中实现后验证；发现新失败只重跑受影响项，不扩到装备/Combat Roll/模组队列：

1. 修夹具实际眼位/头yaw和构图门禁、完成/失败/截图落盘门禁；移动提交日志到真实准入之后。先确保不会再拍空地/加载屏、预算0却报已绘制。
2. 调整Artwork结果及LOD参数，抽出既有碎块调用；主路径只选一次、次级只调用一次。
3. 客户端lane支撑/障碍及有限缓存；共享角点推导客户端保守可视框；服务端逻辑保持不动。
4. 补合法缺PNG/atlas、非法JSON与恢复场景，按实例和资源代数记录帧/提交；核对初次同步和生命周期，不预先承诺16/16自然播放。
5. 集中必要纯逻辑测试、隔离场景和逐图审查，核对产物及纯运行夹具不入包，交付实际证据与未验项。

复用现有FxArtworkTest、FxSlashRouteTest、VfxPipelineTest、SkillEffectMeshTest、RemembranceSkillsTest；只补有意义的回归：

- 四方向/竖直目标/零向量的相机方向与投影正反例，实际头yaw与eyeHeight一致。
- 主接管后不画旧主box；FULL/REDUCED碎块及CORE/HIDDEN零碎块；预算耗尽零提交、日志不得假绿；保留既有预算上限。
- rotated lane世界坐标/UV无重叠、16位mask与UNKNOWN、支撑MISS/未加载不造落点、缓存跨entity/重载不串用。
- 以实际角点验证3.5格火墙、非居中anchor、地形上下界和前后插值均在可视AABB中。
- 缺一PNG/atlas不改合法声明、不压缩帧索引；非法JSON保留上一份，首次非法为空，恢复查新atlas。
- 既有阶段/FP/命中集合/每目标一次、elapsed==4及取消/死亡/换维度回归不变。渲染测试不能替代这些服务端边界。

以下命令仅为DSH执行模板，本轮未运行；精确selector参数若新增须记录在其回执，不谎称当前已有：

```powershell
Set-Location -LiteralPath D:/codexout/mcmod
$env:JAVA_HOME='D:/JDK21'
.\gradlew.bat compileJava test --tests '*FxArtworkTest' --tests '*FxSlashRouteTest' --tests '*VfxPipelineTest' --tests '*SkillEffectMeshTest' --tests '*RemembranceSkillsTest' --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
.\gradlew.bat runClient -PvfxSmoke --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

既有入口隔离目录是`D:/codexout/mcmod/build/vfxSmoke`。可给原夹具加小型场景过滤/场景清单以避免重复全18段；不创建第二套渲染器或另一个正常玩法入口。

## 9. 最小隔离验收矩阵与交付

| 组 | 有界覆盖 | 通过/证据 |
|---|---|---|
| 八技/十路由 | 每技白天第一人称、夜间第三人称；真实允许窗口产生追击/落地/延烧 | 本次cast/phase/FP输入、对应十路由素材、实际提交与可见主体；不只报截图数量 |
| 波方向/地形 | 黄金/火墙代表性四方向；平地、斜坡、半砖/台阶、悬空边缘、侧部实墙/部分墙体 | 世界挂点、lane/UV、遮挡/未知理由，无假落点/穿墙补片；服务器命中差异单列 |
| 视锥/距离 | 火墙顶部可见底部出屏、侧镜头；16/48/128边界及>40候选pressure代表场景 | 实际角点和可视框、FULL/REDUCED/CORE/HIDDEN、主/碎块预算；不以关闭culling通过 |
| 真降级/恢复 | 一条普通斩击及一条波路由：缺PNG、缺atlas、恢复；另测非法JSON/首次非法 | 加载/采样/提交/画面分开；有效轮廓与恢复截图，overlay退出、截图落盘，完成门禁与退出码 |
| 生命周期 | 正常结束、取消、死亡、换维度；等待到最长合法子效果结束 | 清理实体及缓存，无重播尾帧/新命中/额外FP；两类实体的跟随/固定原点差异据实判断 |

为避免矩阵无限膨胀，方向/地形/LOD组合取上述代表正反例，不要求八技乘所有组合；同一运行内场景有上限，所有构建与客户端一次一个并追到终态。构图负例、预算0、缺完成标记应明确失败；保存完整日志、各阶段有效截图或短片、实际退出和源码/JAR哈希。UP-TO-DATE结果按复用描述，不为改措辞强制重跑全套。

输出`D:/codexout/mcmod/docs/VFX-RUNTIME-CLOSURE-REPORT-20261016.md`，逐项列修改、实际命令、机器与视觉结果、真正提交帧集合和缺失原因。保留160帧、用户选版/黑剑刃口朝外、弩炮覆盖、正式依赖锁及保护文件；素材确需修改先给具体画面，由统筹收束战斗后单独排美术。本单不自动启动美术或DSH。

本轮仅静态源码/本地引擎核对与文档交付，未新增VFX运行证据。独立JAR安装、专服实际启动、多人/迟加入/极端延迟、光影、人类物理输入、PCL导入、最终观感及整包生存目标均不由本框架宣称通过；客户端方案要通过common侧无客户端加载依赖检查后，后续再给对应实机证据。
