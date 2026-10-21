package com.dsh.eldencombat.core;

import com.google.gson.JsonObject;

/**
 * 护符（Talisman）参数：**纯逻辑，不依赖 MC 类型**，便于单测与数据包覆盖。
 *
 * <p>数据目录：{@code data/<namespace>/talismans/<id>.json}
 * <pre>
 * {
 *   "name": "自定义护符（旧字段兼容示例）",
 *   "series": "jar",        // 可选：系列（同系列限制用；不写 = 无系列，不受同系列限制）
 *   "maxFp": 30,            // FP 上限加成（绝对值）
 *   "staminaRegen": 0.25,   // 精力恢复速率加成（+25%）
 *   "damage": 0.10,         // 造成伤害加成（+10%）
 *   "damageReduction": 0.12,// 受到伤害减免（-12%）
 *   "heal": 2.0             // 治疗量加成（绝对值）
 * }
 * </pre>
 * 所有字段都可省略，省略即 0。数值由服务端应用，客户端只显示文案。
 *
 * <p>{@code id} 是**完整 namespaced id**（见 {@link TalismanIds}）：不同 namespace 的同名护符
 * 因此不会互相覆盖（§3.6）。聚合与上下限见 {@link TalismanAggregate} / {@link TalismanRules}。
 */
public record TalismanSpec(String id, String displayName, String series, float maxFp, float staminaRegen,
                           float damage, float damageReduction, float heal, TalismanEffects effects) {

    public TalismanSpec(String id, String displayName, String series, float maxFp, float staminaRegen,
                        float damage, float damageReduction, float heal) {
        this(id, displayName, series, maxFp, staminaRegen, damage, damageReduction, heal, TalismanEffects.NONE);
    }

    public static final TalismanSpec NONE =
            new TalismanSpec("", "", "", 0f, 0f, 0f, 0f, 0f);

    /** 兼容构造：不写系列（等价于系列为空串）。 */
    public TalismanSpec(String id, String displayName, float maxFp, float staminaRegen,
                        float damage, float damageReduction, float heal) {
        this(id, displayName, "", maxFp, staminaRegen, damage, damageReduction, heal);
    }

    public boolean isNone() {
        return id == null || id.isEmpty();
    }

    /** 精力恢复倍率（基础 1.0）。 */
    public float staminaRegenMultiplier() {
        return Math.max(0.1f, 1f + staminaRegen);
    }

    /** 伤害倍率（基础 1.0），下限 0.1 防止负数配置把伤害打反。 */
    public float damageMultiplier() {
        return Math.max(0.1f, 1f + damage);
    }

    /**
     * 受伤倍率（基础 1.0），夹在 [0.1, 2.0]。
     *
     * <p>允许 &gt; 1：像"烙印"这类护符是**有代价的**（提高上限但更脆），
     * 只禁止把倍率写成 0 或负数（那会让战斗结算失去意义）。
     */
    public float damageTakenMultiplier() {
        return Math.min(2f, Math.max(0.1f, 1f - damageReduction));
    }

    public static TalismanSpec parse(String id, JsonObject json) {
        if (json == null) {
            return NONE;
        }
        String name = json.has("name") ? json.get("name").getAsString() : id;
        String series = json.has("series") && json.get("series").isJsonPrimitive()
                ? json.get("series").getAsString().trim()
                : "";
        return new TalismanSpec(id, name, series,
                number(json, "maxFp"),
                number(json, "staminaRegen"),
                number(json, "damage"),
                number(json, "damageReduction"),
                number(json, "heal"),
                TalismanEffects.parse(json.has("effects") ? json.getAsJsonObject("effects") : null));
    }

    private static float number(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return 0f;
        }
        float value = json.get(key).getAsFloat();
        return Float.isFinite(value) ? value : 0f;
    }

    public String describe() {
        return String.format("%s(%s) FP上限+%.0f 精力恢复+%.0f%% 伤害+%.0f%% 减伤%.0f%% 治疗+%.1f",
                displayName, id, maxFp, staminaRegen * 100f, damage * 100f, damageReduction * 100f, heal)
                + (effects.equals(TalismanEffects.NONE) ? "" : " " + effects.describe());
    }
}
