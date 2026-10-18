# 已选美术补验：DSH框架与执行交接

2026-10-16。以用户最新分工为准：**战斗部门只负责框架、接口和验收合同，DSH承担后续具体实现。** 旧工单中“战斗直接编码、暂不交DSH”的执行条款已过时。本交接保留此前已经写入的实现和证据，不回滚；分工变更后没有新增Java/脚本/资源实现，也没有启动新的构建、游戏或DSH任务。

美术保持空闲。统筹派发后续执行前仍需只读核对两部门与在途命令；本回执不是自动启动DSH的授权。

## 1. 当前真实状态

| 项目 | 已有证据 | 结论 |
|---|---|---|
| 首轮资源、薄片、UI及黑剑朝向 | 正式路由42ready；黑剑四个手持上下文轴向翻转已通过先前6场景 | 保留，不重新实现或恢复旧方向 |
| 本轮集中编译/测试/JAR | `build-test-jar.log`：22秒成功，31套/207项/0失败/0错误/1跳过，session 21633退出0 | 编译与既有回归通过，不等于新增交互用例已实机通过 |
| 连枷第一次补验 | session 11209退出0，但日志有`ART_ACCEPTANCE_FAIL: absent provider did not draw legacy: "eldencombat:spear"` | **场景失败**。不能用退出0或此前6持握PASS掩盖后续失败 |
| 连枷生存物品栏重跑 | 引擎日志16:26:15记录`ART_FLAIL_COMPLETE`，6持握+物品栏/掉落物/展示框，10件缺提供方回退；16:26:17正常保存/停止 | 引擎场景完成；该次输出管道报错，**Gradle退出码未可靠捕获**，命令门禁仍缺可审计结果 |
| 赐福真实往返 | 已有待执行夹具和同类武器身份修补；本轮尚未启动赐福客户端 | 未验证，不能报告装卸/替换/拒绝链路通过 |
| Spartan实际加载 | 本地精确JAR哈希已核对；有待执行入口与检查 | 尚未实际装载，提供方成功、动态长弓、坏资源恢复均未验 |
| 当前进程 | 交接前`Get-Process java,javaw`结果为空；两个已跟踪session均终态 | 无待跟踪Java进程，没有把超时视为结束 |

统一证据目录：[art-acceptance-closure-20261016](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016)。

- 首轮失败完整日志：[flail-runtime.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/flail-runtime.log)，另有保留副本`flail-attempt1-creative-inventory.log`。
- 后续重跑引擎日志：[flail-retry-engine.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/flail-retry-engine.log)。旧副本名`aborted-retry-latest.log`是收集时暂名，不应由该文件名推断没有执行；以内容中的完成和停止记录为准。
- [进程状态](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/process-state-at-handoff.json)、[验收状态](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/acceptance-state-at-handoff.json)、[测试汇总](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/test-summary.json)、JUnit XML及本轮9张连枷截图均已保存。

发生过两种不同的重跑问题，不能混为一谈：一次Tee输出文件因Windows映射区域占用打开失败，上游客户端实际上继续运行，后来通过引擎日志和进程查询追到正常停止；随后使用新日志名请求重跑时，自动审批服务因额度耗尽未完成审查，工具明确表示**那次请求没有执行**。分工变更后未重试，不绕过审批。两个已启动/尝试启动命令都已核对终态，无继续等待的句柄。

## 2. 当前产物、保护与回退

当前磁盘[JAR](D:/codexout/mcmod/build/libs/eldencombat-0.2.0.jar) SHA256：

`cb6740cd11b497de679fa8a9b483ef084bf4380fda3ec208a3e3e4dc15597838`

它是本轮16:23集中构建的产物，包含此前黑剑朝向及本轮已编译生产代码；不应再把黑剑修正时的`e4c7312c...`或更早的`7d23a652...`当作当前磁盘包。后续生存物品栏夹具/运行结果门禁改动没有可靠的完整构建回执；夹具class时间比上述JAR新，应由DSH在继续执行时重新建立源码—日志—产物一致性。

