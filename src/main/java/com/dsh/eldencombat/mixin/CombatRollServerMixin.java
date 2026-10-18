package com.dsh.eldencombat.mixin;

import com.dsh.eldencombat.compat.CombatRollCompat;
import net.combat_roll.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.combat_roll.network.ServerNetwork", remap=false)
public abstract class CombatRollServerMixin {
    @Inject(method="handleRollPublish", at=@At("HEAD"), cancellable=true, remap=false)
    private static void eldencombat$admission(Packets.RollPublish packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (!server.isSameThread()) {
            server.execute(() -> ServerNetwork.handleRollPublish(packet, server, player));
            ci.cancel();
        } else if (!CombatRollCompat.admit(player, packet.velocity())) ci.cancel();
    }
}
