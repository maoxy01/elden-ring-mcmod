# 八战技 VFX 运行收口报告（DSH）

2026-10-17（主体运行窗口 2026-10-16 23:30 – 2026-10-17 06:51）。本文件是实现回执，对应设计合同
[DSH-VFX-RUNTIME-FRAMEWORK-20261016.md](DSH-VFX-RUNTIME-FRAMEWORK-20261016.md) 的五步收口；
统筹复核要求的 R1–R3 补正先已单独交付：见 [SELECTED-ART-ACCEPTANCE-CLOSURE-20261016.md](SELECTED-ART-ACCEPTANCE-CLOSURE-20261016.md)。

## 0. 结论

**五步全部完成并机器验证**；同时**作废上一版报告里"作者美术没有上屏"的结论**（原因见 §6.1：是夹具截图时机错，
不是产品缺陷）。

| 步骤 | 状态 | 关键机器证据 |
|---|---|---|
| 1 夹具相机 / 构图门禁 | 完成 | 18 段 `VFX_SMOKE_COMPLETE`，退出码 0；每段真实 `Camera` 眼位/头 yaw 核对 + 视锥相交门禁 + 背向负例 |
| 2 主贴片 / 必要碎块一次变换两条职责 | 完成 | `ArtworkRenderer.DrawResult` 接管语义；`VFX_SUBMIT` 只在真正写入顶点后记录 |
| 3 客户端 lane 支撑/遮挡 + 保守可视框 | 完成 | `WaveArtworkVisibility`（每 tick 有界 clip、本波锁存）+ `WaveVisualBounds`；服务端命中/阻挡/推进一行未改 |
| 4 资源降级/恢复 | **完成**（本轮补齐） | 隔离资源包 **7/7 场景** 全绿：合法 / 缺 PNG / 缺 atlas / 非法 JSON / 恢复，`declared/present` 与提交路径逐场景符合期望 |
| 5 回归 + 证据 + 逐图审查 | 完成 | `gradlew test` **221 tests / 0 failed / 1 skipped**；18 段 + 7 场景 + 2 单段截图全部归档并逐张查看（§6） |

**本轮新增的硬门禁（框架 §9 的「可见主体」）**：`VFX_SHOT_DIFF` —— 同一机位下"效果存活帧"与"效果退场干净帧"
的逐像素差分（阈值：任一通道差 ≥24 记该像素变化；要求 ≥300 变化像素）。18 段实测 **26,356 – 409,914**，
最小段也有阈值的 88 倍。这不是放宽门禁，而是把"投影相交 / 提交了顶点"升级成"像素真的变了"。

## 1. 文件级改动

### 1.1 本轮（2026-10-17 凌晨，收口最后一轮）

