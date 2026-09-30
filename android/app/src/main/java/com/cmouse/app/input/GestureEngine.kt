package com.cmouse.app.input

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/**
 * 触控板手势状态机：把手机屏幕上的多点触控翻译为触控板语义。
 *
 * 识别能力：
 *  - 单指：移动 / 轻点=左键 / 双击=双击 / 长按=拖拽
 *  - 双指：滚动（带惯性）/ 轻点=右键 / 捏合=缩放
 *  - 三指：滑动（映射为可配置系统动作）
 *
 * 阈值单位均为原始像素（px），由 View 层按密度换算后传入 Config。
 */
class GestureEngine(private val cfg: Config, private val sink: Sink) {

    data class Config(
        val slop: Float,            // 位移判定阈值
        val tapTimeoutMs: Long,     // 轻点时间上限
        val doubleTapMs: Long,      // 双击间隔
        val longPressMs: Long,      // 长按进入拖拽
        val scrollStepPx: Float,    // 每滚动 1 格对应的像素
        val zoomStepRatio: Float,   // 每缩放 1 格对应的跨度变化比例
        val swipeMinPx: Float       // 三指滑动触发最小位移
    )

    interface Sink {
        fun move(dx: Float, dy: Float)
        fun click(button: Int, double: Boolean)
        fun buttonDown(button: Int)
        fun buttonUp(button: Int)
        fun scroll(dx: Float, dy: Float)
        fun zoom(zoomPct: Float)
        fun swipe(fingers: Int, dir: SwipeDir)
        fun haptic()
        fun momentumTick(dx: Float, dy: Float)
        fun direction(dir: SwipeDir?)
    }

    private enum class Mode { IDLE, ONE, TWO, THREE, DRAG, SCROLL_MOMENTUM }

    private val handler = Handler(Looper.getMainLooper())
    private var mode = Mode.IDLE
    private var velocity: VelocityTracker? = null

    private var downX = 0f; private var downY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var downTime = 0L
    private var lastTapTime = 0L
    private var lastTapX = 0f; private var lastTapY = 0f
    private var pendingSingleTap = false
    private var twoFingerTapStart = 0L
    private var twoFingerMoved = false

    // 双指状态
    private var spanInit = 0f; private var spanLast = 0f
    private var twoInitX = 0f; private var twoInitY = 0f
    private var twoLastX = 0f; private var twoLastY = 0f
    private var pinchMode = false
    private var zoomAcc = 0f
    private var scrollAccX = 0f; private var scrollAccY = 0f

    // 三指状态
    private var threeInitX = 0f; private var threeInitY = 0f
    private var threeLastX = 0f; private var threeLastY = 0f

    // 惯性
    private var momentumVx = 0f; private var momentumVy = 0f
    private val momentumRunnable = object : Runnable {
        override fun run() {
            if (mode != Mode.SCROLL_MOMENTUM) return
            val dxTicks = momentumVx / 1000f * (1f / 60f) / cfg.scrollStepPx
            val dyTicks = momentumVy / 1000f * (1f / 60f) / cfg.scrollStepPx
            if (abs(dxTicks) > 0.02f || abs(dyTicks) > 0.02f) {
                sink.momentumTick(dxTicks, dyTicks)
                momentumVy *= 0.9f
                momentumVx *= 0.9f
                handler.postDelayed(this, 16)
            } else {
                mode = Mode.IDLE
            }
        }
    }

    private val longPressRunnable = Runnable {
        if (mode == Mode.ONE && dist(downX, downY, lastX, lastY) < cfg.slop) {
            mode = Mode.DRAG
            sink.haptic()
            sink.buttonDown(InputDispatcher.BUTTON_LEFT)
        }
    }

    private val singleTapRunnable = Runnable {
        if (pendingSingleTap) {
            pendingSingleTap = false
            sink.click(InputDispatcher.BUTTON_LEFT, false)
        }
    }

    fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> onTouchDown(e)
            MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(e)
            MotionEvent.ACTION_MOVE -> onTouchMove(e)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(e)
            MotionEvent.ACTION_UP -> onTouchUp(e)
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun onTouchDown(e: MotionEvent) {
        stopMomentum()
        mode = Mode.ONE
        downX = e.x; downY = e.y
        lastX = e.x; lastY = e.y
        downTime = e.eventTime
        velocity?.clear()
        velocity = VelocityTracker.obtain()
        velocity?.addMovement(e)
        sink.direction(null)
        handler.postDelayed(longPressRunnable, cfg.longPressMs)
    }

    private fun onPointerDown(e: MotionEvent) {
        when (e.pointerCount) {
            2 -> {
                if (mode == Mode.DRAG) return // 拖拽中忽略新手指
                handler.removeCallbacks(longPressRunnable)
                mode = Mode.TWO
                twoFingerTapStart = e.eventTime
                twoFingerMoved = false
                twoInitX = (e.getX(0) + e.getX(1)) / 2f
                twoInitY = (e.getY(0) + e.getY(1)) / 2f
                twoLastX = twoInitX; twoLastY = twoInitY
                spanInit = hypot((e.getX(0) - e.getX(1)).toDouble(), (e.getY(0) - e.getY(1)).toDouble()).toFloat()
                spanLast = spanInit
                pinchMode = false
                scrollAccX = 0f; scrollAccY = 0f
                zoomAcc = 0f
                velocity?.addMovement(e)
            }
            3 -> {
                mode = Mode.THREE
                threeInitX = centroidX(e); threeInitY = centroidY(e)
                threeLastX = threeInitX; threeLastY = threeInitY
                velocity?.addMovement(e)
            }
            else -> mode = Mode.IDLE
        }
    }

    private fun onTouchMove(e: MotionEvent) {
        velocity?.addMovement(e)
        when (mode) {
            Mode.ONE -> {
                val dx = e.x - lastX; val dy = e.y - lastY
                lastX = e.x; lastY = e.y
                if (dist(downX, downY, e.x, e.y) > cfg.slop) handler.removeCallbacks(longPressRunnable)
                updateDirection(dx, dy)
                sink.move(dx, dy)
            }
            Mode.DRAG -> {
                val dx = e.x - lastX; val dy = e.y - lastY
                lastX = e.x; lastY = e.y
                updateDirection(dx, dy)
                sink.move(dx, dy)
            }
            Mode.TWO -> {
                val cx = (e.getX(0) + e.getX(1)) / 2f
                val cy = (e.getY(0) + e.getY(1)) / 2f
                val span = hypot((e.getX(0) - e.getX(1)).toDouble(), (e.getY(0) - e.getY(1)).toDouble()).toFloat()

                val movedX = cx - twoLastX; val movedY = cy - twoLastY
                if (dist(cx, cy, twoInitX, twoInitY) > cfg.slop * 1.5f) twoFingerMoved = true

                if (!pinchMode) {
                    val spanRatio = if (spanInit > 0f) span / spanInit else 1f
                    if (abs(spanRatio - 1f) > cfg.zoomStepRatio) {
                        pinchMode = true
                        twoFingerMoved = true
                    }
                }

                if (pinchMode) {
                    // 缩放：跨度每变化 zoomStepRatio 记 1 格
                    if (spanInit > 0f) zoomAcc += (span - spanLast) / spanInit
                    while (abs(zoomAcc) >= cfg.zoomStepRatio) {
                        sink.zoom(sign(zoomAcc) * cfg.zoomStepRatio * 100f)
                        zoomAcc -= sign(zoomAcc) * cfg.zoomStepRatio
                    }
                } else {
                    // 滚动：两指平均位移量化为滚轮格
                    scrollAccX += movedX / cfg.scrollStepPx
                    scrollAccY += movedY / cfg.scrollStepPx
                    val tx = scrollAccX.toInt(); val ty = scrollAccY.toInt()
                    if (tx != 0 || ty != 0) {
                        scrollAccX -= tx; scrollAccY -= ty
                        sink.scroll(tx.toFloat(), ty.toFloat())
                    }
                }
                if (abs(movedX) > 0.5f || abs(movedY) > 0.5f) updateDirection(movedX, movedY)
                twoLastX = cx; twoLastY = cy
                spanLast = span
            }
            Mode.THREE -> {
                threeLastX = centroidX(e); threeLastY = centroidY(e)
                updateDirection(threeLastX - threeInitX, threeLastY - threeInitY)
            }
            else -> {}
        }
    }

