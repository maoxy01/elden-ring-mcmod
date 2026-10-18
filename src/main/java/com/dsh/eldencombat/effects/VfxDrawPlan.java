package com.dsh.eldencombat.effects;

/**
 * VFX 主路径调度的纯判据（无客户端类依赖，可直接单测）。
 *
 * <p>背景：旧实现用 {@code !submitted()} 决定"要不要回落旧程序化主视觉"，把
 * "没有声明 / 资源缺失 / CORE / 预算拒绝 / 有意不可见 / 生命周期结束"混成一个布尔值，
 * 于是作者路径有意全部隐藏的片段会被不受同一份可见性约束的旧绘制路径重新画出来。
 *
 * <p>本类把结果拆成三个明确状态：{@link Disposition#NOT_HANDLED}（没接管，兼容主路径有资格执行）、
 * {@link Disposition#DRAWN}（接管且真的提交了顶点）、{@link Disposition#SUPPRESSED}（接管但本帧
 * 有意/因预算与生命周期而 0 提交，<b>不得</b>再回旧路径），并附带 {@link Reason}。
 *
 * <p>调用顺序：先 {@link #decide} 选一次主路径，绘制后再 {@link #finish} 用真实计数定稿；
 * 绝不用"提交数"反推接管权。
 */
public final class VfxDrawPlan {
    /** 与 {@code WaveFront.LANES} 一致的原 lane 数；位掩码按原始 lane ID 编号。 */
    public static final int LANES = 16;
    /** 抬升阈值（格）：障碍柱顶比低侧支撑面高出超过该值才算"被挡"；缓坡与整格台阶（≤1）不锁存。 */
    public static final double LATCH_MIN_RISE = 1.0;

    public enum Disposition { NOT_HANDLED, DRAWN, SUPPRESSED }

    /** 结果原因；与调度状态一一对应，日志与测试都按它判定，不用提交数反推。 */
    public enum Reason {
        /** 无声明/未 ready/没有作者几何：既有兼容主路径有资格执行。 */
        NO_DECLARATION,
        /** 作者主贴片按预算真实提交。 */
        AUTHOR_SPRITE,
        /** 合法路由缺 PNG/atlas 或关闭贴片资源开关：同一路由的合法轮廓。 */
        RESOURCE_OUTLINE,
        /** CORE/渲染压力：同一路由内部的合法轮廓，不是"返回 0 等旧路径补画"。 */
        CORE_OUTLINE,
        /** 主预算拒绝。 */
        NO_BUDGET,
        /** 所有 lane 被挡/UNKNOWN/无支撑：本帧 0 提交。 */
        NO_VISIBLE_LANE,
        /** progress 非有限、&lt;0 或 ≥1 等生命周期外状态。 */
        INVALID_TIME,
        /** LOD 超距：渲染器在进入作者路径前就返回。 */
        HIDDEN,
        /** 取证诊断层本轮禁止该实例提交。 */
        DIAGNOSTIC_FILTER
    }

    /** 一次主路径选择：{@link Route#NONE} 表示不接管（只有 NO_DECLARATION 与抑制类原因）。 */
    public enum Route { NONE, SPRITE, OUTLINE }

    public record Decision(Route route, Reason reason) {
        public boolean handles() { return route != Route.NONE; }
    }

    /** 最终结果：状态 + 原因 + **实际提交**的分类计数。 */
    public record Plan(Disposition disposition, Reason reason, int spriteQuads, int outlinePanels) {
        public boolean legacyEligible() { return disposition == Disposition.NOT_HANDLED; }
        public boolean submitted() { return spriteQuads > 0 || outlinePanels > 0; }
        public int total() { return spriteQuads + outlinePanels; }
        public static Plan unhandled() { return new Plan(Disposition.NOT_HANDLED, Reason.NO_DECLARATION, 0, 0); }
        public static Plan suppressed(Reason reason) { return new Plan(Disposition.SUPPRESSED, reason, 0, 0); }
        public static Plan hidden() { return suppressed(Reason.HIDDEN); }
    }

