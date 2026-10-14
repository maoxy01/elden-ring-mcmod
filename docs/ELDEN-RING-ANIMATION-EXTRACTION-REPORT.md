# 法环动作提取与解码记录

日期：2026-10-11。来源：用户指定的 `E:/stmgame/steamapps/common/ELDEN RING/Game`。

> 后续进展：22:44 黄金波动的本地重定向候选已实际加载，详见 [本地适配测试记录](LOCAL-SOURCE-MOTION-TEST.md)。本记录以下保留提取阶段的结果；其余七个战技尚未接入原始动作。

## 当前结果

已只读提取玩家动作容器、骨架及辅助资源，识别八个追忆武器战技，并将其 15 段主要动作解码为骨骼轨道。**还没有重定向到 Epic Fight，也没有替换模组中的动作。**

- 15 个容器，103,091,408 字节；不是解包整个游戏。
- 10,050 个容器条目，234,492,294 字节。其中 HKX 9,352 个（包含骨架、碰撞文件及动作变体），TAE 642 个，compendium 13 个。不能把这些数量当作独立战技数量。
- 八个战技的 15 段主要动作，共 2,744 帧。源玩家骨架 150 个骨骼；各动作绑定 42、57 或 72 条轨道。
- 5 项本地验证通过：战技/文件数量、源与输出哈希、轨道与时间数组/有限数值/单位四元数、15 张非空骨架图、既有模组 JAR 不包含原版资源。
- 原 `eldenring.exe` 和四个主 BHD 头文件的 SHA256 与提取时一致。没有修改 EXE、进行 UXM patch、启动法环或调用其反作弊组件。没有对全部 BDT 大文件做全量哈希。

## 战技文件对应

