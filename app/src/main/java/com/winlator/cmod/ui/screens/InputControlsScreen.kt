package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSwitch
import androidx.compose.ui.draw.alpha
import com.winlator.cmod.ui.components.GlassChevron
import com.winlator.cmod.ui.components.GlassGroupGap
import com.winlator.cmod.ui.components.GlassPillButton
import com.winlator.cmod.ui.components.GlassPillRow
import com.winlator.cmod.ui.components.GlassRow
import com.winlator.cmod.ui.components.GlassSectionHeader
import com.winlator.cmod.ui.components.GlassSliderRow
import com.winlator.cmod.ui.components.GlassSurface
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassDescColor
import com.winlator.cmod.ui.components.glassGroupDivider
import com.winlator.cmod.ui.components.GlassRoundIconButton
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.widget.SeekBar
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.ControlsEditorActivity
import com.winlator.cmod.GamePadTestActivity
import com.winlator.cmod.IconManagerActivity
import com.winlator.cmod.R
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.HttpUtils
import com.winlator.cmod.inputcontrols.ControlsProfile
import com.winlator.cmod.inputcontrols.InputControlsManager
import com.winlator.cmod.math.Mathf
import com.winlator.cmod.widget.InputControlsView
import com.winlator.cmod.widget.ProfilePreviewView
import org.json.JSONObject

