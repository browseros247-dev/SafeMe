package com.safeme.app.vpn

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Userspace TCP relay: terminates TCP connections from applications (received
 * via the TUN) and re-originates them on real sockets, so every application
 * keeps full, reliable connectivity while the VPN is active.
 *
 * The relay implements the server side of the TCP state machine toward the
 * application (SYN/SYN-ACK, sequence/ack tracking, MSS clamp, Go-Back-N
 * retransmission, FIN/RST handling) and splices payload to the real socket,
 * which performs the actual flow control and congestion control with the
 * remote endpoint.
 *
 * DNS-over-TCP (port 53) is intercepted so domain/keyword filtering also
 * applies to TCP DNS (Android uses it for truncated/large responses).
 */
class TcpRelay(
    private val writer: TunWriter,
    private val protector: SocketProtector,
    private val rules: AtomicReference<BlockingRules>,
    private val mtu: Int,
    private val onBlocked: () -> Unit = {},
    private val tunLocalAddrs: Set<InetAddress> = emptySet(),
    private val fallbackDns: List<InetAddress> = emptyList(),
    private val dnsPort: Int = 53,
) {

    private val connections = ConcurrentHashMap<FlowKey, TcpConnection>()
    private val connectExecutor: ExecutorService = Executors.newFixedThreadPool(4) { r ->
        Thread(r, "SafeMe-TcpConnect").apply { isDaemon = true }
    }

    /**
     * Runs the blocking real-socket connect on the bounded pool. Never throws:
     * after the relay has stopped the pool is shut down, and a late segment
     * must not crash the process with RejectedExecutionException.
     */
    fun submitConnect(task: Runnable) {
        if (!running) return
        runCatching { connectExecutor.execute(task) }
    }

    @Volatile
    private var running = false
    private var sweeper: Thread? = null

    private fun flowKey(clientAddr: InetAddress, clientPort: Int, serverAddr: InetAddress, serverPort: Int): FlowKey =
        FlowKey(
            clientAddr.hostAddress.orEmpty(), clientPort,
            serverAddr.hostAddress.orEmpty(), serverPort,
        )

    private class FlowKey(
        val client: String,
        val clientPort: Int,
        val server: String,
        val serverPort: Int,
    ) {
        override fun equals(other: Any?): Boolean =
            other is FlowKey && other.client == client && other.clientPort == clientPort &&
                other.server == server && other.serverPort == serverPort

        override fun hashCode(): Int {
            var h = client.hashCode()
            h = 31 * h + clientPort
            h = 31 * h + server.hashCode()
            h = 31 * h + serverPort
            return h
        }
    }

    fun start() {
        if (running) return
        running = true
        sweeper = Thread({ sweepLoop() }, "SafeMe-TcpSweeper").also { it.isDaemon = true; it.start() }
    }

    /** Handles an inbound TCP segment from the TUN reader thread. */
    fun handle(
        ipVer: Int,
        clientAddr: InetAddress,
        clientPort: Int,
        serverAddr: InetAddress,
        serverPort: Int,
        tcp: TcpHeader,
        packet: ByteArray,
        transportOffset: Int,
        payloadLength: Int,
    ) {
        if (!running) return
        try {
            handleInternal(ipVer, clientAddr, clientPort, serverAddr, serverPort, tcp, packet, transportOffset, payloadLength)
        } catch (t: Throwable) {
            // A malformed or racing segment must never crash the process; drop
            // the connection and continue serving the rest.
            connections.remove(flowKey(clientAddr, clientPort, serverAddr, serverPort))
        }
    }

    private fun handleInternal(
        ipVer: Int,
        clientAddr: InetAddress,
        clientPort: Int,
        serverAddr: InetAddress,
        serverPort: Int,
        tcp: TcpHeader,
        packet: ByteArray,
        transportOffset: Int,
        payloadLength: Int,
    ) {
        val key = flowKey(clientAddr, clientPort, serverAddr, serverPort)
        var conn = connections[key]

        if (tcp.rst) {
            conn?.close("RST from client")
            connections.remove(key)
            return
        }

        if (tcp.syn && !tcp.ackFlag) {
            if (conn != null) {
                if (TcpSeq.eq(tcp.seq, conn.clientInitialSeq) && conn.state == TcpState.SYN_RCVD) {
                    conn.resendSynAck()
                    return
                }
                // New connection attempt reusing the same 4-tuple (TIME_WAIT reuse).
                conn.close("new SYN on existing flow")
                connections.remove(key)
                conn = null
            }
            val dnsMode = serverPort == dnsPort
            val connectAddr = if (dnsMode) effectiveTarget(serverAddr) else serverAddr
            val newConn = TcpConnection(
                relay = this,
                key = key,
                ipVer = ipVer,
                clientAddr = clientAddr,
                clientPort = clientPort,
                serverAddr = serverAddr,
                connectServerAddr = connectAddr,
                serverPort = serverPort,
                clientInitialSeq = tcp.seq,
                mtu = mtu,
                clientWindow = tcp.window.coerceAtLeast(1),
                windowScale = TcpConnection.parseWindowScale(tcp, packet, transportOffset),
                dnsMode = dnsMode,
            )
            connections[key] = newConn
            newConn.handleSyn()
            return
        }

        conn?.let { it.handleSegment(tcp, packet, transportOffset, payloadLength) }
    }

    fun stop() {
        running = false
        connections.values.forEach { it.close("relay stopped") }
        connections.clear()
        connectExecutor.shutdownNow()
        sweeper?.interrupt()
        sweeper = null
    }

    private fun sweepLoop() {
        while (running) {
            try {
                Thread.sleep(400)
            } catch (_: InterruptedException) {
                break
            }
            val now = System.currentTimeMillis()
            connections.values.forEach { conn ->
                try {
                    conn.maintenance(now)
                } catch (_: Throwable) {
                    // Never let a single connection's maintenance failure kill
                    // the sweeper (and with it the whole process).
                }
            }
        }
    }

    fun removeConnection(conn: TcpConnection) {
        connections.remove(conn.key)
    }

    fun writePacket(packet: ByteArray) = writer.write(packet)

    fun protectSocket(s: Socket): Boolean = protector.protect(s)

    fun shouldBlock(name: String): Boolean = rules.get().shouldBlock(name)

    fun rawMatch(data: ByteArray): String? = rules.get().rawMatch(data)

    fun notifyBlocked() = onBlocked()

    fun isRunning(): Boolean = running

    /**
     * DNS queries addressed to the TUN's own addresses are re-originated to a
     * real upstream resolver; everything else connects to the original target.
     */
    private fun effectiveTarget(dst: InetAddress): InetAddress {
        if (dst !in tunLocalAddrs) return dst
        val sameFamily = fallbackDns.firstOrNull { Addr.isV4(it) == Addr.isV4(dst) }
        if (sameFamily != null) return sameFamily
        return fallbackDns.firstOrNull() ?: dst
    }

    companion object {
        const val DEFAULT_WINDOW = 65535
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val IDLE_TIMEOUT_MS = 300_000L
        const val FIN_GRACE_MS = 10_000L
        const val RETRANSMIT_INTERVAL_MS = 400L
        const val MAX_UNACKED_BYTES = 512 * 1024
        const val MAX_CLIENT_QUEUE_BYTES = 1024 * 1024
        const val MAX_WINDOW_BYTES = 1024 * 1024

        fun mssFor(ipVer: Int, mtu: Int): Int {
            // MTU minus IP header (20/40) minus TCP header (20).
            val overhead = if (ipVer == IpConstants.IPV4) 40 else 60
            return (mtu - overhead).coerceAtLeast(536)
        }
    }
}

