package dev.brightqr;

import android.opengl.GLSurfaceView;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/** Chooses a 10-bit EGL config and tags the SurfaceView buffer as BT.2020 PQ. */
final class HdrEglState implements GLSurfaceView.EGLConfigChooser,
        GLSurfaceView.EGLWindowSurfaceFactory {
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_GL_COLORSPACE_KHR = 0x309D;
    private static final int EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3340;
    private static final String EXT_BT2020_PQ = "EGL_EXT_gl_colorspace_bt2020_pq";

    private final boolean displaySupportsHdr;
    private final AtomicBoolean tenBitConfigSelected = new AtomicBoolean(false);
    private final AtomicBoolean pqSurfaceActive = new AtomicBoolean(false);

    HdrEglState(boolean displaySupportsHdr) {
        this.displaySupportsHdr = displaySupportsHdr;
    }

    @Override
    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
        boolean canUsePq = displaySupportsHdr && hasExtension(egl, display, EXT_BT2020_PQ);
        EGLConfig config = canUsePq ? choose(egl, display, 10, 10, 10, 2) : null;
        tenBitConfigSelected.set(config != null);
        if (config == null) {
            config = choose(egl, display, 8, 8, 8, 8);
        }
        if (config == null) {
            throw new IllegalArgumentException("No compatible window EGL config");
        }
        return config;
    }

    @Override
    public EGLSurface createWindowSurface(
            EGL10 egl,
            EGLDisplay display,
            EGLConfig config,
            Object nativeWindow
    ) {
        if (tenBitConfigSelected.get() && hasExtension(egl, display, EXT_BT2020_PQ)) {
            int[] attributes = {
                    EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_PQ_EXT,
                    EGL10.EGL_NONE
            };
            EGLSurface surface = create(egl, display, config, nativeWindow, attributes);
            if (surface != null && surface != EGL10.EGL_NO_SURFACE) {
                pqSurfaceActive.set(true);
                return surface;
            }
        }

        pqSurfaceActive.set(false);
        return create(egl, display, config, nativeWindow, null);
    }

    @Override
    public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
        pqSurfaceActive.set(false);
        egl.eglDestroySurface(display, surface);
    }

    boolean isPqSurfaceActive() {
        return pqSurfaceActive.get();
    }

    private static EGLSurface create(
            EGL10 egl,
            EGLDisplay display,
            EGLConfig config,
            Object nativeWindow,
            int[] attributes
    ) {
        try {
            return egl.eglCreateWindowSurface(display, config, nativeWindow, attributes);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return null;
        }
    }

    private static EGLConfig choose(
            EGL10 egl,
            EGLDisplay display,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        int[] attributes = {
                EGL10.EGL_RED_SIZE, red,
                EGL10.EGL_GREEN_SIZE, green,
                EGL10.EGL_BLUE_SIZE, blue,
                EGL10.EGL_ALPHA_SIZE, alpha,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_NONE
        };
        int[] count = new int[1];
        if (!egl.eglChooseConfig(display, attributes, null, 0, count) || count[0] == 0) {
            return null;
        }
        EGLConfig[] configs = new EGLConfig[count[0]];
        if (!egl.eglChooseConfig(display, attributes, configs, configs.length, count)) {
            return null;
        }
        for (EGLConfig config : configs) {
            if (componentSize(egl, display, config, EGL10.EGL_RED_SIZE) == red
                    && componentSize(egl, display, config, EGL10.EGL_GREEN_SIZE) == green
                    && componentSize(egl, display, config, EGL10.EGL_BLUE_SIZE) == blue
                    && componentSize(egl, display, config, EGL10.EGL_ALPHA_SIZE) == alpha) {
                return config;
            }
        }
        return null;
    }

    private static int componentSize(
            EGL10 egl,
            EGLDisplay display,
            EGLConfig config,
            int attribute
    ) {
        int[] value = new int[1];
        return egl.eglGetConfigAttrib(display, config, attribute, value) ? value[0] : -1;
    }

    private static boolean hasExtension(EGL10 egl, EGLDisplay display, String wanted) {
        String extensions = egl.eglQueryString(display, EGL10.EGL_EXTENSIONS);
        if (extensions == null) {
            return false;
        }
        for (String extension : extensions.split(" ")) {
            if (wanted.equals(extension)) {
                return true;
            }
        }
        return false;
    }
}
