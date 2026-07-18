package com.swipex.app.network

import android.util.Log
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class WebSocketManager(
    private val onConnectionChanged: (Boolean, String?) -> Unit
) {
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private val executor = Executors.newSingleThreadExecutor()
    
    private var currentIp: String? = null
    private var currentPort: Int = 18888
    private var isConnecting: Boolean = false
    
    var isConnected: Boolean = false
        private set

    fun connect(ip: String, port: Int = 18888) {
        if (currentIp == ip && currentPort == port && (isConnected || isConnecting)) {
            return
        }
        
        disconnect()
        currentIp = ip
        currentPort = port
        isConnecting = true
        
        executor.execute {
            try {
                Log.d("TCPClient", "Connecting to TCP Socket $ip:$port")
                val socketAddress = InetSocketAddress(ip, port)
                val newSocket = Socket()
                newSocket.connect(socketAddress, 5000) // 5 second connection timeout
                
                socket = newSocket
                outputStream = newSocket.getOutputStream()
                isConnected = true
                isConnecting = false
                
                onConnectionChanged(true, null)
                Log.d("TCPClient", "TCP Connected successfully")
            } catch (e: Exception) {
                Log.e("TCPClient", "TCP Connection failed: ${e.message}", e)
                isConnected = false
                isConnecting = false
                val errorMsg = e.localizedMessage ?: e.message ?: "Connection Refused/Timeout"
                onConnectionChanged(false, errorMsg)
            }
        }
    }

    fun disconnect() {
        executor.execute {
            try {
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.e("TCPClient", "Error disconnecting", e)
            } finally {
                socket = null
                outputStream = null
                isConnected = false
                isConnecting = false
                onConnectionChanged(false, null)
            }
        }
    }

    fun sendMessage(message: String) {
        if (isConnected) {
            executor.execute {
                try {
                    val stream = outputStream
                    if (stream != null) {
                        // Append a newline so the python server knows where the packet ends!
                        stream.write((message + "\n").toByteArray(Charsets.UTF_8))
                        stream.flush()
                    }
                } catch (e: Exception) {
                    Log.e("TCPClient", "Error sending message", e)
                    // Auto-disconnect on send failure to clean up resources
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
