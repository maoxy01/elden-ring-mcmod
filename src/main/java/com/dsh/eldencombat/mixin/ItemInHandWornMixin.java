package com.dsh.eldencombat.mixin;

import com.dsh.eldencombat.client.WornHandGear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandWornMixin {
    @Shadow private void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light,
                                         float equipProgress, float swingProgress, HumanoidArm arm) { throw new AssertionError(); }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void eldencombat$renderWornArm(AbstractClientPlayer player, float partialTick, float pitch,
            InteractionHand hand, float swing, ItemStack stack, float equip,
            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (player.isScoping() || !WornHandGear.handles(stack)) return;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        var previous = WornHandGear.enterFirstPerson(stack, arm);
        pose.pushPose();
        try {
            if (!player.isInvisible()) renderPlayerArm(pose, buffers, light, equip, swing, arm);
        } finally {
            pose.popPose();
            WornHandGear.restoreFirstPerson(previous);
        }
        callback.cancel();
    }
}
