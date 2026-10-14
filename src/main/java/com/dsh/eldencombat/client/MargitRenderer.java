package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.entity.MargitEntity;
import com.dsh.eldencombat.skeleton.AnimationClip;
import com.dsh.eldencombat.skeleton.ModelData;
import com.dsh.eldencombat.skeleton.SkeletonAssets;
import com.dsh.eldencombat.skeleton.SkeletonData;
import com.dsh.eldencombat.skeleton.SkeletonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;

import java.util.Map;

/**
 * 玛尔基特渲染器：静态模型 + 动作草稿（20 TPS，客户端用 partial tick 插值），
 * 黄金武器按动作事件 equip/unequip 挂到 spectral_socket 上，全亮渲染。
 */
public class MargitRenderer extends EntityRenderer<MargitEntity> {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "textures/entity/margit.png");

    public MargitRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.9f;
    }

    @Override
    public ResourceLocation getTextureLocation(MargitEntity entity) {
        return TEXTURE;
    }

    @Override public boolean shouldRender(MargitEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                          double cameraX, double cameraY, double cameraZ) {
        return entity.shouldRender(cameraX,cameraY,cameraZ) && frustum.isVisible(entity.getBoundingBox().inflate(3));
    }

    @Override
    public void render(MargitEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        SkeletonAssets assets = SkeletonData.assets();
        if (assets == null) {
            return;
        }
        ModelData model = assets.margit();
        double clipTick = (entity.level().getGameTime() - entity.animStartTick()) + partialTick;
        AnimationClip clip = assets.hasClip(entity.animClip()) ? assets.clip(entity.animClip()) : null;
        SkeletonPose pose = SkeletonPose.of(model, clip == null ? Map.of() : clip.poseAt(clipTick));

        poseStack.pushPose();
        // 模型朝向 -Z，MC 实体 yaw 0 面向 +Z
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        boolean fading=MargitEntity.ANIM_DEATH.equals(entity.animClip()) && clipTick>=40;
        int alpha=fading?(int)(255*Math.max(0,1-(clipTick-40)/10)):255;
        VertexConsumer consumer = buffer.getBuffer(fading?RenderType.entityTranslucent(TEXTURE):RenderType.entityCutoutNoCull(TEXTURE));
        MargitModelRenderer.render(poseStack, consumer, model, pose, packedLight,alpha<<24|0xFFFFFF);

        String equipped = clip == null ? null : clip.equippedAt(clipTick);
        if (equipped != null) {
            ModelData accessory = assets.accessory(equipped);
            if (accessory != null) {
                SkeletonPose accessoryPose = SkeletonPose.of(accessory, Map.of()).attachTo(pose, "spectral_socket");
                VertexConsumer gold = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
                MargitModelRenderer.render(poseStack, gold, accessory, accessoryPose, LightTexture.FULL_BRIGHT);
            }
        }
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }
}