private const val INPUT_CONTROLS_URL = "https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/%s"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputControlsScreen(
    selectedProfileId: Int = 0,
    onOpenGamepadTest: () -> Unit = {},
    onOpenIconManager: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val manager = remember { InputControlsManager(ctx) }
    val prefs = remember { MmkvPreferences() }
    val density = LocalDensity.current

    var profiles by remember { mutableStateOf(manager.profiles ?: emptyList()) }
    var currentProfile by remember {
        mutableStateOf<ControlsProfile?>(
            if (selectedProfileId > 0) manager.getProfile(selectedProfileId) else null
        )
    }
    var cursorSpeedProgress by remember {
        mutableStateOf(((currentProfile?.cursorSpeed ?: 1.0f) * 100).toInt())
    }
    var overlayOpacityProgress by remember {
        mutableStateOf((prefs.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY) * 100).toInt())
    }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadProfilesList by remember { mutableStateOf<List<String>?>(null) }
    var selectedDownloadIndices by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var controllersKey by remember { mutableStateOf(0) }
    var profileExpanded by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val jsonStr = FileUtils.readString(ctx, uri)
                val imported = manager.importProfile(JSONObject(jsonStr))
                currentProfile = imported
                profiles = manager.profiles ?: emptyList()
                cursorSpeedProgress = (imported.cursorSpeed * 100).toInt()
                AppUtils.showToast(ctx, "Profile imported")
            } catch (_: Exception) {
                AppUtils.showToast(ctx, R.string.unable_to_import_profile)
            }
        }
    }

    fun reloadProfiles() {
        controllersKey++
        profiles = manager.profiles ?: emptyList()
    }

    fun onProfileChanged(p: ControlsProfile?) {
        currentProfile = p
        cursorSpeedProgress = ((p?.cursorSpeed ?: 1.0f) * 100).toInt()
    }

    val controllers = remember(currentProfile?.id, controllersKey) {
        val saved = currentProfile?.loadControllers() ?: arrayListOf()
        val merged = ArrayList(saved)
        try {
            val connected = com.winlator.cmod.inputcontrols.ExternalController.getControllers()
            for (c in connected) {
                if (merged.none { it.id == c.id }) merged.add(c)
            }
        } catch (_: Exception) {}
        merged
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = GlassTokens.RowPaddingH,
                end = GlassTokens.RowPaddingH,
                top = 4.dp,
                bottom = 24.dp,
            ),
    ) {
        // ---- Профиль ----
        GlassSectionHeader(stringResource(R.string.profile))
        GlassSurface(Modifier.fillMaxWidth()) {
            Box {
                GlassRow(
                    title = stringResource(R.string.profile),
                    value = currentProfile?.name ?: stringResource(R.string.no_profile_selected),
                    onClick = { profileExpanded = true },
                )
                DropdownMenu(
                    expanded = profileExpanded,
                    onDismissRequest = { profileExpanded = false },
                ) {
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name) },
                            onClick = {
                                onProfileChanged(profile)
                                profileExpanded = false
                            },
                        )
                    }
                    if (profiles.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "(No profiles)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = { profileExpanded = false },
                        )
                    }
                }
            }

            // Действия над профилем — круглые иконки, а не квадратные кнопки.
            Row(
                Modifier
                    .fillMaxWidth()
                    .glassGroupDivider()
                    .padding(
                        horizontal = GlassTokens.RowPaddingH,
                        vertical = GlassTokens.RowPaddingV - 4.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassRoundIconButton(Icons.Filled.Add, stringResource(R.string.add)) {
                    ContentDialog.prompt(ctx, R.string.profile_name, null) { name ->
                        val pr = manager.createProfile(name)
                        onProfileChanged(pr); reloadProfiles()
                    }
                }
                GlassRoundIconButton(
                    Icons.Filled.Edit,
                    stringResource(R.string.edit),
                    enabled = currentProfile != null,
                ) {
                    val pr = currentProfile ?: return@GlassRoundIconButton
                    ContentDialog.prompt(ctx, R.string.profile_name, pr.name) { name ->
                        pr.setName(name); pr.save(); reloadProfiles()
                    }
                }
                GlassRoundIconButton(
                    Icons.Filled.ContentCopy,
                    stringResource(R.string.duplicate),
                    enabled = currentProfile != null,
                ) {
                    val pr = currentProfile ?: return@GlassRoundIconButton
                    ContentDialog.confirm(ctx, R.string.do_you_want_to_duplicate_this_profile) {
                        val dup = manager.duplicateProfile(pr)
                        onProfileChanged(dup); reloadProfiles()
                    }
                }
                GlassRoundIconButton(
                    Icons.Filled.Delete,
                    stringResource(R.string.remove),
                    enabled = currentProfile != null,
                    tint = MaterialTheme.colorScheme.error,
                ) {
                    val pr = currentProfile ?: return@GlassRoundIconButton
                    ContentDialog.confirm(ctx, R.string.do_you_want_to_remove_this_profile) {
                        manager.removeProfile(pr)
                        onProfileChanged(null); reloadProfiles()
                    }
                }
            }
        }

        GlassGroupGap()

        // ---- Параметры ----
        GlassSurface(Modifier.fillMaxWidth()) {
            GlassRow(
                title = stringResource(R.string.controls_profile_preview),
                icon = Icons.Filled.Visibility,
                onClick = { showPreviewDialog = true },
            )

            GlassSliderRow(
                title = stringResource(R.string.cursor_speed),
                valueText = "$cursorSpeedProgress%",
                value = cursorSpeedProgress.toFloat(),
                valueRange = 10f..200f,
                onValueChange = { v ->
                    cursorSpeedProgress = v.toInt()
                    currentProfile?.let { pr ->
                        pr.cursorSpeed = v / 100f; pr.save()
                    }
                },
            )

            GlassSliderRow(
                title = stringResource(R.string.overlay_opacity),
                valueText = "$overlayOpacityProgress%",
                value = overlayOpacityProgress.toFloat(),
                valueRange = 10f..100f,
                onValueChange = { v ->
                    val rounded = Mathf.roundTo(v, 5f).toInt()
                    overlayOpacityProgress = rounded
                    prefs.edit().putFloat("overlay_opacity", rounded / 100.0f).apply()
                },
            )
        }

        GlassGroupGap()

        // ---- Импорт / экспорт ----
        GlassPillRow(
            first = {
                Box {
                    GlassPillButton(
                        text = stringResource(R.string.import_profile),
                        icon = Icons.Filled.FileDownload,
                        onClick = { showImportMenu = true },
                    )
                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.open_file)) },
                            onClick = { showImportMenu = false; importLauncher.launch(arrayOf("*/*")) },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_file)) },
                            onClick = { showImportMenu = false; showDownloadDialog = true },
                            leadingIcon = { Icon(Icons.Filled.CloudDownload, null) },
                        )
                    }
                }
            },
            second = {
                GlassPillButton(
                    text = stringResource(R.string.export_profile),
                    icon = Icons.Filled.FileUpload,
                    enabled = currentProfile != null,
                    onClick = {
                        val pr = currentProfile ?: return@GlassPillButton
                        val f = manager.exportProfile(pr)
                        if (f != null) AppUtils.showToast(ctx, "Profile exported to ${f.path}")
                    },
                )
            },
        )

        GlassGroupGap()

        // ---- Переходы ----
        GlassSurface(Modifier.fillMaxWidth()) {
            GlassRow(
                title = stringResource(R.string.controls_editor),
                enabled = currentProfile != null,
                onClick = {
                    val profile = currentProfile
                    if (profile != null) {
                        val intent = Intent(ctx, ControlsEditorActivity::class.java).apply {
                            putExtra("profile_id", profile.id)
                        }
                        ctx.startActivity(intent)
                    } else {
                        AppUtils.showToast(ctx, R.string.no_profile_selected)
                    }
                },
            )
            GlassRow(
                title = stringResource(R.string.gamepad_test),
                onClick = onOpenGamepadTest,
            )
            GlassRow(
                title = stringResource(R.string.icon_manager),
                onClick = onOpenIconManager,
            )
        }

        GlassGroupGap()

        // ---- Внешние контроллеры ----
        GlassSectionHeader(stringResource(R.string.external_controllers))
        GlassSurface(Modifier.fillMaxWidth()) {
            if (controllers.isEmpty()) {
                Text(
                    stringResource(R.string.no_items_to_display),
                    fontSize = GlassTokens.DescSize,
                    color = glassDescColor(),
                    modifier = Modifier.padding(
                        horizontal = GlassTokens.RowPaddingH,
                        vertical = GlassTokens.RowPaddingV,
                    ),
                )
            } else {
                controllers.forEach { controller ->
                    val isConnected = try { controller.isConnected() } catch (_: Exception) { false }
                    val isSaved = currentProfile?.loadControllers()?.any { it.id == controller.id } == true
                    GlassRow(
                        title = controller.getName(),
                        desc = "${controller.getControllerBindingCount()} ${stringResource(R.string.bindings)}" +
                            if (!isConnected) " • ${stringResource(R.string.not_connected)}" else "",
                        icon = Icons.Filled.Gamepad,
                        onClick = {
                            if (currentProfile != null) {
                                val intent = Intent(
                                    ctx,
                                    com.winlator.cmod.ExternalControllerBindingsActivity::class.java,
                                ).apply {
                                    putExtra("profile_id", currentProfile!!.id)
                                    putExtra("controller_id", controller.id)
                                }
                                ctx.startActivity(intent)
                            } else {
                                AppUtils.showToast(ctx, R.string.no_profile_selected)
                            }
                        },
                        showChevron = false,
                        trailing = {
                            if (isSaved && controller.getControllerBindingCount() > 0) {
                                GlassRoundIconButton(
                                    icon = Icons.Filled.Delete,
                                    description = stringResource(R.string.remove),
                                    tint = MaterialTheme.colorScheme.error,
                                    size = 38.dp,
                                    onClick = {
                                        ContentDialog.confirm(
                                            ctx,
                                            R.string.do_you_want_to_remove_this_controller,
                                        ) {
                                            currentProfile?.let { pr ->
                                                pr.removeController(controller); pr.save(); reloadProfiles()
                                            }
                                        }
                                    },
                                )
                            } else {
                                GlassChevron(
                                    enabled = isConnected,
                                    modifier = Modifier.alpha(if (isConnected) 1f else 0.38f),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    // ---- Preview Dialog ----
    if (showPreviewDialog && currentProfile != null) {
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = { Text(stringResource(R.string.controls_profile_preview)) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(ComposeColor(0xFF1a1a1a)),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { ctx2 ->
                            ProfilePreviewView(ctx2).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setProfile(currentProfile)
                            }
                        },
                        update = { it.setProfile(currentProfile) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ---- Download Dialog ----
    if (showDownloadDialog) {
        LaunchedEffect(showDownloadDialog) {
            if (downloadProfilesList == null) {
                HttpUtils.download(String.format(INPUT_CONTROLS_URL, "index.txt")) { result ->
                    if (result != null && result.isNotEmpty()) {
                        downloadProfilesList = result.split("\n").filter { it.isNotBlank() }
                        selectedDownloadIndices = downloadProfilesList!!.map { false }
                    } else {
                        AppUtils.showToast(ctx, R.string.unable_to_load_profile_list)
                        showDownloadDialog = false
                    }
                }
            }
        }

        if (downloadProfilesList != null) {
            AlertDialog(
                onDismissRequest = { showDownloadDialog = false; downloadProfilesList = null },
                title = { Text("Download Profiles") },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                    ) {
                        downloadProfilesList!!.forEachIndexed { i, name ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                GlassSwitch(
                                    checked = selectedDownloadIndices.getOrElse(i) { false },
                                    onCheckedChange = { checked ->
                                        selectedDownloadIndices = selectedDownloadIndices.toMutableList().also {
                                            if (i < it.size) it[i] = checked
                                        }
                                    }
                                )
                                Text(name, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    HttpUtils.download(String.format(INPUT_CONTROLS_URL, name)) { jsonStr ->
                                        if (jsonStr != null) {
                                            (ctx as? Activity)?.runOnUiThread {
                                                ContentDialog(ctx, com.winlator.cmod.R.layout.input_controls_dialog).apply {
                                                    setTitle("Preview: $name"); show()
                                                }
                                            }
                                        }
                                    }
                                }) { Icon(Icons.Filled.Visibility, "Preview") }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedNames = downloadProfilesList!!.filterIndexed { i, _ -> selectedDownloadIndices.getOrElse(i) { false } }
                        if (selectedNames.isNotEmpty()) {
                            ContentDialog.confirm(ctx, R.string.do_you_want_to_download_the_selected_profiles) {
                                for (name in selectedNames) {
                                    HttpUtils.download(String.format(INPUT_CONTROLS_URL, name)) { jsonStr ->
                                        if (jsonStr != null) {
                                            manager.importProfile(JSONObject(jsonStr))
                                        }
                                    }
                                }
                                (ctx as? Activity)?.runOnUiThread {
                                    reloadProfiles()
                                    AppUtils.showToast(ctx, "Downloaded ${selectedNames.size} profile(s)")
                                }
                            }
                        }
                        showDownloadDialog = false; downloadProfilesList = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDownloadDialog = false; downloadProfilesList = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
