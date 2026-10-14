# 战斗部门施工与验收回执

2026-10-12，北京时间。纯原版 / MC 1.21.1 / NeoForge 21.1.219 / Java 21。

## 施工登记

战斗单一写入：RemembranceRuntime、ClientSmokeHarness、SkillDriver/施放规则（需要时）、build.gradle、modpack/、根 README、docs/MODPACK.md。美术源模型、贴图、比例、item_skins、skill_poses、视觉显示不修改。

备份：docs/code-backups/20261012-combat-department/；修改前已有测试证据：docs/combat-evidence/20261012-before/。

构建排程：不运行 clean；保存证据后，依次 test build → serverSmoke → clientSmoke → 按锁表环境全模组 smoke → 打包。各构建及 syncArt、游戏进程串行。游戏进程仍在运行时不启动下一构建。

## 状态（实时）

- C-1：实机验收通过；黄金波实际按键、生成/传播/14伤害、拒绝/墙/去重已验。
- C0：工程实现、七模组哈希及本地候选归档门禁通过（2026-10-13）。
- C1：实现完成，事件表已交付；唤星时序已按源码修正并实测。
- C2：自动检查与代表场景实机通过（起手拒绝取消、硬直拒绝翻滚、尾段接翻滚、切武器清波）；液体/低顶/台阶/悬崖所有实机场景尚未全部验，不标完整收口。
- C3：八技实际伤害、服务端428共享检查/41数值/锻造已验；真实死亡/属性继承/FP补满/跨维度已验；实际保存退出重启（属性/+2厚重/FP37）已验。比例轮曾失败，已修复场景前置并保留全部原断言及失败日志。光影开启、多人性能、法器独立施法仍未验或未实现。
- 新P0握空气：进行中；接美术三剑柄区与哈希，修正原版柄部锚点，正在准备主副手、第一/第三人称、宽/细臂近景。用户尚未认可尺寸和普通武器质量。
- C4：build/packaging-smoke本地测试候选已生成并验内容；P0最新视觉、尺寸审阅和PCL新实例安装未验，不声称全包完成。
后续：材料获取 → 强化 → 战斗闭环；铃珠商店交易/持久化；属性 UI 服务端校验；JEI 动态产出说明。Boss/地图/新增战技另排。

## 实际证据更新

- C-1：客户端自动实机通过；docs/combat-evidence/client-20261012.log 的22:11记录按 C → 唯一非火焰波 → travel>0 → 一次14点伤害（20→6）、不足 FP 不扣费/不出波、墙后目标20 HP。未改 WaveFront 或视觉网格。
- C0：Fetch-Mods/锁表移除 EF，七模组哈希下载通过（fetch-20261012.log）；Build-Modpack 门禁改现用 mixin、模型/网格/图集/参数；build.gradle 按 client/server env 过滤。README/MODPACK/包说明修正。归档验收待生成。
- C1：COMBAT-EVENTS-20261012.md 已导出并修正统筹指出的唤星追击 WINDUP 起跳时机。没有扩大同步协议。
- C2：SkillCastState.recoveryCancelable 明确硬直/尾段，5=3+2、仪式2=1+1，总时长不变。ServerCombat 仅尾段接受主动取消/普通攻击/翻滚，重复序号门禁保留。Runtime 追踪产生实体，取消主动清理。SkillEffectEntity.spawn 改返回实体供追踪，视觉同步不变。边界单测八技通过；实机尾段衔接与所有强制中断场景仍未验，不标完成。
- C3：test build、独立服务端 smoke 已通过；128项/0失败/0错误/1跳过。首轮客户端20个画面fixture及网络完成。七模组客户端另在 client-pack-20261012.log 执行，包含唤星二段落地实测。原版操作、41件数值、配方静态/运行验证覆盖已有 smoke；逐件远程操作、真实死亡继承/存档重载、光影打开、多人与性能未验。

实际命令均使用 .\gradlew.bat、--offline --no-daemon、-Dorg.gradle.java.home=D:/JDK21；初次沙箱缓存锁拒绝已记录 build-20261012.log 后重跑成功。test build → runServer -PserverSmoke → runClient -PclientSmoke → Fetch-Mods → test build → runClient -PclientSmoke -PpackSmoke 串行，详细启动参数见日志。

美术比例补充：art/weapon-proportions 已存在草案；runtimeIntegrated=false，不把草案作为最终比例。收到最终 CODEX-TO-COMBAT-WEAPON-SCALE-20261012.md 后才接消费接口，倍率/握点仍由美术确定。

