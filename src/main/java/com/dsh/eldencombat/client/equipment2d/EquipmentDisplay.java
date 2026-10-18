package com.dsh.eldencombat.client.equipment2d;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Map;
import java.util.Set;

/**
 * The route's own {@code display} block: one explicit transform per display context.
 *
 * <p>Contract §10.3: the new route is the <em>only</em> owner of the item transform, so the values
 * have to be consumable exactly once and with the same semantics vanilla uses. This record applies
 * them the way {@code ItemTransform.apply} does:
 *
 * <pre>
 *   D = T(±tx, ty, tz) . rotationXYZ(rx, ±ry, ±rz) . S(sx, sy, sz)
 * </pre>
 *
 * with the sign flip applied for the left hand. That matters because the JSON values themselves are
 * stored <em>pre-mirror</em> (vanilla writes {@code thirdperson_lefthand} as the un-mirrored pose and
 * lets the renderer mirror it), so the candidate generator has to invert the mirror and the runtime
 * has to re-apply it. Keeping the two in step is what stops the left hand from being a guess.
 *
 * <p>{@code inheritance} names the context copied into any context this display does not declare, so
 * an unspecified context is a stated decision rather than an accidental identity.
 */
public record EquipmentDisplay(Map<String, Frame> contexts, String inheritance) {

    /** Vanilla names the left hand by suffix; the right-hand counterpart drops it. */
    private static final String LEFT_HAND_SUFFIX = "_lefthand";

    /** One context: translation in blocks (already divided by 16), rotation in degrees, scale unitless. */
    public record Frame(float[] rotation, float[] translation, float[] scale) {

        public Frame {
            if (rotation.length != 3 || translation.length != 3 || scale.length != 3) {
                throw new IllegalArgumentException("display context must hold three triples");
            }
            rotation = rotation.clone();
            translation = translation.clone();
            scale = scale.clone();
            for (float value : rotation) {
                requireFinite("rotation", value);
            }
            for (float value : translation) {
                requireFinite("translation", value);
            }
            for (float value : scale) {
                requireFinite("scale", value);
                if (value == 0f) {
                    throw new IllegalArgumentException("display scale must not be zero");
                }
            }
        }

        private static void requireFinite(String what, float value) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("display " + what + " is not finite: " + value);
            }
        }

        public static Frame identity() {
            return new Frame(new float[] {0f, 0f, 0f}, new float[] {0f, 0f, 0f}, new float[] {1f, 1f, 1f});
        }

        /** The single transform this context contributes, including the left-hand mirror. */
        public Matrix4f matrix(boolean leftHanded) {
            float sign = leftHanded ? -1f : 1f;
            Quaternionf rotation = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(this.rotation[0]),
                    (float) Math.toRadians(sign * this.rotation[1]),
                    (float) Math.toRadians(sign * this.rotation[2]));
            return new Matrix4f()
                    .translate(sign * translation[0], translation[1], translation[2])
                    .rotate(rotation)
                    .scale(scale[0], scale[1], scale[2]);
        }

        /** Applied exactly once; nothing else may touch the pose for a routed item. */
        public void apply(boolean leftHanded, PoseStack pose) {
            pose.mulPose(matrix(leftHanded));
        }
    }

    public EquipmentDisplay {
        contexts = Map.copyOf(contexts);
    }

    public boolean declares(String context) {
        return contexts.containsKey(context);
    }

    public Set<String> declared() {
        return contexts.keySet();
    }

    /**
     * Declared context, else the vanilla left-hand fallback, else {@code inheritance}, else GUI, else
     * identity — stated, never implicit.
     *
     * <p>The left-hand step reproduces {@code ItemTransforms.Deserializer}: an undeclared
     * {@code *_lefthand} context copies the stored {@code *_righthand} values, and the caller's
     * {@code leftHanded} mirroring turns that into the correct left-hand pose. That is deliberately
     * <em>before</em> {@code inheritance}, because a general fallback would have to be mirror-agnostic
     * and "copy fixed into thirdperson_lefthand" is not the pose vanilla would ever produce.
     */
    public Frame resolve(String context) {
        Frame direct = contexts.get(context);
        if (direct != null) {
            return direct;
        }
        if (context != null && context.endsWith(LEFT_HAND_SUFFIX)) {
            Frame rightHand = contexts.get(
                    context.substring(0, context.length() - LEFT_HAND_SUFFIX.length()) + "_righthand");
            if (rightHand != null) {
                return rightHand;
            }
        }
        Frame inherited = inheritance == null ? null : contexts.get(inheritance);
        if (inherited != null) {
            return inherited;
        }
        Frame gui = contexts.get("gui");
        return gui != null ? gui : Frame.identity();
    }

    public Frame resolve(ItemDisplayContext context) {
        return resolve(context.getSerializedName());
    }
}
