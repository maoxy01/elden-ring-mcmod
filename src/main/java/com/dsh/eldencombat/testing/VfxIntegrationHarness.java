package com.dsh.eldencombat.testing;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.client.PresentationRoutes;
import com.dsh.eldencombat.effects.VfxCameraMath;
import com.dsh.eldencombat.server.skills.*;
import com.dsh.eldencombat.entity.skill.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3f;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Explicit isolated visual integration fixture; never runs in ordinary clients or servers.
 *
 * <p>VFX 运行收口夹具（<b>仅</b> {@code eldencombat.vfxSmoke} 时工作）：18 段 × 120 客户端 tick，
 * 8 个战技白天第一人称 + 夜间环绕第三人称。相机使用设计朝向对准"本次实际可见范围中心"，
 * 并用渲染帧里真实的 Camera 基向量做构图门禁；背向目标的负例必须判为不可见。
 * 截图等加载 overlay 消失后发起，并等异步落盘回执才继续/退出。
 */
@EventBusSubscriber(modid=EldenCombat.MODID,value=Dist.CLIENT)
public final class VfxIntegrationHarness {
    private static final boolean ENABLED=Boolean.getBoolean("eldencombat.vfxSmoke")
            &&!Boolean.getBoolean("eldencombat.vfxFixed")&&!Boolean.getBoolean("eldencombat.vfxLatch")
            &&!Boolean.getBoolean("eldencombat.vfxSemantics");
    /** 单段诊断开关：只跑 `-PvfxSegments=1 -PvfxFlame`/`-PvfxMissing` 时用，不改变默认 18 段语义。 */
    private static final boolean FLAME=Boolean.getBoolean("eldencombat.vfxFlame"), MISSING=Boolean.getBoolean("eldencombat.vfxMissing");
    private static final String[] IDS={"wave_of_gold","gold_breaker","destined_death","takers_flames","waterfowl_dance","starcaller_cry","bloodboon_ritual","cursed_blood_slice"};
    /** 段数可用 -D eldencombat.vfxSegments=N 覆盖，便于快速回归；默认 18。 */
    private static final int SEGMENTS=Integer.getInteger("eldencombat.vfxSegments",18);
    private static final int FRAME_BUDGET=120;
    /**
     * 每段"效果活着"的取景采样上限与最小间隔（tick）。固定帧号（20/44/68/92）会全部落在效果消失之后：
     * 实测 golden_wave 只在 tick 20–38 存在，四张固定帧截图因此一张也没拍到主体（见收口报告 §6）。
     */
    private static final int MAX_LIVE_SHOTS=4, MIN_LIVE_SHOT_SPACING=6, FIRST_LIVE_SHOT_FRAME=14;
    /** 效果消失后、相机仍停在锁定机位时补一张干净参考帧，用来与效果帧逐像素差分。 */
    private static final int CLEAN_MIN_DEAD_TICKS=3, CLEAN_MIN_GAP=6;
    /** 主体可见判据下限：效果帧与同机位干净帧的差异像素数（含少量云影移动，实测值见收口报告 §6）。 */
    private static final int MIN_SUBJECT_PIXELS=300;
    /** 像素差异阈值：任一通道差值达到该值才算"这一像素变了"，避免抗锯齿/云影噪声被算成主体。 */
    private static final int SUBJECT_CHANNEL_DELTA=24;
    /** 目标离眼位的最小水平观察距离：以施法者为中心的战技不能把相机逼成俯视脚下。 */
    private static final double MIN_VIEW_DISTANCE=3.0;
    /** 波的最小观察距离：波本身在飞行，太近的波要往外推才看得清整条走廊。 */
    private static final double WAVE_MIN_VIEW_DISTANCE=4.0;
    /** 相机对准目标时每段至少要有的有效门禁次数（短命效果也要有可读样本）。 */
    private static final int MIN_GATE_CHECKS=3;

    /** 反向负例窗口长度（tick）：太短会被截图等待冻住而错过，5 tick 足够覆盖多帧渲染。 */
    private static final int NEGATIVE_TICKS=5;
    /** 反向负例最早可以进入的帧：太早效果还没生成。实测 golden_wave 在 tick 20–38 存活，30 有足够余量。 */
    private static final int NEGATIVE_EARLIEST_FRAME=30;

    // ------------------------------------------------------------------ 隔离资源包真降级/恢复场景
    /** {@code -D eldencombat.vfxReload=true}：不进 18 段，改跑隔离资源包场景清单（框架 §7 第 2/3 行）。 */
    private static final boolean RELOAD=Boolean.getBoolean("eldencombat.vfxReload");
    private static final String PACK_MISSING_PNG="vfx-missing-png";
    private static final String PACK_MISSING_ATLAS="vfx-missing-atlas";
    private static final String PACK_BAD_JSON="vfx-bad-json";
    /** 1.21.1 客户端资源包格式；写进 pack.mcmeta 让隔离包被识别为兼容。 */
    private static final int PACK_FORMAT=34;
    /** 一次场景：用哪个隔离包、打哪个战技、期望哪条路由、期望 atlas 命中帧数与提交路径。 */
    private record ReloadStage(String pack,String skill,String routeId,String expectPath,int expectPresent,String note) {}
    private static final ReloadStage[] STAGES={
        new ReloadStage("","wave_of_gold","golden_wave","sprite",16,"对照：合法资源、全部 PNG 与 sprite 就位"),
        new ReloadStage(PACK_MISSING_PNG,"wave_of_gold","golden_wave","outline",15,"真缺 PNG：合法 JSON 把一帧指向不存在的 PNG"),
        new ReloadStage(PACK_MISSING_PNG,"waterfowl_dance","waterfowl","sprite",16,"对照路由不受缺图包影响"),
        new ReloadStage(PACK_MISSING_ATLAS,"waterfowl_dance","waterfowl","outline",0,"sprite 未缝入 particles atlas（合法 filter 排除）"),
        new ReloadStage(PACK_MISSING_ATLAS,"wave_of_gold","golden_wave","sprite",16,"对照路由不受 atlas 过滤影响"),
        new ReloadStage(PACK_BAD_JSON,"wave_of_gold","golden_wave","sprite",16,"非法 JSON：loader 保留上一份有效 Data 并记录错误"),
        new ReloadStage("","waterfowl_dance","waterfowl","sprite",16,"合法资源恢复并重载：作者帧与轮廓恢复"),
    };
    private static int appliedStage=-1;
    private static boolean packsWritten, stageSettled;
    private static CompletableFuture<Void> reloadFuture;
    private static int reloadWaits;
    private static long revisionAtStageStart;

    private static int stageCount() { return RELOAD?STAGES.length:SEGMENTS; }

    private static int skillIndex(String skill) {
        for(int i=0;i<IDS.length;i++) if(IDS[i].equals(skill)) return i;
        throw new IllegalArgumentException("unknown fixture skill: "+skill);
    }

