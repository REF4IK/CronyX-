package com.winlator.cmod.winhandler

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.core.CPUStatus
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.ProcessHelper
import com.winlator.cmod.core.SensorReader
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.widget.CPUListView
import com.winlator.cmod.xenvironment.ImageFs
import com.winlator.cmod.xserver.XServer
import java.io.File
import java.util.Locale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

class TaskManagerDialog(private val activity: XServerDisplayActivity) : ContentDialog(activity, 0), OnGetProcessInfoListener {
    private val lock = Any()
    private val updateHandler = Handler(Looper.getMainLooper())
    private val sensorReader = SensorReader()
    
    // Compose states
    private var processList by mutableStateOf<List<ProcessInfo>>(emptyList())
    private val tempProcessList = ArrayList<ProcessInfo>()
    private var cpuUsagePercent by mutableStateOf(0)
    private val coreSpeeds = mutableStateListOf<Float>()
    private var usedMemBytes by mutableStateOf(0L)
    private var totalMemBytes by mutableStateOf(0L)
    private var cpuTemp by mutableStateOf("N/A")
    private var batteryTemperature by mutableStateOf(-1.0f)
    private var batteryLevel by mutableStateOf(-1)
    private var numProcessesText by mutableStateOf("0")
    private var activeProfilingResult by mutableStateOf<com.winlator.cmod.widget.ProfilingSession.Result?>(null)

    private val periodicUpdate = object : Runnable {
        override fun run() {
            if (!isShowing) return
            updateStats()
            updateHandler.postDelayed(this, 1000)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            batteryLevel = intent.getIntExtra("level", -1)
            val temp = intent.getIntExtra("temperature", -1)
            batteryTemperature = if (temp != -1) temp / 10.0f else -1.0f
        }
    }

    init {
        setCancelable(false)
        FileUtils.clear(getIconDir(activity))

        val contentView = getContentView() as? LinearLayout
        if (contentView != null) {
            contentView.removeAllViews()
            contentView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            contentView.setPadding(0, 0, 0, 0)

            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window?.decorView?.setPadding(0, 0, 0, 0)
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            val composeView = ComposeView(activity).apply {
                setContent {
                    // Диспетчер задач открывается поверх игрового кадра, поэтому он
                    // всегда тёмный: в светлой теме приложения стекло здесь было
                    // почти невидимым, а светлый текст — нечитаемым.
                    com.winlator.cmod.ui.theme.WinlatorTheme(forceDark = true) {
                        TaskManagerContent()
                    }
                }
            }
            contentView.addView(composeView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))

            // Set owners on all levels via reflection to guarantee Compose can resolve them in this dialog window
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
                setOwners(contentView)
                setOwners(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setOnDismissListener {
            updateHandler.removeCallbacks(periodicUpdate)
            activity.winHandler.setOnGetProcessInfoListener(null)
            try {
                activity.unregisterReceiver(batteryReceiver)
            } catch (e: IllegalArgumentException) {
                // Ignore
            }
        }
    }

    private fun updateStats() {
        synchronized(lock) {
            activity.winHandler.listProcesses()
        }

        // Update CPU Speeds and Usage
        val clockSpeeds = CPUStatus.getCurrentClockSpeeds()
        var totalClockSpeed = 0
        var maxClockSpeed: Short = 0
        
        coreSpeeds.clear()
        for (i in clockSpeeds.indices) {
            val clockSpeed = CPUStatus.getMaxClockSpeed(i)
            coreSpeeds.add(clockSpeeds[i] / 1000.0f)
            totalClockSpeed += clockSpeeds[i].toInt()
            if (clockSpeed > maxClockSpeed) {
                maxClockSpeed = clockSpeed
            }
        }

        val avgClockSpeed = if (clockSpeeds.isNotEmpty()) totalClockSpeed / clockSpeeds.size else 0
        cpuUsagePercent = if (maxClockSpeed > 0) {
            Math.min(100, Math.round((avgClockSpeed.toFloat() / maxClockSpeed.toFloat()) * 100.0f))
        } else 0

        cpuTemp = sensorReader.cpuTemperature ?: "N/A"

        // Update Memory info
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        totalMemBytes = memoryInfo.totalMem
        usedMemBytes = memoryInfo.totalMem - memoryInfo.availMem
    }

    override fun show() {
        updateHandler.removeCallbacks(periodicUpdate)
        updateStats()
        activity.winHandler.setOnGetProcessInfoListener(this)

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        activity.registerReceiver(batteryReceiver, filter)

        super.show()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        contentView?.setPadding(0, 0, 0, 0)
        updateHandler.postDelayed(periodicUpdate, 1000)
    }

    override fun onGetProcessInfo(index: Int, numProcesses: Int, processInfo: ProcessInfo) {
        activity.runOnUiThread {
            synchronized(lock) {
                if (index == 0) {
                    tempProcessList.clear()
                }
                tempProcessList.add(processInfo)
                if (index == numProcesses - 1 || numProcesses == 0) {
                    processList = ArrayList(tempProcessList)
                    numProcessesText = numProcesses.toString()
                }
            }
        }
    }

    @Composable
    private fun TaskManagerContent() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header / Window Title
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.task_manager),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                dismiss()
                                prompt(activity, R.string.new_task, "taskmgr.exe") { command ->
                                    activity.winHandler.exec(command)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(stringResource(R.string.new_task))
                        }
                        IconButton(onClick = { dismiss() }) {
                            Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }

