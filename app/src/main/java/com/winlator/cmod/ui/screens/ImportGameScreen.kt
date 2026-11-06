package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.GlassSurface
import com.winlator.cmod.ui.components.GlassSheetContent
import com.winlator.cmod.ui.components.glassBadgeFill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.glassDescColor
import com.winlator.cmod.ui.components.glassValueColor
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.GameTitleParser
import com.winlator.cmod.core.ShortcutCoverFetcher
import com.winlator.cmod.win32.PEParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private enum class ImportStep { SHEET, BROWSER, CONFIRM, SCANNING, RESULTS }

/**
 * Compose-версия GameImportConfirmActivity.
 * Открывается листом «Добавить»: автопоиск по загрузкам, выбор .exe из папки
 * или прямым файлом; затем confirm-форма как раньше.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportGameScreen(
    initialContainerId: Int = -1,
    onBack: () -> Unit,
    onDone: (Shortcut) -> Unit = {},
) {
    val ctx = LocalContext.current
    val manager = remember { ContainerManager(ctx) }

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var exePath by remember { mutableStateOf("") }
    var exeIcon by remember { mutableStateOf<Bitmap?>(null) }
    var step by remember { mutableStateOf(ImportStep.SHEET) }
    var showContainerPicker by remember { mutableStateOf(false) }
    // Контейнер выбирается в листе «Добавить»; по умолчанию — запрошенный или первый
    var selectedContainer by remember {
        mutableStateOf(manager.getContainerById(initialContainerId) ?: manager.containers.firstOrNull())
    }
    var browserFromResults by remember { mutableStateOf(false) }
    var scanResults by remember { mutableStateOf<List<File>>(emptyList()) }

    BackHandler(enabled = true) {
        when (step) {
            ImportStep.SHEET -> onBack()
            ImportStep.BROWSER -> if (browserFromResults) step = ImportStep.RESULTS else onBack()
            ImportStep.CONFIRM -> step = ImportStep.BROWSER
            else -> onBack()
        }
    }

    when (step) {
        ImportStep.SHEET -> {
            AddGameSheet(
                containerName = selectedContainer?.name ?: "—",
                onClose = onBack,
                onChangeContainer = { showContainerPicker = true },
                onAutoSearch = { step = ImportStep.SCANNING },
                onPickExe = {
                    browserFromResults = false
                    step = ImportStep.BROWSER
                },
            )
        }

        ImportStep.BROWSER -> {
            FileBrowserScreen(
                initialDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                title = stringResource(R.string.select_exe),
                onBack = {
                    if (browserFromResults) step = ImportStep.RESULTS else onBack()
                },
                onFilePicked = { file ->
                    exePath = file.absolutePath
                    // Имя из манифеста Steam/GOG, PE-версии или чистое имя папки/exe
                    name = try {
                        GameTitleParser.suggestName(file)?.takeIf { it.isNotBlank() }
                            ?: file.nameWithoutExtension
                    } catch (_: Throwable) {
                        file.nameWithoutExtension
                    }
                    // Извлекаем иконку
                    exeIcon = try { PEParser.extractIcon(file) } catch (_: Throwable) { null }
                    step = ImportStep.CONFIRM
                },
            )
        }

        ImportStep.CONFIRM -> {
            ImportConfirmUI(
                name = name,
                onNameChange = { if (it.length <= 60) name = it },
                description = description,
                onDescriptionChange = { description = it },
                exePath = exePath,
                exeIcon = exeIcon,
                selectedContainer = selectedContainer,
                onContainerMissing = {
                    AppUtils.showToast(ctx, R.string.select_container_for_import)
                },
                onChangeContainer = { showContainerPicker = true },
                onChangeExe = { step = ImportStep.BROWSER },
                onBack = { step = ImportStep.BROWSER },
                onCancel = onBack,
                onImported = onBack,
            )
        }

        ImportStep.SCANNING -> {
            ScanningScreen(
                onDone = { files ->
                    scanResults = files
                    step = ImportStep.RESULTS
                },
            )
        }

        ImportStep.RESULTS -> {
            ScanResultsScreen(
                files = scanResults,
                container = selectedContainer,
                onContainerMissing = {
                    AppUtils.showToast(ctx, R.string.select_container_for_import)
                },
                onChangeContainer = { showContainerPicker = true },
                onPickFolder = {
                    browserFromResults = true
                    step = ImportStep.BROWSER
                },
                onFinish = onBack,
            )
        }
    }

    // Диалог выбора контейнера
    if (showContainerPicker) {
        ContainerPickerDialog(
            containers = manager.containers,
            onDismiss = { showContainerPicker = false },
            onPick = { c ->
                selectedContainer = c
                showContainerPicker = false
            },
        )
    }
}

/** Лист «Добавить» — как на референсе: автопоиск + выбор из папки. */
@Composable
private fun AddGameSheet(
    containerName: String,
    onClose: () -> Unit,
    onChangeContainer: () -> Unit,
    onAutoSearch: () -> Unit,
    onPickExe: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Затемнение слабое: под листом должен быть виден экран «Ярлыки»
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        GlassSheetContent(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Свои тапы по пустому месту листа не должны закрывать его.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            // Заголовок + крестик
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.add_game_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = glassTitleColor(),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = glassValueColor())
                }
            }

            // Контейнер, в который пойдёт импорт
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clickable(onClick = onChangeContainer),
                shape = RoundedCornerShape(GlassTokens.InnerRadius),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.VideogameAsset, null, tint = glassAccent(), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.container), color = glassTitleColor(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(containerName, color = glassDescColor(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(stringResource(R.string.change), color = glassAccent(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Автопоиск
            Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAutoSearch),
                    shape = RoundedCornerShape(GlassTokens.InnerRadius),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, null, tint = glassAccent(), modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.auto_search), color = glassTitleColor(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.auto_search_hint), color = glassDescColor(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                // Бейдж «Советуем» в углу карточки
                Text(
                    text = stringResource(R.string.badge_recommended),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = (-8).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF57C00))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            // Заголовок секции
            Text(
                text = stringResource(R.string.from_folder_section),
                color = glassTitleColor(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
            )

            // Файл игры (.exe) — единственный способ выбора из папки:
            // отдельная «Папка игры» дублировала его.
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clickable(onClick = onPickExe),
                shape = RoundedCornerShape(GlassTokens.InnerRadius),
            ) {
                SheetActionRow(
                    icon = { Icon(Icons.Filled.Description, null, tint = glassAccent(), modifier = Modifier.size(28.dp)) },
                    title = stringResource(R.string.game_exe_file),
                    subtitle = stringResource(R.string.game_exe_file_hint),
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(glassBadgeFill()),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = glassTitleColor(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = glassDescColor(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = glassValueColor(), modifier = Modifier.size(22.dp))
    }
}

/** Экран сканирования загрузок. Сам запускает поиск и отдаёт результат. */
@Composable
private fun ScanningScreen(onDone: (List<File>) -> Unit) {
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        val results = withContext(Dispatchers.IO) { scanForExes() }
        // Экран сканирования не должен мигать на быстрых телефонах
        val elapsed = System.currentTimeMillis() - started
        if (elapsed < 900) delay(900 - elapsed)
        onDone(results)
    }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = glassValueColor().copy(alpha = 0.55f),
                modifier = Modifier.size(120.dp),
            )
            Text(
                text = stringResource(R.string.scanning_title),
                color = glassTitleColor(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.scanning_hint),
                color = glassDescColor(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ScanResultsScreen(
    files: List<File>,
    container: Container?,
    onContainerMissing: () -> Unit,
    onChangeContainer: () -> Unit,
    onPickFolder: () -> Unit,
    onFinish: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val imported = remember { mutableStateListOf<String>() }
    var importing by remember { mutableStateOf(false) }
    // Где какие exe уже добавлены: путь → имя контейнера ярлыка
    var alreadyMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        alreadyMap = withContext(Dispatchers.IO) { importedExeIds(ctx) }
    }
    val containerName = container?.name
    fun importedInto(file: File): String? = alreadyMap[exeImportId(file).lowercase(Locale.ENGLISH)]
    fun isImportedHere(file: File): Boolean =
        file.absolutePath in imported || importedInto(file) == containerName

    var selected by remember { mutableStateOf(files.map { it.absolutePath }.toHashSet()) }
    LaunchedEffect(alreadyMap, containerName) {
        // В выбранном контейнере уже есть — не предлагаем; в другом — можно добавить
        selected = HashSet(files.filter { !isImportedHere(it) }.map { it.absolutePath })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // Шапка
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.scan_done_title),
                color = glassTitleColor(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.scan_done_hint),
                color = glassDescColor(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Text(
            text = stringResource(R.string.scan_found_count, files.size),
            color = glassTitleColor(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        )

        if (files.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.game_import_empty), color = glassDescColor())
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(files, key = { it.absolutePath }) { file ->
                    val into = importedInto(file)
                    ScanResultRow(
                        file = file,
                        checked = file.absolutePath in selected,
                        imported = isImportedHere(file),
                        statusContainer = into ?: if (file.absolutePath in imported) containerName else null,
                        elsewhereContainer = into?.takeIf { it != containerName },
                        onToggle = { checked ->
                            // Новый set — иначе мутация не триггерит рекомпозицию
                            selected = HashSet(selected).apply {
                                if (checked) add(file.absolutePath) else remove(file.absolutePath)
                            }
                        },
                    )
                }
            }
        }

        // Низ: кнопка импорта и ссылка на папку
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GlassSolidPill(
                onClick = {
                    if (imported.isNotEmpty()) {
                        // Повторное нажатие после импорта = «Готово»
                        onFinish()
                        return@GlassSolidPill
                    }
                    if (selected.isEmpty() || importing) return@GlassSolidPill
                    val target = container
                    if (target == null) {
                        onContainerMissing()
                        return@GlassSolidPill
                    }
                    importing = true
                    scope.launch(Dispatchers.IO) {
                        var count = 0
                        for (path in selected) {
                            val file = File(path)
                            if (!file.exists()) continue
                            val displayName = try {
                                GameTitleParser.suggestName(file)?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                            } catch (_: Throwable) { file.nameWithoutExtension }
                            try {
                                importExeToContainer(ctx, file, target, displayName, "")
                                imported.add(path)
                                count++
                            } catch (_: Exception) {}
                        }
                        withContext(Dispatchers.Main) {
                            importing = false
                            AppUtils.showToast(ctx, ctx.getString(R.string.imported_status) + ": " + count)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = (selected.isNotEmpty() && !importing) || imported.isNotEmpty(),
            ) {
                Text(
                    stringResource(
                        if (imported.isEmpty()) R.string.import_to_library else R.string.scan_done_btn
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TextButton(onClick = onPickFolder, enabled = !importing) {
                Text(stringResource(R.string.scan_pick_folder), color = glassAccent())
            }
        }
    }
}

@Composable
private fun ScanResultRow(
    file: File,
    checked: Boolean,
    imported: Boolean,
    statusContainer: String?,
    elsewhereContainer: String?,
    onToggle: (Boolean) -> Unit,
) {
    var icon by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        icon = withContext(Dispatchers.IO) {
            try { PEParser.extractIcon(file) } catch (_: Throwable) { null }
        }
    }
    val title = remember(file) {
        try {
            GameTitleParser.suggestName(file)?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
        } catch (_: Throwable) { file.nameWithoutExtension }
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GlassTokens.InnerRadius),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !imported) { onToggle(!checked) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(glassBadgeFill()),
                contentAlignment = Alignment.Center,
            ) {
                val currentIcon = icon
                if (currentIcon != null) {
                    Image(
                        bitmap = currentIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(Icons.Filled.Description, null, tint = glassDescColor(), modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = glassTitleColor(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = file.absolutePath,
                    color = glassDescColor(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (imported) {
                // «Импортировано» + в каком контейнере, если он известен
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.imported_status),
                        color = glassDescColor(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (statusContainer != null) {
                        Text(
                            text = stringResource(R.string.in_container_suffix, statusContainer),
                            color = glassDescColor(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(checked = checked, onCheckedChange = onToggle)
                    if (elsewhereContainer != null) {
                        // Уже есть в другом контейнере — но в выбранный добавить можно
                        Text(
                            text = stringResource(R.string.in_container_suffix, elsewhereContainer),
                            color = glassDescColor(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// Мусорные имена exe: инсталляторы, деинсталляторы, JRE, античиты, крэк-тулзы.
private val JUNK_EXE_TOKENS = listOf(
    "setup", "unins", "install", "update", "patch", "redist", "vcredist", "dxsetup",
    "dxwebsetup", "dotnet", "javaw", "java", "javac", "javap", "jawt", "crack", "keygen",
    "activation", "reporter", "license", "unattend", "dedicated", "configurator",
)

// Мусорные папки: распространяемые пакеты и прочее, что не игра.
private val JUNK_DIR_TOKENS = listOf(
    "redist", "commonredist", "directx", "vcredist", "dotnet", "jre", "java",
    "uninstall", "crash", "unsupported", "support",
)

private fun isJunkExe(file: File): Boolean {
    val name = file.nameWithoutExtension.lowercase(Locale.ENGLISH)
    if (JUNK_EXE_TOKENS.any { name.contains(it) }) return true
    // Папки в пути (регистр не важен)
    var dir = file.parentFile
    while (dir != null) {
        val dirName = dir.name.lowercase(Locale.ENGLISH)
        if (JUNK_DIR_TOKENS.any { dirName.contains(it) }) return true
        dir = dir.parentFile
    }
    return false
}

/** Ключ склейки дублей: ACC_dx11.exe и ACC.exe → «acc». */
private fun exeDedupeKey(file: File): String {
    var base = file.nameWithoutExtension.lowercase(Locale.ENGLISH)
    base = base.replace(Regex("\\(\\d+\\)"), " ")           // «game (1)» — копии
    base = base.replace(Regex("[^a-z0-9а-я ]+"), " ")
    val suffixes = listOf("dx11", "dx12", "dx9", "x64", "x86", "win64", "win32", "64bit", "32bit", "final", "launcher")
    val parts = base.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in suffixes }
    val parent = file.parent?.lowercase(Locale.ENGLISH) ?: ""
    return "$parent/${parts.joinToString(" ")}"
}

/**
 * Сканирует частые папки загрузки игр: Downloads (в глубину до 3) и похожие
 * папки в корне накопителя. Возвращает найденные .exe, максимум 100 штук.
 * Мусор (инсталляторы, JRE, редисты) отфильтровывается, дубли схлопываются:
 * из группы остаётся самый большой файл.
 */
private fun scanForExes(): List<File> {
    val candidates = LinkedHashMap<String, File>()
    val root = Environment.getExternalStorageDirectory()
    val roots = mutableListOf<File>()
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (downloads.exists()) roots.add(downloads)
    for (name in listOf("Games", "Game", "Игры", "Download", "Downloads", "Torrent", "torrent")) {
        val dir = File(root, name)
        if (dir.exists() && dir.isDirectory) roots.add(dir)
    }

    var scanned = 0
    fun walk(dir: File, depth: Int) {
        if (depth > 3 || scanned > 6000 || candidates.size >= 300) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (scanned > 6000 || candidates.size >= 300) return
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                walk(child, depth + 1)
            } else {
                scanned++
                if (child.name.endsWith(".exe", ignoreCase = true) && !isJunkExe(child)) {
                    candidates.putIfAbsent(child.absolutePath, child)
                }
            }
        }
    }
    roots.forEach { walk(it, 0) }

    // .exe, лежащие прямо в корне накопителя
    try {
        root.listFiles()?.forEach { child ->
            if (child.isFile && child.name.endsWith(".exe", ignoreCase = true) && !isJunkExe(child)) {
                candidates.putIfAbsent(child.absolutePath, child)
            }
        }
    } catch (_: Throwable) {}

    // Схлопывание дублей: в группе остаётся самый большой exe
    val best = LinkedHashMap<String, File>()
    for (file in candidates.values) {
        val key = exeDedupeKey(file)
        val current = best[key]
        if (current == null || file.length() > current.length()) best[key] = file
    }

    // Вторая склейка — по названию из PE-ресурсов: одна игра, разложенная по
    // подпапкам (ACC.exe, launcher.exe, ...), даёт одинаковый заголовок.
    val byTitle = LinkedHashMap<String, File>()
    for (file in best.values) {
        val title = (try {
            GameTitleParser.suggestName(file)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) { null } ?: file.nameWithoutExtension).lowercase(Locale.ENGLISH)
        val current = byTitle[title]
        if (current == null || file.length() > current.length()) byTitle[title] = file
    }

    return byTitle.values.sortedBy { it.nameWithoutExtension.lowercase(Locale.ENGLISH) }.take(100)
}

/**
 * Идентификатор exe в терминах ярлыка: «D:/путь/файл.exe» — то, что пишется
 * в Exec после «wine ». Используется и при импорте, и при поиске уже
 * добавленных игр, поэтому логика маппинга дисков живёт только здесь.
 */
private fun exeImportId(file: File): String {
    val absolutePath = file.absolutePath
    val externalStoragePath = Environment.getExternalStorageDirectory().absolutePath
    val relativePath = absolutePath.lowercase(Locale.ENGLISH)

    var driveLetter = "D:"
    when {
        relativePath.contains(externalStoragePath.lowercase(Locale.ENGLISH)) -> driveLetter = "D:"
        absolutePath.contains("/imagefs/") -> driveLetter = "Z:"
        absolutePath.contains("/.wine/drive_c/") -> driveLetter = "C:"
    }

    var pathWOutPrefix = absolutePath
    when (driveLetter) {
        "D:" -> {
            pathWOutPrefix = pathWOutPrefix.removePrefix(externalStoragePath).trimStart('/')
            if (pathWOutPrefix.lowercase(Locale.ENGLISH).startsWith("download/")) {
                pathWOutPrefix = pathWOutPrefix.substring(9)
            }
        }
        "Z:" -> {
            val idx = pathWOutPrefix.indexOf("/imagefs/")
            if (idx != -1) pathWOutPrefix = pathWOutPrefix.substring(idx + 9)
        }
        "C:" -> {
            val idx = pathWOutPrefix.indexOf("/.wine/drive_c/")
            if (idx != -1) pathWOutPrefix = pathWOutPrefix.substring(idx + 15)
        }
    }
    return "$driveLetter/$pathWOutPrefix"
}

/** Пути exe, на которые уже есть ярлыки, → имя контейнера ярлыка. */
private fun importedExeIds(ctx: Context): Map<String, String> {
    val ids = HashMap<String, String>()
    val manager = ContainerManager(ctx)
    for (container in manager.containers) {
        val dir = container.desktopDir
        val files = dir?.listFiles { f -> f.name.endsWith(".desktop") } ?: continue
        for (file in files) {
            val text = try { file.readText() } catch (_: Exception) { continue }
            for (line in text.lines()) {
                val idx = line.indexOf(" wine ")
                if (idx == -1) continue
                val id = line.substring(idx + 6).trim()
                if (id.isNotEmpty()) ids[id.lowercase(Locale.ENGLISH)] = container.name
            }
        }
    }
    return ids
}

/** Создаёт .desktop-ярлык игры в контейнере. Общая для confirm-формы и автопоиска. */
private fun importExeToContainer(ctx: Context, exeFile: File, container: Container, displayName: String, description: String) {
    val absolutePath = exeFile.absolutePath
    val fileName = exeFile.name
    val safeName = GameTitleParser.normalizeName(displayName).ifBlank { displayName }
    ShortcutCoverFetcher.clearCoverCache(ctx, safeName)

    val importId = exeImportId(exeFile)
    val driveLetter = importId.substring(0, 2)
    val execPath = importId.substring(3)
    val lastSlash = execPath.lastIndexOf("/")
    val pathDir = if (lastSlash > 0) execPath.substring(0, lastSlash) else ""

    val randomNum = (Math.random() * 10000).toInt()
    val iconName = "${randomNum}_$safeName.0"

    val exeIcon = try { PEParser.extractIcon(exeFile) } catch (_: Throwable) { null }
    if (exeIcon != null) {
        val iconDir = container.getIconsDir(64)
        if (!iconDir.exists()) iconDir.mkdirs()
        FileUtils.saveBitmapToFile(exeIcon, File(iconDir, "$iconName.png"))
    }

    val desktopDir = container.desktopDir
    if (!desktopDir.exists()) desktopDir.mkdirs()
    val file = File(desktopDir, "$safeName.desktop")
    val content = buildString {
        append("[Desktop Entry]\n")
        append("Name=$safeName\n")
        append("Exec=env WINEPREFIX=\"/data/user/0/${MainActivity.PACKAGE_NAME}/files/imagefs/home/xuser/.wine/dosdevices/z:/home/xuser/.wine\" wine $driveLetter/$execPath\n")
        if (description.isNotEmpty()) append("Comment=$description\n")
        append("Type=Application\n")
        append("StartupNotify=true\n")
        append("Path=/data/user/0/${MainActivity.PACKAGE_NAME}/files/imagefs/home/xuser/.wine/dosdevices/${driveLetter.lowercase(Locale.ENGLISH)}/$pathDir\n")
        append("Icon=$iconName\n")
        append("StartupWMClass=${fileName.lowercase(Locale.ENGLISH)}\n")
        append("container_id:${container.id}\n")
    }
    FileUtils.writeString(file, content)
}

/**
 * Confirm-форма: иконка, имя, описание, путь, контейнер.
 * Вынесена из тела ImportGameScreen, чтобы шаги не пересекались по состоянию.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportConfirmUI(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    exePath: String,
    exeIcon: Bitmap?,
    selectedContainer: Container?,
    onContainerMissing: () -> Unit,
    onChangeContainer: () -> Unit,
    onChangeExe: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onImported: () -> Unit,
) {
    val ctx = LocalContext.current
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCoverBrowser by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_game)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Иконка игры (200x112 как в старом GameImportConfirmActivity)
            Box(
                modifier = Modifier
                    .size(200.dp, 112.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x33000000))
                    .clickable { showCoverBrowser = true },
                contentAlignment = Alignment.Center,
            ) {
                val displayIcon = coverBitmap ?: exeIcon
                if (displayIcon != null) {
                    Image(
                        bitmap = displayIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Имя
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("${name.length}/60") },
                shape = GlassFieldShape,
                colors = glassFieldColors())

            // Описание (опционально)
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.description_optional)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = GlassFieldShape,
                colors = glassFieldColors())

            // Путь к exe
            Text(stringResource(R.string.path_to_launch_file), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                border = glassCardBorder(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = exePath,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    TextButton(onClick = onChangeExe) {
                        Text(stringResource(R.string.change))
                    }
                }
            }

            // Контейнер
            Text(stringResource(R.string.container), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onChangeContainer),
                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                border = glassCardBorder(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.VideogameAsset, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(selectedContainer?.name ?: "—", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.change), color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(12.dp))
                GlassSolidPill(
                    onClick = {
                        if (name.isEmpty()) {
                            AppUtils.showToast(ctx, R.string.name_and_exe_required)
                            return@GlassSolidPill
                        }
                        val target = selectedContainer
                        if (target == null) {
                            onContainerMissing()
                            return@GlassSolidPill
                        }
                        try {
                            importExeToContainer(ctx, File(exePath), target, name, description)
                            AppUtils.showToast(ctx, R.string.game_imported)
                            onImported()
                        } catch (e: Exception) {
                            AppUtils.showToast(ctx, "Error: ${e.message}")
                        }
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }

    // FileBrowser для смены/выбора обложки
    if (showCoverBrowser) {
        FileBrowserScreen(
            title = stringResource(R.string.add_cover),
            onBack = { showCoverBrowser = false },
            onFilePicked = { file ->
                try { coverBitmap = BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) {}
                showCoverBrowser = false
            },
        )
    }
}
