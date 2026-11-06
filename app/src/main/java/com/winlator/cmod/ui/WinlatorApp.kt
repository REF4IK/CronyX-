package com.winlator.cmod.ui

import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.ui.navigation.Screen
import com.winlator.cmod.ui.components.GlassSheetContent
import com.winlator.cmod.ui.components.UpdateCheckDialog
import com.winlator.cmod.ui.components.glassIsDark
import com.winlator.cmod.ui.components.glassFillBrush
import com.winlator.cmod.ui.components.glassSheetFill
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.glassValueColor
import com.winlator.cmod.ui.components.glassAccent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.glassBorder
import com.winlator.cmod.ui.screens.*
import com.winlator.cmod.ui.theme.observeFloat
import com.winlator.cmod.ui.theme.observeInt
import com.winlator.cmod.ui.theme.observeString

/**
 * Navigation drawer items corresponding to the main menu.
 */
private val drawerItems = listOf(
    DrawerItem(R.string.my_profile, Icons.Filled.Person, Screen.MyProfile),
    DrawerItem(R.string.shortcuts, Icons.Filled.VideogameAsset, Screen.Shortcuts),
    DrawerItem(R.string.containers, Icons.Filled.Storage, Screen.Containers),
    DrawerItem(R.string.input_controls, Icons.Filled.Gamepad, Screen.InputControls),
    DrawerItem(R.string.saves, Icons.Filled.Save, Screen.Saves),
    DrawerItem(R.string.box64_rc_file, Icons.Filled.Code, Screen.Box86_64RC),
    DrawerItem(R.string.contents, Icons.Filled.Extension, Screen.Contents),
    DrawerItem(R.string.steam, Icons.Filled.Folder, Screen.Steam),
    DrawerItem(R.string.adrenotools_gpu_drivers, Icons.Filled.Adb, Screen.Adrenotools),
    DrawerItem(R.string.settings, Icons.Filled.Settings, Screen.Settings),
    DrawerItem(R.string.about, Icons.Filled.Info, Screen.About),
    DrawerItem(R.string.file_manager, Icons.Filled.Description, Screen.FileManager),
)

private data class DrawerItem(
    val labelResId: Int,
    val icon: ImageVector,
    val screen: Screen,
)

