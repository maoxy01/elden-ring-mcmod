package com.dsh.eldencombat.skeleton;

import com.dsh.eldencombat.core.ActionId;
import com.dsh.eldencombat.core.WeaponClass;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3d;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

public final class PlayerAssets {
    public record Blade(Vector3d from, Vector3d to, double radius) {}
    private static volatile PlayerAssets loaded;
    private final Map<Boolean, ModelData> players = new HashMap<>();
    private final Map<Boolean, SkinLayout> skins = new HashMap<>();
    private final Map<String, ModelData> equipment = new HashMap<>();
    private final Map<String, Map<String, AnimationClip>> profiles = new HashMap<>();
    private final Map<String, Blade> blades = new HashMap<>();

    public static PlayerAssets get() {
        PlayerAssets result = loaded;
        if (result == null) synchronized (PlayerAssets.class) {
            if (loaded == null) loaded = new PlayerAssets();
            result = loaded;
        }
        return result;
    }

    private static JsonObject read(String folder, String file) {
        String path = "/assets/eldencombat/" + folder + "/" + file;
        try (var stream = PlayerAssets.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing asset " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new IllegalStateException(path, e); }
    }

    private PlayerAssets() {
        JsonObject offsets = read("animations/player", "player.visual-offsets.json").getAsJsonObject("clips");
        for (boolean slim : new boolean[]{false, true}) {
            String variant = "player_" + (slim ? "slim" : "classic");
            players.put(slim, ModelData.parse(read("skeletons/player", variant + ".model.json")));
            skins.put(slim, new SkinLayout(read("skeletons/player", variant + ".skin.json")));
            for (WeaponClass weapon : new WeaponClass[]{WeaponClass.LIGHT_SWORD, WeaponClass.GREAT_SWORD}) {
                String file = weapon == WeaponClass.GREAT_SWORD ? variant + ".great_sword.animations.json" : "straight_sword.animations.json";
                Map<String, AnimationClip> clips = new HashMap<>();
                for (var element : read("animations/player", file).getAsJsonArray("clips")) {
                    AnimationClip clip = AnimationClip.parse(element.getAsJsonObject());
                    if (offsets.has(clip.id())) clip = clip.withPositions(offsets.getAsJsonObject(clip.id()).getAsJsonObject("positionTracks"));
                    clips.put(clip.id(), clip);
                }
                profiles.put(key(weapon, slim), Map.copyOf(clips));
            }
        }
        for (String id : new String[]{"straight_sword", "great_sword", "shield", "healing_flask"}) equipment.put(id, ModelData.parse(read("skeletons/player", id + ".model.json")));
        read("skeletons/player", "equipment.hit-shapes.json").getAsJsonObject("weapons").asMap().forEach((id, value) -> {
            JsonObject shape = value.getAsJsonObject();
            var a = shape.getAsJsonArray("from"); var b = shape.getAsJsonArray("to");
            blades.put(id, new Blade(new Vector3d(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()).div(16),
                new Vector3d(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble()).div(16), shape.get("radiusBlocks").getAsDouble()));
        });
    }

    private static String key(WeaponClass weapon, boolean slim) { return weapon.name() + slim; }
    public ModelData model(boolean slim) { return players.get(slim); }
    public SkinLayout skin(boolean slim) { return skins.get(slim); }
    public ModelData equipment(String id) { return equipment.get(id); }
    public AnimationClip clip(WeaponClass weapon, boolean slim, String id) {
        return profiles.get(key(weapon == WeaponClass.NONE ? WeaponClass.LIGHT_SWORD : weapon, slim)).get(id);
    }
    public Blade blade(WeaponClass weapon) { return blades.get(weapon == WeaponClass.GREAT_SWORD ? "great_sword" : "straight_sword"); }

    public static String clipId(ActionId action, boolean guarding, int sequence, double speed, boolean running) {
        return switch (action) {
            case NONE -> guarding ? "guard_idle" : speed > 0.015 ? (running ? "run" : "walk") : "idle";
            case LIGHT_ATTACK -> (sequence & 1) == 0 ? "light_2" : "light_1";
            case HEAVY_CHARGE -> "heavy_windup";
            case HEAVY_ATTACK -> "heavy_release";
            case CHARGED_HEAVY -> "charged_release";
            case ROLL -> "roll";
            case PARRY -> "parry";
            case DRINK -> "drink";
            case GUARD -> "guard_idle";
            case GUARD_BROKEN -> "guard_break";
            case STAGGER -> "hit_stagger";
            case DEATH -> "death";
        };
    }

    public SkeletonPose pose(WeaponClass weapon, boolean slim, boolean leftHanded, String id, double tick) {
        AnimationClip clip = clip(weapon, slim, id);
        Map<String, Vector3f> rotations = clip.poseAt(tick);
        if (leftHanded) {
            Map<String, Vector3f> mirrored = new HashMap<>();
            rotations.forEach((name, v) -> {
                String target = name.endsWith("_r") ? name.substring(0, name.length()-2)+"_l" : name.endsWith("_l") ? name.substring(0, name.length()-2)+"_r" : name;
                mirrored.put(target, name.endsWith("_socket") ? new Vector3f(v) : new Vector3f(v.x, -v.y, -v.z));
            });
            rotations = mirrored;
        }
        return SkeletonPose.of(model(slim), rotations, clip.positionsAt(tick));
    }

    public Matrix4f socket(SkeletonPose pose, String name, boolean leftHanded) {
        if (!leftHanded) return pose.boneWorld(name);
        ModelData.BoneSpec spec = pose.model().bone(name);
        String hand = spec.parent().equals("hand_r") ? "hand_l" : "hand_r";
        Matrix4f local = pose.boneWorld(spec.parent()).invert().mul(pose.boneWorld(name));
        Matrix4f mirrored = new Matrix4f().scaling(-1,1,1).mul(local).scale(-1,1,1);
        return pose.boneWorld(hand).mul(mirrored);
    }

    public Blade sampledBlade(WeaponClass weapon, boolean slim, boolean leftHanded, String id, double tick) {
        Matrix4f socket = socket(pose(weapon, slim, leftHanded, id, tick), "sword_socket", leftHanded);
        Blade source = blade(weapon);
        return new Blade(new Vector3d(socket.transformPosition(new Vector3f((float)source.from.x, (float)source.from.y, (float)source.from.z))),
            new Vector3d(socket.transformPosition(new Vector3f((float)source.to.x, (float)source.to.y, (float)source.to.z))), source.radius);
    }
}
