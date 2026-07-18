package com.swipex.app.network

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class UdpDiscoveryListener(
    private val onServerFound: (String, Int) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null

    fun start(port: Int = 18889) {
        if (running.getAndSet(true)) return

        Thread {
            try {
                socket = DatagramSocket(port, InetAddress.getByName("0.0.0.0")).apply {
                    broadcast = true
                }
                val buffer = ByteArray(1024)
                while (running.get()) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    
                    if (message.startsWith("SwipeXServer:")) {
                        val parts = message.split(":")
                        if (parts.size == 3) {
                            val ip = parts[1]
                            val wsPort = parts[2].toIntOrNull() ?: 18888
                            onServerFound(ip, wsPort)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UDP", "Discovery error", e)
            } finally {
                stop()
            }
        }.start()
    }

    fun stop() {
        running.set(false)
        socket?.close()
        socket = null
    }
}
