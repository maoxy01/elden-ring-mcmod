# Blockbench握姿校准与本机安装

2026-10-19。人类确认“那就这样搞”，采用“先对齐新版模型，在Blockbench显示模式逐件调整，再同步游戏配置”的流程。本轮不启动DSH或美术任务，不绘制新造型；美术原任务及其命令只读核对已结束，原生校准与Java构建串行进行。

## 已交付

- 将28件总工程的94张原生导出作为本轮工作选版同步到full、发布白名单及厚度清单。采用前的full与控制文件保存在 `art/equipment-2d/blockbench-holds-20261019/backup/`；原总工程、咒剑分叉独立工程及此前手绘/选版备份未覆盖。本轮使用总工程对应的二次优化咒剑。
- 在实际Blockbench 5.2.2桌面版中准备28份Java物品显示副本，使用原生DisplaySlot及Undo事务调整56组第一人称左右手参数。位置回到视野下角，保留武器主体并允许柄尾自然出画，不再要求整把武器悬在画面中央。
- 大剑、大弓先单独调整，其余逐件检查。大弓上移以保留弓身和弓弦；中/小盾原姿态会出画，已单独校正；左手按其透视方向打开正面，不镜像修改PNG。世界长度、作者握点及黑剑基础翻刃约束保留。
- 保存28份可继续编辑的 `.bbmodel`、28份原生Java JSON导出。同步器仅消费已保存的显示值，不再次自动拟合；校验工程与导出是否成对、纹理是否为当前工作选版，避免保存了新工程却回写旧参数。
- 对旧模型回退路径只调整第一人称下角位置和展示长度上限。其余运行逻辑未改，第三方JAR未改。

可编辑工程、说明与原生截图位于 `art/equipment-2d/blockbench-holds-20261019/`。大剑和大弓工程分别在 `projects/great_sword.bbmodel`、`projects/great_bow.bbmodel`；完整工程压缩包在本轮dist目录。

## 验证

真实Blockbench原生WebGL截图：112张基础左右手/第三人称视图，另有28张弓拉弦及弩炮装填状态视图。最初批量加载曾在纹理异步完成前得到空图，已加入图片实际加载完成及截图非空检查后重拍；空图不计通过。第一人称左右手接触表及重点武器实际查看，第三人称截图用于比例对照，不宣称游戏动作或全部观感已通过。

接触表与重点图仅裁出原生显示视窗、加中性背景和排版；原始截图保存在qa。它们不是Minecraft截图，不能代替挥击、光影、多人或最终玩家观感。

集中Gradle使用受限堆与单测试worker，构建前提交余量10.31/10.37GiB、物理余量6.76/6.51GiB通过build门槛；未并行启动游戏或专服。首轮测试发现两个旧断言：黑剑测试固定旧PNG轴/第一人称参照角，另一个测试固定整份旧overrides哈希。修正为按当前PNG轴严格验证基础180°翻刃与第三人称姿态、第一人称严格对照原生导出；资源校验由15张扩展到全部94张，并验证28份原生工程哈希与56组导出值。没有删除失败测试或弱化资源门禁。首轮失败日志及XML保存在 `build/blockbench-display-20261019/build-first.log`、`first-test-results/`。

最终37套JUnit共246项，0失败、0错误、1跳过，test实际执行。完整Gradle编译与jar通过；第二轮只有测试源修改，JAR保持对应完整编译产物，不采用旧基线单文件拼装。日志：`build/blockbench-display-20261019/build.log`。

JAR逐文件核对：94张工作选版PNG字节一致，56组第一人称参数与原生导出一致，其余248张PNG与此前安装包完全一致；无Harness或bbmodel进入JAR。相对上一包仅EquipmentRenderer及其枚举内部类两份class变化。详细回执：`build/blockbench-display-20261019/package-audit.json`。

## 安装与安装包

当前项目JAR SHA256：`bd3773706bb766f440c85ec5de8913bfb9ae0c97e99d5b36dbded5a84a552f6b`。

已确认Java进程结束后原子替换 `D:/PCL/.minecraft/versions/Elden Combat 0.2.0 - Vanilla Combat/mods/eldencombat-0.2.0.jar` 并复读哈希。旧版bd4e648e…备份在 `build/blockbench-display-20261019/installed-before.jar`。本机36份第三方JAR逐份SHA512核验通过，总计37个JAR。存档、options与PCL当前内存设置保持原样。

交付目录 `dist/blockbench-holds-20261019/`：

- 单JAR、PCL标准导入ZIP与mrpack、SHA256SUMS、使用说明、INSTALLED和PACKAGE-AUDIT回执。
- `EldenCombat-0.2.0-local-offline-files.zip`：本机37-JAR完整离线文件备份，逐文件复核通过；不含游戏库、账号或玩家存档，不是PCL导入格式。
- `Blockbench-Hold-Projects-20261019.zip`：28份可编辑原生工程、28份导出及操作说明。

标准导入包保持30项Modrinth引用加6项精确CF补装清单；不把标准导入成功称为六项CF自动补齐。离线包与本机实例已含这些文件。正式模组锁未提升。

## 尚未验证

本轮未启动Minecraft，最新游戏内握姿及完整整合包验收仍待完成。完整退出旧游戏后重启才能加载本次Java与资源；F3+T不能替代重启。

此前启动日志的“0 live”发生在资源reload apply之前，不能单凭该数字断言游戏内路由永久为空；本轮也不凭静态文件存在声明实际提交绘制已通过。若重启后仍看到旧版3D外形，须以新运行的实际绘制/回退日志继续定位，不能只重复平移旧模型。

本轮工作选版与原生校准各自记录为humanAccepted/runtimeAccepted=false；Native Display检查、编译、资源一致性与机器游戏/人类观感仍分层记录。
