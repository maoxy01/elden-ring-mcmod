package com.dsh.eldencombat;

import com.dsh.eldencombat.core.AshUnlockSet;
import com.dsh.eldencombat.core.AshWeaponGroups;
import com.dsh.eldencombat.server.skills.OrdinaryAshCatalog;
import com.dsh.eldencombat.server.skills.RemembranceSkillSpec;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 普通战灰目录的不变量测试（不加载 Minecraft）。 */
class OrdinaryAshCatalogTest {

    @Test void catalogIsNotEmptyAndIdsAreUniqueAndNamespaced() {
        List<OrdinaryAshCatalog.Entry> entries = OrdinaryAshCatalog.entries();
        assertFalse(entries.isEmpty(), "实际支持列表不能是空的（空目录必须如实报告，而不是声称完成）");
        Set<String> seen = new HashSet<>();
        for (OrdinaryAshCatalog.Entry entry : entries) {
            assertNotNull(AshUnlockSet.normalize(entry.id()), "必须是完整 namespaced id：" + entry.id());
            assertTrue(seen.add(entry.id()), "战灰 id 不能重复：" + entry.id());
            assertFalse(entry.label().isBlank(), "界面文案不能空：" + entry.id());
        }
    }

    /** {@code AshBindings.Definition.implemented()} 就是靠这条相等关系判定的。 */
    @Test void specIdMatchesEntryIdSoImplementedCheckPasses() {
        for (OrdinaryAshCatalog.Entry entry : OrdinaryAshCatalog.entries()) {
            assertEquals(entry.id(), entry.spec().id(),
                    "spec.id 必须与目录 id 完全一致，否则 implemented() 会判成未实现 -> 不可选");
        }
    }

    @Test void everyEntryIsSingleStageWithoutFollowup() {
        for (OrdinaryAshCatalog.Entry entry : OrdinaryAshCatalog.entries()) {
            RemembranceSkillSpec spec = entry.spec();
            assertFalse(spec.hasFollowup(), entry.id() + " 不该有追击窗口");
            assertEquals(1, spec.maxStages(), entry.id() + " 必须是单段");
            assertEquals(0, spec.followupWindowTicks(), entry.id() + " 追击窗必须为 0");
            assertEquals(spec.fpCost(), spec.costForStage(0), 0.0001f, entry.id() + " 首段费用必须等于 fpCost");
        }
    }

    @Test void everyEntryHasAPositiveTimelineAndCost() {
        for (OrdinaryAshCatalog.Entry entry : OrdinaryAshCatalog.entries()) {
            RemembranceSkillSpec spec = entry.spec();
            assertTrue(spec.windupTicks() > 0, entry.id() + " 需要蓄势");
            assertTrue(spec.activeTicks() > 0, entry.id() + " 需要生效窗口");
            assertTrue(spec.recoveryTicks() >= 0, entry.id() + " 收招不能为负");
            assertTrue(spec.fpCost() > 0, entry.id() + " 必须真的花 FP（否则 SkillTransactions 无法计费/退款）");
            assertTrue(spec.damage() > 0, entry.id() + " 伤害必须为正");
            assertTrue(spec.poiseDamage() >= 0, entry.id() + " 削韧不能为负");
            assertTrue(spec.effectRange() >= 1.5, entry.id() + " 判定距离必须够得着");
            assertTrue(spec.effectRadius() >= 0.5, entry.id() + " 判定半径必须为正");
            assertTrue(spec.stageBudget() > spec.totalNominalTicks(),
                    entry.id() + " 阶段预算必须留出余量，否则会先被超时保护取消");
        }
    }

    @Test void everyEntryUsesAKnownNonEmptyWeaponGroup() {
        for (OrdinaryAshCatalog.Entry entry : OrdinaryAshCatalog.entries()) {
            AshWeaponGroups.Group group = AshWeaponGroups.byKey(entry.groupKey());
            assertNotNull(group, entry.id() + " 的分组不存在：" + entry.groupKey());
            assertFalse(group.profileIds().isEmpty(), entry.id() + " 的分组不能为空（空白名单=永远不可选）");
            assertFalse(AshWeaponGroups.itemIds(entry.groupKey(), "eldencombat").isEmpty(),
                    entry.id() + " 的分组必须能展开出物品 id");
        }
    }

    @Test void ordinaryIdsNeverCollideWithRemembranceIds() {
        Set<String> remembrance = RemembranceSkillSpec.defaultsById().keySet();
        for (OrdinaryAshCatalog.Entry entry : OrdinaryAshCatalog.entries()) {
            String bare = entry.id().substring(entry.id().indexOf(':') + 1);
            assertFalse(remembrance.contains(bare), "普通战灰不能占用追忆战灰的 id：" + entry.id());
        }
    }

    @Test void groupsExpandToCompleteNamespacedIdsWithoutDuplicates() {
        List<String> thrusting = AshWeaponGroups.itemIds("thrusting", "eldencombat");
        assertEquals(5, thrusting.size());
        assertEquals(thrusting.size(), new HashSet<>(thrusting).size(), "展开结果不能有重复");
        assertTrue(thrusting.contains("eldencombat:spear"));
        assertTrue(thrusting.contains("eldencombat:heavy_thrusting_sword"));
        for (String id : thrusting) {
            assertEquals(id, AshUnlockSet.normalize(id));
            assertTrue(AshWeaponGroups.contains("thrusting", id.substring(id.indexOf(':') + 1)));
        }
    }

    @Test void unknownGroupIsEmptyInsteadOfThrowing() {
        assertTrue(AshWeaponGroups.itemIds("nope", "eldencombat").isEmpty());
        assertFalse(AshWeaponGroups.contains("nope", "spear"));
        assertNull(AshWeaponGroups.byKey("nope"));
        assertTrue(AshWeaponGroups.itemIds("thrusting", null).contains("eldencombat:spear"),
                "namespace 缺省时应回落到本模组命名空间");
    }
}
