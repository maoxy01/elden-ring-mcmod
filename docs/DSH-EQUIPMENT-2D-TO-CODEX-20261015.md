# 装备 2D 实施交接报告（DSH → Codex 审查）

2026-10-15。第 1 批（基础适配）。Codex 负责框架、接口与审查；本文件供审查与裁决，不表示已实机通过。

---

## 0. 一句话结论

42 条全部保持 `pending`、`ready=0`、`display=null`。**我在自查中发现一个自己代码里的结构性缺陷（§4）：`ready` 一旦置位就会双重渲染。** 它当前被 `ready=0` 掩盖，不影响现状，但必须在任何条目转 ready 前解决。**该缺陷的修法涉及接口归属，请你裁决。**

| 项目 | 值 |
|---|---|
| 路由条目 | 42（`own_sprite` 28 / `provider_model` 10 / `vanilla_model` 4） |
| `ready` / `pending` | **0 / 42** |
| `display=null` | 42 |
| 阻断项 | 130 条，8 类前缀 |
| 准入自有 PNG | 94（逐条比对 SHA-256） |
| 生成的文件 | 0 |
| 打包内研究资产 | 0 |

---

## 1. 合同 §2 边界逐行回应

| 入口 | 合同要求「DSH 必须补齐」 | 本次状态 | 说明 |
|---|---|---|---|
| `gradle/equipment-2d.gradle` | 路线分类校验、资产准入、GUI/握点/双面模型、外部资源解析；不能仅改 ready | **已实现未验** | 见 §3.1 |
| 同上 `external_model` | 条件加载提供方模型及无依赖回退 | **已实现未验** | 改为不生成 JSON，运行时复用已烘焙模型，从根上杜绝悬空 parent |
| `setupItemStates` | 提供方命名空间的显式视觉属性适配 | **已实现未验** | 仅注册 `pulling`/`pull` 两个纯数值别名 |
| `EquipmentRenderer` / `WeaponDisplayTransforms` | 新 2D 走明确模型路径；**不能再叠旧 3D 归一化和握持偏移** | **未满足** | **见 §4，本次唯一结构性缺陷** |
| `item/WeaponStacks` | 保留原 ItemStack；外部 Item 不能冒充成长 | **未改动** | 2D 路径只读栈，不写组件 |
| `server/skills/AshBindings` | 视觉切换不得绕过此入口或改 ASH_BINDING | **未改动** | 渲染层不触碰绑定 |

---

## 2. 已实现的能力

### 2.1 准入 / 生成（E01）

`tools/equipment2d/build-route-config.py` 确定性生成；`gradle/equipment-2d.gradle` 校验。

- **发布源只有 `held` 与 `gui` 两个字段**，逐条校验：白名单路径 + 实际文件 SHA-256 + 根限定 + 拒绝根。`equipment-42.json` 的 `variants[].file` 有 43 处指向 `research-only-full/`，但它们 `delivery` 为 `provider_reference_only`，**永不作为发布源**（已负向验证：强行指向该目录 → 生成器退出码 1）。
- ID 集合校验：恰为 41 件 + `test_flask`，无重复无额外。
- `provider_model` / `vanilla_model` **不生成任何 JSON**。运行时复用提供方已烘焙 `BakedModel`，loader / 父链 / 附加层 / override / 上下文变换全保留，依赖缺失时零次加载提供方类型。
- **`ready` 闸门改为硬失败**（`throw GradleException`）。初版只记 note 不中断，非法 ready 会被静默降级为「生成 0 文件」却在表里宣称就绪 —— 已修正并负向验证：构建按预期 FAILED 并给出理由。

### 2.2 客户端模型路由 / 握持 GUI / 重载快照（E02、E05）

新增 `client/equipment2d/` 8 个类。不写物品组件，不改伤害/用时/弹药/动画权威。重载为原子替换 + `generation` 递增，旧烘焙引用整体丢弃。

### 2.3 提供方适配（E03）

- 能力探针**先于**模型链解析执行，任一必需资源缺失则整链作为单元回退。
- **`coating` 图层归属已核实并更正**：携带它的是 **9 条 Spartan 近战**（dagger/thrusting_sword/katana/greataxe/hammer/great_hammer/spear/halberd/scythe），`layers=["coating","layer0"]`；**`long_bow` 只有 `layer0`**。这 9 条对应 9 个 `provider_loader_unverified`，数量自洽。我先前口头转述有误，已更正。
- `blocking` / `throwing` 记 `unsupported`（自有玩法无此状态，合同禁止发明）；弩 `firework` 记 `blocked`。

