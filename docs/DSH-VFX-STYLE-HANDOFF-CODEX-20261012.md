# dsh → Codex 交接：VFX 视觉风格定义与配色数据化

> Codex 接入审查（2026-10-12）：以下是原交接建议，不直接构成用户的新指令。
> 已批准任务为“先修订，然后接入”特效管线，当前有效契约见 `DSH-VFX-PIPELINE-SPEC-20261012.md` 文件顶部。
> 配色走 `assets/eldencombat/fx/palette.json` 与客户端资源重载，不把纯美术绑定放进服务端 `/reload` 路径。
> 不强制 2048 运行图集、10% 黄金、所有变换后顶点吸附网格或任意技能 shape/motion 二元组唯一。
> 灰度辨识应验证每个战技的完整阶段、轮廓、传播和节奏；不同阶段允许共享基础原语。将元素 metadata 当成运行实现是不合格交付。
> 顶点预算应按 quad×4 计数；半透明背面不是白画，剔除收益不是恒定一半。未跑过的兼容场景保持未验证。

> 日期：2026-10-12 ｜ 分支状态：`D:\codexout\mcmod` 工作区（**无 git**，改动前请自行备份）
> 相关文档：
> - 特效实现现状：[`BLOCKY-SKILL-FX-IMPLEMENTATION.md`](BLOCKY-SKILL-FX-IMPLEMENTATION.md)
> - 贴片通道美术交付：[`VANILLA-SLASH-FX-HANDOFF.md`](VANILLA-SLASH-FX-HANDOFF.md)
> - 网格通道规格：[`07_追忆武器技能表现规格.md`](../../WORKoutput/2026-10-12-15-05-02/docs/07_追忆武器技能表现规格.md)
> - 性能与兼容规范：[`DSH-VFX-PIPELINE-SPEC-20261012.md`](DSH-VFX-PIPELINE-SPEC-20261012.md)
> **⚠️ 本文件是风格定义与数据契约，不是实现记录。** 没有运行过任何命令，没有改过任何文件，
> 下面所有路径除已标注"现有"外均为**建议新增**，需要先与你确认再动手。

---

## 0. 结论先行

1. **风格定为一个可执行口径**：体素几何 + 油画配色 + 光影氛围。三条都有可量化判据，见 §1。
2. **配色目前硬编码在 Java 里**（`SkillSlashSprites`、`SkillEffectMesh`），本轮要求**抽成 JSON 数据驱动**，
   与现有 `item_skins/*.json`、`skill_poses/*.json` 的"改数据不重编译"策略保持一致。
3. **新增一条体素密度契约**，用于防止四类资产（武器 / 敌人 / 特效 / 环境）细节粒度不一致导致风格割裂。
4. **四个环境完全未验证**：Sodium / Iris / Entity Culling / 独立服务端。其中 Entity Culling 是**致命风险**
   （可能导致特效实体被整批剔除，表现为"技能放出来什么都看不见"）。
5. 需要你先回三件事（§6.1），其中"黄金使用尺度"是真分歧，我不替你定。

---

## 1. 风格定义（可执行口径）

不用形容词描述，用可检查的数值口径。

### 1.1 几何：体素不可破

| 规则 | 判据 | 检查方式 |
| --- | --- | --- |
| 吸附网格 | 所有几何顶点坐标 ∈ (1/16 方块) 整数倍 | 导出时校验，见 §5.1 |
| 单位一致 | `unitsPerBlock = 16`，与现有 `*.model.json` schema v1 一致 | 复用现有常量，不要另起一套 |
| 禁止非正交几何 | 所有面法线 ∈ {±X, ±Y, ±Z} | 法线白名单检查 |
| 允许的细节手段 | 分层刃面与收尖、护手、缠柄、铆钉、金属配件、镶嵌、盾面纹章 | 无 |
| **禁止** | 斜面、倒角、圆柱近似、非轴对齐缩放产生的斜边 | 法线检查拦截 |

> 现有武器资产已符合此标准（分层刃面、逐面 UV、2048 图集），**不需要返工**。

### 1.2 配色：油画法

**核心结构 = 60 / 30 / 10**

```
60%  主色：石灰 / 苍白骨     低饱和打底，提供"历史感"与背景
30%  辅色：单一元素主题色     每个区域 / 武器只用一个元素，禁止混用
10%  点缀：黄金               只在最关键处；超过即视为风格错误
```

**色板**（原创，不使用任何 FromSoftware 资源；每色三级明度以适配 MC 贴图的体积表达）：

