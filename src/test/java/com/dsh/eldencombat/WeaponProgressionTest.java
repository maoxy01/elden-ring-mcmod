package com.dsh.eldencombat;

import com.dsh.eldencombat.core.Affinity;
import com.dsh.eldencombat.core.AttributeKind;
import com.dsh.eldencombat.core.AttributeSet;
import com.dsh.eldencombat.core.CorrectionGrade;
import com.dsh.eldencombat.core.UpgradeMath;
import com.dsh.eldencombat.core.WeaponDefinition;
import com.dsh.eldencombat.core.WeaponProfile;
import com.dsh.eldencombat.core.WeaponProfiles;
import com.dsh.eldencombat.core.WeaponState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §2 锻造与质变的数值核心：两条曲线、质变规则、状态机、补正接口契约。
 *
 * <p>对应方案 §7.2 的可机检清单第 1–4 条：
 * 曲线误差、质变后基础伤害与档位、等级保留、追忆武器战灰锁定。
 */
class WeaponProgressionTest {

    private static final double EPS = 1.0e-6;

    private static WeaponDefinition straightSword() {
        return WeaponDefinition.of(WeaponProfiles.of("straight_sword"), false);
    }

    // ---------------------------------------------------------------- 曲线

    @Test void baseCurveIsLinearAndCorrectionCurveIsAccelerating() {
        assertEquals(1.00, UpgradeMath.baseMultiplier(0.0), EPS);
        assertEquals(2.00, UpgradeMath.baseMultiplier(1.0), EPS);
        assertEquals(1.50, UpgradeMath.baseMultiplier(0.5), EPS);

        assertEquals(1.00, UpgradeMath.correctionMultiplier(0.0), EPS);
        assertEquals(2.50, UpgradeMath.correctionMultiplier(1.0), EPS, "满级补正倍率 = 1 + 1.5");
        // +9（t=0.9）时按公式应为 1 + 1.5×0.9^1.6
        assertEquals(1 + 1.5 * Math.pow(0.9, 1.6), UpgradeMath.correctionMultiplier(0.9), EPS);
    }

    @Test void correctionCurveOvertakesBaseCurveOnlyLate() {
        // 前期两条曲线接近（换武器几乎无损失），后期补正明显更快（换武器成本陡增）
        assertTrue(UpgradeMath.correctionMultiplier(0.2) < UpgradeMath.baseMultiplier(0.2));
        assertTrue(UpgradeMath.correctionMultiplier(0.6) > UpgradeMath.baseMultiplier(0.6));
        assertTrue(UpgradeMath.correctionMultiplier(1.0) > UpgradeMath.baseMultiplier(1.0));
    }

    @Test void everyNormalLevelMatchesTheFormulaWithinTolerance() {
        WeaponDefinition definition = straightSword();
        int max = definition.maxLevel();
        assertEquals(10, max, "类型武器 +0~+10");
        for (int level = 0; level <= max; level++) {
            WeaponState state = new WeaponState(level, Affinity.NONE, false);
            double t = (double) level / max;
            double expected = definition.baseDamage() * UpgradeMath.baseMultiplier(t);
            assertEquals(expected, definition.baseDamageAt(state), 1.0e-3,
                    "+" + level + " 基础伤害应等于 基础值 × B(t)");
        }
    }

    @Test void finalDamageIsBaseTimesBCurvePlusCorrectionTimesSCurve() {
        WeaponDefinition definition = straightSword();
        WeaponState state = new WeaponState(10, Affinity.NONE, false);
        AttributeSet attributes = new AttributeSet(40, 40, 10, 10, 10);
        double t = 1.0;
        double expected = definition.baseDamage() * UpgradeMath.baseMultiplier(t)
                + definition.correctionDamageAt(state, attributes);
        assertEquals(expected, definition.damageAt(state, attributes), 1.0e-6);
        // 补正部分必须真的随属性增长
        assertTrue(definition.damageAt(state, attributes) > definition.baseDamageAt(state));
    }

    // ---------------------------------------------------------------- 质变

    @Test void affinityPenaltyOnlyTouchesBaseDamage() {
        WeaponDefinition definition = straightSword();
        WeaponState plain = new WeaponState(5, Affinity.NONE, false);
        for (Affinity affinity : Affinity.values()) {
            if (affinity.isNone()) {
                continue;
            }
            double penalty = affinity.penalty();
            assertTrue(penalty >= 0.85 - EPS && penalty <= 0.95 + EPS,
                    affinity + " 的惩罚系数必须落在 0.85–0.95（否则构建分歧消失）");
            WeaponState mutated = plain.withAffinity(affinity);
            assertEquals(definition.baseDamageAt(plain) * penalty, definition.baseDamageAt(mutated), 1.0e-3,
                    affinity + "：基础伤害应等于原值 × P");
            assertEquals(plain.upgradeLevel(), mutated.upgradeLevel(), "质变保留强化等级");
        }
    }

