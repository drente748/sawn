package com.sawn.app.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sawn.app.MainActivity
import com.sawn.app.R
import com.sawn.app.SawnApp
import com.sawn.app.block.DomainMatcher
import com.sawn.app.block.SafeSearchHosts
import com.sawn.app.system.KeepAliveWorker
import com.sawn.app.sawnContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Local, no-root VPN that captures DNS traffic and blackholes queries for blocked
 * domains. Non-blocked queries are forwarded to an upstream resolver and NAT'd back.
 * Only DNS is routed into the tunnel, so all other traffic is untouched.
 */
class SawnVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            // A dead background coroutine must not take the tunnel down with it.
            Log.w(TAG, "Background VPN task failed: ${error.javaClass.simpleName}")
        },
    )
    private var loopJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val refreshMutex = Mutex()
    private val forwardPermits = Semaphore(MAX_CONCURRENT_DNS)

    @Volatile private var blockedDomains: Set<String> = emptySet()

    /** When true, search/video queries are rewritten to safe-mode anchors. */
    @Volatile private var safeSearchOn: Boolean = false

    /** Safe-host hostname → resolved IPv4 (as Int), cached for the tunnel's lifetime. */
    private val safeHostIps = HashMap<String, Int>()

    /** Resolve [host] once and cache; returns null on failure (caller falls back). */
    private fun safeHostIp(host: String): Int? = synchronized(safeHostIps) {
        safeHostIps[host]
    } ?: run {
        val ip = resolveA(host)
        if (ip != null) synchronized(safeHostIps) { safeHostIps[host] = ip }
        ip
    }

    /** Plain system-resolver lookup of an A record for [host]. */
    private fun resolveA(host: String): Int? = runCatching {
        val addr = InetAddress.getByName(host)
        if (addr.address.size == 4) {
            ((addr.address[0].toInt() and 0xFF) shl 24) or
                ((addr.address[1].toInt() and 0xFF) shl 16) or
                ((addr.address[2].toInt() and 0xFF) shl 8) or
                (addr.address[3].toInt() and 0xFF)
        } else null
    }.getOrNull()

    /** The device's real DNS resolvers, captured before the tunnel takes over the active
     *  network. We forward non-blocked queries here so Sawn never redirects your DNS to a
     *  third party of its own choosing. */
    @Volatile private var upstreamDns: List<InetAddress> = emptyList()
    private val fallbackDns: InetAddress by lazy { InetAddress.getByName(FALLBACK_DNS) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTunnel(); stopSelf(); return START_NOT_STICKY }
            else -> runCatching { startTunnel() }.onFailure {
                Log.e(TAG, "VPN start failed: ${it.javaClass.simpleName}")
                stopTunnel()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startTunnel() {
        if (tunnel != null) { scope.launch { safeRefreshBlocklist() }; return }
        // Android 14+ يتطلب تحديد نوع الخدمة الأمامية صراحةً مع الاستدعاء —
        // ServiceCompat يعالج الإصدارات تحت ذلك تلقائياً.
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        // Capture the real resolvers BEFORE establish() — afterwards the active network is us.
        upstreamDns = systemDnsServers()

        val builder = Builder()
            .setSession("Sawn")
            .addAddress(VIRTUAL_ADDRESS, 24)
            .addDnsServer(VIRTUAL_DNS)
            // Route BOTH families' resolver space into the tunnel: even though we only
            // terminate v4 DNS inside, claiming the v6 route stops the OS from sending
            // DNS-over-IPv6 around our filter (a complete interception bypass otherwise).
            .addRoute(VIRTUAL_DNS, 32)
            .addRoute(IPV6_DNS, 128)
            .setBlocking(true)
        runCatching { builder.addDisallowedApplication(packageName) } // never filter ourselves

        val established = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "VPN establish() failed: ${e.javaClass.simpleName}")
            null
        } ?: run {
            Log.e(TAG, "VPN establish() returned null (consent not granted?)")
            _running.value = false
            stopSelf()
            return
        }

        tunnel = established
        _running.value = true
        // A running filter is a protected day: the streak must not wait for the user to
        // reopen the app (that froze the day counter at 0 for schedule/focus users).
        scope.launch {
            runCatching {
                if (sawnContainer.blockEngine.isBlockingActiveNow()) {
                    sawnContainer.settings.recordActiveDay()
                }
            }
        }
        registerUpstreamWatcher()
        loopJob = scope.launch {
            safeRefreshBlocklist()
            runPacketLoop(established)
        }
        watchBlocklistChanges()
    }

    /**
     * Track the underlying (non-VPN) network so a Wi-Fi ↔ mobile switch does not leave the
     * forwarder sending every allowed query to a resolver that is no longer reachable.
     */
    private fun registerUpstreamWatcher() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshNetworkDns(cm, network)
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                applyUpstreamDns(linkProperties.dnsServers)
        }
        runCatching { cm.registerNetworkCallback(request, callback) }
            .onSuccess { networkCallback = callback }
            .onFailure { Log.w(TAG, "DNS watcher unavailable: ${it.javaClass.simpleName}") }
    }

    private fun refreshNetworkDns(cm: ConnectivityManager, network: Network) {
        runCatching { cm.getLinkProperties(network) }
            .getOrNull()
            ?.let { applyUpstreamDns(it.dnsServers) }
    }

    private fun applyUpstreamDns(servers: List<InetAddress>) {
        val usable = servers.filterNot { it.isLoopbackAddress }
        if (usable.isEmpty() || usable == upstreamDns) return
        upstreamDns = usable
        synchronized(safeHostIps) { safeHostIps.clear() }
    }

    private fun unregisterUpstreamWatcher() {
        val callback = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    suspend fun refreshBlocklistLocked() {
        val settings = sawnContainer.settings
        val snapshot = settings.protectionSnapshot()
        val blockingActive = sawnContainer.blockEngine.isBlockingActiveNow(peekOnly = true)
        safeSearchOn = blockingActive && snapshot.safeSearchForce && snapshot.pornGuard
        val domains = sawnContainer.blockEngine.blockedDomains(readOnly = true).toSet()
        // The adult extra set (fetched sources) rides outside BlockEngine's gate so a
        // source refresh while blocking is already on reaches the filter immediately.
        val extras = if (blockingActive && snapshot.pornGuard) settings.adultExtraDomains.first() else emptySet()
        blockedDomains = domains + extras
    }

    private suspend fun refreshBlocklist() {
        refreshMutex.withLock { refreshBlocklistLocked() }
    }

    /**
     * A refresh must never kill the packet loop or the watcher. If the DB/DataStore is
     * temporarily unavailable, keep the last known set and try again on the next tick.
     */
    private suspend fun safeRefreshBlocklist() {
        try {
            refreshBlocklist()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Blocklist refresh failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Keep the tunnel's blocked set current: any change to the blocklist, schedules, master
     * switch, or DoH toggle must reach the running filter at once — no "add a site while
     * blocking is on and it stays reachable until you toggle" hole.
     */
    private fun watchBlocklistChanges() {
        val container = sawnContainer
        val repo = container.repository
        val settings = container.settings
        // combine() overloads cap at 5 flows; pair the last two to stay within it.
        val changes = combine(
            repo.items, repo.schedules,
            settings.masterEnabled, settings.alwaysOn, settings.blockDoh,
        ) { _, _, _, _, _ -> Unit }
            .combine(settings.pornGuard) { _, _ -> Unit }
            .combine(settings.adultExtraDomains) { _, _ -> Unit }
            .combine(settings.blockAds) { _, _ -> Unit }
            .combine(settings.safeSearchForce) { _, _ -> Unit }

        scope.launch {
            while (isActive) {
                try {
                    combine(changes, settings.focusEndsAt) { _, _ -> Unit }
                        .collect { safeRefreshBlocklist() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A dead collector would silently stop filtering; restart it.
                    Log.w(TAG, "Blocklist watcher failed; restarting", e)
                    delay(WATCH_RETRY_MS)
                }
            }
        }
        // Schedules activate by wall-clock time, not by a data change; tick so a window
        // boundary reaches the running filter even when nothing else emits.
        scope.launch {
            while (isActive) {
                delay(BLOCKLIST_TICK_MS)
                safeRefreshBlocklist()
            }
        }
    }

    private fun runPacketLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val packet = ByteArray(MTU)

        try {
            while (scope.isActive) {
                val read = try { input.read(packet) } catch (e: Exception) { break }
                if (read <= 0) continue
                try {
                    handleDnsPacket(packet, read, output)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One malformed/edge-case query must never stop the whole filter.
                    Log.w(TAG, "Dropped malformed packet: ${e.javaClass.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Packet loop terminated unexpectedly", e)
        } finally {
            stopTunnel()
            stopSelf()
        }
    }

    private fun handleDnsPacket(packet: ByteArray, read: Int, output: FileOutputStream) {
        val parsed = try { DnsPacket.parse(packet, read) } catch (e: Exception) { null } ?: return
        if (parsed.dstPort != DnsPacket.DNS_PORT) return

        val name = parsed.qname ?: return
        val safeOverride = if (safeSearchOn) SafeSearchHosts.safeHostFor(name) else null
        if (safeOverride != null) {
            // Rewrite: answer with the safe host's A record (resolved+cached). If we
            // don't have it yet, resolve once on this thread then serve from cache.
            val ip = safeHostIp(safeOverride)
            if (ip != null) {
                val reply = DnsPacket.buildSinkholeIpResponse(packet, read, parsed, ip)
                runCatching { writeResponse(output, reply) }
                return
            }
            // resolve failed → fall through to normal forward (never brick a host)
        }
        if (isBlocked(name)) {
            // Never log the domain — that would leak browsing history into logcat.
            // AAAA gets an empty (NOERROR, 0-answer) reply: the domain has no v6
            // address, so clients stop probing IPv6 instead of leaking past us.
            val reply = if (parsed.queryType == DnsPacket.TYPE_AAAA)
                DnsPacket.buildNullResponse(packet, read, parsed)
            else
                DnsPacket.buildSinkholeResponse(packet, read, parsed)
            runCatching { writeResponse(output, reply) }
        } else {
            // Bound in-flight queries; overload drops a query so the client retries
            // instead of growing an unlimited coroutine/socket backlog.
            val swap = packet.copyOf(read)
            if (forwardPermits.tryAcquire()) {
                scope.launch {
                    try {
                        forward(swap, parsed, output)
                    } finally {
                        forwardPermits.release()
                    }
                }
            }
        }
    }

    private fun writeResponse(output: FileOutputStream, response: ByteArray) {
        synchronized(output) { output.write(response) }
    }

    /** A domain is blocked if it equals or is a subdomain of any blocked entry. */
    private fun isBlocked(host: String): Boolean = DomainMatcher.matches(host, blockedDomains)

    /**
     * Forward one DNS query to the device's resolver and write the reply back into the tun.
     * Uses a fresh **connected** socket per query so only the intended resolver's replies are
     * accepted, and verifies the DNS transaction ID — together these block off-path response
     * spoofing / cache-poisoning on a hostile network.
     */
    private fun forward(packet: ByteArray, parsed: DnsPacket.Parsed, output: FileOutputStream) {
        val query = packet.copyOfRange(parsed.dnsPayloadOffset, parsed.dnsPayloadOffset + parsed.dnsPayloadLen)
        val target = upstreamDns.firstOrNull() ?: fallbackDns
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket)
            socket.connect(target, DnsPacket.DNS_PORT) // only accept replies from this resolver
            socket.soTimeout = 3000
            socket.send(DatagramPacket(query, query.size))

            val buf = ByteArray(MTU)
            val replyPacket = DatagramPacket(buf, buf.size)
            socket.receive(replyPacket)
            val replyLen = replyPacket.length
            // Transaction-ID must match the query, else it's a spoofed / stray reply — drop it.
            if (replyLen < 2 || buf[0] != query[0] || buf[1] != query[1]) return
            writeResponse(output, wrapReply(packet, parsed, buf.copyOf(replyLen)))
        } catch (e: Exception) {
            // Don't log the domain (privacy); the exception class is enough to diagnose.
            Log.w(TAG, "DNS forward failed: ${e.javaClass.simpleName}")
        } finally {
            runCatching { socket?.close() }
        }
    }

    /** The device's DNS servers on the underlying network (empty if unavailable). */
    private fun systemDnsServers(): List<InetAddress> = runCatching {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val net = cm.activeNetwork ?: return emptyList()
        cm.getLinkProperties(net)?.dnsServers?.filterNot { it.isLoopbackAddress } ?: emptyList()
    }.getOrDefault(emptyList())

    /** Wrap a raw upstream DNS reply in IP/UDP headers pointed back at the querying app. */
    private fun wrapReply(request: ByteArray, parsed: DnsPacket.Parsed, dnsReply: ByteArray): ByteArray {
        val totalLen = parsed.ipHeaderLen + 8 + dnsReply.size
        val out = ByteBuffer.allocate(totalLen)
        out.put(request, 0, parsed.ipHeaderLen)
        out.putShort(2, totalLen.toShort())
        for (k in 0 until 4) out.put(12 + k, parsed.dstIp[k])
        for (k in 0 until 4) out.put(16 + k, parsed.srcIp[k])
        out.putShort(10, 0)
        // reuse DnsPacket's IP checksum by round-tripping through a sinkhole-style fix
        val arr = out.array()
        ipChecksum(arr, parsed.ipHeaderLen)
        val u = parsed.ipHeaderLen
        out.putShort(u, parsed.dstPort.toShort())
        out.putShort(u + 2, parsed.srcPort.toShort())
        out.putShort(u + 4, (8 + dnsReply.size).toShort())
        out.putShort(u + 6, 0)
        out.position(parsed.ipHeaderLen + 8)
        out.put(dnsReply)
        return arr
    }

    private fun ipChecksum(buf: ByteArray, headerLen: Int) {
        var sum = 0L
        var i = 0
        while (i < headerLen) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val c = sum.inv().toInt() and 0xFFFF
        buf[10] = (c ushr 8).toByte()
        buf[11] = (c and 0xFF).toByte()
    }

    private fun stopTunnel() {
        _running.value = false
        loopJob?.cancel()
        loopJob = null
        unregisterUpstreamWatcher()
        runCatching { tunnel?.close() }
        tunnel = null
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, SawnApp.VPN_CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onRevoke() {
        stopTunnel()
        stopSelf()
        super.onRevoke()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // مسح التطبيق من قائمة المهام لا يوقف الحماية: هذه خدمة نظام مستقلة عن
        // المهمة (`stopWithTask=false`). نعيد نشر الإشعار المستمر دائماً — حتى لو
        // لم يكن الترنل قائماً بعد — ثم نوقظ الوصي بعد لحظات: إن قتلت الشركةُ
        // العمليةَ كاملةً يُعاد تشغيل الترنل، وإن كان حياً يكتفي الوصي بتسجيل اليوم.
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                },
            )
        }
        // لا نشترط أن يكون الترنل حياً: قد يكون النظام قتل العملية للتو. العامل يفحص
        // الحالة بنفسه ولا يفعل شيئاً إن كان المستخدم قد أوقف الحماية من داخل التطبيق.
        KeepAliveWorker.schedule(this, delaySeconds = 8L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // الاستدعاء الحقيقي الوحيد للتدمير: إما ACTION_STOP الصريح أو النظام
        // (uninstall/disable). لمسح التطبيق من الخلفية لا نصل هنا.
        stopTunnel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Whether the filtering tunnel is currently up — read by the Home health indicator so
         *  users are never *silently* unblocked when the OS kills the service. */
        private val _running = MutableStateFlow(false)
        val runningFlow: StateFlow<Boolean> = _running.asStateFlow()
        val running: Boolean get() = _running.value

        private const val TAG = "SawnVpn"
        const val ACTION_START = "com.sawn.app.VPN_START"
        const val ACTION_STOP = "com.sawn.app.VPN_STOP"
        private const val BLOCKLIST_TICK_MS = 30_000L
        private const val WATCH_RETRY_MS = 5_000L
        private const val MAX_CONCURRENT_DNS = 32
        private const val VIRTUAL_ADDRESS = "10.111.222.1"
        private const val VIRTUAL_DNS = "10.111.222.2"
        /** Synthetic v6 route so the OS never sends DNS past us over IPv6 (bypass hole). */
        private const val IPV6_DNS = "fd00:2024:1111:2222::1"
        // Used only if the OS exposes no resolver (rare); prefer the device's own DNS.
        private const val FALLBACK_DNS = "1.1.1.1"
        private const val MTU = 32767
        private const val NOTIF_ID = 1001
    }
}
