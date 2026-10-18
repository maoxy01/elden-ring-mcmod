package com.dsh.eldencombat.compat;

/** Implemented by the optional RollManager mixin, no client types on the common interface. */
public interface RollPrediction {
    void eldencombat$reject(int available, int cooldown, float progress);
}
