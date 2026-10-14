# 赐福复用原型回执

2026-10-13。工单 G-C1→G-C4；MC1.21.1 / NeoForge21.1.219 / Java21。

施工单写：build.gradle、testing/GracePrototypeHarness；后续薄适配server/grace及network，地图兼容client独立。备份docs/code-backups/20261013-grace-prototype。构建、资源同步、服务端与客户端串行，不clean。正式modpack/mods.lock、旧dist、旧实例和旧存档不变。

独立目录：build/gracePrototypeServer，level-name=grace_world，固定新种子20261013、端口25569；客户端另用build/gracePrototypeClient/grace-prototype。依赖仅三模组，audit API SHA512核对后复制到build/gracePrototype-dependencies；API jar仅compileOnly，JM自身内嵌API/network不额外runtime重复安装。测试harness排除发布JAR。

- G-C1：进行中，精确组合启动待实测。
- G-C2：引擎harness将放两真实Waystones节点并命名/保存；玩家激活、休息FP和实际重启尚未验。
- G-C3：未实现地图UUID请求、战斗/权限/落点及原生入口统一校验；不能把Waystones默认旅行当已满足。
- G-C4：未完成；实际地图点击、非OP、多真实玩家与安全场景均待验。初轮普通Waystones方块是协议占位，最终赐福视觉另验。

## 节点/地图/原创资源约定（供美术）

节点权威采用Waystones真实UID；位置/维度/名称/owner/origin/发现不复制到第二数据库。附加赐福元数据仅引用真实UID，原型两节点用普通Waystones默认样式作为占位，绝不标成最终赐福显示。

地图状态：uid、displayName、dimension、discovered、origin(generated/player)、visibility(public/private)、valid；所有字段来自服务器权威。客户端选择只能提交uid+sequence，不能提交可授权坐标/public/owner。地图普通waypoint不注册成赐福。FP休息与旅行成本分开，合法近距离休息恢复现有FP；战灰/技能管理先保留接口，尚无新成长实现。

建议原创资源ID：eldencombat:textures/gui/grace/discovered.png、ancient.png、player.png、public.png、private.png、invalid.png；16x16透明PNG，灰色原版界面内用小标识叠加，不复制第三方资源。交接可补rest/travel/skill三入口图标同目录。世界金色粒子/贴片另列，不把地图图标用作空间效果。

外部武器namespace边界：WeaponStacks.definition只允许eldencombat注册表，Weapons.profileOf/WeaponAttributes使用完整ResourceLocation，WeaponProfiles拒绝其他namespace的同path；自有41条档与已有标签fallback保留。新增纯规则边界单测，尚不安装Spartan。

G-C1服务端启动已通过：grace-server-start-20261013.log，Waystones21.1.46/Balm21.0.66/JM1.21.1-6.0.9/commonnetworking1.0.21/API2.0.0加载；两个真实节点UID=39101552-592d-49e8-9f03-69d0c2015cd6与230f7a67-ddcd-4499-af5e-49dcf002af1a，坐标(0,81,0)/(20,81,0)，有效并命名Grace prototype1/2，保存后正常关闭。未激活玩家，不冒充地图点击或多人验收。世界由本次独立服务器新生成，客户端复制该新测试世界；没有复制旧玩家世界。flat空generator-settings产生默认层告警，试验平台手工放在Y80，正式地形尚未验。

美术约定更新：读取GRACE-PIXEL-ART-HANDOFF，采用assets.json的textures/map、textures/ui、textures/fx实际路径，不另生成早期gui/grace建议。origin generated映射ancient、玩家放置映射player；未发现默认隐藏，无权查看不泄露坐标，invalid失效后移除，public/private纹理不授权。flame16x64按4个16x16帧/4tick采样，不当整张billboard。资源尚未同步/接图，不标已实机。

客户端首次启动：三模组及内嵌依赖加载，新测试世界恢复相同两UID，真实Dev玩家通过引擎fixture激活两节点（不是鼠标互动）；误用泛化key搜索选中minimap_preset，地图未打开，GRACE_PROTO_CLIENT_FAIL并崩溃退出。已改精确key.journeymap.map_toggle，在同一新测试存档复跑，不重新生成节点。日志grace-client-start-20261013.log保留。

出发范围暂依统筹推荐采用非战斗野外地图到已发现节点，节点旁限制留可配置；原生合法context/requirements仍需验证，尚未实现这一规则，不用force/unchecked完成。
