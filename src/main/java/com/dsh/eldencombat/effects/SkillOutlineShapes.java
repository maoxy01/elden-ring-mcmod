package com.dsh.eldencombat.effects;

import java.util.ArrayList;
import java.util.List;

/**
 * Programmatic pixel silhouettes for a routed artwork that is not loaded, and for core LOD where binding
 * the atlas is not worth the vertices. Stepped, grid-aligned panels: a recognizable arc, crescent or
 * ground front instead of crossing translucent boxes, and never the shared explosion frames.
 *
 * <p>Geometry only, no Minecraft type and no randomness: the same inputs always produce the same panels,
 * so the samples can be unit-tested and budgeted without starting the game. Every panel is authored in its
 * own plane; the caller applies {@link FxSlashRoute#planeYaw()} and {@link FxSlashRoute#planePitch()}.
 */
public final class SkillOutlineShapes {
    /** Half a pixel in texture terms: the smallest step a panel may take. */
    public static final float GRID = 1f / 16f;
    /** Panels per effect: an arc pair, a crescent pair plus its tail, or three rows of sixteen lanes. */
    public static final int MAX_PANELS = 48;
    private static final int ARC_STEPS = 9;
    private static final int LANES = WaveFront.LANES;
    private static final float HALF_SPAN = (float) Math.toRadians(62);

    /** One flat quad in the effect's own plane, before the route's plane rotation is applied. */
    public record Panel(float x, float y, float z, float width, float height, float roll, int rgb, float alpha) {}

    private SkillOutlineShapes() {}

    public static List<Panel> build(FxSlashRoute.Outline outline, int color, float inputRadius, float progress,
                                    int phase, float travel, int blockedLanes) {
        if (outline == null || outline == FxSlashRoute.Outline.BOXES) return List.of();
        if (!Float.isFinite(progress) || progress >= 1) return List.of();
        if ((color & ~0xFFFFFF) != 0) return List.of();
        float radius = SkillEffectMesh.radius(inputRadius), p = Math.clamp(progress, 0f, 1f);
        float fade = SkillEffectMesh.opacity(p);
        if (fade <= 0) return List.of();
        int mirror = Math.floorMod(phase, 2) == 0 ? 1 : -1;
        var panels = new ArrayList<Panel>(MAX_PANELS);
        switch (outline) {
            case ARC -> arc(panels, color, radius, p, fade, mirror, false);
            case CRESCENT -> arc(panels, color, radius, p, fade, mirror, true);
            case WAVE_FRONT -> wave(panels, color, radius, p, fade, travel, blockedLanes);
            default -> { }
        }
        return List.copyOf(panels);
    }

    /** Admission check and vertex accounting for the four vertices each panel submits. */
    public static int vertexCount(List<Panel> panels) {
        if (panels.size() > MAX_PANELS) throw new IllegalArgumentException("VFX outline budget exceeded: " + panels.size());
        for (var panel : panels) {
            for (float value : new float[]{panel.x(), panel.y(), panel.z(), panel.width(), panel.height(),
                    panel.roll(), panel.alpha()}) finite(value);
            if (panel.width() <= 0 || panel.height() <= 0 || panel.alpha() < 0 || panel.alpha() > 1
                    || (panel.rgb() & ~0xFFFFFF) != 0)
                throw new IllegalArgumentException("Degenerate VFX outline panel");
        }
        return panels.size() * 4;
    }

    /** Culling extent of a silhouette: the widest arc reaches further than the effect radius. */
    public static float cullingRadius(FxSlashRoute.Outline outline, float radius) {
        if (outline == FxSlashRoute.Outline.WAVE_FRONT) return radius + 1.5f;
        return outline == null || outline == FxSlashRoute.Outline.BOXES ? radius + 1 : Math.max(radius + 1, 5);
    }

