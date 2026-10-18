# 已选美术补验收口（A/B/C 完成，R1–R3 复核补正已并入）

2026-10-16。分工：战斗部门只给框架与验收合同，具体实现由 DSH 完成（见 [DSH交接](D:/codexout/mcmod/docs/DSH-ART-ACCEPTANCE-FRAMEWORK-HANDOFF-20261016.md)）。统筹复核见 [CODEX复审](D:/codexout/mcmod/docs/CODEX-ART-ACCEPTANCE-CLOSURE-REVIEW-20261016.md)，其 R1–R3 最小补正已在本文件与证据中落实（补正明细见第 10 节）。

## 0. 结论

- A（连枷+缺提供方回退）、B（真实赐福菜单+拒绝路径）机器场景通过；**C 的回退/恢复视觉证据已补**：原先两张截到 Mojang 加载界面，现改为等加载 overlay 消失、连续 10 帧有效渲染后再异步截图并等落盘回执。
- 审计脚本已改为**按当前路由/清单逐目标 JAR 路径比对内容**，缺失/内容不符/路径放错/保护文件变化一律非零退出；并用 5 个隔离坏样本证明门禁不假绿。
- 未验（不得据此宣称整合包完成）：人类物理鼠标与最终观感、独立 JAR 安装、专用服务器、多人、光影、PCL 新实例导入；提供方 10 件只有 GUI 得证（长弓另有第一人称右手与四状态），其余手持上下文、多层/自定义 loader 未验。

## 1. 最终产物与源码一致性

| 项 | 值 |
|---|---|
| 最终 JAR | `build/libs/eldencombat-0.2.0.jar` |
| 体积 | 1,595,013 B（1153 条目，其中 PNG 342 条） |
| SHA256 | `231d9cb257a245d960c6d56cc2cceb8c15fec154ef66fda268d49f048a163eb2` |
| 源码/清单哈希 | [source-hashes-at-final-20261016.json](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/source-hashes-at-final-20261016.json)（含 routes/2d.json、equipment-42.json、publishable-assets.json、ui-pixel/assets.json、审计脚本等 12 项） |
| 构建回执 | [jar-rebuild-after-r1-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/jar-rebuild-after-r1-20261016.log)（R1 后重建：`Task :jar UP-TO-DATE`，SHA 不变） |

R1 的源码改动只落在 `SelectedArtAcceptanceHarness`（JAR 排除项），所以重建 JAR 仍判 UP-TO-DATE、SHA 与统筹复核时一致；验收运行的 A/B/C 均使用同一份最终源码（`runClient` 走 classes 目录，产物排除项不影响运行类路径）。

## 2. 测试源码打包边界（结论）

产物 JAR 内 `com/dsh/eldencombat/testing/` 只剩两项，且都**必须保留**：

- `PlayerAssetChecks.class`、`SelfTest.class` —— 被正式命令 `EldenCombatCommand` 调用（`SelfTest` 再调 `PlayerAssetChecks`），删掉整个 testing 目录会导致正式命令缺类。

其余夹具一律不入包（`build.gradle` 的 `tasks.named('jar')` 逐条 `exclude`）：`ClientSmokeHarness`、`ServerSmokeHarness`、`SkillSmokeHarness`、`GracePrototypeHarness`、`GraceClientPrototypeHarness`、`AshSmokeHarness`、`SelectedArtAcceptanceHarness`、`GraceArtAcceptanceHarness`、`VfxIntegrationHarness`。

`VfxIntegrationHarness` 是 `@EventBusSubscriber`，只在系统属性 `eldencombat.vfxSmoke` 下工作；全仓检索确认除自身外无任何引用，因此从产物排除安全。包内不含研究源目录、`.bbmodel`、预览与 qa 目录。

## 3. 审计：目标路径驱动的 102 张 PNG 与 466 份保护校验

审计脚本 [art-acceptance-audit-20261016.ps1](D:/codexout/mcmod/tools/art-acceptance-audit-20261016.ps1)（只读，R2 重写），输出 [audit-r2-targets-102-negative-samples-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/audit-r2-targets-102-negative-samples-20261016.log)。

