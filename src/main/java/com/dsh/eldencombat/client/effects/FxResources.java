package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.effects.FxPalette;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class FxResources {
    private FxResources() {}
    public static FxPalette.Data prepare(ResourceManager manager) {
        var path = ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "fx/palette.json");
        try (var stream = manager.getResourceOrThrow(path).open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var data = FxPalette.parse(JsonParser.parseReader(reader).getAsJsonObject());
            FxPalette.validate(data);
            return data;
        } catch (Exception error) {
            EldenCombat.LOGGER.error("[skill-fx] Invalid palette; keeping the last valid colors", error);
            return FxPalette.current();
        }
    }
}
