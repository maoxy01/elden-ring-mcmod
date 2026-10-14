# 美术交付报告：逐件武器人物比例

日期：2026-10-12。范围：41 件现有武器/盾牌的比例草案、离线预览、战斗接入交接。

2026-10-13 G-A1：新增原创赐福像素资源 15 张（10 地图状态、3 入口、2 空间贴片），交付 `GRACE-PIXEL-ART-HANDOFF-20261013.md` 与 `art/grace-pixel/assets.json`。已通过 PNG/帧/透明检查和电脑/手机离线预览，私人锁拱局部修订并补 16/32 像素对照；未同步或实机接入。G-A2 补了 Spartan 精确发布包六张 PNG、色板/透明和注册定义取证，见审计第 8 节；拳套是立体例外，修正初审缺口表。旧武器版本选择与备份不变。当前停在可交接状态，不开始完整新装备生产。

最终范围说明：先一把代表只是接入次序；最终仍需 33 普通 + 8 追忆的 41 项 2D 运行映射、来源/许可和逐项验收状态。完整依赖/现有合法 PNG 优先，确切缺口才补画，不从头重制 41 件。新方向的最终默认 2D 展示与旧 3D 保留为过渡/备份分开登记；本轮没有修改任何旧模型或运行映射。

2026-10-13 复用审计已收束至 `ART-REUSE-AUDIT-20261013.md`：建议 Spartan 作普通武器试装底库，Simply Swords 作整模组依赖补充，Soulscraft 暂作造型对照。报告明确 33 类覆盖/缺口、许可限制和赐福最小资源。仅资料/源定义审计，未检查实际 PNG、未试装；不继续新 3D 批次，不修改运行资源。大剑/特大剑仍用旧版。

2026-10-13续作：六件刃器实际资产、24视图新旧对照、六件握区已完成；长弓三关键帧和V形弦控制点已交付数据，尚未接入渲染。详见 `CODEX-BLADES-AND-BOW-HANDOFF-20261013.md`。用户最新要求先核查现成资源，暂停开启后续21件全量自研；本轮收束文件与来源证据，所有成果、原备份和旧大剑/特大剑选择保留。

用户最新选择：大剑和特大剑采用本次精修前的旧版，直剑保留新版。大剑/特大剑的 model、mesh、bbmodel 六个文件已恢复并逐文件验证 SHA256 与备份一致；catalog、比例/握区和预览随之刷新。生成脚本的精修白名单只剩 straight_sword，后续重建不会重套被撤回的两件设计。被撤回版本保存在 `art/backups/large-swords-refinement-rejected-20261012/`。前文“三剑精修 R4”是历史记录，以本段为当前选择。

后续批次更新：矛、中盾、长弓新版实际资产已完成，详见末尾。大剑与特大剑仍保留用户选定旧版。

最新更新：三剑像素精修 R4 的实际资产已经制作并通过网格/连通验证，详见末尾“三剑新版资产”。前文“尚未制作”是当时交付状态，现已由此更新取代。游戏同步、手掌对齐和人工验收仍未完成。

## 已交付

- `art/weapon-proportions/targets.json`：逐件比例编辑源，含具体法环参考武器名称和参考链接。
- `art/weapon-proportions/profiles.json`：当前几何尺寸、目标尺寸、独立倍率、握点和当前 display 缩放的计算结果。
- `art/weapon-proportions/SCALE-TABLE.md`：41 件可读尺寸表。
- `art/weapon-proportions/preview.html`：全部武器、八追忆和六类代表的同尺度人偶对照，支持标准/纤细手臂、转向和握点夹具。
- `docs/CODEX-TO-COMBAT-WEAPON-SCALE-20261012.md`：供用户交给战斗对话的 Markdown，包含渲染接线位置、比例/握点约定、服务端职责和验收要求。

本轮新增文件位于上述新目录、三个 `art/tooling/*weapon-proportions*` 文件和两份报告。未覆盖原模型、网格、图集、玩家皮肤、骨架或旧预览；82 个读取源文件的哈希记录在 `source-manifest.json`。因此无需用备份替换现有源资产。已有扁平化备份继续保留。

## 比例依据

参照采用法环玩家持握版本，不使用 Boss 自身放大后的武器。类型武器明确指定代表：直剑参照 Longsword，大剑参照 Claymore，特大剑参照 Greatsword 等。

