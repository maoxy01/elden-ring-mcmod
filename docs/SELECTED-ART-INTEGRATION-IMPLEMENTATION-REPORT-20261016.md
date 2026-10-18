# 已选美术接入实现回执（2026-10-16）

本轮由战斗部门直接实现，没有启动DSH或美术部门。接入五件已选装备、真实薄片模型及已交付的战灰/FP图标；未引入新模组，也未部署到用户游玩实例。代码、构建和测试已完成；隔离客户端验证结果见下文。人工最终观感另行验收。

## 实际改动

### 选版进入现有资源链

- 复用 `tools/equipment2d/build-display-candidates.py`、`build-route-config.py`，重新校验当前白名单和几何后生成正式路由。碎星原有的旧哈希阻断已消除：正式装备路由42项ready、0项pending，其中自有28项、原版/提供方14项。ready表示代码准入，不表示42项都在本轮通过游戏画面验收。
- 连枷、重刺剑、修改后的义手刀、玛利喀斯黑剑及碎星大剑共15张选版PNG，均已核对生成资源与发布JAR里的实际哈希。碎星front仍为 `270c60fe9edff3b0de34263dc2251da19f4fc47995f9c585646537e6ce009a43`。
- 保留原有 `generateEquipment2d` 输出路径及白名单复制流程。本轮生成216份文件，包括94张PNG、94份状态/GUI模型和28份入口模型。

### 薄片与盾牌

- `gradle/equipment-2d.gradle` 消费 `art/item-thickness/manifest.json` 与已交付模型；在构建时检查模型/纹理SHA、准入路径与Z范围，再映射为现有运行时纹理路径。
- 三种盾牌使用已交付的实体薄片模型：前后独立材质、局部Z为7.5至8.5模型单位、仅在透明轮廓边界生成侧壁。实际盾渲染从两个平面改为原生baked模型，保留同一套格挡姿态。
- 新增 `ItemThickness`：按实际渲染pass测量局部Z跨度，以原版钻石剑在同显示上下文的Z缩放为基准，只归一化模型局部Z，保持XY、旋转和中面位置。缺失贴图/无有效深度时在提交顶点前放弃接管。
- `EquipmentRouteRenderer` 将归一化接入自有、原版和提供方模型；原有pass、材质层、状态和附魔光泽继续由真实baked模型提供。`EquipmentRenderer` 的旧模型及药瓶回退路径也接入相同厚度规则，未修改旧模型源文件或逐件长度/握点。

### 战灰、赐福与FP

- `build.gradle` 的现有 `syncArt` 纳入8张已交付UI图；构建检查清单数量、尺寸、路径与SHA。正式UI路由包含FP和三种战灰的16/32图。
- `PresentationRoutes` 按GUI缩放选择16/32图，统一显示为16逻辑像素；单个UI纹理缺失时保留文字并跳过该图标。
- `GraceScreen` 沿用原版按钮背景、焦点、提示和叙述，只调整图标与文字布局。新增 `AshUi` 按完整命名空间ID解析名称；长文本按字体像素宽度截断，提示保留全名和完整ID。补充中英文三种战灰名称。
- 可点击状态依据服务端快照的已实现、拥有、兼容、已绑定及手持拒绝原因判定；替换操作由当前武器是否已有战灰决定。卸除也受服务端手持拒绝状态限制，服务端最终校验与token机制保持原样。
- FP图标与HUD条中心对齐；赐福菜单中FP与手持物品分列，减少文本挤占。

## 保留与资源核对

本轮修改前保存466份受保护文件的SHA，完成后0处变化。范围包括full发布PNG、选版保护及白名单、薄片交付、UI交付、旧武器模型及追忆素材；详见 `docs/combat-evidence/selected-art-integration-20261016/protected-before.json` 与 `protected-after.json`。

`display-overrides.json` 完整SHA仍为 `daefa157e7bb37dcd89bbb2e54863bda31b1bdec948d4a71c2d80d7b1a18dcf0`，弩炮人工校准未被重建脚本覆盖。上一轮模型键/动态状态、战斗盾状态、Boss目标筛选及缺图回退修复均保留，相关回归测试继续通过。

发布JAR检查：94张自有装备PNG、8张UI PNG与各自准入清单逐张一致；未打入research-only、equipment-reuse、item-thickness源目录或bbmodel。详细102项资源哈希及JAR SHA记录在 `docs/combat-evidence/selected-art-integration-20261016/package-audit.json`。

