package com.cmouse.app.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.cmouse.app.input.HidFacade
import java.util.concurrent.Executors

/**
 * 蓝牙 HID 设备角色管理：把手机注册为标准"键鼠复合设备"，
 * 电脑端用系统内置 HID 类驱动免驱识别（macOS/Windows 均无需安装任何东西）。
 */
@SuppressLint("MissingPermission")
class HidDeviceManager(
    context: Context,
    private val listener: Listener
) : HidFacade {
    interface Listener {
        fun onRegistered(registered: Boolean)
        fun onHostChanged(name: String?, connected: Boolean)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter?
        get() = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private var hid: BluetoothHidDevice? = null
    private var registered = false
    private var host: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val h = proxy as? BluetoothHidDevice ?: return
            hid = h
            registered = try {
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "cMouse Trackpad",
                    "cMouse virtual trackpad & keyboard",
                    "cMouse",
                    BluetoothHidDevice.SUBCLASS1_COMBO,
                    HidDescriptors.COMBINED
                )
                h.registerApp(sdp, null, null, executor, hidCallback)
            } catch (e: SecurityException) {
                listener.onError("缺少蓝牙权限：请在系统设置中允许 cMouse \"附近设备\"权限后重试")
                false
            } catch (e: Exception) {
                listener.onError(
                    "HID 注册失败：${e.message ?: e.javaClass.simpleName}。" +
                        "部分系统（HarmonyOS/EMUI、企业管控 ROM）限制虚拟输入设备，请改用 Wi-Fi 接收端模式"
                )
                false
            }
            listener.onRegistered(registered)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hid = null
                registered = false
                listener.onRegistered(false)
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidDeviceManager.registered = registered
            listener.onRegistered(registered)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    listener.onHostChanged(device.safeName(), true)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host == device) host = null
                    listener.onHostChanged(null, false)
                }
            }
        }
    }

    val isRegistered: Boolean get() = registered && hid != null
    val isHostConnected: Boolean get() = host != null

    override val isReady: Boolean get() = isRegistered && isHostConnected

    fun start() {
        // BluetoothHidDevice 为 Android 9 (API 28) 引入；更低版本只支持 Wi-Fi 模式
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            listener.onError("蓝牙直连模式需要 Android 9.0 以上，请改用 Wi-Fi 接收端模式")
            return
        }
        try {
            val a = adapter ?: run {
                listener.onError("设备不支持蓝牙")
                return
            }
            if (!a.isEnabled) {
                listener.onError("请先打开手机蓝牙，再点\"开始连接\"")
                return
            }
            a.getProfileProxy(appContext, profileListener, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            listener.onError("缺少蓝牙权限：请在系统设置中允许 cMouse \"附近设备\"权限后重试")
        }
    }

    fun stop() {
        try {
            hid?.unregisterApp()
        } catch (_: Exception) {
        }
        host = null
        registered = false
        hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hid = null
    }

    /** 发送鼠标报告，x/y 为相对位移，wheel/pan 为滚轮格数。 */
    override fun sendMouse(buttons: Int, x: Int, y: Int, wheel: Int, pan: Int): Boolean {
        val h = hid ?: return false
        val d = host ?: return false
        return try {
            h.sendReport(d, HidDescriptors.REPORT_ID_MOUSE, HidDescriptors.mouseReport(buttons, x, y, wheel, pan))
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    override fun sendKeyboard(modifiers: Int, keys: IntArray): Boolean {
        val h = hid ?: return false
        val d = host ?: return false
        return try {
            h.sendReport(d, HidDescriptors.REPORT_ID_KEYBOARD, HidDescriptors.keyboardReport(modifiers, keys))
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun releaseKeyboard() = sendKeyboard(0, IntArray(6))

    /** 输入单个字符（虚拟键盘）。返回是否成功映射。 */
    override fun typeChar(c: Char): Boolean {
        val e = HidKeyMap.lookup(c) ?: return false
        sendKeyboard(e.modifiers, intArrayOf(e.usage))
        Thread.sleep(6)
        releaseKeyboard()
        return true
    }

    override fun typeText(text: String): Boolean {
        var ok = true
        for (c in text) if (!typeChar(c)) ok = false
        return ok
    }

    /** 发送组合键，如 Ctrl+Up："ctrl+up"、"cmd+tab"、"win+shift+s"。 */
    override fun sendCombo(combo: String): Boolean {
        var mod = 0
        var key = 0
        for (part in combo.lowercase().split('+')) {
            when (part.trim()) {
                "ctrl", "control" -> mod = mod or HidDescriptors.MOD_LCTRL
                "shift" -> mod = mod or HidDescriptors.MOD_LSHIFT
                "alt", "option" -> mod = mod or HidDescriptors.MOD_LALT
                "cmd", "gui", "win", "meta" -> mod = mod or HidDescriptors.MOD_LGUI
                else -> key = SpecialKeys[part.trim()] ?: 0
            }
        }
        if (key == 0) return false
        sendKeyboard(mod, intArrayOf(key))
        Thread.sleep(6)
        releaseKeyboard()
        return true
    }

    private fun BluetoothDevice.safeName(): String =
        try { name ?: "未知设备" } catch (_: SecurityException) { "未知设备" }

    companion object {
        val SpecialKeys = mapOf(
            "up" to 0x52, "down" to 0x51, "left" to 0x50, "right" to 0x4F,
            "enter" to 0x28, "esc" to 0x29, "tab" to 0x2B, "space" to 0x2C,
            "backspace" to 0x2A, "delete" to 0x4C, "home" to 0x4A, "end" to 0x4D,
            "pageup" to 0x4B, "pagedown" to 0x4E,
            "f1" to 0x3A, "f2" to 0x3B, "f3" to 0x3C, "f4" to 0x3D, "f5" to 0x3E,
            "f6" to 0x3F, "f7" to 0x40, "f8" to 0x41, "f9" to 0x42, "f10" to 0x43,
            "f11" to 0x44, "f12" to 0x45
        )
    }
}
