package com.dsh.eldencombat.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import java.util.WeakHashMap;
import java.util.Map;

final class CombatParticle extends TextureSheetParticle {
    private static final Map<CombatParticle,Long> ACTIVE=new WeakHashMap<>();
    static CombatParticle create(ClientLevel world,SpriteSet sprite,double x,double y,double z,double dx,double dy,double dz,int kind) {
        // Expiry also releases particles abandoned by an atlas reload.
        ACTIVE.entrySet().removeIf(entry -> entry.getKey().level != world
            || !entry.getKey().isAlive() || entry.getValue() <= world.getGameTime());
        if(ACTIVE.size()>=160)return null;
        return new CombatParticle(world,sprite,x,y,z,dx,dy,dz,kind);
    }
    private CombatParticle(ClientLevel world,SpriteSet sprite,double x,double y,double z,double dx,double dy,double dz,int kind) {
        super(world,x,y,z,dx,dy,dz);
        xd=dx; yd=dy; zd=dz; friction=0.85f;
        lifetime=kind==2?14:kind==1?10:6;
        ACTIVE.put(this,world.getGameTime()+lifetime);
        quadSize=kind==1?0.06f:0.035f;
        gravity=kind==1?0.08f:0;
        if(kind==2)setColor(0.72f,0.85f,0.65f);
        else if(kind==1)setColor(0.65f,0.70f,0.67f);
        else setColor(0.94f,0.82f,0.5f);
        pickSprite(sprite);
    }
    @Override public void tick() { super.tick(); alpha=Math.max(0,1-age/(float)lifetime); }
    @Override public void remove() {
        ACTIVE.remove(this);
        super.remove();
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
}
