package com.dsh.eldencombat.core;

/**
 * 补正档位 → 每点属性的系数（《成长路径与手感收口方案》§2.5）。
 *
 * <p>MC 本身没有补正系统，这里是它的接口契约：属性系统（力量/灵巧/智力/信仰/神秘）
 * 后做时直接对接这张表；先做的部分用它算"属性加点值不值"。
 */
public enum CorrectionGrade {
    /** 该属性完全不参与补正（例如火焰质变的"无补正"） */
    NONE(0.00),
    E(0.10),
    D(0.20),
    C(0.35),
    B(0.52),
    A(0.70),
    S(0.90);

    private final double coefficient;

    CorrectionGrade(double coefficient) {
        this.coefficient = coefficient;
    }

    /** 每点属性带来的补正系数 */
    public double coefficient() {
        return coefficient;
    }

    public boolean isActive() {
        return this != NONE;
    }

    /** 往上一档（用于"A~S"这种随强化等级上浮的档位）。 */
    public CorrectionGrade upgrade() {
        return switch (this) {
            case NONE -> NONE;
            case E -> D;
            case D -> C;
            case C -> B;
            case B -> A;
            case A, S -> S;
        };
    }

    public static CorrectionGrade byName(String name) {
        if (name == null) {
            return NONE;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return NONE;
        }
    }
}
