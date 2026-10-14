package com.dsh.eldencombat.mixin;

import com.dsh.eldencombat.client.grace.MinimapSize;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional client target: absent JourneyMap never becomes a mandatory dependency. */
@Pseudo
@Mixin(targets = "journeymap.client.properties.MiniMapProperties", remap = false)
public abstract class JourneyMapSizeMixin {
    @Inject(method = "getSize()I", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void eldencombat$squareArea(CallbackInfoReturnable<Integer> callback) {
        var window = Minecraft.getInstance().getWindow();
        callback.setReturnValue(MinimapSize.squareSide(callback.getReturnValue(),
                window.getScreenWidth(), window.getScreenHeight()));
    }
}
