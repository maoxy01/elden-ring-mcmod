# 验收记录 — 2026-10-19

交付环境：Windows、Blockbench 5.2.2、Minecraft 1.21.1、NeoForge 21.1.219、Java 21。插件已作为本地文件安装，持久记录路径为 `D:/codexout/mcmod/tools/blockbench-pose/dist/eldencombat_pose.js`。

| 检查 | 结果 | 证据 |
|---|---|---|
| 八个动作及八件真实持物资源 | 通过；模型与 PNG 校验通过 | `blockbench-live.json` |
| Java 解析、插值、阶段回退 | 20,160 个旋转值通过 | `java-parity.json` |
| 服务端真实阶段状态 | 485 个流程 tick 通过 | `java-parity.json` |
| Blockbench 原生动画与工程往返 | 八个全部通过；720 个原生旋转值对照 | `blockbench-live.json`、`projects/` |
| 原生编辑、撤销、重做、保存、备份、重开 | 通过；测试在独立副本执行 | `native-save.json` |
| 实际界面菜单与控制按钮 | 打开对话框、逐 tick、慢放、暂停、循环通过 | `ui-controls.json` |
| 外部修改与不支持曲线保护 | 拒绝覆盖／导出 | `native-save.json`、核心测试 |
| 真实游戏对照 | 8 个技能、432 组实际姿态／手持矩阵 | `game-live.json` |
| 游戏与编辑器空间对照 | 2,160 点通过；最大误差 0.00004601 模型单位，阈值 0.0001 | `game-blockbench-parity.json` |
| 正式动作完整性 | 八个源文件 SHA256 与验证前一致 | `source-integrity.json` |

游戏验收通过额外 source set 运行，未修改正式 Java 源文件；运行目录为 `build/posePreviewAcceptance`，使用复制的测试世界，完成后自动退出。第二次有效运行已修正测试账号细臂与标准宽臂参考模型的差异。持握矩阵来自真实 `PlayerModel`、真实 baked weapon、`EquipmentDisplay`、`ItemThickness`；不是单独重写一份矩阵公式当作游戏结果。

实际对照发现并修复了 Blockbench 默认非居中网格带来的 X/Z 偏移，以及重复选中同一阶段后原生预览播放标记未恢复的问题。最终预览还等待纹理解码并同步相机控制器，避免首次打开／截图缺少武器。

`previews/` 为工具里的标准宽臂参考人物，`game/` 为实际游戏角色截图。游戏截图使用该测试账号的外观，可能是细臂及不同皮肤；它们用于观察实际动作和武器，不做两张图逐像素等同的结论。432 组矩阵对照明确使用 Minecraft 实际标准宽臂模型。

验收边界：这是姿态与手持武器的离线工具。未验证多人网络延迟、第一人称、移动／潜行叠加、粒子、伤害碰撞或所有第三方渲染模组组合；原项目武器美术的最终人工接受状态没有因本次测试而改变。

原有武器工作标签不关闭。此任务生成的临时动作标签在保存恢复副本后正常关闭；恢复副本只保存在 `session-recovery/`，不作为产品示例打包。
