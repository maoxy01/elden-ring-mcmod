# dsh → Codex 交接：美术建模执行任务包

> 日期：2026-10-12 ｜ 工作区：`D:\codexout\mcmod`（**无 git**，改动前自行备份到 `art/backups/<name>-<reason>-<seq>/`）
> 相关文档：
> - 资产规范与预算：[`DSH-WEAPON-ART-PIPELINE-CODEX-20261012.md`](DSH-WEAPON-ART-PIPELINE-CODEX-20261012.md)
> - 风格与色板口径：[`DSH-VFX-STYLE-HANDOFF-CODEX-20261012.md`](DSH-VFX-STYLE-HANDOFF-CODEX-20261012.md)
> - 特效性能管线：[`DSH-VFX-PIPELINE-SPEC-20261012.md`](DSH-VFX-PIPELINE-SPEC-20261012.md)
> - 追忆网格契约：[`GAME-WEAPONS-ASSETS-HANDOFF.md`](GAME-WEAPONS-ASSETS-HANDOFF.md)
> - 玩家与装备接入：[`ASSETS-FOR-DSH.md`](ASSETS-FOR-DSH.md)
>
> **本文档性质**：可执行工单，不是设计讨论。每一条都应能落到"改哪个文件 / 跑哪个命令 / 断言什么"。
> 本文件未运行任何命令、未修改任何资产。§2 台账依据当前目录实际文件整理；§5 预算为**推导值**，见 §10 第 2 条。

---

## 0. 结论先行

1. **`syncArt` 的缺口已经补上了。** `build.gradle` 第 126–163 行已含 `art/weapons` 与 `art/remembrance-game`
   两个来源（`assetLayoutVersion = 4`）。我上一份文档中"33 件类型武器可能无法渲染"的 P0 **已失效**，作废。
2. **【本次实测发现，最严重】41 件武器在 GUI / 物品栏里全部是同一个占位模型。**
   `grep -l weapon_placeholder models/item/*.json` 命中 **41 个**，一件不差；
   `models/item/weapon_placeholder.json` 的 `parent` 是 `minecraft:builtin/entity`，
   **没有任何 texture 字段**，只有一个 display 变换块。
   结论：玩家在物品栏、快捷栏、JEI、掉落物处看到的 41 件武器**外观完全相同且无贴图**。
   这不是"可能看不见"，是已经确认的现状。因此 Batch A 排在最前。
3. **锻造 / 质变系统已经落地，不要再按空项处理。**
   `textures/item/` 现有 58 项，含 8 个 `affinity_template_*`（heavy/keen/quality/magic/sacred/blood/occult/fire）、
   `upgrade_template_1~8` + `somber_upgrade_template_1~5`、`smithing_stone_1~8`、
   `somber_smithing_stone_1~5`、`ancient_dragon_*`、`bell_bearing_1~4`、`reforge_template`。
4. **除上述之外，建模真正的缺口是 LOD，不是模型数量。** 41 件武器 + Margit + 玩家套件全部只有单一精细度，
   没有中间档、没有独立的 GUI 图标形态。`GAME-WEAPONS-ASSETS-HANDOFF.md` 第 66 行已承认这一点。
5. **环境 / 建筑不要走自定义 skeleton schema**，必须走 MC 原版 block model（§4.5）。这是本文档唯一一条
   会产生结构性分歧的技术判断。
6. **建`art/asset-registry.json`（§3）作为全项目资产台账。** 没有台账就没法做预算治理——
   现在没人知道全项目有多少资产、每个占多少预算、哪些缺 LOD。

---

## 1. 职责边界

### 1.1 你负责

| 项 | 范围 |
| --- | --- |
| 产出 | `art/` 下所有资产与 art tooling 脚本（`art/tooling/*.mjs|cjs`） |
| 阶段 | 源版建模 → 游戏版减面 → 静态自检 → 离线预览 → 交付包目录整理 |
| 文档 | 每个批次回填 `art/<组>/validation.json` 与本文档 §6 的勾选状态 |
| 台账 | 维护 `art/asset-registry.json` |

