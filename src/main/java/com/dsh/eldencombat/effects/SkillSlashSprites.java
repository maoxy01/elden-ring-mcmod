package com.dsh.eldencombat.effects;

import java.util.ArrayList;
import java.util.List;
import com.dsh.eldencombat.effects.SkillEffectMesh.Kind;

/** Visual strips sampling atlas frames; no particles, hits or explosion logic. */
public final class SkillSlashSprites {
    /**
     * One quad. {@code frame} is a semantic step in 0..15 that the effect's material route resamples onto
     * its own frame count; {@code route} names that route, so a strip carries its own material selection
     * instead of every skill sharing one atlas entry.
     */
    public record Strip(float x, float y, float z, float width, float height, float roll,
                        int rgb, float alpha, int frame, String route) {}
    public static final int MAX_STRIPS = 12;
    /** The golden front draws one strip per open lane. */
    public static final int MAX_WAVE_STRIPS = WaveFront.LANES;
    private SkillSlashSprites() {}

    public static boolean supports(Kind kind) {
        return kind == Kind.WATERFOWL || kind == Kind.DEATH_SLASH || kind == Kind.CURSED_SLASH;
    }

    public static List<Strip> build(Kind kind, float radius, float progress, int phase) {
        if (!supports(kind) || !Float.isFinite(progress) || progress >= 1) return List.of();
        String route = FxSlashRoute.routeId(kind);
        float p = Math.max(0, progress), r = Math.min(2.6f, SkillEffectMesh.radius(radius));
        int mirror = Math.floorMod(phase, 2) == 0 ? 1 : -1;
        int count = kind == Kind.WATERFOWL ? 6 : 3;
        var strips = new ArrayList<Strip>(count * 2);
        for (int i = 0; i < count; i++) {
            float delay = i * (kind == Kind.WATERFOWL ? .055f : .035f);
            if (p < delay) continue;
            float t = (p - delay) / (1 - delay);
            float alpha = SkillEffectMesh.opacity(t);
            int frame = Math.min(15, (int) (t * 16));
            boolean wide = kind != Kind.CURSED_SLASH;
            float width = r * (wide ? 2.25f + .65f * Math.min(1, t * 5) : 1.25f + .35f * Math.min(1, t * 5));
            float x = mirror * (i - (count - 1) * .5f) * (wide ? .42f : .2f);
            float y = (wide ? .5f : .7f) + i * (kind == Kind.WATERFOWL ? .3f : wide ? .58f : .28f);
            float z = wide ? .4f + (i % 2) * .65f : .7f + (i % 2) * .2f;
            float roll = mirror * (kind == Kind.WATERFOWL ? (i % 2 == 0 ? 18 : -24) : -28);
            float height = kind == Kind.WATERFOWL ? .3f : kind == Kind.DEATH_SLASH ? .65f : .32f;
            height = Math.min(height, width * .16f);
            int outer = FxPalette.color(kind == Kind.WATERFOWL ? "bone.shadow" : kind == Kind.DEATH_SLASH ? "obsidian.shadow" : "cursed.mid");
            int core = FxPalette.color(kind == Kind.WATERFOWL ? "bone.highlight" : kind == Kind.DEATH_SLASH ? "death.highlight" : "cursed.highlight");
            strips.add(new Strip(x, y, z, width, height, roll, outer, alpha * .8f, frame, route));
            // Offset a thin bright cut on the dark/silver body; avoid a solid rectangular ribbon.
            strips.add(new Strip(x, y + .025f, z + .025f, width * .93f, height * .35f,
                    roll, core, alpha, Math.max(0, frame - 1), route));
        }
        return List.copyOf(strips);
    }

    /**
     * The golden wave front: one strip per open lane, each lagged by its own quantized profile, so a lane
     * that is blocked leaves a gap instead of being covered by one uncut plane. The semantic frame comes
     * from the wave's own progress, which keeps advancing after the front has formed.
     */
    public static List<Strip> buildWave(float inputRadius, float progress, int semanticFrame, float travel, int blockedLanes) {
        if (!Float.isFinite(progress) || progress >= 1) return List.of();
        String route = FxSlashRoute.routeId(Kind.GOLD_WAVE);
        if (route == null) return List.of();
        float p = Math.clamp(progress, 0f, 1f), fade = SkillEffectMesh.opacity(p);
        if (fade <= 0) return List.of();
        float r = Math.min(2.6f, SkillEffectMesh.radius(inputRadius));
        int frame = Math.clamp(semanticFrame, 0, FxSlashRoute.SEMANTIC_FRAMES - 1);
        float width = 2 * r / WaveFront.LANES;
        var strips = new ArrayList<Strip>(MAX_WAVE_STRIPS);
        for (int lane = 0; lane < WaveFront.LANES; lane++) {
            if ((blockedLanes & (1 << lane)) != 0) continue;
            float center = (float) WaveFront.laneCenter(lane);
            float shade = .45f + .55f * (1 - Math.abs(center));
            float height = .28f + .22f * (1 - Math.abs(center));
            strips.add(new Strip(-r + (lane + .5f) * width, .03f, -(float) WaveFront.laneLag(lane, travel),
                    width * .92f, height, 0, 0xFFFFFF, fade * shade, frame, route));
        }
        return List.copyOf(strips);
    }

    public static float cullingRadius(Kind kind, float radius) {
        // Maximum strip half-diagonal plus its offset is below five blocks at every phase.
        return kind == Kind.WATERFOWL || kind == Kind.DEATH_SLASH ? Math.max(radius + 1, 5)
                : kind == Kind.GOLD_WAVE ? radius + 1.5f : radius + 1;
    }
}
