package com.dsh.eldencombat.server;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** One incoming reduction point, after armor and before absorption. Applies in either R mode. */
@EventBusSubscriber(modid = EldenCombat.MODID)
public final class TalismanCombatEvents {
    public static final TagKey<DamageType> PHYSICAL = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "talisman_physical"));

    private TalismanCombatEvents() {}

    @SubscribeEvent
    public static void incoming(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        var source = event.getSource();
        var totals = Talismans.totals(player);
        // getEntity resolves the owner of arrows and other indirect player attacks.
        boolean pvp = source.getEntity() instanceof net.minecraft.world.entity.player.Player;
        boolean physical = source.is(PHYSICAL);
        boolean attack = source.getEntity() != null || source.getDirectEntity() != null;
        float multiplier = totals.damageTakenMultiplier()
                * totals.effects().incomingMultiplier(attack, physical, pvp);
        event.setNewDamage(event.getNewDamage() * multiplier);
    }
}
