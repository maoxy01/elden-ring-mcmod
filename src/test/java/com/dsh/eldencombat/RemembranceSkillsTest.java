package com.dsh.eldencombat;

import com.dsh.eldencombat.server.skills.PlayerFpState;
import com.dsh.eldencombat.server.skills.RemembranceSkillSpec;
import com.dsh.eldencombat.server.skills.SkillCastState;
import com.dsh.eldencombat.server.skills.WaveStep;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 追忆战技基础设施的纯逻辑验收（不依赖 Minecraft / Epic Fight 运行时）。
 */
class RemembranceSkillsTest {

    @Test
    void cancelTailPreservesHardRecoveryAndEndsAtIdleBoundary() {
        for (var spec : RemembranceSkillSpec.defaults()) {
            SkillCastState cast = new SkillCastState();
            cast.begin(spec.id(), 0);
            assertFalse(cast.recoveryCancelable(100, spec.recoveryTicks()));
            cast.enterRecovery(100);
            int tail = Math.min(2, spec.recoveryTicks() - 1);
            assertFalse(cast.recoveryCancelable(100 + spec.recoveryTicks() - tail - 1, spec.recoveryTicks()));
            assertTrue(cast.recoveryCancelable(100 + spec.recoveryTicks() - tail, spec.recoveryTicks()));
            assertTrue(cast.recoveryCancelable(100 + spec.recoveryTicks() - 1, spec.recoveryTicks()));
            assertFalse(cast.recoveryCancelable(100 + spec.recoveryTicks(), spec.recoveryTicks()));
        }
    }

    // ---------------- FP ----------------

    @Test
    void refusedCastDoesNotSpend() {
        PlayerFpState fp = new PlayerFpState();
        fp.setMaxFp(100f);
        fp.trySpend(60f, "wave_of_gold", 10);
        assertEquals(40f, fp.fp(), 1.0e-4f);
        // 剩余 40，消耗 60 的重击必须被拒绝且不改变状态
        assertFalse(fp.trySpend(60f, "gold_breaker", 20));
        assertEquals(40f, fp.fp(), 1.0e-4f);
        assertEquals("wave_of_gold", fp.lastSkillId());
    }

    @Test
    void respawnRefillsButDimensionSwitchDoesNot() {
        PlayerFpState fp = new PlayerFpState();
        fp.setMaxFp(100f);
        fp.trySpend(70f, "waterfowl_dance", 5);
        assertEquals(30f, fp.fp(), 1.0e-4f);
        // 换维度：不补满（调用方不应调用 refillOnRespawn）
        assertEquals(30f, fp.fp(), 1.0e-4f);
        fp.refillOnRespawn();
        assertEquals(100f, fp.fp(), 1.0e-4f);
        assertEquals(1, fp.refillCount());
    }

    @Test
    void cooldownIsPerSkill() {
        PlayerFpState fp = new PlayerFpState();
        fp.setCooldown("wave_of_gold", 100);
        assertTrue(fp.onCooldown("wave_of_gold", 90));
        assertFalse(fp.onCooldown("wave_of_gold", 100));
        assertFalse(fp.onCooldown("gold_breaker", 90));
        assertEquals(10, fp.cooldownRemaining("wave_of_gold", 90));
    }

    // ---------------- 施放状态机 ----------------

    @Test
    void castAssignsIdAndRejectsSecondBegin() {
        SkillCastState cast = new SkillCastState();
        assertTrue(cast.begin("wave_of_gold", 100));
        int firstId = cast.castId();
        assertFalse(cast.begin("gold_breaker", 101));
        assertEquals(firstId, cast.castId());
        assertEquals("wave_of_gold", cast.skillId());
        assertTrue(cast.isCasting());
    }

    @Test
    void sameStageHitsOnceNextStageHitsAgain() {
        SkillCastState cast = new SkillCastState();
        cast.begin("destined_death", 0);
        cast.enterStage(SkillCastState.Stage.ACTIVE, 8);
        assertTrue(cast.markHit(7));
        assertFalse(cast.markHit(7));
        cast.enterStage(SkillCastState.Stage.ACTIVE, 16);
        assertTrue(cast.markHit(7), "进入新段后允许重新命中");
    }

    @Test
    void followupWindowConsumesOnceAndExpires() {
        SkillCastState cast = new SkillCastState();
        cast.begin("starcaller_cry", 0);
        cast.openFollowupWindow(14, 12);
        assertTrue(cast.followupOpen(20));
        assertTrue(cast.consumeFollowup(20));
        assertFalse(cast.consumeFollowup(21), "同一追击窗只能消费一次");
        cast.openFollowupWindow(40, 10);
        assertTrue(cast.followupExpired(51));
        assertFalse(cast.consumeFollowup(51), "过期追击必须被拒绝");
    }

