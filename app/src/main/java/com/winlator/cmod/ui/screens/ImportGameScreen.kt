package com.winlator.cmod.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.GameTitleParser
import com.winlator.cmod.core.ShortcutCoverFetcher
import com.winlator.cmod.win32.PEParser
import java.io.File
import java.util.Locale

/**
 * Compose-версия GameImportConfirmActivity.
 * После выбора .exe в FileBrowser показывает иконку, редактируемое имя, описание, путь и кнопки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportGameScreen(
    container: Container,
    onBack: () -> Unit,
    onDone: (Shortcut) -> Unit = {},
) {
    val ctx = LocalContext.current
    val manager = remember { ContainerManager(ctx) }

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var exePath by remember { mutableStateOf("") }
    var exeIcon by remember { mutableStateOf<Bitmap?>(null) }
    var showContainerPicker by remember { mutableStateOf(false) }
    var selectedContainer by remember { mutableStateOf(container) }
    var showFileBrowser by remember { mutableStateOf(true) } // Сразу показываем FileBrowser
    var showConfirmUI by remember { mutableStateOf(false) }

    BackHandler(enabled = showFileBrowser || showConfirmUI || showContainerPicker) {
        when {
            showFileBrowser -> onBack()
            showConfirmUI -> showConfirmUI = false
            showContainerPicker -> showContainerPicker = false
            else -> onBack()
        }
    }

    // FileBrowser для выбора .exe
    if (showFileBrowser) {
        FileBrowserScreen(
            title = stringResource(R.string.select_exe),
            onBack = {
                showFileBrowser = false
                if (!showConfirmUI) onBack()
            },
            onFilePicked = { file ->
                exePath = file.absolutePath
                // Имя из манифеста Steam/GOG, PE-версии или чистое имя папки/exe
                name = try {
                    GameTitleParser.suggestName(file)?.takeIf { it.isNotBlank() }
                        ?: file.nameWithoutExtension
                } catch (_: Throwable) {
                    file.nameWithoutExtension
                }
                // Извлекаем иконку
                exeIcon = try { PEParser.extractIcon(file) } catch (_: Throwable) { null }
                showFileBrowser = false
                showConfirmUI = true
            },
        )
        return // не рендерим confirm UI пока FileBrowser открыт
    }

    // Confirm UI — показать после выбора файла
    if (showConfirmUI) {
        var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var showCoverBrowser by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.import_game)) },
                    navigationIcon = {
                        IconButton(onClick = { showConfirmUI = false; showFileBrowser = true }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Иконка игры (200x112 как в старом GameImportConfirmActivity)
                Box(
                    modifier = Modifier
                        .size(200.dp, 112.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33000000))
                        .clickable { showCoverBrowser = true },
                    contentAlignment = Alignment.Center,
                ) {
                    val displayIcon = coverBitmap ?: exeIcon
                    if (displayIcon != null) {
                        Image(
                            bitmap = displayIcon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Имя
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text(stringResource(R.string.name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("${name.length}/60") },
                )

                // Описание (опционально)
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Путь к exe
                Text(stringResource(R.string.path_to_launch_file), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = exePath,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        TextButton(onClick = { showFileBrowser = true; showConfirmUI = false }) {
                            Text(stringResource(R.string.change))
                        }
                    }
                }

                // Контейнер
                Text(stringResource(R.string.container), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showContainerPicker = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.VideogameAsset, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(selectedContainer.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.change), color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = {
                        if (name.isEmpty()) {
                            AppUtils.showToast(ctx, R.string.name_and_exe_required)
                            return@Button
                        }
                        try {
                            val absolutePath = exePath
                            val exeFile = File(absolutePath)
                            val fileName = exeFile.name
                            val safeName = GameTitleParser.normalizeName(name).ifBlank { name }
                            ShortcutCoverFetcher.clearCoverCache(ctx, safeName)
                            val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
                            val relativePath = absolutePath.lowercase(Locale.ENGLISH)

                            var driveLetter = "D:"
                            when {
                                relativePath.contains(externalStoragePath.lowercase(Locale.ENGLISH)) -> driveLetter = "D:"
                                absolutePath.contains("/imagefs/") -> driveLetter = "Z:"
                                absolutePath.contains("/.wine/drive_c/") -> driveLetter = "C:"
                            }

                            var pathWOutPrefix = absolutePath
                            when (driveLetter) {
                                "D:" -> {
                                    pathWOutPrefix = pathWOutPrefix.removePrefix(externalStoragePath).trimStart('/')
                                    if (pathWOutPrefix.lowercase(Locale.ENGLISH).startsWith("download/")) {
                                        pathWOutPrefix = pathWOutPrefix.substring(9)
                                    }
                                }
                                "Z:" -> {
                                    val idx = pathWOutPrefix.indexOf("/imagefs/")
                                    if (idx != -1) pathWOutPrefix = pathWOutPrefix.substring(idx + 9)
                                }
                                "C:" -> {
                                    val idx = pathWOutPrefix.indexOf("/.wine/drive_c/")
                                    if (idx != -1) pathWOutPrefix = pathWOutPrefix.substring(idx + 15)
                                }
                            }

                            val execPath = pathWOutPrefix
                            val lastSlash = pathWOutPrefix.lastIndexOf("/")
                            val pathDir = if (lastSlash > 0) pathWOutPrefix.substring(0, lastSlash) else ""

                            val randomNum = (Math.random() * 10000).toInt()
                            val iconName = "${randomNum}_$safeName.0"

                            val exeIcon = try { PEParser.extractIcon(exeFile) } catch (_: Throwable) { null }
                            if (exeIcon != null) {
                                val iconDir = selectedContainer.getIconsDir(64)
                                if (!iconDir.exists()) iconDir.mkdirs()
                                FileUtils.saveBitmapToFile(exeIcon, File(iconDir, "$iconName.png"))
                            }

                            val desktopDir = selectedContainer.desktopDir
                            if (!desktopDir.exists()) desktopDir.mkdirs()
                            val file = File(desktopDir, "$safeName.desktop")
                            val content = buildString {
                                append("[Desktop Entry]\n")
                                append("Name=$safeName\n")
                                append("Exec=env WINEPREFIX=\"/data/user/0/${MainActivity.PACKAGE_NAME}/files/imagefs/home/xuser/.wine/dosdevices/z:/home/xuser/.wine\" wine $driveLetter/$execPath\n")
                                if (description.isNotEmpty()) append("Comment=$description\n")
                                append("Type=Application\n")
                                append("StartupNotify=true\n")
                                append("Path=/data/user/0/${MainActivity.PACKAGE_NAME}/files/imagefs/home/xuser/.wine/dosdevices/${driveLetter.lowercase(Locale.ENGLISH)}/$pathDir\n")
                                append("Icon=$iconName\n")
                                append("StartupWMClass=${fileName.lowercase(Locale.ENGLISH)}\n")
                                append("container_id:${selectedContainer.id}\n")
                            }
                            FileUtils.writeString(file, content)
                            // Сохраняем обложку
                            coverBitmap?.let { bmp ->
                                val coverDir = File(selectedContainer.rootDir, "app_data/cover_arts")
                                coverDir.mkdirs()
                                FileUtils.saveBitmapToFile(bmp, File(coverDir, "$safeName.png"))
                            }
                            AppUtils.showToast(ctx, R.string.game_imported)
                            onBack()
                        } catch (e: Exception) {
                            AppUtils.showToast(ctx, "Error: ${e.message}")
                        }
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }

        // FileBrowser для смены/выбора обложки
        if (showCoverBrowser) {
            FileBrowserScreen(
                title = stringResource(R.string.add_cover),
                onBack = { showCoverBrowser = false },
                onFilePicked = { file ->
                    try { coverBitmap = BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) {}
                    showCoverBrowser = false
                },
            )
        }

        // Диалог выбора контейнера
        if (showContainerPicker) {
            ContainerPickerDialog(
                containers = manager.containers,
                onDismiss = { showContainerPicker = false },
                onPick = { c ->
                    selectedContainer = c
                    showContainerPicker = false
                },
            )
        }
    }
}
