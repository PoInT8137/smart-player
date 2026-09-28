package app.tvplayer.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tvplayer.ui.AppColors
import app.tvplayer.ui.TvButton
import kotlin.math.abs

/** Большой значок плей/пауза по центру: появляется с «пружинкой» и тает. */
@Composable
fun BoxScope.CenterFlash(icon: ImageVector?, lastIcon: ImageVector?) {
    AnimatedVisibility(
        visible = icon != null,
        enter = scaleIn(initialScale = 0.5f, animationSpec = tween(220)) + fadeIn(tween(150)),
        exit = scaleOut(targetScale = 1.4f, animationSpec = tween(450)) + fadeOut(tween(450)),
        modifier = Modifier.align(Alignment.Center),
    ) {
        Box(
            Modifier
                .size(132.dp)
                .background(Color(0x80000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            (icon ?: lastIcon)?.let {
                Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
            }
        }
    }
}

/** «Пузырь» перемотки сбоку экрана: ⏪ −30 с / ⏩ +1:20. */
@Composable
fun BoxScope.SeekBubble(deltaMs: Long?) {
    val forward = (deltaMs ?: 0) >= 0
    AnimatedVisibility(
        visible = deltaMs != null,
        enter = scaleIn(initialScale = 0.7f) + fadeIn(),
        exit = scaleOut(targetScale = 0.9f) + fadeOut(),
        modifier = Modifier
            .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
            .padding(horizontal = 140.dp),
    ) {
        Column(
            modifier = Modifier
                .size(170.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0x99000000), Color(0x00000000))),
                    CircleShape,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
            val d = abs(deltaMs ?: 0)
            Text(
                text = (if (forward) "+" else "−") + if (d < 60_000) "${d / 1000} с" else formatTime(d),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Индикатор загрузки: вращающаяся дуга с градиентом и подпись. */
@Composable
fun BufferingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "buffering")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "angle",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(72.dp)
                .rotate(angle),
        ) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, AppColors.Accent, AppColors.AccentSecondary)),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("Загрузка", color = Color.White.copy(alpha = pulse), fontSize = 16.sp)
    }
}

/** Карточка «Следующая серия» в конце эпизода. */
@Composable
fun NextUpCard(title: String, secondsLeft: Int, progress: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(440.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.Glass)
            .border(1.dp, AppColors.Stroke, RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("ДАЛЕЕ", color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Через $secondsLeft с", color = AppColors.TextSecondary, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                KeyHint("OK", "смотреть сейчас")
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(AppColors.GlassLight),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(Brush.horizontalGradient(listOf(AppColors.Accent, AppColors.AccentSecondary))),
            )
        }
    }
}

/** Подсказка по кнопке пульта: [OK] пауза. */
@Composable
fun KeyHint(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            key,
            color = AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(1.dp, AppColors.TextMuted, RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(action, color = AppColors.TextSecondary, fontSize = 13.sp)
    }
}

@Composable
fun ErrorOverlay(
    message: String,
    details: String,
    focusRequester: FocusRequester,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xE61A0E0A), Color(0xF2000000)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(680.dp),
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(AppColors.Accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("Не удалось воспроизвести", color = AppColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(message, color = AppColors.TextSecondary, fontSize = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(details, color = AppColors.TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvButton("Повторить", onClick = onRetry, modifier = Modifier.focusRequester(focusRequester))
                TvButton("Выйти", onClick = onExit)
            }
        }
    }
}

/** Прогресс-линия: фон, буфер, просмотрено (с градиентом) и бегунок. */
@Composable
fun ProgressTrack(
    fraction: Float,
    bufferedFraction: Float,
    heightPx: Float,
    thumbScale: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val top = (size.height - heightPx) / 2
        val r = androidx.compose.ui.geometry.CornerRadius(heightPx / 2, heightPx / 2)
        drawRoundRect(Color(0x33FFFFFF), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, heightPx), r)
        drawRoundRect(Color(0x59FFFFFF), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width * bufferedFraction, heightPx), r)
        val played = size.width * fraction
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(AppColors.Accent, AppColors.AccentSecondary), endX = size.width.coerceAtLeast(1f)),
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(played, heightPx),
            cornerRadius = r,
        )
        if (thumbScale > 0.01f) {
            val c = Offset(played, size.height / 2)
            drawCircle(AppColors.Accent.copy(alpha = 0.35f * thumbScale), radius = heightPx * 1.9f * thumbScale, center = c)
            drawCircle(Color.White, radius = heightPx * 1.1f * thumbScale, center = c)
        }
    }
}

private class LastValue<T> {
    var value: T? = null
}

/** Последнее ненулевое значение — чтобы анимация исчезновения показывала старый контент. */
@Composable
fun <T> rememberLast(value: T?): T? {
    val holder = remember { LastValue<T>() }
    if (value != null) holder.value = value
    return holder.value
}