@Composable
private fun getScreenTitle(screen: Screen): String {
    return when (screen) {
        Screen.Shortcuts -> stringResource(R.string.shortcuts)
        Screen.MyProfile -> stringResource(R.string.my_profile)
        Screen.Containers -> stringResource(R.string.containers)
        Screen.InputControls -> stringResource(R.string.input_controls)
        Screen.Saves -> stringResource(R.string.saves)
        Screen.Box86_64RC -> stringResource(R.string.box64_rc_file)
        Screen.Contents -> stringResource(R.string.contents)
        Screen.Adrenotools -> stringResource(R.string.adrenotools_gpu_drivers)
        Screen.Settings -> stringResource(R.string.settings)
        Screen.Steam -> stringResource(R.string.steam)
        Screen.About -> stringResource(R.string.about)
        Screen.FileManager -> stringResource(R.string.file_manager)
        Screen.GamepadTest -> stringResource(R.string.gamepad_test)
        Screen.IconManager -> stringResource(R.string.icon_manager)
        Screen.Terminal -> stringResource(R.string.terminal)
        Screen.BigPicture -> stringResource(R.string.big_picture)
        else -> "Winlator CMOD"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinlatorApp(
    isDarkMode: Boolean,
    preferences: SharedPreferences,
    initialScreen: Screen = Screen.Containers,
    selectedProfileId: Int = 0,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onOpenSteam: () -> Unit,
    onOpenFileManager: () -> Unit,
    onImageFsReady: () -> Unit = {},
) {
    var currentScreen by remember { mutableStateOf<Screen>(initialScreen) }
    var showGPUPerformance by remember { mutableStateOf(false) }
    var showAdrenotools by remember { mutableStateOf(false) }
    var showDriverStore by remember { mutableStateOf(false) }
    var showInstalledComponents by remember { mutableStateOf(false) }
    var showContainerEdit by remember { mutableStateOf(false) }
    var editContainerId by remember { mutableStateOf(0) }
    var isContainerEditMode by remember { mutableStateOf(false) }
    var containersRefreshKey by remember { mutableStateOf(0) }
    var adrenotoolsRefreshKey by remember { mutableStateOf(0) }

    // При возврате в приложение (выход из контейнера/игры) — обновляем список
    // контейнеров и ярлыков. Раньше это делал полный перезапуск процесса,
    // теперь MainActivity просто выходит на передний план (без чёрного экрана).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                containersRefreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showContainerImportInfo by remember { mutableStateOf(false) }
    var showImportGame by remember { mutableStateOf(false) }
    var isShortcutsGridView by remember { mutableStateOf(true) }
    var showShortcutSettings by remember { mutableStateOf(false) }
    var shortcutSettingsShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var fileManagerContainerId by remember { mutableStateOf(-1) }
    var hideTopBarBySteamInfo by remember { mutableStateOf(false) }
    var showContentsSourceDialog by remember { mutableStateOf(false) }
    var showContentsInstallConfirm by remember { mutableStateOf(false) }

    // First launch dialog state
    var showFirstLaunchDialog by remember { mutableStateOf(false) }
    var firstLaunchDarkMode by remember { mutableStateOf(false) }

    // ── Авто-проверка обновлений при запуске ──
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.winlator.cmod.core.UpdateManager.UpdateInfo?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableIntStateOf(0) }
    var updateFile by remember { mutableStateOf<java.io.File?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val imageFs = remember { com.winlator.cmod.xenvironment.ImageFs.find(context) }
    var isInstalling by remember {
        mutableStateOf(!imageFs.isValid() || imageFs.version < com.winlator.cmod.xenvironment.ImageFsInstaller.LATEST_VERSION)
    }
    var installProgress by remember { mutableIntStateOf(0) }

    // ── Анимации переходов: стиль и длительность из настроек (реактивно) ──
    var transitionAnim by remember { mutableStateOf(preferences.getString("transition_animation", "none") ?: "none") }
    var animDurationMs by remember { mutableIntStateOf(preferences.getInt("animation_duration_ms", 350)) }
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "transition_animation" -> transitionAnim = prefs.getString(key, "none") ?: "none"
                "animation_duration_ms" -> animDurationMs = prefs.getInt(key, 350)
            }
        }
        com.winlator.cmod.core.MmkvPreferences.registerGlobalListener(listener)
        onDispose { com.winlator.cmod.core.MmkvPreferences.unregisterGlobalListener(listener) }
    }

    LaunchedEffect(isInstalling) {
        if (isInstalling) {
            com.winlator.cmod.xenvironment.ImageFsInstaller.installFromAssets(
                context,
                object : com.winlator.cmod.xenvironment.ImageFsInstaller.OnProgressListener {
                    override fun onProgress(progress: Int) {
                        installProgress = progress
                    }
                    override fun onFinished(success: Boolean) {
                        isInstalling = false
                        containersRefreshKey++
                    }
                }
            )
        }
    }

    // Уведомляем хост о готовности ImageFs (один раз на сессию), чтобы запросить
    // разрешения уже после распаковки и после закрытия диалога первого запуска.
    // rememberSaveable — защита от повторного вызова при recreate() Activity.
    var permissionsRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isInstalling, showFirstLaunchDialog) {
        if (!isInstalling && !showFirstLaunchDialog && !permissionsRequested) {
            permissionsRequested = true
            onImageFsReady()
        }
    }

    // После завершения установки ImageFs проверяем, нужно ли показать диалог первого запуска
    val isFirstLaunch = remember {
        !preferences.getBoolean("first_launch_completed", false)
    }

    LaunchedEffect(isInstalling, isFirstLaunch) {
        if (!isInstalling && isFirstLaunch) {
            firstLaunchDarkMode = false
            showFirstLaunchDialog = true
        }
    }

    // Авто-проверка обновлений: после установки ImageFs и закрытия диалога первого запуска,
    // если в настройках включено уведомление. Колбэк приходит с фонового потока — свитчим на UI.
    LaunchedEffect(isInstalling, showFirstLaunchDialog) {
        if (isInstalling || showFirstLaunchDialog) return@LaunchedEffect
        if (!com.winlator.cmod.core.UpdateManager.isNotifyEnabled(context)) return@LaunchedEffect
        updateChecking = true
        com.winlator.cmod.core.UpdateManager.check(context) { info ->
            (context as? android.app.Activity)?.runOnUiThread {
                updateChecking = false
                if (info != null && info.error == com.winlator.cmod.core.UpdateManager.ManifestError.NONE && info.isNewer &&
                    info.apkUrl != null &&
                    com.winlator.cmod.core.UpdateManager.skippedVersion(context) != info.tagName
                ) {
                    updateInfo = info
                    showUpdateDialog = true
                }
            }
        }
    }

    // Окно (edge-to-edge, скрытый статус-бар) настраивается в MainActivity ДО создания
    // первого кадра — здесь только реактивные цвета навигационной панели при смене темы.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    LaunchedEffect(isDarkMode) {
        if (activity != null) {
            val window = activity.window
            window.navigationBarColor = if (isDarkMode) {
                android.graphics.Color.parseColor("#121212")
            } else {
                android.graphics.Color.parseColor("#FFFFFF")
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val controller = window.decorView.windowInsetsController
                controller?.setSystemBarsAppearance(
                    if (isDarkMode) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val decorView = window.decorView
                var flags = decorView.systemUiVisibility
                flags = if (isDarkMode) {
                    flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
                decorView.systemUiVisibility = flags
            }
        }
    }
    var showMoreSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val appContainerManager = remember(context) { ContainerManager(context) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val importDir = FileUtils.getFileFromUri(context, uri)
            if (importDir == null || !importDir.isDirectory) {
                AppUtils.showToast(context, "Invalid container directory.")
                return@rememberLauncherForActivityResult
            }
            Thread {
                try {
                    appContainerManager.importContainer(importDir) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            containersRefreshKey++
                            AppUtils.showToast(context, "Container imported successfully.")
                        }
                    }
                } catch (e: Exception) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        AppUtils.showToast(context, "Error importing container: ${e.message}")
                    }
                }
            }.start()
        }
    }

    // Обработка системной кнопки "Назад":
    // 1) открыт Driver Store → закрыть его
    // 2) открыт Adrenotools → закрыть его
    // 3) открыт GPU-экран → закрыть его
    // 4) открыт лист «Ещё» → закрыть его
    // 5) не на главном экране → вернуться на Containers (главный)
    // 6) иначе → стандартное поведение (выход/сворачивание приложения)
    BackHandler(enabled = showContainerEdit || showInstalledComponents || showDriverStore || showAdrenotools || showGPUPerformance || showImportGame || showShortcutSettings || currentScreen != Screen.Containers) {
        when {
            showContainerEdit -> { showContainerEdit = false; containersRefreshKey++ }
            showInstalledComponents -> showInstalledComponents = false
            showDriverStore -> showDriverStore = false
            showAdrenotools -> showAdrenotools = false
            showGPUPerformance -> showGPUPerformance = false
            showImportGame -> { showImportGame = false; containersRefreshKey++ }
            showShortcutSettings -> { showShortcutSettings = false }
            currentScreen == Screen.IconManager -> currentScreen = Screen.InputControls
            currentScreen == Screen.GamepadTest -> currentScreen = Screen.InputControls
            currentScreen == Screen.BigPicture -> currentScreen = Screen.Shortcuts
            currentScreen != Screen.Containers -> currentScreen = Screen.Containers
        }
    }

    if (isInstalling) {
        InstallerScreen(progress = installProgress, isFinished = installProgress >= 100)
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (!hideTopBarBySteamInfo && currentScreen != Screen.BigPicture) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = getScreenTitle(currentScreen)
                                )
                            },
                            // Статус-бар скрыт в MainActivity — не добавляем под него отступ,
                            // иначе меню «прыгает» вниз при возврате из контейнера
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            actions = {
                                if (currentScreen == Screen.Shortcuts) {
                                    IconButton(onClick = { currentScreen = Screen.BigPicture }) {
                                        Icon(Icons.Filled.Tv, contentDescription = stringResource(R.string.big_picture))
                                    }
                                    IconButton(onClick = { isShortcutsGridView = !isShortcutsGridView }) {
                                        Icon(
                                            if (isShortcutsGridView) Icons.Filled.GridOn else Icons.Filled.List,
                                            contentDescription = "Toggle view",
                                        )
                                    }
                                }
                                if (currentScreen == Screen.Containers) {
                                    IconButton(onClick = {
                                        currentScreen = Screen.Terminal
                                    }) { Icon(Icons.Filled.Terminal, contentDescription = "Open Terminal") }
                                    IconButton(onClick = { showContainerImportInfo = true }) {
                                        Icon(Icons.Filled.Download, contentDescription = "Import Container")
                                    }
                                }
                                if (currentScreen == Screen.Contents) {
                                    IconButton(onClick = { showInstalledComponents = true }) {
                                        Icon(Icons.Filled.Inventory2, contentDescription = stringResource(R.string.installed_components))
                                    }
                                    IconButton(onClick = { showContentsSourceDialog = true }) {
                                        Icon(Icons.Filled.Source, contentDescription = stringResource(R.string.contents_source))
                                    }
                                    IconButton(onClick = { showContentsInstallConfirm = true }) {
                                        Icon(Icons.Filled.FileDownload, contentDescription = stringResource(R.string.install))
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                },
                bottomBar = {
                    // Big Picture — фулскрин со своим фокус-деревом; панель ломает его.
                    // При скрытом topBar (Steam info) она тоже не нужна.
                    if (currentScreen != Screen.BigPicture && !hideTopBarBySteamInfo) {
                    GlassBottomNavBar(
                        currentScreen = currentScreen,
                        onSelect = { screen ->
                            currentScreen = screen
                            when (screen) {
                                Screen.Steam -> onOpenSteam()
                                Screen.FileManager -> fileManagerContainerId = -1
                                else -> { }
                            }
                        },
                        onMore = { showMoreSheet = true },
                    )
                    }
                },
            ) { padding ->
                val layoutDirection = LocalLayoutDirection.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Плавающая панель не резервирует полосу под собой:
                        // контент уходит под неё, как на референсе. Отступ
                        // остаётся только от системной навигации.
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(
                            start = padding.calculateStartPadding(layoutDirection),
                            top = padding.calculateTopPadding(),
                            end = padding.calculateEndPadding(layoutDirection),
                        ),
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { screenTransition(transitionAnim, animDurationMs) },
                        label = "screen-transition",
                    ) { screen ->
                        when (screen) {
                    Screen.MyProfile -> MyProfileScreen()
                    Screen.Shortcuts -> ShortcutsScreen(
                        refreshKey = containersRefreshKey,
                        isGridView = isShortcutsGridView,
                        onToggleView = { isShortcutsGridView = !isShortcutsGridView },
                        onImportGame = {
                            showImportGame = true
                        },
                        onOpenShortcutSettings = { shortcut ->
                            shortcutSettingsShortcut = shortcut
                            showShortcutSettings = true
                        },
                        onShowSteamInfo = { show -> hideTopBarBySteamInfo = show }
                    )
                    Screen.Containers -> ContainersScreen(
                        containerManager = appContainerManager,
                        refreshKey = containersRefreshKey,
                        onCreateContainer = {
                            editContainerId = 0
                            isContainerEditMode = false
                            showContainerEdit = true
                        },
                        onEditContainer = { id ->
                            editContainerId = id
                            isContainerEditMode = true
                            showContainerEdit = true
                        },
                        onOpenFileBrowser = { id ->
                            fileManagerContainerId = id
                            currentScreen = Screen.FileManager
                        }
                    )
                    Screen.InputControls -> InputControlsScreen(
                        selectedProfileId = selectedProfileId,
                        onOpenGamepadTest = { currentScreen = Screen.GamepadTest },
                        onOpenIconManager = { currentScreen = Screen.IconManager }
                    )
                    Screen.GamepadTest -> GamepadTestScreen(
                        onBack = { currentScreen = Screen.InputControls }
                    )
                    Screen.IconManager -> IconManagerScreen(
                        onBack = { currentScreen = Screen.InputControls }
                    )
                    Screen.Terminal -> TerminalScreen(
                        onBack = { currentScreen = Screen.Containers }
                    )
                    Screen.Saves -> FragmentHostScreen(
                        fragmentClass = com.winlator.cmod.SavesFragment::class.java,
                    )
                    Screen.Box86_64RC -> Box86_64RCScreen(
                        onBack = { currentScreen = Screen.Containers }
                    )
                    Screen.Contents -> ContentsScreen(
                        onBack = { currentScreen = Screen.Containers },
                        onOpenInstalledComponents = { showInstalledComponents = true },
                        showSourceDialog = showContentsSourceDialog,
                        onShowSourceDialogChange = { showContentsSourceDialog = it },
                        showInstallConfirm = showContentsInstallConfirm,
                        onShowInstallConfirmChange = { showContentsInstallConfirm = it }
                    )
                    Screen.Adrenotools -> AdrenotoolsScreen(
                        refreshKey = adrenotoolsRefreshKey,
                        onBack = { currentScreen = Screen.Containers },
                        onOpenDriverStore = { showDriverStore = true },
                    )
                    Screen.Settings -> SettingsScreen(
                        preferences = preferences,
                        appContext = context,
                        onDarkModeChange = onDarkModeChange,
                        onLanguageChange = onLanguageChange,
                        onTransitionAnimationChange = { anim ->
                            preferences?.edit()?.putString("transition_animation", anim)?.apply()
                        },
                        onConfirmSave = {
                            // Как в оригинале: BTConfirm сохраняет настройки и переходит на Containers (главный экран)
                            currentScreen = Screen.Containers
                            com.winlator.cmod.core.AppUtils.showToast(context, "Настройки сохранены")
                        },
                        onOpenGPUPerformance = { showGPUPerformance = true },
                        onReinstallImageFs = { isInstalling = true }
                    )
                    Screen.About -> AboutScreen()
                    Screen.FileManager -> FileManagerScreen(
                        containerId = fileManagerContainerId,
                        onBack = { currentScreen = Screen.Containers }
                    )
                    Screen.BigPicture -> {
                        val shortcuts = remember(containersRefreshKey) { appContainerManager.loadShortcuts() }
                        BigPictureScreen(
                            shortcuts = shortcuts,
                            onRun = { shortcut ->
                                try {
                                    val intent = android.content.Intent(context, XServerDisplayActivity::class.java)
                                    intent.putExtra("container_id", shortcut.container.id)
                                    intent.putExtra("shortcut_path", shortcut.file?.absolutePath ?: "")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    AppUtils.showToast(context, "Cannot start: ${e.message}")
                                }
                            },
                            onOpenShortcutSettings = { shortcut ->
                                shortcutSettingsShortcut = shortcut
                                showShortcutSettings = true
                            },
                            onOpenContainerSettings = { shortcut ->
                                editContainerId = shortcut.container.id
                                isContainerEditMode = true
                                showContainerEdit = true
                            },
                            onOpenFileManager = {
                                fileManagerContainerId = -1
                                currentScreen = Screen.FileManager
                            },
                            onBack = { currentScreen = Screen.Shortcuts }
                        )
                    }
                    else -> PlaceholderScreen(
                        title = "Winlator CMOD",
                        subtitle = "Select a section from the menu",
                    )
                }
                    }
                }
            }

            // Overlay-экраны поверх всего (со своим TopAppBar, без дублирования).
            // Подложка из обоев: предыдущее меню скрыто, фон — наши обои
            if (showGPUPerformance) {
                OverlayBackdrop(preferences) {
                    GPUPerformanceScreen(onBack = { showGPUPerformance = false })
                }
            }
            if (showDriverStore) {
                OverlayBackdrop(preferences) {
                    DriverStoreScreen(
                        onBack = { showDriverStore = false },
                        onDriverInstalled = { adrenotoolsRefreshKey++ },
                    )
                }
            }
            if (showInstalledComponents) {
                OverlayBackdrop(preferences) {
                    InstalledComponentsScreen(onBack = { showInstalledComponents = false })
                }
            }
            if (showContainerEdit) {
                OverlayBackdrop(preferences) {
                    ContainerEditScreen(
                        containerManager = appContainerManager,
                        containerId = editContainerId,
                        isEditMode = isContainerEditMode,
                        onBack = { showContainerEdit = false; containersRefreshKey++ },
                    )
                }
            }

            if (showContainerImportInfo) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showContainerImportInfo = false },
                    title = { androidx.compose.material3.Text("Import Container") },
                    text = { androidx.compose.material3.Text("This option will allow you to restore an exported container. To proceed, click OK and select your 'xuser-' directory. The container's settings will need to be configured after a successful import, but all files and shortcuts should be restored if you are restoring a real container. Beware, the directory you select will be copied into the app's storage directory, so be sure you have enough space. You can delete your copy afterward.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showContainerImportInfo = false; importLauncher.launch(null) }) {
                            androidx.compose.material3.Text("OK")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showContainerImportInfo = false }) {
                            androidx.compose.material3.Text("Cancel")
                        }
                    },
                )
            }
            if (showImportGame) {
                OverlayBackdrop(preferences) {
                    ImportGameScreen(
                        onBack = { showImportGame = false; containersRefreshKey++ },
                    )
                }
            }
            if (showShortcutSettings) {
                OverlayBackdrop(preferences) {
                    val shortcut = shortcutSettingsShortcut
                    if (shortcut != null) {
                        ShortcutSettingsScreen(
                            shortcut = shortcut,
                            onBack = {
                                showShortcutSettings = false
                                containersRefreshKey++
                            },
                        )
                    }
                }
            }

            // Лист «Ещё» — в окне приложения, поверх нижней панели.
            // Не ModalBottomSheet: его отдельное окно показывает статус-бар
            // и в ландшафте открывается только наполовину.
            if (showMoreSheet) {
                GlassMoreSheet(
                    onSelect = { screen ->
                        showMoreSheet = false
                        currentScreen = screen
                        when (screen) {
                            Screen.Steam -> onOpenSteam()
                            Screen.FileManager -> fileManagerContainerId = -1
                            else -> { }
                        }
                    },
                    onDismiss = { showMoreSheet = false },
                )
            }

            // Диалог первого запуска — поверх всего
            if (showFirstLaunchDialog) {
                FirstLaunchDialog(
                    preferences = preferences,
                    isDarkMode = firstLaunchDarkMode,
                    onThemeSelected = { dark ->
                        firstLaunchDarkMode = dark
                        // Немедленно обновляем pref, чтобы тема применилась
                        preferences.edit().putBoolean("dark_mode", dark).apply()
                    },
                    onDismiss = {
                        showFirstLaunchDialog = false
                        // Применяем выбранную тему через recreate в Activity
                        onDarkModeChange(firstLaunchDarkMode)
                    },
                )
            }

            // Диалог обновления (авто-проверка) — поверх всего
            UpdateCheckDialog(
                show = showUpdateDialog,
                checking = updateChecking,
                error = updateError,
                info = updateInfo,
                downloading = updateDownloading,
                progress = updateProgress,
                file = updateFile,
                onDismiss = { showUpdateDialog = false },
                setDownloading = { updateDownloading = it },
                setProgress = { updateProgress = it },
                setFile = { updateFile = it },
            )
        }
    }
    }
}

