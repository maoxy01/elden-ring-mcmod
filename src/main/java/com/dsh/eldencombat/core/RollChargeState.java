package com.dsh.eldencombat.core;

/** Server admission mirror of Combat Roll 2.0.6's charge regeneration; never moves a player. */
public final class RollChargeState {
    private int available;
    private int cooldown;
    private float progress;
    private long rollingUntil = Long.MIN_VALUE;

    public void tick(int capacity, float increment, int cooldownTicks) {
        capacity = Math.max(0, capacity);
        if (available < capacity) {
            progress += Math.max(0, increment);
            if (progress >= cooldown) {
                available++;
                progress = Math.max(0, progress - cooldown);
                cooldown = Math.max(0, cooldownTicks);
            }
        }
        available = Math.min(available, capacity);
        if (available == capacity) progress = 0;
    }

    public boolean rolling(long tick) { return tick <= rollingUntil; }
    public boolean canStart(long tick) { return available > 0 && !rolling(tick); }
    public boolean start(long tick, int duration, int cooldownTicks) {
        if (!canStart(tick)) return false;
        available--;
        cooldown = Math.max(0, cooldownTicks);
        rollingUntil = tick + Math.max(0, duration);
        return true;
    }
    public int available() { return available; }
    public int cooldown() { return cooldown; }
    public float progress() { return progress; }
}
