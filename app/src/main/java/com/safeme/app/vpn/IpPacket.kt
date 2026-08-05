package com.safeme.app.vpn

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Low-level IP / TCP / UDP / ICMP parsing and building primitives used by the
 * userspace TUN engine. Everything in this file is pure JVM code so it can be
 * unit-tested without an Android device.
 */
object IpConstants {
    const val IPV4 = 4
    const val IPV6 = 6

    const val PROTO_ICMP = 1
    const val PROTO_TCP = 6
    const val PROTO_UDP = 17
    const val PROTO_ICMPV6 = 58

    // IPv6 extension header types.
    const val IPV6_HOP_BY_HOP = 0
    const val IPV6_ROUTING = 43
    const val IPV6_FRAGMENT = 44
    const val IPV6_DEST_OPTS = 60
    const val IPV6_AH = 51
    const val IPV6_NO_NEXT = 59

    // ICMP types (IPv4).
    const val ICMP_ECHO_REPLY = 0
    const val ICMP_ECHO_REQUEST = 8
    const val ICMP_DEST_UNREACHABLE = 3

    // ICMPv6 types.
    const val ICMPV6_ECHO_REQUEST = 128
    const val ICMPV6_ECHO_REPLY = 129
}

/** Parsed IP header information (v4 or v6). */
class IpHeader(
    val version: Int,
    val protocol: Int,
    val src: InetAddress,
    val dst: InetAddress,
    /** Total length of the IP packet (header + payload), in bytes. */
    val totalLength: Int,
    /** Offset in the original byte array where the transport header begins. */
    val payloadOffset: Int,
    /** True if the packet is an IP fragment. */
    val isFragment: Boolean,
    /** Fragment offset in bytes (0 for non-fragments). */
    val fragmentOffset: Int,
    /** True if more fragments follow. */
    val moreFragments: Boolean,
    /** Fragment identification (IPv4: 16-bit, IPv6: 32-bit). */
    val identification: Long,
    /** IPv4 header checksum as read from the wire (0 for IPv6). */
    val headerChecksum: Int,
    /** IPv4 header length in bytes (0 for IPv6). */
    val headerLength: Int,
    /** IPv4 TTL / IPv6 hop limit (informational). */
    val hopLimit: Int,
)

object IpPacket {

    private const val IPV4_IHL_MASK = 0x0F
    private const val IPV4_MF_FLAG = 0x2000
    private const val IPV4_FRAG_OFFSET_MASK = 0x1FFF
    private const val IPV6_FRAG_M_MASK = 0x0001

    /**
     * Parses an IPv4 or IPv6 packet. Returns null if the packet is malformed or
     * uses an unknown transport protocol chain.
     */
    fun parse(packet: ByteArray, offset: Int, length: Int): IpHeader? {
        if (length < 20) return null
        val version = (packet[offset].toInt() ushr 4) and 0x0F
        return when (version) {
            IpConstants.IPV4 -> parseIpv4(packet, offset, length)
            IpConstants.IPV6 -> parseIpv6(packet, offset, length)
            else -> null
        }
    }

    private fun parseIpv4(packet: ByteArray, offset: Int, length: Int): IpHeader? {
        val headerLength = ((packet[offset].toInt() and IPV4_IHL_MASK) shl 2)
        if (headerLength < 20 || headerLength > length) return null
        val totalLength = ((packet[offset + 2].toInt() and 0xFF) shl 8) or
            (packet[offset + 3].toInt() and 0xFF)
        if (totalLength < headerLength || totalLength > length) return null

        val protocol = packet[offset + 9].toInt() and 0xFF
        val fragField = ((packet[offset + 6].toInt() and 0xFF) shl 8) or
            (packet[offset + 7].toInt() and 0xFF)
        val moreFragments = (fragField and IPV4_MF_FLAG) != 0
        val fragmentOffset = (fragField and IPV4_FRAG_OFFSET_MASK) shl 3
        val identification = (((packet[offset + 4].toInt() and 0xFF).toLong()) shl 8) or
            (packet[offset + 5].toInt() and 0xFF).toLong()

        val src = InetAddress.getByAddress(packet.copyOfRange(offset + 12, offset + 16))
        val dst = InetAddress.getByAddress(packet.copyOfRange(offset + 16, offset + 20))

        return IpHeader(
            version = IpConstants.IPV4,
            protocol = protocol,
            src = src,
            dst = dst,
            totalLength = totalLength,
            payloadOffset = offset + headerLength,
            isFragment = fragmentOffset != 0 || moreFragments,
            fragmentOffset = fragmentOffset,
            moreFragments = moreFragments,
            identification = identification,
            headerChecksum = ((packet[offset + 10].toInt() and 0xFF) shl 8) or
                (packet[offset + 11].toInt() and 0xFF),
            headerLength = headerLength,
            hopLimit = packet[offset + 8].toInt() and 0xFF,
        )
    }

