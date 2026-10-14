# JourneyMap 左上角正方形调整（2026-10-13）

## 改动
- 当前实例：build/gracePrototypeClient/journeymap/config/6.0/journeymap.minimap.config、journeymap.minimap2.config，两者 shape=Square、position=TopLeft；保持 active、标签、缩放、图标等其余选项不变。
- 可交付配置：modpack-defaults/journeymap/config/6.0/ 同名两文件。分发整合包时复制 modpack-defaults 的内容到游戏目录。
- gradle/minimap-defaults.gradle 和 build.gradle：runClient -PgracePrototype 首次启动前补齐缺失预设；已有配置不覆盖。当前实例的两个既有文件已单独修改。
- src/main/java/com/dsh/eldencombat/client/grace/MinimapSize.java：面积计算。
- src/main/java/com/dsh/eldencombat/mixin/JourneyMapSizeMixin.java 和 src/main/resources/eldencombat.mixins.json：仅客户端 @Pseudo 字符串目标，无 JourneyMap 编译/强制运行依赖，缺目标跳过。getSize 返回值仍以原 sizePercent 为基准，再转换为目标边长；没有增加 Jar 依赖。

## 尺寸口径与依据
实际 6.0.9 JAR 的 javap 输出保存在 docs/combat-evidence/minimap-properties-javap.txt、minimap-displayvars-javap.txt。
原生 getSize 是 max(128,floor(sizePercent/100*screenHeight))；横向矩形宽为 int(height*screenWidth/screenHeight)。原生大小下限阻止小窗口直接通过 sizePercent 达到目标。未发现独立地形尺寸缩放配置，fontScale 等仅用于文字。
新边长为 round(sqrt(oldWidth*oldHeight*2/7))。保留 sizePercent=30 作为旧矩形基准，不将 2/7 错当边长倍率。窗口改变后实时使用新窗口尺寸计算。两个预设尺寸规则相同，切换不会恢复大地图。
面积只计地图地形内容矩形，不包含边框、罗盘超出部分和上下文字；这些装饰维持原生主题与边距，没有缩放。截图被教程遮挡的区域仍包含在旧矩形总面积中。

## 验证
- 修改前 Get-Process java,javaw 无进程；实例日志终止于22:18保存退出。未启动、停止客户端，未触碰存档。
- compileJava processResources --offline --no-daemon 成功，仅原有弃用警告；无全量 build/打包/战技测试。日志 minimap-compile-20261013.log。
- 对实际编译出的 MinimapSize 运行一次离屏 Java 尺寸验证：MinimapGeometryCheck.java、minimap-geometry-20261013.log。

|窗口基准|旧地图内容|新地图内容|面积比|
|---|---|---|---|
|854×480|256×144|103×103|28.7788%|
|1280×720|384×216|154×154|28.5928%|
|1920×1080|576×324|231×231|28.5928%|
|640×360|227×128|91×91|28.5001%|

理论比值28.5714%；每组边长与精确平方根偏差不超过0.5像素。以上为明确窗口基准下的计算验证，不冒充当前实例截图测量。用户截图含窗口外框/裁切，未据此臆定精确客户区尺寸。
本轮未启动游戏，因此无修改后实机截图，也未实际验证 Mixin 运行期注入、无 JourneyMap 客户端/服务端启动。配置、源码和开发运行资源已就绪，下次通过本工程启动才生效；既有发布 Jar 未重新打包，不会自动更新。

备份：docs/code-backups/20261013-minimap/。本次仅处理该地图显示调整。
