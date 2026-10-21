package com.dsh.eldencombat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;

/** Uses the final vanilla/skill arm pose, so gear follows walking and attacks. */
public final class WornHandLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public WornHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float age,
                       float headYaw, float headPitch) {
        for (HumanoidArm arm : HumanoidArm.values()) {
            var part = arm == HumanoidArm.RIGHT ? getParentModel().rightArm : getParentModel().leftArm;
            WornHandGear.render(player, WornHandGear.stackForArm(player, arm), arm, part, pose, buffers, light);
        }
    }
}
