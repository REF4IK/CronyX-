package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassChevronColor

import com.winlator.cmod.ui.components.GlassFieldShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.xenvironment.ImageFs
import com.winlator.cmod.xenvironment.XEnvironment
import com.winlator.cmod.xenvironment.components.BionicProgramLauncherComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var outputText by remember { mutableStateOf("Initializing terminal...\n") }
    var commandInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }

    // Initialize environment objects
    val (xEnv, launcher) = remember {
        val imageFs = ImageFs.find(ctx)
        if (!imageFs.isValid) {
            outputText = "Error: Invalid ImageFs.\n"
            Pair(null, null)
        } else {
            val env = XEnvironment(ctx, imageFs)
            val contentsManager = ContentsManager(ctx)
            val l = BionicProgramLauncherComponent(contentsManager, null, null)
            env.addComponent(l)
            
            // Set execute permissions
            val binDir = File(imageFs.rootDir, "usr/bin")
            if (binDir.isDirectory) {
                binDir.listFiles()?.forEach { file ->
                    FileUtils.chmod(file, 493) // 0755 octal is 493 decimal
                }
            }
            outputText = "Terminal initialized.\nEnter command to begin.\n"
            Pair(env, l)
        }
    }

    val scrollState = rememberScrollState()

    // Auto-scroll output when text changes
    LaunchedEffect(outputText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    fun executeCommand(cmd: String) {
        if (cmd.isBlank() || launcher == null) return
        if (cmd == "bash" || cmd == "dash") {
            outputText += "\n$ $cmd\nInteractive shells are unsupported.\n"
            commandInput = ""
            return
        }

        isExecuting = true
        outputText += "\n$ $cmd\n"
        val inputCmd = commandInput
        commandInput = ""

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                launcher.execShellCommand(inputCmd)
            }
            outputText += result + "\n"
            isExecuting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // Simple Top Header Bar (No separate TopAppBar component to avoid extra space)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.terminal),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }

        // Terminal Output Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = outputText,
                    color = Color(0xFF4AF626),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Command Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.terminal_command_hint), color = Color.Gray) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    // Акцент по теме один на весь интерфейс, поэтому бордюр и
                    // каретка берутся от стеклянных токенов, а не зелёного «матрицы»
                    focusedBorderColor = glassAccent(),
                    unfocusedBorderColor = glassChevronColor(),
                    cursorColor = glassAccent()
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { executeCommand(commandInput) }),
                enabled = !isExecuting
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { executeCommand(commandInput) },
                enabled = !isExecuting && commandInput.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color(0xFF4AF626),
                    disabledContentColor = Color.DarkGray
                )
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Run")
            }
        }
    }
}
