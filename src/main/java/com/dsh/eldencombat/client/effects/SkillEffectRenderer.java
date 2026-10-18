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
        String identity = "effect/" + effect.getId() + "/" + effect.phase();
        // 取证诊断层：非目标实例在 PRIMARY_ONLY/DEBRIS_ONLY 下整段不画，CLEAN 只压目标自身。
        if (!VfxDiagnosticLayers.allowEntity(effect.kind(), identity, effect.phase())) {
            VfxDiagnosticLayers.noteSkipped(identity);
            return;
        }
        if (lod == VfxBudget.Lod.HIDDEN) { ArtworkRenderer.recordHidden(identity, effect.phase()); return; }   // 超出 128 格：不查地形、不提交顶点
        if (FxRenderBudget.pressure || FxRenderBudget.forcedCore()) lod = VfxBudget.Lod.CORE;
        float progress = effect.progress(partialTick);
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
        // 主路径只选一次：作者美术接管后不再叠旧程序化主盒体；只有"没接管"才走既有 legacy 轮廓。
        var artwork = ArtworkRenderer.draw(effect.kind(), progress, effect.phase(), null, partialTick, pose, buffers,
                lod, identity);
        if (artwork.legacyEligible()) { VfxDiagnosticLayers.countLegacy(); drawLegacy(effect, lod, progress, yaw, pose, buffers); }
        drawSecondaryDebris(effect, progress, lod, pose, buffers, identity);
        pose.popPose();
    }

    /**
     * 未接管时的既有主视觉：ready 路由按 LOD 画贴片或轮廓，否则沿用旧盒体。
     * 这条路径只在 {@code ArtworkRenderer} 明确"没接管"时进入，绝不与作者美术叠画。
     */
    private static void drawLegacy(SkillEffectEntity effect, VfxBudget.Lod lod, float progress, float yaw,
                                   PoseStack pose, MultiBufferSource buffers) {
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
    }

    /**
     * 必要碎块：只有 GOLD_IMPACT/GRAVITY_IMPACT/GRAVITY_PULL 定义了碎块几何（见
     * {@link SkillEffectMesh#debris}），每效果 FULL≤12 / REDUCED≤6，且全帧共享 32 次块渲染准入；
     * CORE/HIDDEN 为 0，不因主贴片成功而被旁路或额外叠加。
     *
     * <p>另外两处硬约束：① 生命周期外的 progress（非有限、&lt;0、≥1）主/副效果都是 0 提交，不能因为
     * {@code SkillEffectMesh.debris} 把 progress 夹到 0 就画出早期碎块；② 取证诊断层在
     * PRIMARY_ONLY/CLEAN 下禁止碎块，碎块不得替主贴片"代签"。
     */
    private static void drawSecondaryDebris(SkillEffectEntity effect, float progress, VfxBudget.Lod lod,
                                            PoseStack pose, MultiBufferSource buffers, String identity) {
        if (lod == VfxBudget.Lod.CORE || lod == VfxBudget.Lod.HIDDEN) return;
        if (!(progress >= 0f) || progress >= 1f) return;   // NaN 也在此拦下
        if (!VfxDiagnosticLayers.debrisAllowed(effect.kind(), identity, effect.phase())) return;
        var route = SkillSlashSpriteRenderer.route(effect.kind());
        var geometry = FxGeometryCache.get(effect, effect.kind(), effect.radius(), progress, effect.phase(),
                progress * 16, 0, false, route == null ? null : route.spec());
        if (geometry.debris.isEmpty()) return;
        if (FxRenderBudget.budgetZero()) return;
        int piece = 0;
        int pieces = FxRenderBudget.debris(Math.min(geometry.debris.size(), lod == VfxBudget.Lod.FULL ? 12 : 6));
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
        VfxDiagnosticLayers.countDebris(pieces);
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
