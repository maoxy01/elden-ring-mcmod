package com.dsh.eldencombat.client.equipment2d;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/** Normalizes only model-local Z after the single display transform, keeping XY and the midplane. */
public final class ItemThickness {
    public static final float BASE = 1f / 16f;
    private static final ModelResourceLocation REFERENCE = ModelResourceLocation.inventory(
            ResourceLocation.withDefaultNamespace("diamond_sword"));
    private static final Map<BakedModel, Depth> DEPTHS = new IdentityHashMap<>();

    public record Depth(float min, float max) {
        public float span() { return max - min; }
        public float center() { return (min + max) * .5f; }
        public boolean usable() { return Float.isFinite(min) && Float.isFinite(max) && span() > 1e-6f; }
    }

    private ItemThickness() {}

    public static void clear() { DEPTHS.clear(); }

    public static float referenceScale(ItemDisplayContext context) {
        return Math.abs(Minecraft.getInstance().getModelManager().getModel(REFERENCE)
                .getTransforms().getTransform(context).scale.z);
    }

    /** Target includes the caller's parent scale, before the item's own display is applied. */
    public static float target(PoseStack pose, ItemDisplayContext context) {
        return BASE * referenceScale(context) * zScale(pose);
    }

    public static float zScale(PoseStack pose) {
        var matrix = pose.last().pose();
        return (float) Math.sqrt(matrix.m20() * matrix.m20() + matrix.m21() * matrix.m21()
                + matrix.m22() * matrix.m22());
    }

    public static void normalize(PoseStack pose, Depth depth, float target) {
        float scale = zScale(pose);
        if (!depth.usable() || scale <= 1e-6f || !Float.isFinite(target) || target <= 0f) {
            throw new IllegalArgumentException("Invalid item thickness geometry or display");
        }
        pose.translate(0, 0, depth.center());
        pose.scale(1, 1, target / (depth.span() * scale));
        pose.translate(0, 0, -depth.center());
    }

    /** Measures every actual render pass; missing atlas sprites fail before any vertex is submitted. */
    public static Depth depth(BakedModel model, ItemStack stack, boolean solid) {
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (BakedModel pass : model.getRenderPasses(stack, solid)) {
            Depth part = DEPTHS.computeIfAbsent(pass, ItemThickness::measure);
            if (!part.usable()) return null;
            min = Math.min(min, part.min());
            max = Math.max(max, part.max());
        }
        Depth result = new Depth(min, max);
        return result.usable() ? result : null;
    }

    private static Depth measure(BakedModel model) {
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        RandomSource random = RandomSource.create(42L);
        for (int side = 0; side <= Direction.values().length; side++) {
            Direction direction = side == Direction.values().length ? null : Direction.values()[side];
            for (BakedQuad quad : model.getQuads(null, direction, random)) {
                if (quad.getSprite() == null || MissingTextureAtlasSprite.getLocation()
                        .equals(quad.getSprite().contents().name())) return new Depth(0, 0);
                int[] vertices = quad.getVertices();
                int stride = vertices.length / 4;
                for (int vertex = 0; vertex < 4; vertex++) {
                    float z = Float.intBitsToFloat(vertices[vertex * stride + 2]);
                    min = Math.min(min, z);
                    max = Math.max(max, z);
                }
            }
        }
        return new Depth(min, max);
    }
}
