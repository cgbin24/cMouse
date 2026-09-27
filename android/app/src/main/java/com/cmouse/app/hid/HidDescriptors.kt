package com.cmouse.app.hid

/**
 * 蓝牙 HID 报告描述符与报告构造。
 *
 * 一个描述符内含两个顶层 Collection：
 *  - Report ID 1：鼠标（5 键 + 16 位相对 XY + 垂直滚轮 + AC Pan 水平滚动）
 *  - Report ID 2：键盘（标准 6 键无冲 + 8 个修饰键）
 * 16 位 XY 保证快速滑动单报告即可表达较大位移，避免 8 位描述符丢帧。
 * 描述符以 Int 数组书写再转 Byte，规避 Kotlin 字节常量 0x80-0xFF 的书写坑。
 */
object HidDescriptors {

    private val DESCRIPTOR_WORDS = intArrayOf(
        // ---- 鼠标 ----
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x02, // Usage (Mouse)
        0xA1, 0x01, // Collection (Application)
        0x85, 0x01, //   Report ID (1)
        0x09, 0x01, //   Usage (Pointer)
        0xA1, 0x00, //   Collection (Physical)
        0x05, 0x09, //     Usage Page (Button)
        0x19, 0x01, 0x29, 0x05, //     Usage Min 1 / Max 5
        0x15, 0x00, 0x25, 0x01, //     Logical 0..1
        0x95, 0x05, 0x75, 0x01, //     Count 5, Size 1
        0x81, 0x02, //     Input (Data, Var, Abs)
        0x95, 0x01, 0x75, 0x03, 0x81, 0x01, //     3 bit padding
        0x05, 0x01, //     Usage Page (Generic Desktop)
        0x09, 0x30, 0x09, 0x31, //     X, Y
        0x16, 0x01, 0x80, //     Logical Minimum (-32767)
        0x26, 0xFF, 0x7F, //     Logical Maximum (32767)
        0x75, 0x10, 0x95, 0x02, //     Size 16, Count 2
        0x81, 0x06, //     Input (Data, Var, Rel)
        0x09, 0x38, //     Usage (Wheel)
        0x15, 0x81, 0x25, 0x7F, //     Logical -127..127
        0x75, 0x08, 0x95, 0x01, 0x81, 0x06,
        0x05, 0x0C, //     Usage Page (Consumer)
        0x0A, 0x38, 0x02, //     Usage (AC Pan, 水平滚动)
        0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x01, 0x81, 0x06,
        0xC0, 0xC0,
        // ---- 键盘 ----
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x06, // Usage (Keyboard)
        0xA1, 0x01, // Collection (Application)
        0x85, 0x02, //   Report ID (2)
        0x05, 0x07, //   Usage Page (Keyboard)
        0x19, 0xE0, 0x29, 0xE7, //   LeftCtrl..RightGui
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01, //   reserved byte
        0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02, // LED output
        0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, //   6 keycodes, Logical 0..0x65
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
        0xC0
    )

    val COMBINED: ByteArray = ByteArray(DESCRIPTOR_WORDS.size) { i -> DESCRIPTOR_WORDS[i].toByte() }

    const val REPORT_ID_MOUSE = 1
    const val REPORT_ID_KEYBOARD = 2

    // 修饰键位（HID byte0）
    const val MOD_LCTRL = 0x01
    const val MOD_LSHIFT = 0x02
    const val MOD_LALT = 0x04
    const val MOD_LGUI = 0x08 // macOS Cmd / Windows Win
    const val MOD_RCTRL = 0x10
    const val MOD_RSHIFT = 0x20
    const val MOD_RALT = 0x40
    const val MOD_RGUI = 0x80

    /** 构造鼠标报告（7 字节负载，不含 Report ID）。 */
    fun mouseReport(buttons: Int, x: Int, y: Int, wheel: Int, pan: Int): ByteArray {
        fun le16(v: Int): ByteArray {
            val c = v.coerceIn(-32767, 32767)
            return byteArrayOf((c and 0xFF).toByte(), ((c shr 8) and 0xFF).toByte())
        }
        val xy = le16(x) + le16(y)
        return byteArrayOf(
            (buttons and 0x1F).toByte(),
            xy[0], xy[1], xy[2], xy[3],
            wheel.coerceIn(-127, 127).toByte(),
            pan.coerceIn(-127, 127).toByte()
        )
    }

