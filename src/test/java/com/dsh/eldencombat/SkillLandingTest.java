package com.dsh.eldencombat;

import com.dsh.eldencombat.server.skills.SkillLanding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4 跃起落地统一兜底：六个分支的判定顺序与"只结算一次"。
 *
 * <p>这些分支现实里很难手工复现（低天花板、悬崖、落水、超时），但一旦漏掉就是卡在空中或穿地形，
 * 所以必须用纯逻辑单测把分支表钉死。
 */
class SkillLandingTest {

    /** 可脚本化的假世界：按 tick 序列喂给判定器。 */
    private static final class FakeProbe implements SkillLanding.Probe {
        double y = 100;
        boolean onGround;
        boolean groundContact = true;
        boolean inLiquid;
        double deltaY = 0.5;
        double worldBottom = -64;
        boolean ceilingBlocked;

        @Override public double y() { return y; }
        @Override public boolean onGround() { return onGround; }
        @Override public boolean groundContact() { return groundContact; }
        @Override public boolean inLiquid() { return inLiquid; }
        @Override public double deltaY() { return deltaY; }
        @Override public double worldBottom() { return worldBottom; }
        @Override public boolean ceilingBlocked() { return ceilingBlocked; }
    }

    @Test void keepsFlyingWhileAirborne() {
        SkillLanding landing = new SkillLanding(100);
        FakeProbe probe = new FakeProbe();
        probe.y = 101;
        assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe));
        probe.y = 102;
        assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe));
        assertFalse(landing.resolved());
        assertEquals(2, landing.ticks());
        assertEquals(102, landing.highestY(), 1.0e-6);
    }

    @Test void normalLandingSettlesOnce() {
        SkillLanding landing = new SkillLanding(100);
        FakeProbe probe = new FakeProbe();
        probe.y = 101;
        landing.advance(probe);
        probe.onGround = true;
        probe.y = 100;
        assertEquals(SkillLanding.Outcome.LANDED, landing.advance(probe));
        assertTrue(landing.resolved());
        // 再问一次必须还是同一个结论（落地伤害不会被结算第二次）
        assertEquals(SkillLanding.Outcome.LANDED, landing.advance(probe));
        assertEquals(SkillLanding.Outcome.LANDED, landing.advance(probe));
        assertFalse(SkillLanding.cancelsImpact(SkillLanding.Outcome.LANDED));
    }

    @Test void lowCeilingIsNotTerminalAndSwitchesToFalling() {
        SkillLanding landing = new SkillLanding(100);
        FakeProbe probe = new FakeProbe();
        probe.y = 102;
        probe.ceilingBlocked = true;
        probe.deltaY = -0.1;
        assertEquals(SkillLanding.Outcome.LOW_CEILING, landing.advance(probe));
        assertFalse(landing.resolved(), "低天花板只是转入下落，不是结算分支");
        // 转入下落后仍然要能正常落地
        probe.y = 101.5;
        landing.advance(probe);
        probe.onGround = true;
        probe.y = 101;
        assertEquals(SkillLanding.Outcome.LANDED, landing.advance(probe));
    }

    @Test void liquidCancelsImpactAndSettlesImmediately() {
        SkillLanding landing = new SkillLanding(70);
        FakeProbe probe = new FakeProbe();
        probe.y = 64;
        probe.inLiquid = true;
        assertEquals(SkillLanding.Outcome.LIQUID, landing.advance(probe));
        assertTrue(landing.resolved());
        assertTrue(SkillLanding.cancelsImpact(SkillLanding.Outcome.LIQUID));
        assertFalse(SkillLanding.returnsToStart(SkillLanding.Outcome.LIQUID));
    }

    @Test void voidBranchReturnsPlayerToStart() {
        SkillLanding landing = new SkillLanding(-50);
        FakeProbe probe = new FakeProbe();
        probe.worldBottom = -64;
        probe.y = -60;
        assertEquals(SkillLanding.Outcome.VOID, landing.advance(probe));
        assertTrue(landing.resolved());
        assertTrue(SkillLanding.cancelsImpact(SkillLanding.Outcome.VOID));
        assertTrue(SkillLanding.returnsToStart(SkillLanding.Outcome.VOID));
    }

    @Test void fallingInsideWorldIsNotTreatedAsVoid() {
        SkillLanding landing = new SkillLanding(40);
        FakeProbe probe = new FakeProbe();
        probe.worldBottom = -64;
        probe.y = 30;
        assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe), "世界底部之上仍然是在飞");
        probe.y = -30;
        assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe),
                "接近基岩层仍不算虚空（VOID_MARGIN 之内才判）");
        probe.y = -64 + SkillLanding.VOID_MARGIN - 1;
        assertEquals(SkillLanding.Outcome.VOID, landing.advance(probe));
    }

    @Test void timeoutForceSettles() {
        SkillLanding landing = new SkillLanding(200, 5);
        FakeProbe probe = new FakeProbe();
        probe.y = 200;
        for (int i = 0; i < 4; i++) {
            assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe), "第 " + (i + 1) + " tick");
        }
        assertEquals(SkillLanding.Outcome.TIMEOUT, landing.advance(probe));
        assertTrue(landing.resolved());
        assertFalse(SkillLanding.cancelsImpact(SkillLanding.Outcome.TIMEOUT), "超时是强制结算，不是取消");
        assertFalse(SkillLanding.returnsToStart(SkillLanding.Outcome.TIMEOUT));
    }

    @Test void priorityLiquidBeatsGroundAndVoid() {
        SkillLanding landing = new SkillLanding(80);
        FakeProbe probe = new FakeProbe();
        probe.y = -64 + 1;      // 同时也是"虚空"范围
        probe.inLiquid = true;  // 而且落在水里
        probe.onGround = true;
        assertEquals(SkillLanding.Outcome.LIQUID, landing.advance(probe), "落水优先于落地与虚空");
    }

    @Test void zeroTimeoutIsClampedInsteadOfSettlingInstantly() {
        SkillLanding landing = new SkillLanding(100, 0);
        FakeProbe probe = new FakeProbe();
        assertEquals(1, landing.timeoutTicks());
        assertEquals(SkillLanding.Outcome.TIMEOUT, landing.advance(probe));
    }

    /**
     * 回归：**起跳冲量发出后服务端还会"报地上"几个 tick**（玩家位置与 onGround 都来自客户端上报）。
     * 这时若接受 onGround 为落地，落地伤害与落地特效（黄金碎击的地面碎裂）就会在起跳点、人还在上升前
     * 触发——玩家在空中看到脚下已经裂开。所以"离地"必须先被观察到。
     */
    @Test void staleOnGroundRightAfterTakeoffIsNotALanding() {
        SkillLanding landing = new SkillLanding(100);
        FakeProbe probe = new FakeProbe();
        probe.onGround = true;   // 服务端仍在用客户端上一帧的报值
        probe.y = 100;           // 位置也还没更新
        assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe),
                "冲量已发出但还没离地：这不能算落地");
        assertFalse(landing.departed());
        // 同一个移动包里"位置上去了"与"onGround=false"是一起来的（服务端不会只更新其中一个）
        probe.onGround = false;
        probe.y = 101.5;         // 真的开始上升
        assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe));
        assertTrue(landing.departed());
        probe.onGround = true;   // 真的落地
        probe.y = 100;
        assertEquals(SkillLanding.Outcome.LANDED, landing.advance(probe));
    }

    /** 冲量被顶住时有界收束，但不能白得一次落地伤害或地裂。 */
    @Test void stalledTakeoffCancelsImpactInsteadOfLanding() {
        SkillLanding landing = new SkillLanding(100, 60);
        FakeProbe probe = new FakeProbe();
        probe.onGround = true;
        probe.y = 100;
        for (int i = 0; i < SkillLanding.NO_TAKEOFF_TICKS - 1; i++) {
            assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe), "第 " + (i + 1) + " tick");
        }
        assertEquals(SkillLanding.Outcome.TAKEOFF_FAILED, landing.advance(probe));
        assertFalse(landing.departed(), "从没真的离地：这是冲量失败，不是飞行");
        assertTrue(landing.resolved());
        assertTrue(SkillLanding.cancelsImpact(landing.outcome()));
        probe.onGround = false;
        probe.y = 102;
        assertEquals(SkillLanding.Outcome.TAKEOFF_FAILED, landing.advance(probe), "迟到的移动包不能补发冲击");
    }

    @Test void groundFlagWithoutPhysicalSupportDoesNotTriggerImpact() {
        SkillLanding landing = new SkillLanding(100);
        FakeProbe probe = new FakeProbe();
        probe.onGround = false;
        probe.groundContact = false;
        probe.y = 101;
        landing.advance(probe);
        probe.onGround = true;
        probe.y = 100.5;
        assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe));
        assertFalse(landing.resolved(), "空中的旧落地标志不能触发地裂");
        probe.y = 100;
        probe.groundContact = true;
        assertEquals(SkillLanding.Outcome.LANDED, landing.advance(probe));
    }

    @Test void genuineTakeoffBeforeDeadlineCanStillLandAfterDeadline() {
        SkillLanding landing = new SkillLanding(100);
        FakeProbe probe = new FakeProbe();
        probe.onGround = true;
        for (int i = 1; i < SkillLanding.NO_TAKEOFF_TICKS; i++) landing.advance(probe);
        probe.onGround = false;
        probe.groundContact = false;
        probe.y = 100.5;
        assertEquals(SkillLanding.Outcome.AIRBORNE, landing.advance(probe));
        probe.onGround = true;
        probe.groundContact = true;
        probe.y = 100;
        assertEquals(SkillLanding.Outcome.LANDED, landing.advance(probe));
    }
}
