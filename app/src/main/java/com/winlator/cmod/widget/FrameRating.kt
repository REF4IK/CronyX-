package com.winlator.cmod.widget

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.container.Container
import com.winlator.cmod.core.CPUStatus
import com.winlator.cmod.core.GPUInformation
import com.winlator.cmod.core.SensorReader
import com.winlator.cmod.core.StringUtils
import java.util.Locale

class FrameRating @JvmOverloads constructor(
    context: Context,
    private val container: Container? = null,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), Runnable {

    companion object {
        private const val TAG = "FrameRating"
        private const val MAX_FRAME_TIME_MS = 50.0f
        private const val TARGET_60_FPS_MS = 16.67f
        private const val TARGET_30_FPS_MS = 33.33f
        private const val DEFAULT_SAMPLE_COUNT = 40

        // Native LSFG presented fps (real + generated). Global so it survives
        // HUD recreation and reset(); only the stats readout owns it (0 on stop).
        @Volatile private var presentedFpsGlobal: Float = 0f
        // Engine-measured real fps (same source as the FG menu readout).
        // >= 0: HUD shows the engine pair real->shown; < 0: fallback to guest.
        @Volatile private var fgRealFpsGlobal: Float = -1f
    }

    private val config = FpsCounterConfig(context)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
    private val totalRAM = getTotalRAM()
    private val sensorReader = SensorReader()
    private var lastFrameTimestampNs: Long = 0
    // Реальная (немасштабированная) ширина контента Compose: ComposeView меряется шире
    // родителя при scale<1, а наши собственные границы View-система зажимает до родителя.
    private var contentWidthPx = 0

    private var lastTime: Long = 0
    private var frameCount = 0
    private var lastFPS = 0f
    private var renderer: String? = null
    private var gpuName: String? = null
    private var batteryTemperature = -1.0f
    private var batteryVoltage = -1.0f
    private var batteryCurrent = -1.0f
    private var batteryReceiverRegistered = false

    // State variables for Compose
    private var fpsState by mutableStateOf("0")
    private var rendererState by mutableStateOf("")
    private var gpuState by mutableStateOf("")
    private var gpuLoadState by mutableStateOf("")
    private var gpuTempState by mutableStateOf("")
    private var frameTimeState by mutableStateOf("--")
    private var ramState by mutableStateOf("")
    private var cpuLoadState by mutableStateOf("")
    private var cpuTempState by mutableStateOf("")
    private var batteryTempState by mutableStateOf("")
    private var batteryVoltageState by mutableStateOf("")
    private var gpuFreqState by mutableStateOf("")
    private var batteryPercentState by mutableStateOf("")
    private var ramUsedGiBState by mutableStateOf("")
    private var cpuCoreLoadStates by mutableStateOf(emptyList<Float>())
    private var cpuCoreFreqStates by mutableStateOf(emptyList<Int>())

    // Graph samples state
    private val samples = mutableStateListOf<Float>().apply {
        repeat(DEFAULT_SAMPLE_COUNT) { add(TARGET_60_FPS_MS) }
    }
    private var nextIndex = 0

    // Configuration states
    private var isHorizontalLayout by mutableStateOf(false)
    private var backgroundOpacity by mutableStateOf(153)
    private var counterStyle by mutableIntStateOf(0)
    private var whiteFonts by mutableStateOf(false)

    // Module visibility states
    private var showFps by mutableStateOf(true)
    private var showRenderer by mutableStateOf(true)
    private var showGpu by mutableStateOf(true)
    private var showGpuLoad by mutableStateOf(true)
    private var showGpuTemp by mutableStateOf(true)
    private var showFrameTimeGraph by mutableStateOf(true)
    private var showRam by mutableStateOf(true)
    private var showCpuLoad by mutableStateOf(true)
    private var showCpuTemp by mutableStateOf(true)
    private var showBatteryTemp by mutableStateOf(true)
    private var showBatteryVoltage by mutableStateOf(true)
    private var showCpuCores by mutableStateOf(true)
    private var showGpuFreq by mutableStateOf(true)
    private var showBatteryLevel by mutableStateOf(true)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val temperature = intent.getIntExtra("temperature", -1)
            batteryTemperature = if (temperature != -1) temperature / 10.0f else -1.0f
            val voltage = intent.getIntExtra("voltage", -1)
            batteryVoltage = if (voltage != -1) voltage / 1000.0f else -1.0f
            refreshBatteryCurrent()
        }
    }

    init {
        // Горизонтальный HUD при scale<1 меряется шире родителя (см. onMeasure) —
        // без этого рисование обрезалось бы по нашим (узким) границам.
        clipChildren = false

        // Compose view integration
        val composeView = ComposeView(context).apply {
            setContent {
                FrameRatingContent()
            }
        }
        addView(composeView)

        updateModuleVisibility()
        updateOrientation()
        updateScaleAndTextSize()
        setupDragging(composeView)
    }

    // Compose Content
    @Composable
    private fun FrameRatingContent() {
        val bgColor = ComposeColor(0xFF181818).copy(alpha = backgroundOpacity / 255f)
        val strokeColor = ComposeColor(1f, 1f, 1f, 0.2f)

        // Colors
        val gpuColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFF19D07E)
        val apiColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF05A9D)
        val ramColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFE556D8)
        val cpuColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFF42B6FF)
        val batColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFA6E84B)
        val pwrColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF3DE47)
        val fpsColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFFF5F73)

        // Build active modules list
        val activeModules = remember(
            gpuState, gpuLoadState, rendererState, gpuTempState, ramState,
            cpuLoadState, cpuTempState, batteryTempState, batteryVoltageState, fpsState,
            showGpu, showGpuLoad, showRenderer, showGpuTemp, showRam,
            showCpuLoad, showCpuTemp, showBatteryTemp, showBatteryVoltage, showFps
        ) {
            val list = mutableListOf<ActiveModule>()
            if (showGpu && gpuState.isNotEmpty()) {
                list.add(ActiveModule(FpsCounterConfig.Module.GPU, "GPU", gpuState, gpuColor))
            }
            if (showGpuLoad && gpuLoadState.isNotEmpty()) {
                list.add(ActiveModule(FpsCounterConfig.Module.GPU_LOAD, "GPU", gpuLoadState, gpuColor))
            }
            if (showRenderer && rendererState.isNotEmpty()) {
                list.add(ActiveModule(FpsCounterConfig.Module.RENDERER, "API", rendererState, apiColor))
            }
            if (showGpuTemp && gpuTempState.isNotEmpty()) {
                list.add(ActiveModule(FpsCounterConfig.Module.GPU_TEMP, "GPU", gpuTempState, gpuColor))
            }
            if (showRam && ramState.isNotEmpty()) {
                list.add(ActiveModule(FpsCounterConfig.Module.RAM, "RAM", ramState, ramColor))
            }
            if (showCpuLoad && cpuLoadState.isNotEmpty()) {
                list.add(ActiveModule(FpsCounterConfig.Module.CPU_LOAD, "CPU", cpuLoadState, cpuColor))
            }
            if (showCpuTemp && cpuTempState.isNotEmpty()) {
                list.add(ActiveModule(FpsCounterConfig.Module.CPU_TEMP, "CPU", cpuTempState, cpuColor))
            }
            if (showBatteryTemp && batteryTempState.isNotEmpty()) {
                list.add(ActiveModule(FpsCounterConfig.Module.BATTERY_TEMP, "BAT", batteryTempState, batColor))
            }
            if (showBatteryVoltage && batteryVoltageState.isNotEmpty()) {
                list.add(ActiveModule(FpsCounterConfig.Module.BATTERY_VOLTAGE, "PWR", batteryVoltageState, pwrColor))
            }
            if (showFps) {
                list.add(ActiveModule(FpsCounterConfig.Module.FPS, "FPS", fpsState, fpsColor))
            }
            list
        }

        when (counterStyle) {
            FpsCounterConfig.STYLE_CYBER -> FrameRatingBadges(activeModules, bgColor, strokeColor)
            FpsCounterConfig.STYLE_RETRO -> FrameRatingDashboard(activeModules, bgColor, strokeColor)
            FpsCounterConfig.STYLE_GLASS -> FrameRatingSidebar(activeModules, bgColor, strokeColor)
            FpsCounterConfig.STYLE_WINLATOR_LUDASHI -> FrameRatingWinlatorLudashi(activeModules)
            FpsCounterConfig.STYLE_GAMENATIVE -> FrameRatingGameNative(activeModules)
            FpsCounterConfig.STYLE_MANGOHUD -> FrameRatingMangoHud()
            else -> FrameRatingClassic(activeModules, bgColor, strokeColor)
        }
    }

    private data class ActiveModule(
        val type: FpsCounterConfig.Module,
        val label: String,
        val value: String,
        val color: ComposeColor
    )

    @Composable
    private fun FrameRatingClassic(
        activeModules: List<ActiveModule>,
        bgColor: ComposeColor,
        strokeColor: ComposeColor
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(1.dp, strokeColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            if (isHorizontalLayout) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.widthIn(max = maxHudWidth()),
                    verticalArrangement = Arrangement.Center,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    activeModules.forEach { module ->
                        HudItem(module.label, module.value, module.color)
                    }
                    if (showFrameTimeGraph) {
                        FrameTimeGraphItem()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activeModules.forEach { module ->
                        HudItem(module.label, module.value, module.color)
                    }
                    if (showFrameTimeGraph) {
                        FrameTimeGraphItem()
                    }
                }
            }
        }
    }

    @Composable
    private fun FrameRatingWinlatorLudashi(activeModules: List<ActiveModule>) {
        val background = ComposeColor.Black.copy(alpha = backgroundOpacity / 255f)
        val itemModifier = Modifier.wrapContentSize()
        val orderedModules = activeModules.sortedBy { winlatorLudashiOrder(it.type) }

        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(5.dp))
                .background(background)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            if (isHorizontalLayout) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.widthIn(max = maxHudWidth()),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    maxItemsInEachRow = Int.MAX_VALUE
                ) {
                    orderedModules.forEachIndexed { index, module ->
                        if (index > 0) {
                            Text(
                                text = " | ",
                                color = ComposeColor(0xFF606060),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = itemModifier
                            )
                        }
                        WinlatorLudashiMetric(module)
                    }
                    if (showFrameTimeGraph) {
                        if (orderedModules.isNotEmpty()) {
                            Text(
                                text = " | ",
                                color = ComposeColor(0xFF606060),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = itemModifier
                            )
                        }
                        WinlatorFrameTimeMetric()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    orderedModules.forEach { module -> WinlatorLudashiMetric(module) }
                    if (showFrameTimeGraph) WinlatorFrameTimeMetric()
                }
            }
        }
    }

    @Composable
    private fun WinlatorFrameTimeMetric() {
        Row(
            modifier = Modifier.wrapContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "FT ",
                color = if (whiteFonts) ComposeColor.White else ComposeColor(0xFFFFEA00),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = frameTimeState,
                color = ComposeColor.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(5.dp))
            WinlatorFrameTimeGraph()
        }
    }

    @Composable
    private fun WinlatorFrameTimeGraph() {
        FrameTimeLineGraph(
            lineColor = ComposeColor(0xFFFFEA00),
            glowColor = ComposeColor(0x66FFEA00)
        )
    }

    @Composable
    private fun WinlatorLudashiMetric(module: ActiveModule) {
        if (module.type == FpsCounterConfig.Module.RENDERER) {
            Text(
                text = module.value,
                color = winlatorLudashiColor(module.type),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            return
        }

        Row(
            modifier = Modifier.wrapContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${module.label} ",
                color = winlatorLudashiColor(module.type),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = module.value,
                color = ComposeColor.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    private fun winlatorLudashiColor(type: FpsCounterConfig.Module): ComposeColor {
        if (whiteFonts) return ComposeColor.White

        return when (type) {
            FpsCounterConfig.Module.GPU,
            FpsCounterConfig.Module.GPU_LOAD,
            FpsCounterConfig.Module.GPU_TEMP,
            FpsCounterConfig.Module.GPU_FREQ -> ComposeColor(0xFFE040FB)
            FpsCounterConfig.Module.CPU_LOAD,
            FpsCounterConfig.Module.CPU_TEMP,
            FpsCounterConfig.Module.CPU_CORES -> ComposeColor(0xFF00E5FF)
            FpsCounterConfig.Module.BATTERY_TEMP -> ComposeColor(0xFFEF5350)
            FpsCounterConfig.Module.BATTERY_VOLTAGE,
            FpsCounterConfig.Module.BATTERY_LEVEL -> ComposeColor(0xFFFF8000)
            FpsCounterConfig.Module.FPS -> ComposeColor(0xFF76FF03)
            FpsCounterConfig.Module.RENDERER -> ComposeColor(0xFFFFEA00)
            FpsCounterConfig.Module.RAM -> ComposeColor(0xFFB0FFB0)
            FpsCounterConfig.Module.FRAME_TIME_GRAPH -> ComposeColor(0xFFFFB300)
        }
    }

    private fun winlatorLudashiOrder(type: FpsCounterConfig.Module): Int {
        return when (type) {
            FpsCounterConfig.Module.RENDERER -> 0
            FpsCounterConfig.Module.GPU,
            FpsCounterConfig.Module.GPU_LOAD,
            FpsCounterConfig.Module.GPU_TEMP,
            FpsCounterConfig.Module.GPU_FREQ -> 1
            FpsCounterConfig.Module.CPU_LOAD,
            FpsCounterConfig.Module.CPU_TEMP,
            FpsCounterConfig.Module.CPU_CORES -> 2
            FpsCounterConfig.Module.RAM -> 3
            FpsCounterConfig.Module.BATTERY_VOLTAGE,
            FpsCounterConfig.Module.BATTERY_LEVEL,
            FpsCounterConfig.Module.BATTERY_TEMP -> 4
            FpsCounterConfig.Module.FPS -> 5
            FpsCounterConfig.Module.FRAME_TIME_GRAPH -> 6
        }
    }

    @Composable
    private fun FrameRatingGameNative(activeModules: List<ActiveModule>) {
        val opacity = (backgroundOpacity / 255f).coerceIn(0f, 1f)
        val background = ComposeColor.Black.copy(alpha = opacity)
        val stroke = ComposeColor.White.copy(alpha = 0.38f * opacity)
        val orderedModules = activeModules.sortedBy { gameNativeOrder(it.type) }
        val textStyle = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            shadow = Shadow(
                color = ComposeColor.Black.copy(alpha = 0.86f),
                offset = Offset.Zero,
                blurRadius = 2f
            )
        )

        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(10.dp))
                .background(background)
                .border(1.dp, stroke, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (isHorizontalLayout) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.widthIn(max = maxHudWidth()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    orderedModules.forEach { module ->
                        GameNativeMetric(module, textStyle, compact = true)
                    }
                    if (showFrameTimeGraph) GameNativeFrameTimeMetric(compact = true)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    orderedModules.forEach { module ->
                        GameNativeMetric(module, textStyle, compact = false)
                    }
                    if (showFrameTimeGraph) GameNativeFrameTimeMetric(compact = false)
                }
            }
        }
    }

    @Composable
    private fun GameNativeMetric(module: ActiveModule, textStyle: TextStyle, compact: Boolean) {
        GameNativeMetricText(module, textStyle)
    }

    @Composable
    private fun GameNativeFrameTimeMetric(compact: Boolean) {
        val valueStyle = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            shadow = Shadow(
                color = ComposeColor.Black.copy(alpha = 0.86f),
                offset = Offset.Zero,
                blurRadius = 2f
            )
        )
        val labelColor = if (whiteFonts) ComposeColor.White else ComposeColor(0xFF4CAF50)

        if (compact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("FT", color = labelColor, style = valueStyle)
                GameNativeFrameTimeGraph()
                Text(frameTimeState, color = ComposeColor.White, style = valueStyle)
            }
        } else {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("FT", color = labelColor, style = valueStyle)
                    Text(frameTimeState, color = ComposeColor.White, style = valueStyle)
                }
                GameNativeFrameTimeGraph()
            }
        }
    }

    @Composable
    private fun GameNativeMetricText(module: ActiveModule, textStyle: TextStyle) {
        Text(
            text = "${module.label} ${module.value}",
            color = gameNativeColor(module.type),
            style = textStyle,
            maxLines = 1
        )
    }

    private fun gameNativeColor(type: FpsCounterConfig.Module): ComposeColor {
        if (whiteFonts) return ComposeColor.White

        return when (type) {
            FpsCounterConfig.Module.FPS -> ComposeColor(0xFF4CAF50)
            FpsCounterConfig.Module.CPU_LOAD,
            FpsCounterConfig.Module.CPU_TEMP,
            FpsCounterConfig.Module.CPU_CORES -> ComposeColor(0xFF42A5F5)
            FpsCounterConfig.Module.GPU,
            FpsCounterConfig.Module.GPU_LOAD,
            FpsCounterConfig.Module.GPU_TEMP,
            FpsCounterConfig.Module.GPU_FREQ -> ComposeColor(0xFFEF5350)
            FpsCounterConfig.Module.RAM -> ComposeColor(0xFFFFEE58)
            FpsCounterConfig.Module.BATTERY_TEMP -> ComposeColor(0xFFBDBDBD)
            FpsCounterConfig.Module.BATTERY_VOLTAGE,
            FpsCounterConfig.Module.BATTERY_LEVEL -> ComposeColor(0xFF4DD0E1)
            FpsCounterConfig.Module.RENDERER -> ComposeColor(0xFFA5D6A7)
            FpsCounterConfig.Module.FRAME_TIME_GRAPH -> ComposeColor(0xFF4CAF50)
        }
    }

    private fun gameNativeOrder(type: FpsCounterConfig.Module): Int {
        return when (type) {
            FpsCounterConfig.Module.FPS -> 0
            FpsCounterConfig.Module.CPU_LOAD,
            FpsCounterConfig.Module.CPU_TEMP,
            FpsCounterConfig.Module.CPU_CORES -> 1
            FpsCounterConfig.Module.GPU,
            FpsCounterConfig.Module.GPU_LOAD,
            FpsCounterConfig.Module.GPU_TEMP,
            FpsCounterConfig.Module.GPU_FREQ -> 2
            FpsCounterConfig.Module.RAM -> 3
            FpsCounterConfig.Module.BATTERY_VOLTAGE,
            FpsCounterConfig.Module.BATTERY_LEVEL -> 4
            FpsCounterConfig.Module.BATTERY_TEMP -> 5
            FpsCounterConfig.Module.RENDERER -> 6
            FpsCounterConfig.Module.FRAME_TIME_GRAPH -> 7
        }
    }

    // ── MangoHud ──
    // Цвета по скринам MangoHud: GPU/FPS/батарея зелёные, CPU голубой, RAM фиолетовый,
    // движок красный; значения белые, единицы мелкие серые. Вертикаль = блок строк
    // (включая CPU0..N), горизонталь = компактный бар без строк ядер.

    @Composable
    private fun FrameRatingMangoHud() {
        val opacity = (backgroundOpacity / 255f).coerceIn(0f, 1f)
        val background = ComposeColor.Black.copy(alpha = opacity)
        val valueColor = if (whiteFonts) ComposeColor.White else ComposeColor(0xFFF2F2F2)
        val unitColor = if (whiteFonts) ComposeColor.White.copy(alpha = 0.7f) else ComposeColor(0xFF9E9E9E)
        val gpuColor = if (whiteFonts) ComposeColor.White else ComposeColor(0xFF2ECC71)
        val cpuColor = if (whiteFonts) ComposeColor.White else ComposeColor(0xFF29B6F6)
        val ramColor = if (whiteFonts) ComposeColor.White else ComposeColor(0xFFAB7BF0)
        val engineColor = if (whiteFonts) ComposeColor.White else ComposeColor(0xFFFF5252)
        val fpsColor = gpuColor
        val separatorColor = ComposeColor(0xFF6A6A6A)
        val graphColor = if (whiteFonts) ComposeColor.White else ComposeColor(0xFFFF4757)
        val graphGlow = ComposeColor(0x55FF4757)

        val gpuLoadValue = gpuLoadState.trimEnd('%')
        val gpuTempValue = gpuTempState.removeSuffix("C")
        val cpuLoadValue = cpuLoadState.trimEnd('%')
        val cpuTempValue = cpuTempState.removeSuffix("C")
        val frameTimeValue = frameTimeState.removeSuffix("ms")
        val showFrametimeText = frameTimeState != "--"

        // Сегмент показывается, если включён ХОТЬ один его показатель
        // (например, только температура CPU без загрузки — сегмент не должен исчезать).
        val gpuHasLoad = showGpuLoad && gpuLoadValue.isNotEmpty()
        val gpuHasTemp = showGpuTemp && gpuTempValue.isNotEmpty() && gpuTempValue != "N/A"
        val gpuHasFreq = showGpuFreq && gpuFreqState.isNotEmpty()
        val cpuHasLoad = showCpuLoad && cpuLoadValue.isNotEmpty()
        val cpuHasTemp = showCpuTemp && cpuTempValue.isNotEmpty() && cpuTempValue != "N/A"
        val battHasLevel = showBatteryLevel && batteryPercentState.isNotEmpty()
        val battHasTemp = showBatteryTemp && batteryTempState.isNotEmpty() && batteryTempState != "N/A"

        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            if (isHorizontalLayout) {
                val items = mutableListOf<@Composable () -> Unit>()
                if (gpuHasLoad || gpuHasTemp || gpuHasFreq) {
                    items.add {
                        MangoRow {
                            MangoLabel("GPU", gpuColor)
                            if (gpuHasLoad) MangoMetric(gpuLoadValue, "%", valueColor, unitColor)
                            if (gpuHasTemp) MangoMetric(gpuTempValue, "°C", valueColor, unitColor)
                            if (gpuHasFreq) MangoMetric(gpuFreqState, "MHz", valueColor, unitColor)
                        }
                    }
                }
                if (cpuHasLoad || cpuHasTemp) {
                    items.add {
                        MangoRow {
                            MangoLabel("CPU", cpuColor)
                            if (cpuHasLoad) MangoMetric(cpuLoadValue, "%", valueColor, unitColor)
                            if (cpuHasTemp) MangoMetric(cpuTempValue, "°C", valueColor, unitColor)
                        }
                    }
                }
                if (showRam && ramUsedGiBState.isNotEmpty()) {
                    items.add {
                        MangoRow {
                            MangoLabel("RAM", ramColor)
                            MangoMetric(ramUsedGiBState, "GiB", valueColor, unitColor)
                        }
                    }
                }
                if (battHasLevel || battHasTemp) {
                    items.add {
                        MangoRow {
                            MangoLabel("BATT", fpsColor)
                            if (battHasLevel) MangoMetric(batteryPercentState, "%", valueColor, unitColor)
                            if (battHasTemp) MangoMetric(batteryTempState.removeSuffix("C"), "°C", valueColor, unitColor)
                        }
                    }
                }
                if (showFps && fpsState.isNotEmpty()) {
                    items.add {
                        MangoRow {
                            MangoLabel("FPS", fpsColor)
                            MangoMetric(fpsState, "", valueColor, unitColor)
                            if (showFrametimeText) {
                                MangoMetric(frameTimeValue, "ms", valueColor, unitColor)
                            }
                        }
                    }
                }
                if (showFrameTimeGraph) {
                    items.add {
                        MangoRow { FrameTimeLineGraph(lineColor = graphColor, glowColor = graphGlow) }
                    }
                }

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.widthIn(max = maxHudWidth()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    maxItemsInEachRow = Int.MAX_VALUE
                ) {
                    items.forEachIndexed { index, item ->
                        if (index > 0) {
                            Text(
                                text = " | ",
                                color = separatorColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        item()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    if (showGpu && gpuState.isNotEmpty()) {
                        Text(
                            text = gpuState,
                            color = valueColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (gpuHasLoad || gpuHasTemp || gpuHasFreq) {
                        MangoRow {
                            MangoLabel("GPU", gpuColor)
                            if (gpuHasLoad) MangoMetric(gpuLoadValue, "%", valueColor, unitColor)
                            if (gpuHasTemp) MangoMetric(gpuTempValue, "°C", valueColor, unitColor)
                            if (gpuHasFreq) MangoMetric(gpuFreqState, "MHz", valueColor, unitColor)
                        }
                    }
                    if (cpuHasLoad || cpuHasTemp) {
                        MangoRow {
                            MangoLabel("CPU", cpuColor)
                            if (cpuHasLoad) MangoMetric(cpuLoadValue, "%", valueColor, unitColor)
                            if (cpuHasTemp) MangoMetric(cpuTempValue, "°C", valueColor, unitColor)
                        }
                    }
                    if (showCpuCores) {
                        val coreCount = maxOf(cpuCoreLoadStates.size, cpuCoreFreqStates.size)
                        for (i in 0 until coreCount) {
                            val load = cpuCoreLoadStates.getOrNull(i) ?: -1f
                            val freq = cpuCoreFreqStates.getOrNull(i) ?: 0
                            if (load < 0f && freq <= 0) continue
                            MangoRow {
                                MangoLabel("CPU$i", cpuColor)
                                MangoMetric(
                                    if (load >= 0f) Math.round(load).toString() else "--",
                                    "%", valueColor, unitColor
                                )
                                if (freq > 0) {
                                    MangoMetric(freq.toString(), "MHz", valueColor, unitColor)
                                }
                            }
                        }
                    }
                    if (showRam && ramUsedGiBState.isNotEmpty()) {
                        MangoRow {
                            MangoLabel("RAM", ramColor)
                            MangoMetric(ramUsedGiBState, "GiB", valueColor, unitColor)
                        }
                    }
                    if (battHasLevel || battHasTemp) {
                        MangoRow {
                            MangoLabel("BATT", fpsColor)
                            if (battHasLevel) MangoMetric(batteryPercentState, "%", valueColor, unitColor)
                            if (battHasTemp) MangoMetric(batteryTempState.removeSuffix("C"), "°C", valueColor, unitColor)
                        }
                    }
                    if (showRenderer && rendererState.isNotEmpty()) {
                        MangoRow {
                            MangoLabel(rendererState, engineColor)
                            if (showFps && fpsState.isNotEmpty()) {
                                MangoMetric(fpsState, "FPS", valueColor, unitColor)
                                if (showFrametimeText) {
                                    MangoMetric(frameTimeValue, "ms", valueColor, unitColor)
                                }
                            }
                        }
                    } else if (showFps && fpsState.isNotEmpty()) {
                        MangoRow {
                            MangoLabel("FPS", fpsColor)
                            MangoMetric(fpsState, "", valueColor, unitColor)
                            if (showFrametimeText) {
                                MangoMetric(frameTimeValue, "ms", valueColor, unitColor)
                            }
                        }
                    }
                    if (showFrameTimeGraph) {
                        FrameTimeLineGraph(lineColor = graphColor, glowColor = graphGlow)
                    }
                }
            }
        }
    }

    @Composable
    private fun MangoRow(content: @Composable RowScope.() -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            content = content
        )
    }

    @Composable
    private fun RowScope.MangoLabel(text: String, color: ComposeColor) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.alignByBaseline()
        )
    }

    @Composable
    private fun RowScope.MangoMetric(value: String, unit: String, valueColor: ComposeColor, unitColor: ComposeColor) {
        // Единый Text: единица прижата к значению без зазора и на общей базовой линии (как в MangoHud).
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)) {
                    append(value)
                }
                if (unit.isNotEmpty()) {
                    withStyle(SpanStyle(color = unitColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)) {
                        append(unit)
                    }
                }
            },
            modifier = Modifier.alignByBaseline()
        )
    }

    @Composable
    private fun GameNativeFrameTimeGraph() {
        FrameTimeLineGraph(
            lineColor = ComposeColor(0xFF4CAF50),
            glowColor = ComposeColor(0x664CAF50)
        )
    }

    @Composable
    private fun FrameTimeLineGraph(lineColor: ComposeColor, glowColor: ComposeColor) {

        Canvas(modifier = Modifier.size(width = 72.dp, height = 16.dp)) {
            if (samples.size < 2) return@Canvas

            val path = ComposePath()
            val step = size.width / (samples.size - 1)
            for (index in samples.indices) {
                val sample = samples[(nextIndex + index) % samples.size]
                val x = index * step
                val y = size.height - (sample / MAX_FRAME_TIME_MS * size.height).coerceIn(0f, size.height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = glowColor,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }

    @Composable
    private fun maxHudWidth(): androidx.compose.ui.unit.Dp {
        val screenWidthDp = LocalConfiguration.current.screenWidthDp
        val scale = config.counterScale.coerceAtLeast(60) / 100f
        return ((screenWidthDp - 8).coerceAtLeast(180) / scale)
            .coerceAtLeast(180f)
            .dp
    }

    @Composable
    private fun FrameRatingBadges(
        activeModules: List<ActiveModule>,
        bgColor: ComposeColor,
        strokeColor: ComposeColor
    ) {
        if (isHorizontalLayout) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.widthIn(max = maxHudWidth()),
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeModules.forEach { module ->
                    BadgeItem(module.label, module.value, module.color, bgColor, strokeColor)
                }
                if (showFrameTimeGraph) {
                    BadgeFrameTime(bgColor, strokeColor)
                }
            }
        } else {
            Column(
                modifier = Modifier.wrapContentSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                activeModules.forEach { module ->
                    BadgeItem(module.label, module.value, module.color, bgColor, strokeColor)
                }
                if (showFrameTimeGraph) {
                    BadgeFrameTime(bgColor, strokeColor)
                }
            }
        }
    }

    @Composable
    private fun BadgeItem(
        label: String,
        value: String,
        labelColor: ComposeColor,
        bgColor: ComposeColor,
        strokeColor: ComposeColor
    ) {
        val valueColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF2F2F2)
        Row(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(1.dp, strokeColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }

    @Composable
    private fun BadgeFrameTime(bgColor: ComposeColor, strokeColor: ComposeColor) {
        val labelColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFFF5F73)
        val valueColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF8F8F8)
        Row(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(1.dp, strokeColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("FT", color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            FrameTimeGraphCompose()
            Text(frameTimeState, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }

    @Composable
    private fun FrameRatingDashboard(
        activeModules: List<ActiveModule>,
        bgColor: ComposeColor,
        strokeColor: ComposeColor
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(1.dp, strokeColor, RoundedCornerShape(12.dp))
                .padding(6.dp)
        ) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.widthIn(max = if (isHorizontalLayout) maxHudWidth() else 190.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tileWidth = if (isHorizontalLayout) 88.dp else 86.dp
                activeModules.forEach { module ->
                    val valueColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF2F2F2)
                    Box(
                        modifier = Modifier
                            .width(tileWidth)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor.copy(alpha = (bgColor.alpha * 1.3f).coerceIn(0f, 1f)))
                            .border(1.dp, strokeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(module.label, color = module.color, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                module.value,
                                color = valueColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                    }
                }
                
                if (showFrameTimeGraph) {
                    val ftLabelColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFFF5F73)
                    val ftValueColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF8F8F8)
                    Box(
                        modifier = Modifier
                            .width(if (isHorizontalLayout) 180.dp else 176.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor.copy(alpha = (bgColor.alpha * 1.3f).coerceIn(0f, 1f)))
                            .border(1.dp, strokeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("FT", color = ftLabelColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            FrameTimeGraphCompose()
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(frameTimeState, color = ftValueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FrameRatingSidebar(
        activeModules: List<ActiveModule>,
        bgColor: ComposeColor,
        strokeColor: ComposeColor
    ) {
        val valueColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF2F2F2)
        
        Box(
            modifier = Modifier
                .width(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(1.dp, strokeColor, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                activeModules.forEach { module ->
                    val progressValue = when (module.type) {
                        FpsCounterConfig.Module.GPU_LOAD -> parsePercentage(module.value)
                        FpsCounterConfig.Module.CPU_LOAD -> parsePercentage(module.value)
                        FpsCounterConfig.Module.RAM -> parseRamRatio(module.value)
                        else -> -1f
                    }
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(module.label, color = module.color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                module.value,
                                color = valueColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                        }
                        
                        if (progressValue >= 0f) {
                            Spacer(modifier = Modifier.height(2.dp))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { progressValue },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = module.color,
                                trackColor = module.color.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
                
                if (showFrameTimeGraph) {
                    val ftLabelColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFFF5F73)
                    val ftValueColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF8F8F8)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("FT", color = ftLabelColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(frameTimeState, color = ftValueColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            FrameTimeGraphCompose()
                        }
                    }
                }
            }
        }
    }

    private fun parsePercentage(value: String): Float {
        return try {
            val numeric = value.replace("%", "").trim()
            (numeric.toFloatOrNull() ?: 0f) / 100f
        } catch (e: Exception) {
            0f
        }
    }

    private fun parseRamRatio(value: String): Float {
        return try {
            if (value.contains("/")) {
                val parts = value.split("/")
                val used = parts[0].replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
                val total = parts[1].replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 1f
                if (total > 0f) (used / total).coerceIn(0f, 1f) else 0f
            } else {
                0f
            }
        } catch (e: Exception) {
            0f
        }
    }

    @Composable
    private fun HudItem(label: String, value: String, labelColor: ComposeColor) {
        val valueColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF2F2F2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    @Composable
    private fun FrameTimeGraphItem() {
        val ftLabelColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFFF5F73)
        val ftValueColor = if (whiteFonts) ComposeColor(0xFFFFFFFF) else ComposeColor(0xFFF8F8F8)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            Text(
                text = "FT",
                color = ftLabelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            FrameTimeGraphCompose()
            Text(
                text = frameTimeState,
                color = ftValueColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    @Composable
    private fun FrameTimeGraphCompose() {
        val linePaintColor = ComposeColor(0xFFFFB300)
        val fillPaintColor = ComposeColor(0x33FFB300)
        val guide60Color = ComposeColor(0x55FFFFFF)
        val guide30Color = ComposeColor(0x44FF5252)

        Canvas(modifier = Modifier.size(width = 58.dp, height = 16.dp)) {
            val width = size.width
            val height = size.height

            // Draw guides
            val y60 = height - (TARGET_60_FPS_MS / MAX_FRAME_TIME_MS * height)
            drawLine(
                color = guide60Color,
                start = Offset(0f, y60),
                end = Offset(width, y60),
                strokeWidth = 1f
            )

            val y30 = height - (TARGET_30_FPS_MS / MAX_FRAME_TIME_MS * height)
            drawLine(
                color = guide30Color,
                start = Offset(0f, y30),
                end = Offset(width, y30),
                strokeWidth = 1f
            )

            val pointsCount = samples.size
            if (pointsCount > 1) {
                val spacing = width / (pointsCount - 1)
                val linePath = ComposePath()
                val fillPath = ComposePath()

                for (i in 0 until pointsCount) {
                    val start = nextIndex
                    val sample = samples[(start + i) % pointsCount]
                    val x = i * spacing
                    val y = height - (sample / MAX_FRAME_TIME_MS * height).coerceIn(0f, height)

                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(width, height)
                fillPath.close()

                drawPath(fillPath, color = fillPaintColor)
                drawPath(linePath, color = linePaintColor, style = Stroke(width = 1.8.dp.toPx()))
            }
        }
    }

    private fun getTotalRAM(): String {
        if (activityManager == null) return "N/A"
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return StringUtils.formatBytes(memoryInfo.totalMem)
    }

    private fun getUsedRAM(): String {
        if (activityManager == null) return "N/A"
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val usedMem = memoryInfo.totalMem - memoryInfo.availMem
        return StringUtils.formatBytes(usedMem, false)
    }

    /** Использованная RAM в ГиБ (формат MangoHud "2.5GiB"), "" если недоступна. */
    private fun getUsedRamGiB(): String {
        if (activityManager == null) return ""
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val usedGiB = (memoryInfo.totalMem - memoryInfo.availMem) / 1073741824.0
        return String.format(Locale.ENGLISH, "%.1f", usedGiB)
    }

    fun reset() {
        Log.d(TAG, "Resetting FrameRating")
        renderer = null
        gpuName = null
        lastFPS = 0f
        frameCount = 0
        lastTime = 0
        lastFrameTimestampNs = 0
        samples.clear()
        repeat(DEFAULT_SAMPLE_COUNT) { samples.add(TARGET_60_FPS_MS) }
        nextIndex = 0
    }

    fun setRenderer(renderer: String?) {
        this.renderer = renderer
    }

    // Native LSFG: presented (real + generated) fps from the compositor.
    // Shown as "real -> shown" next to the guest-rate counter; 0 = plain mode.
    fun setPresentedFps(fps: Float) {
        presentedFpsGlobal = fps
        fgRealFpsGlobal = -1f
    }

    // Native LSFG: feed BOTH engine numbers (same source as the FG menu).
    // While shown > 0 the HUD displays the engine pair, not the guest rate.
    fun setFrameGenFps(realFps: Float, shownFps: Float) {
        fgRealFpsGlobal = realFps
        presentedFpsGlobal = shownFps
    }

    fun setGpuName(gpuName: String?) {
        this.gpuName = gpuName
    }

    fun updateOrientation() {
        isHorizontalLayout = config.isHorizontalLayout()
        val flexWidth = ViewGroup.LayoutParams.WRAP_CONTENT
        val clp = layoutParams
        if (clp != null) {
            clp.width = flexWidth
        }
        requestLayout()
    }

    fun updateModuleVisibility() {
        showFps = config.isModuleVisible(FpsCounterConfig.Module.FPS)
        showRenderer = config.isModuleVisible(FpsCounterConfig.Module.RENDERER)
        showGpu = config.isModuleVisible(FpsCounterConfig.Module.GPU)
        showGpuLoad = config.isModuleVisible(FpsCounterConfig.Module.GPU_LOAD)
        showGpuTemp = config.isModuleVisible(FpsCounterConfig.Module.GPU_TEMP)
        showFrameTimeGraph = config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH)
        showRam = config.isModuleVisible(FpsCounterConfig.Module.RAM)
        showCpuLoad = config.isModuleVisible(FpsCounterConfig.Module.CPU_LOAD)
        showCpuTemp = config.isModuleVisible(FpsCounterConfig.Module.CPU_TEMP)
        showBatteryTemp = config.isModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP)
        showBatteryVoltage = config.isModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE)
        showCpuCores = config.isModuleVisible(FpsCounterConfig.Module.CPU_CORES)
        showGpuFreq = config.isModuleVisible(FpsCounterConfig.Module.GPU_FREQ)
        showBatteryLevel = config.isModuleVisible(FpsCounterConfig.Module.BATTERY_LEVEL)
        counterStyle = config.counterStyle
        whiteFonts = config.isWhiteFonts()
        updateBackgroundOpacity()
        updateScaleAndTextSize()
    }

    fun updateBackgroundOpacity() {
        backgroundOpacity = config.backgroundOpacity
    }

    fun updateScaleAndTextSize() {
        val scale = config.counterScale
        val scaleFactor = scale / 100.0f
        pivotX = 0f
        pivotY = 0f
        scaleX = scaleFactor
        scaleY = scaleFactor
        // Доступная ширина зависит от масштаба — перемерять контент.
        requestLayout()

        post { clampToParentBounds() }
    }

    /**
     * Горизонтальный HUD при scale<1 должен помещаться в ширину экрана НА ЭКРАНЕ,
     * но Compose меряется в немасштабированных координатах и упирается в ширину
     * родителя → ранний перенос строк. Разрешаем ComposeView замер до
     * ширина_родителя/scale (на экране после масштабирования это ширина родителя).
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var spec = widthMeasureSpec
        val parentView = parent as? View
        val parentWidth = parentView?.width ?: 0
        if (parentWidth > 0) {
            val mode = View.MeasureSpec.getMode(widthMeasureSpec)
            if (mode == View.MeasureSpec.AT_MOST || mode == View.MeasureSpec.UNSPECIFIED) {
                val scale = if (scaleX > 0f) scaleX else 1f
                val widened = Math.max(View.MeasureSpec.getSize(widthMeasureSpec), (parentWidth / scale).toInt())
                spec = View.MeasureSpec.makeMeasureSpec(widened, View.MeasureSpec.AT_MOST)
            }
        }
        super.onMeasure(spec, heightMeasureSpec)
        contentWidthPx = if (childCount > 0) getChildAt(0).measuredWidth else measuredWidth
    }

    /** Ширина контента для клампа/драга: ComposeView может быть шире наших зажатых границ. */
    private fun hudWidthPx(): Int = if (contentWidthPx > 0) contentWidthPx else width

    private fun clampToParentBounds() {
        val parentView = parent as? View ?: return
        val parentWidth = parentView.width
        val parentHeight = parentView.height
        val scaledWidth = Math.ceil((hudWidthPx() * scaleX).toDouble()).toInt()
        val scaledHeight = Math.ceil((height * scaleY).toDouble()).toInt()

        if (parentWidth <= 0 || parentHeight <= 0 || scaledWidth <= 0 || scaledHeight <= 0) return

        val maxX = Math.max(0f, (parentWidth - scaledWidth).toFloat())
        val maxY = Math.max(0f, (parentHeight - scaledHeight).toFloat())
        x = Math.max(0f, Math.min(x, maxX))
        y = Math.max(0f, Math.min(y, maxY))
    }

    private fun setupDragging(targetView: View) {
        val dX = FloatArray(1)
        val dY = FloatArray(1)
        val dragging = BooleanArray(1)

        targetView.setOnTouchListener { _, event ->
            val action = event.actionMasked
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging[0] = true
                    dX[0] = x - event.rawX
                    dY[0] = y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging[0]) return@setOnTouchListener false
                    var newX = event.rawX + dX[0]
                    var newY = event.rawY + dY[0]
                    val parentView = parent as? View
                    if (parentView != null) {
                        val parentWidth = parentView.width
                        val parentHeight = parentView.height
                        val scaledWidth = Math.ceil((hudWidthPx() * scaleX).toDouble()).toInt()
                        val scaledHeight = Math.ceil((height * scaleY).toDouble()).toInt()
                        if (parentWidth > 0 && parentHeight > 0 && scaledWidth > 0 && scaledHeight > 0) {
                            val maxX = Math.max(0f, (parentWidth - scaledWidth).toFloat())
                            val maxY = Math.max(0f, (parentHeight - scaledHeight).toFloat())
                            newX = Math.max(0f, Math.min(newX, maxX))
                            newY = Math.max(0f, Math.min(newY, maxY))
                        }
                    }
                    x = newX
                    y = newY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging[0] = false
                    true
                }
                else -> false
            }
        }
    }

    fun getConfig(): FpsCounterConfig {
        return config
    }

    fun update() {
        val profiling = ProfilingSession.getInstance().isActive()
        val overlayVisible = config.isEnabled && visibility == View.VISIBLE
        if (!overlayVisible && !profiling) return

        val frameTimestampNs = SystemClock.elapsedRealtimeNanos()
        if (lastFrameTimestampNs != 0L && overlayVisible && config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH)) {
            val frameTimeMs = (frameTimestampNs - lastFrameTimestampNs) / 1000000.0f
            val clamped = Math.max(0f, Math.min(MAX_FRAME_TIME_MS, frameTimeMs))
            samples[nextIndex] = clamped
            nextIndex = (nextIndex + 1) % samples.size
        }
        lastFrameTimestampNs = frameTimestampNs

        if (lastTime == 0L) {
            lastTime = SystemClock.elapsedRealtime()
        }
        val time = SystemClock.elapsedRealtime()
        if (time >= lastTime + 500L) {
            lastFPS = (frameCount * 1000).toFloat() / (time - lastTime)
            if (profiling) {
                ProfilingSession.getInstance().addFpsSample(lastFPS)
            }
            post(this)
            lastTime = time
            frameCount = 0
        }
        frameCount++
    }

    override fun run() {
        val profiling = ProfilingSession.getInstance().isActive()
        val overlayVisible = config.isEnabled && visibility == View.VISIBLE
        if (!overlayVisible && !profiling) return

        if (profiling) {
            collectProfilingSensorSample()
        }
        if (!overlayVisible) return

        if (config.isModuleVisible(FpsCounterConfig.Module.FPS)) {
            val shown = presentedFpsGlobal
            fpsState = if (shown > 0f) {
                // Frame gen on: same numbers as the FG menu (engine-measured).
                val real = fgRealFpsGlobal
                if (real >= 0f) String.format(Locale.ENGLISH, "%.1f→%.1f", real, shown)
                else String.format(Locale.ENGLISH, "%.1f→%.1f", lastFPS, shown)
            } else {
                String.format(Locale.ENGLISH, "%.1f", lastFPS)
            }
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.RENDERER)) {
            rendererState = renderer ?: "OpenGL"
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU)) {
            gpuState = gpuName ?: GPUInformation.getRenderer()
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU_LOAD)) {
            gpuLoadState = sensorReader.gpuLoad
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU_TEMP)) {
            gpuTempState = sensorReader.gpuTemperature
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH)) {
            val frameTimeMs = if (lastFPS > 0f) 1000.0f / lastFPS else 0f
            frameTimeState = if (frameTimeMs > 0f) String.format(Locale.ENGLISH, "%.1fms", frameTimeMs) else "--"
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.RAM)) {
            ramState = "${getUsedRAM()} / $totalRAM"
            ramUsedGiBState = getUsedRamGiB()
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.CPU_LOAD)) {
            cpuLoadState = sensorReader.cpuLoad
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.CPU_TEMP)) {
            cpuTempState = sensorReader.cpuTemperature
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP)) {
            batteryTempState = if (batteryTemperature != -1.0f) String.format(Locale.ENGLISH, "%.1fC", batteryTemperature) else "N/A"
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE)) {
            refreshBatteryCurrent()
            val powerWatts = getBatteryPowerWatts()
            batteryVoltageState = if (powerWatts != -1.0f) String.format(Locale.ENGLISH, "%.2fW", powerWatts) else "N/A"
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU_FREQ)) {
            gpuFreqState = sensorReader.getGpuFrequencyMhz()
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.CPU_CORES)) {
            cpuCoreLoadStates = sensorReader.cpuCoreLoads.toList()
            cpuCoreFreqStates = CPUStatus.getCurrentClockSpeeds().map { it.toInt() }
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.BATTERY_LEVEL)) {
            val percent = sensorReader.getBatteryPercent(batteryManager)
            batteryPercentState = if (percent >= 0) Integer.toString(percent) else ""
        }
    }

    private fun collectProfilingSensorSample() {
        ProfilingSession.getInstance().collectSample(context)
    }

    private fun refreshBatteryCurrent() {
        if (batteryManager == null) {
            batteryCurrent = -1.0f
            return
        }
        batteryCurrent = sensorReader.readCurrentAmpsFromSysfs(batteryManager)
    }

    private fun getBatteryPowerWatts(): Float {
        return sensorReader.getBatteryPowerWatts(batteryManager, batteryVoltage, batteryCurrent)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!batteryReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
            batteryReceiverRegistered = true
        }
        // Ширина родителя известна только после первой раскладки — перемериться,
        // чтобы горизонтальный HUD сразу занял всю ширину с учётом масштаба.
        post { requestLayout() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (batteryReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (ignored: IllegalArgumentException) {
            }
            batteryReceiverRegistered = false
        }
    }
}
