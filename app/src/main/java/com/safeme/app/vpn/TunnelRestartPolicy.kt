package com.safeme.app.vpn

/**
 * Pure decision logic for the DNS-filter tunnel watchdog in
 * [com.safeme.app.service.SafeMeVpnService], extracted into an Android-free
 * object so the restart/cooldown behavior can be unit-tested.
 */
object TunnelRestartPolicy {

    enum class Outcome { KEEP_WAITING, TUNNEL_DEAD }

    // Linux/Android errno values, stable across all Android versions. Defined
    // here instead of referencing android.system.OsConstants so the policy is
    // fully unit-testable on the JVM (the android test stubs zero out all
    // constants, which would make every errno compare equal).
    const val ERRNO_EAGAIN = 11
    const val ERRNO_EINTR = 4

    /**
     * Classifies one result from the blocking read on the tunnel fd.
     *
     * - `bytesRead > 0` — the tunnel delivered data (never happens in the
     *   DNS-only architecture, but a healthy read) → keep waiting.
     * - errno EAGAIN / EINTR — a non-blocking fd reported no data, or the read
     *   was interrupted (typically the stopTunnel() interrupt wake) → transient,
     *   keep waiting.
     * - anything else (`bytesRead <= 0`, or a real errno such as EBADF) — the
     *   tunnel died → treat as death.
     */
    fun onRead(bytesRead: Long, errno: Int?): Outcome = when {
        bytesRead > 0 -> Outcome.KEEP_WAITING
        errno == ERRNO_EAGAIN || errno == ERRNO_EINTR -> Outcome.KEEP_WAITING
        else -> Outcome.TUNNEL_DEAD
    }

    /**
     * Anti-storm gate for watchdog-triggered restarts: if the tunnel died
     * within [cooldownMs] of the last successful establish, restarting would
     * likely die again, so the service should stop instead. A tunnel that
     * survived at least [cooldownMs] is a legitimate restart candidate.
     */
    fun shouldStopInsteadOfRestart(lastRestartAt: Long, now: Long, cooldownMs: Long): Boolean =
        now - lastRestartAt < cooldownMs
}