神躯化剑已目视检查[原游戏玩家持握图](https://www.gamersky.com/handbook/202204/1477232_56.shtml)。截图中剑约 360 像素，人物可见高度约 394 像素；脚部处于画面边缘，站姿和透视有误差。0.95 倍人高是据此提出的美术目标，不是精确实测。其余 40 件是待原游戏持握图校准的美术初值，未宣称已经逐件测量完成。

项目人偶的可见几何高度实测约 1.8 格；使用 `目标尺寸 = 比例 × 人偶高度`。神躯化剑暂为 1.71 格，直剑 1.08 格，特大剑 2.484 格，蒙格温圣矛 3.15 格。尺寸不同的武器不会为占满画面而单独放大或缩小。

这些是静止主要尺寸：双头剑包括两端，碎星按单把，盾/弓取高度，弩取本地 Y 轴的前后长度，鞭取静止跨度。它们不定义攻击 reach。

## 当前发现

`WeaponDisplayTransforms` 的手持 `0.85 / h` 会把每把武器归一到同样长度；这是游戏中的关键接入点。本轮已在交接中明确要求按逐件比例替换，并计入原版 display 和校正矩阵。只变更模型尺寸无法消除此行为。

原任务包中“共用 builtin/entity parent 导致 41 件外观相同”的推断不成立：现有 `EquipmentRenderer` 按物品获取实际模型与图集。当前武器游戏版图集均为 128×128。不要依据旧文档批量改成 generated parent 而丢失手持自定义渲染。

## 验证

命令：

```powershell
node art/tooling/build-weapon-proportions.mjs
node art/tooling/verify-weapon-proportions.mjs --browser
```

静态检查覆盖 41 个唯一 ID、换算结果、原点、display 倍率补偿、大小关系和 82 个源文件哈希。浏览器检查覆盖 1440×1024 与 390×844，八追忆/六类/全部 41 件无空白单元和页面横向溢出；标准/纤细手臂握点预览各留一张截图。详情以 `art/weapon-proportions/validation.json` 为准。

截图：`remembrance-1440.png`、`featured-1440.png`、`all-1440.png`，对应三个 `*-390.png`，以及 `grip-classic.png`、`grip-slim.png`。预览使用中性灰人偶，未改游戏玩家皮肤。

## 尚未验收

- A0 部分完成：41 件比例草案与预览已交付；40 件参考校准、游戏比例接线和原版可见身高测量尚未完成。
- A1 未进行：物品栏/JEI/第一人称/展示框/掉落物的实际游戏截图、图标改造不包含在本轮。
- A2–A3 未进行：本轮未改战技时序、姿态、VFX、道具图标，未新增护甲、敌人或环境资产。
- A4 未进行：未运行 Gradle、游戏或打包；未验证模组兼容、GPU 性能或多人表现。

网页握点场景是美术夹具，不是 Minecraft 渲染变换或实际动作的复现。`runtimeIntegrated=false`、`inGameAccepted=false` 保持真实。由接入方按战斗工单串行构建并提交实机证据，再更新这些状态。

## 后续尺寸候选与用户反馈

独立交付 `art/weapon-proportions/size-review/`：六类代表共 18 个尺寸候选，当前 / +15% / +30%，人物高度与镜头保持一致；握点检查按浮点容差验证一致。候选不覆盖正式 targets、profiles 或原预览，三个文件哈希复查一致。

`node art/tooling/build-weapon-size-review.mjs --browser` 已通过。截图包括六张逐武器对照、全部候选总览、神躯化剑握点和手机布局。每张单武器图分别检查三个候选的实际武器像素可见，人物像素不作为武器非空证明。

统筹后续转来的用户反馈明确为“所有武器在游戏手持画面偏小，普通武器质量不足”。新工单 `WEAPON-SIZE-AND-QUALITY-REVISION-20261012.md` 将下一批设为直剑、大剑、特大剑、矛、中盾、长弓六件实质精修，审阅后推广其余 27 件。这六件精修尚未制作；现有候选组六类是匕首、直剑、特大剑、矛、中盾、神躯化剑，不应冒称已经包含大剑与长弓的新模型。

最新源码已出现逐件 `heldScale` 消费入口，旧 `0.85/h` 的描述保留为历史发现。源码变化由其他开发完成，本报告不确认当前客户端加载了最新 JAR。`runtimeIntegrated=false` 是本次美术导出未验证接入的状态，不是对其他开发接线成果的否定；实机证据尚缺。

新增握点交付：`sword-grip-regions.json` 与 `docs/CODEX-SWORD-GRIP-HANDOFF-20261012.md`。三类剑在当前模型中均包含原点、柄部中心，内缩可握中心线按 0.25 单位采样连续。运行时手掌对齐尚未验证；三剑钻石剑阶梯轮廓精修尚未制作。

## 三剑新版资产

已实际更新 `art/weapons/{straight_sword,great_sword,colossal_sword}.{model.json,mesh.json,bbmodel}`。三剑增加阶梯收尖、分区刃面、刃槽或脊线、连续护手套接、缠柄与柄头；保持不同剪影、原点和一单位薄片厚度。沿用现有低饱和图集，通过逐面 UV 指向不同材质区组织新贴图，不扩展共享 PNG 或改变其他 30 件资产的 UV。

| 剑种 | 新方块数 | 网格三角形 | 连通分量 |
| --- | ---: | ---: | ---: |
| 直剑 | 90 | 548 | 1 |
| 大剑 | 134 | 808 | 1 |
| 特大剑 | 193 | 1108 | 1 |

备份：`art/backups/swords-before-refinement-20261012/` 保存三剑旧模型、网格、Blockbench、catalog、图集、旧预览和旧 flatten tooling。源定义新增 `art/tooling/sword-pixel-refinement.mjs`，已接入 `flatten-equipment.mjs`，重新生成会保留精修。作用于这三件的独立重建入口是 `apply-sword-refinement.mjs`。

同时更新了 `art/weapons/catalog.json` 与 `preview.html`；重新生成比例包围盒、倍率与 `sword-grip-regions.json`。检查按最新源文件执行，41 件比例预览仍通过。三剑尺寸与各自旧版保持同尺度，质量改进不依赖尺寸放大。

新旧对照：`art/weapon-proportions/sword-review/preview.html` 与 `old-new-front-side.png`。三张 `*-grip.png` 是同握点对照；`validation.json` 记录浏览器结果。网页握点夹具仍不证明游戏手掌已包住柄部。

仅本地美术资产已交付；运行资源同步、JAR 构建、第一/第三人称真实手持与用户观感验收交接入方。矛、中盾、长弓及其余普通装备的精修尚未完成。三剑方向参考用户要求的像素阶梯语言，本轮未宣称已逐像素比对原版钻石剑纹理。

## 矛、中盾、长弓新版实际资产

本批完成 `art/weapons/{spear,shield,long_bow}.{model.json,bbmodel}` 以及矛/中盾的 `.mesh.json`。长弓保留四骨骼模型渲染，不生成根节点静态 mesh。`catalog.json`、全部武器预览、41 件比例数据与 `utility-grip-regions.json` 已刷新。

- 矛：叶形阶梯矛头、明暗刃脊、连续金属套接，木杆与皮革握段分别表达。
- 中盾：圆肩与收尖剪影、深色金属盾缘、正面纹章与铆点；背面使用木板和皮革横带独立 UV。背部握带为贴图表达，没有新增浮空把手。
- 长弓：重新设计回曲弓臂、骨质梢、缠柄与清楚弓弦。`root/bow_upper/bow_lower/string` 的 parent、pivot、rotation 均与旧版完全一致。保持骨骼契约不代表拉弓动作已经实机验证。

| 装备 | 方块 | 网格三角形或理论上限 | 静止连通 |
| --- | ---: | ---: | --- |
| 矛 | 134 | 868，静态 mesh | 1 |
| 中盾 | 100 | 544，静态 mesh | 1 |
| 长弓 | 161 | 1932，全部六面理论上限 | 1 |

备份：`art/backups/spear-shield-bow-before-refinement-20261012/`。源定义：`art/tooling/spear-shield-bow-refinement.mjs`，已接入 flatten tooling。单批重建：`node art/tooling/apply-sword-refinement.mjs --utility`。沿用共享图集，新分区以逐面 UV 实现；没有扩容或重排其他装备贴图。

45 件静态检查通过，包括模型/BB/网格/UV和骨骼一致性。新设计不再要求与旧轮廓边界近似相同；针对本批三件，验证其声明的设计边界与实际几何精确匹配，并检查静止连通分量，其他资产保留旧轮廓门禁。

检查已进一步限定：utility_v4 只允许 `eldencombat:spear/shield/long_bow`，必须为 revision 1、源文件 `art/tooling/spear-shield-bow-refinement.mjs`，声明边界必须匹配验证器中独立固定的三项设计边界。其他 ID 挂此标签会失败；允许 ID 篡改版本、源路径或边界也会失败。其余 30 件类型装备继续原轮廓检查。此门禁结果单独记录，不作为视觉质量或实机验收结论。

对照：`art/weapon-proportions/utility-review/preview.html`、`old-new-front-side.png`、`shield-back.png` 与三张 `*-grip.png`。桌面/手机浏览器检查通过，旧/新在同一目标尺寸下展示，未借助单独放大突出改动。更新后的柄部坐标与哈希见 `utility-grip-regions.json`；材质、骨骼契约和接口需求见 `CODEX-UTILITY-ART-HANDOFF-20261012.md`。

本轮未运行 Gradle 或同步游戏资源，尚缺第一/第三人称手持、掌心贴合、盾背、拉弓与用户观感的实机证据。其余 27 件待统筹复核本批后继续。
