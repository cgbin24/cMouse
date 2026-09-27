package com.cmouse.app.ui

import android.content.Context
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.cmouse.app.data.SettingsStore
import com.cmouse.app.input.GestureEngine
import com.cmouse.app.input.InputDispatcher
import com.cmouse.app.input.SwipeDir

/**
 * 触控板表面 View：把触控事件交给 GestureEngine，
 * 引擎产出的语义事件写入 InputDispatcher。
 */
class TrackpadView(
    context: Context,
    private val settings: SettingsStore,
    private val dispatcher: InputDispatcher
) : View(context) {

    private val density = resources.displayMetrics.density
    private val dp: (Float) -> Float = { it * density }

    private val engine = GestureEngine(
        GestureEngine.Config(
            slop = dp(12f),
            tapTimeoutMs = settings.getFloat(SettingsStore.Keys.TAP_INTERVAL_MS, 280f).toLong(),
            doubleTapMs = 320L,
            longPressMs = settings.getFloat(SettingsStore.Keys.LONG_PRESS_MS, 600f).toLong(),
            scrollStepPx = dp(18f),
            zoomStepRatio = 0.08f,
            swipeMinPx = dp(64f)
        ),
        sink
    )

    // 触控板表面不要长按弹菜单等系统行为
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onLongPress(e: MotionEvent) {}
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        return engine.onTouchEvent(event)
    }

    override fun performHapticFeedback(feedbackConstant: Int): Boolean =
        super.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

    private val sink = object : GestureEngine.Sink {
        override fun move(dx: Float, dy: Float) = dispatcher.move(dx, dy)
        override fun click(button: Int, double: Boolean) = dispatcher.click(button, double)
        override fun buttonDown(button: Int) = dispatcher.button(button, true)
        override fun buttonUp(button: Int) = dispatcher.button(button, false)
        override fun scroll(dx: Float, dy: Float) = dispatcher.scroll(dx, dy)
        override fun momentumTick(dy: Float) = dispatcher.scroll(0f, dy)
        override fun zoom(zoomPct: Float) = dispatcher.zoom(zoomPct)
        override fun swipe(fingers: Int, dir: SwipeDir) = dispatcher.swipe(fingers, dir)
        override fun haptic() {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    init {
        // 保持屏幕常亮：触控板使用中不应锁屏
        keepScreenOn = true
    }
}
