package com.winlator.cmod.ui.screens

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.contentdialog.DebugDialog
import com.winlator.cmod.core.Callback
import com.winlator.cmod.core.CPUStatus
import com.winlator.cmod.core.KeyValueSet
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.core.ProcessHelper
import com.winlator.cmod.core.SensorReader
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.inputcontrols.AccentTheme
import com.winlator.cmod.inputcontrols.ControlsProfile
import com.winlator.cmod.inputcontrols.VisualStyle
import com.winlator.cmod.renderer.EffectComposer
import com.winlator.cmod.widget.FpsCounterConfig
import com.winlator.cmod.winhandler.OnGetProcessInfoListener
import com.winlator.cmod.winhandler.ProcessInfo
import com.winlator.cmod.xserver.Window
import com.winlator.cmod.xserver.XServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- INPUT ----------

@Composable
fun XPanelInput(activity: XServerDisplayActivity, onDismiss: () -> Unit) {
    val manager = remember { activity.inputControlsManager }
    val preferences = remember { MmkvPreferences() }
    val profiles = remember { manager.getProfiles(true) ?: ArrayList<ControlsProfile>() }
    val profileItems = remember(profiles) {
        val list = mutableListOf("-- Disabled --")
        profiles.forEach { list.add(it.name) }
        list
    }
    val currentProfile = remember { activity.inputControlsView?.profile }
    var selectedProfileIdx by remember {
        mutableStateOf(run {
            val found = profiles.indexOfFirst { currentProfile != null && it.id == currentProfile.id }
            if (found >= 0) found + 1 else 0
        })
    }
    var showTouchscreen by remember { mutableStateOf(activity.inputControlsView?.isShowTouchscreenControls() ?: true) }
    var enableTimeout by remember { mutableStateOf(preferences.getBoolean("touchscreen_timeout_enabled", false)) }
    var enableHaptics by remember { mutableStateOf(preferences.getBoolean("touchscreen_haptics_enabled", false)) }
    var enableGyro by remember { mutableStateOf(preferences.getBoolean("gyro_enabled", false)) }
    var gyroSensitivity by remember { mutableFloatStateOf(preferences.getFloat("gyro_sensitivity", 1.0f)) }
    var enableQuickAccess by remember { mutableStateOf(preferences.getBoolean("quick_access_panel_enabled", false)) }
    var relativeMouse by remember { mutableStateOf(preferences.getBoolean("relative_mouse_movement", false)) }

    GamePanelShell(
        footer = {
            GameFooterOkCancel(
                onCancel = onDismiss,
                onOk = {
                    val profileId = if (selectedProfileIdx > 0) profiles[selectedProfileIdx - 1].id else 0
                    activity.applyInputControlsSettings(
                        showTouchscreen, enableTimeout, enableHaptics, enableGyro,
                        gyroSensitivity, enableQuickAccess, relativeMouse, profileId
                    )
                    onDismiss()
                }
            )
        }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            GameSectionTitle(stringResource(R.string.profile))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SpinnerRow(
                        label = "",
                        entries = profileItems,
                        selected = profileItems[selectedProfileIdx],
                        onSelected = { selectedProfileIdx = profileItems.indexOf(it).coerceAtLeast(0) }
                    )
                }
                IconButton(onClick = {
                    val position = selectedProfileIdx
                    val profileId = if (position > 0) profiles[position - 1].id else 0
                    activity.launchInputControlsEditor(profileId) { manager.loadProfiles(true) }
                    onDismiss()
                }) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.edit), tint = GameOverlayColors.Accent)
                }
            }
            val selectedProfile = if (selectedProfileIdx > 0) profiles[selectedProfileIdx - 1] else null
            if (selectedProfile != null) {
                val styleItems = remember { VisualStyle.displayNames().toList() }
                var styleCode by remember(selectedProfile.id) { mutableIntStateOf(selectedProfile.visualStyle) }
                var accentCode by remember(selectedProfile.id) { mutableIntStateOf(selectedProfile.accentTheme) }
                fun applyStyle() {
                    selectedProfile.visualStyle = styleCode
                    selectedProfile.accentTheme = accentCode
                    selectedProfile.save()
                    val view = activity.inputControlsView ?: return
                    if (view.profile?.id == selectedProfile.id) {
                        view.setVisualStyle(VisualStyle.fromCode(styleCode))
                        view.setAccentTheme(AccentTheme.fromCode(accentCode))
                    }
                }
                GameSectionTitle(stringResource(R.string.button_style))
                SpinnerRow(
                    label = "",
                    entries = styleItems,
                    selected = styleItems[styleCode.coerceIn(0, styleItems.size - 1)],
                    onSelected = { name ->
                        val idx = styleItems.indexOf(name)
                        if (idx >= 0) {
                            styleCode = idx
                            applyStyle()
                        }
                    }
                )
                Text(
                    stringResource(R.string.accent_color),
                    color = GameOverlayColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccentTheme.values().forEach { theme ->
                        val isSelected = theme.code == accentCode
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .background(Color(theme.accent))
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                    CircleShape
                                )
                                .clickable {
                                    accentCode = theme.code
                                    applyStyle()
                                }
                        )
                    }
                }
                if (styleCode == VisualStyle.GAMEHUB.code) {
                    Text(
                        "Glass рисует собственные цвета — акцент на него не влияет",
                        color = GameOverlayColors.TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            GameSwitchRow(stringResource(R.string.show_touchscreen_controls), showTouchscreen) { showTouchscreen = it }
            GameSwitchRow(stringResource(R.string.enable_touchscreen_timeout), enableTimeout) { enableTimeout = it }
            GameSwitchRow(stringResource(R.string.enable_touchscreen_haptics), enableHaptics) { enableHaptics = it }
            GameSwitchRow(stringResource(R.string.enable_gyroscope_control), enableGyro) { enableGyro = it }
            if (enableGyro) {
                GameSliderRow(
                    label = stringResource(R.string.gyroscope_sensitivity),
                    valueText = String.format(Locale.US, "%.1f", gyroSensitivity),
                    value = gyroSensitivity, onValueChange = { gyroSensitivity = it },
                    valueRange = 0.1f..5.0f
                )
            }
            GameSwitchRow(stringResource(R.string.enable_quick_access_panel), enableQuickAccess) { enableQuickAccess = it }
            GameSwitchRow(stringResource(R.string.relative_mouse_movement), relativeMouse) { relativeMouse = it }
        }
    }
}

