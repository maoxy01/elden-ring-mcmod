package com.dsh.eldencombat.client.grace;

import com.dsh.eldencombat.network.GraceMenuStatePayload;
import net.minecraft.client.Minecraft;

/**
 * 最近一次服务端菜单快照。
 *
 * <p>存在的理由：菜单状态包可能**先于**界面打开到达（服务端 openMenu 与首个快照是两次发送），
 * 所以界面 {@code init()} 也要能从这里取到数据，而不是只依赖"包到的时候界面正好开着"。
 */
public final class GraceScreenState {

    private static volatile GraceMenuStatePayload latest = GraceMenuStatePayload.empty();
    /** 本客户端实际收到的快照序号（纯客户端计数，不上协议）。诊断夹具用它证明"这是本次请求的应答"，
     *  而不是菜单周期刷新里沿用下来的旧 notice。 */
    private static volatile long revision;

    private GraceScreenState() {}

    public static GraceMenuStatePayload latest() {
        return latest;
    }

    public static long revision() {
        return revision;
    }

    public static void accept(GraceMenuStatePayload payload) {
        if (payload == null) return;
        latest = payload;
        revision++;
        if (Minecraft.getInstance().screen instanceof GraceScreen screen) screen.apply(payload);
    }
}
