# DSH 装备 2D 实施合同

2026-10-15，审查修订版。Codex 负责框架、接口与审查，DSH 负责具体实现。本文件已依据第一批代码更新；§2是当前缺陷，§3–9是目标合同，§10–12是修复与成功路径验收要求，不代表已修复或实机通过。本轮只读源码并改文档，不改 Java、Gradle、正式资源或美术，不启动构建、游戏、Harness或DSH。

最新依据：[审查与接口裁决](CODEX-EQUIPMENT-2D-REVIEW-20261015.md)、[DSH审查交接](DSH-EQUIPMENT-2D-TO-CODEX-20261015.md)、[DSH第一批报告](DSH-EQUIPMENT-2D-IMPLEMENTATION-REPORT-20261015.md)。报告是待核对证据，不能覆盖源码事实。采用**修订A：旧变换及首次顶点之前窄范围接管、单次变换、完整回退**，不是机械移动TAIL注入点。

## 1. 范围与依据

最终范围固定为 **42 项：33 类普通装备、8 件追忆武器、test_flask**。六类代表只是首批集成，不把全套交付缩减为六件。沿用现有 41 个装备注册 ID 和药瓶 ID，不新增替代 Item。

输入优先级：

1. [全套美术交接](EQUIPMENT-2D-FULL-HANDOFF-20261015.md)。
2. [实际选择与逐件参数](../art/equipment-2d/full/equipment-42.json)：28 项自有精灵、14 项提供方引用；尺寸/握点/状态以此为准，旧六件样板不覆盖新表。
3. [自有发布白名单](../art/equipment-2d/full/publishable-assets.json)：94 张 PNG，38 张持用/状态图与56张独立 GUI 图。
4. [提供方完整定义证据](../art/equipment-reuse/research-only-full/evidence.json)：14 项、43 个模型状态；包含父模型、附加层和 predicate，不等于43种不同纹理。
5. [总映射](../art/equipment-reuse/equipment-mapping-42.json) 中 selectedProvider/fullAssetManifest 是当前选择，candidate 是历史候选。

素材交付、代码接入、运行验证、人类观感验收分别记录。当前提供方记录为 installed=null、runtimeRegistryVerified=false，不能据发布包取证声称运行依赖已经安装。

## 2. 当前代码的真实边界

本轮读取正式清单仍为42项pending、ready=0。DSH已新增schema v2、准入生成器、equipment2d客户端类和mixin；旧版“external_model直接拒绝、没有别名类”的描述作废。已有代码不等于可运行完成，当前至少有以下独立问题：

| 入口 | 当前源码事实 | DSH 修复要求 |
|---|---|---|
| EquipmentRouteSnapshot | 局部 Display display 始终为null；displayOf仅定义未调用，ready必被拒绝 | 先打通有效display解析→Entry→候选准入→实际消费；不能仅填JSON |
| gradle/equipment-2d.gradle | 已有白名单/哈希/三路线校验；把acceptance_pending等通通作为硬阻断 | 区分技术失败、不可达状态、待观感验收；合法候选必须能进入真实分支 |
| ItemRendererEquipment2dMixin | TAIL时原版或BEWLR已提交顶点；使用localHolder | 改为绘制前窄接管，携带真实上下文与持有人，失败完整走旧路径 |
| EquipmentRouteRenderer | 提供方也取particle图再走front；提供方geometry=null | 提供方复用真实BakedModel绘制；禁止补假geometry掩盖NPE和多层丢失 |
| ItemSpriteGeometry / EquipmentGeometry | 发射顶点是[0,W/16]块，握点却是中心化16模型单位 | 统一原点和单位；验证最终发射顶点，不只复算握点公式 |
| EquipmentStateResolver / EquipmentPredicates | pull=elapsed/getUseDuration | 弓按现有原版属性elapsed/20，弩按getChargeDuration；同一实际栈判断 |
| EquipmentRouteRenderer.applyHeld | 未消费entry.display；仍用旧模型变换、WeaponCorrections和固定-45° | 新路径唯一变换所有者；每件每上下文消费真实display |
| 盾路径 | front/back按状态取图；blocking回退ownModel；法线传单位PoseStack | 同一次绘制正反不同材质、明确格挡姿态，法线随实际变换 |

