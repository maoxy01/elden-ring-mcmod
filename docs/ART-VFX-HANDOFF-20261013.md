# 八个追忆武器战技：像素特效美术交付

日期：2026-10-13。用户最新要求为“直接把八个追忆武器的战技特效都做出来”，覆盖旧工单仅做三个样板的数量限制。本轮交付全部八个战技的美术资源，战斗代码与游戏接入由战斗对话负责。

## 交付状态

- 8 个战技、10 组独立序列、160 张透明 PNG、10 张带边距图集。唤星提供聚引/坠击，咒血提供刀痕/延烧。
- 每组 16 张不同帧，最近邻采样。64 像素资源按 2×2 宏像素绘制，实际逻辑网格 32 像素；黄金波动为 64×32，其他为 64×64。
- 静态检查已通过：文件 SHA-256、透明背景、非空帧、唯一帧、宏像素一致、有限透明度、图集与单帧字节匹配、双波前全部 16 lane 存在。
- 浏览器检查已通过：1440×1000 和 390×844，无横向溢出，160 帧载入，八个画布均有可见特效，播放推进、主副素材切换、三个 burst、咒血先斩后烧、两种波前裁剪、刀光平面缩短、黄金波动成形后 lag 不再变化。检查脚本以浏览器截图读取像素，不读取被本地文件安全策略污染的 canvas。
- 离线预览不是 Minecraft 画面。没有写入 Java、src/main/resources、Gradle、build，也没有启动游戏。未完成实机接入/多人/光影验收。
- 旧三样板已完整备份：`art/backups/vfx-2d-before-eight-20261013-231252/`。

打开 `art/vfx-2d/preview.html` 即可播放，无需启动服务器。页面按八个战技排列；唤星和咒血可切换单次素材，也可播放阶段串演。串演中的落地和输入追击时间是演示假设，绝不能直接搬进服务端时间表。

## 武器与特效对应

| 武器 | 战技 ID | 素材目录 / Kind | 设计 |
|---|---|---|---|
| 神躯化剑 | wave_of_gold | gold_wave / GOLD_WAVE | 金色贴地波前、离散消散尾迹；没有额外横斩 |
| 玛莉卡的石槌 | gold_breaker | gold_impact / GOLD_IMPACT | 不规则块状扩散边缘、地裂、飞起地块 |
| 玛利喀斯的黑剑 | destined_death | death_slash / DEATH_SLASH | 黑刃主体、红色边焰和短余迹，三次爆发 |
| 亵渎圣剑 | takers_flames | flame_front / FLAME_FRONT | 有高度的橙红贴地火浪，逐 lane 阻挡 |
| 玛莲妮亚的义手刀 | waterfowl_dance | waterfowl / WATERFOWL | 银白收尖刀光，三 burst、不同斩击角度 |
| 碎星大剑 | starcaller_cry | gravity_pull / GRAVITY_PULL；gravity_impact / GRAVITY_IMPACT | 紫灰块状聚引圈收缩，然后落地外扩、碎块飞起 |
| 蒙格温圣矛 | bloodboon_ritual | blood_ritual / BLOOD_RITUAL | 方形血焰光圈、断续内框和角标；阶段尺度轻微变化 |
| 蒙葛特咒剑 | cursed_blood_slice | cursed_slash / CURSED_SLASH；blood_flame / BLOOD_FLAME | 明亮细刀痕、暗红余迹，随后独立延烧 |

纹理路径统一为 `art/vfx-2d/textures/particle/<素材目录>/frame_00.png` 至 `frame_15.png`。将来放入资源包的目标相对路径为 `assets/eldencombat/textures/particle/<素材目录>/frame_00.png`。sprite ID 使用 `eldencombat:<素材目录>/frame_00`，不带 textures/particle 前缀和扩展名。

## 尺寸、锚点与时序

世界显示尺寸是美术建议，不是伤害判定半径。锚点以 PNG 左上角为原点；顶点必须相对锚点排布，不能一律把贴图中心放到实体原点。

