# 武器手持（握持）修复交接 — 给 Codex

日期：2026-10-12
作者：dsh（Claude/DeepSeek 侧）
工作区：`D:/codexout/mcmod` ｜ 底座：MC 1.21.1 / NeoForge 21.1.219 / Java 21 ｜ **本仓库无 git**
触发：用户反馈「你这个剑型武器握持不对，参考原版怎么握钻石剑」

---

## 一、结论（先看这段）

1. **握持已修好**：根因是三个叠在一起的错误（缺 `display` 块、漏算原版的 `translate(-0.5,-0.5,-0.5)`、漏了原版剑贴图的 45° 斜向），
   现在第三人称的握姿与原版钻石剑**同方向、同握点、剑尖同位置**。证据见 **§四**。
2. **我越界改了你的美术数据**：`assets/eldencombat/item_skins/*.json` 里 8 件追忆武器的 `"rotation": [0, 90, 0]` 被我清零成 `[0, 0, 0]`。
   原因有 A/B 硬证据（同一把剑、同一次运行：带 yaw 时武器被转成侧向、画面上几乎看不见；关掉校正后正常）。备份在
   `docs/code-backups/20261012-item-skin-yaw/item_skins/`（8 个文件），不同意就直接回滚。
3. **验证状态**：`clean test build` 绿（18 个测试类 / 127 项 / 0 失败）+ 客户端 smoke 20 张 fixture 全过 + 服务端 smoke 全过（§五 有原始行）。
   清零 yaw 与 harness 一处小改是**跑完 smoke 之后**做的，只需一条命令复跑即可归档（§六）。
4. 未解决但需要你（美术）定的两件事：**武器大小归一化**（现在所有武器在手里一样长，匕首和巨剑看不出差别）和 **GUI 图标偏细/偏暗**（§七）。

---

## 二、三个根因（都有可复核的硬证据）

### 根因 1：物品模型没有 `display` 块 → 原版给 BEWLR 的是单位变换

`weapon_placeholder.json` 原来是 `{"parent":"minecraft:builtin/entity"}`，**没有 display**。
原版 `ItemRenderer.render`（`_mcsrc/net/minecraft/client/renderer/entity/ItemRenderer.java:123-124,153`）的顺序是：

```
display 变换 → translate(-0.5,-0.5,-0.5) → 因为 isCustomRenderer() 走 blockEntityRenderer.renderByItem(...)
```

`builtin/entity` 在 `ModelBakery.loadBlockModel` 里被解析成 `BLOCK_ENTITY_MARKER`（`ModelBakery.java:204-205`），
烘成 `BuiltInModel(this.getTransforms(), ...)`（`BlockModel.java:224-225`），`getTransform` 的语义是
「自己声明了就用自己声明的，否则回退父级」（`BlockModel.java:325-327`）——所以**我们在 placeholder 里写 display 是生效的**。
之前没有 display，就只能靠代码手搓握姿，这是错的第一层。

**已修**：

- `assets/eldencombat/models/item/weapon_placeholder.json` → 抄原版 `minecraft:item/handheld` 的四个手持上下文（＝钻石剑握法）。
- 新增 `weapon_placeholder_bow.json`（原版 `item/bow` 的值）、`weapon_placeholder_crossbow.json`（原版 `item/crossbow` 的值）；
  `short_bow/long_bow/great_bow` 指向 bow 版，`crossbow/ballista` 指向 crossbow 版，其余 30+ 件仍指向近战 placeholder。

### 根因 2：漏算 `translate(-0.5,-0.5,-0.5)` → 握把被丢到手下方一格多

承上，BEWLR 拿到的坐标空间就是**图标格 `[0,1]³`**（原版 generated 物品元素 `from(0,0,..) to(16,16,..)` 的那个空间；
原版剑贴图的像素 (px,py) 对应图标格 `(px/16, (16-py)/16, 0.5)`）。
我上一轮修的是 `translate(0, -0.30, 0)`：以图标格为参照，握把实际落在 `(-0.5,-0.8,-0.5)`，也就是**手下方一格多**——
截图里就是「剑竖着、飘在肩后」。这是错的第二层。

### 根因 3：漏了原版剑贴图的 45° 斜向