| 文件 | 改动 |
|---|---|
| `src/main/java/com/dsh/eldencombat/testing/VfxIntegrationHarness.java` | 截图策略重做（见 §6.1）：取消固定帧号，改为"效果存活期采样 + 退场后同机位干净帧"；新增 18 段全配对的逐像素差分与 `VFX_SHOT_DIFF` 门禁；新增 `VFX_SHOT_REQ` 现场快照（眼位/朝向/活效果位置与 `dot`）；背向负例窗口内**冻结**设计朝向（`negativeAim`）并把"翻转过渡帧"单独计入 `negativeSkipped`；新增 `designStableTicks`/`aimSkipped`（1° 级位移抖动不再被当成"不稳定"）；证据帧发起前复核真实相机与设计朝向（超 15° 直接判段失败）；`ArtworkRenderer` 锚点诊断 |
| `src/main/java/com/dsh/eldencombat/client/effects/ArtworkRenderer.java` | `Submission` 增带平面锚点；`VFX_SUBMIT` 追加 `art= cam= camYaw= camPitch= dist= forwardDot=`（区分"没画出来"与"画在视野外/脚下"）；非波平面锚点包含作者 `offset` |
| `src/main/java/com/dsh/eldencombat/effects/VfxCameraMath.java` | 新增 `intersectsFrustum`（半空间 p-vertex，6 面），替代角点投影做可见判据 |
| `src/test/java/com/dsh/eldencombat/VfxCameraMathTest.java` | 新增 2 例：视锥拒绝相机后方/侧后体积；接受"包住相机"与长走廊体积 |
| `src/main/java/com/dsh/eldencombat/client/effects/ArtworkRenderer.java` / `SkillEffectRenderTypes.java` / `GoldenWaveRenderer.java` | 火墙 lane 过滤接入 `WaveArtworkVisibility`；`SLASH_SPRITE`（particle shader + particles atlas + NO_CULL）与 `MESH`（lightning shader）两条提交路径 |
| `build.gradle` | 新增 `-PvfxReload` 客户端块（隔离 gameDir `build/vfxReload`）、`-PvfxVantage` 诊断开关、`vfxReload` 的 `doLast` 门禁（要求完成标记、禁止 FAIL/INCOMPLETE） |
| `tools/vfx-live-clean-sheet.py` | **新增**：从日志的 `VFX_SHOT_DIFF` 行取每段最优配对，产出"效果帧 / 干净帧 / 变化像素掩膜"三联证据图 |

### 1.2 上一轮（2026-10-16，保留）

`VfxIntegrationHarness`（相机/门禁整体重做、段内取景锁定、跟随模式、lane 计数）、`VfxCameraMath`
（`aimAt`/`angleBetween`/`ndcExtent`/`intersectsScreen`/`eyeInside`）、`SkillEffectRenderer`、`GoldenWaveRenderer`、
`WaveArtworkVisibility`（新增）、`WaveVisualBounds`（新增）、`VfxBudget`、`WaveVisualBoundsTest`（新增）、`build.gradle`（VFX 门禁）。

备份：改动前副本在 `docs/code-backups/20261016-vfx-runtime-dsh/`（9 个文件）；
本轮收口状态副本在 `docs/code-backups/20261017-vfx-liveclean-dsh/`。

## 2. 实际命令与终态

```powershell
Set-Location -LiteralPath D:/codexout/mcmod
$env:JAVA_HOME='D:/JDK21'
cmd /c ".\gradlew.bat compileJava --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21"          # exit 0
cmd /c ".\gradlew.bat test --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21"                 # exit 0
cmd /c ".\gradlew.bat runClient -PvfxSmoke --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21" # exit 0（18 段）
cmd /c ".\gradlew.bat runClient -PvfxReload --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21"# exit 0（7 场景）
cmd /c ".\gradlew.bat runClient -PvfxSmoke -PvfxSegments=1 -PvfxFlame --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21"    # exit 0
cmd /c ".\gradlew.bat runClient -PvfxSmoke -PvfxSegments=1 -PvfxMissing --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21"  # exit 0
cmd /c ".\gradlew.bat jar --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21"                  # exit 0
```

运行日志（记录用）：`build/vfx61-full18-final-20261016.log`、`build/vfx57-reload-20261016.log`、
`build/vfx62-flame-final-20261016.log`、`build/vfx62-missing-final-20261016.log`、`build/vfx60-test-20261016.log`、
`build/vfx63-jar-20261016.log`。**以上六次运行使用完全相同的源码**（最后一次源码改动在 `vfx55` 编译之后无改动）。

## 3. 机器结果

### 3.1 18 段验收运行（`-PvfxSmoke`，exit 0）

每段都满足：`invisible=0`、`negativeInvisible==negativeChecks`、`armed=true`、`liveShots≥1`、
`subjectBest≥300`、`maxAimError≤7°`。段 8–15 为夜间（`night=true`），其余白天。

