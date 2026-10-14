package com.dsh.eldencombat.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战灰解锁的**玩法内来源表**（纯逻辑，可单测）。
 *
 * <p>只描述"击杀什么 → 解锁什么"，不含任何 Minecraft 类型；受理在 {@code AshRewards}（服务端事件监听）。
 * 形态刻意是"一个来源 → 若干战灰"：加一个来源就是加一行。
 * **不在这里造**任务系统、成就系统或随机掉落池。
 *
 * <p>{@link #entries()} 就是实际支持列表——报告里必须照抄它，
 * 不允许把"目录有 3 件战灰"说成"3 件都能在玩法里获得"。
 */
public final class AshRewardTable {

    /** @param source 实体类型 id（完整 namespaced） @param ashes 击杀后解锁的战灰 id（可多件） */
    public record Entry(String source, List<String> ashes) {
        public Entry {
            source = AshUnlockSet.normalize(source);
            ashes = List.copyOf(ashes);
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            // 玛尔基特用的是大剑/特大剑系，所以给斩劈系的"踏前上斩"。改奖励就改这一行。
            new Entry("eldencombat:margit", List.of("eldencombat:stamp_upward_cut")));

    private AshRewardTable() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** 某个来源解锁哪些战灰；没有配置返回空列表（不抛异常）。输入大小写/空格会被规范化。 */
    public static List<String> ashesFor(String sourceId) {
        String key = AshUnlockSet.normalize(sourceId);
        if (key == null) return List.of();
        for (Entry entry : ENTRIES) {
            if (key.equals(entry.source())) return entry.ashes();
        }
        return List.of();
    }

    /** 表里出现过的所有战灰 id（去重、稳定顺序）。 */
    public static List<String> allRewardedAshes() {
        Map<String,String> seen = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            for (String id : entry.ashes()) {
                String normalized = AshUnlockSet.normalize(id);
                if (normalized != null) seen.putIfAbsent(normalized, normalized);
            }
        }
        return List.copyOf(seen.values());
    }
}