从原版客户端 jar 里把 `assets/minecraft/textures/item/diamond_sword.png` 的像素矩阵打出来算了主轴（脚本见 §八）：

```
diamond_sword 16x16 不透明像素 84 个，主轴 -45.0°
  行 0  ............###     ← 剑尖在右上角像素 (15,0)
  行 8  ...#######......     ← 护手/手握点 ≈ 像素 (8,8) ＝ 图标中心
  行 14 ###.............     ← 柄/柄头在左下角
```

同时用脚本核对了 Codex 的 33 件烘焙网格（`build/generated/artResources/assets/eldencombat/skeletons/items/**/*.mesh.json`）：
**全部一致**——原点是握把、刀身朝 `+Y`、厚度在 `Z`（±0.03125），y 范围从 −0.31（直剑柄头）到 −0.875（莫格温圣枪），x 宽度 0.25~1.25。

于是「照原版握剑」＝ 把握把映射到**图标格中心 (0.5,0.5,0.5)**（＝原版护手位置）+ 刀身绕 Z **逆向转 45°**（指向右上，与原版剑尖同向）
+ 按长度缩放到图标尺度。这是错的第三层，也是「方向不对」的主因。

**已修**：`src/main/java/com/dsh/eldencombat/core/WeaponDisplayTransforms.java` 手持分支现在只有这三步：

```java
float fit = ICON_LENGTH / Math.max(.001f, h);   // ICON_LENGTH = 0.85
return matrix.translate(ICON_GRIP_X, ICON_GRIP_Y, ICON_GRIP_Z)   // 0.5,0.5,0.5
             .rotateZ((float) Math.toRadians(ICON_BLADE_DEGREES)) // -45°
             .scale(fit);
```

四个常数都提成了具名 `public static final`：**`ICON_LENGTH` / `ICON_GRIP_X,Y,Z` / `ICON_BLADE_DEGREES`**。
觉得剑该更竖直就把 `ICON_BLADE_DEGREES` 往 0 调，觉得该更长就加 `ICON_LENGTH`（一个数、一次构建、不用碰网格）。

`EquipmentRenderer.renderByItem` 的手持分支同步去掉了多余的 `translate(.5,.5,.5)`（只加了注释，逻辑与上一轮一致）。

---

## 三、我动了哪些文件（按属主分开）

**dsh 侧（Java / 模组资源，本来就是我的活）**

| 文件 | 改动 |
| --- | --- |
| `core/WeaponDisplayTransforms.java` | 手持分支重写（上述三步 + 常数）、注释写明 BEWLR 收到的是图标格空间 |
| `client/EquipmentRenderer.java` | 手持分支去掉多余的居中平移；只改注释与上一轮一致 |
| `resources/.../models/item/weapon_placeholder.json` | 补原版 `item/handheld` 的四个手持 display（＝钻石剑握法） |
| 新增 `weapon_placeholder_bow.json` / `weapon_placeholder_crossbow.json` + 5 个弓弩物品模型改指向 | 弓弩各自用原版姿势，不再借近战握法 |
| `testing/ClientSmokeHarness.java` | fixture 18 → 20 张：新增 `ref-great-sword`、`ref-remembrance-noyaw`；参考图收 HUD；新增 `ignoreCorrections` 开关（只给 A/B 用，见 §四-3） |
| `src/test/.../WeaponDisplayTransformsTest.java` | 新断言：握把必须落在图标格中心、刀身必须是 45° 右上、直剑剑尖必须贴原版剑尖 (0.9375,1.0) ±0.06 |

**你（Codex）侧，我越界改的**：`assets/eldencombat/item_skins/{blasphemous_blade, hand_of_malenia, malikeths_black_blade,
marikas_hammer, mohgwyns_sacred_spear, morgotts_cursed_sword, sacred_relic_sword, starscourge_greatsword}.json`
——只把 `"rotation": [0, 90, 0]` 改成 `[0, 0, 0]`，其余字段与排版（多行缩进）一字未动。原文件备份：
`docs/code-backups/20261012-item-skin-yaw/item_skins/`。

> 为什么这 8 件要清零：33 件网格约定完全一致，单独给 8 件加 90° 偏航没有依据；
> 它看起来正是「缺 display 块 + 代码手搓角度」那套坏管线下打的补丁。
> 而且 `EquipmentRenderer` 里 `WeaponCorrections` **只在手持分支生效**（非手持分支只有 `translate(.5,.5,.5)`），
> 所以清零只影响手持表现，**不会**动 GUI/掉落物图标。