### 1.2 你不负责（由 dsh / 其他 agent 接）

| 项 | 原因 |
| --- | --- |
| Minecraft Java 代码 | 资产与游戏解耦，`syncArt` 是唯一耦合点 |
| `build.gradle` 的 `syncArt` 改动 | 如需新增目录映射，提需求给我，不要自己改构建脚本 |
| 材质 /shader / RenderType 注册 | 渲染层决策，见 `DSH-VFX-PIPELINE-SPEC-20261012.md` §5 |
| 音效成品 | `combat.sound-design.json` 仍是规格，不是音频库 |
| 实机游戏验收 | 需要人工跑客户端，见 §8.3 |

---

## 2. 现有资产台账（现状盘点，不要重复造）

### 2.1 已交付

| 组 | 目录 | 内容 | 状态 |
| --- | --- | --- | --- |
| 类型武器 | `art/weapons/` | 33 件，512×512 图集，`*.bbmodel` + `.model.json` + 部分 `.mesh.json` + 2 份 `.animations.json` | ✅ 已接入 JAR |
| 追忆武器（源版） | `art/remembrance/` | 8 件，2048×2048 图集，**高细节编辑源，不进 JAR** | ✅ R3 定稿 |
| 追忆武器（游戏版） | `art/remembrance-game/` | 8 件减面版，`.model.json` + `.mesh.json` + `catalog.json` + `asset-manifest.json` | ✅ 已接入 JAR |
| BOSS | `art/margit/` | Margit 38 方块 / 19 骨骼 + `spectral_dagger` / `spectral_hammer` | ✅ 已接入 |
| 玩家与装备 | `art/player/` | 玩家 classic/slim 骨架、皮肤 sidecar、动作集、`equipment.png`、4 件装备模型、`fx/*.png` | ✅ 已接入 |
| 装备扁平化 | `art/flat-equipment-qa/`、`tooling/flatten-equipment.mjs` | 薄型装备网格 v2，`flat-equipment-report.json` | ⚠️ 进行中（见 §6 Batch B） |
| 特效 | `art/skill-fx/` | `meshes.js`、`budget-report.json`、`qa.json`、13 张预览图、`preview.html` | ✅ |
| **锻造 / 质变** | `src/main/resources/assets/eldencombat/textures/item/` | 8 个 `affinity_template_*`、`upgrade_template_1~8`、`somber_upgrade_template_1~5`、`smithing_stone_1~8`、`somber_smithing_stone_1~5`、`ancient_dragon_*`、`bell_bearing_1~4`、`reforge_template`，共 58 项贴图 + 对应 `models/item/*.json` | ✅ **已落地（本文档核实）** |
| 消耗品 / 护符 / 材料图标 | 同上 | 103 个 `models/item/*.json` 中约 62 个有真实贴图 | ✅ |

> **注意档位**：现有 `smithing_stone` 是 **8 档**（不是我之前文档建议的 10 档），失色 `somber` 是 **5 档**。
> 若要在 Batch B/D 里新增任何与强化相关的资产，**必须按 8 / 5 对齐**，不要引入第 9、10 档。

### 2.2 完全空白

| 缺口 | 影响的可见性 | 状态 |
| --- | --- | --- |
| **GUI 物品图标** `textures/item/<id>.png` | **41 件武器全部渲染为同一个无贴图占位模型**（见 §0 第 2 条：`weapon_placeholder.json` 无 texture 字段，被 41 个 item model 引用） | ❌ **已确认** |
| LOD1 / LOD2 中间档 | 掉落物、盔甲架、远距手持用全量 Hero 网格 | ❌ |
| 护甲套装（3D 穿戴件） | 现只有玩家基类装备层，无成套视觉 | ❌ |
| Margit 之外的敌人模型 | 只有 1 个 BOSS，没有杂兵 / 精英 | ❌ |
| 环境 / 建筑资产 | 风格文档 §4 记为"完全空白" | ❌ |
| 消耗品 / 道具图标 | 部分有真实贴图，部分待补 | ⚠️ |
| `art/asset-registry.json` | 无台账，无法做预算治理 | ❌ |

