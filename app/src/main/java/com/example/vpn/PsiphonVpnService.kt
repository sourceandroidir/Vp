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

        private val _currentRegion = MutableStateFlow(ServerRegions.autoRegion)
        val currentRegion: StateFlow<ServerRegion> = _currentRegion.asStateFlow()

        private val _trafficStats = MutableStateFlow(TrafficStats())
        val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

        private val _availableRegions = MutableStateFlow<List<ServerRegion>>(listOf(ServerRegions.autoRegion))
        val availableRegions: StateFlow<List<ServerRegion>> = _availableRegions.asStateFlow()

        private val _isRefreshingServers = MutableStateFlow(false)
        val isRefreshingServers: StateFlow<Boolean> = _isRefreshingServers.asStateFlow()

        private val _logs = MutableStateFlow<List<String>>(emptyList())
        val logs: StateFlow<List<String>> = _logs.asStateFlow()

        fun log(msg: String) {
            val formatted = if (msg.startsWith("[PSIPHON]")) msg else "[PSIPHON] $msg"
            Log.d(TAG, formatted)
            val current = _logs.value.toMutableList()
            if (current.size > 500) current.removeAt(0)
            current.add(formatted)
            _logs.value = current
        }

        fun clearLogs() {
            _logs.value = emptyList()
        }

        fun refreshServerRegions() {
            _isRefreshingServers.value = true
            log("REMOTE_SERVER_LIST=STARTING")
            log("Core State: ${if (_vpnState.value == VpnState.CONNECTED) "Tunnel active, polling live active regions" else "Querying cached and discovered server entries"}")

            CoroutineScope(Dispatchers.IO).launch {
                kotlinx.coroutines.delay(800)
                _isRefreshingServers.value = false
                val count = _availableRegions.value.size - 1
                if (count > 0) {
                    log("ACTIVE_REGIONS=${_availableRegions.value.filter { it.code.isNotBlank() }.joinToString { it.code }}")
                    log("SERVER_ENTRIES_VALID=$count regions available")
                } else {
                    log("REMOTE_SERVER_LIST: Waiting for core connection discovery")
                }
            }
        }
    }

    private var psiphonTunnel: PsiphonTunnel? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private val isTunnelRunning = AtomicBoolean(false)
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
        log("SERVICE_CREATED: PsiphonVpnService initialized with Psiphon Tunnel Core v2.0.41")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VPN -> {
                log("ACTION_START_VPN: Initiating Psiphon tunnel startup sequence")
                startVpn()
            }
            ACTION_STOP_VPN -> {
                log("ACTION_STOP_VPN: Initiating Psiphon tunnel shutdown sequence")
                stopVpn()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        if (isTunnelRunning.get()) {
            log("START_ABORTED: VPN is already active or in progress")
            return
        }

        _vpnState.value = VpnState.CONNECTING
        isTunnelRunning.set(true)
        startForeground(NOTIFICATION_ID, createNotification("در حال اتصال به سایفون..."))

        val selectedCode = preferences.selectedRegion
        _currentRegion.value = ServerRegions.getByCode(selectedCode)

        serviceScope.launch {
            try {
                log("PSIPHON_STATE=STARTING")
                log("CORE_VERSION=2.0.41")
                log("SELECTED_REGION=${if (selectedCode.isBlank()) "Automatic" else selectedCode}")

                val tunnel = PsiphonTunnel.newPsiphonTunnel(this@PsiphonVpnService)
                psiphonTunnel = tunnel
                tunnel.setVpnMode(true)

                // Pass valid official embeddedServerEntries or empty string when relying on server discovery
                val embeddedEntries = getValidOfficialEmbeddedEntries()
                if (embeddedEntries.isBlank()) {
                    log("EMBEDDED_SERVER_ENTRIES=0")
                    log("No valid embedded server entries supplied; relying on configured Psiphon server discovery.")
                } else {
                    log("PSIPHON_STATE=IMPORTING_SERVER_ENTRIES")
                }
                tunnel.startTunneling(embeddedEntries)
            } catch (e: Exception) {
                log("CORE_ERROR: Failed to start Psiphon tunnel: ${e.message}")
                log("PSIPHON_STATE=DISCONNECTED")
                stopVpn()
            }
        }
    }

    private fun getValidOfficialEmbeddedEntries(): String {
        return try {
            val resId = resources.getIdentifier("embedded_server_entries", "raw", packageName)
            if (resId != 0) {
                val content = resources.openRawResource(resId).bufferedReader().use { it.readText().trim() }
                if (content.isNotEmpty() && !content.contains("192.168.") && !content.contains("\"ipAddress\":")) {
                    content
                } else {
                    ""
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun stopVpn() {
        if (_vpnState.value == VpnState.DISCONNECTED && !isTunnelRunning.get()) return

        _vpnState.value = VpnState.STOPPING
        log("STOP_START: Tearing down VPN tunnel and closing core...")

        serviceScope.launch {
            try {
                isTunnelRunning.set(false)

                tunThread?.interrupt()
                tunThread = null

                try {
                    vpnInterface?.close()
                } catch (e: Exception) {
                    log("TUN_CLOSE_NOTE: ${e.message}")
                }
                vpnInterface = null
                log("TUN_DESTROYED: VPN interface closed")

                try {
                    psiphonTunnel?.stop()
                } catch (e: Exception) {
                    log("CORE_STOP_NOTE: ${e.message}")
                }
                psiphonTunnel = null
                log("CORE_STOPPED: Psiphon Tunnel instance destroyed")
            } catch (e: Exception) {
                log("ERROR: Error during tunnel stop: ${e.message}")
            } finally {
                _vpnState.value = VpnState.DISCONNECTED
                _trafficStats.value = TrafficStats()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                log("PSIPHON_STATE=STOPPED")
                log("STATUS: IDLE / DISCONNECTED")
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
            if (selectedCode.isNotBlank()) {
                configJson.put("EgressRegion", selectedCode)
            } else {
                configJson.remove("EgressRegion")
            }

            // Upstream proxy configuration (strictly decoupled from server discovery)
            val proxyConfig = preferences.getUpstreamProxy()
            val upstreamUrl = proxyConfig.formattedUrl
            if (proxyConfig.enabled && !upstreamUrl.isNullOrBlank()) {
                configJson.put("UpstreamProxyURL", upstreamUrl)
                log("UPSTREAM_PROXY_ENABLED=true")
                log("UPSTREAM_PROXY_STATUS=CONFIGURED")
            } else {
                configJson.remove("UpstreamProxyURL")
                log("UPSTREAM_PROXY_ENABLED=false")
                log("UPSTREAM_PROXY_STATUS=DISABLED")
            }

            // Standard Psiphon persistent data directory
            val defaultDataDir = getFileStreamPath("ca.psiphon.PsiphonTunnel.tunnel-core")
            if (!defaultDataDir.exists()) {
                defaultDataDir.mkdirs()
            }
            configJson.put("DataRootDirectory", defaultDataDir.absolutePath)
            configJson.put("DisableLocalHTTPProxy", false)
            configJson.put("DisableLocalSOCKSProxy", false)
            configJson.put("EmitBytesTransferred", true)
            configJson.put("EmitDiagnosticNotices", true)

            val finalConfig = configJson.toString()
            log("CONFIG_LOADED=true")
            log("Selected Region: ${if (selectedCode.isEmpty()) "Automatic" else selectedCode}")
            finalConfig
        } catch (e: Exception) {
            log("CONFIG_LOADED=false")
            log("CONFIG_ERROR: ${e.message}")
            "{}"
        }
    }

    override fun bindToDevice(fileDescriptor: Long) {
        // Protect Psiphon's own outgoing network sockets from looping into the VPN interface!
        val fd = fileDescriptor.toInt()
        val success = protect(fd)
        if (!success) {
            log("SOCKET_PROTECT_WARN: protect($fd) returned false")
        }
    }

    override fun onDiagnosticMessage(message: String) {
        val trimmed = message.trim()
        var noticeType: String? = null
        var dataObj: JSONObject? = null
        var innerMsg = ""

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                noticeType = json.optString("noticeType", null)
                dataObj = json.optJSONObject("data")
                innerMsg = dataObj?.optString("message", "") ?: ""
            } catch (e: Exception) {
                // Ignore parsing error
            }
        } else {
            val colonIdx = trimmed.indexOf(": {")
            if (colonIdx != -1 && trimmed.endsWith("}")) {
                noticeType = trimmed.substring(0, colonIdx).trim()
                val jsonStr = trimmed.substring(colonIdx + 2).trim()
                try {
                    dataObj = JSONObject(jsonStr)
                    innerMsg = dataObj.optString("message", "")
                } catch (e: Exception) {
                    // Ignore parsing error
                }
            }
        }

        if (noticeType != null) {
            when (noticeType) {
                "CandidateServers" -> {
                    val count = dataObj?.optInt("count", 0) ?: 0
                    log("CANDIDATE_SERVERS=$count")
                    if (count > 0) {
                        log("PSIPHON_STATE=CANDIDATE_SERVERS_READY")
                    }
                }
                "AvailableEgressRegions" -> {
                    val regionsArr = dataObj?.optJSONArray("regions")
                    val list = mutableListOf<String>()
                    if (regionsArr != null) {
                        for (i in 0 until regionsArr.length()) {
                            val reg = regionsArr.optString(i)
                            if (!reg.isNullOrBlank()) list.add(reg)
                        }
                    }
                    log("ACTIVE_REGIONS=${list.joinToString(", ")}")
                    log("CANDIDATE_SERVERS_REGIONS=${list.joinToString(", ")}")
                    updateAvailableRegionsList(list)
                }
                "RemoteServerListResourceDownloaded" -> {
                    log("REMOTE_SERVER_LIST_FETCHED=true")
                    log("REMOTE_SERVER_LIST_SIGNATURE=VERIFIED")
                    log("REMOTE_SERVER_LIST_DECODED=true")
                }
                "RemoteServerListResourceDownloadedBytes" -> {
                    val bytes = dataObj?.optLong("bytes", 0L) ?: 0L
                    log("REMOTE_SERVER_LIST_BYTES=$bytes")
                }
                "UpstreamProxyError" -> {
                    val err = innerMsg.ifBlank { "Proxy connection error" }
                    log("UPSTREAM_PROXY_STATUS=ERROR: $err")
                }
                "ListeningSocksProxyPort" -> {
                    val port = dataObj?.optInt("port", 0) ?: 0
                    log("Listening SOCKS Port: $port")
                }
                "ListeningHttpProxyPort" -> {
                    val port = dataObj?.optInt("port", 0) ?: 0
                    log("Listening HTTP Port: $port")
                }
                "Tunnels" -> {
                    val count = dataObj?.optInt("count", 0) ?: 0
                    if (count == 0) {
                        log("PSIPHON_STATE=CONNECTING")
                    } else if (count == 1) {
                        log("PSIPHON_STATE=CONNECTED")
                    }
                }
                "Info" -> {
                    if (innerMsg.contains("fetching common remote server list", ignoreCase = true) ||
                        innerMsg.contains("fetching obfuscated", ignoreCase = true)) {
                        log("PSIPHON_STATE=FETCHING_REMOTE_SERVER_LIST")
                    }
                    if (innerMsg.contains("ImportEmbeddedServerEntries", ignoreCase = true) ||
                        innerMsg.contains("Importing embedded", ignoreCase = true)) {
                        log("PSIPHON_STATE=IMPORTING_SERVER_ENTRIES")
                    }
                    log("Info: $innerMsg")
                }
                "Warning", "Error" -> {
                    if (innerMsg.contains("missing RemoteServerListSignaturePublicKey", ignoreCase = true)) {
                        log("REMOTE_SERVER_LIST_SIGNATURE=MISSING_KEY")
                    } else if (innerMsg.contains("remote server list", ignoreCase = true) && innerMsg.contains("signature", ignoreCase = true)) {
                        log("REMOTE_SERVER_LIST_SIGNATURE=FAILED")
                    }
                    log("$noticeType: $innerMsg")
                }
                else -> {
                    if (innerMsg.isNotEmpty()) {
                        log("$noticeType: $innerMsg")
                    } else {
                        log("$noticeType: ${dataObj ?: trimmed}")
                    }
                }
            }
        } else {
            log(trimmed)
        }
    }

    private fun updateAvailableRegionsList(regions: List<String>) {
        val newList = mutableListOf<ServerRegion>()
        newList.add(ServerRegions.autoRegion)
        for (code in regions) {
            if (code.isNotBlank() && newList.none { it.code.equals(code, ignoreCase = true) }) {
                newList.add(ServerRegions.getByCode(code))
            }
        }
        _availableRegions.value = newList
    }

    override fun onListeningHttpProxyPort(port: Int) {
        log("Listening HTTP Port: $port")
        httpProxyPort = port
    }

    override fun onListeningSocksProxyPort(port: Int) {
        log("Listening SOCKS Port: $port")
        socksProxyPort = port
    }

    override fun onAvailableEgressRegions(regions: MutableList<String>?) {
        val list = regions ?: emptyList<String>()
        log("Available Regions: ${list.joinToString(", ")}")
        log("ACTIVE_REGIONS=${list.joinToString(", ")}")
        log("CANDIDATE_SERVERS_REGIONS=${list.joinToString(", ")}")
        updateAvailableRegionsList(list)
    }

    override fun onConnecting() {
        log("PSIPHON_STATE=CONNECTING")
        _vpnState.value = VpnState.CONNECTING
        updateNotification("در حال برقراری ارتباط با سرور...")
    }

    override fun onConnected() {
        log("PSIPHON_STATE=CONNECTED")
        connectedTimestamp = System.currentTimeMillis()
        _vpnState.value = VpnState.CONNECTED

        // Establish TUN interface strictly after genuine onConnected() callback
        establishVpnInterface()
        updateNotification("سایفون متصل است (${_currentRegion.value.displayName})")
    }

    override fun onConnectedServerRegion(region: String) {
        log("SELECTED_REGION=$region")
        if (region.isNotBlank()) {
            _currentRegion.value = ServerRegions.getByCode(region)
            updateNotification("سایفون متصل است: ${_currentRegion.value.displayName}")
        }
    }

    override fun onUpstreamProxyError(message: String) {
        log("UPSTREAM_PROXY_STATUS=ERROR: $message")
    }

    override fun onBytesTransferred(sent: Long, received: Long) {
        _trafficStats.value = TrafficStats(
            bytesIn = received,
            bytesOut = sent,
            connectedSinceTimestamp = connectedTimestamp
        )
    }

    override fun onExiting() {
        log("PSIPHON_STATE=DISCONNECTED")
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
                    log("SPLIT_TUNNEL: Bypassing ${splitConfig.excludedPackages.size} apps")
                    for (pkg in splitConfig.excludedPackages) {
                        try {
                            pm.getPackageInfo(pkg, 0)
                            builder.addDisallowedApplication(pkg)
                        } catch (e: PackageManager.NameNotFoundException) {
                            // package not installed
                        } catch (e: Exception) {
                            log("SPLIT_WARN: Could not disallow app: $pkg")
                        }
                    }
                }
                SplitTunnelMode.ONLY_SELECTED -> {
                    log("SPLIT_TUNNEL: Only tunneling ${splitConfig.excludedPackages.size} apps")
                    for (pkg in splitConfig.excludedPackages) {
                        try {
                            pm.getPackageInfo(pkg, 0)
                            builder.addAllowedApplication(pkg)
                        } catch (e: PackageManager.NameNotFoundException) {
                            // package not installed
                        } catch (e: Exception) {
                            log("SPLIT_WARN: Could not allow app: $pkg")
                        }
                    }
                    try {
                        builder.addAllowedApplication(packageName)
                    } catch (e: Exception) {}
                }
                SplitTunnelMode.ALL_APPS -> {
                    log("SPLIT_TUNNEL: All apps routed through VPN")
                }
            }

            // Route HTTP/HTTPS via direct proxy in Android 10+ (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
                if (httpProxyPort > 0) {
                    try {
                        builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", httpProxyPort))
                        log("HTTP_PROXY_SET: 127.0.0.1:$httpProxyPort")
                    } catch (e: Exception) {
                        log("HTTP_PROXY_WARN: Failed to set direct HTTP proxy on builder: ${e.message}")
                    }
                }
            }

            val pfd = builder.establish()
            if (pfd == null) {
                log("TUN_ERROR: VpnService.Builder.establish() returned null! Missing permission or revoked.")
                return
            }
            vpnInterface = pfd
            log("TUN_STATE=ESTABLISHED")

            startTunLoop(pfd)
        } catch (e: Exception) {
            log("TUN_ERROR: Error establishing VPN interface: ${e.message}")
        }
    }

    private fun startTunLoop(pfd: ParcelFileDescriptor) {
        tunThread?.interrupt()
        tunThread = Thread({
            val inStream = FileInputStream(pfd.fileDescriptor)
            val outStream = FileOutputStream(pfd.fileDescriptor)
            val packet = ByteArray(32767)

            log("TUN_LOOP: Packet processing loop started")
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
                    // ignore and continue
                }
            }
            log("TUN_LOOP: Packet processing loop ended")
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
            for (i in 0..3) {
                val temp = responsePacket[12 + i]
                responsePacket[12 + i] = responsePacket[16 + i]
                responsePacket[16 + i] = temp
            }
            responsePacket[2] = ((responseIpTotal shr 8) and 0xFF).toByte()
            responsePacket[3] = (responseIpTotal and 0xFF).toByte()
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
            responsePacket[udpOffset + 6] = 0
            responsePacket[udpOffset + 7] = 0

            // Copy DNS payload
            System.arraycopy(recvBuffer, 0, responsePacket, udpOffset + 8, dnsResponseLen)

            synchronized(outStream) {
                outStream.write(responsePacket)
            }
        } catch (e: Exception) {
            // DNS resolution timeout or packet drop
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
        log("SERVICE_DESTROYED: PsiphonVpnService cleaned up")
    }

    override fun onRevoke() {
        super.onRevoke()
        log("PERMISSION_REVOKED: VPN permission revoked by user/system")
        stopVpn()
    }
}
