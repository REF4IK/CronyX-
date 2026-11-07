package com.winlator.cmod.ui

import android.view.View
import androidx.compose.animation.*
import androidx.compose.foundation.*
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassBorder
import com.winlator.cmod.ui.components.glassFillBrush
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.ui.screens.GameOverlayColors
import com.winlator.cmod.ui.screens.FpsBadgeIcon
import com.winlator.cmod.ui.screens.GameRailItem
import com.winlator.cmod.ui.screens.XPanelActiveWindows
import com.winlator.cmod.ui.screens.XPanelEffects
import com.winlator.cmod.ui.screens.XPanelFps
import com.winlator.cmod.ui.screens.XPanelFrameGen
import com.winlator.cmod.ui.screens.XPanelInput
import com.winlator.cmod.ui.screens.XPanelLogs
import com.winlator.cmod.ui.screens.XPanelTaskManager
import com.winlator.cmod.ui.screens.XPanelWinetricks

class XServerMenuController(private val activity: XServerDisplayActivity) {
    private val composeOverlay: ComposeView = activity.findViewById(R.id.ComposeOverlay)
    var isMenuVisible by mutableStateOf(false)
        private set
    var isControlsEditorVisible by mutableStateOf(false)
        private set

    init {
        composeOverlay.setContent {
            // Оверлей всегда тёмный: поверх игрового кадра светлое стекло нечитаемо,
            // а glass-токены выбирают ветку по яркости фона темы, поэтому внутри
            // WinlatorTheme(darkTheme = true) они сами становятся тёмными.
            com.winlator.cmod.ui.theme.WinlatorTheme(forceDark = true) {
                if (isMenuVisible) {
                    XServerMenuOverlay(
                        activity = activity,
                        onDismiss = { hideMenu() }
                    )
                } else if (isControlsEditorVisible) {
                    com.winlator.cmod.ui.screens.InGameControlsEditorOverlay(
                        activity = activity,
                        onClose = {
                            hideControlsEditor()
                            activity.exitControlsEditMode()
                        }
                    )
                }
            }
        }

        try {
            val viewClass = Class.forName("android.view.View")
            val viewTreeLifecycleOwnerClass = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            val setLifecycleMethod = viewTreeLifecycleOwnerClass.getMethod("set", viewClass, Class.forName("androidx.lifecycle.LifecycleOwner"))
            val viewTreeViewModelStoreOwnerClass = Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner")
            val setViewModelStoreMethod = viewTreeViewModelStoreOwnerClass.getMethod("set", viewClass, Class.forName("androidx.lifecycle.ViewModelStoreOwner"))
            val viewTreeSavedStateRegistryOwnerClass = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            val setSavedStateMethod = viewTreeSavedStateRegistryOwnerClass.getMethod("set", viewClass, Class.forName("androidx.savedstate.SavedStateRegistryOwner"))

            val setOwners = { view: View ->
                setLifecycleMethod.invoke(null, view, activity)
                setViewModelStoreMethod.invoke(null, view, activity)
                setSavedStateMethod.invoke(null, view, activity)
            }

            setOwners(composeOverlay)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showMenu() {
        isControlsEditorVisible = false
        isMenuVisible = true
        composeOverlay.visibility = View.VISIBLE
    }

    fun hideMenu() {
        isMenuVisible = false
        if (!isControlsEditorVisible) composeOverlay.visibility = View.GONE
    }

    /** Редактор кнопок поверх игры: тулбар, ввод не перехватывается. */
    fun showControlsEditor() {
        isMenuVisible = false
        isControlsEditorVisible = true
        composeOverlay.visibility = View.VISIBLE
    }

    fun hideControlsEditor() {
        isControlsEditorVisible = false
        if (!isMenuVisible) composeOverlay.visibility = View.GONE
    }

    fun toggleMenu() {
        if (isMenuVisible) hideMenu() else showMenu()
    }
}

private data class XMenuItem(
    val id: Int,
    val titleRes: Int?,
    val titleString: String?,
    val iconRes: Int,
    val hasPanel: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun XServerMenuOverlay(
    activity: XServerDisplayActivity,
    onDismiss: () -> Unit
) {
    val preferences = remember { MmkvPreferences() }
    val enableLogs = remember {
        preferences.getBoolean("enable_wine_debug", false) || preferences.getBoolean("enable_box86_64_logs", false)
    }

    var isPaused by remember { mutableStateOf(activity.isPaused) }
    var selectedId by remember { mutableStateOf<Int?>(null) }

    val container = remember { activity.getContainer() }
    val lsfgActive = container != null && container.isLsfgNative() && activity.getLastFgMult() >= 2

    val menuItems = remember(isPaused, enableLogs) {
        val hidden = com.winlator.cmod.core.XServerMenuSettings.hiddenIds()
        fun visible(id: String) = id !in hidden
        val list = mutableListOf<XMenuItem>()
        if (visible("keyboard")) list.add(XMenuItem(R.id.main_menu_keyboard, R.string.keyboard, null, R.drawable.icon_keyboard, false))
        if (visible("input_controls")) list.add(XMenuItem(R.id.main_menu_input_controls, R.string.input_controls, null, R.drawable.icon_input_controls, true))
        if (visible("task_manager")) list.add(XMenuItem(R.id.main_menu_task_manager, R.string.task_manager, null, R.drawable.icon_task_manager, true))
        if (visible("fps_counter")) list.add(XMenuItem(R.id.main_menu_fps_counter, R.string.fps_counter, null, R.drawable.icon_debug, true))
        if (visible("frame_generation")) list.add(XMenuItem(R.id.main_menu_frame_generation, R.string.lsfg_title, null, R.drawable.icon_screen_effect, true))
        if (visible("screen_effects")) list.add(XMenuItem(R.id.main_menu_screen_effects, R.string.screen_effect, null, R.drawable.icon_screen_effect, true))
        if (visible("active_windows")) list.add(XMenuItem(R.id.main_menu_active_windows, R.string.active_windows, null, R.drawable.icon_window_list, true))
        if (visible("toggle_fullscreen")) list.add(XMenuItem(R.id.main_menu_toggle_fullscreen, R.string.toggle_fullscreen, null, R.drawable.icon_fullscreen, false))
        if (visible("pip_mode")) list.add(XMenuItem(R.id.main_menu_pip_mode, R.string.pip_mode, null, R.drawable.ic_picture_in_picture_alt, false))
        if (visible("pause")) list.add(
            XMenuItem(
                R.id.main_menu_pause, null,
                if (isPaused) "Resume" else "Pause",
                if (isPaused) R.drawable.icon_play else R.drawable.icon_pause, false
            )
        )
        if (visible("winetricks")) list.add(XMenuItem(R.id.main_menu_winetricks, null, "Winetricks", R.drawable.icon_wine, true))
        if (visible("terminal")) list.add(XMenuItem(R.id.main_menu_terminal, null, "Debug Terminal", R.drawable.icon_env_var, false))
        if (enableLogs && visible("logs")) {
            list.add(XMenuItem(R.id.main_menu_logs, R.string.logs, null, R.drawable.icon_debug, true))
        }
        list.add(XMenuItem(R.id.main_menu_exit, R.string.exit, null, R.drawable.icon_exit, false))
        list
    }

    fun onItemClick(item: XMenuItem) {
        if (item.hasPanel) {
            selectedId = if (selectedId == item.id) null else item.id
        } else {
            activity.handleXServerMenuAction(item.id)
            if (item.id == R.id.main_menu_pause) isPaused = !isPaused
            onDismiss()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val panelWidth = (maxWidth - 140.dp).coerceIn(300.dp, 410.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Rail: иконка + подпись под ней. Прозрачное стекло: тёмная база держит
            // читаемость поверх кадра, градиент и бордер дают сам эффект стекла.
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight(0.94f)
                    .clip(RoundedCornerShape(GlassTokens.CardRadius))
                    .background(Color.Black.copy(alpha = 0.80f))
                    .background(glassFillBrush())
                    .border(glassBorder(), RoundedCornerShape(GlassTokens.CardRadius))
                    .clickable(enabled = false) {}
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    items(menuItems, key = { it.id }) { item ->
                        val title = item.titleRes?.let { stringResource(it) } ?: item.titleString ?: ""
                        GameRailItem(
                            iconRes = item.iconRes,
                            label = title,
                            contentDesc = title,
                            selected = selectedId == item.id,
                            dot = item.id == R.id.main_menu_frame_generation && lsfgActive,
                            onClick = { onItemClick(item) },
                            customIcon = if (item.id == R.id.main_menu_fps_counter) ({ FpsBadgeIcon(it) }) else null
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Panel
            AnimatedVisibility(
                visible = selectedId != null,
                enter = slideInHorizontally(initialOffsetX = { it / 3 }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it / 3 }) + fadeOut()
            ) {
                val sid = selectedId
                Box(
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight(0.94f)
                        .clip(RoundedCornerShape(GlassTokens.CardRadius))
                        .background(Color.Black.copy(alpha = 0.74f))
                        .background(glassFillBrush())
                        .border(glassBorder(), RoundedCornerShape(GlassTokens.CardRadius))
                        .clickable(enabled = false) {}
                ) {
                    val closePanel = { selectedId = null }
                    when (sid) {
                        R.id.main_menu_input_controls -> XPanelInput(activity, onDismiss = closePanel)
                        R.id.main_menu_frame_generation -> XPanelFrameGen(activity, onDismiss = closePanel)
                        R.id.main_menu_screen_effects -> XPanelEffects(activity, onDismiss = closePanel)
                        R.id.main_menu_task_manager -> XPanelTaskManager(activity, onDismiss = closePanel)
                        R.id.main_menu_fps_counter -> XPanelFps(
                            onDismiss = closePanel,
                            onConfigChanged = { activity.onFpsCounterConfigChangedFromCompose() }
                        )
                        R.id.main_menu_active_windows -> XPanelActiveWindows(activity, onDismiss = closePanel)
                        R.id.main_menu_winetricks -> XPanelWinetricks(activity, onDismiss = closePanel)
                        R.id.main_menu_logs -> XPanelLogs(activity, onDismiss = closePanel)
                    }
                }
            }
        }
    }
}
