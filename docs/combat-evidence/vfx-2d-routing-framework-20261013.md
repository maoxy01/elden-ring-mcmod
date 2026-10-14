# 2D 样板接入框架：Kind 路由、斩击平面、真实缺帧降级与预算

日期：2026-10-13 晚
工作区：`D:\codexout\mcmod`（无 Git）
依据：[`DEEPSEEK-COMBAT-FRAMEWORK-HANDOFF-20261013.md`](../DEEPSEEK-COMBAT-FRAMEWORK-HANDOFF-20261013.md) 的「最新表现交接：三个 2D 样板」7 条接线要求 + [`VFX-REDESIGN-PLAN-20261013.md`](../VFX-REDESIGN-PLAN-20261013.md)
本轮边界（用户已确认）：**只做 DSH 侧框架**——不画素材、不下载、不启动游戏、不改伤害/FP/阶段时长/命中窗口。

## 结论

1. 交接的 7 条接线**全部落地**：按 Kind 的路由、真实缺帧降级、技能空间斩击平面、黄金波动逐车道波前、缓存/预算接入。
2. 四条路由（waterfowl / death_slash / cursed_slash / golden_wave）在清单里**全部 `status: pending`**，因此**今天每个 Kind 画出来的东西与改动前逐字节等价**：仍然是相机 billboard 的 `minecraft:explosion_0..15` + 0.18 alpha 盒体。素材放进目录、把 `status` 改成 `ready` 才生效。
3. **纠正交接第 2 条**（原文要求新增 `assets/eldencombat/atlases/particles.json`）：该文件**既不需要也不会被读取**。粒子图集的定义文件路径由原版固定为 `assets/minecraft/atlases/particles.json`，其 `directory` 源本身跨全部 namespace 收集 `textures/particle/**.png`。只要 PNG 放在 `assets/eldencombat/textures/particle/<id>/frame_NN.png`，sprite 就会自动成为 `eldencombat:<id>/frame_NN`。证据见 §4。
4. 验证程度：`compileJava test` 通过（185 项、0 失败），纯逻辑单测覆盖路由/平面/帧重采样/轮廓/预算；**实机观感、图集实际缝合、斩击方向随镜头转动等一律未验证**，清单见 §7。

## 1. 改动文件

改动前源码备份：`docs/code-backups/20261013-slash-route-framework/`（10 个文件；逐文件 SHA256 比对结果：9 个 CHANGED，`client/effects/SkillEffectRenderTypes.java` UNCHANGED，仅为留档）。

| 文件 | 新增/改动 | 内容 |
|---|---|---|
| `effects/FxSlashRoute.java` | 新增 | 一条路由的全部规则：`Orientation{SKILL_PLANE,CAMERA}`、`Outline{ARC,CRESCENT,WAVE_FRONT,BOXES}`、平面 yaw/pitch、顶点染色 tint、帧数、是否就绪；`frameIndex(semantic)` 把 16 个语义步重采样到任意帧数；`routeId(Kind)`、`defaultsForRoute`、`parse(JsonObject, defaults)`（只允许清单覆盖 orientation/plane/tint/outline） |
| `effects/SkillOutlineShapes.java` | 新增 | 纯几何像素轮廓（1/16 格对齐、15° 量化、完全确定性）：ARC/CRESCENT 9 段阶梯弧 + 亮凹边 + 3 点红焰尾；WAVE_FRONT 每个**未阻挡**车道 3 片，上限 `MAX_PANELS=48` |
| `effects/SkillSlashSprites.java` | 改动 | `Strip` 增加 `route`（材质选择）；新增 `buildWave(...)` 逐车道波前；`cullingRadius` 补 GOLD_WAVE |
| `effects/VfxBudget.java` | 改动 | 贴片上限按 Kind（水鸟 12 / 黄金波动 16 / 其它 6）；`Strip.route` 必填校验；新增 `outlineVertices` |
| `effects/VfxFrameBudget.java` | 改动 | 新增 `panels(int)`（4 顶点/片） |
| `client/PresentationRoutes.java` | 改动 | 解析 `fx.routes.<id>`；保留旧 `fx.slash` 兼容；整份清单失败时保留上一版并打日志；`revision()` 供缓存失效 |
| `client/effects/SkillSlashSpriteRenderer.java` | 改动 | `RouteState`（路由 + 真实进入图集的帧）、`route(Kind)`、区分 loaded/missing 的日志；就绪路由按技能空间平面绘制且不再叠 `+yaw`；旧路径逐字节不变 |
| `client/effects/SkillOutlineRenderer.java` | 新增 | 用 `MESH`(POSITION_COLOR) 画轮廓片，共用同一平面变换 |
| `client/effects/FxGeometryCache.java` | 改动 | 缓存键纳入路由；缓存轮廓片；保留 GOLD_WAVE 的 `profileTravel` 截断 |
| `client/effects/SkillEffectRenderer.java` | 改动 | 三段判定：贴图 / 轮廓 / 盒体（旧路径） |
| `client/effects/GoldenWaveRenderer.java` | 改动 | 就绪时逐车道贴图（帧号取自 `visualProgress`），否则轮廓，最后才是盒体；`takers_flames`（FLAME_FRONT）不受影响 |
| `assets/eldencombat/routes/2d.json` | 改动 | 新增 `fx.routes.{waterfowl,death_slash,cursed_slash,golden_wave}`，均 `pending`，各 16 帧 |
| `src/test/java/.../FxSlashRouteTest.java` | 新增 | 7 项纯逻辑测试 |

