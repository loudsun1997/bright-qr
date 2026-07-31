package dev.brightqr;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.Display;

import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

/** Square SurfaceView whose QR white uses all HDR output Android currently grants. */
final class HdrQrSurfaceView extends GLSurfaceView {
    interface Listener {
        void onRenderingModeChanged(boolean hdrActive, String detail);
    }

    private final QrRenderer renderer;

    public HdrQrSurfaceView(Context context) {
        this(context, (hdrActive, detail) -> { });
    }

    HdrQrSurfaceView(Context context, Listener listener) {
        super(context);

        DisplayManager displayManager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager == null
                ? null
                : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        boolean displaySupportsHdr = display != null && display.isHdr();

        // Match the native buffer to the EGL config. Android HDR handhelds are
        // required to expose the PQ EGL extension and a suitable HDR buffer path.
        getHolder().setFormat(
                displaySupportsHdr ? PixelFormat.RGBA_1010102 : PixelFormat.RGBA_8888);

        HdrEglState eglState = new HdrEglState(displaySupportsHdr);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(eglState);
        setEGLWindowSurfaceFactory(eglState);
        renderer = new QrRenderer(
                eglState,
                (hdrActive, detail) -> post(
                        () -> listener.onRenderingModeChanged(hdrActive, detail))
        );
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setPreserveEGLContextOnPause(true);

        applyRequestedHeadroom(HdrPolicy.MAX_REQUESTED_HEADROOM);
    }

    void setPayload(String payload) throws WriterException {
        BitMatrix matrix = QrCodeGenerator.encode(payload);
        renderer.setQrMatrix(matrix);
        requestRender();
    }

    void applyRequestedHeadroom(float headroom) {
        if (Build.VERSION.SDK_INT >= 35) {
            setDesiredHdrHeadroom(headroom);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            size = width;
        } else {
            size = Math.min(width, height);
        }
        setMeasuredDimension(size, size);
    }
}
