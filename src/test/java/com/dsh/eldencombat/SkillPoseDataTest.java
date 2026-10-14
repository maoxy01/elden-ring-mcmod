package com.dsh.eldencombat;

import com.dsh.eldencombat.core.SkillPoseData;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 战技姿态数据：解析、插值、阶段回退。
 *
 * <p>这些是"动作为什么看起来对/不对"的核心逻辑，且完全脱离 MC 类型，所以必须单测。
 */
class SkillPoseDataTest {

    private static final String SAMPLE = """
            {"stages": {
              "WINDUP":  {"rightArm": [[0,[0,0,0]], [8,[-90,0,10]]]},
              "ACTIVE":  {"rightArm": [[0,[-90,0,10]], [4,[-30,0,-10]]], "body": [[0,[0,0,0]], [4,[20,0,0]]]},
              "ACTIVE#2": {"rightArm": [[0,[-30,0,-10]], [8,[-120,0,0]]]}
            }}""";

    @Test void parsesDegreesIntoRadiansAndOnlyVanillaJoints() {
        SkillPoseData.Skill skill = SkillPoseData.parse(JsonParser.parseString(SAMPLE).getAsJsonObject());
        assertNotNull(skill);
        var windup = skill.forStage(1, 1);
        assertEquals(1, windup.parts().size());
        var track = windup.parts().get("rightArm");
        assertEquals(2, track.keyCount());
        float[] out = track.sample(8, new float[3]);
        assertEquals(-Math.PI / 2, out[0], 1.0e-6, "度必须转成弧度");
        assertEquals(0f, out[1], 1.0e-6);
        assertEquals(Math.toRadians(10), out[2], 1.0e-6);
    }

    @Test void samplesLinearlyAndClampsBothEnds() {
        SkillPoseData.Skill skill = SkillPoseData.parse(JsonParser.parseString(SAMPLE).getAsJsonObject());
        var track = skill.forStage(1, 1).parts().get("rightArm");
        float[] out = new float[3];
        track.sample(-5, out);
        assertEquals(0f, out[0], 1.0e-6, "早于首个关键帧取首帧");
        track.sample(4, out);
        assertEquals(Math.toRadians(-45), out[0], 1.0e-6, "中点线性插值");
        track.sample(100, out);
        assertEquals(Math.toRadians(-90), out[0], 1.0e-6, "晚于末个关键帧取末帧");
    }

    @Test void stageIndexOverrideWinsAndUnknownStageFallsBackToActive() {
        SkillPoseData.Skill skill = SkillPoseData.parse(JsonParser.parseString(SAMPLE).getAsJsonObject());
        // ACTIVE#2 覆盖 ACTIVE
        assertEquals(Math.toRadians(-120),
                skill.forStage(2, 2).parts().get("rightArm").sample(8, new float[3])[0], 1.0e-6);
        // 段 3 没有覆盖数据 -> 回退到 ACTIVE
        assertEquals(Math.toRadians(-30),
                skill.forStage(2, 3).parts().get("rightArm").sample(4, new float[3])[0], 1.0e-6);
        // 追击窗口没有数据 -> 冻结 ACTIVE 最后一帧（不能弹回 0）
        var window = skill.forStage(3, 1);
        assertEquals(1, window.parts().get("rightArm").keyCount(), "冻结姿势是单帧轨道");
        assertEquals(Math.toRadians(-30),
                window.parts().get("rightArm").sample(0, new float[3])[0], 1.0e-6);
        // 收招没有数据 -> 同样冻结在生效段末帧
        assertEquals(Math.toRadians(20),
                skill.forStage(4, 1).parts().get("body").sample(3, new float[3])[0], 1.0e-6);
    }

    @Test void rejectsFilesWithoutStagesAndIgnoresUnknownJoints() {
        assertNull(SkillPoseData.parse(JsonParser.parseString("{}").getAsJsonObject()));
        var skill = SkillPoseData.parse(JsonParser.parseString(
                "{\"stages\":{\"WINDUP\":{\"tail\":[[0,[0,0,0]],[4,[10,0,0]]],\"head\":[[0,[0,0,0]],[4,[5,0,0]]]}}}")
                .getAsJsonObject());
        assertNotNull(skill);
        assertEquals(java.util.Set.of("head"), skill.forStage(1, 1).parts().keySet(),
                "非原版关节（如尾巴）必须被丢弃，不允许给玩家模型加关节");
    }

    @Test void stageNamesMatchProtocolOrdinals() {
        assertEquals("IDLE", SkillPoseData.stageName(0));
        assertEquals("WINDUP", SkillPoseData.stageName(1));
        assertEquals("ACTIVE", SkillPoseData.stageName(2));
        assertEquals("FOLLOWUP_WINDOW", SkillPoseData.stageName(3));
        assertEquals("RECOVERY", SkillPoseData.stageName(4));
        assertEquals(5, SkillPoseData.STAGE_NAMES.length);
    }
}
