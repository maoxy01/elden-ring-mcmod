package com.dsh.eldencombat.server.skills;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class SkillMotion {
    final SkillMovementBudget budget;
    final double floor;
    final double ceiling;
    double launchX;
    double launchY;
    double launchZ;
    Vec3 previous;
    Vec3 issued = Vec3.ZERO;
    double heading;
    boolean launched;

    SkillMotion(ServerPlayer player, RemembranceSkillSpec spec) {
        budget = new SkillMovementBudget(spec.maxHorizontal());
        floor = player.getY();
        ceiling = floor + spec.maxVertical();
        previous = player.position();
        heading = Math.toRadians(player.getYRot());
        launchX = player.getX();
        launchY = player.getY();
        launchZ = player.getZ();
    }

    Vec3 direction() { return new Vec3(-Math.sin(heading), 0, Math.cos(heading)); }

    /** 是否已经撞到这次跃起的上升上限（§4 的"低天花板 / 到顶"分支）。 */
    boolean ceilingBlocked(ServerPlayer player) { return launched && player.getY() > ceiling; }

    /** 砍掉上升速度，立即转入下落（不等峰值）。 */
    void cutRise(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y > 0) {
            send(player, new Vec3(velocity.x, 0, velocity.z));
        }
    }

    /** 把玩家放到一个确定的位置并清零速度（落点安全兜底 / 送回起跳点用）。 */
    void place(ServerPlayer player, double x, double y, double z) {
        player.teleportTo(x, y, z);
        send(player, Vec3.ZERO);
        previous = player.position();
    }

    boolean tick(ServerPlayer player) {
        if (!launched) { previous = player.position(); return true; }
        Vec3 moved = player.position().subtract(previous);
        budget.record(moved.x, moved.z);
        previous = player.position();
        if (player.getY() > ceiling + .15 && player.getDeltaMovement().y > 0) {
            send(player, new Vec3(player.getDeltaMovement().x, 0, player.getDeltaMovement().z));
        }
        if (budget.remaining() < .05 && budget.travelled() > 0) stop(player);
        return !budget.exceeded();
    }

    void launch(ServerPlayer player, double horizontal, double vertical, boolean turn) {
        if (turn) heading = SkillMovementBudget.turn(heading, Math.toRadians(player.getYRot()), Math.toRadians(30));
        launched = true;
        launchX = player.getX();
        launchY = player.getY();
        launchZ = player.getZ();
        double h = budget.speed(horizontal);
        Vec3 forward = direction().scale(h);
        double v = player.onGround() && player.getY() < ceiling ? vertical : player.getDeltaMovement().y;
        send(player, new Vec3(forward.x, v, forward.z));
    }

    /**
     * 连段中的**前冲**：只改写水平速度，绝不重新给垂直冲量。
     *
     * <p>为什么必须与 {@link #launch} 分开：{@code launch} 在"服务端认为玩家在地上"时会再加一次
     * 垂直冲量。多段斩击（水鸟乱舞 / 命定之死）每段之间恰好落回地面，于是每段都变成一次新的起跳——
     * 观感就是"原地蹦三下"。前冲只负责把人往视线方向推，竖直方向交给重力。
     */
    void dash(ServerPlayer player, double horizontal, boolean turn) {
        if (turn) heading = SkillMovementBudget.turn(heading, Math.toRadians(player.getYRot()), Math.toRadians(30));
        double h = budget.speed(horizontal);
        if (h <= 0) return;
        launched = true;
        Vec3 forward = direction().scale(h);
        send(player, new Vec3(forward.x, player.getDeltaMovement().y, forward.z));
    }

    void stop(ServerPlayer player) {
        Vec3 velocity = player.getDeltaMovement();
        // Preserve a foreign knockback rather than wiping it on TAKE_DAMAGE_POST.
        double dot = velocity.x * issued.x + velocity.z * issued.z;
        if (dot >= 0 && velocity.horizontalDistance() <= issued.horizontalDistance() + .08) {
            send(player, new Vec3(0, Math.min(0, velocity.y), 0));
        }
    }

    private void send(ServerPlayer player, Vec3 velocity) {
        issued = velocity;
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
