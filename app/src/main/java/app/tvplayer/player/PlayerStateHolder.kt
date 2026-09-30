package app.tvplayer.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

/** Снимок состояния ExoPlayer в виде Compose-состояния. */
class PlayerStateHolder(val player: ExoPlayer) : Player.Listener {

    var isPlaying by mutableStateOf(false)
        private set
    /** Намерение играть (true и во время буферизации) — по нему рисуем кнопку пауза/плей. */
    var playWhenReady by mutableStateOf(false)
        private set
    var playbackState by mutableIntStateOf(Player.STATE_IDLE)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var tracks by mutableStateOf(Tracks.EMPTY)
        private set
    var title by mutableStateOf<String?>(null)
        private set
    var itemIndex by mutableIntStateOf(0)
        private set
    var itemTitles by mutableStateOf<List<String>>(emptyList())
        private set
    var currentUri by mutableStateOf<String?>(null)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var error by mutableStateOf<PlaybackException?>(null)
        private set

    /** Частота кадров видео (измеренная или из контейнера). */
    var videoFps by mutableStateOf<Float?>(null)

    /** Сообщение от активити для всплывающей подсказки (id — чтобы повторы тоже показывались). */
    var notice by mutableStateOf<Notice?>(null)
        private set
    private var noticeId = 0

    fun postNotice(text: String) {
        notice = Notice(++noticeId, text)
    }

    val itemCount: Int get() = itemTitles.size.coerceAtLeast(1)
    val isBuffering: Boolean get() = playbackState == Player.STATE_BUFFERING
    val hasNext: Boolean get() = itemIndex < itemCount - 1
    val hasPrevious: Boolean get() = itemIndex > 0
    val nextTitle: String? get() = itemTitles.getOrNull(itemIndex + 1)
    val remainingMs: Long get() = if (durationMs > 0) ((durationMs - positionMs) / speed).toLong() else 0L

    init {
        player.addListener(this)
        sync()
    }

    fun updateProgress() {
        positionMs = player.currentPosition
        durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        bufferedMs = player.bufferedPosition
    }

    fun clearError() {
        error = null
    }

    override fun onEvents(player: Player, events: Player.Events) = sync()

    override fun onPlayerError(error: PlaybackException) {
        this.error = error
    }

    private fun sync() {
        isPlaying = player.isPlaying
        playWhenReady = player.playWhenReady
        playbackState = player.playbackState
        tracks = player.currentTracks
        title = player.mediaMetadata.title?.toString()
        itemIndex = player.currentMediaItemIndex
        currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        val titles = List(player.mediaItemCount) { i ->
            player.getMediaItemAt(i).mediaMetadata.title?.toString() ?: "Файл ${i + 1}"
        }
        if (titles != itemTitles) itemTitles = titles
        speed = player.playbackParameters.speed
        updateProgress()
    }

    fun release() {
        player.removeListener(this)
    }
}

data class Notice(val id: Int, val text: String)
