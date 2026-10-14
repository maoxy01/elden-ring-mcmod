# 八战技事件表（当前源码）

2026-10-12。单位 tick（20 tick/s）。依据 RemembranceSkillSpec、SkillDriver、SkillCastState、SkillTransactions、RemembranceRuntime；本表不等于实机验收。当前参数未调整。

共同阶段编号：IDLE=0/WINDUP=1/ACTIVE=2/FOLLOWUP_WINDOW=3/RECOVERY=4；stageIndex 起手从 0 开始，进入 ACTIVE 增为 1。初段 accepted tick 为 T；ACTIVE 从 T+windup 开始。追击扣费接受时再 WINDUP，段号保留至 ACTIVE 再加一。窗口包含 deadline，deadline+1 收招。

FP 在请求服务端合法性通过的原子事务扣除；网络 Intent 的重复/过期 sequence、非窗口、FP 不足均拒绝且不扣费。客户端此路径只发 SKILL 意图，不带 castId/stageIndex；段号和技能由服务端决定，matchesRequest 仅为纯逻辑 helper，不把其测试当网络 ID 验证。冷却在初段接受时登记，八技当前 cooldown=0；没有额外固定冷却。收招前 max(1,recovery-2) tick 硬直，余下最多2 tick可接普通攻击、翻滚或主动取消；保持原总时长。移动沿原版物理，尚未增加移动包拦截。

提示最早 accepted 后；客户端消费 FpSync/SkillCast 同步阶段。没有增加网络字段。GoldenWave 保持方向、半径、travel/progress/blockedLanes 语义，owner() 服务端 UUID；SkillEffect 保持 ownerEntityId、phase、progress、follow。未来美术若需 castId 对齐必须先登记消费需求。

## wave_of_gold

物品：eldencombat:sacred_relic_sword

| FP 初段/追击 | WINDUP | ACTIVE | RECOVERY | 追击窗 | maxStages | 距离/半径/寿命 | 伤害/削韧 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 20/0 | 10 | 4 | 5 | 0 | 1 | 16/3/30 | 14/12 |

T+10 生成非火焰地面波，速度 .9 格/tick，半径 3→4.2（WaveFront），方向为起手冻结水平朝向；世界坐标沿地面传播，最多 16 格/30 tick，不跟随玩家。每目标整波一次；中心墙停止，lane 遮挡永久登记，无地面停止。施放 T+19 完成，波允许独立继续传播。

当前数值保持；RECOVERY=5 分为硬直3/尾段2，RECOVERY=2（仪式）分为硬直1/尾段1。单测覆盖八技边界；实机衔接仍待验。

## gold_breaker

物品：eldencombat:marikas_hammer

| FP 初段/追击 | WINDUP | ACTIVE | RECOVERY | 追击窗 | maxStages | 距离/半径/寿命 | 伤害/削韧 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 22/0 | 8 | 16 | 5 | 0 | 1 | 3/3/20 | 26/30 |

T+5 起跳（.32 水平/.5 垂直），进入 ACTIVE 后由落地探测一次结算 26 伤害，玩家落点世界坐标半径 3；GOLD_IMPACT 不跟随，寿命 12、phase=0。落水无伤害；虚空返起跳点退还 50% 本段 FP；超时吸附地面结算。

当前数值保持；RECOVERY=5 分为硬直3/尾段2，RECOVERY=2（仪式）分为硬直1/尾段1。单测覆盖八技边界；实机衔接仍待验。

## destined_death

物品：eldencombat:malikeths_black_blade

| FP 初段/追击 | WINDUP | ACTIVE | RECOVERY | 追击窗 | maxStages | 距离/半径/寿命 | 伤害/削韧 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 25/0 | 8 | 24 | 5 | 0 | 3 | 4/3.5/60 | 12/14 |

T+5 起跳；ACTIVE 24 tick 内三次自动 burst，e=0/8/16，各 burst 前 6 tick 判胶囊近战（当前视线、胸口），独立 channel 去重。DEATH_SLASH 跟随玩家，phase=burst，寿命 8；有效命中附 DestinedDeathEffects。maxStages=3 并不表示三次输入追击，窗口=0。

当前数值保持；RECOVERY=5 分为硬直3/尾段2，RECOVERY=2（仪式）分为硬直1/尾段1。单测覆盖八技边界；实机衔接仍待验。

## takers_flames

物品：eldencombat:blasphemous_blade

| FP 初段/追击 | WINDUP | ACTIVE | RECOVERY | 追击窗 | maxStages | 距离/半径/寿命 | 伤害/削韧 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 20/0 | 9 | 4 | 5 | 0 | 1 | 12/2.5/40 | 13/10 |

T+9 生成火焰地面波，速度 .65，固定半径 2.5，12 格/40 tick，方向冻结世界坐标；每目标整波一次，有效伤害每次回复 1 HP，上限 4。

