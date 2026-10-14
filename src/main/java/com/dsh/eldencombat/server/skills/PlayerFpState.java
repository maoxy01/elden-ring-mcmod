package com.dsh.eldencombat.server.skills;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局 FP（服务端权威）。刻意不依赖 Minecraft / Epic Fight 类型，便于纯逻辑测试。
 *
 * <p>规则（对应计划第 5、6 条）：
 * <ul>
 *   <li>首次进入原型默认满 FP；</li>
 *   <li>换武器 / 换技能 / 换维度**不**补满；</li>
 *   <li>重生（死亡克隆）补满，并明确记录一次；</li>
 *   <li>FP 一旦确认扣除默认不退；被拒绝的请求不扣。</li>
 * </ul>
 */
public final class PlayerFpState {

    public static final float DEFAULT_MAX_FP = 100.0f;

    private float maxFp = DEFAULT_MAX_FP;
    private float fp = DEFAULT_MAX_FP;
    /** 护符等外部来源的 FP 上限加成（不落盘，登录时重算） */
    private float bonusMaxFp;
    /** Transient manual-test override; deliberately not included in the save codec. */
    private boolean unlimitedForTesting;
    /** 每个技能独立冷却：skillId -> 冷却结束 tick */
    private final Map<String, Long> cooldowns = new HashMap<>();
    /** 最近一次确认施放（诊断用） */
    private String lastSkillId = "";
    private long lastCastTick = Long.MIN_VALUE;
    /** 重生补满的审计记录 */
    private int refillCount;

    public float fp() {
        return unlimitedForTesting ? maxFp : fp;
    }

    public boolean unlimitedForTesting() { return unlimitedForTesting; }

    public void setUnlimitedForTesting(boolean enabled) {
        unlimitedForTesting = enabled;
        if (enabled) fp = maxFp;
    }

    public float maxFp() {
        return maxFp;
    }

    public void setMaxFp(float maxFp) {
        this.maxFp = Math.max(1.0f, maxFp);
        this.fp = Math.min(this.fp, this.maxFp);
    }

    /** 护符等外部来源的 FP 上限加成（绝对值），不属于基础值。 */
    public float bonusMaxFp() {
        return bonusMaxFp;
    }

    /**
     * 设置外部来源的 FP 上限加成：实际上限 = {@link #DEFAULT_MAX_FP} + 加成。
     *
     * <p>刻意**不写进存档**：加成每次登录/重生由护符重新应用，避免出现
     * "摘掉护符但上限还留着"的幽灵加成。已有 FP 会被夹到新上限内。
     */
    public void setBonusMaxFp(float bonus) {
        this.bonusMaxFp = Float.isFinite(bonus) && bonus > 0 ? bonus : 0f;
        this.maxFp = Math.max(1.0f, DEFAULT_MAX_FP + this.bonusMaxFp);
        this.fp = Math.min(this.fp, this.maxFp);
    }

    /**
     * 直接改写当前 FP（仅用于权限隔离的测试命令与验收场景；战斗逻辑一律走 {@link #trySpend}）。
     * 结果被夹在 {@code [0, maxFp]}。
     */
    public void setFp(float value) {
        this.fp = Math.max(0.0f, Math.min(this.maxFp, value));
    }

    public boolean has(float cost) {
        return Float.isFinite(cost) && (unlimitedForTesting || fp + 1.0e-4f >= Math.max(0.0f, cost));
    }

    /** 是否已经确认施放过（用于战斗模式/HUD 判断，不用于扣费） */
    public boolean isEmpty() {
        return !unlimitedForTesting && fp <= 0.0f;
    }

    /**
     * 原子扣费：不足返回 false 且不改变任何状态（拒绝的请求不消耗）。
     */
    public boolean trySpend(float cost, String skillId, long tick) {
        float need = Math.max(0.0f, cost);
        if (!has(need)) {
            return false;
        }
        if (!unlimitedForTesting) this.fp -= need;
        this.lastSkillId = skillId == null ? "" : skillId;
        this.lastCastTick = tick;
        return true;
    }

    /** 回退一次扣费（仅在"确认启动后失败"这种异常路径使用，正常不退还） */
    public void refund(float amount) {
        this.fp = Math.min(maxFp, this.fp + Math.max(0.0f, amount));
    }

    /** 重生补满：换维度不算重生，调用方必须区分 */
    public void refillOnRespawn() {
        this.fp = maxFp;
        this.refillCount++;
        this.cooldowns.clear();
    }

    /** 首次进入世界：只补一次（已初始化过的不再补） */
    public void refillOnFirstJoin() {
        if (lastCastTick == Long.MIN_VALUE && refillCount == 0) {
            this.fp = maxFp;
            this.refillCount++;
        }
    }

    public int refillCount() {
        return refillCount;
    }

    public String lastSkillId() {
        return lastSkillId;
    }

    public long lastCastTick() {
        return lastCastTick;
    }

    public boolean onCooldown(String skillId, long tick) {
        Long until = cooldowns.get(skillId);
        return until != null && tick < until;
    }

    public long cooldownRemaining(String skillId, long tick) {
        Long until = cooldowns.get(skillId);
        return until == null ? 0L : Math.max(0L, until - tick);
    }

    public void setCooldown(String skillId, long untilTick) {
        if (skillId != null && !skillId.isEmpty()) {
            cooldowns.put(skillId, untilTick);
        }
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    /**
     * 只清掉一个技能的冷却。
     *
     * <p>用于"事务已提交、但本次启动随后失败"的回滚：那一次施放根本没有生效，
     * 不该把冷却留在玩家头上。刻意不提供"清全部"的调用点，否则一次失败会顺手
     * 把其它技能的冷却也抹掉，等于变相刷冷却。
     */
    public void clearCooldown(String skillId) {
        if (skillId != null && !skillId.isEmpty()) {
            cooldowns.remove(skillId);
        }
    }

    /** 供序列化层读取的只读视图 */
    public Map<String, Long> cooldownsView() {
        return new HashMap<>(cooldowns);
    }

    /**
     * 从存档恢复：只搬运数值，不做任何刷新。
     * 重新登录不得借恢复流程补满 FP（补满只发生在重生与首次进入）。
     */
    public void restore(float fp, String lastSkillId, long lastCastTick, int refillCount,
                        Map<String, Long> savedCooldowns) {
        this.unlimitedForTesting = false;
        this.fp = Math.max(0.0f, Math.min(this.maxFp, fp));
        this.lastSkillId = lastSkillId == null ? "" : lastSkillId;
        this.lastCastTick = lastCastTick;
        this.refillCount = Math.max(0, refillCount);
        this.cooldowns.clear();
        if (savedCooldowns != null) {
            this.cooldowns.putAll(savedCooldowns);
        }
    }

    /** 退出保存 / 重登：只搬运数值，不刷新资源 */
    public void copyFrom(PlayerFpState other) {
        if (other == null) {
            return;
        }
        this.maxFp = other.maxFp;
        this.fp = other.fp;
        this.bonusMaxFp = other.bonusMaxFp;
        this.unlimitedForTesting = false;
        this.cooldowns.clear();
        this.cooldowns.putAll(other.cooldowns);
        this.lastSkillId = other.lastSkillId;
        this.lastCastTick = other.lastCastTick;
        this.refillCount = other.refillCount;
    }

    public String describe() {
        return String.format("FP=%.1f/%.1f(护符+%.0f) 冷却中=%d 上次=%s@%d 补满次数=%d",
                fp, maxFp, bonusMaxFp, cooldowns.size(), lastSkillId, lastCastTick, refillCount);
    }
}
