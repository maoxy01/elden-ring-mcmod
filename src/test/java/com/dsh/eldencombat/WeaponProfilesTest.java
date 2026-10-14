package com.dsh.eldencombat;

import com.dsh.eldencombat.core.ActionId;
import com.dsh.eldencombat.core.ActionSpec;
import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.core.HitWindow;
import com.dsh.eldencombat.core.WeaponClass;
import com.dsh.eldencombat.core.WeaponProfile;
import com.dsh.eldencombat.core.WeaponProfiles;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5 武器差异化数值表：表值、公式、以及"落到动作规格上"的效果。
 *
 * <p>这张表是 33 件类型武器"不只是美术差异"的依据，所以每条断言都对着方案里的表。
 */
class WeaponProfilesTest {

    @AfterEach
    void restoreTable() {
        WeaponProfiles.overrideForTesting(null);
    }

    @Test void tableCoversEveryWeaponInThePlan() {
        // 33 件类型武器 + 8 件追忆武器
        assertEquals(41, WeaponProfiles.size());
        for (String id : List.of("dagger", "straight_sword", "great_sword", "colossal_sword", "spear",
                "whip", "fist", "claw", "short_bow", "great_shield",
                "sacred_relic_sword", "marikas_hammer", "malikeths_black_blade", "blasphemous_blade",
                "hand_of_malenia", "starscourge_greatsword", "mohgwyns_sacred_spear", "morgotts_cursed_sword")) {
            assertNotNull(WeaponProfiles.of(id), "缺少武器数值档: " + id);
        }
        assertNull(WeaponProfiles.of("minecraft:diamond_sword"), "原版物品不该有档位");
        assertNull(WeaponProfiles.of(null));
    }

    @Test void reachTableMatchesThePlan() {
        assertEquals(2.4, WeaponProfiles.of("fist").reach(), 1.0e-9);
        assertEquals(2.4, WeaponProfiles.of("claw").reach(), 1.0e-9);
        assertEquals(2.7, WeaponProfiles.of("dagger").reach(), 1.0e-9);
        assertEquals(3.0, WeaponProfiles.of("straight_sword").reach(), 1.0e-9);
        assertEquals(3.3, WeaponProfiles.of("great_sword").reach(), 1.0e-9);
        assertEquals(3.6, WeaponProfiles.of("colossal_sword").reach(), 1.0e-9);
        // 矛比大矛更长：它的生存理由就是"在安全距离戳"
        assertEquals(3.9, WeaponProfiles.of("spear").reach(), 1.0e-9);
        assertEquals(3.6, WeaponProfiles.of("great_spear").reach(), 1.0e-9);
        assertEquals(3.9, WeaponProfiles.of("whip").reach(), 1.0e-9);
    }

    @Test void poiseTableMatchesThePlanAndKeepsLightBelowHeavy() {
        assertEquals(8f, WeaponProfiles.of("fist").poiseLight(), 1.0e-6);
        assertEquals(14f, WeaponProfiles.of("fist").poiseHeavy(), 1.0e-6);
        assertEquals(12f, WeaponProfiles.of("straight_sword").poiseLight(), 1.0e-6);
        assertEquals(20f, WeaponProfiles.of("straight_sword").poiseHeavy(), 1.0e-6);
        assertEquals(18f, WeaponProfiles.of("great_sword").poiseLight(), 1.0e-6);
        assertEquals(30f, WeaponProfiles.of("great_sword").poiseHeavy(), 1.0e-6);
        assertEquals(26f, WeaponProfiles.of("whip").poiseLight(), 1.0e-6, "软鞭削韧是刻意的例外");
        assertEquals(58f, WeaponProfiles.of("colossal_weapon").poiseHeavy(), 1.0e-6);
        for (WeaponProfile profile : WeaponProfiles.all().values()) {
            if (profile.isMeleeProfile()) {
                assertTrue(profile.poiseHeavy() >= profile.poiseLight(), profile.id() + " 重击削韧不该低于轻击");
            }
        }
        // 远程与盾不参与削韧表
        assertFalse(WeaponProfiles.of("long_bow").isMeleeProfile());
        assertFalse(WeaponProfiles.of("great_shield").isMeleeProfile());
    }

    @Test void weightDrivesStaminaAndAttackSpeedWithinThePlannedClamps() {
        // 基准重量 5：两者都不修正
        WeaponProfile baseline = new WeaponProfile("x", 3.0, 10, 20, 5.0, "NONE");
        assertEquals(1.0, baseline.attackSpeedMultiplier(), 1.0e-9);
        assertEquals(1.0, baseline.staminaCostMultiplier(), 1.0e-9);

        // 匕首（1.5）：更快、更省
        WeaponProfile dagger = WeaponProfiles.of("dagger");
        assertEquals(1.07, dagger.attackSpeedMultiplier(), 1.0e-9);
        assertEquals(0.85, dagger.staminaCostMultiplier(), 1.0e-9, "轻武器的耐力下限是 0.85");

        // 特大武器（16）：更慢、更贵，且都被夹在钳位里
        WeaponProfile colossal = WeaponProfiles.of("colossal_weapon");
        assertEquals(0.78, colossal.attackSpeedMultiplier(), 1.0e-9);
        assertEquals(1.40, colossal.staminaCostMultiplier(), 1.0e-9, "重武器的耐力上限是 1.40");

        WeaponProfile extreme = new WeaponProfile("y", 3.0, 10, 20, 200.0, "NONE");
        assertEquals(0.70, extreme.attackSpeedMultiplier(), 1.0e-9);
        assertEquals(1.40, extreme.staminaCostMultiplier(), 1.0e-9);
    }

