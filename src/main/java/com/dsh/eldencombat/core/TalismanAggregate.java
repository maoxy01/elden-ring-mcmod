package com.dsh.eldencombat.core;

import java.util.List;

/**
 * 护符栏的**聚合结果**（纯逻辑，不依赖 Minecraft，可单测）。
 *
 * <p>§3.3：战斗出口一律读这份聚合值，不各处自己乘。规则是
 * **先把所有护符的同项加成求和，再按 {@link TalismanRules} 的上下限夹紧一次**——
 * 逐件夹紧会让"三件各 +40% 实际叠到 +120%"这种配置绕过上限。
 *
 * <p>倍率的换算与逐件版本（{@link TalismanSpec#damageMultiplier()} 等）**完全一致**，
 * 所以单槽 + 默认规则下的数值与旧实现逐位相同。
 */
public record TalismanAggregate(int count, float maxFp, float staminaRegen, float damage,
                                float damageReduction, float heal, TalismanEffects effects) {

    public static final TalismanAggregate NONE = new TalismanAggregate(0, 0f, 0f, 0f, 0f, 0f, TalismanEffects.NONE);

    /** 没有任何护符生效。 */
    public boolean isNone() {
        return count <= 0;
    }

    /**
     * 聚合 + 夹紧。
     *
     * @param specs 已装备护符的参数（含 {@link TalismanSpec#NONE} 会被跳过）
     * @param rules 集中规则；为 null 时用默认规则（默认 = 既有单槽行为）
     */
    public static TalismanAggregate of(List<TalismanSpec> specs, TalismanRules rules) {
        TalismanRules limits = rules == null ? new TalismanRules() : rules;
        if (specs == null || specs.isEmpty()) {
            return NONE;
        }
        float maxFp = 0f;
        float regen = 0f;
        float damage = 0f;
        float reduction = 0f;
        float heal = 0f;
        int count = 0;
        for (TalismanSpec spec : specs) {
            if (spec == null || spec.isNone()) {
                continue;
            }
            count++;
            maxFp += spec.maxFp();
            regen += spec.staminaRegen();
            damage += spec.damage();
            reduction += spec.damageReduction();
            heal += spec.heal();
        }
        if (count == 0) {
            return NONE;
        }
        return new TalismanAggregate(count,
                clamp(maxFp, 0f, limits.maxFpBonus),
                clamp(regen, -0.9f, limits.maxStaminaBonus),
                clamp(damage, -0.9f, limits.maxDamageBonus),
                // 减伤允许为负（"烙印"类护符是有代价的：更脆），下限 -1（受伤 ×2）
                clamp(reduction, -1f, limits.maxDamageReduction),
                clamp(heal, 0f, limits.maxHealBonus), TalismanEffects.of(specs));
    }

    /** FP 上限加成（绝对值）。 */
    public float maxFpBonus() {
        return maxFp;
    }

    /** 精力恢复倍率（基础 1.0）。 */
    public float staminaRegenMultiplier() {
        return Math.max(0.1f, 1f + staminaRegen);
    }

    /** 造成伤害倍率（基础 1.0）。 */
    public float damageMultiplier() {
        return Math.max(0.1f, 1f + damage);
    }

    /** 受到伤害倍率（基础 1.0），夹在 [0.1, 2.0]（与逐件版本同口径）。 */
    public float damageTakenMultiplier() {
        return Math.min(2f, Math.max(0.1f, 1f - damageReduction));
    }

    /** 治疗量加成（绝对值）。 */
    public float healBonus() {
        return heal;
    }

    public String describe() {
        return String.format("护符数=%d FP上限+%.0f 精力×%.2f 伤害×%.2f 受伤×%.2f 治疗+%.1f",
                count, maxFp, staminaRegenMultiplier(), damageMultiplier(), damageTakenMultiplier(), heal)
                + (effects.equals(TalismanEffects.NONE) ? "" : " " + effects.describe());
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