                // Main body: Left: Process table, Right: Stats panel
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left: Process list Table
                    Card(
                        modifier = Modifier.weight(1.8f).fillMaxHeight(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Column Headers
                            Row(
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.process_name), Modifier.weight(2f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Status", Modifier.weight(0.8f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, alignment = Alignment.CenterHorizontally)
                                Text(stringResource(R.string.pid), Modifier.weight(0.6f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, alignment = Alignment.CenterHorizontally)
                                Text(stringResource(R.string.memory), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, alignment = Alignment.End)
                                Box(Modifier.width(40.dp)) // Menu action spacer
                            }
                            Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

                            if (processList.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_items_to_display), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(processList, key = { it.pid }) { process ->
                                        ProcessRow(process)
                                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                                    }
                                }
                            }
                        }
                    }

                    // Right: Performance & Stats
                    Card(
                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // CPU Card
                            HardwareStatsCard(
                                title = "CPU Usage",
                                percentage = cpuUsagePercent,
                                details = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Temp: $cpuTemp", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(4.dp))
                                        
                                        // Grid of CPU core frequencies (4 cores per row)
                                        val chunkedCores = coreSpeeds.mapIndexed { idx, speed -> idx to speed }.chunked(4)
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            chunkedCores.forEach { rowCores ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    rowCores.forEach { (idx, speed) ->
                                                        Card(
                                                            modifier = Modifier.weight(1f),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                            ),
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                                        ) {
                                                            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                                                Text("Core ${idx + 1}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                Text(String.format(Locale.ENGLISH, "%.1f GHz", speed), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                            }
                                                        }
                                                    }
                                                    if (rowCores.size < 4) {
                                                        repeat(4 - rowCores.size) {
                                                            Spacer(Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            )

                            // Memory Card
                            val memoryPercentage = if (totalMemBytes > 0) ((usedMemBytes.toDouble() / totalMemBytes) * 100).toInt() else 0
                            HardwareStatsCard(
                                title = stringResource(R.string.memory),
                                percentage = memoryPercentage,
                                details = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${StringUtils.formatBytes(usedMemBytes, false)} / ${StringUtils.formatBytes(totalMemBytes)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                                        Text("Battery Level: ${if (batteryLevel != -1) "$batteryLevel%" else "N/A"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Battery Temp: ${if (batteryTemperature != -1.0f) String.format(Locale.ENGLISH, "%.1f C", batteryTemperature) else "N/A"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            )
                        }
                    }
                }

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.processes)}: $numProcessesText",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Dialog of profiling results
                val result = activeProfilingResult
                if (result != null) {
                    Dialog(
                        onDismissRequest = { activeProfilingResult = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .heightIn(max = screenHeight * 0.9f)
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Dialog Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_result_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = { activeProfilingResult = null }) {
                                        Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }

                                // Chart
                                ProfilingChart(result = result)

                                // Text Summary
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val summaryText = remember(result) { result.format(context) }
                                    androidx.compose.foundation.text.selection.SelectionContainer {
                                        Text(
                                            text = summaryText,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                lineHeight = 18.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ProcessRow(process: ProcessInfo) {
        var showMenu by remember { mutableStateOf(false) }

        val window = remember(process.pid) {
            var foundWindow: com.winlator.cmod.xserver.Window? = null
            val xServer = activity.xServer
            try {
                xServer.lock(XServer.Lockable.WINDOW_MANAGER).use {
                    foundWindow = xServer.windowManager.findWindowWithProcessId(process.pid)
                }
            } catch (e: Exception) {
                // Ignore
            }
            foundWindow
        }
        val hasWindow = window != null

        val iconBitmap = remember(window) {
            if (window != null) {
                try {
                    activity.xServer.pixmapManager.getWindowIcon(window)
                } catch (e: Exception) {
                    null
                }
            } else null
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = "Process Icon",
                    modifier = Modifier.padding(end = 8.dp).size(18.dp)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.taskmgr_process),
                    contentDescription = "Process Icon",
                    modifier = Modifier.padding(end = 8.dp).size(18.dp)
                )
            }

            // Process Name
            Text(
                text = process.name + if (process.wow64Process) " *32" else "",
                modifier = Modifier.weight(2f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Status (Running / Background)
            Text(
                text = if (hasWindow) "Running" else "Bg",
                modifier = Modifier.weight(0.8f),
                fontSize = 13.sp,
                color = if (hasWindow) Color(0xFF4CAF50) else Color(0xFF888888),
                alignment = Alignment.CenterHorizontally
            )

            // PID
            Text(
                text = process.pid.toString(),
                modifier = Modifier.weight(0.6f),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                alignment = Alignment.CenterHorizontally
            )

            // Memory Usage
            Text(
                text = process.formattedMemoryUsage ?: "0 B",
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                alignment = Alignment.End
            )

            // Action / End Task menu
            Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Processor Affinity") },
                        onClick = {
                            showMenu = false
                            showProcessorAffinityDialog(process)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Bring to Front") },
                        onClick = {
                            showMenu = false
                            activity.winHandler.bringToFront(process.name)
                            dismiss()
                        }
                    )

                    val session = remember { com.winlator.cmod.widget.ProfilingSession.getInstance() }
                    val isProfilingActive = session.isActive
                    val activeForThis = isProfilingActive && process.name != null && process.name == session.targetProcessName
                    val profileText = if (activeForThis) stringResource(R.string.profile_process_stop) else stringResource(R.string.profile_process_start)
                    val profileEnabled = !isProfilingActive || activeForThis
                    DropdownMenuItem(
                        text = { Text(profileText) },
                        enabled = profileEnabled,
                        onClick = {
                            showMenu = false
                            toggleProfiling(process)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("End Process", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            confirm(activity, R.string.do_you_want_to_end_this_process) {
                                activity.winHandler.killProcess(process.name)
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun HardwareStatsCard(
        title: String,
        percentage: Int,
        details: @Composable () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("$percentage%", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(
                    progress = percentage / 100f,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(4.dp))
                details()
            }
        }
    }

    @Composable
    private fun ProfilingChart(result: com.winlator.cmod.widget.ProfilingSession.Result, modifier: Modifier = Modifier) {
        val samples = result.fpsTimeline
        val avgFps = result.avgFps
        val low1Fps = result.low1Fps
        val minFps = result.minFps
        val maxFps = result.maxFps

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF101820), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) return@Canvas

                val d = density

                val padL = 36f * d
                val padR = 12f * d
                val padT = 12f * d
                val padB = 20f * d
                val plotW = width - padL - padR
                val plotH = height - padT - padB

                if (samples == null || samples.size < 2 || maxFps.isNaN() || maxFps <= 0f) {
                    val paint = android.graphics.Paint().apply {
                        color = Color.Gray.toArgb()
                        textSize = 12f * d
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText("—", width / 2f, height / 2f, paint)
                    return@Canvas
                }

                val yTop = Math.max(60f, (Math.ceil(maxFps.toDouble() / 30.0) * 30.0).toFloat())
                val yBot = 0f

                // Draw grid lines
                val axisPaint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.0f * d
                    color = Color.White.copy(alpha = 0.2f).toArgb()
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f * d, 4f * d), 0f)
                }
                val textPaint = android.graphics.Paint().apply {
                    color = Color.White.copy(alpha = 0.7f).toArgb()
                    textSize = 10f * d
                }

                val gridLines = floatArrayOf(30f, 60f, 90f, 120f, 144f, 240f)
                for (gridFps in gridLines) {
                    if (gridFps > yTop) break
                    val y = padT + plotH - (gridFps - yBot) / (yTop - yBot) * plotH
                    drawContext.canvas.nativeCanvas.drawLine(padL, y, padL + plotW, y, axisPaint)
                    drawContext.canvas.nativeCanvas.drawText(
                        gridFps.toInt().toString(),
                        4f * d,
                        y + 4f * d,
                        textPaint
                    )
                }

                // Build line + fill paths
                val linePath = androidx.compose.ui.graphics.Path()
                val fillPath = androidx.compose.ui.graphics.Path()
                val n = samples.size
                for (i in 0 until n) {
                    val v = Math.max(0f, samples[i])
                    val x = padL + (i.toFloat() / (n - 1).toFloat()) * plotW
                    val y = padT + plotH - (v - yBot) / (yTop - yBot) * plotH
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, padT + plotH)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(padL + plotW, padT + plotH)
                fillPath.close()

                // Fill path
                drawPath(
                    path = fillPath,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x664CAF50), Color(0x114CAF50)),
                        start = Offset(0f, padT),
                        end = Offset(0f, padT + plotH)
                    )
                )

                // Line path
                drawPath(
                    path = linePath,
                    color = Color(0xFF4CAF50),
                    style = Stroke(width = 2f * d, join = StrokeJoin.Round, cap = StrokeCap.Round)
                )

                // Draw average line (cyan)
                if (!avgFps.isNaN()) {
                    val y = padT + plotH - (avgFps - yBot) / (yTop - yBot) * plotH
                    drawLine(
                        color = Color(0xFF03DAC5),
                        start = Offset(padL, y),
                        end = Offset(padL + plotW, y),
                        strokeWidth = 1.2f * d
                    )

                    val labelPaint = android.graphics.Paint().apply {
                        color = 0xFF03DAC5.toInt()
                        textSize = 10f * d
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "avg " + String.format(Locale.ENGLISH, "%.1f", avgFps),
                        padL + plotW - 60f * d,
                        y - 2f * d,
                        labelPaint
                    )
                }

                // Draw 1% low line (orange)
                if (!low1Fps.isNaN()) {
                    val y = padT + plotH - (low1Fps - yBot) / (yTop - yBot) * plotH
                    drawLine(
                        color = Color(0xFFFF9800),
                        start = Offset(padL, y),
                        end = Offset(padL + plotW, y),
                        strokeWidth = 1.2f * d
                    )

                    val labelPaint = android.graphics.Paint().apply {
                        color = 0xFFFF9800.toInt()
                        textSize = 10f * d
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "1% " + String.format(Locale.ENGLISH, "%.1f", low1Fps),
                        padL + plotW - 60f * d,
                        y + 12f * d,
                        labelPaint
                    )
                }
            }
        }
    }

    private fun showProcessorAffinityDialog(processInfo: ProcessInfo) {
        val dialog = ContentDialog(activity, R.layout.cpu_list_dialog)
        dialog.setTitle(processInfo.name)
        dialog.setIcon(R.drawable.icon_cpu)
        val cpuListView = dialog.findViewById<CPUListView>(R.id.CPUListView)
        cpuListView.setCheckedCPUList(processInfo.cpuList)
        dialog.setOnConfirmCallback {
            val winHandler = activity.winHandler
            winHandler.setProcessAffinity(processInfo.pid, ProcessHelper.getAffinityMask(cpuListView.checkedCPUList))
            updateStats()
        }
        dialog.show()
    }

    private fun toggleProfiling(processInfo: ProcessInfo) {
        val session = com.winlator.cmod.widget.ProfilingSession.getInstance()
        if (session.isActive) {
            val result = session.stop()
            if (result != null) activeProfilingResult = result
        } else {
            activity.enableProfilingHook()
            session.start(processInfo.name, processInfo.pid)
            android.widget.Toast.makeText(
                activity,
                activity.getString(R.string.profile_started, processInfo.name),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Helper text extensions to easily align elements inside grid rows
    @Composable
    private fun Text(text: String, modifier: Modifier, fontWeight: FontWeight, fontSize: androidx.compose.ui.unit.TextUnit, color: Color, alignment: Alignment.Horizontal) {
        Box(modifier = modifier, contentAlignment = when (alignment) {
            Alignment.Start -> Alignment.CenterStart
            Alignment.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }) {
            Text(text = text, fontWeight = fontWeight, fontSize = fontSize, color = color)
        }
    }

    @Composable
    private fun Text(text: String, modifier: Modifier, fontSize: androidx.compose.ui.unit.TextUnit, color: Color, alignment: Alignment.Horizontal) {
        Box(modifier = modifier, contentAlignment = when (alignment) {
            Alignment.Start -> Alignment.CenterStart
            Alignment.End -> Alignment.CenterEnd
            else -> Alignment.Center
        }) {
            Text(text = text, fontSize = fontSize, color = color)
        }
    }

    companion object {
        fun getIconDir(context: Context): File {
            val iconDir = File(ImageFs.find(context).rootDir, "home/xuser/.local/share/icons/taskmgr")
            if (!iconDir.isDirectory) iconDir.mkdirs()
            return iconDir
        }
    }
}
