import MetalKit

final class EDRRenderer: NSObject, MTKViewDelegate {
    private weak var metalView: MTKView?
    private let commandQueue: MTLCommandQueue
    private let pipelineState: MTLRenderPipelineState
    private let device: MTLDevice

    private var mode = EDRTestMode.white
    private var matrix = QRMatrix.fallback
    private var maximumWhite: Float = 1
    private var moduleBuffer: MTLBuffer
    private var lastMetrics = EDRRenderMetrics.empty

    var onMetricsChanged: ((EDRRenderMetrics) -> Void)?

    init?(metalView: MTKView) {
        guard
            let device = metalView.device,
            let commandQueue = device.makeCommandQueue(),
            let library = device.makeDefaultLibrary(),
            let vertexFunction = library.makeFunction(name: "fullScreenVertex"),
            let fragmentFunction = library.makeFunction(name: "edrFragment"),
            let initialBuffer = device.makeBuffer(
                length: MemoryLayout<UInt32>.stride,
                options: .storageModeShared
            )
        else {
            return nil
        }

        let descriptor = MTLRenderPipelineDescriptor()
        descriptor.label = "Bright QR EDR pipeline"
        descriptor.vertexFunction = vertexFunction
        descriptor.fragmentFunction = fragmentFunction
        descriptor.colorAttachments[0].pixelFormat = .rgba16Float

        guard let pipelineState = try? device.makeRenderPipelineState(descriptor: descriptor) else {
            return nil
        }

        self.metalView = metalView
        self.device = device
        self.commandQueue = commandQueue
        self.pipelineState = pipelineState
        self.moduleBuffer = initialBuffer
        super.init()

        replaceModuleBuffer(with: matrix)
        metalView.delegate = self
    }

    func update(mode: EDRTestMode, matrix: QRMatrix, maximumWhite: Float) {
        let finiteWhite = maximumWhite.isFinite ? maximumWhite : 1
        let normalizedWhite = max(1, finiteWhite)
        let matrixChanged = self.matrix != matrix
        let changed = self.mode != mode || matrixChanged || self.maximumWhite != normalizedWhite

        self.mode = mode
        self.matrix = matrix
        self.maximumWhite = normalizedWhite
        if matrixChanged {
            replaceModuleBuffer(with: matrix)
        }
        if changed {
            metalView?.setNeedsDisplay()
        }
    }

    private func replaceModuleBuffer(with matrix: QRMatrix) {
        let words = matrix.modules.map { $0 ? UInt32(1) : UInt32(0) }
        let byteCount = max(MemoryLayout<UInt32>.stride, words.count * MemoryLayout<UInt32>.stride)
        if let buffer = words.withUnsafeBytes({ bytes -> MTLBuffer? in
            guard let address = bytes.baseAddress else {
                return device.makeBuffer(length: byteCount, options: .storageModeShared)
            }
            return device.makeBuffer(bytes: address, length: byteCount, options: .storageModeShared)
        }) {
            buffer.label = "QR binary module matrix"
            moduleBuffer = buffer
        }
    }

    func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        view.setNeedsDisplay()
    }

    func draw(in view: MTKView) {
        guard
            view.drawableSize.width >= 1,
            view.drawableSize.height >= 1,
            let descriptor = view.currentRenderPassDescriptor,
            let drawable = view.currentDrawable,
            let commandBuffer = commandQueue.makeCommandBuffer(),
            let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: descriptor)
        else {
            return
        }

        let drawableWidth = UInt32(max(1, Int(view.drawableSize.width.rounded(.down))))
        let drawableHeight = UInt32(max(1, Int(view.drawableSize.height.rounded(.down))))
        let qrLayout = QRLayout.calculate(
            drawableSize: view.drawableSize,
            matrixSize: matrix.size
        )

        var uniforms = EDRUniforms(
            geometry: SIMD4(
                drawableWidth,
                drawableHeight,
                UInt32(matrix.size),
                UInt32(QRLayout.quietZoneModules)
            ),
            layout: SIMD4(
                UInt32(qrLayout.pixelsPerModule),
                UInt32(qrLayout.renderPixels),
                UInt32(qrLayout.originX),
                UInt32(qrLayout.originY)
            ),
            values: SIMD4(maximumWhite, Float(mode.rawValue), 0, 0)
        )

        encoder.label = "Bright QR static EDR draw"
        encoder.setRenderPipelineState(pipelineState)
        encoder.setFragmentBytes(
            &uniforms,
            length: MemoryLayout<EDRUniforms>.stride,
            index: 0
        )
        encoder.setFragmentBuffer(moduleBuffer, offset: 0, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 3)
        encoder.endEncoding()
        commandBuffer.present(drawable)
        commandBuffer.commit()

        publishMetrics(for: view, qrLayout: qrLayout)
    }

    private func publishMetrics(for view: MTKView, qrLayout: QRLayout) {
        let shortestSide = max(1, Int(min(view.drawableSize.width, view.drawableSize.height)))
        let metrics: EDRRenderMetrics
        switch mode {
        case .white:
            metrics = EDRRenderMetrics(
                edrLayerEnabled: true,
                moduleCount: 1,
                pixelsPerModule: shortestSide,
                renderWidth: Int(view.drawableSize.width),
                renderHeight: Int(view.drawableSize.height),
                drawableWidth: Int(view.drawableSize.width),
                drawableHeight: Int(view.drawableSize.height)
            )
        case .checkerboard:
            let cellPixels = max(1, shortestSide / 8)
            metrics = EDRRenderMetrics(
                edrLayerEnabled: true,
                moduleCount: 8,
                pixelsPerModule: cellPixels,
                renderWidth: cellPixels * 8,
                renderHeight: cellPixels * 8,
                drawableWidth: Int(view.drawableSize.width),
                drawableHeight: Int(view.drawableSize.height)
            )
        case .qr:
            metrics = EDRRenderMetrics(
                edrLayerEnabled: true,
                moduleCount: matrix.size,
                pixelsPerModule: qrLayout.pixelsPerModule,
                renderWidth: qrLayout.renderPixels,
                renderHeight: qrLayout.renderPixels,
                drawableWidth: Int(view.drawableSize.width),
                drawableHeight: Int(view.drawableSize.height)
            )
        }

        guard metrics != lastMetrics else { return }
        lastMetrics = metrics
        DispatchQueue.main.async { [weak self] in
            self?.onMetricsChanged?(metrics)
        }
    }
}
