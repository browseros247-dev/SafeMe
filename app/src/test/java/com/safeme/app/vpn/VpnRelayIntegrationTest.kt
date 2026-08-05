package com.safeme.app.vpn

import com.safeme.app.data.BlockedCategory
import com.safeme.app.data.BlockedKeyword
import com.safeme.app.data.BlockedWebsite
import com.safeme.app.data.BlockingPrefsState
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end integration tests: drive the real UDP/TCP relays against actual
 * local sockets (echo servers / DNS servers) while capturing what the relays
 * write "to the TUN". This validates forwarding, DNS filtering, TCP
 * handshake/data/close, and packet integrity over real I/O.
 */
class VpnRelayIntegrationTest {

    private val captureLock = Any()
    private val capture = ByteArrayOutputStream()
    private lateinit var writer: TunWriter

    private val protector = object : SocketProtector {
        override fun protect(socket: DatagramSocket): Boolean = true
        override fun protect(socket: Socket): Boolean = true
    }

    private val clientAddr = InetAddress.getByName("192.168.1.50")
    private val serverAddr = InetAddress.getByName("127.0.0.1")

    @Before
    fun setUp() {
        synchronized(captureLock) { capture.reset() }
        writer = TunWriter(object : OutputStream() {
            override fun write(b: Int) = synchronized(captureLock) { capture.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) =
                synchronized(captureLock) { capture.write(b, off, len) }
        })
    }

