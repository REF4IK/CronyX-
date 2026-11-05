package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.glassSliderColors
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.GlassChevron
import com.winlator.cmod.ui.components.GlassSectionHeader
import com.winlator.cmod.ui.components.GlassSurface
import com.winlator.cmod.ui.components.GlassSwitchRow
import com.winlator.cmod.ui.components.GlassSwitch
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassBorder
import com.winlator.cmod.ui.components.glassFillBrush
import com.winlator.cmod.ui.components.glassGroupDivider
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.glassValueColor
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.box86_64.Box86_64PresetManager
import com.winlator.cmod.box86_64.rc.RCManager
import com.winlator.cmod.container.Container
import com.winlator.cmod.core.KeyValueSet
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.core.WineThemeManager
import com.winlator.cmod.core.ImageUtils
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.fexcore.FEXCorePresetManager
import com.winlator.cmod.winhandler.WinHandler
import com.winlator.cmod.xserver.XKeycode
import com.winlator.cmod.widget.EnvVarsView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import org.json.JSONArray
import java.util.Locale

// ---- Переиспользуемые Composable-компоненты ----

// ---- Стеклянные строки в стиле GameHub ----
//
// Прежний вид (мелкая серая подпись + outlined-дропдаун) не соответствовал
// референсу. В GameHub строка = крупный белый заголовок слева, значение
// справа с обрезкой и шеврон; группы разделены линиями; заголовок секции
// стоит НАД карточкой; действие — полноширинная стеклянная пилюля.
// Сигнатуры функций не изменены, поэтому вкладки правок не требуют.

/** Приведение сохранённого значения к одному из пунктов списка. */
private fun matchEntry(entries: List<String>, selected: String): String {
    val trimmed = selected.trim()
    if (trimmed.isEmpty()) return selected
    return entries.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        ?: entries.firstOrNull {
            it.startsWith(trimmed, true) || trimmed.startsWith(it, true) ||
                it.contains(trimmed, true) || trimmed.contains(it, true)
        }
        ?: selected
}

/**
 * Базовая строка-выбор: заголовок + значение + шеврон, с выпадающим списком.
 * [trailing] — дополнительные круглые кнопки справа (конфиг/загрузка).
 */
@Composable
private fun GlassPickerRow(
    title: String,
    entries: List<String>,
    value: String,
    enabled: Boolean,
    onSelected: (String) -> Unit,
    hasAction: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassGroupDivider()
            .then(if (enabled) Modifier.clickable { expanded = true } else Modifier)
            .padding(
                horizontal = GlassTokens.RowPaddingH,
                vertical = GlassTokens.RowPaddingV,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = GlassTokens.TitleSize,
            fontWeight = FontWeight.Medium,
            color = if (enabled) glassTitleColor() else glassTitleColor().copy(alpha = 0.38f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = value,
                    modifier = Modifier.widthIn(max = if (hasAction) 150.dp else 180.dp),
                    fontSize = GlassTokens.ValueSize,
                    color = glassValueColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!hasAction) GlassChevron(enabled = enabled)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry) },
                        onClick = { onSelected(entry); expanded = false },
                    )
                }
            }
        }

        trailing()
    }
}

