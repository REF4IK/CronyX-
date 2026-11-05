package com.winlator.cmod.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.GlassTokens

import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.GlassSolidPill
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.container.Container
import com.winlator.cmod.ui.components.SectionCard

/**
 * Экран просмотра информации о контейнере (только чтение).
 * Редактирование пока доступно через FragmentHostScreen.
 */
@Composable
fun ContainerDetailScreen(
    container: Container?,
    onBack: () -> Unit = {},
) {
    if (container == null) {
        PlaceholderScreen(title = "Контейнер не найден")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Заголовок с именем контейнера
        Text(
            text = container.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "ID: ${container.id}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Информация о контейнере
        SectionCard(title = "Информация", icon = Icons.Filled.Info) {
            InfoRow("Размер экрана", container.screenSize.ifEmpty { "1280x720" })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            InfoRow("Аудио драйвер", container.audioDriver.ifEmpty { "PulseAudio" })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            InfoRow("Драйвер графики", container.graphicsDriver.ifEmpty { "Wrapper" })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            InfoRow("DX Wrapper", container.getDXWrapper().ifEmpty { "DXVK" })
        }

        // Переменные окружения
        SectionCard(title = "Переменные окружения", icon = Icons.Filled.Code) {
            Surface(modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(GlassTokens.InnerRadius),
                color = glassSheetFill(), contentColor = glassTitleColor(),
                tonalElevation = 0.dp,
                border = glassCardBorder(),
            ) {
                Text(
                    text = container.envVars.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Кнопка назад
        GlassSolidPill(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Назад к списку")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
        )
    }
}
