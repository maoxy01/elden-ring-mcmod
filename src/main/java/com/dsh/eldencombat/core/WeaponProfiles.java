package com.dsh.eldencombat.core;

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
 * 41 件武器的差异化数值表（33 件类型武器 + 8 件追忆武器）。
 *
 * <p>数值直接取自《成长路径与手感收口方案》§5.1（reach）、§5.2（削韧）、§5.3（重量）、
 * §5.4（推荐质变）。**表格是单一事实来源**：物品属性、耐力消耗、削韧判定都从这里读，
 * 不散落在各个类里。
 *
 * <p>可被数据包覆盖：{@code data/eldencombat/weapon_profiles.json}（数组，字段同名）。
 * 覆盖只影响"运行时读到的值"（reach 属性、削韧、耐力系数），不改变已注册物品的静态属性，
 * 因此热改不会要求重启。
 */
public final class WeaponProfiles {

    /** 数据包覆盖文件（可选） */
    public static final String RESOURCE = "/data/eldencombat/weapon_profiles.json";

    private static volatile Map<String, WeaponProfile> table = defaultsById();

    private WeaponProfiles() {
    }

    public static WeaponProfile of(String itemId) {
        if (itemId == null) {
            return null;
        }
        String id=itemId.trim().toLowerCase(java.util.Locale.ROOT);
        if(id.contains(":")) {
            if(!id.startsWith("eldencombat:")) return null;
            id=id.substring("eldencombat:".length());
        }
        return table.get(id);
    }

    public static Map<String, WeaponProfile> all() {
        return table;
    }

    public static int size() {
        return table.size();
    }

