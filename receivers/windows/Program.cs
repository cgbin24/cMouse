// cMouse Windows 接收端入口：托盘程序（纯 Win32 P/Invoke，无 WinForms/WPF 依赖，体积小巧）。
// 事件注入为用户态 SendInput，无驱动、无需管理员权限。
// 协议与 macOS 接收端一致：TCP 8433，换行分隔 JSON（docs/protocol.md）。

using System.Runtime.InteropServices;

internal static class Program
{
    private const uint WM_TRAYICON = 0x0400 + 1;
    private const uint WM_COMMAND = 0x0111;
    private const uint NIM_ADD = 0x00, NIM_DELETE = 0x02;
    private const uint NIF_MESSAGE = 0x01, NIF_ICON = 0x02, NIF_TIP = 0x04;
    private const uint WM_LBUTTONDOWN = 0x0201;
    private const uint MF_STRING = 0x00, MF_SEPARATOR = 0x800, MF_GRAYED = 0x01;
    private const nint HWND_MESSAGE = (nint)(-3);
    private const nint IDI_APPLICATION = (nint)32512;

    private static nint _hwnd;
    private static nint _trayIcon;
    private static nint _menu;
    private static DB _db = null!;
    private static Server _server = null!;

    // 防止 GC 回收窗口过程委托
    private static readonly Program.WndProcDelegate WndProcRef = WndProc;

    [STAThread]
    private static void Main()
    {
        _db = new DB();
        _server = new Server(_db);
        _server.PeerChanged += _ => RebuildMenu();
        _server.Start();

        var hInst = Kernel32.GetModuleHandleW(null);
        var wc = new User32.WNDCLASSW
        {
            lpfnWndProc = Marshal.GetFunctionPointerForDelegate(WndProcRef),
            hInstance = hInst,
            lpszClassName = "cMouseTrayWnd"
        };
        User32.RegisterClassW(ref wc);
        _hwnd = User32.CreateWindowExW(0, "cMouseTrayWnd", "cMouse", 0, 0, 0, 0, 0,
            HWND_MESSAGE, nint.Zero, hInst, nint.Zero);

        _trayIcon = User32.LoadIconW(nint.Zero, IDI_APPLICATION);
        var nid = new User32.NOTIFYICONDATAW
        {
            cbSize = Marshal.SizeOf<User32.NOTIFYICONDATAW>(),
            hWnd = _hwnd,
            uID = 1,
            uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP,
            uCallbackMessage = WM_TRAYICON,
            hIcon = _trayIcon,
            szTip = "cMouse 触控板接收端"
        };
        User32.Shell_NotifyIconW(NIM_ADD, ref nid);

        RebuildMenu();

        while (User32.GetMessageW(out var msg, nint.Zero, 0, 0) > 0)
        {
            User32.TranslateMessage(ref msg);
            User32.DispatchMessageW(ref msg);
        }

        User32.Shell_NotifyIconW(NIM_DELETE, ref nid);
        _server.Stop();
    }

    private static void RebuildMenu()
    {
        if (_menu != nint.Zero) User32.DestroyMenu(_menu);
        _menu = User32.CreatePopupMenu();
        var state = _server.ConnectedName is { } n ? $"已连接：{n}" : "等待手机连接…";
        User32.AppendMenuW(_menu, MF_STRING | MF_GRAYED, (nint)1, state);
        User32.AppendMenuW(_menu, MF_STRING | MF_GRAYED, (nint)2, $"配对码：{_db.PairCode}");
        foreach (var ip in Net.LocalIPv4())
            User32.AppendMenuW(_menu, MF_STRING | MF_GRAYED, (nint)3, $"本机地址：{ip}:8433");
        User32.AppendMenuW(_menu, MF_SEPARATOR, nint.Zero, null);
        User32.AppendMenuW(_menu, MF_STRING, (nint)4, "清除本地数据…");
        User32.AppendMenuW(_menu, MF_STRING, (nint)5, "卸载清理说明…");
        User32.AppendMenuW(_menu, MF_SEPARATOR, nint.Zero, null);
        User32.AppendMenuW(_menu, MF_STRING, (nint)9, "退出");
    }

    private static void ShowMenu()
    {
        User32.GetCursorPos(out var p);
        User32.SetForegroundWindow(_hwnd); // 点击菜单外才能关闭
        User32.TrackPopupMenu(_menu, 0x0180, p.X, p.Y, 0, _hwnd, nint.Zero);
    }

