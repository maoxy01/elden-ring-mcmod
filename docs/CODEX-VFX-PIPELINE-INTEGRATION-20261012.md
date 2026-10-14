# Codex：特效管线修订与接入

日期：2026-10-12。承接用户“先修订，然后接入”；后续两份风格文档作为参考审查，不当作额外授权。

## 文档修订

三份原文均已备份至 `docs/code-backups/20261012-vfx-pipeline/docs/`。
`DSH-VFX-PIPELINE-SPEC-20261012.md` 顶部为当前有效契约；其后保留历史稿。
两份风格文件顶部新增现状与例外说明，避免旧建议覆盖用户已确认的薄片武器方向。

修正重点：quad×4 顶点计数；提交与 GPU 剔除分开；按帧而非 tick 统计提交；半透明背面不能无条件删；
原版碎块成本单列；客户端资源用 F3+T；未实测的兼容与 GPU 时间不能标通过。

## 已接入

| 项目 | 实现 |
| --- | --- |
| 配色 JSON | `assets/eldencombat/fx/palette.json`；语义 ID 与三级明暗，首次接入保持原配色 |
| 资源重载 | `FxPalette/FxResources`，客户端 prepare/apply 原子替换；非法数据保留旧值 |
| 兜底开关 | JSON 可加入 `"slashSprites": false`，F3+T 后选择原生网格；缺少 explosion 图集帧自动回退 |
| 双通道轮廓 | 近处原版图集刀光加淡原生轮廓；远处或无贴片时用完整原生轮廓。水鸟、命定之死的兜底范围同步扩大 |
| 距离 LOD | 16 / 48 / 128 格分档；减少碎块、关闭远处贴片、简化主形状 |
| 并发压力 | 候选实体超过 40 时进入简化档；不删服务端伤害/视觉实体 |
| 每帧门禁 | `VfxFrameBudget` 限制自有网格+贴片提交不超过 30000 顶点，优先保留主形状并预留后续候选的最低额度 |
| 原版碎块 | 每实体近处最多 12、中距离 6、远处 0，全局每帧最多 32 次绘制 |
| 缓存 | 普通特效的描述按客户端 tick 缓存；黄金波面成型后复用固定相对偏移。资源版本变化失效，实体弱引用键 |
| 不透明面优化 | CPU 提交前剔除封闭盒体的背向面；半透明与相机在盒内时保留双面，不新增 RenderType |
| 导出门禁 | `exportSkillFx` 加入 Gradle check/build，校验所有 Kind、phase、radius、progress、波面 travel 与受阻通道 |
| 兼容防护 | 整合包 Entity Culling 白名单加入 `eldencombat:skill_effect`、`eldencombat:golden_wave` |

未修改战技伤害、判定、FP、动作、位移或服务端 tick。实体改动仅为显示距离从 64 格拓展到 128 格。
原版 hurt flash/命中反馈仍由战斗系统产生，美术不从视觉重叠推断伤害。

## 预算与证据

- 每实体自有网格最多 96 box / 576 quad / 2304 提交顶点。贴片单列，每片 4 顶点。
- 报告：`art/skill-fx/budget-report.json`；当前遍历 20,172 个组合。
- 八个最大实体的自有通道静态提交上界估计：16,896 顶点，不含资源包方块模型成本，不是八人帧率成绩。
- 七项新增回归测试覆盖预算拒绝、并发额度、LOD 边界、仪式方框、透明/不透明面、配色重载、结束帧。
- 浏览器检查桌面/手机与 11 张预览，验证刀光、碎击、仪式和黄金波面停止继续变形。
- `verified` 中游戏、Sodium、Iris、Entity Culling、独立服务端、八人性能均保留 false。
- 最终 Gradle 构建通过，七项新增回归全部通过；JAR 中配色资源及配色/预算运行类已复查。

新增 Java 代码需重启游戏才生效。之后通过资源包覆盖同路径配色 JSON，F3+T 可重载。
开发时若修改的是 `src/main/resources`，先运行 `processResources` 同步到运行资源，再按 F3+T；不需要重新编译 Java。

复验命令：

```powershell
.\gradlew.bat build --offline --no-daemon '-Dorg.gradle.java.home=D:/JDK21'
node tools/skill-fx/check-preview.mjs
```

## 明确的限制

没有伪造运行消费者的 `elements/*.json` / `fx_styles/*.json`。完整元素、声音和阶段绑定需要先核对实际战技状态机，不用两项枚举代替视觉辨识。
未增加区域雾、环境建筑、第三套 Shader，也未给武器/Boss 强制套用新图集或色彩面积比例。
按施法/玩家精确分组尚需同步静态效果的 caster/cast 标识；当前不能把 Kind 当成一次施法 ID。
极端超过可保留最低轮廓数量的压力时，顶点硬预算会限制几何提交；这不取消真实技能或命中结果。
GPU 时间、overdraw、光影兼容和顿帧效果必须游戏内验收。不能从浏览器与成功构建推导这些结果。
