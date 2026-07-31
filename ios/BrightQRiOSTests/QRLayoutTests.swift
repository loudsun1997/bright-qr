import CoreGraphics
import XCTest
@testable import BrightQRiOS

final class QRLayoutTests: XCTestCase {
    func testLayoutUsesWholePhysicalPixelsPerModule() {
        let layout = QRLayout.calculate(
            drawableSize: CGSize(width: 793, height: 801),
            matrixSize: 25
        )

        XCTAssertEqual(layout.pixelsPerModule, 24)
        XCTAssertEqual(layout.renderPixels, 792)
        XCTAssertEqual(layout.originX, 0)
        XCTAssertEqual(layout.originY, 4)
        XCTAssertEqual(layout.renderPixels % (25 + QRLayout.quietZoneModules * 2), 0)
    }

    func testUniformLayoutMatchesMetalAlignment() {
        XCTAssertEqual(MemoryLayout<EDRUniforms>.stride, 48)
        XCTAssertEqual(MemoryLayout<EDRUniforms>.alignment, 16)
    }
}
