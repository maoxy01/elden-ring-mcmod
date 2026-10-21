package com.dsh.eldencombat.client;

/** GUI-scaled pixels, aligned to the native 182-pixel hotbar and stat stacks. */
public record CombatHudLayout(int staminaX, int staminaY, int fpX, int fpY, int jumpY) {
    public static final int STAMINA_WIDTH = 182;
    public static final int STAMINA_HEIGHT = 5;
    public static final int FP_WIDTH = 81;
    public static final int FP_HEIGHT = 6;

    public static CombatHudLayout at(int width, int height, int leftHeight, int rightHeight) {
        int top = Math.max(59, rightHeight + 10);
        return new CombatHudLayout(width / 2 - 91, height - 29,
                width / 2 + 10, Math.max(22, height - top), height - 37);
    }

    public int selectedItemLift(int nativeTextY, int textHeight) {
        return Math.max(0, nativeTextY + textHeight - (fpY - 12));
    }

    public static int filled(float current, float maximum, int width) {
        if (!Float.isFinite(current) || !Float.isFinite(maximum) || maximum <= 0) return 0;
        return (int) (Math.max(0, Math.min(1, current / maximum)) * width);
    }
}
