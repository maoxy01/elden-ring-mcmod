# 开发工具导航

| 目录 | 用途 | 入口 |
| --- | --- | --- |
| [equipment2d](equipment2d/) | 装备生成、握姿、原生模型和准入核查 | `audit-model-delivery.py`、`audit-latest-grips.py`；写入类脚本需按对应交接执行 |
| [blockbench-pose](blockbench-pose/) | 八技姿态与持物的 Blockbench 编辑插件 | [中文使用说明](blockbench-pose/README-zh.md)、[验收边界](blockbench-pose/evidence/ACCEPTANCE.md) |
| [skill-fx](skill-fx/) | 特效预览、导出及核查 | 结合 `docs/DSH-VFX-RUNTIME-FRAMEWORK-20261016.md` |
| [elden-source](elden-source/) | 本机原作参数或动作核对 | 原游戏文件与解包产物留本机，参数核对记录不等于素材许可 |
| [dsh-anim](dsh-anim/) | 既有动画工作流辅助 | 按对应任务交接使用 |
| [codex](codex/) | 项目目标提醒 | 本机可选辅助；不影响 Gradle 构建 |
| [repository](repository/) | 仓库文件分类索引 | `Write-FileCatalog.ps1` |

在项目根目录更新文档索引：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/repository/Write-FileCatalog.ps1
```

该命令只更新 `docs/FILE-CATALOG.md`，不构建、不接入美术、不启动部门或 DSH。索引来自 Git 已跟踪文件与未被忽略的新文件；生成目录和本地备份不在新增文件范围内。
