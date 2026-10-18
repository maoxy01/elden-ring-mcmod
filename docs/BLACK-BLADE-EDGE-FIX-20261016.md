# 黑剑刃口朝向修复

用户指出黑剑持握时刃口对着自己。截图对应 `eldencombat:malikeths_black_blade`。

现有display生成器只对齐贴图主轴，没有表达刃口/刀背的朝向。本轮为黑剑增加手持轴向翻转：绕原图刀身长轴旋转180度，使刃口、刀背互换；不采用画面内180度旋转，避免把刀尖一起倒向身体。平移重新按原握点落在掌心求解，长度、宽度、厚度与握点数据不改。

## 修改范围

- `tools/equipment2d/build-display-candidates.py`：黑剑手持轴向翻转规则，保留可重建流程。
- `art/equipment-2d/full/display-candidates.json`、正式 `src/main/resources/assets/eldencombat/routes/2d.json`：重新生成。语义核对仅黑剑四个手持上下文改变；GUI、ground、fixed、head及其余41项装备不变，非equipment段落不变。
- `Equipment2dRoutingTest.java`：适配轴向翻转的display推导检查，新增回归断言，验证刀身纵向不反转、刃口横向与法线反转、使用正旋转而非负缩放；既有握点和长度检查继续覆盖四个视角。
- `build.gradle`、`ClientSmokeHarness.java`：既有握持夹具增加可选 `-PgripItem=<夹具名>` 过滤，避免为一把刀重复运行213个场景。未指定过滤时原夹具表及场景不变；未知名字报错，不产生空夹具PASS。

没有修改任何PNG或旧武器源模型。对上一轮466份受保护源文件重新核对，0处变化，包含选版记录、发布白名单与弩炮display覆盖。备份位于 `docs/code-backups/20261016-black-blade-edge/`。

## 集中验证

全部实现和资源生成完成后执行：

```powershell
$env:JAVA_HOME='D:/JDK21'
.\gradlew.bat compileJava test jar --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
.\gradlew.bat runClient -PclientSmoke -PgripSmoke -PgripItem=malikeths --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
```

- 构建、JAR与测试成功：31套、207项、0失败、0错误、1跳过。
- 隔离客户端6个黑剑场景成功：第三人称左右主手/副手、第一人称左右手。16:01:45记录 `CLIENT_SMOKE_PASS: all 6 visual fixtures rendered`，随后客户端与单人服务器自动停止，命令退出码0。
- 四个第三人称场景的实际握点至掌心距离约0.051315格；未出现prepare/draw异常或夹具失败。已查看左右手第三人称及右手第一人称截图，刃口/刀背方向已翻转，刀尖仍向外，最终观感由用户判断。
- 本轮仅验证黑剑；没有重新宣称42项所有视角通过，没有部署到用户游玩实例。运行的是开发类与资源。

证据目录：`docs/combat-evidence/black-blade-edge-20261016/`，含修改前截图、本轮6张截图、构建/游戏日志、JUnit XML、路由差异核对及素材保护核对。PowerShell序列化会把整数和浮点数表示差异误计为改动，最终 `route-delta.json` 已改用Python JSON语义比较确认仅黑剑四个手持上下文变化。

最终 `build/libs/eldencombat-0.2.0.jar` SHA256：`e4c7312c97a40a3a8ae1474359d95d98e319aa001b628135e022542a66412caf`。

所有本轮命令均已结束；美术部门未被唤醒。需回退时按本轮备份逐文件恢复并重建，不做整仓库reset，不回滚选版素材或此前BUG修复。
