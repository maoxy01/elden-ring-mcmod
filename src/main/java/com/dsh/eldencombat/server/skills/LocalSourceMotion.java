package com.dsh.eldencombat.server.skills;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/** Explicit local-test override: no source assets are bundled or loaded during ordinary runs. */
public final class LocalSourceMotion {
    private static final Set<String> JOINTS = Set.of("Root", "Torso", "Chest", "Head",
            "Thigh_R", "Leg_R", "Knee_R", "Thigh_L", "Leg_L", "Knee_L",
            "Shoulder_R", "Arm_R", "Hand_R", "Tool_R", "Elbow_R",
            "Shoulder_L", "Arm_L", "Hand_L", "Tool_L", "Elbow_L");
    private static Snapshot cached;
    private static Path cachedPath;

    public record Snapshot(JsonObject animation, int windupTicks, int activeTicks, int recoveryTicks,
                           double durationSeconds, String sha256) {
        public RemembranceSkillSpec apply(RemembranceSkillSpec spec) {
            if (!spec.id().equals("wave_of_gold")) return spec;
            return new RemembranceSkillSpec(spec.id(), spec.itemId(), spec.fpCost(), spec.fpCostFollowup(),
                    windupTicks, activeTicks, recoveryTicks, spec.followupWindowTicks(), spec.maxStages(),
                    spec.allowsSkillJump(), spec.maxHorizontal(), spec.maxVertical(), spec.effectRange(),
                    spec.effectLifespanTicks(), spec.effectRadius(), spec.healCap(), spec.cooldownTicks(),
                    windupTicks + activeTicks + recoveryTicks + 20, spec.damage(), spec.poiseDamage(),
                    spec.followupWindupTicks(), spec.followupActiveTicks());
        }
    }

    private LocalSourceMotion() {}

    public static boolean enabled(boolean manualSkills, String profile) {
        return manualSkills && profile != null && !profile.isBlank();
    }

    public static synchronized Snapshot current() {
        String configured = System.getProperty("eldencombat.sourceMotionProfile");
        if (!enabled(Boolean.getBoolean("eldencombat.manualSkills"), configured)) return null;
        Path path = Path.of(configured).toAbsolutePath().normalize();
        if (cached != null) {
            if (!path.equals(cachedPath)) throw new IllegalStateException("Restart the local test to change its motion profile.");
            return cached;
        }
        try {
            cached = load(path);
            cachedPath = path;
            return cached;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Invalid local source motion profile: " + path, exception);
        }
    }

    public static Map<String, RemembranceSkillSpec> apply(Map<String, RemembranceSkillSpec> specs) {
        Snapshot snapshot = current();
        if (snapshot != null) specs.computeIfPresent("wave_of_gold", (id, spec) -> snapshot.apply(spec));
        return specs;
    }

    public static Snapshot load(Path profilePath) throws IOException {
        try {
            Path profile = profilePath.toRealPath();
            JsonObject metadata = JsonParser.parseString(readBounded(profile, 32_768)).getAsJsonObject();
            require(metadata.get("schema").getAsInt() == 1, "Unsupported local profile schema.");
            require(metadata.get("skillId").getAsString().equals("wave_of_gold"), "Only golden wave is supported.");
            Path directory = profile.getParent();
            Path animationFile = directory.resolve(metadata.get("animationFile").getAsString()).normalize();
            require(animationFile.startsWith(directory), "Animation path escapes the local profile directory.");
            animationFile = animationFile.toRealPath();
            require(animationFile.startsWith(directory), "Animation symlink escapes the local profile directory.");
            String animationText = readBounded(animationFile, 4_194_304);
            String hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(animationText.getBytes(StandardCharsets.UTF_8)));
            require(hash.equalsIgnoreCase(metadata.get("animationSha256").getAsString()), "Local animation SHA256 mismatch.");
            int windup = metadata.get("windupTicks").getAsInt();
            int active = metadata.get("activeTicks").getAsInt();
            int recovery = metadata.get("recoveryTicks").getAsInt();
            double duration = metadata.get("durationSeconds").getAsDouble();
            require(windup > 0 && active > 0 && recovery >= 0 && windup <= 200 && active <= 200 && recovery <= 200,
                    "Invalid local skill timing.");
            require(Double.isFinite(duration) && duration > 0 && duration <= 10
                    && Math.abs(duration - (windup + active + recovery) / 20.0) < 1e-6, "Animation/state duration mismatch.");
            JsonObject animation = JsonParser.parseString(animationText).getAsJsonObject();
            validateAnimation(animation, duration);
            return new Snapshot(animation, windup, active, recovery, duration, hash);
        } catch (NoSuchAlgorithmException | RuntimeException exception) {
            throw new IOException("Invalid local animation profile.", exception);
        }
    }

    private static String readBounded(Path path, int maximumBytes) throws IOException {
        require(Files.isRegularFile(path), "Expected a regular local profile file.");
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(maximumBytes + 1);
            require(bytes.length <= maximumBytes, "Local profile file exceeds size limit.");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void validateAnimation(JsonObject animation, double duration) throws IOException {
        require(animation.get("format").getAsString().equals("attributes"), "Expected EF attributes animation.");
        var tracks = animation.getAsJsonArray("animation");
        require(tracks.size() == JOINTS.size(), "Unexpected EF joint count.");
        require(tracks.get(0).getAsJsonObject().get("name").getAsString().equals("Root"), "Root must be the first track.");
        Set<String> names = new HashSet<>();
        for (var trackElement : tracks) {
            var track = trackElement.getAsJsonObject();
            String name = track.get("name").getAsString();
            require(JOINTS.contains(name) && names.add(name), "Unknown or duplicate EF joint.");
            var times = track.getAsJsonArray("time");
            var transforms = track.getAsJsonArray("transform");
            require(times.size() >= 2 && times.size() <= 1200 && times.size() == transforms.size(), "Invalid keyframe count.");
            double previous = -1;
            for (int i = 0; i < times.size(); i++) {
                double time = times.get(i).getAsDouble();
                require(Double.isFinite(time) && time > previous && time <= duration + 1e-6, "Invalid keyframe time.");
                if (i == 0) require(Math.abs(time) < 1e-6, "Animation must start at zero.");
                previous = time;
                var transform = transforms.get(i).getAsJsonObject();
                var location = transform.getAsJsonArray("loc");
                var rotation = transform.getAsJsonArray("rot");
                var scale = transform.getAsJsonArray("sca");
                require(location.size() == 3 && rotation.size() == 4 && scale.size() == 3, "Invalid transform shape.");
                double norm = 0;
                for (var value : rotation) {
                    double component = value.getAsDouble();
                    require(Double.isFinite(component), "Non-finite rotation.");
                    norm += component * component;
                }
                require(Math.abs(norm - 1) < 1e-4, "Non-unit rotation.");
                for (int component = 0; component < 3; component++) {
                    double value = location.get(component).getAsDouble();
                    require(Double.isFinite(value) && Math.abs(value) <= 2, "Invalid local translation.");
                    require(Math.abs(scale.get(component).getAsDouble() - 1) < 1e-5, "Limb scaling is not allowed.");
                    if (!name.equals("Root")) require(Math.abs(value) < 1e-5, "Non-root translations must preserve bind lengths.");
                    if (name.equals("Root") && component != 1) require(Math.abs(value) < 1e-5, "Horizontal root drift is not allowed.");
                }
            }
            require(Math.abs(previous - duration) < 1e-6, "Track duration mismatch.");
        }
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
