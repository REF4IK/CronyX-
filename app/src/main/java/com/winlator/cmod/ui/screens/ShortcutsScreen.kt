package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSurface

import com.winlator.cmod.ui.components.glassBadgeFill
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassIsDark
import com.winlator.cmod.ui.components.GlassSolidPill
import androidx.compose.foundation.shape.CircleShape
import com.winlator.cmod.ui.components.GlassChip
import com.winlator.cmod.ui.components.GlassSurface
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassDescColor
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Environment
import android.widget.VideoView
import android.widget.MediaController
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import java.util.concurrent.ConcurrentHashMap
import coil.request.ImageRequest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import com.winlator.cmod.core.DohOkHttp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.GameTitleMatcher
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.core.ShortcutCoverFetcher
import com.winlator.cmod.core.SteamImageCache
import com.winlator.cmod.core.gameconfig.GameConfigManager
import com.winlator.cmod.core.gameconfig.CloudConfigRepoV2
import com.winlator.cmod.win32.PEParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter

private val STEAM_GAME_INFO_CACHE = ConcurrentHashMap<Int, SteamGameInfo>()
private val STEAM_NAME_TO_APP_ID_CACHE = ConcurrentHashMap<String, Int>()

@Composable
private fun rememberCachedSteamImage(url: String, fallbackUrl: String? = null): Any? {
    val ctx = LocalContext.current
    var file by remember(url) { mutableStateOf(SteamImageCache.getCachedFile(ctx, url)) }

    LaunchedEffect(url, fallbackUrl) {
        android.util.Log.d("SteamImg", "rememberCached: url=$url cached=${file != null}")
        if (file == null) {
            android.util.Log.d("SteamImg", "Downloading: $url")
            val f = SteamImageCache.downloadIfNeeded(ctx, url)
            if (f != null) {
                android.util.Log.d("SteamImg", "Downloaded OK: ${f.absolutePath} size=${f.length()}")
                file = f
            } else if (fallbackUrl != null) {
                android.util.Log.d("SteamImg", "Trying fallback: $fallbackUrl")
                val f2 = SteamImageCache.downloadIfNeeded(ctx, fallbackUrl)
                if (f2 != null) {
                    android.util.Log.d("SteamImg", "Fallback OK: ${f2.absolutePath}")
                    file = f2
                } else {
                    android.util.Log.w("SteamImg", "Both failed for $url")
                }
            }
        }
    }

    return file
}