[包核对](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/package-audit-at-handoff.json)再次逐项检查102张PNG，0缺失/差异，未含研究源目录或bbmodel。本轮新增`SelectedArtAcceptanceHarness`、`GraceArtAcceptanceHarness`及既有主smoke夹具被打包排除。包中仍有既有`SelfTest`、`PlayerAssetChecks`和`VfxIntegrationHarness`：前两者被正式诊断命令引用，不能盲删整个testing目录；DSH应核对纯VFX运行夹具的排除/源集边界，再给“测试源不入包”的准确结论。

[466份保护文件核对](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/protected-at-handoff.json)零变化。必须保留：

- 五件用户选版及全部94张发布PNG；不得通过改PNG来修姿态或UV。
- 黑剑“刃口朝外”：只绕刀身长轴翻转，刀尖方向、尺寸、握点保留；已写入可重建display生成器，不恢复旧朝向。
- 碎星front SHA：`270c60fe9edff3b0de34263dc2251da19f4fc47995f9c585646537e6ce009a43`。
- 弩炮display覆盖完整SHA：`daefa157e7bb37dcd89bbb2e54863bda31b1bdec948d4a71c2d80d7b1a18dcf0`。
- 旧大剑/特大剑、咒剑、亵渎、神躯化剑、盾牌选版，以及前轮模型键/动态状态、战斗盾、Boss筛选、缺图回退修复。
- 正式mods.lock未改，SHA：`e2795047bf804deff9595c03ee24f85bc75ec8518222809c532aa94a661647bf`。不在此单实施模组扩充或Combat Roll。

本轮修改前备份：[20261016-art-acceptance](D:/codexout/mcmod/docs/code-backups/20261016-art-acceptance)。当前修改文件哈希见[source-hashes-at-handoff.json](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/source-hashes-at-handoff.json)。回退只逐文件对照，不用整仓库reset，不覆盖任何素材/保护清单/人工校准。新夹具仅在移除其显式入口调用后才可移走，避免缺类。

## 3. DSH接手的接口与修改点

| 文件/接口 | 已写内容 | 后续责任 |
|---|---|---|
| [build.gradle](D:/codexout/mcmod/build.gradle) | `artAcceptance`诊断开关；`providerAcceptance`精确哈希runtimeOnly；新夹具JAR排除；补验日志完成/失败门禁 | 复核门禁确使失败/缺完成标记退出非0；唯一日志名，可靠捕获Gradle退出；不改正式锁 |
| [ClientSmokeHarness](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/testing/ClientSmokeHarness.java) | 既有gripItems单件过滤后加入连枷；持握完成后调用补验场景；provider模式走单独有界分支 | 保留真实玩家/持握；不要重跑全219项代替定向补验 |
| [SelectedArtAcceptanceHarness](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/testing/SelectedArtAcceptanceHarness.java) | `flailScenes`、`providerTick`、真实实体/物品栏及坏纹理资源包重载 | 接手A/C，修实际暴露的最小问题；失败必须有终态；不要把旧模型回退算提供方通过 |
| [GraceClientPrototypeHarness](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/testing/GraceClientPrototypeHarness.java) | `graceMenu + artAcceptance`显式进入新分支，复用既有Waystones世界和初始化 | 不重造菜单/网络或拥有权规则；依赖缺失需明确拒绝，不伪造UI |
| [GraceArtAcceptanceHarness](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/testing/GraceArtAcceptanceHarness.java) | 真实`Screen.mouseClicked`→现有客户端发送口→服务器→快照；正常与拒绝相位、GUI缩放/中文截图 | 尚未运行；须先审查应答等待，区分旧notice和本次真实应答，然后实际执行B |
| [GraceMenu](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/server/grace/GraceMenu.java)、[GraceMenuActions](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/server/grace/GraceMenuActions.java) | 菜单内按真实ItemStack引用/槽位轮换UUID token；同槽同类物品交换不再沿用旧标记 | 保留本次加固并实机验证；绑定自身组件变化不应使token失效；不增加持久化物品ID或改包长度 |
| [GraceMenuStatePayload](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/network/GraceMenuStatePayload.java) | token说明同步为菜单临时身份，现有协议字段不变 | 不把token当拥有权或永久授权；每次仍走服务器检查 |
| [EquipmentRouteRenderer](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/equipment2d/EquipmentRouteRenderer.java)、[EquipmentRenderer](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/EquipmentRenderer.java) | 显式诊断时，实际提交顶点后记录新模型/legacy、上下文、模型ID、pass/纹理/quad、实际与目标Z跨度；烘焙更换清记录 | 使用`diagnosticDraws()`确认真正走新路径；保留缺资源时提交顶点前退出规则 |
| [PresentationRoutes](D:/codexout/mcmod/src/main/java/com/dsh/eldencombat/client/PresentationRoutes.java) | 显式诊断记录实际图标16/32源、16逻辑像素目标、坐标/GUI缩放 | 用真实菜单/真实HUD截图核对名字、禁用、FP对齐；缺图不靠重画掩盖 |

