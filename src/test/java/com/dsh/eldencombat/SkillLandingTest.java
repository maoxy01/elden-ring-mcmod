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
        boolean inLiquid;
        double deltaY = 0.5;
        double worldBottom = -64;
        boolean ceilingBlocked;

        @Override public double y() { return y; }
        @Override public boolean onGround() { return onGround; }
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
}
