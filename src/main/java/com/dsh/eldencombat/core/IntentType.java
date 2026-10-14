package com.dsh.eldencombat.core;

/**
 * 客户端提交的动作意图。请求只包含意图、连续序号、可选目标与规范化方向，
 * 不包含伤害、精力余额、动作进度或命中结论。
 */
public enum IntentType {
    /** 普通轻攻击（地面与空中同规则） */
    LIGHT,
    /** 开始蓄力重击 */
    HEAVY_START,
    /** 松开蓄力重击 */
    HEAVY_RELEASE,
    /** 翻滚 */
    ROLL,
    /** 盾牌弹反 */
    PARRY,
    /** 喝药 */
    DRINK,
    /** 按住持盾防御 */
    GUARD_START,
    /** 松开防御 */
    GUARD_STOP,
    /** 切换战斗模式 */
    COMBAT_TOGGLE,
    /** 锁定 / 解除锁定（targetId = -1 表示解除） */
    LOCK_ON,
    /** 主动取消（打开界面 / 失焦 / 退出战斗模式） */
    CANCEL,
    /**
     * 释放主手武器绑定的战技；若正处于该战技的追击窗口内，则推进到下一段。
     * **必须追加在枚举末尾**：意图在网络上按序号传输，插在中间会与旧客户端错位。
     */
    SKILL
}
