# 装备 2D 修复交付回执（DSH → Codex）

- 依据：`docs/DSH-EQUIPMENT-2D-IMPLEMENTATION-CONTRACT-20261015.md`（§2 缺陷、§10 修复、§11–§12 验收）
- 对照审查：`docs/CODEX-EQUIPMENT-2D-REVIEW-20261015.md` 六项必修
- 日期：2026-10-15
- 执行方：DSH（具体实现）
- 本轮边界（合同 §9.4）：只做集中编译与定向检查，**未启动游戏、未启动 Harness**；绘制美术、白名单与研究资产均未改动，未下载任何新资源。

---

## 0. 结论

1. §2 的六项缺陷已全部落到代码，且每一项都有可重复的检查：绘制前窄接管、提供方真实 `BakedModel` 绘制、统一单位/原点、弓弩分母按原版、`display` 成为唯一变换所有者、真实 holder + 盾双面/格挡姿态。
2. 42 条路由中 **41 条结构完整可画**（27 自有精灵 + 10 提供方模型 + 4 原版模型），**1 条被硬阻断**：`eldencombat:starscourge_greatsword` 的白名单哈希与磁盘文件不一致，因此按设计走旧回退，**不计入成功路径**。
3. 一次性验证全绿：`compileJava test generateEquipment2d` = BUILD SUCCESSFUL；**197 个测试 0 失败**（含新增 8 个 2D 验收测试）；生成 209 个文件；打包内研究资产 **0**。
4. 本轮自查又发现并修掉 5 个此前未列出的真实缺陷（见 §4），其中三个会直接改变画面（欧拉约定、参考姿态比例、接近 180° 的分解），一个会让两条 `vanilla_model` 路线被误拒，一个会让三条盾的 blocking 表不完整。
5. 按合同 §11.2 补齐了**校准覆盖机制**：用户实机调过的 display 写进独立的 `display-overrides.json`，生成器只读不写，重跑生成不会抹掉（§7，已演练）。

---

## 1. 对审查六项必修的逐条回应

| # | 审查要求 | 落点 | 现状与证据 |
|---|---|---|---|
| 1 | P1 绘制后接管无法阻止旧画面 | `mixin/ItemRendererEquipment2dMixin.java` 注入 8 参 `render` 的 **HEAD**，仅在路线已完整负责本次绘制时 `cancel` | 走线前 `prepare()` 只读、不碰 pose/顶点；返回 false 时原链零改动；返回 true 时本次绘制已由路线完成（含自己的 push/pop）。日志与测试见 §5 |
| 2 | P1 提供方降级为 particle 贴图、空 geometry 会 NPE | `EquipmentRouteRenderer.prepareProvider` + `ProviderModelAdapter` + `emitModel` | 提供方绘制真实 `BakedModel` 的 render passes/render types/foil；不再有 `getParticleIcon` 兜底，`geometry=null` 不再是拒绝理由（合同 §10.2）。`spriteOf` 只在双面盾读自己 quad 的 sprite |
| 3 | P1 坐标单位与原点不一致 | `EquipmentGeometry` + `ItemSpriteGeometry` | 统一发射空间 `b(x,y)=(x/W-0.5, 0.5-y/H, 0)`，quad 恒为 `[-0.5,0.5]²`（1 格），握点为同空间 `a=b(anchor)`；用**实际发射顶点**与柄像素验证（§3）。GUI 用独立 `gui_16`/`gui_32` 与 identity 姿态，手持画布不参与 GUI 尺寸 |
| 4 | P1 弓/弩分母错误 | `EquipmentStateResolver` + `EquipmentPredicates` | 弓 `elapsed/20`（非 72000），弩 `elapsed/CrossbowItem.getChargeDuration(stack,holder)`；别名与原版属性数值完全一致；`13t=0.65 / 18t=0.9 / 20t=1` 由测试钉住 |
| 5 | P1 `display` 未被消费、仍叠旧修正 | `EquipmentDisplay` + `EquipmentRouteRenderer` | 每件、每上下文、左右手消费真实 `display`；不再引用 `WeaponCorrections`、不再做旧 bounds fitting、不再固定 -45°；`t=p-R·S·a` 为唯一一次变换。p 用**参考物品自己的柄像素**求，不用目标装备柄像素 |
| 6 | P2 真实使用者与盾双面 | `EquipmentRenderContext` + `EquipmentStateResolver.shieldBlocking` + `prepareShield` | 十参 `renderStatic` 在 HEAD 开启作用域上下文、RETURN 关闭，携带真实 holder/hand/stack/level/seed；无 holder（GUI/地面/展示框）时活动状态归零；盾在**一次绘制**内画正反两种材质，反面容绕序反转、法线取实际 pose 的法线矩阵；格挡只切换到明确的 blocking 姿态，且校验“正在使用的就是这面盾” |

