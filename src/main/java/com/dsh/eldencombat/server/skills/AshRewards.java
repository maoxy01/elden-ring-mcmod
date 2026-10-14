package com.dsh.eldencombat.server.skills;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.AshRewardTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 战灰解锁的**玩法内入口**（§2.1「奖励来源留给现有探索/奖励能力，不在这里造另一套任务系统」）。
 *
 * <p>只做一件事：把"已有实体被玩家击杀"接成解锁入口。不新增任务、物品或结构；
 * 表在 {@link AshRewardTable}（纯逻辑，可单测）。
 *
 * <p>三条边界：
 * <ul>
 *   <li>只发给**击杀者本人**，不发给"在场所有人"；</li>
 *   <li>非玩家来源（自然死亡、摔死）不解锁；</li>
 *   <li>授予幂等：重复击杀不会重复解锁，也不会重复播报（`grant` 返回 false 就当没发生）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = EldenCombat.MODID)
public final class AshRewards {

    private AshRewards() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        String source = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        var ashes = AshRewardTable.ashesFor(source);
        if (ashes.isEmpty()) return;
        for (String ash : ashes) {
            if (!AshUnlocks.grant(killer, ash)) continue;
            killer.displayClientMessage(Component.literal("获得战灰：" + ash), false);
            EldenCombat.LOGGER.info("ASH_REWARD_GRANTED: entity={} player={} ash={}",
                    source, killer.getUUID(), ash);
        }
    }
}
