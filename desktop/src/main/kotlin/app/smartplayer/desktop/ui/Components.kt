package app.smartplayer.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppColors {
    val Background = Color(0xFF0A0B0F)
    val Surface = Color(0xFF171920)
    val SurfaceRaised = Color(0xFF22252E)
    val GlassLight = Color(0x26FFFFFF)
    val Stroke = Color(0x2EFFFFFF)
    val Accent = Color(0xFFFF6A3D)
    val AccentSecondary = Color(0xFFFF3D71)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xB3FFFFFF)
    val TextMuted = Color(0x73FFFFFF)
    val OnFocused = Color(0xFF0A0B0F)
}

private val CardPalettes = listOf(
    Color(0xFF3A1C71) to Color(0xFFD76D77),
    Color(0xFF0F2027) to Color(0xFF2C5364),
    Color(0xFF42275A) to Color(0xFF734B6D),
    Color(0xFF1D2B64) to Color(0xFF5C7ACF),
    Color(0xFF2B1B17) to Color(0xFFB0573A),
    Color(0xFF13334C) to Color(0xFF2A9D8F),
    Color(0xFF3C1053) to Color(0xFFAD5389),
    Color(0xFF232526) to Color(0xFF5B6770),
)

fun paletteFor(seed: String): Pair<Color, Color> = CardPalettes[(seed.hashCode() and Int.MAX_VALUE) % CardPalettes.size]

@Composable
fun SmartPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppColors.Accent,
            background = AppColors.Background,
            surface = AppColors.Surface,
            onBackground = AppColors.TextPrimary,
            onSurface = AppColors.TextPrimary,
        ),
        content = content,
    )
}

/**
 * Интерактивный элемент: подсвечивается и пружинисто увеличивается при наведении мыши
 * или фокусе с клавиатуры (Tab/стрелки). Клик и Enter — действие.
 */
@Composable
fun Interactive(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onActiveChange: ((Boolean) -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    activeScale: Float = 1.05f,
    background: Color = AppColors.Surface,
    activeBackground: Color = Color.White,
    activeBorder: Boolean = false,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.(active: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = hovered || focused
    val scale by animateFloatAsState(
        if (active) activeScale else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "scale",
    )
    val bg by animateColorAsState(if (active) activeBackground else background, tween(160), label = "bg")
    val borderWidth by animateDpAsState(if (active && activeBorder) 3.dp else 0.dp, tween(160), label = "border")
    val elevation by animateFloatAsState(if (active) 24f else 0f, tween(200), label = "shadow")

    LaunchedEffect(active) { onActiveChange?.invoke(active) }

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
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = contentAlignment,
    ) {
        content(active)
    }
}

/** Кнопка-«таблетка» с иконкой. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    Interactive(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        activeScale = 1.06f,
        background = if (primary) AppColors.Accent else AppColors.GlassLight,
        activeBackground = Color.White,
    ) { active ->
        val color = if (active) AppColors.OnFocused else AppColors.TextPrimary
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** «Обложка» без постера: градиент по хешу названия и крупная полупрозрачная буква. */
@Composable
fun CardArt(seed: String, modifier: Modifier = Modifier, letterSize: TextUnit = 110.sp, content: @Composable BoxScope.() -> Unit = {}) {
    val (from, to) = paletteFor(seed)
    Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(from, to)))) {
        Text(
            seed.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "▶",
            color = Color.White.copy(alpha = 0.14f),
            fontSize = letterSize,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 10.dp),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xCC000000))))
        content()
    }
}

@Composable
fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Text(fmt.format(Date(now)), modifier = modifier, color = AppColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Light)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Caption(text: String, color: Color = AppColors.TextSecondary, size: TextUnit = 14.sp, lines: Int = 1) {
    Text(text, color = color, fontSize = size, maxLines = lines, overflow = TextOverflow.Ellipsis)
}
