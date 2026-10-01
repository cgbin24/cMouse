package com.cmouse.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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
class PadUiHolder(
    val root: FrameLayout,
    val statusPill: TextView,
    val versionName: String,
    private val settings: SettingsStore,
    private val hintView: TextView
) {
    private val isDark get() = (root.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideHint = Runnable { hintView.visibility = View.GONE }

    /** 手势提示（滚动/缩放/拖拽），900ms 后自动消失。 */
    fun showGestureHint(text: String) {
        hintView.text = text
        hintView.visibility = View.VISIBLE
        uiHandler.removeCallbacks(hideHint)
        uiHandler.postDelayed(hideHint, 900)
    }

    /** 刷新状态胶囊文案 + 连接状态圆点。 */
    fun updateStatusPill(status: ConnectionStatus) {
        statusPill.text = padStatusText(status, settings, versionName)
        val lanMode = settings.getString(SettingsStore.Keys.MODE, "hid") == "lan"
        val connected = if (lanMode) status.lanState == LanClient.State.READY
        else (status.hidRegistered && status.hostName != null)
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (connected) 0xFF34C759.toInt() else 0xFF8E8E93.toInt())
            setSize((8 * root.resources.displayMetrics.density).toInt(),
                (8 * root.resources.displayMetrics.density).toInt())
        }
        statusPill.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
        statusPill.compoundDrawablePadding = (6 * root.resources.displayMetrics.density).toInt()
    }
}

fun buildPadUi(
    context: Context,
    settings: SettingsStore,
    dispatcher: InputDispatcher,
    onOpenKeyboard: () -> Unit,
    onOpenSettings: () -> Unit
): PadUiHolder {
    val dn = context.resources.displayMetrics.density
    fun dp(v: Int) = (v * dn + 0.5f).toInt()
    val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val surfaceColor = (if (isDark) 0xE62C2C2E else 0xE6FFFFFF).toInt()
    val textColor = (if (isDark) 0xFFF5F5F7 else 0xFF1D1D1F).toInt()
    val strokeColor = (if (isDark) 0xFF48484A else 0xFFE5E5EA).toInt()
    fun pillBg(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(18).toFloat()
        setColor(surfaceColor)
        setStroke(dp(1), strokeColor)
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
        setTextColor(textColor)
        textSize = 13f
        setPadding(dp(14), dp(8), dp(14), dp(8))
    }
    root.addView(
        statusPill,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = dp(14) }
    )

    // 底部中央：左键 / 右键
    fun clickPill(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        background = pillBg()
        setTextColor(textColor)
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
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dp(26) }
    )

    // 左下 / 右下：键盘 / 设置 圆钮
    fun fab(iconRes: Int, diagMsg: String, onClick: () -> Unit): FrameLayout = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(surfaceColor)
            setStroke(dp(1), strokeColor)
        }
        addView(
            ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(textColor)
            },
            FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)
        )
        setOnClickListener {
            dispatcher.onActionSent?.invoke(diagMsg)
            onClick()
        }
    }
    root.addView(
        fab(R.drawable.ic_keyboard, "✓ 已点击键盘按钮", onOpenKeyboard),
        FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.START)
            .apply { leftMargin = dp(20); bottomMargin = dp(20) }
    )
    root.addView(
        fab(R.drawable.ic_settings, "✓ 已点击设置按钮", onOpenSettings),
        FrameLayout.LayoutParams(dp(52), dp(52), Gravity.BOTTOM or Gravity.END)
            .apply { rightMargin = dp(20); bottomMargin = dp(20) }
    )

    // 手势状态提示（滚动/缩放）
    val hintView = TextView(context).apply {
        background = pillBg()
        setTextColor(textColor)
        textSize = 13f
        setPadding(dp(16), dp(8), dp(16), dp(8))
        visibility = View.GONE
    }
    root.addView(
        hintView,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dp(92) }
    )

    // 首次使用手势引导（点按任意处关闭，仅显示一次）
    if (!settings.getBool(SettingsStore.Keys.GUIDE_SHOWN, false)) {
        val guideText = TextView(context).apply {
            text = "欢迎使用 cMouse 触控板\n\n" +
                "单指：移动 · 轻点=左键 · 双击=双击 · 长按=拖拽\n" +
                "双指：滚动 · 轻点=右键 · 捏合=缩放\n" +
                "三指：滑动触发多任务动作\n\n" +
                "点按任意位置开始"
            setTextColor(0xFFF5F5F7.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        val guide = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(0xE6101014.toInt()) }
            isClickable = true
        }
        guide.addView(
            guideText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        guide.setOnClickListener {
            settings.putBool(SettingsStore.Keys.GUIDE_SHOWN, true)
            root.removeView(guide)
        }
        root.addView(
            guide,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }
    return PadUiHolder(root, statusPill, versionName, settings, hintView)
}

/** 顶部胶囊的状态文案（在 AndroidView 的 update 中随 Compose 状态刷新）。 */
fun padStatusText(status: ConnectionStatus, settings: SettingsStore, versionName: String = ""): String {
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
    } + (if (versionName.isNotEmpty()) " · v$versionName" else "")
}
