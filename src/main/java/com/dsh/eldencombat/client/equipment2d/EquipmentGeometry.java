package com.dsh.eldencombat.client.equipment2d;

/**
 * Art geometry for one own-sprite item, expressed in the single coordinate system the 2D route
 * emits into.
 *
 * <p>Contract DSH-EQUIPMENT-2D-IMPLEMENTATION-CONTRACT-20261015 §5, §10.3. There is exactly one
 * origin and one unit:
 *
 * <pre>
 *   emission space   b(x, y) = (x / W - 0.5, 0.5 - y / H, 0)        centred block coordinates
 *   canvas extent    [-0.5, 0.5] x [-0.5, 0.5]                       the whole sprite, one block
 *   grip             a = b(anchorX, anchorY)                         same space, same origin
 *   in-plane scale   s = worldLengthBlocks * W / sourceSpanPixels     uniform on X and Y
 * </pre>
 *
 * <p>Pixel coordinates are canvas-top-left based pixel centres, matching the art manifest; the Y
 * term flips because texture V grows downwards while model +Y grows upwards.
 *
 * <p>The blur between "model units" and "blocks" is what previously let the emitter draw
 * {@code [0, W/16] x [0, H/16]} quads — two blocks wide for a 32-pixel canvas — while the grip stayed
 * in centred 16-unit model coordinates. Both sides now read this one record.
 */
public record EquipmentGeometry(float canvasWidth, float canvasHeight,
                                float anchorX, float anchorY,
                                float worldLengthBlocks, float sourceSpanPixels) {

    public EquipmentGeometry {
        if (!(canvasWidth > 0f) || !(canvasHeight > 0f)) {
            throw new IllegalArgumentException("canvas must be positive: " + canvasWidth + "x" + canvasHeight);
        }
        if (!(sourceSpanPixels > 0f)) {
            throw new IllegalArgumentException("sourceSpanPixels must be positive: " + sourceSpanPixels);
        }
        if (!(worldLengthBlocks > 0f)) {
            throw new IllegalArgumentException("worldLengthBlocks must be positive: " + worldLengthBlocks);
        }
        if (anchorX < 0f || anchorX > canvasWidth || anchorY < 0f || anchorY > canvasHeight) {
            throw new IllegalArgumentException("anchor outside canvas: " + anchorX + "," + anchorY);
        }
    }

    public static EquipmentGeometry of(float canvasWidth, float canvasHeight, float anchorX, float anchorY,
                                       float worldLengthBlocks, float sourceSpanPixels) {
        return new EquipmentGeometry(canvasWidth, canvasHeight, anchorX, anchorY,
                worldLengthBlocks, sourceSpanPixels);
    }

    /** {@code 16 * x / W - 8} — the grip on X in centred model units, kept for the art audit trail. */
    public float anchorModelUnitsX() {
        return 16f * anchorX / canvasWidth - 8f;
    }

    /** {@code 8 - 16 * y / H} — the grip on Y in centred model units. */
    public float anchorModelUnitsY() {
        return 8f - 16f * anchorY / canvasHeight;
    }

    /** The grip in the emission space itself: {@code (x/W - 0.5, 0.5 - y/H, 0)}. */
    public float gripX() {
        return anchorX / canvasWidth - 0.5f;
    }

    public float gripY() {
        return 0.5f - anchorY / canvasHeight;
    }

    /** {@code s = worldLengthBlocks * W / sourceSpanPixels}; the applied uniform scale. */
    public float uniformXYScale() {
        return worldLengthBlocks * canvasWidth / sourceSpanPixels;
    }

    /**
     * The opaque pixel span implied by the emitted quad: the sprite occupies one block before the
     * display scale, so the measured span reproduces {@code worldLengthBlocks}. Used by the tests
     * that check the final emitted extent rather than re-deriving the formula.
     */
    public float emittedSpanBlocks() {
        return sourceSpanPixels / canvasWidth * uniformXYScale();
    }

    /** Emission-space X of a pixel centre. */
    public static float centredX(float pixel, float canvasWidth) {
        return pixel / canvasWidth - 0.5f;
    }

    /** Emission-space Y of a pixel centre. */
    public static float centredY(float pixel, float canvasHeight) {
        return 0.5f - pixel / canvasHeight;
    }
}
