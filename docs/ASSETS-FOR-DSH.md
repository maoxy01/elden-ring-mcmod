# Codex → dsh：玩家与装备资产第二批

日期：2026-10-11。接续 `REPORT-FOR-CODEX.md`，本文件补充而不替代根目录 `DSH_HANDOFF.md`。

接入更新：用户后续已将剩余代码交给 Codex，本文件下文为原始资产接口说明，不再表示任务仍需 dsh 执行。玩家渲染、同步、刀身判定、装备注册、粒子与 Boss 血条已接入。实际运行时自定义模型路径改为 `assets/eldencombat/skeletons/{player,entity}/`，避免被 Minecraft 原版物品模型加载器误解析；动画、贴图目录不变。最新实现与验收见 `CODEX-IMPLEMENTATION.md`。

## 用户决定

- 保留玩家自己的皮肤；标准臂与细臂都支持。绿色人偶仅供预览，不是玩家默认外观。
- 自制粗块直剑、大剑、盾牌、药瓶；肘膝简单，不加手指、脚趾骨骼。
- 独立战斗引擎，不依赖 Epic Fight。不做处决、配对终结动作或专门跳跃攻击；空中沿用普通攻击。
- Codex 提供美术与计划；Java、Gradle 资源同步和游戏内接入仍由 dsh 实现。这次没有修改你的 Java 文件。
- 玛尔基特原资产未修改：38 方块、19 骨骼、三段尾巴、八个动作和原有窗口全部保留。

## 文件清单

全部在 `art/player/`。`preview.html` 内嵌 Three.js 与资源，可直接打开，无需服务器。

| 文件 | 用途 |
| --- | --- |
| `player_classic.model.json` / `player_slim.model.json` | schema v1 玩家骨架，各 17 骨骼、10 方块，模型高 1.8 格 |
| `player_classic.skin.json` / `player_slim.skin.json` | 六面皮肤 UV、第二层 UV、关节内部面名单；新渲染适配数据 |
| `player_classic.bbmodel` / `player_slim.bbmodel` | 内嵌预览皮肤及直剑 16 个动作的 Blockbench 5.x 源文件 |
| `player_*.great_sword.bbmodel` | 对应臂型的大剑源文件，左手握持已烘焙 |
| `straight_sword.model.json` / `great_sword.model.json` / `shield.model.json` / `healing_flask.model.json` | 装备模型，共用 `equipment.png`；同名 `.bbmodel` 为源文件 |
| `straight_sword.animations.json` | 直剑完整动作集，标准臂与细臂共用 |
| `player_classic.great_sword.animations.json` / `player_slim.great_sword.animations.json` | **运行时优先加载的大剑完整动作集**；按臂型选择，左手旋转已烘焙，不需要 Java IK |
| `great_sword.animations.json` | 未烘焙左手的原始设计轨道，仅留作编辑参考；不要优先于臂型版本加载 |
| `player.common.animations.json` | 共通动作参考；完整武器动作文件已经包含它们，不要重复合并同名动作 |
| `player.visual-offsets.json` | 翻滚、死亡的视觉根骨 Y 位移，需扩展采样接口 |
| `player.presentation.json` | ActionId、武器档位、皮肤与装备显隐映射 |
| `equipment.hit-shapes.json` | 本地握持坐标下的刀身轴与胶囊半径 |
| `combat.effects.json` / `fx/*.png` | 粒子事件规格及三张 16×16 原创透明粒子贴图 |
| `combat.sound-design.json` | 音效制作规格，**不是已完成的音频库**；没有 OGG 或 sounds.json |
| `validation.json` / `preview-validation.json` / `previews/*.png` | 结构与浏览器自动检查结果、实际渲染截图；不代表游戏内验收 |

## 先接入这些接口

### 1. 玩家皮肤，不替换成固定人偶

仍用 schema v1 的骨骼和方块解析器；皮肤 UV 不强塞进 `materials.*.uv`，而由 `*.skin.json` 按 `cubeName → faceName → uvRect` 提供。north 是 -Z，面序同现有渲染器。UV 顶部原点、64×64，最近邻。以游戏获取到的玩家皮肤纹理和 default/slim 类型选择资源；传统皮肤转换交给游戏的皮肤加载流程。

