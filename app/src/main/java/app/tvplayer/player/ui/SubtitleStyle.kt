package app.tvplayer.player.ui

import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/** Отступ субтитров снизу (доля высоты кадра): обычный и когда открыто меню управления. */
const val SUBTITLE_BOTTOM_NORMAL = 0.07f
const val SUBTITLE_BOTTOM_ABOVE_CONTROLS = 0.41f

/**
 * Субтитры «как в кинотеатре»: белый полужирный текст без подложки,
 * с мягкой тенью — читается на любом кадре и не закрывает картинку чёрным блоком.
 */
@OptIn(UnstableApi::class)
fun SubtitleView.applyCinemaStyle() {
    setStyle(
        CaptionStyleCompat(
            /* foregroundColor = */ Color.WHITE,
            /* backgroundColor = */ Color.TRANSPARENT,
            /* windowColor = */ Color.TRANSPARENT,
            /* edgeType = */ CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            /* edgeColor = */ Color.argb(230, 0, 0, 0),
            /* typeface = */ Typeface.create("sans-serif-medium", Typeface.NORMAL),
        )
    )
    // Цвета и позиции из ASS/SSA оставляем, а размер — единый, чтобы не прыгал
    setApplyEmbeddedStyles(true)
    setApplyEmbeddedFontSizes(false)
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.15f)
    setBottomPaddingFraction(SUBTITLE_BOTTOM_NORMAL)
}
