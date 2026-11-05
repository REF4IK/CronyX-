package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassSliderColors
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import com.winlator.cmod.ui.components.GlassSwitch
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.DriverResolver
import com.winlator.cmod.core.GPUInformation
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.StringUtils
import org.json.JSONArray
import org.json.JSONObject
import com.winlator.cmod.widget.SeekBar
import android.util.Log
import android.net.Uri
import androidx.preference.PreferenceManager
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.AdrenotoolsManager
import com.winlator.cmod.contents.Downloader
import com.winlator.cmod.contentdialog.DriverDownloadDialog
import com.winlator.cmod.core.DefaultVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle

// =====================================================================
// Audio Driver Config Dialog (Compose)
// =====================================================================

/**
 * Compose-версия AudioDriverConfigDialog.
 * Поля: performanceMode, volume, latencyMillis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDriverConfigDialogCompose(
    initialConfig: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Парсим конфиг
    val initial = remember(initialConfig) { parseKeyValueSet(initialConfig.ifEmpty { "performanceMode=1,volume=1.0,latencyMillis=20" }) }
    var performanceMode by remember { mutableStateOf(initial["performanceMode"]?.toIntOrNull() ?: 1) }
    var volume by remember { mutableStateOf(((initial["volume"]?.toFloatOrNull() ?: 1.0f) * 100f).toInt()) }
    var latencyIndex by remember {
        mutableStateOf(findClosestLatencyIndex(initial["latencyMillis"]?.toIntOrNull() ?: 20))
    }

    val latencyValues = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                border = glassCardBorder(),
            ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Заголовок с иконкой
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.audio_driver) + " " + stringResource(R.string.configuration),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()

                // Performance Mode
                Text(stringResource(R.string.performance_mode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val performanceModes = listOf(
                    stringResource(R.string.high_performance) to 0,
                    stringResource(R.string.low_latency) to 1,
                    stringResource(R.string.medium_latency) to 2,
                )
                val performanceLabels = performanceModes.map { it.first }
                ConfigSpinnerRow(
                    items = performanceLabels,
                    selectedIndex = performanceMode.coerceIn(0, performanceModes.lastIndex),
                    onSelected = { performanceMode = performanceModes[it].second },
                )

                // Volume
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.volume), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("$volume%", style = MaterialTheme.typography.bodyMedium)
                }
                ConfigSeekBar(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0..100,
                )

                // Latency
                Text(stringResource(R.string.latency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = latencyValues.map { "$it мс" },
                    selectedIndex = latencyIndex,
                    onSelected = { latencyIndex = it },
                )

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassSolidPill(
                        onClick = {
                        val newConfig = "performanceMode=$performanceMode,volume=${volume / 100.0f},latencyMillis=${latencyValues[latencyIndex]}"
                        onConfirm(newConfig)
                    },
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

// =====================================================================
// Graphics Driver Config Dialog (Compose)
// =====================================================================

/**
 * Compose-версия GraphicsDriverConfigDialog.
 * Поля: version, blacklistedExtensions, maxDeviceMemory, adrenotoolsTurnip, frameSync,
 * presentMode, resourceType, bcnEmulation, bcnEmulationType, bcnEmulationCache, blit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphicsDriverConfigDialogCompose(
    context: Context,
    graphicsDriver: String,
    initialConfig: String,
    availableVersions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initial = remember(initialConfig) { parseKeyValueSet(initialConfig) }
    var selectedVersion by remember { mutableStateOf(initial["version"] ?: "System") }
    var vulkanVersion by remember { mutableStateOf(initial["vulkanVersion"] ?: "1.3") }
    var maxDeviceMemory by remember { mutableStateOf(initial["maxDeviceMemory"] ?: "0") }
    var frameSync by remember { mutableStateOf(initial["frameSync"] ?: "Normal") }
    var presentMode by remember { mutableStateOf(initial["presentMode"] ?: "mailbox") }
    var resourceType by remember { mutableStateOf(initial["resourceType"] ?: "auto") }
    var bcnEmulation by remember { mutableStateOf(initial["bcnEmulation"] ?: "auto") }
    var bcnEmulationType by remember { mutableStateOf(initial["bcnEmulationType"] ?: "compute") }
    var bcnEmulationCache by remember { mutableStateOf(initial["bcnEmulationCache"] ?: "0") }
    var adrenotoolsTurnip by remember { mutableStateOf(initial["adrenotoolsTurnip"] != "0") }
    var enableBlit by remember { mutableStateOf(initial["blit"] == "1") }
    var enableTurbo by remember { mutableStateOf(initial["turbo"] == "1") }
    var selectedGpuName by remember { mutableStateOf(initial["gpuName"] ?: "Device") }
    var gpuCustomName by remember { mutableStateOf(initial["gpuCustomName"] ?: "Custom GPU") }
    var gpuCustomDeviceId by remember { mutableStateOf(initial["gpuCustomDeviceId"] ?: "10DE") }
    var gpuCustomVendorId by remember { mutableStateOf(initial["gpuCustomVendorId"] ?: "13C2") }
    val gpuNames = remember {
        try {
            val raw = FileUtils.readString(context, "gpu_cards.json")
            val arr = JSONArray(raw)
            listOf("Device", "Custom") + (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
        } catch (e: Exception) { listOf("Device", "Custom") }
    }

    // Исходный blacklist из конфига контейнера — нужен для восстановления при возврате к исходному драйверу
    val initialBlacklist = remember(initialConfig) { initial["blacklistedExtensions"] ?: "" }
    val initialVersion = remember(initialConfig) { initial["version"] ?: "System" }

    // Динамический список расширений для выбранного драйвера
    // essentialExtensions исключаются — они критичны для работы wrapper'а
    val essentialExtensions = remember {
        setOf(
            "VK_GOOGLE_display_timing",
            "VK_KHR_shader_float_controls",
            "VK_KHR_shader_presentable_image",
            "VK_EXT_image_compression_control_swapchain",
        )
    }

    // Состояние: множество расширений, выключенных пользователем (blacklist)
    val blacklistedExtensions = remember { mutableStateOf(initialBlacklist) }

    // Текущий список доступных расширений (загружается асинхронно при смене драйвера)
    var availableExtensions by remember { mutableStateOf<List<String>>(emptyList()) }
    var extensionsLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Функция загрузки расширений для драйвера. System → системный enumerateExtensions,
    // иначе — динамическая загрузка через adrenotools (enumerateExtensionsWithDriver).
    val loadExtensions: (String) -> Unit = remember(essentialExtensions) { { driverName ->
        extensionsLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val loaded: List<String> = try {
                val raw: Array<String> = if (driverName.isEmpty() || driverName.equals("System", ignoreCase = true)) {
                    GPUInformation.enumerateExtensions()
                } else {
                    GPUInformation.enumerateExtensions(driverName, context)
                }
                raw.filter { it !in essentialExtensions }
            } catch (t: Throwable) {
                Log.e("GraphicsDriverDialog", "Failed to load extensions for driver '$driverName', fallback to system", t)
                try {
                    GPUInformation.enumerateExtensions().toList().filter { it !in essentialExtensions }
                } catch (t2: Throwable) {
                    Log.e("GraphicsDriverDialog", "System enumerateExtensions also failed", t2)
                    emptyList()
                }
            }
            withContext(Dispatchers.Main) {
                availableExtensions = loaded
                extensionsLoading = false

                // Восстановление blacklist:
                // - Если вернулись к исходному драйверу — восстанавливаем исходный blacklist из конфига
                // - Иначе — сохраняем blacklist только тех расширений, которые присутствуют в новом списке
                val restoredBlacklist: String = if (initialVersion == driverName) {
                    initialBlacklist
                } else {
                    val currentSet = blacklistedExtensions.value.split(",").filter { it.isNotEmpty() }.toSet()
                    currentSet.filter { it in loaded }.joinToString(",")
                }
                blacklistedExtensions.value = restoredBlacklist
            }
        }
    } }

    // Первичная загрузка расширений при открытии диалога + при смене драйвера
    LaunchedEffect(selectedVersion) {
        loadExtensions(selectedVersion)
    }

    var versions by remember {
        mutableStateOf(
            (context.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries).toList() +
             AdrenotoolsManager(context).enumarateInstalledDrivers()).distinct()
        )
    }
    val frameSyncOptions = listOf("Normal", "Always", "Never")
    val presentModeOptions = listOf("mailbox", "fifo", "immediate", "relaxed")
    val resourceTypeOptions = listOf("auto", "vk_memory", "dumb", "shared")
    val bcnEmulationOptions = listOf("auto", "disabled", "enable")
    val bcnEmulationTypeOptions = listOf("compute", "copy", "compute_copy")
    val bcnEmulationCacheOptions = listOf("0", "1")
    val maxMemoryOptions = (0..6).map { (it * 1024).toString() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(8.dp),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                border = glassCardBorder(),
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.graphics_driver_configuration),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()

                // Vulkan Version
                val vulkanVersionOptions = remember { context.resources.getStringArray(R.array.vulkan_version_entries).toList() }
                Text(stringResource(R.string.graphics_driver_vulkan_version), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = vulkanVersionOptions,
                    selectedIndex = vulkanVersionOptions.indexOf(vulkanVersion).coerceAtLeast(0),
                    onSelected = { vulkanVersion = vulkanVersionOptions[it] },
                )

                // GPU Name — подмена названия видеокарты
                Text("GPU Name", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = gpuNames,
                    selectedIndex = gpuNames.indexOf(selectedGpuName).coerceAtLeast(0),
                    onSelected = { selectedGpuName = gpuNames[it] },
                )

                if (selectedGpuName == "Custom") {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                        Text("Custom Device Name", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = gpuCustomName,
                            onValueChange = { gpuCustomName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,

                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Device ID", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = gpuCustomDeviceId,
                                    onValueChange = { gpuCustomDeviceId = it.take(8) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("10DE") },

                                    shape = GlassFieldShape,
                                    colors = glassFieldColors())
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Vendor ID", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = gpuCustomVendorId,
                                    onValueChange = { gpuCustomVendorId = it.take(8) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("13C2") },

                                    shape = GlassFieldShape,
                                    colors = glassFieldColors())
                            }
                        }
                    }
                }

                // Version
                Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = versions,
                    selectedIndex = versions.indexOf(selectedVersion).coerceAtLeast(0),
                    onSelected = {
                        selectedVersion = versions[it]
                    },
                )

                var showDownloadListDialog by remember { mutableStateOf(false) }
                var selectedRepo by remember { mutableStateOf<String?>(null) }
                var downloadableDrivers by remember { mutableStateOf<List<DriverResolver.DriverInfo>>(emptyList()) }
                var isLoadingRemote by remember { mutableStateOf(false) }

                var showProgressDialog by remember { mutableStateOf(false) }
                var progressPercent by remember { mutableStateOf(0) }
                var progressMessage by remember { mutableStateOf("") }

                val adrenotoolsManager = remember { AdrenotoolsManager(context) }
                var installedDriverNames by remember { mutableStateOf<Set<String>>(emptySet()) }
                var installedDriverUrls by remember { mutableStateOf<Set<String>>(emptySet()) }

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

                LaunchedEffect(showDownloadListDialog) {
                    if (showDownloadListDialog) {
                        refreshInstalledDrivers()
                    }
                }

                // Download button (реальное скачивание Turnip/драйверов через Compose-диалоги)
                GlassSolidPill(
                    onClick = {
                        isLoadingRemote = true
                        val resolver = DriverResolver(context)
                        coroutineScope.launch(Dispatchers.IO) {
                            resolver.searchDrivers(object : DriverResolver.DriverSearchCallback {
                                override fun onDriversFound(drivers: List<DriverResolver.DriverInfo>) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        isLoadingRemote = false
                                        if (drivers.isEmpty()) {
                                            AppUtils.showToast(context, context.getString(R.string.no_driver_versions_available))
                                        } else {
                                            downloadableDrivers = drivers
                                            showDownloadListDialog = true
                                        }
                                    }
                                }
                                override fun onError(error: String) {
                                    coroutineScope.launch(Dispatchers.Main) {
                                        isLoadingRemote = false
                                        AppUtils.showToast(context, context.getString(R.string.download_error, error))
                                    }
                                }
                            })
                        }
                    },
                    enabled = !isLoadingRemote,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoadingRemote) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.download_graphics_drivers))
                    }
                }

                // Dialog list of downloadable graphics drivers
                if (showDownloadListDialog) {
                    val grouped = remember(downloadableDrivers) { downloadableDrivers.groupBy { it.repoName } }
                    val resolver = remember(context) { DriverResolver(context) }
                    AlertDialog(
                        onDismissRequest = { 
                            showDownloadListDialog = false
                            selectedRepo = null
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectedRepo != null) {
                                    IconButton(onClick = { selectedRepo = null }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (selectedRepo != null) {
                                        resolver.getRepoDisplayName(selectedRepo)
                                    } else {
                                        stringResource(R.string.download_graphics_driver)
                                    }
                                )
                            }
                        },
                        text = {
                            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp)) {
                                val scrollState = rememberScrollState()
                                Column(modifier = Modifier.verticalScroll(scrollState)) {
                                    if (selectedRepo == null) {
                                        grouped.forEach { (repoName, drivers) ->
                                            val displayName = resolver.getRepoDisplayName(repoName)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedRepo = repoName }
                                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = displayName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${drivers.size} drivers available",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            HorizontalDivider()
                                        }
                                    } else {
                                        val drivers = grouped[selectedRepo] ?: emptyList()
                                        drivers.forEach { driver ->
                                            val isInstalled = installedDriverUrls.contains(driver.downloadUrl) || installedDriverNames.any { inst ->
                                                isDriverMatching(inst, driver.name)
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = !isInstalled) {
                                                        showDownloadListDialog = false
                                                        selectedRepo = null
                                                        showProgressDialog = true
                                                        progressMessage = context.getString(R.string.downloading_driver_message, driver.name)
                                                        progressPercent = 0
 
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            resolver.downloadDriver(driver, object : DriverResolver.DriverDownloadCallback {
                                                                override fun onProgress(progress: Int) {
                                                                    coroutineScope.launch(Dispatchers.Main) {
                                                                        progressPercent = progress
                                                                    }
                                                                }
                                                                override fun onComplete(driverUri: Uri?) {
                                                                    coroutineScope.launch(Dispatchers.Main) {
                                                                        progressMessage = context.getString(R.string.installing, driver.name)
                                                                    }
                                                                    coroutineScope.launch(Dispatchers.IO) {
                                                                        try {
                                                                            val adrenotools = AdrenotoolsManager(context)
                                                                            val installedDriverId = adrenotools.installDriver(driverUri)
                                                                            if (!installedDriverId.isNullOrEmpty()) {
                                                                                adrenotools.writeStoreInfo(installedDriverId, driver.name, driver.version, driver.downloadUrl)
                                                                            }
                                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                                showProgressDialog = false
                                                                                if (!installedDriverId.isNullOrEmpty()) {
                                                                                    AppUtils.showToast(context, context.getString(R.string.driver_installed_successfully, driver.name))
                                                                                    val contentsManager = ContentsManager(context)
                                                                                    contentsManager.syncContents()
                                                                                    versions = (context.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries).toList() +
                                                                                                 AdrenotoolsManager(context).enumarateInstalledDrivers()).distinct()
                                                                                    selectedVersion = installedDriverId
                                                                                    refreshInstalledDrivers()
                                                                                } else {
                                                                                    AppUtils.showToast(context, context.getString(R.string.driver_installation_failed))
                                                                                }
                                                                            }
                                                                        } catch (e: Exception) {
                                                                            coroutineScope.launch(Dispatchers.Main) {
                                                                                showProgressDialog = false
                                                                                AppUtils.showToast(context, context.getString(R.string.installation_error, e.message))
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                                override fun onError(error: String) {
                                                                    coroutineScope.launch(Dispatchers.Main) {
                                                                        showProgressDialog = false
                                                                        AppUtils.showToast(context, context.getString(R.string.download_error, error))
                                                                    }
                                                                }
                                                            })
                                                        }
                                                    }
                                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = driver.name,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "Version: ${driver.version}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                }
                                                if (isInstalled) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Filled.CheckCircle,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = stringResource(R.string.installed),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Filled.Download,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                     )
                                                }
                                            }
                                            HorizontalDivider()
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { 
                                showDownloadListDialog = false
                                selectedRepo = null
                            }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // Progress Loading Dialog
                if (showProgressDialog) {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                    ) {
                        Surface(shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                tonalElevation = 0.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                border = glassCardBorder(),
            ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(text = progressMessage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    LinearProgressIndicator(
                                        progress = { progressPercent / 100f },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(text = "$progressPercent%", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Remote fetch Loading dialog (visual indicator)
                if (isLoadingRemote) {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                    ) {
                        Surface(shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                tonalElevation = 0.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                border = glassCardBorder(),
            ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(text = stringResource(R.string.loading), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                // Available extensions — динамический выпадающий список с чекбоксами
                Text(stringResource(R.string.available_extensions), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ExtensionsDropdownRow(
                    availableExtensions = availableExtensions,
                    blacklistedExtensions = blacklistedExtensions,
                    loading = extensionsLoading,
                )

                // Max Device Memory
                Text(stringResource(R.string.max_device_memory), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = maxMemoryOptions,
                    selectedIndex = maxMemoryOptions.indexOf(maxDeviceMemory).coerceAtLeast(0),
                    onSelected = { maxDeviceMemory = maxMemoryOptions[it] },
                )

                // Frame Sync
                Text(stringResource(R.string.frame_synchronization), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = frameSyncOptions,
                    selectedIndex = frameSyncOptions.indexOf(frameSync).coerceAtLeast(0),
                    onSelected = { frameSync = frameSyncOptions[it] },
                )

                // Present Mode
                Text(stringResource(R.string.present_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = presentModeOptions,
                    selectedIndex = presentModeOptions.indexOf(presentMode).coerceAtLeast(0),
                    onSelected = { presentMode = presentModeOptions[it] },
                )

                // Resource Type
                Text(stringResource(R.string.resource_type), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = resourceTypeOptions,
                    selectedIndex = resourceTypeOptions.indexOf(resourceType).coerceAtLeast(0),
                    onSelected = { resourceType = resourceTypeOptions[it] },
                )

                // BCn Emulation
                Text("BCn Emulation", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = bcnEmulationOptions,
                    selectedIndex = bcnEmulationOptions.indexOf(bcnEmulation).coerceAtLeast(0),
                    onSelected = { bcnEmulation = bcnEmulationOptions[it] },
                )

                // BCn Emulation Type
                Text("BCn Emulation Type", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = bcnEmulationTypeOptions,
                    selectedIndex = bcnEmulationTypeOptions.indexOf(bcnEmulationType).coerceAtLeast(0),
                    onSelected = { bcnEmulationType = bcnEmulationTypeOptions[it] },
                )

                // BCn Emulation Cache
                Text("BCn Emulation Cache", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = bcnEmulationCacheOptions,
                    selectedIndex = bcnEmulationCacheOptions.indexOf(bcnEmulationCache).coerceAtLeast(0),
                    onSelected = { bcnEmulationCache = bcnEmulationCacheOptions[it] },
                )

                // Adrenotools Turnip
                ConfigSwitchRow(stringResource(R.string.adrenotools_turnip), adrenotoolsTurnip) { adrenotoolsTurnip = it }

                // Enable Blit
                ConfigSwitchRow(stringResource(R.string.enable_blit), enableBlit) { enableBlit = it }

                // Turbo Mode — через adrenotools, без root
                ConfigSwitchRow("Turbo Mode", enableTurbo) { enableTurbo = it }

                // Blacklisted extensions
                if (blacklistedExtensions.value.isNotEmpty()) {
                    Text("Blacklisted: ${blacklistedExtensions.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                }
                // Bottom fixed buttons
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassSolidPill(
                        onClick = {
                        val newConfig = buildString {
                            append("version=$selectedVersion")
                            append(";vulkanVersion=$vulkanVersion")
                            append(";blacklistedExtensions=${blacklistedExtensions.value}")
                            append(";maxDeviceMemory=$maxDeviceMemory")
                            append(";adrenotoolsTurnip=${if (adrenotoolsTurnip) "1" else "0"}")
                            append(";frameSync=$frameSync")
                            append(";presentMode=$presentMode")
                            append(";resourceType=$resourceType")
                            append(";bcnEmulation=$bcnEmulation")
                            append(";bcnEmulationType=$bcnEmulationType")
                            append(";bcnEmulationCache=$bcnEmulationCache")
                            append(";blit=${if (enableBlit) "1" else "0"}")
                            append(";turbo=${if (enableTurbo) "1" else "0"}")
                            append(";gpuName=$selectedGpuName")
                            if (selectedGpuName == "Custom") {
                                append(";gpuCustomName=$gpuCustomName")
                                append(";gpuCustomDeviceId=$gpuCustomDeviceId")
                                append(";gpuCustomVendorId=$gpuCustomVendorId")
                            }
                        }
                        // Turbo Mode — применяется сразу через adrenotools
                        GPUInformation.setTurboMode(enableTurbo)
                        onConfirm(newConfig)
                    },
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

// =====================================================================
// VKD3D Config Dialog (Compose)
// =====================================================================

/**
 * Compose-версия VKD3DConfigDialog.
 * Поля: vkd3dVersion, vkd3dLevel (Feature Level).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VKD3DConfigDialogCompose(
    context: Context,
    initialConfig: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initial = remember(initialConfig) { parseKeyValueSet(initialConfig) }
    var version by remember(initialConfig) { mutableStateOf(initial["vkd3dVersion"] ?: DefaultVersion.VKD3D) }
    var featureLevel by remember(initialConfig) { mutableStateOf(initial["vkd3dLevel"] ?: "12_1") }

    val VKD3D_FEATURE_LEVEL = listOf("12_0", "12_1", "12_2", "11_1", "11_0", "10_1", "10_0", "9_3", "9_2", "9_1")

    val contentsManager = remember { ContentsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // Списки для версий: displayLabels для показа, versionIdentifiers для сохранения
    val versionLabels = remember(initialConfig) {
        mutableStateOf(run {
            val cm = ContentsManager(context)
            cm.syncContents()
            val labels = mutableListOf<String>()
            // Добавляем встроенные версии из ресурсов
            context.resources.getStringArray(R.array.vkd3d_version_entries).forEach { v ->
                labels.add(v)
            }
            // Добавляем установленные профили VKD3D
            cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VKD3D).forEach { profile ->
                val label = profile.verName
                if (labels.none { it == label }) {
                    labels.add(label)
                }
            }
            val cfgVersion = parseKeyValueSet(initialConfig)["vkd3dVersion"] ?: ""
            if (cfgVersion.isNotEmpty()) {
                // Извлекаем display name из идентификатора (до "-")
                val displayName = cfgVersion.substringBeforeLast('-')
                if (displayName.isNotEmpty() && labels.none { it == displayName }) {
                    labels.add(displayName)
                }
            }
            labels
        })
    }

    // Отображаем в спинере display name, но сохраняем полный идентификатор
    val currentVersionDisplay = version.substringBeforeLast('-')

    var showDownloadListDialog by remember { mutableStateOf(false) }
    var downloadableProfiles by remember { mutableStateOf<List<ContentProfile>>(emptyList()) }
    var installedVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }

    var showProgressDialog by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                border = glassCardBorder(),
            ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Заголовок
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    Text("VKD3D " + stringResource(R.string.configuration), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()

                // Version
                Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = versionLabels.value,
                    selectedIndex = versionLabels.value.indexOfFirst { it.equals(currentVersionDisplay, ignoreCase = true) }.coerceAtLeast(0),
                    onSelected = { idx ->
                        val label = versionLabels.value[idx]
                        // Ищем соответствующий идентификатор
                        val cm = ContentsManager(context)
                        cm.syncContents()
                        var found = false
                        // Проверяем встроенные: идентификатор = label + "-0"
                        for (v in context.resources.getStringArray(R.array.vkd3d_version_entries)) {
                            if (v == label) {
                                version = "$v-0"
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            // Проверяем установленные профили
                            for (profile in cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VKD3D)) {
                                if (profile.verName == label) {
                                    version = profile.verName + "-" + profile.verCode
                                    found = true
                                    break
                                }
                            }
                        }
                        if (!found) {
                            version = "$label-0"
                        }
                    },
                )

                // Download VKD3D button
                GlassSolidPill(
                    onClick = {
                        isLoadingRemote = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val sp = MmkvPreferences()
                                val contentsURL = sp.getString("downloadable_contents_url",
                                    "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json")
                                val json = Downloader.downloadString(contentsURL)
                                if (json != null) {
                                    contentsManager.setRemoteProfiles(json)

                                    val allProfiles = contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VKD3D)
                                    val installed = allProfiles.filter { it.remoteUrl == null || it.remoteUrl.isEmpty() }
                                        .map { it.verName + "_v" + it.verCode }

                                    val downloadable = allProfiles.filter { it.remoteUrl != null && it.remoteUrl.isNotEmpty() }

                                    withContext(Dispatchers.Main) {
                                        downloadableProfiles = downloadable
                                        installedVersions = installed
                                        if (downloadable.isEmpty()) {
                                            AppUtils.showToast(context, context.getString(R.string.all_vkd3d_installed))
                                        } else {
                                            showDownloadListDialog = true
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        AppUtils.showToast(context, context.getString(R.string.failed_to_load_remote_contents))
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    AppUtils.showToast(context, context.getString(R.string.install_failed) + ": " + e.message)
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoadingRemote = false
                                }
                            }
                        }
                    },
                    enabled = !isLoadingRemote,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoadingRemote) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.download_vkd3d))
                    }
                }

                // Dialog list of downloadable VKD3D versions
                if (showDownloadListDialog) {
                    AlertDialog(
                        onDismissRequest = { showDownloadListDialog = false },
                        title = { Text(context.getString(R.string.download_vkd3d_title, downloadableProfiles.size)) },
                        text = {
                            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp)) {
                                val scrollState = rememberScrollState()
                                Column(modifier = Modifier.verticalScroll(scrollState)) {
                                    downloadableProfiles.forEach { profile ->
                                        val versionKey = profile.verName + "_v" + profile.verCode
                                        val isInstalled = installedVersions.contains(versionKey)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isInstalled) {
                                                    showDownloadListDialog = false
                                                    showProgressDialog = true
                                                    progressMessage = context.getString(R.string.downloading, profile.verName)

                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        try {
                                                            val timestamp = System.currentTimeMillis()
                                                            val tempFile = File(context.cacheDir, "vkd3d_temp_${timestamp}.tar.xz")
                                                            val downloaded = Downloader.downloadFile(profile.remoteUrl, tempFile)
                                                            if (!downloaded || !tempFile.exists()) {
                                                                withContext(Dispatchers.Main) {
                                                                    showProgressDialog = false
                                                                    AppUtils.showToast(context, context.getString(R.string.failed_to_download, profile.verName))
                                                                }
                                                                return@launch
                                                            }

                                                            withContext(Dispatchers.Main) {
                                                                progressMessage = context.getString(R.string.installing, profile.verName)
                                                            }

                                                            withContext(Dispatchers.Main) {
                                                                val callback = object : ContentsManager.OnInstallFinishedCallback {
                                                                    var isExtracting = true
                                                                    override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                                                                        showProgressDialog = false
                                                                        val errorMsgResId = when (reason) {
                                                                            ContentsManager.InstallFailedReason.ERROR_BADTAR -> R.string.file_cannot_be_recognized
                                                                            ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> R.string.profile_not_found_in_content
                                                                            ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> R.string.profile_cannot_be_recognized
                                                                            ContentsManager.InstallFailedReason.ERROR_EXIST -> R.string.content_already_exist
                                                                            ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> R.string.content_is_incomplete
                                                                            ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> R.string.content_cannot_be_trusted
                                                                            else -> R.string.unable_to_install_content
                                                                        }
                                                                        AppUtils.showToast(context, context.getString(R.string.install_failed) + ": " + context.getString(errorMsgResId))
                                                                        if (tempFile.exists()) tempFile.delete()
                                                                    }
                                                                    override fun onSucceed(installedProfile: ContentProfile) {
                                                                        if (isExtracting) {
                                                                            isExtracting = false
                                                                            contentsManager.finishInstallContent(installedProfile, this)
                                                                        } else {
                                                                            showProgressDialog = false
                                                                            AppUtils.showToast(context, context.getString(R.string.installed_successfully, installedProfile.verName))
                                                                            contentsManager.syncContents()

                                                                            // Обновляем список версий
                                                                            val newLabels = mutableListOf<String>()
                                                                            context.resources.getStringArray(R.array.vkd3d_version_entries).forEach { v ->
                                                                                newLabels.add(v)
                                                                            }
                                                                            contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VKD3D).forEach { p ->
                                                                                if (newLabels.none { it == p.verName }) {
                                                                                    newLabels.add(p.verName)
                                                                                }
                                                                            }
                                                                            versionLabels.value = newLabels
                                                                            version = installedProfile.verName + "-" + installedProfile.verCode

                                                                            if (tempFile.exists()) tempFile.delete()
                                                                        }
                                                                    }
                                                                }
                                                                contentsManager.extraContentFile(Uri.fromFile(tempFile), callback)
                                                            }
                                                        } catch (e: Exception) {
                                                            withContext(Dispatchers.Main) {
                                                                    showProgressDialog = false
                                                                    AppUtils.showToast(context, context.getString(R.string.install_failed) + ": " + e.message)
                                                            }
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (isInstalled) "${profile.verName} (${context.getString(R.string.installed)})" else profile.verName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (profile.desc != null && profile.desc.isNotEmpty()) {
                                                    Text(
                                                        text = profile.desc,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = null,
                                                tint = if (isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showDownloadListDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // Progress Loading Dialog
                if (showProgressDialog) {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                    ) {
                        Surface(shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                tonalElevation = 0.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                border = glassCardBorder(),
            ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(text = progressMessage, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                // Feature Level
                Text(stringResource(R.string.vkd3d_feature_level), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = VKD3D_FEATURE_LEVEL,
                    selectedIndex = VKD3D_FEATURE_LEVEL.indexOfFirst { it == featureLevel }.coerceAtLeast(0),
                    onSelected = { featureLevel = VKD3D_FEATURE_LEVEL[it] },
                )

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassSolidPill(
                        onClick = {
                        // Сохраняем только vkd3dVersion и vkd3dLevel, остальные ключи не трогаем
                        val oldConfig = parseKeyValueSet(initialConfig)
                        val newConfig = buildString {
                            // Сначала сохраняем старые DXVK-ключи (если были)
                            var first = true
                            for ((key, value) in oldConfig) {
                                if (key != "vkd3dVersion" && key != "vkd3dLevel") {
                                    if (!first) append(",")
                                    append("$key=$value")
                                    first = false
                                }
                            }
                            // Добавляем VKD3D ключи
                            if (!first) append(",")
                            append("vkd3dVersion=$version")
                            append(",vkd3dLevel=$featureLevel")
                        }
                        onConfirm(newConfig)
                    },
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

// =====================================================================
// DXVK Config Dialog (Compose)
// =====================================================================

/**
 * Compose-версия DXVKConfigDialog.
 * Поля: version, framerate, maxDeviceMemory, async, asyncCache, tearFree, maxFrameLatency,
 * tilerMode, deferSurfaceCreation, disableMsaa, maxTessFactor, syncInterval, maxFeatureLevel,
 * forceRefreshRate, shaderModel, floatEmulation, samplerAnisotropy, enableMemoryDefrag,
 * lowerSinCos, clampNegativeLodBias, samplerLodBias, numCompilerThreads,
 * enableGraphicsPipelineLibrary, relaxedBarriers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DXVKConfigDialogCompose(
    context: Context,
    initialConfig: String,
    availableVersions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initial = remember(initialConfig) { parseKeyValueSet(initialConfig) }
    var version by remember(initialConfig) { mutableStateOf(initial["version"] ?: "1.10.1") }
    var framerate by remember { mutableStateOf(initial["framerate"] ?: "0") }
    var maxDeviceMemory by remember { mutableStateOf(initial["maxDeviceMemory"] ?: "0") }
    var async by remember { mutableStateOf(initial["async"] == "1") }
    var asyncCache by remember { mutableStateOf(initial["asyncCache"] == "1") }
    var tearFree by remember { mutableStateOf(initial["tearFree"] ?: "Auto") }
    var maxFrameLatency by remember { mutableStateOf(initial["maxFrameLatency"] ?: "0") }
    var tilerMode by remember { mutableStateOf(initial["tilerMode"] ?: "Auto") }
    var deferSurfaceCreation by remember { mutableStateOf(initial["deferSurfaceCreation"] == "1") }
    var disableMsaa by remember { mutableStateOf(initial["disableMsaa"] == "1") }
    var maxTessFactor by remember { mutableStateOf(initial["maxTessFactor"] ?: "0") }
    var syncInterval by remember { mutableStateOf(initial["syncInterval"] ?: "-1") }
    var maxFeatureLevel by remember { mutableStateOf(initial["maxFeatureLevel"] ?: "12_1") }
    var forceRefreshRate by remember { mutableStateOf(initial["forceRefreshRate"] ?: "0") }
    var shaderModel by remember { mutableStateOf(initial["shaderModel"] ?: "3") }
    var floatEmulation by remember { mutableStateOf(initial["floatEmulation"] ?: "Auto") }
    var samplerAnisotropy by remember { mutableStateOf(initial["samplerAnisotropy"] ?: "-1") }
    var enableMemoryDefrag by remember { mutableStateOf(initial["enableMemoryDefrag"] ?: "Auto") }
    var lowerSinCos by remember { mutableStateOf(initial["lowerSinCos"] ?: "Auto") }
    var clampNegativeLodBias by remember { mutableStateOf(initial["clampNegativeLodBias"] ?: "Auto") }
    var samplerLodBias by remember { mutableStateOf(initial["samplerLodBias"] ?: "0.0") }
    var numCompilerThreads by remember { mutableStateOf(initial["numCompilerThreads"] ?: "0") }
    var enableGraphicsPipelineLibrary by remember { mutableStateOf(initial["enableGraphicsPipelineLibrary"] ?: "Auto") }
    var relaxedBarriers by remember { mutableStateOf(initial["relaxedBarriers"] ?: "Auto") }

    // DXVK type: 0=none, 1=async, 2=gplasync — controls async/asyncCache visibility
    val dxvkType = if (version.contains("gplasync")) 2 else if (version.contains("async")) 1 else 0
    val showAsync = dxvkType != 0
    val showAsyncCache = dxvkType == 2
    LaunchedEffect(version) {
        if (!showAsync) { async = false; asyncCache = false }
        else if (!showAsyncCache) { asyncCache = false }
    }

    val contentsManager = remember { ContentsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var versions by remember(initialConfig) {
        mutableStateOf(run {
            val cm = ContentsManager(context)
            cm.syncContents()
            val originalItems = context.resources.getStringArray(R.array.dxvk_version_entries).toList()
            val installedProfilesList = (cm.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK) ?: emptyList())
                .filter { it.remoteUrl == null || it.remoteUrl.isEmpty() }
                .map {
                    val entryName = ContentsManager.getEntryName(it)
                    val firstDashIndex = entryName.indexOf('-')
                    if (firstDashIndex >= 0) entryName.substring(firstDashIndex + 1) else entryName
                }
            val all = (originalItems + installedProfilesList).distinct()
            val cfgVersion = parseKeyValueSet(initialConfig)["version"] ?: ""
            if (cfgVersion.isNotEmpty() && !all.contains(cfgVersion)) all + cfgVersion else all
        })
    }

    var showDownloadListDialog by remember { mutableStateOf(false) }
    var downloadableProfiles by remember { mutableStateOf<List<ContentProfile>>(emptyList()) }
    var installedVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }

    var showProgressDialog by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }

    val autoOnOff = listOf("Auto", "True", "False")
    val numOptions = (0..8).map { it.toString() }
    val syncIntervalOptions = listOf("-1", "0", "1", "2", "3", "4")
    val featureLevelOptions = listOf("12_1", "12_0", "11_1", "11_0", "10_0")
    val refreshRateOptions = listOf("0", "30", "60", "75", "90", "120", "144", "165")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).padding(4.dp),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                border = glassCardBorder(),
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                        Text("DXVK " + stringResource(R.string.configuration), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                HorizontalDivider()

                // Version
                Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = versions,
                    selectedIndex = versions.indexOfFirst { it.equals(version, ignoreCase = true) || it.startsWith(version) }.coerceAtLeast(0),
                    onSelected = { version = versions[it] },
                )
                GlassSolidPill(
                    onClick = {
                        isLoadingRemote = true
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val sp = MmkvPreferences()
                                val contentsURL = sp.getString("downloadable_contents_url",
                                    "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json")
                                val json = Downloader.downloadString(contentsURL)
                                if (json != null) {
                                    contentsManager.setRemoteProfiles(json)

                                    val allProfiles = contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK)
                                    val installed = allProfiles.filter { it.remoteUrl == null || it.remoteUrl.isEmpty() }
                                        .map { it.verName + "_v" + it.verCode }

                                    val downloadable = allProfiles.filter { it.remoteUrl != null && it.remoteUrl.isNotEmpty() }

                                    withContext(Dispatchers.Main) {
                                        downloadableProfiles = downloadable
                                        installedVersions = installed
                                        if (downloadable.isEmpty()) {
                                            AppUtils.showToast(context, context.getString(R.string.all_dxvk_installed))
                                        } else {
                                            showDownloadListDialog = true
                                        }
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        AppUtils.showToast(context, context.getString(R.string.failed_to_load_remote_contents))
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    AppUtils.showToast(context, context.getString(R.string.install_failed) + ": " + e.message)
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoadingRemote = false
                                }
                            }
                        }
                    },
                    enabled = !isLoadingRemote,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoadingRemote) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.download_dxvk))
                    }
                }

                // Dialog list of downloadable DXVK versions
                if (showDownloadListDialog) {
                    AlertDialog(
                        onDismissRequest = { showDownloadListDialog = false },
                        title = { Text(context.getString(R.string.download_dxvk_title, downloadableProfiles.size)) },
                        text = {
                            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp)) {
                                val scrollState = rememberScrollState()
                                Column(modifier = Modifier.verticalScroll(scrollState)) {
                                    downloadableProfiles.forEach { profile ->
                                        val versionKey = profile.verName + "_v" + profile.verCode
                                        val isInstalled = installedVersions.contains(versionKey)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isInstalled) {
                                                    showDownloadListDialog = false
                                                    showProgressDialog = true
                                                    progressMessage = context.getString(R.string.downloading, profile.verName)

                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        try {
                                                            val timestamp = System.currentTimeMillis()
                                                            val tempFile = File(context.cacheDir, "dxvk_temp_${timestamp}.tar.xz")
                                                            val downloaded = Downloader.downloadFile(profile.remoteUrl, tempFile)
                                                            if (!downloaded || !tempFile.exists()) {
                                                                withContext(Dispatchers.Main) {
                                                                    showProgressDialog = false
                                                                    AppUtils.showToast(context, context.getString(R.string.failed_to_download, profile.verName))
                                                                }
                                                                return@launch
                                                            }

                                                            withContext(Dispatchers.Main) {
                                                                progressMessage = context.getString(R.string.installing, profile.verName)
                                                            }

                                                            withContext(Dispatchers.Main) {
                                                                val callback = object : ContentsManager.OnInstallFinishedCallback {
                                                                    var isExtracting = true
                                                                    override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                                                                        showProgressDialog = false
                                                                        val errorMsgResId = when (reason) {
                                                                            ContentsManager.InstallFailedReason.ERROR_BADTAR -> R.string.file_cannot_be_recognized
                                                                            ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> R.string.profile_not_found_in_content
                                                                            ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> R.string.profile_cannot_be_recognized
                                                                            ContentsManager.InstallFailedReason.ERROR_EXIST -> R.string.content_already_exist
                                                                            ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> R.string.content_is_incomplete
                                                                            ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> R.string.content_cannot_be_trusted
                                                                            else -> R.string.unable_to_install_content
                                                                        }
                                                                        AppUtils.showToast(context, context.getString(R.string.install_failed) + ": " + context.getString(errorMsgResId))
                                                                        if (tempFile.exists()) tempFile.delete()
                                                                    }
                                                                    override fun onSucceed(installedProfile: ContentProfile) {
                                                                        if (isExtracting) {
                                                                            isExtracting = false
                                                                            contentsManager.finishInstallContent(installedProfile, this)
                                                                        } else {
                                                                            showProgressDialog = false
                                                                            AppUtils.showToast(context, context.getString(R.string.installed_successfully, installedProfile.verName))
                                                                            contentsManager.syncContents()

                                                                            val originalItems = context.resources.getStringArray(R.array.dxvk_version_entries).toList()
                                                                            val installedProfilesList = contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK)
                                                                                .filter { it.remoteUrl == null || it.remoteUrl.isEmpty() }
                                                                                .map {
                                                                                    val entryName = ContentsManager.getEntryName(it)
                                                                                    val firstDashIndex = entryName.indexOf('-')
                                                                                    if (firstDashIndex >= 0) entryName.substring(firstDashIndex + 1) else entryName
                                                                                }
                                                                            versions = (originalItems + installedProfilesList).distinct()

                                                                            val entryName = ContentsManager.getEntryName(installedProfile)
                                                                            val firstDashIndex = entryName.indexOf('-')
                                                                            val versionString = if (firstDashIndex >= 0) entryName.substring(firstDashIndex + 1) else entryName
                                                                            version = versionString

                                                                            if (tempFile.exists()) tempFile.delete()
                                                                        }
                                                                    }
                                                                }
                                                                contentsManager.extraContentFile(Uri.fromFile(tempFile), callback)
                                                            }
                                                        } catch (e: Exception) {
                                                            withContext(Dispatchers.Main) {
                                                                    showProgressDialog = false
                                                                    AppUtils.showToast(context, context.getString(R.string.install_failed) + ": " + e.message)
                                                            }
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (isInstalled) "${profile.verName} (${context.getString(R.string.installed)})" else profile.verName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (profile.desc != null && profile.desc.isNotEmpty()) {
                                                    Text(
                                                        text = profile.desc,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = null,
                                                tint = if (isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = {
                            TextButton(onClick = { showDownloadListDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // Progress Loading Dialog
                if (showProgressDialog) {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                    ) {
                        Surface(shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                tonalElevation = 0.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                border = glassCardBorder(),
            ) {
                            Row(
                                modifier = Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(text = progressMessage, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                // Framerate
                val framerateOptions = context.resources.getStringArray(R.array.dxvk_framerate_entries).toList()
                DxvkRowWithHelp(
                    title = stringResource(R.string.frame_rate),
                    helpResId = 0,
                    spinnerItems = framerateOptions,
                    selectedIndex = framerateOptions.indexOfFirst { StringUtils.parseNumber(it) == framerate }.coerceAtLeast(0),
                    onSelected = { framerate = StringUtils.parseNumber(framerateOptions[it]) },
                )
                // Max Device Memory
                val memoryOptions = context.resources.getStringArray(R.array.dxvk_max_device_memory_entries).toList()
                DxvkRowWithHelp(
                    title = stringResource(R.string.max_device_memory),
                    helpResId = 0,
                    spinnerItems = memoryOptions,
                    selectedIndex = memoryOptions.indexOfFirst { StringUtils.parseNumber(it) == maxDeviceMemory }.coerceAtLeast(0),
                    onSelected = { maxDeviceMemory = StringUtils.parseNumber(memoryOptions[it]) },
                )
                // Async
                if (showAsync) {
                    ConfigSwitchRow("Async Pipeline", async) { async = it }
                }
                if (showAsyncCache) {
                    ConfigSwitchRow("Async Cache", asyncCache) { asyncCache = it }
                }
                // Tear Free
                DxvkRowWithHelp(
                    title = stringResource(R.string.tear_free),
                    helpResId = R.string.dxvk_tear_free_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(tearFree).coerceAtLeast(0),
                    onSelected = { tearFree = autoOnOff[it] },
                )
                // Max Frame Latency
                DxvkRowWithHelp(
                    title = "Max Frame Latency",
                    helpResId = R.string.dxvk_max_frame_latency_help,
                    spinnerItems = numOptions,
                    selectedIndex = numOptions.indexOf(maxFrameLatency).coerceAtLeast(0),
                    onSelected = { maxFrameLatency = numOptions[it] },
                )
                // Tiler Mode
                DxvkRowWithHelp(
                    title = "Tiler Mode",
                    helpResId = R.string.dxvk_tiler_mode_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(tilerMode).coerceAtLeast(0),
                    onSelected = { tilerMode = autoOnOff[it] },
                )
                // Max Tess Factor
                DxvkRowWithHelp(
                    title = "Max Tess Factor",
                    helpResId = R.string.dxvk_max_tess_factor_help,
                    spinnerItems = numOptions,
                    selectedIndex = numOptions.indexOf(maxTessFactor).coerceAtLeast(0),
                    onSelected = { maxTessFactor = numOptions[it] },
                )
                // Sync Interval
                DxvkRowWithHelp(
                    title = "Sync Interval",
                    helpResId = R.string.dxvk_sync_interval_help,
                    spinnerItems = syncIntervalOptions,
                    selectedIndex = syncIntervalOptions.indexOf(syncInterval).coerceAtLeast(0),
                    onSelected = { syncInterval = syncIntervalOptions[it] },
                )
                // Max Feature Level
                DxvkRowWithHelp(
                    title = "Max Feature Level",
                    helpResId = R.string.dxvk_max_feature_level_help,
                    spinnerItems = featureLevelOptions,
                    selectedIndex = featureLevelOptions.indexOf(maxFeatureLevel).coerceAtLeast(0),
                    onSelected = { maxFeatureLevel = featureLevelOptions[it] },
                )
                // Force Refresh Rate
                DxvkRowWithHelp(
                    title = "Force Refresh Rate",
                    helpResId = R.string.dxvk_force_refresh_rate_help,
                    spinnerItems = refreshRateOptions,
                    selectedIndex = refreshRateOptions.indexOf(forceRefreshRate).coerceAtLeast(0),
                    onSelected = { forceRefreshRate = refreshRateOptions[it] },
                )
                // Shader Model
                DxvkRowWithHelp(
                    title = "Shader Model",
                    helpResId = R.string.dxvk_shader_model_help,
                    spinnerItems = listOf("1", "2", "3"),
                    selectedIndex = listOf("1", "2", "3").indexOf(shaderModel).coerceAtLeast(0),
                    onSelected = { shaderModel = listOf("1", "2", "3")[it] },
                )
                // Float Emulation
                DxvkRowWithHelp(
                    title = "Float Emulation",
                    helpResId = R.string.dxvk_float_emulation_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(floatEmulation).coerceAtLeast(0),
                    onSelected = { floatEmulation = autoOnOff[it] },
                )
                // Sampler Anisotropy
                DxvkRowWithHelp(
                    title = "Sampler Anisotropy",
                    helpResId = R.string.dxvk_sampler_anisotropy_help,
                    spinnerItems = listOf("-1", "1", "2", "4", "8", "16"),
                    selectedIndex = listOf("-1", "1", "2", "4", "8", "16").indexOf(samplerAnisotropy).coerceAtLeast(0),
                    onSelected = { samplerAnisotropy = listOf("-1", "1", "2", "4", "8", "16")[it] },
                )
                // Enable Memory Defrag
                DxvkRowWithHelp(
                    title = "Enable Memory Defrag",
                    helpResId = R.string.dxvk_enable_memory_defrag_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(enableMemoryDefrag).coerceAtLeast(0),
                    onSelected = { enableMemoryDefrag = autoOnOff[it] },
                )
                // Lower Sin Cos
                DxvkRowWithHelp(
                    title = "Lower Sin Cos",
                    helpResId = R.string.dxvk_lower_sin_cos_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(lowerSinCos).coerceAtLeast(0),
                    onSelected = { lowerSinCos = autoOnOff[it] },
                )
                // Clamp Negative LOD Bias
                DxvkRowWithHelp(
                    title = "Clamp Negative LOD Bias",
                    helpResId = R.string.dxvk_clamp_negative_lod_bias_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(clampNegativeLodBias).coerceAtLeast(0),
                    onSelected = { clampNegativeLodBias = autoOnOff[it] },
                )
                // Sampler LOD Bias
                DxvkRowWithHelp(
                    title = "Sampler LOD Bias",
                    helpResId = R.string.dxvk_sampler_lod_bias_help,
                    spinnerItems = listOf("0.0", "0.5", "1.0", "1.5", "2.0", "-0.5", "-1.0"),
                    selectedIndex = listOf("0.0", "0.5", "1.0", "1.5", "2.0", "-0.5", "-1.0").indexOf(samplerLodBias).coerceAtLeast(0),
                    onSelected = { samplerLodBias = listOf("0.0", "0.5", "1.0", "1.5", "2.0", "-0.5", "-1.0")[it] },
                )
                // Num Compiler Threads
                DxvkRowWithHelp(
                    title = "Num Compiler Threads",
                    helpResId = R.string.dxvk_num_compiler_threads_help,
                    spinnerItems = numOptions,
                    selectedIndex = numOptions.indexOf(numCompilerThreads).coerceAtLeast(0),
                    onSelected = { numCompilerThreads = numOptions[it] },
                )
                // Enable Graphics Pipeline Library
                DxvkRowWithHelp(
                    title = "Enable Graphics Pipeline Library",
                    helpResId = R.string.dxvk_enable_graphics_pipeline_library_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(enableGraphicsPipelineLibrary).coerceAtLeast(0),
                    onSelected = { enableGraphicsPipelineLibrary = autoOnOff[it] },
                )
                // Relaxed Barriers
                DxvkRowWithHelp(
                    title = "Relaxed Barriers",
                    helpResId = R.string.dxvk_relaxed_barriers_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(relaxedBarriers).coerceAtLeast(0),
                    onSelected = { relaxedBarriers = autoOnOff[it] },
                )
                // Switches
                ConfigSwitchRow("Defer Surface Creation", deferSurfaceCreation) { deferSurfaceCreation = it }
                ConfigSwitchRow("Disable MSAA", disableMsaa) { disableMsaa = it }
            }
            // Bottom fixed buttons
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(8.dp))
                    GlassSolidPill(
                        onClick = {
                        val newConfig = buildString {
                            append("version=$version")
                            append(",framerate=$framerate")
                            append(",maxDeviceMemory=$maxDeviceMemory")
                            append(",async=${if (async) "1" else "0"}")
                            append(",asyncCache=${if (asyncCache) "1" else "0"}")
                            append(",tearFree=$tearFree")
                            append(",maxFrameLatency=$maxFrameLatency")
                            append(",tilerMode=$tilerMode")
                            append(",deferSurfaceCreation=${if (deferSurfaceCreation) "1" else "0"}")
                            append(",disableMsaa=${if (disableMsaa) "1" else "0"}")
                            append(",maxTessFactor=$maxTessFactor")
                            append(",syncInterval=$syncInterval")
                            append(",maxFeatureLevel=$maxFeatureLevel")
                            append(",forceRefreshRate=$forceRefreshRate")
                            append(",shaderModel=$shaderModel")
                            append(",floatEmulation=$floatEmulation")
                            append(",samplerAnisotropy=$samplerAnisotropy")
                            append(",enableMemoryDefrag=$enableMemoryDefrag")
                            append(",lowerSinCos=$lowerSinCos")
                            append(",clampNegativeLodBias=$clampNegativeLodBias")
                            append(",samplerLodBias=$samplerLodBias")
                            append(",numCompilerThreads=$numCompilerThreads")
                            append(",enableGraphicsPipelineLibrary=$enableGraphicsPipelineLibrary")
                            append(",relaxedBarriers=$relaxedBarriers")
                        }
                        onConfirm(newConfig)
                    },
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

// =====================================================================
// Утилиты (helpers)
// =====================================================================

@Composable
fun ConfigSpinnerRow(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = items.getOrElse(selectedIndex) { items.firstOrNull() ?: "" }
    Box {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },

            shape = GlassFieldShape,
            colors = glassFieldColors())
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { idx, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelected(idx); expanded = false },
                )
            }
        }
    }
}

@Composable
fun ConfigSeekBar(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange = 0..100,
) {
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
    
        colors = glassSliderColors())
}

@Composable
fun ConfigSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DxvkRowWithHelp(
    title: String,
    helpResId: Int,
    spinnerItems: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (helpResId != 0) {
                IconButton(onClick = { AppUtils.showToast(ctx, helpResId) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.HelpOutline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        ConfigSpinnerRow(items = spinnerItems, selectedIndex = selectedIndex, onSelected = onSelected)
    }
}

/**
 * Выпадающий список Vulkan-расширений с чекбоксами.
 * Расширения, отмеченные галочкой — включены (не в blacklist).
 * Снятие галочки добавляет расширение в blacklist.
 *
 * @param availableExtensions список расширений, доступных для выбранного драйвера
 * @param blacklistedExtensions mutableState со строкой blacklist (через запятую)
 * @param loading флаг асинхронной загрузки расширений
 */
