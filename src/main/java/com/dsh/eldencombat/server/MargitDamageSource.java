package com.dsh.eldencombat.server;

import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.entity.MargitEntity;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Boss 攻击用的伤害源（纯原版）。
 *
 * <p>去掉 Epic Fight 之后不再需要自定义 {@code EpicFightDamageSource}：
 * 格挡、弹反、翻滚无敌都在 {@link HitResolver#applyBossHit} 里**先于**受伤结算处理，
 * 真正落到 {@code LivingEntity.hurt} 的只是原版 mobAttack，用来触发受击反馈与死亡信息。
 */
public final class MargitDamageSource {

    private MargitDamageSource() {
    }

    /** 某次 Boss 攻击对应的原版伤害源；{@code attack} 参与语义标注（可弹反等由调用点自行判断）。 */
    public static DamageSource of(MargitEntity boss, CombatConfig.BossAttack attack) {
        return boss.damageSources().mobAttack(boss);
    }
}
