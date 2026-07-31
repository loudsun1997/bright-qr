import Combine
import SwiftUI
import UIKit

struct ContentView: View {
    static let samplePayload = """
    BRIGHT CINEMA
    The Last Horizon
    Saturday · 7:30 PM
    Auditorium 6
    Row J · Seats 12–13
    Booking BQR-8X42
    """

    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var displayMonitor = EDRDisplayMonitor()
    @State private var selectedMode = EDRTestMode.white
    @State private var renderMetrics = EDRRenderMetrics.empty
    @State private var copiedReport = false

    private let matrix: QRMatrix
    private let generationError: String?
    private let refreshTimer = Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()

    init() {
        do {
            matrix = try QRGenerator.generate(from: Self.samplePayload)
            generationError = nil
        } catch {
            matrix = .fallback
            generationError = error.localizedDescription
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    introduction
                    ticketCard
                    testSelector
                    brightnessComparison

                    if let generationError {
                        Label(generationError, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(.orange)
                    }

                    EDRDiagnosticsView(
                        displayInfo: displayMonitor.info,
                        renderMetrics: renderMetrics,
                        mode: selectedMode,
                        matrix: matrix
                    )

                    Button {
                        UIPasteboard.general.string = diagnosticReport
                        copiedReport = true
                    } label: {
                        Label(copiedReport ? "Report copied" : "Copy test report", systemImage: copiedReport ? "checkmark" : "doc.on.doc")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)

                    testingNotes
                }
                .frame(maxWidth: 620)
                .padding(20)
                .frame(maxWidth: .infinity)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("Bright QR")
            .navigationBarTitleDisplayMode(.inline)
        }
        .onReceive(refreshTimer) { _ in
            displayMonitor.refresh()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                displayMonitor.refresh()
            }
        }
        .onChange(of: selectedMode) { _ in
            copiedReport = false
        }
    }

    private var introduction: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("LOCALIZED EDR TEST")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.tint)
                Text("iOS 16+")
                    .font(.caption2.weight(.semibold))
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(.thinMaterial, in: Capsule())
            }

            Text("Maximum QR brightness without changing your screen-brightness setting")
                .font(.title2.bold())

            Text("Only the Metal region below requests Extended Dynamic Range. The surrounding SwiftUI remains ordinary SDR, and iOS decides the physical output available right now.")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Label("This app never writes UIScreen.brightness.", systemImage: "checkmark.shield")
                .font(.footnote.weight(.medium))
                .foregroundStyle(.green)
        }
    }

    private var ticketCard: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Label("BRIGHT CINEMA", systemImage: "film")
                    .font(.caption.weight(.bold))
                Spacer()
                Text("SAT · 7:30 PM")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            Divider()
            Text("The Last Horizon")
                .font(.headline)
            HStack {
                ticketValue("AUDITORIUM", "6")
                Spacer()
                ticketValue("ROW", "J")
                Spacer()
                ticketValue("SEATS", "12–13")
            }
            Text("Booking BQR-8X42")
                .font(.caption.monospaced())
                .foregroundStyle(.secondary)
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    private func ticketValue(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.headline.monospacedDigit())
        }
    }

    private var testSelector: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Test pattern")
                .font(.headline)
            Picker("Test pattern", selection: $selectedMode) {
                ForEach(EDRTestMode.allCases) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            Text(selectedMode.explanation)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var brightnessComparison: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("Brightness comparison")
                .font(.headline)

            Text("SDR WHITE · ordinary SwiftUI")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Color.white
                .frame(height: 64)
                .overlay(Rectangle().stroke(Color.secondary.opacity(0.35), lineWidth: 1))
                .accessibilityLabel("Ordinary SDR white reference")

            HStack {
                Text(selectedMode == .qr ? "MAX EDR QR" : "MAX EDR \(selectedMode.title.uppercased())")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                Text("White = \(ratio(maximumWhite))")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.tint)
            }

            edrSurface

            Text("Potential headroom is deliberately sent as the white value. If less headroom is currently available, iOS clips it to the attainable peak.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var edrSurface: some View {
        let surface = EDRMetalView(
            mode: selectedMode,
            matrix: matrix,
            maximumWhite: maximumWhite,
            onScreenChanged: { screen in
                displayMonitor.attach(to: screen)
            },
            onMetricsChanged: { metrics in
                if renderMetrics != metrics {
                    renderMetrics = metrics
                }
            }
        )
        .accessibilityLabel("Extended dynamic range \(selectedMode.title) test")

        if selectedMode == .qr {
            surface
                .aspectRatio(1, contentMode: .fit)
                .frame(maxWidth: 360)
                .frame(maxWidth: .infinity)
                .overlay(Rectangle().stroke(Color.secondary.opacity(0.35), lineWidth: 1))
        } else {
            surface
                .frame(height: 104)
                .overlay(Rectangle().stroke(Color.secondary.opacity(0.35), lineWidth: 1))
        }
    }

    private var testingNotes: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text("Controlled iPhone test")
                .font(.headline)
            Label("Set brightness manually near 10%", systemImage: "sun.min")
            Label("Turn off Low Power Mode for the baseline", systemImage: "battery.100")
            Label("Keep the device cool", systemImage: "thermometer.medium")
            Label("Judge luminance on the physical display—not a screenshot", systemImage: "iphone")
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
        .padding(.bottom, 16)
    }

    private var maximumWhite: Float {
        let potential = displayMonitor.info.potentialHeadroom
        guard potential.isFinite else { return 1 }
        return Float(max(1, potential))
    }

    private var diagnosticReport: String {
        """
        BRIGHT QR iOS EDR TEST
        Device: \(UIDevice.current.model) · \(DisplayMetrics.hardwareIdentifier)
        iOS: \(UIDevice.current.systemVersion)
        Brightness: \(String(format: "%.0f%%", displayMonitor.info.systemBrightness * 100))
        Current EDR: \(ratio(Float(displayMonitor.info.currentHeadroom)))
        Potential EDR: \(ratio(Float(displayMonitor.info.potentialHeadroom)))
        Requested white: \(ratio(maximumWhite))
        EDR layer: \(renderMetrics.edrLayerEnabled ? "Enabled" : "Unavailable")
        Renderer: rgba16Float · extendedLinearDisplayP3
        Mode: \(selectedMode.title)
        QR modules: \(matrix.size) × \(matrix.size)
        Pixels/module: \(renderMetrics.pixelsPerModule)
        Rendered: \(renderMetrics.renderWidth) × \(renderMetrics.renderHeight) px
        Thermal: \(DisplayMetrics.thermalStateLabel)
        Low Power Mode: \(ProcessInfo.processInfo.isLowPowerModeEnabled ? "On" : "Off")
        """
    }

    private func ratio(_ value: Float) -> String {
        String(format: "%.2f×", value)
    }
}
