package app.tvplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val Background = Color(0xFF0A0B0F)
    val Surface = Color(0xFF171920)
    val SurfaceRaised = Color(0xFF22252E)
    val Glass = Color(0xE6121419)
    val GlassLight = Color(0x26FFFFFF)
    val Stroke = Color(0x2EFFFFFF)
    val Accent = Color(0xFFFF6A3D)
    val AccentSecondary = Color(0xFFFF3D71)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xB3FFFFFF)
    val TextMuted = Color(0x73FFFFFF)
    val Focused = Color.White
    val OnFocused = Color(0xFF0A0B0F)
    val Scrim = Color(0xCC000000)
}

/** Пары цветов для «обложек» без постеров — выбираются по хешу названия. */
val CardPalettes = listOf(
    Color(0xFF3A1C71) to Color(0xFFD76D77),
    Color(0xFF0F2027) to Color(0xFF2C5364),
    Color(0xFF42275A) to Color(0xFF734B6D),
    Color(0xFF1D2B64) to Color(0xFF5C7ACF),
    Color(0xFF2B1B17) to Color(0xFFB0573A),
    Color(0xFF13334C) to Color(0xFF2A9D8F),
    Color(0xFF3C1053) to Color(0xFFAD5389),
    Color(0xFF232526) to Color(0xFF5B6770),
)

fun paletteFor(seed: String): Pair<Color, Color> =
    CardPalettes[(seed.hashCode() and Int.MAX_VALUE) % CardPalettes.size]

@Composable
fun TVPlayerTheme(content: @Composable () -> Unit) {
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