    private static int ticks, waiting;
    /**
     * 设计朝向比引擎晚一 tick 生效：{@code Minecraft.runTick} 的顺序是"客户端 tick → Camera.setup → 渲染帧事件"，
     * 所以本 tick 设置的头 yaw 要等到下一个渲染帧才被 Camera 读到。夹具因此把计算出的朝向存起来，
     * 下一个 tick 才真正写入，让"渲染帧真实 Camera"与"本帧设计朝向"始终对应同一 tick。
     */
    private static float[] nextAim;             // 本 tick 写入的头朝向（保留字段名，见门禁的相位说明）
    /** 上一 tick 的设计朝向：引擎在 tick 与渲染之间存在一帧相位差，门禁允许相机匹配上一 tick 设计朝向。 */
    private static float[] aimThisTick, previousAim;
    private static String capture;              // 待发起的截图请求（渲染帧侧决策，决策即发起）
    private static String pendingShot;          // 已发起、等待落盘回执的截图
    private static final Set<String> savedShots=new LinkedHashSet<>();
    private static Vec3 sceneTarget=Vec3.ZERO;  // 本次施法落点的兜底目标
    private static GoldenWaveEntity lockedWave; // 本段首次出现的波：取景锚点锁定它，避免命中"哪一帧有哪条实体"
    private static GoldenWaveEntity cameraTargetWave;
    private static Vec3 lockedWaveTarget=Vec3.ZERO;
    /**
     * 本段锁定后的取景目标：段内只锁一次（相机稳定 + 效果存在时），此后 tick 与渲染帧门禁都用同一个点。
     * 逐帧重算目标会让"以施法者为中心"的效果把朝向推来推去，门禁读到的就是没有意义的摆动。
     */
    private static Vec3 segmentTarget;
    /**
     * 锁定点是否"跟随眼位"：锁定瞬间效果体积贴着眼位（以施法者为中心的战技）时，绝对点会被玩家的
     * 突进/位移甩到脚下 —— 实测段 4（waterfowl_dance 突进）目标水平距离掉到 0.3 格，设计俯仰被逼到 82°，
     * 真实相机对极陡俯仰跟不上（超差 26°）。跟随模式只保留"锁定时的方向 + 锁定时的高度 + 最小观察距离"，
     * 水平距离恒等于最小观察距离，俯仰自然有界。
     */
    private static boolean segmentFollows;
    private static double followDirX, followDirZ, followMin, followY;
    /** 俯仰随眼高自适应的上限：效果贴脚下/玩家被抬得很高时，真实相机对极陡俯仰会跟不上（实测超差 26°）。 */
    private static final double FOLLOW_PITCH_LIMIT=60.0;
    /** 源点水平方向的最小可信距离：源点贴着脚下时它的横向抖动会直接变成镜头抖动，此时只更新高度。 */
    private static final double FOLLOW_YAW_MIN_DISTANCE=1.5;
    /** 波在施法者脚下时不允许锁定取景：此时波的包围盒压在镜头下方投影出屏（实测会产出成片"不可见"）。 */
    private static final double WAVE_LOCK_MIN_TRAVEL=4.0;
    /** 波一直没走远时的兜底锁定帧，保证门禁仍有样本。 */
    private static final int LOCK_FALLBACK_FRAME=40;
    /** 段开始时已存在的最大效果实体 id：只有比它新的实体才算"本段新建"（避免锁到上一段残留）。 */
    private static int segmentBaselineId=-1;
    /**
     * {@code -D eldencombat.vfxVantage=true}：相机固定俯视施法者脚下的落点，用来判断"美术没画出来"还是
     * "第一人称平视相机没对到脚下的贴片"。施法者仍站在地面原处（悬空会让效果生成在相机身后）。
     */
    private static final boolean VANTAGE=Boolean.getBoolean("eldencombat.vfxVantage");
    private static final Vec3 VANTAGE_EYE=new Vec3(0.0,81.0,0.0);
    private static final Vec3 VANTAGE_TARGET=new Vec3(0.0,81.2,0.8);
    private static int cleanFrames, shotWait;
    /** 相机等眼高收敛的日志节流：只在"进入等待"时记一条，避免刷屏。 */
    private static boolean eyeWaitPending=true;

    // ---- 取景截图与"主体真的上屏"差分状态（每段重置） ----
    private static String currentId="";          // 本段战技 id（渲染帧侧的截图决策要用）
    private static int liveShots, lastLiveShotFrame=-999, deadTicks, compareWaits;
    /** 本段所有"效果活着"的截图：干净参考帧落地后逐张配对差分，取最大值作为主体可见证据。 */
    private static final List<String> liveShotNames=new ArrayList<>();
    private static String compareClean;          // 已落地的干净参考帧（等待读取比较）
    private static boolean cleanShotTaken;
    private static int subjectBest, subjectPairs;
    private static String subjectPair="";

    // ---- 构图门禁状态（每段重置） ----
    private static List<String> failures=new ArrayList<>();
    private static int gateChecks, gatePasses, gateInvisible, gateEnclosing, negativeChecks, negativeInvisible, negativeSkipped, segmentMaxAimError;
    /** 设计朝向连续"没有大拐弯"的 tick 数：只有翻转/施放那种大跳变才复位。1° 级抖动是位移造成的，
     *  把它当成不稳定会让移动型战技（水鸟乱舞）整段都拍不到图（实测段 12 因此零截图）。 */
    private static int designStableTicks, aimSkipped;
    private static final double DESIGN_STABLE_MAX=25.0;
    /** 反向负例窗口内冻结的设计朝向：窗口由"设计朝向本身翻转"触发，不冻结就永远等不到稳定样本。 */
    private static float[] negativeAim;
    private static int negativeFrame=-1;        // 反向相机负例窗口起点（-1=未安排）
    private static boolean negativeArmed;       // 本段是否成功进入过反向负例（进入即算武装）
    private static boolean nightSegment;        // 本段是否夜间（只影响现场光照，不改相机机位）
    private static int shotsAtSegmentStart;
    private static final StringBuilder gateSample=new StringBuilder();
    /** 失败帧单独留样：正向样本前三帧就把 gateSample 占满，失败帧会被挤掉（不够查）。 */
    private static final StringBuilder gateFails=new StringBuilder();