// ---------- FRAME GENERATION ----------

@Composable
fun XPanelFrameGen(activity: XServerDisplayActivity, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val container = activity.getContainer()
    if (container == null) { onDismiss(); return }
    if (!com.winlator.cmod.core.LsfgNative.isDllAvailable(activity)) {
        GamePanelShell {
            Text(stringResource(R.string.lsfg_not_in_library), color = GameOverlayColors.TextPrimary, fontSize = 13.sp)
        }
        return
    }
    var multiplier by remember { mutableStateOf(activity.getLastFgMult()) }
    var flowScale by remember { mutableFloatStateOf(container.getFrameGenFlowScale()) }
    var targetRate by remember { mutableStateOf(container.getFrameGenTargetRate()) }
    var readout by remember { mutableStateOf(activity.getFgReadout()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            readout = activity.getFgReadout()
        }
    }
    val targetOpts = listOf("Fixed", "60", "90", "120", "144", "165")
    val targetVals = listOf(0, 60, 90, 120, 144, 165)
    val multOpts = listOf("Off", "2x", "3x", "4x")
    val multVals = listOf(0, 2, 3, 4)

    GamePanelShell(
        footer = {
            GameFooterOkCancel(
                onCancel = onDismiss,
                onOk = {
                    if (multiplier > 0) container.setFrameGenMultiplier(multiplier)
                    container.setFrameGenFlowScale(flowScale)
                    container.setFrameGenTargetRate(targetRate)
                    container.setFrameGenEngine(if (multiplier > 0) "lsfg-native" else "off")
                    container.saveData()
                    activity.prepareLsfgNative()
                    activity.applyLsfgNative(if (multiplier > 0) multiplier else 0, flowScale)
                    Toast.makeText(ctx, ctx.getString(R.string.lsfg_applied, if (multiplier > 0) "${multiplier}x" else "Off"), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )
        }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (readout.isNotEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(GameCardShape)
                        .background(GameOverlayColors.CardBg).padding(10.dp)
                ) {
                    Text(readout, color = GameOverlayColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
            }
            GameSectionTitle(stringResource(R.string.lsfg_target))
            GameChipGroup(
                options = targetOpts,
                selectedIndex = targetVals.indexOf(targetRate).coerceAtLeast(0),
                onSelect = { targetRate = targetVals[it] }
            )
            Spacer(Modifier.height(4.dp))
            GameSectionTitle(stringResource(R.string.lsfg_multiplier))
            GameChipGroup(
                options = multOpts,
                selectedIndex = multVals.indexOf(multiplier).coerceAtLeast(0),
                onSelect = { multiplier = multVals[it]; targetRate = 0 }
            )
            if (targetRate != 0) {
                Text(stringResource(R.string.lsfg_mult_fixed_note), color = GameOverlayColors.TextSecondary, fontSize = 11.sp)
            }
            GameSliderRow(
                label = stringResource(R.string.lsfg_flow_scale),
                valueText = String.format(Locale.US, "%.2f", flowScale),
                value = flowScale, onValueChange = { flowScale = it },
                valueRange = 0.25f..1.0f, steps = 14
            )
        }
    }
}

// ---------- SCREEN EFFECTS ----------

@Composable
fun XPanelEffects(activity: XServerDisplayActivity, onDismiss: () -> Unit) {
    val preferences = remember { MmkvPreferences() }
    var brightness by remember { mutableFloatStateOf(preferences.getFloat("effect_brightness", 0f)) }
    var contrast by remember { mutableFloatStateOf(preferences.getFloat("effect_contrast", 0f)) }
    var gamma by remember { mutableFloatStateOf(preferences.getFloat("effect_gamma", 1.0f)) }
    var enableFXAA by remember { mutableStateOf(preferences.getBoolean("effect_fxaa", false)) }
    var enableCRT by remember { mutableStateOf(preferences.getBoolean("effect_crt", false)) }
    var enableToon by remember { mutableStateOf(preferences.getBoolean("effect_toon", false)) }
    var enableNTSC by remember { mutableStateOf(preferences.getBoolean("effect_ntsc", false)) }
    var enableVignette by remember { mutableStateOf(preferences.getBoolean("effect_vignette", false)) }
    var enableSepia by remember { mutableStateOf(preferences.getBoolean("effect_sepia", false)) }
    var enableBlur by remember { mutableStateOf(preferences.getBoolean("effect_blur", false)) }
    var enablePixelate by remember { mutableStateOf(preferences.getBoolean("effect_pixelate", false)) }
    var enableGrayscale by remember { mutableStateOf(preferences.getBoolean("effect_grayscale", false)) }
    var enableSharpen by remember { mutableStateOf(preferences.getBoolean("effect_sharpen", false)) }
    var enableSmooth by remember { mutableStateOf(preferences.getBoolean("effect_smooth", false)) }
    var enableHDR by remember { mutableStateOf(preferences.getBoolean("effect_hdr", false)) }
    var selectedProfile by remember { mutableStateOf(activity.screenEffectProfile ?: "") }
    var profileList by remember {
        mutableStateOf(run {
            val list = mutableListOf("-- Default Profile --")
            val ps = preferences.getStringSet("screen_effect_profiles", mutableSetOf()) ?: emptySet()
            ps.forEach { list.add(it.split(":")[0]) }
            list.toList()
        })
    }
    var showAddProfileDialog by remember { mutableStateOf(false) }

    fun loadProfile(name: String) {
        val ps = preferences.getStringSet("screen_effect_profiles", mutableSetOf()) ?: emptySet()
        val found = ps.firstOrNull { it.split(":")[0] == name }
        if (found != null && found.contains(":")) {
            val s = KeyValueSet(found.split(":")[1])
            brightness = s.getFloat("brightness", 0f)
            contrast = s.getFloat("contrast", 0f)
            gamma = s.getFloat("gamma", 1.0f)
            enableFXAA = s.getBoolean("fxaa", false)
            enableCRT = s.getBoolean("crt_shader", false)
            enableToon = s.getBoolean("toon_shader", false)
            enableNTSC = s.getBoolean("ntsc_effect", false)
            enableVignette = s.getBoolean("vignette_effect", false)
            enableSepia = s.getBoolean("sepia_effect", false)
            enableBlur = s.getBoolean("blur_effect", false)
            enablePixelate = s.getBoolean("pixelate_effect", false)
            enableGrayscale = s.getBoolean("grayscale_effect", false)
            enableSharpen = s.getBoolean("sharpen_effect", false)
            enableSmooth = s.getBoolean("smooth_effect", false)
            enableHDR = s.getBoolean("hdr_effect", false)
        }
    }
    fun resetSettings() {
        brightness = 0f; contrast = 0f; gamma = 1.0f
        enableFXAA = false; enableCRT = false; enableToon = false; enableNTSC = false
        enableVignette = false; enableSepia = false; enableBlur = false; enablePixelate = false
        enableGrayscale = false; enableSharpen = false; enableSmooth = false; enableHDR = false
    }
    fun applyVulkanEffects() {
        val renderer = activity.xServerView?.renderer ?: return
        val isVulkan = renderer is com.winlator.cmod.renderer.VulkanRenderer
        preferences.edit()
            .putFloat("effect_brightness", brightness).putFloat("effect_contrast", contrast)
            .putFloat("effect_gamma", gamma).putBoolean("effect_fxaa", enableFXAA)
            .putBoolean("effect_crt", enableCRT).putBoolean("effect_toon", enableToon)
            .putBoolean("effect_ntsc", enableNTSC).putBoolean("effect_vignette", enableVignette)
            .putBoolean("effect_sepia", enableSepia).putBoolean("effect_blur", enableBlur)
            .putBoolean("effect_pixelate", enablePixelate).putBoolean("effect_grayscale", enableGrayscale)
            .putBoolean("effect_sharpen", enableSharpen).putBoolean("effect_smooth", enableSmooth)
            .putBoolean("effect_hdr", enableHDR).apply()
        val types = ArrayList<Int>(); val paramsList = ArrayList<FloatArray>()
        if (brightness != 0f || contrast != 0f || gamma != 1.0f) {
            types.add(EffectComposer.EFFECT_COLOR)
            paramsList.add(floatArrayOf(brightness / 100f, contrast / 100f, gamma, 0f, 0f, 0f, 0f, 0f))
        }
        if (enableHDR) { types.add(EffectComposer.EFFECT_HDR); paramsList.add(floatArrayOf(0.4f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)) }
        if (enableFXAA) { types.add(EffectComposer.EFFECT_FXAA); paramsList.add(FloatArray(8)) }
        if (enableCRT) { types.add(EffectComposer.EFFECT_CRT); paramsList.add(FloatArray(8)) }
        if (enableToon) { types.add(EffectComposer.EFFECT_TOON); paramsList.add(FloatArray(8)) }
        if (enableNTSC) {
            types.add(EffectComposer.EFFECT_NTSC)
            paramsList.add(floatArrayOf(0f, renderer.surfaceWidth.toFloat(), renderer.surfaceHeight.toFloat(), 0f, 0f, 0f, 0f, 0f))
        }
        if (enableVignette) { types.add(EffectComposer.EFFECT_VIGNETTE); paramsList.add(floatArrayOf(0.5f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f)) }
        if (enableSepia) { types.add(EffectComposer.EFFECT_SEPIA); paramsList.add(floatArrayOf(1.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)) }
        if (enableBlur) { types.add(EffectComposer.EFFECT_BLUR); paramsList.add(floatArrayOf(2.0f, 5.0f, 0f, 0f, 0f, 0f, 0f, 0f)) }
        if (enablePixelate) { types.add(EffectComposer.EFFECT_PIXELATE); paramsList.add(floatArrayOf(4.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)) }
        if (enableGrayscale) { types.add(EffectComposer.EFFECT_GRAYSCALE); paramsList.add(floatArrayOf(1.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)) }
        if (enableSharpen) { types.add(EffectComposer.EFFECT_SHARPEN); paramsList.add(floatArrayOf(1.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)) }
        if (enableSmooth) { types.add(EffectComposer.EFFECT_SMOOTH); paramsList.add(floatArrayOf(1.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)) }
        if (types.isEmpty()) renderer.clearEffects()
        else {
            if (isVulkan) (renderer as com.winlator.cmod.renderer.VulkanRenderer).disableScanoutForEffects()
            renderer.setEffects(types.toIntArray(), paramsList.toTypedArray())
        }
        if (selectedProfile.isNotEmpty() && selectedProfile != "-- Default Profile --") {
            val old = preferences.getStringSet("screen_effect_profiles", mutableSetOf()) ?: emptySet()
            val np = mutableSetOf<String>()
            val s = KeyValueSet()
            s.put("brightness", brightness); s.put("contrast", contrast); s.put("gamma", gamma)
            s.put("fxaa", enableFXAA); s.put("crt_shader", enableCRT); s.put("toon_shader", enableToon)
            s.put("ntsc_effect", enableNTSC); s.put("vignette_effect", enableVignette)
            s.put("sepia_effect", enableSepia); s.put("blur_effect", enableBlur)
            s.put("pixelate_effect", enablePixelate); s.put("grayscale_effect", enableGrayscale)
            s.put("sharpen_effect", enableSharpen); s.put("smooth_effect", enableSmooth); s.put("hdr_effect", enableHDR)
            old.forEach { if (it.split(":")[0] == selectedProfile) np.add("$selectedProfile:$s") else np.add(it) }
            preferences.edit().putStringSet("screen_effect_profiles", np).apply()
            activity.screenEffectProfile = selectedProfile
        }
    }

    GamePanelShell(
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { resetSettings() }) {
                    Text(stringResource(R.string.reset), color = GameOverlayColors.Danger)
                }
                GameFooterOkCancel(onCancel = onDismiss, onOk = { applyVulkanEffects(); onDismiss() })
            }
        }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            GameSectionTitle(stringResource(R.string.color_adjustment))
            GameSliderRow(stringResource(R.string.brightness), "${brightness.toInt()}", brightness, { brightness = it }, -50f..50f)
            GameSliderRow(stringResource(R.string.contrast), "${contrast.toInt()}", contrast, { contrast = it }, -100f..100f)
            GameSliderRow(stringResource(R.string.gamma), String.format(Locale.US, "%.2f", gamma), gamma, { gamma = it }, 0.5f..3.0f)
            GameSectionTitle(stringResource(R.string.profile))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                var dd by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { dd = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameOverlayColors.TextPrimary)
                    ) { Text(if (selectedProfile.isEmpty()) "-- Default Profile --" else selectedProfile, fontSize = 12.sp, maxLines = 1) }
                    DropdownMenu(expanded = dd, onDismissRequest = { dd = false }) {
                        profileList.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedProfile = if (name == "-- Default Profile --") "" else name
                                    if (selectedProfile.isNotEmpty()) loadProfile(selectedProfile)
                                    dd = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { showAddProfileDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Add, null, tint = GameOverlayColors.Accent, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {
                    if (selectedProfile.isNotEmpty()) {
                        val old = preferences.getStringSet("screen_effect_profiles", mutableSetOf()) ?: emptySet()
                        preferences.edit().putStringSet("screen_effect_profiles", old.filter { it.split(":")[0] != selectedProfile }.toSet()).apply()
                        profileList = profileList.filter { it != selectedProfile }
                        selectedProfile = ""; resetSettings()
                    }
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = GameOverlayColors.Danger, modifier = Modifier.size(18.dp))
                }
            }
            GameSectionTitle("FX / Shaders")
            GameCheckRow(stringResource(R.string.enable_fxaa), enableFXAA) { enableFXAA = it }
            GameCheckRow(stringResource(R.string.enable_crt_shader), enableCRT) { enableCRT = it }
            GameCheckRow(stringResource(R.string.enable_toon_shader), enableToon) { enableToon = it }
            GameCheckRow(stringResource(R.string.enable_ntsc_effect), enableNTSC) { enableNTSC = it }
            GameCheckRow(stringResource(R.string.enable_vignette_effect), enableVignette) { enableVignette = it }
            GameCheckRow(stringResource(R.string.enable_sepia_effect), enableSepia) { enableSepia = it }
            GameCheckRow(stringResource(R.string.enable_blur_effect), enableBlur) { enableBlur = it }
            GameCheckRow(stringResource(R.string.enable_pixelate_effect), enablePixelate) { enablePixelate = it }
            GameCheckRow(stringResource(R.string.enable_grayscale_effect), enableGrayscale) { enableGrayscale = it }
            GameCheckRow(stringResource(R.string.enable_sharpen_effect), enableSharpen) { enableSharpen = it }
            GameCheckRow(stringResource(R.string.enable_smooth_effect), enableSmooth) { enableSmooth = it }
            GameCheckRow("HDR", enableHDR) { enableHDR = it }
        }
    }
    if (showAddProfileDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddProfileDialog = false },
            title = { Text(stringResource(R.string.profile)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        val old = preferences.getStringSet("screen_effect_profiles", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        old.add("$name:")
                        preferences.edit().putStringSet("screen_effect_profiles", old).apply()
                        profileList = profileList + name
                        selectedProfile = name
                    }
                    showAddProfileDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showAddProfileDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ---------- FPS ----------

@Composable
fun XPanelFps(onDismiss: () -> Unit, onConfigChanged: () -> Unit) {
    val ctx = LocalContext.current
    val config = remember { FpsCounterConfig(ctx) }
    var enabled by remember { mutableStateOf(config.isEnabled()) }
    var showFps by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.FPS)) }
    var showRam by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.RAM)) }
    var showGpu by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.GPU)) }
    var showGpuLoad by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.GPU_LOAD)) }
    var showGpuTemp by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.GPU_TEMP)) }
    var showFrameTimeGraph by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH)) }
    var showRenderer by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.RENDERER)) }
    var showCpuLoad by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.CPU_LOAD)) }
    var showCpuTemp by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.CPU_TEMP)) }
    var showBatteryTemp by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP)) }
    var showBatteryVoltage by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE)) }
    var horizontalLayout by remember { mutableStateOf(config.isHorizontalLayout()) }
    var backgroundOpacity by remember { mutableFloatStateOf(config.getBackgroundOpacity().toFloat()) }
    var counterScale by remember { mutableFloatStateOf(config.getCounterScale().toFloat().coerceAtLeast(60f)) }
    var fpsLimit by remember { mutableFloatStateOf(config.getFpsLimit().toFloat()) }
    var counterStyle by remember { mutableIntStateOf(config.getCounterStyle()) }
    var whiteFonts by remember { mutableStateOf(config.isWhiteFonts()) }

    GamePanelShell(
        footer = {
            GameFooterOkCancel(
                onCancel = onDismiss,
                onOk = {
                    config.setEnabled(enabled)
                    config.setModuleVisible(FpsCounterConfig.Module.FPS, showFps)
                    config.setModuleVisible(FpsCounterConfig.Module.RAM, showRam)
                    config.setModuleVisible(FpsCounterConfig.Module.GPU, showGpu)
                    config.setModuleVisible(FpsCounterConfig.Module.GPU_LOAD, showGpuLoad)
                    config.setModuleVisible(FpsCounterConfig.Module.GPU_TEMP, showGpuTemp)
                    config.setModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH, showFrameTimeGraph)
                    config.setModuleVisible(FpsCounterConfig.Module.RENDERER, showRenderer)
                    config.setModuleVisible(FpsCounterConfig.Module.CPU_LOAD, showCpuLoad)
                    config.setModuleVisible(FpsCounterConfig.Module.CPU_TEMP, showCpuTemp)
                    config.setModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP, showBatteryTemp)
                    config.setModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE, showBatteryVoltage)
                    config.setHorizontalLayout(horizontalLayout)
                    config.setBackgroundOpacity(backgroundOpacity.toInt())
                    config.setCounterScale(counterScale.toInt().coerceAtLeast(60))
                    config.setFpsLimit(fpsLimit.toInt())
                    config.setCounterStyle(counterStyle)
                    config.setWhiteFonts(whiteFonts)
                    onConfigChanged()
                    onDismiss()
                }
            )
        }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            GameSwitchRow(stringResource(R.string.fps_counter_enabled), enabled) { enabled = it }
            GameSliderRow(
                label = if (fpsLimit == 0f) stringResource(R.string.fps_counter_no_limit) else "${fpsLimit.toInt()}",
                valueText = "", value = fpsLimit, onValueChange = { fpsLimit = it },
                valueRange = 0f..240f, steps = 23
            )
            GameSectionTitle(stringResource(R.string.fps_counter_modules_title))
            GameCheckRow(stringResource(R.string.fps_counter_show_fps), showFps) { showFps = it }
            GameCheckRow(stringResource(R.string.fps_counter_show_ram), showRam) { showRam = it }
            GameCheckRow(stringResource(R.string.fps_counter_show_gpu), showGpu) { showGpu = it }
            GameCheckRow(stringResource(R.string.fps_counter_show_gpu_load), showGpuLoad) { showGpuLoad = it }
            GameCheckRow(stringResource(R.string.fps_counter_show_gpu_temp), showGpuTemp) { showGpuTemp = it }
            GameCheckRow(stringResource(R.string.fps_counter_show_cpu_temp), showCpuTemp) { showCpuTemp = it }
            GameCheckRow(stringResource(R.string.fps_counter_show_renderer), showRenderer) { showRenderer = it }
            GameCheckRow(stringResource(R.string.fps_counter_show_frame_time_graph), showFrameTimeGraph) { showFrameTimeGraph = it }
            GameCheckRow(stringResource(R.string.fps_counter_show_battery_temp), showBatteryTemp) { showBatteryTemp = it }
            GameCheckRow(stringResource(R.string.fps_counter_show_battery_voltage), showBatteryVoltage) { showBatteryVoltage = it }
            GameSwitchRow(stringResource(R.string.fps_counter_horizontal_layout), horizontalLayout) { horizontalLayout = it }
            GameSectionTitle(stringResource(R.string.fps_counter_background_opacity_title))
            GameSliderRow("", "${((backgroundOpacity / 255f) * 100).toInt()}%", backgroundOpacity, { backgroundOpacity = it }, 0f..255f)
            GameSectionTitle(stringResource(R.string.fps_counter_scale_title))
            GameSliderRow("", "${counterScale.toInt()}%", counterScale, { counterScale = it }, 60f..200f)
            GameSectionTitle(stringResource(R.string.fps_counter_style_title))
            val styles = listOf(
                stringResource(R.string.fps_counter_style_default),
                stringResource(R.string.fps_counter_style_cyber),
                stringResource(R.string.fps_counter_style_retro),
                stringResource(R.string.fps_counter_style_glass),
                stringResource(R.string.fps_counter_style_winlator_ludashi),
                stringResource(R.string.fps_counter_style_gamenative)
            )
            GameChipGroup(styles, counterStyle.coerceIn(styles.indices), { counterStyle = it })
            GameSwitchRow(stringResource(R.string.fps_counter_white_fonts), whiteFonts) { whiteFonts = it }
        }
    }
}

