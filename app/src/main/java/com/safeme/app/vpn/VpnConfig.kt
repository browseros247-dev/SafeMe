package com.safeme.app.vpn

import java.net.InetAddress

enum class DnsPreset(
    val label: String,
    val sub: String,
    val v4: String,
    val v6: String,
) {
    CLOUDFLARE_FAMILY("Cloudflare Family", "Balanced · malware + adult blocked", "1.1.1.1", "2606:4700:4700::1111"),
    ADGUARD_FAMILY("AdGuard Family", "Strict · + ads & trackers", "94.140.14.15", "2a10:50c0::ad1:ff"),
    CUSTOM("Custom preset", "IPv4 / IPv6 validated", "", ""),
    ;

    companion object {
        fun fromName(name: String?): DnsPreset? =
            entries.firstOrNull { it.name == name }

        fun fromLabel(label: String): DnsPreset? =
            entries.firstOrNull { it.label == label }
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
