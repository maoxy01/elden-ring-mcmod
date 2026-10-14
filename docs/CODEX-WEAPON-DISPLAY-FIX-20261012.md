# 武器显示与战斗接线修复结果

2026-10-12。用户授权修复后重新读取共享工作区，发现 dsh 已完成输入、ServerCombat 单例注册、蓝条与拒绝原因提示。本轮保留这些并发修改，未重复注册，也未恢复 Epic Fight。

## Codex 改动

- 新增纯数学 `core/WeaponDisplayTransforms.java`，区分显示场景。
- `EquipmentRenderer` 的精细武器不再全部压缩到一格/几何中心：第一与第三人称围绕资产主握持原点绘制，保留左右手区别；第一人称基础倍率 .65，最大长边 1.6 格；第三人称基础倍率 .9，最大长边 2.8 格。这是待用户实机微调的初始值，不改变模型或判伤。
- GUI 图标使用斜置姿态，并按旋转后的投影尺寸适配 0.88 格，中心定位。固定展示与地面掉落另行适配。
- `item_skins` 的握持校正只用于手持，不再同时旋转/缩放 GUI 图标。药瓶旧渲染路径保留，不跟随武器策略改变。
- 蓝条 HUD 整组上移，避免与原版生命/饥饿/快捷栏区域重叠。
- 新增四项数学回归检查：手持握持原点、旋转后图标中心/边界、不同武器长度与视角比例、左右手镜像和掉落尺寸。

## 验证边界

完整离线 build 成功：73 项测试中 72 通过、1 跳过、0 失败。跳过的是已有本地源动作条件测试。数学测试不等于真实摄像机/所有武器实机尺寸已经验收。

运行入口 `tools/Run-ManualSkills.ps1` 使用 `-PmanualSkills`，不使用旧源动作开关，无限蓝仅为本地手动测试。近期工作区清理移除了旧测试世界；若快速进入找不到存档，需要用户创建新的测试世界，不恢复/覆盖用户存档，也不自动施放战技。

验收：按 R 开启战斗模式，主手拿八件追忆之一，再按 C。普通类型武器尚未绑定追忆战技时会显示拒绝原因；不要用普通戟作为黄金波动是否能放的测试武器。

日志 `build/manualSkills/logs/latest.log`；本轮启动输出 `build/manual-display-fix.stdout.log`、`manual-display-fix.stderr.log`。不因构建通过就宣称实际按键施放或命中通过，仍需用户手动反馈。

显示/HUD 改前备份 `docs/code-backups/20261012-weapon-display-fix/`。dsh 接线修复说明以 `DSH-COMBAT-REVIEW-FIXES-RESPONSE-20261012.md` 为准。

## 实机发现并修正的附加故障

15:46 日志确认 ServerCombat 单例注册、41 武器和8姿态加载，用户进入世界。15:47:09 实际绘制刀光时在 Codex 的 `SkillSlashSpriteRenderer` 崩溃：错误地通过 `Minecraft.getTextureAtlas()` 访问粒子图集，该入口查询物品 ModelManager 的 AtlasSet，并不包含 ParticleEngine 的图集。

已改为从 TextureManager 读取 ParticleEngine 注册的粒子 TextureAtlas，再用 `atlas.getSprite()` 读取爆炸帧；图集尚未就绪时不绘制，避免崩溃。首次实际绘制会记 `[skill-fx] Particle atlas sprite ready: ...`，待重启后通过用户手动施放确认。崩溃报告保留在测试目录，不覆盖用户存档。

启动参数也已修正：`-PmanualSkills` 仅在 `codex-skill-smoke/level.dat` 实际存在时启用快速进入。旧验收世界不存在时正常打开主菜单，由用户选择已建存档，不再指向不存在的世界；不创建/重命名/覆盖任何存档。
