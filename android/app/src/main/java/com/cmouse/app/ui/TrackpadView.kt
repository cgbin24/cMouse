package com.cmouse.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.graphics.RectF
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

    // 注意：sink 必须先于 engine 声明（engine 的初始化器引用它）
    private val sink = object : GestureEngine.Sink {
        override fun move(dx: Float, dy: Float) = dispatcher.move(dx, dy)
        override fun click(button: Int, double: Boolean) = dispatcher.click(button, double)
        override fun buttonDown(button: Int) = dispatcher.button(button, true)
        override fun buttonUp(button: Int) = dispatcher.button(button, false)
        override fun scroll(dx: Float, dy: Float) = dispatcher.scroll(dx, dy)
        override fun momentumTick(dx: Float, dy: Float) = dispatcher.scroll(dx, dy)
        override fun zoom(zoomPct: Float) = dispatcher.zoom(zoomPct)
        override fun swipe(fingers: Int, dir: SwipeDir) = dispatcher.swipe(fingers, dir)
        override fun haptic() {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        override fun direction(dir: SwipeDir?) {
            activeDirection = dir
            invalidate()
        }
        override fun dragging(active: Boolean) {
            isDragging = active
            invalidate()
        }
    }

    private var activeDirection: SwipeDir? = null
    private var isDragging = false
    private val directionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = dp(2f)
    }
    private val dragPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.argb(220, 45, 135, 235)
    }
    private val dragTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(13f)
        color = Color.argb(220, 45, 135, 235)
    }

    private val engine = GestureEngine(
        GestureEngine.Config(
            slop = dp(12f),
            tapTimeoutMs = settings.getFloat(SettingsStore.Keys.TAP_INTERVAL_MS, 280f).toLong(),
            doubleTapMs = 320L,
            longPressMs = settings.getFloat(SettingsStore.Keys.LONG_PRESS_MS, 600f).toLong(),
            scrollStepPx = dp(18f),
            zoomStepRatio = 0.08f,
            swipeMinPx = dp(44f)
        ),
        sink
    )

    // 触控板表面不要长按弹菜单等系统行为
    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onLongPress(e: MotionEvent) {}
    })

    init {
        // 保持屏幕常亮：触控板使用中不应锁屏
        keepScreenOn = true
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = dp(26f)
        drawDirection(canvas, SwipeDir.UP, width / 2f, inset)
        drawDirection(canvas, SwipeDir.DOWN, width / 2f, height - inset)
        drawDirection(canvas, SwipeDir.LEFT, inset, height / 2f)
        drawDirection(canvas, SwipeDir.RIGHT, width - inset, height / 2f)
        if (isDragging) {
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(cx, cy, dp(30f), dragPaint)
            canvas.drawText("拖动中", cx, cy + dp(5f), dragTextPaint)
        }
    }

    private fun drawDirection(canvas: Canvas, dir: SwipeDir, x: Float, y: Float) {
        directionPaint.color = if (activeDirection == dir) Color.argb(220, 45, 135, 235)
        else Color.argb(70, 90, 100, 115)
        val size = dp(11f)
        val path = Path()
        when (dir) {
            SwipeDir.UP -> {
                path.moveTo(x, y - size); path.lineTo(x - size, y); path.moveTo(x, y - size); path.lineTo(x + size, y)
            }
            SwipeDir.DOWN -> {
                path.moveTo(x, y + size); path.lineTo(x - size, y); path.moveTo(x, y + size); path.lineTo(x + size, y)
            }
            SwipeDir.LEFT -> {
                path.moveTo(x - size, y); path.lineTo(x, y - size); path.moveTo(x - size, y); path.lineTo(x, y + size)
            }
            SwipeDir.RIGHT -> {
                path.moveTo(x + size, y); path.lineTo(x, y - size); path.moveTo(x + size, y); path.lineTo(x, y + size)
            }
        }
        canvas.drawPath(path, directionPaint)
    }

    /** 悬浮按钮/胶囊区域（根坐标 px）：这些区域的开屏触摸让给上层 Compose 按钮。 */
    var ignoreAreas: List<RectF> = emptyList()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Compose 1.7 interop 在 Initial pass 先派发给 View，View 一旦消费，
        // 上层悬浮按钮的点击就收不到事件——按钮区域必须主动让出。
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            ignoreAreas.any { it.contains(event.x, event.y) }
        ) return false
        detector.onTouchEvent(event)
        return engine.onTouchEvent(event)
    }

    override fun performHapticFeedback(feedbackConstant: Int): Boolean =
        super.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}
