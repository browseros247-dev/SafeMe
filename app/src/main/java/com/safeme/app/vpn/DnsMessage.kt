package com.safeme.app.vpn

/**
 * Minimal DNS wire-format codec: enough to parse the question section of a
 * query, echo it in a synthesized response, and decode names with compression
 * pointers. Used by the DNS filtering relay.
 */
class DnsQuestion(
    val name: String,
    val type: Int,
    val cls: Int,
)

class DnsQuery(
    val id: Int,
    val flags: Int,
    val questions: List<DnsQuestion>,
)

object DnsCodec {

    private const val HEADER_LEN = 12
    private const val MAX_NAME_LENGTH = 255
    private const val POINTER_MASK = 0xC0

    /**
     * Parses a DNS message. Returns null if the header or question section is
     * malformed, or if the message is a response (QR=1).
     */
    fun parseQuery(message: ByteArray, offset: Int = 0, length: Int = message.size - offset): DnsQuery? {
        if (length < HEADER_LEN) return null
        val id = IpPacket.readUInt16(message, offset)
        val flags = IpPacket.readUInt16(message, offset + 2)
        if (flags and 0x8000 != 0) return null // QR=1 → this is a response, not a query
        val qdCount = IpPacket.readUInt16(message, offset + 4)
        if (qdCount == 0) return null

        var cursor = offset + HEADER_LEN
        val questions = ArrayList<DnsQuestion>(qdCount)
        for (i in 0 until qdCount) {
            val nameResult = decodeName(message, cursor, offset + length) ?: return null
            val name = nameResult.first
            cursor = nameResult.second
            if (cursor + 4 > offset + length) return null
            val type = IpPacket.readUInt16(message, cursor)
            val cls = IpPacket.readUInt16(message, cursor + 2)
            questions.add(DnsQuestion(name, type, cls))
            cursor += 4
        }
        return DnsQuery(id = id, flags = flags, questions = questions)
    }

    /**
     * Decodes a DNS name starting at [start]. Returns the name (lowercased) and
     * the offset just past the name. Supports compression pointers (RFC 1035
     * 4.1.4). Returns null on malformed input.
     */
    fun decodeName(message: ByteArray, start: Int, end: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var cursor = start
        var jumped = false
        var returnOffset = -1
        var steps = 0
        var totalLength = 0

        while (true) {
            if (cursor >= end || steps > 128) return null
            val len = message[cursor].toInt() and 0xFF
            if (len and POINTER_MASK == POINTER_MASK) {
                if (cursor + 1 >= end) return null
                val pointer = ((len and 0x3F) shl 8) or (message[cursor + 1].toInt() and 0xFF)
                if (!jumped) {
                    returnOffset = cursor + 2
                    jumped = true
                }
                if (pointer >= end) return null
                cursor = pointer
                steps++
                continue
            }
            if (len and POINTER_MASK != 0) return null // unknown label type
            cursor++
            if (len == 0) break
            if (cursor + len > end) return null
            totalLength += len + 1
            if (totalLength > MAX_NAME_LENGTH) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) {
                val c = message[cursor + i].toInt() and 0xFF
                sb.append(if (c in 0x21..0x7E) c.toChar() else '?')
            }
            cursor += len
            steps++
        }
        return Pair(sb.toString().lowercase(), if (returnOffset >= 0) returnOffset else cursor)
    }

    /** Encodes a domain name in uncompressed wire format (without trailing zero). */
    fun encodeNameLabels(name: String): ByteArray {
        val clean = name.trim().trimEnd('.').lowercase()
        val labels = clean.split('.')
        var size = 0
        for (label in labels) {
            if (label.isEmpty()) continue
            val l = label.toByteArray(Charsets.US_ASCII).size
            if (l > 63) continue
            size += 1 + l
        }
        val out = ByteArray(size + 1)
        var cursor = 0
        for (label in labels) {
            if (label.isEmpty()) continue
            val bytes = label.toByteArray(Charsets.US_ASCII)
            if (bytes.size > 63) continue
            out[cursor] = bytes.size.toByte()
            System.arraycopy(bytes, 0, out, cursor + 1, bytes.size)
            cursor += 1 + bytes.size
        }
        out[cursor] = 0
        return out
    }

    /**
     * Builds a DNS response with the given RCODE that echoes the question
     * section of [query] (needed so clients can match the response). Used to
     * synthesize NXDOMAIN (3) for blocked domains.
     */
    fun buildSyntheticResponse(query: DnsQuery, rcode: Int = 3): ByteArray {
        var size = HEADER_LEN
        val encodedQuestions = ArrayList<ByteArray>()
        for (q in query.questions) {
            val nameBytes = encodeNameLabels(q.name)
            encodedQuestions.add(nameBytes)
            size += nameBytes.size + 4
        }
        val response = ByteArray(size)
        IpPacket.writeUInt16(response, 0, query.id)

        // QR=1 | opcode (echo) | RD (echo) | RA=1 | RCODE
        val outFlags = 0x8000 or
            (query.flags and 0x7800) or
            (query.flags and 0x0100) or
            0x0080 or
            (rcode and 0x0F)
        IpPacket.writeUInt16(response, 2, outFlags)
        IpPacket.writeUInt16(response, 4, query.questions.size)
        IpPacket.writeUInt16(response, 6, 0)
        IpPacket.writeUInt16(response, 8, 0)
        IpPacket.writeUInt16(response, 10, 0)

        var cursor = HEADER_LEN
        for ((i, q) in query.questions.withIndex()) {
            val nameBytes = encodedQuestions[i]
            System.arraycopy(nameBytes, 0, response, cursor, nameBytes.size)
            cursor += nameBytes.size
            IpPacket.writeUInt16(response, cursor, q.type)
            IpPacket.writeUInt16(response, cursor + 2, q.cls)
            cursor += 4
        }
        return response
    }

    /**
     * Returns the (lowercased) question name of a query message, or null if it
     * cannot be parsed or carries no questions.
     */
    fun questionName(message: ByteArray, offset: Int = 0, length: Int = message.size - offset): String? {
        val query = parseQuery(message, offset, length) ?: return null
        return query.questions.firstOrNull()?.name
    }

    /** True if the message looks like a valid DNS query (used for TCP port 53 classification). */
    fun looksLikeDns(message: ByteArray, offset: Int, length: Int): Boolean {
        if (length < HEADER_LEN) return false
        val qdCount = IpPacket.readUInt16(message, offset + 4)
        if (qdCount == 0 || qdCount > 100) return false
        val anCount = IpPacket.readUInt16(message, offset + 6)
        val nsCount = IpPacket.readUInt16(message, offset + 8)
        val arCount = IpPacket.readUInt16(message, offset + 10)
        if (anCount > 0 || nsCount > 0) return false
        if (arCount > 100) return false
        return true
    }
}
