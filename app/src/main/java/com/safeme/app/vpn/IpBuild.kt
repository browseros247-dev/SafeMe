package com.safeme.app.vpn

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds complete IPv4 / IPv6 packets (headers + transport + payload) and
 * implements outbound fragmentation + inbound reassembly so that packets
 * written to the TUN never exceed the interface MTU.
 */
object IpBuilder {

    private const val IPV4_HEADER_LEN = 20
    const val IPV6_HEADER_LEN = 40
    private const val IPV6_FRAG_HEADER_LEN = 8

    /**
     * Builds an IPv4 packet around [payload] (already a complete UDP/TCP/ICMP
     * segment with a zeroed transport checksum, which is filled here if
     * [computeTransportChecksum] is true).
     */
    fun ipv4(
        src: InetAddress,
        dst: InetAddress,
        protocol: Int,
        payload: ByteArray,
        ttl: Int = 64,
        id: Int = (System.nanoTime() and 0xFFFF).toInt(),
        computeTransportChecksum: Boolean = true,
    ): ByteArray {
        val packet = ByteArray(IPV4_HEADER_LEN + payload.size)
        packet[0] = (0x45).toByte() // version 4, IHL 5
        packet[1] = 0
        ByteOrder.putUInt16(packet, 2, packet.size)
        ByteOrder.putUInt16(packet, 4, id and 0xFFFF)
        ByteOrder.putUInt16(packet, 6, 0) // flags + fragment offset
        packet[8] = ttl.toByte()
        packet[9] = protocol.toByte()
        ByteOrder.putUInt16(packet, 10, 0) // header checksum placeholder
        System.arraycopy(src.address, 0, packet, 12, 4)
        System.arraycopy(dst.address, 0, packet, 16, 4)
        System.arraycopy(payload, 0, packet, IPV4_HEADER_LEN, payload.size)

        if (computeTransportChecksum && protocol != IpConstants.PROTO_ICMP) {
            fillTransportChecksum(IpConstants.IPV4, src, dst, protocol, packet, IPV4_HEADER_LEN)
        }
        ByteOrder.putUInt16(packet, 10, InternetChecksum.compute(packet, 0, IPV4_HEADER_LEN))
        return packet
    }

    fun ipv6(
        src: InetAddress,
        dst: InetAddress,
        nextHeader: Int,
        payload: ByteArray,
        hopLimit: Int = 64,
        computeTransportChecksum: Boolean = true,
    ): ByteArray {
        val packet = ByteArray(IPV6_HEADER_LEN + payload.size)
        packet[0] = 0x60.toByte() // version 6, traffic class 0, flow label 0
        ByteOrder.putUInt16(packet, 4, payload.size)
        packet[6] = nextHeader.toByte()
        packet[7] = hopLimit.toByte()
        System.arraycopy(src.address, 0, packet, 8, 16)
        System.arraycopy(dst.address, 0, packet, 24, 16)
        System.arraycopy(payload, 0, packet, IPV6_HEADER_LEN, payload.size)

        if (computeTransportChecksum && nextHeader != IpConstants.PROTO_ICMPV6) {
            fillTransportChecksum(IpConstants.IPV6, src, dst, nextHeader, packet, IPV6_HEADER_LEN)
        }
        return packet
    }

    private fun fillTransportChecksum(
        version: Int,
        src: InetAddress,
        dst: InetAddress,
        protocol: Int,
        packet: ByteArray,
        transportOffset: Int,
    ) {
        val data = packet.copyOfRange(transportOffset, packet.size)
        val checksum = InternetChecksum.transportChecksum(version, src, dst, protocol, data)
        ByteOrder.putUInt16(packet, transportOffset + 6, checksum)
    }

    /**
     * Fragments a complete IP packet into MTU-sized pieces. Returns a single
     * packet unchanged if it already fits. Only the first fragment carries a
     * transport checksum; subsequent fragments carry transport payload only.
     */
    fun fragment(packet: ByteArray, mtu: Int, version: Int): List<ByteArray> {
        if (packet.size <= mtu) return listOf(packet)
        return if (version == IpConstants.IPV4) fragmentIpv4(packet, mtu) else fragmentIpv6(packet, mtu)
    }

