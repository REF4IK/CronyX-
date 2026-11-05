package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSectionHeader
import com.winlator.cmod.ui.components.glassAccent

import androidx.compose.ui.graphics.Color
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSolidPill
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassCardBorder
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.R
import com.winlator.cmod.core.GPUPerformanceManager
import com.winlator.cmod.core.GPUInformation

/**
 * Полный перенос GPUPerformanceDialog.java на Jetpack Compose.
 * Использует строки из R.string.* (подключены переводы values/values-ru/values-zh).
 * Логика 1:1 как в оригинале: режимы, информация о GPU, Apply/Restore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GPUPerformanceScreen(
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = MmkvPreferences()
    val gpuManager = remember { GPUPerformanceManager(ctx) }

    // Режимы производительности из enum PerformanceMode (порядок как в оригинале)
    val modes = remember { GPUPerformanceManager.PerformanceMode.values().toList() }
    val modeLabels = remember(modes) { modes.map { ctx.getString(it.getDisplayNameResId()) } }

    // Сохранённый режим (default если нет)
    val savedModeValue = remember { prefs.getString("gpu_performance_mode", "default") ?: "default" }
    var selectedModeIndex by remember {
        mutableStateOf(modes.indexOfFirst { it.getValue() == savedModeValue }.coerceAtLeast(0))
    }

    // Информация о GPU (обновляемая)
    var gpuInfoText by remember { mutableStateOf("") }
    var currentFreqText by remember { mutableStateOf("") }
    var maxFreqText by remember { mutableStateOf("") }
    var applyEnabled by remember { mutableStateOf(true) }
    var restoreEnabled by remember { mutableStateOf(true) }

    fun updateGPUInfo() {
        try {
            val status = gpuManager.getGPUStatus()
            val renderer = try { GPUInformation.getRenderer() } catch (_: Exception) { "Unknown" }
            val governor = status.governor ?: ctx.getString(R.string.gpu_governor_unknown)
            gpuInfoText = ctx.getString(R.string.gpu_info_format, renderer, governor)

            currentFreqText = if (status.currentFreq > 0) {
                ctx.getString(R.string.gpu_current_freq_format, status.getFrequencyMHz(), status.getUsagePercent())
            } else {
                ctx.getString(R.string.gpu_current_freq_unavailable)
            }

            maxFreqText = if (status.maxFreq > 0) {
                ctx.getString(R.string.gpu_max_freq_format, status.getMaxFrequencyMHz())
            } else {
                ctx.getString(R.string.gpu_max_freq_unavailable)
            }
        } catch (e: Exception) {
            gpuInfoText = ctx.getString(R.string.gpu_info_format, "Unknown", ctx.getString(R.string.gpu_governor_unknown))
            currentFreqText = ctx.getString(R.string.gpu_current_freq_unavailable)
            maxFreqText = ctx.getString(R.string.gpu_max_freq_unavailable)
        }
    }

    // Первичная инициализация: проверка доступности + загрузка инфо (как setupViews в оригинале)
    LaunchedEffect(Unit) {
        if (!gpuManager.isGPUControlAvailable()) {
            if (gpuManager.isNonRootOptimizationAvailable()) {
                Toast.makeText(ctx, ctx.getString(R.string.gpu_control_root_unavailable), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(ctx, ctx.getString(R.string.gpu_control_unavailable), Toast.LENGTH_LONG).show()
                applyEnabled = false
                restoreEnabled = false
            }
        } else if (!gpuManager.isAdrenoGPU()) {
            Toast.makeText(ctx, ctx.getString(R.string.gpu_non_adreno_warning), Toast.LENGTH_LONG).show()
        }
        updateGPUInfo()
    }

    fun applyPerformanceMode() {
        val mode = modes[selectedModeIndex]
        val success = try { gpuManager.applyPerformanceMode(mode) } catch (_: Exception) { false }
        if (success) {
            prefs.edit().putString("gpu_performance_mode", mode.getValue()).apply()
            Toast.makeText(
                ctx,
                ctx.getString(R.string.gpu_performance_applied, ctx.getString(mode.getDisplayNameResId())),
                Toast.LENGTH_SHORT
            ).show()
            updateGPUInfo()
        } else {
            Toast.makeText(ctx, ctx.getString(R.string.gpu_performance_apply_failed), Toast.LENGTH_LONG).show()
        }
    }

    fun restoreOriginalSettings() {
        val success = try { gpuManager.restoreOriginalSettings() } catch (_: Exception) { false }
        if (success) {
            prefs.edit().putString("gpu_performance_mode", "default").apply()
            selectedModeIndex = 0
            Toast.makeText(ctx, ctx.getString(R.string.gpu_settings_restored), Toast.LENGTH_SHORT).show()
            updateGPUInfo()
        } else {
            Toast.makeText(ctx, ctx.getString(R.string.gpu_settings_restore_failed), Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.configure_gpu_performance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(GlassTokens.ListGap),
        ) {
            // Информация о GPU
            // Референс: название секции с иконкой стоит НАД карточкой, внутри — только данные.
            GlassSectionHeader(
                title = stringResource(R.string.configure_gpu_performance),
                action = {
                    Icon(Icons.Filled.Memory, null, tint = glassAccent())
                },
            )
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                border = glassCardBorder(),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(gpuInfoText, style = MaterialTheme.typography.bodyMedium)
                    Text(currentFreqText, style = MaterialTheme.typography.bodyMedium)
                    Text(maxFreqText, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Выбор режима производительности
            GlassSectionHeader(title = stringResource(R.string.gpu_performance_mode_label))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                border = glassCardBorder(),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    modes.forEachIndexed { index, mode ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModeIndex == index,
                                onClick = { selectedModeIndex = index }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(modeLabels[index], style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // Кнопки Apply / Restore
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassSolidPill(
                    onClick = { applyPerformanceMode() },
                    enabled = applyEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.apply))
                }
                GlassPill(
                    onClick = { restoreOriginalSettings() },
                    enabled = restoreEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.restore))
                }
            }
        }
    }
}
