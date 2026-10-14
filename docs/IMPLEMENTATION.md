# 实现说明与验收状态（dsh 侧）

历史记录说明：本文件描述 dsh 首版；用户后续要求由 Codex 完成剩余代码。下面“玩家未接入”“没有 Boss 血条/粒子”“无法运行 GPU 客户端”等是当时状态，现已变化。最新实现和实际游戏测试见 [CODEX-IMPLEMENTATION.md](CODEX-IMPLEMENTATION.md)。

对应交接说明：[DSH_HANDOFF.md](../DSH_HANDOFF.md)。
本文件记录**已实现什么、怎么分层、哪些验收项已自动化、哪些还需游戏内人工确认**。

## 1. 四个模块与代码位置

| 模块 | 包 | 说明 |
| --- | --- | --- |
| 规则核心（无 MC 依赖，可单测） | `com.dsh.eldencombat.core` | `CombatState` 单一状态源、`ActionSpec`/`HitWindow`、`CombatRules`、`CombatConfig`（全部数值集中）、`geom.Collision`（胶囊/盒距离、锥角） |
| 方块骨架与动作数据 | `com.dsh.eldencombat.skeleton` | `ModelData`（schema v1 解析）、`AnimationClip`（20 TPS 线性插值）、`SkeletonPose`（W/A 矩阵）、`HitVolumes`（木杖/匕首/锤头判定体）、`SkeletonData`（全局唯一加载入口，客户端与服务端共用） |
| 服务端动作/资源/命中 | `com.dsh.eldencombat.server`、`entity`、`command`、`network`、`registry` | `ServerCombat`（tick 推进 + 意图校验 + 同步）、`HitResolver`（扫掠判定 + 盾防/弹反/无敌结算）、`Weapons`（物品标签识别）、`MargitEntity`（AI 与动作时间线） |
| 客户端输入/视角/HUD/渲染 | `com.dsh.eldencombat.client` | `ClientCombat`（KeyMapping、意图、锁定视角、状态镜像）、`CombatHud`（HP/精力）、`MargitRenderer` + `MargitModelRenderer`（方块骨架渲染） |

数据流：客户端只提交 `CombatIntentPayload`（意图 + 连续序号 + 可选目标 + 规范化方向）→
服务端校验存活/武器/状态/序号/精力/目标范围 → 扣资源、进入动作、开命中窗口 →
`CombatStatePayload` 回同步（动作 ID、序号、开始 tick、精力、药瓶、锁定、战斗模式）。

## 2. 关键实现约定

- **矩阵约定**（与 `art/tooling/viewer.mjs` 一致）：`W_bone = W_parent · T((pivot-parent.pivot)/16) · R(静态+动画增量, XYZ 欧拉)`；
  另存 `A_bone = W_bone · T(-pivot/16)` 用于把"建模坐标/16"直接变到模型空间。旋转只做局部旋转再串矩阵，不逐级累加世界角度。
- **UV**：材质矩形按像素取半像素内缩，V 轴直接用 PNG 顶部原点（MC 约定），面顺序 +X/-X/+Y/-Y/+Z/-Z，与 three.js BoxGeometry 对齐；
  `head` 的 -Z 面用 `face` 材质；四边形按 TL,BL,BR,TR 提交保证逆时针。
- **武器挂点**：黄金匕首/锤子用 `SkeletonPose.attachTo(pose, "spectral_socket")` 继承插槽矩阵，根部原点即握持点；
  显示时机取动作事件 `equip/unequip`（客户端与服务端读同一份 `margit.animations.json`）。黄金件用 `LightTexture.FULL_BRIGHT` 全亮。
- **判定体**：木杖取 `staff_shaft` 长轴胶囊；匕首取附属模型 AABB 的"握持点→最远角"胶囊；黄金锤取锤头盒的世界 AABB。
  全部覆盖**上一 tick 与当前 tick** 两个位置，并用 `Level.clip` 做阻挡 raycast（隔墙不判定）。
  人体与尾巴从不作为伤害体。
