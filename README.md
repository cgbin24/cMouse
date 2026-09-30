# cMouse — 把手机变成电脑触控板

手机通过**蓝牙免驱直连**（Android）或 **Wi-Fi 局域网接收端**（iPhone）控制电脑：
光标移动、单击、双击、滚动、捏合缩放、三指多任务、虚拟键盘，一切开箱即用。

> 技术决策与平台差异分析见 [docs/feasibility.md](docs/feasibility.md)，
> 传输协议见 [docs/protocol.md](docs/protocol.md)。

## 核心结论（无驱动方案评估）

| 路径 | Android → 电脑 | iPhone → 电脑 | 电脑端安装 |
|------|----------------|---------------|------------|
| A. 蓝牙 HID 直连 | ✅ 零安装（`BluetoothHidDevice`，Android 9+） | ❌ 苹果无此公开 API（硬性平台限制） | 无 |
| B. Wi-Fi 接收端 | ✅ 可选增强 | ✅ 唯一路径 | 单文件小程序（1–10 MB，无驱动） |

- 全程**无内核驱动**：macOS 注入用 CGEventPost（用户态，仅需辅助功能授权）；
  Windows 注入用 SendInput（用户态，无需管理员）。
- 手势在手机端本地识别，翻译为标准鼠标/键盘语义（双指滚动→滚轮、捏合→Ctrl+滚轮、
  三指滑动→可配置快捷键），因此不依赖系统触控板私有协议。
- 数据全部本地 SQLite，无云服务、不上传。

## 目录结构

```
cMouse/
├── docs/
│   ├── feasibility.md        # 无驱动方案可行性评估（先读这个）
│   └── protocol.md           # Wi-Fi 模式传输协议
├── android/                  # Android 客户端（模式 A + B，Kotlin + Compose）
│   └── app/src/main/java/com/cmouse/app/
│       ├── hid/              # 蓝牙 HID 设备角色：报告描述符 / 设备管理 / 前台服务
│       ├── input/            # 手势状态机 + 输入路由
│       ├── transport/        # Wi-Fi 模式 TCP 客户端
│       ├── data/             # SQLite 存储（设置 / 手势映射 / 配对设备）
│       └── ui/               # macOS 风格界面：触控板 / 键盘 / 设置
├── ios/                      # iPhone 客户端（模式 B，SwiftUI，源码 + 工程创建指引）
├── receivers/
│   ├── macos/                # macOS 接收端：Swift 菜单栏应用（已编译验证）
│   └── windows/              # Windows 接收端：C# 托盘程序（纯 Win32 P/Invoke）
└── README.md
```

## 使用方法

### Android → 任意电脑（推荐，电脑端零安装）

1. 安装并打开 cMouse（见下文构建），授予蓝牙权限。
2. 保持默认"蓝牙直连"模式，点 **开始连接**（手机注册为蓝牙键鼠并进入可被发现状态）。
3. 在电脑 **蓝牙设置** 中找到 `cMouse Trackpad` 并配对。完成。
   - macOS：系统设置 → 蓝牙
   - Windows：设置 → 蓝牙和其他设备 → 添加设备
4. 手势见触控板页提示；键盘页可随时呼出虚拟键盘。

### iPhone → 电脑（Wi-Fi 模式）

1. 电脑端运行接收端（见下文），记下菜单栏/托盘显示的 **本机地址** 与 **配对码**。
2. 手机与电脑连同一 Wi-Fi；iOS 端设置页输入 IP + 配对码连接。
3. macOS 首次使用需在 **系统设置 → 隐私与安全性 → 辅助功能** 勾选接收端（一次性授权，
   用户态，非驱动）。

## 最低系统版本（低版本兼容）

| 组件 | 最低版本 | 说明 |
|------|----------|------|
| Android 客户端 · Wi-Fi 模式 | Android 6.0 (API 23) | 覆盖 OPPO R9st 等老机型 |
| Android 客户端 · 蓝牙免安装模式 | Android 9.0 (API 28) | `BluetoothHidDevice` 系统要求；App 内按版本门控，低版本自动提示改用 Wi-Fi 模式 |
| iOS 客户端 | iOS 15.0 | SwiftUI 生命周期框架的下限；源码已按 iOS 15 目标通过编译检查（iPhone 6s 及以后机型均可） |
| macOS 接收端 | macOS 11 Big Sur | Universal 2 二进制（Intel + Apple Silicon） |
| Windows 接收端 | Windows 10 | .NET 8 运行时的最低要求；如需支持 Win7 需另出 .NET Framework 4.8 变体 |

## 安装包（当前状态）

| 安装包 | 状态 | 获取方式 |
|--------|------|----------|
| **macOS 接收端 .pkg** | ✅ 已打包，双击即装（97 KB，装到 /Applications） | `receivers/macos/cMouse-Receiver-macOS-1.0.0.pkg` |
| **Android APK** | 需云端/本机构建后即装 | 见下文"获取 APK" |
| **Windows 接收端 exe** | 需云端/本机构建后即用 | GitHub Actions 或 `dotnet publish` |
| **iOS** | iOS 无侧载安装包（苹果限制），需 Xcode 免费签名真机运行 | 见 [ios/README.md](ios/README.md) |

### 获取 APK（三选一）

> 注意：构建产物（Artifacts）**不在仓库目录里，也不会创建分支**——它们挂在 Actions 运行页面底部
> 的 Artifacts 栏，需登录 GitHub 下载，保留 90 天。想要永久、显眼的下载入口，用 Releases（见方式 1）。

