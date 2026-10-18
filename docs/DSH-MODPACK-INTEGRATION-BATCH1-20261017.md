# 第一批现成模组整合：候选与最小联调合同

2026-10-17。响应人类“推进模组整合”。本轮完成精确锁、官方文件下载、静态依赖核对与隔离目录；具体 Java、构建脚本适配和运行联调交 DSH。本文不是启动 DSH 的消息，也不是扩大实现授权；仍由统筹按当前串行规则派工。

## 当前交付

- 候选锁：`docs/research-evidence/modpack-integration-20261017/candidate.lock.json`，含官方 URL、文件长度、SHA1/SHA512、两端要求、项目与版本 ID。
- 客户端暂存：`build/modpack-integration/client/`，14 个第三方 JAR + 本项目 JAR，共 15 个。
- 专服暂存：`build/modpack-integration/server/`，9 个必需第三方 JAR + 本项目 JAR，共 10 个。移除 Sodium、Iris、Entity Culling、Simply Tooltips；JEI 为可选，不放专服。
- 导入候选：`dist/integration-20261017/EldenCombat-0.2.0-integration-candidate-20261017.mrpack`。仅供新隔离实例联调，未通过启动、导入、多人或生存获取验收；不是正式发行。
- 本项目 JAR：`eldencombat-0.2.0.jar`，SHA256 `9698c73d011545cd3843f18e2b57cb07e3f73875e751a41e6c47f1cc601e22e2`，包内无 Harness。
- 正式 `modpack/mods.lock.json` 保持原七项，SHA256 `e2795047bf804deff9595c03ee24f85bc75ec8518222809c532aa94a661647bf`；未改源码、构建脚本、存档或美术。隔离目录没有游戏运行时或启动器，不能直接双击运行。

## 新增精确文件

| 模组 | 版本 | Modrinth 版本 ID | 两端 |
| --- | --- | --- | --- |
| Simply Swords | 1.70.2-1.21.1 | nS0Yahr5 | 必需 |
| Dungeons and Taverns | v4.4.4+mod | BYUUUeZA | 本候选两端安装；作者允许客户端可选 |
| Lootr | 1.21.1-1.11.38.127 | SPOrIzX8 | 必需 |
| Architectury API | 13.0.11+neoforge | 1IiqEQGl | 必需 |
| Fzzy Config | 0.7.7+1.21+neoforge | uG7oHgw6 | 必需 |
| Simply Tooltips | 0.1.5 | Zb6dytvm | 仅客户端 |
| Kotlin for Forge | 5.12.0 | uhJhCT7X | 本组合两端必需 |

沿用七项基础模组，未加 Better Combat/Epic Fight、Simply More、Combat Roll、脚本栈、灾变或新魔法祷告。More 在基础组合通过后另加；Combat Roll 等 DSH 单输入/单结算适配完成后再进批次。

Fzzy 发布 JAR 明确要求 `kotlinforforge [5.3.0,)`，本次已补齐 5.12.0；不能用项目首页当前的其它 Kotlin 方案替换。其内部 kffmod 元数据支持 MC `[1.20.6,1.21.9)`。Lootr 要求 NeoForge `[21.1.195,)`，现有 21.1.219 满足；原 FerriteCore 要求 `[21.1.218,)`，因此不可随意降级 NeoForge。

## 静态证据与限度

入口 `docs/research-evidence/modpack-integration-20261017/verification.json`：14 个第三方文件官方 SHA512/长度与 ZIP 完整性通过；连同嵌套 JarJar，55 条必需依赖声明 ID 有提供者，客户端/专服分别无必需 ID 缺口。版本范围已逐项静态核对，不能替代实际加载。

`jar-metadata.json` 保存实际 JAR 的 NeoForge 元数据及嵌套依赖；`required-dependencies.json` 保存每条解析与范围结果。两个基础模组（Iris、JEI）的 MC 范围上界写成排除 1.21.1；当前 FML 4.0.42 的 `VersionSupportMatrix` 对 MC 1.21.1 同时接受 1.21 兼容范围，依据本机真实 loader 的 `loader-VersionSupportMatrix.javap.txt`。这是加载器内建兼容机制，不是忽略依赖错误或修改上游 JAR。本批仍须以实际启动结果确认。