    private VfxIntegrationHarness() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null) {
            if(++waiting>2400) { EldenCombat.LOGGER.error("VFX_SMOKE_FAIL startup timeout"); mc.stop(); }
            return;
        }
        mc.options.pauseOnLostFocus=false;
        mc.options.hideGui=true;
        mc.options.fov().set(70);
        int segment=ticks/FRAME_BUDGET, frame=ticks%FRAME_BUDGET;
        if(segment>=stageCount()) { finish(mc); return; }
        // 资源包场景：每段先切隔离资源包并等重载真正落定（重载完成 + overlay 消失 + 干净渲染帧），
        // 落定前不推进 ticks，也就不会在旧资源/加载屏上施放或取景。
        if(RELOAD&&!reloadStageReady(mc,segment)) return;
        int skill=RELOAD?skillIndex(STAGES[segment].skill()):FLAME?3:(segment<16?segment%8:segment==16?0:4);
        // 段 8–15 用夜间第一人称再拍一遍八技。原 ArmorStand 环绕第三人称机位已移除：引擎相机
        // 插值使真实俯仰比夹具设定的机位落后 56–112°，不作为通过证据（见收口报告"未验项"）。
        boolean night=!RELOAD&&segment>=8&&segment<16;
        nightSegment=night;
        String id=IDS[skill];
        // 段 17 = 强制缺图快速诊断；-PvfxMissing 可让任一单段走同一条路径。
        System.setProperty("eldencombat.vfxSmokeMissing",String.valueOf(!RELOAD&&(segment==17||MISSING)));
        if(frame==0) {
            shotsAtSegmentStart=savedShots.size();
            negativeArmed=false;
            negativeFrame=-1;
            lockedWave=null;
            segmentTarget=null;
            segmentFollows=false;
            currentId=id;
            liveShots=0; lastLiveShotFrame=-999; deadTicks=0; cleanShotTaken=false;
            liveShotNames.clear(); compareClean=null; compareWaits=0;
            subjectBest=0; subjectPairs=0; subjectPair="";
            designStableTicks=0; aimSkipped=0;
            segmentBaselineId=newestEffectId(mc);
            scene(mc,id,night,segment);
        }
        if(frame>=10&&frame<90) input(mc,id,frame);
        // 效果不在场的连续 tick 数：干净参考帧只在主体确实退场后补拍，保证"效果帧 vs 干净帧"同机位。
        if(visibleBounds(mc)==null) deadTicks++; else deadTicks=0;

        // ---- 反向负例窗口：在"本 tick 设定朝向"之前就安排好，否则渲染帧门禁先看到新窗口、
        // 而本 tick 已按旧窗口设好朝向，会凭空出现一次 180° 的不一致判为"可见"。
        if(negativeFrame>=0&&frame>negativeFrame+NEGATIVE_TICKS-1) negativeFrame=-1;
        if(!negativeArmed&&negativeFrame<0&&frame>=NEGATIVE_EARLIEST_FRAME
                &&savedShots.size()>shotsAtSegmentStart&&visibleBounds(mc)!=null
                &&capture==null&&pendingShot==null) {
            negativeFrame=frame;
            negativeArmed=true;
            EldenCombat.LOGGER.info("VFX_CAMERA_NEGATIVE_ARM segment={} frame={} window={} ticks",segment,frame,NEGATIVE_TICKS);
        }

        // ---- 相机：真实 Camera 眼位/朝向为准（第一人称；ArmorStand 机位已移除，见上） ----
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        var cam=mc.gameRenderer.getMainCamera();
        Vec3 eye=cam.getPosition();
        if(lockedWave!=null&&!lockedWave.isAlive()) lockedWave=null;   // 波已走完：退回本次施法落点，不再追赶已消失实体
        GoldenWaveEntity wave=firstWave(mc);
        if(lockedWave==null&&wave!=null) {
            lockedWave=wave;
            cameraTargetWave=wave;
            lockedWaveTarget=wave.position().add(0.0,wave.getBbHeight()*0.5,0.0);
        } else if(lockedWave!=null&&lockedWave.isAlive()) {
            // 取景锚点跟随同一实例的波前；换波/消失时不再逐帧跳变到另一个目标。
            lockedWaveTarget=lockedWave.position().add(0.0,lockedWave.getBbHeight()*0.5,0.0);
        }
        {
            var p=mc.player;
            mc.setCameraEntity(p);
            refreshFollow(mc,eye);
            // vantage 模式：相机固定盯住施法落点，用来判断"美术没画出来"还是"相机没对到"。
            if(VANTAGE) segmentTarget=VANTAGE_TARGET;
            // 段内锁定后 tick 与门禁使用同一个取景点；未锁定前用即时目标（门禁此时也不判定）。
            Vec3 aimPoint=aimPoint(mc,eye);
            boolean backwardsNow=isBackwards(ticks);
            if(!backwardsNow) negativeAim=null;
            float[] want=designAim(mc,eye,backwardsNow);
            previousAim=aimThisTick;
            aimThisTick=want;
            designStableTicks=(previousAim!=null&&VfxCameraMath.angleBetween(previousAim[0],previousAim[1],want[0],want[1])<DESIGN_STABLE_MAX)
                    ?designStableTicks+1:0;
            p.setYRot(want[0]); p.yRotO=want[0]; p.setYHeadRot(want[0]); p.yHeadRotO=want[0];
            p.setXRot(want[1]); p.xRotO=want[1];
            if(frame%40==0)
                EldenCombat.LOGGER.info("VFX_AIM tick={} frame={} locked={} want=({},{}) back={} afterYaw={} afterHead={} eye=({},{},{}) target=({},{},{})",
                        ticks,frame,segmentTarget!=null,fmt(want[0]),fmt(want[1]),isBackwards(ticks),fmt(p.getYRot()),fmt(p.yHeadRot),
                        fmt(eye.x),fmt(eye.y),fmt(eye.z),fmt(aimPoint.x),fmt(aimPoint.y),fmt(aimPoint.z));
        }

        if(frame==115) {
            long count=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                    .filter(e->e instanceof SkillEffectEntity||e instanceof GoldenWaveEntity).count();
            EldenCombat.LOGGER.info("VFX_SMOKE_CLEANUP {} entities={}",id,count);
        }
        // 火墙的 lane 支撑/遮挡是客户端表现裁剪，这里只记录本次求解结果，不回写服务端。
        if(frame>=12&&frame<=40&&frame%4==0) {
            for(var e:mc.level.entitiesForRendering()) {
                if(e instanceof GoldenWaveEntity w) {
                    int[] counts=com.dsh.eldencombat.client.effects.WaveArtworkVisibility.counts(w);
                    EldenCombat.LOGGER.info("VFX_LANE segment={} frame={} wave={} flame={} age={} pos=({},{},{}) drawable={} unknown={} obstructed={}",
                            segment,frame,w.getId(),w.isFlame(),w.tickCount,fmt(w.getX()),fmt(w.getY()),fmt(w.getZ()),
                            counts[0],counts[1],counts[2]);
                }
            }
        }
        if(capture!=null||pendingShot!=null) {
            if(++shotWait>240) { fail("segment="+segment+" screenshot never landed: "+(pendingShot!=null?pendingShot:capture)); capture=null; pendingShot=null; }
            else return;   // 等截图落地再推进场景
        }
        shotWait=0;
        // 干净参考帧刚落地：读两张 PNG 做差分（PNG 可能还在刷盘，读失败就下一 tick 重试，全程有界）。
        if(compareClean!=null&&!runSubjectCompare(mc)) return;
        if(frame==FRAME_BUDGET-1) closeSegment(segment,id,night,savedShots.size()-shotsAtSegmentStart);
        ticks++;
    }

    /**
     * 效果帧 vs 同机位干净帧的逐像素差分：证明"作者美术真的上屏了"，而不是只提交了顶点。
     * 判据、阈值与实测值都进日志；读图失败或超时会被记成段失败，不会静默通过。
     */
    private static boolean runSubjectCompare(Minecraft mc) {
        String clean=compareClean;
        try {
            java.io.File dir=new java.io.File(mc.gameDirectory,"screenshots");
            var empty=javax.imageio.ImageIO.read(new java.io.File(dir,clean));
            if(empty==null) throw new java.io.IOException("unreadable clean PNG");
            int w=empty.getWidth(), h=empty.getHeight();
            int[] base=empty.getRGB(0,0,w,h,null,0,w);
            for(String liveName:liveShotNames) {
                var live=javax.imageio.ImageIO.read(new java.io.File(dir,liveName));
                if(live==null) throw new java.io.IOException("unreadable live PNG "+liveName);
                int lw=Math.min(live.getWidth(),w), lh=Math.min(live.getHeight(),h);
                int changed=0,total=0;
                for(int y=0;y<lh;y++) for(int x=0;x<lw;x++) {
                    int a=live.getRGB(x,y), b=base[y*w+x]; total++;
                    int dr=Math.abs((a>>16&255)-(b>>16&255)), dg=Math.abs((a>>8&255)-(b>>8&255)), db=Math.abs((a&255)-(b&255));
                    if(Math.max(dr,Math.max(dg,db))>=SUBJECT_CHANNEL_DELTA) changed++;
                }
                subjectPairs++;
                if(changed>subjectBest) { subjectBest=changed; subjectPair=liveName+" vs "+clean; }
                EldenCombat.LOGGER.info("VFX_SHOT_DIFF segment={} id={} live={} clean={} changed={} total={} threshold={}",
                        ticks/FRAME_BUDGET,currentId,liveName,clean,changed,total,MIN_SUBJECT_PIXELS);
            }
            compareClean=null; compareWaits=0;
            return true;
        } catch(Exception e) {
            if(++compareWaits>120) {
                fail("segment="+ticks/FRAME_BUDGET+" subject diff could not read the shot pair (clean "+clean+"): "+e);
                compareClean=null; compareWaits=0;
                return true;
            }
            return false;
        }
    }

    /** 渲染帧：加载 overlay 消失并完成有效渲染帧后做构图门禁与截图。 */
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(!ENABLED) return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null) return;
        if(mc.getOverlay()!=null) { cleanFrames=0; return; }
        cleanFrames++;
        if(RELOAD&&appliedStage!=ticks/FRAME_BUDGET) return;   // 重载场景还没落定：只累积干净帧，不做门禁
        if(cleanFrames<3) return;
        frameGate(mc);
        // 截图决策与发起放在同一次渲染帧回调里：决策时刻的实体状态就是即将被读出的那一帧，
        // 否则"决策时效果还在、落盘时已经消失"，拍出来的还是空帧（实测差 2 tick 就整帧错过主体）。
        if(capture==null&&pendingShot==null&&negativeFrame<0&&designStableTicks>=2) {
            int frame=ticks%FRAME_BUDGET;
            String name=null;
            if(liveShots<MAX_LIVE_SHOTS&&frame>=FIRST_LIVE_SHOT_FRAME&&frame-lastLiveShotFrame>=MIN_LIVE_SHOT_SPACING
                    &&segmentTarget!=null&&visibleBounds(mc)!=null) {
                name=String.format("vfx-%02d-%s-live%d-%03d.png",ticks/FRAME_BUDGET,currentId,liveShots+1,frame);
                liveShots++;
                lastLiveShotFrame=frame;
                cleanShotTaken=false;
            } else if(liveShots>0&&!cleanShotTaken&&!liveShotNames.isEmpty()&&deadTicks>=CLEAN_MIN_DEAD_TICKS
                    &&frame>=lastLiveShotFrame+CLEAN_MIN_GAP&&visibleBounds(mc)==null) {
                // 主体已退场、相机仍停在锁定机位：这一帧就是"效果帧"的干净参考帧。
                name=String.format("vfx-%02d-%s-clean-%03d.png",ticks/FRAME_BUDGET,currentId,frame);
                cleanShotTaken=true;
            }
            if(name!=null&&!savedShots.contains(name)) capture=name;
        }
        if(capture!=null&&pendingShot==null) {
            String name=capture;
            capture=null;
            pendingShot=name;
            // 证据帧必须真的对准设计朝向：这里再核一次，超差直接判段失败（不允许拿偏构图当证据）。
            double shotError=shotAimError(mc);
            if(shotError>15.0)
                fail("segment="+ticks/FRAME_BUDGET+" screenshot "+name+" was taken while the real camera was "
                        +fmt(shotError)+" degrees off the designed framing");
            logShotContext(mc,name);
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->pendingShot=null);
        }
        postStep(mc);
    }

    /** 截图帧的朝向一致性：真实相机与"本 tick/上一 tick 设计朝向"的最小夹角（与构图门禁同一约定）。 */
    private static double shotAimError(Minecraft mc) {
        var cam=mc.gameRenderer.getMainCamera();
        Vec3 eye=cam.getPosition();
        float[] want=designAim(mc,eye,isBackwards(ticks-1));
        double error=VfxCameraMath.angleBetween(want[0],want[1],cam.getYRot(),cam.getXRot());
        if(previousAim!=null)
            error=Math.min(error,VfxCameraMath.angleBetween(previousAim[0],previousAim[1],cam.getYRot(),cam.getXRot()));
        return error;
    }

    /**
     * 截图发起时刻的现场快照：真实相机眼位/朝向 + 活着的效果实体位置与相对镜头的夹角。
     * 用来把 PNG 与日志对齐，判断"没拍到效果"是取景问题还是提交/渲染问题（见收口报告 §6）。
     */
    private static void logShotContext(Minecraft mc,String name) {
        var cam=mc.gameRenderer.getMainCamera();
        Vec3 eye=cam.getPosition();
        Vec3 look=Vec3.directionFromRotation(cam.getXRot(),cam.getYRot());
        StringBuilder effects=new StringBuilder();
        int live=0;
        for(var e:mc.level.entitiesForRendering()) {
            if(!(e instanceof SkillEffectEntity)&&!(e instanceof GoldenWaveEntity)) continue;
            live++;
            Vec3 delta=e.position().add(0.0,e.getBbHeight()*0.5,0.0).subtract(eye);
            double dist=delta.length();
            double dot=dist<1.0E-6?Double.NaN:delta.dot(look)/dist;
            effects.append(String.format(java.util.Locale.ROOT," | %s#%d pos=(%.2f,%.2f,%.2f) dist=%.2f dot=%.3f age=%d",
                    e instanceof GoldenWaveEntity?"wave":"effect",e.getId(),e.getX(),e.getY(),e.getZ(),dist,dot,e.tickCount));
        }
        EldenCombat.LOGGER.info("VFX_SHOT_REQ name={} tick={} frame={} eye=({},{},{}) yaw={} pitch={} live={} effects=[{}]",
                name,ticks,ticks%FRAME_BUDGET,fmt(eye.x),fmt(eye.y),fmt(eye.z),fmt(cam.getYRot()),fmt(cam.getXRot()),
                live,effects.length()==0?"":effects.substring(3));
    }

    /**
     * 本 tick 的设计朝向。反向负例窗口内 {@code want} 在进入窗口时冻结一次：窗口的成因就是"设计朝向自己翻转"，
     * 逐 tick 重算会让设计朝向一直在动，既拿不到稳定样本，也让"真实相机是否跟到位"无从判定。
     */
    private static float[] designAim(Minecraft mc,Vec3 eye,boolean backwards) {
        if(backwards) {
            if(negativeAim==null) negativeAim=aimAngles(eye,aimPoint(mc,eye),true);
            return negativeAim;
        }
        return aimAngles(eye,aimPoint(mc,eye),false);
    }

    // ------------------------------------------------------------------ 构图门禁
    private static void frameGate(Minecraft mc) {
        AABB box=visibleBounds(mc);
        if(box==null) return;
        var cam=mc.gameRenderer.getMainCamera();
        Vec3 eye=cam.getPosition();
        // Camera.tick 对 eyeHeight 做平滑（每 tick 减半逼近），切换相机实体后要先等它稳定，
        // 否则拿实体眼位算的朝向与真实相机不一致——这既是夹具等待条件，也是构图门禁的前提。
        var cameraEntity=mc.getCameraEntity();
        double eyeDelta=cameraEntity==null?0.0:cameraEntity.getEyePosition(1.0f).distanceTo(eye);
        if(eyeDelta>0.25) {
            if(eyeWaitPending) {
                eyeWaitPending=false;
                EldenCombat.LOGGER.info("VFX_CAMERA_WAIT segment={} frame={} eyeDelta={} (waiting for engine eye-height convergence)",
                        ticks/FRAME_BUDGET,ticks%FRAME_BUDGET,fmt(eyeDelta));
            }
            return;
        }
        eyeWaitPending=true;
        // 段内取景目标只锁一次，条件是"相机稳定 + 出现本段新建的效果实体"：段边界上残留的上一段
        // 旧实体会把目标锁到错误坐标，之后门禁与相机就会一直对着空地（实测 60/120 帧不可见）。
        if(segmentTarget==null) {
            int frame=ticks%FRAME_BUDGET;
            GoldenWaveEntity liveWave=firstWave(mc);
            boolean waveReady=liveWave==null||liveWave.travelDistance()>=WAVE_LOCK_MIN_TRAVEL||frame>=LOCK_FALLBACK_FRAME;
            if(frame>=12&&newestEffectId(mc)>segmentBaselineId&&waveReady) {
                ViewTarget view=visibleTarget(mc);
                Vec3 point=viewTarget(view,eye,mc);
                double min=view.wave?WAVE_MIN_VIEW_DISTANCE:MIN_VIEW_DISTANCE;
                double horizontal=Math.hypot(view.point.x-eye.x,view.point.z-eye.z);
                segmentTarget=point;
                segmentFollows=horizontal<min;
                followMin=min;
                if(segmentFollows) setFollowDirection(mc,eye,view.point);
                EldenCombat.LOGGER.info("VFX_TARGET_LOCK segment={} frame={} target=({},{},{}) source=({},{},{}) horizontal={} min={} wave={} travel={} follows={}",
                        ticks/FRAME_BUDGET,frame,fmt(point.x),fmt(point.y),fmt(point.z),
                        fmt(view.point.x),fmt(view.point.y),fmt(view.point.z),fmt(horizontal),fmt(min),view.wave,
                        liveWave==null?"-":fmt(liveWave.travelDistance()),segmentFollows);
                return;   // 本帧只完成锁定，不计入门禁
            }
            return;
        }
        double aspect=(double)mc.getWindow().getWidth()/(double)Math.max(1,mc.getWindow().getHeight());
        double fov=mc.options.fov().get();
        Vector3f forward=cam.getLookVector(), left=cam.getLeftVector(), up=cam.getUpVector();
        double[] extent=VfxCameraMath.ndcExtent(eye,forward,left,up,box,fov,aspect);
        // 可见判据用"包围盒与视锥是否真的相交"（半空间 p-vertex），不用角点投影：角点全在屏幕外但画面被
        // 长条/大体积盖满时，角点判定会漏报（框架 §3 要求的是"可视包围投影与屏幕有非零交集"）。
        boolean encloses=VfxCameraMath.eyeInside(eye,box);
        boolean visible=VfxCameraMath.intersectsFrustum(eye,forward,left,up,box,fov,aspect,VfxCameraMath.NEAR,VfxCameraMath.FAR);
        // 第一人称：本帧 Camera 读到的是"上一 tick 写入"的那份朝向（引擎 runTick 的顺序是
        // tick → Camera.setup → 渲染帧事件），所以窗口判定与朝向核对都回退一 tick。
        boolean backwards=isBackwards(ticks-1);
        Vec3 target=aimPoint(mc,eye);
        float[] want=designAim(mc,eye,backwards);
        double aimError=VfxCameraMath.angleBetween(want[0],want[1],cam.getYRot(),cam.getXRot());
        // 引擎在 "客户端 tick → Camera.setup → 渲染帧事件" 之间存在一帧相位差：真实相机可能还停在
        // 上一 tick 的设计朝向上。只要匹配任一相邻 tick 的设计朝向即算一致，其余情况必须报错。
        double phaseError=previousAim==null?Double.MAX_VALUE:
                VfxCameraMath.angleBetween(previousAim[0],previousAim[1],cam.getYRot(),cam.getXRot());
        double acceptedError=Math.min(aimError,phaseError);
        if(backwards) {
            if(VfxCameraMath.eyeInside(eye,box)) { negativeSkipped++; }
            else if(acceptedError>15.0) {
                // 翻转过渡帧：真实相机还没转到位（或已转回），这一帧既不是正向样本也不是有效负例。
                // 计入会凭空产出"反向也可见"的假失败（实测段 7 因此报了 1 次）。
                negativeSkipped++;
            }
            else {
                negativeChecks++;
                // 反向负例要证明"朝向反了就不算可见"：除角点投影外，再用"包围盒中心必须在眼位前方"
                // 的严格判据——以施法者为中心的效果体积几乎包住眼位，只看角点会把它误判成可见。
                boolean visibleStrict=VfxCameraMath.intersectsScreen(extent,true);
                if(!visibleStrict) negativeInvisible++;
                Vec3 boxCentre=box.getCenter();
                EldenCombat.LOGGER.info("VFX_CAMERA_NEGATIVE segment={} frame={} eye=({},{},{}) yaw={} pitch={} target=({},{},{}) centre=({},{},{}) near={} front={} onScreen={} centreDepth={} visible={} visibleStrict={}",
                        ticks/FRAME_BUDGET,ticks%FRAME_BUDGET,fmt(eye.x),fmt(eye.y),fmt(eye.z),fmt(cam.getYRot()),fmt(cam.getXRot()),
                        fmt(target.x),fmt(target.y),fmt(target.z),fmt(boxCentre.x),fmt(boxCentre.y),fmt(boxCentre.z),
                        fmt(extent[4]),(int)extent[5],(int)extent[6],fmt(extent[7]),visible,visibleStrict);
            }
            return;
        }
        gateChecks++;
        if(encloses) gateEnclosing++;
        if(visible) gatePasses++;
        else {
            gateInvisible++;
            // 失败样本优先占位，便于从日志直接看出"哪一帧、什么朝向、投影到哪"。
            if(gateFails.length()<900) gateFails.append(gateFails.length()==0?"":" | ").append(String.format(
                    "INVISIBLE f%d eye=(%s,%s,%s) eyeDelta=%s yaw=%s pitch=%s target=(%s,%s,%s) near=%s ndc=[%s,%s,%s,%s] front=%d onScreen=%d aimErr=%s",
                    ticks%FRAME_BUDGET,fmt(eye.x),fmt(eye.y),fmt(eye.z),fmt(eyeDelta),fmt(cam.getYRot()),fmt(cam.getXRot()),
                    fmt(target.x),fmt(target.y),fmt(target.z),fmt(extent[4]),fmt(extent[0]),fmt(extent[1]),fmt(extent[2]),fmt(extent[3]),
                    (int)extent[5],(int)extent[6],fmt(acceptedError)));
        }
        // 真实相机与设计朝向不符时必须留下可查记录。翻转/施放造成的"过渡帧"（设计朝向本身在变，
        // 相机一帧内跟不完）不计入段判据，但计入 aimSkipped 并全部落日志，避免把过渡帧算成"朝向没生效"。
        if(acceptedError>10.0)
            EldenCombat.LOGGER.error("VFX_CAMERA_MISMATCH tick={} frame={} backwards={} negFrame={} camYaw={} camPitch={} wantYaw={} wantPitch={} aimError={} phaseError={} stable={} playerYaw={} playerHead={} viewY={} camEntity={}",
                    ticks,ticks%FRAME_BUDGET,backwards,negativeFrame,fmt(cam.getYRot()),fmt(cam.getXRot()),fmt(want[0]),fmt(want[1]),fmt(aimError),fmt(phaseError),designStableTicks,
                    mc.player==null?"-":fmt(mc.player.getYRot()),mc.player==null?"-":fmt(mc.player.yHeadRot),
                    mc.player==null?"-":fmt(mc.player.getViewYRot(1.0f)),
                    mc.getCameraEntity()==null?"null":(mc.getCameraEntity()==mc.player?"player":"other"));
        if(designStableTicks>=2) segmentMaxAimError=Math.max(segmentMaxAimError,(int)Math.ceil(acceptedError));
        else aimSkipped++;
        if(gateSample.length()<400) gateSample.append(gateSample.length()==0?"":" | ").append(String.format(
                "f%d eye=(%s,%s,%s) eyeDelta=%s yaw=%s pitch=%s target=(%s,%s,%s) near=%s ndc=[%s,%s,%s,%s] front=%d onScreen=%d aimErr=%s visible=%s",
                ticks%FRAME_BUDGET,fmt(eye.x),fmt(eye.y),fmt(eye.z),fmt(eyeDelta),fmt(cam.getYRot()),fmt(cam.getXRot()),
                fmt(target.x),fmt(target.y),fmt(target.z),fmt(extent[4]),fmt(extent[0]),fmt(extent[1]),fmt(extent[2]),fmt(extent[3]),
                (int)extent[5],(int)extent[6],fmt(acceptedError),visible));
    }

    private static void closeSegment(int segment,String id,boolean night,int shots) {
        boolean negativeOk=negativeChecks==0||negativeInvisible==negativeChecks;
        if(gateInvisible>0)
            fail("segment="+segment+" camera was aimed at the effect centre but the projected bounds missed the screen in "+gateInvisible+" rendered frame(s)");
        if(gatePasses-gateEnclosing<MIN_GATE_CHECKS)
            fail("segment="+segment+" only "+(gatePasses-gateEnclosing)+" readable framing checks passed (need "+MIN_GATE_CHECKS+", enclosing="+gateEnclosing+")");
        if(shots<1) fail("segment="+segment+" no screenshot landed");
        if(liveShots<1) fail("segment="+segment+" no screenshot was taken while the effect existed (effect frame missing)");
        if(!RELOAD) {
            // 本组是"随玩家移动的第一人称 NORMAL 组"：这里只保留原始像素读数作参考，
            // **不**作为"作者主体可见"的通过判据（跨机位取最大值不能归因；相机状态未受控）。
            // 受控归因证据在 VfxFixedHarness（固定观察机位 + 分层取样 + 严格配对）。
            EldenCombat.LOGGER.info("VFX_SUBJECT_EVIDENCE mode=NORMAL segment={} id={} pairs={} best={} claim=none reason=camera-state-not-controlled",
                    segment,id,subjectPairs,subjectBest);
        }
        if(!negativeArmed)
            fail("segment="+segment+" never entered the backwards-camera negative window (no live effect while a shot was pending)");
        if(segmentMaxAimError>15)
            fail("segment="+segment+" real camera rotation differs from the aimed head yaw by up to "+segmentMaxAimError+" degrees");
        if(!negativeOk) fail("segment="+segment+" backwards-camera negative control reported the effect visible "+ (negativeChecks-negativeInvisible) +" time(s)");
        if(RELOAD) {
            // 场景判据：atlas 命中数与期望一致（16 = 作者帧直绘；<16 = 声明保留但该路由整体走轮廓回退）。
            ReloadStage stage=STAGES[segment];
            Minecraft mc=Minecraft.getInstance();
            int[] counts=mc==null?new int[]{-1,-1}:routeAtlasCounts(mc,stage.routeId());
            boolean matches=counts[0]==16&&counts[1]==stage.expectPresent();
            EldenCombat.LOGGER.info("VFX_RELOAD_CHECK segment={} pack={} route={} declared={} present={} expected={} expectPath={} revision={} ok={}",
                    segment,stage.pack().isEmpty()?"(none)":stage.pack(),stage.routeId(),counts[0],counts[1],stage.expectPresent(),
                    stage.expectPath(),PresentationRoutes.revision(),matches);
            if(!matches)
                fail("reload segment="+segment+" route "+stage.routeId()+" declared/present "+counts[0]+"/"+counts[1]
                        +" != expected 16/"+stage.expectPresent());
        }
        EldenCombat.LOGGER.info("VFX_SMOKE_SEGMENT segment={} id={} night={} gatePasses={} enclosing={} invisible={} negative={}/{} skipped={} armed={} shots={} liveShots={} subjectPairs={} subjectBest={} maxAimError={} aimSkipped={} sample=[{}] fails=[{}]",
                segment,id,night,gatePasses,gateEnclosing,gateInvisible,negativeInvisible,negativeChecks,negativeSkipped,negativeArmed,shots,
                liveShots,subjectPairs,subjectBest,segmentMaxAimError,aimSkipped,gateSample,gateFails);
        gateChecks=0; gatePasses=0; gateInvisible=0; gateEnclosing=0; negativeChecks=0; negativeInvisible=0; negativeSkipped=0; segmentMaxAimError=0;
        gateSample.setLength(0); gateFails.setLength(0);
    }

    private static void finish(Minecraft mc) {
        if(capture!=null||pendingShot!=null) { if(++shotWait>240) fail("pending screenshot at finish"); else return; }
        if(failures.isEmpty()) {
            EldenCombat.LOGGER.info("VFX_SMOKE_COMPLETE: {} segments; real-camera eye/head-yaw framing gate, backwards negative control and bounded captures; screenshots are fixture evidence, not manual acceptance",stageCount());
        } else {
            for(String failure:failures) EldenCombat.LOGGER.error("VFX_SMOKE_FAIL: {}",failure);
            EldenCombat.LOGGER.error("VFX_SMOKE_INCOMPLETE: {} framing/capture failure(s); no completion marker emitted",failures.size());
        }
        ticks=stageCount()*FRAME_BUDGET+1;
        mc.stop();
    }

    private static void fail(String reason) { if(!failures.contains(reason)) failures.add(reason); }

    // ------------------------------------------------------------------ 场景与输入（保持原夹具语义）
    private static void scene(Minecraft mc,String id,boolean night,int segment) {
        var uuid=mc.player.getUUID();
        sceneTarget=new Vec3(0,82,0);
        mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(p==null)return;
            var level=p.serverLevel();
            SkillDriver.cancel(p,p.level().getGameTime(),"visual fixture reset");
            for(var e:level.getEntities().getAll()) if(e instanceof SkillEffectEntity||e instanceof GoldenWaveEntity)e.discard();
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);
            level.setDayTime(night?18000:6000);
            int far=FLAME?40:24;
            for(int x=-12;x<=12;x++)for(int z=-12;z<=far;z++) {
                level.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState());
                for(int y=81;y<=87;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                if(segment==16&&x>0&&z==5)for(int y=81;y<84;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
            }
            // 火墙单段：在传播方向正前方竖一道 3 格高的墙，让中间 lane 的扫掠必须判定遮挡。
            if(FLAME) for(int x=-12;x<=-2;x++)for(int y=81;y<84;y++)level.setBlockAndUpdate(new BlockPos(x,y,6),Blocks.STONE.defaultBlockState());
            p.connection.teleport(VANTAGE_EYE.x,VANTAGE_EYE.y,VANTAGE_EYE.z,0,15);
            p.setNoGravity(VANTAGE);
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.setHealth(p.getMaxHealth());
            var spec=SkillDriver.spec(id);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("eldencombat",spec.itemId()))));
            var fp=SkillServerEvents.fp(p);fp.setFp(fp.maxFp());fp.clearCooldown(id);
            EldenCombat.LOGGER.info("VFX_SMOKE_SCENE {} {} night={}",segment,id,night);
        });
    }

    private static void input(Minecraft mc,String id,int frame) {
        var uuid=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);if(p==null)return;
            var cast=SkillDriver.cast(p);long now=p.level().getGameTime();
            if(frame==10||cast.followupOpen(now)) {
                var result=SkillCasting.request(p,now);
                EldenCombat.LOGGER.info("VFX_SMOKE_INPUT {} frame={} result={}",id,frame,result);
            }
        });
    }

    // ------------------------------------------------------------------ 相机瞄准
    private static float[] aimAngles(Vec3 eye,Vec3 target,boolean backwards) {
        float[] aim=VfxCameraMath.aimAt(eye,target);
        if(!backwards) return aim;
        float yaw=aim[0]+180.0f;
        while(yaw>180.0f)yaw-=360.0f;
        while(yaw<-180.0f)yaw+=360.0f;
        return new float[]{yaw,-aim[1]};
    }

    /**
     * 本帧/本 tick 的取景点：未锁定时用即时目标；锁定后按 {@link #segmentFollows} 决定取"绝对点"还是
     * "锁定水平方向 + 锁定时源点高度 + 距眼位 min"的点。后者让俯仰随眼高自适应（玩家被抬起时自动更俯视），
     * 水平方向恒定，真实相机暴露的面永远一致。tick 与渲染帧门禁必须共用它。
     */
    private static Vec3 aimPoint(Minecraft mc,Vec3 eye) {
        if(segmentTarget==null) return viewTarget(mc,eye);
        if(!segmentFollows) return segmentTarget;
        double pitch=Math.atan2(eye.y-followY,followMin);
        double limit=Math.toRadians(FOLLOW_PITCH_LIMIT);
        pitch=Math.max(-limit,Math.min(limit,pitch));
        double cos=Math.cos(pitch);
        return new Vec3(eye.x+followDirX*cos*followMin,eye.y-Math.sin(pitch)*followMin,eye.z+followDirZ*cos*followMin);
    }

    /** 锁定时的水平方向：眼位→源点的水平分量；退化为零时取效果自身的传播方向（波 dirX/dirZ）。 */
    private static void setFollowDirection(Minecraft mc,Vec3 eye,Vec3 source) {
        Vec3 delta=source.subtract(eye);
        double horizontal=Math.hypot(delta.x,delta.z);
        Vec3 dir=horizontal<1.0E-6?pushDirection(mc):new Vec3(delta.x/horizontal,0.0,delta.z/horizontal);
        followDirX=dir.x; followDirZ=dir.z; followY=source.y;
    }

    /**
     * 跟随模式下每 tick 刷新一次（渲染帧门禁只读不刷，保证同一 tick 里 tick 与门禁看到同一个取景点）。
     *
     * <p>高度始终跟着当前效果体积：水鸟乱舞会把玩家抬起/突进，固定高度会把地面效果压到画面下方
     * （实测段 4/段 5 各有 3/9 帧"不可见"）。水平方向只在源点明显偏离镜头轴时更新，避免贴脚下时抖动。
     */
    private static void refreshFollow(Minecraft mc,Vec3 eye) {
        if(!segmentFollows) return;
        if(visibleBounds(mc)==null) return;
        Vec3 source=visibleTarget(mc).point;
        Vec3 delta=source.subtract(eye);
        double horizontal=Math.hypot(delta.x,delta.z);
        followY=source.y;
        if(horizontal>=FOLLOW_YAW_MIN_DISTANCE) {
            followDirX=delta.x/horizontal; followDirZ=delta.z/horizontal;
        }
    }

    // ------------------------------------------------------------------ 隔离资源包场景实现
    /**
     * 每段的资源包切换 + 重载等待。返回 true 表示资源已落定，可以施放/取景/截图。
     *
     * <p>落定条件同时要求"重载 future 完成"和"加载 overlay 消失且已过若干有效渲染帧"（复用 R1 对重载截图的
     * 要求）；等待期间不推进 {@code ticks}，所以不会在旧资源或加载屏上施放。
     */
    private static boolean reloadStageReady(Minecraft mc,int segment) {
        if(appliedStage!=segment) {
            writePacks(mc);
            ReloadStage stage=STAGES[segment];
            revisionAtStageStart=PresentationRoutes.revision();
            applyPack(mc,stage.pack());
            reloadWaits=0;
            stageSettled=false;
            appliedStage=segment;
            reloadFuture=mc.reloadResourcePacks();
            EldenCombat.LOGGER.info("VFX_RELOAD_STAGE segment={} pack={} skill={} route={} expectPath={} expectPresent={} note={} revisionBefore={}",
                    segment,stage.pack().isEmpty()?"(none)":stage.pack(),stage.skill(),stage.routeId(),stage.expectPath(),
                    stage.expectPresent(),stage.note(),revisionAtStageStart);
            return false;
        }
        if(reloadFuture!=null&&!reloadFuture.isDone()) {
            if(++reloadWaits>1200) fail("reload segment="+segment+" resource reload never completed");
            return false;
        }
        if(mc.getOverlay()!=null||cleanFrames<3) return false;
        if(!stageSettled) {
            stageSettled=true;
            int[] counts=routeAtlasCounts(mc,STAGES[segment].routeId());
            EldenCombat.LOGGER.info("VFX_RELOAD_SETTLED segment={} pack={} revision={} (before {}), route={} declared={} present={} expected={} selected={}",
                    segment,STAGES[segment].pack().isEmpty()?"(none)":STAGES[segment].pack(),PresentationRoutes.revision(),revisionAtStageStart,
                    STAGES[segment].routeId(),counts[0],counts[1],STAGES[segment].expectPresent(),
                    mc.getResourcePackRepository().getSelectedIds());
        }
        return true;
    }

    /** 只写一次三个隔离资源包：合法缺 PNG、合法 atlas 过滤、非法 JSON。坏包不进入正式资源与锁。 */
    private static void writePacks(Minecraft mc) {
        if(packsWritten) return;
        packsWritten=true;
        try {
            Path root=Path.of(mc.gameDirectory.toURI()).resolve("resourcepacks");
            String manifest=readResource(mc,"eldencombat:routes/2d.json");
            String brokenFrames=manifest.replace("\"eldencombat:gold_wave/frame_00\"","\"eldencombat:gold_wave/frame_00_absent\"");
            if(brokenFrames.equals(manifest)) fail("reload scenario: could not point a golden_wave frame at a missing PNG");
            // 合法 JSON 指向不存在的 PNG：声明仍是 16 帧，PNG 不存在 → loader 记 missing PNG，该路由整体走轮廓回退。
            writePack(root,PACK_MISSING_PNG,Map.of("assets/eldencombat/routes/2d.json",brokenFrames));
            // 合法 atlas 定义：保留 vanilla 目录源，再加一条 filter 把 waterfowl 的 sprite 排除在 particles atlas 之外。
            String atlas="{\n  \"sources\": [\n    {\"type\": \"directory\", \"source\": \"particle\", \"prefix\": \"\"},\n"
                    +"    {\"type\": \"filter\", \"pattern\": {\"namespace\": \"eldencombat\", \"path\": \"waterfowl/.*\"}}\n  ]\n}\n";
            writePack(root,PACK_MISSING_ATLAS,Map.of("assets/minecraft/atlases/particles.json",atlas));
            // 非法 JSON：截断 + 非法片段 → loader 必须保留上一份有效 Data，且不当作缺 PNG。
            writePack(root,PACK_BAD_JSON,Map.of("assets/eldencombat/routes/2d.json",
                    manifest.substring(0,Math.min(600,manifest.length()))+"\n{\"fx\": {\"routes\": {\"golden_wave\": [\n"));
        } catch(Exception error) {
            fail("reload scenario: writing isolated packs failed: "+error);
        }
    }

    private static void writePack(Path root,String name,Map<String,String> files) throws Exception {
        Path dir=root.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":"+PACK_FORMAT+",\"description\":\"eldencombat vfx isolation probe ("+name+"); removed before exit\"}}\n");
        for(var entry:files.entrySet()) {
            Path target=dir.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target,entry.getValue());
        }
    }

    private static String readResource(Minecraft mc,String id) throws Exception {
        var resource=mc.getResourceManager().getResourceOrThrow(ResourceLocation.parse(id));
        try(var reader=resource.openAsReader()) {
            var writer=new StringWriter();
            reader.transferTo(writer);
            return writer.toString();
        }
    }

    /** 选择本场景需要的隔离包：先清掉三个探针包，再按场景加入；坏包退出前会被清掉。 */
    private static void applyPack(Minecraft mc,String pack) {
        try {
            var repository=mc.getResourcePackRepository();
            repository.reload();
            List<String> selected=new ArrayList<>(repository.getSelectedIds());
            selected.removeIf(id->id.startsWith("file/"+PACK_MISSING_PNG)||id.startsWith("file/"+PACK_MISSING_ATLAS)
                    ||id.startsWith("file/"+PACK_BAD_JSON));
            if(!pack.isEmpty()) {
                String id="file/"+pack;
                if(!repository.getAvailableIds().contains(id)) fail("reload scenario: isolated pack "+id+" was not discovered");
                selected.add(id);
            }
            repository.setSelected(selected);
        } catch(Exception error) {
            fail("reload scenario: selecting the isolated pack failed: "+error);
        }
    }

    /** 声明帧数与真正落在 particles atlas 里的帧数（与渲染器 loaded() 同判据，夹具独立复算一遍）。 */
    private static int[] routeAtlasCounts(Minecraft mc,String routeId) {
        List<ResourceLocation> declared=PresentationRoutes.routeFrames(routeId);
        var texture=mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if(!(texture instanceof TextureAtlas atlas)) return new int[]{declared.size(),0};
        int present=0;
        for(var frame:declared) if(atlas.getSprite(frame).contents().name().equals(frame)) present++;
        return new int[]{declared.size(),present};
    }

    /** 本段基线：段开始时现有效果实体的最大 id，"比它新"才算本段新建的效果。 */
    private static int newestEffectId(Minecraft mc) {        int max=-1;
        for(var e:mc.level.entitiesForRendering())
            if(e instanceof SkillEffectEntity||e instanceof GoldenWaveEntity) max=Math.max(max,e.getId());
        return max;
    }

    /** 本段最早出现的波实体（id 最小），无波时返回 null。 */
    private static GoldenWaveEntity firstWave(Minecraft mc) {        GoldenWaveEntity wave=null;
        for(var e:mc.level.entitiesForRendering())
            if(e instanceof GoldenWaveEntity w&&(wave==null||w.getId()<wave.getId())) wave=w;
        return wave;
    }

    /**
     * 本次实际可见范围中心：本段锁定过的波，其次当前跟随效果实体，最后本次施法落点。
     * 波的位置一旦锁定就不再逐帧跟随——短命波让目标在 ±travel 之间跳变时，镜头会每帧翻 180°。
     * 波额外带上"离眼位太近"的标记，由 {@link #viewTarget} 决定是否外推。
     */
    private static final class ViewTarget {
        final Vec3 point;
        final boolean wave;
        ViewTarget(Vec3 point,boolean wave) { this.point=point; this.wave=wave; }
    }

    private static ViewTarget visibleTarget(Minecraft mc) {
        if(lockedWave!=null&&lockedWave.isAlive()) return new ViewTarget(lockedWaveTarget,true);
        // 渲染帧可能比本 tick 的 tick 处理更早看到新波（波在 tick 与渲染帧之间生成），所以这里直接再查一次。
        GoldenWaveEntity wave=firstWave(mc);
        if(wave!=null) return new ViewTarget(wave.position().add(0.0,wave.getBbHeight()*0.5,0.0),true);
        SkillEffectEntity effect=null;
        for(var e:mc.level.entitiesForRendering())
            if(e instanceof SkillEffectEntity s&&(effect==null||s.getId()<effect.getId())) effect=s;
        if(effect!=null) return new ViewTarget(effect.position().add(0.0,effect.getBbHeight()*0.5,0.0),false);
        // 兜底：既不认识实体实例、又确实存在效果体积时，用保守可视包围的中心（避免落回施法原点）。
        AABB box=visibleBounds(mc);
        if(box!=null) return new ViewTarget(box.getCenter(),false);
        return new ViewTarget(sceneTarget,false);
    }

    /**
     * 取景目标 = 本次可见范围中心，离眼位过近时沿<b>效果自身的传播方向</b>（见 {@link #pushDirection}）
     * 外推到最小观察距离之外。以施法者自身为中心的效果起点就在眼位，直接照原样瞄准会退化成"俯视脚下"
     * （pitch 夹到 89°）；外推只改变取景朝向，不改变效果实体的真实位置。
     */
    private static Vec3 viewTarget(Minecraft mc,Vec3 eye) {
        return viewTarget(visibleTarget(mc),eye,mc);
    }
    private static Vec3 viewTarget(ViewTarget target,Vec3 eye,Minecraft mc) {
        double min=target.wave?WAVE_MIN_VIEW_DISTANCE:MIN_VIEW_DISTANCE;
        Vec3 flat=new Vec3(target.point.x-eye.x,0.0,target.point.z-eye.z);
        double distance=flat.length();
        if(distance>=min&&distance>1.0E-4) return target.point;
        Vec3 dir=pushDirection(mc);
        return new Vec3(eye.x+dir.x*min,target.point.y,eye.z+dir.z*min);
    }

    /**
     * 外推方向：只用"效果自身的传播方向"，不用相机朝向。
     *
     * <p>用相机自己的 lookAngle 当兜底方向会形成自我反馈：本 tick 的朝向由目标决定，而目标又由上一 tick
     * 写下的朝向决定，一旦目标与眼位水平重合就会锁死在任意一个方向上（实测段 0 锁到反向 (0,82,-3)）。
     * 波的 dirX/dirZ 是权威传播方向；普通效果没有方向时用现场固定朝向（场景里玩家以 yaw=0 即 +Z 落位）。
     */
    private static Vec3 pushDirection(Minecraft mc) {
        GoldenWaveEntity wave=lockedWave!=null&&lockedWave.isAlive()?lockedWave:firstWave(mc);
        if(wave!=null) {
            double dx=wave.dirX(), dz=wave.dirZ();
            double length=Math.hypot(dx,dz);
            if(length>1.0E-4) return new Vec3(dx/length,0.0,dz/length);
        }
        return new Vec3(0.0,0.0,1.0);
    }

    /** 当前效果实体的保守可视包围；没有活着的效果时返回 null（不产生门禁记录）。 */
    private static AABB visibleBounds(Minecraft mc) {
        AABB box=null;
        for(var e:mc.level.entitiesForRendering()) {
            if(e instanceof SkillEffectEntity||e instanceof GoldenWaveEntity) box=union(box,e.getBoundingBox());
        }
        return box==null?null:box.inflate(1.0);
    }

    /** 反向负例窗口是否生效（窗口由"已出图 + 有活效果"触发，见 tick）。 */
    private static boolean isBackwards(int tick) {
        return negativeFrame>=0&&tick%FRAME_BUDGET>=negativeFrame&&tick%FRAME_BUDGET<=negativeFrame+NEGATIVE_TICKS-1;
    }

    /**
     * 截图落盘回执在 {@code Util.ioPool()} 上异步返回，夹具必须等回执才能继续推进段。
     * 用 {@code savedShots.add} 的返回值判重：重复回执不会把同一张图算两次。
     */
    /**
     * 截图落盘回执在 {@code Util.ioPool()} 上异步返回，夹具必须等文件真正落到"游戏目录/screenshots"下才能
     * 继续推进段（{@code Screenshot._grab} 会把文件名拼到 {@code screenshots} 子目录，不是游戏目录根）。
     * 判重同时看 {@link #savedShots}，重复回执不会把同一张图算两次。
     */
    private static void postStep(Minecraft mc) {
        String waiting=pendingShot;
        if(waiting!=null&&new java.io.File(new java.io.File(mc.gameDirectory,"screenshots"),waiting).isFile()) {
            pendingShot=null;
            if(savedShots.add(waiting)) {
                EldenCombat.LOGGER.info("VFX_SHOT_LANDED {} shots={}",waiting,savedShots.size());
                if(waiting.contains("-live")) liveShotNames.add(waiting);
                else if(waiting.contains("-clean")) compareClean=waiting;
            }
        }
    }

    private static AABB union(AABB a,AABB b) {
        if(a==null) return b;
        return new AABB(Math.min(a.minX,b.minX),Math.min(a.minY,b.minY),Math.min(a.minZ,b.minZ),
                Math.max(a.maxX,b.maxX),Math.max(a.maxY,b.maxY),Math.max(a.maxZ,b.maxZ));
    }

    private static String fmt(double value) { return String.format(java.util.Locale.ROOT,"%.3f",value); }
}
