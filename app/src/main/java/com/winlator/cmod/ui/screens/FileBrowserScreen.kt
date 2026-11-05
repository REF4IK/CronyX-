package com.winlator.cmod.ui.screens

import androidx.compose.foundation.shape.CircleShape
import com.winlator.cmod.ui.components.glassBadgeFill
import android.graphics.Bitmap
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.win32.PEParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Compose-аналог GameImportPickerActivity.
 * Самописный проводник в grid 3 колонки, breadcrumb, иконки папок и exe.
 * Возвращает выбранный файл через [onFilePicked].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    initialDir: File? = null,
    rootDir: File = Environment.getExternalStorageDirectory(),
    title: String = "Внутреннее хранилище",
    onBack: () -> Unit,
    onFilePicked: (File) -> Unit,
) {
    val ctx = LocalContext.current
    var currentDir by remember { mutableStateOf(initialDir?.takeIf { it.exists() && it.isDirectory } ?: rootDir) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    // scope не используется, LaunchedEffect использует свой coroutine

    // Загрузка содержимого папки + извлечение иконок .exe
    LaunchedEffect(currentDir) {
        val list = withContext(Dispatchers.IO) { listDirectory(currentDir) }
        entries = list
    }

    // Системная кнопка "Назад" → вверх по дереву или выход
    BackHandler(enabled = true) {
        val parent = currentDir.parentFile
        if (parent != null && currentDir.absolutePath != rootDir.absolutePath) {
            currentDir = parent
            selectedFile = null
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDir.name.ifEmpty { title }) },
                navigationIcon = {
                    IconButton(onClick = {
                        val parent = currentDir.parentFile
                        if (parent != null && currentDir.absolutePath != rootDir.absolutePath) {
                            currentDir = parent
                            selectedFile = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Breadcrumb
                BreadcrumbBar(
                    rootDir = rootDir,
                    currentDir = currentDir,
                    onNavigate = { currentDir = it },
                )
                Spacer(Modifier.height(12.dp))

                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.no_items_to_display),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(entries) { entry ->
                            FileEntryTile(
                                entry = entry,
                                onClick = {
                                    if (entry.file.isDirectory) {
                                        currentDir = entry.file
                                        selectedFile = null
                                    } else if (entry.file.name.endsWith(".exe", ignoreCase = true)) {
                                        selectedFile = entry.file
                                        onFilePicked(entry.file)
                                    } else {
                                        // В старом интерфейсе видны только .exe — остальные не показываются (фильтр в listDirectory)
                                        AppUtils.showToast(ctx, "Please select an .exe file")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class FileEntry(
    val file: File,
    val icon: Bitmap? = null,
    val size: Long = 0,
    val fileCount: Int = 0,
)

/**
 * Загружает содержимое директории с фильтром: только папки и .exe файлы.
 * Для .exe параллельно извлекает иконку через PEParser.extractIcon.
 */
private fun listDirectory(dir: File): List<FileEntry> {
    val files = dir.listFiles() ?: return emptyList()
    // Фильтр: только папки и .exe файлы
    val filtered = files.filter { f ->
        f.isDirectory || f.name.endsWith(".exe", ignoreCase = true)
    }
    return filtered
        .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        .map { f ->
            if (f.isDirectory) {
                FileEntry(f, fileCount = f.listFiles()?.size ?: 0)
            } else {
                val icon = try { PEParser.extractIcon(f) } catch (_: Throwable) { null }
                FileEntry(f, icon = icon, size = f.length())
            }
        }
}

@Composable
private fun FileEntryTile(
    entry: FileEntry,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val isSelected = remember(entry) { entry.file.name.endsWith(".exe", ignoreCase = true) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(glassBadgeFill()),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.file.isDirectory) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(56.dp),
                )
            } else if (entry.icon != null) {
                Image(
                    bitmap = entry.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            }
        }
        Text(
            text = entry.file.name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )
        if (entry.file.isDirectory) {
            // Plurals для счётчика файлов в папке
            val countText = ctx.resources.getQuantityString(R.plurals.game_import_files_count, entry.fileCount, entry.fileCount)
            Text(text = countText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        } else if (entry.size > 0) {
            Text(
                text = formatSize(entry.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BreadcrumbBar(
    rootDir: File,
    currentDir: File,
    onNavigate: (File) -> Unit,
) {
    val ctx = LocalContext.current
    val relPath = currentDir.absolutePath.removePrefix(rootDir.absolutePath).trim('/')
    val parts = if (relPath.isEmpty()) emptyList() else relPath.split('/')
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onNavigate(rootDir) }) {
            Text(stringResource(R.string.game_import_internal_storage), color = MaterialTheme.colorScheme.primary)
        }
        parts.forEachIndexed { i, part ->
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = {
                val subPath = parts.subList(0, i + 1).joinToString("/")
                onNavigate(File(rootDir, subPath))
            }) {
                Text(part, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.1f GB", gb)
}
