# 整合包构建与验收

2026-10-23 最新本机整包已安装为 **Elden Combat 0.2.0 - 20261023**，39 个 JAR 已补齐并校验，旧实例和存档保留。完整 PCL/mrpack、安装回执、当前入口及未验收项见 [整包更新记录](PLAYABLE-PACK-20261023.md)。本轮没有启动游戏，未提升正式锁。下文七项锁构建入口保留为历史流程，不能用于描述当前 38 项第三方组合。

当前为 0.2.0 候选阶段，PCL 新实例导入未验。操作见 modpack/overrides/README-zh.md，当前证据见 COMBAT-DEPARTMENT-REPORT-20261012.md。

串行运行（Java 21）：

```powershell
./modpack/Fetch-Mods.ps1
./gradlew.bat test build --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"
./gradlew.bat runServer -PserverSmoke -PpackSmoke --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"
./gradlew.bat runClient -PclientSmoke -PpackSmoke --offline --no-daemon "-Dorg.gradle.java.home=D:/JDK21"
./modpack/Build-Modpack.ps1
```

锁表固定七个模组及其哈希，客户端/服务端按 env 过滤。构建器验证当前 neoforge.mods.toml、姿态 mixin、实际模型/网格/图集/参数，排除 smoke harness；通过归档白名单核验载荷哈希。不会递归打包运行目录。旧 Epic Fight 不进入依赖或 manifest。

输出 dist/EldenCombat-0.2.0-PCL.zip、EldenCombat-0.2.0.mrpack、SHA256SUMS.txt。两个归档使用相同 Modrinth 格式载荷。dist/0.1.0 保留。

clientSmoke 使用隔离存档 build/clientSmoke/saves/codex-player-smoke；由 prepareClientSmokeWorld 从 run/world 准备。开发客户端 smoke 不等于启动器实际安装或人工游玩验收。Iris 开光影、性能和保存重载若无证据继续列为未验。

本地测试候选已生成在build/packaging-smoke；ZIP与mrpack载荷/字节一致，SHA256SUMS和安装检查清单同目录。正式dist和PCL导入仍未验收。2026-10-13依用户指令暂停新系统扩展，等待现成方案复用工单。
