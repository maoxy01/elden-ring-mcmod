package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.effects.FxArtwork;
import net.minecraft.world.phys.AABB;

/**
 * 波的客户端保守可视包围（纯数学，无 Minecraft 客户端依赖，可直接单测）。
 *
 * <p>至少联合：实体旧 culling box、前/当前 base 位置、lane 半径与滞后范围、每个 lane 平面的作者
 * offset/height/anchor/scale，以及地面支撑射线可能高度。只用角点推算，不发射射线、不加载区块；
 * 不修改实体真实 boundingBox，服务端命中逻辑与之无关。
 */
public final class WaveVisualBounds {
    /** 无 artwork 声明时的保守作者高度与锚点（现有火墙 3.5 格、anchorV=0.90625）。 */
    public static final double DEFAULT_HEIGHT = 3.5, DEFAULT_ANCHOR_V = 0.90625;
    /** 地面支撑射线窗口（与 ArtworkRenderer / WaveArtworkVisibility 一致）。 */
    public static final double SUPPORT_UP = 1.5, SUPPORT_DOWN = 2.5;
    private WaveVisualBounds() {}

    /**
     * @param baseX/baseY/baseZ   本 tick 起点（旧位置，用于覆盖"上一帧的支撑"）
     * @param currentX/…/currentZ 本 tick 当前位置
     * @param dirX/dirZ           传播方向（内部归一化；零向量按 +Z 处理）
     * @param radius              波半径（横向半宽）
     * @param travel              波已推进距离（用于前/后 base 覆盖）
     * @param art                 作者平面几何；{@code null} 时用 {@link #DEFAULT_HEIGHT}/{@link #DEFAULT_ANCHOR_V}
     * @param fallback            实体自身 culling box（通常 {@code getBoundingBoxForCulling()}）
     */
    public static AABB of(double baseX, double baseY, double baseZ,
                          double currentX, double currentY, double currentZ,
                          double dirX, double dirZ, double radius, double travel,
                          FxArtwork art, AABB fallback) {
        AABB box = fallback == null ? new AABB(baseX - 1, baseY - 1, baseZ - 1, baseX + 1, baseY + 1, baseZ + 1)
                : fallback.inflate(0.5);
        double[] dir = normalized(dirX, dirZ);
        double r = Math.max(radius, 0.0);
        double t = Math.max(Math.abs(travel), 0.0);
        // 前/当前 base 位置 + 两侧 lane 半径（lane 横向轴 = 方向左转 90°，与 FxArtwork.lanes 同一约定）。
        double minY = Math.min(baseY, currentY), maxY = Math.max(baseY, currentY);
        for (int anchor = 0; anchor < 4; anchor++) {
            double ax = anchor == 0 ? baseX : anchor == 1 ? currentX : anchor == 2 ? baseX - dir[0] * t : baseX + dir[0] * t;
            double az = anchor == 0 ? baseZ : anchor == 1 ? currentZ : anchor == 2 ? baseZ - dir[1] * t : baseZ + dir[1] * t;
            box = box.minmax(new AABB(ax - r, minY, az - r, ax + r, maxY, az + r));
        }
        double height = art == null ? DEFAULT_HEIGHT : art.height();
        double scale = 1.0;
        if (art != null) for (float candidate : art.scales()) scale = Math.max(scale, candidate);
        height *= scale;
        double offsetY = art == null ? 0.0 : art.y();
        double anchorV = art == null ? DEFAULT_ANCHOR_V : art.anchorV();
        // 支撑射线窗口：起点与当前位置的地面都可能落在各自 y-2.5 .. y+1.5 之间，再叠作者垂直范围。
        // 逐位置各自求上下界（坡道时两端高度不同，不能只取 min/max 后再套一次窗口）。
        double bottomBase = baseY - SUPPORT_DOWN + offsetY + (anchorV - 1) * height;
        double bottomCurrent = currentY - SUPPORT_DOWN + offsetY + (anchorV - 1) * height;
        double topBase = baseY + SUPPORT_UP + offsetY + anchorV * height;
        double topCurrent = currentY + SUPPORT_UP + offsetY + anchorV * height;
        box = box.minmax(new AABB(baseX - r, Math.min(bottomBase, bottomCurrent), baseZ - r,
                baseX + r, Math.max(topBase, topCurrent), baseZ + r));
        return box.inflate(0.5);
    }

    private static double[] normalized(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        if (!(length > 1.0E-6)) return new double[] { 0, 1 };
        return new double[] { x / length, z / length };
    }
}
