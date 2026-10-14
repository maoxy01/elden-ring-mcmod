# dsh → Codex：Java 侧首版交接与下一步需求

日期：2026-10-11　项目：`D:\codexout\mcmod`　对应你的交接：[DSH_HANDOFF.md](../DSH_HANDOFF.md)

## 0. 一句话

Java 侧首版**已可构建、可运行、已通过自动验收**（`build\libs\eldencombat-0.1.0.jar`），
模型/动作/贴图已按你的 `art/margit/` 接进渲染与服务端判定；
现在缺的是**玩家模型与玩家动作**，以及音效/粒子需求——这些都在等你。

## 1. 我实现了什么（对应你的实现顺序 1~5）

| 你的步骤 | 交付情况 |
| --- | --- |
| ① 工程/JDK21/可重复构建 | NeoForge 21.1.209 + ModDevGradle 2.0.148 + Gradle 8.12；构建 JDK 固定 `D:\JDK21`，系统默认 Java 不动 |
| ① 静态模型/贴图/三尾骨/武器插槽 | 方块骨架加载器 + 渲染器 + 服务端骨骼采样，全部基于你的 `*.model.json` / `*.animations.json` |
| ② 玩家动作状态/资源/输入/平滑 | 轻/重/满蓄力重、翻滚无敌、盾防、弹反、喝药、破防硬直；可改绑 KeyMapping；数值全在 config JSON |
| ③ 服务端扫掠命中/盾防/弹反/失衡硬直 | 胶囊/盒扫掠（覆盖上一与当前 tick）+ 阻挡 raycast + 盾防 120° + 弹反窗口 + 失衡硬直 |
| ④ 玛尔基特两阶段 AI/八个动作/黄金武器 | 距离分档 + 权重选招 + 同招不超两次；`equip/unequip` 事件驱动黄金件全亮显示 |
| ⑤ HUD/调试入口/清理/打包 | HP+精力 HUD、`/eldencombat …` 命令、jar + `docs/BUILD.md` |

已按要求取消：处决、跳跃攻击（空中与地面同规则、同数值、落地不重复命中）、Epic Fight 路线。

## 2. ⚠️ 你改资产时必须保持的接口（我会按这些名字取数据，改名就断）

### 2.1 模型 schema v1（`*.model.json`）

- `schemaVersion = 1`、`unitsPerBlock = 16`、右手系、Y 向上、模型朝向 **-Z**、根部原点在脚底；
- `bones[i]`：`name` / `parent`（根为 null）/ `pivot` / `rotation`（度，**XYZ 欧拉**），父级必须先于子级出现；
- `cubes[i]`：`bone` / `from` / `to` / `material`，`pivot` / `rotation` 可选（缺省 pivot 用 `from`，缺省 rotation 为零）；
- `materials.*.uv` = **PNG 顶部原点**的像素矩形 `[u0,v0,u1,v1]`，我按像素做半像素内缩，不会渗色；
- 渲染矩阵与你的 viewer 完全一致：`W = W_parent · T((pivot-parent.pivot)/16) · R(静态+动画增量, XYZ)`；
- 我已内建等价于 `viewer.html` 的六面 UV 映射（面序 +X/-X/+Y/-Y/+Z/-Z，TL/TR/BL/BR 语义相同），
  所以**浏览器预览看到的贴图朝向就是游戏里看到的朝向**。

### 2.2 骨骼与方块名契约（硬依赖）

| 名字 | 用途 | 谁在用 |
| --- | --- | --- |
| `staff_socket` | 右手木杖插槽 | 渲染 + 判定 |
| `spectral_socket` | 左手黄金武器插槽 | 渲染 + 判定 |
| `staff_shaft` | 木杖长轴判定体（胶囊 r=0.28） | 服务端命中 |
| `head`（方块，位于 `spectral_hammer.model.json`） | 黄金锤锤头盒判定（半边长 0.9/0.7/0.7） | 服务端命中 |
| `hand_l` / `pelvis` / `head`（骨骼） | 自检基准点 | 自检 |
| `tail_01 → tail_02 → tail_03` | 尾巴三段，**只能三根** | 自检强制 |

- 黄金匕首/锤子模型**根部原点必须仍是握持点**，我直接继承插槽矩阵，不做偏移补偿；
- 玩家武器模型将来同理：请给我插槽骨骼（建议 `sword_socket` / `shield_socket`）与根部握持点约定。

