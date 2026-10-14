package com.dsh.eldencombat;

import com.dsh.eldencombat.server.skills.LocalSourceMotion;
import com.dsh.eldencombat.server.skills.RemembranceSkillSpec;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class LocalSourceMotionTest {
    @TempDir Path directory;

    private JsonObject animation() {
        JsonObject animation = new JsonObject();
        animation.addProperty("format", "attributes");
        JsonArray tracks = new JsonArray();
        for (String name : new String[] {"Root", "Torso", "Chest", "Head", "Thigh_R", "Leg_R", "Knee_R",
                "Thigh_L", "Leg_L", "Knee_L", "Shoulder_R", "Arm_R", "Hand_R", "Tool_R", "Elbow_R",
                "Shoulder_L", "Arm_L", "Hand_L", "Tool_L", "Elbow_L"}) {
            JsonObject track = new JsonObject();
            track.addProperty("name", name);
            JsonArray times = new JsonArray();
            times.add(0); times.add(4.5);
            track.add("time", times);
            JsonArray transforms = new JsonArray();
            for (int i = 0; i < 2; i++) {
                JsonObject transform = new JsonObject();
                transform.add("loc", array(0, 0, 0));
                transform.add("rot", array(1, 0, 0, 0));
                transform.add("sca", array(1, 1, 1));
                transforms.add(transform);
            }
            track.add("transform", transforms);
            tracks.add(track);
        }
        animation.add("animation", tracks);
        return animation;
    }

    private static JsonArray array(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) array.add(value);
        return array;
    }

    private Path write(JsonObject animation) throws Exception {
        byte[] bytes = animation.toString().getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("wave_of_gold.json"), bytes);
        JsonObject profile = new JsonObject();
        profile.addProperty("schema", 1);
        profile.addProperty("skillId", "wave_of_gold");
        profile.addProperty("animationFile", "wave_of_gold.json");
        profile.addProperty("animationSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        profile.addProperty("durationSeconds", 4.5);
        profile.addProperty("windupTicks", 40);
        profile.addProperty("activeTicks", 8);
        profile.addProperty("recoveryTicks", 42);
        Path file = directory.resolve("profile.json");
        Files.writeString(file, profile.toString());
        return file;
    }

    @Test void requires_both_manual_test_and_explicit_profile() {
        assertFalse(LocalSourceMotion.enabled(false, "profile.json"));
        assertFalse(LocalSourceMotion.enabled(true, " "));
        assertFalse(LocalSourceMotion.enabled(true, null));
        assertTrue(LocalSourceMotion.enabled(true, "profile.json"));
    }

    @Test void valid_profile_changes_only_golden_wave_timing() throws Exception {
        var snapshot = LocalSourceMotion.load(write(animation()));
        var specs = RemembranceSkillSpec.defaultsById();
        var old = specs.get("wave_of_gold");
        var changed = snapshot.apply(old);
        assertEquals(40, changed.windupTicks());
        assertEquals(8, changed.activeTicks());
        assertEquals(42, changed.recoveryTicks());
        assertEquals(90, changed.totalNominalTicks());
        assertEquals(110, changed.stageBudget());
        assertEquals(old.fpCost(), changed.fpCost());
        assertEquals(old.damage(), changed.damage());
        assertEquals(old.effectRange(), changed.effectRange());
        assertSame(specs.get("waterfowl_dance"), snapshot.apply(specs.get("waterfowl_dance")));
    }

    @Test void rejects_hash_mismatch() throws Exception {
        Path profile = write(animation());
        Files.writeString(directory.resolve("wave_of_gold.json"), "{}");
        assertThrows(IOException.class, () -> LocalSourceMotion.load(profile));
    }

    @Test void rejects_path_escape() throws Exception {
        Path profile = write(animation());
        JsonObject metadata = com.google.gson.JsonParser.parseString(Files.readString(profile)).getAsJsonObject();
        metadata.addProperty("animationFile", "../other.json");
        Files.writeString(profile, metadata.toString());
        assertThrows(IOException.class, () -> LocalSourceMotion.load(profile));
    }

    @Test void rejects_duration_mismatch() throws Exception {
        Path profile = write(animation());
        JsonObject metadata = com.google.gson.JsonParser.parseString(Files.readString(profile)).getAsJsonObject();
        metadata.addProperty("windupTicks", 10);
        Files.writeString(profile, metadata.toString());
        assertThrows(IOException.class, () -> LocalSourceMotion.load(profile));
    }

    @Test void rejects_unknown_or_duplicate_joint() throws Exception {
        JsonObject animation = animation();
        animation.getAsJsonArray("animation").get(1).getAsJsonObject().addProperty("name", "Root");
        Path profile = write(animation);
        assertThrows(IOException.class, () -> LocalSourceMotion.load(profile));
    }

    @Test void rejects_limb_stretch_and_horizontal_drift() throws Exception {
        JsonObject animation = animation();
        animation.getAsJsonArray("animation").get(1).getAsJsonObject().getAsJsonArray("transform").get(0)
                .getAsJsonObject().add("loc", array(0, .1, 0));
        Path profile = write(animation);
        assertThrows(IOException.class, () -> LocalSourceMotion.load(profile));
        animation = animation();
        animation.getAsJsonArray("animation").get(0).getAsJsonObject().getAsJsonArray("transform").get(0)
                .getAsJsonObject().add("loc", array(.1, 0, 0));
        Path driftProfile = write(animation);
        assertThrows(IOException.class, () -> LocalSourceMotion.load(driftProfile));
    }

    @Test void accepts_actual_local_candidate_when_available() throws Exception {
        Path candidate = Path.of("build/elden-source/retargeted/profile.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(candidate));
        var snapshot = LocalSourceMotion.load(candidate);
        assertEquals(2.7, snapshot.durationSeconds());
        assertEquals(6, snapshot.recoveryTicks());
        assertEquals(269, snapshot.animation().getAsJsonArray("animation").get(0).getAsJsonObject().getAsJsonArray("time").size());
    }
}
