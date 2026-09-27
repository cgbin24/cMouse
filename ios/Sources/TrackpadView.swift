// 触控板表面：UIKit 手势识别器 → LanClient。macOS 风格浅色卡片。
import SwiftUI
import UIKit

struct TrackpadView: UIViewRepresentable {
    let client: LanClient

    func makeUIView(context: Context) -> TrackpadUIView { TrackpadUIView(client: client) }
    func updateUIView(_ uiView: TrackpadUIView, context: Context) {}
}

/// 手势 → 事件：
///  单指拖动=移动；单指点按=左键；双指点按=双击
///  双指拖动=滚动；双指点按=右键；双指捏合=缩放
///  长按=拖拽（按住左键移动）
///  三指滑动=多任务（接收端映射快捷键）
final class TrackpadUIView: UIView, UIGestureRecognizerDelegate {

    private let client: LanClient
    private let slop: CGFloat = 8

    // 双指滚动累计
    private var scrollLast: CGPoint = .zero
    // 捏合累计（每 8% 记 1 格）
    private var zoomAcc: CGFloat = 0
    // 三指
    private var threeInit: CGPoint = .zero
    // 长按拖拽
    private var dragging = false

    init(client: LanClient) {
        self.client = client
        super.init(frame: .zero)
        backgroundColor = .clear
        isMultipleTouchEnabled = true

        let pan1 = UIPanGestureRecognizer(target: self, action: #selector(oneFingerPan(_:)))
        pan1.minimumNumberOfTouches = 1
        pan1.maximumNumberOfTouches = 1
        pan1.delegate = self
        addGestureRecognizer(pan1)

        let pan2 = UIPanGestureRecognizer(target: self, action: #selector(twoFingerPan(_:)))
        pan2.minimumNumberOfTouches = 2
        pan2.maximumNumberOfTouches = 2
        pan2.delegate = self
        addGestureRecognizer(pan2)

        let pan3 = UIPanGestureRecognizer(target: self, action: #selector(threeFingerPan(_:)))
        pan3.minimumNumberOfTouches = 3
        pan3.maximumNumberOfTouches = 3
        pan3.delegate = self
        addGestureRecognizer(pan3)

        let tap1 = UITapGestureRecognizer(target: self, action: #selector(tap1(_:)))
        tap1.numberOfTapsRequired = 1
        tap1.numberOfTouchesRequired = 1
        addGestureRecognizer(tap1)

        let tap2 = UITapGestureRecognizer(target: self, action: #selector(tap2(_:)))
        tap2.numberOfTapsRequired = 2
        tap2.numberOfTouchesRequired = 1
        addGestureRecognizer(tap2)

        let tapRight = UITapGestureRecognizer(target: self, action: #selector(tapRight(_:)))
        tapRight.numberOfTapsRequired = 1
        tapRight.numberOfTouchesRequired = 2
        addGestureRecognizer(tapRight)

        let pinch = UIPinchGestureRecognizer(target: self, action: #selector(pinch(_:)))
        pinch.delegate = self
        addGestureRecognizer(pinch)

        let long = UILongPressGestureRecognizer(target: self, action: #selector(longPress(_:)))
        long.minimumPressDuration = 0.5
        long.delegate = self
        addGestureRecognizer(long)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) 未支持") }

    // 允许多个手势同时识别（滚动+捏合等）
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

    // MARK: - 手势处理

    @objc private func oneFingerPan(_ g: UIPanGestureRecognizer) {
        let t = g.translation(in: self)
        switch g.state {
        case .changed:
            if abs(t.x) > slop || abs(t.y) > slop || dragging == false {
                client.move(dx: Double(t.x), dy: Double(t.y))
                g.setTranslation(.zero, in: self)
            }
        case .ended:
            dragging = false
        default: break
        }
    }

    @objc private func twoFingerPan(_ g: UIPanGestureRecognizer) {
        let t = g.translation(in: self)
        switch g.state {
        case .began: scrollLast = .zero
        case .changed:
            client.scroll(dx: Double(t.x - scrollLast.x), dy: Double(t.y - scrollLast.y))
            scrollLast = t
        case .ended, .cancelled:
            scrollLast = .zero
        default: break
        }
    }

    @objc private func threeFingerPan(_ g: UIPanGestureRecognizer) {
        let t = g.translation(in: self)
        switch g.state {
        case .began: threeInit = t
        case .ended:
            let dx = t.x - threeInit.x, dy = t.y - threeInit.y
            guard hypot(dx, dy) > 40 else { return }
            let dir: String
            if abs(dy) > abs(dx) { dir = dy < 0 ? "up" : "down" }
            else { dir = dx < 0 ? "left" : "right" }
            // 三指滑动交由接收端/手机端映射（与 Android 端一致的默认动作）
            let combo = ["up": "ctrl+up", "down": "win+d",
                         "left": "ctrl+left", "right": "ctrl+right"][dir] ?? ""
            if !combo.isEmpty { client.key(combo) }
        default: break
        }
    }

    @objc private func tap1(_ g: UITapGestureRecognizer) {
        client.click("left")
    }

    @objc private func tap2(_ g: UITapGestureRecognizer) {
        client.click("left", double: true)
    }

    @objc private func tapRight(_ g: UITapGestureRecognizer) {
        client.click("right")
    }

    @objc private func pinch(_ g: UIPinchGestureRecognizer) {
        switch g.state {
        case .began: zoomAcc = 0
        case .changed:
            zoomAcc += (g.scale - 1)
            g.scale = 1
            while abs(zoomAcc) >= 0.08 {
                client.zoom(zoomAcc > 0 ? 1 : -1)
                zoomAcc -= zoomAcc > 0 ? 0.08 : -0.08
            }
        default: break
        }
    }

    @objc private func longPress(_ g: UILongPressGestureRecognizer) {
        switch g.state {
        case .began:
            dragging = true
            client.button("left", down: true)
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        case .ended, .cancelled:
            if dragging { client.button("left", down: false) }
            dragging = false
        default: break
        }
    }
}
