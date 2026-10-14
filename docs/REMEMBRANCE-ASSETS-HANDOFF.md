# 追忆武器美术接入补充

日期：2026-10-11。用户要求优先制作神躯化剑等追忆武器，并提高建模像素与细节。Codex 只交付美术和方案，不修改游戏 Java。

## 本批范围

8 件本体追忆武器精细化第二版：神躯化剑、玛莉卡的石槌、玛利喀斯的黑剑、亵渎圣剑、玛莲妮亚的义手刀、碎星大剑、蒙格温圣矛、蒙葛特咒剑。名单与兑换类别参考 [追忆奖励表](https://game8.co/games/Elden-Ring/archives/370962)，外观参考 [武器图鉴](https://www.altchar.com/guides/elden-ring-list-of-all-boss-weapons-remembrance-weapons-aSxMH1L2z6M2)。

这不是全部追忆奖励。未制作拉塔恩的狮子大弓、卡利亚权杖、王斧等其余本体追忆装备，也不包含 DLC。不要把 8 件初版写成“法环全部追忆武器已完成”。轮廓与纹饰是根据参考自行重建的 MC 风格版本，不声称逐顶点一致。

原有总体接入顺序见 [WEAPONS-AND-ARTS-HANDOFF.md](WEAPONS-AND-ARTS-HANDOFF.md)。本文件补充专属武器与新材质格式的差异。

最新美术 profile：精细源版已备份，新增 `art/remembrance-game/` 游戏减面版。游戏默认接入改用游戏版模型、同目录图集与预烘焙网格，ID 不变；不能混用源图集。具体路径、单位、网格契约与验收边界见 [GAME-WEAPONS-ASSETS-HANDOFF.md](GAME-WEAPONS-ASSETS-HANDOFF.md)。下文高细节数量与图集布局描述的是精细编辑源，不是游戏版预算。

## 资产位置与格式

`D:/codexout/mcmod/art/remembrance/`：

- `preview.html`：离线自包含模型审查。
- `catalog.json`：8 个注册 ID、模型路径、材质、握持点、专属战技和双持标记。
- `*.model.json` / `*.bbmodel`：游戏自定义方块骨架数据 / 可编辑 Blockbench free 模型。
- `weapons.png`：2048 x 2048 共用原创图集，16 个材质区各 512 x 512；重新程序生成细颗粒、拉丝、矿物、筋膜等纹理，没有下载原游戏贴图。
- `weapon-arts.design.json`：8 个专属战技的描述与限制，全部未编码。
- `preview-validation.json`、`previews/`：浏览器与文件验收，不是 MC 验收。

单位、握持约定不变：16 单位 = 1 方块，原点 `[0,0,0]`，主要长度轴 +Y，XYZ 欧拉旋转，度数。骨骼只有一个 root；细节由分数单位几何实现，不新增无意义关节。`secondaryGrip` 仍是未在真实玩家身上校准的建议值。

### 必须支持逐面 UV

本批不再把整个材质区重复铺到每个细小方块。模型新增：

```json
{
  "textureSize": [2048, 2048],
  "texelDensityPerUnit": 7,
  "faceUVs": {
    "cube_name": {
      "north": [0, 0, 1, 1],
      "south": [0, 0, 1, 1],
      "east": [0, 0, 1, 1],
      "west": [0, 0, 1, 1],
      "up": [0, 0, 1, 1],
      "down": [0, 0, 1, 1]
    }
  }
}
```

上面 UV 数值仅是结构示例，实际矩形在资产中，单位是图集像素，可包含小数。读取优先级：`faceUVs[cube.name][face]`，不存在再回退 `materials[cube.material].uv`。按 `textureSize` 两轴归一化，不能硬编码 64、512 或旧 1024。窄面做纹理边缘内缩时，内缩量不得超过矩形宽/高的一半。

BB 文件的六面 UV 已与该字段一致。参考实现只在美术工具 `art/tooling/player-scene.mjs` 与 `build-weapons.mjs`，不是已修改的 Java loader。不要误认为旧 `ModelData` 或 `EquipmentRenderer` 已支持这个字段。

`batchPreview` 是预览合并网格提示，不是必须实现的游戏字段。离线预览把同一骨骼的静态方块合成网格，避免数千小方块各发一次 draw call；BB 和 JSON 仍保留每一块可编辑几何。游戏端可以类似烘焙，但需维持插槽、材质和 UV，并实测性能。

第二版每件几何量均超过上一版 4 倍，比较结果见 `validation.json` 的 `comparison`，是实际新增倒角截面、圆形缠柄、雕纹、掌指、铆钉和裂纹，不只换大贴图。源模型为数千至万余部件的高细节编辑版，不能照搬逐块 draw call。游戏端烘焙静态顶点并合批，剔除内部不可见面；远距离/掉落物/GUI 使用经对比验收的 LOD。没有已测游戏帧率、轻量版或现成 LOD 资产，不能把浏览器通过当作性能承诺。

本次浏览器审查修正：四把有断缝的剑补上 `structural_tang_*`、`guard_socket_*` 实体，神躯化剑改用连续刃芯与渐隐双股扭纹，不再拼接一个独立直刃尖。烘焙或 LOD 不能删除连接体导致断缝复发。`structuralChecks` 和 `profileChecks` 是美术验收元数据，游戏不必执行这些采样；检查工具为 `art/tooling/weapon-structure.mjs`。新版 `preview-validation.json` 包含采样数量和截面变化值，这不等于全模型拓扑或游戏性能验收。

后续连接造型修订已把黑剑、亵渎圣剑、碎星大剑与蒙葛特咒剑的刃根下移，宽肩嵌入宽护手座，避免外露细柱托起剑体。保持这一刃根/护手/柄端关系，不能在烘焙简化时退回仅连通但不合理的细接头。正面、侧面、柄部近看和用户造型审查仍是必要验收，不由几何数量或有限采样替代。

## 独立物品与专属战技

| 注册 ID 后缀 | EF 动作方向 | 专属战技 ID 后缀 |
| --- | --- | --- |
| `sacred_relic_sword` | 双手大剑 | `wave_of_gold` |
| `marikas_hammer` | 单手钝击，独立槌 collider | `gold_breaker` |
| `malikeths_black_blade` | 特大剑，重型招式 | `destined_death` |
| `blasphemous_blade` | 双手大剑 | `takers_flames` |
| `hand_of_malenia` | uchigatana，长刀 collider | `waterfowl_dance` |
| `starscourge_greatsword` | 自定义对剑 | `starcaller_cry` |
| `mohgwyns_sacred_spear` | 大矛 | `bloodboon_ritual` |
| `morgotts_cursed_sword` | 大曲剑 | `cursed_blood_slice` |

命名空间均为 `eldencombat`。preset 是拟议分类，不是已注册能力；优先从当前 EF 注册器扩展，不恢复旧独立引擎。

与普通类型模型不同，这批的 `replaceableArt=false` / 战技 `replaceable=false`，不进入普通战灰替换列表。专属战技应按物品固定，而不是拿普通重斩冒充黄金波动。普通类型武器的可替换战灰方案仍保留。

### 碎星大剑双持

单柄模型只有一个握持原点。预览使用左右两个实例，左柄以 Y 轴半周旋转展示对称效果；这个布局是审查用，不是玩家插槽变换。

游戏动作须把两个实例分别绑定左右手，不能把预览中两柄的位置烘焙进同一个物品模型。双击阶段共享一个施放序号，再按是否设计成两次击中决定每阶段命中集合；不能把同一个 collider 额外结算一遍。验证第一人称、副手冲突和非战斗状态的退回表现。

### 最新跳跃战技范围

用户最新明确允许战技跳跃攻击，覆盖此前“战技只能在地面”的要求。处决禁令不变。`weapon-arts.design.json` 现在是 `executions=false`、`specialJumpAttacks=true`、`skillJumpAttacksAllowed=true`；每个战技通过 `allowsSkillJump` 指示是否拟用专用跳跃轨迹，不意味着每个技能都必须跳。

三个设计 ID 更新为不带 `_grounded` 的名称，物品 ID 不变：`gold_breaker_grounded -> gold_breaker`、`destined_death_grounded -> destined_death`、`waterfowl_dance_grounded -> waterfowl_dance`。若其他 agent 已用旧 ID 写配置或存档，要加读取迁移/别名并更新默认技能映射；这些新技能尚未在本工作区 Java 注册。

| 技能 | 拟议阶段，非原作逐帧数据 |
| --- | --- |
| 黄金碎击 | 地面蓄力 -> 服务端起跳 -> 下砸 -> 首次有效落地时黄金冲击 -> 恢复 |
| 命定之死 | 起势 -> 跃起回旋 -> 下砸与刃场 -> 限时持续效果 -> 恢复 |
| 水鸟乱舞 | 跃起蓄势 -> 多段空中突进斩击 -> 收招落地 -> 恢复 |
| 唤星 | 地面重力牵引 -> 确认二段 -> 双剑跃起砸击 -> 落地冲击 -> 恢复 |
| 咒血斩击 | 前进/短跃斩击 -> 延迟血焰 -> 可选二段 -> 恢复 |

代码接续注意：`src/main/java/com/dsh/eldencombat/compat/EpicFightIntegration.java` 的 `CAST_SKILL` 当前仍按旧要求屏蔽部分跃起技能；本次只交模型和方案，没有解除 Java 限制。代码 agent 应将其改为批准技能的权限/资源校验，保留 `EXECUTION` 伤害拒绝和处决禁止。`COMBO_ATTACK` 当前把空中攻击替换成普通连击，跳跃战技激活时必须跳过这段替换，不可覆盖技能的专用动画。

- 不做处决，也不在弹反后启用处决分支。
- 黄金碎击、命定之死、水鸟乱舞等允许专用跃起/腾空攻击，不再强制地面改版；拟议动画仍需自行制作与原作轮廓对照。
- 普通原版跳跃/空中攻击保持普通规则；战技跳攻的消耗、位移和伤害独立配置，不全局给所有空中攻击加成。
- 位移、轨迹和落地点服务端权威；按胶囊/实体体积碰撞做沿途检测，限制高度、距离、滞空时长，不能穿墙、瞬移到客户端任意坐标或永久锁重力。
- 多段命中按 `castId + phaseId + targetId` 去重；下砸和落地冲击分别定义命中策略，落地事件只能结算一次。超时、被打断、死亡、换武器、断线、切维度等出口恢复重力、动作状态与输入，并清掉临时 collider/残留效果。
- 头顶被挡、边缘坠落、未落地、梯子、骑乘和水中要有拒绝/中止策略；客户端只做插值表现，不确认伤害和资源。
- 神躯化剑的黄金波动、掠夺火焰等远距效果要独立服务器命中体、遮挡与一次性结算，不能用超大近战 AABB。
- FP、精力、技能阶段、治疗、状态效果均服务端权威。命定之死的持续效果、火焰吸血必须有施放归属与期限，不能从共享动画上永久修改普通攻击。

本批没有对应玩家动画或特效资产，只有模型和战技说明。代码 agent 可先接模型与普通攻击，再接专属战技；缺少动作时应明确标记未完成，不用错误的招式占位冒充验收。

## 美术与游戏验收

美术检查：8 个第二版模型、每件超过 4 倍旧几何量、BB 引用、2048 图集、六面 UV 与 BB 导出一致、嵌入贴图一致、桌面/手机逐件完整取景、拖动旋转和局部近看。结果以重新生成的 `preview-validation.json` 为准，网页验收不验证游戏战技。

游戏仍需验证：物品注册与中文名、GUI/掉落/左右手/双手/盔甲、真实攻击范围、专属战技消耗与伤害、多人同步、资源重载、客户端优化模组兼容和独立服务端启动。没有新发布 JAR 或完成验收的 0.2.0 整合包。Boss 暂停且此前伤害问题未验收；不拿 Boss 测试结果代替本批武器验收。
