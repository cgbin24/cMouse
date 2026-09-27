# iOS 客户端构建指引（约 5 分钟）

iPhone 没有蓝牙 HID 设备角色的公开 API（苹果平台限制，无法零安装），
因此 iOS 端走 **Wi-Fi 模式**：与电脑端 cMouse Receiver 配合使用。

## 步骤

1. 用 Xcode 打开：**File → New → Project → iOS → App**
   - Product Name：`cMouse`
   - Interface：**SwiftUI**，Language：**Swift**
2. 删除模板生成的 `ContentView.swift` 与 `cMouseApp.swift`。
3. 把本目录 `Sources/` 下的 4 个 Swift 文件拖入工程（勾选 Copy items if needed）：
   - `CMouseApp.swift`（入口）
   - `RootView.swift`（三个页面：触控板 / 键盘 / 设置）
   - `TrackpadView.swift`（UIKit 手势识别）
   - `LanClient.swift`（Network.framework TCP 客户端）
4. 签名：Target → Signing & Capabilities → 选择你的开发者团队（个人免费 Apple ID 即可）。
5. 本地网络权限：首次连接时系统会弹出"本地网络"授权，点允许
   （或在 Info.plist 添加 `NSLocalNetworkUsageDescription`，值如"用于连接电脑端触控板接收端"）。
6. Run 到真机（模拟器无法测试触摸手势）。

## 使用

1. 电脑端启动接收端，记下配对码与本机 IP。
2. 手机与电脑连同一 Wi-Fi → 设置页填 IP / 端口(8433) / 配对码 → 连接。
3. 触控板页与键盘页即可控制电脑。

## 手势

| 手势 | 作用 |
|------|------|
| 单指滑动 / 轻点 / 双击 | 移动 / 左键 / 双击 |
| 双指滑动 / 轻点 / 捏合 | 滚动 / 右键 / 缩放 |
| 长按后拖动 | 拖拽 |
| 三指滑动 | 调度中心 / 任务视图等（映射表与 Android 端一致） |
