package com.dsh.eldencombat.client;

/** V0.2 GUI pixels; inputs are actual native stat stacks, including extra health/mount rows. */
public record CombatHudLayout(int staminaX, int staminaY, int fpX, int fpY, int selectedItemY) {
    public static final int BAR_WIDTH = 81, BAR_HEIGHT = 7, FILL_WIDTH = 79;
    public static final int STAT_LIFT = 4, FP_RESERVATION = 9;

    public static CombatHudLayout at(int width, int height, int leftStack, int rightSlot) {
        int fpY = Math.max(16, height - Math.max(63, leftStack));
        return new CombatHudLayout(width / 2 + 10, Math.max(16, height - Math.max(53, rightSlot)),
                width / 2 - 91, fpY, Math.max(2, fpY - 33));
    }

    public static boolean showAir(boolean eyesInWater, int air, int maximumAir) {
        // Native bubbles remain visible during air recovery after surfacing.
        return eyesInWater || air < maximumAir;
    }

    public static int filled(float current, float maximum, int width) {
        if (width <= 0 || !Float.isFinite(current) || !Float.isFinite(maximum) || maximum <= 0) return 0;
        return (int) (Math.max(0, Math.min(1, (double) current / maximum)) * width);
    }
}