    private fun parseIpv6(packet: ByteArray, offset: Int, length: Int): IpHeader? {
        if (length < 40) return null
        val payloadLength = ((packet[offset + 4].toInt() and 0xFF) shl 8) or
            (packet[offset + 5].toInt() and 0xFF)
        if (40 + payloadLength > length) return null

        val src = InetAddress.getByAddress(packet.copyOfRange(offset + 8, offset + 24))
        val dst = InetAddress.getByAddress(packet.copyOfRange(offset + 24, offset + 40))

        var nextHeader = packet[offset + 6].toInt() and 0xFF
        var cursor = offset + 40
        val end = offset + 40 + payloadLength
        var fragmentOffset = 0
        var moreFragments = false
        var identification = 0L
        var isFragment = false

        // Walk the extension header chain.
        while (cursor < end) {
            when (nextHeader) {
                IpConstants.IPV6_HOP_BY_HOP, IpConstants.IPV6_ROUTING, IpConstants.IPV6_DEST_OPTS,
                IpConstants.IPV6_AH -> {
                    if (cursor + 2 > end) return null
                    val hdrExtLen = (packet[cursor + 1].toInt() and 0xFF)
                    val hdrLen = (hdrExtLen + 1) * 8
                    if (cursor + hdrLen > end) return null
                    nextHeader = packet[cursor].toInt() and 0xFF
                    cursor += hdrLen
                }
                IpConstants.IPV6_FRAGMENT -> {
                    if (cursor + 8 > end) return null
                    nextHeader = packet[cursor].toInt() and 0xFF
                    val fragField = ((packet[cursor + 2].toInt() and 0xFF) shl 8) or
                        (packet[cursor + 3].toInt() and 0xFF)
                    fragmentOffset = (fragField ushr 3) and 0x1FFF
                    fragmentOffset = fragmentOffset shl 3
                    moreFragments = (fragField and IPV6_FRAG_M_MASK) != 0
                    identification =
                        ((packet[cursor + 4].toLong() and 0xFF) shl 24) or
                        ((packet[cursor + 5].toLong() and 0xFF) shl 16) or
                        ((packet[cursor + 6].toLong() and 0xFF) shl 8) or
                        (packet[cursor + 7].toLong() and 0xFF)
                    isFragment = fragmentOffset != 0 || moreFragments
                    cursor += 8
                }
                else -> {
                    // Transport header (or no next header).
                    return IpHeader(
                        version = IpConstants.IPV6,
                        protocol = nextHeader,
                        src = src,
                        dst = dst,
                        totalLength = length,
                        payloadOffset = cursor,
                        isFragment = isFragment,
                        fragmentOffset = fragmentOffset,
                        moreFragments = moreFragments,
                        identification = identification,
                        headerChecksum = 0,
                        headerLength = cursor - offset,
                        hopLimit = packet[offset + 7].toInt() and 0xFF,
                    )
                }
            }
        }
        return null
    }

    /**
     * Reads the 2-byte big-endian integer at [offset] in [packet].
     */
    fun readUInt16(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

    fun writeUInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }
}

/** RFC 1071 internet checksum. */
object InternetChecksum {

    /** Computes the one's complement checksum (RFC 1071 final form). */
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset, initial: Int = 0): Int {
        return complement(sum(data, offset, length, initial))
    }

    /** Accumulates 16-bit words without the final fold; useful for checksum chains. */
    fun sum(data: ByteArray, offset: Int, length: Int, initial: Int = 0): Int {
        var sum = initial
        var i = offset
        val end = offset + (length and -2)
        while (i < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if ((offset + length) and 1 == 1) {
            sum += (data[end].toInt() and 0xFF) shl 8
        }
        return sum
    }

    fun fold(sum: Int): Int {
        var s = sum
        while (s ushr 16 != 0) {
            s = (s and 0xFFFF) + (s ushr 16)
        }
        return s and 0xFFFF
    }

    fun complement(sum: Int): Int = (fold(sum).inv()) and 0xFFFF

    /**
     * Computes the TCP/UDP pseudo-header checksum (RFC 793 / RFC 8200).
     * [transportData] must contain the full transport header + payload; the
     * checksum field itself is expected to be zero.
     */
    fun transportChecksum(
        version: Int,
        src: InetAddress,
        dst: InetAddress,
        protocol: Int,
        transportData: ByteArray,
    ): Int {
        var sum = 0
        val srcBytes = src.address
        val dstBytes = dst.address
        if (version == IpConstants.IPV4) {
            sum = sum(srcBytes, 0, 4, sum)
            sum = sum(dstBytes, 0, 4, sum)
            sum += protocol
            sum += transportData.size
        } else {
            sum = sum(srcBytes, 0, 16, sum)
            sum = sum(dstBytes, 0, 16, sum)
            sum += transportData.size
            sum += protocol
        }
        sum = sum(transportData, 0, transportData.size, sum)
        return complement(sum)
    }
}

/** Parsed UDP header. */
class UdpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val length: Int,
    val payloadOffset: Int,
    val payloadLength: Int,
)

object UdpPacket {
    private const val UDP_HEADER_LEN = 8