### 2.4 盾 / 弓 / 弩（E04）

盾 front/back 相反法线 + 正确 UV；大弓 `standby/pulling_0/1/2`；弩炮 `standby/loading_0/1/2/loaded`；`test_flask` 仅 `full`，`empty` 记 `blocked`。GUI 取独立 `gui_16`/`gui_32`，不被手持态吃掉。

### 2.5 数学核对（合同 §5）

`a=(16*x/W-8, 8-16*y/H, 0)`；`s=worldLengthBlocks*W/sourceSpanPixels`（分母恒为 span，非方形输入同样如此）。

| 装备 | 画布 | span | 锚点像素 | 推导锚点 | 缩放 |
|---|---|---|---|---|---|
| `great_sword` | 32×32 | 37.618080759124 | [7.5, 24.5] | `(-4.25,-4.25,0)` | 1.347437162586 |
| `shield` | 32×32 | 28 | [16.5, 16.5] | `(0.25,-0.25,0)` | 1.028571428571 |

**与合同 §5 给出的标定值完全一致**。全表校验：`geometry` 非空者恰为 28 条 `own_sprite`。

---

## 3. 【请你裁决】接口归属问题

### 3.1 运行时钩子选了 mixin，需要你确认

路由表由客户端资源重载产生，`IClientItemExtensions` 无从读取；而把自有模型 `parent` 设成 `builtin/entity` 会让它变成 per-item-only，丢掉合同要求保留的 GUI/ground/display 行为。故我选了 `ItemRenderer.render` 的 mixin。**这是一个跨层接口决定，请你确认或指定替代入口。**

### 3.2 生成物根模型目前写 `builtin/entity`

`gradle/equipment-2d.gradle:251` 写 `heldModel.parent = 'minecraft:builtin/entity'`。这与 §4 的缺陷直接相关。

---

## 4. 【需你裁决】结构性缺陷：ready 一旦置位即双重渲染

### 4.1 事实链（逐条已验证）

1. `javap` 反汇编 `ItemRenderer` 确认：**`isCustomRenderer()`（偏移 122）、`renderModelLists`（偏移 419）、`BlockEntityWithoutLevelRenderer.renderByItem`（偏移 450）全部位于同一个 8 参 `render(...)` 方法内** —— 正是我 mixin 的目标方法。
2. 我的 mixin 注入点是 `@At("TAIL")`，即**在以上所有分支执行完之后**。
3. `EquipmentRenderer`（旧 BEWLR）已通过 `IClientItemExtensions` 注册给**全部 41 件武器 + `test_flask`**（`EldenCombatClient.java:180-194`）。
4. 41 件武器的 3D 模型（`art/weapons` 33 个 + `art/remembrance-game` 8 个）**全部进入打包产物**（jar 内 `skeletons/items/` 共 77 项），因此 `WeaponAssets.entryFor(...)` 恒非空，`EquipmentRenderer.renderByItem` **会真实绘制旧 3D 模型**；`test_flask` 走 `legacyModel` → `healing_flask` 同样绘制。

### 4.2 结论

当任一条目转 `ready`：旧 3D BEWLR 先画一遍（偏移 450），我的 2D 再画一遍（TAIL）。**双重渲染，且叠加旧 palmAnchor 与旧 3D 归一化。**

这正是合同 §2 明令禁止的「再叠旧 3D 归一化和握持偏移」。**我早先在类注释里写的「不叠加」目前只对 `ready=0` 成立，不是结构保证 —— 该注释不成立，我在此更正。**

另外，`@At("TAIL")` 的 `callback.cancel()` **无法撤销已执行的绘制**，只能阻止返回。所以即便不走 BEWLR，`renderModelLists`（偏移 419）也已在 TAIL 之前画完。注入点本身就是错的。

### 4.3 三个候选修法（请你选，我不擅自决定）

