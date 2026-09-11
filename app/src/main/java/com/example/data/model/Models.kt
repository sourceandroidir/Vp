package com.example.data.model

import android.graphics.drawable.Drawable

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STOPPING;

    val titleFa: String
        get() = when (this) {
            DISCONNECTED -> "قطع شده"
            CONNECTING -> "در حال اتصال..."
            CONNECTED -> "متصل شد"
            STOPPING -> "در حال قطع اتصال..."
        }

    val titleEn: String
        get() = when (this) {
            DISCONNECTED -> "Disconnected"
            CONNECTING -> "Connecting..."
            CONNECTED -> "Connected"
            STOPPING -> "Disconnecting..."
        }
}

data class ServerRegion(
    val code: String, // "" for fastest/best
    val nameEn: String,
    val nameFa: String,
    val flag: String,
    val pingMs: Int? = null,
    val activeServerCount: Int? = null,
    val isAvailable: Boolean = true
) {
    val displayName: String
        get() {
            val countStr = if (activeServerCount != null && activeServerCount > 0) " ($activeServerCount سرور)" else ""
            return if (code.isEmpty()) "$flag $nameFa ($nameEn)$countStr" else "$flag $nameFa - $code$countStr"
        }
}

enum class SplitTunnelMode {
    ALL_APPS,          // Tunnel all apps (no split)
    BYPASS_SELECTED,   // Exclude selected apps from VPN (user requirement: "بعضی از برنامه ها رو فیلتر نکنه و بدون پروکسی باز کنه")
    ONLY_SELECTED      // Only tunnel selected apps through VPN
}

data class SplitTunnelConfig(
    val mode: SplitTunnelMode = SplitTunnelMode.BYPASS_SELECTED,
    val excludedPackages: Set<String> = emptySet()
)

data class UpstreamProxyConfig(
    val enabled: Boolean = false,
    val protocol: String = "HTTP", // "HTTP" or "SOCKS5"
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = ""
) {
    val formattedUrl: String?
        get() {
            if (!enabled || host.isBlank() || port <= 0) return null
            val proto = protocol.lowercase()
            val auth = if (username.isNotBlank()) {
                if (password.isNotBlank()) "$username:$password@" else "$username@"
            } else ""
            return "$proto://$auth$host:$port"
        }
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null
)

data class TrafficStats(
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val connectedSinceTimestamp: Long = 0L
) {
    fun formattedBytesIn(): String = formatBytes(bytesIn)
    fun formattedBytesOut(): String = formatBytes(bytesOut)

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
