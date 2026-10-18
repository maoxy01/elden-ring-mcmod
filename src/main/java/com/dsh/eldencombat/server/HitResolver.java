package com.dsh.eldencombat.server;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.ActionSpec;
import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.core.CombatState;
import com.dsh.eldencombat.core.HitWindow;
import com.dsh.eldencombat.core.WeaponClass;
import com.dsh.eldencombat.core.geom.Box;
import com.dsh.eldencombat.core.geom.Collision;
import com.dsh.eldencombat.entity.MargitEntity;
import com.dsh.eldencombat.registry.ModAttachments;
import com.dsh.eldencombat.skeleton.AnimationClip;
import com.dsh.eldencombat.skeleton.HitVolumes;
import com.dsh.eldencombat.skeleton.SkeletonAssets;
import com.dsh.eldencombat.skeleton.SkeletonData;
import com.dsh.eldencombat.skeleton.SkeletonPose;
import com.dsh.eldencombat.skeleton.PlayerAssets;
import com.dsh.eldencombat.skeleton.ClipTiming;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;

/**
 * 服务端命中判定：骨骼采样 + 胶囊/盒扫掠 + 阻挡 raycast。
 * 判定体覆盖上一 tick 与当前 tick 两个位置，避免高速挥舞漏判；
 * 与人物整体碰撞盒（实体 AABB）是两个模块。
 */
public final class HitResolver {

    private HitResolver() {
    }

    // ------------------------------------------------------------------
    // 玩家 -> 目标
    // ------------------------------------------------------------------

