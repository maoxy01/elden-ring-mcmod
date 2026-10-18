package com.dsh.eldencombat.client.equipment2d;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One flat sprite face, emitted in the route's shared emission space.
 *
 * <p>Contract §10.3: the quad spans the whole canvas, which occupies exactly one block before the
 * display scale — {@code x in [-0.5, 0.5]}, {@code y in [-0.5, 0.5]}, {@code z = facing/32}. The grip is the
 * corresponding point of the same rectangle ({@link EquipmentGeometry#gripX()}), so position and grip
 * finally share one origin and one unit. The previous version emitted {@code [0, W/16] x [0, H/16]},
 * which made a 32-pixel sprite two blocks wide while the grip was still in centred 16-unit model
 * coordinates.
 *
 * <p>This helper emits one surface only; production shields use the admitted baked solid model with
 * contour walls. Callers can emit the front facing {@code +Z} and the back facing {@code -Z}, with
 * reversed winding and an opposite normal. The normal is derived from the pose's own normal matrix,
 * so a non-uniform or rotated transform cannot leave a stale {@code +Z} normal behind. Surface depth
 * is independent of the sprite's resolution.
 */
public final class ItemSpriteGeometry {

    /** Front face winding: counter-clockwise seen from {@code +Z}. */
    private static final float[][] FRONT = {{-0.5f, -0.5f}, {0.5f, -0.5f}, {0.5f, 0.5f}, {-0.5f, 0.5f}};
    /** Back face: the same rectangle with the winding reversed, so the back material is not culled. */
    private static final float[][] BACK = {{0.5f, -0.5f}, {-0.5f, -0.5f}, {-0.5f, 0.5f}, {0.5f, 0.5f}};

    private ItemSpriteGeometry() {
    }

    /** One emitted corner in emission space, with the texture coordinates the sprite supplies. */
    public record Corner(float x, float y, float u, float v) {
    }

    /**
     * The four corners of one face in emission order.
     *
     * <p>Pure and sprite-free on purpose: the rectangle <em>is</em> the contract ({@code x, y} in
     * {@code [-0.5, 0.5]}, one block before the display scale), and the texture
     * coordinates pin to the sprite's own bounds because the quad covers the whole sprite. Keeping it
     * separable means the extent and the winding are testable without a texture atlas.
     */
    public static Corner[] corners(float facing, float u0, float u1, float v0, float v1) {
        float[][] winding = facing > 0f ? FRONT : BACK;
        Corner[] corners = new Corner[4];
        for (int index = 0; index < 4; index++) {
            float x = winding[index][0];
            float y = winding[index][1];
            corners[index] = new Corner(x, y, x < 0f ? u0 : u1, y > 0f ? v0 : v1);
        }
        return corners;
    }

    /** Emit one face; {@code facing} is {@code +1} for the front and {@code -1} for the back. */
    public static void face(PoseStack pose, VertexConsumer consumer, TextureAtlasSprite sprite,
                            int light, int overlay, float facing) {
        if (sprite == null) {
            return;
        }
        Matrix4f matrix = pose.last().pose();
        Vector3f normal = new Vector3f(0f, 0f, facing).mul(pose.last().normal()).normalize();
        for (Corner corner : corners(facing, sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1())) {
            vertex(consumer, matrix, corner.x(), corner.y(), facing / 32f, corner.u(), corner.v(), normal, light, overlay);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float u, float v, Vector3f normal, int light, int overlay) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(normal.x, normal.y, normal.z);
    }
}
