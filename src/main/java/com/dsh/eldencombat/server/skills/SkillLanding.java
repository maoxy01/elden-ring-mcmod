package com.dsh.eldencombat.server.skills;

/**
 * 跃起战技的**统一落地处理器**（《成长路径与手感收口方案》§4）。
 *
 * <p>位移是 MC 里最容易出 bug 的部分：低天花板、悬崖边缘、台阶、水/岩浆、超时未落地、
 * 玩家中途退出——每一种都可能变成"卡在空中"或"穿地形"。所以 5 个跃起战技
 * （黄金碎击 / 命定之死 / 水鸟乱舞 / 唤星 / 咒血斩击）共用这一份判定，
 * 而不是每个技能各写一段。
 *
 * <p>本类刻意**纯逻辑**：世界查询通过 {@link Probe} 注入，因此六个分支都能单测，
 * 而服务端跑的是同一份代码。
 *
 * <p>判定顺序（有意如此）：
 * <ol>
 *   <li>落水/岩浆 → 取消落地伤害，转溅水表现；</li>
 *   <li>掉出世界 → 取消位移，送回起跳点并退还部分 FP；</li>
 *   <li>正常落地 → 只结算一次落地伤害；</li>
 *   <li>低天花板 → 立即转入下落（不等待峰值，**不是**终止分支）；</li>
 *   <li>超时未落地 → 强制吸附到下方最近的实心方块。</li>
 * </ol>
 */
public final class SkillLanding {

    public enum Outcome {
        /** 仍在空中（继续飞） */
        AIRBORNE,
        /** 正常落地 */
        LANDED,
        /** 上升路径被挡住：转入下落（非终止分支） */
        LOW_CEILING,
        /** 落点是液体 */
        LIQUID,
        /** 掉出世界 */
        VOID,
        /** 超时未落地 */
        TIMEOUT,
        /** 未观察到起跳：取消冲击，不把等待超时当成落地。 */
        TAKEOFF_FAILED
    }

    /** 世界查询接口：把 MC 类型挡在外面，让六个分支都能单测。 */
    public interface Probe {
        double y();

        boolean onGround();

        /** 服务端碰撞查询确认脚下确有支撑，不能只采信移动包的 onGround。 */
        boolean groundContact();

        boolean inLiquid();

        double deltaY();

        /** 世界最低可建造高度（虚空判定基准） */
        double worldBottom();

        /** 上升路径是否被位移预算判定为"到顶了" */
        boolean ceilingBlocked();
    }

    /** 起跳后多少 tick 还没落地就强制结算 */
    public static final int DEFAULT_TIMEOUT_TICKS = 60;
    /** 距世界底部多少格内算"掉出世界"（留一点落地余量） */
    public static final double VOID_MARGIN = 8.0;
    /**
     * 判定"真的离开地面"的高度阈值（格）。
     *
     * <p>为什么需要它：起跳冲量是在蓄势末尾写进 {@code deltaMovement} 的，而**服务端玩家的位置与
     * onGround 来自客户端上报**——冲量发出后的 1..3 tick 里，服务端读到的仍是"站在地上"。
     * 若此时接受 {@code onGround()} 为落地，落地伤害与落地特效（如黄金碎击的地面碎裂）就会在
     * **起跳点、人还在上升之前**触发：玩家在空中看到脚下已经裂开。所以"离地"必须先被观察到。
     */
    public static final double DEPART_EPSILON = 0.05;
    /** 冲量始终没把玩家抬起来时，等待多少 tick 后取消冲击。 */
    public static final int NO_TAKEOFF_TICKS = 8;

    private final int timeoutTicks;
    private final double startY;
    private int ticks;
    private double highestY;
    /** 已经观察到"真的离开地面"（见 {@link #DEPART_EPSILON} 的说明） */
    private boolean departed;
    private Outcome outcome = Outcome.AIRBORNE;

    public SkillLanding(double startY) {
        this(startY, DEFAULT_TIMEOUT_TICKS);
    }

    public SkillLanding(double startY, int timeoutTicks) {
        this.startY = startY;
        this.timeoutTicks = Math.max(1, timeoutTicks);
        this.highestY = startY;
    }

    /**
     * 每 tick 调用一次。
     *
     * @return {@link Outcome#AIRBORNE} 表示继续飞；{@link Outcome#LOW_CEILING} 表示
     *         本 tick 应该砍掉上升速度继续下落；其余返回值表示**已经结算**
     *         （再次调用会原样返回同一个结论，天然只结算一次）。
     */
    public Outcome advance(Probe probe) {
        if (isTerminal(outcome)) {
            return outcome;
        }
        ticks++;
        highestY = Math.max(highestY, probe.y());
        if (probe.inLiquid()) {
            return settle(Outcome.LIQUID);
        }
        if (probe.y() < probe.worldBottom() + VOID_MARGIN) {
            return settle(Outcome.VOID);
        }
        // 离地锁：在"真的离开地面"被观察到之前，onGround 不足以判定落地（原因见 DEPART_EPSILON）。
        if (!departed && (!probe.onGround() || probe.y() > startY + DEPART_EPSILON)) {
            departed = true;
        }
        if (departed && probe.onGround() && probe.groundContact()) {
            return settle(Outcome.LANDED);
        }
        if (!departed && ticks >= NO_TAKEOFF_TICKS) {
            return settle(Outcome.TAKEOFF_FAILED);
        }
        if (probe.ceilingBlocked()) {
            return Outcome.LOW_CEILING;
        }
        if (ticks >= timeoutTicks) {
            return settle(Outcome.TIMEOUT);
        }
        return Outcome.AIRBORNE;
    }

    private Outcome settle(Outcome settled) {
        this.outcome = settled;
        return settled;
    }

    public static boolean isTerminal(Outcome outcome) {
        return outcome == Outcome.LANDED || outcome == Outcome.LIQUID
                || outcome == Outcome.VOID || outcome == Outcome.TIMEOUT || outcome == Outcome.TAKEOFF_FAILED;
    }

    /** 这一分支必须**取消**落地伤害（掉水里 / 掉出世界不该白拿一次范围伤害）。 */
    public static boolean cancelsImpact(Outcome outcome) {
        return outcome == Outcome.LIQUID || outcome == Outcome.VOID || outcome == Outcome.TAKEOFF_FAILED;
    }

    /** 这一分支需要把玩家救回起跳点。 */
    public static boolean returnsToStart(Outcome outcome) {
        return outcome == Outcome.VOID;
    }

    /** 虚空分支退还的 FP 比例（"退还部分 FP"，不是全退）。 */
    public static final float VOID_REFUND_RATIO = 0.5f;

    public boolean resolved() {
        return isTerminal(outcome);
    }

    /** 是否已经观察到玩家真的离开地面（诊断 / 夹具断言用）。 */
    public boolean departed() {
        return departed;
    }

    public Outcome outcome() {
        return outcome;
    }

    public int ticks() {
        return ticks;
    }

    public double highestY() {
        return highestY;
    }

    public double startY() {
        return startY;
    }

    public int timeoutTicks() {
        return timeoutTicks;
    }

    public String describe() {
        return String.format("落地判定=%s 已用%d tick 起跳Y=%.2f 最高Y=%.2f 离地=%s",
                outcome, ticks, startY, highestY, departed);
    }
}
