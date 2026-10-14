# 美术复用审计与下一步选型

日期：2026-10-13。范围：装备美术来源、类别覆盖、许可边界、最小赐福/奖励资源。不是安装报告，不修改战斗代码、包锁或运行资源。

当日补证：Spartan 精确发布 JAR 的六张代表 PNG、父模型、配方 ID 与注册定义字节码已核查，见第 8 节；不是全模组验收。赐福原创 15 项已交付 `GRACE-PIXEL-ART-HANDOFF-20261013.md`，尚未接入。

## 1. 结论

1. **普通武器底库优先试用 Spartan Weaponry Unofficial**。已核查的剑、矛、匕首、弓弩资源采用原版薄片物品路径，并按类型设置不同尺寸与握持变换；不能再统一归一为同样长度。
2. **Simply Swords 作为可选依赖补充**，优先评估双头剑等缺口。不因为内容多而默认同时安装两套武器系统；不抽取其贴图、模型、音效或效果图标重新装进自研 JAR。
3. **Soulscraft Tarnished Legacy 暂不作为默认美术底库**。官方主打完整 3D 武器/护甲，与当前原版 2D 像素方向不一致；可作法环类别和造型对照，不是可自由复制的素材库。
4. 先完成“一把普通武器适配”和赐福原型，不先补齐全部 33 类。美术仅补经过审计仍缺少且进入里程碑的资源。

最终装备交付范围仍是 **33 类普通装备 + 8 件追忆，共 41 项**：逐项登记 2D 运行映射、具体来源/许可、可复用或需补画的缺口，以及同步/实机验收状态。一把代表仅是先接入的顺序，不是缩减最终范围。优先完整依赖与现有合法 PNG，不从头重制 41 件。最终默认展示遵循用户已确认的新 2D 方向；旧 3D 保留为过渡对照与可恢复备份，旧大剑/特大剑也可用于自有像素剪影参考，不在本轮覆盖。

以上是隔离试装建议，不代表已安装、已获用户视觉验收或已通过游戏兼容测试。当前 MC 1.21.1 / NeoForge 21.1.219 / Java 21 工程及已有八战技、FP、强化、质变继续保留。附件的“工程尚未启动”不能覆盖现有代码证据。

**旧成果保留：大剑、特大剑使用用户选定旧版，直剑使用精修版；全部现有模型与备份不删除、不批量覆盖。** 停止新 3D 精修批次。正式运行时切换由战斗部门串行接入。

## 2. 来源与许可

