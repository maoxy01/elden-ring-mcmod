package com.dsh.eldencombat.server.skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * §4 落地处理器需要用到的世界查询。
 *
 * <p>单独成类的原因：{@link SkillLanding} 是纯逻辑（可单测），而"下方最近的实心方块在哪"
 * 必须问真实世界。把这部分隔离出来，服务端 smoke 就能直接验证它（不用真的把一个玩家扔下悬崖）。
 */
public final class LandingSupport {

    /** 向下扫描的最大格数（超过就认为"下方没有可落地的面"）。 */
    public static final int MAX_SCAN = 64;

    private LandingSupport() {
    }

    /** 仅检查脚底薄层的方块碰撞；上升中的旧 onGround 标志不能触发地裂。 */
    public static boolean groundContact(net.minecraft.world.entity.Entity entity) {
        var box = entity.getBoundingBox();
        var feet = new net.minecraft.world.phys.AABB(box.minX + 0.001, box.minY - 0.02, box.minZ + 0.001,
                box.maxX - 0.001, box.minY, box.maxZ - 0.001);
        return entity.level().getBlockCollisions(entity, feet).iterator().hasNext();
    }

    /**
     * 从 {@code (x, y, z)} 向下找第一个有碰撞形状的方块，返回其**顶面** Y 坐标。
     * 扫描范围内没有实心方块时返回 {@link Double#NaN}（调用方不得据此传送玩家）。
     */
    public static double groundBelow(ServerLevel level, double x, double y, double z, int maxScan) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable().move(Direction.DOWN);
        int limit = Math.max(1, maxScan);
        for (int i = 0; i < limit; i++) {
            if (pos.getY() < level.getMinBuildHeight()) {
                return Double.NaN;
            }
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() + 1.0;
            }
            pos.move(Direction.DOWN);
        }
        return Double.NaN;
    }

    /** 该位置是否落在液体里（水或岩浆都算；岩浆同时也是伤害地形，所以更要先判）。 */
    public static boolean inLiquid(net.minecraft.world.entity.Entity entity) {
        return entity.isInWater() || entity.isInLava();
    }
}
