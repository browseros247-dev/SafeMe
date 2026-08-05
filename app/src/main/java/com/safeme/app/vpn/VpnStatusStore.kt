package com.safeme.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide source of truth for whether the SafeMe VPN tunnel is currently
 * established. Updated by [com.safeme.app.service.SafeMeVpnService] on tunnel
 * start/stop and on system revocation ([android.net.VpnService.onRevoke]), so
 * the UI reflects the REAL network state in real time — including when the
 * user disables the VPN from the system notification shade or Settings,
 * which the persisted preference alone cannot detect.
 */
object VpnStatusStore {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun setActive(active: Boolean) {
        _active.value = active
    }
}