    @Test
    void finishClearsEverythingWithoutRefund() {
        SkillCastState cast = new SkillCastState();
        cast.begin("cursed_blood_slice", 0);
        cast.enterStage(SkillCastState.Stage.ACTIVE, 8);
        cast.markHit(3);
        cast.markStageResolved();
        cast.finish();
        assertFalse(cast.isCasting());
        assertEquals("", cast.skillId());
        assertEquals(0, cast.stageIndex());
        assertFalse(cast.hasHit(3));
        assertFalse(cast.stageResolved());
    }

    @Test
    void stageTimeoutIsBounded() {
        SkillCastState cast = new SkillCastState();
        cast.begin("bloodboon_ritual", 0);
        cast.enterStage(SkillCastState.Stage.ACTIVE, 10);
        assertFalse(cast.stageTimedOut(30, 40));
        assertTrue(cast.stageTimedOut(51, 40));
    }

    @Test
    void multiStageResolvedCountsOnce() {
        SkillCastState cast = new SkillCastState();
        cast.begin("waterfowl_dance", 0);
        cast.enterStage(SkillCastState.Stage.ACTIVE, 10);
        cast.markStageResolved();
        cast.markStageResolved();
        assertEquals(1, cast.stagesResolved());
        cast.enterStage(SkillCastState.Stage.ACTIVE, 22);
        cast.markStageResolved();
        assertEquals(2, cast.stagesResolved());
    }

    // ---------------- 地面波推进 ----------------

    @Test
    void waveAdvancesAlongDirectionAndStopsAtRange() {
        WaveStep.Step step = WaveStep.advance(0, 0, 1, 0, 1.0, 0, 30, 16.0, false);
        assertEquals(1.0, step.x(), 1.0e-6);
        assertEquals(0.0, step.z(), 1.0e-6);
        assertFalse(step.finished());
        assertEquals(1, step.travelledTicks());

        // 走到最远距离即结束
        WaveStep.Step far = WaveStep.advance(15, 0, 1, 0, 1.0, 15, 30, 16.0, false);
        assertTrue(far.finished());
        assertEquals("到达最远距离", far.reason());

        // 寿命先到也结束
        WaveStep.Step dead = WaveStep.advance(1, 0, 1, 0, 1.0, 29, 30, 100.0, false);
        assertTrue(dead.finished());
        assertEquals("寿命结束", dead.reason());
    }

    @Test
    void waveRadiusGrowsButIsCapped() {
        double start = WaveStep.radiusAt(3.0, 4.2, 0, 1.0, 16.0);
        double middle = WaveStep.radiusAt(3.0, 4.2, 8, 1.0, 16.0);
        double end = WaveStep.radiusAt(3.0, 4.2, 16, 1.0, 16.0);
        assertEquals(3.0, start, 1.0e-6);
        assertTrue(middle > start);
        assertEquals(4.2, end, 1.0e-6);
    }

    // ---------------- 参数数据 ----------------

    @Test
    void defaultSpecsMatchPlanTable() {
        Map<String, RemembranceSkillSpec> specs = RemembranceSkillSpec.defaultsById();
        assertEquals(8, specs.size());
        RemembranceSkillSpec wave = specs.get("wave_of_gold");
        assertEquals("sacred_relic_sword", wave.itemId());
        assertEquals(20f, wave.fpCost(), 1.0e-4f);
        assertEquals(19, wave.totalNominalTicks());
        assertFalse(wave.allowsSkillJump());
        assertEquals(16.0, wave.effectRange(), 1.0e-6);
        assertEquals(30, wave.effectLifespanTicks());

        RemembranceSkillSpec cry = specs.get("starcaller_cry");
        assertTrue(cry.hasFollowup());
        assertEquals(12, cry.followupWindowTicks());
        assertEquals(15f, cry.costForStage(0), 1.0e-4f);
        assertEquals(15f, cry.costForStage(1), 1.0e-4f);

        RemembranceSkillSpec ritual = specs.get("bloodboon_ritual");
        assertEquals(3, ritual.maxStages());
        assertFalse(ritual.allowsSkillJump());
        assertEquals(15f, ritual.costForStage(2), 1.0e-4f);

        RemembranceSkillSpec slice = specs.get("cursed_blood_slice");
        assertEquals(12f, slice.costForStage(0), 1.0e-4f);
        assertEquals(8f, slice.costForStage(1), 1.0e-4f);
        assertTrue(slice.allowsSkillJump());
    }

    @Test
    void resourceFileLoads() {
        Map<String, RemembranceSkillSpec> loaded = RemembranceSkillSpec.load();
        assertEquals(8, loaded.size(), "数据文件应提供全部 8 个战技参数");
        assertNotNull(loaded.get("destined_death"));
        assertTrue(loaded.get("destined_death").allowsSkillJump());
        assertFalse(loaded.get("takers_flames").allowsSkillJump());
    }

