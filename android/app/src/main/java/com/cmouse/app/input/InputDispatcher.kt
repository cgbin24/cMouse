package com.cmouse.app.input

import com.cmouse.app.data.SettingsStore
import com.cmouse.app.hid.HidDescriptors
import com.cmouse.app.transport.LanClient

/**
 * 输入路由：手势引擎产出统一语义事件，这里按当前模式
 * （蓝牙 HID / Wi-Fi 接收端）分发到对应传输层。
 */
class InputDispatcher(private val settings: SettingsStore) {

    var hidProvider: (() -> HidFacade?)? = null
    var lanProvider: (() -> LanClient?)? = null

    private val lanMode: Boolean get() = settings.getString(SettingsStore.Keys.MODE, "hid") == "lan"

    private val sensitivity: Float get() = settings.getFloat(SettingsStore.Keys.SENSITIVITY, 1f)
    private val scrollSpeed: Float get() = settings.getFloat(SettingsStore.Keys.SCROLL_SPEED, 1f)
    private val pinchZoomOn: Boolean get() = settings.getBool(SettingsStore.Keys.PINCH_ZOOM, true)

    // 滚轮累计器：把连续位移量化成整格滚轮
    private var wheelAccY = 0f
    private var wheelAccX = 0f
    private var zoomAcc = 0f
    private var moveAccX = 0f
    private var moveAccY = 0f

    val isReady: Boolean
        get() = if (lanMode) lanProvider?.invoke()?.isReady == true
        else hidProvider?.invoke()?.isReady == true

    fun move(dx: Float, dy: Float) {
        moveAccX += dx * sensitivity
        moveAccY += dy * sensitivity
        val x = moveAccX.toInt()
        val y = moveAccY.toInt()
        if (x == 0 && y == 0) return
        moveAccX -= x
        moveAccY -= y
        if (lanMode) lanProvider?.invoke()?.move(x.toFloat(), y.toFloat())
        else hidProvider?.invoke()?.sendMouse(0, x, y, 0, 0)
    }

    fun click(button: Int, double: Boolean) {
        val name = buttonName(button)
        if (lanMode) lanProvider?.invoke()?.click(name, double)
        else hidProvider?.invoke()?.let { hid ->
            val mask = buttonMask(button)
            hid.sendMouse(mask, 0, 0, 0, 0)
            hid.sendMouse(0, 0, 0, 0, 0)
            if (double) {
                hid.sendMouse(mask, 0, 0, 0, 0)
                hid.sendMouse(0, 0, 0, 0, 0)
            }
        }
    }

    fun button(button: Int, down: Boolean) {
        val mask = buttonMask(button)
        if (lanMode) lanProvider?.invoke()?.button(buttonName(button), down)
        else hidProvider?.invoke()?.sendMouse(if (down) mask else 0, 0, 0, 0, 0)
    }

    /**
     * 滚动。参数为触控空间方向（手指向下 dy>0 → 内容向下滚）。
     * HID 滚轮正值=向上滚，故取负；LAN 协议约定 +dy=内容向下滚。
     */
    fun scroll(dx: Float, dy: Float) {
        wheelAccY += dy * scrollSpeed
        wheelAccX += dx * scrollSpeed
        val ticksY = wheelAccY.toInt()
        val ticksX = wheelAccX.toInt()
        if (ticksY == 0 && ticksX == 0) return
        wheelAccY -= ticksY
        wheelAccX -= ticksX
        if (lanMode) lanProvider?.invoke()?.scroll(ticksX.toFloat(), ticksY.toFloat())
        else hidProvider?.invoke()?.sendMouse(0, 0, 0, (-ticksY).coerceIn(-127, 127), ticksX.coerceIn(-127, 127))
    }

    /** 双指捏合 -> Ctrl + 滚轮（绝大多数应用中等效缩放）。zoom>0 表示放大。 */
    fun zoom(scaleDeltaPct: Float) {
        if (!pinchZoomOn) return
        zoomAcc += scaleDeltaPct / 100f / 0.08f   // 每 8% 跨度变化记 1 格
        val ticks = zoomAcc.toInt()
        if (ticks == 0) return
        zoomAcc -= ticks
        if (lanMode) {
            lanProvider?.invoke()?.zoom(ticks.coerceIn(-30, 30))
        } else {
            hidProvider?.invoke()?.let { zoomHid(it, ticks) }
        }
    }