    @Test void affinityRewritesCorrectionGrades() {
        WeaponDefinition definition = straightSword();
        WeaponState heavy = new WeaponState(0, Affinity.HEAVY, false);
        assertEquals(CorrectionGrade.A, definition.gradesFor(heavy).get(AttributeKind.STRENGTH));
        assertFalse(definition.gradesFor(heavy).containsKey(AttributeKind.DEXTERITY),
                "厚重只吃力量，不该还留灵巧补正");

        WeaponState quality = new WeaponState(0, Affinity.QUALITY, false);
        assertEquals(CorrectionGrade.B, definition.gradesFor(quality).get(AttributeKind.STRENGTH));
        assertEquals(CorrectionGrade.B, definition.gradesFor(quality).get(AttributeKind.DEXTERITY));

        WeaponState fire = new WeaponState(0, Affinity.FIRE, false);
        assertTrue(fire.affinity().damageType().equals("FIRE"));
        assertEquals(0.0, fire.affinity().baseGrades().size(), "火焰是唯一的无补正质变");
        assertEquals(0.95, fire.affinity().penalty(), EPS, "火焰惩罚最轻：定位是兜底选择");
    }

    @Test void affinityGradesStepUpWithUpgradeProgress() {
        // "A~S" 的写法：低强化 A，高强化 S
        assertEquals(CorrectionGrade.A, Affinity.HEAVY.gradesAt(0.0).get(AttributeKind.STRENGTH));
        assertEquals(CorrectionGrade.S, Affinity.HEAVY.gradesAt(1.0).get(AttributeKind.STRENGTH));
        assertEquals(CorrectionGrade.A, Affinity.HEAVY.gradesAt(0.5).get(AttributeKind.STRENGTH));
        assertEquals(CorrectionGrade.S, CorrectionGrade.A.upgrade());
        assertSame(CorrectionGrade.S, CorrectionGrade.S.upgrade(), "S 已是顶档");
    }

    @Test void heavierInvestmentPaysOffMoreAfterMutation() {
        // 同样的属性点，质变后补正应该更高（这就是"质变 = 用基础伤害换补正档位"）
        WeaponDefinition definition = straightSword();
        AttributeSet attributes = new AttributeSet(50, 50, 10, 10, 10);
        WeaponState plain = new WeaponState(10, Affinity.NONE, false);
        WeaponState heavy = new WeaponState(10, Affinity.HEAVY, false);
        assertTrue(definition.correctionDamageAt(heavy, attributes) > definition.correctionDamageAt(plain, attributes));
    }

    // ---------------------------------------------------------------- 状态机

    @Test void reforgeClearsAffinityAndKeepsLevel() {
        WeaponDefinition definition = straightSword();
        WeaponState state = new WeaponState(7, Affinity.MAGIC, false);
        WeaponState reforged = state.reforged(definition);
        assertEquals(7, reforged.upgradeLevel(), "重铸保留等级");
        assertEquals(Affinity.NONE, reforged.affinity(), "重铸清空质变");
    }

    @Test void upgradeStopsAtMaxLevel() {
        WeaponDefinition normal = straightSword();
        WeaponState maxed = new WeaponState(10, Affinity.NONE, false);
        assertTrue(maxed.isMaxed(normal));
        assertFalse(maxed.canUpgrade(normal));
        assertEquals(10, maxed.upgraded(normal).upgradeLevel(), "满级再加不越界");

        WeaponDefinition remembrance = WeaponDefinition.of(WeaponProfiles.of("marikas_hammer"), true);
        assertEquals(5, remembrance.maxLevel(), "追忆武器 +0~+5");
        WeaponState five = new WeaponState(5, Affinity.NONE, false).sanitized(remembrance);
        assertEquals(5, five.upgradeLevel());
        assertEquals(5, five.upgraded(remembrance).upgradeLevel());
    }

    @Test void remembranceWeaponsLockTheArtAndCannotBeReforged() {
        WeaponDefinition remembrance = WeaponDefinition.of(WeaponProfiles.of("sacred_relic_sword"), true);
        assertTrue(remembrance.lockedArt());
        assertFalse(remembrance.artReplaceable());
        WeaponState state = remembrance.fresh();
        assertTrue(state.artLocked(), "追忆武器的战灰天生锁定");
        WeaponState after = new WeaponState(3, Affinity.HEAVY, true).reforged(remembrance);
        assertEquals(Affinity.HEAVY, after.affinity(), "战灰锁定时重铸不生效（原样返回）");
        // 越界等级会被夹住
        assertEquals(5, new WeaponState(12, Affinity.NONE, false).sanitized(remembrance).upgradeLevel());
    }

