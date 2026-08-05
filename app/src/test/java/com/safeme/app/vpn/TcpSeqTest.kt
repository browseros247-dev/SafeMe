package com.safeme.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TcpSeqTest {

    @Test
    fun `simple ordering`() {
        assertTrue(TcpSeq.lt(10, 20))
        assertTrue(TcpSeq.le(10, 20))
        assertFalse(TcpSeq.lt(20, 10))
        assertTrue(TcpSeq.le(20, 20))
        assertTrue(TcpSeq.eq(20, 20))
    }

    @Test
    fun `wrap around ordering`() {
        // 0xFFFFFFF0 is "before" 0x10 in 32-bit sequence space (32 bytes later).
        assertTrue(TcpSeq.lt(0xFFFFFFF0L, 0x10L))
        assertTrue(TcpSeq.lt(0xFFFFFFF0L, 0x00000010L))
        assertFalse(TcpSeq.lt(0x10L, 0xFFFFFFF0L))
    }

    @Test
    fun `addition wraps mod 2^32`() {
        assertEquals(0x10L, TcpSeq.add(0xFFFFFFF0L, 32))
        assertEquals(0L, TcpSeq.add(0xFFFFFFFFL, 1))
        assertEquals(0xFFFFFFFFL, TcpSeq.add(0, -1))
    }

    @Test
    fun `near boundary ordering`() {
        // Sequences 0x7FFFFFFF and 0x80000000 are exactly half the space apart;
        // RFC 1982 treats the smaller as "before".
        assertTrue(TcpSeq.lt(0x7FFFFFFFL, 0x80000000L))
        assertTrue(TcpSeq.eq(0x100000001L, 0x1L))
    }

    @Test
    fun `window scale parsing`() {
        // TCP header with options: NOP, WS(3,7) → header length 24.
        val header = ByteArray(24)
        header[0] = 0x04.toByte() // src port 0x0400
        header[1] = 0x00
        header[2] = 0x00
        header[3] = 0x35 // dst port 53
        // seq = 1
        header[4] = 0
        header[5] = 0
        header[6] = 0
        header[7] = 1
        // ack = 0
        header[8] = 0; header[9] = 0; header[10] = 0; header[11] = 0
        header[12] = (24 ushr 2 shl 4).toByte() // data offset = 6 words
        header[13] = TcpFlags.SYN.toByte()
        header[14] = 0x10.toByte() // window 0x1000
        header[15] = 0x00
        // options: NOP, WS
        header[20] = 1
        header[21] = 3
        header[22] = 3
        header[23] = 7

        val tcp = TcpPacket.parse(header, 0, header.size)!!
        assertEquals(7, TcpConnection.parseWindowScale(tcp, header, 0))
    }

    @Test
    fun `window scale parsing without option returns zero`() {
        val header = ByteArray(20)
        header[12] = (20 ushr 2 shl 4).toByte()
        header[13] = TcpFlags.SYN.toByte()
        val tcp = TcpPacket.parse(header, 0, header.size)!!
        assertEquals(0, TcpConnection.parseWindowScale(tcp, header, 0))
    }

    @Test
    fun `mss calculation respects mtu and family`() {
        assertEquals(1240, TcpRelay.mssFor(IpConstants.IPV4, 1280))
        assertEquals(1220, TcpRelay.mssFor(IpConstants.IPV6, 1280))
        assertEquals(1460, TcpRelay.mssFor(IpConstants.IPV4, 1500))
    }
}
