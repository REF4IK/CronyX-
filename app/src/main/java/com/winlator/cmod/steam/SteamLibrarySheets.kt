package com.winlator.cmod.steam

import androidx.compose.foundation.border

import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassChevronColor
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSwitch

import com.winlator.cmod.ui.components.glassCardFill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import coil.compose.AsyncImage
import com.winlator.cmod.R
import com.winlator.cmod.steam.data.DepotInfo
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.ui.SteamGameDetailUi
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.workshop.WorkshopItem
import com.winlator.cmod.steam.workshop.WorkshopManager
import com.winlator.cmod.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun SteamSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.steam_library_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = glassCardFill(),
                unfocusedContainerColor = glassCardFill(),
                focusedBorderColor = glassAccent(),
                unfocusedBorderColor = glassChevronColor(),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            shape = RoundedCornerShape(18.dp),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun SteamContentManagerSheet(
    game: SteamGameDetailUi,
    selectedBranch: String,
    onDismiss: () -> Unit,
    onInstall: (List<Int>) -> Unit,
) {
    val context = LocalContext.current
    var loading by remember(game.appId, selectedBranch) { mutableStateOf(true) }
    var loadFailed by remember(game.appId, selectedBranch) { mutableStateOf(false) }
    var errorMessage by remember(game.appId) { mutableStateOf("") }
    val dlcApps = remember(game.appId) { mutableStateListOf<SteamContentDlcEntry>() }
    val selectedDlcIds = remember(game.appId) { mutableStateMapOf<Int, Boolean>() }
    val perItemSizeText = remember(game.appId, selectedBranch) { mutableStateMapOf<Int, Pair<String, String>>() }
    var baseGameSizeText by remember(game.appId, selectedBranch) { mutableStateOf("--" to "--") }
    var availableSpaceBytes by remember(game.appId, selectedBranch) { mutableStateOf(0L) }
    var totalDownloadSizeText by remember(game.appId, selectedBranch) { mutableStateOf("--") }
    var totalInstallSizeText by remember(game.appId, selectedBranch) { mutableStateOf("--") }
    var debugLog by remember(game.appId) { mutableStateOf("") }
    val installedDlcIds = remember(game.appId) {
        SteamService.getInstalledDlcDepotsOf(game.appId).orEmpty().toSet()
    }
    val isInstalled = remember(game.appId) { SteamService.isAppInstalled(game.appId) }

    LaunchedEffect(game.appId, selectedBranch) {
        loading = true
        loadFailed = false
        errorMessage = ""
        debugLog = ""
        dlcApps.clear()
        selectedDlcIds.clear()
        perItemSizeText.clear()

        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (!PrefManager.steamOfflineMode && SteamService.isConnected && SteamService.isLoggedIn) {
                    debugLog += "Refreshing DLC from Steam...\n"
                    SteamService.refreshAppMetadataFromSteam(game.appId, refreshRelatedDlcs = true)
                }

                debugLog += "Querying selectable DLC apps...\n"
                val selectableDlcApps = SteamService.getSelectableDlcAppsOf(game.appId)
                debugLog += "Found ${selectableDlcApps.size} selectable DLCs\n"

                val availableBytes = runCatching {
                    StorageUtils.getAvailableSpace(SteamService.getAppDirPath(game.appId))
                }.getOrDefault(0L)

                val dlcEntries = mutableListOf<SteamContentDlcEntry>()
                val dlcSizes = mutableMapOf<Int, Pair<String, String>>()

                for (dlcApp in selectableDlcApps) {
                    val dlcDepots = SteamService.getDownloadableDepots(dlcApp.id)
                        .values
                        .map { depot ->
                            if (depot.dlcAppId == SteamService.INVALID_APP_ID) {
                                depot.copy(dlcAppId = dlcApp.id)
                            } else {
                                depot
                            }
                        }

                    val defaultSelected = installedDlcIds.contains(dlcApp.id)
                    val isInstallable = dlcDepots.isNotEmpty()

                    dlcEntries += SteamContentDlcEntry(
                        appId = dlcApp.id,
                        name = dlcApp.name.ifBlank { "DLC ${dlcApp.id}" },
                        installable = isInstallable,
                        defaultSelected = defaultSelected,
                    )
                    dlcSizes[dlcApp.id] = if (isInstallable) {
                        calculateDepotSizes(dlcDepots, selectedBranch)
                    } else {
                        "--" to "--"
                    }
                }

                val baseDepots = SteamService.getMainAppDepots(game.appId)
                val baseSizes = calculateDepotSizes(baseDepots.values, selectedBranch)

                SteamContentLoadResult(
                    availableBytes = availableBytes,
                    baseSizes = baseSizes,
                    dlcEntries = dlcEntries.sortedBy { it.name.lowercase() },
                    dlcSizes = dlcSizes,
                )
            }
        }

        result.onSuccess { contentResult ->
            availableSpaceBytes = contentResult.availableBytes
            baseGameSizeText = contentResult.baseSizes.first to contentResult.baseSizes.second
            contentResult.dlcEntries.forEach { entry ->
                dlcApps += entry
                selectedDlcIds[entry.appId] = entry.defaultSelected
            }
            contentResult.dlcSizes.forEach { (appId, sizePair) ->
                perItemSizeText[appId] = sizePair.first to sizePair.second
            }
            if (contentResult.dlcEntries.isEmpty()) {
                debugLog += "\nNo DLC entries found. Steam connected: ${SteamService.isConnected}, logged in: ${SteamService.isLoggedIn}"
            }
        }.onFailure { e ->
            loadFailed = true
            errorMessage = e.message ?: "Unknown error"
            debugLog += "\nError: ${e.message}"
        }

        loading = false
    }

    LaunchedEffect(game.appId, selectedBranch, selectedDlcIds.toMap()) {
        if (loading) return@LaunchedEffect
        val enabledDlcIds = selectedDlcIds
            .filterValues { it }
            .keys
            .filter { dlcApps.any { d -> d.appId == it && d.installable } }
        val sizes = withContext(Dispatchers.IO) {
            SteamService.getSelectedManifestSizes(
                appId = game.appId,
                userSelectedDlcAppIds = enabledDlcIds,
                branch = selectedBranch,
            )
        }
        totalDownloadSizeText = formatBinarySize(sizes.downloadSize)
        totalInstallSizeText = formatBinarySize(sizes.installSize)
    }

    val allSelected = dlcApps.isNotEmpty() && dlcApps.all { selectedDlcIds[it.appId] == true }
    val availableSpaceText by remember(availableSpaceBytes) {
        derivedStateOf { formatBinarySize(availableSpaceBytes) }
    }
    val canInstall by remember(selectedDlcIds.toMap(), dlcApps.toList(), totalInstallSizeText, availableSpaceBytes, isInstalled) {
        derivedStateOf {
            val selectedNewDlc = selectedDlcIds
                .filterValues { it }
                .keys
                .any { it !in installedDlcIds && dlcApps.any { d -> d.appId == it && d.installable } }
            (!isInstalled || selectedNewDlc) && availableSpaceBytes > 0L
        }
    }

    SteamFullscreenSheet(
        title = "Content & DLC",
        subtitle = game.name,
        onDismiss = onDismiss,
    ) {
        when {
            loading -> SteamCenteredState(stringResource(R.string.steam_library_loading))
            loadFailed -> Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.steam_library_content_manager_failed), color = MaterialTheme.colorScheme.error)
                if (errorMessage.isNotBlank()) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Size: ${totalDownloadSizeText} / ${totalInstallSizeText}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("Space: $availableSpaceText", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            SteamSelectableRow(
                                title = stringResource(R.string.steam_library_content_base_game),
                                subtitle = "${baseGameSizeText.first} / ${baseGameSizeText.second}",
                                checked = true,
                                enabled = false,
                                onCheckedChange = {},
                            )
                        }

                        if (dlcApps.isNotEmpty()) {
                            item {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("DLC (${dlcApps.size})", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = {
                                        val newVal = !allSelected
                                        dlcApps.forEach { selectedDlcIds[it.appId] = newVal }
                                    }) {
                                        Text(if (allSelected) "Deselect All" else "Select All", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        items(dlcApps, key = { it.appId }) { entry ->
                            val checked = selectedDlcIds[entry.appId] == true
                            val sizeText = perItemSizeText[entry.appId]?.let { "${it.first} / ${it.second}" } ?: "--"
                            val isInstalledDlc = entry.appId in installedDlcIds

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    GlassSwitch(
                                        checked = checked,
                                        onCheckedChange = { if (entry.installable) selectedDlcIds[entry.appId] = it },
                                        enabled = entry.installable,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(entry.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            if (isInstalledDlc) {
                                                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("INSTALLED", color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.sp) }
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(sizeText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        if (dlcApps.isEmpty()) {
                            item {
                                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.steam_library_content_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (debugLog.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(debugLog, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        enabled = canInstall,
                        onClick = {
                            onInstall(
                                selectedDlcIds
                                    .filterValues { it }
                                    .keys
                                    .filter { dlcApps.any { d -> d.appId == it && d.installable } }
                                    .sorted(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text(stringResource(R.string.steam_library_install_selected), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SteamWorkshopManagerSheet(
    game: SteamGameDetailUi,
    currentEnabledIds: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val steamClient = SteamService.instance?.steamClient
    val steamId = SteamService.userSteamId
    val workshopItems = remember(game.appId) { mutableStateListOf<WorkshopItem>() }
    val selectedIds = remember(game.appId) { mutableStateMapOf<Long, Boolean>() }
    var isLoading by remember(game.appId) { mutableStateOf(true) }
    var fetchFailed by remember(game.appId) { mutableStateOf(false) }
    var searchQuery by remember(game.appId) { mutableStateOf("") }

    LaunchedEffect(game.appId) {
        isLoading = true
        fetchFailed = false
        workshopItems.clear()
        selectedIds.clear()
        searchQuery = ""

        if (steamClient == null || steamId == null) {
            Timber.w("Workshop: steamClient or steamId is null (client=$steamClient, id=$steamId)")
            // Retry once after 3 seconds
            delay(3000L)
            val retryClient = SteamService.instance?.steamClient
            val retryId = SteamService.userSteamId
            if (retryClient == null || retryId == null) {
                fetchFailed = true
                isLoading = false
                return@LaunchedEffect
            }
            val retryResult = withContext(Dispatchers.IO) {
                WorkshopManager.getSubscribedItems(game.appId, retryClient, retryId)
            }
            if (!retryResult.succeeded) {
                fetchFailed = true
            } else {
                workshopItems.addAll(retryResult.items)
                retryResult.items.forEach { item ->
                    selectedIds[item.publishedFileId] = currentEnabledIds.contains(item.publishedFileId)
                }
            }
            isLoading = false
            return@LaunchedEffect
        }

        val result = withContext(Dispatchers.IO) {
            WorkshopManager.getSubscribedItems(game.appId, steamClient, steamId)
        }
        Timber.i("Workshop fetch result: succeeded=${result.succeeded}, items=${result.items.size}")
        if (!result.succeeded) {
            fetchFailed = true
        } else {
            workshopItems.addAll(result.items)
            result.items.forEach { item ->
                selectedIds[item.publishedFileId] = currentEnabledIds.contains(item.publishedFileId)
            }
        }
        isLoading = false
    }

    val visibleItems by remember(workshopItems, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                workshopItems.toList()
            } else {
                workshopItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }
        }
    }
    val selectedCount = selectedIds.count { it.value }

    SteamFullscreenSheet(
        title = "Workshop",
        subtitle = game.name,
        onDismiss = onDismiss,
    ) {
        when {
            isLoading -> SteamCenteredState(stringResource(R.string.steam_library_loading))
            fetchFailed -> Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.steam_library_workshop_failed), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                GlassPill(onClick = {
                    scope.launch {
                        isLoading = true
                        fetchFailed = false
                        workshopItems.clear()
                        val c = SteamService.instance?.steamClient
                        val id = SteamService.userSteamId
                        if (c != null && id != null) {
                            val r = withContext(Dispatchers.IO) { WorkshopManager.getSubscribedItems(game.appId, c, id) }
                            if (r.succeeded) {
                                workshopItems.addAll(r.items)
                                r.items.forEach { selectedIds[it.publishedFileId] = currentEnabledIds.contains(it.publishedFileId) }
                            } else { fetchFailed = true }
                        } else { fetchFailed = true }
                        isLoading = false
                    }
                }) { Text("Retry") }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SteamSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = { searchQuery = "" },
                    )

                    if (workshopItems.isEmpty()) {
                        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.FolderZip, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.steam_library_workshop_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Subscribe to items in Steam to see them here", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${visibleItems.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Spacer(Modifier.weight(1f))
                            Text("$selectedCount selected", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(visibleItems, key = { it.publishedFileId }) { item ->
                                val isChecked = selectedIds[item.publishedFileId] == true

                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Preview image
                                        Box(
                                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(glassCardFill()).border(glassCardBorder(), RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (item.previewUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = item.previewUrl,
                                                    contentDescription = item.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Icon(Icons.Filled.FolderZip, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(24.dp))
                                            }
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title.ifEmpty { "Item ${item.publishedFileId}" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Spacer(Modifier.height(2.dp))
                                            if (item.fileSizeBytes > 0L) {
                                                Text(formatBinarySize(item.fileSizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                            }
                                            Text("ID: ${item.publishedFileId}", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp)
                                        }

                                            GlassSwitch(
                                                checked = isChecked,
                                                onCheckedChange = { selectedIds[item.publishedFileId] = it },
                                            )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onSave(selectedIds.filterValues { it }.keys)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = selectedCount > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text(stringResource(R.string.steam_library_apply_and_sync), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SteamBranchPickerSheet(
    gameName: String,
    availableBranches: List<String>,
    currentBranch: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selectedBranch by remember(currentBranch) { mutableStateOf(currentBranch) }

    SteamFullscreenSheet(
        title = stringResource(R.string.steam_library_change_branch_title),
        subtitle = gameName,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SteamInfoBanner(
                title = stringResource(R.string.steam_library_branch_current),
                body = currentBranch,
            )
            availableBranches.forEach { branch ->
                SteamBranchRow(
                    branch = branch,
                    selected = branch == selectedBranch,
                    onSelect = { selectedBranch = branch },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                enabled = selectedBranch != currentBranch,
                onClick = { onConfirm(selectedBranch) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.steam_library_apply_branch))
            }
        }
    }
}

@Composable
private fun SteamFullscreenSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SteamCenteredState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SteamInfoBanner(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SteamSelectableRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null)
        }
    }
}

@Composable
private fun SteamWorkshopRow(
    item: WorkshopItem,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(glassCardFill()),
                contentAlignment = Alignment.Center,
            ) {
                if (item.previewUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.previewUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = item.title.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBinarySize(item.fileSizeBytes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SteamBranchRow(
    branch: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = branch,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun calculateDepotSizes(
    depots: Collection<DepotInfo>,
    branch: String,
): Pair<String, String> {
    val installBytes = depots.sumOf { resolveManifestSize(it, branch) }
    val downloadBytes = depots.sumOf { resolveManifestDownload(it, branch) }
    return formatBinarySize(downloadBytes) to formatBinarySize(installBytes)
}

private fun resolveManifestSize(
    depot: DepotInfo,
    branch: String,
): Long {
    val manifest = depot.manifests[branch]
        ?: depot.encryptedManifests[branch]
        ?: depot.manifests["public"]
        ?: depot.encryptedManifests["public"]
    return manifest?.size ?: 0L
}

private fun resolveManifestDownload(
    depot: DepotInfo,
    branch: String,
): Long {
    val manifest = depot.manifests[branch]
        ?: depot.encryptedManifests[branch]
        ?: depot.manifests["public"]
        ?: depot.encryptedManifests["public"]
    return manifest?.download ?: 0L
}

private fun formatBinarySize(bytes: Long): String {
    return if (bytes > 0L) StorageUtils.formatBinarySize(bytes) else "--"
}

private data class SteamContentDlcEntry(
    val appId: Int,
    val name: String,
    val installable: Boolean,
    val defaultSelected: Boolean,
)

private data class SteamContentLoadResult(
    val availableBytes: Long,
    val baseSizes: Pair<String, String>,
    val dlcEntries: List<SteamContentDlcEntry>,
    val dlcSizes: Map<Int, Pair<String, String>>,
)
