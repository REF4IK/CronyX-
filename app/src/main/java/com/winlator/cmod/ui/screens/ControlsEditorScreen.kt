package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassBadgeFill
import com.winlator.cmod.ui.components.glassSliderColors
import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import com.winlator.cmod.ui.components.GlassSwitch
import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.inputcontrols.AccentTheme
import com.winlator.cmod.inputcontrols.Binding
import com.winlator.cmod.inputcontrols.ControlElement
import com.winlator.cmod.inputcontrols.ControlsProfile
import com.winlator.cmod.inputcontrols.CustomIconManager
import com.winlator.cmod.inputcontrols.IconPackManager
import com.winlator.cmod.inputcontrols.IconPickerDialog
import com.winlator.cmod.inputcontrols.InputControlsManager
import com.winlator.cmod.inputcontrols.VisualStyle
import com.winlator.cmod.widget.InputControlsView
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Экран редактора виртуального управления (Compose-порт ControlsEditorActivity)
// Полотно InputControlsView остаётся нативным View (как и раньше),
// весь UI вокруг него переведён на Compose.
// ---------------------------------------------------------------------------

@Composable
fun ControlsEditorScreen(
    profileId: Int,
    onExit: () -> Unit,
) {
    val ctx = LocalContext.current
    val profile = remember(profileId) {
        InputControlsManager.loadProfile(ctx, ControlsProfile.getProfileFile(ctx, profileId))
    }

    if (profile == null) {
        LaunchedEffect(Unit) {
            AppUtils.showToast(ctx, R.string.no_profile_selected)
            onExit()
        }
        return
    }

    val customIconManager = remember { CustomIconManager(ctx) }
    val iconPackManager = remember { IconPackManager(ctx) }

    val inputControlsView = remember(profile) {
        InputControlsView(ctx).apply {
            setEditMode(true)
            setOverlayOpacity(0.6f)
            setProfile(profile)
        }
    }

    var showQuickAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var bindingPickerTarget by remember { mutableStateOf<Pair<ControlElement, Int>?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    val refresh: () -> Unit = { refreshTick = refreshTick + 1 }

    // ---- Перетаскивание тулбара ----
    var toolbarOffset by remember { mutableStateOf(Offset.Zero) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    val screenSize = with(LocalDensity.current) {
        val cfg = LocalConfiguration.current
        IntSize(cfg.screenWidthDp.dp.roundToPx(), cfg.screenHeightDp.dp.roundToPx())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { inputControlsView },
            modifier = Modifier.fillMaxSize(),
        )

        // ---- Плавающая панель инструментов (перетаскиваемая) ----
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
                        stringResource(R.string.profile),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        profile.getName(),
                        fontSize = 14.sp,
                        color = Color(0xff11a9ef),
                    )
                }
                VerticalDivider(modifier = Modifier.height(32.dp), color = Color(0xff444444))
                ToolbarIconButton(Icons.Filled.Add, stringResource(R.string.add)) { showQuickAdd = true }
                ToolbarIconButton(Icons.Filled.Undo, "Undo") {
                    if (!inputControlsView.undoLastEdit()) AppUtils.showToast(ctx, R.string.no_changes_to_undo)
                }
                ToolbarIconButton(Icons.Filled.Redo, "Redo") {
                    if (!inputControlsView.redoLastEdit()) AppUtils.showToast(ctx, R.string.no_changes_to_redo)
                }
                ToolbarIconButton(Icons.Filled.Delete, stringResource(R.string.remove)) {
                    if (!inputControlsView.removeElement()) AppUtils.showToast(ctx, R.string.no_control_element_selected)
                }
                ToolbarIconButton(Icons.Filled.Settings, stringResource(R.string.settings)) {
                    val element = inputControlsView.getSelectedElement()
                    if (element != null) showSettings = true
                    else AppUtils.showToast(ctx, R.string.no_control_element_selected)
                }
                ToolbarIconButton(Icons.Filled.Palette, "Тема") { showThemeDialog = true }
                VerticalDivider(modifier = Modifier.height(32.dp), color = Color(0xff444444))
                ToolbarIconButton(Icons.Filled.Close, stringResource(R.string.cancel)) { onExit() }
            }
        }
        }
    }

    // ---- Quick Add ----
    if (showQuickAdd) {
        QuickAddControlDialog(
            onDismiss = { showQuickAdd = false },
            onAdd = { type, binding ->
                if (!inputControlsView.addElement(type, binding)) {
                    AppUtils.showToast(ctx, R.string.no_profile_selected)
                }
                refresh()
            },
        )
    }

    // ---- Element Settings ----
    if (showSettings) {
        val element = inputControlsView.getSelectedElement()
        if (element != null) {
            ElementSettingsDialog(
                element = element,
                view = inputControlsView,
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
            view = inputControlsView,
            onChanged = refresh,
            onDismiss = { showThemeDialog = false }
        )
    }

    // ---- Binding Picker ----
    bindingPickerTarget?.let { (element, index) ->
        BindingPickerDialog(
            element = element,
            index = index,
            view = inputControlsView,
            profile = profile,
            onChanged = refresh,
            onDismiss = { bindingPickerTarget = null },
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription, tint = Color.White)
    }
}