---

## 3. 资产台账：`art/asset-registry.json`（**新增，Batch A 的首件产出**）

### 3.1 为什么先做这个

现在每个资产组有自己的 `catalog.json` / `asset-manifest.json`，但没有跨组台账。结果是：
新增一个资产时无法自动判断"图集还剩多少空间""这个类别是否超了 LOD 预算""哪些资产缺图标"。
台账是所有后续自动化的依赖，成本半天，收益贯穿全程。

### 3.2 字段设计

```json
{
  "schemaVersion": 1,
  "updatedAt": "2026-10-12",
  "assets": [
    {
      "id": "sacred_relic_sword",
      "category": "weapon_remembrance",
      "lod0": { "boxes": 1029, "triangles": 11680, "path": "art/remembrance-game/sacred_relic_sword.mesh.json" },
      "lod1": null,
      "lod2": null,
      "lod3": { "icon": "textures/item/sacred_relic_sword.png", "size": [16, 16] },
      "atlas": "remembrance",
      "materialSlots": 1,
      "styleSemantics": ["gold", "bone"],
      "status": "shipped"
    }
  ]
}
```

| 字段 | 说明 | 取值 |
| --- | --- | --- |
| `id` | **必须等于注册 ID**，与其他文件命名一致 | 字符串 |
| `category` | 决定用哪一行预算（§5） | `weapon_type` / `weapon_remembrance` / `enemy` / `armor` / `prop` / `environment` / `fx` |
| `lod0..lod3` | 缺失填 `null`，**不得省略字段** | 对象 / null |
| `atlas` | 所属图集组，见 §4.4 | `weapons` / `remembrance` / `equipment` / `particle` / `block` |
| `materialSlots` | 渲染 draw call 数，**必须 = 1** | 整数 |
| `styleSemantics` | 引用色板 ID，见风格文档 §1.2 | 色板 ID 数组 |
| `status` | `wip` / `art_done` / `shipped` | 枚举 |

`status` 语义要严格：**`art_done` 只表示过了 §8.1 + §8.2 自检，不代表进游戏可见。`shipped` 必须由人工实机验收后填写。**

### 3.3 断言

用 `node tooling/verify-registry.mjs`（建议新增）检查：

| # | 断言 | FAIL 含义 |
| --- | --- | --- |
| 1 | 每个 `lod0` 存在，`boxes` / `triangles` 在 §5 对应类别预算内 | 超预算 |
| 2 | `materialSlots == 1` | 会翻倍 draw call |
| 3 | `category=weapon_*` 且 `status=art_done` 的资产，`lod3.icon` 非 null | 缺 GUI 图标，物品栏不可辨识 |
| 4 | **`models/item/<id>.json` 的 parent 不等于 `eldencombat:item/weapon_placeholder`** | 仍在用无贴图占位模型（当前 41 件全部命中） |
| 5 | `lod3.icon` 指向的文件真实存在 | 台账与磁盘不一致 |
| 6 | `styleSemantics` 中的色板 ID 在 `assets/eldencombat/fx/palette.json` 中可查 | 用了未登记颜色 |

---

## 4. 全局硬约束（所有资产通用）

### 4.1 几何

沿用现有 schema v1，与已交付的 41 件保持完全一致：

| 规则 | 判据 |
| --- | --- |
| 单位 | `unitsPerBlock = 16`，16 单位 = 1 方块 |
| 吸附 | 所有顶点坐标 ∈ (1/16 方块) 整数倍 |
| 法线 | 所有面法线 ∈ {±X, ±Y, ±Z} |
| 根节点 | 只能 1 个 `root`（环境方块除外，见 §4.5） |
| 朝向 | 模型朝 -Z；长度主轴 +Y；握持原点 `[0,0,0]` |
| **禁止** | 斜面、倒角、圆体、非轴对齐缩放产生的斜边（R3 决定，不回头） |
| 允许的细节手段 | 分层刃面与收尖、护手、缠柄、铆钉、金属配件、镶嵌、纹章 |
| 单对象 OBB | 连通分量 = 1 |