    @Test void normalWeaponsAreNotArtLocked() {
        WeaponDefinition normal = straightSword();
        assertTrue(normal.artReplaceable());
        assertFalse(normal.fresh().artLocked());
        assertEquals(Affinity.NONE, new WeaponState(4, Affinity.KEEN, false).reforged(normal).affinity());
    }

    // ---------------------------------------------------------------- 补正契约

    @Test void softnessIsPiecewiseWithHardDiminishingReturns() {
        assertEquals(0.0, AttributeKind.softness(0), EPS);
        assertEquals(20.0, AttributeKind.softness(20), EPS);
        assertEquals(32.0, AttributeKind.softness(40), EPS);
        assertEquals(37.0, AttributeKind.softness(60), EPS, "第 2 段结束 = 32 + 20×0.25");
        assertEquals(40.9, AttributeKind.softness(99), EPS, "第 3 段：37 + 39×0.10");
        // 单调递增
        double previous = -1;
        for (int i = 0; i <= 99; i++) {
            double value = AttributeKind.softness(i);
            assertTrue(value >= previous, "软化函数必须单调不减");
            previous = value;
        }
        // 关键性质：把一项堆到 99 的收益远小于线性
        assertTrue(AttributeKind.softness(99) < 99 * 0.5, "三段衰减必须显著削掉单属性堆叠收益");
    }

    @Test void attributesClampToTheContractRange() {
        AttributeSet set = new AttributeSet(-5, 200, 10, 10, 10);
        assertEquals(0, set.strength());
        assertEquals(AttributeKind.MAX_POINTS, set.dexterity());
        assertEquals(0, set.with(AttributeKind.FAITH, -3).faith());
        assertEquals(AttributeKind.MAX_POINTS, set.with(AttributeKind.ARCANE, 500).arcane());
        assertEquals(0, AttributeSet.EMPTY.total());
        assertNotNull(AttributeKind.byKey("dexterity"));
        assertEquals(AttributeKind.FAITH, AttributeKind.byKey(" FAITH "));
    }

    @Test void hitDamageGrowsWithAttributesButWithDiminishingReturns() {
        WeaponDefinition definition = straightSword();
        WeaponState state = new WeaponState(10, Affinity.QUALITY, false);
        double at0 = definition.damageAt(state, AttributeSet.EMPTY);
        double at20 = definition.damageAt(state, new AttributeSet(20, 20, 0, 0, 0));
        double at40 = definition.damageAt(state, new AttributeSet(40, 40, 0, 0, 0));
        double at80 = definition.damageAt(state, new AttributeSet(80, 80, 0, 0, 0));
        assertTrue(at0 < at20 && at20 < at40 && at40 < at80);
        // 20→40 的增益应大于 40→80（收益递减）
        assertTrue((at40 - at20) > (at80 - at40));
    }

    // ---------------------------------------------------------------- 数据表

    @Test void everyWeaponHasADefinitionAndRemembranceFlagsMatch() {
        var definitions = WeaponDefinition.defaultsById();
        assertEquals(WeaponProfiles.defaultsById().size(), definitions.size());
        for (String id : WeaponDefinition.REMEMBRANCE_IDS) {
            WeaponDefinition definition = definitions.get(id);
            assertNotNull(definition, "缺少追忆武器定义: " + id);
            assertTrue(definition.remembrance(), id + " 必须标记为追忆武器");
            assertEquals(5, definition.maxLevel());
            assertTrue(definition.lockedArt(), id + " 的战灰必须锁定");
        }
        for (var entry : definitions.entrySet()) {
            if (!WeaponDefinition.REMEMBRANCE_IDS.contains(entry.getKey())) {
                assertFalse(entry.getValue().remembrance(), entry.getKey() + " 不该被当成追忆武器");
                assertEquals(10, entry.getValue().maxLevel());
            }
            assertTrue(entry.getValue().baseDamage() > 0);
            assertTrue(entry.getValue().correctionRating() > 0);
        }
    }

    @Test void defaultGradesFollowTheRecommendedAffinity() {
        WeaponDefinition heavy = WeaponDefinition.of(WeaponProfiles.of("greataxe"), false);
        assertEquals(CorrectionGrade.B, heavy.baseGrades().get(AttributeKind.STRENGTH));
        WeaponDefinition keen = WeaponDefinition.of(WeaponProfiles.of("katana"), false);
        assertEquals(CorrectionGrade.B, keen.baseGrades().get(AttributeKind.DEXTERITY));
        WeaponDefinition magic = WeaponDefinition.of(WeaponProfiles.of("glintstone_staff"), false);
        assertEquals(CorrectionGrade.B, magic.baseGrades().get(AttributeKind.INTELLIGENCE));
        // 远程武器的推荐质变是 NONE，落到默认力量 D（占位，不夸张）
        WeaponProfile bow = WeaponProfiles.of("long_bow");
        assertEquals("NONE", bow.recommendedAffinity());
    }
}
