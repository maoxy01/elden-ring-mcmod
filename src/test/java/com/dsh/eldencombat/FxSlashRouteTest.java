package com.dsh.eldencombat;

import com.dsh.eldencombat.effects.FxSlashRoute;
import com.dsh.eldencombat.effects.FxSlashRoute.Outline;
import com.dsh.eldencombat.effects.FxSlashRoute.Orientation;
import com.dsh.eldencombat.effects.SkillEffectMesh.Kind;
import com.dsh.eldencombat.effects.SkillOutlineShapes;
import com.dsh.eldencombat.effects.SkillSlashSprites;
import com.dsh.eldencombat.effects.VfxBudget;
import com.dsh.eldencombat.effects.VfxFrameBudget;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Routing rules for the three 2D samples. Pure logic only: no client type, no atlas and no game launch,
 * so a route can be authored and budgeted before any artwork exists.
 */
class FxSlashRouteTest {
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }

    @Test void onlyTheSelectedSamplesAreRoutedAndTheyStartPending() {
        int routed = 0;
        for (var kind : Kind.values()) {
            if (FxSlashRoute.routeId(kind) != null) routed++;
            if (kind != Kind.NONE) assertNotNull(FxSlashRoute.routeId(kind));
        }
        assertEquals(10, routed);
        assertEquals("waterfowl", FxSlashRoute.routeId(Kind.WATERFOWL));
        assertEquals("death_slash", FxSlashRoute.routeId(Kind.DEATH_SLASH));
        assertEquals("cursed_slash", FxSlashRoute.routeId(Kind.CURSED_SLASH));
        assertEquals("golden_wave", FxSlashRoute.routeId(Kind.GOLD_WAVE));
        assertNull(FxSlashRoute.routeId(null));
        assertNull(FxSlashRoute.defaultsForRoute(null));
        assertNull(FxSlashRoute.defaultsForRoute("unknown_sample"));
        assertSame(FxSlashRoute.WATERFOWL, FxSlashRoute.defaults(Kind.WATERFOWL));
        assertSame(FxSlashRoute.GOLD_WAVE, FxSlashRoute.defaults(Kind.GOLD_WAVE));
        assertNotNull(FxSlashRoute.defaults(Kind.BLOOD_RITUAL));
        for (var route : List.of(FxSlashRoute.WATERFOWL, FxSlashRoute.DEATH_SLASH,
                FxSlashRoute.CURSED_SLASH, FxSlashRoute.GOLD_WAVE)) {
            // Declaring a route must not change what the game draws until its artwork exists.
            assertFalse(route.ready());
            assertFalse(route.replacesSharedArtwork());
            assertFalse(route.usesOutline());
            assertEquals(Orientation.SKILL_PLANE, route.orientation());
            assertFalse(route.cameraFacing());
            assertEquals(FxSlashRoute.SEMANTIC_FRAMES, route.frameCount());
        }
        assertTrue(FxSlashRoute.LEGACY.cameraFacing());
        assertFalse(FxSlashRoute.LEGACY.usesOutline());
    }

    @Test void semanticFramesResampleOntoAnyDeclaredFrameCount() {
        for (int semantic = 0; semantic < FxSlashRoute.SEMANTIC_FRAMES; semantic++)
            assertEquals(semantic, FxSlashRoute.WATERFOWL.frameIndex(semantic));
        assertEquals(0, FxSlashRoute.WATERFOWL.frameIndex(-5));
        assertEquals(15, FxSlashRoute.WATERFOWL.frameIndex(99));
        assertEquals(0, FxSlashRoute.WATERFOWL.withFrames(1).frameIndex(7));
        for (int frames : new int[]{2, 3, 4, 8, 32, 64}) {
            var route = FxSlashRoute.WATERFOWL.withFrames(frames);
            assertEquals(0, route.frameIndex(0));
            assertEquals(frames - 1, route.frameIndex(FxSlashRoute.SEMANTIC_FRAMES - 1));
            var seen = new java.util.HashSet<Integer>();
            int previous = -1;
            for (int semantic = -5; semantic < 40; semantic++) {
                int index = route.frameIndex(semantic);
                assertTrue(index >= 0 && index < frames);
                assertTrue(index >= previous, "frame index must not go backwards");
                previous = index;
                seen.add(index);
            }
            // Every declared frame is reachable, so no frame of the delivered sequence is dead weight.
            assertEquals(Math.min(frames, FxSlashRoute.SEMANTIC_FRAMES), seen.size());
        }
        assertEquals(FxSlashRoute.MAX_FRAMES, new FxSlashRoute(Orientation.SKILL_PLANE, 0, 0, 0xFFFFFF,
                Outline.ARC, 0xFFFFFF, FxSlashRoute.MAX_FRAMES, false).frameCount());
    }

    @Test void manifestOverridesReplaceDefaultsAndRejectBadFields() {
        var overridden = FxSlashRoute.parse(json("""
                {"orientation":"camera","plane":{"yaw":12.5,"pitch":-30.0},
                 "tint":"#112233","outline":{"shape":"boxes","color":"#AABBCC"}}
                """), FxSlashRoute.WATERFOWL);
        assertEquals(Orientation.CAMERA, overridden.orientation());
        assertTrue(overridden.cameraFacing());
        assertEquals(12.5f, overridden.planeYaw());
        assertEquals(-30f, overridden.planePitch());
        assertEquals(0x112233, overridden.tint());
        assertEquals(Outline.BOXES, overridden.outline());
        assertEquals(0xAABBCC, overridden.outlineColor());
        assertEquals(FxSlashRoute.WATERFOWL.frameCount(), overridden.frameCount());
        // The declared defaults stay untouched: one manifest entry must not rewrite another skill's plane.
        assertEquals(Orientation.SKILL_PLANE, FxSlashRoute.WATERFOWL.orientation());
        assertEquals(0xFFFFFF, FxSlashRoute.WATERFOWL.tint());
        assertEquals(Outline.ARC, FxSlashRoute.WATERFOWL.outline());
        assertEquals(90f, FxSlashRoute.WATERFOWL.planePitch());
        assertEquals(FxSlashRoute.GOLD_WAVE, FxSlashRoute.parse(new JsonObject(), FxSlashRoute.GOLD_WAVE));
        assertThrows(IllegalArgumentException.class,
                () -> FxSlashRoute.parse(json("{\"orientation\":\"sideways\"}"), FxSlashRoute.WATERFOWL));
        assertThrows(IllegalArgumentException.class,
                () -> FxSlashRoute.parse(json("{\"outline\":{\"shape\":\"star\"}}"), FxSlashRoute.WATERFOWL));
        assertThrows(IllegalArgumentException.class,
                () -> FxSlashRoute.parse(json("{\"tint\":\"112233\"}"), FxSlashRoute.WATERFOWL));
        assertThrows(IllegalArgumentException.class,
                () -> FxSlashRoute.parse(json("{\"tint\":\"#FFF\"}"), FxSlashRoute.WATERFOWL));
        assertThrows(IllegalArgumentException.class,
                () -> FxSlashRoute.parse(json("{\"outline\":{\"color\":\"#GGGGGG\"}}"), FxSlashRoute.WATERFOWL));
        assertThrows(IllegalArgumentException.class,
                () -> FxSlashRoute.parse(json("{\"plane\":{\"yaw\":\"abc\"}}"), FxSlashRoute.WATERFOWL));
        assertThrows(IllegalArgumentException.class, () -> FxSlashRoute.parse(null, FxSlashRoute.WATERFOWL));
        assertThrows(IllegalArgumentException.class, () -> FxSlashRoute.parse(new JsonObject(), null));
        assertThrows(IllegalArgumentException.class, () -> route(Orientation.SKILL_PLANE, 0, 0, 0xFFFFFF, Outline.ARC, 0));
        assertThrows(IllegalArgumentException.class,
                () -> route(Orientation.SKILL_PLANE, 0, 0, 0xFFFFFF, Outline.ARC, FxSlashRoute.MAX_FRAMES + 1));
        assertThrows(IllegalArgumentException.class,
                () -> route(Orientation.SKILL_PLANE, Float.NaN, 0, 0xFFFFFF, Outline.ARC, 16));
        assertThrows(IllegalArgumentException.class, () -> route(Orientation.SKILL_PLANE, 0, 0, 0x1000000, Outline.ARC, 16));
        assertThrows(IllegalArgumentException.class, () -> route(null, 0, 0, 0xFFFFFF, Outline.ARC, 16));
        assertThrows(IllegalArgumentException.class, () -> route(Orientation.SKILL_PLANE, 0, 0, 0xFFFFFF, null, 16));
        var clamped = route(Orientation.SKILL_PLANE, 400f, -400f, 0xFFFFFF, Outline.ARC, 16);
        assertEquals(180f, clamped.planeYaw());
        assertEquals(-180f, clamped.planePitch());
        assertEquals(0xFFFFFF, FxSlashRoute.color("tint", "#ffffff"));
        assertThrows(IllegalArgumentException.class, () -> FxSlashRoute.orientation(" "));
        assertThrows(IllegalArgumentException.class, () -> FxSlashRoute.outline(""));
    }

    private static FxSlashRoute route(Orientation orientation, float yaw, float pitch, int tint, Outline outline,
                                      int frames) {
        return new FxSlashRoute(orientation, yaw, pitch, tint, outline, 0xFFFFFF, frames, false);
    }

    @Test void outlinesAreBoundedGridAlignedAndDeterministic() {
        for (var outline : List.of(Outline.ARC, Outline.CRESCENT, Outline.WAVE_FRONT))
            for (float progress : new float[]{.05f, .3f, .6f, .95f})
                for (int phase = 0; phase < 2; phase++) {
                    var panels = SkillOutlineShapes.build(outline, 0xE9FAFF, 3f, progress, phase, 2f, 0);
                    assertFalse(panels.isEmpty(), outline + " must keep a recognizable silhouette");
                    assertTrue(panels.size() <= SkillOutlineShapes.MAX_PANELS);
                    assertEquals(panels, SkillOutlineShapes.build(outline, 0xE9FAFF, 3f, progress, phase, 2f, 0));
                    assertEquals(panels.size() * 4, VfxBudget.outlineVertices(panels));
                    for (var panel : panels) {
                        for (float value : new float[]{panel.x(), panel.y(), panel.z(), panel.width(), panel.height(),
                                panel.roll(), panel.alpha()}) assertTrue(Float.isFinite(value));
                        for (float value : new float[]{panel.x(), panel.y(), panel.z(), panel.width(), panel.height()})
                            assertEquals(0f, value / SkillOutlineShapes.GRID - Math.round(value / SkillOutlineShapes.GRID),
                                    1e-3f, "panels snap to the pixel grid");
                        assertTrue(panel.width() > 0 && panel.height() > 0);
                        assertTrue(panel.alpha() > 0 && panel.alpha() <= 1);
                        assertEquals(0, panel.rgb() & ~0xFFFFFF);
                    }
                }
        // Nothing survives the end of the effect, an unknown plane, or a malformed colour.
        for (var outline : List.of(Outline.ARC, Outline.CRESCENT, Outline.WAVE_FRONT)) {
            assertTrue(SkillOutlineShapes.build(outline, 0xE9FAFF, 3f, 1f, 0, 2f, 0).isEmpty());
            assertTrue(SkillOutlineShapes.build(outline, 0xE9FAFF, 3f, Float.NaN, 0, 2f, 0).isEmpty());
            assertTrue(SkillOutlineShapes.build(outline, 0x1000000, 3f, .5f, 0, 2f, 0).isEmpty());
        }
        assertTrue(SkillOutlineShapes.build(Outline.BOXES, 0xE9FAFF, 3f, .5f, 0, 2f, 0).isEmpty());
        assertTrue(SkillOutlineShapes.build(null, 0xE9FAFF, 3f, .5f, 0, 2f, 0).isEmpty());
        assertTrue(SkillOutlineShapes.cullingRadius(Outline.WAVE_FRONT, 10f) > SkillOutlineShapes.cullingRadius(Outline.BOXES, 10f));
        assertEquals(5f, SkillOutlineShapes.cullingRadius(Outline.ARC, 3f));
        var tooMany = new ArrayList<SkillOutlineShapes.Panel>();
        for (int i = 0; i <= SkillOutlineShapes.MAX_PANELS; i++)
            tooMany.add(new SkillOutlineShapes.Panel(0, 0, 0, .5f, .5f, 0, 0xFFFFFF, 1));
        assertThrows(IllegalArgumentException.class, () -> VfxBudget.outlineVertices(tooMany));
        assertThrows(IllegalArgumentException.class, () -> VfxBudget.outlineVertices(List.of(
                new SkillOutlineShapes.Panel(Float.NaN, 0, 0, .5f, .5f, 0, 0xFFFFFF, 1))));
        assertThrows(IllegalArgumentException.class, () -> VfxBudget.outlineVertices(List.of(
                new SkillOutlineShapes.Panel(0, 0, 0, 0, .5f, 0, 0xFFFFFF, 1))));
        assertThrows(IllegalArgumentException.class, () -> VfxBudget.outlineVertices(List.of(
                new SkillOutlineShapes.Panel(0, 0, 0, .5f, .5f, 0, 0x1000000, 1))));
    }

    @Test void waveOutlineHonoursBlockedLanesAndKeepsAdvancing() {
        assertEquals(SkillOutlineShapes.MAX_PANELS,
                SkillOutlineShapes.build(Outline.WAVE_FRONT, 0xFFD044, 3f, .5f, 0, 2f, 0).size());
        assertEquals(SkillOutlineShapes.MAX_PANELS - 9,
                SkillOutlineShapes.build(Outline.WAVE_FRONT, 0xFFD044, 3f, .5f, 0, 2f, 1 | 1 << 5 | 1 << 15).size());
        assertTrue(SkillOutlineShapes.build(Outline.WAVE_FRONT, 0xFFD044, 3f, .5f, 0, 2f, 0xFFFF).isEmpty());
        // The crest is bound to the travelled distance, so the front does not freeze where it formed.
        float standing = lowest(SkillOutlineShapes.build(Outline.WAVE_FRONT, 0xFFD044, 3f, .5f, 0, 0f, 0));
        float advanced = lowest(SkillOutlineShapes.build(Outline.WAVE_FRONT, 0xFFD044, 3f, .5f, 0, 3f, 0));
        assertTrue(advanced < standing, "a further front must sit further along its own axis");
    }

    private static float lowest(List<SkillOutlineShapes.Panel> panels) {
        float lowest = Float.POSITIVE_INFINITY;
        for (var panel : panels) lowest = Math.min(lowest, panel.z());
        return lowest;
    }

    @Test void waveStripsCoverOpenLanesOnlyAndCarryTheirRoute() {
        var lanes = SkillSlashSprites.buildWave(3f, .5f, 8, 2f, 0);
        assertEquals(SkillSlashSprites.MAX_WAVE_STRIPS, lanes.size());
        var left = new java.util.HashSet<Float>();
        for (var strip : lanes) {
            assertEquals("golden_wave", strip.route());
            assertEquals(8, strip.frame());
            assertTrue(strip.alpha() > 0 && strip.alpha() <= 1);
            assertEquals(0xFFFFFF, strip.rgb());
            assertTrue(Float.isFinite(strip.x() + strip.y() + strip.z() + strip.width() + strip.height()));
            assertTrue(left.add(strip.x()), "each lane owns its own column");
        }
        assertEquals(SkillSlashSprites.MAX_WAVE_STRIPS - 1, SkillSlashSprites.buildWave(3f, .5f, 8, 2f, 1).size());
        assertTrue(SkillSlashSprites.buildWave(3f, .5f, 8, 2f, 0xFFFF).isEmpty());
        assertTrue(SkillSlashSprites.buildWave(3f, 1f, 8, 2f, 0).isEmpty());
        assertTrue(SkillSlashSprites.buildWave(3f, Float.NaN, 8, 2f, 0).isEmpty());
        // A routed strip names its material; the budget rejects an unnamed one instead of drawing a guess.
        VfxBudget.validate(Kind.GOLD_WAVE, List.of(), lanes, List.of());
        assertThrows(IllegalArgumentException.class, () -> VfxBudget.validate(Kind.GOLD_WAVE, List.of(),
                List.of(new SkillSlashSprites.Strip(0, 0, 0, 1, 1, 0, 0xFFFFFF, .5f, 0, null)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> VfxBudget.validate(Kind.GOLD_WAVE, List.of(),
                List.of(new SkillSlashSprites.Strip(0, 0, 0, 1, 1, 0, 0xFFFFFF, .5f, 0, " ")), List.of()));
        var budget = new VfxFrameBudget();
        budget.begin(1);
        assertEquals(SkillOutlineShapes.MAX_PANELS, budget.panels(SkillOutlineShapes.MAX_PANELS));
        assertEquals(VfxBudget.MAX_FRAME_VERTICES - SkillOutlineShapes.MAX_PANELS * 4, budget.remaining());
        var starved = new VfxFrameBudget();
        starved.begin(1);
        assertTrue(starved.panels(Integer.MAX_VALUE) <= VfxBudget.MAX_FRAME_VERTICES / 4);
        assertTrue(starved.remaining() >= 0);
    }

    /**
     * The shipped manifest is data, so a typo in it would silently keep the previous (empty) route table and
     * take the shared slash frames down with it. This walks the real file with the same rules the client uses:
     * a route that claims to be ready must have its PNGs on disk, and every declared frame count must be legal.
     */
    @Test void shippedManifestDeclaresTheFourSampleRoutesAndOnlyReadyOnesNeedArt() throws Exception {
        var data = json(Files.readString(Path.of("src/main/resources/assets/eldencombat/routes/2d.json")));
        var fx = data.getAsJsonObject("fx");
        var slash = fx.getAsJsonObject("slash");
        assertEquals(16, slash.getAsJsonArray("frames").size());
        assertEquals("minecraft:textures/atlas/particles.png", slash.get("atlas").getAsString());
        var routes = fx.getAsJsonObject("routes");
        for (var id : List.of("waterfowl", "death_slash", "cursed_slash", "golden_wave"))
            assertTrue(routes.has(id), "the three samples and the wave front must be routable: " + id);
        for (var entry : routes.entrySet()) {
            String id = entry.getKey();
            var defaults = FxSlashRoute.defaultsForRoute(id);
            assertNotNull(defaults, "unknown FX route in the manifest: " + id);
            var route = entry.getValue().getAsJsonObject();
            String status = route.get("status").getAsString();
            assertTrue(List.of("ready", "existing", "pending").contains(status));
            boolean ready = !status.equals("pending");
            int frames = route.getAsJsonArray("frames").size();
            assertTrue(frames >= 1 && frames <= FxSlashRoute.MAX_FRAMES);
            var spec = FxSlashRoute.parse(route, defaults.withFrames(frames).withReady(ready));
            assertEquals(ready, spec.ready());
            assertEquals(frames, spec.frameCount());
            for (var frame : route.getAsJsonArray("frames")) {
                String id0 = frame.getAsString();
                assertTrue(id0.contains(":"), "a frame id needs its namespace: " + id0);
                if (!ready) continue;
                // Whatever is switched on must ship its artwork, in this namespace, under textures/particle.
                String namespace = id0.substring(0, id0.indexOf(':'));
                String path = id0.substring(id0.indexOf(':') + 1);
                var png = Path.of("build/resources/main/assets", namespace, "textures", "particle", path + ".png");
                assertTrue(Files.exists(png), "ready route frame without artwork: " + png);
            }
        }
    }

    @Test void routedStripsKeepTheLegacyShapeAndThePaletteGateStaysAvailable() {
        for (var kind : new Kind[]{Kind.WATERFOWL, Kind.DEATH_SLASH, Kind.CURSED_SLASH}) {
            var strips = SkillSlashSprites.build(kind, 3f, .4f, 0);
            assertTrue(strips.size() <= SkillSlashSprites.MAX_STRIPS);
            VfxBudget.validate(kind, List.of(), strips, List.of());
            for (var strip : strips) {
                assertEquals(FxSlashRoute.routeId(kind), strip.route());
                assertTrue(strip.frame() >= 0 && strip.frame() < FxSlashRoute.SEMANTIC_FRAMES);
            }
        }
        assertTrue(SkillSlashSprites.build(Kind.GOLD_WAVE, 3f, .4f, 0).isEmpty());
        assertEquals(16, FxSlashRoute.SEMANTIC_FRAMES);
        assertEquals(12, SkillSlashSprites.MAX_STRIPS);
    }
}
