# HUD V0.2 接入回执 · 2026-10-23

本次按美术最终磁盘版 `art/ui-pixel/hud-v02-20261023` 接入；用户另行确认生存模式常驻，创造模式在R战斗模式开启时显示。替代2026-10-21旧设计中的“经验条显示耐力、蓝条在右侧”方案。

## 已实现

- 原版快捷栏及经验条上移2 GUI像素，保留真实经验和等级；不改经验数据。
- 蓝条位于左侧护甲上方，金色短耐力位于右侧饥饿上方。不再绘制资源名称或当前值/上限。
- 480×270 GUI下，FP起点(149,207)，耐力起点(250,217)，物品名基线起点y=174，与最终美术布局一致。使用原生状态栈计算额外生命、吸收生命、盔甲和坐骑占用；保留原版多排心形压缩间距，不设4排血量上限。
- 水下及出水后的氧气恢复期间，右侧只显示原版气泡，不画耐力和空耐力提示；氧气条消失后恢复耐力。两种显示共享原生右侧状态栈，坐骑仍可增加行数。
- 保留原版物品名、稀有度字体及计时，移动整层避让资源条。F1、旁观和死亡不绘制自有资源条。
- Boss使用7张接入素材中的两张Boss贴图，进度和名称读取原版同步Boss事件；只绘制一套，小GUI下避开地图入口。
- 移动原生层后转发一次原生Post事件，保留韧性/护甲模组的伴随层入口；没有增加必选依赖。

接入7张自制PNG：FP底图/填充、耐力底图/填充、Boss底图/填充和资源不足提示。与美术manifest尺寸及SHA256逐一一致，整张透明布局图不作为运行贴图。

## 安装

已更新实例：`D:/PCL/.minecraft/versions/Elden Combat 0.2.0 - Vanilla Combat - 副本`。

- 交付入口：[更新包目录](../dist/hud-v02-20261023/)。
- 独立JAR：[eldencombat-0.2.0.jar](../dist/hud-v02-20261023/mods/eldencombat-0.2.0.jar)。
- 手动更新ZIP：[EldenCombat-HUD-V0.2-20261023.zip](../dist/hud-v02-20261023/EldenCombat-HUD-V0.2-20261023.zip)。
- 旧包备份：[eldencombat-0.2.0.before-hud-v02.jar](../dist/hud-v02-20261023-install-backup/eldencombat-0.2.0.before-hud-v02.jar)。回退时退出游戏，用此备份替换副本实例mods中的同名JAR。
- [安装回执](../dist/hud-v02-20261023/installation.json) 与 [包内差异及校验](../dist/hud-v02-20261023/verification.json)。

新JAR SHA256：`8d91b7891ecb9fc28a44855f0662ab19354e87f7c7ef76340ed278ed428c680e`。

旧启用JAR SHA256：`b4aa85d5721d1acbcc000d59467dfdf9d5a0376e32fdd816eaaab84627ef01db`。

相对该旧包，仅改变CombatHud.class、增加CombatHudLayout.class、7张HUD贴图及HUD语言键。没有删除包内条目；721份无关受保护文件哈希未变，保护清单中的两份HUD源码是本次用户授权修改。

## 复用和验证

先核查 [Raised](https://github.com/yurisuika/Raised) 的HUD层移动方案。本次继续使用当前精确NeoForge版本已经提供的GUI分层、状态栈与Boss事件，未复制Raised代码或安装额外模组。原版字体、生命、护甲、食物、氧气、跳跃与经验仍由Minecraft渲染。

执行 `gradlew --offline --no-daemon test --tests '*CombatHudLayoutTest' jar verifyJarHasNoHarness` 成功：4项布局/填充/共享槽检查0失败0错误，Java编译成功，包内无Harness。另核对了7张PNG哈希、允许的JAR差异和安装后的实际文件哈希。未执行全套项目测试，不扩大为整合包全验收。

没有启动游戏。原生字体、第三方HUD的实际遮挡、骑乘/潜水/Boss画面和用户最终观感尚待游戏内验收。

## 尚未接入的示意内容

左上继续是现有赐福地图按键入口，未新增地图后端。当前该实例缺少JourneyMap时会如实提示未启用；美术的演示地形和敌人点没有进入游戏。右上真实药水效果保留原版层；美术演示的自定义异常图标没有虚构出血同步数据或重复叠画药水图标。这两部分不宣称已完整接入。
