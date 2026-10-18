# 模组整合实施交接（Codex 落地版）

2026-10-16。**实施方改为 Codex**：本文给出可直接照做的文件级任务、判据与证据要求。

- 本文**不改变**验收合同：`docs/DSH-MODPACK-ADDITIONS-HANDOFF-20261016.md`（下称《合同》）仍是验收依据，本文只把其中 §2/§3/§4/§5/§6 的实施方从 DSH 改为 Codex，并把"要做什么"细化到文件与行为。
- 上游依据：`docs/MODPACK-ADDITIONS-WORK-ORDER-20261016.md`（工单）、`docs/MODPACK-ADDITIONS-AUDIT-20261016.md`（审计矩阵）、`docs/MODPACK-ADDITIONS-CANDIDATE-LOCK-20261016.json`（30 条候选锁）。
- 取证目录（**不随包发布**）：`docs/research-evidence/modpack-additions-20261016/`，内含 23 个待装 JAR（`jars/`）、每个 JAR 的 `*.jar.toml` 摘录、Combat Roll 的 `combatroll-2.0.6-javap.txt` 与分支源码摘录。

## 0. 分工（一句话）

**Codex 写代码、跑离线验证与开发环境试装；用户跑真实实例与真人验收；装备/VFX 部门不参与本轮。**

| 谁 | 做什么 | 不做什么 |
|---|---|---|
| Codex | 改 `modpack/*.ps1`、加锁驱动的选择层与侧闭包、写 Combat Roll 接线的 Java、改夹具/单测、写回执草稿 | 不碰装备 2D/VFX 代码与路由；不动用户游玩实例；不改第三方 JAR |
| 用户 | 决定 §16 的三个待定项；跑真实启动器实例、中文输入、光影、性能对照、人眼验收 | —— |
| DSH/装备部门 | 只提供本文件所述的既有事实与锚点 | 本轮不再改任何东西 |

## 1. 红线（违反即视为未交付）

1. **不改第三方 JAR**、不打包未获分发准入的字节、不用非官方镜像补包、不引入 Fabric/旧 Forge 版本或兼容桥。
2. **不覆盖用户游玩实例**；一切下载/解压/试装只在工作区内的隔离目录（`build/modpack-batches/<batch>/`、`build/pack-batch-<x>/`）。旧存档、武器注册 ID、战灰/FP/成长/护符/赐福的服务端权威不得改动。
3. **不把候选锁当安装清单**：`...CANDIDATE-LOCK-20261016.json` 的 `kind` 是 `audit_candidate_not_installable_release`，30 条 `installApproved` 全为 `false`。必须由新的**选择层**逐条显式批准。
4. **不在验收前改正式锁** `modpack/mods.lock.json`（当前 SHA256 `e2795047bf804deff9595c03ee24f85bc75ec8518222809c532aa94a661647bf`，与候选锁 `baselineLockSha256` 一致）。只有该批已运行验收通过时才允许 `-Promote`。
5. **不删检查让打包变绿**：可以改判据（从写死改成锁驱动/声明式），不可以删掉来源、哈希、体积、modid、侧闭包、归档白名单校验。
6. **不虚构 API**：Combat Roll 2.0.6 里**不存在**可取消的 pre-roll 事件；`ServerSideRollEvents.PLAYER_START_ROLLING` 是事后通知（`handleRollPublish` 里已先设无敌、加饥饿再通知）。无法可靠接入时**明示阻断**，不许用"事后事件假装完成"。
7. **不把"未选替代品"算作完成**：`Armor Toughness Bar, Again`（`toughnessbar`，候选锁 idx 9）默认**不启用**；`Mod Menu`、`LazyDFU Forge` 保持阻断记录。
8. **本项目没有 git**（`D:\codexout\mcmod` 非仓库）→ 改任何文件前先备份到 `docs/code-backups/20261016-modpack-<tag>-NNN/`（沿用现有备份目录习惯），回执里写清备份路径。
9. **脚本必须能在 Windows PowerShell 5.1 下运行**（用户环境没有 `pwsh` 7，本文件的命令均实测于 5.1.26100）。见 §9.4 的既存缺陷。

## 2. 现状事实（已实测，可直接复现；Codex 开工前先自证一遍）