## 2. 路由模型与绘制判定

```
Kind ──FxSlashRoute.routeId──▶ "waterfowl" / "death_slash" / "cursed_slash" / "golden_wave" / null
      ──PresentationRoutes.route(id)──▶ Route(spec, frames)
      ──SkillSlashSpriteRenderer.route(kind)──▶ RouteState(spec, 真实入集帧) 或 null
```

判定（`SkillEffectRenderer` / `GoldenWaveRenderer` 一致）：

| 条件 | 画什么 |
|---|---|
| 路由存在且 `ready()==false`（本轮全部） | **旧路径原样**：相机 billboard + `minecraft:explosion_0..15` + 0.18 alpha 盒体（近战）/ 1.0 alpha 盒体（黄金波动） |
| `ready()` 且 LOD=FULL/REDUCED 且全部声明帧已入图集 且 `palette.slashSprites=true` | 技能空间平面上的序列帧贴图（贴片）；**不再画盒体** |
| `ready()` 但帧未全部入集 / LOD=CORE / `FxRenderBudget.pressure` / `slashSprites=false` | 程序化像素轮廓（ARC/CRESCENT/WAVE_FRONT）；仍然**不画盒体** |
| `ready()` 且 `outline.shape == boxes` | 显式要求时才回退旧盒体 |

平面契约（实体渲染器已施加 `-yaw`）：局部 **+Z = 施法者前方**、**+X = 其左侧**；`planeYaw` 绕局部 Y 旋转、`planePitch` 绕局部 X 倾斜（0 = 身前竖立、90 = 平铺地面）、roll 是四边形面内旋转。相机朝向只留给烟雾/火星（`orientation: camera`），主斩击不用。

黄金波动的帧号取自 `visualProgress(partialTick)`（形成后仍单调前进），**不取 travel**：`FxGeometryCache` 对 GOLD_WAVE 把 `profileTravel` 截断在 `WaveFront.FORMATION_DISTANCE`（4 格），若用 travel 驱动会在形成后停帧。逐车道 `z = -laneLag(lane, travel)`，被阻挡的车道不生成贴片/轮廓片，维持 `WaveFront` 的横向分段语义。

## 3. 清单 schema（`fx.routes.<id>`）