预览支持上传 64×64 PNG，但只展示基础皮肤层，不展示第二层或盔甲。**游戏接入必须按玩家设置绘制帽子、外套、袖子、裤腿第二层；不能因此移除盔甲、披风等原版层。** 第二层 inflate 按 sidecar 的模型单位使用；关节内部面不要套外层衣物，避免肘膝之间出现浮动盖板。身体无动作时退回原版渲染；本批只定义战斗时第三人称骨架，不应直接整段替换原版玩家生命周期和死亡重生流程。

几何和 pivot 已乘 0.9，使总高为 1.8 格；**渲染和判定不要再次缩放**。`unitsPerBlock=16` 不变。根在脚底，朝向 -Z。骨骼名称见模型数据，不要把玛尔基特的玩家臂名称套进来。

主手插槽 `sword_socket`、副手 `shield_socket`、喝药 `drink_socket`。剑身沿装备本地 +Y，原点为握持点。直剑 blade 轴 `[0,3,0]→[0,22,0]`，大剑 `[0,4.5,0]→[0,31,0]`，除以 16 后进入世界坐标。用插槽矩阵乘以轴端点，沿上一与当前 tick 扫掠；**不要继续拿 2.6/3.2 的近似 reach 再叠加这套模型**。这些是新的真实几何范围，是否调整尺寸由游戏实测决定。

左右手切换必须让几何、骨架旋转、装备插槽与服务端命中采样一致镜像，皮肤 UV 不能翻转。服务器需要知道臂型与主手设置，不能依据客户端提交的世界端点判伤。

### 2. 旋转不变；视觉位移新增 sidecar

现有 `AnimationClip` 的旋转、静态角逐轴相加、XYZ、20 TPS 和线性插值全部不变。

`player.visual-offsets.json.clips[clipId].positionTracks[boneName]` 使用同样的 `{tick,value:[x,y,z]}` 键格式，单位为模型单位。只给 root 的 Y 位移。矩阵扩展为：

```text
W = W_parent * T((pivot - parentPivot + positionDelta) / 16) * R(static + rotationDelta)
```

root 无 parent 时减项为零。位移每帧重置后重新采样，不累积。翻滚 Y 补偿使身体不穿地，死亡 Y 补偿使倒地贴地；**不写实体坐标、速度、碰撞盒或无敌判定**。翻滚前进仍完全由现有服务端 movementTicks / rollSpeed 决定，不能再加模型 Z 位移。没有实现 positionTracks 前，不要把仅旋转的翻滚/倒地当成最终画面。

大剑左手使用臂型版本的已烘焙旋转轨道，浏览器也播放同一份轨道。预览生成器的 IK 只用于离线制作，不要求把 Three.js 或 IK 引擎带进模组。

### 3. 动作映射与窗口

| 状态 | clip | 总 tick / 伤害窗口 |
| --- | --- | --- |
| NONE | idle / walk / run | 按速度选择，循环 |
| LIGHT_ATTACK，直剑 | light_1 / light_2 | 22 / [8,12) |
| LIGHT_ATTACK，大剑 | light_1 / light_2 | 30 / [12,17) |
| HEAVY_CHARGE | heavy_windup | 30；20 达到完整姿态，20～30 保持，无伤害 |
| HEAVY_ATTACK | heavy_release | 34 / [15,20) |
| CHARGED_HEAVY | charged_release | 36 / [16,22) |
| ROLL | roll | 20；仍用现有无敌 [4,11)、移动 [2,14) |
| PARRY | parry | 18；现有弹反窗口 [3,7)，成功仅硬直 |
| DRINK | drink | 28；18 消耗与回血仍由服务器执行 |
| GUARD | guard_idle | 循环；guard_hit 是防御成功后的 12 tick 视觉覆盖，不新增规则状态 |
| GUARD_BROKEN | guard_break | 24 |
| STAGGER | hit_stagger | 16 tick 视觉草稿，按实际服务器硬直长度映射播放进度 |
| DEATH | death | 40 tick 视觉草稿，不改变原版玩家死亡/重生计时 |

`light_2` 是备选动作，不新增连击取消、额外伤害窗口或特殊输入。首版可以只用 light_1；启用备选后必须同步/固定同一动作变体，两端不能分别随机选。武器档位在动作起始锁定；服务端和渲染器用相同档位、变体和 elapsed tick 采样。重击释放若尚未满蓄力，首帧与上一姿态做短视觉过渡，但不要拖延服务端窗口。

数据内所有新事件按 tick 排序。hit_start / hit_end 保留现有权限。`sword_whoosh`、`drink_visual` 等是表现标记，不触发判伤、回血、精力消耗。脚步只在接地且实际移动时播放；取消、跳过 tick、低帧率或循环时按事件跨越区间处理，每个事件只播一次。