| # | 事实 | 复现 |
|---|---|---|
| F1 | 打包脚本**当前就失败**：`Unexpected pose mixins`（退出码 1） | `cd D:\codexout\mcmod; & .\modpack\Build-Modpack.ps1 -OutputDirectory build/tmp-packcheck` |
| F2 | 原因：`Build-Modpack.ps1:35` 要求 `client` 恰为 `['PlayerModelPoseMixin']`，而 `src/main/resources/eldencombat.mixins.json:6` 声明 3 条：`PlayerModelPoseMixin`、`JourneyMapSizeMixin`、`ItemRendererEquipment2dMixin`（三者都有对应 `.class` 在 `build/libs/eldencombat-0.2.0.jar`） | 对比上述两处 |
| F3 | `Build-Modpack.ps1:23` 的 `[Convert]::ToHexString` 在 PS 5.1 不存在 | `try{[Convert]::ToHexString([byte[]](1))}catch{$_.Exception.Message}` |
| F4 | `Fetch-Mods.ps1:7–15` 写死 7 个 pin；只允许 `cdn.modrinth.com`；`:33` **直接重写** `modpack/mods.lock.json` | 读脚本 |
| F5 | 锁有**第三个消费者**：`build.gradle:376–378`（`-PpackSmoke` 时按 `mods.lock.json` 的 `env.client/server != unsupported` 过滤，从 `build/modpack-dependencies/` 取 JAR 进开发运行时） | 读 `build.gradle` |
| F6 | 候选锁 30 条：24 条 Modrinth（`platform` 缺省、下载域 `cdn.modrinth.com`）、6 条 CurseForge；`env.server` 分布 `required 19 / unsupported 9 / optional 2`；`localHashVerified` 仅 17 条为 `true`（CF 条目的哈希来自**观测到的官方 CDN 字节**，无独立平台哈希，锁内 `hashProvenance` 已注明） | 读候选锁 |
| F7 | 候选锁前 7 条（= 正式锁）**没有 `modId`/`slug` 字段**；后 23 条有 | 读候选锁 |
| F8 | 现有 7 项依赖已下载并校验在 `build/modpack-dependencies/`（7 个 JAR）；`dist/` 只有 0.1.0 产物，0.2.0 包从未成功生成 | 列目录 |
| F9 | 包内 `overrides/THIRD-PARTY.md` 已过时：仍列 Epic Fight（项目已移除该依赖），且不含任何新模组 | 读 `docs/THIRD-PARTY.md:9,18` |
| F10 | `modpack/overrides/options.txt` 只有 `version/lang/autoJump/renderDistance/simulationDistance`，**不含任何键位** | 读文件 |
| F11 | `modpack/overrides/README-zh.md` 是 GBK 编码（按 UTF-8 读是乱码）→ 改动前先确认编码，别整文件重写 | 读文件 |
| F12 | 候选锁 29 号 `fastleafdecay` 的 `env.client = optional_integrated_server`（单机集成服需要，纯专服包不需要它在客户端） | 读候选锁 |

## 3. Combat Roll 侧已核实的硬事实（写 Java 时的依据）

- 目标：`mods/combat_roll-neoforge-2.0.6+1.21.1.jar`（Modrinth `FT7t1n1a`，`modId=combat_roll`）。**最终类签名以 `docs/research-evidence/modpack-additions-20261016/jars/combat_roll-neoforge-2.0.6+1.21.1.jar` 的 javap 为准**；`combatroll-*.java.txt` 是分支源码（Yarn 命名），只作语义参考。
- 服务端权威入口（javap 实测）：
  `public static void handleRollPublish(net.combat_roll.network.Packets$RollPublish, net.minecraft.server.MinecraftServer, net.minecraft.server.level.ServerPlayer)`，类 `net.combat_roll.network.ServerNetwork`。
  其内部顺序：先向 tracking 玩家广播 `RollAnimation` → 再 `executeSync`：`setRollInvulnerableTicks(invulnerable_ticks_upon_roll)` → `addExhaustion(exhaust_on_roll)` → 触发 `PLAYER_START_ROLLING`（`PlayerStartRolling.onPlayerStartedRolling(ServerPlayer, Vec3)` **返回 void、不可取消**）。
  ⇒ 拦截点必须在 `handleRollPublish` 的 **HEAD**，才能同时挡住广播与副作用。
- 客户端：`net.combat_roll.client.Keybindings.roll`（翻译键 `keybinds.combat_roll.roll`，默认 `GLFW_KEY_R`）；`net.combat_roll.internals.RollManager`（`isEnabled/isRollAvailable/isRolling/tick` 等，具体签名自行 javap）。`RollManager.isEnabled` 等是**客户端**状态，不构成服务端权限闸门。
- 已固定的默认行为（首批不改）：`invulnerable_ticks_upon_roll=0`、`roll_duration=8 tick`、`roll_cooldown=4s`、`exhaust_on_roll=0.1`、`food_level_required=6`、`allow_jump_while_rolling=false`、`allow_rolling_while_airborn=false`、`allow_rolling_while_weapon_cooldown=false`、`allow_auto_jump_while_rolling=true`、`additional_roll_distance=0`。**不新增 FP/精力消耗，不启用任何无敌帧。**

## 4. 交付物清单（文件级）

