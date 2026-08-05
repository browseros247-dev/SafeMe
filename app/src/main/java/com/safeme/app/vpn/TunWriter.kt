package com.safeme.app.vpn

import java.io.IOException
import java.io.OutputStream
import java.net.DatagramSocket
import java.net.Socket

/**
 * Serializes writes to the TUN device. All threads that emit packets to the
 * tunnel must go through this class.
 */
class TunWriter(private val output: OutputStream) {

    private val lock = Any()

    fun write(packet: ByteArray) {
        if (packet.isEmpty()) return
        synchronized(lock) {
            try {
                output.write(packet)
                output.flush()
            } catch (_: IOException) {
                // Tunnel closed; the reader loop will observe shutdown.
            }
        }
    }

    fun close() {
        synchronized(lock) {
            runCatching { output.flush() }
            runCatching { output.close() }
        }
    }
}

/**
 * Marks sockets so their traffic bypasses the VPN tunnel (prevents relayed
 * traffic from re-entering the TUN in an infinite loop). Implemented by the
 * service via [android.net.VpnService.protect].
 */
interface SocketProtector {
    fun protect(socket: DatagramSocket): Boolean
    fun protect(socket: Socket): Boolean
}

/** InetAddress helper used across the relays. */
object Addr {
    fun isV4(address: java.net.InetAddress): Boolean =
        address is java.net.Inet4Address

    fun isV6(address: java.net.InetAddress): Boolean =
        address is java.net.Inet6Address
}