### 2.3 动作数据契约（`margit.animations.json`）

- `tick` 是整数游戏 tick（20 TPS），插值线性；`tracks` 按骨骼名索引，**缺失轨道 = 零增量**；
- 播放姿态 = 静态旋转 + 增量**逐轴相加**后按 XYZ 构造旋转；每次播放新动作从静态姿态重来（不累积）；
- 八个 clip id 固定：`idle`、`walk`、`staff_sweep`、`staff_slam`、`dagger_slash`、`hammer_slam`、`stagger`、`death`；
- **`hit_start`/`hit_end` 就是服务端伤害窗口**，我直接用，不另设计时器——要调手感就改这两个 tick；
- `impact` 目前只被记录（留给粒子/音效），`footstep` 同理；`equip` 必须带字段 `item`，取值只能是
  `spectral_dagger` 或 `spectral_hammer`；`unequip` 撤下黄金件；
- 客户端用 partial tick 插值显示，服务端只用整数 tick，所以 20 TPS 与渲染帧率无关。

### 2.4 贴图与美术约束

- `margit.png` 128×128 RGBA，游戏内**最近邻采样**（不模糊）；
- `head` 方块的 **-Z（north）面使用 `face` 材质**，其余五面用方块自身材质（这是 schema v1 约定）；
- `gold.emissive` 的材质我用 `FULL_BRIGHT` 全亮渲染，首版不做透明折射与动态光照；
- 实体碰撞盒 1.0×3.1：长杖、角、尾巴、黄金锤**都不进整体碰撞盒**（判定体与碰撞盒是两个模块）。

## 3. 我加的自检：你改完资产请跑一次

```powershell
cd D:\codexout\mcmod
$env:JAVA_HOME='D:\JDK21'
.\gradlew.bat test        # 95 项，0 失败才算过
```

或游戏内 `/eldencombat selftest`（同一份清单，失败项红字列出）。

其中**与你的资产直接相关**的检查（改了会立刻 FAIL）：

- 骨骼 19 / 方块 38；尾巴骨骼 **3**、尾巴方块 **3**、链必须是 `tail_01→tail_02→tail_03` 且不得有 `tail_04`；
- 存在 `staff_socket`、`spectral_socket`；
- 八个动作齐全；`dagger_slash` 的 `equip` 是 `spectral_dagger`、`hammer_slam` 是 `spectral_hammer`、`death` 期间无武器；
- 骨盆原点 y=1.5 方块、头部高于骨盆、躯干盒在 1.0~3.0 之间（几何没跑偏）；
- 木杖判定体长度 >1.5 方块、锤头盒在握持点附近。

如果你有意要改这些数量/命名，先告诉我，我改检查项，避免你的新资产被判 FAIL。

## 4. 我需要你的下一批交付（按优先级）

### 4.1 玩家模型与玩家动作（阻塞项，最优先）

我现在玩家侧是**数值胶囊**近似（手部→刀尖 `reach` 2.6/3.2、横扫角 120°/140°、半径 0.18/0.24），
拿到你的玩家骨架后我会换成真实骨骼采样，**规则层不用改，只换采样源**。请给我：

1. 玩家模型 `*.model.json`（同一 schema v1），含插槽骨骼 `sword_socket` / `shield_socket`；
2. 三种载体各自的动作草稿，建议 clip id：`idle`、`walk`、`run`、`roll`、`guard_idle`、`guard_hit`、`guard_break`、
   `parry`、`drink`、`hit_stagger`、`death`，以及直剑/大剑/盾牌的：
   `light_1`、`light_2`、`heavy_windup`（蓄力循环或定格）、`heavy_release`、`charged_release`、`jump_attack`
   （注意：最新范围里**不做专门跳跃攻击**，空中沿用普通轻/重，所以这组可以不做，或只做轻/重通用的空中姿态）；
3. 每个攻击 clip 给出 `lengthTicks` + `hit_start`/`hit_end`，与我现有默认窗口对齐或直接给新数值（我改 config）：
   直剑轻击 22 tick / [8,12)，大剑轻击 30 / [12,17)，重击 34 / [15,20)，满蓄力重击 36 / [16,22)；
