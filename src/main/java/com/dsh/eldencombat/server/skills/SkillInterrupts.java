package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

/**
 * 施放中断接线：把"切武器"和"受击"两条现实事件接到 {@link SkillDriver#cancel}。
 *
 * <p>原版方案下事件源改成 NeoForge 的装备变化与受伤后置事件
 * （原先挂在 Epic Fight 的 {@code CHANGE_INNATE_SKILL} / {@code TAKE_DAMAGE_POST} 上）。
 * 死亡、离线、换维度、超时已经在 {@code SkillServerEvents}/{@code SkillDriver} 里处理。
 */
@EventBusSubscriber(modid = EldenCombat.MODID)
public final class SkillInterrupts {

    private SkillInterrupts() {
    }

    /** 主手武器变化：新武器不再对应正在施放的战技时中断。 */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSlot() != EquipmentSlot.MAINHAND) {
            return;
        }
        SkillCastState cast = SkillDriver.cast(player);
        if (!cast.isCasting()) {
            return;
        }
        RemembranceSkillSpec spec = SkillDriver.spec(cast.skillId());
        if (spec == null) {
            return;
        }
        ResourceLocation held = BuiltInRegistries.ITEM.getKey(event.getTo().getItem());
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, spec.itemId());
        if (!expected.equals(held)) {
            SkillDriver.cancel(player, player.level().getGameTime(), "切换武器导致专属技能变化");
        }
    }

    /** 受击中断：只撤销本技能施加的临时状态，不退还已确认的 FP。 */
    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getNewDamage() <= 0.0f) {
            return;
        }
        SkillDriver.cancel(player, player.level().getGameTime(), "受击中断");
    }
}