    private static void arc(List<Panel> panels, int color, float radius, float progress, float fade,
                            int mirror, boolean crescent) {
        float arcRadius = Math.min(2.4f, radius) * 1.2f;
        float lift = Math.min(.9f, radius * .45f);
        float reveal = Math.clamp(progress / .38f, 0f, 1f);
        int bright = lighten(color, crescent ? .55f : .45f);
        for (int i = 0; i < ARC_STEPS; i++) {
            float t0 = i / (float) ARC_STEPS, t1 = (i + 1f) / ARC_STEPS, mid = (t0 + t1) * .5f;
            if (mid > reveal) break;
            float angle = -HALF_SPAN + (t0 + t1) * HALF_SPAN;
            float x = (float) Math.sin(angle) * arcRadius * mirror;
            float y = lift + (float) ((1 - Math.cos(angle)) * arcRadius * .35f);
            float chord = arcRadius * (2 * HALF_SPAN / ARC_STEPS) * 1.3f;
            float taper = (float) Math.sin(Math.PI * mid);
            float roll = stepAngle((float) Math.toDegrees(angle)) * mirror;
            float thickness = Math.max(2 * GRID, arcRadius * (crescent ? .05f + .14f * taper : .08f));
            add(panels, x, y, 0, chord, thickness, roll, color, fade * (.5f + .5f * taper));
            // A thinner bright cut sits on the concave side, so the body reads as a crescent and not a bar.
            add(panels, x, y + thickness * .5f, GRID, chord * .9f,
                    Math.max(GRID, thickness * (crescent ? .3f : .45f)), roll, bright, fade * (.55f + .45f * taper));
        }
        if (!crescent) return;
        // Short flame tail: three flecks that leave early instead of lingering over the whole life.
        for (int i = 0; i < 3; i++) {
            float t = .22f + i * .28f;
            if (t > reveal) break;
            float angle = -HALF_SPAN + t * 2 * HALF_SPAN;
            float x = (float) Math.sin(angle) * arcRadius * 1.22f * mirror;
            float y = lift + (float) ((1 - Math.cos(angle)) * arcRadius * .35f) + radius * .16f;
            float roll = stepAngle((float) Math.toDegrees(angle)) * mirror;
            float alpha = fade * Math.max(0f, 1 - progress * 2.4f) * .85f;
            add(panels, x, y, GRID * 2, 3 * GRID, 2 * GRID, roll, lighten(color, .3f), alpha);
            add(panels, x, y + 2 * GRID, GRID * 3, 2 * GRID, GRID, roll, lighten(color, .6f), alpha * .8f);
        }
    }

    private static void wave(List<Panel> panels, int color, float radius, float progress, float fade,
                             float travel, int blockedLanes) {
        float laneWidth = 2 * radius / LANES;
        float crest = .2f + .16f * (1 - progress);
        int bright = lighten(color, .45f), dim = darken(color, .35f);
        for (int lane = 0; lane < LANES; lane++) {
            // A blocked lane contributes nothing: one flat plane must not run through an obstacle.
            if ((blockedLanes & (1 << lane)) != 0) continue;
            float center = (float) WaveFront.laneCenter(lane);
            float shade = .45f + .55f * (1 - Math.abs(center));
            float x = -radius + (lane + .5f) * laneWidth;
            float z = -(float) WaveFront.laneLag(lane, travel);
            add(panels, x, 3 * GRID, z, laneWidth * .92f, crest, 0, bright, fade * shade);
            add(panels, x, 2 * GRID, z - 5 * GRID, laneWidth * .92f, crest * .6f, 0, color, fade * shade * .55f);
            add(panels, x, GRID, z - 10 * GRID, laneWidth * .86f, crest * .4f, 0, dim, fade * shade * .3f);
        }
    }

    private static void add(List<Panel> panels, float x, float y, float z, float width, float height,
                            float roll, int rgb, float alpha) {
        if (panels.size() >= MAX_PANELS || !(alpha > 0)) return;
        panels.add(new Panel(snap(x), snap(y), snap(z), Math.max(GRID, snap(width)), Math.max(GRID, snap(height)),
                roll, rgb, Math.clamp(alpha, 0f, 1f)));
    }

    private static float snap(float value) {
        if (!Float.isFinite(value)) return 0;
        return Math.min(64f, Math.max(-64f, Math.round(value / GRID) * GRID));
    }

    private static float stepAngle(float degrees) {
        if (!Float.isFinite(degrees)) return 0;
        return Math.clamp(Math.round(degrees / 15f) * 15f, -180f, 180f);
    }

    private static int lighten(int rgb, float amount) {
        float a = Math.clamp(amount, 0, 1);
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        return Math.round(r + (255 - r) * a) << 16 | Math.round(g + (255 - g) * a) << 8 | Math.round(b + (255 - b) * a);
    }

    private static int darken(int rgb, float amount) {
        float a = 1 - Math.clamp(amount, 0, 1);
        return Math.round((rgb >> 16 & 255) * a) << 16 | Math.round((rgb >> 8 & 255) * a) << 8 | Math.round((rgb & 255) * a);
    }

    private static void finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite VFX outline coordinate");
    }
}
