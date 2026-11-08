package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSolidPill
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassDescColor
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.winlator.cmod.contentdialog.DXVKConfigDialog
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.core.AppUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.box86_64.Box86_64Preset
import com.winlator.cmod.box86_64.Box86_64PresetManager
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.core.WineRegistryEditor
import com.winlator.cmod.core.WineThemeManager
import com.winlator.cmod.fexcore.FEXCorePreset
import com.winlator.cmod.fexcore.FEXCorePresetManager
import com.winlator.cmod.winhandler.WinHandler
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Полный перенос ContainerDetailFragment.java на Jetpack Compose.
 * Редактирование/создание контейнера со всеми настройками.
 * Config-диалоги вызываются через оригинальные Java-классы (нужен dummy View с tag).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerEditScreen(
    containerManager: ContainerManager,
    containerId: Int,
    isEditMode: Boolean,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val contentsManager = remember { ContentsManager(ctx) }
    val sp = remember { MmkvPreferences() }
    val container = remember(isEditMode, containerId) {
        if (isEditMode) containerManager.getContainerById(containerId) else null
    }

    // ---- Состояния всех полей ----
    var name by remember { mutableStateOf(container?.name ?: "Container-${containerManager.getNextContainerId()}") }
    val screenSizeEntries = remember { ctx.resources.getStringArray(R.array.screen_size_entries).toList() }
    val initialScreenSize = remember(container) { container?.screenSize ?: Container.DEFAULT_SCREEN_SIZE }
    var screenSize by remember(initialScreenSize) {
        val entry = screenSizeEntries.firstOrNull { it.split(" ").firstOrNull() == initialScreenSize }
        mutableStateOf(entry ?: if (initialScreenSize.contains("x")) "Custom" else initialScreenSize)
    }
    var isCustomScreen by remember { mutableStateOf(screenSize == "Custom") }
    var customScreenWidth by remember(initialScreenSize, isCustomScreen) {
        mutableStateOf(if (isCustomScreen && initialScreenSize.contains("x")) {
            initialScreenSize.split("x").getOrElse(0) { "" }
        } else "")
    }
    var customScreenHeight by remember(initialScreenSize, isCustomScreen) {
        mutableStateOf(if (isCustomScreen && initialScreenSize.contains("x")) {
            initialScreenSize.split("x").getOrElse(1) { "" }
        } else "")
    }

    val wineVersions = remember { loadWineVersions(ctx, contentsManager) }
    var wineVersion by remember { mutableStateOf(container?.wineVersion ?: WineInfo.MAIN_WINE_VERSION.identifier()) }

    var graphicsDriver by remember { mutableStateOf(container?.graphicsDriver ?: Container.DEFAULT_GRAPHICS_DRIVER) }
    var graphicsDriverConfig by remember { mutableStateOf(container?.graphicsDriverConfig ?: Container.DEFAULT_GRAPHICSDRIVERCONFIG) }
    var wrapperEntries by remember { mutableStateOf(com.winlator.cmod.contents.WrapperManager.driverEntries(ctx)) }
    var showWrapperDownload by remember { mutableStateOf(false) }
    val displayRendererEntries = remember { ctx.resources.getStringArray(R.array.displayrenderers_entries).map { it.lowercase(Locale.ENGLISH) } }
    var displayRenderer by remember { mutableStateOf(container?.displayRenderer ?: Container.DEFAULT_DISPLAY_RENDERER) }
    var sfCompatMode by remember { mutableStateOf(container?.sfCompatMode ?: true) }
    var dxwrapper by remember { mutableStateOf(container?.dxWrapper ?: Container.DEFAULT_DXWRAPPER) }
    var dxwrapperConfig by remember { mutableStateOf(container?.getDXWrapperConfig() ?: Container.DEFAULT_DXWRAPPERCONFIG) }
    var ddrawrapper by remember { mutableStateOf(container?.dDrawWrapper ?: Container.DEFAULT_DDRAWRAPPER) }
    var audioDriver by remember { mutableStateOf(container?.audioDriver ?: Container.DEFAULT_AUDIO_DRIVER) }
    var audioDriverConfig by remember { mutableStateOf(container?.audioDriverConfig ?: "performanceMode=1,volume=1.0,latencyMillis=20") }
    var emulator by remember { mutableStateOf(container?.emulator ?: Container.DEFAULT_EMULATOR) }

    val wineInfo = remember(wineVersion) { WineInfo.fromIdentifier(ctx, contentsManager, wineVersion) }
    val isArm64EC = remember(wineInfo) { wineInfo.isArm64EC() }

    LaunchedEffect(isArm64EC) {
        if (!isArm64EC) {
            emulator = "box64"
        }
    }

    var midiSoundFont by remember { mutableStateOf(container?.midiSoundFont ?: "") }
    var lcAll by remember { mutableStateOf(container?.getLC_ALL() ?: (Locale.getDefault().language + "_" + Locale.getDefault().country + ".UTF-8")) }
    var fullscreenStretched by remember { mutableStateOf(container?.isFullscreenStretched ?: false) }
    var useUnixLibs by remember { mutableStateOf(container?.isUseUnixLibs ?: true) }

    // Wine Configuration (registry keys)
    var csmt by remember { mutableStateOf(3) }
    var gpuNamePos by remember { mutableStateOf(0) }
    var offscreenRenderingMode by remember { mutableStateOf("fbo") }
    var strictShaderMath by remember { mutableStateOf(1) }
    var videoMemorySize by remember { mutableStateOf("2048") }
    var mouseWarpOverride by remember { mutableStateOf("disable") }
    var logPixels by remember { mutableStateOf(96) }
    var desktopTheme by remember { mutableStateOf(container?.desktopTheme ?: WineThemeManager.DEFAULT_DESKTOP_THEME) }

    // Win Components
    var winComponents by remember { mutableStateOf(container?.winComponents ?: Container.DEFAULT_WINCOMPONENTS) }

    // Environment Variables
    var envVars by remember { mutableStateOf(container?.envVars ?: Container.DEFAULT_ENV_VARS) }

    // Drives
    var drives by remember { mutableStateOf(container?.drives ?: Container.DEFAULT_DRIVES) }

    // Advanced
    var box64Version by remember { mutableStateOf(container?.box64Version ?: DefaultVersion.BOX64) }
    var box64Preset by remember { mutableStateOf(container?.box64Preset ?: sp.getString("box64_preset", Box86_64Preset.COMPATIBILITY) ?: Box86_64Preset.COMPATIBILITY) }
    var rcfileId by remember { mutableStateOf(container?.getRCFileId() ?: 0) }
    var fexcoreVersion by remember { mutableStateOf(container?.getFEXCoreVersion() ?: DefaultVersion.FEXCORE) }
    var fexcorePreset by remember { mutableStateOf(container?.getFEXCorePreset() ?: FEXCorePreset.INTERMEDIATE) }
    var startupSelection by remember { mutableStateOf((container?.startupSelection ?: Container.STARTUP_SELECTION_ESSENTIAL).toInt()) }
    var wow64Mode by remember { mutableStateOf(container?.isWoW64Mode ?: true) }

    var showBox64Download by remember { mutableStateOf(false) }
    var showFexcoreDownload by remember { mutableStateOf(false) }

    // Game Controller
    val legacyMode = remember { sp.getBoolean("legacy_mode_enabled", false) }
    var enableXInput by remember {
        val it = (container?.inputType ?: WinHandler.DEFAULT_INPUT_TYPE).toInt()
        mutableStateOf(legacyMode || (it and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0)
    }
    var enableDInput by remember {
        val it = (container?.inputType ?: WinHandler.DEFAULT_INPUT_TYPE).toInt()
        mutableStateOf((it and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0)
    }
    var dinputMapperType by remember {
        val it = (container?.inputType ?: WinHandler.DEFAULT_INPUT_TYPE).toInt()
        mutableStateOf(if ((it and WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt()) != 0) 1 else 0)
    }
    var sdl2Toggle by remember { mutableStateOf(envVars.contains("SDL_XINPUT_ENABLED=1")) }

    // CPU lists
    var cpuList by remember { mutableStateOf(container?.cpuList ?: Container.getFallbackCPUList()) }
    var cpuListWoW64 by remember { mutableStateOf(container?.cpuListWoW64 ?: Container.getFallbackCPUListWoW64()) }

    // XR
    var primaryController by remember { mutableStateOf(container?.primaryController ?: 1) }
    var controllerMapping by remember {
        val mappings = Container.XrControllerMapping.values()
        if (container != null) {
            val bytes = ByteArray(mappings.size)
            mappings.forEachIndexed { i, m -> bytes[i] = container.getControllerMapping(m) }
            mutableStateOf(String(bytes))
        } else {
            mutableStateOf(String(ByteArray(10)))
        }
    }

    // Загрузка registry keys из user.reg (edit mode)
    LaunchedEffect(container) {
        if (isEditMode && container != null) {
            val userReg = File(container.rootDir, ".wine/user.reg")
            if (userReg.exists()) {
                WineRegistryEditor(userReg).use { reg ->
                    csmt = reg.getDwordValue("Software\\Wine\\Direct3D", "csmt", 3) ?: 3
                    val gpuId = reg.getDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", 1728) ?: 1728
                    gpuNamePos = findGpuNamePosition(ctx, gpuId)
                    offscreenRenderingMode = reg.getStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", "fbo") ?: "fbo"
                    strictShaderMath = reg.getDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", 1) ?: 1
                    videoMemorySize = reg.getStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", "2048") ?: "2048"
                    mouseWarpOverride = reg.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable") ?: "disable"
                    logPixels = reg.getDwordValue("Control Panel\\Desktop", "LogPixels", 96) ?: 96
                }
            }
        }
    }

    var currentTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.wine_version) to 0,
        stringResource(R.string.win_components) to 1,
        stringResource(R.string.environment_variables) to 2,
        stringResource(R.string.drives) to 3,
        stringResource(R.string.advanced) to 4,
        stringResource(R.string.registry_editor) to 5,
        stringResource(R.string.xr) to 6,
    )

    var showPreloader by remember { mutableStateOf(false) }
    var preloaderText by remember { mutableStateOf("") }

    // Состояния для Compose-диалогов конфигурации
    var showGraphicsConfigDialog by remember { mutableStateOf(false) }
    var showDxConfigDialog by remember { mutableStateOf(false) }
    var showAudioConfigDialog by remember { mutableStateOf(false) }

    var activeBox64PresetEditId by remember { mutableStateOf<String?>(null) }
    var activeFexcorePresetEditId by remember { mutableStateOf<String?>(null) }
    var showBox64PresetDialog by remember { mutableStateOf(false) }
    var showFexcorePresetDialog by remember { mutableStateOf(false) }

    // Списки доступных версий (из ресурсов, fallback — массивы по умолчанию)
    val graphicsDriverVersions = remember { ctx.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries).toList() }
    val dxvkVersions = remember { ctx.resources.getStringArray(R.array.dxvk_version_entries).toList() }

    var box64Versions by remember(isArm64EC) {
        mutableStateOf(loadBox64Versions(ctx, contentsManager, isArm64EC))
    }
    var fexcoreVersions by remember {
        mutableStateOf(loadFEXCoreVersions(ctx, contentsManager))
    }

    var presetsRefreshKey by remember { mutableStateOf(0) }

    val importBox64PresetLauncher = rememberLauncherForActivityResult<Array<String>, Uri?>(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { isStream ->
                    Box86_64PresetManager.importPreset("box64", ctx, isStream)
                }
                presetsRefreshKey++
                AppUtils.showToast(ctx, "Box64 Preset imported successfully.")
            } catch (e: Exception) {
                AppUtils.showToast(ctx, "Failed to import Box64 preset: ${e.message}")
            }
        }
    }

    val importFexcorePresetLauncher = rememberLauncherForActivityResult<Array<String>, Uri?>(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { isStream ->
                    FEXCorePresetManager.importPreset(ctx, isStream)
                }
                presetsRefreshKey++
                AppUtils.showToast(ctx, "FEXCore Preset imported successfully.")
            } catch (e: Exception) {
                AppUtils.showToast(ctx, "Failed to import FEXCore preset: ${e.message}")
            }
        }
    }

    var showProfilePreviewDialog by remember { mutableStateOf<JSONObject?>(null) }
    var previewText by remember { mutableStateOf("") }

    val exportProfileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val profileJson = buildContainerProfileJsonCompose(
                    name = name,
                    screenSize = if (isCustomScreen) "${customScreenWidth}x${customScreenHeight}" else (screenSize.split(" ").firstOrNull() ?: screenSize),
                    envVars = envVars,
                    graphicsDriver = graphicsDriver,
                    graphicsDriverConfig = graphicsDriverConfig,
                    dxwrapper = dxwrapper,
                    ddrawrapper = ddrawrapper,
                    dxwrapperConfig = dxwrapperConfig,
                    audioDriver = audioDriver,
                    audioDriverConfig = audioDriverConfig,
                    emulator = emulator,
                    winComponents = winComponents,
                    drives = drives,
                    fullscreenStretched = fullscreenStretched,
                    useUnixLibs = useUnixLibs,
                    cpuList = cpuList,
                    cpuListWoW64 = cpuListWoW64,
                    wow64Mode = wow64Mode,
                    startupSelection = startupSelection,
                    box64Version = box64Version,
                    box64Preset = box64Preset,
                    fexcoreVersion = fexcoreVersion,
                    fexcorePreset = fexcorePreset,
                    desktopTheme = desktopTheme,
                    rcfileId = rcfileId,
                    midiSoundFont = midiSoundFont,
                    lcAll = lcAll,
                    primaryController = primaryController,
                    controllerMapping = controllerMapping,
                    enableXInput = enableXInput,
                    enableDInput = enableDInput,
                    dinputMapperType = dinputMapperType,
                    csmt = csmt,
                    gpuNamePos = gpuNamePos,
                    offscreenRenderingMode = offscreenRenderingMode,
                    strictShaderMath = strictShaderMath,
                    videoMemorySize = videoMemorySize,
                    mouseWarpOverride = mouseWarpOverride,
                    logPixels = logPixels
                )
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(profileJson.toByteArray(Charsets.UTF_8))
                }
                AppUtils.showToast(ctx, R.string.container_profile_exported)
            } catch (e: Exception) {
                AppUtils.showToast(ctx, "Failed to export profile: ${e.message}")
            }
        }
    }

    val importProfileLauncher = rememberLauncherForActivityResult<Array<String>, Uri?>(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = ctx.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                if (content != null) {
                    val profile = JSONObject(content)
                    val settings = profile.optJSONObject("settings") ?: profile
                    
                    val lines = mutableListOf<String>()
                    val dxConfig = DXVKConfigDialog.parseConfig(settings.optString("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG))
                    val dxWrapperVal = settings.optString("dxwrapper", "-")
                    val dxVersion = if (dxWrapperVal.equals("vkd3d", ignoreCase = true)) {
                        dxConfig.get("vkd3dVersion", "-")
                    } else {
                        dxConfig.get("version", "-")
                    }
                    val graphicsDriverVersion = GraphicsDriverConfigDialog.getVersion(settings.optString("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG))
                    
                    val pName = profile.optString("profileName", "")
                    if (pName.isNotEmpty()) lines.add(ctx.getString(R.string.profile_name_label) + ": " + pName)
                    lines.add(ctx.getString(R.string.profile_source_container) + ": " + profile.optString("sourceContainerName", "-"))
                    lines.add(ctx.getString(R.string.screen_size) + ": " + settings.optString("screenSize", "-"))
                    lines.add(ctx.getString(R.string.graphics_driver) + ": " + settings.optString("graphicsDriver", "-") + " / " + graphicsDriverVersion)
                    lines.add("DX Wrapper: " + settings.optString("dxwrapper", "-") + " / " + dxVersion)
                    lines.add("DDraw/Glide: " + settings.optString("ddrawrapper", "-"))
                    lines.add(ctx.getString(R.string.audio_driver) + ": " + settings.optString("audioDriver", "-"))
                    lines.add("Emulator: " + settings.optString("emulator", "-"))
                    lines.add("Box64: " + settings.optString("box64Version", "-") + " / " + settings.optString("box64Preset", "-"))
                    lines.add("FEXCore: " + settings.optString("fexcoreVersion", "-") + " / " + settings.optString("fexcorePreset", "-"))
                    lines.add("Box64 preset config: " + (if (settings.has("box64PresetProfile")) ctx.getString(R.string.included) else ctx.getString(R.string.not_set)))
                    lines.add("FEXCore preset config: " + (if (settings.has("fexcorePresetProfile")) ctx.getString(R.string.included) else ctx.getString(R.string.not_set)))
                    lines.add(ctx.getString(R.string.startup_selection) + ": " + settings.optInt("startupSelection", 0))
                    lines.add(ctx.getString(R.string.audio_driver) + " config: " + (if (settings.has("audioDriverConfig")) ctx.getString(R.string.included) else ctx.getString(R.string.not_set)))
                    lines.add(ctx.getString(R.string.graphics_driver) + " config: " + (if (settings.has("graphicsDriverConfig")) ctx.getString(R.string.included) else ctx.getString(R.string.not_set)))
                    lines.add("DX config: " + (if (settings.has("dxwrapperConfig")) ctx.getString(R.string.included) else ctx.getString(R.string.not_set)))
                    lines.add("Win components: " + settings.optString("wincomponents", "-"))
                    lines.add("Drives: " + settings.optString("drives", "-"))
                    lines.add("Env Vars: " + settings.optString("envVars", "-"))
                    lines.add("MIDI: " + settings.optString("midiSoundFont", ctx.getString(R.string.disabled)))
                    lines.add("LC_ALL: " + settings.optString("lc_all", "-"))
                    lines.add("CSMT: " + (if (settings.optBoolean("csmtEnabled", true)) ctx.getString(R.string.enabled) else ctx.getString(R.string.disabled)))
                    lines.add("GPU device ID: " + settings.optInt("gpuDeviceId", 0))
                    lines.add("Offscreen: " + settings.optString("offscreenRenderingMode", "-"))
                    lines.add("Video memory: " + settings.optString("videoMemorySize", "-"))
                    lines.add("Mouse warp: " + settings.optString("mouseWarpOverride", "-"))
                    lines.add("Fullscreen stretched: " + (if (settings.optBoolean("fullscreenStretched", false)) ctx.getString(R.string.enabled) else ctx.getString(R.string.disabled)))
                    lines.add("UnixLibs: " + (if (settings.optBoolean("useUnixLibs", true)) ctx.getString(R.string.enabled) else ctx.getString(R.string.disabled)))
                    lines.add("WoW64 mode: " + (if (settings.optBoolean("wow64Mode", true)) ctx.getString(R.string.enabled) else ctx.getString(R.string.disabled)))
                    lines.add("Input flags: " + settings.optInt("inputType", 0))
                    lines.add("RC file id: " + settings.optInt("rcfileId", 0))
                    lines.add("XR primary controller: " + settings.optInt("primaryController", 1))
                    lines.add("XR mapping: " + (if (settings.has("controllerMapping")) ctx.getString(R.string.included) else ctx.getString(R.string.not_set)))
                    lines.add(ctx.getString(R.string.bundled_components) + ": " + profile.optInt("bundledComponentsCount", 0))
                    
                    previewText = lines.joinToString("\n")
                    showProfilePreviewDialog = settings
                }
            } catch (e: Exception) {
                AppUtils.showToast(ctx, "Failed to import profile: ${e.message}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditMode) R.string.edit_container else R.string.new_container)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    saveContainer(
                                            ctx = ctx, isEditMode = isEditMode, container = container,
                                            containerManager = containerManager, contentsManager = contentsManager,
                                            name = name,
                                            screenSize = if (isCustomScreen) "${customScreenWidth}x${customScreenHeight}" else (screenSize.split(" ").firstOrNull() ?: screenSize),
                                            envVars = envVars, graphicsDriver = graphicsDriver, graphicsDriverConfig = graphicsDriverConfig,
                                            displayRenderer = displayRenderer, sfCompatMode = sfCompatMode,
                                            dxwrapper = dxwrapper, ddrawrapper = ddrawrapper, dxwrapperConfig = dxwrapperConfig,
                                            audioDriver = audioDriver, audioDriverConfig = audioDriverConfig, emulator = emulator,
                                            wincomponents = winComponents, drives = drives, fullscreenStretched = fullscreenStretched,
                                            useUnixLibs = useUnixLibs,
                                            cpuList = cpuList, cpuListWoW64 = cpuListWoW64, wow64Mode = wow64Mode,
                                            startupSelection = startupSelection, box64Version = box64Version, box64Preset = box64Preset,
                                            fexcoreVersion = fexcoreVersion, fexcorePreset = fexcorePreset, desktopTheme = desktopTheme,
                                            rcfileId = rcfileId, midiSoundFont = midiSoundFont, lcAll = lcAll,
                                            primaryController = primaryController, controllerMapping = controllerMapping,
                                            wineVersion = wineVersion, enableXInput = enableXInput, enableDInput = enableDInput,
                                            dinputMapperType = dinputMapperType, sdl2Toggle = sdl2Toggle, legacyMode = legacyMode,
                                            csmt = csmt, gpuNamePos = gpuNamePos, offscreenRenderingMode = offscreenRenderingMode,
                                            strictShaderMath = strictShaderMath, videoMemorySize = videoMemorySize,
                                            mouseWarpOverride = mouseWarpOverride, logPixels = logPixels,
                                            onPreloader = { text -> showPreloader = true; preloaderText = text },
                                            onPreloaderClose = { showPreloader = false },
                                            onDone = onBack)
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                // Защита от среза кнопки системным баром/вырезом в ландшафте:
                // инсеты навигации прибавляются к отступам со всех сторон
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp, end = 4.dp),
            ) {
                Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // Такой же боковой отступ, как в «Управлении вводом»:
                // карточки не должны прилипать к краям экрана.
                .padding(
                    start = GlassTokens.RowPaddingH,
                    end = GlassTokens.RowPaddingH,
                    bottom = 88.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Верхний блок (вне вкладок) ----
                        SectionCard(title = stringResource(R.string.general), icon = Icons.Filled.Settings) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ,
                    shape = GlassFieldShape,
                    colors = glassFieldColors())
                ContainerSpinnerRow(
                    label = stringResource(R.string.screen_size),
                    entries = ctx.resources.getStringArray(R.array.screen_size_entries).toList(),
                    selected = screenSize,
                    onSelected = { screenSize = it; isCustomScreen = it == "Custom" },
                )
                if (isCustomScreen) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customScreenWidth, onValueChange = { customScreenWidth = it.filter { c -> c.isDigit() } },
                            label = { Text("W") }, singleLine = true, modifier = Modifier.weight(1f)
                        ,
                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                        OutlinedTextField(
                            value = customScreenHeight, onValueChange = { customScreenHeight = it.filter { c -> c.isDigit() } },
                            label = { Text("H") }, singleLine = true, modifier = Modifier.weight(1f)
                        ,
                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                    }
                }
                val lcAllEntries = remember { ctx.resources.getStringArray(R.array.some_lc_all).toList() }
                val lcAllNames = remember { ctx.resources.getStringArray(R.array.some_lc_all_names).toList() }
                val lcAllDisplayName = remember(lcAll) {
                    val code = lcAll.replace(".UTF-8", "")
                    val idx = lcAllEntries.indexOf(code)
                    if (idx >= 0) lcAllNames[idx] else lcAll
                }
                ContainerSpinnerRow(
                    label = stringResource(R.string.locale),
                    entries = lcAllNames, selected = lcAllDisplayName,
                    onSelected = { name ->
                        val idx = lcAllNames.indexOf(name)
                        if (idx >= 0) lcAll = lcAllEntries[idx] + ".UTF-8"
                    },
                )
                SwitchRow(stringResource(R.string.fullscreen_stretched), fullscreenStretched) { fullscreenStretched = it }
            }

            SectionCard(title = stringResource(R.string.graphics), icon = Icons.Filled.Image) {
                ContainerSpinnerRowWithConfigAndDownload(
                    label = stringResource(R.string.graphics_driver),
                    entries = wrapperEntries,
                    selected = graphicsDriver, onSelected = { graphicsDriver = it },
                    onConfigClick = { showGraphicsConfigDialog = true },
                    onDownloadClick = { showWrapperDownload = true },
                )
                ContainerSpinnerRowWithConfig(
                    label = stringResource(R.string.dxwrapper),
                    entries = ctx.resources.getStringArray(R.array.dxwrapper_entries).toList(),
                    selected = dxwrapper,
                    showConfigButton = dxwrapper.lowercase(Locale.ENGLISH) != "wined3d",
                    onSelected = { dxwrapper = it.lowercase(Locale.ENGLISH) },
                    onConfigClick = { showDxConfigDialog = true },
                )
                ContainerSpinnerRow(
                    label = stringResource(R.string.ddraw_wrapper),
                    entries = ctx.resources.getStringArray(R.array.ddrawrapper_entries).toList(),
                    selected = ddrawrapper,
                    onSelected = { ddrawrapper = it },
                )
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
            }

            SectionCard(title = stringResource(R.string.compatibility), icon = Icons.Filled.Build) {
                ContainerSpinnerRow(
                    label = stringResource(R.string.wine_version),
                    entries = wineVersions, selected = wineVersion, enabled = !isEditMode,
                    onSelected = { wineVersion = it },
                )
                ContainerSpinnerRow(
                    label = stringResource(R.string.emulator),
                    entries = ctx.resources.getStringArray(R.array.emulator_entries).toList(),
                    selected = if (emulator.lowercase(Locale.ENGLISH) == "fexcore") "FEXCore" else "Box64",
                    enabled = isArm64EC,
                    onSelected = { emulator = it.lowercase(Locale.ENGLISH) },
                )
                ContainerSpinnerRowWithConfig(
                    label = stringResource(R.string.audio_driver),
                    entries = ctx.resources.getStringArray(R.array.audio_driver_entries).toList(),
                    selected = audioDriver, onSelected = { audioDriver = it },
                    onConfigClick = { showAudioConfigDialog = true },
                )
            }

            // ---- Вкладки ----
            // Референс: текстовые табы с коротким подчёркиванием, активный —
            // жирный белый, неактивный — серый. Лента без плотной подложки.
            ScrollableTabRow(
                selectedTabIndex = currentTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                // contentColor задаёт и цвет штатного подчёркивания,
                // и цвет текста неактивных табов (M3 сам берёт от него alpha)
                contentColor = glassAccent(),
                divider = { },
            ) {
                tabs.forEachIndexed { index, (title, _) ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        text = {
                            Text(
                                title,
                                maxLines = 1,
                                softWrap = false,
                                fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp,
                                color = if (currentTab == index) glassTitleColor() else glassDescColor(),
                            )
                        },
                    )
                }
            }

            when (currentTab) {
                0 -> WineConfigTab(
                    desktopTheme = desktopTheme, onDesktopThemeChange = { desktopTheme = it },
                    csmt = csmt, onCsmtChange = { csmt = it },
                    gpuNamePos = gpuNamePos, onGpuNamePosChange = { gpuNamePos = it },
                    offscreenRenderingMode = offscreenRenderingMode, onOffscreenRenderingModeChange = { offscreenRenderingMode = it },
                    strictShaderMath = strictShaderMath, onStrictShaderMathChange = { strictShaderMath = it },
                    videoMemorySize = videoMemorySize, onVideoMemorySizeChange = { videoMemorySize = it },
                    mouseWarpOverride = mouseWarpOverride, onMouseWarpOverrideChange = { mouseWarpOverride = it },
                    logPixels = logPixels, onLogPixelsChange = { logPixels = it },
                    onExportProfile = { exportProfileLauncher.launch(name.ifEmpty { "container-profile" } + ".json") },
                    onImportProfile = { importProfileLauncher.launch(arrayOf("application/json", "*/*")) }
                )
                1 -> WinComponentsTab(winComponents = winComponents, onWinComponentsChange = { winComponents = it })
                2 -> EnvVarsTab(envVars = envVars, onEnvVarsChange = { envVars = it })
                3 -> DrivesTab(drives = drives, onDrivesChange = { drives = it })
                4 -> AdvancedTab(
                    box64Version = box64Version, onBox64VersionChange = { box64Version = it },
                    box64Versions = box64Versions,
                    box64Preset = box64Preset, onBox64PresetChange = { box64Preset = it },
                    rcfileId = rcfileId, onRcfileIdChange = { rcfileId = it },
                    fexcoreVersion = fexcoreVersion, onFexcoreVersionChange = { fexcoreVersion = it },
                    fexcoreVersions = fexcoreVersions,
                    fexcorePreset = fexcorePreset, onFexcorePresetChange = { fexcorePreset = it },
                    useUnixLibs = useUnixLibs, onUseUnixLibsChange = { useUnixLibs = it },
                    startupSelection = startupSelection, onStartupSelectionChange = { startupSelection = it },
                    wow64Mode = wow64Mode, onWow64ModeChange = { wow64Mode = it },
                    cpuList = cpuList, onCpuListChange = { cpuList = it },
                    cpuListWoW64 = cpuListWoW64, onCpuListWoW64Change = { cpuListWoW64 = it },
                    legacyMode = legacyMode,
                    enableXInput = enableXInput, onEnableXInputChange = { enableXInput = it },
                    enableDInput = enableDInput, onEnableDInputChange = { enableDInput = it },
                    dinputMapperType = dinputMapperType, onDinputMapperTypeChange = { dinputMapperType = it },
                    sdl2Toggle = sdl2Toggle, onSdl2ToggleChange = { sdl2Toggle = it },
                    presetsRefreshKey = presetsRefreshKey,
                    isArm64EC = isArm64EC,
                    emulator = emulator,
                    onBox64PresetAdd = {
                        activeBox64PresetEditId = null
                        showBox64PresetDialog = true
                    },
                    onBox64PresetEdit = {
                        activeBox64PresetEditId = box64Preset
                        showBox64PresetDialog = true
                    },
                    onBox64PresetDuplicate = {
                        Box86_64PresetManager.duplicatePreset("box64", ctx, box64Preset)
                        presetsRefreshKey++
                    },
                    onBox64PresetRemove = {
                        if (!box64Preset.startsWith("custom")) {
                            AppUtils.showToast(ctx, R.string.you_cannot_remove_this_preset)
                        } else {
                            Box86_64PresetManager.removePreset("box64", ctx, box64Preset)
                            presetsRefreshKey++
                        }
                    },
                    onBox64PresetExport = {
                        Box86_64PresetManager.exportPreset("box64", ctx, box64Preset)
                    },
                    onBox64PresetImport = {
                        importBox64PresetLauncher.launch(arrayOf("*/*"))
                    },
                    onFexcorePresetAdd = {
                        activeFexcorePresetEditId = null
                        showFexcorePresetDialog = true
                    },
                    onFexcorePresetEdit = {
                        activeFexcorePresetEditId = fexcorePreset
                        showFexcorePresetDialog = true
                    },
                    onFexcorePresetDuplicate = {
                        FEXCorePresetManager.duplicatePreset(ctx, fexcorePreset)
                        presetsRefreshKey++
                    },
                    onFexcorePresetRemove = {
                        if (!fexcorePreset.startsWith("custom")) {
                            AppUtils.showToast(ctx, R.string.you_cannot_remove_this_preset)
                        } else {
                            FEXCorePresetManager.removePreset(ctx, fexcorePreset)
                            presetsRefreshKey++
                        }
                    },
                    onFexcorePresetExport = {
                        FEXCorePresetManager.exportPreset(ctx, fexcorePreset)
                    },
                    onFexcorePresetImport = {
                        importFexcorePresetLauncher.launch(arrayOf("*/*"))
                    },
                    onBox64VersionDownload = { showBox64Download = true },
                    onFexcoreVersionDownload = { showFexcoreDownload = true }
                )
                5 -> RegistryEditorTab(
                    containerRootDir = container?.rootDir,
                    wineVersion = wineVersion,
                    wineArch = wineInfo.getArch(),
                    onToast = { AppUtils.showToast(ctx, it) },
                )
                6 -> XRTab(
                    primaryController = primaryController, onPrimaryControllerChange = { primaryController = it },
                    controllerMapping = controllerMapping, onControllerMappingChange = { controllerMapping = it },
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showPreloader) {
        AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text(preloaderText) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        )
    }

    // ---- Compose-диалоги конфигурации ----
    if (showGraphicsConfigDialog) {
        GraphicsDriverConfigDialogCompose(
            context = ctx,
            graphicsDriver = graphicsDriver,
            initialConfig = graphicsDriverConfig,
            availableVersions = graphicsDriverVersions,
            onDismiss = { showGraphicsConfigDialog = false },
            onConfirm = { newConfig ->
                graphicsDriverConfig = newConfig
                showGraphicsConfigDialog = false
            },
        )
    }
    if (showDxConfigDialog) {
        if (dxwrapper == "dxvk") {
            DXVKConfigDialogCompose(
                context = ctx,
                initialConfig = dxwrapperConfig,
                availableVersions = dxvkVersions,
                onDismiss = { showDxConfigDialog = false },
                onConfirm = { newConfig ->
                    dxwrapperConfig = newConfig
                    showDxConfigDialog = false
                },
            )
        } else if (dxwrapper == "vkd3d") {
            VKD3DConfigDialogCompose(
                context = ctx,
                initialConfig = dxwrapperConfig,
                onDismiss = { showDxConfigDialog = false },
                onConfirm = { newConfig ->
                    dxwrapperConfig = newConfig
                    showDxConfigDialog = false
                },
            )
        }
    }
    if (showAudioConfigDialog) {
        AudioDriverConfigDialogCompose(
            initialConfig = audioDriverConfig,
            onDismiss = { showAudioConfigDialog = false },
            onConfirm = { newConfig ->
                audioDriverConfig = newConfig
                showAudioConfigDialog = false
            },
        )
    }

    if (showBox64PresetDialog) {
        EditPresetDialog(
            prefix = "box64",
            presetId = activeBox64PresetEditId,
            onDismiss = { showBox64PresetDialog = false },
            onConfirm = {
                presetsRefreshKey++
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
                presetsRefreshKey++
                showFexcorePresetDialog = false
            }
        )
    }

    if (showBox64Download) {
        // На arm64ec wine скачивается WOWBox64, а не обычный Box64
        val box64ContentType = if (isArm64EC) {
            com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
        } else {
            com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_BOX64
        }
        val box64DisplayName = if (isArm64EC) "WOWBox64" else "Box64"
        ContentDownloadDialogCompose(
            context = ctx,
            contentType = box64ContentType,
            displayName = box64DisplayName,
            onDismiss = { showBox64Download = false },
            onInstalled = { installedVersion ->
                box64Version = installedVersion
                box64Versions = loadBox64Versions(ctx, contentsManager, isArm64EC)
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
                fexcoreVersions = loadFEXCoreVersions(ctx, contentsManager)
            }
        )
    }

    if (showWrapperDownload) {
        WrapperDownloadDialog(
            currentDriver = graphicsDriver,
            onDismiss = {
                showWrapperDownload = false
                wrapperEntries = com.winlator.cmod.contents.WrapperManager.driverEntries(ctx)
            },
            onInstalled = { id ->
                wrapperEntries = com.winlator.cmod.contents.WrapperManager.driverEntries(ctx)
                graphicsDriver = id
            }
        )
    }

    if (showProfilePreviewDialog != null) {
        val settings = showProfilePreviewDialog!!
        AlertDialog(
            onDismissRequest = { showProfilePreviewDialog = null },
            title = { Text(stringResource(R.string.container_profile_preview)) },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(text = previewText, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showProfilePreviewDialog = null
                    try {
                        if (settings.has("screenSize")) screenSize = settings.getString("screenSize")
                        if (settings.has("envVars")) envVars = settings.getString("envVars")
                        if (settings.has("cpuList")) cpuList = settings.getString("cpuList")
                        if (settings.has("cpuListWoW64")) cpuListWoW64 = settings.getString("cpuListWoW64")
                        if (settings.has("graphicsDriver")) graphicsDriver = settings.getString("graphicsDriver")
                        if (settings.has("graphicsDriverConfig")) graphicsDriverConfig = settings.getString("graphicsDriverConfig")
                        if (settings.has("dxwrapper")) dxwrapper = settings.getString("dxwrapper")
                        if (settings.has("ddrawrapper")) ddrawrapper = settings.getString("ddrawrapper")
                        if (settings.has("dxwrapperConfig")) dxwrapperConfig = settings.getString("dxwrapperConfig")
                        if (settings.has("audioDriver")) audioDriver = settings.getString("audioDriver")
                        if (settings.has("audioDriverConfig")) audioDriverConfig = settings.getString("audioDriverConfig")
                        if (settings.has("emulator")) emulator = settings.getString("emulator")
                        if (settings.has("wincomponents")) winComponents = settings.getString("wincomponents")
                        if (settings.has("drives")) drives = settings.getString("drives")
                        if (settings.has("fullscreenStretched")) fullscreenStretched = settings.getBoolean("fullscreenStretched")
                        if (settings.has("wow64Mode")) wow64Mode = settings.getBoolean("wow64Mode")
                        if (settings.has("startupSelection")) startupSelection = settings.getInt("startupSelection")
                        if (settings.has("box64Version")) box64Version = settings.getString("box64Version")
                        if (settings.has("box64Preset")) box64Preset = settings.getString("box64Preset")
                        if (settings.has("fexcoreVersion")) fexcoreVersion = settings.getString("fexcoreVersion")
                        if (settings.has("fexcorePreset")) fexcorePreset = settings.getString("fexcorePreset")
                        if (settings.has("desktopTheme")) desktopTheme = settings.getString("desktopTheme")
                        if (settings.has("rcfileId")) rcfileId = settings.getInt("rcfileId")
                        if (settings.has("midiSoundFont")) midiSoundFont = settings.getString("midiSoundFont")
                        if (settings.has("lc_all")) lcAll = settings.getString("lc_all")
                        if (settings.has("primaryController")) primaryController = settings.getInt("primaryController")
                        if (settings.has("controllerMapping")) controllerMapping = settings.getString("controllerMapping")
                        
                        val inputType = settings.optInt("inputType", 0)
                        enableXInput = (inputType and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0
                        enableDInput = (inputType and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0
                        dinputMapperType = if ((inputType and WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt()) != 0) 1 else 0

                        if (settings.has("csmtEnabled")) csmt = if (settings.optBoolean("csmtEnabled", true)) 3 else 0
                        if (settings.has("gpuDeviceId")) {
                            val deviceId = settings.getInt("gpuDeviceId")
                            gpuNamePos = findGpuNamePosition(ctx, deviceId)
                        }
                        if (settings.has("offscreenRenderingMode")) offscreenRenderingMode = settings.getString("offscreenRenderingMode")
                        if (settings.has("strictShaderMath")) strictShaderMath = settings.getInt("strictShaderMath")
                        if (settings.has("videoMemorySize")) videoMemorySize = settings.getString("videoMemorySize")
                        if (settings.has("mouseWarpOverride")) mouseWarpOverride = settings.getString("mouseWarpOverride")
                        if (settings.has("logPixels")) logPixels = settings.getInt("logPixels")

                        AppUtils.showToast(ctx, R.string.container_profile_applied)
                    } catch (e: Exception) {
                        AppUtils.showToast(ctx, "Failed to apply profile: ${e.message}")
                    }
                }) {
                    Text(stringResource(R.string.apply_profile))
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfilePreviewDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ---- Вспомогательные функции для config-диалогов ----
// Диалоги конфигурации реализованы на Compose (см. ConfigDialogs.kt).
// Внутри Compose-кода достаточно вызвать GraphicsDriverConfigDialogCompose / DXVKConfigDialogCompose /
// AudioDriverConfigDialogCompose. Эти функции оставлены как заглушки-обёртки для совместимости
// сигнатур (используются из onConfigClick = { showGraphicsDriverConfigDialog(...) }), но
// реальный вызов перенесён в сам @Composable (см. блок overlay в конце ContainerEditScreen).
private fun showGraphicsDriverConfigDialog(
    ctx: Context, graphicsDriver: String, currentConfig: String, onResult: (String) -> Unit
) { /* вызов перенесён в ContainerEditScreen через state showGraphicsConfigDialog */ }

private fun showDxWrapperConfigDialog(
    ctx: Context, dxwrapper: String, currentConfig: String, onResult: (String) -> Unit
) { /* вызов перенесён в ContainerEditScreen через state showDxConfigDialog */ }

private fun showAudioDriverConfigDialog(
    ctx: Context, currentConfig: String, onResult: (String) -> Unit
) { /* вызов перенесён в ContainerEditScreen через state showAudioConfigDialog */ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WrapperDownloadDialog(
    currentDriver: String,
    onDismiss: () -> Unit,
    onInstalled: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val manager = remember { com.winlator.cmod.contents.WrapperManager(ctx) }
    var entries by remember { mutableStateOf<List<com.winlator.cmod.contents.WrapperCatalogEntry>?>(null) }
    var source by remember { mutableStateOf(com.winlator.cmod.contents.WrapperCatalog.Source.NONE) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var installedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installedVersions by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var deleteCatalogId by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingInspection by remember { mutableStateOf<com.winlator.cmod.contents.WrapperManager.Inspection?>(null) }
    var importNameDraft by remember { mutableStateOf("") }

    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    fun postProgress(p: Int) { mainHandler.post { progress = p } }

    suspend fun refreshInstalled() {
        val ids = withContext(Dispatchers.IO) { runCatching { manager.installedCatalogIds() }.getOrDefault(emptySet()) }
        installedIds = ids
        installedVersions = withContext(Dispatchers.IO) { runCatching { ids.associateWith { manager.installedCatalogVersion(it) } }.getOrDefault(emptyMap()) }
    }

    LaunchedEffect(Unit) {
        val res = withContext(Dispatchers.IO) { com.winlator.cmod.contents.WrapperCatalog.loadCached(ctx) }
        entries = res.entries
        source = res.source
        if (res.entries.isEmpty() && res.source == com.winlator.cmod.contents.WrapperCatalog.Source.NONE) {
            errorMsg = "Каталог пуст или нет сети"
        }
        refreshInstalled()
    }

    val offline = source != com.winlator.cmod.contents.WrapperCatalog.Source.NETWORK
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    val inspection = withContext(Dispatchers.IO) { manager.inspectUri(uri) }
                    if (!inspection.valid) {
                        com.winlator.cmod.core.AppUtils.showToast(ctx, ctx.getString(R.string.wrapper_invalid_toast))
                    } else {
                        val base = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                            ?.takeIf { it.isNotBlank() } ?: "Imported wrapper"
                        importNameDraft = base
                        pendingInspection = inspection
                        pendingImportUri = uri
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wrapper_catalog_dialog_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (entries == null) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (entries!!.isEmpty()) {
                    Text(errorMsg ?: stringResource(R.string.wrapper_catalog_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (source != com.winlator.cmod.contents.WrapperCatalog.Source.NONE) {
                        Text("Источник: $source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Источник: $source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (offline) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(if (source == com.winlator.cmod.contents.WrapperCatalog.Source.CACHE) R.string.wrapper_catalog_offline_cache else R.string.wrapper_catalog_offline),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    GlassPill(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            filePicker.launch(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.wrapper_import_title))
                    }
                    Spacer(Modifier.height(4.dp))
                    entries!!.forEach { e ->
                        val isDownloading = downloadingId == e.id
                        val installed = e.id in installedIds
                        val updateAvailable = installed && e.version > (installedVersions[e.id] ?: 0)
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                            shape = RoundedCornerShape(GlassTokens.CardRadius),
                            border = glassCardBorder(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(e.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (installed && !updateAvailable) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.wrapper_installed), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    if (installed) {
                                        IconButton(onClick = { deleteCatalogId = e.id }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.wrapper_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                if (updateAvailable) {
                                    Spacer(Modifier.height(3.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                                        Text(stringResource(R.string.wrapper_update_available), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                } else if (installed) {
                                    Spacer(Modifier.height(3.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                                        Text(stringResource(R.string.wrapper_installed), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Автор: ${e.author} • v${e.version} • ${e.fileSize/1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                if (isDownloading) {
                                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                                    Text(if (progress >= 100) "${stringResource(R.string.wrapper_catalog_installing)}…" else "${stringResource(R.string.wrapper_catalog_downloading)}… $progress%", style = MaterialTheme.typography.bodySmall)
                                } else if (!updateAvailable && installed) {
                                    // актуальная версия уже стоит — кнопка не нужна
                                } else {
                                    GlassSolidPill(
                                        enabled = !offline && downloadingId == null,
                                        onClick = {
                                            downloadingId = e.id
                                            progress = 0
                                            errorMsg = null
                                            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                                                val id = withContext(Dispatchers.IO) {
                                                    com.winlator.cmod.contents.WrapperCatalogDownloader.install(ctx, e) { p -> postProgress(p) }
                                                }
                                                if (id != null) {
                                                    com.winlator.cmod.core.AppUtils.showToast(ctx, ctx.getString(R.string.wrapper_catalog_download_ok, e.name))
                                                    refreshInstalled()
                                                    onInstalled(e.name)
                                                    onDismiss()
                                                } else {
                                                    errorMsg = ctx.getString(R.string.wrapper_catalog_download_failed, e.name)
                                                    downloadingId = null
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(if (updateAvailable) stringResource(R.string.wrapper_update) else stringResource(R.string.wrapper_catalog_download)) }
                                }
                            }
                        }
                    }
                    if (errorMsg != null) Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.wrapper_catalog_close)) } }
    )

    val delId = deleteCatalogId
    if (delId != null) {
        AlertDialog(
            onDismissRequest = { deleteCatalogId = null },
            title = { Text(stringResource(R.string.wrapper_delete_confirm_title)) },
            text = { Text(stringResource(R.string.wrapper_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                        val removedLabel = withContext(Dispatchers.IO) {
                            val wm = com.winlator.cmod.contents.WrapperManager(ctx)
                            val impId = runCatching { wm.findImportByCatalogId(delId) }.getOrNull()
                            if (impId != null) {
                                val label = entries?.firstOrNull { it.id == delId }?.name
                                wm.deleteImported(impId)
                                label ?: impId
                            } else {
                                for (slot in com.winlator.cmod.contents.WrapperManager.SLOTS) {
                                    if (delId == runCatching { wm.slotCatalogId(slot.fileName) }.getOrNull()) { wm.removeOverride(slot.fileName); break }
                                }
                                entries?.firstOrNull { it.id == delId }?.name
                            }
                        }
                        deleteCatalogId = null
                        refreshInstalled()
                        if (removedLabel != null && (currentDriver == removedLabel || currentDriver == delId)) {
                            onInstalled(com.winlator.cmod.container.Container.DEFAULT_GRAPHICS_DRIVER)
                        }
                    }
                }) { Text(stringResource(R.string.wrapper_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteCatalogId = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }

    val importUri = pendingImportUri
    val inspection = pendingInspection
    if (importUri != null && inspection != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null; pendingInspection = null },
            title = { Text(stringResource(R.string.wrapper_import_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.wrapper_import_name_message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = importNameDraft, onValueChange = { importNameDraft = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = importNameDraft.trim()
                    if (name.isEmpty()) {
                        com.winlator.cmod.core.AppUtils.showToast(ctx, ctx.getString(R.string.wrapper_import_name_empty_toast)); return@TextButton
                    }
                    if (manager.isReservedIdentifier(com.winlator.cmod.core.StringUtils.parseIdentifier(name))) {
                        com.winlator.cmod.core.AppUtils.showToast(ctx, ctx.getString(R.string.wrapper_import_reserved_toast)); return@TextButton
                    }
                    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                        val id = withContext(Dispatchers.IO) { manager.importWrapper(importUri, name) }
                        pendingImportUri = null; pendingInspection = null
                        if (id != null) {
                            com.winlator.cmod.core.AppUtils.showToast(ctx, ctx.getString(R.string.wrapper_imported_toast))
                            refreshInstalled()
                            onInstalled(name)
                            onDismiss()
                        } else {
                            com.winlator.cmod.core.AppUtils.showToast(ctx, ctx.getString(R.string.wrapper_invalid_toast))
                        }
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null; pendingInspection = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

// ---- Загрузка данных ----

private fun loadWineVersions(ctx: Context, contentsManager: ContentsManager): List<String> {
    val list = mutableListOf<String>()
    // Все встроенные версии из массива (proton-9.0-x86_64, proton-9.0-arm64ec, ...)
    val versions = ctx.resources.getStringArray(R.array.wine_entries)
    list.addAll(versions)
    // Установленные профили Wine (только установленные, без дубликатов)
    contentsManager.syncContents()
    val profiles = contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE) ?: emptyList()
    val deduped = linkedMapOf<String, String>()
    for (profile in profiles) {
        val installed = profile.remoteUrl == null || ContentsManager.getInstallDir(ctx, profile).exists()
        if (!installed) continue
        val entryName = ContentsManager.getEntryName(profile)
        deduped[entryName] = entryName
    }
    list.addAll(deduped.values)
    return list.distinct()
}

private fun loadBox64Versions(ctx: Context, manager: ContentsManager, isArm64EC: Boolean): List<String> {
    manager.syncContents()
    val list = mutableListOf<String>()
    val originalItems = ctx.resources.getStringArray(
        if (isArm64EC) R.array.wowbox64_version_entries else R.array.box64_version_entries
    )
    list.addAll(originalItems)

    val contentType = if (isArm64EC) ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64 else ContentProfile.ContentType.CONTENT_TYPE_BOX64
    val profiles = manager.getProfiles(contentType) ?: emptyList()
    for (profile in profiles) {
        if (profile.remoteUrl == null || ContentsManager.getInstallDir(ctx, profile).exists()) {
            val entryName = ContentsManager.getEntryName(profile)
            val firstDashIndex = entryName.indexOf('-')
            list.add(if (firstDashIndex >= 0) entryName.substring(firstDashIndex + 1) else entryName)
        }
    }
    return list.distinct()
}

private fun loadFEXCoreVersions(ctx: Context, manager: ContentsManager): List<String> {
    manager.syncContents()
    val list = mutableListOf<String>()
    val originalItems = ctx.resources.getStringArray(R.array.fexcore_version_entries)
    list.addAll(originalItems)

    val profiles = manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE) ?: emptyList()
    for (profile in profiles) {
        if (profile.remoteUrl == null || ContentsManager.getInstallDir(ctx, profile).exists()) {
            val entryName = ContentsManager.getEntryName(profile)
            val firstDashIndex = entryName.indexOf('-')
            list.add(if (firstDashIndex >= 0) entryName.substring(firstDashIndex + 1) else entryName)
        }
    }
    return list.distinct()
}

private fun loadGpuCards(ctx: Context): JSONArray {
    return try { JSONArray(com.winlator.cmod.core.FileUtils.readString(ctx, "gpu_cards.json")) }
    catch (_: Exception) { JSONArray() }
}

private fun findGpuNamePosition(ctx: Context, gpuId: Int): Int {
    val json = loadGpuCards(ctx)
    for (i in 0 until json.length()) {
        val obj = json.optJSONObject(i) ?: continue
        if (obj.optInt("deviceID", 0) == gpuId) return i
    }
    return 0
}

private fun getGpuId(ctx: Context, position: Int): Int {
    val json = loadGpuCards(ctx)
    if (position in 0 until json.length()) {
        return json.optJSONObject(position)?.optInt("deviceID", 1728) ?: 1728
    }
    return 1728
}

private fun getGpuVendorId(ctx: Context, position: Int): Int {
    val json = loadGpuCards(ctx)
    if (position in 0 until json.length()) {
        return json.optJSONObject(position)?.optInt("vendorID", 0x1002) ?: 0x1002
    }
    return 0x1002
}

private val SDL2_ENV_VARS = listOf(
    "SDL_JOYSTICK_WGI=0", "SDL_XINPUT_ENABLED=1", "SDL_JOYSTICK_RAWINPUT=0",
    "SDL_JOYSTICK_HIDAPI=1", "SDL_DIRECTINPUT_ENABLED=0", "SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS=1",
    "SDL_HINT_FORCE_RAISEWINDOW=0", "SDL_ALLOW_TOPMOST=0", "SDL_MOUSE_FOCUS_CLICKTHROUGH=1"
)

private fun saveContainer(
    ctx: Context, isEditMode: Boolean, container: Container?,
    containerManager: ContainerManager, contentsManager: ContentsManager,
    name: String, screenSize: String, envVars: String,
    graphicsDriver: String, graphicsDriverConfig: String,
    displayRenderer: String, sfCompatMode: Boolean,
    dxwrapper: String, ddrawrapper: String, dxwrapperConfig: String,
    audioDriver: String, audioDriverConfig: String, emulator: String,
    wincomponents: String, drives: String, fullscreenStretched: Boolean,
    useUnixLibs: Boolean,
    cpuList: String, cpuListWoW64: String, wow64Mode: Boolean,
    startupSelection: Int, box64Version: String, box64Preset: String,
    fexcoreVersion: String, fexcorePreset: String, desktopTheme: String,
    rcfileId: Int, midiSoundFont: String, lcAll: String,
    primaryController: Int, controllerMapping: String, wineVersion: String,
    enableXInput: Boolean, enableDInput: Boolean, dinputMapperType: Int,
    sdl2Toggle: Boolean, legacyMode: Boolean,
    csmt: Int, gpuNamePos: Int, offscreenRenderingMode: String,
    strictShaderMath: Int, videoMemorySize: String, mouseWarpOverride: String, logPixels: Int,
    onPreloader: (String) -> Unit, onPreloaderClose: () -> Unit, onDone: () -> Unit,
) {
    // Обработка SDL2 env vars
    var finalEnvVars = envVars
    if (sdl2Toggle) {
        for (ev in SDL2_ENV_VARS) {
            if (!finalEnvVars.contains(ev)) finalEnvVars += (if (finalEnvVars.isEmpty()) "" else " ") + ev
        }
    } else {
        for (ev in SDL2_ENV_VARS) {
            finalEnvVars = finalEnvVars.replace(ev, "").replace("\\s{2,}".toRegex(), " ").trim()
        }
    }

    // Input type
    var finalInputType = 0
    if (legacyMode) {
        finalInputType = WinHandler.DEFAULT_INPUT_TYPE.toInt()
    } else {
        if (enableXInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
        if (enableDInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
        finalInputType = finalInputType or (if (dinputMapperType == 0) WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt() else WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt())
    }

    if (isEditMode && container != null) {
        container.name = name
        container.screenSize = screenSize
        container.envVars = finalEnvVars
        container.cpuList = cpuList
        container.cpuListWoW64 = cpuListWoW64
        container.graphicsDriver = graphicsDriver
        container.graphicsDriverConfig = graphicsDriverConfig
        container.displayRenderer = displayRenderer
        container.sfCompatMode = sfCompatMode
        container.dxWrapper = dxwrapper
        container.dDrawWrapper = ddrawrapper
        container.dxWrapperConfig = dxwrapperConfig
        container.audioDriver = audioDriver
        container.audioDriverConfig = audioDriverConfig
        container.emulator = emulator
        container.winComponents = wincomponents
        container.drives = drives
        container.isFullscreenStretched = fullscreenStretched
        container.isUseUnixLibs = useUnixLibs
        container.inputType = finalInputType
        container.isWoW64Mode = wow64Mode
        container.startupSelection = startupSelection.toByte()
        container.box64Version = box64Version
        container.box64Preset = box64Preset
        container.setFEXCoreVersion(fexcoreVersion)
        container.setFEXCorePreset(fexcorePreset)
        container.desktopTheme = desktopTheme
        container.setRcfileId(rcfileId)
        container.setMidiSoundFont(midiSoundFont)
        container.setLC_ALL(lcAll)
        container.primaryController = primaryController
        container.setControllerMapping(controllerMapping)
        container.saveData()

        saveWineRegistryKeys(ctx, container, csmt, gpuNamePos, offscreenRenderingMode, strictShaderMath, videoMemorySize, mouseWarpOverride, logPixels)
        onDone()
    } else {
        onPreloader(ctx.getString(R.string.creating_container))
        val data = JSONObject()
        try {
            data.put("name", name)
            data.put("screenSize", screenSize)
            data.put("envVars", finalEnvVars)
            data.put("cpuList", cpuList)
            data.put("cpuListWoW64", cpuListWoW64)
            data.put("graphicsDriver", graphicsDriver)
            data.put("graphicsDriverConfig", graphicsDriverConfig)
            data.put("displayRenderer", displayRenderer)
            data.put("sfCompatMode", sfCompatMode)
            data.put("dxwrapper", dxwrapper)
            data.put("ddrawrapper", ddrawrapper)
            data.put("dxwrapperConfig", dxwrapperConfig)
            data.put("audioDriver", audioDriver)
            data.put("audioDriverConfig", audioDriverConfig)
            data.put("emulator", emulator)
            data.put("wincomponents", wincomponents)
            data.put("drives", drives)
            data.put("fullscreenStretched", fullscreenStretched)
            data.put("useUnixLibs", useUnixLibs)
            data.put("inputType", finalInputType)
            data.put("wow64Mode", wow64Mode)
            data.put("startupSelection", startupSelection)
            data.put("box64Version", box64Version)
            data.put("box64Preset", box64Preset)
            data.put("fexcoreVersion", fexcoreVersion)
            data.put("fexcorePreset", fexcorePreset)
            data.put("desktopTheme", desktopTheme)
            data.put("rcfileId", rcfileId)
            data.put("midiSoundFont", midiSoundFont)
            data.put("lc_all", lcAll)
            data.put("primaryController", primaryController)
            data.put("controllerMapping", controllerMapping)
            data.put("wineVersion", wineVersion)
            data.put("csmtEnabled", csmt)
            data.put("gpuDeviceId", getGpuId(ctx, gpuNamePos))
            data.put("offscreenRenderingMode", offscreenRenderingMode)
            data.put("strictShaderMath", strictShaderMath)
            data.put("videoMemorySize", videoMemorySize)
            data.put("mouseWarpOverride", mouseWarpOverride)
        } catch (e: Exception) { android.widget.Toast.makeText(ctx, e.message, android.widget.Toast.LENGTH_LONG).show() }

        containerManager.createContainerAsync(data, contentsManager) { createdContainer ->
            if (createdContainer != null) {
                saveWineRegistryKeys(ctx, createdContainer, csmt, gpuNamePos, offscreenRenderingMode, strictShaderMath, videoMemorySize, mouseWarpOverride, logPixels)
            }
            (ctx as? Activity)?.runOnUiThread {
                onPreloaderClose()
                onDone()
            }
        }
    }
}

private fun saveWineRegistryKeys(
    ctx: Context, container: Container,
    csmt: Int, gpuNamePos: Int, offscreenRenderingMode: String,
    strictShaderMath: Int, videoMemorySize: String, mouseWarpOverride: String, logPixels: Int,
) {
    val userReg = File(container.rootDir, ".wine/user.reg")
    if (!userReg.exists()) return
    val gpuId = getGpuId(ctx, gpuNamePos)
    val gpuVendorId = getGpuVendorId(ctx, gpuNamePos)
    WineRegistryEditor(userReg).use { reg ->
        reg.setDwordValue("Software\\Wine\\Direct3D", "csmt", if (csmt == 0) 0 else 3)
        reg.setDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", gpuId)
        reg.setDwordValue("Software\\Wine\\Direct3D", "VideoPciVendorID", gpuVendorId)
        reg.setStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", offscreenRenderingMode)
        reg.setDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", strictShaderMath)
        reg.setStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", videoMemorySize)
        reg.setStringValue("Software\\Wine\\Direct3D", "shader_backend", "glsl")
        reg.setStringValue("Software\\Wine\\Direct3D", "UseGLSL", "enabled")
        reg.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", mouseWarpOverride)
        reg.setDwordValue("Control Panel\\Desktop", "LogPixels", logPixels)
    }
}

private fun buildContainerProfileJsonCompose(
    name: String, screenSize: String, envVars: String,
    graphicsDriver: String, graphicsDriverConfig: String,
    dxwrapper: String, ddrawrapper: String, dxwrapperConfig: String,
    audioDriver: String, audioDriverConfig: String, emulator: String,
    winComponents: String, drives: String, fullscreenStretched: Boolean,
    useUnixLibs: Boolean,
    cpuList: String, cpuListWoW64: String, wow64Mode: Boolean,
    startupSelection: Int, box64Version: String, box64Preset: String,
    fexcoreVersion: String, fexcorePreset: String, desktopTheme: String,
    rcfileId: Int, midiSoundFont: String, lcAll: String,
    primaryController: Int, controllerMapping: String,
    enableXInput: Boolean, enableDInput: Boolean, dinputMapperType: Int,
    csmt: Int, gpuNamePos: Int, offscreenRenderingMode: String,
    strictShaderMath: Int, videoMemorySize: String, mouseWarpOverride: String, logPixels: Int
): String {
    val profile = JSONObject()
    profile.put("profileType", "container-profile")
    profile.put("schemaVersion", 1.0)
    profile.put("profileName", name)
    profile.put("sourceContainerName", name)
    profile.put("exportedAt", System.currentTimeMillis())

    val settings = JSONObject()
    settings.put("screenSize", screenSize)
    settings.put("envVars", envVars)
    settings.put("cpuList", cpuList)
    settings.put("cpuListWoW64", cpuListWoW64)
    settings.put("graphicsDriver", graphicsDriver)
    settings.put("graphicsDriverConfig", graphicsDriverConfig)
    settings.put("dxwrapper", dxwrapper)
    settings.put("ddrawrapper", ddrawrapper)
    settings.put("dxwrapperConfig", dxwrapperConfig)
    settings.put("audioDriver", audioDriver)
    settings.put("audioDriverConfig", audioDriverConfig)
    settings.put("emulator", emulator)
    settings.put("wincomponents", winComponents)
    settings.put("drives", drives)
    settings.put("fullscreenStretched", fullscreenStretched)
    settings.put("useUnixLibs", useUnixLibs)
    settings.put("wow64Mode", wow64Mode)
    settings.put("startupSelection", startupSelection)
    settings.put("box64Version", box64Version)
    settings.put("box64Preset", box64Preset)
    settings.put("fexcoreVersion", fexcoreVersion)
    settings.put("fexcorePreset", fexcorePreset)
    settings.put("desktopTheme", desktopTheme)
    settings.put("rcfileId", rcfileId)
    settings.put("midiSoundFont", midiSoundFont)
    settings.put("lc_all", lcAll)
    settings.put("primaryController", primaryController)
    settings.put("controllerMapping", controllerMapping)

    var inputType = 0
    if (enableXInput) inputType = inputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
    if (enableDInput) inputType = inputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
    inputType = inputType or (if (dinputMapperType == 0) WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt() else WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt())
    settings.put("inputType", inputType)

    settings.put("csmtEnabled", csmt != 0)
    settings.put("offscreenRenderingMode", offscreenRenderingMode)
    settings.put("strictShaderMath", strictShaderMath)
    settings.put("videoMemorySize", videoMemorySize)
    settings.put("mouseWarpOverride", mouseWarpOverride)
    settings.put("logPixels", logPixels)

    profile.put("settings", settings)
    return profile.toString(2)
}
