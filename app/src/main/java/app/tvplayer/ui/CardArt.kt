package app.tvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «Обложка» без постера: диагональный градиент по хешу названия и крупная
 * полупрозрачная первая буква. Поверх рисуется [content].
 */
@Composable
fun CardArt(
    seed: String,
    modifier: Modifier = Modifier,
    letterSize: TextUnit = 140.sp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val (from, to) = paletteFor(seed)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(from, to))),
    ) {
        Text(
            text = seed.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "▶",
            color = Color.White.copy(alpha = 0.14f),
            fontSize = letterSize,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 12.dp),
        )
        // Затемнение снизу — под текст
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xCC000000))),
        )
        content()
    }
}