诊断绘制日志只在显式`eldencombat.artAcceptance`下启用，不能用于改FP、命中、拥有权或平衡。持握夹具每服务器tick会摆设装备，扩展相位须确认未被它干扰；提供方模式绕过该初始化，不能误开两个夹具控制同一玩家。

## 4. A：连枷与缺提供方回退合同

首轮失败定位：夹具`prepare`把玩家设为创造，`InventoryScreen`会切到创造物品栏；只观察到快捷栏中的8件provider回退，主物品栏里的spear/thrusting_sword没绘制。**这不能推断两件装备的回退坏了。** 现有源码已改为生存物品栏，并显式提供测试箭；后续引擎日志已观察到10件回退和连枷GUI/GROUND/FIXED。

DSH应先复核现存9张截图和`ART_DRAW`，不从头改模型；需要补命令门禁/退出结果时只重跑本单。示例命令的日志应选择新的、不被读取器映射的文件名：

```powershell
Set-Location -LiteralPath D:/codexout/mcmod
$env:JAVA_HOME='D:/JDK21'
.\gradlew.bat compileJava test jar --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
.\gradlew.bat runClient -PclientSmoke -PgripSmoke -PgripItem=flail -PartAcceptance --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

验收必须同时满足：一三人称左右主手及第三人称副手、真实物品栏GUI、真实ItemEntity的GROUND、真实ItemFrame的FIXED；新选版纹理路径/哈希正确，实际握点保持，Z与参考上下文一致，画面对象可见。引擎日志已有源深度0.0625，手持/世界变换后跨度按父缩放变化；GUI的跨度单位含GUI绘制缩放，不能把日志里的GUI数值当世界格宽度。截图不代填人类观感通过。

## 5. B：真实赐福菜单与拒绝路径合同

接手入口（本轮未执行）：

```powershell
.\gradlew.bat runClient -PgracePrototype -PgraceMenu -PartAcceptance --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

复用隔离世界`D:/codexout/mcmod/build/gracePrototypeClient/saves/grace-prototype`。必要本地依赖在`D:/codexout/mcmod/build/gracePrototype-dependencies`：Waystones 21.1.46、Balm 21.0.66；现有prototype开关还加载JourneyMap 6.0.9及其内嵌依赖。不要覆盖游玩实例，不把这些依赖写入正式锁。

验收序列：

1. 服务端快照创建真实菜单；中文三种名字/图标、未拥有禁用，原版控件的禁用鼠标事件不能成功。
2. 故意发送未拥有请求，经真实网络被服务端拒绝，整件物品及组件不变；测试授予必须明确记录为诊断准备，不是正常玩法奖励。
3. 已拥有且兼容→真实Screen鼠标事件→BIND→服务器组件→新快照→已绑定禁用；随后REMOVE同样闭环。
4. REPLACE使用当前绑定状态选择动作。**当前三战灰支持分组互斥，没有一把正常武器可同时兼容两种。** 现有夹具仅通过显式种入“旧的不兼容绑定”测试替换传输/写入链，不能称正常玩法中两个兼容战灰替换已验收，也不能为验收扩大白名单或伪造第四种战灰。
5. 已拥有但不兼容、无效token、过期token分别经真实网络拒绝，服务端完整物品数据不变；特别包含同槽换入另一件相同类型/组件物品的旧token。绑定自身组件变化不应轮换同一物品token。
6. GUI缩放1/2实际截图；图标实际16/32源选择、文字截断和提示可读、禁用状态；真实服务端FP镜像的HUD对齐截图。只发现确切布局问题才局部修代码，不派美术重画。