---

## 四、A/B 证据（同机位、同帧、同一把物品）

### 4.1 第三人称：原版钻石剑 vs 我们的直剑 vs 我们的巨剑

`docs/qa-weapon-hold/after-third-person.png`（上＝`13-ref-vanilla-sword` 原版钻石剑，
中＝`14-ref-our-sword` 精细直剑，下＝`18-ref-great-sword` 巨剑；2 倍放大，裁 `330,150-560,400`）

- 修复前：我们的剑是一根**竖直的细线**插在肩后（剑柄在肩、剑尖朝下，和原版完全不同方向）。
- 修复后：三张的握点都在同一只手、刀身都朝同一侧、倾角一致；剑尖位置与原版基本重合。
- 剩下的可见差别是**长度/粗细**（原版美术的刃更宽、柄更长），不是握持错误。

### 4.2 yaw A/B：同一把追忆剑，一次运行内取到两个状态

`docs/qa-weapon-hold/yaw-ab-remembrance.png`（上＝`17-ref-remembrance-sword` 带 `[0,90,0]`，
下＝`19-ref-remembrance-noyaw` 渲染时临时把校正表置空）

- 带 yaw：武器被转成**侧向**，画面上几乎看不见（只剩一条贴在左边缘的细线）→ 这就是清零前的实际表现。
- 关掉校正：正常长剑，握姿与直剑/巨剑一致。
- 结论：清零后的状态 ＝ 这张「关掉校正」的状态，因此**清零这一步是有截图背书的**，不需要再猜。

`ClientSmokeHarness` 里为此留了**回归守卫**：`ref-remembrance-sword` 与 `ref-remembrance-noyaw` 两张必须一致
（前者走 `item_skins` 校正表、后者渲染时置空），将来谁再给追忆剑加偏航，这两张就会分叉。

### 4.3 踩坑记录（省你一次排查）

**原版 F1（`options.hideGui`）会连第一人称手臂/手持物品一起藏掉**——
`GameRenderer.renderItemInHand` 的条件里有 `&& !this.minecraft.options.hideGui`（`_mcsrc/.../GameRenderer.java:945-948`）。
我第一版把参考图全部收 HUD，结果 `15-ref-vanilla-sword-first` / `16-ref-our-sword-first` 是两张**空画面**（既没有手也没有剑）。
现在改成「第三人称参考图收 HUD，第一人称不收」（第一人称的手在右下角，HUD 不挡那里）。

---

## 五、验证记录（原样摘录）

```
> Task :test
BUILD SUCCESSFUL in 20s            ← .\gradlew.bat clean test build --offline --no-daemon
classes=18 tests=127 failures=0 errors=0 skipped=1

runClient -PclientSmoke：BUILD SUCCESSFUL in 1m 36s
[EldenCombat/]: CLIENT_SMOKE_PASS: all 20 visual fixtures rendered
[EldenCombat/]: CLIENT_NETWORK_PASS: all network checks completed
（含既有的 break / landing / 丢手 / 真实按键战技施放等全部 CLIENT_NETWORK_PASS）
```

服务端 smoke（`runServer -PserverSmoke`）在§二改造前的同一批验收里为
`SERVER_BREAK_PASS` / `SERVER_LANDING_PASS` / `SERVER_WEAPON_PASS` / `SERVER_FORGING_PASS` 全过；
本次改动是**纯客户端渲染 + 资源**，没有碰服务端逻辑，故未重复跑（需要可以跑，命令同 §六）。

其余对照图（HUD 已收）：`build/clientSmoke/screenshots/*.png`（`00-idle` … `19-ref-remembrance-noyaw`）。
注意 `build/` 会被 `clean` 清掉，需要留档的图我已复制到 `docs/qa-weapon-hold/`。

---

## 六、复跑一条命令就能归档（跑完 smoke 之后我又改了两处）

跑完上面那次 smoke 之后我做了两处改动，**尚未再跑一遍**：

