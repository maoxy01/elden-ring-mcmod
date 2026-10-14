package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 -> 客户端的权威战斗状态同步。
 * 至少包含被接受的动作 ID、动作序号与开始时间；精力与药瓶次数用于 HUD。
 */
public record CombatStatePayload(int entityId, String actionId, int actionSequence, long actionStartTick, float stamina,
                                 float maxStamina, int potionCharges, int targetId, boolean combatMode, boolean guarding,
                                 String weaponClass, boolean slim, boolean leftHanded, int duration, int hitStart, int hitEnd,
                                 int fullChargeTicks, long guardImpactTick, float rollYaw)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CombatStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatStatePayload> STREAM_CODEC =
            StreamCodec.of(CombatStatePayload::write, CombatStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buffer, CombatStatePayload payload) {
        buffer.writeVarInt(payload.entityId);
        buffer.writeUtf(payload.actionId, 32);
        buffer.writeVarInt(payload.actionSequence);
        buffer.writeVarLong(payload.actionStartTick);
        buffer.writeFloat(payload.stamina);
        buffer.writeFloat(payload.maxStamina);
        buffer.writeVarInt(payload.potionCharges);
        buffer.writeVarInt(payload.targetId);
        buffer.writeBoolean(payload.combatMode);
        buffer.writeBoolean(payload.guarding);
        buffer.writeUtf(payload.weaponClass, 32);
        buffer.writeBoolean(payload.slim);
        buffer.writeBoolean(payload.leftHanded);
        buffer.writeVarInt(payload.duration);
        buffer.writeVarInt(payload.hitStart);
        buffer.writeVarInt(payload.hitEnd);
        buffer.writeVarInt(payload.fullChargeTicks);
        buffer.writeLong(payload.guardImpactTick);
        buffer.writeFloat(payload.rollYaw);
    }

    private static CombatStatePayload read(RegistryFriendlyByteBuf buffer) {
        return new CombatStatePayload(
                buffer.readVarInt(),
                buffer.readUtf(32),
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(32), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(), buffer.readFloat());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
