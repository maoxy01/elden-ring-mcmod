package com.dsh.eldencombat.effects;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 相机瞄准与屏幕投影的纯数学，供 VFX 隔离夹具使用（无客户端类依赖，可直接单测）。
 *
 * <p>单位约定：坐标为世界格，角度为度；内部只做弧度换算。正常游戏中没有任何代码路径调用它，
 * 只有显式 {@code eldencombat.vfxSmoke} 夹具使用。
 */
public final class VfxCameraMath {
    /** 近平面以内不参与屏幕范围统计（与引擎 near 同量级，只用于"是否可见"判断）。 */
    public static final double NEAR = 0.05;
    /** 视锥远平面：只用于"包围盒是否与视锥相交"，取引擎默认量级即可（本模组效果都在 200 格内）。 */
    public static final double FAR = 256.0;

    private VfxCameraMath() {}

    /**
     * 从眼位看向目标的设计朝向，返回 {@code {yawDegrees, pitchDegrees}}。
     *
     * <p>约定与 Minecraft 一致：yaw 由 {@code atan2(-dx, dz)} 得出，pitch 向下为正；
     * 目标与眼位重合（含零向量）或结果非有限值直接抛错，绝不返回 NaN。
     */
    public static float[] aimAt(Vec3 eye, Vec3 target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0e-6 && Math.abs(dy) < 1.0e-6)
            throw new IllegalArgumentException("camera target coincides with camera eye");
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalStateException("non-finite camera aim");
        return new float[] { yaw, (float) Math.max(-89.0, Math.min(89.0, pitch)) };
    }

    /** 两个设计朝向之间的角度差（度），用于核对"设置值 vs 渲染帧真实 Camera"。 */
    public static double angleBetween(float yawA, float pitchA, float yawB, float pitchB) {
        double dyaw = Math.toRadians(yawA - yawB);
        double dpitch = Math.toRadians(pitchA - pitchB);
        double dot = Math.cos(dpitch) * Math.cos(dyaw);
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    /**
     * 用相机基向量把 AABB 八个角投影到 NDC，返回
     * {@code [minX, minY, maxX, maxY, minViewZ, cornersInFront, cornersOnScreen, centreDepth]}。
     *
     * <p>全部角都在近平面之后（或背后）时，前四个值为 {@code NaN}、minViewZ 为 {@code +Inf}、
     * 计数为 0、centreDepth 为包围盒中心沿 forward 的深度；调用方用
     * {@link #intersectsScreen(double[], boolean)} 判定即可，不需要额外空值分支。
     */
    public static double[] ndcExtent(Vec3 eye, Vector3f forward, Vector3f left, Vector3f up,
                                     AABB box, double fovYDeg, double aspect) {
        if (!(fovYDeg > 1.0) || !(aspect > 1.0e-4))
            throw new IllegalArgumentException("invalid projection: fovY=" + fovYDeg + " aspect=" + aspect);
        double tanHalf = Math.tan(Math.toRadians(fovYDeg) * 0.5);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        int inFront = 0, onScreen = 0;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
            double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
            double depth = dx * forward.x + dy * forward.y + dz * forward.z;
            if (!(depth > NEAR)) continue;
            double sx = dx * left.x + dy * left.y + dz * left.z;
            double sy = dx * up.x + dy * up.y + dz * up.z;
            double nx = (sx / depth) / (tanHalf * aspect);
            double ny = (sy / depth) / tanHalf;
            inFront++;
            minZ = Math.min(minZ, depth);
            minX = Math.min(minX, nx);
            maxX = Math.max(maxX, nx);
            minY = Math.min(minY, ny);
            maxY = Math.max(maxY, ny);
            if (Math.abs(nx) <= 1.0 && Math.abs(ny) <= 1.0) onScreen++;
        }
        double cx = (box.minX + box.maxX) * 0.5 - eye.x;
        double cy = (box.minY + box.maxY) * 0.5 - eye.y;
        double cz = (box.minZ + box.maxZ) * 0.5 - eye.z;
        double centreDepth = cx * forward.x + cy * forward.y + cz * forward.z;
        if (inFront == 0)
            return new double[] { Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.POSITIVE_INFINITY, 0, 0, centreDepth };
        return new double[] { minX, minY, maxX, maxY, minZ, inFront, onScreen, centreDepth };
    }

    /** 投影范围与屏幕是否有非零面积交集（至少一个角在近平面前）。 */
    public static boolean intersectsScreen(double[] extent) {
        return intersectsScreen(extent, false);
    }

    /**
     * 投影范围与屏幕是否有非零面积交集。
     *
     * @param requireCentreFront 为 true 时，还要求包围盒中心位于眼位前方（相机朝向张角 ≤90° 的针孔模型下，
     *        中心在背后、只用角点判定会把"以施法者为中心"的立体效果误判为可见）。负例门禁用它来严格证明
     *        "朝向反了就不算可见"；正向构图门禁保持角点判定，避免把"相机朝错"错报为"效果不在画面里"。
     */
    public static boolean intersectsScreen(double[] extent, boolean requireCentreFront) {
        if (extent == null || extent.length < 8) return false;
        if (!(extent[5] > 0)) return false;
        if (requireCentreFront && !(extent[7] > 0)) return false;
        return extent[2] > -1.0 && extent[0] < 1.0 && extent[3] > -1.0 && extent[1] < 1.0;
    }

    /** 眼位是否落在包围盒内（此时"反向相机"负例无意义，调用方应跳过并记录）。 */
    public static boolean eyeInside(Vec3 eye, AABB box) {
        return box.contains(eye.x, eye.y, eye.z);
    }

    /**
     * 包围盒与相机视锥是否真的相交（六个半空间的 p-vertex 判定）。
     *
     * <p>只投影八个角点会漏掉两类真实可见的情况：包围盒大过整个画面（角点全在屏幕外、画面却被盖满），
     * 以及长条包围盒横跨镜头（角点落在近平面之后被丢弃）。半空间判定对这两类都正确，且天然涵盖
     * "眼位在盒内"。每一面取"最不利角点"（p-vertex）——它在该面外侧时才可能整体分离，反之至少有一点在内侧。
     *
     * @param fovYDeg 竖直视场角（度）
     * @param near    近平面距离（格），必须 &gt; 0
     * @param far     远平面距离（格），必须 &gt; near
     */
    public static boolean intersectsFrustum(Vec3 eye, Vector3f forward, Vector3f left, Vector3f up,
                                            AABB box, double fovYDeg, double aspect, double near, double far) {
        if (box == null || forward == null || left == null || up == null) return false;
        if (!(fovYDeg > 1.0) || !(aspect > 1.0e-4) || !(near > 0.0) || !(far > near)) return false;
        double tanHalf = Math.tan(Math.toRadians(fovYDeg) * 0.5);
        double tanX = tanHalf * aspect;
        // 盒心与半尺寸在相机坐标系下的三个分量（半尺寸用相机基向量的绝对值投影，得到包围盒在相机空间的外接长方体）。
        double cx = box.getCenter().x - eye.x, cy = box.getCenter().y - eye.y, cz = box.getCenter().z - eye.z;
        double hx = (box.maxX - box.minX) * 0.5, hy = (box.maxY - box.minY) * 0.5, hz = (box.maxZ - box.minZ) * 0.5;
        double[] centre = { cx * left.x + cy * left.y + cz * left.z,
                            cx * up.x + cy * up.y + cz * up.z,
                            cx * forward.x + cy * forward.y + cz * forward.z };
        double[] half = { Math.abs(left.x) * hx + Math.abs(left.y) * hy + Math.abs(left.z) * hz,
                          Math.abs(up.x) * hx + Math.abs(up.y) * hy + Math.abs(up.z) * hz,
                          Math.abs(forward.x) * hx + Math.abs(forward.y) * hy + Math.abs(forward.z) * hz };
        // 六个半空间：{nx, ny, nz, d}，内侧满足 n·p + d >= 0（p 为相机坐标）。
        double[][] planes = {
            { 0.0, 0.0, 1.0, -near },
            { 0.0, 0.0, -1.0, far },
            { tanX, 0.0, 1.0, 0.0 },
            { -tanX, 0.0, 1.0, 0.0 },
            { 0.0, tanHalf, 1.0, 0.0 },
            { 0.0, -tanHalf, 1.0, 0.0 },
        };
        for (double[] plane : planes) {
            double support = centre[0] * plane[0] + centre[1] * plane[1] + centre[2] * plane[2] + plane[3]
                    + Math.abs(plane[0]) * half[0] + Math.abs(plane[1]) * half[1] + Math.abs(plane[2]) * half[2];
            if (support < 0.0) return false;   // 整盒都在该面外侧：不可能与视锥相交
        }
        return true;
    }
}