最终产物 `build/libs/eldencombat-0.2.0.jar`，SHA256：`7d23a652871e15068cf265ef2a655bf51aac8951060d43141d0a7d78f7eed684`。正式路由其余fx/existing/auxiliaryModels等段落与修改前语义一致，见同证据目录 `route-audit.json`；本轮源码哈希见 `source-hashes.json`。

## 集中验证

实现完成后执行，未边写边启动游戏：

```powershell
$env:JAVA_HOME='D:/JDK21'
.\gradlew.bat compileJava test jar --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

结果：BUILD SUCCESSFUL；31个测试套件、206项测试、0失败、1项跳过。新增4项测试覆盖旋转/比例下的Z厚度与XY/中面保持、三盾透明轮廓和双面材质、15张选版图与8张UI图哈希、战灰服务端状态和替换动作选择。既有装备路由与上一轮BUG回归测试保留。

日志及JUnit XML：`docs/combat-evidence/selected-art-integration-20261016/build-test-jar.log` 和同目录 `TEST-*.xml`。

隔离客户端使用既有握持夹具，命令如下；仅使用 `build/clientSmoke`，未覆盖游玩实例：

```powershell
.\gradlew.bat runClient -PclientSmoke -PgripSmoke --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

运行结果：2026-10-16 15:52:14记录 `CLIENT_SMOKE_PASS: all 213 visual fixtures rendered`；保存213张截图后客户端自动退出，服务端存档并停止，Gradle BUILD SUCCESSFUL，退出码0。日志未出现ERROR、夹具FAIL、模型接管prepare/draw异常、模型加载/烘焙失败或UI缺图警告。日志中的Spartan缺失警告为本隔离环境的预期回退；另有既有JourneyMap类缺失警告。

完整日志：`docs/combat-evidence/selected-art-integration-20261016/grip-runtime.log`；结构化摘要 `runtime-summary.json`。同目录另保存本轮新截图10张，含钻石剑、重刺剑、碎星、黑剑、义手刀、中盾背面、三盾格挡及长弓拉满；其余截图位于 `build/clientSmoke/screenshots`。截图证明该场景实际绘制，不作为所有侧壁尺寸和最终观感的人工通过记录。

游戏退出后仅校正 `ItemSpriteGeometry` 中过时的平面/盾牌用途注释，再执行 `compileJava jar`，结果成功；JAR任务up-to-date，最终JAR哈希未变。再次核对102份包内纹理与466份受保护源文件，仍0差异。该次命令日志为 `final-comment-build.log`。本轮所有构建、测试和客户端命令均已结束。

## 验收边界

- 测试验证几何与资源契约；不能把局部Z厚度等同于旋转后的屏幕投影宽度。
- 本轮握持夹具覆盖部分自有装备、三盾、弓弩状态、左右手与第一/第三人称；不等于42项所有上下文验收。连枷未在既有握持夹具表内，本轮只完成其源码/生成资源/JAR哈希检查。
- 隔离环境未安装Spartan，提供方缺失时走旧模型回退；不能据此声称Spartan实际模型厚度、多层自定义加载器已实机通过。
- 启动日志中的能力摘要在资源监听器应用前触发，记录的0 live不是实际接管数；本回执42项ready取自正式路由及解析测试，不将该摘要当作运行时42项通过证明。
- 赐福按钮点击/装卸往返、不同GUI缩放下的菜单观感，以及GUI/地面/展示框全项、多人、光影和VFX阶段/地形/昼夜尚未在本轮实机验收。FP图标已在游戏HUD出现。
- 本轮运行的是开发类与资源，未安装发布JAR，也未运行整合包部署。未更改Combat Roll或模组扩充任务状态。

## 回退方式

修改前代码备份位于 `docs/code-backups/20261016-selected-art-integration/`，按相同相对路径逐文件恢复即可保留上一轮BUG修复；不要执行整仓库reset。新增文件为 `ItemThickness.java`、`AshUi.java`、`SelectedArtIntegrationTest.java`，回退时在移除调用后再移走这三份文件。恢复路由、候选display和构建脚本后重建资源及JAR。不要用代码回退覆盖任何已选PNG、选版保护、薄片交付或人工display覆盖文件。
