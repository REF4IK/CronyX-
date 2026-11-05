package com.winlator.cmod.steam

import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors

import com.winlator.cmod.ui.components.GlassPill

import com.winlator.cmod.ui.components.glassCardFill
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.absoluteValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Badge
import androidx.compose.material3.Switch
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winlator.cmod.R
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.steam.enums.AppType
import com.winlator.cmod.steam.enums.ControllerSupport
import com.winlator.cmod.steam.enums.DownloadPhase
import com.winlator.cmod.steam.enums.PathType
import com.winlator.cmod.steam.enums.SaveLocation
import com.winlator.cmod.steam.enums.SyncResult
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.achievements.AchievementOverlay
import com.winlator.cmod.steam.achievements.AchievementWatcherHost
import com.winlator.cmod.steam.ui.SteamContainerOption
import androidx.compose.material3.HorizontalDivider
import com.winlator.cmod.steam.ui.AchievementsSheet
import com.winlator.cmod.steam.ui.SteamFriendsScreen
import com.winlator.cmod.steam.ui.SteamGameDetailUi
import com.winlator.cmod.steam.ui.SteamLibraryGameUi
import com.winlator.cmod.steam.ui.SteamLibraryUiState
import com.winlator.cmod.steam.ui.SteamLibraryViewModel
import com.winlator.cmod.steam.store.SteamStoreViewModel
import com.winlator.cmod.steam.store.ui.StoreCatalogScreen
import com.winlator.cmod.steam.store.ui.StoreDetailScreen
import com.winlator.cmod.steam.store.ui.StoreHomeScreen
import com.winlator.cmod.steam.store.ui.StoreSearchResults
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.utils.getAvatarURL
import com.winlator.cmod.steam.workshop.WorkshopManager
import com.winlator.cmod.ui.theme.WinlatorTheme
import com.winlator.cmod.utils.StorageUtils
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SteamViewMode { GRID, GRID_CAPSULE, LIST, COMPACT, CAROUSEL }

private enum class SteamTab {
    STORE,
    STEAM,
    DOWNLOADS,
}

private enum class SteamContentFilter {
    ALL,
    GAMES,
    DLC,
    APPLICATIONS,
    TOOLS,
}

private fun SteamContentFilter.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    SteamContentFilter.ALL -> Icons.Filled.Apps
    SteamContentFilter.GAMES -> Icons.Filled.SportsEsports
    SteamContentFilter.DLC -> Icons.Filled.Extension
    SteamContentFilter.APPLICATIONS -> Icons.Filled.GridView
    SteamContentFilter.TOOLS -> Icons.Filled.Build
}

