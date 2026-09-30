package com.cmouse.app.transport

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 模式 B：Wi-Fi 局域网直连桌面接收端。
 * 协议：TCP，换行分隔 JSON（见 docs/protocol.md）。
 * 安全：连接时先发送配对码，接收端校验通过后才接受输入事件。
 */
class LanClient(
    private val host: String,
    private val port: Int,
    private val deviceName: String,
    private val listener: Listener
) {
    interface Listener {
        fun onState(state: State, message: String = "")
    }

    enum class State { CONNECTING, PAIRING, READY, ERROR, CLOSED }

    private val running = AtomicBoolean(false)
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private val sendLock = Any()

    fun start(pairCode: String) {
        if (!running.compareAndSet(false, true)) return
        Thread({
            try {
                listener.onState(State.CONNECTING)
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 4000)
                s.tcpNoDelay = true
                socket = s
                writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))

                // 读取线程
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                Thread({
                    try {
                        while (running.get()) {
                            val line = reader.readLine() ?: break
                            val json = JSONObject(line)
                            when (json.optString("t")) {
                                "pair-ok" -> listener.onState(State.READY)
                                "pair-fail" -> {
                                    val lock = json.optInt("lock", 0)
                                    listener.onState(
                                        State.ERROR,
                                        if (lock > 0) "配对码错误次数过多，已锁定 ${lock} 秒" else "配对码错误"
                                    )
                                }
                                "pong" -> {}
                            }
                        }
                    } catch (_: Exception) {
                    }
                    if (running.get()) {
                        running.set(false)
                        listener.onState(State.CLOSED, "连接已断开")
                    }
                }, "cmouse-recv").start()

                // 发送配对请求
                listener.onState(State.PAIRING)
                send(JSONObject().apply {
                    put("t", "hello")
                    put("name", deviceName)
                    put("code", pairCode)
                    put("proto", 1)
                })
            } catch (e: Exception) {
                running.set(false)
                listener.onState(State.ERROR, "连接失败：${e.message ?: e.javaClass.simpleName}")
            }
        }, "cmouse-lan").start()
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        listener.onState(State.CLOSED)
    }

    val isReady: Boolean get() = running.get() && socket?.isConnected == true

    fun send(json: JSONObject) {
        if (!isReady) return
        synchronized(sendLock) {
            try {
                writer?.write(json.toString())
                writer?.write("\n")
                writer?.flush()
            } catch (e: Exception) {
                Log.w("cMouse", "send failed: ${e.message}")
            }
        }
    }

    fun move(dx: Float, dy: Float) = send(JSONObject().put("t", "move").put("dx", dx.round1()).put("dy", dy.round1()))
    fun abs(x: Float, y: Float) = send(JSONObject().put("t", "abs").put("x", x.coerceIn(0f, 1f).round3()).put("y", y.coerceIn(0f, 1f).round3()))
    fun click(btn: String, double: Boolean) = send(JSONObject().put("t", "click").put("btn", btn).put("dbl", double))
    fun button(btn: String, down: Boolean) = send(JSONObject().put("t", "btn").put("btn", btn).put("down", down))
    fun scroll(dx: Float, dy: Float) = send(JSONObject().put("t", "scroll").put("dx", dx.round1()).put("dy", dy.round1()))
    fun zoom(deltaTicks: Int) = send(JSONObject().put("t", "zoom").put("d", deltaTicks))
    fun combo(keys: String) = send(JSONObject().put("t", "key").put("keys", keys))
    fun text(s: String) = send(JSONObject().put("t", "text").put("text", s))
    fun key(code: String) = send(JSONObject().put("t", "key").put("keys", code))

    private fun Float.round1(): Double = Math.round(this * 10.0) / 10.0
    private fun Float.round3(): Double = Math.round(this * 1000.0) / 1000.0
}
