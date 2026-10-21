package com.dsh.eldencombat.core;

import com.google.gson.JsonObject;
import java.util.List;

/** Scoped passive effects. Resource amounts use Minecraft units, not Elden Ring stat points. */
public record TalismanEffects(float skillDamage, float physicalReduction, float pvpPhysicalReduction,
                              float attackDamageTaken, float staminaRegenPerSecond,
                              float maxHealth, float maxStamina, int strength, int dexterity) {
    public static final TalismanEffects NONE = new TalismanEffects(0, 0, 0, 0, 0, 0, 0, 0, 0);

    public TalismanEffects {
        skillDamage = bounded(skillDamage, 10);
        physicalReduction = bounded(physicalReduction, .95f);
        pvpPhysicalReduction = bounded(pvpPhysicalReduction, .95f);
        attackDamageTaken = bounded(attackDamageTaken, 1);
        staminaRegenPerSecond = bounded(staminaRegenPerSecond, 1000);
        maxHealth = bounded(maxHealth, 1000);
        maxStamina = bounded(maxStamina, 10000);
        strength = Math.clamp(strength, 0, 99);
        dexterity = Math.clamp(dexterity, 0, 99);
    }

    private static float bounded(float value, float max) {
        return Float.isFinite(value) ? Math.clamp(value, 0, max) : 0;
    }

    public static TalismanEffects parse(JsonObject json) {
        if (json == null) return NONE;
        return new TalismanEffects(number(json, "skillDamage"), number(json, "physicalReduction"),
                number(json, "pvpPhysicalReduction"), number(json, "attackDamageTaken"),
                number(json, "staminaRegenPerSecond"), number(json, "maxHealth"),
                number(json, "maxStamina"), (int) number(json, "strength"), (int) number(json, "dexterity"));
    }

    private static float number(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsFloat() : 0;
    }

    public static TalismanEffects of(List<TalismanSpec> specs) {
        float skill = 0, physical = 1, pvp = 1, taken = 1, regen = 0, health = 0, stamina = 0;
        int str = 0, dex = 0;
        for (TalismanSpec spec : specs) {
            if (spec == null || spec.isNone()) continue;
            TalismanEffects e = spec.effects();
            skill += e.skillDamage;
            physical *= 1 - e.physicalReduction;
            pvp *= 1 - e.pvpPhysicalReduction;
            taken *= 1 + e.attackDamageTaken;
            regen += e.staminaRegenPerSecond;
            health += e.maxHealth;
            stamina += e.maxStamina;
            str += e.strength;
            dex += e.dexterity;
        }
        return new TalismanEffects(skill, 1 - physical, 1 - pvp, taken - 1, regen, health, stamina, str, dex);
    }

    public float incomingMultiplier(boolean attack, boolean physical, boolean pvp) {
        return (attack ? 1 + attackDamageTaken : 1)
                * (physical ? 1 - (pvp ? pvpPhysicalReduction : physicalReduction) : 1);
    }

    public AttributeSet attributes(AttributeSet base) {
        return base.with(AttributeKind.STRENGTH, base.strength() + strength)
                .with(AttributeKind.DEXTERITY, base.dexterity() + dexterity);
    }

    public String describe() {
        var parts = new java.util.ArrayList<String>();
        if (skillDamage > 0) parts.add(String.format("战技伤害+%.0f%%", skillDamage * 100));
        if (physicalReduction > 0) parts.add(String.format("物理减伤PvE %.0f%%/PvP %.0f%%", physicalReduction * 100, pvpPhysicalReduction * 100));
        if (staminaRegenPerSecond > 0) parts.add(String.format("精力恢复+%.2f/秒", staminaRegenPerSecond));
        if (strength > 0 || dexterity > 0) parts.add("力量+" + strength + " 灵巧+" + dexterity);
        if (maxHealth > 0 || maxStamina > 0) parts.add(String.format("生命上限+%.0f 精力上限+%.0f（MC适配）", maxHealth, maxStamina));
        if (attackDamageTaken > 0) parts.add(String.format("受到攻击伤害+%.0f%%", attackDamageTaken * 100));
        return String.join("，", parts);
    }
}
