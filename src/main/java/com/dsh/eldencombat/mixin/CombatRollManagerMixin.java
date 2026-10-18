package com.dsh.eldencombat.mixin;

import com.dsh.eldencombat.client.RollClient;
import com.dsh.eldencombat.compat.RollPrediction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.combat_roll.internals.RollManager", remap=false)
public abstract class CombatRollManagerMixin implements RollPrediction {
    @Shadow private int timeSinceLastRoll;
    @Shadow private int availableRolls;
    @Shadow private int currentCooldownLength;
    @Shadow private float currentCooldownProgress;
    @Inject(method="isRollAvailable",at=@At("RETURN"),cancellable=true,remap=false)
    private void eldencombat$canPredict(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !RollClient.allowed()) cir.setReturnValue(false);
    }
    @Override public void eldencombat$reject(int available, int cooldown, float progress) {
        timeSinceLastRoll = net.combat_roll.internals.RollManager.rollDuration()+1;
        availableRolls = Math.max(0, available);
        currentCooldownLength = Math.max(0, cooldown);
        currentCooldownProgress = Math.max(0, progress);
    }
}