/**
 * Compose-версия ShortcutsFragment.
 * Список шорткатов в grid-режиме, FAB для импорта, диалог выбора контейнера.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(
    refreshKey: Int = 0,
    isGridView: Boolean = true,
    onToggleView: () -> Unit = {},
    onImportGame: (Container) -> Unit = {},
    onOpenShortcutSettings: (Shortcut) -> Unit = {},
    onBack: () -> Unit = {},
    onShowSteamInfo: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val spanCount = if (isLandscape) 4 else 2
    var refreshKeyInternal by remember { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKeyInternal++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val manager = remember(refreshKey, refreshKeyInternal) { ContainerManager(ctx) }
    var showContainerPicker by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf(false) }
    var shortcutForClone by remember { mutableStateOf<Shortcut?>(null) }
    var showPublishDialog by remember { mutableStateOf<Shortcut?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var showCommunityConfigs by remember { mutableStateOf(false) }
    var communityConfigsShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var showPropertiesFor by remember { mutableStateOf<Shortcut?>(null) }
    var shortcutForSteamInfo by remember { mutableStateOf<Shortcut?>(null) }

    LaunchedEffect(shortcutForSteamInfo) {
        onShowSteamInfo(shortcutForSteamInfo != null)
    }

    // Собираем все шорткаты из всех контейнеров
    val shortcuts = remember(refreshKey, refreshKeyInternal) {
        manager.loadShortcuts()
    }

    BackHandler(enabled = showContainerPicker || shortcutForSteamInfo != null || showCommunityConfigs) {
        when {
            showCommunityConfigs -> showCommunityConfigs = false
            shortcutForSteamInfo != null -> shortcutForSteamInfo = null
            showContainerPicker -> showContainerPicker = false
        }
    }

    if (showCommunityConfigs) {
        CommunityConfigsScreen(onBack = { showCommunityConfigs = false; communityConfigsShortcut?.let { shortcutForSteamInfo = it }; communityConfigsShortcut = null }, contextShortcut = communityConfigsShortcut)
    } else {
Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
        if (shortcuts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VideogameAsset,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_shortcuts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (isGridView) {
            // Grid view — большие карточки
            LazyVerticalGrid(
                columns = GridCells.Fixed(spanCount),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 84.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shortcuts, key = { it.hashCode() }) { shortcut ->
                    ShortcutLargeCard(
                        shortcut = shortcut,
                        modifier = Modifier.animateItem(),
                        onClick = { runShortcut(ctx, shortcut) },
                        onLongClick = { shortcutForSteamInfo = shortcut },
                    )
                }
            }
        } else {
            // List view — компактные карточки
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shortcuts) { shortcut ->
                    ShortcutCard(
                        shortcut = shortcut,
                        modifier = Modifier.animateItem(),
                        onClick = { runShortcut(ctx, shortcut) },
                        onLongClick = { shortcutForSteamInfo = shortcut },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                pendingImport = true
                showContainerPicker = true
            },
            // FAB без shape берёт shapes.large (26dp) — был квадратом
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
    } // Column wrapper end

    // Диалог выбора контейнера
    if (showContainerPicker) {
        ContainerPickerDialog(
            containers = manager.containers,
            onDismiss = { showContainerPicker = false; pendingImport = false },
            onPick = { container ->
                showContainerPicker = false
                if (pendingImport) {
                    pendingImport = false
                    onImportGame(container)
                } else {
                    pendingImport = false
                    AppUtils.showToast(ctx, "Add shortcut to ${container.name}")
                }
            },
        )
    }



    // Диалог выбора контейнера для клонирования
    shortcutForClone?.let { s ->
        ContainerPickerDialog(
            containers = manager.containers,
            onDismiss = { shortcutForClone = null },
            onPick = { c ->
                shortcutForClone = null
                val ok = s.cloneToContainer(c)
                if (ok) {
                    AppUtils.showToast(ctx, "Cloned to ${c.name}")
                    refreshKeyInternal++
                } else {
                    AppUtils.showToast(ctx, "Clone failed")
                }
            },
        )
    }

    // Диалог Properties
    showPropertiesFor?.let { s ->
        val prefs = MmkvPreferences("playtime_stats")
        val playtimeKey = "${s.name}_playtime"
        val playCountKey = "${s.name}_play_count"
        val totalPlaytime = prefs.getLong(playtimeKey, 0)
        val playCount = prefs.getInt(playCountKey, 0)
        val seconds = (totalPlaytime / 1000) % 60
        val minutes = (totalPlaytime / (1000 * 60)) % 60
        val hours = (totalPlaytime / (1000 * 60 * 60)) % 24
        val days = totalPlaytime / (1000 * 60 * 60 * 24)
        val playtime = "${days}d ${String.format("%02d", hours)}h ${String.format("%02d", minutes)}m ${String.format("%02d", seconds)}s"
        val gameInfo = rememberGameInfo(s)
        val exePath = remember(s) { s.resolveExeFile()?.absolutePath }

        AlertDialog(
            onDismissRequest = { showPropertiesFor = null },
            title = { Text("Properties", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val infoRows = listOfNotNull(
                        gameInfo?.version?.let { "Version" to it },
                        gameInfo?.productName?.let { "Product" to it },
                        gameInfo?.company?.let { "Developer" to it },
                        gameInfo?.description?.let { "Description" to it },
                        gameInfo?.copyright?.let { "Copyright" to it },
                        gameInfo?.originalFilename?.let { "File name" to it },
                        exePath?.let { "EXE path" to it },
                    )
                    infoRows.forEach { (label, value) ->
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("Container: ${s.container.name}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Number of times played: $playCount", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Playtime: $playtime", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    GlassSolidPill(
                        onClick = {
                            prefs.edit().remove(playtimeKey).remove(playCountKey).apply()
                            AppUtils.showToast(ctx, "Properties reset")
                            showPropertiesFor = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reset Properties") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPropertiesFor = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Диалог Steam Info
    shortcutForSteamInfo?.let { s ->
        SteamInfoDialog(
            shortcut = s,
            onDismiss = { shortcutForSteamInfo = null },
            onOpenSettings = {
                onOpenShortcutSettings(s)
            },
            onCloneClick = {
                shortcutForSteamInfo = null
                shortcutForClone = s
            },
            onPropertiesClick = {
                showPropertiesFor = s
            },
            onRefresh = {
                refreshKeyInternal++
            },
            onSearchConfigs = { shortcut ->
                communityConfigsShortcut = shortcut
                showCommunityConfigs = true
                shortcutForSteamInfo = null
            }
        )
    }

    // Publish dialog
    showPublishDialog?.let { s ->
        val publishScope = rememberCoroutineScope()
        var pubDesc by remember(s) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPublishDialog = null },
            title = { Text(stringResource(R.string.publish_config)) },
            text = {
                Column {
                    Text(s.name, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pubDesc,
                        onValueChange = { pubDesc = it },
                        label = { Text(stringResource(R.string.config_description_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,

                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isPublishing) {
                            isPublishing = true
                            val config = GameConfigManager.buildGameConfig(s.container, s, pubDesc)
                            val bundler = com.winlator.cmod.core.gameconfig.GameConfigBundler(ctx)
                            publishScope.launch(Dispatchers.IO) {
                                try {
                                    val result = bundler.buildBundle(config.containerSettings, ctx.cacheDir)
                                    com.winlator.cmod.core.gameconfig.BundleRepoClient.uploadBundle(
                                        result.zipFile, config.toJson().toString(), config.gameName, pubDesc,
                                        config.device, config.gpu,
                                        com.winlator.cmod.community.AccountManager.session(ctx),
                                        object : com.winlator.cmod.core.gameconfig.BundleRepoClient.BundleUploadCallback {
                                            override fun onComplete(success: Boolean, sha: String, bundleUrl: String, error: String?) {
                                                result.zipFile.delete()
                                                isPublishing = false
                                                showPublishDialog = null
                                                AppUtils.showToast(ctx, if (success) ctx.getString(R.string.bundle_published, sha) else "$error")
                                            }
                                        })
                                } catch (e: Exception) {
                                    isPublishing = false
                                    AppUtils.showToast(ctx, "Ошибка: ${e.message}")
                                }
                            }
                        }
                    },
                    enabled = !isPublishing
                ) { Text(if (isPublishing) "..." else stringResource(R.string.publish)) }
            },
            dismissButton = {
                TextButton(onClick = { showPublishDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    } // else block for showCommunityConfigs
}

// ---- Вспомогательные функции для popup-меню ----

private fun addShortcutToHomeScreen(ctx: android.content.Context, shortcut: Shortcut) {
    val shortcutManager = ctx.getSystemService(ShortcutManager::class.java)
    if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
        val icon = shortcut.displayIcon ?: BitmapFactory.decodeResource(ctx.resources, R.drawable.icon_shortcut)
        val intent = Intent(ctx, XServerDisplayActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.putExtra("container_id", shortcut.container.id)
        intent.putExtra("shortcut_path", shortcut.file?.absolutePath ?: "")

        val info = ShortcutInfo.Builder(ctx, shortcut.getExtra("uuid"))
            .setShortLabel(shortcut.name)
            .setLongLabel(shortcut.name)
            .setIcon(Icon.createWithBitmap(icon))
            .setIntent(intent)
            .build()
        shortcutManager.requestPinShortcut(info, null)
    }
}

private fun disableShortcutOnScreen(ctx: android.content.Context, shortcut: Shortcut) {
    try {
        val shortcutManager = ctx.getSystemService(ShortcutManager::class.java)
        shortcutManager?.disableShortcuts(
            listOf(shortcut.getExtra("uuid")),
            ctx.getString(R.string.shortcut_not_available)
        )
    } catch (_: Exception) {}
}

private fun exportShortcutToFrontend(ctx: android.content.Context, shortcut: Shortcut) {
    try {
        val frontendDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Winlator/Frontend")
        if (!frontendDir.exists()) frontendDir.mkdirs()

        // FRONTEND_INSTRUCTIONS.txt
        val instrFile = File(frontendDir, "FRONTEND_INSTRUCTIONS.txt")
        if (!instrFile.exists()) {
            FileWriter(instrFile).use { w ->
                w.write("Instructions for adding Winlator shortcuts to Frontends (WIP):\n\n")
                w.write("Daijisho:\n1. Add Winlator as a custom platform\n2. Point to $frontendDir\n3. Set emulation profile\n\n")
                w.write("Beacon:\n1. Scan $frontendDir\n2. The launch command is already configured\n")
            }
        }
        // metadata.pegasus.txt
        val metaFile = File(frontendDir, "metadata.pegasus.txt")
        FileWriter(metaFile).use { w ->
            w.write("collection: Windows\nshortname: windows\nextensions: desktop\n")
            w.write("launch: am start -n ${ctx.packageName}/.XServerDisplayActivity -e shortcut_path {file.path} --activity-clear-task --activity-clear-top --activity-no-history\n")
        }
        // Копируем файл шортката
        val exportFile = File(frontendDir, shortcut.file?.name ?: "${shortcut.name}.desktop")
        FileUtils.copy(shortcut.file, exportFile)
        AppUtils.showToast(ctx, "Exported to ${exportFile.absolutePath}")
    } catch (e: Exception) {
        AppUtils.showToast(ctx, "Export failed: ${e.message}")
    }
}

// ---- Карточка как в старом ShortcutsFragment (list_item.xml) — компактная ----
data class GameInfo(
    val version: String?,
    val company: String?,
    val productName: String?,
    val description: String?,
    val copyright: String?,
    val originalFilename: String?,
)

private val GAME_INFO_CACHE = ConcurrentHashMap<String, GameInfo>()

@Composable
private fun rememberGameInfo(shortcut: Shortcut): GameInfo? {
    val cacheKey = shortcut.file?.absolutePath ?: shortcut.name
    val cached = GAME_INFO_CACHE[cacheKey]
    var info by remember(shortcut) { mutableStateOf(cached) }
    LaunchedEffect(shortcut) {
        if (info == null) {
            info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val exe = shortcut.resolveExeFile()
                    val fi = if (exe != null) PEParser.getFileVersionInfo(exe) else null
                    val gameInfo = if (fi != null) GameInfo(
                        version = (fi.FileVersion ?: fi.ProductVersion)?.takeIf { it.isNotBlank() },
                        company = fi.CompanyName?.takeIf { it.isNotBlank() },
                        productName = fi.ProductName?.takeIf { it.isNotBlank() },
                        description = fi.FileDescription?.takeIf { it.isNotBlank() },
                        copyright = fi.LegalCopyright?.takeIf { it.isNotBlank() },
                        originalFilename = fi.OriginalFilename?.takeIf { it.isNotBlank() },
                    ) else null
                    android.util.Log.d("GameVersion", "shortcut=${shortcut.name} exe=${exe?.absolutePath} info=${fi != null} version=${gameInfo?.version}")
                    if (gameInfo != null) GAME_INFO_CACHE[cacheKey] = gameInfo
                    gameInfo
                } catch (e: Throwable) {
                    android.util.Log.d("GameVersion", "shortcut=${shortcut.name} error: ${e.message}")
                    null
                }
            }
        }
    }
    return info
}

@Composable
private fun rememberPlaytime(shortcut: Shortcut): String? {
    return remember(shortcut) {
        val prefs = MmkvPreferences("playtime_stats")
        val totalMs = prefs.getLong("${shortcut.name}_playtime", 0)
        val count = prefs.getInt("${shortcut.name}_play_count", 0)
        if (totalMs <= 0 && count <= 0) {
            null
        } else {
            val totalSeconds = totalMs / 1000
            val days = totalSeconds / 86400
            val hours = (totalSeconds % 86400) / 3600
            val minutes = (totalSeconds % 3600) / 60
            val timeStr = buildString {
                if (days > 0) append("${days}д ")
                if (hours > 0) append("${hours}ч ")
                append("${minutes}м")
            }
            if (count > 0) "$timeStr · $count запусков" else timeStr
        }
    }
}

@Composable
private fun GameInfoDialog(info: GameInfo, shortcut: Shortcut, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(shortcut.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val rows = listOfNotNull(
                    info.description?.let { "Описание" to it },
                    info.productName?.let { "Продукт" to it },
                    info.version?.let { "Версия" to it },
                    info.company?.let { "Разработчик" to it },
                    info.copyright?.let { "Авторские права" to it },
                    info.originalFilename?.let { "Имя файла" to it },
                )
                if (rows.isEmpty()) {
                    Text(
                        "Информация о версии не найдена",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    rows.forEach { (label, value) ->
                        Column {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.okay)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ShortcutCard(
    shortcut: Shortcut,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    var coverFile by remember(shortcut) { mutableStateOf<File?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(shortcut, refreshTrigger) {
        coverFile = ShortcutCoverFetcher.loadCoverArt(context, shortcut, landscape = false)
        if (coverFile == null) {
            ShortcutCoverFetcher.fetchCoverArt(context, shortcut, landscape = false) {
                refreshTrigger++
            }
        }
    }

    var icon by remember(shortcut) { mutableStateOf(shortcut.getDisplayIcon()?.asImageBitmap()) }

    LaunchedEffect(shortcut, coverFile, icon) {
        if (coverFile == null && icon == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val extracted = shortcut.extractAndSaveIcon()
                if (extracted != null) {
                    icon = extracted.asImageBitmap()
                }
            }
        }
    }
    val gameInfo = rememberGameInfo(shortcut)
    var showGameInfo by remember(shortcut) { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(GlassTokens.CardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
        border = glassCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Иконка 60x60 с тёмным фоном
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(glassBadgeFill()),
                contentAlignment = Alignment.Center,
            ) {
                if (coverFile != null) {
                    coil.compose.AsyncImage(
                        model = coverFile,
                        contentDescription = shortcut.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon!!,
                        contentDescription = shortcut.name,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        Icons.Filled.VideogameAsset,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            // Текст
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shortcut.container.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Версия Wine с карточки убрана: она показывалась и как значение
                // gameInfo.version (PEParser читает версию с бинаря Wine/Proton).
                gameInfo?.version?.takeIf { it != shortcut.container.wineVersion }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp).clickable { showGameInfo = true },
                    )
                }
                rememberPlaytime(shortcut)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // Кнопка ⋮
            IconButton(onClick = onLongClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
            }
        }
    }
    if (showGameInfo && gameInfo != null) {
        GameInfoDialog(info = gameInfo, shortcut = shortcut, onDismiss = { showGameInfo = false })
    }
}

// ---- Карточка как в старом ShortcutsFragment (grid_item.xml) — большая ----
@Composable
private fun ShortcutLargeCard(
    shortcut: Shortcut,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var coverFile by remember(shortcut) { mutableStateOf<File?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(shortcut, refreshTrigger) {
        coverFile = ShortcutCoverFetcher.loadCoverArt(context, shortcut, landscape = false)
        if (coverFile == null) {
            ShortcutCoverFetcher.fetchCoverArt(context, shortcut, landscape = false) {
                refreshTrigger++
            }
        }
    }

    var icon by remember(shortcut) { mutableStateOf(shortcut.getDisplayIcon()?.asImageBitmap()) }

    LaunchedEffect(shortcut, coverFile, icon) {
        if (coverFile == null && icon == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val extracted = shortcut.extractAndSaveIcon()
                if (extracted != null) {
                    icon = extracted.asImageBitmap()
                }
            }
        }
    }
    val gameInfo = rememberGameInfo(shortcut)
    var showGameInfo by remember(shortcut) { mutableStateOf(false) }
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        // Текстового блока на плитке нет: название, чип версии и строка контейнера
        // убирали у обложки ~71dp. Вся карточка — картинка, высота 222dp = 150dp
        // обложки + 71dp отданного ей места, поэтому размер карточки не изменился.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(222.dp)
                .background(Color(0xFF101015)),
            contentAlignment = Alignment.Center,
        ) {
            if (coverFile != null) {
                coil.compose.AsyncImage(
                    model = coverFile,
                    contentDescription = shortcut.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (icon != null) {
                androidx.compose.foundation.Image(
                    bitmap = icon!!,
                    contentDescription = shortcut.name,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    Icons.Filled.VideogameAsset,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = glassDescColor(),
                )
            }
            // ⋮ — круглая плашка поверх обложки справа сверху
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onLongClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Название игры и контейнер — НАД обложкой внизу, а не под ней: текст
            // не отнимает у картинки место, а затемнение снизу даёт читать на любом арте.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)),
                        ),
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 10.dp),
            ) {
                Text(
                    text = shortcut.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = shortcut.container.name,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Версия игры и число запусков. Версию фильтруем от строки Wine:
                // для Proton-контейнеров PEParser отдаёт FileVersion бинаря Wine.
                val gameVer = gameInfo?.version?.takeIf { it != shortcut.container.wineVersion }
                val launches = remember(shortcut) {
                    MmkvPreferences("playtime_stats").getInt("${shortcut.name}_play_count", 0)
                }
                Text(
                    text = if (gameVer != null) "$gameVer · ${stringResource(R.string.launches_count, launches)}"
                    else stringResource(R.string.launches_count, launches),
                    color = Color.White.copy(alpha = 0.66f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (showGameInfo && gameInfo != null) {
        GameInfoDialog(info = gameInfo, shortcut = shortcut, onDismiss = { showGameInfo = false })
    }
}

private fun runShortcut(ctx: android.content.Context, shortcut: Shortcut) {
    try {
        val intent = Intent(ctx, XServerDisplayActivity::class.java)
        intent.putExtra("container_id", shortcut.container.id)
        intent.putExtra("shortcut_path", shortcut.file?.absolutePath ?: "")
        ctx.startActivity(intent)
    } catch (e: Exception) {
        AppUtils.showToast(ctx, "Cannot start: ${e.message}")
    }
}

// ---- SteamDB / Steam API Game Information Card implementation ----

class SteamGameInfo(
    val appId: Int,
    val name: String,
    val description: String,
    val headerImage: String,
    val releaseDate: String,
    val developers: List<String>,
    val publishers: List<String>,
    val genres: List<String>,
    val screenshots: List<String>,
    val trailerUrl: String?,
    val controllerSupport: String,
    val minRequirements: String,
    val recRequirements: String,
    val supportedLanguages: String,
    val metacriticScore: Int,
    val metacriticUrl: String,
    val reviewScoreDesc: String,
    val positivePercent: Int,
    val totalReviews: Int
)

private fun SteamGameInfo.hasContent(): Boolean =
    headerImage.isNotEmpty() || screenshots.isNotEmpty() || description.isNotEmpty()

fun SteamGameInfo.toJson(): JSONObject {
    val json = JSONObject()
    json.put("appId", appId)
    json.put("name", name)
    json.put("description", description)
    json.put("headerImage", headerImage)
    json.put("releaseDate", releaseDate)
    json.put("developers", org.json.JSONArray().apply { developers.forEach { put(it) } })
    json.put("publishers", org.json.JSONArray().apply { publishers.forEach { put(it) } })
    json.put("genres", org.json.JSONArray().apply { genres.forEach { put(it) } })
    json.put("screenshots", org.json.JSONArray().apply { screenshots.forEach { put(it) } })
    json.put("trailerUrl", trailerUrl ?: JSONObject.NULL)
    json.put("controllerSupport", controllerSupport)
    json.put("minRequirements", minRequirements)
    json.put("recRequirements", recRequirements)
    json.put("supportedLanguages", supportedLanguages)
    json.put("metacriticScore", metacriticScore)
    json.put("metacriticUrl", metacriticUrl)
    json.put("reviewScoreDesc", reviewScoreDesc)
    json.put("positivePercent", positivePercent)
    json.put("totalReviews", totalReviews)
    return json
}

fun JSONObject.toSteamGameInfo(): SteamGameInfo {
    val appId = optInt("appId")
    val name = optString("name", "")
    val description = optString("description", "")
    val headerImage = optString("headerImage", "")
    val releaseDate = optString("releaseDate", "")
    
    val developers = ArrayList<String>()
    optJSONArray("developers")?.let { arr ->
        for (i in 0 until arr.length()) {
            developers.add(arr.optString(i))
        }
    }
    
    val publishers = ArrayList<String>()
    optJSONArray("publishers")?.let { arr ->
        for (i in 0 until arr.length()) {
            publishers.add(arr.optString(i))
        }
    }
    
    val genres = ArrayList<String>()
    optJSONArray("genres")?.let { arr ->
        for (i in 0 until arr.length()) {
            genres.add(arr.optString(i))
        }
    }
    
    val screenshots = ArrayList<String>()
    optJSONArray("screenshots")?.let { arr ->
        for (i in 0 until arr.length()) {
            screenshots.add(arr.optString(i))
        }
    }
    
    val trailerUrl = if (has("trailerUrl") && !isNull("trailerUrl")) optString("trailerUrl") else null
    val controllerSupport = optString("controllerSupport", "")
    val minRequirements = optString("minRequirements", "")
    val recRequirements = optString("recRequirements", "")
    val supportedLanguages = optString("supportedLanguages", "")
    val metacriticScore = optInt("metacriticScore", 0)
    val metacriticUrl = optString("metacriticUrl", "")
    val reviewScoreDesc = optString("reviewScoreDesc", "")
    val positivePercent = optInt("positivePercent", 0)
    val totalReviews = optInt("totalReviews", 0)
    
    return SteamGameInfo(
        appId = appId,
        name = name,
        description = description,
        headerImage = headerImage,
        releaseDate = releaseDate,
        developers = developers,
        publishers = publishers,
        genres = genres,
        screenshots = screenshots,
        trailerUrl = trailerUrl,
        controllerSupport = controllerSupport,
        minRequirements = minRequirements,
        recRequirements = recRequirements,
        supportedLanguages = supportedLanguages,
        metacriticScore = metacriticScore,
        metacriticUrl = metacriticUrl,
        reviewScoreDesc = reviewScoreDesc,
        positivePercent = positivePercent,
        totalReviews = totalReviews
    )
}

private fun getDiskCacheFile(context: Context, appId: Int): File {
    val dir = File(context.filesDir, "steam_info_cache")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "steam_info_${appId}.json")
}

private fun saveToDiskCache(context: Context, info: SteamGameInfo) {
    try {
        val file = getDiskCacheFile(context, info.appId)
        val jsonStr = info.toJson().toString()
        FileUtils.writeString(file, jsonStr)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun loadFromDiskCache(context: Context, appId: Int): SteamGameInfo? {
    try {
        val file = getDiskCacheFile(context, appId)
        if (file.exists() && file.isFile) {
            val lines = FileUtils.readLines(file)
            val jsonStr = lines.joinToString("\n")
            if (jsonStr.isNotEmpty()) {
                val json = JSONObject(jsonStr)
                return json.toSteamGameInfo()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

private fun getGameInfoFromCache(context: Context, appId: Int): SteamGameInfo? {
    val memInfo = STEAM_GAME_INFO_CACHE[appId]
    if (memInfo != null) return memInfo
    val diskInfo = loadFromDiskCache(context, appId)
    if (diskInfo != null) {
        STEAM_GAME_INFO_CACHE[appId] = diskInfo
        return diskInfo
    }
    return null
}

private fun getAppIdDir(context: Context): File {
    val dir = File(context.filesDir, "steam_appid_cache")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun getCachedAppIdByName(context: Context, name: String): Int? {
    val file = File(getAppIdDir(context), "appid_${name.hashCode()}.txt")
    if (!file.exists()) return null
    val text = file.readText().trim()
    return text.toIntOrNull()
}

private fun saveCachedAppIdByName(context: Context, name: String, appId: Int) {
    try {
        val file = File(getAppIdDir(context), "appid_${name.hashCode()}.txt")
        file.writeText(appId.toString())
    } catch (_: Exception) {}
}

private fun deleteCachedAppIdByName(context: Context, name: String) {
    try {
        val file = File(getAppIdDir(context), "appid_${name.hashCode()}.txt")
        if (file.exists()) file.delete()
    } catch (_: Exception) {}
}

private fun deleteGameInfoFromDiskCache(context: Context, appId: Int) {
    try {
        val file = getDiskCacheFile(context, appId)
        if (file.exists()) file.delete()
    } catch (_: Exception) {}
}

/**
 * Вычисляет score совпадения между именем ярлыка и именем результата.
 * Используется для валидации Steam Store search результатов в SteamInfoDialog.
 */
