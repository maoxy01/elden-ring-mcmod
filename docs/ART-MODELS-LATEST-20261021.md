# 建模资产总览：最新版在哪、做了什么

> 生成：2026-10-20 21:58（本机）｜批次目录名沿用 `20261021`
> 范围：mcmod 自制建模（Blockbench 工程 + 贴图 + 图标 + 验收图），不含 14 件外部提供方资产

## 0. 一句话结论

**建模（Blockbench 工程）只有一份权威最新文件**：

- **28 件手持武器** → `art/equipment-2d/blockbench-all-items-20261018/`（1 个总工程 + 28 个独立工程 + 94 张贴图）
- **抓钩 / 拳套的穿戴手甲** → `art/equipment-2d/worn-hand-gear-20261021/`（2 个工程 + 1 张图集 + 图标 + 验收图）

四件武器的重做交付副本与包内总工程 **hash 完全相同**，不是第二版。旧代次只存在于带日期的 `revisions/`、`backups/`、`redo-*/` 快照目录。

**一处必须知道的差异**：运行层（`art/equipment-2d/full/` + `routes/2d.json` + 白名单）**仍是四件武器改版前的贴图**。这是"候选/已采纳"两层设计的结果，不是重复建模；采纳步骤见 §5。

## 1. 权威文件清单

### 1.1 28 件手持武器（现行游戏内）

| 项 | 路径 | 规模 / 校验 |
|---|---|---|
| 总工程 | `art/equipment-2d/blockbench-all-items-20261018/all-items-optimized.bbmodel` | 1,872,527 B；sha256 `0203520ea8ad…`；2476 立方体 / 122 分组 / 94 贴图 / 28 个物品根组 |
| 独立工程 | `…/items/*.bbmodel`（28 个，2026-10-20 21:16 同批） | 每件 3 状态叠在同一握点、握点=模型原点、内嵌 3 张贴图 |
| 状态贴图 | `…/textures/*.png`（94 张） | front / gui_16 / gui_32 |
| 清单 | `…/manifest.json`、`…/validation.json` | 28 个 `modelSha256` + 94 个状态 sha/尺寸/像素统计 |
| 溯源 | `before-current.bbmodel`、`input-current.bbmodel` | 改前 / 输入快照，**不是建模版本** |

**本轮实跑复核**：总工程 1,872,527 B、sha `0203520ea8ad…`；`28/28` 件 `items` 与 `manifest.modelSha256` 全部匹配（0 不匹配、0 缺失）；94 张贴图。

### 1.2 四件重做（抓钩 / 拳套 / 咒剑 / 碎星）交付快照

| 项 | 路径 | 关系 |
|---|---|---|
| 交付副本 | `E:\dshwork\blockbench-ai\redo-20261021\all-items-mc-flat-redo.bbmodel` | 与 §1.1 总工程**同一 hash**（`0203520ea8ad…`），只是副本 |
| 说明 | `…\redo-20261021\REPORT.md`、`report-final.json` | 逐件改法与数字 |
| 配套 | `sprites/`(12)、`qa/`(8)、`qa_sheet.png`、`compare_ref.png` | 对照与验收图 |

改法：按 `dist/法环武器图标/武器/` 官方图重画，MC 扁平薄片（刃身 1 unit / 柄 2 unit）；咒剑改近黑紫阴森色调；碎星剑身等宽、只在剑尖收拢；握点（`anchorPixels`）逐件保持。

### 1.3 穿戴手甲（本轮新做）

