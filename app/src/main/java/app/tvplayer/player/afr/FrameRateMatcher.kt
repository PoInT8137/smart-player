package app.tvplayer.player.afr

import kotlin.math.abs
import kotlin.math.roundToInt

/** Режим экрана без привязки к android.view.Display — чтобы логику можно было тестировать. */
data class DisplayModeInfo(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/**
 * Подбор частоты экрана под частоту кадров видео.
 *
 * Идеально — частота экрана равна частоте кадров (23.976 → 23.976 Гц) или кратна ей
 * (25 → 50 Гц, 29.97 → 59.94 Гц): тогда каждый кадр показывается одинаковое время
 * и нет подёргиваний, которые дают 24 кадра на 60 Гц.
 */
object FrameRateMatcher {

    /** Стандартные частоты кадров, к которым «прилипают» измеренные значения. */
    private val KNOWN_RATES = floatArrayOf(
        23.976f, 24f, 25f, 29.97f, 30f, 47.952f, 48f, 50f, 59.94f, 60f, 100f, 119.88f, 120f,
    )

    /** Точное совпадение: 23.976 и 24 отличаются на 0.1%, их нужно различать. */
    private const val EXACT_TOLERANCE = 0.0004f

    /** Приблизительное (23.976 на 24 Гц): лучше, чем 60 Гц, но хуже точного. */
    private const val APPROX_TOLERANCE = 0.0015f

    private const val MAX_MULTIPLE = 4

    /** Приводит измеренную частоту к стандартной; null — если она ни на что не похожа. */
    fun normalize(fps: Float): Float? {
        if (fps <= 0f || fps.isNaN()) return null
        val nearest = KNOWN_RATES.minBy { abs(it - fps) / it }
        return nearest.takeIf { abs(nearest - fps) / nearest < 0.01f }
    }

    /**
     * Лучший режим с тем же разрешением, что и [current], или null, если переключаться
     * не нужно (текущий режим уже лучший) или подходящего режима нет.
     */
    fun bestMode(fps: Float, current: DisplayModeInfo, modes: List<DisplayModeInfo>): DisplayModeInfo? {
        val best = modes
            .filter { it.width == current.width && it.height == current.height }
            .mapNotNull { mode -> score(fps, mode.refreshRate)?.let { mode to it } }
            .minWithOrNull(compareBy<Pair<DisplayModeInfo, Score>> { it.second.tier }
                .thenBy { it.second.multiple }
                .thenBy { it.second.error })
            ?: return null

        // Текущий режим не хуже найденного — не дёргаем телевизор
        val currentScore = score(fps, current.refreshRate)
        if (best.first.id == current.id) return null
        if (currentScore != null && currentScore.tier <= best.second.tier && currentScore.multiple <= best.second.multiple) {
            return null
        }
        return best.first
    }

    private data class Score(val tier: Int, val multiple: Int, val error: Float)

    private fun score(fps: Float, refreshRate: Float): Score? {
        val ratio = refreshRate / fps
        val multiple = ratio.roundToInt()
        if (multiple !in 1..MAX_MULTIPLE) return null
        val error = abs(ratio - multiple) / multiple
        val tier = when {
            error <= EXACT_TOLERANCE -> 0
            error <= APPROX_TOLERANCE -> 1
            else -> return null
        }
        return Score(tier, multiple, error)
    }

    /** 23.976 → «23.976», 24.0 → «24», 29.97 → «29.97». */
    fun format(rate: Float): String =
        if (abs(rate - rate.roundToInt()) < 0.005f) rate.roundToInt().toString()
        else "%.3f".format(java.util.Locale.US, rate).trimEnd('0').trimEnd('.')
}
