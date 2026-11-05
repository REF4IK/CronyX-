package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSurface

import androidx.compose.ui.graphics.Color
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSolidPill
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.contents.AdrenotoolsManager
import com.winlator.cmod.core.DriverResolver
import com.winlator.cmod.core.GPUInformation
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Полный перенос DriverStoreFragment.java на Jetpack Compose.
 * Поиск драйверов в репозиториях, загрузка с прогрессом, установка,
 * управление репозиториями (добавление/просмотр/удаление).
 * Все строки из R.string.* (подключены переводы).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverStoreScreen(
    onBack: () -> Unit,
    onDriverInstalled: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val driverResolver = remember { DriverResolver(ctx) }
    val adrenotoolsManager = remember { AdrenotoolsManager(ctx) }
    val coroutineScope = rememberCoroutineScope()

    // Состояния
    var drivers by remember { mutableStateOf<List<DriverResolver.DriverInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isEmpty by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    // Выбранный репозиторий (null = показываем список репозиториев)
    var selectedRepo by remember { mutableStateOf<String?>(null) }

    // Обработка системной кнопки "Назад":
    // если выбран репозиторий → вернуться к списку репозиториев,
    // иначе → закрыть Driver Store
    BackHandler(enabled = selectedRepo != null) {
        selectedRepo = null
    }

    // Диалоги
    var showRepoMenu by remember { mutableStateOf(false) }
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var showCustomReposDialog by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Загрузка драйвера с прогрессом
    var downloadingDriver by remember { mutableStateOf<DriverResolver.DriverInfo?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }

    // Имена уже установленных драйверов (для отметки в магазине)
    var installedDriverNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installedDriverUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Обновляем множество имён установленных драйверов
    fun refreshInstalledDrivers() {
        val names = mutableSetOf<String>()
        val urls = mutableSetOf<String>()
        try {
            for (id in adrenotoolsManager.enumarateInstalledDrivers()) {
                names.add(id)
                val n = adrenotoolsManager.getDriverName(id)
                if (n.isNotEmpty()) names.add(n)

                val storeInfo = adrenotoolsManager.getStoreInfo(id)
                if (storeInfo != null) {
                    val url = storeInfo.optString("downloadUrl")
                    if (url.isNotEmpty()) urls.add(url)
                    val sName = storeInfo.optString("storeName")
                    if (sName.isNotEmpty()) names.add(sName)
                }
            }
        } catch (_: Exception) { }
        installedDriverNames = names
        installedDriverUrls = urls
    }
    // Первичная загрузка списка установленных
    LaunchedEffect(Unit) { refreshInstalledDrivers() }

    // Информация о GPU
    val gpuModel = remember {
        try {
            val renderer = GPUInformation.getRenderer()
            extractGpuModel(renderer)
        } catch (_: Exception) { null }
    }

    // Очистка executor при выходе
    DisposableEffect(Unit) {
        onDispose { driverResolver.shutdown() }
    }

    // Загрузка драйверов
    fun loadDrivers() {
        isLoading = true
        isEmpty = false
        errorMsg = ""
        driverResolver.searchDrivers(object : DriverResolver.DriverSearchCallback {
            override fun onDriversFound(found: List<DriverResolver.DriverInfo>) {
                drivers = found
                isLoading = false
                isEmpty = found.isEmpty()
            }
            override fun onError(error: String) {
                isLoading = false
                isEmpty = true
                errorMsg = error
                Toast.makeText(ctx, error, Toast.LENGTH_LONG).show()
            }
        })
    }

    // Первичная загрузка
    LaunchedEffect(Unit) { loadDrivers() }

    // Установка драйвера после загрузки
    fun installDownloadedDriver(uri: Uri, driverInfo: DriverResolver.DriverInfo) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val installedId = adrenotoolsManager.installDriver(uri)
                if (installedId.isNotEmpty()) {
                    adrenotoolsManager.writeStoreInfo(installedId, driverInfo.name, driverInfo.version, driverInfo.downloadUrl)
                }
                withContext(Dispatchers.Main) {
                    if (installedId.isNotEmpty()) {
                        Toast.makeText(ctx, ctx.getString(R.string.driver_installed_successfully, driverInfo.name), Toast.LENGTH_LONG).show()
                        // Обновляем отметку установленных и остаёмся в магазине (на списке драйверов)
                        refreshInstalledDrivers()
                        onDriverInstalled()
                    } else {
                        Toast.makeText(ctx, R.string.driver_installation_failed, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, ctx.getString(R.string.installation_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Скачивание + установка
    fun downloadAndInstall(driverInfo: DriverResolver.DriverInfo) {
        downloadingDriver = driverInfo
        downloadProgress = 0
        driverResolver.downloadDriver(driverInfo, object : DriverResolver.DriverDownloadCallback {
            override fun onProgress(progress: Int) { downloadProgress = progress }
            override fun onComplete(driverUri: Uri) {
                coroutineScope.launch(Dispatchers.Main) {
                    downloadingDriver = null
                    installDownloadedDriver(driverUri, driverInfo)
                }
            }
            override fun onError(error: String) {
                coroutineScope.launch(Dispatchers.Main) {
                    downloadingDriver = null
                    Toast.makeText(ctx, ctx.getString(R.string.download_error, error), Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.driver_store_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedRepo != null) selectedRepo = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Кнопка управления репозиториями
                    IconButton(onClick = { showRepoMenu = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.manage_repositories))
                    }
                    // Кнопка обновления
                    IconButton(onClick = { loadDrivers() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
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
            // Информация о GPU
            if (gpuModel != null && gpuModel.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.gpu_model_format, gpuModel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.downloading_driver), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                isEmpty -> {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (errorMsg.isNotEmpty()) stringResource(R.string.driver_load_error, errorMsg) else "No drivers found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            GlassSolidPill(
                                onClick = { loadDrivers() },
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.refresh))
                            }
                        }
                    }
                }
                else -> {
                    val grouped = drivers.groupBy { it.repoName }
                    if (selectedRepo == null) {
                        // Список репозиториев — выбираешь репо, потом показываются его драйверы
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(grouped.keys.toList(), key = { it }) { repoName ->
                                GlassSurface(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { selectedRepo = repoName },
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Store, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.width(16.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(repoName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Text("${grouped[repoName]?.size ?: 0} drivers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    } else {
                        // Драйверы выбранного репозитория
                        val repoDrivers = grouped[selectedRepo] ?: emptyList()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(repoDrivers, key = { it.downloadUrl }) { driver ->
                                DriverDownloadCard(
                                    driver = driver,
                                    isDownloading = downloadingDriver == driver,
                                    downloadProgress = downloadProgress,
                                    isInstalled = installedDriverUrls.contains(driver.downloadUrl) || installedDriverNames.any { inst ->
                                        isDriverMatching(inst, driver.name)
                                    },
                                    onDownload = { downloadAndInstall(driver) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог выбора: добавить / просмотреть репозитории
    if (showRepoMenu) {
        AlertDialog(
            onDismissRequest = { showRepoMenu = false },
            title = { Text(stringResource(R.string.manage_repositories)) },
            text = {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { showRepoMenu = false; showAddRepoDialog = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.add_custom_repository))
                    }
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { showRepoMenu = false; showCustomReposDialog = true }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.view_custom_repositories))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRepoMenu = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Диалог добавления репозитория
    if (showAddRepoDialog) {
        AddRepositoryDialog(
            onDismiss = { showAddRepoDialog = false },
            onAdd = { url, name ->
                driverResolver.addCustomRepository(url, name)
                Toast.makeText(ctx, ctx.getString(R.string.repository_added_successfully, name), Toast.LENGTH_SHORT).show()
                showAddRepoDialog = false
                loadDrivers()
            }
        )
    }

    // Диалог списка пользовательских репозиториев
    if (showCustomReposDialog) {
        val customRepos = remember { driverResolver.getCustomRepositories() }
        if (customRepos.isEmpty()) {
            Toast.makeText(ctx, R.string.no_custom_repositories, Toast.LENGTH_SHORT).show()
            showCustomReposDialog = false
        } else {
            AlertDialog(
                onDismissRequest = { showCustomReposDialog = false },
                title = { Text(stringResource(R.string.custom_repositories)) },
                text = {
                    Column {
                        customRepos.forEach { repo ->
                            val name = driverResolver.getCustomRepositoryName(repo).takeIf { it.isNotEmpty() } ?: repo
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodyLarge)
                                    Text(stringResource(R.string.repository_url_format, repo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    showCustomReposDialog = false
                                    repoToDelete = repo to name
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.confirm_deletion), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showCustomReposDialog = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }
    }

    // Диалог подтверждения удаления репозитория
    repoToDelete?.let { (repoUrl, repoName) ->
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            title = { Text(stringResource(R.string.confirm_deletion)) },
            text = { Text(stringResource(R.string.confirm_delete_repository, repoName)) },
            confirmButton = {
                TextButton(onClick = {
                    driverResolver.removeCustomRepository(repoUrl)
                    Toast.makeText(ctx, ctx.getString(R.string.repository_deleted, repoName), Toast.LENGTH_SHORT).show()
                    repoToDelete = null
                    loadDrivers()
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { repoToDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun DriverDownloadCard(
    driver: DriverResolver.DriverInfo,
    isDownloading: Boolean,
    downloadProgress: Int,
    isInstalled: Boolean,
    onDownload: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasDescription = driver.description != null && driver.description.isNotEmpty()
    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(driver.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (isInstalled) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.installed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (driver.version.isNotEmpty()) {
                Text(driver.version, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Кнопка "развернуть" для показа описания
            if (hasDescription) {
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (expanded) "Скрыть описание" else "Показать описание",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(driver.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (isDownloading) {
                LinearProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("$downloadProgress%", style = MaterialTheme.typography.bodySmall)
            } else if (isInstalled) {
                // Драйвер уже установлен — показываем неактивную кнопку
                GlassPill(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                ) {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.installed))
                }
            } else {
                GlassSolidPill(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.downloading_driver))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var repoUrl by remember { mutableStateOf("") }
    var repoName by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    fun extractRepoName(url: String): String {
        val normalized = url.replace(Regex("^(https?://)?(www\\.)?github\\.com/"), "").trimEnd('/')
        val parts = normalized.split("/")
        return if (parts.size >= 2) parts[1] else ""
    }

    fun isValidGitHubRepo(url: String): Boolean {
        return url.matches(Regex("^[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$")) ||
            url.matches(Regex("^(https?://)?(www\\.)?github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+/?$"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_custom_repository)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = {
                        repoUrl = it
                        if (repoName.isEmpty()) repoName = extractRepoName(it)
                    },
                    label = { Text(stringResource(R.string.repository_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                ,
                    shape = GlassFieldShape,
                    colors = glassFieldColors())
                OutlinedTextField(
                    value = repoName,
                    onValueChange = { repoName = it },
                    label = { Text(stringResource(R.string.repository_name_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                ,
                    shape = GlassFieldShape,
                    colors = glassFieldColors())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = repoUrl.trim()
                if (url.isEmpty()) {
                    Toast.makeText(ctx, R.string.repo_url_required, Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                if (!isValidGitHubRepo(url)) {
                    Toast.makeText(ctx, R.string.invalid_repo_format, Toast.LENGTH_LONG).show()
                    return@TextButton
                }
                val name = repoName.trim().ifEmpty { extractRepoName(url) }
                onAdd(url, name)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// Извлечение модели GPU из строки рендерера (как extractGpuModel в оригинале)
private fun extractGpuModel(renderer: String?): String? {
    if (renderer.isNullOrEmpty()) return null
    val lower = renderer.lowercase(Locale.ENGLISH)
    if (lower.contains("adreno")) {
        val pattern = Regex("adreno[^0-9]*([0-9]{3})")
        val matcher = pattern.find(lower)
        if (matcher != null) return "Adreno ${matcher.groupValues[1]}"
    }
    return renderer
}

private fun isDriverMatching(installedName: String, storeName: String): Boolean {
    val clean = { s: String ->
        s.lowercase()
         .replace("gmem", "")
         .replace("sysmem", "")
         .replace(Regex("[^a-z0-9]"), "")
    }
    
    val cleanInst = clean(installedName)
    val cleanStore = clean(storeName)
    
    if (cleanInst.isEmpty() || cleanStore.isEmpty()) return false
    
    if (cleanInst == cleanStore || cleanStore.contains(cleanInst) || cleanInst.contains(cleanStore)) {
        return true
    }
    
    val cleanInstNoV = cleanInst.replace("v", "")
    val cleanStoreNoV = cleanStore.replace("v", "")
    return cleanInstNoV.contains(cleanStoreNoV) || cleanStoreNoV.contains(cleanInstNoV)
}