class SteamLibraryActivity : ComponentActivity() {
    private var pendingSelectedAppId by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSelectedAppId = intent.getIntExtra(EXTRA_SELECTED_APP_ID, 0).takeIf { it > 0 }
        SteamService.initLoginStatus(this)
        SteamService.start(this)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            WinlatorTheme {
                val viewModel: SteamLibraryViewModel = viewModel()
                val state by viewModel.uiState.collectAsState()
                val context = LocalContext.current
                val activity = this@SteamLibraryActivity
                val currentSelectedGameId by rememberUpdatedState(state.selectedGame?.appId)
                var pendingExportGameId by rememberSaveable { mutableStateOf<Int?>(null) }
                var pendingImportGameId by rememberSaveable { mutableStateOf<Int?>(null) }
                val loginLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    viewModel.onLoginFinished()
                }
                val folderPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    val selectedGameId = currentSelectedGameId ?: return@rememberLauncherForActivityResult
                    if (uri == null) return@rememberLauncherForActivityResult

                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }

                    val resolvedPath = resolveFolderPath(context, uri)
                    if (resolvedPath.isNullOrBlank()) {
                        Toast.makeText(context, context.getString(R.string.steam_library_folder_resolve_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.setCustomInstallPath(selectedGameId, resolvedPath)
                    }
                }
                val savesExportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/zip"),
                ) { uri ->
                    val appId = pendingExportGameId
                    pendingExportGameId = null
                    if (uri == null || appId == null) return@rememberLauncherForActivityResult
                    activity.lifecycleScope.launch {
                        val exported = SteamGameActions.exportSaves(activity, appId, uri)
                        val message = if (exported) {
                            activity.getString(R.string.steam_library_saves_export_success)
                        } else {
                            activity.getString(R.string.steam_library_saves_export_failed)
                        }
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                val savesImportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    val appId = pendingImportGameId
                    pendingImportGameId = null
                    if (uri == null || appId == null) return@rememberLauncherForActivityResult
                    activity.lifecycleScope.launch {
                        val imported = SteamGameActions.importSaves(activity, appId, uri)
                        val message = if (imported) {
                            activity.getString(R.string.steam_library_saves_import_success)
                        } else {
                            activity.getString(R.string.steam_library_saves_import_failed)
                        }
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.loadContainers()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    SteamLibraryScreen(
                        state = state,
                    requestedSelectedGameId = pendingSelectedAppId,
                    onBack = ::finish,
                    onLogin = { loginLauncher.launch(Intent(activity, SteamLoginActivity::class.java)) },
                    onRefresh = viewModel::refreshLibrary,
                    onLogout = viewModel::logOut,
                    onSetOfflineMode = viewModel::setOfflineMode,
                    onDismissError = viewModel::clearError,
                    onSelectGame = viewModel::setSelectedGame,
                    onSelectContainer = viewModel::setSelectedContainer,
                    onPickInstallFolder = { folderPickerLauncher.launch(null) },
                    onDownload = viewModel::downloadGame,
                    onPauseDownload = viewModel::pauseDownload,
                    onResumeDownload = viewModel::resumeDownload,
                    onCancelDownload = viewModel::cancelDownload,
                    onPlay = viewModel::playGame,
                    onDelete = viewModel::deleteGame,
                    onOpenSettings = { appId, containerId ->
                        val opened = SteamGameActions.openShortcutSettings(activity, appId, containerId)
                        if (!opened) {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.steam_library_settings_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onCreateShortcut = { appId, containerId ->
                        activity.lifecycleScope.launch {
                            val created = SteamGameActions.addShortcutToHomeScreen(activity, appId, containerId)
                            val message = if (created) {
                                activity.getString(R.string.steam_library_shortcut_created)
                            } else {
                                activity.getString(R.string.steam_library_shortcut_failed)
                            }
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExportSaves = { appId, gameName ->
                        pendingExportGameId = appId
                        val safeName = gameName
                            .replace(":", "")
                            .replace("/", "_")
                            .replace("\\", "_")
                            .replace(" ", "_")
                            .ifBlank { "steam_game" }
                        savesExportLauncher.launch("${safeName}_saves.zip")
                    },
                    onImportSaves = { appId ->
                        pendingImportGameId = appId
                        savesImportLauncher.launch(arrayOf("application/zip"))
                    },
                    onUploadCloudSaves = { appId ->
                        activity.lifecycleScope.launch {
                            val synced = SteamGameActions.syncCloudSaves(activity, appId, SaveLocation.Local)
                            val message = if (synced) {
                                activity.getString(R.string.steam_library_cloud_upload_success)
                            } else {
                                activity.getString(R.string.steam_library_cloud_upload_failed)
                            }
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDownloadCloudSaves = { appId ->
                        activity.lifecycleScope.launch {
                            val synced = SteamGameActions.syncCloudSaves(activity, appId, SaveLocation.Remote)
                            val message = if (synced) {
                                activity.getString(R.string.steam_library_cloud_download_success)
                            } else {
                                activity.getString(R.string.steam_library_cloud_download_failed)
                            }
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSelectedGameRequestConsumed = {
                        pendingSelectedAppId = null
                    },
                    )
                    AchievementWatcherHost(appId = state.selectedGame?.appId)
                    AchievementOverlay()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSelectedAppId = intent.getIntExtra(EXTRA_SELECTED_APP_ID, 0).takeIf { it > 0 }
    }

    companion object {
        const val EXTRA_SELECTED_APP_ID = "steam_library_selected_app_id"
    }
}

private fun resolveFolderPath(context: android.content.Context, uri: Uri): String? {
    val directPath = FileUtils.getFilePathFromUri(context, uri)
    if (!directPath.isNullOrBlank()) return directPath

    return try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        when {
            docId.startsWith("primary:") -> {
                val path = docId.substringAfter(":")
                val storage = android.os.Environment.getExternalStorageDirectory().path
                if (path.isBlank()) storage else "$storage/$path"
            }
            docId.contains(":") -> {
                val parts = docId.split(":", limit = 2)
                if (parts.size == 2) {
                    if (parts[1].isBlank()) "/storage/${parts[0]}" else "/storage/${parts[0]}/${parts[1]}"
                } else {
                    uri.path
                }
            }
            else -> uri.path
        }
    } catch (_: Exception) {
        uri.path
    }
}

@Composable
private fun SteamLibraryScreen(
    state: SteamLibraryUiState,
    requestedSelectedGameId: Int?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onSetOfflineMode: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onSelectGame: (Int) -> Unit,
    onSelectContainer: (Int) -> Unit,
    onPickInstallFolder: (Int) -> Unit,
    onDownload: (Int) -> Unit,
    onPauseDownload: (Int) -> Unit,
    onResumeDownload: (Int) -> Unit,
    onCancelDownload: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onOpenSettings: (Int, Int?) -> Unit,
    onCreateShortcut: (Int, Int?) -> Unit,
    onExportSaves: (Int, String) -> Unit,
    onImportSaves: (Int) -> Unit,
    onUploadCloudSaves: (Int) -> Unit,
    onDownloadCloudSaves: (Int) -> Unit,
    onSelectedGameRequestConsumed: () -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(SteamTab.STEAM) }
    var contentFilter by rememberSaveable { mutableStateOf(SteamContentFilter.GAMES) }
    var selectedOverlayGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var storeDetailAppId by rememberSaveable { mutableStateOf<Int?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var loginPlaceholderUnlocked by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFriends by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf<Int?>(null) }
    var viewMode by rememberSaveable { mutableStateOf(SteamViewMode.GRID) }
    val storeViewModel: SteamStoreViewModel = viewModel()
    val storeState by storeViewModel.ui.collectAsState()

    val tabGames = when (tab) {
        SteamTab.DOWNLOADS -> state.games.filter { it.isDownloading || it.installed }
        else -> state.games
    }
    val visibleGames = tabGames
        .distinctBy { it.appId }
        .filter { it.matchesContentFilter(contentFilter) }
        .filter { game ->
            searchQuery.isBlank() ||
                game.name.contains(searchQuery, ignoreCase = true) ||
                game.subtitle.contains(searchQuery, ignoreCase = true)
        }
    val filteredSteamCount = state.games.distinctBy { it.appId }.count { it.matchesContentFilter(contentFilter) }
    val selectedContainerId = state.selectedGame?.assignedContainerId ?: state.selectedContainerId
    val selectedGame = state.selectedGame?.takeIf { it.appId == selectedOverlayGameId }
    val profileName = state.profile.name.ifBlank { "Steam" }
    val profileOnline = state.profile.isOnline
    val profileStatus = state.profile.status
    val profileGame = state.profile.currentGame
    val hasOfflineLibrary = state.games.isNotEmpty()
    val hasStoredSession = remember(state.isLoggedIn, state.isOfflineMode) {
        state.isLoggedIn || state.isOfflineMode || SteamService.hasStoredCredentials(context)
    }
    val shouldShowStartupLoader = !state.isLoggedIn && !loginPlaceholderUnlocked && hasStoredSession && !hasOfflineLibrary

    LaunchedEffect(state.isLoggedIn, state.isConnected) {
        if (state.isLoggedIn) {
            loginPlaceholderUnlocked = true
        } else if (!loginPlaceholderUnlocked) {
            delay(1200)
            loginPlaceholderUnlocked = true
        }
    }

    LaunchedEffect(requestedSelectedGameId, state.games) {
        val targetAppId = requestedSelectedGameId ?: return@LaunchedEffect
        val targetGame = state.games.firstOrNull { it.appId == targetAppId } ?: return@LaunchedEffect
        selectedOverlayGameId = targetGame.appId
        onSelectGame(targetGame.appId)
        onSelectedGameRequestConsumed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // A single soft primary wash at the top, so the chrome sits on something
        // with depth without tinting the artwork below it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SteamTopBar(
                tab = tab,
                isLoggedIn = state.isLoggedIn,
                steamCount = filteredSteamCount,
                canSearch = state.isLoggedIn || hasOfflineLibrary || tab == SteamTab.STORE,
                viewMode = viewMode,
                profileName = profileName,
                avatarUrl = state.profile.avatarUrl,
                profileOnline = state.profile.isOnline,
                profileStatus = state.profile.status,
                profileGame = state.profile.currentGame,
                onBack = onBack,
                onRefresh = {
                    if (tab == SteamTab.STORE) storeViewModel.refreshHome()
                    else onRefresh()
                },
                onSelectTab = {
                    tab = it
                    storeDetailAppId = null
                },
                onSearchClick = {
                    searchVisible = !searchVisible
                    if (!searchVisible) {
                        searchQuery = ""
                        storeViewModel.setSearchQuery("")
                    }
                },
                onMenuClick = { menuExpanded = true },
                onProfileClick = { if (state.isLoggedIn) showProfileDialog = true },
                onViewModeChange = { viewMode = it },
            )

            if (searchVisible) {
                SteamSearchField(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        if (tab == SteamTab.STORE) storeViewModel.setSearchQuery(it)
                    },
                    onClose = {
                        searchQuery = ""
                        searchVisible = false
                        storeViewModel.setSearchQuery("")
                    },
                )
                // Suggest-дропдаун под полем — только для магазина
                if (tab == SteamTab.STORE && searchQuery.trim().length >= 2 && storeState.suggest.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            storeState.suggest.forEach { s ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        storeViewModel.clearSuggest()
                                        storeDetailAppId = s.id
                                    }.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (s.image.isNotBlank()) {
                                        AsyncImage(
                                            model = s.image,
                                            contentDescription = null,
                                            modifier = Modifier.width(96.dp).aspectRatio(460f / 215f).clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(s.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (s.priceText.isNotBlank()) {
                                            Text(s.priceText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                Text(
                    text = stringResource(R.string.steam_library_content_types_title),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                val filterCounts = remember(state.games) {
                    val distinct = state.games.distinctBy { it.appId }
                    SteamContentFilter.entries.associateWith { f -> distinct.count { it.matchesContentFilter(f) } }
                }
                SteamContentFilter.entries.forEach { filter ->
                    val selected = contentFilter == filter
                    SteamMenuRow(
                        text = filter.label(),
                        bold = selected,
                        textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        leading = {
                            Icon(
                                filter.icon(),
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${filterCounts[filter] ?: 0}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        onClick = {
                            contentFilter = filter
                            menuExpanded = false
                        },
                    )
                }
                if (state.isLoggedIn || hasStoredSession) {
                    SteamMenuRow(
                        text = stringResource(R.string.steam_library_refresh),
                        leading = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRefresh()
                        },
                    )
                }
                if (state.isOfflineMode || (!state.isLoggedIn && hasStoredSession)) {
                    SteamMenuRow(
                        text = stringResource(R.string.steam_library_go_online),
                        leading = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSetOfflineMode(false)
                            onRefresh()
                        },
                    )
                } else if (state.isLoggedIn) {
                    SteamMenuRow(
                        text = stringResource(R.string.steam_library_go_offline),
                        leading = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSetOfflineMode(true)
                        },
                    )
                }
                HorizontalDivider()
                var autoUpdateEnabled by remember { mutableStateOf(PrefManager.autoUpdateEnabled) }
                var wifiOnly by remember { mutableStateOf(PrefManager.autoUpdateWifiOnly) }
                SteamMenuRow(
                    text = stringResource(R.string.steam_library_autoupdate_title),
                    trailing = {
                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = {
                                autoUpdateEnabled = it
                                PrefManager.autoUpdateEnabled = it
                            },
                        )
                    },
                    onClick = {
                        autoUpdateEnabled = !autoUpdateEnabled
                        PrefManager.autoUpdateEnabled = autoUpdateEnabled
                    },
                )
                if (autoUpdateEnabled) {
                    SteamMenuRow(
                        text = stringResource(R.string.steam_library_autoupdate_wifi_only),
                        trailing = {
                            Switch(
                                checked = wifiOnly,
                                onCheckedChange = {
                                    wifiOnly = it
                                    PrefManager.autoUpdateWifiOnly = it
                                },
                            )
                        },
                        onClick = {
                            wifiOnly = !wifiOnly
                            PrefManager.autoUpdateWifiOnly = wifiOnly
                        },
                    )
                }
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.steam_library_download_speed_title),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                var downloadSpeed by remember { mutableStateOf(PrefManager.downloadSpeed) }
                val speedOptions = listOf(
                    8 to stringResource(R.string.steam_library_download_speed_slow),
                    16 to stringResource(R.string.steam_library_download_speed_medium),
                    24 to stringResource(R.string.steam_library_download_speed_fast),
                    32 to stringResource(R.string.steam_library_download_speed_max),
                )
                speedOptions.forEach { (value, label) ->
                    val selected = downloadSpeed == value
                    SteamMenuRow(
                        text = label,
                        bold = selected,
                        textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        trailing = {
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            downloadSpeed = value
                            PrefManager.downloadSpeed = value
                        },
                    )
                }
                if (state.isLoggedIn || hasStoredSession) {
                    SteamMenuRow(
                        text = stringResource(R.string.steam_library_logout_action),
                        textColor = MaterialTheme.colorScheme.error,
                        leading = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onLogout()
                        },
                    )
                } else {
                    SteamMenuRow(
                        text = stringResource(R.string.steam_library_login_button),
                        leading = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onLogin()
                        },
                    )
                }
            }

            if (state.isRefreshing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                }
            }
            if (state.errorMessage != null) {
                SteamInlineMessage(
                    title = stringResource(R.string.steam_library_error_title),
                    body = state.errorMessage,
                    onDismiss = onDismissError,
                )
            }

            // Разделы витрины поверх библиотечной сетки
            if (tab == SteamTab.STORE) {
                if (storeState.catalogOpen) {
                    StoreCatalogScreen(
                        onBack = { storeViewModel.closeCatalog() },
                        onOpenDetail = {
                            storeViewModel.closeCatalog()
                            storeDetailAppId = it
                        },
                        viewModel = storeViewModel,
                    )
                } else if (storeDetailAppId != null) {
                    StoreDetailScreen(
                        appId = storeDetailAppId!!,
                        onBack = { storeDetailAppId = null },
                        onFindInLibrary = { appId ->
                            storeDetailAppId = null
                            tab = SteamTab.STEAM
                            val target = state.games.firstOrNull { it.appId == appId }
                            if (target != null) {
                                selectedOverlayGameId = target.appId
                                onSelectGame(target.appId)
                            }
                        },
                        viewModel = storeViewModel,
                        onOpenDetail = { storeDetailAppId = it },
                    )
                } else if (searchQuery.isNotBlank()) {
                    StoreSearchResults(
                        state = storeState,
                        onOpenDetail = { storeDetailAppId = it },
                    )
                } else {
                    StoreHomeScreen(
                        onOpenDetail = { storeDetailAppId = it },
                        viewModel = storeViewModel,
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LibraryGridContent(
                    state = state,
                    viewMode = viewMode,
                    visibleGames = visibleGames,
                    shouldShowStartupLoader = shouldShowStartupLoader,
                    hasOfflineLibrary = hasOfflineLibrary,
                    hasStoredSession = hasStoredSession,
                    onLogin = onLogin,
                    onOpenGame = { appId ->
                        selectedOverlayGameId = appId
                        onSelectGame(appId)
                    },
                )
                }
            }
        }

        if (selectedOverlayGameId != null) {
            // Оверлей ждёт деталь из ViewModel; если её нет (офлайн/гонка) —
            // строим минимальную из списка, чтобы тап всегда открывал карточку
            val overlayGame = selectedGame ?: state.games
                .firstOrNull { it.appId == selectedOverlayGameId }
                ?.let {
                    com.winlator.cmod.steam.ui.SteamGameDetailUi(
                        appId = it.appId,
                        name = it.name,
                        subtitle = it.subtitle,
                        appType = it.appType,
                        capsuleUrl = it.capsuleUrl,
                        heroUrl = it.heroUrl,
                        logoUrl = it.logoUrl,
                        installed = it.installed,
                        isDownloading = it.isDownloading,
                        downloadPhase = it.downloadPhase,
                        downloadProgress = it.downloadProgress,
                        statusLine = it.statusLine,
                        installPath = "",
                        downloadSizeBytes = 0L,
                        installSizeBytes = 0L,
                        availableBytes = 0L,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        speedBytesPerSec = null,
                        etaMs = null,
                        currentFileName = null,
                        releaseDateSeconds = 0L,
                        assignedContainerId = it.assignedContainerId,
                    )
                }
            SteamGameOverlay(
                game = overlayGame,
                activeTab = tab,
                containers = state.containers,
                selectedContainerId = selectedContainerId,
                onClose = { selectedOverlayGameId = null },
                onSelectContainer = onSelectContainer,
                onPickInstallFolder = onPickInstallFolder,
                onDownload = { appId ->
                    selectedOverlayGameId = appId
                    tab = SteamTab.DOWNLOADS
                    onSelectGame(appId)
                    onDownload(appId)
                },
                onPauseDownload = onPauseDownload,
                onResumeDownload = onResumeDownload,
                onCancelDownload = onCancelDownload,
                onPlay = onPlay,
                onDelete = onDelete,
                onOpenSettings = onOpenSettings,
                onCreateShortcut = onCreateShortcut,
                onExportSaves = onExportSaves,
                onImportSaves = onImportSaves,
                onUploadCloudSaves = onUploadCloudSaves,
                onDownloadCloudSaves = onDownloadCloudSaves,
                isOfflineMode = state.isOfflineMode,
            )
        }

        if (showFriends) {
            SteamFriendsScreen(onBack = { showFriends = false })
        }
        if (showProfileDialog) {
            SteamProfileDialog(
                profile = state.profile,
                isLoggedIn = state.isLoggedIn,
                gamesCount = state.games.size,
                installedCount = state.games.count { it.installed },
                onDismiss = { showProfileDialog = false },
                onOpenFriends = { showProfileDialog = false; showFriends = true },
            )
        }
    }
}

@Composable
private fun SteamTopBar(
    tab: SteamTab,
    isLoggedIn: Boolean,
    steamCount: Int,
    canSearch: Boolean,
    viewMode: SteamViewMode,
    profileName: String,
    avatarUrl: String,
    profileOnline: Boolean,
    profileStatus: String,
    profileGame: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (SteamTab) -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit,
    onViewModeChange: (SteamViewMode) -> Unit,
) {
    // Одна строка: назад + табы + поиск/обновление/меню + аватар в углу.
    // Высота тулбара ужата: кнопки 36dp, табы без лишних паддингов.
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(max = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundActionButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, size = 36)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(glassCardFill())
                .padding(3.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SteamTabIcon(
                icon = Icons.Filled.Home,
                label = "Магазин",
                selected = tab == SteamTab.STORE,
                onClick = { onSelectTab(SteamTab.STORE) },
            )
            SteamTabIcon(
                icon = Icons.Filled.GridView,
                label = "STEAM",
                badge = steamCount.takeIf { it > 0 }?.toString(),
                selected = tab == SteamTab.STEAM,
                onClick = { onSelectTab(SteamTab.STEAM) },
            )
            SteamTabIcon(
                icon = Icons.Filled.CloudDownload,
                label = stringResource(R.string.steam_library_tab_downloads),
                selected = tab == SteamTab.DOWNLOADS,
                onClick = { onSelectTab(SteamTab.DOWNLOADS) },
            )
        }
        RoundActionButton(Icons.Filled.Search, onSearchClick, enabled = canSearch, size = 36)
        RoundActionButton(Icons.Filled.Refresh, onRefresh, size = 36)
        RoundActionButton(Icons.Filled.Tune, onMenuClick, size = 36)
        // Профиль — аватар в правом углу тулбара
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = profileName,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = profileName, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            if (isLoggedIn) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (profileOnline) Color(0xFF3DDC84) else MaterialTheme.colorScheme.onSurfaceVariant)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
    }

    // Переключатель вида — только на табе STEAM, в одну строку с отступом 0
    if (tab == SteamTab.STEAM) {
        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clip(CircleShape)
                .background(glassCardFill())
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SteamViewButton(
                icon = Icons.Filled.GridView,
                selected = viewMode == SteamViewMode.GRID,
                onClick = { onViewModeChange(SteamViewMode.GRID) },
            )
            SteamViewButton(
                icon = Icons.Filled.Image,
                selected = viewMode == SteamViewMode.GRID_CAPSULE,
                onClick = { onViewModeChange(SteamViewMode.GRID_CAPSULE) },
            )
            SteamViewButton(
                icon = Icons.Filled.FormatListBulleted,
                selected = viewMode == SteamViewMode.LIST,
                onClick = { onViewModeChange(SteamViewMode.LIST) },
            )
            SteamViewButton(
                icon = Icons.Filled.ViewCompact,
                selected = viewMode == SteamViewMode.COMPACT,
                onClick = { onViewModeChange(SteamViewMode.COMPACT) },
            )
            SteamViewButton(
                icon = Icons.Filled.Slideshow,
                selected = viewMode == SteamViewMode.CAROUSEL,
                onClick = { onViewModeChange(SteamViewMode.CAROUSEL) },
            )
        }
    }
}

@Composable
private fun SteamTabIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "tabIconContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabIconContent",
    )
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(18.dp))
            Text(
                text = label,
                color = content,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(content.copy(alpha = 0.22f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = badge,
                        color = content,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGridContent(
    state: SteamLibraryUiState,
    viewMode: SteamViewMode,
    visibleGames: List<SteamLibraryGameUi>,
    shouldShowStartupLoader: Boolean,
    hasOfflineLibrary: Boolean,
    hasStoredSession: Boolean,
    onLogin: () -> Unit,
    onOpenGame: (Int) -> Unit,
) {
    when {
        state.containers.isEmpty() -> SteamPlaceholder(
            title = stringResource(R.string.steam_no_containers),
            body = stringResource(R.string.steam_library_no_containers_message),
        )
        shouldShowStartupLoader -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
        }
        !state.isLoggedIn && !hasOfflineLibrary && !hasStoredSession -> SteamPlaceholder(
            title = stringResource(R.string.steam_library_login_title),
            body = stringResource(R.string.steam_library_login_hint),
            actionLabel = stringResource(R.string.steam_library_login_button),
            onAction = onLogin,
        )
        visibleGames.isEmpty() -> SteamPlaceholder(
            title = stringResource(R.string.steam_library_empty_title),
            body = stringResource(R.string.steam_library_empty_filtered),
        )
        else -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (viewMode) {
                    SteamViewMode.GRID -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(182.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(visibleGames, key = { it.appId }) { game ->
                                SteamGameCard(game = game, onClick = { onOpenGame(game.appId) })
                            }
                        }
                    }
                    SteamViewMode.GRID_CAPSULE -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(122.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(visibleGames, key = { it.appId }) { game ->
                                SteamGameCapsuleCard(game = game, onClick = { onOpenGame(game.appId) })
                            }
                        }
                    }
                    SteamViewMode.LIST -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(visibleGames, key = { it.appId }) { game ->
                                SteamGameListCard(game = game, onClick = { onOpenGame(game.appId) })
                            }
                        }
                    }
                    SteamViewMode.COMPACT -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(visibleGames, key = { it.appId }) { game ->
                                SteamGameCompactCard(game = game, onClick = { onOpenGame(game.appId) })
                            }
                        }
                    }
                    SteamViewMode.CAROUSEL -> {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(visibleGames, key = { it.appId }) { game ->
                                SteamGameCarouselCard(game = game, onClick = { onOpenGame(game.appId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Int = 48,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "iconPress",
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(size.dp)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    glassCardFill()
                } else {
                    glassCardFill()
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.size((size * 0.44f).dp),
        )
    }
}

/** A segment of the tab track: the selected one is a filled pill. */
@Composable
private fun SteamTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "tabContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "tabContent",
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SteamViewButton(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "viewContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "viewContent",
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
    }
}

/** Error strip: an error-tinted rail plus the message, dismissible. */
@Composable
private fun SteamInlineMessage(title: String, body: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f))
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Empty state. Centred, narrow, with a soft primary halo behind the icon so the
 * screen still feels composed rather than blank.
 */
