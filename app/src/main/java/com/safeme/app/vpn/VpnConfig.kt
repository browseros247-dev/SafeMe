package com.safeme.app.vpn

import java.net.InetAddress

enum class DnsPreset(
    val label: String,
) {
    CLOUDFLARE_FAMILY("Cloudflare Family"),
    ADGUARD_FAMILY("AdGuard Family"),
    CUSTOM("Custom preset"),
    ;

    companion object {
        fun fromName(name: String?): DnsPreset? =
            entries.firstOrNull { it.name == name }
    }
}

object VpnValidation {

    /** Strict IPv4 validation: four decimal octets, each 0-255, no leading zeros, no trailing garbage. */
    fun isValidIpv4(ip: String): Boolean {
        if (ip.isBlank()) return false
        val parts = ip.trim().split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || part.length > 3) return@all false
            if (!part.all { it.isDigit() }) return@all false
            if (part.length > 1 && part.startsWith('0')) return@all false
            val value = part.toIntOrNull() ?: return@all false
            value in 0..255
        }
    }

    /** Validates an IPv6 literal using InetAddress (bracketed form stripped). */
    fun isValidIpv6(ip: String): Boolean {
        if (ip.isBlank()) return false
        return try {
            val candidate = ip.trim().removeSurrounding("[", "]")
            if (candidate.contains('.')) return false
            val addr = InetAddress.getByName(candidate)
            addr is java.net.Inet6Address
        } catch (_: Exception) {
            false
        }
    }
}
