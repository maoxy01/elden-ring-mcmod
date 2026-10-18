package com.dsh.eldencombat.client.effects;

import com.dsh.eldencombat.client.PresentationRoutes;
import com.dsh.eldencombat.effects.FxArtwork;
import com.dsh.eldencombat.effects.VfxDrawPlan;
import com.dsh.eldencombat.effects.WaveFront;
import com.dsh.eldencombat.entity.skill.GoldenWaveEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 火墙的纯客户端表现遮挡求解：只算"这个 lane 有没有可支撑的地面、有没有被地形挡住"，
 * 不提交顶点、不拥有战技时间线、不做命中判定，也绝不回写服务端的 DATA_BLOCKED。
 *
 * <p>求解节奏：客户端每个 tick 由 {@link #beginClientTick()} / {@link #endClientTick()} 括起来，
 * 期间对每个火墙调用一次 {@link #solve(GoldenWaveEntity)}；渲染帧只读 {@link #drawable}。
 * 缓存按"波形实例 + 维度 + 资源代数"隔离并保留插入序，实体移除/维度切换/重载后重新求解，
 * 新波不继承旧锁存。
 *
 * <p><b>lane 索引约定</b>：内部数组一律用**原始 lane ID**（0..15，见 {@link FxArtwork.Lane#id()}）作下标。
 * 过滤后的列表序号在服务端 mask 非零时与原始 ID 不同，绝不能拿来查状态或位掩码。
 *
 * <p><b>碰撞判据</b>：上一有效支撑点 → 当前支撑点之间用原版连续 {@code level.clip(ClipContext)} 扫掠一次，
 * 命中处的障碍柱顶（有界向上扫描列）比低侧支撑面高出超过 {@link VfxDrawPlan#LATCH_MIN_RISE} 才锁存遮挡；
 * 旧实现的"0.5 格步进点 + 0.05 薄盒"采样会漏掉薄 Collider，且会把 3 格实墙当成"抬高的合法支撑"。
 */
public final class WaveArtworkVisibility {
    /** 地面射线窗口：与 ArtworkRenderer 现有 1.5 上 / 2.5 下保持一致。 */
    private static final double GROUND_UP = 1.5, GROUND_DOWN = 2.5;
    /** 连续扫掠线段抬离支撑面的高度（格）：贴着支撑面走，避免起点落在支撑面体内。 */
    private static final double SWEEP_LIFT = 0.05;
    /** 命中列向上扫描的格数上限（有界，不加载区块）：3~6 格高的墙都能取到真实墙顶。 */
    private static final int MAX_COLUMN_SCAN = 6;
    /** 冷启动历史重建上限：每 lane 至多 8 步、每步 ≤1 格，每客户端 tick 只推进一步。 */
    private static final int HISTORY_MAX_STEPS = 8, HISTORY_STEP = 1;
    /** 历史重建的收敛距离（格）：与旧的 0.5 步长约定位一致，小于该距离即认为已追上当前中心。 */
    private static final double HISTORY_SNAP = 0.5;
    /** 每客户端 tick 的 clip 次数上限：16 lane ×（1 次支撑 + 1 次扫掠）= 32。 */
    public static final int CLIPS_PER_TICK_LIMIT = 32;
    /** 每客户端 tick 增量处理的 lane 数：8 个 lane ≤ 16 次 clip，稳稳落在 32 次预算之内。 */
    public static final int LANES_PER_TICK = 8;
    /** 缓存条目上限：超出时先清理最旧的，绝不随游玩无限增长。 */
    private static final int MAX_ENTRIES = 64;

    private static final Map<Key, State> CACHE = new LinkedHashMap<>();
    private static int clipsThisTick;
    /** 夹具/诊断用的客户端 tick 序号：每 lane 用它判"本 tick 是否已求解"。 */
    private static long clientTick;

    private record Key(GoldenWaveEntity wave, String dimension, long generation) {}

    /** 一个 lane 的本波状态；obstructed 一旦置位就在本波内锁存（跨墙不闪回）。 */
    private static final class Lane {
        int id = -1;
        boolean held;                   // 上一 tick 是否已有有效支撑
        double lastGroundY, lastLaneX, lastLaneZ;
        double historyX, historyZ;      // 冷启动重建用的当前位置
        boolean historyReady;
        int historyProgress;
        boolean unknown;                // 未加载/历史过长：UNKNOWN，不当作永久撞墙
        String unknownReason = "";
        boolean obstructed;             // 本波已判定被挡：锁存隐藏
        double obstructedAtY = Double.NaN;
        String obstructedBy = "";
        boolean drawable;               // 上一 tick 的最终判定（渲染帧只读这个）
        boolean ready;                  // 本 tick 的待发布判定，由 endClientTick 翻转
        boolean pendingUnknown;
        long solvedTick = Long.MIN_VALUE;   // 本客户端 tick 是否已完成支撑+扫掠（避免重复消耗 clip）
        // 本 tick 现场快照：只读诊断按历程序列读出"上一/当前支撑与扫掠段"，不用聚合计数代替状态链。
        boolean probeValid;
        double probeX, probeZ, probeGroundY;
    }

    private static final class State {
        final Lane[] lanes = new Lane[WaveFront.LANES];   // 下标 = 原始 lane ID
        final ArrayList<Integer> present = new ArrayList<>();   // 本 tick 参与求解的原始 ID
        int cursor;                     // 增量求解游标：每 tick 至多处理 LANES_PER_TICK 个 lane
        double baseX, baseY, baseZ, dirX, dirZ, travel;   // 本 tick 的求解上下文
        Key key;
    }

    private WaveArtworkVisibility() {}

    /** 每个客户端 tick 开头调用一次：清空本 tick 的 clip 计数与"本 tick 已求解"标记。 */
    public static void beginClientTick() {
        clipsThisTick = 0;
        clientTick++;
    }

    /**
     * 客户端 tick 结束调用：把本 tick 的待发布判定翻转为渲染帧读取的快照。
     * 渲染帧读的是"上一个完整 tick"的结果，不依赖"渲染发生在 solve 之后"这种顺序假设。
     */
    public static void endClientTick() {
        for (State state : CACHE.values())
            for (int id : state.present) {
                Lane lane = state.lanes[id];
                if (lane == null) continue;
                lane.unknown = lane.pendingUnknown;
                lane.drawable = lane.ready && !lane.pendingUnknown && !lane.obstructed;
            }
    }

    /** 求解一个火墙的本 tick lane 状态：每 tick 至多处理 {@link #LANES_PER_TICK} 个 lane，其余沿用上次判定。 */
    public static void solve(GoldenWaveEntity wave) {
        if (wave == null || wave.level() == null || !wave.isFlame()) return;
        State state = stateFor(wave);
        float radius = wave.visualRadius(1.0f), travel = wave.visualTravel(1.0f);
        List<FxArtwork.Lane> lanes = FxArtwork.lanes(radius, travel, wave.blockedLanes(), true);
        state.present.clear();
        for (FxArtwork.Lane lane : lanes) state.present.add(lane.id());
        if (lanes.isEmpty()) {
            state.cursor = 0;
            return;
        }
        state.baseX = wave.xo;
        state.baseY = wave.yo;
        state.baseZ = wave.zo;
        state.travel = travel;
        double[] dir = normalized(wave.dirX(), wave.dirZ());
        state.dirX = dir[0];
        state.dirZ = dir[1];
        // 每 tick 只推进一个有界窗口：总 clip 数因此每 tick ≤ LANES_PER_TICK × 2，不会把后面的 lane 都拖成 UNKNOWN。
        int count = lanes.size();
        for (int processed = 0; processed < Math.min(LANES_PER_TICK, count); processed++) {
            int slot = (state.cursor + processed) % count;
            FxArtwork.Lane lane = lanes.get(slot);
            Lane entry = state.lanes[lane.id()];
            if (entry == null) entry = state.lanes[lane.id()] = new Lane();
            entry.id = lane.id();
            Result result = solveLane(wave, state, entry, lane);
            if (result == Result.BUDGET) break;
        }
        state.cursor = (state.cursor + LANES_PER_TICK) % count;
    }

    private enum Result { SOLVED, UNKNOWN, BUDGET }

    /** 单个 lane 的支撑 + 连续扫掠；未加载/MISS 记 UNKNOWN（不当永久撞墙），预算耗尽则本 tick 放弃。 */
    private static Result solveLane(GoldenWaveEntity wave, State state, Lane entry, FxArtwork.Lane lane) {
        // 同一客户端 tick 内只求解一次：重复调用（渲染器 + 夹具）不再重复消耗 clip 预算。
        if (entry.solvedTick == clientTick) return Result.SOLVED;
        entry.ready = false;
        entry.pendingUnknown = false;
        entry.unknownReason = "";
        entry.probeValid = false;
        double[] lateralAxis = { state.dirZ, -state.dirX };
        double centerX = state.baseX + lateralAxis[0] * lane.center() + state.dirX * lane.forward();
        double centerZ = state.baseZ + lateralAxis[1] * lane.center() + state.dirZ * lane.forward();
        // 冷启动：第一次同步没有历史时，用 base - dir*visualTravel 恢复发射点，按有限步长追赶。
        if (!entry.historyReady) {
            entry.historyX = state.baseX - state.dirX * state.travel + lateralAxis[0] * lane.center();
            entry.historyZ = state.baseZ - state.dirZ * state.travel + lateralAxis[1] * lane.center();
            entry.historyProgress = 0;
            entry.historyReady = true;
        }
        for (int step = 0; step < HISTORY_MAX_STEPS; step++) {
            double dx = centerX - entry.historyX, dz = centerZ - entry.historyZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance <= HISTORY_SNAP) break;
            double move = Math.min(HISTORY_STEP, distance);
            entry.historyX += dx / distance * move;
            entry.historyZ += dz / distance * move;
            entry.historyProgress++;
            if (entry.historyProgress >= HISTORY_MAX_STEPS) {
                // 历史过长/无法重建：保守跳过并记录 UNKNOWN，不做长距离无限扫描。
                entry.pendingUnknown = true;
                entry.unknownReason = "history-too-long";
                entry.solvedTick = clientTick;
                return Result.UNKNOWN;
            }
        }
        // 1) 支撑：向上 1.5 / 向下 2.5，必须命中 COLLIDER；未加载/MISS 都记 UNKNOWN 并跳过该 lane。
        Double ground = groundY(wave, centerX, state.baseY, centerZ, entry);
        if (ground == null) {
            entry.pendingUnknown = true;
            entry.held = false;
            if (clipsThisTick > CLIPS_PER_TICK_LIMIT) return Result.BUDGET;
            entry.solvedTick = clientTick;
            return Result.UNKNOWN;
        }
        entry.probeValid = true;
        entry.probeX = centerX;
        entry.probeZ = centerZ;
        entry.probeGroundY = ground;
        // 2) 扫掠：上一有效支撑点到当前支撑点，一次原版连续 COLLIDER clip；命中列顶抬升超过阈值才锁存遮挡。
        if (entry.held) {
            SweepHit hit = sweep(wave, entry.lastLaneX, entry.lastGroundY, entry.lastLaneZ, centerX, ground, centerZ);
            if (hit != null) {
                double surfaceY = columnTop(wave, hit.pos(), hit.location().y);
                if (VfxDrawPlan.blocksLane(surfaceY, Math.min(entry.lastGroundY, ground), VfxDrawPlan.LATCH_MIN_RISE)) {
                    entry.obstructed = true;
                    entry.obstructedAtY = surfaceY;
                    entry.obstructedBy = hit.pos().getX() + "," + hit.pos().getY() + "," + hit.pos().getZ()
                            + " hit=(" + String.format(Locale.ROOT, "%.3f,%.3f,%.3f", hit.location().x, hit.location().y, hit.location().z)
                            + ") shapeTop=" + String.format(Locale.ROOT, "%.3f", surfaceY);
                }
            }
            if (clipsThisTick > CLIPS_PER_TICK_LIMIT) return Result.BUDGET;
        }
        entry.held = true;
        entry.lastGroundY = ground;
        entry.lastLaneX = centerX;
        entry.lastLaneZ = centerZ;
        entry.ready = true;
        entry.solvedTick = clientTick;
        return Result.SOLVED;
    }

    /** 渲染帧只读该波该 lane 的缓存：未知、未支撑、已锁存遮挡都不画。laneId 是原始 lane ID。 */
    public static boolean drawable(GoldenWaveEntity wave, int laneId) {
        Key key = keyFor(wave);
        State state = key == null ? null : CACHE.get(key);
        if (state == null || laneId < 0 || laneId >= state.lanes.length || state.present.isEmpty()) return false;
        Lane lane = state.lanes[laneId];
        return lane != null && lane.drawable;
    }

    /**
     * 客户端隐藏位集（按**原始 lane ID**）：服务端已挡、本帧没有状态、未支撑/UNKNOWN/锁存遮挡的 lane 都在内。
     * 这是主贴片、轮廓与兼容 wave 唯一的客户端可见约束来源，避免各分支各查一次得到矛盾结果。
     */
    public static int obscuredMask(GoldenWaveEntity wave) {
        Key key = keyFor(wave);
        State state = key == null ? null : CACHE.get(key);
        int mask = 0xFFFF;
        if (state == null) return mask;
        for (int id = 0; id < state.lanes.length; id++) {
            Lane lane = state.lanes[id];
            if (lane != null && lane.drawable) mask &= ~(1 << id);
        }
        return mask & 0xFFFF;
    }

    /** 单个原始 lane ID 是否允许提交（渲染帧只读；服务端 mask 仍是硬约束）。 */
    public static boolean clientVisible(GoldenWaveEntity wave, int laneId) {
        if (wave == null || laneId < 0 || laneId >= WaveFront.LANES) return false;
        if ((wave.blockedLanes() & (1 << laneId)) != 0) return false;
        return (obscuredMask(wave) & (1 << laneId)) == 0;
    }

    /** 诊断用：本波的开通/未知/遮挡 lane 数（聚合摘要，不替代逐 lane 状态链）。 */
    public static int[] counts(GoldenWaveEntity wave) {
        Key key = keyFor(wave);
        State state = key == null ? null : CACHE.get(key);
        if (state == null) return new int[] { 0, 0, 0 };
        int drawable = 0, unknown = 0, obstructed = 0;
        for (int id : state.present) {
            Lane lane = state.lanes[id];
            if (lane == null) continue;
            if (lane.drawable) drawable++;
            if (lane.unknown) unknown++;
            if (lane.obstructed) obstructed++;
        }
        return new int[] { drawable, unknown, obstructed };
    }

    /** 本波本 tick 的开通 lane 清单（**原始 lane ID**，与 ArtworkRenderer 共用同一份 lane 数据）。 */
    public static List<Integer> openLanes(GoldenWaveEntity wave) {
        Key key = keyFor(wave);
        State state = key == null ? null : CACHE.get(key);
        if (state == null) return List.of();
        var open = new ArrayList<Integer>();
        for (int id : state.present) {
            Lane lane = state.lanes[id];
            if (lane != null && lane.drawable) open.add(id);
        }
        return List.copyOf(open);
    }

    /**
     * 只读逐 lane 状态链（同一 wave UUID/维度/资源代数、原始 lane ID、客户端 tick）：
     * 上一/当前有效支撑、held、UNKNOWN 原因、锁存遮挡及其命中列、最终 drawable。
     * 供夹具把"支撑→真实碰撞→锁存→越墙仍隐藏"整条链落进日志，聚合 counts 只作摘要。
     */
    public record LaneState(int id, boolean present, boolean held, boolean unknown, String unknownReason,
                            boolean obstructed, boolean drawable, double lastGroundY,
                            boolean probeValid, double probeX, double probeZ, double probeGroundY,
                            double obstructedAtY, String obstructedBy) {}

    public static List<LaneState> laneStates(GoldenWaveEntity wave) {
        Key key = keyFor(wave);
        State state = key == null ? null : CACHE.get(key);
        if (state == null) return List.of();
        var out = new ArrayList<LaneState>(state.present.size());
        for (int id : state.present) {
            Lane lane = state.lanes[id];
            if (lane == null) {
                out.add(new LaneState(id, true, false, true, "no-state", false, false, Double.NaN,
                        false, Double.NaN, Double.NaN, Double.NaN, Double.NaN, ""));
                continue;
            }
            out.add(new LaneState(id, true, lane.held, lane.unknown, lane.unknownReason, lane.obstructed, lane.drawable,
                    lane.lastGroundY, lane.probeValid, lane.probeX, lane.probeZ, lane.probeGroundY,
                    lane.obstructedAtY, lane.obstructedBy));
        }
        return List.copyOf(out);
    }

    /** 地面射线；未加载 / MISS / 预算耗尽都返回 null（不造 STONE 落点，不跨虚空找地面），并记录原因。 */
    private static Double groundY(GoldenWaveEntity wave, double x, double y, double z, Lane entry) {
        Vec3 from = new Vec3(x, y + GROUND_UP, z);
        Vec3 to = new Vec3(x, y - GROUND_DOWN, z);
        if (!wave.level().hasChunkAt(BlockPos.containing(from)) || !wave.level().hasChunkAt(BlockPos.containing(to))) {
            entry.unknownReason = "from-unloaded";
            diag(wave, "from-unloaded", x, y, z, null, null);
            return null;
        }
        if (++clipsThisTick > CLIPS_PER_TICK_LIMIT) {
            entry.unknownReason = "budget";
            diag(wave, "budget", x, y, z, null, null);
            return null;
        }
        HitResult hit = wave.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wave));
        if (hit.getType() != HitResult.Type.BLOCK) {
            entry.unknownReason = "miss";
            diag(wave, "miss", x, y, z, null, null);
            return null;
        }
        return hit.getLocation().y;
    }

    private static final java.util.Set<String> DIAG = new java.util.HashSet<>();

    /** 仅 {@code eldencombat.vfxSmoke} 下、每种原因各记一条，便于定位"为什么这个 lane 未知"。 */
    private static void diag(GoldenWaveEntity wave, String reason, double x, double y, double z, Object hit, Object ground) {
        if (!Boolean.getBoolean("eldencombat.vfxSmoke")) return;
        if (DIAG.size() > 8 || !DIAG.add(reason)) return;
        com.dsh.eldencombat.EldenCombat.LOGGER.info("VFX_LANE_DIAG reason={} base=({},{},{}) probe=({},{},{}) clips={}",
                reason, wave.xo, wave.yo, wave.zo, x, y, z, clipsThisTick);
    }

    /**
     * 连续扫掠：从上一有效支撑点到当前支撑点做**一次**原版 COLLIDER clip（线段贴着各自的支撑面）。
     * 命中的是障碍本体（薄 Collider 也不会被步长漏掉），障碍高度由 {@link #columnTop} 单独取。
     */
    private static SweepHit sweep(GoldenWaveEntity wave, double x0, double y0, double z0,
                                  double x1, double y1, double z1) {
        if (clipsThisTick >= CLIPS_PER_TICK_LIMIT) return null;
        clipsThisTick++;
        Vec3 from = new Vec3(x0, y0 + SWEEP_LIFT, z0);
        Vec3 to = new Vec3(x1, y1 + SWEEP_LIFT, z1);
        HitResult raw = wave.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, wave));
        if (!(raw instanceof BlockHitResult hit)) return null;
        BlockPos pos = hit.getBlockPos();
        if (!wave.level().hasChunkAt(pos)) return null;
        return new SweepHit(pos, hit.getLocation());
    }

    private record SweepHit(BlockPos pos, Vec3 location) {}

    /**
     * 命中点所在**竖直列**的碰撞顶面（有界向上扫描，最多 {@link #MAX_COLUMN_SCAN} 格）。
     * 只用方块状态与碰撞形状，不再发射射线、不加载区块；3 格实墙得到真实墙顶，而不是命中那一层的顶面。
     */
    private static double columnTop(GoldenWaveEntity wave, BlockPos hitPos, double fallback) {
        double top = fallback;
        for (int dy = 0; dy <= MAX_COLUMN_SCAN; dy++) {
            BlockPos pos = hitPos.above(dy);
            if (!wave.level().hasChunkAt(pos)) break;
            VoxelShape shape = wave.level().getBlockState(pos).getCollisionShape(wave.level(), pos);
            if (shape.isEmpty()) break;
            AABB bounds = shape.bounds();
            top = Math.max(top, bounds.maxY + pos.getY());
        }
        return top;
    }

    private static double[] normalized(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        if (!(length > 1.0E-6)) return new double[] { 0, 1 };
        return new double[] { x / length, z / length };
    }

    private static Key keyFor(GoldenWaveEntity wave) {
        if (wave == null || wave.level() == null) return null;
        return new Key(wave, wave.level().dimension().location().toString(), PresentationRoutes.revision());
    }

    private static State stateFor(GoldenWaveEntity wave) {
        Key key = keyFor(wave);
        State state = CACHE.get(key);
        if (state == null) {
            while (CACHE.size() >= MAX_ENTRIES) {
                var oldest = CACHE.keySet().iterator();
                if (!oldest.hasNext()) break;
                CACHE.remove(oldest.next());
            }
            state = new State();
            state.key = key;
            CACHE.put(key, state);
        }
        return state;
    }

    /** 冷启动历史重建的每 lane 步数上限（供测试核对有界性）。 */
    public static int historyStepLimit() { return HISTORY_MAX_STEPS; }

    /** 扫掠抬升阈值（供测试核对）：缓坡/整格台阶不锁存，栅栏与 3 格墙锁存。 */
    public static double latchMinRise() { return VfxDrawPlan.LATCH_MIN_RISE; }

    /** 供测试/诊断：清空全部缓存。 */
    public static void clear() { CACHE.clear(); clipsThisTick = 0; clientTick = 0; }

    /** 供测试/诊断：当前缓存条目数。 */
    public static int cached() { return CACHE.size(); }
}
