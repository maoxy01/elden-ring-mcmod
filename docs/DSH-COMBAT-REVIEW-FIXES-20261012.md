# dsh：实机故障审查与修复说明

日期：2026-10-12。工作区：`D:/codexout/mcmod`。

用户反馈：武器显示尺寸不正确，不能释放战技。截图为本轮原版战斗测试。本文是代码审查交接，不是已修复报告；Codex 本轮未修改战斗代码。

## 分工

- dsh：修复输入事件、服务端战斗循环、状态同步、HUD 的蓝条接入及相关战斗测试。
- Codex：修复武器显示尺寸、握持点及显示场景差异，继续维护特效美术。
- 共享文件修改前说明范围，避免覆盖对方改动；不要恢复 Epic Fight，也不要沿用旧文档中“这些类不要注册”的过时要求。

## P1：客户端按键未接到事件总线

位置：`src/main/java/com/dsh/eldencombat/client/ClientCombat.java`，类声明约第 35 行，`onClientTick()` 约第 136 行，`SKILL.consumeClick()` 约第 168 行。

`EldenCombatClient.onRegisterKeys()` 注册了 C、R 等 KeyMapping，但实际消费按键的 ClientCombat 只有方法级 `@SubscribeEvent`，没有类级 `@EventBusSubscriber`；当前代码也没有手动注册它或直接调用其事件处理方法。

结果：按键设置能看到战技键，但按 C 不会通过这条路径调用 `send(IntentType.SKILL, ...)`；R 切换战斗模式同样失效。这里不是耗蓝数值问题。

修复建议：ClientCombat 的处理方法为 static，可以添加限定 `Dist.CLIENT` 的自动订阅，订阅游戏事件总线，不是 MOD 总线。也可以从客户端安全入口手动注册类，但只能选择一种方式，防止重复发送请求。清理类头已过时的 EF 残留说明。

## P1：服务端战斗循环未接到事件总线

位置：`src/main/java/com/dsh/eldencombat/server/ServerCombat.java`，类声明约第 48 行，`INSTANCE` 约第 50 行，实例方法 `onServerTick()` 约第 75 行。

ServerCombat 同样没有自动订阅，当前主类没有注册 `ServerCombat.INSTANCE`。其 `@SubscribeEvent` 方法是实例方法，不能只给类添加用于静态方法的自动注册便认为完成。

注意：网络注册中的 `ServerCombat::handleIntent` 已接线。这能执行请求处理，不等于服务端 tick/受伤/离线等事件处理也已注册。`SkillServerEvents` 另有自动注册，其独立战技推进不能代替 ServerCombat 的普攻、翻滚、精力和状态同步循环。

修复建议：在公共初始化中将 `ServerCombat.INSTANCE` 注册到 `NeoForge.EVENT_BUS`，保证只注册一次；检查该类全部实例事件处理方法都生效。不要将此类放在 MOD 事件总线。

影响：普攻命中窗口、翻滚位移、动作结束、精力恢复和部分状态清理缺少持续执行。只修 C 键不足以使整套战斗正常。

## P1：武器显示尺寸与握持点混用

位置：`src/main/java/com/dsh/eldencombat/client/EquipmentRenderer.java`，`renderByItem()` 约第 89 行，最大边长缩放约第 101 行，几何中心平移约第 115 行。

当前方法没有按 `ItemDisplayContext` 分支，所有场景统一按最大边长归一到一格，并移到模型几何中心。模型加载/预烘焙已经把坐标转换成方块单位，不应把手持场景也当作 GUI 图标处理。

结果：长兵器与大剑的原始长度比例被压缩；原点是握持点的模型被重新按中心摆放，握把位置偏移。截图中的尺寸问题与此路径一致；快捷栏图标是否还受观察方向影响，需要单独实机检查，不把每个视觉问题都归为同一原因。

Codex 负责修复：GUI/FIXED 等展示场景使用各自适配策略；第一/第三人称围绕握持点使用合理比例与独立姿态校正。不要为了恢复战斗而由 dsh 再改此文件或统一放大所有模型。

## P2：HUD 未注册且没有 FP 绘制

位置：`src/main/java/com/dsh/eldencombat/client/CombatHud.java`，类声明约第 22 行，`onRender()` 约第 31 行。

该类也只有方法级事件订阅，没有自动/手动注册。即使补注册，现有绘制也只有 HP、精力、药瓶/状态文本，没有 FP 蓝条，不能视为已实现蓝条界面。

修复建议：限定客户端、游戏总线、仅注册一次。FP 使用已有 `ClientSkills.fp()`、`maxFp()` 或 `ratio()` 镜像，不读写服务端 attachment、不引入怒气充能。用户测试要求无限蓝，正常模式仍扣蓝。

HUD 的布局与配色可由 Codex 后续打磨；本轮 dsh 至少先保证数据与绘制接线有效。

## 不要误判武器绑定

`server/skills/SkillCasting.specForMainHand()` 约第 22 行按 `remembrance_parameters.json` 的 itemId 匹配主手，没找到即拒绝。当前只绑定八件追忆武器，不是全部 33 件类型武器。

截图外观像普通戟，但不能仅凭图片确认物品 ID。验收先拿明确绑定的神躯化剑、水鸟对应刀或黑剑；普通戟未绑定不等于这八个技能全部没实现。拒绝时建议给出简短原因或调试日志，区分非绑定武器、战斗模式未开启、FP 不足与阶段不允许。

## 验收顺序

1. 启动当前原版版本，日志无事件订阅或 mixin 注入错误；专用服务器不能加载客户端类。
2. 在按键设置确认 C/R 无冲突。按 R，服务端状态和 HUD 应切换；不能只有 KeyMapping 已注册的证据。
3. 主手拿神躯化剑，按 C：检查请求被服务端接受、阶段推进、挥剑姿态、黄金波动实体/特效产生以及收招恢复。横斩独立光带已按用户要求删除，不要重新生成。
4. 正常模式校验成功施放扣一次 FP，失败不扣；`-PmanualSkills` 单人测试保持无限 FP，不扩大到专用服务器或默认玩法。
5. 测普攻至少一次有效命中、翻滚实际位移、精力恢复、切武器/受击/死亡中断；再测追击窗口，防止因补注册而重复扣费或重复判伤。
6. 逐件验证八武器绑定。至少一次两人联机确认旁观者姿态与特效同步。
7. Codex 另验收 GUI、第一人称、第三人称、左右手和掉落展示的尺寸/握持；战斗修复不要阻塞这条独立工作。

## 测试与交付

目前单元测试偏向纯规则、数据格式与资源检查，没有覆盖上述事件总线接线，因此构建通过不能证明按键或 tick 会运行。

补充输入/总线接线回归检查及实际事件触发证据，尤其是“每次输入只发送一次”“每 tick 只推进一次”。静态注解检查不能替代实机或总线触发测试。

```powershell
.\gradlew.bat build --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
.\gradlew.bat runClient -PmanualSkills --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

日志：`build/manualSkills/logs/latest.log`。2026-10-12 本轮日志证明 41 个武器缓存、8 个姿态和8个技能参数成功载入，但不证明按键请求被接受。此次结论来自代码调用链与用户反馈，不宣称已通过修复后的游戏验收。

交付时请写明修复文件、采用哪种总线注册方式、重复注册防护、实际施放/命中证据和残余问题；不要只报单测数量。有关当前美术见 `IRREGULAR-IMPACT-FX-HANDOFF.md`、`VANILLA-SLASH-FX-HANDOFF.md`。