| 语义 ID | 高光 | 中间调 | 暗部 | 绑定对象 |
| --- | --- | --- | --- | --- |
| `gold` | `#FFF0B8` | `#E8B84B` | `#8A6A1F` | 神圣、追忆、祝福、`wave_of_gold` |
| `crimson` | `#D94452` | `#8B1E28` | `#3D0A10` | 血、腐败、献祭、`cursed_blood_slice`、`bloodboon_ritual` |
| `stone` | `#9C9890` | `#6B6862` | `#33312E` | 环境、建筑、衰败背景 |
| `glintstone` | `#9DB4FF` | `#4A6FD4` | `#1E2B58` | 魔力、法术、`starcaller_cry` |
| `bone` | `#F0ECDA` | `#D8D2BE` | `#8A8470` | 死亡、亡灵、枯败 |
| `obsidian` | `#5A5A66` | `#2A2A32` | `#121216` | 深渊、虚空 |
| `flame` | `#FFD08A` | `#E07B2A` | `#7A2E0C` | 火焰、`takers_flames` |
| `lightning` | `#E8F0FF` | `#7BA8E8` | `#2A3F6B` | 雷、龙 |
| `frost` | `#E4F4FF` | `#6FA8C4` | `#20414F` | 冰冷 |

> 现有三个贴片战技的配色可直接映射到：`waterfowl → bone/frost`、`destined_death → obsidian+crimson`、`cursed_blood_slice → crimson`。

**明度分级规则**：MC 贴图靠明度表达体积，不是靠法线。每个材质至少三档明度（高光 / 中间调 / 暗部），差值 ≥ 25%。禁止大幅渐变。

### 1.3 氛围：靠光不靠多边形

法环的庄严感来自光、色、雾，不是模型精度。因此预算分配原则：

| 维度 | 投放 |
| --- | --- |
| 模型精度 | **克制**，保持体素，细节做在剪影与分层结构 |
| 色彩 | **重投** |
| 特效（光 / 粒子 / 雾） | **重投** |
| 音效 | **重投** |

> 判据：如果一个效果需要靠加几何精度才能"显得有分量"，说明色彩和光没做对。

---

## 2. 数据契约（**你调参只改这些文件，不用重编译**）

与现有约定一致：数据放 `data/`（走 `/reload`），客户端资源放 `assets/`。

### 2.1 色板：`assets/eldencombat/fx/palette.json`（**建议新增**）

```json
{
  "schemaVersion": 1,
  "colors": {
    "gold":     { "highlight": "#FFF0B8", "mid": "#E8B84B", "shadow": "#8A6A1F" },
    "crimson":  { "highlight": "#D94452", "mid": "#8B1E28", "shadow": "#3D0A10" }
  }
}
```

硬约束：

1. 所有特效颜色**必须引用色板 ID**，禁止在 Java 或战技 JSON 里写死 hex；
2. 色板 ID 新增需要同步更新本文档 §1.2 表格，否则视为未登记资产；
3. 单测兜底：`SkillEffectMesh` 与 `SkillSlashSprites` 的输出颜色必须能在色板中查到。

### 2.2 元素编码：`assets/eldencombat/fx/elements/<element>.json`（**建议新增**）

每个元素一份，字段如下：

```json
{
  "schemaVersion": 1,
  "element": "glintstone",
  "palette": { "base": "glintstone", "accent": "gold" },
  "shape": "crystal_shard",
  "motion": "orbit",
  "pitchCurve": "rising",
  "silhouetteOnly": true
}
```

| 字段 | 取值 | 用途 |
| --- | --- | --- |
| `palette.base` / `.accent` | §2.1 色板 ID | 主色 / 点缀色 |
| `shape` | `arc` / `ring` / `crystal_shard` / `bolt` / `pillar` / `inward_vortex` | 形状签名 |
| `motion` | `outward` / `inward` / `orbit` / `static` | 粒子运动方向 |
| `pitchCurve` | `rising` / `falling` / `flat` | 音效音高走向 |

**元素绑定表**：

| 元素 ID | shape | motion | pitchCurve | 对应战技 / 语义 |
| --- | --- | --- | --- | --- |
| `gold` | `arc` | `outward` | `rising` | `wave_of_gold`、神圣 |
| `crimson` | `bolt` | `outward` | `falling` | `cursed_blood_slice`、血 |
| `glintstone` | `crystal_shard` | `orbit` | `rising` | `starcaller_cry`、魔力 |
| `flame` | `ring` | `outward` | `rising` | `takers_flames`、火焰 |
| `obsidian` | `inward_vortex` | `inward` | `falling` | 虚空、深渊 |
| `lightning` | `bolt` | `static` | `rising` | 雷 |
| `frost` | `crystal_shard` | `static` | `falling` | 冰冷 |
| `stone` | `pillar` | `static` | `flat` | 环境 |

