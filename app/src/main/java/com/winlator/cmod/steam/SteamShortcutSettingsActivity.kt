package com.winlator.cmod.steam

import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassChevronColor

import com.winlator.cmod.ui.components.glassCardFill
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.winlator.cmod.R
import com.winlator.cmod.ContainerDetailFragment
import com.winlator.cmod.box86_64.Box86_64EditPresetDialog
import com.winlator.cmod.box86_64.Box86_64Preset
import com.winlator.cmod.box86_64.Box86_64PresetManager
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.contents.AdrenotoolsManager
import com.winlator.cmod.contentdialog.AudioDriverConfigDialog
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.contentdialog.ContentInfoDialog
import com.winlator.cmod.contentdialog.ContentUntrustedDialog
import com.winlator.cmod.contentdialog.DXVKConfigDialog
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog
import com.winlator.cmod.contentdialog.VKD3DConfigDialog
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.contents.Downloader
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.DriverResolver
import com.winlator.cmod.core.KeyValueSet
import com.winlator.cmod.core.PreloaderDialog
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.fexcore.FEXCoreEditPresetDialog
import com.winlator.cmod.fexcore.FEXCoreManager
import com.winlator.cmod.fexcore.FEXCorePreset
import com.winlator.cmod.fexcore.FEXCorePresetManager
import com.winlator.cmod.ui.theme.WinlatorTheme
import com.winlator.cmod.winhandler.WinHandler
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private data class SteamOption(
    val label: String,
    val value: String,
)

private data class WinComponentItem(
    val key: String,
    val label: String,
)

private data class SteamFieldAction(
    val label: String,
    val onClick: () -> Unit,
    @DrawableRes val iconRes: Int? = null,
)

private enum class SteamFieldActionsPlacement {
    BELOW,
    SIDE,
}

private sealed interface SteamOverlayState {
    data object GraphicsDriverConfig : SteamOverlayState
    data class DxWrapperConfig(val dxWrapper: String) : SteamOverlayState
    data object AudioDriverConfig : SteamOverlayState
}

private data class SteamComponentDownloadRequest(
    val type: ContentProfile.ContentType,
    val displayName: String,
    val onInstalled: (String) -> Unit,
)

private data class SteamRemoteContentOption(
    val title: String,
    val subtitle: String?,
    val profile: ContentProfile,
    val selectedValue: String,
)

private data class SteamDriverRemoteOption(
    val title: String,
    val subtitle: String?,
    val info: DriverResolver.DriverInfo,
)

private enum class SteamSettingsSection(@StringRes val titleRes: Int) {
    GENERAL(R.string.general),
    WIN_COMPONENTS(R.string.win_components),
    ENV_VARS(R.string.environment_variables),
    ADVANCED(R.string.advanced),
}

class SteamShortcutSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_SteamSettings)
        super.onCreate(savedInstanceState)

        val appId = intent.getIntExtra(EXTRA_APP_ID, -1)
        val containerId = intent.getIntExtra(EXTRA_CONTAINER_ID, -1).takeIf { it >= 0 }
        val shortcut = SteamGameActions.ensureSteamShortcut(this, appId, containerId)
        if (appId <= 0 || shortcut == null) {
            Toast.makeText(this, getString(R.string.steam_library_settings_unavailable), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            WinlatorTheme {
                SteamShortcutSettingsScreen(
                    shortcut = shortcut,
                    onClose = ::finish,
                    onSaved = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_APP_ID = "steam_app_id"
        const val EXTRA_CONTAINER_ID = "steam_container_id"
    }
}

@Composable
private fun SteamShortcutSettingsScreen(
    shortcut: Shortcut,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val container = shortcut.container
    val contentsManager = remember {
        ContentsManager(context).apply {
            syncContents()
        }
    }
    val shortcutWineIsArm64EC = remember(container.wineVersion, contentsManager) {
        runCatching {
            WineInfo.fromIdentifier(context, contentsManager, container.wineVersion).isArm64EC()
        }.getOrDefault(false)
    }

    val graphicsOptions = remember {
        ensureCurrentOption(
            buildIdentifierOptions(context, R.array.graphics_driver_entries),
            shortcut.getExtra("graphicsDriver", container.graphicsDriver),
        )
    }
    val dxWrapperOptions = remember {
        ensureCurrentOption(
            buildIdentifierOptions(context, R.array.dxwrapper_entries),
            shortcut.getExtra("dxwrapper", container.getDXWrapper()),
        )
    }
    val ddrawOptions = remember {
        ensureCurrentOption(
            buildIdentifierOptions(context, R.array.ddrawrapper_entries),
            shortcut.getExtra("ddrawrapper", container.getDDrawWrapper()),
        )
    }
    val audioOptions = remember {
        ensureCurrentOption(
            buildIdentifierOptions(context, R.array.audio_driver_entries),
            shortcut.getExtra("audioDriver", container.audioDriver),
        )
    }
    val containerEmulator = remember(container) {
        container.emulator?.takeIf { it.isNotBlank() } ?: Container.DEFAULT_EMULATOR
    }
    val normalizedContainerEmulator = remember(containerEmulator) {
        StringUtils.parseIdentifier(containerEmulator)
    }
    val rawEmulatorOptions = remember { buildIdentifierOptions(context, R.array.emulator_entries) }
    val emulatorOptions = remember(shortcutWineIsArm64EC, rawEmulatorOptions) {
        val filteredOptions = if (shortcutWineIsArm64EC) {
            rawEmulatorOptions
        } else {
            rawEmulatorOptions.filterNot { it.value.equals("fexcore", ignoreCase = true) }
        }
        ensureCurrentOption(filteredOptions, if (shortcutWineIsArm64EC) {
            StringUtils.parseIdentifier(shortcut.getExtra("emulator", containerEmulator))
        } else {
            "box64"
        })
    }
    val screenOptions = remember { buildIdentifierOptions(context, R.array.screen_size_entries) }
    val startupEntries = remember { context.resources.getStringArray(R.array.startup_selection_entries).toList() }
    val startupOptions = remember(startupEntries) {
        startupEntries.mapIndexed { index, label -> SteamOption(label = label, value = index.toString()) }
    }
    val dinputMapperOptions = remember {
        context.resources.getStringArray(R.array.dinput_mapper_type_entries).mapIndexed { index, label ->
            SteamOption(label = label, value = index.toString())
        }
    }
    val winComponentToggleOptions = remember {
        context.resources.getStringArray(R.array.wincomponent_entries).mapIndexed { index, label ->
            SteamOption(label = label, value = index.toString())
        }
    }

    val initialScreenSize = remember { shortcut.getExtra("screenSize", container.screenSize) }
    val initialCustomSize = remember(initialScreenSize, screenOptions) {
        initialScreenSize !in screenOptions.map { it.value }.toSet()
    }
    val (initialCustomWidth, initialCustomHeight) = remember(initialScreenSize, container.screenSize) {
        splitScreenSize(initialScreenSize, container.screenSize)
    }

    val initialWinComponents = remember {
        parseWinComponents(shortcut.getExtra("wincomponents", container.winComponents), context)
    }
    val winComponentItems = remember(initialWinComponents) { initialWinComponents.map { it.first } }
    val winComponentValues = remember {
        mutableStateMapOf<String, Int>().apply {
            initialWinComponents.forEach { (item, value) -> this[item.key] = value }
        }
    }
    var componentRefreshToken by remember { mutableIntStateOf(0) }
    var presetRefreshToken by remember { mutableIntStateOf(0) }

    var selectedSection by remember { mutableStateOf(SteamSettingsSection.GENERAL) }
    var execArgs by remember { mutableStateOf(shortcut.getExtra("execArgs")) }
    var screenSize by remember { mutableStateOf(if (initialCustomSize) "custom" else initialScreenSize) }
    var customScreenWidth by remember { mutableStateOf(initialCustomWidth) }
    var customScreenHeight by remember { mutableStateOf(initialCustomHeight) }
    var graphicsDriver by remember { mutableStateOf(shortcut.getExtra("graphicsDriver", container.graphicsDriver)) }
    var graphicsDriverConfig by remember {
        mutableStateOf(shortcut.getExtra("graphicsDriverConfig", container.graphicsDriverConfig))
    }
    var dxWrapper by remember { mutableStateOf(shortcut.getExtra("dxwrapper", container.getDXWrapper())) }
    var dxWrapperConfig by remember {
        mutableStateOf(shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig()))
    }
    var ddrawWrapper by remember { mutableStateOf(shortcut.getExtra("ddrawrapper", container.getDDrawWrapper())) }
    var audioDriver by remember { mutableStateOf(shortcut.getExtra("audioDriver", container.audioDriver)) }
    var audioDriverConfig by remember {
        mutableStateOf(shortcut.getExtra("audioDriverConfig", container.audioDriverConfig))
    }
    var emulator by remember {
        mutableStateOf(
            StringUtils.parseIdentifier(shortcut.getExtra("emulator", containerEmulator))
                .let { if (!shortcutWineIsArm64EC && it.equals("fexcore", ignoreCase = true)) "box64" else it },
        )
    }
    var forceFullscreen by remember { mutableStateOf(shortcut.getExtra("forceFullscreen", "0") == "1") }
    var fullscreenStretched by remember { mutableStateOf(shortcut.getExtra("fullscreenStretched", "0") == "1") }
    val initialSecondaryExec = remember { shortcut.getExtra("secondaryExec", "") }
    var useSecondaryExec by remember { mutableStateOf(initialSecondaryExec.isNotEmpty()) }
    var secondaryExec by remember { mutableStateOf(initialSecondaryExec) }
    var execDelay by remember { mutableStateOf(shortcut.getExtra("execDelay", "0")) }
    var startupSelection by remember {
        mutableIntStateOf(
            shortcut.getExtra("startupSelection", container.startupSelection.toString())
                .toIntOrNull()
                ?.coerceIn(0, startupEntries.lastIndex.coerceAtLeast(0))
                ?: container.startupSelection.toInt(),
        )
    }
    var envVars by remember { mutableStateOf(shortcut.getExtra("envVars")) }
    val initialInputType = remember {
        shortcut.getExtra("inputType", container.inputType.toString()).toIntOrNull() ?: container.inputType
    }
    var enableXInput by remember {
        val xinputFlag = WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
        mutableStateOf((initialInputType and xinputFlag) == xinputFlag)
    }
    var enableDInput by remember {
        val dinputFlag = WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
        mutableStateOf((initialInputType and dinputFlag) == dinputFlag)
    }
    var dinputMapperType by remember {
        val mapperStandardFlag = WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt()
        mutableIntStateOf(
            if ((initialInputType and mapperStandardFlag) == mapperStandardFlag) 0 else 1,
        )
    }
    var disableXInput by remember { mutableStateOf(shortcut.getExtra("disableXinput", "0") == "1") }
    var relativeMouseMovement by remember {
        mutableStateOf(
            shortcut.getExtra("relativeMouseMovement", if (container.isRelativeMouseMovement) "1" else "0") == "1",
        )
    }
    var simTouchScreen by remember { mutableStateOf(shortcut.getExtra("simTouchScreen", "0") == "1") }
    var box64Version by remember { mutableStateOf(shortcut.getExtra("box64Version", container.box64Version)) }
    var box64Preset by remember { mutableStateOf(shortcut.getExtra("box64Preset", container.box64Preset)) }
    var fexcoreVersion by remember { mutableStateOf(shortcut.getExtra("fexcoreVersion", container.getFEXCoreVersion())) }
    var fexcorePreset by remember { mutableStateOf(shortcut.getExtra("fexcorePreset", container.getFEXCorePreset())) }
    var useUnixLibs by remember { mutableStateOf(shortcut.getExtra("useUnixLibs", if (container.isUseUnixLibs) "1" else "0") == "1") }
    LaunchedEffect(shortcutWineIsArm64EC) {
        if (!shortcutWineIsArm64EC && emulator.equals("fexcore", ignoreCase = true)) {
            emulator = "box64"
        }
    }

    val usingFexcoreEmulator = remember(shortcutWineIsArm64EC, emulator) {
        shortcutWineIsArm64EC && emulator.equals("fexcore", ignoreCase = true)
    }

    val box64VersionOptions = remember(componentRefreshToken, box64Version, shortcutWineIsArm64EC) {
        ensureCurrentOption(
            loadBox64VersionOptions(context, contentsManager, shortcutWineIsArm64EC),
            box64Version,
        )
    }
    val fexcoreVersionOptions = remember(componentRefreshToken, fexcoreVersion) {
        ensureCurrentOption(
            loadFexcoreVersionOptions(context, contentsManager),
            fexcoreVersion,
        )
    }
    val box64PresetOptions = remember(presetRefreshToken, box64Preset) {
        ensureCurrentOption(
            Box86_64PresetManager.getPresets("box64", context).map { SteamOption(it.name, it.id) },
            box64Preset,
        )
    }
    val fexcorePresetOptions = remember(presetRefreshToken, fexcorePreset) {
        ensureCurrentOption(
            FEXCorePresetManager.getPresets(context).map { SteamOption(it.name, it.id) },
            fexcorePreset,
        )
    }

    fun refreshComponentVersions() {
        contentsManager.syncContents()
        componentRefreshToken++
    }

    fun refreshBox64Presets(selectLast: Boolean = false) {
        presetRefreshToken++
        val presets = Box86_64PresetManager.getPresets("box64", context)
        when {
            presets.isEmpty() -> Unit
            selectLast -> box64Preset = presets.last().id
            presets.none { it.id == box64Preset } -> box64Preset = presets.first().id
        }
    }

    fun refreshFexcorePresets(selectLast: Boolean = false) {
        presetRefreshToken++
        val presets = FEXCorePresetManager.getPresets(context)
        when {
            presets.isEmpty() -> Unit
            selectLast -> fexcorePreset = presets.last().id
            presets.none { it.id == fexcorePreset } -> fexcorePreset = presets.first().id
        }
    }

    var activeOverlay by remember { mutableStateOf<SteamOverlayState?>(null) }
    var standaloneDownloadRequest by remember { mutableStateOf<SteamComponentDownloadRequest?>(null) }

    val openGraphicsDriverConfig = {
        activeOverlay = SteamOverlayState.GraphicsDriverConfig
    }

    val openDxWrapperConfig = {
        activeOverlay = SteamOverlayState.DxWrapperConfig(dxWrapper)
    }

    val openAudioDriverConfig = {
        activeOverlay = SteamOverlayState.AudioDriverConfig
    }

    val downloadBox64Version = {
        val contentType = if (shortcutWineIsArm64EC) {
            ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
        } else {
            ContentProfile.ContentType.CONTENT_TYPE_BOX64
        }
        val displayName = if (shortcutWineIsArm64EC) "WoWBox64" else "Box64"
        standaloneDownloadRequest = SteamComponentDownloadRequest(contentType, displayName) { selectedValue ->
            refreshComponentVersions()
            box64Version = selectedValue
        }
    }

    val downloadFexcoreVersion = {
        standaloneDownloadRequest = SteamComponentDownloadRequest(
            ContentProfile.ContentType.CONTENT_TYPE_FEXCORE,
            "FEXCore",
        ) { selectedValue ->
            refreshComponentVersions()
            fexcoreVersion = selectedValue
        }
    }

    val addBox64Preset: () -> Unit = {
        val previousCount = Box86_64PresetManager.getPresets("box64", context).size
        Box86_64EditPresetDialog(context, "box64", null).apply {
            setOnDismissListener {
                refreshBox64Presets(
                    selectLast = Box86_64PresetManager.getPresets("box64", context).size > previousCount,
                )
            }
        }.show()
        Unit
    }

    val editBox64Preset: () -> Unit = {
        Box86_64EditPresetDialog(context, "box64", box64Preset).apply {
            setOnDismissListener { refreshBox64Presets() }
        }.show()
        Unit
    }

    val duplicateBox64Preset: () -> Unit = {
        ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset) {
            Box86_64PresetManager.duplicatePreset("box64", context, box64Preset)
            refreshBox64Presets(selectLast = true)
        }
        Unit
    }

    val removeBox64Preset: () -> Unit = {
        if (!box64Preset.startsWith(Box86_64Preset.CUSTOM)) {
            AppUtils.showToast(context, R.string.you_cannot_remove_this_preset)
        } else {
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset) {
                Box86_64PresetManager.removePreset("box64", context, box64Preset)
                refreshBox64Presets()
            }
        }
        Unit
    }

    val exportBox64Preset: () -> Unit = {
        Box86_64PresetManager.exportPreset("box64", context, box64Preset)
        Unit
    }

    val importBox64Preset: () -> Unit = {
        val files = Box86_64PresetManager.listExportedPresets(context)
        if (files.isEmpty()) {
            AppUtils.showToast(context, "No presets found")
        } else {
            ContentDialog.showSingleChoiceList(
                context,
                R.string.import_profile,
                files.map(File::getName).toTypedArray(),
            ) { index ->
                if (index in files.indices) {
                    Box86_64PresetManager.importPreset("box64", context, files[index])
                    refreshBox64Presets(selectLast = true)
                }
            }
        }
        Unit
    }

    val addFexcorePreset: () -> Unit = {
        val previousCount = FEXCorePresetManager.getPresets(context).size
        FEXCoreEditPresetDialog(context, null).apply {
            setOnDismissListener {
                refreshFexcorePresets(
                    selectLast = FEXCorePresetManager.getPresets(context).size > previousCount,
                )
            }
        }.show()
        Unit
    }

    val editFexcorePreset: () -> Unit = {
        FEXCoreEditPresetDialog(context, fexcorePreset).apply {
            setOnDismissListener { refreshFexcorePresets() }
        }.show()
        Unit
    }

    val duplicateFexcorePreset: () -> Unit = {
        ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset) {
            FEXCorePresetManager.duplicatePreset(context, fexcorePreset)
            refreshFexcorePresets(selectLast = true)
        }
        Unit
    }

    val removeFexcorePreset: () -> Unit = {
        if (!fexcorePreset.startsWith(FEXCorePreset.CUSTOM)) {
            AppUtils.showToast(context, R.string.you_cannot_remove_this_preset)
        } else {
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset) {
                FEXCorePresetManager.removePreset(context, fexcorePreset)
                refreshFexcorePresets()
            }
        }
        Unit
    }

    val exportFexcorePreset: () -> Unit = {
        FEXCorePresetManager.exportPreset(context, fexcorePreset)
        Unit
    }

    val importFexcorePreset: () -> Unit = {
        val files = FEXCorePresetManager.listExportedPresets(context)
        if (files.isEmpty()) {
            AppUtils.showToast(context, "No presets found")
        } else {
            ContentDialog.showSingleChoiceList(
                context,
                R.string.import_profile,
                files.map(File::getName).toTypedArray(),
            ) { index ->
                if (index in files.indices) {
                    FEXCorePresetManager.importPreset(context, files[index])
                    refreshFexcorePresets(selectLast = true)
                }
            }
        }
        Unit
    }

    val saveAndClose: () -> Unit = {
        runCatching {
            saveShortcutSettings(
                shortcut = shortcut,
                container = container,
                containerEmulator = normalizedContainerEmulator,
                execArgs = execArgs,
                screenSize = screenSize,
                customScreenWidth = customScreenWidth,
                customScreenHeight = customScreenHeight,
                graphicsDriver = graphicsDriver,
                graphicsDriverConfig = graphicsDriverConfig,
                dxWrapper = dxWrapper,
                dxWrapperConfig = dxWrapperConfig,
                ddrawWrapper = ddrawWrapper,
                audioDriver = audioDriver,
                audioDriverConfig = audioDriverConfig,
                emulator = emulator,
                forceFullscreen = forceFullscreen,
                fullscreenStretched = fullscreenStretched,
                useSecondaryExec = useSecondaryExec,
                secondaryExec = secondaryExec,
                execDelay = execDelay,
                startupSelection = startupSelection,
                envVars = envVars,
                winComponentItems = winComponentItems,
                winComponentValues = winComponentValues,
                enableXInput = enableXInput,
                enableDInput = enableDInput,
                dinputMapperType = dinputMapperType,
                disableXInput = disableXInput,
                relativeMouseMovement = relativeMouseMovement,
                simTouchScreen = simTouchScreen,
                box64Version = box64Version,
                box64Preset = box64Preset,
                fexcoreVersion = fexcoreVersion,
                fexcorePreset = fexcorePreset,
                useUnixLibs = useUnixLibs,
            )
        }.onSuccess {
            onSaved()
        }.onFailure {
            Toast.makeText(
                context,
                context.getString(R.string.steam_library_settings_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
        }
        Unit
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF111116), Color(0xFF09090D), Color(0xFF050507)),
                ),
            )
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = shortcut.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "STEAM",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = saveAndClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SteamSettingsSidebar(
                    selectedSection = selectedSection,
                    onSelectSection = { selectedSection = it },
                    modifier = Modifier
                        .width(182.dp)
                        .fillMaxHeight(),
                )
                SteamSettingsContentPane(
                    selectedSection = selectedSection,
                    execArgs = execArgs,
                    onExecArgsChange = { execArgs = it },
                    screenSize = screenSize,
                    onScreenSizeChange = { screenSize = it },
                    customScreenWidth = customScreenWidth,
                    onCustomScreenWidthChange = { customScreenWidth = it },
                    customScreenHeight = customScreenHeight,
                    onCustomScreenHeightChange = { customScreenHeight = it },
                    graphicsDriver = graphicsDriver,
                    onGraphicsDriverChange = { graphicsDriver = it },
                    graphicsDriverConfig = graphicsDriverConfig,
                    onOpenGraphicsDriverConfig = openGraphicsDriverConfig,
                    dxWrapper = dxWrapper,
                    onDxWrapperChange = { dxWrapper = it },
                    dxWrapperConfig = dxWrapperConfig,
                    onOpenDxWrapperConfig = openDxWrapperConfig,
                    ddrawWrapper = ddrawWrapper,
                    onDdrawWrapperChange = { ddrawWrapper = it },
                    audioDriver = audioDriver,
                    onAudioDriverChange = { audioDriver = it },
                    audioDriverConfig = audioDriverConfig,
                    onOpenAudioDriverConfig = openAudioDriverConfig,
                    emulator = emulator,
                    onEmulatorChange = { emulator = it },
                    emulatorEnabled = shortcutWineIsArm64EC,
                    forceFullscreen = forceFullscreen,
                    onForceFullscreenChange = { forceFullscreen = it },
                    fullscreenStretched = fullscreenStretched,
                    onFullscreenStretchedChange = { fullscreenStretched = it },
                    useSecondaryExec = useSecondaryExec,
                    onUseSecondaryExecChange = { useSecondaryExec = it },
                    secondaryExec = secondaryExec,
                    onSecondaryExecChange = { secondaryExec = it },
                    execDelay = execDelay,
                    onExecDelayChange = { execDelay = it },
                    startupSelection = startupSelection,
                    onStartupSelectionChange = { startupSelection = it },
                    envVars = envVars,
                    onEnvVarsChange = { envVars = it },
                    winComponentItems = winComponentItems,
                    winComponentValues = winComponentValues,
                    onWinComponentChange = { key, value -> winComponentValues[key] = value },
                    box64Version = box64Version,
                    onBox64VersionChange = { box64Version = it },
                    enableXInput = enableXInput,
                    onEnableXInputChange = { enableXInput = it },
                    enableDInput = enableDInput,
                    onEnableDInputChange = { enableDInput = it },
                    dinputMapperType = dinputMapperType,
                    onDinputMapperTypeChange = { dinputMapperType = it },
                    disableXInput = disableXInput,
                    onDisableXInputChange = { disableXInput = it },
                    relativeMouseMovement = relativeMouseMovement,
                    onRelativeMouseMovementChange = { relativeMouseMovement = it },
                    simTouchScreen = simTouchScreen,
                    onSimTouchScreenChange = { simTouchScreen = it },
                    screenOptions = screenOptions,
                    graphicsOptions = graphicsOptions,
                    dxWrapperOptions = dxWrapperOptions,
                    ddrawOptions = ddrawOptions,
                    audioOptions = audioOptions,
                    emulatorOptions = emulatorOptions,
                    startupOptions = startupOptions,
                    usingFexcoreEmulator = usingFexcoreEmulator,
                    box64VersionLabel = if (shortcutWineIsArm64EC) "WoWBox64 Version" else context.getString(R.string.box64_version),
                    box64VersionOptions = box64VersionOptions,
                    onDownloadBox64Version = downloadBox64Version,
                    box64Preset = box64Preset,
                    onBox64PresetChange = { box64Preset = it },
                    box64PresetOptions = box64PresetOptions,
                    onAddBox64Preset = addBox64Preset,
                    onEditBox64Preset = editBox64Preset,
                    onDuplicateBox64Preset = duplicateBox64Preset,
                    onRemoveBox64Preset = removeBox64Preset,
                    onExportBox64Preset = exportBox64Preset,
                    onImportBox64Preset = importBox64Preset,
                    fexcoreVersion = fexcoreVersion,
                    onFexcoreVersionChange = { fexcoreVersion = it },
                    fexcoreVersionOptions = fexcoreVersionOptions,
                    onDownloadFexcoreVersion = downloadFexcoreVersion,
                    fexcorePreset = fexcorePreset,
                    onFexcorePresetChange = { fexcorePreset = it },
                    fexcorePresetOptions = fexcorePresetOptions,
                    useUnixLibs = useUnixLibs,
                    onUseUnixLibsChange = { useUnixLibs = it },
                    onAddFexcorePreset = addFexcorePreset,
                    onEditFexcorePreset = editFexcorePreset,
                    onDuplicateFexcorePreset = duplicateFexcorePreset,
                    onRemoveFexcorePreset = removeFexcorePreset,
                    onExportFexcorePreset = exportFexcorePreset,
                    onImportFexcorePreset = importFexcorePreset,
                    dinputMapperOptions = dinputMapperOptions,
                    winComponentToggleOptions = winComponentToggleOptions,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (val overlay = activeOverlay) {
            SteamOverlayState.GraphicsDriverConfig -> {
                SteamGraphicsDriverConfigOverlay(
                    graphicsDriver = graphicsDriver,
                    currentConfig = graphicsDriverConfig,
                    onDismiss = { activeOverlay = null },
                    onSave = {
                        graphicsDriverConfig = it
                        activeOverlay = null
                    },
                )
            }

            is SteamOverlayState.DxWrapperConfig -> {
                SteamDxWrapperConfigOverlay(
                    dxWrapper = overlay.dxWrapper,
                    currentConfig = dxWrapperConfig,
                    contentsManager = contentsManager,
                    onDismiss = { activeOverlay = null },
                    onSave = {
                        dxWrapperConfig = it
                        activeOverlay = null
                    },
                )
            }

            SteamOverlayState.AudioDriverConfig -> {
                SteamAudioDriverConfigOverlay(
                    currentConfig = audioDriverConfig,
                    onDismiss = { activeOverlay = null },
                    onSave = {
                        audioDriverConfig = it
                        activeOverlay = null
                    },
                )
            }

            null -> Unit
        }

        standaloneDownloadRequest?.let { request ->
            SteamComponentDownloadOverlay(
                displayName = request.displayName,
                type = request.type,
                contentsManager = contentsManager,
                onDismiss = { standaloneDownloadRequest = null },
                onInstalled = { selectedValue ->
                    standaloneDownloadRequest = null
                    request.onInstalled(selectedValue)
                },
            )
        }
    }
}

