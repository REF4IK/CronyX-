package com.winlator.cmod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Стеклянная система в стиле GameHub: полупрозрачные панели со светящейся
 * гранью, сгруппированные строки с разделителями, синий акцент.
 *
 * Стекло строится из альфы поверх фона/обоев, поэтому настоящеё размытия
 * не требуется: под обоями (ThemePrefs.UI_WALLPAPER + UI_WALLPAPER_BLUR)
 * панели просвечивают сами.
 */
object GlassTokens {
    /** Радиус большой карточки. 26dp по референсу. */
    val CardRadius: Dp = 26.dp
    val InnerRadius: Dp = 18.dp
    val RowPaddingH: Dp = 20.dp
    val RowPaddingV: Dp = 14.dp
    val GroupPaddingV: Dp = 4.dp
    val ListGap: Dp = 16.dp

    /** Текст строки: заголовок / описание / значение / шеврон. */
    val TitleSize = 18.sp
    val DescSize = 14.sp
    val ValueSize = 16.sp
    val SectionSize = 22.sp

    /** Акцент GameHub. Берётся из colorScheme.primary, если он валиден. */
    val AccentFallback = Color(0xFF2E9BF0)
}

/** Тёмное ли стекло: зависит от яркости фона темы, а не от флага. */
@Composable
fun glassIsDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Ставится равным true внутри [GlassSurface]/[SettingsGroupCard].
 * Строка, видя его, рисует разделитель снизу: линии соседних строк
 * совпадают с границами карточки, поэтому последняя уходит под её рамку.
 */
val LocalGlassGrouped = compositionLocalOf { false }

/**
 * Стеклянная заливка: вертикальный градиент от более светлой грани к телу.
 * В светлой теме инвертируется — иначе белое на белом невидимо.
 */
@Composable
fun glassFillBrush(): Brush {
    val dark = glassIsDark()
    return remember(dark) {
        if (dark) Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.095f), Color.White.copy(alpha = 0.045f)),
        ) else Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.45f)),
        )
    }
}

@Composable
fun glassBorder(): BorderStroke {
    val dark = glassIsDark()
    return remember(dark) {
        BorderStroke(
            1.dp,
            if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.12f),
        )
    }
}

@Composable
fun glassDividerColor(): Color =
    if (glassIsDark()) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.07f)

@Composable
fun glassTitleColor(): Color =
    if (glassIsDark()) Color(0xFFF2F2F4) else Color(0xFF15161A)

@Composable
fun glassDescColor(): Color =
    if (glassIsDark()) Color(0xFF9A9AA0) else Color(0xFF63666D)

@Composable
fun glassValueColor(): Color =
    if (glassIsDark()) Color(0xFFC9C9CE) else Color(0xFF454A52)

@Composable
fun glassChevronColor(): Color =
    if (glassIsDark()) Color(0xFF6E6E74) else Color(0xFF9AA0A8)

/**
 * Базовая стеклянная панель. Всё стекло в приложении идёт отсюда.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassTokens.CardRadius),
    padded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(
        LocalGlassGrouped provides true,
        // Card сам подставлял contentColor, а голый background — нет, поэтому
        // Text без явного цвета наследовал неопределённый цвет и был почти чёрным.
        LocalContentColor provides glassTitleColor(),
    ) {
        Column(
            modifier = modifier
                .shadow(10.dp, shape, clip = false)
                .clip(shape)
                .background(glassFillBrush())
                .border(glassBorder(), shape)
                .then(if (padded) Modifier.padding(vertical = GlassTokens.GroupPaddingV) else Modifier),
            content = content,
        )
    }
}

/**
 * Разделитель группы: рисуется нижней границей строки, когда строка
 * находится внутри [GlassSurface].
 */
@Composable
internal fun Modifier.glassGroupDivider(): Modifier {
    if (!LocalGlassGrouped.current) return this
    val c = glassDividerColor()
    val inset = GlassTokens.RowPaddingH
    return drawBehind {
        val insetPx = inset.toPx()
        drawRect(
            color = c,
            topLeft = Offset(insetPx, size.height - 1.dp.toPx()),
            size = Size(size.width - insetPx, 1.dp.toPx()),
        )
    }
}

/**
 * Группа строк в одной стеклянной карточке. Разделители рисуют сами
 * строки (см. [glassGroupDivider]), поэтому здесь они не нужны.
 *
 * GlassList(items = listOf("a", "b")) { item -> GlassRow(title = item) }
 */
