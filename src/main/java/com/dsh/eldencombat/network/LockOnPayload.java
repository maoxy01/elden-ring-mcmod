package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 锁定目标的权威结果：服务端校验存活、敌对、距离与视线后回执，
 * 客户端只按回执控制视角，不能用锁定绕过攻击距离或隔墙打击。
 */
public record LockOnPayload(int targetId, boolean accepted) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LockOnPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "lock_on"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LockOnPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LockOnPayload::targetId,
            ByteBufCodecs.BOOL, LockOnPayload::accepted,
            LockOnPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
