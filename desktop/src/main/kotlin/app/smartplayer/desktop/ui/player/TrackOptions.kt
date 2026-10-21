package app.smartplayer.desktop.ui.player

import app.smartplayer.desktop.player.MpvEdition
import app.smartplayer.desktop.player.MpvEngine
import app.smartplayer.desktop.player.MpvTrack
import java.util.Locale

data class PanelOption(
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean,
    /** Заголовок группы над пунктом — когда в одной вкладке несколько настроек. */
    val header: String? = null,
    val onSelect: () -> Unit,
)

private val ru = Locale.forLanguageTag("ru")

fun audioOptions(engine: MpvEngine): List<PanelOption> {
    val audio = engine.tracks.filter { it.type == "audio" }
    return audio.mapIndexed { i, t ->
        PanelOption(
            title = trackTitle(t, "Дорожка ${i + 1}"),
            subtitle = listOfNotNull(codecName(t.codec), channelsName(t.channels)).joinToString(" · ").ifEmpty { null },
            selected = t.selected,
            onSelect = { engine.setAudio(t.id) },
        )
    }
}

fun subtitleOptions(engine: MpvEngine): List<PanelOption> {
    val subs = engine.tracks.filter { it.type == "sub" }
    val off = PanelOption("Выключены", selected = subs.none { it.selected }, onSelect = { engine.setSubtitle(null) })
    return listOf(off) + subs.mapIndexed { i, t ->
        PanelOption(
            title = trackTitle(t, "Субтитры ${i + 1}"),
            subtitle = listOfNotNull(codecName(t.codec), "форсированные".takeIf { t.forced }).joinToString(" · ").ifEmpty { null },
            selected = t.selected,
            onSelect = { engine.setSubtitle(t.id) },
        )
    }
}

/** «Русский · AC3 · 5.1» для кнопки «Звук». */
fun currentAudioSummary(engine: MpvEngine): String? = engine.tracks.firstOrNull { it.type == "audio" && it.selected }?.let { t ->
    listOfNotNull(languageName(t.lang), codecName(t.codec), channelsName(t.channels)).joinToString(" · ")
}

fun currentSubtitleSummary(engine: MpvEngine): String =
    engine.tracks.firstOrNull { it.type == "sub" && it.selected }?.let { trackTitle(it, "Вкл") } ?: "Выкл"

/** «4K», «1080p», «720p»… по размеру кадра; null, если размер неизвестен. */
private fun resolutionName(width: Int, height: Int): String? {
    val w = maxOf(width, height)
    val h = minOf(width, height)
    return when {
        w >= 3800 -> "4K"
        w >= 2500 || h >= 1400 -> "1440p"
        w >= 1900 || h >= 1000 -> "1080p"
        w >= 1260 || h >= 700 -> "720p"
        h > 0 -> "${h}p"
        else -> null
    }
}

private fun bitrateName(bitrate: Int): String? =
    bitrate.takeIf { it > 0 }?.let { String.format(ru, "%.1f Мбит/с", it / 1_000_000f) }

/**
 * Разрешения видео. Несколько вариантов бывает у HLS-потоков (новый mpv отдаёт их как editions,
 * старый — как видеодорожки); у обычного файла — один пункт с его разрешением.
 */
