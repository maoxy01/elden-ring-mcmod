package com.dsh.eldencombat.server;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.ActionId;
import com.dsh.eldencombat.core.ActionSpec;
import com.dsh.eldencombat.core.CombatConfig;
import com.dsh.eldencombat.core.CombatRules;
import com.dsh.eldencombat.core.CombatState;
import com.dsh.eldencombat.core.IntentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import com.dsh.eldencombat.core.WeaponClass;
import com.dsh.eldencombat.core.geom.Collision;
import com.dsh.eldencombat.network.CombatIntentPayload;
import com.dsh.eldencombat.network.CombatStatePayload;
import com.dsh.eldencombat.network.LockOnPayload;
import com.dsh.eldencombat.registry.ModAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端战斗循环：整合服务器权威判定（单人亦同），
 * 负责动作接受、资源消耗、窗口结算、动作取消与状态同步。
 *
 * <p><b>必须显式注册到游戏事件总线</b>：本类的 {@code @SubscribeEvent} 都是**实例方法**，
 * {@code @EventBusSubscriber} 只适用于静态处理器，所以走 {@link #register()} 注册单例。
 * 历史上这里写着"不要注册，会和 Epic Fight 冲突"——EF 已移除，那条约束作废；
 * 不注册的后果是普攻命中窗口、翻滚位移、精力恢复、受击打断全部静默失效。
 */
public final class ServerCombat {

    public static final ServerCombat INSTANCE = new ServerCombat();

    /** 一次性注册防护：重复注册会让每个事件被执行两遍（双倍扣精力、双倍判伤）。 */
    private static boolean registered;
    /** 真实事件计数：仅供自检与日志使用，证明总线真的连上了。 */
    private static long tickEvents;

    /** 在模组构造期调用一次；重复调用无副作用。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(INSTANCE);
        registered = true;
        EldenCombat.LOGGER.info("[EldenCombat] ServerCombat 已注册到游戏事件总线（单例，一次）");
    }

    public static boolean isRegistered() {
        return registered;
    }

    /** 已处理的服务端 tick 事件数（自检用：为 0 说明没接上总线）。 */
    public static long tickEventCount() {
        return tickEvents;
    }

    /** 每个玩家最后接受的动作序号，用于拒绝重放 */
    private final Map<UUID, Integer> lastSequence = new HashMap<>();

    private ServerCombat() {
    }

    // ------------------------------------------------------------------
    // 网络入口
    // ------------------------------------------------------------------

    /**
     * 外部改动 {@link CombatState} 后立刻重同步（赐福休息回精力/药瓶等）。
     * 战斗循环本身不变：它只在状态变化时调用私有 sync，所以外部改动必须显式通知。
     */
    public static void syncNow(ServerPlayer player) {
        INSTANCE.sync(player, player.getData(com.dsh.eldencombat.registry.ModAttachments.COMBAT_STATE));
    }

    public static void handleIntent(CombatIntentPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                INSTANCE.apply(player, payload);
            }
        });
    }

    // ------------------------------------------------------------------
    // 每 tick 推进
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickEvents++;
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player, player.level().getGameTime());
        }
    }

    private void tickPlayer(ServerPlayer player, long tick) {
        CombatConfig config = EldenCombat.config();
        CombatState state = player.getData(ModAttachments.COMBAT_STATE);
        state.ensureInit(config);

        com.dsh.eldencombat.compat.CombatRollCompat.tick(player);

        // §5：主手武器的 reach / 重量攻速在任何模式下都生效（退出战斗模式不等于武器变短）
        WeaponAttributes.tick(player);

        if (!player.isAlive()) {
            if (state.isBusy() || state.guarding || state.combatMode) {
                state.reset(config);
                sync(player, state);
            }
            return;
        }

        if (!state.combatMode) {
            state.tickStamina(config, tick, false, Talismans.staminaRegenMultiplier(player));
            sync(player, state);
            return;
        }

        if ((state.action.isAttack() || state.action == ActionId.HEAVY_CHARGE) && !weaponUnchanged(player, state)) {
            state.clearAction();
            state.guarding = false;
        }
        if (state.guarding && !Weapons.controlsGuard(player)) state.guarding = false;
        player.setYBodyRot(player.getYRot());

        // 蓄力：最多 30 tick 自动释放
        if (state.action == ActionId.HEAVY_CHARGE) {
            state.chargeTicks++;
            WeaponClass weapon = state.actionWeapon;
            if (weapon == WeaponClass.NONE || CombatRules.chargeExpired(config, state, weapon)) {
                releaseHeavy(player, state, config, tick);
            }
        }

        // 喝药：第 consumeTick tick 生效一次；被打断未到生效时刻不消耗
        if (state.action == ActionId.DRINK && state.spec != null && !state.drinkConsumed
                && state.tickInAction(tick) >= state.spec.consumeTick()) {
            state.drinkConsumed = true;
            if (state.potionCharges > 0) {
                state.potionCharges--;
                player.heal(config.drink.heal + Talismans.healBonus(player));
                CombatEffects.drink(player);
            }
        }

        // 翻滚位移：走原版地形碰撞，不瞬移穿墙
        if (state.action == ActionId.ROLL && !com.dsh.eldencombat.compat.CombatRollCompat.present()) {
            int inAction = state.tickInAction(tick);
            if (inAction >= config.roll.moveStart && inAction < config.roll.moveEnd) {
                double previousY = player.getY();
                player.move(MoverType.SELF, new Vec3(state.rollDirX * config.roll.speed,
                        0, state.rollDirZ * config.roll.speed));
                // Acknowledge the collision-resolved position so vanilla movement packets cannot undo it.
                // Relative Y preserves ordinary jumping/falling unless the move actually stepped up.
                var relative = java.util.EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT);
                boolean unchangedY = Math.abs(player.getY()-previousY) < 1.0e-8;
                if (unchangedY) relative.add(RelativeMovement.Y);
                // This API takes absolute targets; it derives relative packet deltas itself.
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), relative);
            }
        }

        // 命中窗口结算（服务端骨骼采样 + 上一/当前 tick 判定体）
        if (state.action.isAttack() && state.spec != null) {
            WeaponClass weapon = state.actionWeapon;
            if (weapon != WeaponClass.NONE) {
                HitResolver.resolvePlayerAttack(player, state, state.spec, weapon);
            }
        }

        CombatEffects.playerTick(player,state,tick);
        state.finishIfDone(tick);
        state.tickStamina(config, tick, state.shieldActive(tick), Talismans.staminaRegenMultiplier(player));
        validateLockOn(player, state, config);

        sync(player, state);
    }

    /** 锁定目标：移除、死亡、切换维度或超出距离立即解除。 */
    private void validateLockOn(ServerPlayer player, CombatState state, CombatConfig config) {
        if (state.targetId == CombatState.NO_TARGET) {
            return;
        }
        Entity target = player.level().getEntity(state.targetId);
        boolean valid = target instanceof LivingEntity living
                && living.isAlive()
                && living.level() == player.level()
                && player.distanceTo(living) <= config.lockOn.breakRange;
        if (!valid) {
            state.targetId = CombatState.NO_TARGET;
            PacketDistributor.sendToPlayer(player, new LockOnPayload(CombatState.NO_TARGET, false));
        }
    }

    private void sync(ServerPlayer player, CombatState state) {
        WeaponClass weapon = state.actionWeapon != WeaponClass.NONE ? state.actionWeapon : Weapons.classify(player);
        int hitStart = -1, hitEnd = -1;
        if (state.action.isAttack() && state.spec != null && !state.spec.windows().isEmpty()) {
            hitStart = state.spec.windows().getFirst().start();
            hitEnd = state.spec.windows().getFirst().endExclusive();
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new CombatStatePayload(
                player.getId(),
                state.action.name(),
                state.actionSequence,
                state.actionStartTick,
                state.stamina,
                EldenCombat.config().stamina.max,
                state.potionCharges,
                state.targetId,
                state.combatMode, state.guarding, weapon.name(),
                state.actionItem.isEmpty() ? PlayerAppearance.slim(player) : state.actionSlim,
                state.actionItem.isEmpty() ? player.getMainArm() == HumanoidArm.LEFT : state.actionLeftHanded,
                state.spec == null ? 0 : state.spec.totalTicks(), hitStart, hitEnd,
                weapon == WeaponClass.NONE ? 20 : EldenCombat.config().weapon(weapon).chargeFullTicks, state.guardImpactTick,
                (float)Math.toDegrees(Math.atan2(-state.rollDirX, state.rollDirZ))));
    }

    private static void bindWeapon(ServerPlayer player, CombatState state) {
        state.actionWeapon = Weapons.classify(player);
        state.actionItem = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
        state.actionLeftHanded = player.getMainArm() == HumanoidArm.LEFT;
        state.actionSlim = PlayerAppearance.slim(player);
    }

    private static boolean weaponUnchanged(ServerPlayer player, CombatState state) {
        return state.actionWeapon == Weapons.classify(player)
            && state.actionItem.equals(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString())
            && state.actionLeftHanded == (player.getMainArm() == HumanoidArm.LEFT);
    }

    // ------------------------------------------------------------------
    // 意图处理
    // ------------------------------------------------------------------

    private void apply(ServerPlayer player, CombatIntentPayload payload) {
        CombatConfig config = EldenCombat.config();
        CombatState state = player.getData(ModAttachments.COMBAT_STATE);
        state.ensureInit(config);
        long tick = player.level().getGameTime();

        if (payload.intent() < 0 || payload.intent() >= IntentType.values().length
                || !Float.isFinite(payload.dirX()) || !Float.isFinite(payload.dirZ())) return;

        // 序号校验：重放或非法请求不消耗任何资源
        int last = lastSequence.getOrDefault(player.getUUID(), Integer.MIN_VALUE);
        if (!CombatRules.sequenceIsNewer(payload.sequence(), last)) {
            return;
        }
        lastSequence.put(player.getUUID(), payload.sequence());

        if (!player.isAlive()) {
            return;
        }

        IntentType intent = payload.intentType();
        // Reject the legacy packet before it can cancel a cast or spend resources.
        if (intent == IntentType.ROLL && com.dsh.eldencombat.compat.CombatRollCompat.present()) return;
        if ((intent == IntentType.LIGHT || intent == IntentType.HEAVY_START || intent == IntentType.HEAVY_RELEASE)
                && !Weapons.controlsMelee(player.getMainHandItem())) return;
        if (com.dsh.eldencombat.compat.CombatRollCompat.rolling(player)
                && intent != IntentType.COMBAT_TOGGLE && intent != IntentType.CANCEL && intent != IntentType.LOCK_ON) return;
        if (intent == IntentType.COMBAT_TOGGLE) {
            state.combatMode = !state.combatMode;
            state.clearAction();
            state.guarding = false;
            state.chargeTicks = 0;
            if (!state.combatMode) {
                state.targetId = CombatState.NO_TARGET;
            }
            sync(player, state);
            return;
        }
        if (intent == IntentType.CANCEL) {
            var skillCast = com.dsh.eldencombat.server.skills.SkillDriver.cast(player);
            var skillSpec = com.dsh.eldencombat.server.skills.SkillDriver.spec(skillCast.skillId());
            if (skillCast.isCasting() && (skillSpec == null
                    || !skillCast.recoveryCancelable(tick, skillSpec.recoveryTicks()))) return;
            state.clearAction();
            state.guarding = false;
            state.chargeTicks = 0;
            // 主动取消同时结束正在进行的战技施放（清临时状态，不退已确认的 FP）
            com.dsh.eldencombat.server.skills.SkillDriver.cancel(player, tick, "玩家取消");
            sync(player, state);
            return;
        }
        if (!state.combatMode) {
            if (intent == IntentType.SKILL) {
                // 战斗模式关闭时按战技键：明确告诉玩家原因，而不是"没反应"
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("先按 R 切换战斗模式才能释放战技"), true);
            }
            return;
        }

        // 施放战技期间不接受别的动作输入：只有"再按一次战技（追击）"和"锁定"有效。
        // 取消与战斗模式切换已在上面的分支里处理。
        com.dsh.eldencombat.server.skills.SkillCastState cast =
                com.dsh.eldencombat.server.skills.SkillDriver.cast(player);
        if (cast.isCasting()) {
            var skillSpec = com.dsh.eldencombat.server.skills.SkillDriver.spec(cast.skillId());
            if ((intent == IntentType.ROLL || intent == IntentType.LIGHT) && skillSpec != null
                    && cast.recoveryCancelable(tick, skillSpec.recoveryTicks())) {
                com.dsh.eldencombat.server.skills.SkillDriver.cancel(player, tick, "收招尾段衔接");
            } else {
                switch (intent) {
                    case SKILL -> com.dsh.eldencombat.server.skills.SkillCasting.request(player, tick);
                    case LOCK_ON -> handleLockOn(player, state, payload.targetId(), config);
                    default -> {
                    }
                }
                sync(player, state);
                return;
            }
        }

        switch (intent) {
            case GUARD_START -> {
                if (Weapons.controlsGuard(player) && CombatRules.canAccept(state, ActionId.NONE, tick)) {
                    state.guarding = true;
                }
            }
            case GUARD_STOP -> state.guarding = false;
            case LOCK_ON -> handleLockOn(player, state, payload.targetId(), config);
            case LIGHT -> startAttack(player, state, config, tick, ActionId.LIGHT_ATTACK);
            case HEAVY_START -> startCharge(player, state, config, tick);
            case HEAVY_RELEASE -> releaseHeavy(player, state, config, tick);
            case ROLL -> startRoll(player, state, config, tick, payload.dirX(), payload.dirZ());
            case PARRY -> startSimple(player, state, config, tick, ActionId.PARRY, true);
            case DRINK -> startDrink(player, state, config, tick);
            case SKILL -> {
                // 战技起手要求处于待机（不在挥砍/翻滚/喝药中）
                if (CombatRules.canAccept(state, ActionId.GUARD, tick)) {
                    com.dsh.eldencombat.server.skills.SkillCasting.request(player, tick);
                }
            }
            default -> {
            }
        }
        sync(player, state);
    }

    private void startAttack(ServerPlayer player, CombatState state, CombatConfig config, long tick, ActionId action) {
        WeaponClass weapon = Weapons.classify(player);
        if (weapon == WeaponClass.NONE) {
            return;
        }
        if (!CombatRules.canAccept(state, action, tick)) {
            return;
        }
        ActionSpec spec = WeaponAttributes.applyProfile(CombatRules.specFor(config, weapon, action),
                WeaponAttributes.profile(player));
        if (!CombatRules.staminaOk(state, spec)) {
            // 精力不足：不播放动作、不发伤害
            return;
        }
        state.guarding = false;
        state.acceptAction(spec, tick);
        bindWeapon(player, state);
    }

    private void startCharge(ServerPlayer player, CombatState state, CombatConfig config, long tick) {
        WeaponClass weapon = Weapons.classify(player);
        if (weapon == WeaponClass.NONE) {
            return;
        }
        if (!CombatRules.canAccept(state, ActionId.HEAVY_CHARGE, tick)) {
            return;
        }
        int maxCharge = Math.max(1, config.weapon(weapon).chargeMaxTicks);
        state.acceptAction(ActionSpec.simple(ActionId.HEAVY_CHARGE, maxCharge + 8, 0f), tick);
        bindWeapon(player, state);
        state.guarding = false;
    }

    private void releaseHeavy(ServerPlayer player, CombatState state, CombatConfig config, long tick) {
        if (state.action != ActionId.HEAVY_CHARGE) {
            return;
        }
        WeaponClass weapon = state.actionWeapon;
        if (!weaponUnchanged(player, state)) {
            state.clearAction();
            return;
        }
        if (weapon == WeaponClass.NONE) {
            state.clearAction();
            return;
        }
        ActionId id = CombatRules.chargedOrNormal(config, state, weapon);
        int chargeTicks = state.chargeTicks;
        ActionSpec spec = WeaponAttributes.applyProfile(CombatRules.specFor(config, weapon, id),
                WeaponAttributes.profile(player));
        if (!CombatRules.staminaOk(state, spec)) {
            state.clearAction();
            return;
        }
        state.acceptAction(spec, tick);
        bindWeapon(player, state);
        // 蓄力计数在 acceptAction 里被清零，这里只用于日志
        EldenCombat.LOGGER.debug("[EldenCombat] 释放重击 {} 蓄力 {} tick", id, chargeTicks);
    }

    private void startRoll(ServerPlayer player, CombatState state, CombatConfig config, long tick, float dirX, float dirZ) {
        if (!CombatRules.canAccept(state, ActionId.ROLL, tick)) {
            return;
        }
        ActionSpec spec = config.rollSpec();
        if (!CombatRules.staminaOk(state, spec)) {
            return;
        }
        double[] direction = Collision.normalize2D(dirX, dirZ);
        if (direction[0] == 0 && direction[1] == 0) {
            // 无方向时后撤
            direction[0] = Math.sin(Math.toRadians(player.getYRot()));
            direction[1] = -Math.cos(Math.toRadians(player.getYRot()));
        }
        state.rollDirX = direction[0];
        state.rollDirZ = direction[1];
        state.guarding = false;
        state.acceptAction(spec, tick);
    }

    private void startSimple(ServerPlayer player, CombatState state, CombatConfig config, long tick, ActionId action,
                             boolean requiresShield) {
        if (requiresShield && !Weapons.hasShield(player)) {
            return;
        }
        if (!CombatRules.canAccept(state, action, tick)) {
            return;
        }
        ActionSpec spec = CombatRules.specFor(config, WeaponClass.NONE, action);
        if (!CombatRules.staminaOk(state, spec)) {
            return;
        }
        state.guarding = false;
        state.acceptAction(spec, tick);
    }

    private void startDrink(ServerPlayer player, CombatState state, CombatConfig config, long tick) {
        if (state.potionCharges <= 0) {
            return;
        }
        startSimple(player, state, config, tick, ActionId.DRINK, false);
    }

    private void handleLockOn(ServerPlayer player, CombatState state, int targetId, CombatConfig config) {
        if (targetId < 0) {
            state.targetId = CombatState.NO_TARGET;
            PacketDistributor.sendToPlayer(player, new LockOnPayload(CombatState.NO_TARGET, false));
            return;
        }
        Entity entity = player.level().getEntity(targetId);
        boolean accepted = entity instanceof LivingEntity living
                && living.isAlive()
                && living != player
                && !(living instanceof ServerPlayer)
                && player.distanceTo(living) <= config.lockOn.range
                && player.hasLineOfSight(living);
        state.targetId = accepted ? targetId : CombatState.NO_TARGET;
        PacketDistributor.sendToPlayer(player, new LockOnPayload(state.targetId, accepted));
    }

    // ------------------------------------------------------------------
    // 生命周期清理
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) com.dsh.eldencombat.compat.CombatRollCompat.clear(player);
        lastSequence.remove(event.getEntity().getUUID());
        // 武器属性修饰符是瞬时的：退出时清掉记录，避免下次登录"以为已经应用过"而漏掉
        WeaponAttributes.forget(event.getEntity().getUUID());
        CombatState state = event.getEntity().getData(ModAttachments.COMBAT_STATE);
        state.reset(EldenCombat.config());
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player) com.dsh.eldencombat.compat.CombatRollCompat.clear(player);
        // 死亡或切换维度后重建状态：不残留无敌、武器显示与命中窗口
        CombatState state = event.getEntity().getData(ModAttachments.COMBAT_STATE);
        state.reset(EldenCombat.config());
        // 重生会重建实体，瞬时修饰符随之消失 —— 必须忘记"已应用过"，下一 tick 才会重新应用
        WeaponAttributes.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        lastSequence.clear();
        com.dsh.eldencombat.compat.CombatRollCompat.clearAll();
        WeaponAttributes.forgetAll();
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) com.dsh.eldencombat.compat.CombatRollCompat.clear(player);
    }

    /** 受击打断当前动作（环境伤害不打断，翻滚无敌只过滤本系统战斗伤害）。 */
    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSource().getEntity() == null && event.getSource().getDirectEntity() == null) {
            return;
        }
        CombatState state = player.getData(ModAttachments.COMBAT_STATE);
        state.ensureInit(EldenCombat.config());
        if (!state.combatMode) return;
        state.acceptAction(ActionSpec.simple(ActionId.STAGGER, 16, 0), player.level().getGameTime());
        state.guarding = false;
        state.parryChain = 0;
    }
}