### 4.2 UV 与材质

| 规则 | 判据 |
| --- | --- |
| material slot 数 | **= 1**（加第二张贴图 = draw call 翻倍） |
| V 原点 | 文件内为下原点；`WeaponMeshData` 加载时做一次 `1-v`，**提交环节禁止二次翻转** |
| `positions` | 已除以 16，**加载端禁止再次除 16** |
| 六面 UV 顺序 | 固定 `+X/-X/+Y/-Y/+Z/-Z`，TL/TR/BL/BR 语义唯一 |
| 逐面 UV | `faceUVs[cube.name][face]` 优先，回退 `materials[].uv` |
| UV 分母 | **按 `textureSize` 两轴归一化，禁止硬编码 64 / 512 / 1024** |
| 滤波 | 运行时最近邻，不做模糊；mipmap 见 §4.4 |

> `WEAPONS-AND-ARTS-HANDOFF.md` 第 126 行记录了旧预览曾误用 512 分母给新资产 UV——**这条已修过，不要再犯**。

### 4.3 体素密度契约（防风格割裂）

风格散架的头号原因是各资产细节粒度不一致。同屏出现"精细剑砍豆腐块墙"最难看。
最小细节单元：

| 层级 | 最小细节单元 | 当前状态 |
| --- | --- | --- |
| 武器 | 2 单位（1/8 方块） | ✅ 符合 |
| 敌人 / BOSS | 4 单位 | ✅ Margit 符合 |
| 特效 | 2 单位 | ✅ 符合 |
| 道具 | 4 单位 | ⚠️ 无资产可查 |
| 环境 / 建筑 | 8 单位 | ❌ 完全空白 |

**相邻层级粒度差 ≤ 2 级。** 敌人不跟着武器提精度——粗块敌人衬托精细武器是有效的层次策略，前提是只差 1 级。

### 4.4 图集治理

| 图集 | 尺寸 | 归属 | 现状 |
| --- | --- | --- | --- |
| `weapons.png` | 512×512 RGBA | 33 件类型武器，13 个共享材质区 | ✅ |
| `remembrance.png` | 2048×2048 RGBA | 8 件追忆武器，16 个 512×512 材质区 | ✅ |
| `equipment.png` | — | 玩家 / 装备组 | ✅ |
| `skill-fx` 组 | 16×16 | 粒子 `fx/*.png` | ✅ |

硬规则：

1. **禁止为单个资产新增独立 PNG。** 一律并入所属图集。
2. 图集 UV 每个 `2×2` 像素单元共用一色（R3 决定）。
3. **mipmap 规则**：UI 图标（LOD3）关 mipmap，实体 / 手持贴图开 mipmap。
4. 图集扩容前先在 §3 台账里登记，避免 UV 布局冲突。

### 4.5 环境 / 建筑走原版 block model（**结构性判断**）

不要给方块类资产用自定义 skeleton schema v1。理由：

| 维度 | 原版 block model | 自定义 schema v1 |
| --- | --- | --- |
| 面剔除 / AO / 光影参与 | 引擎原生处理 | 需自行实现 |
| 资源包替换 | 玩家可直接换 | 不支持 |
| 玩家认知 | 标准的 `blockstates` + `models/block` 结构 | 非标准，难调试 |
| 与 entity culling / Sodium 交互 | 已验证路径 | 未验证路径 |

自定义 schema 是为**实体与手持物**设计的，那是它正确的用途。方块是另一条管线，不要复用。
产出目录：`src/main/resources/assets/eldencombat/blockstates/` 与 `models/block/`、`models/item/`。

> 这条会改变"新增环境资产"的做法。若你不同意，在动手前来问，不要在 Batch D 里默默做。

---

## 5. 分类预算表（含 LOD 链）

### 5.1 LOD 语义（映射到 MC 实际场景）

MC 没有引擎内建的按距离 LOD 切换，所以按**使用场景**定义档位，而不是按米数：

