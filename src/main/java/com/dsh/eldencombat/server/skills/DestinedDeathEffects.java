package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = EldenCombat.MODID)
public final class DestinedDeathEffects {
    private record Key(ResourceKey<Level> dimension, UUID target) {}
    private record Effect(UUID owner, DeathMark mark) {}
    private static final Map<Key, Effect> EFFECTS = new HashMap<>();
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(.55f, .02f, .04f), 1.2f);
    private DestinedDeathEffects() {}

    public static void apply(ServerPlayer owner, LivingEntity target, RemembranceSkillSpec spec, long tick) {
        Key key = new Key(owner.level().dimension(), target.getUUID());
        Effect old = EFFECTS.get(key);
        DeathMark mark = old == null ? new DeathMark(tick, spec.effectLifespanTicks(), 3) : old.mark();
        mark.refresh(tick, spec.effectLifespanTicks(), 3);
        EFFECTS.put(key, new Effect(owner.getUUID(), mark));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var iterator = EFFECTS.entrySet().iterator();
        while (iterator.hasNext()) {
            var item = iterator.next();
            ServerLevel level = event.getServer().getLevel(item.getKey().dimension());
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(item.getValue().owner());
            if (level == null || owner == null || owner.level() != level
                    || !(level.getEntity(item.getKey().target()) instanceof LivingEntity target)
                    || !SkillHitEffects.validTarget(owner, target)) { iterator.remove(); continue; }
            long tick = level.getGameTime();
            DeathMark mark = item.getValue().mark();
            float damage = mark.pulse(tick);
            if (damage > 0) {
                SkillHitEffects.hurt(owner, target, damage, 0, true);
                level.sendParticles(RED, target.getX(), target.getY() + .8, target.getZ(), 5, .3, .3, .3, 0);
            }
            if (mark.expired(tick)) iterator.remove();
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) { EFFECTS.clear(); }
}