    @After
    fun tearDown() {
        synchronized(captureLock) { capture.reset() }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun readPackets(): List<ByteArray> = synchronized(captureLock) {
        val bytes = capture.toByteArray()
        capture.reset()
        val packets = ArrayList<ByteArray>()
        var offset = 0
        while (bytes.size - offset >= 20) {
            val version = (bytes[offset].toInt() ushr 4) and 0xF
            val totalLen = if (version == IpConstants.IPV4) {
                IpPacket.readUInt16(bytes, offset + 2)
            } else {
                40 + IpPacket.readUInt16(bytes, offset + 4)
            }
            if (totalLen <= 0 || offset + totalLen > bytes.size) break
            packets.add(bytes.copyOfRange(offset, offset + totalLen))
            offset += totalLen
        }
        packets
    }

    private val packetQueue = ArrayDeque<ByteArray>()

    /**
     * Awaits a packet matching [predicate]. Packets that don't match are kept
     * in a queue so relays writing several packets in quick succession (e.g. a
     * response followed by a FIN) are all observable.
     */
    private fun awaitPacket(timeoutMs: Long = 8000, predicate: (ByteArray) -> Boolean): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            packetQueue.addAll(readPackets())
            val it = packetQueue.iterator()
            while (it.hasNext()) {
                val p = it.next()
                if (predicate(p)) {
                    it.remove()
                    return p
                }
            }
            Thread.sleep(15)
        }
        return null
    }

    private fun parseIp(p: ByteArray): IpHeader = IpPacket.parse(p, 0, p.size)!!

    private fun parseTcp(p: ByteArray): TcpHeader {
        val ip = parseIp(p)
        return TcpPacket.parse(p, ip.payloadOffset, ip.totalLength - ip.payloadOffset)!!
    }

    private fun parseUdp(p: ByteArray): UdpHeader {
        val ip = parseIp(p)
        return UdpPacket.parse(p, ip.payloadOffset, ip.totalLength - ip.payloadOffset)!!
    }

    private fun tcpPayload(p: ByteArray): ByteArray {
        val ip = parseIp(p)
        val tcp = TcpPacket.parse(p, ip.payloadOffset, ip.totalLength - ip.payloadOffset)!!
        return p.copyOfRange(ip.payloadOffset + tcp.dataOffset, ip.totalLength)
    }

    /** Builds a full IPv4+TCP packet as the app would emit into the TUN. */
    private fun buildClientTcp(
        clientPort: Int,
        serverPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        payload: ByteArray = ByteArray(0),
    ): ByteArray {
        val segment = TcpPacket.build(clientPort, serverPort, seq, ack, flags, 65535, payload)
        val checksum = InternetChecksum.transportChecksum(
            IpConstants.IPV4, clientAddr, serverAddr, IpConstants.PROTO_TCP, segment,
        )
        ByteOrder.putUInt16(segment, 16, checksum)
        return IpBuilder.ipv4(clientAddr, serverAddr, IpConstants.PROTO_TCP, segment, computeTransportChecksum = false)
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private fun blockingRules(blocked: List<String>): BlockingRules = BlockingRules.fromPrefs(
        BlockingPrefsState(
            blockedWebsites = blocked.map { BlockedWebsite(it, BlockedCategory.CUSTOM) },
            blockingEnabled = true,
        ),
        bundledKeywords = listOf(BlockedKeyword("sexchat", BlockedCategory.ADULT)),
    )

    // ------------------------------------------------------------------
    // UDP
    // ------------------------------------------------------------------

    @Test
    fun `udp relay forwards datagrams end to end`() {
        val port = freePort()
        val echo = DatagramSocket(port)
        val echoThread = Thread {
            val buf = ByteArray(4096)
            val p = DatagramPacket(buf, buf.size)
            while (!echo.isClosed) {
                runCatching {
                    echo.receive(p)
                    echo.send(DatagramPacket(buf, p.length, p.address, p.port))
                }
            }
        }
        echoThread.isDaemon = true
        echoThread.start()

        try {
            val relay = UdpRelay(writer, protector, AtomicReference(BlockingRules.passthrough()), 1280)
            relay.start()
            val sent = "ping-udp-payload".toByteArray()
            relay.handle(IpConstants.IPV4, clientAddr, 41001, serverAddr, port, sent)

            val resp = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                ip != null && ip.protocol == IpConstants.PROTO_UDP
            }
            assertNotNull("UDP echo response not received", resp)
            val udp = parseUdp(resp!!)
            assertEquals(port, udp.srcPort)
            assertEquals(41001, udp.dstPort)
            val ip = parseIp(resp)
            assertEquals(clientAddr, ip.dst)
            val payload = resp.copyOfRange(udp.payloadOffset, udp.payloadOffset + udp.payloadLength)
            assertArrayEquals(sent, payload)
            relay.stop()
        } finally {
            echo.close()
        }
    }

    @Test
    fun `dns udp filtering blocks and forwards`() {
        val dnsPort = freePort()
        val dnsServer = DatagramSocket(dnsPort)
        val received = AtomicInteger(0)
        val dnsThread = Thread {
            val buf = ByteArray(4096)
            val p = DatagramPacket(buf, buf.size)
            while (!dnsServer.isClosed) {
                runCatching {
                    dnsServer.receive(p)
                    received.incrementAndGet()
                    // Canned response: echo ID, set QR, RCODE 0, echo question.
                    val id = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
                    val resp = ByteArray(16)
                    ByteOrder.putUInt16(resp, 0, id)
                    ByteOrder.putUInt16(resp, 2, 0x8180)
                    ByteOrder.putUInt16(resp, 4, 1)
                    dnsServer.send(DatagramPacket(resp, resp.size, p.address, p.port))
                }
            }
        }
        dnsThread.isDaemon = true
        dnsThread.start()

        try {
            val rules = blockingRules(listOf("blocked.example"))
            val blockedCounter = AtomicInteger(0)
            val relay = UdpRelay(
                writer, protector, AtomicReference(rules), 1280,
                onBlocked = { blockedCounter.incrementAndGet() },
                dnsPort = dnsPort,
            )
            relay.start()

            fun query(name: String, id: Int): ByteArray {
                val nameBytes = DnsCodec.encodeNameLabels(name)
                val q = ByteArray(12 + nameBytes.size + 4)
                ByteOrder.putUInt16(q, 0, id)
                ByteOrder.putUInt16(q, 2, 0x0100)
                ByteOrder.putUInt16(q, 4, 1)
                System.arraycopy(nameBytes, 0, q, 12, nameBytes.size)
                ByteOrder.putUInt16(q, 12 + nameBytes.size, 1)
                ByteOrder.putUInt16(q, 12 + nameBytes.size + 2, 1)
                return q
            }

            // Blocked domain: the relay must answer locally with NXDOMAIN and
            // never reach the upstream server.
            relay.handle(IpConstants.IPV4, clientAddr, 42001, serverAddr, dnsPort, query("blocked.example", 0x1111))
            val blockedResp = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                ip != null && ip.protocol == IpConstants.PROTO_UDP
            }
            assertNotNull("No response for blocked domain", blockedResp)
            val blockedUdp = parseUdp(blockedResp!!)
            val msg = blockedResp.copyOfRange(blockedUdp.payloadOffset, blockedUdp.payloadOffset + blockedUdp.payloadLength)
            assertEquals(0x1111, IpPacket.readUInt16(msg, 0))
            val flags = IpPacket.readUInt16(msg, 2)
            assertTrue("QR not set", flags and 0x8000 != 0)
            assertEquals("expected NXDOMAIN", 3, flags and 0x000F)
            val qname = DnsCodec.decodeName(msg, 12, msg.size)!!.first
            assertEquals("blocked.example", qname)
            assertEquals("blocked counter", 1, blockedCounter.get())
            assertEquals("upstream must not see blocked query", 0, received.get())

            // Allowed domain: must be forwarded upstream and relayed back.
            relay.handle(IpConstants.IPV4, clientAddr, 42002, serverAddr, dnsPort, query("ok.example", 0x2222))
            val allowedResp = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                ip != null && ip.protocol == IpConstants.PROTO_UDP
            }
            assertNotNull("No response for allowed domain", allowedResp)
            val allowedUdp = parseUdp(allowedResp!!)
            val allowedMsg = allowedResp.copyOfRange(allowedUdp.payloadOffset, allowedUdp.payloadOffset + allowedUdp.payloadLength)
            assertEquals(0x2222, IpPacket.readUInt16(allowedMsg, 0))
            assertEquals(0, IpPacket.readUInt16(allowedMsg, 2) and 0x000F) // RCODE 0 from upstream
            assertEquals("upstream must receive allowed query", 1, received.get())

            relay.stop()
        } finally {
            dnsServer.close()
        }
    }

    // ------------------------------------------------------------------
    // TCP
    // ------------------------------------------------------------------

    @Test
    fun `tcp relay completes full request-response cycle`() {
        val port = freePort()
        val server = ServerSocket(port)
        val serverThread = Thread {
            runCatching {
                val s = server.accept()
                val buf = ByteArray(8192)
                val n = s.getInputStream().read(buf)
                s.getOutputStream().write(buf, 0, n)
                s.getOutputStream().flush()
                s.close()
            }
            server.close()
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val relay = TcpRelay(writer, protector, AtomicReference(BlockingRules.passthrough()), 1280)
            relay.start()

            val clientPort = 43001
            val c0 = 1_000_000L

            // 1. SYN
            val syn = buildClientTcp(clientPort, port, c0, 0, TcpFlags.SYN)
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, port,
                TcpPacket.parse(syn, 20, syn.size - 20)!!, syn, 20, 0,
            )

            // 2. Expect SYN-ACK
            val synAck = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                ip != null && ip.protocol == IpConstants.PROTO_TCP
            }
            assertNotNull("no SYN-ACK", synAck)
            val synAckTcp = parseTcp(synAck!!)
            assertTrue(synAckTcp.syn && synAckTcp.ackFlag)
            assertEquals(TcpSeq.add(c0, 1), synAckTcp.ack)
            val y = synAckTcp.seq

            // 3. ACK + request payload
            val request = "hello tcp relay".toByteArray()
            val dataSeg = buildClientTcp(
                clientPort, port, TcpSeq.add(c0, 1), TcpSeq.add(y, 1),
                TcpFlags.PSH or TcpFlags.ACK, request,
            )
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, port,
                TcpPacket.parse(dataSeg, 20, dataSeg.size - 20)!!, dataSeg, 20, request.size,
            )

            // 4. Expect the echo response from the real server, wrapped for the app
            val resp = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                if (ip == null || ip.protocol != IpConstants.PROTO_TCP) return@awaitPacket false
                val tcp = TcpPacket.parse(p, ip.payloadOffset, ip.totalLength - ip.payloadOffset)!!
                tcp.dstPort == clientPort && tcp.payloadLength > 0
            }
            assertNotNull("no data response", resp)
            val respTcp = parseTcp(resp!!)
            assertArrayEquals(request, tcpPayload(resp))
            assertEquals(TcpSeq.add(c0, (1 + request.size).toLong()), respTcp.ack)
            assertEquals(TcpSeq.add(y, 1), respTcp.seq)

            // 5. ACK the response
            val respLen = tcpPayload(resp).size
            val ackSeg = buildClientTcp(
                clientPort, port, TcpSeq.add(c0, (1 + request.size).toLong()), TcpSeq.add(y, (1 + respLen).toLong()),
                TcpFlags.ACK,
            )
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, port,
                TcpPacket.parse(ackSeg, 20, ackSeg.size - 20)!!, ackSeg, 20, 0,
            )

            // 6. FIN, then expect the relay to complete the close (FIN from server side)
            val finSeg = buildClientTcp(
                clientPort, port, TcpSeq.add(c0, (1 + request.size).toLong()), TcpSeq.add(y, (1 + respLen).toLong()),
                TcpFlags.FIN or TcpFlags.ACK,
            )
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, port,
                TcpPacket.parse(finSeg, 20, finSeg.size - 20)!!, finSeg, 20, 0,
            )

            val fin = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                if (ip == null || ip.protocol != IpConstants.PROTO_TCP) return@awaitPacket false
                val tcp = TcpPacket.parse(p, ip.payloadOffset, ip.totalLength - ip.payloadOffset)!!
                tcp.dstPort == clientPort && tcp.fin
            }
            assertNotNull("no FIN from relay", fin)

            relay.stop()
        } finally {
            server.close()
        }
    }

    @Test
    fun `tcp dns filtering blocks and forwards`() {
        val dnsPort = freePort()
        val server = ServerSocket(dnsPort)
        val received = AtomicInteger(0)
        val serverThread = Thread {
            runCatching {
                val s = server.accept()
                val input = s.getInputStream()
                val lenBytes = ByteArray(2)
                var read = 0
                while (read < 2) {
                    val n = input.read(lenBytes, read, 2 - read)
                    if (n < 0) break
                    read += n
                }
                val msgLen = ((lenBytes[0].toInt() and 0xFF) shl 8) or (lenBytes[1].toInt() and 0xFF)
                val msg = ByteArray(msgLen)
                var off = 0
                while (off < msgLen) {
                    val n = input.read(msg, off, msgLen - off)
                    if (n < 0) break
                    off += n
                }
                received.incrementAndGet()
                val id = ((msg[0].toInt() and 0xFF) shl 8) or (msg[1].toInt() and 0xFF)
                val resp = ByteArray(2 + 16)
                ByteOrder.putUInt16(resp, 0, 16)
                ByteOrder.putUInt16(resp, 2, id)
                ByteOrder.putUInt16(resp, 4, 0x8180)
                ByteOrder.putUInt16(resp, 6, 1)
                s.getOutputStream().write(resp)
                s.getOutputStream().flush()
                s.close()
            }
            server.close()
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val rules = blockingRules(listOf("blocked.example"))
            val relay = TcpRelay(
                writer, protector, AtomicReference(rules), 1280,
                onBlocked = {},
                dnsPort = dnsPort,
            )
            relay.start()

            val clientPort = 43003
            val c0 = 77_000L

            // Handshake
            val syn = buildClientTcp(clientPort, dnsPort, c0, 0, TcpFlags.SYN)
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, dnsPort,
                TcpPacket.parse(syn, 20, syn.size - 20)!!, syn, 20, 0,
            )
            val synAck = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                ip != null && ip.protocol == IpConstants.PROTO_TCP
            }
            assertNotNull("no SYN-ACK for tcp dns", synAck)
            val synAckTcp = parseTcp(synAck!!)
            val y = synAckTcp.seq
            assertEquals(TcpSeq.add(c0, 1), synAckTcp.ack)

            val ackSeg = buildClientTcp(
                clientPort, dnsPort, TcpSeq.add(c0, 1), TcpSeq.add(y, 1), TcpFlags.ACK,
            )
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, dnsPort,
                TcpPacket.parse(ackSeg, 20, ackSeg.size - 20)!!, ackSeg, 20, 0,
            )

            fun framedQuery(name: String, id: Int): ByteArray {
                val nameBytes = DnsCodec.encodeNameLabels(name)
                val msg = ByteArray(12 + nameBytes.size + 4)
                ByteOrder.putUInt16(msg, 0, id)
                ByteOrder.putUInt16(msg, 2, 0x0100)
                ByteOrder.putUInt16(msg, 4, 1)
                System.arraycopy(nameBytes, 0, msg, 12, nameBytes.size)
                ByteOrder.putUInt16(msg, 12 + nameBytes.size, 1)
                ByteOrder.putUInt16(msg, 12 + nameBytes.size + 2, 1)
                val framed = ByteArray(2 + msg.size)
                ByteOrder.putUInt16(framed, 0, msg.size)
                System.arraycopy(msg, 0, framed, 2, msg.size)
                return framed
            }

            // Blocked query → local NXDOMAIN, upstream untouched.
            val blockedQuery = framedQuery("blocked.example", 0x3333)
            val seg1 = buildClientTcp(
                clientPort, dnsPort, TcpSeq.add(c0, 1), TcpSeq.add(y, 1),
                TcpFlags.PSH or TcpFlags.ACK, blockedQuery,
            )
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, dnsPort,
                TcpPacket.parse(seg1, 20, seg1.size - 20)!!, seg1, 20, blockedQuery.size,
            )

            val blockedResp = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                if (ip == null || ip.protocol != IpConstants.PROTO_TCP) return@awaitPacket false
                val tcp = TcpPacket.parse(p, ip.payloadOffset, ip.totalLength - ip.payloadOffset)!!
                tcp.dstPort == clientPort && tcp.payloadLength > 0
            }
            assertNotNull("no response for blocked tcp dns", blockedResp)
            val blockedPayload = tcpPayload(blockedResp!!)
            assertTrue(blockedPayload.size >= 2 + 12)
            val blockedMsgLen = ((blockedPayload[0].toInt() and 0xFF) shl 8) or (blockedPayload[1].toInt() and 0xFF)
            assertEquals(blockedPayload.size - 2, blockedMsgLen)
            assertEquals(0x3333, IpPacket.readUInt16(blockedPayload, 2))
            assertEquals(3, IpPacket.readUInt16(blockedPayload, 4) and 0x000F)
            val blockedName = DnsCodec.decodeName(blockedPayload, 2 + 12, 2 + blockedMsgLen)!!.first
            assertEquals("blocked.example", blockedName)
            assertEquals("upstream must not see blocked tcp query", 0, received.get())

            // ACK the relay's response so the connection keeps flowing.
            val blockedLen = blockedPayload.size
            val ack2 = buildClientTcp(
                clientPort, dnsPort, TcpSeq.add(c0, (1 + blockedQuery.size).toLong()),
                TcpSeq.add(y, (1 + blockedLen).toLong()), TcpFlags.ACK,
            )
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, dnsPort,
                TcpPacket.parse(ack2, 20, ack2.size - 20)!!, ack2, 20, 0,
            )

            // Allowed query → forwarded upstream, canned response relayed back.
            val okQuery = framedQuery("ok.example", 0x4444)
            val seg2 = buildClientTcp(
                clientPort, dnsPort, TcpSeq.add(c0, (1 + blockedQuery.size).toLong()),
                TcpSeq.add(y, (1 + blockedLen).toLong()), TcpFlags.PSH or TcpFlags.ACK, okQuery,
            )
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, dnsPort,
                TcpPacket.parse(seg2, 20, seg2.size - 20)!!, seg2, 20, okQuery.size,
            )

            val okResp = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                if (ip == null || ip.protocol != IpConstants.PROTO_TCP) return@awaitPacket false
                val tcp = TcpPacket.parse(p, ip.payloadOffset, ip.totalLength - ip.payloadOffset)!!
                tcp.dstPort == clientPort && tcp.payloadLength > 0
            }
            assertNotNull("no response for allowed tcp dns", okResp)
            val okPayload = tcpPayload(okResp!!)
            assertTrue(okPayload.size >= 2 + 16)
            assertEquals(0x4444, IpPacket.readUInt16(okPayload, 2))
            assertEquals(0, IpPacket.readUInt16(okPayload, 4) and 0x000F)
            assertEquals("upstream must receive allowed tcp query", 1, received.get())

            relay.stop()
        } finally {
            server.close()
        }
    }

    @Test
    fun `tcp relay streams large payload with backpressure`() {
        val payload = ByteArray(200 * 1024) { (it % 251).toByte() }
        val port = freePort()
        val server = ServerSocket(port)
        val serverThread = Thread {
            runCatching {
                val s = server.accept()
                val req = ByteArray(4)
                s.getInputStream().read(req)
                s.getOutputStream().write(payload)
                s.getOutputStream().flush()
                Thread.sleep(300)
                s.close()
            }
            server.close()
        }
        serverThread.isDaemon = true
        serverThread.start()

        try {
            val relay = TcpRelay(writer, protector, AtomicReference(BlockingRules.passthrough()), 1280)
            relay.start()

            val clientPort = 43010
            val c0 = 5_000_000L
            val request = "BIG!\n".toByteArray()

            // Handshake
            val syn = buildClientTcp(clientPort, port, c0, 0, TcpFlags.SYN)
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, port,
                TcpPacket.parse(syn, 20, syn.size - 20)!!, syn, 20, 0,
            )
            val synAck = awaitPacket { p ->
                val ip = IpPacket.parse(p, 0, p.size)
                ip != null && ip.protocol == IpConstants.PROTO_TCP
            }
            assertNotNull("no SYN-ACK", synAck)
            val y = parseTcp(synAck!!).seq

            val reqSeg = buildClientTcp(
                clientPort, port, TcpSeq.add(c0, 1), TcpSeq.add(y, 1),
                TcpFlags.PSH or TcpFlags.ACK, request,
            )
            relay.handle(
                IpConstants.IPV4, clientAddr, clientPort, serverAddr, port,
                TcpPacket.parse(reqSeg, 20, reqSeg.size - 20)!!, reqSeg, 20, request.size,
            )

            // Consume the stream, ACKing contiguously as a real client would.
            val received = ByteArrayOutputStream()
            var expectedSeq = TcpSeq.add(y, 1)
            var finSeen = false
            val deadline = System.currentTimeMillis() + 30_000
            while (received.size() < payload.size && System.currentTimeMillis() < deadline && !finSeen) {
                val pkt = awaitPacket(timeoutMs = 3000) { p ->
                    val ip = IpPacket.parse(p, 0, p.size)
                    if (ip == null || ip.protocol != IpConstants.PROTO_TCP) return@awaitPacket false
                    val tcp = TcpPacket.parse(p, ip.payloadOffset, ip.totalLength - ip.payloadOffset)!!
                    tcp.dstPort == clientPort
                } ?: break
                val tcp = parseTcp(pkt)
                if (tcp.fin) {
                    finSeen = true
                    break
                }
                val seg = tcpPayload(pkt)
                if (seg.isNotEmpty()) {
                    if (TcpSeq.eq(tcp.seq, expectedSeq)) {
                        received.write(seg)
                        expectedSeq = TcpSeq.add(expectedSeq, seg.size.toLong())
                    }
                    // Always ACK the contiguous prefix; re-ACKs tolerate duplicates.
                }
                val ackSeg = buildClientTcp(
                    clientPort, port, TcpSeq.add(c0, (1 + request.size).toLong()), expectedSeq, TcpFlags.ACK,
                )
                relay.handle(
                    IpConstants.IPV4, clientAddr, clientPort, serverAddr, port,
                    TcpPacket.parse(ackSeg, 20, ackSeg.size - 20)!!, ackSeg, 20, 0,
                )
            }

            assertTrue("expected full payload, got ${received.size()}", received.size() >= payload.size)
            assertArrayEquals(payload, received.toByteArray())
            relay.stop()
        } finally {
            server.close()
        }
    }

    @Test
    fun `tcp relay resets when destination is unreachable`() {
        val deadPort = freePort() // nothing listens here
        val relay = TcpRelay(writer, protector, AtomicReference(BlockingRules.passthrough()), 1280)
        relay.start()

        val syn = buildClientTcp(43002, deadPort, 500L, 0, TcpFlags.SYN)
        relay.handle(
            IpConstants.IPV4, clientAddr, 43002, serverAddr, deadPort,
            TcpPacket.parse(syn, 20, syn.size - 20)!!, syn, 20, 0,
        )

        val rst = awaitPacket(timeoutMs = 15000) { p ->
            val ip = IpPacket.parse(p, 0, p.size)
            if (ip == null || ip.protocol != IpConstants.PROTO_TCP) return@awaitPacket false
            val tcp = TcpPacket.parse(p, ip.payloadOffset, ip.totalLength - ip.payloadOffset)!!
            tcp.dstPort == 43002 && tcp.rst
        }
        assertNotNull("expected RST after connect failure", rst)
        relay.stop()
    }
}
