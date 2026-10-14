package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.effects.SkillEffectMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.dsh.eldencombat.effects.VfxBudget;
import java.util.List;

final class SkillEffectMeshRenderer {
    private static final Matrix4f CAMERA_MATRIX = new Matrix4f();
    private static final Vector3f EYE = new Vector3f();
    private SkillEffectMeshRenderer() {}

    static void draw(PoseStack pose, MultiBufferSource buffers, List<SkillEffectMesh.Box> boxes) {
        draw(pose, buffers, boxes, boxes.size(), 1, false);
    }

    static void draw(PoseStack pose, MultiBufferSource buffers, List<SkillEffectMesh.Box> boxes, int selected,
                     float alphaScale, boolean square) {
        if (boxes.isEmpty() || selected == 0) return;
        VertexConsumer out = buffers.getBuffer(SkillEffectRenderTypes.MESH);
        Matrix4f matrix = pose.last().pose();
        matrix.invert(CAMERA_MATRIX).transformPosition(EYE.set(0, 0, 0));
        for (int slot = 0; slot < selected; slot++) {
            var b = boxes.get(VfxBudget.index(slot, boxes.size(), selected, square));
            if (b.alpha() * alphaScale < 1f / 255) continue;
            int faces = VfxBudget.faceMask(b, EYE.x, EYE.y, EYE.z, alphaScale);
            if ((faces & 1) != 0)
                face(out, matrix, b, alphaScale, b.x0(), b.y0(), b.z0(), b.x1(), b.y0(), b.z0(), b.x1(), b.y1(), b.z0(), b.x0(), b.y1(), b.z0());
            if ((faces & 2) != 0)
                face(out, matrix, b, alphaScale, b.x1(), b.y0(), b.z1(), b.x0(), b.y0(), b.z1(), b.x0(), b.y1(), b.z1(), b.x1(), b.y1(), b.z1());
            if ((faces & 4) != 0)
                face(out, matrix, b, alphaScale, b.x0(), b.y0(), b.z1(), b.x0(), b.y0(), b.z0(), b.x0(), b.y1(), b.z0(), b.x0(), b.y1(), b.z1());
            if ((faces & 8) != 0)
                face(out, matrix, b, alphaScale, b.x1(), b.y0(), b.z0(), b.x1(), b.y0(), b.z1(), b.x1(), b.y1(), b.z1(), b.x1(), b.y1(), b.z0());
            if ((faces & 16) != 0)
                face(out, matrix, b, alphaScale, b.x0(), b.y1(), b.z0(), b.x1(), b.y1(), b.z0(), b.x1(), b.y1(), b.z1(), b.x0(), b.y1(), b.z1());
            if ((faces & 32) != 0)
                face(out, matrix, b, alphaScale, b.x0(), b.y0(), b.z1(), b.x1(), b.y0(), b.z1(), b.x1(), b.y0(), b.z0(), b.x0(), b.y0(), b.z0());
        }
    }

    private static void face(VertexConsumer out, Matrix4f matrix, SkillEffectMesh.Box box, float alphaScale,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        int red = box.rgb() >> 16 & 255, green = box.rgb() >> 8 & 255, blue = box.rgb() & 255;
        int alpha = Math.clamp((int) (box.alpha() * alphaScale * 255), 0, 255);
        out.addVertex(matrix, x0, y0, z0).setColor(red, green, blue, alpha);
        out.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha);
        out.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha);
        out.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, alpha);
    }
}
