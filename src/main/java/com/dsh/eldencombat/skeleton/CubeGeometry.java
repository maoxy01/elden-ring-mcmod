package com.dsh.eldencombat.skeleton;

/**
 * 方块六面的顶点与 UV 表，语义与 three.js BoxGeometry / viewer.mjs 完全一致：
 * 面顺序 +X, -X, +Y, -Y, +Z, -Z；每面 4 个角按 TL, TR, BL, BR 给出（建模坐标的 ±from/to 组合）。
 *
 * <p>角点索引位：bit0 = x 取 to，bit1 = y 取 to，bit2 = z 取 to。
 * 渲染时按 TL, BL, BR, TR 顺序提交（逆时针），与 MC 的四边形绕序一致。
 */
public final class CubeGeometry {

    /** 每面 [TL, TR, BL, BR] 的角点索引 */
    public static final int[][] FACE_CORNERS = {
            {7, 3, 5, 1}, // +X
            {2, 6, 0, 4}, // -X
            {2, 3, 6, 7}, // +Y
            {4, 5, 0, 1}, // -Y
            {6, 7, 4, 5}, // +Z
            {3, 2, 1, 0}  // -Z
    };

    public static final float[][] FACE_NORMALS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    /** MC 提交四边形时使用的顶点顺序（TL, BL, BR, TR） */
    public static final int[] SUBMIT_ORDER = {0, 2, 3, 1};

    private CubeGeometry() {
    }

    /**
     * 材质矩形映射到面 UV。贴图原点在左上角，V 轴按 MC 约定（0 = 顶部）直接使用像素坐标，
     * 并做半像素内缩避免相邻材质渗色。
     */
    /**
     * 优先使用逐方块逐面 UV（追忆武器的 2048 图集），没有时退回材质矩形（旧 128/512 资产）。
     * 两种来源都要按各自 {@code textureSize} 归一化，不能硬编码 /64、/512 或 /1024。
     */
    public static float[] faceUv(ModelData model, ModelData.CubeSpec cube, ModelData.MaterialSpec material, int face) {
        float[] perFace = model.faceUvFor(cube.name(), face);
        return perFace != null ? perFace : faceUv(model, material);
    }

    public static float[] faceUv(ModelData model, ModelData.MaterialSpec material) {
        float u0 = (material.u0() + 0.5f) / model.textureWidth;
        float u1 = (material.u1() - 0.5f) / model.textureWidth;
        float v0 = (material.v0() + 0.5f) / model.textureHeight;
        float v1 = (material.v1() - 0.5f) / model.textureHeight;
        // TL, TR, BL, BR
        return new float[]{u0, v0, u1, v0, u0, v1, u1, v1};
    }
}
