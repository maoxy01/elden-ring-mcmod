# DSH 装备 2D 实施方案交付报告

- 合同：`docs/DSH-EQUIPMENT-2D-IMPLEMENTATION-CONTRACT-20261015.md`
- 美术交接：`docs/EQUIPMENT-2D-FULL-HANDOFF-20261015.md`
- 执行方：DSH（具体实现）
- 日期：2026-10-15
- 交付批次：**第 1 批（基础适配 + 变更清单 + 仍阻断能力）**

---

## 0. 结论速览

**本批交付「基础适配」：路由表 schema、生成器准入、客户端运行时、谓词桥接、可选依赖回退、重载快照全部实现并通过本地正向/负向验证。**

**但以下事实必须明说：42 条装备当前全部为 `pending`，`ready` 数量为 0，`display` 全部为 `null`。**

这不是未完成，而是合同的正确状态：`display` 需要真实掌心标定（palm target `p` 与旋转 `R`），该数据不存在于任何已交付输入中；而合同明确 `ready` 不得代替验收。因此本批**不产出任何 `ready` 条目**，也不以 `ready` 数量作为交付指标。

| 项目 | 值 |
|---|---|
| 路由条目 | 42（`own_sprite` 28 / `provider_model` 10 / `vanilla_model` 4） |
| `status=ready` | **0** |
| `status=pending` | **42** |
| `display=null` | **42** |
| 阻断项合计 | 130 条（按前缀去重后 8 类） |
| 已准入自有 PNG | 94（全部校验 SHA-256） |
| 生成的文件 | 0（无 `ready` 即无生成物，符合设计） |
| 打包进 jar 的研究资产 | **0** |

---

## 1. 变更清单

### 1.1 新增文件

| 文件 | 职责 |
|---|---|
| `tools/equipment2d/build-route-config.py` | 确定性重新生成 `routes/2d.json` 装备区。重新发出 `ui`/`fx`/`existing`/`auxiliaryModels`，保证合同外区段逐字节不变。 |
| `src/main/java/com/dsh/eldencombat/client/equipment2d/EquipmentGeometry.java` | 像素→模型坐标数学：锚点、统一缩放、单次变换合成。 |
| `src/main/java/com/dsh/eldencombat/client/equipment2d/EquipmentRouteSnapshot.java` | 客户端重载监听器；解析路由表为原子快照。 |
| `src/main/java/com/dsh/eldencombat/client/equipment2d/EquipmentStateResolver.java` | 从真实 ItemStack / 上下文 / 使用状态解析状态名。 |
| `src/main/java/com/dsh/eldencombat/client/equipment2d/ProviderModelAdapter.java` | 提供方模型解析 + 能力探针 + 整链回退。 |
| `src/main/java/com/dsh/eldencombat/client/equipment2d/ItemSpriteGeometry.java` | 正面 / 背面 quad 发射，含 UV 与法线。 |
| `src/main/java/com/dsh/eldencombat/client/equipment2d/EquipmentPredicates.java` | 在提供方命名空间注册 `pulling` / `pull` 别名。 |
| `src/main/java/com/dsh/eldencombat/client/equipment2d/EquipmentRouteRenderer.java` | 单次变换渲染；手柄 / GUI 独立；背面支持。 |
| `src/main/java/com/dsh/eldencombat/mixin/ItemRendererEquipment2dMixin.java` | `ItemRenderer.render` TAIL 注入，仅在新路径命中时取消原渲染。 |

### 1.2 修改文件

| 文件 | 变更 |
|---|---|
| `src/main/resources/assets/eldencombat/routes/2d.json` | 装备区重写为 schema v2；新增 `equipmentSchemaVersion=2`、`equipmentAdmission`。`ui`/`fx`/`existing`/`auxiliaryModels` 逐字节保持不变（已校验）。 |
| `gradle/equipment-2d.gradle` | 生成器重写：路由形态校验、schema 版本校验、42 ID 精确覆盖、准入校验（白名单 + SHA-256 + 根目录限定 + 拒绝根）、`ready` 闸门改为**硬失败**。 |
| `src/main/resources/eldencombat.mixins.json` | `client` 数组新增 `ItemRendererEquipment2dMixin`。 |
| `src/main/java/com/dsh/eldencombat/client/EldenCombatClient.java` | 注册重载监听器、别名注册器、`RegisterAdditional` 自有模型、`BakingCompleted` 缓存烘焙模型。 |