@Composable
fun ExtensionsDropdownRow(
    availableExtensions: List<String>,
    blacklistedExtensions: androidx.compose.runtime.MutableState<String>,
    loading: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val blacklistSet = blacklistedExtensions.value.split(",").filter { it.isNotEmpty() }.toMutableSet()

    val displayText: String = when {
        loading -> "Loading…"
        availableExtensions.isEmpty() -> "No extensions available"
        else -> {
            val enabledCount = availableExtensions.size - blacklistSet.size
            "$enabledCount / ${availableExtensions.size} enabled"
        }
    }

    Box {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
            },

            shape = GlassFieldShape,
            colors = glassFieldColors())
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(enabled = !loading && availableExtensions.isNotEmpty()) { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 400.dp),
        ) {
            if (availableExtensions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No extensions available") },
                    onClick = { expanded = false },
                )
            } else {
                availableExtensions.forEach { extension ->
                    val isChecked = extension !in blacklistSet
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassSwitch(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        val newSet = blacklistSet.toMutableSet()
                                        if (checked) {
                                            newSet.remove(extension)
                                        } else {
                                            newSet.add(extension)
                                        }
                                        blacklistedExtensions.value = newSet.joinToString(",")
                                    },
                                )
                                Text(
                                    text = extension,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        },
                        onClick = {
                            val newSet = blacklistSet.toMutableSet()
                            if (isChecked) {
                                newSet.add(extension)
                            } else {
                                newSet.remove(extension)
                            }
                            blacklistedExtensions.value = newSet.joinToString(",")
                        },
                    )
                }
            }
        }
    }
}

