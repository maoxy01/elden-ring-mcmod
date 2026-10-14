package com.dsh.eldencombat.effects;

/** Per-frame vertex admission. No entity removal or server state changes. */
public final class VfxFrameBudget {
    private int remaining = VfxBudget.MAX_FRAME_VERTICES, pending;
    public void begin(int candidates) { remaining = VfxBudget.MAX_FRAME_VERTICES; pending = Math.max(0, candidates); }
    public int mainBoxes(int requested) {
        pending = Math.max(0, pending - 1);
        int available = available();
        int selected = Math.min(requested, available / 24);
        remaining -= selected * 24;
        return selected;
    }
    public int sprites(int requested) {
        int available = available();
        int selected = Math.min(requested, available / 4);
        remaining -= selected * 4;
        return selected;
    }
    /** Programmatic silhouette panels submit four vertices each, like a strip. */
    public int panels(int requested) {
        int available = available();
        int selected = Math.min(requested, available / 4);
        remaining -= selected * 4;
        return selected;
    }
    public int remaining() { return remaining; }
    /** One authored effect owns its admission; it does not reserve a phantom legacy box draw. */
    public void beginArtwork() { pending = Math.max(0, pending - 1); }
    private int available() { return (int) Math.max(0L, remaining - Math.min((long) remaining, (long) pending * 24)); }
}