| 段 | 战技 | night | gatePasses | enclosing | negative | liveShots | pairs | subjectBest | maxAimError | aimSkipped |
|---|---|---|---|---|---|---|---|---|---|---|
| 0 | wave_of_gold | false | 39 | 0 | 29/29 | 2 | 2 | 26356 | 0 | 12 |
| 1 | gold_breaker | false | 40 | 0 | 29/29 | 2 | 2 | 53021 | 2 | 11 |
| 2 | destined_death | false | 105 | 3 | 28/28 | 4 | 4 | 70959 | 7 | 12 |
| 3 | takers_flames | false | 34 | 0 | 30/30 | 1 | 1 | 33393 | 0 | 11 |
| 4 | waterfowl_dance | false | 94 | 0 | 22/22 | 4 | 10 | 163695 | 6 | 10 |
| 5 | starcaller_cry | false | 105 | 0 | 29/29 | 4 | 6 | 216116 | 6 | 12 |
| 6 | bloodboon_ritual | false | 183 | 0 | 29/29 | 4 | 4 | 145760 | 0 | 12 |
| 7 | cursed_blood_slice | false | 120 | 43 | 13/13 | 4 | 4 | 409914 | 3 | 12 |
| 8 | wave_of_gold | true | 41 | 0 | 29/29 | 1 | 1 | 27041 | 0 | 11 |
| 9 | gold_breaker | true | 40 | 0 | 30/30 | 2 | 2 | 78166 | 2 | 11 |
| 10 | destined_death | true | 104 | 2 | 30/30 | 4 | 4 | 40200 | 6 | 11 |
| 11 | takers_flames | true | 34 | 0 | 30/30 | 1 | 1 | 26380 | 0 | 10 |
| 12 | waterfowl_dance | true | 97 | 0 | 21/21 | 4 | 4 | 93927 | 6 | 12 |
| 13 | starcaller_cry | true | 103 | 0 | 30/30 | 4 | 6 | 258039 | 6 | 12 |
| 14 | bloodboon_ritual | true | 182 | 0 | 30/30 | 4 | 4 | 119470 | 0 | 12 |
| 15 | cursed_blood_slice | true | 121 | 45 | 13/13 | 4 | 4 | 409914 | 3 | 12 |
| 16 | wave_of_gold | false | 41 | 0 | 29/29 | 1 | 1 | 27294 | 0 | 11 |
| 17 | waterfowl_dance | false | 98 | 0 | 18/18 | 4 | 4 | 121936 | 6 | 12 |

- 截图 76 张（`live` / `clean` 两类，每段 2–7 张），另 18 张三联证据图。
- `enclosing` 单列：包围盒把相机包住的帧（以施法者为中心的效果），它们**不计入**可读门禁次数
  （要求 `gatePasses - enclosing ≥ 3`）。
- `aimSkipped` 是"设计朝向正在大拐弯"的帧数（翻转/施放瞬间），这些帧不参与"真实相机是否跟到位"的段判据；
  每一条都在日志里留了 `VFX_CAMERA_MISMATCH ... stable=0` 记录，没有被静默丢弃。

### 3.2 隔离资源包降级/恢复（`-PvfxReload`，exit 0，7/7）

| 场景 | 隔离包 | 路由 | declared | present | 期望路径 | revision | ok |
|---|---|---|---|---|---|---|---|
| 0 | （无） | golden_wave | 16 | 16 | sprite | 2 | true |
| 1 | vfx-missing-png | golden_wave | 16 | 15 | outline | 3 | true |
| 2 | vfx-missing-png | waterfowl | 16 | 16 | sprite | 4 | true |
| 3 | vfx-missing-atlas | waterfowl | 16 | 0 | outline | 5 | true |
| 4 | vfx-missing-atlas | golden_wave | 16 | 16 | sprite | 6 | true |
| 5 | vfx-bad-json | golden_wave | 16 | 16 | sprite | 7 | true |
| 6 | （无） | waterfowl | 16 | 16 | sprite | 8 | true |

即：真缺 PNG → 声明保留 16 / 实际命中 15 / 该路由整体走轮廓；缺 atlas → 该路由命中 0 / 走轮廓；
非法 JSON → loader 保留上一份有效 Data（revision 仍递增，路由与路径不变）；改回合法资源后重载恢复。