// Простой парсер key=value через запятую (как в KeyValueSet)
private fun parseKeyValueSet(s: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    if (s.isEmpty()) return result
    s.split(",", ";").forEach { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size == 2) result[parts[0].trim()] = parts[1].trim()
    }
    return result
}

private fun findClosestLatencyIndex(target: Int): Int {
    val values = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)
    var bestIdx = 1 // 20 мс
    var minDiff = kotlin.math.abs(values[bestIdx] - target)
    for (i in values.indices) {
        val diff = kotlin.math.abs(values[i] - target)
        if (diff < minDiff) {
            minDiff = diff
            bestIdx = i
        }
    }
    return bestIdx
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentDownloadDialogCompose(
    context: Context,
    contentType: ContentProfile.ContentType,
    displayName: String,
    onDismiss: () -> Unit,
    onInstalled: (String) -> Unit
) {
    val contentsManager = remember { ContentsManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var showDownloadListDialog by remember { mutableStateOf(false) }
    var downloadableProfiles by remember { mutableStateOf<List<ContentProfile>>(emptyList()) }
    var installedVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingRemote by remember { mutableStateOf(false) }

    var showProgressDialog by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }

    // Load remote profiles automatically on launch
    LaunchedEffect(Unit) {
        isLoadingRemote = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Ensure contentsManager syncContents is called
                contentsManager.syncContents()

                val sp = MmkvPreferences()
                val contentsURL = sp.getString("downloadable_contents_url",
                    "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json")
                val json = Downloader.downloadString(contentsURL)
                if (json != null) {
                    contentsManager.setRemoteProfiles(json)

                    val allProfiles = contentsManager.getProfiles(contentType)
                    val installed = allProfiles.filter { ContentsManager.getInstallDir(context, it).exists() }
                        .map { it.verName + "_v" + it.verCode }

                    val downloadable = allProfiles.filter { it.remoteUrl != null && it.remoteUrl.isNotEmpty() && !ContentsManager.getInstallDir(context, it).exists() }

                    withContext(Dispatchers.Main) {
                        downloadableProfiles = downloadable
                        installedVersions = installed
                        if (downloadable.isEmpty()) {
                            AppUtils.showToast(context, context.getString(R.string.all_component_versions_installed, displayName))
                            onDismiss()
                        } else {
                            showDownloadListDialog = true
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        AppUtils.showToast(context, context.getString(R.string.failed_to_load_remote_contents))
                        onDismiss()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    AppUtils.showToast(context, context.getString(R.string.install_failed) + ": " + e.message)
                    onDismiss()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoadingRemote = false
                }
            }
        }
    }

    if (isLoadingRemote) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Surface(shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                tonalElevation = 0.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                border = glassCardBorder(),
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(text = context.getString(R.string.loading), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (showDownloadListDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(context.getString(R.string.download_component_title, displayName, downloadableProfiles.size)) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 550.dp)) {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        downloadableProfiles.forEach { profile ->
                            val versionKey = profile.verName + "_v" + profile.verCode
                            val isInstalled = installedVersions.contains(versionKey)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isInstalled) {
                                        showDownloadListDialog = false
                                        showProgressDialog = true
                                        progressMessage = context.getString(R.string.downloading, profile.verName)

                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                // Create target download directory (Download/Winlator)
                                                val downloadDir = File(android.os.Environment.getExternalStorageDirectory(), "Download/Winlator")
                                                if (!downloadDir.exists()) downloadDir.mkdirs()

                                                val timestamp = System.currentTimeMillis()
                                                val tempFile = File(context.cacheDir, "comp_temp_${timestamp}.tar.xz")
                                                val downloaded = Downloader.downloadFile(profile.remoteUrl, tempFile)
                                                if (!downloaded || !tempFile.exists()) {
                                                    withContext(Dispatchers.Main) {
                                                        showProgressDialog = false
                                                        AppUtils.showToast(context, context.getString(R.string.failed_to_download, profile.verName))
                                                    }
                                                    return@launch
                                                }

                                                withContext(Dispatchers.Main) {
                                                    progressMessage = context.getString(R.string.installing, profile.verName)
                                                }

                                                withContext(Dispatchers.Main) {
                                                    val callback = object : ContentsManager.OnInstallFinishedCallback {
                                                        var isExtracting = true
                                                        override fun onFailed(reason: ContentsManager.InstallFailedReason, e: Exception?) {
                                                            showProgressDialog = false
                                                            val errorMsgResId = when (reason) {
                                                                ContentsManager.InstallFailedReason.ERROR_BADTAR -> R.string.file_cannot_be_recognized
                                                                ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> R.string.profile_not_found_in_content
                                                                ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> R.string.profile_cannot_be_recognized
                                                                ContentsManager.InstallFailedReason.ERROR_EXIST -> R.string.content_already_exist
                                                                ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> R.string.content_is_incomplete
                                                                ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> R.string.content_cannot_be_trusted
                                                                else -> R.string.unable_to_install_content
                                                            }
                                                            AppUtils.showToast(context, context.getString(R.string.install_failed) + ": " + context.getString(errorMsgResId))
                                                            if (tempFile.exists()) tempFile.delete()
                                                            onDismiss()
                                                        }
                                                        override fun onSucceed(installedProfile: ContentProfile) {
                                                            if (isExtracting) {
                                                                isExtracting = false
                                                                contentsManager.finishInstallContent(installedProfile, this)
                                                            } else {
                                                                showProgressDialog = false
                                                                AppUtils.showToast(context, context.getString(R.string.installed_successfully, installedProfile.verName))
                                                                contentsManager.syncContents()

                                                                val entryName = ContentsManager.getEntryName(installedProfile)
                                                                val firstDashIndex = entryName.indexOf('-')
                                                                val versionString = if (firstDashIndex >= 0) entryName.substring(firstDashIndex + 1) else entryName

                                                                onInstalled(versionString)
                                                                if (tempFile.exists()) tempFile.delete()
                                                                onDismiss()
                                                            }
                                                        }
                                                    }
                                                    contentsManager.extraContentFile(Uri.fromFile(tempFile), callback)
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    showProgressDialog = false
                                                    AppUtils.showToast(context, context.getString(R.string.install_failed) + ": " + e.message)
                                                    onDismiss()
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isInstalled) "${profile.verName} (${context.getString(R.string.installed)})" else profile.verName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (profile.desc != null && profile.desc.isNotEmpty()) {
                                        Text(
                                            text = profile.desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = if (isInstalled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showProgressDialog) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassCardFill(),
                tonalElevation = 0.dp,
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                border = glassCardBorder(),
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text(text = progressMessage, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
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
