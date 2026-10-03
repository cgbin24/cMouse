package com.cmouse.app.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.cmouse.app.MainActivity
import com.cmouse.app.data.SettingsStore
import com.cmouse.app.input.InputDispatcher

/**
 * Android 8.0 以下设备的传统 View 界面：
 * 主界面（PadUi）+ 设置对话框 + 键盘对话框，全程传统 View API，
 * 完全绕开 Compose 互操作（部分老 ROM 的 interop 触摸分发不可靠，已在 OPPO R9st 实测）。
 */
fun setupLegacyUi(
    activity: MainActivity,
    settings: SettingsStore,
    dispatcher: InputDispatcher
): PadUiHolder {
    var padUi: PadUiHolder? = null
    padUi = buildPadUi(
        activity, settings, dispatcher,
        onOpenKeyboard = { showLegacyKeyboardDialog(activity, settings, dispatcher) },
        onOpenSettings = { showLegacySettingsDialog(activity, settings, dispatcher, padUi) }
    )
    activity.setContentView(padUi.root)
    padUi.statusPill.text = padStatusText(activity.currentStatus(), settings, padUi.versionName)
    return padUi
}

// ---------------- 通用小部件 ----------------

private fun dpF(context: Context, v: Int): Int =
    (v * context.resources.displayMetrics.density + 0.5f).toInt()

private fun legacyLabel(context: Context, s: String): TextView = TextView(context).apply {
    text = s
    textSize = 15f
    setPadding(0, dpF(context, 10), 0, dpF(context, 4))
}

private fun legacyButton(context: Context, s: String, onClick: () -> Unit): Button =
    Button(context).apply { text = s; setOnClickListener { onClick() } }

private fun legacyRow(context: Context, vararg children: android.view.View): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        children.forEach {
            addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = dpF(context, 8) })
        }
    }

private fun legacyEdit(context: Context, hint: String, input: Int = InputType.TYPE_CLASS_TEXT): EditText =
    EditText(context).apply {
        this.hint = hint
        inputType = input or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSingleLine(true)
    }

// ---------------- 设置对话框 ----------------

