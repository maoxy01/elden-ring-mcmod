package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端（含旁观者）的战技施放快照，**只用于动作表现**。
 *
 * <p>为什么要单开一个包而不是塞进 {@link FpSyncPayload}：FP 是玩家私有信息且只发给本人，
 * 而战技动作必须让**其他玩家也看得到**，所以这里用
 * {@code PacketDistributor.sendToPlayersTrackingEntityAndSelf} 广播。
 *
 * <p>{@code stage} 是 {@code SkillCastState.Stage} 的序号（0=IDLE…4=RECOVERY），
 * {@code stageTicks} 是该阶段的已过 tick，客户端据此推算本地起算点，之后自己插值，
 * 不依赖每 tick 同步。
 */
public record SkillCastPayload(int entityId, String skillId, int castId, int stage, int stageIndex, int stageTicks)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SkillCastPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "skill_cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillCastPayload> STREAM_CODEC =
            StreamCodec.of(SkillCastPayload::write, SkillCastPayload::read);

    private static void write(RegistryFriendlyByteBuf buffer, SkillCastPayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeUtf(payload.skillId, 64);
        buffer.writeVarInt(payload.castId);
        buffer.writeVarInt(payload.stage);
        buffer.writeVarInt(payload.stageIndex);
        buffer.writeVarInt(payload.stageTicks);
    }

    private static SkillCastPayload read(RegistryFriendlyByteBuf buffer) {
        return new SkillCastPayload(
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