    private VfxDrawPlan() {}

    /**
     * 选定本帧唯一一条主路径。顺序即优先级：未声明 → 生命周期 → 诊断禁止 → CORE → 资源轮廓 → 作者贴片。
     *
     * @param declared            路由声明、ready 与作者几何都成立
     * @param timeValid           progress 在 [0,1) 且帧号可解析
     * @param core                本帧 LOD 为 CORE（渲染压力或超距内的核心档）
     * @param spriteResourceReady 贴片资源（帧 PNG、atlas、调色板开关）齐全
     * @param diagSuppressed      取证诊断层本轮禁止该实例的主贴片
     */
    public static Decision decide(boolean declared, boolean timeValid, boolean core,
                                  boolean spriteResourceReady, boolean diagSuppressed) {
        if (!declared) return new Decision(Route.NONE, Reason.NO_DECLARATION);
        if (!timeValid) return new Decision(Route.NONE, Reason.INVALID_TIME);
        if (diagSuppressed) return new Decision(Route.NONE, Reason.DIAGNOSTIC_FILTER);
        if (core) return new Decision(Route.OUTLINE, Reason.CORE_OUTLINE);
        if (!spriteResourceReady) return new Decision(Route.OUTLINE, Reason.RESOURCE_OUTLINE);
        return new Decision(Route.SPRITE, Reason.AUTHOR_SPRITE);
    }

    /**
     * 用真实提交计数定稿。
     *
     * @param decision      {@link #decide} 的结果
     * @param spriteQuads   实际提交的作者贴片 quad 数（轮廓 panel <b>不能</b>算进来）
     * @param outlinePanels 实际提交的轮廓 panel 数
     * @param anyCandidate  本帧是否存在通过可见性/支撑筛选的候选（lane 或平面本体）
     * @param budgetRefused 候选被预算拒绝（而不是被可见性筛掉）
     */
    public static Plan finish(Decision decision, int spriteQuads, int outlinePanels,
                              boolean anyCandidate, boolean budgetRefused) {
        if (decision == null) return Plan.unhandled();
        if (!decision.handles()) {
            return decision.reason() == Reason.NO_DECLARATION ? Plan.unhandled() : Plan.suppressed(decision.reason());
        }
        int sprite = Math.max(0, spriteQuads), outline = Math.max(0, outlinePanels);
        if (sprite + outline > 0) return new Plan(Disposition.DRAWN, decision.reason(), sprite, outline);
        // 接管了却一顶点未提交：真实原因是预算还是可见性，按调用方记录的现场判定，绝不借此回落旧路径。
        return Plan.suppressed(budgetRefused && anyCandidate ? Reason.NO_BUDGET : Reason.NO_VISIBLE_LANE);
    }

    /**
     * 合并"服务端权威 mask"与"客户端表现裁剪 mask"，结果按**原始 lane ID** 位编号。
     * 过滤后的列表序号不能冒充原始 ID，故所有下游（主贴片/轮廓/兼容波几何）只消费本函数结果。
     */
    public static int combineMask(int serverBlocked, int clientHidden) {
        return (serverBlocked | clientHidden) & 0xFFFF;
    }

    /** 原始 lane ID 是否在该 mask 中被挡。 */
    public static boolean laneHidden(int mask, int laneId) {
        return laneId >= 0 && laneId < LANES && (mask & (1 << laneId)) != 0;
    }

    /**
     * 障碍是否真的挡住该 lane：障碍柱顶比**低侧支撑面**高出超过 {@code minRise} 才算。
     * 全格台阶与缓坡的抬升 ≤1 格，因此不会把正常地形误判成永久遮挡。
     */
    public static boolean blocksLane(double surfaceY, double supportLow, double minRise) {
        if (!Double.isFinite(surfaceY) || !Double.isFinite(supportLow) || !Double.isFinite(minRise)) return false;
        return surfaceY - supportLow > minRise + 1.0E-6;
    }
}
