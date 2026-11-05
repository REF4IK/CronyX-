package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.winhandler.ProcessInfo

data class ProcessUiInfo(
    val name: String,
    val pid: Int,
    val memory: String,
    val isRunning: Boolean,
    val isWoW64: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskManagerScreen(
    processes: List<ProcessUiInfo> = emptyList(),
    cpuUsage: Int = 0,
    memoryUsage: Int = 0,
    memoryUsed: String = "0 MB",
    memoryTotal: String = "0 MB",
    batteryLevel: String = "N/A",
    cpuTemp: String = "N/A",
    batteryTemp: String = "N/A",
    onBack: () -> Unit,
    onBringToFront: (ProcessUiInfo) -> Unit = {},
    onEndProcess: (ProcessUiInfo) -> Unit = {},
) {
    var showEndConfirm by remember { mutableStateOf<ProcessUiInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Text(
                        "${processes.size} processes",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Stats Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // CPU Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                        shape = RoundedCornerShape(GlassTokens.CardRadius),
                        border = glassCardBorder(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("CPU Usage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { cpuUsage / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("$cpuUsage%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Memory Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                        shape = RoundedCornerShape(GlassTokens.CardRadius),
                        border = glassCardBorder(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Memory", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { memoryUsage / 100f },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("$memoryUsage%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("$memoryUsed / $memoryTotal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Info rows
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                    shape = RoundedCornerShape(GlassTokens.CardRadius),
                    border = glassCardBorder(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        InfoRow("CPU Temp", cpuTemp)
                        InfoRow("Battery", "$batteryLevel / $batteryTemp")
                    }
                }
            }

            // Process list header
            item {
                Text(
                    "Processes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }

            if (processes.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No processes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(processes, key = { "${it.name}_${it.pid}" }) { proc ->
                    ProcessItem(
                        info = proc,
                        onBringToFront = { onBringToFront(proc) },
                        onEndProcess = { showEndConfirm = proc },
                    )
                }
            }
        }
    }

    // End process confirmation
    showEndConfirm?.let { proc ->
        AlertDialog(
            onDismissRequest = { showEndConfirm = null },
            title = { Text("End Process") },
            text = { Text("Do you want to end ${proc.name} (PID: ${proc.pid})?") },
            confirmButton = {
                TextButton(onClick = {
                    onEndProcess(proc)
                    showEndConfirm = null
                }) { Text("End", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProcessItem(
    info: ProcessUiInfo,
    onBringToFront: () -> Unit,
    onEndProcess: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
        shape = RoundedCornerShape(GlassTokens.CardRadius),
        border = glassCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            Icon(
                Icons.Filled.Memory,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (info.isRunning) Color(0xFF4CAF50) else Color.Gray,
            )
            Spacer(Modifier.width(12.dp))
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    info.name + if (info.isWoW64) " *32" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PID: ${info.pid}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(info.memory, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (info.isRunning) "Running" else "Bg",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (info.isRunning) Color(0xFF4CAF50) else Color.Gray,
                    )
                }
            }
            // Menu button
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Bring to Front") }, onClick = { showMenu = false; onBringToFront() }, leadingIcon = { Icon(Icons.Filled.OpenInNew, null) })
                    DropdownMenuItem(text = { Text("End Process", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onEndProcess() }, leadingIcon = { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