| LOD | MC 场景 | 资产形式 |
| --- | --- | --- |
| LOD0 | 第一 / 第三人称手持、展示框、盔甲架近景 | `.mesh.json` 全量 |
| LOD1 | 掉落物（≤16 格）、副手持握 | 减面版 `.mesh.json` |
| LOD2 | 掉落物（>16 格）、多人远处 | 进一步减面或公告牌 |
| LOD3 | **GUI 物品栏 / JEI / 快捷栏 / 远距掉落** | **16×16 PNG 预渲染图标** |

**强制交付规则：任何 `category=weapon_*` / `armor` / `prop` 的资产，`lod0` 与 `lod3` 必须同时交付。**
`lod1` / `lod2` 在 ≤16 格场景下与 `lod0` 差异可忽略，列为可选。

### 5.2 预算

三角形理论上限 = 方块数 × 12（6 面 × 2）。

| 类别 | LOD0 方块 | LOD0 三角 | LOD1 | LOD2 | LOD3 |
| --- | --- | --- | --- | --- | --- |
| 短武器（匕首 / 拳套 / 钩爪 / 圣印记 / 短弓） | ≤320 | ≤3800 | ≤160 | ≤60 | 16×16 |
| 中型武器（直剑 / 刀 / 曲剑 / 刺剑 / 斧 / 槌 / 矛 / 杖 / 盾） | ≤640 | ≤7600 | ≤320 | ≤120 | 16×16 |
| 大型武器（大剑 / 大曲剑 / 大斧 / 大槌 / 镰 / 戟 / 长弓） | ≤900 | ≤10800 | ≤450 | ≤170 | 16×16 |
| Hero / 巨武器（特大剑 / 特大武器 / 重弩 / **8 件追忆**） | ≤1200 | ≤14400 | ≤600 | ≤200 | 16×16 |
| 敌人（杂兵 / 精英） | ≤60 | ≤720 | ≤40 | — | 不需要 |
| BOSS | ≤80 | ≤960 | ≤50 | — | 不需要 |
| 护甲件（单件穿戴） | ≤40 | ≤480 | ≤20 | — | 16×16 |
| 道具 / 消耗品 | ≤8 | ≤96 | — | — | **仅图标** |
| 环境 / 建筑（单方块） | ≤32 | ≤384 | — | — | 走 `models/item` |

> **锚点说明**：Hero 档的 1200 / 14400 是按现有 8 件追忆的实测值反推的锚点，
> 其中亵渎圣剑实测 12348 三角已占 86%。数值未经类别内极值验证，见 §10 第 2 条。
> Margit 实测 38 方块，BOSS 档据此上浮到 80 留余量。

### 5.3 Draw call 预算

| 场景 | 上限 |
| --- | --- |
| 单件武器 / 护甲件 | 1 draw call |
| 单敌人 / BOSS | ≤3 |
| 同屏玩家（8 人）可见新增 draw call | ≤24 |
| 单环境方块（含 item form） | ≤2 |

---

## 6. 批次任务

### Batch A — GUI 图标与台账（**最高优先级，阻塞可见性**）

**为什么排第一（基于实测，不是推测）**：

```
$ grep -l weapon_placeholder models/item/*.json | wc -l
41
```

41 件武器的 item model 全部只有一行 `{"parent": "eldencombat:item/weapon_placeholder"}`，
而 `weapon_placeholder.json` 是 `parent: minecraft:builtin/entity` 且**不含 texture**。
结果是这 41 件已经精细建模的武器，在玩家最常看到的界面（物品栏 / 快捷栏 / JEI）里
**长得一模一样且没有贴图**。这批建模工作在玩家视角的回报率接近零。

修好这一项，等于让已有的 41 件资产第一次真正被看见——**收益远大于新建任何模型**。
而且这是唯一零风险的批次：不碰渲染路径、不改 Java，只加 PNG 与一行 model JSON。

