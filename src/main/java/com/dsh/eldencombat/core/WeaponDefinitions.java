package com.dsh.eldencombat.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 41 件武器的完整定义表（基础伤害 + 补正 + 差异化数值）。
 *
 * <p>与 {@link WeaponProfiles} 的关系：那一位是"手感维度"（reach/削韧/重量），
 * 这一位是"成长维度"（基础伤害/补正强度/档位）。默认定义由 profile 派生，
 * 可用 {@code data/eldencombat/weapon_definitions.json} 覆盖基础伤害与补正强度。
 *
 * <p>纯数据 + 纯公式，可单测。
 */
public final class WeaponDefinitions {

    /** 数据包覆盖文件（可选） */
    public static final String RESOURCE = "/data/eldencombat/weapon_definitions.json";

    /** 覆盖项：只允许改这三个字段，档位与 id 由内置表决定，避免数据包把武器改成另一种武器 */
    public record Override(String id, double baseDamage, double correctionRating) {
    }

    private static volatile Map<String, WeaponDefinition> table = build();

    private WeaponDefinitions() {
    }

    public static WeaponDefinition of(String itemId) {
        if (itemId == null) {
            return null;
        }
        return table.get(itemId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static Map<String, WeaponDefinition> all() {
        return table;
    }

    public static int size() {
        return table.size();
    }

    /** 服务端启动 / 资源重载时调用：跟当前 profile 表 + 内置默认 + 数据包覆盖。 */
    public static void reload() {
        table = build();
        try (InputStream stream = WeaponDefinitions.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                table = parse(new InputStreamReader(stream, StandardCharsets.UTF_8), table);
            }
        } catch (Exception exception) {
            CoreLog.LOGGER.error("读取武器定义覆盖失败，使用内置定义", exception);
            table = build();
        }
    }

    /** 解析覆盖数组；未知 id 忽略。 */
    public static Map<String, WeaponDefinition> parse(Reader reader, Map<String, WeaponDefinition> base) {
        Map<String, WeaponDefinition> result = new LinkedHashMap<>(base);
        try {
            Gson gson = new GsonBuilder().create();
            java.util.List<Override> parsed = gson.fromJson(reader,
                    new TypeToken<java.util.List<Override>>() {
                    }.getType());
            if (parsed != null) {
                for (Override override : parsed) {
                    if (override == null || override.id() == null) {
                        continue;
                    }
                    String id = override.id().trim().toLowerCase(java.util.Locale.ROOT);
                    WeaponDefinition existing = result.get(id);
                    if (existing == null) {
                        continue;
                    }
                    result.put(id, new WeaponDefinition(existing.id(), existing.profile(), existing.remembrance(),
                            override.baseDamage() > 0 ? override.baseDamage() : existing.baseDamage(),
                            override.correctionRating() >= 0 ? override.correctionRating() : existing.correctionRating(),
                            existing.baseGrades(), existing.artReplaceable()));
                }
            }
        } catch (RuntimeException exception) {
            CoreLog.LOGGER.error("解析武器定义覆盖失败，忽略覆盖", exception);
        }
        return result;
    }

    /** 测试用：直接替换整张表；传 null 复位。 */
    public static void overrideForTesting(Map<String, WeaponDefinition> replacement) {
        table = replacement == null ? build() : new LinkedHashMap<>(replacement);
    }

    private static Map<String, WeaponDefinition> build() {
        Map<String, WeaponDefinition> map = new LinkedHashMap<>();
        for (WeaponProfile profile : WeaponProfiles.all().values()) {
            boolean remembrance = WeaponDefinition.REMEMBRANCE_IDS.contains(profile.id());
            map.put(profile.id(), WeaponDefinition.of(profile, remembrance));
        }
        return map;
    }
}
