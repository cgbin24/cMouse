// 主界面：触控板 / 键盘 / 设置 三个标签页，macOS 设计语言（#F5F5F7 背景 + Apple 蓝）。
import SwiftUI

struct RootView: View {
    @StateObject private var client = LanClient()
    @AppStorage("lan_host") private var host = ""
    @AppStorage("lan_port") private var port = "8433"
    @AppStorage("lan_code") private var code = ""

    var body: some View {
        TabView {
            TrackpadScreen(client: client)
                .tabItem { Label("触控板", systemImage: "hand.draw") }
            KeyboardScreen(client: client)
                .tabItem { Label("键盘", systemImage: "keyboard") }
            SettingsScreen(client: client, host: $host, port: $port, code: $code)
                .tabItem { Label("设置", systemImage: "gearshape") }
        }
        .tint(Color(red: 0, green: 0.478, blue: 1))
    }
}

// MARK: - 触控板页

struct TrackpadScreen: View {
    @ObservedObject var client: LanClient

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 8) {
                Circle()
                    .fill(client.isReady ? Color.green : Color.secondary.opacity(0.5))
                    .frame(width: 10, height: 10)
                Text(client.isReady ? "已连接" : client.message.isEmpty ? client.state.rawValue : client.message)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))

            ZStack {
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.white)
                    .shadow(color: .black.opacity(0.06), radius: 4, y: 1)
                Text("单指移动 · 轻点单击 · 双指点按双击\n双指滑动滚动 · 双指轻点右键 · 捏合缩放")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
                    .multilineTextAlignment(.center)
                TrackpadView(client: client)
            }
            .frame(maxHeight: .infinity)

            HStack(spacing: 10) {
                PillButton("左键") { client.click("left") }
                PillButton("右键") { client.click("right") }
            }
        }
        .padding(16)
        .background(Color(red: 0.961, green: 0.961, blue: 0.969).ignoresSafeArea())
    }
}

struct PillButton: View {
    let title: String
    let action: () -> Void

    init(_ title: String, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
        }
        .buttonStyle(.bordered)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.white))
    }
}

// MARK: - 键盘页

struct KeyboardScreen: View {
    @ObservedObject var client: LanClient
    @State private var sticky: Set<String> = []
    @State private var textBuf = ""

    private let rows = [
        Array("1234567890"),
        Array("qwertyuiop"),
        Array("asdfghjkl")
    ]

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                TextField("输入文本（直传，支持中文）", text: $textBuf)
                    .textFieldStyle(.roundedBorder)
                Button("发送") {
                    if !textBuf.isEmpty { client.text(textBuf); textBuf = "" }
                }
            }
            .padding(.horizontal, 12)

            HStack(spacing: 6) {
                KeyCap("esc", sticky: false) { client.key("esc") }
                KeyCap("tab", sticky: false) { client.key("tab") }
                KeyCap("←", sticky: false) { client.key("left") }
                KeyCap("↑", sticky: false) { client.key("up") }
                KeyCap("↓", sticky: false) { client.key("down") }
                KeyCap("→", sticky: false) { client.key("right") }
                KeyCap("⌫", sticky: false) { client.key("backspace") }
                KeyCap("⏎", sticky: false) { client.key("enter") }
            }

            ForEach(rows, id: \.self) { row in
                HStack(spacing: 6) {
                    ForEach(row, id: \.self) { ch in
                        KeyCap(String(ch), sticky: sticky.contains("shift")) { press(ch) }
                    }
                }
            }

            HStack(spacing: 6) {
                KeyCap("⇧", sticky: sticky.contains("shift")) { toggle("shift") }
                KeyCap("z", sticky: false) { press("z") }
                KeyCap("x", sticky: false) { press("x") }
                KeyCap("c", sticky: false) { press("c") }
                KeyCap("v", sticky: false) { press("v") }
                KeyCap("⌃", sticky: sticky.contains("ctrl")) { toggle("ctrl") }
                KeyCap("⌥", sticky: sticky.contains("alt")) { toggle("alt") }
                KeyCap("⌘", sticky: sticky.contains("cmd")) { toggle("cmd") }
                KeyCap("空格", sticky: false) { press(" ") }
            }
        }
        .padding(12)
        .background(Color(red: 0.961, green: 0.961, blue: 0.969).ignoresSafeArea())
    }

    private func toggle(_ m: String) {
        if sticky.contains(m) { sticky.remove(m) } else { sticky.insert(m) }
    }

    private func press(_ ch: Character) {
        if sticky.isEmpty {
            client.text(String(ch))
        } else if sticky.contains("cmd") || sticky.contains("ctrl") {
            client.key(sticky.joined(separator: "+") + "+" + ch.lowercased())
        } else {
            client.text(String(ch))
        }
        if sticky.contains("shift") { sticky.remove("shift") }
    }
}

struct KeyCap: View {
    let label: String
    let sticky: Bool
    let action: () -> Void

    init(_ label: String, sticky: Bool = false, action: @escaping () -> Void) {
        self.label = label
        self.sticky = sticky
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(.footnote, design: .rounded))
                .frame(maxWidth: .infinity, minHeight: 40)
        }
        .buttonStyle(.plain)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(sticky ? Color(red: 0, green: 0.478, blue: 1) : Color.white)
        )
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.25)))
        .foregroundStyle(sticky ? .white : .primary)
    }
}

// MARK: - 设置页

struct SettingsScreen: View {
    @ObservedObject var client: LanClient
    @Binding var host: String
    @Binding var port: String
    @Binding var code: String

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("设置").font(.title2.bold())

                Card("连接") {
                    TextField("电脑 IP（接收端菜单栏可见）", text: $host)
                        .textFieldStyle(.roundedBorder)
                        .keyboardType(.decimalPad)
                    HStack {
                        TextField("端口", text: $port).keyboardType(.numberPad)
                        TextField("配对码", text: $code).keyboardType(.numberPad)
                    }
                    HStack {
                        Button("连接") {
                            client.connect(host: host, port: UInt16(port) ?? 8433,
                                           code: code, deviceName: UIDevice.current.name)
                        }
                        .buttonStyle(.borderedProminent)
                        Button("断开") { client.disconnect() }
                            .buttonStyle(.bordered)
                        Text(client.isReady ? "已连接" : client.message.isEmpty ? client.state.rawValue : client.message)
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                }

                Card("数据与隐私") {
                    Text("所有数据仅保存在本机与电脑端 SQLite，不采集、不上传。")
                        .font(.footnote).foregroundStyle(.secondary)
                }

                Card("说明") {
                    Text("iPhone 无法模拟蓝牙 HID 设备（苹果平台限制），因此使用 Wi-Fi 模式：手机与电脑在同一局域网，电脑端运行 cMouse Receiver（无驱动、单文件）。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .padding(16)
        }
        .background(Color(red: 0.961, green: 0.961, blue: 0.969).ignoresSafeArea())
    }
}

struct Card<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.headline).foregroundStyle(Color(red: 0, green: 0.478, blue: 1))
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
    }
}
