# 装备2D迁移：42项清单与六类代表交付

日期：2026-10-14，Asia/Shanghai。本批依据 ART-2D-PRIORITY-WORK-ORDER-20261014 执行。独立美术交付，未修改 Java、正式 src 资源、Gradle、build 或运行实例。已有 VFX 未重做。

## 本批真实成果

1. `art/equipment-reuse/equipment-mapping-42.json`：42项逐条映射，33普通装备、8追忆、1药瓶；附现有自有模型哈希、候选真实ID、版本/发布包哈希、来源/许可、严格2D证据、尺寸依据、缺状态和接口。Markdown同名文件供快速审阅。
2. `art/equipment-2d/six-representatives.json`：直剑、大剑、矛、中盾、长弓、神躯化剑的透明边界、握柄像素、长轴尺寸、左右手建议和状态。
3. `art/equipment-2d/sprites/`：4张自有透明 PNG，大剑1张、神躯化剑1张、中盾正反2张。中盾格挡复用正面像素并改变姿态，未用重复PNG虚增状态数量。
4. `art/equipment-reuse/research-only-six/`：从已经存在且绑定哈希的引擎/Spartan JAR读取6张未修改的研究PNG，分别为铁剑、矛、长弓待机和3档拉弓；evidence.json保留原始模型、父模型、状态predicate和贴图入口。
5. `art/equipment-2d/preview.html`：可直接离线打开；原生像素、nearest放大、浅深背景、同尺度1.8格人偶、左右手、纤细臂、握点、弓盾状态。

这不是42项全部素材完成。六类代表已交付，剩余36项的路径和缺口已登记。运行接入和实机验收均未完成，正式装备2D路由在读取时42项全部pending。

## 六类选择与握点

| 项目 | 本次方案 | PNG/状态 | 握点（左上为原点，像素中心） | 目标全长/高 |
|---|---|---|---|---|
| straight_sword 直剑 | 引用 minecraft:iron_sword | 16×16，待机 | 3.5,12.5 | 1.080格 |
| great_sword 大剑 | 用户选定旧版自有轮廓投影 | 32×32，front | 7.5,24.5 | 1.584格 |
| spear 矛 | 引用 spartan_weaponry_unofficial:iron_spear | 32×32，待机 | 12.5,19.5 | 2.664格 |
| shield 中盾 | 现有自有盾轮廓；补绘木背、皮带和柄 | 32×32，front/back；blocking姿态 | 16.5,16.5，两面一致 | 0.900格 |
| long_bow 长弓 | 引用 spartan_weaponry_unofficial:longbow_iron_strengthened | 16×16，standby/pulling_0/1/2 | 3.5,7.5，四状态一致 | 1.836格 |
| sacred_relic_sword 神躯化剑 | 现有自有薄片模型投影，保留分叉护手与刃根连接 | 64×64，front | 11.5,52.5 | 1.710格 |

目标来自已存在的 `art/weapon-proportions/profiles.json`。这些是逐件美术比例草案，不是官方尺寸；神躯化剑有低置信度人物持剑图估计，其余仍为待原作参考/实机校准。六件未按同一手持全长归一化。

直剑/矛/长弓优先完整引用提供方。没有为它们重画换色替代图，也不把研究PNG算作本项目可再发布资源。中盾已有 Spartan Shields 源码候选，但未绑定精确发布包、状态PNG和许可，因此本批没有为了中盾新增依赖。神躯化剑没有已确认可复用的等价2D资源。

大剑/特大剑仍保留用户选定的旧模型。生成器核对 `large-sword-version-selection.json` 中全部6个旧 model/mesh/bbmodel 哈希，未覆盖；大剑仅另出独立2D预览候选。特大剑没有制作新样板，也没有擅自切换其运行资源。

## 复用证据与发布边界

- Spartan 精确发布包：`spartan_weaponry_unofficial-1.2.3-1.21.1.jar`，SHA-256 `84d3ddafb519e2f5e03f2681ff52c30c92c9512181f61a7a7487399a0179888f`。出处与作者署名沿用 `docs/ART-REUSE-AUDIT-20261013.md` 的已归档证据；作者 ObliviousSpartan、xwerffx、Mai_xiyu。声明 Apache-2.0，但单项素材例外未完成准入，本批只交完整依赖引用。
- 原版铁剑来自本地 Minecraft 1.21.1 / NeoForge 21.1.219 引擎资源JAR，完整哈希在研究 evidence.json；运行时引用 `minecraft:item/iron_sword`，不要把 Mojang PNG 打进自有资源包。
- 其余候选继续使用已完成的23项发布包/引擎定义证据与11项源码证据，不重搜已确认事实。Simply Swords 的 twinblade/claymore 依赖映射不能当作自由抽取资源许可。
- `publishable-assets.json` 只列4张本项目自有衍生PNG。研究PNG、带第三方素材的预览截图明确排除；没有复制第三方纹理进正式运行资源。
- 清单中的 candidate.installed 为 null，runtimeRegistryVerified 为 false：未执行当前运行实例注册表检查，不以“JAR存在于研究目录”推断安装。近似类别单列，不能把重弩当弩炮、大锤当特大武器直接算覆盖。

## 显示与状态接口