- **玩家武器**：Codex 的玩家模型/动作尚未交付，首版用"手部→刀尖"的数值胶囊 + 横扫角（`weapons.*.reach/sweepDegrees/bladeRadius`）近似骨骼采样，
  判定算法与 Boss 侧共用同一套几何代码；玩家模型到位后把这一段换成真实的骨骼采样即可，规则层不用改。
- **伤害与无敌**：翻滚无敌、盾防、弹反都在 `HitResolver` 里**在调用 `hurt` 之前**结算，
  因此环境伤害（虚空/溺水/饥饿）与其它来源伤害完全不受影响；受击打断另由 `LivingIncomingDamageEvent` 兜底。
- **状态存储**：`CombatState` 挂在玩家 AttachmentType 上且**不序列化**——读档/退出/重生不会残留无敌、武器显示与锁定；重生时药瓶次数按配置恢复。
- **已取消的内容**：处决、跳跃攻击、FP、魔法/祷告/战技、属性加点、负重档位、符文、地图、赐福。
  空中攻击与地面共用同一 `ActionSpec`，不叠加下落伤害或削韧倍率，落地不会重复命中（同一 `actionSequence` 只结算一次）。

## 3. 验收状态

自动化（`gradlew test` 与游戏内 `/eldencombat selftest` 共用 `testing.SelfTest`，共 95 项，当前 0 失败）：

| 交接说明的"必须检查" | 状态 |
| --- | --- |
| 精力不足 / 重复包不触发攻击 | 自动：`CombatRules.staminaOk`、`sequenceIsNewer` + 单测 |
| 每个窗口只命中一次 | 自动：`CombatState.markHit` 语义单测 |
| 翻滚窗口内外 | 自动：无敌窗口 [4,11) 内外边界单测 |
| 正面/背面盾防与破防 | 自动：0°/59°/180° 锥角判定 + 破防 24 tick 单测 |
| 弹反窗口边界 | 自动：2/3/6/7 tick 边界单测 |
| 失衡仅造成硬直、不触发处决 | 自动：`ActionId` 中不存在处决动作 + 弹反两次达失衡值 |
| 喝药中断不误扣 | 自动：第 18 tick 才扣，提前打断不扣 |
| 读档/死亡/卸载不残留无敌与武器 | 自动：`CombatState.reset` 检查 + 附件不落盘的设计 |
| 空中与地面攻击数值相同、落地不重复命中 | 自动：同一 `ActionSpec` + 同一序号只命中一次 |
| 20 TPS 与渲染 FPS 无关 | 架构：窗口判定只吃整数游戏 tick，客户端插值只影响表现 |
| 翻滚撞墙、界面终止按住、二阶段只触发一次 | 需游戏内人工确认（见下） |

服务端集成验收（`runServer` + 自动数据包）已实测通过：
自检 95 项全 PASS、刷 Boss、存活 100 tick（AI 与骨骼采样无异常）、
`damage` 掉血生效、死亡后按 `death` 动作停留再清理。

## 4. 尚未完成 / 需要 Codex 与用户配合

1. **客户端观感验收**：`gradlew runClient` 需要人工看画面——骨骼姿态、武器是否脱手、前摇落点是否看得清、贴图是否最近邻不模糊。
   自动化环境跑不了 GPU 客户端，这一项必须人工录像复核。
2. **玩家模型与动作**：玩家侧仍是原版模型/动作；`weapons.*` 的数值胶囊要替换为 Codex 交付的玩家骨架采样。
3. **音效**：目前借用原版 Ravager 的受伤/死亡音与盾牌格挡音，待 Codex/用户给音源。
4. **Boss 血条与粒子**：首版未做 Boss 血条、死亡粒子与冲击特效。
5. **手感数值**：动作时长、削韧、翻滚距离都是首轮调试默认值，需在游戏内共同调。
