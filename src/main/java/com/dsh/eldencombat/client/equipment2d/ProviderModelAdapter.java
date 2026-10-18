package com.dsh.eldencombat.client.equipment2d;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reads the already-baked provider models so the full chain is reused instead of re-declared.
 *
 * <p>Why this exists: making an external model the JSON {@code parent} of our own file proves nothing.
 * It does not prove that the provider's custom loader, its additional layers, its override resolution,
 * its per-context display overrides or its predicate namespace came along. Hand-writing a wrapper that
 * declares {@code parent: <provider model>} also leaves a dangling parent in a shipped file whenever
 * the dependency is absent.
 *
 * <p>So the chain is never re-declared and never flattened. The actual {@link BakedModel} the provider
 * produced is looked up in the baked table and handed straight to
 * {@link EquipmentRouteRenderer}, which draws it with the provider's own render passes, render types
 * and per-context display. {@code getParticleIcon} is not an acceptable substitute for a multi-layer
 * item model, and a {@code null} own geometry is a legitimate route difference rather than a reason to
 * reject the entry.
 *
 * <p>Everything here is capability checked <em>before</em> a provider model is consulted, so an absent
 * provider yields nothing and the legacy path stays in place without loading a provider class.
 */
public final class ProviderModelAdapter {

    /** One selectable provider state and the baked model its predicates resolve to. */
    public record Resolved(String state, ResourceLocation model, BakedModel baked) {
    }

    private ProviderModelAdapter() {
    }

    /**
     * Provider models for one entry, in override order, or an empty list when the capability is absent.
     *
     * @param baked     the baked model table handed out by the model event
     * @param manager   resource manager used for the presence probe
     * @param entry     the admitted route entry
     * @param predicate resolves a predicate resource ID to its current value for the actual stack
     */
    public static List<Resolved> resolve(Map<ModelResourceLocation, BakedModel> baked,
                                         ResourceManager manager,
                                         EquipmentRouteSnapshot.Entry entry,
                                         PredicateValue predicate) {
        if (entry.providerBase() == null || baked == null) {
            return List.of();
        }
        // Capability check happens before any provider model is consulted.
        for (ResourceLocation required : entry.requiredPresence()) {
            if (!EquipmentRouteSnapshot.present(manager, required)) {
                return List.of();
            }
        }
        List<Resolved> resolved = new ArrayList<>(entry.providerStates().size());
        for (EquipmentRouteSnapshot.ProviderState state : entry.providerStates()) {
            if (!state.usable()) {
                // Reachable only by a state own play cannot select (for example the provider's
                // blocking/throwing poses). Recorded as evidence, never fabricated, and it does not
                // stop the stand-by pose from being drawn.
                continue;
            }
            BakedModel model = baked.get(EquipmentRouteSnapshot.inventoryModel(state.model()));
            if (model == null) {
                model = baked.get(EquipmentRouteSnapshot.standaloneModel(state.model()));
            }
            if (model == null) {
                // The provider declared this state but its model is not in the baked table: the whole
                // chain is unavailable, so fall back as a unit rather than drawing a partial chain.
                return List.of();
            }
            if (!matches(state, predicate)) {
                continue;
            }
            resolved.add(new Resolved(state.state(), state.model(), model));
        }
        return List.copyOf(resolved);
    }

    /** Highest override wins, so the last matching state in declaration order is the visible one. */
    private static boolean matches(EquipmentRouteSnapshot.ProviderState state, PredicateValue predicate) {
        for (Map.Entry<ResourceLocation, Float> condition : state.predicates().entrySet()) {
            float actual = predicate.value(condition.getKey());
            if (actual < condition.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Current value of one provider predicate for the actual stack. */
    @FunctionalInterface
    public interface PredicateValue {
        float value(ResourceLocation predicate);
    }

    /** Aggregated, de-duplicated reasons the provider chain is unavailable, grouped per equipment. */
    public static Map<String, List<String>> capabilityReport(ResourceManager manager,
                                                             List<EquipmentRouteSnapshot.Entry> providerEntries) {
        Map<String, List<String>> report = new TreeMap<>();
        for (EquipmentRouteSnapshot.Entry entry : providerEntries) {
            List<String> reasons = new ArrayList<>();
            Set<ResourceLocation> missing = new LinkedHashSet<>();
            for (ResourceLocation required : entry.requiredPresence()) {
                if (!EquipmentRouteSnapshot.present(manager, required)) {
                    missing.add(required);
                }
            }
            if (!missing.isEmpty()) {
                reasons.add("missing provider resources: " + missing);
            }
            if (!reasons.isEmpty()) {
                report.put(entry.id(), List.copyOf(reasons));
            }
        }
        return Map.copyOf(report);
    }
}
