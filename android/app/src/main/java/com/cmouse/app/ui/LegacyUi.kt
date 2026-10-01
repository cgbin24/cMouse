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

    box.addView(legacyLabel(ctx, "连接模式（当前：${if (mode == "hid") "蓝牙直连" else "Wi-Fi 接收端"}）"))
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
    box.addView(legacyRow(ctx, naturalBtn, pinchBtn))

    box.addView(legacyLabel(ctx, "数据"))
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
