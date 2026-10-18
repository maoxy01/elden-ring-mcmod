package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.client.effects.VfxDiagnosticLayers;
import com.dsh.eldencombat.effects.VfxCameraMath;
import com.dsh.eldencombat.entity.skill.GoldenWaveEntity;
import com.dsh.eldencombat.entity.skill.SkillEffectEntity;
import com.dsh.eldencombat.server.skills.RemembranceRuntime;
import com.dsh.eldencombat.server.skills.SkillCasting;
import com.dsh.eldencombat.server.skills.SkillDriver;
import com.dsh.eldencombat.server.skills.SkillServerEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * **动作语义夹具**（仅 {@code eldencombat.vfxSmoke + eldencombat.vfxSemantics} 时工作）。
 *
 * <p>为什么需要它：受控像素夹具（{@code VfxFixedHarness}）证明的是"某个实例在这一帧提交了作者美术、
 * 像素归属正确"。它<strong>证明不了</strong>下面这些玩家一眼就能看见的事：
 *
 * <ul>
 *   <li>三次挥击是不是**每一次**都画出来了（旧夹具只对"第一个提交的实例"取一次样，
 *       第 2、3 段的实例往往只落在诊断层里，既没被证真也没被证伪）；</li>
 *   <li>施法者是不是真的**挥了手**（服务器从不播原版挥臂动画时，第一人称下手臂纹丝不动）；</li>
 *   <li>突进技能是"往前冲"还是"原地蹦"（水平位移）；</li>
 *   <li>落地特效是不是**落地之后**才出现（起跳冲量发出后服务端的 onGround 还会骗人几 tick）。</li>
 * </ul>
 *
 * <p>所以本夹具不拍差分图、不做像素归因，只做四件确定性的事（全部落在日志与门禁上）：
 * <ol>
 *   <li>整段保持 {@code NORMAL} 层（= 玩家真实观感），逐帧登记每个效果实例身份
 *       （{@code wave/<id>} / {@code effect/<id>/<phase>}）是否有**真实美术提交**；
 *       门禁：本段出现过的每个实例都必须至少有一帧真的提交过，缺一个就失败并点名；</li>
 *   <li>逐 tick 统计**客户端施法者**的挥击起手次数（{@code swingTime} 重置），
 *       门禁：需要挥击的战技，次数不能少于实际挥出的段数；</li>
 *   <li>读服务端 {@link RemembranceRuntime.JumpTrace}：必须"真的离地、有最高点、落地耗时合理"，
 *       突进战技还必须有**最小水平位移**；</li>
 *   <li>落地时序：{@code settleTick - launchTick} 不得小于 {@code MIN_AIR_TICKS}
 *       （这条就是"黄金碎击没落地就有地面碎裂特效"的回归门禁）。</li>
 * </ol>
 */
@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)
public final class VfxSemanticsHarness {
    private static final boolean ENABLED = Boolean.getBoolean("eldencombat.vfxSmoke")
            && Boolean.getBoolean("eldencombat.vfxSemantics");
    private static final int FRAME_BUDGET = 200;
    private static final int CAST_FRAME = 45, CAST_END = 57, FOLLOWUP_END = 110;
    /** 这一帧之后不再观察，直接出结论（效果寿命都在 2 秒内）。 */
    private static final int EVALUATE_FRAME = 150;
    /** 起跳冲量发出到落地的最短 tick 数：落地特效/落地伤害早于此就说明"没落地就先炸了"。 */
    private static final int MIN_AIR_TICKS = 5;
    /** "真的跳起来了"的最低高度（格）。 */
    private static final double MIN_APEX = 0.25;

    /** 一个战技的语义预期。{@code minSwings<0} 表示"不做下限断言，只记录"；{@code minChannels} 是本段里
     *  至少要有几个**不同的伤害段**真的打中了前方的受击靶（水鸟/命定的三段斩击必须是 3）；
     *  {@code minRatio} 是"水平位移 / 起跳高度"的下限——真正的突进是往前冲，不是原地蹦。 */
    private record Case(String skill, boolean night, boolean expectJump, double minHorizontal, int minSwings,
                        int minChannels, double minRatio) {}