/** Круглая стеклянная кнопка-действие справа от строки. */
@Composable
private fun GlassRoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(glassAccent().copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = glassAccent(),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun ContainerSpinnerRow(
    label: String,
    entries: List<String>,
    selected: String,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    GlassPickerRow(
        title = label,
        entries = entries,
        value = matchEntry(entries, selected),
        enabled = enabled,
        onSelected = onSelected,
    )
}

@Composable
fun ContainerSpinnerRowWithDownload(
    label: String,
    entries: List<String>,
    selected: String,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
    onDownloadClick: () -> Unit,
) {
    GlassPickerRow(
        title = label,
        entries = entries,
        value = matchEntry(entries, selected),
        enabled = enabled,
        onSelected = onSelected,
        hasAction = true,
        trailing = {
            GlassRoundAction(Icons.Filled.Download, "Download", onDownloadClick)
        },
    )
}

@Composable
fun ContainerSpinnerRowWithConfig(
    label: String,
    entries: List<String>,
    selected: String,
    enabled: Boolean = true,
    showConfigButton: Boolean = true,
    onSelected: (String) -> Unit,
    onConfigClick: () -> Unit = {},
) {
    GlassPickerRow(
        title = label,
        entries = entries,
        value = matchEntry(entries, selected),
        enabled = enabled,
        onSelected = onSelected,
        hasAction = showConfigButton,
        trailing = {
            if (showConfigButton) {
                GlassRoundAction(
                    Icons.Filled.Settings,
                    stringResource(R.string.configuration),
                    onConfigClick,
                )
            }
        },
    )
}

@Composable
fun ContainerSpinnerRowWithConfigAndDownload(
    label: String,
    entries: List<String>,
    selected: String,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
    onConfigClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
) {
    GlassPickerRow(
        title = label,
        entries = entries,
        value = matchEntry(entries, selected),
        enabled = enabled,
        onSelected = onSelected,
        hasAction = true,
        trailing = {
            GlassRoundAction(
                Icons.Filled.Settings,
                stringResource(R.string.configuration),
                onConfigClick,
            )
            GlassRoundAction(Icons.Filled.Download, "Download", onDownloadClick)
        },
    )
}

/**
 * Действие — полноширинная стеклянная пилюля (как «Установить новый компонент»
 * в референсе), а не outlined-кнопка с иконкой настроек.
 */
@Composable
fun ButtonRow(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(GlassTokens.CardRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GlassTokens.RowPaddingH, vertical = 10.dp)
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(glassFillBrush())
            .border(glassBorder(), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = GlassTokens.TitleSize,
            fontWeight = FontWeight.Medium,
            color = glassTitleColor(),
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GlassSwitchRow(
        title = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

/**
 * Группа: заголовок секции НАД стеклянной карточкой, как в референсе.
 * Иконка оставлена в сигнатуре, но в стекло-стиле заголовок без иконки —
 * она только подкрашивает акцент, если вызывающий код её передаёт.
 */
@Composable
fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        GlassSectionHeader(
            title = title,
            action = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glassAccent(),
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        GlassSurface(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

// ---- Вкладка 0: Wine Configuration ----

@Composable
fun WineConfigTab(
    desktopTheme: String,
    onDesktopThemeChange: (String) -> Unit,
    csmt: Int, onCsmtChange: (Int) -> Unit,
    gpuNamePos: Int, onGpuNamePosChange: (Int) -> Unit,
    offscreenRenderingMode: String, onOffscreenRenderingModeChange: (String) -> Unit,
    strictShaderMath: Int, onStrictShaderMathChange: (Int) -> Unit,
    videoMemorySize: String, onVideoMemorySizeChange: (String) -> Unit,
    mouseWarpOverride: String, onMouseWarpOverrideChange: (String) -> Unit,
    logPixels: Int, onLogPixelsChange: (Int) -> Unit,
    onExportProfile: () -> Unit,
    onImportProfile: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionCard(title = stringResource(R.string.desktop_theme), icon = Icons.Filled.Palette) {
            val themeParts = desktopTheme.split(",")
            val theme = themeParts.getOrElse(0) { "LIGHT" }
            val bgType = themeParts.getOrElse(1) { "IMAGE" }
            val bgColor = themeParts.getOrElse(2) { "#0277bd" }
            ContainerSpinnerRow(
                label = stringResource(R.string.desktop_theme),
                entries = listOf("LIGHT", "DARK"),
                selected = theme,
                onSelected = { onDesktopThemeChange("$it,$bgType,$bgColor") },
            )
            ContainerSpinnerRow(
                label = stringResource(R.string.desktop_background),
                entries = listOf("IMAGE", "COLOR"),
                selected = bgType,
                onSelected = { onDesktopThemeChange("$theme,$it,$bgColor") },
            )
            if (bgType == "COLOR") {
                val colors = listOf("#ff8f00", "#d32f2f", "#9575cd", "#2e7d32", "#00838f", "#0277bd", "#607d8b", "#000000")
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.color), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors.forEach { colorHex ->
                            val color = remember(colorHex) { Color(android.graphics.Color.parseColor(colorHex)) }
                            val isSelected = bgColor.equals(colorHex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(color, shape = CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { onDesktopThemeChange("$theme,$bgType,$colorHex") }
                            )
                        }
                    }
                }
            }
            if (bgType == "IMAGE") {
                val userWallpaperFile = remember { WineThemeManager.getUserWallpaperFile(ctx) }
                var wallpaperExists by remember { mutableStateOf(userWallpaperFile.isFile) }
                val bitmap by produceState<ImageBitmap?>(initialValue = null, wallpaperExists, desktopTheme, userWallpaperFile) {
                    value = if (userWallpaperFile.isFile) {
                        withContext(Dispatchers.IO) {
                            try {
                                BitmapFactory.decodeFile(userWallpaperFile.path)?.asImageBitmap()
                            } catch (_: Exception) { null }
                        }
                    } else null
                }
                
                val pickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            val bitmapObj = ImageUtils.getBitmapFromUri(ctx, uri, 1280)
                            if (bitmapObj != null) {
                                ImageUtils.save(bitmapObj, userWallpaperFile, Bitmap.CompressFormat.PNG, 100)
                                wallpaperExists = true
                                onDesktopThemeChange("$theme,$bgType,$bgColor,${userWallpaperFile.lastModified()}")
                            }
                        } catch (e: Exception) {
                            AppUtils.showToast(ctx, "Failed to load image: ${e.message}")
                        }
                    }
                }
                
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.desktop_background) + ":",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bitmap != null) {
                            Card(
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.size(80.dp),
                                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                                border = glassCardBorder(),
                            ) {
                                Image(
                                    bitmap = bitmap!!,
                                    contentDescription = "Current wallpaper",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            Card(
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.size(80.dp),
                                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                                border = glassCardBorder(),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No Image", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            GlassSolidPill(
                                onClick = { pickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Photo, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.browse))
                            }
                            if (wallpaperExists) {
                                GlassPill(
                                    onClick = {
                                        userWallpaperFile.delete()
                                        wallpaperExists = false
                                        onDesktopThemeChange("$theme,$bgType,$bgColor,0")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.remove))
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.registry_keys), icon = Icons.Filled.Tune) {
            ContainerSpinnerRow(
                label = stringResource(R.string.csmt),
                entries = listOf("Disable", "Enable"),
                selected = if (csmt == 0) "Disable" else "Enable",
                onSelected = { onCsmtChange(if (it == "Disable") 0 else 3) },
            )
            val gpuNames = remember {
                try {
                    val json = JSONArray(com.winlator.cmod.core.FileUtils.readString(ctx, "gpu_cards.json"))
                    (0 until json.length()).map { json.getJSONObject(it).optString("name", "GPU $it") }
                } catch (_: Exception) { listOf("Default GPU") }
            }
            ContainerSpinnerRow(
                label = stringResource(R.string.gpu_name),
                entries = gpuNames,
                selected = gpuNames.getOrElse(gpuNamePos) { gpuNames[0] },
                onSelected = { onGpuNamePosChange(gpuNames.indexOf(it)) },
            )
            ContainerSpinnerRow(
                label = stringResource(R.string.offscreen_rendering_mode),
                entries = listOf("Backbuffer", "FBO"),
                selected = if (offscreenRenderingMode.equals("backbuffer", true)) "Backbuffer" else "FBO",
                onSelected = { onOffscreenRenderingModeChange(it.lowercase()) },
            )
            ContainerSpinnerRow(
                label = stringResource(R.string.strict_shader_math),
                entries = listOf("Disable", "Enable"),
                selected = if (strictShaderMath == 0) "Disable" else "Enable",
                onSelected = { onStrictShaderMathChange(if (it == "Disable") 0 else 1) },
            )
            ContainerSpinnerRow(
                label = stringResource(R.string.video_memory_size),
                entries = ctx.resources.getStringArray(R.array.video_memory_size_entries).toList(),
                selected = videoMemorySize,
                onSelected = { onVideoMemorySizeChange(it) },
            )
            ContainerSpinnerRow(
                label = stringResource(R.string.mouse_warp_override),
                entries = listOf("Disable", "Enable", "Force"),
                selected = mouseWarpOverride.replaceFirstChar { it.uppercase() },
                onSelected = { onMouseWarpOverrideChange(it.lowercase()) },
            )
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("${stringResource(R.string.dpi)}: $logPixels", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = logPixels.toFloat(),
                    onValueChange = { onLogPixelsChange((it / 24 * 24).toInt().coerceIn(96, 240)) },
                    valueRange = 96f..240f,
                    steps = (240 - 96) / 24 - 1,
                
                    colors = glassSliderColors())
            }
        }

        SectionCard(title = stringResource(R.string.container_profile_actions), icon = Icons.Filled.Palette) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassSolidPill(
                    onClick = onExportProfile,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Publish, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_container_profile))
                }
                GlassSolidPill(
                    onClick = onImportProfile,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_container_profile))
                }
            }
        }
    }
}

