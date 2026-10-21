package com.dsh.eldencombat;

import com.dsh.eldencombat.core.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TalismanMechanicsTest {
    private static TalismanSpec shipped(String id) throws Exception {
        return TalismanSpec.parse("eldencombat:" + id, JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/data/eldencombat/talismans", id + ".json"))).getAsJsonObject());
    }

    @Test void jarShardBoostsOnlySkillsAndHasNoInventedRegen() throws Exception {
        var jar = shipped("warrior_jar_shard");
        assertEquals(1, jar.damageMultiplier());
        assertEquals(.10f, jar.effects().skillDamage(), 1e-6);
        assertEquals(1, jar.staminaRegenMultiplier());
        assertEquals(0, jar.effects().staminaRegenPerSecond());
    }

    @Test void shieldDistinguishesPhysicalMagicAndPlayerDamage() throws Exception {
        var e = shipped("dragoncrest_shield_talisman").effects();
        assertEquals(.90f, e.incomingMultiplier(true, true, false), 1e-6);
        assertEquals(.98f, e.incomingMultiplier(true, true, true), 1e-6);
        assertEquals(1, e.incomingMultiplier(true, false, false));
        assertEquals(1, e.incomingMultiplier(false, false, false));
        assertEquals(0, shipped("dragoncrest_shield_talisman").heal());
    }

    @Test void sealTradeoffMultipliesShieldReductionRatherThanSubtractingPercentages() throws Exception {
        var e = TalismanEffects.of(List.of(shipped("radagon_soreseal"), shipped("dragoncrest_shield_talisman")));
        assertEquals(1.15f * .90f, e.incomingMultiplier(true, true, false), 1e-6);
        assertEquals(1.15f * .98f, e.incomingMultiplier(true, true, true), 1e-6);
        assertEquals(1.15f, e.incomingMultiplier(true, false, false), 1e-6);
        assertEquals(1, e.incomingMultiplier(false, false, false));
    }

    @Test void sealUsesDerivedStrengthDexterityAndMappedResourcesWithoutChangingAllocatedPoints() throws Exception {
        var seal = shipped("radagon_soreseal");
        var base = new AttributeSet(7, 3, 4, 5, 6);
        assertEquals(new AttributeSet(12, 8, 4, 5, 6), seal.effects().attributes(base));
        assertEquals(7, base.strength());
        assertEquals(0, seal.maxFp());
        assertEquals(0, seal.staminaRegen());
        assertEquals(5, seal.effects().maxHealth());
        assertEquals(10, seal.effects().maxStamina());
    }

    @Test void turtleAddsFixedConvertedRecoveryAfterTheExistingDelayAndGuardPenalty() throws Exception {
        var config = CombatConfig.defaults();
        var state = new CombatState();
        state.ensureInit(config);
        state.stamina = 10;
        state.lastStaminaUseTick = 100;
        float flat = shipped("green_turtle_talisman").effects().staminaRegenPerSecond();
        state.tickStamina(config, 111, false, 1, flat);
        assertEquals(10, state.stamina);
        state.tickStamina(config, 112, false, 1, flat);
        assertEquals(10 + 53f / 45f, state.stamina, 1e-5);
        config.stamina.regenPerTick = 2;
        state.stamina = 10;
        state.tickStamina(config, 113, true, 1, flat);
        assertEquals(10 + (2 + 8f / 45f) * config.stamina.guardRegenMultiplier, state.stamina, 1e-5);
    }

    @Test void repeatedCapacityChangesDoNotHealAndRestUsesTheEffectiveCap() {
        var config = CombatConfig.defaults();
        var state = new CombatState();
        state.ensureInit(config);
        state.stamina = 37;
        state.potionCharges = 1;
        state.setBonusMaxStamina(10, config);
        state.setBonusMaxStamina(10, config);
        assertEquals(37, state.stamina);
        assertEquals(110, state.maxStamina(config));
        state.refillStamina(config);
        assertEquals(110, state.stamina);
        assertEquals(1, state.potionCharges);
        state.setBonusMaxStamina(0, config);
        assertEquals(100, state.stamina);
        state.setBonusMaxStamina(10, config);
        assertEquals(100, state.stamina);
    }
}
