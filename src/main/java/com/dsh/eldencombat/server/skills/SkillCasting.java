package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.server.level.ServerPlayer;

/**
 * 战技输入的服务端唯一入口。
 *
 * <p>客户端只发一个 {@code IntentType.SKILL}，**发不出技能 id、段号、FP 扣费**：
 * 技能由主手武器决定，段数由服务端状态机决定，费用由 {@link SkillTransactions} 原子扣除。
 * 命令行的 {@code /eldencombat fp skill cast <id>} 也走同一个方法（只是跳过武器绑定校验），
 * 保证"测试路径"和"按键路径"不会各写一套规则。
 */
public final class SkillCasting {

    private SkillCasting() {
    }

    /** 主手武器绑定的战技；没有绑定返回 null。 */
    public static RemembranceSkillSpec specForMainHand(ServerPlayer player) {
        return AshBindings.resolve(player.getMainHandItem());
    }

    /** 战技键：未施放时起手；追击窗口内推进一段。被拒绝时给玩家一句可读的原因。 */
    public static SkillTransactions.Result request(ServerPlayer player, long tick) {
        if (com.dsh.eldencombat.compat.CombatRollCompat.rolling(player)) return SkillTransactions.Result.REJECTED;
        SkillCastState cast = SkillDriver.cast(player);
        RemembranceSkillSpec spec;
        if (cast.isCasting()) {
            spec = SkillDriver.spec(cast.skillId());
            if (spec == null || SkillDriver.runtime(spec.id()) == null) {
                return SkillTransactions.Result.REJECTED;
            }
            if (!spec.hasFollowup()) {
                notify(player, skillName(spec) + " 没有追击段");
                return SkillTransactions.Result.REJECTED;
            }
            if (!cast.followupOpen(tick)) {
                // 施放中但不在窗口内：这是最常见的一种"按了没反应"，必须说明白
                notify(player, "不在追击窗口内（" + cast.stage() + " 段" + cast.stageIndex() + "）");
                return SkillTransactions.Result.REJECTED;
            }
            if (cast.stageIndex() >= spec.maxStages()) {
                notify(player, skillName(spec) + " 已达最大段数 " + spec.maxStages());
                return SkillTransactions.Result.REJECTED;
            }
        } else {
            spec = specForMainHand(player);
            if (spec == null || SkillDriver.runtime(spec.id()) == null) {
                notify(player, "主手武器没有绑定战技（未绑定或实现尚未准入）");
                return SkillTransactions.Result.REJECTED;
            }
            PlayerFpState fp = SkillServerEvents.fp(player);
            if (fp.onCooldown(spec.id(), tick)) {
                notify(player, skillName(spec) + " 冷却中 " + (fp.cooldownRemaining(spec.id(), tick) / 20 + 1) + "s");
                return SkillTransactions.Result.REJECTED;
            }
            if (!fp.has(spec.fpCost())) {
                notify(player, "FP 不足：需要 " + (int) spec.fpCost() + "，当前 " + (int) fp.fp());
                return SkillTransactions.Result.REJECTED;
            }
        }
        // 合法性只算一次：拒绝时才能把原因说准（"武器不匹配" vs "启动失败"）
        boolean legal = RemembranceWeaponRules.matches(player, spec);
        SkillTransactions.Result result = submit(player, cast, spec, tick, legal);
        if (result == SkillTransactions.Result.REJECTED && !legal) {
            notify(player, "主手武器当前不匹配 " + skillName(spec) + "（绑定已变化）");
        }
        return result;
    }

    /** 给玩家发一条动作栏提示：按战技键没反应时，玩家能看到到底卡在哪一步。 */
    private static void notify(ServerPlayer player, String message) {
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
    }

    private static String skillName(RemembranceSkillSpec spec) {
        return spec.id() + "(" + spec.itemId() + ")";
    }

    /** 命令入口：按 id 强制施放（仍受 FP、冷却、段数上限约束，只跳过武器绑定校验）。 */
    public static SkillTransactions.Result requestById(ServerPlayer player, String skillId, long tick) {
        RemembranceSkillSpec spec = SkillDriver.spec(skillId);
        if (spec == null || SkillDriver.runtime(spec.id()) == null) {
            return SkillTransactions.Result.REJECTED;
        }
        SkillCastState cast = SkillDriver.cast(player);
        return submit(player, cast, spec, tick, true);
    }

    private static SkillTransactions.Result submit(ServerPlayer player, SkillCastState cast,
            RemembranceSkillSpec spec, long tick, boolean legal) {
        SkillTransactions.Outcome outcome = SkillTransactions.commit(cast, SkillServerEvents.fp(player),
                spec, tick, legal);
        if (!outcome.accepted()) {
            // 只处理提交阶段的拒绝，避免覆盖下方初始化失败的退款提示。
            if (legal) {
                PlayerFpState fp = SkillServerEvents.fp(player);
                float cost = spec.costForStage(cast.isCasting() ? cast.stageIndex() : 0);
                if (!fp.has(cost)) {
                    notify(player, "FP 不足：需要 " + (int) cost + "，当前 " + (int) fp.fp());
                } else {
                    notify(player, skillName(spec) + " 当前无法施放（冷却或追击状态不满足）");
                }
            }
            return SkillTransactions.Result.REJECTED;
        }
        boolean followup = outcome.result() == SkillTransactions.Result.FOLLOWUP;
        if (!SkillDriver.acceptedSafely(player, tick, followup)) {
            // 事务已提交（扣了费、也提交了阶段），但 Runtime 没起来 → 这一段从未生效。
            rollback(player, spec, outcome);
            return SkillTransactions.Result.REJECTED;
        }
        SkillServerEvents.sync(player);
        return outcome.result();
    }

    /**
     * Runtime 初始化失败的费用回滚：只退**本次刚提交的那一笔**，并清掉刚写入的冷却。
     *
     * <p>为什么不"笼统退款"：多段技能的前几段已经真实生效（打出了伤害/位移），
     * 把它们一起退掉等于开了一个"反复触发启动失败来刷 FP"的口子。金额直接取事务报告的
     * {@link SkillTransactions.Outcome#charged()}，不回算——段号算术已经错过一次，
     * 回滚路径不该再复述一遍。
     */
    private static void rollback(ServerPlayer player, RemembranceSkillSpec spec, SkillTransactions.Outcome outcome) {
        PlayerFpState fp = SkillServerEvents.fp(player);
        if (outcome.charged() > 0f) {
            fp.refund(outcome.charged());
        }
        fp.clearCooldown(spec.id());
        // abort 已同步过中止状态；退款后再同步，客户端才能立即拿到恢复的 FP。
        SkillServerEvents.sync(player);
        notify(player, "§c启动失败，已退回本次 FP " + Math.round(outcome.charged()));
        EldenCombat.LOGGER.warn("[skills] {} 启动失败：已回滚 FP {} 并清除本次冷却", spec.id(), outcome.charged());
    }
}