// ---------------------------------------------------------------------------
// Quick Add Control
// ---------------------------------------------------------------------------

@Composable
private fun QuickAddControlDialog(
    onDismiss: () -> Unit,
    onAdd: (ControlElement.Type, Binding) -> Unit,
) {
    val quickBindings = listOf(
        Binding.KEY_W, Binding.KEY_A, Binding.KEY_S, Binding.KEY_D,
        Binding.KEY_SPACE, Binding.KEY_ENTER, Binding.KEY_ESC,
        Binding.MOUSE_LEFT_BUTTON, Binding.MOUSE_RIGHT_BUTTON,
        Binding.GAMEPAD_BUTTON_A, Binding.GAMEPAD_BUTTON_B, Binding.GAMEPAD_BUTTON_X, Binding.GAMEPAD_BUTTON_Y,
    )
    data class QuickItem(val label: String, val type: ControlElement.Type, val binding: Binding)
    val items = listOf(
        QuickItem("Button", ControlElement.Type.BUTTON, Binding.NONE),
        QuickItem("Combo Button", ControlElement.Type.COMBO_BUTTON, Binding.KEY_CTRL_L),
        QuickItem("D-Pad", ControlElement.Type.D_PAD, Binding.NONE),
        QuickItem("Stick", ControlElement.Type.STICK, Binding.NONE),
        QuickItem("Trackpad", ControlElement.Type.TRACKPAD, Binding.NONE),
    ) + quickBindings.map { QuickItem(it.toString(), ControlElement.Type.BUTTON, it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_add_control)) },
        text = {
            Column {
                items.forEach { item ->
                    Text(
                        text = item.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAdd(item.type, item.binding)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        },
        confirmButton = {},
    )
}

// ---------------------------------------------------------------------------
// Element Settings
// ---------------------------------------------------------------------------

@Composable
private fun ElementSettingsDialog(
    element: ControlElement,
    view: InputControlsView,
    profile: ControlsProfile,
    customIconManager: CustomIconManager,
    iconPackManager: IconPackManager,
    refreshKey: Int,
    onOpenBindingPicker: (ControlElement, Int) -> Unit,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current

    // Читаем refreshKey, чтобы панель перерисовывалась при внешних изменениях
    // (например, сразу после выбора клавиши в пикере привязок)
    if (refreshKey >= 0) Unit

    var type by remember { mutableStateOf(element.type) }
    var shape by remember { mutableStateOf(element.shape) }
    var range by remember { mutableStateOf(element.range) }
    var orientation by remember { mutableStateOf(element.orientation.toInt()) }
    var bindingCount by remember { mutableStateOf(element.bindingCount) }
    var scale by remember { mutableStateOf((element.scale * 100).roundToInt()) }
    var iconScale by remember { mutableStateOf((element.iconScale * 100).roundToInt()) }
    var opacity by remember { mutableStateOf((element.opacity * 100).roundToInt()) }
    var fillOpacity by remember { mutableStateOf((element.fillOpacity * 100).roundToInt()) }
    var rotation by remember { mutableStateOf(element.rotation.roundToInt()) }
    var toggleSwitch by remember { mutableStateOf(element.isToggleSwitch) }
    var hideBorder by remember { mutableStateOf(element.isHideBorder) }
    var customText by remember { mutableStateOf(element.text) }
    var iconTick by remember { mutableStateOf(0) }
    var scaleUndo by remember { mutableStateOf(false) }
    var opacityUndo by remember { mutableStateOf(false) }
    var fillOpacityUndo by remember { mutableStateOf(false) }
    var rotationUndo by remember { mutableStateOf(false) }
    var iconScaleUndo by remember { mutableStateOf(false) }

    fun commit(block: () -> Unit) {
        block()
        profile.save()
        view.invalidate()
        onChanged()
    }

    fun mutateWithUndo(block: () -> Unit) {
        view.saveSelectedElementStateForUndo()
        commit(block)
    }

    val showButtonExtras = type == ControlElement.Type.BUTTON || type == ControlElement.Type.COMBO_BUTTON
    val showRangeOptions = type == ControlElement.Type.RANGE_BUTTON

    val bindingSlots: List<Pair<Int, Int>> = when (type) {
        ControlElement.Type.BUTTON -> listOf(0 to R.string.binding)
        ControlElement.Type.COMBO_BUTTON -> (0 until bindingCount).map { it to R.string.binding }
        ControlElement.Type.D_PAD, ControlElement.Type.STICK, ControlElement.Type.TRACKPAD -> listOf(
            0 to R.string.binding_up, 1 to R.string.binding_right,
            2 to R.string.binding_down, 3 to R.string.binding_left,
        )
        ControlElement.Type.STEERING_WHEEL -> listOf(1 to R.string.binding_right, 3 to R.string.binding_left)
        else -> emptyList()
    }

    val paletteColors = listOf(
        AndroidColor.TRANSPARENT, AndroidColor.WHITE, 0xff00e5ff.toInt(), 0xff00e676.toInt(),
        0xffffea00.toInt(), 0xffff9100.toInt(), 0xffff1744.toInt(), 0xff7c4dff.toInt(),
    )

    // Полноэкранный оверлей с затемнением (как в FpsCounterSettingsDialog)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        // Боковая панель настроек справа (~половина экрана)
        Surface(modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .clickable(enabled = false, onClick = {}),
                shape = RectangleShape,
                color = glassCardFill(),
                tonalElevation = 0.dp,
                border = glassCardBorder(),
            ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }

                // ---- Тип ----
                SettingsDropdownRow(
                    label = stringResource(R.string.type),
                    options = ControlElement.Type.values().map { it.name.replace("_", "-") },
                    selectedIndex = type.ordinal,
                    onSelect = { idx ->
                        val newType = ControlElement.Type.values()[idx]
                        if (newType != type) {
                            mutateWithUndo {
                                element.setType(newType)
                                type = newType
                                shape = element.shape
                                bindingCount = element.bindingCount
                                orientation = element.orientation.toInt()
                            }
                        }
                    },
                )

                // ---- Форма (только для кнопок) ----
                if (showButtonExtras) {
                    SettingsDropdownRow(
                        label = stringResource(R.string.shape),
                        options = ControlElement.Shape.values().map { it.name.replace("_", " ") },
                        selectedIndex = shape.ordinal,
                        onSelect = { idx ->
                            val newShape = ControlElement.Shape.values()[idx]
                            if (newShape != shape) {
                                mutateWithUndo {
                                    element.setShape(newShape)
                                    shape = newShape
                                }
                            }
                        },
                    )
                }

                // ---- Масштаб ----
                SettingsSliderRow(
                    label = stringResource(R.string.scale),
                    valueText = "$scale%",
                    value = scale.toFloat(),
                    valueRange = 50f..150f,
                    steps = 19,
                    onValueChange = { v ->
                        if (!scaleUndo) {
                            view.saveSelectedElementStateForUndo()
                            scaleUndo = true
                        }
                        scale = v.roundToInt()
                        commit { element.setScale(scale / 100.0f) }
                    },
                )

                // ---- Прозрачность ----
                SettingsSliderRow(
                    label = stringResource(R.string.opacity),
                    valueText = "$opacity%",
                    value = opacity.toFloat(),
                    valueRange = 10f..100f,
                    onValueChange = { v ->
                        if (!opacityUndo) {
                            view.saveSelectedElementStateForUndo()
                            opacityUndo = true
                        }
                        opacity = v.roundToInt()
                        commit { element.setOpacity(opacity / 100.0f) }
                    },
                )

                // ---- Заливка ----
                SettingsSliderRow(
                    label = stringResource(R.string.fill_opacity),
                    valueText = "$fillOpacity%",
                    value = fillOpacity.toFloat(),
                    valueRange = 0f..100f,
                    onValueChange = { v ->
                        if (!fillOpacityUndo) {
                            view.saveSelectedElementStateForUndo()
                            fillOpacityUndo = true
                        }
                        fillOpacity = v.roundToInt()
                        commit { element.setFillOpacity(fillOpacity / 100.0f) }
                    },
                )

                // ---- Поворот ----
                SettingsSliderRow(
                    label = stringResource(R.string.rotation),
                    valueText = "$rotation deg",
                    value = rotation.toFloat(),
                    valueRange = 0f..359f,
                    onValueChange = { v ->
                        if (!rotationUndo) {
                            view.saveSelectedElementStateForUndo()
                            rotationUndo = true
                        }
                        rotation = v.roundToInt()
                        commit { element.setRotation(rotation.toFloat()) }
                    },
                )

                // ---- Цветовая палитра ----
                Text(stringResource(R.string.colors), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    paletteColors.forEach { color ->
                        val isTransparent = color == AndroidColor.TRANSPARENT
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTransparent) Color(0x22ffffff) else Color(color))
                                .border(2.dp, if (isTransparent) Color.White else Color(0x66000000), RoundedCornerShape(6.dp))
                                .clickable {
                                    mutateWithUndo {
                                        element.setBorderColor(color)
                                        element.setTextColor(color)
                                        element.setFillColor(color)
                                    }
                                },
                        )
                    }
                }

                // ---- Range options ----
                if (showRangeOptions) {
                    SettingsDropdownRow(
                        label = stringResource(R.string.range),
                        options = ControlElement.Range.values().map { it.name.replace("_", " ") },
                        selectedIndex = range.ordinal,
                        onSelect = { idx ->
                            val newRange = ControlElement.Range.values()[idx]
                            if (newRange != range) {
                                mutateWithUndo {
                                    element.setRange(newRange)
                                    range = newRange
                                }
                            }
                        },
                    )

                    Text(stringResource(R.string.orientation), style = MaterialTheme.typography.bodyMedium)
                    Row {
                        Row(
                            modifier = Modifier.weight(1f).clickable { mutateWithUndo { element.setOrientation(0); orientation = 0 } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = orientation != 1, onClick = { mutateWithUndo { element.setOrientation(0); orientation = 0 } })
                            Text(stringResource(R.string.horizontal))
                        }
                        Row(
                            modifier = Modifier.weight(1f).clickable { mutateWithUndo { element.setOrientation(1); orientation = 1 } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = orientation == 1, onClick = { mutateWithUndo { element.setOrientation(1); orientation = 1 } })
                            Text(stringResource(R.string.vertical))
                        }
                    }

                    // ---- Колонки ----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.columns), modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                if (bindingCount > 3) {
                                    mutateWithUndo {
                                        element.setBindingCount(bindingCount - 1)
                                        bindingCount = element.bindingCount
                                    }
                                }
                            },
                        ) { Icon(Icons.Filled.Remove, null) }
                        Text("$bindingCount", fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = {
                                if (bindingCount < 8) {
                                    mutateWithUndo {
                                        element.setBindingCount(bindingCount + 1)
                                        bindingCount = element.bindingCount
                                    }
                                }
                            },
                        ) { Icon(Icons.Filled.Add, null) }
                    }
                }

                // ---- Привязки ----
                bindingSlots.forEach { (index, labelRes) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(labelRes),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        GlassPill(
                            onClick = { onOpenBindingPicker(element, index) },
                        ) {
                            Text(element.getBindingAt(index).toString())
                        }
                    }
                }

                // ---- Toggle switch / Hide border ----
                if (showButtonExtras) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassSwitch(
                            checked = toggleSwitch,
                            onCheckedChange = { checked ->
                                mutateWithUndo {
                                    element.setToggleSwitch(checked)
                                    toggleSwitch = checked
                                }
                            },
                        )
                        Text(stringResource(R.string.toggle_switch), modifier = Modifier.clickable {
                            mutateWithUndo {
                                element.setToggleSwitch(!toggleSwitch)
                                toggleSwitch = !toggleSwitch
                            }
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassSwitch(
                            checked = hideBorder,
                            onCheckedChange = { checked ->
                                mutateWithUndo {
                                    element.setHideBorder(checked)
                                    hideBorder = checked
                                }
                            },
                        )
                        Text(stringResource(R.string.hide_border), modifier = Modifier.clickable {
                            mutateWithUndo {
                                element.setHideBorder(!hideBorder)
                                hideBorder = !hideBorder
                            }
                        })
                    }

                    // ---- Свой текст ----
                    Text(stringResource(R.string.custom_text), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { newText ->
                            customText = newText
                            commit { element.setText(newText) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),

                        shape = GlassFieldShape,
                        colors = glassFieldColors())

                    // ---- Размер иконки ----
                    SettingsSliderRow(
                        label = stringResource(R.string.icon_size),
                        valueText = "$iconScale%",
                        value = iconScale.toFloat(),
                        valueRange = 50f..150f,
                        steps = 19,
                        onValueChange = { v ->
                            if (!iconScaleUndo) {
                                view.saveSelectedElementStateForUndo()
                                iconScaleUndo = true
                            }
                            iconScale = v.roundToInt()
                            commit { element.setIconScale(iconScale / 100.0f) }
                        },
                    )

                    // ---- Иконка ----
                    Text(stringResource(R.string.icon), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val iconId = element.getIconId()
                        if (iconId > 0) {
                            val bmp = when {
                                IconPackManager.isPackIcon(iconId) -> iconPackManager.loadIconByGlobalId(iconId)
                                CustomIconManager.isCustomIcon(iconId) -> customIconManager.loadIcon(iconId)
                                else -> null
                            }
                            if (bmp != null) {
                                Box(
                                    modifier = Modifier.size(44.dp).clip(CircleShape).background(glassBadgeFill()),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.padding(4.dp),
                                    )
                                }
                            }
                        } else {
                            Text(
                                "+ " + stringResource(R.string.add_single_icon),
                                color = Color(0xffaaaaaa),
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xff2a2a2a))
                                    .clickable {
                                        IconPickerDialog(ctx as Activity, customIconManager, iconPackManager, element.getIconId()) { iconId ->
                                            commit { element.setIconId(iconId) }
                                            iconTick++
                                        }.show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        GlassPill(
                            onClick = {
                                IconPickerDialog(ctx as Activity, customIconManager, iconPackManager, element.getIconId()) { iconId ->
                                    commit { element.setIconId(iconId) }
                                    iconTick++
                                }.show()
                            },
                        ) { Text(stringResource(R.string.select_icon)) }
                    }
                    // Обновляем подписку на смену иконки
                    if (iconTick >= 0) Unit
                }
            }
        }
    }
}

