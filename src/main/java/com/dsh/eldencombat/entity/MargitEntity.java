package com.dsh.eldencombat.entity;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.core.CoreLog;
import com.dsh.eldencombat.server.HitResolver;
import com.dsh.eldencombat.skeleton.SkeletonData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 玛尔基特 Boss。AI、动作时间线与命中窗口都在这里驱动；
 * 命中判定走 HitResolver（骨骼采样 + 胶囊/盒 + 阻挡 raycast），不使用原版近战计时器。
 */
public class MargitEntity extends Monster {

    public static final String ANIM_IDLE = "idle";
    public static final String ANIM_WALK = "walk";
    public static final String ANIM_STAFF_SWEEP = "staff_sweep";
    public static final String ANIM_STAFF_SLAM = "staff_slam";
    public static final String ANIM_DAGGER_SLASH = "dagger_slash";
    public static final String ANIM_HAMMER_SLAM = "hammer_slam";
    public static final String ANIM_STAGGER = "stagger";
    public static final String ANIM_DEATH = "death";

    /** 资产缺失时的回退时长（tick） */
    private static final Map<String, Integer> FALLBACK_LENGTHS = Map.of(
            ANIM_IDLE, 60, ANIM_WALK, 26, ANIM_STAFF_SWEEP, 34, ANIM_STAFF_SLAM, 42,
            ANIM_DAGGER_SLASH, 22, ANIM_HAMMER_SLAM, 50, ANIM_STAGGER, 70, ANIM_DEATH, 50);

    /** 死亡后停留时长（tick），让 death 动作播完再清理 */
    private static final int DEATH_LINGER_TICKS = 52;