    private static nint WndProc(nint hWnd, uint msg, nint wParam, nint lParam)
    {
        switch (msg)
        {
            case WM_TRAYICON when (uint)(lParam & 0xFFFF) == WM_LBUTTONDOWN:
                ShowMenu();
                return 0;
            case WM_COMMAND when wParam.ToInt64() == 4: // 清除本地数据
                ClearAllData();
                return 0;
            case WM_COMMAND when wParam.ToInt64() == 5: // 卸载清理说明
                ShowUninstallHelp();
                return 0;
            case WM_COMMAND when wParam.ToInt64() == 9: // 退出
                User32.PostQuitMessage(0);
                return 0;
            default:
                return User32.DefWindowProcW(hWnd, msg, wParam, lParam);
        }
    }

    private static void ClearAllData()
    {
        const uint MB_OKCANCEL = 0x01, MB_ICONWARNING = 0x30, MB_YESNO = 0x04, IDYES = 6;
        if (User32.MessageBoxW(_hwnd, "将删除配对码、已配对设备记录（仅存于本机 SQLite）。配对码会重新生成，所有手机需要重新配对。继续？",
                "清除所有本地数据", MB_OKCANCEL | MB_ICONWARNING) != IDYES)
            return;
        _server.DisconnectCurrent();
        _db.ClearAll();
        RebuildMenu();
    }

    private static void ShowUninstallHelp()
    {
        const uint MB_OK = 0x00, MB_ICONINFORMATION = 0x40;
        _ = User32.MessageBoxW(_hwnd,
            "卸载与彻底清理：\n\n" +
            "1. 托盘图标右键 → 退出\n" +
            "2. 删除 cMouse.exe 文件（本程序为单文件，无服务/驱动/自启动）\n" +
            "3. 删除文件夹 %APPDATA%\\cMouse\\（配对码与设备记录的 SQLite）\n" +
            "4. 可选：Windows 防火墙 → 入站规则 中移除 cMouse 条目（删除程序后即已失效）\n\n" +
            "以上完成后即无痕清除。",
            "卸载清理说明", MB_OK | MB_ICONINFORMATION);
    }

    internal delegate nint WndProcDelegate(nint hWnd, uint msg, nint wParam, nint lParam);
}

// ---------------- Win32 P/Invoke ----------------

internal static class Kernel32
{
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    internal static extern nint GetModuleHandleW(string? name);
}

internal static class User32
{
    [StructLayout(LayoutKind.Sequential)]
    internal struct POINT { public int X, Y; }

    [StructLayout(LayoutKind.Sequential)]
    internal struct MSG { public nint hwnd; public uint message; public nint wParam; public nint lParam; public uint time; public POINT pt; }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    internal struct WNDCLASSW
    {
        public uint style;
        public nint lpfnWndProc;
        public int cbClsExtra, cbWndExtra;
        public nint hInstance, hIcon, hCursor, hbrBackground;
        public string? lpszMenuName;
        public string lpszClassName;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    internal struct NOTIFYICONDATAW
    {
        public int cbSize;
        public nint hWnd;
        public uint uID;
        public uint uFlags;
        public uint uCallbackMessage;
        public nint hIcon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string szTip;
        public uint dwState;
        public uint dwStateMask;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string szInfo;
        public uint uVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)] public string szInfoTitle;
        public uint dwInfoFlags;
        public Guid guidItem;
        public nint hBalloonIcon;
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    internal static extern bool Shell_NotifyIconW(uint dwMessage, ref NOTIFYICONDATAW lpData);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    internal static extern ushort RegisterClassW(ref WNDCLASSW lpWndClass);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    internal static extern nint CreateWindowExW(uint exStyle, string className, string windowName,
        uint style, int x, int y, int w, int h, nint parent, nint menu, nint instance, nint param);

    [DllImport("user32.dll")]
    internal static extern nint DefWindowProcW(nint hWnd, uint msg, nint wParam, nint lParam);

    [DllImport("user32.dll")]
    internal static extern int GetMessageW(out MSG msg, nint hWnd, uint min, uint max);

    [DllImport("user32.dll")]
    internal static extern bool TranslateMessage(ref MSG msg);

    [DllImport("user32.dll")]
    internal static extern nint DispatchMessageW(ref MSG msg);

    [DllImport("user32.dll")]
    internal static extern void PostQuitMessage(int code);

    [DllImport("user32.dll")]
    internal static extern nint LoadIconW(nint hInstance, nint lpIconName);

    [DllImport("user32.dll")]
    internal static extern nint CreatePopupMenu();

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    internal static extern bool AppendMenuW(nint menu, uint flags, nint id, string? text);

    [DllImport("user32.dll")]
    internal static extern bool TrackPopupMenu(nint menu, uint flags, int x, int y, int reserved, nint hwnd, nint rect);

    [DllImport("user32.dll")]
    internal static extern bool GetCursorPos(out POINT p);

    [DllImport("user32.dll")]
    internal static extern bool DestroyMenu(nint menu);

    [DllImport("user32.dll")]
    internal static extern bool SetForegroundWindow(nint hWnd);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    internal static extern int MessageBoxW(nint hWnd, string text, string caption, uint type);
}
