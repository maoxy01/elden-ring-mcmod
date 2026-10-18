package com.dsh.eldencombat.effects;

import java.util.List;

/** Submission limits, not a GPU timing or overdraw measurement. */
public final class VfxBudget {
    public static final int MAX_BOXES = 96, MAX_QUADS = MAX_BOXES * 6, MAX_VERTICES = MAX_QUADS * 4;
    public static final int MAX_DEBRIS = 12, MAX_FRAME_VERTICES = 30000;
    public enum Lod { FULL, REDUCED, CORE, HIDDEN }
    public record Counts(int boxes, int quads, int vertices, int sprites, int debris) {}
    private VfxBudget() {}
    public static Lod lod(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared > 128 * 128) return Lod.HIDDEN;
        if (distanceSquared > 48 * 48) return Lod.CORE;
        if (distanceSquared > 16 * 16) return Lod.REDUCED;
        return Lod.FULL;
    }
    /** 一次波更新的纯几何快照：每个 lane 的支撑/未知状态与地面高度，供客户端表现逐片绘制。 */
    public record LaneSupport(float groundY, boolean supported, boolean unknown) {}

    public static Counts validate(SkillEffectMesh.Kind kind, List<SkillEffectMesh.Box> boxes,
                                  List<SkillSlashSprites.Strip> sprites, List<SkillEffectMesh.Box> debris) {
        if (boxes.size() > MAX_BOXES || debris.size() > MAX_DEBRIS
                || sprites.size() > (kind == SkillEffectMesh.Kind.WATERFOWL ? SkillSlashSprites.MAX_STRIPS
                        : kind == SkillEffectMesh.Kind.GOLD_WAVE ? SkillSlashSprites.MAX_WAVE_STRIPS : 6))
            throw new IllegalArgumentException("VFX budget exceeded: " + kind);
        for (var b : boxes) box(b);
        for (var b : debris) box(b);
        for (var s : sprites) {
            for (float n : new float[]{s.x(), s.y(), s.z(), s.width(), s.height(), s.roll(), s.alpha()}) finite(n);
            if (s.width() <= 0 || s.height() <= 0 || s.alpha() < 0 || s.alpha() > 1 || s.frame() < 0 || s.frame() > 15)
                throw new IllegalArgumentException("Degenerate VFX strip");
            // Every strip names the material route it samples; a strip without one has no artwork to trust.
            if (s.route() == null || s.route().isBlank())
                throw new IllegalArgumentException("VFX strip without a material route");
        }
        return new Counts(boxes.size(), boxes.size() * 6, boxes.size() * 24 + sprites.size() * 4, sprites.size(), debris.size());
    }

    /** Programmatic silhouettes submit four vertices per panel; the shape builder owns the panel limits. */
    public static int outlineVertices(List<SkillOutlineShapes.Panel> panels) {
        return SkillOutlineShapes.vertexCount(panels);
    }
    private static void box(SkillEffectMesh.Box b) {
        for (float n : new float[]{b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1(), b.alpha()}) finite(n);
        if (b.x1() <= b.x0() || b.y1() <= b.y0() || b.z1() <= b.z0() || b.alpha() < 0 || b.alpha() > 1)
            throw new IllegalArgumentException("Degenerate VFX box");
    }
    private static void finite(float n) { if (!Float.isFinite(n)) throw new IllegalArgumentException("Nonfinite VFX coordinate"); }

    /** Keep a square ritual's first four sides; distribute the remaining samples over its extent. */
    public static int index(int slot, int count, int selected, boolean square) {
        if (selected >= count) return slot;
        int prefix = square && selected >= 4 ? 4 : 0;
        if (slot < prefix) return slot;
        return prefix + (slot - prefix) * (count - prefix) / (selected - prefix);
    }

    public static int faceMask(SkillEffectMesh.Box b, float eyeX, float eyeY, float eyeZ, float alphaScale) {
        boolean inside = eyeX >= b.x0() && eyeX <= b.x1() && eyeY >= b.y0() && eyeY <= b.y1()
                && eyeZ >= b.z0() && eyeZ <= b.z1();
        if (b.alpha() * alphaScale < .999f || inside) return 63;
        return (eyeZ <= b.z0() ? 1 : 0) | (eyeZ >= b.z1() ? 2 : 0)
                | (eyeX <= b.x0() ? 4 : 0) | (eyeX >= b.x1() ? 8 : 0)
                | (eyeY >= b.y1() ? 16 : 0) | (eyeY <= b.y0() ? 32 : 0);
    }
}