    @Test void applyToReplacesPoiseScalesStaminaAndKeepsDamage() {
        CombatConfig config = CombatConfig.defaults();
        WeaponProfile spear = WeaponProfiles.of("spear");
        WeaponProfile dagger = WeaponProfiles.of("dagger");

        ActionSpec light = config.lightSpec(WeaponClass.LIGHT_SWORD);
        ActionSpec spawned = spear.applyTo(light);
        HitWindow window = spawned.windows().getFirst();
        assertEquals(spear.poiseLight(), window.poiseDamage(), 1.0e-6, "削韧换成矛的值");
        assertEquals(light.windows().getFirst().damage(), window.damage(), 1.0e-6, "伤害不动");
        assertEquals(light.totalTicks(), spawned.totalTicks(), "时序不动");
        assertEquals(light.staminaCost() * spear.staminaCostMultiplier(), spawned.staminaCost(), 1.0e-3);

        // 重击走另一档
        ActionSpec heavy = config.heavySpec(WeaponClass.LIGHT_SWORD);
        assertEquals(spear.poiseHeavy(), spear.applyTo(heavy).windows().getFirst().poiseDamage(), 1.0e-6);
        // 蓄力重击也算重
        ActionSpec charged = config.chargedHeavySpec(WeaponClass.LIGHT_SWORD);
        assertEquals(spear.poiseHeavy(), spear.applyTo(charged).windows().getFirst().poiseDamage(), 1.0e-6);
        assertTrue(WeaponProfile.isHeavyAction(ActionId.CHARGED_HEAVY));

        // 匕首更省耐力
        assertTrue(dagger.applyTo(light).staminaCost() < light.staminaCost());
        // 无档位 / 空动作原样返回
        assertNull(new WeaponProfile("z", 3.0, 0, 0, 5.0, "NONE").applyTo(null));
        ActionSpec untouched = new WeaponProfile("z", 3.0, 0, 0, 5.0, "NONE").applyTo(light);
        assertEquals(light.staminaCost(), untouched.staminaCost(), 1.0e-6, "非近战档位不改动作");
    }

    @Test void dataPackOverrideOnlyTouchesKnownIds() {
        Map<String, WeaponProfile> base = WeaponProfiles.defaultsById();
        String json = """
                [{"id":"spear","reach":4.5,"poiseLight":30,"poiseHeavy":50,"weight":9.0,"recommendedAffinity":"keen"},
                 {"id":"not_a_weapon","reach":9.0,"poiseLight":1,"poiseHeavy":1,"weight":1.0,"recommendedAffinity":"none"}]""";
        Map<String, WeaponProfile> merged = WeaponProfiles.parse(new StringReader(json), base);
        assertEquals(4.5, merged.get("spear").reach(), 1.0e-9);
        assertEquals(30f, merged.get("spear").poiseLight(), 1.0e-6);
        assertEquals("KEEN", merged.get("spear").recommendedAffinity(), "质变名统一成大写");
        assertFalse(merged.containsKey("not_a_weapon"), "未知 id 不该被加进表里");
        assertEquals(base.size(), merged.size());
    }

    @Test void overriddenTableIsWhatLookupsSee() {
        Map<String, WeaponProfile> custom = new LinkedHashMap<>();
        custom.put("spear", new WeaponProfile("spear", 5.0, 40, 60, 12.0, "quality"));
        WeaponProfiles.overrideForTesting(custom);
        assertEquals(5.0, WeaponProfiles.of("spear").reach(), 1.0e-9);
        assertNull(WeaponProfiles.of("dagger"), "覆盖后的表就是全部（测试用）");
        WeaponProfiles.overrideForTesting(null);
        assertNotNull(WeaponProfiles.of("dagger"));
    }

    @Test void reachBonusIsRelativeToVanillaBase() {
        assertEquals(0.0, WeaponProfiles.of("straight_sword").reachBonus(), 1.0e-9);
        assertEquals(0.9, WeaponProfiles.of("spear").reachBonus(), 1.0e-9);
        assertEquals(-0.6, WeaponProfiles.of("fist").reachBonus(), 1.0e-9);
        // 越界的原版值会被夹住，不会出现负距离
        WeaponProfile silly = new WeaponProfile("silly", -5.0, 10, 10, 5.0, "NONE");
        assertTrue(silly.reach() >= WeaponProfile.MIN_REACH);
        assertTrue(new WeaponProfile("silly", 99.0, 10, 10, 5.0, "NONE").reach() <= WeaponProfile.MAX_REACH);
    }

    @Test void planJsonShapeParsesAsExpected() {
        // 顺带确认 Gson 能按字段名反序列化（数据包覆盖走的就是这条路径）
        var parsed = new com.google.gson.Gson().fromJson(JsonParser.parseString(
                "{\"id\":\"dagger\",\"reach\":2.7,\"poiseLight\":8,\"poiseHeavy\":14,\"weight\":1.5,"
                        + "\"recommendedAffinity\":\"keen\"}"), WeaponProfile.class);
        assertEquals("dagger", parsed.id());
        assertEquals(14f, parsed.poiseHeavy(), 1.0e-6);
        assertEquals("KEEN", parsed.recommendedAffinity());
    }
}