### 1.3 关键设计决定

**决定一：提供方模型不做 JSON `parent`。**
合同禁止随包发出悬空 `parent`。因此运行时改为在 `ModelEvent.BakingCompleted` 缓存提供方**已烘焙**的 `BakedModel`，直接复用。这样 loader、parent 链、额外图层、overrides、display 全部原样保留，且在依赖缺失时**零次加载提供方类型**。

**决定二：运行时钩子是 `ItemRenderer.render` 的 mixin。**
路由表来自客户端资源重载，`IClientItemExtensions` 看不到它；而强制 `parent: builtin/entity` 会摧毁合同要求的 GUI / 地面 / display 行为。故采用 TAIL 注入 + 仅在命中时取消。

**决定三：`ready` 闸门硬失败。**
本批发现并修正：初版闸门只记录 note 不中断构建，非法 `ready` 会被静默降级为「已生成 0 文件」，从而在表里宣称 ready 实则未产出。现改为收集全部拒绝理由后 `throw GradleException`（见 §4.2 负向验证）。

---

## 2. 数学实现与核对

合同 §5 公式已逐条落地于 `EquipmentGeometry.java`：

| 合同要求 | 实现 |
|---|---|
| 画布左上原点像素锚点 | `anchorModelUnits()` = `[16*x/W - 8, 8 - 16*y/H, 0]` |
| 方形画布 XY 统一缩放 | `uniformXYScale()` = `worldLengthBlocks * W / sourceSpanPixels`（**分母恒为 `sourceSpanPixels`，非方形输入同样如此**） |
| 掌心目标单次施加 | `heldPlacement(a, s, r, grip)` = `translate(anchor) · rotateZ(-45°) · scale(scale) · translate(-grip)`，**仅施加一次** |

抽样核对（数值直接取自 `routes/2d.json` 的 `geometry` 与 `derived` 字段）：

| 装备 | 画布 | `sourceSpanPixels` | 锚点像素 | 推导锚点 | 统一缩放 |
|---|---|---|---|---|---|
| `great_sword` | 32×32 | 37.618080759124 | [7.5, 24.5] | `[-4.25, -4.25, 0]` | 1.347437162586 |
| `shield` | 32×32 | 28 | [16.5, 16.5] | `[0.25, -0.25, 0]` | 1.028571428571 |

`great_sword`（`a=(-4.25,-4.25,0)`，`s≈1.347437`）与 `shield`（`a=(0.25,-0.25,0)`，`s≈1.028571`）**与合同 §5 给出的标定值完全一致**。这验证了公式与实现一致：合同给的锚点确实由该像素坐标按 `16*x/W-8` / `8-16*y/H` 推出，缩放由 `worldLengthBlocks*W/sourceSpanPixels` 推出。

> `axe` 等 4 条 `vanilla_model` 与 10 条 `provider_model` 的 `geometry` 为 `null` —— 它们不发布自有贴图，不参与本公式，属正确状态（已全表校验：`geometry` 非空者恰为 28 条 `own_sprite`）。

### 2.1 多状态条目核对（合同 §6 要求）

`own_sprite` 中状态数大于 1 的条目，与合同要求的档位逐一对应：

| 装备 | `states.supported` | 合同要求 |
|---|---|---|
| `shield` / `great_shield` / `small_shield` | `front`, `back` | 正反面，法线相反、UV 正确；`blocking` 重新摆姿两面的姿态 |
| `great_bow` | `standby`, `pulling_0`, `pulling_1`, `pulling_2` | 待机 + 三档拉弓 |
| `ballista` | `standby`, `loading_0`, `loading_1`, `loading_2`, `loaded` | 待机 + 三档装填 + 已装填 |
| `test_flask` | `full` | **仅满状态**；`empty` 明确 `blocked`，不新增消耗 / 补充规则 |