**接手先审查的夹具风险：** `GraceArtAcceptanceHarness`相位13、15复用了“主手武器已变化”notice，现有`age>10`并不是本次应答证明。DSH须让每个拒绝检查等待独立的新快照/本次回执，例如在测试准备阶段清除notice并等待新客户端revision，再发送并观察后续revision；不要仅检查遗留notice。不要为了夹具给正常协议增加不必要的玩法状态。此夹具尚未进机，可能还有时序/世界前置问题，以真实失败为准修最少内容。

`Screen.mouseClicked`是引擎层鼠标事件分发，证明真实控件及网络闭环；须与人类物理鼠标输入验收区分，不能声称真人点击已完成。

## 6. C：实际Spartan、动态状态、坏资源恢复合同

本地原始JAR：[spartan_weaponry_unofficial-1.2.3-1.21.1.jar](D:/codexout/mcmod/docs/research-evidence/reuse-audit-20261013/spartan_weaponry_unofficial-1.2.3-1.21.1.jar)。SHA256：

`84d3ddafb519e2f5e03f2681ff52c30c92c9512181f61a7a7487399a0179888f`

无需从头搜包。当前metadata只有MC/NeoForge必需依赖，Spartan Shields为可选；若真实加载暴露其它缺类，再按具体官方前置处理，不能借此扩装模组清单。

待执行入口：

```powershell
.\gradlew.bat runClient -PclientSmoke -PproviderAcceptance -PartAcceptance --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

`providerAcceptance`仅在此运行加入精确哈希JAR。`SelectedArtAcceptanceHarness.providerTick`已写但未跑：10项`providerItem`真实注册ID核对→在真实生存物品栏绘制10件自有映射装备→用真实长弓物品使用流程触发状态→坏纹理资源包重载→legacy→恢复原资源包→新路径恢复。

验收必须记录实际Mod List、Spartan版本/哈希、10项注册ID和baked模型/纹理/pass证据。长弓实际模型为`spartan_weaponry_unofficial:item/longbow_iron_strengthened`及`_pulling_0/1/2`；需要所有可达状态真正绘制的记录，不用仅有JSON/图集存在代替。若本版实际只有SimpleBakedModel单层，就据实记录，不能虚构验证了多层或自定义loader。

坏资源夹具仅在隔离资源包中覆盖长弓根模型为不存在的纹理：`D:/codexout/mcmod/build/clientSmoke/resourcepacks/art-acceptance-bad.zip`。必须实际应用资源包并重载图集，证明新路径拒绝接管、旧路径完整绘制；随后取消该包并再次重载，证明本代缓存清除及新路径恢复。最后恢复原资源包选择并收束客户端。C未运行前不能将A的“缺Spartan合法回退”截图写成提供方成功。

## 7. 最终交付与后续队列

DSH以当前源码续写最小补丁，先完成需要修改的实现，再集中编译/测试，三个客户端严格串行。日志、截图、实际退出、模组加载、路由/深度证据逐项对应A/B/C；任何FAIL优先于exit0/通用PASS。没有完成标记、输出管道丢失或进程未知就列明缺证，不伪造完整验收。

应更新[SELECTED-ART-ACCEPTANCE-CLOSURE-20261016.md](D:/codexout/mcmod/docs/SELECTED-ART-ACCEPTANCE-CLOSURE-20261016.md)，重记实际JAR SHA、源码一致性、102张PNG与保护校验、测试源打包边界及全部命令终态。正常独立JAR安装、专服、多人、光影、PCL新实例导入及人类最终观感仍未验；本轮门禁不是整个整合包完成。

后续只排队：按[VFX运行收口工单](D:/codexout/mcmod/docs/VFX-RUNTIME-CLOSURE-WORK-ORDER-20261016.md)与[既有DSH VFX合同](D:/codexout/mcmod/docs/DSH-VFX-IMPLEMENTATION-CONTRACT-20261014.md)处理相机朝向、成功贴片后的碎块、火墙lane遮挡、culling高度及真实缺图轮廓回退；Combat Roll单输入/单结算替换、模组扩充和遗迹奖励继续后排。上述旧工单的“战斗直接实现”条款由最新分工覆盖：战斗给框架，DSH具体实现；本轮没有执行这些任务。