// ---- Вкладка 1: Win Components ----

@Composable
fun WinComponentsTab(
    winComponents: String,
    onWinComponentsChange: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val kv = remember(winComponents) { KeyValueSet(winComponents) }
    val directXComponents = listOf("direct3d", "directsound", "directmusic", "directshow", "directplay")
    val generalComponents = listOf("xaudio", "vcrun2010", "opengl")
    val options = listOf("Builtin (Wine)", "Native (Windows)")

    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionCard(title = stringResource(R.string.directx), icon = Icons.Filled.VideogameAsset) {
            directXComponents.forEach { comp ->
                val value = kv.get(comp) ?: "0"
                ContainerSpinnerRow(
                    label = StringUtils.getString(ctx, comp),
                    entries = options,
                    selected = options.getOrElse(value.toIntOrNull() ?: 0) { options[0] },
                    onSelected = {
                        kv.put(comp, options.indexOf(it).toString())
                        onWinComponentsChange(kv.toString())
                    },
                )
            }
        }
        SectionCard(title = stringResource(R.string.general), icon = Icons.Filled.Build) {
            generalComponents.forEach { comp ->
                val value = kv.get(comp) ?: "0"
                ContainerSpinnerRow(
                    label = StringUtils.getString(ctx, comp),
                    entries = options,
                    selected = options.getOrElse(value.toIntOrNull() ?: 0) { options[0] },
                    onSelected = {
                        kv.put(comp, options.indexOf(it).toString())
                        onWinComponentsChange(kv.toString())
                    },
                )
            }
        }
    }
}

