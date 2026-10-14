# 赐福像素美术交接 G-A1

日期：2026-10-13。原创资源草案，未同步运行目录、未接入地图或节点渲染、未实机验收。当前战斗回执尚无最终状态 DTO，以下字段是美术映射建议，不是已实现协议。

## 1. 交付与复用

- `art/grace-pixel/assets.json`：15 个素材的完整 ID、文件、尺寸、锚点、帧参数、原创逐像素网格、色板、逐 PNG SHA256 与现有图标复用记录。
- `art/grace-pixel/textures/map/`：古老/自建各五种状态，共 10 张透明 16×16 PNG。
- `art/grace-pixel/textures/ui/`：休息、旅行、战灰管理入口各一张透明 16×16 PNG；灰色按钮背景、字体、禁用/悬停态直接使用原版控件。
- `art/grace-pixel/textures/fx/`：一张 16×64 四帧金色贴片，一张 16×16 地面贴片。不是完整自定义粒子实现。
- `art/grace-pixel/preview.html`：浅/深背景、动画暂停；电脑与手机截图仅离线视觉检查。
- `art/tooling/build-grace-pixel-art.mjs`：可编辑的原创像素源，生成 PNG、清单、预览与校验；清单中的 frames 也可逐像素修改。

现有锻造石、失色锻造石、质变模板与星光碎片只读取检查尺寸/哈希，不复制、重绘或改名为战灰。Waystones 精确候选 JAR 有现成节点模型，本轮仅查看文件目录，未抽取其美术。先保留原版 Waystones 方块作为协议占位；本素材供后续自有赐福标识使用。

所有新增网格为本轮原创，按 `LICENSE.txt` 的 CC0-1.0 声明交付，没有复制 ARR 模组贴图。`assets.json.hashes` 是逐文件交接依据，不等于游戏兼容或第三方许可证明。未生成 Game Development Studio canonical package：本机未发现 game-dev 命令；这里是原工程的轻量 PNG/JSON 交付，不宣称通过该工具的准入/引擎验证。

## 2. 地图状态与 ID

文件规则：清单 ID 为 `eldencombat:grace_<origin>_<state>`；具体纹理 ResourceLocation 为 `eldencombat:textures/map/grace_<origin>_<state>.png`。origin 仅 `ancient` / `player`，分别为古老赐福与玩家自建。资源 ID **不是物品注册 ID或节点 UID**。

| state | 外观 | 使用条件 |
| --- | --- | --- |
| undiscovered | 灰色基础符号 | 未发现默认不放地图 marker；仅在服务端允许显示的列表/教学场景使用，不暴露未知坐标 |
| discovered | 金色基础符号 | 已发现，尚无公共/私人语义或不需要显示权限标记 |
| public | 金色符号加小十字路标 | 服务端同步为公共显示状态；不直接赋予旅行权限 |
| private | 金色符号加小锁 | 服务端允许该玩家看到的私人节点；不向无权限玩家泄露私人位置 |
| invalid | 灰色符号加红叉 | 已显示节点收到撤销/失效通知后可短暂表示；随后按接口删除 marker |

例如 `eldencombat:grace_ancient_public` 和 `eldencombat:grace_player_private`。10 个明确 ID 与 PNG 路径均见清单。公共/私人/失效用颜色和小符号双重区分；古老基底分叉，自建基底菱形，不依赖颜色区分类别。

建议适配层输入：真实 `nodeUid: UUID`、`origin: ancient|player`、`discovered: boolean`、`visibility: public|private|unspecified`、`valid: boolean`，以及服务端决定的 `visibleToViewer`。这些只是对应美术语义的字段，不能从 waypoint 名称、图标或 owner 是否为空推断。

选择顺序：不允许可见 → 不创建 marker；可见节点失效 → invalid（或按协议立即移除）；未发现 → 隐藏地图；已发现且 private/public → 对应带标记 PNG；其他已发现 → discovered。不要将美术建议直接当权限校验。现成 WaystoneOrigin/visibility 枚举到自有语义的转换由战斗部门明确。

地图像素锚点 `[8,8]`，原图 16×16；屏幕可按整倍数呈现，nearest 采样。地图 API 的实际锚点单位由适配器换算，不能未经核对照搬像素值。坐标和节点 UID 只取真实同步数据，不另维护节点网络。

## 3. 界面与空间贴片

