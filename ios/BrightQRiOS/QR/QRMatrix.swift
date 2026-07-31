import Foundation

struct QRMatrix: Equatable {
    let size: Int
    let modules: [Bool]

    init(size: Int, modules: [Bool]) {
        precondition(size > 0)
        precondition(modules.count == size * size)
        self.size = size
        self.modules = modules
    }

    subscript(x: Int, y: Int) -> Bool {
        modules[(y * size) + x]
    }

    static let fallback = QRMatrix(
        size: 1,
        modules: [false]
    )
}