这些新渲染缺陷目前被零ready及解析失败遮住，不代表启用后的安全性。修复快照准入后才能观察后续缺陷，不能把“未进分支”记为渲染验证通过。

item/WeaponStacks和server/skills/AshBindings继续保持现有命名空间、成长、白名单、权限和锁定语义。自有long_bow是BowItem；与提供方LongbowItem类型不同本身不是所有视觉复用的阻断理由，只有具体回调/loader确实依赖该类型时才需适配或回退。

## 3. 最小结构及责任

沿用现有 routes/2d.json 的 equipmentSchemaVersion=2、生成任务和equipment2d类修复，不再重建第二套路由；不要更改同文件的fx/ui契约。以下是职责划分，不要求每行新建一层框架。

| 职责 | 输入 → 输出 | 约束 |
|---|---|---|
| 准入/生成 | 42项清单、白名单、资源根 → 自有资源与模型声明 | 校验后生成；禁止复制研究素材；输出仅在 build 内 |
| 客户端模型路由 | 实际 ItemStack、显示上下文、当前使用状态 → 模型 | 不写物品组件，不改变伤害、用时、弹药或动画权威 |
| 提供方适配 | 已加载资源/loader、完整模型 ID、明确支持的状态 → 候选模型或回退 | 外部类与 loader 缺失时不触发类加载；专用服务器不加载客户端类 |
| 握持与 GUI | 单件尺寸、握点、上下文、状态 → 一次最终变换/GUI模型 | 持用比例和GUI适配独立；所有状态共享同一坐标约定 |
| 重载快照 | 完整解析结果 → 一次替换的不可变路由表 | 清理旧 baked model 引用，不在半完成表上渲染 |

路线必须区分：

- **own_sprite**：只准入本批白名单图；独立 GUI、必要状态、盾双面均由自有资源生成。
- **vanilla_model**：引用 minecraft 模型资源，保留状态；不复制提取的 Mojang PNG。直剑/斧/短弓/弩共4项。
- **provider_model**：自有 Item 使用已安装提供方的视觉模型；10项 Spartan 映射。保留完整模型链，按自有实际栈适配状态。
- **真实外部 Item**：不是本次视觉路线。若以后要让第三方物品参与战灰，须以完整物品 ID 经 Definition.items 和 compatible 显式准入，再走原服务端权限/绑定入口；这仍不自动获得 WeaponStacks 成长能力。

## 4. 提议的装备配置契约

以下保留最初的概念示例，**不等同DSH现有schema v2字段形状，不能直接覆盖正式清单**（现有held/gui为数组）。按现有schema修复读写一致性，不为示例再迁移一次。保留pending/ready；ready表示适配能力和静态输入完整，观感验收另存回执。acceptance_pending不是技术准入硬失败；隔离候选流程见§11。

```json
{
  "id": "eldencombat:great_sword",
  "status": "pending",
  "route": "own_sprite",
  "assetManifest": "art/equipment-2d/full/equipment-42.json",
  "assetId": "great_sword",
  "held": {"front": "sprites/great_sword_front.png"},
  "gui": {"16": "sprites/great_sword_gui_16.png", "32": "sprites/great_sword_gui_32.png"},
  "geometry": {"canvas": [32, 32], "anchorPixels": [7.5, 24.5], "worldLengthBlocks": 1.584, "sourceSpanPixels": 37.618080759124325},
  "display": null,
  "fallback": "legacy"
}
```

示例held/gui源路径相对assetManifest所在目录；现有schema v2的路径根按其准入声明执行，不混用两种相对根。生成器依据白名单映射为eldencombat自有texture ID。display=null没有可执行变换，不能直接进入ready；DSH应先给候选填写明确参数并标记未验，而非要求用户提供矩阵。display至少明确firstperson/thirdperson左右手、GUI、ground、fixed及未指定上下文的继承；未经实机校准的值是候选，不是最终观感值。

提供方条目使用 providerMod、model、states、predicateBindings、displayPolicy、fallback；model 是 `namespace:item/model_name`，不是物品注册 ID。例：long_bow 的 model 是 `spartan_weaponry_unofficial:item/longbow_iron_strengthened`，三状态模型后缀为 `_pulling_0/1/2`。图像研究路径不进入该运行配置。

