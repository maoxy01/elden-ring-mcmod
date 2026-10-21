package com.dsh.eldencombat;

import com.dsh.eldencombat.client.CombatHudLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CombatHudLayoutTest {
    @Test void staminaOccupiesNativeExperienceSlotAndManaAlignsWithFood() {
        for (int width : new int[]{320, 480, 640, 854}) {
            var layout = CombatHudLayout.at(width, 240, 49, 49);
            assertEquals(width / 2 - 91, layout.staminaX());
            assertEquals(240 - 29, layout.staminaY());
            assertEquals(width / 2 + 91, layout.fpX() + CombatHudLayout.FP_WIDTH);
            assertTrue(layout.fpY() + CombatHudLayout.FP_HEIGHT < 240 - 49);
            assertTrue(layout.jumpY() + CombatHudLayout.STAMINA_HEIGHT < layout.staminaY());
        }
    }

    @Test void tallHealthAirAndMountStacksKeepManaAboveSelectedItemText() {
        for (int height : new int[]{240, 360, 540}) for (int stack : new int[]{49, 59, 79, 109}) {
            var layout = CombatHudLayout.at(640, height, stack, stack + 8);
            int itemName = height - Math.max(59, stack + 8 + 9);
            assertTrue(itemName - layout.selectedItemLift(itemName, 9) + 9 <= layout.fpY() - 12);
            assertTrue(layout.fpY() + CombatHudLayout.FP_HEIGHT < height - stack - 8);
            assertTrue(layout.fpY() - 10 >= 0);
        }
    }

    @Test void emptyFullAndInvalidServerResourcesNeverOverflowTheTrack() {
        assertEquals(0, CombatHudLayout.filled(0, 100, 182));
        assertEquals(182, CombatHudLayout.filled(100, 100, 182));
        assertEquals(91, CombatHudLayout.filled(50, 100, 182));
        assertEquals(182, CombatHudLayout.filled(200, 100, 182));
        assertEquals(0, CombatHudLayout.filled(-1, 100, 182));
        assertEquals(0, CombatHudLayout.filled(1, 0, 182));
        assertEquals(0, CombatHudLayout.filled(Float.NaN, 100, 182));
    }
}
