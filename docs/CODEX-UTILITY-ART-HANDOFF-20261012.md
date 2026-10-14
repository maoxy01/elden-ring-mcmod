# 矛、中盾、长弓精修资产接入交接

日期：2026-10-12。旧大剑、旧特大剑继续保留；直剑新版保留。

2026-10-13更新：已补 `long_bow.animations.json` 与 `long_bow.draw.json`，梢端补到弦附着点并刷新模型hash、profiles与握区。长弓现为159方块，1908是六面理论三角上限。此前“没有动作数据”的状态已改变，但旋转/两段弦的游戏消费仍未接线。具体接口见 `CODEX-BLADES-AND-BOW-HANDOFF-20261013.md`；不可把刚性string整体平移当作拉弓完成。

同步当前 `art/weapons/` 的 spear/shield/long_bow 模型与 Blockbench、矛/盾 mesh、共享图集和 catalog。图集字节未改，正反面新材质分区已由 faceUVs 表达。不要使用旧缓存网格，也不要只复制新 model 而继续读旧 mesh。

源定义是 `spear-shield-bow-refinement.mjs`；独立重建入口 `apply-sword-refinement.mjs --utility`。完整 flatten 管线已消费该定义。本批备份位于 `art/backups/spear-shield-bow-before-refinement-20261012/`。

新的比例包围盒和握区已导出：`art/weapon-proportions/profiles.json`、`utility-grip-regions.json`。模型单位为 16/格，mesh positions 已为格。主轴 +Y，厚度 Z，原点不变。矛头宽度有改变，但总长不变；不能由新装饰边界自动改变服务端 reach。

握区 JSON 给出当前几何中的候选锚点：矛 [0,0,0]，中盾背面 [0,-0.5,-0.5]，长弓 [0,-0.5,0]（均为模型单位）。这些位置已静态检查落在几何上，不能替代最终手掌对齐。中盾背部握带画在板内，实际掌心与前臂位置需显示层按盾背方向校准。

长弓保持原有四骨骼 parent/pivot/rotation。不新增根节点 prebaked mesh，避免弓臂和弓弦失去活动能力。当前资源目录没有 long_bow.animations.json，不能据此声称拉弓动作已经制作或工作；请检查运行时实际使用的机械动画路径，并拍蓄力前/中/满弓三帧，检查弦、弓梢和手部接续。

离线对照在 `art/weapon-proportions/utility-review/preview.html`，截图与浏览器结果同目录。静态检查已覆盖45件；本批矛/盾868/544三角，弓1932是全部六面理论上限，不是实测 GPU 提交量或帧率结论。

轮廓特例门禁已收紧：仅这三个注册 ID 能使用 utility_v4，revision/source/设计边界均按独立固定值核对。其他装备不因挂此标签而跳过原门禁。当前模型、82 个比例输入哈希、三件握区静态检查均通过；这不表示用户已认可新版或游戏手持已验。

接入验收：第一/第三人称主副手、classic/slim、附魔、资源重载；盾面/盾背方向、矛握段与掌心、弓弦蓄力动作；用户选定旧大剑/特大剑的资源不回退到已撤回的 R4 设计。保存当前 JAR 哈希与实际画面，不以网页结果代替游戏手持验收。

本轮美术未运行 Gradle、未覆盖 src 运行资源或战斗矩阵。同步和构建与其他开发串行安排。