清单校验要求ID集合恰为当前41件加药瓶、无重复/额外项；状态名、资源ID、有限正尺寸、握点范围与白名单哈希有效。源路径解析后仍在对应准入根。缺必要输入、未知且可能可达的状态、未解析的必要loader或无完整回退时拒绝该路径；有证据不可达的predicate保留为不适用，不能永久阻断正常standby。可达但暂不支持的状态须有明确的旧路径回退，不能无声选standby。观感未确认、旧源须保留、物品类型不同不单独构成技术硬失败。

## 5. 长度、握点与独立 GUI

像素握点采用画布左上角为原点、像素中心坐标。W×H 画布的中心化模型坐标为 `a=(16*x/W-8, 8-16*y/H, 0)`。方形画布按有效跨度计算 XY 比例 `s=worldLengthBlocks*W/sourceSpanPixels`；不能按整张 PNG 或非透明包围盒的单一边长替代斜向跨度。非方形输入须按相同归一坐标计算跨度，不直接套 W。

大剑32图的 a=(-4.25,-4.25,0)，建议 s≈1.347437；普通盾 a=(0.25,-0.25,0)，s≈1.028571；神躯化剑64图长度1.710。它们都是逐件初值，未完成游戏掌心校准。掌心目标 p 与旋转 R 确定后，平移按 `t=p-R*S*a` 求出，使用同一模型单位；接入时对齐 MC 的变换顺序和中心平移，只施加一次。

第一/第三人称与左右手分别校准；左手不是随意水平翻转纹理。父模型已有 display 和状态覆盖必须明确采用或替换，不能父模型一遍、wrapper一遍、旧 palmAnchor 再一遍。提供方换状态后也不能跳回另一套比例/握点。

JSON display 只能变换，不能仅靠它切换 GUI 纹理。DSH 在现有 MC1.21.1 模型接口上实现最小上下文选择：GUI 取独立 gui_16（默认）或显式选择的 gui_32，手持取 held；其余上下文明确使用何者。保留 overrides、发光/附魔等实际适用行为，禁止共享可变上下文污染另一只手或另一物品。不要引用其他 MC 版本的物品模型配置 API。

提供方条目 gui 为空时使用提供方模型的 GUI 路径，不生成或打包研究缩略图。保护用户已选定的大剑、特大剑旧 model/mesh/bbmodel：先保留旧回退，新2D作为候选接入，不覆盖旧源设计。

## 6. 提供方状态、loader 与完整模型链

本地固定取证包：spartan_weaponry_unofficial-1.2.3-1.21.1.jar，SHA256 `84d3ddafb519e2f5e03f2681ff52c30c92c9512181f61a7a7487399a0179888f`。文件位置及43个状态详见 evidence.json；依赖版本/哈希不符时重新准入，不悄悄继承旧结论。

关键证据见 [ClientHelper 反汇编](research-evidence/spartan-client-helper-20261015.txt)：registerLongbowPropertyOverrides 接收 LongbowItem 并逐 Item 注册属性；pull 回调捕获该 LongbowItem，调用 getNockProgress。**不能把这段提供方回调绑定到自有 BowItem 当通用函数，也不能强转物品类型。**

| 模型 | 实际 predicate | 本模组适配要求 |
|---|---|---|
| long_bow pulling_0 | spartan_weaponry_unofficial:pulling=1 | 仅实际栈正在使用时为1 |
| long_bow pulling_1 | 同上且 spartan_weaponry_unofficial:pull≥0.65 | 从自有弓现有使用进度计算视觉比例 |
| long_bow pulling_2 | 同上且 pull≥0.9 | 完整保留命名空间和覆盖顺序 |
| spear blocking / throwing | spartan_weaponry_unofficial:blocking / throwing=1 | 只有自有玩法真的存在对应状态才映射；不能因模型存在而新增投掷/格挡玩法 |

setupItemStates注册minecraft属性，新增EquipmentPredicates已尝试注册Spartan别名，但当前分母错误且value对其他predicate默认返回0，不能声称提供方/原版状态链已通。DSH须复用实际Item上正确的原版属性或等效计算，再桥接明确的别名；拿不到真实使用者/栈或已中断时归零。给每个predicate列出支持/不适用/阻断原因；不适用状态保留证据但不伪造触发，不把整个装备一起封死。

