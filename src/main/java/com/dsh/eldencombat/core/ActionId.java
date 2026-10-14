package com.dsh.eldencombat.core;

/**
 * 动作标识。玩家动作与 Boss 动作分开：
 * 这里只描述玩家侧的动作状态机；Boss 侧动作由 margit.animations.json 的 clip id 描述。
 */
public enum ActionId {
    /** 无动作（待机） */
    NONE(false, false),
    LIGHT_ATTACK(true, false),
    HEAVY_CHARGE(false, false),
    HEAVY_ATTACK(true, false),
    CHARGED_HEAVY(true, false),
    ROLL(false, true),
    PARRY(false, false),
    DRINK(false, false),
    GUARD(false, false),
    GUARD_BROKEN(false, false),
    STAGGER(false, false),
    DEATH(false, false);

    private final boolean attack;
    private final boolean movementOverride;

    ActionId(boolean attack, boolean movementOverride) {
        this.attack = attack;
        this.movementOverride = movementOverride;
    }

    /** 是否为攻击动作（消耗精力、带命中窗口） */
    public boolean isAttack() {
        return attack;
    }

    /** 是否接管位移（翻滚） */
    public boolean isMovementOverride() {
        return movementOverride;
    }

    public boolean isBusy() {
        return this != NONE;
    }
}