像素锚点位于真实不透明的柄部/盾柄区域。网页把该像素对齐到示意手心，只证明数学锚点与像素位置一致，不能证明 Minecraft 掌心握持正确。

建议按 `six-representatives.json.suggestedDisplay` 使用每件 sourceAxisPixels 和 worldLengthBlocks。统一XY倍率为 `worldLengthBlocks * textureWidth / sourceAxisPixels`；这只是薄片模型长轴的尺寸换算。柄在16单位模型坐标中为 `[anchorX/width*16-8, 8-anchorY/height*16, 0]`，应用旋转缩放后将这个点平移到实际手心。不能沿用旧3D本体长度补偿，也不能仅把所有贴图中心平移到掌心。

左右手独立校准。网页镜像仅用于对照；实际模型应使用左右手display，不盲目镜像UV。第一人称另调镜头侧偏和握点，保持武器类型间长度差异；GUI/JEI可单独适配槽位，不能把GUI归一化倍率写回手持比例。

中盾的两面像素不同：generated单layer默认会把同一张图显示两面，不能自动使用back.png。接入方需明确双面薄片材质/前后面路由及格挡姿态，不能叠两个完整generated模型形成重影；背面柄可保持平面绘制，少量厚度建议为1/32格。格挡时前面朝向来袭方向，背面柄朝掌心，盾的世界比例保持0.9格草案。

长弓四档沿用提供方原状态与父模型。拉弓predicate为 `spartan_weaponry_unofficial:pulling`，拉力 `spartan_weaponry_unofficial:pull` 的阈值0.65、0.9；不能替换成未注册的普通字符串或误用Minecraft属性。每档的原始模型/父display均在研究evidence中，四档柄部像素锚点一致。网页没有模拟另一只手拉弦与箭矢碰撞，不据此认定动作已接通。

当前 `gradle/equipment-2d.gradle` 会拒绝 ready 的 external_model，完整外部物品/模型准入接口尚待代码方实现。其 sprite 分支仅组装parent/textures/overrides，也未消费本包握点和逐件display建议；直接设ready不会自动获得上述尺寸。这里给的是接入合同与美术数据，不是可直接覆盖正式 routes 的配置。

## 剩余36项路径

| 路径 | 项目 |
|---|---|
| 同一Spartan依赖继续逐项查像素/状态 | dagger、thrusting_sword、katana、greataxe、great_hammer、halberd、scythe |
| 原版物品完整引用，保留功能状态 | axe、short_bow、crossbow |
| 有候选但须确认形制/语义，不能直接算覆盖 | colossal_sword（仍保留选定旧版）、colossal_weapon、curved_sword、great_spear、hammer、ballista |
| 可选额外依赖或自制补缺，须先确定单一来源 | twinblade（Simply Swords）、great_shield（Spartan Shields源码候选）、claw（Vanilla Claws源码候选） |
| 当前缺严格2D合适候选，沿用自有设计补图 | curved_greatsword、heavy_thrusting_sword、flail、glintstone_staff、sacred_seal、small_shield、great_bow、whip、fist（cestus为立体六块） |
| 剩余七件追忆，沿用已有自有设计逐件转换 | blasphemous_blade、hand_of_malenia、malikeths_black_blade、marikas_hammer、mohgwyns_sacred_spear、morgotts_cursed_sword、starscourge_greatsword |
| 药瓶 | test_flask：保留 healing_flask 自有来源；独立精灵与消耗/补充状态语义待定 |

上述合计36项。尚未制作它们的新PNG，不将清单行数作为素材完成数；护甲、玩家、Boss不在42项范围。

## 检查与后续验收

静态：42个唯一ID与正式路由一致；33+8+1数量一致；旧大剑/特大剑哈希未变；4张自有PNG连通、留透明边界；全部图片alpha为0/255；所有状态握点在不透明像素；研究PNG哈希与已读JAR匹配。原版/Spartan图有触边像素，这是原图布局，未擅自加边距或修改提供方像素。

浏览器：1440×1080和390×844已检查图片载入、无横向溢出、两种手向、纤细手臂、4档弓与3种盾表达；同尺度图片已目视查看，边缘清晰，未见断柄或分离护手。证据为 `art/equipment-2d/six-*-light.png`、`-dark.png`、`-left-slim.png`、`-states.png`。这些均非游戏截图。

首次浏览器检查曾被自动审核以旧“仅三个VFX”范围拒绝；随后读取总控对话核实真实用户新指示和派工，再向同一审核路径提交后获准。没有更换工具绕过拒绝。

本批交付后，由统筹审阅六类视觉方向并安排下一批。代码方后续验证第一/第三人称、左右手、标准/纤细手臂、GUI/展示框/掉落、盾格挡与背柄、弓箭状态、资源重载和缺失依赖回退。安装提供方模组不属于本美术批次。

复现工具（均只写独立美术目录）：

```powershell
& ./art/tooling/collect-equipment-six.ps1
& 'C:/Program Files/nodejs/node.exe' art/tooling/build-equipment-six.mjs
& 'C:/Program Files/nodejs/node.exe' art/tooling/verify-equipment-six.mjs
```

前两步不需要网络。PNG处理依赖已有pngjs，浏览器检查依赖已有Playwright与Edge；`--inspect`仅截图、不会标记验收通过。重新生成会将browserVerified重置false。可发布清单不等于已经通过游戏资源准入。
