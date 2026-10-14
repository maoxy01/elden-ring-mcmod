# 方块战技特效接入记录

日期：2026-10-11。范围：八件追忆武器的特效网格与黄金波动的动作注册修正。

## 本轮修改

- 黄金波动：用阶梯状金色波面替换空渲染器与火花，传播方向和半径同步到客户端；额外显示短横斩光带。动画改为自有 AttackAnimation，不再注册为待机类 StaticAnimation。
- 黄金碎击：落地时出现双层方形冲击框和短立柱。
- 命定之死：每段出现阶梯式暗红厚刀影和红色刃线。
- 掠夺火焰：沿地面推进的块状暗红、橙色、金色分层火墙。
- 水鸟乱舞：三层相连的折线刀光，随施放者移动，三段交替方向。
- 唤星：收缩方框、悬浮方块与短牵引线；追击落地使用另一种方形冲击网格。
- 授血仪式：相连的双层地面方框、四角短柱、分段血色光柱；每次付费追击增加一层上方方框。
- 咒血斩击：折线血色刃线，随后显示滞留的块状血焰。
- 在已存在的生效和落地时点播放原版挥击、火焰、仪式与冲击声音。

本轮没有重做八种动作的骨骼关键帧，也没有加入按武器模板区分的新普攻连段。此前的这两项需求仍需后续完成。

## 渲染与边界

源文件：`effects/SkillEffectMesh.java`、`entity/skill/SkillEffectEntity.java`、`client/effects/`。

所有主特效为自有立体网格，使用位置与颜色顶点；不依赖原版粒子设置、特效贴图下载、Photon 或 LDLib2。正常透明混合、深度测试，不写入深度，避免遮住后续半透明物体。真正的光照投射和后处理泛光尚未实现。

单特效网格上限 96 个方块（每块六个四边形）；黄金波面的实际数量受定形参数影响，预算包括相邻波段的连接面。黄金碎击和唤星落地另外各有 12 个地表碎块，使用实际地表方块的渲染材质，只作视觉表现，不删除世界方块、不生成真实掉落物。每个临时特效实体寿命不超过 40 tick；跟随玩家的刀光在取消、切武器、死亡、离线、跨维度时结束，已经释放的静态冲击继续到有限寿命结束。网络只同步实体类型、位置及少量参数，不逐顶点发送。没有额外伤害结算。

普通玩法的 FP、伤害和追击窗口保持原逻辑。视觉方框不是新的方形伤害边界；方框布置在原径向范围以内。地面波起点移回施放者位置，让原有墙体检测也检查第一段传播，避免原先向前偏移 0.9 格跳过贴脸墙。

黄金波动的视觉与服务端命中共同使用 `WaveFront`：前 4 格传播时中心快、两翼慢，形成对称的阶梯弧形；中心传播到 4 格后，各段相对位置、宽度均固定，整片以中心速度平移，不持续加大两翼滞后距离。波段撞墙后不再渲染或判定命中；中心撞墙仍结束整片波动。掠夺火焰不使用此黄金波动定形规则。参数是当前试验值，不是已确认的原版法环数值。

`-PmanualSkills` 的单人验收模式启用无限蓝，起手与付费追击都不减少 FP，HUD 标为 `FP INF`。仍保留追击窗口和重复请求校验；普通运行与专服不启用，保存/复制玩家 FP 状态也不持久化测试开关。

## 验证

- 最新 `gradlew.bat test jar --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21` 成功，共 43 项测试通过，失败与错误均为 0。新增定形、定形后等速/固定宽度、命中/视觉一致性、地表碎块和无限蓝回归测试。
- 新增测试覆盖所有特效各阶段网格的有限坐标、非退化尺寸、数量预算、方框连接、波面范围、寿命、非法输入和同步类型。
- `art/skill-fx/preview.html` 展示直接从同一 Java 网格函数导出的数据。桌面、手机预览与时间/播放/选择/旋转操作通过 Playwright 检查，截图有非空特效像素、无脚本异常、无横向溢出。
- 预览只证明网格形状与浏览器呈现，不证明 MC 材质、光影兼容、真实动画衔接或性能。
- 手动验收客户端于 20:42 启动并进入世界，八件武器实际技能绑定检查通过；日志记录黄金波动起手成功并扣除 20 FP。没有自动攻击，没有自动关闭游戏，效果审美由用户实机验收。
- 启动日志出现 Epic Fight 自身外部网站连接失败；世界进入与技能起手仍成功。当前未进行 Sodium/Iris/Entity Culling 整合环境验证、专服运行测试或帧率基准。

构建产物：`build/libs/eldencombat-0.2.0.jar`。本轮未替换整合包分发目录的旧 JAR。20:42 的游戏记录属于首轮版本，不证明后来加入的定形传播、地表碎块和无限蓝已经实机验收。

## 预览重建

```powershell
D:/JDK21/bin/javac.exe -d build/skill-fx-export src/main/java/com/dsh/eldencombat/effects/SkillEffectMesh.java src/main/java/com/dsh/eldencombat/effects/WaveFront.java tools/skill-fx/EffectPreviewExporter.java
D:/JDK21/bin/java.exe -cp build/skill-fx-export EffectPreviewExporter art/skill-fx/meshes.js
& 'C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' art/node_modules/esbuild/bin/esbuild art/skill-fx/preview.mjs --bundle --minify --outfile=art/skill-fx/preview.bundle.js
& 'C:/Users/30869/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' tools/skill-fx/check-preview.mjs
```

## 外部方案参考

查阅了 [Photon 官方项目](https://github.com/Low-Drag-MC/Photon) 与 [客户端 Java 接口说明](https://low-drag-mc.github.io/LowDragMC-Doc/en/photon2/java-api/)。它可用于轨迹、光束等复杂特效，但当前会增加 Photon 和 LDLib2 依赖，故这批明确的方块网格先直接实现。没有复制第三方特效资源或源代码，也没有增加此依赖。

修改前六个已有文件的备份：`docs/code-backups/20261011-blocky-skill-fx/`。
