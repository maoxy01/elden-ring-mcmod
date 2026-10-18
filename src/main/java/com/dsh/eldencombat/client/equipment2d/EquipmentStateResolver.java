package com.dsh.eldencombat.client.equipment2d;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.dsh.eldencombat.client.ClientCombat;
import com.dsh.eldencombat.server.Weapons;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.core.component.DataComponents;

/**
 * Which visual state to draw, decided from the <em>actual</em> stack in the <em>actual</em> context.
 *
 * <p>Contract §2 row "EquipmentStateResolver / EquipmentPredicates" and §10.4. The previous version
 * divided elapsed ticks by {@code ItemStack.getUseDuration}, which is 72000 for a bow and
 * {@code chargeDuration + 3} for a crossbow. That made a full draw read 0.000278 and a crossbow stage
 * off by the +3; the vanilla values the contract names are:
 *
 * <pre>
 *   bow        pull = (useDuration - useItemRemainingTicks) / 20      (ItemProperties "pull")
 *   crossbow   pull = (useDuration - useItemRemainingTicks) / CrossbowItem.getChargeDuration(stack, holder)
 *              and 0 while already charged
 * </pre>
 *
 * <p>Rules this class exists to keep:
 * <ul>
 *   <li>Never build a temporary stack. A scratch stack loses upgrade, affinity, ash, custom name,
 *       durability and enchantment, and would then resolve against the wrong provider model.</li>
 *   <li>Every fraction is a <em>visual</em> quantity. It never sets charge time, fire rate or damage.</li>
 *   <li>Zero whenever the drawn stack is not the stack actually in use, and zero when there is no
 *       holder at all (GUI, ground, item frame), so an observer never borrows the local player.</li>
 *   <li>A state the art does not carry returns {@code null} so the caller falls back, instead of
 *       silently drawing a stand-by pose that claims to be the real state.</li>
 * </ul>
 */
public final class EquipmentStateResolver {

    /** Vanilla bow thresholds; 13 ticks = 0.65, 18 ticks = 0.9, 20 ticks = 1. */
    public static final float PULL_1 = 0.65f;
    public static final float PULL_2 = 0.9f;

    /** The vanilla {@code minecraft:pull} divisor for bows. Not the 72000 use duration. */
    public static final float BOW_FULL_PULL_TICKS = 20f;

    /** Own ballista stage thresholds, taken from the art's declared {@code artDrawFraction}. */
    public static final float BALLISTA_STAGE_1 = 0.30f;
    public static final float BALLISTA_STAGE_2 = 0.65f;

    private EquipmentStateResolver() {
    }

    /** True when this holder is holding exactly this stack and is using it right now. */
    public static boolean using(LivingEntity holder, ItemStack stack) {
        if (holder == null || stack == null || stack.isEmpty()) {
            return false;
        }
        return holder.isUsingItem() && holder.getUseItem() == stack;
    }

    /** Elapsed use ticks of the real stack; 0 when it is not the using stack. */
    public static int elapsedTicks(LivingEntity holder, ItemStack stack) {
        if (!using(holder, stack)) {
            return 0;
        }
        int elapsed = stack.getUseDuration(holder) - holder.getUseItemRemainingTicks();
        return Math.max(0, elapsed);
    }

    /**
     * The one place a use fraction is formed: {@code elapsed / divisor}, clamped to {@code [0, 1]}.
     *
     * <p>Kept pure and public so the divisor itself — the defect this class was rewritten for — is
     * checkable without a client: a bow divides by 20, never by its 72000-tick use duration, and a
     * crossbow divides by its real charge duration, never by {@code chargeDuration + 3}.
     */
    public static float fraction(int elapsedTicks, int divisorTicks) {
        if (divisorTicks <= 0) {
            return 0f;
        }
        return Math.clamp(elapsedTicks / (float) divisorTicks, 0f, 1f);
    }