当前数值保持；RECOVERY=5 分为硬直3/尾段2，RECOVERY=2（仪式）分为硬直1/尾段1。单测覆盖八技边界；实机衔接仍待验。

## waterfowl_dance

物品：eldencombat:hand_of_malenia

| FP 初段/追击 | WINDUP | ACTIVE | RECOVERY | 追击窗 | maxStages | 距离/半径/寿命 | 伤害/削韧 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 28/0 | 10 | 36 | 5 | 0 | 3 | 3/3/20 | 9/8 |

T+7 起跳；ACTIVE 36 tick 内三次自动 burst，e=0/12/24，各前 6 tick 胶囊近战，burst 冲量 .28/.32。WATERFOWL 跟随，phase=burst，寿命 8；独立 channel 去重。maxStages=3 是身份参数，没有输入追击。

当前数值保持；RECOVERY=5 分为硬直3/尾段2，RECOVERY=2（仪式）分为硬直1/尾段1。单测覆盖八技边界；实机衔接仍待验。

## starcaller_cry

物品：eldencombat:starscourge_greatsword

| FP 初段/追击 | WINDUP | ACTIVE | RECOVERY | 追击窗 | maxStages | 距离/半径/寿命 | 伤害/削韧 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 15/15 | 8 | 6 | 5 | 12 | 2 | 5/5/20 | 10/16 |

初段不跃起，T+8 半径 5 一次范围伤害并于 ACTIVE 持续拉近（小于 1.2 格停止）；GRAVITY_PULL 世界坐标固定，phase=0 寿命12。T+14 打开追击窗直到 T+26，未追击 T+27 收招。追击 WINDUP=6/ACTIVE=8，于追击接受+3 tick 起跳，重新武装 landing；段2落地一次冲击倍率2.5，半径3.5，GRAVITY_IMPACT 寿命12 phase=0。二段落地冲击待实机复验。

当前数值保持；RECOVERY=5 分为硬直3/尾段2，RECOVERY=2（仪式）分为硬直1/尾段1。单测覆盖八技边界；实机衔接仍待验。

## bloodboon_ritual

物品：eldencombat:mohgwyns_sacred_spear

| FP 初段/追击 | WINDUP | ACTIVE | RECOVERY | 追击窗 | maxStages | 距离/半径/寿命 | 伤害/削韧 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 15/15 | 6 | 4 | 2 | 10 | 3 | 4/4/20 | 11/10 |

T+6 半径4 范围伤害一次、BLOOD_RITUAL 世界坐标不跟随，phase=段号-1，寿命16。T+10 打开10 tick追击窗；追击 WINDUP=6/ACTIVE=4，最多3个输入段，各扣15 FP、各重新去重。

当前数值保持；RECOVERY=5 分为硬直3/尾段2，RECOVERY=2（仪式）分为硬直1/尾段1。单测覆盖八技边界；实机衔接仍待验。

## cursed_blood_slice

物品：eldencombat:morgotts_cursed_sword

| FP 初段/追击 | WINDUP | ACTIVE | RECOVERY | 追击窗 | maxStages | 距离/半径/寿命 | 伤害/削韧 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 12/8 | 8 | 6 | 5 | 10 | 2 | 4/3/20 | 13/12 |

T+5 冲量 .3/.25；T+8 CURSED_SLASH 跟随玩家 phase=段号、寿命8；ACTIVE e<4 胶囊命中(channel0)，e=4 在生效时冻结原点+起手方向1.5格范围火焰(channel1)，半径2/倍率.5，BLOOD_FLAME 不跟随，寿命12。T+14 打开10 tick追击窗；追击 WINDUP=4/ACTIVE=4，因此当前 e=4 会在 ACTIVE 退出的 tick 执行延迟爆发。

当前数值保持；RECOVERY=5 分为硬直3/尾段2，RECOVERY=2（仪式）分为硬直1/尾段1。单测覆盖八技边界；实机衔接仍待验。

## 清理及已知缺口

死亡/主手改变/阶段超时由 SkillDriver.cancel；离线/换维度由 SkillServerEvents 中断。Runtime 恢复 motion，未落地转安全网仅救援不伤害。特效按寿命清除；波 owner 失效/死亡即清除，Runtime 现追踪本次产生的实体，onCancel 主动 discard，覆盖尚处施放内的切武器/死亡/离线/换维度取消；正常 onFinish 保留波继续传播。finish 后再换武器的波仍按寿命存活，此行为待验收。命中顿帧本轮没有增加。


共享接口登记：SkillEffectEntity.spawn 返回生成实体供 Runtime 追踪；现有调用方仍可忽略返回值。没有增加同步字段或改变 ownerEntityId、phase、progress/跟随语义。


生命周期更新：logout/clone/dimension 走 detach，取消后移除本Runtime孤立落地记录，防止旧维度起跳点跨维度救援。Clone以LOWEST晚于NeoForge附件复制处理FP补满，死亡/维度/存档重启均已有实机日志。


