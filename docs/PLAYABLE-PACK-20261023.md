# 2026-10-23 整包更新与安装回执

本机新实例已安装：`D:/PCL/.minecraft/versions/Elden Combat 0.2.0 - 20261023`。在 `E:/PCL/Plain Craft Launcher 2.exe` 的版本列表选择 **Elden Combat 0.2.0 - 20261023**。如列表尚未刷新，刷新版本列表即可；本轮未更改启动器账号或全局选中版本。

## 交付

- [完整 PCL 导入包](../dist/playable-pack-20261023/EldenCombat-0.2.0-20261023-full-PCL.zip)
- [相同内容的 mrpack](../dist/playable-pack-20261023/EldenCombat-0.2.0-20261023-full.mrpack)
- [安装说明](../dist/playable-pack-20261023/README-先读我.txt)、[安装回执](../dist/playable-pack-20261023/INSTALLED.json)、[包哈希](../dist/playable-pack-20261023/SHA256SUMS.txt)
- [模组与依赖存在审计](../dist/playable-pack-20261023/PACKAGE-AUDIT.json)、[安装后运行文件审计](../dist/playable-pack-20261023/INSTALLED-RUNTIME-AUDIT.json)

两个导入包字节相同，约 91.2 MB。所有 38 个第三方模组与本项目 JAR 全部内置，不需要手动补 CurseForge 文件。属于本机自用备份，未发布或上传。导入到其它机器时，游戏本体、NeoForge、Java 和资源由启动器安装，个人世界不在导入包中。

平台继续保持 Minecraft 1.21.1、NeoForge 21.1.219、Java 21；沿用已审查的[38 项精确锁](../modpack/candidates/grace-talismans-20261019.lock.json)。本轮更新现有已接入内容及配置，没有批量追逐第三方最新版。未提升正式锁的运行验收状态。

## 这次调整

- 纳入新版 HUD JAR，SHA256：`8d91b7891ecb9fc28a44855f0662ab19354e87f7c7ef76340ed278ed428c680e`。
- 赐福四项恢复为 FULL，护符配置保留当前一槽及现有四件效果；Combat Roll 使用已核对的默认参数，包括 0 无敌帧。
- 保留 R 战斗、G 重击、C 战技，解绑 Iris 的 R 重载、SimplyTooltips 的 G 切页和背包的 C 物品交互；解绑创造快捷栏的 C/X 辅助键，避免战技/弹反组合触发保存/加载工具栏。背包 B、物品栏中的排序和 JEI 使用继续保留。
- 默认 8 区块视距、5 区块模拟、60 FPS、关闭光影。MemorySweep 保留安装，默认关闭定时清扫；尚无性能对照证据，不声称它已造成退化。
- 核对了 [PCL 官方源码](https://github.com/Meloong-Git/PCL/blob/main/Plain%20Craft%20Launcher%202/Pages/PageInstance/PageInstanceSetup.xaml.vb)：内存值 18 实际是 4.5 GiB，值 15 为 3 GiB。本机新实例使用 15，原实例设置未改。官方源码快照保存在 `build/pack-release-20261023/research/PageInstanceSetup.vb`。
- 新建同名版本目录、JSON id 与原版客户端 JAR，修正旧“副本”文件名与内部 id 不一致的布局。游戏 JAR 按 Mojang SHA1 核对。
- 补齐唯一缺少的官方日志配置 `assets/log_configs/client-1.12.xml`，888 字节，SHA1 `bd65e7d2e3c237be76cfbef4c2405033d7f91521`。
- 原“副本”实例的 `saves` 复制到新实例；29 个文件逐项 SHA256 核验。原世界不写入、不搬走；之后两份世界会独立保存，继续游玩请选择新实例。

## 核验范围

39 个模组 JAR、190 个归档载荷文件逐项哈希通过；JAR 内不存在 Harness。按客户端/服务端环境检查了顶层及嵌套 JAR 的必需 modId，客户端 39、专服组合 24 个 JAR 均无缺失；锁定版本未变。嵌套库有多个候选时如实记录，最终版本选择仍由加载器完成，静态存在检查不冒充实际加载成功。未另建或启动专服。

安装后的原版 JAR、适用于当前 Windows 环境的运行库、资源索引、4,008 个总运行文件通过哈希或存在核对。归档按 [Modrinth 官方格式](https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack) 使用 `overrides` 和 `client-overrides`，客户端专用 JAR 不放入公共载荷。模拟解包和本机文件安装已核验；没有操作启动器完成一次全新导入。

本轮未启动 Minecraft，未运行新一轮游戏、专服、多人或性能测试。文件安装齐全与完整玩法验收是两项状态。之前 HUD 单测证据见[HUD 回执](COMBAT-HUD-V02-INTEGRATION-20261023.md)，不将历史测试报告记作本轮新运行。

## 已知缺口

- JourneyMap 未进入当前精确组合。现有缓存 6.0.9 的作者发行说明要求 NeoForge 21.1.228，尽管历史开发客户端在 219 上启动过，本轮未据此忽略作者要求或擅自提升加载器。当前可通过 Waystones 菜单使用赐福，HUD 地图提示仍会显示不可用。
- LazyDFU Forge 没有核验到 1.21.1 兼容文件，继续单列未满足。
- V0.2 新模型、握姿与战技编辑工程尚未转换成实际运行资源。本包保留当前已接入 JAR，未改用户建模或握姿，也未把编辑工程直接打入包中冒充接入。
- 全部法环武器、战灰、护符、探索获取流程仍不是已完成状态；本次是可安装的完整现有组合更新。

本机启动前可双击新实例中的 `启动前检查.cmd`。它只检查内存，不启动游戏；PCL 自己的启动按钮不会自动执行该门槛。仍需留出足够系统提交内存，不能把 3 GiB 堆上限当作整机内存保护。

复用入口：`tools/Package-PlayablePack.py`、`tools/Verify-PackRuntime.py`、`tools/Install-PlayablePack.ps1`。安装器只允许新目录，不覆盖既有实例。包内来源清单保留作者官方精确下载与版本信息。
