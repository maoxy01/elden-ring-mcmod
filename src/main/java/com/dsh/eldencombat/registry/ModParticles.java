package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.particles.ParticleType;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES=DeferredRegister.create(Registries.PARTICLE_TYPE,EldenCombat.MODID);
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> SPARK=PARTICLES.register("spark",()->new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> DUST=PARTICLES.register("dust",()->new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>,SimpleParticleType> HEAL=PARTICLES.register("heal",()->new SimpleParticleType(false));
    private ModParticles() {}
}
