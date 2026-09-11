package com.example.ui.dialogs

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerRegion
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkSurfaceVariant

@Composable
fun ServerSelectionDialog(
    currentSelection: ServerRegion,
    availableRegions: List<ServerRegion>,
    isRefreshing: Boolean = false,
    onSelectRegion: (ServerRegion) -> Unit,
    onRefreshServers: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(searchQuery, availableRegions) {
        if (searchQuery.isBlank()) availableRegions
        else availableRegions.filter {
            it.nameFa.contains(searchQuery, ignoreCase = true) ||
            it.nameEn.contains(searchQuery, ignoreCase = true) ||
            it.code.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "انتخاب سرور سایفون",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshServers,
                        enabled = !isRefreshing,
                        modifier = Modifier.testTag("refresh_servers_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CyberCyan
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "بروزرسانی لیست سرورها",
                                tint = CyberCyan
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_server_dialog")) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_search_input"),
                    placeholder = { Text("جستجوی کشور یا سرور...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isRefreshing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberCyan.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "در حال دریافت و بروزرسانی لیست سرورها...",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.code }) { region ->
                        val isSelected = region.code.equals(currentSelection.code, ignoreCase = true)
                        val serverCount = region.activeServerCount ?: 10

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectRegion(region)
                                    onDismiss()
                                }
                                .testTag("server_item_${region.code.ifEmpty { "auto" }}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) DarkSurfaceVariant else MaterialTheme.colorScheme.surface,
                            tonalElevation = if (isSelected) 4.dp else 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = region.flag,
                                        fontSize = 20.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = region.nameFa,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) CyberCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    if (region.code.isEmpty()) {
                                        Text(
                                            text = "اتصال خودکار به سریع‌ترین سرور ($serverCount سرور فعال)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${region.nameEn} (${region.code})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = " • $serverCount سرور فعال",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = CyberCyan
                                            )
                                        }
                                    }
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectRegion(region)
                                        onDismiss()
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = CyberCyan
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("confirm_server_selection")) {
                Text("تأیید", style = MaterialTheme.typography.labelLarge)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

