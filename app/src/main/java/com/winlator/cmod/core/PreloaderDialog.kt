package com.winlator.cmod.core

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.winlator.cmod.R
import com.winlator.cmod.inputcontrols.ExternalController
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.glassBorder
import com.winlator.cmod.ui.components.glassFillBrush
import com.winlator.cmod.ui.components.glassSheetFill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import coil.compose.AsyncImage

class PreloaderDialog(private val activity: Activity) {
    private var dialog: Dialog? = null

    private var isVisible by mutableStateOf(false)
    private var titleText by mutableStateOf<CharSequence?>(null)
    private var subtitleText by mutableStateOf<CharSequence?>(null)
    private var stageText by mutableStateOf<CharSequence?>(null)
    private var coverArtBitmap by mutableStateOf<Bitmap?>(null)
    private var _steamAppId by mutableStateOf<String?>(null)
    private var exitMode by mutableStateOf(false)

    private var exitTriggered by mutableStateOf(false)
    private var onFadeOutComplete: (() -> Unit)? = null

    // Плавное закрытие: fade-out альфы корня, потом настоящий dismiss
    private var closing by mutableStateOf(false)
    private val closeHandler = Handler(Looper.getMainLooper())
    private var forceDismissRunnable: Runnable? = null
    private var closeGeneration = 0

    private fun create() {
        if (dialog != null) return
        dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)

            val composeView = ComposeView(activity).apply {
                setContent {
                    val isDark = com.winlator.cmod.contentdialog.ContentDialog.shouldUseDarkDialog(activity)
                    com.winlator.cmod.ui.theme.WinlatorTheme(darkTheme = isDark) {
                        SteamDeckBootScreen()
                    }
                }
            }
            setContentView(composeView)

