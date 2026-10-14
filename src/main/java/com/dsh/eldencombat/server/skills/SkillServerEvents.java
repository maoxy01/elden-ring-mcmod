package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.network.FpSyncPayload;
import com.dsh.eldencombat.network.SkillCastPayload;
import com.dsh.eldencombat.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * FP 状态的生命周期与同步（服务端权威）。
 *
 * <p>规则：登录只同步不补满；重生（死亡克隆）补满；换维度不补满；退出保存由 attachment 序列化承担。
 */
@EventBusSubscriber(modid = EldenCombat.MODID)
public final class SkillServerEvents {

    /** 同步间隔（tick）。FP 变化不频繁，5 tick 足够 HUD 平滑。 */
    private static final int SYNC_INTERVAL = 5;

    /** 真实收到并处理的 server tick 事件数：自检用（为 0 说明本类没接上总线）。 */
    private static long busTicks;

    private SkillServerEvents() {
    }

    public static long busTickCount() {
        return busTicks;
    }

    public static PlayerFpState fp(ServerPlayer player) {
        PlayerFpState state = player.getData(ModAttachments.PLAYER_FP);
        // Only the isolated single-player manual harness gets unlimited FP, not normal games or smoke tests.
        boolean unlimited = Boolean.getBoolean("eldencombat.manualSkills") && !player.server.isDedicatedServer();
        if (state.unlimitedForTesting() != unlimited) state.setUnlimitedForTesting(unlimited);
        return state;
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 首次进入且没有存档：补满一次；有存档则保持存档值（重新登录不刷恢复）
            fp(player).refillOnFirstJoin();
            // 护符加成不落盘：登录时按附件里记录的护符重新应用（同时跑一次性旧数据迁移）
            com.dsh.eldencombat.server.Talismans.refresh(player);
            // §3.5 登录时下发护符展示数据（tooltip 只用这份同步数据）
            com.dsh.eldencombat.server.Talismans.syncDisplay(player);
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 离线清理：结束施放，避免留下半开的命中窗口
            SkillDriver.detach(player, player.level().getGameTime(), "离线");
        }
    }

    // NeoForge copies serialized attachments at NORMAL priority. Refill after that copy.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getOriginal() instanceof ServerPlayer original) {
            SkillDriver.detach(original, original.level().getGameTime(), "克隆");
        }
        SkillDriver.cast(player).finish();
        com.dsh.eldencombat.server.Talismans.refresh(player);
        PlayerFpState state = fp(player);
        if (event.isWasDeath()) {
            // 死亡重生：明确补满并记录
            state.refillOnRespawn();
            EldenCombat.LOGGER.debug("[skills] {} 重生补满 FP: {}", player.getGameProfile().getName(), state.describe());
        }
        // 换维度（非死亡克隆）：不补满、不刷冷却；但要重新应用护符加成（克隆后附件已搬运）
        sync(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        busTicks++;
        long tick = event.getServer().getTickCount();
        // 施放时间线每 tick 推进（整数 tick，服务端权威）
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            SkillDriver.tick(player, player.level().getGameTime());
            // §4 落地安全网：施放已结束但仍悬空的跃起继续被轮询（只救人，不结算伤害）
            SkillDriver.safetyTick(player, player.level().getGameTime());
        }
        if (tick % SYNC_INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SkillDriver.reloadSpecs();
        EldenCombat.LOGGER.info("[skills] 追忆战技参数载入 {} 项", SkillDriver.specs().size());
        // §5：武器数值表（reach / 削韧 / 重量）+ 可选的数据包覆盖
        com.dsh.eldencombat.core.WeaponProfiles.reload();
        EldenCombat.LOGGER.info("[weapons] 武器数值档载入 {} 件", com.dsh.eldencombat.core.WeaponProfiles.size());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillDriver.detach(player, player.level().getGameTime(), "换维度");
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        PlayerFpState state = fp(player);
        SkillCastState cast = SkillDriver.cast(player);
        PacketDistributor.sendToPlayer(player, new FpSyncPayload(
                state.fp(),
                state.maxFp(),
                state.lastSkillId(),
                cast.castId(),
                cast.skillId(),
                cast.isCasting(), cast.stageIndex()));
        // 动作表现走广播：自己 + 正在追踪自己的其他玩家都能看到战技动作。
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SkillCastPayload(
                player.getId(),
                cast.skillId(),
                cast.castId(),
                cast.stage().ordinal(),
                cast.stageIndex(),
                cast.stageTicks(player.level().getGameTime())));
    }
}
