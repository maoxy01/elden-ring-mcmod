# 战技工程的 V0.2 武器和握姿

八项战技均已换入 V0.2 配对后的武器几何、嵌入贴图及你保存的握姿。原始装备和旧动作工程保留。2026-10-23 整理。

## 两类动作工程

- **原作提取动作、1.3倍速、腰部收幅、手臂70%**：打开 [原作动作研究新版](D:/codexout/mcmod/local-research/elden-source-motion-20261021-arm70-v02/projects)。8份free格式工程、15段动作，直接用Blockbench打开，不依赖动作编辑器插件。已加入右手武器，随人物朝向及右臂移动；原关键帧逐项不变。原始HKX和完整解码仍保留在相邻旧研究目录。
- **目前游戏阶段动作的编辑工程**：打开本目录 [projects](projects)，需要加载更新后的 [动作编辑器插件](D:/codexout/mcmod/tools/blockbench-pose/dist/eldencombat_pose.js)。8份工程保留原阶段、关键帧和源文件校验；这套阶段动作与上述原作研究是两个来源，没有把原作片段伪装成已经接入的游戏阶段动作。

咒血斩的阶段工程以本次刷新前保存的当前打开工程为基底，保留其动作。其他七份以已有编辑工程为基底，源动作哈希均与当前项目相符。

## 对应清单

| 战技 | 武器 | 阶段编辑工程 |
|---|---|---|
| 黄金波动 | 神躯化剑 | [wave_of_gold](projects/wave_of_gold.bbmodel) |
| 黄金碎击 | 玛莉卡的石槌 | [gold_breaker](projects/gold_breaker.bbmodel) |
| 命定之死 | 玛利喀斯的黑剑 | [destined_death](projects/destined_death.bbmodel) |
| 掠夺火焰 | 亵渎圣剑 | [takers_flames](projects/takers_flames.bbmodel) |
| 水鸟乱舞 | 玛莲妮亚的义手刀 | [waterfowl_dance](projects/waterfowl_dance.bbmodel) |
| 唤星 | 碎星大剑 | [starcaller_cry](projects/starcaller_cry.bbmodel) |
| 授血仪式 | 蒙格温圣矛 | [bloodboon_ritual](projects/bloodboon_ritual.bbmodel) |
| 咒血斩 | 蒙葛特咒剑 | [cursed_blood_slice](projects/cursed_blood_slice.bbmodel) |

## 以后在动作编辑器中使用

新打开战技默认读取 `V0.2/holds/projects` 中已保存的最新模型和握姿。旧标签页可点击 **刷新V0.2武器和握姿**；刷新只替换武器，保留骨骼编号和动作轨道，可撤销/重做。不会自动保存动作JSON。

若重新打开Blockbench仍显示旧武器，重新加载上面的插件，再刷新旧工程。工程面板会显示“V0.2模型与已保存握姿”。这批第三人称握姿只应用一次，取消旧运行资源专用的额外厚度校正。

## 已核对与待核对

- 8份阶段工程与8份原作工程的模型、贴图及握姿来源已核对；人物骨骼、原关键帧数据保持不变。
- 40次输入文件哈希检查一致；原作工程的几何变换误差最大约 `1.1e-14` 模型单位。
- Node/Java六项检查通过。Blockbench中单项从旧运行武器刷新到V0.2及撤销/重做已验证；当前打开的咒血斩已刷新。
- Blockbench连接随后不可用，整批16份最终磁盘工程的原生重开、逐帧观感仍待补验，不将离线检查记作整批原生验收。
- 未替换游戏资源、未构建或安装JAR、未启动游戏。单手挂载预览也不代表双手接触、原作武器轨迹、VFX或命中已经完成。

详情见 [配对清单](manifest.json)、[离线验证](qa/validation.json) 和 [原生会话记录](qa/native-session-note.json)。
