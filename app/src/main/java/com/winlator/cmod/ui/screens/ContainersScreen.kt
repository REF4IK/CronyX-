package com.winlator.cmod.ui.screens

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.winlator.cmod.ui.components.GlassDivider
import com.winlator.cmod.ui.components.GlassRoundIconButton
import com.winlator.cmod.ui.components.GlassSurface
import androidx.compose.foundation.shape.CircleShape
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassDescColor
import com.winlator.cmod.ui.components.glassDividerColor
import com.winlator.cmod.ui.components.glassTitleColor
import android.app.Activity
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import com.winlator.cmod.FileManagerActivity
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.XrActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.xenvironment.ImageFs
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainersScreen(
    containerManager: ContainerManager?,
    refreshKey: Int = 0,
    onCreateContainer: () -> Unit = {},
    onEditContainer: (Int) -> Unit = {},
    onOpenFileBrowser: (Int) -> Unit = {},
) {
    val ctx = LocalContext.current
    val manager = containerManager ?: remember(ctx) { ContainerManager(ctx) }
    var containers by remember { mutableStateOf(manager.containers?.toList() ?: emptyList()) }
    var showPreloader by remember { mutableStateOf(false) }
    var preloaderText by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmTitle by remember { mutableStateOf("") }
    var showStorageInfoContainer by remember { mutableStateOf<Container?>(null) }

    fun reload() {
        manager.reload()
        containers = manager.containers?.toList() ?: emptyList()
    }

    LaunchedEffect(refreshKey) { reload() }

    Scaffold(
        containerColor = Color.Transparent,
        // Внешний Scaffold (WinlatorApp) уже даёт отступы от системных баров —
        // здесь убираем, чтобы не было двойного отступа снизу и «подскока» списка
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {},
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (ImageFs.find(ctx).isValid()) onCreateContainer() },
                // FAB без shape берёт shapes.large (26dp) — был квадратом
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add), tint = MaterialTheme.colorScheme.onPrimary) }
        },
    ) { padding ->
        if (containers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.containers), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(containers, key = { it.id }) { container ->
                    ContainerCard(
                        container = container,
                        onRun = { runContainer(ctx, container) },
                        onEdit = { onEditContainer(container.id) },
                        onDuplicate = {
                            confirmTitle = ctx.getString(R.string.do_you_want_to_duplicate_this_container)
                            confirmAction = {
                                showPreloader = true; preloaderText = ctx.getString(R.string.duplicating_container)
                                manager.duplicateContainerAsync(container) {
                                    (ctx as? Activity)?.runOnUiThread { showPreloader = false; reload() }
                                }
                            }
                        },
                        onRemove = {
                            confirmTitle = ctx.getString(R.string.do_you_want_to_remove_this_container)
                            confirmAction = {
                                showPreloader = true; preloaderText = ctx.getString(R.string.removing_container)
                                for (shortcut in manager.loadShortcuts()) {
                                    if (shortcut.container == container) {
                                        com.winlator.cmod.ShortcutsFragment.disableShortcutOnScreen(ctx, shortcut)
                                    }
                                }
                                manager.removeContainerAsync(container) {
                                    (ctx as? Activity)?.runOnUiThread { showPreloader = false; reload() }
                                }
                            }
                        },
                        onStorageInfo = { showStorageInfoContainer = container },
                        onFileManager = {
                            if (container.rootDir == null || !container.rootDir.isDirectory) {
                                AppUtils.showToast(ctx, R.string.container_file_manager_unavailable)
                            } else {
                                onOpenFileBrowser(container.id)
                            }
                        },
                        onReconfigure = {
                            confirmTitle = ctx.getString(R.string.do_you_want_to_reconfigure_wine)
                            confirmAction = { File(container.rootDir, ".wine/.update-timestamp").delete() }
                        },
                        onExport = {
                            showPreloader = true; preloaderText = ctx.getString(R.string.exporting_container)
                            manager.exportContainer(container) {
                                (ctx as? Activity)?.runOnUiThread {
                                    showPreloader = false
                                    val backupDir = File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                        "Winlator/Backups/Containers"
                                    )
                                    AppUtils.showToast(ctx, "Container exported successfully to ${backupDir.path}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmAction != null) {
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(confirmTitle) },
            confirmButton = { TextButton(onClick = { val a = confirmAction; confirmAction = null; a?.invoke() }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showPreloader) {
        AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text(preloaderText) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        )
    }

    if (showStorageInfoContainer != null) {
        StorageInfoDialogCompose(
            context = ctx,
            container = showStorageInfoContainer!!,
            onDismiss = { showStorageInfoContainer = null }
        )
    }
}

private fun runContainer(ctx: android.content.Context, container: Container) {
    if (!XrActivity.isEnabled(ctx)) {
        val intent = Intent(ctx, XServerDisplayActivity::class.java)
        intent.putExtra("container_id", container.id)
        ctx.startActivity(intent)
    } else {
        XrActivity.openIntent(ctx as Activity, container.id, null)
    }
}

@Composable
fun ContainerCard(
    container: Container,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onStorageInfo: () -> Unit,
    onFileManager: () -> Unit,
    onReconfigure: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        // Обложка вместо картинки игры: иконка контейнера на акцентном
        // градиенте. Нижние блоки повторяют референс: title, meta, разделитель,
        // ряд круглых иконок-действий.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(glassAccent().copy(alpha = 0.20f), Color.Transparent),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.icon_container),
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = glassAccent(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = GlassTokens.RowPaddingH, end = GlassTokens.RowPaddingH, bottom = 8.dp),
        ) {
            Text(
                text = container.name,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = glassTitleColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val wv = container.wineVersion
            if (!wv.isNullOrEmpty()) {
                Text(
                    text = wv,
                    fontSize = GlassTokens.DescSize,
                    color = glassDescColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        GlassDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GlassTokens.RowPaddingH, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassRoundIconButton(
                icon = Icons.Filled.PlayArrow,
                description = stringResource(R.string.run),
                onClick = onRun,
                size = 44.dp,
            )
            Spacer(Modifier.width(10.dp))

            // Анкор для меню — кнопка ⋮; меню выпадает справа, прижато к правому краю экрана
            Box {
                GlassRoundIconButton(
                    icon = Icons.Filled.MoreVert,
                    description = stringResource(R.string.options),
                    onClick = { menuOpen = true },
                    size = 44.dp,
                )

                if (menuOpen) {
                    val density = LocalDensity.current
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = with(density) { IntOffset(0, 52.dp.roundToPx()) },
                        properties = PopupProperties(focusable = true),
                        onDismissRequest = { menuOpen = false }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(GlassTokens.InnerRadius),
                            color = glassSheetFill(),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .border(
                                    width = 1.dp,
                                    color = glassDividerColor(),
                                    shape = RoundedCornerShape(GlassTokens.InnerRadius)
                                )
                        ) {
                            Column(Modifier.width(IntrinsicSize.Max)) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                                    text = { Text(stringResource(R.string.container_file_manager)) },
                                    onClick = { menuOpen = false; onFileManager() }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary) },
                                    text = { Text(stringResource(R.string.edit)) },
                                    onClick = { menuOpen = false; onEdit() }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null, tint = MaterialTheme.colorScheme.primary) },
                                    text = { Text(stringResource(R.string.duplicate)) },
                                    onClick = { menuOpen = false; onDuplicate() }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    text = { Text(stringResource(R.string.remove)) },
                                    onClick = { menuOpen = false; onRemove() }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
                                    text = { Text(stringResource(R.string.storage_info)) },
                                    onClick = { menuOpen = false; onStorageInfo() }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary) },
                                    text = { Text(stringResource(R.string.reconfigure)) },
                                    onClick = { menuOpen = false; onReconfigure() }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary) },
                                    text = { Text(stringResource(R.string.export_container)) },
                                    onClick = { menuOpen = false; onExport() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageInfoDialogCompose(
    context: android.content.Context,
    container: Container,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var driveCSize by remember { mutableLongStateOf(0L) }
    var cacheSize by remember { mutableLongStateOf(0L) }
    var totalSize by remember { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }

    val internalStorageSize = remember { com.winlator.cmod.core.FileUtils.getInternalStorageSize() }
    val rootDir = container.rootDir
    val driveCDir = remember(rootDir) { File(rootDir, ".wine/drive_c") }
    val cacheDir = remember(rootDir) { File(rootDir, ".cache") }

    fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var size = 0L
        val stack = java.util.Stack<File>()
        stack.push(dir)
        while (!stack.isEmpty()) {
            val current = stack.pop()
            val files = current.listFiles()
            if (files != null) {
                for (f in files) {
                    if (f.isDirectory) {
                        stack.push(f)
                    } else {
                        size += f.length()
                    }
                }
            }
        }
        return size
    }

    LaunchedEffect(container) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val dCSize = calculateDirectorySize(driveCDir)
            val cSize = calculateDirectorySize(cacheDir)
            withContext(Dispatchers.Main) {
                driveCSize = dCSize
                cacheSize = cSize
                totalSize = dCSize + cSize
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.icon_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(stringResource(R.string.storage_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                } else {
                    val progress = if (internalStorageSize > 0) totalSize.toFloat() / internalStorageSize else 0f
                    val percentage = (progress * 100).toInt()

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 8.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = stringResource(R.string.estimated_used_space),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailRow(label = "Drive C:", value = com.winlator.cmod.core.StringUtils.formatBytes(driveCSize))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        DetailRow(label = "Cache:", value = com.winlator.cmod.core.StringUtils.formatBytes(cacheSize))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        DetailRow(label = "Total Size:", value = com.winlator.cmod.core.StringUtils.formatBytes(totalSize))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        withContext(Dispatchers.IO) {
                            com.winlator.cmod.core.FileUtils.clear(cacheDir)
                            container.putExtra("desktopTheme", null)
                            container.saveData()
                            val dCSize = calculateDirectorySize(driveCDir)
                            withContext(Dispatchers.Main) {
                                driveCSize = dCSize
                                cacheSize = 0L
                                totalSize = dCSize
                                isLoading = false
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.clear_cache))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
