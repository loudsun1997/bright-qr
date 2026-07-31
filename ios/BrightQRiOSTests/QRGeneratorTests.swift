import XCTest
@testable import BrightQRiOS

final class QRGeneratorTests: XCTestCase {
    func testGeneratedMatrixHasThreeCorrectlyOrientedFinderPatterns() throws {
        let matrix = try QRGenerator.generate(from: "BRIGHT QR ORIENTATION TEST")

        XCTAssertTrue(matchesFinder(in: matrix, originX: 0, originY: 0))
        XCTAssertTrue(matchesFinder(in: matrix, originX: matrix.size - 7, originY: 0))
        XCTAssertTrue(matchesFinder(in: matrix, originX: 0, originY: matrix.size - 7))
        XCTAssertFalse(matchesFinder(in: matrix, originX: matrix.size - 7, originY: matrix.size - 7))
    }

    func testSampleTicketProducesSquareBinaryMatrix() throws {
        let matrix = try QRGenerator.generate(from: ContentView.samplePayload)

        XCTAssertGreaterThanOrEqual(matrix.size, 21)
        XCTAssertEqual(matrix.modules.count, matrix.size * matrix.size)
        XCTAssertTrue(matrix.modules.contains(true))
        XCTAssertTrue(matrix.modules.contains(false))
    }

    private func matchesFinder(in matrix: QRMatrix, originX: Int, originY: Int) -> Bool {
        guard originX >= 0, originY >= 0 else { return false }
        for y in 0..<7 {
            for x in 0..<7 {
                let expectedBlack =
                    x == 0 || x == 6 || y == 0 || y == 6 ||
                    ((2...4).contains(x) && (2...4).contains(y))
                if matrix[originX + x, originY + y] != expectedBlack {
                    return false
                }
            }
        }
        return true
    }
}