| 素材 | 锚点像素 | 显示尺寸（格） | 方向/事件/视觉寿命 |
|---|---|---|---|
| waterfowl | 32,36 | 6×4.5 | 跟随施法者的固定斩击平面；ACTIVE 的 0/12/24；每次 8 tick |
| death_slash | 32,36 | 7×5 | 同上；ACTIVE 的 0/8/16；每次 8 tick |
| gold_wave | 32,8 | 宽=2×实体 visualRadius，纵深2.8 | 水平地面；实体进度、地形/距离/寿命驱动 |
| gold_impact | 32,32 | 7×7 | 落地点水平面；实际 landingImpact 后 12 tick |
| flame_front | 32,58 | 宽=2×实体 visualRadius，高3.5 | 沿波前横向的竖直面；底部落在各 lane 地形上 |
| gravity_pull | 32,32 | 10×10 | 第一输入段 ACTIVE；水平面；12 tick |
| gravity_impact | 32,32 | 8×8 | 确认追击后的实际落地；水平面；12 tick |
| blood_ritual | 32,32 | 8×8 | 施法地点上方约2.4格的水平面；每次确认段16 tick |
| cursed_slash | 32,36 | 6×4.5 | 每次确认输入段 ACTIVE；跟随斩击平面；8 tick |
| blood_flame | 32,56 | 4×3 | 延迟原点的竖直火焰面；固定世界位置；12 tick |

详细 basis、偏移、phaseRollDegrees、phaseVariants、事件条件、来源参数和对应文件哈希在 `manifest.json`。这些默认战技时序来自当前代码；配置与 LocalSourceMotion 可能覆盖，运行时始终以同步实体进度为准。

每个序列按 progress 采样 `min(15,floor(clamp(progress,0,1)*16))`；progress>=1 或实体已丢弃则不画。前3帧出现、中8帧主体、末5帧离散消散。实体为8 tick时需要 partialTick 才能展示完整16帧。RGB 已着色，顶点 tint 使用白色；alpha 是分级透明度，避免叠乘旧特效颜色造成发黑，也避免继续叠旧盒体和多层细条。

水鸟/命定之死的三个 burst 不等于用户输入三段追击。授血/唤星/咒血的追加阶段则需要实际输入事件；预览自动串演不能成为自动追击实现。

## 接入需补齐的地方

以下为本次读取源码时的状态，战斗对话应先核对并发修改，再落实现有入口。

1. `FxSlashRoute` 已支持 `waterfowl`、`death_slash`、`cursed_slash`、`golden_wave` 四个路由，素材的 `gold_wave` 必须映射到现有 **golden_wave** 路由名。另六个 Kind 需增加各自路由，不要全挤进共享 slash。
2. `SkillSlashSpriteRenderer` 已有独立路由、atlas 实际载入检查和非 billboard 分支，可复用。当前顶点仍围绕贴图中心，需接入本包 anchor；`SkillSlashSprites.build` 仍生成多层薄条，width/height 会把这些完整轮廓压扁，应改为每事件一个主要轮廓平面。
3. 默认路由的水鸟 pitch=90、命定/咒血 pitch=45 并非本次美术夹具默认平面。按 manifest 平面明确覆盖；实体旋转已施加一次 yaw，不要重复旋转。主刀光不随相机转，烟尘等次级效果才可 billboard。
4. `SkillEffectRenderer` 当前调用 `effect.progress(0)`；要对这批16帧使用 partialTick，并使几何缓存键包含实际帧索引。不能靠每 tick 同一张图凑出16帧。
5. `SkillSlashSprites.buildWave` 当前把半径夹到2.6，并且没有 lane UV 子区间；渲染时对每个 strip 采样完整 sprite。这会缩小波宽并重复整张图，必须改为真实 visualRadius 和每 lane 四源像素的 UV 裁剪。
6. 黄金波动与掠夺火焰都走 `GoldenWaveEntity`，使用各自材质/平面。黄金波动为地面，掠夺火焰为竖直火墙底边贴地，不能套用同一平面参数。遮挡 lane 必须连同对应火焰完全不画。
7. 当前 `FxSlashRoute.Outline` 只有 ARC/CRESCENT/WAVE_FRONT/BOXES。新类型需匹配轮廓的低成本降级；正常与CORE不能突然变成爆炸贴图。可保留主要帧、减少次级碎块；距离过远使用既有 HIDDEN。
8. 帧文件复制不等于 atlas 注册成功。单帧需进入 particles atlas，并逐项确认加载；review atlas 的2px边距和4×4排布仅用于查看，不能直接按16格无边距切割。
9. **咒血追击延烧风险**：`RemembranceRuntime.activeTick` 在 elapsed==4 生成 BLOOD_FLAME，而默认 followupActiveTicks=4。若阶段在 elapsed>=duration 转移，该事件永远无法触发。本包已做好素材，战斗端需验证阶段循环并确定事件在窗口内的正确位置；不要靠客户端定时补伤害。
10. 授血预览中的额外下落细屑、黄金/唤星的飞起地块是辅助表现示意。地块渲染沿用现有 groundState/debris；授血可使用序列内点状痕迹，并按预算补少量辅助绘制。没有新增方块破坏权限或真实地图修改。任何伤害/拉拽/扣蓝/治疗仍由服务端负责。

