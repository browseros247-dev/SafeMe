package com.safeme.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessageTest {

    /** Builds a minimal DNS query for [name] with a 16-bit id. */
    private fun buildQuery(name: String, id: Int = 0x1234, qtype: Int = 1): ByteArray {
        val nameBytes = DnsCodec.encodeNameLabels(name)
        val query = ByteArray(12 + nameBytes.size + 4)
        ByteOrder.putUInt16(query, 0, id)
        ByteOrder.putUInt16(query, 2, 0x0100) // RD
        ByteOrder.putUInt16(query, 4, 1) // QDCOUNT
        System.arraycopy(nameBytes, 0, query, 12, nameBytes.size)
        ByteOrder.putUInt16(query, 12 + nameBytes.size, qtype)
        ByteOrder.putUInt16(query, 12 + nameBytes.size + 2, 1)
        return query
    }

    @Test
    fun `parse simple query`() {
        val query = buildQuery("www.Example.COM")
        val parsed = DnsCodec.parseQuery(query)
        assertNotNull(parsed)
        parsed!!
        assertEquals(0x1234, parsed.id)
        assertEquals(1, parsed.questions.size)
        assertEquals("www.example.com", parsed.questions[0].name)
        assertEquals(1, parsed.questions[0].type)
        assertEquals(1, parsed.questions[0].cls)
    }

    @Test
    fun `reject responses and malformed input`() {
        val response = buildQuery("example.com")
        ByteOrder.putUInt16(response, 2, 0x8180) // QR=1
        assertNull(DnsCodec.parseQuery(response))

        assertNull(DnsCodec.parseQuery(ByteArray(4)))
        assertNull(DnsCodec.parseQuery(ByteArray(12)))
    }

    @Test
    fun `decode names with compression pointer`() {
        // Message: [header: 12 bytes] example.com (13 bytes @12) www + pointer @25
        val exampleCom = DnsCodec.encodeNameLabels("example.com") // includes trailing zero
        val message = ByteArray(12 + exampleCom.size + 2 + 3 + 2)
        System.arraycopy(exampleCom, 0, message, 12, exampleCom.size)
        val pointerPos = 12 + exampleCom.size
        // "www" label then a pointer to offset 12
        message[pointerPos] = 3
        message[pointerPos + 1] = 'w'.code.toByte()
        message[pointerPos + 2] = 'w'.code.toByte()
        message[pointerPos + 3] = 'w'.code.toByte()
        message[pointerPos + 4] = (0xC0).toByte()
        message[pointerPos + 5] = 12

        val decoded = DnsCodec.decodeName(message, pointerPos, message.size)
        assertNotNull(decoded)
        assertEquals("www.example.com", decoded!!.first)
        assertEquals(pointerPos + 6, decoded.second)
    }

    @Test
    fun `synthetic block response echoes id and question with nxdomain rcode`() {
        val query = buildQuery("pornhub.com", id = 0xBEEF)
        val parsed = DnsCodec.parseQuery(query)!!
        val response = DnsCodec.buildSyntheticResponse(parsed, rcode = 3)

        assertEquals(12 + DnsCodec.encodeNameLabels("pornhub.com").size + 4, response.size)

        val id = IpPacket.readUInt16(response, 0)
        assertEquals(0xBEEF, id)

        val flags = IpPacket.readUInt16(response, 2)
        assertTrue(flags and 0x8000 != 0) // QR=1
        assertEquals(3, flags and 0x000F) // RCODE=NXDOMAIN
        assertTrue(flags and 0x0080 != 0) // RA

        assertEquals(1, IpPacket.readUInt16(response, 4)) // QDCOUNT echoed
        assertEquals(0, IpPacket.readUInt16(response, 6)) // ANCOUNT=0

        // Question section must echo the original name.
        val nameResult = DnsCodec.decodeName(response, 12, response.size)
        assertEquals("pornhub.com", nameResult!!.first)
    }

    @Test
    fun `question name extraction`() {
        val query = buildQuery("ads.example.net")
        assertEquals("ads.example.net", DnsCodec.questionName(query))
        assertNull(DnsCodec.questionName(ByteArray(0)))
    }

    @Test
    fun `encode name handles trailing dot and case`() {
        val encoded = DnsCodec.encodeNameLabels("Example.COM.")
        val decoded = DnsCodec.decodeName(encoded, 0, encoded.size)
        assertEquals("example.com", decoded!!.first)
        assertEquals(encoded.size, decoded.second)
    }

    @Test
    fun `looks like dns`() {
        val query = buildQuery("example.com")
        assertTrue(DnsCodec.looksLikeDns(query, 0, query.size))
    }
}
