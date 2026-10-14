package com.dsh.eldencombat.client;

import com.dsh.eldencombat.core.TalismanSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端护符**展示数据**缓存（§3.5）。
 *
 * <p>只被 {@link com.dsh.eldencombat.network.TalismanDataPayload} 的处理器写入，
 * tooltip/界面只读这里——客户端**不**读服务端的静态 specs 表（远程客户端根本没有那份表），
 * 也**不**用这份数据做任何战斗结算（伤害/减伤/精力/FP 一律服务端算）。
 */
public final class TalismanClientData {

    private static volatile Map<String, TalismanSpec> specs = Map.of();

    private TalismanClientData() {
    }

    /** 全量替换（服务端每次下发的都是完整目录）。 */
    public static void replace(List<TalismanSpec> entries) {
        Map<String, TalismanSpec> next = new LinkedHashMap<>();
        if (entries != null) {
            for (TalismanSpec spec : entries) {
                if (spec != null && !spec.isNone()) {
                    next.put(spec.id(), spec);
                }
            }
        }
        specs = Map.copyOf(next);
    }

    /** 没同步到就是 {@link TalismanSpec#NONE}（调用方应显示"数据未同步"，不要编数值）。 */
    public static TalismanSpec spec(String id) {
        if (id == null || id.isEmpty()) {
            return TalismanSpec.NONE;
        }
        return specs.getOrDefault(id, TalismanSpec.NONE);
    }

    public static int size() {
        return specs.size();
    }

    public static Map<String, TalismanSpec> all() {
        return specs;
    }

    /** 断开连接/退回主菜单时清掉，避免把上一个服务器的数据带到下一个存档。 */
    public static void clear() {
        specs = Map.of();
    }
}
