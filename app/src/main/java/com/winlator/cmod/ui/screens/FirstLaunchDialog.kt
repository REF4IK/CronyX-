package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.theme.ThemePrefs
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.R
import com.winlator.cmod.ui.theme.getColorSchemeFor

/**
 * Диалог первого запуска приложения.
 * Появляется после завершения установки ImageFs и предлагает выбрать тему (светлую или тёмную).
 * Пользователь может изменить тему позже в настройках.
 */
@Composable
fun FirstLaunchDialog(
    preferences: SharedPreferences,
    isDarkMode: Boolean,
    onThemeSelected: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // Берём настройки темы один раз — диалог только для первого запуска
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = MmkvPreferences()
    val currentThemeId = prefs.getString(ThemePrefs.THEME_ID, ThemePrefs.DEFAULT_THEME_ID) ?: ThemePrefs.DEFAULT_THEME_ID
    val currentCustomColor = prefs.getInt("custom_theme_color", 0xFF1A6C59.toInt())

    val lightScheme = remember(currentThemeId, currentCustomColor) {
        getColorSchemeFor(currentThemeId, false, currentCustomColor)
    }
    val darkScheme = remember(currentThemeId, currentCustomColor) {
        getColorSchemeFor(currentThemeId, true, currentCustomColor)
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            // Диалог перекрывает экран: почти непрозрачное стекло,
            // иначе сквозь панель просвечивает текст и кнопки под ним
            colors = CardDefaults.cardColors(containerColor = glassSheetFill(), contentColor = glassTitleColor()),
            border = glassCardBorder(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Заголовок
                Text(
                    text = stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Приветствие
                Text(
                    text = stringResource(R.string.welcome_greeting),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Выбор темы: Светлая / Тёмная
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Карточка светлой темы
                    ThemeOptionCard(
                        title = stringResource(R.string.theme_light),
                        emoji = "\u2600\uFE0F", // ☀️
                        description = stringResource(R.string.theme_light_desc),
                        isSelected = !isDarkMode,
                        colors = ThemeOptionColors(
                            background = lightScheme.background,
                            cardBackground = lightScheme.surface,
                            primary = lightScheme.primary,
                            text = lightScheme.onSurface,
                            textSecondary = lightScheme.onSurfaceVariant,
                            border = lightScheme.primary,
                        ),
                        onClick = { onThemeSelected(false) },
                        modifier = Modifier.weight(1f),
                    )

                    // Карточка тёмной темы
                    ThemeOptionCard(
                        title = stringResource(R.string.theme_dark),
                        emoji = "\uD83C\uDF19", // 🌙
                        description = stringResource(R.string.theme_dark_desc),
                        isSelected = isDarkMode,
                        colors = ThemeOptionColors(
                            background = darkScheme.background,
                            cardBackground = darkScheme.surface,
                            primary = darkScheme.primary,
                            text = darkScheme.onSurface,
                            textSecondary = darkScheme.onSurfaceVariant,
                            border = darkScheme.primary,
                        ),
                        onClick = { onThemeSelected(true) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Кнопка подтверждения
                GlassSolidPill(
                    onClick = {
                        // Сохраняем выбор навсегда
                        preferences.edit()
                            .putBoolean("dark_mode", isDarkMode)
                            .putBoolean("first_launch_completed", true)
                            .apply()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = stringResource(R.string.start_using),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Подпись про настройки
                Text(
                    text = stringResource(R.string.can_change_in_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class ThemeOptionColors(
    val background: Color,
    val cardBackground: Color,
    val primary: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
)

/**
 * Карточка выбора темы с предпросмотром цветов.
 */
@Composable
private fun ThemeOptionCard(
    title: String,
    emoji: String,
    description: String,
    isSelected: Boolean,
    colors: ThemeOptionColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) colors.border else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            ),
        shape = RoundedCornerShape(GlassTokens.CardRadius),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp,
        ),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBackground,
        ),
        border = glassCardBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Emoji-превью
            Text(
                text = emoji,
                fontSize = 32.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Название темы
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Мини-предпросмотр: блоки цветов
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Primary color
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                )
                // Surface card
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.cardBackground)
                        .border(1.dp, colors.textSecondary.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                )
                // Text color sample
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.text),
                )
            }
        }

        // Описание
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.cardBackground)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}
