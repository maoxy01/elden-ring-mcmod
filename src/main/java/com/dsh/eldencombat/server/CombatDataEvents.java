package com.dsh.eldencombat.server;

import com.dsh.eldencombat.EldenCombat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * 战斗数据包加载入口：护符与药物参数。
 *
 * <p>放在服务端 {@code AddReloadListenerEvent} 上，{@code /reload} 与开服都会重新读，
 * 与战技参数（{@code dsh_skills/remembrance_parameters.json}，走类路径资源）不同：
 * 这两类是地道的"数据包内容"，可以被整合包覆盖。
 */
@EventBusSubscriber(modid = EldenCombat.MODID)
public final class CombatDataEvents {

    private CombatDataEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new Talismans.Listener());
        event.addListener(new Consumables.Listener());
    }
}
