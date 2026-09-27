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

    lateinit var hid: HidDeviceManager
        private set

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: HidService get() = this@HidService
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        hid = HidDeviceManager(applicationContext, object : HidDeviceManager.Listener {
            override fun onRegistered(registered: Boolean) {
                statusListener?.onRegistered(registered)
            }

            override fun onHostChanged(name: String?, connected: Boolean) {
                statusListener?.onHostChanged(name, connected)
            }

            override fun onError(message: String) {
                statusListener?.onError(message)
            }
        })
        hid.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        hid.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    var statusListener: HidDeviceManager.Listener? = null

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