Simply Swords 的 `LootConfig` 和 `ModLootTableModifiers` 发布版字节码已存入同目录 `*.javap.txt`。可核实 `enableLootDrops`、`enableLootInVillages`、`uniqueLootTableOptions`、`uniqueHardPity` 等真实字段；默认启用掉落、关闭村庄掉落。普通武器注入检查路径包含 `chests`，D&T 有相应表；**不能因此认定每个 D&T 宝箱均支持独特武器或每玩家保底**。先验证原生行为，确有缺口才改生成配置或普通数据包，不新写 Java 奖励框架。

## 已确认的代码冲突与最小接口边界

`ClientCombat.onInteractionKey`（当前约 202 行）在 `combatMode=true` 时无条件取消左键并发送 LIGHT。`Weapons.classify`（约 40 行）只认本项目数值档或本项目标签；`ServerCombat.startAttack`（约 353 行）对 NONE 直接返回。因此第三方武器若不在这些档/标签，开启本项目战斗模式会被拦截且无法按原生方式普攻。这是源码可证明的条件性冲突，尚未在组合实例复现。

DSH 修复合同：

1. 在现有 Weapons 识别层提供一个客户端/服务端一致的“本项目是否接管近战输入”判断，按真实数值档/既有受控标签判断，不将所有 SwordItem、所有外来命名空间或所有第三方武器批量纳入。具体命名可沿用现有风格，无需新路由框架。
2. 第三方武器未接管时，客户端不取消其原生左键，不发送替代 LIGHT/HEAVY；服务端也拒绝伪造的本项目近战结算。不要简单取消全部意图，误伤翻滚、喝药、锁定、赐福或已有效的追忆追击窗口。
3. 第三方武器保留原生右键/副手交互、费用、冷却和技能表现；本项目 C 键不能附带扣 FP 或造成第二份伤害。没有已验证映射时，不宣称已支持通用可换战灰。
4. 修复只围绕可重现冲突；保留现有自有武器、盾、追忆及存档兼容，不改 PNG、VFX 路由、数值体系，不重做成熟模组能力。

## DSH 一次性联调顺序和交付条件

1. 核对两部门真实回合及在途命令已收束，再启动本工单。从候选锁读取精确版本，禁用 latest；不要先覆盖正式七项锁。
2. 最小修改现有 Fetch-Mods/Build-Modpack/packSmoke，使它们可以显式使用候选锁与独立游戏目录。消除只支持七项的硬编码时，保留哈希、平台、side、Mixin 和 Harness 检查，按当前真实客户端 Mixin 名单精确验；不要删门禁或运行旧 Fetch-Mods 把候选退回七项。
3. 在新目录使用候选 JAR，项目模组只能加载一次。开发运行从源码加载时勿同时放入本项目 JAR；独立发行验证必须用这里的实际 JAR。专服不要带 Simply Tooltips 或其它客户端独占模组。
4. 集中做客户端加载/新世界、专服加载/同包客户端连接、原版动作与输入测试；记录每次进程的正常结束或明确失败。修好上述输入冲突后再作同批回归，避免每改一行重启游戏。
5. 原生能力至少验证一把普通武器、一把带主动能力武器，R 关/开各一次：左键伤害、右键/副手、C 键、切武器、死亡/重连；无双重伤害或错误 FP 扣费，自有追忆仍正常。先查发布版能力实际触发条件，不假定所有武器都用同一按键。
6. 在新生成区块找到一个实际 D&T 遗迹及带原始 loot table 的箱；验证自然生成和原生武器来源。双玩家分别开同一 Lootr 箱，第二人可独立领取、第一人重开不重复领取，重启后已领状态保留。命令刷物只能用于诊断，不证明自然来源；概率掉落不能用一次空箱判失败。
7. 将通过的生成配置/必要数据包收回 overrides，候选包从新启动器实例导入并验证依赖数、版本、默认原版动作；最后才审查正式锁提升。交付源码 diff、精确锁、打包哈希、客户端/专服终态日志和自然来源/双人箱证据；机器通过与人工观感分别标注。

本轮不开展运行循环、不自动唤醒 DSH。上述运行与实现项目均为后续待验，不计入当前通过项。
