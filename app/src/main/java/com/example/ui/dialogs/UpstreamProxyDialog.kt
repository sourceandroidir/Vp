package com.example.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UpstreamProxyConfig
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DisconnectRed
import com.example.ui.theme.NeonGreen
import kotlinx.coroutines.launch

@Composable
fun UpstreamProxyDialog(
    initialConfig: UpstreamProxyConfig,
    onSaveConfig: (UpstreamProxyConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember { mutableStateOf(initialConfig.enabled) }
    var protocol by remember { mutableStateOf(initialConfig.protocol) }
    var host by remember { mutableStateOf(initialConfig.host) }
    var portText by remember { mutableStateOf(if (initialConfig.port > 0) initialConfig.port.toString() else "8080") }
    var username by remember { mutableStateOf(initialConfig.username) }
    var password by remember { mutableStateOf(initialConfig.password) }

    val scope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultSuccess by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تنظیمات پروکسی بالا دست (Upstream)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_proxy_dialog")) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Info banner
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "هسته سایفون ترافیک خود را از طریق این پروکسی ارسال خواهد کرد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Enable toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "فعال‌سازی پروکسی بالا دست",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (enabled) "پروکسی فعال است" else "پروکسی غیرفعال است",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        modifier = Modifier.testTag("toggle_upstream_proxy"),
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                    )
                }

                if (enabled) {
                    // Protocol selection (HTTP / SOCKS5)
                    Text(
                        text = "نوع پروتکل پروکسی:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = protocol.equals("HTTP", ignoreCase = true),
                            onClick = { protocol = "HTTP" },
                            label = { Text("HTTP Proxy") },
                            modifier = Modifier.testTag("protocol_http"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan
                            )
                        )
                        FilterChip(
                            selected = protocol.equals("SOCKS5", ignoreCase = true),
                            onClick = { protocol = "SOCKS5" },
                            label = { Text("SOCKS5 Proxy") },
                            modifier = Modifier.testTag("protocol_socks5"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan
                            )
                        )
                    }

                    // Host
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_host_input"),
                        label = { Text("آدرس سرور (Host / IP)") },
                        placeholder = { Text("مثال: 192.168.1.100 یا proxy.example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Port
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { char -> char.isDigit() } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_port_input"),
                        label = { Text("پورت (Port)") },
                        placeholder = { Text("8080") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Optional auth
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_username_input"),
                        label = { Text("نام کاربری (اختیاری)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_password_input"),
                        label = { Text("رمز عبور (اختیاری)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test connection row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (host.isBlank()) {
                                    testResultText = "لطفاً ابتدا آدرس هاست پروکسی را وارد کنید."
                                    testResultSuccess = false
                                    return@TextButton
                                }
                                val portInt = portText.toIntOrNull() ?: 0
                                if (portInt <= 0 || portInt > 65535) {
                                    testResultText = "لطفاً پورت معتبر وارد کنید."
                                    testResultSuccess = false
                                    return@TextButton
                                }
                                isTesting = true
                                testResultText = "در حال تست اتصال به پروکسی..."
                                testResultSuccess = null
                                scope.launch {
                                    val res = com.example.util.ProxyTester.testConnection(host, portInt, protocol)
                                    isTesting = false
                                    testResultSuccess = res.isSuccess
                                    testResultText = if (res.isSuccess) {
                                        "${res.message} (تأخیر: ${res.latencyMs}ms)"
                                    } else {
                                        res.message
                                    }
                                }
                            },
                            enabled = !isTesting,
                            modifier = Modifier.testTag("test_proxy_button")
                        ) {
                            Text(
                                text = if (isTesting) "در حال تست..." else "تست اتصال پروکسی بالا دست",
                                color = if (isTesting) Color.Gray else CyberCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (testResultText != null) {
                            Text(
                                text = testResultText ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = when (testResultSuccess) {
                                    true -> NeonGreen
                                    false -> DisconnectRed
                                    else -> CyberCyan
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val portInt = portText.toIntOrNull() ?: 8080
                    onSaveConfig(
                        UpstreamProxyConfig(
                            enabled = enabled,
                            protocol = protocol,
                            host = host.trim(),
                            port = portInt,
                            username = username.trim(),
                            password = password.trim()
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("save_proxy_button")
            ) {
                Text("ذخیره تنظیمات", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", style = MaterialTheme.typography.labelLarge)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
