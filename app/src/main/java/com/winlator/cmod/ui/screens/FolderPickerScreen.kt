package com.winlator.cmod.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.GlassSurface
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassBadgeFill
import com.winlator.cmod.ui.components.glassChevronColor
import com.winlator.cmod.ui.components.glassDescColor
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassTitleColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Стеклянный пикер папок: выбор каталога сохранений и т.п.
 * Замена легаси-экрана activity_file_picker; контракт тот же —
 * навигация по подпапкам от стартовой, «Подтвердить выбор» отдаёт текущую.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    initialDir: File,
    onBack: () -> Unit,
    onPick: (File) -> Unit,
) {
    var currentDir by remember { mutableStateOf(initialDir) }
    var dirs by remember { mutableStateOf<List<File>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Содержимое папки: только каталоги
    LaunchedEffect(currentDir) {
        dirs = withContext(Dispatchers.IO) {
            currentDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        }
    }

    // Назад: подъём по дереву, с корня — выход
    val upDir = currentDir.parentFile
    BackHandler {
        if (upDir != null && currentDir.absolutePath != initialDir.absolutePath) {
            currentDir = upDir
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.select_directory)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (upDir != null && currentDir.absolutePath != initialDir.absolutePath) currentDir = upDir
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = glassTitleColor(),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = GlassTokens.RowPaddingH),
        ) {
            // Хлебные крошки от стартовой папки
            val relPath = currentDir.absolutePath
                .removePrefix(initialDir.absolutePath)
                .trim('/', '\\')
            val parts = if (relPath.isEmpty()) emptyList() else relPath.split('/')

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { currentDir = initialDir }) {
                    Text(
                        initialDir.name,
                        color = glassAccent(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                parts.forEachIndexed { i, part ->
                    Text("›", color = glassChevronColor())
                    TextButton(onClick = {
                        currentDir = File(initialDir, parts.subList(0, i + 1).joinToString("/"))
                    }) {
                        Text(
                            part,
                            color = glassAccent(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (dirs.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.game_import_empty),
                        color = glassDescColor(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(dirs, key = { it.absolutePath }) { dir ->
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentDir = dir },
                            shape = RoundedCornerShape(GlassTokens.InnerRadius),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(glassBadgeFill()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                                Text(
                                    text = dir.name,
                                    color = glassTitleColor(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            GlassSolidPill(
                onClick = { onPick(currentDir) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.confirm_selection),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
