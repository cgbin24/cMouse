// cMouse iOS 客户端入口（Wi-Fi 接收端模式；iPhone 无蓝牙 HID 设备角色 API，见 docs/feasibility.md）
import SwiftUI

@main
struct CMouseApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.light)
        }
    }
}
