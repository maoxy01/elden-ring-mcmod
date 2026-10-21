package com.dsh.eldencombat.server.grace;

import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.WaystoneVisibility;
import net.minecraft.server.level.ServerPlayer;
import com.dsh.eldencombat.network.GraceNodesPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图显示、菜单列表、休息与旅行共用的发现/权限边界。
 *
 * <p>{@link #authorizedNodes} 是**唯一**的"可以披露哪些节点"判定：地图下发与菜单旅行列表走同一份，
 * 免得出现"地图看不到但菜单能传"的缺口。
 */
public final class GraceAccess {
    private GraceAccess() {}

    public static String rejection(ServerPlayer player, Waystone node) {
        GraceDiscovery.admit(player, node);
        if (!GraceRegistry.get(player.server).contains(node.getWaystoneUid()) || !node.isValid()
                || WaystonesAPI.getWaystone(player.server, node.getWaystoneUid()).isEmpty())
            return "赐福已失效";
        if (!WaystonesAPI.isWaystoneActivated(player, node)) return "尚未发现此赐福";
        // Conservative prototype policy; team/sharestone access needs its own native permission adapter.
        if (node.getVisibility() != WaystoneVisibility.GLOBAL && node.hasOwner() && !node.isOwner(player))
            return "私人赐福无权访问";
        return null;
    }

    /** 服务端授权披露的节点快照（名字、维度、坐标、来源、可见性状态都来自 Waystones 实时值）。 */
    public static List<GraceNodesPayload.Node> authorizedNodes(ServerPlayer player, int limit) {
        List<GraceNodesPayload.Node> nodes = new ArrayList<>();
        int cap = Math.max(1, limit);
        for (var node : WaystonesAPI.getActivatedWaystones(player)) {
            if (nodes.size() >= cap) break;
            if (rejection(player, node) != null) continue;
            nodes.add(new GraceNodesPayload.Node(node.getWaystoneUid(), node.getName().getString(),
                    node.getDimension().location(), node.getPos(),
                    node.wasGenerated() ? "ancient" : "player",
                    node.getVisibility() == WaystoneVisibility.GLOBAL ? "public" : node.hasOwner() ? "private" : "discovered"));
        }
        return List.copyOf(nodes);
    }
}
