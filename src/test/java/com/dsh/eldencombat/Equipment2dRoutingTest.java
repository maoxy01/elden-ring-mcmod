package com.dsh.eldencombat;

import com.dsh.eldencombat.client.equipment2d.EquipmentDisplay;
import com.dsh.eldencombat.client.equipment2d.EquipmentGeometry;
import com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot;
import com.dsh.eldencombat.client.equipment2d.EquipmentStateResolver;
import com.dsh.eldencombat.client.equipment2d.ItemSpriteGeometry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance tests for the repaired equipment 2D route (contract
 * DSH-EQUIPMENT-2D-IMPLEMENTATION-CONTRACT-20261015 §2, §10, §12).
 *
 * <p>The shipped {@code routes/2d.json} is fed through the <em>real</em> parser, and its own display
 * numbers are re-derived from the vanilla reference poses and the item's grip pixel, so the file, the
 * parser, the generator and the runtime transform are held to one shared unit and one origin. The
 * tests never touch a texture atlas, a buffer source or a GL context: everything checked here is pure
 * geometry, units and admission structure.
 */
class Equipment2dRoutingTest {

    private static final Path ROUTE = Path.of("src/main/resources/assets/eldencombat/routes/2d.json");
    private static final Path CANDIDATES = Path.of("art/equipment-2d/full/display-candidates.json");
    private static final Path OVERRIDES = Path.of("art/equipment-2d/full/display-overrides.json");

    /** Every context {@code ItemDisplayContext} can ask for: the seven required plus {@code head}. */
    private static final List<String> ALL_CONTEXTS = List.of(
            "thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand",
            "gui", "ground", "fixed", "head");

    private static final float EPS = 1.0e-4f;

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    // ---------------------------------------------------------------------------------------------
    // 1. admission structure of the shipped table
    // ---------------------------------------------------------------------------------------------

    @Test
    void jomlRotationXyzIsRxRyRzWhichIsHowVanillaReadsDisplayJson() {
        float rx = (float) Math.toRadians(20);
        float ry = (float) Math.toRadians(-90);
        float rz = (float) Math.toRadians(55);
        Matrix4f viaQuaternion = new Matrix4f().rotate(new Quaternionf().rotationXYZ(rx, ry, rz));
        Matrix4f inOrderXyz = new Matrix4f().rotateX(rx).rotateY(ry).rotateZ(rz);
        Matrix4f inOrderZyx = new Matrix4f().rotateZ(rz).rotateY(ry).rotateX(rx);

        assertEquals(0f, maxDiff(inOrderXyz, viaQuaternion), 1.0e-6f,
                "Quaternionf.rotationXYZ composes as Rx.Ry.Rz, so display JSON is inverted that way");
        assertTrue(maxDiff(inOrderZyx, viaQuaternion) > 1.0e-3f,
                "if this ever holds, every stored euler triple has to be re-derived");
    }