// ---------- TASK MANAGER (compact) ----------

@Composable
fun XPanelTaskManager(activity: XServerDisplayActivity, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var processes by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
    var cpuUsage by remember { mutableIntStateOf(0) }
    var coreSpeeds by remember { mutableStateOf<List<Float>>(emptyList()) }
    var usedMem by remember { mutableLongStateOf(0L) }
    var totalMem by remember { mutableLongStateOf(0L) }
    var cpuTemp by remember { mutableStateOf("N/A") }
    var batteryLevel by remember { mutableIntStateOf(-1) }
    var batteryTemp by remember { mutableFloatStateOf(-1f) }
    val sensorReader = remember { SensorReader() }

    DisposableEffect(Unit) {
        val lock = Any()
        val tmp = ArrayList<ProcessInfo>()
        val listener = object : OnGetProcessInfoListener {
            override fun onGetProcessInfo(index: Int, numProcesses: Int, processInfo: ProcessInfo) {
                (ctx as? XServerDisplayActivity)?.runOnUiThread {
                    synchronized(lock) {
                        if (index == 0) tmp.clear()
                        tmp.add(processInfo)
                        if (index == numProcesses - 1 || numProcesses == 0) processes = ArrayList(tmp)
                    }
                }
            }
        }
        activity.winHandler.setOnGetProcessInfoListener(listener)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                batteryLevel = intent.getIntExtra("level", -1)
                val t = intent.getIntExtra("temperature", -1)
                batteryTemp = if (t != -1) t / 10.0f else -1.0f
            }
        }
        ctx.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        var alive = true
        fun tick() {
            if (!alive) return
            try { synchronized(lock) { activity.winHandler.listProcesses() } } catch (_: Exception) {}
            try {
                val speeds = CPUStatus.getCurrentClockSpeeds()
                var total = 0; var max: Short = 0
                val list = mutableListOf<Float>()
                for (i in speeds.indices) {
                    val m = CPUStatus.getMaxClockSpeed(i)
                    list.add(speeds[i] / 1000.0f)
                    total += speeds[i].toInt()
                    if (m > max) max = m
                }
                coreSpeeds = list
                val avg = if (speeds.isNotEmpty()) total / speeds.size else 0
                cpuUsage = if (max > 0) Math.min(100, Math.round(avg.toFloat() / max.toFloat() * 100f)) else 0
            } catch (_: Exception) {}
            cpuTemp = sensorReader.cpuTemperature ?: "N/A"
            try {
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                totalMem = mi.totalMem
                usedMem = mi.totalMem - mi.availMem
            } catch (_: Exception) {}
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tick() }, 1000)
        }
        tick()
        onDispose {
            alive = false
            try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
            try { activity.winHandler.setOnGetProcessInfoListener(null) } catch (_: Exception) {}
        }
    }

    val memPct = if (totalMem > 0) ((usedMem.toDouble() / totalMem) * 100).toInt() else 0

    GamePanelShell(
        footer = {
            Button(
                onClick = {
                    activity.winHandler.exec("taskmgr.exe")
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GameOverlayColors.Accent, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.new_task), fontWeight = FontWeight.Bold) }
        }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                GameRing("$cpuUsage%", "CPU", cpuUsage / 100f)
                GameRing("$memPct%", "MEM", memPct / 100f, ringColor = Color(0xFF4FC3F7))
            }
            Text(
                "Temp: $cpuTemp · Bat: ${if (batteryLevel != -1) "$batteryLevel%" else "N/A"} ${if (batteryTemp != -1f) String.format(Locale.ENGLISH, "%.1fC", batteryTemp) else ""} · ${StringUtils.formatBytes(usedMem, false)}/${StringUtils.formatBytes(totalMem)}",
                color = GameOverlayColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp)
            )
            GameSectionTitle("Cores")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                coreSpeeds.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { k, s ->
                            val globalIdx = coreSpeeds.chunked(4).indexOf(row) * 4 + k
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(GameOverlayColors.CardBg).padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Column {
                                    Text("C${globalIdx + 1}", color = GameOverlayColors.TextSecondary, fontSize = 9.sp)
                                    Text(String.format(Locale.ENGLISH, "%.1fG", s), color = GameOverlayColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            GameSectionTitle("${stringResource(R.string.processes)} (${processes.size})")
            if (processes.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_items_to_display), color = GameOverlayColors.TextSecondary, fontSize = 12.sp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    processes.forEach { p ->
                        var showMenu by remember(p.pid) { mutableStateOf(false) }
                        Box(
                            Modifier.fillMaxWidth().clip(GameCardShape)
                                .background(GameOverlayColors.CardBg)
                                .clickable { showMenu = true }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (p.name ?: "?") + if (p.wow64Process) " *32" else "",
                                        color = GameOverlayColors.TextPrimary, fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium, maxLines = 1
                                    )
                                    Text(
                                        "PID ${p.pid} · ${p.formattedMemoryUsage ?: "0 B"}",
                                        color = GameOverlayColors.TextSecondary, fontSize = 10.sp, maxLines = 1
                                    )
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Bring to Front") },
                                        onClick = {
                                            showMenu = false
                                            p.name?.let { activity.winHandler.bringToFront(it) }
                                            onDismiss()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("End Process", color = Color.Red) },
                                        onClick = {
                                            showMenu = false
                                            p.name?.let { activity.winHandler.killProcess(it) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- ACTIVE WINDOWS ----------

@Composable
fun XPanelActiveWindows(activity: XServerDisplayActivity, onDismiss: () -> Unit) {
    var windows by remember { mutableStateOf<List<Window>>(emptyList()) }
    var previews by remember { mutableStateOf<Map<Long, Bitmap?>>(emptyMap()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val xServer = activity.xServer ?: return@withContext
            val list = mutableListOf<Window>()
            val seen = mutableSetOf<Long>()
            val lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)
            try {
                fun collect(w: Window) {
                    if (w.isRenderable && w != xServer.windowManager.rootWindow && !w.isDesktopWindow) {
                        val cn = w.className
                        if ((cn == null || !cn.equals("explorer.exe", ignoreCase = true)) && !seen.contains(w.handle) && w.content != null) {
                            seen.add(w.handle); list.add(w)
                        }
                    }
                    w.children?.forEach { collect(it) }
                }
                xServer.windowManager.rootWindow?.let { collect(it) }
            } finally { lock?.close() }
            windows = list.reversed()
            val map = mutableMapOf<Long, Bitmap?>()
            list.forEach { win ->
                val content = win.content
                if (!win.isIconic && content != null) {
                    val dl = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)
                    try {
                        synchronized(content.renderLock) {
                            val buf = content.data
                            if (buf != null) {
                                val pixels = IntArray(content.width * content.height)
                                buf.rewind()
                                for (j in pixels.indices) {
                                    val r = buf.get().toInt() and 0xFF
                                    val g = buf.get().toInt() and 0xFF
                                    val b = buf.get().toInt() and 0xFF
                                    val a = buf.get().toInt() and 0xFF
                                    pixels[j] = (a shl 24) or (r shl 16) or (g shl 8) or b
                                }
                                buf.rewind()
                                map[win.handle] = Bitmap.createBitmap(pixels, content.width.toInt(), content.height.toInt(), Bitmap.Config.ARGB_8888)
                            }
                        }
                    } catch (_: Exception) {} finally { dl?.close() }
                }
            }
            previews = map
        }
    }
    GamePanelShell(
    ) {
        if (windows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_items_to_display), color = GameOverlayColors.TextSecondary)
            }
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                windows.forEach { w ->
                    val title = w.name.ifEmpty { w.parent?.name ?: "" }
                    Box(
                        Modifier.fillMaxWidth().clip(GameCardShape)
                            .background(GameOverlayColors.CardBg)
                            .clickable {
                                activity.winHandler.bringToFront(w.className, w.handle)
                                onDismiss()
                            }
                            .padding(10.dp)
                    ) {
                        Column {
                            Box(
                                Modifier.fillMaxWidth().height(110.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                val bm = previews[w.handle]
                                if (bm != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bm.asImageBitmap(), contentDescription = null,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(painterResource(R.drawable.icon_window_default), null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(title.ifEmpty { w.className ?: "?" }, color = GameOverlayColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(w.className ?: "", color = GameOverlayColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------- WINETRICKS ----------

@Composable
fun XPanelWinetricks(activity: XServerDisplayActivity, onDismiss: () -> Unit) {
    var verb by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val outputSink = remember { TextView(activity) }
    val outputScroll = rememberScrollState()
    val outputHScroll = rememberScrollState()

    DisposableEffect(outputSink) {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                output = s?.toString() ?: ""
            }
        }
        outputSink.addTextChangedListener(watcher)
        onDispose { outputSink.removeTextChangedListener(watcher) }
    }

    LaunchedEffect(output.length) {
        if (output.isNotEmpty()) {
            outputScroll.scrollTo(outputScroll.maxValue)
            outputHScroll.scrollTo(0)
        }
    }

    GamePanelShell(
        footer = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Hide Winetricks", color = GameOverlayColors.TextSecondary)
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = verb,
                onValueChange = { verb = it },
                placeholder = { Text("Enter Winetricks verb...", color = GameOverlayColors.TextSecondary) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = GameOverlayColors.TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GameOverlayColors.Accent,
                    unfocusedBorderColor = GameOverlayColors.Track,
                    cursorColor = GameOverlayColors.Accent
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { activity.composeRunWinetricksStable(verb.trim(), outputSink) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GameOverlayColors.Accent,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Execute Winetricks", fontWeight = FontWeight.Bold) }
            Button(
                onClick = { activity.composeRunWinetricksFolder(outputSink) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GameOverlayColors.CardBg,
                    contentColor = GameOverlayColors.TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open Winetricks Folder") }
            Button(
                onClick = { activity.composeRestartWineserver(outputSink) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GameOverlayColors.CardBg,
                    contentColor = GameOverlayColors.TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restart Wineserver") }
            Box(
                Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.Black).padding(8.dp)
            ) {
                if (output.isEmpty()) {
                    Text(
                        "Output...",
                        color = GameOverlayColors.TextSecondary, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = output,
                            color = GameOverlayColors.TextPrimary, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(outputScroll)
                                .horizontalScroll(outputHScroll)
                        )
                    }
                }
            }
        }
    }
}

// ---------- LOGS ----------

@Composable
fun XPanelLogs(activity: XServerDisplayActivity, onDismiss: () -> Unit) {
    val lines = remember { mutableStateListOf<String>() }
    var paused by remember { mutableStateOf(DebugDialog.getPaused()) }
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    DisposableEffect(Unit) {
        val callback = object : Callback<String> {
            override fun call(line: String) {
                activity.runOnUiThread {
                    if (!DebugDialog.getPaused()) {
                        lines.add("[${timeFmt.format(Date())}]  ${line.replace("\n", "")}")
                        if (lines.size > 1000) lines.removeRange(0, lines.size - 1000)
                    }
                }
            }
        }
        // История, накопленная старым диалогом с запуска
        try {
            val history = activity.getDebugDialog()?.getLogLines()
            if (history != null) {
                lines.clear()
                val start = maxOf(0, history.size - 1000)
                for (i in start until history.size) lines.add(history[i])
            }
        } catch (_: Exception) {}
        ProcessHelper.addDebugCallback(callback)
        onDispose { ProcessHelper.removeDebugCallback(callback) }
    }

    LaunchedEffect(lines.size, paused) {
        if (lines.isNotEmpty() && !paused) listState.scrollToItem(lines.size - 1)
    }

    GamePanelShell(
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { lines.clear() }) {
                    Icon(Icons.Filled.Delete, "Clear", tint = GameOverlayColors.Accent)
                }
                IconButton(onClick = {
                    paused = !paused
                    DebugDialog.setPaused(paused)
                }) {
                    Icon(
                        if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        if (paused) "Resume" else "Pause",
                        tint = GameOverlayColors.Accent
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GameOverlayColors.Accent,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp)
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            }
        }
    ) {
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp))
                .background(Color.Black).padding(vertical = 4.dp)
        ) {
            if (lines.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_items_to_display),
                        color = GameOverlayColors.TextSecondary, fontSize = 12.sp
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(lines.size) { idx ->
                            val line = lines[idx]
                            Text(
                                text = line,
                                color = GameOverlayColors.TextPrimary, fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (idx % 2 != 0) Color(0xFF1E2A30)
                                        else Color.Transparent
                                    )
                                    .horizontalScroll(hScroll)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
