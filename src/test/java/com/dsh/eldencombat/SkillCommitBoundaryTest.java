package com.dsh.eldencombat;

import com.dsh.eldencombat.server.skills.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 事务边界与中止语义的不变量（Runtime 启动失败如何退款、中止后能不能续段）。
 *
 * <p>为什么要有这一组：{@code SkillCasting} 在 Runtime 初始化失败时要退款，退款金额一旦
 * 不等于"实际扣减量"，就会变成一个刷 FP 的漏洞；中止（参数丢失 / 取消）之后如果追击窗口
 * 还能被消费，就会出现"取消后继续出伤害"。
 *
 * <p>全部断言只依赖纯逻辑类型（{@link SkillTransactions} / {@link SkillCastState} /
 * {@link PlayerFpState}），不启动 Minecraft，因此可以在任何机器上稳定复现；
 * 需要真实服务端/客户端的场景仍走 smoke 夹具。
 */
class SkillCommitBoundaryTest {

    private static final Map<String, RemembranceSkillSpec> SPECS = RemembranceSkillSpec.load();

    @Test
    void reportedChargeAlwaysEqualsTheActualDeduction() {
        for (var spec : SPECS.values()) {
            var fp = new PlayerFpState();
            var cast = new SkillCastState();
            float before = fp.fp();
            var outcome = SkillTransactions.commit(cast, fp, spec, 0, true);
            assertEquals(SkillTransactions.Result.STARTED, outcome.result(), spec.id());
            assertEquals(before - fp.fp(), outcome.charged(), 1e-4f,
                    spec.id() + "：事务报告的扣费必须等于实际扣减，否则回滚会退错金额");
        }
    }

    @Test
    void followupChargeIsReportedExactly() {
        var spec = SPECS.get("bloodboon_ritual");
        var fp = new PlayerFpState();
        var cast = new SkillCastState();
        SkillTransactions.commit(cast, fp, spec, 0, true);
        cast.enterStage(SkillCastState.Stage.ACTIVE, 6);
        cast.openFollowupWindow(10, 10);
        float before = fp.fp();
        var outcome = SkillTransactions.commit(cast, fp, spec, 11, true);
        assertEquals(SkillTransactions.Result.FOLLOWUP, outcome.result());
        assertEquals(before - fp.fp(), outcome.charged(), 1e-4f, "追击段同样要报准金额");
    }

    @Test
    void rejectedCommitChargesNothing() {
        var spec = SPECS.get("gold_breaker");
        var fp = new PlayerFpState();
        var cast = new SkillCastState();
        var outcome = SkillTransactions.commit(cast, fp, spec, 0, false);
        assertEquals(SkillTransactions.Result.REJECTED, outcome.result());
        assertEquals(0f, outcome.charged(), 1e-6f, "被拒绝的提交不能报告费用");
        assertEquals(100f, fp.fp(), 1e-6f);
        assertFalse(cast.isCasting(), "被拒绝的提交不能起手");
    }

    @Test
    void refundingReportedChargeRestoresNonFullBalance() {
        var spec = SPECS.get("destined_death");
        var fp = new PlayerFpState();
        fp.setFp(80f);
        var cast = new SkillCastState();
        float before = fp.fp();
        float spent = SkillTransactions.commit(cast, fp, spec, 0, true).charged();
        assertTrue(spent > 0f);
        fp.refund(spent);
        assertEquals(before, fp.fp(), 1e-4f, "单次退款必须恢复非满额余额，不能依赖上限掩盖超额退款");
    }

    @Test
    void insufficientFpRejectsFollowupWithoutConsumingItsWindow() {
        var spec = SPECS.get("bloodboon_ritual");
        var fp = new PlayerFpState();
        var cast = new SkillCastState();
        assertTrue(SkillTransactions.commit(cast, fp, spec, 0, true).accepted());
        cast.enterStage(SkillCastState.Stage.ACTIVE, 6);
        cast.openFollowupWindow(10, 10);
        int stage = cast.stageIndex();
        fp.setFp(0f);

        var outcome = SkillTransactions.commit(cast, fp, spec, 11, true);

        assertEquals(SkillTransactions.Result.REJECTED, outcome.result());
        assertEquals(0f, outcome.charged(), 1e-6f);
        assertEquals(0f, fp.fp(), 1e-6f);
        assertEquals(stage, cast.stageIndex());
        assertTrue(cast.followupOpen(11), "FP 不足不能消耗追击窗口");
    }

