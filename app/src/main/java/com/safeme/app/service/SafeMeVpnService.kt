package com.safeme.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.safeme.app.MainActivity
import com.safeme.app.R
import com.safeme.app.data.BlockingPrefsState
import com.safeme.app.data.BundledKeywords
import com.safeme.app.data.DnsVpnSettings
import com.safeme.app.data.NOTIF_CUSTOM
import com.safeme.app.data.NOTIF_HIDE
import com.safeme.app.data.blockingPrefs
import com.safeme.app.data.dnsVpnSettings
import com.safeme.app.data.incrementBlockedToday
import com.safeme.app.data.setVpnEnabled
import com.safeme.app.vpn.BlockingRules
import com.safeme.app.vpn.DnsPreset
import com.safeme.app.vpn.SocketProtector
import com.safeme.app.vpn.VpnEngine
import com.safeme.app.vpn.VpnStatusStore
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * SafeMe VPN filtering service.
 *
 * The tunnel is established with routes 0.0.0.0/0 and ::/0 and a userspace
 * engine ([VpnEngine]) that:
 *  - forwards ALL traffic by default (UDP relay, TCP relay, ICMP echo), so
 *    every application keeps normal internet connectivity,
 *  - filters DNS by domain/keyword rules (NXDOMAIN for blocked names),
 *  - respects excluded applications via [android.net.VpnService.Builder.addDisallowedApplication].
 *
 * Lifecycle: START_STICKY, serialized start/stop, automatic restart on network
 * changes, clean teardown on destroy, persisted enabled state restored at boot
 * by [VpnBootReceiver].
 */
class SafeMeVpnService : VpnService(), SocketProtector {

    companion object {
        const val ACTION_START = "com.safeme.app.action.START_VPN"
        const val ACTION_STOP = "com.safeme.app.action.STOP_VPN"
        const val ACTION_STOP_PERSIST = "com.safeme.app.action.STOP_VPN_PERSIST"
        const val ACTION_UPDATE_NOTIF = "com.safeme.app.action.UPDATE_NOTIF"
        const val CHANNEL_ID = "vpn_filtering"
        const val NOTIFICATION_ID = 1

        const val TUN_ADDR_V4 = "10.0.0.2"
        const val TUN_ADDR_V6 = "fd00:10:0:0:2::2"
        const val TUN_MTU = 1280

        const val NETWORK_RESTART_DEBOUNCE_MS = 1500L
        const val RESTART_COOLDOWN_MS = 5000L

        @Volatile
        var isActive = false
    }

    private val commandExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SafeMe-VpnCommand").apply { isDaemon = true }
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val restartDebounced = AtomicBoolean(false)

    private var engine: VpnEngine? = null
    private var networkCallbackRegistered = false
    private var rulesJob: kotlinx.coroutines.Job? = null