| # | 文件 | 动作 | 内容 |
|---|---|---|---|
| D1 | `modpack/ModpackLock.psm1` | 新增 | 共享模块：读选择层、校验、平台准入、哈希校验、侧闭包、plan/receipt 输出。全部纯函数，可离线自测 |
| D2 | `modpack/locks/batch-a.json` … `batch-e.json` | 新增 | 每批一条**选择层**（§6 schema）；A–D 为增量批，E 为整合批 |
| D3 | `modpack/Fetch-Mods.ps1` | 重写 | 参数化、锁驱动、双来源准入、隔离输出、`-Plan` 默认只读、`-Promote` 显式晋级 |
| D4 | `modpack/tests/ModpackLock.Tests.ps1` + `Run-ModpackTests.ps1` | 新增 | 侧闭包/校验/准入的离线用例（§8 给出必须覆盖的 12 个用例） |
| D5 | `modpack/Build-Modpack.ps1` | 重写 | 逐条去掉写死判据（§9），集合驱动、回执按真实装入/排除/未验生成 |
| D6 | `modpack/pack-expectations.json` | 新增 | 声明式打包期望：必需资源清单、依赖 modId 白名单、mixin 声明来源、proportions/grip/skill 计数及其**来源**说明 |
| D7 | `build.gradle:376–378` | 改 | 加 `-PlockFile=` / `-PmodsDir=`（默认值保持现状），供批次试装复用第三消费者 |
| D8 | `build.gradle`（run 配置区） | 加 | `-PpackBatch=<x>` → `gameDirectory build/pack-batch-<x>`，批次隔离运行目录 |
| D9 | `src/main/java/com/dsh/eldencombat/server/RollGate.java` | 新增 | 翻滚准入的**单一规则源**（客户端预测与服务端准入共用） |
| D10 | `src/main/java/com/dsh/eldencombat/mixin/CombatRollAdmissionMixin.java` + `CombatRollMixinPlugin.java` + `src/main/resources/eldencombat.combatroll.mixins.json` | 新增 | 服务端窄 hook（`handleRollPublish` HEAD，可取消；`required:false` + 插件按 `combat_roll` 是否加载决定是否应用） |
| D11 | `src/main/java/com/dsh/eldencombat/mixin/CombatRollClientGuardMixin.java`（同一 mixins.json 的 `client` 数组） | 新增 | 客户端预测拦截（RollManager 起滚 tick 的 HEAD，按本地 `RollGate` 判定） |
| D12 | `src/main/java/com/dsh/eldencombat/client/ModKeyMigrations.java` | 新增 | 一次性键位迁移（§11.2）：把 Combat Roll 默认 R 改为左 Alt，尊重用户自定义，只做一次 |
| D13 | 旧翻滚入口改造：`client/EldenCombatClient.java`、`client/ClientCombat.java`、`server/ServerCombat.java`、`network/CombatStatePayload.java`（仅注释/空写）、夹具与单测 | 改 | 见 §11.1、§12 |
| D14 | `docs/THIRD-PARTY.md` | 改 | 删 Epic Fight 行、补本批实际装入的第三方项目与许可证（§9.5） |
| D15 | `docs/modpack-receipts/BATCH-<X>-<YYYYMMDD>.md` + `docs/modpack-evidence/<batch>-<date>/` | 新增 | 每批回执与证据（§13 模板） |

## 5. 批次编排（**严格串行**，一批关停实例并确认命令结束后再开下一批）