    public static void resolvePlayerAttack(ServerPlayer player, CombatState state, ActionSpec spec, WeaponClass weaponClass) {
        long tick = player.level().getGameTime();
        int tickInAction = state.tickInAction(tick);
        HitWindow window = spec.activeWindow(tickInAction);
        if (window == null) {
            return;
        }
        CombatConfig config = EldenCombat.config();
        PlayerAssets assets = PlayerAssets.get();
        String id = PlayerAssets.clipId(state.action, false, state.actionSequence, 0, false);
        AnimationClip clip = assets.clip(weaponClass, state.actionSlim, id);
        double frame = ClipTiming.frame(clip, tickInAction, spec.totalTicks(), window.start(), window.endExclusive(), config.weapon(weaponClass).chargeFullTicks);
        double prevFrame = ClipTiming.frame(clip, Math.max(window.start(), tickInAction - 1), spec.totalTicks(), window.start(), window.endExclusive(), config.weapon(weaponClass).chargeFullTicks);
        PlayerAssets.Blade current = assets.sampledBlade(weaponClass, state.actionSlim, state.actionLeftHanded, id, frame);
        PlayerAssets.Blade previous = assets.sampledBlade(weaponClass, state.actionSlim, state.actionLeftHanded, id, prevFrame);
        Vector3d nowA = toWorld(player, player.getYRot(), current.from());
        Vector3d nowB = toWorld(player, player.getYRot(), current.to());
        Vector3d movement = new Vector3d(player.xo-player.getX(), player.yo-player.getY(), player.zo-player.getZ());
        Vector3d prevA = toWorld(player, player.yRotO, previous.from()).add(movement);
        Vector3d prevB = toWorld(player, player.yRotO, previous.to()).add(movement);
        double radius = current.radius();
        AABB search = new AABB(nowA.x, nowA.y, nowA.z, nowA.x, nowA.y, nowA.z)
            .minmax(new AABB(nowB.x, nowB.y, nowB.z, nowB.x, nowB.y, nowB.z))
            .minmax(new AABB(prevA.x, prevA.y, prevA.z, prevA.x, prevA.y, prevA.z))
            .minmax(new AABB(prevB.x, prevB.y, prevB.z, prevB.x, prevB.y, prevB.z)).inflate(radius);
        List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, search,
                candidate -> isValidAttackTarget(player, candidate));
        for (LivingEntity target : targets) {
            if (state.hasHit(target.getId())) {
                continue;
            }
            Box box = toBox(target.getBoundingBox());
            // 覆盖上一 tick 与当前 tick 的武器判定体
            if (!Collision.sweptCapsuleHitsBox(prevA, prevB, nowA, nowB, box, radius)) {
                continue;
            }
            if (isBlocked(player, target)) {
                continue;
            }
            if (!state.markHit(target.getId())) {
                continue;
            }
            // 护符加成 → 崩解易伤（§6：失衡打满后 2 秒内 +50% 伤害）→ §2 武器成长倍率
            float damage = window.damage() * Talismans.damageMultiplier(player);
            if (target instanceof MargitEntity broken) {
                damage *= broken.damageTakenMultiplier();
            }
            damage *= (float) com.dsh.eldencombat.item.WeaponStacks.damageMultiplier(
                    player.getMainHandItem(), PlayerAttributes.of(player));
            if (!target.hurt(player.damageSources().playerAttack(player), damage)) continue;
            CombatEffects.hit(player,target);
            if (target instanceof MargitEntity margit) {
                margit.addStagger(window.poiseDamage(), tick);
            }
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double[] dir = Collision.normalize2D(dx, dz);
            target.push(dir[0] * config.attackKnockback, 0.1, dir[1] * config.attackKnockback);
        }
    }

    private static boolean isValidAttackTarget(ServerPlayer player, LivingEntity candidate) {
        if (candidate == player || !candidate.isAlive() || candidate.isSpectator() || candidate.isInvulnerable()) {
            return false;
        }
        return candidate instanceof Enemy;
    }

    // ------------------------------------------------------------------
    // Boss -> 玩家
    // ------------------------------------------------------------------

    /**
     * Boss 攻击判定：窗口来自动作数据，判定体按初始模型确定
     * （木杖长轴胶囊、匕首短轴胶囊、黄金锤锤头扫掠盒）；不使用人体或尾巴。
     */
    public static void resolveBossAttack(ServerLevel level, MargitEntity boss, String clipId, int tickInAction) {
        CombatConfig.BossAttack attack = EldenCombat.config().bossAttacks.get(clipId);
        if (attack == null) {
            return;
        }
        long tick = level.getGameTime();
        SkeletonAssets assets = SkeletonData.assets();
        double yaw = boss.getYRot();

        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
                continue;
            }
            Box target = toBox(player.getBoundingBox());
            boolean hit;
            if (assets == null) {
                hit = fallbackHit(boss, player, yaw, attack);
            } else {
                hit = com.dsh.eldencombat.skeleton.BossHitGeometry.hits(assets, clipId, tickInAction, attack,
                        target, boss.getX(), boss.getY(), boss.getZ(), yaw);
            }
            if (!hit) {
                if (Boolean.getBoolean("eldencombat.epicSmoke") && tickInAction == assets.clip(clipId).hitStart())
                    EldenCombat.LOGGER.info("EPIC_SMOKE_GEOMETRY: {} boss={} yaw={} target={} distance={} no weapon contact",clipId,boss.position(),yaw,player.position(),boss.distanceTo(player));
                continue;
            }
            if (isBlocked(boss, player)) {
                continue;
            }
            if (!boss.markHit(player.getId())) {
                continue;
            }
            boolean damaged = player.hurt(MargitDamageSource.of(boss, attack),
                    attack.damage * Talismans.damageTakenMultiplier(player));
            if (Boolean.getBoolean("eldencombat.epicSmoke")) EldenCombat.LOGGER.info("EPIC_SMOKE_CONTACT: {} accepted={} playerHealth={}",clipId,damaged,player.getHealth());
        }
    }

    /** 玩家侧结算：翻滚无敌 -> 弹反 -> 盾防 -> 实际受伤。 */
    private static void applyBossHit(ServerPlayer player, MargitEntity boss, CombatConfig.BossAttack attack, long tick) {
        CombatConfig config = EldenCombat.config();
        CombatState state = player.getData(ModAttachments.COMBAT_STATE);
        state.ensureInit(config);

        // 翻滚无敌只过滤本系统战斗伤害，环境伤害不经过这里
        if (!com.dsh.eldencombat.compat.CombatRollCompat.present() && state.isRollInvulnerable(tick)) {
            return;
        }

        // 弹反：只影响标记为可弹反的攻击
        if (attack.parryable && Weapons.hasShield(player) && state.isParryWindow(tick)) {
            CombatEffects.blocked(player,false,true);
            state.parryChain = (tick - state.lastParryTick <= config.parryChainWindowTicks) ? state.parryChain + 1 : 1;
            state.lastParryTick = tick;
            boss.addStagger(config.parryPoiseDamage, tick);
            if (state.parryChain >= config.parryChainRequired) {
                state.parryChain = 0;
                boss.addStagger(config.poise.max, tick);
            }
            player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.4f);
            return;
        }

        // 盾防：只覆盖玩家面前 120 度
        if (Weapons.hasShield(player) && state.shieldActive(tick)) {
            double toBossX = boss.getX() - player.getX();
            double toBossZ = boss.getZ() - player.getZ();
            double lookX = -Math.sin(Math.toRadians(player.getYRot()));
            double lookZ = Math.cos(Math.toRadians(player.getYRot()));
            if (Collision.withinCone(lookX, lookZ, toBossX, toBossZ, config.guard.coneDegrees)) {
                if (state.hasStamina(attack.guardCost)) {
                    CombatEffects.blocked(player,false,false);
                    state.stamina -= attack.guardCost;
                    state.lastStaminaUseTick = tick;
                    state.guardImpactTick = tick;
                    player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                    return;
                }
                // 精力耗尽：破防硬直，不能继续挡
                state.stamina = 0f;
                CombatEffects.blocked(player,true,false);
                state.guarding = false;
                state.acceptAction(config.guardBrokenSpec(), tick);
                state.guardBrokenUntilTick = tick + config.guard.breakTicks;
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                return;
            }
        }

        // 命中：打断当前动作、重置弹反连计
        state.clearAction();
        state.guarding = false;
        state.parryChain = 0;
        player.hurt(boss.damageSources().mobAttack(boss), attack.damage);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static SkeletonPose poseFor(SkeletonAssets assets, String clipId, int tickInAction) {
        AnimationClip clip = assets.clip(clipId);
        return SkeletonPose.of(assets.margit(), clip.poseAt(tickInAction));
    }

    private static HitVolumes.Capsule capsuleFor(SkeletonAssets assets, String clipId, SkeletonPose pose,
                                                 CombatConfig.BossAttack attack) {
        if (MargitEntity.ANIM_DAGGER_SLASH.equals(clipId)) {
            SkeletonPose accessory = SkeletonPose.of(assets.spectralDagger(), Map.of())
                    .attachTo(pose, "spectral_socket");
            var matrix = accessory.boneWorld("root");
            return new HitVolumes.Capsule(new Vector3d(matrix.transformPosition(new org.joml.Vector3f(0,4.5f/16,0))),
                new Vector3d(matrix.transformPosition(new org.joml.Vector3f(0,18f/16,0))),attack.capsuleRadius);
        }
        if (assets.margit().hasBone(attack.hitBone)) {
            return HitVolumes.cubeAxisCapsule(pose, "staff_shaft", attack.capsuleRadius);
        }
        return null;
    }

    /** 匕首根部是握持点（模型原点），取离原点最远的角作为刀尖。 */
    private static Vector3d farthest(Box box) {
        Vector3d best = new Vector3d(box.maxX(), box.maxY(), box.maxZ());
        double bestDistance = best.lengthSquared();
        for (int i = 0; i < 8; i++) {
            Vector3d corner = new Vector3d(
                    (i & 1) == 0 ? box.minX() : box.maxX(),
                    (i & 2) == 0 ? box.minY() : box.maxY(),
                    (i & 4) == 0 ? box.minZ() : box.maxZ());
            double distance = corner.lengthSquared();
            if (distance > bestDistance) {
                bestDistance = distance;
                best = corner;
            }
        }
        return best;
    }

    private static Box hammerHeadBox(SkeletonAssets assets, SkeletonPose pose) {
        SkeletonPose hammer = SkeletonPose.of(assets.spectralHammer(), Map.of()).attachTo(pose, "spectral_socket");
        return HitVolumes.cubeBox(hammer, "head");
    }

    private static boolean fallbackHit(MargitEntity boss, ServerPlayer player, double yaw, CombatConfig.BossAttack attack) {
        // 资产缺失时的保守回退：正前方 3.5 方块内的玩家
        double dx = player.getX() - boss.getX();
        double dz = player.getZ() - boss.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > 3.5) {
            return false;
        }
        double lookX = -Math.sin(Math.toRadians(yaw));
        double lookZ = Math.cos(Math.toRadians(yaw));
        return Collision.withinCone(lookX, lookZ, dx, dz, 120.0);
    }

    /** 建模空间（单位方块，原点在脚底、朝向 -Z）-> 世界坐标。 */
    public static Vector3d toWorld(Entity entity, double yawDegrees, Vector3d modelPoint) {
        double theta = Math.toRadians(180.0 - yawDegrees);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        // Authored player sides use the opposite X convention from Minecraft's main arm.
        double modelX = entity instanceof net.minecraft.world.entity.player.Player ? -modelPoint.x : modelPoint.x;
        double x = modelX * cos + modelPoint.z * sin;
        double z = -modelX * sin + modelPoint.z * cos;
        return new Vector3d(entity.getX() + x, entity.getY() + modelPoint.y, entity.getZ() + z);
    }

    private static Box worldBox(Entity entity, double yawDegrees, Box modelBox) {
        Vector3d[] corners = {
                new Vector3d(modelBox.minX(), modelBox.minY(), modelBox.minZ()),
                new Vector3d(modelBox.minX(), modelBox.minY(), modelBox.maxZ()),
                new Vector3d(modelBox.minX(), modelBox.maxY(), modelBox.minZ()),
                new Vector3d(modelBox.minX(), modelBox.maxY(), modelBox.maxZ()),
                new Vector3d(modelBox.maxX(), modelBox.minY(), modelBox.minZ()),
                new Vector3d(modelBox.maxX(), modelBox.minY(), modelBox.maxZ()),
                new Vector3d(modelBox.maxX(), modelBox.maxY(), modelBox.minZ()),
                new Vector3d(modelBox.maxX(), modelBox.maxY(), modelBox.maxZ())
        };
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vector3d corner : corners) {
            Vector3d world = toWorld(entity, yawDegrees, corner);
            minX = Math.min(minX, world.x);
            minY = Math.min(minY, world.y);
            minZ = Math.min(minZ, world.z);
            maxX = Math.max(maxX, world.x);
            maxY = Math.max(maxY, world.y);
            maxZ = Math.max(maxZ, world.z);
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean overlaps(Box a, Box b) {
        return a.maxX() >= b.minX() && a.minX() <= b.maxX()
                && a.maxY() >= b.minY() && a.minY() <= b.maxY()
                && a.maxZ() >= b.minZ() && a.minZ() <= b.maxZ();
    }

    public static Box toBox(AABB aabb) {
        return new Box(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    /** 阻挡方块 raycast：隔墙不判定命中。 */
    private static boolean isBlocked(Entity attacker, Entity target) {
        Vec3 start = attacker.getEyePosition();
        Vec3 end = target.getBoundingBox().getCenter();
        BlockHitResult result = attacker.level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
        return result.getType() != HitResult.Type.MISS;
    }
}
