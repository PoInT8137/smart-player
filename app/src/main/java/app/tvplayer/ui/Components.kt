package app.tvplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Плавно растворяет верхний край содержимого (для прокручиваемых списков). */
fun Modifier.fadingTopEdge(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val stop = (height.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, stop to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }

/** requestFocus(), который не падает, если элемент ещё не в композиции. */
fun FocusRequester.safeRequestFocus() {
    runCatching { requestFocus() }
}

/**
 * Базовый элемент для пульта. В фокусе — пружинисто увеличивается, плавно
 * меняет фон, получает светящуюся рамку и тень. Содержимое получает [focused].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    focusedScale: Float = 1.06f,
    background: Color = AppColors.Surface,
    focusedBackground: Color = AppColors.Focused,
    focusBorder: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "focusScale",
    )
    val bg by animateColorAsState(if (focused) focusedBackground else background, tween(180), label = "focusBg")
    val borderWidth by animateDpAsState(if (focused && focusBorder) 3.dp else 0.dp, tween(180), label = "focusBorder")
    val elevation by animateFloatAsState(if (focused) 28f else 0f, tween(220), label = "focusShadow")

    LaunchedEffect(focused) { onFocusChange?.invoke(focused) }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
                this.shape = shape
                clip = true
            }
            .background(bg)
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, Color.White, shape) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
    ) {
        content(focused)
    }
}

/** Кнопка-«таблетка» с иконкой, подписью и (необязательно) текущим значением. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    background: Color = AppColors.GlassLight,
    compact: Boolean = false,
) {
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.08f,
        background = background,
    ) { focused ->
        val color = if (focused) AppColors.OnFocused else AppColors.TextPrimary
        Row(
            modifier = if (compact) {
                Modifier.padding(start = 14.dp, end = 18.dp, top = 5.dp, bottom = 5.dp)
            } else {
                Modifier.padding(start = 20.dp, end = 24.dp, top = 10.dp, bottom = 10.dp)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (compact) 18.dp else 24.dp))
                Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
            }
            Column {
                Text(
                    text,
                    color = color,
                    fontSize = if (compact) 13.sp else 16.sp,
                    lineHeight = if (compact) 16.sp else 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (value != null) {
                    Text(
                        value,
                        color = color.copy(alpha = 0.65f),
                        fontSize = if (compact) 10.sp else 12.sp,
                        lineHeight = if (compact) 13.sp else 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Небольшой «бейдж» с технической информацией: 1080p, HDR10, AC3 5.1... */
@Composable
fun InfoBadge(text: String, modifier: Modifier = Modifier, accent: Boolean = false, small: Boolean = false) {
    Text(
        text = text,
        color = if (accent) AppColors.OnFocused else AppColors.TextPrimary,
        fontSize = if (small) 11.sp else 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(if (accent) AppColors.Accent else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.dp, if (accent) AppColors.Accent else AppColors.Stroke, RoundedCornerShape(6.dp))
            .padding(horizontal = if (small) 6.dp else 8.dp, vertical = if (small) 1.dp else 3.dp),
    )
}

/** Часы; при заданном [remainingMs] показывают, во сколько закончится просмотр. */
@Composable
fun Clock(modifier: Modifier = Modifier, remainingMs: Long? = null, big: Boolean = true) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(
            fmt.format(Date(now)),
            color = AppColors.TextPrimary,
            fontSize = if (big) 30.sp else 22.sp,
            fontWeight = FontWeight.Light,
        )
        if (remainingMs != null && remainingMs > 0) {
            Text(
                "Конец в " + fmt.format(Date(now + remainingMs)),
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
            )
        }
    }
}