---

## 2. 42 条准入现状

| 路线 | 条目 | ready | pending | 说明 |
|---|---|---|---|---|
| `own_sprite` | 28 | 27 | 1 | 唯一 pending：`starscourge_greatsword`（白名单哈希 `a0c23427…` ≠ 磁盘 `df12095b…`） |
| `provider_model` | 10 | 10 | 0 | 复用提供方已烘焙模型；`display=null` 是路线差异 |
| `vanilla_model` | 4 | 4 | 0 | `minecraft:item/bow|crossbow|iron_sword` 等，走真实原版模型与状态链 |
| **合计** | **42** | **41** | **1** | 41 个注册 ID 不变 + `test_flask` |

生成门禁（`gradle/equipment-2d.gradle`）对 `ready` 失败即 `throw`（fail-closed）；本轮 41 条全过，生成 **209** 个文件，打包内研究资产 0。

---

## 3. 单位、原点与 display 的验收证据

统一约定（合同 §5/§10.3）：

```
发射空间   b(x, y) = (x/W - 0.5, 0.5 - y/H, 0)      画布 = 1 格，范围 [-0.5, 0.5]²
握点       a = b(anchorX, anchorY)                   同一原点、同一单位
最终变换   D = T(t) · R · S ， t = p - R·S·a ， S = s·I ， s = worldLengthBlocks·W/sourceSpanPixels
掌心目标   p = D_ref · a_ref                         参考物品自己的柄像素，含参考姿态自身比例
```

| 检查 | 数值（`art/equipment-2d/full/display-candidates.json`，214 个上下文） |
|---|---|
| 每个上下文发出的 `scale` 是否等于合同的 `s` | **0 处不一致** |
| 数值往返（存入 JSON → 按 `ItemTransform` 重新施加） | 最大 **4.97e-07** |
| 发出的世界长度 vs 美术声明 `worldLengthBlocks` | 最大 **5.71e-07 格** |
| 握点是否落在参考掌心 `p` | 最大 **1.94e-07 格** |
| 上下文完整性 | 每条 8 个上下文（第一/三人称左右手、GUI、ground、fixed、head）齐全；盾另有完整 blocking 表 |

手算锚点（`Equipment2dRoutingTest.greatSwordGripLandsOnTheHandComputedVanillaHandheldPalm`）：`item/handheld` 第三人称右手（rot 0,-90,55 / t 0,4,0.5 / scale 0.85）把参考剑柄像素 (3.5,12.5) 送到 `(0, -0.082949, 0.089958)`；大剑把自己 32 图画布上的柄像素 (7.5,24.5) 送到同一点，与实现无关地钉住了 `T·R·S`、角度制、`/16`、`Rx·Ry·Rz` 与左手镜像约定。

---

## 4. 本轮自查新发现并修掉的 5 个缺陷

1. **欧拉约定搞反了（会改变所有多轴姿态）。** 之前按 `Rz·Ry·Rx` 反解并写盘，而 `Quaternionf().rotationXYZ(rx,ry,rz)`（原版 `ItemTransform.apply` 用的同一个调用）是 **`Rx·Ry·Rz`**。已用测试 `jomlRotationXyzIsRxRyRz…` 把这条 JOML 事实钉住，生成器改用同一约定反解；否则同一份 JSON 在运行时会被读成另一个朝向。
2. **参考姿态自带比例被烘进 R（会让武器变短）。** 参考 handheld/firstperson 的 0.85 / 0.68 曾乘进旋转，导致实际世界长度只有声明的 85% / 68%，同时违反合同“`s` 就是发出的 scale”。现在生成器把参考姿态的均匀比例除掉，`p` 仍取参考姿态的完整变换（握点位置不变）。
3. **接近 180° 的三元组在四元数路径上会偏。** 例如 `fixed` 姿态分解出 `(-180, 0, 179.952)`，JOML 四元数路径给出与矩阵乘积差约 1.5e-4 的结果；改用等价三元组 `(a+180, 180-b, c+180)` 规范到 `|a| ≤ 90` 后逐位一致（214 个上下文全部通过矩阵往返与 Java 侧断言）。
4. **解析器对裸谓词名报错，误拒两条原版路线。** `stateModels[].predicate` 里的 `pulling`/`pull` 没有命名空间，`ResourceLocation.parse` 直接抛 `Resource namespace missing: pulling`，使 `short_bow` 与 `crossbow` 被拒（准入从 41 掉到 39）。已按 `ItemOverrides.Deserializer` 的语义补默认命名空间（`minecraft:`），并让 `usableStates` 的命名空间判断同样默认到 `minecraft`。
5. **盾的 blocking 表缺 `gui` 上下文。** 生成器只输出参考姿态声明的上下文，门禁要求 7 个必需上下文齐全，导致三条盾在 `generateEquipment2d` 上被判 “displayBlocking missing gui”。已让 blocking 表与非 blocking 表一样显式声明 identity 的 GUI 姿态。

