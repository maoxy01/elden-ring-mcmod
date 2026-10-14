package com.dsh.eldencombat.registry;

import com.dsh.eldencombat.core.Affinity;
import com.dsh.eldencombat.core.WeaponState;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * {@link WeaponState} 的序列化（§2.7 的 Data Component）。
 *
 * <p>刻意放在 registry 层而不是 core：core 保持"纯逻辑、可在单测里直接跑"，
 * 序列化依赖 Mojang 的 Codec / StreamCodec，属于平台细节。
 *
 * <p>三个字段都要落盘（方案 §7.2 第 7 条："存档重载后等级与质变保持"）。
 * 反序列化时质变名容错（认不出的按"无质变"处理），避免数据包改名把存档读坏。
 */
public final class WeaponStateCodec {

    public static final Codec<WeaponState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("upgrade_level", 0).forGetter(WeaponState::upgradeLevel),
            Codec.STRING.optionalFieldOf("affinity", "NONE")
                    .forGetter(state -> state.affinity().name()),
            Codec.BOOL.optionalFieldOf("art_locked", false).forGetter(WeaponState::artLocked)
    ).apply(instance, (level, affinity, locked) -> new WeaponState(level, Affinity.byName(affinity), locked)));

    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WeaponState::upgradeLevel,
            ByteBufCodecs.STRING_UTF8, state -> state.affinity().name(),
            ByteBufCodecs.BOOL, WeaponState::artLocked,
            (level, affinity, locked) -> new WeaponState(level, Affinity.byName(affinity), locked));

    private WeaponStateCodec() {
    }
}
