package com.dsh.eldencombat.effects;

import java.util.Locale;

/**
 * V1 配对判据与像素差分的纯数学（无客户端类依赖，可直接单测）。
 *
 * <p>旧门禁把同一段里所有"效果帧"与后来的一张"干净帧"逐像素比较，只取最大值，从不检查两张图的
 * 实际相机与投影。只要镜头在效果寿命内动过，画面里地平线/背景/hand 的变化都会被算成"作者主体"。
 * 本类先把"能不能比"作为独立判据：矩阵、相机、视口、资源代数、维度与显示设置任一失配即
 * {@code NOT_COMPARABLE}，此时**不产生任何差分分数**，也不允许把阈值放宽来修补。
 */
public final class VfxShotComparison {
    /** 允许的投影误差上限（像素）：固定机位下的数值误差可以容到这个量级，超过即判不可比。 */
    public static final double MAX_PIXEL_ERROR = 1.0;
    /**
     * 眼位的数值容差（格）。引擎的相机眼高是逐帧逼近的，静止观察实体会残留 ~1e-7 格的收敛尾差；
     * 1e-3 格在 480 像素、70° 视场下约为 0.045 像素，远小于 {@link #MAX_PIXEL_ERROR}，
     * 而真实机位差异（本夹具负例移动 6 格以上）会被下面的投影误差判据直接拒绝。
     */
    public static final double EYE_TOLERANCE = 1.0E-3;
    /** 朝向的数值容差（度），同样远小于一个像素所对应的角度。 */
    public static final double ANGLE_TOLERANCE = 1.0E-3;

    /** 一帧截图的完整渲染状态；数值以全精度（double/float）保存，序列化不得只留 3 位小数。 */
    public record ShotState(String sceneId, int renderFrameId, long resourceRevision, String dimension,
                            String cameraEntity, String layer, String targetIdentity,
                            double eyeX, double eyeY, double eyeZ, float yaw, float pitch,
                            float[] modelView, float[] projection,
                            int fbWidth, int fbHeight, int windowWidth, int windowHeight,
                            double guiScale, boolean bobView, double gameTime, boolean night,
                            boolean raining) {
        public static ShotState of(String sceneId, String layer, String targetIdentity) {
            return new ShotState(sceneId, -1, -1, "", "", layer, targetIdentity,
                    Double.NaN, Double.NaN, Double.NaN, Float.NaN, Float.NaN,
                    new float[16], new float[16], 0, 0, 0, 0, Double.NaN, false, Double.NaN, false, false);
        }
    }

    public record PairVerdict(boolean comparable, String reason) {}

    private VfxShotComparison() {}

    /** 先判可比：只有状态真正一致（投影误差 &lt; {@link #MAX_PIXEL_ERROR} 像素）才允许算差分。 */
    public static PairVerdict comparable(ShotState live, ShotState clean, double[][] worldCorners) {
        if (live == null || clean == null) return new PairVerdict(false, "missing-state");
        if (!java.util.Objects.equals(live.dimension(), clean.dimension())) return new PairVerdict(false, "dimension");
        if (live.resourceRevision() != clean.resourceRevision()) return new PairVerdict(false, "resource-revision");
        if (!java.util.Objects.equals(live.cameraEntity(), clean.cameraEntity())) return new PairVerdict(false, "camera-entity");
        if (!java.util.Objects.equals(live.targetIdentity(), clean.targetIdentity())) return new PairVerdict(false, "target");
        if (live.fbWidth() != clean.fbWidth() || live.fbHeight() != clean.fbHeight()) return new PairVerdict(false, "framebuffer");
        if (live.windowWidth() != clean.windowWidth() || live.windowHeight() != clean.windowHeight()) return new PairVerdict(false, "window");
        if (Double.compare(live.guiScale(), clean.guiScale()) != 0) return new PairVerdict(false, "gui-scale");
        if (live.bobView() != clean.bobView()) return new PairVerdict(false, "bob-view");
        double eyeDelta = Math.max(Math.abs(live.eyeX() - clean.eyeX()),
                Math.max(Math.abs(live.eyeY() - clean.eyeY()), Math.abs(live.eyeZ() - clean.eyeZ())));
        if (!(eyeDelta <= EYE_TOLERANCE)) return new PairVerdict(false, "eye-delta=" + fmt(eyeDelta));
        double angleDelta = Math.max(Math.abs(live.yaw() - clean.yaw()), Math.abs(live.pitch() - clean.pitch()));
        if (!(angleDelta <= ANGLE_TOLERANCE)) return new PairVerdict(false, "orientation-delta=" + fmt(angleDelta));
        if (!finite(live.modelView()) || !finite(live.projection()) || !finite(clean.modelView()) || !finite(clean.projection()))
            return new PairVerdict(false, "matrix-shape");
        if (worldCorners == null || worldCorners.length == 0) return new PairVerdict(false, "no-roi-corners");
        double error = maxPixelError(live, clean, worldCorners);
        if (!(error <= MAX_PIXEL_ERROR))
            return new PairVerdict(false, "projection-error=" + fmt(error));
        return new PairVerdict(true, "ok(eyeDelta=" + fmt(eyeDelta) + ",angleDelta=" + fmt(angleDelta)
                + ",pixelError=" + fmt(error) + ")");
    }

