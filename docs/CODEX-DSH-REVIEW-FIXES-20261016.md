# Codex审查BUG修复回执

2026-10-16。用户明确要求Codex直接修复，覆盖此前“本轮仅设计、具体编写交DSH”的本次执行分工。核对美术idle/最新回合completed后进行；没有派工或启动其他部门。

对应 [4项审查](CODEX-DSH-CODE-REVIEW-20261016.md)，修改前备份在docs/code-backups/20261016-review-fixes。

| 问题 | 本轮代码修复 |
|---|---|
| inventory键错误、动态状态不在顶层表 | EquipmentRouteSnapshot.inventoryModel把模型JSON的item/前缀与物品inventory键分开；新增additionalModelIds在资源存在后注册可用提供方/原版状态的standalone键；EldenCombatClient接入注册。动态状态不被误当注册Item，也不会无条件注册缺失的Spartan模型 |
| 战斗盾不进入格挡姿态 | EquipmentStateResolver从被绘制玩家的ClientCombat服务端镜像读取combatMode/guarding/action；只在NONE且为选定盾栈时启用，选手沿用服务端Weapons.SHIELDS标签；非战斗模式保留真实原版物品使用判断，无本地按键猜测、无第二次防御结算 |
| 创造玩家阻断Boss索敌 | MargitEntity把存活/非旁观/非创造条件放进最近玩家查询谓词，创造玩家不再先占最近结果 |
| 缺图占位模型阻止回退 | 新增EquipmentModelResources检查本代自有JSON/父模型/纹理及别名引用；renderer缓存前还检查baked quad是否引用missing sprite。无效结果缓存为null，绘制前返回旧路径；新烘焙/重载代数使缓存失效，允许恢复 |

修改范围为上述5个既有Java文件，新增1个资源检查类与1个回归测试类。未改素材、正式路由、物品注册ID、组件、伤害/FP或旧存档。

## 集中验证

全部实现写完后集中验证，没有运行中逐步改游戏：

1. `gradlew.bat compileJava test --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21` → BUILD SUCCESSFUL，退出码0，全套测试通过。日志：[全套验证](combat-evidence/codex-review-fixes-20261016.log)。
2. 最后将持盾手筛选与服务端标签完全对齐后，`gradlew.bat compileJava test --tests '*Equipment2dRegressionTest' --tests '*Equipment2dRoutingTest' --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21` → BUILD SUCCESSFUL，退出码0；**2套/13项/0失败/0错误/0跳过**。日志：[定向复跑](combat-evidence/codex-review-fixes-focused-20261016.log)。

新增5项回归分别覆盖：实际inventory键；原版动态状态注册及缺Spartan不注册；inventory基础模型+standalone动态模型的真实适配器解析与缺资源拒绝；战斗盾镜像在非选定栈/松开/攻击/破防时不格挡；自有JSON缺失/损坏、纹理缺失、恢复及父链循环。测试使用内存资源管理器和模拟baked表，不冒充真实GPU/游戏验收。

定向XML和本轮7个源码文件哈希已保存在 [证据目录](combat-evidence/codex-review-fixes-20261016)。首次普通沙箱构建因Gradle全局缓存锁无写权限未启动成功；取得工具权限后两次构建均正常完成，日志以实际成功运行记录为准。现有API弃用警告未扩大修理。

## 未验范围

本轮没有启动客户端、专服或Harness；未声称提供方loader/实际图集、玩家观察者格挡画面、缺图重载画面和混合模式多人Boss已实机通过。Boss筛选修复已对照本地1.21.1 EntityGetter源码确认谓词在距离比较前执行，但未添加伪造多人PASS测试。

下一次集中进机应优先核对：原版直剑/弓弩确实走新模型；有Spartan时长弓切态；战斗盾自己/观察者/双手及破防；创造观察者更近时Boss仍索敌生存玩家；坏资源→旧回退→恢复后新路径。人工观感与其他装备/VFX/整合包工单不因本轮测试通过自动完成。两次验证命令均已结束。
