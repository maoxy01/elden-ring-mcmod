# 追忆武器游戏版 R3：大像素方块版

2026-10-11。按用户最新要求取消 R2 平滑曲面与降噪，恢复硬边方块造型，并将每个 2 x 2 小纹理像素合成一个大像素。八件武器的物品/战技 ID、握持原点、16 单位一格、单 root 和资源路径不变。专属战技由 dsh 实施，本轮没有修改战技 Java、发布 JAR 或重发整合包。

## 备份与源版修复

- 原精细源：`art/backups/remembrance-highdetail-20261011-001/`，75 个文件。
- R1 方块游戏版：`art/backups/remembrance-game-before-smoothing-20261011-001/`，154 个文件。
- 本次修改前的 R2 平滑版及源版、工具、交接说明：`art/backups/remembrance-smooth-before-pixel-20261011-001/`，221 个文件。

每份备份有逐文件 SHA-256 清单，不覆盖、不自动恢复工作区。当前精细源版仅修复神躯化剑根部：两侧宽肩把扭转剑刃接入主体，新增 20 个方块；游戏版对应增加 4 块。不是用细柱托住悬空刃片。其余七件源模型、源图集和两版战技设计文件保持不变；生成索引与预览随修复更新。证据见 `pixel-validation.json`。

## 模型与预算

| 武器 | 精细源方块 | 游戏版方块 | 预烘焙三角形 |
| --- | ---: | ---: | ---: |
| 神躯化剑 | 8825 | 997 | 11680 |
| 玛莉卡的石槌 | 2855 | 222 | 2664 |
| 玛利喀斯的黑剑 | 6077 | 571 | 6852 |
| 亵渎圣剑 | 8794 | 1029 | 12348 |
| 玛莲妮亚的义手刀 | 8306 | 529 | 6348 |
| 碎星大剑，单柄 | 12456 | 500 | 6000 |
| 蒙格温圣矛 | 6045 | 490 | 5796 |
| 蒙葛特咒剑 | 6937 | 582 | 6984 |

每件不超过 1200 方块，相比精细源减少至少 80%。实际网格重新由这些方块烘焙，使用恒定逐面法线，不使用 smoothSurfaces 连续曲面。八件单柄合计 58672 三角形，比 R1 的 58624 多 48 个根部连接三角形；不再适用 R2 的 38.2% 减面结论。碎星双持是两个独立 6000 三角形实例，阴影、附魔和技能特效另计。

## 大像素与贴合

游戏图集仍是 2048 x 2048 RGBA，UV 布局不变；16 个 384 像素正背面投影区和 16 个 128 像素材质区分别处理，不跨区合并。每个 2 x 2 单元的四个 texel 使用同一颜色，合计 655360 个恒色单元。默认按单元颜色合并，遇到连贯高对比纹饰保留代表色；无模糊滤波、无平滑法线，预览继续 nearest 采样。这是纹理颗粒合并，不是将不同位置的装饰或承力方块强行合成一个大方块。

保留刃形、扭转双股、碎刃、三叉尖、掌指与嵌入石体的黄金碎片。立体装饰必须与主体贴合，八件旋转方块的 OBB 接合图均只有一个连通分量，并包含故意断开部件的负向测试。神躯化剑新宽肩另有离轴实体探针与移除宽肩必失败的测试。连通检查不等于全部内部面已清理，也不替代外观审查。

图集尺寸未降低，因此不宣称显存或 FPS 改善；恢复方块网格也比 R2 平滑版三角形更多。尚无远距、掉落物或 GUI 专用 LOD。

## 文件与接入

- `preview.html`：离线交互预览，显示运行用 `.mesh.json`，可查看柄部与剑刃近景。
- `*.bbmodel`：可见、可编辑的方块与逐面 UV；R3 不再附加可见平滑网格或隐藏方块。
- `*.model.json`：保留 cubes/faceUVs，`bakedMeshPath` 指向预烘焙网格；新增 pixelConsolidation 元数据可被运行端忽略。
- `*.mesh.json`：schemaVersion=1、units=blocks、bone=root；位置、法线、归一化 UV、整数三角形索引及一个共用图集材质组。资源重载时缓存，不逐帧重建。
- `catalog.json`：沿用已有物品 ID，不注册第二套武器。
- `validation.json`、`preview-validation.json`、`comparison-validation.json`、`pixel-validation.json`：当前 R3 验收证据。历史 R2 smoothing-validation 仅保存在备份。
- `qa/`、`previews/`、`asset-manifest.json`：对照、截图与 SHA-256 交付清单，不进入运行时资源。

继续沿用现有 syncArt 与 WeaponMeshData 加载器，不需要 dsh 修改 collider 或专属技能。构建只带游戏版模型、网格、图集与索引，不带 Blockbench、预览、备份和报告。详细契约见 `docs/GAME-WEAPONS-ASSETS-HANDOFF.md`。允许专属战技跳跃，仍不做处决，Boss 暂停。

## 再生成与检查

在 `art` 目录执行；依赖目录需包含 playwright 和 pngjs，浏览器使用本机 Edge：

```powershell
npm run build:remembrance
npm run build:remembrance-game
node tooling/verify-pixel-weapons.mjs <依赖目录>
node tooling/verify-weapons.cjs <依赖目录> --remembrance
node tooling/verify-weapons.cjs <依赖目录> --game-remembrance
node tooling/verify-game-comparison.mjs <依赖目录>
node tooling/seal-game-assets.mjs
node tooling/seal-game-assets.mjs --verify
```

先生成源版根部修复，再从源版投影烘焙游戏图集。修改 BB 后需回写生成定义，再生成会覆盖本目录资产。R3 要求实际 mesh 与方块轮廓 IoU >= 0.995；与当前精细源及 R1 方块版分别比较四视角，正背面 >= 0.9、斜视 >= 0.85、侧视 >= 0.75。截图只证明浏览器渲染，不证明 Minecraft 实机、多人命中或战技验收。

本机未发现 Game Development Studio CLI，使用项目已有 Three.js/JSON/Blockbench 工具链；未调用付费生成器、未制作 canonical GLB 包、未新增再分发许可。最终游戏外观和性能仍需实机审查。
