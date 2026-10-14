package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端的 FP 快照。HUD 只显示服务端值，客户端不参与 FP 结算。
 */
public record FpSyncPayload(float fp, float maxFp, String lastSkillId, int activeCastId, String activeSkillId,
                            boolean casting, int stageIndex)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FpSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "fp_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FpSyncPayload> STREAM_CODEC =
            StreamCodec.of(FpSyncPayload::write, FpSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buffer, FpSyncPayload payload) {
        buffer.writeFloat(payload.fp);
        buffer.writeFloat(payload.maxFp);
        buffer.writeUtf(payload.lastSkillId, 64);
        buffer.writeVarInt(payload.activeCastId);
        buffer.writeUtf(payload.activeSkillId, 64);
        buffer.writeBoolean(payload.casting);
        buffer.writeVarInt(payload.stageIndex);
    }

    private static FpSyncPayload read(RegistryFriendlyByteBuf buffer) {
        return new FpSyncPayload(
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readBoolean(), buffer.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