    /**
     * The default network the tunnel is currently running over. Tracked so a
     * restart is only triggered by a REAL handover (Wi-Fi ↔ cellular, loss of
     * connectivity), never by the initial onAvailable that fires right after
     * registering the callback, and never by same-network capability churn.
     */
    @Volatile
    private var currentDefaultNetwork: Network? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = currentDefaultNetwork
            currentDefaultNetwork = network
            // The first onAvailable after registration is the network we just
            // started the tunnel on — restarting then would churn every app's
            // connections ~1.5s after each enable (and race the teardown).
            if (previous != null && previous != network) {
                scheduleTunnelRestart()
            }
        }

        override fun onLost(network: Network) {
            if (currentDefaultNetwork == network) {
                currentDefaultNetwork = null
                scheduleTunnelRestart()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Internal stop (e.g. config-change restart): keep the persisted
                // enabled state untouched.
                dispatchCommand {
                    stopTunnel()
                    stopSelf()
                }
            }
            ACTION_STOP_PERSIST -> {
                // User-initiated stop from the notification: persist the off state
                // so the UI and boot receiver stay consistent.
                dispatchCommand {
                    stopTunnel()
                    runBlocking { setVpnEnabled(false) }
                    stopSelf()
                }
            }
            ACTION_UPDATE_NOTIF -> {
                // Notification-only change: refresh the foreground notification
                // without tearing down the tunnel.
                dispatchCommand {
                    if (isActive) {
                        runCatching {
                            startForegroundWithNotification(runBlocking { dnsVpnSettings().first() })
                        }
                    }
                }
            }
            else -> {
                dispatchCommand {
                    if (engine == null || !isActive) {
                        startTunnel()
                    }
                    // Already running: nothing to do. Config changes are applied
                    // through a STOP followed by a START from the UI.
                }
            }
        }
        return START_STICKY
    }

    /** Dispatches a command onto the serial executor without ever throwing on
     *  the caller (main) thread — e.g. if the executor was shut down during
     *  teardown, the command is simply dropped. */
    private fun dispatchCommand(command: () -> Unit) {
        if (commandExecutor.isShutdown) return
        runCatching { commandExecutor.execute(command) }
    }

    override fun onDestroy() {
        commandExecutor.execute {
            stopTunnel()
            commandExecutor.shutdown()
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Tunnel lifecycle
    // ------------------------------------------------------------------

    private fun startTunnel() {
        try {
            // A restart may have been scheduled while the tunnel was down; a
            // fresh start supersedes it.
            cancelPendingRestart()
            val vpnSettings = runBlocking { dnsVpnSettings().first() }
            if (!vpnSettings.enabled) {
                // START_STICKY restart after a system kill, but the user has
                // disabled the VPN: don't resurrect it.
                stopSelf()
                return
            }
            val blockingState = runBlocking { blockingPrefs().first() }
            val rulesRef = java.util.concurrent.atomic.AtomicReference(buildRules(blockingState))

            val newEngine = establishTunnel(vpnSettings, rulesRef)
            if (newEngine == null) {
                // Tunnel could not be established — revert persisted state so
                // the UI and the boot receiver stay consistent.
                isActive = false
                VpnStatusStore.setActive(false)
                runBlocking { setVpnEnabled(false) }
                stopSelf()
                return
            }

            engine = newEngine
            isActive = true
            VpnStatusStore.setActive(true)
            newEngine.start()
            startForegroundWithNotification(vpnSettings)
            registerNetworkCallback()
            // Apply blocking-rule changes (keywords, websites, master toggle)
            // to the running tunnel without a restart.
            startRulesCollector(rulesRef)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            isActive = false
            VpnStatusStore.setActive(false)
            engine?.stop()
            engine = null
            stopSelf()
        }
    }

    private fun buildRules(state: BlockingPrefsState): BlockingRules =
        BlockingRules.fromPrefs(state, BundledKeywords.keywords, BundledKeywords.websites)

    private fun startRulesCollector(ref: java.util.concurrent.atomic.AtomicReference<BlockingRules>) {
        rulesJob?.cancel()
        rulesJob = serviceScope.launch {
            blockingPrefs().collect { state ->
                if (isActive) ref.set(buildRules(state))
            }
        }
    }

    private fun establishTunnel(
        vpnSettings: DnsVpnSettings,
        rules: java.util.concurrent.atomic.AtomicReference<BlockingRules>,
    ): VpnEngine? {
        return try {
            val builder = Builder()
                .addAddress(TUN_ADDR_V4, 32)
                .addAddress(TUN_ADDR_V6, 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .setMtu(TUN_MTU)

            when (vpnSettings.preset) {
                DnsPreset.ADGUARD_FAMILY -> {
                    builder.addDnsServer("94.140.14.15")
                    builder.addDnsServer("2a10:50c0::ad1:ff")
                }
                DnsPreset.CLOUDFLARE_FAMILY -> {
                    builder.addDnsServer("1.1.1.1")
                    builder.addDnsServer("2606:4700:4700::1111")
                }
                DnsPreset.CUSTOM -> {
                    // Custom servers apply ONLY under the Custom preset. The
                    // built-in presets are mutually exclusive with custom values,
                    // so leftover custom addresses from a previous selection must
                    // not leak into e.g. Cloudflare/AdGuard (they would be used in
                    // addition to — or instead of — the chosen preset).
                    if (vpnSettings.customV4.isNotBlank()) {
                        builder.addDnsServer(vpnSettings.customV4)
                    }
                    if (vpnSettings.customV6.isNotBlank()) {
                        builder.addDnsServer(vpnSettings.customV6)
                    }
                }
            }

            // Our own package must never be routed back into the tunnel.
            runCatching { builder.addDisallowedApplication(packageName) }
            // Excluded applications bypass the VPN entirely (existing behavior).
            vpnSettings.whitelist.forEach { packageName ->
                runCatching { builder.addDisallowedApplication(packageName) }
            }

            val pfd = builder.establish() ?: return null

            val dnsServers = collectDnsServers(vpnSettings)
            val tunLocal = setOf(
                InetAddress.getByName(TUN_ADDR_V4),
                InetAddress.getByName(TUN_ADDR_V6),
            )

            VpnEngine(
                pfd = pfd,
                mtu = TUN_MTU,
                rules = rules,
                protector = this,
                onBlocked = { notifyBlocked() },
                onTunnelClosed = { scheduleTunnelRestart() },
                tunLocalAddrs = tunLocal,
                fallbackDns = dnsServers,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun collectDnsServers(vpnSettings: DnsVpnSettings): List<InetAddress> {
        val servers = ArrayList<InetAddress>()
        val add = { literal: String ->
            runCatching { InetAddress.getByName(literal) }.getOrNull()?.let { servers.add(it) }
        }
        when (vpnSettings.preset) {
            DnsPreset.ADGUARD_FAMILY -> {
                add("94.140.14.15")
                add("2a10:50c0::ad1:ff")
            }
            DnsPreset.CLOUDFLARE_FAMILY -> {
                add("1.1.1.1")
                add("2606:4700:4700::1111")
            }
            DnsPreset.CUSTOM -> {
                // Same exclusivity rule as the tunnel builder: custom addresses
                // apply only under the Custom preset.
                if (vpnSettings.customV4.isNotBlank()) add(vpnSettings.customV4)
                if (vpnSettings.customV6.isNotBlank()) add(vpnSettings.customV6)
            }
        }
        return servers
    }

    private fun stopTunnel() {
        cancelPendingRestart()
        rulesJob?.cancel()
        rulesJob = null
        engine?.stop()
        engine = null
        isActive = false
        VpnStatusStore.setActive(false)
        unregisterNetworkCallback()
    }

    /**
     * The system has revoked/disabled our VPN (user turned it off in the
     * notification shade or Settings, or another VPN took over). Persist the
     * off state so the UI and the boot receiver agree with reality, and tear
     * the tunnel down.
     */
    override fun onRevoke() {
        dispatchCommand {
            stopTunnel()
            runBlocking { setVpnEnabled(false) }
            stopSelf()
        }
        super.onRevoke()
    }

    // ------------------------------------------------------------------
    // Network-change recovery
    // ------------------------------------------------------------------

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(networkCallback, mainHandler)
            networkCallbackRegistered = true
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
        // Forget the tracked network so the next registration's initial
        // onAvailable is treated as the fresh baseline, not a handover.
        currentDefaultNetwork = null
    }

    /**
     * Debounced tunnel restart when connectivity changes (Wi-Fi ↔ cellular,
     * carrier handover, airplane mode toggle). Re-establishing the tunnel
     * re-reads DNS settings and gives every app a fresh, consistent path.
     *
     * The restart is cancellable: a subsequent startTunnel/stopTunnel removes
     * the pending callback, so a stale restart can never fire after the user
     * or UI already changed the tunnel state.
     */
    private var lastRestartAt = 0L
    private var pendingRestart: Runnable? = null

    private fun scheduleTunnelRestart() {
        if (!isActive) return
        if (!restartDebounced.compareAndSet(false, true)) return
        val task = Runnable {
            restartDebounced.set(false)
            pendingRestart = null
            val now = System.currentTimeMillis()
            if (now - lastRestartAt < RESTART_COOLDOWN_MS) return@Runnable
            dispatchCommand {
                if (isActive) {
                    stopTunnel()
                    startTunnel()
                    lastRestartAt = System.currentTimeMillis()
                }
            }
        }
        pendingRestart = task
        mainHandler.postDelayed(task, NETWORK_RESTART_DEBOUNCE_MS)
    }

    private fun cancelPendingRestart() {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        pendingRestart = null
        restartDebounced.set(false)
    }

    // ------------------------------------------------------------------
    // Socket protection (VpnService.protect) — prevents relay loops
    // ------------------------------------------------------------------

    override fun protect(socket: java.net.DatagramSocket): Boolean =
        runCatching { super.protect(socket) }.getOrDefault(false)

    override fun protect(socket: java.net.Socket): Boolean =
        runCatching { super.protect(socket) }.getOrDefault(false)

    private fun notifyBlocked() {
        serviceScope.launch {
            runCatching { incrementBlockedToday() }
        }
    }

    // ------------------------------------------------------------------
    // Foreground notification
    // ------------------------------------------------------------------

    private fun startForegroundWithNotification(vpnSettings: DnsVpnSettings) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val contentText = when (vpnSettings.notifMode) {
            NOTIF_CUSTOM -> if (vpnSettings.notifCustom.isNotBlank()) {
                vpnSettings.notifCustom
            } else {
                getString(R.string.vpn_notif_active)
            }
            NOTIF_HIDE -> getString(R.string.vpn_notif_hidden)
            else -> getString(R.string.vpn_notif_active)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, SafeMeVpnService::class.java).setAction(ACTION_STOP_PERSIST)
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notif_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.vpn_notif_stop), stopPending)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
