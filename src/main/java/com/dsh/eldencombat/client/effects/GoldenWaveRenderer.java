package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.effects.FxArtwork;
import com.dsh.eldencombat.effects.FxPalette;
import com.dsh.eldencombat.effects.FxSlashRoute;
import com.dsh.eldencombat.effects.SkillEffectMesh;
import com.dsh.eldencombat.effects.SkillSlashSprites;
import com.dsh.eldencombat.effects.VfxBudget;
import com.dsh.eldencombat.effects.VfxDrawPlan;
import com.dsh.eldencombat.entity.skill.GoldenWaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)
public final class GoldenWaveRenderer extends EntityRenderer<GoldenWaveEntity> {
    private static final ResourceLocation UNUSED = ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "skill_mesh");
    public GoldenWaveRenderer(EntityRendererProvider.Context context) { super(context); }

    /** 客户端 tick：每波的 lane 支撑/遮挡至多求解一次，渲染帧只消费上一个完整 tick 的快照。 */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        WaveArtworkVisibility.beginClientTick();
        for (var entity : mc.level.entitiesForRendering())
            if (entity instanceof GoldenWaveEntity wave && wave.isFlame()) WaveArtworkVisibility.solve(wave);
        WaveArtworkVisibility.endClientTick();
    }

    @Override public void render(GoldenWaveEntity wave, float yaw, float partialTick,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        var lod = VfxBudget.lod(wave.position().distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
        var kind = wave.isFlame() ? SkillEffectMesh.Kind.FLAME_FRONT : SkillEffectMesh.Kind.GOLD_WAVE;
        String identity = "wave/" + wave.getId();
        // 取证诊断层：非目标实例在 PRIMARY_ONLY/DEBRIS_ONLY 下整段不画，CLEAN 只压目标自身。
        if (!VfxDiagnosticLayers.allowEntity(kind, identity, 0)) { VfxDiagnosticLayers.noteSkipped(identity); return; }
        if (lod == VfxBudget.Lod.HIDDEN) { ArtworkRenderer.recordHidden(identity, 0); return; }   // 超出 128 格：不查地形、不提交顶点
        if (FxRenderBudget.pressure || FxRenderBudget.forcedCore()) lod = VfxBudget.Lod.CORE;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(wave.dirX(), wave.dirZ())));
        var artwork = ArtworkRenderer.draw(kind, wave.visualProgress(partialTick), 0, wave, partialTick, pose, buffers,
                lod, identity);
        // 只有"没接管"才允许走既有兼容主路径：CORE/缺资源轮廓/预算拒绝/生命周期外/诊断抑制一律不再回旧路径，
        // 且旧路径本身也消费同一份 lane 可见约束（见 laneMask）。
        if (artwork.legacyEligible()) { VfxDiagnosticLayers.countLegacy(); drawLegacy(wave, kind, lod, partialTick, pose, buffers); }
        pose.popPose();
    }

    /**
     * 兼容主路径唯一的 lane 可见约束：服务端权威 mask ∪ 客户端遮挡快照，按**原始 lane ID** 位编号。
     * 主贴片、轮廓与兼容 wave 几何都消费它，避免各分支各查一次得到矛盾结果。
     */
    static int laneMask(GoldenWaveEntity wave) {
        int client = wave.isFlame() ? WaveArtworkVisibility.obscuredMask(wave) : 0;
        return VfxDrawPlan.combineMask(wave.blockedLanes(), client);
    }

    /**
     * 未接管时的既有主路径：ready 路由按 LOD 画一张/多条 lane 贴片或轮廓，否则沿用旧盒体。
     * 与作者美术共用同一份 lane 几何与同一份可见约束，不重画第二套 UV。
     */
    private static void drawLegacy(GoldenWaveEntity wave, SkillEffectMesh.Kind kind, VfxBudget.Lod lod,
                                   float partialTick, PoseStack pose, MultiBufferSource buffers) {
        int mask = laneMask(wave);
        var route = SkillSlashSpriteRenderer.route(kind);
        var geometry = FxGeometryCache.get(wave, kind, wave.visualRadius(partialTick), wave.visualProgress(partialTick),
                0, wave.visualTravel(partialTick), mask, true, route == null ? null : route.spec());
        if (route != null && route.ready()) {
            drawRouted(lod, route, geometry, wave, partialTick, pose, buffers, mask);
        } else {
            int selected = FxRenderBudget.FRAME.mainBoxes(Math.min(geometry.boxes.size(), lod == VfxBudget.Lod.CORE ? 24 : 96));
            SkillEffectMeshRenderer.draw(pose, buffers, geometry.boxes, selected, 1, false);
        }
    }

    /**
     * The golden front's ready route draws one strip per open lane, its frame taken from the wave's own
     * progress so the crest keeps advancing after the front has formed; a blocked lane simply has no strip.
     * The lane mask is the shared "server blocked ∪ client hidden" mask, so the compatibility path cannot
     * re-draw a lane the authored path deliberately hid. Core LOD, render pressure and a partially stitched
     * atlas fall back to the lane-segmented outline, and only an explicit {@code boxes} outline keeps the old
     * box mesh. No hits or timing ride on this choice.
     */
    private static void drawRouted(VfxBudget.Lod lod, SkillSlashSpriteRenderer.RouteState route,
                                   FxGeometryCache geometry, GoldenWaveEntity wave, float partialTick,
                                   PoseStack pose, MultiBufferSource buffers, int laneMask) {
        if (lod != VfxBudget.Lod.CORE && route.loaded() && FxPalette.current().slashSprites()) {
            float progress = wave.visualProgress(partialTick);
            int semantic = Math.clamp((int) (progress * FxSlashRoute.SEMANTIC_FRAMES), 0, FxSlashRoute.SEMANTIC_FRAMES - 1);
            var lanes = SkillSlashSprites.buildWave(wave.visualRadius(partialTick), progress, semantic,
                    wave.visualTravel(partialTick), laneMask);
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

    /**
     * 保守客户端可视包围：只做纯角点推导（见 {@link WaveVisualBounds}），不重复发射射线、
     * 不强制加载区块；实体真实 boundingBox 与服务端逻辑都不变。
     */
    static AABB visualBounds(GoldenWaveEntity wave, FxArtwork art) {
        return WaveVisualBounds.of(wave.xo, wave.yo, wave.zo,
                wave.getX(), wave.getY(), wave.getZ(),
                wave.dirX(), wave.dirZ(),
                Math.max(wave.visualRadius(1.0f), 0.0), wave.visualTravel(1.0f),
                art, wave.getBoundingBoxForCulling());
    }

    /**
     * 不先调用 {@code super.shouldRender} 再指望扩大框补回来：实体自身的距离规则（{@code shouldRender} 内的
     * {@code shouldRenderAtSqrDistance}）保持不动，只用客户端保守可视框补视锥判定。
     */
    @Override public boolean shouldRender(GoldenWaveEntity wave, Frustum frustum, double camX, double camY, double camZ) {
        if (!wave.shouldRender(camX, camY, camZ)) return false;
        var kind = wave.isFlame() ? SkillEffectMesh.Kind.FLAME_FRONT : SkillEffectMesh.Kind.GOLD_WAVE;
        var declaration = com.dsh.eldencombat.client.PresentationRoutes.route(FxSlashRoute.routeId(kind));
        return frustum.isVisible(visualBounds(wave, declaration == null ? null : declaration.artwork()));
    }

    @Override public ResourceLocation getTextureLocation(GoldenWaveEntity entity) { return UNUSED; }
}