    private static String fmt(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6g", value) : String.valueOf(value);
    }

    public static PairVerdict comparable(ShotState live, ShotState clean) {
        return comparable(live, clean, new double[][] { { 0, 0, 0 } });
    }

    private static boolean finite(float[] matrix) {
        if (matrix == null || matrix.length != 16) return false;
        for (float value : matrix) if (!Float.isFinite(value)) return false;
        return true;
    }

    /**
     * 用两帧各自的 模型视图×投影 把同一组世界角点投影到像素坐标，取最大欧氏距离（像素）。
     * 只在近平面之后（w ≤ 0）的角点被跳过（保守包围盒的背面角点本来就不该参与），
     * 但至少要有两个角点在两帧都可用，否则该配对不可比（返回 +∞）。
     */
    public static double maxPixelError(ShotState a, ShotState b, double[][] corners) {
        double worst = 0.0;
        int usable = 0;
        for (double[] corner : corners) {
            double[] pa = projectWorld(a, corner[0], corner[1], corner[2]);
            double[] pb = projectWorld(b, corner[0], corner[1], corner[2]);
            if (pa == null || pb == null) continue;
            usable++;
            worst = Math.max(worst, Math.hypot(pa[0] - pb[0], pa[1] - pb[1]));
        }
        return usable < 2 ? Double.POSITIVE_INFINITY : worst;
    }

    /**
     * 世界坐标点 → 像素坐标：先减去该帧真实眼位换成相机相对坐标，再套该帧的模型视图×投影。
     * 引擎的关卡模型视图是"以相机为原点"的（渲染实体时同样先减 camX/camY/camZ），
     * 直接把世界坐标喂进去会在镜头背后的角点上得到 w ≤ 0，从而把可比对误判成不可比。
     */
    public static double[] projectWorld(ShotState state, double x, double y, double z) {
        if (state == null) return null;
        return project(state.modelView(), state.projection(),
                x - state.eyeX(), y - state.eyeY(), z - state.eyeZ(), state.fbWidth(), state.fbHeight());
    }

    /** 相机相对坐标 → 像素坐标（列主序 4×4；w ≤ 0 返回 null）。 */
    public static double[] project(float[] modelView, float[] projection, double x, double y, double z,
                                  int fbWidth, int fbHeight) {
        if (!finite(modelView) || !finite(projection) || fbWidth <= 0 || fbHeight <= 0) return null;
        double vx = modelView[0] * x + modelView[4] * y + modelView[8] * z + modelView[12];
        double vy = modelView[1] * x + modelView[5] * y + modelView[9] * z + modelView[13];
        double vz = modelView[2] * x + modelView[6] * y + modelView[10] * z + modelView[14];
        double vw = modelView[3] * x + modelView[7] * y + modelView[11] * z + modelView[15];
        double cx = projection[0] * vx + projection[4] * vy + projection[8] * vz + projection[12] * vw;
        double cy = projection[1] * vx + projection[5] * vy + projection[9] * vz + projection[13] * vw;
        double cw = projection[3] * vx + projection[7] * vy + projection[11] * vz + projection[15] * vw;
        if (!(cw > 1.0E-6)) return null;
        double ndcX = cx / cw, ndcY = cy / cw;
        return new double[] { (ndcX * 0.5 + 0.5) * fbWidth, (1.0 - (ndcY * 0.5 + 0.5)) * fbHeight };
    }