@Composable
private fun SettingsDropdownRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        GlassPill(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(options.getOrElse(selectedIndex) { "" }, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, option ->
                DropdownMenuItem(
                    text = { Text(option, color = if (idx == selectedIndex) MaterialTheme.colorScheme.primary else Color.Unspecified) },
                    onClick = { expanded = false; onSelect(idx) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        
            colors = glassSliderColors())
    }
}

// ---------------------------------------------------------------------------
// Theme Picker
// ---------------------------------------------------------------------------

// Цвет тела для превью-кружка стиля
private fun styleSwatchBody(style: VisualStyle): Color = when (style) {
    VisualStyle.ORIGINAL -> Color(0x33000000)
    VisualStyle.GAMEHUB -> Color(0x8CFFFFFF)
    VisualStyle.SHADOW -> Color(0xFF0A1422)
    VisualStyle.SLATE -> Color(0xFF14181F)
    VisualStyle.HALO -> Color(0xFF171A20)
    VisualStyle.GLINT -> Color(0xFF101216)
    VisualStyle.RETICLE -> Color(0xFF0E1116)
    VisualStyle.NEON -> Color(0xFF0A0A16)
    VisualStyle.LUMINA -> Color(0xFF2E343C)
}

@Composable
private fun StyleSwatch(style: VisualStyle, accent: AccentTheme) {
    val accentColor = Color(accent.accent)
    val borderWidth = when (style) {
        VisualStyle.HALO -> 3.dp
        VisualStyle.GLINT -> 1.dp
        else -> 1.5.dp
    }
    val borderColor = when (style) {
        VisualStyle.ORIGINAL, VisualStyle.GAMEHUB, VisualStyle.GLINT -> Color.White.copy(alpha = 0.7f)
        else -> accentColor
    }
    Box(
        Modifier.size(28.dp).clip(CircleShape)
            .background(styleSwatchBody(style))
            .border(borderWidth, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (style == VisualStyle.LUMINA || style == VisualStyle.NEON) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accentColor))
        }
    }
}

