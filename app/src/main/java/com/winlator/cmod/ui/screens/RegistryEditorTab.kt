package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassChip

import com.winlator.cmod.ui.components.GlassPill
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.core.WineRegistryEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val HIVE_USER = 0
private const val HIVE_SYSTEM = 1

private val REG_VALUE_TYPES = listOf("String", "Dword", "Hex")

private const val PREFS_SPOOF_IDENTIFIER = "reg_spoof_identifier"
private const val PREFS_SPOOF_VENDOR = "reg_spoof_vendor"
private const val PREFS_SPOOF_MHZ = "reg_spoof_mhz"
private const val PREFS_SPOOF_FEATURESET = "reg_spoof_featureset"

private data class HiveRoot(
    val name: String,
    val hive: Int,
    val path: String = "",
)

private val HIVE_ROOTS = listOf(
    HiveRoot("HKEY_CURRENT_USER", HIVE_USER),
    HiveRoot("HKEY_LOCAL_MACHINE", HIVE_SYSTEM),
    HiveRoot("HKEY_CLASSES_ROOT", HIVE_SYSTEM, "Software\\Classes"),
    HiveRoot("HKEY_USERS", HIVE_USER),
    HiveRoot("HKEY_CURRENT_CONFIG", HIVE_SYSTEM, "System\\CurrentControlSet\\Hardware Profiles\\Current"),
)

private data class CpuSpoofPreset(
    val name: String,
    val identifier: String,
    val vendor: String,
    val mhz: Int,
    val featureSet: String,
)

private val PRESET_ARM = CpuSpoofPreset(
    name = "ARM",
    identifier = "ARMv8 (64-bit) Family 8 Model D82 Revision 1",
    vendor = "ARM",
    mhz = 2016,
    featureSet = "f4",
)