    private fun onPointerUp(e: MotionEvent) {
        if (e.pointerCount == 3 && mode == Mode.THREE) {
            // 计算抬指前最后一个完整三指位置，避免使用已失效的指针坐标。
            threeSwipeDecide(threeLastX, threeLastY)
            reset()
        }
    }

    private fun onTouchUp(e: MotionEvent) {
        handler.removeCallbacks(longPressRunnable)
        val elapsed = e.eventTime - downTime
        when (mode) {
            Mode.ONE -> {
                if (elapsed <= cfg.tapTimeoutMs && dist(downX, downY, e.x, e.y) < cfg.slop) {
                    val now = e.eventTime
                    val isDouble = now - lastTapTime < cfg.doubleTapMs &&
                        dist(lastTapX, lastTapY, e.x, e.y) < cfg.slop * 2
                    if (isDouble) {
                        handler.removeCallbacks(singleTapRunnable)
                        pendingSingleTap = false
                        sink.click(InputDispatcher.BUTTON_LEFT, true)
                    } else {
                        pendingSingleTap = true
                        handler.postDelayed(singleTapRunnable, cfg.doubleTapMs)
                    }
                    lastTapTime = now
                    lastTapX = e.x; lastTapY = e.y
                }
            }
            Mode.DRAG -> {
                sink.buttonUp(InputDispatcher.BUTTON_LEFT)
            }
            Mode.TWO -> {
                val tapOk = e.eventTime - twoFingerTapStart < cfg.tapTimeoutMs + 60 &&
                    !twoFingerMoved && !pinchMode
                if (tapOk) {
                    sink.click(InputDispatcher.BUTTON_RIGHT, false)
                } else {
                    // 结束滚动，启动惯性
                    if (!pinchMode) {
                        velocity?.computeCurrentVelocity(1000)
                        momentumVy = velocity?.yVelocity ?: 0f
                        momentumVx = velocity?.xVelocity ?: 0f
                    }
                    if (!pinchMode && abs(momentumVy) + abs(momentumVx) > 300f) {
                        mode = Mode.SCROLL_MOMENTUM
                        handler.post(momentumRunnable)
                    }
                }
            }
            Mode.THREE -> threeSwipeDecide(threeLastX, threeLastY)
            else -> {}
        }
        velocity?.recycle()
        velocity = null
        sink.direction(null)
        if (mode != Mode.SCROLL_MOMENTUM) mode = Mode.IDLE
    }

    private fun threeSwipeDecide(e: MotionEvent) {
        threeSwipeDecide(centroidX(e), centroidY(e))
    }

    private fun threeSwipeDecide(cx: Float, cy: Float) {
        val dx = cx - threeInitX; val dy = cy - threeInitY
        if (hypot(dx.toDouble(), dy.toDouble()) < cfg.swipeMinPx) return
        val dir = if (abs(dy) > abs(dx)) {
            if (dy < 0) SwipeDir.UP else SwipeDir.DOWN
        } else {
            if (dx < 0) SwipeDir.LEFT else SwipeDir.RIGHT
        }
        sink.swipe(3, dir)
    }

    private fun stopMomentum() {
        handler.removeCallbacks(momentumRunnable)
    }

    fun reset() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(singleTapRunnable)
        stopMomentum()
        velocity?.recycle()
        velocity = null
        mode = Mode.IDLE
        sink.direction(null)
    }

    private fun centroidX(e: MotionEvent): Float {
        var s = 0f; for (i in 0 until e.pointerCount) s += e.getX(i); return s / e.pointerCount
    }

    private fun centroidY(e: MotionEvent): Float {
        var s = 0f; for (i in 0 until e.pointerCount) s += e.getY(i); return s / e.pointerCount
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()

    private fun updateDirection(dx: Float, dy: Float) {
        if (abs(dx) < 0.5f && abs(dy) < 0.5f) return
        sink.direction(
            if (abs(dx) > abs(dy)) {
                if (dx < 0f) SwipeDir.LEFT else SwipeDir.RIGHT
            } else {
                if (dy < 0f) SwipeDir.UP else SwipeDir.DOWN
            }
        )
    }
}