| 方案 | 做法 | 代价 |
|---|---|---|
| A | 生成器不再写 `builtin/entity` 根模型，改自有 parent + display 块；mixin 改在 `isCustomRenderer` 分支**之前**注入并 cancel | 需重写注入点；合同 §5「JSON display 只能变换」仍满足 |
| B | `EquipmentRenderer.renderByItem` 开头对「2D 会接管」的栈直接 return | 最小侵入，但需让旧渲染器知道路由表，且要保留解析失败时的 legacy 回退 |
| C | 2D 路由完全不走 mixin，改为生成常规 item 模型（layer0 指向 2D 图）+ JSON display，仅在需要掌心变换处保留 hook | 最贴合原版，但合同 §5 的 `t=p-R*S*a` 掌心标定无法纯靠 JSON display 表达 |

**我倾向 A**，因为它既保留 display 变换又彻底切断旧路径。但这属于框架接口决定，请你定。

### 4.4 影响面

**当前零影响** —— `handles()` 要求 `entry.ready()`，而 `ready=0`，故 2D 路径从不触发。**在缺陷修复前，我不会把任何条目置为 ready。** 这也是我坚持 42 条全 pending 的实际原因之一，不只是标定缺失。

---

## 5. 仍阻断（需你或用户提供）

1. **42 条 `display_not_calibrated`** —— 掌心目标 `p` 与旋转 `R` 不在任何已交付输入中，需游戏内观感标定。**解除途径只有用户实测。**
2. **42 条 `acceptance_pending`** —— 合同 §4.2 规定 `ready` 不等于验收。
3. **10 条 `provider_not_installed` + 9 条 `provider_loader_unverified`** —— `installed=null`、`runtimeRegistryVerified=false`。需确认是否安装 `spartan_weaponry_unofficial-1.2.3-1.21.1.jar`（SHA256 `84d3ddaf…988f`）。
4. **14 条 `provider_item_type_mismatch`** —— 已按合同约束实现（不绑定提供方回调到自有 `BowItem`、不强转物品类型）。
5. **`crossbow` 的 `firework`** —— 烟花/额外弹药美术未交付，需确认是否补交。

---

## 6. 工单状态

| 工单 | 状态 | 备注 |
|---|---|---|
| E01 生成 / 准入 | 已实现未验 | 含硬失败闸门 |
| E02 握点 / GUI | 已实现未验 | `display` 全 null |
| E03 提供方状态 / loader | 已实现未验 | 提供方未安装 |
| E04 盾 / 弓 / 弩 | 已实现未验 | 逻辑齐备，无真机观感 |
| E05 回退 / 重载 | 已实现未验 | 顺序符合合同 §8 |
| E06 全 42 验收 | **待实现** | 需集中构建与运行 |

**无一标「已验证」。**

---

## 7. 验证证据

```
$ ./gradlew build --console=plain          → BUILD SUCCESSFUL in 1m 1s（10 tasks）
$ javac 全模块                              → 308 类，exit 0，equipment2d 包 20 个类
$ unzip -l jar | grep -ciE 'equipment-reuse|research-only|spartan_weaponry'  → 0
$ unzip -p jar routes/2d.json              → entries=42, ready=0
$ python build-route-config.py（重跑比对）  → 生成结果确定性一致
$ 负向：强标 ready 且 display=null          → BUILD FAILED，给出理由
$ 负向：发布源指向 research-only-full/      → 生成器 exit 1
```

唯一告警：`EldenCombatClient.java:30` 的 `EventBusSubscriber.bus()` 弃用（2 条），既存问题，与本次无关。

`ui` / `fx` / `existing` / `auxiliaryModels` 逐字节未变，`fx` 契约未触碰。

## 8. 未跑场景

真机进服渲染、掌心观感标定、左手镜像预览、盾 blocking 摆姿、提供方依赖缺失与回退、`oil_coated_item` loader 与 `coating` 层、`long_bow` 谓词别名真机行为、`ready→pending` 后 `processResources` 陈旧清理（因 `ready=0` 无真实生成物）、多人/专用服务端、增量构建响应。

---

## 9. 范围声明

8 项战技 VFX 归 `DSH-VFX-IMPLEMENTATION-CONTRACT-20261014.md`；三系统框架不属本文件。相机夹具、波形可见帧、缺图回退、火墙障碍等旧缺口**不得用装备 2D 验收销账**。
