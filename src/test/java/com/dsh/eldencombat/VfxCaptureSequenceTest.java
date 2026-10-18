package com.dsh.eldencombat;

import com.dsh.eldencombat.effects.VfxCaptureSequence;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class VfxCaptureSequenceTest {
    @Test void fourPairsKeepTheirIdentityAndDoNotFinishAfterTheFirstReference() {
        var capture = new VfxCaptureSequence();
        var names = new HashSet<String>();
        for (int i = 1; i <= 4; i++) {
            String live = capture.sampleLabel("primary2", 4) + "-a.png";
            assertTrue(names.add(live));
            assertFalse(capture.liveCaptured(4, true));
            assertTrue(capture.awaitingClean());
            String clean = capture.sampleLabel("primary2", 4) + "-clean.png";
            assertTrue(names.add(clean));
            assertEquals(live, clean.replace("-clean.png", "-a.png"), "落盘回调必须能找到同一组live文件");
            assertEquals(i == 4, capture.cleanCaptured(4));
            assertFalse(capture.awaitingClean());
            assertEquals(i, capture.completed());
        }
        assertEquals(8, names.size());
    }

    @Test void unpairedOrbitNeedsBothSamples() {
        var capture = new VfxCaptureSequence();
        assertFalse(capture.liveCaptured(2, false));
        assertTrue(capture.liveCaptured(2, false));
    }

    @Test void resetDoesNotLeakAnIncompletePairIntoTheNextSegment() {
        var capture = new VfxCaptureSequence();
        capture.liveCaptured(4, true);
        capture.reset();
        assertFalse(capture.awaitingClean());
        assertEquals("primary2-1", capture.sampleLabel("primary2", 4));
        assertEquals("primary1", capture.sampleLabel("primary1", 1));
        assertTrue(capture.liveCaptured(1, false));
    }

    @Test void aReferenceCannotBeCountedTwiceOrSkippedByAnotherLiveCapture() {
        var capture = new VfxCaptureSequence();
        assertThrows(IllegalStateException.class, () -> capture.cleanCaptured(4));
        capture.liveCaptured(4, true);
        assertThrows(IllegalStateException.class, () -> capture.liveCaptured(4, true));
        assertFalse(capture.cleanCaptured(4));
        assertThrows(IllegalStateException.class, () -> capture.cleanCaptured(4));
    }
}
