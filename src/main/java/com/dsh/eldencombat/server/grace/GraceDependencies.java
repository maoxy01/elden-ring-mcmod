package com.dsh.eldencombat.server.grace;

import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * 赐福系统的依赖准入。
 *
 * <p>**本类不得引用任何 Waystones/Balm/JourneyMap 类型**：它是"判断依赖在不在"的地方，
 * 一旦这里出现适配类的类型，缺失依赖时判断本身就会 NoClassDefFoundError，
 * 普通战斗模组也就跟着起不来了。
 *
 * <p>规则：
 * <ul>
 *   <li>{@link #REQUIRED} 缺任何一个 → 赐福接口显示不可用，战斗功能照常；</li>
 *   <li>{@link #MAP} 只是客户端地图显示，缺了只少地图标记，不影响休息/旅行。</li>
 * </ul>
 */
public final class GraceDependencies {

    /** 服务端赐福底座必需：Waystones 提供节点/旅行，Balm 提供它的事件桥。 */
    public static final List<String> REQUIRED = List.of("waystones", "balm");

    /** 可选的客户端地图集成。 */
    public static final String MAP = "journeymap";

    private GraceDependencies() {
    }

    /** 缺失的必需依赖（空 = 可以装载适配器）。 */
    public static List<String> missing() {
        List<String> result = new ArrayList<>();
        for (String id : REQUIRED) {
            if (!ModList.get().isLoaded(id)) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    public static boolean present() {
        return missing().isEmpty();
    }

    /** 客户端地图集成是否可用；不用它做服务端判定。 */
    public static boolean mapPresent() {
        return ModList.get().isLoaded(MAP);
    }
}
