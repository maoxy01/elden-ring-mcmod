package com.dsh.eldencombat.entity.skill;

import com.dsh.eldencombat.server.skills.WaveStep;
import com.dsh.eldencombat.server.skills.SkillHitEffects;
import com.dsh.eldencombat.effects.WaveFront;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 地面波命中体（黄金波动 / 掠夺火焰共用模板）。
 *
 * <p>服务端权威：方向、寿命、最远距离、墙体遮挡、每目标只结算一次，全部在服务端算；
 * 客户端用同步方向、半径绘制阶梯波面；伤害框不依赖特效网格。
 */
public class GoldenWaveEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(GoldenWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_FLAME =
            SynchedEntityData.defineId(GoldenWaveEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_DIR_X =
            SynchedEntityData.defineId(GoldenWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DIR_Z =
            SynchedEntityData.defineId(GoldenWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TRAVEL =
            SynchedEntityData.defineId(GoldenWaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_BLOCKED =
            SynchedEntityData.defineId(GoldenWaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PROGRESS =
            SynchedEntityData.defineId(GoldenWaveEntity.class, EntityDataSerializers.FLOAT);
    private float previousRadius = 3f;
    private float previousTravel;
    private float previousProgress;

    /** 每 tick 推进距离（方块） */
    private double speed = 0.9;
    private double maxRange = 16.0;
    private int lifespanTicks = 30;
    private double baseRadius = 3.0;
    private double maxRadius = 4.2;
    private float damage = 12f;
    private float poiseDamage = 8f;
    /** 是否治疗施放者（掠夺火焰的比例不同，模板只给上限） */
    private float healCap;
    private float healed;
    private double dirX = 1.0;
    private double dirZ;
    private int travelledTicks;
    private boolean finished;
    private boolean reachedRange;

    @Nullable
    private UUID ownerId;
    private final Set<Integer> hitTargets = new HashSet<>();
    /** 本波是否已经产生过有效伤害（供吸血判定：没有伤害就没有收益） */
    private boolean dealtDamage;

    public GoldenWaveEntity(EntityType<? extends GoldenWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 3.0f);
        builder.define(DATA_FLAME, false);
        builder.define(DATA_DIR_X, 1f);
        builder.define(DATA_DIR_Z, 0f);
        builder.define(DATA_TRAVEL, 0f);
        builder.define(DATA_BLOCKED, 0);
        builder.define(DATA_PROGRESS, 0f);
    }

    /** 由技能在施放瞬间配置。 */
    public void configure(@Nullable LivingEntity owner, Vec3 direction, double speed, double maxRange,
                          int lifespanTicks, double baseRadius, double maxRadius, float damage,
                          float poiseDamage, float healCap) {
        this.ownerId = owner == null ? null : owner.getUUID();
        double length = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (length > 1.0e-6) {
            this.dirX = direction.x / length;
            this.dirZ = direction.z / length;
        }
        this.speed = Math.max(0.05, speed);
        this.maxRange = Math.max(1.0, maxRange);
        this.lifespanTicks = Math.max(1, lifespanTicks);
        this.baseRadius = Math.max(0.2, baseRadius);
        this.maxRadius = Math.max(this.baseRadius, maxRadius);
        this.damage = damage;
        this.poiseDamage = poiseDamage;
        this.healCap = Math.max(0f, healCap);
        this.entityData.set(DATA_RADIUS, (float) this.baseRadius);
        this.previousRadius = (float) this.baseRadius;
        this.entityData.set(DATA_DIR_X, (float) this.dirX);
        this.entityData.set(DATA_DIR_Z, (float) this.dirZ);
    }

    public float damage() {
        return damage;
    }

    public float poiseDamage() {
        return poiseDamage;
    }

    public boolean dealtDamage() {
        return dealtDamage;
    }

    public float healedAmount() {
        return healed;
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public void setFlame(boolean flame) { entityData.set(DATA_FLAME, flame); }

    public boolean isFlame() { return entityData.get(DATA_FLAME); }

    public double dirX() {
        return entityData.get(DATA_DIR_X);
    }

    public double dirZ() {
        return entityData.get(DATA_DIR_Z);
    }

    public int travelledTicks() {
        return travelledTicks;
    }

    public float travelDistance() { return entityData.get(DATA_TRAVEL); }
    public int blockedLanes() { return entityData.get(DATA_BLOCKED); }
    public float visualRadius(float partialTick) { return Mth.lerp(partialTick, previousRadius, radius()); }
    public float visualTravel(float partialTick) { return Mth.lerp(partialTick, previousTravel, travelDistance()); }
    public float visualProgress(float partialTick) {
        return Mth.lerp(partialTick, previousProgress, entityData.get(DATA_PROGRESS));
    }

    @Override
    public void tick() {
        previousRadius = radius();
        previousTravel = travelDistance();
        previousProgress = entityData.get(DATA_PROGRESS);
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (finished) {
            discard();
            return;
        }
        serverTick();
    }

    private void serverTick() {
        if (owner() == null || !owner().isAlive()) { discard(); return; }
        if (travelledTicks == 0) {
            // 起始位置对齐到施放者脚下的地面高度：向上/向下各探一次
            HitResult ground = level().clip(new ClipContext(position().add(0, 1, 0), position().add(0, -2, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (ground.getType() != HitResult.Type.BLOCK) { discard(); return; }
            setPos(getX(), ground.getLocation().y + 0.1, getZ());
        }

        double previousDistance = travelledTicks * speed;
        double previousRadius = radius();
        WaveStep.Step step = WaveStep.advance(getX(), getZ(), dirX, dirZ, speed, travelledTicks,
                lifespanTicks, maxRange, reachedRange);
        travelledTicks = step.travelledTicks();
        double radius = isFlame() ? WaveStep.radiusAt(baseRadius, maxRadius, travelledTicks, speed, maxRange)
                : WaveFront.radius(baseRadius, maxRadius, travelledTicks * speed);
        entityData.set(DATA_RADIUS, (float) radius);
        entityData.set(DATA_TRAVEL, (float) (travelledTicks * speed));
        entityData.set(DATA_PROGRESS, (float) Math.min(1, Math.max(travelledTicks * speed / maxRange,
                (double) travelledTicks / lifespanTicks)));

        Vec3 from = position();
        Vec3 to = new Vec3(step.x(), from.y, step.z());
        HitResult ground = level().clip(new ClipContext(to.add(0, 1.1, 0), to.add(0, -1.5, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (ground.getType() != HitResult.Type.BLOCK) { discard(); return; }
        to = new Vec3(to.x, ground.getLocation().y + .1, to.z);
        // 墙体遮挡：撞到方块即停止，不穿墙
        HitResult clip = level().clip(new ClipContext(from.add(0, .5, 0), to.add(0, .5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (clip.getType() == HitResult.Type.BLOCK) {
            hitAround(radius);
            finished = true;
            discard();
            return;
        }

        if (!isFlame()) updateBlockedLanes(from, to, previousRadius, previousDistance, radius);
        setPos(to);
        hitAround(radius);

        if (step.finished()) {
            finished = true;
            discard();
        }
    }

    private void hitAround(double radius) {
        if (damage <= 0) {
            return;
        }
        LivingEntity owner = owner();
        if (!(owner instanceof ServerPlayer player)) return;
        double trailing = isFlame() ? 0 : WaveFront.lag(1, travelDistance());
        AABB area = getBoundingBox().inflate(radius + trailing, 1.2, radius + trailing);
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != owner && candidate.isAlive() && !candidate.isSpectator()
                        && (owner == null || !owner.isAlliedTo(candidate)));
        for (LivingEntity target : candidates) {
            Vec3 relative = target.getBoundingBox().getCenter().subtract(position());
            double forward = relative.x * dirX + relative.z * dirZ;
            double lateral = relative.x * dirZ - relative.z * dirX;
            double margin = target.getBbWidth() * .5;
            int lane = WaveFront.lane(lateral, radius);
            double lag = isFlame() ? 0 : WaveFront.laneLag(lane, travelDistance());
            if ((blockedLanes() & (1 << lane)) != 0 || Math.abs(forward + lag) > .8 + margin || Math.abs(lateral) > radius + margin
                    || !SkillHitEffects.validTarget(player, target)) continue;
            // 之间是否有实墙：隔墙不判定
            Vec3 front = position().add(-dirX * lag + dirZ * lateral, .5, -dirZ * lag - dirX * lateral);
            HitResult blocked = level().clip(new ClipContext(front, target.getBoundingBox().getCenter(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blocked.getType() == HitResult.Type.BLOCK) {
                continue;
            }
            if (!hitTargets.add(target.getId())) continue;
            if (SkillHitEffects.hurtMagic(player, target, damage, poiseDamage) > 0) {
                dealtDamage = true;
                healOwner(owner);
            }
        }
    }

    private void updateBlockedLanes(Vec3 from, Vec3 to, double oldRadius, double oldDistance, double newRadius) {
        int blocked = blockedLanes();
        for (int lane = 0; lane < WaveFront.LANES; lane++) {
            if ((blocked & (1 << lane)) != 0) continue;
            double u = WaveFront.laneCenter(lane), oldLag = WaveFront.laneLag(lane, oldDistance), newLag = WaveFront.laneLag(lane, travelDistance());
            Vec3 oldFront = from.add(-dirX * oldLag + dirZ * u * oldRadius, .5, -dirZ * oldLag - dirX * u * oldRadius);
            Vec3 newFront = to.add(-dirX * newLag + dirZ * u * newRadius, .5, -dirZ * newLag - dirX * u * newRadius);
            if (level().clip(new ClipContext(oldFront, newFront, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                    .getType() == HitResult.Type.BLOCK) blocked |= 1 << lane;
        }
        entityData.set(DATA_BLOCKED, blocked);
    }

    private void healOwner(@Nullable LivingEntity owner) {
        if (owner == null || healCap <= 0f || healed >= healCap) {
            return;
        }
        float amount = Math.min(1.0f, healCap - healed);
        owner.heal(amount);
        healed += amount;
    }

    @Nullable
    public LivingEntity owner() {
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(ownerId);
        return entity instanceof LivingEntity living ? living : null;
    }

    public void markReachedRange() {
        this.reachedRange = true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance <= 128 * 128;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double trailing = isFlame() ? 0 : WaveFront.lag(1, travelDistance());
        return getBoundingBox().inflate(radius() + trailing + .5, 2, radius() + trailing + .5);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 效果实体不落盘：寿命短，重载不残留
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 同上
    }
}
