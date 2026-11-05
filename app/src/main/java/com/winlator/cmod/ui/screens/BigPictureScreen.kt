package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassPill
import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.graphics.BitmapFactory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.winlator.cmod.R
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.core.ShortcutCoverFetcher
import com.winlator.cmod.win32.PEParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val GAME_VERSION_CACHE = ConcurrentHashMap<String, String>()

private enum class BpZone { RAIL, PLAY, CAROUSEL }

private val CARD_WIDTH = 84.dp
private val CARD_HEIGHT = 126.dp

/**
 * Big Picture — телевизионный лаунчер в духе Steam Big Picture:
 * размытый фон из обложки, rail-кнопки, hero с инфо выбранной игры,
 * карусель обложек снизу. Навигация controller-first (D-pad).
 */
@Composable
fun BigPictureScreen(
    shortcuts: List<Shortcut>,
    onRun: (Shortcut) -> Unit,
    onOpenShortcutSettings: (Shortcut) -> Unit,
    onOpenContainerSettings: (Shortcut) -> Unit,
    onOpenFileManager: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT

    val manager = remember { ContainerManager(context) }
    var allShortcuts by remember { mutableStateOf(manager.loadShortcuts()) }
    var selectedIndex by remember { mutableStateOf(0) }
    var zone by remember { mutableStateOf(BpZone.CAROUSEL) }
    var railIndex by remember { mutableStateOf(0) }
    var playIndex by remember { mutableStateOf(0) }
    var showGameOptions by remember { mutableStateOf(false) }

    val coverCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val inFlight = remember { HashSet<String>() }

    val rootFocus = remember { FocusRequester() }
    val grabFocus: () -> Unit = { runCatching { rootFocus.requestFocus() } }

    // Версия игры из PE (для чипа), с кэшем
    var gameVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(allShortcuts.getOrNull(selectedIndex)) {
        gameVersion = null
        val s = allShortcuts.getOrNull(selectedIndex) ?: return@LaunchedEffect
        gameVersion = GAME_VERSION_CACHE[s.name] ?: kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val exe = s.resolveExeFile()
                val fi = if (exe != null) PEParser.getFileVersionInfo(exe) else null
                (fi?.FileVersion ?: fi?.ProductVersion)?.takeIf { it.isNotBlank() }?.also { GAME_VERSION_CACHE[s.name] = it }
            } catch (_: Throwable) { null }
        }
    }

    // Загрузка обложек: кэш-файл → иначе скачивание через ShortcutCoverFetcher
    val ensureCover: (Shortcut) -> Unit = { s ->
        if (!coverCache.containsKey(s.name) && inFlight.add(s.name)) {
            scope.launch(Dispatchers.IO) {
                val file = ShortcutCoverFetcher.loadCoverArt(context, s, landscape = false)
                val img = file?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
                if (img != null) {
                    coverCache[s.name] = img
                    inFlight.remove(s.name)
                } else {
                    ShortcutCoverFetcher.fetchCoverArt(context, s, landscape = false) {
                        val f2 = ShortcutCoverFetcher.loadCoverArt(context, s, landscape = false)
                        val img2 = f2?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
                        scope.launch {
                            if (img2 != null) coverCache[s.name] = img2
                            inFlight.remove(s.name)
                        }
                    }
                }
            }
        }
    }

    // Обновление ярлыков при возврате из игры
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allShortcuts = manager.loadShortcuts()
                if (selectedIndex > allShortcuts.lastIndex) {
                    selectedIndex = allShortcuts.lastIndex.coerceAtLeast(0)
                }
                grabFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { grabFocus() }
    LaunchedEffect(showGameOptions) { if (!showGameOptions) grabFocus() }

    // Подгрузка обложки выбранной игры
    LaunchedEffect(selectedIndex, allShortcuts) {
        allShortcuts.getOrNull(selectedIndex)?.let { ensureCover(it) }
    }

    // Центрирование карусели на выбранной карточке
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, allShortcuts.size) {
        if (allShortcuts.isNotEmpty()) {
            val viewport = listState.layoutInfo.viewportSize.width
            val itemPx = with(density) { CARD_WIDTH.roundToPx() }
            val offset = if (viewport > 0) -(viewport / 2 - itemPx / 2) else 0
            runCatching { listState.animateScrollToItem(selectedIndex.coerceIn(0, allShortcuts.lastIndex), offset) }
        }
    }

    val selected = allShortcuts.getOrNull(selectedIndex)
    val heroCover = selected?.let { coverCache[it.name] }

    val onLaunch: () -> Unit = {
        selected?.let { onRun(it) }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (showGameOptions) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.ButtonB, Key.Back -> { showGameOptions = false; true }
                        else -> false
                    }
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        when (zone) {
                            BpZone.CAROUSEL -> if (selectedIndex > 0) selectedIndex--
                            BpZone.RAIL -> if (railIndex > 0) railIndex--
                            BpZone.PLAY -> playIndex = 0
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        when (zone) {
                            BpZone.CAROUSEL -> if (selectedIndex < allShortcuts.lastIndex) selectedIndex++
                            BpZone.RAIL -> if (railIndex < 2) railIndex++
                            BpZone.PLAY -> playIndex = 1
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        zone = when (zone) {
                            BpZone.CAROUSEL -> { playIndex = 0; BpZone.PLAY }
                            BpZone.PLAY -> BpZone.RAIL
                            BpZone.RAIL -> BpZone.RAIL
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        zone = when (zone) {
                            BpZone.RAIL -> { playIndex = 0; BpZone.PLAY }
                            BpZone.PLAY -> BpZone.CAROUSEL
                            BpZone.CAROUSEL -> BpZone.CAROUSEL
                        }
                        true
                    }
                    Key.ButtonA, Key.Enter, Key.DirectionCenter -> {
                        when (zone) {
                            BpZone.CAROUSEL -> onLaunch()
                            BpZone.PLAY -> if (playIndex == 0) onLaunch()
                                else if (selected != null) showGameOptions = true
                            BpZone.RAIL -> when (railIndex) {
                                0 -> selected?.let { onOpenShortcutSettings(it) }
                                1 -> onOpenFileManager()
                                2 -> onBack()
                            }
                        }
                        true
                    }
                    Key.ButtonY -> {
                        if (selected != null && zone != BpZone.RAIL) showGameOptions = true
                        true
                    }
                    Key.ButtonB, Key.Back -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // ── Размытый фон из обложки выбранной игры ──
        Crossfade(
            targetState = (selected?.name ?: "") to heroCover,
            animationSpec = tween(350),
            label = "bp-hero-bg",
        ) { (_, cover) ->
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF141A24), Color(0xFF0B0D12)))),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)))),
        )

        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            // ── Rail (верх, справа) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RailButton(
                    icon = Icons.Filled.Edit,
                    label = stringResource(R.string.shortcut_settings),
                    focused = zone == BpZone.RAIL && railIndex == 0,
                    onClick = { zone = BpZone.RAIL; railIndex = 0; selected?.let(onOpenShortcutSettings) },
                )
                Spacer(Modifier.width(12.dp))
                RailButton(
                    icon = Icons.Filled.FolderOpen,
                    label = stringResource(R.string.file_manager),
                    focused = zone == BpZone.RAIL && railIndex == 1,
                    onClick = { zone = BpZone.RAIL; railIndex = 1; onOpenFileManager() },
                )
                Spacer(Modifier.width(12.dp))
                RailButton(
                    icon = Icons.Filled.PowerSettingsNew,
                    label = stringResource(R.string.big_picture_power),
                    focused = zone == BpZone.RAIL && railIndex == 2,
                    onClick = { zone = BpZone.RAIL; railIndex = 2; onBack() },
                )
            }

            if (allShortcuts.isEmpty()) {
                // Пустое состояние
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.Tv,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.big_picture_empty),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    GlassSolidPill(
                        onClick = onBack,
                    ) { Text(stringResource(R.string.back)) }
                }
            } else {
                // ── Hero ──
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val coverH = maxHeight.coerceAtMost(if (isPortrait) 150.dp else 280.dp)
                    val coverW = coverH * 2f / 3f
                    val sel = selected
                    val playFocused = zone == BpZone.PLAY && playIndex == 0
                    val optionsFocused = zone == BpZone.PLAY && playIndex == 1
                    val onOptions: () -> Unit = {
                        zone = BpZone.PLAY; playIndex = 1
                        if (selected != null) showGameOptions = true
                    }
                    val onPlay: () -> Unit = { zone = BpZone.PLAY; playIndex = 0; onLaunch() }

                    if (isPortrait) {
                        // Портрет: карточка + инфо/чипы сверху, кнопки на всю ширину снизу
                        Column(Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(coverH),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CoverCard(
                                    cover = heroCover,
                                    modifier = Modifier.width(coverW).fillMaxHeight(),
                                    selected = false,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    Column(modifier = Modifier.weight(1f).clipToBounds()) {
                                        if (sel != null) {
                                            Text(
                                                text = sel.name,
                                                color = Color.White,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(Modifier.height(3.dp))
                                            val stats = playtimeStats(sel)
                                            Text(
                                                text = stringResource(R.string.big_picture_played, stats.first, stats.second),
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    if (sel != null) {
                                        FlowRowChips(chips = buildChips(sel, gameVersion))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                BpPlayButton(playFocused = playFocused, onPlay = onPlay, modifier = Modifier.weight(1f))
                                BpOptionsButton(optionsFocused = optionsFocused, onOptions = onOptions, modifier = Modifier.weight(1f))
                            }
                        }
                    } else {
                        // Ландшафт: обложка + текст/чипы, кнопки внизу столбца
                        Row(
                            modifier = Modifier.fillMaxWidth().height(coverH),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CoverCard(
                                cover = heroCover,
                                modifier = Modifier.width(coverW).fillMaxHeight(),
                                selected = false,
                            )
                            Spacer(Modifier.width(24.dp))
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Column(modifier = Modifier.weight(1f).clipToBounds()) {
                                    if (sel != null) {
                                        Text(
                                            text = sel.name,
                                            color = Color.White,
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        val stats = playtimeStats(sel)
                                        Text(
                                            text = stringResource(R.string.big_picture_played, stats.first, stats.second),
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                if (sel != null) {
                                    FlowRowChips(chips = buildChips(sel, gameVersion))
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BpPlayButton(playFocused = playFocused, onPlay = onPlay)
                                    Spacer(Modifier.width(12.dp))
                                    BpOptionsButton(optionsFocused = optionsFocused, onOptions = onOptions)
                                }
                            }
                        }
                    }
                }

                // ── Карусель обложек ──
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(allShortcuts) { index, s ->
                        val isSel = index == selectedIndex
                        val scale by animateFloatAsState(if (isSel) 1.12f else 1f, label = "bp-card-scale")
                        LaunchedEffect(s.name) { ensureCover(s) }
                        CoverCard(
                            cover = coverCache[s.name],
                            modifier = Modifier
                                .width(CARD_WIDTH)
                                .height(CARD_HEIGHT)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clickable { zone = BpZone.CAROUSEL; selectedIndex = index },
                            selected = isSel,
                        )
                    }
                }
            }
        }
    }

    // ── Sheet: настройки игры ──
    if (showGameOptions) {
        selected?.let { s ->
            GameOptionsSheet(
                shortcut = s,
                onDismiss = { showGameOptions = false },
                onEditShortcut = { showGameOptions = false; onOpenShortcutSettings(s) },
                onContainerSettings = { showGameOptions = false; onOpenContainerSettings(s) },
                onChangeCover = { bmp ->
                    s.saveCustomCoverArt(bmp)
                    coverCache[s.name] = bmp.asImageBitmap()
                },
                onRemoveCover = {
                    s.removeCustomCoverArt()
                    coverCache.remove(s.name)
                    inFlight.remove(s.name)
                },
            )
        }
    }
}

// ── Блоки ─────────────────────────────────────────────────────────────

@Composable
private fun RailButton(
    icon: ImageVector,
    label: String,
    focused: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.12f))
            .then(
                if (focused) Modifier.border(2.dp, Color(0xFF4FC3F7), RoundedCornerShape(50))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) Color(0xFF0B0D12) else Color.White,
        )
    }
}

