package com.safeme.app.vpn

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * The TUN packet engine: reads packets from the tunnel, parses IPv4/IPv6,
 * reassembles fragments, and dispatches to the UDP relay, TCP relay or the
 * local ICMP echo responder.
 *
 * Default policy is forward-everything: traffic is only ever blocked when a
 * rule (domain / keyword) intentionally matches a DNS name. Rules are held in
 * an [AtomicReference] so preference changes apply to a running tunnel without
 * a restart.
 */
class VpnEngine(
    private val pfd: ParcelFileDescriptor,
    private val mtu: Int,
    val rules: AtomicReference<BlockingRules>,
    private val protector: SocketProtector,
    private val onBlocked: () -> Unit = {},
    private val onTunnelClosed: () -> Unit = {},
    private val tunLocalAddrs: Set<InetAddress> = emptySet(),
    private val fallbackDns: List<InetAddress> = emptyList(),
) {

    private val input = FileInputStream(pfd.fileDescriptor)
    private val output = FileOutputStream(pfd.fileDescriptor)
    private val writer = TunWriter(output)

    private val udpRelay = UdpRelay(writer, protector, rules, mtu, onBlocked, tunLocalAddrs = tunLocalAddrs, fallbackDns = fallbackDns)
    private val tcpRelay = TcpRelay(writer, protector, rules, mtu, onBlocked, tunLocalAddrs = tunLocalAddrs, fallbackDns = fallbackDns)
    private val reassembler = FragmentReassembler()

    @Volatile
    private var running = false
    private var readerThread: Thread? = null

    fun start() {
        if (running) return
        running = true
        udpRelay.start()
        tcpRelay.start()
        readerThread = Thread({ readLoop() }, "SafeMe-TunReader").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        if (!running) return
        running = false
        udpRelay.stop()
        tcpRelay.stop()
        reassembler.clear()
        writer.close()
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { pfd.close() }
    }

    private fun readLoop() {
        try {
            val buffer = ByteArray(mtu.coerceIn(576, 65535))
            while (running) {
                val n = try {
                    input.read(buffer)
                } catch (_: Exception) {
                    -1
                }
                if (n <= 0) break
                try {
                    handlePacket(buffer, n)
                } catch (t: Throwable) {
                    // A single malformed packet must never take down the VPN
                    // (and with it the whole app process). Drop and continue.
                }
            }
        } catch (t: Throwable) {
            // Fall through to teardown below.
        }
        // Tunnel closed unexpectedly (e.g. another VPN started, permission
        // revoked, or the kernel tore the interface down): tear down and let
        // the service decide whether to re-establish.
        if (running) {
            running = false
            udpRelay.stop()
            tcpRelay.stop()
            runCatching { pfd.close() }
            onTunnelClosed()
        }
    }

    private fun handlePacket(buffer: ByteArray, length: Int) {
        val ip = IpPacket.parse(buffer, 0, length) ?: return

        // Fragment reassembly: fragments are held until the datagram completes.
        if (ip.isFragment) {
            val assembled = reassembler.addFragment(ip, buffer, 0, length) ?: return
            val completeIp = IpPacket.parse(assembled, 0, assembled.size) ?: return
            dispatch(assembled, completeIp)
            return
        }
        dispatch(buffer, ip)
    }

    private fun dispatch(packet: ByteArray, ip: IpHeader) {
        val transportLength = ip.totalLength - ip.payloadOffset
        if (transportLength <= 0) return
        when (ip.protocol) {
            IpConstants.PROTO_TCP -> handleTcp(packet, ip, transportLength)
            IpConstants.PROTO_UDP -> handleUdp(packet, ip, transportLength)
            IpConstants.PROTO_ICMP -> handleIcmp4(packet, ip)
            IpConstants.PROTO_ICMPV6 -> handleIcmp6(packet, ip)
            else -> Unit // Unknown protocol: drop quietly.
        }
    }

    private fun handleTcp(packet: ByteArray, ip: IpHeader, transportLength: Int) {
        val tcp = TcpPacket.parse(packet, ip.payloadOffset, transportLength) ?: return
        tcpRelay.handle(
            ipVer = ip.version,
            clientAddr = ip.src,
            clientPort = tcp.srcPort,
            serverAddr = ip.dst,
            serverPort = tcp.dstPort,
            tcp = tcp,
            packet = packet,
            transportOffset = ip.payloadOffset,
            payloadLength = tcp.payloadLength,
        )
    }

    private fun handleUdp(packet: ByteArray, ip: IpHeader, transportLength: Int) {
        val udp = UdpPacket.parse(packet, ip.payloadOffset, transportLength) ?: return
        if (udp.payloadLength <= 0) return
        val payload = packet.copyOfRange(udp.payloadOffset, udp.payloadOffset + udp.payloadLength)
        udpRelay.handle(
            ipVer = ip.version,
            clientAddr = ip.src,
            clientPort = udp.srcPort,
            serverAddr = ip.dst,
            serverPort = udp.dstPort,
            payload = payload,
        )
    }

    // ---- ICMP echo (ping) ----

    private fun handleIcmp4(packet: ByteArray, ip: IpHeader) {
        if (ip.totalLength - ip.payloadOffset < 8) return
        val type = packet[ip.payloadOffset].toInt() and 0xFF
        if (type != IpConstants.ICMP_ECHO_REQUEST) return
        val icmp = packet.copyOfRange(ip.payloadOffset, ip.totalLength)
        icmp[0] = IpConstants.ICMP_ECHO_REPLY.toByte()
        icmp[2] = 0
        icmp[3] = 0
        val checksum = InternetChecksum.compute(icmp)
        ByteOrder.putUInt16(icmp, 2, checksum)
        val reply = IpBuilder.ipv4(ip.dst, ip.src, IpConstants.PROTO_ICMP, icmp, computeTransportChecksum = false)
        writer.write(reply)
    }

    private fun handleIcmp6(packet: ByteArray, ip: IpHeader) {
        if (ip.totalLength - ip.payloadOffset < 8) return
        val type = packet[ip.payloadOffset].toInt() and 0xFF
        if (type != IpConstants.ICMPV6_ECHO_REQUEST) return
        val icmp = packet.copyOfRange(ip.payloadOffset, ip.totalLength)
        icmp[0] = IpConstants.ICMPV6_ECHO_REPLY.toByte()
        icmp[2] = 0
        icmp[3] = 0
        val checksum = InternetChecksum.transportChecksum(
            IpConstants.IPV6, ip.dst, ip.src, IpConstants.PROTO_ICMPV6, icmp,
        )
        ByteOrder.putUInt16(icmp, 2, checksum)
        val reply = IpBuilder.ipv6(ip.dst, ip.src, IpConstants.PROTO_ICMPV6, icmp, computeTransportChecksum = false)
        writer.write(reply)
    }
}
