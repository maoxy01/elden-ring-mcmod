package com.dsh.eldencombat.client;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.WeaponDisplayTransforms;
import com.dsh.eldencombat.registry.ModItems;
import com.dsh.eldencombat.skeleton.HitVolumes;
import com.dsh.eldencombat.skeleton.ModelData;
import com.dsh.eldencombat.skeleton.PlayerAssets;
import com.dsh.eldencombat.skeleton.SkeletonPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * GUI / 地面掉落 / 物品展示框的装备绘制入口。
 *
 * <p>33 件精细武器走 {@link WeaponAssets} 与 512 图集；药瓶等旧装备继续走
 * {@link PlayerAssets} 与 {@code equipment.png}。两套资产图集尺寸不同，UV 基准不能混用。
 */
public final class EquipmentRenderer extends BlockEntityWithoutLevelRenderer {

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "textures/item/equipment.png");

    public EquipmentRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    /** 旧装备（非精细武器）的资产名；既无精细模型也无旧资产时返回 null。 */
    public static String asset(ItemStack stack) {
        if (stack.is(ModItems.TEST_FLASK.get())) {
            return "healing_flask";
        }
        return null;
    }

    public static ModelData modelFor(ItemStack stack) {
        ModelData weapon = WeaponAssets.modelFor(stack);
        return weapon != null ? weapon : legacyModel(stack);
    }

    public static ResourceLocation textureFor(ItemStack stack) {
        WeaponAssets.Entry entry = WeaponAssets.entryFor(stack);
        return entry == null ? TEXTURE : entry.texture();
    }

    public static void render(ItemStack stack, Matrix4f socket, PoseStack pose, MultiBufferSource buffers, int light) {
        WeaponAssets.Entry entry = WeaponAssets.entryFor(stack);
        ModelData model = entry != null ? entry.model() : legacyModel(stack);
        if (model == null) {
            return;
        }
        var output = ItemRenderer.getFoilBufferDirect(buffers,
                RenderType.entityCutoutNoCull(textureFor(stack)), true, stack.hasFoil());
        if (entry != null && entry.mesh() != null) {
            pose.pushPose();
            pose.mulPose(socket);
            entry.mesh().render(output, pose.last(), light, -1);
            pose.popPose();
            return;
        }
        SkeletonPose attached = SkeletonPose.rest(model).attachTo(socket);
        MargitModelRenderer.render(pose, output, model, attached, light);
    }

    private static ModelData legacyModel(ItemStack stack) {
        String legacy = asset(stack);
        return legacy == null ? null : PlayerAssets.get().equipment(legacy);
    }

    public static org.joml.Vector3f palmAnchor(ItemStack stack, ItemDisplayContext context, boolean left) {
        var mc=Minecraft.getInstance();
        var actual=new PoseStack(); var reference=new PoseStack();
        mc.getItemRenderer().getModel(stack,mc.level,null,0).getTransforms().getTransform(context).apply(left,actual);
        actual.translate(-.5,-.5,-.5);
        mc.getItemRenderer().getModel(new ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD),mc.level,null,0)
                .getTransforms().getTransform(context).apply(left,reference);
        reference.translate(-.5,-.5,-.5);
        return WeaponDisplayTransforms.palmAnchor(actual.last().pose(),reference.last().pose());
    }

    /** 旧签名：只用于旧装备资产名（药瓶），保留给尚未清理的渲染代码。 */
    public static void render(String id, Matrix4f socket, PoseStack pose, MultiBufferSource buffers, int light, boolean foil) {
        ModelData model = PlayerAssets.get().equipment(id);
        if (model == null) {
            return;
        }
        SkeletonPose attached = SkeletonPose.rest(model).attachTo(socket);
        var output = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), true, foil);
        MargitModelRenderer.render(pose, output, model, attached, light);
    }

    @Override
    public void renderByItem(ItemStack item, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        ModelData model = modelFor(item);
        if (model == null) {
            return;
        }
        WeaponAssets.Entry entry = WeaponAssets.entryFor(item);
        var bounds = entry != null ? entry.bounds() : HitVolumes.modelAabb(SkeletonPose.rest(model));
        var baked = Minecraft.getInstance().getItemRenderer().getModel(item, Minecraft.getInstance().level, null, 0);
        float upstreamZ = Math.abs(baked.getTransforms().getTransform(context).scale.z);
        float thickness = com.dsh.eldencombat.client.equipment2d.ItemThickness.target(pose, context)
                / Math.max(1e-6f, upstreamZ);
        var depth = new com.dsh.eldencombat.client.equipment2d.ItemThickness.Depth(
                (float) bounds.minZ(), (float) bounds.maxZ());
        if (entry != null) {
            var view = switch (context) {
                case GUI -> WeaponDisplayTransforms.View.GUI;
                case FIXED -> WeaponDisplayTransforms.View.FIXED;
                case GROUND -> WeaponDisplayTransforms.View.GROUND;
                case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> WeaponDisplayTransforms.View.FIRST_PERSON;
                case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> WeaponDisplayTransforms.View.THIRD_PERSON;
                case HEAD -> WeaponDisplayTransforms.View.HEAD;
                default -> WeaponDisplayTransforms.View.NONE;
            };
            boolean held = view == WeaponDisplayTransforms.View.FIRST_PERSON || view == WeaponDisplayTransforms.View.THIRD_PERSON;
            boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            pose.pushPose();
            if (view == WeaponDisplayTransforms.View.FIRST_PERSON
                    && !(item.getItem() instanceof net.minecraft.world.item.ShieldItem)
                    && !BuiltInRegistries.ITEM.getKey(item.getItem()).getPath().equals("test_flask")) {
                // The missing-provider fallback needs the same first-person presentation policy.
                // Undo only this item's display (not the caller's equip/swing/bob animation), then
                // fit around the real grip. World size and every third-person pose stay unchanged.
                var display = new PoseStack();
                baked.getTransforms().getTransform(context).apply(left, display);
                display.translate(-.5f, -.5f, -.5f);
                pose.mulPose(new Matrix4f(display.last().pose()).invert());
                boolean bow = item.getItem() instanceof net.minecraft.world.item.BowItem;
                pose.translate(left ? .12f : -.12f, bow ? .48f : .24f, -.45f);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(left ? 25f : -25f));
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(left ? 35f : 55f));
                WeaponCorrections.get(BuiltInRegistries.ITEM.getKey(item.getItem()).getPath()).apply(pose);
                var profile = entry.proportion();
                float length = profile == null ? 1.10f
                        : Math.min(1.10f, profile.ratioToPlayer() * profile.referencePlayerHeight());
                float scale = length / Math.max(.001f, (float)(bounds.maxY() - bounds.minY()));
                pose.mulPose(WeaponDisplayTransforms.matrix(bounds, view, left, scale,
                        profile == null ? 0 : profile.gripX(), profile == null ? 0 : profile.gripY(),
                        profile == null ? 0 : profile.gripZ(), 0, 0, 0));
            } else if (held) {
                // 手持：**原版已经替我们摆好了**——物品模型的 display 块（weapon_placeholder.json =
                // 原版 item/handheld，也就是钻石剑的握法）由 ItemRenderer 在调用 BEWLR 之前施加，
                // 并且随后还做了 translate(-0.5,-0.5,-0.5)，所以这里拿到的坐标空间就是"图标格 [0,1]³"。
                // 因此不要再 translate(.5,.5,.5) 把自己挪到格子中心：
                // WeaponDisplayTransforms 负责把握把放到图标格中心、刀身转成原版贴图那个 45°、
                // 再按长度缩放到图标尺度。
                // Apply authored rotation/scale around the palm anchor, not the icon corner.
                var anchor=palmAnchor(item,context,left);
                pose.translate(anchor.x,anchor.y,anchor.z);
                WeaponCorrections.get(BuiltInRegistries.ITEM.getKey(item.getItem()).getPath()).apply(pose);
                pose.translate(-anchor.x,-anchor.y,-anchor.z);
                float scale = 1f;
                if (entry.proportion() != null) {
                    float upstream = baked.getTransforms().getTransform(context).scale.y;
                    scale = entry.proportion().heldScale((float)(bounds.maxY()-bounds.minY()),
                            view == WeaponDisplayTransforms.View.FIRST_PERSON, upstream);
                }
                var profile=entry.proportion();
                pose.mulPose(WeaponDisplayTransforms.matrix(bounds, view, left, scale,
                        profile==null?0:profile.gripX(),profile==null?0:profile.gripY(),profile==null?0:profile.gripZ(),anchor.x,anchor.y,anchor.z));
            } else {
                pose.translate(.5, .5, .5);
                pose.mulPose(WeaponDisplayTransforms.matrix(bounds, view, left));
            }
            com.dsh.eldencombat.client.equipment2d.ItemThickness.normalize(pose, depth, thickness);
            render(item, new Matrix4f(), pose, buffers, light);
            com.dsh.eldencombat.client.equipment2d.EquipmentRouteRenderer.traceLegacy(item, context);
            pose.popPose();
            return;
        }
        // 精细武器按 1/16 格建模，长兵器会超出 16 像素格：按最大边长归一到 1 格。
        float extent = (float) Math.max(bounds.maxX() - bounds.minX(),
                Math.max(bounds.maxY() - bounds.minY(), bounds.maxZ() - bounds.minZ()));
        float scale = extent > 1.0F ? 1.0F / extent : 1.0F;
        double centerX = (bounds.minX() + bounds.maxX()) / 2;
        double centerY = (bounds.minY() + bounds.maxY()) / 2;
        double centerZ = (bounds.minZ() + bounds.maxZ()) / 2;

        pose.pushPose();
        // 原版流程：ItemRenderer 已经按 display 变换摆好原点，并对物品格做了 translate(-0.5)。
        // 因此"模型空间 [0,1]^3"就是物品格，这里把网格缩放居中到 (0.5,0.5,0.5) 即可。
        pose.translate(0.5, 0.5, 0.5);
        pose.scale(scale, scale, scale);
        pose.translate(-centerX, -centerY, -centerZ);
        com.dsh.eldencombat.client.equipment2d.ItemThickness.normalize(pose, depth, thickness);
        render(item, new Matrix4f(), pose, buffers, light);
        com.dsh.eldencombat.client.equipment2d.EquipmentRouteRenderer.traceLegacy(item, context);
        pose.popPose();
    }
}