@Composable
fun <T> GlassList(
    items: List<T>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassTokens.CardRadius),
    itemContent: @Composable (T) -> Unit,
) {
    GlassSurface(modifier = modifier, shape = shape) {
        items.forEach { item -> itemContent(item) }
    }
}

/** Разделитель между строками группы. */
@Composable
fun GlassDivider(
    modifier: Modifier = Modifier,
    insetStart: Dp = GlassTokens.RowPaddingH,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(start = insetStart),
        content = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(glassDividerColor()),
            )
        },
    )
}

/**
 * Строка настроек: заголовок + описание слева, значение/шеврон или свой
 * trailing справа. Клик по всей строке — через [onClick].
 */
@Composable
fun GlassRow(
    title: String,
    modifier: Modifier = Modifier,
    desc: String = "",
    value: String = "",
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleC = if (enabled) glassTitleColor() else glassTitleColor().copy(alpha = 0.38f)
    val descC = if (enabled) glassDescColor() else glassDescColor().copy(alpha = 0.38f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .glassGroupDivider()
            .padding(
                horizontal = GlassTokens.RowPaddingH,
                vertical = GlassTokens.RowPaddingV,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) GlassIconBadge(icon = icon, enabled = enabled)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = GlassTokens.TitleSize,
                fontWeight = FontWeight.Medium,
                color = titleC,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (desc.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = desc,
                    fontSize = GlassTokens.DescSize,
                    color = descC,
                    lineHeight = 19.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else {
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    fontSize = GlassTokens.ValueSize,
                    color = glassValueColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showChevron) GlassChevron(enabled = enabled)
        }
    }
}

/** Строка со стеклянным переключателем. */
@Composable
fun GlassSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    desc: String = "",
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    GlassRow(
        title = title,
        desc = desc,
        icon = icon,
        enabled = enabled,
        modifier = modifier,
        onClick = if (enabled) { { onCheckedChange(!checked) } } else null,
        showChevron = false,
        trailing = { GlassSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
    )
}

/** Синий светящийся Switch в стиле GameHub. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = glassAccent()
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color(0xFFD8D8DC),
            uncheckedTrackColor = if (glassIsDark()) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f),
            uncheckedBorderColor = Color.Transparent,
            disabledCheckedThumbColor = Color.White.copy(alpha = 0.5f),
            disabledCheckedTrackColor = accent.copy(alpha = 0.4f),
            disabledUncheckedThumbColor = Color(0xFFD8D8DC).copy(alpha = 0.4f),
            disabledUncheckedTrackColor = Color.Gray.copy(alpha = 0.2f),
        ),
    )
}

/** Шеврон строки. */
@Composable
fun GlassChevron(modifier: Modifier = Modifier, enabled: Boolean = true) {
    Icon(
        imageVector = Icons.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = if (enabled) glassChevronColor() else glassChevronColor().copy(alpha = 0.38f),
        modifier = modifier.size(24.dp),
    )
}

/** Круглая плашка с иконкой слева от строки (как «Виртуальные кнопки»). */
@Composable
fun GlassIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    tint: Color? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (glassIsDark()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: if (enabled) glassValueColor() else glassValueColor().copy(alpha = 0.38f),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Заголовок секции — стоит НАД карточкой, не внутри. */
@Composable
fun GlassSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.padding(top = 18.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = GlassTokens.SectionSize,
            fontWeight = FontWeight.Bold,
            color = glassTitleColor(),
            modifier = Modifier.weight(1f),
        )
        if (action != null) action()
    }
}

/** Акцентный цвет стекла: primary темы, если он читается, иначе фирменный синий. */
@Composable
fun glassAccent(): Color {
    val primary = MaterialTheme.colorScheme.primary
    // Очень тёмный primary (напр. teal #1A6C59 на чёрном) даёт нечитаемый трек
    return if (primary.luminance() < 0.18f) GlassTokens.AccentFallback else primary
}

