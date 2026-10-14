package com.dsh.eldencombat;

import com.dsh.eldencombat.core.AshRewardTable;
import com.dsh.eldencombat.core.AshUnlockSet;
import com.dsh.eldencombat.server.skills.OrdinaryAshCatalog;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 战灰玩法内来源表的不变量（不加载 Minecraft）。 */
class AshRewardTableTest {

    @Test void everySourceAndRewardIsAFullNamespacedId() {
        assertFalse(AshRewardTable.entries().isEmpty(), "至少要有一个玩法内来源，否则战灰根本拿不到");
        for (AshRewardTable.Entry entry : AshRewardTable.entries()) {
            assertNotNull(entry.source(), "来源必须是完整 namespaced 实体 id：" + entry.source());
            assertEquals(entry.source(), AshUnlockSet.normalize(entry.source()));
            assertFalse(entry.ashes().isEmpty(), entry.source() + " 的解锁列表不能为空");
            for (String ash : entry.ashes()) {
                assertEquals(ash, AshUnlockSet.normalize(ash), "奖励必须是完整 namespaced 战灰 id：" + ash);
            }
        }
    }

    /** 硬不变量：奖励的战灰**必须真的在目录里**，否则玩家拿到了也装不上（等于假奖励）。 */
    @Test void everyRewardedAshExistsInTheSupportedCatalog() {
        Set<String> catalog = OrdinaryAshCatalog.entries().stream()
                .map(OrdinaryAshCatalog.Entry::id).collect(Collectors.toSet());
        for (String ash : AshRewardTable.allRewardedAshes()) {
            assertTrue(catalog.contains(ash), "奖励了目录里没有的战灰：" + ash);
        }
    }

    @Test void lookupNormalizesAndToleratesUnknownSources() {
        assertEquals(List.of("eldencombat:stamp_upward_cut"), AshRewardTable.ashesFor("eldencombat:margit"));
        assertEquals(List.of("eldencombat:stamp_upward_cut"), AshRewardTable.ashesFor("  EldenCombat:Margit  "));
        assertTrue(AshRewardTable.ashesFor("minecraft:zombie").isEmpty(), "没有配置的击杀不该解锁任何东西");
        assertTrue(AshRewardTable.ashesFor("bare").isEmpty(), "裸路径不能被当成来源");
        assertTrue(AshRewardTable.ashesFor(null).isEmpty());
    }

    @Test void rewardedAshListDeduplicates() {
        List<String> all = AshRewardTable.allRewardedAshes();
        assertEquals(all.size(), new HashSet<>(all).size());
        for (String ash : all) assertTrue(AshRewardTable.entries().stream()
                .anyMatch(entry -> entry.ashes().contains(ash)));
    }
}
