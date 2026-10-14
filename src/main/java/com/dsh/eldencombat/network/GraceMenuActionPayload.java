package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 赐福菜单的按钮请求（客户端 → 服务端）：**只带选择信息**。
 *
 * <p>操作、菜单 id、目标节点 UUID、战灰 id、序号，外加界面展示的武器身份 {@code heldToken}——
 * 就这些。拥有权、FP、段数、ItemStack、以及"我在哪个赐福"一律由服务端现取：
 * 赐福 UUID 来自服务端自己的菜单实例，客户端给什么都不信。
 * {@code containerId} 只用来挡过期/伪造包；{@code heldToken} 只用来防"装错武器"（它是防呆，不是授权：
 * 想通过校验就必须等于服务端读到的当前主手，伪造没有收益）。
 */
public record GraceMenuActionPayload(int containerId, int action, UUID target, String ashId, int sequence,
                                     String heldToken) implements CustomPacketPayload {

    public static final int REST = 0;
    public static final int TRAVEL = 1;
    public static final int BIND = 2;
    public static final int REPLACE = 3;
    public static final int REMOVE = 4;

    public static final Type<GraceMenuActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "grace_menu_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GraceMenuActionPayload> STREAM_CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeVarInt(value.containerId);
            buf.writeVarInt(value.action);
            buf.writeBoolean(value.target != null);
            if (value.target != null) buf.writeUUID(value.target);
            buf.writeUtf(value.ashId, 128);
            buf.writeVarInt(value.sequence);
            buf.writeUtf(value.heldToken, 64);
        }, buf -> new GraceMenuActionPayload(buf.readVarInt(), buf.readVarInt(),
                buf.readBoolean() ? buf.readUUID() : null, buf.readUtf(128), buf.readVarInt(), buf.readUtf(64)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
