package com.dsh.eldencombat;

import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.core.PoiseMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 削韧 payoff（§6 崩解）的纯逻辑：上限缩放、窗口时长、易伤倍率。
 *
 * <p>这些数字决定"失衡打满之后玩家能不能感知到收益"，且完全不依赖 MC 类型，
 * 所以单测与游戏内 selftest 用同一份公式。
 */
class PoiseMathTest {

    @Test void singlePlayerKeepsBaseLimit() {
        assertEquals(100f, PoiseMath.maxForPlayers(100f, 1, 0.5f), 1.0e-4);
        assertEquals(100f, PoiseMath.maxForPlayers(100f, 0, 0.5f), 1.0e-4, "空玩家数按单人处理");
    }

    @Test void multiplayerScalesLimitSoBreakLoopSurvives() {
        assertEquals(150f, PoiseMath.maxForPlayers(100f, 2, 0.5f), 1.0e-4);
        assertEquals(200f, PoiseMath.maxForPlayers(100f, 3, 0.5f), 1.0e-4);
        assertEquals(100f, PoiseMath.maxForPlayers(100f, 3, 0f), 1.0e-4, "缩放系数为 0 时不放大");
    }

    @Test void negativeScaleNeverShrinksTheLimit() {
        assertEquals(100f, PoiseMath.maxForPlayers(100f, 4, -1f), 1.0e-4);
    }

    @Test void breakWindowIsAtLeastTheHardStunAnimation() {
        assertEquals(70, PoiseMath.breakTicks(70, 40), "动作比配置长时以动作为准");
        assertEquals(40, PoiseMath.breakTicks(20, 40), "动作比配置短时补到 2 秒窗口");
        assertEquals(40, PoiseMath.breakTicks(40, 40));
        assertEquals(1, PoiseMath.breakTicks(0, 0), "窗口不允许为 0");
    }

    @Test void brokenTargetTakesConfiguredExtraDamage() {
        assertEquals(1f, PoiseMath.damageTakenMultiplier(false, 1.5f), 1.0e-6);
        assertEquals(1.5f, PoiseMath.damageTakenMultiplier(true, 1.5f), 1.0e-6);
        assertEquals(1f, PoiseMath.damageTakenMultiplier(true, 0.5f), 1.0e-6,
                "配置写错时宁可没有收益，也不能让崩解的敌人更抗打");
    }

    @Test void configExposesTheSameContract() {
        CombatConfig config = CombatConfig.defaults();
        assertEquals(40, config.poise.brokenTicks);
        assertEquals(1.5f, config.poise.brokenDamageMultiplier, 1.0e-6);
        assertEquals(100f, config.poiseMaxFor(1), 1.0e-4);
        assertEquals(150f, config.poiseMaxFor(2), 1.0e-4);
        assertEquals(1.5f, config.poiseDamageTakenMultiplier(true), 1.0e-6);
        assertEquals(1f, config.poiseDamageTakenMultiplier(false), 1.0e-6);
    }

    @Test void normalizeRepairsBrokenBreakConfig() {
        CombatConfig config = CombatConfig.defaults();
        config.poise.brokenTicks = -5;
        config.poise.brokenDamageMultiplier = 0.2f;
        config.poise.multiplayerScalePerExtraPlayer = -3f;
        config.normalize();
        assertEquals(40, config.poise.brokenTicks);
        assertEquals(1.5f, config.poise.brokenDamageMultiplier, 1.0e-6);
        assertEquals(0f, config.poise.multiplayerScalePerExtraPlayer, 1.0e-6);
    }
}