做法（不再是"整包 PNG 哈希集合命中"）：目标 JAR 路径由**当前** `routes/2d.json`（28 个 `own_sprite`/ready 条目的 held+gui 状态）与 `art/ui-pixel/assets.json` 推出，期望哈希取构建准入用的 `publishable-assets.json`（并与 `equipment-42.json` 交叉核对，两处不一致即失败）。逐项检查：源 PNG 存在且等于期望哈希 → JAR 内**该路径**存在且内容哈希相等 → 2D 纹理命名空间里不允许出现目标以外的 PNG（挪路径/残留在此暴露）→ 466 份保护文件逐字节。

| 检查 | 结果 |
|---|---|
| 装备目标（`assets/eldencombat/textures/item/2d/*.png`） | 94 |
| UI 图标目标（`assets/eldencombat/textures/ui/*.png`，由 assets.json 的 8 条建议资源 ID 推出，**磁盘缺失不会被跳过，仍列预期并记缺失**） | 8 |
| 期望目标合计 | 102 |
| 源 PNG 缺失/差异 | 0 / 0 |
| JAR 内该路径缺失/内容不符 | 0 / 0 |
| 2D 命名空间多余 PNG | 0 |
| 保护文件 466 份 变化/缺失 | 0 / 0 |
| 结果 | `RESULT = PASS`，`OVERALL = PASS`，退出 0 |

隔离坏样本（`-NegativeSamples`，只在 `build/art-acceptance-audit-negative/` 里制造，不碰正式素材与产物）：

| 坏样本 | 观察到的失败 | 退出码 |
|---|---|---|
| JAR 内删掉 1 张目标 PNG | `jarMissing = 1` | **1** |
| 目标 PNG 被挪到错误路径 | `jarMissing = 1` + `jarUnexpected2dPng = 1` | **1** |
| 目标 PNG 内容被换成另一张（同字节存在但内容错） | `jarMismatch = 1` | **1** |
| JAR 内删掉 1 张 UI 图标 | `jarMissing = 1`（`.../textures/ui/spinning_slash.png`） | **1** |
| 保护清单被改哈希 | `protectedChanged = 466` | **1** |

退出码证据：[audit-r2-negative-exit-codes-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/audit-r2-negative-exit-codes-20261016.log)。保留口径未变：五件用户选版与全部 94 张发布 PNG 不得改（修姿态/UV 不许动 PNG）；黑剑刃口朝外；碎星 front `270c60fe9edff3b0de34263dc2251da19f4fc47995f9c585646537e6ce009a43`；弩炮 display 覆盖 `daefa157e7bb37dcd89bbb2e54863bda31b1bdec948d4a71c2d80d7b1a18dcf0`；正式 `modpack/mods.lock.json` `e2795047bf804deff9595c03ee24f85bc75ec8518222809c532aa94a661647bf`；Spartan 提供方 JAR `84d3ddafb519e2f5e03f2681ff52c30c92c9512181f61a7a7487399a0179888f`。

## 4. 全部命令终态

| 命令 | 退出码 | 终态标记/结果 | 证据 |
|---|---|---|---|
| `gradlew test`（R1 后对最终源码**真实执行**） | **0** | `Task :test` executed；31 套 / 207 项 / 0 失败 / 0 错误 / 1 跳过 | [test-final-r1r3-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/test-final-r1r3-20261016.log)、31 份 TEST-*.xml（20:51 时间戳） |
| `gradlew jar`（R1 后重建） | **0** | `Task :jar UP-TO-DATE`，SHA 不变 | [jar-rebuild-after-r1-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/jar-rebuild-after-r1-20261016.log) |
| A `runClient -PclientSmoke -PgripSmoke -PgripItem=flail -PartAcceptance` | **0** | `ART_FLAIL_COMPLETE: real inventory, ItemEntity and ItemFrame; provider absent fallbacks=10` | [a-final-gradle-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/a-final-gradle-20261016.log)、[a-final-client-latest.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/a-final-client-latest.log)、9 张 `a-*.png` |
| B `runClient -PgracePrototype -PgraceMenu -PartAcceptance` | **0** | `GRACE_ART_COMPLETE: bind/replace/remove snapshots, unowned/incompatible/invalid/stale token refusal; GUI scales 1 and 2; engine mouse dispatch, not physical mouse acceptance` | [b-grace-menu-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/b-grace-menu-20261016.log)、5 张 `b-grace-art-*.png` |
| C `runClient -PclientSmoke -PproviderAcceptance -PartAcceptance`（R1 后重跑，含 overlay 等待） | **0** | `ART_PROVIDER_FALLBACK_FRAME: loading overlay gone, 10 overlay-free frames with screen=InventoryScreen` + `ART_PROVIDER_COMPLETE: 10 real provider routes, longbow states, broken resource fallback and reload recovery` | [c-provider-gradle-r1-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/c-provider-gradle-r1-20261016.log)、[c-provider-acceptance-r1-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/c-provider-acceptance-r1-20261016.log)、`c-*.png` |
| 门禁负例 `runClient -PgracePrototype -PartAcceptance`（不带 `-PgraceMenu`，永不产生 `GRACE_ART_COMPLETE`） | **1** | `> Art acceptance fixture failed or incomplete; inspect isolated log` + `BUILD FAILED in 35s`；同一轮客户端日志只有 `GRACE_PROTO_CLIENT_COMPLETE` | [gate-negative-gradle-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/gate-negative-gradle-20261016.log)、[gate-negative-client-latest-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/gate-negative-client-latest-20261016.log) |
| 资源审计（正式包） | **0** | 102 目标全过 + 466 保护零变化 | [audit-r2-targets-102-negative-samples-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/audit-r2-targets-102-negative-samples-20261016.log) |
| 资源审计（5 个坏样本当正式包跑） | **1 × 5** | 见第 3 节表 | [audit-r2-negative-exit-codes-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/audit-r2-negative-exit-codes-20261016.log) |

