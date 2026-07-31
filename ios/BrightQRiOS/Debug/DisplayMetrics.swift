import Foundation

struct EDRRenderMetrics: Equatable {
    var edrLayerEnabled = false
    var moduleCount = 0
    var pixelsPerModule = 0
    var renderWidth = 0
    var renderHeight = 0
    var drawableWidth = 0
    var drawableHeight = 0

    static let empty = EDRRenderMetrics()
}

enum DisplayMetrics {
    static var hardwareIdentifier: String {
        var systemInfo = utsname()
        uname(&systemInfo)

        let capacity = MemoryLayout.size(ofValue: systemInfo.machine)
        return withUnsafePointer(to: &systemInfo.machine) { pointer in
            pointer.withMemoryRebound(to: CChar.self, capacity: capacity) {
                String(cString: $0)
            }
        }
    }

    static var thermalStateLabel: String {
        switch ProcessInfo.processInfo.thermalState {
        case .nominal: return "Nominal"
        case .fair: return "Fair"
        case .serious: return "Serious"
        case .critical: return "Critical"
        @unknown default: return "Unknown"
        }
    }
}