1. **Releases（推荐，永久保存）**：推送版本标签，构建成功后 APK 与 Windows exe 自动出现在
   仓库首页的 Releases 栏：
   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```
2. **Actions Artifacts**：仓库页 Actions → 点进某次构建运行 → 页面底部 Artifacts → 下载
   `cMouse-debug-apk`（zip 内是 app-debug.apk，Debug 签名可直接安装）。
3. **本机构建**：装 Android Studio 打开 `android/` 目录 → Build → Build APK(s)；
   或自行装好 JDK 17 / Gradle / Android SDK 后执行 `gradle -p android assembleDebug`
   （`scripts/setup-android-toolchain.sh` 仅供参考）。

### 构建接收端（如需自行构建）

```bash
# macOS（系统自带 swiftc，无需安装任何东西）
cd receivers/macos
swiftc -O -swift-version 5 -target arm64-apple-macos11.0 DB.swift Injector.swift Server.swift AppDelegate.swift main.swift -o /tmp/cmouse-arm64
swiftc -O -swift-version 5 -target x86_64-apple-macos11.0 DB.swift Injector.swift Server.swift AppDelegate.swift main.swift -o /tmp/cmouse-x86_64
lipo -create /tmp/cmouse-arm64 /tmp/cmouse-x86_64 -output payload/cMouse.app/Contents/MacOS/cmouse-receiver
codesign --force -s - payload/cMouse.app
pkgbuild --root payload --identifier com.cmouse.receiver --version 1.0.0 --install-location /Applications cMouse-Receiver.pkg

# Windows（需 .NET 8 SDK 或用 GitHub Actions）
cd receivers/windows
dotnet publish -c Release -r win-x64 --self-contained -p:PublishSingleFile=true
# 产物约 65 MB；若追求体积可再加 -p:PublishTrimmed=true（与 SQLite 组合需自行验证运行时）
```

### iOS 验证状态

源码已按 **iOS 15.0** 目标通过系统 swiftc 的 typecheck 与完整模拟器链接
（`xcrun -sdk iphonesimulator swiftc -target arm64-apple-ios15.0-simulator`，产物 353 KB）。
真机安装必须经 Xcode 签名（免费 Apple ID 即可，7 天有效期；TestFlight 可长期），这是苹果对非企业开发者侧载的唯一途径。

## 已验证 / 未验证事项（如实说明）

| 组件 | 验证状态 |
|------|----------|
| macOS 接收端 | ✅ Universal 2（macOS 11+）编译通过，协议冒烟测试通过（配对码双路径）；**.pkg 安装包已产出** |
| iOS 客户端源码 | ✅ 按 iOS 15 目标通过 typecheck + 模拟器完整链接；真机安装需 Xcode 免费签名 |
| Android 客户端 | 源码交付（minSdk 24，低版本门控完成），本机未装工具链未编译；GitHub Actions 一键出 APK |
| Windows 接收端 | 源码交付，GitHub Actions 一键出 exe；本机未编译（API 均为标准 Win32/Sqlite） |

## 常见问题排查

### 安装 APK 时提示"安装包已损坏 / 建议使用应用商店下载"

这通常是**厂商"纯净模式"拦截非商店安装**的提示文案，不是文件真的损坏：

- 优先用 Releases 页直链下载原始 APK（无 zip 包装，无需登录）；
- 华为/荣耀：设置 → 系统与更新 → **纯净模式** → 关闭；
- 小米：设置 → 特色功能 → **纯净模式** → 关闭；或安装弹窗选"仍要安装"；
- OPPO/一加/realme：设置 → 安全 → 允许安装外部来源应用（对浏览器/文件管理器放行）；
- vivo：设置 → 安全 → 更多安全设置 → 关闭"应用安装拦截"；
- 仍怀疑文件损坏时对比 SHA-256：Release 附带 `SHA256SUMS.txt`，电脑端 `shasum -a 256 cMouse-debug.apk`。

### 点击"开始连接"闪退 / 无任何反应

v1.0.3 起内置黑匣子：任何闪退的完整堆栈会写入本地并在**下次启动时弹窗显示**（含设备型号与系统版本）。
如遇到闪退，重新打开 App，把弹窗内容截图发给我们即可精确定位。
同时启动链路已全部加异常保护——大部分异常现在会显示为触控板页/设置页的错误文字而非闪退。

### 蓝牙模式显示"未注册"或注册失败

1. 点"开始连接"后**按顺序完成三个弹窗**：附近设备（蓝牙）权限 → 通知权限 → "允许对其他蓝牙设备可见"（300 秒）；
2. 确认手机蓝牙已开启，然后到电脑蓝牙设置中搜索 **cMouse Trackpad** 并配对（macOS 系统设置→蓝牙；Windows 设置→蓝牙和其他设备→添加设备）；
3. 现在失败原因会直接显示在触控板页与设置页：
   - "缺少蓝牙权限" → 到系统设置授予 cMouse"附近设备"权限；
   - "HID 注册失败…" → 说明该系统限制了虚拟输入设备（HarmonyOS/EMUI、企业管控 ROM 较常见），请改用 **Wi-Fi 接收端**模式（电脑端运行 cMouse Receiver，功能完全一致）；
4. 配对成功后状态会变为"已配对：设备名"，即可直接使用触控板与键盘。

## 隐私与安全

- 电脑端不开放任何写文件/执行命令能力，只接受输入事件；配对码 + 单客户端策略。
- 全部数据（设置、手势映射、配对设备）存本地 SQLite：Android `cmouse.db`、
  macOS `~/Library/Application Support/cMouse/cmouse.db`、Windows `%APPDATA%\cMouse\cmouse.db`。
- 无任何网络上报。
