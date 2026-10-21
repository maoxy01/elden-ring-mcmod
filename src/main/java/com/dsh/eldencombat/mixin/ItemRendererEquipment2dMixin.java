package com.dsh.eldencombat.mixin;

import com.dsh.eldencombat.client.equipment2d.EquipmentRenderContext;
import com.dsh.eldencombat.client.equipment2d.EquipmentRouteRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The narrowest possible hook for the 2D equipment route.
 *
 * <p>Contract §10.1. The previous version injected at {@code TAIL} and cancelled there, which is too
 * late by construction: at that point {@code ClientHooks.handleCameraTransforms} has already applied
 * the old display block, the {@code translate(-0.5,-0.5,-0.5)} has already run, and the vanilla model
 * or the BEWLR has already committed its vertices. Cancelling then cannot remove that picture, so the
 * item was drawn twice.
 *
 * <p>This version injects at the <em>start</em> of the eight-argument {@code render}, which is before
 * {@code pushPose}, before the display transform and before any vertex:
 *
 * <ul>
 *   <li>{@code false} from the route means nothing at all was touched, so the ordinary model or the
 *       BEWLR draws the item exactly once — the old picture stays byte-identical.</li>
 *   <li>{@code true} means the route already owned the whole draw, including the single display
 *       transform and its own push/pop, so cancelling here skips only the duplicate.</li>
 *   <li>Ordering properties such as foil and enchantment are read from the real stack, so they are
 *       unaffected.</li>
 * </ul>
 *
 * <p>Two hooks are needed because the low-level method has no holder parameter. The ten-argument
 * {@code renderStatic} is the point where every hand render arrives with the real entity
 * ({@code ItemInHandRenderer.renderItem} for both first and third person), so it opens the scoped
 * context on the way in and closes it on the way out. GUI, ground, item frames and dropped items call
 * the eight-argument overload directly or with a {@code null} holder, which correctly reads as "no
 * holder": their active states stay zeroed instead of borrowing the local player.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererEquipment2dMixin {

    private static final String RENDER = "render(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;Z"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II"
            + "Lnet/minecraft/client/resources/model/BakedModel;)V";

    private static final String RENDER_STATIC = "renderStatic("
            + "Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemDisplayContext;Z"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "Lnet/minecraft/world/level/Level;III)V";

    /** Take over before the old display transform and before the first vertex. */
    @Inject(method = RENDER, at = @At("HEAD"), cancellable = true)
    private void eldencombat$routeEquipment2d(ItemStack stack, ItemDisplayContext context,
                                              boolean leftHanded, PoseStack pose,
                                              MultiBufferSource buffers, int light, int overlay,
                                              BakedModel model, CallbackInfo callback) {
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation registryId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (registryId == null) {
            return;
        }
        EquipmentRenderContext.Frame frame = EquipmentRenderContext.current(stack, context);
        LivingEntity holder = frame == null ? null : frame.holder();
        if ((context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                && holder instanceof net.minecraft.client.player.AbstractClientPlayer
                && com.dsh.eldencombat.client.WornHandGear.handles(stack)) {
            callback.cancel(); // The player arm layer draws this stack once, before item display transforms.
            return;
        }
        if (!EquipmentRouteRenderer.handles(registryId)) return;
        int seed = frame == null ? 0 : frame.seed();
        if (EquipmentRouteRenderer.render(stack, context, leftHanded, pose, buffers, light, overlay,
                holder, seed, registryId)) {
            callback.cancel();
        }
    }

    /** Open the real render context where the true holder is still known. */
    @Inject(method = RENDER_STATIC, at = @At("HEAD"))
    private void eldencombat$enterRenderContext(LivingEntity holder, ItemStack stack,
                                                ItemDisplayContext context, boolean leftHanded,
                                                PoseStack pose, MultiBufferSource buffers,
                                                Level level, int light, int overlay, int seed,
                                                CallbackInfo callback) {
        EquipmentRenderContext.enter(holder, stack, context, level, seed);
    }

    /** Close it again on the normal return path. */
    @Inject(method = RENDER_STATIC, at = @At("RETURN"))
    private void eldencombat$exitRenderContext(LivingEntity holder, ItemStack stack,
                                               ItemDisplayContext context, boolean leftHanded,
                                               PoseStack pose, MultiBufferSource buffers,
                                               Level level, int light, int overlay, int seed,
                                               CallbackInfo callback) {
        EquipmentRenderContext.exit();
    }
}
