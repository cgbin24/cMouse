// cMouse macOS 接收端入口：纯菜单栏应用（.accessory，不出现在 Dock）
import AppKit

let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
