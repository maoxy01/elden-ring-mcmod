# V0.2 新模型与已保存握姿配对

已完成28件装备、38份可编辑工程的配对，并通过Blockbench原生导入、贴图解码、几何/UV往返及导出检查。

模型取自上一级V0.2选版包；握姿取自 `art/equipment-2d/blockbench-holds-20261022/` 磁盘文件。本次不读取或覆盖Blockbench里尚未保存的修改。

## 打开和检查

- `projects/`：实际配对后的38份Blockbench工程，直接打开这里继续编辑。
- `textures/`：36张手持状态贴图，均与V0.2选定PNG哈希一致；工程内也已嵌入。
- `exports/`：Blockbench原生导出的36份Java物品模型JSON，属于待接入资料，不是可直接替换整个游戏资源目录的安装包。
- `mapping.json`：每个工程的新模型来源、握姿来源、状态、纹理资源和SHA256。
- `input-hashes.json`：原始输入文件哈希快照；末次复查全部未变。
- `validation.json` 与 `qa/native-validation.json`：静态与Blockbench原生检查结果。
- `qa/native-roundtrip/`：38份原生编解码往返副本，仅供对照；继续编辑优先使用 `projects/`。
- `qa/screenshots/`：36份手持工程的第一/第三人称左右手共144张原生预览。

### 预览总览

- [firstperson_righthand](qa/contact-firstperson_righthand.png)
- [firstperson_lefthand](qa/contact-firstperson_lefthand.png)
- [thirdperson_righthand](qa/contact-thirdperson_righthand.png)
- [thirdperson_lefthand](qa/contact-thirdperson_lefthand.png)

## 保留规则

手持工程逐项原样复制你保存的 `display` 旋转、位置、缩放和所有显示槽。新几何沿用原握姿的转换比例与握点坐标，不重新全局缩放、移动或翻转刀刃；旧握点在四个手持视角中的数学位置未变。各武器新版原生比例差异继续保留。

大弓待机旧工程缺少转换比例字段，使用其明确记录的旧原生源工程恢复比例，没有按新版轮廓重新拟合；详情见 `mapping.json` 的 `factorSource`。

弓的待机与三段拉弦、弩炮的待机/三段装填/装填完成、三种盾的正反面均分别配对。静态状态文件不代表已经完成连续动画或游戏条件路由接入。

钩爪和拳套保留原生free工程、现有左右手几何及肩部枢轴（右[-5,22,0]、左[5,22,0]）；参考假人不导出，左手不重复镜像。两份配对工程与选定模型/已保存握姿逐字节相同。穿戴图集及16/32图标保留在上级 `textures/` 与 `icons/`。详见 [穿戴合同](../contracts/WEAR-CONTRACT.md)。

## 对应表

| 装备 | 模型选版 | 配对工程/状态 |
|---|---|---|
| 弩炮 (`ballista`) | V0.2最新候选 | [standby](projects/ballista.bbmodel)；[loading_0](projects/ballista__loading_0.bbmodel)；[loading_1](projects/ballista__loading_1.bbmodel)；[loading_2](projects/ballista__loading_2.bbmodel)；[loaded](projects/ballista__loaded.bbmodel) |
| 亵渎圣剑 (`blasphemous_blade`) | V0.2最新候选 | [front](projects/blasphemous_blade.bbmodel) |
| 钩爪 (`claw`) | V0.2穿戴版 | [左右手穿戴](projects/claw.bbmodel) |
| 特大剑 (`colossal_sword`) | V0.2对照页原版 | [front](projects/colossal_sword.bbmodel) |
| 特大武器 (`colossal_weapon`) | V0.2最新候选 | [front](projects/colossal_weapon.bbmodel) |
| 大曲剑 (`curved_greatsword`) | V0.2最新候选 | [front](projects/curved_greatsword.bbmodel) |
| 曲剑 (`curved_sword`) | V0.2最新候选 | [front](projects/curved_sword.bbmodel) |
| 拳套 (`fist`) | V0.2穿戴版 | [左右手穿戴](projects/fist.bbmodel) |
| 连枷 (`flail`) | V0.2最新候选 | [front](projects/flail.bbmodel) |
| 辉石杖 (`glintstone_staff`) | V0.2最新候选 | [front](projects/glintstone_staff.bbmodel) |
| 大弓 (`great_bow`) | V0.2最新候选 | [standby](projects/great_bow.bbmodel)；[pulling_0](projects/great_bow__pulling_0.bbmodel)；[pulling_1](projects/great_bow__pulling_1.bbmodel)；[pulling_2](projects/great_bow__pulling_2.bbmodel) |
| 大盾 (`great_shield`) | V0.2最新候选 | [front](projects/great_shield.bbmodel)；[back](projects/great_shield__back.bbmodel) |
| 大矛 (`great_spear`) | V0.2最新候选 | [front](projects/great_spear.bbmodel) |
| 大剑 (`great_sword`) | V0.2最新候选 | [front](projects/great_sword.bbmodel) |
| 玛莲妮亚的义手刀 (`hand_of_malenia`) | V0.2最新候选 | [front](projects/hand_of_malenia.bbmodel) |
| 重刺剑 (`heavy_thrusting_sword`) | V0.2对照页原版 | [front](projects/heavy_thrusting_sword.bbmodel) |
| 玛利喀斯的黑剑 (`malikeths_black_blade`) | V0.2最新候选 | [front](projects/malikeths_black_blade.bbmodel) |
| 玛莉卡的石槌 (`marikas_hammer`) | V0.2最新候选 | [front](projects/marikas_hammer.bbmodel) |
| 蒙格温圣矛 (`mohgwyns_sacred_spear`) | V0.2最新候选 | [front](projects/mohgwyns_sacred_spear.bbmodel) |
| 蒙葛特咒剑 (`morgotts_cursed_sword`) | V0.2最新候选 | [front](projects/morgotts_cursed_sword.bbmodel) |
| 神躯化剑 (`sacred_relic_sword`) | V0.2最新候选 | [front](projects/sacred_relic_sword.bbmodel) |
| 圣印记 (`sacred_seal`) | V0.2最新候选 | [front](projects/sacred_seal.bbmodel) |
| 中盾 (`shield`) | V0.2最新候选 | [front](projects/shield.bbmodel)；[back](projects/shield__back.bbmodel) |
| 小盾 (`small_shield`) | V0.2最新候选 | [front](projects/small_shield.bbmodel)；[back](projects/small_shield__back.bbmodel) |
| 碎星大剑 (`starscourge_greatsword`) | V0.2最新候选 | [front](projects/starscourge_greatsword.bbmodel) |
| 药瓶 (`test_flask`) | V0.2最新候选 | [full](projects/test_flask.bbmodel) |
| 双头剑 (`twinblade`) | V0.2最新候选 | [front](projects/twinblade.bbmodel) |
| 软鞭 (`whip`) | V0.2最新候选 | [front](projects/whip.bbmodel) |

## 验证边界

复查220个原始文件哈希一致；38份原生往返成功，36份Java导出成功，144张PNG有效；原有Blockbench标签页（包括未保存工程）均保留，检查后恢复原活动标签。

本次交付是模型与握姿的配对文件及原生预览，未改正式资源、未构建或安装JAR、未启动游戏。Blockbench检查通过不代表握姿已获人类观感验收；游戏内视野遮挡、左右手、盾牌动作与弓弩路由仍需后续实际加载核对。