/** Пилюля-чип (как «UID 1420106», «Не верифицирован», «Недавние»). */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    accent: Boolean = false,
) {
    val shape = CircleShape
    val bg = when {
        accent -> glassAccent().copy(alpha = if (glassIsDark()) 0.22f else 0.14f)
        else -> if (glassIsDark()) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    }
    val fg = when {
        accent -> glassAccent()
        else -> glassValueColor()
    }
    Row(
        modifier = modifier
            .shadow(if (accent) 8.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(bg)
            .border(
                width = if (accent) 1.dp else 0.dp,
                color = if (accent) glassAccent().copy(alpha = 0.6f) else Color.Transparent,
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg, maxLines = 1)
    }
}

/**
 * Акцентная светящаяся кнопка-пилюля («Рекоменд.» в шапке РС-движка).
 */
@Composable
fun GlassGlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = glassAccent()
    val shape = CircleShape
    Box(
        modifier = modifier
            .shadow(elevation = if (enabled) 12.dp else 0.dp, shape = shape, clip = false, spotColor = accent, ambientColor = accent)
            .clip(shape)
            .background(if (enabled) accent else accent.copy(alpha = 0.38f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * Подложка для M3 ModalBottomSheet: прозрачная, чтобы стекло ниже само
 * дало границу и градиент.
 *
 * ModalBottomSheet(containerColor = glassSheetContainerColor(), ...) { GlassSheetContent { ... } }
 */
@Composable
fun glassSheetContainerColor(): Color = Color.Transparent

/** Стеклянная подложка bottom sheet: скруглённый верх + градиент + грань. */
@Composable
fun GlassSheetContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    CompositionLocalProvider(
        LocalGlassGrouped provides true,
        LocalContentColor provides glassTitleColor(),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(glassSheetFill())
                .border(glassBorder(), shape)
                // Лист без скролла и без нижнего инсета обрезается и уезжает
                // под системную навигацию, когда пунктов много.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 8.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

/**
 * Стеклянная пилюля с произвольным содержимым — замена OutlinedButton.
 * Сигнатура совместима с оригиналом (onClick / modifier / enabled / content),
 * поэтому вызовы переименовываются без правки аргументов.
 */
@Composable
fun GlassPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(GlassTokens.CardRadius)
    CompositionLocalProvider(
        LocalContentColor provides if (enabled) glassTitleColor() else glassTitleColor().copy(alpha = 0.38f),
    ) {
        Row(
            modifier = modifier
                .shadow(if (enabled) 6.dp else 0.dp, shape, clip = false)
                .clip(shape)
                .background(glassFillBrush())
                .border(glassBorder(), shape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * Заполненная акцентом пилюля — замена Material Button.
 */
@Composable
fun GlassSolidPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(GlassTokens.CardRadius)
    val accent = glassAccent()
    CompositionLocalProvider(
        LocalContentColor provides if (enabled) Color.White else Color.White.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = modifier
                .shadow(if (enabled) 10.dp else 0.dp, shape, clip = false, spotColor = accent, ambientColor = accent)
                .clip(shape)
                .background(if (enabled) accent else accent.copy(alpha = 0.38f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** Отступ-«разделитель» между группами карточек. */
@Composable
fun GlassGroupGap() {
    Spacer(Modifier.height(GlassTokens.ListGap))
}

/**
 * Строка со слайдером: сверху заголовок и значение, ниже — сам ползунок.
 * Используется там, где в референсе строка с числовым параметром.
 */
@Composable
fun GlassSliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    desc: String = "",
) {
    val accent = glassAccent()
    Column(
        modifier
            .fillMaxWidth()
            .glassGroupDivider()
            .padding(
                start = GlassTokens.RowPaddingH,
                end = GlassTokens.RowPaddingH,
                top = GlassTokens.RowPaddingV - 4.dp,
                bottom = GlassTokens.RowPaddingV - 4.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = GlassTokens.TitleSize,
                fontWeight = FontWeight.Medium,
                color = if (enabled) glassTitleColor() else glassTitleColor().copy(alpha = 0.38f),
                maxLines = 2,
            )
            Text(
                text = valueText,
                fontSize = GlassTokens.ValueSize,
                color = glassValueColor(),
            )
        }
        if (desc.isNotEmpty()) {
            Text(
                text = desc,
                fontSize = GlassTokens.DescSize,
                color = glassDescColor(),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = if (glassIsDark()) Color.White.copy(alpha = 0.14f)
                                      else Color.Black.copy(alpha = 0.12f),
            ),
        )
    }
}

/**
 * Полноширинная стеклянная пилюля-действие — как «Установить новый компонент»
 * и «Предпросмотр…» в референсе. Это НЕ FAB и не outlined-кнопка.
 */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val shape = RoundedCornerShape(GlassTokens.CardRadius)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(if (enabled) 6.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(glassFillBrush())
            .border(glassBorder(), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = GlassTokens.RowPaddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = glassAccent(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            fontSize = GlassTokens.TitleSize,
            fontWeight = FontWeight.Medium,
            color = if (enabled) glassTitleColor() else glassTitleColor().copy(alpha = 0.38f),
            maxLines = 1,
        )
    }
}

/**
 * Подложка маленькой плашки (иконка, бейдж, превью). Заменяет плотный
 * surfaceVariant: в стекло-стиле такие плашки полупрозрачные.
 */
@Composable
fun glassBadgeFill(): Color =
    if (glassIsDark()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)

/**
 * Цвета ползунка по стекло-стилю: акцентные дорожка и ручка, тёмный
 * неактивный остаток. Даётся в `colors =` у Slider.
 */
@Composable
fun glassSliderColors(): SliderColors {
    val accent = glassAccent()
    return SliderDefaults.colors(
        thumbColor = accent,
        activeTrackColor = accent,
        inactiveTrackColor = if (glassIsDark()) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.12f),
        disabledThumbColor = accent.copy(alpha = 0.38f),
        disabledActiveTrackColor = accent.copy(alpha = 0.24f),
    )
}

/**
 * Заливка стеклянной панели диалога/оверлея. Почти непрозрачная: диалог
 * живёт поверх затемнения, и сильная альфа сделала бы текст нечитаемым.
 */
@Composable
fun glassCardFill(): Color =
    // Полупрозрачная панель: Card не умеет градиент, поэтому берётся среднее
    // значение того же стекла, что рисует GlassSurface. Непрозрачные поверхности
    // (диалоги, листы, боковые панели) используют glassSheetFill, иначе сквозь
    // них просвечивает интерфейс.
    if (glassIsDark()) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.72f)

/**
 * Заливка листов (bottom sheet). Почти непрозрачная: лист перекрывает
 * интерфейс, и с полупрозрачным градиентом сквозь него были видны нижняя
 * навигация и FAB (баг, замеченный на скриншотах).
 */
@Composable
fun glassSheetFill(): Color =
    if (glassIsDark()) Color(0xFF141419) else Color(0xFFFFFFFF)

/** Грань стеклянной панели диалога. */
@Composable
fun glassCardBorder(): BorderStroke = glassBorder()

/**
 * Форма текстовых полей по стекло-стилю: скругление 18dp вместо прямого угла.
 */
val GlassFieldShape: Shape = RoundedCornerShape(GlassTokens.InnerRadius)

/**
 * Цвета outlined-поля в стекло-стиле: полупрозрачная подложка, акцентная
 * рамка в фокусе. Даётся в `colors =` у OutlinedTextField.
 */
@Composable
fun glassFieldColors(): TextFieldColors {
    val dark = glassIsDark()
    val accent = glassAccent()
    val idle = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.16f)
    val fill = if (dark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
    return TextFieldDefaults.colors(
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = fill,
        cursorColor = accent,
        errorCursorColor = MaterialTheme.colorScheme.error,
        focusedIndicatorColor = accent,
        unfocusedIndicatorColor = idle,
        disabledIndicatorColor = idle.copy(alpha = 0.4f),
        errorIndicatorColor = MaterialTheme.colorScheme.error,
    )
}

/**
 * Круглая иконка-действие. Заменяет квадратные FilledIconButton: в стекло-
 * стиле действия — круглые плашки с акцентной подложкой.
 */
@Composable
fun GlassRoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = glassAccent(),
    size: Dp = 42.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (enabled) tint.copy(alpha = 0.18f) else tint.copy(alpha = 0.06f),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/**
 * Ряд из двух равношироких стеклянных пилюль (Импорт / Экспорт).
 */
@Composable
fun GlassPillRow(
    modifier: Modifier = Modifier,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GlassTokens.RowPaddingH, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f)) { first() }
        Box(Modifier.weight(1f)) { second() }
    }
}


/**
 * Разделитель строки, не зависящий от LocalGlassGrouped.
 * Нужен в диалогах: список опций там не обёрнут в GlassSurface, а разделители
 * между строками по референсу есть.
 */
@Composable
fun Modifier.glassAlwaysDivider(): Modifier {
    val c = glassDividerColor()
    val inset = GlassTokens.RowPaddingH
    return drawBehind {
        val insetPx = inset.toPx()
        drawRect(
            color = c,
            topLeft = Offset(insetPx, size.height - 1.dp.toPx()),
            size = Size(size.width - insetPx, 1.dp.toPx()),
        )
    }
}