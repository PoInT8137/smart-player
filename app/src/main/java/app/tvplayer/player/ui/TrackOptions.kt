package app.tvplayer.player.ui

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale

data class PanelOption(
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean,
    /** Заголовок группы над пунктом — когда в одной вкладке несколько настроек. */
    val header: String? = null,
    val onSelect: () -> Unit,
)

private val ruLocale = Locale.forLanguageTag("ru")

fun audioOptions(player: Player, tracks: Tracks): List<PanelOption> {
    var number = 0
    return tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group ->
        (0 until group.length).map { i ->
            number++
            val format = group.getTrackFormat(i)
            val details = listOfNotNull(
                codecName(format),
                channelsName(format.channelCount),
                format.bitrate.takeIf { it > 0 }?.let { "${it / 1000} кбит/с" },
                "не поддерживается".takeUnless { group.isTrackSupported(i) },
            ).joinToString(" · ")
            PanelOption(
                title = trackTitle(format, "Дорожка $number"),
                subtitle = details.ifEmpty { null },
                selected = group.isTrackSelected(i),
                onSelect = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                        .build()
                },
            )
        }
    }
}

fun subtitleOptions(player: Player, tracks: Tracks): List<PanelOption> {
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val anySelected = textGroups.any { it.isSelected }
    val off = PanelOption(
        title = "Выключены",
        selected = !anySelected,
        onSelect = {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        },
    )
    var number = 0
    val options = textGroups.flatMap { group ->
        (0 until group.length).map { i ->
            number++
            val format = group.getTrackFormat(i)
            val details = listOfNotNull(
                codecName(format),
                "форсированные".takeIf { format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 },
            ).joinToString(" · ")
            PanelOption(
                title = trackTitle(format, "Субтитры $number"),
                subtitle = details.ifEmpty { null },
                selected = group.isTrackSelected(i),
                onSelect = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                        .build()
                },
            )
        }
    }
    return listOf(off) + options
}

private fun selectedFormat(tracks: Tracks, type: Int): Format? =
    tracks.groups.filter { it.type == type }.firstNotNullOfOrNull { group ->
        (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
    }

/** Короткое описание текущей звуковой дорожки: «Русский · AC3 5.1». */
fun currentAudioSummary(tracks: Tracks): String? = selectedFormat(tracks, C.TRACK_TYPE_AUDIO)?.let { f ->
    listOfNotNull(languageName(f.language), codecName(f), channelsName(f.channelCount)).joinToString(" · ")
}

/** Короткое описание текущих субтитров или «Выкл». */
fun currentSubtitleSummary(tracks: Tracks): String =
    selectedFormat(tracks, C.TRACK_TYPE_TEXT)?.let { trackTitle(it, "Вкл") } ?: "Выкл"

/** Бейджи для верхней панели: разрешение, кодек, HDR, звук. */
@OptIn(UnstableApi::class)
fun mediaBadges(tracks: Tracks): List<String> {
    val badges = mutableListOf<String>()
    selectedFormat(tracks, C.TRACK_TYPE_VIDEO)?.let { v ->
        val h = minOf(v.height, v.width).takeIf { it > 0 }
        val w = maxOf(v.height, v.width)
        when {
            w >= 3800 -> badges += "4K"
            h != null && h >= 1000 || w >= 1900 -> badges += "1080p"
            h != null && h >= 700 || w >= 1260 -> badges += "720p"
            h != null -> badges += "${h}p"
        }
        when (v.sampleMimeType) {
            MimeTypes.VIDEO_H265 -> badges += "HEVC"
            MimeTypes.VIDEO_H264 -> badges += "H.264"
            MimeTypes.VIDEO_AV1 -> badges += "AV1"
            MimeTypes.VIDEO_VP9 -> badges += "VP9"
            MimeTypes.VIDEO_DOLBY_VISION -> badges += "Dolby Vision"
        }
        when (v.colorInfo?.colorTransfer) {
            C.COLOR_TRANSFER_ST2084 -> badges += "HDR10"
            C.COLOR_TRANSFER_HLG -> badges += "HLG"
        }
    }
    selectedFormat(tracks, C.TRACK_TYPE_AUDIO)?.let { a ->
        listOfNotNull(codecName(a), channelsName(a.channelCount)).joinToString(" ").takeIf { it.isNotBlank() }
            ?.let { badges += it }
    }
    return badges
}

private fun languageName(code: String?): String? = code
    ?.takeUnless { it == C.LANGUAGE_UNDETERMINED || it == "und" }
    ?.let { Locale.forLanguageTag(it).getDisplayLanguage(ruLocale) }
    ?.takeIf { it.isNotBlank() }
    ?.replaceFirstChar { it.titlecase(ruLocale) }

val speedValues = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

fun speedName(speed: Float): String =
    if (speed == 1f) "Обычная" else speed.toString().removeSuffix(".0") + "×"

@OptIn(UnstableApi::class)
val resizeModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Вписать",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Заполнить (обрезать края)",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Растянуть",
)

private fun trackTitle(format: Format, fallback: String): String {
    val language = languageName(format.language)
    val label = format.label?.takeIf { it.isNotBlank() }
    return when {
        language != null && label != null && !label.contains(language, ignoreCase = true) -> "$language — $label"
        label != null -> label
        language != null -> language
        else -> fallback
    }
}

@OptIn(UnstableApi::class)
private fun codecName(format: Format): String? {
    // Субтитры с парсингом при извлечении приходят как media3-cues, исходный тип — в codecs
    val mime = if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) format.codecs else format.sampleMimeType
    return when (mime) {
        null -> null
        MimeTypes.AUDIO_AC3 -> "AC3"
        MimeTypes.AUDIO_E_AC3 -> "E-AC3"
        MimeTypes.AUDIO_E_AC3_JOC -> "E-AC3 Atmos"
        MimeTypes.AUDIO_AC4 -> "AC4"
        MimeTypes.AUDIO_DTS -> "DTS"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_DTS_EXPRESS -> "DTS Express"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_RAW -> "PCM"
        MimeTypes.APPLICATION_SUBRIP -> "SRT"
        MimeTypes.TEXT_SSA -> "ASS"
        MimeTypes.TEXT_VTT -> "WebVTT"
        MimeTypes.APPLICATION_TTML -> "TTML"
        MimeTypes.APPLICATION_PGS -> "PGS"
        MimeTypes.APPLICATION_VOBSUB -> "VobSub"
        MimeTypes.APPLICATION_DVBSUBS -> "DVB"
        else -> mime.substringAfter('/').uppercase()
    }
}

private fun channelsName(count: Int): String? = when (count) {
    Format.NO_VALUE, 0 -> null
    1 -> "моно"
    2 -> "2.0"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$count кан."
}

@OptIn(UnstableApi::class)
fun describeError(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
        "Нет соединения с сервером. Проверьте, что TorrServer запущен и доступен."
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "Сервер не отвечает. Возможно, у раздачи мало сидов — попробуйте позже или другую раздачу."
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
        val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        "Сервер вернул ошибку" + (code?.let { " $it" } ?: "") + "."
    }
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Файл не найден."
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Нет доступа к файлу."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
        "Формат файла не поддерживается или файл повреждён."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
        "Устройство не может декодировать это видео: кодек или разрешение не поддерживаются."
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
        "Ошибка аудиовыхода. Попробуйте выбрать другую звуковую дорожку."
    else -> "Ошибка воспроизведения."
}

fun formatTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000)
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
