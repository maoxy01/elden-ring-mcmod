package com.dsh.eldencombat.server.grace;

import com.dsh.eldencombat.EldenCombat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 赐福服务的**唯一配置处**（{@code config/eldencombat-grace.json}），{@code /eldencombat reload} 可热读。
 *
 * <p>资源恢复规则集中在这里，默认值 = 既有行为：
 * <ul>
 *   <li>休息只回 FP（{@code restFp = FULL}，其余 {@code KEEP}）；</li>
 *   <li>生命/精力/药瓶必须显式改成 {@code FULL} 才会恢复，且都接**现有权威状态**
 *       （原版生命值、{@link com.dsh.eldencombat.core.CombatState} 的精力与药瓶），不新造第二份资源；</li>
 *   <li>旅行与打开菜单**永不**恢复资源，只有 {@link GraceRest#rest} 读这份配置；</li>
 *   <li>重生点没有接到本项目权威状态，所以这里**故意没有**对应开关——不能被静默启用，
 *       也不可能出现"配了却没生效"的假开关。</li>
 * </ul>
 */
public final class GraceSettings {

    /** 休息时某项资源的处理规则。 */
    public enum Rest {
        /** 保持不动（默认，除 FP 外） */
        KEEP,
        /** 补满（FP / 生命 / 精力 / 药瓶都接到现有权威状态） */
        FULL
    }

    // ---- 战斗与旅行规则 ----
    public int combatTicks = 200;
    public int cooldownTicks = 100;
    public int syncTicks = 40;
    /** pending 旅行请求的存活上限（tick）；超时即清理，避免玩家被"旅行请求正在处理"永久卡住。 */
    public int pendingTimeoutTicks = 1200;
    /** true = 只能在赐福旁出发。 */
    public boolean requireNearby = false;
    /** 下发给客户端的节点快照上限（只影响已授权节点）。 */
    public int snapshotLimit = 512;

    // ---- 休息恢复规则 ----
    public Rest restFp = Rest.FULL;
    public Rest restHealth = Rest.KEEP;
    public Rest restStamina = Rest.KEEP;
    public Rest restFlasks = Rest.KEEP;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile GraceSettings current = new GraceSettings();

    /** 当前生效的设置；永不为 null（未装载赐福后端时就是默认值）。 */
    public static GraceSettings current() {
        return current;
    }

    /** 启动路径与 {@code /eldencombat reload} 共用。 */
    public static GraceSettings reload() {
        current = loadOrCreate(EldenCombat.graceConfigPath());
        GraceSettings value = current;
        EldenCombat.LOGGER.info("[grace] 配置已载入 {}（休息恢复：FP={} 生命={} 精力={} 药瓶={}）",
                EldenCombat.graceConfigPath(), value.restFp, value.restHealth, value.restStamina, value.restFlasks);
        return value;
    }

    /** 读不到/解析失败就用默认值；文件不存在时写出默认值，方便玩家改。 */
    public static GraceSettings loadOrCreate(Path file) {
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                GraceSettings parsed = GSON.fromJson(reader, GraceSettings.class);
                if (parsed != null) {
                    parsed.sanitize();
                    return parsed;
                }
            } catch (IOException | JsonSyntaxException error) {
                EldenCombat.LOGGER.error("[grace] 配置解析失败，改用默认值：{}", file, error);
            }
        }
        // Gson 走无参构造，缺字段时保留 Java 初始值（默认值），所以旧存档的配置文件也能平滑升级。
        GraceSettings fresh = new GraceSettings();
        write(file, fresh);
        return fresh;
    }

    private static void write(Path file, GraceSettings settings) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(settings, writer);
            }
        } catch (IOException error) {
            EldenCombat.LOGGER.error("[grace] 配置写入失败：{}", file, error);
        }
    }

    /** 夹住明显不合理的值：配置写错不应该让 tick 循环空转或让 pending 永不过期。 */
    void sanitize() {
        combatTicks = Math.max(1, combatTicks);
        cooldownTicks = Math.max(0, cooldownTicks);
        syncTicks = Math.max(1, syncTicks);
        pendingTimeoutTicks = Math.max(20, pendingTimeoutTicks);
        snapshotLimit = Math.min(4096, Math.max(1, snapshotLimit));
        restFp = restFp == null ? Rest.FULL : restFp;
        restHealth = restHealth == null ? Rest.KEEP : restHealth;
        restStamina = restStamina == null ? Rest.KEEP : restStamina;
        restFlasks = restFlasks == null ? Rest.KEEP : restFlasks;
    }
}
