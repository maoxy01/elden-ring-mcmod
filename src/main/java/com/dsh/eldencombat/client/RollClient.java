package com.dsh.eldencombat.client;

import com.dsh.eldencombat.compat.*;
import com.dsh.eldencombat.core.*;
import com.dsh.eldencombat.network.RollRejectedPayload;
import net.combat_roll.internals.RollingEntity;
import net.minecraft.client.Minecraft;

public final class RollClient {
    private RollClient() {}
    public static boolean rolling() {
        var p = Minecraft.getInstance().player;
        return CombatRollCompat.present() && p instanceof RollingEntity r && r.getRollManager().isRolling();
    }
    public static boolean allowed() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.isWindowActive() || !mc.player.isAlive() || mc.player.isSpectator()) return false;
        if (!SkillPoseAnimator.rollCancelable()) return false;
        var state = ClientCombat.stateFor(mc.player);
        if (state != null && state.guarding()) return false;
        if (state == null || "NONE".equals(state.actionId())) return true;
        ActionId action;
        try { action=ActionId.valueOf(state.actionId()); } catch (IllegalArgumentException ignored) { return false; }
        if (action == ActionId.HEAVY_CHARGE) return true;
        if (action == ActionId.DEATH || action == ActionId.STAGGER || action == ActionId.GUARD_BROKEN || action == ActionId.ROLL) return false;
        var spec = CombatRules.specFor(com.dsh.eldencombat.EldenCombat.config(), WeaponClass.valueOf(state.weaponClass()), action);
        return spec.rollCancelAllowed(mc.level.getGameTime()-state.actionStartTick());
    }
    public static void rejected(RollRejectedPayload payload) {
        var player = Minecraft.getInstance().player;
        if (CombatRollCompat.present() && player instanceof RollingEntity r) {
            ((RollPrediction) r.getRollManager()).eldencombat$reject(payload.available(), payload.cooldown(), payload.progress());
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
    }
}