@Composable
private fun CoverCard(
    cover: ImageBitmap?,
    modifier: Modifier = Modifier,
    selected: Boolean,
) {
    Card(
        modifier = modifier.then(
            if (selected) Modifier.border(3.dp, Color(0xFF4FC3F7), RoundedCornerShape(12.dp))
            else Modifier
        ),
        shape = RoundedCornerShape(GlassTokens.CardRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 12.dp else 4.dp),
        colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
        border = glassCardBorder(),
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.icon_shortcut),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

// Чипы с естественной шириной: идут в строку до края, кому не хватило
// места — переносится на следующую строку (2-3 строки).
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(chips: List<Pair<String, String?>>) {
    val visible = chips.filter { it.second != null && it.second!!.isNotBlank() }
    if (visible.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEach { (label, value) ->
            ChipItem(label to value)
        }
    }
}

@Composable
private fun ChipItem(chip: Pair<String, String?>) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.12f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(chip.first, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, lineHeight = 11.sp)
            Text(chip.second!!, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ── Sheets ────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BpSheetScaffold(
    title: String,
    rows: List<Pair<ImageVector, Pair<String, () -> Unit>>>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1A202B),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            rows.forEach { (icon, pair) ->
                val (label, onClick) = pair
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick)
                        .focusable()
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF4FC3F7), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(label, color = Color.White, fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GameOptionsSheet(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
    onEditShortcut: () -> Unit,
    onContainerSettings: () -> Unit,
    onChangeCover: (android.graphics.Bitmap) -> Unit,
    onRemoveCover: () -> Unit,
) {
    val context = LocalContext.current
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)?.let(onChangeCover)
                }
            }
        }
    }
    val editStr = stringResource(R.string.big_picture_edit_shortcut)
    val containerStr = stringResource(R.string.big_picture_container_settings)
    val changeCoverStr = stringResource(R.string.big_picture_change_cover)
    val removeCoverStr = stringResource(R.string.big_picture_remove_cover)
    val rows: MutableList<Pair<ImageVector, Pair<String, () -> Unit>>> = mutableListOf()
    rows.add(Icons.Filled.Edit to (editStr to onEditShortcut))
    rows.add(Icons.Filled.Tune to (containerStr to onContainerSettings))
    rows.add(Icons.Filled.Image to (changeCoverStr to {
        coverPicker.launch("image/*")
    }))
    if (!shortcut.customCoverArtPath.isNullOrEmpty()) {
        rows.add(Icons.Filled.Delete to (removeCoverStr to { onRemoveCover(); onDismiss() }))
    }
    BpSheetScaffold(title = shortcut.name, rows = rows, onDismiss = onDismiss)
}