门禁实现：`build.gradle` 的 `runClient` `doLast` 在 `-PartAcceptance` 下读取本次隔离运行的 `build/<夹具目录>/logs/latest.log`，缺完成标记或出现 `ART_ACCEPTANCE_FAIL` / `GRACE_ART_FAIL` 即抛 `GradleException`；`latest.log` 每次运行轮转（历史在 `logs/*.log.gz`），不会串到上一轮。

## 5. A：连枷与缺提供方回退

- 6 个持握场景（一三人称左右主手、第三人称副手）保持：`CLIENT_GRIP_2D` 连枷右/左握点到掌心 0.0513 格，与原版对照件差 0.0176 格。
- 连枷自身走新 2D 路径：`ART_DRAW: eldencombat:flail|<上下文>|eldencombat:item/2d/flail_front` 覆盖 `THIRD_PERSON_RIGHT/LEFT_HAND`、`FIRST_PERSON_RIGHT/LEFT_HAND`、`GROUND`、`FIXED`（quads=82，单 pass `SimpleBakedModel`），GUI 用 `eldencombat:item/2d/flail_gui16`（quads=44）；实际深度等于目标深度（GUI 的 1.0 含 GUI 绘制缩放，不能当世界格宽）。
- 缺 Spartan 时 10 件 `providerItem` 全部回退旧路径：`ART_FALLBACK_DRAW: legacy|eldencombat:{spear,thrusting_sword,katana,halberd,greataxe,hammer,great_hammer,scythe,dagger,long_bow}|GUI`，且没有任何一件误走新路径。
- 首轮失败（`ART_ACCEPTANCE_FAIL: absent provider did not draw legacy: "eldencombat:spear"`）根因是夹具把玩家设为创造、只看到快捷栏 8 件；已改为真实生存物品栏 + 显式测试箭，本轮 10 件回退可见。历史日志保留在 [flail-runtime.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/flail-runtime.log)。

## 6. B：真实赐福菜单与拒绝路径

全部经**真实 `Screen.mouseClicked` → 现有客户端发送口 → 服务端 → 新快照**闭环：

- 未拥有：三个战灰按钮均为禁用文案 `踏前上斩/重刺/回旋斩 · 未获得`（3× `GRACE_ART_DISABLED`）；故意发送未拥有请求被真实网络拒绝，`notice='尚未获得此战灰'`，整件物品及组件不变。
- BIND：已拥有且兼容 → 真实点击 → 服务器写组件 → 新快照 → `踏前上斩 · 已绑定` 且禁用（`GRACE_ART_BIND_PASS`）。
- REPLACE：`GRACE_ART_REPLACE_PASS`（**夹具显式种入“旧的不兼容绑定”**，见第 8 节边界）。
- REMOVE：`GRACE_ART_REMOVE_PASS`。
- 拒绝路径：不兼容 `notice='无法装配：当前主手武器不支持战灰'`；无效 token 与过期 token 均为 `notice='主手武器已变化（换手或换槽），已刷新，请重试'`；同槽换入另一件相同类型/组件物品后旧 token 失效，并有 `GRACE_ART_TOKEN_ROTATION`。每次拒绝都先清空 notice 并等到新 revision 再发送，再观察**本次应答之后**的新 revision——不复用上一相位的遗留 notice。
- 图标与缩放：`ART_UI_ICON` 在 guiScale 1.0 用 16px 源、guiScale 2.0 用 `_32` 源，三枚战灰图标与 FP 图标均记录；截图覆盖缩放 1/2。

