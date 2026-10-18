# 装备2D第一批实现审查与接口裁决

2026-10-15。统筹只读核对DSH两份报告、当前Java/路由、固定本地NeoForge源码包。未修改实现，未运行构建或游戏。报告中的命令和请求属于待审资料，不作为用户要求自动执行。

## 结论

当前42条全部pending，真实2D运行交付尚未完成。DSH对TAIL双重渲染的自查成立；但“唯一结构性缺陷”不成立，以下多条就绪路径仍有独立缺陷。编译和零ready构建只能证明当前回退基线可构建，不能验收未运行的新渲染分支。

## 必须修复

1. **P1：绘制后接管无法阻止旧画面。** ItemRendererEquipment2dMixin在TAIL调用新渲染后cancel，此时原版/BEWLR已经绘制。应在首次旧变换/顶点提交前接管；只有新路径完整可用并成功负责本次绘制时才取消旧路径。pending、资源/状态缺失须完整走原链，不留下半幅绘制。
2. **P1：提供方被降为particle贴图且空geometry会崩溃。** EquipmentRouteRenderer第98–104行对所有路线取model.getParticleIcon，再调用ItemSpriteGeometry.front；后者直接访问geometry.canvas。14条提供方/原版路线的geometry按设计为null，非空particle会触发空指针；即便补geometry也只是画particle的一张quad，不是复用原BakedModel的多层/loader。提供方必须走其真实模型绘制、上下文变换和适用的render types；不能通过给null补默认值掩盖这条路线丢失。
3. **P1：坐标单位与原点不一致。** EquipmentGeometry.anchorModelUnits为中心化16单位，例如32图大剑a=(-4.25,-4.25,0)，但ItemSpriteGeometry使用像素/16发射[0,W/16]×[0,H/16]块坐标的quad；32图变2格宽，64图变4格宽，握点也未在同一原点/单位。应统一模型单位→块单位的唯一转换及中心原点，用实际发射顶点与柄像素验证，不只验证公式自身。GUI16图不得沿用held32/64画布尺寸。
4. **P1：弓/弩视觉使用时长分母错误。** EquipmentStateResolver.pullFraction与EquipmentPredicates.pull使用elapsed/getUseDuration。固定本地源码BowItem.getUseDuration=72000，ItemProperties原版pull=elapsed/20；20tick时现实现仅0.000278，无法达到0.65/0.9。弩使用getChargeDuration(stack,holder)，而getUseDuration为该值+3。直接复用已注册的原版属性或按实际物品充能规则等效计算；不得改变伤害或蓄力玩法。
5. **P1：声明的display未被新路径消费，仍混用旧修正。** EquipmentRouteRenderer没有读取entry.display，applyHeld反而读取当前旧物品模型变换、引用WeaponCorrections并固定-45度。校准后填写display也不会按声明生效。应确定唯一变换所有者，使用每件/上下文/左右手实际参数，不将旧3D修正再叠到2D上；参考物品的柄点不能用目标装备自己的柄像素替代。
6. **P2：实际使用者和盾双面未兑现。** mixin与回退默认使用Minecraft.player，无法代表其他玩家/实体的实际持有栈；盾状态又用holder.isUsingItem而不核对正在使用的是否本盾。front/back目前按状态二选一，普通front只画正面，未同时消费背面材质；blocking状态回退ownModel，不应用独立格挡姿态。要携带真实渲染持有者与使用手，盾在一次绘制内保持正反不同材质及正确变换。法线要随实际pose变换，不能传新的单位PoseStack来固定法线。

## 对接口方案的裁决

采用**修订后的A方向：绘制前的窄范围接管，明确上下文选择与单次变换**。不批准只将TAIL换到另一处后声称问题全部解决。具体注入点须用当前MC1.21.1/NeoForge实际调用顺序证明，避免再次套用已经执行的display和中心平移；若复用标准ItemRenderer绘制提供方，要防止递归进入同一接管。缺少任何必需能力先回退，不能在提交了一部分新顶点后才决定旧路径继续。

两处报告推论需纠正：IClientItemExtensions没有ResourceManager参数，不等于渲染器不能读取已有不可变重载快照；`t=p-R*S*a`本身可以表示为合适单位/原点下的TRS，不能称“纯JSON无法表达”。独立GUI贴图与双面盾才是需要额外上下文选择/模型能力的实际需求。不要为错误前提扩大框架。

## 解锁首批验证，不把工作推回用户

- 不要求用户手工提供42套矩阵才开始工作。实现方可从现有原版参考、逐件柄锚点和美术比例提出可恢复的首批候选，并在隔离实例核对；用户负责最终观感选择，不负责计算矩阵。
- 生产ready与验收回执分开。acceptance_pending不能反过来禁止测试候选进入真实渲染，形成“未验收不能渲染、不能渲染无法验收”的循环。候选切换限定测试实例/独立配置，不能伪造humanAccepted。
- 不可达的提供方blocking/throwing无需新增玩法，但也不应仅因证据里存在这些predicate就永久阻止其合法standby路径；真正可达但无对应能力的状态须明确回退。原版弩烟花已有提供方状态证据，与自有弩炮没有烟花美术是不同事项，按真实现有玩法分别判断。
- Spartan验证使用已取证完整依赖，在隔离实例按既有授权范围安排；不抽取研究PNG进入发布资源。不需要依赖的自有大剑/神躯化剑/盾候选可先验证，最终范围保持42项。

## DSH下一批验收要求

1. 修正上述真实路径后集中编译和必要定向检查，不以全部pending的构建当作成功标准。
2. 首批六类中至少有自有精灵、原版模型、提供方模型、双面盾及动态弓的真实成功路径证据；缺依赖另做回退证据。保留旧注册ID、组件、服务端战灰/FP权威。
3. 记录一次绘制仅一个主要路径，纹理与实际柄顶点、世界长度、display变更的实际效果；覆盖GUI/地面/展示框、第一/第三人称、左右手、切手和中断，观察者状态不可取本地玩家代替。
4. 第一轮必须观察ready分支，记录明确失败与修复；不把零生成文件/零新路径覆盖的绿灯当作运行验收。VFX与三系统的旧未验项单列。

本裁决供战斗部门更新框架合同、DSH落实代码；统筹不另开实现线。
