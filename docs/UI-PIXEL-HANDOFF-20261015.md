# 战灰图标与 FP 最小美术交付

2026-10-15。本次只新增 art/ui-pixel、两个美术工具和本文。没有修改 Java、src、Gradle、build 或运行实例。

## 交付

已交付4种原创图标，每种16/32像素，共8张透明PNG。32版是精确nearest两倍像素版本，保持GUI不同缩放下的同一轮廓；没有使用质变模板改名。许可CC0-1.0，来源为本项目原创像素网格，见 assets.json 与 LICENSE.txt。

| 消费ID | 语义 | 本地文件（相对 art/ui-pixel） | 建议正式资源 |
|---|---|---|---|
| eldencombat:spinning_slash | 回旋斩：回转轨迹与斜刃 | textures/spinning_slash_16.png、spinning_slash_32.png | eldencombat:textures/ui/spinning_slash.png |
| eldencombat:heavy_thrust | 重刺：向前尖刃与平行速度线 | textures/heavy_thrust_16.png、heavy_thrust_32.png | eldencombat:textures/ui/heavy_thrust.png |
| eldencombat:stamp_upward_cut | 踏前上斩：立刃、上扬线与踏地底部 | textures/stamp_upward_cut_16.png、stamp_upward_cut_32.png | eldencombat:textures/ui/stamp_upward_cut.png |
| ui 路由键 fp | 专注值：蓝色结晶滴形 | textures/fp_16.png、fp_32.png | eldencombat:textures/ui/fp.png |

32版正式路径在以上文件名后加 `_32`，完整路径、哈希、锚点均在 `art/ui-pixel/assets.json`。所有新图的绘制锚点为左上角[0,0]，中心为[8,8]或[16,16]。图标只表达技能类别，不承诺额外突进、追击或位移玩法。

## 核对结果与复用

- OrdinaryAshCatalog 当前实际注册三项，名称分别为回旋斩、重刺、踏前上斩；本批只绘制这三项。
- CombatHud.drawBar 已经满足最小像素蓝条需要，继续复用；没有新增条框、填充、空条PNG。
- FP 当前路由为 pending，资源目标是 `eldencombat:textures/ui/fp.png`。PresentationRoutes.drawIcon 固定按16×16整图绘制，因此接入应使用16版；不能把32版直接交给当前固定UV尺寸调用。
- 赐福现有15张PNG、正式物品目录现有58张PNG，合计73张已逐项记录路径与SHA256，检查后保持原样。复用记录不代表把这些素材统一重授CC0许可。
- 战灰装配入口继续引用 `art/grace-pixel/textures/ui/grace_ashes.png`；旅行/休息继续引用 grace_travel.png / grace_rest.png。入口语义是管理战灰，不代表回旋斩等某个具体技能。
- 物品图标仍由现有物品模型消费，不新增替换副本。此次源文件哈希记录在 assets.json/sourceEvidence，便于识别后续并行修改。

## FP 尺寸与状态

内容150×6 GUI像素，外围1像素边框；背景#2A2A2A，填充#3F7FD0，整数宽度为 floor(150×clamp(ratio,0,1))。x=guiWidth/2-75，FP条y=guiHeight-92。数值继续取 ClientSkills 服务端镜像。

当前图标调用在(x-18,fpY)，16高图标中心会低于6高条中心。建议接入方改为(x-18,fpY-5)，使中心对齐。文字保留(x+154,fpY-2)和现有游戏字体。预览展示0%、10%、100%；10%只是低值检查样例，不新增不足阈值、闪烁或警报规则。

## 战灰列表接入清单

1. 当前 GraceScreen 是330×210 GUI像素；列宽150，行间距18，可点击行高16。建议图标在行左上角偏移(2,0)，按16×16绘制；文本从x+20起，预留126像素。图标透明边缘保留，不拉伸成12像素导致阶梯不一致。
2. 按完整战灰ID查找纹理及本地化名称；未知ID保持文字回退。新增UI键或独立映射均由代码侧决定，本文没有更改路由结构。
3. **当前 ashLabel 使用 shorten(ash.id(),12)**，上述三项都可能显示为同样的 `eldencombat…`。应先将完整ID解析为本地化名称，再用游戏font.width按像素截断；完整名称与状态放原版Tooltip。不要仅按中文字符数截断。
4. **当前 buildAsh 没有按 implemented/owned/compatible 设置按钮active**，只是修改标签。接入方应根据服务端快照明确可用状态，保留服务端再次校验；绑定/替换行为仍遵循原有协议。
5. 悬停、键盘焦点、禁用背景继续用原版Button；图标禁用时用绘制时调暗，绘制后恢复颜色，避免污染其它控件。无需增加悬停/禁用PNG。预览的CSS灰底只用于布局审查，不是原版按钮纹理交付。
6. 图标属于界面资源，不注册为奖励物品；战灰拥有权、绑定、FP消耗不由素材决定。

## 预览与验证

- `art/ui-pixel/preview.html`：4种图标、真实16/32槽位、FP空/低/满、330×210列表草案、长中文名称、禁用与悬停，浅深背景切换。
- `art/ui-pixel/validation.json`：8张PNG尺寸/哈希/透明度检查，73项复用哈希未变，1200及390宽度截图，图像加载、无横向溢出、原生尺寸检查通过。
- 制作侧已查看浅底桌面和深底手机截图。网页字体与游戏字体不同，不能据此声称游戏中文排版已验收。
- 新图均尚未接入游戏；需要代码侧准入PNG、接通具体战灰图标、修正标签/禁用逻辑，并检查GUI缩放、资源重载及FP数值变化。用户最终美术审查与实机验收仍待完成。
