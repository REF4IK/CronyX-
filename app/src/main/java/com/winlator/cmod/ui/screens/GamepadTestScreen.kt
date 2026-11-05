package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSectionHeader
import com.winlator.cmod.ui.components.GlassSolidPill
import androidx.compose.foundation.shape.RoundedCornerShape
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.winlator.cmod.R
import java.util.Locale

@Composable
fun JoystickViewCompose(x: Float, y: Float, dotColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Draw outer circle
        drawCircle(
            color = Color(0xFF666666),
            radius = radius - 4f,
            center = center,
            style = Stroke(width = 4f)
        )

        // Draw crosshair lines
        drawLine(
            color = Color(0xFF444444),
            start = Offset(4f, center.y),
            end = Offset(size.width - 4f, center.y),
            strokeWidth = 2f
        )
        drawLine(
            color = Color(0xFF444444),
            start = Offset(center.x, 4f),
            end = Offset(center.x, size.height - 4f),
            strokeWidth = 2f
        )

        // Draw dot position. x and y are -1.0 to 1.0.
        val dotRadius = radius * 0.2f
        val maxOffset = radius - dotRadius - 4f
        val dotCenter = Offset(
            center.x + x * maxOffset,
            center.y + y * maxOffset
        )
        drawCircle(
            color = dotColor,
            radius = dotRadius,
            center = dotCenter
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamepadTestScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var deviceInfo by remember { mutableStateOf("No gamepad detected\nConnect a gamepad and press buttons") }
    var leftStickX by remember { mutableStateOf(0f) }
    var leftStickY by remember { mutableStateOf(0f) }
    var rightStickX by remember { mutableStateOf(0f) }
    var rightStickY by remember { mutableStateOf(0f) }
    var leftTrigger by remember { mutableStateOf(0f) }
    var rightTrigger by remember { mutableStateOf(0f) }
    val pressedButtons = remember { mutableStateListOf<String>() }

    var vibrator: Vibrator? by remember { mutableStateOf(null) }
    var vibrationSupported by remember { mutableStateOf(false) }
    var vibrationStatus by remember { mutableStateOf("Vibration: Idle") }
    var vibrationStatusColor by remember { mutableStateOf(Color(0xFF666666)) }

    LaunchedEffect(Unit) {
        val deviceIds = InputDevice.getDeviceIds()
        for (deviceId in deviceIds) {
            val dev = InputDevice.getDevice(deviceId)
            if (dev != null && (
                (dev.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (dev.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            )) {
                deviceInfo = "${dev.name}\nVendor: ${String.format("%04X", dev.vendorId)} Product: ${String.format("%04X", dev.productId)}"
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dev.vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    dev.vibrator
                }
                vibrator = v
                vibrationSupported = v?.hasVibrator() == true
                if (vibrationSupported) {
                    vibrationStatus = "Vibration: Idle"
                    vibrationStatusColor = Color(0xFF666666)
                } else {
                    vibrationStatus = "Vibration: Not Supported on Gamepad"
                    vibrationStatusColor = Color(0xFFFF5722)
                }
                break
            }
        }
    }

    // Hiddden View to catch gamepad keys & axes
    AndroidView(
        factory = { context ->
            val view = object : View(context) {
                override fun onGenericMotionEvent(event: MotionEvent): Boolean {
                    if ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                        event.action == MotionEvent.ACTION_MOVE
                    ) {
                        event.device?.let { dev ->
                            deviceInfo = "${dev.name}\nVendor: ${String.format("%04X", dev.vendorId)} Product: ${String.format("%04X", dev.productId)}"
                            if (vibrator == null) {
                                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    dev.vibratorManager?.defaultVibrator
                                } else {
                                    @Suppress("DEPRECATION")
                                    dev.vibrator
                                }
                                vibrator = v
                                vibrationSupported = v?.hasVibrator() == true
                                if (!vibrationSupported) {
                                    vibrationStatus = "Vibration: Not Supported on Gamepad"
                                    vibrationStatusColor = Color(0xFFFF5722)
                                }
                            }
                        }

                        leftStickX = getCenteredAxis(event, MotionEvent.AXIS_X)
                        leftStickY = getCenteredAxis(event, MotionEvent.AXIS_Y)
                        rightStickX = getCenteredAxis(event, MotionEvent.AXIS_Z)
                        rightStickY = getCenteredAxis(event, MotionEvent.AXIS_RZ)
                        leftTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
                        rightTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
                        return true
                    }
                    return super.onGenericMotionEvent(event)
                }

                override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
                    event.device?.let { dev ->
                        deviceInfo = "${dev.name}\nVendor: ${String.format("%04X", dev.vendorId)} Product: ${String.format("%04X", dev.productId)}"
                    }
                    if (isGamepadButton(keyCode)) {
                        val btn = getButtonName(keyCode)
                        if (!pressedButtons.contains(btn)) pressedButtons.add(btn)
                        return true
                    }
                    return super.onKeyDown(keyCode, event)
                }

                override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
                    if (isGamepadButton(keyCode)) {
                        pressedButtons.remove(getButtonName(keyCode))
                        return true
                    }
                    return super.onKeyUp(keyCode, event)
                }

                private fun getCenteredAxis(event: MotionEvent, axis: Int): Float {
                    val range = event.device?.getMotionRange(axis, event.source) ?: return 0f
                    val value = event.getAxisValue(axis)
                    return if (Math.abs(value) > range.flat) value else 0f
                }

                private fun isGamepadButton(keyCode: Int): Boolean {
                    return when (keyCode) {
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
                        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
                        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
                        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
                        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
                        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
                        KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> true
                        else -> false
                    }
                }

                private fun getButtonName(keyCode: Int): String {
                    return when (keyCode) {
                        KeyEvent.KEYCODE_BUTTON_A -> "A"
                        KeyEvent.KEYCODE_BUTTON_B -> "B"
                        KeyEvent.KEYCODE_BUTTON_X -> "X"
                        KeyEvent.KEYCODE_BUTTON_Y -> "Y"
                        KeyEvent.KEYCODE_BUTTON_L1 -> "LB"
                        KeyEvent.KEYCODE_BUTTON_R1 -> "RB"
                        KeyEvent.KEYCODE_BUTTON_L2 -> "LT"
                        KeyEvent.KEYCODE_BUTTON_R2 -> "RT"
                        KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
                        KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
                        KeyEvent.KEYCODE_BUTTON_START -> "START"
                        KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
                        KeyEvent.KEYCODE_BUTTON_MODE -> "HOME"
                        KeyEvent.KEYCODE_DPAD_UP -> "D-UP"
                        KeyEvent.KEYCODE_DPAD_DOWN -> "D-DOWN"
                        KeyEvent.KEYCODE_DPAD_LEFT -> "D-LEFT"
                        KeyEvent.KEYCODE_DPAD_RIGHT -> "D-RIGHT"
                        else -> "UNKNOWN"
                    }
                }
            }
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
            view
        },
        modifier = Modifier.size(1.dp)
    )

    fun startVibration() {
        val v = vibrator ?: return
        if (!vibrationSupported) return
        vibrationStatus = "Vibration: ON"
        vibrationStatusColor = Color(0xFF4CAF50)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000), intArrayOf(0, 255), 0))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 1000), 0)
        }
    }

    fun stopVibration() {
        val v = vibrator ?: return
        if (!vibrationSupported) return
        vibrationStatus = "Vibration: Idle"
        vibrationStatusColor = Color(0xFF666666)
        v.cancel()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device Info Card
        GlassSectionHeader(title = "Gamepad Info")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            border = glassCardBorder(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = deviceInfo, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Joystick Joysticks Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(text = "Left Stick", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                JoystickViewCompose(
                    x = leftStickX,
                    y = leftStickY,
                    dotColor = Color(0xFF4CAF50),
                    modifier = Modifier.size(140.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = String.format(Locale.US, "X: %.2f Y: %.2f", leftStickX, leftStickY), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(text = "Right Stick", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                JoystickViewCompose(
                    x = rightStickX,
                    y = rightStickY,
                    dotColor = Color(0xFF2196F3),
                    modifier = Modifier.size(140.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = String.format(Locale.US, "X: %.2f Y: %.2f", rightStickX, rightStickY), fontSize = 12.sp)
            }
        }

        // Triggers Axis Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Card(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                border = glassCardBorder(),
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Left Trigger (LT)", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f", leftTrigger),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Card(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
                shape = RoundedCornerShape(GlassTokens.CardRadius),
                border = glassCardBorder(),
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Right Trigger (RT)", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f", rightTrigger),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Pressed Buttons Display
        GlassSectionHeader(title = "Pressed Buttons")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            border = glassCardBorder(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (pressedButtons.isEmpty()) "None" else pressedButtons.joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Vibration Testing Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = glassTitleColor()),
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            border = glassCardBorder(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Gamepad Haptic",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = vibrationStatus, color = vibrationStatusColor, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                GlassSolidPill(
                    onClick = {},
                    enabled = vibrationSupported,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(vibrationSupported) {
                            detectTapGestures(
                                onPress = {
                                    startVibration()
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        stopVibration()
                                    }
                                }
                            )
                        },
                ) {
                    Text("Hold to Test Vibration")
                }
            }
        }
    }
}
