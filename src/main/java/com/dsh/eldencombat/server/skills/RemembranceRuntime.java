package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.geom.Collision;
import com.dsh.eldencombat.entity.skill.GoldenWaveEntity;
import com.dsh.eldencombat.entity.skill.SkillEffectEntity;
import com.dsh.eldencombat.effects.SkillEffectMesh.Kind;
import com.dsh.eldencombat.registry.ModEntities;
import com.dsh.eldencombat.server.HitResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Distinct skill timelines share bounded movement and a single server hit path. */
public final class RemembranceRuntime implements SkillDriver.Runtime {
    private final Map<UUID, State> states = new HashMap<>();
    /** §4 安全网：施放结束时仍悬空的跃起（收招期间掉下悬崖等），继续被轮询到落地为止 */
    private final Map<UUID, SkillLanding> orphanLandings = new HashMap<>();
    private final Map<UUID, double[]> orphanOrigins = new HashMap<>();

    /** 安全网结算次数与最后一次结论（诊断 / smoke 断言用） */
    private static volatile int safetyResolutions;
    private static volatile String lastSafetyOutcome = "";

    public static int safetyResolutions() { return safetyResolutions; }

    public static String lastSafetyOutcome() { return lastSafetyOutcome; }

    private static final class State {
        final SkillMotion motion;
        Vec3 effectOrigin;
        int lastBurst = -1;
        int inputStage;
        boolean impacted;
        final java.util.List<net.minecraft.world.entity.Entity> effects = new java.util.ArrayList<>();
        /** §4 统一落地处理器：本次跃起从起跳到结算（含异常分支）都由它判定 */
        SkillLanding landing;
        State(ServerPlayer player, RemembranceSkillSpec spec) { motion = new SkillMotion(player, spec); }
    }

    @Override
    public void onBegin(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        states.put(player.getUUID(), new State(player, spec));
        // 重新起手时清掉上一次的安全网残留（同一玩家不需要两条落地判定）
        orphanLandings.remove(player.getUUID());
        orphanOrigins.remove(player.getUUID());
    }

