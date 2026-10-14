# 游戏版追忆武器接入补充

2026-10-11 更新：用户授权 Codex 实际接入 Java 与构建资源。本轮接入八件减面模型、EF 基础攻击能力和客户端缓存，未修改 Boss 行为，也未完成法环专属战技或重发整合包。具体状态见 `WEAPONS-INTEGRATION-REVIEW.md`。

优先接入 `art/remembrance-game/`，不要把 `art/remembrance/` 的高细节编辑源当默认游戏网格。源版仍保留，并有 `art/backups/remembrance-highdetail-20261011-001/backup-manifest.json` 的哈希备份。两个目录相同物品 ID，对应不同美术 profile；不是再注册八件重复物品。

## 资源路径

| 源 | 已接入游戏路径 |
| --- | --- |
| `art/remembrance-game/*.model.json` | `assets/eldencombat/skeletons/items/remembrance/<id>.model.json` |
| `art/remembrance-game/*.mesh.json` | `assets/eldencombat/skeletons/items/remembrance/<id>.mesh.json` |
| `art/remembrance-game/weapons.png` | `assets/eldencombat/textures/item/remembrance.png` |
| `art/remembrance-game/catalog.json` | `assets/eldencombat/weapons/remembrance.catalog.json` |

仅同步模型、网格、图集和必要索引，不复制 `.bbmodel`、预览、qa、备份和美术验收 JSON。战技 design 文件继续只是说明，不作为服务器运行配置自动生效。

## 网格契约

`*.model.json` 保持 `unitsPerBlock=16` 的方块骨架格式；`bakedMeshPath` 是相对该模型目录的文件名，读取时限制在合法资源目录。不能用同名原精细图集替代游戏版图集，UV 布局已不同。

`*.mesh.json`：

- `schemaVersion=1`，`bone=root`，`units=blocks`；扁平 `positions` 已除以 16，游戏端不能再次除以 16。
- `normals` 为单位法线，`uvs` 已归一化至 0..1，文件 V 为下原点。Three.js 不再翻转；Minecraft VertexConsumer 使用上原点，`WeaponMeshData` 加载时做一次 `1-v`，后续提交不再翻转，也不再次除以 2048。
- `indices` 为三角形索引，`indexType=uint16/uint32`，不要截断索引。`vertexCount` / `triangleCount` 与数组长度必须相符。当前 MC 实现保留整数索引读取，在重载时展开为 `a,b,c,c` 退化四边形，适配实体/附魔 QUADS 通道；不直接提交三个顶点，不声称已实现 GPU 索引缓冲。
- 一组共用图集材质，加载后缓存网格；EF 局部手矩阵与自有握持校准各应用一次。附魔闪光、半透明/不透明、GUI 与原版回退单独处理。
- 仅严格重合且法线相反的整面被移除；不按包围盒交叠删除旋转面。所有 cubes 仍保留作为可编辑兼容回退。
- 解析验证有限数、数组长度、索引范围、法线、UV、包围盒和版本；缺失网格可在重载时从 cubes 烘焙一次并报诊断，不逐帧重建或静默换回旧高细节图集。
- 一材质组有利于合批，但不承诺一次总 draw call；EF 多个 pass、附魔/阴影、双持及具体渲染实现另行测试。

客户端资源重载时通过 ResourceManager 准备新缓存，再原子切换；旧 CPU 缓存由 GC 回收，当前未持有专用 GPU VBO，不需要虚构 GPU 释放流程。损坏网格报错后从当前模型烘焙一次；损坏模型单项报错，不导致整个重载崩溃。独立服务端不加载这套客户端美术，也不从几何数量、贴图或 `damageParts` 自动决定 collider。仍按总体方案使用唯一服务端技能/判伤入口。

## 造型与预算

### 2026-10-11 大像素方块版 R3：当前交付

用户最新选择取消平滑，将四个小纹理像素合成一个大像素。当前八件游戏资产恢复可编辑硬边方块，无 smoothSurfaces，使用恒定逐面法线；图集的每个 2 x 2 单元共用一个颜色，不做模糊滤波。图集仍为 2048，UV 与资源路径不变，运行端忽略 pixelConsolidation 元数据即可，继续加载 bakedMeshPath。dsh 不需要为此改技能或 collider。