4. 翻滚 20 tick / 无敌 [4,11)、弹反 18 / [3,7)、喝药 28 / 第 18 tick 生效——这三个是我这边的状态机，动作只要时长匹配即可。

### 4.2 音效清单（格式 ogg，事件 tick 对齐）

挥砍（轻/重/蓄力）、命中、盾防格挡、破防、弹反成功、翻滚、喝药、玩家受伤/死亡、
Boss 脚步、Boss 三种武器挥动与命中、Boss 咆哮、Boss 死亡。现在临时借用了原版 Ravager 与盾牌音效。
`footstep` / `impact` 事件已经存在于动作数据里，你给音源我就挂上。

### 4.3 粒子与特效要求

Boss 死亡消失时机、锤头落点尘土、黄金件残影——我留了 `impact` 事件钩子与 `death` 结束后 52 tick 的清理窗口，
给我"什么 tick、什么特效、什么颜色/密度"就能加。

### 4.4 美术复核清单（请按此给我 tick 级反馈）

- 木杖横扫/下砸、匕首补刀、黄金锤下砸的**前摇是否看得清**（我给当前窗口：20-27 / 30-35 / 11-17 / 39-45）；
- 二阶段锤击是否需要改成跳砸（我在 config 里加动作与距离分档即可）；
- 正/侧/背轮廓与 01C 的一致性；
- 手持武器在动作中是否脱离插槽（尤其 `hammer_slam` 与 `staff_slam` 的极端帧）。

## 5. 判定体口径（美术验收时可直接对照）

| 判定体 | 形状 | 尺寸 |
| --- | --- | --- |
| 木杖横扫/下砸 | `staff_shaft` 长轴胶囊 | r = 0.28 / 0.34 |
| 黄金匕首 | 握持点→最远角胶囊 | r = 0.20 |
| 黄金锤 | `head` 方块世界 AABB（扫掠） | 半边长 0.9/0.7/0.7 |
| 玩家直剑/大剑 | 手部→刀尖胶囊（待玩家骨架） | reach 2.6 / 3.2，半径 0.18 / 0.24 |

全部覆盖**上一 tick 与当前 tick**，并做阻挡 raycast；人体与尾巴永不作为伤害体。

## 6. 已知偏差与待办（我这边）

1. **客户端观感未复核**：需要 `gradlew runClient` 人工看画面（前摇、落点、武器是否脱手、贴图是否锐利）。
2. 玩家侧仍是数值胶囊（等 4.1）。
3. 借用原版音效；无 Boss 血条、无死亡粒子与打击特效。
4. 手感数值均为首轮调试默认值，等你的 tick 级反馈一起调。

## 7. 怎么复现我的验收

```powershell
cd D:\codexout\mcmod
$env:JAVA_HOME='D:\JDK21'
$env:GRADLE_OPTS='-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890'
.\gradlew.bat test        # 95 项自检
.\gradlew.bat runServer   # run\ 里的自动数据包会刷 Boss、验伤、验死亡清理
```

最近一次实测输出：`[selftest] PASS ×95 / FAIL 0`、
`MARGIT_SUMMONED` → `MARGIT_ALIVE_AFTER_100_TICKS` → `DAMAGE_PIPELINE_OBSERVED` → `MARGIT_REMOVED_AFTER_DEATH` → `DONE`。

## 8. 文件索引

| 内容 | 位置 |
| --- | --- |
| 规则层（无 MC 依赖，可单测） | `src/main/java/com/dsh/eldencombat/core/` |
| 方块骨架/动作/判定体采样 | `src/main/java/com/dsh/eldencombat/skeleton/` |
| 服务端状态机与命中 | `src/main/java/com/dsh/eldencombat/server/` |
| Boss 实体与 AI | `src/main/java/com/dsh/eldencombat/entity/MargitEntity.java` |
| 客户端输入/HUD/渲染 | `src/main/java/com/dsh/eldencombat/client/` |
| 自检清单（95 项） | `src/main/java/com/dsh/eldencombat/testing/SelfTest.java` |
| 构建与安装 | [docs/BUILD.md](BUILD.md) |
| 实现与验收状态 | [docs/IMPLEMENTATION.md](IMPLEMENTATION.md) |
| 可调数值（运行时生成） | `run/config/eldencombat-combat.json`（改完 `/eldencombat reload`） |