    @Override
    public void onActive(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        State state = states.get(player.getUUID());
        if (state == null) return;
        state.effectOrigin = player.position();
        state.inputStage = cast.stageIndex();
        state.lastBurst = -1;
        state.impacted = false;
        switch (spec.id()) {
            case "gold_breaker" -> sound(player, SoundEvents.PLAYER_ATTACK_STRONG, .75f);
            case "takers_flames" -> sound(player, SoundEvents.FIRECHARGE_USE, .8f);
            case "bloodboon_ritual" -> sound(player, SoundEvents.BEACON_POWER_SELECT, .65f);
            case "starcaller_cry" -> sound(player, SoundEvents.ENDERMAN_TELEPORT, .6f);
            case "cursed_blood_slice" -> {
                visual(player, player.position(), Kind.CURSED_SLASH, spec.effectRadius(), 8, cast.stageIndex(), true);
                sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, .8f);
            }
            default -> {}
        }
        if (spec.id().equals("wave_of_gold") || spec.id().equals("takers_flames")) {
            boolean flame = spec.id().equals("takers_flames");
            GoldenWaveEntity wave = ModEntities.GOLDEN_WAVE.get().create(player.level());
            if (wave != null) {
                wave.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
                wave.configure(player, state.motion.direction(), flame ? .65 : .9, spec.effectRange(), spec.effectLifespanTicks(),
                        spec.effectRadius(), flame ? spec.effectRadius() : spec.effectRadius() * 1.4,
                        spec.damage(), spec.poiseDamage(), spec.healCap());
                wave.setFlame(flame);
                player.level().addFreshEntity(wave);
                state.effects.add(wave);
            }
        }
        if (spec.id().equals("bloodboon_ritual")) {
            area(player, cast, spec, player.position(), spec.effectRadius(), 1, 0, false, tick);
            visual(player, player.position(), Kind.BLOOD_RITUAL, spec.effectRadius(), 16, cast.stageIndex() - 1, false);
        }
        if (spec.id().equals("starcaller_cry") && cast.stageIndex() == 1) {
            area(player, cast, spec, player.position(), spec.effectRadius(), 1, 0, false, tick);
            visual(player, player.position(), Kind.GRAVITY_PULL, spec.effectRadius(), 12, 0, false);
        }
        activeTick(player, cast, spec, state, 0, tick);
    }

    @Override
    public void onTick(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        State state = states.get(player.getUUID());
        if (state == null) { SkillDriver.cancel(player, tick, "缺少运行状态"); return; }
        if (!state.motion.tick(player)) { SkillDriver.cancel(player, tick, "技能位移超出预算"); return; }
        // §4：跃起技能的落地统一在这里收口（正常落地 / 低天花板 / 落水 / 虚空 / 超时）
        landingTick(player, cast, spec, state, tick);
        int elapsed = cast.stageTicks(tick);
        if (cast.stage() == SkillCastState.Stage.WINDUP) {
            int launchTick = Math.max(0, spec.windupForStage(cast.stageIndex()) - 3);
            if (elapsed == launchTick) {
                switch (spec.id()) {
                    case "gold_breaker" -> state.motion.launch(player, .32, .5, false);
                    case "destined_death" -> state.motion.launch(player, .2, .52, false);
                    case "waterfowl_dance" -> state.motion.launch(player, 0, .52, false);
                    case "cursed_blood_slice" -> state.motion.launch(player, .3, .25, true);
                    case "starcaller_cry" -> { if (cast.stageIndex() == 1) state.motion.launch(player, 0, .42, false); }
                    default -> {}
                }
                if (spec.allowsSkillJump() && state.motion.launched) {
                    // 每次起跳都重新武装落地判定：多段技能（水鸟/命定）每段独立兜底
                    state.landing = new SkillLanding(player.getY());
                }
            }
        } else if (cast.stage() == SkillCastState.Stage.ACTIVE) activeTick(player, cast, spec, state, elapsed, tick);
    }

    /**
     * §4 落地处理器：跃起期间每 tick 只问一次世界，六个分支表见 {@link SkillLanding}。
     *
     * <p>三条硬约束：
     * <ol>
     *   <li>落地伤害只结算一次（{@link SkillLanding} 一旦结算就锁死结论，且 {@code state.impacted} 二次保护）；</li>
     *   <li>"不穿墙"与"落点安全"是两件事：这里补的是后者；</li>
     *   <li>每个分支都会恢复重力（{@code SkillMotion.stop/place} 都会把速度接管掉）。</li>
     * </ol>
     */
    private void landingTick(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec,
                             State state, long tick) {
        if (state.landing == null || state.landing.resolved()) return;
        // 起跳是在蓄势末尾给的冲量，但"离开地面"发生在下一个 tick 的移动里。
        // 所以蓄势阶段不判定，避免把"还站在地上"误判成落地（那会让黄金碎击提前放伤害）。
        if (cast.stage() == SkillCastState.Stage.WINDUP) return;
        SkillLanding.Outcome outcome = state.landing.advance(probe(player, state));
        switch (outcome) {
            case LOW_CEILING -> state.motion.cutRise(player);
            case LANDED -> landingImpact(player, cast, spec, state, tick);
            case LIQUID -> {
                state.impacted = true;
                state.motion.stop(player);
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, .7f, 1.0f);
                EldenCombat.LOGGER.debug("[skills] {} 落水：取消落地伤害", spec.id());
            }
            case VOID -> returnToStart(player, cast, spec, state);
            case TIMEOUT -> snapToGround(player, cast, spec, state, tick);
            default -> { }
        }
    }

    private SkillLanding.Probe probe(ServerPlayer player, State state) {
        return new SkillLanding.Probe() {
            @Override public double y() { return player.getY(); }
            @Override public boolean onGround() { return player.onGround(); }
            @Override public boolean inLiquid() { return LandingSupport.inLiquid(player); }
            @Override public double deltaY() { return player.getDeltaMovement().y; }
            @Override public double worldBottom() { return player.level().getMinBuildHeight(); }
            @Override public boolean ceilingBlocked() { return state.motion.ceilingBlocked(player); }
        };
    }

    /** 正常落地：只对"落地才有伤害"的战技结算，且同一段只结算一次。 */
    private void landingImpact(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec,
                               State state, long tick) {
        state.motion.stop(player);
        boolean landingSkill = spec.id().equals("gold_breaker")
                || (spec.id().equals("starcaller_cry") && cast.stageIndex() > 1);
        if (!landingSkill || state.impacted) return;
        state.impacted = true;
        if (spec.id().equals("gold_breaker")) {
            area(player, cast, spec, player.position(), spec.effectRadius(), 1, 0, false, tick);
            visual(player, player.position(), Kind.GOLD_IMPACT, spec.effectRadius(), 12, 0, false);
            sound(player, SoundEvents.ANVIL_LAND, 1.2f);
        } else {
            area(player, cast, spec, player.position(), Math.min(3.5, spec.effectRadius()), 2.5f, 0, false, tick);
            visual(player, player.position(), Kind.GRAVITY_IMPACT, Math.min(3.5, spec.effectRadius()), 12, 0, false);
            sound(player, SoundEvents.ANVIL_LAND, .65f);
        }
    }

    /** 掉出世界：取消位移，送回起跳点，退还部分 FP（不结算落地伤害）。 */
    private void returnToStart(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, State state) {
        state.impacted = true;
        state.motion.place(player, state.motion.launchX, state.motion.launchY, state.motion.launchZ);
        float refund = spec.costForStage(Math.max(0, cast.stageIndex() - 1)) * SkillLanding.VOID_REFUND_RATIO;
        if (refund > 0) {
            SkillServerEvents.fp(player).refund(refund);
            SkillServerEvents.sync(player);
        }
        player.displayClientMessage(Component.literal("§c[战技] 落点不安全：已送回起跳点"), true);
        EldenCombat.LOGGER.info("[skills] {} 掉出世界：已送回起跳点 ({} / {} / {})，退还 FP {:.1f}",
                spec.id(), state.motion.launchX, state.motion.launchY, state.motion.launchZ, refund);
    }

    /** 超时未落地：吸附到下方最近的实心方块后强制结算。 */
    private void snapToGround(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec,
                             State state, long tick) {
        double ground = LandingSupport.groundBelow(player.serverLevel(), player.getX(), player.getY(),
                player.getZ(), LandingSupport.MAX_SCAN);
        if (Double.isNaN(ground)) {
            // 下方 64 格没有可落地的面：当虚空处理，别把玩家留在半空
            state.landing = null;
            returnToStart(player, cast, spec, state);
            return;
        }
        state.motion.place(player, player.getX(), ground, player.getZ());
        player.displayClientMessage(Component.literal("§e[战技] 落地超时：已强制结算"), true);
        EldenCombat.LOGGER.info("[skills] {} 落地超时：吸附到 Y={:.2f}", spec.id(), ground);
        landingImpact(player, cast, spec, state, tick);
    }

    private void activeTick(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec,
                            State state, int elapsed, long tick) {
        int duration = spec.activeForStage(cast.stageIndex());
        switch (spec.id()) {
            case "gold_breaker" -> {
                // 落地判定已统一到 landingTick（§4）：这里不再用"elapsed + onGround"这种
                // 只覆盖正常落地的写法——那样落水/超时/虚空都会漏掉结算。
            }
            case "destined_death", "waterfowl_dance" -> {
                if (elapsed >= duration) return;
                int burst = Math.min(2, elapsed * 3 / duration);
                int start = burst * duration / 3;
                if (state.lastBurst != burst) {
                    state.lastBurst = burst;
                    if (spec.id().equals("waterfowl_dance")) state.motion.launch(player, .28, .32, true);
                    visual(player, player.position(), spec.id().equals("destined_death") ? Kind.DEATH_SLASH : Kind.WATERFOWL,
                            spec.effectRadius(), 8, burst, true);
                    sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, spec.id().equals("destined_death") ? .65f : 1.35f);
                }
                if (elapsed - start < 6) melee(player, cast, spec, burst, tick);
            }
            case "cursed_blood_slice" -> {
                if (elapsed < Math.min(4, duration)) melee(player, cast, spec, 0, tick);
                if (elapsed == 4) {
                    Vec3 origin = state.effectOrigin.add(state.motion.direction().scale(1.5));
                    area(player, cast, spec, origin, Math.min(2, spec.effectRadius()), .5f, 1, false, tick);
                    visual(player, origin, Kind.BLOOD_FLAME, Math.min(2, spec.effectRadius()), 12, cast.stageIndex(), false);
                    sound(player, SoundEvents.FIRECHARGE_USE, .7f);
                }
            }
            case "starcaller_cry" -> {
                if (cast.stageIndex() == 1 && elapsed < duration) pull(player, spec);
                // 第二段的落地冲击同样交给 landingTick 统一结算（§4）
            }
            default -> {}
        }
    }

    /**
     * 近战段判定（纯原版，自研扫掠）。
     *
     * <p>原先借 Epic Fight 的 {@code AttackAnimation.getPhaseByTime(...).getCollidingEntities(...)} 拿判定体；
     * 去掉 EF 后改成"手中兵器胶囊"：从胸口沿视线方向伸出 {@code effectRange} 格，
     * 半径跟随战技的 {@code effectRadius}，每 tick 用 {@link Collision#capsuleHitsBox} 判交。
     * 判定逻辑与表现解耦：客户端播什么动作都不影响命中范围。
     */
    private void melee(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, int channel, long tick) {
        Vec3 look = player.getLookAngle();
        double reach = Math.max(1.5, spec.effectRange());
        Vector3d hand = new Vector3d(player.getX(), player.getY() + 1.2, player.getZ());
        Vector3d tip = new Vector3d(hand.x + look.x * reach, hand.y + look.y * reach, hand.z + look.z * reach);
        double radius = Math.max(0.55, spec.effectRadius() * 0.35);
        AABB search = new AABB(hand.x, hand.y, hand.z, tip.x, tip.y, tip.z).inflate(radius + 1.0);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, search)) {
            if (target.distanceTo(player) > reach + 1.5) continue;
            if (!Collision.capsuleHitsBox(hand, tip, HitResolver.toBox(target.getBoundingBox()), radius)) continue;
            hit(player, cast, spec, target, channel, spec.damage(), spec.poiseDamage(),
                    spec.id().equals("destined_death"), tick, player.getEyePosition());
        }
    }

    private void area(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, Vec3 origin,
                      double radius, float multiplier, int channel, boolean death, long tick) {
        AABB bounds = new AABB(origin.x-radius, origin.y-1, origin.z-radius,
                origin.x+radius, origin.y+2, origin.z+radius);
        if (player.level().clip(new net.minecraft.world.level.ClipContext(player.getEyePosition(), origin.add(0, .6, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                player)).getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, bounds)) {
            AABB box = target.getBoundingBox();
            double dx = Math.max(box.minX-origin.x, Math.max(0, origin.x-box.maxX));
            double dz = Math.max(box.minZ-origin.z, Math.max(0, origin.z-box.maxZ));
            if (dx*dx + dz*dz <= radius*radius) hit(player, cast, spec, target, channel,
                    spec.damage()*multiplier, spec.poiseDamage(), death, tick, origin.add(0, .6, 0));
        }
    }

    private void hit(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, LivingEntity target,
                     int channel, float damage, float impact, boolean death, long tick, Vec3 origin) {
        if (!SkillHitEffects.validTarget(player, target) || !SkillHitEffects.visible(player, origin, target)
                || !cast.markHit(target.getId(), channel)) return;
        if (SkillHitEffects.hurt(player, target, damage, impact, false) > 0) {
            cast.markStageResolved();
            cast.markResolvedChannel(channel);
            if (death) DestinedDeathEffects.apply(player, target, spec, tick);
        }
    }

    private void pull(ServerPlayer player, RemembranceSkillSpec spec) {
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(spec.effectRadius(), 2, spec.effectRadius()))) {
            Vec3 delta = player.position().subtract(target.position()).multiply(1, 0, 1);
            double distance = delta.length();
            if (distance < 1.2 || distance > spec.effectRadius() || !SkillHitEffects.validTarget(player, target)
                    || !SkillHitEffects.visible(player, player.getEyePosition(), target)) continue;
            target.setDeltaMovement(delta.normalize().scale(Math.min(.25, distance - 1.2))
                    .add(0, target.getDeltaMovement().y, 0));
            target.hurtMarked = true;
        }
    }

    private void visual(ServerPlayer player, Vec3 origin, Kind kind, double radius, int duration, int phase, boolean follow) {
        SkillEffectEntity effect = SkillEffectEntity.spawn(player, origin, kind, (float) radius, duration, phase, follow);
        State state = states.get(player.getUUID());
        if (state != null && effect != null) state.effects.add(effect);
    }

    private void sound(ServerPlayer player, SoundEvent event, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), event, SoundSource.PLAYERS, .7f, pitch);
    }

    @Override
    public void onRecovery(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        State state = states.get(player.getUUID());
        if (state != null) state.motion.stop(player);
    }

    @Override
    public void onFinish(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        State state = states.remove(player.getUUID());
        if (state != null) {
            state.motion.stop(player);
            parkOrphan(player, state);
        }
    }

    /**
     * §4 安全网登记：施放已经结束但人还在空中（例如收招期间被击退/走下悬崖）时，
     * 把落地判定交给 {@link #onSafetyTick}。安全网只负责"别卡在空中 / 别掉进虚空"，
     * 不结算任何伤害，所以登记它不产生收益——任何终止路径（含参数缺失的中止）都该登记。
     */
    private void parkOrphan(ServerPlayer player, State state) {
        if (state.landing != null && !state.landing.resolved() && !player.onGround()) {
            orphanLandings.put(player.getUUID(), state.landing);
            orphanOrigins.put(player.getUUID(), new double[]{state.motion.launchX, state.motion.launchY, state.motion.launchZ});
        }
    }

    @Override
    public void onCancel(ServerPlayer player, SkillCastState cast, RemembranceSkillSpec spec, long tick) {
        State state = states.get(player.getUUID());
        if (state != null) state.effects.forEach(net.minecraft.world.entity.Entity::discard);
        onFinish(player, cast, spec, tick);
    }

    /**
     * 参数不可用时的收尾：**没有 spec**，所以只做"释放本次施放自己生成的东西"——
     * 丢弃效果实体、停掉位移（把速度和重力交还给原版），落地安全网照常登记。
     *
     * <p>不许读 spec、不许结算伤害：拿不到参数就没有可信的伤害窗口，
     * 这里多做一件事都可能变成"参数丢失刷伤害"。
     */
    @Override
    public void onAbort(ServerPlayer player, SkillCastState cast, long tick) {
        State state = states.remove(player.getUUID());
        if (state == null) return;
        state.effects.forEach(net.minecraft.world.entity.Entity::discard);
        state.motion.stop(player);
        parkOrphan(player, state);
    }

    /**
     * §4 落地安全网。施放已经结束，所以这里**只做三件事**：
     * 落水/正常落地什么都不用做（重力已经正常）、掉出世界送回起跳点、超时吸附到地面。
     */
    @Override
    public void onSafetyTick(ServerPlayer player, long tick) {
        UUID id = player.getUUID();
        SkillLanding landing = orphanLandings.get(id);
        if (landing == null) return;
        if (!player.isAlive() || player.onGround()) {
            settleOrphan(id, "LANDED");
            return;
        }
        SkillLanding.Outcome outcome = landing.advance(new SkillLanding.Probe() {
            @Override public double y() { return player.getY(); }
            @Override public boolean onGround() { return player.onGround(); }
            @Override public boolean inLiquid() { return LandingSupport.inLiquid(player); }
            @Override public double deltaY() { return player.getDeltaMovement().y; }
            @Override public double worldBottom() { return player.level().getMinBuildHeight(); }
            @Override public boolean ceilingBlocked() { return false; }
        });
        if (!SkillLanding.isTerminal(outcome)) return;
        double[] origin = orphanOrigins.get(id);
        switch (outcome) {
            case VOID -> {
                if (origin != null) {
                    player.teleportTo(origin[0], origin[1], origin[2]);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.hurtMarked = true;
                    player.displayClientMessage(Component.literal("§c[战技] 落点不安全：已送回起跳点"), true);
                }
            }
            case TIMEOUT -> {
                double ground = LandingSupport.groundBelow(player.serverLevel(), player.getX(), player.getY(),
                        player.getZ(), LandingSupport.MAX_SCAN);
                if (Double.isNaN(ground) && origin != null) {
                    player.teleportTo(origin[0], origin[1], origin[2]);
                } else if (!Double.isNaN(ground)) {
                    player.teleportTo(player.getX(), ground, player.getZ());
                }
                player.setDeltaMovement(Vec3.ZERO);
                player.hurtMarked = true;
            }
            default -> { }
        }
        settleOrphan(id, outcome.name());
    }

    private void settleOrphan(UUID id, String outcome) {
        orphanLandings.remove(id);
        orphanOrigins.remove(id);
        safetyResolutions++;
        lastSafetyOutcome = outcome;
    }

    @Override
    public void onDetach(ServerPlayer player) {
        states.remove(player.getUUID());
        orphanLandings.remove(player.getUUID());
        orphanOrigins.remove(player.getUUID());
    }
}
