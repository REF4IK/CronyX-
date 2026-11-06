package com.winlator.cmod.ui.screens

import androidx.compose.foundation.shape.CircleShape

import com.winlator.cmod.ui.components.glassSliderColors
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSwitch
import com.winlator.cmod.ui.components.GlassChevron
import com.winlator.cmod.ui.components.GlassSurface
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassGroupDivider
import com.winlator.cmod.ui.components.glassTitleColor
import android.content.Intent
import android.content.SharedPreferences
import com.winlator.cmod.core.MmkvPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.UpdateCheckDialog

/**
 * Полный перенос SettingsFragment.java на Jetpack Compose.
 * Все значения хранятся в mutableStateOf, поэтому UI обновляется сразу.
 * Все переключатели сразу пишут в SharedPreferences.
 * Все клики выполняют реальные действия из оригинала.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: SharedPreferences?,
    appContext: android.content.Context,
    onDarkModeChange: (Boolean) -> Unit = {},
    onLanguageChange: (String) -> Unit = {},
    onTransitionAnimationChange: (String) -> Unit = {},
    onConfirmSave: () -> Unit = {},
    onOpenGPUPerformance: () -> Unit = {},
    onReinstallImageFs: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = preferences ?: MmkvPreferences()

    fun saveBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun saveFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    fun toast(resId: Int) = Toast.makeText(ctx, ctx.getString(resId), Toast.LENGTH_SHORT).show()

    // ---- Реактивные состояния с точными дефолтами из SettingsFragment.java ----
    var appLanguage by remember { mutableStateOf(prefs.getString("app_language", "system") ?: "system") }
    var transitionAnim by remember { mutableStateOf(prefs.getString("transition_animation", "none") ?: "none") }
    var cursorLock by remember { mutableStateOf(prefs.getBoolean("cursor_lock", true)) }
    var xinputToggle by remember { mutableStateOf(prefs.getBoolean("xinput_toggle", false)) }
    var legacyMode by remember { mutableStateOf(prefs.getBoolean("legacy_mode_enabled", false)) }
    var gyroEnabled by remember { mutableStateOf(prefs.getBoolean("gyro_enabled", false)) }
    var gyroMode by remember { mutableStateOf(prefs.getInt("gyro_mode", 0)) }
    var gyroTriggerButton by remember { mutableStateOf(prefs.getInt("gyro_trigger_button", android.view.KeyEvent.KEYCODE_BUTTON_L1)) }
    var useDri3 by remember { mutableStateOf(prefs.getBoolean("use_dri3", true)) }
    var useXr by remember { mutableStateOf(prefs.getBoolean("use_xr", true)) }
    var cursorSpeed by remember { mutableStateOf(prefs.getFloat("cursor_speed", 1.0f)) }
    var enableWineDebug by remember { mutableStateOf(prefs.getBoolean("enable_wine_debug", false)) }
    var enableBoxLogs by remember { mutableStateOf(prefs.getBoolean("enable_box86_64_logs", false)) }
    var triggerType by remember { mutableStateOf(prefs.getInt("trigger_type", 1)) }
    var enableFileProvider by remember { mutableStateOf(prefs.getBoolean("enable_file_provider", true)) }
    var openWithBrowser by remember { mutableStateOf(prefs.getBoolean("open_with_android_browser", false)) }
    var shareClipboard by remember { mutableStateOf(prefs.getBoolean("share_android_clipboard", false)) }
    var adrenoTurbo by remember { mutableStateOf(prefs.getBoolean("adreno_turbo_mode", false)) }

    // ---- Состояния диалогов ----
    var showCustomization by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAnimDialog by remember { mutableStateOf(false) }
    var showTriggerDialog by remember { mutableStateOf(false) }
    var showCursorSpeedDialog by remember { mutableStateOf(false) }
    var showGyroButtonDialog by remember { mutableStateOf(false) }
    var showGyroModeDialog by remember { mutableStateOf(false) }
    var showReinstallConfirm by remember { mutableStateOf(false) }
    var showBackupConfirm by remember { mutableStateOf(false) }
    var cursorSpeedSlider by remember { mutableStateOf((cursorSpeed * 100).toInt()) }

    // ---- Состояния апдейтера ----
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.winlator.cmod.core.UpdateManager.UpdateInfo?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var updateFile by remember { mutableStateOf<java.io.File?>(null) }
    var updateNotify by remember { mutableStateOf(com.winlator.cmod.core.UpdateManager.isNotifyEnabled(ctx)) }
    var showUpdateCheckDialog by remember { mutableStateOf(false) }

    // ---- Лаунчеры для файловых пикеров (реальные действия) ----
    val soundFontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val preloader = com.winlator.cmod.core.PreloaderDialog(ctx as android.app.Activity)
                preloader.showOnUiThread(com.winlator.cmod.R.string.installing_content)
                com.winlator.cmod.midi.MidiManager.installSF2File(ctx, uri, object : com.winlator.cmod.midi.MidiManager.OnSoundFontInstalledCallback {
                    override fun onSuccess() {
                        preloader.closeOnUiThread()
                        (ctx as android.app.Activity).runOnUiThread {
                            com.winlator.cmod.contentdialog.ContentDialog.alert(ctx, com.winlator.cmod.R.string.sound_font_installed_success, null)
                        }
                    }
                    override fun onFailed(reason: Int) {
                        preloader.closeOnUiThread()
                        val resId = when (reason) {
                            com.winlator.cmod.midi.MidiManager.ERROR_BADFORMAT -> com.winlator.cmod.R.string.sound_font_bad_format
                            com.winlator.cmod.midi.MidiManager.ERROR_EXIST -> com.winlator.cmod.R.string.sound_font_already_exist
                            else -> com.winlator.cmod.R.string.sound_font_installed_failed
                        }
                        (ctx as android.app.Activity).runOnUiThread {
                            com.winlator.cmod.contentdialog.ContentDialog.alert(ctx, resId, null)
                        }
                    }
                })
            } catch (e: Exception) {
                toast(com.winlator.cmod.R.string.unable_to_install_soundfont)
            }
        }
    }

    val losslessDllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            val imported = com.winlator.cmod.core.LsfgNative.importGlobalLosslessDll(ctx, uri)
            toast(if (imported) com.winlator.cmod.R.string.lsfg_lossless_dll_imported else com.winlator.cmod.R.string.lsfg_lossless_dll_import_failed)
            if (imported) {
                // Pre-build the SPIR-V cache now (Bannerlator parity): first
                // in-game enable then finds a ready cache, no restart needed.
                Thread({
                    val st = com.winlator.cmod.core.LsfgNative.ensureCache(ctx, false)
                    android.util.Log.i("SettingsScreen",
                        "LSFG cache pre-build: " + com.winlator.cmod.core.LsfgNative.explain(st))
                }, "lsfg-native-cache").start()
            }
        }
    }

    val frontendPathLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            saveString("frontend_export_uri", uri.toString())
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                toast("Unable to take persistable permissions: ${e.message}")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val intent = Intent(ctx, Class.forName("com.winlator.cmod.restore.RestoreActivity"))
                intent.data = uri
                ctx.startActivity(intent)
                (ctx as? android.app.Activity)?.finish()
            } catch (e: Exception) {
                toast("Restore: ${e.message}")
            }
        }
    }

    // ---- Списки для диалогов ----
    val languageOptions = listOf("system" to ctx.getString(com.winlator.cmod.R.string.app_language_system), "en" to "English", "ru" to "Русский", "zh" to "中文", "pt" to "Português", "pt-rBR" to "Português (Brasil)", "pl" to "Polski", "es" to "Español", "ja" to "日本語", "ar" to "العربية")
    val animOptions = listOf("none" to "None", "slide_vertical" to "Slide Vertical", "slide_horizontal" to "Slide Horizontal", "fade" to "Fade", "zoom" to "Zoom")
    val triggerOptions = listOf(0 to "Is Button", 1 to "Is Axis", 2 to "Is Mixed")
    val gyroModeOptions = listOf(0 to "Hold Mode", 1 to "Toggle Mode")
    val gyroButtonOptions = listOf(
        android.view.KeyEvent.KEYCODE_BUTTON_L1 to "L1",
        android.view.KeyEvent.KEYCODE_BUTTON_R1 to "R1",
        android.view.KeyEvent.KEYCODE_BUTTON_L2 to "L2",
        android.view.KeyEvent.KEYCODE_BUTTON_R2 to "R2",
        android.view.KeyEvent.KEYCODE_BUTTON_THUMBL to "Left Stick",
        android.view.KeyEvent.KEYCODE_BUTTON_THUMBR to "Right Stick",
    )

    fun langLabel(v: String) = languageOptions.firstOrNull { it.first == v }?.second ?: ctx.getString(com.winlator.cmod.R.string.app_language_system)
    fun animLabel(v: String) = animOptions.firstOrNull { it.first == v }?.second ?: "None"
    fun trigLabel(v: Int) = triggerOptions.firstOrNull { it.first == v }?.second ?: "Is Axis"
    fun gyroModeLabel(v: Int) = gyroModeOptions.firstOrNull { it.first == v }?.second ?: "Hold Mode"
    fun gyroButtonLabel(v: Int) = gyroButtonOptions.firstOrNull { it.first == v }?.second ?: "L1"

    // ---- UI ----
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 2. Общие
            SectionHeader(stringResource(com.winlator.cmod.R.string.general), Icons.Filled.Palette)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Language,
                    title = stringResource(com.winlator.cmod.R.string.language),
                    subtitle = langLabel(appLanguage),
                    onClick = { showLanguageDialog = true }
                )
                SettingsDivider()
                SettingsClickRow(
                    icon = Icons.Filled.Tune,
                    title = stringResource(com.winlator.cmod.R.string.customization),
                    subtitle = stringResource(com.winlator.cmod.R.string.customization_desc),
                    onClick = { showCustomization = true }
                )
            }

            // 3. Shortcuts
            SectionHeader(stringResource(com.winlator.cmod.R.string.shortcut_settings), Icons.Filled.Shortcut)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Folder,
                    title = stringResource(com.winlator.cmod.R.string.frontend_export_path),
                    subtitle = prefs.getString("frontend_export_uri", null) ?: "Downloads/Winlator/Frontend",
                    onClick = { frontendPathLauncher.launch(null) }
                )
            }

            // 4. Frame Generation
            SectionHeader(stringResource(com.winlator.cmod.R.string.frame_generation), Icons.Filled.Speed)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Memory,
                    title = stringResource(com.winlator.cmod.R.string.lossless_scaling_dll),
                    subtitle = run {
                        val dllPath = try { com.winlator.cmod.core.LsfgNative.globalDllPath(ctx) } catch (_: Exception) { null }
                        when {
                            dllPath != null -> stringResource(com.winlator.cmod.R.string.installed_at, dllPath)
                            else -> stringResource(com.winlator.cmod.R.string.not_installed)
                        }
                    },
                    onClick = { losslessDllLauncher.launch(arrayOf("*/*")) }
                )
            }

            // 5. XServer
            SectionHeader(stringResource(com.winlator.cmod.R.string.xserver_section), Icons.Filled.DesktopWindows)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Mouse,
                    title = stringResource(com.winlator.cmod.R.string.cursor_speed),
                    subtitle = "${(cursorSpeed * 100).toInt()}%",
                    onClick = {
                        cursorSpeedSlider = (cursorSpeed * 100).toInt()
                        showCursorSpeedDialog = true
                    }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.Bolt,
                    title = stringResource(com.winlator.cmod.R.string.dri3),
                    checked = useDri3,
                    onCheckedChange = { useDri3 = it; saveBool("use_dri3", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.ViewInAr,
                    title = stringResource(com.winlator.cmod.R.string.xr_mode),
                    checked = useXr,
                    onCheckedChange = { useXr = it; saveBool("use_xr", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.Lock,
                    title = stringResource(com.winlator.cmod.R.string.true_mouse_control),
                    checked = cursorLock,
                    onCheckedChange = { cursorLock = it; saveBool("cursor_lock", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.TouchApp,
                    title = stringResource(com.winlator.cmod.R.string.disable_xinput),
                    checked = xinputToggle,
                    onCheckedChange = { xinputToggle = it; saveBool("xinput_toggle", it) }
                )
            }

            // 6. GPU Performance
            SectionHeader(stringResource(com.winlator.cmod.R.string.gpu_performance_section), Icons.Filled.Memory)
            SettingsCard {
                SettingsButtonRow(
                    icon = Icons.Filled.Speed,
                    title = stringResource(com.winlator.cmod.R.string.configure_gpu_performance),
                    onClick = { onOpenGPUPerformance() }
                )
            }

            // 7. Adreno Turbo
            SectionHeader(stringResource(com.winlator.cmod.R.string.adreno_turbo), Icons.Filled.FlashOn)
            SettingsCard {
                SettingsCheckRow(
                    icon = Icons.Filled.FlashOn,
                    title = stringResource(com.winlator.cmod.R.string.max_gpu_frequency),
                    checked = adrenoTurbo,
                    onCheckedChange = {
                        try {
                            val success = com.winlator.cmod.core.GPUInformation.setTurboMode(it)
                            if (success) {
                                adrenoTurbo = it
                                saveBool("adreno_turbo_mode", it)
                                toast(ctx.getString(if (it) com.winlator.cmod.R.string.adreno_turbo_enabled else com.winlator.cmod.R.string.adreno_turbo_disabled))
                            } else {
                                toast(com.winlator.cmod.R.string.turbo_unavailable)
                            }
                        } catch (e: Exception) {
                            toast("Turbo: ${e.message}")
                        }
                    }
                )
            }

            // 8. Gyro
            SectionHeader(stringResource(com.winlator.cmod.R.string.gyro_settings), Icons.Filled.RotateRight)
            SettingsCard {
                SettingsCheckRow(
                    icon = Icons.Filled.GpsFixed,
                    title = stringResource(com.winlator.cmod.R.string.enable_gyroscope),
                    checked = gyroEnabled,
                    onCheckedChange = { gyroEnabled = it; saveBool("gyro_enabled", it) }
                )
                SettingsDivider()
                SettingsClickRow(
                    icon = Icons.Filled.Gamepad,
                    title = stringResource(com.winlator.cmod.R.string.gyro_activation_button),
                    subtitle = gyroButtonLabel(gyroTriggerButton),
                    onClick = { showGyroButtonDialog = true }
                )
                SettingsDivider()
                SettingsClickRow(
                    icon = Icons.Filled.ToggleOn,
                    title = stringResource(com.winlator.cmod.R.string.gyro_mode),
                    subtitle = gyroModeLabel(gyroMode),
                    onClick = { showGyroModeDialog = true }
                )
                SettingsDivider()
                SettingsButtonRow(
                    icon = Icons.Filled.Tune,
                    title = stringResource(com.winlator.cmod.R.string.gyro_calibration),
                    onClick = { toast(com.winlator.cmod.R.string.calibration_hint) }
                )
            }

            // 9. Логи
            SectionHeader(stringResource(com.winlator.cmod.R.string.logs), Icons.Filled.BugReport)
            SettingsCard {
                SettingsCheckRow(
                    icon = Icons.Filled.Code,
                    title = stringResource(com.winlator.cmod.R.string.wine_debug),
                    checked = enableWineDebug,
                    onCheckedChange = { enableWineDebug = it; saveBool("enable_wine_debug", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.Description,
                    title = stringResource(com.winlator.cmod.R.string.box86_64_logs),
                    checked = enableBoxLogs,
                    onCheckedChange = { enableBoxLogs = it; saveBool("enable_box86_64_logs", it) }
                )
            }

            // 10. Game Controller
            SectionHeader(stringResource(com.winlator.cmod.R.string.game_controller), Icons.Filled.Gamepad)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Tune,
                    title = stringResource(com.winlator.cmod.R.string.trigger_type),
                    subtitle = trigLabel(triggerType),
                    onClick = { showTriggerDialog = true }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.History,
                    title = stringResource(com.winlator.cmod.R.string.legacy_input_mode),
                    checked = legacyMode,
                    onCheckedChange = { legacyMode = it; saveBool("legacy_mode_enabled", it) }
                )
                SettingsDivider()
                SettingsButtonRow(
                    icon = Icons.Filled.Tune,
                    title = stringResource(com.winlator.cmod.R.string.configure_analog_sticks),
                    onClick = { toast(com.winlator.cmod.R.string.sticks_hint) }
                )
            }

            // Звук (после Game Controller)
            SectionHeader(stringResource(com.winlator.cmod.R.string.sound_audio), Icons.Filled.MusicNote)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.LibraryMusic,
                    title = stringResource(com.winlator.cmod.R.string.midi_soundfont),
                    subtitle = stringResource(com.winlator.cmod.R.string.default_soundfont),
                    onClick = { toast(com.winlator.cmod.R.string.select_soundfont_hint) }
                )
                SettingsDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill(
                        onClick = { soundFontLauncher.launch(arrayOf("*/*")) },
                        Modifier.weight(1f),
                    ) { Text("Установить") }
                    GlassPill(
                        onClick = {
                        try {
                            val removed = com.winlator.cmod.midi.MidiManager.removeSF2File(ctx, "Default")
                            toast(if (removed) com.winlator.cmod.R.string.sound_font_removed_success else com.winlator.cmod.R.string.sound_font_removed_failed)
                        } catch (e: Exception) { toast(com.winlator.cmod.R.string.remove_error) }
                    },
                        Modifier.weight(1f),
                    ) { Text("Удалить") }
                }
            }

            // 11. Experimental
            SectionHeader(stringResource(com.winlator.cmod.R.string.experimental), Icons.Filled.Science)
            SettingsCard {
                SettingsCheckRow(
                    icon = Icons.Filled.FolderShared,
                    title = stringResource(com.winlator.cmod.R.string.file_provider),
                    checked = enableFileProvider,
                    onCheckedChange = {
                        enableFileProvider = it
                        saveBool("enable_file_provider", it)
                        toast(com.winlator.cmod.R.string.take_effect_next_startup)
                    }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.OpenInBrowser,
                    title = stringResource(com.winlator.cmod.R.string.open_with_browser),
                    checked = openWithBrowser,
                    onCheckedChange = { openWithBrowser = it; saveBool("open_with_android_browser", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.ContentPaste,
                    title = stringResource(com.winlator.cmod.R.string.share_clipboard),
                    checked = shareClipboard,
                    onCheckedChange = { shareClipboard = it; saveBool("share_android_clipboard", it) }
                )
            }

            // 12. ImageFs
            SectionHeader(stringResource(com.winlator.cmod.R.string.imagefs), Icons.Filled.Storage)
            SettingsCard {
                SettingsButtonRow(
                    icon = Icons.Filled.Build,
                    title = stringResource(com.winlator.cmod.R.string.reinstall_imagefs),
                    onClick = { showReinstallConfirm = true }
                )
                SettingsDivider()
                SettingsButtonRow(
                    icon = Icons.Filled.CloudUpload,
                    title = stringResource(com.winlator.cmod.R.string.backup_data),
                    onClick = { showBackupConfirm = true }
                )
                SettingsDivider()
                SettingsButtonRow(
                    icon = Icons.Filled.Restore,
                    title = stringResource(com.winlator.cmod.R.string.restore_data),
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) }
                )
            }

            // 13. Updates
            SectionHeader(stringResource(com.winlator.cmod.R.string.update_section), Icons.Filled.SystemUpdate)
            SettingsCard {
                SettingsButtonRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = stringResource(com.winlator.cmod.R.string.update_check),
                    onClick = {
                        showUpdateCheckDialog = true
                        updateChecking = true
                        updateError = false
                        updateInfo = null
                        updateFile = null
                        updateDownloading = false
                        updateProgress = 0
                        com.winlator.cmod.core.UpdateManager.check(ctx) { info ->
                            (ctx as? android.app.Activity)?.runOnUiThread {
                                updateChecking = false
                                if (info == null) {
                                    updateError = true
                                } else {
                                    updateInfo = info
                                }
                            }
                        }
                    }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(com.winlator.cmod.R.string.update_notify),
                    checked = updateNotify,
                    onCheckedChange = {
                        updateNotify = it
                        com.winlator.cmod.core.UpdateManager.setNotifyEnabled(ctx, it)
                    }
                )
            }

            Spacer(Modifier.height(88.dp))
        }

        // FAB - подтверждение сохранения (как BTConfirm в оригинале)
        FloatingActionButton(
            onClick = { onConfirmSave() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 98.dp),
            // В Material3 FAB по умолчанию берёт shapes.large, то есть 26dp —
            // плавающая кнопка выглядела скруглённым квадратом.
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Filled.Check, "Save", tint = MaterialTheme.colorScheme.onPrimary) }
    }

    // Подменю «Кастомизация» поверх основного экрана
    if (showCustomization) {
        CustomizationScreen(
            preferences = prefs,
            onBack = { showCustomization = false }
        )
    }

    // ---- Диалоги ----
    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(com.winlator.cmod.R.string.language),
            options = languageOptions,
            selected = appLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { value ->
                appLanguage = value
                saveString("app_language", value)
                onLanguageChange(value)
                showLanguageDialog = false
            }
        )
    }
    if (showAnimDialog) {
        ChoiceDialog(
            title = stringResource(com.winlator.cmod.R.string.transition_animation),
            options = animOptions,
            selected = transitionAnim,
            onDismiss = { showAnimDialog = false },
            onSelect = { value ->
                transitionAnim = value
                saveString("transition_animation", value)
                onTransitionAnimationChange(value)
                showAnimDialog = false
            }
        )
    }
    if (showTriggerDialog) {
        ChoiceDialog(
            title = stringResource(com.winlator.cmod.R.string.trigger_type),
            options = triggerOptions,
            selected = triggerType,
            onDismiss = { showTriggerDialog = false },
            onSelect = { value ->
                triggerType = value
                saveInt("trigger_type", value)
                showTriggerDialog = false
            }
        )
    }
    if (showGyroButtonDialog) {
        ChoiceDialog(
            title = "Кнопка активации гироскопа",
            options = gyroButtonOptions,
            selected = gyroTriggerButton,
            onDismiss = { showGyroButtonDialog = false },
            onSelect = { value ->
                gyroTriggerButton = value
                saveInt("gyro_trigger_button", value)
                showGyroButtonDialog = false
            }
        )
    }
    if (showGyroModeDialog) {
        ChoiceDialog(
            title = "Режим гироскопа",
            options = gyroModeOptions,
            selected = gyroMode,
            onDismiss = { showGyroModeDialog = false },
            onSelect = { value ->
                gyroMode = value
                saveInt("gyro_mode", value)
                showGyroModeDialog = false
            }
        )
    }
    if (showCursorSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showCursorSpeedDialog = false },
            title = { Text("Скорость курсора: $cursorSpeedSlider%") },
            text = {
                Column(Modifier.padding(8.dp)) {
                    Slider(
                        value = cursorSpeedSlider.toFloat(),
                        onValueChange = { cursorSpeedSlider = it.toInt() },
                        valueRange = 10f..200f,
                        steps = 18
                    ,
                        colors = glassSliderColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cursorSpeed = cursorSpeedSlider / 100f
                    saveFloat("cursor_speed", cursorSpeed)
                    showCursorSpeedDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showCursorSpeedDialog = false }) { Text("Отмена") } }
        )
    }
    if (showReinstallConfirm) {
        AlertDialog(
            onDismissRequest = { showReinstallConfirm = false },
            title = { Text(stringResource(com.winlator.cmod.R.string.reinstall_imagefs_label)) },
            text = { Text(ctx.getString(com.winlator.cmod.R.string.do_you_want_to_reinstall_imagefs)) },
            confirmButton = {
                TextButton(onClick = {
                    showReinstallConfirm = false
                    onReinstallImageFs()
                }) { Text(stringResource(com.winlator.cmod.R.string.yes)) }
            },
            dismissButton = { TextButton(onClick = { showReinstallConfirm = false }) { Text(stringResource(com.winlator.cmod.R.string.no)) } }
        )
    }
    if (showBackupConfirm) {
        AlertDialog(
            onDismissRequest = { showBackupConfirm = false },
            title = { Text("Backup Data") },
            text = { Text("Создать резервную копию данных приложения?") },
            confirmButton = {
                TextButton(onClick = {
                    showBackupConfirm = false
                    try {
                        val dataDir = ctx.filesDir.parentFile
                        val backupFile = java.io.File(android.os.Environment.getExternalStorageDirectory(), "app_data_backup.tar")
                        val preloader = com.winlator.cmod.core.PreloaderDialog(ctx as android.app.Activity)
                        preloader.showOnUiThread(com.winlator.cmod.R.string.backing_up_data)
                        val executor = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
                        executor.execute {
                            try {
                                com.winlator.cmod.core.TarCompressorUtils.archive(arrayOf(dataDir), backupFile) { file ->
                                    !file.absolutePath.contains("imagefs/tmp/.sysvshm")
                                }
                                (ctx as android.app.Activity).runOnUiThread {
                                    preloader.closeOnUiThread()
                                    toast("Backup completed: ${backupFile.path}")
                                }
                            } catch (e: Exception) {
                                (ctx as android.app.Activity).runOnUiThread {
                                    preloader.closeOnUiThread()
                                    toast("Backup failed.")
                                }
                            }
                        }
                    } catch (e: Exception) { toast("Backup: ${e.message}") }
                }) { Text("Да") }
            },
            dismissButton = { TextButton(onClick = { showBackupConfirm = false }) { Text("Нет") } }
        )
    }

    // ---- Диалог апдейтера ----
    UpdateCheckDialog(
        show = showUpdateCheckDialog,
        checking = updateChecking,
        error = updateError,
        info = updateInfo,
        downloading = updateDownloading,
        progress = updateProgress,
        file = updateFile,
        onDismiss = { showUpdateCheckDialog = false },
        setDownloading = { updateDownloading = it },
        setProgress = { updateProgress = it },
        setFile = { updateFile = it },
    )
}

// ---- Вспомогательные Composable ----

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        Modifier.padding(top = 18.dp, bottom = 10.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, color = glassTitleColor(), fontSize = GlassTokens.SectionSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun SettingsClickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).glassGroupDivider().padding(horizontal = GlassTokens.RowPaddingH, vertical = GlassTokens.RowPaddingV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlassChevron()
    }
}

@Composable
private fun SettingsCheckRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.glassGroupDivider().padding(horizontal = GlassTokens.RowPaddingH, vertical = GlassTokens.RowPaddingV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsButtonRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).glassGroupDivider().padding(horizontal = GlassTokens.RowPaddingH, vertical = GlassTokens.RowPaddingV + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        GlassChevron()
    }
}

@Composable
private fun SettingsDivider() {
    // Разделитель рисует сама строка внутри GlassSurface (glassGroupDivider),
    // явная линия здесь дублировалась бы.
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val maxListHeight = with(androidx.compose.ui.platform.LocalConfiguration.current) {
                (screenHeightDp * 0.6f).dp
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = maxListHeight)) {
                items(options.size) { index ->
                    val (value, label) = options[index]
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