七模组验证：client-pack-20261012.log 22:20:23/25：唤星初段100→90、二段落地90→65、FP100→70，真实两次按键通过；黄金波动全部断言复验通过。Iris无选中光影包。第三方可选Mekanism/ImmersiveEngineering mixin类缺失警告及账号网络超时出现在日志，但游戏继续，最终所有网络检查完成，Gradle成功。

server-pack-20261012.log：锁表筛选服务端依赖后启动成功，428共享检查、41件数值/原版reach、强化/质变/重铸/错档拒绝/追忆锁定通过，无客户端类加载崩溃。

跳过1项：LocalSourceMotionTest.accepts_actual_local_candidate_when_available，build/elden-source/retargeted/profile.json 不存在，assumeTrue 跳过；本轮不导入源游戏素材，候选包不依赖它。下载后的七模组 versionId 与 SHA512 与修改前锁表逐项一致。

新增比例轮失败记录：client-proportions-save-20261012.log 四项失败（bound light attack / stale cancel rejected / real blade hits target once / full charge release），最终 Gradle runClient 非零退出。原因：33个视觉夹具最后为弩左手，原网络场景隐式继承其主手，却要求直剑右手。已显式重置直剑/右手/盾牌，保留原断言；onServer 对任意 RuntimeException/Error 标记失败，客户端下一tick抛异常阻止继续。此轮画面独立归档到20261012-proportions-current，功能全轮记失败，不当通过。

比例消费：WeaponProportions/WeaponAssets/EquipmentRenderer/WeaponDisplayTransforms/build.gradle 已接 profiles 到 assets/eldencombat/weapons/proportions.json，不改美术倍率。基于本地Minecraft源码 HumanoidModel 8+12+12像素=2格；PlayerRenderer .9375根缩放同时作用于人和手持物，第三人称相对比例中抵消，裸模型世界身高1.875。第一人称使用表的1.8构图基准。局部矩阵仍T(iconGrip)*R(-45deg)*S(逐件比例/实际displayScale)，没有长度归一；item_skins仍由美术维护，当前41项scale=1。profiles.hash=8ca944ad6b282e4d4843bc4d33bc82349bf3cee3e2c94af8e37bf7e30c31ed8d；包含比例接口的JAR哈希039b119c3f3ffec39dde57d0902d7f6b2404462c261aa79ac79c2089662a8ab7。开发runClient加载build/classes和resources，不把该JAR哈希称为已安装JAR。

用户质量/尺寸修订优先：候选包等待六代表款精修与用户尺寸审阅；当前比例为草案，未获用户尺寸认可。所有41件的显示与服务端reach独立。PCL路径已通过异步问题请求，未收到路径；当前无PCL导入证据。

备份补充：SkillDriver/SkillServerEvents首次detach小补丁遗漏预拷贝，已精确反向仅移除新增方法/调用重建可恢复原内容；说明见combat-evidence/backup-notes.txt。其它修改文件保留修改前副本。


C3存档实机：client-lifecycle-fixed-20261012.log 22:41通过真实死亡/重生、属性继承、keepInventory=true时武器组件保留、FP补满、实际下界来回/不补FP/取消瞬态；client-reload-20261012.log 22:44通过实际save→关闭Java→重新启动，属性(7,3,0,0,0)、直剑+2厚重、FP37保留。死亡FP曾失败（client-lifecycle-20261012.log），查NeoForge AttachmentInternals.onPlayerClone 在NORMAL复制附件后覆盖本模组原NORMAL补满；将本模组Clone处理改LOWEST并先恢复护符上限再补满，实测修复。失败门禁已在该失败轮证明：服务端异常→下一客户端tick异常→Gradle退出-1，没有假通过。

握持P0：原ICON_GRIP(.5,.5,.5)是护手附近，误当掌心。读取原版diamond_sword.png棕色柄部x=2..4/y=11..13，用像素中心(3.5,12.5)、纹理Y反转得到图标锚点(.21875,.21875,.5)。T(grip)后旋转/缩放，末端T(-模型柄点)；WeaponCorrections现以掌心锚点为旋转/缩放中心，避免改变尺寸把柄带走。美术三柄中心(-.5/-1/-2模型单位)从sword-grip-regions.json消费，只有当前模型SHA256匹配才启用；不写模型源或item_skins。近景宽臂/细臂正在验证，暂不宣称用户已认可。

