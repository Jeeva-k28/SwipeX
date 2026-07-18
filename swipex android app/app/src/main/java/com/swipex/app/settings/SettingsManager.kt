package com.swipex.app.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("SwipeXSettings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_CURSOR_SPEED = "cursor_speed"
        private const val KEY_SCROLL_SPEED = "scroll_speed"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_AUTO_CONNECT = "auto_connect"
        private const val KEY_LAST_IP = "last_ip"
        private const val KEY_LAST_PORT = "last_port"
    }

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value).apply()

    var cursorSpeed: Float
        get() = prefs.getFloat(KEY_CURSOR_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_CURSOR_SPEED, value).apply()

    var scrollSpeed: Float
        get() = prefs.getFloat(KEY_SCROLL_SPEED, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SCROLL_SPEED, value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()

    var lastIp: String?
        get() = prefs.getString(KEY_LAST_IP, null)
        set(value) = prefs.edit().putString(KEY_LAST_IP, value).apply()

    var lastPort: Int
        get() = prefs.getInt(KEY_LAST_PORT, 18888)
        set(value) = prefs.edit().putInt(KEY_LAST_PORT, value).apply()
}