| # | 任务 | 产出路径 | 验收 |
| --- | --- | --- | --- |
| A1 | 从源版批量渲染导出 41 张武器图标 | `src/main/resources/assets/eldencombat/textures/item/<id>.png` | 文件存在，16×16，非全透明 |
| A2 | 逐个改写 41 个 `models/item/<id>.json`，从 `weapon_placeholder` 改为 `parent=item/generated` + `layer0` 指向新图标 | `models/item/<id>.json` | `/reload` 后物品栏 41 件外观互不相同 |
| A3 | **不要删 `weapon_placeholder.json`**——它承载手持 display 变换，3D 手持仍在用 | 保留 | `minecraft:builtin/entity` 路径不变 |
| A4 | 建 `art/asset-registry.json`，回填现有 41 + Margit + 玩家套件 + 58 项锻造资产 | `art/asset-registry.json` | §3.3 五条断言 PASS |
| A5 | 新增 `tooling/verify-registry.mjs` | `art/tooling/verify-registry.mjs` | 可独立运行，超预算返回非零退出码 |

**A1 不要手绘。** 你们已有 `tooling/weapon-render-probe.mjs` 与 `player-scene.mjs` 的离线渲染路径，
套一个 headless 渲染循环即可。这一步的产出质量取决于光照角度，建议固定俯视 30°、正面 -Z。

> **A2 有个坑**：`weapon_placeholder` 里的 `display` 变换是手持专用的（含 `firstperson_righthand` 等）。
> 改成 `item/generated` 后 GUI 会正确显示图标，但**手持形态会退化成 2D 贴图片**。
> 正确做法是两种都要：GUI 用 `item/generated` 子模型，手持仍走 `builtin/entity` 的自定义渲染。
> 若 MC 的 model 系统不允许一个 item 同时指定两种parent，则需要由 dsh 在 Java 侧处理——
> **这条属于 §1.2 边界外的部分，遇到请停下来问我，不要自行改 Java。**

### Batch B — 护甲套装（衔接现有装备扁平化）

`flatten-equipment.mjs` 与 `equipment-material-style.mjs` 已在做薄型装备（syncArt 注释
`assetLayoutVersion 4: thin equipment meshes`）。Batch B 是这条线的延续，**不要另起炉灶**。

| # | 任务 | 说明 |
| --- | --- | --- |
| B1 | 确认 `flat-equipment-report.json` 的结论是否被采纳为基线 | 若已采纳，以下基于此扩展 |
| B2 | 3 套护甲（头 / 胸 / 腿 / 靴） | 每套 4 件，各自 §5.2 护甲预算内 |
| B3 | 每套出一张正面 + 一张 45° 侧后预览图 | `art/<组>/previews/` |
| B4 | 每件补 `textures/item/<id>.png` 图标 | 复用 Batch A 管线 |

约束：护甲件不可破体素契约（§4.1），单件 ≤1 material slot，
与现有 `equipment.png` 图集合并，不新增图集。

### Batch C — 敌人第二梯队（Margit 之外）

现在只有 1 个 BOSS，没有一个杂兵。这是形状最规整、最容易批量复制的一块内容缺口：

| # | 任务 | 预算 |
| --- | --- | --- |
| C1 | 2 个杂兵（近战 / 远程各一） | ≤60 方块 / ≤720 三角 each |
| C2 | 1 个精英 | ≤70 方块 / ≤840 三角 |
| C3 | 各自的待机 / 移动 / 攻击机械动作草稿 | 沿用 Margit 的关键帧格式 |

密度严守 4 单位（§4.3）。**不要跟着武器提精度**——这是层次策略，不是偷工减料。
每个敌人都要回答"它的剪影在 8 格外还认得出来吗"，该项进 §8.1。

### Batch D — 环境 / 建筑（**先确认 §4.5 再做**）

| # | 任务 |
| --- | --- |
| D1 | 先回复 §4.5 的技术判断（原版 block model vs 自定义 schema） |
| D2 | 一组 8–12 个建筑方块（墙 / 柱 / 地砖 / 门 / 窗 / 台阶），密度 8 单位 |
| D3 | 每个方块配 `models/item` 形式，使其可在物品栏正常显示 |
| D4 | 配色按 60/30/10，环境主色绑 `stone`，点缀 `gold` |

---

