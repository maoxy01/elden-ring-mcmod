package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.client.PresentationRoutes;
import com.dsh.eldencombat.effects.FxPalette;
import com.dsh.eldencombat.effects.FxSlashRoute;
import com.dsh.eldencombat.effects.SkillEffectMesh;
import com.dsh.eldencombat.effects.SkillSlashSprites;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws strip artwork from the particle atlas.
 *
 * <p>A routed effect uses its own frames and its own plane: the quad is authored in the skill's space, so
 * the artwork keeps its direction when the camera turns. A kind without a ready route keeps the shared
 * explosion frames as a world-space billboard, exactly as before.
 */
final class SkillSlashSpriteRenderer {
    private static boolean atlasReported;
    private static final ResourceLocation[] LEGACY_FRAMES = new ResourceLocation[16];
    private static final Set<String> REPORTED = new HashSet<>();
    private static long reportedRevision = Long.MIN_VALUE;
    static {
        for (int i = 0; i < LEGACY_FRAMES.length; i++) LEGACY_FRAMES[i] = ResourceLocation.withDefaultNamespace("explosion_" + i);
    }
    private SkillSlashSpriteRenderer() {}

    /** Resolved state of one effect family: its route rules and the frames really present in the atlas. */
    record RouteState(String id, FxSlashRoute spec, List<ResourceLocation> frames) {
        boolean ready() { return spec != null && spec.ready(); }
        /** Ready artwork counts as loaded only when every declared frame landed in the stitched atlas. */
        boolean loaded() { return ready() && spec.frameCount() == frames.size(); }
    }

    /** Null keeps the kind on the shared legacy artwork. */
    static RouteState route(SkillEffectMesh.Kind kind) {
        String id = FxSlashRoute.routeId(kind);
        if (id == null) return null;
        var declared = PresentationRoutes.route(id);
        if (declared == null) return null;
        if (!declared.spec().ready()) return new RouteState(id, declared.spec(), List.of());
        return new RouteState(id, declared.spec(), loadedFrames(id, declared.frames()));
    }

    /** Frames present in the atlas, and one line per revision that distinguishes loaded art from missing art. */
    private static List<ResourceLocation> loadedFrames(String id, List<ResourceLocation> declared) {
        if (PresentationRoutes.revision() != reportedRevision) { REPORTED.clear(); reportedRevision = PresentationRoutes.revision(); }
        var texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (!(texture instanceof TextureAtlas atlas)) return List.of();
        var present = new ArrayList<ResourceLocation>(declared.size());
        for (var frame : declared) if (atlas.getSprite(frame).contents().name().equals(frame)) present.add(frame);
        if (REPORTED.add(id)) {
            if (present.size() == declared.size())
                EldenCombat.LOGGER.info("[skill-fx] route {} loaded {}/{} frames from the particle atlas", id, present.size(), declared.size());
            else
                EldenCombat.LOGGER.warn("[skill-fx] route {} is missing {} of {} frames in the particle atlas; "
                        + "the outline fallback is drawn instead of shared explosion frames",
                        id, declared.size() - present.size(), declared.size());
        }
        return List.copyOf(present);
    }

    /** True when the palette enables 2D slash artwork and all sixteen legacy frames are stitched. */
    static boolean available() {
        if (!FxPalette.current().slashSprites()) return false;
        var texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (!(texture instanceof TextureAtlas atlas)) return false;
        for (int i = 0; i < LEGACY_FRAMES.length; i++) {
            var frame = PresentationRoutes.slashFrame(i, LEGACY_FRAMES[i]);
            if (!atlas.getSprite(frame).contents().name().equals(frame)) return false;
        }
        return true;
    }

    static void draw(PoseStack pose, MultiBufferSource buffers, List<SkillSlashSprites.Strip> strips, float yaw,
                     RouteState route) {
        if (strips.isEmpty()) return;
        var mc = Minecraft.getInstance();
        // ParticleEngine registers this atlas with TextureManager, not ModelManager's AtlasSet.
        var texture = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (!(texture instanceof TextureAtlas atlas)) return;
        FxSlashRoute spec = route != null && route.ready() ? route.spec() : null;
        if (spec != null && !route.loaded()) return;
        var out = buffers.getBuffer(SkillEffectRenderTypes.SLASH_SPRITE);
        for (var strip : strips) {
            if (strip.alpha() < 1f / 255) continue;
            ResourceLocation spriteId;
            if (spec != null) {
                var frames = route.frames();
                int index = spec.frameIndex(strip.frame());
                if (index < 0 || index >= frames.size()) continue;
                spriteId = frames.get(index);
            } else {
                int semantic = Math.clamp(strip.frame(), 0, LEGACY_FRAMES.length - 1);
                spriteId = PresentationRoutes.slashFrame(semantic, LEGACY_FRAMES[semantic]);
            }
            TextureAtlasSprite sprite = atlas.getSprite(spriteId);
            if (!sprite.contents().name().equals(spriteId)) continue;
            if (!atlasReported) {
                atlasReported = true;
                EldenCombat.LOGGER.info("[skill-fx] Particle atlas sprite ready: {}", sprite.contents().name());
            }
            int tint = spec != null ? spec.tint() : strip.rgb();
            pose.pushPose();
            pose.translate(strip.x(), strip.y(), strip.z());
            if (spec != null && !spec.cameraFacing()) {
                // The entity renderer already applied -yaw, so the local frame is the skill's own. Applying
                // the entity yaw again here would rotate the artwork twice and would tie it to the camera.
                pose.mulPose(Axis.YP.rotationDegrees(spec.planeYaw()));
                pose.mulPose(Axis.XP.rotationDegrees(spec.planePitch()));
            } else {
                // Legacy and particle-like artwork stays a world-space billboard facing the camera.
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            }
            pose.mulPose(Axis.ZP.rotationDegrees(strip.roll()));
            var matrix = pose.last().pose();
            float x = strip.width() / 2, y = strip.height() / 2;
            vertex(out, matrix, -x, -y, sprite.getU0(), sprite.getV1(), strip.alpha(), tint);
            vertex(out, matrix, x, -y, sprite.getU1(), sprite.getV1(), strip.alpha(), tint);
            vertex(out, matrix, x, y, sprite.getU1(), sprite.getV0(), strip.alpha(), tint);
            vertex(out, matrix, -x, y, sprite.getU0(), sprite.getV0(), strip.alpha(), tint);
            pose.popPose();
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float u, float v,
                               float alpha, int tint) {
        out.addVertex(matrix, x, y, 0).setUv(u, v)
                .setColor(tint >> 16 & 255, tint >> 8 & 255, tint & 255,
                        Math.clamp((int) (alpha * 255), 0, 255)).setLight(LightTexture.FULL_BRIGHT);
    }
}
