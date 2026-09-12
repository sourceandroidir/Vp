package com.example.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.data.ServerRegions
import com.example.data.model.AppInfo
import com.example.data.model.ServerRegion
import com.example.data.model.SplitTunnelConfig
import com.example.data.model.TrafficStats
import com.example.data.model.UpstreamProxyConfig
import com.example.data.model.VpnState
import com.example.data.preferences.VpnPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class VpnRepository(private val context: Context) {
    private val preferences = VpnPreferences(context)

    val vpnState: StateFlow<VpnState> = PsiphonVpnService.vpnState
    val currentRegion: StateFlow<ServerRegion> = PsiphonVpnService.currentRegion
    val trafficStats: StateFlow<TrafficStats> = PsiphonVpnService.trafficStats
    val availableRegions: StateFlow<List<ServerRegion>> = PsiphonVpnService.availableRegions
    val isRefreshingServers: StateFlow<Boolean> = PsiphonVpnService.isRefreshingServers
    val logs: StateFlow<List<String>> = PsiphonVpnService.logs

    fun clearLogs() {
        PsiphonVpnService.clearLogs()
    }

    fun refreshServers() {
        PsiphonVpnService.refreshServerRegions(context)
    }

    fun getSelectedRegion(): ServerRegion {
        return ServerRegions.getByCode(preferences.selectedRegion)
    }

    fun setSelectedRegion(region: ServerRegion) {
        preferences.selectedRegion = region.code
    }

    fun getUpstreamProxy(): UpstreamProxyConfig {
        return preferences.getUpstreamProxy()
    }

    fun saveUpstreamProxy(config: UpstreamProxyConfig) {
        preferences.saveUpstreamProxy(config)
    }

    fun getSplitTunnelConfig(): SplitTunnelConfig {
        return preferences.getSplitTunnelConfig()
    }

    fun saveSplitTunnelConfig(config: SplitTunnelConfig) {
        preferences.saveSplitTunnelConfig(config)
    }

    fun startVpn() {
        val intent = Intent(context, PsiphonVpnService::class.java).apply {
            action = PsiphonVpnService.ACTION_START_VPN
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopVpn() {
        val intent = Intent(context, PsiphonVpnService::class.java).apply {
            action = PsiphonVpnService.ACTION_STOP_VPN
        }
        context.startService(intent)
    }

    suspend fun getInstalledApplications(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<AppInfo>()

        for (app in installed) {
            // Exclude our own package from split selection
            if (app.packageName == context.packageName) continue

            val appName = pm.getApplicationLabel(app).toString()
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val icon = try {
                pm.getApplicationIcon(app)
            } catch (e: Exception) {
                null
            }

            result.add(
                AppInfo(
                    packageName = app.packageName,
                    appName = appName,
                    isSystemApp = isSystem,
                    icon = icon
                )
            )
        }

        // Sort user apps first, then alphabetical by app name
        result.sortedWith(
            compareBy<AppInfo> { it.isSystemApp }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
        )
    }
}
