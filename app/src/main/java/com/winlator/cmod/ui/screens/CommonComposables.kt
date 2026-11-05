package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.GlassTokens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.container.Container

/**
 * Универсальный placeholder для ещё не реализованных экранов.
 * Используется в OtherScreens.kt.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String = "",
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Универсальный диалог выбора контейнера.
 * Используется в ShortcutsScreen и ImportGameScreen.
 */
@Composable
fun ContainerPickerDialog(
    containers: ArrayList<Container>,
    onDismiss: () -> Unit,
    onPick: (Container) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                color = glassSheetFill(),
                border = glassCardBorder(),
            ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.select_container),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (containers.isEmpty()) {
                    Text(stringResource(R.string.no_containers), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    containers.forEach { c ->
                        TextButton(
                            onClick = { onPick(c) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}