// ── Кнопки hero ───────────────────────────────────────────────────────

@Composable
private fun BpPlayButton(
    playFocused: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSolidPill(
        onClick = onPlay,
        modifier = modifier
            .height(50.dp)
            .then(
                if (playFocused) Modifier.border(3.dp, Color(0xFF4FC3F7), RoundedCornerShape(16.dp))
                else Modifier
            ),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.big_picture_play), fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BpOptionsButton(
    optionsFocused: Boolean,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPill(
        onClick = onOptions,
        modifier = modifier
            .height(50.dp)
            .then(
                if (optionsFocused) Modifier.border(3.dp, Color(0xFF4FC3F7), RoundedCornerShape(16.dp))
                else Modifier
            ),
    ) {
        Icon(Icons.Filled.Tune, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.big_picture_game_options), color = Color.White)
    }
}

// ── Чипы спецификаций контейнера ─────────────────────────────────────

private fun buildChips(shortcut: Shortcut, gameVersion: String?): List<Pair<String, String?>> {
    val c = shortcut.container
    val dxw = c.getDXWrapper().lowercase()
    val dxwConfig = parseKeyValue(c.getDXWrapperConfig(), ',')
    val driverConfig = parseKeyValue(c.getGraphicsDriverConfig(), ';')
    val driverVersion = driverConfig["version"]
    val emulator = c.getEmulator().lowercase()
    val chips = mutableListOf<Pair<String, String?>>()
    chips.add("Container" to c.name)
    chips.add("Wine" to c.wineVersion.takeIf { it.isNotBlank() })
    val driverLabel = c.graphicsDriver.ifBlank { "wrapper" }
    chips.add("Driver" to buildString {
        append(driverLabel)
        if (!driverVersion.isNullOrBlank()) append(" · $driverVersion")
    })
    if (dxw.contains("dxvk")) chips.add("DXVK" to dxwConfig["version"])
    if (dxw.contains("vkd3d")) chips.add("VKD3D" to dxwConfig["vkd3dVersion"])
    if (emulator.contains("box64")) chips.add("Box64" to c.getBox64Version().takeIf { it.isNotBlank() })
    if (emulator.contains("fex")) chips.add("FEX" to c.getFEXCoreVersion().takeIf { it.isNotBlank() })
    chips.add("Version" to gameVersion)
    return chips
}

private fun parseKeyValue(raw: String, separator: Char): Map<String, String> {
    val map = HashMap<String, String>()
    if (raw.isBlank()) return map
    for (part in raw.split(separator)) {
        val idx = part.indexOf('=')
        if (idx > 0) {
            val key = part.substring(0, idx).trim()
            val value = part.substring(idx + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
    }
    return map
}

// ── Статистика ────────────────────────────────────────────────────────

private fun playtimeStats(shortcut: Shortcut): Pair<Int, String> {
    val prefs = MmkvPreferences("playtime_stats")
    val totalMs = prefs.getLong("${shortcut.name}_playtime", 0)
    val count = prefs.getInt("${shortcut.name}_play_count", 0)
    val totalSeconds = totalMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val time = buildString {
        if (hours > 0) append("${hours}ч ")
        append("${minutes}м")
    }
    return count to time
}
