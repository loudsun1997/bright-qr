package dev.brightqr;

/** Constants that define the app's maximum-output policy. */
public final class HdrPolicy {
    /** Maximum value accepted by SurfaceView.setDesiredHdrHeadroom on API 35+. */
    public static final float MAX_REQUESTED_HEADROOM = 10_000f;

    /** SMPTE ST 2084 (PQ) code value 1.0 represents 10,000-nit mastering white. */
    public static final float PQ_MAX_CODE_VALUE = 1f;

    /** Final dial position is the explicit, uncapped MAX policy. */
    public static final int DEV_DIAL_MAX_PROGRESS = 100;

    /**
     * Maps the developer dial to useful 0.1x increments from 1.0x through 10.9x.
     * The final position deliberately jumps to the platform maximum request.
     */
    public static float headroomForDialProgress(int progress) {
        int bounded = Math.max(0, Math.min(DEV_DIAL_MAX_PROGRESS, progress));
        return bounded == DEV_DIAL_MAX_PROGRESS
                ? MAX_REQUESTED_HEADROOM
                : 1f + bounded / 10f;
    }

    public static boolean isMaximumRequest(float headroom) {
        return headroom >= MAX_REQUESTED_HEADROOM;
    }

    private HdrPolicy() {
    }
}
