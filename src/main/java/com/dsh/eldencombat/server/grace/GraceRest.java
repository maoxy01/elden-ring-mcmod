package com.dsh.eldencombat.server.grace;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.registry.ModAttachments;
import com.dsh.eldencombat.server.ServerCombat;
import com.dsh.eldencombat.server.skills.SkillServerEvents;
import net.blay09.mods.waystones.api.Waystone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * 休息的**唯一入口**（方块交互）。地图显示与旅行永不调用本类。
 *
 * <p>恢复规则集中在 {@link GraceSettings}：默认只回 FP。其他资源接现有权威状态，
 * 恢复后必须立刻同步给客户端（FP 走 {@code SkillServerEvents.sync}，
 * 精力/药瓶走 {@code ServerCombat.syncNow}），否则客户端 HUD 会显示旧值。
 */
public final class GraceRest {
    private GraceRest() {}

    public static String rest(ServerPlayer player, Waystone node, BlockPos interactedPos) {
        String rejection = rejectLocalUse(player, node, interactedPos);
        if (rejection != null) return rejection;
        GraceSettings settings = GraceSettings.current();
        boolean fpChanged = false;
        boolean combatChanged = false;

        if (settings.restFp == GraceSettings.Rest.FULL) {
            var fp = SkillServerEvents.fp(player);
            fp.setFp(fp.maxFp());
            fpChanged = true;
        }
        if (settings.restHealth == GraceSettings.Rest.FULL && player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        var state = player.getData(ModAttachments.COMBAT_STATE);
        if (settings.restStamina == GraceSettings.Rest.FULL) {
            state.refillStamina(EldenCombat.config());
            combatChanged = true;
        }
        if (settings.restFlasks == GraceSettings.Rest.FULL) {
            state.refillFlasks(EldenCombat.config());
            combatChanged = true;
        }

        if (fpChanged) SkillServerEvents.sync(player);
        if (combatChanged) ServerCombat.syncNow(player);
        EldenCombat.LOGGER.info("GRACE_REST_APPLIED: player={} uid={} fp={} fpRule={} healthRule={} staminaRule={} flaskRule={}",
                player.getUUID(), node.getWaystoneUid(), SkillServerEvents.fp(player).fp(),
                settings.restFp, settings.restHealth, settings.restStamina, settings.restFlasks);
        return null;
    }

    /** 休息与战灰菜单共用的本地权限校验；校验访问权限**永不**恢复资源。 */
    public static String rejectLocalUse(ServerPlayer player, Waystone node, BlockPos interactedPos) {
        if (player.level().dimension() != node.getDimension()
                || !node.getPos().equals(interactedPos)
                || !player.canInteractWithBlock(interactedPos, 0)) return "需要靠近赐福休息";
        String rejection = GraceService.rejectActivity(player);
        if (rejection == null) rejection = GraceAccess.rejection(player, node);
        return rejection;
    }
}
