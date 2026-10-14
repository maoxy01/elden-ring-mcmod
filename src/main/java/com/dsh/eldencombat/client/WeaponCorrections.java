package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 武器手持/展示姿态校正，数据来自 {@code assets/eldencombat/item_skins/<id>.json} 的
 * {@code eldencombat} 字段。
 *
 * <p>为什么保留这套数据：精细武器的网格是按真实比例建模的，GUI、掉落物、物品展示框、
 * 第一人称和第三人称共用同一条 BEWLR 路径，但不同武器想要的手持角度并不一致
 * （长剑平举、法杖竖握、巨锤斜扛）。把这些角度放进资源包，改姿势**不用重新编译**。
 *
 * <p>字段含义（都在"格"空间里，就是游戏里看到的方向）：
 * <pre>
 * {"eldencombat": {"translation": [0,0,0], "rotation": [0,0,0], "scale": 1}}
 * </pre>
 * {@code translation} 单位是格，{@code rotation} 单位是度、按 Z→Y→X 依次施加，
 * {@code scale} 支持单值或三值。
 */
public final class WeaponCorrections {

    public static final String DIRECTORY = "item_skins";

    /** 一条校正；全 0 / 1 时表示不需要校正。 */
    public record Entry(float[] translation, float[] rotation, float[] scale) {

        public static final Entry IDENTITY = new Entry(new float[]{0, 0, 0}, new float[]{0, 0, 0}, new float[]{1, 1, 1});

        public boolean isIdentity() {
            for (int i = 0; i < 3; i++) {
                if (translation[i] != 0 || rotation[i] != 0 || scale[i] != 1) {
                    return false;
                }
            }
            return true;
        }

        /** 施加到 pose 上：平移 -> 旋转(Z→Y→X) -> 缩放。 */
        public void apply(PoseStack pose) {
            pose.translate(translation[0], translation[1], translation[2]);
            if (rotation[2] != 0) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation[2]));
            if (rotation[1] != 0) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotation[1]));
            if (rotation[0] != 0) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotation[0]));
            if (scale[0] != 1 || scale[1] != 1 || scale[2] != 1) pose.scale(scale[0], scale[1], scale[2]);
        }
    }

    private static volatile Map<String, Entry> loaded = Map.of();

    private WeaponCorrections() {
    }

    public static Entry get(String weaponId) {
        return loaded.getOrDefault(weaponId, Entry.IDENTITY);
    }

    public static Map<String, Entry> all() {
        return loaded;
    }

    public static void apply(Map<String, Entry> prepared) {
        loaded = Map.copyOf(prepared);
        EldenCombat.LOGGER.info("[EldenCombat] 武器姿态校正就绪：{} 条", loaded.size());
    }

    public static Map<String, Entry> prepare(ResourceManager manager) {
        Map<String, Entry> map = new HashMap<>();
        var found = manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            try (var reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("eldencombat")) {
                    continue;
                }
                Entry parsed = parse(root.getAsJsonObject("eldencombat"));
                if (!parsed.isIdentity()) {
                    map.put(id, parsed);
                }
            } catch (IOException | RuntimeException error) {
                EldenCombat.LOGGER.error("[EldenCombat] 武器姿态校正解析失败 {}", entry.getKey(), error);
            }
        }
        return Map.copyOf(map);
    }

    private static Entry parse(JsonObject json) {
        return new Entry(vec(json, "translation", 0f), vec(json, "rotation", 0f), scale(json));
    }

    private static float[] vec(JsonObject json, String key, float fallback) {
        float[] out = new float[]{fallback, fallback, fallback};
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (int i = 0; i < Math.min(3, array.size()); i++) {
                out[i] = array.get(i).getAsFloat();
            }
        }
        return out;
    }

    private static float[] scale(JsonObject json) {
        if (json.has("scale")) {
            if (json.get("scale").isJsonPrimitive()) {
                float s = json.get("scale").getAsFloat();
                return new float[]{s, s, s};
            }
            JsonArray array = json.getAsJsonArray("scale");
            if (array.size() == 1) {
                float s = array.get(0).getAsFloat();
                return new float[]{s, s, s};
            }
            return vec(json, "scale", 1f);
        }
        return new float[]{1f, 1f, 1f};
    }
}