9条Spartan近战模型含 `spartan_weaponry_unofficial:oil_coated_item` loader 和 `coating` 附加纹理，长弓只有layer0，不能把两者混为一谈。选择实际baked model时保留loader、父链、附加层、override解析和适用的render passes/types；不能以particle icon替代完整模型。核对具体回调是否要求提供方Item类型或专用组件；不满足必要前提时回退，不顺便增加涂油玩法。

只把外部模型设为 JSON parent 不足以证明 loader、override、涂层与状态 display 全部继承正确。DSH 应给出适配入口和能力回执；逐状态确认父 display 不覆盖最终握点校准。模型解析始终使用真实物品栈，不能替换为丢失强化、质变、战灰、自定义名、耐久或附魔的临时栈。

## 7. 盾、弓弩及特殊资产

- 三种盾分别有 front/back；以相反法线、正确 UV 和侧边处理构成双面表现，不能两面都采 front，也不能重合面造成闪烁。blocking 复用两面材质改变姿态，使用现有实际格挡状态；不是新贴图或新防御结算。
- 大弓 standby/pulling_0/1/2；弩炮 standby/loading_0/1/2/loaded。状态映射读取当前物品的真实使用/装填数据，切手、中断、放箭后回退；GUI 不因 held 状态丢失独立小图。
- artDrawFraction 仅描述视觉拉开程度，不规定充能时间、射速或伤害。弩炮额外弹药/烟花图尚未交付：保留既有玩法，并为现有可达而未覆盖的状态指定旧模型回退，不能套用新原版弩玩法或误显示已装普通箭。
- 碎星大剑是单刃精灵；若现有双持表现需要两把，摆放两个实例，不能拼成单张双剑图。软鞭、连枷本次仅静置图，不宣称已有攻击形变。
- test_flask 仅 full 态；保留现有饮用/补充规则。没有空瓶素材不等于允许新增消耗或无限补充规则。

## 8. 缺依赖、重载与发布

缺依赖回退必须发生在解析外部 loader/模型链之前。基础自有模型保留可用 legacy 回退，不能让其无条件 parent 指向缺失提供方，再指望渲染阶段补救。提供方存在但模型/纹理/loader 任一缺失也使用同一回退；日志按装备与原因去重，不逐帧刷屏。若通过额外候选模型加载实现，先检查准入和资源可用性，再将候选交给烘焙流程。

资源重载重新解析并原子替换路由；新快照失效的条目立即采用旧模型资源的重新烘焙结果，不能继续引用上轮图集的 baked model。缺依赖不影响服务端开机、物品注册或存档读取。静态构建验证与客户端依赖检测分开：发布包允许保留可选引用，运行时必须有真实能力检测。

打包仅按 publishable-assets.json 准入自有94图和DSH生成的模型/路由。白名单路径相对清单目录，比较实际文件哈希。研究目录、包含提供方研究素材的预览合成图、提取PNG和取证JAR均不进入本模组包。原版资源直接引用；Spartan以完整依赖方式处理，署名/许可证据沿用已有审计，不将 Apache-2.0 声明扩大为所有单张美术的拆分许可。

构建输入必须包含实际准入清单/源图/适配配置，否则增量构建会漏掉改图。只清理受控 build 生成目录，并处理 ready→pending 后 processResources 目标目录的陈旧覆盖文件；不得删正式旧模型。资源优先级固定并记录，不能靠复制先后偶然覆盖。

## 9. DSH 分批交付与集中验收

1. **基础适配**：实现上述最小生成、上下文选择、predicate、可选依赖与回退合同；先交变更清单及仍阻断的能力。
2. **首批六类**：straight_sword、great_sword、spear、shield、long_bow、sacred_relic_sword。覆盖原版引用、自有32/64图、外部loader与状态、双面盾、独立GUI。大剑新路线不覆盖选定旧源。
3. **剩余36项**：沿用首批能力扩展全42覆盖；补齐其他盾、大弓、弩炮及药瓶状态边界。缺能力的条目继续 pending，逐件说明，不能用静置图冒充完整弓弩。
4. **实现后集中验收**：按用户安排统一构建与运行，不边写边启动。记录实际命令/日志、未跑场景及用户观感确认，不能只交 ready 数量。

