package com.dsh.eldencombat.effects;

import java.util.ArrayList;
import java.util.List;

/** Original coarse, axis-aligned effect geometry; independent of the game and particle engine. */
public final class SkillEffectMesh {
    public enum Kind {
        GOLD_WAVE(0), FLAME_FRONT(1), GOLD_IMPACT(2), DEATH_SLASH(3), WATERFOWL(4),
        GRAVITY_PULL(5), GRAVITY_IMPACT(6), BLOOD_RITUAL(7), CURSED_SLASH(8),
        BLOOD_FLAME(9), NONE(10);

        private final int wireId;
        Kind(int wireId) { this.wireId = wireId; }
        public int wireId() { return wireId; }
        public static Kind fromWire(int id) {
            for (Kind kind : values()) if (kind.wireId == id) return kind;
            return NONE;
        }
    }

    public record Box(float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float alpha) {}
    public static final int MAX_BOXES = 96;

    private SkillEffectMesh() {}

    public static float radius(float input) { return Float.isFinite(input) ? Math.clamp(input, .25f, 6f) : 1f; }
    public static float progress(float age, int duration) {
        return Float.isFinite(age) ? Math.clamp(age / Math.clamp(duration, 1, 40), 0f, 1f) : 1f;
    }

    public static float opacity(float progress) {
        if (!Float.isFinite(progress) || progress >= 1) return 0;
        float p = Math.clamp(progress, 0, 1);
        float reveal = Math.clamp(p / .08f, 0, 1);
        float dissolve = Math.clamp((1 - p) / .3f, 0, 1);
        return (.25f + .75f * smooth(reveal)) * smooth(dissolve);
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    public static List<Box> build(Kind kind, float inputRadius, float inputProgress, int inputPhase) {
        return build(kind, inputRadius, inputProgress, inputPhase, inputProgress * 16f, 0);
    }

    public static List<Box> build(Kind kind, float inputRadius, float inputProgress, int inputPhase, float travel, int blockedLanes) {
        if (kind == Kind.NONE) return List.of();
        float r = radius(inputRadius), p = Float.isFinite(inputProgress) ? Math.clamp(inputProgress, 0f, 1f) : 1f;
        if (p >= 1) return List.of();
        int phase = Math.floorMod(inputPhase, 3);
        Mesh m = new Mesh(opacity(p));
        int GOLD = FxPalette.color("gold.mid"), PALE_GOLD = FxPalette.color("gold.highlight"),
                RED = FxPalette.color("crimson.mid"), DARK_RED = FxPalette.color("crimson.shadow"),
                PURPLE = FxPalette.color("gravity.mid"), SILVER = FxPalette.color("bone.mid"), ORANGE = FxPalette.color("flame.mid");
        switch (kind) {
            case GOLD_WAVE -> wave(m, r, p, false, travel, blockedLanes);
            case FLAME_FRONT -> wave(m, r, p, true, 0, blockedLanes);
            case GOLD_IMPACT, GRAVITY_IMPACT -> {
                impact(m, r, p, kind == Kind.GOLD_IMPACT ? GOLD : PURPLE, false);
            }
            case GRAVITY_PULL -> {
                impact(m, r, p, PURPLE, true);
            }
            case BLOOD_RITUAL -> {
                float size = r * .7f * (.8f + .2f * p);
                m.frame(size, .05f, .14f, RED, 1);
                m.frame(size * .74f, .1f, .07f, DARK_RED, .85f);
                for (int i = 0; i <= phase; i++) m.frame(.46f + i * .26f, 1.6f + i * .55f, .09f, RED, .85f);
                for (int i = 0; i < 7; i++) {
                    float y = 1.3f + i * .45f;
                    m.box(-.1f, y, -.1f, .1f, y + .36f, .1f, i % 2 == 0 ? RED : DARK_RED, .8f);
                }
                for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
                    float x = sx * size, z = sz * size;
                    m.box(x - .09f, .08f, z - .09f, x + .09f, .6f + .2f * phase, z + .09f, RED, .8f);
                    m.box(x - .3f, .06f, z - .035f, x + .3f, .12f, z + .035f, PALE_GOLD, .7f);
                }
            }
            case DEATH_SLASH, CURSED_SLASH -> {
                slash(m, r, p, phase, RED, kind == Kind.DEATH_SLASH);
            }
            case WATERFOWL -> {
                // Connected stepped ribbons, not a spherical shell or a smooth torus.
                for (int layer = 0; layer < 3; layer++) {
                    float y = .45f + layer * .55f;
                    float size = Math.min(2.2f, r) * 1.7f * (.65f + layer * .1f);
                    int mirror = (phase + layer) % 2 == 0 ? 1 : -1;
                    float[] xs = {-size, -size, -size * .5f, -size * .5f, size * .5f, size * .5f, size, size};
                    float[] zs = {-.3f, .5f, .5f, .9f, .9f, .5f, .5f, -.3f};
                    for (int i = 0; i < xs.length - 1; i++) {
                        m.bar(xs[i] * mirror, y, zs[i], xs[i + 1] * mirror, y, zs[i + 1], .12f, FxPalette.color("water.mid"), .3f);
                        m.bar(xs[i] * mirror, y, zs[i], xs[i + 1] * mirror, y, zs[i + 1], .045f, SILVER, .9f - layer * .12f);
                    }
                }
            }
            case BLOOD_FLAME -> {
                float size = Math.min(2, r);
                for (int i = 0; i < 9; i++) {
                    float x = (i - 4) * size / 4;
                    float h = .25f + ((i + phase) % 3) * .17f + (1 - p) * .3f;
                    m.box(x - .13f, .05f, -.18f, x + .13f, h, .18f, RED, .8f);
                    m.box(x - .065f, .06f, -.1f, x + .065f, h * .65f, .1f, ORANGE, .85f);
                }
            }
        }
        return List.copyOf(m.boxes);
    }

