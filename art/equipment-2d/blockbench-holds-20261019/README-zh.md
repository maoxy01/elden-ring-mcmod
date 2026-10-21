# Blockbench 持物显示校准

本轮采用28件总工程对应的94张现有原生导出，未重新绘制素材。原始总工程与有分叉的咒剑独立工程保持原样；本轮咒剑按总工程的二次优化版接入。采用前full、姿态配置和厚度合同在 `backup/`。

## 在 Blockbench 继续调整

1. 打开 `projects/` 下对应的 `.bbmodel`，例如 `great_sword.bbmodel` 或 `great_bow.bbmodel`。这些是独立的 Java 物品显示副本，包含实际纹理、几何和可撤销的校准记录。
2. 切换“显示调整”，选择第一人称右手或左手。只改该槽位的位置、旋转、缩放，不移动编辑模式的几何来补偿持物偏移。第三人称用于对照角色比例。
3. 保存工程。另从“文件 → 导出 → Java 方块/物品模型”导出到 `exports/同名.json`；这是同步器读取的原生显示数据。两者姿态不一致时同步器拒绝回写，避免误用旧导出。
4. 在项目根目录执行 `tools/equipment2d/sync-blockbench-holds.py`，然后执行 `build-route-config.py`，按现有受限内存构建流程生成JAR。不能把保存bbmodel当作已更新游戏实例。

本轮由 `native-holds.js` 在真实Blockbench中使用DisplaySlot及Undo事务调整，使用 `Preview.screenshot` 原生WebGL截图。`projects/`为原生保存，`exports/`为原生Java模型导出；同步器不重新拟合姿态。

## 预览与验收范围

- 28件装备、112张基础左右手/第三人称截图，另有弓拉弦及弩炮装填的28张状态截图。`capture-validation.json`记录非空检查和原始图像哈希。
- `native-firstperson_righthand-contact.png`、`native-firstperson_lefthand-contact.png`是原生截图的排版图：裁去预览窗口外框、加中性底色并缩放排列。不是Minecraft截图，也没有重新绘制武器。
- 初次批量加载曾因纹理异步未完成得到空图，已加入实际图片加载完成及截图非空检查，并重拍受影响视图；不把空图计为通过。
- 左手另按其相机视角打开武器正面，未镜像修改PNG。握点仍来自作者登记位置；允许柄尾自然出画，不要求武器全身悬在视野中间。
- GUI使用独立16/32图标；盾牌预览的正背面使用各自素材。原版及第三方提供方的14条路线不在这28件编辑工程中。
- Blockbench预览不包含游戏挥击、光影、其它模组渲染及最终玩家观感；游戏验收仍待完成。