    private fun fragmentIpv4(packet: ByteArray, mtu: Int): List<ByteArray> {
        val headerLen = ((packet[0].toInt() and 0x0F) shl 2)
        val id = ((packet[4].toInt() and 0xFF) shl 8) or (packet[5].toInt() and 0xFF)
        val ttl = packet[8].toInt() and 0xFF
        val protocol = packet[9].toInt() and 0xFF
        val src = packet.copyOfRange(12, 16)
        val dst = packet.copyOfRange(16, 20)
        val payload = packet.copyOfRange(headerLen, packet.size)

        val maxPayload = ((mtu - headerLen) ushr 3) shl 3
        val result = ArrayList<ByteArray>()
        var offset = 0
        while (offset < payload.size) {
            val len = minOf(maxPayload, payload.size - offset)
            val last = offset + len >= payload.size
            val frag = ByteArray(headerLen + len)
            System.arraycopy(packet, 0, frag, 0, headerLen)
            frag[6] = 0
            frag[7] = 0
            val fragField = (offset ushr 3) and 0x1FFF
            if (!last) {
                frag[6] = 0x20.toByte() // MF flag
            }
            frag[6] = (frag[6].toInt() or (fragField ushr 8)).toByte()
            frag[7] = (fragField and 0xFF).toByte()
            ByteOrder.putUInt16(frag, 2, frag.size)
            ByteOrder.putUInt16(frag, 4, id)
            frag[8] = ttl.toByte()
            frag[9] = protocol.toByte()
            ByteOrder.putUInt16(frag, 10, 0)
            System.arraycopy(payload, offset, frag, headerLen, len)
            ByteOrder.putUInt16(frag, 10, InternetChecksum.compute(frag, 0, headerLen))
            result.add(frag)
            offset += len
        }
        return result
    }

    private fun fragmentIpv6(packet: ByteArray, mtu: Int): List<ByteArray> {
        val src = packet.copyOfRange(8, 24)
        val dst = packet.copyOfRange(24, 40)
        val nextHeader = packet[6].toInt() and 0xFF
        val hopLimit = packet[7].toInt() and 0xFF
        val payload = packet.copyOfRange(IPV6_HEADER_LEN, packet.size)

        val maxPayload = ((mtu - IPV6_HEADER_LEN - IPV6_FRAG_HEADER_LEN) ushr 3) shl 3
        val identification = (System.nanoTime() and 0xFFFFFFFFL)
        val result = ArrayList<ByteArray>()
        var offset = 0
        while (offset < payload.size) {
            val len = minOf(maxPayload, payload.size - offset)
            val last = offset + len >= payload.size
            val frag = ByteArray(IPV6_HEADER_LEN + IPV6_FRAG_HEADER_LEN + len)
            frag[0] = 0x60.toByte()
            ByteOrder.putUInt16(frag, 4, IPV6_FRAG_HEADER_LEN + len)
            frag[6] = IpConstants.IPV6_FRAGMENT.toByte()
            frag[7] = hopLimit.toByte()
            System.arraycopy(src, 0, frag, 8, 16)
            System.arraycopy(dst, 0, frag, 24, 16)
            // Fragment header
            frag[40] = nextHeader.toByte()
            frag[41] = 0
            val fragField = ((offset ushr 3) and 0x1FFF) shl 3
            val fieldWithM = if (!last) fragField or 1 else fragField
            ByteOrder.putUInt16(frag, 42, fieldWithM)
            ByteOrder.putUInt32(frag, 44, identification)
            System.arraycopy(payload, offset, frag, IPV6_HEADER_LEN + IPV6_FRAG_HEADER_LEN, len)
            result.add(frag)
            offset += len
        }
        return result
    }
}

