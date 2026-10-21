package com.winlator.cmod.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.GameTitleParser
import com.winlator.cmod.win32.PEParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.DateFormat
import java.util.Locale

private enum class FMSortBy { NAME, DATE, SIZE }
private enum class FMEntryType { DRIVE, DIRECTORY, FILE }

private data class FMFileEntry(
    val type: FMEntryType,
    val title: String,
    val subtitle: String,
    val file: File,
    val modifiedAt: Long,
    val size: Long,
    val icon: Bitmap? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(
    containerId: Int = -1,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val containerManager = remember { ContainerManager(ctx) }
    val activeContainer = remember(containerId) {
        if (containerId >= 0) containerManager.getContainerById(containerId) else null
    }

    var currentDir by remember { mutableStateOf<File?>(null) }
    var showingDriveRoot by remember { mutableStateOf(containerId >= 0) }
    
    var allEntries by remember { mutableStateOf<List<FMFileEntry>>(emptyList()) }
    var currentQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(FMSortBy.NAME) }
    var sortDesc by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(com.winlator.cmod.core.MmkvPreferences().getBoolean("fmShowHidden", false)) }
    var gridMode by remember { mutableStateOf(com.winlator.cmod.core.MmkvPreferences().getBoolean("fmGridView", false)) }
    var isOpRunning by remember { mutableStateOf(false) }
    var opProgress by remember { mutableStateOf(0f) }
    var opLabel by remember { mutableStateOf("") }

    // Clipboard
    var clipboardFile by remember { mutableStateOf<File?>(null) }
    var clipboardMove by remember { mutableStateOf(false) }

    // Dialogs
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showDeleteDialog by remember { mutableStateOf<File?>(null) }
    var activeContextEntry by remember { mutableStateOf<FMFileEntry?>(null) }
    var showShortcutContainerSelect by remember { mutableStateOf<File?>(null) }

    // Helper functions
    fun reloadCurrentLocation() {
        scope.launch(Dispatchers.IO) {
            if (showingDriveRoot && activeContainer != null) {
                val drives = LinkedHashMap<String, FMFileEntry>()
                val driveC = File(activeContainer.rootDir, ".wine/drive_c")
                if (driveC.exists()) {
                    drives["C:"] = FMFileEntry(FMEntryType.DRIVE, "C:", driveC.absolutePath, driveC, driveC.lastModified(), 0L)
                }
                try {
                    val driveZ = File(activeContainer.rootDir, "../..").canonicalFile
                    if (driveZ.exists()) {
                        drives["Z:"] = FMFileEntry(FMEntryType.DRIVE, "Z:", driveZ.absolutePath, driveZ, driveZ.lastModified(), 0L)
                    }
                } catch (_: IOException) {}

                for (drive in activeContainer.drivesIterator()) {
                    val driveLabel = drive[0].uppercase(Locale.ENGLISH) + ":"
                    val target = File(drive[1])
                    if (target.exists() && !drives.containsKey(driveLabel.uppercase(Locale.ENGLISH))) {
                        drives[driveLabel.uppercase(Locale.ENGLISH)] = FMFileEntry(FMEntryType.DRIVE, driveLabel, target.absolutePath, target, target.lastModified(), 0L)
                    }
                }
                val list = drives.values.sortedWith(compareBy { it.title.lowercase(Locale.ENGLISH) })
                withContext(Dispatchers.Main) {
                    allEntries = list
                }
            } else {
                val dir = currentDir ?: Environment.getExternalStorageDirectory()
                var children = dir.listFiles() ?: emptyArray()
                if (!showHidden) children = children.filter { !it.name.startsWith(".") }.toTypedArray()
                val list = children.map { child ->
                    val type = if (child.isDirectory) FMEntryType.DIRECTORY else FMEntryType.FILE
                    val subtitle = if (child.isDirectory) {
                        ctx.getString(R.string.fm_subtitle_template, ctx.getString(R.string.fm_folder_label), DateFormat.getDateTimeInstance().format(child.lastModified()))
                    } else {
                        ctx.getString(R.string.fm_subtitle_template, formatSize(child.length()), DateFormat.getDateTimeInstance().format(child.lastModified()))
                    }
                    val icon = if (!child.isDirectory && child.name.endsWith(".exe", ignoreCase = true)) {
                        try { PEParser.extractIcon(child) } catch (_: Throwable) { null }
                    } else null

                    FMFileEntry(type, child.name, subtitle, child, child.lastModified(), if (child.isDirectory) 0L else child.length(), icon)
                }

                val sortedList = list.sortedWith { left, right ->
                    if (left.type != right.type) {
                        if (left.type == FMEntryType.DIRECTORY || left.type == FMEntryType.DRIVE) -1 else 1
                    } else {
                        val cmp = when (sortBy) {
                            FMSortBy.DATE -> right.modifiedAt.compareTo(left.modifiedAt)
                            FMSortBy.SIZE -> right.size.compareTo(left.size)
                            FMSortBy.NAME -> left.title.compareTo(right.title, ignoreCase = true)
                        }
                        if (sortDesc) -cmp else cmp
                    }
                }

                withContext(Dispatchers.Main) {
                    allEntries = sortedList
                }
            }
        }
    }

    // Trigger initial load and reload on changes
    LaunchedEffect(currentDir, showingDriveRoot, sortBy, sortDesc, showHidden) {
        reloadCurrentLocation()
    }

    fun navigateUp(): Boolean {
        if (showingDriveRoot) return false
        val dir = currentDir ?: return false
        val rootLimit = if (activeContainer != null) File(activeContainer.rootDir, ".wine/drive_c") else null

        if (activeContainer != null && rootLimit != null && dir.absolutePath == rootLimit.absolutePath) {
            showingDriveRoot = true
            currentDir = null
            return true
        }

        val parentDir = dir.parentFile
        if (parentDir == null) return false

        if (rootLimit != null && !parentDir.absolutePath.startsWith(rootLimit.absolutePath)) {
            if (activeContainer != null) {
                showingDriveRoot = true
                currentDir = null
                return true
            }
            return false
        }

        currentDir = parentDir
        return true
    }

    BackHandler {
        if (!navigateUp()) onBack()
    }

    val filteredEntries = remember(allEntries, currentQuery) {
        val q = currentQuery.trim().lowercase(Locale.ENGLISH)
        if (q.isEmpty()) allEntries
        else allEntries.filter { it.title.lowercase(Locale.ENGLISH).contains(q) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (showingDriveRoot) stringResource(R.string.fm_drives_root) else (currentDir?.name ?: "Storage"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!showingDriveRoot) {
                            Text(
                                text = currentDir?.absolutePath ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (!navigateUp()) onBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showHidden = !showHidden
                        com.winlator.cmod.core.MmkvPreferences().edit().putBoolean("fmShowHidden", showHidden).apply()
                    }) {
                        Icon(if (showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = "Hidden")
                    }
                    IconButton(onClick = {
                        sortDesc = !sortDesc
                        com.winlator.cmod.core.MmkvPreferences().edit().putBoolean("fmSortDesc", sortDesc).apply()
                    }) {
                        Icon(if (sortDesc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, contentDescription = "Sort Desc")
                    }
                    IconButton(onClick = {
                        gridMode = !gridMode
                        com.winlator.cmod.core.MmkvPreferences().edit().putBoolean("fmGridView", gridMode).apply()
                    }) {
                        Icon(if (gridMode) Icons.Filled.List else Icons.Filled.GridOn, contentDescription = "Toggle Layout")
                    }
                    var showSortMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort By")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fm_sort_by_name)) },
                            onClick = { sortBy = FMSortBy.NAME; showSortMenu = false; reloadCurrentLocation() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fm_sort_by_date)) },
                            onClick = { sortBy = FMSortBy.DATE; showSortMenu = false; reloadCurrentLocation() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fm_sort_by_size)) },
                            onClick = { sortBy = FMSortBy.SIZE; showSortMenu = false; reloadCurrentLocation() }
                        )
                    }
                    if (!showingDriveRoot) {
                        var showActionMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showActionMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More Actions")
                        }
                        DropdownMenu(expanded = showActionMenu, onDismissRequest = { showActionMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fm_new_folder_title)) },
                                onClick = { showNewFolderDialog = true; showActionMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fm_new_file_title)) },
                                onClick = { showNewFileDialog = true; showActionMenu = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (clipboardFile != null && !showingDriveRoot) {
                FloatingActionButton(
                    onClick = {
                        val source = clipboardFile!!
                        val current = currentDir ?: Environment.getExternalStorageDirectory()
                        scope.launch(Dispatchers.IO) {
                            var dest = File(current, source.name)
                            if (dest.exists()) {
                                val base = source.nameWithoutExtension
                                val ext = source.extension
                                var index = 1
                                do {
                                    dest = File(current, "$base ($index).$ext")
                                    index++
                                } while (dest.exists())
                            }
                            isOpRunning = true; opProgress = 0f; opLabel = if (clipboardMove) "Перемещение..." else "Копирование..."
                            val success = if (clipboardMove) {
                                source.renameTo(dest)
                            } else {
                                com.winlator.cmod.core.FileUtils.copyWithProgress(source, dest) { c, t -> opProgress = if (t>0) c.toFloat()/t else 0f }
                            }
                            isOpRunning = false
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    AppUtils.showToast(ctx, if (clipboardMove) R.string.fm_paste_success_move else R.string.fm_paste_success_copy)
                                    clipboardFile = null
                                    clipboardMove = false
                                    reloadCurrentLocation()
                                } else {
                                    AppUtils.showToast(ctx, R.string.fm_paste_fail)
                                }
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "Paste Here")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Search Bar
            OutlinedTextField(
                value = currentQuery,
                onValueChange = { currentQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.fm_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            if (isOpRunning) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(opLabel, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { opProgress }, modifier = Modifier.fillMaxWidth())
                }
            }
            // Current location breadcrumb
            if (!showingDriveRoot) {
                val rootLimit = if (activeContainer != null) File(activeContainer.rootDir, ".wine/drive_c") else Environment.getExternalStorageDirectory()
                val currentPath = currentDir ?: rootLimit
                val relPath = currentPath.absolutePath.removePrefix(rootLimit.absolutePath).trim('/')
                val parts = if (relPath.isEmpty()) emptyList() else relPath.split('/')
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { currentDir = rootLimit }) {
                        Text(if (activeContainer != null) "drive_c" else "Storage", fontWeight = FontWeight.Bold)
                    }
                    parts.forEachIndexed { i, part ->
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = {
                            val subPath = parts.subList(0, i + 1).joinToString("/")
                            currentDir = File(rootLimit, subPath)
                        }) {
                            Text(part)
                        }
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.no_items_to_display), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!showHidden) TextButton(onClick = { showHidden = true }) { Text("Показать скрытые") }
                    }
                }
            } else {
                if (gridMode) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        items(filteredEntries) { entry ->
                            GridEntryTile(
                                entry = entry,
                                onClick = {
                                    if (entry.type == FMEntryType.DRIVE || entry.type == FMEntryType.DIRECTORY) {
                                        showingDriveRoot = false
                                        currentDir = entry.file
                                    } else {
                                        activeContextEntry = entry
                                    }
                                },
                                onLongClick = {
                                    activeContextEntry = entry
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        items(filteredEntries) { entry ->
                            ListEntryRow(
                                entry = entry,
                                onClick = {
                                    if (entry.type == FMEntryType.DRIVE || entry.type == FMEntryType.DIRECTORY) {
                                        showingDriveRoot = false
                                        currentDir = entry.file
                                    } else {
                                        activeContextEntry = entry
                                    }
                                },
                                onLongClick = {
                                    activeContextEntry = entry
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Context Action Dialog
    if (activeContextEntry != null) {
        val entry = activeContextEntry!!
        val options = mutableListOf<String>()
        options.add(stringResource(R.string.fm_ctx_open))
        if (entry.type == FMEntryType.FILE) {
            options.add(stringResource(R.string.fm_ctx_share))
            if (entry.title.endsWith(".exe", ignoreCase = true)) {
                options.add(stringResource(R.string.fm_ctx_create_shortcut))
            }
        }
        if (entry.type != FMEntryType.DRIVE) {
            options.add(stringResource(R.string.fm_ctx_copy))
            options.add(stringResource(R.string.fm_ctx_move))
            options.add(stringResource(R.string.fm_ctx_rename))
            options.add(stringResource(R.string.fm_ctx_delete))
        }

        AlertDialog(
            onDismissRequest = { activeContextEntry = null },
            title = { Text(entry.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { action ->
                        TextButton(
                            onClick = {
                                activeContextEntry = null
                                when (action) {
                                    ctx.getString(R.string.fm_ctx_open) -> {
                                        if (entry.type == FMEntryType.DRIVE || entry.type == FMEntryType.DIRECTORY) {
                                            showingDriveRoot = false
                                            currentDir = entry.file
                                        } else {
                                            // Handle executable or system open
                                            if (entry.title.endsWith(".exe", ignoreCase = true) || entry.title.endsWith(".msi", ignoreCase = true)) {
                                                // Run exe / msi
                                                if (containerId >= 0 && activeContainer != null) {
                                                    // Run directly in this container
                                                    runExecutable(ctx, entry.file, activeContainer)
                                                } else {
                                                    // Prompt to choose container
                                                    showShortcutContainerSelect = entry.file
                                                }
                                            } else {
                                                // Share / Open in default app
                                                try {
                                                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".tileprovider", entry.file)
                                                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, "application/octet-stream")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    ctx.startActivity(Intent.createChooser(openIntent, ctx.getString(R.string.fm_ctx_open)))
                                                } catch (e: Exception) {
                                                    AppUtils.showToast(ctx, "Failed to open file: ${e.message}")
                                                }
                                            }
                                        }
                                    }
                                    ctx.getString(R.string.fm_ctx_share) -> {
                                        try {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ctx, ctx.packageName + ".tileprovider", entry.file))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            ctx.startActivity(Intent.createChooser(shareIntent, ctx.getString(R.string.fm_share_chooser)))
                                        } catch (e: Exception) {
                                            AppUtils.showToast(ctx, "Failed to share: ${e.message}")
                                        }
                                    }
                                    ctx.getString(R.string.fm_ctx_copy) -> {
                                        clipboardFile = entry.file
                                        clipboardMove = false
                                        AppUtils.showToast(ctx, ctx.getString(R.string.fm_clipboard_copy, entry.title))
                                    }
                                    ctx.getString(R.string.fm_ctx_move) -> {
                                        clipboardFile = entry.file
                                        clipboardMove = true
                                        AppUtils.showToast(ctx, ctx.getString(R.string.fm_clipboard_move, entry.title))
                                    }
                                    ctx.getString(R.string.fm_ctx_rename) -> {
                                        showRenameDialog = entry.file
                                    }
                                    ctx.getString(R.string.fm_ctx_delete) -> {
                                        showDeleteDialog = entry.file
                                    }
                                    ctx.getString(R.string.fm_ctx_create_shortcut) -> {
                                        showShortcutContainerSelect = entry.file
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(action, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(stringResource(R.string.fm_new_folder_title)) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(stringResource(R.string.fm_new_folder_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNewFolderDialog = false
                    if (folderName.isNotEmpty()) {
                        val current = currentDir ?: Environment.getExternalStorageDirectory()
                        val newFolder = File(current, folderName.trim())
                        if (!newFolder.exists() && newFolder.mkdirs()) {
                            reloadCurrentLocation()
                        } else {
                            AppUtils.showToast(ctx, R.string.fm_new_folder_fail)
                        }
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // New Text File Dialog
    if (showNewFileDialog) {
        var fileName by remember { mutableStateOf("") }
        var fileContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text(stringResource(R.string.fm_new_file_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text(stringResource(R.string.fm_new_file_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fileContent,
                        onValueChange = { fileContent = it },
                        label = { Text(stringResource(R.string.fm_new_file_content_hint)) },
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showNewFileDialog = false
                    if (fileName.isNotEmpty()) {
                        val current = currentDir ?: Environment.getExternalStorageDirectory()
                        val newFile = File(current, fileName.trim())
                        try {
                            FileWriter(newFile).use { it.write(fileContent) }
                            reloadCurrentLocation()
                        } catch (e: Exception) {
                            AppUtils.showToast(ctx, ctx.getString(R.string.installation_error, e.message ?: ""))
                        }
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog != null) {
        val file = showRenameDialog!!
        var newName by remember { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text(stringResource(R.string.fm_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRenameDialog = null
                    if (newName.isNotEmpty() && newName != file.name) {
                        val renamed = File(file.parentFile, newName.trim())
                        if (!renamed.exists() && file.renameTo(renamed)) {
                            reloadCurrentLocation()
                        } else {
                            AppUtils.showToast(ctx, R.string.fm_rename_fail)
                        }
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete Dialog
    if (showDeleteDialog != null) {
        val file = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.fm_delete_title)) },
            text = { Text(file.name) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = null
                    scope.launch(Dispatchers.IO) {
                        val success = deleteRecursively(file)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                AppUtils.showToast(ctx, R.string.fm_delete_success)
                                reloadCurrentLocation()
                            } else {
                                AppUtils.showToast(ctx, R.string.fm_delete_fail)
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.fm_ctx_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Shortcut Container selection / Run selector Dialog
    if (showShortcutContainerSelect != null) {
        val exeFile = showShortcutContainerSelect!!
        val containers = remember { containerManager.containers }
        AlertDialog(
            onDismissRequest = { showShortcutContainerSelect = null },
            title = { Text(stringResource(R.string.fm_select_container)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (containers.isNullOrEmpty()) {
                        Text(stringResource(R.string.fm_no_containers), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        containers.forEach { container ->
                            TextButton(
                                onClick = {
                                    showShortcutContainerSelect = null
                                    // If we are just creating a shortcut
                                    if (activeContextEntry == null) {
                                        createShortcutForContainer(ctx, exeFile, container)
                                    } else {
                                        // Run it
                                        runExecutable(ctx, exeFile, container)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(container.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShortcutContainerSelect = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridEntryTile(
    entry: FMFileEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when (entry.type) {
                FMEntryType.DRIVE -> {
                    Icon(
                        Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                FMEntryType.DIRECTORY -> {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(40.dp)
                    )
                }
                FMEntryType.FILE -> {
                    if (entry.icon != null) {
                        Image(
                            bitmap = entry.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = entry.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListEntryRow(
    entry: FMFileEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when (entry.type) {
                FMEntryType.DRIVE -> {
                    Icon(
                        Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                FMEntryType.DIRECTORY -> {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(28.dp)
                    )
                }
                FMEntryType.FILE -> {
                    if (entry.icon != null) {
                        Image(
                            bitmap = entry.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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

private fun deleteRecursively(target: File): Boolean {
    if (target.isDirectory) {
        val children = target.listFiles()
        if (children != null) {
            for (child in children) {
                if (!deleteRecursively(child)) return false
            }
        }
    }
    return target.delete()
}

private fun copyRecursively(source: File, destination: File): Boolean {
    try {
        if (source.isDirectory) {
            if (!destination.exists() && !destination.mkdirs()) return false
            val children = source.listFiles()
            if (children != null) {
                for (child in children) {
                    if (!copyRecursively(child, File(destination, child.name))) return false
                }
            }
            return true
        } else {
            FileInputStream(source).use { inStream ->
                FileOutputStream(destination).use { outStream ->
                    val buffer = ByteArray(1024 * 4)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        outStream.write(buffer, 0, read)
                    }
                }
            }
            return true
        }
    } catch (_: Exception) {
        return false
    }
}

private fun runExecutable(ctx: Context, file: File, container: Container) {
    val intent = Intent(ctx, com.winlator.cmod.XServerDisplayActivity::class.java).apply {
        putExtra("container_id", container.id)
        val absolutePath = file.absolutePath
        var driveLetter = "D:"
        var relativePath = absolutePath

        val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
        if (absolutePath.startsWith(externalStoragePath)) {
            relativePath = absolutePath.substring(externalStoragePath.length).trim('/')
        } else if (absolutePath.contains("/.wine/drive_c/")) {
            driveLetter = "C:"
            relativePath = absolutePath.substring(absolutePath.indexOf("/.wine/drive_c/") + 15)
        } else if (absolutePath.contains("/imagefs/")) {
            driveLetter = "Z:"
            relativePath = absolutePath.substring(absolutePath.indexOf("/imagefs/") + 9)
        }
        putExtra("executablePath", "$driveLetter\\$relativePath")
    }
    ctx.startActivity(intent)
}

private fun createShortcutForContainer(ctx: Context, exeFile: File, container: Container) {
    try {
        val absolutePath = exeFile.absolutePath
        val fileName = exeFile.name
        val fileNameWithoutExt = exeFile.nameWithoutExtension
        val displayName = GameTitleParser.normalizeName(
            GameTitleParser.suggestName(exeFile)?.takeIf { it.isNotBlank() } ?: fileNameWithoutExt
        ).ifBlank { fileNameWithoutExt }

        var driveLetter = "D:"
        val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath

        if (absolutePath.startsWith(externalStoragePath)) {
            driveLetter = "D:"
        } else if (absolutePath.contains("/.wine/drive_c/")) {
            driveLetter = "C:"
        } else if (absolutePath.contains("/imagefs/")) {
            driveLetter = "Z:"
        }

        var pathWOutPrefix = absolutePath
        if (driveLetter == "D:") {
            pathWOutPrefix = pathWOutPrefix.removePrefix(externalStoragePath).trim('/')
            if (pathWOutPrefix.lowercase(Locale.ENGLISH).startsWith("download/")) {
                pathWOutPrefix = pathWOutPrefix.substring(9)
            }
        } else if (driveLetter == "Z:") {
            val idx = pathWOutPrefix.indexOf("/imagefs/")
            if (idx != -1) pathWOutPrefix = pathWOutPrefix.substring(idx + 9)
        } else if (driveLetter == "C:") {
            val idx = pathWOutPrefix.indexOf("/.wine/drive_c/")
            if (idx != -1) pathWOutPrefix = pathWOutPrefix.substring(idx + 15)
        }

        val execPath = pathWOutPrefix
        val lastSlash = pathWOutPrefix.lastIndexOf("/")
        val pathDir = if (lastSlash > 0) pathWOutPrefix.substring(0, lastSlash) else ""

        val randomNum = (Math.random() * 10000).toInt()
        val iconName = "${randomNum}_$displayName.0"

        try {
            val exeIcon = PEParser.extractIcon(exeFile)
            if (exeIcon != null) {
                val iconDir = container.getIconsDir(64)
                if (!iconDir.exists()) iconDir.mkdirs()
                val iconFile = File(iconDir, "$iconName.png")
                FileUtils.saveBitmapToFile(exeIcon, iconFile)
            }
        } catch (e: Exception) {
            Log.e("FileManager", "Error extracting icon", e)
        }

        val shortcutDesktop =
            "[Desktop Entry]\n" +
            "Name=$displayName\n" +
            "Exec=env WINEPREFIX=\"/data/user/0/${MainActivity.PACKAGE_NAME}/files/imagefs/home/xuser/.wine/dosdevices/z:/home/xuser/.wine\" wine $driveLetter/$execPath\n" +
            "Type=Application\n" +
            "StartupNotify=true\n" +
            "Path=/data/user/0/${MainActivity.PACKAGE_NAME}/files/imagefs/home/xuser/.wine/dosdevices/${driveLetter.lowercase(Locale.ENGLISH)}/$pathDir\n" +
            "Icon=$iconName\n" +
            "StartupWMClass=${fileName.lowercase(Locale.ENGLISH)}"

        val desktopFile = File(container.getDesktopDir(), "$displayName.desktop")
        FileWriter(desktopFile).use { it.write(shortcutDesktop) }

        AppUtils.showToast(ctx, ctx.getString(R.string.fm_shortcut_created_success, container.name))
    } catch (e: Exception) {
        Log.e("FileManager", "Error creating shortcut", e)
        AppUtils.showToast(ctx, ctx.getString(R.string.fm_shortcut_create_fail))
    }
}