@Composable
private fun AccentChip(theme: AccentTheme, selected: Boolean, onClick: () -> Unit) {
    val color = Color(theme.accent)
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable { onClick() },
    )
}

@Composable
private fun ThemePickerDialog(
    profile: ControlsProfile,
    view: InputControlsView,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedStyle by remember { mutableStateOf(profile.visualStyleEnum) }
    var selectedAccent by remember { mutableStateOf(profile.accentThemeEnum) }
    val styles = VisualStyle.values()
    val styleNames = VisualStyle.displayNames()
    val accents = AccentTheme.values()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.button_style)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Выбери стиль отрисовки экранных кнопок",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                styles.forEach { style ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { selectedStyle = style }.padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        RadioButton(selected = selectedStyle == style, onClick = { selectedStyle = style })
                        Spacer(Modifier.width(6.dp))
                        Text(styleNames[style.code], modifier = Modifier.weight(1f))
                        StyleSwatch(style, selectedAccent)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                accents.toList().chunked(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { theme ->
                            AccentChip(theme = theme, selected = selectedAccent == theme) { selectedAccent = theme }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                profile.visualStyle = selectedStyle.code
                profile.accentTheme = selectedAccent.code
                view.setVisualStyle(selectedStyle)
                view.setAccentTheme(selectedAccent)
                profile.save()
                view.invalidate()
                onChanged()
                onDismiss()
            }) { Text("Применить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ---------------------------------------------------------------------------
// Binding Picker (Select Key)
// ---------------------------------------------------------------------------

@Composable
private fun BindingPickerDialog(
    element: ControlElement,
    index: Int,
    view: InputControlsView,
    profile: ControlsProfile,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }

    fun applyBinding(binding: Binding) {
        if (binding != element.getBindingAt(index)) {
            view.saveSelectedElementStateForUndo()
            element.setBindingAt(index, binding)
            profile.save()
            view.invalidate()
            onChanged()
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(),
                color = glassCardFill(),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                border = glassCardBorder(),
            ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                    Text(
                        stringResource(R.string.select_key),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { applyBinding(Binding.NONE) }) {
                        Text(stringResource(R.string.none), color = Color(0xffff6666))
                    }
                }

                // ---- Вкладки ----
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BindingTab(stringResource(R.string.main_keyboard), active = tab == 0, modifier = Modifier.weight(1f), onClick = { tab = 0 })
                    BindingTab(stringResource(R.string.mouse_buttons), active = tab == 1, modifier = Modifier.weight(1f), onClick = { tab = 1 })
                    BindingTab(stringResource(R.string.gamepad), active = tab == 2, modifier = Modifier.weight(1f), onClick = { tab = 2 })
                }

                Spacer(Modifier.height(8.dp))

                // Клавиатура и мышь — в скролле, геймпад — на весь оставшийся экран
                when (tab) {
                    0 -> Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        KeyboardBindingContent(element, index, ctx, ::applyBinding)
                    }
                    1 -> Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        MouseBindingContent(element, index, ::applyBinding)
                    }
                    2 -> GamepadBindingContent(element, index, ctx, ::applyBinding)
                }
            }
        }
    }
}