## 7. C：真实 Spartan、动态状态、坏资源恢复（含 R1 视觉补正）

- 实际加载：Mod List 含 `Spartan Weaponry Unofficial 1.2.3 (spartan_weaponry_unofficial)`，按精确 SHA 加入（`providerAcceptance`）。
- 10 条 `ART_PROVIDER_REGISTRY` 全部命中真实注册 ID 与真实类（如 `eldencombat:long_bow -> spartan_weaponry_unofficial:longbow_iron_strengthened actual=…item.LongbowItem`，其余为 `SwordBaseItem`）。
- 长弓动态状态：用**真实持续按住使用键**（`ART_PROVIDER_USE_START: keyUse held down; real input path, not a one-shot useItem`）驱动，`ART_PROVIDER_USE` 记录 `clientUsing=true`、`pull 0.1→0.9`；`ART_PROVIDER_STATES: all 4 usable provider states actually drawn: [longbow_iron_strengthened, _pulling_0, _pulling_1, _pulling_2]`（每态都有对应 `ART_DRAW`，不用 JSON/图集存在代替）。
- 坏资源恢复：真实装包 `file/art-acceptance-bad.zip` 并重载 ResourceManager 后，新路径拒绝接管，只出现 `ART_FALLBACK_DRAW: legacy|eldencombat:long_bow|{GUI,FIRST_PERSON_RIGHT_HAND,THIRD_PERSON_RIGHT_HAND}`；取消该包再次重载后 `ART_DRAW: eldencombat:long_bow|GUI|spartan_weaponry_unofficial:item/longbow_iron_strengthened` 重新出现。
- **R1 视觉补正**：两张关键截图原先截到红色 Mojang 资源加载界面（overlay 未撤）。现改为：等 `mc.getOverlay()==null` 且目标界面存在并**连续 10 帧**有效渲染（日志 `ART_PROVIDER_FALLBACK_FRAME: loading overlay gone, 10 overlay-free frames with screen=InventoryScreen`）→ 发起截图 → 等 `Screenshot.grab` 的**落盘回执**（`ART_CAPTURE: … Saved screenshot as …`）→ 再进入下一步。新图 [c-art-provider-bad-resource-fallback.png](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/c-art-provider-bad-resource-fallback.png)、[c-art-provider-restored.png](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/c-art-provider-restored.png) 都是真实物品栏画面；[快捷栏 3 倍放大对比](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/c-hotbar-fallback-vs-restored-zoom-3x.png)（上=回退帧，下=恢复帧）可看出长弓槽由缺纹理的残影变为完整弓形。旧的无效图保留但改名标记：`c-art-provider-*-INVALID-mojang-loading.png`。
- 早期两次 C 失败也已定位：run1 是固定时间窗（age≥50）判定导致漏看中间态，改为按“每个可用状态是否真被绘制”等待；run2 一次性 `useItem` 无法维持拉弓（`using=false elapsed=0`），改用真实按住使用键。

## 8. 诚实边界（未验项）