    private static final EntityDataAccessor<String> DATA_ANIM =
            SynchedEntityData.defineId(MargitEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_ANIM_START =
            SynchedEntityData.defineId(MargitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(MargitEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_STAGGER =
            SynchedEntityData.defineId(MargitEntity.class, EntityDataSerializers.FLOAT);
    /**
     * 崩解窗口是否开启（同步给客户端）。
     * 硬直动作名 {@code stagger} 已经同步，但"崩解"比"硬直"多一层语义
     * （2 秒易伤窗口），HUD 靠这个布尔值决定是否爆闪，所以单独同步一个字段。
     */
    private static final EntityDataAccessor<Boolean> DATA_BROKEN =
            SynchedEntityData.defineId(MargitEntity.class, EntityDataSerializers.BOOLEAN);

    // ---- 服务端 AI 状态（不参与同步） ----
    private String actionClip = ANIM_IDLE;
    private long clipStartTick;
    private boolean actionRunning;
    private float attackYaw;
    private int cooldownTicks;
    private String lastMove = "";
    private int sameMoveRepeats;
    private float staggerValue;
    private long lastStaggerHitTick = Long.MIN_VALUE;
    private boolean staggered;
    private long staggerEndTick;
    private boolean phaseTwoTriggered;
    private final Set<Integer> hitTargets = new HashSet<>();
    /** 落地后短暂不行动，避免刚生成就贴脸 */
    private int spawnGraceTicks = 10;
    private final ServerBossEvent healthBar = new ServerBossEvent(Component.translatable("entity.eldencombat.margit"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.PROGRESS);

    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); healthBar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); healthBar.removePlayer(player); }
    @Override public void tick() {
        super.tick();
        if(level() instanceof ServerLevel) {
            if (actionRunning) {
                setYRot(attackYaw);
                setYHeadRot(attackYaw);
                yBodyRot = attackYaw;
            } else if (getTarget() != null && !staggered && isAlive()) {
                faceTarget(getTarget(), 25f);
            }
            healthBar.setProgress(Math.max(0,Math.min(1,getHealth()/getMaxHealth())));
            healthBar.setVisible(isAlive());
            com.dsh.eldencombat.server.CombatEffects.bossTick(this);
        }
    }

    public MargitEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        if (getAttribute(Attributes.MAX_HEALTH) != null) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(EldenCombat.config().boss.maxHealth);
            setHealth(EldenCombat.config().boss.maxHealth);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 0.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ANIM, ANIM_IDLE)
                .define(DATA_ANIM_START, 0)
                .define(DATA_PHASE, 1)
                .define(DATA_STAGGER, 0.0f)
                .define(DATA_BROKEN, false);
    }

    @Override
    protected void registerGoals() {
        // 移动与攻击完全由 customServerAiStep 控制，不使用原版近战目标
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    protected float getSoundVolume() {
        return 1.0f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    // ------------------------------------------------------------------
    // 同步与查询
    // ------------------------------------------------------------------

    public String animClip() {
        return entityData.get(DATA_ANIM);
    }

    public int animStartTick() {
        return entityData.get(DATA_ANIM_START);
    }

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    public float staggerValue() {
        return entityData.get(DATA_STAGGER);
    }

    public boolean isStaggered() {
        return staggered;
    }

    /** 崩解窗口（失衡打满后的 2 秒易伤期）是否开启；客户端镜像用同一个字段。 */
    public boolean isBroken() {
        return entityData.get(DATA_BROKEN);
    }

    /**
     * 本次受击的伤害倍率：崩解期 ×{@code poise.brokenDamageMultiplier}，其余为 1。
     *
     * <p>所有对 Boss 的伤害路径（普攻 {@code HitResolver}、战技 {@code SkillHitEffects}、
     * 金色波动体）都必须乘这个倍率，否则"削韧 payoff"只对其中一种打法生效。
     */
    public float damageTakenMultiplier() {
        return EldenCombat.config().poiseDamageTakenMultiplier(isBroken());
    }

    /** 当前失衡上限（按在场玩家数缩放，多人不会秒破防）。 */
    public float poiseMax() {
        int players = level().players().size();
        return EldenCombat.config().poiseMaxFor(players);
    }

    public String actionClip() {
        return actionClip;
    }

    public boolean isActionRunning() {
        return actionRunning;
    }

    /** 本次攻击是否已经命中过该实体（同一动作序号对同一目标只结算一次） */
    public boolean markHit(int entityId) {
        return hitTargets.add(entityId);
    }

    private void setClip(String clip, long tick) {
        this.actionClip = clip;
        this.clipStartTick = tick;
        this.entityData.set(DATA_ANIM, clip);
        this.entityData.set(DATA_ANIM_START, (int) tick);
    }

    // ------------------------------------------------------------------
    // 失衡（削韧）
    // ------------------------------------------------------------------

    /** 增加削韧。硬直期间不累计下一次失衡。 */
    public void addStagger(float amount, long tick) {
        if (staggered) {
            return;
        }
        CombatConfig config = EldenCombat.config();
        float limit = poiseMax();
        this.staggerValue = Math.min(limit, this.staggerValue + amount);
        this.lastStaggerHitTick = tick;
        this.entityData.set(DATA_STAGGER, this.staggerValue);
        if (this.staggerValue >= limit - 1.0e-4f) {
            enterStagger(tick);
        }
    }

    /**
     * 进入崩解：硬直 + 易伤窗口。
     *
     * <p>窗口时长取 {@code max(硬直动作时长, config.poise.brokenTicks)}——
     * 动作必须播完，同时保证 2 秒的窗口下限（§6 的 "+50% 易伤" 就在这个窗口里）。
     * 客户端表现：原版发光描边（{@code setGlowingTag} 会同时置位"客户端可见的共享标志位 6"，
     * 客户端 {@code isCurrentlyGlowing()} 读的就是它）+ HUD 失衡条爆闪（读同步的 {@code DATA_BROKEN}）。
     *
     * <p>别用 {@code hasGlowingTag()} 判断"客户端能不能看到描边"：那个字段**不同步**
     * （客户端恒为 false，会误判成"描边没同步"），可见性一律用 {@code isCurrentlyGlowing()}。
     */
    private void enterStagger(long tick) {
        CombatConfig config = EldenCombat.config();
        this.staggered = true;
        this.staggerValue = 0f;
        this.entityData.set(DATA_STAGGER, 0f);
        this.entityData.set(DATA_BROKEN, true);
        this.setGlowingTag(true);
        this.hitTargets.clear();
        this.actionRunning = false;
        this.cooldownTicks = 0;
        this.getNavigation().stop();
        this.staggerEndTick = tick + config.poiseBreakTicks(lengthOf(ANIM_STAGGER));
        setClip(ANIM_STAGGER, tick);
    }

    private void exitStagger(CombatConfig config) {
        this.staggered = false;
        this.entityData.set(DATA_BROKEN, false);
        this.setGlowingTag(false);
        this.cooldownTicks = config.ai.actionCooldownTicks;
        setClip(ANIM_IDLE, level().getGameTime());
    }

    // ------------------------------------------------------------------
    // 主循环
    // ------------------------------------------------------------------

    @Override
    protected void customServerAiStep() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long tick = serverLevel.getGameTime();
        CombatConfig config = EldenCombat.config();

        if (isDeadOrDying()) {
            return;
        }
        if (spawnGraceTicks > 0) {
            spawnGraceTicks--;
            return;
        }

        // 硬直：取消当前攻击与武器显示，结束后恢复 AI
        if (staggered) {
            if (tick >= staggerEndTick) {
                exitStagger(config);
            }
            return;
        }

        // 二阶段：只触发一次，保持剩余血量
        if (!phaseTwoTriggered && getHealth() <= getMaxHealth() * config.boss.phaseTwoThreshold) {
            phaseTwoTriggered = true;
            entityData.set(DATA_PHASE, 2);
        }

        // 进行中的动作
        if (actionRunning) {
            tickAction(serverLevel, tick);
            return;
        }

        // 失衡值自然恢复
        if (staggerValue > 0 && tick - lastStaggerHitTick >= config.poise.regenDelayTicks) {
            staggerValue = Math.max(0f, staggerValue - config.poise.regenPerTick);
            entityData.set(DATA_STAGGER, staggerValue);
        }

        LivingEntity target = resolveTarget(serverLevel, tick, config);
        if (target == null) {
            getNavigation().stop();
            if (!ANIM_IDLE.equals(animClip())) {
                setClip(ANIM_IDLE, tick);
            }
            return;
        }

        faceTarget(target, 25f);
        double distance = distanceTo(target);
        if (cooldownTicks > 0) {
            cooldownTicks--;
            getNavigation().stop();
            // 停步观察
            getLookControl().setLookAt(target, 20f, 20f);
            if (!ANIM_IDLE.equals(animClip())) {
                setClip(ANIM_IDLE, tick);
            }
            return;
        }

        if (distance > config.ai.slamRangeMax || !hasLineOfSight(target)) {
            getNavigation().moveTo(target, config.ai.chaseSpeed / getAttributeValue(Attributes.MOVEMENT_SPEED));
            if (!ANIM_WALK.equals(animClip())) {
                setClip(ANIM_WALK, tick);
            }
            return;
        }

        getNavigation().stop();
        String move = chooseMove(config, distance);
        if (move == null) {
            // 距离不满足任何起手条件：走近一点再打
            getNavigation().moveTo(target, config.ai.chaseSpeed / getAttributeValue(Attributes.MOVEMENT_SPEED));
            if (!ANIM_WALK.equals(animClip())) {
                setClip(ANIM_WALK, tick);
            }
            return;
        }
        startAction(move, tick);
    }

    @Nullable
    private LivingEntity resolveTarget(ServerLevel level, long tick, CombatConfig config) {
        LivingEntity current = getTarget();
        if (current != null && (!current.isAlive() || current.isSpectator()
                || current instanceof Player player && player.isCreative()
                || current.level() != level || distanceTo(current) > config.ai.loseTargetRange)) {
            // 脱战不保留上一目标
            setTarget(null);
            current = null;
        }
        if (current == null) {
            Player nearest = level.getNearestPlayer(getX(), getY(), getZ(), config.lockOn.range,
                    candidate -> candidate instanceof Player player && player.isAlive()
                            && !player.isSpectator() && !player.isCreative());
            // 创造/旁观玩家永远不是战斗目标。上面那段"脱战"分支会把创造目标丢掉，这一行以前只看
            // isAlive/!isSpectator，于是紧接着又把它抓回来：创造模式下玛尔基特因此每 tick 都在
            // "丢目标→重新锁定"，表现就是 BUG 反馈里的"仍有攻击欲望"。
            if (nearest != null && nearest.isAlive() && !nearest.isSpectator() && !nearest.isCreative()) {
                setTarget(nearest);
                current = nearest;
            }
        }
        return current;
    }

    /** 加权候选 + 上次招式记录：同一招不连续超过 maxSameMoveRepeat 次。 */
    @Nullable
    private String chooseMove(CombatConfig config, double distance) {
        Map<String, Double> weights = config.ai.phaseWeights.get(phaseTwoTriggered ? "2" : "1");
        if (weights == null || weights.isEmpty()) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        List<Double> cumulative = new ArrayList<>();
        double total = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            String move = entry.getKey();
            if (!inStartRange(config, move, distance)) {
                continue;
            }
            if (move.equals(lastMove) && sameMoveRepeats >= config.ai.maxSameMoveRepeat) {
                continue;
            }
            total += entry.getValue();
            candidates.add(move);
            cumulative.add(total);
        }
        if (candidates.isEmpty() || total <= 0) {
            return null;
        }
        double roll = getRandom().nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            if (roll <= cumulative.get(i)) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    /** 攻击只能在定义的距离与视线范围内起手。 */
    private boolean inStartRange(CombatConfig config, String move, double distance) {
        if (isMeleeMove(move)) {
            return distance <= (ANIM_DAGGER_SLASH.equals(move) ? Math.min(1.9, config.ai.meleeRange) : config.ai.meleeRange);
        }
        return distance >= config.ai.slamRangeMin && distance <= config.ai.slamRangeMax;
    }

    private static boolean isMeleeMove(String move) {
        return ANIM_STAFF_SWEEP.equals(move) || ANIM_DAGGER_SLASH.equals(move);
    }

    private void startAction(String clip, long tick) {
        this.actionRunning = true;
        this.attackYaw = getYRot();
        this.hitTargets.clear();
        this.getNavigation().stop();
        this.setDeltaMovement(0, getDeltaMovement().y, 0);
        setClip(clip, tick);
        if (clip.equals(lastMove)) {
            sameMoveRepeats++;
        } else {
            lastMove = clip;
            sameMoveRepeats = 1;
        }
        CoreLog.LOGGER.debug("[Margit] 起手 {}", clip);
    }

    /** 动作时间线：命中窗口直接用动作数据的 hit_start/hit_end，不另设重复计时器。 */
    private void tickAction(ServerLevel level, long tick) {
        setYRot(attackYaw);
        setYHeadRot(attackYaw);
        yBodyRot = attackYaw;
        int tickInAction = (int) (tick - clipStartTick);
        int length = lengthOf(actionClip);
        if (tickInAction >= length) {
            actionRunning = false;
            cooldownTicks = EldenCombat.config().ai.actionCooldownTicks;
            setClip(ANIM_IDLE, tick);
            return;
        }

        // 前摇前半段缓慢转向，后半段固定朝向（不再重新设置看向点）
        LivingEntity target = getTarget();
        if (target != null) {
            int windupEnd = windupEnd(actionClip);
            if (tickInAction < windupEnd / 2) {
                faceTarget(target, EldenCombat.config().ai.turnSlowDegreesPerTick);
                attackYaw = getYRot();
            }
        }

        if (SkeletonData.isHitWindowOpen(actionClip, tickInAction, fallbackHitStart(actionClip), fallbackHitEnd(actionClip))) {
            HitResolver.resolveBossAttack(level, this, actionClip, tickInAction);
        }
    }

    private int windupEnd(String clip) {
        var assets = SkeletonData.assets();
        if (assets != null && assets.hasClip(clip) && assets.clip(clip).hitStart() >= 0) return assets.clip(clip).hitStart();
        return fallbackHitStart(clip);
    }

    private void faceTarget(LivingEntity target, float limit) {
        float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - getZ(), target.getX() - getX())) - 90f;
        float facing = net.minecraft.util.Mth.approachDegrees(getYRot(), yaw, limit);
        setYRot(facing);
        setYHeadRot(facing);
        yBodyRot = facing;
    }

    private static int fallbackHitStart(String clip) {
        return switch (clip) {
            case ANIM_STAFF_SWEEP -> 15;
            case ANIM_STAFF_SLAM -> 23;
            case ANIM_DAGGER_SLASH -> 8;
            case ANIM_HAMMER_SLAM -> 30;
            default -> Integer.MAX_VALUE;
        };
    }

    private static int fallbackHitEnd(String clip) {
        return switch (clip) {
            case ANIM_STAFF_SWEEP -> 21;
            case ANIM_STAFF_SLAM -> 27;
            case ANIM_DAGGER_SLASH -> 12;
            case ANIM_HAMMER_SLAM -> 35;
            default -> Integer.MAX_VALUE;
        };
    }

    private int lengthOf(String clip) {
        return SkeletonData.clipLength(clip, FALLBACK_LENGTHS.getOrDefault(clip, 40));
    }

    @Override
    public void die(DamageSource source) {
        healthBar.setVisible(false);
        healthBar.removeAllPlayers();
        // 死亡：取消所有命中窗口，播放 death 后再清理
        this.actionRunning = false;
        this.hitTargets.clear();
        this.staggered = false;
        this.entityData.set(DATA_BROKEN, false);
        this.setGlowingTag(false);
        this.getNavigation().stop();
        if (level() instanceof ServerLevel serverLevel) {
            setClip(ANIM_DEATH, serverLevel.getGameTime());
        }
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= DEATH_LINGER_TICKS && !this.level().isClientSide()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }
}
