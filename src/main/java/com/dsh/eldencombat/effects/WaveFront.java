package com.dsh.eldencombat.effects;

/** Shared, quantized propagation profile for the golden wave's visual and authoritative hit front. */
public final class WaveFront {
    public static final int LANES = 16;
    public static final double EDGE_SPEED_FACTOR = .55;
    public static final double GRID = .125;
    public static final double FORMATION_DISTANCE = 4;
    private WaveFront() {}

    public static double speedFactor(double normalizedLateral) {
        if (!Double.isFinite(normalizedLateral)) return EDGE_SPEED_FACTOR;
        double u = Math.clamp(Math.abs(normalizedLateral), 0, 1);
        return 1 - (1 - EDGE_SPEED_FACTOR) * u * u;
    }

    public static double lag(double normalizedLateral, double centerDistance) {
        if (!Double.isFinite(centerDistance)) return 0;
        // Relative offsets stop growing once the front has formed; every lane then translates together.
        double behind = Math.clamp(centerDistance, 0, FORMATION_DISTANCE) * (1 - speedFactor(normalizedLateral));
        return Math.floor(behind / GRID) * GRID;
    }

    public static double radius(double baseRadius, double maxRadius, double centerDistance) {
        if (!Double.isFinite(centerDistance) || maxRadius <= baseRadius) return baseRadius;
        double formation = Math.clamp(centerDistance / FORMATION_DISTANCE, 0, 1);
        return baseRadius + (maxRadius - baseRadius) * formation;
    }

    public static double laneCenter(int lane) { return -1 + (lane + .5) * 2 / LANES; }
    public static int lane(double lateral, double radius) {
        if (!Double.isFinite(lateral) || !Double.isFinite(radius) || radius <= 0) return LANES / 2;
        return Math.clamp((int) Math.floor((lateral / radius + 1) * .5 * LANES), 0, LANES - 1);
    }

    public static double laneLag(int lane, double centerDistance) { return lag(laneCenter(lane), centerDistance); }
}