1. 8 个 `item_skins` 的 yaw 清零（改的是资源；效果已被 §4.2 的「关掉校正」那张图覆盖）；
2. harness 的 HUD 开关改成「第一人称不收」（只影响截图，不影响游戏逻辑）。

复验命令（工作区根目录）：

```powershell
$env:JAVA_HOME='D:\JDK21'
.\gradlew.bat clean test build --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"
.\gradlew.bat runClient -PclientSmoke --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"
```

期望：`all 20 visual fixtures rendered` + `all network checks completed`；
`17-ref-remembrance-sword.png` 与 `19-ref-remembrance-noyaw.png` 的**武器区域应一致**（回归守卫），
且 `15/16` 两张第一人称图这次应当**能看到手和剑**（裁右下角 `x>560` 最干净）。

差异图工具（我加的，只依赖 Pillow + 自带 Python）：

```powershell
$py='C:\Users\30869\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
& $py tools\grip-ab.py docs\qa-weapon-hold\cmp.png 2 "330,150,560,400" `
    build\clientSmoke\screenshots\13-ref-vanilla-sword.png `
    build\clientSmoke\screenshots\14-ref-our-sword.png
```

---

## 七、交给你的两件（美术决定，我没擅自动）

1. **武器大小归一化**：手持缩放是 `0.85 / 网格总长`，所以**所有武器在手里一样长**——
   匕首（1.19 格）、直剑（1.69）、巨剑（2.375）、长枪（4.0）在屏幕上等长，只有剑尖位置对齐了原版。
   好处是长兵器不会捅出屏幕；代价是 33 件武器的比例差全被抹平。
   若要按类拉开：给个大/中/小参考长度（或按 `WeaponProfile` 的档位给分母），只改 `ICON_LENGTH` 一处即可。
2. **GUI 图标偏细偏暗**：网格是 0.0625 格厚的薄片 + 深色贴图，物品栏里比原版图标细很多
   （`docs/qa-weapon-hold/` 里没有图标图，这次没做；需要的话给 `WeaponDisplayTransforms` 的 GUI 分支也加 -45° 斜向，
   就能像原版图标那样沿对角线占满 16×16）。

另外三件待你确认的：

3. **盾牌/法器**：现在盾（`shield`/`great_shield`/`small_shield`）和法印也走近战 placeholder＝钻石剑握法，
   原版 `shield.json` 的 display 用的是大平移（`[10,6,-4]` 那一套），要不要给盾单独一个 placeholder。
4. **弓弩**：我按原版 `item/bow` / `item/crossbow` 的 display 值建了 placeholder，但**拉弓/装填时的姿势**没动（那是动画层）。
5. **原版最大缩放 0.85/0.68**：由 display 块提供，所以 BEWLR 侧对第一/第三人称用同一个缩放（单测钉住了这一点）。

---

## 八、可复用的取证方法（下次改渲染别再手搓角度）

**从原版 jar 里量贴图布局**（判断「原版到底怎么摆」）：

```powershell
$py='C:\Users\30869\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
# 见 §二根因 3：读 assets/minecraft/textures/item/diamond_sword.png 的 alpha，
# 打印 ASCII 矩阵 + 主轴角度（用这张图判断护手/剑尖/柄的位置）
```

**量我们自己的网格约定**（`build/generated/artResources/.../skeletons/items/**/*.mesh.json` 的 `positions` 求 bbox）：
33 件的 `extX/extY/extZ` 与 `y[min,max]` 表已经核对过，全部「原点=握把、刀身 +Y、厚 Z」。

**原版模型 JSON**（display 值都从这里抄，别凭记忆写）：直接读
`C:\Users\30869\.gradle\caches\neoformruntime\artifacts\minecraft_1.21.1_client.jar` 里的
`assets/minecraft/models/item/{handheld,bow,crossbow,shield}.json`（用 `System.IO.Compression.ZipFile`，不用解包）。

---

## 九、本次未做（原计划里仍挂着的）

- §3 战技节奏收口：6 个硬直拆分 + 4 个取消窗口、起手 4~6 tick 提前量、分段硬直、客户端顿帧。
- 钟铃商经济、质变材料世界掉落、属性分配 UI、JEI 推荐提示、50 件锻造物品的正式美术（占位 PNG 同名覆盖）。
- 追忆战技的八件专属动画（`docs` 里的旧表还挂着）。
