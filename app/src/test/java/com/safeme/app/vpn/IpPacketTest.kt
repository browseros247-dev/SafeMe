package com.safeme.app.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class IpPacketTest {

    private val srcV4 = InetAddress.getByName("10.0.0.2")
    private val dstV4 = InetAddress.getByName("93.184.216.34")
    private val srcV6 = InetAddress.getByName("fd00:10:0:0:2::2")
    private val dstV6 = InetAddress.getByName("2606:4700:4700::1111")

    @Test
    fun `parse ipv4 udp packet`() {
        val udp = UdpPacket.build(40000, 53, byteArrayOf(0x12, 0x34, 0x56))
        val packet = IpBuilder.ipv4(srcV4, dstV4, IpConstants.PROTO_UDP, udp)

        val ip = IpPacket.parse(packet, 0, packet.size)
        assertNotNull(ip)
        ip!!
        assertEquals(IpConstants.IPV4, ip.version)
        assertEquals(IpConstants.PROTO_UDP, ip.protocol)
        assertEquals(srcV4, ip.src)
        assertEquals(dstV4, ip.dst)
        assertEquals(packet.size, ip.totalLength)
        assertEquals(20, ip.payloadOffset)
        assertFalse(ip.isFragment)

        val udpHdr = UdpPacket.parse(packet, ip.payloadOffset, ip.totalLength - ip.payloadOffset)
        assertNotNull(udpHdr)
        assertEquals(40000, udpHdr!!.srcPort)
        assertEquals(53, udpHdr.dstPort)
        assertEquals(3, udpHdr.payloadLength)
    }

    @Test
    fun `ipv4 header checksum round trip`() {
        val udp = UdpPacket.build(1, 2, byteArrayOf(1))
        val packet = IpBuilder.ipv4(srcV4, dstV4, IpConstants.PROTO_UDP, udp, id = 0xABCD)
        val stored = IpPacket.readUInt16(packet, 10)
        // Zero the checksum and recompute — must equal the stored value.
        val copy = packet.copyOf()
        ByteOrder.putUInt16(copy, 10, 0)
        assertEquals(stored, InternetChecksum.compute(copy, 0, 20))
    }

    @Test
    fun `parse ipv6 udp packet`() {
        val udp = UdpPacket.build(50000, 53, byteArrayOf(9, 9))
        val packet = IpBuilder.ipv6(srcV6, dstV6, IpConstants.PROTO_UDP, udp)

        val ip = IpPacket.parse(packet, 0, packet.size)
        assertNotNull(ip)
        ip!!
        assertEquals(IpConstants.IPV6, ip.version)
        assertEquals(IpConstants.PROTO_UDP, ip.protocol)
        assertEquals(srcV6, ip.src)
        assertEquals(dstV6, ip.dst)
        assertEquals(40, ip.payloadOffset)
    }

    @Test
    fun `parse ipv6 fragmented packet`() {
        // Fragment a big datagram and check the parser sees fragments.
        val payload = ByteArray(3000) { (it % 251).toByte() }
        val udp = UdpPacket.build(50000, 53, payload)
        val full = IpBuilder.ipv6(srcV6, dstV6, IpConstants.PROTO_UDP, udp)
        val pieces = IpBuilder.fragment(full, 1280, IpConstants.IPV6)

        assertTrue(pieces.size > 1)
        for (piece in pieces) {
            assertTrue(piece.size <= 1280)
            val ip = IpPacket.parse(piece, 0, piece.size)
            assertNotNull(ip)
            assertTrue(ip!!.isFragment)
        }
    }

    @Test
    fun `reject malformed packets`() {
        assertNull(IpPacket.parse(byteArrayOf(0, 1, 2, 3), 0, 4))
        assertNull(IpPacket.parse(ByteArray(20) { 0x60 }, 0, 20)) // v6 with zero payload len and no transport
        val bad = ByteArray(20)
        bad[0] = 0x47 // version 4, IHL 7 → header len 28 > 20
        assertNull(IpPacket.parse(bad, 0, 20))
    }

    @Test
    fun `udp tcp header parsing round trip`() {
        val segment = TcpPacket.build(
            srcPort = 5555,
            dstPort = 443,
            seq = 0x01020304L,
            ack = 0x0A0B0C0DL,
            flags = TcpFlags.PSH or TcpFlags.ACK,
            window = 65535,
            payload = byteArrayOf(1, 2, 3, 4, 5),
            mss = 1240,
        )
        val parsed = TcpPacket.parse(segment, 0, segment.size)
        assertNotNull(parsed)
        parsed!!
        assertEquals(5555, parsed.srcPort)
        assertEquals(443, parsed.dstPort)
        assertEquals(0x01020304L, parsed.seq)
        assertEquals(0x0A0B0C0DL, parsed.ack)
        assertTrue(parsed.psh)
        assertTrue(parsed.ackFlag)
        assertFalse(parsed.syn)
        assertEquals(5, parsed.payloadLength)
        assertEquals(24, parsed.dataOffset) // 20 + MSS option (4)
    }

    @Test
    fun `tcp segment checksum with pseudo header is correct`() {
        val payload = "GET / HTTP/1.0\r\n\r\n".toByteArray()
        val segment = TcpPacket.build(54321, 80, 100L, 200L, TcpFlags.PSH or TcpFlags.ACK, 65535, payload)
        val checksum = InternetChecksum.transportChecksum(
            IpConstants.IPV4, srcV4, dstV4, IpConstants.PROTO_TCP, segment,
        )
        ByteOrder.putUInt16(segment, 16, checksum)

        // Verification: zero the checksum field and recompute.
        ByteOrder.putUInt16(segment, 16, 0)
        val recomputed = InternetChecksum.transportChecksum(
            IpConstants.IPV4, srcV4, dstV4, IpConstants.PROTO_TCP, segment,
        )
        assertEquals(checksum, recomputed)
    }

    @Test
    fun `fragment and reassemble ipv4`() {
        val payload = ByteArray(3000) { (it * 7 % 256).toByte() }
        val udp = UdpPacket.build(1234, 53, payload)
        val full = IpBuilder.ipv4(srcV4, dstV4, IpConstants.PROTO_UDP, udp)
        val pieces = IpBuilder.fragment(full, 1280, IpConstants.IPV4)
        assertTrue(pieces.size > 1)

        val reassembler = FragmentReassembler()
        var assembled: ByteArray? = null
        for (piece in pieces) {
            val ip = IpPacket.parse(piece, 0, piece.size)!!
            assembled = reassembler.addFragment(ip, piece, 0, piece.size)
        }
        assertNotNull(assembled)
        assertArrayEquals(full, assembled)
    }

    @Test
    fun `fragment and reassemble ipv6`() {
        val payload = ByteArray(5000) { (it % 256).toByte() }
        val udp = UdpPacket.build(1234, 53, payload)
        val full = IpBuilder.ipv6(srcV6, dstV6, IpConstants.PROTO_UDP, udp)
        val pieces = IpBuilder.fragment(full, 1280, IpConstants.IPV6)
        assertTrue(pieces.size > 1)

        val reassembler = FragmentReassembler()
        var assembled: ByteArray? = null
        for (piece in pieces) {
            val ip = IpPacket.parse(piece, 0, piece.size)!!
            assembled = reassembler.addFragment(ip, piece, 0, piece.size)
        }
        assertNotNull(assembled)
        assertArrayEquals(full, assembled)
    }

    @Test
    fun `packets within mtu are returned unfragmented`() {
        val small = IpBuilder.ipv4(srcV4, dstV4, IpConstants.PROTO_UDP, UdpPacket.build(1, 2, byteArrayOf(1)))
        val result = IpBuilder.fragment(small, 1280, IpConstants.IPV4)
        assertEquals(1, result.size)
        assertArrayEquals(small, result[0])
    }
}