    /** 构造键盘报告（8 字节负载，不含 Report ID）。 */
    fun keyboardReport(modifiers: Int, keys: IntArray): ByteArray {
        val r = ByteArray(8)
        r[0] = (modifiers and 0xFF).toByte()
        for (i in 0 until 6) r[2 + i] = (keys.getOrNull(i) ?: 0).toByte()
        return r
    }
}

/**
 * ASCII 可打印字符 → HID 键码 + 需要的 Shift 修饰。
 * 中文等非 ASCII 字符在模式 A 下交给电脑端输入法（与物理键盘行为一致）。
 */
object HidKeyMap {

    data class Entry(val modifiers: Int, val usage: Int)

    private val letters = buildMap {
        for (i in 0 until 26) {
            put('a' + i, Entry(0, 0x04 + i))
            put('A' + i, Entry(HidDescriptors.MOD_LSHIFT, 0x04 + i))
        }
    }

    private val digitsAndSymbols = mapOf(
        '1' to Entry(0, 0x1E), '!' to Entry(HidDescriptors.MOD_LSHIFT, 0x1E),
        '2' to Entry(0, 0x1F), '@' to Entry(HidDescriptors.MOD_LSHIFT, 0x1F),
        '3' to Entry(0, 0x20), '#' to Entry(HidDescriptors.MOD_LSHIFT, 0x20),
        '4' to Entry(0, 0x21), '$' to Entry(HidDescriptors.MOD_LSHIFT, 0x21),
        '5' to Entry(0, 0x22), '%' to Entry(HidDescriptors.MOD_LSHIFT, 0x22),
        '6' to Entry(0, 0x23), '^' to Entry(HidDescriptors.MOD_LSHIFT, 0x23),
        '7' to Entry(0, 0x24), '&' to Entry(HidDescriptors.MOD_LSHIFT, 0x24),
        '8' to Entry(0, 0x25), '*' to Entry(HidDescriptors.MOD_LSHIFT, 0x25),
        '9' to Entry(0, 0x26), '(' to Entry(HidDescriptors.MOD_LSHIFT, 0x26),
        '0' to Entry(0, 0x27), ')' to Entry(HidDescriptors.MOD_LSHIFT, 0x27),
        '\n' to Entry(0, 0x28),
        '\t' to Entry(0, 0x2B),
        ' ' to Entry(0, 0x2C),
        '-' to Entry(0, 0x2D), '_' to Entry(HidDescriptors.MOD_LSHIFT, 0x2D),
        '=' to Entry(0, 0x2E), '+' to Entry(HidDescriptors.MOD_LSHIFT, 0x2E),
        '[' to Entry(0, 0x2F), '{' to Entry(HidDescriptors.MOD_LSHIFT, 0x2F),
        ']' to Entry(0, 0x30), '}' to Entry(HidDescriptors.MOD_LSHIFT, 0x30),
        '\\' to Entry(0, 0x31), '|' to Entry(HidDescriptors.MOD_LSHIFT, 0x31),
        ';' to Entry(0, 0x33), ':' to Entry(HidDescriptors.MOD_LSHIFT, 0x33),
        '\'' to Entry(0, 0x34), '"' to Entry(HidDescriptors.MOD_LSHIFT, 0x34),
        '`' to Entry(0, 0x35), '~' to Entry(HidDescriptors.MOD_LSHIFT, 0x35),
        ',' to Entry(0, 0x36), '<' to Entry(HidDescriptors.MOD_LSHIFT, 0x36),
        '.' to Entry(0, 0x37), '>' to Entry(HidDescriptors.MOD_LSHIFT, 0x37),
        '/' to Entry(0, 0x38), '?' to Entry(HidDescriptors.MOD_LSHIFT, 0x38)
    )

    fun lookup(c: Char): Entry? = letters[c] ?: digitsAndSymbols[c]
}
