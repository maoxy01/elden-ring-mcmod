# 精细武器资产

33 件主要类型代表模型，22 种战技设计。游戏代码由其他 agent 接续，这批尚未接入 Minecraft。

后续优先批次：`art/remembrance/preview.html` 有 8 件具名追忆武器和独立 2048 图集的精细化第二版。它们不是本目录通用模型的简单换皮，也尚未进入游戏。顶部“追忆武器”链接可切换审查。最新战技方案允许跃起攻击，处决仍禁用；本目录 33 件模型与现有地面招式不变。

打开 `preview.html` 审查全部模型或选择单件旋转查看。每件有 `.bbmodel` 与 `.model.json`，共用 `weapons.png` 的 512 x 512 图集。玩家保留自己的皮肤，这里不提供替代玩家模型。

武器细节独立于 Boss 的粗块风格：刀刃分层收尖、护手、刀镡、缠柄、铆钉、镶嵌、盾面纹章；细节不增加活动关节。连枷/软鞭各三节，附带机械动作草稿。弓弩仅静态模型，弓弦拉伸尚未制作。这里没有玩家攻击或战技动画。

16 单位 = 1 方块，握持原点 `[0,0,0]`；旋转 XYZ，机械关键帧是初始旋转上的增量。真实 MC 手部对齐、第一人称与多人动作尚未验收。

再生成：在 `art` 安装 package.json 的工具依赖后执行 `npm run build:weapons`。类型轮廓源定义在 `tooling/weapons.mjs`，精细结构在 `tooling/refine-weapons.mjs`，图集与导出在 `tooling/build-weapons.mjs`。Blockbench 修改应回写源定义，否则再生成会覆盖它。

验证：`node tooling/verify-weapons.cjs <含 playwright 与 pngjs 的依赖目录>`。结果为 `preview-validation.json`，审查图在 `previews`。这只是美术/浏览器验收，不是游戏功能测试。

代码方案：`D:/codexout/mcmod/docs/WEAPONS-AND-ARTS-HANDOFF.md`。`catalog.json` 的 preset 和默认战技都是拟议映射；`custom:*` 不是已注册能力。

自制类型代表资产，不声称精确复刻法环所有具名武器。Three.js 仅用于离线预览，许可证见 `THREE-LICENSE.txt`。
