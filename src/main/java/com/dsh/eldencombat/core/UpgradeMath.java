package com.dsh.eldencombat.core;

/**
 * 强化曲线（《成长路径与手感收口方案》§2.3）。
 *
 * <p>两条曲线**必须分开**，否则后期补正会被基础伤害淹没、属性加点失去意义：
 * <pre>
 *   基础伤害倍率 B(t) = 1.00 + 1.00 × t            // 线性，满级 ×2.00
 *   补正伤害倍率 S(t) = 1.00 + 1.50 × t^1.6        // 加速，满级 ×2.50
 *   最终伤害 = 基础伤害 × B(t) + 补正伤害 × S(t)
 * </pre>
 *
 * <p>{@code S(t)} 用 1.6 次幂的意义：**前期靠武器本身，后期靠属性加点**。
 * 玩家在 +3 之前换武器几乎无损失、+7 之后换武器成本陡增——这正是"要不要把资源投给这把剑"
 * 的决策，也就是魂系武器系统的情感核心。
 */
public final class UpgradeMath {

    /** 基础伤害在满级时的额外倍率（线性项系数） */
    public static final double BASE_PER_T = 1.00;
    /** 补正在满级时的额外倍率（幂函数系数） */
    public static final double CORRECTION_PER_T = 1.50;
    /** 补正曲线的幂次 */
    public static final double CORRECTION_EXPONENT = 1.6;

    /** 类型武器的强化上限（不照搬原作的 +25：MC 的 tooltip 信息密度承受不了） */
    public static final int MAX_LEVEL_NORMAL = 10;
    /** 追忆武器的强化上限：终局奖励，跨度短而陡 */
    public static final int MAX_LEVEL_REMEMBRANCE = 5;

    private UpgradeMath() {
    }

    public static int maxLevel(boolean remembrance) {
        return remembrance ? MAX_LEVEL_REMEMBRANCE : MAX_LEVEL_NORMAL;
    }

    /** 归一化进度 {@code t = L / Lmax}（自动夹到 0..1） */
    public static double progress(int level, int maxLevel) {
        if (maxLevel <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) level / maxLevel));
    }

    /** 基础伤害倍率 */
    public static double baseMultiplier(double progress) {
        return 1.00 + BASE_PER_T * clamp01(progress);
    }

    /** 补正伤害倍率 */
    public static double correctionMultiplier(double progress) {
        return 1.00 + CORRECTION_PER_T * Math.pow(clamp01(progress), CORRECTION_EXPONENT);
    }

    /**
     * 材料是否够升到下一级：每级消耗该档位的锻造石数量。
     * 首版按"等级 +1 → 1 颗该档石头"实现，后续要调只改这里。
     */
    public static int stonesForNextLevel(int currentLevel) {
        return 1;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
