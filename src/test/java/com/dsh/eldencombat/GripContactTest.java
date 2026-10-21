package com.dsh.eldencombat;

import com.dsh.eldencombat.client.equipment2d.EquipmentDisplay;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GripContactTest {
    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
    }

    private static float[] triple(JsonObject frame, String field, float divisor) {
        var values = frame.getAsJsonArray(field);
        return new float[]{values.get(0).getAsFloat() / divisor,
                values.get(1).getAsFloat() / divisor, values.get(2).getAsFloat() / divisor};
    }

    @Test void crossbowAndFlaskGripStayInBothPalms() throws Exception {
        var assets = read("art/equipment-2d/blockbench-all-items-20261018/manifest.json").getAsJsonArray("assets");
        var routes = read("src/main/resources/assets/eldencombat/routes/2d.json").getAsJsonArray("equipment");
        for (String id : new String[]{"ballista", "test_flask"}) {
            JsonObject asset = null, route = null;
            for (var e : assets) if (e.getAsJsonObject().get("id").getAsString().equals(id)) asset = e.getAsJsonObject();
            for (var e : routes) if (e.getAsJsonObject().has("assetId") && e.getAsJsonObject().get("assetId").getAsString().equals(id)) route = e.getAsJsonObject();
            var anchor = asset.getAsJsonArray("anchorPixels");
            var size = asset.getAsJsonArray("variants").get(0).getAsJsonObject().getAsJsonArray("size");
            var grip = new Vector3f(anchor.get(0).getAsFloat() / size.get(0).getAsFloat() - .5f,
                    .5f - anchor.get(1).getAsFloat() / size.get(1).getAsFloat(), 0);
            for (boolean left : new boolean[]{false, true}) {
                float sign = left ? -1 : 1;
                var data = route.getAsJsonObject("display").getAsJsonObject("thirdperson_" + (left ? "lefthand" : "righthand"));
                var frame = new EquipmentDisplay.Frame(triple(data, "rotation", 1), triple(data, "translation", 16), triple(data, "scale", 1));
                // ItemInHandLayer, measured in arm-local coordinates: palm centre = (-sign, 8, 0).
                var hand = new Matrix4f().rotateX(-(float) Math.PI / 2).rotateY((float) Math.PI)
                        .translate(sign / 16, 2f / 16, -10f / 16).mul(frame.matrix(left));
                var actual = hand.transformPosition(new Vector3f(grip)).mul(16);
                assertEquals(0, actual.distance(new Vector3f(-sign, 8, 0)), .001, id + ": left=" + left);
            }
        }
    }
}