    /** Bow draw fraction in vanilla's own scale: 20 elapsed ticks is a full draw. */
    public static float bowPull(LivingEntity holder, ItemStack stack) {
        if (!using(holder, stack)) {
            return 0f;
        }
        return fraction(elapsedTicks(holder, stack), (int) BOW_FULL_PULL_TICKS);
    }

    /**
     * Crossbow charge fraction using the item's real charge rule, so a Quick Charge enchantment
     * moves the divisor exactly as the gameplay does. Zero while the crossbow is already charged.
     */
    public static float crossbowCharge(LivingEntity holder, ItemStack stack) {
        if (!using(holder, stack) || CrossbowItem.isCharged(stack)) {
            return 0f;
        }
        return fraction(elapsedTicks(holder, stack), CrossbowItem.getChargeDuration(stack, holder));
    }

    /** Own bow family: the shared art states every own bow emits, chosen from the draw fraction. */
    public static String bowState(LivingEntity holder, ItemStack stack) {
        if (!using(holder, stack)) {
            return "standby";
        }
        return bowStateFor(bowPull(holder, stack));
    }

    /** The fraction -> state mapping on its own, so the thresholds are checkable off-client. */
    public static String bowStateFor(float pull) {
        if (pull >= PULL_2) {
            return "pulling_2";
        }
        if (pull >= PULL_1) {
            return "pulling_1";
        }
        return "pulling_0";
    }

    /** The crossbow fraction -> own ballista loading state mapping. */
    public static String ballistaLoadingFor(float charge) {
        if (charge >= BALLISTA_STAGE_2) {
            return "loading_2";
        }
        if (charge >= BALLISTA_STAGE_1) {
            return "loading_1";
        }
        return "loading_0";
    }

    /**
     * Own ballista: {@code standby / loading_0..2 / loaded}, or {@code null} for a reachable state the
     * art does not cover.
     *
     * <p>A charged firework is exactly that case (§7/§10.4). The ballista has no firework artwork, and
     * the contract forbids dressing it up as a loaded ordinary bolt or as stand-by, so the caller must
     * fall back to the legacy model instead.
     */
    public static String ballistaState(LivingEntity holder, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (using(holder, stack) && stack.getItem() instanceof CrossbowItem) {
            return ballistaLoadingFor(crossbowCharge(holder, stack));
        }
        if (!(stack.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(stack)) {
            return "standby";
        }
        return hasFireworkProjectile(stack) ? null : "loaded";
    }

    /**
     * Shield blocking, from the real using stack.
     *
     * <p>Both halves matter: the item must be a shield (so an unrelated use action such as eating does
     * not raise the guard) <em>and</em> it must be this very stack (so the other hand's shield is not
     * posed by this one).
     */
    public static boolean shieldBlocking(LivingEntity holder, ItemStack stack) {
        if (holder == null || stack == null || stack.isEmpty() || !(stack.getItem() instanceof ShieldItem)) {
            return false;
        }
        if (holder instanceof Player player) {
            var state = ClientCombat.stateFor(player);
            if (state != null && state.combatMode()) {
                // Combat mode cancels vanilla item use. Use this rendered player's server mirror,
                // selecting the main-hand shield first when two shields are held.
                ItemStack main = holder.getMainHandItem();
                ItemStack shield = main.is(Weapons.SHIELDS) ? main : holder.getOffhandItem();
                return combatShieldBlocking(state.guarding(), state.actionId(), stack == shield);
            }
        }
        return using(holder, stack);
    }

    public static boolean combatShieldBlocking(boolean guarding, String actionId, boolean selectedShield) {
        return guarding && "NONE".equals(actionId) && selectedShield;
    }

    /** True when the charged projectile really is a firework rocket, read from the stack component. */
    public static boolean hasFireworkProjectile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (charged == null || charged.isEmpty()) {
            return false;
        }
        return charged.contains(net.minecraft.world.item.Items.FIREWORK_ROCKET);
    }

    /** Own sprites that only ship a single held pose. */
    public static String staticState() {
        return "standby";
    }
}
