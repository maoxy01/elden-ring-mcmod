package com.dsh.eldencombat.core.geom;

import org.joml.Vector3d;

/**
 * 简化碰撞：线段/胶囊对 AABB 的距离判定。
 * 判定体由服务端骨骼采样得到（木杖长轴、匕首短轴、锤头盒），与人物整体碰撞盒是两个模块。
 */
public final class Collision {

    private Collision() {
    }

    /** 点到 AABB 最近距离 */
    public static double distancePointToBox(Vector3d point, Box box) {
        return box.distanceTo(point.x, point.y, point.z);
    }

    /**
     * 线段到 AABB 的最近距离。
     * 点沿线段移动时到凸集的距离是关于参数 t 的凸函数，用三分搜索即可得到高精度结果。
     */
    public static double distanceSegmentToBox(Vector3d a, Vector3d b, Box box) {
        double best = Math.min(distancePointToBox(a, box), distancePointToBox(b, box));
        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < 48; i++) {
            double m1 = lo + (hi - lo) / 3.0;
            double m2 = hi - (hi - lo) / 3.0;
            if (sampleDistance(a, b, m1, box) < sampleDistance(a, b, m2, box)) {
                hi = m2;
            } else {
                lo = m1;
            }
        }
        double mid = (lo + hi) * 0.5;
        return Math.min(best, sampleDistance(a, b, mid, box));
    }

    private static double sampleDistance(Vector3d a, Vector3d b, double t, Box box) {
        double x = a.x + (b.x - a.x) * t;
        double y = a.y + (b.y - a.y) * t;
        double z = a.z + (b.z - a.z) * t;
        return box.distanceTo(x, y, z);
    }

    /** 胶囊（线段 + 半径）是否与 AABB 相交 */
    public static boolean capsuleHitsBox(Vector3d a, Vector3d b, Box box, double radius) {
        return distanceSegmentToBox(a, b, box) <= radius;
    }

    /**
     * 扫掠判定：同时检查上一 tick 与当前 tick 的判定体，避免高速挥舞漏判。
     */
    public static boolean sweptCapsuleHitsBox(Vector3d prevA, Vector3d prevB, Vector3d nowA, Vector3d nowB,
                                             Box box, double radius) {
        if (capsuleHitsBox(nowA,nowB,box,radius) || capsuleHitsBox(prevA,prevB,box,radius)) return true;
        double travel = Math.max(prevA.distance(nowA), prevB.distance(nowB));
        int steps = Math.min(256,Math.max(1,(int)Math.ceil(travel/Math.max(0.01,radius*0.25))));
        // The extra radius bounds the motion between adjacent samples.
        double conservativeRadius=radius+travel/(2*steps);
        for(int i=0;i<steps;i++) {
            double t=(i+0.5)/steps;
            Vector3d a=new Vector3d(prevA).lerp(nowA,t), b=new Vector3d(prevB).lerp(nowB,t);
            if(capsuleHitsBox(a,b,box,conservativeRadius))return true;
        }
        return false;
    }

    /** 球体是否与 AABB 相交（锤头扫掠的简化形态） */
    public static boolean sphereHitsBox(Vector3d center, double radius, Box box) {
        return distancePointToBox(center, box) <= radius;
    }

    /** 把方向规范化；零向量返回 (0,0)。 */
    public static double[] normalize2D(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return new double[]{0.0, 0.0};
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0e-6) {
            return new double[]{0.0, 0.0};
        }
        return new double[]{x / length, z / length};
    }

    /**
     * 目标是否位于朝向的水平锥角内。
     *
     * @param coneDegrees 锥角总角度（例如盾防 120 度表示左右各 60 度）
     */
    public static boolean withinCone(double lookX, double lookZ, double toTargetX, double toTargetZ, double coneDegrees) {
        double[] look = normalize2D(lookX, lookZ);
        double[] toTarget = normalize2D(toTargetX, toTargetZ);
        if (look[0] == 0 && look[1] == 0) {
            return true;
        }
        if (toTarget[0] == 0 && toTarget[1] == 0) {
            return true;
        }
        double dot = look[0] * toTarget[0] + look[1] * toTarget[1];
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return angle <= coneDegrees * 0.5 + 1.0e-6;
    }
}
