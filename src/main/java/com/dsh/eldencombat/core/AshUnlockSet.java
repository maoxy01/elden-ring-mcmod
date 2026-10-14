package com.dsh.eldencombat.core;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * 玩家战灰解锁集合（**不依赖 Minecraft**，所以纯逻辑可单测）。
 *
 * <p>语义：
 * <ul>
 *   <li>只收**完整 namespaced ID**（{@code namespace:path}）。裸路径一律拒绝——
 *       "哪个命名空间的同名战灰"说不清，是兼容性事故的源头；</li>
 *   <li>授予**幂等**：重复授予同一 ID 返回 {@code false} 且内容不变
 *       （奖励来源重试、重复发放、多次拾取都不会翻倍）；</li>
 *   <li>落盘用**稳定字典序**，读入时对脏数据宽容：格式不对的条目丢弃并计数，
 *       绝不让一条坏数据把整份存档读崩。</li>
 * </ul>
 */
public final class AshUnlockSet {

    /** 与 {@code ResourceLocation} 同规则：命名空间 {@code [a-z0-9_.-]+}，路径 {@code [a-z0-9_./-]+}。 */
    private static final String NAMESPACE = "[a-z0-9_.-]+";
    private static final String PATH = "[a-z0-9_./-]+";

    private final Set<String> ids = new TreeSet<>();

    /**
     * 规范化：合法返回 {@code namespace:path}（小写），非法返回 {@code null}。
     *
     * <p>大小写统一成小写是刻意的：{@code ResourceLocation} 本身只认小写，
     * 玩家在命令里敲 `EldenCombat:Foo` 时应当得到同一条记录，而不是"看起来授予了其实没生效"。
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) return null;
        if (value.indexOf(':', colon + 1) >= 0) return null;
        String namespace = value.substring(0, colon);
        String path = value.substring(colon + 1);
        if (!namespace.matches(NAMESPACE) || !path.matches(PATH)) return null;
        return namespace + ":" + path;
    }

    /** 幂等授予；返回是否**新**加入。ID 非法直接抛（调用方必须先校验并给出可读反馈）。 */
    public boolean grant(String raw) {
        String id = normalize(raw);
        if (id == null) throw new IllegalArgumentException("战灰 ID 必须是完整的 namespace:path：" + raw);
        return ids.add(id);
    }

    /** 撤销；返回是否真的移除了。 */
    public boolean revoke(String raw) {
        String id = normalize(raw);
        return id != null && ids.remove(id);
    }

    /** 是否已解锁。ID 非法一律视为"没有"，不抛异常（查询路径要能被随便问）。 */
    public boolean owns(String raw) {
        String id = normalize(raw);
        return id != null && ids.contains(id);
    }

    /** 稳定顺序（字典序）的快照，供落盘/展示/断言。 */
    public List<String> ids() {
        return List.copyOf(ids);
    }

    public int size() {
        return ids.size();
    }

    /** 读入结果：合法条目 + 被丢弃的脏条目数（用于日志，不静默吞掉数据问题）。 */
    public record LoadResult(AshUnlockSet set, int dropped) {}

    /** 从落盘数据读入；重复条目自动去重，非法条目计入 {@code dropped}。 */
    public static LoadResult load(List<String> raw) {
        AshUnlockSet set = new AshUnlockSet();
        int dropped = 0;
        if (raw != null) {
            for (String entry : raw) {
                String id = normalize(entry);
                if (id == null) {
                    dropped++;
                    continue;
                }
                set.ids.add(id);
            }
        }
        return new LoadResult(set, dropped);
    }
}