### 3.3 单段诊断

| 运行 | exit | 结论 |
|---|---|---|
| `-PvfxSmoke -PvfxSegments=1 -PvfxFlame` | 0 | 火墙场景 `invisible=0`、`subjectBest=29114`；lane 计数 `VFX_LANE` 正常 |
| `-PvfxSmoke -PvfxSegments=1 -PvfxMissing` | 0 | 强制缺图 → 轮廓降级路径 `subjectBest=3418`（**降级后的轮廓也真的上了屏**） |

### 3.4 单元测试

`gradlew test`：**221 tests / 0 failed / 0 errors / 1 skipped**（33 个 suite）；
含新增 `VfxCameraMathTest`（视锥 p-vertex 正反例）与 `WaveVisualBoundsTest`（5 例）。

### 3.5 交付 JAR

`build/libs/eldencombat-0.2.0.jar`（1,618,188 B，2026-10-17 06:51:26）
SHA256 `07A14CEB3A061405299DCDEF8F6E9F986FF3AC1A5354ECD0445033DB19DF8E6F`。
包含 `com/dsh/eldencombat/testing/` 下**仅** `PlayerAssetChecks.class`、`SelfTest.class`；
**所有 `*Harness*` 夹具类均不在包内**（`build.gradle` 排除规则，已用 `jar tf` 核对）。

## 4. 真实提交帧与缺失原因

- **提交口径**：`VFX_SUBMIT` 只在 `plane()` 真正拿到 buffer 并写入顶点后记录（`FxRenderBudget.FRAME.sprites/panels` 准入通过），
  键含 `资源代数 | 实体身份 | phase | frame | path | lanes`，不再把"预算前 / 地形裁剪前"的候选当成已提交。
- **本轮新增诊断**：`VFX_SUBMIT` 现在附带 `art=(x,y,z) cam=(x,y,z) camYaw camPitch dist forwardDot`。
  实测例（段 2 destined_death 首帧）：`art=(0.000,82.298,0.599) cam=(0.000,83.918,0.599) dist=1.620 forwardDot=0.459`
  ——作者平面就锚在施法者脚下的 1.6 格处，`forwardDot<1` 说明它在镜头轴线外约 63°，这正是"投影相交门禁会过、
  但肉眼未必好看"的形态；因此门禁只把它当"构图可读"，真正的可见性交给 §6 的像素差分。
- **缺失原因分类**（保持上一版并补充实测）：预算拒绝（`handled=true` 但 `records=0`）、
  地形支撑 MISS / 区块未加载（火墙记 UNKNOWN、跳过该 lane）、LOD HIDDEN（>128 格）、生命周期结束（实体移除）、
  进度越界（`frame<0` → `handled=true`、提交 0）。
- **帧可达 ≠ 实际提交**：`FxArtwork.frame` 的 1..64 映射由既有单测保证可达；本报告只声明"本次生命周期实际提交集合"。

## 5. 视觉证据

目录：`docs/combat-evidence/vfx-runtime-closure-20261016/`

| 路径 | 内容 |
|---|---|
| `vfx-smoke-18seg-liveclean-20261016.log` | 18 段记录日志（含 `VFX_SHOT_REQ`、`VFX_SHOT_DIFF`、`VFX_SMOKE_SEGMENT`） |
| `screenshots-18seg-liveclean/` | 76 张：每段"效果存活帧"`-liveN-` 与"同机位干净帧"`-clean-` |
| `live-clean-sheets/live-clean-<段>-<战技>.png` | 每段三联图（效果帧 / 干净帧 / 变化像素掩膜），共 18 张 |
| `live-clean-sheets/live-clean-all.png`、`evidence-sheet-part1.png`、`evidence-sheet-part2.png` | 汇总图 |
| `vfx-reload-liveclean-20261016.log`、`screenshots-reload-liveclean/` | 7 场景 56 张（每场景 live/clean 配对） |
| `vfx-flame-final-20261016.log` / `screenshots-flame/` | 火墙墙场景 3 张 |
| `vfx-missing-final-20261016.log` / `screenshots-missing/` | 强制缺图（轮廓降级）3 张 |
| `unit-tests-20261016.log` | 221 tests / 0 failed / 1 skipped |
| `superseded-*` | **上一版（固定帧号 20/44/68/92）的截图与接触表**：作为"错误证据"保留，见 §6.1 |

