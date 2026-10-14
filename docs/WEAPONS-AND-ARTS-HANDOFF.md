# 武器模型与代码接入方案

日期：2026-10-11。按用户最新分工，Codex 负责建模与方案，游戏代码由用户安排的其他 agent 编写。本文件是建议与交接信息，不是已完成的功能清单。

后续优先批次：已另做 8 件本体追忆武器的 2048 图集精细化第二版。专属战技不可替换，模型新增逐面 UV；详见 [REMEMBRANCE-ASSETS-HANDOFF.md](REMEMBRANCE-ASSETS-HANDOFF.md)。下文仍描述原有 33 件主要类型代表模型，不把两批混称为已接入游戏的 41 件武器。

## 目标与边界

- 先覆盖主要武器类型，不逐件复刻所有具名武器，也不声称覆盖 DLC 全类别。
- 武器采用精细 MC 方块风格：分层刃面与收尖、护手、缠柄、铆钉、金属配件、镶嵌、盾面纹章。不要退回仅改名字的同形模型。
- 保留玩家实际皮肤。玩家动作以 Epic Fight 为底座，扩展武器分类、招式与战技，不恢复旧独立战斗引擎。
- 不做处决、配对终结。用户最新允许战技专用跃起、腾空和落地攻击；普通空中攻击仍可保留，现有踏步砸击可以维持地面招式，不必改成跳攻。
- Boss 暂停。Boss 的粗块风格、简单关节和三节尾巴约束不变；武器的精细度独立提高。

## 美术交付

入口：`D:/codexout/mcmod/art/weapons/preview.html`。离线直接打开，不需服务器。

| 文件 | 用途 |
| --- | --- |
| `art/weapons/catalog.json` | 33 件资产索引、骨骼、握持点、拟配动作类别、默认战技 |
| `art/weapons/*.bbmodel` | 33 个可编辑 Blockbench free 模型，内嵌图集 |
| `art/weapons/*.model.json` | 同一套自定义方块骨架格式，含旋转和材质 UV |
| `art/weapons/weapons.png` | 512 x 512 RGBA 原创程序绘制图集，13 个材质区 |
| `art/weapons/flail.animations.json` | 三节连枷的机械运动草稿 |
| `art/weapons/whip.animations.json` | 三节软鞭的机械运动草稿 |
| `art/weapons/weapon-arts.design.json` | 22 种战技设计及初始调参建议，未实现 |
| `art/weapons/previews/` | 桌面总览、手机和单件审查截图 |
| `art/weapons/preview-validation.json` | 模型引用、图集解码、预览画面和交互检查结果 |

这些是自制的类型代表模型，不是提取原游戏资源。武器与盾牌的握持原点统一为 `[0,0,0]`，16 单位 = 1 方块。主要长度轴是 +Y；盾面在 -Z，盾背在 +Z。弓弩瞄准方向需要单独的手部变换，不可直接按剑的变换使用。

旋转采用 XYZ 欧拉角、度数；机械动作是骨骼初始旋转上的增量。必须先计算父骨骼世界矩阵，再求模型 bounds。左右手用插槽变换处理，不可翻转贴图。

连枷与软鞭各三节活动骨骼，加一个 root；弓保留弓臂和弓弦骨骼，但没有合格的拉弦动画。当前弓弩只交付静态模型，后续需要两端保持连接的弓弦变形。机械草稿不是玩家攻击动画，也不是直接可用的 Epic Fight 动画 JSON。

`secondaryGrip` 是双手动作的初始建议值，仍需在真实玩家身上校准；不代表已经通过 MC 手部对齐测试。`damageParts` 仅是候选几何零件名称，不是现成 collider。

## 武器与默认战技

完整字段以 `catalog.json` 为准；这里的 Epic Fight 分类映射是建议，尤其 `custom:*` 尚未注册。

| 类型 / ID | 动作方向 | 默认战技 |
| --- | --- | --- |
| 匕首 `dagger` | dagger | 迅步 |
| 直剑 `straight_sword` | sword 单手 | 准备架式 |
| 大剑 `great_sword` | greatsword 双手 | 重斩 |
| 特大剑 `colossal_sword` | 重型双手剑 | 踏步砸击 |
| 刺剑 `thrusting_sword` | 自定义单手刺击 | 贯穿 |
| 重刺剑 `heavy_thrusting_sword` | 自定义重刺击 | 突刺 |
| 曲剑 `curved_sword` | sword 横斩 | 回旋斩 |
| 大曲剑 `curved_greatsword` | 双手横斩 | 横扫 |
| 刀 `katana` | uchigatana | 居合 |
| 双头剑 `twinblade` | 自定义双端连击 | 连续斩 |
| 斧 `axe` | axe | 战吼 |
| 大斧 `greataxe` | 自定义重斧 | 重斩 |
| 槌 `hammer` | 自定义单手钝击 | 忍耐 |
| 大槌 `great_hammer` | 自定义双手钝击 | 踏步砸击 |
| 特大武器 `colossal_weapon` | 自定义重钝击 | 踏步砸击 |
| 连枷 `flail` | 自定义链节扫掠 | 旋转连枷 |
| 矛 `spear` | spear | 贯穿 |
| 大矛 `great_spear` | spear 重型 | 突刺 |
| 戟 `halberd` | 自定义刺击 + 横扫 | 横扫 |
| 镰刀 `scythe` | 自定义长柄横扫 | 回旋斩 |
| 软鞭 `whip` | 自定义多段鞭击 | 鞭打 |
| 拳套 `fist` | fist | 连续拳 |
| 钩爪 `claw` | 自定义短距双手 | 迅步 |
| 短弓 `short_bow` | bow | 连续射击 |
| 长弓 `long_bow` | bow | 强力射击 |
| 大弓 `great_bow` | 重弓 | 强力射击 |
| 弩 `crossbow` | crossbow | 重弩射击 |
| 弩炮 `ballista` | 自定义重弩 | 重弩射击 |
| 辉石杖 `glintstone_staff` | 自定义施法 | 辉石弹 |
| 圣印记 `sacred_seal` | 自定义施法 | 恢复祷告 |
| 小盾 `small_shield` | shield | 弹反 |
| 中盾 `shield` | shield | 弹反 |
| 大盾 `great_shield` | shield 重型 | 盾击 |

