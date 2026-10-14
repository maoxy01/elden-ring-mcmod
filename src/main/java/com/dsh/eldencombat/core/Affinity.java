package com.dsh.eldencombat.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * 8 种质变（《成长路径与手感收口方案》§2.4）。
 *
 * <p>质变的定义是"**用基础伤害换补正档位**"，规则一句话能讲清，玩家才会去算：
 * <pre>
 *   质变后基础伤害 = 原基础伤害 × penalty        // penalty ∈ [0.85, 0.95]
 *   质变后补正档位 = 按质变类型重写
 * </pre>
 *
 * <p>惩罚系数压在 0.85–0.95 的窄带是硬约束：
 * P &gt; 0.95 时人人质变、构建分歧消失；P &lt; 0.85 时不质变就是亏，同样没有分歧。
 *
 * <p>档位写的是"基础档"，随强化等级上浮一档（对应原作 A~S 的写法）：
 * 见 {@link #gradesAt(double)}。
 */
public enum Affinity {
    /** 无质变：保留武器原本的补正档位 */
    NONE("无", 1.00, "PHYSICAL"),
    /** 厚重：力量 A~S，无额外代价 */
    HEAVY("厚重", 0.92, "PHYSICAL", AttributeKind.STRENGTH, CorrectionGrade.A),
    /** 锋利：灵巧 A~S */
    KEEN("锋利", 0.92, "PHYSICAL", AttributeKind.DEXTERITY, CorrectionGrade.A),
    /** 优质：力量 B / 灵巧 B（不能只堆一项） */
    QUALITY("优质", 0.88, "PHYSICAL",
            AttributeKind.STRENGTH, CorrectionGrade.B, AttributeKind.DEXTERITY, CorrectionGrade.B),
    /** 魔力：智力 A~S，伤害类型转魔力 */
    MAGIC("魔力", 0.90, "MAGIC", AttributeKind.INTELLIGENCE, CorrectionGrade.A),
    /** 火焰：**唯一无补正**的质变，惩罚最轻（属性点没投对时的兜底） */
    FIRE("火焰", 0.95, "FIRE"),
    /** 神圣：信仰 A~S，伤害类型转神圣 */
    SACRED("神圣", 0.90, "HOLY", AttributeKind.FAITH, CorrectionGrade.A),
    /** 血：灵巧 C / 神秘 C，附加出血累积 */
    BLOOD("血", 0.87, "PHYSICAL",
            AttributeKind.DEXTERITY, CorrectionGrade.C, AttributeKind.ARCANE, CorrectionGrade.C),
    /** 神秘：神秘 A~S，附加异常抗性穿透 */
    OCCULT("神秘", 0.90, "PHYSICAL", AttributeKind.ARCANE, CorrectionGrade.A);

    /** 强化到多高时补正档位上浮一档（对应原作 "A~S" 的写法） */
    private static final double GRADE_STEP_AT = 0.6;

    private final String label;
    private final double penalty;
    private final String damageType;
    private final Map<AttributeKind, CorrectionGrade> grades;

    Affinity(String label, double penalty, String damageType, Object... gradePairs) {
        this.label = label;
        this.penalty = penalty;
        this.damageType = damageType;
        Map<AttributeKind, CorrectionGrade> map = new EnumMap<>(AttributeKind.class);
        for (int i = 0; i + 1 < gradePairs.length; i += 2) {
            map.put((AttributeKind) gradePairs[i], (CorrectionGrade) gradePairs[i + 1]);
        }
        this.grades = Map.copyOf(map);
    }

    public String label() {
        return label;
    }

    /** 基础伤害惩罚系数（0.85–0.95） */
    public double penalty() {
        return penalty;
    }

    public String damageType() {
        return damageType;
    }

    public Map<AttributeKind, CorrectionGrade> baseGrades() {
        return grades;
    }

    public boolean isNone() {
        return this == NONE;
    }

    public boolean hasCorrection() {
        return !grades.isEmpty();
    }

    /**
     * 该质变在某个强化进度下的补正档位。
     *
     * @param progress 归一化强化进度 {@code t = L / Lmax}，取值 0..1
     */
    public Map<AttributeKind, CorrectionGrade> gradesAt(double progress) {
        boolean stepped = progress >= GRADE_STEP_AT;
        Map<AttributeKind, CorrectionGrade> result = new EnumMap<>(AttributeKind.class);
        for (Map.Entry<AttributeKind, CorrectionGrade> entry : grades.entrySet()) {
            result.put(entry.getKey(), stepped ? entry.getValue().upgrade() : entry.getValue());
        }
        return result;
    }

    public static Affinity byName(String name) {
        if (name == null) {
            return NONE;
        }
        String trimmed = name.trim().toUpperCase(java.util.Locale.ROOT);
        for (Affinity value : values()) {
            if (value.name().equals(trimmed)) {
                return value;
            }
        }
        return NONE;
    }
}
