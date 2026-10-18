package com.dsh.eldencombat;

import com.dsh.eldencombat.effects.VfxDrawPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V2 主路径调度判据：**"接管了但这一帧 0 提交"绝不等于"没接管"**。
 *
 * <p>旧实现的缺陷正是把这两件事混成一个布尔值（`!submitted()`），于是作者路径有意隐藏的片段
 * 会被不受同一份可见性约束的旧绘制路径重新画出来——统筹复核也点名过"零提交触发 legacy 存在绕过新裁剪的分支"。
 * 这些测试把三态（NOT_HANDLED / DRAWN / SUPPRESSED）与九个原因钉死。
 */
class VfxDrawPlanTest {

    @Test void decideUsesPriorityOrderAndOnlyNoDeclarationIsUnhandled() {
        // 未声明：唯一允许回落兼容主路径的分支
        var undeclared = VfxDrawPlan.decide(false, true, false, true, false);
        assertFalse(undeclared.handles());
        assertSame(VfxDrawPlan.Reason.NO_DECLARATION, undeclared.reason());

        // 生命周期外与诊断抑制都"已接管"：必须压住旧路径，而不是还给旧路径
        var invalid = VfxDrawPlan.decide(true, false, false, true, false);
        assertFalse(invalid.handles());
        assertSame(VfxDrawPlan.Reason.INVALID_TIME, invalid.reason());
        var filtered = VfxDrawPlan.decide(true, true, false, true, true);
        assertFalse(filtered.handles());
        assertSame(VfxDrawPlan.Reason.DIAGNOSTIC_FILTER, filtered.reason());

        // 声明成立时按 CORE → 缺资源 → 作者贴片 顺序选一条（且只选一条）
        assertSame(VfxDrawPlan.Reason.CORE_OUTLINE, VfxDrawPlan.decide(true, true, true, true, false).reason());
        assertSame(VfxDrawPlan.Reason.CORE_OUTLINE, VfxDrawPlan.decide(true, true, true, false, false).reason());
        assertSame(VfxDrawPlan.Reason.RESOURCE_OUTLINE, VfxDrawPlan.decide(true, true, false, false, false).reason());
        assertEquals(VfxDrawPlan.Route.OUTLINE, VfxDrawPlan.decide(true, true, false, false, false).route());
        var sprite = VfxDrawPlan.decide(true, true, false, true, false);
        assertEquals(VfxDrawPlan.Route.SPRITE, sprite.route());
        assertSame(VfxDrawPlan.Reason.AUTHOR_SPRITE, sprite.reason());
    }

    @Test void notHandledIsTheOnlyLegacyEligibleOutcome() {
        assertTrue(VfxDrawPlan.finish(VfxDrawPlan.decide(false, true, false, true, false), 0, 0, false, false)
                .legacyEligible());
        assertTrue(VfxDrawPlan.Plan.unhandled().legacyEligible());
        // 其余任何定稿都不得让旧路径复活
        assertFalse(VfxDrawPlan.finish(VfxDrawPlan.decide(true, false, false, true, false), 0, 0, false, false)
                .legacyEligible());
        assertFalse(VfxDrawPlan.finish(VfxDrawPlan.decide(true, true, false, true, true), 0, 0, false, false)
                .legacyEligible());
        assertFalse(VfxDrawPlan.finish(VfxDrawPlan.decide(true, true, false, true, false), 5, 0, true, false)
                .legacyEligible());
        assertFalse(VfxDrawPlan.finish(VfxDrawPlan.decide(true, true, false, true, false), 0, 0, true, true)
                .legacyEligible());
    }

    @Test void zeroSubmissionIsSuppressedWithTheRealReason() {
        var sprite = VfxDrawPlan.decide(true, true, false, true, false);
        // 有候选但被预算拒绝：NO_BUDGET（不是"没接管"）
        var refused = VfxDrawPlan.finish(sprite, 0, 0, true, true);
        assertSame(VfxDrawPlan.Disposition.SUPPRESSED, refused.disposition());
        assertSame(VfxDrawPlan.Reason.NO_BUDGET, refused.reason());
        assertFalse(refused.submitted());
        // 候选被可见性筛掉（没有候选）：NO_VISIBLE_LANE
        assertSame(VfxDrawPlan.Reason.NO_VISIBLE_LANE, VfxDrawPlan.finish(sprite, 0, 0, false, false).reason());
        // 有候选、没被预算拒，却一个顶点没提交：同样按可见性收口，绝不回落旧路径
        assertSame(VfxDrawPlan.Reason.NO_VISIBLE_LANE, VfxDrawPlan.finish(sprite, 0, 0, true, false).reason());
        // 抑制类原因原样保留
        assertSame(VfxDrawPlan.Reason.INVALID_TIME,
                VfxDrawPlan.finish(VfxDrawPlan.decide(true, false, false, true, false), 0, 0, true, false).reason());
    }

