import Foundation

enum EDRTestMode: UInt32, CaseIterable, Identifiable {
    case white = 0
    case checkerboard = 1
    case qr = 2

    var id: UInt32 { rawValue }

    var title: String {
        switch self {
        case .white: return "White"
        case .checkerboard: return "Checker"
        case .qr: return "QR"
        }
    }

    var explanation: String {
        switch self {
        case .white:
            return "Proves localized EDR before introducing module geometry."
        case .checkerboard:
            return "Proves sharp black and maximum-EDR pixels can coexist."
        case .qr:
            return "Renders a real QR matrix with a four-module EDR quiet zone."
        }
    }
}
