import SwiftUI
import UIKit

struct EDRDiagnosticsView: View {
    let displayInfo: EDRDisplayInfo
    let renderMetrics: EDRRenderMetrics
    let mode: EDRTestMode
    let matrix: QRMatrix

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Live diagnostics", systemImage: "waveform.path.ecg.rectangle")
                    .font(.headline)
                Spacer()
                Text(displayInfo.edrAvailable ? "EDR AVAILABLE" : "SDR FALLBACK")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(displayInfo.edrAvailable ? Color.green : Color.orange)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(.thinMaterial, in: Capsule())
            }

            Grid(alignment: .leading, horizontalSpacing: 18, verticalSpacing: 7) {
                diagnosticRow("Device", "\(UIDevice.current.model) · \(DisplayMetrics.hardwareIdentifier)")
                diagnosticRow("iOS", "\(UIDevice.current.systemVersion)")
                diagnosticRow("Brightness", percent(displayInfo.systemBrightness))
                diagnosticRow("Current EDR", ratio(displayInfo.currentHeadroom))
                diagnosticRow("Potential EDR", ratio(displayInfo.potentialHeadroom))
                diagnosticRow("Requested white", ratio(max(1, displayInfo.potentialHeadroom)))
                diagnosticRow("EDR layer", renderMetrics.edrLayerEnabled ? "Enabled" : "Unavailable")
                diagnosticRow("Pixel format", "rgba16Float")
                diagnosticRow("Color space", "extendedLinearDisplayP3")
                diagnosticRow("Test mode", mode.title)
                diagnosticRow("QR modules", mode == .qr ? "\(matrix.size) × \(matrix.size)" : "—")
                diagnosticRow("Pixels/module", renderMetrics.pixelsPerModule > 0 ? "\(renderMetrics.pixelsPerModule) px" : "—")
                diagnosticRow("Rendered", renderSize)
                diagnosticRow("Drawable", drawableSize)
                diagnosticRow("Thermal", DisplayMetrics.thermalStateLabel)
                diagnosticRow("Low Power Mode", ProcessInfo.processInfo.isLowPowerModeEnabled ? "On" : "Off")
            }
            .font(.caption)
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private func diagnosticRow(_ label: String, _ value: String) -> some View {
        GridRow {
            Text(label)
                .foregroundStyle(.secondary)
            Text(value)
                .fontDesign(.monospaced)
                .textSelection(.enabled)
        }
    }

    private var renderSize: String {
        guard renderMetrics.renderWidth > 0, renderMetrics.renderHeight > 0 else { return "—" }
        return "\(renderMetrics.renderWidth) × \(renderMetrics.renderHeight) px"
    }

    private var drawableSize: String {
        guard renderMetrics.drawableWidth > 0, renderMetrics.drawableHeight > 0 else { return "—" }
        return "\(renderMetrics.drawableWidth) × \(renderMetrics.drawableHeight) px"
    }

    private func ratio(_ value: CGFloat) -> String {
        String(format: "%.2f×", value)
    }

    private func percent(_ value: CGFloat) -> String {
        String(format: "%.0f%%", value * 100)
    }
}
