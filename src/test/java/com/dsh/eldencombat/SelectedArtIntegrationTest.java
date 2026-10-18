package com.dsh.eldencombat;

import com.dsh.eldencombat.client.equipment2d.ItemThickness;
import com.dsh.eldencombat.client.grace.AshUi;
import com.dsh.eldencombat.network.GraceMenuActionPayload;
import com.dsh.eldencombat.network.GraceMenuStatePayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SelectedArtIntegrationTest {
    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
    }

    @Test
    void normalizationKeepsTheMidplaneAndXyWhileMatchingDiamondSwordThickness() {
        for (float xy : new float[]{.4f, 1f, 3.2f}) {
            for (float referenceZ : new float[]{.5f, .68f, .85f, 1f}) {
                var pose = new PoseStack();
                pose.translate(.3, -.2, .7);
                pose.mulPose(new org.joml.Quaternionf().rotationXYZ(.4f, .9f, .2f));
                pose.scale(xy, xy, xy);
                Vector3f before = pose.last().pose().transformPosition(new Vector3f(.27f, .32f, .5f));
                var depth = new ItemThickness.Depth(7.5f / 16, 8.5f / 16);
                ItemThickness.normalize(pose, depth, ItemThickness.BASE * referenceZ);
                Vector3f after = pose.last().pose().transformPosition(new Vector3f(.27f, .32f, .5f));
                assertEquals(0, before.distance(after), 1e-6);
                Vector3f front = pose.last().pose().transformPosition(new Vector3f(.27f, .32f, depth.min()));
                Vector3f back = pose.last().pose().transformPosition(new Vector3f(.27f, .32f, depth.max()));
                assertEquals(ItemThickness.BASE * referenceZ, front.distance(back), 1e-6);
                assertEquals(xy, pose.last().pose().transformDirection(new Vector3f(1, 0, 0)).length(), 1e-6);
            }
        }
    }

    @Test
    void shippedShieldModelsHaveTwoMaterialsAndOnlyExposedPixelContourWalls() throws Exception {
        for (String shield : new String[]{"shield", "small_shield", "great_shield"}) {
            var image = ImageIO.read(Path.of("art/equipment-2d/full/sprites/" + shield + "_front.png").toFile());
            var model = json("build/resources/main/assets/eldencombat/models/item/2d/" + shield + "_front.json");
            assertEquals("eldencombat:item/2d/" + shield + "_back", model.getAsJsonObject("textures").get("back").getAsString());
            int pixels = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if (opaque(image, x, y)) pixels++;
            }
            assertEquals(pixels, model.getAsJsonArray("elements").size());
            for (var element : model.getAsJsonArray("elements")) {
                var cube = element.getAsJsonObject();
                var from = cube.getAsJsonArray("from");
                var to = cube.getAsJsonArray("to");
                assertEquals(7.5, from.get(2).getAsDouble());
                assertEquals(8.5, to.get(2).getAsDouble());
                int x = Math.round(from.get(0).getAsFloat() * image.getWidth() / 16);
                int y = Math.round((16 - to.get(1).getAsFloat()) * image.getHeight() / 16);
                assertTrue(opaque(image, x, y));
                var faces = cube.getAsJsonObject("faces");
                assertEquals("#front", faces.getAsJsonObject("south").get("texture").getAsString());
                assertEquals("#back", faces.getAsJsonObject("north").get("texture").getAsString());
                assertEquals(!opaque(image, x - 1, y), faces.has("west"));
                assertEquals(!opaque(image, x + 1, y), faces.has("east"));
                assertEquals(!opaque(image, x, y - 1), faces.has("up"));
                assertEquals(!opaque(image, x, y + 1), faces.has("down"));
            }
        }
    }

    @Test
    void generatedResourcesMatchTheNewSelectionsAndUiMasters() throws Exception {
        var whitelist = json("art/equipment-2d/full/publishable-assets.json");
        Set<String> selected = Set.of("flail", "heavy_thrusting_sword", "hand_of_malenia",
                "malikeths_black_blade", "starscourge_greatsword");
        int images = 0;
        for (var value : whitelist.getAsJsonArray("include")) {
            var asset = value.getAsJsonObject();
            String name = Path.of(asset.get("file").getAsString()).getFileName().toString();
            if (selected.stream().noneMatch(id -> name.startsWith(id + "_"))) continue;
            String target = "build/resources/main/assets/eldencombat/textures/item/2d/" + name
                    .replace("_gui_16", "_gui16").replace("_gui_32", "_gui32");
            assertEquals(asset.get("sha256").getAsString(), hash(Path.of(target)));
            images++;
        }
        assertEquals(15, images);
        var ui = json("art/ui-pixel/assets.json");
        for (var value : ui.getAsJsonArray("assets")) {
            var asset = value.getAsJsonObject();
            String target = "build/resources/main/assets/eldencombat/" + asset.get("suggestedResource")
                    .getAsString().split(":", 2)[1];
            assertEquals(asset.get("sha256").getAsString(), hash(Path.of(target)));
        }
        assertEquals("daefa157e7bb37dcd89bbb2e54863bda31b1bdec948d4a71c2d80d7b1a18dcf0",
                hash(Path.of("art/equipment-2d/full/display-overrides.json")));
    }

    @Test
    void ashButtonsUseServerAvailabilityAndReplacementStateInsteadOfIdPrefixes() {
        var ash = new GraceMenuStatePayload.Ash("eldencombat:spinning_slash", true, true, false, true);
        assertEquals("ash.eldencombat.spinning_slash", AshUi.nameKey(ash.id()));
        assertTrue(AshUi.canApply(ash, ""));
        assertFalse(AshUi.canApply(ash, "忙碌"));
        assertFalse(AshUi.canApply(new GraceMenuStatePayload.Ash(ash.id(), false, true, false, true), ""));
        assertFalse(AshUi.canApply(new GraceMenuStatePayload.Ash(ash.id(), true, false, false, true), ""));
        assertFalse(AshUi.canApply(new GraceMenuStatePayload.Ash(ash.id(), true, true, false, false), ""));
        assertFalse(AshUi.canApply(new GraceMenuStatePayload.Ash(ash.id(), true, true, true, true), ""));
        assertEquals(GraceMenuActionPayload.BIND, AshUi.action(""));
        assertEquals(GraceMenuActionPayload.REPLACE, AshUi.action("eldencombat:heavy_thrust"));
        assertEquals("ash.othermod.spinning_slash", AshUi.nameKey("othermod:spinning_slash"));
    }

    private static boolean opaque(java.awt.image.BufferedImage image, int x, int y) {
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()
                && (image.getRGB(x, y) >>> 24) != 0;
    }

    private static String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
