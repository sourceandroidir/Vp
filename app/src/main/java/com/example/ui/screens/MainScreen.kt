package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SplitTunnelMode
import com.example.data.model.VpnState
import com.example.ui.MainViewModel
import com.example.ui.activities.LogsActivity
import com.example.ui.dialogs.LogsDialog
import com.example.ui.dialogs.ServerSelectionDialog
import com.example.ui.dialogs.SplitTunnelDialog
import com.example.ui.dialogs.UpstreamProxyDialog
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DisconnectRed
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val vpnState by viewModel.vpnState.collectAsState()
    val currentRegion by viewModel.currentRegion.collectAsState()
    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val trafficStats by viewModel.trafficStats.collectAsState()
    val availableRegions by viewModel.availableRegions.collectAsState()
    val isRefreshingServers by viewModel.isRefreshingServers.collectAsState()
    val upstreamProxy by viewModel.upstreamProxy.collectAsState()
    val splitTunnelConfig by viewModel.splitTunnelConfig.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()
    val appSearchQuery by viewModel.appSearchQuery.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var showServerDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    // Request VPN system permission
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn()
        }
    }

    // Notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Active connection duration timer
    var durationSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vpnState) {
        if (vpnState == VpnState.CONNECTED) {
            val startTime = System.currentTimeMillis()
            while (true) {
                durationSeconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        } else {
            durationSeconds = 0L
        }
    }

    fun handleConnectToggle() {
        if (vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING) {
            viewModel.stopVpn()
        } else {
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                vpnPrepareLauncher.launch(vpnIntent)
            } else {
                viewModel.startVpn()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnLock,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "سایفون وی‌پی‌ان",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Upstream Proxy quick action
                    IconButton(
                        onClick = { showProxyDialog = true },
                        modifier = Modifier.testTag("action_upstream_proxy")
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "پروکسی بالا دست",
                                tint = if (upstreamProxy.enabled) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (upstreamProxy.enabled) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CyberCyan)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }

                    // Split Tunnel quick action
                    IconButton(
                        onClick = { showSplitDialog = true },
                        modifier = Modifier.testTag("action_split_tunnel")
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.CallSplit,
                                contentDescription = "اسپلیت تونل",
                                tint = if (splitTunnelConfig.excludedPackages.isNotEmpty()) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (splitTunnelConfig.excludedPackages.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }

                    // Logs quick action
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, LogsActivity::class.java))
                        },
                        modifier = Modifier.testTag("action_logs")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ListAlt,
                            contentDescription = "گزارشات",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // VPN Status Pill
            VpnStatusBadge(vpnState = vpnState)

            // Connection Duration (if connected)
            if (vpnState == VpnState.CONNECTED) {
                val hours = durationSeconds / 3600
                val minutes = (durationSeconds % 3600) / 60
                val seconds = durationSeconds % 60
                val timeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main VPN Connect / Disconnect Action Button
            VpnPowerButton(
                vpnState = vpnState,
                onClick = { handleConnectToggle() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Server Selection Card
            ServerSelectorCard(
                activeRegion = if (vpnState == VpnState.CONNECTED) currentRegion else selectedRegion,
                onClick = { showServerDialog = true }
            )

            // Real-time Traffic Statistics Card
            TrafficStatsCard(stats = trafficStats, isConnected = vpnState == VpnState.CONNECTED)

            // Features Overview Row (Upstream Proxy + Split Tunneling)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Upstream proxy card
                FeatureSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "پروکسی بالا دست",
                    status = if (upstreamProxy.enabled) "${upstreamProxy.protocol}: ${upstreamProxy.host}" else "غیرفعال",
                    isActive = upstreamProxy.enabled,
                    icon = Icons.Default.CloudQueue,
                    onClick = { showProxyDialog = true },
                    testTag = "card_upstream_proxy"
                )

                // Split tunnel card
                val splitStatusText = when (splitTunnelConfig.mode) {
                    SplitTunnelMode.BYPASS_SELECTED -> "${splitTunnelConfig.excludedPackages.size} برنامه بدون پروکسی"
                    SplitTunnelMode.ONLY_SELECTED -> "${splitTunnelConfig.excludedPackages.size} برنامه تونل شده"
                    SplitTunnelMode.ALL_APPS -> "کل گوشی تونل شده"
                }
                FeatureSummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "اسپلیت تونل",
                    status = splitStatusText,
                    isActive = splitTunnelConfig.excludedPackages.isNotEmpty(),
                    icon = Icons.Default.CallSplit,
                    onClick = { showSplitDialog = true },
                    testTag = "card_split_tunnel"
                )
            }

            // Whole device tunneling indicator notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurface.copy(alpha = 0.8f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (vpnState == VpnState.CONNECTED) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (vpnState == VpnState.CONNECTED) "تونل سراسری فعال است" else "امنیت و حفظ حریم خصوصی",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (vpnState == VpnState.CONNECTED)
                                "تمام ترافیک مجاز گوشی از طریق هسته سایفون ایمن شده است."
                            else
                                "با فشردن کلید اتصال، کلیه ترافیک گوشی از بستر امن سایفون عبور خواهد کرد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Dialogs
    if (showServerDialog) {
        ServerSelectionDialog(
            currentSelection = selectedRegion,
            availableRegions = availableRegions,
            isRefreshing = isRefreshingServers,
            onSelectRegion = { region ->
                viewModel.selectRegion(region)
            },
            onRefreshServers = {
                viewModel.refreshServers()
            },
            onDismiss = { showServerDialog = false }
        )
    }

    if (showProxyDialog) {
        UpstreamProxyDialog(
            initialConfig = upstreamProxy,
            onSaveConfig = { config ->
                viewModel.saveUpstreamProxy(config)
            },
            onDismiss = { showProxyDialog = false }
        )
    }

    if (showSplitDialog) {
        SplitTunnelDialog(
            config = splitTunnelConfig,
            apps = filteredApps,
            isLoading = isLoadingApps,
            searchQuery = appSearchQuery,
            onSearchQueryChange = { viewModel.setAppSearchQuery(it) },
            onModeChange = { viewModel.setSplitTunnelMode(it) },
            onToggleApp = { viewModel.toggleAppSplit(it) },
            onSelectAll = { viewModel.selectAllAppsForSplit(it) },
            onDismiss = { showSplitDialog = false }
        )
    }

    if (showLogsDialog) {
        LogsDialog(
            logs = logs,
            onDismiss = { showLogsDialog = false }
        )
    }
}

