package com.dsh.eldencombat.server.skills;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;

public final class SkillHitEffects {
    private SkillHitEffects() {}

    public static boolean validTarget(ServerPlayer owner, LivingEntity target) {
        return owner != target && target.isAlive() && !target.isSpectator() && !owner.isAlliedTo(target)
                && (!(target instanceof Player player) || owner.canHarmPlayer(player));
    }

    public static boolean visible(ServerPlayer owner, Vec3 from, LivingEntity target) {
        return owner.level().clip(new ClipContext(from, target.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.BLOCK;
    }

    /**
     * 战技伤害结算（纯原版伤害源）。
     *
     * <p>{@code impact} 目前只用于调用方语义（削韧在 {@code cast.markStageResolved}/{@code MargitEntity.addStagger}
     * 里单独处理），不再挂到 Epic Fight 伤害源上。
     *
     * <p>每次命中前清零目标的受击无敌帧：战技是多段判定（水鸟乱舞、命定之死等），
     * 若沿用原版 20 tick 无敌帧，同一段内的连续命中会被静默丢弃。
     */
    public static float hurt(ServerPlayer owner, LivingEntity target, float damage, float impact, boolean dot) {
        DamageSource source = dot ? owner.damageSources().indirectMagic(owner, owner)
                : owner.damageSources().playerAttack(owner);
        return hurt(owner, target, damage, impact, dot, source);
    }

    /** Holy/magical projectiles must not inherit the physical player-attack damage type. */
    public static float hurtMagic(ServerPlayer owner, LivingEntity target, float damage, float impact) {
        return hurt(owner, target, damage, impact, false, owner.damageSources().indirectMagic(owner, owner));
    }

    private static float hurt(ServerPlayer owner, LivingEntity target, float damage, float impact, boolean dot,
                              DamageSource source) {
        if (!validTarget(owner, target)) return 0;
        // Skill boosts apply to direct skill hits, not fixed/status damage-over-time ticks.
        float applied = damage * (dot ? com.dsh.eldencombat.server.Talismans.damageMultiplier(owner)
                : com.dsh.eldencombat.server.Talismans.skillDamageMultiplier(owner));
        // 崩解易伤（§6）：只对崩解窗口内的 Boss 生效，未崩解时倍率为 1
        if (target instanceof com.dsh.eldencombat.entity.MargitEntity margit) {
            applied *= margit.damageTakenMultiplier();
        }
        // §2：武器成长倍率（强化等级 / 质变 / 属性补正）——战技同样吃武器成长
        applied *= (float) com.dsh.eldencombat.item.WeaponStacks.damageMultiplier(
                owner.getMainHandItem(), com.dsh.eldencombat.server.PlayerAttributes.effective(owner));
        target.invulnerableTime = 0;
        float before = target.getHealth();
        return target.hurt(source, applied) ? Math.max(0, before - target.getHealth()) : 0;
    }
}