名称与组号参考公开的 [Rodan 动作表，Skill TAE IDs 分页](https://docs.google.com/spreadsheets/d/1evpDLAfi1b3cYfGilDtMWXDvynwyN_lbX1chnSSR7Uk/edit?gid=1077055983)，再核对本机 TAE 的实际文件引用和提取出的 HKX。尚未用原游戏播放逐项人工核验。

| 战技 | TAE 组 | 已解码 HKX | 原始时长（秒） |
| --- | --- | --- | --- |
| 掠夺火焰 | 814 | a668_040000 | 3.500 |
| 黄金波动 | 820 | a820_040000 | 4.467 |
| 咒血斩 | 827 | a827_040000 / 040010 | 3.367 / 2.833 |
| 水鸟乱舞 | 828 | a828_040000 / 040010 / 040020 | 4.500 / 2.833 / 4.433 |
| 黄金碎击 | 829 | a829_040000 / 040004 | 4.333 / 3.333 |
| 唤星 | 832 | a832_040000 / 040010 | 4.000 / 4.633 |
| 授血仪式 | 834 | a834_040000 / 040010 / 040020 | 4.667 / 3.500 / 5.833 |
| 命定之死 | 838 | a838_040000 | 5.033 |

注意：掠夺火焰实际复用 `a668` 动作，不能按 `a814` 组号猜文件名。唤星组还引用普通动作，当前只解码编号 40000 及以上的战技段。无文件名的空条目不等于丢失动作。TAE 中的 100 秒事件结尾也不能当作动作时长。

## 工具与边界

- [Nuxe 1.2.1](https://github.com/JKAnderson/Nuxe/releases/tag/v1.2.1)：调用其只读头文件审计及指定文件解包方法，输出在工作区；没有执行 GUI 或 patch/restore。
- [UXM Selective Unpack 2.4.2](https://github.com/Nordgaren/UXM-Selective-Unpack/releases/tag/2.4.2)：只使用其内置 SoulsFormats 库拆 BND/DCX，并读取本机 Oodle DLL 解压。没有启动 UXM GUI。
- [WitchyBND 3.0.1.0](https://github.com/ividyon/WitchyBND/releases/tag/3.0.1.0)：使用内置 SoulsFormats 读取 TAE；在新 PowerShell 进程启动前设置 `COMPlus_ReadyToRun=0`，避免复合 ReadyToRun 程序集兼容问题。
- [Soulstruct Havok](https://github.com/Grimrukh/soulstruct-havok)：工作区独立安装 `soulstruct-havok==1.2.2`、`soulstruct==2.3.2`，配合 numpy 2.5.4、scipy 1.16.3。通过通用 HKX/compendium API 和官方 spline 解码器读取轨道，不使用有缺失附带数据问题的跨游戏封装导入链。
- 依赖、缓存、日志、原始动作和解码输出均留在 `build/elden-source/`，不进入 `src/main/resources` 或 `art` 的构建资源目录，也不进入可分发整合包。

解码保留原始四元数样本，也导出解码器旋转对象的归一化 XYZW 四元数。未绑定骨骼采用源骨架参考姿势补全，用于诊断。根位移样本独立保存；授血仪式三段没有独立 extractedMotion，不伪造。输出 NPZ 包含 `times`、`track_bones`、`translation`、`quaternion_xyzw`、`quaternion_raw_xyzw`、`scale`、`world_matrices`、`root_motion`。

## 产物与复现

工作区根目录为 `D:/codexout/mcmod`。

- `build/elden-source/verified-inventory.json`：当前资产计数与原 EXE/BHD 哈希核对结果。以这份清单统计容器，早期提取回执的递归列表包含一个元数据文件，不用于资产计数。
- `build/elden-source/skill-animation-index.json`：八个组的 TAE 事件和实际 HKX 引用；事件类型与参数仅保存原始值，未解释为命中/音效时机。
- `build/elden-source/decoded-skills/decode-report.json`：逐动作文件哈希、轨道、帧数、时长和根位移存在性。
- `build/elden-source/decoded-skills/*.npz`、`skeleton.json`：解码轨道与源骨架。
- `build/elden-source/source-motion-preview/*.png`：15 张原始骨架对照图，固定斜视投影、同一动作内固定缩放。只锁定骨盆水平位置；不叠加 extractedMotion，没有武器网格、特效或 MC 骨骼。
- `tools/elden-source/`：提取、索引、解码、原始骨架诊断、验证脚本。

```powershell
$env:COMPlus_ReadyToRun = '0'
pwsh -NoProfile -File tools/elden-source/Index-SkillAnimations.ps1
python tools/elden-source/Decode-SkillAnimations.py
python tools/elden-source/Render-SourcePoses.py
python tools/elden-source/Test-SourceAnimations.py
pwsh -NoProfile -File tools/elden-source/Verify-Extraction.ps1
```

需要使用支持的 PowerShell 7 / .NET 10 和 Python 3.12；本次使用 Codex 自带运行时。首次操作前必须先准备本记录中对应工具，脚本不会自动下载、修改游戏或把资源复制进模组。

## 下一阶段

1. 优先黄金波动。核对源骨骼与 EF biped 的参考姿势、坐标轴及关节层级；不能把两者局部四元数直接互抄。
2. 保留身体扭转、肩肘腕姿势和挥剑节奏，按 MC 的四肢比例重定向；使用现有自制神躯化剑，校正 Tool_R 和握持位置，确保手与剑连续连接。
3. 根运动与动画姿势分开处理，交由服务器审核位移；检查脚底、跳跃、墙体及双持握持约束。允许战技跳跃，仍不引入处决。
4. 从实际动作与经验证的事件语义确定挥剑/出波/命中窗口，不按 100 秒原始事件哨兵或旧自制动画时间硬套。黄金波动特效仍遵守先形成弧形、随后固定形状整体推进。
5. 做原始骨架与 EF 骨架对照、客户端加载和游戏验收，再考虑替换其他七种战技。现有无限蓝测试开关继续仅限本地手动测试。

当前不宣称授权可分发原游戏动作。原始与派生数据仅作本地研究参考；面向发布的整合包保持与这些提取数据隔离，发布资产另行处理。
