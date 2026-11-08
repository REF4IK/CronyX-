package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSwitch
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.box86_64.Box86_64PresetManager
import com.winlator.cmod.core.EnvVars
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.fexcore.FEXCorePresetManager
import org.json.JSONArray
import java.util.Locale

private data class PresetEnvVar(
    val name: String,
    val values: List<String>,
    val defaultValue: String,
    val toggleSwitch: Boolean,
    val editText: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPresetDialog(
    prefix: String,
    presetId: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val isBox64 = prefix == "box64"

    // Is it a readonly preset?
    val readonly = remember(prefix, presetId) {
        if (presetId == null) false
        else {
            if (isBox64) {
                val p = Box86_64PresetManager.getPreset(prefix, context, presetId)
                p != null && !p.isCustom
            } else {
                val p = FEXCorePresetManager.getPreset(context, presetId)
                p != null && !p.isCustom
            }
        }
    }

    // Load vars definition from JSON
    val vars = remember(prefix, readonly) {
        val varsList = mutableListOf<PresetEnvVar>()
        try {
            val jsonString = FileUtils.readString(context, "${prefix}_env_vars.json")
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val valuesJson = obj.getJSONArray("values")
                val values = List(valuesJson.length()) { valuesJson.getString(it) }
                val defaultValue = obj.getString("defaultValue")
                val toggleSwitch = obj.optBoolean("toggleSwitch", obj.optBoolean("toggleswitch", false))
                val editText = obj.optBoolean("editText", false)
                varsList.add(PresetEnvVar(name, values, defaultValue, toggleSwitch, editText))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Пользовательские переменные из настроек (SettingsScreen -> UserEnvVarsDialog):
        // значение из настроек = дефолт, пресет может переопределить (сохраняется через currentValues).
        // К встроенным (readonly) пресетам не применяются — там их не показываем вовсе.
        if (!readonly) {
            loadUserEnvVars(prefix).forEach { (name, value) ->
                if (varsList.none { it.name == name }) {
                    varsList.add(PresetEnvVar(name, listOf(value), value, false, true))
                }
            }
        }
        varsList
    }

    val envVars = remember(prefix, presetId) {
        if (presetId != null) {
            if (isBox64) {
                Box86_64PresetManager.getEnvVars(prefix, context, presetId)
            } else {
                FEXCorePresetManager.getEnvVars(context, presetId)
            }
        } else null
    }

    // Preset Name State
    var nameState by remember(prefix, presetId) {
        val defaultName = if (presetId != null) {
            if (isBox64) {
                Box86_64PresetManager.getPreset(prefix, context, presetId)?.name ?: ""
            } else {
                FEXCorePresetManager.getPreset(context, presetId)?.name ?: ""
            }
        } else {
            val nextId = if (isBox64) {
                Box86_64PresetManager.getNextPresetId(context, prefix)
            } else {
                FEXCorePresetManager.getNextPresetId(context)
            }
            context.getString(R.string.preset) + "-" + nextId
        }
        mutableStateOf(defaultName)
    }

    // Presets values state map
    val currentValues = remember(prefix, presetId, vars) {
        val map = mutableStateMapOf<String, String>()
        vars.forEach { v ->
            val savedVal = if (envVars != null && envVars.has(v.name)) envVars.get(v.name) else v.defaultValue
            map[v.name] = savedVal
        }
        map
    }

    var helpDialogText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.95f),
            // tonalElevation подмешивал primary-тонировку: на surface
            // #121215 это давало сине-зелёную панель вместо стекла
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
                    text = StringUtils.getString(context, "${prefix}_preset") ?: if (isBox64) "Box64 Preset" else "FEXCore Preset",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Name field
                OutlinedTextField(
                    value = nameState,
                    onValueChange = { nameState = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    enabled = !readonly,
                    modifier = Modifier.fillMaxWidth()
                ,
                    shape = GlassFieldShape,
                    colors = glassFieldColors())

                Spacer(modifier = Modifier.height(16.dp))

                // Environment Variables Title
                Text(
                    text = stringResource(R.string.environment_variables),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Variables List
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        vars.forEach { variable ->
                            val currentValue = currentValues[variable.name] ?: variable.defaultValue
                            val helpText = remember(variable.name) {
                                val suffix = variable.name.replace(prefix.uppercase(Locale.ENGLISH) + "_", "").lowercase(Locale.ENGLISH)
                                StringUtils.getString(context, "box86_64_env_var_help__$suffix")
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                                shape = RoundedCornerShape(GlassTokens.CardRadius),
                                modifier = Modifier.fillMaxWidth(),
                                border = glassCardBorder(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    // Row with Name and Help button
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = variable.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (helpText != null) {
                                            IconButton(
                                                onClick = { helpDialogText = helpText },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.HelpOutline,
                                                    contentDescription = "Help",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Control based on variable type
                                    when {
                                        variable.toggleSwitch -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (currentValue == "1") "Enabled" else "Disabled",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                GlassSwitch(
                                                    checked = currentValue == "1",
                                                    onCheckedChange = { checked ->
                                                        currentValues[variable.name] = if (checked) "1" else "0"
                                                    },
                                                    enabled = !readonly
                                                )
                                            }
                                        }
                                        variable.editText -> {
                                            OutlinedTextField(
                                                value = currentValue,
                                                onValueChange = { currentValues[variable.name] = it },
                                                singleLine = true,
                                                enabled = !readonly,
                                                modifier = Modifier.fillMaxWidth(),
                                                textStyle = MaterialTheme.typography.bodyMedium
                                            ,
                                                shape = GlassFieldShape,
                                                colors = glassFieldColors())
                                        }
                                        else -> {
                                            var expanded by remember { mutableStateOf(false) }
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                GlassPill(
                                                    onClick = { expanded = true },
                                                    enabled = !readonly,
                                                    modifier = Modifier.fillMaxWidth(),
                                                ) {
                                                    Text(
                                                        text = currentValue,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (readonly) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = expanded,
                                                    onDismissRequest = { expanded = false },
                                                    modifier = Modifier.fillMaxWidth(0.85f)
                                                ) {
                                                    variable.values.forEach { option ->
                                                        DropdownMenuItem(
                                                            text = { Text(option, style = MaterialTheme.typography.bodyMedium) },
                                                            onClick = {
                                                                currentValues[variable.name] = option
                                                                expanded = false
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    GlassSolidPill(
                        onClick = {
                            val finalName = nameState.trim().replace("[,|]+".toRegex(), "")
                            if (finalName.isNotEmpty()) {
                                val envVarsResult = EnvVars()
                                currentValues.forEach { (k, v) ->
                                    envVarsResult.put(k, v)
                                }
                                if (isBox64) {
                                    Box86_64PresetManager.editPreset(prefix, context, presetId, finalName, envVarsResult)
                                } else {
                                    FEXCorePresetManager.editPreset(context, presetId, finalName, envVarsResult)
                                }
                                onConfirm()
                            }
                        },
                        enabled = !readonly && nameState.trim().isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }

    // Help Dialog overlay
    if (helpDialogText != null) {
        AlertDialog(
            onDismissRequest = { helpDialogText = null },
            confirmButton = {
                TextButton(onClick = { helpDialogText = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text("Information") },
            text = { Text(helpDialogText!!) }
        )
    }
}
