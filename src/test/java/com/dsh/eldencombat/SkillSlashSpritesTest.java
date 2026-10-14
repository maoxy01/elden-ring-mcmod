package com.dsh.eldencombat;

import com.dsh.eldencombat.effects.SkillEffectMesh.Kind;
import com.dsh.eldencombat.effects.SkillSlashSprites;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillSlashSpritesTest {
    @Test void stripsAreBoundedFiniteAndElongated() {
        for (var kind : new Kind[]{Kind.WATERFOWL, Kind.DEATH_SLASH, Kind.CURSED_SLASH})
            for (float radius : new float[]{.25f, 3, 6, Float.NaN})
                for (float progress : new float[]{0, .1f, .5f, .99f}) {
                    var strips = SkillSlashSprites.build(kind, radius, progress, 2);
                    assertFalse(strips.isEmpty()); assertTrue(strips.size() <= SkillSlashSprites.MAX_STRIPS);
                    for (var s : strips) {
                        assertTrue(s.width() > s.height());
                        assertTrue(s.width() < 7.6f && s.height() > 0);
                        assertTrue(s.frame() >= 0 && s.frame() < 16);
                        assertTrue(s.alpha() > 0 && s.alpha() <= 1);
                        assertTrue(Float.isFinite(s.x()+s.y()+s.z()+s.roll()));
                    }
                }
    }

    @Test void endedOrUnsupportedEffectsEmitNothing() {
        for (Kind kind : Kind.values()) {
            for (float p : new float[]{1, Float.NaN, Float.POSITIVE_INFINITY})
                assertTrue(SkillSlashSprites.build(kind, 3, p, 0).isEmpty());
            if (!SkillSlashSprites.supports(kind)) assertTrue(SkillSlashSprites.build(kind, 3, .3f, 0).isEmpty());
        }
    }

    @Test void skillsHaveDifferentLayersWidthsAndColors() {
        var water = SkillSlashSprites.build(Kind.WATERFOWL, 3, .4f, 0);
        var death = SkillSlashSprites.build(Kind.DEATH_SLASH, 3, .4f, 0);
        var curse = SkillSlashSprites.build(Kind.CURSED_SLASH, 3, .4f, 0);
        assertEquals(12, water.size()); assertEquals(6, death.size()); assertEquals(6, curse.size());
        assertTrue(death.get(0).height() > curse.get(0).height());
        assertTrue(curse.get(0).height() > water.get(0).height());
        assertNotEquals(death.get(0).rgb(), curse.get(0).rgb());
        assertNotEquals(water.get(1).rgb(), death.get(1).rgb());
    }

    @Test void phaseMirrorsDirectionAndLayersRevealOverTime() {
        var first = SkillSlashSprites.build(Kind.WATERFOWL, 3, 0, 0);
        var all = SkillSlashSprites.build(Kind.WATERFOWL, 3, .4f, 0);
        var mirrored = SkillSlashSprites.build(Kind.WATERFOWL, 3, .4f, 1);
        assertEquals(2, first.size()); assertEquals(12, all.size());
        for (int i = 0; i < all.size(); i++) {
            assertEquals(-all.get(i).x(), mirrored.get(i).x());
            assertEquals(-all.get(i).roll(), mirrored.get(i).roll());
        }
        assertTrue(all.get(0).frame() > first.get(0).frame());
    }

    @Test void wideSkillsAreLargerAndFitTheirRenderBounds() {
        for (var kind : new Kind[]{Kind.WATERFOWL, Kind.DEATH_SLASH}) {
            var strips = SkillSlashSprites.build(kind, 3, .5f, 0);
            assertTrue(strips.get(0).width() > 7);
            for (float p : new float[]{0,.2f,.5f,.99f}) for (var s : SkillSlashSprites.build(kind, 3, p, 1)) {
                double edge = Math.max(Math.abs(s.x()),Math.abs(s.z())) + Math.hypot(s.width(),s.height())/2;
                assertTrue(edge < SkillSlashSprites.cullingRadius(kind,3));
            }
        }
        assertTrue(SkillSlashSprites.build(Kind.CURSED_SLASH,3,.5f,0).get(0).width()<4.3f);
    }
}
