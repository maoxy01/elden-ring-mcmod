package com.dsh.eldencombat.client.equipment2d;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Predicate values for the provider override chain, resolved on the <em>actual</em> stack.
 *
 * <p>Contract §2 row on {@code EquipmentPredicates} and §6/§10.4. Two things were wrong before and are
 * fixed here:
 *
 * <ol>
 *   <li>The aliases divided by {@code getUseDuration} instead of the visual scale. They now share
 *       {@link EquipmentStateResolver}'s arithmetic: {@code elapsed / 20} for a bow and
 *       {@code elapsed / CrossbowItem.getChargeDuration} for a crossbow.</li>
 *   <li>{@code value(...)} returned 0 for everything it did not own, which silently disabled the
 *       {@code minecraft:*} half of every provider chain. The vanilla properties are already
 *       registered on our own items during client setup (they delegate to the reference item's own
 *       property, so there is no cross-item cast), so the real registered function is consulted
 *       first. {@code getProperty} is evaluated against the actual stack, never a scratch one.</li>
 * </ol>
 *
 * <p>The disassembled provider {@code registerLongbowPropertyOverrides} captures a
 * {@code LongbowItem} and calls {@code getNockProgress} on it. That callback cannot be reused and the
 * item type must not be cast, so equivalent visual values are published in the provider's own
 * predicate namespace instead, computed from our own item's real usage and zeroed whenever the drawn
 * stack is not the using stack.
 *
 * <p>{@code blocking} and {@code throwing} exist in the provider models but own play has no such
 * state. They are recorded as evidence and never registered, so they can never fire.
 */
public final class EquipmentPredicates {

    /** One registered alias: which predicate, and how its value is derived. */
    private record Alias(ResourceLocation predicate, BiFunction<ItemStack, LivingEntity, Float> value) {
    }

    private static final Set<ResourceLocation> registered = new LinkedHashSet<>();
    private static final Map<ResourceLocation, Alias> aliases = new LinkedHashMap<>();

    private static final ResourceLocation SPARTAN_PULLING =
            ResourceLocation.fromNamespaceAndPath("spartan_weaponry_unofficial", "pulling");
    private static final ResourceLocation SPARTAN_PULL =
            ResourceLocation.fromNamespaceAndPath("spartan_weaponry_unofficial", "pull");

    /** Provider predicates that are deliberately never registered because own play has no such state. */
    private static final Set<String> EVIDENCE_ONLY = Set.of("blocking", "throwing");

    private EquipmentPredicates() {
    }

    /**
     * Register the aliases the admitted provider entries need.
     *
     * <p>Registration is idempotent, runs on the items themselves, and the item set comes from the
     * routes, so nothing is registered for a state the contract marked unsupported.
     */
    public static void register(Set<String> aliasPredicates, Set<ResourceLocation> items) {
        aliases.clear();
        registered.clear();
        for (String predicate : aliasPredicates) {
            ResourceLocation key = ResourceLocation.tryParse(predicate);
            if (key == null) {
                continue;
            }
            BiFunction<ItemStack, LivingEntity, Float> value = switch (key.getPath()) {
                case "pulling" -> EquipmentPredicates::pulling;
                case "pull" -> EquipmentPredicates::pull;
                default -> null;
            };
            if (value == null) {
                // blocking / throwing and anything else stay evidence only: never registered, never fired.
                continue;
            }
            aliases.put(key, new Alias(key, value));
        }
        if (aliases.isEmpty()) {
            return;
        }
        for (ResourceLocation itemId : items) {
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            for (Alias alias : aliases.values()) {
                ItemProperties.register(item, alias.predicate(),
                        (stack, level, entity, seed) -> alias.value().apply(stack, entity));
                registered.add(alias.predicate());
            }
        }
    }

    /** 1 only while the drawn stack is the one actually being used; 0 otherwise. */
    private static float pulling(ItemStack stack, LivingEntity holder) {
        if (!EquipmentStateResolver.using(holder, stack)) {
            return 0f;
        }
        // Matches the vanilla crossbow rule: a charged crossbow is no longer "pulling".
        if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            return 0f;
        }
        return 1f;
    }

    /**
     * Visual draw/charge fraction, in the provider's own threshold space (0.65 then 0.9).
     *
     * <p>This is a visual quantity. It does not set charge time, fire rate or damage, and it returns 0
     * when the use has already ended or belongs to another stack.
     */
    private static float pull(ItemStack stack, LivingEntity holder) {
        if (stack.getItem() instanceof CrossbowItem) {
            return EquipmentStateResolver.crossbowCharge(holder, stack);
        }
        return EquipmentStateResolver.bowPull(holder, stack);
    }

    /**
     * Value of one predicate for the render path.
     *
     * <p>Order matters: our own alias first (it is the value we publish), then the property actually
     * registered on the item — which is where the vanilla {@code pull}/{@code pulling}/{@code charged}/
     * {@code firework}/{@code blocking} semantics come from. An unknown predicate reads as zero, so a
     * state is never selected by accident.
     */
    public static float value(ResourceLocation predicate, ItemStack stack, LivingEntity holder) {
        if (predicate == null || stack == null || stack.isEmpty()) {
            return 0f;
        }
        Alias alias = aliases.get(predicate);
        if (alias != null) {
            return alias.value().apply(stack, holder);
        }
        if (SPARTAN_PULLING.equals(predicate)) {
            return pulling(stack, holder);
        }
        if (SPARTAN_PULL.equals(predicate)) {
            return pull(stack, holder);
        }
        ItemPropertyFunction property = ItemProperties.getProperty(stack, predicate);
        if (property == null) {
            return 0f;
        }
        return property.call(stack, clientLevel(holder), holder, 0);
    }

    private static ClientLevel clientLevel(LivingEntity holder) {
        if (holder != null && holder.level() instanceof ClientLevel level) {
            return level;
        }
        return Minecraft.getInstance().level;
    }

    /** True when this predicate is deliberately not registered (evidence-only provider states). */
    public static boolean evidenceOnly(ResourceLocation predicate) {
        return predicate != null && EVIDENCE_ONLY.contains(predicate.getPath());
    }

    /** Predicates actually registered on own items; the rest stay as evidence only. */
    public static Set<ResourceLocation> registeredPredicates() {
        return Set.copyOf(registered);
    }
}
