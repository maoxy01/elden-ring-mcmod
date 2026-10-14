package com.dsh.eldencombat.server.skills;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link PlayerFpState} 与存档之间的编解码适配层。
 * 放在这一层是为了让 PlayerFpState 保持不依赖 Minecraft 类型，便于纯逻辑测试。
 */
public final class FpStateCodec {

    /** 只用于序列化的扁平 DTO */
    public record FpDto(float fp, float maxFp, String lastSkillId, long lastCastTick, int refillCount,
                        Map<String, Long> cooldowns) {
    }

    public static final Codec<Map<String, Long>> COOLDOWN_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.LONG);

    public static final Codec<FpDto> DTO_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("fp").orElse(PlayerFpState.DEFAULT_MAX_FP).forGetter(FpDto::fp),
            Codec.FLOAT.fieldOf("max_fp").orElse(PlayerFpState.DEFAULT_MAX_FP).forGetter(FpDto::maxFp),
            Codec.STRING.fieldOf("last_skill").orElse("").forGetter(FpDto::lastSkillId),
            Codec.LONG.fieldOf("last_cast_tick").orElse(Long.MIN_VALUE).forGetter(FpDto::lastCastTick),
            Codec.INT.fieldOf("refill_count").orElse(0).forGetter(FpDto::refillCount),
            COOLDOWN_CODEC.fieldOf("cooldowns").orElse(Map.of()).forGetter(FpDto::cooldowns)
    ).apply(instance, FpDto::new));

    /** 真正挂到 attachment 上的 codec */
    public static final Codec<PlayerFpState> CODEC = DTO_CODEC.xmap(FpStateCodec::toState, FpStateCodec::fromState);

    private FpStateCodec() {
    }

    public static PlayerFpState toState(FpDto dto) {
        PlayerFpState state = new PlayerFpState();
        state.setMaxFp(dto.maxFp() <= 0 ? PlayerFpState.DEFAULT_MAX_FP : dto.maxFp());
        state.restore(dto.fp(), dto.lastSkillId(), dto.lastCastTick(), dto.refillCount(),
                dto.cooldowns() == null ? new HashMap<>() : new LinkedHashMap<>(dto.cooldowns()));
        return state;
    }

    public static FpDto fromState(PlayerFpState state) {
        return new FpDto(state.fp(), state.maxFp(), state.lastSkillId(), state.lastCastTick(),
                state.refillCount(), state.cooldownsView());
    }
}
