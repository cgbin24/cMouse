package com.cmouse.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地数据：全部存 SQLite（应用私有目录，不上传）。
 *  - settings  键值配置（灵敏度、模式、接收端地址等）
 *  - gestures  自定义手势映射（手势 -> 动作 + 参数）
 *  - devices   已配对主机记录
 */
class SettingsStore(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    interface Keys {
        companion object {
            const val MODE = "mode"                    // "hid" | "lan"
            const val SENSITIVITY = "sensitivity"      // 0.5 ~ 3.0
            const val SCROLL_SPEED = "scroll_speed"    // 0.3 ~ 3.0
            const val PINCH_ZOOM = "pinch_zoom"        // "1"/"0"
            const val LAN_HOST = "lan_host"
            const val LAN_PORT = "lan_port"
            const val LAN_CODE = "lan_code"
            const val THEME = "theme"                  // "light" | "dark" | "system"
            const val TAP_INTERVAL_MS = "tap_interval_ms"
            const val LONG_PRESS_MS = "long_press_ms"
            const val GESTURE_THREE_UP = "gesture_three_up"
            const val GESTURE_THREE_DOWN = "gesture_three_down"
            const val GESTURE_THREE_LEFT = "gesture_three_left"
            const val GESTURE_THREE_RIGHT = "gesture_three_right"
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE gestures(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                gesture TEXT NOT NULL UNIQUE,
                action TEXT NOT NULL,
                param TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL(
            """CREATE TABLE devices(
                mac TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                kind TEXT NOT NULL,          -- hid | lan
                last_seen INTEGER NOT NULL)"""
        )
        // 默认自定义手势映射
        fun ins(gesture: String, action: String, param: String) {
            db.insertWithOnConflict("gestures", null, ContentValues().apply {
                put("gesture", gesture); put("action", action); put("param", param)
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        ins("three_up", "combo", "ctrl+up")          // macOS 调度中心 / Win 任务视图
        ins("three_down", "combo", "win+d")          // 显示桌面
        ins("three_left", "combo", "ctrl+left")      // 全屏应用切换
        ins("three_right", "combo", "ctrl+right")
        setDefault(db, Keys.MODE, "hid")
        setDefault(db, Keys.SENSITIVITY, "1.0")
        setDefault(db, Keys.SCROLL_SPEED, "1.0")
        setDefault(db, Keys.PINCH_ZOOM, "1")
        setDefault(db, Keys.LAN_PORT, "8433")
        setDefault(db, Keys.LAN_CODE, "")
        setDefault(db, Keys.THEME, "system")
        setDefault(db, Keys.TAP_INTERVAL_MS, "280")
        setDefault(db, Keys.LONG_PRESS_MS, "600")
    }

    private fun setDefault(db: SQLiteDatabase, k: String, v: String) {
        db.insertWithOnConflict("settings", null, ContentValues().apply {
            put("key", k); put("value", v)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 结构简单，直接重建
        db.execSQL("DROP TABLE IF EXISTS settings")
        db.execSQL("DROP TABLE IF EXISTS gestures")
        db.execSQL("DROP TABLE IF EXISTS devices")
        onCreate(db)
    }

    // ---- settings ----
    fun getString(key: String, def: String = ""): String =
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else def
        }

    fun putString(key: String, value: String) {
        writableDatabase.insertWithOnConflict("settings", null, ContentValues().apply {
            put("key", key); put("value", value)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getFloat(key: String, def: Float): Float = getString(key, def.toString()).toFloatOrNull() ?: def
    fun putFloat(key: String, v: Float) = putString(key, v.toString())
    fun getBool(key: String, def: Boolean): Boolean =
        getString(key, if (def) "1" else "0") == "1"

    fun putBool(key: String, v: Boolean) = putString(key, if (v) "1" else "0")

    // ---- gestures ----
    fun gestureAction(gesture: String): Pair<String, String>? =
        readableDatabase.rawQuery("SELECT action, param FROM gestures WHERE gesture=?", arrayOf(gesture)).use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
        }

    fun setGestureAction(gesture: String, action: String, param: String) {
        writableDatabase.insertWithOnConflict("gestures", null, ContentValues().apply {
            put("gesture", gesture); put("action", action); put("param", param)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---- devices ----
    fun rememberDevice(mac: String, name: String, kind: String) {
        writableDatabase.insertWithOnConflict("devices", null, ContentValues().apply {
            put("mac", mac); put("name", name); put("kind", kind)
            put("last_seen", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun devices(): List<Triple<String, String, String>> =
        readableDatabase.rawQuery(
            "SELECT mac, name, kind FROM devices ORDER BY last_seen DESC", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getString(2)))
            }
        }

    companion object {
        private const val DB_NAME = "cmouse.db"
        private const val DB_VERSION = 1
    }
}
