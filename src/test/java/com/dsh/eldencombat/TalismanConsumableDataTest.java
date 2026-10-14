package com.dsh.eldencombat;

import com.dsh.eldencombat.core.ConsumableSpec;
import com.dsh.eldencombat.core.TalismanSpec;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 护符与药物参数的解析与倍率换算（纯逻辑）。 */
class TalismanConsumableDataTest {

    @Test void talismanParsesNumbersAndClampsMultipliers() {
        TalismanSpec spec = TalismanSpec.parse("warrior_jar_shard",
                JsonParser.parseString("{\"name\":\"战士壶碎片\",\"staminaRegen\":0.20,\"damage\":0.08}")
                        .getAsJsonObject());
        assertEquals("战士壶碎片", spec.displayName());
        assertEquals(1.20f, spec.staminaRegenMultiplier(), 1.0e-6);
        assertEquals(1.08f, spec.damageMultiplier(), 1.0e-6);
        assertEquals(1.0f, spec.damageTakenMultiplier(), 1.0e-6, "没写减伤就是 1 倍");
    }

    @Test void talismanAllowsCostlyTradeoffButNotAbsurdValues() {
        // 烙印：有代价的护符（受伤 +10%）必须真的更疼
        TalismanSpec soreseal = TalismanSpec.parse("radagon_soreseal",
                JsonParser.parseString("{\"maxFp\":30,\"damageReduction\":-0.10}").getAsJsonObject());
        assertEquals(30f, soreseal.maxFp(), 1.0e-6);
        assertEquals(1.10f, soreseal.damageTakenMultiplier(), 1.0e-6);
        // 极端配置被夹住，不允许 0 伤害/负伤害
        TalismanSpec absurd = TalismanSpec.parse("x",
                JsonParser.parseString("{\"damageReduction\":5,\"damage\":-9,\"staminaRegen\":-9}").getAsJsonObject());
        assertEquals(0.1f, absurd.damageTakenMultiplier(), 1.0e-6);
        assertEquals(0.1f, absurd.damageMultiplier(), 1.0e-6);
        assertEquals(0.1f, absurd.staminaRegenMultiplier(), 1.0e-6);
    }

    @Test void talismanMissingFieldsAreZeroAndNoneIsEmpty() {
        TalismanSpec empty = TalismanSpec.parse("empty",
                JsonParser.parseString("{}").getAsJsonObject());
        assertEquals(0f, empty.maxFp(), 1.0e-6);
        assertEquals(1f, empty.damageMultiplier(), 1.0e-6);
        assertTrue(TalismanSpec.NONE.isNone());
        assertFalse(empty.isNone(), "有 id 但不是 NONE：装备与查询要能区分");
    }

    @Test void consumableParsesEffectsWithSecondsAndSanitizes() {
        ConsumableSpec spec = ConsumableSpec.parse("flame_grant_me_strength",
                JsonParser.parseString("{\"name\":\"火焰赐予我力量\",\"effects\":["
                        + "{\"effect\":\"minecraft:strength\",\"amplifier\":1,\"seconds\":60},"
                        + "{\"effect\":\"minecraft:regeneration\",\"amplifier\":-3,\"seconds\":0}]}")
                        .getAsJsonObject());
        assertEquals(2, spec.effects().size());
        assertEquals(1200, spec.effects().get(0).durationTicks());
        assertEquals(1, spec.effects().get(0).amplifier());
        assertEquals(0, spec.effects().get(1).amplifier(), "负数等级被夹到 0");
        assertEquals(20, spec.effects().get(1).durationTicks(), "0 秒被夹到 1 秒");
    }

    @Test void consumableIgnoresMalformedEffectsAndKeepsHeal() {
        ConsumableSpec spec = ConsumableSpec.parse("starlight_shard",
                JsonParser.parseString("{\"heal\":2.0,\"effects\":[{\"amplifier\":1},"
                        + "\"not-an-object\",{\"effect\":\"minecraft:absorption\"}]}").getAsJsonObject());
        assertEquals(2.0f, spec.heal(), 1.0e-6);
        assertEquals(1, spec.effects().size(), "缺 effect 字段的条目必须被丢弃");
        assertEquals(600, spec.effects().get(0).durationTicks(), "没写时长时默认 30 秒");
        assertTrue(ConsumableSpec.NONE.isNone());
    }
}
