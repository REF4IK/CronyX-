package com.winlator.cmod.ui.screens

import androidx.compose.foundation.shape.CircleShape
import com.winlator.cmod.ui.components.glassBadgeFill
import com.winlator.cmod.ui.components.glassSliderColors
import com.winlator.cmod.ui.components.GlassSwitch
import com.winlator.cmod.ui.components.GlassAlertDialog
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassDescColor
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.inputcontrols.ControlsProfile
import com.winlator.cmod.inputcontrols.InputControlsManager
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.box86_64.Box86_64Preset
import com.winlator.cmod.box86_64.Box86_64PresetManager
import com.winlator.cmod.fexcore.FEXCorePreset
import com.winlator.cmod.fexcore.FEXCorePresetManager
import com.winlator.cmod.core.gameconfig.GameConfigManager
import com.winlator.cmod.core.gameconfig.CloudConfigRepo
import com.winlator.cmod.fexcore.FEXCoreManager
import com.winlator.cmod.winhandler.WinHandler
import com.winlator.cmod.box86_64.rc.RCManager
import com.winlator.cmod.box86_64.rc.RCFile
import com.winlator.cmod.midi.MidiManager
import com.winlator.cmod.contentdialog.ShortcutSettingsDialog
import com.winlator.cmod.saves.CustomFilePickerActivity
import com.winlator.cmod.xenvironment.ImageFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Копия каталога между контейнерами (рекурсивно), без папки Desktop:
 * в ней лежат .desktop-ярлыки, и копирование перетащило бы в целевой
 * контейнер ярлыки всех остальных игр.
 */
private fun copyProfileDir(src: File, dst: File) {
    if (!src.exists()) return
    if (src.isDirectory) {
        if (!dst.exists() && !dst.mkdirs()) return
        val children = src.listFiles() ?: return
        for (child in children) {
            val skip = child.isDirectory &&
                child.parentFile == src &&
                child.name.equals("Desktop", ignoreCase = true)
            if (skip) continue
            copyProfileDir(child, File(dst, child.name))
        }
    } else {
        FileUtils.copy(src, dst)
    }
}

private fun normalizeTitleKey(s: String): String =
    s.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9а-я]+"), "")

private fun matchesGameTitle(dirName: String, normalizedTitle: String): Boolean {
    val n = normalizeTitleKey(dirName)
    if (n.isEmpty()) return false
    if (n.contains(normalizedTitle)) return true
    // Короткие имена вида «AC» при сверке с длинным названием дают ложные
    // совпадения, поэтому обратная сверка только для достаточно длинных имён
    return normalizedTitle.contains(n) && n.length >= 5
}

/** Есть ли в файле/каталоге хоть один непустой файл. */
private fun hasContent(file: File): Boolean {
    if (file.isFile) return file.length() > 0
    file.walkTopDown().forEach { if (it.isFile && it.length() > 0) return true }
    return false
}

/**
 * Компактный индекс путей сохранений из ludusavi-manifest
 * (assets/save_manifest.json, см. tools/gen_save_manifest.mjs):
 * нормализованное имя/installDir/exe → пути относительно drive_c.
 */
private object SaveManifestIndex {
    @Volatile private var cached: org.json.JSONObject? = null

    fun load(ctx: Context): org.json.JSONObject? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: try {
                val text = ctx.assets.open("save_manifest.json").bufferedReader().use { it.readText() }
                org.json.JSONObject(text).also { cached = it }
            } catch (_: Throwable) { null }
        }
    }
}

/**
 * Перенос сохранений по базе ludusavi: название игры, installDir и имя exe
 * ищутся в индексе, найденные пути копируются из старого контейнера в новый.
 * @return true, если скопирован хотя бы один путь.
 */
private fun copySavesFromManifest(
    ctx: Context,
    srcDriveC: File,
    dstDriveC: File,
    gameTitle: String,
    exeName: String,
): Boolean {
    val index = SaveManifestIndex.load(ctx) ?: return false
    val keys = LinkedHashSet<String>()
    normalizeTitleKey(gameTitle).takeIf { it.isNotEmpty() }?.let { keys.add(it) }
    normalizeTitleKey(exeName).takeIf { it.isNotEmpty() }?.let { keys.add(it) }

    var copiedAny = false
    for (key in keys) {
        val paths = index.optJSONArray(key) ?: continue
        for (i in 0 until paths.length()) {
            val rel = paths.optString(i) ?: continue
            val src = File(srcDriveC, rel)
            if (!src.exists() || !hasContent(src)) continue // пустые каталоги не считаем найденными
            val dst = File(dstDriveC, rel)
            try {
                if (src.isDirectory) copyProfileDir(src, dst) else FileUtils.copy(src, dst)
                copiedAny = true
            } catch (_: Exception) {}
        }
    }
    return copiedAny
}

/**
 * Перенос сохранений конкретной игры. Приложение не знает, где именно игра
 * хранит сейвы, поэтому сканируются типичные корни (AppData, Documents,
 * Saved Games) и копируются только каталоги, чьё имя совпадает с названием
 * игры — сохранения остальных игр не тянуться.
 * @return true, если нашлась и скопировалась хотя бы одна папка.
 */