/**
 * Подложка вложенного экрана: обои (или непрозрачный фон, если обоев нет),
 * чтобы скрыть предыдущее меню и показать только обои.
 */
@Composable
private fun OverlayBackdrop(
    preferences: SharedPreferences,
    content: @Composable () -> Unit,
) {
    val wpPath by preferences.observeString(com.winlator.cmod.ui.theme.ThemePrefs.UI_WALLPAPER, "")
    val wpBlur by preferences.observeInt(com.winlator.cmod.ui.theme.ThemePrefs.UI_WALLPAPER_BLUR, 20)
    val wpDarken by preferences.observeInt(com.winlator.cmod.ui.theme.ThemePrefs.UI_WALLPAPER_DARKEN, 40)
    val wpLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val wpScale by preferences.observeFloat(com.winlator.cmod.ui.screens.wallpaperScaleKey(wpLandscape), 1f)
    val wpOffX by preferences.observeFloat(com.winlator.cmod.ui.screens.wallpaperOffsetXKey(wpLandscape), 0f)
    val wpOffY by preferences.observeFloat(com.winlator.cmod.ui.screens.wallpaperOffsetYKey(wpLandscape), 0f)

    Box(Modifier.fillMaxSize()) {
        if (wpPath.isNotEmpty()) {
            com.winlator.cmod.ui.screens.WallpaperLayer(
                path = wpPath,
                blur = wpBlur,
                darken = wpDarken,
                scale = wpScale,
                offsetRatioX = wpOffX,
                offsetRatioY = wpOffY,
                modifier = Modifier.fillMaxSize(),
                allowVideo = false,
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 1f)))
        }
        content()
    }
}