> 注意：以上是**几何可推导量**。合同 §5 所称的掌心目标 `p` 与旋转 `R` 是**观感标定值**，无法从美术输入推导，故 42 条 `display` 全部保持 `null`（见 §3.1）。

---

## 3. 仍阻断的能力（逐条对应合同要求）

### 3.1 全部 42 条：`display_not_calibrated`

`display` 需要真实掌心目标 `p` 与旋转 `R`。合同 §5.3 / §6.1 要求 `display=null` 不得变为 `ready`，实现中已由闸门强制。**这是本批最大阻断项，且无法由 DSH 单方解除** —— 需要用户在实际游戏内提供观感标定。

### 3.2 全部 42 条：`acceptance_pending`

合同 §4.2：`ready` 仅表示「能力 + 静态输入齐备」，**不等于进机验收**。验收是独立回执。故本批不标任何 `ready`。

### 3.3 `provider_model` 10 条：`provider_not_installed`（10）+ `provider_loader_unverified`（9）

`evidence.json` 中 `installed=null`、`runtimeRegistryVerified=false`。合同要求不得据未验证注册表宣称能力。

**`coating` 额外图层归属已核实**：携带该图层的是 **9 条 Spartan 近战**条目（`dagger`、`thrusting_sword`、`katana`、`greataxe`、`hammer`、`great_hammer`、`spear`、`halberd`、`scythe`），其 `stateModels[].layers = ["coating", "layer0"]`。这 9 条已逐条记录 `loader.coatingLayer=true` 与 `loader.loaderId=spartan_weaponry_unofficial:oil_coated_item`，并各自获得一条 `provider_loader_unverified` 阻断 —— **数量恰好对应 9**。

`long_bow` 的图层为**仅 `layer0`**，不含 `coating`，故未记录 `coatingLayer`；这一点在核实证据后被修正，先前口头转述有误。

其余 `provider_loader_unverified` 为常规模型链但同样未验证。**契约要求：模型链不得压平为 `layer0`** —— 实现中 `loader.note` 明确记录「复用提供方实际 `BakedModel`；链条永不压平为 layer0」。

### 3.4 `provider_item_type_mismatch` 14 条

出现在 10 条 `provider_model` + 4 条 `vanilla_model`（后者的提供方物品为原版 `iron_sword`/`iron_axe`/`bow`/`crossbow`，同样非本 mod 物品类）。

反编译显示 `registerLongbowPropertyOverrides` 取 `LongbowItem` 且其 pull 回调捕获该 item 并调用 `getNockProgress`。合同明确：**该回调不得绑定到我们自己的 `BowItem`，也不得强制转换 item 类型。** 实现遵循此约束 —— `EquipmentPredicates` 只注册 `pulling`/`pull` 两个纯数值别名，不触碰任何提供方类型。

### 3.5 `unsupported_predicates` 10 条

阻断出现在**恰好 10 条**上，构成如下：

| 归属 | 条目 | 谓词 | 分类 | 原因 |
|---|---|---|---|---|
| Spartan 近战 ×9 | `dagger` `thrusting_sword` `katana` `greataxe` `hammer` `great_hammer` `spear` `halberd` `scythe` | `spartan_weaponry_unofficial:blocking` | `unsupported` | 自有玩法**没有**格挡状态，合同禁止发明 |
| 同上 | 同上 | `spartan_weaponry_unofficial:throwing` | `unsupported` | 自有玩法**没有**投掷状态，合同禁止发明 |
| 弩 ×1 | `crossbow` | `firework` | `blocked` | 烟花 / 额外弹药美术未交付 |
| 同上 | `crossbow` | `pulling` / `pull` | `unsupported` | 弩条目不参与拉弓，该属性无意义 |

**据此明确不实现的行为**（合同 §6）：不发明 blocking/throwing 玩法；弩的烟花弹不新增玩法，保持现有行为并回退旧模型，不暗示原版弩新玩法。

### 3.6 `predicate_alias_unverified` 1 条