// ---- Вкладка 2: Environment Variables ----

@Composable
fun EnvVarsTab(
    envVars: String,
    onEnvVarsChange: (String) -> Unit,
) {
    var vars by remember(envVars) {
        mutableStateOf(envVars.split(" ").filter { it.contains("=") }.map {
            val idx = it.indexOf("=")
            it.substring(0, idx) to it.substring(idx + 1)
        })
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassSolidPill(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add))
        }
        if (vars.isEmpty()) {
            Text(stringResource(R.string.no_env_vars), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        } else {
            vars.forEachIndexed { index, (name, value) ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                    editingIndex = index
                    showAddDialog = true
                },
                    colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                    shape = RoundedCornerShape(GlassTokens.CardRadius),
                    border = glassCardBorder(),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        }
                        IconButton(onClick = {
                            vars = vars.toMutableList().also { it.removeAt(index) }
                            onEnvVarsChange(vars.joinToString(" ") { "${it.first}=${it.second}" })
                        }) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var varName by remember { mutableStateOf(if (editingIndex >= 0) vars[editingIndex].first else "") }
        var varValue by remember { mutableStateOf(if (editingIndex >= 0) vars[editingIndex].second else "") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false; editingIndex = -1 },
            title = { Text(if (editingIndex >= 0) stringResource(R.string.edit) else stringResource(R.string.add_env_var)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Name row with trailing dropdown icon
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = varName,
                            onValueChange = { varName = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        ,
                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                        Spacer(Modifier.width(8.dp))
                        Box {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select variable")
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                EnvVarsView.knownEnvVars.forEach { known ->
                                    DropdownMenuItem(
                                        text = { Text(known[0]) },
                                        onClick = {
                                            varName = known[0]
                                            varValue = when (known[1]) {
                                                "CHECKBOX", "SELECT" -> known[2]
                                                "SELECT_MULTIPLE" -> ""
                                                else -> ""
                                            }
                                            menuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Value field based on variable type
                    val knownVar = remember(varName) { EnvVarsView.knownEnvVars.firstOrNull { it[0] == varName } }
                    if (knownVar != null) {
                        val type = knownVar[1]
                        when (type) {
                            "CHECKBOX", "SELECT" -> {
                                val options = knownVar.slice(2 until knownVar.size)
                                ContainerSpinnerRow(
                                    label = stringResource(R.string.value),
                                    entries = options,
                                    selected = varValue,
                                    onSelected = { varValue = it }
                                )
                            }
                            "SELECT_MULTIPLE" -> {
                                val options = knownVar.slice(2 until knownVar.size)
                                val selectedOptions = remember(varValue) { varValue.split(",").filter { it.isNotEmpty() }.toSet() }
                                Text("Select values:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                                    val scroll = rememberScrollState()
                                    Column(modifier = Modifier.verticalScroll(scroll)) {
                                        options.forEach { option ->
                                            val checked = selectedOptions.contains(option)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val updated = if (checked) selectedOptions - option else selectedOptions + option
                                                        varValue = updated.joinToString(",")
                                                    }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                GlassSwitch(
                                                    checked = checked,
                                                    onCheckedChange = {
                                                        val updated = if (it) selectedOptions + option else selectedOptions - option
                                                        varValue = updated.joinToString(",")
                                                    }
                                                )
                                                Text(option, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                            "NUMBER" -> {
                                OutlinedTextField(
                                    value = varValue,
                                    onValueChange = { varValue = it.filter { c -> c.isDigit() } },
                                    label = { Text("Value (Number)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                ,
                                    shape = GlassFieldShape,
                                    colors = glassFieldColors())
                            }
                            else -> {
                                OutlinedTextField(
                                    value = varValue,
                                    onValueChange = { varValue = it },
                                    label = { Text("Value") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                ,
                                    shape = GlassFieldShape,
                                    colors = glassFieldColors())
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = varValue,
                            onValueChange = { varValue = it },
                            label = { Text("Value") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        ,
                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (varName.isNotEmpty()) {
                        val newVars = vars.toMutableList()
                        if (editingIndex >= 0) {
                            newVars[editingIndex] = varName to varValue
                        } else {
                            newVars.add(varName to varValue)
                        }
                        vars = newVars
                        onEnvVarsChange(vars.joinToString(" ") { "${it.first}=${it.second}" })
                    }
                    showAddDialog = false
                    editingIndex = -1
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false; editingIndex = -1 }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ---- Вкладка 3: Drives ----

@Composable
fun DrivesTab(
    drives: String,
    onDrivesChange: (String) -> Unit,
) {
    var driveList by remember(drives) { mutableStateOf(parseDrives(drives).toMutableList()) }
    val letters = ('D'..'Z').toList()
    var editingDriveIndex by remember { mutableStateOf(-1) }

    val openTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && editingDriveIndex >= 0) {
            val path = uri.path?.let { p ->
                if (p.startsWith("/tree/primary:")) "/storage/emulated/0/" + p.removePrefix("/tree/primary:")
                else p
            } ?: uri.toString()
            val newList = driveList.toMutableList()
            newList[editingDriveIndex] = newList[editingDriveIndex].first to path
            driveList = newList
            onDrivesChange(driveList.joinToString("") { "${it.first}:${it.second}" })
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassSolidPill(
            onClick = {
            val usedLetters = driveList.map { it.first }
            val nextLetter = letters.firstOrNull { it !in usedLetters } ?: return@GlassSolidPill
            driveList = (driveList + (nextLetter to "")).toMutableList()
            onDrivesChange(driveList.joinToString("") { "${it.first}:${it.second}" })
        },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add))
        }
        if (driveList.isEmpty()) {
            Text(stringResource(R.string.no_drives), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        } else {
            driveList.forEachIndexed { index, (letter, path) ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                    shape = RoundedCornerShape(GlassTokens.CardRadius),
                    border = glassCardBorder(),
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(letter.toString(), modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = path, onValueChange = { newPath ->
                                val newList = driveList.toMutableList()
                                newList[index] = letter to newPath
                                driveList = newList
                                onDrivesChange(driveList.joinToString("") { "${it.first}:${it.second}" })
                            },
                            singleLine = true, modifier = Modifier.weight(1f)
                        ,
                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                        IconButton(onClick = {
                            editingDriveIndex = index
                            openTreeLauncher.launch(null)
                        }) { Icon(Icons.Filled.Folder, null) }
                        IconButton(onClick = {
                            driveList = driveList.toMutableList().also { it.removeAt(index) }
                            onDrivesChange(driveList.joinToString("") { "${it.first}:${it.second}" })
                        }) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

private fun parseDrives(drives: String): List<Pair<Char, String>> {
    val result = mutableListOf<Pair<Char, String>>()
    val regex = Regex("([A-Z]):(.*?)(?=[A-Z]:|\$)")
    for (match in regex.findAll(drives)) {
        val letter = match.groupValues[1][0]
        val path = match.groupValues[2]
        result.add(letter to path)
    }
    return result
}

// ---- Вкладка 4: Advanced ----

@Composable
fun AdvancedTab(
    box64Version: String, onBox64VersionChange: (String) -> Unit,
    box64Versions: List<String>,
    box64Preset: String, onBox64PresetChange: (String) -> Unit,
    rcfileId: Int, onRcfileIdChange: (Int) -> Unit,
    fexcoreVersion: String, onFexcoreVersionChange: (String) -> Unit,
    fexcoreVersions: List<String>,
    fexcorePreset: String, onFexcorePresetChange: (String) -> Unit,
    useUnixLibs: Boolean, onUseUnixLibsChange: (Boolean) -> Unit,
    startupSelection: Int, onStartupSelectionChange: (Int) -> Unit,
    wow64Mode: Boolean, onWow64ModeChange: (Boolean) -> Unit,
    cpuList: String, onCpuListChange: (String) -> Unit,
    cpuListWoW64: String, onCpuListWoW64Change: (String) -> Unit,
    legacyMode: Boolean,
    enableXInput: Boolean, onEnableXInputChange: (Boolean) -> Unit,
    enableDInput: Boolean, onEnableDInputChange: (Boolean) -> Unit,
    dinputMapperType: Int, onDinputMapperTypeChange: (Int) -> Unit,
    sdl2Toggle: Boolean, onSdl2ToggleChange: (Boolean) -> Unit,
    presetsRefreshKey: Int,
    isArm64EC: Boolean,
    emulator: String,
    onBox64PresetAdd: () -> Unit,
    onBox64PresetEdit: () -> Unit,
    onBox64PresetDuplicate: () -> Unit,
    onBox64PresetRemove: () -> Unit,
    onBox64PresetExport: () -> Unit,
    onBox64PresetImport: () -> Unit,
    onFexcorePresetAdd: () -> Unit,
    onFexcorePresetEdit: () -> Unit,
    onFexcorePresetDuplicate: () -> Unit,
    onFexcorePresetRemove: () -> Unit,
    onFexcorePresetExport: () -> Unit,
    onFexcorePresetImport: () -> Unit,
    onBox64VersionDownload: () -> Unit,
    onFexcoreVersionDownload: () -> Unit,
) {
    val ctx = LocalContext.current
    val numCpus = remember { Runtime.getRuntime().availableProcessors() }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (emulator.lowercase(java.util.Locale.ENGLISH) != "fexcore") {
            // Box64
            SectionCard(title = stringResource(R.string.box64), icon = Icons.Filled.Code) {
                ContainerSpinnerRowWithDownload(
                    label = stringResource(R.string.version),
                    entries = box64Versions, selected = box64Version,
                    onSelected = { onBox64VersionChange(it) },
                    onDownloadClick = onBox64VersionDownload
                )
                val box64Presets = remember(presetsRefreshKey) { Box86_64PresetManager.getPresets("box64", ctx) }
                val box64PresetNames = remember(box64Presets) { box64Presets.map { it.name } }
                ContainerSpinnerRow(
                    label = stringResource(R.string.preset),
                    entries = box64PresetNames,
                    selected = box64Presets.firstOrNull { it.id == box64Preset }?.name ?: box64Preset,
                    onSelected = {
                        val preset = box64Presets.firstOrNull { p -> p.name == it }
                        if (preset != null) onBox64PresetChange(preset.id)
                    },
                )
                // Кнопки управления Box64 preset (Add, Edit, Duplicate, Remove, Export, Import)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onBox64PresetAdd) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
                    IconButton(onClick = onBox64PresetEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = onBox64PresetDuplicate) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.duplicate)) }
                    IconButton(onClick = onBox64PresetRemove) { Icon(Icons.Filled.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error) }
                    IconButton(onClick = onBox64PresetExport) { Icon(Icons.Filled.Publish, stringResource(R.string.export_container_profile)) }
                    IconButton(onClick = onBox64PresetImport) { Icon(Icons.Filled.Download, stringResource(R.string.import_container_profile)) }
                }
                val rcManager = remember { RCManager(ctx) }
                val rcFiles = remember { rcManager.getRCFiles() }
                val rcFileNames = remember(rcFiles) { rcFiles.map { it.getName() } }
                ContainerSpinnerRow(
                    label = stringResource(R.string.rc_files),
                    entries = rcFileNames,
                    selected = rcFiles.getOrElse(rcfileId) { rcFiles.getOrNull(0) }?.getName() ?: "",
                    onSelected = { onRcfileIdChange(rcFiles.indexOfFirst { r -> r.getName() == it }) },
                )
            }
        }

        if (isArm64EC && emulator.lowercase(java.util.Locale.ENGLISH) == "fexcore") {
            // FEXCore
            SectionCard(title = stringResource(R.string.fexcore), icon = Icons.Filled.Memory) {
                ContainerSpinnerRowWithDownload(
                    label = stringResource(R.string.version),
                    entries = fexcoreVersions, selected = fexcoreVersion,
                    onSelected = { onFexcoreVersionChange(it) },
                    onDownloadClick = onFexcoreVersionDownload
                )
                SwitchRow(stringResource(R.string.use_unix_libs), useUnixLibs, onUseUnixLibsChange)
                val fexcorePresets = remember(presetsRefreshKey) { FEXCorePresetManager.getPresets(ctx) }
                val fexcorePresetNames = remember(fexcorePresets) { fexcorePresets.map { it.name } }
                ContainerSpinnerRow(
                    label = stringResource(R.string.preset),
                    entries = fexcorePresetNames,
                    selected = fexcorePresets.firstOrNull { it.id == fexcorePreset }?.name ?: fexcorePreset,
                    onSelected = {
                        val preset = fexcorePresets.firstOrNull { p -> p.name == it }
                        if (preset != null) onFexcorePresetChange(preset.id)
                    },
                )
                // Кнопки управления FEXCore preset (Add, Edit, Duplicate, Remove, Export, Import)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onFexcorePresetAdd) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
                    IconButton(onClick = onFexcorePresetEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = onFexcorePresetDuplicate) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.duplicate)) }
                    IconButton(onClick = onFexcorePresetRemove) { Icon(Icons.Filled.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error) }
                    IconButton(onClick = onFexcorePresetExport) { Icon(Icons.Filled.Publish, stringResource(R.string.export_container_profile)) }
                    IconButton(onClick = onFexcorePresetImport) { Icon(Icons.Filled.Download, stringResource(R.string.import_container_profile)) }
                }
            }
        }

        // System
        SectionCard(title = stringResource(R.string.system), icon = Icons.Filled.Settings) {
            ContainerSpinnerRow(
                label = stringResource(R.string.startup_selection),
                entries = ctx.resources.getStringArray(R.array.startup_selection_entries).toList(),
                selected = ctx.resources.getStringArray(R.array.startup_selection_entries).getOrElse(startupSelection) { "" },
                onSelected = { onStartupSelectionChange(ctx.resources.getStringArray(R.array.startup_selection_entries).indexOf(it)) },
            )
            SwitchRow(stringResource(R.string.wow64_mode), wow64Mode, onWow64ModeChange)
        }

        // Game Controller
        SectionCard(title = stringResource(R.string.game_controller), icon = Icons.Filled.Gamepad) {
            if (legacyMode) {
                Text(stringResource(R.string.legacy_mode_message), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            } else {
                SwitchRow(stringResource(R.string.enable_xinput), enableXInput, onEnableXInputChange)
                SwitchRow(stringResource(R.string.enable_dinput), enableDInput, onEnableDInputChange)
                if (enableDInput) {
                    ContainerSpinnerRow(
                        label = stringResource(R.string.dinput_mapper),
                        entries = ctx.resources.getStringArray(R.array.dinput_mapper_type_entries).toList(),
                        selected = ctx.resources.getStringArray(R.array.dinput_mapper_type_entries).getOrElse(dinputMapperType) { "" },
                        onSelected = { onDinputMapperTypeChange(ctx.resources.getStringArray(R.array.dinput_mapper_type_entries).indexOf(it)) },
                    )
                }
            }
            SwitchRow(stringResource(R.string.sdl2_compatibility), sdl2Toggle, onSdl2ToggleChange)
        }

        // Processor Affinity
        SectionCard(title = stringResource(R.string.processor_affinity), icon = Icons.Filled.Memory) {
            CpuListRow(
                label = stringResource(R.string.processor_affinity),
                cpuList = cpuList, numCpus = numCpus, onCpuListChange = onCpuListChange,
            )
            CpuListRow(
                label = stringResource(R.string.processor_affinity_wow64),
                cpuList = cpuListWoW64, numCpus = numCpus, onCpuListChange = onCpuListWoW64Change,
            )
        }
    }
}

@Composable
fun CpuListRow(label: String, cpuList: String, numCpus: Int, onCpuListChange: (String) -> Unit) {
    val selected = remember(cpuList) {
        if (cpuList.isEmpty()) (0 until numCpus).toMutableSet()
        else cpuList.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        val rows = (0 until numCpus).chunked(3)
        rows.forEach { rowCpus ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCpus.forEach { cpu ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        GlassSwitch(
                            checked = cpu in selected,
                            onCheckedChange = { checked ->
                                val newSelected = selected.toMutableSet()
                                if (checked) newSelected.add(cpu) else newSelected.remove(cpu)
                                onCpuListChange(newSelected.joinToString(","))
                            }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("CPU$cpu", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (rowCpus.size < 3) repeat(3 - rowCpus.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---- Вкладка 5: XR ----

@Composable
fun XRTab(
    primaryController: Int, onPrimaryControllerChange: (Int) -> Unit,
    controllerMapping: String, onControllerMappingChange: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val controllers = remember { ctx.resources.getStringArray(R.array.xr_controllers).toList() }
    val keycodes = remember { XKeycode.values().map { it.name } }
    val mappingBytes = remember(controllerMapping) { controllerMapping.toByteArray().map { (it.toInt() and 0xFF) } }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionCard(title = stringResource(R.string.xr), icon = Icons.Filled.ViewInAr) {
            ContainerSpinnerRow(
                label = stringResource(R.string.primary_controller),
                entries = controllers,
                selected = controllers.getOrElse(primaryController) { controllers.getOrElse(1) { "" } },
                onSelected = { onPrimaryControllerChange(controllers.indexOf(it)) },
            )
            val buttonNames = listOf("Button A", "Button B", "Button X", "Button Y", "Button Grip", "Button Trigger", "Thumbstick Up", "Thumbstick Down", "Thumbstick Left", "Thumbstick Right")
            buttonNames.forEachIndexed { index, btnName ->
                val kcId = mappingBytes.getOrElse(index) { 0 }
                val kcName = keycodes.getOrElse(kcId) { keycodes.getOrElse(0) { "KEY_A" } }
                ContainerSpinnerRow(
                    label = btnName,
                    entries = keycodes,
                    selected = kcName,
                    onSelected = { newKcName ->
                        val newId = XKeycode.values().firstOrNull { it.name == newKcName }?.id?.toInt() ?: 0
                        val newBytes = mappingBytes.toMutableList()
                        if (index < newBytes.size) newBytes[index] = newId else {
                            while (newBytes.size <= index) newBytes.add(0)
                            newBytes[index] = newId
                        }
                        onControllerMappingChange(String(newBytes.map { it.toByte() }.toByteArray()))
                    },
                )
            }
        }
    }
}
