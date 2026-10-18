# DSH最新代码审查

更新：本文件保留审查时的缺陷证据。用户随后授权Codex直接修复，4项代码修复及集中验证结果见 [修复回执](CODEX-DSH-REVIEW-FIXES-20261016.md)；游戏验收仍未完成。

2026-10-16。范围为最新装备2D修复、相关防御输入/同步和Margit创造目标修复。先只读核对美术对话idle且最新回合completed，再开始审查；本轮没有修改Java、资源或美术，没有运行构建、测试、游戏或DSH。

依据为当前源码、正式routes/2d.json、DSH-EQUIPMENT-2D-REPAIR-RECEIPT-20261015.md和固定本地neoforge-21.1.219-sources.jar。下面是源码调用链可确定的问题，触发步骤供实现方集中复验，**不是本轮已跑的游戏复现记录**。没有把旧TAIL双绘、旧elapsed/72000、displayOf未调用等已经修复的问题重复报出。

## 1. [P1] 提供方烘焙表键错误，原版/外部路线实际不能接管

位置：src/main/java/com/dsh/eldencombat/client/equipment2d/ProviderModelAdapter.java:72–80；关联EquipmentRouteSnapshot.java:390–396、client/EldenCombatClient.java:208–224。

当前正式直剑model是minecraft:item/iron_sword。适配器查询 `minecraft:item/iron_sword#inventory`，再查standalone键；但ModelBakery.loadItemModelAndDependencies对注册物品 `minecraft:iron_sword` 生成的是 `minecraft:iron_sword#inventory`，只在读取JSON时附加item/。两者不是一个键。项目RegisterAdditional又明确只注册own_sprite，不会补上这些提供方standalone键。

因此普通直剑/斧就会查不到模型而走旧BEWLR；短弓/弩/长弓的状态JSON虽然会作为override依赖烘焙，但这些依赖也不会自动成为getBakedTopLevelModels顶层键，当前循环要求每个可用状态都有顶层键，还会导致整链退回。安装Spartan本身不解决这个模型ID/顶层注册问题。41条静态准入不能当作41条真实新路径。

触发：无第三方依赖的普通客户端渲染自有straight_sword、short_bow或crossbow；观察prepareProvider返回null和旧BEWLR被调用。原版资源文件存在，providerAvailable不会对此告警，易误认为接入成功。

修复方向：区分物品注册键与模型资源ID；普通Item入口使用正确inventory键并让真实override链解析状态，或在能力检查后显式注册所需standalone模型。单独解决动态状态不是顶层模型的问题，不能仅为所有路径删item/（pulling_0不是注册Item）。验收至少包含无Spartan直剑、原版弓弩以及安装Spartan后长弓的实际成功路径。

本地引擎证据：[ModelBakery](research-evidence/equipment-review-20261016/ModelBakery.java.txt:175)、[ModelResourceLocation](research-evidence/equipment-review-20261016/ModelResourceLocation.java.txt:23)、[ModelManager](research-evidence/equipment-review-20261016/ModelManager.java.txt)。

## 2. [P2] 新盾格挡姿态未接项目真实防御状态

位置：src/main/java/com/dsh/eldencombat/client/equipment2d/EquipmentStateResolver.java:159–161；关联EquipmentRouteRenderer.java:248–250、client/ClientCombat.java:191–200及221–226、server/ServerCombat.java:328–333。

shieldBlocking仅检查原版holder.isUsingItem/getUseItem；但战斗模式持盾右键会在ClientCombat取消原版物品使用，只发GUARD_START。服务端只设置state.guarding=true，既不startUsingItem也不建立原版useItem状态。服务器可真实防住伤害且CombatStatePayload.guarding已同步，新渲染器仍必选entry.display而非displayBlocking。

触发：主手或副手持任一自有2D盾，R进入战斗模式后按住右键；盾继续待机姿态。退出战斗模式后原版使用可能有格挡姿态，不能据此宣称项目战斗防御路径已验收。

修复方向：渲染读取当前被绘制玩家的服务端防御镜像，结合真实持盾手/栈与有效防御条件选择姿态；原版使用作为非战斗模式路径。不能借本地玩家guardHeld替代他人状态，也不为了姿态重复启动另一套防御结算。验收覆盖自己/观察者、双手、松开、换盾和破防。

## 3. [P2] 玛尔基特会被附近创造玩家阻断生存目标选择

位置：src/main/java/com/dsh/eldencombat/entity/MargitEntity.java:385–392。

新增逻辑先取不排除创造的getNearestPlayer(this,range)，再判断返回者不是创造。只要更近的是创造玩家，便得到current=null，不会继续搜索范围内稍远的生存玩家；后续tick又选中同一个创造玩家并拒绝。因此混合模式多人时，创造观察者能让无当前目标的Boss持续不获取有效目标。

触发：Boss当前无目标，一名创造玩家在2格、一名生存玩家在4格且都在索敌范围。旧创造目标被清掉后也会进入同一分支。引擎EntityGetter该重载传false，只排旁观，不排创造。

修复方向：将存活/非创造/非旁观等有效性条件放进最近玩家查询谓词，在候选集合里筛选，而不是只拒绝查询结果。集中验证只有创造、只有生存、创造更近且生存稍远，以及当前目标改创造的四种情况。

本地引擎证据：[EntityGetter](research-evidence/equipment-review-20261016/EntityGetter.java.txt:96)。

## 4. [P2] 缺失/损坏自有状态模型被当成有效模型，承诺的旧回退失效

位置：src/main/java/com/dsh/eldencombat/client/equipment2d/EquipmentRouteRenderer.java:405–410；关联同文件182–185、215–218、235–245。

model()只检查baked表是否取到非null。NeoForge/原版ModelBakery读取缺失或坏JSON时会把该位置替换为missingModel，随后作为非null模型烘焙并发布到注册的顶层键。own_sprite的requiredPresence虽被解析，却没有像providerAvailable那样在准备阶段检查；返回的missing模型/缺图sprite也未被拒绝。

触发：在隔离实例让一条ready自有路线的状态JSON缺失或损坏后重载。RegisterAdditional仍注册清单里的同一个ID；prepare把烘焙出的missing模型视为成功，绘出紫黑缺图并取消旧路径。盾路径spriteOf也可从missing模型取得非null的missing纹理，因而通过判空。若只是纹理缺失，非null的atlas缺图sprite同样不能由当前判空识别。

修复方向：准备阶段对自有路线验证本次需要的资源以及模型/纹理实际烘焙结果，识别缺失占位结果；任何必要面/状态缺失在提交顶点前整体返回旧路径。仅与全局missingModel对象做身份比较也未必足够：坏JSON的missing几何可在不同模型键下重新烘焙。恢复文件再重载应重新启用新路径。

本地引擎证据：[ModelBakery.getModel异常回退](research-evidence/equipment-review-20261016/ModelBakery.java.txt:146)，配合该类bakeModels与顶层模型表发布链。

## 验证边界

DSH已有197项测试主要覆盖JSON准入、数学和纯状态分档，测试注释明确不触及纹理图集、buffer或GL。它们不能排除本次模型表查询、实际防御输入与资源重载回退问题。既有游戏会话无异常日志，也不能证明提供方接管成功：问题1直接返回旧路径，不抛异常。

本轮没有重新运行这些测试，没有将上述触发步骤记为实机PASS。建议DSH先按以上四条修复，再一次集中验证成功路径与拒绝/恢复路径；本审查不替代后续人工观感、多人、装备全42项或整合包验收。
