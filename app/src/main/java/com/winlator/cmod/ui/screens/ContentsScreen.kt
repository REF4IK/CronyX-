package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.contents.Downloader
import com.winlator.cmod.core.MmkvPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

private val URL_REF4IK = "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json"
private val URL_THE412BANNER = "https://raw.githubusercontent.com/The412Banner/winlator-contents/main/contents.json"

/**
 * Полный перенос ContentsFragment.java на Jetpack Compose.
 * Список контента (Wine/DXVK/VKD3D/Box64/WOWBox64/FEXCore) с фильтром по типу,
 * скачиванием с прогрессом, установкой из файла, управлением источником контента,
 * инфо о контенте и удалением. Все строки из R.string.* (подключены переводы).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsScreen(
    onBack: () -> Unit,
    onOpenInstalledComponents: () -> Unit,
    showSourceDialog: Boolean,
    onShowSourceDialogChange: (Boolean) -> Unit,
    showInstallConfirm: Boolean,
    onShowInstallConfirmChange: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val contentsManager = remember { ContentsManager(ctx) }
    val sp = remember { MmkvPreferences() }
    val fileSizeCache = remember { MmkvPreferences("file_size_cache") }

    // Текущий тип контента
    val contentTypes = remember { ContentProfile.ContentType.values().toList() }
    var currentContentType by remember { mutableStateOf(ContentProfile.ContentType.CONTENT_TYPE_WINE) }
    var contentTypeExpanded by remember { mutableStateOf(false) }

    // Список профилей для текущего типа
    var profiles by remember { mutableStateOf<List<ContentProfile>>(emptyList()) }
    // Кэш размеров файлов: remoteUrl -> bytes (-1 = unknown)
    var sizesMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // Состояние установки (прелоадер)
    var installing by remember { mutableStateOf(false) }
    var installStatus by remember { mutableStateOf("") }

    // Скачивание: remoteUrl -> прогресс (0..100, -1 = unknown total)
    var downloadingUrl by remember { mutableStateOf<String?>(null) }
    var downloadProgressText by remember { mutableStateOf("") }

    // Диалоги
    var profileInfo by remember { mutableStateOf<ContentProfile?>(null) }
    var profileToRemove by remember { mutableStateOf<ContentProfile?>(null) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var untrustedFiles by remember { mutableStateOf<List<ContentProfile.ContentFile>?>(null) }
    var untrustedProfile by remember { mutableStateOf<ContentProfile?>(null) }

    // Очистка кэша при выходе
    DisposableEffect(Unit) {
        onDispose {
            Thread {
                com.winlator.cmod.core.FileUtils.clear(ctx.cacheDir)
            }.start()
        }
    }

    // Первичная синхронизация
    LaunchedEffect(Unit) {
        contentsManager.syncContents()
        profiles = contentsManager.getProfiles(currentContentType) ?: emptyList()
    }

    // Загрузка удалённых профилей (как onResume в оригинале)
    fun loadRemoteProfiles() {
        scope.launch {
            val contentsUrl = sp.getString("downloadable_contents_url", URL_REF4IK) ?: URL_REF4IK
            val json = withContext(Dispatchers.IO) { Downloader.downloadString(contentsUrl) }
            if (json != null) {
                contentsManager.setRemoteProfiles(json)
                profiles = contentsManager.getProfiles(currentContentType) ?: emptyList()
            }
        }
    }
    LaunchedEffect(Unit) { loadRemoteProfiles() }

    // Предзагрузка размеров файлов
    LaunchedEffect(profiles) {
        val newSizes = mutableMapOf<String, Long>()
        val toFetch = mutableListOf<String>()
        for (p in profiles) {
            val url = p.remoteUrl ?: continue
            if (fileSizeCache.contains(url)) {
                newSizes[url] = fileSizeCache.getLong(url, -1)
            } else {
                toFetch.add(url)
            }
        }
        sizesMap = newSizes
        if (toFetch.isNotEmpty()) {
            scope.launch {
                for (url in toFetch) {
                    val size = withContext(Dispatchers.IO) { Downloader.getFileSize(url) }
                    fileSizeCache.edit().putLong(url, size).apply()
                    newSizes[url] = size
                    sizesMap = newSizes.toMap()
                }
            }
        }
    }

    // Обновление списка при смене типа
    fun reloadList() {
        profiles = contentsManager.getProfiles(currentContentType) ?: emptyList()
    }

    // Форматирование размера
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    // Размер установленного контента (локальный)
    fun getDirSize(dir: File): Long {
        if (dir.isFile) return dir.length()
        var size = 0L
        val files = dir.listFiles() ?: return 0
        for (f in files) size += if (f.isFile) f.length() else getDirSize(f)
        return size
    }

    // Установка контента из URI (как onActivityResult + extraContentFile)
    fun installContentFromUri(uri: Uri) {
        installing = true
        installStatus = ctx.getString(R.string.installing_content)
        Executors.newSingleThreadExecutor().execute {
            contentsManager.extraContentFile(uri, object : ContentsManager.OnInstallFinishedCallback {
                var isExtracting = true
                override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                    val msgId = when (reason) {
                        ContentsManager.InstallFailedReason.ERROR_BADTAR -> R.string.file_cannot_be_recognied
                        ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> R.string.profile_not_found_in_content
                        ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> R.string.profile_cannot_be_recognized
                        ContentsManager.InstallFailedReason.ERROR_EXIST -> R.string.content_already_exist
                        ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> R.string.content_is_incomplete
                        ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> R.string.content_cannot_be_trusted
                        else -> R.string.unable_to_install_content
                    }
                    installStatus = ctx.getString(R.string.install_failed) + ": " + ctx.getString(msgId)
                    installing = false
                }

                override fun onSucceed(profile: ContentProfile) {
                    if (isExtracting) {
                        // Показываем инфо о контенте, затем проверяем ненадёжные файлы
                        profileInfo = profile
                        // Флаг: после закрытия инфо — продолжить установку
                        untrustedProfile = profile
                        installing = false
                    } else {
                        // Финальная установка завершена
                        installing = false
                        contentsManager.syncContents()
                        currentContentType = profile.type
                        reloadList()
                        Toast.makeText(ctx, R.string.content_installed_success, Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
    }

    // Лаунчер выбора файла контента
    val installContentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) installContentFromUri(uri)
    }

    // Скачивание + установка удалённого контента
    fun downloadAndInstall(profile: ContentProfile) {
        val url = profile.remoteUrl ?: return
        downloadingUrl = url
        downloadProgressText = ctx.getString(R.string.download_progress)
        Thread {
            val timestamp = System.currentTimeMillis()
            val output = File(ctx.cacheDir, "temp_$timestamp")
            val success = Downloader.downloadFile(url, output) { downloaded, total ->
                if (total > 0) {
                    val percent = (downloaded * 100 / total).toInt()
                    downloadProgressText = ctx.getString(R.string.download_progress) + ": " +
                        formatSize(downloaded) + " / " + formatSize(total) + " ($percent%)"
                } else {
                    downloadProgressText = ctx.getString(R.string.download_progress) + ": " + formatSize(downloaded)
                }
            }
            downloadingUrl = null
            if (success) {
                installContentFromUri(Uri.fromFile(output))
            } else {
                Toast.makeText(ctx, R.string.download_failed, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // Удаление контента с проверкой использования контейнером
    fun removeContent(profile: ContentProfile) {
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
        reloadList()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Выпадающий список типов контента
        Box {
            GlassPill(
                onClick = { contentTypeExpanded = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(currentContentType.toString(), modifier = Modifier.weight(1f))
                Icon(if (contentTypeExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded = contentTypeExpanded, onDismissRequest = { contentTypeExpanded = false }) {
                contentTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.toString()) },
                        onClick = {
                            currentContentType = type
                            contentTypeExpanded = false
                            reloadList()
                        }
                    )
                }
            }
        }

        when {
            installing -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(installStatus, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            profiles.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_content_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(profiles, key = { ContentsManager.getEntryName(it) }) { profile ->
                        ContentItemCard(
                            profile = profile,
                            sizeText = when {
                                profile.remoteUrl != null -> {
                                    val size = sizesMap[profile.remoteUrl]
                                    when {
                                        size == null -> ctx.getString(R.string.loading_size)
                                        size > 0 -> ctx.getString(R.string.file_size, formatSize(size))
                                        else -> ctx.getString(R.string.file_size, ctx.getString(R.string.unknown_size))
                                    }
                                }
                                else -> {
                                    val dir = ContentsManager.getInstallDir(ctx, profile)
                                    if (dir.exists()) ctx.getString(R.string.installed_size) + ": " + formatSize(getDirSize(dir))
                                    else ""
                                }
                            },
                            isDownloading = downloadingUrl == profile.remoteUrl,
                            downloadProgressText = downloadProgressText,
                            onDownload = { downloadAndInstall(profile) },
                            onInfo = { profileInfo = profile },
                            onRemove = { profileToRemove = profile },
                        )
                    }
                }
            }
        }
    }

    // Диалог выбора источника контента
    if (showSourceDialog) {
        val currentUrl = sp.getString("downloadable_contents_url", URL_REF4IK) ?: URL_REF4IK
        var checkedItem = 2
        if (currentUrl == URL_REF4IK) checkedItem = 0
        else if (currentUrl == URL_THE412BANNER) checkedItem = 1
        val presets = listOf("REF4IK", "The412Banner", ctx.getString(R.string.custom_profile))
        AlertDialog(
            onDismissRequest = { onShowSourceDialogChange(false) },
            title = { Text(stringResource(R.string.contents_source)) },
            text = {
                Column {
                    presets.forEachIndexed { index, label ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onShowSourceDialogChange(false)
                                if (index < 2) {
                                    val url = if (index == 0) URL_REF4IK else URL_THE412BANNER
                                    sp.edit().putString("downloadable_contents_url", url).apply()
                                    loadRemoteProfiles()
                                } else {
                                    showCustomUrlDialog = true
                                }
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = index == checkedItem, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onShowSourceDialogChange(false) }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Диалог кастомного URL
    if (showCustomUrlDialog) {
        var name by remember { mutableStateOf(sp.getString("custom_contents_name", "") ?: "") }
        var url by remember { mutableStateOf(sp.getString("custom_contents_url", "") ?: "") }
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text(stringResource(R.string.custom_profile)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text(stringResource(R.string.profile_name_hint)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    ,
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text(stringResource(R.string.profile_url_hint)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    ,
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val u = url.trim()
                    if (u.isNotEmpty()) {
                        sp.edit()
                            .putString("custom_contents_name", name.trim())
                            .putString("custom_contents_url", u)
                            .putString("downloadable_contents_url", u)
                            .apply()
                        showCustomUrlDialog = false
                        loadRemoteProfiles()
                    }
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showCustomUrlDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Диалог подтверждения установки контента
    if (showInstallConfirm) {
        AlertDialog(
            onDismissRequest = { onShowInstallConfirmChange(false) },
            title = { Text(stringResource(R.string.install)) },
            text = {
                Text(
                    stringResource(R.string.do_you_want_to_install_content) + " " +
                    stringResource(R.string.pls_make_sure_content_trustworthy) + " " +
                    stringResource(R.string.content_suffix_is_wcp_packed_xz_zst) + "\n" +
                    stringResource(R.string.get_more_contents_form_github)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onShowInstallConfirmChange(false)
                    installContentLauncher.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { onShowInstallConfirmChange(false) }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Диалог инфо о контенте
    profileInfo?.let { profile ->
        ContentInfoDialog(
            profile = profile,
            onDismiss = { profileInfo = null },
            onContinue = {
                profileInfo = null
                // После инфо — проверяем ненадёжные файлы
                val untrusted = contentsManager.getUnTrustedContentFiles(profile)
                if (untrusted.isNotEmpty()) {
                    untrustedFiles = untrusted
                    untrustedProfile = profile
                } else {
                    // Нет ненадёжных — завершаем установку
                    val p = profile
                    installing = true
                    installStatus = ctx.getString(R.string.installing_content)
                    Executors.newSingleThreadExecutor().execute {
                        contentsManager.finishInstallContent(p, object : ContentsManager.OnInstallFinishedCallback {
                            override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                                installing = false
                            }
                            override fun onSucceed(p: ContentProfile) {
                                installing = false
                                contentsManager.syncContents()
                                currentContentType = p.type
                                reloadList()
                                Toast.makeText(ctx, R.string.content_installed_success, Toast.LENGTH_LONG).show()
                            }
                        })
                    }
                }
            }
        )
    }

    // Диалог ненадёжных файлов
    untrustedFiles?.let { files ->
        ContentUntrustedDialog(
            files = files,
            onDismiss = {
                untrustedFiles = null
                untrustedProfile = null
            },
            onContinue = {
                untrustedFiles = null
                val p = untrustedProfile
                untrustedProfile = null
                if (p != null) {
                    installing = true
                    installStatus = ctx.getString(R.string.installing_content)
                    Executors.newSingleThreadExecutor().execute {
                        contentsManager.finishInstallContent(p, object : ContentsManager.OnInstallFinishedCallback {
                            override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                                installing = false
                            }
                            override fun onSucceed(p: ContentProfile) {
                                installing = false
                                contentsManager.syncContents()
                                currentContentType = p.type
                                reloadList()
                                Toast.makeText(ctx, R.string.content_installed_success, Toast.LENGTH_LONG).show()
                            }
                        })
                    }
                }
            }
        )
    }

    // Диалог подтверждения удаления
    profileToRemove?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToRemove = null },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(stringResource(R.string.do_you_want_to_remove_this_content)) },
            confirmButton = {
                TextButton(onClick = {
                    removeContent(profile)
                    profileToRemove = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { profileToRemove = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun ContentItemCard(
    profile: ContentProfile,
    sizeText: String,
    isDownloading: Boolean,
    downloadProgressText: String,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    val iconRes = if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE) R.drawable.icon_wine else R.drawable.icon_settings
    val isRemote = profile.remoteUrl != null
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
        shape = RoundedCornerShape(GlassTokens.CardRadius),
        border = glassCardBorder(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.verName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ctx.getString(R.string.version_code) + ": " + profile.verCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sizeText.isNotEmpty()) {
                    Text(sizeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isDownloading) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(downloadProgressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            // Меню (только для локального контента)
            if (!isRemote) {
                IconButton(onClick = onInfo) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.content_info), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error)
                }
            } else if (!isDownloading) {
                // Кнопка скачивания для удалённого
                IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.download), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ContentInfoDialog(
    profile: ContentProfile,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.content_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profile.type.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.version) + ": " + profile.verName, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.version_code) + ": " + profile.verCode, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(profile.desc, style = MaterialTheme.typography.bodySmall)
                if (profile.fileList.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.content_info), style = MaterialTheme.typography.titleSmall)
                    profile.fileList.forEach { f ->
                        Text("${f.source} -> ${f.target}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string._continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ContentUntrustedDialog(
    files: List<ContentProfile.ContentFile>,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.warning)) },
        text = {
            Column {
                files.forEach { f ->
                    Text("${f.source} -> ${f.target}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string._continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
