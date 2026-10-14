package com.dsh.eldencombat.mixin;

import com.dsh.eldencombat.client.SkillPoseAnimator;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 战技动作注入点：原版 {@link PlayerModel#setupAnim} 的最后一步。
 *
 * <p>为什么选这里：原版在 {@code setupAnim} 里算完走路/挥臂/潜行姿态并同步了
 * 袖子、裤腿、帽子、外套之后，我们**在其之上**只改写战技涉及的那几个关节，
 * 因此普攻、走路、潜行、游泳全部保持原版表现——这正是"普攻动作就用原版"的实现方式。
 *
 * <p>目标方法全限定描述符：
 * {@code setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V}
 * （{@code PlayerModel<T extends LivingEntity>} 的泛型擦除结果；类里另有一个
 * {@code (Lnet/minecraft/world/entity/Entity;FFFFF)V} 的桥方法，不能选错）。
 *
 * <p>改完关节后必须重新同步附加部件，否则袖子/裤腿会留在原版姿态上"脱节"。
 */
@Mixin(PlayerModel.class)
public class PlayerModelPoseMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void eldencombat$applySkillPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        PlayerModel<?> self = (PlayerModel<?>) (Object) this;
        if (!SkillPoseAnimator.applyPose(self, entity, ageInTicks)) {
            return;
        }
        // HumanoidModel/PlayerModel 原本在这一步同步附加部件，我们改过主部件后要重做一遍。
        self.hat.copyFrom(self.head);
        self.leftSleeve.copyFrom(self.leftArm);
        self.rightSleeve.copyFrom(self.rightArm);
        self.leftPants.copyFrom(self.leftLeg);
        self.rightPants.copyFrom(self.rightLeg);
        self.jacket.copyFrom(self.body);
    }
}