    private static final Case[] CASES = {
        new Case("gold_breaker", false, true, 0.3, 1, 1, 1.2),
        new Case("destined_death", false, true, 1.2, 3, 3, 1.2),
        new Case("waterfowl_dance", false, true, 2.0, 3, 3, 1.2),
        new Case("cursed_blood_slice", false, true, 0.4, 1, 1, 1.2),
        new Case("starcaller_cry", false, false, 0.0, -1, -1, 0.0),
        new Case("bloodboon_ritual", false, false, 0.0, -1, 1, 0.0),
        new Case("takers_flames", false, false, 0.0, -1, -1, 0.0),
        new Case("wave_of_gold", false, false, 0.0, -1, -1, 0.0),
    };
    private static final int CASE_COUNT = Math.min(CASES.length,
            Integer.getInteger("eldencombat.vfxSemanticsCases", CASES.length));

    private static final double EYE_X = 6.5, EYE_Y = 84.2, EYE_Z = -3.5;
    private static final double AIM_X = 0.0, AIM_Y = 81.6, AIM_Z = 3.0;

    // ---- 运行状态 ----
    private static int ticks, waiting, stage, frameCounter;
    private static boolean purged, evaluated;
    private static final List<String> failures = new ArrayList<>();
    private static ArmorStand observer;

    /** 本段：实例身份 -> [提交帧数, 出现的帧数] */
    private static final Map<String, int[]> coverage = new LinkedHashMap<>();
    private static final Set<String> seen = new LinkedHashSet<>();
    private static int swings, lastSwingTime = -1;
    private static int jumpCountAtStageStart;
    /** 受击靶与"这一段打中了几段"（施法者正前方 3 格、侧偏 0.9 格） */
    private static net.minecraft.world.entity.Mob target;
    private static final double TARGET_X = 0.9, TARGET_Z = 3.0;
    private static int maxChannels, maxHits;
    private static Case current;

    private VfxSemanticsHarness() {}

