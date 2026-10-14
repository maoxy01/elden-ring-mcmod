package com.dsh.eldencombat.client.grace;

/** Pixel geometry for the pack's square minimap; labels and frame are not map area. */
public final class MinimapSize {
    private MinimapSize() {}

    public static int squareSide(int originalHeight, int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return originalHeight;
        // Match JourneyMap 6.0.9's horizontal rectangle truncation before taking its area.
        int originalWidth = (int) (originalHeight * (screenWidth * 1.0 / screenHeight));
        return Math.max(1, (int) Math.round(Math.sqrt((double) originalWidth * originalHeight * 2.0 / 7.0)));
    }
}
