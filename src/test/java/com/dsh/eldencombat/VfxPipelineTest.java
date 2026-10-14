package com.dsh.eldencombat;

import com.dsh.eldencombat.effects.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VfxPipelineTest {
    @Test void countsSubmittedQuadVerticesWithoutPretendingGpuCullHalvesCpuWork() {
        var boxes = SkillEffectMesh.build(SkillEffectMesh.Kind.GOLD_WAVE, 3, .2f, 0);
        var counts = VfxBudget.validate(SkillEffectMesh.Kind.GOLD_WAVE, boxes, List.of(), List.of());
        assertEquals(boxes.size() * 24, counts.vertices());
        assertEquals(2304, VfxBudget.MAX_VERTICES);
        assertThrows(IllegalArgumentException.class, () -> VfxBudget.validate(SkillEffectMesh.Kind.GOLD_WAVE,
                java.util.Collections.nCopies(97, boxes.getFirst()), List.of(), List.of()));
        var invalid = new SkillEffectMesh.Box(Float.NaN, 0, 0, 1, 1, 1, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> VfxBudget.validate(SkillEffectMesh.Kind.GOLD_WAVE, List.of(invalid), List.of(), List.of()));
    }
    @Test void frameBudgetNeverOverspendsAndKeepsAMinimumForOtherCandidates() {
        for (int entities : new int[]{1, 8, 40, 100, 1000}) {
            var budget = new VfxFrameBudget();budget.begin(entities);int vertices = 0;
            for (int i = 0; i < entities; i++) {
                int boxes = budget.mainBoxes(96);assertTrue(boxes >= 1);
                vertices += boxes * 24 + budget.sprites(12) * 4;
            }
            assertTrue(vertices <= 30000);assertEquals(30000 - vertices, budget.remaining());
        }
        var huge = new VfxFrameBudget();huge.begin(Integer.MAX_VALUE);
        assertEquals(0, huge.mainBoxes(96));assertEquals(30000, huge.remaining());
    }
    @Test void lodUsesSquaredDistancesAndDoesNotDropCoreAt128Blocks() {
        assertEquals(VfxBudget.Lod.FULL, VfxBudget.lod(16 * 16));
        assertEquals(VfxBudget.Lod.REDUCED, VfxBudget.lod(48 * 48));
        assertEquals(VfxBudget.Lod.CORE, VfxBudget.lod(128 * 128));
        assertEquals(VfxBudget.Lod.HIDDEN, VfxBudget.lod(128 * 128 + 1));
        assertEquals(VfxBudget.Lod.HIDDEN, VfxBudget.lod(Double.NaN));
    }
    @Test void squareSelectionKeepsAllFourConnectedSides() {
        for (int slot = 0; slot < 4; slot++) assertEquals(slot, VfxBudget.index(slot, 40, 12, true));
        var seen = new java.util.HashSet<Integer>();
        for (int slot = 0; slot < 12; slot++) assertTrue(seen.add(VfxBudget.index(slot, 40, 12, true)));
    }
    @Test void opaqueBoxesCullOnlyBackFacesAndTranslucentBoxesStayDoubleSided() {
        var box = new SkillEffectMesh.Box(-1, -1, -1, 1, 1, 1, 0, 1);
        assertEquals(1, VfxBudget.faceMask(box, 0, 0, -3, 1));
        assertEquals(2 | 8 | 16, VfxBudget.faceMask(box, 3, 3, 3, 1));
        assertEquals(63, VfxBudget.faceMask(box, 0, 0, 0, 1));
        assertEquals(63, VfxBudget.faceMask(box, 3, 3, 3, .5f));
    }
    @Test void paletteReloadChangesColorsAndRejectsBadReferencesAtomically() {
        var original = FxPalette.current();
        try {
            var map = new java.util.HashMap<>(original.colors());map.put("gold.mid", 0x917344);
            FxPalette.apply(new FxPalette.Data(map, false));
            assertFalse(FxPalette.current().slashSprites());
            assertTrue(SkillEffectMesh.build(SkillEffectMesh.Kind.GOLD_WAVE, 3, .2f, 0).stream().anyMatch(b -> b.rgb() == 0x917344));
            var active = FxPalette.current();map.remove("gold.highlight");
            assertThrows(IllegalArgumentException.class, () -> FxPalette.apply(new FxPalette.Data(map, true)));
            assertSame(active, FxPalette.current());
            assertThrows(IllegalArgumentException.class, () -> FxPalette.parse(JsonParser.parseString("{\"schemaVersion\":2}").getAsJsonObject()));
            assertThrows(IllegalArgumentException.class, () -> FxPalette.parse(JsonParser.parseString("{\"schemaVersion\":1,\"colors\":{\"gold\":{\"highlight\":\"invalid\",\"mid\":\"#000000\",\"shadow\":\"#000000\"}}}").getAsJsonObject()));
        } finally { FxPalette.apply(original); }
    }
    @Test void generatedColorsAreRegisteredAndEveryEffectEnds() {
        for (var kind : SkillEffectMesh.Kind.values()) for (int phase = 0; phase < 3; phase++) {
            var boxes = SkillEffectMesh.build(kind, 3, .3f, phase);
            var strips = SkillSlashSprites.build(kind, 3, .3f, phase);
            var debris = SkillEffectMesh.debris(kind, 3, .3f);
            VfxBudget.validate(kind, boxes, strips, debris);
            for (var box : boxes) assertTrue(FxPalette.current().colors().containsValue(box.rgb()));
            for (var strip : strips) assertTrue(FxPalette.current().colors().containsValue(strip.rgb()));
            assertTrue(SkillEffectMesh.build(kind, 3, 1, phase).isEmpty());
            assertTrue(SkillSlashSprites.build(kind, 3, 1, phase).isEmpty());
            assertTrue(SkillEffectMesh.debris(kind, 3, 1).isEmpty());
        }
    }
}