| ID | 文件 | 帧与锚点 | 用途 |
| --- | --- | --- | --- |
| eldencombat:grace_rest | textures/ui/grace_rest.png | 16×16 单帧；UI 左上 `[0,0]` | 原版灰色休息按钮图标 |
| eldencombat:grace_travel | textures/ui/grace_travel.png | 同上 | 旅行按钮箭头 |
| eldencombat:grace_ashes | textures/ui/grace_ashes.png | 同上 | 战灰管理入口；不是某个奖励战灰的物品贴图 |
| eldencombat:grace_flame | textures/fx/grace_flame.png | 16×64，4 帧从上至下；每帧 4 ticks，循环 16 ticks；帧内锚点 `[8,14]` | 金色赐福小型竖向贴片 |
| eldencombat:grace_ground | textures/fx/grace_ground.png | 16×16 单帧；中心 `[8,8]` | 小型不规则像素地面符号，无连续圆弧 |

按 20 TPS，flame 每帧 0.2 秒，全周期 0.8 秒。运行采样建议 `floor((gameTime + phaseTicks)/4) % 4`；相位仅改变视觉。动画源在清单；如使用原版纹理动画，需要接入方生成等价 `.mcmeta` 或拆四帧并注册 SpriteSet，不能把整个 16×64 图当单张方形 billboard。当前没有交付注册 JSON 或 `.mcmeta`，避免暗示已进入粒子图集。

旅行规则更新：用户已确认**非战斗时可从野外通过地图前往已发现赐福**。旅行按钮不能因为玩家不在节点附近就一律禁用，可用/禁用及拒绝原因由服务器规则驱动；休息与补 FP 仍只允许合法近距离节点交互。该规则不改变图标资源，不新增界面或网络实现。

世界尺寸建议：flame `0.375×0.375` 格，每可见节点最多两平面；ground `0.5×0.5` 格，表面抬高建议 `0.003` 格防止 Z-fighting。有限不透明/半透明色阶，无模糊滤镜、圆形大光环或复杂骨架。使用正常 alpha 混合，若启用自发光只影响自身，不要求修改光照系统。尺寸、距离裁剪及全局数量上限由接入方按实机预算确定。

**节点接口先行**：先保留原 Waystones 碰撞箱、交互与合法旅行语义；自有金色贴片只是绑定真实节点的显示。不要用装饰实体代替实际节点，也不要只隐藏碰撞模型而留下不合理点击区域。最终小型赐福若要求变更节点方块外观/高度，需要战斗确认公开扩展是否支持后再做模型；本轮未替换 Waystones 方块纹理或渲染器。

## 4. 验证状态与实机需求

已通过：15 个 PNG 尺寸、逐帧非空和透明背景、10 状态文件唯一；已有图标只读复用记录；Edge 1200×900 / 390×844 浅/深预览无横向溢出，暂停与背景切换可用；已目视检查深色总览。私人标记已局部改为两像素锁拱、两侧立柱与下方实心锁体，预览底部补古老/自建的私人/公共/失效 16/32 像素原尺寸对照。16 像素锁仍很小，最终由实际地图与用户验收，不能以文件字节不同代替人眼辨识。详见 `validation.json`。这些不证明实际地图采样、按钮或游戏渲染正确；低 alpha 地面/火焰的真实场景可见性留待联调。

战斗部门同步/构建后，请交付以下实机证据：

1. 地图实际缩放下古老/自建可区分，公共/私人/失效标记在 16 像素和缩放后仍可读；没有重复原生 waypoint。
2. 未发现/无权查看节点不出现在地图；删除与撤权后旧图标不能继续请求旅行；图标不是权限来源。
3. 休息/旅行/战灰入口的正常、悬停、禁用状态及中文文字布局；仅合法休息补 FP，管理入口不能越权开放。
4. 贴片帧 UV 正确，无整张长条显示、透明黑底、地面闪烁或夸张遮挡；一个与多个节点、远近距离、昼夜截图。
5. 节点标识与交互区域吻合；保留第三方正常路径，不产生新增网络/重复发现存储。两玩家可见性与权限不同也显示正确。

本部门没有运行 Gradle、复制到 src/main/resources、启动 Minecraft 或测试网络。状态维持 `runtimeIntegrated=false` / `inGameAccepted=false`；由战斗提交实机证据后再更新，不以离线预览冒充完成。
