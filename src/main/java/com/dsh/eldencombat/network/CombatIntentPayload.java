package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.IntentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 -> 服务端的动作意图。
 * 只包含动作意图、连续序号、可选目标与规范化方向；不提交伤害、精力余额、动作进度或命中结论。
 */
public record CombatIntentPayload(int sequence, int intent, int targetId, float dirX, float dirZ)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CombatIntentPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "intent"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombatIntentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatIntentPayload::sequence,
            ByteBufCodecs.VAR_INT, CombatIntentPayload::intent,
            ByteBufCodecs.VAR_INT, CombatIntentPayload::targetId,
            ByteBufCodecs.FLOAT, CombatIntentPayload::dirX,
            ByteBufCodecs.FLOAT, CombatIntentPayload::dirZ,
            CombatIntentPayload::new);

    public static CombatIntentPayload of(int sequence, IntentType intent, int targetId, double dirX, double dirZ) {
        return new CombatIntentPayload(sequence, intent.ordinal(), targetId, (float) dirX, (float) dirZ);
    }

    public IntentType intentType() {
        IntentType[] values = IntentType.values();
        if (intent < 0 || intent >= values.length) {
            return IntentType.CANCEL;
        }
        return values[intent];
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
