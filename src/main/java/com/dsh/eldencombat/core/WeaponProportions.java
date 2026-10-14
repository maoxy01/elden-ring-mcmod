package com.dsh.eldencombat.core;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/** Authored ratios remain per item; icon fitting never determines held weapon size. */
public final class WeaponProportions {
    // HumanoidModel: head 8 + torso 12 + legs 12 pixels. The player root scale
    // (0.9375) also scales held items, so it cancels from the weapon/player ratio.
    public static final float PLAYER_MODEL_HEIGHT = 32f / 16f;

    public record Profile(float ratioToPlayer, float referencePlayerHeight, float gripX, float gripY, float gripZ) {
        public float heldScale(float modelHeight, boolean firstPerson, float displayScale) {
            float playerHeight = firstPerson ? referencePlayerHeight : PLAYER_MODEL_HEIGHT;
            return ratioToPlayer * playerHeight / Math.max(.001f, modelHeight)
                    / Math.max(.001f, Math.abs(displayScale));
        }
    }

    private WeaponProportions() {}

    public record Grip(String modelSha256, float x, float y, float z) {}

    public static Map<String, Grip> parseGrips(JsonObject root) {
        Map<String, Grip> result = new HashMap<>();
        for (var value : root.getAsJsonArray("entries")) {
            var entry = value.getAsJsonObject();
            String id = entry.get("id").getAsString();
            var center = entry.getAsJsonArray(entry.has("handleCenterModelUnits")?"handleCenterModelUnits":"gripCenterModelUnits");
            if (entry.get("unitsPerBlock").getAsInt()!=16 || !id.matches("eldencombat:[a-z0-9_]+")
                    || center.size()!=3) throw new IllegalArgumentException("Invalid handle region: "+id);
            result.put(id.substring("eldencombat:".length()), new Grip(entry.get(entry.has("modelSha256")?"modelSha256":"sha256").getAsString(),
                    center.get(0).getAsFloat()/16,center.get(1).getAsFloat()/16,center.get(2).getAsFloat()/16));
        }
        return Map.copyOf(result);
    }

    public static Map<String, Profile> parse(JsonObject root) {
        if (root.get("geometryScaleBaked").getAsBoolean())
            throw new IllegalArgumentException("Proportions require unscaled source geometry");
        float height = root.get("referencePlayerHeightBlocks").getAsFloat();
        if (!Float.isFinite(height) || height <= 0) throw new IllegalArgumentException("Invalid player height");
        Map<String, Profile> result = new HashMap<>();
        for (var value : root.getAsJsonArray("entries")) {
            var entry = value.getAsJsonObject();
            String id = entry.get("id").getAsString();
            float ratio = entry.get("ratioToPlayer").getAsFloat();
            var grip = entry.getAsJsonArray("mainGripModelUnits");
            if (grip == null || grip.size() != 3) throw new IllegalArgumentException("Missing grip: " + id);
            float x=grip.get(0).getAsFloat()/16, y=grip.get(1).getAsFloat()/16, z=grip.get(2).getAsFloat()/16;
            if (!id.matches("eldencombat:[a-z0-9_]+") || !Float.isFinite(ratio) || ratio <= 0
                    || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || result.putIfAbsent(id.substring("eldencombat:".length()), new Profile(ratio, height, x, y, z)) != null)
                throw new IllegalArgumentException("Invalid or duplicate proportion: " + id);
        }
        return Map.copyOf(result);
    }
}
