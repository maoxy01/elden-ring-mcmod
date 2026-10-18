# MC百科复用候选：整合包先采用成熟模组

2026-10-17。按人类要求从MC百科筛选，以作者发布页交叉核对版本。只选型，未下载/试装/改正式锁；新做魔法祷告特效继续搁置。整模组采用优先，配置/数据包其次，仅为确切缺口做少量适配。

| 优先级 | MC百科 | 能直接借用的内容 | 官方发行依据 |
|---|---|---|---|
| 第一批 | [简易刀剑 Simply Swords](https://www.mcmod.cn/class/7736.html) | 武器种类、独特武器能力、探索战利品与现成表现；不从零制作整套武器技能 | [1.70.2 / MC1.21.1 NeoForge](https://www.curseforge.com/minecraft/mc-mods/simply-swords/files/8746001)；[作者Modrinth说明](https://modrinth.com/mod/simply-swords)把Better Combat列推荐、Architectury列必需 |
| 扩展 | [Simply More](https://www.mcmod.cn/class/19059.html) | 扩充简易刀剑的武器类别和独特武器，不逐把自写 | [官方页面](https://www.curseforge.com/minecraft/mc-mods/simply-more)列1.2.3 Release / MC1.21.1 NeoForge和1.3.0_alpha5；优先核对稳定版组合。Simply Swords、Cloth Config、Architectury为前置，Better Combat明确可选 |
| 第二批战斗内容 | [灾变 Cataclysm](https://www.mcmod.cn/class/5214.html) | 现成Boss、地牢及强力装备/武器能力，构成挑战与奖励内容 | [1.21.1-3.33 NeoForge](https://www.curseforge.com/minecraft/mc-mods/lendercataclysm/files/8931182)；[作者项目](https://www.curseforge.com/minecraft/mc-mods/lendercataclysm)。仍须核对所选文件前置、难度与当前包性能 |
| 第一批探索载体 | [地下城与酒馆 Dungeons and Taverns](https://www.mcmod.cn/class/10835.html) | 直接使用自然遗迹/地牢，不另造结构生成器或逐座建造 | [官方1.21.1列表](https://www.curseforge.com/minecraft/mc-mods/dungeon-and-taverns/files/all?page=1&pageSize=20&version=1.21.1)有4.4.4 NeoForge；与数据包二选一，避免重复安装 |
| 第一批多人战利品 | [Lootr](https://www.mcmod.cn/class/2924.html) | 每位玩家独立搜刮同一宝箱，减少新写多人宝箱系统 | [作者项目](https://www.curseforge.com/minecraft/mc-mods/lootr)有MC1.21.1 NeoForge版；它不直接提供战灰解锁、统一FP或任务系统 |
| 需要改掉落时 | [LootJS: KubeJS Addon](https://www.mcmod.cn/class/6327.html) | 用已有KubeJS/LootJS改战利品/掉落，优先少量脚本或数据包而不是新增Java奖励框架 | [作者项目](https://www.curseforge.com/minecraft/mc-mods/lootjs)有NeoForge1.21.1-3.7.0；需匹配KubeJS及前置版本，若纯数据包足够则无需为一张表加整套脚本栈 |

建议顺序：先试简易刀剑 + 地下城与酒馆 + Lootr及其必要前置，形成探索→得武器→直接使用既有能力的可玩组合；再按内容缺口加Simply More或灾变，不以堆模组数量为目标。已有Combat Roll、Waystones/Balm、JourneyMap等选择继续复用。

硬边界：上述只核实已有对应MC/加载器发行版与功能方向，**不等于已证明适配当前NeoForge21.1.219、依赖完整或互相运行兼容**；需固定精确文件后分批隔离试装。Better Combat在作者说明中可选，不表示不装时所有动作/技能观感都已实测。保留用户选定的原版人物动作和装备美术，不擅自覆盖。

现成武器自带能力并不自动变成“任意武器自由更换战灰、共用本项目FP”；这一小层法环规则仍须核对扩展口，必要时才交DSH适配。直接安装成熟内容与复制其代码/美术是不同事项：源码可见不自动授予搬用权；本轮优先整模组使用。

已排除的明显错配：Marium's Soulslike Weaponry的[作者发布页](https://www.curseforge.com/minecraft/mc-mods/mariums-soulslike-weaponry)所列MC1.21.1文件为Fabric，不能直接装当前NeoForge包。未改变MC版本/加载器，也未用兼容桥补装。
