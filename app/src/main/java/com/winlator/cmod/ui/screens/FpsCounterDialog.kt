package com.winlator.cmod.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.GlassTokens

import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.glassSliderColors
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.GlassSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.widget.FpsCounterConfig

/**
 * Compose-панель настроек счётчика FPS.
 * Отображается как полноэкранный оверлей с прижатой вправо панелью (без использования platform Dialog),
 * что гарантирует полное отсутствие отступов сверху, снизу и по бокам.
 */
@Composable
fun FpsCounterSettingsDialog(
    onDismiss: () -> Unit,
    onConfigChanged: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val config = remember { FpsCounterConfig(ctx) }

    // --- Состояния ---
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
    var showCpuCores by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.CPU_CORES)) }
    var showGpuFreq by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.GPU_FREQ)) }
    var showBatteryLevel by remember { mutableStateOf(config.isModuleVisible(FpsCounterConfig.Module.BATTERY_LEVEL)) }
    var horizontalLayout by remember { mutableStateOf(config.isHorizontalLayout()) }

    var backgroundOpacity by remember { mutableFloatStateOf(config.getBackgroundOpacity().toFloat()) }
    var counterScale by remember { mutableFloatStateOf(config.getCounterScale().toFloat().coerceAtLeast(60f)) }
    var fpsLimit by remember { mutableFloatStateOf(config.getFpsLimit().toFloat()) }
    var counterStyle by remember { mutableIntStateOf(config.getCounterStyle()) }
    var whiteFonts by remember { mutableStateOf(config.isWhiteFonts()) }

    // Полноэкранный контейнер для оверлея
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.TopEnd
    ) {
        // Сама боковая панель настроек
        Surface(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .clickable(enabled = false, onClick = {}), // Предотвращаем закрытие при клике по самой панели
            // Панель прижата к правому краю, поэтому скруглены только левые углы:
            // RectangleShape давал тот самый «квадрат» внутри стекла.
            shape = RoundedCornerShape(
                topStart = GlassTokens.CardRadius,
                bottomStart = GlassTokens.CardRadius,
            ),
            // Панель закрывает игру: с полупрозрачной заливкой сквозь неё
            // проступает кадр, поэтому берём почти непрозрачную.
            color = glassSheetFill(),
            contentColor = glassTitleColor(),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Заголовок ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.fps_counter_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                // ── Прокручиваемый контент ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {

                    // Включить счётчик
                    FpsCompactSwitchRow(
                        label = stringResource(R.string.fps_counter_enabled),
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )

                    // ── Лимит FPS (сразу после кнопки включения счётчика) ──
                    FpsSliderRow(
                        label = if (fpsLimit == 0f) stringResource(R.string.fps_counter_no_limit)
                                else "${fpsLimit.toInt()}",
                        value = fpsLimit,
                        onValueChange = { fpsLimit = it },
                        valueRange = 0f..240f,
                        steps = 23,
                        enabled = true // Всегда работает без включения самого счетчика
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                    // ── Модули в 2 колонки ──
                    FpsSectionHeader(
                        title = stringResource(R.string.fps_counter_modules_title),
                        icon = Icons.Filled.Dashboard
                    )

                    val modules = listOf(
                        stringResource(R.string.fps_counter_show_fps) to showFps,
                        stringResource(R.string.fps_counter_show_ram) to showRam,
                        stringResource(R.string.fps_counter_show_gpu) to showGpu,
                        stringResource(R.string.fps_counter_show_gpu_load) to showGpuLoad,
                        stringResource(R.string.fps_counter_show_gpu_temp) to showGpuTemp,
                        stringResource(R.string.fps_counter_show_cpu_temp) to showCpuTemp,
                        stringResource(R.string.fps_counter_show_renderer) to showRenderer,
                        stringResource(R.string.fps_counter_show_frame_time_graph) to showFrameTimeGraph,
                        stringResource(R.string.fps_counter_show_battery_temp) to showBatteryTemp,
                        stringResource(R.string.fps_counter_show_battery_voltage) to showBatteryVoltage,
                        stringResource(R.string.fps_short_cpu_cores) to showCpuCores,
                        stringResource(R.string.fps_short_gpu_freq) to showGpuFreq,
                        stringResource(R.string.fps_short_battery_level) to showBatteryLevel,
                    )

                    // Сетка 2 колонки
                    val chunked = modules.chunked(2)
                    chunked.forEach { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                        ) {
                            pair.forEachIndexed { idx, (label, checked) ->
                                FpsCheckboxItem(
                                    label = label,
                                    checked = checked,
                                    enabled = enabled,
                                    modifier = Modifier.weight(1f),
                                    onCheckedChange = { newVal ->
                                        when (label) {
                                            ctx.getString(R.string.fps_counter_show_fps) -> showFps = newVal
                                            ctx.getString(R.string.fps_counter_show_ram) -> showRam = newVal
                                            ctx.getString(R.string.fps_counter_show_gpu) -> showGpu = newVal
                                            ctx.getString(R.string.fps_counter_show_gpu_load) -> showGpuLoad = newVal
                                            ctx.getString(R.string.fps_counter_show_gpu_temp) -> showGpuTemp = newVal
                                            ctx.getString(R.string.fps_counter_show_cpu_temp) -> showCpuTemp = newVal
                                            ctx.getString(R.string.fps_counter_show_renderer) -> showRenderer = newVal
                                            ctx.getString(R.string.fps_counter_show_frame_time_graph) -> showFrameTimeGraph = newVal
                                            ctx.getString(R.string.fps_counter_show_battery_temp) -> showBatteryTemp = newVal
                                            ctx.getString(R.string.fps_counter_show_battery_voltage) -> showBatteryVoltage = newVal
                                            ctx.getString(R.string.fps_short_cpu_cores) -> showCpuCores = newVal
                                            ctx.getString(R.string.fps_short_gpu_freq) -> showGpuFreq = newVal
                                            ctx.getString(R.string.fps_short_battery_level) -> showBatteryLevel = newVal
                                        }
                                    }
                                )
                            }
                            // Заполнитель если нечётное
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                    // ── Горизонтальная компоновка ──
                    FpsSectionHeader(
                        title = stringResource(R.string.fps_counter_orientation_title),
                        icon = Icons.Filled.ViewColumn
                    )
                    FpsCompactSwitchRow(
                        label = stringResource(R.string.fps_counter_horizontal_layout),
                        checked = horizontalLayout,
                        enabled = enabled,
                        onCheckedChange = { horizontalLayout = it }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                    // ── Прозрачность фона ──
                    FpsSectionHeader(
                        title = stringResource(R.string.fps_counter_background_opacity_title),
                        icon = Icons.Filled.Opacity
                    )
                    FpsSliderRow(
                        label = "${((backgroundOpacity / 255f) * 100).toInt()}%",
                        value = backgroundOpacity,
                        onValueChange = { backgroundOpacity = it },
                        valueRange = 0f..255f,
                        enabled = enabled
                    )

                    // ── Масштаб ──
                    FpsSectionHeader(
                        title = stringResource(R.string.fps_counter_scale_title),
                        icon = Icons.Filled.ZoomIn
                    )
                    FpsSliderRow(
                        label = "${counterScale.toInt()}%",
                        value = counterScale,
                        onValueChange = { counterScale = it },
                        valueRange = 60f..200f,
                        enabled = enabled
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                    // ── Стиль оформления ──
                    FpsSectionHeader(
                        title = stringResource(R.string.fps_counter_style_title),
                        icon = Icons.Filled.Palette
                    )
                    FpsStyleSelectionRow(
                        selectedStyle = counterStyle,
                        onStyleSelected = { counterStyle = it },
                        enabled = enabled
                    )
                    FpsCompactSwitchRow(
                        label = stringResource(R.string.fps_counter_white_fonts),
                        checked = whiteFonts,
                        enabled = enabled,
                        onCheckedChange = { whiteFonts = it }
                    )
                }

                HorizontalDivider()

                // ── Кнопки ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    GlassPill(
                        onClick = onDismiss,
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    GlassSolidPill(
                        onClick = {
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
                        config.setModuleVisible(FpsCounterConfig.Module.CPU_CORES, showCpuCores)
                        config.setModuleVisible(FpsCounterConfig.Module.GPU_FREQ, showGpuFreq)
                        config.setModuleVisible(FpsCounterConfig.Module.BATTERY_LEVEL, showBatteryLevel)
                        config.setHorizontalLayout(horizontalLayout)
                        config.setBackgroundOpacity(backgroundOpacity.toInt())
                        config.setCounterScale(counterScale.toInt().coerceAtLeast(60))
                        config.setFpsLimit(fpsLimit.toInt())
                        config.setCounterStyle(counterStyle)
                        config.setWhiteFonts(whiteFonts)
                        onConfigChanged()
                        onDismiss()
                    },
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

// ── Вспомогательные компоненты ──

@Composable
private fun FpsSectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun FpsCompactSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        GlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun FpsCheckboxItem(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            maxLines = 2,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

@Composable
private fun FpsSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ,
            colors = glassSliderColors())
    }
}

@Composable
private fun FpsStyleSelectionRow(
    selectedStyle: Int,
    onStyleSelected: (Int) -> Unit,
    enabled: Boolean
) {
    val styles = listOf(
        stringResource(R.string.fps_counter_style_default),
        stringResource(R.string.fps_counter_style_cyber),
        stringResource(R.string.fps_counter_style_retro),
        stringResource(R.string.fps_counter_style_glass),
        stringResource(R.string.fps_counter_style_winlator_ludashi),
        stringResource(R.string.fps_counter_style_gamenative),
        stringResource(R.string.fps_counter_style_mangohud)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        styles.chunked(2).forEachIndexed { rowIndex, rowStyles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowStyles.forEachIndexed { columnIndex, styleName ->
                    val index = rowIndex * 2 + columnIndex
                    val isSelected = selectedStyle == index
                    GlassSolidPill(
                        onClick = { onStyleSelected(index) },
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(styleName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                if (rowStyles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
