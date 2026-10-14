package com.dsh.eldencombat.server.skills;

import java.util.HashSet;
import java.util.Set;

/**
 * 单个玩家的施放实例状态机（服务端权威，不依赖 MC/EF 类型，便于纯逻辑测试）。
 *
 * <p>阶段流：{@code IDLE -> WINDUP -> ACTIVE -> FOLLOWUP_WINDOW/RECOVERY -> IDLE}。
 * 每个阶段有独立命中集合：同段对同一目标只结算一次，进入下一段后允许重新命中。
 * 每次 begin 分配新的 castId，客户端无法通过伪造 ID 绕过限制。
 */
public final class SkillCastState {

    public enum Stage {
        IDLE,
        WINDUP,
        ACTIVE,
        FOLLOWUP_WINDOW,
        RECOVERY
    }

    private int nextCastId = 1;
    private int castId;
    private String skillId = "";
    private Stage stage = Stage.IDLE;
    private long stageStartTick = Long.MIN_VALUE;
    /** 当前是第几段（0 起） */
    private int stageIndex;
    /** 本次施放已生效的段数 */
    private int stagesResolved;
    /** 追击窗口结束 tick */
    private long followupDeadline = Long.MIN_VALUE;
    /** 当前阶段的命中集合 */
    private final Set<Integer> hitTargets = new HashSet<>();
    private final Set<Long> hitChannels = new HashSet<>();
    private final Set<Integer> resolvedChannels = new HashSet<>();
    /** 本阶段是否已经结算过伤害 */
    private boolean stageResolved;

    public int castId() {
        return castId;
    }

    public String skillId() {
        return skillId;
    }

    public Stage stage() {
        return stage;
    }

    public int stageIndex() {
        return stageIndex;
    }

    public int stagesResolved() {
        return stagesResolved;
    }

    public long stageStartTick() {
        return stageStartTick;
    }

    public long followupDeadline() {
        return followupDeadline;
    }

    public boolean isCasting() {
        return stage != Stage.IDLE;
    }

    /** Preserve existing duration; only the last two recovery ticks can connect to another action. */
    public boolean recoveryCancelable(long tick, int recoveryTicks) {
        int tail = Math.min(2, Math.max(0, recoveryTicks - 1));
        int elapsed = stageTicks(tick);
        return stage == Stage.RECOVERY && tail > 0
                && elapsed >= recoveryTicks - tail && elapsed < recoveryTicks;
    }

    public int stageTicks(long tick) {
        return (int) Math.max(0L, tick - stageStartTick);
    }

    /**
     * 开始一次施放；若已在施放中返回 false（不覆盖、不分配新 castId）。
     */
    public boolean begin(String skillId, long tick) {
        if (isCasting() || skillId == null || skillId.isEmpty()) {
            return false;
        }
        this.castId = nextCastId++;
        this.skillId = skillId;
        this.stage = Stage.WINDUP;
        this.stageStartTick = tick;
        this.stageIndex = 0;
        this.stagesResolved = 0;
        this.followupDeadline = Long.MIN_VALUE;
        this.hitTargets.clear();
        this.hitChannels.clear();
        this.resolvedChannels.clear();
        this.stageResolved = false;
        return true;
    }

    /** 阶段推进；进入新段会清空命中集合，使多段技能可以重新命中同一目标。 */
    public void enterStage(Stage next, long tick) {
        if (!isCasting()) {
            return;
        }
        if (next == Stage.ACTIVE || next == Stage.WINDUP) {
            this.hitTargets.clear();
            this.hitChannels.clear();
            this.resolvedChannels.clear();
            this.stageResolved = false;
        }
        this.stage = next;
        this.stageStartTick = tick;
        if (next == Stage.ACTIVE) {
            this.stageIndex++;
        }
    }

    /** 打开追击窗口（例如唤星呼喊后 12 tick、咒血斩击 10 tick）。 */
    public void openFollowupWindow(long tick, int windowTicks) {
        this.stage = Stage.FOLLOWUP_WINDOW;
        this.stageStartTick = tick;
        this.followupDeadline = tick + Math.max(1, windowTicks);
    }

    public boolean followupOpen(long tick) {
        return stage == Stage.FOLLOWUP_WINDOW && tick <= followupDeadline;
    }

    public boolean followupExpired(long tick) {
        return stage == Stage.FOLLOWUP_WINDOW && tick > followupDeadline;
    }

    /** 追击请求：必须属于同一次施放、时机合法、且尚未被消费。 */
    public boolean consumeFollowup(long tick) {
        if (!followupOpen(tick)) {
            return false;
        }
        this.followupDeadline = Long.MIN_VALUE;
        this.hitTargets.clear();
        this.hitChannels.clear();
        this.resolvedChannels.clear();
        this.stageResolved = false;
        this.stage = Stage.ACTIVE;
        this.stageStartTick = tick;
        this.stageIndex++;
        return true;
    }

    /** 进入收招 */
    public void enterRecovery(long tick) {
        this.stage = Stage.RECOVERY;
        this.stageStartTick = tick;
    }

    /** 本阶段命中记录：返回 true 表示首次命中，应当结算伤害。 */
    public boolean markHit(int entityId) {
        return markHit(entityId, 0);
    }

    public boolean markHit(int entityId, int channel) {
        long key = ((long) channel << 32) | (entityId & 0xffffffffL);
        if (!hitChannels.add(key)) return false;
        hitTargets.add(entityId);
        return true;
    }

    public boolean queueFollowup(long tick, int maxStages) {
        if (!followupOpen(tick) || stageIndex >= maxStages) return false;
        followupDeadline = Long.MIN_VALUE;
        enterStage(Stage.WINDUP, tick);
        return true;
    }

    public boolean hasHit(int entityId) {
        return hitTargets.contains(entityId);
    }

    /** 当前阶段已命中的不同目标数（诊断/测试用） */
    public int hitCount() {
        return hitTargets.size();
    }

    public boolean stageResolved() {
        return stageResolved;
    }

    /** 标记本段已产生有效伤害（用于吸血等"确认伤害后"的收益）。 */
    public void markStageResolved() {
        if (!stageResolved) {
            stageResolved = true;
            stagesResolved++;
        }
    }

    public void markResolvedChannel(int channel) { resolvedChannels.add(channel); }
    public int resolvedChannelCount() { return resolvedChannels.size(); }

    /** 阶段是否超时（每个阶段都有上界，防止卡死）。 */
    public boolean stageTimedOut(long tick, int maxStageTicks) {
        return isCasting() && stageStartTick != Long.MIN_VALUE && tick - stageStartTick > maxStageTicks;
    }

    /** 统一结束：任何终止路径都走这里，清空临时状态。 */
    public void finish() {
        this.stage = Stage.IDLE;
        this.skillId = "";
        this.stageStartTick = Long.MIN_VALUE;
        this.followupDeadline = Long.MIN_VALUE;
        this.stageIndex = 0;
        this.hitTargets.clear();
        this.hitChannels.clear();
        this.resolvedChannels.clear();
        this.stageResolved = false;
    }

    public String describe() {
        return String.format("cast#%d %s %s 段%d 已结算%d 命中%d 追击至%d",
                castId, skillId, stage, stageIndex, stagesResolved, hitTargets.size(), followupDeadline);
    }
}
