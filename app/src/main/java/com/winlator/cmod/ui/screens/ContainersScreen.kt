package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.glassTitleColor

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.BorderStroke
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
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.contentdialog.DXVKConfigDialog
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog
import com.winlator.cmod.contents.AdrenotoolsManager
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.ui.components.GlassSolidPill
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
    // Профили Wine для чипа Wine и установленные adrenotools-драйверы (Turnip).
    val contentsManager = remember(ctx) { ContentsManager(ctx) }
    val adrenotoolsManager = remember(ctx) { AdrenotoolsManager(ctx) }
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
                // Поднять над плавающей нижней панелью.
                modifier = Modifier.padding(bottom = 82.dp),
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add), tint = MaterialTheme.colorScheme.onPrimary) }
        },
    ) { padding ->
        if (containers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.containers), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                // Широкая карточка на всю ширину — по одной в ряд.
                columns = GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 176.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(containers, key = { it.id }) { container ->
                    ContainerCard(
                        container = container,
                        contentsManager = contentsManager,
                        adrenotoolsManager = adrenotoolsManager,
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

/** Данные чипов и подзаголовка карточки контейнера (считаются в фоне). */
private data class ContainerCardInfo(
    val sizeText: String = "",
    val wineText: String = "…",
    val dxvkText: String = "…",
    val turnipText: String = "…",
    val emulatorLabel: String = "Box64",
    val emulatorText: String = "…",
)

/** Рекурсивный размер каталога (обход стеком). */
private fun calculateDirectorySize(dir: File): Long {
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

@Composable
fun ContainerCard(
    container: Container,
    contentsManager: ContentsManager,
    adrenotoolsManager: AdrenotoolsManager,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onStorageInfo: () -> Unit,
    onFileManager: () -> Unit,
    onReconfigure: () -> Unit,
    onExport: () -> Unit,
) {
    val ctx = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var info by remember(container.id) { mutableStateOf(ContainerCardInfo()) }

    // Версии/размер читаются с диска — считаем в фоне один раз на контейнер.
    LaunchedEffect(container.id, container) {
        info = withContext(Dispatchers.IO) {
            val wineInfo = WineInfo.fromIdentifier(ctx, contentsManager, container.wineVersion ?: "")
            val dxw = (container.dxWrapper ?: "").lowercase()
            val dxConfig = DXVKConfigDialog.parseConfig(
                container.dxWrapperConfig ?: Container.DEFAULT_DXWRAPPERCONFIG
            )
            val dxvkText = when {
                dxw.contains("vkd3d") -> dxConfig.get("vkd3dVersion").ifBlank { "VKD3D" }
                dxw.contains("dxvk") -> dxConfig.get("version").ifBlank { "DXVK" }
                else -> container.dxWrapper ?: "-"
            }
            // Графический драйвер: "System" (встроенный) или id adrenotools-драйвера,
            // точная версия лежит в его meta.json.
            val driverId = GraphicsDriverConfigDialog.getVersion(container.graphicsDriverConfig ?: "")
            val turnipText = if (driverId.isNullOrEmpty() || driverId == DefaultVersion.WRAPPER) {
                DefaultVersion.WRAPPER
            } else {
                adrenotoolsManager.getDriverVersion(driverId).ifBlank { driverId }
            }
            // Дефолт этого форка — FEXCore: вместо Box64 показываем FEX.
            val isFex = (container.emulator ?: "").lowercase().contains("fex")
            ContainerCardInfo(
                sizeText = container.rootDir?.let { StringUtils.formatBytes(calculateDirectorySize(it)) } ?: "",
                wineText = "${wineInfo.version} ${wineInfo.arch ?: ""}".trim(),
                dxvkText = dxvkText,
                turnipText = turnipText,
                emulatorLabel = if (isFex) "FEX" else "Box64",
                emulatorText = if (isFex) {
                    container.getFEXCoreVersion()?.ifBlank { DefaultVersion.FEXCORE } ?: DefaultVersion.FEXCORE
                } else {
                    container.box64Version?.ifBlank { "-" } ?: "-"
                },
            )
        }
    }

    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Шапка: аватар, имя + размер, кебаб.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(glassAccent().copy(alpha = 0.18f), RoundedCornerShape(GlassTokens.InnerRadius)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.icon_container),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = glassAccent(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = container.name,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = glassTitleColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (info.sizeText.isNotEmpty()) {
                        Text(text = info.sizeText, fontSize = 13.sp, color = glassDescColor())
                    }
                }
                // Анкор для меню — кнопка ⋮; меню выпадает справа, прижато к правому краю экрана
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.options),
                            tint = glassTitleColor(),
                        )
                    }

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
                                color = glassSheetFill(), contentColor = glassTitleColor(),
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

            Spacer(Modifier.height(14.dp))

            // Чипы версий 2×2: Wine / DXVK / Turnip / Box64 (или FEX).
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ContainerInfoChip("Wine", info.wineText, Modifier.weight(1f))
                ContainerInfoChip("DXVK", info.dxvkText, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ContainerInfoChip("Turnip", info.turnipText, Modifier.weight(1f))
                ContainerInfoChip(info.emulatorLabel, info.emulatorText, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))

            // Запуск + настройки контейнера (шестерёнка открывает редактор).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassSolidPill(onClick = onRun, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.run),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                GlassRoundIconButton(
                    icon = Icons.Filled.Settings,
                    description = stringResource(R.string.edit),
                    onClick = onEdit,
                    size = 46.dp,
                )
            }
        }
    }
}

/** Чип с версией: подпись сверху, значение снизу. */
@Composable
private fun ContainerInfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(GlassTokens.InnerRadius),
        color = glassCardFill(),
        contentColor = glassTitleColor(),
        border = BorderStroke(1.dp, glassDividerColor()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = glassDescColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