/** TCP sequence-number arithmetic (RFC 1982, 32-bit space). */
object TcpSeq {
    private const val MOD = 0x1_0000_0000L

    fun eq(a: Long, b: Long): Boolean = (a and (MOD - 1)) == (b and (MOD - 1))

    fun lt(a: Long, b: Long): Boolean = ((a - b) and (MOD - 1)) >= (MOD ushr 1)

    fun le(a: Long, b: Long): Boolean = eq(a, b) || lt(a, b)

    fun add(seq: Long, delta: Long): Long = (seq + delta) and (MOD - 1)
}

enum class TcpState { SYN_RCVD, ESTABLISHED, FIN_RCVD, FIN_SENT, CLOSED }

class TcpConnection internal constructor(
    private val relay: TcpRelay,
    val key: Any,
    private val ipVer: Int,
    private val clientAddr: InetAddress,
    private val clientPort: Int,
    private val serverAddr: InetAddress,
    private val connectServerAddr: InetAddress,
    private val serverPort: Int,
    val clientInitialSeq: Long,
    private val mtu: Int,
    clientWindow: Int,
    windowScale: Int,
    private val dnsMode: Boolean,
) {
    private val lock = Any()
    private val closed = AtomicBoolean(false)

    @Volatile
    var state: TcpState = TcpState.SYN_RCVD
        private set

    var clientSeq: Long = TcpSeq.add(clientInitialSeq, 1)
        private set

    private var ourInitialSeq: Long =
        ((System.nanoTime() xor (clientSeq shl 7)) and 0xFFFFFFFFL)

    private var ourSeq: Long = TcpSeq.add(ourInitialSeq, 1)
    private var sendBufferStartSeq: Long = ourSeq
    private val sendBuffer = ArrayDeque<ByteArray>()

    @Volatile
    private var unackedBytes = 0L

    private val clientData = LinkedBlockingQueue<ByteArray>()

    @Volatile
    private var clientQueuedBytes = 0L

    private var socket: Socket? = null

    @Volatile
    private var connected = false

    private var ackReceived = false
    private var clientFinReceived = false
    private var ourFinSent = false
    private var serverEofReceived = false

    /** Bytes read from the real socket but not yet delivered (window full). */
    private var pendingServer: ByteArray? = null
    private var pendingOffset = 0

    private var lastSendTime = System.currentTimeMillis()
    private var lastActivity = System.currentTimeMillis()
    private var createdAt = System.currentTimeMillis()
    private var finSentAt = 0L

    private val mss = TcpRelay.mssFor(ipVer, mtu)
    private val clientWindowBytes = (clientWindow.toLong() shl windowScale)
        .coerceIn(1024L, TcpRelay.MAX_WINDOW_BYTES.toLong())
        .toInt()

    private var dnsBuffer = ByteArrayBuffer()

    init {
        if (TcpSeq.eq(ourInitialSeq, clientInitialSeq)) {
            ourInitialSeq = TcpSeq.add(ourInitialSeq, 1)
            ourSeq = TcpSeq.add(ourInitialSeq, 1)
        }
    }

    fun handleSyn() {
        synchronized(lock) {
            sendSynAckLocked()
            connectAsync()
        }
    }

    fun resendSynAck() {
        synchronized(lock) {
            if (state == TcpState.SYN_RCVD) sendSynAckLocked()
        }
    }

    private fun sendSynAckLocked() {
        if (closed.get()) return
        sendToClientLocked(
            TcpFlags.SYN or TcpFlags.ACK,
            ourInitialSeq,
            TcpSeq.add(clientInitialSeq, 1),
            mss = mss,
        )
    }

    private fun connectAsync() {
        relay.submitConnect(Runnable {
            val s = Socket()
            try {
                relay.protectSocket(s)
                s.connect(InetSocketAddress(connectServerAddr, serverPort), TcpRelay.CONNECT_TIMEOUT_MS.toInt())
                s.tcpNoDelay = true
                synchronized(lock) {
                    socket = s
                    connected = true
                    lastActivity = System.currentTimeMillis()
                    if (ackReceived) state = TcpState.ESTABLISHED
                }
                startIoThreads(s)
            } catch (_: Exception) {
                synchronized(lock) {
                    if (!closed.get()) sendRstLocked()
                }
                close("connect failed")
            }
        })
    }

    fun handleSegment(tcp: TcpHeader, packet: ByteArray, transportOffset: Int, payloadLength: Int) {
        if (closed.get()) return
        synchronized(lock) {
            lastActivity = System.currentTimeMillis()

            if (tcp.ackFlag) {
                processAckLocked(tcp.ack)
                // ACKs free window space: flush any deferred server data.
                sendAvailableLocked()
                maybeSendFinLocked()
            }

            if (tcp.syn) {
                if (state == TcpState.SYN_RCVD && TcpSeq.eq(tcp.seq, clientInitialSeq)) {
                    sendSynAckLocked()
                }
                return
            }

            if (state == TcpState.SYN_RCVD) {
                if (!ackReceived) {
                    ackReceived = true
                    if (connected) state = TcpState.ESTABLISHED
                }
                if (!connected) {
                    if (payloadLength > 0) {
                        if (TcpSeq.eq(tcp.seq, clientSeq)) {
                            if (acceptClientPayloadLocked(packet, transportOffset + tcp.dataOffset, payloadLength)) {
                                clientSeq = TcpSeq.add(clientSeq, payloadLength.toLong())
                                sendAckLocked()
                            }
                        } else {
                            sendAckLocked()
                        }
                    }
                    if (tcp.fin) handleClientFinLocked()
                    return
                }
                if (state == TcpState.SYN_RCVD) state = TcpState.ESTABLISHED
            }

            if (payloadLength > 0) {
                when {
                    TcpSeq.eq(tcp.seq, clientSeq) -> {
                        if (acceptClientPayloadLocked(packet, transportOffset + tcp.dataOffset, payloadLength)) {
                            clientSeq = TcpSeq.add(clientSeq, payloadLength.toLong())
                            sendAckLocked()
                        }
                    }
                    TcpSeq.lt(tcp.seq, clientSeq) -> sendAckLocked()
                    else -> sendAckLocked()
                }
            }

            if (tcp.fin) handleClientFinLocked()
        }
    }

    private fun acceptClientPayloadLocked(packet: ByteArray, offset: Int, length: Int): Boolean {
        if (clientQueuedBytes + length > TcpRelay.MAX_CLIENT_QUEUE_BYTES) {
            sendRstLocked()
            closeLocked("client queue overflow")
            return false
        }
        clientData.add(packet.copyOfRange(offset, offset + length))
        clientQueuedBytes += length
        return true
    }

    private fun processAckLocked(ack: Long) {
        if (TcpSeq.le(ack, sendBufferStartSeq)) return
        var removed = 0L
        while (sendBuffer.isNotEmpty()) {
            val first = sendBuffer.first()
            val chunkEnd = TcpSeq.add(sendBufferStartSeq, removed + first.size)
            if (TcpSeq.le(chunkEnd, ack)) {
                removed += first.size
                sendBuffer.removeFirst()
            } else break
        }
        if (removed > 0) {
            sendBufferStartSeq = TcpSeq.add(sendBufferStartSeq, removed)
            unackedBytes -= removed
            if (unackedBytes < 0) unackedBytes = 0
        }
        if (sendBuffer.isEmpty() && TcpSeq.lt(sendBufferStartSeq, ack)) {
            sendBufferStartSeq = if (TcpSeq.lt(ack, ourSeq)) ack else ourSeq
        }
    }

    private fun handleClientFinLocked() {
        if (clientFinReceived) return
        clientFinReceived = true
        clientSeq = TcpSeq.add(clientSeq, 1)
        sendAckLocked()
        if (state == TcpState.ESTABLISHED) state = TcpState.FIN_RCVD
        val s = socket
        if (s != null && connected) {
            try {
                s.shutdownOutput()
            } catch (_: IOException) {
            }
        }
        maybeFinishCloseLocked()
    }

    private fun maybeFinishCloseLocked() {
        if (clientFinReceived && ourFinSent) closeLocked("both sides finished")
    }

    private fun sendAckLocked() {
        sendToClientLocked(TcpFlags.ACK, ourSeq, clientSeq)
    }

    private fun sendRstLocked() {
        sendToClientLocked(TcpFlags.RST or TcpFlags.ACK, ourSeq, clientSeq)
    }

    private fun sendToClientLocked(
        flags: Int,
        seq: Long,
        ack: Long,
        mss: Int? = null,
        payload: ByteArray? = null,
    ) {
        if (closed.get()) return
        val segment = TcpPacket.build(serverPort, clientPort, seq, ack, flags, TcpRelay.DEFAULT_WINDOW, payload, mss)
        val checksum = InternetChecksum.transportChecksum(ipVer, serverAddr, clientAddr, IpConstants.PROTO_TCP, segment)
        ByteOrder.putUInt16(segment, 16, checksum)
        val packet = if (ipVer == IpConstants.IPV4) {
            IpBuilder.ipv4(serverAddr, clientAddr, IpConstants.PROTO_TCP, segment, computeTransportChecksum = false)
        } else {
            IpBuilder.ipv6(serverAddr, clientAddr, IpConstants.PROTO_TCP, segment, computeTransportChecksum = false)
        }
        relay.writePacket(packet)
        lastSendTime = System.currentTimeMillis()
    }

    // ---- Server → client (reader thread) ----

    private fun startIoThreads(s: Socket) {
        Thread({ serverReader(s) }, "SafeMe-TcpServer-" + System.nanoTime())
            .also { it.isDaemon = true; it.start() }
        Thread({ clientWriter(s) }, "SafeMe-TcpClient-" + System.nanoTime())
            .also { it.isDaemon = true; it.start() }
    }

    private fun serverReader(s: Socket) {
        val buf = ByteArray(16 * 1024)
        try {
            while (!closed.get() && relay.isRunning()) {
                // Backpressure: wait until the client window has room, flushing
                // any deferred bytes in the meantime.
                while (!closed.get() && relay.isRunning()) {
                    var hasRoom = false
                    synchronized(lock) {
                        if (pendingServer != null) sendAvailableLocked()
                        hasRoom = unackedBytes < clientWindowBytes
                    }
                    if (hasRoom) break
                    Thread.sleep(20)
                }
                if (closed.get() || !relay.isRunning()) break
                val n = s.inputStream.read(buf)
                if (n < 0) {
                    onServerEof()
                    return
                }
                if (n == 0) continue
                synchronized(lock) {
                    if (!closed.get()) onServerDataLocked(buf, n)
                }
            }
        } catch (_: Exception) {
            synchronized(lock) {
                if (!closed.get()) {
                    sendRstLocked()
                    closeLocked("server socket error")
                }
            }
        }
    }

    private fun onServerEof() {
        synchronized(lock) {
            if (closed.get() || ourFinSent) return
            serverEofReceived = true
            sendAvailableLocked()
            maybeSendFinLocked()
        }
    }

    /** Appends server bytes to the pending buffer and sends as much as the window allows. */
    private fun onServerDataLocked(buf: ByteArray, n: Int) {
        val pending = pendingServer
        if (pending != null) {
            val unread = pending.size - pendingOffset
            val combined = ByteArray(unread + n)
            System.arraycopy(pending, pendingOffset, combined, 0, unread)
            System.arraycopy(buf, 0, combined, unread, n)
            pendingServer = combined
            pendingOffset = 0
        } else {
            pendingServer = buf.copyOf(n)
            pendingOffset = 0
        }
        sendAvailableLocked()
    }

    private fun sendAvailableLocked() {
        val data = pendingServer ?: return
        val available = data.size - pendingOffset
        if (available <= 0) {
            pendingServer = null
            pendingOffset = 0
            maybeSendFinLocked()
            return
        }
        val room = (clientWindowBytes - unackedBytes).coerceAtLeast(0L)
        if (room <= 0) return
        var sent = 0
        while (sent < available && sent < room) {
            val chunkLen = minOf(mss, available - sent, (room - sent).toInt())
            val chunk = data.copyOfRange(pendingOffset + sent, pendingOffset + sent + chunkLen)
            sendToClientLocked(TcpFlags.PSH or TcpFlags.ACK, ourSeq, clientSeq, payload = chunk)
            sendBuffer.add(chunk)
            unackedBytes += chunkLen
            ourSeq = TcpSeq.add(ourSeq, chunkLen.toLong())
            sent += chunkLen
        }
        pendingOffset += sent
        if (pendingOffset >= data.size) {
            pendingServer = null
            pendingOffset = 0
            maybeSendFinLocked()
        }
    }

    private fun maybeSendFinLocked() {
        if (serverEofReceived && pendingServer == null && !ourFinSent) {
            sendToClientLocked(TcpFlags.FIN or TcpFlags.ACK, ourSeq, clientSeq)
            ourFinSent = true
            finSentAt = System.currentTimeMillis()
            state = TcpState.FIN_SENT
            maybeFinishCloseLocked()
        }
    }

    // ---- Client → server (writer thread) ----

    private fun clientWriter(s: Socket) {
        try {
            while (!closed.get() && relay.isRunning()) {
                val data = clientData.poll(500, TimeUnit.MILLISECONDS) ?: continue
                clientQueuedBytes -= data.size
                if (dnsMode) {
                    processDnsClientData(s, data)
                } else {
                    s.outputStream.write(data)
                    s.outputStream.flush()
                }
            }
        } catch (_: Exception) {
            synchronized(lock) {
                if (!closed.get()) {
                    sendRstLocked()
                    closeLocked("client socket error")
                }
            }
        }
    }

    private fun processDnsClientData(s: Socket, data: ByteArray) {
        dnsBuffer.write(data)
        val stream = dnsBuffer.toByteArray()
        var consumed = 0
        while (stream.size - consumed >= 2) {
            val msgLen = ((stream[consumed].toInt() and 0xFF) shl 8) or
                (stream[consumed + 1].toInt() and 0xFF)
            if (msgLen < 12 || stream.size - consumed < 2 + msgLen) break
            val message = stream.copyOfRange(consumed + 2, consumed + 2 + msgLen)
            consumed += 2 + msgLen

            val name = DnsCodec.questionName(message)
            var blocked = name != null && relay.shouldBlock(name)
            if (!blocked && name == null) {
                // Fallback: raw scan for a blocked domain when the question
                // section couldn't be decoded normally.
                blocked = relay.rawMatch(message) != null
            }
            if (blocked) {
                val query = DnsCodec.parseQuery(message)
                if (query != null) {
                    val response = DnsCodec.buildSyntheticResponse(query, rcode = 3)
                    val framed = ByteArray(2 + response.size)
                    ByteOrder.putUInt16(framed, 0, response.size)
                    System.arraycopy(response, 0, framed, 2, response.size)
                    synchronized(lock) {
                        if (!closed.get()) {
                            sendToClientLocked(TcpFlags.PSH or TcpFlags.ACK, ourSeq, clientSeq, payload = framed)
                            ourSeq = TcpSeq.add(ourSeq, framed.size.toLong())
                        }
                    }
                }
                // Unparseable but clearly blocked: drop the query upstream.
                relay.notifyBlocked()
            } else {
                val framed = ByteArray(2 + message.size)
                ByteOrder.putUInt16(framed, 0, message.size)
                System.arraycopy(message, 0, framed, 2, message.size)
                s.outputStream.write(framed)
                s.outputStream.flush()
            }
        }
        if (consumed > 0) {
            val remainder = stream.copyOfRange(consumed, stream.size)
            dnsBuffer = ByteArrayBuffer()
            dnsBuffer.write(remainder)
        }
    }

    // ---- Maintenance (sweeper thread) ----

    fun maintenance(now: Long) {
        if (closed.get()) return
        synchronized(lock) {
            // Retransmit unacked data (Go-Back-N) on a timer.
            if (sendBuffer.isNotEmpty() && connected && now - lastSendTime > TcpRelay.RETRANSMIT_INTERVAL_MS) {
                retransmitLocked()
            }
            // Flush deferred server data and deliver any pending FIN.
            sendAvailableLocked()
            maybeSendFinLocked()
            // Connect timeout.
            if (state == TcpState.SYN_RCVD && !connected && now - createdAt > TcpRelay.CONNECT_TIMEOUT_MS) {
                sendRstLocked()
                closeLocked("connect timeout")
                return
            }
            // Idle timeout.
            if (now - lastActivity > TcpRelay.IDLE_TIMEOUT_MS) {
                sendRstLocked()
                closeLocked("idle timeout")
                return
            }
            // FIN grace.
            if (ourFinSent && now - finSentAt > TcpRelay.FIN_GRACE_MS) {
                closeLocked("fin grace expired")
            }
        }
    }

    private fun retransmitLocked() {
        if (sendBuffer.isEmpty()) return
        var seq = sendBufferStartSeq
        for (chunk in sendBuffer) {
            sendToClientLocked(TcpFlags.PSH or TcpFlags.ACK, seq, clientSeq, payload = chunk)
            seq = TcpSeq.add(seq, chunk.size.toLong())
        }
    }

    // ---- Teardown ----

    fun close(reason: String) {
        synchronized(lock) { closeLocked(reason) }
    }

    private fun closeLocked(reason: String) {
        if (!closed.compareAndSet(false, true)) return
        state = TcpState.CLOSED
        relay.removeConnection(this)
        val s = socket
        socket = null
        runCatching { s?.close() }
    }

    companion object {
        /** Parses the window scale option (RFC 7323); 0 if not present. */
        fun parseWindowScale(tcp: TcpHeader, packet: ByteArray, transportOffset: Int): Int {
            if (!tcp.syn) return 0
            val optionsEnd = transportOffset + tcp.dataOffset
            var cursor = transportOffset + 20
            while (cursor + 1 < optionsEnd) {
                val kind = packet[cursor].toInt() and 0xFF
                when (kind) {
                    0 -> break
                    1 -> cursor++
                    3 -> {
                        if (cursor + 3 <= optionsEnd) {
                            val shift = packet[cursor + 2].toInt() and 0xFF
                            return shift.coerceIn(0, 14)
                        }
                        break
                    }
                    else -> {
                        if (cursor + 1 >= optionsEnd) break
                        val len = packet[cursor + 1].toInt() and 0xFF
                        if (len < 2) break
                        cursor += len
                    }
                }
            }
            return 0
        }
    }
}

/** Growable byte buffer used for DNS-over-TCP message reassembly. */
private class ByteArrayBuffer {
    private var buf = ByteArray(256)
    private var size = 0

    fun write(b: ByteArray) = write(b, 0, b.size)

    fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        ensure(size + len)
        System.arraycopy(b, off, buf, size, len)
        size += len
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)

    private fun ensure(capacity: Int) {
        if (capacity > buf.size) {
            var newSize = buf.size * 2
            while (newSize < capacity) newSize *= 2
            buf = buf.copyOf(newSize)
        }
    }
}
