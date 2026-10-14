# dsh → Codex 交接：武器资产制作流水线

> 日期：2026-10-12 ｜ 分支状态：`D:\codexout\mcmod` 工作区（**无 git**，改动前请自行备份）
> 相关文档：
> - 现状与已交付资产：[`WEAPONS-AND-ARTS-HANDOFF.md`](WEAPONS-AND-ARTS-HANDOFF.md)
> - 追忆网格契约与 R3 决定：[`GAME-WEAPONS-ASSETS-HANDOFF.md`](GAME-WEAPONS-ASSETS-HANDOFF.md)
> - 显示适配现状：[`CODEX-WEAPON-DISPLAY-FIX-20261012.md`](CODEX-WEAPON-DISPLAY-FIX-20261012.md)
> - 风格口径：[`DSH-VFX-STYLE-HANDOFF-CODEX-20261012.md`](DSH-VFX-STYLE-HANDOFF-CODEX-20261012.md)
> **⚠️ 本文档是资产制作规范，不是实现记录。** 未运行任何命令、未修改任何文件。
> §5 的路径映射依赖 `build.gradle` 的 `syncArt` 现状，**需要先与你核对**，不得照抄。

---

## 0. 结论先行

1. **41 件已建模**（33 类型 + 8 追忆），追忆已完成 R2（平滑）→ R3（大像素方块）两轮迭代，
   当前交付为 **R3：恢复硬边方块、取消平滑、2×2 恒色像素**。这条决定不再回头。
2. **源版 / 游戏版双轨制是这条管线里最有价值的设计**，必须固化为规范（§1）。
   美术在源版无限迭代，运行时永远跑预算内的资产。
3. **缺 LOD**。`GAME-WEAPONS-ASSETS-HANDOFF.md` 第 66 行明确写着这是"近距离手持预算，
   不是完整 LOD 系统；掉落 / GUI / 远距还需更低成本资产"。§3 给出补齐方案。
4. **每把武器只允许 1 个 material slot**（单材质组合批）。现有状态已符合，**不要破坏**。
   加第二张贴图 = 每把武器两次 draw call。
5. 本文面向**后续新增武器**。已有 41 件如无承载问题不返工。

---

## 1. 双轨制：源版 ≠ 游戏版

这是现有资产组织方式，我认为应当写成强制规范：

| | 源版 `art/remembrance/`、`art/weapons/` | 游戏版 `art/remembrance-game/` |
| --- | --- | --- |
| 用途 | 美术编辑、迭代、保留细节 | 运行时加载 |
| 预算 | **不限**，可以堆细节 | 严格受 §2 约束 |
| 格式 | `.bbmodel`（含隐藏 cubes 回退 + 可见 mesh） | `.model.json` + `.mesh.json` + 图集 |
| 进 JAR | **否** | 是（经 `syncArt`） |
| 进 JAR | **否**（`.bbmodel`、预览、qa、备份都不进） | 是（经 `syncArt`） |

**为什么这条重要**：如果不分轨，美术改一个细节就要重新走一遍减面 + 校验 + 备份，迭代成本高到没有人愿意改。分轨之后，"改源版"和"出游戏版"是两个独立动作，各自可以独立节奏。

**已有先例可循**：`art/backups/remembrance-smooth-before-pixel-20261011-001/`（221 文件哈希备份）、
`art/backups/remembrance-highdetail-20261011-001/`。每次源版改动前先打哈希备份——这个习惯要保持。

---

## 2. 预算表

### 2.1 按类别的几何预算（游戏版）

方块数为主指标，三角形数为校验指标（理论上限 12 tri/方块 = 6 面 × 2）。

