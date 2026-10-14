package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.effects.FxSlashRoute;
import com.dsh.eldencombat.effects.SkillOutlineShapes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import java.util.List;

/**
 * Untextured, grid-aligned silhouette panels in the route's own plane: the degraded artwork used when a
 * routed effect has no loaded atlas frames, and at core LOD where binding the atlas is not worth it.
 * Replaces the crossing translucent box mesh for those effects instead of stacking on top of it.
 */
final class SkillOutlineRenderer {
    private SkillOutlineRenderer() {}

    static void draw(PoseStack pose, MultiBufferSource buffers, List<SkillOutlineShapes.Panel> panels, FxSlashRoute spec) {
        if (panels.isEmpty() || spec == null) return;
        VertexConsumer out = buffers.getBuffer(SkillEffectRenderTypes.MESH);
        for (var panel : panels) {
            if (panel.alpha() < 1f / 255) continue;
            pose.pushPose();
            pose.translate(panel.x(), panel.y(), panel.z());
            pose.mulPose(Axis.YP.rotationDegrees(spec.planeYaw()));
            pose.mulPose(Axis.XP.rotationDegrees(spec.planePitch()));
            pose.mulPose(Axis.ZP.rotationDegrees(panel.roll()));
            Matrix4f matrix = pose.last().pose();
            float x = panel.width() / 2, y = panel.height() / 2;
            int red = panel.rgb() >> 16 & 255, green = panel.rgb() >> 8 & 255, blue = panel.rgb() & 255;
            int alpha = Math.clamp((int) (panel.alpha() * 255), 0, 255);
            out.addVertex(matrix, -x, -y, 0).setColor(red, green, blue, alpha);
            out.addVertex(matrix, x, -y, 0).setColor(red, green, blue, alpha);
            out.addVertex(matrix, x, y, 0).setColor(red, green, blue, alpha);
            out.addVertex(matrix, -x, y, 0).setColor(red, green, blue, alpha);
            pose.popPose();
        }
    }
}
