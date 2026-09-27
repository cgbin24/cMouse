// 用户态事件注入：SendInput（Win32 标准 API，无驱动、无需管理员权限）。

using System.Runtime.InteropServices;

internal static class Injector
{
    private const uint INPUT_MOUSE = 0, INPUT_KEYBOARD = 1;
    private const uint MOUSEEVENTF_MOVE = 0x0001, MOUSEEVENTF_LEFTDOWN = 0x0002, MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008, MOUSEEVENTF_RIGHTUP = 0x0010;
    private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020, MOUSEEVENTF_MIDDLEUP = 0x0040;
    private const uint MOUSEEVENTF_WHEEL = 0x0800, MOUSEEVENTF_HWHEEL = 0x1000;
    private const uint MOUSEEVENTF_ABSOLUTE = 0x8000, MOUSEEVENTF_VIRTUALDESK = 0x4000;
    private const uint KEYEVENTF_KEYUP = 0x0002, KEYEVENTF_UNICODE = 0x0004;
    private const int WHEEL_DELTA = 120;

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public nint dwExtraInfo; }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public nint dwExtraInfo; }

    [StructLayout(LayoutKind.Explicit)]
    private struct INPUTUNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT { public uint type; public INPUTUNION u; }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    private static extern int GetSystemMetrics(int index);

    private static void Mouse(uint flags, int dx, int dy, int data)
    {
        var inp = new INPUT
        {
            type = INPUT_MOUSE,
            u = new INPUTUNION { mi = new MOUSEINPUT { dx = dx, dy = dy, mouseData = unchecked((uint)data), dwFlags = flags } }
        };
        SendInput(1, new[] { inp }, Marshal.SizeOf<INPUT>());
    }

    public static void Move(int dx, int dy) => Mouse(MOUSEEVENTF_MOVE, dx, dy, 0);

    public static void Absolute(double x, double y)
    {
        // VIRTUALDESK 覆盖多显示器，坐标归一化到 0..65535
        int vx = GetSystemMetrics(76); // SM_XVIRTUALSCREEN
        int vy = GetSystemMetrics(77); // SM_YVIRTUALSCREEN
        int w = GetSystemMetrics(78);  // SM_CXVIRTUALSCREEN
        int h = GetSystemMetrics(79);  // SM_CYVIRTUALSCREEN
        if (w <= 0 || h <= 0) { Move(0, 0); return; }
        int px = vx + (int)(w * x);
        int py = vy + (int)(h * y);
        const uint fx = MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK | MOUSEEVENTF_MOVE;
        Mouse(fx, px * 65535 / (w - 1), py * 65535 / (h - 1), 0);
    }

    public static void Button(string name, bool down)
    {
        uint f = (name switch
        {
            "right" => down ? MOUSEEVENTF_RIGHTDOWN : MOUSEEVENTF_RIGHTUP,
            "middle" => down ? MOUSEEVENTF_MIDDLEDOWN : MOUSEEVENTF_MIDDLEUP,
            _ => down ? MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP
        });
        Mouse(f, 0, 0, 0);
    }

    public static void Click(string name, bool doubleClick)
    {
        Button(name, true);
        Button(name, false);
        if (doubleClick)
        {
            Thread.Sleep(20);
            Button(name, true);
            Button(name, false);
        }
    }

    /// dy>0 表示内容向下滚（手机端语义），Windows 滚轮正值=向上滚，取负。
    public static void Scroll(int dx, int dy)
    {
        if (dy != 0) Mouse(MOUSEEVENTF_WHEEL, 0, 0, -dy * WHEEL_DELTA);
        if (dx != 0) Mouse(MOUSEEVENTF_HWHEEL, 0, 0, dx * WHEEL_DELTA);
    }

    /// d>0 放大：按住 Ctrl + 滚轮。
    public static void Zoom(int d)
    {
        if (d == 0) return;
        KeyDown(0xA2 /*VK_LCONTROL*/);
        Mouse(MOUSEEVENTF_WHEEL, 0, 0, d * WHEEL_DELTA);
        KeyUp(0xA2);
    }

    private static void KeyDown(ushort vk) => Keyboard(vk, 0, 0);
    private static void KeyUp(ushort vk) => Keyboard(vk, 0, KEYEVENTF_KEYUP);

    private static void Keyboard(ushort vk, ushort scan, uint flags)
    {
        var inp = new INPUT
        {
            type = INPUT_KEYBOARD,
            u = new INPUTUNION { ki = new KEYBDINPUT { wVk = vk, wScan = scan, dwFlags = flags } }
        };
        SendInput(1, new[] { inp }, Marshal.SizeOf<INPUT>());
    }

    private static readonly Dictionary<string, ushort> Vk = new()
    {
        ["tab"] = 0x09, ["enter"] = 0x0D, ["esc"] = 0x1B, ["space"] = 0x20,
        ["backspace"] = 0x08, ["delete"] = 0x2E, ["home"] = 0x24, ["end"] = 0x23,
        ["pageup"] = 0x21, ["pagedown"] = 0x22, ["left"] = 0x25, ["up"] = 0x26,
        ["right"] = 0x27, ["down"] = 0x28,
        ["f1"] = 0x70, ["f2"] = 0x71, ["f3"] = 0x72, ["f4"] = 0x73, ["f5"] = 0x74,
        ["f6"] = 0x75, ["f7"] = 0x76, ["f8"] = 0x77, ["f9"] = 0x78, ["f10"] = 0x79,
        ["f11"] = 0x7A, ["f12"] = 0x7B
    };

    /// 组合键，如 "win+tab"、"ctrl+up"、"cmd+ctrl+q"（cmd 即 Win 键）。
    public static void Combo(string s)
    {
        var parts = s.ToLowerInvariant().Split('+', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Length == 0) return;
        var last = parts[^1];
        ushort vk;
        if (last.Length == 1 && char.IsAsciiLetterOrDigit(last[0]))
            vk = (ushort)char.ToUpperInvariant(last[0]); // VK_A..VK_Z / VK_0..VK_9
        else if (!Vk.TryGetValue(last, out vk)) return;

        // VK 值不是位标志，逐个记下再按序按下、逆序释放
        var modKeys = new List<ushort>();
        foreach (var p in parts.AsSpan(0, parts.Length - 1))
        {
            ushort? m = p switch
            {
                "ctrl" or "control" => 0xA2,
                "shift" => 0xA0,
                "alt" => 0xA4,
                "cmd" or "win" or "gui" or "meta" => 0x5B /*VK_LWIN*/,
                _ => null
            };
            if (m.HasValue) modKeys.Add(m.Value);
        }
        foreach (var m in modKeys) KeyDown(m);
        KeyDown(vk);
        Thread.Sleep(5);
        KeyUp(vk);
        for (var i = modKeys.Count - 1; i >= 0; i--) KeyUp(modKeys[i]);
    }

    /// Unicode 文本直传（支持中文整句）。
    public static void Text(string s)
    {
        foreach (var ch in s)
        {
            var inputs = new INPUT[2];
            inputs[0] = KeyInput(ch, 0);
            inputs[1] = KeyInput(ch, KEYEVENTF_KEYUP);
            SendInput(2, inputs, Marshal.SizeOf<INPUT>());
        }
    }

    private static INPUT KeyInput(char ch, uint extraFlags)
    {
        return new INPUT
        {
            type = INPUT_KEYBOARD,
            u = new INPUTUNION
            {
                ki = new KEYBDINPUT { wScan = ch, dwFlags = KEYEVENTF_UNICODE | extraFlags }
            }
        };
    }
}
