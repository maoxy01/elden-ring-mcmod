package com.dsh.eldencombat.server.skills;

/** Refresh replaces duration without stacking another damage pool. */
public final class DeathMark {
    private long expires;
    private long nextPulse;
    private float remaining;

    public DeathMark(long tick, int duration, float budget) { refresh(tick, duration, budget); }
    public void refresh(long tick, int duration, float budget) {
        expires = tick + Math.min(60, Math.max(1, duration));
        nextPulse = tick + 20;
        remaining = Math.max(remaining, Math.max(0, budget));
    }
    public float pulse(long tick) {
        if (tick > expires || tick < nextPulse || remaining <= 0) return 0;
        nextPulse = tick + 20;
        float amount = Math.min(1, remaining);
        remaining -= amount;
        return amount;
    }
    public boolean expired(long tick) { return tick > expires || remaining <= 0; }
    public float remaining() { return remaining; }
    public long expires() { return expires; }
}