```json
"waterfowl": {
  "status": "pending",                       // ready | existing | pending（仅 ready/existing 才算就绪）
  "orientation": "skill_plane",              // skill_plane | camera
  "plane": { "yaw": 0.0, "pitch": 90.0 },    // 度，夹取到 ±180
  "tint": "#FFFFFF",                         // 顶点乘色；#RRGGBB
  "outline": { "shape": "arc", "color": "#E9FAFF" },  // arc | crescent | wave_front | boxes
  "atlas": "minecraft:textures/atlas/particles.png",  // 可选，写就必须是这个
  "frames": [ "eldencombat:waterfowl/frame_00", ... ] // 1..64 帧
}
```

- 校验失败（未知 orientation/outline、非法 `#RRGGBB`、非数字、帧数越界、`ready` 却没有帧、`ready` 但 PNG 不存在、`atlas` 不是粒子图集）⇒ 抛错 ⇒ **整份清单作废、保留上一版**，不会出现半更新的路由。
- `pending` 条目跳过 PNG 存在性检查（素材还没到也要能启动）；`ready` 条目逐帧检查 `assets/<ns>/textures/particle/<path>.png` 是否存在。
- 客户端还有第二道校验：sprite 必须真的缝进粒子图集，否则打 WARN `[skill-fx] route <id> is missing X of N frames ...` 并降级到轮廓 —— 即「不能悄悄回退爆炸帧却宣称替换完成」。
- `frames` 数量可任意 1..64；`frameIndex` 把 16 个语义步重采样（16 = 恒等；1 = 单帧；中间数覆盖满量程且不重复），因此美术给 N≠16 时**只需改 manifest**，不用改代码。

## 4. 纠正交接第 2 条：粒子图集不需要也不该新增 `assets/eldencombat/atlases/particles.json`

证据（`build/moddev/artifacts/neoforge-21.1.219-sources.jar` 与 `...-client-extra-aka-minecraft-resources.jar`，行号为该 jar 内源码行号）：

| 位置 | 事实 |
|---|---|
| `ParticleEngine.java:74` | `PARTICLES_ATLAS_INFO = ResourceLocation.withDefaultNamespace("particles")` → 图集定义 id 是 **`minecraft:particles`** |
| `ParticleEngine.java:95` | 粒子图集由 `ParticleEngine` 自己 `new TextureAtlas(LOCATION_PARTICLES)` 并注册进 `TextureManager`（不在 `ModelManager.VANILLA_ATLASES` 里，见 `ModelManager.java:46-65`） |
| `SpriteSourceList.java:32,70-71` | `ATLAS_INFO_CONVERTER = FileToIdConverter("atlases",".json")`，`idToFile` ⇒ 定义文件固定读到 **`assets/minecraft/atlases/particles.json`**；别的 namespace 下同名文件不会被请求 |
| `DirectoryLister.java:36-38` | `FileToIdConverter("textures/particle",".png").listMatchingResources(manager)` → 把所有 namespace 的 `textures/particle/**.png` 变成 sprite |
| `MultiPackResourceManager.java:89-94` | `listResources` 遍历**所有** `namespacedManagers`（按 namespace 过滤的是 `...FromNamespace` 变体）⇒ 确认跨 namespace |
| 原版 `assets/minecraft/atlases/particles.json` | `{"sources":[{"type":"directory","source":"particle","prefix":""}]}` |

结论：`assets/eldencombat/atlases/particles.json` 对应的 ResourceLocation 永远不会被请求（**不要创建**）；mod 的 PNG 只要放在 `assets/eldencombat/textures/particle/<id>/frame_NN.png` 就会自动进入粒子图集，sprite id 为 `eldencombat:<id>/frame_NN`。仅当将来确实需要**新增图集源类型**时，才应写 `assets/minecraft/atlases/particles.json`（同 id 会被 `getResourceStack` 合并）。

## 5. 每样板参数表（草案）

数值为**当前代码接口推导值**，不是美术定稿；`r = min(2.6, radius(RADIUS))`，`radius()` 把输入夹到 0.25..6；`alpha = opacity(progress)`（前 8% 淡入、后 30% 淡出：`(.25+.75*smooth(p/.08)) * smooth((1-p)/.3)`）。

