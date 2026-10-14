package com.dsh.eldencombat.server;

import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class PlayerAppearance {
    private PlayerAppearance() {}
    public static boolean slim(ServerPlayer player) {
        try {
            for (var property : player.getGameProfile().getProperties().get("textures")) {
                var root = JsonParser.parseString(new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8)).getAsJsonObject();
                var textures = root.getAsJsonObject("textures");
                if (textures != null && textures.has("SKIN")) {
                    var skin = textures.getAsJsonObject("SKIN");
                    return skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                        && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
                }
            }
        } catch (RuntimeException ignored) { }
        // Matches the pinned 1.21.1 offline default: 9 slim, then 9 wide skins.
        return Math.floorMod(player.getUUID().hashCode(), 18) < 9;
    }
}