| 验收项 | 判定 |
|---|---|
| 注册和保存 | 41旧装备+药瓶ID不变；视觉切换/重载前后强化、质变、ASH_BINDING、其他组件完整 |
| 显示上下文 | 六类首批逐件检查GUI16/32、掉落物、展示框、一/三人称左右手、标准/纤细手臂；扩展后42项均有回执 |
| 状态连续性 | 盾两面/格挡，弓起始/半拉/满拉/中断，弩装填/已装填/发射；无跳握点、错手同步或残留状态 |
| 依赖及重载 | 无Spartan、有已准入Spartan、缺模型/纹理、重载失败和恢复；无紫黑缺图、客户端类泄漏或旧缓存残留 |
| 包内容 | 只包含准入自有资产和生成资源；不含研究提取PNG/JAR；pending回退无陈旧覆盖 |
| 权威隔离 | 客户端只选模型；战灰仍经原绑定/施放入口；护符、赐福数据与权限不被视觉模块改写 |

装备2D工单编号建议 E01生成/准入、E02握点GUI、E03提供方状态loader、E04盾弓弩、E05回退重载、E06全42验收。每项注明“待实现/已实现未验/已验证”并附证据。

既有八战技特效仍独立按 [VFX实施合同](DSH-VFX-IMPLEMENTATION-CONTRACT-20261014.md) 收口；相机夹具、波形可见帧、缺图回退、火墙障碍等旧缺口不能用装备2D验收销账。本文件也不覆盖 [三系统框架](DSH-ASH-TALISMAN-GRACE-FRAMEWORK-20261013.md) 和 [DSH三系统进度](DSH-THREE-SYSTEMS-PROGRESS-20261013.md) 的玩法与多人待验项。

## 10. 修订A的调用合同

### 10.1 准备完整后再接管

当前本地 `build/moddev/artifacts/neoforge-21.1.219-sources.jar` 的ItemRenderer.render顺序是：pushPose → 特例模型选择 → ClientHooks.handleCameraTransforms → translate(-0.5,-0.5,-0.5) → 普通模型render passes或BEWLR → popPose。isCustomRenderer分支之前已经发生旧display/中心变换，因此“改到该分支前”仍不足以满足单次变换。

DSH应在本模组目标Item的该8参render方法入口、旧display和内部push之前尝试窄接管；此处是设计锚点，交付时附本版本实际调用链及第三方注入兼容结果。不要给所有物品改全局绘制规则。低层方法没有holder参数，须从真实上游渲染调用携带上下文，不能在这里猜Minecraft.player。

逻辑上分两步，可用现有类内方法实现，无需新建通用渲染框架：

1. **准备**：只读当前栈、真实holder/hand、level、seed、显示上下文及leftHanded；固定本次snapshot/model generation；选择状态、模型、必要图层/盾两面、最终变换、回退能力。此步不改外部PoseStack、不提交顶点，返回“本次不接管”或完整可绘制结果。
2. **绘制**：准备成功后仅由新路径负责本次绘制；push/pop与递归防护成对恢复，使用try/finally。自身没有再走同一接管的递归；复用标准ItemRenderer时用有作用域的绕过标志且在退出时恢复原值。仅该本模组路由被绕过，其他物品保持原流程。

pending、缺资源、缺可达状态等预期失败在步骤1返回旧路径，旧画面完整绘制一次。不能一边提交新顶点一边发现缺图，再返回false画旧路径；普通VertexConsumer不能撤销已提交顶点。绘制阶段的意外异常要记录为失败，不能伪装成可无损回退或成功验收。测试应证明所有已知能力缺失都在提交前处理。

旧回退必须是完整旧模型/BEWLR行为。若正式根模型被生成资源覆盖，明确保存/选择旧回退根的机制；不能返回false却让原链读取同一个坏的新根。也不能仅在旧BEWLR开头return，导致新准备失败时两边都不画。

### 10.2 自有精灵与提供方绘制分流

own_sprite消费白名单精灵及自己的几何数据。vanilla_model/provider_model消费真实BakedModel及其必要的render passes/types、tint/foil、override上下文；geometry=null是合法的路线差异，不应访问自有quad方法，也不应在能力报告里因“无geometry”拒绝。getParticleIcon仅是粒子用途，不是多层物品模型的可替代输出。

