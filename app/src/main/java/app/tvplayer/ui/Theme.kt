package app.tvplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val Background = Color(0xFF0E0F13)
    val Surface = Color(0xFF1A1C22)
    val SurfaceRaised = Color(0xFF252831)
    val Accent = Color(0xFFFF6A3D)
    val TextPrimary = Color.White
    val TextSecondary = Color(0xB3FFFFFF)
    val TextMuted = Color(0x80FFFFFF)
    val Focused = Color.White
    val OnFocused = Color(0xFF0E0F13)
}

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