private fun computeMatchScore(queryName: String, resultName: String): Int =
    GameTitleMatcher.matchScore(queryName, resultName)

private fun fetchAppIdByName(query: String, callback: (Int?) -> Unit) {
    val queries = GameTitleMatcher.searchQueries(query).ifEmpty { listOf(query.trim()) }
    fun attempt(index: Int) {
        if (index >= queries.size) {
            callback(null)
            return
        }
        val cleanQuery = queries[index]
        if (cleanQuery.length < 3) {
            attempt(index + 1)
            return
        }
        val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
        val url = "https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=us"
        val request = Request.Builder().url(url).build()

        DohOkHttp.get().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                attempt(index + 1)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        attempt(index + 1)
                        return
                    }
                    val body = response.body?.string() ?: ""
                    val items = JSONObject(body).optJSONArray("items")
                    if (items == null || items.length() == 0) {
                        attempt(index + 1)
                        return
                    }

                    var bestScore = -1
                    var bestId: Int? = null
                    var bestName = ""

                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val name = item.optString("name", "").trim()
                        if (name.isEmpty()) continue
                        val id = item.optInt("id", 0)
                        if (id <= 0) continue

                        val score = GameTitleMatcher.matchScore(cleanQuery, name)
                        if (score > bestScore) {
                            bestScore = score
                            bestId = id
                            bestName = name
                        }
                    }

                    if (bestId != null && bestScore >= 70) {
                        android.util.Log.i("CoverArt", "fetchAppIdByName: query=\"$query\" clean=\"$cleanQuery\" -> appId=$bestId \"$bestName\" score=$bestScore")
                        callback(bestId)
                    } else {
                        android.util.Log.w("CoverArt", "fetchAppIdByName: no good match query=\"$query\" clean=\"$cleanQuery\" bestScore=$bestScore best=\"$bestName\"")
                        attempt(index + 1)
                    }
                } catch (e: Exception) {
                    attempt(index + 1)
                }
            }
        })
    }
    attempt(0)
}

