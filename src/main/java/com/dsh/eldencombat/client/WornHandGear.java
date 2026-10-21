package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.skeleton.ModelData;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Arm-local gear; separate from item display transforms and the 2D fallback. */
public final class WornHandGear {
    public record Pair(WeaponMesh right, WeaponMesh left) {}
    public record FirstPerson(ItemStack stack, HumanoidArm arm) {}
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "textures/item/worn_hands.png");
    private static volatile Map<String, Pair> loaded = Map.of();
    private static final ThreadLocal<FirstPerson> FIRST_PERSON = new ThreadLocal<>();

    private WornHandGear() {}

    public static Map<String, Pair> prepare(ResourceManager manager) {
        Map<String, Pair> prepared = new HashMap<>();
        for (String id : new String[]{"claw", "fist"}) {
            try {
                if (manager.getResource(TEXTURE).isEmpty()) throw new IllegalArgumentException("Missing hand atlas");
                prepared.put(id, new Pair(load(manager, id, "right"), load(manager, id, "left")));
            } catch (Exception error) {
                // A pair is admitted atomically; an incomplete resource pack uses the old item route.
                EldenCombat.LOGGER.error("[EldenCombat] Invalid worn gear {}; keeping item fallback", id, error);
            }
        }
        return Map.copyOf(prepared);
    }

    private static WeaponMesh load(ResourceManager manager, String id, String arm) throws java.io.IOException {
        var location = ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "worn/" + id + "_" + arm + ".json");
        try (var reader = new InputStreamReader(manager.getResource(location).orElseThrow().open(), StandardCharsets.UTF_8)) {
            ModelData model = ModelData.parse(JsonParser.parseReader(reader).getAsJsonObject());
            if (!model.id.equals(EldenCombat.MODID + ":" + id + "_" + arm) || model.unitsPerBlock != 16)
                throw new IllegalArgumentException("Worn model identity/units mismatch");
            return WeaponMesh.bake(model);
        }
    }

    public static void apply(Map<String, Pair> prepared) { loaded = Map.copyOf(prepared); }

    private static Pair entry(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return EldenCombat.MODID.equals(id.getNamespace()) ? loaded.get(id.getPath()) : null;
    }

    public static boolean handles(ItemStack stack) { return entry(stack) != null; }

    public static ItemStack stackForArm(AbstractClientPlayer player, HumanoidArm arm) {
        return player.getMainArm() == arm ? player.getMainHandItem() : player.getOffhandItem();
    }

    public static FirstPerson firstPerson() { return FIRST_PERSON.get(); }
    public static FirstPerson enterFirstPerson(ItemStack stack, HumanoidArm arm) {
        FirstPerson previous = FIRST_PERSON.get();
        FIRST_PERSON.set(new FirstPerson(stack, arm));
        return previous;
    }
    public static void restoreFirstPerson(FirstPerson previous) {
        if (previous == null) FIRST_PERSON.remove(); else FIRST_PERSON.set(previous);
    }

    public static void render(AbstractClientPlayer player, ItemStack stack, HumanoidArm arm, ModelPart part,
                              PoseStack pose, MultiBufferSource buffers, int light) {
        Pair pair = entry(stack);
        if (pair == null || player.isInvisible()) return;
        pose.pushPose();
        try {
            part.translateAndRotate(pose);
            if (player.getSkin().model() == PlayerSkin.Model.SLIM)
                pose.translate(arm == HumanoidArm.RIGHT ? .5f / 16 : -.5f / 16, 0, 0);
            // Authored arm-local Y points up; ModelPart Y points down. Left is already authored.
            pose.scale(1, -1, 1);
            var vertices = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), false, stack.hasFoil());
            (arm == HumanoidArm.RIGHT ? pair.right() : pair.left()).render(vertices, pose.last(), light, -1);
        } finally {
            pose.popPose();
        }
    }
}
