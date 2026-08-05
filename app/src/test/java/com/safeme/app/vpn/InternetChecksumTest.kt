package com.safeme.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.net.InetAddress

class InternetChecksumTest {

    @Test
    fun `classic ipv4 header checksum vector`() {
        // Well-known example: an IPv4 header with a zero checksum field.
        val header = byteArrayOf(
            0x45, 0x00, 0x00, 0x73, 0x00, 0x00, 0x40, 0x00,
            0x40, 0x11, 0x00, 0x00, (-0x40).toByte(), (-0x58).toByte(), 0x00, 0x01,
            (-0x40).toByte(), (-0x58).toByte(), 0x00, (-0x39).toByte(),
        )
        assertEquals(0xB861, InternetChecksum.compute(header))
    }

    @Test
    fun `complement of computed checksum is zero for valid header`() {
        val header = byteArrayOf(
            0x45, 0x00, 0x00, 0x73, 0x00, 0x00, 0x40, 0x00,
            0x40, 0x11, 0x00, 0x00, (-0x40).toByte(), (-0x58).toByte(), 0x00, 0x01,
            (-0x40).toByte(), (-0x58).toByte(), 0x00, (-0x39).toByte(),
        )
        val checksum = InternetChecksum.compute(header)
        ByteOrder.putUInt16(header, 10, checksum)
        // The full-header sum (including the checksum field) must fold to all-ones.
        val total = InternetChecksum.fold(InternetChecksum.sum(header, 0, header.size))
        assertEquals(0xFFFF, total)
    }

    @Test
    fun `summation is order-independent`() {
        val a = "hello world".toByteArray()
        val b = a.copyOf()
        b.reverse()
        assertEquals(
            InternetChecksum.compute(a),
            InternetChecksum.compute(b),
        )
    }

    @Test
    fun `odd length is handled with trailing zero byte`() {
        val data = "abc".toByteArray() // 3 bytes
        val checksum = InternetChecksum.compute(data)
        val padded = "abc\u0000".toByteArray()
        assertEquals(checksum, InternetChecksum.compute(padded))
    }

    @Test
    fun `tcp pseudo header checksum is commutative under address swap and sensitive to data`() {
        val src = InetAddress.getByName("192.168.1.5")
        val dst = InetAddress.getByName("93.184.216.34")
        val segment = TcpPacket.build(12345, 443, 1L, 1L, TcpFlags.ACK, 65535, byteArrayOf(1, 2, 3))
        val c1 = InternetChecksum.transportChecksum(IpConstants.IPV4, src, dst, IpConstants.PROTO_TCP, segment)
        val c2 = InternetChecksum.transportChecksum(IpConstants.IPV4, dst, src, IpConstants.PROTO_TCP, segment)
        // Addition is commutative: swapping the pseudo-header addresses yields
        // the same sum (the direction is encoded by the packet itself).
        assertEquals(c1, c2)

        val other = TcpPacket.build(12345, 443, 1L, 1L, TcpFlags.ACK, 65535, byteArrayOf(9, 9, 9))
        val c3 = InternetChecksum.transportChecksum(IpConstants.IPV4, src, dst, IpConstants.PROTO_TCP, other)
        assertNotEquals(c1, c3)
    }
}
