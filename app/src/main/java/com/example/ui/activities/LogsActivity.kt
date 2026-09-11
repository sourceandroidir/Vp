package com.example.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.WarningAmber
import com.example.vpn.PsiphonVpnService
import kotlinx.coroutines.launch

class LogsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LogsScreen(onBackPressed = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val logs by PsiphonVpnService.logs.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else logs.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    // Auto-scroll to end on new logs
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && searchQuery.isBlank()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    fun copyLogsToClipboard() {
        if (logs.isEmpty()) {
            Toast.makeText(context, "گزارشی برای کپی وجود ندارد.", Toast.LENGTH_SHORT).show()
            return
        }
        val fullText = logs.joinToString(separator = "\n")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Psiphon Logs", fullText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "${logs.size} سطر گزارش در حافظه کپی شد.", Toast.LENGTH_SHORT).show()
    }

    fun clearAllLogs() {
        if (logs.isEmpty()) {
            Toast.makeText(context, "گزارشات از قبل خالی هستند.", Toast.LENGTH_SHORT).show()
            return
        }
        PsiphonVpnService.clearLogs()
        Toast.makeText(context, "تمامی گزارشات پاک شدند.", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "گزارشات هسته سایفون",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${logs.size} رخداد ثبت‌شده",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackPressed,
                        modifier = Modifier.testTag("logs_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Search toggle button
                    IconButton(
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier.testTag("logs_toggle_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "جستجو در لاگ",
                            tint = if (showSearch) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Copy logs button
                    IconButton(
                        onClick = { copyLogsToClipboard() },
                        modifier = Modifier.testTag("logs_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "کپی کردن لاگ",
                            tint = CyberCyan
                        )
                    }

                    // Clear logs button
                    IconButton(
                        onClick = { clearAllLogs() },
                        modifier = Modifier.testTag("logs_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "پاک کردن لاگ",
                            tint = Color(0xFFFF6B6B)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (filteredLogs.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(filteredLogs.size - 1)
                        }
                    },
                    modifier = Modifier.testTag("logs_scroll_bottom_fab"),
                    containerColor = DarkSurface,
                    contentColor = CyberCyan
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "رفتن به آخرین لاگ"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search Input Row
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("logs_search_input"),
                    placeholder = { Text("جستجو در گزارشات...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "پاک کردن جستجو")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Terminal Console Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkBg)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color.DarkGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "هنوز هیچ رخدادی ثبت نشده است." else "موردی یافت نشد.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLogs) { log ->
                            val textColor = when {
                                log.contains("CONNECTED", ignoreCase = true) || log.contains("متصل", ignoreCase = true) -> NeonGreen
                                log.contains("CONNECTING", ignoreCase = true) || log.contains("در حال اتصال", ignoreCase = true) -> CyberCyan
                                log.contains("Error", ignoreCase = true) || log.contains("Failed", ignoreCase = true) || log.contains("Exception", ignoreCase = true) -> Color(0xFFFF6B6B)
                                log.contains("Warning", ignoreCase = true) -> WarningAmber
                                log.contains("Psiphon Core", ignoreCase = true) -> Color(0xFFA5B4FC)
                                else -> Color(0xFFCBD5E1)
                            }

                            Text(
                                text = log,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = textColor,
                                lineHeight = 17.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ثبت رویدادهای زنده هسته پروتکل سایفون",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                    ) {
                        Text(
                            text = "کپی (${logs.size})",
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
