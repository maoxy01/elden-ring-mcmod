package com.dsh.eldencombat.client;

import com.dsh.eldencombat.skeleton.ModelData;
import com.dsh.eldencombat.core.geom.Box;
import com.dsh.eldencombat.skeleton.WeaponMeshData;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Cached QUADS-compatible geometry for vanilla entity and foil buffers. */
public final class WeaponMesh {
    private final WeaponMeshData data;

    private WeaponMesh(WeaponMeshData data) { this.data = data; }
    public static WeaponMesh parse(JsonObject root) { return new WeaponMesh(WeaponMeshData.parse(root)); }
    public static WeaponMesh bake(ModelData model) { return new WeaponMesh(WeaponMeshData.bake(model)); }
    public int vertexCount() { return data.vertexCount(); }
    public int triangleCount() { return data.triangleCount(); }
    public void validateBounds(Box bounds) { data.validateBounds(bounds); }

    public void render(VertexConsumer consumer, PoseStack.Pose entry, int light, int color) {
        for (int v = 0; v < data.vertexCount(); v++) {
            consumer.addVertex(entry, data.value(v, 0), data.value(v, 1), data.value(v, 2))
                    .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24)
                    .setUv(data.value(v, 3), data.value(v, 4))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(entry, data.value(v, 5), data.value(v, 6), data.value(v, 7));
        }
    }
}
