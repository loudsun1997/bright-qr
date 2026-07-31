import Combine
import UIKit

struct EDRDisplayInfo: Equatable {
    var currentHeadroom: CGFloat
    var potentialHeadroom: CGFloat
    var systemBrightness: CGFloat
    var edrAvailable: Bool

    static let unavailable = EDRDisplayInfo(
        currentHeadroom: 1,
        potentialHeadroom: 1,
        systemBrightness: 0,
        edrAvailable: false
    )

    @MainActor
    static func capture(from associatedScreen: UIScreen?) -> EDRDisplayInfo {
        let screen = associatedScreen ?? applicationWindowScreen()
        guard let screen else { return .unavailable }

        let current = max(1, screen.currentEDRHeadroom)
        let potential = max(1, screen.potentialEDRHeadroom)
        return EDRDisplayInfo(
            currentHeadroom: current,
            potentialHeadroom: potential,
            systemBrightness: screen.brightness,
            edrAvailable: potential > 1
        )
    }

    @MainActor
    private static func applicationWindowScreen() -> UIScreen? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .screen
    }
}

@MainActor
final class EDRDisplayMonitor: ObservableObject {
    @Published private(set) var info = EDRDisplayInfo.unavailable
    private weak var associatedScreen: UIScreen?

    func attach(to screen: UIScreen?) {
        associatedScreen = screen
        refresh()
    }

    func refresh() {
        info = EDRDisplayInfo.capture(from: associatedScreen)
    }
}
