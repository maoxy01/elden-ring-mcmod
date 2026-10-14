package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.AshUnlockSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 战灰解锁的服务端入口（**唯一权威**）。
 *
 * <p>{@link AshWorkshop.Ownership} 只在这里接线一次——它是个纯函数式接口，
 * 任何客户端布尔、任何本地开关都实现不了它，这是"服务端权威"这条线的落点。
 *
 * <p>本类**不造任务系统、也不自己发奖励**：只提供幂等的
 * {@link #grant}/{@link #revoke} 与查询，奖励来源由调用方（现有探索/奖励能力）决定。
 * 因此它没有"每 tick 检查某条件"之类的逻辑，也没有任何隐藏的自动解锁路径。
 */
public final class AshUnlocks {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private AshUnlocks() {}

    /** 由 common setup 调用一次；重复调用无害。 */
    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            AshWorkshop.installOwnership(AshUnlocks::owns);
        } catch (IllegalStateException already) {
            EldenCombat.LOGGER.warn("ASH_OWNERSHIP_ALREADY_INSTALLED: 战灰解锁存储已接线，本次安装跳过");
            return;
        }
        EldenCombat.LOGGER.info("ASH_OWNERSHIP_INSTALLED: 战灰解锁走服务端持久化存档（SavedData，UUID 为键，幂等授予）");
    }

    public static boolean owns(ServerPlayer player, ResourceLocation ashId) {
        if (ashId == null) return false;
        requireServerThread(player);
        return AshUnlockData.get(player.server).contains(player.getUUID(), ashId.toString());
    }

    /**
     * 幂等授予；返回是否**新**加入。奖励来源可以放心重试：
     * 重复调用不会重复解锁，也不会产生任何副作用。
     *
     * @throws IllegalArgumentException ID 不是完整的 {@code namespace:path}
     */
    public static boolean grant(ServerPlayer player, String rawId) {
        requireServerThread(player);
        String id = AshUnlockSet.normalize(rawId);
        if (id == null) throw new IllegalArgumentException("战灰 ID 必须是完整的 namespace:path：" + rawId);
        boolean added = AshUnlockData.get(player.server).grant(player.getUUID(), id);
        if (added) EldenCombat.LOGGER.info("ASH_UNLOCK_GRANTED: player={} ash={}", player.getUUID(), id);
        return added;
    }

    public static boolean revoke(ServerPlayer player, String rawId) {
        requireServerThread(player);
        String id = AshUnlockSet.normalize(rawId);
        if (id == null) return false;
        boolean removed = AshUnlockData.get(player.server).revoke(player.getUUID(), id);
        if (removed) EldenCombat.LOGGER.info("ASH_UNLOCK_REVOKED: player={} ash={}", player.getUUID(), id);
        return removed;
    }

    /** 已解锁列表（字典序）。 */
    public static List<String> list(ServerPlayer player) {
        requireServerThread(player);
        return AshUnlockData.get(player.server).list(player.getUUID());
    }

    private static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Ash unlocks require server thread");
    }
}
