# 八战技 2D 特效：框架设计与 DSH 实施交接

日期：2026-10-14。最新用户要求：继续完成代码框架设计，具体代码编写交给 DSH。该分工覆盖此前“战斗部门直接完成剩余代码”的实施安排。Codex 维护设计、接口及审查；DSH 完成后续实现和验证；美术继续独占 art/vfx-2d。本轮不再修改 Java、资源或构建脚本，不启动 Harness。

## 当前接入基线：保留，不从旧 pending 框架重做

用户改变分工前，已有一批接入代码写入工作区；尚未正式验收，不回滚，也不能忽略它们再覆盖：

- 新增 effects/FxArtwork.java：尺寸、锚点、偏移、水平/竖直平面、phase roll/scale、直接 progress→frame、逐 lane 宽度/UV/lag 的纯逻辑模型。
- 新增 client/effects/ArtworkRenderer.java：按完整素材绘制一个主平面；波前按实际 visualRadius 切16段，各自使用UV子区间和客户端地形挂点；白色顶点染色；缺图的简单程序化轮廓。当前该路径绕开旧细条和盒体缓存，逐渲染帧取 progress，不通过16个语义步重采样。
- effects/FxSlashRoute.java 已覆盖10个Kind；client/PresentationRoutes.java 的 Route 新增 artwork；ready路由缺 PNG 改为警告并保留声明，实际atlas缺帧走轮廓，非法JSON仍保留上一份有效配置。
- SkillEffectRenderer 已使用 effect.progress(partialTick) 并优先接 ArtworkRenderer；GoldenWaveRenderer 同样接新路径；VfxFrameBudget 新增 beginArtwork。
- 正式 assets/eldencombat/routes/2d.json 已有10条ready路由与artwork字段；build.gradle 的 syncArt 复制美术单帧到生成资源，未复制带边距的review atlas。原始美术目录未改。
- 新增 testing/VfxIntegrationHarness.java、FxArtworkTest.java；更新 FxSlashRouteTest.java。夹具显式 -PvfxSmoke 才启用，隔离目录 build/vfxSmoke；生产导出应继续沿用项目 testing 包排除规则。

已有文件备份在 docs/code-backups/20261013-eight-vfx-integration/。这些代码是本轮接续基础，不是要求 DSH 再造一套相同渲染器。

## 统一接口与职责

|层|输入|输出/约束|
|---|---|---|
|美术合同|art/vfx-2d/manifest.json、单帧PNG、来源许可|美术拥有；代码只读取。width/height 是显示尺寸，不是伤害范围。|
|构建同步|美术 frame_*.png|generatedArt/assets/eldencombat/textures/particle；processResources合并；不对review atlas二次切片。|
|路由加载|fx.routes.<id>，1..64个帧ID，artwork|不可变 Route；配置非法与资源缺失分开处理。|
|几何规则|FxArtwork、phase、progress、实体实际半径/距离/blockedLanes|主平面或 lane 清单；不得更改伤害、FP、运动和施放时长。|
|客户端绘制|真实atlas sprite、几何、partialTick、预算|白色顶点、作者alpha、世界平面；缺帧明确降级；结束不画。|
|阶段/生命周期|既有服务端实体及同步字段|仍为唯一事件时间源；UI/预览不得合成追击或命中。|
|验证夹具|独立测试世界、明确测试开关|真实截图与实际帧记录；夹具跑完不等于视觉通过。|

依赖方向为 manifest→正式路由/生成资源→不可变加载数据→几何规则→渲染；服务端只提供阶段/位置等事实，不依赖美术帧数。当前正常主平面仅4顶点，没有必要为它增加复杂缓存；如DSH复用/扩展缓存，键至少包括路由资源版本、实际frameIndex、phase、半径、blockedLanes及相关地形状态，不能只按world tick或截断后的profileTravel缓存动画。

## 路由和事件合同

|素材目录|路由|Kind|现有触发与平面|
|---|---|---|---|
|waterfowl|waterfowl|WATERFOWL|ACTIVE三个burst各8tick；施法者局部竖直斩击面+phase roll|
|death_slash|death_slash|DEATH_SLASH|ACTIVE三个burst各8tick；同上|
|gold_wave|golden_wave|GOLD_WAVE|独立波实体；水平地面、真实半径、逐lane lag/阻挡|
|gold_impact|gold_impact|GOLD_IMPACT|实际landingImpact；落地点水平面|
|flame_front|flame_front|FLAME_FRONT|独立波实体；竖直火墙、逐lane地形挂点|
|gravity_pull|gravity_pull|GRAVITY_PULL|第一段ACTIVE；水平聚引|
|gravity_impact|gravity_impact|GRAVITY_IMPACT|确认追击后的实际落地；水平坠击|
|blood_ritual|blood_ritual|BLOOD_RITUAL|每个确认输入段；地面上方2.4格水平面，phase scale|
|cursed_slash|cursed_slash|CURSED_SLASH|每个确认输入段ACTIVE；局部斩击面|
|blood_flame|blood_flame|BLOOD_FLAME|既有elapsed==4事件；固定延迟原点的竖直面|

gold_wave→golden_wave 必须保留。全部新主素材不使用相机billboard。外层实体yaw仅应用一次；水平素材PNG向下映射到局部-Z。顶点坐标依 normalized anchor 计算，不强制居中。

直接帧公式 floor(progress*N)，progress>=1或实体已丢弃则不画；支持1..64时每个帧都必须可达。作者透明度已包含出现/主段/消散，主贴图不再叠旧alpha曲线或palette染色。CORE保留一个主轮廓或16个必要波前片段，减少次级碎块；HIDDEN不画。预算不足依已有全局顶点预算退让，不无限增加绘制。

