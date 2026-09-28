package app.tvplayer.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.tvplayer.data.HistoryEntry
import app.tvplayer.data.PlaybackHistory
import app.tvplayer.data.PlayerSettings
import app.tvplayer.player.afr.AutoFrameRate
import app.tvplayer.player.afr.FrameRateDetector
import app.tvplayer.player.afr.FrameRateMatcher
import app.tvplayer.player.ui.PlayerScreen
import app.tvplayer.player.ui.formatTime
import app.tvplayer.ui.TVPlayerTheme

/**
 * Полноэкранный плеер. Запускается из Lampa и других приложений через ACTION_VIEW,
 * по завершении возвращает позицию в формате MX Player (и VLC) через setResult.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var history: PlaybackHistory
    private lateinit var settings: PlayerSettings
    private lateinit var autoFrameRate: AutoFrameRate
    private var autoFrameRateEnabled by mutableStateOf(true)
    private var player: ExoPlayer? = null
    private var stateHolder by mutableStateOf<PlayerStateHolder?>(null)
    private var startHint: String? = null
    private var completed = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                completed = true
                finishWithResult()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Предыдущая серия досмотрена — убираем её из «Продолжить просмотр»
            // Новая серия может иметь другую частоту кадров
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                frameRateDetector.reset()
                autoFrameRate.onNewItem()
                stateHolder?.videoFps = null
            }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val p = player ?: return
                val previousIndex = p.previousMediaItemIndex
                if (previousIndex != C.INDEX_UNSET) history.remove(p.getMediaItemAt(previousIndex).mediaId)
            }
        }
    }

    private val frameRateDetector = FrameRateDetector { fps ->
        stateHolder?.videoFps = fps
        player?.let { autoFrameRate.onFrameRateDetected(fps, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        history = PlaybackHistory(this)
        settings = PlayerSettings(this)
        autoFrameRateEnabled = settings.autoFrameRate
        autoFrameRate = AutoFrameRate(this) { hz ->
            stateHolder?.postNotice("Частота экрана: ${FrameRateMatcher.format(hz)} Гц — под фильм")
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            TVPlayerTheme {
                stateHolder?.let { holder ->
                    key(holder) {
                        PlayerScreen(
                            state = holder,
                            initialHint = startHint,
                            autoFrameRate = autoFrameRateEnabled,
                            onAutoFrameRateChange = ::setAutoFrameRate,
                            onExit = ::finishWithResult,
                        )
                    }
                }
            }
        }

        if (!openIntent(intent)) finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        saveProgress()
        openIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        player?.pause()
    }

    override fun onDestroy() {
        autoFrameRate.restore()
        releasePlayer()
        super.onDestroy()
    }

    private fun openIntent(intent: Intent): Boolean {
        val request = PlaybackRequest.fromIntent(intent)
        if (request == null) {
            Toast.makeText(this, "Не передана ссылка на видео", Toast.LENGTH_LONG).show()
            return false
        }

        releasePlayer()
        completed = false
        frameRateDetector.reset()
        autoFrameRate.onNewItem()
        autoFrameRate.setEnabled(autoFrameRateEnabled, null)

        val items = request.items.mapIndexed { index, entry ->
            buildMediaItem(entry, if (index == request.startIndex) request.subtitles else emptyList())
        }
        val saved = history.get(request.current.uri.toString())?.resumePositionMs
        val startPosition = request.startPositionMs ?: saved ?: 0L
        startHint = if (request.startPositionMs == null && saved != null) {
            "Продолжаем с ${formatTime(saved)}"
        } else {
            null
        }

        val newPlayer = PlayerFactory.create(this, request.headers).apply {
            addListener(playerListener)
            setVideoFrameMetadataListener(frameRateDetector)
            applyFrameRateStrategy()
            setMediaItems(items, request.startIndex, startPosition)
            prepare()
            playWhenReady = true
        }
        player = newPlayer
        stateHolder = PlayerStateHolder(newPlayer)
        return true
    }

    private fun setAutoFrameRate(enabled: Boolean) {
        autoFrameRateEnabled = enabled
        settings.autoFrameRate = enabled
        player?.let {
            it.applyFrameRateStrategy()
            autoFrameRate.setEnabled(enabled, it)
        }
    }

    /**
     * Когда AFR включён, режим экрана выбираем сами; встроенную «бесшовную» смену
     * частоты ExoPlayer отключаем, чтобы они не спорили друг с другом.
     */
    private fun ExoPlayer.applyFrameRateStrategy() {
        videoChangeFrameRateStrategy = if (autoFrameRateEnabled) {
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        } else {
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
        }
    }

    private fun buildMediaItem(entry: MediaEntry, subtitles: List<SubtitleSource>): MediaItem =
        MediaItem.Builder()
            .setUri(entry.uri)
            .setMediaId(entry.uri.toString())
            .setMimeType(adaptiveMimeType(entry.mimeType))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).build())
            .setSubtitleConfigurations(
                subtitles.map {
                    MediaItem.SubtitleConfiguration.Builder(it.uri)
                        .setMimeType(subtitleMimeType(it.uri))
                        .setLabel(it.name)
                        .build()
                }
            )
            .build()

    /** Явно указываем тип только для HLS/DASH — прогрессивные файлы ExoPlayer распознает сам. */
    private fun adaptiveMimeType(mime: String?): String? = when (mime?.lowercase()) {
        "application/x-mpegurl", "application/vnd.apple.mpegurl" -> MimeTypes.APPLICATION_M3U8
        "application/dash+xml" -> MimeTypes.APPLICATION_MPD
        else -> null
    }

    private fun subtitleMimeType(uri: Uri): String =
        when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }

    private fun saveProgress() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        if (completed) {
            history.remove(item.mediaId)
            return
        }
        history.save(
            HistoryEntry(
                uri = item.mediaId,
                title = item.mediaMetadata.title?.toString() ?: item.mediaId,
                positionMs = p.currentPosition,
                durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun finishWithResult() {
        if (isFinishing) return
        autoFrameRate.restore()
        val p = player
        val duration = p?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val position = if (completed) duration else p?.currentPosition ?: 0L
        saveProgress()

        val result = Intent(RESULT_ACTION).apply {
            data = p?.currentMediaItem?.localConfiguration?.uri
            // MX Player
            putExtra("end_by", if (completed) "playback_completion" else "user")
            putExtra("position", position.toInt())
            putExtra("duration", duration.toInt())
            // VLC
            putExtra("extra_position", position)
            putExtra("extra_duration", duration)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun releasePlayer() {
        stateHolder?.release()
        stateHolder = null
        player?.run {
            removeListener(playerListener)
            clearVideoFrameMetadataListener(frameRateDetector)
            release()
        }
        player = null
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val RESULT_ACTION = "com.mxtech.intent.result.VIEW"
    }
}
