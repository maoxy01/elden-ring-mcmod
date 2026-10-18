package com.dsh.eldencombat.compat;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/** Data-only repair for the pinned release; upstream functions and trades stay upstream. */
@EventBusSubscriber(modid=EldenCombat.MODID, bus=EventBusSubscriber.Bus.MOD)
public final class DntDataCompat {
    private DntDataCompat() {}

    @SubscribeEvent public static void packs(AddPackFindersEvent event) {
        if (event.getPackType()!=PackType.SERVER_DATA) return;
        var mod=ModList.get().getModContainerById("mr_dungeons_andtaverns");
        if (mod.isEmpty() || !"1-v4.4.4".equals(mod.get().getModInfo().getVersion().toString())) return;
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID,"datapacks/dnt_4_4_4"),
                PackType.SERVER_DATA, Component.literal("Dungeons and Taverns 4.4.4 compatibility"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }
}