| 项 | 水鸟银白短弧 | 命定之死黑红弯月 | 黄金波动金色波前 | （附带）cursed_slash |
|---|---|---|---|---|
| Kind / 路由 id | `WATERFOWL` / `waterfowl` | `DEATH_SLASH` / `death_slash` | `GOLD_WAVE` / `golden_wave` | `CURSED_SLASH` / `cursed_slash` |
| 素材包与许可 | 待美术（候选 Foozle Lucifer Effects、Kenney Particle Pack；须存作者 URL、版本/哈希、随包许可） | 同左 | 同左 | 本轮不改观感 |
| 透明 PNG 尺寸 | 待美术 | 待美术 | 待美术 | 待美术 |
| 声明帧数 N | 16（可 1..64，改 manifest 即可） | 16 | 16 | 16 |
| 语义帧来源 | 每层 `delay=0.055*i`（i=0..5），`t=(p-delay)/(1-delay)`，`frame=min(15,int(t*16))` | 每层 `delay=0.035*i`（i=0..2） | `semantic=int(visualProgress*16)` 夹到 0..15 | 同命定之死 |
| burst 播放 | 服务端 3 个 burst（ACTIVE 0/12/24 tick，phase=0/1/2），每个视觉实体 8 tick；phase 偶/奇决定 `mirror=±1` | 同左（0/8/16 tick） | 单一实体，按 `visualProgress` 连续推进 | 同左 |
| 挂点偏移（相对施法者原点） | 6 层：`x=mirror*(i-2.5)*0.42`、`y=0.5+0.3i`、`z=0.4+(i%2)*0.65`、`roll=mirror*[18,-24]` | 3 层：`x=mirror*(i-1)*0.42`、`y=0.5+0.58i`、`z=0.4+(i%2)*0.65`、`roll=-28*mirror` | 每车道：`x=-r+(lane+.5)*w`、`y=0.03`、`z=-laneLag(lane,travel)`、`roll=0` | 3 层：`x=mirror*(i-1)*0.2`、`y=0.7+0.28i`、`z=0.7+(i%2)*0.2`、`roll=-28*mirror` |
| 平面朝向（草案） | `skill_plane`, yaw 0, pitch **90**（平铺） | `skill_plane`, yaw 0, pitch **45** | `skill_plane`, yaw 0, pitch **90**（实体另有 `atan2(dirX,dirZ)` 朝向） | `skill_plane`, yaw 0, pitch 45 |
| 建议显示宽高（格，RADIUS=3 ⇒ r=2.6） | 宽 `r*(2.25+0.65*min(1,5t))` ≈ 5.85..7.54；高 `min(0.3, 宽*0.16)` = 0.3；亮芯叠层 宽×0.93、高×0.35 | 宽同左 ≈ 5.85..7.54；高 `min(0.65, 宽*0.16)` = 0.65 | 车道宽 `2r/16*0.92` ≈ 0.30；高 `0.28+0.22*(1-\|laneCenter\|)` ≈ 0.28..0.50 | 宽 ≈ 3.25..4.16；高 `min(0.32, 宽*0.16)` = 0.32 |
| alpha 曲线 | 外层 `opacity(t)*0.8`、亮芯 `opacity(t)` | 同左 | `opacity(progress)*(0.45+0.55*(1-\|laneCenter\|))` | 同左 |
| 轮廓降级 | ARC：18 片（≤48） | CRESCENT：18 + 6 片 | WAVE_FRONT：每未阻挡车道 3 片，满勤 48 片 | ARC：18 片 |
| 预算 | 贴片 ≤12/帧 | ≤6/帧 | ≤16/帧（`MAX_WAVE_STRIPS`） | ≤6/帧 |

**已知接口缺口（本轮未加字段，等美术素材到齐后定）**：贴图四边形的宽高直接取 `Strip.width/height` 并把整张 PNG 的 UV 映射上去，因此 **PNG 的宽高比会被拉伸到上表的宽高比**（如水鸟 ≈25:1、黄金波动车道 ≈1:1）。若美术素材比例不同，需要二选一：调整 `SkillSlashSprites` 的 width/height 公式，或在清单/`FxSlashRoute` 增加「显示宽高（格）」覆盖字段。这一条应在美术交付 manifest 时一并确认。