| 物件 | 文件 | 规模 / 校验 | 形态 |
|---|---|---|---|
| 抓钩 | `worn-hand-gear-20261021/claw_worn.bbmodel` | 76,098 B；sha `8fa7cb8684ad…`；104 立方体（导出 94）/ 6 组 / 1 图集 | 手背木板 4 片 + 2 道钢箍（6 铆钉）+ 指节护板 + 3 根 5 段弯爪 + 2 道掌带 + 腕带 |
| 拳套 | `worn-hand-gear-20261021/fist_worn.bbmodel` | 53,155 B；sha `de204285f66f…`；66 立方体（导出 56）/ 6 组 / 1 图集 / **0 mesh** | 7.5³ 方形铁头 + 正面加强板 + 上下盖沿 + 4 条角包边 + 赤道铁箍（4 铆钉）+ 4 段喇叭护腕 + 前臂皮带 |
| 贴图 | `textures/worn_atlas.png` | 128×128 单图集，逐面 UV，1 texel = 1/16 格 | 铁 / 锈 / 钢 / 木 / 皮革 / 黄铜 |
| 图标 | `icons/<id>_gui_16.png`、`_gui_32.png` | 16 原生 + 32 高清 | 由模型 64px 渲染收边 |
| 验收 | `qa/`（7 张）、`qa_worn_sheet.png` | 正面 / 3-4 / 手背（外侧）/ 出拳侧视 / 出拳正面 / 半抬姿势 | Blockbench 场景自建相机 |
| 契约 | `WEAR-CONTRACT.md`、`manifest.json` | 坐标、枢轴、组结构、镜像、渲染接线 | 供代码侧照接 |

**挂点**：右臂枢轴 `(-5,22,0)`、左臂 `(5,22,0)`；`arm_right` / `arm_right_l` 组 origin 即枢轴；左手是右手关于 x=0 的镜像副本（运行时**不要**再翻）；参考假人 `ref_player` 全部 `export=false`。

