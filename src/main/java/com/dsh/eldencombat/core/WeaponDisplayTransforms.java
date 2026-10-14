package com.dsh.eldencombat.core;

import com.dsh.eldencombat.core.geom.Box;
import org.joml.Matrix4f;

/** Display fitting is separate from the model's block units and main-grip origin. */
public final class WeaponDisplayTransforms {
    public enum View { GUI, FIXED, GROUND, FIRST_PERSON, THIRD_PERSON, HEAD, NONE }

    // Diamond sword's brown grip occupies x=2..4, y=11..13 in the 16px sprite.
    // The guard near (8,8) is not the palm anchor. Texture Y is inverted in model space.
    public static final float ICON_GRIP_X = 3.5f / 16f;
    public static final float ICON_GRIP_Y = 1f - 12.5f / 16f;
    public static final float ICON_GRIP_Z = 0.5f;
    /** 原版剑贴图里刀身的主轴方向：图标平面内 45°（离 +Y 偏 45°，朝 +X）。 */
    public static final float ICON_BLADE_DEGREES = -45f;

    private WeaponDisplayTransforms() {}

    public static Matrix4f matrix(Box bounds, View view, boolean leftHand) {
        return matrix(bounds, view, leftHand, 1f);
    }

    public static Matrix4f matrix(Box bounds, View view, boolean leftHand, float heldScale) {
        return matrix(bounds, view, leftHand, heldScale, 0, 0, 0);
    }

    public static Matrix4f matrix(Box bounds, View view, boolean leftHand, float heldScale,
                                  float gripX, float gripY, float gripZ) {
        return matrix(bounds,view,leftHand,heldScale,gripX,gripY,gripZ,ICON_GRIP_X,ICON_GRIP_Y,ICON_GRIP_Z);
    }

    public static org.joml.Vector3f palmAnchor(Matrix4f display, Matrix4f referenceDisplay) {
        var reference = referenceDisplay.transformPosition(new org.joml.Vector3f(ICON_GRIP_X,ICON_GRIP_Y,ICON_GRIP_Z));
        return new Matrix4f(display).invert().transformPosition(reference);
    }

    public static Matrix4f matrix(Box bounds, View view, boolean leftHand, float heldScale,
                                  float gripX, float gripY, float gripZ, float anchorX, float anchorY, float anchorZ) {
        float w = (float) (bounds.maxX() - bounds.minX());
        float h = (float) (bounds.maxY() - bounds.minY());
        float d = (float) (bounds.maxZ() - bounds.minZ());
        float extent = Math.max(.001f, Math.max(w, Math.max(h, d)));
        var matrix = new Matrix4f();
        if (view == View.FIRST_PERSON || view == View.THIRD_PERSON) {
            // **不要再加自己的手持角度**：原版 ItemRenderer 已经施加了物品模型 display 块里的
            // 手持变换（weapon_placeholder.json = 原版 item/handheld，也就是钻石剑的握法）。
            //
            // 这一层要解决的只有一个问题：让网格**占据图标格的方式**和原版剑贴图一致。
            // 原版 ItemRenderer 在调用 BEWLR 之前已经做过 display 变换 **和 translate(-0.5,-0.5,-0.5)**，
            // 所以这里拿到的坐标系就是"图标格 [0,1]³"（原版 generated 物品元素 from(0,0,..) to(16,16,..)
            // 的那个空间）。原版剑贴图的画法是：护手在像素 (8,8)=图标中心、刀身在平面内斜 45° 指向右上、
            // 柄朝左下角。我们的网格约定（33 件统一）是"原点是握把、刀身 +Y、厚度在 Z"，
            // 于是照原版握法 = 握把挪到原版柄部锚点 + 刀身绕 Z 逆向转 45° + 逐件比例缩放。
            //
            // 历史 bug（"剑型武器握持不对"）的第二个根因就在这里：把握把放到了 (0,-0.30,0)，
            // 漏算了 ItemRenderer 的 translate(-0.5,...)，握把实际落到图标格 (-0.5,-0.8,-0.5)，
            // 也就是手下方一格多——看起来就是"剑竖着飘在肩后"。
            return matrix.translate(anchorX, anchorY, anchorZ)
                    .rotateZ((float) Math.toRadians(ICON_BLADE_DEGREES))
                    .scale(heldScale).translate(-gripX, -gripY, -gripZ);
        }
        if (view == View.GUI) {
            matrix.rotateXYZ((float) Math.toRadians(15), (float) Math.toRadians(-25), (float) Math.toRadians(-35));
            float projectedWidth = Math.abs(matrix.m00()) * w + Math.abs(matrix.m10()) * h + Math.abs(matrix.m20()) * d;
            float projectedHeight = Math.abs(matrix.m01()) * w + Math.abs(matrix.m11()) * h + Math.abs(matrix.m21()) * d;
            matrix.scale(.88f / Math.max(.001f, Math.max(projectedWidth, projectedHeight)));
        } else {
            float target = view == View.GROUND ? .5f : .9f;
            matrix.scale(target / extent);
        }
        return matrix.translate((float) -(bounds.minX() + bounds.maxX()) / 2,
                (float) -(bounds.minY() + bounds.maxY()) / 2, (float) -(bounds.minZ() + bounds.maxZ()) / 2);
    }
}