`long_bow` 的 `pulling` / `pull` 需在提供方命名空间注册别名，已实现但**未在真机验证**。

### 3.7 `legacy_selection_preserved` 2 条

`great_sword`、`colossal_sword` 的 `legacySelectionPreserved: true`，旧选择行为保持不变。

---

## 4. 验证证据（实际命令与结果）

### 4.1 正向验证

```
$ python tools/equipment2d/build-route-config.py
  ... 42 entries printed ...
$ cmp <重跑前> <重跑后>          # 确定性
OK: 生成结果确定性一致
```

```
$ ./gradlew generateEquipment2d --console=plain --rerun-tasks
2D route coverage: 42 entries; own_sprite=28, vanilla_model=4, provider_model=10;
ready=0, pending=42; generated files=0
BUILD SUCCESSFUL in 9s
```

```
$ javac -nowarn -proc:none -implicit:none -cp @javacp.txt -d build/eq2d-all @eq2d-srcs.txt
javac_exit=0
classes=308          # equipment2d 包 20 个类全部产出
```

```
$ ./gradlew build --console=plain
> Task :compileJava
> Task :generateEquipment2d UP-TO-DATE
> Task :processResources
> Task :test
> Task :build
BUILD SUCCESSFUL in 1m 1s
10 actionable tasks: 6 executed, 4 up-to-date
```

> 唯一告警：`EldenCombatClient.java:30` 的 `EventBusSubscriber.bus()` / `Bus.MOD` 弃用（2 条），**属既存问题，与本次改动无关**。

### 4.2 负向验证 —— `ready` 闸门必须硬失败

植入非法条目（`straight_sword` 强标 `ready` 但 `display=null`）：

```
$ ./gradlew generateEquipment2d --rerun-tasks
  eldencombat:straight_sword -> ready refused: display is null (palm target p and rotation R undefined)
FAILURE: Build failed with an exception.
> Equipment 2D: 1 ready entry/entries failed admission and were not generated:
    eldencombat:straight_sword -> display is null
BUILD FAILED in 12s
```

**闸门按预期硬失败，并给出可操作理由。**

### 4.3 负向验证 —— 准入拒绝研究目录资产

把 `great_sword`（`own_sprite`，真实发布源）的首个 variant 指向 `art/equipment-reuse/research-only-full/`：

```
$ python tools/equipment2d/build-route-config.py
asset not on the publishable whitelist: art/equipment-reuse/research-only-full/minecraft_item_iron_axe.png
gen_exit=1
```

**生成器以退出码 1 硬失败。**

### 4.4 打包验证

```
$ unzip -l build/libs/eldencombat-0.2.0.jar | grep -ciE 'equipment-reuse|research-only|spartan_weaponry'
0                                     # 研究资产 / 提供方资产 0 条
$ unzip -l ... | grep -i equipment2d  # 20 个类 + mixin 全部在包内
$ unzip -p ... assets/eldencombat/routes/2d.json
 keys: [version, equipmentSchemaVersion, equipmentAdmission, ui, equipment, fx, existing, auxiliaryModels, externalProviderStatus]
 entries: 42 | ready: 0
```

**研究目录、含提供方材质的预览合成图、抽取 PNG、evidence JAR 均未进入 mod 包。** 原版资源直接引用；Spartan 作为整体依赖处理；未把 Apache-2.0 声明拆成逐资产许可。

### 4.5 合同外区段完整性

`ui` / `fx` / `existing` / `auxiliaryModels` 在重写前后**逐字节一致**（canonical JSON 的 SHA-256 对比）。`fx` 合同未改动。

---

## 5. 工单状态（E01–E06）

