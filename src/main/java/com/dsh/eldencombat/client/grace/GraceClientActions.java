package com.dsh.eldencombat.client.grace;

import com.dsh.eldencombat.network.GraceMenuActionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * 客户端侧唯一的菜单/地图请求出口。
 *
 * <p>序号是**共享的单调计数器**：服务端 {@code requestTravel} 用它挡重复旅行请求，
 * 所以菜单和地图必须用同一个计数器，否则两边的号会互相把对方判成重放。
 */
public final class GraceClientActions {

    private static int sequence;

    private GraceClientActions() {}

    public static synchronized int nextSequence() {
        return ++sequence;
    }

    public static void send(int containerId, int action, UUID target, String ashId, String heldToken) {
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new GraceMenuActionPayload(containerId, action, target,
                ashId == null ? "" : ashId, nextSequence(), heldToken == null ? "" : heldToken));
    }
}