## 7. 出品流程（每个资产的固定动作）

```
① 源文件建模（Blockbench / tooling 定义，不设预算）
     ↓
② 改源版前：打哈希备份 art/backups/<name>-<reason>-<seq>/
     ↓
③ 减面出游戏版 + LOD3 图标
     目标：方块数相对源版减少 ≥ 80%
     ↓
④ 静态自检（§8.1，任一 FAIL 退回 ③）
     ↓
⑤ 离线预览 + 截图（§8.2）
     ↓
⑥ 回填 art/asset-registry.json，status = art_done
     ↓
⑦ 交付 dsh → syncArt → 构建 → 人工实机验收（§8.3）
     ↓
⑧ 验收通过 → status = shipped
```

**注意第 ⑥ 步不能跳过。** `art_done` 与 `shipped` 是两个不同的状态，在实机验收之前永远不许写 `shipped`。

---

## 8. 三道自检关

### 8.1 静态自检（`art/tooling/` 已有大部分脚本）

| # | 检查 | 现有工具 |
| --- | --- | --- |
| 1 | 几何预算（方块 / 三角）在 §5.2 内 | `verify-registry.mjs`（待加） |
| 2 | 顶点吸附 1/16 网格 | `weapon-mesh-validation.mjs` |
| 3 | 法线 ∈ {±X,±Y,±Z}，无斜面 | 同上 |
| 4 | `materialSlots == 1` | 同上 |
| 5 | OBB 连通（含断开负向用例） | `weapon-connectivity.mjs` |
| 6 | 柄刃接续无断缝 | `weapon-structure.mjs` |
| 7 | 网格索引范围 / 绕序 / `vertexCount` 与数组长度一致 | `weapon-mesh-validation.mjs` |
| 8 | mesh 与 cubes 轮廓 IoU ≥ 0.995 | `verify-game-comparison.mjs` |
| 9 | UV 分母按 `textureSize` 归一化 | `verify-pixel-weapons.mjs` |
| 10 | 六面 UV 与 BB 导出一致 | 同上 |
| 11 | `2×2` 恒色像素块（R3） | 同上 |
| 12 | **LOD3 图标存在且非空** | **待加** |
| 13 | **注册表 `id` 等于注册 ID** | **待加** |
| 14 | **`models/item/<id>.json` 的 parent 不等于 `eldencombat:item/weapon_placeholder`** | **待加**（当前 41 件全部 FAIL） |

> 「任何失败不算通过」这条保留。轮廓比较**不证明**纹理外观一致、动作正确或无内部面。
>
> 第 14 条现在是全红状态——**不要因为它全红就把这条检查删掉**，它是 Batch A 的存在理由。

### 8.2 离线预览命令

```powershell
cd D:\codexout\mcmod\art

# 类型武器
npm run build:weapons
# 追忆源版
npm run build:remembrance
# 追忆游戏减面版
npm run build:remembrance-game
# 玩家与装备
npm run build:player
# 启动预览服务
npm run preview
```

验证脚本（需要含 playwright 与 pngjs 的依赖目录）：

```powershell
node tooling/verify-preview.cjs        <depsDir>
node tooling/verify-player-preview.cjs <depsDir>
node tooling/verify-pixel-weapons.mjs  <depsDir>
node tooling/verify-flat-equipment.mjs <depsDir>
```

每组产出 `validation.json` / `preview-validation.json` 与 `previews/*.png`。

> **浏览器通过 ≠ 游戏通过。** 这行字在 `REMEMBRANCE-ASSETS-HANDOFF.md` 第 115 行出现过，
> 这里重复一次：网页验收不验证游戏战技、不验证实体裁剪、不验证兼容模组。

### 8.3 实机验收（人工，Gradle 退出码不可替代）

```powershell
$env:JAVA_HOME='D:\JDK21'; cd D:\codexout\mcmod
.\gradlew.bat build --offline
.\gradlew.bat runClient -PmanualSkills --offline
# 游戏目录 build/manualSkills/，日志 build/manualSkills/logs/latest.log
```