## 代码大致结构

建议沿用当前注册器和 Epic Fight 接口，不整套重写玩家渲染与联网。

1. **物品与模型索引**：一个 `WeaponDefinition` 描述 item ID、分类、攻击属性、握持点、模型、材质、招式和战技兼容组。注册物品、中文名、创造标签与 JEI 展示。33 件不能都继承普通 SwordItem；弓、弩、盾、法器有独立使用逻辑。
2. **Epic Fight 动作适配**：在现有 `EpicFightIntegration` 附近扩展 `ItemPresetRegister` 和 `MovesetRegister`。有相近招式的类型继承再调整；双头剑、连枷、软鞭、法器需要自定义动作，不允许全部映射成直剑。
3. **战技与装配**：`WeaponArtDefinition` 管兼容组、FP、精力、冷却、分段时窗和伤害参数；一个自定义 `WeaponInnateSkill` 通过物品实例上的战技组件选择实现。先做默认战技，再做赐福/工作台式的战灰替换界面。不要把每把武器永久写死成无法换战技。
4. **资源与状态**：新增独立 FP，不冒用 Epic Fight 精力当 FP。精力仍由 Epic Fight 管。服务端校验武器、资源、地面条件与冷却后原子扣费；同一次请求不能重复消费。使用 `castId + startTick + phase` 跟踪状态，向本人及追踪玩家同步。
5. **表现与判伤**：玩家动画由 Epic Fight 同步；模型附在真实手部插槽。优先使用 EF 攻击阶段和 collider，每个命中只能结算一次。不要同时开启旧 `ServerCombat` 判伤和 EF 判伤。

### 可核对的本地接口

锁定 MC 1.21.1 / NeoForge 21.1.219 / Java 21 / Epic Fight 21.17.3.1，不要照搬其他大版本示例。

- 现有适配入口：`src/main/java/com/dsh/eldencombat/compat/EpicFightIntegration.java`。
- 本地解包源码：`build/epicfight-sources/yesman/epicfight/`，依赖缓存不是需要打包的项目源码。
- `Moveset.builder().parent(...).replaceComboAttacks(...)` 已在适配器中使用。
- 本地 `Moveset.Builder.addInnateSkill(BiFunction<ItemStack, PlayerPatch<?>, Skill>)` 可用于按物品实例选战技。
- `Player.CAST_SKILL`、`Player.COMBO_ATTACK` 和 `Entity.DELIVER_DAMAGE_*` 可用于权限、动作和确认后的伤害反馈。伤害逻辑必须在服务端执行。
- `EpicFightDamageSource.attachDamageModifier`、`attachImpactModifier`、`attachArmorNegationModifier` 是本地版本的方法名。

重要陷阱：`SimpleWeaponInnateSkill.registerPropertiesToAnimation()` 会把属性写入其引用的共享动画 Phase。不要给多个战技和普通攻击复用同一动画对象后分别改伤害，那会污染普通攻击。选独立注册的攻击动画，或严格按施放实例在服务端修改当前 damage source；切换物品/取消时立即清理。

### 战技规则

`weapon-arts.design.json` 中 FP、时长、倍率是原型建议，不是法环原作精确数据，也不是游戏当前参数。兼容表可先使用上面的默认配对，后续扩展组内换装。

