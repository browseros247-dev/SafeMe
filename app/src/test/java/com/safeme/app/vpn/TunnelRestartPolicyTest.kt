package com.safeme.app.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelRestartPolicyTest {

    @Test
    fun dataDeliveredKeepsWaiting() {
        assertEquals(TunnelRestartPolicy.Outcome.KEEP_WAITING, TunnelRestartPolicy.onRead(1, null))
        assertEquals(TunnelRestartPolicy.Outcome.KEEP_WAITING, TunnelRestartPolicy.onRead(2048, null))
    }

    @Test
    fun eofAndNegativeReadsAreTunnelDeath() {
        assertEquals(TunnelRestartPolicy.Outcome.TUNNEL_DEAD, TunnelRestartPolicy.onRead(0, null))
        assertEquals(TunnelRestartPolicy.Outcome.TUNNEL_DEAD, TunnelRestartPolicy.onRead(-1, null))
        // EBADF (9) / EIO (5): real errnos from a closed/broken fd.
        assertEquals(TunnelRestartPolicy.Outcome.TUNNEL_DEAD, TunnelRestartPolicy.onRead(-1, 9))
        assertEquals(TunnelRestartPolicy.Outcome.TUNNEL_DEAD, TunnelRestartPolicy.onRead(-1, 5))
    }

    @Test
    fun eagainAndEintrKeepWaiting() {
        assertEquals(
            TunnelRestartPolicy.Outcome.KEEP_WAITING,
            TunnelRestartPolicy.onRead(-1, TunnelRestartPolicy.ERRNO_EAGAIN),
        )
        assertEquals(
            TunnelRestartPolicy.Outcome.KEEP_WAITING,
            TunnelRestartPolicy.onRead(-1, TunnelRestartPolicy.ERRNO_EINTR),
        )
    }

    @Test
    fun cooldownGatesWatchdogRestarts() {
        val cooldown = 5000L
        // Tunnel died just after establish → stop instead of restarting.
        assertTrue(TunnelRestartPolicy.shouldStopInsteadOfRestart(1_000L, 1_000L + cooldown - 1, cooldown))
        // Survived exactly the cooldown window → restart allowed.
        assertFalse(TunnelRestartPolicy.shouldStopInsteadOfRestart(1_000L, 1_000L + cooldown, cooldown))
        // Long-lived tunnel → restart allowed.
        assertFalse(TunnelRestartPolicy.shouldStopInsteadOfRestart(1_000L, 1_000L + cooldown + 1, cooldown))
    }
}
