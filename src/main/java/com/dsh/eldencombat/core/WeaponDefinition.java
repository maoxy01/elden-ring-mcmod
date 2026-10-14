package com.dsh.eldencombat.core;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一件武器的完整定义（基础伤害 + 补正 + 差异化数值），**数据包驱动**。
 *
 * <p>刻意不在物品类里硬编码：{@code WeaponDefinition} 是"武器基础数据"的唯一来源，
 * {@link WeaponProfile} 提供 reach / 削韧 / 重量，本记录补上基础伤害与补正档位。
 *
 * <p>伤害**不在物品属性里写死**，而是命中结算时按公式现算
 * （否则 buff / 附魔 / 质变会双重计算）。
 */
public record WeaponDefinition(
        String id,
        WeaponProfile profile,
        boolean remembrance,
        double baseDamage,
        double correctionRating,
        Map<AttributeKind, CorrectionGrade> baseGrades,
        boolean artReplaceable) {

    /** 8 件追忆武器（战灰不可替换；强化上限 +5）。 */
    public static final java.util.Set<String> REMEMBRANCE_IDS = java.util.Set.of(
            "sacred_relic_sword", "marikas_hammer", "malikeths_black_blade", "blasphemous_blade",
            "hand_of_malenia", "starscourge_greatsword", "mohgwyns_sacred_spear", "morgotts_cursed_sword");

    public WeaponDefinition {
        id = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
        baseDamage = Math.max(1.0, baseDamage);
        correctionRating = Math.max(0.0, correctionRating);
        Map<AttributeKind, CorrectionGrade> grades = new EnumMap<>(AttributeKind.class);
        if (baseGrades != null) {
            grades.putAll(baseGrades);
        }
        baseGrades = Map.copyOf(grades);
    }

    public int maxLevel() {
        return UpgradeMath.maxLevel(remembrance);
    }

    public boolean lockedArt() {
        return !artReplaceable;
    }

    /** 该武器在某状态下实际使用的补正档位：质变优先，无质变时用武器自带的档位。 */
    public Map<AttributeKind, CorrectionGrade> gradesFor(WeaponState state) {
        if (state != null && state.affinity() != null && state.affinity().hasCorrection()) {
            return state.affinity().gradesAt(state.progress(this));
        }
        return baseGrades;
    }

    /** 基础伤害（已含质变惩罚与强化曲线） */
    public double baseDamageAt(WeaponState state) {
        double penalty = state == null ? 1.0 : state.affinity().penalty();
        return baseDamage * penalty * UpgradeMath.baseMultiplier(progressOf(state));
    }

    /** 补正伤害（已含强化曲线与属性软化） */
    public double correctionDamageAt(WeaponState state, AttributeSet attributes) {
        double sum = 0.0;
        if (attributes != null) {
            for (Map.Entry<AttributeKind, CorrectionGrade> entry : gradesFor(state).entrySet()) {
                sum += entry.getValue().coefficient() * AttributeKind.softness(attributes.points(entry.getKey()));
            }
        }
        return correctionRating * sum * UpgradeMath.correctionMultiplier(progressOf(state));
    }

    /**
     * 最终伤害 = 基础伤害 × B(t) + 补正伤害 × S(t)。
     *
     * <p>补正部分 = {@code correctionRating × Σ(档位系数 × 软化(属性点))}：
     * 强化只放大补正，不改变"投哪项属性"的决策，这正是两条曲线分开的目的。
     */
    public double damageAt(WeaponState state, AttributeSet attributes) {
        return baseDamageAt(state) + correctionDamageAt(state, attributes);
    }

    private double progressOf(WeaponState state) {
        return state == null ? 0.0 : state.progress(this);
    }

    public WeaponState fresh() {
        return new WeaponState(0, Affinity.NONE, lockedArt()).sanitized(this);
    }

    /** 由 {@link WeaponProfile} 的推荐质变推出首版默认补正档位（后续可用数据包覆盖）。 */
    public static Map<AttributeKind, CorrectionGrade> defaultGrades(String recommendedAffinity) {
        Map<AttributeKind, CorrectionGrade> grades = new EnumMap<>(AttributeKind.class);
        switch (recommendedAffinity == null ? "NONE" : recommendedAffinity.toUpperCase(java.util.Locale.ROOT)) {
            case "HEAVY" -> grades.put(AttributeKind.STRENGTH, CorrectionGrade.B);
            case "KEEN" -> grades.put(AttributeKind.DEXTERITY, CorrectionGrade.B);
            case "QUALITY" -> {
                grades.put(AttributeKind.STRENGTH, CorrectionGrade.C);
                grades.put(AttributeKind.DEXTERITY, CorrectionGrade.C);
            }
            case "MAGIC" -> grades.put(AttributeKind.INTELLIGENCE, CorrectionGrade.B);
            case "SACRED" -> grades.put(AttributeKind.FAITH, CorrectionGrade.B);
            case "BLOOD" -> {
                grades.put(AttributeKind.DEXTERITY, CorrectionGrade.D);
                grades.put(AttributeKind.ARCANE, CorrectionGrade.D);
            }
            case "OCCULT" -> grades.put(AttributeKind.ARCANE, CorrectionGrade.B);
            default -> grades.put(AttributeKind.STRENGTH, CorrectionGrade.D);
        }
        return grades;
    }

    /** 首版内置定义：基础伤害按 profile 的重量与类别给一个同人量级值。 */
    public static WeaponDefinition of(WeaponProfile profile, boolean remembrance) {
        double base = 8.0 + profile.weight() * 1.6 + (profile.poiseLight() * 0.35);
        double rating = 4.0 + profile.weight() * 0.9;
        return new WeaponDefinition(profile.id(), profile, remembrance, Math.round(base * 10) / 10.0,
                Math.round(rating * 10) / 10.0, defaultGrades(profile.recommendedAffinity()),
                !remembrance);
    }

    public static Map<String, WeaponDefinition> defaultsById() {
        Map<String, WeaponDefinition> map = new LinkedHashMap<>();
        for (WeaponProfile profile : WeaponProfiles.defaults()) {
            boolean remembrance = REMEMBRANCE_IDS.contains(profile.id());
            map.put(profile.id(), of(profile, remembrance));
        }
        return map;
    }

    public String describe() {
        return String.format("%s%s 基础伤害%.1f 补正强度%.1f 满级+%d 档位=%s",
                id, remembrance ? "(追忆)" : "", baseDamage, correctionRating, maxLevel(), baseGrades);
    }
}
