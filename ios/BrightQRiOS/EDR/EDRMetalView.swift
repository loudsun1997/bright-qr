import MetalKit
import SwiftUI
import UIKit

struct EDRMetalView: UIViewRepresentable {
    let mode: EDRTestMode
    let matrix: QRMatrix
    let maximumWhite: Float
    let onScreenChanged: (UIScreen?) -> Void
    let onMetricsChanged: (EDRRenderMetrics) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> EDRHostView {
        let host = EDRHostView()
        host.onScreenChanged = onScreenChanged

        configureEDR(host.metalView)
        if let renderer = EDRRenderer(metalView: host.metalView) {
            context.coordinator.renderer = renderer
            host.showsMetal = true
        } else {
            host.showsMetal = false
        }
        updateCallbacks(context.coordinator)
        return host
    }

    func updateUIView(_ host: EDRHostView, context: Context) {
        host.onScreenChanged = onScreenChanged
        updateCallbacks(context.coordinator)

        host.fallbackView.mode = mode
        host.fallbackView.matrix = matrix
        context.coordinator.renderer?.update(
            mode: mode,
            matrix: matrix,
            maximumWhite: maximumWhite
        )

        if context.coordinator.renderer == nil {
            let fallbackMetrics = EDRRenderMetrics(
                edrLayerEnabled: false,
                moduleCount: mode == .qr ? matrix.size : (mode == .checkerboard ? 8 : 1),
                pixelsPerModule: 0,
                renderWidth: Int(host.bounds.width),
                renderHeight: Int(host.bounds.height),
                drawableWidth: Int(host.bounds.width),
                drawableHeight: Int(host.bounds.height)
            )
            DispatchQueue.main.async {
                onMetricsChanged(fallbackMetrics)
            }
        }
    }

    private func updateCallbacks(_ coordinator: Coordinator) {
        coordinator.renderer?.onMetricsChanged = onMetricsChanged
    }

    private func configureEDR(_ view: MTKView) {
        view.colorPixelFormat = .rgba16Float
        view.clearColor = MTLClearColorMake(0, 0, 0, 1)
        view.framebufferOnly = true
        view.isPaused = true
        view.enableSetNeedsDisplay = true
        view.autoResizeDrawable = true
        view.isOpaque = true

        guard let metalLayer = view.layer as? CAMetalLayer else { return }
        metalLayer.colorspace = CGColorSpace(name: CGColorSpace.extendedLinearDisplayP3)
        metalLayer.wantsExtendedDynamicRangeContent = true

        // CALayer.preferredDynamicRange is an iOS 26 SDK addition. Keep the
        // iOS 16 CAMetalLayer switch as the independent baseline so the project
        // still compiles and runs with Xcode 16-era SDKs.
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            metalLayer.preferredDynamicRange = .high
        }
        #endif
    }

    final class Coordinator {
        var renderer: EDRRenderer?
    }
}

final class EDRHostView: UIView {
    let metalView: MTKView
    let fallbackView = SDRPatternView()
    var onScreenChanged: ((UIScreen?) -> Void)?

    var showsMetal = true {
        didSet {
            metalView.isHidden = !showsMetal
            fallbackView.isHidden = showsMetal
        }
    }

    override init(frame: CGRect) {
        metalView = MTKView(frame: .zero, device: MTLCreateSystemDefaultDevice())
        super.init(frame: frame)
        isOpaque = true
        backgroundColor = .black
        addSubview(fallbackView)
        addSubview(metalView)
        fallbackView.isHidden = true
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        metalView.frame = bounds
        fallbackView.frame = bounds
        metalView.setNeedsDisplay()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.onScreenChanged?(self.window?.screen)
        }
    }
}

final class SDRPatternView: UIView {
    var mode = EDRTestMode.white {
        didSet { setNeedsDisplay() }
    }
    var matrix = QRMatrix.fallback {
        didSet { setNeedsDisplay() }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        isOpaque = true
        contentMode = .redraw
        backgroundColor = .white
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        context.setAllowsAntialiasing(false)
        context.setShouldAntialias(false)
        context.setFillColor(UIColor.white.cgColor)
        context.fill(bounds)

        switch mode {
        case .white:
            return
        case .checkerboard:
            let cell = max(1, floor(min(bounds.width, bounds.height) / 8))
            context.setFillColor(UIColor.black.cgColor)
            for y in 0..<8 where y.isMultiple(of: 2) {
                for x in 0..<8 where x.isMultiple(of: 2) {
                    context.fill(CGRect(x: CGFloat(x) * cell, y: CGFloat(y) * cell, width: cell, height: cell))
                }
            }
            for y in 0..<8 where !y.isMultiple(of: 2) {
                for x in 0..<8 where !x.isMultiple(of: 2) {
                    context.fill(CGRect(x: CGFloat(x) * cell, y: CGFloat(y) * cell, width: cell, height: cell))
                }
            }
        case .qr:
            let totalModules = matrix.size + (QRLayout.quietZoneModules * 2)
            let modulePoints = max(1, floor(min(bounds.width, bounds.height) / CGFloat(totalModules)))
            let renderPoints = modulePoints * CGFloat(totalModules)
            let origin = CGPoint(
                x: floor((bounds.width - renderPoints) / 2),
                y: floor((bounds.height - renderPoints) / 2)
            )
            context.setFillColor(UIColor.black.cgColor)
            for y in 0..<matrix.size {
                for x in 0..<matrix.size where matrix[x, y] {
                    context.fill(CGRect(
                        x: origin.x + CGFloat(x + QRLayout.quietZoneModules) * modulePoints,
                        y: origin.y + CGFloat(y + QRLayout.quietZoneModules) * modulePoints,
                        width: modulePoints,
                        height: modulePoints
                    ))
                }
            }
        }
    }
}
