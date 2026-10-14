package com.dsh.eldencombat;

import com.dsh.eldencombat.server.skills.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RemainingSkillsTest {
    private static final Map<String, RemembranceSkillSpec> SPECS = RemembranceSkillSpec.load();

    @Test void everySkillHasShortRecoveryInBothResourceAndFallback() {
        var defaults = RemembranceSkillSpec.defaultsById();
        for (var spec : SPECS.values()) {
            int expected = spec.id().equals("bloodboon_ritual") ? 2 : 5;
            assertEquals(expected, spec.recoveryTicks(), spec.id());
            assertEquals(expected, defaults.get(spec.id()).recoveryTicks(), spec.id());
            assertEquals(defaults.get(spec.id()).windupTicks(), spec.windupTicks());
            assertEquals(defaults.get(spec.id()).activeTicks(), spec.activeTicks());
        }
    }

    @Test void everySkillRejectsIllegalAndDuplicateStarts() {
        for (var spec : SPECS.values()) {
            var fp = new PlayerFpState(); var cast = new SkillCastState();
            assertEquals(SkillTransactions.Result.REJECTED, SkillTransactions.request(cast, fp, spec, 0, false));
            assertEquals(100, fp.fp());
            assertEquals(SkillTransactions.Result.STARTED, SkillTransactions.request(cast, fp, spec, 10, true));
            float spent = fp.fp(); int id = cast.castId();
            assertEquals(SkillTransactions.Result.REJECTED, SkillTransactions.request(cast, fp, spec, 10, true));
            assertEquals(spent, fp.fp()); assertEquals(id, cast.castId());
        }
    }

    @Test void ritualChargesEachConfirmedStageAndCapsAtThree() {
        var spec = SPECS.get("bloodboon_ritual"); var fp = new PlayerFpState(); var cast = new SkillCastState();
        assertEquals(SkillTransactions.Result.STARTED, SkillTransactions.request(cast, fp, spec, 0, true));
        cast.enterStage(SkillCastState.Stage.ACTIVE, 6);
        for (int stage = 1; stage < 3; stage++) {
            int id = cast.castId();
            cast.openFollowupWindow(stage * 20, 10);
            assertTrue(SkillTransactions.matchesRequest(cast, id, stage));
            assertEquals(SkillTransactions.Result.FOLLOWUP, SkillTransactions.request(cast, fp, spec, stage * 20, true));
            assertEquals(SkillCastState.Stage.WINDUP, cast.stage(), "仪式每段重新蓄势");
            assertEquals(SkillTransactions.Result.REJECTED, SkillTransactions.request(cast, fp, spec, stage * 20, true));
            cast.enterStage(SkillCastState.Stage.ACTIVE, stage * 20 + 6);
        }
        assertEquals(55, fp.fp());
        cast.openFollowupWindow(70, 10);
        assertEquals(SkillTransactions.Result.REJECTED, SkillTransactions.request(cast, fp, spec, 70, true));
        assertEquals(55, fp.fp());
    }

    @Test void insufficientExpiredAndWrongCastFollowupsLeaveFpAndWindowIntact() {
        var spec = SPECS.get("cursed_blood_slice"); var fp = new PlayerFpState(); var cast = new SkillCastState();
        SkillTransactions.request(cast, fp, spec, 0, true); cast.enterStage(SkillCastState.Stage.ACTIVE, 8);
        cast.openFollowupWindow(14, 10); fp.setFp(7);
        assertFalse(SkillTransactions.matchesRequest(cast, 0, 0), "旧起手包不能被当作追击");
        assertFalse(SkillTransactions.matchesRequest(cast, cast.castId() + 1, 1));
        assertEquals(SkillTransactions.Result.REJECTED, SkillTransactions.request(cast, fp, spec, 15, true));
        assertEquals(7, fp.fp()); assertTrue(cast.followupOpen(15));
        fp.setFp(20);
        assertEquals(SkillTransactions.Result.REJECTED, SkillTransactions.request(cast, fp, spec, 25, true));
        assertEquals(20, fp.fp());
        assertEquals(SkillTransactions.Result.REJECTED, SkillTransactions.request(cast, fp, SPECS.get("starcaller_cry"), 20, true));
        assertEquals(20, fp.fp());
    }

    @Test void consumedStageProofCannotReplayInNextWindow() {
        var cast = new SkillCastState(); cast.begin("bloodboon_ritual", 0);
        cast.enterStage(SkillCastState.Stage.ACTIVE, 6); int id = cast.castId();
        cast.openFollowupWindow(10, 10); assertTrue(cast.queueFollowup(11, 3));
        cast.enterStage(SkillCastState.Stage.ACTIVE, 17); cast.openFollowupWindow(21, 10);
        assertFalse(SkillTransactions.matchesRequest(cast, id, 1));
        assertTrue(SkillTransactions.matchesRequest(cast, id, 2));
        cast.finish(); assertFalse(SkillTransactions.matchesRequest(cast, id, 2));
    }

    @Test void multiHitChannelsAreIndependentButDeduplicated() {
        var cast = new SkillCastState(); cast.begin("destined_death", 0);
        cast.enterStage(SkillCastState.Stage.ACTIVE, 8);
        for (int channel = 0; channel < 3; channel++) {
            assertTrue(cast.markHit(7, channel)); assertFalse(cast.markHit(7, channel));
        }
        assertEquals(1, cast.hitCount());
        cast.enterStage(SkillCastState.Stage.ACTIVE, 40); assertTrue(cast.markHit(7, 0));
    }

    @Test void movementUsesPathLengthAndBoundedHeading() {
        var budget = new SkillMovementBudget(6);
        budget.record(2, 0); budget.record(-2, 0); budget.record(0, 1.5);
        assertEquals(.5, budget.speed(2), 1e-6); assertFalse(budget.exceeded());
        budget.record(0, 1); assertTrue(budget.exceeded()); assertEquals(0, budget.remaining());
        assertEquals(Math.toRadians(30), SkillMovementBudget.turn(0, Math.PI, Math.toRadians(30)), 1e-6);
        assertEquals(Math.toRadians(181), SkillMovementBudget.turn(Math.toRadians(179), Math.toRadians(-179), .1), 1e-6);
    }

    @Test void deathRefreshIsBoundedAndNeverStacksDamagePools() {
        var mark = new DeathMark(0, 60, 3);
        assertEquals(0, mark.pulse(19)); assertEquals(1, mark.pulse(20)); assertEquals(0, mark.pulse(20));
        mark.refresh(25, 5000, 3); assertEquals(85, mark.expires()); assertEquals(3, mark.remaining());
        mark.refresh(25, 60, 3); assertEquals(3, mark.remaining());
        assertEquals(1, mark.pulse(45)); assertEquals(1, mark.pulse(65)); assertEquals(1, mark.pulse(85));
        assertTrue(mark.expired(85)); assertEquals(0, mark.pulse(86));
        assertFalse(java.util.Arrays.stream(DeathMark.class.getDeclaredFields()).anyMatch(f->f.getName().contains("maxHealth")));
    }

    @Test void allWeaponBindingsAndPoseTimelinesMatch() throws Exception {
        for (var spec : SPECS.values()) {
            assertFalse(spec.itemId().isBlank(), spec.id() + " 必须绑定一把武器");
            var root = pose(spec.id());
            // 姿态关键帧必须与技能参数的整数 tick 时间线对齐，否则动作和判定对不上
            checkStage(root, spec.id(), "WINDUP", spec.windupForStage(0));
            checkStage(root, spec.id(), "ACTIVE", spec.activeForStage(1));
            checkStage(root, spec.id(), "RECOVERY", spec.recoveryTicks());
            if (spec.id().equals("starcaller_cry") || spec.id().equals("cursed_blood_slice")) {
                // 这两个战技有独立追击段（各自的蓄势与生效 tick 不同）
                checkStage(root, spec.id(), "WINDUP#2", spec.windupForStage(1));
                checkStage(root, spec.id(), "ACTIVE#2", spec.activeForStage(2));
            }
            if (spec.id().equals("bloodboon_ritual")) {
                // 三段仪式：第 3 段复用第 2 段数据（回退链）
                assertNotNull(root.getAsJsonObject("stages").getAsJsonObject("ACTIVE#2"), "bloodboon_ritual 需要 ACTIVE#2");
            }
        }
    }

    /** 读取某个战技的姿态数据文件。 */
    private static com.google.gson.JsonObject pose(String id) throws Exception {
        try (var in = RemainingSkillsTest.class.getResourceAsStream("/assets/eldencombat/skill_poses/" + id + ".json")) {
            assertNotNull(in, "缺少战技姿态数据: " + id);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** 校验某个阶段：存在、只用原版关节、关键帧从 0 开始且末帧等于该阶段 tick 数。 */
    private static void checkStage(com.google.gson.JsonObject root, String id, String stage, int ticks) {
        var stages = root.getAsJsonObject("stages");
        assertNotNull(stages, id + " 缺少 stages");
        var tracks = stages.getAsJsonObject(stage);
        assertNotNull(tracks, id + " 缺少阶段 " + stage);
        for (var part : tracks.entrySet()) {
            assertTrue(java.util.Set.of("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg")
                            .contains(part.getKey()),
                    id + "/" + stage + " 出现非原版关节 " + part.getKey());
            var keys = part.getValue().getAsJsonArray();
            assertTrue(keys.size() >= 2, id + "/" + stage + "/" + part.getKey() + " 至少 2 个关键帧");
            assertEquals(0f, keys.get(0).getAsJsonArray().get(0).getAsFloat(), 1e-6, id);
            float last = -1f;
            for (var key : keys) {
                var pair = key.getAsJsonArray();
                assertEquals(2, pair.size(), "关键帧必须是 [tick,[x,y,z]]");
                assertEquals(3, pair.get(1).getAsJsonArray().size());
                float t = pair.get(0).getAsFloat();
                assertTrue(t > last, id + "/" + stage + " 关键帧 tick 必须严格递增");
                last = t;
            }
            assertEquals(ticks, last, 1e-6, id + "/" + stage + "/" + part.getKey() + " 末帧必须等于阶段时长");
        }
    }
}
