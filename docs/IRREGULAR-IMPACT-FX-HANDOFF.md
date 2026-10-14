# 不规则冲击与扩大刀光

2026-10-12。Codex 美术改动，沿用 dsh 当前纯原版战斗，不恢复 Epic Fight。

## 表现

- 黄金碎击：规则方框改成确定性的不规则方块圆边界，20 块起伏破裂边缘、金色阶梯地裂、向外翻起的碎块。范围先展开后稳定，末段淡出。
- 唤星聚拢：同类不规则紫色地裂圈缓慢内收，碎块向中心悬起。
- 唤星重击：紫色裂缝向外展开，碎块外抛。与聚拢方向不同。
- 水鸟及命定之死：单道刀光长度从最大约 4.16 格改为 7.54 格，约 1.81 倍；增大横向、垂直和前后层间距。水鸟维持细银白，命定之死更宽、红黑；咒血刀光范围不变。
- 扩大刀光的视锥裁剪包围盒，以覆盖相机朝向贴片；不修改实体伤害框。边界使用常数上界，查询不创建刀光列表。

地面破坏是纯视觉：不调用 destroyBlock、不生成掉落、不生成真正 FallingBlock、不写世界方块。实际游戏碎块沿用实体同步的落点地面 BlockState，唤星聚拢也采样该状态。碎块翻转、升起、外抛/内收、缩小后清理；不提供物理碰撞。预览里的灰块只是材质替身，非实机材质截图。

对用户提供两个 JAR 做了只读资源目录/类接口检查：core 包含 `MeteorImpactVFXManager`、`ChargePunchVFXManager` 等独立冲击表现入口，flight 主要为飞行渲染接口。这是组织方式参考，没有复制类或资产，也没有宣称已运行参考模组、复现其全部破坏行为。

## 协作接口

现有 GOLD_IMPACT、GRAVITY_PULL、GRAVITY_IMPACT 类型及线协议 ID 未改；仍由 dsh 控制触发、生命周期、位置、伤害和范围。视觉轮廓不是精确命中边界，大刀光不等于扩大判伤；后续由 dsh 结合手感与命中契约决定是否调整数值。

若要真实地形破坏，由 dsh 明确实现独立开关、权限/保护区、可破坏方块集合、方块实体保护、单次数量/半径预算、掉落和同步规则。当前美术版不承担这项功能，不因贴片或地裂碰到方块就删除地形。

改动文件：`effects/SkillEffectMesh.java`、`effects/SkillSlashSprites.java`、`client/effects/SkillEffectRenderer.java`、`entity/skill/SkillEffectEntity.java`、对应美术测试及预览导出产物。`SkillEffectEntity` 仅增补聚拢地面材质采样与裁剪边界，其余战斗接口保持。

## 验证

- 完整离线 build 成功，含最新 dsh 原版战斗改造；69 项测试中 68 通过、1 跳过、0 失败。跳过测试为原有本地源动作条件测试，不算实机验证。
- 现有全部几何预算、有限值、生命周期、黄金波动固定成形、授血方形契约通过；新检查验证不规则边界、碎块传播方向、大刀光范围及裁剪覆盖。
- 桌面/手机预览、切换、播放、相机操作、非空像素差与页面溢出检查通过。
- 当前每个效果网格仍上限 96 方块；新冲击效果约 70 个几何方块及 10 块材质碎块。没有增加新实体或每顶点网络同步，不宣称已做 FPS 测量。
- 尚未游戏实机验收；截图仅为共享纯几何数据的 Three.js 审查画面。

预览 `art/skill-fx/preview.html`；截图 `gold_impact-irregular-desktop.png`、`gravity_pull-irregular-desktop.png`、`gravity_impact-irregular-desktop.png` 及更新的两张刀光图；QA 为 `art/skill-fx/qa.json`。

备份：`docs/code-backups/20261012-irregular-impact-fx/`。这份交付补充 `VANILLA-SLASH-FX-HANDOFF.md`，其“完整构建暂时阻塞”是早前迁移中的历史状态，当前已通过构建。
