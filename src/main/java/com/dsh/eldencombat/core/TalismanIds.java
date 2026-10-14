package com.dsh.eldencombat.core;

/**
 * 护符 ID 规范化（**纯逻辑**，不依赖 Minecraft，可单测）。
 *
 * <p>§3.6 要求：护符身份必须是**完整 namespaced id**（{@code namespace:path}），
 * 不能让不同 namespace 的同名护符互相覆盖。旧存档里写的是**裸路径**
 * （旧 {@code Talismans.equip} 落盘的是 {@code getKey().getPath()}），
 * 迁移时按 {@code eldencombat:<path>} 认领——旧记录的命名空间本来就是本模组。
 *
 * <p>刻意**不**在同一个字符串上兼容两种语义（"既是 id 又是裸路径"）：规范化只有一条出口，
 * 且非法输入返回空串而不是猜一个 id。
 */
public final class TalismanIds {

    public static final String MOD_NAMESPACE = "eldencombat";

    private TalismanIds() {
    }

    /**
     * 规范化护符 ID：
     * <ul>
     *   <li>空 / null / 非法字符 → 空串（调用方必须当成"无效"，不要当成"没装备"）；</li>
     *   <li>裸路径 {@code warrior_jar_shard} → {@code eldencombat:warrior_jar_shard}；</li>
     *   <li>完整 ID {@code eldencombat:warrior_jar_shard} → 原样保留（namespace 不同的同名护符因此互相独立）。</li>
     * </ul>
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        int colon = value.indexOf(':');
        if (colon < 0) {
            return validPath(value) ? MOD_NAMESPACE + ":" + value : "";
        }
        String namespace = value.substring(0, colon);
        String path = value.substring(colon + 1);
        if (!validNamespace(namespace) || !validPath(path) || path.indexOf(':') >= 0) {
            return "";
        }
        return namespace + ":" + path;
    }

    /** 是否本模组命名空间的护符（用来把"数据缺失"的提示说清楚）。 */
    public static boolean isModId(String id) {
        return id != null && id.startsWith(MOD_NAMESPACE + ":");
    }

    /** 取路径部分（物品注册名就是它）：{@code a:b/c} → {@code b/c}。 */
    public static String pathOf(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private static boolean validNamespace(String part) {
        if (part.isEmpty()) {
            return false;
        }
        for (int i = 0; i < part.length(); i++) {
            if (!allowed(part.charAt(i), false)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validPath(String part) {
        if (part.isEmpty()) {
            return false;
        }
        for (int i = 0; i < part.length(); i++) {
            if (!allowed(part.charAt(i), true)) {
                return false;
            }
        }
        return true;
    }

    /** 原版 ResourceLocation 的字符集：命名空间 {@code [a-z0-9_.-]}，路径额外允许 {@code /}。 */
    private static boolean allowed(char c, boolean path) {
        if (c >= 'a' && c <= 'z') {
            return true;
        }
        if (c >= '0' && c <= '9') {
            return true;
        }
        if (c == '_' || c == '-' || c == '.') {
            return true;
        }
        return path && c == '/';
    }
}
