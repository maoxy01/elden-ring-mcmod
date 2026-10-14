package com.dsh.eldencombat;

import com.dsh.eldencombat.core.WeaponProfiles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponNamespaceTest {
    @Test void externalIdenticalPathsCannotAcquireOurProfile() {
        for(String path:new String[]{"dagger","spear","straight_sword"}) {
            assertNotNull(WeaponProfiles.of("eldencombat:"+path));
            assertSame(WeaponProfiles.of(path),WeaponProfiles.of("eldencombat:"+path));
            assertNull(WeaponProfiles.of("spartan_weaponry_unofficial:"+path));
            assertNull(WeaponProfiles.of("minecraft:"+path));
        }
        assertNull(WeaponProfiles.of("eldencombat:not_registered"));
    }
}
