package com.dsh.eldencombat.server.skills;

/**
 * 地面波推进的纯逻辑（黄金波动 / 掠夺火焰共用）。
 * 不依赖 Minecraft 类型，便于单测：服务端每 tick 用它算下一段、判寿命与射程。
 */
public final class WaveStep {

    /** 一步推进的结果 */
    public record Step(double x, double y, double z, int travelledTicks, boolean finished, String reason) {
    }

    private WaveStep() {
    }

    /**
     * 推进一步。
     *
     * @param x              当前 x
     * @param z              当前 z
     * @param dirX           归一化方向 x
     * @param dirZ           归一化方向 z
     * @param speedPerTick   每 tick 前进距离（方块）
     * @param travelledTicks 已经推进的 tick 数（调用前）
     * @param lifespanTicks  寿命上限（tick）
     * @param maxRange       最远传播距离（方块）
     * @param reachedRange   推算后是否已超过 maxRange
     */
    public static Step advance(double x, double z, double dirX, double dirZ, double speedPerTick,
                               int travelledTicks, int lifespanTicks, double maxRange, boolean reachedRange) {
        int nextTicks = travelledTicks + 1;
        double nx = x + dirX * speedPerTick;
        double nz = z + dirZ * speedPerTick;
        double travelled = nextTicks * speedPerTick;
        if (nextTicks >= lifespanTicks) {
            return new Step(nx, 0.0, nz, nextTicks, true, "寿命结束");
        }
        if (reachedRange || travelled >= maxRange) {
            return new Step(nx, 0.0, nz, nextTicks, true, "到达最远距离");
        }
        return new Step(nx, 0.0, nz, nextTicks, false, "");
    }

    /** 判定体半径随传播线性扩展：起点 baseRadius，走到最远距离时到达 maxRadius。 */
    public static double radiusAt(double baseRadius, double maxRadius, int travelledTicks, double speedPerTick,
                                 double maxRange) {
        if (maxRange <= 0 || maxRadius <= baseRadius) {
            return baseRadius;
        }
        double progress = Math.min(1.0, travelledTicks * speedPerTick / maxRange);
        return baseRadius + (maxRadius - baseRadius) * progress;
    }
}
