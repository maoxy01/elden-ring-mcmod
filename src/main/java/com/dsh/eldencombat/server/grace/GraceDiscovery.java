package com.dsh.eldencombat.server.grace;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.server.level.ServerPlayer;

/** Enrol ordinary native waystones; Waystones remains the authority for discovery and ownership. */
public final class GraceDiscovery {
    private GraceDiscovery() {}

    public static void admit(ServerPlayer player, Waystone node) {
        if (node.isValid() && WaystoneTypes.WAYSTONE.equals(node.getWaystoneType())
                && WaystonesAPI.isWaystoneActivated(player, node)
                && WaystonesAPI.getWaystone(player.server, node.getWaystoneUid()).isPresent()) {
            GraceRegistry.get(player.server).register(node.getWaystoneUid());
        }
    }
}
