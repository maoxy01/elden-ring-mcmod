package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.registry.ModItems;
import com.dsh.eldencombat.core.geom.Box;
import com.dsh.eldencombat.core.WeaponProportions;
import com.dsh.eldencombat.skeleton.HitVolumes;
import com.dsh.eldencombat.skeleton.ModelData;
import com.dsh.eldencombat.skeleton.SkeletonPose;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Client-only resource-pack-aware cache, prepared off-thread and replaced on resource reload. */
public final class WeaponAssets {
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "textures/item/weapons.png");
    public static final ResourceLocation REMEMBRANCE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "textures/item/remembrance.png");

    public record Entry(ModelData model, WeaponMesh mesh, ResourceLocation texture, Box bounds,
                        WeaponProportions.Profile proportion) {}
    private static volatile Map<String, Entry> loaded = Map.of();

    private WeaponAssets() {}
    public static Map<String, Entry> get() { return loaded; }
    public static Entry entry(String weaponId) { return loaded.get(weaponId); }

    public static Entry entryFor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return EldenCombat.MODID.equals(key.getNamespace()) ? entry(key.getPath()) : null;
    }

    public static ModelData modelFor(ItemStack stack) {
        Entry entry = entryFor(stack);
        return entry == null ? null : entry.model();
    }

    public static void apply(Map<String, Entry> prepared) {
        loaded = Map.copyOf(prepared);
        EldenCombat.LOGGER.info("[EldenCombat] Weapon resource cache ready: {} entries", loaded.size());
    }

    public static Map<String, Entry> prepare(ResourceManager manager) {
        Map<String, Entry> map = new HashMap<>();
        for (String id : ModItems.REMEMBRANCE_IDS) loadInto(manager, map, id, "remembrance/", REMEMBRANCE_TEXTURE);
        for (String id : ModItems.WEAPON_IDS) loadInto(manager, map, id, "", TEXTURE);
        try {
            var root = read(manager, "weapons/proportions.json");
            if (root == null) throw new IllegalArgumentException("Missing per-item proportions");
            var profiles = WeaponProportions.parse(root);
            map.replaceAll((id, entry) -> new Entry(entry.model(), entry.mesh(), entry.texture(), entry.bounds(), profiles.get(id)));
            for(String regionPath : new String[]{"weapons/sword_grip_regions.json","weapons/utility_grip_regions.json"}) {
            var regions = read(manager,regionPath);
            if(regions!=null) for(var grip : WeaponProportions.parseGrips(regions).entrySet()) {
                var entry=map.get(grip.getKey());
                if(entry==null || entry.proportion()==null) continue;
                var path=ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID,"skeletons/items/"+grip.getKey()+".model.json");
                try(var stream=manager.getResource(path).orElseThrow().open()) {
                    String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                    if(!hash.equals(grip.getValue().modelSha256())) {
                        EldenCombat.LOGGER.error("[EldenCombat] Stale handle region for {}; keeping profile anchor",grip.getKey());
                        continue;
                    }
                }
                var p=entry.proportion(); var g=grip.getValue();
                map.put(grip.getKey(),new Entry(entry.model(),entry.mesh(),entry.texture(),entry.bounds(),
                        new WeaponProportions.Profile(p.ratioToPlayer(),p.referencePlayerHeight(),g.x(),g.y(),g.z())));
            }
            }
        } catch (IOException | RuntimeException | java.security.NoSuchAlgorithmException error) {
            EldenCombat.LOGGER.error("[EldenCombat] Invalid proportions; keeping source weapon sizes", error);
        }
        return Map.copyOf(map);
    }

    private static void loadInto(ResourceManager manager, Map<String, Entry> map, String id,
                                 String subdir, ResourceLocation texture) {
        try {
            String directory = "skeletons/items/" + subdir;
            JsonObject root = read(manager, directory + id + ".model.json");
            if (root == null) throw new IllegalArgumentException("Missing weapon model: " + id);
            ModelData model = ModelData.parse(root);
            if (!model.id.equals(EldenCombat.MODID + ":" + id) || model.unitsPerBlock != 16)
                throw new IllegalArgumentException("Weapon model ID or units mismatch: " + id);
            if (manager.getResource(texture).isEmpty())
                throw new IllegalArgumentException("Missing atlas: " + texture);
            Box bounds = HitVolumes.modelAabb(SkeletonPose.rest(model));
            WeaponMesh mesh = null;
            if (root.has("bakedMeshPath")) {
                try {
                    String filename = root.get("bakedMeshPath").getAsString();
                    if (!filename.matches("[a-z0-9_]+\\.mesh\\.json"))
                        throw new IllegalArgumentException("Invalid bakedMeshPath: " + filename);
                    JsonObject meshRoot = read(manager, directory + filename);
                    if (meshRoot == null) throw new IllegalArgumentException("Missing baked mesh: " + filename);
                    if (!model.id.equals(meshRoot.get("id").getAsString()))
                        throw new IllegalArgumentException("Mesh/model ID mismatch");
                    mesh = WeaponMesh.parse(meshRoot);
                    mesh.validateBounds(bounds);
                } catch (RuntimeException | IOException error) {
                    mesh = null;
                    EldenCombat.LOGGER.error("[EldenCombat] Invalid mesh for " + id + "; baking static cubes once", error);
                }
            }
            if (mesh == null) mesh = WeaponMesh.bake(model);
            map.put(id, new Entry(model, mesh, texture, bounds, null));
        } catch (RuntimeException | IOException error) {
            EldenCombat.LOGGER.error("[EldenCombat] Failed to load weapon " + id, error);
        }
    }

    private static JsonObject read(ResourceManager manager, String path) throws IOException {
        var resource = manager.getResource(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, path));
        if (resource.isEmpty()) return null;
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
