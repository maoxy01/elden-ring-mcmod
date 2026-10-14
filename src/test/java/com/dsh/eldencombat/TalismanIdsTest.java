package com.dsh.eldencombat;

import com.dsh.eldencombat.core.TalismanIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** §3.6 护符 ID 命名空间迁移：裸路径补 eldencombat，完整 ID 原样保留，非法输入不被猜成合法 ID。 */
class TalismanIdsTest {

    @Test void legacyBarePathIsClaimedByThisMod() {
        assertEquals("eldencombat:warrior_jar_shard", TalismanIds.normalize("warrior_jar_shard"),
                "旧存档写的是裸路径，必须按本模组认领");
    }

    @Test void fullIdKeepsItsOwnNamespace() {
        assertEquals("othermod:warrior_jar_shard", TalismanIds.normalize("othermod:warrior_jar_shard"));
        // 不同 namespace 的同名护符是**两个身份**，不能互相覆盖
        assertNotEquals(TalismanIds.normalize("warrior_jar_shard"),
                TalismanIds.normalize("othermod:warrior_jar_shard"));
    }

    @Test void surroundingWhitespaceIsTrimmedButInnerJunkIsRejected() {
        assertEquals("eldencombat:green_turtle_talisman", TalismanIds.normalize("  green_turtle_talisman  "));
        assertEquals("", TalismanIds.normalize("Warrior_Jar_Shard"), "大写不是合法 ResourceLocation");
        assertEquals("", TalismanIds.normalize("eldencombat:"), "空路径非法");
        assertEquals("", TalismanIds.normalize(":warrior_jar_shard"), "空 namespace 非法");
        assertEquals("", TalismanIds.normalize("a:b:c"), "多冒号非法");
        assertEquals("", TalismanIds.normalize(""));
        assertEquals("", TalismanIds.normalize(null));
    }

    @Test void pathAndNamespaceHelpers() {
        assertEquals("warrior_jar_shard", TalismanIds.pathOf("eldencombat:warrior_jar_shard"));
        assertEquals("warrior_jar_shard", TalismanIds.pathOf("warrior_jar_shard"));
        assertTrue(TalismanIds.isModId("eldencombat:x"));
        assertFalse(TalismanIds.isModId("othermod:x"));
    }
}
