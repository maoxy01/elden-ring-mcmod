# 六件刃器与长弓动作数据交接

日期：2026-10-13。最新方向是先核查可复用资源；当前自研成果保留，暂停启动后续21件全量自研。

## 当前可恢复交付

六件已写入 `art/weapons/`，每件同时更新 model/mesh/bbmodel、逐面 UV 和 catalog：dagger、thrusting_sword、heavy_thrusting_sword、curved_sword、katana、twinblade。类型分别明确短刃、护手笼细刺刃、长握柄重刺刃、弯刃、刀镡与渐弯刀、中央可握双端剑。保留稳定注册 ID、原点和一模型单位厚度；沿用共享图集并调整材质 UV，没有下载第三方模型或替换 PNG。

| ID | 方块 | 网格三角 |
| --- | ---: | ---: |
| dagger | 49 | 336 |
| thrusting_sword | 97 | 596 |
| heavy_thrusting_sword | 128 | 752 |
| curved_sword | 85 | 532 |
| katana | 129 | 736 |
| twinblade | 166 | 976 |

备份目录：`art/backups/six-blades-before-refinement-20261012/`，沿用已存在备份，不覆盖。源定义 `blade-equipment-refinement.mjs` 已被 flatten tooling 消费。重建六件：`node art/tooling/apply-sword-refinement.mjs --blades`。新旧对照位于 `art/weapon-proportions/blades-review/`，包含24个正/侧面视图、六张握点夹具和手机截图。

45件静态检查、41件比例输入哈希检查、六件网格连通均通过。每件当前握柄中心与可握区段已由实际几何采样，位于 `blades-grip-regions.json`；中间区包含原点，双头剑柄区是[-3.5,3.5]模型单位。数据以16单位/格，mesh已为格；运行缩放以固定握点为中心。哈希见 `blades-refinement-report.json`、`profiles.json` 和握区 JSON。

新直剑、旧大剑/特大剑选择保留；两件旧剑的六个文件已再次比对原备份哈希一致。八追忆形状未改。当前比例仍为草案，40件原作持握尺寸尚未测量，不能因为模型已完成而宣称比例准确。

## 长弓动作文件

- `art/tooling/long-bow-motion.mjs` 是数据源。
- `art/weapons/long_bow.animations.json`：20 tick非循环 bow_draw，bow_upper 绕Z从0到-3到-6度，bow_lower 从0到+3到+6度；root保持静止。
- `art/weapons/long_bow.draw.json`：起始/半拉/满拉分别在tick 0/10/20；弦中心在模型坐标[1.5,0,0]、[4.5,0,0]、[7.5,0,0]。
- 弦上/下端分别附着 bow_upper/bow_lower，相对骨骼pivot为[1.5,25.5,0]和[1.5,-25.5,0]。本批将梢端补齐到这些端点，保留四骨骼parent/pivot/rotation。
- `bow-motion-validation.json` 保存三关键帧端点、中心、根节点不动和端点落在对应弓臂上的检查结果。

渲染需用两段弦：上端 → 中心 → 下端。两端随弓臂骨骼变换，中心沿轨道移动。不能平移整个现有 string 骨骼的刚性长条，否则弦端会离开弓梢。消费方隐藏原刚性弦，绘制方块阶梯或薄条两段。弦厚度建议1模型单位，宽度随整件视觉倍率换算。

游戏按 `tick=clamp(drawProgress,0,1)*20` 采样，满拉保持20，结束回到起始。20 tick是美术参考时长，不要求修改服务端蓄力规则。无需新玩家骨架。

当前 `WeaponAssets` 未加载武器动画或draw sidecar；通用 AnimationClip 读取旋转，位移需要额外消费。接入方增加资源映射、重载和原版使用进度采样，检查syncArt是否包含 `.draw.json`。本美术轮次没有改Java/Gradle或同步运行资源。

通用网页武器预览未接V形弦绘制，已避免在其“机械动作”开关里播放只动弓臂的半套长弓动作。三帧数据已验证，不声称网页或游戏拉弓已可见。

## 来源与后续方向

六件为本地自制像素资产，候选参考名称为Dagger/Estoc/Great Epee/Scimitar/Uchigatana/Twinblade，继承 `targets.json` 的图鉴参考。未复制法环原模型，未导入其他模组或不明许可资产；本轮不宣称逐件完成图鉴测量。

现成模组/资产和许可由统筹先核查，之后按明确复用工单继续。保留本目录模型、工具源与备份，避免无依据从头替换已验数据。尚未完成实际手持/掌心贴柄、蓄力弦连接、附魔、资源重载与运行模组兼容验收。
