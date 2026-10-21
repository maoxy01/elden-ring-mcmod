package com.dsh.eldencombat;

import com.dsh.eldencombat.core.TalismanAggregate;
import com.dsh.eldencombat.core.TalismanRules;
import com.dsh.eldencombat.core.TalismanSpec;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §3.3 护符聚合：单槽结果必须与旧的逐件换算**逐位相同**（升级不改平衡），
 * 多槽先求和再夹紧（不能靠多件绕过上限）。
 */
class TalismanAggregateTest {

    private static TalismanSpec parse(String id, String json) {
        return TalismanSpec.parse(id, JsonParser.parseString(json).getAsJsonObject());
    }

    /** 旧版数据包样本，验证自定义数据包的兼容语义；不是当前内置数值。 */
    private static final List<TalismanSpec> SHIPPED = List.of(
            parse("eldencombat:warrior_jar_shard", "{\"name\":\"战士壶碎片\",\"staminaRegen\":0.20,\"damage\":0.08}"),
            parse("eldencombat:radagon_soreseal", "{\"name\":\"拉达冈的烙印\",\"maxFp\":30,\"staminaRegen\":0.10,\"damageReduction\":-0.10}"),
            parse("eldencombat:green_turtle_talisman", "{\"name\":\"绿龟护符\",\"staminaRegen\":0.30}"),
            parse("eldencombat:dragoncrest_shield_talisman", "{\"name\":\"龙印护盾护符\",\"damageReduction\":0.18,\"heal\":1.0}"));

    @Test void singleSlotMatchesTheLegacyPerSpecMathForEveryShippedTalisman() {
        TalismanRules rules = new TalismanRules();
        for (TalismanSpec spec : SHIPPED) {
            TalismanAggregate one = TalismanAggregate.of(List.of(spec), rules);
            assertEquals(1, one.count(), spec.id());
            assertEquals(spec.maxFp(), one.maxFpBonus(), 1.0e-6, spec.id() + " FP 上限");
            assertEquals(spec.staminaRegenMultiplier(), one.staminaRegenMultiplier(), 1.0e-6, spec.id() + " 精力");
            assertEquals(spec.damageMultiplier(), one.damageMultiplier(), 1.0e-6, spec.id() + " 伤害");
            assertEquals(spec.damageTakenMultiplier(), one.damageTakenMultiplier(), 1.0e-6, spec.id() + " 受伤");
            assertEquals(spec.heal(), one.healBonus(), 1.0e-6, spec.id() + " 治疗");
        }
    }

    @Test void twoSlotsSumThenClampOnceInsteadOfClampingEachItem() {
        TalismanRules rules = new TalismanRules();
        rules.slots = 2;
        rules.maxDamageBonus = 0.25f;
        TalismanSpec first = parse("eldencombat:a", "{\"damage\":0.20}");
        TalismanSpec second = parse("eldencombat:b", "{\"damage\":0.20}");
        TalismanAggregate both = TalismanAggregate.of(List.of(first, second), rules);
        assertEquals(2, both.count());
        // 逐件夹紧会得到 0.20 + 0.20 = 0.40（绕过上限）；正确行为是先求和 0.40 再夹到 0.25
        assertEquals(1.25f, both.damageMultiplier(), 1.0e-6);
    }

    @Test void costlyTradeoffStillWorksAndIsSummed() {
        TalismanRules rules = new TalismanRules();
        rules.slots = 3;
        TalismanAggregate none = TalismanAggregate.of(List.of(), rules);
        assertTrue(none.isNone());
        assertEquals(1f, none.damageTakenMultiplier(), 1.0e-6);

        TalismanSpec soreseal = parse("eldencombat:radagon_soreseal", "{\"damageReduction\":-0.10}");
        assertEquals(1.10f, TalismanAggregate.of(List.of(soreseal), rules).damageTakenMultiplier(), 1.0e-6);

        // 一正一负：先求和（0.18 + -0.10 = 0.08）再换算，不是各算各的
        TalismanSpec shield = parse("eldencombat:dragoncrest_shield_talisman", "{\"damageReduction\":0.18}");
        assertEquals(0.92f,
                TalismanAggregate.of(List.of(soreseal, shield), rules).damageTakenMultiplier(), 1.0e-6);
    }

    @Test void reductionCapStopsStackingToInvulnerability() {
        TalismanRules rules = new TalismanRules();
        rules.slots = 4;
        rules.maxDamageReduction = 0.60f;
        List<TalismanSpec> four = List.of(
                parse("a:b", "{\"damageReduction\":0.20}"),
                parse("a:c", "{\"damageReduction\":0.20}"),
                parse("a:d", "{\"damageReduction\":0.20}"),
                parse("a:e", "{\"damageReduction\":0.20}"));
        // 0.80 的减伤被夹到 0.60：受伤倍率 0.40，而不是 0.20
        assertEquals(0.40f, TalismanAggregate.of(four, rules).damageTakenMultiplier(), 1.0e-6);
    }

    @Test void noneSpecsAreSkippedAndInvalidNumbersFallBackToTheLowerBound() {
        TalismanRules rules = new TalismanRules();
        TalismanAggregate onlyNone = TalismanAggregate.of(List.of(TalismanSpec.NONE, TalismanSpec.NONE), rules);
        assertTrue(onlyNone.isNone());
        assertEquals(1f, onlyNone.damageMultiplier(), 1.0e-6);

        TalismanSpec absurd = parse("eldencombat:x", "{\"damageReduction\":5,\"damage\":-9,\"staminaRegen\":-9}");
        TalismanAggregate clamped = TalismanAggregate.of(List.of(absurd), rules);
        assertEquals(0.4f, clamped.damageTakenMultiplier(), 1.0e-6, "减伤 5 被夹到上限 0.6");
        assertEquals(0.1f, clamped.damageMultiplier(), 1.0e-6);
        assertEquals(0.1f, clamped.staminaRegenMultiplier(), 1.0e-6);
    }
}