            window?.let { win ->
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

                // Полный экран: скрыть статус-бар и навигационный бар
                win.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                win.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                win.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

                // Контент под системными барами, immersive
                win.decorView.systemUiVisibility =
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

                // Покрыть вырез камеры (notch) контентом
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    val lp = win.attributes
                    lp.layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    win.attributes = lp
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

                window?.decorView?.let { setOwners(it) }
                setOwners(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Composable
    private fun SteamDeckBootScreen() {
        var visible by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            visible = true
        }

        // Fade-out при закрытии: анимируем альфу корня, затем реально закрываем диалог
        val fade = remember { Animatable(1f) }
        LaunchedEffect(closing) {
            if (closing) {
                fade.animateTo(0f, tween(450, easing = EaseOutCubic))
                if (closing) dismissNow()
            } else {
                fade.snapTo(1f)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade.value }
                .background(Color(0xFF0B0C11))
        ) {
            val appId = _steamAppId?.toIntOrNull()

            // Обложка: сильный блюр и низкая альфа, чтобы фон оставался ровным
            if (appId != null && appId > 0) {
                val heroUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_hero.jpg"
                val capsuleUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/capsule_616x353.jpg"
                var heroFile by remember(appId) { mutableStateOf<java.io.File?>(SteamImageCache.getCachedFile(activity, heroUrl)) }

                LaunchedEffect(appId) {
                    if (heroFile == null) {
                        heroFile = SteamImageCache.downloadIfNeeded(activity, heroUrl)
                            ?: SteamImageCache.downloadIfNeeded(activity, capsuleUrl)
                    }
                }

                heroFile?.let { file ->
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(72.dp)
                            .alpha(0.18f),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                coverArtBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(72.dp)
                            .alpha(0.18f)
                    )
                }
            }

            // Ровная вуаль поверх обложки, без градиентов — на них бандинг полосами
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE110111A))
            )

            // Уведомление о контроллере — плавно выплывает справа сверху
            ControllerHintToast(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = 20.dp)
            )

            // Центральная композиция (иконка Windows → стрелка → миниатюра обложки)
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600)) + scaleIn(initialScale = 0.92f, animationSpec = tween(600, easing = EaseOutCubic)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    BootCenterRow(
                        bitmap = coverArtBitmap,
                        appId = appId,
                        activity = activity,
                        exit = exitMode
                    )
                }
            }

            // Нижняя панель: полоска прогресса + стадия
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val displayStage = stageText?.toString() ?: ""

                AnimatedContent(
                    targetState = displayStage,
                    transitionSpec = {
                        (fadeIn(tween(280)) + slideInVertically(animationSpec = tween(280)) { it / 4 })
                            .togetherWith(fadeOut(tween(180)) + slideOutVertically(animationSpec = tween(180)) { -it / 4 })
                    },
                    label = "stage"
                ) { stage ->
                    if (stage.isNotEmpty()) {
                        Text(
                            text = stage,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    offset = Offset(1f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            color = Color.White.copy(alpha = 0.85f),
                            letterSpacing = 0.4.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Резервируем высоту, чтобы колонка не «прыгала» при первой стадии
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                LoadingBar()
            }
        }
    }

    /**
     * Плавающее уведомление в правом верхнем углу: какой контроллер используется.
     * Если подключён физический геймпад — показывает его модель.
     * Если нет — показывает «Виртуальные кнопки».
     */
    @Composable
    private fun ControllerHintToast(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        var controllerName by remember { mutableStateOf<String?>(null) }

        DisposableEffect(Unit) {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
            val refresh = {
                controllerName = ExternalController.getControllers().firstOrNull()?.name
            }
            val listener = object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) = refresh()
                override fun onInputDeviceRemoved(deviceId: Int) = refresh()
                override fun onInputDeviceChanged(deviceId: Int) = refresh()
            }
            refresh()
            inputManager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
            onDispose { inputManager.unregisterInputDeviceListener(listener) }
        }

        val show = controllerName != null
        val text = controllerName ?: context.getString(R.string.controller_virtual)

        // Плавный выезд справа: стартуем за экраном, после появления диалога выезжаем
        val slide = remember { Animatable(1f) }
        val fade = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(400)
            launch { slide.animateTo(0f, tween(450, easing = EaseOutCubic)) }
            launch { fade.animateTo(1f, tween(450, easing = EaseOutCubic)) }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.55f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
            shadowElevation = 10.dp,
            modifier = modifier.graphicsLayer {
                translationX = slide.value * 240.dp.toPx()
                alpha = fade.value
            }
        ) {
            Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = null,
                        tint = if (show) Color(0xFF00A5FF) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = text,
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.7f),
                                offset = Offset(1f, 1f),
                                blurRadius = 3f
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
        }
    }

    /**
     * Центральный ряд: вход — иконка Windows → стрелка → обложка;
     * выход — обложка → стрелка (влево) → иконка Windows.
     */
    @Composable
    private fun BootCenterRow(
        bitmap: Bitmap?,
        appId: Int?,
        activity: Activity,
        exit: Boolean = false
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (exit) {
                // ВЫХОД: слева обложка игры, стрелка влево, справа Windows
                ThumbnailBox(bitmap = bitmap, appId = appId, activity = activity)
                ArrowBox(back = true)
                WindowsIconBox()
            } else {
                // ВХОД: слева Windows, стрелка вправо, справа обложка
                WindowsIconBox()
                ArrowBox(back = false)
                ThumbnailBox(bitmap = bitmap, appId = appId, activity = activity)
            }
        }
    }

    @Composable
    private fun WindowsIconBox() {
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(GlassTokens.InnerRadius), ambientColor = Color(0xFF00A5FF), spotColor = Color(0xFF00A5FF))
                .clip(RoundedCornerShape(GlassTokens.InnerRadius))
                .background(glassSheetFill())
                .background(glassFillBrush())
                .border(glassBorder(), RoundedCornerShape(GlassTokens.InnerRadius)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_windows_10),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
    }

    @Composable
    private fun ArrowBox(back: Boolean) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (back) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = glassAccent(),
                modifier = Modifier.size(28.dp)
            )
        }
    }

    @Composable
    private fun ThumbnailBox(bitmap: Bitmap?, appId: Int?, activity: Activity) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(elevation = 14.dp, shape = RoundedCornerShape(GlassTokens.InnerRadius), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(GlassTokens.InnerRadius))
                .background(glassSheetFill())
                .background(glassFillBrush())
                .border(glassBorder(), RoundedCornerShape(GlassTokens.InnerRadius))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (appId != null && appId > 0) {
                val capsuleUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/capsule_231x87.jpg"
                var capsuleFile by remember(appId) { mutableStateOf<java.io.File?>(SteamImageCache.getCachedFile(activity, capsuleUrl)) }
                LaunchedEffect(appId) {
                    if (capsuleFile == null) {
                        capsuleFile = SteamImageCache.downloadIfNeeded(activity, capsuleUrl)
                    }
                }
                if (capsuleFile != null) {
                    AsyncImage(
                        model = capsuleFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ThumbnailPlaceholder()
                }
            } else {
                ThumbnailPlaceholder()
            }
        }
    }

    @Composable
    private fun ThumbnailPlaceholder() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF243049),
                            Color(0xFF111521)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Computer,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(32.dp)
            )
        }
    }

    @Composable
    private fun LoadingBar() {
        // Лёгкая пульсация, чтобы полоса «дышала» на долгих стадиях
        val pulse = rememberInfiniteTransition(label = "loadingPulse").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "loadingPulseAlpha"
        )
        LinearProgressIndicator(
            modifier = Modifier
                .width(260.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = glassAccent().copy(alpha = pulse.value),
            trackColor = Color.White.copy(alpha = 0.10f)
        )
    }

    @Synchronized
    fun show(textResId: Int) {
        if (isShowing()) return
        // Отменяем незавершённый fade-out: новый показ обнуляет предыдущее закрытие
        closeGeneration++
        forceDismissRunnable?.let { closeHandler.removeCallbacks(it) }
        forceDismissRunnable = null
        if (dialog == null) create()
        exitTriggered = false
        closing = false
        stageText = activity.getString(textResId)
        dialog?.show()
        isVisible = true
    }

    fun showOnUiThread(textResId: Int) {
        activity.runOnUiThread { show(textResId) }
    }

    @Synchronized
    fun setTitle(title: CharSequence?) {
        this.titleText = title
    }

    @Synchronized
    fun setSteamAppId(steamAppId: String?) {
        this._steamAppId = steamAppId
    }

    @Synchronized
    fun setSubtitle(subtitle: CharSequence?) {
        this.subtitleText = subtitle
    }

    fun setSubtitleOnUiThread(subtitle: CharSequence?) {
        activity.runOnUiThread { setSubtitle(subtitle) }
    }

    @Synchronized
    fun setStage(stage: CharSequence?) {
        this.stageText = stage
    }

    fun setStageOnUiThread(stage: CharSequence?) {
        activity.runOnUiThread { setStage(stage) }
    }

    @Synchronized
    fun setExitModeEnabled(exit: Boolean) {
        this.exitMode = exit
    }

    @Synchronized
    fun updateText(text: String?) {
        setStage(text)
    }

    fun updateTextOnUiThread(text: String?) {
        activity.runOnUiThread { updateText(text) }
    }

    @Synchronized
    fun setCoverArt(bitmap: Bitmap?) {
        this.coverArtBitmap = bitmap
    }

    fun setCoverArtOnUiThread(bitmap: Bitmap?) {
        activity.runOnUiThread { setCoverArt(bitmap) }
    }

    @Synchronized
    fun close() {
        if (dialog == null || closing) return
        val gen = closeGeneration
        activity.runOnUiThread {
            if (dialog == null || closing || closeGeneration != gen) return@runOnUiThread
            // Диалог не показан — закрываем сразу, без анимации
            if (dialog?.isShowing != true) {
                dismissNow()
                return@runOnUiThread
            }
            closing = true
            // Фолбэк: если анимация не отработает (композиция не жива) — принудительно закрыть
            val force = Runnable {
                if (dialog != null && closeGeneration == gen) dismissNow()
            }
            forceDismissRunnable = force
            closeHandler.postDelayed(force, 800)
        }
    }

    @Synchronized
    private fun dismissNow() {
        forceDismissRunnable?.let { closeHandler.removeCallbacks(it) }
        forceDismissRunnable = null
        val d = dialog
        dialog = null
        isVisible = false
        closing = false
        exitMode = false
        try {
            d?.dismiss()
        } catch (ignored: Exception) {
        }
    }

    fun closeOnUiThread() {
        activity.runOnUiThread {
            exitTriggered = true
            close()
        }
    }

    fun isShowing(): Boolean {
        return dialog != null && dialog!!.isShowing
    }
}
