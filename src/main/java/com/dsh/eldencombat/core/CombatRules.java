package com.dsh.eldencombat.core;

/**
 * 动作接受/取消/窗口判定的纯规则。服务端与单测共用同一份实现，避免两边规则漂移。
 */
public final class CombatRules {

    private CombatRules() {
    }

    /**
     * 判断在当前状态下能否接受新动作。
     * 规则：攻击前摇与有效窗口不可被新动作打断；后摇最后 N tick 允许翻滚取消；
     * 蓄力可被翻滚或打断取消；喝药只按自身规则（受击/翻滚取消）。
     */
    public static boolean canAccept(CombatState state, ActionId next, long now) {
        if (state.action == ActionId.DEATH || state.action == ActionId.STAGGER
                || state.action == ActionId.GUARD_BROKEN) {
            return false;
        }
        if (state.action == ActionId.NONE) {
            return true;
        }
        int tick = state.tickInAction(now);
        if (next == ActionId.ROLL) {
            if (state.action == ActionId.HEAVY_CHARGE) {
                return true;
            }
            return state.spec != null && state.spec.rollCancelAllowed(tick);
        }
        return false;
    }

    /** 精力是否足够；不足则不播放动作、不发伤害。 */
    public static boolean staminaOk(CombatState state, ActionSpec spec) {
        return state.hasStamina(spec.staminaCost());
    }

    /** 蓄力是否已经超过自动释放上限（最多 30 tick 自动释放） */
    public static boolean chargeExpired(CombatConfig config, CombatState state, WeaponClass weaponClass) {
        if (state.action != ActionId.HEAVY_CHARGE) {
            return false;
        }
        return state.chargeTicks >= Math.max(1, config.weapon(weaponClass).chargeMaxTicks);
    }

    /** 蓄力是否达到满蓄力 */
    public static boolean chargeFull(CombatConfig config, CombatState state, WeaponClass weaponClass) {
        return state.chargeTicks >= config.weapon(weaponClass).chargeFullTicks;
    }

    /** 选择释放后的动作：满蓄力 -> 满蓄力重击，否则普通重击 */
    public static ActionId chargedOrNormal(CombatConfig config, CombatState state, WeaponClass weaponClass) {
        return chargeFull(config, state, weaponClass) ? ActionId.CHARGED_HEAVY : ActionId.HEAVY_ATTACK;
    }

    public static ActionSpec specFor(CombatConfig config, WeaponClass weaponClass, ActionId id) {
        return switch (id) {
            case LIGHT_ATTACK -> config.lightSpec(weaponClass);
            case HEAVY_ATTACK -> config.heavySpec(weaponClass);
            case CHARGED_HEAVY -> config.chargedHeavySpec(weaponClass);
            case ROLL -> config.rollSpec();
            case PARRY -> config.parrySpec();
            case DRINK -> config.drinkSpec();
            case GUARD_BROKEN -> config.guardBrokenSpec();
            default -> throw new IllegalArgumentException("动作没有规格: " + id);
        };
    }

    /** 攻击序号是否比上次新（防重放：旧序号或相等序号一律拒绝且不消耗资源）。 */
    public static boolean sequenceIsNewer(int incoming, int lastAccepted) {
        return incoming > lastAccepted;
    }
}
