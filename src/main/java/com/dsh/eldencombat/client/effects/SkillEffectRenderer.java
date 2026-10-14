package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.effects.FxPalette;
import com.dsh.eldencombat.effects.SkillEffectMesh;
import com.dsh.eldencombat.effects.SkillSlashSprites;
import com.dsh.eldencombat.entity.skill.SkillEffectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.dsh.eldencombat.effects.VfxBudget;

public final class SkillEffectRenderer extends EntityRenderer<SkillEffectEntity> {
    private static final ResourceLocation UNUSED = ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "skill_mesh");
    public SkillEffectRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public void render(SkillEffectEntity effect, float yaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        var lod = VfxBudget.lod(effect.position().distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
        if (lod == VfxBudget.Lod.HIDDEN) return;
        if (FxRenderBudget.pressure) lod = VfxBudget.Lod.CORE;
        pose.pushPose();
        var owner = effect.level().getEntity(effect.ownerEntityId());
        if (owner != null) {
            // Follow the caster's interpolated pose, not a separately interpolated tracking entity.
            pose.translate(Mth.lerp(partialTick, owner.xo, owner.getX()) - Mth.lerp(partialTick, effect.xo, effect.getX()),
                    Mth.lerp(partialTick, owner.yo, owner.getY()) - Mth.lerp(partialTick, effect.yo, effect.getY()),
                    Mth.lerp(partialTick, owner.zo, owner.getZ()) - Mth.lerp(partialTick, effect.zo, effect.getZ()));
            yaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
        }
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        float progress = effect.progress(partialTick);
        if (ArtworkRenderer.draw(effect.kind(), progress, effect.phase(), null, partialTick, pose, buffers)) {
            pose.popPose();
            return;
        }
        var route = SkillSlashSpriteRenderer.route(effect.kind());
        var geometry = FxGeometryCache.get(effect, effect.kind(), effect.radius(), progress, effect.phase(),
                progress * 16, 0, false, route == null ? null : route.spec());
        if (route != null && route.ready()) {
            drawRouted(effect.kind(), lod, route, geometry, yaw, pose, buffers);
        } else {
            boolean sprites = lod != VfxBudget.Lod.CORE && SkillSlashSprites.supports(effect.kind())
                    && SkillSlashSpriteRenderer.available();
            int selected = FxRenderBudget.FRAME.mainBoxes(Math.min(geometry.boxes.size(), lod == VfxBudget.Lod.CORE ? 24 : 96));
            // A faint native silhouette survives atlas/shader loss; sprites remain the main close-range artwork.
            SkillEffectMeshRenderer.draw(pose, buffers, geometry.boxes, selected, sprites ? .18f : 1,
                    effect.kind() == SkillEffectMesh.Kind.BLOOD_RITUAL);
            if (sprites) {
                int count = FxRenderBudget.FRAME.sprites(geometry.strips.size());
                SkillSlashSpriteRenderer.draw(pose, buffers,
                        geometry.strips.subList(0, count), yaw, null);
            }
        }
        int piece = 0;
        int pieces = FxRenderBudget.debris(lod == VfxBudget.Lod.CORE ? 0 : Math.min(geometry.debris.size(), lod == VfxBudget.Lod.FULL ? 12 : 6));
        for (var debris : geometry.debris.subList(0, pieces)) {
            pose.pushPose();
            pose.translate((debris.x0() + debris.x1()) / 2, (debris.y0() + debris.y1()) / 2, (debris.z0() + debris.z1()) / 2);
            pose.mulPose(Axis.YP.rotationDegrees(piece * 47 + progress * 95));
            float tilt = (float) Math.sin(progress * Math.PI) * (effect.kind() == SkillEffectMesh.Kind.GRAVITY_PULL ? 18 : 55);
            pose.mulPose(Axis.XP.rotationDegrees((piece++ % 2 == 0 ? 1 : -1) * tilt));
            pose.scale(debris.x1() - debris.x0(), debris.y1() - debris.y0(), debris.z1() - debris.z0());
            pose.translate(-.5, -.5, -.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(effect.groundState(), pose, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * A ready route owns the silhouette, so the coarse box mesh is dropped instead of stacked under the
     * artwork: frames while they are loaded, one programmatic outline at core LOD, under render pressure
     * or while the atlas is missing frames, and the old box mesh only for an explicit {@code boxes}
     * outline. No hits, no particles and no timing changes ride on this choice.
     */
    private static void drawRouted(SkillEffectMesh.Kind kind, VfxBudget.Lod lod,
                                   SkillSlashSpriteRenderer.RouteState route, FxGeometryCache geometry, float yaw,
                                   PoseStack pose, MultiBufferSource buffers) {
        if (lod != VfxBudget.Lod.CORE && route.loaded() && FxPalette.current().slashSprites()
                && !geometry.strips.isEmpty()) {
            int count = FxRenderBudget.FRAME.sprites(geometry.strips.size());
            SkillSlashSpriteRenderer.draw(pose, buffers, geometry.strips.subList(0, count), yaw, route);
            return;
        }
        if (route.spec().usesOutline() && !geometry.outline.isEmpty()) {
            int count = FxRenderBudget.FRAME.panels(geometry.outline.size());
            SkillOutlineRenderer.draw(pose, buffers, geometry.outline.subList(0, count), route.spec());
            return;
        }
        int selected = FxRenderBudget.FRAME.mainBoxes(Math.min(geometry.boxes.size(), lod == VfxBudget.Lod.CORE ? 24 : 96));
        SkillEffectMeshRenderer.draw(pose, buffers, geometry.boxes, selected, 1,
                kind == SkillEffectMesh.Kind.BLOOD_RITUAL);
    }

    @Override public ResourceLocation getTextureLocation(SkillEffectEntity entity) { return UNUSED; }
}
