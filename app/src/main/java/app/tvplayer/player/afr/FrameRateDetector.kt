package app.tvplayer.player.afr

import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.video.VideoFrameMetadataListener

/**
 * Определяет частоту кадров видео. Если контейнер её сообщает — берём сразу,
 * иначе считаем по меткам времени показанных кадров (MKV часто без fps).
 *
 * Метки в MKV округлены до миллисекунд (41/42 мс для 23.976), поэтому берём
 * среднее по длинному отрезку, а не медиану соседних интервалов: за ~3 с
 * погрешность ~0.03% — достаточно, чтобы отличить 23.976 от 24.
 *
 * Колбэк плеера приходит в потоке воспроизведения, результат — в главном.
 */
@OptIn(UnstableApi::class)
class FrameRateDetector(private val onDetected: (Float) -> Unit) : VideoFrameMetadataListener {

    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var firstUs = -1L
    private var lastUs = -1L
    private var frames = 0
    private var done = false

    /** Сбросить для нового файла (другая серия может иметь другую частоту). */
    fun reset() = synchronized(lock) {
        firstUs = -1L
        lastUs = -1L
        frames = 0
        done = false
    }

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long,
        releaseTimeNs: Long,
        format: Format,
        mediaFormat: MediaFormat?,
    ) {
        val result = synchronized(lock) {
            if (done) return
            if (format.frameRate > 0f) {
                done = true
                return@synchronized format.frameRate
            }
            val delta = presentationTimeUs - lastUs
            if (firstUs < 0 || delta <= 0 || delta > MAX_FRAME_GAP_US) {
                // Первый кадр, перемотка или разрыв — начинаем отрезок заново
                firstUs = presentationTimeUs
                lastUs = presentationTimeUs
                frames = 1
                return
            }
            lastUs = presentationTimeUs
            frames++
            val span = lastUs - firstUs
            if (frames < MIN_FRAMES || span < MIN_SPAN_US) return
            done = true
            (frames - 1) * 1_000_000f / span
        }
        main.post { onDetected(result) }
    }

    private companion object {
        const val MIN_FRAMES = 60
        const val MIN_SPAN_US = 2_500_000L
        const val MAX_FRAME_GAP_US = 250_000L
    }
}