    @Test void drawnKeepsTheRealCountsAndNeverCountsOutlineAsSprite() {
        var plan = VfxDrawPlan.finish(VfxDrawPlan.decide(true, true, false, true, false), 16, 0, true, false);
        assertSame(VfxDrawPlan.Disposition.DRAWN, plan.disposition());
        assertSame(VfxDrawPlan.Reason.AUTHOR_SPRITE, plan.reason());
        assertEquals(16, plan.spriteQuads());
        assertEquals(0, plan.outlinePanels());
        assertEquals(16, plan.total());

        var outline = VfxDrawPlan.finish(VfxDrawPlan.decide(true, true, false, false, false), 0, 12, true, false);
        assertEquals(0, outline.spriteQuads());
        assertEquals(12, outline.outlinePanels());

        // 负数计数按 0 处理：不能靠"负提交"变成 DRAWN 之外的状态，也不能把轮廓算成贴片
        var clamped = VfxDrawPlan.finish(VfxDrawPlan.decide(true, true, false, true, false), -5, 0, false, false);
        assertSame(VfxDrawPlan.Disposition.SUPPRESSED, clamped.disposition());
        assertEquals(0, clamped.spriteQuads());
    }

    @Test void finishToleratesNullDecision() {
        var plan = VfxDrawPlan.finish(null, 3, 3, true, true);
        assertSame(VfxDrawPlan.Disposition.NOT_HANDLED, plan.disposition());
        assertTrue(plan.legacyEligible());
    }

    @Test void laneMasksUseOriginalIdsAndIgnoreBitsAboveSixteen() {
        // 过滤后的列表序号不能冒充原始 lane ID：mask 位按原始编号，超出的位必须被裁掉
        assertEquals(0b1, VfxDrawPlan.combineMask(0x1_0000, 0b1));
        assertEquals(0b1001, VfxDrawPlan.combineMask(0b1000, 0b1));
        assertTrue(VfxDrawPlan.laneHidden(0b1, 0));
        assertTrue(VfxDrawPlan.laneHidden(1 << 15, 15));
        assertFalse(VfxDrawPlan.laneHidden(0b1, 1));
        assertFalse(VfxDrawPlan.laneHidden(0xFFFF, -1));
        assertFalse(VfxDrawPlan.laneHidden(0xFFFF, VfxDrawPlan.LANES));
    }

    @Test void blocksLaneOnlyForRealRises() {
        // 缓坡与整格台阶（抬升 ≤1 格）不得被误判成永久遮挡
        assertFalse(VfxDrawPlan.blocksLane(81.0, 81.0, VfxDrawPlan.LATCH_MIN_RISE));
        assertFalse(VfxDrawPlan.blocksLane(81.5, 81.0, VfxDrawPlan.LATCH_MIN_RISE));
        assertFalse(VfxDrawPlan.blocksLane(82.0, 81.0, VfxDrawPlan.LATCH_MIN_RISE), "恰好 1 格不算被挡");
        assertTrue(VfxDrawPlan.blocksLane(82.01, 81.0, VfxDrawPlan.LATCH_MIN_RISE));
        assertTrue(VfxDrawPlan.blocksLane(85.0, 81.0, VfxDrawPlan.LATCH_MIN_RISE));
        // 非法数值一律不锁存（宁可不挡，也不要因为 NaN 把整条 lane 永久关掉）
        assertFalse(VfxDrawPlan.blocksLane(Double.NaN, 81.0, VfxDrawPlan.LATCH_MIN_RISE));
        assertFalse(VfxDrawPlan.blocksLane(82.0, Double.POSITIVE_INFINITY, VfxDrawPlan.LATCH_MIN_RISE));
        assertFalse(VfxDrawPlan.blocksLane(82.0, 81.0, Double.NaN));
    }

    @Test void planFactoriesStayConsistent() {
        assertSame(VfxDrawPlan.Reason.HIDDEN, VfxDrawPlan.Plan.hidden().reason());
        assertSame(VfxDrawPlan.Disposition.SUPPRESSED, VfxDrawPlan.Plan.hidden().disposition());
        assertFalse(VfxDrawPlan.Plan.suppressed(VfxDrawPlan.Reason.NO_VISIBLE_LANE).submitted());
        assertEquals(1.0, VfxDrawPlan.LATCH_MIN_RISE, 1.0E-9, "锁存阈值是 1 格（整格台阶不锁）");
    }
}
