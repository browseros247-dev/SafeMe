package com.safeme.app.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnValidationTest {

    @Test
    fun validIpv4Addresses() {
        assertTrue(VpnValidation.isValidIpv4("1.1.1.1"))
        assertTrue(VpnValidation.isValidIpv4("94.140.14.15"))
        assertTrue(VpnValidation.isValidIpv4("0.0.0.0"))
        assertTrue(VpnValidation.isValidIpv4("255.255.255.255"))
        assertTrue(VpnValidation.isValidIpv4("8.8.8.8"))
        assertTrue(VpnValidation.isValidIpv4("10.0.0.2"))
    }

    @Test
    fun invalidIpv4Addresses() {
        assertFalse(VpnValidation.isValidIpv4(""))
        assertFalse(VpnValidation.isValidIpv4("   "))
        assertFalse(VpnValidation.isValidIpv4("1.1.1"))
        assertFalse(VpnValidation.isValidIpv4("1.1.1.1.1"))
        assertFalse(VpnValidation.isValidIpv4("256.1.1.1"))
        assertFalse(VpnValidation.isValidIpv4("1.300.1.1"))
        assertFalse(VpnValidation.isValidIpv4("01.1.1.1"))
        assertFalse(VpnValidation.isValidIpv4("1.1.1.1a"))
        assertFalse(VpnValidation.isValidIpv4("a.b.c.d"))
        assertFalse(VpnValidation.isValidIpv4("1,1,1,1"))
        assertFalse(VpnValidation.isValidIpv4("999.999.999.999"))
        assertFalse(VpnValidation.isValidIpv4("-1.2.3.4"))
    }

    @Test
    fun validIpv6Addresses() {
        assertTrue(VpnValidation.isValidIpv6("2606:4700:4700::1111"))
        assertTrue(VpnValidation.isValidIpv6("2a10:50c0::ad1:ff"))
        assertTrue(VpnValidation.isValidIpv6("fd00:10:0:0:2::2"))
        assertTrue(VpnValidation.isValidIpv6("2001:db8::1"))
        assertTrue(VpnValidation.isValidIpv6("::1"))
        assertTrue(VpnValidation.isValidIpv6("::"))
    }

    @Test
    fun invalidIpv6Addresses() {
        assertFalse(VpnValidation.isValidIpv6(""))
        assertFalse(VpnValidation.isValidIpv6("   "))
        assertFalse(VpnValidation.isValidIpv6("2606:4700:4700:1111"))
        assertFalse(VpnValidation.isValidIpv6("1.1.1.1"))
        assertFalse(VpnValidation.isValidIpv6("2001:db8:gggg::1"))
        assertFalse(VpnValidation.isValidIpv6("hello"))
    }
}
