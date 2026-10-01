package com.cmouse.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
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

    // 注意：sink 必须先于 engine 声明（engine 的初始化器引用它）
    private val sink = object : GestureEngine.Sink {
        override fun move(dx: Float, dy: Float) = dispatcher.move(dx, dy)
        override fun click(button: Int, double: Boolean) = dispatcher.click(button, double)
        override fun buttonDown(button: Int) = dispatcher.button(button, true)
        override fun buttonUp(button: Int) = dispatcher.button(button, false)
        override fun scroll(dx: Float, dy: Float) {
            dispatcher.scroll(dx, dy)
            emitHint("双指滚动")
        }
        override fun momentumTick(dx: Float, dy: Float) = dispatcher.scroll(dx, dy)
        override fun zoom(zoomPct: Float) {
            dispatcher.zoom(zoomPct)
            emitHint("捏合缩放")
        }
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
        if (rippleAlpha > 0f) {
            ripplePaint.alpha = (140 * rippleAlpha).toInt()
            canvas.drawCircle(rippleX, rippleY, dp(24f), ripplePaint)
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

    private var lastTouchDiag = 0L

    /** 手势提示回调（滚动/缩放开始时触发，由 PadUi 显示文字提示）。 */
    var onGestureHint: ((String) -> Unit)? = null
    private var lastHint = ""
    private var lastHintTime = 0L
    private fun emitHint(text: String) {
        val now = System.currentTimeMillis()
        if (text == lastHint && now - lastHintTime < 1200) return
        lastHint = text; lastHintTime = now
        onGestureHint?.invoke(text)
    }

    // 触摸涟漪：触点显示淡蓝圆点，抬手淡出
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x28007AFF }
    private var rippleX = 0f; private var rippleY = 0f
    private var rippleAlpha = 1f
    private val rippleFade = object : Runnable {
        override fun run() {
            rippleAlpha -= 0.15f
            if (rippleAlpha > 0f) { invalidate(); postDelayed(this, 16) } else invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 诊断：节流上报"触控板确实收到了触摸"，用于排查整机触摸失效问题
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastTouchDiag > 1500) {
                lastTouchDiag = now
                dispatcher.onActionSent?.invoke("✓ 触控板已收到触摸")
            }
            removeCallbacks(rippleFade)
            rippleX = event.x; rippleY = event.y; rippleAlpha = 1f
            invalidate()
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                rippleX = event.x; rippleY = event.y; invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> postDelayed(rippleFade, 16)
        }
        detector.onTouchEvent(event)
        return engine.onTouchEvent(event)
    }

    override fun performHapticFeedback(feedbackConstant: Int): Boolean =
        super.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}
