package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** No client supplied position, dimension, owner or visibility can authorize travel. */
public record GraceTravelPayload(UUID uid, int sequence) implements CustomPacketPayload {
    public static final Type<GraceTravelPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID,"grace_travel"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GraceTravelPayload> STREAM_CODEC=StreamCodec.of(
            (buf,value)->{buf.writeUUID(value.uid);buf.writeVarInt(value.sequence);},
            buf->new GraceTravelPayload(buf.readUUID(),buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