private fun fetchGameDetails(appId: Int, locale: String, callback: (SteamGameInfo?) -> Unit) {
    // Steam's appdetails endpoint rejects some localized values with success=false even for
    // a valid app (e.g. l=russian for 239140). Retry in English instead of showing "not found".
    val languages = if (locale.equals("russian", ignoreCase = true)) listOf("russian", "english") else listOf(locale)
    fun attempt(index: Int) {
        if (index >= languages.size) {
            callback(null)
            return
        }
        val url = "https://store.steampowered.com/api/appdetails?appids=$appId&l=${languages[index]}"
        val request = Request.Builder().url(url).build()

        DohOkHttp.get().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                attempt(index + 1)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        attempt(index + 1)
                        return
                    }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val appObj = json.optJSONObject(appId.toString())
                if (appObj != null && appObj.optBoolean("success", false)) {
                    val data = appObj.getJSONObject("data")
                    
                    val name = data.getString("name")
                    val description = data.optString("short_description", "").ifEmpty { data.optString("detailed_description", "") }
                    val headerImage = data.optString("header_image", "")
                    
                    val releaseObj = data.optJSONObject("release_date")
                    val releaseDate = releaseObj?.optString("date", "") ?: ""
                    
                    val developers = mutableListOf<String>()
                    val devsArr = data.optJSONArray("developers")
                    if (devsArr != null) {
                        for (i in 0 until devsArr.length()) developers.add(devsArr.getString(i))
                    }
                    
                    val publishers = mutableListOf<String>()
                    val pubsArr = data.optJSONArray("publishers")
                    if (pubsArr != null) {
                        for (i in 0 until pubsArr.length()) publishers.add(pubsArr.getString(i))
                    }
                    
                    val genres = mutableListOf<String>()
                    val genresArr = data.optJSONArray("genres")
                    if (genresArr != null) {
                        for (i in 0 until genresArr.length()) {
                            genres.add(genresArr.getJSONObject(i).getString("description"))
                        }
                    }
                    
                    val screenshots = mutableListOf<String>()
                    val ssArr = data.optJSONArray("screenshots")
                    if (ssArr != null) {
                        for (i in 0 until ssArr.length()) {
                            screenshots.add(ssArr.getJSONObject(i).getString("path_full"))
                        }
                    }
                    
                    var trailerUrl: String? = null
                    val moviesArr = data.optJSONArray("movies")
                    if (moviesArr != null && moviesArr.length() > 0) {
                        val firstMovie = moviesArr.getJSONObject(0)
                        
                        var rawUrl = firstMovie.optString("hls_h264", "").ifEmpty {
                            firstMovie.optString("dash_h264", "")
                        }
                        
                        if (rawUrl.isEmpty()) {
                            val mp4Obj = firstMovie.optJSONObject("mp4")
                            rawUrl = mp4Obj?.optString("max", "")?.ifEmpty { mp4Obj?.optString("480", "") } ?: ""
                        }
                        
                        if (rawUrl.isEmpty()) {
                            val webmObj = firstMovie.optJSONObject("webm")
                            rawUrl = webmObj?.optString("max", "")?.ifEmpty { webmObj?.optString("480", "") } ?: ""
                        }
                        
                        if (rawUrl.isNotEmpty()) {
                            trailerUrl = rawUrl.replace("http://", "https://")
                        }
                    }
                    
                    var controllerSupport = ""
                    val categories = data.optJSONArray("categories")
                    if (categories != null) {
                        for (i in 0 until categories.length()) {
                            val cat = categories.getJSONObject(i)
                            val catId = cat.optInt("id")
                            if (catId == 28) {
                                controllerSupport = "full"
                            } else if (catId == 18 && controllerSupport != "full") {
                                controllerSupport = "partial"
                            }
                        }
                    }

                    val pcReq = data.optJSONObject("pc_requirements")
                    val minRequirements = pcReq?.optString("minimum", "") ?: ""
                    val recRequirements = pcReq?.optString("recommended", "") ?: ""

                    val supportedLanguages = data.optString("supported_languages", "")

                    val metacriticObj = data.optJSONObject("metacritic")
                    val metacriticScore = metacriticObj?.optInt("score", 0) ?: 0
                    val metacriticUrl = metacriticObj?.optString("url", "") ?: ""

                    // A localized response can be success=true but carry no usable details.
                    // Try the next language before showing an empty card.
                    if (headerImage.isEmpty() && screenshots.isEmpty() && description.isEmpty() && index + 1 < languages.size) {
                        attempt(index + 1)
                        return
                    }

                    val reviewsUrl = "https://store.steampowered.com/appreviews/$appId?json=1&language=all&purchase_type=all"
                    val reviewsRequest = Request.Builder().url(reviewsUrl).build()
                    DohOkHttp.get().newCall(reviewsRequest).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            callback(SteamGameInfo(
                                appId = appId,
                                name = name,
                                description = description,
                                headerImage = headerImage,
                                releaseDate = releaseDate,
                                developers = developers,
                                publishers = publishers,
                                genres = genres,
                                screenshots = screenshots,
                                trailerUrl = trailerUrl,
                                controllerSupport = controllerSupport,
                                minRequirements = minRequirements,
                                recRequirements = recRequirements,
                                supportedLanguages = supportedLanguages,
                                metacriticScore = metacriticScore,
                                metacriticUrl = metacriticUrl,
                                reviewScoreDesc = "",
                                positivePercent = 0,
                                totalReviews = 0
                            ))
                        }

                        override fun onResponse(call: Call, response: Response) {
                            var reviewScoreDesc = ""
                            var positivePercent = 0
                            var totalReviews = 0
                            try {
                                val body = response.body?.string() ?: ""
                                val reviewsJson = JSONObject(body)
                                val summary = reviewsJson.optJSONObject("query_summary")
                                if (summary != null) {
                                    reviewScoreDesc = summary.optString("review_score_desc", "")
                                    val totalPositive = summary.optInt("total_positive", 0)
                                    totalReviews = summary.optInt("total_reviews", 0)
                                    positivePercent = if (totalReviews > 0) (totalPositive * 100 / totalReviews) else 0
                                }
                            } catch (e: Exception) {}

                            callback(SteamGameInfo(
                                appId = appId,
                                name = name,
                                description = description,
                                headerImage = headerImage,
                                releaseDate = releaseDate,
                                developers = developers,
                                publishers = publishers,
                                genres = genres,
                                screenshots = screenshots,
                                trailerUrl = trailerUrl,
                                controllerSupport = controllerSupport,
                                minRequirements = minRequirements,
                                recRequirements = recRequirements,
                                supportedLanguages = supportedLanguages,
                                metacriticScore = metacriticScore,
                                metacriticUrl = metacriticUrl,
                                reviewScoreDesc = reviewScoreDesc,
                                positivePercent = positivePercent,
                                totalReviews = totalReviews
                            ))
                        }
                    })
                } else {
                    attempt(index + 1)
                }
            } catch (e: Exception) {
                attempt(index + 1)
            }
        }
    })
    }
    attempt(0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamInfoDialog(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloneClick: () -> Unit,
    onPropertiesClick: () -> Unit,
    onRefresh: () -> Unit,
    onSearchConfigs: (Shortcut) -> Unit = {},
) {
    val ctx = LocalContext.current
    val locale = java.util.Locale.getDefault().language
    val isRussian = locale == "ru"

    val initialAppId = remember(shortcut) {
        shortcut.getExtra("steamAppId").toIntOrNull()
            ?: STEAM_NAME_TO_APP_ID_CACHE[shortcut.name]
            ?: getCachedAppIdByName(ctx, shortcut.name)
    }
    val initialInfo = remember(initialAppId) {
        initialAppId?.let { getGameInfoFromCache(ctx, it) }
            ?.takeIf {
                it.hasContent() &&
                    GameTitleMatcher.matchScore(GameTitleMatcher.bestQuery(shortcut.name), it.name) >= 75
            }
    }

    var appIdState by remember(shortcut) { mutableStateOf<Int?>(initialAppId) }
    var gameInfo by remember(shortcut) { mutableStateOf<SteamGameInfo?>(initialInfo) }
    var isLoading by remember(shortcut) { mutableStateOf(initialInfo == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var activeFullScreenScreenshotIndex by remember { mutableStateOf<Int?>(null) }
    var activeTrailerUrl by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    /**
     * Проверяет, совпадает ли имя игры из Steam API с именем ярлыка.
     * Если score < 75 — считаем что appId не соответствует этой игре.
     */
    fun isGameNameMatch(shortcutName: String, gameName: String): Boolean {
        return computeMatchScore(GameTitleMatcher.bestQuery(shortcutName), gameName) >= 75
    }

    /** Persist the chosen Steam appId so the details survive closing/reopening the menu. */
    fun applyAppId(appId: Int) {
        if (appId <= 0) return
        STEAM_NAME_TO_APP_ID_CACHE[shortcut.name] = appId
        saveCachedAppIdByName(ctx, shortcut.name, appId)
        shortcut.putExtra("steamAppId", appId.toString())
        shortcut.saveData()
    }

    fun loadDetails(appId: Int) {
        val cachedInfo = getGameInfoFromCache(ctx, appId)
        if (cachedInfo != null) {
            // If names match and the cache actually has content — accept cached info
            if (isGameNameMatch(shortcut.name, cachedInfo.name) && cachedInfo.hasContent()) {
                applyAppId(appId)
                gameInfo = cachedInfo
                isLoading = false
                errorMessage = null
                return
            } else {
                // Cached info is for a different game or is empty/stale — invalidate it
                android.util.Log.w("CoverArt", "Cached game info mismatch/empty: shortcut=${shortcut.name} vs game=${cachedInfo.name} appId=$appId")
                STEAM_GAME_INFO_CACHE.remove(appId)
                deleteGameInfoFromDiskCache(ctx, appId)
                // Clear shortcut's cached appId
                shortcut.putExtra("steamAppId", "")
                shortcut.saveData()
            }
        }
        isLoading = true
        errorMessage = null
        fetchGameDetails(appId, if (isRussian) "russian" else "english") { info ->
            if (info != null) {
                // If name validation is skipped OR names match — accept
                if (isGameNameMatch(shortcut.name, info.name)) {
                    applyAppId(appId)
                    STEAM_GAME_INFO_CACHE[appId] = info
                    saveToDiskCache(ctx, info)
                    gameInfo = info
                    isLoading = false
                } else {
                    android.util.Log.w("CoverArt", "Fetched game info mismatch: shortcut=${shortcut.name} vs game=${info.name} appId=$appId — re-searching")
                    // Name mismatch — clear cached appId and re-search
                    shortcut.putExtra("steamAppId", "")
                    shortcut.saveData()
                    STEAM_NAME_TO_APP_ID_CACHE.remove(shortcut.name)
                    deleteCachedAppIdByName(ctx, shortcut.name)
                    STEAM_GAME_INFO_CACHE.remove(appId)
                    deleteGameInfoFromDiskCache(ctx, appId)
                    // Try to find the correct appId via search
                    fetchAppIdByName(shortcut.name) { resolvedId ->
                        if (resolvedId != null && resolvedId > 0 && resolvedId != appId) {
                            applyAppId(resolvedId)
                            appIdState = resolvedId
                            loadDetails(resolvedId)
                        } else {
                            isLoading = false
                            errorMessage = if (isRussian) "Не удалось автоматически найти игру в Steam." else "Could not find the game on Steam automatically."
                        }
                    }
                }
            } else {
                // appdetails returns null for bundle/package ids (e.g. a DLC bundle stored
                // by an older version). Resolve a real app by name before giving up.
                shortcut.putExtra("steamAppId", "")
                shortcut.saveData()
                STEAM_NAME_TO_APP_ID_CACHE.remove(shortcut.name)
                deleteCachedAppIdByName(ctx, shortcut.name)
                fetchAppIdByName(shortcut.name) { resolvedId ->
                    if (resolvedId != null && resolvedId > 0 && resolvedId != appId) {
                        applyAppId(resolvedId)
                        appIdState = resolvedId
                        loadDetails(resolvedId)
                    } else {
                        isLoading = false
                        errorMessage = if (isRussian) "Не удалось автоматически найти игру в Steam." else "Could not find the game on Steam automatically."
                    }
                }
            }
        }
    }

    /** Automatic retry: no manual search, just resolve the shortcut title again. */
    fun retryAutoResolve() {
        isLoading = true
        errorMessage = null
        fetchAppIdByName(shortcut.name) { resolvedId ->
            if (resolvedId != null && resolvedId > 0) {
                applyAppId(resolvedId)
                appIdState = resolvedId
                loadDetails(resolvedId)
            } else {
                isLoading = false
                errorMessage = if (isRussian) "Не удалось автоматически найти игру в Steam." else "Could not find the game on Steam automatically."
            }
        }
    }

    LaunchedEffect(shortcut) {
        val existingInfo = gameInfo
        if (existingInfo != null) {
            // Cached details already valid: persist the id that produced them, so the
            // shortcut stops carrying an old bundle/package id.
            if (existingInfo.appId > 0) {
                appIdState = existingInfo.appId
                applyAppId(existingInfo.appId)
            }
            return@LaunchedEffect
        }
        val cachedAppId = shortcut.getExtra("steamAppId").toIntOrNull()
            ?: STEAM_NAME_TO_APP_ID_CACHE[shortcut.name]
            ?: getCachedAppIdByName(ctx, shortcut.name)
        if (cachedAppId != null && cachedAppId > 0) {
            appIdState = cachedAppId
            STEAM_NAME_TO_APP_ID_CACHE[shortcut.name] = cachedAppId
            loadDetails(cachedAppId)
        } else {
            retryAutoResolve()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            )
                    ) {
                        if (gameInfo != null) {
                            val heroUrl = remember(gameInfo) {
                                "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${gameInfo!!.appId}/library_hero.jpg"
                            }
                            val capsuleUrl = remember(gameInfo) {
                                "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${gameInfo!!.appId}/capsule_616x353.jpg"
                            }
                            val heroFile = rememberCachedSteamImage(heroUrl, capsuleUrl)
                            val logoUrl = remember(gameInfo) {
                                "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${gameInfo!!.appId}/logo.png"
                            }
                            val logoFile = rememberCachedSteamImage(logoUrl)
                            
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (heroFile != null) {
                                    coil.compose.AsyncImage(
                                        model = heroFile,
                                        contentDescription = gameInfo!!.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                if (logoFile != null) {
                                    coil.compose.AsyncImage(
                                        model = logoFile,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .height(90.dp)
                                            .padding(horizontal = 16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                var displayIcon by remember(shortcut) { mutableStateOf(shortcut.getDisplayIcon()) }

                                LaunchedEffect(shortcut, displayIcon) {
                                    if (displayIcon == null) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val extracted = shortcut.extractAndSaveIcon()
                                            if (extracted != null) {
                                                displayIcon = extracted
                                            }
                                        }
                                    }
                                }

                                if (displayIcon != null) {
                                    coil.compose.AsyncImage(
                                        model = displayIcon,
                                        contentDescription = shortcut.name,
                                        modifier = Modifier.size(80.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.VideogameAsset,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.6f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .align(Alignment.TopCenter),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = gameInfo?.name ?: shortcut.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        GlassSolidPill(
                            onClick = {
                                onDismiss()
                                runShortcut(ctx, shortcut)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isRussian) "ИГРАТЬ" else "PLAY",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }

                        GlassSurface(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ActionGridItem(
                                        icon = Icons.Filled.Settings,
                                        label = if (isRussian) "Настройки" else "Settings",
                                        onClick = onOpenSettings,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.AddToHomeScreen,
                                        label = if (isRussian) "На экран" else "Add Shortcut",
                                        onClick = {
                                            if (shortcut.getExtra("uuid").isEmpty()) shortcut.genUUID()
                                            addShortcutToHomeScreen(ctx, shortcut)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.ContentCopy,
                                        label = if (isRussian) "Клон" else "Clone",
                                        onClick = onCloneClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ActionGridItem(
                                        icon = Icons.Filled.Cloud,
                                        label = if (isRussian) "Конфиги" else "Configs",
                                        onClick = {
                                            onSearchConfigs(shortcut)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.IosShare,
                                        label = if (isRussian) "Экспорт" else "Export",
                                        onClick = { exportShortcutToFrontend(ctx, shortcut) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.Info,
                                        label = if (isRussian) "Свойства" else "Properties",
                                        onClick = onPropertiesClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.Delete,
                                        label = if (isRussian) "Удалить" else "Delete",
                                        iconTint = MaterialTheme.colorScheme.error,
                                        onClick = {
                                            val ok = shortcut.file?.delete() ?: false
                                            if (ok) {
                                                disableShortcutOnScreen(ctx, shortcut)
                                                AppUtils.showToast(ctx, if (isRussian) "Ярлык удален" else "Shortcut removed")
                                                onRefresh()
                                                onDismiss()
                                            } else {
                                                AppUtils.showToast(ctx, if (isRussian) "Ошибка удаления" else "Failed to remove")
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (isRussian) "Загрузка информации из Steam..." else "Loading information from Steam...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else if (gameInfo != null) {
                            val info = gameInfo!!
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (info.reviewScoreDesc.isNotEmpty() || info.totalReviews > 0) {
                                    GlassSurface(
                                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.ThumbUp,
                                                    contentDescription = "Rating",
                                                    tint = Color(0xFF4CAF50),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "${info.positivePercent}%",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4CAF50)
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = if (isRussian) {
                                                    when (info.reviewScoreDesc.lowercase()) {
                                                        "overwhelmingly positive" -> "Крайне положительные"
                                                        "very positive" -> "Очень положительные"
                                                        "positive" -> "Положительные"
                                                        "mostly positive" -> "В основном положительные"
                                                        "mixed" -> "Смешанные"
                                                        "mostly negative" -> "В основном отрицательные"
                                                        "negative" -> "Отрицательные"
                                                        "very negative" -> "Очень отрицательные"
                                                        "overwhelmingly negative" -> "Крайне отрицательные"
                                                        else -> info.reviewScoreDesc
                                                    }
                                                } else info.reviewScoreDesc,
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(1.dp))
                                            Text(
                                                text = if (isRussian) "${java.text.NumberFormat.getInstance().format(info.totalReviews)} отзывов"
                                                       else "${java.text.NumberFormat.getInstance().format(info.totalReviews)} reviews",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }

                                GlassSurface(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.VideogameAsset,
                                                contentDescription = "Controller",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (isRussian) "Геймпад" else "Controller",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (info.controllerSupport == "full") {
                                                if (isRussian) "Полная" else "Full"
                                            } else if (info.controllerSupport == "partial") {
                                                if (isRussian) "Частичная" else "Partial"
                                            } else {
                                                if (isRussian) "Нет данных" else "No Data"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (info.controllerSupport == "full") Color(0xFF4CAF50) else if (info.controllerSupport == "partial") Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                if (info.metacriticScore > 0) {
                                    GlassSurface(
                                        modifier = Modifier.weight(0.8f).fillMaxHeight(),
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "Metacritic",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = if (info.metacriticScore >= 75) Color(0xFF66CC33)
                                                                else if (info.metacriticScore >= 50) Color(0xFFFFCC33)
                                                                else Color(0xFFFF3333),
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = info.metacriticScore.toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (info.releaseDate.isNotEmpty()) {
                                        MetadataRow(label = if (isRussian) "Дата релиза" else "Release Date", value = info.releaseDate)
                                    }
                                    if (info.developers.isNotEmpty()) {
                                        MetadataRow(label = if (isRussian) "Разработчик" else "Developer", value = info.developers.joinToString(", "))
                                    }
                                    if (info.publishers.isNotEmpty()) {
                                        MetadataRow(label = if (isRussian) "Издатель" else "Publisher", value = info.publishers.joinToString(", "))
                                    }
                                    if (info.genres.isNotEmpty()) {
                                        MetadataRow(label = if (isRussian) "Жанры" else "Genres", value = info.genres.joinToString(", "))
                                    }
                                }
                            }

                            Text(
                                text = if (isRussian) "Описание" else "Description",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val plainDescription = remember(info.description) {
                                try {
                                    android.text.Html.fromHtml(info.description, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                                } catch (e: Throwable) {
                                    info.description.replace("<[^>]*>".toRegex(), "").trim()
                                }
                            }

                            Text(
                                text = plainDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            if (info.screenshots.isNotEmpty() || info.trailerUrl != null) {
                                Text(
                                    text = if (isRussian) "Скриншоты и видео" else "Screenshots & Video",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (info.trailerUrl != null) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .width(160.dp)
                                                    .height(90.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { activeTrailerUrl = info.trailerUrl }
                                            ) {
                                                coil.compose.AsyncImage(
                                                    model = rememberCachedSteamImage(info.headerImage),
                                                    contentDescription = "Video preview",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.4f))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(percent = 50))
                                                        .align(Alignment.Center),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.PlayArrow,
                                                        contentDescription = "Play Video",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    var idx = 0
                                    items(info.screenshots) { ssUrl ->
                                        val currentIndex = idx++
                                        Box(
                                            modifier = Modifier
                                                .width(160.dp)
                                                .height(90.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { activeFullScreenScreenshotIndex = currentIndex }
                                        ) {
                                            coil.compose.AsyncImage(
                                                model = rememberCachedSteamImage(ssUrl),
                                                contentDescription = "screenshot",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }

                            if (info.minRequirements.isNotEmpty() || info.recRequirements.isNotEmpty()) {
                                var showRequirements by remember { mutableStateOf(false) }
                                
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showRequirements = !showRequirements }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isRussian) "Системные требования" else "System Requirements",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (showRequirements) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = "Toggle Requirements",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    if (showRequirements) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (info.minRequirements.isNotEmpty()) {
                                                val minText = remember(info.minRequirements) {
                                                    try {
                                                        android.text.Html.fromHtml(info.minRequirements, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                                                    } catch (e: Throwable) {
                                                        info.minRequirements.replace("<[^>]*>".toRegex(), "").trim()
                                                    }
                                                }
                                                GlassSurface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = if (isRussian) "Минимальные требования:" else "Minimum Requirements:",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = minText,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            if (info.recRequirements.isNotEmpty()) {
                                                val recText = remember(info.recRequirements) {
                                                    try {
                                                        android.text.Html.fromHtml(info.recRequirements, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                                                    } catch (e: Throwable) {
                                                        info.recRequirements.replace("<[^>]*>".toRegex(), "").trim()
                                                    }
                                                }
                                                GlassSurface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp),
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = if (isRussian) "Рекомендуемые требования:" else "Recommended Requirements:",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = recText,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = if (isRussian) "Ищем игру в Steam…" else "Looking up the game on Steam…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Text(
                                        text = errorMessage ?: (if (isRussian) "Игра не найдена в Steam." else "Game not found on Steam."),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        textAlign = TextAlign.Center
                                    )
                                    GlassSolidPill(
                                        onClick = { retryAutoResolve() },
                                    ) {
                                        Text(if (isRussian) "Повторить поиск" else "Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    activeFullScreenScreenshotIndex?.let { startIndex ->
        val pagerState = rememberPagerState(
            initialPage = startIndex,
            pageCount = { gameInfo?.screenshots?.size ?: 0 }
        )
        Dialog(
            onDismissRequest = { activeFullScreenScreenshotIndex = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val ssUrl = gameInfo?.screenshots?.getOrNull(page)
                    if (ssUrl != null) {
                        coil.compose.AsyncImage(
                            model = rememberCachedSteamImage(ssUrl),
                            contentDescription = "screenshot_fullscreen",
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                IconButton(
                    onClick = { activeFullScreenScreenshotIndex = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Text(
                    text = "${pagerState.currentPage + 1} / ${gameInfo?.screenshots?.size ?: 0}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    activeTrailerUrl?.let { trailerUrl ->
        Dialog(
            onDismissRequest = { activeTrailerUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            webChromeClient = android.webkit.WebChromeClient()
                            webViewClient = android.webkit.WebViewClient()
                            
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                    <style>
                                        body, html { margin: 0; padding: 0; width: 100vw; height: 100vh; overflow: hidden; background-color: black; }
                                        video { position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: contain; }
                                    </style>
                                    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                                </head>
                                <body>
                                    <video id="video" controls autoplay playsinline></video>
                                    <script>
                                        var video = document.getElementById('video');
                                        var videoSrc = '$trailerUrl';
                                        if (Hls.isSupported()) {
                                            var hls = new Hls();
                                            hls.loadSource(videoSrc);
                                            hls.attachMedia(video);
                                            hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                                video.play();
                                            });
                                        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                            video.src = videoSrc;
                                            video.addEventListener('loadedmetadata', function() {
                                                video.play();
                                            });
                                        }
                                    </script>
                                </body>
                                </html>
                            """.trimIndent()
                            
                            loadDataWithBaseURL("https://video.akamai.steamstatic.com/", html, "text/html", "UTF-8", null)
                        }
                    },
                    onRelease = { webView ->
                        webView.destroy()
                    }
                )

                IconButton(
                    onClick = { activeTrailerUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionGridItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = glassAccent()
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(GlassTokens.InnerRadius))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Референс (photo_3 «Еще меню»): иконка в круглой плашке, подпись под ней.
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(
                    if (glassIsDark()) Color.White.copy(alpha = 0.08f)
                    else Color.Black.copy(alpha = 0.05f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = glassTitleColor(),
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
