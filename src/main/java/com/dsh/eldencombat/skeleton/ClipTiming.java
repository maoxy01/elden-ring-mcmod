package com.dsh.eldencombat.skeleton;

public final class ClipTiming {
    private ClipTiming() {}
    public static double frame(AnimationClip clip, double elapsed, int duration, int hitStart, int hitEnd, int fullCharge) {
        elapsed = Math.max(0, elapsed);
        if (clip.loop()) return elapsed;
        if (clip.id().equals("heavy_windup")) return Math.min(30, elapsed * 20 / Math.max(1, fullCharge));
        if (duration <= 0) return elapsed;
        int a = clip.hitStart(), b = clip.hitEnd();
        if (a >= 0 && b > a && hitStart > 0 && hitEnd > hitStart && duration > hitEnd) {
            if (elapsed < hitStart) return elapsed * a / hitStart;
            if (elapsed < hitEnd) return a + (elapsed - hitStart) * (b-a) / (hitEnd-hitStart);
            return Math.min(clip.lengthTicks(), b + (elapsed-hitEnd) * (clip.lengthTicks()-b) / (duration-hitEnd));
        }
        return Math.min(clip.lengthTicks(), elapsed * clip.lengthTicks() / duration);
    }
}