    @Test
    void shippedTableAdmitsEveryStructurallyCompleteEntryAndLeavesTheLegacyHalvesAlone() throws Exception {
        JsonObject document = json(ROUTE);
        EquipmentRouteSnapshot.Data data = EquipmentRouteSnapshot.parse(document);

        assertEquals(42, data.admitted(), "the selected starscourge artwork has been readmitted");
        assertEquals(42, data.entries().size());
        assertTrue(data.rejected().isEmpty());
        assertEquals(42, data.notes().size(), "every admitted entry records its receipt");

        int ownSprites = 0;
        int vanillaModels = 0;
        int providerModels = 0;
        int shields = 0;
        for (EquipmentRouteSnapshot.Entry entry : data.entries().values()) {
            assertTrue(entry.blockers().isEmpty(), entry.id() + " was admitted with hard blockers");
            assertFalse(entry.acceptance().humanAccepted(), entry.id() + " must not claim acceptance");
            assertFalse(entry.notes().isEmpty(), entry.id() + " carries no receipt at all");

            if (entry.route() != EquipmentRouteSnapshot.Route.OWN_SPRITE) {
                // A provider route reuses the provider's own baked model, so it owns no display of its
                // own: null here is the route difference, not a missing input.
                if (entry.route() == EquipmentRouteSnapshot.Route.VANILLA_MODEL) {
                    vanillaModels++;
                } else {
                    providerModels++;
                }
                assertNull(entry.display(), entry.id());
                assertEquals("not_required_inherit_provider", entry.acceptance().displayStatus(),
                        entry.id() + " inherits the provider's own display, so it declares none");
                assertNotNull(entry.providerBase(), entry.id());
                assertFalse(entry.providerStates().isEmpty(), entry.id());
                assertFalse(entry.requiredPresence().isEmpty(), entry.id());
                continue;
            }

            ownSprites++;
            EquipmentGeometry geometry = assertInstanceOf(EquipmentGeometry.class, entry.geometry(), entry.id());
            EquipmentDisplay display = assertInstanceOf(EquipmentDisplay.class, entry.display(), entry.id());
            assertNotNull(entry.ownHeldModel(), entry.id());
            assertFalse(entry.ownStates().isEmpty(), entry.id());
            assertFalse(entry.requiredPresence().isEmpty(), entry.id());
            assertNull(entry.providerBase(), entry.id());
            for (String context : ALL_CONTEXTS) {
                assertTrue(display.declares(context), entry.id() + " does not declare " + context);
            }
            assertEquals("fixed", display.inheritance(), entry.id());
            assertIdentity(display.resolve("gui"), entry.id() + " gui");
            assertTrue(Set.of("candidate_not_game_verified", "calibrated_not_game_verified")
                            .contains(entry.acceptance().displayStatus()),
                    entry.id() + " owns its display, so it may only be an unverified or calibrated "
                            + "candidate, was " + entry.acceptance().displayStatus());
            assertTrue(Math.abs(geometry.gripX()) <= 0.5f && Math.abs(geometry.gripY()) <= 0.5f,
                    entry.id() + " grip must live inside the emitted [-0.5,0.5] canvas, not in 16-unit space");

            if (entry.kind() == EquipmentRouteSnapshot.Kind.SHIELD) {
                shields++;
                assertTrue(entry.doubleFaced(), entry.id() + " ships front and back as one geometry");
                EquipmentDisplay blocking = entry.blockingDisplay();
                assertNotNull(blocking, entry.id() + " has no separate blocking pose");
                for (String context : ALL_CONTEXTS) {
                    EquipmentDisplay.Frame frame = blocking.resolve(context);
                    assertNotNull(frame, entry.id() + " blocking " + context);
                }
            } else {
                assertNull(entry.blockingDisplay(), entry.id() + " is not a shield, so it has no guard pose");
            }
        }

        assertEquals(28, ownSprites);
        assertEquals(4, vanillaModels);
        assertEquals(10, providerModels);
        assertEquals(3, shields);

        // The legacy halves are untouched: the fx palette, the FP overlay and the existing-model list
        // are byte-for-byte the ones the old path used, so a routed item that falls back still draws
        // exactly what it drew before.
        JsonObject slash = document.getAsJsonObject("fx").getAsJsonObject("slash");
        assertEquals("existing", slash.get("status").getAsString());
        JsonArray frames = slash.getAsJsonArray("frames");
        assertEquals(16, frames.size());
        assertEquals("minecraft:explosion_0", frames.get(0).getAsString());
        assertEquals("minecraft:explosion_15", frames.get(15).getAsString());
        assertEquals("ready", document.getAsJsonObject("ui").getAsJsonObject("fp").get("status").getAsString());
        assertTrue(document.getAsJsonArray("existing").size() > 50);
    }

    // ---------------------------------------------------------------------------------------------
    // 2. one origin, one unit: the shipped display really reproduces the vanilla reference palm
    // ---------------------------------------------------------------------------------------------

    @Test
    void everyShippedDisplayReproducesItsReferencePoseOnTheItemsOwnGrip() throws Exception {
        EquipmentRouteSnapshot.Data data = EquipmentRouteSnapshot.parse(json(ROUTE));
        JsonObject candidates = json(CANDIDATES).getAsJsonObject("items");
        JsonObject overrides = json(OVERRIDES).getAsJsonObject("overrides");

        for (EquipmentRouteSnapshot.Entry entry : data.entries().values()) {
            if (entry.route() != EquipmentRouteSnapshot.Route.OWN_SPRITE) {
                continue;
            }
            JsonObject candidate = candidates.getAsJsonObject(entry.assetId());
            assertNotNull(candidate, entry.id() + " has no display candidate");
            assertEquals("proposal_not_game_verified", candidate.get("status").getAsString(), entry.id());
            assertFalse(candidate.get("humanAccepted").getAsBoolean(), entry.id());

            JsonObject itemOverride = overrides.has(entry.assetId())
                    ? overrides.getAsJsonObject(entry.assetId()) : null;
            JsonObject calibrated = contextsOf(itemOverride, "contexts");
            JsonObject calibratedBlocking = contextsOf(itemOverride, "blocking");

            EquipmentGeometry geometry = entry.geometry();
            Vector3f grip = new Vector3f(geometry.gripX(), geometry.gripY(), 0f);
            float scale = geometry.uniformXYScale();
            String family = candidate.get("family").getAsString();
            JsonObject orientation = candidate.getAsJsonObject("axis");
            Vector3f referenceGrip = referenceGrip(candidate.getAsJsonObject("reference"));
            assertReferenceHalfBox(entry.id(), family, candidate.getAsJsonObject("reference"));
            Map<String, Ref> reference = REFERENCES.get(family);
            assertNotNull(reference, entry.id() + " unknown reference family " + family);

            checkDisplay(entry, entry.display(), candidate.getAsJsonObject("display"),
                    reference, referenceGrip, grip, scale, orientation, entry.id() + " display", calibrated);

            if (entry.blockingDisplay() == null) {
                assertTrue(candidate.get("displayBlocking").isJsonNull(), entry.id());
                continue;
            }
            Map<String, Ref> blockingReference = BLOCKING_REFERENCES.get(family);
            assertNotNull(blockingReference, entry.id() + " has a guard pose without a blocking reference");
            checkDisplay(entry, entry.blockingDisplay(), candidate.getAsJsonObject("displayBlocking"),
                    blockingReference, referenceGrip, grip, scale, orientation, entry.id() + " blocking",
                    calibratedBlocking);
        }
    }

