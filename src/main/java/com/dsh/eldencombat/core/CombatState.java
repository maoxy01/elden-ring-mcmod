package com.dsh.eldencombat.core;

import java.util.HashSet;
import java.util.Set;

/**
 * 服务端权威的玩家战斗状态。HP 用原版 LivingEntity 生命，其他全部集中在这里：
 * 当前动作窗口、受击硬直、翻滚无敌、盾防与弹反资格都从本状态推导，不另设互相矛盾的布尔量。
 *
 * <p>本类刻意不依赖 Minecraft 类型，便于纯逻辑单测。
 */
public final class CombatState {

    public static final int NO_TARGET = -1;

    /** 当前动作 */
    public ActionId action = ActionId.NONE;
    /** 当前动作规格（NONE 时为 null） */
    public ActionSpec spec;
    /** 动作被接受的服务器 tick */
    public long actionStartTick;
    /** 动作序号：每次被接受的动作递增，同一序号对同一目标最多造成一次伤害 */
    public int actionSequence;
    public WeaponClass actionWeapon = WeaponClass.NONE;
    public String actionItem = "";
    public boolean actionLeftHanded;
    public boolean actionSlim;
    public long guardImpactTick = Long.MIN_VALUE;
    /** 最近一次消耗精力的 tick（用于恢复延迟） */
    public long lastStaminaUseTick = Long.MIN_VALUE;
    public float stamina;

    /** 战斗模式开关（关闭后回到原版挖掘模式） */
    public boolean combatMode;
    /** 当前锁定的目标实体 id */
    public int targetId = NO_TARGET;

    /** 蓄力累计 tick（仅 HEAVY_CHARGE 期间有效） */
    public int chargeTicks;
    /** 是否因为蓄力被打断而需要丢弃（换武器/受击） */
    public boolean chargeCancelled;

    /** 是否按住盾牌防御 */
    public boolean guarding;
    /** 破防硬直结束 tick */
    public long guardBrokenUntilTick = Long.MIN_VALUE;

    /** 连续弹反计数与最近一次成功弹反 tick */
    public int parryChain;
    public long lastParryTick = Long.MIN_VALUE;

    /** 喝药次数（每次重生恢复；首版不做赐福） */
    public int potionCharges;
    /** 当前喝药动作是否已经生效过一次 */
    public boolean drinkConsumed;

    /** 翻滚方向（规范化，无方向时由服务端填后撤方向） */
    public double rollDirX;
    public double rollDirZ;

    /** 本次动作已经命中过的实体，保证同一序号同一目标只吃一次伤害 */
    private final Set<Integer> hitTargets = new HashSet<>();
    /** 是否已经按配置初始化过（附件默认构造时为 false） */
    public boolean initialized;

    public void ensureInit(CombatConfig config) {
        if (!initialized) {
            initFrom(config);
            initialized = true;
        }
    }

    public void initFrom(CombatConfig config) {
        this.stamina = config.stamina.max;
        this.potionCharges = config.drink.charges;
    }

    /**
     * 赐福休息类恢复：只把精力补满，**不**重置战斗状态
     * （{@link #reset} 会清守卫/连击/动作，休息不该顺手清掉这些）。
     */
    public void refillStamina(CombatConfig config) {
        this.stamina = config.stamina.max;
    }

    /** 赐福休息类恢复：只把药瓶补满（现有权威字段 {@link #potionCharges}）。 */
    public void refillFlasks(CombatConfig config) {
        this.potionCharges = config.drink.charges;
    }

    /** 重置战斗状态（重生 / 退出战斗模式 / 卸载）：不残留无敌、武器显示与锁定目标 */
    public void reset(CombatConfig config) {
        action = ActionId.NONE;
        spec = null;
        actionStartTick = 0;
        chargeTicks = 0;
        chargeCancelled = false;
        guarding = false;
        guardBrokenUntilTick = Long.MIN_VALUE;
        parryChain = 0;
        lastParryTick = Long.MIN_VALUE;
        hitTargets.clear();
        rollDirX = 0;
        rollDirZ = 0;
        drinkConsumed = false;
        targetId = NO_TARGET;
        combatMode = false;
        actionWeapon = WeaponClass.NONE;
        actionItem = "";
        guardImpactTick = Long.MIN_VALUE;
        stamina = config.stamina.max;
        potionCharges = config.drink.charges;
    }

