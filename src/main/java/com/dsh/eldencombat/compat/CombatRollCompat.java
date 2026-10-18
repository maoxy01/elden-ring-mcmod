package com.dsh.eldencombat.compat;

import com.dsh.eldencombat.core.*;
import com.dsh.eldencombat.network.RollRejectedPayload;
import com.dsh.eldencombat.registry.ModAttachments;
import com.dsh.eldencombat.server.ServerCombat;
import com.dsh.eldencombat.server.skills.SkillDriver;
import net.combat_roll.CombatRollMod;
import net.combat_roll.api.CombatRoll;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Optional integration. All calls mentioning upstream types are guarded by present(). */
public final class CombatRollCompat {
    private static final Map<UUID, RollChargeState> STATES = new HashMap<>();
    private CombatRollCompat() {}
    public static boolean present() { return ModList.get().isLoaded("combat_roll"); }
    public static void clear(ServerPlayer player) { STATES.remove(player.getUUID()); }
    public static void clearAll() { STATES.clear(); }
    public static boolean rolling(ServerPlayer player) {
        var state = STATES.get(player.getUUID());
        return present() && state != null && state.rolling(player.server.getTickCount());
    }
    public static void tick(ServerPlayer player) {
        if (!present()) return;
        if (!player.isAlive()) { clear(player); return; }
        var config = CombatRollMod.config;
        STATES.computeIfAbsent(player.getUUID(), id -> new RollChargeState()).tick(
                (int) player.getAttributeValue(CombatRoll.Attributes.COUNT.entry),
                (float) (player.getAttributeValue(CombatRoll.Attributes.RECHARGE.entry) / 20.0),
                Math.round(config.roll_cooldown * 20));
        var combat = player.getData(ModAttachments.COMBAT_STATE);
        if (combat.action == ActionId.ROLL) combat.clearAction();
    }
    /** Invoked on the server thread BEFORE upstream broadcasts or charges exhaustion. */
    public static boolean admit(ServerPlayer player, Vec3 velocity) {
        var budget = STATES.computeIfAbsent(player.getUUID(), id -> new RollChargeState());
        var config = CombatRollMod.config;
        long now = player.server.getTickCount();
        var combat = player.getData(ModAttachments.COMBAT_STATE);
        var cast = SkillDriver.cast(player);
        var spec = SkillDriver.spec(cast.skillId());
        boolean skillAllowed = !cast.isCasting() || (spec != null
                && cast.recoveryCancelable(player.level().getGameTime(), spec.recoveryTicks()));
        boolean allowed = player.isAlive() && !player.isSpectator() && !player.isSleeping()
                && !player.isPassenger() && !player.isSwimming() && !player.isVisuallyCrawling()
                && !player.isUsingItem() && !player.isBlocking()
                && player.containerMenu == player.inventoryMenu
                && player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0
                && (config.allow_rolling_while_airborn || player.onGround())
                && player.getFoodData().getFoodLevel() > config.food_level_required
                && (config.allow_rolling_while_weapon_cooldown || player.getAttackStrengthScale(0) >= .95f)
                && Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)
                && skillAllowed && !combat.guarding && CombatRules.canAccept(combat, ActionId.ROLL, player.level().getGameTime())
                && budget.canStart(now);
        if (!allowed) {
            PacketDistributor.sendToPlayer(player, new RollRejectedPayload(budget.available(), budget.cooldown(), budget.progress()));
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            return false;
        }
        // All rejection checks precede cancellation: a stale/forged request cannot cancel a skill.
        budget.start(now, config.roll_duration, Math.round(config.roll_cooldown * 20));
        if (cast.isCasting()) SkillDriver.cancel(player, player.level().getGameTime(), "Combat Roll收招衔接");
        combat.clearAction();
        combat.guarding = false;
        ServerCombat.syncNow(player);
        return true;
    }
}