| 类别 | 方块上限 | 三角形上限 | 覆盖 |
| --- | --- | --- | --- |
| 短武器 | ≤ 320 | ≤ 3800 | 匕首、拳套、钩爪、圣印记、短弓 |
| 中型 | ≤ 640 | ≤ 7600 | 直剑、刀、曲剑、刺剑、重刺剑、双头剑、斧、槌、连枷、软鞭、矛、辉石杖、三种盾 |
| 大型 | ≤ 900 | ≤ 10800 | 大剑、大曲剑、大斧、大槌、镰刀、戟、大矛、长弓、弩、大弓 |
| Hero / 巨武器 | ≤ 1200 | ≤ 14400 | 特大剑、特大武器、重弩、弩炮、**8 件追忆** |

**现有追忆实测值**（对照用，全部在预算内）：

| 武器 | 方块 | 三角形 |
| --- | --- | --- |
| 玛莉卡的石槌 | 222 | 2664 |
| 碎星（单柄） | 500 | 6000 |
| 蒙格温圣矛 | — | 5796 |
| 玛莲妮亚的义手刀 | — | 6348 |
| 玛利喀斯的黑剑 | — | 6852 |
| 蒙葛特咒剑 | — | 6984 |
| 神躯化剑 | 1029 | 11680 |
| 亵渎圣剑 | — | 12348 |
| **合计** | — | **58672** |

> 亵渎圣剑 12348 已逼近 14400 上限，后续若继续加细节请先确认是否改用更大档。

### 2.2 贴图预算

| 组 | 图集上限 | 现状 |
| --- | --- | --- |
| 33 件类型武器 | 512×512 RGBA | ✅ `art/weapons/weapons.png`，13 个共享材质区 |
| 8 件追忆武器 | 2048×2048 RGBA | ✅ `art/remembrance-game/weapons.png` |

硬约束：

1. **每把武器 ≤ 1 个 material slot**（一组共用图集材质）
2. 禁止为单把武器新增第二张 PNG
3. 图集内部 UV 每个 `2×2` 单元共用一个颜色（R3 决定，见 §6.3）
4. 运行中**不做模糊滤波**，保持最近邻锐利

> **不能用源精细图集替代游戏版图集**——两者 UV 布局已不同（R3 像素合并改变了布局）。

### 2.3 结构约束

| 项 | 约束 |
| --- | --- |
| 根节点数 | 1（全部单 `root`） |
| OBB 连通分量 | 1 |
| material slot 数 | 1（见 §2.2） |
| 子树degeneracy | 允许隐藏 cubes 回退，不参与渲染 |

---

## 3. LOD（最大缺口，当前完全缺失）

`GAME-WEAPONS-ASSETS-HANDOFF.md` 第 66 行已承认这不是完整 LOD 系统。补齐方案：

| 场景 | 资产 | 预算 | 现状 |
| --- | --- | --- | --- |
| LOD0 第一人称手持 | `.mesh.json` 全量 | §2.1 上限 | ✅ 已有（`WeaponDisplayTransforms`，基础倍率 0.65，最大长边 1.6 格） |
| LOD0 第三人称手持 | 同上 | 同上 | ✅ 已有（基础倍率 0.9，最大长边 2.8 格） |
| LOD1 展示框 / 固定展示 | 同上 | 同上 | ✅ 已有 |
| **LOD2 掉落物（近距离）** | 同上 | 同上 | ✅ 已有 |
| **LOD3 掉落物（远距离）** | **2D 图标 billboard** | 16×16 PNG | ❌ **缺失** |
| **LOD-GUI 物品栏 / JEI** | **预渲染 2D 图标** | 16×16 PNG | ❌ **缺失** |

### 3.1 关键判断：GUI 不要渲染 3D 模型

现在 GUI 是渲染 3D 模型再斜置适配（`CODEX-WEAPON-DISPLAY-FIX-20261012.md` 第 9 行）。我建议改成**预渲染 2D 图标**，理由：

1. **MC 的物品栏惯例就是 16×16 图标**，玩家可以装资源包替换，符合预期
2. **零渲染成本**——现在物品栏里 36 格 × 3D 模型 + 旋转适配是纯浪费
3. 解决"Editorial 图标是否需要 3D 资产"这个 LOD 空白
4. 掉落物远距离切换同一张图标，两次收益

