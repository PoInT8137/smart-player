package app.tvplayer.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import app.tvplayer.BuildConfig

@OptIn(UnstableApi::class)
object PlayerFactory {

    private const val USER_AGENT = "SmartPlayer/0.2 (Android TV)"

    const val SEEK_STEP_MS = 10_000L

    fun create(context: Context, headers: Map<String, String>): ExoPlayer {
        // TorrServer может долго отвечать, пока набирает пиров — таймауты побольше
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: USER_AGENT)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(60_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)

        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultDataSource.Factory(context, httpFactory),
            extractorsFactory,
        )

        // EXTENSION_RENDERER_MODE_ON: сначала аппаратный декодер / passthrough по HDMI,
        // а если устройство формат не тянет (DTS, TrueHD...) — программный FFmpeg.
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage("ru")
                    .setPreferredTextLanguage("ru")
            )
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
            .also {
                // В отладке пишем в logcat выбранные декодеры, форматы и ошибки (тег EventLogger)
                if (BuildConfig.DEBUG) it.addAnalyticsListener(EventLogger())
            }
    }
}
