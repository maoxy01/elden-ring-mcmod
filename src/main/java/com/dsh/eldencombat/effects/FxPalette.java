package com.dsh.eldencombat.effects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure color data; no Minecraft/client classes, also usable by the preview exporter. */
public final class FxPalette {
    public record Data(Map<String, Integer> colors, boolean slashSprites) {
        public Data {
            colors = Map.copyOf(colors);
            for (int value : colors.values()) if (value < 0 || value > 0xffffff) throw new IllegalArgumentException("FX RGB range");
        }
        public int color(String reference) {
            Integer value = colors.get(reference);
            if (value == null) throw new IllegalArgumentException("Unknown FX color: " + reference);
            return value;
        }
    }
    private static final Data DEFAULTS = bundled();
    private static volatile Data current = DEFAULTS;
    private static volatile long revision;
    private FxPalette() {}
    public static Data current() { return current; }
    public static int color(String reference) { return current.color(reference); }
    public static long revision() { return revision; }
    public static void apply(Data data) {
        validate(data);
        current = data;
        revision++;
    }
    public static void validate(Data data) {
        for (String key : DEFAULTS.colors.keySet()) data.color(key);
    }
    public static Data parse(JsonObject root) {
        if (root.get("schemaVersion").getAsInt() != 1) throw new IllegalArgumentException("FX palette schema");
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("colors").entrySet()) {
            if (!entry.getKey().matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("FX color ID");
            var ramp = entry.getValue().getAsJsonObject();
            for (String tier : new String[]{"highlight", "mid", "shadow"}) {
                String hex = ramp.get(tier).getAsString();
                if (!hex.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("FX color: " + hex);
                colors.put(entry.getKey() + "." + tier, Integer.parseInt(hex.substring(1), 16));
            }
        }
        if (root.has("slashSprites") && (!root.get("slashSprites").isJsonPrimitive()
                || !root.get("slashSprites").getAsJsonPrimitive().isBoolean())) throw new IllegalArgumentException("slashSprites must be boolean");
        return new Data(colors, !root.has("slashSprites") || root.get("slashSprites").getAsBoolean());
    }
    private static Data bundled() {
        try (var stream = FxPalette.class.getResourceAsStream("/assets/eldencombat/fx/palette.json")) {
            if (stream == null) throw new IllegalStateException("Missing bundled FX palette");
            return parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException error) { throw new IllegalStateException("FX palette", error); }
    }
}
