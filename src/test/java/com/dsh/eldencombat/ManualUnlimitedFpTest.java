package com.dsh.eldencombat;

import com.dsh.eldencombat.server.skills.PlayerFpState;
import com.dsh.eldencombat.server.skills.RemembranceSkillSpec;
import com.dsh.eldencombat.server.skills.SkillCastState;
import com.dsh.eldencombat.server.skills.SkillTransactions;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ManualUnlimitedFpTest {
    @Test void unlimitedTestFpStaysFullForRepeatedConfirmedCasts() {
        var fp = new PlayerFpState(); fp.setFp(0); fp.setUnlimitedForTesting(true);
        for (int i = 0; i < 100; i++) {
            assertTrue(fp.trySpend(28, "waterfowl_dance", i)); assertEquals(100, fp.fp());
        }
        assertFalse(fp.isEmpty()); assertFalse(fp.trySpend(Float.NaN, "invalid", 200));
        assertFalse(fp.trySpend(Float.POSITIVE_INFINITY, "invalid", 200));
        fp.setUnlimitedForTesting(false); assertTrue(fp.trySpend(20, "wave_of_gold", 300));
        assertEquals(80, fp.fp());
    }

    @Test void unlimitedTestingDoesNotBypassCastProofsOrFollowupWindows() {
        var fp = new PlayerFpState(); fp.setUnlimitedForTesting(true);
        var spec = RemembranceSkillSpec.defaultsById().get("bloodboon_ritual"); var cast = new SkillCastState();
        assertEquals(SkillTransactions.Result.REJECTED, SkillTransactions.request(cast, fp, spec, 0, false));
        assertEquals(SkillTransactions.Result.STARTED, SkillTransactions.request(cast, fp, spec, 1, true));
        assertEquals(SkillTransactions.Result.REJECTED, SkillTransactions.request(cast, fp, spec, 2, true));
        cast.enterStage(SkillCastState.Stage.ACTIVE, 7);cast.openFollowupWindow(11, 10);
        assertEquals(SkillTransactions.Result.FOLLOWUP, SkillTransactions.request(cast, fp, spec, 12, true));
        assertEquals(100, fp.fp());
    }

    @Test void testOverrideNeverSurvivesCopyOrRestore() {
        var source = new PlayerFpState(); source.setUnlimitedForTesting(true);
        var copy = new PlayerFpState();copy.copyFrom(source);assertFalse(copy.unlimitedForTesting());
        copy.setUnlimitedForTesting(true);copy.restore(20, "wave_of_gold", 10, 1, Map.of());
        assertFalse(copy.unlimitedForTesting());assertEquals(20, copy.fp());
        assertTrue(copy.trySpend(20, "wave_of_gold", 11));assertFalse(copy.trySpend(1, "wave_of_gold", 12));
    }
}