    fun parse(packet: ByteArray, offset: Int, length: Int): UdpHeader? {
        if (length < UDP_HEADER_LEN) return null
        val srcPort = IpPacket.readUInt16(packet, offset)
        val dstPort = IpPacket.readUInt16(packet, offset + 2)
        val udpLength = IpPacket.readUInt16(packet, offset + 4)
        if (udpLength < UDP_HEADER_LEN || udpLength > length) return null
        return UdpHeader(
            srcPort = srcPort,
            dstPort = dstPort,
            length = udpLength,
            payloadOffset = offset + UDP_HEADER_LEN,
            payloadLength = udpLength - UDP_HEADER_LEN,
        )
    }

    /** Builds a UDP packet (without IP header) for writing back to the TUN. */
    fun build(srcPort: Int, dstPort: Int, payload: ByteArray, payloadOffset: Int = 0, payloadLength: Int = payload.size - payloadOffset): ByteArray {
        val udp = ByteArray(UDP_HEADER_LEN + payloadLength)
        IpPacket.writeUInt16(udp, 0, srcPort)
        IpPacket.writeUInt16(udp, 2, dstPort)
        IpPacket.writeUInt16(udp, 4, udp.size)
        IpPacket.writeUInt16(udp, 6, 0) // checksum, computed by caller
        System.arraycopy(payload, payloadOffset, udp, UDP_HEADER_LEN, payloadLength)
        return udp
    }
}

/** Parsed TCP header. */
class TcpHeader(
    val srcPort: Int,
    val dstPort: Int,
    val seq: Long,
    val ack: Long,
    val dataOffset: Int,
    val flags: Int,
    val window: Int,
    val payloadOffset: Int,
    val payloadLength: Int,
) {
    val syn: Boolean get() = flags and TcpFlags.SYN != 0
    val ackFlag: Boolean get() = flags and TcpFlags.ACK != 0
    val fin: Boolean get() = flags and TcpFlags.FIN != 0
    val rst: Boolean get() = flags and TcpFlags.RST != 0
    val psh: Boolean get() = flags and TcpFlags.PSH != 0
}

object TcpFlags {
    const val FIN = 0x01
    const val SYN = 0x02
    const val RST = 0x04
    const val PSH = 0x08
    const val ACK = 0x10
    const val URG = 0x20
}

object TcpPacket {

    private const val MIN_TCP_HEADER = 20

    fun parse(packet: ByteArray, offset: Int, length: Int): TcpHeader? {
        if (length < MIN_TCP_HEADER) return null
        val dataOffset = ((packet[offset + 12].toInt() ushr 4) and 0x0F) shl 2
        if (dataOffset < MIN_TCP_HEADER || dataOffset > length) return null
        return TcpHeader(
            srcPort = IpPacket.readUInt16(packet, offset),
            dstPort = IpPacket.readUInt16(packet, offset + 2),
            seq = readUInt32(packet, offset + 4),
            ack = readUInt32(packet, offset + 8),
            dataOffset = dataOffset,
            flags = packet[offset + 13].toInt() and 0xFF,
            window = IpPacket.readUInt16(packet, offset + 14),
            payloadOffset = offset + dataOffset,
            payloadLength = length - dataOffset,
        )
    }

    fun readUInt32(packet: ByteArray, offset: Int): Long =
        ((packet[offset].toLong() and 0xFF) shl 24) or
            ((packet[offset + 1].toLong() and 0xFF) shl 16) or
            ((packet[offset + 2].toLong() and 0xFF) shl 8) or
            (packet[offset + 3].toLong() and 0xFF)

    fun writeUInt32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value ushr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }

    /**
     * Builds a TCP segment (without IP header). If [mss] is non-null an MSS
     * option is appended. The checksum field is left at zero and must be filled
     * by the caller using [InternetChecksum.transportChecksum].
     */
    fun build(
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        window: Int,
        payload: ByteArray? = null,
        mss: Int? = null,
    ): ByteArray {
        var optionsLength = 0
        if (mss != null) optionsLength += 4
        val headerLength = MIN_TCP_HEADER + optionsLength
        val payloadLength = payload?.size ?: 0
        val segment = ByteArray(headerLength + payloadLength)

        IpPacket.writeUInt16(segment, 0, srcPort)
        IpPacket.writeUInt16(segment, 2, dstPort)
        writeUInt32(segment, 4, seq)
        writeUInt32(segment, 8, ack)
        segment[12] = ((headerLength ushr 2) shl 4).toByte()
        segment[13] = flags.toByte()
        IpPacket.writeUInt16(segment, 14, window)
        IpPacket.writeUInt16(segment, 16, 0) // checksum placeholder

        var cursor = MIN_TCP_HEADER
        if (mss != null) {
            segment[cursor] = 2 // kind
            segment[cursor + 1] = 4 // length
            IpPacket.writeUInt16(segment, cursor + 2, mss)
            cursor += 4
        }
        if (payload != null && payload.isNotEmpty()) {
            System.arraycopy(payload, 0, segment, cursor, payloadLength)
        }
        return segment
    }
}

/** Simple big-endian helpers shared by the builders. */
object ByteOrder {
    fun putUInt16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    fun putUInt32(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value ushr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }
}