    /** 服务端启动 / 资源重载时调用：内置表 + 数据包覆盖。 */
    public static void reload() {
        Map<String, WeaponProfile> merged = defaultsById();
        try (InputStream stream = WeaponProfiles.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                merged = parse(new InputStreamReader(stream, StandardCharsets.UTF_8), merged);
            }
        } catch (Exception exception) {
            CoreLog.LOGGER.error("读取武器数值覆盖失败，使用内置数值表", exception);
            merged = defaultsById();
        }
        table = merged;
    }

    /** 从 JSON 数组读取覆盖项；只覆盖表里已存在的 id，未知 id 忽略。 */
    public static Map<String, WeaponProfile> parse(Reader reader, Map<String, WeaponProfile> base) {
        Map<String, WeaponProfile> result = new LinkedHashMap<>(base);
        try {
            Gson gson = new GsonBuilder().create();
            List<WeaponProfile> parsed = gson.fromJson(reader,
                    new TypeToken<List<WeaponProfile>>() {
                    }.getType());
            if (parsed != null) {
                for (WeaponProfile profile : parsed) {
                    if (profile != null && result.containsKey(profile.id())) {
                        result.put(profile.id(), profile);
                    }
                }
            }
        } catch (RuntimeException exception) {
            CoreLog.LOGGER.error("解析武器数值覆盖失败，忽略覆盖", exception);
        }
        return result;
    }

    /** 测试用：直接替换整张表（随后用 {@link #reload()} 复位）。 */
    public static void overrideForTesting(Map<String, WeaponProfile> replacement) {
        table = replacement == null ? defaultsById() : new LinkedHashMap<>(replacement);
    }

    // ------------------------------------------------------------------
    // 内置数值表
    // ------------------------------------------------------------------

    private static WeaponProfile p(String id, double reach, float poiseLight, float poiseHeavy,
                                   double weight, String affinity) {
        return new WeaponProfile(id, reach, poiseLight, poiseHeavy, weight, affinity);
    }

    public static List<WeaponProfile> defaults() {
        List<WeaponProfile> list = new ArrayList<>();

        // ---- 短兵 / 直刃（reach 2.4 / 2.7 / 3.0 / 3.3 / 3.6） ----
        list.add(p("dagger", 2.7, 8, 14, 1.5, "KEEN"));
        list.add(p("straight_sword", 3.0, 12, 20, 3.5, "QUALITY"));
        list.add(p("great_sword", 3.3, 18, 30, 8.0, "HEAVY"));
        list.add(p("colossal_sword", 3.6, 36, 58, 14.0, "QUALITY"));
        list.add(p("thrusting_sword", 3.3, 12, 20, 3.5, "MAGIC"));
        list.add(p("heavy_thrusting_sword", 3.6, 18, 30, 7.0, "MAGIC"));
        list.add(p("curved_sword", 3.0, 12, 20, 3.5, "QUALITY"));
        list.add(p("curved_greatsword", 3.6, 18, 30, 8.5, "QUALITY"));
        list.add(p("katana", 3.0, 12, 20, 5.0, "KEEN"));
        // 方案里双头剑同时出现在 12/20 与 18/30 两行，这里取先出现的一行（12/20）
        list.add(p("twinblade", 3.3, 12, 20, 6.0, "KEEN"));

        // ---- 斧 / 钝器 ----
        list.add(p("axe", 3.0, 18, 30, 5.5, "HEAVY"));
        list.add(p("greataxe", 3.3, 26, 42, 10.0, "HEAVY"));
        list.add(p("hammer", 3.0, 18, 30, 6.0, "HEAVY"));
        list.add(p("great_hammer", 3.3, 26, 42, 10.0, "HEAVY"));
        list.add(p("colossal_weapon", 3.6, 36, 58, 16.0, "HEAVY"));

        // ---- 长柄 / 软兵 ----
        list.add(p("flail", 3.3, 18, 30, 6.0, "QUALITY"));
        list.add(p("spear", 3.9, 26, 42, 8.0, "QUALITY"));
        list.add(p("great_spear", 3.6, 26, 42, 10.0, "QUALITY"));
        list.add(p("halberd", 3.6, 26, 42, 9.0, "QUALITY"));
        list.add(p("scythe", 3.6, 26, 42, 9.0, "QUALITY"));
        // 软鞭的削韧是刻意的例外：它对群怪的生存理由就是削韧，不是伤害
        list.add(p("whip", 3.9, 26, 42, 5.0, "KEEN"));

        // ---- 拳 / 爪 ----
        list.add(p("fist", 2.4, 8, 14, 2.0, "KEEN"));
        list.add(p("claw", 2.4, 8, 14, 2.5, "KEEN"));

        // ---- 远程：不参与 reach / 削韧表（走原版装填与弹药） ----
        list.add(p("short_bow", WeaponProfile.VANILLA_REACH, 0, 0, 3.0, WeaponProfile.AFFINITY_NONE));
        list.add(p("long_bow", WeaponProfile.VANILLA_REACH, 0, 0, 5.0, WeaponProfile.AFFINITY_NONE));
        list.add(p("great_bow", WeaponProfile.VANILLA_REACH, 0, 0, 10.0, WeaponProfile.AFFINITY_NONE));
        list.add(p("crossbow", WeaponProfile.VANILLA_REACH, 0, 0, 9.0, WeaponProfile.AFFINITY_NONE));
        list.add(p("ballista", WeaponProfile.VANILLA_REACH, 0, 0, 15.0, WeaponProfile.AFFINITY_NONE));

        // ---- 法器 ----
        list.add(p("glintstone_staff", 3.0, 12, 20, 2.5, "MAGIC"));
        list.add(p("sacred_seal", 3.0, 12, 20, 1.5, "SACRED"));

        // ---- 盾：不参与 reach / 削韧表 ----
        list.add(p("small_shield", WeaponProfile.VANILLA_REACH, 0, 0, 4.0, WeaponProfile.AFFINITY_NONE));
        list.add(p("shield", WeaponProfile.VANILLA_REACH, 0, 0, 6.0, WeaponProfile.AFFINITY_NONE));
        list.add(p("great_shield", WeaponProfile.VANILLA_REACH, 0, 0, 12.0, WeaponProfile.AFFINITY_NONE));

        // ---- 8 件追忆武器：按最接近的类型武器定位（方案 §5 只给了 33 件的表） ----
        list.add(p("sacred_relic_sword", 3.0, 12, 20, 5.0, "QUALITY"));
        list.add(p("marikas_hammer", 3.3, 26, 42, 10.0, "HEAVY"));
        list.add(p("malikeths_black_blade", 3.6, 36, 58, 14.0, "QUALITY"));
        list.add(p("blasphemous_blade", 3.3, 18, 30, 8.0, "HEAVY"));
        list.add(p("hand_of_malenia", 3.0, 12, 20, 4.0, "KEEN"));
        list.add(p("starscourge_greatsword", 3.6, 36, 58, 15.0, "QUALITY"));
        list.add(p("mohgwyns_sacred_spear", 3.6, 26, 42, 10.0, "QUALITY"));
        list.add(p("morgotts_cursed_sword", 3.6, 18, 30, 8.0, "QUALITY"));

        return list;
    }

    public static Map<String, WeaponProfile> defaultsById() {
        Map<String, WeaponProfile> map = new LinkedHashMap<>();
        for (WeaponProfile profile : defaults()) {
            map.put(profile.id(), profile);
        }
        return map;
    }
}