    private static void wave(Mesh m, float r, float p, boolean flame, float travel, int blockedLanes) {
        int GOLD = FxPalette.color("gold.mid"), PALE_GOLD = FxPalette.color("gold.highlight"),
                DARK_RED = FxPalette.color("crimson.shadow"), ORANGE = FxPalette.color("flame.mid");
        int count = WaveFront.LANES;
        float width = 2 * r / count;
        for (int i = 0; i < count; i++) {
            if ((blockedLanes & (1 << i)) != 0) continue;
            float x = -r + i * width;
            float z = flame ? -.12f * (Math.abs(i - 7) / 3) : -(float) WaveFront.laneLag(i, travel);
            if (flame) {
                float height = .65f + (i % 3) * .28f + .12f * (1 - p);
                m.box(x, .03f, z - .23f, x + width, height, z + .15f, DARK_RED, .6f);
                m.box(x + width * .15f, .04f, z - .1f, x + width * .85f, height * .8f, z + .1f, ORANGE, .95f);
                m.box(x + width * .32f, .06f, z - .04f, x + width * .68f, height * .45f, z + .07f, GOLD, .85f);
            } else {
                float height = .45f + .6f * (1 - (float) Math.abs(WaveFront.laneCenter(i)));
                m.box(x, .06f, z - .1f, x + width + .005f, height, z + .04f, GOLD, .4f);
                m.box(x, height - .08f, z - .12f, x + width + .005f, height + .03f, z + .08f, PALE_GOLD, 1);
                m.box(x, .055f, z - .35f, x + width + .005f, .11f, z + .08f, GOLD, .6f);
                // The bright core touches the crest/foot instead of floating behind the wave.
                m.box(x, .1f, z - .055f, x + width + .005f, height - .04f, z + .045f, PALE_GOLD, .58f);
                if (i > 0 && (blockedLanes & (1 << (i - 1))) == 0) {
                    float previousZ = -(float) WaveFront.laneLag(i - 1, travel);
                    if (Math.abs(previousZ - z) > .01f) {
                        float previousHeight = .45f + .6f * (1 - (float) Math.abs(WaveFront.laneCenter(i - 1)));
                        float joinHeight = Math.min(height, previousHeight);
                        m.box(x - .03f, .06f, Math.min(z, previousZ) - .06f, x + .03f, joinHeight,
                                Math.max(z, previousZ) + .04f, GOLD, .32f);
                        m.box(x - .04f, joinHeight - .08f, Math.min(z, previousZ) - .08f, x + .04f, joinHeight + .03f,
                                Math.max(z, previousZ) + .08f, PALE_GOLD, .9f);
                    }
                }
            }
        }
    }

    private static boolean footprint(int x, int z) {
        int noise = Math.floorMod(x * 17 + z * 31 + x * z * 7, 7);
        float edge = 3.95f + (noise - 3) * .11f;
        return x * x + z * z <= edge * edge;
    }

    private static boolean rim(int x, int z) {
        return footprint(x, z) && (!footprint(x - 1, z) || !footprint(x + 1, z)
                || !footprint(x, z - 1) || !footprint(x, z + 1));
    }

