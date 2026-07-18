package com.swipex.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.swipex.app.connection.UdpDiscoveryListener
import com.swipex.app.network.WebSocketManager
import com.swipex.app.touchpad.OneEuroFilter
import com.swipex.app.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SwipeXViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application.applicationContext)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _serverIp = MutableStateFlow<String?>(null)
    val serverIp: StateFlow<String?> = _serverIp

    private val _sensitivity = MutableStateFlow(settingsManager.sensitivity)
    val sensitivity: StateFlow<Float> = _sensitivity

    private val _cursorSpeed = MutableStateFlow(settingsManager.cursorSpeed)
    val cursorSpeed: StateFlow<Float> = _cursorSpeed

    private val _scrollSpeed = MutableStateFlow(settingsManager.scrollSpeed)
    val scrollSpeed: StateFlow<Float> = _scrollSpeed

    private val _isDarkMode = MutableStateFlow(settingsManager.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _autoConnect = MutableStateFlow(settingsManager.autoConnect)
    val autoConnect: StateFlow<Boolean> = _autoConnect

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError

    private val wsManager = WebSocketManager { connected, error ->
        _isConnected.value = connected
        _connectionError.value = error
        if (connected) {
            val ip = _serverIp.value
            if (ip != null) {
                settingsManager.lastIp = ip
            }
        } else {
            if (_autoConnect.value) {
                startUdpDiscovery()
            }
        }
    }

    private val udpListener = UdpDiscoveryListener { ip, port ->
        if (!_isConnected.value) {
            _serverIp.value = ip
            wsManager.connect(ip, port)
        }
    }

    private val xFilter = OneEuroFilter()
    private val yFilter = OneEuroFilter()

    init {
        val lastIp = settingsManager.lastIp
        val lastPort = settingsManager.lastPort
        if (_autoConnect.value && lastIp != null) {
            _serverIp.value = lastIp
            wsManager.connect(lastIp, lastPort)
        }
        
        startUdpDiscovery()
    }

    private fun startUdpDiscovery() {
        udpListener.start()
    }

    fun onTouchMove(dx: Float, dy: Float) {
        val timestamp = System.currentTimeMillis()
        val filteredDx = xFilter.filter(dx.toDouble(), timestamp).toFloat() * _sensitivity.value * _cursorSpeed.value
        val filteredDy = yFilter.filter(dy.toDouble(), timestamp).toFloat() * _sensitivity.value * _cursorSpeed.value
        wsManager.sendMouseMove(filteredDx, filteredDy)
    }

    fun onAction(button: String, action: String) {
        wsManager.sendClick(button, action)
    }

    fun onScroll(dy: Float) {
        wsManager.sendScroll(dy * _scrollSpeed.value)
    }

    private fun idToIp(id: String): String? {
        return try {
            val cleanId = id.replace("-", "").trim().uppercase()
            if (cleanId.length == 8 && cleanId.all { it in '0'..'9' || it in 'A'..'F' }) {
                val b1 = cleanId.substring(0, 2).toInt(16)
                val b2 = cleanId.substring(2, 4).toInt(16)
                val b3 = cleanId.substring(4, 6).toInt(16)
                val b4 = cleanId.substring(6, 8).toInt(16)
                "$b1.$b2.$b3.$b4"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun connectToIp(payload: String) {
        _connectionError.value = null
        val trimmed = payload.trim()
        
        // Try decoding as connection ID first
        val decodedIp = idToIp(trimmed)
        if (decodedIp != null) {
            _serverIp.value = decodedIp
            settingsManager.lastPort = 18888
            wsManager.connect(decodedIp, 18888)
            return
        }

        // Otherwise fallback to normal IP or IP:Port parsing
        val parts = trimmed.split(":")
        if (parts.size == 2) {
            val ip = parts[0].trim()
            val port = parts[1].trim().toIntOrNull() ?: 18888
            _serverIp.value = ip
            settingsManager.lastPort = port
            wsManager.connect(ip, port)
        } else {
            val ip = trimmed
            _serverIp.value = ip
            settingsManager.lastPort = 18888
            wsManager.connect(ip)
        }
    }

    fun disconnect() {
        wsManager.disconnect()
        _serverIp.value = null
    }

    fun setSensitivity(value: Float) {
        _sensitivity.value = value
        settingsManager.sensitivity = value
    }

    fun setCursorSpeed(value: Float) {
        _cursorSpeed.value = value
        settingsManager.cursorSpeed = value
    }

    fun setScrollSpeed(value: Float) {
        _scrollSpeed.value = value
        settingsManager.scrollSpeed = value
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        settingsManager.isDarkMode = enabled
    }

    fun setAutoConnect(enabled: Boolean) {
        _autoConnect.value = enabled
        settingsManager.autoConnect = enabled
    }

    override fun onCleared() {
        super.onCleared()
        wsManager.disconnect()
        udpListener.stop()
    }
}
