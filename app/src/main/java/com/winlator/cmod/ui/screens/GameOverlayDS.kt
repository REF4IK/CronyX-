package com.winlator.cmod.ui.screens

import com.winlator.cmod.ui.components.GlassSolidPill
import com.winlator.cmod.ui.components.GlassSwitch
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object GameOverlayColors {
    val PanelBg = Color(0xE6262626)
    val RailBg = Color(0xE61E1E1E)
    val CardBg = Color(0xFF333333)
    val CardBgSoft = Color(0xFF2E2E2E)
    val Accent = Color(0xFFFFC107)
    val AccentDim = Color(0xFF8A6D00)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val Track = Color(0xFF4A4A4A)
    val Danger = Color(0xFFFF6B6B)
}

val GamePanelShape = RoundedCornerShape(22.dp)
val GameCardShape = RoundedCornerShape(14.dp)

@Composable
fun GamePanelShell(
    footer: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            content = content
        )
        if (footer != null) {
            Spacer(Modifier.height(10.dp))
            footer()
        }
    }
}

@Composable
fun GameSectionTitle(text: String) {
    Text(
        text = text,
        color = GameOverlayColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
fun GameSliderRow(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = GameOverlayColors.TextPrimary, fontSize = 13.sp)
            Text(valueText, color = GameOverlayColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                activeTrackColor = GameOverlayColors.Accent,
                inactiveTrackColor = GameOverlayColors.Track,
                thumbColor = GameOverlayColors.Accent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun GameSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GameCardShape)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = GameOverlayColors.TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = GameOverlayColors.Accent,
                checkedThumbColor = Color.Black,
                uncheckedTrackColor = GameOverlayColors.Track,
                uncheckedThumbColor = Color.White
            ),
            modifier = Modifier.height(30.dp)
        )
    }
}

@Composable
fun GameCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GameCardShape)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Переключатель вместо чекбокса, но в палитре самого оверлея: у него своя
        // система цветов, стеклянные токены сюда не подходят.
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = GameOverlayColors.Accent,
                uncheckedThumbColor = GameOverlayColors.TextSecondary,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = GameOverlayColors.TextSecondary,
            ),
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = GameOverlayColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun GameChipGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { opt ->
                    val globalIdx = options.indexOf(opt)
                    val selected = globalIdx == selectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) GameOverlayColors.Accent else GameOverlayColors.CardBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(globalIdx) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            opt,
                            color = if (selected) Color.Black else GameOverlayColors.TextPrimary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun GameRing(
    centerTop: String,
    centerBottom: String,
    progress: Float,
    size: Dp = 92.dp,
    ringColor: Color = GameOverlayColors.Accent
) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val arcTopLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = Color(0xFF4A4A4A),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = (progress.coerceIn(0f, 1f)) * 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, color = GameOverlayColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            Text(centerBottom, color = GameOverlayColors.TextSecondary, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
fun GameFooterOkCancel(
    okText: String = "OK",
    cancelText: String = "Отмена",
    onCancel: () -> Unit,
    onOk: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) {
            Text(cancelText, color = GameOverlayColors.TextSecondary, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(8.dp))
        GlassSolidPill(
            onClick = onOk,
            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 10.dp),
        ) {
            Text(okText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GameRailItem(
    iconRes: Int,
    label: String,
    contentDesc: String,
    selected: Boolean,
    dot: Boolean = false,
    onClick: () -> Unit,
    customIcon: (@Composable (tint: Color) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (selected) GameOverlayColors.Accent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            val tint = if (selected) Color.Black else GameOverlayColors.TextPrimary
            if (customIcon != null) {
                customIcon(tint)
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = contentDesc,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (dot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = 6.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) GameOverlayColors.Accent else GameOverlayColors.TextSecondary,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Иконка FPS из SVG: скруглённая рамка + текст FPS
@Composable
fun FpsBadgeIcon(tint: Color) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(22.dp)
            .border(2.dp, tint, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "FPS",
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}