| 候选 | 目标文件与来源 | 美术使用边界 | 当前选择 |
| --- | --- | --- | --- |
| Spartan Weaponry Unofficial | [1.21.1-1.2.3 文件页](https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry-unofficial/files/8717810)、[作者仓库](https://github.com/Mai-xiyu/SpartanWeaponry-NeoForge) | 项目声明 Apache-2.0；改作/分发应保留许可、署名及适用通知，并标记修改。提取单项前仍须核查该项独立来源与第三方例外 | 优先整模组依赖试用；允许适配，暂未批准任何素材抽取 |
| Simply Swords | [1.70.2 NeoForge 1.21.1 文件页](https://www.curseforge.com/minecraft/mc-mods/simply-swords/files/8746001)、[作者仓库](https://github.com/Sweenus/SimplySwords)、[许可全文](https://www.curseforge.com/minecraft/mc-mods/simply-swords/license) | Timefall Development License 1.2；整合包可通过官方平台清单下载。直接打包到 overrides、复制资源再发布不能据此放行；音效和部分效果图标另有第三方限制 | 可选整模组依赖；不当战灰/赐福/VFX 素材库 |
| Soulscraft Tarnished Legacy | [官方项目页](https://www.curseforge.com/minecraft/mc-mods/soulscraft-tarnished-legacy)，NeoForge 1.21.1 / 1.2.1 | ARR；页面列 GeckoLib、Architectury API，未取得可复制资源许可 | 仅作造型/覆盖参考；不选默认 2D 路径 |
| Dungeons and Taverns | [v4.4.4 数据包](https://modrinth.com/datapack/dungeons-and-taverns/version/v4.4.4)，标记 1.21–1.21.1 | ARR；若选用需按允许方式使用完整包，不能拆出 NBT 作为自有素材重新分发 | 小型遗迹候选，结构/奖励兼容由战斗部门验证 |
| Lootr | [许可全文](https://www.curseforge.com/minecraft/mc-mods/lootr/license) | 代码 MIT，但全部美术资产 ARR；不能因 MIT 标签复制宝箱贴图 | 复用模组功能，不抽取其美术 |

本轮仅公开资料与资源定义审计。未下载第三方模型/贴图到运行目录，未进行素材包准入、安装、分发或构建。公共仓库可读不等于可重新发布。

## 3. 证据等级

| 检查项 | Spartan | Simply Swords | Soulscraft |
| --- | --- | --- | --- |
| 官方项目页、目标加载器和许可声明 | 已查 | 已查 | 已查 |
| 作者源树与若干物品 JSON 定义 | 已查，固定提交见下文 | 已查，固定提交见下文 | 未查到可核对源树 |
| 实际 PNG 的分辨率、色板、透明握柄区域 | 6 张发布包代表已检查；实机握持未验 | 未检查 | 未检查 |
| 发布 JAR 与源提交一致、完整依赖/资源署名 | 发布包绑定哈希；源提交一致性及完整例外仍待核对 | 待核对 | 待核对 |
| 本工程第一/第三人称、左右手、细臂实机 | 未验证 | 未验证 | 未验证 |

初审只判断抽样资源的渲染路径和变换；后续第 8 节补了实际像素取证。**目前仍不能判断是否实机握空气或是否与原法环比例一致**。官方图链接未成功取得可审查图像，不把页面描述当作逐像素验收。

### Spartan：固定源提交与抽样

分支 `1.21.1`，提交 `af0914b736b70545f88519ad45e9f24230cb0ab5`。核对 [固定提交 LICENSE](https://github.com/Mai-xiyu/SpartanWeaponry-NeoForge/blob/af0914b736b70545f88519ad45e9f24230cb0ab5/LICENSE) 与完整、未截断源树。

基类位于 `src/main/resources/assets/spartan_weaponry_unofficial/models/item/base/`，物品定义位于 `src/generated/resources/assets/spartan_weaponry_unofficial/models/item/`。抽样值：

| 基类 | parent / 显式 elements | 第三人称 scale | 第一人称 scale | 观察 |
| --- | --- | --- | --- | --- |
| dagger | item/handheld / 无 | 0.85, 0.85, 0.85 | 0.68, 0.68, 0.68 | 匕首较小，独立握持变换 |
| greatsword | item/handheld / 无 | 1.7, 1.7, 0.85 | 0.95, 0.95, 0.425 | 大型剑薄片，不是匕首同倍率 |
| spear | item/handheld / 无 | 2.125, 2.125, 1 | 0.85, 0.85, 0.425 | 长杆有独立旋转与平移 |
| longbow | item/generated / 无 | 1.2, 1.2, 1.2 | 本轮不作为握持结论 | iron_strengthened 物品定义有 3 个状态 overrides |
| heavy_crossbow | item/generated / 无 | 0.9, 0.9, 0.9 | 本轮不作为握持结论 | iron_strengthened 物品定义有 5 个状态 overrides |

`aluminum_dagger`、`aluminum_greatsword`、`aluminum_spear` 的物品定义继承上述基类，另有涂层与动作状态层/覆盖。这里的“2D”是原版精灵生成的薄片模型，并非屏幕贴图，也不代表整个项目所有物品均没有复杂模型。

### Simply Swords：固定源提交与抽样

分支 `Architectury-1.21`，提交 `708a57bc591e0af75ae0c5ec6beb0d8ff22f67af`。核对 [固定提交 LICENSE](https://github.com/Sweenus/SimplySwords/blob/708a57bc591e0af75ae0c5ec6beb0d8ff22f67af/LICENSE) 与完整、未截断源树。

目录 `common/src/main/resources/assets/simplyswords/models/item/`。`diamond_longsword`、`diamond_katana`、`diamond_twinblade` 对应模板继承 `minecraft:item/generated`，没有显式 elements。三类第三人称 scale 为 `1.7,1.7,0.85`，但 Y 平移分别是 `6`、`6.5`、`-2.5`；双头剑不能套单刃剑握区。

另有 `big_handheld` 与 `long_handheld` 模板，第三人称 scale 分别为 `1.75,1.75,0.85` 和 `2.55,2.55,0.85`，左右手平移也有差别。接入应先保留提供方模板，而不是复制一份通用 display 给所有装备。官方另列的 Classic/Legacy 3D 模型不是本轮默认选择。

## 4. 33 类普通装备覆盖与缺口

下列名称是已核查源树中的 **资源文件 basename**，不是已验证的注册物品 ID。SW = Spartan，SS = Simply Swords。“近似”只表示可试作轮廓/类别候选，不表示法环造型、尺寸或战斗语义完全一致。

| 自有类别 | 可试用来源 | 判断 |
| --- | --- | --- |
| dagger | SW iron_dagger | 已找到对应类别资源 |
| straight_sword | 原版 iron_sword；SW iron_longsword | 原版可先试用；长剑近似 |
| great_sword | SS iron_claymore；SW iron_longsword | 近似，需实机确认类别与尺寸 |
| colossal_sword | SW iron_greatsword | 大型剑候选，不等于已复刻特大剑 |
| thrusting_sword | SW / SS iron_rapier | 已找到对应类别资源 |
| heavy_thrusting_sword | 无确认候选 | 缺口，不能把普通刺剑仅改名算覆盖 |
| curved_sword | SW iron_saber；SS iron_cutlass | 近似弯刀候选 |
| curved_greatsword | 无确认候选 | 缺口，claymore 不是大曲剑 |
| katana | SW / SS iron_katana | 已找到对应类别资源 |
| twinblade | SS iron_twinblade | SS 的明确补充项 |
| axe | 原版 iron_axe | 可先复用，战斗属性另适配 |
| greataxe | SW iron_battleaxe；SS iron_greataxe | 类别候选 |
| hammer | SW iron_flanged_mace / iron_warhammer | 近似，钉锤不能宣称造型完全相同 |
| great_hammer | SW iron_battle_hammer；SS iron_greathammer | 类别候选 |
| colossal_weapon | 大锤资源可作代理 | 近似/缺口，不证明有独立特大型武器类别 |
| flail | 无确认候选 | 缺口 |
| spear | SW / SS iron_spear | 已找到对应类别资源 |
| great_spear | SW iron_lance / iron_pike | 近似，大矛握持与语义待验证 |
| halberd | SW / SS iron_halberd | 已找到对应类别资源 |
| scythe | SW / SS iron_scythe | 已找到对应类别资源 |
| whip | 无确认候选 | 缺口 |
| fist | SW cestus / studded_cestus | 已查到拳套候选，但 cestus 为六块立体模型；严格薄片仍是缺口 |
| claw | 无确认候选 | 缺口 |
| short_bow | 原版 bow | 近似，原版未区分法环短弓类别 |
| long_bow | SW longbow_iron_strengthened | 资源及拉弓状态定义已查 |
| great_bow | 无确认候选 | 缺口 |
| crossbow | 原版 crossbow | 可复用，装填/攻击语义另验证 |
| ballista | SW heavy_crossbow_iron_strengthened | 近似，重弩不能直接算弩炮 |
| glintstone_staff | 无确认候选 | 缺口；SW quarterstaff 是近战棍，不是法杖 |
| sacred_seal | 无确认候选 | 缺口 |
| small_shield | 无确认候选 | 缺口 |
| shield | 原版 shield | 可沿用原版，但它是原版立体盾；严格薄片要求尚未覆盖 |
| great_shield | 无确认候选 | 缺口 |

初次按类别名搜索未找到 SW 对应盾、鞭、连枷、爪、弩炮路径；拳后来按 cestus 同义词查到，已更正。SS 未找到对应弓弩、盾、鞭、拳爪、法杖、圣印路径。这是有界路径审计，不是对未来版本或其他独立模组的否定。

普通类别表不等于八把追忆武器已被替代。特殊造型保留现有自制来源；未来若转像素图标，仅按进入玩法闭环的实际物品逐件制作。护甲本轮没有足够源图/部位证据，不宣布已覆盖。

## 5. 最小新增美术，不先造大全套

现有 `src/main/resources/assets/eldencombat/textures/item/` 已有 smithing_stone_1..8、somber_smithing_stone_1..5、古龙锻造石、质变模板、护符和 starlight_shard 等。优先复用；质变模板不冒充战灰。未找到已交付的赐福、memory_stone 或通用战灰图标。

| 资源 | 首轮处理 | 开画前需要的接口 |
| --- | --- | --- |
| 灰色 UI | 沿用原版控件、字体及布局；不复制第三方面板 PNG | 页面入口与控件尺寸 |
| 赐福/地图标识 | 暂定 16×16，古代节点与玩家节点各一基础符号；状态用少量覆盖标记 | 未激活/已发现/公共/私人/失效的真实状态枚举、锚点与地图 API |
| 赐福物品图标 | 仅自有节点物品确需外观时新增；采用现成节点则先检查能否原样使用 | 实际物品 ID，是否允许资源覆盖 |
| 第一份战灰 | 一个 16×16 或 32×32 透明图标；物品与技能选择器能共用则共用 | 已选奖励与技能 ID、兼容武器类别 |
| 锻造石/材料 | 复用现有自有 PNG，不重新绘制全套 | 奖励物品 ID |
| 线索 | 先用原版纸/书；需要辨识特定线索时再补一个标记 | 实际线索事件与物品类型 |
| 记忆石 | 延后至记忆槽进入里程碑，不列为双赐福原型必需项 | 槽位/解锁机制确定 |
| 遗迹外观 | 先筛选现成小结构；不拆未授权 NBT，不新建华丽赐福建筑 | 选定结构与允许的集成方式 |

新增资源交付透明 PNG、可编辑源、帧尺寸/顺序、锚点、物品/技能映射、原创/来源说明和哈希。状态必须由战斗数据驱动，不能由贴图猜测或本地缓存授予旅行权限。初审时未开画；后续 G-A1 工单已明确状态语义并交付原创资源草案，最终 DTO 和实机接入仍由战斗确认。

## 6. 给战斗部门的接入边界

1. 先用一把原版剑与一把 SW 武器验证 ID/标签适配、FP/强化/战灰绑定。第三方物品保留原有模型与 display，不全部转成自有 Item 复制品。
2. 隔离试装再扩到匕首、大型剑、矛、长弓、弩等代表；目标发布 JAR、依赖、哈希和资源例外许可由接入方登记。上述提交不是发布 JAR 字节一致性证明。
3. 薄片/generated 物品不能直接套现有自有 3D 网格的尺寸补偿和握区。检查自定义渲染是否拦截外部物品；不得统一拟合长度，也不得依靠把所有武器缩小来解决遮挡。
4. 用真实游戏截图验第一/第三人称、左/右手、标准/纤细手臂，补物品栏/JEI/掉落物/展示框、附魔光效、弓拉弦与弩装填检查。源 JSON 不能替代实机掌柄接触与尺寸验收。
5. 提供方自带能力可能与 FP、输入或属性叠加。战斗部门确定单一施法与扣费权威，避免原能力和自有战灰同时触发；本报告不替代其 API/兼容审计。
6. 为每次切换保留旧资产和映射，选定旧大剑/特大剑不因重建被覆盖。不要沿用旧 3D 哈希的握持证明给第三方 2D 资源背书。

## 7. 本次完成与未完成

已完成：候选初选、固定源定义抽样、33 类覆盖/缺口表、许可边界、现有图标复用检查、最小新增美术清单。

初审未完成：目标 JAR 完整核验、实际像素图像审查、第三方安装/适配、赐福图标制作、游戏视觉验收、性能比较。后续仅补了 Spartan 六张像素取证与 G-A1 原创草案；完整核验、第三方安装、实机验收仍未完成。没有运行 Gradle、启动游戏或修改运行资源。

下一步：统筹选择最小试装组合；战斗完成一个外部物品适配与赐福状态接口；美术根据实机截图确认像素风格和握持后，再补一套必要的节点/首份奖励图标。其余缺口排队，不先开启另一轮全量自制。

## 8. Spartan 发布包像素补证

研究源为已归档精确 JAR `docs/research-evidence/reuse-audit-20261013/spartan_weaponry_unofficial-1.2.3-1.21.1.jar`，SHA256 `84d3ddafb519e2f5e03f2681ff52c30c92c9512181f61a7a7487399a0179888f`。绑定 [官方文件页](https://www.curseforge.com/minecraft/mc-mods/spartan-weaponry-unofficial/files/8717810)，不声称其与前文源提交逐字节一致。

输出仅 `docs/research-evidence/spartan-art-20261013/`：六张未修改原 PNG、`evidence.json` 父模型/配方定义/贴图哈希、`pixel-stats.json`、三份注册字节码与只供研究的预览。署名保留 ObliviousSpartan（原作者）、xwerffx（多数美术）、Mai_xiyu（移植）。本目录不是运行资产包，不能直接复制到 src 或随自研资源重新发布；独立例外仍未放行。

| 已查物品 ID（namespace 均为 spartan_weaponry_unofficial） | PNG | 非透明颜色数 | 透明像素数 | 父模型路径 |
| --- | --- | --- | --- | --- |
| iron_dagger | 16×16 | 10 | 198/256 | handheld 薄片 |
| iron_greatsword | 32×32 | 10 | 850/1024 | handheld 薄片 |
| iron_spear | 32×32 | 14 | 911/1024 | handheld 薄片 |
| longbow_iron_strengthened | 16×16 | 11 | 180/256 | generated 薄片 |
| heavy_crossbow_iron_strengthened | 16×16 | 13 | 134/256 | generated 薄片 |
| cestus | 16×16 UV 图 | 77 | 26/256 | base/cestus，6 个 elements；不是精灵薄片 |

六张 alpha 仅 0/255，无边缘半透明。已目视检查原 PNG 的 nearest 放大预览：前三件刃器有连接的刃根/护手/柄轮廓，剑柄在斜向图的左下方，不能按贴图中心握持；矛柄连续至底端。长弓和重弩用低色阶木/金属像素轮廓。大型剑/矛采用 32 像素保持长度细节，并非所有装备都应统一 16 像素。颜色数是技术统计，不直接等于好看或人眼可读性。

拳套贴图是立体模型的 UV 区块，77 色与大面积填充不能当作一个武器精灵判断风格。这也说明不能根据部分 generated/handheld 样本概括整个模组。

注册定义证据：ModItems 创建 `spartan_weaponry_unofficial` 的物品 DeferredRegister，指定 dagger/greatsword/spear 分组；WeaponItemsMelee 的铁材质名称拼接 `iron_<type>` 并 register。远程分组指定 longbow/heavy_crossbow，WeaponItemsRanged 在这两类添加 `_strengthened`，铁材质拼接 `<type>_iron<suffix>` 并 register；cestus 是 ModItems 的直接 register。六个配方 result.id 与模型名称对应。**这些证明发布包定义的注册名称，不证明当前运行注册表已包含它们**，配置禁用/兼容和实机 `/give` 仍需战斗验证。

结论不变：先试用该模组本体的普通武器，不抽取或改绘其素材。柄部透明区已在原图检查，真实手掌位置、左右手、细臂、弓弩动作和法环人物比例依然未实机验证。法杖、圣印、严格薄片盾仍未找到已批准同版本替代，后续继续按确切需求筛选现成内容；本轮到此收束，不全量从头画。
