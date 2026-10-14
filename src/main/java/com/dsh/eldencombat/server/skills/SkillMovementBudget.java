package com.dsh.eldencombat.server.skills;

/** Tracks actual horizontal path length, not just displacement from the start. */
public final class SkillMovementBudget {
    private final double limit;
    private double travelled;

    public SkillMovementBudget(double limit) { this.limit = Math.max(0, limit); }

    public void record(double dx, double dz) { travelled += Math.hypot(dx, dz); }
    public double remaining() { return Math.max(0, limit - travelled); }
    public double travelled() { return travelled; }
    public double speed(double requested) { return Math.min(Math.max(0, requested), remaining()); }
    public boolean exceeded() { return travelled > limit + 0.35; }

    public static double turn(double current, double requested, double maximum) {
        double delta = Math.atan2(Math.sin(requested - current), Math.cos(requested - current));
        return current + Math.max(-maximum, Math.min(maximum, delta));
    }
}
