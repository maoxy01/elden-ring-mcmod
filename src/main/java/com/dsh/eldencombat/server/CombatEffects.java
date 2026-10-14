package com.dsh.eldencombat.server;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.ActionId;
import com.dsh.eldencombat.core.CombatState;
import com.dsh.eldencombat.core.WeaponClass;
import com.dsh.eldencombat.entity.MargitEntity;
import com.dsh.eldencombat.registry.ModParticles;
import com.dsh.eldencombat.skeleton.PlayerAssets;
import com.dsh.eldencombat.skeleton.AnimationClip;
import com.dsh.eldencombat.skeleton.ClipTiming;
import com.dsh.eldencombat.skeleton.SkeletonData;
import com.dsh.eldencombat.skeleton.SkeletonPose;
import com.dsh.eldencombat.skeleton.HitVolumes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import org.joml.Vector3d;
import org.joml.Vector3f;
import java.util.Map;

/** Server-owned one-shot feedback; particles never decide damage or recovery. */
public final class CombatEffects {
    private CombatEffects() {}
    private static void sound(Entity actor,SoundEvent sound,float volume,float pitch) {
        actor.level().playSound(null,actor.getX(),actor.getY(),actor.getZ(),sound,SoundSource.PLAYERS,volume,pitch);
    }
    private static void burst(ServerLevel level,SimpleParticleType type,Vec3 point,int count,double spread,double speed) {
        level.sendParticles(type,point.x,point.y,point.z,count,spread,spread/3,spread,speed);
    }
    public static void hit(Entity actor,Entity victim) {
        if(actor.level() instanceof ServerLevel level) {
            sound(actor,SoundEvents.PLAYER_ATTACK_STRONG,0.6f,1);
            burst(level,ModParticles.SPARK.get(),victim.getBoundingBox().getCenter(),5,0.13,0.04);
        }
    }
    public static void blocked(ServerPlayer player,boolean broken,boolean parried) {
        burst(player.serverLevel(),broken?ModParticles.DUST.get():ModParticles.SPARK.get(),player.getEyePosition().add(player.getLookAngle().scale(0.45)),
            broken?10:parried?12:7,0.15,0.04);
    }
    public static void drink(ServerPlayer player) {
        sound(player,SoundEvents.GENERIC_DRINK,0.45f,1);
        burst(player.serverLevel(),ModParticles.HEAL.get(),player.position().add(0,1,0),8,0.2,0.025);
    }
    public static void playerTick(ServerPlayer player,CombatState state,long now) {
        if(state.action==ActionId.NONE||state.spec==null||state.action==ActionId.STAGGER)return;
        WeaponClass weapon=state.actionWeapon!=WeaponClass.NONE?state.actionWeapon:Weapons.classify(player);
        PlayerAssets assets=PlayerAssets.get();
        String id=PlayerAssets.clipId(state.action,false,state.actionSequence,0,false);
        var clip=assets.clip(weapon,state.actionSlim,id);
        int start=state.spec.windows().isEmpty()?-1:state.spec.windows().getFirst().start();
        int end=state.spec.windows().isEmpty()?-1:state.spec.windows().getFirst().endExclusive();
        int full=weapon==WeaponClass.NONE?20:EldenCombat.config().weapon(weapon).chargeFullTicks;
        int elapsed=state.tickInAction(now);
        double frame=ClipTiming.frame(clip,elapsed,state.spec.totalTicks(),start,end,full);
        double previous=elapsed==0?-1:ClipTiming.frame(clip,elapsed-1,state.spec.totalTicks(),start,end,full);
        for(var event:clip.eventsBetween(previous,frame)) switch(event.type()) {
            case "sword_whoosh","charged_whoosh" -> sound(player,SoundEvents.PLAYER_ATTACK_SWEEP,weapon==WeaponClass.GREAT_SWORD?0.65f:0.45f,weapon==WeaponClass.GREAT_SWORD?0.85f:1.15f);
            case "roll_whoosh" -> {
                sound(player,SoundEvents.WOOL_STEP,0.4f,1);
                if(player.onGround())burst(player.serverLevel(),ModParticles.DUST.get(),player.position().add(0,0.05,0),6,0.2,0.03);
            }
            case "parry_whoosh" -> sound(player,SoundEvents.PLAYER_ATTACK_SWEEP,0.3f,1.3f);
            default -> { }
        }
    }
    public static void bossTick(MargitEntity boss) {
        if(!(boss.level() instanceof ServerLevel level)||boss.isRemoved())return;
        var assets=SkeletonData.assets(); if(assets==null)return;
        String id=boss.animClip(); var clip=assets.clip(id);
        long elapsed=level.getGameTime()-boss.animStartTick();
        if(elapsed<0)return;
        SkeletonPose pose=SkeletonPose.of(assets.margit(),clip.poseAt(elapsed));
        for(var event:clip.eventsBetween(elapsed-1,elapsed)) {
            if(event.type().equals("equip")) {
                sound(boss,SoundEvents.AMETHYST_BLOCK_CHIME,0.5f,1.05f);
                burst(level,ModParticles.SPARK.get(),world(boss,pose.boneOrigin("spectral_socket")),8,0.15,0.025);
            } else if(event.type().equals("impact")) {
                boolean hammer=id.equals(MargitEntity.ANIM_HAMMER_SLAM);
                var staff=HitVolumes.cubeAxisCapsule(pose,"staff_shaft",0.28);
                Vector3f point=hammer?SkeletonPose.rest(assets.spectralHammer()).attachTo(pose,"spectral_socket").modelToWorld("root",0,27,0)
                    :new Vector3f((float)(staff.a().y<staff.b().y?staff.a().x:staff.b().x),
                        (float)Math.min(staff.a().y,staff.b().y),(float)(staff.a().y<staff.b().y?staff.a().z:staff.b().z));
                Vec3 probe=world(boss,point);
                var contact=level.clip(new ClipContext(probe.add(0,0.5,0),probe.add(0,-4,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
                if(contact.getType()!=HitResult.Type.MISS)probe=contact.getLocation();
                sound(boss,hammer?SoundEvents.ANVIL_LAND:SoundEvents.WOOD_BREAK,hammer?1:0.85f,0.85f);
                burst(level,ModParticles.DUST.get(),probe,hammer?22:18,hammer?0.6:0.4,0.08);
                if(hammer)burst(level,ModParticles.SPARK.get(),probe,8,0.4,0.07);
            }
        }
        if(id.equals(MargitEntity.ANIM_DEATH)) {
            if(elapsed==40||elapsed==44||elapsed==48)burst(level,ModParticles.DUST.get(),world(boss,pose.boneOrigin("torso")),12,0.5,0.02);
            return;
        }
        String item=clip.equippedAt(elapsed);
        if(item!=null && elapsed>=clip.hitStart() && elapsed<clip.hitEnd()) {
            var accessory=SkeletonPose.rest(assets.accessory(item)).attachTo(pose,"spectral_socket");
            var point=accessory.modelToWorld("root",0,item.equals("spectral_hammer")?27:18,0);
            burst(level,ModParticles.SPARK.get(),world(boss,point),2,0.015,0);
        }
    }
    private static Vec3 world(MargitEntity boss,Vector3f point) {
        Vector3d p=HitResolver.toWorld(boss,boss.yBodyRot,new Vector3d(point)); return new Vec3(p.x,p.y,p.z);
    }
}