| 工单 | 内容 | 状态 | 证据 |
|---|---|---|---|
| E01 | 生成 / 准入 | **已实现未验** | §4.1 §4.3；准入校验、SHA-256、拒绝根、42 ID 覆盖均生效 |
| E02 | 手柄 / GUI | **已实现未验** | 单次变换与 GUI 独立 sprite 已实现（§2）；**`display` 全为 `null`，真机观感未验** |
| E03 | 提供方状态 / loader | **已实现未验** | 能力探针先于模型解析、整链回退、`coating` 层保留；**提供方未安装，无法验证** |
| E04 | 盾 / 弓 / 弩 | **已实现未验** | 盾正反面法线 + UV、`blocking` 重新摆姿（非新贴图）、弓多档、弩烟花 `blocked` |
| E05 | 回退 / 重载 | **已实现未验** | 原子表替换、`generation` 递增、旧烘焙引用丢弃、per-equipment 去重日志 |
| E06 | 全 42 验收 | **待实现** | 需进机；本批 `ready=0`，不构成验收 |

**所有工单均未标「已验证」** —— 合同 §9.4 要求验收须记录实际命令 / 日志 / 未跑场景与用户观感确认。

### 5.1 E05 细节：回退顺序（合同重点）

合同 §7 要求「缺失依赖回退必须发生在解析外部 loader / 模型链**之前**」，且基础自有模型保留可用旧回退。实现顺序：

1. `ProviderModelAdapter.resolve()` 先跑**能力探针**（`entry.requiredPresence()`）。
2. 任一必需资源缺失或任一状态模型不在烘焙表中 → **返回空列表，整链作为一个单元回退**。
3. 基础自有模型始终有 `fallback: "legacy_builtin_entity"`。
4. 日志按装备 + 原因记录，**去重，不逐帧**。

---

## 6. 未跑场景（明确声明）

以下场景本批**未运行**，不得视为通过：

1. **真机进服渲染** —— 未启动游戏客户端。
2. **掌心观感标定** —— 未进行任何游戏内标定（这正是 42 条 `display=null` 的原因）。
3. **左手 / 镜像预览** —— `leftHand.previewMirrorOnly=true` 仅记录，未在真机确认。
4. **盾牌 `blocking` 重新摆姿** —— 逻辑已实现，未在真机确认观感。
5. **提供方（Spartan）依赖缺失与缺失后的回退** —— 提供方未安装，未实测。这同时是 §3.3 阻断项的验证缺口。
6. **提供方 loader（`oil_coated_item`）与 `coating` 层** —— 未实测，`loader.coatingLayer` 仅按证据记录在 9 条近战条目上。
7. **`long_bow` 谓词别名真机行为** —— 未实测。
8. **`ready → pending` 回退后的陈旧 `processResources` 目标清理** —— 代码路径存在（仅限 `models/item/2d` 与 `textures/item/2d` 两个命名空间），但因 `ready=0` 未产生真实生成物，**未端到端实测**。
9. **多人 / 专用服务端** —— 未在任何服务端环境启动，客户端类隔离未实测。
10. **增量构建对美术变更的响应** —— 生成器已把实际准入清单 / 源美术 / 适配配置纳入输入，但未跑增量场景。

---

## 7. 待用户决策 / 需用户提供

合同 §9.4 明确「实现后集中验收由用户安排统一构建与运行」。需要用户：

1. **提供游戏内掌心标定数据**（`p` 与 `R`）或授权 DSH 提出观感候选值待您在游戏内确认。**这是解除 42 条 `display_not_calibrated` 的唯一途径。**
2. **安排集中构建与运行**，并记录实际命令 / 日志 / 观感确认。
3. **确认是否安装 Spartan 依赖**（`spartan_weaponry_unofficial-1.2.3-1.21.1.jar`，SHA-256 `84d3ddafb519e2f5e03f2681ff52c30c92c9512181f61a7a7487399a0179888f`）以验证 10 条 `provider_model` 与 `installed=null` 阻断。
4. **确认弩烟花弹药美术是否补交** —— 否则 `crossbow` 的 `firework` 谓词将长期 `blocked` 并保持旧模型回退。

---

## 8. 合同范围外（本文件不覆盖）

- 8 项战技 VFX —— 归 `docs/DSH-VFX-IMPLEMENTATION-CONTRACT-20261014.md`。
- 三系统框架 —— 本文件不覆盖。
- 被明确排除的「真实外部 Item」第 4 类 —— 不属于本视觉路线。