@Composable
private fun VpnStatusBadge(vpnState: VpnState) {
    val bgColor = when (vpnState) {
        VpnState.DISCONNECTED -> DisconnectRed.copy(alpha = 0.15f)
        VpnState.CONNECTING -> WarningAmber.copy(alpha = 0.15f)
        VpnState.CONNECTED -> NeonGreen.copy(alpha = 0.15f)
        VpnState.STOPPING -> WarningAmber.copy(alpha = 0.15f)
    }
    val textColor = when (vpnState) {
        VpnState.DISCONNECTED -> DisconnectRed
        VpnState.CONNECTING -> WarningAmber
        VpnState.CONNECTED -> NeonGreen
        VpnState.STOPPING -> WarningAmber
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = vpnState.titleFa,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun VpnPowerButton(
    vpnState: VpnState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (vpnState == VpnState.CONNECTING) 1.15f else if (vpnState == VpnState.CONNECTED) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val ringColor by animateColorAsState(
        targetValue = when (vpnState) {
            VpnState.CONNECTED -> NeonGreen
            VpnState.CONNECTING -> WarningAmber
            VpnState.STOPPING -> WarningAmber
            VpnState.DISCONNECTED -> CyberCyan
        },
        label = "ringColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(190.dp)
            .testTag("vpn_power_button_container")
    ) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(ringColor.copy(alpha = if (vpnState == VpnState.CONNECTED) 0.18f else 0.08f))
        )

        // Middle border ring
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(CircleShape)
                .border(2.dp, ringColor.copy(alpha = 0.5f), CircleShape)
        )

        // Main inner interactive button
        Surface(
            modifier = Modifier
                .size(136.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .shadow(16.dp, CircleShape, spotColor = ringColor)
                .testTag("vpn_toggle_button"),
            shape = CircleShape,
            color = DarkSurfaceVariant,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "اتصال یا قطع وی پی ان",
                    tint = ringColor,
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (vpnState) {
                        VpnState.CONNECTED -> "قطع اتصال"
                        VpnState.CONNECTING -> "صبر کنید..."
                        VpnState.STOPPING -> "قطع کردن..."
                        VpnState.DISCONNECTED -> "اتصال"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ringColor
                )
            }
        }
    }
}

@Composable
private fun ServerSelectorCard(
    activeRegion: com.example.data.model.ServerRegion,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("server_selector_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flag avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = activeRegion.flag, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "سرور انتخاب شده",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = activeRegion.nameFa,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (activeRegion.code.isEmpty()) "سریع‌ترین سرور در دسترس" else "${activeRegion.nameEn} (${activeRegion.code})",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberCyan
                )
            }

            Text(
                text = "تغییر سرور",
                style = MaterialTheme.typography.bodySmall,
                color = CyberCyan,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = null,
                tint = CyberCyan,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun TrafficStatsCard(
    stats: com.example.data.model.TrafficStats,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Download
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CyberCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "دریافتی (دانلود)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isConnected) stats.formattedBytesIn() else "0 B",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(1.dp)
                    .background(DarkCardBorder)
            )

            // Upload
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeonGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ارسالی (آپلود)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isConnected) stats.formattedBytesOut() else "0 B",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    status: String,
    isActive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) CyberCyan.copy(alpha = 0.5f) else DarkCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CyberCyan)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
