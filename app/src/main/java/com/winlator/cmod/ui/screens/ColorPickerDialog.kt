package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.glassCardFill
import com.winlator.cmod.ui.components.glassCardBorder
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.GlassFieldShape
import com.winlator.cmod.ui.components.glassFieldColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Свободный выбор цвета (HSV): полосы Hue / Saturation / Brightness + hex-поле.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var hexText by remember { mutableStateOf(String.format("#%06X", 0xFFFFFF and initialColor)) }

    val currentColor = Color.hsv(hue, saturation, value)
    val currentArgb = currentColor.toArgb()

    fun applyHex(raw: String) {
        val clean = raw.trim().removePrefix("#")
        if (clean.length != 6) return
        val parsed = clean.toIntOrNull(16) ?: return
        val c = Color(parsed)
        hue = c.hueFromColor()
        saturation = c.saturationFromColor()
        value = c.valueFromColor()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(GlassTokens.CardRadius),
            colors = CardDefaults.cardColors(containerColor = glassCardFill(), contentColor = MaterialTheme.colorScheme.onSurface),
            border = glassCardBorder(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Custom Color",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(currentColor, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = {
                            hexText = it
                            if (it.length == 6 || it.length == 7) applyHex(it)
                        },
                        singleLine = true,
                        label = { Text("HEX") },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),

                        shape = GlassFieldShape,
                        colors = glassFieldColors())
                }

                GradientBar(
                    gradient = Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
                        ),
                    ),
                    fraction = hue / 360f,
                ) { fraction ->
                    hue = (fraction * 360f).coerceIn(0f, 359.9f)
                    hexText = String.format("#%06X", 0xFFFFFF and currentArgb)
                }

                GradientBar(
                    gradient = Brush.horizontalGradient(
                        listOf(Color.White, Color.hsv(hue, 1f, value)),
                    ),
                    fraction = saturation,
                ) { fraction ->
                    saturation = fraction.coerceIn(0f, 1f)
                    hexText = String.format("#%06X", 0xFFFFFF and currentArgb)
                }

                GradientBar(
                    gradient = Brush.horizontalGradient(
                        listOf(Color.Black, Color.hsv(hue, saturation, 1f)),
                    ),
                    fraction = value,
                ) { fraction ->
                    value = fraction.coerceIn(0f, 1f)
                    hexText = String.format("#%06X", 0xFFFFFF and currentArgb)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    GlassSolidPill(
                        onClick = {
                        onColorSelected(currentArgb)
                        onDismiss()
                    },
                    ) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun GradientBar(
    gradient: Brush,
    fraction: Float,
    onFractionChanged: (Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onFractionChanged(offset.x / size.width) },
                    onDrag = { change, _ ->
                        onFractionChanged(change.position.x / size.width)
                    },
                )
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
        ) {
            drawRect(brush = gradient, size = size)
            val markerX = (fraction.coerceIn(0f, 1f)) * size.width
            drawLine(
                color = Color.White,
                start = Offset(markerX, 0f),
                end = Offset(markerX, size.height),
                strokeWidth = 3f,
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.6f),
                start = Offset(markerX - 1f, 0f),
                end = Offset(markerX - 1f, size.height),
                strokeWidth = 1f,
            )
        }
    }
}

private fun Color.hueFromColor(): Float {
    val h = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), h)
    return h[0]
}

private fun Color.saturationFromColor(): Float {
    val h = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), h)
    return h[1]
}

private fun Color.valueFromColor(): Float {
    val h = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), h)
    return h[2]
}