代价：需要为每个武器导出一张 16×16（或 32×32）PNG。**可以从现有源版自动批量渲染导出，不需要手绘。**

> 现有 7 张护符/药物占位图标已经是这个路子（`textures/item/<id>.png` + `parent=item/generated`），基础设施是现成的。

---

## 4. 建模硬约束（出品前的 checklist）

### 4.1 几何

| 规则 | 判据 |
| --- | --- |
| 体素网格 | 所有 `from` / `to` ∈ (1/16 方块) 整数倍 |
| `unitsPerBlock = 16` | 沿用现有 schema v1 |
| 法线白名单 | 所有面法线 ∈ {±X, ±Y, ±Z} |
| 根节点 | 只能 1 个 `root` |
| 握持原点 | `[0,0,0]`，见 §4.3 |
| **禁止** | 斜面、倒角、圆体、非轴对齐缩放产生的斜边（R3 已取消平滑） |
| 允许的细节手段 | 分层刃面与收尖、护手、缠柄、铆钉、金属配件、镶嵌、盾面纹章 |
| 保留自然宽肩嵌接 | **不恢复细柱托刃**（R3 已修，宽肩有移除即失败的实体探针） |

> **不要退回"只改名字的同形模型"**——`WEAPONS-AND-ARTS-HANDOFF.md` 第 10 行已写明。

### 4.2 材质与 UV

| 规则 | 判据 |
| --- | --- |
| material slot 数 | = 1 |
| UV 单位 | 归一化 0..1（`.mesh.json`） |
| V 原点 | **文件内为下原点**；`WeaponMeshData` 加载时做一次 `1-v`，**提交环节不得再次翻转** |
| 单位换算 | `positions` 已除以 16，**加载端禁止再次除以 16** |
| UV 导出 | 每个 `2×2` 像素单元共用一色（R3） |
| 面 UV | 六面 UV 映射顺序固定 `+X/-X/+Y/-Y/+Z/-Z`，TL/TR/BL/BR 语义唯一 |

### 4.3 原点与朝向契约

| 项 | 约定 |
| --- | --- |
| 握持原点 | `[0,0,0]` |
| 主长度轴 | +Y |
| 盾面 / 盾背 | −Z / +Z |
| 模型朝向 | −Z（与 `*.model.json` schema v1 一致） |
| 左右手 | 用插槽变换处理，**不可翻转贴图** |
| `secondaryGrip` | 双手动作的**初始建议值**，需在真实玩家身上校准，**不是 IK** |
| `damageParts` | 候选几何零件名称，**不是现成 collider** |

### 4.4 索引与网格（`.mesh.json`）

| 规则 | 判据 |
| --- | --- |
| `schemaVersion` | 1 |
| `bone` | `root` |
| `units` | `blocks` |
| `normals` | 单位法线 |
| `indices` | 三角形索引，`indexType` = `uint16`/`uint32`，**不得截断** |
| `vertexCount` / `triangleCount` | 必须与数组长度相符 |
| 退化四边形展开 | 加载时展开为 `a,b,c,c`（适配实体/附魔 QUADS 通道）；不直接提交 3 顶点 |
| 面剔除策略 | **仅移除严格重合且法线相反的整面**；不按包围盒交叠删旋转面 |
| cubes 保留 | 全部保留，作为可编辑兼容回退 |

---

## 5. 文件命名与路径映射

### 5.1 命名（必须等于物品注册 ID）

```
art/<组>/<weaponId>.bbmodel        源版（含 cubes + 隐藏 mesh）
art/<组>/<weaponId>.model.json     游戏版方块骨架
art/<组>/<weaponId>.mesh.json      预烘焙三角网格
art/<组>/-game/*.png               共享图集
textures/item/<weaponId>.png       GUI 图标（§3.1 新增）
```