/** Inbound IP fragment reassembly (IPv4 and IPv6). */
class FragmentReassembler(
    private val maxDatagram: Int = 65535,
    private val timeoutMillis: Long = 10_000L,
) {
    private data class Key(
        val version: Int,
        val src: String,
        val dst: String,
        val id: Long,
        val protocol: Int,
    )

    private data class Fragment(
        val key: Key,
        val buffer: ByteArray,
        var received: BooleanArray,
        var receivedBytes: Int,
        var totalLength: Int,
        var lastSeen: Long,
        var lastFragmentSeen: Boolean,
        val src: InetAddress,
        val dst: InetAddress,
        val protocol: Int,
    )

    private val fragments = ConcurrentHashMap<Key, Fragment>()
    private val lock = Any()


    /**
     * Attempts to add an IP fragment. Returns the fully reassembled datagram
     * (complete IP packet) if this fragment completed a datagram, otherwise
     * returns null (the caller keeps waiting for more fragments).
     */
    fun addFragment(ip: IpHeader, packet: ByteArray, offset: Int, length: Int): ByteArray? {
        if (!ip.isFragment) return null
        val key = Key(ip.version, ip.src.hostAddress.orEmpty(), ip.dst.hostAddress.orEmpty(), ip.identification, ip.protocol)

        synchronized(lock) {
            purgeExpiredLocked()
            val fragLen = length - ip.payloadOffset
            if (fragLen <= 0) return null
            val endOffset = ip.fragmentOffset + fragLen
            if (endOffset > maxDatagram) {
                // Oversized datagram; drop and discard the whole flow.
                fragments.remove(key)
                return null
            }

            val frag = fragments.getOrPut(key) {
                val total = if (!ip.moreFragments) endOffset else 0
                Fragment(
                    key = key,
                    buffer = ByteArray(maxDatagram),
                    received = BooleanArray(maxDatagram),
                    receivedBytes = 0,
                    totalLength = total,
                    lastSeen = System.currentTimeMillis(),
                    lastFragmentSeen = !ip.moreFragments,
                    src = ip.src,
                    dst = ip.dst,
                    protocol = ip.protocol,
                )
            }

            if (!ip.moreFragments) {
                frag.totalLength = endOffset
                frag.lastFragmentSeen = true
            }

            // Copy fragment data. totalLength is only known once the last
            // fragment arrives, so positions are bounded by maxDatagram here.
            var copied = 0
            for (i in 0 until fragLen) {
                val pos = ip.fragmentOffset + i
                if (pos >= maxDatagram) break
                if (!frag.received[pos]) {
                    frag.received[pos] = true
                    frag.buffer[pos] = packet[ip.payloadOffset + i]
                    copied++
                }
            }
            frag.receivedBytes += copied
            frag.lastSeen = System.currentTimeMillis()

            if (!frag.lastFragmentSeen || frag.totalLength <= 0) return null
            if (frag.receivedBytes < frag.totalLength) return null

            // Complete — build the final IP packet.
            fragments.remove(key)
            return assemble(ip, frag)
        }
    }

    private fun assemble(ip: IpHeader, frag: Fragment): ByteArray {
        return if (ip.version == IpConstants.IPV4) {
            val headerLen = ip.headerLength
            val packet = ByteArray(headerLen + frag.totalLength)
            // Rebuild a minimal IPv4 header from the first fragment's header is
            // not possible because we only stored offsets; rebuild from scratch.
            packet[0] = (0x45).toByte()
            ByteOrder.putUInt16(packet, 2, packet.size)
            ByteOrder.putUInt16(packet, 4, ip.identification.toInt() and 0xFFFF)
            ByteOrder.putUInt16(packet, 6, 0)
            packet[8] = ip.hopLimit.toByte()
            packet[9] = ip.protocol.toByte()
            ByteOrder.putUInt16(packet, 10, 0)
            System.arraycopy(ip.src.address, 0, packet, 12, 4)
            System.arraycopy(ip.dst.address, 0, packet, 16, 4)
            System.arraycopy(frag.buffer, 0, packet, headerLen, frag.totalLength)
            ByteOrder.putUInt16(packet, 10, InternetChecksum.compute(packet, 0, headerLen))
            packet
        } else {
            val packet = ByteArray(IpBuilder.IPV6_HEADER_LEN + frag.totalLength)
            packet[0] = 0x60.toByte()
            ByteOrder.putUInt16(packet, 4, frag.totalLength)
            packet[6] = ip.protocol.toByte()
            packet[7] = ip.hopLimit.toByte()
            System.arraycopy(ip.src.address, 0, packet, 8, 16)
            System.arraycopy(ip.dst.address, 0, packet, 24, 16)
            System.arraycopy(frag.buffer, 0, packet, IpBuilder.IPV6_HEADER_LEN, frag.totalLength)
            packet
        }
    }

    private fun purgeExpiredLocked() {
        val now = System.currentTimeMillis()
        val it = fragments.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value.lastSeen > timeoutMillis) it.remove()
        }
    }

    fun clear() {
        synchronized(lock) { fragments.clear() }
    }
}
