package com.dsh.eldencombat.core;

/**
 * 失衡（削韧）的纯计算层：上限缩放、崩解窗口时长、崩解期易伤倍率。
 *
 * <p>放在 core 里（不依赖 MC 类型），因此单测和游戏内 selftest 可以共用同一份断言。
 *
 * <p>设计依据（《成长路径与手感收口方案》§6）：失衡打满不是"白给一段硬直"，
 * 而是开出一个 2 秒的**崩解窗口**——敌人硬直、受到伤害 +50%。
 * 多人时崩解倍率不变（那是"敌人变脆"，不是"玩家变强"），
 * 但失衡上限要按人数放大，否则多人会秒破防、崩解循环直接崩掉。
 */
public final class PoiseMath {

    private PoiseMath() {
    }

    /**
     * 按在场玩家数放大失衡上限：{@code base × (1 + k × (n-1))}。
     *
     * @param players         在场玩家数（&lt;1 视为 1，单人时结果等于 base）
     * @param perExtraPlayer  每多一名玩家的缩放比例；负值按 0 处理（不缩小上限）
     */
    public static float maxForPlayers(float baseMax, int players, float perExtraPlayer) {
        int count = Math.max(1, players);
        float scale = 1f + Math.max(0f, perExtraPlayer) * (count - 1);
        return baseMax * scale;
    }

    /**
     * 崩解窗口时长 = max(硬直动作时长, 配置下限)。
     * 动作必须播完（否则姿态被下一段动作截断），配置下限用于保证窗口"值得追"。
     */
    public static int breakTicks(int animationTicks, int configured) {
        return Math.max(Math.max(1, animationTicks), Math.max(0, configured));
    }

    /**
     * 崩解期易伤倍率：未崩解恒为 1；崩解期取配置值，但不允许小于 1
     * （配置写错时宁可没有收益，也不能让崩解的敌人反而更抗打）。
     */
    public static float damageTakenMultiplier(boolean broken, float multiplier) {
        return broken ? Math.max(1f, multiplier) : 1f;
    }
}