**生成器（工作目录，非资产）**：`E:\dshwork\blockbench-ai\worn-20261021\` → `wg_lib.py`（图集/立方体/mesh/写出）、`wg_build.py`（两件几何）、`make_icons.py`、`sheet.py`、`package.py`、`verify_pack.py`（28 件复核）、`compare_sprites.py`（候选 vs 运行层比对）；`out/` 为构建产物，与交付文件同 hash。

### 1.4 其它建模资产（既有，本轮未改）

| 资产 | 权威路径 | 说明 |
|---|---|---|
| Margit 恶兆妖鬼 | `art/weapons/Margit_Fell_Omen.bbmodel`（141.4 KB，sha `BC273E2A2E…`） | 生成器 `E:\dshwork\blockbench-ai\margit_v2\gen2\gen2.mjs`，产物与权威同 hash |
| 玩家 / 早期武器生成批次 | `art/player/`、`art/remembrance-game/`、`art/weapons/` | 2026-10-11/10-12 早期批次；现行运行资产由 Gradle 从 `art/equipment-2d/full` 生成，不在这些 bbmodel 上直接改；有 `backup-manifest.json` 引用，保留作历史 |

### 1.5 候选层 vs 运行层（四件武器）

**建模层已是最新；运行层贴图仍是改版前**。逐像素比对结果（`compare_sprites.py`）：

| 物件 | 状态 | 候选层（包内，最新） | 运行层 `full/sprites` | 像素相同 |
|---|---|---|---|---|
| 抓钩 | front | `2955cf37d9…` | `7628b14ee0…` | 否 |
| 抓钩 | gui_16 | `b02cc660a0…` | `b902bf510a…` | 否 |
| 抓钩 | gui_32 | `2955cf37d9…` | `7628b14ee0…` | 否 |
| 拳套 | front | `1a38ada5cc…` | `5bc1c527cc…` | 否 |
| 拳套 | gui_16 | `198d774b8e…` | `88f030cfe4…` | 否 |
| 拳套 | gui_32 | `1a38ada5cc…` | `5bc1c527cc…` | 否 |
| 咒剑 | front | `b8eb485058…` | `0b9ae9ac38…` | 否 |
| 咒剑 | gui_16 | `f268746afc…` | `30f0900a42…` | 否 |
| 咒剑 | gui_32 | `95bee7ba5a…` | `f18cc64854…` | 否 |
| 碎星 | front | `486ede885b…` | `25c0b13383…` | 否 |
| 碎星 | gui_16 | `6eab3029e1…` | `96b72ef6c0…` | 否 |
| 碎星 | gui_32 | `2e7dc8678d…` | `2da942180e…` | 否 |

采纳时会变的派生数（世界长度不变，握点不变，缩放随轮廓跨度变）：

| 物件 | 世界长度 | 跨度 候选 / 运行 | 缩放 候选 / 运行 |
|---|---|---|---|
| 抓钩 | 0.558 格 | 36.069 / 30.798 px | 0.4950 / 0.5798 |
| 拳套 | 0.306 格 | 33.941 / 24.749 px | 0.2885 / 0.3957 |
| 咒剑 | 2.088 格 | 73.553 / 73.539 px | 1.8168 / 1.8172 |
| 碎星 | 1.980 格 | 74.953 / 77.853 px | 1.6902 / 1.6277 |

## 2. 版本纪律

1. **一件资产 = 一个权威 bbmodel**；其余同名文件只能是交付副本（必须同 hash）、批次快照（`revisions/`、`backups/`、`redo-*/`）或改前/输入溯源（`before-current`、`input-current`）。
2. 同物品多代次时以最新批次目录 + `manifest.modelSha256` 为准；`items/*.bbmodel` 与清单不符即视为过期。
3. 交付副本与权威 hash 不一致时以权威为准，重新导出副本。
4. 运行层（`full/` + `routes/2d.json` + `publishable-assets.json`）是**已采纳**的一层，改动必须走 §5 的采纳步骤，不能直接覆盖文件（白名单按 sha256 校验，覆盖不改清单会构建失败）。

## 3. 本轮删除 / 覆盖

| 对象 | 处理 |
|---|---|
| 拳套球形版（低模 mesh 球） | 被方形铁头覆盖，磁盘已无副本（`fist_worn.bbmodel` 85,967 B → 53,155 B） |
| 图标中间件（`claw_icon32`、`claw_icon_preview`、`claw_icon_src`、`fist_icon_src`） | 删除；保留 `*_icon64.png` 作图标原始渲染 |
| 旧图标脚本 `icons.py`、临时脚本 `tmp_preview.py`、`cleanup.py`、`icon_sheet.py` | 删除；图标统一由 `make_icons.py` 生成 |

## 4. 校验记录（本轮实跑）

- 28 件包：master 1,872,527 B、sha `0203520ea8ad…`；`28/28` 件 sha 与清单一致；94 张贴图；重做副本同 hash。
- 两个穿戴工程 Blockbench 原生 codec 重载通过：抓钩 104 立方体 / 6 组 / 1 贴图；拳套 66 立方体 / 6 组 / 1 贴图（无 mesh）。
- 图标与验收图全部来自 Blockbench 场景（自建 WebGLRenderer + 固定相机）。

## 5. 还没做的

### 5.1 四件武器的运行层采纳（一步到位清单）

1. 把包内 12 张 `textures/<id>_<state>.png` 覆盖到 `art/equipment-2d/full/sprites/`；
2. 重算并写入 `full/equipment-42.json` 对应 4 件的 `variants`/`gui`（`sha256`、`boundsInclusive`、`opaquePixels`、`colors`、`components8`、`anchorPixels`）与 `sourceSpanPixels`；
3. 更新 `full/publishable-assets.json` 白名单里这 12 个文件的 `sha256`（漏改会被 `generateEquipment2d` 拒绝）；
4. 更新 `routes/2d.json` 这 4 件的 `geometry.sourceSpanPixels`、`derived.uniformXYScale` 与 7 个上下文的 `display.*.scale`（按 §1.5 的新缩放）；
5. 跑 `gradlew generateEquipment2d` 验证路由/白名单/厚度全部通过，再做逐物品、逐上下文的游戏姿态复核。

### 5.2 穿戴件进游戏

需要新开 `worn` 路由 + 玩家手臂渲染层（现有管线只接受 42 条 `own_sprite` 路由，直接塞入会构建失败），接线细节见 `WEAR-CONTRACT.md` §5。

旧的手持 `claw`/`fist` 仍是 42 条路由在用的现行资产，所以保留未删；穿戴版落地后再切换。

## 6. 三个入口

- 手持 28 件：`art/equipment-2d/blockbench-all-items-20261018/all-items-optimized.bbmodel`
- 穿戴 2 件：`art/equipment-2d/worn-hand-gear-20261021/claw_worn.bbmodel`、`fist_worn.bbmodel`
- 穿戴契约 + 图标 + 验收图：`…/worn-hand-gear-20261021/WEAR-CONTRACT.md`、`icons/`、`qa_worn_sheet.png`