| # | 检查 | 不通过的含义 |
| --- | --- | --- |
| 1 | 左右手是否正确（主手 / 副手分别查） | 插槽变换错 |
| 2 | 第一 / 第三人称比例是否正常 | display 倍率待调 |
| 3 | **物品栏 / JEI / 快捷栏图标是否可辨识** | **LOD3 缺失** |
| 4 | 掉落物近距离是否正常，远距离是否可见 | LOD2 / LOD3 缺失 |
| 5 | 佩戴盔甲时的显示（尤其头盔 / 胸甲） | 渲染层级冲突 |
| 6 | 常规皮肤 + 细臂皮肤两种都要看 | 手臂模型差异 |
| 7 | `F3+T` 重载资源后是否正常 | 缓存未清 |
| 8 | 附魔后是否显示正确 | QUADS 通道 / 退化四边形展开失败 |

第 5、6 两条原本不在清单里，是我加的——它们是最容易出问题却最常漏的组合。

---

## 9. 反模式清单（出现即退回）

| # | 反模式 | 为什么 |
| --- | --- | --- |
| 1 | 给单武器开第二张贴图 | draw call 翻倍，违反 §4.2 |
| 2 | 只改名字的同形模型 | `WEAPONS-AND-ARTS-HANDOFF.md` 第 10 行已禁止 |
| 3 | 减面时删掉了柄刃连接体 | 断缝复发（`REMEMBRANCE-ASSETS-HANDOFF.md` 第 57 行已踩过） |
| 4 |  GUI 用 3D 模型渲染再斜置适配 | 物品栏渲染成本 + 不符合 MC 惯例，见 §5.1 LOD3 |
| 5 | UV 里硬编码 64 / 512 / 1024 分母 | 图集扩容即崩，§4.2 明令禁止 |
| 6 | 环境方块走自定义 skeleton schema | 见 §4.5 |
| 7 | 未备份先改源版 | 无 git，改坏不可逆 |
| 8 | 把"浏览器预览 PASS"写成"已验收" | §8.2 已明确区分 |
| 9 | 勾选 `status = shipped` 但没跑实机 | 见 §7 第 ⑧ 步 |
| 10 | 环境资产用超出 `stone` + 单一辅色的配色 | 违反 60/30/10 |

---

## 10. 已知缺口（诚实记录）

1. 本文档**未运行构建、未修改任何文件、未做实机验收**。
   §2 台账与 §0 第 2、3 条是通过列目录与 grep 核实的**文件事实**（命令见对应章节）；
   除此之外没有执行任何 npm / Gradle 命令。
2. **§5.2 的预算是推导值不是实测值。** 锚点只有 8 件追忆与 Margit 的实测数据；
   大型武器组（≤900 方块）与敌人组（≤60）还没有实际资产验证过。第一批产出后要回来校准。
3. **Batch A 的图标渲染角度（俯视 30° / 正面 -Z）是我给的初值**，需要出一批样例后目视调整。
4. **§4.5 是本文档唯一可能产生结构性分歧的判断。** 环境资产走哪条管线，动手前必须确认。
5. **全项目零 GPU 性能实测。** `GAME-WEAPONS-ASSETS-HANDOFF.md` 第 72 行明确：
   "不能将 88%–96% 的方块减少写成同等帧率提升"。§5 的预算是**几何预算，不是性能保证**。
6. **§5.1 的 LOD 档位是按使用场景定义的，不是按距离。** 如果后续引入 distance-based culling mod，
   或 dsh 在渲染层加了距离切换，这张表要重画。
7. **黄金占比的可检查口径仍未定义**（按像素面积 / 资产数 / 出现时长？），
   因此 §3.3 第 5 条只能检查色板 ID 是否登记，**无法自动检查占比是否超标**。
8. 上一份 `DSH-WEAPON-ART-PIPELINE-CODEX-20261012.md` §7 P0 第 1、2 条（syncArt 与
   `EquipmentRenderer.asset()`）状态已变化：**syncArt 缺口经核实已补全**，作废；
   第 2 条的选择逻辑是否也已更新，本文档未核实。
