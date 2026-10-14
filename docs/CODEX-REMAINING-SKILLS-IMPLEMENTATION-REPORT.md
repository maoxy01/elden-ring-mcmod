# Codex 追忆战技与 FP 绑定修复

日期：2026-10-11。基线：Minecraft 1.21.1 / NeoForge 21.1.219 / Epic Fight 21.17.3.1 / Java 21。

## 根因与修复

用户实测出现「钢铁旋风」和攻击蓄能。原因是物品级 capability 的 `innate_skills` 字段没有被当前 EF 的 `api/data/reloader/ItemCapabilityReloadListener.deserializeWeapon` 读取；该字段的旧解析器属于武器类型配置，不属于物品配置。只检查 JSON 字段存在的旧单测无法验证真正的绑定。

现由 `compat/RemembranceWeaponPresets.java` 注册八个专属 preset，并在每个实际使用的 moveset 上调用 `Moveset.Builder.addInnateSkill`。八份物品 JSON 改为指向各自的 preset。义手刀同时覆盖双手与收鞘姿态；圣矛同时覆盖单手与双手姿态。未修改普通武器或 EF 的全局预设。

八个战技使用 `Skill.Resource.NONE`，不读取 EF 的蓄能栈；`RemembranceWeaponSkill.resourcePredicate` 校验全局 FP，真正的扣费由服务端 `SkillTransactions` 原子完成。覆盖 `setConsumption`，阻止普通攻击给这些战技积累武器能量。HUD 显示当前绑定的战技名称和每段 FP 消耗；客户端退出时清空 FP 施放镜像。

## 当前战技

| 武器 | 战技 | 起手 / 追击 FP |
| --- | --- | --- |
| 神躯化剑 | 黄金波动 | 20 |
| 玛莉卡的石槌 | 黄金碎击 | 22 |
| 玛利喀斯的黑剑 | 命定之死 | 25 |
| 亵渎圣剑 | 掠夺火焰 | 20 |
| 玛莲妮亚的义手刀 | 水鸟乱舞 | 28 |
| 碎星大剑 | 唤星 | 15 / 15 |
| 蒙格温圣矛 | 授血仪式 | 每段 15，最多三段 |
| 蒙葛特咒剑 | 咒血斩击 | 12 / 8 |

黄金波动保留 dsh 的基础实现；其余七个已加入专属动作和服务端运行逻辑，包括范围判伤、多段去重、限幅跳跃移动、短时死亡效果、火焰命中回血、吸引及输入追击。无处决。玩家皮肤、游戏版 R3 武器模型和 Boss 均未改动。动作、判伤和手感仍需用户实机验收。

## 验证边界

- `gradlew.bat test --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21`：30 项测试通过。
- 20:07:36 手动世界启动，八个 `MANUAL_BINDING_PASS` 全部出现；检查了服务端实际 capability、已绑定 moveset 的所有现有姿态及 `Resource.NONE`。
- 20:07:37 客户端实际圣矛槽为 `eldencombat:bloodboon_ritual`，资源类型 `NONE`，FP 为 100。客户端每次切换快捷栏后会记录实际槽技能。
- 用户手动施放日志：20:07:45 / 20:07:50 水鸟乱舞各扣 28 FP（100 → 72 → 44）；20:07:56 / 20:07:58 黄金波动各扣 20 FP（44 → 24 → 4）。确认真实客户端输入进入专属服务端施放入口；不据此推定命中和视觉验收通过。
- 根据用户指示未自动施放、未自动退出；伤害、位移、追击以及多人联机不据此宣称通过。未重新发布 JAR 或整合包。
- 手动运行：`gradlew.bat runClient -PmanualSkills --offline --no-daemon -Dorg.gradle.java.home=D:/JDK21`。隔离目录 `build/manualSkills`，日志 `build/manualSkills/logs/latest.log`。试验场玩家有测试命令权限，可用 `/eldencombat fp max` 补满 FP，不影响正式存档。
- 本次修改前备份：`docs/code-backups/20261011-remembrance-binding-fix`；七个战技实现前备份：`docs/code-backups/20261011-remaining-skills-001`。

旧 `DSH-SKILLS-IMPLEMENTATION-REPORT.md` 保留历史交付内容，不作为当前八个战技状态或物品绑定格式的依据。