| 批 | 内容（`modId`） | 侧 | 关键验收（《合同》§5 的落地） |
|---|---|---|---|
| A | 保留 7 项 + `itemborders`(+`iceberg``prism`) + `sophisticatedbackpacks`+`sophisticatedcore` + `dummmmmmy`(+`moonlight`) + `overloadedarmorbar` + `fpsreducer` + `imblocker` + `enchdesc`(+`bookshelf``prickle`) + `fastleafdecay` | 客户端为主；`fastleafdecay` 进专服/集成服侧 | 界面可读；背包组件保存/放置/携带/重登/真实重启/死亡；假人普通攻击与战技**真的掉血**；叶片自然衰减；无重复 modid；客户端专属项不进专服 |
| B | `alexsmobs`(+`citadel`) + `alexsdelight`(+`farmersdelight`，两条 `extraDependencyEdges`） | C/S | 新测试世界自然生成；Alex's Delight 掉落→食材→配方闭环；错 Citadel 版本被拒；旧世界备份后升级/回退 |
| C | `combat_roll`(+`playeranimator`) + D9–D13 的 Java | C/S | 同轮证明：旧入口关闭、左 Alt 可滚、R 只切模式；服务端准入拒绝硬阶段；默认 0 无敌下正常受伤；无新增 FP/精力扣费 |
| D | `memorysweep`(+`architectury`) | C/S | 同世界/同 JVM/同视距/同路线/同时长，开/关各跑一次，记录 GC 日志与帧时间 p50/p95/p99 |
| E | A–D 全部 + 新实例导入 | C/S | 独立客户端 + 专服加载列表；PCL 新实例导入成功、来源与哈希可复核；光影/Entity Culling 开关不隐藏武器/战技/假人/新生物 |

每批顺序：`-Plan`（只读）→ 备份 → 下载/校验 → 离线自测 → 开发环境试装 → 真实实例验收（用户）→ 回执 → （通过才）`-Promote`。

## 6. 选择层 schema（D2；这是"批准"的唯一载体）

候选锁**只读不改**。选择层按 `ref` 引用条目，`ref` 对 Modrinth 条目是 `versionId`，对 CF 条目是 `"<project>:<fileId>"`（F7：前 7 条没有 `modId`，所以**不要用 modId 做引用键**）。

```json
{
  "schemaVersion": 1,
  "kind": "modpack_batch_selection",
  "batch": "A",
  "date": "2026-10-16",
  "minecraft": "1.21.1",
  "neoforge": "21.1.219",
  "java": 21,
  "candidateLock": {
    "path": "docs/MODPACK-ADDITIONS-CANDIDATE-LOCK-20261016.json",
    "sha256": "<写入时实测>",
    "kind": "audit_candidate_not_installable_release"
  },
  "baselineLock": {
    "path": "modpack/mods.lock.json",
    "sha256": "e2795047bf804deff9595c03ee24f85bc75ec8518222809c532aa94a661647bf"
  },
  "approved": [
    { "ref": "FT7t1n1a", "expectModId": "combat_roll", "side": "both",
      "approval": "user-work-order-20261016", "note": "C 批；含 playerAnimator 前置" }
  ],
  "excluded": [
    { "ref": "9473L0CF", "expectModId": "toughnessbar",
      "reason": "alternative_proposal_not_authorized",
      "note": "Armor Toughness Bar, Again 是替代提案，未获批准，不得计入任何批次" }
  ],
  "extraDependencyEdges": [
    ["alexsdelight", "alexsmobs", "project-declared required; omitted by jar TOML"],
    ["alexsdelight", "farmersdelight", "project-declared required; omitted by jar TOML"]
  ],
  "curseForgeRoute": "manual_checklist",
  "runtimeVerified": false,
  "notes": []
}
```

字段规则（模块必须强制）：

- `approved[*].side ∈ {client, server, both, auto}`；`auto` 表示由 §7 的闭包决定，默认 `auto`。
- `ref` 必须在候选锁里**恰好命中一条**；命中 0 或 >1 条 → 报错退出。
- `expectModId` 非空时，必须与**从 JAR 元数据读出的 modId**一致，否则报错（补齐 F7 的缺口）。
- `approved` 为空、或同一 `modId` 出现两次 → 报错。
- 候选锁里 `blockedRequests` 的三个项目（Mod Menu / LazyDFU / Armor Toughness Bar）**永不允许**出现在 `approved`。
- `curseForgeRoute ∈ {manual_checklist, reference_url}`；默认 `manual_checklist`（§7.2）。
- `runtimeVerified` 只有在该批实际跑通后才能由用户/Codex 手工置 `true`；`-Promote` 要求它为 `true` 且本次所有条目 `verified=true`。

## 7. 侧闭包与来源准入

### 7.1 侧闭包（`Resolve-BatchSides`）

根集合 = `approved` 条目。规则：

1. `env.server == 'unsupported'` → 客户端专属根，**不进专服**（例：Sodium、Iris、EntityCulling、ItemBorders、Prism、FPSReducer、Overloaded Armor Bar、IMBlocker）。
2. `env.server == 'required'` → 两侧都要。
3. `env.server == 'optional'`（`jei`、`enchdesc`）→ **不因它自己进专服**；仅当某个专服根经依赖边需要它时才进。
4. `fastleafdecay`：`env.client == optional_integrated_server` → 单机集成服客户端包**需要**，纯专服包也需要（它在 `server: required`）。
5. 依赖边 = 候选锁 `metadataDependencies`（`dependency_type == required` 的 `project_id` + 本批 `extraDependencyEdges` + JAR `jarDependencyDeclarations` 交叉核对）。**库只有被本侧某个根需要时才随装**：Iceberg/Prism 仅因 `itemborders`(C) 入场 → 只在 C 侧；`iceberg` 的 `env.server` 虽为 `required`，但无服务端根需要它时**不得**仅凭 TOML 就塞进 S 侧。
6. 每条入选条目必须能打印**理由链**（`根 modId →（边）→ 库 modId`），写进回执；无法给出理由的条目视为错误。
7. 任何两侧合并后出现重复 `modId` → 报错退出。

### 7.2 来源准入（`Get-PlatformAdmission`）

| 来源 | 准入条件 | 做法 |
|---|---|---|
| Modrinth（`platform` 缺省且 `downloads[0]` 主机为 `cdn.modrinth.com`） | 允许下载 | 先 `GET https://api.modrinth.com/v2/version/<versionId>`：`game_versions ⊇ {1.21.1}` 且 `loaders ⊇ {neoforge}`，`files[primary].filename`/`.size`/`.hashes.sha512` 必须与锁逐字相同；只从 `cdn.modrinth.com` 下载；落盘后校验 `size + sha512`（锁里有 sha1/sha256 时一并校验） |
| CurseForge（`platform == 'curseforge'`） | **默认不下载字节** | 生成 `MANUAL-FETCH.md`（官方 file 页 URL、`project:fileId`、文件名、字节数、锁内 sha256/sha512、`hashProvenance`），并把该条标为 `acquisition: manual_pending`；若用户已手工放到 `mods/<fileName>` 且 `size+sha512` 匹配 → 记为 `manual_verified_local` 并照常进包；否则该批 **非零退出**，不得静默跳过。`curseForgeRoute=reference_url` 时才允许把 CF CDN URL 当引用写进 manifest，且回执必须标注"无独立平台哈希" |

其它强制项：下载前按 `minecraft`/`neoforge`/版本固定/必需·可选·不兼容依赖范围做文件级证据记录；下载后比较体积、锁哈希、（Modrinth）平台哈希、JAR 实际 `modid` 与 `neoforge.mods.toml` 元数据；**缓存按哈希复用，不得凭同名文件直接认通过**；解压/复制目标必须在隔离根，拒绝路径逃逸与重复覆盖；`api.modrinth.com` 与 CDN 之外的主机一律拒绝（保留重定向后的最终主机校验）。

## 8. 离线自测（D4；无网络、无游戏，`Run-ModpackTests.ps1` 必须退出码 0）

必须覆盖的用例（用合成选择层/锁，不依赖真实网络）：

1. `approved` 为空 → 拒绝。2. `ref` 命中 0 条 → 拒绝。3. `ref` 命中 2 条 → 拒绝。
4. `expectModId` 与 JAR 元数据不符 → 拒绝。5. 重复 `modId` → 拒绝。6. 把 `toughnessbar`/Mod Menu/LazyDFU 放进 `approved` → 拒绝。
7. `approved` 含 `itemborders` 时，C 侧含 `iceberg`+`prism`，**S 侧不含**。
8. `enchdesc`（server optional）单独成批时 → 不进 S 侧；与某个 S 侧根共享依赖时 → 才进。
9. `fastleafdecay` → S 侧必含、C 侧标为集成服需要。
10. 每条入选条目都带非空 `reason`（理由链）。11. `-Plan` 不发起任何网络请求、不改任何文件（除 plan 输出）。
12. `-Promote` 在 `runtimeVerified=false` 时 → 拒绝且 `mods.lock.json` 的 SHA256 不变。
13. 路径逃逸输入（`path` 含 `..`/绝对路径/非 `mods/*.jar`）→ 拒绝。14. CF 条目缺本地文件且 `manual_checklist` → 退出码非 0 且产出 `MANUAL-FETCH.md`。

## 9. `Build-Modpack.ps1` 改造细则（逐条对现状）

| 现状 | 改成 |
|---|---|
| `:7,74` 写死 `$version='0.2.0'` | 从 `build.gradle:7` 的 `version = '...'` 解析（或参数 `-PackVersion`），并与 JAR 内 TOML 的 `version = "<v>"` 交叉验证 |
| `:29` 硬写出 `version = "0.2.0"` | 用上一步的版本变量；`modId=dependencies` 白名单放 `pack-expectations.json` |
| `:34–35` mixin 恰为 `['PlayerModelPoseMixin']`（**当前必然失败**，见 F2） | 以 `src/main/resources/eldencombat.mixins.json` 为**声明源**：JAR 内该文件必须与源码一致；声明集合 == JAR 内 `com/dsh/eldencombat/mixin/*Mixin.class` 集合（多一个/少一个都报错）。允许/必需集合写在 `pack-expectations.json`，但**不得**用"删掉检查"代替适配 |
| `:36–39` 10 个必需资源写死 | 移到 `pack-expectations.json`，并可增补（新增 mod 不改这些） |
| `:42` `proportions.entries.Count -ne 41` | 移入 `pack-expectations.json` 并注明来源；同时加一条 JUnit（§12）把该数字钉在生成物上，防止美术批次漂移后无人发现 |
| `:43–55` grip region 哈希校验 | 保留（这是真检查），改成从 `pack-expectations.json` 读区名列表 |
| `:56` `skills.Count -ne 8` | 同上，声明式 + 单测钉住 |
| `:65` `$lock.mods.Count -ne 7` | 删除计数判据：集合由选择层 + 侧闭包决定；校验"生成集合 == 计划集合"（按 `modId` 比较集合相等），并检查无重复 `modId`、无多项/漏项 |
| `:68` 只允许 `cdn.modrinth.com` | 按 §7.2 的来源准入路由处理；CF 条目默认不打包，`reference_url` 路线才写 manifest 引用 |
| `:75` summary 写死"seven … mods" | 由真实入选条目生成 |
| `:80` scope 写死旧战斗候选文案 | 由选择层生成：本轮**实际装入 / 排除 / 未验**三组清单及理由；不得声称整包完成 |
| `:116` `$index.files.Count -ne 7` | 改为 `== 计划侧集合大小`；`dependencies.neoforge` 与锁一致 |
| `:107` 归档白名单条目数 | 保持"载荷键集合精确一致"检查（这是真检查） |
| 新增 | 回执（§13）与 `SHA256SUMS.txt` 里记录：选择层 sha256、正式锁 sha256（若 `-Promote`）、每批 `modId` 集合、mixin 检查结果、未跑项 |

`build.gradle`（D7/D8）：`-PlockFile=`（默认 `modpack/mods.lock.json`）、`-PmodsDir=`（默认 `build/modpack-dependencies`）、`-PpackBatch=<x>` → `gameDirectory` 改为 `build/pack-batch-<x>`；`-PclientSmoke`/`-PserverSmoke` 的现有行为与装备部门夹具**不得改变**。

## 10. Fetch/Build 的参数与退出码约定

```
# 只读计划（默认模式，无网络、无写入）
.\modpack\Fetch-Mods.ps1 -Selection modpack/locks/batch-a.json -Mode Plan
# 下载与校验（隔离目录）
.\modpack\Fetch-Mods.ps1 -Selection modpack/locks/batch-a.json -Mode Fetch -OutDirectory build/modpack-batches/a
# 离线自测（本机无 pwsh 7，用 powershell 5.1；见 §1 红线 9）
powershell -NoProfile -File .\modpack\tests\Run-ModpackTests.ps1
# 批次隔离试装（开发环境）
.\gradlew.bat runClient -PclientSmoke -PpackSmoke -PlockFile=modpack/locks/batch-a.json -PmodsDir=build/modpack-batches/a/mods -PpackBatch=a --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"
# 打包
.\modpack\Build-Modpack.ps1 -Selection modpack/locks/batch-a.json -OutputDirectory dist
# 晋级（仅在验收通过后）
.\modpack\Fetch-Mods.ps1 -Selection modpack/locks/batch-a.json -Mode Fetch -OutDirectory build/modpack-dependencies -Promote
```

退出码：0 = 全部条目按声明达成；1 = 断言/校验失败（**不产包、不改锁**）；2 = 允许的"未完成"（如 CF 手动获取待办、`manual_pending`）。所有模式都要写机器可读结果：`plan.json` / `fetch-receipt.json`（含每条的 `ref/modId/fileName/fileSize/hash/来源/准入结论/理由链`）。

## 11. Combat Roll 接线的 Java 行为（C 批）

### 11.1 旧翻滚入口的一致停用

| 位置 | 现状 | 要求 |
|---|---|---|
| `client/EldenCombatClient.java:37,99` | 注册 `ROLL`（`key.eldencombat.roll`，默认左 Alt） | **删除该 KeyMapping 与其注册**（`:95–96` 的注释警告过"未注册的 KeyMapping 永远为假"，别留半死字段）；同步删掉 `assets/eldencombat/lang/en_us.json:11`、`zh_cn.json:11` 的 `key.eldencombat.roll` |
| `client/ClientCombat.java:162–165` | `while (ROLL.consumeClick())` → `send(IntentType.ROLL, …)` | 整段删除；客户端**不再发送**旧翻滚输入 |
| `core/IntentType.java:15` | `ROLL` 枚举 | **保留枚举与序号**：`network/CombatIntentPayload.java:30` 用 `intent.ordinal()` 编码、`:38` 按数组下标反查，**改顺序就破坏 wire 兼容**。只让它成为被拒绝的意图 |
| `server/ServerCombat.java:252+`（`apply`） | 先走 `CANCEL`/战斗模式分支，`:312–314` 会在收招尾段 `SkillDriver.cancel`，`:338` 调 `startRoll` | 在 `apply` 的最前面（序号校验与 `isAlive` 之后、`COMBAT_TOGGLE`/`CANCEL` 之前）加：`if (intent == IntentType.ROLL) { 计数+debug 日志; syncNow(player); return; }`。**必须先拒绝，不得先 cancel 技能再拒绝**（旧客户端/伪造包同样：无动作、无扣费、无中断） |
| `server/ServerCombat.java:162–177` | `action == ROLL` 时 `player.move(...)` + `teleport` | 删除；新路线**不得**有任何旧翻滚位移/速度 |
| `server/ServerCombat.java:413–431` | `startRoll(...)` | 删除（连同 `:338` 的调用）。`state.rollDirX/rollDirZ` 的写入只此一处，删除后字段仅剩 legacy |
| `core/CombatState.java:161–167` | `isRollInvulnerable` | 保留方法（`HitResolver:170` 仍在用），但**不得有任何代码路径**再进入 `ActionId.ROLL`；加单测证明服务端收到 ROLL 意图后 `action==NONE` 且 `isRollInvulnerable==false` |
| `core/CombatConfig.java:229`、`CombatConfig.roll` | `rollSpec()` | 保留（配置键别删，避免旧配置/夹具炸），加 `@Deprecated` 注释"旧翻滚物理已停用，由 Combat Roll 拥有" |
| `network/CombatStatePayload.java:16,44` | `rollYaw` 字段 | **保持位置与 wire 顺序**，服务端写 `0f`；客户端本就没有消费者，不新增 |
| `skeleton/PlayerAssets.java:84`、`testing/PlayerAssetChecks.java:44,57` | `ROLL → "roll"` 姿态/素材 | 素材保留（删了会影响资源校验），但不再由新翻滚驱动；注释说明其只服务历史夹具 |
| `client/CombatHud.java` | 无翻滚冷却条 | **不新增**任何冷却/次数/扣蓝显示；旧动作文本里不会再出现 `roll` |

静态搜索必须覆盖：`ROLL`、`startRoll`、`isRollInvulnerable`、`rollYaw`、`rollDir`、以及数据驱动的动作选择（`ActionId` 到姿态/素材/配置的映射），改完把命中清单附在回执里。

### 11.2 键位：R 只切模式、翻滚移到左 Alt（尊重用户自定义）

`modpack/overrides/options.txt`（F10）**不含键位**，所以只能在客户端代码里做一次性迁移（D12）：

1. 仅在 `Dist.CLIENT`、游戏选项已加载后执行（`FMLClientSetupEvent` 的 `enqueueWork` 或首个客户端 tick）。
2. 遍历 `Minecraft.getInstance().options.keyMappings`，找 `getName()` 等于 `"keybinds.combat_roll.roll"` 的那条（**不要**编译期引用 `net.combat_roll.client.Keybindings`，本项目不依赖该模组）。
3. 标记文件不存在时：
   - 若它当前绑定的仍是默认 `R` **且** `key.eldencombat.toggle_combat` 仍是 `R` → 通过 `options.setKey(mapping, InputConstants.Type.KEYSYM.getOrCreate(GLFW_KEY_LEFT_ALT))` 改为左 Alt，`options.save()`，聊天栏提示一句，日志记 `MODPACK_KEY_MIGRATION combat_roll.roll R->LEFT_ALT`；
   - 若它已被用户改成别的键 → **不动**，只记日志"尊重用户自定义"；
   - 两种情况都写标记文件（`config/eldencombat-compat.json`：`{"combatRollRollMigrated":true,"at":"<ISO>"}`）。
4. 标记存在 → **永不重写**（不每次登录重置）。若发现仍冲突（两者都是 R），只在聊天/日志提示冲突，不改键。
5. 迁移逻辑必须在无 `combat_roll` 时安全跳过（找不到映射）。

### 11.3 准入（D9/D10/D11）

统一规则源 `RollGate`（服务端与客户端预测共用，参照 `CombatRules` 的"单份规则"风格）：

- 允许：原版生存模式、战斗模式、非战斗模式（`R` 不再是起滚前提）。
- 拒绝：死亡/旁观/载具/菜单（客户端侧）/输入框（客户端侧）/原版或模组自身禁用场景（如饥饿 <6、`allow_rolling_while_airborn=false` 的空中、武器冷却中），一律按**原版/模组真实能力**判断，不自己发明规则。
- 技能互斥：`WINDUP/ACTIVE/追击窗口/硬收招` 禁止起滚；**合法可取消的收招尾段允许**，且必须**先确认准入、再调用一次** `SkillDriver.cancel(player, tick, "翻滚衔接")`；不得先 cancel 再因冷却/状态拒绝。
- 普通攻击/喝药/格挡沿用既有可取消区间判断（`SkillCastState.recoveryCancelable` 同类语义），与 Combat Roll 自身限制叠加。
- Combat Roll 进行中拒绝新的攻击/战技输入，避免绕过其只拦原版操作的限制；不修改技能伤害或 FP 段价。
- 服务端**必须**有同一准入（不能只把客户端 `RollManager.isEnabled=false`）；服务端不得信任客户端"已通过"的布尔值。
- 拒绝路径：服务端 HEAD 取消 `handleRollPublish`（同时挡住广播与无敌/饥饿副作用）→ 回传拒绝 → 客户端撤销本地预测（位置/动画恢复），**不得**在拒绝后仍广播成功。
- 登录/死亡重生/换维度/离线不得复活旧 ROLL 或无敌；不得保留上一玩家/世界的冷却快照。
- 若窄 hook 无法可靠挂载（签名不符、类被混淆、插件被跳过）→ **明示阻断并写进回执**，不用事后事件假装。

## 12. 测试与夹具（不得删除失败断言来掩盖变化）

- `src/test/java/.../SelfTestTest.java` 的翻滚窗口断言（`SelfTest.java:166–168`）：保留为 `CombatState`/`ActionSpec` 的纯函数测试，但**注释标注"legacy helper，live path 不再进入"**；新增：收到 ROLL 意图后 `action==NONE`、精力不变（22 不扣）、位置不变、`isRollInvulnerable==false`。
- `testing/ClientSmokeHarness.java:82` 的 `Fixture("roll", ActionId.ROLL, …)` 与 `:567,:568,:571,:580,:583` 用例：改为**拒绝夹具**（发送 ROLL 意图 → 断言无状态变化、无位移、无精力消耗、无敌为假）。
- `:739–744`（收招尾段衔接用例）：改为"ROLL 意图**不得**导致 `SkillDriver.cancel`"（对应《合同》§4"先拒绝、不能先 cancel"）。
- 新增 `-PcombatRollSmoke`（仅在该批锁含 `combat_roll` 时使用）：断言选项里 `keybinds.combat_roll.roll` 已被迁移为左 Alt、R 仍是 `key.eldencombat.toggle_combat`、我们的 `RollGate` 在硬阶段拒绝、在合法尾段只 cancel 一次；第三方翻滚动画/位移本身只能作为**日志+截图**证据（真人验收）。
- 新增 JUnit：`RollGateTest`（准入矩阵）、`ModKeyMigrationTest`（迁移幂等、尊重自定义）、以及 §9 提到把 `proportions`/skills 计数钉在生成物上的测试。
- 装备 2D 相关测试与 `-PequipmentView`、`-PgripSmoke` **一律不动**。

## 13. 每批必跑与证据记录（回执字段）

必跑并记录**实际命令 + 退出码 + 日志路径**：

1. `.\modpack\tests\Run-ModpackTests.ps1`
2. `.\modpack\Fetch-Mods.ps1 -Selection <batch> -Mode Plan`，再 `-Mode Fetch`
3. `.\gradlew.bat test build --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"`
4. 批次隔离 `runClient` / `runServer`（`-PpackBatch=<x>`，勿污染 `build/clientSmoke`）
5. `.\modpack\Build-Modpack.ps1 -Selection <batch> -OutputDirectory dist`
6. 用户侧：真实实例启动、PCL 导入、真人输入/中文 IME、性能对照

回执（`docs/modpack-receipts/BATCH-<X>-<date>.md`）必须含：批准锁哈希、原包基线、Java/加载器、实例路径、配置差异、命令与退出码、两侧实际加载列表、选择层 sha256、正式锁 sha256（若晋级）、每条的来源/体积/哈希/准入选型、Combat Roll 的 hook 点与旧翻滚关闭证据、**未跑项**、恢复步骤、以及"设备/夹具证据 vs 真人证据"的分别标注。

## 14. DoD 勾选表

- [ ] `Run-ModpackTests.ps1` 退出码 0，§8 的 14 个用例都有断言
- [ ] `Fetch-Mods.ps1` 默认 `-Plan` 无网络无写入；`-Mode Fetch` 逐条校验体积+哈希+modid+平台元数据；`-Promote` 在未验收时拒绝
- [ ] 双来源准入落地：Modrinth 走 API+CDN 校验；CF 默认手动清单路线且缺件非零退出
- [ ] 侧闭包按 §7.1 六条规则输出，`itemborders` 批的 S 侧不含 `iceberg`/`prism`
- [ ] `Build-Modpack.ps1` 不再有 `Count -ne 7`/写死 mixin 集合/写死 scope 文案；F2 的失败被真正修好（不是删检查）
- [ ] PS 5.1 下可运行（F3 修掉）
- [ ] `build.gradle` 三个新属性生效且不改变现有 `-PclientSmoke`/`-PserverSmoke` 行为
- [ ] 旧翻滚入口按 §11.1 全表停用，静态搜索命中清单附回执
- [ ] 键位迁移按 §11.2 幂等、尊重自定义、不每次登录重置；选项里左 Alt = Combat Roll、R = 战斗模式
- [ ] 服务端准入有真实窄 hook（或明确阻断记录），无虚构事件、无"仅客户端禁用"
- [ ] 夹具改为拒绝式断言；无"删断言换绿灯"
- [ ] `docs/THIRD-PARTY.md` 更新（删 Epic Fight、补本批项目与许可证）
- [ ] 每批回执齐全且不把"下载完成/能启动"写成"整合完成"

## 15. 建议的提交顺序（便于逐步复核）

1. D1+D2+D4（模块、选择层、离线测试）→ 先让 `Run-ModpackTests.ps1` 绿。
2. D3（Fetch 重写，`-Plan` 先行）→ 用正式锁 7 条做一次只读回归：计划输出必须与原 7 条**逐条一致**（这是"没破坏既有能力"的证据）。
3. D6+D5+D7+D8（打包改造 + gradle 属性）→ 用原 7 条跑一次打包，产物与 0.1.0 结构对齐（版本 0.2.0）。
4. A 批 → 回执 → 通过后 `-Promote`。
5. B 批 → 回执。6. C 批（Java）→ 回执。7. D 批 → 回执。8. E 批 → 回执。
9. 最后统一更新 `docs/MODPACK.md`（操作步骤与产物名）与 `modpack/overrides/README-zh.md`（注意 F11 的编码）。

## 16. 需要用户先定 / 可先按推荐默认开工

| 项 | 推荐默认（不阻塞开工） | 影响 |
|---|---|---|
| CurseForge 条目的分发路线 | `manual_checklist`：脚本产出官方手动获取清单，不把 CF CDN URL 写进包 | 若要走 `reference_url`（把 CF CDN 当引用下载），需用户明确同意，且回执必须标注"哈希来自观测 CDN 字节、无独立平台哈希" |
| `Armor Toughness Bar, Again`（`toughnessbar`） | 不启用、不计入任何批次，仅在回执里保留阻断记录 | 用户批准替代后才可加入 A 批 |
| `Mod Menu` / `LazyDFU Forge` | 保持阻断：Mod Menu 用 NeoForge 内置 Mods 屏；LazyDFU 先测现有 21.1.219 启动基线，再评估同功能项 | 本轮不引入 Fabric 版或兼容桥 |

## 17. 与旧文档的关系

- 本文替代《合同》§2/§4/§5 中"由 DSH 实施"的安排；《合同》其余条款（批次验收内容、Combat Roll 行为依据、回执必须包含项）**仍是验收标准**。
- 候选锁、审计、工单三份上游文件**只读**；任何"需要改候选锁"的想法都应改成在选择层/回执里表达。
- 装备 2D/VFX 部门本轮状态已闭（收据见 `docs/DSH-EQUIPMENT-2D-REPAIR-RECEIPT-20261015.md` §11），本文件不涉及、不启动其工作。
