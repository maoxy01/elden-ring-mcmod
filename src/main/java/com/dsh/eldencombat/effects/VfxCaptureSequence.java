package com.dsh.eldencombat.effects;

/** Counts complete captures, keeping both sides of a pair on the same sample number. */
public final class VfxCaptureSequence {
    private int completed;
    private boolean awaitingClean;

    public boolean awaitingClean() { return awaitingClean; }
    public int completed() { return completed; }

    public String sampleLabel(String label, int repeats) {
        return repeats > 1 ? label + "-" + (completed + 1) : label;
    }

    /** A paired capture is counted only after its clean reference is captured. */
    public boolean liveCaptured(int repeats, boolean paired) {
        if (awaitingClean) throw new IllegalStateException("Clean reference is still pending");
        if (paired) { awaitingClean = true; return false; }
        return ++completed >= repeats;
    }

    public boolean cleanCaptured(int repeats) {
        if (!awaitingClean) throw new IllegalStateException("No live capture to pair");
        awaitingClean = false;
        return ++completed >= repeats;
    }

    public void reset() { completed = 0; awaitingClean = false; }
}
