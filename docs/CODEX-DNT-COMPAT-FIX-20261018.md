# D&T 4.4.4 数据兼容修补

2026-10-18，继续模组扩充工单。复用上游交易函数与NeoForge内置数据包机制，不重做寻图、交易或世界生成系统。

## 根因与改动

固定上游`dungeons-and-taverns-v4.4.4.jar`的`minecraft:give_quest_trader_trade`与`minecraft:wander_add_map`都引用不存在的父进度`minecraft:root`，因此原日志报告两项无法加入进度树。进一步核对发现，前者奖励函数存在，后者引用的`nova_structures:choose_wander`根本不在该发布包中。

新增`compat/DntDataCompat.java`，仅在modId为`mr_dungeons_andtaverns`且版本精确为`1-v4.4.4`时，用NeoForge的`AddPackFindersEvent.addPackFinders`注册高优先级、自动启用的内置数据包。未装D&T或升级到其它版本时不启用。

- `give_quest_trader_trade`：只删除不存在的父节点，criteria名称、villager_trade触发器、NPC标签条件、奖励函数保持原样；静态核验确认除此之外数据完全一致。
- `wander_add_map`：使用`neoforge:false`条件停用上游已失效的旧入口。不伪造缺失的函数，也不宣传流浪商人寻图已恢复。

数据位于`src/main/resources/datapacks/dnt_4_4_4/`，pack_format=48。该机制依据[NeoForge 1.21.1数据加载条件](https://docs.neoforged.net/docs/1.21.1/resources/server/conditions/)及本机固定21.1.219的AddPackFindersEvent源码；已核对ServerAdvancementManager使用ConditionalCodec。上游JAR、精确候选锁及正式锁均不改动。

## 已完成的运行证据

新JAR SHA256：`4d8ec7dd3d6d7de79d285dc474c407a01831877aac76203c12bdf5f893625338`。发布包无Harness，构建成功。

与旧JAR逐条目比较：所有既有条目内容完全一致，仅新增DntDataCompat.class及兼容包的3份数据文件；既有素材、战斗类和其它资源零变化。四文件源码补丁随候选交付于`dist/expanded-20261018/DNT-COMPAT.patch`。逐文件哈希、运行结果和未完成项在同目录ACCEPTANCE.json及证据目录acceptance.json中。

普通安装的隔离22-JAR专服：17:13:50自动加载`mod/eldencombat:datapacks/dnt_4_4_4`，17:13:58完成启动，17:14:08自动stop，17:14:09三个维度保存完成，进程exit0。完整日志没有ERROR，两项旧进度错误消失。

仅本项目JAR的隔离专服：17:15:02完成启动，17:15:12自动stop，17:15:13保存完成，进程exit0；没有发现或启用D&T兼容包，也没有ERROR。两轮分别通过10GiB提交/3GiB物理内存启动门槛，前一进程结束后才启动后一进程。

证据目录为`docs/research-evidence/modpack-expanded-20261018/`。测试世界均位于`build/modpack-expanded-20261018/`，与玩家PCL世界隔离。

## 尚不能据此声称完成

专服加载与退出证明数据包准入、进度加载错误和可选模组缺席路径，不等于NPC交易、地图自然获取、双玩家Lootr或完整探索奖励闭环通过。本次新JAR已集中回归37套246项，0失败0错误1跳过，test任务实际执行，JAR无Harness；证据final-test.log。新候选导入包位于dist/expanded-20261018，PCL项目JAR已更新，玩家世界未改动，旧版备份在build/modpack-expanded-20261018/baseline。客户端门槛检查为提交9.59GiB、物理4.33GiB，未启动背包测试。此前客户端运行日志仅适用于旧JAR；新JAR客户端、背包真实重启、性能、光影、中文输入和HUD仍需后续验收，正式锁不提升。
