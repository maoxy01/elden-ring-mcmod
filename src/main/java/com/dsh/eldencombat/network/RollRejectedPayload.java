package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Reconciles a rejected upstream prediction without adding another roll HUD or resource. */
public record RollRejectedPayload(int available, int cooldown, float progress) implements CustomPacketPayload {
    public static final Type<RollRejectedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID,"roll_rejected"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RollRejectedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RollRejectedPayload::available, ByteBufCodecs.VAR_INT, RollRejectedPayload::cooldown,
            ByteBufCodecs.FLOAT, RollRejectedPayload::progress, RollRejectedPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