实际模型选择与绘制只解析一次状态链。如果沿用标准模型override，正确的属性绑定应在实际自有Item上就绪；如果适配器显式选择状态，必须保持提供方顺序及语义，不能再重复选择出不同模型。loader生成的动态行为不能因手工选JSON模型而丢失。涉及provider具体类型的行为逐项核对；不以“14个自有Item不是提供方Item”一刀切拒绝直剑/斧等可复用模型。

### 10.3 坐标和display的唯一消费点

自有精灵统一发射中心化块坐标 `b(x,y)=(x/W-0.5, 0.5-y/H, 0)`，未缩放画布范围为[-0.5,0.5]；握点 `aBlock=aModel/16`，最终变换 `T(pBlock)·R·S·T(-aBlock)`。JSON translation为模型单位时仅转换一次/16，rotation是角度，scale无单位；不把角度除16。方形图用§5的s，不能再按W/16放大顶点；非方形若暂无归一跨度证据应明确不支持，不写“相同公式已正确支持”。

JSON里的普通TRS可表达 `t=p-R*S*a` 对应的单次仿射变换（这里S为统一比例），必须对齐单位、旋转约定及左手处理；“掌心标定无法用JSON表达”不是选择mixin的理由。独立GUI资源选择、双面盾和可选模型路由才是本次额外能力。

entry.display必须实际解析并消费，字段变化要能改变画面。明确采用“最终display替换”或“提供方display作为唯一基础”的逐上下文策略；采用替换时标准绘制的相机变换不能再叠一遍。旧WeaponCorrections、旧3D bounds fitting和固定-45°不得作为新路线隐藏修正。参考物品用于提出掌心候选时，用参考物品自己的柄坐标求p，不能用目标装备柄像素代替。

GUI选独立16/32模型和该图自己的尺寸，不能拿held32/64画布发射GUI顶点；切换GUI分辨率不改变手持世界长度。盾front/back是同一几何的两种面材质，不是两个互斥使用状态。格挡仅切换明确的display姿态；两面始终匹配、绕序/剔除策略一致，无重合闪烁。法线用实际最终pose的法线变换，不能传新单位PoseStack；非统一缩放也不能简单用位置矩阵当法线矩阵。

IClientItemExtensions没有ResourceManager参数不代表BEWLR不能读客户端现有不可变快照；报告这条前提错误。此轮选修订A是为统一绘制前路由和回退，不是宣称BEWLR在技术上不能访问snapshot。无需为此额外创建第二份可变资源状态。

### 10.4 真实使用者和状态

上下文必须保留真实渲染holder、使用手、实际stack、level与seed；第三人称他人持物不能读本地玩家。GUI/地面/展示框没有holder时保持null，活动拉弓/格挡归零，已装填弹药等栈内状态仍可读。有作用域的上游上下文桥接须支持嵌套并在退出时恢复，不能用一个未清理的静态lastHolder。

基于同一实际栈且正在使用的前提：普通BowItem视觉pull取elapsed/20，clamp用于本地视觉分档；13tick=0.65、18tick=0.9、20tick=1。72000是最大使用时长，不是拉满用时。CrossbowItem取elapsed/getChargeDuration(stack,holder)，后者包含实际附魔等充能规则；getUseDuration=chargeDuration+3，不能作分母。两处已由本地1.21.1源码核对。取现有原版属性时保持非使用栈归零保护；Spartan别名只桥接纯视觉数值，不调用捕获外部LongbowItem的函数。

原版弩也使用pull/pulling，不能因不是BowItem就标不适用；charged与firework读取真实弹药组件。vanilla_model的crossbow已有 `minecraft:item/crossbow_firework` 提供方状态证据，应接入既有烟花显示。它与own_sprite的ballista缺烟花图是两件事：弩炮真实装了烟花时走明确旧回退，不能冒充loaded普通箭或standby。盾格挡同时检查实际使用栈及对应状态，不因holder正在吃东西就格挡。

## 11. 候选标定和准入，避免验证循环

DSH按用户后续实施安排执行以下步骤，本合同本身不启动DSH或实例：

