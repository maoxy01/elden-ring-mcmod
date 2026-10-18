# 第一人称武器展示修补

2026-10-18。人类直接要求参考三叉戟及其它模组修正握姿，尽量展示武器全貌。本工单由Codex直接修补，不启动DSH或美术。派工状态只读核对：美术回合已结束、命令均有终态；本轮不修改任何PNG或模型几何。

## 参照与原因

已核对实际实例`D:/PCL/.minecraft/versions/Elden Combat 0.2.0 - Vanilla Combat`：旧JAR为4d8ec7dd…，FOV默认70，resourcePacks为空。截图不是由外部资源包覆盖引起。

使用固定1.21.1原版handheld、trident_in_hand与ItemInHandRenderer/GameRenderer源码，以及已锁定Simply Swords 1.70.2的template_longsword、template_claymore、big_handheld。三叉戟是builtin/entity专用立体模型，平移[-3,17,1]不能直接套到像素贴片；Simply Swords的第一人称显示尺寸与世界尺寸分别配置，XY与Z也分别考虑。没有复制外部模组贴图。上游入口：[Simply Swords源码](https://github.com/Sweenus/SimplySwords)。实际参数以本地固定JAR为准，不追随仓库最新分支。

自有路由先前把世界长度直接用于第一人称；部分长武器还朝向近裁剪面，导致画面边缘只剩放大的局部。外部提供者缺席时又会走另一条旧BEWLR路径，单改2D路由无法覆盖截图中的所有武器。

## 修改范围

- 24把自有武器、48个左右手上下文：保留原握点与刃口修正，单独计算展示缩放、向内/向前的位置、露出正面的角度。测量全部手持状态的透明轮廓边界，在4:3、16:10、16:9、21:9下拟合静止手持画面。弓、弩炮和双头剑按中握类型安排展示中心。
- 10类外部模型缺席的旧渲染回退：仅第一人称撤去该物品的旧display，保留上层换物、挥击和晃动；绕真实握点安排展示尺寸。盾牌、药瓶、第三人称、GUI与世界尺寸走原路径。提供者存在时继续复用提供者自己的显示。
- 参数写入display-overrides并同步routes，重复生成不会反复缩小或旋转。所有humanAccepted仍为false。

源码：`tools/equipment2d/calibrate-first-person.py`、`src/main/java/com/dsh/eldencombat/client/EquipmentRenderer.java`；封装脚本`tools/equipment2d/package-first-person.py`。新增变化没有触碰战技、伤害、FP、网络或第三方JAR。

## 验证与安装

48个上下文投影检查通过；10类回退的20个左右手包围盒投影也在屏内。校准工具重复运行，routes哈希保持一致。投影基于静止姿态和70度手部投影，不是游戏截图，不能证明挥击、蓄力、疾跑晃动、光影和人类观感通过。

由于整机提交余量不足，未启动完整Gradle或游戏。采用一个受限javac编译单个渲染文件，堆256MiB、Metaspace192MiB、代码缓存64MiB、直接内存32MiB；编译前另要求4GiB提交余量、2GiB物理内存且无Java。编译exit0。客户端12GiB提交/4GiB物理门槛保持原值，本轮实际检查拒绝启动。没有重新运行JUnit，不把此前246项回归套到新JAR上。

已在已验证的4d8ec7dd…基线JAR上封装此次显示资源和编译产物，并逐条目核验：342张包内PNG字节完全一致，模型其它上下文及几何不变，无Harness，ZIP CRC完整。当前JAR SHA256：`bd4e648ea9866bfb77ff59204a92e2eeea802b57405e55d5a18f6d1a5984d769`。

已确认无Java后，原子替换用户实际使用的`D:/PCL/.minecraft/versions/Elden Combat 0.2.0 - Vanilla Combat/mods/eldencombat-0.2.0.jar`，并复读哈希一致。原JAR备份在`build/firstperson-framing-20261018/installed-before.jar`；玩家存档、选项、模组集合均未改。修改包含客户端类，下次完整重启游戏生效，F3+T不能替代重启。

单JAR与回执：`dist/firstperson-framing-20261018/`。更新的整合候选包：`dist/expanded-20261018-firstperson/`，保留原30个Modrinth引用与6项CF精确补装规则；正式锁不提升。

证据在`docs/research-evidence/firstperson-framing-20261018/`：projection-checks.json、legacy-projection-checks.json、javac.log、client-budget.log、RECEIPT.json及INSTALL-RECEIPT.json。下一步是内存条件允许后，在实际PCL实例检查截图对应武器、左右手与蓄力/挥击；当前不标记为人类视觉验收完成。