    @Test
    void unlimitedFpReportsZeroSoRollbackNeverMintsFp() {
        var spec = SPECS.get("gold_breaker");
        var fp = new PlayerFpState();
        fp.setUnlimitedForTesting(true);
        var cast = new SkillCastState();
        var outcome = SkillTransactions.commit(cast, fp, spec, 0, true);
        assertEquals(SkillTransactions.Result.STARTED, outcome.result());
        assertEquals(0f, outcome.charged(), 1e-6f, "无限 FP 下没有实际扣减，就必须报告 0");
        fp.refund(outcome.charged());
        assertEquals(fp.maxFp(), fp.fp(), 1e-6f);
    }

    @Test
    void abortClosesTheFollowupWindowAndRetiresTheOldCastId() {
        var spec = SPECS.get("starcaller_cry");
        var fp = new PlayerFpState();
        var cast = new SkillCastState();
        var started = SkillTransactions.commit(cast, fp, spec, 0, true);
        int castId = cast.castId();
        cast.enterStage(SkillCastState.Stage.ACTIVE, 8);
        cast.openFollowupWindow(14, 12);
        assertTrue(cast.followupOpen(20));

        cast.finish(); // 取消 / 中止 / 参数丢失统一走这里

        assertFalse(cast.followupOpen(20), "结束后追击窗口必须关闭");
        assertFalse(SkillTransactions.matchesRequest(cast, castId, 1), "旧 castId 的追击包随之作废");
        var again = SkillTransactions.commit(cast, fp, spec, 20, true);
        assertEquals(SkillTransactions.Result.STARTED, again.result(), "窗口外的按键只能是全新起手，不能续段");
        assertNotEquals(castId, cast.castId(), "新起手必须换 castId");
        assertEquals(100f - started.charged() - again.charged(), fp.fp(), 1e-4f);
    }

    @Test
    void clearCooldownOnlyTouchesTheNamedSkill() {        var fp = new PlayerFpState();
        fp.setCooldown("gold_breaker", 500);
        fp.setCooldown("wave_of_gold", 500);
        fp.clearCooldown("gold_breaker");
        assertFalse(fp.onCooldown("gold_breaker", 100), "启动失败的技能要能立刻重试");
        assertTrue(fp.onCooldown("wave_of_gold", 100), "不能顺手清掉别的技能的冷却");
        fp.clearCooldown(null);
        assertTrue(fp.onCooldown("wave_of_gold", 100));
    }

    /**
     * §2.6 待查项：{@code cursed_blood_slice} 首次追击的 stageIndex 与 costForStage 的对应关系。
     *
     * <p>核对方式刻意是**驱动真实状态机**（commit → enterStage(ACTIVE) → openFollowupWindow → commit），
     * 而不是自己给 {@code costForStage} 传一个索引——待查项问的正是"运行期实际传进去的是哪个索引"，
     * 传参测试会把这个前提当成已知，等于没查。
     *
     * <p>结论（本测例即为证据）：第一次进入 ACTIVE 时 {@code stageIndex} 已经是 1，
     * 所以首次追击走的是 {@code costForStage(1)} = {@code fpCostFollowup}，
     * **没有多收首段价**；不需要给索引加 +1。
     */
    @Test
    void firstFollowupChargesTheFollowupPriceNotTheFirstStagePrice() {
        var spec = SPECS.get("cursed_blood_slice");
        assertEquals(12f, spec.fpCost(), 1e-4f);
        assertEquals(8f, spec.fpCostFollowup(), 1e-4f);
        var fp = new PlayerFpState();
        var cast = new SkillCastState();

        var opening = SkillTransactions.commit(cast, fp, spec, 0, true);
        assertEquals(SkillTransactions.Result.STARTED, opening.result());
        assertEquals(spec.fpCost(), opening.charged(), 1e-4f, "首段收首段价");

        cast.enterStage(SkillCastState.Stage.ACTIVE, 5);
        assertEquals(1, cast.stageIndex(), "进入 ACTIVE 后段号必须已是 1（这正是索引问题的关键事实）");
        cast.openFollowupWindow(10, 10);
        float before = fp.fp();
        var followup = SkillTransactions.commit(cast, fp, spec, 12, true);

        assertEquals(SkillTransactions.Result.FOLLOWUP, followup.result());
        assertEquals(spec.fpCostFollowup(), followup.charged(), 1e-4f,
                "首次追击必须收 fpCostFollowup（8），而不是首段价（12）");
        assertEquals(before - fp.fp(), followup.charged(), 1e-4f);
        // 段号不是在"买下追击"时推进的：queueFollowup 只回到 WINDUP，真正 +1 发生在进入 ACTIVE 时。
        // 所以追击提交之后仍是 1，进入下一段 ACTIVE 才变 2 —— 这条正是索引问题的关键事实。
        assertEquals(1, cast.stageIndex(), "追击提交只回到 WINDUP，段号此刻仍是 1");
        cast.enterStage(SkillCastState.Stage.ACTIVE, 14);
        assertEquals(2, cast.stageIndex(), "进入下一段 ACTIVE 后才推进到 2");
        assertEquals(100f - 12f - 8f, fp.fp(), 1e-4f, "整条链只应扣掉 12 + 8");
    }

