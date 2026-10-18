# 第一批模组整合：隔离联调候选 2026-10-17

MC 1.21.1 / NeoForge 21.1.219 / Java 21。请导入全新实例，不覆盖现有存档。
当前仅通过文件、依赖声明和归档静态检查；未通过本组合启动、专服、多人、自然战利品和启动器导入验收。此包不是正式发行。

新增 Simply Swords 1.70.2、Dungeons and Taverns 4.4.4、Lootr 1.11.38.127，以及 Architectury 13.0.11、Fzzy Config 0.7.7、Simply Tooltips 0.1.5、Kotlin for Forge 5.12.0。保留原七项基础模组与 EldenCombat 0.2.0。第三方 JAR 通过官方 Modrinth 清单下载，未装入 overrides。

已知待修：本项目 R 战斗模式会拦截未接管第三方武器的左键，可能无法原生普攻。第三方武器联调先保持 R 战斗模式关闭；DSH 需修好输入边界后再测试开关两种状态。第三方能力暂走原生规则，尚未统一项目 FP 或赐福战灰绑定。

本候选没有加入 Combat Roll、Better Combat/Epic Fight、Simply More、灾变、新魔法祷告或额外脚本栈。没有附带测试存档、源游戏素材、测试 Harness 或游戏启动器。暂存专服目录需要另外安装相同 NeoForge 运行时，不能直接双击运行。

后续验收：独立客户端加载、新世界自然结构/武器来源、同版本专服连接、两位玩家 Lootr 独立领取及重启持久性、R 开关与 C/右键无重复结算、自有武器回归。具体 Java 与脚本适配交 DSH；统筹按串行规则派工。
