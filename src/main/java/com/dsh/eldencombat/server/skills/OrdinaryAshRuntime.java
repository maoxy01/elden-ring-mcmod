package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.geom.Collision;
import com.dsh.eldencombat.effects.SkillEffectMesh.Kind;
import com.dsh.eldencombat.entity.skill.SkillEffectEntity;
import com.dsh.eldencombat.server.HitResolver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * 普通战灰（Ash of War）的运行时。
 *
 * <p>**刻意与 {@link RemembranceRuntime} 分开**：{@code AshBindings.Definition.implemented()}
 * 明确排斥 {@code RemembranceRuntime}，所以普通战灰不可能借追忆战技的时间线冒充实现。
 *
 * <p>边界（用户要求"简单的战技释放动作，不要给布偶添加多余的关节"）：
 * <ul>
 *   <li>**单段**：只有 {@link #onActive} 有实际动作；没有追击段、没有多段状态机；</li>
 *   <li>**不位移**：不做跃起/突进，因此没有落地安全网、悬空追踪与残留清理，
 *       {@link #onAbort} 也就**没有**需要收回的东西——这正是它不能、也不该结算伤害的原因；</li>
 *   <li>**无独立缓存**：本类不持有任何 per-player 状态，{@link #onCancel}/{@link #onFinish}/{@link #onDetach}
 *       因此不需要实现（默认空实现就是正确行为）；唯一生成物是当帧的特效实体，它自己会过期消失；</li>
 *   <li>命中用与追忆战技**同一套**服务端判定助手（{@link SkillHitEffects} + 胶囊判交），
 *       表现复用已有的 {@link Kind}，不新增美术。</li>
 * </ul>
 */
public final class OrdinaryAshRuntime implements SkillDriver.Runtime {

    @Override public void onActive(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        sound(player, soundFor(spec.id()));
        visual(player, visualFor(spec.id()), spec.effectRadius(), cast.stageIndex());
        // 判定形状按战灰分两种：绕身横扫（回旋斩）与身前扫掠（刺/斩）。
        if ("eldencombat:spinning_slash".equals(spec.id())) {
            whirl(player, cast, spec);
        } else {
            sweep(player, cast, spec);
        }
    }

    /** 绕身横扫：以玩家为中心的圆形判定（回旋斩的"回旋"）。 */
    private void whirl(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec) {
        double radius = Math.max(1.0, spec.effectRadius());
        AABB bounds = player.getBoundingBox().inflate(radius, 1.5, radius);
        int hits = 0;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, bounds)) {
            double dx = Math.max(0.0, player.getX() - target.getX());
            double dz = Math.max(0.0, player.getZ() - target.getZ());
            if (target.distanceTo(player) > radius + 1.0) continue;
            if (dx * dx + dz * dz > radius * radius + 1.0) continue;
            if (land(player, cast, spec, target, 0)) hits++;
        }
        EldenCombat.LOGGER.debug("[ash] {} 绕身判定命中 {} 个目标", spec.id(), hits);
    }

    /** 身前扫掠：手中兵器的胶囊判定（从胸口沿视线伸出 effectRange 格）。 */
    private void sweep(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec) {
        Vec3 look = player.getLookAngle();
        double reach = Math.max(1.5, spec.effectRange());
        Vector3d hand = new Vector3d(player.getX(), player.getY() + 1.2, player.getZ());
        Vector3d tip = new Vector3d(hand.x + look.x * reach, hand.y + look.y * reach, hand.z + look.z * reach);
        double radius = Math.max(0.55, spec.effectRadius() * 0.35);
        AABB search = new AABB(hand.x, hand.y, hand.z, tip.x, tip.y, tip.z).inflate(radius + 1.0);
        int hits = 0;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, search)) {
            if (target.distanceTo(player) > reach + 1.5) continue;
            if (!Collision.capsuleHitsBox(hand, tip, HitResolver.toBox(target.getBoundingBox()), radius)) continue;
            if (land(player, cast, spec, target, 0)) hits++;
        }
        EldenCombat.LOGGER.debug("[ash] {} 身前判定命中 {} 个目标", spec.id(), hits);
    }

    /** 同一段/同一通道只结算一次（与追忆战技共用 {@link SkillCastState} 的去重规则）。 */
    private boolean land(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, LivingEntity target, int channel) {
        if (!SkillHitEffects.validTarget(player, target)
                || !SkillHitEffects.visible(player, player.getEyePosition(), target)
                || !cast.markHit(target.getId(), channel)) return false;
        if (SkillHitEffects.hurt(player, target, spec.damage(), spec.poiseDamage(), false) <= 0) return false;
        cast.markStageResolved();
        cast.markResolvedChannel(channel);
        return true;
    }

    private void visual(ServerPlayer player, Kind kind, double radius, int phase) {
        SkillEffectEntity.spawn(player, player.position(), kind, (float) radius, 10, phase, true);
    }

    private void sound(ServerPlayer player, SoundEvent event) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                event, SoundSource.PLAYERS, .7f, 1.0f);
    }

    private static SoundEvent soundFor(String id) {
        return switch (id) {
            case "eldencombat:spinning_slash" -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case "eldencombat:heavy_thrust" -> SoundEvents.PLAYER_ATTACK_STRONG;
            default -> SoundEvents.PLAYER_ATTACK_CRIT;
        };
    }

    private static Kind visualFor(String id) {
        return switch (id) {
            case "eldencombat:spinning_slash" -> Kind.CURSED_SLASH;
            case "eldencombat:heavy_thrust" -> Kind.DEATH_SLASH;
            default -> Kind.GOLD_IMPACT;
        };
    }
}
