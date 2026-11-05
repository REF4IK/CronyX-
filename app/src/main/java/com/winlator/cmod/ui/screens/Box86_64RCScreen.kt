package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSectionHeader
import com.winlator.cmod.ui.components.GlassSwitch
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.box86_64.rc.RCField
import com.winlator.cmod.box86_64.rc.RCFile
import com.winlator.cmod.box86_64.rc.RCGroup
import com.winlator.cmod.box86_64.rc.RCItem
import com.winlator.cmod.box86_64.rc.RCManager
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import org.json.JSONObject
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Box86_64RCScreen(
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val rcManager = remember { RCManager(ctx).also { it.loadRCFiles() } }
    var refreshKey by remember { mutableStateOf(0) }
    val rcFiles = remember(refreshKey) { rcManager.rcFiles }

    var currentRCFile by remember { mutableStateOf<RCFile?>(null) }
    var activeGroup by remember { mutableStateOf<RCGroup?>(null) }

    // Dialog state
    var showPromptTitle by remember { mutableStateOf<String?>(null) }
    var showPromptInitialValue by remember { mutableStateOf("") }
    var showPromptLabel by remember { mutableStateOf("") }
    var onPromptConfirm by remember { mutableStateOf<(String) -> Unit>({}) }
    var showPrompt by remember { mutableStateOf(false) }

    var showConfirmTitle by remember { mutableStateOf("") }
    var showConfirmMsg by remember { mutableStateOf("") }
    var onConfirmAction by remember { mutableStateOf<() -> Unit>({}) }
    var showConfirm by remember { mutableStateOf(false) }

    var showChoiceTitle by remember { mutableStateOf("") }
    var showChoiceItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var onChoiceSelected by remember { mutableStateOf<(Int) -> Unit>({}) }
    var showChoiceList by remember { mutableStateOf(false) }

    var showImportGroupDialog by remember { mutableStateOf(false) }

    fun triggerPrompt(title: String, initial: String, label: String, onConfirm: (String) -> Unit) {
        showPromptTitle = title
        showPromptInitialValue = initial
        showPromptLabel = label
        onPromptConfirm = onConfirm
        showPrompt = true
    }

    fun triggerConfirm(title: String, message: String, onConfirm: () -> Unit) {
        showConfirmTitle = title
        showConfirmMsg = message
        onConfirmAction = onConfirm
        showConfirm = true
    }

    // JSON file import/export launchers
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonString = FileUtils.readString(ctx, uri)
                val importedProfile = RCManager.loadRCFile(ctx, JSONObject(jsonString))
                if (importedProfile != null) {
                    val duplicated = rcManager.duplicateRCFile(importedProfile)
                    currentRCFile = duplicated
                    refreshKey++
                    AppUtils.showToast(ctx, "Profile imported successfully")
                }
            } catch (e: Exception) {
                AppUtils.showToast(ctx, R.string.unable_to_import_profile)
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = activeGroup != null) {
        rcManager.saveAllRCFiles()
        activeGroup = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
            if (activeGroup != null) {
                // Render Group Editor Screen
                RCGroupEditor(
                    group = activeGroup!!,
                    onBack = {
                        rcManager.saveAllRCFiles()
                        activeGroup = null
                    },
                    onPrompt = ::triggerPrompt,
                    onConfirm = ::triggerConfirm,
                    onChoice = { title, choices, onSelected ->
                        showChoiceTitle = title
                        showChoiceItems = choices
                        onChoiceSelected = onSelected
                        showChoiceList = true
                    },
                    onGroupNameChanged = {
                        rcManager.saveAllRCFiles()
                    }
                )
            } else {
                // Render Main Profile / Group List Screen
                RCScreenMain(
                    rcManager = rcManager,
                    rcFiles = rcFiles,
                    currentRCFile = currentRCFile,
                    onCurrentRCFileChange = { currentRCFile = it },
                    onPrompt = ::triggerPrompt,
                    onConfirm = ::triggerConfirm,
                    onImportProfile = { importLauncher.launch(arrayOf("*/*")) },
                    onRefresh = { refreshKey++ },
                    onGroupClick = { activeGroup = it },
                    onShowImportGroup = { showImportGroupDialog = true },
                    refreshKey = refreshKey
                )
            }
        }

        // Modal dialogs
        if (showPrompt) {
            PromptDialog(
                title = showPromptTitle ?: "",
                initialValue = showPromptInitialValue,
                label = showPromptLabel,
                onConfirm = {
                    onPromptConfirm(it)
                    showPrompt = false
                },
                onDismiss = { showPrompt = false }
            )
        }

        if (showConfirm) {
            ConfirmDialog(
                title = showConfirmTitle,
                message = showConfirmMsg,
                onConfirm = {
                    onConfirmAction()
                    showConfirm = false
                },
                onDismiss = { showConfirm = false }
            )
        }

        if (showChoiceList) {
            SingleChoiceDialog(
                title = showChoiceTitle,
                choices = showChoiceItems,
                onSelected = {
                    onChoiceSelected(it)
                    showChoiceList = false
                },
                onDismiss = { showChoiceList = false }
            )
        }

        if (showImportGroupDialog && currentRCFile != null) {
            ImportGroupDialogCompose(
                rcManager = rcManager,
                onGroupSelected = { importedGroup ->
                    currentRCFile!!.groups.add(importedGroup)
                    rcManager.saveAllRCFiles()
                    showImportGroupDialog = false
                    refreshKey++
                },
                onDismiss = { showImportGroupDialog = false }
            )
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RCScreenMain(
    rcManager: RCManager,
    rcFiles: List<RCFile>,
    currentRCFile: RCFile?,
    onCurrentRCFileChange: (RCFile?) -> Unit,
    onPrompt: (String, String, String, (String) -> Unit) -> Unit,
    onConfirm: (String, String, () -> Unit) -> Unit,
    onImportProfile: () -> Unit,
    onRefresh: () -> Unit,
    onGroupClick: (RCGroup) -> Unit,
    onShowImportGroup: () -> Unit,
    refreshKey: Int,
) {
    val ctx = LocalContext.current
    var filterMode by remember { mutableStateOf(0) } // 0: All, 1: Enabled, 2: Disabled

    // Profile Dropdown entries
    val profileNames = remember(rcFiles) {
        val list = mutableListOf("-- " + ctx.getString(R.string.select_profile) + " --")
        list.addAll(rcFiles.map { it.name })
        list
    }
    val selectedProfileName = currentRCFile?.name ?: ("-- " + ctx.getString(R.string.select_profile) + " --")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // ---- Profile Selector Card ----
        GlassSectionHeader(title = stringResource(R.string.profile))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            border = glassCardBorder(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                SpinnerRow(
                    label = "",
                    entries = profileNames,
                    selected = selectedProfileName,
                    onSelected = { selected ->
                        val index = profileNames.indexOf(selected)
                        if (index > 0) {
                            onCurrentRCFileChange(rcFiles[index - 1])
                        } else {
                            onCurrentRCFileChange(null)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Profile action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Add Profile (Create/Import) Button
                    var showAddMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
                        }
                        DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_file)) },
                                onClick = {
                                    showAddMenu = false
                                    onPrompt(
                                        ctx.getString(R.string.profile_name),
                                        "",
                                        "",
                                        { name ->
                                            val file = rcManager.createRCFile(name)
                                            onCurrentRCFileChange(file)
                                            onRefresh()
                                        }
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_container_profile)) },
                                onClick = {
                                    showAddMenu = false
                                    onImportProfile()
                                }
                            )
                        }
                    }

                    // Rename / Restore Button
                    IconButton(onClick = {
                        if (currentRCFile != null) {
                            if (currentRCFile.id == 1) {
                                onConfirm(
                                    ctx.getString(R.string.confirmation),
                                    ctx.getString(R.string.do_you_want_to_restore_default_profile),
                                    {
                                        rcManager.removeRCFile(currentRCFile)
                                        FileUtils.copy(ctx, "box86_64/rcfiles", RCManager.getRCFilesDir(ctx))
                                        rcManager.loadRCFiles()
                                        onCurrentRCFileChange(rcManager.getRcfile(1))
                                        onRefresh()
                                    }
                                )
                            } else {
                                onPrompt(
                                    ctx.getString(R.string.profile_name),
                                    currentRCFile.name,
                                    "",
                                    { name ->
                                        currentRCFile.name = name
                                        currentRCFile.save()
                                        onRefresh()
                                    }
                                )
                            }
                        } else {
                            AppUtils.showToast(ctx, R.string.no_profile_selected)
                        }
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                    }

                    // Duplicate Profile Button
                    IconButton(onClick = {
                        if (currentRCFile != null) {
                            onConfirm(
                                ctx.getString(R.string.confirmation),
                                ctx.getString(R.string.do_you_want_to_duplicate_this_profile),
                                {
                                    val duplicated = rcManager.duplicateRCFile(currentRCFile)
                                    onCurrentRCFileChange(duplicated)
                                    onRefresh()
                                }
                            )
                        } else {
                            AppUtils.showToast(ctx, R.string.no_profile_selected)
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.duplicate))
                    }

                    // Export Profile Button
                    IconButton(onClick = {
                        if (currentRCFile != null) {
                            val exported = rcManager.exportRCFile(currentRCFile)
                            if (exported != null) {
                                val path = exported.path.substring(exported.path.indexOf(Environment.DIRECTORY_DOWNLOADS))
                                AppUtils.showToast(ctx, ctx.getString(R.string.profile_exported_to) + " " + path)
                            }
                        } else {
                            AppUtils.showToast(ctx, R.string.no_profile_selected)
                        }
                    }) {
                        Icon(Icons.Filled.Publish, contentDescription = stringResource(R.string.export_container_profile))
                    }

                    // Delete Profile Button
                    IconButton(onClick = {
                        if (currentRCFile != null) {
                            if (currentRCFile.id == 1) {
                                AppUtils.showToast(ctx, R.string.cannot_remove_default_profile)
                            } else {
                                onConfirm(
                                    ctx.getString(R.string.confirmation),
                                    ctx.getString(R.string.do_you_want_to_remove_this_profile),
                                    {
                                        rcManager.removeRCFile(currentRCFile)
                                        onCurrentRCFileChange(null)
                                        onRefresh()
                                    }
                                )
                            }
                        } else {
                            AppUtils.showToast(ctx, R.string.no_profile_selected)
                        }
                    }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.remove),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterRadioButton(label = "All", selected = filterMode == 0, onClick = { filterMode = 0 })
            FilterRadioButton(label = "Enabled", selected = filterMode == 1, onClick = { filterMode = 1 })
            FilterRadioButton(label = "Disabled", selected = filterMode == 2, onClick = { filterMode = 2 })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Groups Section Header ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Groups",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = {
                    if (currentRCFile != null) {
                        onPrompt(
                            ctx.getString(R.string.group_name),
                            "",
                            "",
                            { name ->
                                currentRCFile.groups.add(RCGroup(name, "", true, null))
                                rcManager.saveAllRCFiles()
                                onRefresh()
                            }
                        )
                    } else {
                        AppUtils.showToast(ctx, R.string.no_profile_selected)
                    }
                }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Group")
                }
                IconButton(onClick = {
                    if (currentRCFile != null) {
                        onShowImportGroup()
                    } else {
                        AppUtils.showToast(ctx, R.string.no_profile_selected)
                    }
                }) {
                    Icon(Icons.Filled.Input, contentDescription = "Import Group")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---- List of Groups ----
        if (currentRCFile == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No profile selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val filteredGroups = remember(currentRCFile.groups, filterMode, refreshKey) {
                currentRCFile.groups.filter { group ->
                    val matchesFilter = when (filterMode) {
                        1 -> group.isEnabled
                        2 -> !group.isEnabled
                        else -> true
                    }
                    matchesFilter
                }
            }

            if (filteredGroups.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No groups found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredGroups) { group ->
                        GroupListItemCard(
                            group = group,
                            onGroupClick = { onGroupClick(group) },
                            onEnabledToggle = { isChecked ->
                                group.isEnabled = isChecked
                                rcManager.saveAllRCFiles()
                                onRefresh()
                            },
                            onDelete = {
                                onConfirm(
                                    ctx.getString(R.string.confirmation),
                                    ctx.getString(R.string.do_you_want_to_remove_this_group),
                                    {
                                        currentRCFile.groups.remove(group)
                                        rcManager.saveAllRCFiles()
                                        onRefresh()
                                    }
                                )
                            },
                            onDuplicate = {
                                onConfirm(
                                    ctx.getString(R.string.confirmation),
                                    ctx.getString(R.string.do_you_want_to_duplicate_this_group),
                                    {
                                        val copied = RCGroup.copy(group)
                                        var newName: String
                                        var i = 1
                                        while (true) {
                                            newName = "${copied.groupName} ($i)"
                                            if (currentRCFile.groups.none { it.groupName == newName }) {
                                                break
                                            }
                                            i++
                                        }
                                        copied.groupName = newName
                                        currentRCFile.groups.add(copied)
                                        rcManager.saveAllRCFiles()
                                        onRefresh()
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupListItemCard(
    group: RCGroup,
    onGroupClick: () -> Unit,
    onEnabledToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
        shape = RoundedCornerShape(GlassTokens.CardRadius),
        border = glassCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onGroupClick() }
            ) {
                Text(
                    text = group.groupName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            GlassSwitch(
                checked = group.isEnabled,
                onCheckedChange = onEnabledToggle
            )

            Spacer(modifier = Modifier.width(8.dp))

            var showGroupMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showGroupMenu = true }) {
                    Icon(Icons.Filled.MoreVert, null)
                }
                DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate)) },
                        onClick = {
                            showGroupMenu = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove)) },
                        onClick = {
                            showGroupMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRadioButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onClick() }
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---- Group Editor Screen ----
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RCGroupEditor(
    group: RCGroup,
    onBack: () -> Unit,
    onPrompt: (String, String, String, (String) -> Unit) -> Unit,
    onConfirm: (String, String, () -> Unit) -> Unit,
    onChoice: (String, List<String>, (Int) -> Unit) -> Unit,
    onGroupNameChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    var groupName by remember { mutableStateOf(group.groupName) }
    var refreshKey by remember { mutableStateOf(0) }
    val itemsList = remember(refreshKey) { group.items }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back Button & Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Edit Group",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        // Group Name Input
        OutlinedTextField(
            value = groupName,
            onValueChange = {
                groupName = it
                group.groupName = it
                onGroupNameChanged()
            },
            label = { Text(stringResource(R.string.group_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        ,
            shape = GlassFieldShape,
            colors = glassFieldColors())

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.processes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            GlassSolidPill(
                onClick = {
                    onPrompt(
                        ctx.getString(R.string.process_name),
                        "",
                        "",
                        { name ->
                            group.items.add(RCItem(name, "", null))
                            onGroupNameChanged()
                            refreshKey++
                        }
                    )
                },
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (itemsList.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No processes configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(itemsList) { item ->
                    ProcessItemCard(
                        item = item,
                        onPrompt = onPrompt,
                        onConfirm = onConfirm,
                        onChoice = onChoice,
                        onChanged = onGroupNameChanged,
                        onDelete = {
                            onConfirm(
                                ctx.getString(R.string.confirmation),
                                ctx.getString(R.string.do_you_want_to_remove_this_process),
                                {
                                    group.items.remove(item)
                                    onGroupNameChanged()
                                    refreshKey++
                                }
                            )
                        },
                        onDuplicate = {
                            onConfirm(
                                ctx.getString(R.string.confirmation),
                                ctx.getString(R.string.do_you_want_to_duplicate_this_process),
                                {
                                    val copied = RCItem.copy(item)
                                    var newName: String
                                    var i = 1
                                    while (true) {
                                        newName = "${copied.processName} ($i)"
                                        if (group.items.none { it.processName == newName }) {
                                            break
                                        }
                                        i++
                                    }
                                    copied.processName = newName
                                    group.items.add(copied)
                                    onGroupNameChanged()
                                    refreshKey++
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessItemCard(
    item: RCItem,
    onPrompt: (String, String, String, (String) -> Unit) -> Unit,
    onConfirm: (String, String, () -> Unit) -> Unit,
    onChoice: (String, List<String>, (Int) -> Unit) -> Unit,
    onChanged: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var refreshVarsKey by remember { mutableStateOf(0) }
    val varList = remember(refreshVarsKey) { item.varMap.entries.toList() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
        shape = RoundedCornerShape(GlassTokens.CardRadius),
        border = glassCardBorder(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Process Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.processName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Control Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                val fields = RCField.getEnabledField().toList()
                                onChoice(ctx.getString(R.string.variable_name), fields) { index ->
                                    val name = fields[index]
                                    if (item.varMap.containsKey(name)) {
                                        AppUtils.showToast(ctx, R.string.variable_already_exists)
                                    } else {
                                        item.varMap[name] = ""
                                        onChanged()
                                        refreshVarsKey++
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Add, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Var")
                        }

                        IconButton(onClick = {
                            onPrompt(
                                ctx.getString(R.string.process_name),
                                item.processName,
                                "",
                                { name ->
                                    item.processName = name
                                    onChanged()
                                    refreshVarsKey++
                                }
                            )
                        }) {
                            Icon(Icons.Filled.Edit, null)
                        }

                        IconButton(onClick = onDuplicate) {
                            Icon(Icons.Filled.ContentCopy, null)
                        }

                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // List of Variables
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        varList.forEach { entry ->
                            VarConfigRow(
                                key = entry.key,
                                value = entry.value,
                                onKeyChange = { newKey ->
                                    val trimmed = newKey.replace(" ", "")
                                    if (trimmed.isNotEmpty() && trimmed != entry.key) {
                                        if (item.varMap.containsKey(trimmed)) {
                                            AppUtils.showToast(ctx, R.string.variable_already_exists)
                                        } else {
                                            val oldVal = item.varMap.remove(entry.key) ?: ""
                                            item.varMap[trimmed] = oldVal
                                            onChanged()
                                            refreshVarsKey++
                                        }
                                    }
                                },
                                onValueChange = { newVal ->
                                    item.varMap[entry.key] = newVal
                                    onChanged()
                                },
                                onShowKeyChoices = {
                                    val fields = RCField.getEnabledField().toList()
                                    onChoice(ctx.getString(R.string.variable_name), fields) { index ->
                                        val name = fields[index]
                                        if (item.varMap.containsKey(name)) {
                                            AppUtils.showToast(ctx, R.string.variable_already_exists)
                                        } else {
                                            val oldVal = item.varMap.remove(entry.key) ?: ""
                                            item.varMap[name] = oldVal
                                            onChanged()
                                            refreshVarsKey++
                                        }
                                    }
                                },
                                onShowValueChoices = {
                                    try {
                                        val field = RCField.valueOf(entry.key)
                                        if (field.isEnabled) {
                                            val selections = field.selections.toList()
                                            if (selections.isNotEmpty()) {
                                                onChoice(entry.key, selections) { index ->
                                                    item.varMap[entry.key] = selections[index]
                                                    onChanged()
                                                    refreshVarsKey++
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {}
                                },
                                onDelete = {
                                    onConfirm(
                                        ctx.getString(R.string.confirmation),
                                        ctx.getString(R.string.do_you_want_to_remove_this_variable),
                                        {
                                            item.varMap.remove(entry.key)
                                            onChanged()
                                            refreshVarsKey++
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VarConfigRow(
    key: String,
    value: String,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onShowKeyChoices: () -> Unit,
    onShowValueChoices: () -> Unit,
    onDelete: () -> Unit,
) {
    var keyInput by remember(key) { mutableStateOf(key) }
    var valueInput by remember(value) { mutableStateOf(value) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Variable Key Field
        OutlinedTextField(
            value = keyInput,
            onValueChange = {
                keyInput = it
                onKeyChange(it)
            },
            trailingIcon = {
                IconButton(onClick = onShowKeyChoices) {
                    Icon(Icons.Filled.List, null)
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1.2f)
        ,
            shape = GlassFieldShape,
            colors = glassFieldColors())

        Spacer(modifier = Modifier.width(8.dp))

        // Variable Value Field
        OutlinedTextField(
            value = valueInput,
            onValueChange = {
                valueInput = it
                onValueChange(it)
            },
            trailingIcon = {
                IconButton(onClick = onShowValueChoices) {
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1f)
        ,
            shape = GlassFieldShape,
            colors = glassFieldColors())

        Spacer(modifier = Modifier.width(4.dp))

        // Delete Variable Button
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportGroupDialogCompose(
    rcManager: RCManager,
    onGroupSelected: (RCGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val rcfiles = remember { rcManager.rcFiles }
    var selectedFileIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_group), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Dropdown to select RCFile
                if (rcfiles.isNotEmpty()) {
                    val fileNames = rcfiles.map { it.name }
                    SpinnerRow(
                        label = "Profile",
                        entries = fileNames,
                        selected = fileNames[selectedFileIndex],
                        onSelected = { selected ->
                            selectedFileIndex = fileNames.indexOf(selected).coerceAtLeast(0)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Groups",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // List of Groups in selected RCFile
                if (rcfiles.isNotEmpty()) {
                    val groups = rcfiles[selectedFileIndex].groups
                    if (groups.isEmpty()) {
                        Text("No groups in selected profile", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(groups) { group ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val copy = RCGroup.copy(group)
                                            onGroupSelected(copy)
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Folder, null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = group.groupName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptDialog(
    title: String,
    initialValue: String = "",
    label: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var valueState by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = valueState,
                onValueChange = { valueState = it },
                label = if (label.isNotEmpty()) { { Text(label) } } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            ,
                shape = GlassFieldShape,
                colors = glassFieldColors())
        },
        confirmButton = {
            GlassSolidPill(
                onClick = { onConfirm(valueState) },
            ) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message) },
        confirmButton = {
            GlassSolidPill(
                onClick = onConfirm,
            ) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleChoiceDialog(
    title: String,
    choices: List<String>,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(choices.size) { index ->
                    Text(
                        text = choices[index],
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )
}
