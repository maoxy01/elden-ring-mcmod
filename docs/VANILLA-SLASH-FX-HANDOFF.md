# 原版爆炸贴图刀光美术交付

2026-10-12 后续更新：按用户要求删除独立“黄金波动·横斩”特效及预览项，保留黄金波动本体和挥剑动作。旧线协议编号 10 保留为 NONE 空效果，未知编号同样不渲染。完整 build 与静态导出检查通过；本次浏览器截图复查两次审批超时未执行，上一轮 QA 截图不作为本次删除后的截图证据。

2026-10-12。仅修改美术表现，不修改 dsh 的战斗迁移、输入、FP、施放时序、判伤或位移。

## 本轮表现

- 水鸟乱舞：六道错时出现的银白刀光，暗银边和细亮芯，最多十二张贴片。
- 命定之死：三道宽厚黑色刀痕配红色细芯，最多六张贴片。
- 咒血斩击：三道更窄的暗猩红刀痕配鲜红亮芯，最多六张贴片。这里的错时/残影仅是视觉，延迟爆发与判伤仍归 dsh。
- 其余战技保留原有网格表现；黄金波动成形规则和授血仪式方形光圈未改。

运行时直接读取 Minecraft 粒子图集 `TextureAtlas.LOCATION_PARTICLES` 中的 `minecraft:explosion_0` 至 `explosion_15`，不创建 TNT，不调用爆炸逻辑，也不批量生成原版粒子。使用拉长、着色、短渐入/淡出的相机朝向贴片，中心保留既有特效实体位置空间。

原版贴图文件不加入 `src/main/resources` 或 JAR。`art/skill-fx/meshes.js` 的本地审查页嵌入本机资源 JAR 的帧图像用于离线预览，这不是分发资源。

## 代码接口

`effects/SkillSlashSprites.java` 只接受已有 `SkillEffectMesh.Kind`、半径、归一化寿命进度和 phase，返回带颜色、透明度、位置、尺寸、角度和帧号的 Strip；不依赖 Minecraft 或 EF。

`client/effects/SkillSlashSpriteRenderer.java` 用原版 particle shader 和粒子图集绘制，全亮、深度测试、不写深度；其他客户端渲染继续使用原路径。

`client/effects/SkillEffectRenderer.java` 仅对 WATERFOWL、DEATH_SLASH、CURSED_SLASH 选择贴图路径，不与旧方块刀光重复叠加。`SkillEffectMesh` 中的旧几何仍保留作原有接口兼容，不代表这三项在游戏仍同时绘制旧网格。

dsh 如改变特效枚举/实体接口，请保留 kind、radius、progress、phase 及 ownerEntityId 的视觉契约，或明确给出新适配接口。没有要求 Epic Fight，适用于正在进行的原版战斗迁移。相机朝向是贴片表现，不是武器轨迹重建；实际刀光与挥刀时点仍需联调。

## 验证边界

- 发现工作区正在按 `DSH-VANILLA-COMBAT-PLAN.md` 迁移移除 EF；本轮完整 build 因未清理完的旧 EF/动画引用失败，未覆盖或恢复 dsh 的依赖和文件。
- 单独编译六个美术类成功，验证当前 NeoForge/Minecraft 图集、shader 与顶点 API；使用现存旧 JAR 仅提供隔离编译时的实体类接口，不是发布新 JAR。
- 四项隔离美术测试通过，覆盖数量上限、极小/非法半径、有限值、拉长比例、生命周期、三个战技配色/厚度区别、分层出现和 phase 镜像。
- 桌面/手机 Three.js 预览通过截图、非空像素差、切换、播放、相机交互和溢出检查；未宣称 Minecraft 实际 shader、Iris 组合和联机已验收。
- 未自动启动游戏：需要 dsh 完成迁移后重新完整构建，再让用户手动验收。

隔离检查命令：

```powershell
.\gradlew.bat testSlashFx --init-script tools/skill-fx/validate.init.gradle --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

预览：`art/skill-fx/preview.html`。截图：`waterfowl-vanilla-desktop.png`、`death_slash-vanilla-desktop.png`、`cursed_slash-vanilla-desktop.png`；QA：`art/skill-fx/qa.json`。

原美术文件备份：`docs/code-backups/20261012-vanilla-slash-fx/`。纯美术类和隔离验证工具不需要恢复 EF，后续战斗构建成功后再做完整测试。