- 提供方验证范围（R3 校准）：10 件提供方只有 **GUI** 上下文记录；只有 `long_bow` 另有 `FIRST_PERSON_RIGHT_HAND`（5 条，含 4 个状态）与 1 条 `THIRD_PERSON_RIGHT_HAND`（depth 1.494，来自物品栏界面的玩家预览渲染）。**其余 9 件的手持上下文未验**，不宣称通过；全装备手持矩阵留待后续验收。
- 长弓链在本版为 `SimpleBakedModel` 单 pass，据实记录，**未验证多层或自定义 loader**。
- 图示/JSON/图集“存在”不等于状态被绘制——本轮一律以实际 `ART_DRAW` 记录判定。
- `Screen.mouseClicked` 是引擎层鼠标事件分发，证明真实控件与网络闭环，**不等于人类物理鼠标操作与最终观感验收**；截图不能代填人工观感。
- REPLACE 的“两个战灰替换”用显式种入的不兼容旧绑定测传输/写入链；三战灰按组互斥，**没有一把正常武器同时兼容两种战灰**，故正常玩法下的两兼容战灰替换未验，未为此扩大白名单或伪造第四种战灰。
- 独立 JAR 安装、专用服务器、多人、光影、PCL 新实例导入、人类最终观感均**未验**；本轮门禁不等于整个整合包完成。
- 测试口径：统筹复核指出的“排除后重新执行全量测试”措辞已修正——`test-postexcl-20261016.log` 那次是 `Task :test UP-TO-DATE`（复用 19:55 的既有结果）。本文件第 4 节的 207 项是 R1 之后**真实执行**的一次（20:51，`Task :test` executed，31/207/0/0/1），两份日志都在证据目录。
- 本轮未安装任何模组、未替换 Combat Roll、未动遗迹奖励。

## 9. 证据目录

统一证据目录：[art-acceptance-closure-20261016](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016)：A/B/C 三份客户端日志与三份 Gradle 日志、门禁正负例、资源审计（正式包 + 5 个坏样本 + 退出码）、21 张有效截图（另 2 张已标记 INVALID）、31 份 JUnit XML、包/保护/源码哈希。改动前备份：[20261016-art-acceptance](D:/codexout/mcmod/docs/code-backups/20261016-art-acceptance)；本轮编辑后快照：`20261016-art-acceptance-dsh`。

## 10. R1–R3 补正记录（对应统筹复核）

| 项 | 复核要求 | 本次做法 | 证据 |
|---|---|---|---|
| R1 | 补 C 回退/恢复有效画面：等加载 overlay 消失、目标界面恢复、至少一个有效渲染帧，异步截图确实保存后再结束；保留真实 fallback/恢复日志；等待有上限 | `SelectedArtAcceptanceHarness` 相位 3/4 改为"overlay 消失 + 连续 10 帧 + 目标界面"判定，截图后等 `Screenshot.grab` 落盘回执才继续/退出；整体仍受 `age>400` 超时与 `ART_ACCEPTANCE_FAIL` 约束；C 重跑退出 0 | [c-provider-acceptance-r1-20261016.log](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/c-provider-acceptance-r1-20261016.log)、两张新图 |
| R2 | 审计按当前路由/清单逐目标 JAR 路径+内容校验；缺 UI 仍列预期并记缺失；缺失/差异/保护变化非零退出；用隔离坏样本证明不假绿 | 脚本重写为路由驱动（94 装备 + 8 UI = 102 目标逐路径比对），加 2D 命名空间多余 PNG 检查与清单互校；`exit 1` 失败门禁；5 个坏样本（含删 UI 图标、挪路径、换内容、保护清单改哈希）全部判失败且退出 1 | [审计日志](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/audit-r2-targets-102-negative-samples-20261016.log)、[退出码](D:/codexout/mcmod/docs/combat-evidence/art-acceptance-closure-20261016/audit-r2-negative-exit-codes-20261016.log) |
| R3 | 校准措辞：10 件仅 GUI、长弓另有一人称右手与四状态；单 pass 边界；UP-TO-DATE 不得写成重新执行 207 项；两张加载图记为无效视觉证据并补图 | 本文件第 4/7/8 节改写；旧图改名 INVALID；测试口径如实分列（19:55 真实执行 + postexcl UP-TO-DATE + R1 后 20:51 真实执行） | 本文件 + 第 9 节列出的日志 |

只读性质保留：审计脚本与坏样本只读正式素材、只写 `build/`；未改 PNG、未改正式 JAR、未改 mods.lock。

## 11. 后续队列

按 [DSH-EXECUTION-INDEX](D:/codexout/mcmod/docs/DSH-EXECUTION-INDEX-20261016.md)：R1–R3 完成并核对命令终态后，下一项是 [八技 VFX 运行框架](D:/codexout/mcmod/docs/DSH-VFX-RUNTIME-FRAMEWORK-20261016.md) 的运行收口（相机眼位/头 yaw/构图、主贴片与必要碎块、火墙遮挡与视锥、真实顶点提交、正常缺资源与非法 JSON 分别降级恢复，不重画 160 帧、不改命中/FP）。其后：赐福/地图视觉与普通战灰获取、用户新增模组与 Combat Roll、全包真实交付。以上均未启动。
