package com.dsh.eldencombat;

import com.dsh.eldencombat.core.AshUnlockSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 战灰解锁集合的纯逻辑测试（不加载 Minecraft）。 */
class AshUnlockSetTest {

    @Test void normalizeRequiresFullNamespace() {
        assertNull(AshUnlockSet.normalize("spinning_slash"), "裸路径必须被拒绝：裸路径说不清是哪个命名空间");
        assertNull(AshUnlockSet.normalize(":path"));
        assertNull(AshUnlockSet.normalize("ns:"));
        assertNull(AshUnlockSet.normalize("a:b:c"));
        assertNull(AshUnlockSet.normalize(null));
        assertNull(AshUnlockSet.normalize("Bad Namespace:x"));
        assertNull(AshUnlockSet.normalize("ns:BAD PATH"));
    }

    @Test void normalizeLowercasesAndKeepsPath() {
        assertEquals("eldencombat:spinning_slash", AshUnlockSet.normalize("  EldenCombat:Spinning_Slash  "));
        assertEquals("minecraft:a/b-c.d_e", AshUnlockSet.normalize("minecraft:a/b-c.d_e"));
    }

    @Test void grantIsIdempotent() {
        AshUnlockSet set = new AshUnlockSet();
        assertTrue(set.grant("eldencombat:a"));
        assertFalse(set.grant("eldencombat:a"), "重复授予必须返回 false");
        assertFalse(set.grant("ELDENCOMBAT:A"), "规范化后相同，也算重复");
        assertEquals(1, set.size());
    }

    @Test void grantRejectsBarePathButOwnsDoesNotThrow() {
        AshUnlockSet set = new AshUnlockSet();
        assertThrows(IllegalArgumentException.class, () -> set.grant("bare"));
        assertFalse(set.owns("bare"), "查询路径不该抛异常");
        assertFalse(set.owns(null));
    }

    @Test void revokeRemovesOnlyThatId() {
        AshUnlockSet set = new AshUnlockSet();
        set.grant("eldencombat:a");
        set.grant("eldencombat:b");
        assertTrue(set.revoke("eldencombat:a"));
        assertFalse(set.revoke("eldencombat:a"), "撤销两次第二次应无事发生");
        assertEquals(List.of("eldencombat:b"), set.ids());
    }

    @Test void idsAreSortedAndASnapshot() {
        AshUnlockSet set = new AshUnlockSet();
        set.grant("eldencombat:zeta");
        set.grant("eldencombat:alpha");
        assertEquals(List.of("eldencombat:alpha", "eldencombat:zeta"), set.ids(), "落盘顺序必须稳定");
        List<String> snapshot = set.ids();
        set.grant("eldencombat:mid");
        assertEquals(2, snapshot.size(), "ids() 是快照，不随后续修改变化");
    }

    @Test void saveLoadRoundTripDedupes() {
        AshUnlockSet set = new AshUnlockSet();
        set.grant("eldencombat:a");
        set.grant("eldencombat:b");
        List<String> saved = set.ids();
        AshUnlockSet.LoadResult result = AshUnlockSet.load(new ArrayList<>(List.of(saved.get(0), saved.get(0), saved.get(1))));
        assertEquals(0, result.dropped());
        assertEquals(saved, result.set().ids());
    }

    @Test void loadDropsGarbageButKeepsTheRest() {
        AshUnlockSet.LoadResult result = AshUnlockSet.load(
                List.of("eldencombat:good", "", "   ", "no_namespace", "a:b:c", "bad id:x"));
        assertEquals(List.of("eldencombat:good"), result.set().ids(), "一条脏数据不能让整份存档读崩");
        assertEquals(5, result.dropped(), "丢弃数必须如实上报，不能静默");
    }

    @Test void loadToleratesNullAndEmpty() {
        assertEquals(0, AshUnlockSet.load(null).set().size());
        assertEquals(0, AshUnlockSet.load(List.of()).set().size());
    }
}
