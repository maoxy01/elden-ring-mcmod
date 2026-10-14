package com.dsh.eldencombat.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 战技姿态数据（纯逻辑，不依赖任何 MC / 客户端类型，便于单测）。
 *
 * <p>关键帧写在 {@code assets/eldencombat/skill_poses/<skillId>.json}，格式：
 * <pre>
 * {
 *   "stages": {
 *     "WINDUP":  {"rightArm": [[0,[0,0,0]], [8,[-150,0,10]]]},
 *     "ACTIVE":  {...},
 *     "ACTIVE#2": {...},          // 第 2 段的覆盖写法
 *     "RECOVERY": {...}
 *   }
 * }
 * </pre>
 * 时间为**该阶段内**经过的 tick，角度为**度**（渲染时转弧度）。
 */
public final class SkillPoseData {

    /** 允许的阶段名；下标就是网络里的 {@code stage} 序号。 */
    public static final String[] STAGE_NAMES = {"IDLE", "WINDUP", "ACTIVE", "FOLLOWUP_WINDOW", "RECOVERY"};

    /** 允许出现的关节：只有原版六件套。**不允许**给玩家模型增加关节。 */
    public static final java.util.Set<String> JOINTS =
            java.util.Set.of("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg");

    private SkillPoseData() {
    }

    /** 一个关节的关键帧轨道，按 tick 升序。 */
    public static final class Track {
        private final float[] ticks;
        private final float[] x;
        private final float[] y;
        private final float[] z;

        Track(float[] ticks, float[] x, float[] y, float[] z) {
            this.ticks = ticks;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** 单帧轨道（用于跨阶段冻结姿势）。 */
        public static Track single(float tick, float x, float y, float z) {
            return new Track(new float[]{tick}, new float[]{x}, new float[]{y}, new float[]{z});
        }

        public int keyCount() {
            return ticks.length;
        }

        public float lastTick() {
            return ticks[ticks.length - 1];
        }

        /** 采样（线性插值，越界取端点），结果写入 out 三元组。 */
        public float[] sample(double tick, float[] out) {
            if (tick <= ticks[0]) {
                return fill(out, 0);
            }
            if (tick >= ticks[ticks.length - 1]) {
                return fill(out, ticks.length - 1);
            }
            int i = 0;
            while (i < ticks.length - 1 && ticks[i + 1] < tick) {
                i++;
            }
            double span = ticks[i + 1] - ticks[i];
            double f = span <= 0 ? 0 : (tick - ticks[i]) / span;
            out[0] = (float) (x[i] + (x[i + 1] - x[i]) * f);
            out[1] = (float) (y[i] + (y[i + 1] - y[i]) * f);
            out[2] = (float) (z[i] + (z[i + 1] - z[i]) * f);
            return out;
        }

        private float[] fill(float[] out, int i) {
            out[0] = x[i];
            out[1] = y[i];
            out[2] = z[i];
            return out;
        }
    }

    /** 单个阶段的全部关节轨道。 */
    public record Stage(Map<String, Track> parts) {
        public static final Stage EMPTY = new Stage(Map.of());
    }

    /** 单个战技：阶段名（可带 {@code #段号}）-> 轨道集合。 */
    public record Skill(Map<String, Stage> stages) {

        /**
         * 取某阶段（含段号覆盖）的轨道。
         *
         * <p>回退链：{@code STAGE#段号} -> {@code STAGE}；
         * 收招与追击窗口没有显式数据时冻结在生效段的最后一帧，避免弹回站立姿势。
         */
        public Stage forStage(int stageOrdinal, int stageIndex) {
            String base = stageOrdinal >= 0 && stageOrdinal < STAGE_NAMES.length
                    ? STAGE_NAMES[stageOrdinal] : "IDLE";
            Stage exact = stages.get(base + "#" + stageIndex);
            if (exact != null) {
                return exact;
            }
            Stage plain = stages.get(base);
            if (plain != null) {
                return plain;
            }
            if ("FOLLOWUP_WINDOW".equals(base) || "RECOVERY".equals(base)) {
                Stage hold = stages.get("ACTIVE#" + stageIndex);
                if (hold == null) {
                    hold = stages.get("ACTIVE");
                }
                if (hold != null) {
                    return frozen(hold);
                }
            }
            return Stage.EMPTY;
        }

        /** 把整组轨道压成"最后一帧"。 */
        private static Stage frozen(Stage stage) {
            Map<String, Track> parts = new LinkedHashMap<>();
            for (Map.Entry<String, Track> entry : stage.parts().entrySet()) {
                Track track = entry.getValue();
                float[] last = track.sample(track.lastTick(), new float[3]);
                parts.put(entry.getKey(), Track.single(track.lastTick(), last[0], last[1], last[2]));
            }
            return new Stage(Map.copyOf(parts));
        }
    }

    /** 一个战技 id -> 姿态。 */
    public record Data(Map<String, Skill> skills) {
        public static final Data EMPTY = new Data(Map.of());
    }

    /** 解析一个姿态文件；非法（没有 stages / 关节名不对）返回 null。 */
    public static Skill parse(JsonObject root) {
        JsonObject stages = root == null ? null : root.getAsJsonObject("stages");
        if (stages == null) {
            return null;
        }
        Map<String, Stage> parsed = new HashMap<>();
        for (Map.Entry<String, JsonElement> stage : stages.entrySet()) {
            if (!stage.getValue().isJsonObject()) {
                continue;
            }
            Map<String, Track> parts = new HashMap<>();
            for (Map.Entry<String, JsonElement> part : stage.getValue().getAsJsonObject().entrySet()) {
                if (!JOINTS.contains(part.getKey())) {
                    continue;
                }
                Track track = parseTrack(part.getValue());
                if (track != null) {
                    parts.put(part.getKey(), track);
                }
            }
            if (!parts.isEmpty()) {
                parsed.put(stage.getKey(), new Stage(Map.copyOf(parts)));
            }
        }
        return parsed.isEmpty() ? null : new Skill(Map.copyOf(parsed));
    }

    private static Track parseTrack(JsonElement element) {
        if (!element.isJsonArray()) {
            return null;
        }
        JsonArray keys = element.getAsJsonArray();
        float[] ticks = new float[keys.size()];
        float[] x = new float[keys.size()];
        float[] y = new float[keys.size()];
        float[] z = new float[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            JsonArray key = keys.get(i).getAsJsonArray();
            ticks[i] = Math.max(0f, key.get(0).getAsFloat());
            JsonArray rot = key.get(1).getAsJsonArray();
            x[i] = (float) Math.toRadians(rot.get(0).getAsFloat());
            y[i] = (float) Math.toRadians(rot.get(1).getAsFloat());
            z[i] = (float) Math.toRadians(rot.get(2).getAsFloat());
        }
        return ticks.length == 0 ? null : new Track(ticks, x, y, z);
    }

    /** {@code stageOrdinal} -> 名字，越界返回 "IDLE"。 */
    public static String stageName(int ordinal) {
        return ordinal >= 0 && ordinal < STAGE_NAMES.length ? STAGE_NAMES[ordinal] : "IDLE";
    }
}