    /**
     * 目标可视角点 → 屏幕 ROI 矩形 {@code {x0,y0,x1,y1}}（含 margin，像素坐标，已裁剪到画面内）。
     * 所有角点都在近平面之后时返回 null：这一帧没有可用于差分的主体 ROI。
     */
    public static int[] roiFromCorners(ShotState state, double[][] corners, int margin) {
        if (state == null || corners == null || corners.length == 0) return null;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            double[] pixel = projectWorld(state, corner[0], corner[1], corner[2]);
            if (pixel == null) continue;
            minX = Math.min(minX, pixel[0]); maxX = Math.max(maxX, pixel[0]);
            minY = Math.min(minY, pixel[1]); maxY = Math.max(maxY, pixel[1]);
        }
        if (!Double.isFinite(minX) || !Double.isFinite(minY)) return null;
        int x0 = (int) Math.max(0, Math.floor(minX) - margin);
        int y0 = (int) Math.max(0, Math.floor(minY) - margin);
        int x1 = (int) Math.min(state.fbWidth() - 1, Math.ceil(maxX) + margin);
        int y1 = (int) Math.min(state.fbHeight() - 1, Math.ceil(maxY) + margin);
        if (x1 <= x0 || y1 <= y0) return null;
        return new int[] { x0, y0, x1, y1 };
    }

    /** AABB 的八个角点（供 ROI 与世界角点使用）。 */
    public static double[][] corners(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double[][] out = new double[8][3];
        for (int corner = 0; corner < 8; corner++) {
            out[corner][0] = (corner & 1) == 0 ? minX : maxX;
            out[corner][1] = (corner & 2) == 0 ? minY : maxY;
            out[corner][2] = (corner & 4) == 0 ? minZ : maxZ;
        }
        return out;
    }

    /** 差分结果：画面内总变化、ROI 内变化、ROI 外背景漂移与 ROI 面积。 */
    public record Diff(int changed, int total, int inRoi, int outside, int roiPixels) {}

    /**
     * 逐像素差分：任一通道差 ≥ {@code channelDelta} 记为该像素变化；同时分别统计 ROI 内/外，
     * ROI 外的显著漂移说明背景/镜头不受控，该对照不能用来给主体归因。
     *
     * <p>{@code exclude} 是必须从"主体 ROI"里扣掉的矩形（通常是施法者自身在屏幕上的投影框）：
     * 演员在相邻两帧仍会移动，其像素变化不得被算成作者美术，也不能靠"隐藏演员"从证据里消失。
     */
    public static Diff diff(int[] live, int[] clean, int width, int height, int channelDelta, int[] roi, int[] exclude) {
        if (live == null || clean == null || width <= 0 || height <= 0) return new Diff(0, 0, 0, 0, 0);
        int x0 = 0, y0 = 0, x1 = width - 1, y1 = height - 1;
        if (roi != null && roi.length == 4) { x0 = roi[0]; y0 = roi[1]; x1 = roi[2]; y1 = roi[3]; }
        int ex0 = 0, ey0 = 0, ex1 = -1, ey1 = -1;
        if (exclude != null && exclude.length == 4) { ex0 = exclude[0]; ey0 = exclude[1]; ex1 = exclude[2]; ey1 = exclude[3]; }
        int changed = 0, inRoi = 0, outside = 0, roiPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (index >= live.length || index >= clean.length) break;
                boolean inside = x >= x0 && x <= x1 && y >= y0 && y <= y1;
                boolean actor = x >= ex0 && x <= ex1 && y >= ey0 && y <= ey1;
                if (inside && !actor) roiPixels++;
                int a = live[index], b = clean[index];
                int dr = Math.abs((a >> 16 & 255) - (b >> 16 & 255));
                int dg = Math.abs((a >> 8 & 255) - (b >> 8 & 255));
                int db = Math.abs((a & 255) - (b & 255));
                if (Math.max(dr, Math.max(dg, db)) < channelDelta) continue;
                changed++;
                // 演员像素在两侧统计里都不计：它既不是主体，也不是"背景漂移"。
                if (inside && !actor) inRoi++; else if (!inside && !actor) outside++;
            }
        }
        return new Diff(changed, width * height, inRoi, outside, roiPixels);
    }

    public static Diff diff(int[] live, int[] clean, int width, int height, int channelDelta, int[] roi) {
        return diff(live, clean, width, height, channelDelta, roi, null);
    }

    /** 全精度日志串：让"取整相同"的旧日志无法再冒充同一渲染状态。 */
    public static String describe(ShotState state) {
        if (state == null) return "null";
        return String.format(Locale.ROOT,
                "scene=%s frame=%d rev=%d dim=%s cam=%s layer=%s target=%s eye=(%s,%s,%s) yaw=%s pitch=%s "
                        + "fb=%dx%d win=%dx%d gui=%s bob=%s time=%s night=%s rain=%s mv=%s proj=%s",
                state.sceneId(), state.renderFrameId(), state.resourceRevision(), state.dimension(),
                state.cameraEntity(), state.layer(), state.targetIdentity(),
                hex(state.eyeX()), hex(state.eyeY()), hex(state.eyeZ()),
                Float.toHexString(state.yaw()), Float.toHexString(state.pitch()),
                state.fbWidth(), state.fbHeight(), state.windowWidth(), state.windowHeight(),
                state.guiScale(), state.bobView(), hex(state.gameTime()), state.night(), state.raining(),
                hash(state.modelView()), hash(state.projection()));
    }

    private static String hex(double value) { return Double.toHexString(value); }

    private static String hash(float[] matrix) {
        if (matrix == null || matrix.length != 16) return "none";
        long hash = 1125899906842597L;
        for (float value : matrix) hash = hash * 31 + Float.floatToIntBits(value);
        return Long.toHexString(hash);
    }
}
