package com.safeme.app.vpn

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Relays UDP packets between the TUN and the real network.
 *
 * - Non-DNS traffic is always forwarded (never blocked) so applications keep
 *   full connectivity.
 * - DNS (UDP port 53) is parsed and checked against [BlockingRules]; blocked
 *   queries are answered locally with NXDOMAIN, everything else is relayed.
 *
 * All channel I/O happens on a single selector thread; the TUN reader only
 * enqueues outbound datagrams, which keeps the design race-free.
 */
class UdpRelay(
    private val writer: TunWriter,
    private val protector: SocketProtector,
    private val rules: AtomicReference<BlockingRules>,
    private val mtu: Int,
    private val onBlocked: () -> Unit = {},
    private val idleTimeoutMillis: Long = 120_000L,
    private val tunLocalAddrs: Set<InetAddress> = emptySet(),
    private val fallbackDns: List<InetAddress> = emptyList(),
    private val dnsPort: Int = 53,
) {

    private class Flow(
        val key: FlowKey,
        val ipVer: Int,
        val clientAddr: InetAddress,
        val clientPort: Int,
        val serverAddr: InetAddress,
        val serverPort: Int,
        val sendTarget: InetAddress,
        val channel: DatagramChannel,
    ) {
        @Volatile
        var lastActivity: Long = System.currentTimeMillis()
        var closed = false
    }

    private class FlowKey(
        val src: String,
        val srcPort: Int,
        val dst: String,
        val dstPort: Int,
    ) {
        override fun equals(other: Any?): Boolean =
            other is FlowKey && other.src == src && other.srcPort == srcPort &&
                other.dst == dst && other.dstPort == dstPort

        override fun hashCode(): Int {
            var h = src.hashCode()
            h = 31 * h + srcPort
            h = 31 * h + dst.hashCode()
            h = 31 * h + dstPort
            return h
        }
    }

    private class Outbound(
        val ipVer: Int,
        val clientAddr: InetAddress,
        val clientPort: Int,
        val serverAddr: InetAddress,
        val serverPort: Int,
        val payload: ByteArray,
    )

    private val flows = ConcurrentHashMap<FlowKey, Flow>()
    private val outboundQueue = LinkedBlockingQueue<Outbound>()
    private val selector: Selector = Selector.open()
    // Must fit the largest UDP datagram (e.g. EDNS0 DNS responses); receive()
    // silently truncates anything larger.
    private val buffer = ByteBuffer.allocateDirect(65535)

    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "SafeMe-UdpRelay").also { it.isDaemon = true; it.start() }
    }

    /**
     * Called from the TUN reader thread with a complete UDP payload (transport
     * data only). Returns true if the packet was consumed (relayed or blocked).
     */
    fun handle(ipVer: Int, clientAddr: InetAddress, clientPort: Int, serverAddr: InetAddress, serverPort: Int, payload: ByteArray): Boolean {
        if (!running) return false

        // DNS interception: filter, or synthesize NXDOMAIN for blocked names.
        if (serverPort == dnsPort) {
            val rulesNow = rules.get()
            val name = DnsCodec.questionName(payload)
            var blocked = name != null && rulesNow.shouldBlock(name)
            if (!blocked && name == null) {
                // Question could not be decoded normally — scan the raw bytes
                // for a blocked domain so filtering still applies.
                blocked = rulesNow.rawMatch(payload) != null
            }
            if (blocked) {
                val query = DnsCodec.parseQuery(payload)
                if (query != null) {
                    val response = DnsCodec.buildSyntheticResponse(query, rcode = 3)
                    writeDatagram(ipVer, serverAddr, serverPort, clientAddr, clientPort, response)
                }
                // If the query can't even be parsed, drop it rather than
                // forward a blocked name upstream.
                onBlocked()
                return true
            }
        }

        outboundQueue.add(
            Outbound(ipVer, clientAddr, clientPort, serverAddr, serverPort, payload)
        )
        selector.wakeup()
        return true
    }

    fun stop() {
        running = false
        selector.wakeup()
        thread?.interrupt()
        thread = null
        closeAll()
    }

    private fun loop() {
        var lastCleanup = System.currentTimeMillis()
        while (running) {
            try {
                drainOutbound()
                val now = System.currentTimeMillis()
                if (now - lastCleanup > 30_000L) {
                    lastCleanup = now
                    cleanupIdle(now)
                }
                val ready = selector.select(1000)
                if (ready > 0) {
                    val keys = selector.selectedKeys()
                    val it = keys.iterator()
                    while (it.hasNext()) {
                        val key = it.next()
                        it.remove()
                        if (!key.isValid) continue
                        val flow = key.attachment() as Flow
                        try {
                            receiveResponse(flow)
                        } catch (_: Throwable) {
                            closeFlow(flow)
                        }
                    }
                }
            } catch (_: Throwable) {
                if (!running) break
            }
        }
    }

    private fun drainOutbound() {
        while (true) {
            val out = outboundQueue.poll() ?: break
            try {
                val key = FlowKey(
                    out.clientAddr.hostAddress.orEmpty(),
                    out.clientPort,
                    out.serverAddr.hostAddress.orEmpty(),
                    out.serverPort,
                )
                val flow = flows[key] ?: createFlow(key, out).also { flows[key] = it }
                if (flow.closed) continue
                val socketAddress = InetSocketAddress(flow.sendTarget, out.serverPort)
                val payload = out.payload
                val buf = ByteBuffer.wrap(payload)
                synchronized(flow.channel) {
                    flow.channel.send(buf, socketAddress)
                }
                flow.lastActivity = System.currentTimeMillis()
            } catch (_: Exception) {
                // Individual datagram failure must not kill the relay loop.
            }
        }
    }

    private fun createFlow(key: FlowKey, out: Outbound): Flow {
        val channel = DatagramChannel.open()
        channel.configureBlocking(false)
        val socket = channel.socket()
        protector.protect(socket)
        val flow = Flow(
            key = key,
            ipVer = out.ipVer,
            clientAddr = out.clientAddr,
            clientPort = out.clientPort,
            serverAddr = out.serverAddr,
            serverPort = out.serverPort,
            sendTarget = effectiveTarget(out.serverAddr),
            channel = channel,
        )
        selector.wakeup()
        synchronized(selector) {
            channel.register(selector, SelectionKey.OP_READ, flow)
        }
        return flow
    }

    /**
     * DNS queries addressed to the TUN's own addresses (e.g. 10.0.0.2) are
     * re-originated to a real upstream resolver; every other destination is
     * used as-is.
     */
    private fun effectiveTarget(dst: InetAddress): InetAddress {
        if (dst !in tunLocalAddrs) return dst
        val sameFamily = fallbackDns.firstOrNull { Addr.isV4(it) == Addr.isV4(dst) }
        if (sameFamily != null) return sameFamily
        return fallbackDns.firstOrNull() ?: dst
    }

    private fun receiveResponse(flow: Flow) {
        buffer.clear()
        val sender = flow.channel.receive(buffer) // returns the sender address
        if (sender == null) return
        buffer.flip()
        val payload = ByteArray(buffer.remaining())
        buffer.get(payload)

        // Rewrite: response arrives from the server; forward to the client with
        // the source port the client originally addressed (usually 53).
        writeDatagram(
            flow.ipVer,
            flow.serverAddr,
            flow.serverPort,
            flow.clientAddr,
            flow.clientPort,
            payload,
        )
        flow.lastActivity = System.currentTimeMillis()
    }

    /** Builds and writes a UDP/IP datagram to the TUN, fragmenting if needed. */
    private fun writeDatagram(
        ipVer: Int,
        srcAddr: InetAddress,
        srcPort: Int,
        dstAddr: InetAddress,
        dstPort: Int,
        payload: ByteArray,
    ) {
        if (payload.isEmpty()) return
        try {
            val udp = UdpPacket.build(srcPort, dstPort, payload)
            val transport = InternetChecksum.transportChecksum(ipVer, srcAddr, dstAddr, IpConstants.PROTO_UDP, udp)
            ByteOrder.putUInt16(udp, 6, transport)
            val packet = if (ipVer == IpConstants.IPV4) {
                IpBuilder.ipv4(srcAddr, dstAddr, IpConstants.PROTO_UDP, udp, computeTransportChecksum = false)
            } else {
                IpBuilder.ipv6(srcAddr, dstAddr, IpConstants.PROTO_UDP, udp, computeTransportChecksum = false)
            }
            IpBuilder.fragment(packet, mtu, ipVer).forEach { writer.write(it) }
        } catch (_: Exception) {
            // Malformed address or closed tunnel; drop the datagram.
        }
    }

    private fun cleanupIdle(now: Long) {
        flows.values.forEach { flow ->
            if (now - flow.lastActivity > idleTimeoutMillis) closeFlow(flow)
        }
    }

    private fun closeFlow(flow: Flow) {
        if (flow.closed) return
        flow.closed = true
        synchronized(selector) {
            val key = flow.channel.keyFor(selector)
            if (key != null) key.cancel()
        }
        flows.remove(flow.key)
        runCatching { flow.channel.close() }
    }

    private fun closeAll() {
        flows.values.forEach { closeFlow(it) }
        flows.clear()
    }
}
