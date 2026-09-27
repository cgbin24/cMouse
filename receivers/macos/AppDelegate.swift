import AppKit
import Network

/// 菜单栏应用（无 Dock 图标、无窗口）：显示状态 / 配对码 / 本机 IP / 授权引导。
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private var server: Server!
    private var db: DB!
    private var peerName: String?

    func applicationDidFinishLaunching(_ notification: Notification) {
        db = DB()
        server = Server(db: db)
        server.onPeerChanged = { [weak self] name in
            self?.peerName = name
            self?.refreshMenu()
        }
        server.start()

        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        statusItem.button?.title = "⌖"
        buildMenu()

        if !Injector.isAccessibilityGranted() {
            promptAccessibility()
        }
    }

    func applicationWillTerminate(_ notification: Notification) {
        server?.stop()
    }

    // MARK: - 菜单

    private func buildMenu() {
        let menu = NSMenu()

        let state = NSMenuItem(
            title: peerName.map { "已连接：\($0)" } ?? "等待手机连接…",
            action: nil, keyEquivalent: "")
        state.isEnabled = false
        menu.addItem(state)

        let code = NSMenuItem(title: "配对码：\(db.pairCode)", action: nil, keyEquivalent: "")
        code.isEnabled = false
        menu.addItem(code)

        for ip in localIPv4() {
            let item = NSMenuItem(title: "本机地址：\(ip):8433", action: nil, keyEquivalent: "")
            item.isEnabled = false
            menu.addItem(item)
        }

        menu.addItem(.separator())

        let granted = Injector.isAccessibilityGranted()
        let ax = NSMenuItem(
            title: granted ? "辅助功能：已授权" : "辅助功能：未授权（点击去授权）",
            action: granted ? nil : #selector(openAccessibility),
            keyEquivalent: "")
        menu.addItem(ax)

        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "退出 cMouse", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))

        menu.autoenablesItems = false
        statusItem.menu = menu
    }

    private func refreshMenu() {
        buildMenu()
    }

    @objc private func openAccessibility() {
        let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")!
        NSWorkspace.shared.open(url)
    }

    private func promptAccessibility() {
        let alert = NSAlert()
        alert.messageText = "cMouse 需要辅助功能授权"
        alert.informativeText = "为注入鼠标/键盘事件（用户态，无驱动），请在\"系统设置 → 隐私与安全性 → 辅助功能\"中勾选 cMouse。此授权仅用于控制本机，不访问任何数据。"
        alert.addButton(withTitle: "去授权")
        alert.addButton(withTitle: "稍后")
        if alert.runModal() == .alertFirstButtonReturn {
            openAccessibility()
        }
    }

    // MARK: - 网络

    private func localIPv4() -> [String] {
        var result: [String] = []
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0 else { return result }
        defer { freeifaddrs(ifaddr) }
        var ptr = ifaddr
        while let p = ptr {
            let intf = p.pointee
            if let sa = intf.ifa_addr, (sa.pointee.sa_family == UInt8(AF_INET)),
               (intf.ifa_flags & UInt32(IFF_LOOPBACK)) == 0 {
                var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                if getnameinfo(sa, socklen_t(sa.pointee.sa_len), &host, socklen_t(host.count),
                               nil, 0, NI_NUMERICHOST) == 0 {
                    let ip = String(cString: host)
                    result.append(ip)
                }
            }
            ptr = intf.ifa_next
        }
        return result
    }
}