    // ------------------------------------------------------------------ 客户端 tick：场景与施放
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            if (++waiting > 2400) { EldenCombat.LOGGER.error("VFX_SMOKE_FAIL semantics fixture startup timeout"); mc.stop(); }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        mc.options.fov().set(70);
        mc.options.bobView().set(false);
        mc.options.cloudStatus().set(CloudStatus.OFF);
        stage = ticks / FRAME_BUDGET;
        if (stage >= CASE_COUNT) { finish(mc); return; }
        int frame = ticks % FRAME_BUDGET;
        if (frame == 0) beginStage(mc, stage);
        countSwing(mc);
        if (frame >= CAST_FRAME && frame <= FOLLOWUP_END) input(mc, frame);
        // 相机就用施法者自己的第一人称视角：这既是玩家真实观感（作者美术在不在自己眼前），
        // 也是唯一能验证"服务器给的位移到底有没有作用到玩家身上"的视角。
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
        if (frame >= CAST_FRAME - 4 && frame <= CAST_FRAME + 34 && frame % 2 == 0) {
            EldenCombat.LOGGER.info("VFX_SEMANTICS_MOTION {} frame={} y={} dy={} onGround={} cam={}",
                    current.skill(), frame, fmt(mc.player.getY()), fmt(mc.player.getDeltaMovement().y),
                    mc.player.onGround(), mc.getCameraEntity() == mc.player ? "player" : "other");
        }
        ticks++;
    }

    /**
     * 施法者自己也要有"挥击"：服务端每次真实挥击都会重置本地玩家的 {@code swingTime}，
     * 所以"起手次数"= swingTime 从 -1/较大值回落到小值的次数。
     */
    private static void countSwing(Minecraft mc) {
        int now = mc.player.swinging ? mc.player.swingTime : -1;
        if (now >= 0 && (lastSwingTime < 0 || now < lastSwingTime)) swings++;
        lastSwingTime = now;
    }

    private static void beginStage(Minecraft mc, int index) {
        current = CASES[index];
        coverage.clear();
        seen.clear();
        swings = 0;
        lastSwingTime = -1;
        maxChannels = 0;
        maxHits = 0;
        evaluated = false;
        jumpCountAtStageStart = RemembranceRuntime.jumpCount();
        VfxDiagnosticLayers.reset();   // 整段 NORMAL 层：这就是玩家真实观感，不做任何诊断抑制
        scene(mc, current, index);
        EldenCombat.LOGGER.info("VFX_SEMANTICS_STAGE_BEGIN stage={} skill={} night={} expectJump={} minHorizontal={} minSwings={}",
                index, current.skill(), current.night(), current.expectJump(), current.minHorizontal(), current.minSwings());
    }

    private static void scene(Minecraft mc, Case def, int index) {
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var p = server.getPlayerList().getPlayer(uuid);
            if (p == null) return;
            var level = p.serverLevel();
            SkillDriver.cancel(p, p.level().getGameTime(), "semantics fixture reset");
            for (var e : level.getEntities().getAll())
                if (e instanceof SkillEffectEntity || e instanceof GoldenWaveEntity) e.discard();
            // 只留施法者与专用受击靶：野生的生物会跑进挥击胶囊，把"打中了几段"变成噪声。
            for (var e : level.getEntities().getAll())
                if (e instanceof net.minecraft.world.entity.LivingEntity living && living != p) living.discard();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setDayTime(def.night() ? 18000 : 6000);
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 24; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
                for (int y = 81; y <= 87; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            p.connection.teleport(0, 81, 0, 0, 0);
            p.setNoGravity(false);
            p.setDeltaMovement(Vec3.ZERO);
            p.setHealth(p.getMaxHealth());
            var spec = SkillDriver.spec(def.skill());
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("eldencombat", spec.itemId()))));
            var fp = SkillServerEvents.fp(p);
            fp.setFp(fp.maxFp());
            fp.clearCooldown(def.skill());
            placeTarget(level);
            EldenCombat.LOGGER.info("VFX_SEMANTICS_SCENE {} {}", index, def.skill());
        });
    }

    /** 受击靶：施法者正前方 3 格、偏侧 0.9 格（不挡突进路线，但进得了挥击胶囊）。 */
    private static void placeTarget(net.minecraft.server.level.ServerLevel level) {
        if (target != null) { target.discard(); target = null; }
        var zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) return;
        zombie.moveTo(TARGET_X, 81.0, TARGET_Z, 180.0f, 0.0f);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        // 白天不烧、不还手、不移动：它只是"被不被砍到"的几何参照物。
        zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 100000, 0, false, false));
        // 血量必须撑过整段连击：靶子中途死掉会让"第 2、3 段有没有打中"变成"靶子还在不在"。
        var health = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(500.0);
        zombie.setHealth(zombie.getMaxHealth());
        level.addFreshEntity(zombie);
        target = zombie;
    }

    private static void input(Minecraft mc, int frame) {
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var p = server.getPlayerList().getPlayer(uuid);
            if (p == null) return;
            long now = p.level().getGameTime();
            var cast = SkillDriver.cast(p);
            // 首帧起手；之后只在**真实允许的追击窗口**里补请求（多段战技的第二段必须走这条路）。
            if (frame == CAST_FRAME || cast.followupOpen(now)) {
                var result = SkillCasting.request(p, now);
                EldenCombat.LOGGER.info("VFX_SEMANTICS_INPUT {} frame={} result={}", current.skill(), frame, result);
            }
            // 逐帧记录"这一段到底打中了几段"，并把靶子奶满，保证三段各自都能结算。
            if (cast.isCasting()) {
                maxChannels = Math.max(maxChannels, cast.resolvedChannelCount());
                maxHits = Math.max(maxHits, cast.hitCount());
            }
            if (target != null && target.isAlive()) target.setHealth(target.getMaxHealth());
        });
    }

    // ------------------------------------------------------------------ 渲染帧：逐实例真实提交覆盖
    @SubscribeEvent public static void level(RenderLevelStageEvent event) {
        if (!ENABLED) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) { VfxDiagnosticLayers.beginFrame(); return; }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        observeInstances(mc);
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!ENABLED) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        frameCounter++;
        if (stage >= CASE_COUNT || evaluated) return;
        if (ticks % FRAME_BUDGET < EVALUATE_FRAME) return;
        evaluated = true;
        evaluate(mc, stage);
    }

    /** 逐帧登记"这个实例这一帧有没有真实美术提交"（提交登记由渲染器在真实提交后写）。 */
    private static void observeInstances(Minecraft mc) {
        Set<String> submitted = VfxDiagnosticLayers.submittedIdentitiesThisFrame();
        for (var e : mc.level.entitiesForRendering()) {
            String identity;
            if (e instanceof GoldenWaveEntity wave) identity = "wave/" + wave.getId();
            else if (e instanceof SkillEffectEntity effect) identity = "effect/" + effect.getId() + "/" + effect.phase();
            else continue;
            seen.add(identity);
            int[] row = coverage.computeIfAbsent(identity, key -> new int[2]);
            row[1]++;
            if (submitted.contains(identity)) row[0]++;
        }
    }

    // ------------------------------------------------------------------ 判据
    private static void evaluate(Minecraft mc, int index) {
        Case def = CASES[index];
        List<String> missing = new ArrayList<>();
        for (String identity : seen) {
            int[] row = coverage.getOrDefault(identity, new int[2]);
            if (row[0] < 1) missing.add(identity + "(seen=" + row[1] + ",submitted=0)");
        }
        if (seen.isEmpty()) fail("stage=" + index + " " + def.skill() + ": no effect instance ever appeared on the client");
        if (!missing.isEmpty()) fail("stage=" + index + " " + def.skill()
                + ": authored artwork never submitted for " + missing.size() + " instance(s): " + missing);
        var trace = RemembranceRuntime.lastJump();
        boolean jumped = RemembranceRuntime.jumpCount() > jumpCountAtStageStart;
        if (def.expectJump()) {
            if (!jumped || trace == null) {
                fail("stage=" + index + " " + def.skill() + ": expected a jump but no landing timeline was recorded");
            } else {
                if (!trace.skill().equals(def.skill()))
                    fail("stage=" + index + " " + def.skill() + ": jump timeline belongs to " + trace.skill());
                if (!trace.departed())
                    fail("stage=" + index + " " + def.skill() + ": never observed leaving the ground (departed=false)");
                if (trace.apexY() - trace.startY() < MIN_APEX)
                    fail("stage=" + index + " " + def.skill() + ": apex only " + fmt(trace.apexY() - trace.startY())
                            + " block(s) above the take-off");
                if (trace.durationTicks() < MIN_AIR_TICKS)
                    fail("stage=" + index + " " + def.skill() + ": settled " + trace.durationTicks()
                            + " tick(s) after take-off (min " + MIN_AIR_TICKS + ") — impact effects must not fire before touchdown");
                if (trace.horizontal() < def.minHorizontal())
                    fail("stage=" + index + " " + def.skill() + ": only advanced " + fmt(trace.horizontal())
                            + " block(s) horizontally (need " + def.minHorizontal() + ") — jumping in place is not a lunge");
                // "蹦"与"突进"的区别在比例：垂直冲量再小，只要人几乎不往前，观感就是原地蹦。
                double rise = trace.apexY() - trace.startY();
                if (def.minRatio() > 0 && rise > 0 && trace.horizontal() < def.minRatio() * rise)
                    fail("stage=" + index + " " + def.skill() + ": advanced only " + fmt(trace.horizontal())
                            + " block(s) horizontally while rising " + fmt(rise) + " block(s) (need horizontal ≥ "
                            + def.minRatio() + "× rise) — this is a vertical hop, not a forward lunge");
            }
        }
        if (def.minSwings() >= 0 && swings < def.minSwings())
            fail("stage=" + index + " " + def.skill() + ": caster played " + swings
                    + " swing animation(s), expected at least " + def.minSwings());
        // 多段斩击必须"每一段都真的打到前面的目标"：只打到第一段就是"只有挥剑的第一下有（命中）效果"。
        if (def.minChannels() >= 0 && maxChannels < def.minChannels())
            fail("stage=" + index + " " + def.skill() + ": only " + maxChannels + " of the expected "
                    + def.minChannels() + " damage segment(s) connected with a target right in front — the later swings "
                    + "miss (the caster is airborne above the sweep while slashing downward into empty air)");
        StringBuilder rows = new StringBuilder();
        for (var entry : coverage.entrySet()) {
            if (rows.length() > 0) rows.append(" | ");
            rows.append(String.format(Locale.ROOT, "%s submitted=%d/%d", entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        EldenCombat.LOGGER.info("VFX_SEMANTICS_STAGE stage={} skill={} instances={} covered={} missing={} swings={} channels={}/{} hits={} jumped={} jump=[{}] coverage=[{}]",
                index, def.skill(), seen.size(), seen.size() - missing.size(), missing, swings,
                maxChannels, def.minChannels(), maxHits, jumped,
                trace == null ? "none" : String.format(Locale.ROOT,
                        "skill=%s outcome=%s start=(%.2f,%.2f,%.2f) apex=%.2f landed=(%.2f,%.2f,%.2f) horizontal=%.2f ticks=%d departed=%s launchOnGround=%s launchUp=%.3f",
                        trace.skill(), trace.outcome(), trace.startX(), trace.startY(), trace.startZ(), trace.apexY(),
                        trace.landedX(), trace.landedY(), trace.landedZ(), trace.horizontal(), trace.durationTicks(),
                        trace.departed(), trace.launchOnGround(), trace.launchUp()),
                rows);
        VfxDiagnosticLayers.reset();
    }

    private static String fmt(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    // ------------------------------------------------------------------ 相机
    /**
     * **旧的观察机位（相机实体 = 盔甲架）——刻意保留，作为"夹具盲区"的证据。**
     *
     * <p>实测结论（见报告 §4）：把相机实体设成盔甲架时，服务端发出的技能位移**完全没有作用到玩家身上**
     * （`apex=81.000`、`horizontal=0.000`），所以用这种机位拍出来的 13 段受控取证在"玩家位移"
     * 这一类现象上恒为 0 —— "原地蹦 / 三段捞空 / 落地特效早于落地"就在那个盲区里被漏掉了。
     * 现在的 {@link #tick} 直接用施法者自己的第一人称相机。
     */
    @SuppressWarnings("unused")
    private static void pinObserver(Minecraft mc) {
        if (observer == null || observer.level() != mc.level) {
            observer = EntityType.ARMOR_STAND.create(mc.level);
            if (observer == null) return;
            observer.setInvisible(true);
            observer.setNoGravity(true);
            observer.setInvulnerable(true);
            observer.setSilent(true);
        }
        float[] aim = VfxCameraMath.aimAt(new Vec3(EYE_X, EYE_Y, EYE_Z), new Vec3(AIM_X, AIM_Y, AIM_Z));
        observer.moveTo(EYE_X, EYE_Y - observer.getEyeHeight(), EYE_Z, aim[0], aim[1]);
        observer.setYHeadRot(aim[0]);
        observer.yHeadRotO = aim[0];
        observer.yRotO = aim[0];
        observer.xRotO = aim[1];
        observer.setOldPosAndRot();
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != observer) mc.setCameraEntity(observer);
    }

    // ------------------------------------------------------------------ 收尾
    private static void finish(Minecraft mc) {
        if (failures.isEmpty()) {
            EldenCombat.LOGGER.info("VFX_SEMANTICS_COMPLETE: {} skill(s); per-instance authored submission under NORMAL, caster swing count, jump timeline and touch-down ordering", CASE_COUNT);
            EldenCombat.LOGGER.info("VFX_SMOKE_COMPLETE: semantics fixture; {} skill(s); no pixel attribution claimed here", CASE_COUNT);
        } else {
            for (String failure : failures) EldenCombat.LOGGER.error("VFX_SMOKE_FAIL: {}", failure);
            EldenCombat.LOGGER.error("VFX_SMOKE_INCOMPLETE: {} semantics failure(s); no completion marker emitted", failures.size());
        }
        ticks = CASE_COUNT * FRAME_BUDGET + 1;
        mc.stop();
    }

    private static void fail(String reason) { if (!failures.contains(reason)) failures.add(reason); }
}
