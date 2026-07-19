package com.swipex.app.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val onConnectionChanged: (Boolean, String?) -> Unit
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(3, TimeUnit.SECONDS) // Heartbeat ping every 3s
        .build()

    private var webSocket: WebSocket? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentIp: String? = null
    private var currentPort: Int = 18888
    private var isConnecting: Boolean = false

    var isConnected: Boolean = false
        private set

    // Background thread runner for auto-reconnection
    private var reconnectThread: Thread? = null
    private var shouldReconnect: Boolean = false

    fun connect(ip: String, port: Int = 18888) {
        if (currentIp == ip && currentPort == port && (isConnected || isConnecting)) {
            return
        }

        currentIp = ip
        currentPort = port
        shouldReconnect = true

        startConnectionLoop()
    }

    private fun startConnectionLoop() {
        if (reconnectThread != null && reconnectThread!!.isAlive) {
            return
        }

        reconnectThread = Thread {
            while (shouldReconnect && !isConnected) {
                val ip = currentIp
                if (ip != null) {
                    isConnecting = true
                    try {
                        val request = Request.Builder()
                            .url("ws://$ip:$currentPort/ws")
                            .build()

                        Log.d("WebSocket", "Attempting connection to ws://$ip:$currentPort/ws")
                        webSocket = client.newWebSocket(request, object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                Log.d("WebSocket", "WebSocket Connected successfully")
                                isConnected = true
                                isConnecting = false
                                mainHandler.post {
                                    onConnectionChanged(true, null)
                                }
                            }

                            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                                webSocket.close(1000, null)
                            }

                            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                Log.d("WebSocket", "WebSocket Closed")
                                handleDisconnect(null)
                            }

                            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                Log.e("WebSocket", "WebSocket Failure: ${t.message}")
                                handleDisconnect(t.localizedMessage ?: t.message ?: "Connection Failure")
                            }
                        })
                    } catch (e: Exception) {
                        Log.e("WebSocket", "Exception in webSocket init: ${e.message}")
                        handleDisconnect(e.message)
                    }
                }

                // Wait 2 seconds before checking / retrying reconnect
                try {
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun handleDisconnect(errorMsg: String?) {
        isConnected = false
        isConnecting = false
        mainHandler.post {
            onConnectionChanged(false, errorMsg)
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectThread?.interrupt()
        reconnectThread = null

        webSocket?.close(1000, "User disconnected")
        webSocket = null
        isConnected = false
        isConnecting = false

        mainHandler.post {
            onConnectionChanged(false, null)
        }
    }

    fun sendMessage(message: String) {
        if (isConnected) {
            executor.execute {
                try {
                    webSocket?.send(message)
                } catch (e: Exception) {
                    Log.e("WebSocket", "Error sending message: ${e.message}")
                    disconnect()
                }
            }
        }
    }

    fun sendMouseMove(dx: Float, dy: Float) {
        sendMessage("m,$dx,$dy")
    }

    fun sendClick(button: String, action: String) {
        sendMessage("c,$button,$action")
    }

    fun sendScroll(dy: Float) {
        sendMessage("s,$dy")
    }
}