    @Test
    void blackBladeReversesItsEdgeWithoutReversingItsLongitudinalDirection() throws Exception {
        var entry = EquipmentRouteSnapshot.parse(json(ROUTE)).get("eldencombat:malikeths_black_blade");
        var candidate = json(CANDIDATES).getAsJsonObject("items").getAsJsonObject(entry.assetId());
        var orientation = candidate.getAsJsonObject("axis");
        assertEquals(180.0, orientation.get("heldBladeRollDegrees").getAsDouble(), 1e-6);
        // Artwork can be revised and the camera pose can be calibrated. The authored edge reversal
        // remains a half-turn around the current sprite's longitudinal axis in the derived baseline.
        double angle = Math.toRadians(orientation.get("heldBladeAxisFromXDegrees").getAsDouble());
        Vector3f along = new Vector3f((float) Math.cos(angle), (float) Math.sin(angle), 0);
        Vector3f edge = new Vector3f(-along.y, along.x, 0);
        for (String context : List.of("firstperson_righthand", "firstperson_lefthand",
                "thirdperson_righthand", "thirdperson_lefthand")) {
            boolean left = context.endsWith("lefthand");
            Matrix4f before = normalizedRotation(contextSpec(REFERENCES.get("melee"), context).matrix(left))
                    .rotateZ((float) Math.toRadians(orientation.get("appliedAxisDegrees").getAsDouble()));
            JsonObject baseline = candidate.getAsJsonObject("display").getAsJsonObject(context);
            float[] rotation = triple(baseline, "rotation");
            Matrix4f after = new Matrix4f().rotate(new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(rotation[0]),
                    (float) Math.toRadians((left ? -1 : 1) * rotation[1]),
                    (float) Math.toRadians((left ? -1 : 1) * rotation[2])));
            assertEquals(0, before.transformDirection(new Vector3f(along)).normalize()
                    .distance(after.transformDirection(new Vector3f(along)).normalize()), 1e-5, context + " tip");
            assertEquals(0, before.transformDirection(new Vector3f(edge)).normalize().negate()
                    .distance(after.transformDirection(new Vector3f(edge)).normalize()), 1e-5, context + " edge");
            assertEquals(0, before.transformDirection(new Vector3f(0, 0, 1)).normalize().negate()
                    .distance(after.transformDirection(new Vector3f(0, 0, 1)).normalize()), 1e-5, context + " face");
            assertTrue(after.determinant3x3() > 0, context + " uses a rotation, not negative UV scale");
            if (context.startsWith("thirdperson_")) {
                assertFrame(baseline, entry.display().resolve(context), context + " world pose");
            } else {
                var override = json(OVERRIDES).getAsJsonObject("overrides").getAsJsonObject(entry.assetId());
                Path nativeProject = Path.of(override.getAsJsonObject("nativeCalibration").get("project").getAsString());
                var nativeDisplay = json(nativeProject.getParent().getParent().resolve("exports")
                        .resolve(entry.assetId() + ".json")).getAsJsonObject("display");
                assertFrame(nativeDisplay.getAsJsonObject(context), entry.display().resolve(context),
                        context + " native camera calibration");
                assertTrue(entry.display().resolve(context).matrix(left).determinant3x3() > 0);
            }
        }
    }

    /**
     * One display table against one reference pose table.
     *
     * <p>For every declared context the expected transform is rebuilt from scratch —
     * {@code D = T(p).R.S} with {@code p} the reference item's own grip point, {@code R} the reference
     * rotation (its uniform scale divided out, because the contract prescribes {@code S} itself) and
     * {@code t = p - R.S.a} — and compared with the matrix the runtime actually applies, including the
     * left-hand mirror. The stored JSON numbers are compared too, so the file and the runtime cannot
     * drift apart, and each context's emitted world length is checked against the art's declared one.
     */
    private static void checkDisplay(EquipmentRouteSnapshot.Entry entry, EquipmentDisplay display,
                                     JsonObject stored, Map<String, Ref> reference, Vector3f referenceGrip,
                                     Vector3f grip, float scale, JsonObject orientation, String label,
                                     JsonObject calibrated) {
        for (String context : ALL_CONTEXTS) {
            if ("gui".equals(context)) {
                // The inventory slot is the independent gui_16/gui_32 asset with an identity pose: a
                // held canvas must never set the GUI geometry.
                assertIdentity(display.resolve(context), label + " gui");
                continue;
            }
            if (calibrated != null && calibrated.has(context)) {
                // A human calibration replaces the derivation on purpose, so it is checked as a value
                // statement instead: the shipped table must carry exactly what the user tuned.
                assertFrame(calibrated.getAsJsonObject(context), display.resolve(context),
                        label + " calibration " + context);
                continue;
            }
            Ref spec = contextSpec(reference, context);
            if (spec == null) {
                assertFalse(display.declares(context),
                        label + " invents " + context + ", which the reference pose does not define");
                continue;
            }
            assertTrue(display.declares(context), label + " does not declare " + context);
            boolean left = context.endsWith("lefthand");

            Matrix4f applied = spec.matrix(left);
            Vector3f palm = applied.transformPosition(new Vector3f(referenceGrip));
            Matrix4f rotation = normalizedRotation(applied);
            rotation.rotateZ((float) Math.toRadians(orientation.get("appliedAxisDegrees").getAsFloat()));
            if (orientation.has("heldBladeRollDegrees")
                    && (context.startsWith("firstperson_") || context.startsWith("thirdperson_"))) {
                double angle = Math.toRadians(orientation.get("heldBladeAxisFromXDegrees").getAsDouble());
                rotation.rotate((float) Math.toRadians(orientation.get("heldBladeRollDegrees").getAsFloat()),
                        (float) Math.cos(angle), (float) Math.sin(angle), 0f);
            }
            Matrix4f rotationScale = new Matrix4f(rotation).scale(scale, scale, scale);
            Matrix4f expected = new Matrix4f()
                    .translation(new Vector3f(palm).sub(rotationScale.transformDirection(new Vector3f(grip))))
                    .mul(rotationScale);

            EquipmentDisplay.Frame frame = display.resolve(context);
            assertMatrix(expected, frame.matrix(left), label + " " + context);

            JsonObject values = stored.getAsJsonObject(context);
            assertFrame(values, frame, label + " " + context + " stored");

            assertEquals(0f, palm.distance(frame.matrix(left).transformPosition(new Vector3f(grip))), EPS,
                    label + " " + context + " grip does not land on the reference palm");
            assertEquals(entry.geometry().worldLengthBlocks(),
                    frame.scale()[0] * entry.geometry().sourceSpanPixels() / entry.geometry().canvasWidth(),
                    EPS, label + " " + context + " emits the wrong world length");
        }
    }

    /**
     * One hand-computed anchor, independent of the generator: the vanilla {@code item/handheld}
     * third-person right-hand pose (rotation 0,-90,55; translation 0,4,0.5; scale 0.85) puts the
     * reference sword's grip pixel (3.5, 12.5) of a 16-pixel canvas at
     *
     * <pre>
     *   a   = (3.5/16-0.5, 0.5-12.5/16, 0)                     = (-0.28125, -0.28125, 0)
     *   R   = Rx(0).Ry(-90).Rz(55)                             (JOML rotationXYZ, verified above)
     *   R.a = (0, 0.819152.aX + 0.573576.aY, 0.573576.aX - 0.819152.aY)
     *       = (0, -0.391705, 0.069068)
     *   p   = 0.85.R.a + (0, 0.25, 0.03125)                    = (0, -0.082949, 0.089958)
     * </pre>
     *
     * The shipped great sword must land its own grip pixel on exactly that point, which is what pins
     * the convention (T.R.S, degrees, /16, RX.RY.RZ, mirrored left hand) to vanilla's own arithmetic.
     *
     * <p>It reads the <em>derived candidate</em> rather than the shipped route entry, so a user
     * calibration (which replaces that context on purpose) cannot invalidate the anchor; the shipped
     * value is checked against the calibration by
     * {@link #everyShippedDisplayReproducesItsReferencePoseOnTheItemsOwnGrip}.
     */
    @Test
    void greatSwordGripLandsOnTheHandComputedVanillaHandheldPalm() throws Exception {
        EquipmentRouteSnapshot.Data data = EquipmentRouteSnapshot.parse(json(ROUTE));
        EquipmentRouteSnapshot.Entry greatSword = data.get("eldencombat:great_sword");
        assertNotNull(greatSword);
        EquipmentGeometry geometry = greatSword.geometry();
        Vector3f grip = new Vector3f(geometry.gripX(), geometry.gripY(), 0f);
        assertEquals(-0.265625f, grip.x, 1.0e-6f, "grip pixel 7.5 of a 32-pixel canvas");
        assertEquals(-0.265625f, grip.y, 1.0e-6f, "grip pixel 24.5 of a 32-pixel canvas");

        JsonObject candidate = json(CANDIDATES).getAsJsonObject("items")
                .getAsJsonObject("great_sword").getAsJsonObject("display")
                .getAsJsonObject("thirdperson_righthand");
        float[] rotation = triple(candidate, "rotation");
        float[] translation = triple(candidate, "translation");
        EquipmentDisplay.Frame derived = new EquipmentDisplay.Frame(rotation,
                new float[] {translation[0] / 16f, translation[1] / 16f, translation[2] / 16f},
                triple(candidate, "scale"));

        Vector3f landed = derived.matrix(false).transformPosition(new Vector3f(grip));
        assertEquals(0f, landed.x, 1.0e-5f);
        assertEquals(-0.082949f, landed.y, 1.0e-5f);
        assertEquals(0.089958f, landed.z, 1.0e-5f);
    }

    // ---------------------------------------------------------------------------------------------
    // 3. units, states and the emitter rectangle
    // ---------------------------------------------------------------------------------------------

    @Test
    void geometryKeepsOneOriginOneUnitAndTheContractScale() {
        EquipmentGeometry geometry = EquipmentGeometry.of(32f, 32f, 7.5f, 24.5f, 1.584f, 37.618080759124325f);
        assertEquals(-4.25f, geometry.anchorModelUnitsX(), 1.0e-6f);
        assertEquals(-4.25f, geometry.anchorModelUnitsY(), 1.0e-6f);
        assertEquals(-0.265625f, geometry.gripX(), 1.0e-6f);
        assertEquals(-0.265625f, geometry.gripY(), 1.0e-6f);
        assertEquals(1.584f * 32f / 37.618080759124325f, geometry.uniformXYScale(), 1.0e-6f);
        assertEquals(1.584f, geometry.emittedSpanBlocks(), 1.0e-6f,
                "the emitted opaque span reproduces the declared world length");
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentGeometry.of(32f, 32f, 40f, 0f, 1f, 10f), "anchor outside the canvas");
        assertThrows(IllegalArgumentException.class,
                () -> EquipmentGeometry.of(32f, 32f, 0f, 0f, 0f, 10f), "zero world length");
    }

    @Test
    void stateFractionsUseVanillasDivisorsAndTheArtsOwnThresholds() {
        assertEquals(0.65f, EquipmentStateResolver.fraction(13, 20), 1.0e-6f, "13 ticks is stage 1");
        assertEquals(0.9f, EquipmentStateResolver.fraction(18, 20), 1.0e-6f, "18 ticks is stage 2");
        assertEquals(1f, EquipmentStateResolver.fraction(20, 20), 1.0e-6f);
        assertEquals(1f, EquipmentStateResolver.fraction(40, 20), 1.0e-6f, "clamped, never above one");
        assertEquals(0f, EquipmentStateResolver.fraction(-5, 20), 1.0e-6f);
        assertEquals(0f, EquipmentStateResolver.fraction(3, 0), 1.0e-6f, "an unknown divisor is not a crash");
        assertEquals("pulling_0",
                EquipmentStateResolver.bowStateFor(EquipmentStateResolver.fraction(20, 72000)),
                "72000 is the maximum use duration, not the full-draw time");
        assertTrue(EquipmentStateResolver.fraction(20, 72000) < 0.01f);

        assertEquals("pulling_0", EquipmentStateResolver.bowStateFor(0.649f));
        assertEquals("pulling_1", EquipmentStateResolver.bowStateFor(0.65f));
        assertEquals("pulling_2", EquipmentStateResolver.bowStateFor(0.9f));

        assertEquals("loading_0", EquipmentStateResolver.ballistaLoadingFor(0.299f));
        assertEquals("loading_1", EquipmentStateResolver.ballistaLoadingFor(0.30f));
        assertEquals("loading_2", EquipmentStateResolver.ballistaLoadingFor(0.65f));
    }

    @Test
    void spriteFaceSpansExactlyOneBlockAndReversesItsWinding() {
        ItemSpriteGeometry.Corner[] front = ItemSpriteGeometry.corners(1f, 0.1f, 0.6f, 0.2f, 0.7f);
        assertEquals(4, front.length);
        for (ItemSpriteGeometry.Corner corner : front) {
            assertEquals(0.5f, Math.abs(corner.x()), 1.0e-6f, "the quad is exactly one block wide");
            assertEquals(0.5f, Math.abs(corner.y()), 1.0e-6f, "and exactly one block tall");
        }
        assertEquals(0.1f, front[0].u(), 1.0e-6f, "the lower-left corner takes the sprite's left edge");
        assertEquals(0.7f, front[0].v(), 1.0e-6f, "and its bottom edge, because V grows downwards");
        assertEquals(0.6f, front[1].u(), 1.0e-6f);
        assertTrue(signedArea(front) > 0f, "the front face keeps the counter-clockwise winding");

        ItemSpriteGeometry.Corner[] back = ItemSpriteGeometry.corners(-1f, 0.1f, 0.6f, 0.2f, 0.7f);
        assertTrue(signedArea(back) < 0f, "the back face must be wound the other way or it is culled");
        for (ItemSpriteGeometry.Corner corner : back) {
            assertEquals(0.5f, Math.abs(corner.x()), 1.0e-6f);
            assertEquals(0.5f, Math.abs(corner.y()), 1.0e-6f);
        }
    }

    @Test
    void displayResolveMirrorsTheLeftHandAndStatesItsInheritance() {
        EquipmentDisplay.Frame right = new EquipmentDisplay.Frame(
                new float[] {0f, -90f, 55f}, new float[] {-1f, 4f, 2f}, new float[] {0.85f, 0.85f, 0.85f});
        EquipmentDisplay.Frame fixed = new EquipmentDisplay.Frame(
                new float[] {0f, 180f, 0f}, new float[] {0f, 0f, 0f}, new float[] {1f, 1f, 1f});
        EquipmentDisplay.Frame gui = EquipmentDisplay.Frame.identity();
        Map<String, EquipmentDisplay.Frame> declared = new LinkedHashMap<>();
        declared.put("thirdperson_righthand", right);
        declared.put("gui", gui);
        declared.put("fixed", fixed);
        EquipmentDisplay display = new EquipmentDisplay(declared, "fixed");

        assertSame(right, display.resolve("thirdperson_lefthand"),
                "an undeclared left hand copies the stored right hand, as ItemTransforms does");
        assertSame(fixed, display.resolve("head"), "everything else falls back to the stated inheritance");
        assertSame(gui, display.resolve("gui"));
        assertMatrix(EquipmentDisplay.Frame.identity().matrix(false),
                new EquipmentDisplay(Map.of(), "fixed").resolve("ground").matrix(false),
                "an empty table is the identity transform, never a guess");

        Matrix4f mirror = new Matrix4f().scale(-1f, 1f, 1f);
        Matrix4f expected = new Matrix4f(mirror).mul(right.matrix(false)).mul(mirror);
        assertMatrix(expected, right.matrix(true), "the left hand is the mirrored right hand, not a flip");

        assertThrows(IllegalArgumentException.class, () -> new EquipmentDisplay.Frame(
                new float[] {0f, 0f}, new float[] {0f, 0f, 0f}, new float[] {1f, 1f, 1f}));
        assertThrows(IllegalArgumentException.class, () -> new EquipmentDisplay.Frame(
                new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f}, new float[] {1f, 0f, 1f}));
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private static float[] triple(JsonObject object, String key) {
        JsonArray array = object.getAsJsonArray(key);
        assertEquals(3, array.size(), key + " must hold three numbers");
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    /** The stored JSON triple of one context, in the runtime's own units (translation already /16). */
    private static void assertFrame(JsonObject values, EquipmentDisplay.Frame frame, String label) {
        for (int index = 0; index < 3; index++) {
            assertEquals(values.getAsJsonArray("rotation").get(index).getAsFloat(),
                    frame.rotation()[index], EPS, label + " rotation");
            assertEquals(values.getAsJsonArray("translation").get(index).getAsFloat() / 16f,
                    frame.translation()[index], EPS, label + " translation");
            assertEquals(values.getAsJsonArray("scale").get(index).getAsFloat(),
                    frame.scale()[index], EPS, label + " scale");
        }
    }

    /** The contexts an override file replaces, or {@code null} when the item has no calibration. */
    private static JsonObject contextsOf(JsonObject itemOverride, String key) {
        if (itemOverride == null || !itemOverride.has(key)) {
            return null;
        }
        return itemOverride.getAsJsonObject(key);
    }

    private static void assertIdentity(EquipmentDisplay.Frame frame, String label) {
        assertArrayEquals(new float[] {0f, 0f, 0f}, frame.rotation(), EPS, label + " rotation");
        assertArrayEquals(new float[] {0f, 0f, 0f}, frame.translation(), EPS, label + " translation");
        assertArrayEquals(new float[] {1f, 1f, 1f}, frame.scale(), EPS, label + " scale");
    }

    private static void assertMatrix(Matrix4f expected, Matrix4f actual, String label) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                assertEquals(expected.get(column, row), actual.get(column, row), EPS,
                        label + " element " + column + "," + row);
            }
        }
    }

    /**
     * The reference pose's rotation with its own uniform scale divided out.
     *
     * <p>The contract prescribes the emitted scale itself ({@code s = worldLengthBlocks*W/sourceSpan}),
     * so the reference's 0.85/0.68 must not survive into {@code R}: keeping it would emit
     * {@code refScale*s} and make the weapon shorter than the art declares. {@code p} does keep the
     * reference scale, because p is where the reference item's grip actually lands.
     */
    private static Matrix4f normalizedRotation(Matrix4f applied) {
        Matrix4f rotation = new Matrix4f(applied).setTranslation(0f, 0f, 0f);
        Vector4f column = new Vector4f();
        for (int index = 0; index < 3; index++) {
            rotation.getColumn(index, column);
            float length = new Vector3f(column.x, column.y, column.z).length();
            rotation.setColumn(index, column.div(length));
        }
        return rotation;
    }

    private static float maxDiff(Matrix4f left, Matrix4f right) {        float worst = 0f;
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                worst = Math.max(worst, Math.abs(left.get(column, row) - right.get(column, row)));
            }
        }
        return worst;
    }

    private static float signedArea(ItemSpriteGeometry.Corner[] corners) {
        float area = 0f;
        for (int index = 0; index < corners.length; index++) {
            ItemSpriteGeometry.Corner current = corners[index];
            ItemSpriteGeometry.Corner next = corners[(index + 1) % corners.length];
            area += current.x() * next.y() - next.x() * current.y();
        }
        return area / 2f;
    }

    /**
     * The one datum that is not derivable from the art: whether the reference model is authored in the
     * [0,1] icon box or around its own origin.
     *
     * <p>A shield is the latter, and the vanilla chain then puts the plate centre half a box away from
     * the display point on every axis; a box reference is the former and declares zero. Pinned here so a
     * silent edit cannot put the shield back where the bug report found it (floating beside the hand).
     */
    private static void assertReferenceHalfBox(String id, String family, JsonObject reference) {
        JsonArray offset = reference.has("gripOffsetBlocks")
                ? reference.getAsJsonArray("gripOffsetBlocks") : new JsonArray();
        float x = offset.size() == 3 ? offset.get(0).getAsFloat() : 0f;
        float y = offset.size() == 3 ? offset.get(1).getAsFloat() : 0f;
        float z = offset.size() == 3 ? offset.get(2).getAsFloat() : 0f;
        if ("shield".equals(family)) {
            assertEquals(-0.5f, x, 1.0e-6f, id + " shield half box x");
            assertEquals(-0.5f, y, 1.0e-6f, id + " shield half box y");
            assertEquals(-0.5f, z, 1.0e-6f, id + " shield half box z");
        } else {
            assertEquals(0f, x, 1.0e-6f, id + " box reference declares no half-box offset");
            assertEquals(0f, y, 1.0e-6f, id);
            assertEquals(0f, z, 1.0e-6f, id);
        }
    }

    private static Vector3f referenceGrip(JsonObject reference) {
        JsonArray pixels = reference.getAsJsonArray("anchorPixels");
        Vector3f grip = new Vector3f(EquipmentGeometry.centredX(pixels.get(0).getAsFloat(), 16f),
                EquipmentGeometry.centredY(pixels.get(1).getAsFloat(), 16f), 0f);
        // `gripOffsetBlocks` is the half box ItemRenderer shifts every item by, expressed *inside* the
        // display frame. A box reference (item/generated, item/handheld, item/bow, item/crossbow) is
        // authored in the [0,1] icon box and the centred grip above already carries it, so its offset is
        // zero; a builtin/entity reference (the shield) authors its geometry around the model origin, so
        // the point that ends up under the fist is the origin minus that half box. Getting this wrong in
        // either direction is the "shield floats next to the hand" defect, which is why it is re-derived
        // here instead of being copied from the shipped display.
        if (reference.has("gripOffsetBlocks") && !reference.get("gripOffsetBlocks").isJsonNull()) {
            JsonArray offset = reference.getAsJsonArray("gripOffsetBlocks");
            grip.add(offset.get(0).getAsFloat(), offset.get(1).getAsFloat(), offset.get(2).getAsFloat());
        }
        return grip;
    }

    private static Ref contextSpec(Map<String, Ref> display, String context) {
        Ref direct = display.get(context);
        if (direct != null) {
            return direct;
        }
        if (context.endsWith("_lefthand")) {
            return display.get(context.substring(0, context.length() - "_lefthand".length()) + "_righthand");
        }
        return null;
    }

    /** One vanilla display entry, exactly as {@code ItemTransform.apply} consumes it. */
    private record Ref(float[] rotation, float[] translation, float[] scale) {

        static Ref of(float rx, float ry, float rz, float tx, float ty, float tz, float uniform) {
            return new Ref(new float[] {rx, ry, rz}, new float[] {tx, ty, tz},
                    new float[] {uniform, uniform, uniform});
        }

        Matrix4f matrix(boolean leftHanded) {
            float sign = leftHanded ? -1f : 1f;
            return new Matrix4f()
                    .translate(sign * translation[0] / 16f, translation[1] / 16f, translation[2] / 16f)
                    .rotate(new Quaternionf().rotationXYZ((float) Math.toRadians(rotation[0]),
                            (float) Math.toRadians(sign * rotation[1]),
                            (float) Math.toRadians(sign * rotation[2])))
                    .scale(scale[0], scale[1], scale[2]);
        }
    }

    private static Map<String, Ref> merge(Map<String, Ref> base, Map<String, Ref> override) {
        Map<String, Ref> merged = new LinkedHashMap<>(base);
        merged.putAll(override);
        return Map.copyOf(merged);
    }

    // ---------------------------------------------------------------------------------------------
    // Reference display blocks, copied verbatim from the vanilla resource pack in
    // build/moddev/artifacts/neoforge-21.1.219-client-extra-aka-minecraft-resources.jar.
    // ---------------------------------------------------------------------------------------------

    private static final Map<String, Ref> GENERATED = Map.of(
            "thirdperson_righthand", Ref.of(0f, 0f, 0f, 0f, 3f, 1f, 0.55f),
            "firstperson_righthand", Ref.of(0f, -90f, 25f, 1.13f, 3.2f, 1.13f, 0.68f),
            "ground", Ref.of(0f, 0f, 0f, 0f, 2f, 0f, 0.5f),
            "head", Ref.of(0f, 180f, 0f, 0f, 13f, 7f, 1f),
            "fixed", Ref.of(0f, 180f, 0f, 0f, 0f, 0f, 1f));

    private static final Map<String, Ref> HANDHELD = Map.of(
            "thirdperson_righthand", Ref.of(0f, -90f, 55f, 0f, 4f, 0.5f, 0.85f),
            "thirdperson_lefthand", Ref.of(0f, 90f, -55f, 0f, 4f, 0.5f, 0.85f),
            "firstperson_righthand", Ref.of(0f, -90f, 25f, 1.13f, 3.2f, 1.13f, 0.68f),
            "firstperson_lefthand", Ref.of(0f, 90f, -25f, 1.13f, 3.2f, 1.13f, 0.68f));

    private static final Map<String, Ref> BOW = Map.of(
            "thirdperson_righthand", Ref.of(-80f, 260f, -40f, -1f, -2f, 2.5f, 0.9f),
            "thirdperson_lefthand", Ref.of(-80f, -280f, 40f, -1f, -2f, 2.5f, 0.9f),
            "firstperson_righthand", Ref.of(0f, -90f, 25f, 1.13f, 3.2f, 1.13f, 0.68f),
            "firstperson_lefthand", Ref.of(0f, 90f, -25f, 1.13f, 3.2f, 1.13f, 0.68f));

    private static final Map<String, Ref> CROSSBOW = Map.of(
            "thirdperson_righthand", Ref.of(-90f, 0f, -60f, 2f, 0.1f, -3f, 0.9f),
            "thirdperson_lefthand", Ref.of(-90f, 0f, 30f, 2f, 0.1f, -3f, 0.9f),
            "firstperson_righthand", Ref.of(-90f, 0f, -55f, 1.13f, 3.2f, 1.13f, 0.68f),
            "firstperson_lefthand", Ref.of(-90f, 0f, 35f, 1.13f, 3.2f, 1.13f, 0.68f));

    private static final Map<String, Ref> SHIELD = Map.of(
            "thirdperson_righthand", Ref.of(0f, 90f, 0f, 10f, 6f, -4f, 1f),
            "thirdperson_lefthand", Ref.of(0f, 90f, 0f, 10f, 6f, 12f, 1f),
            "firstperson_righthand", Ref.of(0f, 180f, 5f, -10f, 2f, -10f, 1.25f),
            "firstperson_lefthand", Ref.of(0f, 180f, 5f, 10f, 0f, -10f, 1.25f),
            "ground", Ref.of(0f, 0f, 0f, 2f, 4f, 2f, 0.25f),
            "fixed", Ref.of(0f, 180f, 0f, -4.5f, 4.5f, -5f, 0.55f));

    private static final Map<String, Ref> SHIELD_BLOCKING = Map.of(
            "thirdperson_righthand", Ref.of(45f, 155f, 0f, -3.49f, 11f, -2f, 1f),
            "thirdperson_lefthand", Ref.of(45f, 155f, 0f, 11.51f, 7f, 2.5f, 1f),
            "firstperson_righthand", Ref.of(0f, 180f, -5f, -15f, 5f, -11f, 1.25f),
            "firstperson_lefthand", Ref.of(0f, 180f, -5f, 5f, 5f, -11f, 1.25f));

    private static final Map<String, Map<String, Ref>> REFERENCES = Map.of(
            "melee", merge(GENERATED, HANDHELD),
            "bow", merge(GENERATED, BOW),
            "crossbow", merge(GENERATED, CROSSBOW),
            "shield", merge(GENERATED, SHIELD),
            "flask", GENERATED);

    private static final Map<String, Map<String, Ref>> BLOCKING_REFERENCES =
            Map.of("shield", merge(SHIELD, SHIELD_BLOCKING));
}
