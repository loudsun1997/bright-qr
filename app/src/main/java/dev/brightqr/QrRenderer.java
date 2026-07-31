package dev.brightqr;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.google.zxing.common.BitMatrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class QrRenderer implements GLSurfaceView.Renderer {
    interface StatusListener {
        void onRenderingModeChanged(boolean hdrActive, String detail);
    }

    private static final float[] QUAD = {
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f
    };

    private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "  vTexCoord = aTexCoord;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "uniform sampler2D uQr;\n" +
            "uniform float uWhiteCodeValue;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  float module = step(0.5, texture2D(uQr, vTexCoord).r);\n" +
            "  float white = 1.0 - module;\n" +
            // For a PQ EGL surface, 1.0 is ST 2084's 10,000-nit endpoint.
            // SurfaceFlinger maps/clamps that request to current display headroom.
            "  gl_FragColor = vec4(vec3(white * uWhiteCodeValue), 1.0);\n" +
            "}\n";

    private final HdrEglState eglState;
    private final StatusListener statusListener;
    private final FloatBuffer quadBuffer;

    private volatile BitMatrix pendingMatrix;
    private BitMatrix uploadedMatrix;
    private int program;
    private int texture;

    QrRenderer(HdrEglState eglState, StatusListener statusListener) {
        this.eglState = eglState;
        this.statusListener = statusListener;
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
    }

    void setQrMatrix(BitMatrix matrix) {
        pendingMatrix = matrix;
    }

    @Override
    public void onSurfaceCreated(GL10 ignored, EGLConfig config) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        checkProgram(program);
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        texture = textures[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DITHER);

        boolean hdrActive = eglState.isPqSurfaceActive();
        statusListener.onRenderingModeChanged(
                hdrActive,
                hdrActive ? "10-bit BT.2020 PQ" : "standard EGL surface"
        );
        uploadedMatrix = null;
    }

    @Override
    public void onSurfaceChanged(GL10 ignored, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 ignored) {
        BitMatrix matrix = pendingMatrix;
        if (matrix != null && matrix != uploadedMatrix) {
            upload(matrix);
            uploadedMatrix = matrix;
        }

        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (uploadedMatrix == null) {
            return;
        }

        GLES20.glUseProgram(program);
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int texCoord = GLES20.glGetAttribLocation(program, "aTexCoord");

        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES,
                quadBuffer);
        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(texCoord);
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES,
                quadBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uQr"), 0);
        GLES20.glUniform1f(
                GLES20.glGetUniformLocation(program, "uWhiteCodeValue"),
                HdrPolicy.PQ_MAX_CODE_VALUE
        );
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisableVertexAttribArray(texCoord);
    }

    private void upload(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels.put(matrix.get(x, y) ? (byte) 0xff : 0);
            }
        }
        pixels.position(0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_LUMINANCE,
                width,
                height,
                0,
                GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE,
                pixels
        );
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("GL shader compilation failed: " + log);
        }
        return shader;
    }

    private static void checkProgram(int program) {
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("GL program link failed: " + log);
        }
    }
}