    /** 真正有追击窗的三件：每一段都必须按 spec 收（15/15 与 12/8 两种）。 */
    @Test
    void stagesWithAFollowupWindowChargeTheirConfiguredPrice() {
        record Case(String id, float fpCost, float followupCost) {}
        for (Case probe : java.util.List.of(new Case("bloodboon_ritual", 15f, 15f),
                new Case("starcaller_cry", 15f, 15f), new Case("cursed_blood_slice", 12f, 8f))) {
            var spec = SPECS.get(probe.id());
            assertTrue(spec.hasFollowup(), probe.id() + " 应当有追击窗");
            assertEquals(probe.fpCost(), spec.fpCost(), 1e-4f, probe.id());
            assertEquals(probe.followupCost(), spec.fpCostFollowup(), 1e-4f, probe.id());
            var fp = new PlayerFpState();
            var cast = new SkillCastState();
            assertEquals(probe.fpCost(), SkillTransactions.commit(cast, fp, spec, 0, true).charged(), 1e-4f,
                    probe.id() + " 首段收首段价");
            cast.enterStage(SkillCastState.Stage.ACTIVE, 5);
            cast.openFollowupWindow(10, spec.followupWindowTicks());
            var outcome = SkillTransactions.commit(cast, fp, spec, 11, true);
            assertEquals(SkillTransactions.Result.FOLLOWUP, outcome.result(), probe.id());
            assertEquals(probe.followupCost(), outcome.charged(), 1e-4f, probe.id() + " 追击段收追击价");
        }
    }

    /**
     * 真实发现（§2.6 核对时查出来的，**不是**收费 bug）：{@code destined_death} / {@code waterfowl_dance}
     * 的 {@code maxStages=3} 是**死配置**——它们的 {@code followupWindowTicks=0}，
     * 所以 {@code hasFollowup()} 为假，永远买不到第二段。
     *
     * <p>这三段体感来自运行时的**内部分段**：{@code activeTick} 用
     * {@code burst = min(2, elapsed*3/duration)} 当命中通道号，在一个长 ACTIVE 窗口里连打三下。
     * 因此 {@code stageIndex} 全程是 1，收费也只发生一次（首段价）——这与"每段收费"并不冲突，
     * 但"配置里写了 maxStages=3"会让人误以为它能追段，属于**配置语义不清**，记在这里备查。
     * 本轮**不改**这 8 件的参数（越界改平衡），只把它标出来。
     */
    @Test
    void multiStageConfigIsInertWithoutAFollowupWindow() {
        for (String id : new String[]{"destined_death", "waterfowl_dance"}) {
            var spec = SPECS.get(id);
            assertTrue(spec.maxStages() > 1, id + " 配置里声明了多段");
            assertFalse(spec.hasFollowup(), id + " 但没有追击窗 -> maxStages 读不到");
            var fp = new PlayerFpState();
            var cast = new SkillCastState();
            assertTrue(SkillTransactions.commit(cast, fp, spec, 0, true).accepted());
            cast.enterStage(SkillCastState.Stage.ACTIVE, 5);
            assertFalse(cast.followupOpen(6), id + "：没有追击窗就没有窗口");
            assertEquals(SkillTransactions.Result.REJECTED,
                    SkillTransactions.commit(cast, fp, spec, 6, true).result(),
                    id + "：没有追击窗时第二段必须被拒——这就是 maxStages 无效的证据");
            assertEquals(1, cast.stageIndex(), id + "：内部分段不推进 stageIndex");
        }
    }

    /**
     * 价格表只有两档：首段价与追击价。任何 stageIndex &gt; 0 都是追击价。
     *
     * <p>所以"给索引加 +1"这类改动**在这张表上不产生任何金额差异**——这正是那个待查项容易被误判的原因，
     * 也是为什么"首次追击多收 4 FP"的推断站不住脚（实测首次追击走的是 {@code costForStage(1)}）。
     *
     * <p>顺带两条真实限制：①三段以上的技能**无法**给第三段单独定价，要三档价必须先扩这个类型；
     * ②{@code fpCostFollowup=0} 时的"回落到首段价"分支，在现有 8 件里**走不到**
     * （有追击窗的三件都配了非 0 追击价，没窗的两件根本买不到追击段）。
     */
    @Test
    void priceTableHasExactlyTwoTiersSoLaterStagesCannotBePricedSeparately() {
        for (var spec : SPECS.values()) {
            assertEquals(spec.costForStage(1), spec.costForStage(2), 1e-6f, spec.id());
            assertEquals(spec.costForStage(2), spec.costForStage(9), 1e-6f, spec.id());
        }
    }
}
