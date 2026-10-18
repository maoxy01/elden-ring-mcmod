package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.client.PresentationRoutes;
import com.dsh.eldencombat.effects.*;
import com.dsh.eldencombat.entity.skill.GoldenWaveEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Authored planes, including independently clipped wave lanes. No damage or entity mutation.
 *
 * <p>本渲染层不再用"提交数"反推"有没有接管"：先由 {@link VfxDrawPlan#decide} 选定本帧唯一一条主路径，
 * 再用真实计数由 {@link VfxDrawPlan#finish} 定稿为 NOT_HANDLED / DRAWN / SUPPRESSED。
 * 只有 NOT_HANDLED 才允许调用方回落到既有兼容主路径；CORE、缺资源轮廓、预算拒绝、生命周期结束与
 * 诊断抑制都是"接管且 0 提交"，绝不借 legacy 复活。
 *
 * <p>lane 一律按 {@link FxArtwork.Lane#id()} 的**原始 lane ID**处理：客户端遮挡快照、服务端 mask 与
 * 诊断层共用同一编号，过滤后的列表序号不会冒充 lane ID。
 */
final class ArtworkRenderer {
    private ArtworkRenderer() {}

    /** 一次接管的最终结果：显式状态/原因 + **实际提交**的分类计数。 */
    record DrawResult(VfxDrawPlan.Plan plan) {
        static DrawResult unhandled() { return new DrawResult(VfxDrawPlan.Plan.unhandled()); }
        VfxDrawPlan.Disposition disposition() { return plan.disposition(); }
        VfxDrawPlan.Reason reason() { return plan.reason(); }
        boolean handled() { return plan.disposition() != VfxDrawPlan.Disposition.NOT_HANDLED; }
        boolean submitted() { return plan.submitted(); }
        boolean legacyEligible() { return plan.legacyEligible(); }
        int spriteQuads() { return plan.spriteQuads(); }
        int outlinePanels() { return plan.outlinePanels(); }
    }

    /** 诊断记录的去重键与上下文；只在 {@code eldencombat.vfxSmoke} 下生成日志。 */
    record Submission(VfxBudget.Lod lod, String identity, int phase, int frame, String path, VfxDrawPlan.Reason reason,
                      int lanes, int spriteQuads, int outlinePanels, Vec3 anchor, Double anchorGroundY) {}

    static DrawResult draw(SkillEffectMesh.Kind kind, float progress, int phase,
                           GoldenWaveEntity wave, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           VfxBudget.Lod lod) {
        return draw(kind, progress, phase, wave, partialTick, pose, buffers, lod, null);
    }

    static DrawResult draw(SkillEffectMesh.Kind kind, float progress, int phase,
                           GoldenWaveEntity wave, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           VfxBudget.Lod lod, String identity) {
        var resolved = SkillSlashSpriteRenderer.route(kind);
        var declaration = PresentationRoutes.route(FxSlashRoute.routeId(kind));
        boolean declared = resolved != null && resolved.ready() && declaration != null && declaration.artwork() != null;
        if (!declared) {
            record(new Submission(lod, identity, phase, -1, "none", VfxDrawPlan.Reason.NO_DECLARATION, 0, 0, 0, null, null));
            return DrawResult.unhandled();
        }
        var art = declaration.artwork();
        int frame = FxArtwork.frame(progress, resolved.spec().frameCount());
        boolean timeValid = frame >= 0;   // progress 非有限、<0 或 ≥1 都是生命周期外
        boolean core = lod == VfxBudget.Lod.CORE;
        boolean forceMissing = Boolean.getBoolean("eldencombat.vfxSmoke")
                && Boolean.getBoolean("eldencombat.vfxSmokeMissing");
        boolean spriteResourceReady = resolved.loaded() && FxPalette.current().slashSprites() && !forceMissing;
        boolean diagSuppressed = !VfxDiagnosticLayers.primaryAllowed(kind, identity, phase);
        var decision = VfxDrawPlan.decide(declared, timeValid, core, spriteResourceReady, diagSuppressed);
        if (!decision.handles()) {
            VfxDiagnosticLayers.countSuppressed();
            var stopped = VfxDrawPlan.finish(decision, 0, 0, false, false);
            record(new Submission(lod, identity, phase, frame, "none", stopped.reason(), 0, 0, 0, null, null));
            return new DrawResult(stopped);
        }
        FxRenderBudget.FRAME.beginArtwork();
        TextureAtlasSprite sprite = null;
        if (decision.route() == VfxDrawPlan.Route.SPRITE) {
            var atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            sprite = atlas.getSprite(resolved.frames().get(frame));
        }
        int lanes = 0;
        Vec3 anchor = null;
        Double anchorGroundY = null;
        Panels panels;
        boolean anyCandidate;
        if (wave == null) {
            // 非波效果：作者平面挂在实体渲染姿势上，取"含作者 offset 的平面原点"（相机相对坐标）换算回世界坐标做诊断锚点。
            var origin = pose.last().pose().transformPosition(new org.joml.Vector3f(art.x(), art.y(), art.z()));
            Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            anchor = new Vec3(origin.x + camPos.x, origin.y + camPos.y, origin.z + camPos.z);
            anchorGroundY = anchor.y;
            panels = plane(pose, buffers, art, phase, art.width() * art.scale(phase), art.height() * art.scale(phase),
                    art.anchorU(), 0, 1, sprite, resolved.spec().outlineColor(), kind, progress);
            anyCandidate = true;   // 平面本体就是候选：0 提交只可能是预算拒绝
        } else {
            float radius = wave.visualRadius(partialTick), travel = wave.visualTravel(partialTick);
            Vec3 base = new Vec3(Mth.lerp(partialTick, wave.xo, wave.getX()), Mth.lerp(partialTick, wave.yo, wave.getY()), Mth.lerp(partialTick, wave.zo, wave.getZ()));
            // 火墙的纯客户端逐 lane 可见性：服务端 blockedLanes 始终是硬约束，这里再叠客户端遮挡快照（原 lane ID）。
            boolean flame = wave.isFlame();
            var laneList = FxArtwork.lanes(radius, travel, wave.blockedLanes(), flame);
            int spriteQuads = 0, outlinePanels = 0;
            boolean refused = false;
            for (var lane : laneList) {
                int laneId = lane.id();
                float lateral = lane.center();
                float forward = lane.forward();
                Vec3 center = base.add(wave.dirZ() * lateral + wave.dirX() * forward, 0, -wave.dirX() * lateral + wave.dirZ() * forward);
                // Use loaded client terrain only; rendering must not force chunks or modify blocks.
                if (!wave.level().hasChunkAt(net.minecraft.core.BlockPos.containing(center))) continue;
                if (flame && !WaveArtworkVisibility.clientVisible(wave, laneId)) continue;
                var ground = wave.level().clip(new ClipContext(center.add(0, 1.5, 0), center.add(0, -2.5, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wave));
                if (ground.getType() != HitResult.Type.BLOCK) continue;
                lanes++;
                if (anchor == null) { anchor = center; anchorGroundY = ground.getLocation().y; }
                pose.pushPose();
                pose.translate(lateral, ground.getLocation().y - base.y, forward);
                Panels piece = plane(pose, buffers, art, 0, lane.width(), art.height(), .5f,
                        lane.u0(), lane.u1(), sprite, resolved.spec().outlineColor(), kind, progress);
                spriteQuads += piece.spriteQuads();
                outlinePanels += piece.outlinePanels();
                refused |= piece.refused();
                pose.popPose();
            }
            panels = new Panels(spriteQuads, outlinePanels, refused);
            anyCandidate = lanes > 0;
        }
        var plan = VfxDrawPlan.finish(decision, panels.spriteQuads(), panels.outlinePanels(), anyCandidate, panels.refused());
        if (plan.disposition() == VfxDrawPlan.Disposition.SUPPRESSED) VfxDiagnosticLayers.countSuppressed();
        VfxDiagnosticLayers.countSprite(plan.spriteQuads());
        VfxDiagnosticLayers.countOutline(plan.outlinePanels());
        // 逐实例登记"这一帧真的提交了"：多段技能（水鸟/命定）的每一段都有自己的实例与 phase，
        // 只有按身份登记才能在夹具里回答"第 2、3 次挥击到底有没有画出来"。
        VfxDiagnosticLayers.noteSubmit(identity, plan.spriteQuads(), plan.outlinePanels());
        // 同一帧里登记美术走到第几帧：夹具据此把"样本取自淡入帧还是中段帧"写进证据。
        if (plan.submitted()) VfxDiagnosticLayers.noteArtFrame(frame, resolved.spec().frameCount());
        record(new Submission(lod, identity, phase, frame,
                plan.submitted() ? (plan.spriteQuads() > 0 ? "sprite" : "outline") : "none",
                plan.reason(), lanes, plan.spriteQuads(), plan.outlinePanels(), anchor, anchorGroundY));
        return new DrawResult(plan);
    }

    /** 裸平面提交结果：贴片 quad 与轮廓 panel 必须分别计数（轮廓不能算成 sprite）。 */
    private record Panels(int spriteQuads, int outlinePanels, boolean refused) {}

    /** 超距 LOD 的 0 提交记录：每种身份只记一条，避免每帧刷屏。 */
    static void recordHidden(String identity, int phase) {
        VfxDiagnosticLayers.countSuppressed();
        record(new Submission(VfxBudget.Lod.HIDDEN, identity, phase, -1, "none", VfxDrawPlan.Reason.HIDDEN, 0, 0, 0, null, null));
    }

    // ------------------------------------------------------------------ 资源提交记录
    private static final Set<String> SEEN = new HashSet<>();

    /**
     * 真实提交之后的帧记录，去重键含资源代数、实体实例、phase、route/kind、帧号、提交路径与原因。
     * 抑制记录同样落日志（path=none + 明确 reason），提交、采样与跳过原因不混用。
     */
    private static void record(Submission submission) {
        if (!Boolean.getBoolean("eldencombat.vfxSmoke")) return;
        String key = PresentationRoutes.revision() + "|" + submission.identity() + "|" + submission.phase() + "|"
                + submission.frame() + "|" + submission.path() + "|" + submission.reason() + "|" + submission.lanes();
        if (!SEEN.add(key)) return;
        // 诊断：把"作者平面真实落在世界的哪个点"与"当前相机的眼位/朝向"一起记下来，
        // 用来区分"美术没画出来"和"画在了视野外/地面之下"（见收口报告 §6）。
        String where = "";
        if (submission.anchor() != null && submission.anchorGroundY() != null) {
            Vec3 anchor = submission.anchor();
            var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vec3 eye = cam.getPosition();
            Vec3 look = Vec3.directionFromRotation(cam.getXRot(), cam.getYRot());
            Vec3 art = new Vec3(anchor.x, submission.anchorGroundY(), anchor.z);
            Vec3 delta = art.subtract(eye);
            double dist = delta.length();
            where = String.format(java.util.Locale.ROOT,
                    " art=(%.3f,%.3f,%.3f) cam=(%.3f,%.3f,%.3f) camYaw=%.1f camPitch=%.1f dist=%.3f forwardDot=%.3f",
                    art.x, art.y, art.z, eye.x, eye.y, eye.z, cam.getYRot(), cam.getXRot(), dist,
                    dist < 1.0E-6 ? Double.NaN : delta.dot(look) / dist);
        }
        com.dsh.eldencombat.EldenCombat.LOGGER.info(
                "VFX_SUBMIT lod={} id={} phase={} frame={} path={} reason={} lanes={} spriteQuads={} outlinePanels={} layer={}{}",
                submission.lod(), submission.identity(), submission.phase(), submission.frame(), submission.path(),
                submission.reason(), submission.lanes(), submission.spriteQuads(), submission.outlinePanels(),
                VfxDiagnosticLayers.layer(), where);
    }

    /**
     * 画一片作者平面，返回真正提交的分类计数。贴图缺失时用既有程序化碎片保持"可辨认 + 有方向"，
     * 不引入爆炸/方盒兜底；预算拒绝时返回 0 并标记 refused，由调用方判定原因，绝不放宽成"没接管"。
     */
    private static Panels plane(PoseStack pose, MultiBufferSource buffers, FxArtwork art, int phase,
                                float width, float height, float anchorU, float u0, float u1,
                                TextureAtlasSprite sprite, int color, SkillEffectMesh.Kind kind, float progress) {
        int spriteQuads = 0, outlinePanels = 0;
        boolean refused = false;
        boolean budgetZero = FxRenderBudget.budgetZero();
        pose.pushPose();
        pose.translate(art.x(), art.y(), art.z());
        if (art.horizontal()) pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.ZP.rotationDegrees(art.roll(phase)));
        float left = -anchorU * width, bottom = (art.anchorV() - 1) * height;
        if (sprite != null) {
            if (!budgetZero && FxRenderBudget.FRAME.sprites(1) > 0) {
                var out = buffers.getBuffer(SkillEffectRenderTypes.SLASH_SPRITE);
                quad(pose, out, left, bottom, left + width, bottom + height,
                        sprite.getU(u0), sprite.getV1(), sprite.getU(u1), sprite.getV0(), 0xFFFFFF, 1, true);
                spriteQuads++;
            } else {
                refused = true;
            }
        } else {
            // Missing assets stay recognizable and directional. No explosion or box fallback.
            var out = buffers.getBuffer(SkillEffectRenderTypes.MESH);
            float alpha = Math.min(1, (1 - progress) * 4);
            boolean wave = kind == SkillEffectMesh.Kind.GOLD_WAVE || kind == SkillEffectMesh.Kind.FLAME_FRONT;
            boolean fire = kind == SkillEffectMesh.Kind.BLOOD_FLAME || kind == SkillEffectMesh.Kind.FLAME_FRONT;
            int pieces = wave ? 1 : 12;
            int admitted = budgetZero ? 0 : FxRenderBudget.FRAME.panels(pieces);
            if (admitted <= 0) refused = true;
            for (int i = 0; i < admitted; i++) {
                float x, y, w, h;
                if (wave) {
                    x = left; y = 0; w = width; h = fire ? height * .65f : .15f;
                } else if (fire) {
                    x = left + i * width / pieces; y = bottom; w = width / pieces;
                    h = height * (.3f + .5f * (float) Math.sin((i + .5f) / pieces * Math.PI));
                } else {
                    boolean arc = kind == SkillEffectMesh.Kind.WATERFOWL || kind == SkillEffectMesh.Kind.DEATH_SLASH || kind == SkillEffectMesh.Kind.CURSED_SLASH;
                    double angle = (i + .5) / pieces * (arc ? Math.PI : Math.PI * 2);
                    x = left + width * .5f + (float) Math.cos(angle) * width * .4f;
                    y = bottom + height * .5f + (float) Math.sin(angle) * height * .4f;
                    w = Math.max(.125f, width * .08f); h = Math.max(.125f, height * .06f);
                }
                quad(pose, out, x, y, x + w, y + h, 0, 0, 0, 0, color, alpha, false);
                outlinePanels++;
            }
        }
        pose.popPose();
        return new Panels(spriteQuads, outlinePanels, refused);
    }

    private static void quad(PoseStack pose,VertexConsumer out,float x0,float y0,float x1,float y1,
                             float u0,float v0,float u1,float v1,int color,float alpha,boolean textured) {
        vertex(pose,out,x0,y0,u0,v0,color,alpha,textured);vertex(pose,out,x1,y0,u1,v0,color,alpha,textured);
        vertex(pose,out,x1,y1,u1,v1,color,alpha,textured);vertex(pose,out,x0,y1,u0,v1,color,alpha,textured);
    }
    private static void vertex(PoseStack pose,VertexConsumer out,float x,float y,float u,float v,int color,float alpha,boolean textured) {
        out.addVertex(pose.last().pose(),x,y,0).setColor(color>>16&255,color>>8&255,color&255,(int)(alpha*255));
        if(textured) out.setUv(u,v).setLight(LightTexture.FULL_BRIGHT);
    }
}
