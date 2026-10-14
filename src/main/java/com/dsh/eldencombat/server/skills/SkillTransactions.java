package com.dsh.eldencombat.server.skills;

/** Pure, server-thread transaction shared by every remembrance skill. */
public final class SkillTransactions {
    public enum Result { REJECTED, STARTED, FOLLOWUP }

    /**
     * 一次提交的可核对结果：结论 + **实际扣掉的 FP**。
     *
     * <p>为什么要单独带金额：调用方（{@link SkillCasting}）在 Runtime 初始化失败时要"精确退掉本次
     * 刚提交的那一笔"，而 {@code costForStage} 的金额随段号变化、且 {@code -PmanualSkills} 的
     * 无限 FP 根本不扣钱。让扣费方把金额报出来，回滚方就不需要重新推一遍算术——
     * 任何"重新算一遍应该退多少"的写法都可能在多段技能上退错。
     */
    public record Outcome(Result result, float charged) {
        public boolean accepted() {
            return result != Result.REJECTED;
        }
    }

    private SkillTransactions() {}

    public static boolean matchesRequest(SkillCastState cast, int castId, int stageIndex) {
        return cast.isCasting() ? castId == cast.castId() && stageIndex == cast.stageIndex()
                : castId == 0 && stageIndex == 0;
    }

    /** 只关心结论的调用方（测试与既有路径）看这里；需要回滚金额的用 {@link #commit}。 */
    public static Result request(SkillCastState cast, PlayerFpState fp, RemembranceSkillSpec spec,
                                 long tick, boolean legal) {
        return commit(cast, fp, spec, tick, legal).result();
    }

    public static Outcome commit(SkillCastState cast, PlayerFpState fp, RemembranceSkillSpec spec,
                                 long tick, boolean legal) {
        if (!legal || spec == null) return new Outcome(Result.REJECTED, 0f);
        boolean followup = cast.isCasting();
        if (followup) {
            if (!spec.id().equals(cast.skillId()) || !spec.hasFollowup()
                    || !cast.followupOpen(tick) || cast.stageIndex() >= spec.maxStages()) return new Outcome(Result.REJECTED, 0f);
        } else if (fp.onCooldown(spec.id(), tick)) return new Outcome(Result.REJECTED, 0f);
        float cost = spec.costForStage(followup ? cast.stageIndex() : 0);
        // "报告金额 == 实际扣减"是硬不变量：无限 FP 下真正扣掉的是 0，报告 0 才不会在回滚时凭空加钱。
        float before = fp.fp();
        if (!fp.trySpend(cost, spec.id(), tick)) return new Outcome(Result.REJECTED, 0f);
        float charged = Math.max(0f, before - fp.fp());
        boolean accepted = followup ? cast.queueFollowup(tick, spec.maxStages()) : cast.begin(spec.id(), tick);
        if (!accepted) {
            fp.refund(cost);
            return new Outcome(Result.REJECTED, 0f);
        }
        if (!followup && spec.cooldownTicks() > 0) fp.setCooldown(spec.id(), tick + spec.cooldownTicks());
        return new Outcome(followup ? Result.FOLLOWUP : Result.STARTED, charged);
    }
}