    public int tickInAction(long now) {
        return (int) Math.max(0, now - actionStartTick);
    }

    public boolean isBusy() {
        return action != ActionId.NONE;
    }

    /** 接受一个新动作：扣精力、递增序号、清空命中记录。 */
    public void acceptAction(ActionSpec spec, long now) {
        this.action = spec.id();
        this.spec = spec;
        this.actionStartTick = now;
        this.actionSequence++;
        this.hitTargets.clear();
        this.drinkConsumed = false;
        if (spec.staminaCost() > 0) {
            this.stamina = Math.max(0f, this.stamina - spec.staminaCost());
            this.lastStaminaUseTick = now;
        }
        if (spec.id() == ActionId.HEAVY_CHARGE) {
            this.chargeTicks = 0;
            this.chargeCancelled = false;
        }
    }

    public void clearAction() {
        this.action = ActionId.NONE;
        this.spec = null;
        this.chargeTicks = 0;
        this.hitTargets.clear();
        this.drinkConsumed = false;
        this.actionWeapon = WeaponClass.NONE;
        this.actionItem = "";
    }

    /** 动作自然结束或被打断；返回是否发生了变化。 */
    public boolean finishIfDone(long now) {
        if (spec != null && spec.isFinished(tickInAction(now))) {
            clearAction();
            return true;
        }
        return false;
    }

    /** 是否处于翻滚无敌窗口。只过滤本系统战斗伤害，环境伤害不受影响。 */
    public boolean isRollInvulnerable(long now) {
        if (action != ActionId.ROLL || spec == null) {
            return false;
        }
        int tick = tickInAction(now);
        return spec.windows().stream().anyMatch(window -> window.activeAt(tick));
    }

    /** 是否处于弹反成功窗口 */
    public boolean isParryWindow(long now) {
        if (action != ActionId.PARRY || spec == null) {
            return false;
        }
        return spec.activeWindow(tickInAction(now)) != null;
    }

    public boolean isGuardBroken(long now) {
        return action == ActionId.GUARD_BROKEN || now < guardBrokenUntilTick;
    }

    /** 盾防是否真正生效：按住防御、当前无其他动作、且没有破防 */
    public boolean shieldActive(long now) {
        return guarding && !isBusy() && !isGuardBroken(now);
    }

    public boolean hasHit(int entityId) {
        return hitTargets.contains(entityId);
    }

    /** 记录命中；返回 true 表示是首次命中（应当结算伤害）。 */
    public boolean markHit(int entityId) {
        return hitTargets.add(entityId);
    }

    /**
     * 精力恢复。停止消耗 regenDelayTicks 后按每 tick 速率恢复；防御时速率减半。
     */
    public void tickStamina(CombatConfig config, long now, boolean guardingNow) {
        tickStamina(config, now, guardingNow, 1f);
    }

    /**
     * 精力恢复（带外部倍率）。
     *
     * @param regenMultiplier 护符/药物等来源的恢复速率倍率（1 = 无加成）
     */
    public void tickStamina(CombatConfig config, long now, boolean guardingNow, float regenMultiplier) {
        if (lastStaminaUseTick != Long.MIN_VALUE && now - lastStaminaUseTick < config.stamina.regenDelayTicks) {
            return;
        }
        float multiplier = Float.isFinite(regenMultiplier) ? Math.max(0.1f, regenMultiplier) : 1f;
        float rate = config.stamina.regenPerTick * multiplier
                * (guardingNow ? config.stamina.guardRegenMultiplier : 1f);
        if (stamina < config.stamina.max) {
            stamina = Math.min(config.stamina.max, stamina + rate);
        }
    }

    public boolean hasStamina(float cost) {
        return stamina + 1.0e-4f >= cost;
    }
}
