package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 赐福菜单的服务端状态快照（服务端 → 客户端，纯展示）。
 *
 * <p>权威值全部来自服务端：旅行列表只含**已授权披露**的节点（与地图同一份判定），
 * 战灰列表带「是否已实现 / 是否已获得 / 是否已绑在手上这把」，手上武器的展示名与
 * 不可编辑原因也由服务端给出。客户端拿它渲染，不据此做任何判定。
 *
 * <p>{@code heldToken} 是菜单内"界面展示的是哪一把武器"的临时身份标记；
 * 客户端点击时原样回传，服务端提交前核对它是否仍等于**当前**主手，
 * 避免把战灰装到玩家后来拿起的那把武器上（见 {@code GraceMenuActions}）。
 */
public record GraceMenuStatePayload(UUID graceId, String graceName, List<Node> nodes, List<Ash> ashes,
                                    String heldItem, String heldRejection, String heldToken, String boundAsh,
                                    int fp, int maxFp, String notice) implements CustomPacketPayload {

    public record Node(UUID uid, String name, ResourceLocation dimension, BlockPos pos, String state) {}
    /** {@code compatible} = 当前主手武器能不能用这件战灰（服务端算的兼容性预览）。 */
    public record Ash(String id, boolean implemented, boolean owned, boolean bound, boolean compatible) {}

    public static final Type<GraceMenuStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "grace_menu_state"));

    public static GraceMenuStatePayload empty() {
        return new GraceMenuStatePayload(new UUID(0, 0), "", List.of(), List.of(), "", "", "", "", 0, 0, "");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, GraceMenuStatePayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeUUID(value.graceId);
            buf.writeUtf(value.graceName, 128);
            buf.writeVarInt(value.nodes.size());
            for (var n : value.nodes) {
                buf.writeUUID(n.uid);
                buf.writeUtf(n.name, 256);
                buf.writeResourceLocation(n.dimension);
                buf.writeBlockPos(n.pos);
                buf.writeUtf(n.state, 16);
            }
            buf.writeVarInt(value.ashes.size());
            for (var a : value.ashes) {
                buf.writeUtf(a.id, 128);
                buf.writeBoolean(a.implemented);
                buf.writeBoolean(a.owned);
                buf.writeBoolean(a.bound);
                buf.writeBoolean(a.compatible);
            }
            buf.writeUtf(value.heldItem, 256);
            buf.writeUtf(value.heldRejection, 256);
            buf.writeUtf(value.heldToken, 64);
            buf.writeUtf(value.boundAsh, 128);
            buf.writeVarInt(value.fp);
            buf.writeVarInt(value.maxFp);
            buf.writeUtf(value.notice, 256);
        }, buf -> {
            UUID graceId = buf.readUUID();
            String graceName = buf.readUtf(128);
            int nodeCount = buf.readVarInt();
            if (nodeCount < 0 || nodeCount > 4096) throw new IllegalArgumentException("Invalid grace menu node count");
            List<Node> nodes = new ArrayList<>(nodeCount);
            for (int i = 0; i < nodeCount; i++) nodes.add(new Node(buf.readUUID(), buf.readUtf(256),
                    buf.readResourceLocation(), buf.readBlockPos(), buf.readUtf(16)));
            int ashCount = buf.readVarInt();
            if (ashCount < 0 || ashCount > 4096) throw new IllegalArgumentException("Invalid grace menu ash count");
            List<Ash> ashes = new ArrayList<>(ashCount);
            for (int i = 0; i < ashCount; i++) ashes.add(new Ash(buf.readUtf(128), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
            return new GraceMenuStatePayload(graceId, graceName, List.copyOf(nodes), List.copyOf(ashes),
                    buf.readUtf(256), buf.readUtf(256), buf.readUtf(64), buf.readUtf(128),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(256));
        });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