@Composable
private fun BindingTab(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0x1600aaff) else Color.Transparent)
            .border(1.dp, if (active) Color(0xffa8c9ff) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun KeyboardBindingContent(
    element: ControlElement,
    index: Int,
    ctx: Context,
    applyBinding: (Binding) -> Unit,
) {
    val current = element.getBindingAt(index)
    val rows = listOf(
        listOf(Binding.KEY_ESC, Binding.KEY_F1, Binding.KEY_F2, Binding.KEY_F3, Binding.KEY_F4, Binding.KEY_F5, Binding.KEY_F6, Binding.KEY_F7, Binding.KEY_F8, Binding.KEY_F9, Binding.KEY_F10, Binding.KEY_F11, Binding.KEY_F12),
        listOf(Binding.KEY_GRAVE, Binding.KEY_1, Binding.KEY_2, Binding.KEY_3, Binding.KEY_4, Binding.KEY_5, Binding.KEY_6, Binding.KEY_7, Binding.KEY_8, Binding.KEY_9, Binding.KEY_0, Binding.KEY_MINUS, Binding.KEY_BKSP),
        listOf(Binding.KEY_TAB, Binding.KEY_Q, Binding.KEY_W, Binding.KEY_E, Binding.KEY_R, Binding.KEY_T, Binding.KEY_Y, Binding.KEY_U, Binding.KEY_I, Binding.KEY_O, Binding.KEY_P, Binding.KEY_BRACKET_LEFT, Binding.KEY_BRACKET_RIGHT, Binding.KEY_BACKSLASH),
        listOf(Binding.KEY_CAPS_LOCK, Binding.KEY_A, Binding.KEY_S, Binding.KEY_D, Binding.KEY_F, Binding.KEY_G, Binding.KEY_H, Binding.KEY_J, Binding.KEY_K, Binding.KEY_L, Binding.KEY_SEMICOLON, Binding.KEY_APOSTROPHE, Binding.KEY_ENTER),
        listOf(Binding.KEY_SHIFT_L, Binding.KEY_Z, Binding.KEY_X, Binding.KEY_C, Binding.KEY_V, Binding.KEY_B, Binding.KEY_N, Binding.KEY_M, Binding.KEY_COMMA, Binding.KEY_PERIOD, Binding.KEY_SLASH, Binding.KEY_SHIFT_R),
        listOf(Binding.KEY_CTRL_L, Binding.KEY_ALT_L, Binding.KEY_SPACE, Binding.KEY_ALT_R, Binding.KEY_CTRL_R, Binding.KEY_INSERT, Binding.KEY_HOME, Binding.KEY_PG_UP, Binding.KEY_DEL, Binding.KEY_END, Binding.KEY_PG_DOWN, Binding.KEY_UP, Binding.KEY_LEFT, Binding.KEY_DOWN, Binding.KEY_RIGHT),
    )
    rows.forEach { rowBindings ->
        BindingKeyRow(bindings = rowBindings, current = current, ctx = ctx, onClick = applyBinding)
    }
}

@Composable
private fun MouseBindingContent(
    element: ControlElement,
    index: Int,
    applyBinding: (Binding) -> Unit,
) {
    val current = element.getBindingAt(index)

    // ---- Ряд 1: кнопки мыши и колесо ----
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MouseImageKey(
            iconRes = R.drawable.mouse_left_click,
            accent = current == Binding.MOUSE_LEFT_BUTTON,
            onClick = { applyBinding(Binding.MOUSE_LEFT_BUTTON) },
            modifier = Modifier.weight(1f),
        )
        MouseImageKey(
            iconRes = R.drawable.mouse_right_click,
            accent = current == Binding.MOUSE_RIGHT_BUTTON,
            onClick = { applyBinding(Binding.MOUSE_RIGHT_BUTTON) },
            modifier = Modifier.weight(1f),
        )
        MouseImageKey(
            iconRes = R.drawable.mouse_wheel_click,
            accent = current == Binding.MOUSE_MIDDLE_BUTTON,
            onClick = { applyBinding(Binding.MOUSE_MIDDLE_BUTTON) },
            modifier = Modifier.weight(1f),
        )
        MouseImageKey(
            iconRes = R.drawable.mouse_wheel_click,
            accent = current == Binding.MOUSE_SCROLL_UP,
            onClick = { applyBinding(Binding.MOUSE_SCROLL_UP) },
            modifier = Modifier.weight(1f),
            arrowIcon = Icons.Filled.KeyboardArrowUp,
        )
        MouseImageKey(
            iconRes = R.drawable.mouse_wheel_click,
            accent = current == Binding.MOUSE_SCROLL_DOWN,
            onClick = { applyBinding(Binding.MOUSE_SCROLL_DOWN) },
            modifier = Modifier.weight(1f),
            arrowIcon = Icons.Filled.KeyboardArrowDown,
        )
    }

    Spacer(Modifier.height(10.dp))

    // ---- Ряд 2: вверх (по центру) ----
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        DirectionKey(
            accent = current == Binding.MOUSE_MOVE_UP,
            icon = Icons.Filled.ArrowUpward,
            onClick = { applyBinding(Binding.MOUSE_MOVE_UP) },
        )
    }

    Spacer(Modifier.height(8.dp))

    // ---- Ряд 3: влево / вниз / вправо ----
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        DirectionKey(
            accent = current == Binding.MOUSE_MOVE_LEFT,
            icon = Icons.Filled.ArrowBack,
            onClick = { applyBinding(Binding.MOUSE_MOVE_LEFT) },
        )
        DirectionKey(
            accent = current == Binding.MOUSE_MOVE_DOWN,
            icon = Icons.Filled.ArrowDownward,
            onClick = { applyBinding(Binding.MOUSE_MOVE_DOWN) },
        )
        DirectionKey(
            accent = current == Binding.MOUSE_MOVE_RIGHT,
            icon = Icons.Filled.ArrowForward,
            onClick = { applyBinding(Binding.MOUSE_MOVE_RIGHT) },
        )
    }
}

