# 模组扩充候选交付

> 最新：2026-10-19人类改由Codex直接编写赐福/护符，已完成本轮实现并安装到实际PCL实例。当前JAR SHA256为 `6ba1e702a29588d8129f89dd8a32e6939e579187de34429519e8e245a3572a3e`，交付目录 `dist/grace-talismans-20261019/`；Waystones/Balm补齐后38份第三方依赖哈希通过，客户端共39个JAR。40套257项JUnit为0失败0错误1跳过；342张PNG及武器显示/比例与此前Blockbench版一致。详见[原作核对与实现记录](CODEX-GRACE-TALISMAN-20261019.md)。本轮没有启动游戏或专服，运行验收及正式锁提升仍未完成。

> 此前：2026-10-19 Blockbench校准版JAR SHA256为 `bd3773706bb766f440c85ec5de8913bfb9ae0c97e99d5b36dbded5a84a552f6b`，现已备份；当时交付目录为 `dist/blockbench-holds-20261019/`。28件/94张素材与56组原生持物参数已对齐，当时完整离线文件包含37个JAR，游戏未启动复验。详见 [Blockbench校准报告](CODEX-BLOCKBENCH-HOLDS-20261019.md)。下方旧哈希与目录保留为各自历史快照。

> 2026-10-19 本机安装更新：人类要求安装包准备好后自行启动。本轮未启动游戏。已核对实际使用的 `D:/PCL/.minecraft/versions/Elden Combat 0.2.0 - Vanilla Combat`，原有31个JAR，补齐Alex’s Mobs、Citadel、Alex’s Delight、MemorySweep、Overloaded Armor Bar、Fast Leaf Decay六个锁定文件后共37个；36个第三方SHA512及项目JAR SHA256均核验通过，无额外JAR。项目JAR为 `bd4e648ea9866bfb77ff59204a92e2eeea802b57405e55d5a18f6d1a5984d769`，含第一人称修补，仍待进游戏验收。保留玩家存档、options和当前PCL内存设置。交付材料在 `dist/install-20261019/`：完整37-JAR本机离线文件备份包、标准导入包、单JAR、SHA256SUMS、使用说明及INSTALLED回执；离线包逐条目复核SHA512，标准导入包仍是30项下载引用加6项补装清单。下文10月18日路径和旧哈希保留为历史记录，正式锁未提升。

2026-10-18。本轮继续人类批准的扩充整合，不启动DSH、美术或其它排队任务。当前为可安装候选，正式锁尚未提升。

## 使用现有实例

PCL启动器：`E:/PCL/Plain Craft Launcher 2.exe`。版本选择左侧选择`EldenCombat-Candidate-20261017`，再选`Elden Combat 0.2.0 - Vanilla Combat`。实例位于`build/modpack-expanded-20261017/pcl/.minecraft/versions/Elden Combat 0.2.0 - Vanilla Combat`。本轮已更新项目JAR，未启动该PCL实例或修改其玩家世界；旧JAR备份在`build/modpack-expanded-20261018/baseline/`。专服在另建测试目录验证。

该实例含36个第三方JAR和本项目JAR，已补齐6个CF文件。独立内存档2.5GiB、Java21；R切换战斗模式，左Alt由Combat Roll翻滚。启动前运行`./modpack/Assert-TestBudget.ps1 -Mode client`，并检查没有其它Java进程。PCL按钮本身不会调用该检查，不能将内存档位当作整机保护。

本地专服目录：`build/modpack-expanded-20261018/instances-ready/server`，22个JAR、官方运行库、配置及启动脚本齐全。在该目录执行`./Start-Server.ps1 -JavaHome D:/JDK21`；首次按Minecraft提示处理EULA。默认只监听127.0.0.1:25565，保持正版认证；使用stop正常关服。该交付目录不含验收世界或自动关服测试数据包。不能在当前内存条件下同时启动客户端和专服。

## 复制到新启动器

使用`dist/expanded-20261018/EldenCombat-0.2.0-integration-candidate-PCL.zip`或同内容`.mrpack`，导入新隔离实例。包引用30个Modrinth精确文件；另6个CF文件需按包内`REQUIRED-CURSEFORGE-FILES.json`补装。已补齐的本机实例不代表新导入也会自动得到这6个文件。不覆盖旧实例。10月17日的导入包保留为历史快照。

候选锁：`modpack/candidates/expanded-20261017.lock.json`。当前JAR SHA256为`4d8ec7dd3d6d7de79d285dc474c407a01831877aac76203c12bdf5f893625338`；当前导入包SHA256为`857354f5fc51d0478c9a44598a45c6ee8dd1b7ce3598812a28fce9daba8e5938`。逐文件与本轮验收见`research-evidence/modpack-expanded-20261018/acceptance.json`。源码保留在当前工作区，变更说明见[实施记录](CODEX-MODPACK-EXPANSION-IMPLEMENTATION-20261017.md)及[D&T最小修补](CODEX-DNT-COMPAT-FIX-20261018.md)。此前acceptance-20261018.json只对应当时的旧JAR快照，不代表当前文件。

## 验收边界

| 项目 | 当前证据 |
|---|---|
| 精确版本、依赖、两侧分包及文件哈希 | 客户端37 / 专服22；本轮再次核对 |
| JUnit及发布JAR | 37套246项，0失败0错误1跳过；无Harness |
| 战斗输入、翻滚费用及拒绝行为 | 单JVM集成场景通过，正常保存退出 |
| 普通安装专服 / 无可选依赖独立JAR | 本次新JAR的两轮专服均正常启动、保存并退出 |
| D&T两项进度错误 | 固定4.4.4数据修补后消失；原生交易与地图获取仍待实际验收 |
| PCL新实例导入及实际客户端 | 导入完成；旧JAR有游玩日志，新JAR已更新但尚未启动客户端 |
| 背包真实重启 | 夹具已编译，种子保存与二次启动尚未执行 |
| Lootr双玩家、死亡/重连/换维度 | 待完整联调；此前双进程OOM轮次不计通过 |
| Alex自然生成、掉落、食材/配方及遗迹奖励 | 待生存流程验收；注册恢复不代替自然获取 |
| 假人、树叶、中文输入、失焦限帧、装备HUD | 待实际功能及视觉检查 |
| MemorySweep性能及有效光影包 | 未完成对照；既有日志光影未启用 |
| LazyDFU Forge | 未满足，无兼容1.21.1文件，不装旧版 |

本轮专服门槛恢复后完成两次正常退出；最后客户端检查提交余量9.59GiB、物理4.33GiB，未达到12GiB提交门槛，背包保存测试未启动。下一次满足门槛后先完成背包保存重启，再串行处理生存功能与界面；双玩家需有足够资源的独立服务端环境。
