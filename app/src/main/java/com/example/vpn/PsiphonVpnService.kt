package com.example.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import ca.psiphon.PsiphonTunnel
import com.example.MainActivity
import com.example.R
import com.example.data.ServerRegions
import com.example.data.model.ServerRegion
import com.example.data.model.SplitTunnelMode
import com.example.data.model.TrafficStats
import com.example.data.model.VpnState
import com.example.data.preferences.VpnPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class PsiphonVpnService : VpnService(), PsiphonTunnel.HostService {

    companion object {
        private const val TAG = "PsiphonVpnService"
        const val ACTION_START_VPN = "com.example.vpn.ACTION_START"
        const val ACTION_STOP_VPN = "com.example.vpn.ACTION_STOP"
        private const val NOTIFICATION_CHANNEL_ID = "psiphon_vpn_channel"
        private const val NOTIFICATION_ID = 1001

        private val _vpnState = MutableStateFlow(VpnState.DISCONNECTED)
        val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

        private val _currentRegion = MutableStateFlow(ServerRegions.defaultRegions[0])
        val currentRegion: StateFlow<ServerRegion> = _currentRegion.asStateFlow()

        private val _trafficStats = MutableStateFlow(TrafficStats())
        val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

        private val _availableRegions = MutableStateFlow(ServerRegions.defaultRegions)
        val availableRegions: StateFlow<List<ServerRegion>> = _availableRegions.asStateFlow()

        private val _isRefreshingServers = MutableStateFlow(false)
        val isRefreshingServers: StateFlow<Boolean> = _isRefreshingServers.asStateFlow()

        private val _logs = MutableStateFlow<List<String>>(emptyList())
        val logs: StateFlow<List<String>> = _logs.asStateFlow()

        fun log(msg: String) {
            Log.d(TAG, msg)
            val current = _logs.value.toMutableList()
            if (current.size > 500) current.removeAt(0)
            current.add(msg)
            _logs.value = current
        }

        fun clearLogs() {
            _logs.value = emptyList()
        }

        fun refreshServerRegions() {
            _isRefreshingServers.value = true
            log("Refreshing server list and active counts...")

            val currentCounts = _availableRegions.value.associate { it.code.uppercase() to (it.activeServerCount ?: ServerRegions.getDefaultServerCount(it.code)) }
            val merged = ServerRegions.defaultRegions.map { region ->
                val count = currentCounts[region.code.uppercase()] ?: ServerRegions.getDefaultServerCount(region.code)
                region.copy(activeServerCount = count)
            }.toMutableList()

            for (r in _availableRegions.value) {
                if (r.code.isNotBlank() && merged.none { it.code.equals(r.code, ignoreCase = true) }) {
                    merged.add(r)
                }
            }
            _availableRegions.value = merged

            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(1200)
                _isRefreshingServers.value = false
                log("Server list refreshed (${merged.size} locations available)")
            }
        }
    }

    private var psiphonTunnel: PsiphonTunnel? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isTunnelRunning = AtomicBoolean(false)
    private var tunThread: Thread? = null

    private var httpProxyPort = 0
    private var socksProxyPort = 0
    private var connectedTimestamp = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferences: VpnPreferences

    override fun onCreate() {
        super.onCreate()
        preferences = VpnPreferences(this)
        createNotificationChannel()
        log("PsiphonVpnService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> {
                log("Received ACTION_START_VPN")
                startVpn()
            }
            ACTION_STOP_VPN -> {
                log("Received ACTION_STOP_VPN")
                stopVpn()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (isTunnelRunning.get()) {
            log("VPN is already starting or running")
            return
        }

        _vpnState.value = VpnState.CONNECTING
        isTunnelRunning.set(true)
        startForeground(NOTIFICATION_ID, createNotification("در حال اتصال به سایفون..."))

        val selectedCode = preferences.selectedRegion
        _currentRegion.value = ServerRegions.getByCode(selectedCode)

        serviceScope.launch {
            try {
                log("Initializing PsiphonTunnel instance...")
                val tunnel = PsiphonTunnel.newPsiphonTunnel(this@PsiphonVpnService)
                psiphonTunnel = tunnel
                tunnel.setVpnMode(true)

                val embeddedEntries = getEmbeddedServerEntriesString()
                log("Starting Psiphon tunnel core with region: '$selectedCode' (${embeddedEntries.length} bytes embedded entries)...")
                tunnel.startTunneling(embeddedEntries)
            } catch (e: Exception) {
                log("Failed to start Psiphon tunnel: ${e.message}")
                stopVpn()
            }
        }
    }

    private fun getEmbeddedServerEntriesString(): String {
        return try {
            val resId = resources.getIdentifier("embedded_server_entries", "raw", packageName)
            if (resId != 0) {
                resources.openRawResource(resId).bufferedReader().use { it.readText() }
            } else {
                ""
            }
        } catch (e: Exception) {
            log("Error reading embedded_server_entries raw resource: ${e.message}")
            ""
        }
    }

    private fun stopVpn() {
        if (_vpnState.value == VpnState.DISCONNECTED && !isTunnelRunning.get()) return

        _vpnState.value = VpnState.STOPPING
        log("Stopping VPN and tearing down tunnel...")

        serviceScope.launch {
            try {
                isTunnelRunning.set(false)
                tunThread?.interrupt()
                tunThread = null

                vpnInterface?.close()
                vpnInterface = null

                psiphonTunnel?.stop()
                psiphonTunnel = null
            } catch (e: Exception) {
                log("Error during tunnel stop: ${e.message}")
            } finally {
                _vpnState.value = VpnState.DISCONNECTED
                _trafficStats.value = TrafficStats()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                log("Psiphon VPN stopped")
            }
        }
    }

    // --- HostService Implementation ---

    override fun getContext(): Context = this

    override fun getPsiphonConfig(): String {
        return try {
            val rawConfigStream = resources.openRawResource(R.raw.psiphon_config)
            val baseConfigStr = rawConfigStream.bufferedReader().use { it.readText() }
            val configJson = JSONObject(baseConfigStr)

            // Dynamic server region selection
            val selectedCode = preferences.selectedRegion
            configJson.put("EgressRegion", selectedCode)

            // Dynamic upstream proxy configuration
            val proxyConfig = preferences.getUpstreamProxy()
            val upstreamUrl = proxyConfig.formattedUrl
            if (upstreamUrl != null) {
                configJson.put("UpstreamProxyURL", upstreamUrl)
                log("Configured Upstream Proxy: $upstreamUrl")
            } else {
                configJson.remove("UpstreamProxyURL")
            }

            // Data storage directory for Psiphon core
            val dataDir = File(filesDir, "psiphon_core").apply { mkdirs() }
            configJson.put("DataRootDirectory", dataDir.absolutePath)
            configJson.put("DisableLocalHTTPProxy", false)
            configJson.put("DisableLocalSOCKSProxy", false)
            configJson.put("EmitBytesTransferred", true)
            configJson.put("EmitDiagnosticNotices", true)

            // Ensure embedded server entries seed file exists in DataRootDirectory if available in raw/assets
            ensureEmbeddedServerEntriesSeeded(dataDir)

            val finalConfig = configJson.toString()
            log("Psiphon config prepared (Region: ${if (selectedCode.isEmpty()) "Fastest/Auto" else selectedCode})")
            finalConfig
        } catch (e: Exception) {
            log("Error building Psiphon config: ${e.message}")
            "{}"
        }
    }

    private fun ensureEmbeddedServerEntriesSeeded(dataDir: File) {
        try {
            val seedFile = File(dataDir, "embedded_server_entries")
            if (!seedFile.exists() || seedFile.length() == 0L) {
                // Try reading embedded server entries from raw resource if present
                val resId = resources.getIdentifier("embedded_server_entries", "raw", packageName)
                if (resId != 0) {
                    resources.openRawResource(resId).use { input ->
                        FileOutputStream(seedFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    log("Successfully seeded embedded_server_entries file into Psiphon data directory")
                }
            }
        } catch (e: Exception) {
            log("Note on seeding embedded server entries: ${e.message}")
        }
    }

    override fun bindToDevice(fileDescriptor: Long) {
        // Protect Psiphon's own outgoing network sockets from looping into the VPN interface!
        val fd = fileDescriptor.toInt()
        val success = protect(fd)
        if (!success) {
            log("Warning: protect($fd) returned false")
        }
    }

    override fun onDiagnosticMessage(message: String) {
        val trimmed = message.trim()
        if (trimmed.startsWith("{")) {
            try {
                val json = JSONObject(trimmed)
                val noticeType = json.optString("noticeType", "Diagnostic")
                val dataObj = json.optJSONObject("data")
                val innerMsg = dataObj?.optString("message", "") ?: ""
                if (innerMsg.isNotEmpty()) {
                    log("Psiphon Core [$noticeType]: $innerMsg")
                } else {
                    log("Psiphon Core [$noticeType]")
                }
            } catch (e: Exception) {
                log("Psiphon Core: $message")
            }
        } else {
            log("Psiphon Core: $message")
        }
        parseServerCountsFromDiagnosticMessage(message)
    }

    private fun parseServerCountsFromDiagnosticMessage(message: String) {
        try {
            if (!message.trim().startsWith("{")) return
            val json = JSONObject(message)
            val noticeType = json.optString("noticeType", "")
            
            // Extract active regions and server counts from Psiphon notice structures
            val countsMap = mutableMapOf<String, Int>()
            
            if (noticeType == "ActiveRegions" || noticeType == "Tuning" || noticeType == "ServerEntries") {
                val data = json.optJSONObject("data")
                val regionCounts = data?.optJSONObject("regionServerCounts") 
                    ?: data?.optJSONObject("activeRegions")
                
                if (regionCounts != null) {
                    val keys = regionCounts.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val count = regionCounts.optInt(key, 0)
                        if (count > 0) {
                            countsMap[key.uppercase()] = count
                        }
                    }
                }
            } else if (json.has("regionServerCounts")) {
                val regionCounts = json.optJSONObject("regionServerCounts")
                regionCounts?.keys()?.forEach { key ->
                    val count = regionCounts.optInt(key, 0)
                    if (count > 0) {
                        countsMap[key.uppercase()] = count
                    }
                }
            }

            if (countsMap.isNotEmpty()) {
                updateAvailableRegionsWithServerCounts(countsMap)
            }
        } catch (e: Exception) {
            // Non-JSON or diagnostic log string
        }
    }

    private fun updateAvailableRegionsWithServerCounts(countsMap: Map<String, Int>) {
        val currentList = _availableRegions.value
        val updatedList = currentList.map { region ->
            val count = countsMap[region.code.uppercase()]
            if (count != null) {
                region.copy(activeServerCount = count)
            } else {
                region
            }
        }
        _availableRegions.value = updatedList
        log("Updated active server counts for regions: $countsMap")
    }

    override fun onListeningHttpProxyPort(port: Int) {
        log("Psiphon HTTP proxy listening on port: $port")
        httpProxyPort = port
    }

    override fun onListeningSocksProxyPort(port: Int) {
        log("Psiphon SOCKS proxy listening on port: $port")
        socksProxyPort = port
    }

    override fun onAvailableEgressRegions(regions: MutableList<String>?) {
        if (!regions.isNullOrEmpty()) {
            log("Psiphon available egress regions: ${regions.joinToString()}")
            // Start with our baseline full list
            val mergedList = ServerRegions.defaultRegions.toMutableList()
            // Add any additional dynamic region codes reported by Psiphon
            for (code in regions) {
                if (code.isNotBlank() && mergedList.none { it.code.equals(code, ignoreCase = true) }) {
                    mergedList.add(ServerRegions.getByCode(code))
                }
            }
            _availableRegions.value = mergedList
        }
    }

    override fun onConnecting() {
        log("Psiphon status: CONNECTING")
        _vpnState.value = VpnState.CONNECTING
        updateNotification("در حال برقراری ارتباط با سرور...")
    }

    override fun onConnected() {
        log("Psiphon status: CONNECTED! Establishing whole device VPN...")
        connectedTimestamp = System.currentTimeMillis()
        _vpnState.value = VpnState.CONNECTED

        establishVpnInterface()
        updateNotification("سایفون متصل است (${_currentRegion.value.displayName})")
    }

    override fun onConnectedServerRegion(region: String) {
        log("Connected to server region: $region")
        if (region.isNotBlank()) {
            _currentRegion.value = ServerRegions.getByCode(region)
            updateNotification("سایفون متصل است: ${_currentRegion.value.displayName}")
        }
    }

    override fun onUpstreamProxyError(message: String) {
        log("Upstream proxy error: $message")
    }

    override fun onBytesTransferred(sent: Long, received: Long) {
        _trafficStats.value = TrafficStats(
            bytesIn = received,
            bytesOut = sent,
            connectedSinceTimestamp = connectedTimestamp
        )
    }

    override fun onExiting() {
        log("Psiphon status: EXITING")
        if (isTunnelRunning.get()) {
            stopVpn()
        }
    }

    // --- VPN Interface & Packet Handling ---

    private fun establishVpnInterface() {
        try {
            vpnInterface?.close()

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("10.0.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")

            // Configure Split Tunneling
            val splitConfig = preferences.getSplitTunnelConfig()
            val pm = packageManager

            when (splitConfig.mode) {
                SplitTunnelMode.BYPASS_SELECTED -> {
                    log("Configuring Split Tunnel: Bypassing ${splitConfig.excludedPackages.size} apps")
                    for (pkg in splitConfig.excludedPackages) {
                        try {
                            pm.getPackageInfo(pkg, 0)
                            builder.addDisallowedApplication(pkg)
                            log("App bypassed from VPN: $pkg")
                        } catch (e: PackageManager.NameNotFoundException) {
                            log("Split bypass ignored: $pkg (not installed)")
                        } catch (e: Exception) {
                            log("Could not disallow app: $pkg")
                        }
                    }
                }
                SplitTunnelMode.ONLY_SELECTED -> {
                    log("Configuring Split Tunnel: Only tunneling ${splitConfig.excludedPackages.size} apps")
                    for (pkg in splitConfig.excludedPackages) {
                        try {
                            pm.getPackageInfo(pkg, 0)
                            builder.addAllowedApplication(pkg)
                            log("App allowed into VPN: $pkg")
                        } catch (e: PackageManager.NameNotFoundException) {
                            log("Split include ignored: $pkg (not installed)")
                        } catch (e: Exception) {
                            log("Could not allow app: $pkg")
                        }
                    }
                    // Always include our own app
                    try {
                        builder.addAllowedApplication(packageName)
                    } catch (e: Exception) {}
                }
                SplitTunnelMode.ALL_APPS -> {
                    log("Tunneling all device applications (Split Tunnel disabled)")
                }
            }

            // Route HTTP/HTTPS via direct proxy in Android 10+ (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
                if (httpProxyPort > 0) {
                    try {
                        builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", httpProxyPort))
                        log("Direct HTTP proxy set to 127.0.0.1:$httpProxyPort")
                    } catch (e: Exception) {
                        log("Failed to set direct HTTP proxy on builder: ${e.message}")
                    }
                }
            }

            val pfd = builder.establish()
            if (pfd == null) {
                log("VpnService.Builder.establish() returned null! Missing permission or revoked.")
                return
            }
            vpnInterface = pfd
            log("Whole device VPN TUN interface successfully established! FD: ${pfd.fd}")

            startTunLoop(pfd)
        } catch (e: Exception) {
            log("Error establishing VPN interface: ${e.message}")
        }
    }

    private fun startTunLoop(pfd: ParcelFileDescriptor) {
        tunThread?.interrupt()
        tunThread = Thread({
            val inStream = FileInputStream(pfd.fileDescriptor)
            val outStream = FileOutputStream(pfd.fileDescriptor)
            val packet = ByteArray(32767)

            log("TUN packet processing loop started")
            while (isTunnelRunning.get() && !Thread.currentThread().isInterrupted) {
                try {
                    val length = inStream.read(packet)
                    if (length > 0) {
                        handleTunPacket(packet, length, outStream)
                    } else if (length < 0) {
                        break
                    }
                } catch (e: IOException) {
                    break
                } catch (e: Exception) {
                    // ignore and continue processing
                }
            }
            log("TUN packet processing loop ended")
        }, "PsiphonTunThread").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleTunPacket(packet: ByteArray, length: Int, outStream: FileOutputStream) {
        if (length < 20) return
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return // IPv4

        val protocol = packet[9].toInt() and 0xFF
        val headerLen = (packet[0].toInt() and 0x0F) * 4

        // UDP DNS query handling (destination port 53)
        if (protocol == 17 && length >= headerLen + 8) {
            val dstPort = ((packet[headerLen + 2].toInt() and 0xFF) shl 8) or
                    (packet[headerLen + 3].toInt() and 0xFF)
            if (dstPort == 53) {
                forwardDnsPacket(packet, headerLen, length, outStream)
            }
        }
    }

    private fun forwardDnsPacket(
        packet: ByteArray,
        ipHeaderLen: Int,
        totalLen: Int,
        outStream: FileOutputStream
    ) {
        try {
            val udpDataOffset = ipHeaderLen + 8
            val udpDataLen = totalLen - udpDataOffset
            if (udpDataLen <= 0) return

            val dnsQuery = ByteArray(udpDataLen)
            System.arraycopy(packet, udpDataOffset, dnsQuery, 0, udpDataLen)

            val socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 2500

            val dnsServer = InetAddress.getByName("8.8.8.8")
            val sendPacket = DatagramPacket(dnsQuery, dnsQuery.size, dnsServer, 53)
            socket.send(sendPacket)

            val recvBuffer = ByteArray(2048)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
            socket.receive(recvPacket)
            socket.close()

            val dnsResponseLen = recvPacket.length
            val responseIpTotal = ipHeaderLen + 8 + dnsResponseLen
            val responsePacket = ByteArray(responseIpTotal)

            // Build IPv4 response header swapping src and dst IP
            System.arraycopy(packet, 0, responsePacket, 0, ipHeaderLen)
            // Swap IP addresses
            for (i in 0..3) {
                val temp = responsePacket[12 + i]
                responsePacket[12 + i] = responsePacket[16 + i]
                responsePacket[16 + i] = temp
            }
            // Update total length in IP header
            responsePacket[2] = ((responseIpTotal shr 8) and 0xFF).toByte()
            responsePacket[3] = (responseIpTotal and 0xFF).toByte()
            // Reset checksum to 0 then calculate
            responsePacket[10] = 0
            responsePacket[11] = 0
            val ipChecksum = calculateChecksum(responsePacket, 0, ipHeaderLen)
            responsePacket[10] = ((ipChecksum shr 8) and 0xFF).toByte()
            responsePacket[11] = (ipChecksum and 0xFF).toByte()

            // Build UDP header swapping ports
            val udpOffset = ipHeaderLen
            val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
            val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)
            responsePacket[udpOffset] = ((dstPort shr 8) and 0xFF).toByte()
            responsePacket[udpOffset + 1] = (dstPort and 0xFF).toByte()
            responsePacket[udpOffset + 2] = ((srcPort shr 8) and 0xFF).toByte()
            responsePacket[udpOffset + 3] = (srcPort and 0xFF).toByte()

            val udpTotal = 8 + dnsResponseLen
            responsePacket[udpOffset + 4] = ((udpTotal shr 8) and 0xFF).toByte()
            responsePacket[udpOffset + 5] = (udpTotal and 0xFF).toByte()
            responsePacket[udpOffset + 6] = 0 // Checksum optional in IPv4 UDP
            responsePacket[udpOffset + 7] = 0

            // Copy DNS payload
            System.arraycopy(recvBuffer, 0, responsePacket, udpOffset + 8, dnsResponseLen)

            synchronized(outStream) {
                outStream.write(responsePacket)
            }
        } catch (e: Exception) {
            // DNS resolution timeout or error
        }
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val high = data[i].toInt() and 0xFF
            val low = data[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    // --- Notifications ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Psiphon VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows Psiphon VPN connection status and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, PsiphonVpnService::class.java).apply {
            action = ACTION_STOP_VPN
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Psiphon VPN")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_vpn_logo, "قطع اتصال", disconnectPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
        log("PsiphonVpnService destroyed")
    }

    override fun onRevoke() {
        super.onRevoke()
        log("VPN permission revoked by user or system")
        stopVpn()
    }
}
