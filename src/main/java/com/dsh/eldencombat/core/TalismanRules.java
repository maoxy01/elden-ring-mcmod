package com.dsh.eldencombat.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 护符装备规则（**纯逻辑**，不依赖 Minecraft，可单测）：槽数、重复限制、倍率叠加上下限。
 *
 * <p>§3.3 要求"集中定义"，所以战斗出口不各自夹数值，一律读
 * {@link TalismanAggregate#of(List, TalismanRules)} 的结果。
 *
 * <p>单槽默认与旧容器兼容。这些倍率上限继续约束旧数据包字段；
 * 2026-10-19的内置护符采用 {@link TalismanEffects} 中带适用范围的效果，
 * 不再把战技增伤、物理减伤和所有来源增伤混成同一字段。
 *
 * <p>新增规则（槽数 / 重复 / 上限）全部是配置文件里的可调项，
 * 见 {@code config/eldencombat-talisman.json}。
 */
public final class TalismanRules {

    /** 槽位上限：护符栏再多也只是把上下限更快顶满，不给无限叠乘。 */
    public static final int MAX_SLOTS = 6;

    // ---- 槽位与重复限制 ----
    /** 护符槽位数（默认 1 = 既有单槽行为）。 */
    public int slots = 1;
    /** false = 同一件护符不能装两遍；true = 允许（多槽时才看得出区别）。 */
    public boolean allowDuplicates = false;
    /**
     * false = 同一个"系列"（{@link TalismanSpec#series()}）不能装两件；
     * 数据里没写 {@code series} 的护符视为**无系列**，不受这条限制。
     */
    public boolean allowSameSeries = false;

    // ---- 倍率叠加上下限（先求和再夹紧，不是逐件夹） ----
    /** 伤害加成合计上限（0.5 = 最多 +50%）。 */
    public float maxDamageBonus = 0.5f;
    /** 减伤合计上限（0.6 = 最多 -60% 受伤）。 */
    public float maxDamageReduction = 0.6f;
    /** 精力恢复合计上限（1.0 = 最多 +100%）。 */
    public float maxStaminaBonus = 1.0f;
    /** FP 上限加成合计上限（绝对值）。 */
    public float maxFpBonus = 100f;
    /** 治疗量加成合计上限（绝对值）。 */
    public float maxHealBonus = 20f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile TalismanRules current = new TalismanRules();

    /** 当前生效的规则；永不为 null。 */
    public static TalismanRules current() {
        return current;
    }

    /** 启动路径与 {@code /eldencombat reload} 共用。 */
    public static void reload(Path file) {
        current = loadOrCreate(file);
    }

    /** 只给测试用：把当前规则换成指定值。 */
    public static void install(TalismanRules rules) {
        current = rules == null ? new TalismanRules() : rules;
    }

    public static TalismanRules loadOrCreate(Path file) {
        TalismanRules rules = new TalismanRules();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                TalismanRules parsed = GSON.fromJson(reader, TalismanRules.class);
                if (parsed != null) {
                    rules = parsed;
                }
            } catch (IOException | JsonSyntaxException exception) {
                CoreLog.LOGGER.error("读取护符规则失败，改用内置默认值: " + file, exception);
                rules = new TalismanRules();
            }
        }
        rules.sanitize();
        rules.save(file);
        return rules;
    }

    public void save(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            CoreLog.LOGGER.error("写入护符规则失败: " + file, exception);
        }
    }

    /** 夹掉非法配置：槽数 1..{@link #MAX_SLOTS}；上限不能是 NaN/负数，也不能小于 0。 */
    public void sanitize() {
        slots = clampInt(slots, 1, MAX_SLOTS);
        maxDamageBonus = clamp(maxDamageBonus, 0f, 10f);
        maxDamageReduction = clamp(maxDamageReduction, 0f, 0.95f);
        maxStaminaBonus = clamp(maxStaminaBonus, 0f, 10f);
        maxFpBonus = clamp(maxFpBonus, 0f, 1000f);
        maxHealBonus = clamp(maxHealBonus, 0f, 1000f);
    }

    /** 实际生效的槽数（配置被手改成 0/负数时也不至于把护符栏变成 0 格）。 */
    public int activeSlots() {
        return clampInt(slots, 1, MAX_SLOTS);
    }

    /**
     * 能否再装一件：只判"重复限制"，槽位是否够由容器自己判。
     *
     * <p>刻意由调用方把已装备的**系列**一起传进来，而不是在这里反查数据包——
     * 这条判定要保持纯逻辑，才能单测；也避免把静态配置实例变成可变状态。
     *
     * @param equippedIds    已装备护符的完整 ID（规范化后的）
     * @param equippedSeries 与 {@code equippedIds} **一一对应**的系列（无系列传空串）
     */
    public boolean accepts(List<String> equippedIds, List<String> equippedSeries, TalismanSpec candidate) {
        if (candidate == null || candidate.isNone()) {
            return false;
        }
        if (equippedIds == null || equippedIds.isEmpty()) {
            return true;
        }
        if (!allowDuplicates && equippedIds.contains(candidate.id())) {
            return false;
        }
        if (!allowSameSeries && !candidate.series().isEmpty()) {
            List<String> series = equippedSeries == null ? List.of() : equippedSeries;
            for (int i = 0; i < Math.min(series.size(), equippedIds.size()); i++) {
                // 同款由 allowDuplicates 管；这条只管"同系列但不同款"，否则
                // allowDuplicates=true 会被同系列规则顺手否掉，变成没有意义。
                if (equippedIds.get(i).equals(candidate.id())) {
                    continue;
                }
                if (candidate.series().equals(series.get(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