### 5.2 现有路径映射（**待与 Codex 核对 syncArt 现状**）

| 源 | JAR 路径 |
| --- | --- |
| `art/remembrance-game/*.model.json` `.mesh.json` | `assets/eldencombat/skeletons/items/remembrance/` |
| `art/remembrance-game/weapons.png` | `assets/eldencombat/textures/item/remembrance.png` |
| `art/remembrance-game/catalog.json` | `assets/eldencombat/weapons/remembrance.catalog.json` |
| `art/weapons/*.model.json` | `assets/eldencombat/skeletons/items/` |
| `art/weapons/*.animations.json` | `assets/eldencombat/animations/items/` |
| `art/weapons/weapons.png` | `assets/eldencombat/textures/item/weapons.png` |

**已知缺口**：`WEAPONS-AND-ARTS-HANDOFF.md` 第 121 行指出 `syncArt` 目前**只同步 `art/margit` 与 `art/player`**，
"新增精细武器同步"是待办。需要确认当前是否已补齐——**这是 33 件类型武器能否渲染的前提**。

---

## 6. 出品流程（新增一把武器的标准动作）

```
① 源版建模（Blockbench，不设预算，允许堆叠细节）
     ↓
② 改源版前：打哈希备份到 art/backups/<name>-<reason>-<seq>/
     ↓
③ 减面 → 游戏版 .model.json + .mesh.json
     目标：相对源版减少 ≥ 80% 方块
     引用口径：运行预算以 `GAME-WEAPONS-ASSETS-HANDOFF.md` 的 R3 补充和 catalog 为准
     ↓
④ 静态校验（§6.1 清单，任一项 FAIL 则退回 ③）
     ↓
⑤ 预览：art/skill-fx/preview.html 同源渲染 + 截图
     ↓
⑥ syncArt 同步 → 构建 → gradlew test
     ↓
⑦ 实机验收（§6.2，必须人跑）
```

### 6.1 静态校验清单

`GAME-WEAPONS-ASSETS-HANDOFF.md` 第 70 行已列出完整的美术工具验证项，整理为可执行清单：

| # | 检查 | 依据 |
| --- | --- | --- |
| 1 | 备份哈希一致 | 现有流程 |
| 2 | 几何预算（方块/三角形）在 §2.1 内 | 现有 |
| 3 | `2×2` 恒色像素块 | R3 决定 |
| 4 | 整件旋转 **OBB 连通**（含断开负向用例） | 现有 |
| 5 | 宽肩实体探针（移除即失败） | R3 修正项 |
| 6 | 柄刃采样 | 现有 |
| 7 | UV / bounding box | 现有 |
| 8 | 网格索引范围 / 硬边法线 / 绕序 | 现有 |
| 9 | **mesh / cubes 轮廓 IoU ≥ 0.995** | 现有（R2 指标不适用于 R3，但轮廓一致性仍要查） |
| 10 | 桌面 + 手机截图、旋转交互 | 现有 |
| 11 | 四视角源版轮廓对比 | 现有 |
| 12 | 预烘焙 / 方块渲染一致性 | 现有 |
| 13 | material slot == 1 | **本文 §2.2 新增** |
| 14 | 法线 ∈ {±X,±Y,±Z}（无斜面） | **本文 §4.1 新增** |

> 「任何失败不算通过」这条保留。轮廓比较**不证明**纹理外观一致、动作正确或无内部面。

### 6.2 实机验收（Gradle 退出码不能替代）

```
.\gradlew.bat runClient -PmanualSkills --offline
游戏目录 build/manualSkills/，日志 build/manualSkills/logs/latest.log
```

逐条检查：

