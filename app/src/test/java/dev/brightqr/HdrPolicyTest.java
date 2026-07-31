package dev.brightqr;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class HdrPolicyTest {
    @Test
    public void requestedHeadroomUsesPlatformMaximum() {
        assertEquals(10_000f, HdrPolicy.MAX_REQUESTED_HEADROOM, 0f);
    }

    @Test
    public void pqWhiteUsesMaximumCodeValue() {
        assertEquals(1f, HdrPolicy.PQ_MAX_CODE_VALUE, 0f);
    }

    @Test
    public void developerDialStartsAtSdrWhite() {
        assertEquals(1f, HdrPolicy.headroomForDialProgress(0), 0f);
    }

    @Test
    public void developerDialUsesTenthsForPracticalRatios() {
        assertEquals(4f, HdrPolicy.headroomForDialProgress(30), 0f);
        assertEquals(8f, HdrPolicy.headroomForDialProgress(70), 0f);
        assertEquals(10.9f, HdrPolicy.headroomForDialProgress(99), 0.0001f);
    }

    @Test
    public void developerDialFinalPositionRestoresMaximumPolicy() {
        assertEquals(
                HdrPolicy.MAX_REQUESTED_HEADROOM,
                HdrPolicy.headroomForDialProgress(HdrPolicy.DEV_DIAL_MAX_PROGRESS),
                0f
        );
    }
}