/**
 * Нижняя навигация в стиле GameHub: основные разделы + «Ещё».
 * Состав берётся из drawerItems, поэтому новый пункт меню появляется
 * в панели или в sheet автоматически.
 */
@Composable
private fun GlassBottomNavBar(
    currentScreen: Screen,
    onSelect: (Screen) -> Unit,
    onMore: () -> Unit,
) {
    val primaryScreens = listOf(Screen.Shortcuts, Screen.Containers, Screen.InputControls, Screen.Settings)
    val primary = primaryScreens.mapNotNull { ps -> drawerItems.firstOrNull { it.screen == ps } }
    val moreCount = drawerItems.size - primary.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Без учёта инсета панель залезает под системный нав-бар и нижние
            // табы перестают нажиматься.
            .windowInsetsPadding(WindowInsets.navigationBars)
            // Плавающая капсула: отступы от краёв экрана и системного нав-бара.
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
            .clip(CircleShape)
            // Непрозрачная база: просвечивать под панелью не должно.
            .background(glassSheetFill())
            .background(glassFillBrush())
            .border(glassBorder(), CircleShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primary.forEach { item ->
                NavTab(
                    labelResId = item.labelResId,
                    icon = item.icon,
                    selected = currentScreen == item.screen,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(item.screen) },
                )
            }
            if (moreCount > 0) {
                NavTab(
                    labelResId = R.string.more_menu,
                    icon = Icons.Filled.GridOn,
                    selected = showMoreSheetSelected(currentScreen, primary),
                    modifier = Modifier.weight(1f),
                    onClick = onMore,
                )
            }
        }
    }
}