fun qualityOptions(engine: MpvEngine): List<PanelOption> {
    if (engine.editions.size > 1) {
        return engine.editions
            .sortedWith(compareByDescending<MpvEdition> { minOf(it.width, it.height) }.thenByDescending { it.bitrate })
            .map { e ->
                PanelOption(
                    title = resolutionName(e.width, e.height) ?: "Вариант ${e.id + 1}",
                    subtitle = bitrateName(e.bitrate),
                    selected = e.id == engine.currentEdition,
                    onSelect = { engine.setEdition(e.id) },
                )
            }
    }
    val videos = engine.tracks.filter { it.type == "video" && !it.albumArt }
        .sortedWith(compareByDescending<MpvTrack> { minOf(it.width, it.height) }.thenByDescending { it.bitrate })
    if (videos.size <= 1) {
        return videos.map {
            PanelOption(
                title = resolutionName(engine.videoWidth, engine.videoHeight) ?: "Исходное",
                subtitle = "Источник отдаёт видео только в этом качестве",
                selected = true,
                onSelect = {},
            )
        }
    }
    return videos.mapIndexed { i, t ->
        PanelOption(
            title = resolutionName(t.width, t.height) ?: trackTitle(t, "Вариант ${i + 1}"),
            subtitle = listOfNotNull(
                bitrateName(t.bitrate),
                codecName(t.codec),
            ).joinToString(" · ").ifEmpty { null },
            selected = t.selected,
            onSelect = { engine.setVideo(t.id) },
        )
    }
}

/** Текущее разрешение для кнопки «Качество». */
fun currentQualitySummary(engine: MpvEngine): String? = resolutionName(engine.videoWidth, engine.videoHeight)

/** Техническая информация о потоке: разрешение, кодек, HDR, звук, fps. */
fun mediaBadges(engine: MpvEngine): List<String> {
    val badges = mutableListOf<String>()
    resolutionName(engine.videoWidth, engine.videoHeight)?.let { badges += it }
    engine.tracks.firstOrNull { it.type == "video" && it.selected }?.codec?.let { codecName(it) }?.let { badges += it }
    engine.hdr?.let { badges += it }
    engine.tracks.firstOrNull { it.type == "audio" && it.selected }?.let { a ->
        listOfNotNull(codecName(a.codec), channelsName(a.channels)).joinToString(" ").takeIf { it.isNotBlank() }?.let { badges += it }
    }
    if (engine.fps > 0f) badges += formatFps(engine.fps) + " fps"
    return badges
}

private fun formatFps(f: Float): String =
    if (kotlin.math.abs(f - Math.round(f)) < 0.01f) Math.round(f).toString()
    else "%.3f".format(Locale.US, f).trimEnd('0').trimEnd('.')

private fun trackTitle(t: MpvTrack, fallback: String): String {
    val language = languageName(t.lang)
    val label = t.title?.takeIf { it.isNotBlank() }
    return when {
        language != null && label != null && !label.contains(language, ignoreCase = true) -> "$language — $label"
        label != null -> label
        language != null -> language
        else -> fallback
    }
}

private fun languageName(code: String?): String? = code
    ?.takeUnless { it.isBlank() || it == "und" }
    ?.let { Locale.forLanguageTag(it).getDisplayLanguage(ru).ifBlank { it } }
    ?.replaceFirstChar { it.titlecase(ru) }

/** Названия кодеков mpv → привычные. */
private fun codecName(codec: String?): String? = when (codec?.lowercase()) {
    null, "" -> null
    "ac3" -> "AC3"
    "eac3" -> "E-AC3"
    "dts" -> "DTS"
    "truehd", "mlp" -> "TrueHD"
    "aac" -> "AAC"
    "mp3", "mp2" -> "MP3"
    "opus" -> "Opus"
    "vorbis" -> "Vorbis"
    "flac" -> "FLAC"
    "pcm_s16le", "pcm_s24le", "pcm_s32le" -> "PCM"
    "h264" -> "H.264"
    "hevc" -> "HEVC"
    "av1" -> "AV1"
    "vp9" -> "VP9"
    "mpeg4" -> "MPEG-4"
    "subrip" -> "SRT"
    "ass", "ssa" -> "ASS"
    "webvtt" -> "WebVTT"
    "hdmv_pgs_subtitle" -> "PGS"
    "dvd_subtitle" -> "VobSub"
    else -> codec.uppercase()
}

private fun channelsName(count: Int?): String? = when (count) {
    null, 0 -> null
    1 -> "моно"
    2 -> "2.0"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$count кан."
}

val speedValues = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

fun speedName(speed: Float): String = if (speed == 1f) "Обычная" else speed.toString().removeSuffix(".0") + "×"

fun formatTime(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