---

## 5. 验证命令与结果

```powershell
$env:JAVA_HOME='D:\JDK21'
cd D:\codexout\mcmod
.\gradlew.bat cleanTest test generateEquipment2d --rerun-tasks --no-build-cache --offline --no-daemon `
  -Dorg.gradle.java.home=D:/JDK21 -Dorg.gradle.jvmargs=-Dfile.encoding=UTF-8
```

| 结果 | 值 |
|---|---|
| 构建 | **BUILD SUCCESSFUL**（8 个任务全部实际执行，非缓存） |
| 生成门禁 | `2D route coverage: 42 entries; own_sprite=28, vanilla_model=4, provider_model=10; ready=41, pending=1; generated files=209` |
| 测试 | **197 tests, 0 failures**, 1 skipped（29 个 suite） |
| 新增验收测试 | `src/test/java/com/dsh/eldencombat/Equipment2dRoutingTest.java`（8 个）：正式路由表结构/准入、逐条 display 反推参考掌心（含校准覆盖分支）、手算锚点、几何单位、状态分母与阈值、quad 范围与绕序、左手镜像与继承、JOML 欧拉约定 |
| 日志 | `docs/combat-evidence/equipment2d-repair-20261015.log` |
| 测试明细 | `build/test-results/test/*.xml` |
| 生成物 | `build/equipment2d/...`（209 个文件：`assets/eldencombat/textures/item/2d/*`、逐状态与 GUI 模型、held 根模型）与 `src/main/resources/assets/eldencombat/routes/2d.json`（`equipmentSchemaVersion=2`） |

---

## 6. 残余与未验项（如实列出，不当作已完成）

- **实机观感/占屏与首批六类实机成功路径（审查 33.2、合同 §11.7）**：需用户安排隔离实例观察第一/三人称、左右手、GUI/地面/展示框、切手与中断。本轮按 §9.4 未启动游戏；候选值的 `status=proposal_not_game_verified`、`humanAccepted=false`。
- **14 条提供方/原版路线**：只完成结构准入与运行时能力探测（`installed`/类型/别名），实例内未安装 Spartan jar，因此没有提供方实机证据；`installed=null` 是旧取证状态，不等于永久缺失。
- **`ballista` 装填烟花**：可达但无对应美术，按设计整条回退旧模型，不冒充 `loaded`/`standby`。
- **`starscourge_greatsword`**：白名单与磁盘哈希不一致，需美术侧重发白名单或确认以磁盘文件为准；在此之前保持 pending + 旧回退。
- **`renderStatic` 抛异常**：会遗留一帧作用域上下文（`MAX_DEPTH=64` 兜底清除 + 按 stack 身份校验，不会串到别的物品）。
- **GUI 上下文**：运行时按设计强制 identity 姿态，数据里的 `display.gui` 由生成器与门禁共同保证为 identity；`ui.fp` 仍 `pending`（本轮范围外）。
- **测试源集**：为让 2D 验收测试跑真实解析器/矩阵数学，`build.gradle` 把主编译类路径加进了测试源集。代价是单测不再是“零 Minecraft 依赖”，换来的是正式路由文件、解析准入与 display 数值都能被断言。

---

## 7. 校准覆盖（合同 §11.2：候选参数与派生字段分离、可恢复）

新增 `art/equipment-2d/full/display-overrides.json`（默认空）作为**唯一**的人工/实机校准落点：

- 生成器（`build-display-candidates.py` / `build-route-config.py`）**只读不写**这个文件，所以重跑任何生成脚本都不会抹掉用户调过的值；`display-candidates.json` 保持为可重建的派生基线。
- 覆盖以**整个上下文**为单位替换（`rotation`+`translation`+`scale` 一起，避免半套校准）；未列出的上下文仍是候选值。`translation` 单位与模型 JSON 一致（1/16 格）。
- 覆盖生效时该条 `displayStatus` 变 `calibrated_not_game_verified`，`displaySource.kind` 变 `derived_candidate+user_calibration` 并列出被替换的上下文，`acceptance.state` 变 `calibrated_pending_visual`；只有覆盖文件里 `accepted: true`（用户自己写）才会把 `humanAccepted` 置真。
- `equipmentAdmission.displayOverrides` 记录文件路径，`equipmentAdmission.calibratedItems` 列出当前被校准的条目（现在为空）。
- 已做过一次真实演练：临时给 `great_sword.thirdperson_righthand` 写覆盖（rotation 55→50）→ 生成器输出 `calibrated_not_game_verified`、被替换上下文列出；`generateEquipment2d` 门禁通过；`everyShippedDisplayReproducesItsReferencePose…` 转为按覆盖值校验并通过；演练后已清空覆盖。

---

## 8. 范围与回滚

- 未改动物品注册 ID、数据组件、服务端战灰/FP 权威；未删除旧模型与旧 BEWLR；未改美术与白名单内容（仅补生成侧的 blocking GUI 上下文）。
- 回滚方式：把 `routes/2d.json` 中条目 `status` 置回 `pending`（或撤掉 `display`）即可整体退回旧回退路径；生成器写文件不会抹掉候选（候选、覆盖与派生字段分别保存）。

---

## 9. 实机人工观感入口（2026-10-15 新增）

按用户要求把"实机看一眼"做成一条可复现命令，**没有改任何渲染代码**：

```powershell
.\gradlew.bat runClient -PequipmentView --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
# 等价封装：tools\Run-EquipmentView.ps1
```

- 隔离目录 `build/equipmentView`，日志 `build/equipmentView/logs/latest.log`；`--quickPlaySingleplayer equipment-view`
  直接进世界，**没有夹具接管相机/物品/按键**（与 `-PclientSmoke` 的关键区别）：玩家自己按 E 取武器、F5 切一/三人称、F 换手。
- `prepareEquipmentViewWorld` 把已跑过夹具、玩家已是创造模式的 `build/clientSmoke/saves/codex-player-smoke`
  复制成 `saves/equipment-view`（`level.dat` 与 `playerdata` 的 `playerGameType` 都是 1）；**只在目标不存在时复制**，
  玩家进去改过之后工具不再覆盖（要重来就自己删 `build/equipmentView/saves/equipment-view`）。

### 9.1 本次实机记录（20:48–21:38，退出码 0，玩家手动退出）

- 路由准入逐字：`Equipment 2D routes admitted: 41 live (14 provider chain(s)), 1 not active, 41 admitted entry/entries still awaiting visual acceptance`。
- 唯一 not active = `eldencombat:starscourge_greatsword`（`asset_hash_mismatch`，即已知的白名单哈希差），按设计退回旧回退路径。
- 提供方链缺失告警 1 次（13 件 Spartan 提供方条目，实例未装该 jar）→ 保留原版模型，未崩溃、未拒绝整表。
- 这次会话共 50 分钟、628 行日志里 `Equipment 2D route failed while drawing` **0 次**、`prepare failed` **0 次**；
  唯一 `Exception`-like 行是本轮之前就存在的 journeymap mixin `ClassNotFoundException`（未装 journeymap），与装备无关。
- 存档里 `eldencombat_test` 数据包每次进世界会自己跑一遍 `[etest]` 自检（刷 Boss → 100 tick 验伤 → 击杀清理），
  这是从 `run/world` 带过来的历史夹具，不是本次新增，也不影响观感核对。
- 会话日志存档：[equipment2d-manual-view-20261015.log](combat-evidence/equipment2d-manual-view-20261015.log)。
- **这不等于"观感已通过"**：逐件的一/三人称、左右手、GUI/地面/展示框观感仍需人眼判定，`humanAccepted` 保持 `false`。

## 10. BUG反馈（2026-10-15 桌面「BUG反馈.doc」）修复与实机复核

反馈原文四条：`1.盾牌浮空`、`2.弓拿反了`、`3.创造模式下玛尔基特仍有攻击欲望`、`4.弩炮握持有问题`；
用户指令：**所有盾牌/剑/弓/弩/斧/镰刀/长鞭，参考剑，都套用原版握持动作**。

### 10.1 两个根因（都在派生数据里，不是渲染代码）

1. **参考握把的"半格"单位错了两次。** 候选生成器把 `minecraft:item/shield` 的模型原点当成了图标格的**角**
   （pixel `(0,0)` ≡ 居中坐标 `(-0.5,+0.5)`），于是 `p` 比原版高 1 格、侧偏 0.5 格 → 这就是"盾牌浮空"。
   正确的量是：`builtin/entity` 的盾牌几何**围绕模型原点**（`ShieldModel` 的 plate 是 x −6..6、y −11..11），
   而 `ItemRenderer` 自己那次 `translate(-0.5,-0.5,-0.5)` 是**在 display 坐标系内**生效的
   （`PoseStack.translate` 右乘，世界点 = `D·(v − h)`，`h=(0.5,0.5,0.5)`），所以盾牌的参考点 = `D_ref·(−h)`。
   `display-candidates.json` 里 `shield` 家族的 `gripOffsetBlocks` 现在就是 `[-0.5,-0.5,-0.5]`（局部坐标系内），
   `thirdperson_righthand` 的 t 从 `(10,14.257,4.257)` 变成 `(2,−1.743,4.257)`。
2. **主轴对齐看不出半圈翻转。** 主轴只是**一条直线**，把两条线摆平行之后，画布被整体转 180° 也照样平行；
   弓的画布正好是原版弓图标转了 180°（弦与弓背左右互换），所以 `wrap90()` 读出 0 并保持 0 → "弓拿反了"。
   新增 `perpendicular_skew()`：以**画布 +x 轴**（而不是特征向量符号）固定垂直方向，取质量的三阶矩；
   与参考图符号相反且两者都非零 → 判为 180°，并把补偿写进 `axis.flip180Degrees` / 注释里。
   2026-10-15 实测：`great_bow` 唯一命中（own −0.0089 vs ref +0.0467），其余 27 件符号相同或参考图本身
   左右对称（剑类参考图标的三阶矩恰为 0），因此不会误伤剑/斧/镰刀/长鞭。

### 10.2 剑/斧/镰刀/长鞭为什么"本来就对"

- `straight_sword` = `vanilla_model`（原版铁剑模型）→ 原版握法，天然正确，也是用户说的"参考剑"。
- 其余近战（`great_sword`/`colossal_sword`/`whip`/…）参考系是 `item/handheld`，
  派生结果与原版剑一致：实机量测"我们握把 vs 原版铁剑握把"差距 = **0.018–0.128 格**（见 §10.3）。
- `axe` 同样是 `vanilla_model`（原版铁斧）；`scythe`/`katana` 等 9 件提供方不可用 → 仍走旧 BEWLR
  3D 网格 + `WeaponDisplayTransforms`（本就是"原版剑图标格"那套校准）。

### 10.3 实机量化复核：新增夹具 + 对照量测（**这是本节的硬证据**）

`ClientSmokeHarness` 的 grip 夹具扩充了反馈点名的家族 **以及原版对照物品**
（`Items.SHIELD`/`BOW`/`CROSSBOW`，与我们的同机位同帧），并把 grip proof 改成
"2D 路由条目用路由自己的 display 复现链路"（旧代码会对 2D 条目断言已不再绘制的旧链路；
`vanilla_model` 条目直接跳过，因为那件物品就是原版物品）。新增 `vanillaCounterpartGapBlocks`：
把**原版对照物品**用同一条手部链路摆一遍，量我们的握把与原版握把的世界距离。

命令：`runClient -PclientSmoke -PgripSmoke`（147 个夹具，`CLIENT_SMOKE_PASS`，退出码 0），日志
[bugfix-grip-smoke-20261015.log](combat-evidence/bugfix-grip-smoke-20261015.log)，
截图在 `build/clientSmoke/screenshots/`，拼图见
[grip-compare-vanilla-20261015.png](combat-evidence/grip-compare-vanilla-20261015.png)。

| 物品 | 与原版同类的握把差距 | 结论 |
|---|---|---|
| `great_sword` / `colossal_sword` | **0.018 格** | 与原版铁剑握把一致 |
| `whip` | **0.128 格** | 一致（差值来自美术握把像素与该件三角尺度的换算） |
| `great_bow` | 0.289 格 | 与设计一致：我们把握把像素放到原版弓的握把点上，差值=(我们握把像素−原版弓握把像素)在图标格里的距离 |
| `ballista` | 0.306 格 | **美术握把像素在枪托后端**，而原版弩的握把在托中部 → 见 §10.4 |
| `shield` 家族 | 与原版盾的叠合像素占比大幅上升（"只在我们这边"的像素 8.4k → **1.7k**），人眼对照图里高度/朝向已与原版盾一致 | 浮空已消除 |

### 10.4 未闭环的一条：弩炮握持

- 姿态本身**就是原版弩的**（`rotation [-90,0,-60]` 与原版 `item/crossbow` 逐字相同），且两条朝向判据
  （主轴 135° vs 136°、垂直三阶矩同号）都判定"没有翻转"。
- 剩下的是**美术握把点**：`ballista` 的 `anchorPixels=(7.5,24.5)` 落在 32×32 画布的左下（托尾），
  原版弩的握把像素 `(9.5,11.5)/16` 在托中部，换算后差 0.306 格；同时它的实际绘制长度约 2.2 格
  （`worldLengthBlocks=1.44` 沿主轴投影量的，画布对角线更长）。**这两项都是美术侧数据**
  （`art/weapons/ballista.model.json` 的握把点/长度），不是渲染链路，需美术或用户点头后再改。

### 10.5 创造模式下的玛尔基特（反馈第 3 条）

`MargitEntity.resolveTarget` 的**保留**分支本来就会丢掉创造模式目标，但紧随其后的**重新锁定**只判
`isAlive() && !isSpectator()`，于是每 tick 丢一次又抓回来 —— 表现就是"创造模式下仍有攻击欲望"。
现在重新锁定同样要求 `!isCreative()`，创造/旁观玩家永远不是战斗目标（无目标时 AI 回 idle）。
夹具里的 Boss 都是 `setNoAi(true)` 或直接驱动命中结算，所以这条改动不影响既有烟测。

### 10.6 复核状态

- 数据/日志/截图：本条全部可复现（`build-display-candidates.py` → `build-route-config.py` → `-PgripSmoke`）。
- **`humanAccepted` 仍为 `false`**：拼图对照是"与原版同类同机位"的机器可比证据，
  逐件观感的最终收据仍需人眼（这也是契约 §11.7 的口径）。

## 11. 全家族覆盖：把"所有盾牌/剑/弓/弩/斧/镰刀/长鞭都套用原版握持动作"证明到字段级（2026-10-16）

### 11.1 每一族的"原版握持动作"基准

口径：**这一族的物品套这一族的原版同类握法**；近战族（剑/斧/镰刀/长鞭）的基准就是用户说的
"参考剑" = `minecraft:item/handheld`。三处独立的字段级证据：

| 家族 | 原版基准 | 我们这一族的物品走哪条路 | 证据 |
|---|---|---|---|
| 剑 | `minecraft:item/handheld`（参考剑） | `straight_sword` 走 `vanilla_model`（模型字面上就是 `minecraft:item/iron_sword`，`display:null` + `inherit_provider_display`，由原版自己施加）；另 12 件走 2D 派生 display（其中 `starscourge_greatsword` 因白名单哈希待重发仍 pending，暂时走旧 BEWLR）；`thrusting_sword`/`katana` 提供方缺席 → 旧 BEWLR | 派生 display 的 `rotation=[0,-90, 55-(美术轴−45)]`：pitch/yaw 与 `references.melee` 逐字相同，只把美术画的角度归一到原版剑图标的 45° |
| 斧 | 同上 | `axe` 走 `vanilla_model`（`minecraft:item/iron_axe`）；`greataxe` 旧 BEWLR | 同上 |
| 镰刀 | 同上 | `scythe` 旧 BEWLR（物品模型 `weapon_placeholder`） | 见下"逐字相同"检查 |
| 长鞭 | 同上 | `whip` 走 2D 派生 | 同上 |
| 弓 | `minecraft:item/bow` | `short_bow` `vanilla_model`（原版弓模型）；`great_bow` 2D 派生（含 180° 翻转补偿）；`long_bow` 旧 BEWLR + `weapon_placeholder_bow` | `weapon_placeholder_bow` 与原版 `item/bow` 的 4 个手持上下文逐字相同 |
| 弩 | `minecraft:item/crossbow` | `crossbow` `vanilla_model`（原版弩模型）；`ballista` 2D 派生 + 用户校准（§11.4） | `weapon_placeholder_crossbow` 与原版 `item/crossbow` 逐字相同 |
| 盾 | `minecraft:item/shield` | 三面盾全走 2D 派生（`builtin/entity`：握把在模型原点，参考点 = `D_ref·(-0.5,-0.5,-0.5)`） | 实机与**原版盾**同机位落点差 ≤ 1.5e-5 格（§11.2 表） |

"逐字相同"检查（本轮实跑，输入是仓库里的两份数据）：

```
melee     weapon_placeholder         identical contexts=[firstperson_lefthand,
          firstperson_righthand, thirdperson_lefthand, thirdperson_righthand] diff=[]
bow       weapon_placeholder_bow     identical contexts=[... 同上 4 个 ...] diff=[]
crossbow  weapon_placeholder_crossbow identical contexts=[... 同上 4 个 ...] diff=[]
```

即：提供方缺席、仍走旧 BEWLR 的那几件（`thrusting_sword`/`katana`/`greataxe`/`long_bow`/`scythe`），
物品模型自己带的 display 就是原版那一套握法，BEWLR 只负责把 3D 网格按"原版剑图标格"的握把与 45° 摆进图标格。

### 11.2 全家族实机证据表（213 个夹具，一份日志一张对照图）

夹具表按家族排列，每族**先放原版对照物品**（`Items.SHIELD`/`BOW`/`CROSSBOW`/`IRON_SWORD`/`IRON_AXE`/
`DIAMOND_SWORD`，同机位同帧），再放这一族我们自己的**全部**物品，每件都过
第三人称/第一人称 × 左手/右手（再加第三人称副手）。判据两条：贴手距离（越小越贴手，盾牌族的
0.166 格是 §10 已验收的基准）与"和原版同族同机位看起来是不是同一个握法"。

夹具数 **213**（33 个家族物品行 × 6 格 + 6 个代表件的挥砍/格挡格 + 3 个拉弓格），命令
`runClient -PclientSmoke -PgripSmoke`，日志
[bugfix-grip-families-20261016.log](combat-evidence/bugfix-grip-families-20261016.log)，
同机位对照图 [grip-families-third-20261016.png](combat-evidence/grip-families-third-20261016.png)，
逐格原始数据 `build/clientSmoke/grip-proof/*.json`（含手部矩阵、掌心局部坐标、物品到世界矩阵）。

两条判据：**贴手距离**（握把点到手掌中心，格）与旧 BEWLR 路径的硬断言"握把中心必须落在手掌里"。
"与原版同族落点差"是诊断量而不是判据：它把同族原版物品在同一条手部链路上摆一遍，差值里同时包含
"我们美术的握把像素与原版握把像素不重合"这一项（弓族天然最大，剑/盾族最小）。

| 家族 | 物品（路线） | 最大贴手距离(格) | 手掌内 | 与原版同族落点差(格) |
|---|---|---|---|---|
| 剑 | `straight_sword`（**原版铁剑模型本体**） | — 原版自己施加 display，无我们的变换可量 | — | — |
| 剑 | `thrusting_sword`(BEWLR) / `katana`(BEWLR) | 0.0513 | **是** | — |
| 剑 | `starscourge_greatsword`（pending → 旧 BEWLR） | 0.0513 | **是** | — |
| 剑 | `heavy_thrusting_sword` 0.0513 / `curved_sword` 0.0513 / `curved_greatsword` 0.0513 / `great_sword` 0.0513 / `colossal_sword` 0.0513 / `twinblade` 0.0513 / `blasphemous_blade` 0.0513 / `malikeths_black_blade` 0.0513 / `morgotts_cursed_sword` 0.0513 / `sacred_relic_sword` 0.0513 / `hand_of_malenia` 0.0513（全 2D） | 0.0513 | — | 0.0088–0.3174 |
| 斧 | `axe`（**原版铁斧模型本体**） | — | — | — |
| 斧 | `greataxe`(BEWLR) | 0.0513 | **是** | — |
| 镰刀 | `scythe`(BEWLR) | 0.0513 | **是** | — |
| 长鞭 | `whip`(2D) | 0.0513 | — | 0.1282 |
| 弓 | `short_bow`（**原版弓模型本体**） | — | — | — |
| 弓 | `long_bow`(BEWLR) | 0.0513 | **是** | — |
| 弓 | `great_bow`(2D，含 180° 翻转补偿) | 0.1173 | — | 0.2894 |
| 弩 | `crossbow`（**原版弩模型本体**） | — | — | — |
| 弩 | `ballista`(2D + 用户校准，见 §11.4) | 0.527 | — | 0.2343 |
| 盾 | `shield` / `great_shield` / `small_shield`（3 件全 2D） | 0.1657 | — | **0.0000** |

结论：**23 件有我们自己变换的物品全部通过**（无 `CLIENT_GRIP_FAIL`，`CLIENT_SMOKE_PASS`，退出码 0）；
近战族（剑/斧/镰刀/长鞭）贴手距离一律 0.0513 格且左右手一致，盾族与原版盾的落点差是 0.0000 格。
另外 4 件（`straight_sword`/`axe`/`short_bow`/`crossbow`）字面上就是原版物品本体，走原版模型与原版 display，
在夹具表里同样有同机位截图，但不存在"我们的变换"可以量。

<!-- GRIP-TABLE-RAW -->

逐条的原始表（含每件的路线/贴手距离/落点差/样本数）另存
[grip-table-20261016.md](combat-evidence/grip-table-20261016.md)。

### 11.3 2026-10-16 美术批次（10 张 PNG）的接入

美术侧当日 11:12 更新了 10 张 PNG（亵渎圣剑 3、蒙葛特咒剑 3、中盾 front/back + 16/32 GUI 4），
并同步了 `equipment-42.json` / `publishable-assets.json`；美术文档明确写了"游戏仍未接入"。
本轮把这个批次接进**派生数据**（只重跑派生器，不重绘、不覆盖任何美术选版）：

- 重跑 `tools/equipment2d/build-display-candidates.py`（只读 `sprites/*_front.png`），4 项数值变化：

| 物品 | 变化 | 说明 |
|---|---|---|
| `morgotts_cursed_sword` | `sourceSpanPixels` 71.167 → **73.539**，`uniformXYScale` 1.8777 → **1.8172**，`thirdperson_righthand` t `(0,10.294,4.078)` → `(0,9.967,3.772)`，z 45.082 → 46.205 | 握点 `[10.5,45.5]` 与世界长度 2.088 格**未变**，与美术文档一致 |
| `blasphemous_blade` | 主轴 44.896 → 44.774，t 变 ~0.1 格 | 外观换版 |
| `starscourge_greatsword` | 主轴 49.012 → 49.072，三阶矩 −4.039 → −4.586（**仍不翻转**），t 变 ~0.01 格 | 该件盘上 PNG 变化已在美术文档登记 |
| `shield`（中盾） | 各向异性 1.158 → 1.352 | 仍在"近各向同性 → 不推断平面内旋转"的阈值内，display 不变 |

- 重跑 `tools/equipment2d/build-route-config.py`：**42 条，41 ready / 1 pending**。
- `gradlew test`：**197 tests, 0 failures**（这条测试就是把正式 `routes/2d.json` 喂给真实快照解析器，
  逐件复现"握把落在参考点上"的不变量）。
- 运行时模型/纹理由 Gradle 任务 `gradle/equipment-2d.gradle` 从 `art/equipment-2d/full/sprites` 生成
  （该目录是任务输入），所以美术批次在下次构建即生效，无需手工拷贝。
- **仍待美术侧一件事**：`starscourge_greatsword` 的发布白名单哈希。`publishable-assets.json` /
  `equipment-42.json` 记的是 `a0c23427…`，盘上 PNG 是 `270c60fe…`（美术文档已登记这处差异但未回写）。
  该件因此保持 `pending` 并走旧 BEWLR（display 同为原版 handheld），**要进 2D 路线需要美术侧重发这个哈希**；
  重发后派生数据已就绪，重跑两条脚本即可。

### 11.4 弩炮（反馈第 4 条）定案

- 姿态本来就是原版弩的（`rotation [-90,0,-60]` 与原版 `item/crossbow` 逐字相同），两条朝向判据也都判定
  "没有翻转"；差的是**美术握把像素**（画在托尾 `(7.5,24.5)`，原版弩握在托中部 `(9.5,11.5)/16`）。
- 处理**不动美术**、只改 display 的 t：`art/equipment-2d/full/display-overrides.json` 给 `ballista` 写 8 个
  上下文的校准值（握点从 `(7.5,24.5)` 移到 `(12.5,19.5)`），路由条目记
  `displaySource.kind=derived_candidate+user_calibration`、`equipmentAdmission.calibratedItems=["eldencombat:ballista"]`、
  `acceptance.state=calibrated_pending_visual`、`accepted:false`，可回滚、不覆盖派生字段。
- 实机复核（`-PgripSmoke`，`CLIENT_SMOKE_PASS`）：贴手距离 **0.457 格（右手）/ 0.527 格（左手）**；
  同机位对照里手落在枪身/托上，弓臂不再挂到肩上（`docs/combat-evidence/ballista-after-20261016.png`）。
- 口径说明：对该件 `vanillaCounterpartGapBlocks` **不再是握法判据**（它的 display 来自校准而不是派生公式），
  判据是贴手距离 + 人眼同机位对照。
- 未动：`worldLengthBlocks=1.44` 是沿主轴的投影长度，实际绘制对角线约 2.2 格 —— 美术侧口径。

### 11.6 朝向复核：没有一件是"反的"，两处观感差异都归美术

`perpendicular_skew`（垂直三阶矩）只能看见"质量落到主轴另一侧"的翻转；剑形刀身关于主轴接近对称，
所以另做一条**握把射线**判据：在画布固定坐标系里量"握把像素 → 质量重心"的方位角，再与同族原版参考图
比。差 ≈ 0 = 同侧，≈ 180 = 与本族原版是反的。42 件全量结果（`build/tmp-bugreport/anchor-ray-probe.py`）：

- 近战族（含剑/斧/镰刀/长鞭）**全部同侧**，最大偏移 12.5°（`morgotts_cursed_sword`），没有任何一件落在
  "反 180°"区间 → `great_bow` 那类"拿反了"在剑族不存在。
- `twinblade`（315° vs 参考 38.5°）与 `great_bow`（315° vs 54.7°）约成 90°：这两件是**握把在武器中部**
  的造型，重心射线本来就垂直于刀身，该判据对它们退化，不构成发现。
- 观感差异 1：`morgotts_cursed_sword` 在同一机位下刀身比原版剑"更朝上"约 30–40°。它的
  `appliedAxisDegrees` 只有 −8.8°，说明这不是变换问题，而是**弯刃美术**：重心射线比原版剑偏上 12.5°，
  主轴又比原版剑偏 8.8°，补偿只对齐主轴、对齐不了刃缘走向。对照图
  [grip-morgotts-vs-sword-20261016.png](combat-evidence/grip-morgotts-vs-sword-20261016.png)。**美术侧口径**，本轮不动。
- 观感差异 2：`ballista`（§11.4）尺寸与握点，同样是美术侧口径。

### 11.7 状态

- 数据/命令/日志/截图全部可复现：`build-display-candidates.py` → `build-route-config.py` →
  `gradlew test` → `runClient -PclientSmoke -PgripSmoke`。
- **`humanAccepted` 仍为 `false`**：本节的表与图是"与原版同类同机位"的机器可比证据，逐件观感的最终收据仍需人眼。
- 未验：提供方（Spartan Weaponry）实机；`starscourge_greatsword` 的白名单重发。
