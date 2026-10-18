package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.client.PresentationRoutes;
import com.dsh.eldencombat.client.effects.VfxDiagnosticLayers;
import com.dsh.eldencombat.effects.VfxCameraMath;
import com.dsh.eldencombat.effects.VfxCaptureSequence;
import com.dsh.eldencombat.effects.VfxShotComparison;
import com.dsh.eldencombat.entity.skill.GoldenWaveEntity;
import com.dsh.eldencombat.entity.skill.SkillEffectEntity;
import com.dsh.eldencombat.server.skills.SkillCasting;
import com.dsh.eldencombat.server.skills.SkillDriver;
import com.dsh.eldencombat.server.skills.SkillServerEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * V1 受控取证夹具（仅 {@code eldencombat.vfxSmoke + eldencombat.vfxFixed} 时工作）。
 *
 * <p>与 18 段旧夹具的区别：相机是**独立观察实体的固定斜侧机位**（施放前选定、整段锁定，不追玩家跃起/
 * 突进/波移动），每个技能按"预先分层取样 + 相邻帧配对"取证：
 *
 * <ul>
 *   <li>{@code CLEAN}（目标主/副 VFX 都不画）→ 同状态背景参照；</li>
 *   <li>{@code NORMAL}（演员可见、全部 VFX 正常）→ 外部动作样本，只作可读性/锚点审查；</li>
 *   <li>{@code PRIMARY_ONLY} ↔ 紧邻 {@code CLEAN} 帧 → 主体归因的 live/clean 配对；</li>
 *   <li>{@code DEBRIS_ONLY} ↔ 紧邻 {@code CLEAN} 帧 → 碎块单独归因，不能给主贴片代签。</li>
 * </ul>
 *
 * <p>每帧在 {@link RenderLevelStageEvent}（AFTER_LEVEL）复制真实模型视图/投影矩阵、相机、视口与渲染设置，
 * 与同帧的截图绑定；配对先过 {@link VfxShotComparison#comparable} 才算差分，失配只记 NOT_COMPARABLE，
 * 不产生分数、不放宽阈值。施法者在屏幕上的投影框从主体 ROI 中扣除，避免把玩家移动算成作者美术。
 *
 * <p>负例：① 无效果、只移动相机 → 必须判不可比；② 目标主贴片被诊断抑制 → 主体门禁必须失败，
 * 同段再单独取碎块，证明"碎块可见但不给主贴片代签"。正常客户端不加载本类；发布 JAR 亦排除 {@code *Harness*}。
 */
@EventBusSubscriber(modid = EldenCombat.MODID, value = Dist.CLIENT)
public final class VfxFixedHarness {
    private static final boolean ENABLED = Boolean.getBoolean("eldencombat.vfxSmoke")
            && Boolean.getBoolean("eldencombat.vfxFixed");
    private static final int FRAME_BUDGET = 200;
    private static final int FIRST_FRAME = 8;
    /** 场景重建后先让区块/光照/资源真正落定，再拍"拍摄前 CLEAN 参照"，否则参照帧会是半加载世界。 */
    private static final int SETTLE_TICKS = 40;
    /** 施放帧与真实连段窗口：只在窗口内发请求，不无限刷技能。 */
    private static final int CAST_FRAME = 45, CAST_END = 57;
    /** 效果退场/飞离后的背景参照不早于该 tick（效果实体寿命可能长于取景窗口，故用同层干净帧作后参照）。 */
    private static final int POST_TICKS = 140;
    private static final int LIVE_SPACING = 6, SHORT_LIFE_SPACING = 2, MAX_INFLIGHT = 2;
    private static final int MIN_SUBJECT_PIXELS = 300;
    private static final int CHANNEL_DELTA = 24;
    private static final int ROI_MARGIN = 3, ACTOR_MARGIN = 6;
    private static final double MAX_OUTSIDE_FRACTION = 0.005;

    private record Vantage(double eyeX, double eyeY, double eyeZ, double aimX, double aimY, double aimZ) {}

    private record Segment(String skill, boolean night, boolean debris, boolean orbit, boolean negativeCamera,
                           boolean negativePrimary, Vantage vantage, Vantage secondVantage, String note) {}

    private static final Vantage GROUND_FRONT = new Vantage(6.5, 84.2, -3.5, 0.0, 81.6, 3.0);
    private static final Vantage WAVE_SIDE = new Vantage(8.0, 84.6, -3.0, 0.0, 81.8, 6.0);
    /**
     * 面向火墙正面（施法者在 z=0 朝 +Z，故机位放在前方回看）：火墙是**横贯的墙**，
     * 斜侧机位只会拍到墙的侧棱并被人自己挡住——实测侧棱机位只有 139 像素，无法代表"作者美术上没上屏"。
     */
    private static final Vantage WAVE_FRONT = new Vantage(0.0, 83.6, 9.0, 0.0, 81.6, 1.0);
    private static final Vantage SELF_CENTRED = new Vantage(5.4, 84.0, -3.2, 0.0, 81.9, 0.6);
    private static final Vantage OVERHEAD = new Vantage(6.8, 85.0, -3.6, 0.0, 82.4, 2.4);
    private static final Vantage MOVED = new Vantage(2.5, 85.0, -4.5, 0.0, 82.0, 4.0);

    private static final Segment[] SEGMENTS = {
        new Segment("wave_of_gold", false, false, true, false, false, WAVE_SIDE, null, "金波：外部机位 + 有限环绕"),
        new Segment("gold_breaker", false, true, false, false, false, GROUND_FRONT, null, "金色坠击主贴片 + 碎块单独归因"),
        new Segment("destined_death", false, false, false, false, false, GROUND_FRONT, null, "延迟斩击"),
        new Segment("takers_flames", false, false, true, false, false, WAVE_FRONT, null, "火墙：正面机位 + 有限环绕"),
        new Segment("waterfowl_dance", false, false, false, false, false, GROUND_FRONT, null, "突进型连续斩击"),
        new Segment("starcaller_cry", false, true, false, false, false, OVERHEAD, null, "重力技能 + 碎块单独归因"),
        new Segment("bloodboon_ritual", false, false, false, false, false, SELF_CENTRED, null, "以施法者为中心的血仪式"),
        new Segment("cursed_blood_slice", false, false, false, false, false, GROUND_FRONT, null, "猩红斩击"),
        new Segment("destined_death", true, false, false, false, false, GROUND_FRONT, null, "夜间：延迟斩击"),
        new Segment("waterfowl_dance", true, false, false, false, false, GROUND_FRONT, null, "夜间：突进连续斩击"),
        new Segment("bloodboon_ritual", true, false, false, false, false, SELF_CENTRED, null, "夜间：血仪式"),
        new Segment("", false, false, false, true, false, GROUND_FRONT, MOVED, "负例：无目标效果，只移动相机"),
        new Segment("gold_breaker", false, true, false, false, true, GROUND_FRONT, null, "负例：主贴片被诊断抑制 + 碎块单独取样"),
    };
    private static final int SEGMENT_COUNT = Math.min(SEGMENTS.length,
            Integer.getInteger("eldencombat.vfxFixedSegments", SEGMENTS.length));

    private enum Need { ALIVE, PRIMARY, DEBRIS, DEAD, PRE, ANY }

    private enum Camera { OBSERVER, PLAYER_THIRD }

    /**
     * 一个取证步骤。{@code spacing} 是"同一重复步骤两次取样之间至少隔几帧"。
     *
     * <p>为什么必须按步骤给：短寿命效果的整条命只有 8 tick，而默认 {@link #LIVE_SPACING}=6 让
     * "取 4 帧"实际只能取到第 1 帧——4 段曾因此只量到 184/266/176/31 像素，看起来像"作者美术太薄"，
     * 实际是**只截到了作者动画的第 0 帧**（水鸟 frame_00 墨量 72，中段 frame_08 是 140，包围盒大 4 倍）。
     * 主体取样改成隔 2 帧，才能覆盖淡入→中段→收尾。
     */
    private record Step(VfxDiagnosticLayers.Layer layerA, VfxDiagnosticLayers.Layer layerB, String label,
                        Need need, Camera camera, int repeats, int spacing) {}

    private static Step step(VfxDiagnosticLayers.Layer a, VfxDiagnosticLayers.Layer b, String label,
                             Need need, Camera camera, int repeats) {
        return new Step(a, b, label, need, camera, repeats, LIVE_SPACING);
    }

    // ---- 运行状态 ----
    private static int ticks, waiting, segment, frameCounter, cleanFrames;
    private static boolean purged;
    private static ArmorStand observer;
    private static boolean observerMoved;
    private static final Set<String> landed = new LinkedHashSet<>();
    private static final List<String> inflight = new ArrayList<>();
    private static final Map<String, Integer> inflightAge = new LinkedHashMap<>();
    /** 文件出现后还要再等几帧才认为写入完成：io 池先建文件后刷内容，立刻读会读到空图。 */
    private static final Map<String, Integer> settling = new LinkedHashMap<>();
    private static final int SETTLE_FRAMES = 2;
    private static final List<String> failures = new ArrayList<>();

    // ---- 每帧渲染状态（AFTER_LEVEL 复制，同帧截图绑定） ----
    private static VfxShotComparison.ShotState pendingState;
    private static final Map<String, VfxShotComparison.ShotState> states = new LinkedHashMap<>();
    private static final Map<String, double[][]> shotCorners = new LinkedHashMap<>();
    private static final Map<String, int[]> shotActorRects = new LinkedHashMap<>();
    /** 本段"演员扫过的屏幕区域"并集（背景稳定性扣除用，见 {@link #actorSweptRect}） */
    private static int[] segmentActorHalo;
    private static final Map<String, String> shotPurpose = new LinkedHashMap<>();
    private static final Map<String, String> shotCamera = new LinkedHashMap<>();
    /** 每张样本发起时的作者美术帧号（{@code 帧/总}）：证据里必须写明"量的是哪一帧"。 */
    private static final Map<String, String> shotArt = new LinkedHashMap<>();

    // ---- 本段计划与判据 ----
    private static List<Step> plan = List.of();
    private static int stepIndex, stepShotFrame = -999;
    private static final VfxCaptureSequence capture = new VfxCaptureSequence();
    private static String currentSkill = "", segmentTag = "negative";
    private static Segment currentDef;
    private static int deadTicks, clearedTicks;
    private static int pairs, comparables, notComparables, subjectBest, outsideWorst, roiPixelsLast;
    private static int preCleanShots, postCleanShots, actionShots, thirdShots;
    private static int primaryInRoi, debrisInRoi, suppressedInRoi, spriteTotal, outlineTotal, debrisTotal, legacyTotal;
    private static boolean negativeCameraOk, negativePrimaryOk;
    private static boolean suppressedEvaluated, primaryEvaluated, debrisEvaluated;
    private static boolean postCleanWhileDead;
    private static final StringBuilder rois = new StringBuilder();

    private VfxFixedHarness() {}

    // ------------------------------------------------------------------ 客户端 tick：场景与输入
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            if (++waiting > 2400) { EldenCombat.LOGGER.error("VFX_SMOKE_FAIL fixed fixture startup timeout"); mc.stop(); }
            return;
        }
        purgeOldShots(mc);
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        mc.options.fov().set(70);
        mc.options.bobView().set(false);
        mc.options.cloudStatus().set(CloudStatus.OFF);   // 关掉云的背景运动，受控对照里只留静态场景
        segment = ticks / FRAME_BUDGET;
        if (segment >= SEGMENT_COUNT) { finish(mc); return; }
        int frame = ticks % FRAME_BUDGET;
        var def = SEGMENTS[segment];
        if (frame == 0) beginSegment(mc, segment, def);
        // 只走真实允许窗口：首帧施放，之后的连段请求仅在本段前段（真实连段窗口内）发出，
        // 不无限刷技能；这样效果能自然结束，post-clean 才能真正等到主体退场。
        if (!def.skill().isEmpty() && frame >= CAST_FRAME && frame <= CAST_END) input(mc, def.skill(), frame);
        // 相机在 tick 侧就位（渲染帧只做判定与截图）：观察实体每 tick 钉死在固定机位与朝向上，
        // 负例段由计划自己移动它；只影响相机，不冻结服务端技能/玩家。
        if (!def.negativeCamera()) pinObserver(mc, def.vantage());
        applyDesiredCamera(mc);
        if (visibleBounds(mc) == null) deadTicks++; else deadTicks = 0;
        if (targetCleared(mc)) clearedTicks++; else clearedTicks = 0;
        if (frame == FRAME_BUDGET - 1) {
            // 还有截图在飞或配对未读完：本段延长，不越界进入下一段（超限由截图的落盘超时判失败）。
            if (!inflight.isEmpty() || !pendingPairs.isEmpty() || !settling.isEmpty()) return;
            closeSegment(mc, segment, def);
        }
        ticks++;
    }

    private static void purgeOldShots(Minecraft mc) {
        if (purged) return;
        purged = true;
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] stale = dir.listFiles((d, name) -> name.startsWith("vfxfixed-"));
        if (stale == null) return;
        for (File file : stale) if (!file.delete()) fail("could not delete stale fixture screenshot " + file.getName());
        EldenCombat.LOGGER.info("VFX_FIXED_PURGE removed {} stale fixture screenshot(s)", stale.length);
    }

    private static void beginSegment(Minecraft mc, int index, Segment def) {
        currentSkill = def.skill();
        currentDef = def;
        segmentTag = def.negativeCamera() || def.negativePrimary() ? "negative" : def.skill();
        plan = buildPlan(def);
        stepIndex = 0; capture.reset(); deadTicks = 0; clearedTicks = 0; stepShotFrame = -999;
        pairs = 0; comparables = 0; notComparables = 0; subjectBest = 0; outsideWorst = 0; roiPixelsLast = 0;
        preCleanShots = 0; postCleanShots = 0; actionShots = 0; thirdShots = 0;
        primaryInRoi = 0; debrisInRoi = 0; suppressedInRoi = 0;
        suppressedEvaluated = false; primaryEvaluated = false; debrisEvaluated = false; postCleanWhileDead = false;
        spriteTotal = 0; outlineTotal = 0; debrisTotal = 0; legacyTotal = 0;
        negativeCameraOk = false; negativePrimaryOk = false; observerMoved = false;
        segmentActorHalo = null;
        rois.setLength(0);
        inflight.clear(); inflightAge.clear(); settling.clear(); pendingPairs.clear(); pendingPairAge.clear();
        int owner = mc.player == null ? -1 : mc.player.getId();
        VfxDiagnosticLayers.select(VfxDiagnosticLayers.Layer.NORMAL, owner, -1, List.of(), -1);
        scene(mc, def, index);
        EldenCombat.LOGGER.info("VFX_FIXED_STAGE segment={} skill={} night={} debris={} orbit={} negativeCamera={} negativePrimary={} eye=({},{},{}) aim=({},{},{}) frames={} note={}",
                index, def.skill().isEmpty() ? "(no cast)" : def.skill(), def.night(), def.debris(), def.orbit(),
                def.negativeCamera(), def.negativePrimary(), def.vantage().eyeX(), def.vantage().eyeY(), def.vantage().eyeZ(),
                def.vantage().aimX(), def.vantage().aimY(), def.vantage().aimZ(), plan.size(), def.note());
    }

    private static List<Step> buildPlan(Segment def) {
        var steps = new ArrayList<Step>();
        if (def.negativeCamera()) {
            steps.add(step(VfxDiagnosticLayers.Layer.NORMAL, null, "camA", Need.ANY, Camera.OBSERVER, 1));
            steps.add(step(VfxDiagnosticLayers.Layer.NORMAL, null, "camB", Need.ANY, Camera.OBSERVER, 1));
            return List.copyOf(steps);
        }
        if (def.negativePrimary()) {
            steps.add(step(VfxDiagnosticLayers.Layer.CLEAN, null, "pre-clean", Need.ANY, Camera.OBSERVER, 1));
            steps.add(step(VfxDiagnosticLayers.Layer.CLEAN, VfxDiagnosticLayers.Layer.CLEAN, "suppressed", Need.ALIVE, Camera.OBSERVER, 1));
            steps.add(step(VfxDiagnosticLayers.Layer.PRIMARY_ONLY, VfxDiagnosticLayers.Layer.CLEAN, "primary", Need.PRIMARY, Camera.OBSERVER, 1));
            steps.add(step(VfxDiagnosticLayers.Layer.DEBRIS_ONLY, VfxDiagnosticLayers.Layer.CLEAN, "debris", Need.DEBRIS, Camera.OBSERVER, 1));
            return List.copyOf(steps);
        }
        steps.add(step(VfxDiagnosticLayers.Layer.CLEAN, null, "pre-clean", Need.PRE, Camera.OBSERVER, 1));
        steps.add(step(VfxDiagnosticLayers.Layer.NORMAL, null, "action", Need.PRIMARY, Camera.OBSERVER, 1));
        steps.add(step(VfxDiagnosticLayers.Layer.PRIMARY_ONLY, VfxDiagnosticLayers.Layer.CLEAN, "primary1", Need.PRIMARY, Camera.OBSERVER, 1));
        // 追加四组完整配对，保留不同动画相位；间隔单位是渲染帧，不是游戏tick。
        steps.add(new Step(VfxDiagnosticLayers.Layer.PRIMARY_ONLY, VfxDiagnosticLayers.Layer.CLEAN, "primary2",
                Need.PRIMARY, Camera.OBSERVER, 4, SHORT_LIFE_SPACING));
        if (def.debris()) steps.add(step(VfxDiagnosticLayers.Layer.DEBRIS_ONLY, VfxDiagnosticLayers.Layer.CLEAN, "debris1", Need.DEBRIS, Camera.OBSERVER, 1));
        // 环绕必须在效果存活期内取样，故排在 post-clean 之前；有限角度/张数，移动镜头样本不进入固定机位配对。
        if (def.orbit()) steps.add(step(VfxDiagnosticLayers.Layer.NORMAL, null, "third", Need.ALIVE, Camera.PLAYER_THIRD, 2));
        // 效果结束后的背景参照：实体已退场，或（长寿命波飞离固定机位后）本段后段同层同机位的干净帧。
        steps.add(step(VfxDiagnosticLayers.Layer.CLEAN, null, "post-clean", Need.DEAD, Camera.OBSERVER, 1));
        return List.copyOf(steps);
    }

    // ------------------------------------------------------------------ 渲染帧：状态复制 + 计划执行
    @SubscribeEvent public static void level(RenderLevelStageEvent event) {
        if (!ENABLED) return;
        // AFTER_SKY 早于实体渲染：在这里清空"本帧真实提交"计数，AFTER_LEVEL 读到的就是本帧的提交结果。
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) { VfxDiagnosticLayers.beginFrame(); return; }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float[] modelView = new float[16], projection = new float[16];
        event.getModelViewMatrix().get(modelView);
        event.getProjectionMatrix().get(projection);
        var cam = event.getCamera();
        Vec3 eye = cam.getPosition();
        long dayTime = mc.level.getDayTime() % 24000L;
        pendingState = new VfxShotComparison.ShotState(
                "fixed/" + segment + "/" + (currentSkill.isEmpty() ? "negative" : currentSkill), event.getRenderTick(),
                PresentationRoutes.revision(), mc.level.dimension().location().toString(),
                cameraKind(mc), VfxDiagnosticLayers.layer().name(), targetIdentity(mc),
                eye.x, eye.y, eye.z, cam.getYRot(), cam.getXRot(), modelView, projection,
                mc.getMainRenderTarget().width, mc.getMainRenderTarget().height,
                mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getWindow().getGuiScale(),
                mc.options.bobView().get(), dayTime, dayTime >= 13000 && dayTime < 23000, mc.level.isRaining());
    }

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!ENABLED) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        frameCounter++;
        if (mc.getOverlay() != null) { cleanFrames = 0; return; }
        cleanFrames++;
        collectLanded(mc);
        evaluatePairs(mc);
        if (segment >= SEGMENT_COUNT) return;
        if (!cameraSettled(mc)) {
            if (frameCounter % 40 == 0) EldenCombat.LOGGER.info("VFX_STEP_WAIT segment={} frame={} reason=camera-entity-unsettled camEntity={}",
                    segment, frameCounter, cameraKind(mc));
            return;
        }
        if (frameCounter < FIRST_FRAME) return;
        planFrame(mc);
    }

    private static void planFrame(Minecraft mc) {
        if (plan.isEmpty() || stepIndex >= plan.size()) return;
        if (inflight.size() >= MAX_INFLIGHT) return;
        // 目标实例集合每帧刷新：客户端实体上的施法者 id 可能是 -1，必须按实体编号锁定本次施放的实例。
        VfxDiagnosticLayers.retarget(liveTargetIds(mc));
        var renderedLayer = VfxDiagnosticLayers.layer();
        Step step = plan.get(stepIndex);
        // 相机设置与分层都必须"先改、下一帧再拍"：本帧渲染用的仍是旧设置。
        if (step.camera() == Camera.PLAYER_THIRD) {
            if (mc.getCameraEntity() != mc.player) { applyCamera(mc, step.camera()); return; }
        } else if (mc.getCameraEntity() == mc.player) { applyCamera(mc, step.camera()); return; }
        if (step.label().equals("camB") && !observerMoved) {
            observerMoved = true;
            moveObserver(mc, currentDef.secondVantage());
            return;
        }
        // 配对第二阶段必须优先于"层是否等于 layerA"的切换判断，否则 live/clean 两层会互相把对方顶掉。
        if (capture.awaitingClean()) {
            if (renderedLayer != step.layerB()) { VfxDiagnosticLayers.setLayer(step.layerB()); return; }
            if (!requestShot(mc, step, "clean", false)) return;
            if (capture.cleanCaptured(step.repeats())) { stepIndex++; capture.reset(); }
            stepShotFrame = frameCounter;
            return;
        }
        if (renderedLayer != step.layerA()) { VfxDiagnosticLayers.setLayer(step.layerA()); return; }
        if (step.repeats() > 1 && frameCounter - stepShotFrame < step.spacing()) return;
        // 暂时没有提交可能只是两次burst之间的空档，不能把未完成的重复取样当成完成。
        // 整段仍由既有FRAME_BUDGET有界收束；缺样会按未完成步骤报告失败。
        if (!needSatisfied(mc, step.need())) {
            if (frameCounter % 40 == 0)
                EldenCombat.LOGGER.info("VFX_STEP_WAIT segment={} frame={} step={} need={} bounds={} deadTicks={} ticks={} identity={}",
                        segment, frameCounter, step.label(), step.need(), visibleBounds(mc) != null, deadTicks, ticks, targetIdentity(mc));
            return;
        }
        // 只有"效果必须活着/这一帧必须真有提交"的步骤才要求现场有活效果实体；参照帧与负例机位帧不要求。
        boolean requireEffect = step.need() == Need.ALIVE || step.need() == Need.PRIMARY || step.need() == Need.DEBRIS;
        if (!requestShot(mc, step, "a", requireEffect)) return;
        stepShotFrame = frameCounter;
        boolean paired = step.layerB() != null;
        boolean complete = capture.liveCaptured(step.repeats(), paired);
        if (paired) {
            VfxDiagnosticLayers.setLayer(step.layerB());
            return;
        }
        if (complete) { stepIndex++; capture.reset(); }
    }

    private static boolean requestShot(Minecraft mc, Step step, String slot, boolean requireEffect) {
        // 截图决策与发起在同一次渲染帧回调里：决策时刻的实体状态就是这一帧被读出的内容。
        if (pendingState == null) return false;
        if (requireEffect && visibleBounds(mc) == null) return false;
        String name = String.format(Locale.ROOT, "vfxfixed-%02d-%s-%s-%s.png", segment, segmentTag,
                capture.sampleLabel(step.label(), step.repeats()), slot);
        AABB bounds = visibleBounds(mc);
        double[][] corners = bounds == null
                ? VfxShotComparison.corners(0, 81, 0, 0.5, 82, 0.5)
                : VfxShotComparison.corners(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
        states.put(name, pendingState);
        shotCorners.put(name, corners);
        shotActorRects.put(name, actorRect(mc, pendingState));
        segmentActorHalo = unionRect(segmentActorHalo, actorSweptRect(mc, pendingState));
        shotPurpose.put(name, step.label());
        shotCamera.put(name, cameraKind(mc));
        shotArt.put(name, VfxDiagnosticLayers.artThisFrame());
        inflight.add(name); inflightAge.put(name, 0);
        if (step.label().equals("post-clean")) postCleanWhileDead = visibleBounds(mc) == null;
        EldenCombat.LOGGER.info("VFX_SHOT_REQ name={} tick={} frame={} step={} layer={} cam={} slot={} art={} effects=[{}]",
                name, ticks, ticks % FRAME_BUDGET, step.label(), VfxDiagnosticLayers.layer(), cameraKind(mc), slot,
                VfxDiagnosticLayers.artThisFrame(), effectSummary(mc));
        EldenCombat.LOGGER.info("VFX_SHOT_STATE name={} {}", name, VfxShotComparison.describe(pendingState));
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> { });
        return true;
    }

    private static void collectLanded(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        for (var iterator = inflight.iterator(); iterator.hasNext(); ) {
            String name = iterator.next();
            if (new File(dir, name).isFile()) {
                iterator.remove(); inflightAge.remove(name);
                settling.put(name, 0);
            } else if (inflightAge.merge(name, 1, Integer::sum) > 300) {
                iterator.remove(); inflightAge.remove(name);
                fail("segment=" + segment + " screenshot never landed: " + name);
            }
        }
        for (var iterator = settling.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            String name = entry.getKey();
            int age = entry.setValue(entry.getValue() + 1);
            if (age > 300) { iterator.remove(); fail("segment=" + segment + " screenshot never finished writing: " + name); continue; }
            if (age < SETTLE_FRAMES || !new File(dir, name).isFile()) continue;
            iterator.remove();
            if (!landed.add(name)) continue;
            EldenCombat.LOGGER.info("VFX_SHOT_LANDED {} shots={}", name, landed.size());
            onLanded(mc, name);
        }
    }

    /** 一张图落盘后：按用途计数；若是配对帧，则排队等两张都真正可读后做 comparable 判定与差分。 */
    private static void onLanded(Minecraft mc, String name) {
        String purpose = shotPurpose.getOrDefault(name, "");
        if (purpose.equals("pre-clean")) preCleanShots++;
        else if (purpose.equals("post-clean")) postCleanShots++;
        else if (purpose.equals("action")) actionShots++;
        else if (purpose.equals("third")) thirdShots++;
        if (!name.endsWith("-clean.png")) return;
        pendingPairs.put(name, name.replace("-clean.png", "-a.png"));
    }

    private static final Map<String, String> pendingPairs = new LinkedHashMap<>();
    private static final Map<String, Integer> pendingPairAge = new LinkedHashMap<>();

    /** 配对求值：io 池先建文件后刷内容，读不出来就下一帧重试（有界，超限记段失败）。 */
    private static void evaluatePairs(Minecraft mc) {
        for (var iterator = pendingPairs.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            String clean = entry.getKey(), live = entry.getValue();
            var liveState = states.get(live);
            var cleanState = states.get(clean);
            if (liveState == null || cleanState == null) { iterator.remove(); fail("segment=" + segment + " pair state missing: " + live); continue; }
            var corners = shotCorners.get(live);
            int[] diff = readDiff(live, clean, corners, shotActorRects.get(live));
            if (diff == null) {
                int age = pendingPairAge.merge(clean, 1, Integer::sum);
                if (age > 240) { iterator.remove(); fail("segment=" + segment + " pair images never became readable: " + live); }
                continue;
            }
            iterator.remove(); pendingPairAge.remove(clean);
            pairs++;
            var verdict = VfxShotComparison.comparable(liveState, cleanState, corners);
            EldenCombat.LOGGER.info("VFX_PAIR_VERDICT live={} clean={} comparable={} reason={} liveLayer={} cleanLayer={}",
                    live, clean, verdict.comparable(), verdict.reason(), shotLayerOf(liveState), shotLayerOf(cleanState));
            if (!verdict.comparable()) { notComparables++; continue; }
            comparables++;
            String purpose = shotPurpose.getOrDefault(live, "");
            int inRoi = diff[0], outside = diff[1], roiPixels = diff[2];
            outsideWorst = Math.max(outsideWorst, outside);
            if (inRoi > subjectBest) subjectBest = inRoi;
            if (rois.length() < 600) {
                if (rois.length() > 0) rois.append(" | ");
                rois.append(String.format(Locale.ROOT, "%s inRoi=%d outside=%d roi=%d", purpose, inRoi, outside, roiPixels));
            }
            if (purpose.startsWith("primary")) primaryInRoi = Math.max(primaryInRoi, inRoi);
            if (purpose.startsWith("debris")) debrisInRoi = Math.max(debrisInRoi, inRoi);
            if (purpose.equals("suppressed")) { suppressedInRoi = Math.max(suppressedInRoi, inRoi); suppressedEvaluated = true; }
            if (purpose.equals("primary")) primaryEvaluated = true;
            if (purpose.equals("debris")) debrisEvaluated = true;
            int[] layerCounts = VfxDiagnosticLayers.counts();
            spriteTotal = layerCounts[0]; outlineTotal = layerCounts[1]; debrisTotal = layerCounts[2]; legacyTotal = layerCounts[3];
            EldenCombat.LOGGER.info("VFX_PAIR_DIFF live={} clean={} purpose={} art={} inRoi={} outside={} roiPixels={} threshold={} maxOutside={} layer=[{}]",
                    live, clean, purpose, shotArt.getOrDefault(live, "-"), inRoi, outside, roiPixels,
                    MIN_SUBJECT_PIXELS,
                    (int) (mc.getMainRenderTarget().width * (double) mc.getMainRenderTarget().height * MAX_OUTSIDE_FRACTION),
                    VfxDiagnosticLayers.summary());
        }
    }

    private static String shotLayerOf(VfxShotComparison.ShotState state) {
        return state == null ? "?" : state.layer();
    }

    /** 配对差分：ROI 取效果帧的包围盒投影，扣除施法者投影框；读图失败记段失败。 */
    private static int[] readDiff(String live, String clean, double[][] corners, int[] actorRect) {
        try {
            var liveState = states.get(live);
            int[] roi = VfxShotComparison.roiFromCorners(liveState, corners, ROI_MARGIN);
            File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
            var liveImage = ImageIO.read(new File(dir, live));
            var cleanImage = ImageIO.read(new File(dir, clean));
            if (liveImage == null || cleanImage == null) return null;
            int w = Math.min(liveImage.getWidth(), cleanImage.getWidth());
            int h = Math.min(liveImage.getHeight(), cleanImage.getHeight());
            int[] a = liveImage.getRGB(0, 0, w, h, null, 0, w);
            int[] b = cleanImage.getRGB(0, 0, w, h, null, 0, w);
            var diff = VfxShotComparison.diff(a, b, w, h, CHANNEL_DELTA, roi, actorRect);
            roiPixelsLast = diff.roiPixels();
            return new int[] { diff.inRoi(), diff.outside(), diff.roiPixels(), diff.changed() };
        } catch (Exception error) {
            // io 池先建文件后刷内容：读失败交给 evaluatePairs 的下一帧重试处理（有界）。
            return null;
        }
    }

    // ------------------------------------------------------------------ 相机与场景
    /**
     * 期望相机 = 当前步骤所需的相机（默认观察机位）。在 tick 侧设置：渲染帧只负责"改层/判定/截图"，
     * 相机切换不会因为"等相机收敛"的帧门禁而互相死锁。
     */
    private static void applyDesiredCamera(Minecraft mc) {
        Camera want = stepIndex < plan.size() ? plan.get(stepIndex).camera() : Camera.OBSERVER;
        applyCamera(mc, want);
    }

    private static void applyCamera(Minecraft mc, Camera kind) {
        if (kind == Camera.PLAYER_THIRD) {
            if (mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (observer != null && mc.getCameraEntity() != observer) mc.setCameraEntity(observer);
        }
    }

    /** 观察实体：客户端 ArmorStand，位置与朝向每 tick 钉死；只改相机，不动服务端技能与玩家。 */
    private static void pinObserver(Minecraft mc, Vantage vantage) {
        if (observer == null || observer.level() != mc.level) {
            observer = EntityType.ARMOR_STAND.create(mc.level);
            if (observer == null) return;
            observer.setInvisible(true);
            observer.setNoGravity(true);
            observer.setInvulnerable(true);
            observer.setSilent(true);
        }
        place(observer, vantage);
    }

    private static void moveObserver(Minecraft mc, Vantage vantage) {
        if (observer == null) return;
        place(observer, vantage);
    }

    private static void place(ArmorStand stand, Vantage vantage) {
        float[] aim = VfxCameraMath.aimAt(new Vec3(vantage.eyeX(), vantage.eyeY(), vantage.eyeZ()),
                new Vec3(vantage.aimX(), vantage.aimY(), vantage.aimZ()));
        stand.moveTo(vantage.eyeX(), vantage.eyeY() - stand.getEyeHeight(), vantage.eyeZ(), aim[0], aim[1]);
        stand.setYHeadRot(aim[0]);
        stand.yHeadRotO = aim[0];
        stand.yRotO = aim[0];
        stand.xRotO = aim[1];
        stand.setOldPosAndRot();
    }

    private static boolean cameraSettled(Minecraft mc) {
        var entity = mc.getCameraEntity();
        if (entity == null) return false;
        // 第三人称/普通游玩机位本来就在玩家身后，不能用"眼位差"判收敛；观察机位才需要等引擎眼高收敛。
        if (entity == mc.player) return true;
        double delta = entity.getEyePosition(1.0f).distanceTo(mc.gameRenderer.getMainCamera().getPosition());
        return delta <= 0.05;
    }

    private static void scene(Minecraft mc, Segment def, int index) {
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var p = server.getPlayerList().getPlayer(uuid);
            if (p == null) return;
            var level = p.serverLevel();
            SkillDriver.cancel(p, p.level().getGameTime(), "visual fixture reset");
            for (var e : level.getEntities().getAll())
                if (e instanceof SkillEffectEntity || e instanceof GoldenWaveEntity) e.discard();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.setDayTime(def.night() ? 18000 : 6000);
            // 每次整段重写同一块平整石台：清掉上一模式/上一段留下的任何墙或残留方块。
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 24; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
                for (int y = 81; y <= 87; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            p.connection.teleport(0, 81, 0, 0, 0);
            p.setNoGravity(false);
            p.setDeltaMovement(Vec3.ZERO);
            p.setHealth(p.getMaxHealth());
            if (!def.skill().isEmpty()) {
                var spec = SkillDriver.spec(def.skill());
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("eldencombat", spec.itemId()))));
                var fp = SkillServerEvents.fp(p);
                fp.setFp(fp.maxFp());
                fp.clearCooldown(def.skill());
            }
            EldenCombat.LOGGER.info("VFX_FIXED_SCENE {} {} night={}", index, def.skill().isEmpty() ? "(no cast)" : def.skill(), def.night());
        });
    }

    private static void input(Minecraft mc, String id, int frame) {
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer();
            var p = server.getPlayerList().getPlayer(uuid);
            if (p == null) return;
            var cast = SkillDriver.cast(p);
            long now = p.level().getGameTime();
            if (frame == CAST_FRAME || cast.followupOpen(now)) {
                var result = SkillCasting.request(p, now);
                EldenCombat.LOGGER.info("VFX_FIXED_INPUT {} frame={} result={}", id, frame, result);
            }
        });
    }

    // ------------------------------------------------------------------ 判据
    private static boolean needSatisfied(Minecraft mc, Need need) {
        return switch (need) {
            case ALIVE -> visibleBounds(mc) != null && targetIdentity(mc) != null;
            // 主体样本只在本帧"目标真的提交了主贴片/合法轮廓"时取（框架：像素对照样本必须有实际主贴片提交）。
            case PRIMARY -> visibleBounds(mc) != null && VfxDiagnosticLayers.submittedThisFrame();
            case DEBRIS -> visibleBounds(mc) != null && VfxDiagnosticLayers.debrisThisFrame();
            // "退场干净帧"：效果实体已消失，或者它已经完全离开这个固定机位的画面（飞出战技自然寿命的远端）。
            // 两者都保证 ROI 里没有目标像素；不延长寿命、不改追击窗口。
            case DEAD -> visibleBounds(mc) == null || (clearedTicks >= 3 && targetCleared(mc)) || ticks % FRAME_BUDGET >= POST_TICKS;
            case PRE -> ticks % FRAME_BUDGET >= SETTLE_TICKS && cleanFrames >= SETTLE_TICKS;
            case ANY -> true;
        };
    }

    /** 目标可视包围是否已经完全投影到画面之外（用于固定机位的"退场干净帧"）。 */
    private static boolean targetCleared(Minecraft mc) {
        AABB bounds = visibleBounds(mc);
        if (bounds == null) return true;
        if (pendingState == null) return false;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (double[] corner : VfxShotComparison.corners(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)) {
            double[] pixel = VfxShotComparison.projectWorld(pendingState, corner[0], corner[1], corner[2]);
            if (pixel == null) continue;
            minX = Math.min(minX, pixel[0]); maxX = Math.max(maxX, pixel[0]);
            minY = Math.min(minY, pixel[1]); maxY = Math.max(maxY, pixel[1]);
        }
        if (!Double.isFinite(minX) || !Double.isFinite(minY)) return true;
        double w = pendingState.fbWidth(), h = pendingState.fbHeight();
        return maxX < 0 || minX > w || maxY < 0 || minY > h;
    }

    private static void closeSegment(Minecraft mc, int index, Segment def) {
        int[] counts = VfxDiagnosticLayers.counts();
        if (!inflight.isEmpty()) fail("segment=" + index + " screenshots still in flight at segment end: " + inflight);
        if (def.negativeCamera()) { evaluateNegativeCamera(index); VfxDiagnosticLayers.reset(); return; }
        if (def.negativePrimary()) { evaluateNegativePrimary(index); VfxDiagnosticLayers.reset(); return; }
        double maxOutside = mc.getMainRenderTarget().width * (double) mc.getMainRenderTarget().height * MAX_OUTSIDE_FRACTION;
        if (comparables < 1) fail("segment=" + index + " " + def.skill() + ": no comparable live/clean pair (pairs=" + pairs + ", notComparable=" + notComparables + ")");
        if (preCleanShots < 1) fail("segment=" + index + " " + def.skill() + ": no pre-effect CLEAN reference frame landed");
        if (actionShots < 1) fail("segment=" + index + " " + def.skill() + ": no NORMAL external action frame (actor-visible) landed");
        if (postCleanShots < 1) fail("segment=" + index + " " + def.skill() + ": no post-effect CLEAN frame landed");
        if (subjectBest < MIN_SUBJECT_PIXELS) fail("segment=" + index + " " + def.skill()
                + ": best comparable primary ROI diff only " + subjectBest + " pixel(s) (need " + MIN_SUBJECT_PIXELS + ")");
        if (outsideWorst > maxOutside) fail("segment=" + index + " " + def.skill()
                + ": background drift outside the subject ROI reached " + outsideWorst + " pixel(s) (limit " + (int) maxOutside + ")");
        if (counts[3] != 0) fail("segment=" + index + " " + def.skill()
                + ": legacy compatibility path was drawn " + counts[3] + " time(s) although the authored route handled the frame");
        if (counts[0] + counts[1] < 1) fail("segment=" + index + " " + def.skill()
                + ": no real authored submission in the controlled group (sprite=" + counts[0] + " outline=" + counts[1] + ")");
        if (counts[0] < 1) EldenCombat.LOGGER.info("VFX_FIXED_NOTE segment={} {} drew outline-only frames (sprite=0 outline={})",
                index, def.skill(), counts[1]);
        if (def.debris() && counts[2] < 1) fail("segment=" + index + " " + def.skill() + ": debris layer produced no real submission");
        if (def.orbit() && thirdShots < 1) fail("segment=" + index + " " + def.skill() + ": no third-person orbiting sample landed");
        int backgroundChanged = checkBackgroundStability(index);
        if (backgroundChanged >= 0 && backgroundChanged > maxOutside)
            fail("segment=" + index + " " + def.skill() + ": pre/post CLEAN reference frames differ by " + backgroundChanged
                    + " pixel(s) outside the tested background budget (" + (int) maxOutside + "), so the controlled background is not stable");
        EldenCombat.LOGGER.info("VFX_FIXED_SEGMENT segment={} skill={} night={} steps={}/{} pairs={} comparable={} notComparable={} subjectBest={} outsideWorst={} roiPixels={} preClean={} postClean={} action={} third={} primaryInRoi={} debrisInRoi={} sprite={} outline={} debrisPieces={} legacy={} residual={} layer=[{}] rois=[{}]",
                index, def.skill(), def.night(), stepIndex, plan.size(), pairs, comparables, notComparables, subjectBest,
                outsideWorst, roiPixelsLast, preCleanShots, postCleanShots, actionShots, thirdShots, primaryInRoi, debrisInRoi,
                counts[0], counts[1], counts[2], counts[3], String.valueOf(targetIdentity(mc)), VfxDiagnosticLayers.summary(), rois);
        VfxDiagnosticLayers.reset();
    }

    private static void evaluateNegativeCamera(int index) {
        var first = states.get(String.format(Locale.ROOT, "vfxfixed-%02d-negative-camA-a.png", index));
        var second = states.get(String.format(Locale.ROOT, "vfxfixed-%02d-negative-camB-a.png", index));
        if (first == null || second == null) { fail("segment=" + index + " camera negative: both shots were not produced"); return; }
        double[][] corners = VfxShotComparison.corners(-1, 81, -1, 1, 82, 1);
        var verdict = VfxShotComparison.comparable(first, second, corners);
        negativeCameraOk = !verdict.comparable();
        EldenCombat.LOGGER.info("VFX_NEGATIVE_CAMERA segment={} comparable={} reason={} ok={} a=[{}] b=[{}]",
                index, verdict.comparable(), verdict.reason(), negativeCameraOk,
                VfxShotComparison.describe(first), VfxShotComparison.describe(second));
        if (!negativeCameraOk)
            fail("segment=" + index + " negative control: a camera-only pair without any target effect was accepted as comparable");
    }

    /**
     * 负例②：同一波/同机位下，把目标主贴片用诊断层抑制（CLEAN↔CLEAN）时主体门禁必须失败；
     * 同段再单独取碎块（DEBRIS_ONLY↔CLEAN）与正常主贴片（PRIMARY_ONLY↔CLEAN）作对照，
     * 证明"碎块/背景可见"不等于"主贴片可见"，且门禁对真实主贴片仍然灵敏。
     */
    private static void evaluateNegativePrimary(int index) {
        negativePrimaryOk = suppressedEvaluated && primaryEvaluated
                && suppressedInRoi < MIN_SUBJECT_PIXELS && primaryInRoi >= MIN_SUBJECT_PIXELS;
        EldenCombat.LOGGER.info("VFX_NEGATIVE_PRIMARY segment={} suppressedEvaluated={} suppressedInRoi={} primaryEvaluated={} primaryInRoi={} debrisEvaluated={} debrisInRoi={} min={} ok={}",
                index, suppressedEvaluated, suppressedInRoi, primaryEvaluated, primaryInRoi, debrisEvaluated, debrisInRoi,
                MIN_SUBJECT_PIXELS, negativePrimaryOk);
        if (!suppressedEvaluated)
            fail("segment=" + index + " negative control: the suppressed-primary pair was never evaluated");
        else if (suppressedInRoi >= MIN_SUBJECT_PIXELS)
            fail("segment=" + index + " negative control: the primary gate would have passed with the target primary suppressed ("
                    + suppressedInRoi + " px)");
        if (!primaryEvaluated || primaryInRoi < MIN_SUBJECT_PIXELS)
            fail("segment=" + index + " negative control: the positive control inside this segment showed no real primary ("
                    + primaryInRoi + " px)");
    }

    // ------------------------------------------------------------------ 收尾
    private static void finish(Minecraft mc) {
        if (!inflight.isEmpty()) {
            if (++waiting > 300) fail("screenshots still in flight at finish: " + inflight);
            else return;
        }
        var counts = VfxDiagnosticLayers.counts();
        if (failures.isEmpty()) {
            EldenCombat.LOGGER.info("VFX_FIXED_COMPLETE: {} controlled segments; fixed observer eye/orientation, layer-scoped probes, comparable-gated ROI diff, camera-only and primary-suppressed negatives", SEGMENT_COUNT);
            EldenCombat.LOGGER.info("VFX_SMOKE_COMPLETE: fixed observer fixture; {} segments; screenshots are fixture evidence, not manual acceptance", SEGMENT_COUNT);
        } else {
            for (String failure : failures) EldenCombat.LOGGER.error("VFX_SMOKE_FAIL: {}", failure);
            EldenCombat.LOGGER.error("VFX_SMOKE_INCOMPLETE: {} fixed-segment failure(s); no completion marker emitted", failures.size());
        }
        EldenCombat.LOGGER.info("VFX_FIXED_TOTAL segments={} shots={} legacy={} suppressed={}",
                SEGMENT_COUNT, landed.size(), counts[3], counts[4]);
        ticks = SEGMENT_COUNT * FRAME_BUDGET + 1;
        mc.stop();
    }

    /**
     * 背景稳定性：把本段的"拍摄前 CLEAN 参照"与"效果结束/退场后的 CLEAN 参照"逐像素比较。
     * 两张背景不一致（云/粒子/演员/资源变化）时这一组不能自动过关。返回全画面变化像素数；缺图返回 -1。
     */
    private static int checkBackgroundStability(int index) {
        String pre = String.format(Locale.ROOT, "vfxfixed-%02d-%s-pre-clean-a.png", index, segmentTag);
        String post = String.format(Locale.ROOT, "vfxfixed-%02d-%s-post-clean-a.png", index, segmentTag);
        var preState = states.get(pre);
        var postState = states.get(post);
        if (preState == null || postState == null) {
            EldenCombat.LOGGER.info("VFX_BACKGROUND_STABILITY segment={} pre={} post={} comparable=false reason=missing-state", index, pre, post);
            return -1;
        }
        var verdict = VfxShotComparison.comparable(preState, postState, VfxShotComparison.corners(-4, 80, -4, 4, 84, 20));
        try {
            File dir = new File(Minecraft.getInstance().gameDirectory, "screenshots");
            var preImage = ImageIO.read(new File(dir, pre));
            var postImage = ImageIO.read(new File(dir, post));
            if (preImage == null || postImage == null) return -1;
            int w = Math.min(preImage.getWidth(), postImage.getWidth());
            int h = Math.min(preImage.getHeight(), postImage.getHeight());
            int[] a = preImage.getRGB(0, 0, w, h, null, 0, w);
            int[] b = postImage.getRGB(0, 0, w, h, null, 0, w);
            // 参照帧相隔较久，施法者姿态/位移会变：两张的演员投影框都从背景稳定性里扣除，
            // 只考核"非演员背景"是否漂移；扣除面积进日志，便于判断这个检查还有多少覆盖面。
            // 扣除区域 = 本段所有取景时刻的**演员扫过区域并集**（离线分析证明漂移就集中在这块），
            // 阈值本身不动（2049 像素），所以这里没有放宽门禁，只是把"背景"定义成实测的静止区域。
            int[] exclude = unionRect(segmentActorHalo, inflateRect(unionRect(shotActorRects.get(pre), shotActorRects.get(post)), 24));
            var diff = VfxShotComparison.diff(a, b, w, h, CHANNEL_DELTA, null, exclude);
            // 注意：这里必须用已经**扣除演员扫过区域**的计数（roi=null 时 inRoi 就是"非演员区域的变化"），
            // 不能用 diff.changed()——那是全屏原始计数，会把演员自己的位移算成"背景漂移"，
            // 使这条注释里承诺的"只考核非演员背景"形同虚设（2026-10-17 查出的夹具缺陷）。
            int backgroundChanged = diff.inRoi();
            EldenCombat.LOGGER.info("VFX_BACKGROUND_STABILITY segment={} pre={} post={} comparable={} reason={} backgroundChanged={} rawChanged={} actorPixels={} total={} excludedPixels={} coveredPixels={} postWhileDead={}",
                    index, pre, post, verdict.comparable(), verdict.reason(), backgroundChanged, diff.changed(),
                    diff.changed() - backgroundChanged, diff.total(),
                    exclude == null ? 0 : Math.max(0, exclude[2] - exclude[0]) * Math.max(0, exclude[3] - exclude[1]),
                    diff.roiPixels(), postCleanWhileDead);
            return verdict.comparable() ? backgroundChanged : -1;
        } catch (Exception error) {
            return -1;
        }
    }

    private static void fail(String reason) { if (!failures.contains(reason)) failures.add(reason); }

    private static int[] unionRect(int[] a, int[] b) {
        if (a == null) return b;
        if (b == null) return a;
        return new int[] { Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3]) };
    }

    /** 扩张矩形：演员的落地阴影与手臂动作会超出实体投影框，参照帧比较时一并从背景里扣掉。 */
    private static int[] inflateRect(int[] rect, int margin) {
        if (rect == null) return null;
        return new int[] { rect[0] - margin, rect[1] - margin, rect[2] + margin, rect[3] + margin };
    }

    // ------------------------------------------------------------------ 现场读取助手
    /** 本段最显眼的效果身份（与渲染器身份约定一致），没有活效果时返回 null。 */
    private static String targetIdentity(Minecraft mc) {
        GoldenWaveEntity wave = null;
        SkillEffectEntity effect = null;
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof GoldenWaveEntity w && (wave == null || w.getId() < wave.getId())) wave = w;
            if (e instanceof SkillEffectEntity s && (effect == null || s.getId() < effect.getId())) effect = s;
        }
        if (wave != null) return "wave/" + wave.getId();
        if (effect != null) return "effect/" + effect.getId() + "/" + effect.phase();
        return null;
    }

    /** 本段活着的效果实体编号集合（诊断层的目标实例集合）。 */
    private static List<Integer> liveTargetIds(Minecraft mc) {
        var ids = new ArrayList<Integer>(4);
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof GoldenWaveEntity || e instanceof SkillEffectEntity) ids.add(e.getId());
        return ids;
    }

    /**
     * 当前目标的保守可视包围：波用实体的渲染剔除框；普通效果再叠上**作者平面几何**的保守立方
     * （作者 width/height/offset 是渲染真正覆盖的范围，只按实体物理盒取 ROI 会把美术算到 ROI 外）。
     * 没有活着的效果时返回 null。
     */
    private static AABB visibleBounds(Minecraft mc) {
        AABB box = null;
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof GoldenWaveEntity) {
                box = union(box, e.getBoundingBoxForCulling());
            } else if (e instanceof SkillEffectEntity effect) {
                box = union(box, effect.getBoundingBoxForCulling());
                var artwork = artworkOf(effect.kind());
                if (artwork != null) {
                    double spread = Math.max(artwork.width(), artwork.height()) * 0.75 + 1.5;
                    Vec3 anchor = effect.position().add(artwork.x(), artwork.y(), artwork.z());
                    box = union(box, new AABB(anchor.x - spread, anchor.y - spread, anchor.z - spread,
                            anchor.x + spread, anchor.y + spread, anchor.z + spread));
                }
            }
        }
        return box == null ? null : box.inflate(0.5);
    }

    private static com.dsh.eldencombat.effects.FxArtwork artworkOf(com.dsh.eldencombat.effects.SkillEffectMesh.Kind kind) {
        var routeId = com.dsh.eldencombat.effects.FxSlashRoute.routeId(kind);
        var declaration = routeId == null ? null : PresentationRoutes.route(routeId);
        return declaration == null ? null : declaration.artwork();
    }

    /** 施法者自身在屏幕上的投影框（含余量）：从主体 ROI 中扣除，玩家移动不得算成作者美术。 */
    private static int[] actorRect(Minecraft mc, VfxShotComparison.ShotState state, double inflate, int margin) {
        if (mc.player == null || state == null) return null;
        AABB box = mc.player.getBoundingBox().inflate(inflate);
        return VfxShotComparison.roiFromCorners(state,
                VfxShotComparison.corners(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ), margin);
    }

    /** 施法者自身在屏幕上的投影框（含余量）：从主体 ROI 中扣除，玩家移动不得算成作者美术。 */
    private static int[] actorRect(Minecraft mc, VfxShotComparison.ShotState state) {
        return actorRect(mc, state, 0.35, ACTOR_MARGIN);
    }

    /**
     * 背景稳定性专用：**演员扫过的屏幕区域**。
     *
     * <p>盒子比主体扣除更宽（含手持物的挥击弧线与地面影子），并在整段所有取景时刻取并集——
     * 显式的用法是"扣掉演员之后，背景还有没有漂移"。离线分析（`analyze_bg_drift.py`）证明
     * pre/post 参照帧之间的变化像素集中在一个约 160×145 的演员框内（均值 |Δ| 50–70，
     * 正是手臂/手持物/影子在两个不同站位之间的位移），所以这个并集是**实测出来的**，不是放宽阈值。
     */
    private static int[] actorSweptRect(Minecraft mc, VfxShotComparison.ShotState state) {
        if (mc.player == null || state == null) return null;
        AABB box = mc.player.getBoundingBox().inflate(1.2);
        return VfxShotComparison.roiFromCorners(state,
                VfxShotComparison.corners(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ), 48);
    }

    private static String cameraKind(Minecraft mc) {
        if (mc.getCameraEntity() == mc.player) return "player";
        if (mc.getCameraEntity() != null) return "observer";
        return "other";
    }

    private static String effectSummary(Minecraft mc) {
        var builder = new StringBuilder();
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof SkillEffectEntity || e instanceof GoldenWaveEntity) {
                if (builder.length() > 0) builder.append(" | ");
                builder.append(String.format(Locale.ROOT, "%s#%d pos=(%.2f,%.2f,%.2f) age=%d",
                        e instanceof GoldenWaveEntity ? "wave" : "effect", e.getId(), e.getX(), e.getY(), e.getZ(), e.tickCount));
            }
        }
        return builder.toString();
    }

    private static AABB union(AABB a, AABB b) {
        if (a == null) return b;
        if (b == null) return a;
        return new AABB(Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ));
    }
}