    private static void impact(Mesh m, float r, float p, int color, boolean pull) {
        float expand = pull ? 1 - .18f * p : .5f + .5f * Math.min(1, p * 4);
        float cell = r * expand / 4.7f;
        // A deterministic broken disk rim: no smooth circles, and no per-frame random flicker.
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            if (!rim(x, z)) continue;
            float x0 = (x - .5f) * cell, z0 = (z - .5f) * cell;
            float lift = .04f + (pull ? .03f : .18f * (1 - p)) * (1 + Math.floorMod(x + z, 3));
            m.box(x0, .025f, z0, x0 + cell * .95f, lift + .04f, z0 + cell * .95f, FxPalette.color("stone.mid"), .85f);
            m.box(x0 + cell * .12f, lift + .045f, z0 + cell * .12f,
                    x0 + cell * .85f, lift + .075f, z0 + cell * .85f, color, .9f);
        }
        // Connected, stepped radial fissures, independently varied in length.
        for (int ray = 0; ray < 6; ray++) {
            double angle = ray * Math.PI / 3 + .19;
            float x = 0, z = 0;
            for (int step = 0; step < 3; step++) {
                float nx = (float) Math.round(Math.cos(angle) * (step + 1)) * cell;
                float nz = (float) Math.round(Math.sin(angle) * (step + 1)) * cell;
                m.bar(x, .045f, z, nx, .045f, z, Math.max(.012f, cell * .12f), color, .8f);
                if (nz != z) m.bar(nx, .045f, z, nx, .045f, nz, Math.max(.012f, cell * .12f), color, .8f);
                x = nx; z = nz;
            }
        }
    }

    public static List<Box> debris(Kind kind, float inputRadius, float inputProgress) {
        if (kind != Kind.GOLD_IMPACT && kind != Kind.GRAVITY_IMPACT && kind != Kind.GRAVITY_PULL) return List.of();
        float p = Float.isFinite(inputProgress) ? Math.clamp(inputProgress, 0, 1) : 1;
        if (p >= 1) return List.of();
        float radius = radius(inputRadius) / 4.7f;
        List<Box> result = new ArrayList<>();
        int piece = 0;
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            if (!rim(x, z) || (piece++ % 2) != 0) continue;
            boolean pull = kind == Kind.GRAVITY_PULL;
            float spread = pull ? 1 - .4f * p : .52f + .45f * p;
            float size = Math.min(.6f, radius * (.7f + piece % 3 * .16f)) * (1 - .85f * p);
            float y = .06f + 4 * p * (1 - p) * (pull ? .85f : .45f + piece % 3 * .18f);
            float cx = x * radius * spread, cz = z * radius * spread;
            result.add(new Box(cx - size / 2, y, cz - size / 2,
                    cx + size / 2, y + size * .65f, cz + size / 2, FxPalette.color("stone.highlight"), 1));
        }
        return List.copyOf(result);
    }

    private static void slash(Mesh m, float r, float p, int phase, int color, boolean shadow) {
        int GOLD = FxPalette.color("gold.mid"), PALE_GOLD = FxPalette.color("gold.highlight"), DARK_RED = FxPalette.color("crimson.shadow");
        float size = Math.min(2.6f, r) * (shadow ? 1.45f : 1), z = .85f + .15f * p;
        int mirror = phase % 2 == 0 ? 1 : -1;
        for (int i = 0; i < 10; i++) {
            float x = (-1 + i * .2f) * size, next = x + size * .2f;
            float y = color == GOLD ? 1f : .4f + i * .17f;
            float w = shadow ? .11f : .065f;
            if (shadow) m.bar(x * mirror, y, z, next * mirror, y, z, .22f, DARK_RED, .55f);
            m.bar(x * mirror, y, z - .03f, next * mirror, y, z - .03f, w, color, .95f);
            int core = color == GOLD ? PALE_GOLD : FxPalette.color("crimson.highlight");
            m.bar(x * mirror, y, z - .04f, next * mirror, y, z - .04f, w * .35f, core, .9f);
            if (i < 9 && color != GOLD) {
                m.bar(next * mirror, y, z, next * mirror, y + .17f, z, w, color, .95f);
                m.bar(next * mirror, y, z - .01f, next * mirror, y + .17f, z - .01f, w * .35f, core, .9f);
            }
        }
    }

    private static final class Mesh {
        final List<Box> boxes = new ArrayList<>();
        final float fade;
        Mesh(float fade) { this.fade = fade; }
        void box(float x0, float y0, float z0, float x1, float y1, float z1, int color, float alpha) {
            if (boxes.size() >= MAX_BOXES) throw new IllegalStateException("Skill mesh budget exceeded");
            boxes.add(new Box(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1),
                    Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), color, alpha * fade));
        }
        void bar(float x0, float y0, float z0, float x1, float y1, float z1, float w, int color, float alpha) {
            box(Math.min(x0, x1) - w / 2, Math.min(y0, y1) - w / 2, Math.min(z0, z1) - w / 2,
                    Math.max(x0, x1) + w / 2, Math.max(y0, y1) + w / 2, Math.max(z0, z1) + w / 2, color, alpha);
        }
        void frame(float half, float y, float w, int color, float alpha) {
            // Four touching bars; no detached corners and no coplanar overlapping faces.
            w = Math.min(w, half * .5f);
            box(-half, y, -half, half, y + .06f, -half + w, color, alpha);
            box(-half, y, half - w, half, y + .06f, half, color, alpha);
            box(-half, y, -half + w, -half + w, y + .06f, half - w, color, alpha);
            box(half - w, y, -half + w, half, y + .06f, half - w, color, alpha);
        }
    }
}
