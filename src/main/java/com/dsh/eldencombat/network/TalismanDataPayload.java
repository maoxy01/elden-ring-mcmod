package com.dsh.eldencombat.network;

import com.dsh.eldencombat.EldenCombat;
import com.dsh.eldencombat.core.TalismanSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端的护符**展示数据**快照（§3.5）。
 *
 * <p>修的问题：远程客户端的 {@code Talismans.Listener} **不会**读到服务端数据包
 * （只在服务端整合服务器上才可能同时存在），所以 tooltip 以前只能显示"护符数据缺失"。
 * 客户端现在只用这份**服务端同步过来的**数据画 tooltip/界面，
 * 战斗数值仍由服务器计算（客户端这份数据不参与任何结算）。
 *
 * <p>全量替换语义：每次下发都是完整目录，客户端整体覆盖，不做增量合并
 * （避免删掉一个 json 后客户端还留着旧条目）。
 */
public record TalismanDataPayload(List<TalismanSpec> entries) implements CustomPacketPayload {

    /** 目录上限：护符是人工维护的小列表，超过就是数据出错，直接拒收。 */
    public static final int MAX_ENTRIES = 512;

    public static final Type<TalismanDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EldenCombat.MODID, "talisman_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanDataPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.entries.size());
                for (TalismanSpec spec : value.entries) {
                    buf.writeUtf(spec.id(), 128);
                    buf.writeUtf(spec.displayName(), 256);
                    buf.writeUtf(spec.series(), 64);
                    buf.writeFloat(spec.maxFp());
                    buf.writeFloat(spec.staminaRegen());
                    buf.writeFloat(spec.damage());
                    buf.writeFloat(spec.damageReduction());
                    buf.writeFloat(spec.heal());
                }
            },
            buf -> {
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Invalid talisman entry count: " + count);
                }
                List<TalismanSpec> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(new TalismanSpec(buf.readUtf(128), buf.readUtf(256), buf.readUtf(64),
                            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
                }
                return new TalismanDataPayload(List.copyOf(entries));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
