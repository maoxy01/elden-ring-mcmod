package com.dsh.eldencombat.server.skills;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个追忆战技的可调参数（同人原型值，非原作数值）。
 * 数据文件：{@code data/eldencombat/skill_parameters/remembrance_skills.json}；
 * 缺失或非法时回退到 {@link #defaults()}，保证不会因为配置坏掉而无法施放。
 */
public record RemembranceSkillSpec(
        String id,
        String itemId,
        float fpCost,
        float fpCostFollowup,
        int windupTicks,
        int activeTicks,
        int recoveryTicks,
        int followupWindowTicks,
        int maxStages,
        boolean allowsSkillJump,
        double maxHorizontal,
        double maxVertical,
        double effectRange,
        int effectLifespanTicks,
        double effectRadius,
        float healCap,
        int cooldownTicks,
        int maxStageTicks,
        float damage,
        float poiseDamage,
        int followupWindupTicks,
        int followupActiveTicks) {

    /**
     * 参数文件路径。**刻意不放在 {@code data/<ns>/skill_parameters/}**：
     * Epic Fight 自己的 {@code SkillReloadListener} 以 {@code SimpleJsonResourceReloadListener(GSON, "skill_parameters")}
     * 扫描**所有命名空间**的该目录，并把每个文件名当成技能 id
     * （{@code SkillReloadListener.java:121,128-132}），会对不存在的技能刷
     * "Skill ... doesn't exist in the registry." 警告。本模组的参数是"一个聚合文件管八个战技"，
     * 与 EF"一技能一文件"的约定不同，因此放在自己的目录里。
     */
    public static final String RESOURCE = "/data/eldencombat/dsh_skills/remembrance_parameters.json";

    public int totalNominalTicks() {
        return windupTicks + activeTicks + recoveryTicks;
    }

    public boolean hasFollowup() {
        return followupWindowTicks > 0;
    }

    public boolean multiStage() {
        return maxStages > 1;
    }

    /** 第 stageIndex 段（0 起）的 FP 消耗 */
    public float costForStage(int stageIndex) {
        if (stageIndex <= 0) {
            return fpCost;
        }
        return fpCostFollowup > 0 ? fpCostFollowup : fpCost;
    }

    public int stageBudget() {
        return maxStageTicks > 0 ? maxStageTicks : totalNominalTicks() + followupWindowTicks + 20;
    }

    public int windupForStage(int activeStages) {
        if (activeStages == 0) return windupTicks;
        return followupWindupTicks > 0 ? followupWindupTicks : windupTicks;
    }

    public int activeForStage(int stageIndex) {
        if (stageIndex <= 1) return activeTicks;
        return followupActiveTicks > 0 ? followupActiveTicks : activeTicks;
    }

    /** 诊断文本（命令 / 日志用） */
    public String describe() {
        return String.format("%s 物品=%s FP=%.0f(追击%.0f) 蓄势%d+生效%d+收招%d 追击窗%d 段数%d 跃起=%s "
                        + "位移(%.1f格/%.1f格) 效果(%.1f格/%dtick/半径%.1f) 伤害%.1f/削韧%.1f 冷却%d",
                id, itemId, fpCost, fpCostFollowup, windupTicks, activeTicks, recoveryTicks,
                followupWindowTicks, maxStages, allowsSkillJump, maxHorizontal, maxVertical,
                effectRange, effectLifespanTicks, effectRadius, damage, poiseDamage, cooldownTicks);
    }

    public static List<RemembranceSkillSpec> defaults() {
        List<RemembranceSkillSpec> list = new ArrayList<>();
        list.add(new RemembranceSkillSpec("wave_of_gold", "sacred_relic_sword", 20f, 0f,
                10, 4, 5, 0, 1, false, 0.0, 0.0, 16.0, 30, 3.0, 0f, 0, 0, 14f, 12f, 0, 0));
        list.add(new RemembranceSkillSpec("gold_breaker", "marikas_hammer", 22f, 0f,
                8, 16, 5, 0, 1, true, 4.0, 2.0, 3.0, 20, 3.0, 0f, 0, 0, 26f, 30f, 0, 0));
        list.add(new RemembranceSkillSpec("destined_death", "malikeths_black_blade", 25f, 0f,
                8, 24, 5, 0, 3, true, 3.0, 2.5, 4.0, 60, 3.5, 0f, 0, 0, 12f, 14f, 0, 0));
        list.add(new RemembranceSkillSpec("takers_flames", "blasphemous_blade", 20f, 0f,
                9, 4, 5, 0, 1, false, 0.0, 0.0, 12.0, 40, 2.5, 4f, 0, 0, 13f, 10f, 0, 0));
        list.add(new RemembranceSkillSpec("waterfowl_dance", "hand_of_malenia", 28f, 0f,
                10, 36, 5, 0, 3, true, 6.0, 2.5, 3.0, 20, 3.0, 0f, 0, 0, 9f, 8f, 0, 0));
        list.add(new RemembranceSkillSpec("starcaller_cry", "starscourge_greatsword", 15f, 15f,
                8, 6, 5, 12, 2, true, 0.0, 1.5, 5.0, 20, 5.0, 0f, 0, 0, 10f, 16f, 6, 8));
        list.add(new RemembranceSkillSpec("bloodboon_ritual", "mohgwyns_sacred_spear", 15f, 15f,
                6, 4, 2, 10, 3, false, 0.0, 0.0, 4.0, 20, 4.0, 0f, 0, 0, 11f, 10f, 6, 4));
        list.add(new RemembranceSkillSpec("cursed_blood_slice", "morgotts_cursed_sword", 12f, 8f,
                8, 6, 5, 10, 2, true, 3.0, .7, 4.0, 20, 3.0, 0f, 0, 0, 13f, 12f, 4, 4));
        return list;
    }

    public static Map<String, RemembranceSkillSpec> defaultsById() {
        Map<String, RemembranceSkillSpec> map = new LinkedHashMap<>();
        for (RemembranceSkillSpec spec : defaults()) {
            map.put(spec.id(), spec);
        }
        return map;
    }

    /** 从 JSON 数组读取；缺项用默认值补齐，非法项忽略。 */
    public static Map<String, RemembranceSkillSpec> parse(Reader reader) {
        Map<String, RemembranceSkillSpec> result = defaultsById();
        try {
            Gson gson = new GsonBuilder().create();
            List<RemembranceSkillSpec> parsed = gson.fromJson(reader,
                    new TypeToken<List<RemembranceSkillSpec>>() {
                    }.getType());
            if (parsed != null) {
                for (RemembranceSkillSpec spec : parsed) {
                    if (spec != null && spec.id() != null && result.containsKey(spec.id())) {
                        result.put(spec.id(), spec.sanitized());
                    }
                }
            }
        } catch (RuntimeException exception) {
            com.dsh.eldencombat.core.CoreLog.LOGGER.error("读取追忆战技参数失败，使用内置默认值", exception);
        }
        return result;
    }

    public static Map<String, RemembranceSkillSpec> load() {
        Map<String, RemembranceSkillSpec> loaded;
        try (InputStream stream = RemembranceSkillSpec.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                com.dsh.eldencombat.core.CoreLog.LOGGER.warn("未找到 {}，使用内置默认参数", RESOURCE);
                loaded = defaultsById();
            } else {
                loaded = parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (Exception exception) {
            com.dsh.eldencombat.core.CoreLog.LOGGER.error("加载追忆战技参数异常，使用内置默认值", exception);
            loaded = defaultsById();
        }
        return LocalSourceMotion.apply(loaded);
    }

    private RemembranceSkillSpec sanitized() {
        float cost = fpCost < 0 ? 0f : fpCost;
        float followupCost = fpCostFollowup < 0 ? 0f : fpCostFollowup;
        int windup = Math.max(1, windupTicks);
        int active = Math.max(1, activeTicks);
        int recovery = Math.max(0, recoveryTicks);
        int window = Math.max(0, followupWindowTicks);
        int stages = Math.min(3, Math.max(1, maxStages));
        double horizontal = Math.max(0.0, maxHorizontal);
        double vertical = Math.max(0.0, maxVertical);
        double range = Math.max(0.5, effectRange);
        int lifespan = Math.max(1, effectLifespanTicks);
        double radius = Math.max(0.5, effectRadius);
        float heal = Math.max(0f, healCap);
        int cooldown = Math.max(0, cooldownTicks);
        int budget = maxStageTicks > 0 ? maxStageTicks : windup + active + recovery + window + 20;
        float safeDamage = damage > 0 ? damage : 10f;
        float safePoise = poiseDamage >= 0 ? poiseDamage : 8f;
        RemembranceSkillSpec base = defaultsById().get(id);
        return new RemembranceSkillSpec(id, base.itemId(), cost, followupCost, windup, active, recovery, window,
                stages, allowsSkillJump, horizontal, vertical, range, lifespan, radius, heal, cooldown, budget,
                safeDamage, safePoise,
                Math.max(0, followupWindupTicks > 0 ? followupWindupTicks : base.followupWindupTicks()),
                Math.max(0, followupActiveTicks > 0 ? followupActiveTicks : base.followupActiveTicks()));
    }
}
