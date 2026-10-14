package com.dsh.eldencombat.skeleton;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一段骨骼动作（20 TPS，tick 为整数游戏 tick，插值为线性）。
 * 每次播放新动作都从静态姿态重新开始，不累积上一帧旋转：本类只提供"相对静态姿态的增量"。
 */
public final class AnimationClip {

    public record ClipEvent(int tick, String type, String item) {
    }

    private final String id;
    private final int lengthTicks;
    private final boolean loop;
    private final List<ClipEvent> events;
    private final Map<String, List<Key>> tracks;
    private Map<String, List<Key>> positions = Map.of();

    private record Key(int tick, Vector3f value) {
    }

    private AnimationClip(String id, int lengthTicks, boolean loop, List<ClipEvent> events, Map<String, List<Key>> tracks) {
        this.id = id;
        this.lengthTicks = lengthTicks;
        this.loop = loop;
        this.events = List.copyOf(events);
        this.tracks = tracks;
    }

    public static AnimationClip parse(JsonObject json) {
        String id = json.get("id").getAsString();
        int length = json.get("lengthTicks").getAsInt();
        boolean loop = json.has("loop") && json.get("loop").getAsBoolean();

        List<ClipEvent> events = new ArrayList<>();
        if (json.has("events")) {
            for (JsonElement element : json.getAsJsonArray("events")) {
                JsonObject event = element.getAsJsonObject();
                events.add(new ClipEvent(
                        event.get("tick").getAsInt(),
                        event.get("type").getAsString(),
                        event.has("item") ? event.get("item").getAsString() : null));
            }
        }

        Map<String, List<Key>> tracks = new LinkedHashMap<>();
        if (json.has("tracks")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("tracks").entrySet()) {
                List<Key> keys = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    JsonObject key = element.getAsJsonObject();
                    JsonArray value = key.getAsJsonArray("value");
                    keys.add(new Key(key.get("tick").getAsInt(),
                            new Vector3f(value.get(0).getAsFloat(), value.get(1).getAsFloat(), value.get(2).getAsFloat())));
                }
                keys.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
                tracks.put(entry.getKey(), keys);
            }
        }
        events.sort(java.util.Comparator.comparingInt(ClipEvent::tick));
        return new AnimationClip(id, length, loop, events, tracks);
    }

    public String id() {
        return id;
    }

    public int lengthTicks() {
        return lengthTicks;
    }

    public boolean loop() {
        return loop;
    }

    public List<ClipEvent> events() {
        return events;
    }

    /** 播放位置（tick）：循环动作取模，非循环动作截断在总长处。 */
    public double frameFor(double tick) {
        if (tick < 0) {
            return 0;
        }
        if (loop) {
            return lengthTicks <= 0 ? 0 : tick % lengthTicks;
        }
        return Math.min(tick, lengthTicks);
    }

    /** 缺失轨道返回零增量。 */
    public Vector3f deltaAt(String bone, double tick) {
        return sample(tracks.get(bone), tick);
    }

    private Vector3f sample(List<Key> keys, double tick) {
        if (keys == null || keys.isEmpty()) {
            return new Vector3f();
        }
        double frame = frameFor(tick);
        if (frame <= keys.get(0).tick()) {
            return new Vector3f(keys.get(0).value());
        }
        for (int i = 1; i < keys.size(); i++) {
            Key previous = keys.get(i - 1);
            Key next = keys.get(i);
            if (frame <= next.tick()) {
                float span = next.tick() - previous.tick();
                float t = span <= 0 ? 0f : (float) ((frame - previous.tick()) / span);
                return new Vector3f(
                        previous.value().x + (next.value().x - previous.value().x) * t,
                        previous.value().y + (next.value().y - previous.value().y) * t,
                        previous.value().z + (next.value().z - previous.value().z) * t);
            }
        }
        return new Vector3f(keys.get(keys.size() - 1).value());
    }

    /** 整段动作在某一 tick 的全部骨骼增量 */
    public Map<String, Vector3f> poseAt(double tick) {
        Map<String, Vector3f> pose = new LinkedHashMap<>();
        for (String bone : tracks.keySet()) {
            pose.put(bone, deltaAt(bone, tick));
        }
        return pose;
    }

    public AnimationClip withPositions(JsonObject positionTracks) {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("id", id);
        wrapper.addProperty("lengthTicks", lengthTicks);
        wrapper.add("tracks", positionTracks);
        AnimationClip result = new AnimationClip(id, lengthTicks, loop, events, tracks);
        result.positions = parse(wrapper).tracks;
        return result;
    }

    public Map<String, Vector3f> positionsAt(double tick) {
        Map<String, Vector3f> result = new LinkedHashMap<>();
        positions.forEach((bone, keys) -> result.put(bone, sample(keys, tick)));
        return result;
    }

    /** Events crossed once, including loop boundaries and skipped render frames. */
    public List<ClipEvent> eventsBetween(double previous, double current) {
        List<ClipEvent> result = new ArrayList<>();
        if (current <= previous) return result;
        if (!loop || lengthTicks <= 0) {
            for (ClipEvent event : events) if (event.tick > previous && event.tick <= current) result.add(event);
        } else {
            long firstCycle = Math.max(0, (long) Math.floor(previous / lengthTicks));
            long lastCycle = (long) Math.floor(current / lengthTicks);
            // Never replay an unbounded backlog when joining an old looping clip.
            firstCycle = Math.max(firstCycle, lastCycle - 1);
            for (long cycle = firstCycle; cycle <= lastCycle; cycle++) for (ClipEvent event : events) {
                double at = cycle * lengthTicks + event.tick;
                if (at > previous && at <= current) result.add(event);
            }
        }
        return result;
    }

    public int eventTick(String type) {
        for (ClipEvent event : events) {
            if (event.type().equals(type)) {
                return event.tick();
            }
        }
        return -1;
    }

    /** 某一 tick 时该骨骼动作应该显示哪件黄金武器（equip 之后、unequip 之前），无则 null。 */
    public String equippedAt(double tick) {
        double frame = frameFor(tick);
        String current = null;
        for (ClipEvent event : events) {
            if (event.tick() > frame) {
                break;
            }
            if ("equip".equals(event.type())) {
                current = event.item();
            } else if ("unequip".equals(event.type())) {
                current = null;
            }
        }
        return current;
    }

    /** 命中窗口 [hitStart, hitEnd)，事件缺失时返回 -1。 */
    public int hitStart() {
        return eventTick("hit_start");
    }

    public int hitEnd() {
        return eventTick("hit_end");
    }
}
