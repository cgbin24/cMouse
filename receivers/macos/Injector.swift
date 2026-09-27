import CoreGraphics
import AppKit

/// 用户态事件注入：CGEventPost（Quartz Event Services）。
/// 无驱动、无内核组件；仅需系统"辅助功能"授权（一次性用户勾选）。
enum Injector {

    static func isAccessibilityGranted() -> Bool {
        AXIsProcessTrusted()
    }

    // MARK: - 光标

    static func move(dx: CGFloat, dy: CGFloat) {
        guard let current = CGEvent(source: nil)?.location else { return }
        let screen = NSScreen.screens.first?.frame ?? CGRect(x: 0, y: 0, width: 1920, height: 1080)
        // 多显示器坐标空间以主屏左上为原点；简单钳制防止光标丢失在负空间
        let x = min(max(current.x + dx, screen.minX), screen.maxX)
        let y = min(max(current.y - dy, screen.minY), screen.maxY) // 屏幕坐标系 y 轴向下
        guard let e = CGEvent(mouseEventSource: nil, mouseType: .mouseMoved,
                              mouseCursorPosition: CGPoint(x: x, y: y), mouseButton: .left) else { return }
        e.post(tap: .cghidEventTap)
    }

    static func absolute(x: CGFloat, y: CGFloat) {
        let w = CGFloat(CGDisplayPixelsWide(CGMainDisplayID()))
        let h = CGFloat(CGDisplayPixelsHigh(CGMainDisplayID()))
        let p = CGPoint(x: w * x, y: h * (1 - y))
        guard let e = CGEvent(mouseEventSource: nil, mouseType: .mouseMoved,
                              mouseCursorPosition: p, mouseButton: .left) else { return }
        e.post(tap: .cghidEventTap)
    }

    // MARK: - 按键

    static func button(_ name: String, down: Bool, clicks: Int = 1) {
        let type: CGEventType
        let mb: CGMouseButton
        switch name {
        case "right": mb = .right
            type = down ? .rightMouseDown : .rightMouseUp
        case "middle": mb = .center
            type = down ? .otherMouseDown : .otherMouseUp
        default: mb = .left
            type = down ? .leftMouseDown : .leftMouseUp
        }
        guard let loc = CGEvent(source: nil)?.location,
              let e = CGEvent(mouseEventSource: nil, mouseType: type,
                              mouseCursorPosition: loc, mouseButton: mb) else { return }
        if clicks > 1 { e.setIntegerValueField(.mouseEventClickState, value: Int64(clicks)) }
        e.post(tap: .cghidEventTap)
    }

    static func click(_ name: String, double: Bool) {
        if double {
            button(name, down: true, clicks: 1); button(name, down: false, clicks: 1)
            usleep(30_000)
            button(name, down: true, clicks: 2); button(name, down: false, clicks: 2)
        } else {
            button(name, down: true, clicks: 1)
            button(name, down: false, clicks: 1)
        }
    }

    // MARK: - 滚动（含捏合缩放）

    /// dx/dy：手机端语义（+dy 内容向下滚）。macOS wheel1 正值=向上滚。
    static func scroll(dx: Int, dy: Int) {
        guard let e = CGEvent(scrollWheelEvent2Source: nil, units: .line, wheelCount: 2,
                              wheel1: -Int32(dy), wheel2: Int32(dx), wheel3: 0) else { return }
        e.post(tap: .cghidEventTap)
    }

    /// d>0 放大。以 Ctrl+滚轮 注入，绝大多数应用中等效于捏合。
    static func zoom(delta d: Int) {
        guard let e = CGEvent(scrollWheelEvent2Source: nil, units: .line, wheelCount: 1,
                              wheel1: Int32(d), wheel2: 0, wheel3: 0) else { return }
        e.flags = .maskControl
        e.post(tap: .cghidEventTap)
    }

    // MARK: - 键盘

    private static let keyCodes: [String: CGKeyCode] = [
        "a": 0, "s": 1, "d": 2, "f": 3, "h": 4, "g": 5, "z": 6, "x": 7, "c": 8, "v": 9,
        "b": 11, "q": 12, "w": 13, "e": 14, "r": 15, "y": 16, "t": 17,
        "1": 18, "2": 19, "3": 20, "4": 21, "6": 22, "5": 23, "=": 24, "9": 25, "7": 26,
        "-": 27, "8": 28, "0": 29, "]": 30, "o": 31, "u": 32, "[": 33, "i": 34, "p": 35,
        "l": 37, "j": 38, "'": 39, "k": 40, ";": 41, "\\": 42, ",": 43, "/": 44, "n": 45, "m": 46, ".": 47,
        "tab": 48, "space": 49, "`": 50, "delete": 51, "esc": 53,
        "enter": 36, "backspace": 51,
        "home": 115, "end": 119, "pageup": 116, "pagedown": 121,
        "left": 123, "right": 124, "down": 125, "up": 126,
        "f1": 122, "f2": 120, "f3": 99, "f4": 118, "f5": 96, "f6": 97,
        "f7": 98, "f8": 100, "f9": 101, "f10": 109, "f11": 103, "f12": 111
    ]

    private static func flags(_ mods: [String]) -> CGEventFlags {
        var f: CGEventFlags = []
        for m in mods {
            switch m {
            case "ctrl", "control": f.insert(.maskControl)
            case "alt", "option": f.insert(.maskAlternate)
            case "shift": f.insert(.maskShift)
            case "cmd", "gui", "win", "meta": f.insert(.maskCommand)
            default: break
            }
        }
        return f
    }

    /// 组合键，如 "cmd+ctrl+q" / "ctrl+up"。
    static func combo(_ s: String) {
        let parts = s.lowercased().split(separator: "+").map(String.init)
        guard let keyName = parts.last, let code = keyCodes[keyName] else { return }
        let f = flags(Array(parts.dropLast()))
        guard let down = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: true) else { return }
        down.flags = f
        down.post(tap: .cghidEventTap)
        guard let up = CGEvent(keyboardEventSource: nil, virtualKey: code, keyDown: false) else { return }
        up.flags = f
        up.post(tap: .cghidEventTap)
    }

    /// Unicode 文本直传（支持中文整句）。逐字符注入。
    static func text(_ s: String) {
        for ch in s {
            guard let down = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: true),
                  let up = CGEvent(keyboardEventSource: nil, virtualKey: 0, keyDown: false) else { continue }
            var units = Array(String(ch).utf16)
            down.keyboardSetUnicodeString(stringLength: units.count, unicodeString: &units)
            up.keyboardSetUnicodeString(stringLength: units.count, unicodeString: &units)
            down.post(tap: .cghidEventTap)
            up.post(tap: .cghidEventTap)
        }
    }
}
