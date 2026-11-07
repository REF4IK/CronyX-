package com.winlator.cmod.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.inputcontrols.CustomIconManager
import com.winlator.cmod.inputcontrols.IconPackManager
import com.winlator.cmod.widget.InputControlsView
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * Редактор виртуальных кнопок ПРЯМО поверх игры: тулбар поверх живого
 * InputControlsView в edit-mode (сам view остаётся в иерархии XServerDisplayActivity).
 */
@Composable
fun InGameControlsEditorOverlay(
    activity: XServerDisplayActivity,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = activity.inputControlsView ?: return
    val profile = view.profile ?: return

    val customIconManager = remember { CustomIconManager(ctx) }
    val iconPackManager = remember { IconPackManager(ctx) }

    var showQuickAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var bindingPickerTarget by remember { mutableStateOf<Pair<com.winlator.cmod.inputcontrols.ControlElement, Int>?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    val refresh: () -> Unit = { refreshTick = refreshTick + 1 }

    // ---- Перетаскивание тулбара (паттерн ControlsEditorScreen) ----
    var toolbarOffset by remember { mutableStateOf(Offset.Zero) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val screenSize = with(LocalDensity.current) {
        val cfg = LocalConfiguration.current
        IntSize(cfg.screenWidthDp.dp.roundToPx(), cfg.screenHeightDp.dp.roundToPx())
    }

    // Тулбар не должен перекрывать ввод: клики проходят мимо него в игру,
    // перехватывает только сама панель.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(toolbarOffset.x.roundToInt(), toolbarOffset.y.roundToInt()) }
                .onSizeChanged { toolbarSize = it }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newX = (toolbarOffset.x + dragAmount.x)
                            .coerceIn(-toolbarSize.width / 2f, (screenSize.width - toolbarSize.width) / 2f)
                        val newY = (toolbarOffset.y + dragAmount.y)
                            .coerceIn(0f, (screenSize.height - toolbarSize.height).toFloat())
                        toolbarOffset = Offset(newX, newY)
                    }
                },
        ) {
            Surface(
                modifier = Modifier.padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xE6202020),
                contentColor = Color.White,
                shadowElevation = 4.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = null,
                        tint = Color(0x66ffffff),
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            stringResource(R.string.controls_edit_ingame),
                            fontSize = 13.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            profile.getName(),
                            fontSize = 13.sp,
                            color = Color(0xff11a9ef),
                        )
                    }
                    VerticalDivider(modifier = Modifier.height(32.dp), color = Color(0xff444444))
                    ToolbarIconButtonShared(Icons.Filled.Add, stringResource(R.string.add)) { showQuickAdd = true }
                    ToolbarIconButtonShared(Icons.Filled.Undo, "Undo") {
                        if (!view.undoLastEdit()) AppUtils.showToast(ctx, R.string.no_changes_to_undo)
                    }
                    ToolbarIconButtonShared(Icons.Filled.Redo, "Redo") {
                        if (!view.redoLastEdit()) AppUtils.showToast(ctx, R.string.no_changes_to_redo)
                    }
                    ToolbarIconButtonShared(Icons.Filled.Delete, stringResource(R.string.remove)) {
                        if (!view.removeElement()) AppUtils.showToast(ctx, R.string.no_control_element_selected)
                    }
                    ToolbarIconButtonShared(Icons.Filled.Settings, stringResource(R.string.settings)) {
                        if (view.selectedElement != null) showSettings = true
                        else AppUtils.showToast(ctx, R.string.no_control_element_selected)
                    }
                    ToolbarIconButtonShared(Icons.Filled.Palette, "Тема") { showThemeDialog = true }
                    VerticalDivider(modifier = Modifier.height(32.dp), color = Color(0xff444444))
                    ToolbarIconButtonShared(Icons.Filled.Close, stringResource(R.string.controls_edit_exit)) { onClose() }
                }
            }
        }
    }

    // ---- Quick Add ----
    if (showQuickAdd) {
        QuickAddControlDialog(
            onDismiss = { showQuickAdd = false },
            onAdd = { type, binding ->
                if (!view.addElement(type, binding)) {
                    AppUtils.showToast(ctx, R.string.no_profile_selected)
                }
                refresh()
            },
        )
    }

    // ---- Element Settings ----
    if (showSettings) {
        val element = view.selectedElement
        if (element != null) {
            ElementSettingsDialog(
                element = element,
                view = view,
                profile = profile,
                customIconManager = customIconManager,
                iconPackManager = iconPackManager,
                refreshKey = refreshTick,
                onOpenBindingPicker = { el, index -> bindingPickerTarget = el to index },
                onChanged = refresh,
                onDismiss = { showSettings = false },
            )
        } else {
            LaunchedEffect(Unit) { showSettings = false }
        }
    }

    // ---- Theme Picker ----
    if (showThemeDialog) {
        ThemePickerDialog(
            profile = profile,
            view = view,
            onChanged = refresh,
            onDismiss = { showThemeDialog = false }
        )
    }

    // ---- Binding Picker ----
    bindingPickerTarget?.let { (element, index) ->
        BindingPickerDialog(
            element = element,
            index = index,
            view = view,
            profile = profile,
            onChanged = refresh,
            onDismiss = { bindingPickerTarget = null }
        )
    }
}

@Composable
private fun ToolbarIconButtonShared(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription, tint = Color.White)
    }
}
