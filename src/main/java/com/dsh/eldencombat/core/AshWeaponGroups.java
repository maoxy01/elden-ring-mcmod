package com.dsh.eldencombat.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 普通战灰的武器白名单分组（**纯逻辑**，不依赖 Minecraft，可单测）。
 *
 * <p>分组用得是现有的 {@link WeaponProfile} id——本工程的 33 件武器都是
 * {@code eldencombat:<profile id>}，所以"武器类型白名单"不需要另立一套分类表，
 * 展开出来的就是完整的 namespaced 物品 id。
 *
 * <p>**只列实际支持的**：这里没有出现的武器类型就是"没有可绑定的普通战灰"，
 * 不允许在菜单里显示成可选（见 {@code AshBindings.check} 的 UNSUPPORTED_ITEM）。
 */
public final class AshWeaponGroups {

    /** key 用于代码与诊断，label 用于界面文案，profileIds 是 {@link WeaponProfile} 的 id。 */
    public record Group(String key, String label, List<String> profileIds) {
        public Group {
            profileIds = List.copyOf(profileIds);
        }
    }

    private static final List<Group> GROUPS = List.of(
            new Group("slashing", "斩击系（短兵器/曲剑/刀/双头剑）",
                    List.of("dagger", "curved_sword", "curved_greatsword", "katana", "twinblade")),
            new Group("thrusting", "刺击系（刺剑/长枪/戟）",
                    List.of("thrusting_sword", "heavy_thrusting_sword", "spear", "great_spear", "halberd")),
            new Group("cleaving", "斩劈系（直剑/大剑/特大剑/斧）",
                    List.of("straight_sword", "great_sword", "colossal_sword", "axe", "greataxe")));

    private AshWeaponGroups() {
    }

    public static List<Group> all() {
        return GROUPS;
    }

    public static Group byKey(String key) {
        for (Group group : GROUPS) {
            if (group.key().equals(key)) return group;
        }
        return null;
    }

    /** 展开成完整 namespaced 物品 id（去重、稳定顺序）。未知分组返回空列表而不是抛异常。 */
    public static List<String> itemIds(String groupKey, String namespace) {
        Group group = byKey(groupKey);
        if (group == null) return List.of();
        String ns = namespace == null || namespace.isBlank() ? "eldencombat" : namespace.trim().toLowerCase(java.util.Locale.ROOT);
        Set<String> ids = new LinkedHashSet<>();
        for (String path : group.profileIds()) {
            String id = AshUnlockSet.normalize(ns + ":" + path);
            if (id != null) ids.add(id);
        }
        return List.copyOf(new ArrayList<>(ids));
    }

    /** 该武器路径是否属于这一分组；也是运行时兼容性判定用的唯一依据。 */
    public static boolean contains(String groupKey, String itemPath) {
        Group group = byKey(groupKey);
        return group != null && itemPath != null && group.profileIds().contains(itemPath);
    }
}
