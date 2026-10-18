package com.dsh.eldencombat;

import com.dsh.eldencombat.client.equipment2d.EquipmentModelResources;
import com.dsh.eldencombat.client.equipment2d.EquipmentRouteSnapshot;
import com.dsh.eldencombat.client.equipment2d.EquipmentStateResolver;
import com.dsh.eldencombat.client.equipment2d.ProviderModelAdapter;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class Equipment2dRegressionTest {
    private static final Path ROUTE = Path.of("src/main/resources/assets/eldencombat/routes/2d.json");

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }

    @Test
    void inventoryLookupUsesItemRegistryIdRatherThanModelJsonPath() {
        assertEquals(ModelResourceLocation.inventory(id("minecraft:iron_sword")),
                EquipmentRouteSnapshot.inventoryModel(id("minecraft:item/iron_sword")));
        assertNotEquals(EquipmentRouteSnapshot.inventoryModel(id("minecraft:item/bow")),
                EquipmentRouteSnapshot.standaloneModel(id("minecraft:item/bow")));
    }

    @Test
    void additionalModelsIncludeDynamicVanillaStatesButNotAbsentOptionalProvider() throws Exception {
        Map<ResourceLocation, String> resources = new HashMap<>();
        resources.put(EquipmentRouteSnapshot.ROUTE_FILE, Files.readString(ROUTE));
        var data = EquipmentRouteSnapshot.parse(JsonParser.parseString(Files.readString(ROUTE)).getAsJsonObject());
        for (var entry : data.entries().values()) {
            if (entry.route() == EquipmentRouteSnapshot.Route.VANILLA_MODEL) {
                entry.requiredPresence().forEach(file -> resources.put(file, "{}"));
            }
        }
        var models = EquipmentRouteSnapshot.additionalModelIds(manager(resources));
        assertTrue(models.contains("minecraft:item/iron_sword"));
        assertTrue(models.contains("minecraft:item/bow_pulling_2"));
        assertTrue(models.contains("minecraft:item/crossbow_firework"));
        assertFalse(models.stream().anyMatch(name -> name.startsWith("spartan_weaponry_unofficial:")));

        var longBow = data.get("eldencombat:long_bow");
        longBow.requiredPresence().forEach(file -> resources.put(file, "{}"));
        models = EquipmentRouteSnapshot.additionalModelIds(manager(resources));
        assertTrue(models.contains("spartan_weaponry_unofficial:item/longbow_iron_strengthened_pulling_2"));
    }

    @Test
    void providerResolvesInventoryBaseAndStandalonePullingStates() throws Exception {
        var data = EquipmentRouteSnapshot.parse(JsonParser.parseString(Files.readString(ROUTE)).getAsJsonObject());
        var entry = data.get("eldencombat:short_bow");
        Map<ResourceLocation, String> resources = new HashMap<>();
        entry.requiredPresence().forEach(file -> resources.put(file, "{}"));
        Map<ModelResourceLocation, BakedModel> baked = new HashMap<>();
        BakedModel base = model();
        BakedModel drawn = model();
        baked.put(ModelResourceLocation.inventory(id("minecraft:bow")), base);
        for (var state : entry.providerStates()) {
            if (!state.model().equals(entry.providerBase())) {
                baked.put(ModelResourceLocation.standalone(state.model()), drawn);
            }
        }
        var idle = ProviderModelAdapter.resolve(baked, manager(resources), entry, predicate -> 0f);
        assertSame(base, idle.getLast().baked());
        var pulling = ProviderModelAdapter.resolve(baked, manager(resources), entry, predicate -> 1f);
        assertEquals(id("minecraft:item/bow_pulling_2"), pulling.getLast().model());
        assertSame(drawn, pulling.getLast().baked());
        resources.remove(entry.requiredPresence().iterator().next());
        assertTrue(ProviderModelAdapter.resolve(baked, manager(resources), entry, predicate -> 1f).isEmpty());
    }

    @Test
    void combatGuardMirrorOnlyPosesTheSelectedShieldWhileIdle() {
        assertTrue(EquipmentStateResolver.combatShieldBlocking(true, "NONE", true));
        assertFalse(EquipmentStateResolver.combatShieldBlocking(true, "NONE", false));
        assertFalse(EquipmentStateResolver.combatShieldBlocking(false, "NONE", true));
        assertFalse(EquipmentStateResolver.combatShieldBlocking(true, "GUARD_BROKEN", true));
        assertFalse(EquipmentStateResolver.combatShieldBlocking(true, "LIGHT_ATTACK", true));
    }

    @Test
    void missingOrBrokenModelAndTextureResourcesRejectBeforeDrawingAndRecover() {
        ResourceLocation model = id("eldencombat:item/2d/shield_front");
        ResourceLocation file = id("eldencombat:models/item/2d/shield_front.json");
        ResourceLocation texture = id("eldencombat:textures/item/2d/shield_front.png");
        Map<ResourceLocation, String> resources = new HashMap<>();
        resources.put(id("minecraft:models/item/generated.json"), "{\"parent\":\"minecraft:builtin/generated\"}");
        String valid = "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"eldencombat:item/2d/shield_front\"}}";
        resources.put(file, valid);
        resources.put(texture, "present");
        ResourceManager manager = manager(resources);
        assertTrue(EquipmentModelResources.complete(manager, model));
        resources.remove(file);
        assertFalse(EquipmentModelResources.complete(manager, model));
        resources.put(file, "{");
        assertFalse(EquipmentModelResources.complete(manager, model));
        resources.put(file, valid);
        resources.remove(texture);
        assertFalse(EquipmentModelResources.complete(manager, model));
        resources.put(texture, "restored");
        assertTrue(EquipmentModelResources.complete(manager, model));
        resources.put(file, "{\"parent\":\"eldencombat:item/2d/shield_front\"}");
        assertFalse(EquipmentModelResources.complete(manager, model));
    }

    private static ResourceManager manager(Map<ResourceLocation, String> resources) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getResource") || method.getName().equals("getResourceOrThrow")) {
                        String text = resources.get(args[0]);
                        Resource resource = text == null ? null : new Resource(null,
                                () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
                        if (method.getName().equals("getResource")) return Optional.ofNullable(resource);
                        if (resource == null) throw new FileNotFoundException(args[0].toString());
                        return resource;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static BakedModel model() {
        return (BakedModel) Proxy.newProxyInstance(BakedModel.class.getClassLoader(),
                new Class<?>[]{BakedModel.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getQuads")) return List.of();
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
    }
}