C0打包器增加OutputDirectory以便在build/packaging-smoke验证归档，输出强制位于工作区；新增柄区与JAR内模型哈希门禁。该打包自测不替代用户批准美术后的dist候选或PCL导入。

用户最终三剑版本：新版straight_sword + 精修前great_sword/colossal_sword。此前新版大剑/特大剑截图标为unselected，只作过程证据；下一轮selected-side以最新柄区/profile哈希重拍。第一次侧视相机只设置了身体yaw，LivingEntity.getViewYRot读取head yaw，故仍是正面；修正测试相机head yaw±45后重新取证，不把之前正面标作45度侧视。

第一人称原版行为依据：build/moddev/artifacts/neoforge-21.1.219-sources.jar!net/minecraft/client/renderer/ItemInHandRenderer.java:400–410（摘录在combat-evidence/vanilla-first-person-branch.txt）。空主手才调用renderPlayerArm，地图另有分支；持普通剑使用item渲染。按统筹/用户方向，第一人称验构图和原版变换A/B，第三人称验实际掌柄近景，不新增第一人称玩家手臂系统。

掌柄空间取证：测试harness新增exportGripProof，从实际PlayerRenderer的当前arm ModelPart/cube取末端4模型像素掌区；复现玩家根(.9375)、实际arm pose/细臂半像素修正、ItemInHandLayer、左右手display.apply、-0.5、WeaponCorrections围绕锚点、heldScale、真实柄点的完整矩阵。输出palmToWorld/itemToWorld、柄区世界采样、柄中心世界坐标/掌区局部坐标和中心距离，中心必须进入真实掌区体积才允许画面夹具通过。仅harness诊断，不进发布JAR。

宽臂直剑右手当前帧：掌区局部px=[-3,6,-2]→[1,10,2]；柄中心局部px≈[-1.0000,8.5606,-0.6729]，inside=true，距掌区中心≈0.051315格。源码和完整世界矩阵/柄区证据在build/clientSmoke/grip-proof/04-ref-grip-straight-third-right.json。主/副手与攻防逐帧按同一门禁检查；近景是否满足用户观感仍交审阅，不由名义锚点直接宣称完成。

新增utility交接已读：新版spear/shield/long_bow及utility-grip-regions已由美术提供。当前长弓没有animations.json，现WeaponAssets静态bake+EquipmentRenderer静态网格路径不能证明动态拉弓。保持该项未完成，下一步接实际握区并验证原版BowItem射箭；需要美术提供已有四骨骼拉弓旋转/弦位移数据供消费，不自行改美术骨骼/网格。

## 2026-10-13 续工

施工排程：先备份到docs/code-backups/20261013-combat-continuation，归档宽臂世界坐标证据到combat-evidence/grip-wide-world-proof；utility握区接线 → test build → server原版弓射箭 → slim掌柄对照 → 本地packaging-smoke。没有clean，没有覆盖源美术。

宽臂三剑每件6个第三人称帧（主/副手、左/右、攻击/防御）全部柄中心进入实际末端4px掌区体积，中心距最大约0.05132格；美术整体观感仍待用户验收。工具握区现在按其SHA256消费。长弓使用自己的原版display时，局部图标握点由实际display的逆矩阵与原版钻石剑参照计算，避免直接套剑的固定图标点造成漂移；不更改服务端reach。

长弓动作数据需求已写COMBAT-UTILITY-MOTION-REQUEST-20261013.md供统筹转交美术，未自行改骨骼或制作动画。当前只有真实原版BowItem功能与蓄力状态取证，动态弓弦仍未实现。

## 复用优先收束（2026-10-13）

收到最新用户指令：先核查现成方案；保留成果，暂停新系统扩展。当前只收束已在执行的验证、打包内容与回执，不恢复Epic Fight，不迁移加载器。

长弓测试过程两轮失败均保留：server-bow-20261013.log（启动时立即搜未进入ticking chunk的箭，启动异常但Java退出0）；server-bow-fixed-20261013.log（verifyBusWiring的合成Post tick先于弓场景建立，触发测试自身null）。已将场景迁至已加载出生区，并仅在全部fixture建立后开始真实tick断言；保持生成箭及实际伤害两条断言，未弱化。新增Gradle serverSmoke门禁：日志必须有SERVER_SMOKE_COMPLETE且无SERVER_SMOKE_FAIL，证明第二失败轮正确返回BUILD FAILED。