fun showLegacySettingsDialog(
    activity: MainActivity,
    settings: SettingsStore,
    dispatcher: InputDispatcher,
    padUi: PadUiHolder?
) {
    val ctx = activity
    var mode = settings.getString(SettingsStore.Keys.MODE, "hid")

    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpF(ctx, 22), dpF(ctx, 8), dpF(ctx, 22), dpF(ctx, 4))
    }

    // ---- 连接模式 ----
    box.addView(legacyLabel(ctx, "连接模式（当前：${if (mode == "hid") "蓝牙直连" else "Wi-Fi 接收端"}）"))
    box.addView(legacyRow(ctx,
        legacyButton(ctx, "蓝牙直连") {
            settings.putString(SettingsStore.Keys.MODE, "hid")
            activity.stopHid(); activity.disconnectLan(); mode = "hid"
            padUi?.statusPill?.text = padStatusText(activity.currentStatus(), settings, padUi.versionName)
            Toast.makeText(ctx, "已切换为蓝牙直连模式（电脑端零安装）", Toast.LENGTH_SHORT).show()
        },
        legacyButton(ctx, "Wi-Fi 接收端") {
            settings.putString(SettingsStore.Keys.MODE, "lan")
            activity.stopHid(); mode = "lan"
            padUi?.statusPill?.text = padStatusText(activity.currentStatus(), settings, padUi.versionName)
            Toast.makeText(ctx, "已切换为 Wi-Fi 接收端模式", Toast.LENGTH_SHORT).show()
        }
    ))

    // ---- 蓝牙连接 ----
    box.addView(legacyLabel(ctx, "蓝牙：启动后在电脑蓝牙设置中配对 cMouse Trackpad（需 Android 9+）"))
    box.addView(legacyRow(ctx,
        legacyButton(ctx, "启动并等待配对") {
            activity.startHid(); activity.refreshLegacyStatus()
        },
        legacyButton(ctx, "停止") {
            activity.stopHid(); activity.refreshLegacyStatus()
        }
    ))
    if (activity.currentStatus().hidMsg.isNotEmpty()) {
        box.addView(TextView(ctx).apply {
            text = activity.currentStatus().hidMsg
            textSize = 13f
            setTextColor(0xFFD93025.toInt())
            setPadding(0, dpF(ctx, 2), 0, dpF(ctx, 2))
        })
    }

    // ---- 接收端连接 ----
    box.addView(legacyLabel(ctx, "Wi-Fi：填电脑端接收端显示的 IP / 端口 / 配对码"))
    val hostEdit = legacyEdit(ctx, "电脑 IP").apply { setText(settings.getString(SettingsStore.Keys.LAN_HOST)) }
    val portEdit = legacyEdit(ctx, "端口", InputType.TYPE_CLASS_NUMBER).apply {
        setText(settings.getString(SettingsStore.Keys.LAN_PORT, "8433"))
    }
    val codeEdit = legacyEdit(ctx, "配对码", InputType.TYPE_CLASS_NUMBER).apply {
        setText(settings.getString(SettingsStore.Keys.LAN_CODE))
    }
    box.addView(legacyRow(ctx, hostEdit, portEdit))
    box.addView(codeEdit)
    box.addView(legacyRow(ctx,
        legacyButton(ctx, "连接") {
            settings.putString(SettingsStore.Keys.LAN_HOST, hostEdit.text.toString().trim())
            settings.putString(SettingsStore.Keys.LAN_PORT, portEdit.text.toString().ifEmpty { "8433" })
            settings.putString(SettingsStore.Keys.LAN_CODE, codeEdit.text.toString().trim())
            activity.connectLan(hostEdit.text.toString().trim(),
                portEdit.text.toString().toIntOrNull() ?: 8433, codeEdit.text.toString().trim())
        },
        legacyButton(ctx, "断开") { activity.disconnectLan() }
    ))

    // ---- 触控手感 ----
    box.addView(legacyLabel(ctx, "触控手感"))
    val sensValues = floatArrayOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f)
    var sensIdx = sensValues.indexOfFirst { it >= settings.getFloat(SettingsStore.Keys.SENSITIVITY, 1f) - 0.01f }
        .coerceAtLeast(0)
    val sensBtn = legacyButton(ctx, "光标灵敏度：${sensValues[sensIdx]}×") { }
    sensBtn.setOnClickListener {
        sensIdx = (sensIdx + 1) % sensValues.size
        settings.putFloat(SettingsStore.Keys.SENSITIVITY, sensValues[sensIdx])
        sensBtn.text = "光标灵敏度：${sensValues[sensIdx]}×"
    }
    box.addView(sensBtn)
    val scrollValues = floatArrayOf(0.3f, 0.5f, 1f, 1.5f, 2f, 3f)
    var scrollIdx = scrollValues.indexOfFirst { it >= settings.getFloat(SettingsStore.Keys.SCROLL_SPEED, 1f) - 0.01f }
        .coerceAtLeast(0)
    val scrollBtn = legacyButton(ctx, "滚动速度：${scrollValues[scrollIdx]}×") { }
    scrollBtn.setOnClickListener {
        scrollIdx = (scrollIdx + 1) % scrollValues.size
        settings.putFloat(SettingsStore.Keys.SCROLL_SPEED, scrollValues[scrollIdx])
        scrollBtn.text = "滚动速度：${scrollValues[scrollIdx]}×"
    }
    box.addView(scrollBtn)
    val naturalBtn = legacyButton(ctx,
        "自然滚动：${if (settings.getBool(SettingsStore.Keys.NATURAL_SCROLL, true)) "开" else "关"}") { }
    naturalBtn.setOnClickListener {
        val v = !settings.getBool(SettingsStore.Keys.NATURAL_SCROLL, true)
        settings.putBool(SettingsStore.Keys.NATURAL_SCROLL, v)
        naturalBtn.text = "自然滚动：${if (v) "开" else "关"}"
    }
    val pinchBtn = legacyButton(ctx,
        "捏合缩放：${if (settings.getBool(SettingsStore.Keys.PINCH_ZOOM, true)) "开" else "关"}") { }
    pinchBtn.setOnClickListener {
        val v = !settings.getBool(SettingsStore.Keys.PINCH_ZOOM, true)
        settings.putBool(SettingsStore.Keys.PINCH_ZOOM, v)
        pinchBtn.text = "捏合缩放：${if (v) "开" else "关"}"
    }
    val calibBtn = legacyButton(ctx, "滚动方向不对？点此翻转") {
        val v = !settings.getBool(SettingsStore.Keys.NATURAL_SCROLL, true)
        settings.putBool(SettingsStore.Keys.NATURAL_SCROLL, v)
        Toast.makeText(ctx, "滚动方向已翻转，请再试一次双指滑动", Toast.LENGTH_SHORT).show()
    }
    box.addView(calibBtn)
    box.addView(legacyRow(ctx, naturalBtn, pinchBtn))

    // ---- 三指滑动自定义映射 ----
    box.addView(legacyLabel(ctx, "三指滑动（点按切换动作）"))
    val gestureOptions = listOf(
        "无动作" to "", "调度中心/任务视图" to "ctrl+up", "应用窗口切换" to "ctrl+left",
        "显示桌面" to "win+d", "启动台/开始菜单" to "win", "锁屏" to "cmd+ctrl+q"
    )
    fun gestureButtonText(gesture: String): String {
        val act = settings.gestureAction(gesture)
        val combo = act?.second ?: ""
        val name = gestureOptions.firstOrNull { it.second == combo }?.first
            ?: if (act == null || act.first == "none") "无动作" else combo
        return name
    }
    fun gestureButton(gesture: String, label: String): Button {
        val btn = legacyButton(ctx, "$label：${gestureButtonText(gesture)}") { }
        btn.setOnClickListener {
            val current = settings.gestureAction(gesture)?.second ?: ""
            val idx = gestureOptions.indexOfFirst { it.second == current }.let { if (it < 0) 0 else it }
            val next = gestureOptions[(idx + 1) % gestureOptions.size]
            if (next.second.isEmpty()) settings.setGestureAction(gesture, "none", "")
            else settings.setGestureAction(gesture, "combo", next.second)
            btn.text = "$label：${next.first}"
        }
        return btn
    }
    box.addView(gestureButton("three_up", "上滑"))
    box.addView(gestureButton("three_down", "下滑"))
    box.addView(gestureButton("three_left", "左滑"))
    box.addView(gestureButton("three_right", "右滑"))
    box.addView(legacyButton(ctx, "测试三指动作（发送上滑动作）") {
        dispatcher.swipe(3, com.cmouse.app.input.SwipeDir.UP)
    })
    box.addView(TextView(ctx).apply {
        text = "手势说明：单指移动/轻点/双击/长按拖拽；双指滚动/右键/捏合缩放；三指滑动按上表发送组合键（触发时震动）"
        textSize = 12f
        setPadding(0, dpF(ctx, 6), 0, 0)
    })

    // ---- 已配对设备 ----
    box.addView(legacyLabel(ctx, "已配对设备"))
    val devices = settings.devices()
    if (devices.isEmpty()) {
        box.addView(TextView(ctx).apply {
            text = "暂无"
            textSize = 13f
        })
    } else {
        devices.forEach { d ->
            box.addView(TextView(ctx).apply {
                text = "· ${d.second}（${if (d.third == "hid") "蓝牙" else "Wi-Fi"}）"
                textSize = 13f
            })
        }
    }

    // ---- 数据与隐私 ----
    box.addView(legacyLabel(ctx, "数据与隐私：仅存本机 SQLite，卸载 App 自动彻底清除"))
    box.addView(legacyButton(ctx, "清除所有本地数据") {
        activity.disconnectLan(); activity.stopHid(); settings.resetAll()
        Toast.makeText(ctx, "已清除全部本地数据并恢复默认设置", Toast.LENGTH_SHORT).show()
        activity.refreshLegacyStatus()
    })

    val scroll = ScrollView(ctx).apply { addView(box) }
    AlertDialog.Builder(ctx)
        .setTitle("设置")
        .setView(scroll)
        .setNegativeButton("关闭", null)
        .show()
}

