package com.winlator.cmod.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.core.EnvVars
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassFieldColors
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassTitleColor

/**
 * Пользовательские env-переменные Box64/FEX, задаваемые в настройках.
 * Хранение: MMKV "<prefix>_user_env_vars" в формате "KEY=VALUE KEY=VALUE" (как EnvVars).
 * В редакторе пресета (EditPresetDialog) показываются как дефолт, пресет может переопределить.
 */

fun loadUserEnvVars(prefix: String): List<Pair<String, String>> {
    val raw = MmkvPreferences().getString("${prefix}_user_env_vars", "") ?: ""
    if (raw.isEmpty()) return emptyList()
    val envVars = EnvVars(raw)
    return envVars.map { it to envVars.get(it) }
}

fun saveUserEnvVars(prefix: String, vars: List<Pair<String, String>>) {
    val value = vars.filter { it.first.isNotEmpty() }.joinToString(" ") { "${it.first}=${it.second}" }
    MmkvPreferences().edit().putString("${prefix}_user_env_vars", value).apply()
}

@Composable
fun UserEnvVarsDialog(prefix: String, onDismiss: () -> Unit) {
    var vars by remember(prefix) { mutableStateOf(loadUserEnvVars(prefix)) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.85f),
            color = glassSheetFill(),
            contentColor = glassTitleColor(),
            tonalElevation = 0.dp,
            border = glassCardBorder(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Text(
                    text = (if (prefix == "box64") "Box64" else "FEXCore") + " — " + stringResource(R.string.environment_variables),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                GlassSolidPill(
                    onClick = {
                        editingIndex = -1
                        showEditDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Vars list
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (vars.isEmpty()) {
                        Text(
                            stringResource(R.string.no_env_vars),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            vars.forEachIndexed { index, (name, value) ->
                                Card(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editingIndex = index
                                            showEditDialog = true
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
                                            saveUserEnvVars(prefix, vars)
                                        }) {
                                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        var varName by remember { mutableStateOf(if (editingIndex >= 0) vars[editingIndex].first else "") }
        var varValue by remember { mutableStateOf(if (editingIndex >= 0) vars[editingIndex].second else "") }
        AlertDialog(
            onDismissRequest = { showEditDialog = false; editingIndex = -1 },
            title = { Text(if (editingIndex >= 0) stringResource(R.string.edit) else stringResource(R.string.add_env_var)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = varName,
                        onValueChange = { varName = it.trim().replace(" ", "") },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    OutlinedTextField(
                        value = varValue,
                        onValueChange = { varValue = it },
                        label = { Text(stringResource(R.string.value)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (varName.isNotEmpty()) {
                        val newVars = vars.toMutableList()
                        if (editingIndex >= 0) {
                            newVars[editingIndex] = varName to varValue
                        } else {
                            // Добавление/замена по имени: повторное имя обновляет значение
                            val existing = newVars.indexOfFirst { it.first == varName }
                            if (existing >= 0) newVars[existing] = varName to varValue else newVars.add(varName to varValue)
                        }
                        vars = newVars
                        saveUserEnvVars(prefix, vars)
                    }
                    showEditDialog = false
                    editingIndex = -1
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false; editingIndex = -1 }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