| # | 检查 | 不通过的含义 |
| --- | --- | --- |
| 1 | 左右手是否正确（主手/副手分别检查） | 插槽变换错了 |
| 2 | 第一/第三人称都要看，比例是否正常 | `WeaponDisplayTransforms` 倍率待调 |
| 3 | 掉落物是否正常显示，远距离是否可见 | LOD3 缺失（§3） |
| 4 | GUI / 物品栏 / JEI 图标 | LOD-GUI 缺失（§3.1） |
| 5 | 佩戴盔甲时的显示（尤其头盔/胸甲） | 渲染层级问题 |
| 6 | 常规皮肤 + 细臂皮肤两种 | 手臂模型差异 |
| 7 | `F3+T` 重载资源后是否正常 | 缓存未清 |
| 8 | 附魔后是否显示正确 | QUADS 通道 / 退化四边形展开失败 |

> 第 5、6 两条在现有文档中未被列为必须，我建议加进来——它们是最容易出问题的组合。

---

## 7. 交给 Codex 的清单

### P0（阻塞 33 件类型武器）

1. **核对 `build.gradle` 的 `syncArt` 是否已含武器同步。**
   若未含 → 33 件类型武器当前**无法渲染**，这优先于任何新武器产出。
2. **核对 `EquipmentRenderer.asset()` 的选择逻辑。**
   `WEAPONS-AND-ARTS-HANDOFF.md` 第 124 行指出它目前只有剑/大剑/盾标签回退，
   **会把不同种类画成同一件**。需要先按注册 ID 选精细模型，再考虑旧标签兼容。

### P1

3. **导出 41 张 GUI 图标**（§3.1），从源版批量渲染，不手绘。
   文件名必须是物品注册名。
4. **核对预览图的 UV 分母**：`WEAPONS-AND-ARTS-HANDOFF.md` 第 126 行指出
   "预览使用了 512 图集，不能继续把所有新武器的 UV 除以旧的 64"。确认新 assets 走正确分母。
5. **资源重载清缓存**：确保 F3+T / 资源包切换能重建 CPU 缓存。

### P2

6. 远距离掉落物切 2D 图标（§3 LOD3）
7. 按 §2.1 预算复核现有 41 件，标注超预算项
8. 连枷 / 软鞭三节次级摆动（`WEAPONS-AND-ARTS-HANDOFF.md` 标注为机械草稿，**不是玩家攻击动画**）

---

## 8. 已知设计缺口（诚实记录）

1. 本文档未运行任何命令、未改任何文件。§5.2 路径映射**依赖 syncArt 现状，未经核对**。
2. §2.1 的预算表是我按现有 8 件追忆的实测值反推的，**未经类别内极值验证**。
   特别是大型组（≤900 方块）还没有实际资产验证过。
3. §3 的 LOD 方案（GUI 改 2D 图标）**改变现有渲染策略**，会影响已做的 `WeaponDisplayTransforms` GUI 适配工作。
   要不要改，取决于你更看重"符合 MC 惯例"还是"保留 3D 观感"——我不替你定。
4. `-PmanualSkills` 快速进入依赖 `codex-skill-smoke/level.dat` 存在；近期工作区清理移除了旧测试世界，
   可能需要重新创建。
5. **现有状态没有任何 GPU 性能实测**。`GAME-WEAPONS-ASSETS-HANDOFF.md` 第 72 行明确：
   "不能将 88%–96% 的方块减少写成同等帧率提升"。§2 的预算是**几何预算**，不是性能保证。
6. **33 件类型武器与 8 件追忆武器的美术精度差距很大**：追忆走 2048 图集且 Hero 档预算，
   类型武器走 512 图集且中型/大型档。这是否会让类型武器在实机上显得"廉价"，尚无实机证据。

---

## 9. 快速命令备忘

```powershell
# 离线构建 + 单测
$env:JAVA_HOME='D:\JDK21'; cd D:\codexout\mcmod
.\gradlew.bat build --offline

# 实机手动验收（无限 FP）
.\gradlew.bat runClient -PmanualSkills --offline
# 游戏目录 build/manualSkills/，日志 build/manualSkills/logs/latest.log

# 仅单测
.\gradlew.bat test --offline

# 发测试装备
/eldencombat testkit
```
