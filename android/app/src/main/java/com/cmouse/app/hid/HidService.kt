package com.cmouse.app.hid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder

/**
 * 前台服务：保持 HID 设备注册不被系统回收。
 * 前台服务仅为保活，无任何后台数据行为。
 */
class HidService : Service() {

    /**
     * 仅 Android 9+ 才会构造（BluetoothHidDevice 相关类在 API<28 上不存在，
     * 过早构造会在类加载时抛 NoClassDefFoundError，已在 Android 6.0 实测复现）。
     */
    var hid: HidDeviceManager? = null
        private set

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: HidService get() = this@HidService
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            hid = HidDeviceManager(applicationContext, object : HidDeviceManager.Listener {
                override fun onRegistered(registered: Boolean) {
                    if (registered) lastError = null
                    statusListener?.onRegistered(registered)
                }

                override fun onHostChanged(name: String?, connected: Boolean) {
                    statusListener?.onHostChanged(name, connected)
                }

                override fun onError(message: String) {
                    // 绑定发生前产生的错误先缓存，绑定后由 Activity 补发
                    lastError = message
                    statusListener?.onError(message)
                }
            })
            try {
                hid?.start()
            } catch (e: Throwable) {
                lastError = "启动异常：${e.javaClass.simpleName}: ${e.message ?: ""}"
                statusListener?.onError(lastError!!)
            }
        } else {
            lastError = "蓝牙直连模式需要 Android 9.0 以上（当前 Android ${Build.VERSION.RELEASE}），请改用 Wi-Fi 接收端模式"
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        hid?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    var statusListener: HidDeviceManager.Listener? = null

    /** 绑定前产生的最后一次错误，绑定后补发。 */
    var lastError: String? = null
        private set

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "cMouse 蓝牙触控板", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n: Notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("cMouse 正在运行")
                    .setContentText("蓝牙触控板已就绪，可在电脑端配对")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setOngoing(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setContentTitle("cMouse 正在运行")
                    .setContentText("蓝牙触控板已就绪")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setOngoing(true)
                    .build()
            }
        startForeground(NOTIFICATION_ID, n)
    }

    companion object {
        private const val CHANNEL_ID = "cmouse_hid"
        private const val NOTIFICATION_ID = 1
    }
}
