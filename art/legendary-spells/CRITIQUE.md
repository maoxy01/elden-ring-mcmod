# Taste-Skill 截图评审

## 版本说明

以下ROUND 1/2是首版历史记录，已由用户明确指出不够符合MC方块刚硬风格；7.4分的作者自评不能覆盖用户反馈，也不是当前V2的评级。V2已重构七套形体为4像素网格块面，并新增真实旧版图集对照。

## V2：2026-10-17 作者目视复核

Seen in one second: 青白束芯和厚实阶梯束壳；不是细碎发光噪点。

Idea (one sentence): 保留七招各自的法环识别形态，用大块面而非缩小原纹理来组织内部明暗。

Live thing: 同一进度、同一背景切换真正的旧版与新版；播放和逐帧操作保持。

Layout words by section: stage=full-width tool / gallery=unequal prints / sources=reference index。

Scores: design 7 · usability 8 · creativity 7 · content 7 → overall 7.3；仅作者自评，不给用户观感预先打通过。

Caps applied: 4像素网格通过不等于“MC风格认证”，浏览器功能通过不等于实际渲染验收。

Top three problems (most visible first), each with the screenshot it shows in and the fix:

1. 初轮V2 `comparisons/fell_god_flame.png` 的火焰只在上方，读作着火的容器。最终加入包覆侧焰、下缘火舌和少量大块熔亮切口；已查看更新后的同名左右对照。
2. V1过密纹理与纤细曲线的问题，已在七份 `comparisons/*.png` 左旧右新逐张检查。V2月面、星雨和龙首更简化，这是此次块面方向的明确取舍，不把原版纹理细节丢失藏起来；下一轮由用户按辨识度与块面风格共同审看。
3. 最终手机首屏 `taste-inspect/mobile-fold.jpg` 可见独立“旧版对照”行，无工具按钮错位；无横向溢出。新对照不会因重播/跳帧造成错误比较，浏览器按七招三视口分别检查。

已查看最终桌面/平板/手机首屏、全部分页接触表、全部七份左右对照。Taste最终0 FAIL / 2 WARN，与下方既有检测范围解释一致。新版本仍是侧视构图样片，月球/龙首环绕表现、正式分层、光影曝光和游戏内性能均未由此证明。

作者评审，不是人类验收；不用于宣称实机效果通过。

## ROUND 1

Seen in one second: 青白彗星光束，而非网页装饰。

Idea (one sentence): 将提取库的星云、月面与焰纹转译成七种可辨识的 MC 像素施法形态。

Live thing: 法术切换、逐帧拖动、明暗与透明底、倍速和重播。

Layout words by section: stage=full-width tool / gallery=unequal prints / sources=reference index。

Scores: design 6.5 · usability 7 · creativity 7 · content 8 → overall 6.9。

Caps applied: 无营销页英雄字号要求；这是素材审看工具。没有把绘制代码或静帧当成实机证明。

Top three problems (most visible first), each with the screenshot it shows in and the fix:

1. 初版 `taste-inspect/mobile-fold.jpg` 中重播被挤成单独一行。改为固定四轨工具栏，法术独占一行，背景/速度/播放/重播共用下一行；最终同名截图已刷新。
2. 初版金色主星与细流星权重太接近、尾迹显短。增加主轨迹长度、内部三道亮脉和子星尾长；最终 `qa/elden_stars-1440-light.png` 可辨认主次。
3. 初版火球外圈接近均匀圆环，龙首内部过平。给翻焰加入角向卷动色带，龙首增加鳞列与颅骨暗线；最终峰值图与 `qa/greyoll_roar-1440-dark.png` 保留其结果。

## ROUND 2

Seen in one second: 光束、流星、月面、火核与龙首有不同主形。

Idea (one sentence): 原始提取材质在保持有限色阶和像素边缘的前提下，为各招提供不同的内部纹理。

Live thing: 真实 Canvas 序列切换；对七招分别检查不同帧的像素变化和播放时帧推进。

Layout words by section: stage=full-width tool / gallery=unequal prints / sources=reference index。

Scores: design 7 · usability 8 · creativity 7 · content 8 → overall 7.4。

Caps applied: 没有套用“必须找摄影或营销 CTA”的标准来代替真实素材；17张样片与原纹理均是本任务内容。只主张美术样片，不给游戏中的深度、性能与相机评分。

Top three problems (most visible first), each with the screenshot it shows in and the fix:

1. 手机工具栏的问题已在最终 `taste-inspect/mobile-fold.jpg` 目视确认修复，图像和文案没有横向溢出。
2. 明亮场景中的金色次级尾迹比暗色场景更弱，但主星仍可分辨；保留次级低权重，不将整片改成白色。此项应在实机曝光/光影验收时再次判断，不用浏览器底色推定通过。
3. 龙首仍为侧影、月面仍为单视角样片。作为方向审看已清楚，正式接入须拆分层并补足环绕表现，不能把单幅侧影放大后宣称体积成立。

## 实际检查与警告

- 目视检查了 Taste 的 desktop/tablet/mobile 首屏和全部分页接触表；另看最终龙吼暗色桌面、金星亮色桌面、暗月手机截图。所有七种 PNG 峰值也已逐张查看。
- 最终 lint：0 FAIL、0 WARN。最终 inspect：0 FAIL、2 WARN，报告保留原样，不删除警告。
- `first-action`：检查器未识别 `data-ui` 内的符号控制。截图中首屏确有播放/暂停和重播按钮；针对浏览器测试实际点击并验证状态/帧变化，因此记录为检测范围限制，而不是另加假 CTA。
- `no-footage`：未加无关视频。画面由真实序列播放，故用逐帧像素差和实际推进检查补足；不把 Canvas 说成 video 元素。
- `still-page`：显式允许，因为它统计 DOM 元素位移而不统计 Canvas 像素。该豁免未隐藏文字/碰撞/溢出检查。
- 浏览器工具在普通沙箱内首次启动失败，之后经权限工具允许启动本机无头浏览器；最终两项检查均正常结束。失败不记作通过。