1. 先修§2/§10，包括display解析缺口。复用现有原版参考姿态、真实柄点和美术逐件比例，由DSH计算首批六类明确的display候选。候选来源和单位记录在回执中，不需要用户计算p/R或42套矩阵。
2. 将候选参数与生成派生字段分开保存；重跑build-route-config不能无条件把候选display重置null。用最小可恢复的校准配置/覆盖文件即可，不另造编辑器。
3. 在隔离构建/运行实例合并候选覆盖，允许技术输入完整的首批条目成为测试配置的ready；生产配置保持原状态直到完成应有准入。测试档仍标humanAccepted=false/待确认，并记录启用ID、资源版本、实例与输出路径，不把测试输出混入正式发布。
4. acceptance_pending是回执状态，不是阻止候选渲染的条件。display明确但未人工确认可以作为候选验证；display=null、缺必要模型/loader或有已知崩溃路径仍不能直接放行。运行后失败应修复和复验，不能换回全部pending后声称成功。
5. 按每个状态判能力：supported可选；有证据unreachable的blocking/throwing不强造玩法且不阻断standby；可达却未覆盖的状态必须在绘制前完整回退；未知可达性单列调查。基础模型/loader确实缺失则整个候选回退，不能为了进机忽略必要前提。
6. 不需要Spartan的自有大剑、神躯化剑、盾及原版直剑可先完成成功路径验证；Spartan使用已取证完整依赖在隔离实例验证，记录实际加载版本/哈希。installed=null是旧取证时未知，不等价于永久未安装，DSH在执行时核对实际实例。未完成该组就明确缺哪组，不把回退画面计作提供方成功。
7. 首批工程验证通过后提交可比较的一/三人称、左右手与GUI观感证据，请用户选择/微调；DSH负责更新参数。保留旧大剑/特大剑源文件及可恢复回退不阻止测试候选。推广到全部42项后逐件留回执。

## 12. 下一批成功路径验收与残余不确定项

修复实现后集中编译和有针对性的测试，再按安排运行；此轮文档更新没有执行这些测试。不能仅交零ready构建、公式复算或拒绝路径日志。

| 检查 | 必须出现的成功证据 | 同批拒绝/恢复证据 |
|---|---|---|
| 配置链 | 一条真实候选经生成→displayOf→快照准入→绘制；改display平移后实际顶点按预期移动 | 缺display拒绝且指出字段，重跑生成不抹掉校准值 |
| 单次接管 | 六类记录所选新路径；同次绘制旧BEWLR/普通旧模型无额外主要绘制 | pending或缺资源走旧路径一次；嵌套委托后递归标志和pose恢复 |
| 实际几何 | 对真实发射顶点/UV取样：大剑柄映射到p，32/64图的有效跨度匹配1.584/1.710格；GUI16不沿用held64几何 | 切GUI分辨率不改手持长度，左手与旋转后法线正确 |
| 提供方模型 | 原版直剑完整模型；Spartan矛保留模型层/pass；长弓真实跨三拉弓状态 | 缺依赖/必要loader时完整回退；geometry=null不崩、不取particle代画 |
| 弓与弩 | 弓13/18/20tick对应0.65/0.9/1；弩按实际chargeDuration到满，快装附魔时分母随规则变；原版弩烟花状态 | 切手、中断、非使用栈归零；弩炮无烟花图明确旧回退 |
| 盾与观察者 | 待机和格挡均有不同正反图，实际盾使用时姿态生效；他人拉弓可被观察 | 本地玩家吃东西不让他人盾格挡；GUI/掉落物无holder不借本地使用状态 |
| 重载/覆盖 | 非零候选产物在重载后使用同代快照/模型；ready→pending恢复旧模型 | 缺图再恢复无旧图集引用；正式包无研究PNG/JAR和测试覆盖 |

世界长度取模型空间/世界单位测量，不能用透视截图像素长度当唯一判据。日志能说明分支与状态，画面能说明显示结果，两者互补；多人观察者状态、第一人称占屏和最终观感仍要真实场景。

残余待实现方确定并给证据：精确上游holder桥接位置及其他mixin兼容；提供方loader在真实自有栈上的动态行为和render passes；候选display的实际占屏/握点；快照解析与模型烘焙事件的同代发布顺序。不能以“待用户给矩阵”取代这些实现责任。

本次只更新本合同和框架交接索引。DSH第一批报告保留为历史提交；其中“唯一结构性缺陷”“基础适配全部完成”“用户必须提供标定矩阵”均不作为当前裁决。装备运行未完成，VFX及三系统待验项仍独立。
