package com.example.ui.dialogs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.AppInfo
import com.example.data.model.SplitTunnelConfig
import com.example.data.model.SplitTunnelMode
import com.example.ui.theme.CyberCyan

@Composable
fun SplitTunnelDialog(
    config: SplitTunnelConfig,
    apps: List<AppInfo>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onModeChange: (SplitTunnelMode) -> Unit,
    onToggleApp: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "اسپلیت تونل (مدیریت برنامه‌ها)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_split_dialog")) {
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
                    .height(480.dp)
            ) {
                // Mode selector chips
                Text(
                    text = "حالت تفکیک ترافیک:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = config.mode == SplitTunnelMode.BYPASS_SELECTED,
                        onClick = { onModeChange(SplitTunnelMode.BYPASS_SELECTED) },
                        label = { Text("بدون پروکسی (Bypass)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("split_mode_bypass"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.25f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                    FilterChip(
                        selected = config.mode == SplitTunnelMode.ONLY_SELECTED,
                        onClick = { onModeChange(SplitTunnelMode.ONLY_SELECTED) },
                        label = { Text("فقط اینها (Tunnel)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("split_mode_tunnel"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan.copy(alpha = 0.25f),
                            selectedLabelColor = CyberCyan
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Explanatory hint
                Text(
                    text = when (config.mode) {
                        SplitTunnelMode.BYPASS_SELECTED -> "برنامه‌های تیک‌خورده فیلترشکن را دور می‌زنند و با اینترنت معمولی باز می‌شوند."
                        SplitTunnelMode.ONLY_SELECTED -> "تنها برنامه‌های تیک‌خورده از فیلترشکن عبور خواهند کرد."
                        SplitTunnelMode.ALL_APPS -> "تمام برنامه‌ها از فیلترشکن عبور می‌کنند."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_apps_input"),
                    placeholder = { Text("جستجوی برنامه‌ها...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Count & quick actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${config.excludedPackages.size} برنامه انتخاب شده",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onSelectAll(true) }, modifier = Modifier.testTag("split_select_all")) {
                            Text("انتخاب همه", fontSize = 12.sp)
                        }
                        TextButton(onClick = { onSelectAll(false) }, modifier = Modifier.testTag("split_clear_all")) {
                            Text("پاک کردن", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyberCyan)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            val isChecked = config.excludedPackages.contains(app.packageName)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onToggleApp(app.packageName) }
                                    .testTag("split_app_${app.packageName}"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                tonalElevation = if (isChecked) 3.dp else 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // App Icon
                                    if (app.icon != null) {
                                        val bitmap = remember(app.packageName) {
                                            try {
                                                app.icon.toBitmap(width = 72, height = 72, config = Bitmap.Config.ARGB_8888)
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = app.appName,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        } else {
                                            AppIconFallback(app.appName)
                                        }
                                    } else {
                                        AppIconFallback(app.appName)
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { onToggleApp(app.packageName) },
                                        colors = CheckboxDefaults.colors(checkedColor = CyberCyan)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("confirm_split_button")) {
                Text("تأیید و ذخیره", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun AppIconFallback(appName: String) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = appName.take(1).uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = CyberCyan
        )
    }
}