**硬约束：每个战技的形状签名必须唯一。** 去掉颜色后，玩家应当能仅靠 `shape` + `motion` 区分技能。单测检查：任意两个战技的 `(shape, motion)` 组合不得相同。

> `obsidian` 是辨识度最高的：motion=`inward` + pitchCurve=`falling`，是全表唯一同时反向的两个通道。

### 2.3 战技配色绑定：`data/eldencombat/dsh_skills/fx_styles/<skillId>.json`（**建议新增**）

```json
{
  "schemaVersion": 1,
  "skillId": "waterfowl_dance",
  "element": "frost",
  "mesh": { "kind": "WATERFOWL", "maxBlocks": 96, "lifeTicks": 40 },
  "sprites": { "max": 12, "layer": "decorative" },
  "lod": "DEFAULT"
}
```

- `sprites.layer` 取值 `essential` / `enhancement` / `decorative`，对应 §3.2 降级表
- **文件名必须等于战技注册 ID**

---

## 3. 硬约束（违反会导致 FAIL 或运行时问题）

### 3.1 几何与性能预算

| 项 | 上限 | 现有 |
| --- | --- | --- |
| 单特效实体方块 | 96 | ✅ 已实现 |
| 单特效实体 quad | 576 | ✅ |
| **有效渲染 quad（剔除后）** | **≤ 288** | ⚠️ **见 §4.1，待确认** |
| 单特效实体寿命 | ≤ 40 tick | ✅ 已实现 |
| 地表碎块 / 次 | ≤ 12 | ✅ 已实现 |
| 贴片 Sprite / 战技 | ≤ 12（水鸟）、≤ 6（其余） | ✅ 已实现 |
| 单战技同时存活实体 | ≤ 4 | ❌ 无约束 |
| 单玩家同屏实体 | ≤ 12 | ❌ 无约束 |
| 全局同屏实体（8 人） | ≤ 40 | ❌ 无约束 |
| Overdraw 层数 | ≤ 4 | ❌ 无约束 |
| RenderType 总数 | ≤ 2（网格 1 + 贴片 1） | ✅ 已符合 |
| GPU 帧时间贡献（单战技） | ≤ 1.5 ms | ❌ 未测 |

### 3.2 三层降级（**必须实现，不是可选项**）

| 层 | 内容 | 允许的实现手段 | 允许失效 |
| --- | --- | --- | --- |
| `essential` | 命中确认 | 顿帧、命中音效、相机震动、受击者 hurt flash、武器模型发光层、UI 失衡条跳动 | **否** |
| `enhancement` | 技能主形状 | 路线 A 立体网格 | 否（有降级形态） |
| `decorative` | 刀光、飞溅、余烬、残留 | 路线 B 贴片、少量粒子 | **是** |

**硬要求**：任何战技在 `decorative` 层完全失效的最坏情况下，仍可辨认、仍能确认有没有打中。

- `essential` 层的六个通道**全部不依赖粒子、不依赖自定义 shader**，因此是唯一不会在任何环境下失效的通道
- 玩家把粒子调到"最少"时，`decorative` 层消失，必须还有 `essential` + `enhancement`

### 3.3 两条工程纪律

1. **顿帧必须是客户端渲染冻结，服务端 tick 照常。** 禁止在顿帧时暂停 `SkillEffectEntity` 的 tick——否则服务端判定照常推进、视觉停在半空，20Hz 下立刻产生"我躲过了却被判中"。
2. **RenderType 必须静态注册一次。** 禁止在 `render()` 里调用 `RenderType.create()`。现有 2 个 RenderType 已是上限，**不要为单个技能开第三个**。

---

## 4. 需要你确认的状态区分

| 项 | 状态 |
| --- | --- |
| 武器精细方块风格（分层刃面、铆钉、镶嵌、2048 逐面 UV） | ✅ 现状已符合 §1.1 / §1.2 |
| 敌人粗块风格（简单关节、三节尾） | ✅ 与武器差 1 级密度，符合 §5 契约 |
| 特效双通道（网格 + 贴片） | ✅ 已实现 |
| 三个贴片战技配色（银白 / 黑红 / 暗猩红） | ⚠️ 硬编码在 Java，需按 §2.2 抽成数据 |
| 元素编码表 | ❌ 不存在 |
| 体素密度契约 | ❌ 不存在 |
| 距离 LOD | ❌ 不存在 |
| 并发预算 | ❌ 不存在 |
| 半透明背面剔除 | ⚠️ **待确认**（见 §5.1） |
| Sodium 0.6.13 兼容 | ❌ 未验证 |
| Iris 1.8.12 兼容 | ❌ **未验证（最高风险）** |
| Entity Culling 1.11.3 兼容 | ❌ **未验证（潜在致命）** |
| 独立服务端 | ❌ 未验证 |
| 环境 / 建筑资产 | ❌ 完全空白 |