@Composable
private fun SteamSettingsSidebar(
    selectedSection: SteamSettingsSection,
    onSelectSection: (SteamSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(top = 40.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.settings),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Box(
            modifier = Modifier
                .width(74.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(modifier = Modifier.height(2.dp))
        SteamSettingsSection.entries.forEach { section ->
            val isSelected = section == selectedSection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable { onSelectSection(section) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(section.titleRes),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SteamSettingsContentPane(
    selectedSection: SteamSettingsSection,
    execArgs: String,
    onExecArgsChange: (String) -> Unit,
    screenSize: String,
    onScreenSizeChange: (String) -> Unit,
    customScreenWidth: String,
    onCustomScreenWidthChange: (String) -> Unit,
    customScreenHeight: String,
    onCustomScreenHeightChange: (String) -> Unit,
    graphicsDriver: String,
    onGraphicsDriverChange: (String) -> Unit,
    graphicsDriverConfig: String,
    onOpenGraphicsDriverConfig: () -> Unit,
    dxWrapper: String,
    onDxWrapperChange: (String) -> Unit,
    dxWrapperConfig: String,
    onOpenDxWrapperConfig: () -> Unit,
    ddrawWrapper: String,
    onDdrawWrapperChange: (String) -> Unit,
    audioDriver: String,
    onAudioDriverChange: (String) -> Unit,
    audioDriverConfig: String,
    onOpenAudioDriverConfig: () -> Unit,
    emulator: String,
    onEmulatorChange: (String) -> Unit,
    emulatorEnabled: Boolean,
    forceFullscreen: Boolean,
    onForceFullscreenChange: (Boolean) -> Unit,
    fullscreenStretched: Boolean,
    onFullscreenStretchedChange: (Boolean) -> Unit,
    useSecondaryExec: Boolean,
    onUseSecondaryExecChange: (Boolean) -> Unit,
    secondaryExec: String,
    onSecondaryExecChange: (String) -> Unit,
    execDelay: String,
    onExecDelayChange: (String) -> Unit,
    startupSelection: Int,
    onStartupSelectionChange: (Int) -> Unit,
    envVars: String,
    onEnvVarsChange: (String) -> Unit,
    winComponentItems: List<WinComponentItem>,
    winComponentValues: Map<String, Int>,
    onWinComponentChange: (String, Int) -> Unit,
    box64Version: String,
    onBox64VersionChange: (String) -> Unit,
    usingFexcoreEmulator: Boolean,
    box64VersionLabel: String,
    box64VersionOptions: List<SteamOption>,
    onDownloadBox64Version: () -> Unit,
    box64Preset: String,
    onBox64PresetChange: (String) -> Unit,
    box64PresetOptions: List<SteamOption>,
    onAddBox64Preset: () -> Unit,
    onEditBox64Preset: () -> Unit,
    onDuplicateBox64Preset: () -> Unit,
    onRemoveBox64Preset: () -> Unit,
    onExportBox64Preset: () -> Unit,
    onImportBox64Preset: () -> Unit,
    fexcoreVersion: String,
    onFexcoreVersionChange: (String) -> Unit,
    fexcoreVersionOptions: List<SteamOption>,
    onDownloadFexcoreVersion: () -> Unit,
    fexcorePreset: String,
    onFexcorePresetChange: (String) -> Unit,
    fexcorePresetOptions: List<SteamOption>,
    useUnixLibs: Boolean,
    onUseUnixLibsChange: (Boolean) -> Unit,
    onAddFexcorePreset: () -> Unit,
    onEditFexcorePreset: () -> Unit,
    onDuplicateFexcorePreset: () -> Unit,
    onRemoveFexcorePreset: () -> Unit,
    onExportFexcorePreset: () -> Unit,
    onImportFexcorePreset: () -> Unit,
    enableXInput: Boolean,
    onEnableXInputChange: (Boolean) -> Unit,
    enableDInput: Boolean,
    onEnableDInputChange: (Boolean) -> Unit,
    dinputMapperType: Int,
    onDinputMapperTypeChange: (Int) -> Unit,
    disableXInput: Boolean,
    onDisableXInputChange: (Boolean) -> Unit,
    relativeMouseMovement: Boolean,
    onRelativeMouseMovementChange: (Boolean) -> Unit,
    simTouchScreen: Boolean,
    onSimTouchScreenChange: (Boolean) -> Unit,
    screenOptions: List<SteamOption>,
    graphicsOptions: List<SteamOption>,
    dxWrapperOptions: List<SteamOption>,
    ddrawOptions: List<SteamOption>,
    audioOptions: List<SteamOption>,
    emulatorOptions: List<SteamOption>,
    startupOptions: List<SteamOption>,
    dinputMapperOptions: List<SteamOption>,
    winComponentToggleOptions: List<SteamOption>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(top = 0.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(selectedSection.titleRes),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF263A54)),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(end = 4.dp, top = 0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (selectedSection) {
                SteamSettingsSection.GENERAL -> SteamGeneralSection(
                    execArgs = execArgs,
                    onExecArgsChange = onExecArgsChange,
                    screenSize = screenSize,
                    onScreenSizeChange = onScreenSizeChange,
                    customScreenWidth = customScreenWidth,
                    onCustomScreenWidthChange = onCustomScreenWidthChange,
                    customScreenHeight = customScreenHeight,
                    onCustomScreenHeightChange = onCustomScreenHeightChange,
                    graphicsDriver = graphicsDriver,
                    onGraphicsDriverChange = onGraphicsDriverChange,
                    graphicsDriverConfig = graphicsDriverConfig,
                    onOpenGraphicsDriverConfig = onOpenGraphicsDriverConfig,
                    dxWrapper = dxWrapper,
                    onDxWrapperChange = onDxWrapperChange,
                    dxWrapperConfig = dxWrapperConfig,
                    onOpenDxWrapperConfig = onOpenDxWrapperConfig,
                    ddrawWrapper = ddrawWrapper,
                    onDdrawWrapperChange = onDdrawWrapperChange,
                    audioDriver = audioDriver,
                    onAudioDriverChange = onAudioDriverChange,
                    audioDriverConfig = audioDriverConfig,
                    onOpenAudioDriverConfig = onOpenAudioDriverConfig,
                    emulator = emulator,
                    onEmulatorChange = onEmulatorChange,
                    emulatorEnabled = emulatorEnabled,
                    forceFullscreen = forceFullscreen,
                    onForceFullscreenChange = onForceFullscreenChange,
                    fullscreenStretched = fullscreenStretched,
                    onFullscreenStretchedChange = onFullscreenStretchedChange,
                    useSecondaryExec = useSecondaryExec,
                    onUseSecondaryExecChange = onUseSecondaryExecChange,
                    secondaryExec = secondaryExec,
                    onSecondaryExecChange = onSecondaryExecChange,
                    execDelay = execDelay,
                    onExecDelayChange = onExecDelayChange,
                    startupSelection = startupSelection,
                    onStartupSelectionChange = onStartupSelectionChange,
                    screenOptions = screenOptions,
                    graphicsOptions = graphicsOptions,
                    dxWrapperOptions = dxWrapperOptions,
                    ddrawOptions = ddrawOptions,
                    audioOptions = audioOptions,
                    emulatorOptions = emulatorOptions,
                    startupOptions = startupOptions,
                )

                SteamSettingsSection.WIN_COMPONENTS -> SteamWinComponentsSection(
                    winComponentItems = winComponentItems,
                    winComponentValues = winComponentValues,
                    onWinComponentChange = onWinComponentChange,
                    winComponentToggleOptions = winComponentToggleOptions,
                )

                SteamSettingsSection.ENV_VARS -> SteamTextField(
                    label = stringResource(R.string.environment_variables),
                    value = envVars,
                    onValueChange = onEnvVarsChange,
                    singleLine = false,
                    minLines = 12,
                )

                SteamSettingsSection.ADVANCED -> SteamAdvancedSection(
                    usingFexcoreEmulator = usingFexcoreEmulator,
                    box64VersionLabel = box64VersionLabel,
                    box64Version = box64Version,
                    onBox64VersionChange = onBox64VersionChange,
                    onDownloadBox64Version = onDownloadBox64Version,
                    box64Preset = box64Preset,
                    onBox64PresetChange = onBox64PresetChange,
                    box64PresetOptions = box64PresetOptions,
                    onAddBox64Preset = onAddBox64Preset,
                    onEditBox64Preset = onEditBox64Preset,
                    onDuplicateBox64Preset = onDuplicateBox64Preset,
                    onRemoveBox64Preset = onRemoveBox64Preset,
                    onExportBox64Preset = onExportBox64Preset,
                    onImportBox64Preset = onImportBox64Preset,
                    fexcoreVersion = fexcoreVersion,
                    onFexcoreVersionChange = onFexcoreVersionChange,
                    fexcoreVersionOptions = fexcoreVersionOptions,
                    onDownloadFexcoreVersion = onDownloadFexcoreVersion,
                    fexcorePreset = fexcorePreset,
                    onFexcorePresetChange = onFexcorePresetChange,
                    fexcorePresetOptions = fexcorePresetOptions,
                    useUnixLibs = useUnixLibs,
                    onUseUnixLibsChange = onUseUnixLibsChange,
                    onAddFexcorePreset = onAddFexcorePreset,
                    onEditFexcorePreset = onEditFexcorePreset,
                    onDuplicateFexcorePreset = onDuplicateFexcorePreset,
                    onRemoveFexcorePreset = onRemoveFexcorePreset,
                    onExportFexcorePreset = onExportFexcorePreset,
                    onImportFexcorePreset = onImportFexcorePreset,
                    enableXInput = enableXInput,
                    onEnableXInputChange = onEnableXInputChange,
                    enableDInput = enableDInput,
                    onEnableDInputChange = onEnableDInputChange,
                    dinputMapperType = dinputMapperType,
                    onDinputMapperTypeChange = onDinputMapperTypeChange,
                    disableXInput = disableXInput,
                    onDisableXInputChange = onDisableXInputChange,
                    relativeMouseMovement = relativeMouseMovement,
                    onRelativeMouseMovementChange = onRelativeMouseMovementChange,
                    simTouchScreen = simTouchScreen,
                    onSimTouchScreenChange = onSimTouchScreenChange,
                    box64VersionOptions = box64VersionOptions,
                    dinputMapperOptions = dinputMapperOptions,
                )
            }
        }
    }
}

@Composable
private fun SteamGeneralSection(
    execArgs: String,
    onExecArgsChange: (String) -> Unit,
    screenSize: String,
    onScreenSizeChange: (String) -> Unit,
    customScreenWidth: String,
    onCustomScreenWidthChange: (String) -> Unit,
    customScreenHeight: String,
    onCustomScreenHeightChange: (String) -> Unit,
    graphicsDriver: String,
    onGraphicsDriverChange: (String) -> Unit,
    graphicsDriverConfig: String,
    onOpenGraphicsDriverConfig: () -> Unit,
    dxWrapper: String,
    onDxWrapperChange: (String) -> Unit,
    dxWrapperConfig: String,
    onOpenDxWrapperConfig: () -> Unit,
    ddrawWrapper: String,
    onDdrawWrapperChange: (String) -> Unit,
    audioDriver: String,
    onAudioDriverChange: (String) -> Unit,
    audioDriverConfig: String,
    onOpenAudioDriverConfig: () -> Unit,
    emulator: String,
    onEmulatorChange: (String) -> Unit,
    emulatorEnabled: Boolean,
    forceFullscreen: Boolean,
    onForceFullscreenChange: (Boolean) -> Unit,
    fullscreenStretched: Boolean,
    onFullscreenStretchedChange: (Boolean) -> Unit,
    useSecondaryExec: Boolean,
    onUseSecondaryExecChange: (Boolean) -> Unit,
    secondaryExec: String,
    onSecondaryExecChange: (String) -> Unit,
    execDelay: String,
    onExecDelayChange: (String) -> Unit,
    startupSelection: Int,
    onStartupSelectionChange: (Int) -> Unit,
    screenOptions: List<SteamOption>,
    graphicsOptions: List<SteamOption>,
    dxWrapperOptions: List<SteamOption>,
    ddrawOptions: List<SteamOption>,
    audioOptions: List<SteamOption>,
    emulatorOptions: List<SteamOption>,
    startupOptions: List<SteamOption>,
) {
    SteamSelectField(
        label = stringResource(R.string.screen_size),
        value = screenSize,
        options = screenOptions,
        onSelect = onScreenSizeChange,
    )
    if (screenSize == "custom") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SteamTextField(
                label = "Width",
                value = customScreenWidth,
                onValueChange = { onCustomScreenWidthChange(it.filter(Char::isDigit)) },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            SteamTextField(
                label = "Height",
                value = customScreenHeight,
                onValueChange = { onCustomScreenHeightChange(it.filter(Char::isDigit)) },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
        }
    }
    SteamSelectField(
        label = stringResource(R.string.graphics_driver),
        value = graphicsDriver,
        options = graphicsOptions,
        onSelect = onGraphicsDriverChange,
        supportingText = graphicsDriverConfig
            .takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.graphics_driver_version) + ": " + GraphicsDriverConfigDialog.getVersion(it) },
        actionsPlacement = SteamFieldActionsPlacement.SIDE,
        actions = listOf(
            SteamFieldAction(
                label = stringResource(R.string.action_configure),
                onClick = onOpenGraphicsDriverConfig,
                iconRes = R.drawable.icon_settings_shortcut,
            ),
        ),
    )
    SteamSelectField(
        label = stringResource(R.string.dxwrapper),
        value = dxWrapper,
        options = dxWrapperOptions,
        onSelect = onDxWrapperChange,
        supportingText = describeDxWrapperConfig(dxWrapper, dxWrapperConfig),
        actionsPlacement = SteamFieldActionsPlacement.SIDE,
        actions = buildList {
            if (dxWrapper.equals("dxvk", ignoreCase = true) || dxWrapper.equals("vkd3d", ignoreCase = true)) {
                add(
                    SteamFieldAction(
                        label = stringResource(R.string.action_configure),
                        onClick = onOpenDxWrapperConfig,
                        iconRes = R.drawable.icon_settings_shortcut,
                    ),
                )
            }
        },
    )
    SteamSelectField(
        label = stringResource(R.string.ddraw_wrapper),
        value = ddrawWrapper,
        options = ddrawOptions,
        onSelect = onDdrawWrapperChange,
    )
    SteamSelectField(
        label = stringResource(R.string.audio_driver),
        value = audioDriver,
        options = audioOptions,
        onSelect = onAudioDriverChange,
        actionsPlacement = SteamFieldActionsPlacement.SIDE,
        actions = listOf(
            SteamFieldAction(
                label = stringResource(R.string.action_configure),
                onClick = onOpenAudioDriverConfig,
                iconRes = R.drawable.icon_settings_shortcut,
            ),
        ),
    )
    SteamSelectField(
        label = "Emulator",
        value = emulator,
        options = emulatorOptions,
        onSelect = onEmulatorChange,
        enabled = emulatorEnabled,
    )
    SteamTextField(
        label = stringResource(R.string.exec_arguments),
        value = execArgs,
        onValueChange = onExecArgsChange,
        singleLine = false,
    )
    SteamToggleField(
        label = stringResource(R.string.force_fullscreen),
        checked = forceFullscreen,
        onCheckedChange = onForceFullscreenChange,
    )
    SteamToggleField(
        label = stringResource(R.string.fullscreen_stretched),
        checked = fullscreenStretched,
        onCheckedChange = onFullscreenStretchedChange,
    )
    SteamToggleField(
        label = stringResource(R.string.use_secondary_executable),
        checked = useSecondaryExec,
        onCheckedChange = onUseSecondaryExecChange,
    )
    if (useSecondaryExec) {
        SteamTextField(
            label = stringResource(R.string.secondary_executable),
            value = secondaryExec,
            onValueChange = onSecondaryExecChange,
            singleLine = false,
        )
        SteamTextField(
            label = stringResource(R.string.exec_delay),
            value = execDelay,
            onValueChange = { onExecDelayChange(it.filter(Char::isDigit)) },
            keyboardType = KeyboardType.Number,
        )
    }
    SteamSelectField(
        label = stringResource(R.string.startup_selection),
        value = startupSelection.toString(),
        options = startupOptions,
        onSelect = { selected -> onStartupSelectionChange(selected.toIntOrNull() ?: 0) },
    )
}

@Composable
private fun SteamWinComponentsSection(
    winComponentItems: List<WinComponentItem>,
    winComponentValues: Map<String, Int>,
    onWinComponentChange: (String, Int) -> Unit,
    winComponentToggleOptions: List<SteamOption>,
) {
    val directComponents = winComponentItems.filter { it.key.startsWith("direct") }
    val commonComponents = winComponentItems.filterNot { it.key.startsWith("direct") }

    if (directComponents.isNotEmpty()) {
        Text(
            text = "DirectX",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        directComponents.forEach { item ->
            SteamSelectField(
                label = item.label,
                value = (winComponentValues[item.key] ?: 0).toString(),
                options = winComponentToggleOptions,
                onSelect = { selected -> onWinComponentChange(item.key, selected.toIntOrNull()?.coerceIn(0, 1) ?: 0) },
            )
        }
    }

    if (commonComponents.isNotEmpty()) {
        Text(
            text = "General",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        commonComponents.forEach { item ->
            SteamSelectField(
                label = item.label,
                value = (winComponentValues[item.key] ?: 0).toString(),
                options = winComponentToggleOptions,
                onSelect = { selected -> onWinComponentChange(item.key, selected.toIntOrNull()?.coerceIn(0, 1) ?: 0) },
            )
        }
    }
}

@Composable
private fun SteamAdvancedSection(
    usingFexcoreEmulator: Boolean,
    box64VersionLabel: String,
    box64Version: String,
    onBox64VersionChange: (String) -> Unit,
    onDownloadBox64Version: () -> Unit,
    box64Preset: String,
    onBox64PresetChange: (String) -> Unit,
    box64PresetOptions: List<SteamOption>,
    onAddBox64Preset: () -> Unit,
    onEditBox64Preset: () -> Unit,
    onDuplicateBox64Preset: () -> Unit,
    onRemoveBox64Preset: () -> Unit,
    onExportBox64Preset: () -> Unit,
    onImportBox64Preset: () -> Unit,
    fexcoreVersion: String,
    onFexcoreVersionChange: (String) -> Unit,
    fexcoreVersionOptions: List<SteamOption>,
    onDownloadFexcoreVersion: () -> Unit,
    fexcorePreset: String,
    onFexcorePresetChange: (String) -> Unit,
    fexcorePresetOptions: List<SteamOption>,
    useUnixLibs: Boolean,
    onUseUnixLibsChange: (Boolean) -> Unit,
    onAddFexcorePreset: () -> Unit,
    onEditFexcorePreset: () -> Unit,
    onDuplicateFexcorePreset: () -> Unit,
    onRemoveFexcorePreset: () -> Unit,
    onExportFexcorePreset: () -> Unit,
    onImportFexcorePreset: () -> Unit,
    enableXInput: Boolean,
    onEnableXInputChange: (Boolean) -> Unit,
    enableDInput: Boolean,
    onEnableDInputChange: (Boolean) -> Unit,
    dinputMapperType: Int,
    onDinputMapperTypeChange: (Int) -> Unit,
    disableXInput: Boolean,
    onDisableXInputChange: (Boolean) -> Unit,
    relativeMouseMovement: Boolean,
    onRelativeMouseMovementChange: (Boolean) -> Unit,
    simTouchScreen: Boolean,
    onSimTouchScreenChange: (Boolean) -> Unit,
    box64VersionOptions: List<SteamOption>,
    dinputMapperOptions: List<SteamOption>,
) {
    if (usingFexcoreEmulator) {
        SteamSelectField(
            label = stringResource(R.string.fexcore_version),
            value = fexcoreVersion,
            options = fexcoreVersionOptions,
            onSelect = onFexcoreVersionChange,
            actionsPlacement = SteamFieldActionsPlacement.SIDE,
            actions = listOf(
                SteamFieldAction(
                    label = stringResource(R.string.action_download),
                    onClick = onDownloadFexcoreVersion,
                    iconRes = R.drawable.icon_popup_menu_download,
                ),
            ),
        )
        SteamSelectField(
            label = stringResource(R.string.fexcore_preset),
            value = fexcorePreset,
            options = fexcorePresetOptions,
            onSelect = onFexcorePresetChange,
            actions = listOf(
                SteamFieldAction(stringResource(R.string.action_add), onAddFexcorePreset),
                SteamFieldAction(stringResource(R.string.action_edit), onEditFexcorePreset),
                SteamFieldAction(stringResource(R.string.action_duplicate), onDuplicateFexcorePreset),
                SteamFieldAction(stringResource(R.string.action_remove), onRemoveFexcorePreset),
                SteamFieldAction(stringResource(R.string.action_export), onExportFexcorePreset),
                SteamFieldAction(stringResource(R.string.action_import), onImportFexcorePreset),
            ),
        )
        SteamToggleField(
            label = stringResource(R.string.use_unix_libs),
            checked = useUnixLibs,
            onCheckedChange = onUseUnixLibsChange,
        )
    } else {
        SteamSelectField(
            label = box64VersionLabel,
            value = box64Version,
            options = box64VersionOptions,
            onSelect = onBox64VersionChange,
            actionsPlacement = SteamFieldActionsPlacement.SIDE,
            actions = listOf(
                SteamFieldAction(
                    label = stringResource(R.string.action_download),
                    onClick = onDownloadBox64Version,
                    iconRes = R.drawable.icon_popup_menu_download,
                ),
            ),
        )
        SteamSelectField(
            label = stringResource(R.string.box64_preset),
            value = box64Preset,
            options = box64PresetOptions,
            onSelect = onBox64PresetChange,
            actions = listOf(
                SteamFieldAction(stringResource(R.string.action_add), onAddBox64Preset),
                SteamFieldAction(stringResource(R.string.action_edit), onEditBox64Preset),
                SteamFieldAction(stringResource(R.string.action_duplicate), onDuplicateBox64Preset),
                SteamFieldAction(stringResource(R.string.action_remove), onRemoveBox64Preset),
                SteamFieldAction(stringResource(R.string.action_export), onExportBox64Preset),
                SteamFieldAction(stringResource(R.string.action_import), onImportBox64Preset),
            ),
        )
    }
    SteamToggleField(
        label = stringResource(R.string.enable_xinput_for_wine_game),
        checked = enableXInput,
        onCheckedChange = onEnableXInputChange,
    )
    SteamToggleField(
        label = stringResource(R.string.enable_dinput_for_wine_game),
        checked = enableDInput,
        onCheckedChange = onEnableDInputChange,
    )
    if (enableDInput) {
        SteamSelectField(
            label = "DInput Mapper",
            value = dinputMapperType.toString(),
            options = dinputMapperOptions,
            onSelect = { selected -> onDinputMapperTypeChange(selected.toIntOrNull()?.coerceIn(0, 1) ?: 0) },
        )
    }
    SteamToggleField(
        label = stringResource(R.string.disable_xinput_for_shortcut),
        checked = disableXInput,
        onCheckedChange = onDisableXInputChange,
    )
    SteamToggleField(
        label = stringResource(R.string.relative_mouse_movement),
        checked = relativeMouseMovement,
        onCheckedChange = onRelativeMouseMovementChange,
    )
    SteamToggleField(
        label = stringResource(R.string.simulate_touch_screen),
        checked = simTouchScreen,
        onCheckedChange = onSimTouchScreenChange,
    )
}

@Composable
private fun SteamFooterButton(
    text: String,
    background: Brush,
    onClick: () -> Unit,
) {
    // Legacy helper kept for future reuse.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SteamTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = glassAccent(),
                unfocusedBorderColor = glassChevronColor(),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = glassCardFill(),
                unfocusedContainerColor = glassCardFill(),
                cursorColor = glassAccent(),
            ),
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun SteamSelectField(
    label: String,
    value: String,
    options: List<SteamOption>,
    onSelect: (String) -> Unit,
    supportingText: String? = null,
    enabled: Boolean = true,
    actions: List<SteamFieldAction> = emptyList(),
    actionsPlacement: SteamFieldActionsPlacement = SteamFieldActionsPlacement.BELOW,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == value }?.label ?: value
    val useSideActions = actions.isNotEmpty() && actionsPlacement == SteamFieldActionsPlacement.SIDE

    @Composable
    fun SelectorCard(modifier: Modifier = Modifier) {
        Box(modifier = modifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (enabled) MaterialTheme.colorScheme.surfaceVariant else glassCardFill())
                    .border(1.dp, if (enabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable(enabled = enabled && options.isNotEmpty()) { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = selectedLabel,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .fillMaxWidth(0.9f),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                color = if (option.value == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(option.value)
                        },
                    )
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (useSideActions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectorCard(Modifier.weight(1f))
                actions.forEach { action ->
                    SteamInlineActionButton(action = action)
                }
            }
        } else {
            SelectorCard(Modifier.fillMaxWidth())
        }
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!useSideActions && actions.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    SteamActionChip(
                        text = action.label,
                        onClick = action.onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamInlineActionButton(
    action: SteamFieldAction,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (action.iconRes != null) {
            Icon(
                painter = painterResource(action.iconRes),
                contentDescription = action.label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Text(
                text = action.label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SteamActionChip(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SteamModalScaffold(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            if (onConfirm != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    SteamModalButton(
                        text = stringResource(R.string.cancel),
                        background = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)),
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        onClick = onDismiss,
                    )
                    SteamModalButton(
                        text = stringResource(R.string.ok),
                        background = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
                        borderColor = Color.Transparent,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamModalButton(
    text: String,
    background: Brush,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SteamDownloadItemCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101722))
            .border(1.dp, Color(0xFF223349), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = Color(0xFF95A6BF),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SteamComponentDownloadOverlay(
    displayName: String,
    type: ContentProfile.ContentType,
    contentsManager: ContentsManager,
    onDismiss: () -> Unit,
    onInstalled: (String) -> Unit,
) {
    val context = LocalContext.current
    var reloadToken by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf(emptyList<SteamRemoteContentOption>()) }

    LaunchedEffect(type, reloadToken) {
        isLoading = true
        errorMessage = null
        items = emptyList()
        loadRemoteProfilesAsync(
            context = context,
            contentsManager = contentsManager,
            onError = {
                isLoading = false
                errorMessage = it
            },
            onLoaded = {
                val profiles = contentsManager.getProfiles(type)
                    .filter { profile ->
                        !profile.remoteUrl.isNullOrEmpty() &&
                            !ContentsManager.getInstallDir(context, profile).exists()
                    }
                items = profiles.map { profile ->
                    val selectedValue = when (type) {
                        ContentProfile.ContentType.CONTENT_TYPE_VKD3D -> vkd3dVersionIdentifier(profile)
                        else -> getVersionSpinnerValue(profile)
                    }
                    SteamRemoteContentOption(
                        title = if (type == ContentProfile.ContentType.CONTENT_TYPE_VKD3D) {
                            profile.verName
                        } else {
                            selectedValue
                        },
                        subtitle = buildString {
                            if (profile.verCode > 0) append(context.getString(R.string.version_code) + ": " + profile.verCode)
                            if (!profile.desc.isNullOrBlank()) {
                                if (isNotBlank()) append(" • ")
                                append(profile.desc)
                            }
                        }.ifBlank { null },
                        profile = profile,
                        selectedValue = selectedValue,
                    )
                }
                isLoading = false
            },
        )
    }

    SteamModalScaffold(
        title = context.getString(R.string.download_component_title, displayName, items.size),
        onDismiss = onDismiss,
    ) {
        when {
            isLoading -> {
                Text(
                    text = stringResource(R.string.loading_component_versions),
                    color = Color(0xFF95A6BF),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF18C5BE),
                    trackColor = Color(0xFF223349),
                )
            }

            !errorMessage.isNullOrBlank() -> {
                Text(
                    text = errorMessage.orEmpty(),
                    color = Color(0xFFFF9BA1),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SteamActionChip(
                    text = stringResource(R.string.action_download),
                    onClick = { reloadToken++ },
                )
            }

            items.isEmpty() -> {
                Text(
                    text = context.getString(R.string.all_component_versions_installed, displayName),
                    color = Color(0xFF95A6BF),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            else -> {
                items.forEach { item ->
                    SteamDownloadItemCard(
                        title = item.title,
                        subtitle = item.subtitle,
                    ) {
                        onDismiss()
                        downloadAndInstallShortcutContent(context, contentsManager, item.profile) {
                            contentsManager.syncContents()
                            onInstalled(item.selectedValue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamDriverDownloadOverlay(
    onDismiss: () -> Unit,
    onInstalled: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val resolver = remember { DriverResolver(context) }
    val adrenotoolsManager = remember { AdrenotoolsManager(context) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var progressLabel by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var items by remember { mutableStateOf(emptyList<SteamDriverRemoteOption>()) }

    DisposableEffect(Unit) {
        onDispose { resolver.shutdown() }
    }

    LaunchedEffect(reloadToken) {
        isLoading = true
        errorMessage = null
        progressLabel = null
        downloadProgress = -1
        items = emptyList()
        resolver.searchDrivers(object : DriverResolver.DriverSearchCallback {
            override fun onDriversFound(drivers: List<DriverResolver.DriverInfo>) {
                activity?.runOnUiThread {
                    items = drivers.map { driver ->
                        SteamDriverRemoteOption(
                            title = driver.name,
                            subtitle = listOfNotNull(
                                driver.repoName.takeIf { it.isNotBlank() },
                                driver.version.takeIf { it.isNotBlank() },
                            ).joinToString(" • ").ifBlank { null },
                            info = driver,
                        )
                    }
                    isLoading = false
                }
            }

            override fun onError(error: String) {
                activity?.runOnUiThread {
                    errorMessage = error
                    isLoading = false
                }
            }
        })
    }

    SteamModalScaffold(
        title = stringResource(R.string.download_graphics_driver),
        onDismiss = onDismiss,
    ) {
        when {
            isLoading -> {
                Text(
                    text = stringResource(R.string.loading_driver_versions),
                    color = Color(0xFF95A6BF),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF18C5BE),
                    trackColor = Color(0xFF223349),
                )
            }

            !errorMessage.isNullOrBlank() -> {
                Text(
                    text = errorMessage.orEmpty(),
                    color = Color(0xFFFF9BA1),
                    style = MaterialTheme.typography.bodyLarge,
                )
                SteamActionChip(
                    text = stringResource(R.string.action_download),
                    onClick = { reloadToken++ },
                )
            }

            progressLabel != null -> {
                Text(
                    text = progressLabel.orEmpty(),
                    color = Color(0xFFF3F7FF),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinearProgressIndicator(
                    progress = { (downloadProgress.coerceAtLeast(0) / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF18C5BE),
                    trackColor = Color(0xFF223349),
                )
                Text(
                    text = "${downloadProgress.coerceAtLeast(0)}%",
                    color = Color(0xFF95A6BF),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            items.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_driver_versions_available),
                    color = Color(0xFF95A6BF),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            else -> {
                items.forEach { item ->
                    SteamDownloadItemCard(
                        title = item.title,
                        subtitle = item.subtitle,
                    ) {
                        progressLabel = context.getString(R.string.downloading_driver_message, item.info.name)
                        downloadProgress = 0
                        resolver.downloadDriver(item.info, object : DriverResolver.DriverDownloadCallback {
                            override fun onProgress(progress: Int) {
                                activity?.runOnUiThread {
                                    downloadProgress = progress
                                }
                            }

                            override fun onComplete(driverUri: Uri) {
                                Thread {
                                    val installedDriverId = adrenotoolsManager.installDriver(driverUri)
                                    activity?.runOnUiThread {
                                        if (installedDriverId.isNotBlank()) {
                                            onInstalled(installedDriverId)
                                            onDismiss()
                                        } else {
                                            progressLabel = null
                                            downloadProgress = -1
                                            errorMessage = context.getString(R.string.driver_installation_failed)
                                        }
                                    }
                                }.start()
                            }

                            override fun onError(error: String) {
                                activity?.runOnUiThread {
                                    progressLabel = null
                                    downloadProgress = -1
                                    errorMessage = context.getString(R.string.download_error, error)
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamGraphicsDriverConfigOverlay(
    graphicsDriver: String,
    currentConfig: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    val config = remember(currentConfig) { GraphicsDriverConfigDialog.parseGraphicsDriverConfig(currentConfig) }
    var version by remember(currentConfig) { mutableStateOf(config["version"].orEmpty().ifBlank { DefaultVersion.WRAPPER }) }
    var maxDeviceMemory by remember(currentConfig) { mutableStateOf(config["maxDeviceMemory"].orEmpty().ifBlank { "0" }) }
    var frameSync by remember(currentConfig) { mutableStateOf(config["frameSync"].orEmpty().ifBlank { "Normal" }) }
    var presentMode by remember(currentConfig) { mutableStateOf(config["presentMode"].orEmpty().ifBlank { "mailbox" }) }
    var resourceType by remember(currentConfig) { mutableStateOf(config["resourceType"].orEmpty().ifBlank { "auto" }) }
    var adrenotoolsTurnip by remember(currentConfig) { mutableStateOf(config["adrenotoolsTurnip"] != "0") }
    var blit by remember(currentConfig) { mutableStateOf(config["blit"] == "1") }
    var showDriverDownloads by remember { mutableStateOf(false) }

    val versionOptions = remember(showDriverDownloads, currentConfig, version) {
        ensureCurrentOption(loadGraphicsDriverVersionOptions(context), version)
    }
    val memoryOptions = remember { buildNumericOptions(context, R.array.device_memory_entries) }
    val frameSyncOptions = remember { buildRawOptions(context, R.array.frame_sync_entries) }
    val presentModeOptions = remember { buildRawOptions(context, R.array.present_mode_entries) }
    val resourceTypeOptions = remember { buildRawOptions(context, R.array.resource_type_entries) }

    SteamModalScaffold(
        title = stringResource(R.string.graphics_driver_configuration),
        onDismiss = onDismiss,
        onConfirm = {
            onSave(
                buildGraphicsDriverConfigValue(
                    version = version,
                    blacklistedExtensions = config["blacklistedExtensions"].orEmpty(),
                    maxDeviceMemory = maxDeviceMemory,
                    adrenotoolsTurnip = adrenotoolsTurnip,
                    frameSync = frameSync,
                    presentMode = presentMode,
                    resourceType = resourceType,
                    blit = blit,
                ),
            )
        },
    ) {
        Text(
            text = graphicsDriver,
            color = Color(0xFF18C5BE),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        SteamSelectField(
            label = stringResource(R.string.graphics_driver_version),
            value = version,
            options = versionOptions,
            onSelect = { version = it },
            actionsPlacement = SteamFieldActionsPlacement.SIDE,
            actions = listOf(
                SteamFieldAction(
                    label = stringResource(R.string.action_download),
                    onClick = { showDriverDownloads = true },
                    iconRes = R.drawable.icon_popup_menu_download,
                ),
            ),
        )
        SteamSelectField(
            label = stringResource(R.string.graphics_driver_max_device_memory),
            value = maxDeviceMemory,
            options = memoryOptions,
            onSelect = { maxDeviceMemory = it },
        )
        SteamSelectField(
            label = stringResource(R.string.graphics_driver_frame_sync),
            value = frameSync,
            options = frameSyncOptions,
            onSelect = { frameSync = it },
        )
        SteamSelectField(
            label = stringResource(R.string.graphics_driver_present_modes),
            value = presentMode,
            options = presentModeOptions,
            onSelect = { presentMode = it },
        )
        SteamSelectField(
            label = stringResource(R.string.graphics_driver_resource_type),
            value = resourceType,
            options = resourceTypeOptions,
            onSelect = { resourceType = it },
        )
        SteamToggleField(
            label = stringResource(R.string.graphics_driver_adrenotools_turnip),
            checked = adrenotoolsTurnip,
            onCheckedChange = { adrenotoolsTurnip = it },
        )
        SteamToggleField(
            label = stringResource(R.string.graphics_driver_enable_blit),
            checked = blit,
            onCheckedChange = { blit = it },
        )
    }

    if (showDriverDownloads) {
        SteamDriverDownloadOverlay(
            onDismiss = { showDriverDownloads = false },
            onInstalled = {
                version = it
                showDriverDownloads = false
            },
        )
    }
}

@Composable
private fun SteamDxWrapperConfigOverlay(
    dxWrapper: String,
    currentConfig: String,
    contentsManager: ContentsManager,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var nestedDownloadRequest by remember { mutableStateOf<SteamComponentDownloadRequest?>(null) }

    if (dxWrapper.equals("vkd3d", ignoreCase = true)) {
        val config = remember(currentConfig) { VKD3DConfigDialog.parseConfig(currentConfig) }
        var version by remember(currentConfig) { mutableStateOf(config.get("vkd3dVersion", DefaultVersion.VKD3D)) }
        var featureLevel by remember(currentConfig) { mutableStateOf(config.get("vkd3dLevel", "12_1")) }
        val versionOptions = remember(currentConfig, version) {
            ensureCurrentOption(loadVkd3dVersionOptions(context, contentsManager), version)
        }
        val featureLevelOptions = remember { buildRawOptions(context, R.array.dxvk_max_feature_level_entries) }

        SteamModalScaffold(
            title = "VKD3D ${context.getString(R.string.configuration)}",
            onDismiss = onDismiss,
            onConfirm = {
                val newConfig = VKD3DConfigDialog.parseConfig(currentConfig)
                newConfig.put("vkd3dVersion", version)
                newConfig.put("vkd3dLevel", featureLevel)
                onSave(newConfig.toString())
            },
        ) {
            SteamSelectField(
                label = stringResource(R.string.version),
                value = version,
                options = versionOptions,
                onSelect = { version = it },
                actionsPlacement = SteamFieldActionsPlacement.SIDE,
                actions = listOf(
                    SteamFieldAction(
                        label = stringResource(R.string.action_download),
                        onClick = {
                            nestedDownloadRequest = SteamComponentDownloadRequest(
                                type = ContentProfile.ContentType.CONTENT_TYPE_VKD3D,
                                displayName = "VKD3D",
                            ) { selectedValue ->
                                version = selectedValue
                            }
                        },
                        iconRes = R.drawable.icon_popup_menu_download,
                    ),
                ),
            )
            SteamSelectField(
                label = "Feature Level",
                value = featureLevel,
                options = featureLevelOptions,
                onSelect = { featureLevel = it },
            )
        }
    } else {
        val config = remember(currentConfig) { DXVKConfigDialog.parseConfig(currentConfig) }
        var version by remember(currentConfig) { mutableStateOf(config.get("version", DefaultVersion.DXVK)) }
        var maxDeviceMemory by remember(currentConfig) { mutableStateOf(config.get("maxDeviceMemory", "0")) }
        var maxFrameLatency by remember(currentConfig) { mutableStateOf(config.get("maxFrameLatency", "0")) }
        var enableMemoryDefrag by remember(currentConfig) { mutableStateOf(config.get("enableMemoryDefrag", "Auto")) }
        var async by remember(currentConfig) { mutableStateOf(config.get("async", "0") == "1") }
        var asyncCache by remember(currentConfig) { mutableStateOf(config.get("asyncCache", "0") == "1") }
        val dxvkType = remember(version) { classifyDxvkVersion(version) }

        val versionOptions = remember(currentConfig, version) {
            ensureCurrentOption(loadDxvkVersionOptions(context, contentsManager), version)
        }
        val memoryOptions = remember { buildNumericOptions(context, R.array.dxvk_max_device_memory_entries) }
        val frameLatencyOptions = remember { buildNumericOptions(context, R.array.dxvk_max_frame_latency_entries) }
        val booleanOptions = remember { buildRawOptions(context, R.array.dxvk_boolean_option_entries) }

        SteamModalScaffold(
            title = "DXVK ${context.getString(R.string.configuration)}",
            onDismiss = onDismiss,
            onConfirm = {
                val newConfig = DXVKConfigDialog.parseConfig(currentConfig)
                newConfig.put("version", version)
                newConfig.put("maxDeviceMemory", maxDeviceMemory)
                newConfig.put("maxFrameLatency", maxFrameLatency)
                newConfig.put("enableMemoryDefrag", enableMemoryDefrag)
                newConfig.put("async", if (dxvkType != DXVKConfigDialog.DXVK_TYPE_NONE && async) "1" else "0")
                newConfig.put(
                    "asyncCache",
                    if (dxvkType == DXVKConfigDialog.DXVK_TYPE_GPLASYNC && asyncCache) "1" else "0",
                )
                onSave(newConfig.toString())
            },
        ) {
            SteamSelectField(
                label = stringResource(R.string.version),
                value = version,
                options = versionOptions,
                onSelect = { version = it },
                actionsPlacement = SteamFieldActionsPlacement.SIDE,
                actions = listOf(
                    SteamFieldAction(
                        label = stringResource(R.string.action_download),
                        onClick = {
                            nestedDownloadRequest = SteamComponentDownloadRequest(
                                type = ContentProfile.ContentType.CONTENT_TYPE_DXVK,
                                displayName = "DXVK",
                            ) { selectedValue ->
                                version = selectedValue
                            }
                        },
                        iconRes = R.drawable.icon_popup_menu_download,
                    ),
                ),
            )
            SteamSelectField(
                label = stringResource(R.string.max_device_memory),
                value = maxDeviceMemory,
                options = memoryOptions,
                onSelect = { maxDeviceMemory = it },
            )
            SteamSelectField(
                label = stringResource(R.string.dxvk_max_frame_latency),
                value = maxFrameLatency,
                options = frameLatencyOptions,
                onSelect = { maxFrameLatency = it },
            )
            SteamSelectField(
                label = stringResource(R.string.dxvk_enable_memory_defrag),
                value = enableMemoryDefrag,
                options = booleanOptions,
                onSelect = { enableMemoryDefrag = it },
            )
            if (dxvkType != DXVKConfigDialog.DXVK_TYPE_NONE) {
                SteamToggleField(
                    label = "Async",
                    checked = async,
                    onCheckedChange = { async = it },
                )
            }
            if (dxvkType == DXVKConfigDialog.DXVK_TYPE_GPLASYNC) {
                SteamToggleField(
                    label = "Async Cache",
                    checked = asyncCache,
                    onCheckedChange = { asyncCache = it },
                )
            }
        }
    }

    nestedDownloadRequest?.let { request ->
        SteamComponentDownloadOverlay(
            displayName = request.displayName,
            type = request.type,
            contentsManager = contentsManager,
            onDismiss = { nestedDownloadRequest = null },
            onInstalled = { selectedValue ->
                nestedDownloadRequest = null
                request.onInstalled(selectedValue)
            },
        )
    }
}

@Composable
private fun SteamAudioDriverConfigOverlay(
    currentConfig: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    val config = remember(currentConfig) { KeyValueSet(currentConfig.replace(';', ',')) }
    var performanceMode by remember(currentConfig) { mutableStateOf(config.getInt("performanceMode", 1).toString()) }
    var volume by remember(currentConfig) { mutableStateOf(config.getFloat("volume", 1.0f).coerceIn(0f, 1f)) }
    var latencyMillis by remember(currentConfig) { mutableStateOf(config.getInt("latencyMillis", 20).toString()) }

    val performanceOptions = remember { buildIndexedOptions(context, R.array.audio_performance_mode_entries) }
    val latencyOptions = remember {
        buildIndexedOptionsFromArrays(context, R.array.audio_latency_entries, R.array.audio_latency_values)
    }

    SteamModalScaffold(
        title = context.getString(R.string.audio_driver) + " " + context.getString(R.string.configuration),
        onDismiss = onDismiss,
        onConfirm = {
            val newConfig = KeyValueSet()
            newConfig.put("performanceMode", performanceMode.toIntOrNull() ?: 1)
            newConfig.put("volume", volume)
            newConfig.put("latencyMillis", latencyMillis.toIntOrNull() ?: 20)
            onSave(newConfig.toString())
        },
    ) {
        SteamSelectField(
            label = stringResource(R.string.performance_mode),
            value = performanceMode,
            options = performanceOptions,
            onSelect = { performanceMode = it },
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.volume),
                color = Color(0xFF9CB0CE),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = volume,
                onValueChange = { volume = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF18C5BE),
                    activeTrackColor = Color(0xFF18C5BE),
                    inactiveTrackColor = Color(0xFF223349),
                ),
            )
            Text(
                text = "${(volume * 100f).roundToInt()}%",
                color = Color(0xFF95A6BF),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SteamSelectField(
            label = stringResource(R.string.average_latency),
            value = latencyMillis,
            options = latencyOptions,
            onSelect = { latencyMillis = it },
        )
    }
}

@Composable
private fun SteamToggleField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101722))
            .border(1.dp, Color(0xFF223349), RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                color = Color(0xFFF3F7FF),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (checked) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                color = Color(0xFF95A6BF),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF18C5BE),
                checkedTrackColor = Color(0xFF0E4E83),
                uncheckedThumbColor = Color(0xFF95A6BF),
                uncheckedTrackColor = Color(0xFF263348),
            ),
        )
    }
}

private fun saveShortcutSettings(
    shortcut: Shortcut,
    container: Container,
    containerEmulator: String,
    execArgs: String,
    screenSize: String,
    customScreenWidth: String,
    customScreenHeight: String,
    graphicsDriver: String,
    graphicsDriverConfig: String,
    dxWrapper: String,
    dxWrapperConfig: String,
    ddrawWrapper: String,
    audioDriver: String,
    audioDriverConfig: String,
    emulator: String,
    forceFullscreen: Boolean,
    fullscreenStretched: Boolean,
    useSecondaryExec: Boolean,
    secondaryExec: String,
    execDelay: String,
    startupSelection: Int,
    envVars: String,
    winComponentItems: List<WinComponentItem>,
    winComponentValues: Map<String, Int>,
    enableXInput: Boolean,
    enableDInput: Boolean,
    dinputMapperType: Int,
    disableXInput: Boolean,
    relativeMouseMovement: Boolean,
    simTouchScreen: Boolean,
    box64Version: String,
    box64Preset: String,
    fexcoreVersion: String,
    fexcorePreset: String,
    useUnixLibs: Boolean,
) {
    val resolvedScreenSize = resolveScreenSize(
        selectedValue = screenSize,
        customWidth = customScreenWidth,
        customHeight = customScreenHeight,
        fallback = container.screenSize,
    )
    val serializedWinComponents = serializeWinComponents(winComponentItems, winComponentValues)
    val resolvedStartupSelection = startupSelection.coerceIn(0, 2).toByte()

    var finalInputType = 0
    if (enableXInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
    if (enableDInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
    finalInputType = finalInputType or if (dinputMapperType == 0) {
        WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt()
    } else {
        WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt()
    }

    shortcut.putExtra("inputType", finalInputType.toString())
    shortcut.putExtra("disableXinput", if (disableXInput) "1" else null)
    shortcut.putExtra("relativeMouseMovement", if (relativeMouseMovement) "1" else "0")
    shortcut.putExtra("simTouchScreen", if (simTouchScreen) "1" else "0")

    shortcut.putExtra("execArgs", execArgs.trim().ifEmpty { null })
    shortcut.putExtra("screenSize", if (resolvedScreenSize != container.screenSize) resolvedScreenSize else null)
    shortcut.putExtra("graphicsDriver", if (graphicsDriver != container.graphicsDriver) graphicsDriver else null)
    shortcut.putExtra(
        "graphicsDriverConfig",
        if (graphicsDriverConfig != container.graphicsDriverConfig) graphicsDriverConfig else null,
    )
    shortcut.putExtra("dxwrapper", if (dxWrapper != container.getDXWrapper()) dxWrapper else null)
    shortcut.putExtra("dxwrapperConfig", if (dxWrapperConfig != container.getDXWrapperConfig()) dxWrapperConfig else null)
    shortcut.putExtra("ddrawrapper", if (ddrawWrapper != container.getDDrawWrapper()) ddrawWrapper else null)
    shortcut.putExtra("audioDriver", if (audioDriver != container.audioDriver) audioDriver else null)
    shortcut.putExtra("audioDriverConfig", if (audioDriverConfig != container.audioDriverConfig) audioDriverConfig else null)
    shortcut.putExtra("emulator", if (emulator != containerEmulator) emulator else null)
    shortcut.putExtra("forceFullscreen", if (forceFullscreen) "1" else null)
    shortcut.putExtra("fullscreenStretched", if (fullscreenStretched) "1" else null)

    if (useSecondaryExec) {
        shortcut.putExtra("secondaryExec", secondaryExec.trim().ifEmpty { null })
        shortcut.putExtra("execDelay", execDelay.trim().ifEmpty { null })
    } else {
        shortcut.putExtra("secondaryExec", null)
        shortcut.putExtra("execDelay", null)
    }

    shortcut.putExtra("wincomponents", if (serializedWinComponents != container.winComponents) serializedWinComponents else null)
    shortcut.putExtra("envVars", envVars.trim().ifEmpty { null })
    shortcut.putExtra(
        "startupSelection",
        if (resolvedStartupSelection != container.startupSelection) resolvedStartupSelection.toString() else null,
    )
    shortcut.putExtra("box64Version", if (box64Version != container.box64Version) box64Version else null)
    shortcut.putExtra("box64Preset", if (box64Preset != container.box64Preset) box64Preset else null)
    shortcut.putExtra("fexcoreVersion", if (fexcoreVersion != container.getFEXCoreVersion()) fexcoreVersion else null)
    shortcut.putExtra("fexcorePreset", if (fexcorePreset != container.getFEXCorePreset()) fexcorePreset else null)
    shortcut.putExtra("useUnixLibs", if (useUnixLibs != container.isUseUnixLibs) (if (useUnixLibs) "1" else "0") else null)
    shortcut.saveData()
}

private fun buildIdentifierOptions(context: Context, @ArrayRes arrayRes: Int): List<SteamOption> {
    return context.resources.getStringArray(arrayRes).map { label ->
        SteamOption(label = label, value = StringUtils.parseIdentifier(label))
    }
}

private fun buildRawOptions(context: Context, @ArrayRes arrayRes: Int): List<SteamOption> {
    return context.resources.getStringArray(arrayRes).map { SteamOption(label = it, value = it) }
}

private fun buildNumericOptions(context: Context, @ArrayRes arrayRes: Int): List<SteamOption> {
    return context.resources.getStringArray(arrayRes).map { SteamOption(label = it, value = StringUtils.parseNumber(it)) }
}

private fun buildIndexedOptions(context: Context, @ArrayRes arrayRes: Int): List<SteamOption> {
    return context.resources.getStringArray(arrayRes).mapIndexed { index, label ->
        SteamOption(label = label, value = index.toString())
    }
}

private fun buildIndexedOptionsFromArrays(
    context: Context,
    @ArrayRes labelArrayRes: Int,
    @ArrayRes valueArrayRes: Int,
): List<SteamOption> {
    val labels = context.resources.getStringArray(labelArrayRes)
    val values = context.resources.getStringArray(valueArrayRes)
    return labels.mapIndexed { index, label ->
        SteamOption(label = label, value = values.getOrNull(index) ?: label)
    }
}

private fun ensureCurrentOption(options: List<SteamOption>, currentValue: String): List<SteamOption> {
    if (currentValue.isBlank()) return options
    return if (options.any { it.value == currentValue }) {
        options
    } else {
        options + SteamOption(label = currentValue, value = currentValue)
    }
}

private fun splitScreenSize(value: String, fallback: String): Pair<String, String> {
    val source = if (Regex("^\\d+x\\d+$").matches(value)) value else fallback
    val parts = source.split("x")
    return if (parts.size == 2) {
        parts[0] to parts[1]
    } else {
        "1280" to "720"
    }
}

private fun resolveScreenSize(
    selectedValue: String,
    customWidth: String,
    customHeight: String,
    fallback: String,
): String {
    if (selectedValue != "custom") return selectedValue
    val width = customWidth.toIntOrNull()
    val height = customHeight.toIntOrNull()
    return if (width != null && height != null && width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0) {
        "${width}x${height}"
    } else {
        fallback
    }
}

private fun parseWinComponents(raw: String, context: Context): List<Pair<WinComponentItem, Int>> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",").mapNotNull { pair ->
        val separator = pair.indexOf('=')
        if (separator <= 0) {
            null
        } else {
            val key = pair.substring(0, separator)
            val value = pair.substring(separator + 1).toIntOrNull()?.coerceIn(0, 1) ?: 0
            val fallbackLabel = key
                .replace('_', ' ')
                .replaceFirstChar {
                    if (it.isLowerCase()) {
                        it.titlecase(Locale.getDefault())
                    } else {
                        it.toString()
                    }
                }
            val label = StringUtils.getString(context, key)?.takeIf { it.isNotBlank() } ?: fallbackLabel
            WinComponentItem(key = key, label = label) to value
        }
    }
}

private fun serializeWinComponents(
    items: List<WinComponentItem>,
    values: Map<String, Int>,
): String {
    return items.joinToString(",") { item -> "${item.key}=${values[item.key] ?: 0}" }
}

private fun loadBox64VersionOptions(
    context: Context,
    contentsManager: ContentsManager,
    isArm64EC: Boolean,
): List<SteamOption> {
    val arrayRes = if (isArm64EC) R.array.wowbox64_version_entries else R.array.box64_version_entries
    val items = context.resources.getStringArray(arrayRes).toMutableList()
    val type = if (isArm64EC) {
        ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
    } else {
        ContentProfile.ContentType.CONTENT_TYPE_BOX64
    }
    contentsManager.getProfiles(type).forEach { profile ->
        if (profile.remoteUrl != null && !ContentsManager.getInstallDir(context, profile).exists()) return@forEach
        val version = getVersionSpinnerValue(profile)
        if (version !in items) {
            items.add(version)
        }
    }
    return items.map { SteamOption(label = it, value = it) }
}

private fun loadFexcoreVersionOptions(
    context: Context,
    contentsManager: ContentsManager,
): List<SteamOption> {
    val items = context.resources.getStringArray(R.array.fexcore_version_entries).toMutableList()
    contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE).forEach { profile ->
        if (profile.remoteUrl != null && !ContentsManager.getInstallDir(context, profile).exists()) return@forEach
        val version = getVersionSpinnerValue(profile)
        if (version !in items) {
            items.add(version)
        }
    }
    return items.map { SteamOption(label = it, value = it) }
}

private fun loadGraphicsDriverVersionOptions(context: Context): List<SteamOption> {
    val items = context.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries).toMutableList()
    AdrenotoolsManager(context).enumarateInstalledDrivers().forEach { version ->
        if (version !in items) {
            items.add(version)
        }
    }
    return items.map { SteamOption(label = it, value = it) }
}

private fun loadDxvkVersionOptions(
    context: Context,
    contentsManager: ContentsManager,
): List<SteamOption> {
    val items = context.resources.getStringArray(R.array.dxvk_version_entries).toMutableList()
    contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK).forEach { profile ->
        if (profile.remoteUrl != null && !ContentsManager.getInstallDir(context, profile).exists()) return@forEach
        val version = getVersionSpinnerValue(profile)
        if (version !in items) {
            items.add(version)
        }
    }
    return items.map { SteamOption(label = it, value = it) }
}

private fun loadVkd3dVersionOptions(
    context: Context,
    contentsManager: ContentsManager,
): List<SteamOption> {
    val items = context.resources.getStringArray(R.array.vkd3d_version_entries).map {
        SteamOption(label = it, value = "$it-0")
    }.toMutableList()
    contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VKD3D).forEach { profile ->
        if (profile.remoteUrl != null && !ContentsManager.getInstallDir(context, profile).exists()) return@forEach
        val value = vkd3dVersionIdentifier(profile)
        if (items.none { it.value == value }) {
            items.add(SteamOption(label = profile.verName, value = value))
        }
    }
    return items
}

private fun describeDxWrapperConfig(
    dxWrapper: String,
    configValue: String,
): String? {
    return when {
        dxWrapper.equals("dxvk", ignoreCase = true) -> {
            val config = DXVKConfigDialog.parseConfig(configValue)
            "DXVK ${config.get("version", "")}".trim().ifBlank { null }
        }

        dxWrapper.equals("vkd3d", ignoreCase = true) -> {
            val config = VKD3DConfigDialog.parseConfig(configValue)
            listOf(
                config.get("vkd3dVersion", ""),
                config.get("vkd3dLevel", ""),
            ).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { null }
        }

        else -> null
    }
}

private fun classifyDxvkVersion(version: String): Int {
    return when {
        version.contains("gplasync", ignoreCase = true) -> DXVKConfigDialog.DXVK_TYPE_GPLASYNC
        version.contains("async", ignoreCase = true) -> DXVKConfigDialog.DXVK_TYPE_ASYNC
        else -> DXVKConfigDialog.DXVK_TYPE_NONE
    }
}

private fun buildGraphicsDriverConfigValue(
    version: String,
    blacklistedExtensions: String,
    maxDeviceMemory: String,
    adrenotoolsTurnip: Boolean,
    frameSync: String,
    presentMode: String,
    resourceType: String,
    blit: Boolean,
): String {
    val normalizedFrameSync = when (frameSync) {
        "Never", "Always" -> frameSync
        else -> "Normal"
    }
    val effectivePresentMode = when {
        presentMode.isBlank() && normalizedFrameSync == "Never" -> "immediate"
        presentMode.isBlank() && normalizedFrameSync == "Always" -> "fifo"
        presentMode.isBlank() -> "mailbox"
        normalizedFrameSync == "Normal" && presentMode.equals("relaxed", ignoreCase = true) -> "mailbox"
        else -> presentMode.lowercase(Locale.getDefault())
    }
    return GraphicsDriverConfigDialog.toGraphicsDriverConfig(
        hashMapOf(
            "version" to version,
            "blacklistedExtensions" to blacklistedExtensions,
            "maxDeviceMemory" to maxDeviceMemory,
            "adrenotoolsTurnip" to if (adrenotoolsTurnip) "1" else "0",
            "frameSync" to normalizedFrameSync,
            "presentMode" to effectivePresentMode,
            "resourceType" to resourceType,
            "blit" to if (blit) "1" else "0",
            "enableHDR" to "0",
            "hdrMode" to "0",
            "hdrColorSpace" to "0",
            "hdrToneMapping" to "0",
            "enable10Bit" to "0",
            "enableWideColorGamut" to "0",
        ),
    )
}

private fun vkd3dVersionIdentifier(profile: ContentProfile): String {
    return "${profile.verName}-${profile.verCode}"
}

private fun loadRemoteProfilesAsync(
    context: Context,
    contentsManager: ContentsManager,
    onError: (String) -> Unit,
    onLoaded: () -> Unit,
) {
    val activity = context.findActivity()
    Thread {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val contentsUrl = preferences.getString(
            "downloadable_contents_url",
            "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json",
        )
        val json = Downloader.downloadString(contentsUrl)
        activity?.runOnUiThread {
            if (json == null) {
                onError(context.getString(R.string.failed_to_load_remote_contents))
            } else {
                contentsManager.setRemoteProfiles(json)
                onLoaded()
            }
        }
    }.start()
}

private fun showGraphicsDriverConfigDialog(
    context: Context,
    graphicsDriver: String,
    currentConfig: String,
    onConfigChanged: (String) -> Unit,
) {
    val anchor = View(context).apply {
        tag = currentConfig
    }
    val versionView = TextView(context)
    GraphicsDriverConfigDialog(anchor, graphicsDriver, versionView).apply {
        setOnDismissListener {
            onConfigChanged(anchor.tag?.toString().orEmpty().ifBlank { currentConfig })
        }
    }.show()
}

private fun showDxWrapperConfigDialog(
    context: Context,
    dxWrapper: String,
    currentConfig: String,
    onConfigChanged: (String) -> Unit,
) {
    val anchor = View(context).apply {
        tag = currentConfig
    }
    val dialog = when {
        dxWrapper.equals("dxvk", ignoreCase = true) -> DXVKConfigDialog(anchor)
        dxWrapper.equals("vkd3d", ignoreCase = true) -> VKD3DConfigDialog(anchor)
        else -> null
    } ?: return
    dialog.setOnDismissListener {
        onConfigChanged(anchor.tag?.toString().orEmpty().ifBlank { currentConfig })
    }
    dialog.show()
}

private fun showAudioDriverConfigDialog(
    context: Context,
    currentConfig: String,
    onConfigChanged: (String) -> Unit,
) {
    val anchor = View(context).apply {
        tag = currentConfig
    }
    AudioDriverConfigDialog(anchor).apply {
        setOnDismissListener {
            onConfigChanged(anchor.tag?.toString().orEmpty().ifBlank { currentConfig })
        }
    }.show()
}

private fun showComponentVersionDownloadDialog(
    context: Context,
    contentsManager: ContentsManager,
    type: ContentProfile.ContentType,
    displayName: String,
    onInstalled: (String) -> Unit,
) {
    loadRemoteProfiles(context, contentsManager) {
        val downloadableProfiles = contentsManager.getProfiles(type).filter { profile ->
            !profile.remoteUrl.isNullOrEmpty() && !ContentsManager.getInstallDir(context, profile).exists()
        }
        if (downloadableProfiles.isEmpty()) {
            AppUtils.showToast(context, context.getString(R.string.all_component_versions_installed, displayName))
            return@loadRemoteProfiles
        }

        val items = downloadableProfiles.map(::getVersionSpinnerValue).toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.download_component_title, displayName, downloadableProfiles.size))
            .setItems(items) { _, which ->
                if (which !in downloadableProfiles.indices) return@setItems
                val profile = downloadableProfiles[which]
                val selectedValue = items[which]
                downloadAndInstallShortcutContent(context, contentsManager, profile) {
                    contentsManager.syncContents()
                    onInstalled(selectedValue)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

private fun loadRemoteProfiles(
    context: Context,
    contentsManager: ContentsManager,
    onLoaded: () -> Unit,
) {
    val activity = context.findActivity() ?: return
    val dialog = PreloaderDialog(activity)
    dialog.showOnUiThread(R.string.loading_component_versions)

    Thread {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val contentsUrl = preferences.getString(
            "downloadable_contents_url",
            "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json",
        )
        val json = Downloader.downloadString(contentsUrl)
        dialog.closeOnUiThread()

        activity.runOnUiThread {
            if (json == null) {
                AppUtils.showToast(context, R.string.failed_to_load_remote_contents)
                return@runOnUiThread
            }
            contentsManager.setRemoteProfiles(json)
            onLoaded()
        }
    }.start()
}

private fun downloadAndInstallShortcutContent(
    context: Context,
    contentsManager: ContentsManager,
    profile: ContentProfile,
    onInstalled: () -> Unit,
) {
    val activity = context.findActivity() ?: return
    if (profile.remoteUrl.isNullOrEmpty()) return

    val downloadDialog = PreloaderDialog(activity)
    downloadDialog.showOnUiThread(R.string.downloading_file)

    Thread {
        val output = File(context.cacheDir, "shortcut_content_${System.currentTimeMillis()}")
        if (!Downloader.downloadFile(profile.remoteUrl, output)) {
            downloadDialog.closeOnUiThread()
            activity.runOnUiThread {
                AppUtils.showToast(context, R.string.unable_to_install_content)
            }
            return@Thread
        }

        downloadDialog.closeOnUiThread()
        installShortcutContentFromUri(
            context = context,
            contentsManager = contentsManager,
            uri = Uri.fromFile(output),
            expectedProfile = profile,
            onInstalled = {
                if (output.exists()) output.delete()
                onInstalled()
            },
            onFailed = {
                if (output.exists()) output.delete()
            },
        )
    }.start()
}

private fun installShortcutContentFromUri(
    context: Context,
    contentsManager: ContentsManager,
    uri: Uri,
    expectedProfile: ContentProfile,
    onInstalled: () -> Unit,
    onFailed: () -> Unit,
) {
    val activity = context.findActivity() ?: return
    val dialog = PreloaderDialog(activity)
    dialog.showOnUiThread(R.string.installing_content)

    val callback = object : ContentsManager.OnInstallFinishedCallback {
        private var isExtracting = true

        override fun onFailed(reason: ContentsManager.InstallFailedReason?, e: Exception?) {
            val msgId = when (reason) {
                ContentsManager.InstallFailedReason.ERROR_BADTAR -> R.string.file_cannot_be_recognied
                ContentsManager.InstallFailedReason.ERROR_NOPROFILE -> R.string.profile_not_found_in_content
                ContentsManager.InstallFailedReason.ERROR_BADPROFILE -> R.string.profile_cannot_be_recognized
                ContentsManager.InstallFailedReason.ERROR_EXIST -> R.string.content_already_exist
                ContentsManager.InstallFailedReason.ERROR_MISSINGFILES -> R.string.content_is_incomplete
                ContentsManager.InstallFailedReason.ERROR_UNTRUSTPROFILE -> R.string.content_cannot_be_trusted
                else -> R.string.unable_to_install_content
            }
            activity.runOnUiThread {
                ContentDialog.alert(
                    context,
                    context.getString(R.string.install_failed) + ": " + context.getString(msgId),
                ) {
                    dialog.closeOnUiThread()
                    onFailed()
                }
            }
        }

        override fun onSucceed(installedProfile: ContentProfile) {
            installedProfile.type = expectedProfile.type
            installedProfile.verName = expectedProfile.verName
            installedProfile.verCode = expectedProfile.verCode

            if (isExtracting) {
                val continueCallback = this
                activity.runOnUiThread {
                    val infoDialog = ContentInfoDialog(context, installedProfile)
                    (infoDialog.findViewById<View>(R.id.BTConfirm) as? TextView)?.setText(R.string._continue)
                    infoDialog.setOnConfirmCallback {
                        isExtracting = false
                        val untrustedFiles = contentsManager.getUnTrustedContentFiles(installedProfile)
                        if (untrustedFiles.isNotEmpty()) {
                            val untrustedDialog = ContentUntrustedDialog(context, untrustedFiles)
                            untrustedDialog.setOnCancelCallback {
                                dialog.closeOnUiThread()
                                onFailed()
                            }
                            untrustedDialog.setOnConfirmCallback {
                                contentsManager.finishInstallContent(installedProfile, continueCallback)
                            }
                            untrustedDialog.show()
                        } else {
                            contentsManager.finishInstallContent(installedProfile, continueCallback)
                        }
                    }
                    infoDialog.setOnCancelCallback {
                        dialog.closeOnUiThread()
                        onFailed()
                    }
                    infoDialog.show()
                }
            } else {
                dialog.closeOnUiThread()
                activity.runOnUiThread {
                    ContentDialog.alert(context, R.string.content_installed_success, null)
                    contentsManager.syncContents()
                    onInstalled()
                }
            }
        }
    }

    Thread {
        contentsManager.extraContentFile(uri, callback)
    }.start()
}

private fun getVersionSpinnerValue(profile: ContentProfile): String {
    val entryName = ContentsManager.getEntryName(profile)
    val firstDashIndex = entryName.indexOf('-')
    return if (firstDashIndex >= 0) entryName.substring(firstDashIndex + 1) else entryName
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
