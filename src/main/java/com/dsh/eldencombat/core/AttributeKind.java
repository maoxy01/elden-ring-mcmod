package com.dsh.eldencombat.core;

/**
 * 五项属性与**软化函数**（《成长路径与手感收口方案》§2.5 的接口契约）。
 *
 * <p>软化函数的意义是防止单项属性无脑堆到 99：三段衰减是硬收益递减，
 * 让玩家在 40 点左右开始考虑第二属性——"优质"质变与混合构建才有存在空间。
 *
 * <p>属性加点 UI 不在本轮范围；这里先定契约，数值系统（命令式加点 + 存档附件）先跑通。
 */
public enum AttributeKind {
    STRENGTH("strength", "力量"),
    DEXTERITY("dexterity", "灵巧"),
    INTELLIGENCE("intelligence", "智力"),
    FAITH("faith", "信仰"),
    ARCANE("arcane", "神秘");

    private final String key;
    private final String label;

    AttributeKind(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /**
     * 软化：{@code x ≤ 20} 时 1:1；之后每段收益递减
     * （20-40 段 ×0.6、40-60 段 ×0.25、60 以上 ×0.10）。
     */
    public static double softness(int points) {
        double x = Math.max(0, points);
        if (x <= 20) {
            return x;
        }
        if (x <= 40) {
            return 20 + (x - 20) * 0.6;
        }
        if (x <= 60) {
            return 32 + (x - 40) * 0.25;
        }
        return 37 + (x - 60) * 0.10;
    }

    /** 属性点上限（原作 99；这里先按 99 定契约） */
    public static final int MAX_POINTS = 99;

    public static AttributeKind byKey(String key) {
        if (key != null) {
            for (AttributeKind kind : values()) {
                if (kind.key.equalsIgnoreCase(key.trim())) {
                    return kind;
                }
            }
        }
        return null;
    }
}