/** Пункт выбран, если он относится к основным, но не показан в панели. */
private fun showMoreSheetSelected(currentScreen: Screen, primary: List<DrawerItem>): Boolean =
    primary.none { it.screen == currentScreen } &&
        drawerItems.any { it.screen == currentScreen }

@Composable
private fun NavTab(
    labelResId: Int,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (selected) glassAccent() else glassValueColor().copy(alpha = 0.7f)
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) glassAccent().copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(labelResId),
            fontSize = 11.sp,
            maxLines = 1,
            color = tint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Стеклянный sheet «Ещё меню»: сетка круглых иконок, 4 в ряд —
 * как в референсе GameHub.
 */
@Composable
private fun GlassMoreSheet(
    onSelect: (Screen) -> Unit,
    onDismiss: () -> Unit,
) {
    val primaryScreens = listOf(Screen.Shortcuts, Screen.Containers, Screen.InputControls, Screen.Settings)
    val secondary = drawerItems.filterNot { it.screen in primaryScreens }

    // Отдельное окно ModalBottomSheet показывало системный статус-бар и в
    // ландшафте открывалось наполовину. Этот лист живёт в окне приложения.
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.more_menu),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = glassTitleColor(),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = glassValueColor())
                }
            }

            secondary.chunked(4).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { item ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (glassIsDark()) Color.White.copy(alpha = 0.08f)
                                        else Color.Black.copy(alpha = 0.05f),
                                    )
                                    .clickable { onSelect(item.screen) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelResId),
                                    tint = glassValueColor(),
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(item.labelResId),
                                fontSize = 12.sp,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                color = glassTitleColor(),
                            )
                        }
                    }
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
