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

/** Authored planes, including independently clipped wave lanes. No damage or entity mutation. */
final class ArtworkRenderer {
    private ArtworkRenderer() {}

    static boolean draw(SkillEffectMesh.Kind kind, float progress, int phase,
                        GoldenWaveEntity wave, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        var resolved = SkillSlashSpriteRenderer.route(kind);
        var declaration = PresentationRoutes.route(FxSlashRoute.routeId(kind));
        if (resolved == null || !resolved.ready() || declaration.artwork() == null) return false;
        var art = declaration.artwork();
        int frame = FxArtwork.frame(progress, resolved.spec().frameCount());
        if (frame < 0) return true;
        FxRenderBudget.FRAME.beginArtwork();
        TextureAtlasSprite sprite = null;
        boolean forceMissing=Boolean.getBoolean("eldencombat.vfxSmoke")&&Boolean.getBoolean("eldencombat.vfxSmokeMissing");
        if (resolved.loaded() && FxPalette.current().slashSprites() && !forceMissing) {
            var atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            sprite = atlas.getSprite(resolved.frames().get(frame));
        }
        if(Boolean.getBoolean("eldencombat.vfxSmoke")) recordFrame(resolved.id(),frame,sprite!=null);
        // CORE keeps one main plane (or the 16 clipped lanes), and omits secondary decorations.
        if (wave == null) {
            plane(pose,buffers,art,phase,art.width()*art.scale(phase),art.height()*art.scale(phase),
                    art.anchorU(),0,1,sprite,resolved.spec().outlineColor(),kind,progress);
        } else {
            float radius=wave.visualRadius(partialTick), travel=wave.visualTravel(partialTick);
            Vec3 base = new Vec3(Mth.lerp(partialTick,wave.xo,wave.getX()),Mth.lerp(partialTick,wave.yo,wave.getY()),Mth.lerp(partialTick,wave.zo,wave.getZ()));
            for(var lane:FxArtwork.lanes(radius,travel,wave.blockedLanes(),wave.isFlame())) {
                float lateral=lane.center();
                float forward=lane.forward();
                Vec3 center=base.add(wave.dirZ()*lateral+wave.dirX()*forward,0,-wave.dirX()*lateral+wave.dirZ()*forward);
                // Use loaded client terrain only; rendering must not force chunks or modify blocks.
                if(!wave.level().hasChunkAt(net.minecraft.core.BlockPos.containing(center))) continue;
                var ground=wave.level().clip(new ClipContext(center.add(0,1.5,0),center.add(0,-2.5,0),
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,wave));
                if(ground.getType()!=HitResult.Type.BLOCK) continue;
                pose.pushPose();
                pose.translate(lateral,ground.getLocation().y-base.y,forward);
                plane(pose,buffers,art,0,lane.width(),art.height(),.5f,
                        lane.u0(),lane.u1(),sprite,resolved.spec().outlineColor(),kind,progress);
                pose.popPose();
            }
        }
        return true;
    }

    private static final java.util.Map<String,java.util.BitSet> SEEN=new java.util.HashMap<>();
    private static void recordFrame(String id,int frame,boolean loaded) {
        String key=id+(loaded?"/atlas":"/outline");var seen=SEEN.computeIfAbsent(key,k->new java.util.BitSet());
        if(!seen.get(frame)) {
            seen.set(frame);
            com.dsh.eldencombat.EldenCombat.LOGGER.info("VFX_SMOKE_FRAME {} frame={}",key,frame);
        }
    }

    private static void plane(PoseStack pose, MultiBufferSource buffers, FxArtwork art, int phase,
                              float width,float height,float anchorU,float u0,float u1,
                              TextureAtlasSprite sprite,int color,SkillEffectMesh.Kind kind,float progress) {
        pose.pushPose();
        pose.translate(art.x(),art.y(),art.z());
        if(art.horizontal()) pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.mulPose(Axis.ZP.rotationDegrees(art.roll(phase)));
        float left=-anchorU*width, bottom=(art.anchorV()-1)*height;
        if(sprite!=null) {
            if(FxRenderBudget.FRAME.sprites(1)>0) {
                var out=buffers.getBuffer(SkillEffectRenderTypes.SLASH_SPRITE);
                quad(pose,out,left,bottom,left+width,bottom+height,sprite.getU(u0),sprite.getV1(),sprite.getU(u1),sprite.getV0(),0xFFFFFF,1,true);
            }
        } else {
            // Missing assets stay recognizable and directional. No explosion or box fallback.
            var out=buffers.getBuffer(SkillEffectRenderTypes.MESH);
            float alpha=Math.min(1,(1-progress)*4);
            boolean wave=kind==SkillEffectMesh.Kind.GOLD_WAVE||kind==SkillEffectMesh.Kind.FLAME_FRONT;
            boolean fire=kind==SkillEffectMesh.Kind.BLOOD_FLAME||kind==SkillEffectMesh.Kind.FLAME_FRONT;
            int pieces=wave?1:12;
            int admitted=FxRenderBudget.FRAME.panels(pieces);
            for(int i=0;i<admitted;i++) {
                float x,y,w,h;
                if(wave) {x=left;y=0;w=width;h=fire?height*.65f:.15f;}
                else if(fire) {x=left+i*width/pieces;y=bottom;w=width/pieces;h=height*(.3f+.5f*(float)Math.sin((i+.5f)/pieces*Math.PI));}
                else {
                    boolean arc=kind==SkillEffectMesh.Kind.WATERFOWL||kind==SkillEffectMesh.Kind.DEATH_SLASH||kind==SkillEffectMesh.Kind.CURSED_SLASH;
                    double angle=(i+.5)/pieces*(arc?Math.PI:Math.PI*2);
                    x=left+width*.5f+(float)Math.cos(angle)*width*.4f;
                    y=bottom+height*.5f+(float)Math.sin(angle)*height*.4f;
                    w=Math.max(.125f,width*.08f);h=Math.max(.125f,height*.06f);
                }
                quad(pose,out,x,y,x+w,y+h,0,0,0,0,color,alpha,false);
            }
        }
        pose.popPose();
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
