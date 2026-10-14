package com.dsh.eldencombat.client;

import com.dsh.eldencombat.skeleton.CubeGeometry;
import com.dsh.eldencombat.skeleton.ModelData;
import com.dsh.eldencombat.skeleton.SkeletonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * 小型方块骨架渲染器：把建模坐标系里的方块按骨骼矩阵变换后逐面提交四边形。
 * 面顺序、UV 映射与 art/tooling/viewer.mjs 一致；不实现通用 glTF，也不做柔性蒙皮。
 */
public final class MargitModelRenderer {

    private MargitModelRenderer() {
    }

    public static void render(PoseStack poseStack, VertexConsumer consumer, ModelData model, SkeletonPose pose, int light) {
        render(poseStack,consumer,model,pose,light,-1);
    }

    public static void render(PoseStack poseStack, VertexConsumer consumer, ModelData model, SkeletonPose pose, int light, int color) {
        PoseStack.Pose entry = poseStack.last();
        for (ModelData.CubeSpec cube : model.cubes) {
            for (int face = 0; face < 6; face++) {
                String materialName = model.materialForFace(cube, face);
                ModelData.MaterialSpec material = model.material(materialName);
                float[] uv = CubeGeometry.faceUv(model, cube, material, face);
                int[] corners = CubeGeometry.FACE_CORNERS[face];
                Vector3f normal = new Vector3f(CubeGeometry.FACE_NORMALS[face]);
                pose.boneWorld(cube.bone()).rotateXYZ((float)Math.toRadians(cube.effectiveRotation().x), (float)Math.toRadians(cube.effectiveRotation().y),
                    (float)Math.toRadians(cube.effectiveRotation().z)).transformDirection(normal).normalize();

                for (int i = 0; i < 4; i++) {
                    int slot = CubeGeometry.SUBMIT_ORDER[i];
                    Vector3f position = pose.cubeCorner(cube, corners[slot]);
                    consumer.addVertex(entry, position.x, position.y, position.z)
                            .setColor(color>>16&255,color>>8&255,color&255,color>>>24)
                            .setUv(uv[slot * 2], uv[slot * 2 + 1])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(entry, normal.x, normal.y, normal.z);
                }
            }
        }
    }
}
