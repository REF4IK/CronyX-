package com.winlator.cmod.ui.screens

import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.InstalledComponent
import com.winlator.cmod.R
import com.winlator.cmod.SectionHeader
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contents.AdrenotoolsManager
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.midi.MidiManager
import java.io.File

/**
 * Полный перенос InstalledComponentsFragment.java на Jetpack Compose.
 * Показывает все установленные компоненты (контент, GPU-драйверы, SoundFont'ы)
 * с группировкой по секциям, общим размером и удалением.
 * Все строки из R.string.* (подключены переводы).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledComponentsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val contentsManager = remember { ContentsManager(ctx) }
    val adrenotoolsManager = remember { AdrenotoolsManager(ctx) }

    // Список компонентов (с секциями)
    var components by remember { mutableStateOf<List<InstalledComponent>>(emptyList()) }
    var totalSize by remember { mutableStateOf(0L) }
    var componentToDelete by remember { mutableStateOf<InstalledComponent?>(null) }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun getDirSize(dir: File): Long {
        if (dir.isFile) return dir.length()
        var size = 0L
        val files = dir.listFiles() ?: return 0
        for (f in files) size += if (f.isFile) f.length() else getDirSize(f)
        return size
    }

    // Загрузка списка установленных компонентов
    fun loadInstalledComponents() {
        contentsManager.syncContents()
        val list = mutableListOf<InstalledComponent>()

        // 1. Контент (Wine/DXVK/VKD3D/Box64/WOWBox64/FEXCore)
        for (type in ContentProfile.ContentType.values()) {
            val profiles = contentsManager.getProfiles(type) ?: continue
            val typeComponents = mutableListOf<InstalledComponent>()
            for (profile in profiles) {
                if (profile.remoteUrl != null) continue
                val installDir = ContentsManager.getInstallDir(ctx, profile)
                val size = if (installDir.exists()) getDirSize(installDir) else 0
                val iconRes = if (type == ContentProfile.ContentType.CONTENT_TYPE_WINE) R.drawable.icon_wine else R.drawable.icon_settings
                val version = ctx.getString(R.string.version) + ": " + profile.verName + " (" + profile.verCode + ")"
                typeComponents.add(InstalledComponent(profile.verName, version, type.toString(), size, iconRes, InstalledComponent.ComponentCategory.CONTENT, profile))
            }
            if (typeComponents.isNotEmpty()) {
                list.add(SectionHeader(type.toString()))
                list.addAll(typeComponents)
            }
        }

        // 2. GPU-драйверы (Adrenotools)
        val drivers = adrenotoolsManager.enumarateInstalledDrivers()
        if (drivers.isNotEmpty()) {
            list.add(SectionHeader(ctx.getString(R.string.component_type_gpu_driver)))
            for (driverId in drivers) {
                val name = adrenotoolsManager.getDriverName(driverId)
                val driverVersion = adrenotoolsManager.getDriverVersion(driverId)
                val driverPath = File(ctx.filesDir, "imagefs/contents/adrenotools/$driverId")
                val size = if (driverPath.exists()) getDirSize(driverPath) else 0
                val version = if (driverVersion.isNotEmpty()) ctx.getString(R.string.version) + ": " + driverVersion else ""
                list.add(InstalledComponent(
                    if (name.isNotEmpty()) name else driverId,
                    version,
                    ctx.getString(R.string.component_type_gpu_driver),
                    size,
                    R.drawable.icon_snapdragon,
                    InstalledComponent.ComponentCategory.ADRENOTOOLS,
                    driverId
                ))
            }
        }

        // 3. SoundFont'ы
        val sf2Files = MidiManager.getSF2Files(ctx)
        if (sf2Files != null && sf2Files.isNotEmpty()) {
            val sfComponents = mutableListOf<InstalledComponent>()
            for (file in sf2Files) {
                if (file.name == MidiManager.DEFAULT_SF2_FILE) continue
                val size = file.length()
                sfComponents.add(InstalledComponent(file.name, formatSize(size), ctx.getString(R.string.component_type_soundfont), size, R.drawable.icon_audio_settings, InstalledComponent.ComponentCategory.SOUNDFONT, file.name))
            }
            if (sfComponents.isNotEmpty()) {
                list.add(SectionHeader(ctx.getString(R.string.component_type_soundfont)))
                list.addAll(sfComponents)
            }
        }

        components = list
        totalSize = list.filter { !it.isSectionHeader() }.sumOf { it.sizeBytes }
    }

    LaunchedEffect(Unit) { loadInstalledComponents() }

    // Удаление компонента
    fun deleteComponent(component: InstalledComponent) {
        when (component.category) {
            InstalledComponent.ComponentCategory.CONTENT -> {
                val profile = component.identifier as ContentProfile
                if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE) {
                    val containerManager = ContainerManager(ctx)
                    for (container in containerManager.containers) {
                        if (container.wineVersion == ContentsManager.getEntryName(profile)) {
                            Toast.makeText(ctx, ctx.getString(R.string.unable_to_remove_content_since_container_using, container.name), Toast.LENGTH_LONG).show()
                            return
                        }
                    }
                }
                contentsManager.removeContent(profile)
            }
            InstalledComponent.ComponentCategory.ADRENOTOOLS -> {
                val driverId = component.identifier as String
                adrenotoolsManager.removeDriver(driverId)
            }
            InstalledComponent.ComponentCategory.SOUNDFONT -> {
                val fileName = component.identifier as String
                MidiManager.removeSF2File(ctx, fileName)
            }
            null -> {}
        }
        loadInstalledComponents()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.installed_components)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (components.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_content_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Общий размер
                Text(
                    text = stringResource(R.string.total_size, formatSize(totalSize)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(components, key = { component -> (component.name ?: "") + component.type + component.sizeBytes }) { component ->
                        if (component.isSectionHeader()) {
                            Text(
                                text = component.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        } else {
                            InstalledComponentRow(
                                component = component,
                                onDelete = { componentToDelete = component }
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог подтверждения удаления
    componentToDelete?.let { component ->
        AlertDialog(
            onDismissRequest = { componentToDelete = null },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.confirm_delete_component, component.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteComponent(component)
                    componentToDelete = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { componentToDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun InstalledComponentRow(
    component: InstalledComponent,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(component.iconResId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(component.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (component.version.isNotEmpty()) {
                Text(component.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
        }
    }
}
