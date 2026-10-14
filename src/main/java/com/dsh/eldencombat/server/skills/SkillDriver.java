package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * 施放时间线驱动器：每 tick 推进每个玩家正在进行的战技施放。
 *
 * <p>阶段（由 {@link RemembranceSkillSpec} 的参数驱动）：
 * WINDUP（蓄势）→ ACTIVE（生效：生成命中体/结算）→ RECOVERY（收招）→ IDLE。
 * 任何终止路径（受击、切武器、死亡、离线、换维度、超时）都走 {@link #cancel}，统一清理。
 */
public final class SkillDriver {

    /** 单个战技在"生效"与"收招"时要做的事 */
    public interface Runtime {
        default void onBegin(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {}

        default void onTick(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {}

        void onActive(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick);

        default void onRecovery(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        }

        default void onFinish(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        }

        default void onCancel(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        }

        /**
         * 参数不可用时的收尾回调（数据包重载后技能定义消失、注册项被拒绝）。
         *
         * <p>与 {@link #onCancel} 的唯一区别：**拿不到 spec**。所以实现里不许读 spec，
         * 更不许据此结算任何伤害——没有可信时间线的时候，"把本次施放自己生成的东西收回来"
         * 是唯一安全的动作。默认退化为 {@link #onDetach}：对没有独立缓存的实现，
         * 整玩家范围的瞬态清理就是正确兜底。
         */
        default void onAbort(ServerPlayer player, SkillCastState cast, long tick) {
            onDetach(player);
        }

        /**
         * §4 落地安全网：**施放结束之后**仍然悬空的跃起（例如战技收招期间掉下悬崖）
         * 继续被轮询。它只做"把人救回来 / 吸引到地面"，不再结算任何伤害——
         * 伤害的窗口属于施放本身，施放结束就不该再有收益。
         */
        default void onSafetyTick(ServerPlayer player, long tick) {
        }

        default void onDetach(ServerPlayer player) {
        }
    }

    private static final Map<String, Runtime> RUNTIMES = new HashMap<>();
    private static Map<String, RemembranceSkillSpec> specs = RemembranceSkillSpec.defaultsById();

    private SkillDriver() {
    }

    public static void register(String skillId, Runtime runtime) {
        RUNTIMES.put(skillId, runtime);
    }

    /** 把同一个运行时挂到全部战技上（八个追忆战技共用一个按 id 分派的实现）。 */
    public static void registerAll(Runtime runtime) {
        for (String skillId : RemembranceSkillSpec.defaultsById().keySet()) {
            RUNTIMES.put(skillId, runtime);
        }
    }

    public static Runtime runtime(String skillId) {
        return RUNTIMES.get(skillId);
    }

    public static RemembranceSkillSpec spec(String skillId) {
        var fixed=specs.get(skillId);
        return fixed!=null?fixed:AshBindings.spec(skillId);
    }

    public static Map<String, RemembranceSkillSpec> specs() {
        return specs;
    }

    /** 服务端启动/重载时调用 */
    public static void reloadSpecs() {
        specs = RemembranceSkillSpec.load();
    }

    public static SkillCastState cast(ServerPlayer player) {
        return player.getData(ModAttachments.PLAYER_SKILL_CAST);
    }

    /** 开始一次施放（调用方已完成 FP 校验与扣费）。 */
    public static boolean begin(ServerPlayer player, String skillId, long tick) {
        SkillCastState cast = cast(player);
        if (!cast.begin(skillId, tick)) {
            return false;
        }
        cast.enterStage(SkillCastState.Stage.WINDUP, tick);
        accepted(player, tick, false);
        return true;
    }

    public static void accepted(ServerPlayer player, long tick, boolean followup) {
        SkillCastState cast = cast(player);
        Runtime runtime = RUNTIMES.get(cast.skillId());
        RemembranceSkillSpec spec = spec(cast.skillId());
        if (!followup && runtime != null && spec != null) runtime.onBegin(player, cast, spec, tick);
    }

    /**
     * {@link #accepted} 的异常安全版本：Runtime 初始化抛异常时统一收尾，返回 false。
     *
     * <p>事务（扣费 + 阶段提交）在调用本方法之前就已经完成，所以拿到 false 的调用方必须按
     * "本次没有真正起手"处理：退还**本次刚提交的那一笔**、清掉刚写入的冷却、报告玩家。
     * 这里刻意不替调用方决定费用策略（退多少是事务方的账），只负责"把施放状态收干净"。
     * 异常本身记录到日志并保留 cause，不吞掉错误性质。
     */
    public static boolean acceptedSafely(ServerPlayer player, long tick, boolean followup) {
        try {
            accepted(player, tick, followup);
            return true;
        } catch (RuntimeException error) {
            SkillCastState cast = cast(player);
            EldenCombat.LOGGER.error("[skills] 运行时初始化失败，终止本次施放：{}", cast.skillId(), error);
            abort(player, cast, tick, "运行时初始化失败");
            return false;
        }
    }

    /** §4 落地安全网：施放结束但人还悬空时继续轮询；由服务端 tick 事件调用。 */
    public static void safetyTick(ServerPlayer player, long tick) {
        for (Runtime runtime : new java.util.HashSet<>(RUNTIMES.values())) {
            runtime.onSafetyTick(player, tick);
        }
    }

    /** 每 tick 推进；由服务端 tick 事件调用。 */
    public static void tick(ServerPlayer player, long tick) {
        SkillCastState cast = cast(player);
        if (!cast.isCasting()) {
            return;
        }
        RemembranceSkillSpec spec = spec(cast.skillId());
        if (spec == null) {
            // 参数在施放期间消失（重载丢项 / 注册项不再实现）：走统一中止，
            // 而不是只把状态机清零——否则本次施放生成的效果实体与位移追踪会留在世界上。
            abort(player, cast, tick, "缺少参数定义");
            return;
        }
        Runtime runtime = RUNTIMES.get(cast.skillId());
        if (!player.isAlive() || !RemembranceWeaponRules.matches(player, spec)) {
            cancel(player, tick, "施放者死亡或主手武器变化");
            return;
        }
        if (runtime != null) runtime.onTick(player, cast, spec, tick);
        if (!cast.isCasting()) return;
        int elapsed = cast.stageTicks(tick);

        // 超时保护：任何阶段都不允许无限停留
        if (cast.stageTimedOut(tick, spec.stageBudget())) {
            cancel(player, tick, "阶段超时");
            return;
        }

        switch (cast.stage()) {
            case WINDUP -> {
                if (elapsed >= spec.windupForStage(cast.stageIndex())) {
                    cast.enterStage(SkillCastState.Stage.ACTIVE, tick);
                    if (runtime != null) {
                        runtime.onActive(player, cast, spec, tick);
                    }
                    SkillServerEvents.sync(player);
                }
            }
            case ACTIVE -> {
                if (elapsed >= spec.activeForStage(cast.stageIndex())) {
                    if (spec.hasFollowup() && cast.stageIndex() < spec.maxStages()) {
                        cast.openFollowupWindow(tick, spec.followupWindowTicks());
                        SkillServerEvents.sync(player);
                    } else {
                        cast.enterRecovery(tick);
                        if (runtime != null) {
                            runtime.onRecovery(player, cast, spec, tick);
                        }
                        // 收招阶段也要广播：客户端据此从"生效姿势"过渡回收招姿势。
                        SkillServerEvents.sync(player);
                    }
                }
            }
            case FOLLOWUP_WINDOW -> {
                if (cast.followupExpired(tick)) {
                    cast.enterRecovery(tick);
                    if (runtime != null) {
                        runtime.onRecovery(player, cast, spec, tick);
                    }
                    SkillServerEvents.sync(player);
                }
            }
            case RECOVERY -> {
                if (elapsed >= spec.recoveryTicks()) {
                    if (runtime != null) {
                        runtime.onFinish(player, cast, spec, tick);
                    }
                    cast.finish();
                    SkillServerEvents.sync(player);
                }
            }
            default -> cast.finish();
        }
    }

    /** 追击输入：只有合法窗口 + 同一次施放才能通过，通过后进入新的 ACTIVE 段。 */
    public static boolean followup(ServerPlayer player, long tick) {
        SkillCastState cast = cast(player);
        RemembranceSkillSpec spec = spec(cast.skillId());
        return cast.isCasting() && SkillTransactions.request(cast, SkillServerEvents.fp(player), spec,
                tick, RemembranceWeaponRules.matches(player, spec)) == SkillTransactions.Result.FOLLOWUP;
    }

    /** 统一取消：清理施放状态与临时状态，不退还已确认的 FP。 */
    public static void detach(ServerPlayer player, long tick, String reason) {
        cancel(player, tick, reason);
        for (Runtime runtime : new java.util.HashSet<>(RUNTIMES.values())) runtime.onDetach(player);
    }

    /**
     * 参数缺失 / 运行时不可用时的统一中止：先释放本次施放归属的东西，再结束并同步。
     *
     * <p>刻意**不编造任何参数**：拿不到 spec 就没有可信时间线，不猜 FP、不猜阶段、不结算伤害。
     * 客户端收到 IDLE 快照后自动回到原版姿态，不需要服务端复位动画。
     */
    public static void abort(ServerPlayer player, SkillCastState cast, long tick, String reason) {
        Runtime runtime = RUNTIMES.get(cast.skillId());
        if (runtime != null) {
            try {
                runtime.onAbort(player, cast, tick);
            } catch (RuntimeException error) {
                // 清理必须容错：一次清理失败不能把整条服务端 tick 拖垮（那会让所有人卡住）
                EldenCombat.LOGGER.error("[skills] 清理 {} 的残留状态失败", cast.skillId(), error);
            }
        }
        EldenCombat.LOGGER.warn("[skills] 中止施放 {}：{}", cast.skillId(), reason);
        cast.finish();
        SkillServerEvents.sync(player);
    }

    public static void cancel(ServerPlayer player, long tick, String reason) {
        SkillCastState cast = cast(player);
        if (!cast.isCasting()) {
            return;
        }
        RemembranceSkillSpec spec = spec(cast.skillId());
        Runtime runtime = RUNTIMES.get(cast.skillId());
        if (runtime != null) {
            try {
                if (spec != null) {
                    runtime.onCancel(player, cast, spec, tick);
                } else {
                    // 参数在施放期间消失时也要收干净；onCancel 拿不到 spec 就无从谈起
                    runtime.onAbort(player, cast, tick);
                }
            } catch (RuntimeException error) {
                EldenCombat.LOGGER.error("[skills] 取消 {} 时清理失败", cast.skillId(), error);
            }
        }
        EldenCombat.LOGGER.debug("[skills] 取消施放 {}：{}", cast.skillId(), reason);
        // 纯原版方案：动作表现完全由客户端 SkillPoseAnimator 依据同步的施放状态驱动，
        // 这里只要把状态归零，客户端下一次同步就会自动回到原版姿态，不需要服务端复位动画。
        cast.finish();
        SkillServerEvents.sync(player);
    }
}
