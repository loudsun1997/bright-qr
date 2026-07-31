import CoreGraphics

struct QRLayout: Equatable {
    static let quietZoneModules = 4

    let pixelsPerModule: Int
    let renderPixels: Int
    let originX: Int
    let originY: Int

    static func calculate(drawableSize: CGSize, matrixSize: Int) -> QRLayout {
        let drawableWidth = max(0, Int(drawableSize.width.rounded(.down)))
        let drawableHeight = max(0, Int(drawableSize.height.rounded(.down)))
        let totalModules = max(1, matrixSize + (quietZoneModules * 2))
        let availablePixels = min(drawableWidth, drawableHeight)
        let pixelsPerModule = max(1, availablePixels / totalModules)
        let renderPixels = totalModules * pixelsPerModule

        return QRLayout(
            pixelsPerModule: pixelsPerModule,
            renderPixels: renderPixels,
            originX: max(0, (drawableWidth - renderPixels) / 2),
            originY: max(0, (drawableHeight - renderPixels) / 2)
        )
    }
}