    /**
     * HID 模式专用：Ctrl 按住 + 滚轮。鼠标报告不携带键盘修饰位，
     * 因此先发键盘报告按住 Ctrl，再发滚轮报告（负值=向上滚=放大），最后释放。
     */
    fun zoomHid(hid: HidFacade, ticks: Int) {
        hid.sendKeyboard(HidDescriptors.MOD_LCTRL, IntArray(6))
        hid.sendMouse(0, 0, 0, (-ticks).coerceIn(-127, 127), 0)
        hid.sendKeyboard(0, IntArray(6))
    }

    fun swipe(fingers: Int, dir: SwipeDir) {
        val gesture = when (dir) {
            SwipeDir.UP -> "three_up"; SwipeDir.DOWN -> "three_down"
            SwipeDir.LEFT -> "three_left"; SwipeDir.RIGHT -> "three_right"
        }.let { if (fingers == 3) it else "${fingers}_${dir.name.lowercase()}" }
        when (val act = settings.gestureAction(gesture)) {
            null -> {}
            else -> when (act.first) {
                "combo" -> {
                    if (lanMode) lanProvider?.invoke()?.combo(act.second)
                    else hidProvider?.invoke()?.sendCombo(act.second)
                }
                "none" -> {}
            }
        }
    }

    fun text(s: String) {
        if (lanMode) lanProvider?.invoke()?.text(s)
        else hidProvider?.invoke()?.typeText(s)
    }

    fun keyChar(c: Char) {
        if (lanMode) lanProvider?.invoke()?.text(c.toString())
        else hidProvider?.invoke()?.typeChar(c)
    }

    /** 带粘滞修饰键的字符输入（虚拟键盘 Ctrl/Alt/Cmd + 字母场景）。 */
    fun keyChar(c: Char, mods: Collection<String>) {
        if (mods.isEmpty()) {
            keyChar(c)
            return
        }
        if (lanMode) {
            val combo = (mods + c.toString()).joinToString("+")
            lanProvider?.invoke()?.key(combo)
        } else {
            val e = com.cmouse.app.hid.HidKeyMap.lookup(c) ?: return
            var m = e.modifiers
            for (mod in mods) m = m or when (mod) {
                "ctrl" -> HidDescriptors.MOD_LCTRL
                "shift" -> HidDescriptors.MOD_LSHIFT
                "alt" -> HidDescriptors.MOD_LALT
                "cmd", "win" -> HidDescriptors.MOD_LGUI
                else -> 0
            }
            hidProvider?.invoke()?.let {
                it.sendKeyboard(m, intArrayOf(e.usage))
                it.sendKeyboard(0, IntArray(6))
            }
        }
    }

    fun specialKey(combo: String) {
        if (lanMode) lanProvider?.invoke()?.key(combo)
        else hidProvider?.invoke()?.sendCombo(combo)
    }

    private fun buttonName(b: Int) = when (b) {
        BUTTON_RIGHT -> "right"; BUTTON_MIDDLE -> "middle"; else -> "left"
    }

    private fun buttonMask(b: Int) = when (b) {
        BUTTON_RIGHT -> 0x02; BUTTON_MIDDLE -> 0x04; else -> 0x01
    }

    companion object {
        const val BUTTON_LEFT = 0
        const val BUTTON_RIGHT = 1
        const val BUTTON_MIDDLE = 2
    }
}

/** HidDeviceManager 的最小接口视图，便于手势层解耦。 */
interface HidFacade {
    val isReady: Boolean
    fun sendMouse(buttons: Int, x: Int, y: Int, wheel: Int, pan: Int): Boolean
    fun sendKeyboard(modifiers: Int, keys: IntArray): Boolean
    fun sendCombo(combo: String): Boolean
    fun typeChar(c: Char): Boolean
    fun typeText(text: String): Boolean
}

enum class SwipeDir { UP, DOWN, LEFT, RIGHT }