private val PRESET_INTEL = CpuSpoofPreset(
    name = "Intel x86",
    identifier = "Intel64 Family 6 Model 70 Stepping 1",
    vendor = "GenuineIntel",
    mhz = 2016,
    featureSet = "2b7bbfff",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RegistryEditorTab(
    containerRootDir: File?,
    wineVersion: String,
    wineArch: String,
    onToast: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val sp = remember { MmkvPreferences() }
    val scope = rememberCoroutineScope()

    // Корневой экран (Computer) или открытый хайв
    var selectedRoot by remember { mutableStateOf<HiveRoot?>(null) }
    var hive by remember { mutableStateOf(HIVE_USER) }
    var currentPath by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    val regFile = remember(containerRootDir, hive) {
        if (containerRootDir == null) null
        else File(containerRootDir, if (hive == HIVE_USER) ".wine/user.reg" else ".wine/system.reg")
    }
    val hivePrefix = if (hive == HIVE_USER) "HKEY_CURRENT_USER" else "HKEY_LOCAL_MACHINE"

    var values by remember { mutableStateOf<List<WineRegistryEditor.RegValue>>(emptyList()) }
    var fileExists by remember { mutableStateOf(false) }

    LaunchedEffect(regFile, currentPath, refreshKey) {
        if (regFile == null || !regFile.isFile) {
            values = emptyList()
            fileExists = false
            return@LaunchedEffect
        }
        fileExists = true
        WineRegistryEditor(regFile).use { reg ->
            values = reg.getValues(currentPath)
        }
    }

    // Дерево: загруженные подключи по пути + раскрытые узлы
    var loadedChildren by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var expandedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun loadChildren(path: String) {
        if (loadedChildren.containsKey(path)) return
        scope.launch(Dispatchers.IO) {
            val children = regFile?.let { WineRegistryEditor(it).use { r -> r.getSubKeys(path) } } ?: emptyList()
            loadedChildren = loadedChildren + (path to children)
        }
    }

    fun toggleNode(path: String) {
        if (path in expandedPaths) {
            expandedPaths = expandedPaths - path
        } else {
            expandedPaths = expandedPaths + path
            loadChildren(path)
        }
    }

    val visibleTreeNodes = remember(loadedChildren, expandedPaths) {
        val result = mutableListOf<Pair<String, Int>>()
        fun walk(path: String, depth: Int) {
            val children = loadedChildren[path] ?: return
            for (child in children) {
                val childPath = if (path.isEmpty()) child else "$path\\$child"
                result.add(childPath to depth)
                if (childPath in expandedPaths) walk(childPath, depth + 1)
            }
        }
        walk("", 0)
        result
    }

    fun enterRoot(root: HiveRoot) {
        selectedRoot = root
        hive = root.hive
        currentPath = root.path
        loadedChildren = emptyMap()
        expandedPaths = emptySet()
        refreshKey++
        val file = containerRootDir?.let { dir ->
            File(dir, if (root.hive == HIVE_USER) ".wine/user.reg" else ".wine/system.reg")
        }
        scope.launch(Dispatchers.IO) {
            if (file == null || !file.isFile) return@launch
            val newChildren = mutableMapOf<String, List<String>>()
            val newExpanded = mutableSetOf<String>()
            val segments = root.path.split("\\").filter { it.isNotEmpty() }
            var path = ""
            WineRegistryEditor(file).use { reg ->
                newChildren[""] = reg.getSubKeys("")
                if (root.path.isNotEmpty()) newExpanded.add("")
                for (segment in segments) {
                    path = if (path.isEmpty()) segment else "$path\\$segment"
                    newChildren[path] = reg.getSubKeys(path)
                    newExpanded.add(path)
                }
            }
            loadedChildren = newChildren
            expandedPaths = newExpanded
            refreshKey++
        }
    }

    fun exitToComputer() {
        selectedRoot = null
        currentPath = ""
        loadedChildren = emptyMap()
        expandedPaths = emptySet()
        values = emptyList()
    }

    // Поиск
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<String>?>(null) }

    fun runSearch() {
        val q = searchQuery.trim()
        if (q.isEmpty() || regFile == null) return
        scope.launch(Dispatchers.IO) {
            val results = WineRegistryEditor(regFile).use { it.searchKeys(q, 300) }
            searchResults = results
        }
    }

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var editingValue by remember { mutableStateOf<WineRegistryEditor.RegValue?>(null) }
    var addingValue by remember { mutableStateOf(false) }
    var deleteKeyConfirm by remember { mutableStateOf<String?>(null) }
    var deleteValueConfirm by remember { mutableStateOf<WineRegistryEditor.RegValue?>(null) }
    var showSpoofDialog by remember { mutableStateOf(false) }

    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copyToClipboard(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("registry", text))
        onToast(ctx.getString(R.string.registry_copied, text))
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null || regFile == null) return@rememberLauncherForActivityResult
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                onToast(ctx.getString(R.string.registry_import_failed))
                return@rememberLauncherForActivityResult
            }
            val content = decodeRegText(bytes)
            scope.launch(Dispatchers.IO) {
                WineRegistryEditor(regFile).use { it.importRegFile(content) }
                refreshKey++
            }
            onToast(ctx.getString(R.string.registry_import_done))
        } catch (e: Exception) {
            onToast(ctx.getString(R.string.registry_import_failed) + ": ${e.message}")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri == null || regFile == null) return@rememberLauncherForActivityResult
        val path = currentPath
        val prefix = hivePrefix
        scope.launch(Dispatchers.IO) {
            try {
                val text = WineRegistryEditor(regFile).use { it.exportReg(path, prefix) }
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(text.toByteArray(Charsets.UTF_8))
                }
                onToast(ctx.getString(R.string.registry_export_done))
            } catch (e: Exception) {
                onToast(ctx.getString(R.string.registry_export_failed) + ": ${e.message}")
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (containerRootDir == null) {
            Text(
                stringResource(R.string.registry_created),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        SectionCard(title = stringResource(R.string.registry_editor), icon = Icons.Filled.AccountTree) {
            Text(
                stringResource(R.string.registry_current_wine, "$wineVersion (${wineArch})"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Поиск (по текущему хайву)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.registry_search)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                ,
                    shape = GlassFieldShape,
                    colors = glassFieldColors())
                Spacer(Modifier.width(6.dp))
                FilledIconButton(onClick = { runSearch() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                if (searchResults != null) {
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { searchResults = null; searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            }

            if (searchResults != null) {
                val results = searchResults!!
                Text(
                    stringResource(R.string.registry_search_results, results.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (results.isEmpty()) {
                    Text(
                        stringResource(R.string.registry_search_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    results.forEach { keyPath ->
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                                .clickable {
                                    searchResults = null
                                    searchQuery = ""
                                    val root = if (hive == HIVE_USER) HIVE_ROOTS[0] else HIVE_ROOTS[1]
                                    enterRoot(root)
                                    currentPath = keyPath
                                    refreshKey++
                                },
                            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                            shape = RoundedCornerShape(GlassTokens.CardRadius),
                            border = glassCardBorder(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(keyPath, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else if (selectedRoot == null) {
                // ==== Экран Computer: корневые папки ====
                if (!fileExists && regFile != null && regFile.isFile) fileExists = true
                Text(
                    stringResource(R.string.registry_computer),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                HIVE_ROOTS.forEach { root ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                            .clickable { enterRoot(root) },
                        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                        shape = RoundedCornerShape(GlassTokens.CardRadius),
                        border = glassCardBorder(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Storage,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(root.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                                Text(
                                    when (root.hive) {
                                        HIVE_USER -> "user.reg" + if (root.path.isNotEmpty()) " / $root.path" else ""
                                        else -> "system.reg" + if (root.path.isNotEmpty()) " / $root.path" else ""
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Кнопки: импорт/экспорт (корневой экран — применяется к выбранному по умолчанию user.reg)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassPill(
                        onClick = { hive = HIVE_USER; importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.registry_import), maxLines = 1)
                    }
                    GlassPill(
                        onClick = { exportLauncher.launch("registry_export.reg") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.registry_export), maxLines = 1)
                    }
                }
            } else {
                // ==== Дерево хайва ====
                val root = selectedRoot!!
                // Breadcrumb: Computer > HKEY_... > путь
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.registry_computer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { exitToComputer() }.padding(4.dp)
                    )
                    Text(">", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        root.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(4.dp)
                    )
                }
                val pathParts = if (currentPath.isEmpty()) emptyList() else currentPath.split("\\")
                if (pathParts.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val scrollState = rememberScrollState()
                        Row(Modifier.horizontalScroll(scrollState), verticalAlignment = Alignment.CenterVertically) {
                            pathParts.forEachIndexed { index, part ->
                                Text(">", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val targetPath = pathParts.subList(0, index + 1).joinToString("\\")
                                val isLast = index == pathParts.size - 1
                                Text(
                                    part,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                    fontWeight = if (isLast) FontWeight.Bold else null,
                                    modifier = if (isLast) Modifier.padding(4.dp) else Modifier.clickable { currentPath = targetPath; refreshKey++ }.padding(4.dp)
                                )
                            }
                        }
                    }
                }

                // Кнопки действий
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassPill(
                        onClick = { showAddKeyDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.registry_add_key), maxLines = 1)
                    }
                    GlassPill(
                        onClick = { addingValue = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.registry_add_value), maxLines = 1)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassPill(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.registry_import), maxLines = 1)
                    }
                    GlassPill(
                        onClick = { exportLauncher.launch("registry_export.reg") },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.registry_export), maxLines = 1)
                    }
                    if (hive == HIVE_SYSTEM) {
                        GlassPill(
                            onClick = { showSpoofDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Memory, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.registry_cpu_spoof), maxLines = 1)
                        }
                    }
                }

                // Дерево
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                ) {
                    if (visibleTreeNodes.isEmpty()) {
                        Text(
                            stringResource(R.string.registry_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    visibleTreeNodes.forEach { (nodePath, depth) ->
                        val nodeName = nodePath.substringAfterLast("\\")
                        val isExpanded = nodePath in expandedPaths
                        val hasLoaded = loadedChildren.containsKey(nodePath)
                        val loadedEmpty = hasLoaded && loadedChildren[nodePath]!!.isEmpty()
                        val isSelected = nodePath == currentPath
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = (depth * 16).dp, end = 4.dp)
                                .combinedClickable(
                                    onClick = {
                                        currentPath = nodePath
                                        if (!loadedEmpty) toggleNode(nodePath)
                                        refreshKey++
                                    },
                                    onLongClick = { copyToClipboard(nodePath) }
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (loadedEmpty) {
                                Spacer(Modifier.width(28.dp))
                            } else {
                                IconButton(onClick = { toggleNode(nodePath) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                        null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Icon(
                                if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                nodeName + (if (hasLoaded) " (${loadedChildren[nodePath]!!.size})" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else null,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Значения выбранного ключа
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                val currentChildren = loadedChildren[currentPath]
                Text(
                    "${stringResource(R.string.registry_values)} (${values.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (values.isEmpty()) {
                    Text(
                        if (currentChildren != null && currentChildren.isNotEmpty())
                            stringResource(R.string.registry_key_no_values_hint, currentChildren.size)
                        else
                            stringResource(R.string.registry_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    values.forEach { value ->
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                                .combinedClickable(
                                    onClick = { editingValue = value },
                                    onLongClick = { copyToClipboard("${value.name ?: "(Default)"} = ${value.value}") }
                                ),
                            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                            shape = RoundedCornerShape(GlassTokens.CardRadius),
                            border = glassCardBorder(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        value.name ?: stringResource(R.string.registry_default_value),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "${value.type}: ${value.value.ifEmpty { "(empty)" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2
                                    )
                                }
                                IconButton(onClick = { deleteValueConfirm = value }) {
                                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                if (currentPath.isNotEmpty()) {
                    TextButton(
                        onClick = { deleteKeyConfirm = currentPath },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.registry_delete_key), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // Диалог добавления ключа
    if (showAddKeyDialog) {
        var keyName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            title = { Text(stringResource(R.string.registry_add_key)) },
            text = {
                Column {
                    Text("Path: ${if (currentPath.isEmpty()) "\\" else currentPath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyName,
                        onValueChange = { keyName = it },
                        label = { Text(stringResource(R.string.registry_key_name)) },
                        singleLine = true
                    ,
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = keyName.trim()
                    if (trimmed.isNotEmpty() && !trimmed.contains("\\")) {
                        val newKey = if (currentPath.isEmpty()) trimmed else "$currentPath\\$trimmed"
                        scope.launch(Dispatchers.IO) {
                            WineRegistryEditor(regFile).use { reg ->
                                reg.setStringValue(newKey, null, "")
                            }
                            refreshKey++
                        }
                    }
                    showAddKeyDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showAddKeyDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Диалог добавления/редактирования значения
    if (addingValue || editingValue != null) {
        val existing = editingValue
        var valueName by remember(existing) { mutableStateOf(existing?.name ?: "") }
        var valueType by remember(existing) { mutableStateOf(existing?.type?.let { t ->
            if (t in REG_VALUE_TYPES) t else if (t == "Qword") "Hex" else "String"
        } ?: "String") }
        var valueText by remember(existing) { mutableStateOf(existing?.value ?: "") }

        AlertDialog(
            onDismissRequest = { addingValue = false; editingValue = null },
            title = { Text(if (existing != null) stringResource(R.string.registry_edit_value) else stringResource(R.string.registry_add_value)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valueName,
                        onValueChange = { valueName = it },
                        label = { Text(stringResource(R.string.registry_value_name)) },
                        singleLine = true,
                        enabled = existing == null
                    ,
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    var typeExpanded by remember { mutableStateOf(false) }
                    Box {
                        GlassPill(
                            onClick = { typeExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.registry_type) + ": $valueType", modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            REG_VALUE_TYPES.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = { valueType = type; typeExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text(stringResource(R.string.registry_value)) },
                        singleLine = valueType != "Hex",
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
                    ,
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            WineRegistryEditor(regFile).use { reg ->
                                val name = valueName.trim().ifEmpty { null }
                                when (valueType) {
                                    "Dword" -> {
                                        val clean = valueText.trim().removePrefix("0x")
                                        if (clean.isEmpty()) throw IllegalArgumentException("empty")
                                        reg.setDwordValue(currentPath, name, java.lang.Long.decode("0x" + clean).toInt())
                                    }
                                    "Hex" -> {
                                        val clean = valueText.filter { it.isDigit() || it in "abcdefABCDEF" }
                                        if (clean.isEmpty()) throw IllegalArgumentException("empty")
                                        reg.setHexValue(currentPath, name, clean)
                                    }
                                    else -> reg.setStringValue(currentPath, name, valueText)
                                }
                            }
                            refreshKey++
                            onToast(ctx.getString(R.string.registry_saved))
                        } catch (e: Exception) {
                            onToast(ctx.getString(R.string.registry_invalid_hex))
                        }
                    }
                    addingValue = false
                    editingValue = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { addingValue = false; editingValue = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Диалог спуфинга CPU
    if (showSpoofDialog) {
        val autoPreset = if (wineArch.equals("arm64ec", true)) PRESET_ARM else PRESET_INTEL
        var spoofIdentifier by remember { mutableStateOf(sp.getString(PREFS_SPOOF_IDENTIFIER, null) ?: autoPreset.identifier) }
        var spoofVendor by remember { mutableStateOf(sp.getString(PREFS_SPOOF_VENDOR, null) ?: autoPreset.vendor) }
        var spoofMhz by remember { mutableStateOf(sp.getString(PREFS_SPOOF_MHZ, null) ?: autoPreset.mhz.toString()) }
        var spoofFeatureSet by remember { mutableStateOf(sp.getString(PREFS_SPOOF_FEATURESET, null) ?: autoPreset.featureSet) }
        var spoofPresetName by remember { mutableStateOf(if (sp.getString(PREFS_SPOOF_IDENTIFIER, null) == null) autoPreset.name else "") }

        fun applyPreset(preset: CpuSpoofPreset) {
            spoofIdentifier = preset.identifier
            spoofVendor = preset.vendor
            spoofMhz = preset.mhz.toString()
            spoofFeatureSet = preset.featureSet
            spoofPresetName = preset.name
        }

        AlertDialog(
            onDismissRequest = { showSpoofDialog = false },
            title = { Text(stringResource(R.string.registry_cpu_spoof)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip(
                            text = PRESET_ARM.name,
                            onClick = { applyPreset(PRESET_ARM) },
                            accent = spoofPresetName == PRESET_ARM.name,
                        )
                        GlassChip(
                            text = PRESET_INTEL.name,
                            onClick = { applyPreset(PRESET_INTEL) },
                            accent = spoofPresetName == PRESET_INTEL.name,
                        )
                    }
                    Text(
                        stringResource(R.string.registry_cpu_spoof_hint, wineArch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = spoofIdentifier,
                        onValueChange = { spoofIdentifier = it; spoofPresetName = "" },
                        label = { Text("Identifier") },
                        singleLine = true
                    ,
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    OutlinedTextField(
                        value = spoofVendor,
                        onValueChange = { spoofVendor = it; spoofPresetName = "" },
                        label = { Text("VendorIdentifier") },
                        singleLine = true
                    ,
                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = spoofMhz,
                            onValueChange = { spoofMhz = it.filter { c -> c.isDigit() }; spoofPresetName = "" },
                            label = { Text("~MHz") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        ,
                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                        OutlinedTextField(
                            value = spoofFeatureSet,
                            onValueChange = { spoofFeatureSet = it.filter { c -> c.isDigit() || c in "abcdefABCDEF" }; spoofPresetName = "" },
                            label = { Text("FeatureSet (hex)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        ,
                            shape = GlassFieldShape,
                            colors = glassFieldColors())
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            WineRegistryEditor(regFile).use { reg ->
                                val numCpus = Runtime.getRuntime().availableProcessors()
                                for (i in 0 until numCpus) {
                                    val key = "Hardware\\Description\\System\\CentralProcessor\\$i"
                                    reg.setStringValue(key, "Identifier", spoofIdentifier)
                                    reg.setStringValue(key, "VendorIdentifier", spoofVendor)
                                    reg.setDwordValue(key, "~MHz", spoofMhz.toIntOrNull() ?: 0)
                                    reg.setDwordValue(key, "FeatureSet", spoofFeatureSet.toLongOrNull(16)?.toInt() ?: 0)
                                }
                            }
                            refreshKey++
                        }
                        sp.edit()
                            .putString(PREFS_SPOOF_IDENTIFIER, spoofIdentifier)
                            .putString(PREFS_SPOOF_VENDOR, spoofVendor)
                            .putString(PREFS_SPOOF_MHZ, spoofMhz)
                            .putString(PREFS_SPOOF_FEATURESET, spoofFeatureSet)
                            .apply()
                        showSpoofDialog = false
                        onToast(ctx.getString(R.string.registry_cpu_spoof_done))
                    }) {
                        Text(stringResource(R.string.apply))
                    }
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            WineRegistryEditor(regFile).use { reg ->
                                val numCpus = Runtime.getRuntime().availableProcessors()
                                for (i in 0 until numCpus) {
                                    val key = "Hardware\\Description\\System\\CentralProcessor\\$i"
                                    reg.removeValue(key, "Identifier")
                                    reg.removeValue(key, "VendorIdentifier")
                                    reg.removeValue(key, "~MHz")
                                    reg.removeValue(key, "FeatureSet")
                                }
                            }
                            refreshKey++
                        }
                        sp.edit()
                            .remove(PREFS_SPOOF_IDENTIFIER)
                            .remove(PREFS_SPOOF_VENDOR)
                            .remove(PREFS_SPOOF_MHZ)
                            .remove(PREFS_SPOOF_FEATURESET)
                            .apply()
                        showSpoofDialog = false
                        onToast(ctx.getString(R.string.registry_cpu_spoof_cleared))
                    }) {
                        Text(stringResource(R.string.registry_cpu_spoof_clear), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showSpoofDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    // Подтверждение удаления ключа
    deleteKeyConfirm?.let { keyPath ->
        AlertDialog(
            onDismissRequest = { deleteKeyConfirm = null },
            title = { Text(stringResource(R.string.registry_delete_key)) },
            text = { Text(stringResource(R.string.registry_confirm_delete_key, keyPath)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        WineRegistryEditor(regFile).use { reg ->
                            reg.removeKey(keyPath, true)
                        }
                        refreshKey++
                    }
                    deleteKeyConfirm = null
                }) { Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteKeyConfirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Подтверждение удаления значения
    deleteValueConfirm?.let { value ->
        AlertDialog(
            onDismissRequest = { deleteValueConfirm = null },
            title = { Text(stringResource(R.string.registry_delete_value)) },
            text = { Text(stringResource(R.string.registry_confirm_delete_value, value.name ?: "(Default)")) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        WineRegistryEditor(regFile).use { reg ->
                            reg.removeValue(currentPath, value.name)
                        }
                        refreshKey++
                    }
                    deleteValueConfirm = null
                }) { Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteValueConfirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private fun decodeRegText(bytes: ByteArray): String {
    val start = if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) 2 else 0
    if (start == 2) return String(bytes, start, bytes.size - start, Charsets.UTF_16LE)
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    return String(bytes, Charsets.UTF_8)
}