## DSH 需要收口的实现与验证

### A. 先修验收夹具，再评价画面

当前 VfxIntegrationHarness 的环绕相机位置是 (sin(theta)*8,85,cos(theta)*8)，却用了 yaw=180+theta。Minecraft朝向的X为-sin(yaw)，因此该位置朝向中心应为180-theta（更稳妥是由camera→target向量计算yaw/pitch）。这处已有截图显示角色/特效不在视野中心，必须由DSH修正再验收，不能把夜间空地截图算作通过。

夹具第一轮曾错误使用 server.getTickCount 作为施放时间；已改为 level.getGameTime。保留这个修复，失败轮位于 screenshots-invalid-clock 和 eight-vfx-client-invalid-clock 日志，不得用于验收。每场恢复测试FP，自动追加仅在真实followupOpen窗口调用服务端入口；这是接口驱动夹具，不是“真人按键验收”。

### B. 检查新渲染路径的实际视觉边界

1. 当前 ArtworkRenderer 成功处理后让 SkillEffectRenderer 提前return，因而跳过原来的 groundState/debris 次级地块。DSH按预算恢复金色坠击/重力坠击等需要的辅助碎块；不恢复旧多层盒体主效果。CORE继续省略装饰。
2. 地面/火墙的逐lane落点目前在客户端对已加载区块做短射线，缺地形跳过；用斜坡、台阶、悬空边缘确认落点和frustum范围。核对 GoldenWaveEntity 的Y裁剪范围能容纳3.5格火墙，避免底部不可见时上部被误裁。
3. 黄金波动已有服务端blockedLanes；掠夺火焰的旧服务端逻辑未执行updateBlockedLanes（只做中央撞墙、目标可见性），通常mask为0。客户端已消费mask，但不能据此声称火墙实现了服务端逐lane阻挡。DSH应选择不改变现有命中的纯表现障碍裁剪，或在确实需要变更服务端语义时单列说明；不能顺手把lag、碰撞、平衡全面改掉。
4. 第一人称近距离大斩击平面可能超出视锥，不能只看图集日志判断可读性。核对作者挂点与角色姿势，保持世界朝向，不通过恢复billboard来掩盖问题。亮/暗环境的轮廓、角色遮挡和方向必须实际观看。
5. 正常资源缺失：缺一帧应保留该ready路由并使用相应轮廓，其他合法路由仍可画。非法配置保持上一份有效数据；首次非法配置回空的策略应明确记录，不能与合法配置但PNG缺失混淆。应补真实缺PNG/图集未载入/恢复资源后的重载验收，而非仅切布尔变量。
6. 如要保留旧 SkillSlashSprites 和 FxGeometryCache 兼容路径，明确仅用于旧/未配置素材；新路径不再生成12条细带、不重复整张波前。清单变更和字段校验维护一个真实schema，不再使用过时“只需改ready无需Java”的承诺。

### C. 生命周期与咒血边界

已核对 SkillDriver.tick 在阶段切换前调用 runtime.onTick；RemembranceRuntime.onTick 在ACTIVE调用activeTick。因此 followupActiveTicks=4 时，elapsed==4仍先执行延烧，再切阶段；不能擅自提前到3或增加持续时间。此次真实日志中咒血发生FOLLOWUP，blood_flame也载入并绘制到16个帧索引。DSH继续覆盖取消/死亡/换维度，不为预览自动续段或补客户端伤害。

## 已有验证证据及准确结论

- 集中定向测试最终28项通过：FxArtworkTest 3、FxSlashRouteTest 8、RemembranceSkillsTest 17。测试覆盖1..64帧可达、8tick/partialTick采样、实际半径与不重叠lane UV、blocked mask、形成后lag稳定、正式资源与美术原PNG逐字节一致。
- 客户端日志：docs/combat-evidence/eight-vfx-client-20261013.log，实际10组均报告16/16帧已缝入粒子atlas。另有波前第一人称截图显示金色前沿确实绘制。
- 实际被渲染帧索引：waterfowl、death_slash、gold_impact、gravity_pull、gravity_impact、blood_ritual、cursed_slash、blood_flame各16个；golden_wave和flame_front各15个。后两者缺哪一帧与寿命/首次实体同步关联需核对，不把“载入16帧”写成“实机完整播放16帧”。
- 截图目录 build/vfxSmoke/screenshots，共396张，含白天第一人称、夜间环绕、波前障碍与强制缺图夹具计划场景；环绕朝向错误，后几类不能据此确认通过。没有有效outline绘制帧日志，缺帧降级实机仍未验证。
- 18场清理采样都记录 effects entities=0；这是采样时结束已清理，不等于所有中断路径都测试过。
- 测试实例已自行退出，无构建或游戏进程继续运行。原美术目录和既有用户游玩世界未修改；测试世界在独立build/vfxSmoke。

当前结论：接入基线可编译、资源已真实载入并有绘制证据；尚未达到全部视觉验收。没有检查所有396张截图，不做“八技全部好看/全部验收通过”的结论。

## DSH 最终交付要求

完成上述实现缺口后集中执行必要定向检查；修好夹具再录实际第一/第三人称、亮/暗、转镜头、波前阻挡和斜坡短片。逐组给出实际帧/纹理日志、可见画面、结束清理和缺资源降级证据。多人、光影、资源重载和真人输入确未测时单列，不能用单机接口驱动夹具替代。

输出修改文件、命令/测试数量、证据链接、未完成项。具体实现由DSH执行；Codex后续按本合同审查，不自动再开Harness或扩展地图、护符、赐福功能。
