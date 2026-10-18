# 模组扩充实施记录

2026-10-17。人类在确认计划后明确“你自己完成”，随后要求继续：本轮由 Codex 直接实施脚本、Java 适配与集中验收；不启动 DSH 或美术。此前只交框架的分工在本工单范围内由这条人类指令调整，其它任务不扩大。

## 精确集合与文件准备

2026-10-18后续补正：[D&T兼容修补](CODEX-DNT-COMPAT-FIX-20261018.md)已完成并集中回归，当前JAR为`4d8ec7dd3d6d7de79d285dc474c407a01831877aac76203c12bdf5f893625338`；本次37套246项0失败0错误1跳过，test实际执行。两组普通专服均正常保存退出；原两项进度加载错误不再出现。新包位于`dist/expanded-20261018`，PCL项目JAR已更新但新客户端未启动。下文52896...及10月17日导入包数字保留为先前快照，最新以[交付清单](MODPACK-CANDIDATE-DELIVERY-20261018.md)为准。正式锁、第三方版本及文件哈希不变。

已合并 `modpack/candidates/expanded-20261017.lock.json`：36个第三方文件，客户端36项、专服21项。全部固定旧审计版本并通过SHA512，实际JAR及嵌套JarJar的modId已核对。包含上一批Simply Swords/D&T/Lootr；Sophisticated Core、Architectury和原七项均去重。未修改正式七项锁。

人类已确认：Mod Menu使用NeoForge内置页面，韧性条采用Again v9，Alex's Mobs采用Raguto非官方移植及对应Citadel；LazyDFU Forge仍无匹配文件。没有混装Fabric、旧Forge或兼容桥。

`modpack/Fetch-Mods.ps1`已改为锁驱动，支持隔离目录、两侧选择、校验缓存与离线运行，不再重写正式锁。下载及每次重定向仅允许明确官方CDN；缺文件产生不完整回执。实际JAR检查只从mods段识别提供者，递归检查JarJar，防止把依赖声明误算成提供者。已验证拒绝重复条目、未批准条目、路径逃逸和同名错误缓存。

`modpack/Build-Modpack.ps1`已接受显式锁与依赖目录，替换七项数量硬编码；继续检查实际Mixin集合/类、平台、资源、握点与无Harness。候选锁必须显式使用Candidate。当前导入包引用30个Modrinth文件，另外6个CF文件生成精确官方补装清单，未把第三方JAR塞入overrides；不能把该导入包称为自动获取全部36项。

## 适配行为

- 主手受控数值档/标签决定是否接管近战，未接管第三方武器的左键和副手盾牌右键保留原生行为，服务端拒绝伪造本项目攻击。
- 装Combat Roll时不注册旧翻滚键、不处理旧ROLL位移/无敌；旧网络请求在取消战技或扣费之前拒绝。未装时保留单独模组的原路径。
- Combat Roll沿用原生输入、移动、动画、HUD和费用配置；默认Left Alt，R保持切战斗模式，尊重其它实例已有键位。
- 精确2.0.6的ServerNetwork.handleRollPublish在广播与副作用前挂窄Mixin，并统一到服务器线程；镜像原生次数/充值属性和4秒默认冷却，仅做准入，不额外推位移、扣精力或FP。
- WINDUP/ACTIVE等硬阶段拒绝；合法收招尾段在所有准入成功后才cancel一次。翻滚期间拒绝项目攻击/施法；回执纠正被拒绝的本地预测和原生冷却镜像。登出、重生、换维度及停止服务器清理状态。网络协议版本提升为7，旧Intent枚举编号保留。

## 已遇到并处理的问题

- 原生背包不暴露通用ItemHandler能力；首轮夹具把能力缺失当失败，现已改用发布版BackpackWrapper接口。真实插入17个苹果、栈序列化恢复及提取无复制已在联网专服通过；这尚不证明真实重启持久性或全部升级组合。
- 夹具原先按两端固定tick推进，客户端收到场景时服务器已切换状态，产生假失败。现改为客户端实际收到场景后发送测试专用ready命令，再进入下一阶段，不放宽断言。
- Alex移植1.22.17遗漏7项出生注册。复用其现有实体predicate及AMEntityRegistry叶片位置函数，恢复5种树栖生物、Spectre及Cosmic Cod的注册；只对精确1.22.17生效，无上游JAR/AI/素材改写。启动已确认注册完成且旧7项错误不再出现，仍需自然生成验证。
- D&T 4.4.4仍有两个上游advancement无法加载（wander_add_map、give_quest_trader_trade）；服务器可启动，此项未改动、未宣称任务链完整。