utility握区消费和display逆补偿已编译/单测通过；细臂+utility最新渲染/盾背与弓弦仍未完成。长弓动态数据需求交统筹协调，暂停自行扩展新视觉系统。已有宽臂三剑18帧世界证据保留；新consumer改变后仍需细臂复验，不用旧结果冒充最新全通过。

长弓最终验证通过：server-bow-final-20261013.log 原版BowItem release创建箭，在真实服务器tick中命中指定Husk，100→93.112；记录SERVER_NATIVE_BOW_PASS及300tick SERVER_SMOKE_COMPLETE，Gradle BUILD SUCCESSFUL。这是原版弹药/释放/伤害的实机引擎验证，不代表动态弓弦或玩家真人按鼠标已验。

### 当前收束结果（2026-10-13）

C0：自动内容验收通过。build-final-20261013.log / build-package-refresh-20261013.log构建成功；package-20261013.log首次正确拦截long_bow新model+旧握区哈希，重同步后package-final-20261013.log全部PACK_PASS。Build-Modpack -OutputDirectory build/packaging-smoke 生成ZIP与mrpack（各899653字节），根manifest七依赖/正确MC与NeoForge，不含EF，载荷白名单及JAR资源/姿态/八技/三剑与utility握区哈希均通过。测试harness、世界与账号不进入归档。dist历史0.1未覆盖。

C3：最新130项、0失败/错误、1跳过；跳过仍为无本地源动作候选文件的LocalSourceMotionTest，不影响包依赖。原版BowItem真实箭伤害新增通过；server-bow-final-20261013.log同时有300tick完成标记、最终Gradle成功。最新utility/display逆补偿仅编译/自动检查通过，尚未补最新细臂/盾背/动态弓弦的全部实机证据，原已通过三剑宽臂资料不冒充新全轮结果。新的低顶/台阶/液体/悬崖与死亡出波场景已写入harness但尚未执行，继续标未验。

C4：本地测试候选实现完成、内容自动检查通过；PCL真实导入/下载/Java启动、新世界试玩与保存重进未验收。准备清单在build/packaging-smoke/INSTALL-CHECKLIST-zh.md。PCL路径仍缺；最终dist/正式发布未进行。

包SHA256（ZIP与mrpack相同）：8ea44449ef77839bef3f2885521f56facf38205ff89ca7932cf569702b5d44fe。
JAR SHA256：bd0d86f4beb39119032959d7146db0bd95e721899b4d3cf44dfb0f0ecf31696a。
精确manifest及白名单内容：docs/combat-evidence/candidate-manifest-20261013.json、candidate-contents-20261013.txt。完整和独立客户端开发实例日志不等同于该JAR已在PCL安装运行。

后续按复用工单决定范围；已有成长获取/铃珠经济、属性UI服务端、JEI动态产出待办仍保留，不继续从零扩建。用户选定新直剑+旧大剑/特大剑，尺寸、普通装备质量与其余视觉由用户及美术验收。


## R-C0复用审计交付（2026-10-13）

已提交COMBAT-REUSE-AUDIT-20261013.md。首选最小新增依赖Waystones21.1.46+Balm21.0.66+JourneyMap1.21.1-6.0.9；逐JAR元数据/官方SHA512静态核对范围允许NeoForge21.1.219。JMI1.9.1只是额外marker及创建客户端waypoint，弹出菜单为空，不满足普通玩家赐福旅行；需薄地图UUID请求适配与Waystones/Balm可取消Pre事件统一服务器校验。没有安装、没有运行候选、没有修改包锁或旧存档。

研究缓存、精确版本API JSON、JAR元数据、公开API签名/调用链及观察哈希：docs/research-evidence/reuse-audit-20261013/。R-C0末项明确未核实FTB精确JAR（403）、法术附加包精确JAR（SSL）/许可读取失败；不以缓存错误当支持。Lootr许可按官方区分代码MIT和资产ARR；JourneyMap API也不是MIT，依其授权使用未修改API；27法术自带mana，FP单一权威未解决，不直接纳主线。

现工程weapon lookup仅取path存在外部同名ID误配风险，记录为普通武器正式适配前的局部修复，不在研究轮擅改。所有赐福配额/记忆槽/技能数值仍为草案。等待统筹R-C1薄适配工单，不重新创建工程、不恢复EF、不从零扩建系统。
