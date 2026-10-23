# 法环战技转 Blockbench · 1.3 倍速、腰部收紧

日期：2026-10-21。当前为本地动作编辑候选，未替换游戏动作。

后续用户要求手臂减少 30%：最新修订及工程入口见 [手臂70%修订](ELDEN-SKILL-ARM70-20261021.md)。下文保留腰部收紧初版记录。

已从用户指定的本机游戏档案恢复此前核对过的八技十五段 HKX，使用既有 Soulstruct Havok 1.2.2 / Soulstruct 2.3.2 解码，再生成并在 Blockbench 5.2.2 原生验证编辑工程。不是把旧手工战技样例重新命名为原作动作。

## 编辑入口

稳定保存位置：[本地研究工程说明](../local-research/elden-source-motion-20261021/README.md)。该目录单独忽略、不随 build clean 删除；研究产物没有纳入 Git、模组资源或发布包。

| 战技 | Blockbench 工程 | 片段 |
|---|---|---:|
| 掠夺火焰 | [打开工程](../local-research/elden-source-motion-20261021/projects/takers_flames.bbmodel) | 1 |
| 黄金波动 | [打开工程](../local-research/elden-source-motion-20261021/projects/wave_of_gold.bbmodel) | 1 |
| 咒血斩 | [打开工程](../local-research/elden-source-motion-20261021/projects/cursed_blood_slice.bbmodel) | 2 |
| 水鸟乱舞 | [打开工程](../local-research/elden-source-motion-20261021/projects/waterfowl_dance.bbmodel) | 3 |
| 黄金碎击 | [打开工程](../local-research/elden-source-motion-20261021/projects/gold_breaker.bbmodel) | 2 |
| 唤星 | [打开工程](../local-research/elden-source-motion-20261021/projects/starcaller_cry.bbmodel) | 2 |
| 授血仪式 | [打开工程](../local-research/elden-source-motion-20261021/projects/bloodboon_ritual.bbmodel) | 3 |
| 命定之死 | [打开工程](../local-research/elden-source-motion-20261021/projects/destined_death.bbmodel) | 1 |

通用模型格式，皮肤与几何已内嵌，不要求安装项目动作插件。打开后选择动画片段并播放。像素人物是收幅候选，细线骨架是原始动作对照；视图显示 22 个主要关节点、21 条连接，完整 150 骨骼解码数据保存在 reference/。

## 本轮按用户要求的调整

- 所有时间统一除以 1.3。黄金波动由 4.466667 秒变为 3.435897 秒；源采样帧没有丢弃。十五段原始时长及转换后时长见入口说明。
- 腰部输入增益 0.6，并用平滑限幅将前后弯、侧倾各限制在 15° 内，局部扭腰限制在 20° 内。较大的输入会被进一步收紧，并非所有角度机械乘 60%。
- 从骨盆旋转提取整体 Y 轴朝向，单独交给人物父组。完整转身保留；人物整体朝向不占腰部的局部角度限额。父组绕人物中心转动，不绕场景原点公转。
- 其余关节的幅度控制为 0.85：缩放四元数向量部分后归一化，普通摆动轻微减小，通过 ±180° 时连续；不机械缩短完整旋转。
- 使用 Blockbench 原生逐骨骼四元数插值。原有游戏姿态加载器的欧拉角线性插值不能直接复用本工程的插值结果。
- 原始骨架对照与源 HKX 均未收幅；武器模型与握姿未纳入本轮转换。

## 实际检查

离线检查核对了输入/输出哈希、十五段共 2,744 源帧、1.3 倍速时间关系、收幅前的四肢解剖方向、收幅后四元数、腰部边界，以及普通关节收幅后角距离不增大。

Blockbench 原生加载八工程、十五片段，核对 1,260 次六关节姿态、75 个帧间时刻的四元数插值，另核对父组朝向与原骨架关节点；最大旋转矩阵分量误差约 1.57e-11，参考关节点的最大分量误差小于 0.015 模型单位。八工程均经过原生编译，黄金波动还重开编译文件检查插值。四个原有握姿标签页的 UUID、名称与保存状态未改变。

修复了原生导入中一个数值问题：关键帧值由 Blockbench 按 Molang 表达式解析，`-4e-9` 等科学计数法会误读。因此导出的原生关键帧使用不含指数的十进制字符串。

初次直接导入被自动审批拦截，原因是可能影响未保存的握姿工程；后续改为明确新建、按 UUID 追踪自己的空白验证标签页，只在这些工程中导入与关闭，完成了验证。

黄金波动有一张检查过的静态预览和 64 帧原生导出动图。动图总长 3.440 秒，受 GIF 的 10ms 时间分辨率取整；编辑工程的实际时长仍是 3.435897 秒。其它缓存截图未作为人工视觉通过的证据或正式交付预览。

![黄金波动腰部收紧预览](../local-research/elden-source-motion-20261021/previews/wave_of_gold-1.3x-waist.gif)

## 边界和下一步

这批是可编辑、可对照的动作候选。六部件模型没有独立肘、膝、腕，未提供四肢平移与实体位移，也没有武器、命中时机、声音或 VFX。独立源片段没有盲目拼成可结算连段。

当前游戏姿态格式既不支持四元数插值，也没有本工程的整体朝向父组轨道，不能将这些文件直接替换现有 skill_poses JSON。接入时仍需明确阶段切分、追击衔接、姿态叠加和服务端事件合同。本轮没有启动游戏、修改游戏动作/伤害/蓝量/冷却/位移，也不将本地研究素材当作发布准入已完成。

复现脚本在 tools/elden-source/：Restore-SkillSources.py、Decode-SkillAnimations.py、Convert-SkillsToBlockbench.py、Validate-BlockbenchSkills.py、Validate-BlockbenchSkills.js、Save-BlockbenchStudies.py。来源复用 [Soulstruct Havok](https://github.com/Grimrukh/soulstruct-havok)、[Nuxe](https://github.com/JKAnderson/Nuxe) 文件字典和项目已有只读读取工具。持久副本逐文件核对 SHA256；已存在且内容不同的研究工程拒绝覆盖，保留后续人工编辑。
