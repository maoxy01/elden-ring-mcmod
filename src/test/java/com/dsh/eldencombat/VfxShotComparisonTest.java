package com.dsh.eldencombat;

import com.dsh.eldencombat.effects.VfxShotComparison;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V1 配对判据与像素差分的纯数学。
 *
 * <p>为什么这些断言重要：旧门禁从"同一段里所有效果帧 vs 后来一张干净帧"取最大值，从不检查两帧的真实
 * 相机/投影，于是镜头一动，地平线与背景的变化就被算成"作者主体"。这里把三类不变量钉死：
 * <ol>
 *   <li>能不能比（状态/眼位/朝向/投影误差），失配就**不产生分数**；</li>
 *   <li>世界点投影必须减去该帧真实眼位（关卡模型视图以相机为原点）；</li>
 *   <li>{@code changed} 是全屏原始计数，而 {@code inRoi}/{@code outside} 才扣除演员——
 *       门禁必须用后者（2026-10-17 固定夹具就是因为误用 {@code changed} 把演员位移算成背景漂移）。</li>
 * </ol>
 */
class VfxShotComparisonTest {

    /** 单位模型视图 + 单位投影：相机相对坐标直接当 NDC 用，便于手算期望像素。 */
    private static final float[] IDENTITY = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1};

    private static VfxShotComparison.ShotState state(String dimension, String camera, String target,
                                                     double eyeX, float yaw, float pitch) {
        return new VfxShotComparison.ShotState("scene", 7, 3, dimension, camera, "NORMAL", target,
                eyeX, 0, 0, yaw, pitch, IDENTITY.clone(), IDENTITY.clone(), 100, 100, 100, 100, 2.0,
                false, 6000, false, false);
    }

    private static VfxShotComparison.ShotState baseline() {
        return state("minecraft:overworld", "observer", "effect/7/0", 0, 0f, 0f);
    }

    @Test void projectionSubtractsTheEyeAndFlipsY() {
        var frame = baseline();
        double[] pixel = VfxShotComparison.projectWorld(frame, 0.5, 0.25, 100);
        assertNotNull(pixel);
        assertEquals(75.0, pixel[0], 1.0E-6, "x 正向 → 画面右侧");
        assertEquals(37.5, pixel[1], 1.0E-6, "y 正向 → 画面上方（像素 y 翻转）");
        // w ≤ 0（近平面之后）没有像素坐标
        float[] projection = IDENTITY.clone();
        projection[15] = 0;
        assertNull(VfxShotComparison.project(IDENTITY.clone(), projection, 0, 0, 0, 100, 100));
        // 视口非法同样返回 null，而不是抛异常
        assertNull(VfxShotComparison.project(IDENTITY.clone(), IDENTITY.clone(), 0, 0, 0, 0, 100));
    }

    @Test void identicalStatesAreComparableOnlyWithEnoughCorners() {
        var corners = VfxShotComparison.corners(0, 0, 0, 1, 1, 1);
        var verdict = VfxShotComparison.comparable(baseline(), baseline(), corners);
        assertTrue(verdict.comparable(), verdict.reason());
        // 只有一个角点可用时无法判定投影一致性 → 必须判不可比，而不是"误差 0 就比"
        var tooFew = VfxShotComparison.comparable(baseline(), baseline(), new double[][] { { 0, 0, 0 } });
        assertFalse(tooFew.comparable());
        assertTrue(tooFew.reason().contains("projection-error") || tooFew.reason().contains("no-roi-corners"),
                tooFew.reason());
        assertEquals(Double.POSITIVE_INFINITY,
                VfxShotComparison.maxPixelError(baseline(), baseline(), new double[][] { { 0, 0, 0 } }));
    }

    @Test void stateMismatchIsRejectedBeforeAnyScoring() {
        var corners = VfxShotComparison.corners(0, 0, 0, 1, 1, 1);
        var other = state("minecraft:the_nether", "observer", "effect/7/0", 0, 0f, 0f);
        assertEquals("dimension", VfxShotComparison.comparable(baseline(), other, corners).reason());
        var otherCamera = state("minecraft:overworld", "player", "effect/7/0", 0, 0f, 0f);
        assertEquals("camera-entity", VfxShotComparison.comparable(baseline(), otherCamera, corners).reason());
        var otherTarget = state("minecraft:overworld", "observer", "effect/8/0", 0, 0f, 0f);
        assertEquals("target", VfxShotComparison.comparable(baseline(), otherTarget, corners).reason());
        // 眼位/朝向的数值容差：1e-4 格与 1e-4 度仍在可比范围内（引擎收敛尾差），
        // 但整格移动必须拒绝（负例移动 6 格以上时正是靠这条判掉）
        var drifted = state("minecraft:overworld", "observer", "effect/7/0", 1.0E-4, 1.0E-4f, 0f);
        assertTrue(VfxShotComparison.comparable(baseline(), drifted, corners).comparable());
        var moved = state("minecraft:overworld", "observer", "effect/7/0", 6.0, 0f, 0f);
        var movedVerdict = VfxShotComparison.comparable(baseline(), moved, corners);
        assertFalse(movedVerdict.comparable());
        assertTrue(movedVerdict.reason().startsWith("eye-delta"), movedVerdict.reason());
    }

    @Test void roiIsClampedToTheFramebufferWithMargin() {
        var frame = baseline();
        int[] roi = VfxShotComparison.roiFromCorners(frame, VfxShotComparison.corners(0, 0, 0, 1, 1, 1), 100);
        assertNotNull(roi);
        assertEquals(0, roi[0]);
        assertEquals(0, roi[1]);
        assertEquals(99, roi[2], "ROI 必须裁到画面内");
        assertEquals(99, roi[3]);
        // 所有角点都在近平面之后 → 这一帧没有可用 ROI
        assertNull(VfxShotComparison.roiFromCorners(null, VfxShotComparison.corners(0, 0, 0, 1, 1, 1), 3));
    }

    /**
     * 回归（2026-10-17 固定夹具缺陷）：`changed` 是全屏原始计数，**不**扣除演员；
     * 想"扣掉演员只比背景"就必须用 `inRoi`（roi=null 时 inRoi 即"非演员区域的变化"）。
     * 当时门禁误用 `changed`，于是施法者自己的位移被算成"背景漂移"，注释里承诺的扣除从未生效。
     */
    @Test void actorPixelsAreExcludedFromRoiAndOutsideButNotFromChanged() {
        int width = 4, height = 4;
        int[] live = new int[width * height];
        int[] clean = new int[width * height];
        for (int i = 0; i < live.length; i++) { live[i] = 0x000000; clean[i] = 0x000000; }
        live[0] = 0xFFFFFF;                       // (0,0)：ROI 内 + 演员框内
        live[1 * width + 0] = 0xFFFFFF;           // (0,1)：ROI 内 + 演员框内
        live[0 * width + 1] = 0xFFFFFF;           // (1,0)：ROI 内 + 演员框外
        live[3 * width + 3] = 0xFFFFFF;           // (3,3)：ROI 外 + 演员框外
        int[] roi = { 0, 0, 2, 2 };
        int[] actor = { 0, 0, 0, 1 };
        var diff = VfxShotComparison.diff(live, clean, width, height, 24, roi, actor);
        assertEquals(4, diff.changed(), "changed 是全屏原始计数，演员像素也计入");
        assertEquals(1, diff.inRoi(), "只有演员框外的 ROI 内变化算主体");
        assertEquals(1, diff.outside(), "只有演员框外的 ROI 外变化算背景漂移");
        assertEquals(7, diff.roiPixels(), "ROI 面积里要扣掉演员像素");
        // 演员框盖住整屏：非演员区域的变化必须归零（这正是"只考核背景"的定义）
        var allActor = VfxShotComparison.diff(live, clean, width, height, 24, null, new int[] { 0, 0, 3, 3 });
        assertEquals(4, allActor.changed());
        assertEquals(0, allActor.inRoi());
        assertEquals(0, allActor.outside());
        assertEquals(0, allActor.roiPixels());
        // 低于通道阈值的差异不算变化
        int[] faint = clean.clone();
        faint[0] = 0x0A0A0A;
        assertEquals(0, VfxShotComparison.diff(faint, clean, width, height, 24, null, null).changed());
    }
}
