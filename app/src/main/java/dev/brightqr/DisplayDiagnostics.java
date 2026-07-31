package dev.brightqr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Consumer;

final class DisplayDiagnostics {
    interface Listener {
        void onDiagnosticsChanged(String text);
    }

    private final Context context;
    private final Display display;
    private final Listener listener;
    private final PowerManager powerManager;

    private Consumer<Display> ratioListener;
    private PowerManager.OnThermalStatusChangedListener thermalListener;
    private BroadcastReceiver powerSaveReceiver;
    private float requestedHeadroom = HdrPolicy.MAX_REQUESTED_HEADROOM;
    private boolean hdrRendererActive;
    private String rendererDetail = "starting";
    private String lastReport = "Diagnostics not ready";

    DisplayDiagnostics(Context context, Display display, Listener listener) {
        this.context = context.getApplicationContext();
        this.display = display;
        this.listener = listener;
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    void start(java.util.concurrent.Executor mainExecutor) {
        publish();
        if (Build.VERSION.SDK_INT >= 34
                && display != null
                && display.isHdrSdrRatioAvailable()
                && ratioListener == null) {
            ratioListener = ignored -> publish();
            display.registerHdrSdrRatioChangedListener(mainExecutor, ratioListener);
        }
        if (Build.VERSION.SDK_INT >= 29
                && powerManager != null
                && thermalListener == null) {
            thermalListener = ignored -> publish();
            powerManager.addThermalStatusListener(mainExecutor, thermalListener);
        }
        if (powerSaveReceiver == null) {
            powerSaveReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ignored, Intent intent) {
                    publish();
                }
            };
            IntentFilter filter = new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(powerSaveReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(powerSaveReceiver, filter);
            }
        }
    }

    void stop() {
        if (Build.VERSION.SDK_INT >= 34 && display != null && ratioListener != null) {
            display.unregisterHdrSdrRatioChangedListener(ratioListener);
            ratioListener = null;
        }
        if (Build.VERSION.SDK_INT >= 29 && powerManager != null && thermalListener != null) {
            powerManager.removeThermalStatusListener(thermalListener);
            thermalListener = null;
        }
        if (powerSaveReceiver != null) {
            context.unregisterReceiver(powerSaveReceiver);
            powerSaveReceiver = null;
        }
    }

    void refresh() {
        publish();
    }

    void setRequestedHeadroom(float headroom) {
        requestedHeadroom = headroom;
        publish();
    }

    void setRendererState(boolean hdrActive, String detail) {
        hdrRendererActive = hdrActive;
        rendererDetail = detail;
        publish();
    }

    String getReport() {
        return lastReport;
    }

    private void publish() {
        String current = "unavailable";
        if (Build.VERSION.SDK_INT >= 34
                && display != null
                && display.isHdrSdrRatioAvailable()) {
            current = formatRatio(display.getHdrSdrRatio());
        }

        String highest = Build.VERSION.SDK_INT >= 36
                ? readHighestRatioApi36(display)
                : "requires Android 16";
        String device = titleCase(Build.MANUFACTURER) + " " + Build.MODEL;
        String renderer = (hdrRendererActive ? "HDR · " : "SDR · ") + rendererDetail;
        String thermal = currentThermalStatus();
        String batterySaver = powerManager != null && powerManager.isPowerSaveMode() ? "On" : "Off";
        String hdrDisplay = display != null && display.isHdr() ? "Yes" : "No";

        lastReport =
                "BRIGHT QR HDR TEST\n" +
                "Device: " + device + "\n" +
                "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "HDR display: " + hdrDisplay + "\n" +
                "Renderer: " + renderer + "\n" +
                "Maximum supported: " + highest + "\n" +
                "Currently available: " + current + "\n" +
                "Requested: " + formatRequest(requestedHeadroom) + "\n" +
                "Thermal: " + thermal + "\n" +
                "Battery Saver: " + batterySaver;
        listener.onDiagnosticsChanged(lastReport);
    }

    private String currentThermalStatus() {
        if (Build.VERSION.SDK_INT < 29 || powerManager == null) {
            return "unavailable";
        }
        return thermalStatusLabel(powerManager.getCurrentThermalStatus());
    }

    static String thermalStatusLabel(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE:
                return "Normal";
            case PowerManager.THERMAL_STATUS_LIGHT:
                return "Light";
            case PowerManager.THERMAL_STATUS_MODERATE:
                return "Moderate";
            case PowerManager.THERMAL_STATUS_SEVERE:
                return "Severe";
            case PowerManager.THERMAL_STATUS_CRITICAL:
                return "Critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY:
                return "Emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                return "Shutdown";
            default:
                return "Unknown (" + status + ")";
        }
    }

    // Reflection keeps the project buildable with the locally installed API 35 SDK.
    // The method is public platform API starting at API 36.
    private static String readHighestRatioApi36(Display display) {
        if (display == null) {
            return "unavailable";
        }
        try {
            Method method = Display.class.getMethod("getHighestHdrSdrRatio");
            Object result = method.invoke(display);
            return result instanceof Float ? formatRatio((Float) result) : "unavailable";
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException error) {
            return "unavailable";
        }
    }

    private static String formatRatio(float ratio) {
        return String.format(Locale.US, "%.1f×", ratio);
    }

    private static String formatRequest(float headroom) {
        return HdrPolicy.isMaximumRequest(headroom)
                ? "MAX (10,000×)"
                : formatRatio(headroom);
    }

    private static String titleCase(String value) {
        if (value == null || value.isEmpty()) {
            return "Unknown";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