- 架式类：服务端区分准备、追加入力窗口、释放和收招，不是按一次键立即全额伤害。
- 多段类：每段独立命中集合；一段内对同一目标最多结算一次。追击请求只在合法时窗接受。
- 迅步/突刺：服务端碰撞约束位移，不穿墙，不冻结原版下落。
- 战吼/忍耐：有期限、不能无限叠加；忍耐是减伤与抗打断，不是无敌。
- 连枷/鞭：按骨架与真实球头/鞭段扫掠；使用分段胶囊或 OBB、tick 内插值和遮挡测试。不能用一个巨大实体 AABB 全向打人。
- 震波：有限半径和遮挡检测，明确主击与震波对同目标是否合并，防止误双倍伤害。
- 弹反：正面时窗与可弹反伤害分类；成功只造成硬直/削韧，不开放处决。
- 弓弩：继承或复用原版 BowItem/CrossbowItem 装填、弹药和投射物逻辑。连续射击逐箭扣弹；强力箭消耗一支箭；重弩战技不能凭空装填。弩的 CHARGED_PROJECTILES 状态以服务端为准。
- 法器：辉石弹必须是有碰撞与生命周期的真实投射物；恢复祷告引导可被打断，只治疗自身或合法同队目标。
- 切武器、死亡、换世界、退出、受击中断时清理施放、连击缓冲与临时倍率。明确已扣 FP 的退款策略，默认成功开始后不退，服务器拒绝则不扣。

## 资源接入的必要改动

当前生成资产尚未进入游戏，下面是代码 agent 的工作，不是本批已经完成的修改。

- `build.gradle` 的 `syncArt` 目前只同步 `art/margit` 与 `art/player`。新增精细武器同步：模型放 `assets/eldencombat/skeletons/items/`，机械动画放 `animations/items/`，图集放 `textures/item/weapons.png`。更新任务输入版本。
- 自定义 `.model.json` 不能直接放原版 `models/item`，也不能假装是 EF 玩家动画文件。原版物品 JSON 只引用正确的自定义渲染入口与展示变换。
- `PlayerAssets` 目前硬编码加载直剑、大剑、盾、药瓶。扩展或独立建立物品模型索引，不让服务端装载客户端渲染类。
- `EquipmentRenderer.asset()` 目前只有剑/大剑/盾标签回退，会把不同种类画成同一件。先按注册 ID 选精细模型，再考虑旧标签兼容；武器使用新图集，药瓶继续用旧图集。
- EF 某些物品 preset 可能提供自己的 renderer，需确认不会覆盖自制模型。验收 GUI、地面掉落、第一/第三人称、左右手、双手握持和盔甲，而不是只看背包名称。
- 支持资源重载并清缓存。预览使用了 512 图集，不能继续把所有新武器的 UV 除以旧的 64。
- 不覆盖玩家已经学习的翻滚/防御技能；仅填空槽。给战技合理的中文 tooltip、资源不足提示和冷却显示。

## 建议实现顺序与验收

1. 接入 33 个物品与模型，先检查直剑、刀、矛、弓、盾的左右手和视角。已有同 ID 直剑/大剑/盾要替换资产，不重复注册。
2. 做各类型普通攻击和命中形状；验证轻重武器速度差异与长柄范围。用户已反馈动作太慢，不要用冗长收招占位。
3. 做 FP、服务器施放状态和默认战技。先以准备架式、迅步、贯穿、居合验证系统，再扩展其他近战和防御战技。
4. 接入远程弹药、装填、投射物与法器。完善战灰兼容和装配界面。
5. 完成武器专用真实 C2S 验收与多人同步，再制作新整合包；不要让 Boss 测试阻止武器验收。

通过标准：正常攻击和战技让真实目标扣血；FP/精力/弹药准确消耗且只消耗一次；无资源、乱序、重复请求、换物品都被正确拒绝；墙后不命中；取消后不残留倍率；没有处决。允许跳跃战技，但要验证服务器轨迹、碰撞、落地只结算一次、取消后重力恢复。测试常规/细臂皮肤、左右手、盔甲、第一/第三人称、两名玩家和独立服务端，发布 JAR 不包含测试 harness。

预览检查只证明美术文件能解析、贴图能显示、模型可旋转且机械草稿会运动，不证明上述游戏验收通过。

## 工程现状与整合包

更早的用户授权下已有一批 EF 迁移代码修改，保留原样供接续，不把它们说成已完成的武器版本。本批分工调整后没有继续修改游戏 Java。

- 更早的编译与离线测试通过，包括共享 420 项检查和 Boss 方向几何测试；这些不是 33 类武器/22 种战技的功能测试。
- EF 加七个指定模组的开发客户端曾成功启动，但最新 Boss 生存伤害 smoke 测试失败。Boss 未验收且现在暂停；不依赖其旧测试证明武器可用。
- `ClientSmokeHarness` 对应旧独立引擎；`EpicFightSmokeHarness` 当前是 Boss 优先并失败的流程。请新增独立的武器验收场景，不能把 Gradle 退出码 0 当作游戏日志 PASS。
- `dist` 现有成品是历史 0.1.0；没有已验收的 0.2.0 整合包。本批精细模型也没有打进任何发布 JAR。

继续使用 `modpack/mods.lock.json` 已锁定的开发组合，不自动追最新版：Epic Fight 21.17.3.1、Sodium 0.6.13、Iris 1.8.12、Entity Culling 1.11.3、Cloth Config 15.0.140、FerriteCore 7.0.3、Lithium 0.15.4、JEI 19.27.0.340。缓存里另有更新 JEI，不能误装到这个锁定组合。

完整整合包验收还需 PCL 实际导入、客户端和服务端兼容测试；没有交付地图或光影包。第三方依赖按现有官方下载清单与来源记录处理，不把解包源码或第三方动画资产混入美术交付。
