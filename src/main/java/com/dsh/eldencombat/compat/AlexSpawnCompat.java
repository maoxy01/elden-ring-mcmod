package com.dsh.eldencombat.compat;

import com.dsh.eldencombat.EldenCombat;
import com.github.alexthe666.alexsmobs.entity.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Restores seven omitted registrations in the exact 1.22.17 port, using its existing predicates.
 * The five leaf placements follow the original author's 1.20 registry; no entity or AI is copied.
 */
@EventBusSubscriber(modid=EldenCombat.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class AlexSpawnCompat {
    private AlexSpawnCompat() {}
    @SubscribeEvent public static void register(RegisterSpawnPlacementsEvent event) {
        var mod=ModList.get().getModContainerById("alexsmobs");
        if(mod.isEmpty() || !"1.22.17".equals(mod.get().getModInfo().getVersion().toString()))return;
        SpawnPlacementType leaves=AMEntityRegistry::createLeavesSpawnPlacement;
        var height=Heightmap.Types.MOTION_BLOCKING;
        var replace=RegisterSpawnPlacementsEvent.Operation.REPLACE;
        event.register(AMEntityRegistry.TOUCAN.get(),leaves,height,EntityToucan::canToucanSpawn,replace);
        event.register(AMEntityRegistry.CAPUCHIN_MONKEY.get(),leaves,height,EntityCapuchinMonkey::canCapuchinSpawn,replace);
        event.register(AMEntityRegistry.POTOO.get(),leaves,height,EntityPotoo::canPotooSpawn,replace);
        event.register(AMEntityRegistry.SUGAR_GLIDER.get(),leaves,height,EntitySugarGlider::canSugarGliderSpawn,replace);
        event.register(AMEntityRegistry.BLUE_JAY.get(),leaves,height,EntityBlueJay::checkBlueJaySpawnRules,replace);
        event.register(AMEntityRegistry.SPECTRE.get(),SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,EntitySpectre::canSpectreSpawn,replace);
        // Cosmic Cod is a flying End fish: retain its existing placement freedom and instance roll check.
        event.register(AMEntityRegistry.COSMIC_COD.get(),SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(type,world,reason,pos,random)->true,replace);
        EldenCombat.LOGGER.info("[integration] Registered seven missing Alex 1.22.17 spawn placements");
    }
}
