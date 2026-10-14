package com.dsh.eldencombat.core;

/**
 * 单件武器的差异化数值（《成长路径与手感收口方案》§5）。
 *
 * <p>这是"33 件类型武器不只是美术差异"的数据载体。四个维度：
 * <ul>
 *   <li>{@code reach} —— 攻击距离，落到原版属性 {@code Attributes.ENTITY_INTERACTION_RANGE}
 *       （基准 3.0），所以长柄武器真的能更远打到人，而不是换个数字好看；</li>
 *   <li>{@code poiseLight}/{@code poiseHeavy} —— 削韧，接进现有的 {@code HitWindow.poiseDamage}
 *       （它决定"这一下能不能打断 Boss 的动作"以及多快打出崩解窗口）；</li>
 *   <li>{@code weight} —— 重量，**不引入负重系统**（那会连锁改动已验收的翻滚），
 *       只按公式映射到攻速与耐力消耗；</li>
 *   <li>{@code recommendedAffinity} —— 推荐质变，只用于 UI 提示与排序，不限制玩家选择。</li>
 * </ul>
 *
 * <p>纯数据 + 纯公式，不依赖 MC 类型，因此单测与游戏内自检共用同一套断言。
 */
public record WeaponProfile(
        String id,
        double reach,
        float poiseLight,
        float poiseHeavy,
        double weight,
        String recommendedAffinity) {

    /** 原版生存模式玩家的攻击距离基准 */
    public static final double VANILLA_REACH = 3.0;
    public static final double MIN_REACH = 1.5;
    public static final double MAX_REACH = 6.0;
    public static final double MIN_WEIGHT = 0.5;
    public static final double MAX_WEIGHT = 25.0;
    /** 重量基准：等于此值时攻速与耐力消耗都不修正 */
    public static final double WEIGHT_BASELINE = 5.0;

    /** 远程 / 盾：不参与 reach 与削韧表，走各自的原版逻辑。 */
    public static final String AFFINITY_NONE = "NONE";

    public WeaponProfile {
        id = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
        reach = clamp(reach, MIN_REACH, MAX_REACH);
        poiseLight = (float) clamp(poiseLight, 0.0, 200.0);
        poiseHeavy = (float) clamp(poiseHeavy, 0.0, 200.0);
        weight = clamp(weight, MIN_WEIGHT, MAX_WEIGHT);
        recommendedAffinity = recommendedAffinity == null || recommendedAffinity.isBlank()
                ? AFFINITY_NONE : recommendedAffinity.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** reach 相对原版基准的增量，直接作为属性修饰符的 {@code ADD_VALUE} 值。 */
    public double reachBonus() {
        return reach - VANILLA_REACH;
    }

    /** 是否参与 reach/削韧表（弓弩与盾不参与）。 */
    public boolean isMeleeProfile() {
        return poiseLight > 0f || poiseHeavy > 0f;
    }

    /**
     * 耐力消耗系数：{@code clamp(1.00 + 0.06 × (weight - 5), 0.85, 1.40)}。
     * 重武器每一次挥砍都更贵，这是重量除了攻速之外的第二个代价。
     */
    public double staminaCostMultiplier() {
        return clamp(1.00 + 0.06 * (weight - WEIGHT_BASELINE), 0.85, 1.40);
    }

    /**
     * 攻速修正：{@code clamp(1.00 - 0.02 × (weight - 5), 0.70, 1.15)}。
     *
     * <p>落到原版 {@code Attributes.ATTACK_SPEED} 上（连带影响原版攻击冷却），
     * 这正是方案 §5.3 想要的手感：重武器挥得慢，轻武器可以连。
     */
    public double attackSpeedMultiplier() {
        return clamp(1.00 - 0.02 * (weight - WEIGHT_BASELINE), 0.70, 1.15);
    }

    public float poise(boolean heavy) {
        return heavy ? poiseHeavy : poiseLight;
    }

    /** 该动作是否算"重击"（用于挑轻/重削韧）。 */
    public static boolean isHeavyAction(ActionId id) {
        return id == ActionId.HEAVY_ATTACK || id == ActionId.CHARGED_HEAVY;
    }

    /**
     * 把本档位写进动作规格：耐力消耗乘档位系数、命中窗口的削韧换成该武器的轻/重值。
     *
     * <p>"削韧必须和伤害分开"（方案 §5.2）——所以这里只换削韧与耐力，不碰伤害与时序。
     * 放在 core 里（不依赖 MC 类型），因此单测可以直接验证。
     */
    public ActionSpec applyTo(ActionSpec spec) {
        if (spec == null || !isMeleeProfile()) {
            return spec;
        }
        boolean heavy = isHeavyAction(spec.id());
        java.util.List<HitWindow> windows = new java.util.ArrayList<>(spec.windows().size());
        for (HitWindow window : spec.windows()) {
            windows.add(new HitWindow(window.start(), window.endExclusive(), window.damage(),
                    poise(heavy), window.guardCost(), window.parryable()));
        }
        return new ActionSpec(spec.id(), spec.totalTicks(),
                (float) (spec.staminaCost() * staminaCostMultiplier()),
                java.util.List.copyOf(windows), spec.rollCancelFrom(), spec.consumeTick(), spec.heal(),
                spec.guardBreakTicks());
    }

    /** 诊断文本（命令 / 日志用） */
    public String describe() {
        return String.format("%s reach=%.1f 削韧=%.0f/%.0f 重量=%.1f 攻速×%.2f 耐力×%.2f 推荐质变=%s",
                id, reach, poiseLight, poiseHeavy, weight, attackSpeedMultiplier(), staminaCostMultiplier(),
                recommendedAffinity);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
