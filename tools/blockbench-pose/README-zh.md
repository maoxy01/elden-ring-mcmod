# Elden Combat 动作编辑器

适用于 Blockbench **5.2.2+ 桌面版**与当前 Minecraft 1.21.1 六关节战技。日常预览和修改不需要启动 Minecraft，也不需要为模组增加依赖。

## 打开与编辑

1. 在 Blockbench“文件 → 插件”中选择“从文件加载插件”，打开 `dist/eldencombat_pose.js`。本次安装已在当前电脑完成。
2. 选择“工具 → Elden Combat：打开项目动作”。第一次选择项目根目录（包含 `src`、`art`），并允许该插件访问此目录。可选择“Always allow for this plugin”记住授权。
3. 选择战技。右侧面板显示当前战技和武器，原生动画列表显示阶段；默认选中右臂轨道，方便直接编辑。
4. 在时间轴新增、删除或拖动**旋转**关键帧，或通过原生关键帧面板编辑角度；`Ctrl+Z` 撤销。面板支持逐 tick、慢放和循环。每秒 20 tick。
5. 使用“完整流程（只读）”预览“不追击”或“立即追击至最大段数”。编辑时切回具体阶段。新增分段动作使用面板的“新增阶段／段号覆盖”。
6. **点击“保存回项目动作 JSON”才会改变游戏动作数据。** `Ctrl+S` 和“另存编辑工程”保存的是 `.bbmodel`，两种保存有明确区别。

源动作保存在 `src/main/resources/assets/eldencombat/skill_poses/`。保存前在同目录生成带时间戳及随机后缀的 `.bak`，随后替换 JSON；源文件被其他工具修改时拒绝覆盖。备份不会以 `.json` 结尾，因此不会被游戏当作另一份动作加载。

八个现成编辑工程位于 `evidence/projects/`。工程内嵌参考玩家、武器几何与贴图，保留项目路径、原始数据哈希及阶段映射。必须在安装插件后打开；保留旧哈希可以发现工程保存后源文件发生的冲突。

## 坐标、时序与边界

- 玩家保持六个互不继承身体旋转的原版关节；采用标准宽臂模型、中立站姿，根缩放为原版 PlayerRenderer 的 0.9375。
- 编辑器采用 Y 向上坐标。原版旋转 `[x,y,z]` 映射为编辑器 `[-x,-y,z]`，ZYX 旋转顺序；导出会转换回原版角度。插件界面角度因此可能与源 JSON 的 X/Y 符号不同。
- 未写入某阶段的关节使用中立站姿。游戏中它们仍受原版行走、潜行、持物等动画影响，离线工具没有模拟这些叠加。
- 段号与运行时一致：首段 WINDUP 为 0，首段 ACTIVE 为 1；追击 WINDUP 保持上一 ACTIVE 段号，下一 ACTIVE 再加一。无追击时窗口保留 `followupWindowTicks+1` tick，与服务端 `tick > deadline` 一致。
- 阶段使用 `STAGE#段号 → STAGE` 回退；缺少收招或追击窗口时冻结当前 ACTIVE 轨道的末帧。插值为数值线性插值，不跨 ±180°走最短弧。
- 武器读取 Gradle 正在使用的 `routes/2d.json`、厚度模型清单和校验通过的实际持物 PNG；沿透明轮廓生成与运行模型相同的一单位厚度。使用真实第三人称显示变换、左右手镜像和钻石剑参考厚度 0.85，显示变换只施加一次。
- 武器和持握参数只读。保存动作时拒绝模型层级或几何修改、非六关节轨道、位移、缩放、表达式、曲线和全局／四元数插值。运行时不支持的模型会显示错误，不显示替代占位物。
- 完整流程是阶段姿态预览；不包含人物位移、碰撞、命中、粒子、刀光、第一人称或其他模组渲染。独立 `sourceMotionProfile` 本地测试覆盖不属于此工具的数据源。

保存 JSON 后按项目现有流程构建／同步资源；如果游戏已经运行，资源重新加载读取的是游戏实例中的资源，不能假定源码保存会自动同步到已启动实例。

## 构建与验证

无需安装 npm 依赖，使用现成 Node.js：

```powershell
node build.cjs
node --test test/core.test.cjs
```

测试默认项目为 `D:/codexout/mcmod`，可通过 `ELDEN_PROJECT` 覆盖。Java 对照使用项目实际源文件、已有 Gson/SLF4J 依赖与 `D:/JDK21`；报告在 `evidence/java-parity.json`。

`test/live-blockbench.js` 在已安装插件的 Blockbench 中运行，验证八个实际动作、左右手、厚度、原生关键帧和工程往返。它仅保存、正常关闭自己新建的验证工程，不关闭运行前已存在的标签页。

需要重复原生回写测试时，先运行 `node test/prepare-fixture.cjs`，再执行 `test/live-save.js`。此测试只写工具目录下的动作副本。

`test/game.init.gradle` 与 `test/game/` 提供独立验收 source set；仅在显式 `-I` 加载时参与运行，不修改正式 Java 源码或常规模组包。游戏对照报告与截图的状态见 `evidence/ACCEPTANCE.md`。

## 开源复用

复用 [Blockbench](https://github.com/JannisX11/blockbench) 的模型、时间轴、关键帧、撤销与插件接口；此插件本身按 MIT 发布，没有复制或修改 Blockbench 主程序。
调研参考：[Emotecraft 动画工作流](https://github.com/KosmX/emotes/tree/dev/blender)、[BlockBenchMCP](https://github.com/RenasDemirbas/BlockBenchMCP)、[Epic Fight Blender 插件](https://github.com/Antikythera-Studios/blender-json-addon)。当前工具不依赖后三者。
参考玩家皮肤是程序生成的示意图；武器及其素材沿用所选项目的授权范围，不因插件的 MIT 许可而改变。
