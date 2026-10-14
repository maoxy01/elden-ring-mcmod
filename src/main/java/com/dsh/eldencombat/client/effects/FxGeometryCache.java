package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.effects.FxPalette;
import com.dsh.eldencombat.effects.FxSlashRoute;
import com.dsh.eldencombat.effects.SkillEffectMesh;
import com.dsh.eldencombat.effects.SkillOutlineShapes;
import com.dsh.eldencombat.effects.SkillSlashSprites;
import com.dsh.eldencombat.effects.WaveFront;
import net.minecraft.world.entity.Entity;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;

/** Normal effects sample geometry at the client tick; world movement remains interpolated. */
final class FxGeometryCache {
    private static final WeakHashMap<Entity, FxGeometryCache> ENTRIES = new WeakHashMap<>();
    private long tick = Long.MIN_VALUE, revision = -1;
    private float radius, progress, travel;
    private int phase, blocked;
    private SkillEffectMesh.Kind kind;
    private FxSlashRoute route;
    List<SkillEffectMesh.Box> boxes = List.of(), debris = List.of();
    List<SkillSlashSprites.Strip> strips = List.of();
    List<SkillOutlineShapes.Panel> outline = List.of();
    private FxGeometryCache() {}
    static FxGeometryCache get(Entity entity, SkillEffectMesh.Kind kind, float radius, float progress, int phase,
                               float travel, int blocked, boolean wave, FxSlashRoute route) {
        var cache = ENTRIES.computeIfAbsent(entity, ignored -> new FxGeometryCache());
        long tick = wave ? 0 : entity.level().getGameTime();
        // A formed golden front has fixed relative offsets; don't rebuild for its translation. Its crest
        // animation rides on progress, which keeps changing after formation, so lanes cannot stop animating.
        float profileTravel = kind == SkillEffectMesh.Kind.GOLD_WAVE ? Math.min(travel, (float) WaveFront.FORMATION_DISTANCE) : travel;
        boolean routeChanged = !Objects.equals(cache.route, route);
        if (cache.tick != tick || cache.revision != FxPalette.revision() || cache.kind != kind
                || cache.radius != radius || cache.progress != progress || cache.phase != phase
                || cache.travel != profileTravel || cache.blocked != blocked || routeChanged) {
            cache.boxes = SkillEffectMesh.build(kind, radius, progress, phase, profileTravel, blocked);
            cache.debris = SkillEffectMesh.debris(kind, radius, progress);
            cache.strips = SkillSlashSprites.build(kind, radius, progress, phase);
            cache.outline = route != null && route.usesOutline()
                    ? SkillOutlineShapes.build(route.outline(), route.outlineColor(), radius, progress, phase, profileTravel, blocked)
                    : List.of();
            cache.tick = tick;cache.revision = FxPalette.revision();cache.kind = kind;
            cache.radius = radius;cache.progress = progress;cache.phase = phase;cache.travel = profileTravel;cache.blocked = blocked;
            cache.route = route;
        }
        return cache;
    }
}
