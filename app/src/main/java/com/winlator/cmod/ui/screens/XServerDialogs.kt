package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.glassTitleColor

import com.winlator.cmod.ui.components.glassAlwaysDivider
import com.winlator.cmod.ui.components.glassSliderColors
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassSwitch
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.core.KeyValueSet
import com.winlator.cmod.inputcontrols.ControlsProfile
import kotlinx.coroutines.delay
import com.winlator.cmod.renderer.EffectComposer
import com.winlator.cmod.renderer.VulkanRenderer
import com.winlator.cmod.xserver.Window
import com.winlator.cmod.xserver.XLock
import com.winlator.cmod.xserver.XServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpinnerRow(
    label: String,
    entries: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            GlassPill(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = selected)
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(text = entry) },
                        onClick = {
                            onSelected(entry)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CheckBoxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .glassAlwaysDivider()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ActiveWindowsDialogCompose(
    activity: XServerDisplayActivity,
    onDismiss: () -> Unit
) {
    var activeWindows by remember { mutableStateOf<List<Window>>(emptyList()) }
    var previews by remember { mutableStateOf<Map<Long, Bitmap?>>(emptyMap()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val xServer = activity.xServer ?: return@withContext
            val list = mutableListOf<Window>()
            val seenHandles = mutableSetOf<Long>()
            
            val lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)
            try {
                fun collect(window: Window) {
                    if (window.isRenderable && window != xServer.windowManager.rootWindow && !window.isDesktopWindow) {
                        val className = window.className
                        val handle = window.handle
                        val content = window.content
                        if ((className == null || !className.equals("explorer.exe", ignoreCase = true)) &&
                            !seenHandles.contains(handle) && content != null) {
                            seenHandles.add(handle)
                            list.add(window)
                        }
                    }
                    window.children?.forEach { collect(it) }
                }
                xServer.windowManager.rootWindow?.let { collect(it) }
            } finally {
                lock?.close()
            }

            activeWindows = list.reversed()

            val previewMap = mutableMapOf<Long, Bitmap?>()
            list.forEach { win ->
                val content = win.content
                if (!win.isIconic && content != null) {
                    val dlock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)
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
                                val bitmap = Bitmap.createBitmap(pixels, content.width.toInt(), content.height.toInt(), Bitmap.Config.ARGB_8888)
                                previewMap[win.handle] = bitmap
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        dlock?.close()
                    }
                }
            }
            previews = previewMap
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.icon_window_list), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.active_windows))
            }
        },
        text = {
            if (activeWindows.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_items_to_display))
                }
            } else {
                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeWindows) { window ->
                            val title = window.name.ifEmpty { window.parent?.name ?: "" }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activity.winHandler.bringToFront(window.className, window.handle)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                border = glassCardBorder(),
                                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(Color.Black),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val bitmap = previews[window.handle]
                                        if (window.isIconic) {
                                            Icon(
                                                painterResource(R.drawable.icon_window_default),
                                                null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Iconic", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        } else if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                painterResource(R.drawable.icon_window_default),
                                                null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    window.className?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenEffectDialogCompose(
    activity: XServerDisplayActivity,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
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
            val profiles = preferences.getStringSet("screen_effect_profiles", mutableSetOf()) ?: emptySet()
            profiles.forEach { list.add(it.split(":")[0]) }
            list.toList()
        })
    }

    fun loadProfile(profileName: String) {
        val profiles = preferences.getStringSet("screen_effect_profiles", mutableSetOf()) ?: emptySet()
        val found = profiles.firstOrNull { it.split(":")[0] == profileName }
        if (found != null && found.contains(":")) {
            val settings = KeyValueSet(found.split(":")[1])
            brightness = settings.getFloat("brightness", 0f)
            contrast = settings.getFloat("contrast", 0f)
            gamma = settings.getFloat("gamma", 1.0f)
            enableFXAA = settings.getBoolean("fxaa", false)
            enableCRT = settings.getBoolean("crt_shader", false)
            enableToon = settings.getBoolean("toon_shader", false)
            enableNTSC = settings.getBoolean("ntsc_effect", false)
            enableVignette = settings.getBoolean("vignette_effect", false)
            enableSepia = settings.getBoolean("sepia_effect", false)
            enableBlur = settings.getBoolean("blur_effect", false)
            enablePixelate = settings.getBoolean("pixelate_effect", false)
            enableGrayscale = settings.getBoolean("grayscale_effect", false)
            enableSharpen = settings.getBoolean("sharpen_effect", false)
            enableSmooth = settings.getBoolean("smooth_effect", false)
            enableHDR = settings.getBoolean("hdr_effect", false)
        }
    }

    fun resetSettings() {
        brightness = 0f
        contrast = 0f
        gamma = 1.0f
        enableFXAA = false
        enableCRT = false
        enableToon = false
        enableNTSC = false
        enableVignette = false
        enableSepia = false
        enableBlur = false
        enablePixelate = false
        enableGrayscale = false
        enableSharpen = false
        enableSmooth = false
        enableHDR = false
    }

    fun saveProfile(profileName: String) {
        if (profileName.isNotEmpty() && profileName != "-- Default Profile --") {
            val oldProfiles = preferences.getStringSet("screen_effect_profiles", mutableSetOf()) ?: emptySet()
            val newProfiles = mutableSetOf<String>()
            val settings = KeyValueSet()
            settings.put("brightness", brightness)
            settings.put("contrast", contrast)
            settings.put("gamma", gamma)
            settings.put("fxaa", enableFXAA)
            settings.put("crt_shader", enableCRT)
            settings.put("toon_shader", enableToon)
            settings.put("ntsc_effect", enableNTSC)
            settings.put("vignette_effect", enableVignette)
            settings.put("sepia_effect", enableSepia)
            settings.put("blur_effect", enableBlur)
            settings.put("pixelate_effect", enablePixelate)
            settings.put("grayscale_effect", enableGrayscale)
            settings.put("sharpen_effect", enableSharpen)
            settings.put("smooth_effect", enableSmooth)
            settings.put("hdr_effect", enableHDR)

            oldProfiles.forEach {
                if (it.split(":")[0] == profileName) {
                    newProfiles.add("$profileName:${settings}")
                } else {
                    newProfiles.add(it)
                }
            }
            preferences.edit().putStringSet("screen_effect_profiles", newProfiles).apply()
            activity.screenEffectProfile = profileName
        }
    }

    fun applyVulkanEffects() {
        val renderer = activity.xServerView?.renderer ?: return
        val isVulkan = renderer is com.winlator.cmod.renderer.VulkanRenderer
        
        preferences.edit()
            .putFloat("effect_brightness", brightness)
            .putFloat("effect_contrast", contrast)
            .putFloat("effect_gamma", gamma)
            .putBoolean("effect_fxaa", enableFXAA)
            .putBoolean("effect_crt", enableCRT)
            .putBoolean("effect_toon", enableToon)
            .putBoolean("effect_ntsc", enableNTSC)
            .putBoolean("effect_vignette", enableVignette)
            .putBoolean("effect_sepia", enableSepia)
            .putBoolean("effect_blur", enableBlur)
            .putBoolean("effect_pixelate", enablePixelate)
            .putBoolean("effect_grayscale", enableGrayscale)
            .putBoolean("effect_sharpen", enableSharpen)
            .putBoolean("effect_smooth", enableSmooth)
            .putBoolean("effect_hdr", enableHDR)
            .apply()

        val types = ArrayList<Int>()
        val paramsList = ArrayList<FloatArray>()

        if (brightness != 0f || contrast != 0f || gamma != 1.0f) {
            types.add(EffectComposer.EFFECT_COLOR)
            paramsList.add(floatArrayOf(brightness / 100f, contrast / 100f, gamma, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableHDR) {
            types.add(EffectComposer.EFFECT_HDR)
            paramsList.add(floatArrayOf(0.4f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableFXAA) {
            types.add(EffectComposer.EFFECT_FXAA)
            paramsList.add(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableCRT) {
            types.add(EffectComposer.EFFECT_CRT)
            paramsList.add(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableToon) {
            types.add(EffectComposer.EFFECT_TOON)
            paramsList.add(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableNTSC) {
            types.add(EffectComposer.EFFECT_NTSC)
            val screenW = renderer.surfaceWidth.toFloat()
            val screenH = renderer.surfaceHeight.toFloat()
            paramsList.add(floatArrayOf(0f, screenW, screenH, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableVignette) {
            types.add(EffectComposer.EFFECT_VIGNETTE)
            paramsList.add(floatArrayOf(0.5f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableSepia) {
            types.add(EffectComposer.EFFECT_SEPIA)
            paramsList.add(floatArrayOf(1.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableBlur) {
            types.add(EffectComposer.EFFECT_BLUR)
            paramsList.add(floatArrayOf(2.0f, 5.0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enablePixelate) {
            types.add(EffectComposer.EFFECT_PIXELATE)
            paramsList.add(floatArrayOf(4.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableGrayscale) {
            types.add(EffectComposer.EFFECT_GRAYSCALE)
            paramsList.add(floatArrayOf(1.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableSharpen) {
            types.add(EffectComposer.EFFECT_SHARPEN)
            paramsList.add(floatArrayOf(1.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (enableSmooth) {
            types.add(EffectComposer.EFFECT_SMOOTH)
            paramsList.add(floatArrayOf(1.0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        }

        if (types.isEmpty()) {
            renderer.clearEffects()
        } else {
            if (isVulkan) {
                (renderer as com.winlator.cmod.renderer.VulkanRenderer).disableScanoutForEffects()
            }
            val typeArr = types.toIntArray()
            val paramsArr = paramsList.toTypedArray()
            renderer.setEffects(typeArr, paramsArr)
        }

        saveProfile(selectedProfile)
    }

    var showAddProfileDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.95f),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassSheetFill(), contentColor = glassTitleColor(),
                tonalElevation = 0.dp,
                border = glassCardBorder(),
            ) {
            Column(Modifier.fillMaxSize()) {
                val scrollState = rememberScrollState()
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    // Left column: color sliders
                    Column(
                        Modifier.weight(1.2f).fillMaxHeight().verticalScroll(scrollState).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.color_adjustment), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.brightness), style = MaterialTheme.typography.bodySmall)
                                Text("${brightness.toInt()}", style = MaterialTheme.typography.bodySmall)
                            }
                            Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -50f..50f,
                                colors = glassSliderColors())
                        }

                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.contrast), style = MaterialTheme.typography.bodySmall)
                                Text("${contrast.toInt()}", style = MaterialTheme.typography.bodySmall)
                            }
                            Slider(value = contrast, onValueChange = { contrast = it }, valueRange = -100f..100f,
                                colors = glassSliderColors())
                        }

                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.gamma), style = MaterialTheme.typography.bodySmall)
                                Text(String.format(Locale.US, "%.2f", gamma), style = MaterialTheme.typography.bodySmall)
                            }
                            Slider(value = gamma, onValueChange = { gamma = it }, valueRange = 0.5f..3.0f,
                                colors = glassSliderColors())
                        }
                    }

                    Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                    // Right column: profile + effects
                    Column(
                        Modifier.weight(1.8f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(stringResource(R.string.profile), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                GlassPill(
                                    onClick = { dropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(selectedProfile.ifEmpty { "-- Default Profile --" }, style = MaterialTheme.typography.bodySmall)
                                }
                                DropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                                    profileList.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                selectedProfile = if (name == "-- Default Profile --") "" else name
                                                if (selectedProfile.isNotEmpty()) loadProfile(selectedProfile)
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { showAddProfileDialog = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Add, "Add profile", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                if (selectedProfile.isNotEmpty()) {
                                    val oldProfiles = preferences.getStringSet("screen_effect_profiles", mutableSetOf()) ?: emptySet()
                                    val newProfiles = oldProfiles.filter { it.split(":")[0] != selectedProfile }.toSet()
                                    preferences.edit().putStringSet("screen_effect_profiles", newProfiles).apply()
                                    profileList = profileList.filter { it != selectedProfile }
                                    selectedProfile = ""
                                    resetSettings()
                                }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, "Remove profile", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 2.dp))

                        CheckBoxRow(label = stringResource(R.string.enable_fxaa), checked = enableFXAA, onCheckedChange = { enableFXAA = it })
                        CheckBoxRow(label = stringResource(R.string.enable_crt_shader), checked = enableCRT, onCheckedChange = { enableCRT = it })
                        CheckBoxRow(label = stringResource(R.string.enable_toon_shader), checked = enableToon, onCheckedChange = { enableToon = it })
                        CheckBoxRow(label = stringResource(R.string.enable_ntsc_effect), checked = enableNTSC, onCheckedChange = { enableNTSC = it })
                        CheckBoxRow(label = stringResource(R.string.enable_vignette_effect), checked = enableVignette, onCheckedChange = { enableVignette = it })
                        CheckBoxRow(label = stringResource(R.string.enable_sepia_effect), checked = enableSepia, onCheckedChange = { enableSepia = it })
                        CheckBoxRow(label = stringResource(R.string.enable_blur_effect), checked = enableBlur, onCheckedChange = { enableBlur = it })
                        CheckBoxRow(label = stringResource(R.string.enable_pixelate_effect), checked = enablePixelate, onCheckedChange = { enablePixelate = it })
                        CheckBoxRow(label = stringResource(R.string.enable_grayscale_effect), checked = enableGrayscale, onCheckedChange = { enableGrayscale = it })
                        CheckBoxRow(label = stringResource(R.string.enable_sharpen_effect), checked = enableSharpen, onCheckedChange = { enableSharpen = it })
                        CheckBoxRow(label = stringResource(R.string.enable_smooth_effect), checked = enableSmooth, onCheckedChange = { enableSmooth = it })
                        CheckBoxRow(label = "HDR", checked = enableHDR, onCheckedChange = { enableHDR = it })
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { resetSettings() }) {
                        Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    GlassSolidPill(
                        onClick = {
                        applyVulkanEffects()
                        onDismiss()
                    },
                    ) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrameGenerationDialogCompose(
    activity: XServerDisplayActivity,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val container = activity.getContainer() ?: return
    val lsfgDllAvailable = remember { com.winlator.cmod.core.LsfgNative.isDllAvailable(activity) }
    var engine by remember { mutableStateOf(if (container.isCronyfg() || !container.isLsfgNative()) "cronyfg" else "lsfg-native") }

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(horizontal = 8.dp, vertical = 0.dp),
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = MaterialTheme.colorScheme.onSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = glassCardBorder(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.icon_screen_effect), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lsfg_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                if (readout.isNotEmpty()) {
                    Text(
                        readout,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text("Engine", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("cronyfg", "lsfg-native").forEachIndexed { idx, value ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { engine = value }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            RadioButton(selected = engine == value, onClick = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (idx == 0) "CronyFG" else "LSFG")
                        }
                    }
                }
                if (engine == "lsfg-native" && !lsfgDllAvailable) {
                    Text(
                        stringResource(R.string.lsfg_not_in_library),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(8.dp))

                Text(stringResource(R.string.lsfg_target), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(0, 60, 90, 120, 144, 165).forEach { valTag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { targetRate = valTag }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            RadioButton(selected = targetRate == valTag, onClick = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (valTag == 0) "Fixed" else "${valTag}")
                        }
                    }
                }
                Text(
                    stringResource(R.string.lsfg_target_note),
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(8.dp))

                Text(stringResource(R.string.lsfg_multiplier), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    listOf(0, 2, 3, 4).forEach { valTag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { multiplier = valTag; targetRate = 0 }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            RadioButton(selected = multiplier == valTag, onClick = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (valTag == 0) "Off" else "${valTag}x")
                        }
                    }
                }
                if (targetRate != 0) {
                    Text(
                        stringResource(R.string.lsfg_mult_fixed_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.lsfg_flow_scale))
                    Text(String.format(java.util.Locale.US, "%.2f", flowScale))
                }
                Slider(
                    value = flowScale,
                    onValueChange = { flowScale = it },
                    valueRange = 0.25f..1.0f,
                    steps = 14
                ,
                    colors = glassSliderColors())
                Spacer(Modifier.height(8.dp))
                } // scrollable content

                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassSolidPill(
                        onClick = {
                            // Off disarms but keeps the saved multiplier preset.
                            if (multiplier > 0) container.setFrameGenMultiplier(multiplier)
                            container.setFrameGenFlowScale(flowScale)
                            container.setFrameGenTargetRate(targetRate)
                            container.setFrameGenEngine(if (multiplier > 0) engine else "off")
                            container.saveData()
                            // Same as the legacy dialog: rebuild the shader cache in
                            // background AND apply now. Prepare's completion re-sets
                            // the cache path, which unlatches the native engine
                            // retry — first enable works without a restart.
                            activity.prepareLsfgNative()
                            activity.applyLsfgNative(if (multiplier > 0) multiplier else 0, flowScale)
                            val msg = ctx.getString(R.string.lsfg_applied, if (multiplier > 0) "${multiplier}x" else "Off")
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    ) { Text(stringResource(R.string.ok), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun InputControlsDialogCompose(
    activity: XServerDisplayActivity,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.icon_input_controls), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.input_controls))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        SpinnerRow(
                            label = stringResource(R.string.profile),
                            entries = profileItems,
                            selected = profileItems[selectedProfileIdx],
                            onSelected = { selectedProfileIdx = profileItems.indexOf(it).coerceAtLeast(0) }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        val position = selectedProfileIdx
                        val profileId = if (position > 0) profiles[position - 1].id else 0
                        activity.launchInputControlsEditor(profileId) {
                            manager.loadProfiles(true)
                        }
                        onDismiss()
                    }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.edit))
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                CheckBoxRow(label = stringResource(R.string.show_touchscreen_controls), checked = showTouchscreen, onCheckedChange = { showTouchscreen = it })
                CheckBoxRow(label = stringResource(R.string.enable_touchscreen_timeout), checked = enableTimeout, onCheckedChange = { enableTimeout = it })
                CheckBoxRow(label = stringResource(R.string.enable_touchscreen_haptics), checked = enableHaptics, onCheckedChange = { enableHaptics = it })
                CheckBoxRow(label = stringResource(R.string.enable_gyroscope_control), checked = enableGyro, onCheckedChange = { enableGyro = it })

                if (enableGyro) {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.gyroscope_sensitivity))
                            Text(String.format(Locale.US, "%.1f", gyroSensitivity))
                        }
                        Slider(value = gyroSensitivity, onValueChange = { gyroSensitivity = it }, valueRange = 0.1f..5.0f,
                            colors = glassSliderColors())
                    }
                }

                CheckBoxRow(label = stringResource(R.string.enable_quick_access_panel), checked = enableQuickAccess, onCheckedChange = { enableQuickAccess = it })
                CheckBoxRow(label = stringResource(R.string.relative_mouse_movement), checked = relativeMouse, onCheckedChange = { relativeMouse = it })
            }
        },
        confirmButton = {
            GlassSolidPill(
                onClick = {
                val profileId = if (selectedProfileIdx > 0) profiles[selectedProfileIdx - 1].id else 0
                activity.applyInputControlsSettings(
                    showTouchscreen,
                    enableTimeout,
                    enableHaptics,
                    enableGyro,
                    gyroSensitivity,
                    enableQuickAccess,
                    relativeMouse,
                    profileId
                )
                onDismiss()
            },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