证据图由 `tools/vfx-live-clean-sheet.py <log> <screenshots> <out>` 生成，阈值与夹具一致（通道差 ≥24）。

## 6. 逐图审查

### 6.1 先更正一条作废结论

上一版收口过程中曾得出"18 段无一可见、0 个饱和美术像素，只有 starcaller_cry 例外"的结论，并据此怀疑
`SLASH_SPRITE` 的深度/合成。**该结论已作废**，根因是夹具的截图时机，而不是渲染：

1. 旧夹具把截图帧写死为 `{20,44,68,92}`。实测 `golden_wave` 只在 tick 20–38 存活
   （`VFX_LANE ... age=4/8/12/16` 对应 tick 24/28/32/36），第 2、3、4 张图必然拍在效果消失之后。
2. 旧夹具只在"发起时有效果"的状态下记录 `VFX_SUBMIT`，因此日志一直显示 `lanes=16 quads=16` 提交正常，
   而截图里什么都没有 —— 两条证据链指向不同时刻。
3. 新夹具的 `VFX_SHOT_REQ` 直接记录现场：旧方案下 `live=0 effects=[]`（拍空帧），
   新方案下 `live=1 effects=[wave#3 ... dist=4.61 dot=1.000]`（正对镜头，4.6 格）。
4. 换成"效果存活期采样 + 同机位干净帧差分"后，同一段同一二进制给出 `changed=2079`（阈值 300），
   肉眼可见金色波前；全 18 段最小 26,356。

**教训（已写入长期记忆规则）**：`VFX_SUBMIT` 只能证明"提交过顶点"，`VFX_SMOKE_SEGMENT` 只能证明"包围盒进过视锥"；
主体是否上屏必须用**同一机位、同一几何**的"有/无效果"像素差分，且截图必须与效果寿命对齐。

### 6.2 逐段结论（每段取变化像素最多的配对）

| 段 | 战技 | 变化像素 | 视觉结论（人工查看三联图） |
|---|---|---|---|
| 0 | wave_of_gold | 26356 | 清晰：贴地金色波前横贯画面，带锯齿状浪尖 |
| 1 | gold_breaker | 53021 | 大范围半透明覆盖（弥散斑点掩膜），画面亮度/色调整体被压暗 |
| 2 | destined_death | 70959 | 同上：以施法者脚下 1.6 格为锚的平面，覆盖屏幕下半区 |
| 3 | takers_flames | 33393 | 清晰：红橙火焰墙，火舌分层、底色明亮 |
| 4 | waterfowl_dance | 163695 | 大范围覆盖（掩膜几乎铺满中下部），与突进位移叠加 |
| 5 | starcaller_cry | 216116 | 清晰：淡紫色环形缎带 + 被吸起的石块 |
| 6 | bloodboon_ritual | 145760 | 清晰：画面上方的猩红弧带 |
| 7 | cursed_blood_slice | 409914 | 清晰：猩红斩击铺满全屏（掩膜≈全屏） |
| 8 | wave_of_gold | 27041 | 夜间：金色波前在暗场里对比强烈 |
| 9 | gold_breaker | 78166 | 夜间半透明覆盖 |
| 10 | destined_death | 40200 | 夜间脚下平面覆盖 |
| 11 | takers_flames | 26380 | 夜间火焰墙 |
| 12 | waterfowl_dance | 93927 | 夜间大范围覆盖 |
| 13 | starcaller_cry | 258039 | 夜间紫色环 + 石块 |
| 14 | bloodboon_ritual | 119470 | 夜间猩红弧带 |
| 15 | cursed_blood_slice | 409914 | 夜间猩红斩击铺满全屏 |
| 16 | wave_of_gold | 27294 | 白天（墙场景段）金色波前 |
| 17 | waterfowl_dance | 121936 | 大范围覆盖 |

