package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.effects.FxPalette;
import com.dsh.eldencombat.effects.FxSlashRoute;
import com.dsh.eldencombat.effects.SkillEffectMesh;
import com.dsh.eldencombat.effects.SkillSlashSprites;
import com.dsh.eldencombat.entity.skill.GoldenWaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.dsh.eldencombat.effects.VfxBudget;
import net.minecraft.client.Minecraft;

public final class GoldenWaveRenderer extends EntityRenderer<GoldenWaveEntity> {
    private static final ResourceLocation UNUSED = ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "skill_mesh");
    public GoldenWaveRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public void render(GoldenWaveEntity wave, float yaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        var lod = VfxBudget.lod(wave.position().distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
        if (lod == VfxBudget.Lod.HIDDEN) return;
        if (FxRenderBudget.pressure) lod = VfxBudget.Lod.CORE;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(wave.dirX(), wave.dirZ())));
        var kind = wave.isFlame() ? SkillEffectMesh.Kind.FLAME_FRONT : SkillEffectMesh.Kind.GOLD_WAVE;
        if (ArtworkRenderer.draw(kind, wave.visualProgress(partialTick), 0, wave, partialTick, pose, buffers)) {
            pose.popPose();
            return;
        }
        var route = SkillSlashSpriteRenderer.route(kind);
        var geometry = FxGeometryCache.get(wave, kind, wave.visualRadius(partialTick), wave.visualProgress(partialTick),
                0, wave.visualTravel(partialTick), wave.blockedLanes(), true, route == null ? null : route.spec());
        if (route != null && route.ready()) {
            drawRouted(lod, route, geometry, wave, partialTick, pose, buffers);
        } else {
            int selected = FxRenderBudget.FRAME.mainBoxes(Math.min(geometry.boxes.size(), lod == VfxBudget.Lod.CORE ? 24 : 96));
            SkillEffectMeshRenderer.draw(pose, buffers, geometry.boxes, selected, 1, false);
        }
        pose.popPose();
    }

    /**
     * The golden front's ready route draws one strip per open lane, its frame taken from the wave's own
     * progress so the crest keeps advancing after the front has formed; a blocked lane simply has no strip.
     * Core LOD, render pressure and a partially stitched atlas fall back to the lane-segmented outline, and
     * only an explicit {@code boxes} outline keeps the old box mesh. No hits or timing ride on this choice.
     */
    private static void drawRouted(VfxBudget.Lod lod, SkillSlashSpriteRenderer.RouteState route,
                                   FxGeometryCache geometry, GoldenWaveEntity wave, float partialTick,
                                   PoseStack pose, MultiBufferSource buffers) {
        if (lod != VfxBudget.Lod.CORE && route.loaded() && FxPalette.current().slashSprites()) {
            float progress = wave.visualProgress(partialTick);
            int semantic = Math.clamp((int) (progress * FxSlashRoute.SEMANTIC_FRAMES), 0, FxSlashRoute.SEMANTIC_FRAMES - 1);
            var lanes = SkillSlashSprites.buildWave(wave.visualRadius(partialTick), progress, semantic,
                    wave.visualTravel(partialTick), wave.blockedLanes());
            if (!lanes.isEmpty()) {
                int count = FxRenderBudget.FRAME.sprites(lanes.size());
                SkillSlashSpriteRenderer.draw(pose, buffers, lanes.subList(0, count), 0, route);
                return;
            }
        }
        if (route.spec().usesOutline() && !geometry.outline.isEmpty()) {
            int count = FxRenderBudget.FRAME.panels(geometry.outline.size());
            SkillOutlineRenderer.draw(pose, buffers, geometry.outline.subList(0, count), route.spec());
            return;
        }
        int selected = FxRenderBudget.FRAME.mainBoxes(Math.min(geometry.boxes.size(), lod == VfxBudget.Lod.CORE ? 24 : 96));
        SkillEffectMeshRenderer.draw(pose, buffers, geometry.boxes, selected, 1, false);
    }

    @Override public ResourceLocation getTextureLocation(GoldenWaveEntity entity) { return UNUSED; }
}
