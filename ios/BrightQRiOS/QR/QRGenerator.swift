import CoreImage
import CoreImage.CIFilterBuiltins
import Foundation

enum QRGeneratorError: Error, LocalizedError {
    case emptyPayload
    case generationFailed
    case invalidExtent
    case renderingFailed

    var errorDescription: String? {
        switch self {
        case .emptyPayload:
            return "The QR payload is empty."
        case .generationFailed:
            return "Core Image could not generate the QR code."
        case .invalidExtent:
            return "Core Image returned an invalid QR matrix size."
        case .renderingFailed:
            return "Core Image could not expose the QR module matrix."
        }
    }
}

enum QRGenerator {
    private static let context = CIContext(options: [
        .cacheIntermediates: false,
        .useSoftwareRenderer: true
    ])

    static func generate(from payload: String) throws -> QRMatrix {
        guard let data = payload.data(using: .utf8), !data.isEmpty else {
            throw QRGeneratorError.emptyPayload
        }

        let filter = CIFilter.qrCodeGenerator()
        filter.message = data
        filter.correctionLevel = "M"

        guard let image = filter.outputImage else {
            throw QRGeneratorError.generationFailed
        }

        let extent = image.extent.integral
        let width = Int(extent.width)
        let height = Int(extent.height)
        guard width > 0, width == height else {
            throw QRGeneratorError.invalidExtent
        }

        var pixels = [UInt8](repeating: 0, count: width * height)
        let rendered = pixels.withUnsafeMutableBytes { bytes -> Bool in
            guard let address = bytes.baseAddress else { return false }
            context.render(
                image,
                toBitmap: address,
                rowBytes: width,
                bounds: extent,
                format: .L8,
                colorSpace: nil
            )
            return true
        }
        guard rendered else {
            throw QRGeneratorError.renderingFailed
        }

        // Core Image coordinates start at the lower-left. Metal fragment
        // coordinates start at the upper-left, so normalize the module array
        // into visual top-to-bottom order here rather than mirroring the QR.
        var modules = [Bool](repeating: false, count: width * height)
        for y in 0..<height {
            let sourceY = height - 1 - y
            for x in 0..<width {
                modules[(y * width) + x] = pixels[(sourceY * width) + x] < 128
            }
        }

        return QRMatrix(size: width, modules: modules)
    }
}