    @Test
    void setFpClampsAndDoesNotTouchCooldowns() {
        PlayerFpState fp = new PlayerFpState();
        fp.setMaxFp(100f);
        fp.setCooldown("wave_of_gold", 500);
        fp.setFp(999f);
        assertEquals(100f, fp.fp(), 1.0e-4f, "超过上限要夹住");
        fp.setFp(-50f);
        assertEquals(0f, fp.fp(), 1.0e-4f, "负数要夹到 0");
        assertTrue(fp.onCooldown("wave_of_gold", 400), "测试用改 FP 不应清冷却");
        // 降低上限时当前 FP 要跟着夹住
        fp.setFp(100f);
        fp.setMaxFp(40f);
        assertEquals(40f, fp.fp(), 1.0e-4f, "降低上限时当前 FP 要跟着夹住");
    }


    // ---------------- 战技姿态数据（纯原版关节） ----------------

    /** 允许出现的关节：只有原版六件套。给玩家模型加自定义关节是被明确禁止的。 */
    private static final java.util.Set<String> VANILLA_JOINTS = java.util.Set.of(
            "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg");

    @Test
    void deliveredSkillPosesUseVanillaJointsOnly() throws Exception {
        Map<String, RemembranceSkillSpec> specs = RemembranceSkillSpec.load();
        int delivered = 0;
        for (String id : specs.keySet()) {
            String path = "/assets/eldencombat/skill_poses/" + id + ".json";
            try (java.io.InputStream in = RemembranceSkillsTest.class.getResourceAsStream(path)) {
                if (in == null) {
                    continue; // 缺席由 allWeaponBindingsAndPoseTimelinesMatch 统一报错
                }
                delivered++;
                com.google.gson.JsonObject root = com.google.gson.JsonParser
                        .parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                com.google.gson.JsonObject stages = root.getAsJsonObject("stages");
                assertNotNull(stages, id + " 必须有 stages");
                assertTrue(stages.size() > 0, id + " 至少要有一个阶段");
                for (var stage : stages.entrySet()) {
                    com.google.gson.JsonObject tracks = stage.getValue().getAsJsonObject();
                    assertTrue(tracks.size() > 0, id + "/" + stage.getKey() + " 至少一个关节");
                    for (var part : tracks.entrySet()) {
                        assertTrue(VANILLA_JOINTS.contains(part.getKey()),
                                id + "/" + stage.getKey() + " 出现非原版关节 " + part.getKey());
                        com.google.gson.JsonArray keys = part.getValue().getAsJsonArray();
                        assertTrue(keys.size() >= 2, id + "/" + part.getKey() + " 至少 2 个关键帧");
                        for (var key : keys) {
                            com.google.gson.JsonArray pair = key.getAsJsonArray();
                            assertEquals(2, pair.size(), id + "/" + part.getKey() + " 关键帧必须形如 [tick,[x,y,z]]");
                            assertEquals(3, pair.get(1).getAsJsonArray().size(),
                                    id + "/" + part.getKey() + " 旋转必须是三个角度");
                        }
                    }
                }
            }
        }
        assertEquals(8, delivered, "八个战技均须提供独立姿态数据");
    }

    @Test
    void deliveredWavePoseMatchesSkillTimeline() throws Exception {
        Map<String, RemembranceSkillSpec> specs = RemembranceSkillSpec.load();
        RemembranceSkillSpec wave = specs.get("wave_of_gold");
        String path = "/assets/eldencombat/skill_poses/wave_of_gold.json";
        try (java.io.InputStream in = RemembranceSkillsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "黄金波动姿态数据必须存在: " + path);
            com.google.gson.JsonObject stages = com.google.gson.JsonParser
                    .parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("stages");
            // 姿态关键帧的末帧必须落在技能参数的整数 tick 时间线上，否则动作与判定对不上。
            assertEquals(wave.windupTicks(), lastTick(stages.getAsJsonObject("WINDUP")), 1.0e-6, "蓄势");
            assertEquals(wave.activeForStage(1), lastTick(stages.getAsJsonObject("ACTIVE")), 1.0e-6, "生效");
            assertEquals(wave.recoveryTicks(), lastTick(stages.getAsJsonObject("RECOVERY")), 1.0e-6, "收招");
        }
    }

    /** 一个阶段里所有关节轨道最后关键帧 tick 的最大值。 */
    private static float lastTick(com.google.gson.JsonObject tracks) {
        float last = -1f;
        for (var part : tracks.entrySet()) {
            com.google.gson.JsonArray keys = part.getValue().getAsJsonArray();
            last = Math.max(last, keys.get(keys.size() - 1).getAsJsonArray().get(0).getAsFloat());
        }
        return last;
    }
}