@Composable
private fun SteamPlaceholder(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.GridView,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, shape = CircleShape) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SteamMenuRow(
    text: String,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

@Composable
private fun ProfileStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = glassCardFill()),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SteamProfileDialog(
    profile: com.winlator.cmod.steam.ui.SteamProfileUi,
    isLoggedIn: Boolean,
    gamesCount: Int,
    installedCount: Int,
    onDismiss: () -> Unit,
    onOpenFriends: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var chatFriendId by remember { mutableStateOf<Long?>(null) }
    var chatInput by remember { mutableStateOf("") }
    val friends by SteamService.friendsList.collectAsState()
    val chatMap by SteamService.chatMessages.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val onlineColor = Color(0xFF3DDC84)
    val steamId = SteamService.userSteamId?.convertToUInt64() ?: PrefManager.steamUserSteamId64
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth()) {
                // Шапка: градиент + аватар с кольцом статуса + имя + пилюля статуса
                Box(
                    modifier = Modifier.fillMaxWidth().background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f),
                            ),
                        ),
                    ).padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(76.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(3.dp, if (profile.isOnline) onlineColor else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (profile.avatarUrl.isNotBlank()) {
                                AsyncImage(model = profile.avatarUrl, contentDescription = profile.name, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(profile.name.ifBlank { "Steam" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (profile.isOnline) onlineColor else MaterialTheme.colorScheme.onSurfaceVariant))
                                Text(
                                    profile.status.ifBlank { if (profile.isOnline) "В сети" else "Не в сети" },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (profile.isOnline) onlineColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (profile.currentGame?.isNotBlank() == true) {
                                Text("Играет: ${profile.currentGame}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
                    }
                }
                // Полоса статистики
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProfileStatCard(label = "Игры", value = gamesCount.toString(), modifier = Modifier.weight(1f))
                    ProfileStatCard(label = "Установлено", value = installedCount.toString(), modifier = Modifier.weight(1f))
                    ProfileStatCard(label = "Друзья", value = "${friends.count { it.isOnline }}/${friends.size}", modifier = Modifier.weight(1f))
                }
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(selected = tab == 0, onClick = { tab = 0; chatFriendId = null }, text = { Text("Профиль") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Друзья (${friends.count { it.isOnline }}/${friends.size})") })
                }
                HorizontalDivider()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(colors = CardDefaults.cardColors(containerColor = glassCardFill()), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (profile.isOnline) onlineColor else MaterialTheme.colorScheme.onSurfaceVariant))
                                        Text("Статус", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text(
                                            profile.status.ifBlank { if (profile.isOnline) "В сети" else "Не в сети" },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (profile.isOnline) onlineColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text("Steam ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$steamId", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = {
                                            clipboard.setText(AnnotatedString("$steamId"))
                                            Toast.makeText(context, "Steam ID скопирован", Toast.LENGTH_SHORT).show()
                                        }) { Icon(Icons.Filled.ContentCopy, null, tint = MaterialTheme.colorScheme.primary) }
                                    }
                                    if (!isLoggedIn) {
                                        Text("Гостевой режим — войди, чтобы видеть друзей и чат.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Button(onClick = onOpenFriends, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Filled.Person, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Открыть чат с друзьями")
                            }
                        }
                        1 -> {
                            if (chatFriendId != null) {
                                val fid = chatFriendId!!
                                val friend = friends.firstOrNull { it.steamId64 == fid }
                                val msgs = chatMap[fid] ?: emptyList()
                                Column(Modifier.fillMaxSize()) {
                                    Row(Modifier.fillMaxWidth().background(glassCardFill()).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { chatFriendId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                                        Text(friend?.name ?: "Чат", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    }
                                    LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(msgs) { m ->
                                            val isMe = !m.isIncoming
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
                                                Surface(color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.widthIn(max = 260.dp)) {
                                                    Text(m.text, modifier = Modifier.padding(10.dp), color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value = chatInput, onValueChange = { chatInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Сообщение...") }, singleLine = true, shape = GlassFieldShape, colors = glassFieldColors())
                                        IconButton(onClick = {
                                            if (chatInput.isNotBlank()) {
                                                val t = chatInput; chatInput = ""
                                                scope.launch { runCatching { SteamService.sendChatMessage(fid, t) } }
                                            }
                                        }) { Icon(Icons.Filled.Send, null, tint = MaterialTheme.colorScheme.primary) }
                                    }
                                }
                            } else {
                                if (friends.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Нет друзей или не загружено", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                } else {
                                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(friends, key = { it.steamId64 }) { f ->
                                            Card(modifier = Modifier.fillMaxWidth().clickable { chatFriendId = f.steamId64 }, colors = CardDefaults.cardColors(containerColor = glassCardFill()), shape = RoundedCornerShape(12.dp)) {
                                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                                                        if (f.avatarHash.isNotBlank()) {
                                                            AsyncImage(model = f.avatarHash.getAvatarURL(), contentDescription = f.name, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                                        } else Icon(Icons.Filled.Person, null, modifier = Modifier.size(18.dp))
                                                    }
                                                    Column(Modifier.weight(1f)) {
                                                        Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                        Text(if (f.isOnline) (f.gameName?.takeIf { it.isNotBlank() } ?: "В сети") else "Не в сети", style = MaterialTheme.typography.labelSmall, color = if (f.isOnline) Color(0xFF3DDC84) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                    }
                                                    if (f.isOnline) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF3DDC84)))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Deterministic accent for a game, derived from its app id. Used to tint the
 * fallback art so a library without capsule images still reads as a set of
 * distinct titles rather than a wall of identical grey boxes.
 */
private fun steamAccentFor(appId: Int): Color {
    val hues = listOf(202f, 258f, 292f, 338f, 16f, 42f, 92f, 160f)
    return Color.hsl(hues[(appId.hashCode().absoluteValue) % hues.size], 0.42f, 0.34f)
}

/** First letters of the title — the fallback "cover" when no art exists. */
private fun steamInitials(name: String): String =
    name.split(' ', ':', '-')
        .filter { it.isNotBlank() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }

/**
 * Cover art with a graceful fallback. Steam's `libraryCapsule` is a 2:3 poster,
 * so it is shown uncropped; when it is missing we paint a tinted panel with the
 * title's initials instead of leaving an empty rectangle.
 */
@Composable
private fun SteamCoverArt(
    game: SteamLibraryGameUi,
    cacheKey: String,
    targetSize: Pair<Int, Int>,
    initialsStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    imageUrl: String = game.capsuleUrl,
) {
    val context = LocalContext.current
    val accent = remember(game.appId) { steamAccentFor(game.appId) }
    var artFailed by remember(game.appId, imageUrl) { mutableStateOf(false) }

    val request = remember(game.appId, imageUrl, cacheKey) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(180)
            .memoryCacheKey("$cacheKey-${game.appId}")
            .diskCacheKey(imageUrl)
            .size(targetSize.first, targetSize.second)
            .build()
    }

    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isBlank() || artFailed) {
            Text(
                text = steamInitials(game.name),
                color = Color.White.copy(alpha = 0.92f),
                style = initialsStyle,
                fontWeight = FontWeight.Black,
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { artFailed = true },
            )
        }
    }
}

/**
 * Grid tile: a full-bleed 2:3 poster with the title laid over a scrim, an
 * installed dot, and a progress bar pinned to the bottom edge while downloading.
 */
@Composable
private fun SteamGameCard(game: SteamLibraryGameUi, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardPress",
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(glassCardFill())
            .then(
                if (game.installed) {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        RoundedCornerShape(14.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        // Steam header art is 460x215 — matching that ratio shows it uncropped.
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f)) {
            SteamCoverArt(
                game = game,
                cacheKey = "steam-grid",
                targetSize = 460 to 215,
                initialsStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxSize(),
            )

            if (game.isDownloading) {
                // Progress hugs the bottom edge of the art, above the caption.
                LinearProgressIndicator(
                    progress = { game.downloadProgress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Black.copy(alpha = 0.45f),
                )
            }

            if (game.installed && !game.isDownloading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = game.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (game.isDownloading) {
                Text(
                    text = game.statusLine,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            } else if (game.subtitle.isNotBlank()) {
                Text(
                    text = game.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * List row: a small 2:3 poster, the title with its developer line, and the
 * install state carried by a slim accent rail down the leading edge.
 */
@Composable
private fun SteamGameListCard(game: SteamLibraryGameUi, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            glassCardFill()
        },
        label = "rowPress",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SteamCoverArt(
            game = game,
            cacheKey = "steam-list",
            targetSize = 230 to 108,
            initialsStyle = MaterialTheme.typography.titleMedium,
            imageUrl = game.smallCapsuleUrl,
            modifier = Modifier
                .width(96.dp)
                .height(45.dp)
                .clip(RoundedCornerShape(8.dp)),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = game.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (game.subtitle.isNotBlank()) {
                Text(
                    text = game.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (game.isDownloading) {
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { game.downloadProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                )
            }
        }

        if (game.isDownloading) {
            StatusBadge(text = game.statusLine, solid = true)
        } else if (game.installed) {
            StatusBadge(text = stringResource(R.string.steam_library_installed_badge))
        }
    }
}

/** Capsule grid: vertical 2:3 poster, compact title below. */
@Composable
private fun SteamGameCapsuleCard(game: SteamLibraryGameUi, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (pressed) 0.96f else 1f, label = "capsulePress")
    Column(
        modifier = Modifier.width(122.dp).scale(scale).clip(RoundedCornerShape(12.dp))
            .background(glassCardFill())
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            SteamCoverArt(game = game, cacheKey = "steam-capsule", targetSize = 300 to 450, initialsStyle = MaterialTheme.typography.titleLarge, imageUrl = game.libraryCapsuleUrl, modifier = Modifier.fillMaxSize())
            if (game.isDownloading) LinearProgressIndicator(progress = { game.downloadProgress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
            if (game.installed && !game.isDownloading) Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
        Text(text = game.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp))
    }
}

@Composable
private fun SteamGameCarouselCard(game: SteamLibraryGameUi, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(200.dp).clip(RoundedCornerShape(14.dp))
            .background(glassCardFill())
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(460f / 215f)) {
            SteamCoverArt(game = game, cacheKey = "steam-carousel", targetSize = 460 to 215, initialsStyle = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxSize())
            if (game.isDownloading) LinearProgressIndicator(progress = { game.downloadProgress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp))
        }
        Text(text = game.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
        if (game.subtitle.isNotBlank()) Text(text = game.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp))
    }
}

/** Compact row: the densest view — thumbnail, title, and a state dot. */
@Composable
private fun SteamGameCompactCard(game: SteamLibraryGameUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SteamCoverArt(
            game = game,
            cacheKey = "steam-compact",
            targetSize = 138 to 64,
            initialsStyle = MaterialTheme.typography.labelSmall,
            imageUrl = game.smallCapsuleUrl,
            modifier = Modifier
                .width(58.dp)
                .height(27.dp)
                .clip(RoundedCornerShape(5.dp)),
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = game.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (game.subtitle.isNotBlank()) {
                Text(
                    text = game.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when {
            game.isDownloading -> Text(
                text = "${(game.downloadProgress * 100f).toInt()}%",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            game.installed -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private fun SteamLibraryGameUi.matchesContentFilter(filter: SteamContentFilter): Boolean {
    return when (filter) {
        SteamContentFilter.ALL -> true
        SteamContentFilter.GAMES -> appType == AppType.game || appType == AppType.demo || appType == AppType.invalid
        SteamContentFilter.DLC -> appType == AppType.dlc
        SteamContentFilter.APPLICATIONS -> appType == AppType.application
        SteamContentFilter.TOOLS -> appType == AppType.tool
    }
}

@Composable
private fun SteamContentFilter.label(): String {
    return when (this) {
        SteamContentFilter.ALL -> stringResource(R.string.steam_library_content_filter_all)
        SteamContentFilter.GAMES -> stringResource(R.string.steam_library_content_filter_games)
        SteamContentFilter.DLC -> stringResource(R.string.steam_library_content_filter_dlc)
        SteamContentFilter.APPLICATIONS -> stringResource(R.string.steam_library_content_filter_applications)
        SteamContentFilter.TOOLS -> stringResource(R.string.steam_library_content_filter_tools)
    }
}

@Composable
private fun SteamGameOverlay(
    game: SteamGameDetailUi?,
    activeTab: SteamTab,
    containers: List<SteamContainerOption>,
    selectedContainerId: Int?,
    onClose: () -> Unit,
    onSelectContainer: (Int) -> Unit,
    onPickInstallFolder: (Int) -> Unit,
    onDownload: (Int) -> Unit,
    onPauseDownload: (Int) -> Unit,
    onResumeDownload: (Int) -> Unit,
    onCancelDownload: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onOpenSettings: (Int, Int?) -> Unit,
    onCreateShortcut: (Int, Int?) -> Unit,
    onExportSaves: (Int, String) -> Unit,
    onImportSaves: (Int) -> Unit,
    onUploadCloudSaves: (Int) -> Unit,
    onDownloadCloudSaves: (Int) -> Unit,
    isOfflineMode: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val compactLayout = activeTab == SteamTab.DOWNLOADS
    var isLaunchingGame by remember(game?.appId) { mutableStateOf(false) }
    var showContentManager by remember(game?.appId) { mutableStateOf(false) }
    var showWorkshopManager by remember(game?.appId) { mutableStateOf(false) }
    var showBranchPicker by remember(game?.appId) { mutableStateOf(false) }
    var showAchievements by remember(game?.appId) { mutableStateOf(false) }
    var workshopRefreshToken by remember(game?.appId) { mutableStateOf(0) }
    val appInfo = remember(game?.appId) { game?.let { SteamService.getAppInfoOf(it.appId) } }
    var selectedBranch by remember(game?.appId) {
        mutableStateOf(game?.let { PrefManager.getSteamSelectedBranch(it.appId) } ?: "public")
    }
    var playtimeText by remember(game?.appId) { mutableStateOf("--") }
    var lastPlayedText by remember(game?.appId) { mutableStateOf("--") }
    var cloudDifference by remember(game?.appId) { mutableStateOf<Boolean?>(null) }
    var updatePending by remember(game?.appId, selectedBranch) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(isLaunchingGame) {
        if (isLaunchingGame) {
            delay(12000)
            isLaunchingGame = false
        }
    }

    LaunchedEffect(game?.appId, selectedBranch) {
        val currentGame = game ?: return@LaunchedEffect
        val canReachSteam = !isOfflineMode && SteamService.isConnected && SteamService.isLoggedIn
        val steamId64 = SteamService.userSteamId?.convertToUInt64()
            ?: PrefManager.steamUserSteamId64.takeIf { it != 0L }

        if (canReachSteam && steamId64 != null && steamId64 != 0L) {
            val ownedGame = withContext(Dispatchers.IO) {
                SteamService.getOwnedGames(steamId64).firstOrNull { it.appId == currentGame.appId }
            }
            playtimeText = ownedGame?.let { formatPlaytimeHours(it.playtimeForever) } ?: "--"
            lastPlayedText = ownedGame?.rtimeLastPlayed
                ?.takeIf { it > 0 }
                ?.let(::formatSteamTimestamp)
                ?: formatInstallTimestamp(currentGame.installPath)
        } else {
            playtimeText = "--"
            lastPlayedText = formatInstallTimestamp(currentGame.installPath)
        }

        if (currentGame.installed && canReachSteam) {
            cloudDifference = withContext(Dispatchers.IO) { SteamService.cloudSavesDiffer(currentGame.appId) }
            updatePending = withContext(Dispatchers.IO) {
                SteamService.isUpdatePending(currentGame.appId, selectedBranch)
            }
        } else {
            cloudDifference = null
            updatePending = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(
                enabled = !isLaunchingGame && !showContentManager && !showWorkshopManager && !showBranchPicker,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (game == null) {
            CircularProgressIndicator()
            return@Box
        }

        var savesMenuExpanded by remember { mutableStateOf(false) }
        var cloudMenuExpanded by remember { mutableStateOf(false) }
        val showInstalledActions = game.installed
        val showFolderPicker = activeTab == SteamTab.STEAM && !game.installed && !game.isDownloading
        val isDownloadSession = game.isDownloading && !showInstalledActions
        val isPausedDownload = game.downloadPhase == DownloadPhase.PAUSED
        val enabledWorkshopIds = remember(game.appId, workshopRefreshToken) {
            PrefManager.getSteamWorkshopEnabledItemIds(game.appId)
        }
        val availableBranches = remember(appInfo?.branches, selectedBranch) {
            buildList {
                add("public")
                appInfo?.branches
                    ?.filterValues { !it.pwdRequired }
                    ?.keys
                    ?.sorted()
                    ?.forEach(::add)
                if (selectedBranch.isNotBlank()) {
                    add(selectedBranch)
                }
            }.distinct()
        }
        val controllerSupportLabel = remember(appInfo?.controllerSupport) {
            when (appInfo?.controllerSupport ?: ControllerSupport.none) {
                ControllerSupport.full -> context.getString(R.string.steam_library_controller_support_full)
                ControllerSupport.partial -> context.getString(R.string.steam_library_controller_support_partial)
                ControllerSupport.none -> context.getString(R.string.steam_library_controller_support_none)
            }
        }
        val steamInputAvailable = remember(game.appId) {
            runCatching { SteamService.resolveSteamControllerVdfText(game.appId) != null }.getOrDefault(false)
        }
        val canPauseDownload = game.downloadPhase == DownloadPhase.PREPARING ||
            game.downloadPhase == DownloadPhase.VERIFYING ||
            game.downloadPhase == DownloadPhase.PATCHING ||
            game.downloadPhase == DownloadPhase.DOWNLOADING ||
            game.downloadPhase == DownloadPhase.QUEUED

        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Full-screen hero image
                AsyncImage(
                    model = game.heroUrl.ifBlank { game.capsuleUrl },
                    contentDescription = game.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Full-screen gradient overlay (transparent top → dark bottom)
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.87f),
                            ),
                        ),
                    ),
                )
                // Close button
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)) {
                    RoundActionButton(Icons.Filled.Close, onClose, size = 40)
                }
                // Game info at top-left — hero-баннер в стиле десктопа:
                // логотип + имя + разработчик + статус-пилюли поверх арта
                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (game.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = game.logoUrl,
                            contentDescription = game.name,
                            modifier = Modifier
                                .height(44.dp)
                                .fillMaxWidth(0.5f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(
                        game.name,
                        color = Color.White,
                        style = if (compactLayout) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (game.subtitle.isNotBlank()) {
                        Text(
                            game.subtitle,
                            color = Color.White.copy(alpha = 0.75f),
                            style = if (compactLayout) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Статус-пилюли: установлен / загрузка / обновление
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (showInstalledActions) {
                            StatusBadge(text = game.statusLine, solid = true)
                        } else if (isDownloadSession) {
                            StatusBadge(text = game.statusLine, solid = true)
                        } else {
                            StatusBadge(text = stringResource(R.string.steam_library_tab_steam), solid = true)
                        }
                        if (updatePending == true) {
                            StatusBadge(text = stringResource(R.string.steam_library_update_pending_badge))
                        }
                    }
                    // Наиграно / последний запуск прямо под именем
                    if (showInstalledActions) {
                        Text(
                            "$playtimeText • $lastPlayedText",
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Content panel at bottom — compact & transparent
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (compactLayout) 6.dp else 8.dp, vertical = if (compactLayout) 4.dp else 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                        StatusBadge(text = stringResource(R.string.steam_library_tab_steam), solid = true)
                        if (updatePending == true) {
                            StatusBadge(text = stringResource(R.string.steam_library_update_pending_badge))
                        }
                        if (isDownloadSession) {
                            DownloadProgressCard(game = game, compactLayout = compactLayout)
                            if (!game.currentFileName.isNullOrBlank()) {
                                DetailInfoCard(
                                    title = stringResource(R.string.steam_library_current_file),
                                    value = game.currentFileName,
                                )
                            }
                            DetailInfoCard(
                                title = stringResource(R.string.steam_library_install_path),
                                value = game.installPath,
                            )
                        } else if (showInstalledActions) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CompactStatCard(
                                    icon = Icons.Filled.Storage,
                                    label = stringResource(R.string.steam_library_size),
                                    value = buildString {
                                        append("${formatBinarySize(game.downloadSizeBytes)} / ${formatBinarySize(game.installSizeBytes)}")
                                        game.sizeOnDiskBytes?.takeIf { it > 0L }?.let {
                                            append(" • ${formatBinarySize(it)}")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.Refresh,
                                    label = stringResource(R.string.steam_library_playtime),
                                    value = playtimeText,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.Check,
                                    label = stringResource(R.string.steam_library_last_played),
                                    value = lastPlayedText,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CompactStatCard(
                                    icon = Icons.Filled.CloudUpload,
                                    label = stringResource(R.string.steam_library_cloud_status),
                                    value = when (cloudDifference) {
                                        true -> stringResource(R.string.steam_library_cloud_changed)
                                        false -> stringResource(R.string.steam_library_cloud_in_sync)
                                        null -> stringResource(R.string.steam_library_cloud_unknown)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.Settings,
                                    label = stringResource(R.string.steam_library_controller_support),
                                    value = controllerSupportLabel,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.Home,
                                    label = stringResource(R.string.steam_library_workshop_title),
                                    value = if (enabledWorkshopIds.isEmpty()) {
                                        stringResource(R.string.steam_library_workshop_disabled)
                                    } else {
                                        stringResource(
                                            R.string.steam_library_workshop_enabled_count,
                                            enabledWorkshopIds.size,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            // Не установлена: размер + место + путь + прогресс
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CompactStatCard(
                                    icon = Icons.Filled.Storage,
                                    label = stringResource(R.string.steam_library_size),
                                    value = "${formatBinarySize(game.downloadSizeBytes)} / ${formatBinarySize(game.installSizeBytes)}",
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.FolderOpen,
                                    label = stringResource(R.string.steam_library_available_space),
                                    value = formatBinarySize(game.availableBytes),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            DetailInfoCard(
                                title = stringResource(R.string.steam_library_install_path),
                                value = game.installPath,
                            )
                            if (game.isDownloading || game.downloadProgress > 0f) {
                                DownloadProgressCard(game = game, compactLayout = compactLayout)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (!isDownloadSession) {
                            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = glassCardFill())) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(if (compactLayout) 6.dp else 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            ContainerDropdown(
                                                containers = containers,
                                                selectedContainerId = selectedContainerId,
                                                onSelectContainer = onSelectContainer,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        if (showFolderPicker) {
                                            GlassPill(
                                                onClick = { onPickInstallFolder(game.appId) },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.steam_library_choose_folder), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = glassCardFill())) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.steam_library_download_status_title),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        StatusBadge(text = game.statusLine)
                                    }
                                    if (!game.currentFileName.isNullOrBlank()) {
                                        Text(
                                            text = "${stringResource(R.string.steam_library_download_file_label)}: ${game.currentFileName}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        if (showInstalledActions) {
                            GradientButton(
                                text = stringResource(R.string.steam_library_play),
                                icon = Icons.Filled.PlayArrow,
                                onClick = {
                                    isLaunchingGame = true
                                    onPlay(game.appId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.Settings,
                                    text = stringResource(R.string.steam_library_settings),
                                    onClick = { onOpenSettings(game.appId, selectedContainerId) },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Home,
                                    text = stringResource(R.string.steam_library_shortcut),
                                    onClick = { onCreateShortcut(game.appId, selectedContainerId) },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.Refresh,
                                    text = stringResource(R.string.steam_library_update),
                                    onClick = {
                                        scope.launch {
                                            val started = withContext(Dispatchers.IO) {
                                                SteamService.downloadApp(
                                                    appId = game.appId,
                                                    downloadableDepots = SteamService.getDownloadableDepots(game.appId),
                                                    userSelectedDlcAppIds = SteamService.getInstalledDlcDepotsOf(game.appId).orEmpty(),
                                                    branch = selectedBranch,
                                                    includeInstalledDepots = true,
                                                    enableVerify = false,
                                                )
                                            }
                                            Toast.makeText(
                                                context,
                                                if (started != null) {
                                                    context.getString(R.string.steam_library_update_started)
                                                } else {
                                                    context.getString(R.string.steam_library_update_failed)
                                                },
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Check,
                                    text = stringResource(R.string.steam_library_verify_files),
                                    onClick = {
                                        scope.launch {
                                            val started = withContext(Dispatchers.IO) {
                                                SteamService.downloadApp(
                                                    appId = game.appId,
                                                    downloadableDepots = SteamService.getDownloadableDepots(game.appId),
                                                    userSelectedDlcAppIds = SteamService.getInstalledDlcDepotsOf(game.appId).orEmpty(),
                                                    branch = selectedBranch,
                                                    includeInstalledDepots = true,
                                                    enableVerify = true,
                                                )
                                            }
                                            Toast.makeText(
                                                context,
                                                if (started != null) {
                                                    context.getString(R.string.steam_library_verify_started)
                                                } else {
                                                    context.getString(R.string.steam_library_verify_failed)
                                                },
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.Storage,
                                    text = stringResource(R.string.steam_library_content_action),
                                    onClick = { showContentManager = true },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.FolderOpen,
                                    text = stringResource(R.string.steam_library_workshop_action),
                                    onClick = { showWorkshopManager = true },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Star,
                                    text = stringResource(R.string.steam_library_achievements),
                                    onClick = { showAchievements = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.Tune,
                                    text = stringResource(R.string.steam_library_change_branch),
                                    onClick = { showBranchPicker = true },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamMenuActionTile(
                                    icon = Icons.Filled.Save,
                                    text = stringResource(R.string.steam_library_saves),
                                    expanded = savesMenuExpanded,
                                    onExpandedChange = { savesMenuExpanded = it },
                                    actions = listOf(
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_export_saves),
                                            icon = Icons.Filled.Save,
                                            onClick = { onExportSaves(game.appId, game.name) },
                                        ),
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_import_saves),
                                            icon = Icons.Filled.FolderOpen,
                                            onClick = { onImportSaves(game.appId) },
                                        ),
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                SteamMenuActionTile(
                                    icon = Icons.Filled.CloudDownload,
                                    text = stringResource(R.string.steam_library_cloud_saves),
                                    expanded = cloudMenuExpanded,
                                    onExpandedChange = { cloudMenuExpanded = it },
                                    actions = listOf(
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_cloud_upload),
                                            icon = Icons.Filled.CloudUpload,
                                            onClick = { onUploadCloudSaves(game.appId) },
                                        ),
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_cloud_download),
                                            icon = Icons.Filled.CloudDownload,
                                            onClick = { onDownloadCloudSaves(game.appId) },
                                        ),
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_force_cloud_sync),
                                            icon = Icons.Filled.Refresh,
                                            onClick = {
                                                scope.launch {
                                                    val steamId = SteamService.userSteamId
                                                    if (steamId == null) {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.steam_library_login_required),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    } else {
                                                        val result = withContext(Dispatchers.IO) {
                                                            SteamGameActions.forceCloudSync(context, game.appId)
                                                        }
                                                        val message = when (result) {
                                                            SyncResult.Success -> context.getString(R.string.steam_library_cloud_force_success)
                                                            SyncResult.UpToDate -> context.getString(R.string.steam_library_cloud_force_up_to_date)
                                                            else -> context.getString(R.string.steam_library_cloud_force_failed)
                                                        }
                                                        if (result == SyncResult.Success || result == SyncResult.UpToDate) {
                                                            cloudDifference = false
                                                        }
                                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                        ),
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_browse_online_saves),
                                            icon = Icons.Filled.Search,
                                            onClick = {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://store.steampowered.com/account/remotestorageapp/?appid=${game.appId}"),
                                                    ),
                                                )
                                            },
                                        ),
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.PhotoLibrary,
                                    text = stringResource(R.string.steam_library_screenshots),
                                    onClick = {
                                        val steamId64 = SteamService.userSteamId?.convertToUInt64()
                                            ?: PrefManager.steamUserSteamId64
                                        if (steamId64 != 0L) {
                                            val url = "https://steamcommunity.com/profiles/$steamId64/screenshots/?appid=${game.appId}"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Delete,
                                    text = stringResource(R.string.steam_library_uninstall),
                                    onClick = { onDelete(game.appId) },
                                    modifier = Modifier.weight(1f),
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            if (game.isDownloading) {
                                GradientButton(
                                    text = if (isPausedDownload) {
                                        stringResource(R.string.steam_library_resume_download)
                                    } else {
                                        stringResource(R.string.steam_library_pause_download)
                                    },
                                    icon = if (isPausedDownload) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    onClick = {
                                        if (canPauseDownload) {
                                            onPauseDownload(game.appId)
                                        } else {
                                            onResumeDownload(game.appId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Delete,
                                    text = stringResource(R.string.steam_library_cancel_download),
                                    onClick = { onCancelDownload(game.appId) },
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                GradientButton(
                                    text = stringResource(R.string.steam_library_download),
                                    icon = Icons.Filled.CloudDownload,
                                    onClick = { onDownload(game.appId) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Storage,
                                    text = stringResource(R.string.steam_library_content_action),
                                    onClick = { showContentManager = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showContentManager) {
            SteamContentManagerSheet(
                game = game,
                selectedBranch = selectedBranch,
                onDismiss = { showContentManager = false },
                onInstall = { selectedDlcIds ->
                    showContentManager = false
                    scope.launch {
                        val started = withContext(Dispatchers.IO) {
                            SteamService.downloadApp(
                                appId = game.appId,
                                downloadableDepots = SteamService.getDownloadableDepots(game.appId),
                                userSelectedDlcAppIds = selectedDlcIds,
                                branch = selectedBranch,
                                includeInstalledDepots = false,
                                enableVerify = false,
                            )
                        }
                        Toast.makeText(
                            context,
                            if (started != null) {
                                context.getString(R.string.steam_library_content_install_started)
                            } else {
                                context.getString(R.string.steam_library_content_install_failed)
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }

        if (showWorkshopManager) {
            SteamWorkshopManagerSheet(
                game = game,
                currentEnabledIds = enabledWorkshopIds,
                onDismiss = { showWorkshopManager = false },
                onSave = { enabledIds ->
                    showWorkshopManager = false
                    PrefManager.setSteamWorkshopEnabledItemIds(game.appId, enabledIds)
                    workshopRefreshToken++
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            WorkshopManager.syncEnabledItems(
                                context = context,
                                appId = game.appId,
                                containerId = selectedContainerId,
                                enabledIds = enabledIds,
                            )
                        }
                        val message = when {
                            result.failedCount > 0 -> context.getString(R.string.steam_library_workshop_sync_failed)
                            enabledIds.isEmpty() -> context.getString(R.string.steam_library_workshop_cleared)
                            else -> context.getString(
                                R.string.steam_library_workshop_sync_success,
                                result.syncedCount,
                                result.skippedCount,
                                result.removedCount,
                            )
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        if (showBranchPicker) {
            SteamBranchPickerSheet(
                gameName = game.name,
                availableBranches = availableBranches,
                currentBranch = selectedBranch,
                onDismiss = { showBranchPicker = false },
                onConfirm = { branch ->
                    selectedBranch = branch
                    PrefManager.setSteamSelectedBranch(game.appId, branch)
                    showBranchPicker = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.steam_library_branch_selected, branch),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }

        if (showAchievements && game != null) {
            AchievementsSheet(appId = game.appId, onDismiss = { showAchievements = false })
        }

        if (game != null && isLaunchingGame) {
            SteamLaunchOverlay(game = game)
        }

    }
}
}

@Composable
private fun DownloadProgressCard(game: SteamGameDetailUi, compactLayout: Boolean) {
    // The percentage is the headline here; the byte counts and rate sit under it
    // as supporting figures rather than competing label/value pairs.
    val percent = (game.downloadProgress.coerceIn(0f, 1f) * 100f)
    val animatedProgress by animateFloatAsState(
        targetValue = game.downloadProgress.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "downloadProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(glassCardFill())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "%.0f".format(percent),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            StatusBadge(text = game.statusLine, solid = true)
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DownloadMetric(
                label = stringResource(R.string.steam_library_downloaded),
                value = buildProgressText(game.downloadedBytes, game.totalBytes),
                modifier = Modifier.weight(1.4f),
            )
            DownloadMetric(
                label = stringResource(R.string.steam_library_speed),
                value = formatSpeed(game.speedBytesPerSec),
                modifier = Modifier.weight(1f),
            )
            DownloadMetric(
                label = stringResource(R.string.steam_library_eta),
                value = formatEta(game.etaMs),
                modifier = Modifier.weight(1f),
            )
        }

        if (!game.currentFileName.isNullOrBlank()) {
            Text(
                text = game.currentFileName,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One figure in the download readout: caption above, value below. */
@Composable
private fun DownloadMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SteamLaunchOverlay(game: SteamGameDetailUi) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.62f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp),
                ) {
                    AsyncImage(
                        model = game.heroUrl.ifBlank { game.capsuleUrl },
                        contentDescription = game.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.18f),
                                        Color.Black.copy(alpha = 0.34f),
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ),
                            ),
                    )
                    StatusBadge(
                        text = stringResource(R.string.steam_library_launching_title),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = game.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (game.subtitle.isNotBlank()) {
                            Text(
                                text = game.subtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.steam_library_launching_status),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.4.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.steam_library_launching_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerDropdown(
    containers: List<SteamContainerOption>,
    selectedContainerId: Int?,
    onSelectContainer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedContainer = containers.firstOrNull { it.id == selectedContainerId }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(glassCardFill())
                .clickable { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Storage,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = selectedContainer?.name ?: stringResource(R.string.steam_library_select_container),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            containers.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    trailingIcon = {
                        if (option.id == selectedContainerId) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectContainer(option.id)
                    },
                )
            }
        }
    }
}
/**
 * A pill carrying one piece of state. `solid` marks the active/primary case
 * (downloading); the quiet variant is for settled states such as "installed".
 */
@Composable
private fun StatusBadge(text: String, modifier: Modifier = Modifier, solid: Boolean = false) {
    val container = if (solid) {
        MaterialTheme.colorScheme.primary
    } else {
        glassCardFill()
    }
    val content = if (solid) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(content.copy(alpha = if (solid) 0.9f else 0.7f)),
        )
        Text(
            text = text,
            color = content,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun DetailInfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(glassCardFill())
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailMiniLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * One statistic: the icon sits in its own tinted chip so a row of these scans
 * as a set of readings rather than a wall of small text.
 */
@Composable
private fun CompactStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(glassCardFill())
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The primary call to action — Play / Download. */
@Composable
private fun GradientButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ctaPress",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                    ),
                ),
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DeleteGameButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.steam_library_delete_game),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
    }
}

private data class SteamMenuAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun SteamActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = glassCardFill(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = if (pressed) containerColor.copy(alpha = 0.9f) else containerColor,
        label = "tilePress",
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SteamMenuActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<SteamMenuAction>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SteamActionTile(
            icon = icon,
            text = text,
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        action.onClick()
                    },
                )
            }
        }
    }
}

private fun formatBinarySize(bytes: Long): String {
    return if (bytes > 0L) StorageUtils.formatBinarySize(bytes) else "--"
}

private fun buildProgressText(downloadedBytes: Long, totalBytes: Long): String {
    return if (totalBytes > 0L) {
        "${StorageUtils.formatBinarySize(downloadedBytes)} / ${StorageUtils.formatBinarySize(totalBytes)}"
    } else if (downloadedBytes > 0L) {
        StorageUtils.formatBinarySize(downloadedBytes)
    } else {
        "--"
    }
}

private fun formatSpeed(bytesPerSecond: Long?): String {
    return if (bytesPerSecond != null && bytesPerSecond > 0L) {
        "${StorageUtils.formatBinarySize(bytesPerSecond, 1)}/s"
    } else {
        "--"
    }
}

private fun formatEta(etaMs: Long?): String {
    if (etaMs == null || etaMs <= 0L) return "--"
    return DateUtils.formatElapsedTime(etaMs / 1000L)
}

private fun formatReleaseDate(releaseDateSeconds: Long): String {
    if (releaseDateSeconds <= 0L) return "--"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(releaseDateSeconds * 1000L))
}

private fun formatPlaytimeHours(minutes: Int): String {
    if (minutes <= 0) return "--"
    if (minutes < 60) return "${minutes}m"

    val hours = minutes / 60
    if (hours < 24) return "${hours}h"

    val days = hours / 24
    val remainingHours = hours % 24
    return if (remainingHours == 0) {
        "${days}d"
    } else {
        "${days}d ${remainingHours}h"
    }
}

private fun formatSteamTimestamp(timestampSeconds: Int): String {
    if (timestampSeconds <= 0) return "--"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestampSeconds * 1000L))
}

private fun formatInstallTimestamp(path: String): String {
    if (path.isBlank()) return "--"
    val target = File(path)
    val lastModified = when {
        target.isFile -> target.lastModified()
        target.isDirectory -> target.lastModified()
        else -> 0L
    }
    if (lastModified <= 0L) return "--"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(lastModified))
}
