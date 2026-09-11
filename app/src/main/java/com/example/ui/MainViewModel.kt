package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppInfo
import com.example.data.model.ServerRegion
import com.example.data.model.SplitTunnelConfig
import com.example.data.model.SplitTunnelMode
import com.example.data.model.TrafficStats
import com.example.data.model.UpstreamProxyConfig
import com.example.data.model.VpnState
import com.example.vpn.VpnRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VpnRepository(application)

    val vpnState: StateFlow<VpnState> = repository.vpnState
    val currentRegion: StateFlow<ServerRegion> = repository.currentRegion
    val trafficStats: StateFlow<TrafficStats> = repository.trafficStats
    val availableRegions: StateFlow<List<ServerRegion>> = repository.availableRegions
    val isRefreshingServers: StateFlow<Boolean> = repository.isRefreshingServers
    val logs: StateFlow<List<String>> = repository.logs

    private val _selectedRegion = MutableStateFlow(repository.getSelectedRegion())
    val selectedRegion: StateFlow<ServerRegion> = _selectedRegion.asStateFlow()

    private val _upstreamProxy = MutableStateFlow(repository.getUpstreamProxy())
    val upstreamProxy: StateFlow<UpstreamProxyConfig> = _upstreamProxy.asStateFlow()

    private val _splitTunnelConfig = MutableStateFlow(repository.getSplitTunnelConfig())
    val splitTunnelConfig: StateFlow<SplitTunnelConfig> = _splitTunnelConfig.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = combine(_installedApps, _appSearchQuery) { apps, query ->
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadInstalledApps()
    }

    fun selectRegion(region: ServerRegion) {
        _selectedRegion.value = region
        repository.setSelectedRegion(region)
    }

    fun saveUpstreamProxy(config: UpstreamProxyConfig) {
        _upstreamProxy.value = config
        repository.saveUpstreamProxy(config)
    }

    fun setSplitTunnelMode(mode: SplitTunnelMode) {
        val updated = _splitTunnelConfig.value.copy(mode = mode)
        _splitTunnelConfig.value = updated
        repository.saveSplitTunnelConfig(updated)
    }

    fun toggleAppSplit(packageName: String) {
        val currentPackages = _splitTunnelConfig.value.excludedPackages.toMutableSet()
        if (currentPackages.contains(packageName)) {
            currentPackages.remove(packageName)
        } else {
            currentPackages.add(packageName)
        }
        val updated = _splitTunnelConfig.value.copy(excludedPackages = currentPackages)
        _splitTunnelConfig.value = updated
        repository.saveSplitTunnelConfig(updated)
    }

    fun selectAllAppsForSplit(select: Boolean) {
        val packages = if (select) {
            _installedApps.value.map { it.packageName }.toSet()
        } else {
            emptySet()
        }
        val updated = _splitTunnelConfig.value.copy(excludedPackages = packages)
        _splitTunnelConfig.value = updated
        repository.saveSplitTunnelConfig(updated)
    }

    fun setAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            try {
                _installedApps.value = repository.getInstalledApplications()
            } catch (e: Exception) {
                // handle error
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun refreshServers() {
        repository.refreshServers()
    }

    fun clearLogs() {
        repository.clearLogs()
    }

    fun startVpn() {
        repository.startVpn()
    }

    fun stopVpn() {
        repository.stopVpn()
    }
}
