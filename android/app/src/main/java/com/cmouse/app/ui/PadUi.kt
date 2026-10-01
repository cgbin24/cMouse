package com.cmouse.app.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.cmouse.app.ConnectionStatus
import com.cmouse.app.R
import com.cmouse.app.data.SettingsStore
import com.cmouse.app.input.InputDispatcher
import com.cmouse.app.transport.LanClient

/**
 * 主界面原生控件容器：触控板 + 状态胶囊 + 左/右键 + 键盘/设置圆钮。
 * 控件必须是 TrackpadView 的兄弟原生 View（View 体系子控件优先分发触摸），
 * 不能用 Compose 悬浮层——Compose 1.7 interop 会把触摸先派发给触控板 View。
 */
class PadUiHolder(val root: FrameLayout, val statusPill: TextView)

fun buildPadUi(
    context: Context,
    settings: SettingsStore,
    dispatcher: InputDispatcher,
    onOpenKeyboard: () -> Unit,
    onOpenSettings: () -> Unit
): PadUiHolder {
    val dn = context.resources.displayMetrics.density
    fun dp(v: Int) = (v * dn + 0.5f).toInt()
    fun pillBg(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(0xE6FFFFFF.toInt())
        setStroke(dp(1), 0xFFE5E5EA.toInt())
    }

    val root = FrameLayout(context)

    // 整屏触控面
    val pad = TrackpadView(context, settings, dispatcher)
    root.addView(
        pad,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    // 顶部状态胶囊
    val statusPill = TextView(context).apply {
        background = pillBg()
        setTextColor(0xFF1D1D1F.toInt())
        textSize = 13f
        setPadding(dp(14), dp(8), dp(14), dp(8))
    }
    root.addView(
        statusPill,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, dp(14), 0, 0
        )
    )

    // 底部中央：左键 / 右键
    fun clickPill(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        background = pillBg()
        setTextColor(0xFF1D1D1F.toInt())
        textSize = 14f
        setPadding(dp(26), dp(11), dp(26), dp(11))
        setOnClickListener { onClick() }
    }
    val btnRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    btnRow.addView(clickPill("左键") { dispatcher.click(InputDispatcher.BUTTON_LEFT, false) })
    btnRow.addView(
        clickPill("右键") { dispatcher.click(InputDispatcher.BUTTON_RIGHT, false) },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(14) }
    )
    root.addView(
        btnRow,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 0, 0, dp(26)
        )
    )

    // 左下 / 右下：键盘 / 设置 圆钮
    fun fab(iconRes: Int, onClick: () -> Unit): FrameLayout = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xE6FFFFFF.toInt())
            setStroke(dp(1), 0xFFE5E5EA.toInt())
        }
        addView(
            ImageView(context).apply { setImageResource(iconRes) },
            FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
        )
        setOnClickListener { onClick() }
    }
    root.addView(
        fab(R.drawable.ic_keyboard, onOpenKeyboard),
        FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.START, dp(20), 0, 0, dp(20))
    )
    root.addView(
        fab(R.drawable.ic_settings, onOpenSettings),
        FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END, dp(20), 0, 0, dp(20))
    )

    return PadUiHolder(root, statusPill)
}

/** 顶部胶囊的状态文案（在 AndroidView 的 update 中随 Compose 状态刷新）。 */
fun padStatusText(status: ConnectionStatus, settings: SettingsStore): String {
    val lanMode = settings.getString(SettingsStore.Keys.MODE, "hid") == "lan"
    val connected = if (lanMode) status.lanState == LanClient.State.READY
    else (status.hidRegistered && status.hostName != null)
    return when {
        lanMode && status.lanState == LanClient.State.READY -> "Wi-Fi · ${status.hostName ?: "接收端"}"
        lanMode && status.lanMsg.isNotEmpty() -> status.lanMsg
        lanMode -> "Wi-Fi · 未连接"
        status.hidMsg.isNotEmpty() -> status.hidMsg
        !status.hidRegistered -> "蓝牙 · 未注册"
        else -> "蓝牙 · ${status.hostName ?: "已配对"}"
    }
}
