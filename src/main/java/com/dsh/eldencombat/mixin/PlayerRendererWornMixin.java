package com.dsh.eldencombat.mixin;

import com.dsh.eldencombat.client.WornHandGear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerRendererWornMixin {
    @Inject(method = "renderHand", at = @At("TAIL"))
    private void eldencombat$attachToFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light,
            AbstractClientPlayer player, ModelPart arm, ModelPart sleeve, CallbackInfo callback) {
        var frame = WornHandGear.firstPerson();
        if (frame == null) return;
        WornHandGear.render(player, frame.stack(), frame.arm(), arm, pose, buffers, light);
    }
}
