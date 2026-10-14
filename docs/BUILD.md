# 构建与运行说明（dsh）

模组：`eldencombat` 0.1.0 · Minecraft Java 1.21.1 · NeoForge 21.1.209 · Java 21 · Gradle 8.12

## 本机工具链（固定，不动系统默认 Java）

| 项 | 值 |
| --- | --- |
| 构建用 JDK | `D:\JDK21`（Temurin 21.0.12.1，由 `gradle.properties` 的 `org.gradle.java.home` 指定） |
| 系统默认 Java | 仍是 `D:\JDK17`，未修改 `JAVA_HOME` / `PATH` |
| Gradle | 仓库自带 wrapper（8.12），首次会下载到用户缓存 |
| 依赖版本 | ModDevGradle `2.0.148`、NeoForge `21.1.209`、JUnit `5.11.4` |

本机访问外网需经本地代理（`127.0.0.1:7890`）。代理写在 `gradle.properties` 的 `systemProp.*` 里，
在不需要代理的网络上删掉那四行即可。

## 常用命令（在 `D:\codexout\mcmod` 下）

```powershell
$env:JAVA_HOME='D:\JDK21'
$env:GRADLE_OPTS='-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890'

.\gradlew.bat build        # 编译 + 单测 + 打包，产物 build\libs\eldencombat-0.1.0.jar
.\gradlew.bat test         # 规则/骨架/皮肤/扫掠/握持自检（420 项，与游戏内 selftest 共用）
.\gradlew.bat runClient    # 启动开发客户端（需要图形界面，人工看渲染）
.\gradlew.bat runServer    # 启动独立服务器（run\ 目录，已配 eula 与测试数据包）
```

## 安装到普通客户端

1. 装 NeoForge 21.1.209（MC 1.21.1）；
2. 把 `build\libs\eldencombat-0.1.0.jar` 丢进 `mods\`；
3. 进游戏后 `/eldencombat testkit` 领测试装备，`/eldencombat summon margit` 在身前空地刷 Boss。

## 美术资产如何进包

`art/margit/` 由 Codex 维护，构建时由 Gradle 任务 `syncArt` 复制到
`build/generated/artResources/assets/eldencombat/{skeletons/entity,animations,textures/entity}`，
再随 `processResources` 进 jar。**不要手工把资产拷进 `src/main/resources`**，
否则 Codex 改源模型后会出现两份不一致的副本。

`art/player/` 同步到 `skeletons/player`、`animations/player`、`textures/item`、`textures/particle` 与 `effects`。
只有原版物品模型保留在 `models/item`。自定义骨架 JSON 不放在 `models/`，避免原版模型扫描报错。

## 游戏内自检

```
/eldencombat selftest   # 420 项检查，失败项会红字列出，明细写日志
/eldencombat status     # 当前动作/序号/精力/防御/锁定/药瓶
/eldencombat reload     # 热改 config/eldencombat-combat.json 后重载
```

`run/` 目录里带一个自动验收数据包 `world/datapacks/eldencombat_test`，
服务器一启动就会跑自检、刷 Boss、100 tick 后验伤、再打死验证死亡清理，
日志里看 `[etest]` 与 `[selftest]` 行即可。

## 隔离的自动集成测试

开发测试不写原来的 `run/world`。存档由**构建脚本自动准备**（`clean` 会删掉 `build/`，
而 `--quickPlaySingleplayer` 找不到存档时客户端会**静默退出**，日志里连一行 smoke 都没有——
看起来像"构建失败"，实际是环境没准备）：

| 任务 | 作用 | 何时自动跑 |
| --- | --- | --- |
| `prepareClientSmokeWorld` | `run/world` → `build/clientSmoke/saves/codex-player-smoke` | `-PclientSmoke` 的 `runClient` |
| `prepareServerSmokeDir` | `run/eula.txt` + `run/server.properties` → `build/serverSmoke` | `-PserverSmoke` 的 `runServer` |

需要手工准备时（服务端换目录、要带测试数据包）就照上表复制一次即可。

```powershell
.\gradlew.bat runClient -PclientSmoke --offline --no-daemon
.\gradlew.bat runServer -PserverSmoke --offline --no-daemon
```

客户端测试自动展示 13 个渲染场景并保存截图，然后通过**真实按键**（`KeyMapping.click`）检查战斗动作、
战技端到端与削韧崩解同步；最后自动退出。服务端测试在端口 25566 启动，跑共享自检 + 真实事件总线投递检查
（`SERVER_BUS_PASS` / `SERVER_BREAK_PASS`），300 tick 后退出。日志分别在 `build/clientSmoke/logs`
与 `build/serverSmoke/logs`。测试入口类不打进发布 jar。普通 `runClient` / `runServer` 不会自动退出。