private fun copyGameSaves(srcProfile: File, dstProfile: File, gameTitle: String): Boolean {
    val title = gameTitle.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9а-я]+"), "")
    if (title.isEmpty()) return false
    var copiedAny = false
    val roots = listOf("AppData/Roaming", "AppData/Local", "Documents", "Saved Games")
    for (root in roots) {
        val srcRoot = File(srcProfile, root)
        if (!srcRoot.isDirectory) continue
        val dstRoot = File(dstProfile, root)
        if (!dstRoot.exists() && !dstRoot.mkdirs()) continue
        for (entry in srcRoot.listFiles() ?: continue) {
            if (matchesGameTitle(entry.name, title)) {
                if (!hasContent(entry)) continue // пустые каталоги не считаем найденными
                if (entry.isDirectory) copyProfileDir(entry, File(dstRoot, entry.name))
                else FileUtils.copy(entry, File(dstRoot, entry.name))
                copiedAny = true
                continue
            }
            // На уровень глубже: Documents/My Games/<Игра>, AppData/<x>/<Игра>
            if (!entry.isDirectory) continue
            for (child in entry.listFiles() ?: continue) {
                if (!matchesGameTitle(child.name, title)) continue
                if (!hasContent(child)) continue
                val dstChild = File(File(dstRoot, entry.name), child.name)
                if (child.isDirectory) copyProfileDir(child, dstChild)
                else FileUtils.copy(child, dstChild)
                copiedAny = true
            }
        }
    }
    return copiedAny
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutSettingsScreen(
    shortcut: Shortcut,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val manager = remember { ContainerManager(ctx) }
    val contentsManager = remember { ContentsManager(ctx) }
    val inputControlsManager = remember { InputControlsManager(ctx) }
    val sp = remember { MmkvPreferences() }
    val container = shortcut.container
    val isLegacyModeEnabled = remember { sp.getBoolean("legacy_mode_enabled", false) }
    val moveScope = rememberCoroutineScope()
    var showMovePicker by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<Container?>(null) }
    var movingShortcut by remember { mutableStateOf(false) }
    // Ручной выбор папки сохранений: контекст (srcDriveC, dstDriveC, ярлык)
    var pendingManualCopy by remember { mutableStateOf<Triple<File, File, Shortcut>?>(null) }
    var showManualPickDialog by remember { mutableStateOf(false) }

    val savesFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingManualCopy
        val pickedPath = result.data?.getStringExtra("selectedDirectory")
        if (pending == null || pickedPath == null) {
            showManualPickDialog = false
            AppUtils.showToast(ctx, ctx.getString(R.string.shortcut_moved_to, pending?.third?.name ?: ""))
            onBack()
            return@rememberLauncherForActivityResult
        }
        val (srcDriveC, dstDriveC, moved) = pending
        pendingManualCopy = null
        showManualPickDialog = false
        moveScope.launch(Dispatchers.IO) {
            val src = File(pickedPath)
            val rel = src.absolutePath.removePrefix(srcDriveC.absolutePath).trim('/')
            if (rel.isNotEmpty()) {
                try {
                    val dst = File(dstDriveC, rel)
                    if (src.isDirectory) copyProfileDir(src, dst) else FileUtils.copy(src, dst)
                    moved.putExtra("savesDir", rel)
                    moved.saveData()
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                AppUtils.showToast(ctx, ctx.getString(R.string.shortcut_moved_to, moved.name))
                onBack()
            }
        }
    }

    // Determine if Wine is arm64EC
    val wineVersion = remember { container.wineVersion }
    val wineInfo = remember { WineInfo.fromIdentifier(ctx, contentsManager, wineVersion) }
    val isArm64EC = remember { wineInfo.isArm64EC() }

    // States
    var name by remember { mutableStateOf(shortcut.name) }
    var customIcon by remember { mutableStateOf<Bitmap?>(null) }
    var execArgs by remember { mutableStateOf(shortcut.getExtra("execArgs")) }
    val screenSizeEntries = remember { ctx.resources.getStringArray(R.array.screen_size_entries).toList() }
    val initialScreenSize = remember { shortcut.getExtra("screenSize", container.screenSize) }
    var screenSize by remember {
        val entry = screenSizeEntries.firstOrNull { it.split(" ").firstOrNull() == initialScreenSize }
        mutableStateOf(entry ?: if (initialScreenSize.contains("x")) "Custom" else initialScreenSize)
    }
    var isCustomScreen by remember { mutableStateOf(screenSize == "Custom") }
    var customScreenWidth by remember {
        mutableStateOf(if (isCustomScreen && initialScreenSize.contains("x")) {
            initialScreenSize.split("x").getOrElse(0) { "" }
        } else "")
    }
    var customScreenHeight by remember {
        mutableStateOf(if (isCustomScreen && initialScreenSize.contains("x")) {
            initialScreenSize.split("x").getOrElse(1) { "" }
        } else "")
    }
    var graphicsDriver by remember { mutableStateOf(shortcut.getExtra("graphicsDriver", container.graphicsDriver)) }
    var graphicsDriverConfig by remember { mutableStateOf(shortcut.getExtra("graphicsDriverConfig", container.graphicsDriverConfig)) }
    var dxwrapper by remember { mutableStateOf(shortcut.getExtra("dxwrapper", container.dxWrapper)) }
    var dxwrapperConfig by remember { mutableStateOf(shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig())) }
    var ddrawrapper by remember { mutableStateOf(shortcut.getExtra("ddrawrapper", container.dDrawWrapper)) }
    var audioDriver by remember { mutableStateOf(shortcut.getExtra("audioDriver", container.audioDriver)) }
    var audioDriverConfig by remember { mutableStateOf(shortcut.getExtra("audioDriverConfig", container.audioDriverConfig)) }
    val displayRendererEntries = remember { ctx.resources.getStringArray(R.array.displayrenderers_entries).map { it.lowercase(Locale.ENGLISH) } }
    var displayRenderer by remember { mutableStateOf(shortcut.getExtra("displayRenderer", container.displayRenderer)) }
    var sfCompatMode by remember { mutableStateOf(shortcut.getExtra("sfCompatMode", if (container.sfCompatMode) "1" else "0") == "1") }
    var emulator by remember {
        val raw = shortcut.getExtra("emulator", container.emulator) ?: ""
        mutableStateOf(if (raw.lowercase() == "box64") "Box64" else "FEXCore")
    }
    var showPublishDialog by remember { mutableStateOf(false) }
    var publishDescription by remember { mutableStateOf("") }
    var isPublishing by remember { mutableStateOf(false) }

    var publishStatus by remember { mutableStateOf("") }
    var showBox64Download by remember { mutableStateOf(false) }
    var showFexcoreDownload by remember { mutableStateOf(false) }

    LaunchedEffect(isArm64EC) {
        if (!isArm64EC) {
            emulator = "Box64"
        }
    }

    var emulator64 by remember {
        val raw = shortcut.getExtra("emulator64", container.emulator) ?: ""
        mutableStateOf(if (raw.lowercase() == "box64") "Box64" else "FEXCore")
    }
    var midiSoundFont by remember { mutableStateOf(shortcut.getExtra("midiSoundFont", container.midiSoundFont)) }
    var forceFullscreen by remember { mutableStateOf(shortcut.getExtra("forceFullscreen", "0") == "1") }

    // Secondary exec
    var useSecondaryExec by remember {
        mutableStateOf(shortcut.getExtra("secondaryExec", "").isNotEmpty())
    }
    var secondaryExec by remember { mutableStateOf(shortcut.getExtra("secondaryExec")) }
    var execDelay by remember { mutableStateOf(shortcut.getExtra("execDelay", "0")) }

    var fullscreenStretched by remember { mutableStateOf(shortcut.getExtra("fullscreenStretched", "0") == "1") }
    var useUnixLibs by remember { mutableStateOf(shortcut.getExtra("useUnixLibs", if (container.isUseUnixLibs) "1" else "0") == "1") }
    var startupSelection by remember { mutableStateOf(shortcut.getExtra("startupSelection", container.startupSelection.toString())) }
    var controlsProfileId by remember { mutableStateOf(shortcut.getExtra("controlsProfile", "0")) }
    var box64Version by remember { mutableStateOf(shortcut.getExtra("box64Version", container.box64Version)) }
    var box64Preset by remember { mutableStateOf(shortcut.getExtra("box64Preset", container.box64Preset)) }
    var fexcoreVersion by remember { mutableStateOf(shortcut.getExtra("fexcoreVersion", container.getFEXCoreVersion())) }
    val initialFexPreset = container.getFEXCorePreset()
    var fexcorePreset by remember { mutableStateOf(shortcut.getExtra("fexcorePreset", initialFexPreset)) }
    var rcfileId by remember { mutableStateOf(shortcut.getExtra("rcfileId", container.getRCFileId().toString())) }

    // inputType as bitmask (matching old Java code)
    val initialInputType = shortcut.getExtra("inputType", container.inputType.toString()).toIntOrNull() ?: WinHandler.DEFAULT_INPUT_TYPE.toInt()
    var enableXInput by remember { mutableStateOf((initialInputType and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0) }
    var enableDInput by remember { mutableStateOf((initialInputType and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0) }
    var dinputMapperStandard by remember { mutableStateOf((initialInputType and WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt()) != 0) }

    var disableXinput by remember { mutableStateOf(shortcut.getExtra("disableXinput", "0") == "1") }
    var touchscreenMode by remember { mutableStateOf(shortcut.getExtra("simTouchScreen", "0") == "1") }

    // Sharpness — defaults 100 like old code
    var sharpnessEffect by remember { mutableStateOf(shortcut.getExtra("sharpnessEffect", "None")) }
    var sharpnessLevel by remember { mutableStateOf(shortcut.getExtra("sharpnessLevel", "100")) }
    var sharpnessDenoise by remember { mutableStateOf(shortcut.getExtra("sharpnessDenoise", "100")) }

    // CPU lists as comma-separated strings like old code
    var cpuList by remember { mutableStateOf(shortcut.getExtra("cpuList", container.getCPUList(true))) }
    var cpuListWoW64 by remember { mutableStateOf(shortcut.getExtra("cpuListWoW64", container.getCPUListWoW64(true))) }
    var winComponents by remember { mutableStateOf(shortcut.getExtra("wincomponents", container.winComponents)) }
    var envVars by remember { mutableStateOf(shortcut.getExtra("envVars", container.envVars)) }
    var lcAll by remember { mutableStateOf(shortcut.getExtra("lc_all", container.getLC_ALL() ?: (Locale.getDefault().language + "_" + Locale.getDefault().country + ".UTF-8"))) }

    // Dialogs
    var showGraphicsConfig by remember { mutableStateOf(false) }
    var showDxConfig by remember { mutableStateOf(false) }
    var showAudioConfig by remember { mutableStateOf(false) }

    var activeBox64PresetEditId by remember { mutableStateOf<String?>(null) }
    var activeFexcorePresetEditId by remember { mutableStateOf<String?>(null) }
    var showBox64PresetDialog by remember { mutableStateOf(false) }
    var showFexcorePresetDialog by remember { mutableStateOf(false) }

    val dialogHelper = remember { ShortcutSettingsDialog(ctx, shortcut) }
    var versionRefreshTrigger by remember { mutableStateOf(0) }

    val box64Versions = remember(isArm64EC, versionRefreshTrigger) {
        contentsManager.syncContents()
        val list = mutableListOf<String>()
        val resId = if (isArm64EC) R.array.wowbox64_version_entries else R.array.box64_version_entries
        list.addAll(ctx.resources.getStringArray(resId))

        val contentType = if (isArm64EC) {
            com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
        } else {
            com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_BOX64
        }

        contentsManager.getProfiles(contentType)?.forEach { profile ->
            if (profile.remoteUrl == null || com.winlator.cmod.contents.ContentsManager.getInstallDir(ctx, profile).exists()) {
                val entryName = com.winlator.cmod.contents.ContentsManager.getEntryName(profile)
                val firstDashIndex = entryName.indexOf('-')
                if (firstDashIndex >= 0) {
                    list.add(entryName.substring(firstDashIndex + 1))
                }
            }
        }
        list.distinct()
    }

    val fexcoreVersions = remember(versionRefreshTrigger) {
        contentsManager.syncContents()
        val list = mutableListOf<String>()
        list.addAll(ctx.resources.getStringArray(R.array.fexcore_version_entries))
        contentsManager.getProfiles(com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)?.forEach { profile ->
            if (profile.remoteUrl == null || com.winlator.cmod.contents.ContentsManager.getInstallDir(ctx, profile).exists()) {
                val entryName = com.winlator.cmod.contents.ContentsManager.getEntryName(profile)
                val firstDashIndex = entryName.indexOf('-')
                if (firstDashIndex >= 0) {
                    list.add(entryName.substring(firstDashIndex + 1))
                }
            }
        }
        list.distinct()
    }

    val graphicsDriverVersions = remember { ctx.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries).toList() }
    val dxvkVersions = remember { ctx.resources.getStringArray(R.array.dxvk_version_entries).toList() }

    // Icon picker
    val iconPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    customIcon = bmp
                    shortcut.saveCustomIcon(bmp)
                }
            } catch (_: Exception) {}
        }
    }

    // Preset import launchers
    var presetsRefreshTrigger by remember { mutableStateOf(0) }

    val box64ImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                    Box86_64PresetManager.importPreset("box64", ctx, inputStream)
                    presetsRefreshTrigger++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val fexcoreImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FEXCorePresetManager.importPreset(ctx, inputStream)
                    presetsRefreshTrigger++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Emulator enable/disable based on arm64EC
    LaunchedEffect(isArm64EC) {
        if (!isArm64EC) {
            emulator = "box64"
            emulator64 = "box64"
        }
    }

    BackHandler { onBack() }

    if (showPublishDialog) {
        AlertDialog(
            onDismissRequest = { showPublishDialog = false },
            title = { Text(stringResource(R.string.publish_config)) },
            text = {
                Column {
                    if (publishStatus.isNotEmpty()) {
                        Text(publishStatus, color = if (publishStatus.contains("Error") || publishStatus.contains("Failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = publishDescription,
                        onValueChange = { publishDescription = it },
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
                            publishStatus = ctx.getString(R.string.bundle_publishing)
                            val config = GameConfigManager.buildGameConfig(container, shortcut, publishDescription)
                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val actualSize = if (isCustomScreen && customScreenWidth.isNotBlank()) "${customScreenWidth}x${customScreenHeight}" else screenSize
                                    val settings = org.json.JSONObject()
                                    settings.put("wineVersion", container.wineVersion)
                                    settings.put("screenSize", actualSize)
                                    settings.put("dxwrapper", dxwrapper)
                                    settings.put("dxwrapperConfig", dxwrapperConfig)
                                    settings.put("graphicsDriver", graphicsDriver)
                                    settings.put("graphicsDriverConfig", graphicsDriverConfig)
                                    settings.put("displayRenderer", displayRenderer)
                                    settings.put("audioDriver", audioDriver)
                                    settings.put("audioDriverConfig", audioDriverConfig)
                                    settings.put("box64Version", box64Version)
                                    settings.put("box64Preset", box64Preset)
                                    settings.put("fexcoreVersion", fexcoreVersion)
                                    settings.put("fexcorePreset", fexcorePreset)
                                    settings.put("emulator", emulator.lowercase())
                                    settings.put("envVars", envVars)

                                    val bundler = com.winlator.cmod.core.gameconfig.GameConfigBundler(ctx)
                                    val result = bundler.buildBundle(settings, ctx.cacheDir)

                                    config.containerSettings = settings
                                    com.winlator.cmod.core.gameconfig.BundleRepoClient.uploadBundle(
                                        result.zipFile, config.toJson().toString(), config.gameName, publishDescription,
                                        config.device, config.gpu,
                                        com.winlator.cmod.community.AccountManager.session(ctx),
                                        object : com.winlator.cmod.core.gameconfig.BundleRepoClient.BundleUploadCallback {
                                            override fun onComplete(success: Boolean, sha: String, bundleUrl: String, error: String?) {
                                                result.zipFile.delete()
                                                isPublishing = false
                                                publishStatus = if (success) ctx.getString(R.string.bundle_published, sha) else "$error"
                                            }
                                        })
                                } catch (e: Exception) {
                                    isPublishing = false
                                    publishStatus = "Ошибка: ${e.message}"
                                }
                            }
                        }
                    },
                    enabled = !isPublishing
                ) { Text(if (isPublishing) "..." else stringResource(R.string.publish)) }
            },
            dismissButton = {
                TextButton(onClick = { showPublishDialog = false; publishStatus = "" }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { showPublishDialog = true }) {
                                        Icon(Icons.Filled.CloudUpload, contentDescription = stringResource(R.string.publish_config))
                                    }
                                    IconButton(
                                        onClick = {
                                            // Rename if needed (inline, matching old ShortcutSettingsDialog.renameShortcut via reflection)
                                            val newName = name.trim()
                                        if (newName.isNotEmpty() && newName != shortcut.name) {
                                            val parent = shortcut.file.parentFile
                                            val oldDesktopFile = shortcut.file
                                            val oldName = shortcut.name
                                            val newDesktopFile = File(parent, "$newName.desktop")

                                            // Handle Windows case-insensitivity by using a temporary file name if only case changed
                                            val renamed = if (oldDesktopFile.canonicalPath.equals(newDesktopFile.canonicalPath, ignoreCase = true)) {
                                                val tempFile = File(parent, "${newName}_temp.desktop")
                                                oldDesktopFile.renameTo(tempFile) && tempFile.renameTo(newDesktopFile)
                                            } else {
                                                !newDesktopFile.isFile && oldDesktopFile.renameTo(newDesktopFile)
                                            }

                                            if (renamed) {
                                                // Use reflection to update final fields (same as old Java code)
                                                try {
                                                    val fileField = Shortcut::class.java.getDeclaredField("file")
                                                    fileField.isAccessible = true
                                                    fileField.set(shortcut, newDesktopFile)
                                                    val nameField = Shortcut::class.java.getDeclaredField("name")
                                                    nameField.isAccessible = true
                                                    nameField.set(shortcut, newName)
                                                } catch (_: Exception) {}
                                                // Delete old file if it still exists and path actually changed
                                                if (oldDesktopFile.exists() && oldDesktopFile.absolutePath != newDesktopFile.absolutePath) {
                                                    oldDesktopFile.delete()
                                                }
                                            }

                                            // Rename link file if exists, using correct oldName variable
                                            val linkFile = File(parent, "$oldName.lnk")
                                            if (linkFile.isFile) {
                                                val newLinkFile = File(parent, "$newName.lnk")
                                                if (oldName.equals(newName, ignoreCase = true)) {
                                                    val tempLink = File(parent, "${newName}_temp.lnk")
                                                    linkFile.renameTo(tempLink) && tempLink.renameTo(newLinkFile)
                                                } else if (!newLinkFile.isFile) {
                                                    linkFile.renameTo(newLinkFile)
                                                }
                                            }
                                        }

                                        // Build inputType bitmask (matching old Java save logic)
                                        var finalInputType = 0
                                        if (enableXInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
                                        if (enableDInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
                                        finalInputType = finalInputType or (if (dinputMapperStandard) WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt() else WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt())

                                        shortcut.putExtra("inputType", finalInputType.toString())

                                        shortcut.putExtra("execArgs", execArgs.ifEmpty { null })
                                        val finalScreenSize = if (isCustomScreen) "${customScreenWidth}x${customScreenHeight}" else (screenSize.split(" ").firstOrNull() ?: screenSize)
                                        shortcut.putExtra("screenSize", if (finalScreenSize != container.screenSize) finalScreenSize else null)
                                        shortcut.putExtra("graphicsDriver", if (graphicsDriver != container.graphicsDriver) graphicsDriver else null)
                                        shortcut.putExtra("graphicsDriverConfig", if (graphicsDriverConfig != container.graphicsDriverConfig) graphicsDriverConfig else null)
                                        shortcut.putExtra("dxwrapper", if (dxwrapper != container.dxWrapper) dxwrapper else null)
                                        shortcut.putExtra("dxwrapperConfig", if (dxwrapperConfig != container.getDXWrapperConfig()) dxwrapperConfig else null)
                                        shortcut.putExtra("ddrawrapper", if (ddrawrapper != container.dDrawWrapper) ddrawrapper else null)
                                        shortcut.putExtra("audioDriver", if (audioDriver != container.audioDriver) audioDriver else null)
                                        shortcut.putExtra("displayRenderer", if (displayRenderer != container.displayRenderer) displayRenderer else null)
                                        shortcut.putExtra("sfCompatMode", if (sfCompatMode != container.sfCompatMode) (if (sfCompatMode) "1" else "0") else null)
                                        val emuSave = emulator.lowercase()
                                        shortcut.putExtra("emulator", if (emuSave != container.emulator.lowercase()) emuSave else null)
                                        val emu64Save = emulator64.lowercase()
                                        shortcut.putExtra("emulator64", if (emu64Save != container.emulator.lowercase()) emu64Save else null)
                                        shortcut.putExtra("midiSoundFont", if (midiSoundFont != container.midiSoundFont) midiSoundFont else null)
                                        shortcut.putExtra("forceFullscreen", if (forceFullscreen) "1" else null)

                                        // Secondary exec
                                        if (useSecondaryExec) {
                                            val secExec = secondaryExec.trim()
                                            val delay = execDelay.trim()
                                            shortcut.putExtra("secondaryExec", secExec.ifEmpty { null })
                                            shortcut.putExtra("execDelay", delay.ifEmpty { null })
                                        } else {
                                            shortcut.putExtra("secondaryExec", null)
                                            shortcut.putExtra("execDelay", null)
                                        }

                                        shortcut.putExtra("fullscreenStretched", if (fullscreenStretched) "1" else null)
                                        shortcut.putExtra("useUnixLibs", if (useUnixLibs != container.isUseUnixLibs) (if (useUnixLibs) "1" else "0") else null)
                                        shortcut.putExtra("disableXinput", if (disableXinput) "1" else null)
                                        shortcut.putExtra("simTouchScreen", if (touchscreenMode) "1" else "0")

                                        shortcut.putExtra("box64Version", if (box64Version != container.box64Version) box64Version else null)
                                        val box64PresetSave = box64Preset
                                        shortcut.putExtra("box64Preset", if (box64PresetSave != container.box64Preset) box64PresetSave else null)

                                        shortcut.putExtra("rcfileId", if (rcfileId.toIntOrNull() != container.getRCFileId()) rcfileId else null)

                                        shortcut.putExtra("fexcoreVersion", if (fexcoreVersion != container.getFEXCoreVersion()) fexcoreVersion else null)
                                        shortcut.putExtra("fexcorePreset", if (fexcorePreset != container.getFEXCorePreset()) fexcorePreset else null)

                                        val startupIdx = startupSelection.toIntOrNull() ?: 0
                                        shortcut.putExtra("startupSelection", if (startupIdx.toByte() != container.startupSelection) startupSelection else null)

                                        val profileList = inputControlsManager.getProfiles(true) ?: arrayListOf<ControlsProfile>()
                                        val controlsProfileInt = controlsProfileId.toIntOrNull() ?: 0
                                        shortcut.putExtra("controlsProfile", if (controlsProfileInt > 0) controlsProfileId else null)

                                        shortcut.putExtra("sharpnessEffect", sharpnessEffect)
                                        shortcut.putExtra("sharpnessLevel", sharpnessLevel)
                                        shortcut.putExtra("sharpnessDenoise", sharpnessDenoise)

                                        shortcut.putExtra("cpuList", if (cpuList != container.getCPUList(true)) cpuList else null)
                                        shortcut.putExtra("cpuListWoW64", if (cpuListWoW64 != container.getCPUListWoW64(true)) cpuListWoW64 else null)
                                        shortcut.putExtra("wincomponents", if (winComponents != container.winComponents) winComponents else null)
                                        shortcut.putExtra("envVars", envVars.ifEmpty { null })
                                        shortcut.putExtra("lc_all", if (lcAll != container.getLC_ALL()) lcAll else null)

                                        shortcut.saveData()
                                        AppUtils.showToast(ctx, R.string.saved)
                                        onBack()
                                    }) {
                                    Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
                                        }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // Та же ширина карточек, что в «Редактировании контейнера»
                .padding(
                    start = GlassTokens.RowPaddingH,
                    end = GlassTokens.RowPaddingH,
                    bottom = 88.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- Верхний блок: Contents ----
            SectionCard(title = stringResource(R.string.contents), icon = Icons.Filled.Settings) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),

                    shape = GlassFieldShape,
                    colors = glassFieldColors())

                // Custom Icon row
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(stringResource(R.string.custom_game_icon), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(glassBadgeFill()),
                            contentAlignment = Alignment.Center,
                        ) {
                            val displayIcon = customIcon ?: shortcut.displayIcon
                            if (displayIcon != null) {
                                Image(bitmap = displayIcon.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                            } else {
                                Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            }
                        }
                        GlassPill(
                            onClick = { iconPickerLauncher.launch("image/*") },
                        ) { Text(stringResource(R.string.select_icon)) }
                        if (customIcon != null || shortcut.getExtra("customIconPath").isNotEmpty()) {
                            GlassPill(
                                onClick = {
                                customIcon = null
                                shortcut.removeCustomIcon()
                            },
                            ) { Text(stringResource(R.string.remove_icon)) }
                        }
                    }
                }

                // Screen Size
                val screenSizeEntries = ctx.resources.getStringArray(R.array.screen_size_entries).toList()
                ContainerSpinnerRow(
                    label = stringResource(R.string.screen_size),
                    entries = screenSizeEntries,
                    selected = screenSize,
                    onSelected = {
                        screenSize = it; isCustomScreen = it == "Custom"
                    }
                )
                if (isCustomScreen) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customScreenWidth,
                            onValueChange = { customScreenWidth = it.filter { c -> c.isDigit() } },
                            label = { Text("W") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        ,
                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                        OutlinedTextField(
                            value = customScreenHeight,
                            onValueChange = { customScreenHeight = it.filter { c -> c.isDigit() } },
                            label = { Text("H") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        ,
                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                    }
                }

                // Graphics Driver
                ContainerSpinnerRowWithConfig(
                    label = stringResource(R.string.graphics_driver),
                    entries = ctx.resources.getStringArray(R.array.graphics_driver_entries).toList(),
                    selected = graphicsDriver,
                    onSelected = { graphicsDriver = it },
                    onConfigClick = { showGraphicsConfig = true }
                )

                // DX Wrapper
                ContainerSpinnerRowWithConfig(
                    label = stringResource(R.string.dxwrapper),
                    entries = ctx.resources.getStringArray(R.array.dxwrapper_entries).toList(),
                    selected = dxwrapper,
                    showConfigButton = dxwrapper.lowercase(Locale.ENGLISH) != "wined3d",
                    onSelected = { dxwrapper = it.lowercase(Locale.ENGLISH) },
                    onConfigClick = { showDxConfig = true }
                )

                // DDraw Wrapper
                val ddrawEntries = ctx.resources.getStringArray(R.array.ddrawrapper_entries).toList()
                ContainerSpinnerRow(
                    label = stringResource(R.string.ddraw_wrapper),
                    entries = ddrawEntries,
                    selected = ddrawrapper,
                    onSelected = { ddrawrapper = it }
                )

                // Audio Driver
                ContainerSpinnerRowWithConfig(
                    label = stringResource(R.string.audio_driver),
                    entries = ctx.resources.getStringArray(R.array.audio_driver_entries).toList(),
                    selected = audioDriver,
                    onSelected = { audioDriver = it },
                    onConfigClick = { showAudioConfig = true }
                )

                // Display Renderer
                ContainerSpinnerRow(
                    label = stringResource(R.string.display_renderer),
                    entries = ctx.resources.getStringArray(R.array.displayrenderers_entries).toList(),
                    selected = displayRendererEntries.firstOrNull { it == displayRenderer }?.let { renderer ->
                        ctx.resources.getStringArray(R.array.displayrenderers_entries)
                            .firstOrNull { it.lowercase(Locale.ENGLISH) == renderer }
                    } ?: "Vulkan",
                    onSelected = {
                        displayRenderer = it.lowercase(Locale.ENGLISH)
                    },
                )
                if (displayRenderer == "surfaceflinger") {
                    SwitchRow(
                        stringResource(R.string.sf_compat_mode),
                        sfCompatMode,
                        onCheckedChange = { sfCompatMode = it },
                    )
                }

                // DLL Emulator
                val emulatorEntries = ctx.resources.getStringArray(R.array.emulator_entries).toList()
                ContainerSpinnerRow(
                    label = stringResource(R.string.dll_emulator),
                    entries = emulatorEntries,
                    selected = if (emulator.lowercase(Locale.ENGLISH) == "fexcore") "FEXCore" else "Box64",
                    enabled = isArm64EC,
                    onSelected = { emulator = it.lowercase(Locale.ENGLISH) }
                )

                // MIDI SoundFont
                val midiFiles = remember { MidiManager.getSF2Files(ctx) }
                val midiEntries = remember(midiFiles) {
                    val list = mutableListOf("None", MidiManager.DEFAULT_SF2_FILE)
                    midiFiles?.forEach { list.add(it.name) }
                    list
                }
                ContainerSpinnerRow(
                    label = stringResource(R.string.midi_sound_font),
                    entries = midiEntries,
                    selected = if (midiSoundFont.isEmpty()) "None" else midiSoundFont,
                    onSelected = { midiSoundFont = if (it == "None") "" else it }
                )

                // Locale (LC_ALL)
                val lcAllEntries = remember { ctx.resources.getStringArray(R.array.some_lc_all).toList() }
                val lcAllNames = remember { ctx.resources.getStringArray(R.array.some_lc_all_names).toList() }
                val lcAllDisplayName = remember(lcAll) {
                    val code = lcAll.replace(".UTF-8", "")
                    val idx = lcAllEntries.indexOf(code)
                    if (idx >= 0) lcAllNames[idx] else lcAll
                }
                ContainerSpinnerRow(
                    label = stringResource(R.string.locale),
                    entries = lcAllNames,
                    selected = lcAllDisplayName,
                    onSelected = { name ->
                        val idx = lcAllNames.indexOf(name)
                        if (idx >= 0) lcAll = lcAllEntries[idx] + ".UTF-8"
                    }
                )
            }

            // ---- Контейнер запуска ----
            SectionCard(title = stringResource(R.string.container), icon = Icons.Filled.VideogameAsset) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !movingShortcut) { showMovePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.VideogameAsset, null, tint = glassAccent(), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.container_current),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(container.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Text(stringResource(R.string.change), color = glassAccent())
                }
            }

            // Выбор целевого контейнера для переноса (текущий не предлагается)
            if (showMovePicker) {
                val otherContainers = remember(manager) {
                    ArrayList(manager.containers.filter { it.id != shortcut.container.id })
                }
                ContainerPickerDialog(
                    containers = otherContainers,
                    onDismiss = { showMovePicker = false },
                    onPick = { target ->
                        showMovePicker = false
                        moveTarget = target
                    },
                )
            }

            // Подтверждение переноса
            moveTarget?.let { target ->
                val targetIsArm64EC = remember(target) {
                    WineInfo.fromIdentifier(ctx, contentsManager, target.wineVersion).isArm64EC()
                }
                val archMismatch = targetIsArm64EC != isArm64EC
                var copySaves by remember(target) { mutableStateOf(true) }
                var inheritContainer by remember(target) { mutableStateOf(true) }
                GlassAlertDialog(
                    onDismissRequest = { if (!movingShortcut) moveTarget = null },
                    title = stringResource(R.string.move_shortcut_title),
                    confirmText = stringResource(R.string.move_shortcut_confirm),
                    dismissText = stringResource(R.string.cancel),
                    confirmEnabled = !movingShortcut,
                    dismissEnabled = !movingShortcut,
                    onConfirm = {
                        movingShortcut = true
                        moveScope.launch(Dispatchers.IO) {
                            // На wine другой архитектуры переопределения эмулятора
                            // не действуют — сбрасываем на значения контейнера
                            val drop = if (archMismatch) setOf(
                                "emulator", "fexcoreVersion", "fexcorePreset",
                                "useUnixLibs", "box64Version",
                            ) else null
                            val moved = shortcut.moveToContainer(target, drop, inheritContainer)
                            if (moved == null) {
                                withContext(Dispatchers.Main) {
                                    movingShortcut = false
                                    moveTarget = null
                                    AppUtils.showToast(ctx, R.string.move_shortcut_fail)
                                }
                                return@launch
                            }
                            if (copySaves) {
                                var copied = false
                                val srcDriveC = File(container.rootDir, ".wine/drive_c")
                                val dstDriveC = File(target.rootDir, ".wine/drive_c")
                                try {
                                    // 0) Папка, указанная вручную при прошлом переносе
                                    val storedRel = moved.getExtra("savesDir")
                                    if (!storedRel.isNullOrEmpty()) {
                                        val src = File(srcDriveC, storedRel)
                                        if (src.exists() && hasContent(src)) {
                                            val dst = File(dstDriveC, storedRel)
                                            if (src.isDirectory) copyProfileDir(src, dst) else FileUtils.copy(src, dst)
                                            copied = true
                                        }
                                    }
                                    // 1) Точный перенос по базе ludusavi
                                    if (!copied) {
                                        copied = copySavesFromManifest(
                                            ctx, srcDriveC, dstDriveC,
                                            moved.name,
                                            moved.resolveExeFile()?.nameWithoutExtension ?: "",
                                        )
                                    }
                                    // 2) Фолбэк: типичные корни по имени игры
                                    if (!copied) {
                                        val srcProfile = File(srcDriveC, "users/" + ImageFs.USER)
                                        val dstProfile = File(dstDriveC, "users/" + ImageFs.USER)
                                        if (srcProfile.exists()) {
                                            copied = copyGameSaves(srcProfile, dstProfile, moved.name)
                                        }
                                    }
                                } catch (_: Exception) { copied = false }
                                if (!copied) {
                                    // 3) Ничего не нашли — предлагаем указать папку вручную.
                                    // Подтверждение переноса закрываем: сам ярлык уже перенесён
                                    withContext(Dispatchers.Main) {
                                        movingShortcut = false
                                        moveTarget = null
                                        pendingManualCopy = Triple(srcDriveC, dstDriveC, moved)
                                        showManualPickDialog = true
                                    }
                                    return@launch // завершение — после ручного выбора/пропуска
                                }
                            }
                            withContext(Dispatchers.Main) {
                                movingShortcut = false
                                moveTarget = null
                                AppUtils.showToast(ctx, ctx.getString(R.string.shortcut_moved_to, target.name))
                                onBack()
                            }
                        }
                    },
                    onDismiss = { moveTarget = null },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.move_shortcut_hint, target.name),
                            color = glassDescColor(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (archMismatch) {
                            Text(
                                stringResource(R.string.move_warning_arch),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = copySaves, onCheckedChange = { copySaves = it }, enabled = !movingShortcut)
                            Text(stringResource(R.string.move_copy_saves))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = inheritContainer, onCheckedChange = { inheritContainer = it }, enabled = !movingShortcut)
                            Text(stringResource(R.string.inherit_container_settings))
                        }
                        if (movingShortcut) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(stringResource(R.string.saves_migrating), color = glassDescColor(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Ручной выбор папки сохранений, если автопоиск не справился
            if (showManualPickDialog) {
                pendingManualCopy?.let { pending ->
                    GlassAlertDialog(
                        onDismissRequest = { },
                        title = stringResource(R.string.saves_manual_title),
                        confirmText = stringResource(R.string.pick_folder),
                        dismissText = stringResource(R.string.skip),
                        onConfirm = {
                            showManualPickDialog = false
                            val intent = Intent(ctx, CustomFilePickerActivity::class.java)
                                .putExtra("initialDirectory", pending.first.absolutePath)
                            savesFolderPicker.launch(intent)
                        },
                        onDismiss = {
                            val (_, _, moved) = pending
                            pendingManualCopy = null
                            showManualPickDialog = false
                            AppUtils.showToast(ctx, ctx.getString(R.string.shortcut_moved_to, moved.name))
                            onBack()
                        },
                    ) {
                        Text(
                            stringResource(R.string.saves_manual_hint, pending.third.name),
                            color = glassDescColor(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // ---- Tab Layout ----
            var currentTab by remember { mutableStateOf(0) }
            val tabTitles = listOf(
                stringResource(R.string.win_components),
                stringResource(R.string.environment_variables),
                stringResource(R.string.advanced)
            )

            TabRow(
                selectedTabIndex = currentTab,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                contentColor = glassAccent(),
                divider = { },
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (currentTab) {
                0 -> {
                    WinComponentsTab(winComponents = winComponents, onWinComponentsChange = { winComponents = it })
                }
                1 -> {
                    EnvVarsTab(envVars = envVars, onEnvVarsChange = { envVars = it })
                }
                2 -> {
                    // ---- Advanced Tab Content ----
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ======== Box86/Box64 Section ========
                        if (emulator.lowercase(Locale.ENGLISH) != "fexcore") {
                            SectionCard(title = stringResource(R.string.box64), icon = Icons.Filled.Code) {
                                ContainerSpinnerRowWithDownload(
                                    label = stringResource(R.string.version),
                                    entries = box64Versions,
                                    selected = box64Version,
                                    onSelected = { box64Version = it },
                                    onDownloadClick = { showBox64Download = true }
                                )

                                val box64Presets = remember(presetsRefreshTrigger) { Box86_64PresetManager.getPresets("box64", ctx) }
                                val box64PresetNames = remember(box64Presets) { box64Presets.map { it.name } }
                                val selectedBox64PresetName = box64Presets.firstOrNull { it.id == box64Preset }?.name ?: box64Preset
                                ContainerSpinnerRow(
                                    label = stringResource(R.string.preset),
                                    entries = box64PresetNames,
                                    selected = selectedBox64PresetName,
                                    onSelected = { name ->
                                        val preset = box64Presets.firstOrNull { p -> p.name == name }
                                        if (preset != null) box64Preset = preset.id
                                    }
                                )

                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = {
                                        activeBox64PresetEditId = null
                                        showBox64PresetDialog = true
                                    }) { Icon(Icons.Filled.Add, "Add Preset") }

                                    IconButton(onClick = {
                                        activeBox64PresetEditId = box64Preset
                                        showBox64PresetDialog = true
                                    }) { Icon(Icons.Filled.Edit, "Edit Preset") }

                                    IconButton(onClick = {
                                        Box86_64PresetManager.duplicatePreset("box64", ctx, box64Preset)
                                        presetsRefreshTrigger++
                                    }) { Icon(Icons.Filled.ContentCopy, "Duplicate Preset") }

                                    if (box64Preset.startsWith("custom-")) {
                                        IconButton(onClick = {
                                            Box86_64PresetManager.removePreset("box64", ctx, box64Preset)
                                            presetsRefreshTrigger++
                                        }) { Icon(Icons.Filled.Delete, "Remove Preset") }
                                    }

                                    IconButton(onClick = {
                                        Box86_64PresetManager.exportPreset("box64", ctx, box64Preset)
                                    }) { Icon(Icons.Filled.IosShare, "Export Preset") }

                                    IconButton(onClick = {
                                        box64ImportLauncher.launch("*/*")
                                    }) { Icon(Icons.Filled.FileOpen, "Import Preset") }
                                }

                                val rcManager = remember { RCManager(ctx) }
                                val rcFiles: List<RCFile> = remember { rcManager.rcFiles }
                                val rcFileNames = remember(rcFiles) { rcFiles.map { it.name } }
                                val rcfileIdInt = rcfileId.toIntOrNull() ?: 0
                                val selectedRcFileName = rcFiles.getOrNull(rcfileIdInt)?.name ?: rcFiles.firstOrNull()?.name ?: ""
                                ContainerSpinnerRow(
                                    label = stringResource(R.string.box86_64_rc_files),
                                    entries = rcFileNames,
                                    selected = selectedRcFileName,
                                    onSelected = { name ->
                                        val idx = rcFiles.indexOfFirst { r -> r.name == name }
                                        if (idx >= 0) rcfileId = idx.toString()
                                    }
                                )
                            }
                        }

                        // ======== FEXCore Section ========
                        if (isArm64EC && emulator.lowercase(Locale.ENGLISH) == "fexcore") {
                            SectionCard(title = stringResource(R.string.fexcore), icon = Icons.Filled.Memory) {
                                ContainerSpinnerRowWithDownload(
                                    label = stringResource(R.string.version),
                                    entries = fexcoreVersions,
                                    selected = fexcoreVersion,
                                    onSelected = { fexcoreVersion = it },
                                    onDownloadClick = { showFexcoreDownload = true }
                                )

                                SwitchRow(stringResource(R.string.use_unix_libs), useUnixLibs) { useUnixLibs = it }

                                val fexPresets = remember(presetsRefreshTrigger) { FEXCorePresetManager.getPresets(ctx) }
                                val selectedPresetName = fexPresets.find { it.id == fexcorePreset }?.name ?: fexcorePreset
                                ContainerSpinnerRow(
                                    label = stringResource(R.string.preset),
                                    entries = fexPresets.map { it.name },
                                    selected = selectedPresetName,
                                    onSelected = { name -> fexcorePreset = fexPresets.find { it.name == name }?.id ?: name }
                                )

                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = {
                                        activeFexcorePresetEditId = null
                                        showFexcorePresetDialog = true
                                    }) { Icon(Icons.Filled.Add, "Add Preset") }

                                    IconButton(onClick = {
                                        activeFexcorePresetEditId = fexcorePreset
                                        showFexcorePresetDialog = true
                                    }) { Icon(Icons.Filled.Edit, "Edit Preset") }

                                    IconButton(onClick = {
                                        FEXCorePresetManager.duplicatePreset(ctx, fexcorePreset)
                                        presetsRefreshTrigger++
                                    }) { Icon(Icons.Filled.ContentCopy, "Duplicate Preset") }

                                    if (fexcorePreset.startsWith("custom-")) {
                                        IconButton(onClick = {
                                            FEXCorePresetManager.removePreset(ctx, fexcorePreset)
                                            presetsRefreshTrigger++
                                        }) { Icon(Icons.Filled.Delete, "Remove Preset") }
                                    }

                                    IconButton(onClick = {
                                        FEXCorePresetManager.exportPreset(ctx, fexcorePreset)
                                    }) { Icon(Icons.Filled.IosShare, "Export Preset") }

                                    IconButton(onClick = {
                                        fexcoreImportLauncher.launch("*/*")
                                    }) { Icon(Icons.Filled.FileOpen, "Import Preset") }
                                }
                            }
                        }

                        // ======== System ========
                        SectionCard(title = stringResource(R.string.system), icon = Icons.Filled.Settings) {
                            val startupSelectionEntries = ctx.resources.getStringArray(R.array.startup_selection_entries).toList()
                            val startupSelectionInt = startupSelection.toIntOrNull() ?: 0
                            val selectedStartupSelection = startupSelectionEntries.getOrElse(startupSelectionInt) { startupSelectionEntries.first() }
                            ContainerSpinnerRow(
                                label = stringResource(R.string.startup_selection),
                                entries = startupSelectionEntries,
                                selected = selectedStartupSelection,
                                onSelected = { name ->
                                    val idx = startupSelectionEntries.indexOf(name)
                                    if (idx >= 0) startupSelection = idx.toString()
                                },
                            )
                        }

                        // ======== Input Controls ========
                        SectionCard(title = stringResource(R.string.input_controls), icon = Icons.Filled.Keyboard) {
                            val profileList = remember { inputControlsManager.getProfiles(true) ?: arrayListOf() }
                            val selectedProfile = profileList.find { it.id.toString() == controlsProfileId }?.name ?: stringResource(R.string.none)
                            ContainerSpinnerRow(
                                label = stringResource(R.string.profile),
                                entries = listOf(stringResource(R.string.none)) + profileList.map { it.name },
                                selected = selectedProfile,
                                onSelected = { name ->
                                    val profile = profileList.find { it.name == name }
                                    controlsProfileId = profile?.id?.toString() ?: "0"
                                },
                            )
                            SwitchRow(stringResource(R.string.disable_xinput_for_shortcut), disableXinput) { disableXinput = it }
                            SwitchRow(stringResource(R.string.simulate_touch_screen), touchscreenMode) { touchscreenMode = it }
                        }

                        // ======== Game Controller ========
                        SectionCard(title = stringResource(R.string.game_controller), icon = Icons.Filled.Gamepad) {
                            if (isLegacyModeEnabled) {
                                Text(
                                    "You are in 7.1.2 legacy input mode. Advanced input settings are not available.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                SwitchRow(stringResource(R.string.enable_xinput_for_wine_game), enableXInput) {
                                    enableXInput = it
                                    if (it && enableDInput) {
                                        Toast.makeText(ctx, R.string.enable_xinput_and_dinput_same_time, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                SwitchRow(stringResource(R.string.enable_dinput_for_wine_game), enableDInput) {
                                    enableDInput = it
                                    if (it && enableXInput) {
                                        Toast.makeText(ctx, R.string.enable_xinput_and_dinput_same_time, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                if (enableDInput) {
                                    val dinputEntries = ctx.resources.getStringArray(R.array.dinput_mapper_type_entries).toList()
                                    ContainerSpinnerRow(
                                        label = stringResource(R.string.directinput_mapper_type),
                                        entries = dinputEntries,
                                        selected = if (dinputMapperStandard) dinputEntries.getOrElse(0) { "" } else dinputEntries.getOrElse(1) { "" },
                                        onSelected = { selected -> dinputMapperStandard = (dinputEntries.indexOf(selected) == 0) },
                                    )
                                }
                            }
                        }

                        // ======== Secondary Executable ========
                        SectionCard(title = stringResource(R.string.secondary_exec), icon = Icons.Filled.PlayArrow) {
                            SwitchRow(stringResource(R.string.use_secondary_executable), useSecondaryExec) { useSecondaryExec = it }
                            if (useSecondaryExec) {
                                OutlinedTextField(
                                    value = secondaryExec ?: "",
                                    onValueChange = { secondaryExec = it },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.enter_secondary_exec_path)) }
                                ,
                                    shape = GlassFieldShape,
                                    colors = glassFieldColors())
                                OutlinedTextField(
                                    value = execDelay,
                                    onValueChange = { execDelay = it.filter { c -> c.isDigit() } },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.enter_delay_seconds)) }
                                ,
                                    shape = GlassFieldShape,
                                    colors = glassFieldColors())
                            }
                        }

                        // ======== Exec Arguments & Window Options ========
                        SectionCard(title = stringResource(R.string.exec_arguments), icon = Icons.Filled.Terminal) {
                            val extraArgsItems = listOf(
                                "-force-gfx-direct",
                                "-force-d3d11-singlethreaded",
                                "-force-dx9",
                                "-force-d3d9",
                                "-force-d3d11",
                                "--force-gfx-direct",
                                "--force-d3d11-singlethreaded",
                                "--force-dx9",
                                "--force-d3d9",
                                "--force-d3d11",
                                "/d3d9"
                            )
                            var showArgsMenu by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                OutlinedTextField(
                                    value = execArgs,
                                    onValueChange = { execArgs = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { showArgsMenu = true }) {
                                            Icon(Icons.Filled.Menu, "Arguments helper")
                                        }
                                    }
                                ,
                                    shape = GlassFieldShape,
                                    colors = glassFieldColors())
                                DropdownMenu(expanded = showArgsMenu, onDismissRequest = { showArgsMenu = false }) {
                                    extraArgsItems.forEach { arg ->
                                        DropdownMenuItem(
                                            text = { Text(arg) },
                                            onClick = {
                                                if (!execArgs.contains(arg)) {
                                                    execArgs = if (execArgs.trim().isEmpty()) arg else "${execArgs.trim()} $arg"
                                                }
                                                showArgsMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            SwitchRow(stringResource(R.string.fullscreen_stretched), fullscreenStretched) { fullscreenStretched = it }
                            SwitchRow(stringResource(R.string.force_fullscreen), forceFullscreen) { forceFullscreen = it }
                        }

                        // ======== VkBaSalt ========
                        SectionCard(title = "VkBaSalt", icon = Icons.Filled.Brush) {
                            val sharpnessEntries = ctx.resources.getStringArray(R.array.vkbasalt_sharpness_entries).toList()
                            ContainerSpinnerRow(
                                label = stringResource(R.string.vkbasalt_sharpness_effects),
                                entries = sharpnessEntries,
                                selected = sharpnessEffect,
                                onSelected = { sharpnessEffect = it }
                            )
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.vkbasalt_sharpness_level), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${sharpnessLevel}%", style = MaterialTheme.typography.bodyMedium)
                                }
                                Slider(
                                    value = sharpnessLevel.toFloatOrNull() ?: 100f,
                                    valueRange = 0f..100f,
                                    onValueChange = { sharpnessLevel = it.toInt().toString() }
                                ,
                                    colors = glassSliderColors())
                            }
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.vkbasalt_sharpness_denoise), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${sharpnessDenoise}%", style = MaterialTheme.typography.bodyMedium)
                                }
                                Slider(
                                    value = sharpnessDenoise.toFloatOrNull() ?: 100f,
                                    valueRange = 0f..100f,
                                    onValueChange = { sharpnessDenoise = it.toInt().toString() }
                                ,
                                    colors = glassSliderColors())
                            }
                        }

                        // ======== Processor Affinity ========
                        SectionCard(title = stringResource(R.string.processor_affinity), icon = Icons.Filled.Memory) {
                            val numCpus = remember { Runtime.getRuntime().availableProcessors() }
                            CpuListRow(
                                label = stringResource(R.string.processor_affinity),
                                cpuList = cpuList, numCpus = numCpus, onCpuListChange = { cpuList = it }
                            )
                            CpuListRow(
                                label = stringResource(R.string.processor_affinity_wow64),
                                cpuList = cpuListWoW64, numCpus = numCpus, onCpuListChange = { cpuListWoW64 = it }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(72.dp))
        }
    }

    // Config dialogs
    if (showGraphicsConfig) {
        GraphicsDriverConfigDialogCompose(
            context = ctx, graphicsDriver = graphicsDriver,
            initialConfig = graphicsDriverConfig, availableVersions = graphicsDriverVersions,
            onDismiss = { showGraphicsConfig = false },
            onConfirm = { graphicsDriverConfig = it; showGraphicsConfig = false },
        )
    }
    if (showDxConfig) {
        if (dxwrapper == "dxvk") {
            DXVKConfigDialogCompose(
                context = ctx, initialConfig = dxwrapperConfig, availableVersions = dxvkVersions,
                onDismiss = { showDxConfig = false },
                onConfirm = { dxwrapperConfig = it; showDxConfig = false },
            )
        } else if (dxwrapper == "vkd3d") {
            VKD3DConfigDialogCompose(
                context = ctx, initialConfig = dxwrapperConfig,
                onDismiss = { showDxConfig = false },
                onConfirm = { dxwrapperConfig = it; showDxConfig = false },
            )
        }
    }
    if (showAudioConfig) {
        AudioDriverConfigDialogCompose(
            initialConfig = audioDriverConfig,
            onDismiss = { showAudioConfig = false },
            onConfirm = { audioDriverConfig = it; showAudioConfig = false },
        )
    }

    if (showBox64PresetDialog) {
        EditPresetDialog(
            prefix = "box64",
            presetId = activeBox64PresetEditId,
            onDismiss = { showBox64PresetDialog = false },
            onConfirm = {
                presetsRefreshTrigger++
                showBox64PresetDialog = false
            }
        )
    }

    if (showFexcorePresetDialog) {
        EditPresetDialog(
            prefix = "fexcore",
            presetId = activeFexcorePresetEditId,
            onDismiss = { showFexcorePresetDialog = false },
            onConfirm = {
                presetsRefreshTrigger++
                showFexcorePresetDialog = false
            }
        )
    }

    if (showBox64Download) {
        ContentDownloadDialogCompose(
            context = ctx,
            contentType = if (isArm64EC) com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64 else com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_BOX64,
            displayName = "Box64",
            onDismiss = { showBox64Download = false },
            onInstalled = { installedVersion ->
                box64Version = installedVersion
                versionRefreshTrigger++
            }
        )
    }

    if (showFexcoreDownload) {
        ContentDownloadDialogCompose(
            context = ctx,
            contentType = com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
            displayName = "FEXCore",
            onDismiss = { showFexcoreDownload = false },
            onInstalled = { installedVersion ->
                fexcoreVersion = installedVersion
                versionRefreshTrigger++
            }
        )
    }
}
