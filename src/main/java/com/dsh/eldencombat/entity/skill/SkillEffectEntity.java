package com.dsh.eldencombat.entity.skill;

import com.dsh.eldencombat.effects.SkillEffectMesh;
import com.dsh.eldencombat.effects.SkillSlashSprites;
import com.dsh.eldencombat.registry.ModEntities;
import com.dsh.eldencombat.server.skills.SkillDriver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Synced visual only. No damage, collision, persistence, or per-vertex network traffic. */
public final class SkillEffectEntity extends Entity {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SkillEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SkillEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(SkillEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(SkillEffectEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(SkillEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SkillEffectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockState> GROUND = SynchedEntityData.defineId(SkillEffectEntity.class, EntityDataSerializers.BLOCK_STATE);
    private ServerPlayer owner;
    private int castId;

    public SkillEffectEntity(EntityType<? extends SkillEffectEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 10); builder.define(RADIUS, 1f); builder.define(DURATION, 10);
        builder.define(START, 0L); builder.define(PHASE, 0); builder.define(OWNER, -1);
        builder.define(GROUND, Blocks.STONE.defaultBlockState());
    }

    public static SkillEffectEntity spawn(ServerPlayer player, Vec3 origin, SkillEffectMesh.Kind kind, float radius,
                             int duration, int phase, boolean followOwner) {
        SkillEffectEntity effect = ModEntities.SKILL_EFFECT.get().create(player.level());
        if (effect == null) return null;
        effect.entityData.set(KIND, kind.wireId());
        effect.entityData.set(RADIUS, SkillEffectMesh.radius(radius));
        effect.entityData.set(DURATION, Math.clamp(duration, 1, 40));
        effect.entityData.set(START, player.level().getGameTime());
        effect.entityData.set(PHASE, Math.floorMod(phase, 3));
        if (kind == SkillEffectMesh.Kind.GOLD_IMPACT || kind == SkillEffectMesh.Kind.GRAVITY_IMPACT
                || kind == SkillEffectMesh.Kind.GRAVITY_PULL) {
            BlockState ground = player.level().getBlockState(BlockPos.containing(origin).below());
            effect.entityData.set(GROUND, ground.getRenderShape() == RenderShape.MODEL ? ground : Blocks.STONE.defaultBlockState());
        }
        if (followOwner) {
            effect.owner = player;
            effect.castId = SkillDriver.cast(player).castId();
            effect.entityData.set(OWNER, player.getId());
        }
        effect.moveTo(origin.x, origin.y, origin.z, player.getYRot(), 0);
        player.level().addFreshEntity(effect);
        return effect;
    }

    public SkillEffectMesh.Kind kind() { return SkillEffectMesh.Kind.fromWire(entityData.get(KIND)); }
    public float radius() { return entityData.get(RADIUS); }
    public int phase() { return entityData.get(PHASE); }
    public int ownerEntityId() { return entityData.get(OWNER); }
    public BlockState groundState() { return entityData.get(GROUND); }
    public float progress(float partialTick) {
        return SkillEffectMesh.progress(level().getGameTime() - entityData.get(START) + partialTick, entityData.get(DURATION));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (level().getGameTime() - entityData.get(START) >= entityData.get(DURATION)) { discard(); return; }
        if (owner != null) {
            var cast = SkillDriver.cast(owner);
            if (owner.isRemoved() || !owner.isAlive() || owner.level() != level()
                    || !cast.isCasting() || cast.castId() != castId) { discard(); return; }
            setPos(owner.position());
            setYRot(owner.getYRot());
        }
    }

    @Override public AABB getBoundingBoxForCulling() {
        float extent = SkillSlashSprites.cullingRadius(kind(), radius());
        return getBoundingBox().inflate(extent, 6, extent);
    }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance <= 128 * 128; }
    @Override public boolean isPickable() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