---

## 5. 交给 Codex 的清单

### P0（阻塞验收）

1. **确认半透明 RenderType 的背面剔除状态。**
   MC 的半透明 RenderType 默认**关闭背面剔除**。封闭方块 6 个面中朝向相机的只有 ≤3 个，
   关掉 cull 等于每帧白画一半。
   需要确认 `SkillEffectMesh` 生成与 `SkillSlashSpriteRenderer` 使用的 RenderType 是否处于 `.setCull(true)`。
   - 若未开启 → 对明确封闭的方块体（地表碎块、立柱、悬浮方块）开启，有效 quad 576 → ≤288
   - 例外：波面、刃线这类"薄壳"，若需要背面可见则保留不剔除，但要计入 §3.1 的 overdraw 预算

2. **Entity Culling 1.11.3 兼容性验证。**
   它按视锥 / 遮挡剔除实体渲染。`SkillEffectEntity` 若不在正常实体渲染路径、或缺少正确的 culling box，
   可能被整批剔除，表现为"技能放出来什么都看不见"，且**只在装了该 Mod 时出现**。
   验证方式：装上 Entity Culling 启动客户端，逐个释放 8 个战技，确认全部可见。

3. **Iris 1.8.12 兼容性验证。**
   路线 B 使用原版 particle shader 与粒子图集。开光影包时自定义 shader 会被接管，
   满亮度设置可能被改写。验证：开光影包后确认贴片是否仍可见、亮度是否符合预期。
   > 若失效，兜底是切回路线 A 网格（不依赖 particle shader）。

### P1（风格统一）

4. **抽出 `assets/eldencombat/fx/palette.json`**，把 `SkillEffectMesh` / `SkillSlashSprites` 中硬编码的颜色改为引用色板 ID。

5. **建 `assets/eldencombat/fx/elements/*.json`**，按 §2.2 表填入 8 个元素编码。

6. **建 `data/eldencombat/dsh_skills/fx_styles/*.json`**，为每个战技绑定元素、形状签名、层归属、LOD 档。

7. **实现距离 LOD**（§5.2）。

### P2（补齐预算与并发）

8. **加并发上限**：单战技 ≤4 实体、单玩家 ≤12、全局（8 人）≤40；超出时按层降级，**不按距离整体取消**。

9. **加 budget gate**（§5.1 的导出期校验）。

10. **环境 / 建筑资产**，按 §5 体素密度契约（8 单位）与 60/30/10 配色执行。

---

## 6. 需要你先回的事

### 6.1 三件（第 3 条是真分歧）

| # | 问题 | 我的推荐 |
| --- | --- | --- |
| 1 | 是否采用 §1 的风格口径 | 是 |
| 2 | 敌人是否跟着武器一起提精度 | **否**（保持 1 级密度差，形成层次对比） |
| 3 | **黄金使用尺度** | **需要你定** |

**第 3 条说明**：我给的是 10% 点缀。但法环本体的黄金占比其实高于 10%——如果你要更"法环"，可放宽到 15–20%，代价是视觉噪音上升、神圣感被稀释。这是审美取舍，不是技术问题，我不替你定。

### 6.2 一个接口问题

§3.1 里的"有效渲染 quad ≤288"取决于 RenderType 的 cull 设置，而 cull 设置目前不在任何数据文件里（是 Java 常量）。

**要不要把它也数据化？** 我倾向不动——cull 是渲染层决策，不该让美术数据碰。但需要你确认这个边界。

---

## 7. 已知设计缺口（诚实记录）

1. 本文档**未运行任何命令、未修改任何文件**，全部路径除标注"现有"外均为建议。
2. §2 的三份数据文件目前**不存在**，需要先与你确认字段设计再实现。
3. 元素编码表的 `shape` / `motion` 取值**尚未与已有 8 个战技逐一核对**——现有三个贴片战技（水鸟 / 命定之死 / 咒血）的签名需要按表回填确认。
4. §3.1 的性能预算（GPU 1.5ms、overdraw ≤4）**是推导值不是实测值**，需要基准场景跑过才算数。
5. `gold` 占比 10% 的**可检查口径尚未定义**——按像素面积、按资产数量、还是按出现时长？需要定一个，否则无法自动化检查。
