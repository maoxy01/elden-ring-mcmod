# 赐福代码框架

本轮按用户要求先集中编写框架，不编译、不运行测试、不启动游戏，不制作或修改美术。当前是未验收的原型代码。

## 模块与调用路径

| 模块 | 职责 |
| --- | --- |
| client/grace/GraceMapPlugin | 接收本人可见节点快照，地图菜单发送真实 UID 与请求序号 |
| network/GraceNodesPayload | 服务端至客户端的展示快照，不作为授权依据 |
| network/GraceTravelPayload | 客户端至服务端的旅行请求，不接受坐标、owner 或 public 参数 |
| registry/ModPayloads | 两端注册协议，服务端线程处理请求 |
| server/grace/GraceRegistry | 保存赐福 UID 登记，提供登记/撤销；节点本体、发现、名字仍由 Waystones 保存 |
| server/grace/GraceSettings | 集中原型配置：战斗窗口、旅行冷却、同步间隔、可选近节点出发限制 |
| server/grace/GraceAccess | 地图披露、休息、旅行共用的有效性、发现与权限入口 |
| server/grace/GraceRest | 真实方块近距离交互恢复既有 FP；独立于旅行冷却和落点计算 |
| server/grace/GraceService | 战斗状态、请求去重、原生旅行拦截、落点检查、异步结果及瞬态清理 |

默认流程：地图菜单 → UID 请求 → 服务端重取节点 → 活动/发现/权限/冷却/落点判断 → Waystones 默认 context → tryTeleportAsync → 服务端反馈。原生旅行入口通过 Pre 和 requirement 装饰接入相同规则。野外出发默认允许。

休息流程：方块交互 → 真实节点、同维度与交互距离 → 活动/发现/权限 → 现有 FP 数据恢复与同步。地图查看及旅行不会调用休息入口。床与原版重生由现有流程处理。

## 接下来的实现边界

- 当前仍由 gracePrototype 开关启用；发布依赖准入和正式配置迁移另做。
- TEAM/共享节点的精确权限适配尚未完成，当前权限采用保守原型规则。
- 异步期间的撤销、失败扣费回退、落点区块边界、并发和断线需要集中复核。
- 地图跨世界清理、失效同步及实际菜单操作需要集中联调。
- 两真实客户端的发现隔离、原生菜单/道具一致性和重启旅行仍待验收。
- 后续遗迹奖励引用已有武器成长与技能运行时；本轮不提前添加第二套成长系统。

本轮备份：docs/code-backups/20261013-grace-framework。这里记录代码职责，不将接口存在、日志名称或编译记录当作玩法通过证据。
