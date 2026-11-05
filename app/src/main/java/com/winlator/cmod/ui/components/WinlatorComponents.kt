package com.winlator.cmod.ui.components

import com.winlator.cmod.ui.components.GlassSectionHeader
import com.winlator.cmod.ui.components.glassTitleColor
import com.winlator.cmod.ui.components.GlassTokens
import com.winlator.cmod.ui.components.glassAccent
import com.winlator.cmod.ui.components.GlassSolidPill
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Карточка-контейнер для группировки настроек.
 * Рендерится стеклянной панелью (GlassSurface); разделители между строками
 * рисуют сами строки, пока находятся внутри группы (LocalGlassGrouped).
 */
@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier.fillMaxWidth(), content = content)
}

/**
 * Заголовок секции. По стилю GameHub стоит над карточкой, крупный bold.
 */
@Composable
fun SectionHeader(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    GlassSectionHeader(
        title = title,
        modifier = modifier,
        action = if (icon != null) {
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glassAccent(),
                    modifier = Modifier.size(20.dp),
                )
            }
        } else null,
    )
}

/**
 * Строка с переключателем (Switch).
 */
@Composable
fun SettingsRowSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSwitchRow(
        title = title,
        desc = subtitle,
        icon = icon,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
    )
}

/**
 * Кликабельная строка с иконкой и шевроном.
 */
@Composable
fun SettingsRowClick(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    value: String = "",
) {
    GlassRow(
        title = title,
        desc = subtitle,
        value = value,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Информационная строка (не кликабельная).
 */
@Composable
fun SettingsRowInfo(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    value: String = "",
) {
    GlassRow(
        title = title,
        desc = subtitle,
        value = value,
        icon = icon,
        onClick = null,
        showChevron = false,
        modifier = modifier,
    )
}

/**
 * Карточка с заголовком секции сверху.
 */
@Composable
fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        if (title != null) {
            SectionHeader(title = title, icon = icon)
        }
        SettingsGroupCard(content = content)
    }
}

/**
 * Кнопка действия с иконкой. Акцентная — со стеклянным свечением.
 */
@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    GlassSolidPill(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontWeight = FontWeight.Medium)
    }
}

/**
 * Строка-переход в стиле хаба «РС-движок»: только название и шеврон,
 * без иконки и описания.
 */
@Composable
fun GlassNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .glassGroupDivider()
            .padding(
                horizontal = GlassTokens.RowPaddingH,
                vertical = GlassTokens.RowPaddingV + 2.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = GlassTokens.TitleSize,
            fontWeight = FontWeight.Medium,
            color = glassTitleColor(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = glassChevronColor(),
        )
    }
}
