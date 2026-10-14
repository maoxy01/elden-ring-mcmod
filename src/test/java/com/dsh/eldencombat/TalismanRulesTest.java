package com.dsh.eldencombat;

import com.dsh.eldencombat.core.TalismanRules;
import com.dsh.eldencombat.core.TalismanSpec;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §3.3 集中规则：默认值必须等于既有单槽行为；槽数/重复/上下限可配置且非法值被夹住。
 */
class TalismanRulesTest {

    private static TalismanSpec spec(String id, String series) {
        return TalismanSpec.parse(id,
                JsonParser.parseString("{\"name\":\"" + id + "\",\"series\":\"" + series + "\"}").getAsJsonObject());
    }

    @Test void defaultsKeepTheExistingSingleSlotBehaviourAndRealValuesFitUnderEveryCap() {
        TalismanRules rules = new TalismanRules();
        assertEquals(1, rules.activeSlots(), "默认单槽 = 既有行为");
        assertFalse(rules.allowDuplicates);
        assertFalse(rules.allowSameSeries);
        // 现有四件护符的实际数值都远在上限之内，所以加规则不改平衡
        assertTrue(rules.maxDamageBonus >= 0.08f);
        assertTrue(rules.maxDamageReduction >= 0.18f);
        assertTrue(rules.maxStaminaBonus >= 0.30f);
        assertTrue(rules.maxFpBonus >= 30f);
        assertTrue(rules.maxHealBonus >= 1.0f);
    }

    @Test void duplicateAndSameSeriesLimitsAreConfigurable() {
        TalismanRules strict = new TalismanRules();
        TalismanSpec jar = spec("eldencombat:warrior_jar_shard", "jar");
        assertTrue(strict.accepts(List.of(), List.of(), jar), "空栏谁都能装");
        assertFalse(strict.accepts(List.of("eldencombat:warrior_jar_shard"), List.of("jar"), jar),
                "同款默认不允许重复");

        TalismanRules permissive = new TalismanRules();
        permissive.allowDuplicates = true;
        assertTrue(permissive.accepts(List.of("eldencombat:warrior_jar_shard"), List.of("jar"), jar),
                "允许重复时同款能装两遍（同系列规则只管不同款，不顺手否掉它）");

        TalismanRules seriesStrict = new TalismanRules();
        TalismanSpec otherJar = spec("othermod:jar_fragment", "jar");
        assertFalse(seriesStrict.accepts(List.of("eldencombat:warrior_jar_shard"), List.of("jar"), otherJar),
                "同系列默认不允许");
        seriesStrict.allowSameSeries = true;
        assertTrue(seriesStrict.accepts(List.of("eldencombat:warrior_jar_shard"), List.of("jar"), otherJar));

        // 没写 series 的护符不受"同系列"限制（"无系列"不能互相算成同一个系列）
        TalismanSpec noSeries = spec("othermod:plain", "");
        TalismanRules seriesOnly = new TalismanRules();
        assertTrue(seriesOnly.accepts(List.of("eldencombat:x", "othermod:y"), List.of("", ""), noSeries));
    }

    @Test void sanitizeClampsAbsurdConfigurationInsteadOfTrustingTheFile() {
        TalismanRules rules = new TalismanRules();
        rules.slots = 99;
        rules.maxDamageBonus = -5f;
        rules.maxDamageReduction = 5f;
        rules.maxStaminaBonus = Float.NaN;
        rules.maxFpBonus = -1f;
        rules.maxHealBonus = Float.POSITIVE_INFINITY;
        rules.sanitize();
        assertEquals(TalismanRules.MAX_SLOTS, rules.activeSlots());
        assertEquals(0f, rules.maxDamageBonus, 1.0e-6);
        assertTrue(rules.maxDamageReduction <= 0.95f);
        assertEquals(0f, rules.maxStaminaBonus, 1.0e-6, "NaN 落回下限而不是把倍率变 NaN");
        assertEquals(0f, rules.maxFpBonus, 1.0e-6);
        assertEquals(0f, rules.maxHealBonus, 1.0e-6, "非有限值落回下限，不会把倍率变 NaN/Inf");

        rules.slots = 0;
        assertEquals(1, rules.activeSlots(), "槽数 0 至少给 1 格");
        rules.slots = -3;
        assertEquals(1, rules.activeSlots());
    }

    @Test void configFileRoundTripsAndMissingFileFallsBackToDefaults(@TempDir Path dir) {
        Path file = dir.resolve("eldencombat-talisman.json");
        TalismanRules missing = TalismanRules.loadOrCreate(file);
        assertEquals(1, missing.activeSlots());
        assertTrue(Files.isRegularFile(file), "首次运行会把默认值写出来，便于玩家改");

        TalismanRules custom = new TalismanRules();
        custom.slots = 3;
        custom.allowSameSeries = true;
        custom.maxFpBonus = 45f;
        custom.save(file);

        TalismanRules reloaded = TalismanRules.loadOrCreate(file);
        assertEquals(3, reloaded.activeSlots());
        assertTrue(reloaded.allowSameSeries);
        assertEquals(45f, reloaded.maxFpBonus, 1.0e-6);

        TalismanRules installed = new TalismanRules();
        TalismanRules.install(reloaded);
        assertSame(reloaded, TalismanRules.current());
        TalismanRules.install(installed);
        assertEquals(1, TalismanRules.current().activeSlots());
    }

    @Test void brokenConfigFileDoesNotBreakStartup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("eldencombat-talisman.json");
        Files.writeString(file, "{ not json at all", StandardCharsets.UTF_8);
        TalismanRules rules = TalismanRules.loadOrCreate(file);
        assertEquals(1, rules.activeSlots(), "坏文件退回内置默认值，而不是抛异常拖死开服");
    }
}
