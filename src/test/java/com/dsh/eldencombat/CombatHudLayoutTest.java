package com.dsh.eldencombat;

import com.dsh.eldencombat.client.CombatHudLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatHudLayoutTest {
    @Test void matchesFinalArtAt480By270() {
        var layout = CombatHudLayout.at(480, 270, 63, 53);
        assertEquals(149, layout.fpX());
        assertEquals(250, layout.staminaX());
        assertEquals(207, layout.fpY());
        assertEquals(217, layout.staminaY());
        assertEquals(174, layout.selectedItemY());
        assertEquals(216 - 2, layout.fpY() + CombatHudLayout.BAR_HEIGHT);
    }

    @Test void extraHealthAndMountRowsMoveTheirOwnColumn() {
        for (int width : new int[]{320, 480, 640, 854}) {
            var base = CombatHudLayout.at(width, 270, 63, 53);
            var health = CombatHudLayout.at(width, 270, 83, 53);
            var mount = CombatHudLayout.at(width, 270, 63, 83);
            assertEquals(base.fpY() - 20, health.fpY());
            assertEquals(base.selectedItemY() - 20, health.selectedItemY());
            assertEquals(base.staminaY(), health.staminaY());
            assertEquals(base.staminaY() - 30, mount.staminaY());
            assertEquals(base.fpY(), mount.fpY());
            assertEquals(width / 2 + 91, base.staminaX() + CombatHudLayout.BAR_WIDTH);
        }
    }

    @Test void airOwnsTheSlotUntilNativeBubblesDisappear() {
        assertTrue(CombatHudLayout.showAir(true, 300, 300));
        assertTrue(CombatHudLayout.showAir(false, 299, 300));
        assertTrue(CombatHudLayout.showAir(true, 0, 300));
        assertFalse(CombatHudLayout.showAir(false, 300, 300));
    }

    @Test void invalidResourcesCannotOverflowFill() {
        assertEquals(0, CombatHudLayout.filled(0, 100, 79));
        assertEquals(79, CombatHudLayout.filled(100, 100, 79));
        assertEquals(39, CombatHudLayout.filled(50, 100, 79));
        assertEquals(79, CombatHudLayout.filled(200, 100, 79));
        assertEquals(0, CombatHudLayout.filled(-1, 100, 79));
        assertEquals(0, CombatHudLayout.filled(1, 0, 79));
        assertEquals(0, CombatHudLayout.filled(Float.NaN, 100, 79));
        assertEquals(79, CombatHudLayout.filled(Float.MAX_VALUE, Float.MIN_VALUE, 79));
    }
}