// ---------------- 键盘对话框 ----------------

fun showLegacyKeyboardDialog(
    activity: MainActivity,
    settings: SettingsStore,
    dispatcher: InputDispatcher
) {
    val ctx = activity
    val lanMode = settings.getString(SettingsStore.Keys.MODE, "hid") == "lan"
    val sticky = mutableSetOf<String>()

    fun pressChar(c: Char) {
        if (sticky.isEmpty()) dispatcher.keyChar(c) else dispatcher.keyChar(c, sticky)
        if (sticky.contains("shift")) sticky.remove("shift")
    }

    fun pressCombo(combo: String) {
        val translated = when (combo) {
            "pgup" -> "pageup"; "pgdn" -> "pagedown"; "del" -> "delete"
            else -> combo
        }
        dispatcher.specialKey(if (sticky.isEmpty()) translated else (sticky + translated).joinToString("+"))
        sticky.clear()
    }

    val box = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpF(ctx, 12), dpF(ctx, 8), dpF(ctx, 12), dpF(ctx, 4))
    }

    if (lanMode) {
        val textEdit = legacyEdit(ctx, "输入文本（直传，支持中文）")
        box.addView(legacyRow(ctx,
            textEdit,
            legacyButton(ctx, "发送") {
                if (textEdit.text.isNotEmpty()) {
                    dispatcher.text(textEdit.text.toString())
                    textEdit.setText("")
                }
            }
        ))
    }

    fun keyRow(labels: List<Pair<String, () -> Unit>>) {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        labels.forEach { (label, action) ->
            val w = if (label.length <= 1) 1f else 1.4f
            row.addView(legacyButton(ctx, label, action),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)
                    .apply { marginEnd = dpF(ctx, 4) })
        }
        box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpF(ctx, 6) })
    }

    fun charRow(chars: String) = keyRow(chars.map { ch -> ch.toString() to { pressChar(ch) } })

    keyRow(listOf(
        "esc" to { pressCombo("esc") }, "tab" to { pressCombo("tab") },
        "home" to { pressCombo("home") }, "end" to { pressCombo("end") },
        "pgup" to { pressCombo("pgup") }, "pgdn" to { pressCombo("pgdn") },
        "del" to { pressCombo("del") }
    ))
    charRow("1234567890")
    charRow("qwertyuiop")
    charRow("asdfghjkl")
    keyRow(listOf(
        "⇧" to { if (sticky.contains("shift")) sticky.remove("shift") else sticky.add("shift") },
        "z" to { pressChar('z') }, "x" to { pressChar('x') }, "c" to { pressChar('c') },
        "v" to { pressChar('v') }, "b" to { pressChar('b') }, "n" to { pressChar('n') },
        "m" to { pressChar('m') }, "," to { pressChar(',') }, "." to { pressChar('.') },
        "⌫" to { pressCombo("backspace") }
    ))
    keyRow(listOf(
        "Ctrl" to { if (sticky.contains("ctrl")) sticky.remove("ctrl") else sticky.add("ctrl") },
        "⌥" to { if (sticky.contains("alt")) sticky.remove("alt") else sticky.add("alt") },
        "⌘" to { if (sticky.contains("cmd")) sticky.remove("cmd") else sticky.add("cmd") },
        "space" to { pressChar(' ') },
        "⏎" to { pressCombo("enter") }
    ))
    keyRow(listOf(
        "←" to { pressCombo("left") }, "↑" to { pressCombo("up") },
        "↓" to { pressCombo("down") }, "→" to { pressCombo("right") }
    ))

    val scroll = ScrollView(ctx).apply { addView(box) }
    AlertDialog.Builder(ctx)
        .setTitle("键盘（输入发送到已连接的电脑）")
        .setView(scroll)
        .setNegativeButton("收起", null)
        .show()
}