黄金波动的16 lane 横向参数 `u=-1+(lane+.5)*2/16`；lag 为 `floor(clamp(travel,0,4)*0.45*abs(u)^2/0.125)*0.125`。travel到4后 profile固定，所有lane相对位置不再变化、一起前进。贴图帧继续按 visualProgress 播放，不能一起冻结。每 lane UV 为 `[lane/16,(lane+1)/16]`，被 blockedLanes 命中的 lane 整段省略。

## 资源文件与复现

- `art/vfx-2d/manifest.json`：全部素材/时序/锚点/坐标/哈希/来源合同。
- `art/vfx-2d/pixel-source.json`、`art/tooling/build-vfx-2d.mjs`、`art/tooling/vfx-eight-clips.mjs`：可编辑色板与确定性像素生成源。
- `art/vfx-2d/preview-data.js` 从 manifest 同源生成；静态 verifier 验证两者严格相等。
- `art/vfx-2d/validation.json`：检查结果，runtimeIntegrated 与 inGameAccepted 均为 false。
- `art/vfx-2d/eight-1440-*.png`、`eight-390-*.png`：本批截图。旧 `preview-1440-*.png` 属于三样板旧批次，不能用作本次验收证据。
- `art/vfx-2d/LICENSE.txt`：本项目原创像素资源 CC0-1.0；没有第三方像素、付费生成或提取法环素材。manifest 中免费候选作者页是来源调研，未下载验明的包不宣称已获包级验证。

在项目根目录运行：

```powershell
& 'C:/Program Files/nodejs/node.exe' art/tooling/build-vfx-2d.mjs
& 'C:/Program Files/nodejs/node.exe' art/tooling/verify-vfx-2d.mjs
& 'C:/Program Files/nodejs/node.exe' art/tooling/verify-vfx-2d.mjs --browser
```

生成器仅更新 art/vfx-2d 并将浏览器验收重置为 false；修改后必须重跑 browser verifier。依赖使用已有 Codex Node runtime 的 pngjs 与 Playwright，浏览器为 Edge；不属于 game-dev CLI canonical package。

## 战斗端实机验收

八种战技逐一检查第一/第三人称、明暗处、观察者转镜头、窄通道、斜坡和同时施法；对照实际加载帧日志确认未走占位资源。黄金波动测形成弧形前后与墙体遮挡；掠夺火焰测逐lane贴地；唤星区分聚引和落地；授血只在实际输入段重复；咒血核对追击延烧。地块表现保持世界方块不被客户端改动。

任何通过静态或离线浏览器检查的结论都不能替代上述实机接入验收。