R2 平滑版连同修改前源版、工具与交接说明已保存为 221 文件哈希备份：`art/backups/remembrance-smooth-before-pixel-20261011-001/`。当前源版神躯化剑额外新增 20 块宽肩连接，游戏版新增 4 块，解决两侧扭转刃根部空隙；其余七件源模型、源图集和战技设计不变。原高精细备份没有修改。

当前实际三角形：神躯化剑 11680、石槌 2664、黑剑 6852、亵渎圣剑 12348、义手刀 6348、碎星单柄 6000、圣矛 5796、咒剑 6984；合计 58672。R2 的减面百分比不适用于当前版，没有游戏帧率实测。每件 222–1029 方块，保留每件不超过 1200、相对源模型减少至少 80% 的预算。

当前 Blockbench 只显示可编辑方块，不隐藏回退或附加 R2 平滑 mesh。验收使用 `pixel-validation.json`、`preview-validation.json` 和 `comparison-validation.json`；mesh/cubes 轮廓 IoU >= 0.995，源版和 R1 双重四视角阈值保留。宽肩有移除即失败的实体探针，不能只用轴心细柱掩盖两侧断开。

本轮只更新美术、生成验证工具与本文档，不修改 dsh 战技 Java，不覆盖发布 JAR 或整合包。下次构建的 syncArt 将自动带入 R3。

### 平滑版 R2：历史记录，已被 R3 替代

Codex 已将八件游戏版的主要刃面、宽肩连接、曲线与圆体改为连续网格，并对图集分区降噪；dsh 的 Java 与专属战技资源不在本轮修改范围。
修改前游戏版另有 154 文件哈希备份：`art/backups/remembrance-game-before-smoothing-20261011-001/`。

实际 `.mesh.json` 三角形：神躯化剑 5904、石槌 2512、黑剑 3448、亵渎圣剑 10448、义手刀 3044、碎星单柄 3144、圣矛 4560、咒剑 3188。
合计 58624 -> 36248，约少 38.2%；图集仍为 2048，没有性能实测，不保证同等 FPS 提升。
上一版 cubes 数量仍用于编辑兼容回退，不代表当前连续网格的面数。

`*.bbmodel` 现包含可见完整 mesh 与隐藏、不导出的方块回退；model 增加 `smoothSurfaces` 生成规格，现有运行时可忽略该元数据，继续优先加载 `bakedMeshPath`。
资源路径、握持原点、units、UV、schemaVersion 与单材质契约不变；不需要 dsh 为平滑版另造加载器或改 collider。
原有 mesh/cubes 精确轮廓一致性不作为历史 R2 验收条件。历史报告仅保存在 R2 备份的 `art/remembrance-game/smoothing-validation.json`；不用于当前 R3 验收。
这轮未覆盖发布 JAR 或整合包；下一次构建沿用 syncArt 自动带入新模型/网格/图集。

以下是方块版的共同约束；当前运行预算以 R3 补充和 catalog 为准。

保留自然宽肩嵌接，不恢复细柱托刃；纹饰烘焙到表面，保留立体结构必须贴合主体。每件 222–1029 个方块、2664–12348 三角形；全部单 root，OBB 接合检查一个连通分量。碎星单柄 500 块、6000 三角形，左右手分别绑定。

这是近距离手持预算，不是完整 LOD 系统；掉落/GUI/远距还需更低成本资产或图标。源版 2048 和游戏版 2048 并存供编辑，发布时默认仅带游戏版图集及必要资源，避免同时加载两套编辑源。

## 验收边界

美术工具验证：备份哈希、源版仅允许神躯化剑宽肩修复及派生索引/预览更新、其余源模型与源图集不变、几何预算、2 x 2 恒色像素块、整件旋转 OBB 连通（含断开负向用例）、柄刃采样、UV/BB、网格索引/硬边法线/绕序、桌面手机截图与旋转交互、四视角源版轮廓对比和预烘焙/方块渲染一致性。以本目录最新验收 JSON 为准，任何失败不算通过。

轮廓比较不证明纹理外观完全相同、动作正确或没有任何内部面；用户仍需审查外观。没有 MC 性能实测，不能将 88%–96% 的方块减少写成同等帧率提升。游戏端需要继续验收 EF/非战斗模式、握持、多人、实际命中、资源重载与锁定整合包兼容。

专属技能 ID 和跳跃许可不变，仍不做处决，Boss 暂停。详见 `REMEMBRANCE-ASSETS-HANDOFF.md` 与 `EF-WEAPONS-IMPLEMENTATION-PLAN.md`。