## 6. 素材到齐后的切换清单

1. 把 PNG 放进 `src/main/resources/assets/eldencombat/textures/particle/<route>/frame_NN.png`（**不要**建 `assets/eldencombat/atlases/particles.json`）。
2. 若总帧数不是 16，改 `2d.json` 该条的 `frames` 数组（1..64 均可）；必要时调 `plane` / `tint` / `outline`。
3. 把该条 `status` 从 `pending` 改成 `ready`。
4. 重载资源（游戏内 F3+T，或用既有 `-PclientSmokeReload` 夹具），看客户端日志：
   - `[skill-fx] route <id> loaded N/N frames from the particle atlas` → 走序列帧；
   - `[skill-fx] route <id> is missing X of N frames ...` → 说明 PNG 没进图集，此时只画轮廓（不会回退爆炸帧）。
5. 只改 manifest 就能切换，不需要动 Java。

## 7. 验证结果与未验证项

**已做（本轮）**

- `compileJava test --offline --no-daemon`：`BUILD SUCCESSFUL in 12s`；随后补了「真实清单」用例再跑一次 `test`：`BUILD SUCCESSFUL in 8s`。`build/test-results/test` 汇总 **27 个测试类 / 186 项 / 0 失败 / 0 错误 / 1 跳过**（跳过项是既有的 `LocalSourceMotionTest`）；新增 `FxSlashRouteTest` 8 项全部通过。
- 日志：`docs/combat-evidence/vfx-2d-routing-20261013.log`（同 `build/logs/vfx-2d-routing-20261013.txt`，另附测试汇总）。第一次运行时 `GoldenWaveRenderer.java:26` 报了旧调用签名（该文件的编辑当时未落盘，已用整文件重写修复），失败日志被第二次运行覆盖，此处以文字记录。
- 覆盖范围：路由 id 映射与默认值、16→任意帧重采样（端点/夹取/单调/满覆盖）、清单覆盖与非法值拒绝、轮廓片数上界与 1/16 格量化与确定性、阻挡车道不产生波前片、波前随 travel 前进、`buildWave` 只覆盖未阻挡车道、贴片必须带材质路由、帧预算计费，以及**真实 `2d.json` 按客户端同样规则解析**（`ui`/`fx.slash` 完整性、四个路由 id 已知、帧数合法、`status` 与 `ready` 一致、已就绪路由的每一帧必须有对应 PNG）。

**未验证（不得据此声称已验收）**

- 实机观感：第一/三人称、亮处/暗处可读性、连发残留、多人叠加预算 —— 本轮**未启动游戏**。
- 图集实际缝合：四个 route 目前没有 PNG，`loadedFrames` 的真实 loaded/missing 分支只在逻辑上成立，未在运行时观察过日志。
- 斩击方向随镜头转动、水鸟/命定之死与 `skill_poses` 的对齐、三个 burst 的可读性 —— 需素材 + 实机短片。
- `FxPalette.slashSprites=false` 时落到轮廓的实际画面（本轮只保证代码路径存在）。
- 黄金波动逐车道贴图的节奏（帧号由 `visualProgress` 驱动，未实机看）。
- 遗留挂账（与本轮「不启动游戏」边界冲突，未做）：`-PashSmoke` 夹具复跑；专用服务器 Waystones 夹具阻断。

## 8. 复跑命令

```powershell
$env:JAVA_HOME='D:\JDK21'
cd D:\codexout\mcmod
cmd /c ".\gradlew.bat compileJava test --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21 > build\logs\vfx-2d-routing-20261013.txt 2>&1"
```

- 不要在这条前面加 `clean`（会删掉 `build/clientSmoke|gracePrototypeClient` 下的存档，破坏依赖持久化的夹具）。
- 只看 `BUILD SUCCESSFUL` 不够：再核对 `build/test-results/test/*.xml` 的 `failures="0"`（或 `grep -c '0 failed'`）。