**诚实说明三类观感差异**：
1. 「清晰作者美术」= 掩膜呈单一连通形状，肉眼一眼能认出（wave_of_gold / takers_flames / starcaller_cry /
   bloodboon_ritual / cursed_blood_slice）。
2. 「大范围半透明覆盖」= 掩膜呈弥散斑点（gold_breaker / destined_death / waterfowl_dance）：这类战技的平面
   贴着眼位且面积大，画面被整体染色而不是呈现一个可辨认图形。**它的可见性有两帧以上证据，但它是否"好看"
   属于人类观感验收，不由本报告背书。**
3. 同一段内不同 live 帧的变化量差异很大（例如段 2 `live1=55399 → live4=0`，段 6 `live1=145754 → live2..4=0`）：
   效果后段要么平面已掠过镜头、要么被背向负例窗口挡住采样。门禁取"该段最优配对"，**逐帧原始数字都在
   `VFX_SHOT_DIFF` 行里**，没有被平均或挑拣掉。

## 7. 未验项与诚实边界

1. **第三人称/环绕机位未收口**：夹具原有的 ArmorStand 环绕机位实测"真实 Camera 俯仰比设定值落后 56–112°"
   （引擎对相机眼位/眼高逐 tick 平滑），本轮已移除；段 8–15 是**夜间第一人称**，因此
   **不宣称合同里"夜间环绕第三人称"通过**。
2. **像素差分 ≠ 人类观感验收**：差分把云影移动与相机微动也算进去。本夹具里玩家冻结、机位锁定，噪声极小
   （干净帧之间的差异量级见 `VFX_SHOT_DIFF` 中同段多配对的一致性），但"挡住/好看/符合原版节奏"仍需人工观看。
3. JSON/atlas 存在 ≠ 被绘制；独立 JAR 安装、专服、多人/迟加入/极端延迟、光影、PCL 导入、最终人类观感均未验证。
4. 保护文件未改动：5 个用户选版美术、94 张已发布 PNG（+8 个 UI 图标）、黑剑刃口朝外、
   `starscourge` 正面 SHA `270c60fe…`、`display-overrides.json` `daefa157…`、`modpack/mods.lock.json` `e2795047…`、
   Spartan JAR `84d3ddaf…`。
5. 服务端命中语义未变：火墙视觉遮挡不回写 `DATA_BLOCKED`；"隐藏 lane 仍发生既有命中"的真实差异本轮仍未找到例子（不代表不存在）。
6. `-PvfxVantage`（俯视自己脚下的诊断开关）**只作诊断**：实测相机放在施法者脚下时，飞出去的金色波已经离开视野
   （vantage 截图里只有地面与被打飞的石块），不能作为验收证据。
7. 火墙 lane 的"遮挡锁存"在最终运行里没有被触发（支撑探针落在墙内部 → 判定 UNKNOWN 的路径），
   锁存行为只在更早一次同墙配置运行 `build/vfx1-flame4-20261016.log` 里出现过（`obstructed=5`）；本报告不据此宣称已回归。

## 8. 下一步队列

1. grace/map 视觉与普通战灰获取。
2. 模组整合（新增模组 + Combat Roll 替换）：实施已交接 Codex，DSH 侧等待合并后复验。
3. 整包交付（发布 JAR + 依赖锁 + 说明文档）。
4. 若仍要第三人称机位：改用"真实 `Camera` 位置回读"作为瞄准基准，或让机位每 tick 位移 ≤0.25 格后再复测。
