package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.SplitTunnelConfig
import com.example.data.model.SplitTunnelMode
import com.example.data.model.UpstreamProxyConfig

class VpnPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("psiphon_vpn_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SELECTED_REGION = "selected_region"
        private const val KEY_PROXY_ENABLED = "proxy_enabled"
        private const val KEY_PROXY_PROTOCOL = "proxy_protocol"
        private const val KEY_PROXY_HOST = "proxy_host"
        private const val KEY_PROXY_PORT = "proxy_port"
        private const val KEY_PROXY_USERNAME = "proxy_username"
        private const val KEY_PROXY_PASSWORD = "proxy_password"

        private const val KEY_SPLIT_MODE = "split_mode"
        private const val KEY_SPLIT_PACKAGES = "split_packages"
    }

    var selectedRegion: String
        get() = prefs.getString(KEY_SELECTED_REGION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_REGION, value).apply()

    fun getUpstreamProxy(): UpstreamProxyConfig {
        return UpstreamProxyConfig(
            enabled = prefs.getBoolean(KEY_PROXY_ENABLED, false),
            protocol = prefs.getString(KEY_PROXY_PROTOCOL, "HTTP") ?: "HTTP",
            host = prefs.getString(KEY_PROXY_HOST, "") ?: "",
            port = prefs.getInt(KEY_PROXY_PORT, 8080),
            username = prefs.getString(KEY_PROXY_USERNAME, "") ?: "",
            password = prefs.getString(KEY_PROXY_PASSWORD, "") ?: ""
        )
    }

    fun saveUpstreamProxy(config: UpstreamProxyConfig) {
        prefs.edit()
            .putBoolean(KEY_PROXY_ENABLED, config.enabled)
            .putString(KEY_PROXY_PROTOCOL, config.protocol)
            .putString(KEY_PROXY_HOST, config.host)
            .putInt(KEY_PROXY_PORT, config.port)
            .putString(KEY_PROXY_USERNAME, config.username)
            .putString(KEY_PROXY_PASSWORD, config.password)
            .apply()
    }

    fun getSplitTunnelConfig(): SplitTunnelConfig {
        val modeStr = prefs.getString(KEY_SPLIT_MODE, SplitTunnelMode.BYPASS_SELECTED.name)
        val mode = try {
            SplitTunnelMode.valueOf(modeStr ?: SplitTunnelMode.BYPASS_SELECTED.name)
        } catch (e: Exception) {
            SplitTunnelMode.BYPASS_SELECTED
        }
        val packages = prefs.getStringSet(KEY_SPLIT_PACKAGES, emptySet()) ?: emptySet()
        return SplitTunnelConfig(mode = mode, excludedPackages = packages)
    }

    fun saveSplitTunnelConfig(config: SplitTunnelConfig) {
        prefs.edit()
            .putString(KEY_SPLIT_MODE, config.mode.name)
            .putStringSet(KEY_SPLIT_PACKAGES, config.excludedPackages)
            .apply()
    }
}
