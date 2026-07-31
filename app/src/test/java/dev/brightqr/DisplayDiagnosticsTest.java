package dev.brightqr;

import android.os.PowerManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DisplayDiagnosticsTest {
    @Test
    public void labelsNormalThermalState() {
        assertEquals(
                "Normal",
                DisplayDiagnostics.thermalStatusLabel(PowerManager.THERMAL_STATUS_NONE)
        );
    }

    @Test
    public void labelsSevereThermalState() {
        assertEquals(
                "Severe",
                DisplayDiagnostics.thermalStatusLabel(PowerManager.THERMAL_STATUS_SEVERE)
        );
    }
}