装备显隐见 presentation：翻滚/死亡全隐藏，喝药只显药瓶；大剑待机/移动/攻击/蓄力时双手握持、不显示盾牌；防御/弹反临时恢复盾牌。这里隐藏仅是客户端表现，不移动或销毁背包物品，也不新增玩家 equip/unequip 黄金武器事件。身体脚步不应为原版跳跃、下落增加额外攻击招式。

## 音效与粒子

`combat.effects.json` 描述事件来源、接触点、数量、颜色、大小、寿命和预算。命中、盾防、弹反成功、破防粒子以**服务端确认结果**为准；挥空不播命中粒子。guard_hit 的视觉 clip 和实际确认事件不能各播放一次同一个音效。key 为 entityId/actionSequence/eventType/eventTick 去重。不要把普通动画的预测播音当成服务器伤害证据。

木杖 slam 的 impact tick 为 33，黄金锤为 42；接触点来自武器骨骼向地面 raycast，不随意落在实体中心。黄金武器保持不透明、全亮；细小 3 tick 拖尾只在现有伤害窗口出现，不绘制大屏弧线。无血液、全屏闪白或默认震屏。Boss death 在 40/44/48 各 12 个尘粒，40～50 淡出，实体仍按当前 52 tick 清理。淡出要用独立透明渲染通道，不能改变黄金材质或误伤其他实体。

音效表目前只有 14 类原创制作规格、每类计划两变体，没有成品音频。暂时沿用已在 Java 中验证的游戏声音；不要生成指向不存在 OGG 的 sounds.json。后续 Codex 补原创录音/合成，**不提取原作音频**。调音范围与去重规则见 `combat.sound-design.json`。

## 资源同步及已发现的接入问题

1. `build.gradle` 当前 syncArt 只复制 `art/margit/`。请增加玩家目录的 model、skin、animations、presentation、visual-offsets、hit-shapes 和 texture/particle 映射；`.bbmodel`、preview、验证报告不是运行时资源。建议统一放 `assets/eldencombat/models/player/`、`animations/player/`、`textures/entity/player/`、`textures/item/`、`textures/particle/`，加载器使用一致的路径。
2. `SkeletonAssets` 目前只加载 Boss 与黄金件；增加玩家/装备资源入口，服务器采样不能依赖客户端纹理加载。先能读资产，再替换数值胶囊采样。
3. `ModelData.materialForFace` 目前对所有名为 head 的方块应用脸部贴图；黄金锤锤头也叫 head。请把特殊例外限定到 **id=eldencombat:margit 且 cube=head**，或者显式面材质。玩家使用皮肤 sidecar，不走这个例外。
4. 原 Boss staff_slam / hammer_slam 的 impact 在 JSON 中位于 hit_end 后，tick 并非升序。本次没有动这些已验收源文件；请在加载事件时排序，或事件派发遍历全部事件，不对未排序列表提前 break，避免漏播 tick 33/42 的 impact。

## 接入顺序与验收

1. 修正锤头面材质特例；添加资源同步和加载。先显示自己的皮肤、两种臂型与四件装备，检查默认正面和盾牌朝向。
2. 接旋转与 Y 位移、动作映射、装备显隐；从直剑轻击、翻滚、喝药开始，再接大剑与防御。任何身体/装备重复渲染都必须消除。
3. 服务端改为同一骨架采样；调试画刀身轴与上一/当前 tick 胶囊，对比两臂型、左手主手、朝向与动作变体。墙后、一次动作重复伤害、落地重复命中仍需实测。
4. 接粒子与临时音效，最后做 Boss 血条。不得为声光效果改动伤害窗口或加处决入口。
5. 保留现有 95 项自检，新增玩家资产加载、皮肤不被替换、动作窗口、根位移不驱动物理、左右手一致、大剑握持、事件去重和原版渲染退回的测试；再做实际游戏第三人称和第一人称人工验收。

Codex 验证命令：在 art 运行 `node tooling/build-player-assets.mjs`，随后运行 `node tooling/verify-player-preview.cjs <包含playwright和pngjs的node_modules路径>`。构建会覆盖生成文件；修改资产应回写 `art/tooling/player.mjs`、`player-scene.mjs` 或 effects 定义，不只改生成文件。浏览器检查不是游戏内渲染检查，玩家 Java 接入仍待你完成。
