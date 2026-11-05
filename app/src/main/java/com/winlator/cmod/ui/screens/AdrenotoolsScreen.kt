package com.winlator.cmod.ui.screens

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.contents.AdrenotoolsManager

/**
 * Полный перенос AdrenotoolsFragment.java на Jetpack Compose.
 * Список установленных драйверов + установка + экспорт + удаление.
 * Кнопка "Driver Store" открывает DriverStoreScreen (через колбэк).
 * Без собственного TopAppBar — использует общий из WinlatorApp.
 * Все строки из R.string.* (подключены переводы).
 */
@Composable
fun AdrenotoolsScreen(
    refreshKey: Int = 0,
    onBack: () -> Unit,
    onOpenDriverStore: () -> Unit,
) {
    val ctx = LocalContext.current
    val adrenotoolsManager = remember { AdrenotoolsManager(ctx) }

    // Список установленных драйверов (обновляемый)
    var driversList by remember { mutableStateOf(adrenotoolsManager.enumarateInstalledDrivers()) }

    LaunchedEffect(refreshKey) {
        driversList = adrenotoolsManager.enumarateInstalledDrivers()
    }

    // Состояние диалога подтверждения установки
    var showInstallConfirm by remember { mutableStateOf(false) }
    // Состояние диалога подтверждения удаления
    var driverToRemove by remember { mutableStateOf<String?>(null) }

    // Лаунчер выбора файла драйвера (как onActivityResult с OPEN_FILE_REQUEST_CODE)
    val installDriverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Сохраняем persistable-разрешение на чтение (на случай если URI понадобится позже)
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                ctx.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) { /* не критично — используем сразу */ }

            val driver = try {
                adrenotoolsManager.installDriver(uri)
            } catch (e: Exception) {
                android.util.Log.e("AdrenotoolsScreen", "installDriver failed", e)
                ""
            }
            if (driver.isNotEmpty()) {
                driversList = adrenotoolsManager.enumarateInstalledDrivers()
                Toast.makeText(ctx, ctx.getString(R.string.install_drivers) + ": OK", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, R.string.install_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (driversList.isEmpty()) {
            // Пустое состояние
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Memory, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("No drivers installed", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.install_drivers_message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 32.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(driversList, key = { it }) { driverId ->
                    DriverCard(
                        driverId = driverId,
                        name = adrenotoolsManager.getDriverName(driverId),
                        version = adrenotoolsManager.getDriverVersion(driverId),
                        onExport = {
                            try {
                                val ok = adrenotoolsManager.exportDriverToDownloads(driverId)
                                Toast.makeText(ctx, if (ok) R.string.export_success else R.string.export_failed, Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, R.string.export_failed, Toast.LENGTH_LONG).show()
                            }
                        },
                        onRemove = { driverToRemove = driverId }
                    )
                }
            }
        }

        // FAB внизу справа
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Driver Store
            FloatingActionButton(
                onClick = onOpenDriverStore,
                // FAB без shape берёт shapes.large (26dp) — был квадратом
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(Icons.Filled.Store, contentDescription = stringResource(R.string.driver_store))
            }
            // Install driver
            FloatingActionButton(
                onClick = { showInstallConfirm = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.install_drivers), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    // Диалог подтверждения установки (как ContentDialog.confirm в оригинале)
    if (showInstallConfirm) {
        AlertDialog(
            onDismissRequest = { showInstallConfirm = false },
            title = { Text(stringResource(R.string.install_drivers)) },
            text = {
                Text(stringResource(R.string.install_drivers_message) + " " + stringResource(R.string.install_drivers_warning))
            },
            confirmButton = {
                TextButton(onClick = {
                    showInstallConfirm = false
                    installDriverLauncher.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showInstallConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Диалог подтверждения удаления драйвера
    driverToRemove?.let { driverId ->
        AlertDialog(
            onDismissRequest = { driverToRemove = null },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.do_you_want_to_remove_this_content)) },
            confirmButton = {
                TextButton(onClick = {
                    adrenotoolsManager.removeDriver(driverId)
                    driversList = adrenotoolsManager.enumarateInstalledDrivers()
                    driverToRemove = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { driverToRemove = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun DriverCard(
    driverId: String,
    name: String,
    version: String,
    onExport: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
        shape = RoundedCornerShape(GlassTokens.CardRadius),
        border = glassCardBorder(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifEmpty { driverId }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (version.isNotEmpty()) {
                    Text(version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Экспорт
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.export), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Удаление
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
