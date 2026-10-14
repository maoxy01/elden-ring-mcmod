package com.dsh.eldencombat.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 药物参数（消耗品加成）：**纯逻辑，不依赖 MC 类型**。
 *
 * <p>数据目录：{@code data/<namespace>/consumables/<id>.json}，{@code id} 必须等于物品注册名
 * （例如物品 {@code eldencombat:flame_grant_me_strength} 对应同名文件）。
 * <pre>
 * {
 *   "name": "火焰赐予我力量",
 *   "heal": 0,
 *   "effects": [{"effect": "minecraft:strength", "amplifier": 1, "seconds": 60}]
 * }
 * </pre>
 * 效果按原版注册名解析；解析失败的效果会被丢弃并记录，不影响其余效果。
 */
public record ConsumableSpec(String id, String displayName, float heal, List<Effect> effects) {

    public static final ConsumableSpec NONE = new ConsumableSpec("", "", 0f, List.of());

    /** 一条药水效果：{@code effect} 是原版效果注册名。 */
    public record Effect(String effectId, int amplifier, int durationTicks) {
        public Effect sanitized() {
            return new Effect(effectId, Math.max(0, Math.min(9, amplifier)), Math.max(1, durationTicks));
        }
    }

    public boolean isNone() {
        return id == null || id.isEmpty();
    }

    public static ConsumableSpec parse(String id, JsonObject json) {
        if (json == null) {
            return NONE;
        }
        String name = json.has("name") ? json.get("name").getAsString() : id;
        float heal = json.has("heal") && json.get("heal").isJsonPrimitive() ? json.get("heal").getAsFloat() : 0f;
        List<Effect> effects = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("effects")) {
                Effect effect = parseEffect(element);
                if (effect != null) {
                    effects.add(effect.sanitized());
                }
            }
        }
        return new ConsumableSpec(id, name, Float.isFinite(heal) ? Math.max(0f, heal) : 0f, List.copyOf(effects));
    }

    private static Effect parseEffect(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject effect = element.getAsJsonObject();
        if (!effect.has("effect")) {
            return null;
        }
        String effectId = effect.get("effect").getAsString();
        if (effectId == null || effectId.isBlank()) {
            return null;
        }
        int amplifier = effect.has("amplifier") ? effect.get("amplifier").getAsInt() : 0;
        int seconds = effect.has("seconds") ? effect.get("seconds").getAsInt()
                : (effect.has("ticks") ? effect.get("ticks").getAsInt() / 20 : 30);
        return new Effect(effectId, amplifier, Math.max(1, seconds) * 20);
    }

    /** 供命令与日志使用的一行摘要。 */
    public String describe() {
        StringBuilder builder = new StringBuilder(displayName).append('(').append(id).append(") 治疗+")
                .append(String.format("%.1f", heal));
        if (!effects.isEmpty()) {
            builder.append(" 效果=");
            for (int i = 0; i < effects.size(); i++) {
                Effect effect = effects.get(i);
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(effect.effectId()).append('x').append(effect.amplifier() + 1)
                        .append('@').append(effect.durationTicks() / 20).append('s');
            }
        }
        return builder.toString();
    }
}
