package com.dsh.eldencombat.effects;

import com.dsh.eldencombat.effects.SkillEffectMesh.Kind;
import com.google.gson.JsonObject;

/**
 * One routable 2D artwork: which frames it plays, whether the artwork is fixed in the skill's own space
 * or billboarded to the camera, its vertex tint, and what to draw when the artwork is not loaded.
 *
 * <p>Pure data and pure rules; no Minecraft client type appears here, so kind-to-route selection,
 * orientation defaults and frame sampling stay testable without starting the game. The manifest may
 * override orientation, plane, tint and outline; frame count and readiness are taken from the declared
 * frame list and the entry status so a route cannot claim art it does not have.
 *
 * <p>The plane is expressed the same way the entity render frame is: after the renderer applied
 * {@code -yaw}, local +Z points along the caster's facing and local +X to its left. A quad is authored in
 * its own XY plane; {@code planeYaw} spins it about local Y and {@code planePitch} tips it about local X
 * (0 = upright wall in front of the caster, 90 = flat on the ground). Roll stays an in-plane rotation of
 * the quad itself. Camera-facing routes are only for smoke and sparks, never for a main slash silhouette.
 */
public record FxSlashRoute(Orientation orientation, float planeYaw, float planePitch, int tint,
                           Outline outline, int outlineColor, int frameCount, boolean ready) {
    public enum Orientation {
        /** Fixed in the skill's own space: the quad follows the caster, not the camera. */
        SKILL_PLANE,
        /** World-space billboard: the quad follows the camera. Legacy and particle-like artwork only. */
        CAMERA
    }

    /**
     * Procedural silhouette drawn when a routed artwork is missing, and at core LOD where binding the
     * atlas is not worth the vertices. {@link #BOXES} keeps the old coarse box mesh as the fallback.
     */
    public enum Outline {
        /** Stepped silver-white arc; the waterfowl sample's degraded silhouette. */
        ARC,
        /** Dark crescent with a bright inner edge and a short flame tail; the destined-death sample. */
        CRESCENT,
        /** Lane-segmented golden ground front that honours blocked lanes; the wave-of-gold sample. */
        WAVE_FRONT,
        /** No programmatic silhouette: the coarse box mesh stays the fallback. */
        BOXES
    }

    /** The legacy interface samples sixteen semantic steps; routes resample them onto their own count. */
    public static final int SEMANTIC_FRAMES = 16;
    public static final int MAX_FRAMES = 64;

    // Draft sample defaults. They are declared so a route can be authored before the artwork exists, and
    // the manifest overrides each of them; the exact sample values still wait for the in-game look review.
    public static final FxSlashRoute WATERFOWL = new FxSlashRoute(Orientation.SKILL_PLANE, 0f, 90f, 0xFFFFFF,
            Outline.ARC, 0xE9FAFF, SEMANTIC_FRAMES, false);
    public static final FxSlashRoute DEATH_SLASH = new FxSlashRoute(Orientation.SKILL_PLANE, 0f, 45f, 0xFFFFFF,
            Outline.CRESCENT, 0xEC304D, SEMANTIC_FRAMES, false);
    public static final FxSlashRoute CURSED_SLASH = new FxSlashRoute(Orientation.SKILL_PLANE, 0f, 45f, 0xFFFFFF,
            Outline.ARC, 0xFF6673, SEMANTIC_FRAMES, false);
    public static final FxSlashRoute GOLD_WAVE = new FxSlashRoute(Orientation.SKILL_PLANE, 0f, 90f, 0xFFFFFF,
            Outline.WAVE_FRONT, 0xFFD044, SEMANTIC_FRAMES, false);
    /** Shared explosion frames, camera facing, tinted per strip by the palette: the untouched old look. */
    public static final FxSlashRoute LEGACY = new FxSlashRoute(Orientation.CAMERA, 0f, 0f, 0xFFFFFF,
            Outline.BOXES, 0xFFFFFF, SEMANTIC_FRAMES, false);

    public FxSlashRoute {
        if (orientation == null || outline == null) throw new IllegalArgumentException("FX route enum missing");
        if (frameCount < 1 || frameCount > MAX_FRAMES) throw new IllegalArgumentException("FX route frame count: " + frameCount);
        if (!Float.isFinite(planeYaw) || !Float.isFinite(planePitch)) throw new IllegalArgumentException("FX route plane is not finite");
        if ((tint & ~0xFFFFFF) != 0 || (outlineColor & ~0xFFFFFF) != 0) throw new IllegalArgumentException("FX route RGB range");
        planeYaw = clampDegrees(planeYaw);
        planePitch = clampDegrees(planePitch);
    }

    private static float clampDegrees(float value) { return Math.clamp(value, -180f, 180f); }

    public boolean cameraFacing() { return orientation == Orientation.CAMERA; }

    /** True when the route is allowed to replace the shared explosion artwork at all. */
    public boolean replacesSharedArtwork() { return ready; }

    /** True when a missing atlas sprite must be drawn as a programmatic silhouette, not as explosion frames. */
    public boolean usesOutline() { return ready && outline != Outline.BOXES; }

    public FxSlashRoute withFrames(int frames) {
        return new FxSlashRoute(orientation, planeYaw, planePitch, tint, outline, outlineColor, frames, ready);
    }

    public FxSlashRoute withReady(boolean value) {
        return new FxSlashRoute(orientation, planeYaw, planePitch, tint, outline, outlineColor, frameCount, value);
    }

    /**
     * Resamples one of the sixteen semantic steps onto this route's own frame count. Sixteen frames are the
     * identity, one frame is a single still and any other count covers its full range without stretching.
     */
    public int frameIndex(int semantic) {
        int clamped = Math.clamp(semantic, 0, SEMANTIC_FRAMES - 1);
        if (frameCount <= 1) return 0;
        int scaled = (int) Math.round(clamped * (frameCount - 1) / (double) (SEMANTIC_FRAMES - 1));
        return Math.clamp(scaled, 0, frameCount - 1);
    }

    /** Effect kind to manifest route id; null keeps the kind on the legacy shared route. */
    public static String routeId(Kind kind) {
        if (kind == null) return null;
        return switch (kind) {
            case WATERFOWL -> "waterfowl";
            case DEATH_SLASH -> "death_slash";
            case CURSED_SLASH -> "cursed_slash";
            case GOLD_WAVE -> "golden_wave";
            case GOLD_IMPACT -> "gold_impact";
            case FLAME_FRONT -> "flame_front";
            case GRAVITY_PULL -> "gravity_pull";
            case GRAVITY_IMPACT -> "gravity_impact";
            case BLOOD_RITUAL -> "blood_ritual";
            case BLOOD_FLAME -> "blood_flame";
            default -> null;
        };
    }

    /** Declared defaults for a manifest route id, or null when the id is not a routable effect. */
    public static FxSlashRoute defaultsForRoute(String routeId) {
        if (routeId == null) return null;
        return switch (routeId) {
            case "waterfowl" -> WATERFOWL;
            case "death_slash" -> DEATH_SLASH;
            case "cursed_slash" -> CURSED_SLASH;
            case "golden_wave" -> GOLD_WAVE;
            case "gold_impact", "gravity_pull", "gravity_impact", "blood_ritual" -> GOLD_WAVE;
            case "flame_front", "blood_flame" -> CURSED_SLASH;
            default -> null;
        };
    }

    public static FxSlashRoute defaults(Kind kind) { return defaultsForRoute(routeId(kind)); }

    /**
     * Reads the optional overrides of one manifest entry on top of the declared defaults. Unknown enum
     * values, malformed colours and non-numeric fields raise instead of silently keeping a wrong route.
     */
    public static FxSlashRoute parse(JsonObject entry, FxSlashRoute defaults) {
        if (entry == null || defaults == null) throw new IllegalArgumentException("FX route needs an entry and its defaults");
        Orientation orientation = entry.has("orientation")
                ? orientation(entry.get("orientation").getAsString()) : defaults.orientation();
        float yaw = defaults.planeYaw(), pitch = defaults.planePitch();
        if (entry.has("plane")) {
            JsonObject plane = entry.getAsJsonObject("plane");
            if (plane.has("yaw")) yaw = plane.get("yaw").getAsFloat();
            if (plane.has("pitch")) pitch = plane.get("pitch").getAsFloat();
        }
        int tint = entry.has("tint") ? color("tint", entry.get("tint").getAsString()) : defaults.tint();
        Outline outline = defaults.outline();
        int outlineColor = defaults.outlineColor();
        if (entry.has("outline")) {
            JsonObject shape = entry.getAsJsonObject("outline");
            if (shape.has("shape")) outline = outline(shape.get("shape").getAsString());
            if (shape.has("color")) outlineColor = color("outline color", shape.get("color").getAsString());
        }
        return new FxSlashRoute(orientation, yaw, pitch, tint, outline, outlineColor,
                defaults.frameCount(), defaults.ready());
    }

    public static Orientation orientation(String value) {
        return switch (require(value, "orientation").toLowerCase(java.util.Locale.ROOT)) {
            case "skill_plane" -> Orientation.SKILL_PLANE;
            case "camera" -> Orientation.CAMERA;
            default -> throw new IllegalArgumentException("Unknown FX route orientation: " + value);
        };
    }

    public static Outline outline(String value) {
        return switch (require(value, "outline shape").toLowerCase(java.util.Locale.ROOT)) {
            case "arc" -> Outline.ARC;
            case "crescent" -> Outline.CRESCENT;
            case "wave_front" -> Outline.WAVE_FRONT;
            case "boxes" -> Outline.BOXES;
            default -> throw new IllegalArgumentException("Unknown FX outline shape: " + value);
        };
    }

    public static int color(String field, String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("FX route " + field + ": " + value);
        return Integer.parseInt(value.substring(1), 16);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("FX route " + field + " is missing");
        return value;
    }
}