// ---- Кнопка-мышь с картинкой ----
@Composable
private fun MouseImageKey(
    iconRes: Int,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrowIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val bodyColor = if (accent) Color(0xff11a9ef) else Color(0xff353535)
    val borderColor = if (accent) Color(0xff5bdcff) else Color(0xff444444)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bodyColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(36.dp),
        )
        if (arrowIcon != null) {
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(16.dp)
                    .background(Color(0x99000000), CircleShape),
            )
        }
    }
}

// ---- Круглая кнопка-стрелка перемещения ----
@Composable
private fun DirectionKey(
    accent: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (accent) Color(0xff11a9ef) else Color(0xff353535))
            .border(1.dp, if (accent) Color(0xff5bdcff) else Color(0xff444444), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun BindingKeyRow(
    bindings: List<Binding>,
    current: Binding,
    ctx: Context,
    onClick: (Binding) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        bindings.forEach { binding ->
            val accent = binding == current
            Box(
                modifier = Modifier
                    .weight(keyWeight(binding))
                    .height(29.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (accent) Color(0xff11a9ef) else Color(0xff353535))
                    .border(1.dp, if (accent) Color(0xff5bdcff) else Color(0xff444444), RoundedCornerShape(5.dp))
                    .clickable { onClick(binding) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bindingLabel(ctx, binding),
                    color = Color.White,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun GamepadBindingContent(
    element: ControlElement,
    index: Int,
    ctx: Context,
    applyBinding: (Binding) -> Unit,
) {
    val current = element.getBindingAt(index)
    data class PadPos(val binding: Binding, val leftPercent: Float, val topDp: Float, val widthPercent: Float, val heightPercent: Float)

    val padButtons = listOf(
        PadPos(Binding.GAMEPAD_BUTTON_L2, 11f, 7f, 7f, 4f),
        PadPos(Binding.GAMEPAD_BUTTON_L1, 20f, 7f, 7f, 4f),
        PadPos(Binding.GAMEPAD_BUTTON_R1, 73f, 7f, 7f, 4f),
        PadPos(Binding.GAMEPAD_BUTTON_R2, 82f, 7f, 7f, 4f),
        PadPos(Binding.GAMEPAD_DPAD_UP, 19f, 72f, 7f, 7f),
        PadPos(Binding.GAMEPAD_DPAD_LEFT, 15f, 116f, 7f, 7f),
        PadPos(Binding.GAMEPAD_DPAD_RIGHT, 23f, 116f, 7f, 7f),
        PadPos(Binding.GAMEPAD_DPAD_DOWN, 19f, 160f, 7f, 7f),
        PadPos(Binding.GAMEPAD_BUTTON_SELECT, 42f, 94f, 8f, 4f),
        PadPos(Binding.GAMEPAD_BUTTON_START, 51f, 94f, 8f, 4f),
        PadPos(Binding.GAMEPAD_BUTTON_L3, 43f, 150f, 6f, 6f),
        PadPos(Binding.GAMEPAD_BUTTON_R3, 53f, 150f, 6f, 6f),
        PadPos(Binding.GAMEPAD_BUTTON_Y, 78f, 62f, 7f, 7f),
        PadPos(Binding.GAMEPAD_BUTTON_X, 72f, 116f, 7f, 7f),
        PadPos(Binding.GAMEPAD_BUTTON_B, 84f, 116f, 7f, 7f),
        PadPos(Binding.GAMEPAD_BUTTON_A, 78f, 170f, 7f, 7f),
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(top = 4.dp),
    ) {
        val w = maxWidth
        val h = maxHeight
        padButtons.forEach { pos ->
            val binding = pos.binding
            val accent = binding == current
            val radius = gamepadCornerRadius(binding)
            Box(
                modifier = Modifier
                    .offset(x = w * pos.leftPercent / 100, y = h * pos.topDp / 250f)
                    .size(
                        width = maxOf(52.dp, w * pos.widthPercent / 100),
                        height = maxOf(34.dp, h * pos.heightPercent / 100),
                    )
                    .clip(RoundedCornerShape(radius))
                    .background(gamepadColor(binding))
                    .border(1.dp, if (accent) Color(0xff5bdcff) else Color(0xff444444), RoundedCornerShape(radius))
                    .clickable { applyBinding(binding) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bindingLabel(ctx, binding),
                    color = if (isAbxy(binding)) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (isAbxy(binding)) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun isAbxy(binding: Binding): Boolean =
    binding == Binding.GAMEPAD_BUTTON_A || binding == Binding.GAMEPAD_BUTTON_B ||
        binding == Binding.GAMEPAD_BUTTON_X || binding == Binding.GAMEPAD_BUTTON_Y

private fun gamepadColor(binding: Binding): Color = when (binding) {
    Binding.GAMEPAD_BUTTON_A, Binding.GAMEPAD_BUTTON_Y -> Color(0xffffe000)
    Binding.GAMEPAD_BUTTON_B -> Color(0xffff2b2b)
    Binding.GAMEPAD_BUTTON_X -> Color(0xff03a9f4)
    Binding.GAMEPAD_BUTTON_SELECT, Binding.GAMEPAD_BUTTON_START -> Color(0xff4a4a4a)
    else -> Color(0xff353535)
}

private fun gamepadCornerRadius(binding: Binding): Int =
    if (isAbxy(binding)) 18 else 6

private fun keyWeight(binding: Binding): Float = when (binding) {
    Binding.KEY_SPACE -> 4.0f
    Binding.KEY_SHIFT_L, Binding.KEY_SHIFT_R -> 1.8f
    Binding.KEY_ENTER, Binding.KEY_BKSP, Binding.KEY_CAPS_LOCK, Binding.KEY_TAB -> 1.55f
    else -> if (binding.isMouse()) 2.3f else if (binding.isGamepad()) 1.2f else 1.0f
}

private fun bindingLabel(ctx: Context, binding: Binding): String = when (binding) {
    Binding.MOUSE_LEFT_BUTTON -> "(L)\n" + ctx.getString(R.string.mouse_left_button_short)
    Binding.MOUSE_RIGHT_BUTTON -> "(R)\n" + ctx.getString(R.string.mouse_right_button_short)
    Binding.MOUSE_MIDDLE_BUTTON -> "(M)\n" + ctx.getString(R.string.mouse_middle_button_short)
    Binding.MOUSE_SCROLL_UP -> "^\n" + ctx.getString(R.string.mouse_scroll_up_short)
    Binding.MOUSE_SCROLL_DOWN -> "v\n" + ctx.getString(R.string.mouse_scroll_down_short)
    Binding.MOUSE_MOVE_UP -> "^\n" + ctx.getString(R.string.move_up_short)
    Binding.MOUSE_MOVE_DOWN -> "v\n" + ctx.getString(R.string.move_down_short)
    Binding.MOUSE_MOVE_LEFT -> "<\n" + ctx.getString(R.string.move_left_short)
    Binding.MOUSE_MOVE_RIGHT -> ">\n" + ctx.getString(R.string.move_right_short)
    Binding.KEY_BKSP -> "Bksp"
    Binding.KEY_TAB -> "Tab"
    Binding.KEY_CAPS_LOCK -> "Caps"
    Binding.KEY_ENTER -> "Enter"
    Binding.KEY_SHIFT_L -> "L Shift"
    Binding.KEY_SHIFT_R -> "R Shift"
    Binding.KEY_CTRL_L -> "L Ctrl"
    Binding.KEY_CTRL_R -> "R Ctrl"
    Binding.KEY_ALT_L -> "L Alt"
    Binding.KEY_ALT_R -> "R Alt"
    Binding.KEY_SPACE -> "Space"
    Binding.KEY_INSERT -> "Ins"
    Binding.KEY_PG_UP -> "PgUp"
    Binding.KEY_PG_DOWN -> "PgDn"
    Binding.KEY_DEL -> "Del"
    Binding.KEY_UP -> "^"
    Binding.KEY_LEFT -> "<"
    Binding.KEY_DOWN -> "v"
    Binding.KEY_RIGHT -> ">"
    Binding.GAMEPAD_BUTTON_L2 -> "LT"
    Binding.GAMEPAD_BUTTON_L1 -> "LB"
    Binding.GAMEPAD_BUTTON_R1 -> "RB"
    Binding.GAMEPAD_BUTTON_R2 -> "RT"
    Binding.GAMEPAD_BUTTON_SELECT -> "SELECT"
    Binding.GAMEPAD_BUTTON_START -> "START"
    Binding.GAMEPAD_BUTTON_A -> "A"
    Binding.GAMEPAD_BUTTON_B -> "B"
    Binding.GAMEPAD_BUTTON_X -> "X"
    Binding.GAMEPAD_BUTTON_Y -> "Y"
    Binding.GAMEPAD_BUTTON_L3 -> "L3"
    Binding.GAMEPAD_BUTTON_R3 -> "R3"
    Binding.GAMEPAD_DPAD_UP -> "D^"
    Binding.GAMEPAD_DPAD_LEFT -> "D<"
    Binding.GAMEPAD_DPAD_RIGHT -> "D>"
    Binding.GAMEPAD_DPAD_DOWN -> "Dv"
    Binding.GAMEPAD_LEFT_THUMB_UP -> "L^"
    Binding.GAMEPAD_LEFT_THUMB_RIGHT -> "L>"
    Binding.GAMEPAD_LEFT_THUMB_DOWN -> "Lv"
    Binding.GAMEPAD_LEFT_THUMB_LEFT -> "L<"
    Binding.GAMEPAD_RIGHT_THUMB_UP -> "R^"
    Binding.GAMEPAD_RIGHT_THUMB_RIGHT -> "R>"
    Binding.GAMEPAD_RIGHT_THUMB_DOWN -> "Rv"
    Binding.GAMEPAD_RIGHT_THUMB_LEFT -> "R<"
    else -> binding.toString()
}

