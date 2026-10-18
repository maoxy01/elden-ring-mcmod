package com.dsh.eldencombat.client.equipment2d;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Checks own sprite resources before a baked missing-model placeholder can take over the draw. */
public final class EquipmentModelResources {
    private EquipmentModelResources() {
    }

    public static boolean complete(ResourceManager manager, ResourceLocation model) {
        try {
            Map<String, String> textures = new HashMap<>();
            if (!readModel(manager, model, textures, new HashSet<>())) return false;
            for (String value : textures.values()) {
                Set<String> visited = new HashSet<>();
                while (value != null && value.startsWith("#")) {
                    if (!visited.add(value)) return false;
                    value = textures.get(value.substring(1));
                }
                if (value == null) return false;
                ResourceLocation texture = ResourceLocation.parse(value);
                if (manager.getResource(ResourceLocation.fromNamespaceAndPath(texture.getNamespace(),
                        "textures/" + texture.getPath() + ".png")).isEmpty()) return false;
            }
            return true;
        } catch (Exception failure) {
            return false;
        }
    }

    private static boolean readModel(ResourceManager manager, ResourceLocation model,
                                     Map<String, String> textures, Set<ResourceLocation> parents) throws Exception {
        if (!parents.add(model)) return false;
        if ("minecraft".equals(model.getNamespace()) && "builtin/generated".equals(model.getPath())) {
            return true;
        }
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(model.getNamespace(),
                "models/" + model.getPath() + ".json");
        JsonObject json;
        try (Reader reader = manager.getResourceOrThrow(file).openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (json.has("parent") && !readModel(manager,
                ResourceLocation.parse(json.get("parent").getAsString()), textures, parents)) return false;
        JsonObject ownTextures = json.getAsJsonObject("textures");
        if (ownTextures != null) {
            for (Map.Entry<String, JsonElement> entry : ownTextures.entrySet()) {
                textures.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return true;
    }
}
