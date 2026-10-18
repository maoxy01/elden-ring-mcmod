# DSH动作/VFX代码复核与局部修复

2026-10-17。依据人类本轮直接要求“审查它写的代码，并修复bug”执行这次局部修复；不从附件推导长期分工变化。美术及统筹线程只读查询均为idle，修改前无Java/Gradle进程，本轮未启动游戏、美术或DSH。

## 确认并修复

1. **未起跳也结算冲击**：SkillLanding原先在从未观察到离地、onGround仍为true时，8 tick后返回LANDED。现改为终止结果TAKEOFF_FAILED，运行时通过原有取消出口清理；不产生落地伤害/地裂、不额外退款，迟到移动包不能把失败改成成功。普通落地还需服务端脚底薄层方块碰撞支撑，避免空中的旧onGround直接结算；取消后本tick不再继续执行技能逻辑。
2. **重复配对采样失效**：VfxFixedHarness原先拍完clean后无条件推进步骤，repeats=4没有被执行；重复文件名又是`-clean1.png`，无法被仅识别`-clean.png`的落盘回调配对。现复用现有截图流程，以小型纯Java计数器VfxCaptureSequence仅在完整配对后计数，文件名改为`primary2-1-a.png`/`primary2-1-clean.png`；保留live/clean身份一致、逐样本唯一。两次burst之间暂时没有提交时继续等采样，整段仍受原FRAME_BUDGET约束，缺样按未完成步骤失败，不放宽像素门禁。
3. **挥臂动画重复发包**：本地NeoForge21.1.219合并源码的LivingEntity.swing(hand,true)明确调用broadcastAndSend，已包含施法者；DSH又手动发一次ClientboundAnimatePacket。删除额外发送和错误注释，保留原版挥臂出口。

五个既有文件的修改前副本保存在`docs/code-backups/20261017-codex-vfx-bugs/`，未用Git HEAD覆盖其它在途成果。新增代码只含采样计数器及其JUnit。

## 未采纳的推论与实际边界

- 水鸟/命定的追击阶段2/3不可达是配置事实，但一次ACTIVE内部已经自动产生三次burst；旧日志也有三实例及0..15帧提交。它不能直接解释后两挥不可见。
- 从第0帧起播是正常序列行为，截图取到第0帧不证明运行时只播弱帧；8 tick与2渲染帧也不是同一时间单位。
- dash的`.15/.22`是速度，不能直接求和当成累计位移。未据此改为逐tick传送、增加同时叠层、跳帧或延长视觉寿命。
- 本轮修正可确认的逻辑与诊断缺陷；原地蹦/三挥可读性及实际落地视觉仍需后续同一客户端场景验收。没有新的客户端运行证据，不能称三条人类观感全部修好。旧V1–V3、现成模组选型及新魔法祷告搁置状态继续保留。

## 集中验证

离线执行一次`gradlew.bat test verifyJarHasNoHarness --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21`，exit0，`:compileJava`、`:compileTestJava`、`:test`、`:jar`与JAR检查实际执行。最初沙箱内尝试因Gradle缓存锁权限被拒未运行测试，随后经自动审批允许缓存访问完成验证，没有绕过失败门禁。

- 当前36份JUnit XML：**242项，0失败、0错误、1跳过**；包括未起跳终止、无支撑不落地、截止前真实起跳，以及四组配对计数/命名/重置/错误顺序回归。
- 当前`build/libs/eldencombat-0.2.0.jar` SHA256：`9698C73D011545CD3843F18E2B57CB07E3F73875E751A41E6C47F1CC601E22E2`；1180条目，0个`*Harness*`条目。构建产物已更新，独立安装/客户端尚未验证。
- `art/vfx-2d`与`art/equipment-2d/full`共300个文件前后指纹相同：`585861AAD2D9D4258C40FA905BF71AF4E0303BD556C2E796A3D8B15FE6F3BB6A`。此处不扩大为重新审计其它466项保护清单。
- 修复所改既有文件的差异空白检查通过（按CRLF处理）；所有已启动命令已结束，Java/Gradle进程数0。

证据：[集中检查日志](D:/codexout/mcmod/docs/combat-evidence/codex-vfx-bugfix-20261017/unit-and-jar.log)、[结果汇总](D:/codexout/mcmod/docs/combat-evidence/codex-vfx-bugfix-20261017/result.json)。

## 同日后续：按人类要求启动游戏测试

人类随后直接要求“启动游戏测试”。重新只读核对美术/统筹idle且Java/Gradle为0后，串行运行两次真实MC1.21.1/NeoForge21.1.219客户端；两次均正常保存世界、退出，最终无Java/Gradle进程。JAR哈希仍为上文9698C73D…，本次没有再改实现或素材。

1. `runClient -PvfxSmoke -PvfxSemantics -PvfxSemanticsCases=3`：exit0，`VFX_SEMANTICS_COMPLETE: 3 skill(s)`。命定/水鸟均swings=3、instances=3、covered=3、channels=3/3，后续实例有0..15帧实际提交；黄金碎击departed=true，14tick后在平台Y=81.000结算。这里验证正常平地施放，没有额外在游戏内重跑“起跳完全受阻”的异常分支。
2. `runClient -PvfxSmoke -PvfxFixed`：exit0，`VFX_FIXED_COMPLETE: 13 controlled segments`，0条VFX_SMOKE_FAIL。11个正常场景各5组配对，有碎块的场景6组；均可比。两个负例模式也完成。本次新归档160张截图。原3个失败段现在分别为水鸟白天1159、命定夜间1595、水鸟夜间893，阈值仍300；这些是主体ROI差分，不能直接扩大为全部挥击的观感通过，也没有重跑“两次同段波动≤20%”门禁。

**目视仍未收口：**已查看黄金碎击、水鸟白天/夜间的本次截图，水鸟刀光可见；但[黄金碎击受控图](D:/codexout/mcmod/docs/combat-evidence/codex-vfx-bugfix-20261017/screenshots/vfxfixed-01-gold_breaker-primary2-4-a.png)里地裂出现时人物仍呈悬空姿态。观察者模式的跃起记录本身有apex=81.000/horizontal=0，不能把这个机位当正常玩家物理观感的替代，更不能以本轮数值通过宣称原地蹦/落地姿势同步全部修好。正常玩家机位语义测试与受控贴片截图分别成立；人物姿势/落地画面的同步和人类手动观感继续保持未验收。本轮不依据此图继续猜原因或改动画/位移。

本次证据：[运行汇总](D:/codexout/mcmod/docs/combat-evidence/codex-vfx-bugfix-20261017/runtime-result.json)、[动作日志](D:/codexout/mcmod/docs/combat-evidence/codex-vfx-bugfix-20261017/semantics-client.log)、[截图回归日志](D:/codexout/mcmod/docs/combat-evidence/codex-vfx-bugfix-20261017/fixed-client.log)、`semantics-exit.json`/`fixed-exit.json`及同目录screenshots。此前result.json的clientStarted=false仅记录游戏外修复阶段，此后客户端运行以runtime-result.json和两份退出记录为准。