出生规则依据：[原作者1.20注册](https://github.com/AlexModGuy/AlexsMobs/blob/1.20/src/main/java/com/github/alexthe666/alexsmobs/entity/AMEntityRegistry.java)、[移植源码](https://github.com/Raguto/AlexsMobs-1.21.1/blob/master/src/main/java/com/github/alexthe666/alexsmobs/entity/AMEntityRegistry.java)与本地固定JAR字节码。角色、模型和动作仍由原模组提供。

## 验证进度：候选可复核，正式验收未完成

黑屏后已重新集中执行JUnit：37套246项，0失败0错误1跳过，test任务实际执行；发布JAR无Harness。最终JAR SHA256：`52896dbb54035abc9e8dc6029faaea802a2a68a1bb48e1af0fb7f6cc27e13e6d`。构建使用768MiB Gradle堆、2工作线程、禁用并行，不同时启动游戏。

36项客户端和21项专服已实际加载、真实客户端连接成功。已通过受控/外来武器识别、服务端伪造外来LIGHT拒绝、旧ROLL无动作/扣费、背包存取/序列化。首轮原始证据在 `docs/research-evidence/modpack-expanded-20261017/round1-*.log`；后续轮次仍以各自日志和完整终态为准。

完整36项客户端及集成服务器场景已通过：原生Left Alt唯一翻滚、移动、旧精力/FP不重复扣费、0无敌帧、技能硬阶段拒绝且不先取消技能，客户端和服务器都有COMPLETE，无FAIL；所有维度正常保存并退出，Gradle exit0。证据为`single-pass.log`、`single-command-pass.log`。这属于带隔离夹具的开发场景，不能替代独立发布JAR、两玩家或人类观感验收。

专服与客户端同时运行的两轮已触发系统提交内存耗尽，不能记为完整通过。Windows事件2004和Java原生OOM已经交叉确认，后续服务也因分页文件不足失败，系统发生异常重启。具体见[黑屏核查](BLACK-SCREEN-INVESTIGATION-20261017.md)。不归咎于某个模组，不将MemorySweep当作系统内存故障修复。

新增`modpack/Assert-TestBudget.ps1`：读取Windows系统提交与物理内存余量，构建/客户端/专服分别要求至少8/12/10GiB提交余量与2/4/3GiB可用物理内存；检测到任何既有Java进程则拒绝新启动。这是启动检查，仍需运行期间监测；不更改系统分页文件或关闭其它软件。

候选包已使用最终JAR重打：`dist/expanded-20261017/EldenCombat-0.2.0-integration-candidate.mrpack`及同内容PCL.zip，SHA256均为`b385135b5f06fd9af2e4e43186f5419b08a1c1a8a79a3acc860ad5448ba7b4fe`。包中固定Combat Roll原生默认参数与HUD位置，不重叠旧翻滚HUD。

`Build-Modpack.ps1`新增可选`StageDirectory`，在新隔离目录分两侧复制并重新核验全部文件。`build/modpack-expanded-20261017/instances-ready/client`为37个JAR，server为22个；各有INSTANCE-CONTENTS.json及精确锁。分包阶段补齐六个CF文件，本身不含游戏库；随后server目录已补官方运行库与带内存检查的Start-Server.ps1。真正PCL导入另见下段，不能从分包文件齐全推断导入成功。正式七项锁SHA256保持`e2795047bf804deff9595c03ee24f85bc75ec8518222809c532aa94a661647bf`。

独立发布JAR已通过NeoForge官方固定21.1.219安装器运行。安装器SHA256为`140df0fa17fd438848051ecfa3d091081515ad12bfe34fa126405037ba01de44`，官方SHA1已校验。开发启动器尝试加载发布JAR时的Mixin类路径失败仅是历史失败证据，已撤回该实验配置；随后按[官方服务端安装方式](https://docs.neoforged.net/user/docs/server/)完成安装。网络中断已按官方哈希复用本机缓存处理。

普通安装专服含21个第三方JAR及本项目JAR，16:20:25启动完成，16:20:35执行测试数据包stop，全部维度保存后exit0；类加载来源确认是mods中的发布JAR，无Harness。另一个仅装本项目JAR的专服16:24:09启动完成，16:24:19正常保存退出，验证Combat Roll及Alex缺席时可独立加载。证据为`production-server-pass.log`、`production-class-origin.txt`及`standalone-server-pass.log`。这些是启动/保存验收，未覆盖双玩家或长期运行。

PCL新隔离实例已实际导入成功，随后补齐6个CF文件，37个JAR核验通过，平台为1.21.1/21.1.219。证据为`pcl-import-success.png`、`pcl-import-receipt.json`。2026-10-18续工发现该实例已有10月17日19:11–19:18真实运行日志：固定模组列表、进入世界、保存三个维度、Minecraft Stopping终态均存在；这是对既有运行的事后日志核查，不冒充本轮新启动或人工功能验收。原始日志归档为`pcl-client-observed-20261017.log`；日志显示没有启用有效光影包，不能记为光影兼容通过。

2026-10-18通过PCL界面将该实例独立内存档设为2.5GiB，并复读Setup.ini确认VersionRamType=1、VersionRamCustom=14已持久化；Java自动选择21.0.7。该档位是Java堆分配，不能当作整个进程或整机内存占用上限。当前提交余量约4.2GiB、可用物理内存约3.3GiB，无Java进程；未达到客户端12/4GiB门槛，本轮不启动游戏，不降低门槛。

后续待完成：背包真实重启持久性、Lootr双玩家及死亡/重连/换维度联调、自然遗迹和Alex食材配方闭环、假人计伤/树叶腐烂、MemorySweep同场景对照、中文输入与装备HUD人工检查、有效光影包联调。背包重启夹具已编译但尚未执行种子保存与二次启动，不能标为通过。正式锁尚未提升，LazyDFU仍未满足。最新验收状态见[交付与剩余验收](MODPACK-CANDIDATE-DELIVERY-20261018.md)。
