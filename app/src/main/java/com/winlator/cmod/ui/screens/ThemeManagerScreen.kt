package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSwitch
import com.winlator.cmod.ui.components.GlassChevron
import com.winlator.cmod.ui.components.GlassSurface
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassGroupDivider
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.ui.theme.ThemePrefs
import com.winlator.cmod.ui.theme.ThemesList

/**
 * Экран «Менеджер тем»: выбор пресета, режим темы, динамический цвет (Material You).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeManagerScreen(
    preferences: SharedPreferences?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = preferences ?: MmkvPreferences()

    var themeId by remember { mutableStateOf(prefs.getString(ThemePrefs.THEME_ID, ThemePrefs.DEFAULT_THEME_ID) ?: ThemePrefs.DEFAULT_THEME_ID) }
    var themeMode by remember { mutableStateOf(ThemePrefs.resolveThemeMode(prefs)) }
    var dynamicColor by remember { mutableStateOf(prefs.getBoolean(ThemePrefs.DYNAMIC_COLOR, false)) }
    var showThemeModeDialog by remember { mutableStateOf(false) }

    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun saveBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

    val themeModeOptions = listOf(
        ThemePrefs.THEME_MODE_SYSTEM to ctx.getString(com.winlator.cmod.R.string.theme_mode_system),
        ThemePrefs.THEME_MODE_LIGHT to ctx.getString(com.winlator.cmod.R.string.theme_mode_light),
        ThemePrefs.THEME_MODE_DARK to ctx.getString(com.winlator.cmod.R.string.theme_mode_dark),
    )
    fun themeModeLabel(v: String) = themeModeOptions.firstOrNull { it.first == v }?.second ?: ctx.getString(com.winlator.cmod.R.string.theme_mode_system)

    fun themeNameRes(id: String): Int = when (id) {
        "gamehub" -> com.winlator.cmod.R.string.theme_gamehub        "midnight" -> com.winlator.cmod.R.string.theme_midnight
        "cyberpunk" -> com.winlator.cmod.R.string.theme_cyberpunk
        "royal" -> com.winlator.cmod.R.string.theme_royal
        "dracula" -> com.winlator.cmod.R.string.theme_dracula
        "frost" -> com.winlator.cmod.R.string.theme_frost
        "forest" -> com.winlator.cmod.R.string.theme_forest
        "ocean" -> com.winlator.cmod.R.string.theme_ocean
        "sakura" -> com.winlator.cmod.R.string.theme_sakura
        "sunset" -> com.winlator.cmod.R.string.theme_sunset
        "matrix" -> com.winlator.cmod.R.string.theme_matrix
        "monochrome" -> com.winlator.cmod.R.string.theme_monochrome
        "chocolate" -> com.winlator.cmod.R.string.theme_chocolate
        "custom" -> com.winlator.cmod.R.string.theme_custom
        else -> com.winlator.cmod.R.string.theme_default
    }

    BackHandler(onBack = onBack)

    // Непрозрачная подложка из обоев: вложенный экран скрывает предыдущее меню
    val wpLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Box(modifier = Modifier.fillMaxSize()) {
    com.winlator.cmod.ui.screens.WallpaperLayer(
        path = prefs.getString(com.winlator.cmod.ui.theme.ThemePrefs.UI_WALLPAPER, "") ?: "",
        blur = prefs.getInt(com.winlator.cmod.ui.theme.ThemePrefs.UI_WALLPAPER_BLUR, 20),
        darken = prefs.getInt(com.winlator.cmod.ui.theme.ThemePrefs.UI_WALLPAPER_DARKEN, 40),
        scale = prefs.getFloat(com.winlator.cmod.ui.screens.wallpaperScaleKey(wpLandscape), 1f),
        offsetRatioX = prefs.getFloat(com.winlator.cmod.ui.screens.wallpaperOffsetXKey(wpLandscape), 0f),
        offsetRatioY = prefs.getFloat(com.winlator.cmod.ui.screens.wallpaperOffsetYKey(wpLandscape), 0f),
        modifier = Modifier.fillMaxSize(),
        allowVideo = false,
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.winlator.cmod.R.string.theme_manager)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Настройки режима
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(com.winlator.cmod.R.string.theme_mode),
                    subtitle = themeModeLabel(themeMode),
                    onClick = { showThemeModeDialog = true }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.AutoAwesome,
                    title = stringResource(com.winlator.cmod.R.string.dynamic_color),
                    checked = dynamicColor,
                    onCheckedChange = {
                        dynamicColor = it
                        saveBool(ThemePrefs.DYNAMIC_COLOR, it)
                    }
                )
            }

            // Список пресетов
            SettingsCard {
                ThemesList.forEach { theme ->
                    val isSelected = theme.id == themeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                themeId = theme.id
                                saveString("theme_id", theme.id)
                            }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(theme.primaryColor, RoundedCornerShape(5.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(5.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(theme.accentColor, RoundedCornerShape(5.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(5.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(themeNameRes(theme.id)),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                themeId = theme.id
                                saveString("theme_id", theme.id)
                            }
                        )
                    }
                    if (theme != ThemesList.last()) {
                        // Разделитель рисует сама строка внутри GlassSurface (glassGroupDivider),
    // явная линия здесь дублировалась бы.
    Spacer(Modifier.height(0.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showThemeModeDialog) {
        ChoiceDialog(
            title = stringResource(com.winlator.cmod.R.string.theme_mode),
            options = themeModeOptions,
            selected = themeMode,
            onDismiss = { showThemeModeDialog = false },
            onSelect = { value ->
                themeMode = value
                saveString(ThemePrefs.THEME_MODE, value)
                saveBool("dark_mode", value == ThemePrefs.THEME_MODE_DARK)
                showThemeModeDialog = false
            }
        )
    }
    } // Box с непрозрачной подложкой
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun SettingsClickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
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
private fun SettingsCheckRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            Column {
                options.forEach { (value, label) ->
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
